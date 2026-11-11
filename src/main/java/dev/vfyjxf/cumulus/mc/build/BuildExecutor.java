package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.EditStep;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.mc.CaptureReports;
import dev.vfyjxf.cumulus.mc.LevelCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The build flow: fresh capture, check, solve, host-side dry run, transaction, re-check. Every
 * refusal is a report with zero writes.
 */
public final class BuildExecutor {

    private static final int maxElements = 1_000_000;
    private static final int maxRemedies = 64;

    private final LevelCapture capture;
    private final StructureKernel kernel;
    private final OwnerId owner;
    private final BuildPolicy serverPolicy;
    private final Materializer materializer = new VanillaMaterializer();

    public BuildExecutor(LevelCapture capture, StructureKernel kernel, OwnerId owner, BuildPolicy configured) {
        this.capture = Objects.requireNonNull(capture, "capture");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.serverPolicy = Objects.requireNonNull(configured, "configured");
    }

    public ApplyReport run(BuildRequest request) {
        Objects.requireNonNull(request, "request");
        BuildJournal journal = new BuildJournal(capture.level(), request.player().getUUID());
        return run(blueprint(request.blueprint()), request, journal);
    }

    /**
     * Runs a blueprint the caller already holds — a set member's compiled blueprint is not always a
     * document — and records its pre-image in the caller's journal.
     */
    public ApplyReport run(Blueprint blueprint, BuildRequest request, BuildJournal journal) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(journal, "journal");
        BuildPolicy policy = effective(request.policy());
        Cuboid bounds = request.siting().toWorld(blueprint.bounds());
        if (bounds == null) {
            throw new IllegalArgumentException("the sited bounds leave the lattice: " + request.siting());
        }
        return new Run(request, blueprint, policy, bounds, journal).execute();
    }

    /** A per-run policy may tighten the server's ceiling, never raise it. */
    private BuildPolicy effective(BuildPolicy requested) {
        return requested.maxTouchedVolume() > serverPolicy.maxTouchedVolume()
                ? requested.withMaxTouchedVolume(serverPolicy.maxTouchedVolume())
                : requested;
    }

    private Blueprint blueprint(NamespacedId id) {
        Document document = kernel.document(id);
        if (document == null) {
            throw new IllegalArgumentException("no document " + id.text());
        }
        Blueprint blueprint = kernel.compile(document).blueprint();
        if (blueprint == null) {
            throw new IllegalArgumentException("the document does not compile: " + id.text());
        }
        return blueprint;
    }

    private final class Run {

        private final BuildRequest request;
        private final Blueprint blueprint;
        private final BuildPolicy policy;
        private final Cuboid bounds;
        private final ServerLevel level;
        private final BuildJournal journal;

        private Run(
            BuildRequest request,
            Blueprint blueprint,
            BuildPolicy policy,
            Cuboid bounds,
            BuildJournal journal
        ) {
            this.request = request;
            this.blueprint = blueprint;
            this.policy = policy;
            this.bounds = bounds;
            this.level = capture.level();
            this.journal = journal;
        }

        private ApplyReport execute() {
            CaptureResult fresh = capture.capture(
                new CaptureRequest(bounds, policy(blueprint, request.siting())),
                request.siting().placement().orientation()
            );
            if (!(fresh instanceof CaptureResult.Captured captured)) {
                return ApplyReport.refused(CaptureReports.relay(reportOf(fresh), kernel.catalogs().codes()));
            }
            try (SnapshotLease lease = captured.lease()) {
                ClaimView claims = claims(bounds, request.siting());
                MatchResult checked = check(lease, claims);
                if (matched(checked)) {
                    return ApplyReport.formed();
                }
                SolveResult solved = kernel.solve(
                    checked.findings(),
                    new RemedyContext(
                        blueprint,
                        request.siting(),
                        claims,
                        claims == null ? 0 : claims.revision(),
                        outcome(checked),
                        null,
                        maxRemedies,
                        request.preferences()
                    )
                );
                Remedy picked = pick(solved);
                if (!(picked instanceof Remedy.Repair repair)) {
                    return withFinding(solved, noMachineRepair(checked));
                }
                return validated(repair, solved, claims);
            }
        }

        private ApplyReport validated(Remedy.Repair repair, SolveResult solved, @Nullable ClaimView claims) {
            EditPlan plan = repair.plan();
            List<MaterializedEdit> steps = new ArrayList<>(plan.steps().size());
            List<Finding> findings = new ArrayList<>(solved.findings().findings());
            for (int index = 0; index < plan.steps().size(); index++) {
                EditStep step = plan.steps().get(index);
                if (step.applicability() != Applicability.machineApplicable) {
                    findings.add(noMachineRepairStep(index));
                    return ApplyReport.refused(FindingBundle.of(findings));
                }
                MaterializedEdit materialized = materializer
                        .materialize(step.edit(), request.siting().placement().orientation(), materializeContext());
                if (materialized == null) {
                    findings.add(materializationFailure(index, step.edit()));
                    return ApplyReport.refused(FindingBundle.of(findings));
                }
                findings.addAll(materialized.findings());
                steps.add(materialized);
            }
            findings.addAll(BuildGates.evaluate(plan, steps, policy, claims, request.source()));
            CuboidSet touched = BuildGates.touched(plan);
            if (!findings.isEmpty()) {
                return ApplyReport.refused(FindingBundle.of(findings));
            }
            if (request.dry()) {
                return new ApplyReport(ApplyReport.Status.committed, List.of(), List.of(), FindingBundle.empty());
            }
            return commit(plan, steps, touched);
        }

        private ApplyReport commit(EditPlan plan, List<MaterializedEdit> steps, CuboidSet touched) {
            List<ApplyReport.Step> outcomes = new ArrayList<>(steps.size());
            List<ItemStack> consumed = new ArrayList<>();
            List<Finding> findings = new ArrayList<>();
            Map<LatticePoint, BlockPlacement> preImage;
            try (EditTransaction transaction = EditTransaction.open(level, touched)) {
                boolean failed = false;
                for (int index = 0; index < steps.size(); index++) {
                    MaterializedEdit step = steps.get(index);
                    List<ItemStack> extracted = extract(step.items());
                    if (extracted == null) {
                        findings.add(itemShortageAtCommit(index, step));
                        failed = true;
                        break;
                    }
                    consumed.addAll(extracted);
                    ApplyReport.Step outcome = transaction.apply(index, step.placements());
                    outcomes.add(outcome);
                    if (outcome.failed()) {
                        findings.add(stepFailure(index, plan.steps().get(index).edit(), outcome.reason()));
                        failed = true;
                        break;
                    }
                }
                if (failed) {
                    return rolledBack(transaction, outcomes, consumed, findings, touched);
                }
                preImage = transaction.preImage();
            }
            ApplyReport committed = new ApplyReport(
                ApplyReport.Status.committed,
                outcomes,
                consumed,
                FindingBundle.of(findings)
            );
            journal.record(preImage);
            return recheck(committed);
        }

        private ApplyReport rolledBack(
            EditTransaction transaction,
            List<ApplyReport.Step> outcomes,
            List<ItemStack> consumed,
            List<Finding> findings,
            CuboidSet touched
        ) {
            List<ApplyReport.Step> restored = transaction.rollback(outcomes);
            List<ApplyReport.Step> steps = aligned(outcomes, restored);
            boolean incomplete = false;
            for (ApplyReport.Step step : restored) {
                if (step.failed()) {
                    incomplete = true;
                    findings.add(
                        BuildFindings.at(
                            BuildCodes.incompleteRollbackEntry,
                            Phase.settle,
                            "step " + step.index() + " could not be restored: " + step.reason(),
                            touched.bounds()
                        )
                    );
                }
            }
            List<Finding> returned = BuildReturns
                    .insertAll(level, request.source(), consumed, touched.bounds(), Phase.settle);
            incomplete = incomplete || !returned.isEmpty();
            findings.addAll(returned);
            return new ApplyReport(
                incomplete ? ApplyReport.Status.partiallyRolledBack : ApplyReport.Status.rolledBack,
                steps,
                consumed,
                FindingBundle.of(findings)
            );
        }

        private ApplyReport recheck(ApplyReport committed) {
            CaptureResult fresh = capture.capture(
                new CaptureRequest(bounds, policy(blueprint, request.siting())),
                request.siting().placement().orientation()
            );
            if (!(fresh instanceof CaptureResult.Captured captured)) {
                return append(
                    committed,
                    BuildFindings.at(
                        BuildCodes.recheckNotFormedEntry,
                        Phase.check,
                        "the post-build capture is unavailable",
                        bounds
                    )
                );
            }
            try (SnapshotLease lease = captured.lease()) {
                MatchResult checked = check(lease, claims(bounds, request.siting()));
                if (matched(checked)) {
                    return committed;
                }
                return append(
                    committed,
                    BuildFindings.at(
                        BuildCodes.recheckNotFormedEntry,
                        Phase.check,
                        "the post-build check is still " + describe(checked),
                        bounds
                    )
                );
            }
        }

        private MatchResult check(SnapshotLease lease, @Nullable ClaimView claims) {
            StructureAttempt draft = kernel.structures()
                    .begin(world(), new StructureSpec(owner, request.siting(), blueprint.id()));
            try {
                return kernel.check(
                    MatchRequest.of(blueprint, request.siting(), lease, draft.id(), owner, Budget.unbounded(), claims)
                );
            } finally {
                abort(draft);
            }
        }

        private @Nullable ClaimView claims(Cuboid box, Siting siting) {
            ClaimLedger ledger = kernel.ledger();
            if (ledger == null) {
                return null;
            }
            ClaimView view = ledger.read(box);
            List<ClaimRow> kept = new ArrayList<>(view.rows().size());
            for (ClaimRow row : view.rows()) {
                if (!row.siting().equals(siting)) {
                    kept.add(row);
                }
            }
            return new ClaimView(kept, view.revision(), view.available());
        }

        private World world() {
            var location = level.dimension().location();
            return World.of(NamespacedId.of(location.getNamespace(), location.getPath()));
        }

        private MaterializeContext materializeContext() {
            return new MaterializeContext(level);
        }

        private @Nullable MatchOutcome outcome(MatchResult result) {
            return switch (result) {
                case MatchResult.Admitted admitted -> admitted.outcome();
                case MatchResult.Formed formed -> formed.outcome();
                case MatchResult.Undecided undecided -> undecided.partial();
                case MatchResult.Failed failed -> failed.outcome();
                case MatchResult.Dropped ignored -> null;
            };
        }

        private @Nullable List<ItemStack> extract(List<ItemRequirement> requirements) {
            List<ItemStack> extracted = new ArrayList<>(requirements.size());
            for (ItemRequirement requirement : requirements) {
                ItemStack taken = request.source()
                        .extract(requirement.template(), requirement.count(), ExtractionMode.execute);
                if (taken.getCount() < requirement.count()) {
                    if (!taken.isEmpty()) {
                        request.source().insert(taken, ExtractionMode.execute);
                    }
                    for (ItemStack stack : extracted) {
                        request.source().insert(stack, ExtractionMode.execute);
                    }
                    return null;
                }
                extracted.add(taken);
            }
            return extracted;
        }

        private Finding noMachineRepair(MatchResult checked) {
            return BuildFindings.at(
                BuildCodes.noMachineRepairEntry,
                Phase.check,
                "the check is " + describe(checked) + " and no machine-applicable repair exists",
                bounds,
                "pick a repair yourself or adjust the blueprint"
            );
        }

        private Finding noMachineRepairStep(int index) {
            return BuildFindings.at(
                BuildCodes.noMachineRepairEntry,
                Phase.check,
                "plan step " + index + " is not machine-applicable",
                bounds
            );
        }

        private Finding materializationFailure(int index, Edit edit) {
            return BuildFindings.at(
                BuildCodes.materializationFailureEntry,
                Phase.check,
                "plan step " + index + " (" + describe(edit) + ") cannot be materialized",
                BuildGates.cellsOf(edit).bounds(),
                "an unknown block, property or value, or a state that cannot express the siting orientation"
            );
        }

        private Finding stepFailure(int index, Edit edit, @Nullable String reason) {
            return BuildFindings.at(
                BuildCodes.stepFailureEntry,
                Phase.settle,
                "plan step " + index + " failed" + (reason == null ? "" : ": " + reason),
                BuildGates.cellsOf(edit).bounds()
            );
        }

        private Finding itemShortageAtCommit(int index, MaterializedEdit step) {
            return BuildFindings.at(
                BuildCodes.itemShortageEntry,
                Phase.check,
                "the item source ran dry at plan step " + index,
                BuildFindings.bounds(List.of(step.placements()))
            );
        }

    }

    /** A matched check: a settled instance answers {@code Formed}, an unsettled one {@code Admitted}. */
    private static boolean matched(MatchResult result) {
        return result instanceof MatchResult.Formed || result instanceof MatchResult.Admitted;
    }

    private ApplyReport append(ApplyReport report, Finding finding) {
        List<Finding> findings = new ArrayList<>(report.findings().findings());
        findings.add(finding);
        return new ApplyReport(report.status(), report.steps(), report.itemsConsumed(), FindingBundle.of(findings));
    }

    /** One step per plan index: a restored step replaces the outcome the write reported. */
    private static List<ApplyReport.Step> aligned(List<ApplyReport.Step> outcomes, List<ApplyReport.Step> restored) {
        Map<Integer, ApplyReport.Step> byIndex = new TreeMap<>();
        for (ApplyReport.Step step : restored) {
            byIndex.put(step.index(), step);
        }
        List<ApplyReport.Step> steps = new ArrayList<>(outcomes.size());
        for (ApplyReport.Step step : outcomes) {
            steps.add(byIndex.getOrDefault(step.index(), step));
        }
        return List.copyOf(steps);
    }

    private ApplyReport withFinding(SolveResult solved, Finding finding) {
        List<Finding> findings = new ArrayList<>(solved.findings().findings());
        findings.add(finding);
        return ApplyReport.refused(FindingBundle.of(findings));
    }

    private static @Nullable Remedy pick(SolveResult solved) {
        for (Remedy remedy : solved.remedies()) {
            if (remedy instanceof Remedy.Repair repair && repair.applicability() == Applicability.machineApplicable) {
                return repair;
            }
        }
        return null;
    }

    private static SnapshotPolicy policy(Blueprint blueprint, Siting siting) {
        ReadScope reads = blueprint.reads();
        List<EntityWindow> windows = new ArrayList<>(reads.windows().size());
        for (EntityWindow window : reads.windows()) {
            Cuboid world = siting.toWorld(window.cells());
            if (world != null) {
                windows.add(new EntityWindow(world, window.type()));
            }
        }
        return new SnapshotPolicy(maxElements, SnapshotPolicy.defaultMaxDepth, reads.kinds(), reads.traits(), windows);
    }

    private static CaptureReport reportOf(CaptureResult result) {
        return switch (result) {
            case CaptureResult.TooLarge tooLarge -> tooLarge.report();
            case CaptureResult.Unavailable unavailable -> unavailable.report();
            case CaptureResult.Captured captured -> captured.report();
        };
    }

    private static String describe(MatchResult result) {
        return switch (result) {
            case MatchResult.Admitted ignored -> "admitted";
            case MatchResult.Formed ignored -> "formed";
            case MatchResult.Undecided undecided -> "undecided (" + undecided.reason() + ")";
            case MatchResult.Failed ignored -> "failed";
            case MatchResult.Dropped ignored -> "dropped";
        };
    }

    private static String describe(Edit edit) {
        return switch (edit) {
            case Edit.Place place -> "place " + place.type().text();
            case Edit.Remove ignored -> "remove";
            case Edit.Replace replace -> "replace " + replace.type().text();
            case Edit.SetTrait trait -> "setTrait " + trait.trait().key().id().text();
        };
    }

    private static void abort(StructureAttempt draft) {
        try {
            draft.abort();
        } catch (IllegalStateException ignored) {
            // the manager already closed it
        }
    }
}
