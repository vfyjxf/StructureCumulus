package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.mc.CaptureReports;
import dev.vfyjxf.cumulus.mc.LevelCapture;
import dev.vfyjxf.cumulus.mc.SetPresence;
import dev.vfyjxf.cumulus.mc.StructureSet;
import dev.vfyjxf.cumulus.mc.StructureSetHost;
import dev.vfyjxf.cumulus.mc.StructureSetResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The set build: presence, links and one shared item source over the members in declaration order.
 *
 * <p>Each member runs the single structure's build flow; a member that fails tears the members this run
 * already wrote down through their own pre-images in reverse commit order, returns what they consumed
 * and drops the run's undo entry. Build is not settle: claims appear only through the {@code --settle}
 * chain, which re-captures the set region and runs {@link StructureSetHost} over the whole set.
 */
public final class SetBuildHost {

    private final LevelCapture capture;
    private final StructureKernel kernel;
    private final OwnerId owner;

    public SetBuildHost(LevelCapture capture, StructureKernel kernel, OwnerId owner) {
        this.capture = Objects.requireNonNull(capture, "capture");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public StructureSetResult run(SetBuildRequest request, SnapshotLease lease, Budget budget) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(budget, "budget");
        StructureSet set = request.set();
        List<Finding> setFindings = new ArrayList<>(StructureSet.validate(set));
        Map<NamespacedId, MatchOutcome> outcomes = new LinkedHashMap<>();
        Map<NamespacedId, List<ClaimRow>> claims = new LinkedHashMap<>();
        List<NamespacedId> settled = new ArrayList<>();
        List<NamespacedId> rolledBack = new ArrayList<>();
        List<Finding> rollbackFailures = new ArrayList<>();
        Map<NamespacedId, ApplyReport> builds = new LinkedHashMap<>();
        if (hasErrors(setFindings)) {
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
        }
        if (!lease.isCurrent()) {
            setFindings.add(staleLease());
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
        }
        SetPresence.Budget setBudget = new SetPresence.Budget(budget);
        for (StructureSet.Satellite satellite : SetPresence.requiredFirst(set.satellites())) {
            SetPresence.Probe probe = SetPresence.probe(satellite, lease.view(), setBudget);
            if (probe.state() == SetPresence.State.unknown) {
                setFindings.add(SetPresence.unreadable(satellite, probe));
                return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
            }
        }
        for (StructureSet.Satellite satellite : set.satellites()) {
            setFindings.addAll(StructureSet.linkFindings(set, satellite));
        }
        if (hasErrors(setFindings)) {
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
        }
        BuildJournal journal = new BuildJournal(capture.level(), request.player().getUUID());
        List<Written> written = new ArrayList<>();
        boolean failed = false;
        for (Member member : members(set)) {
            int before = journal.commitOrder().size();
            ApplyReport report = buildOne(member, request, journal);
            builds.put(member.key(), report);
            setFindings.addAll(report.findings().findings());
            if (journal.commitOrder().size() > before) {
                written.add(new Written(member.key(), report));
            }
            if (report.status() != ApplyReport.Status.committed) {
                failed = true;
                break;
            }
        }
        if (failed) {
            tearDown(journal, written, builds, request.source(), setFindings, rollbackFailures, rolledBack);
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
        }
        if (!request.settle() || request.dry() || hasErrors(setFindings)) {
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
        }
        StructureSetResult settleResult = settle(set, lease, setBudget);
        outcomes.putAll(settleResult.outcomes());
        claims.putAll(settleResult.claims());
        settled.addAll(settleResult.settled());
        setFindings.addAll(settleResult.setFindings());
        rollbackFailures.addAll(settleResult.rollbackFailures());
        if (!settleResult.formed()) {
            tearDown(journal, written, builds, request.source(), setFindings, rollbackFailures, rolledBack);
        }
        rolledBack.addAll(settleResult.rolledBack());
        return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
    }

    private ApplyReport buildOne(Member member, SetBuildRequest request, BuildJournal journal) {
        BuildExecutor executor = new BuildExecutor(capture, kernel, owner, request.policy());
        BuildRequest build = new BuildRequest(
            member.blueprint().id(),
            member.siting(),
            request.source(),
            request.policy(),
            request.dry(),
            null,
            request.player()
        );
        return executor.run(member.blueprint(), build, journal);
    }

    /** The settle chain: the set layer's own check-all and settle-all over the written set. */
    private StructureSetResult settle(StructureSet set, SnapshotLease lease, SetPresence.Budget budget) {
        CaptureResult fresh = capture.capture(new CaptureRequest(lease.view().bounds(), lease.view().policy()), null);
        if (!(fresh instanceof CaptureResult.Captured captured)) {
            return new StructureSetResult(
                Map.of(),
                CaptureReports.relay(reportOf(fresh), kernel.catalogs().codes()).findings(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
            );
        }
        try (SnapshotLease settleLease = captured.lease()) {
            return new StructureSetHost(kernel).run(set, settleLease, owner, budget.remaining(), this::draft);
        }
    }

    private StructureAttempt draft(StructureSpec spec) {
        return kernel.structures().begin(world(), spec);
    }

    private void tearDown(
        BuildJournal journal,
        List<Written> written,
        Map<NamespacedId, ApplyReport> builds,
        ItemSource source,
        List<Finding> setFindings,
        List<Finding> rollbackFailures,
        List<NamespacedId> rolledBack
    ) {
        List<Map<LatticePoint, BlockPlacement>> preImages = journal.commitOrder();
        boolean restored = true;
        for (int index = preImages.size() - 1; index >= 0; index--) {
            Written member = written.get(index);
            Map<LatticePoint, BlockPlacement> preImage = preImages.get(index);
            Cuboid bounds = Cells.bounds(preImage.keySet());
            String failure = restore(preImage, bounds);
            List<Finding> findings = new ArrayList<>();
            if (failure != null) {
                restored = false;
                findings.add(
                    BuildFindings.at(
                        BuildCodes.incompleteRollbackEntry,
                        Phase.settle,
                        "the pre-image of " + member.key().text() + " could not be restored: " + failure,
                        bounds
                    )
                );
            }
            findings.addAll(
                BuildReturns.insertAll(capture.level(), source, member.report().itemsConsumed(), bounds, Phase.settle)
            );
            setFindings.addAll(findings);
            rollbackFailures.addAll(findings);
            builds.put(member.key(), tornDown(member.report(), findings));
            rolledBack.add(member.key());
        }
        if (restored) {
            journal.forget();
        }
    }

    /** Restores one member's pre-image as its own transaction and answers null or the failure reason. */
    private @Nullable String restore(Map<LatticePoint, BlockPlacement> preImage, @Nullable Cuboid bounds) {
        if (bounds == null) {
            return null;
        }
        Map<LatticePoint, BlockPlacement> reversed = new LinkedHashMap<>();
        List<LatticePoint> cells = new ArrayList<>(preImage.keySet());
        for (int index = cells.size() - 1; index >= 0; index--) {
            LatticePoint cell = cells.get(index);
            reversed.put(cell, preImage.get(cell));
        }
        try (EditTransaction transaction = EditTransaction.open(capture.level(), CuboidSet.of(bounds))) {
            ApplyReport.Step step = transaction.apply(0, reversed);
            return step.failed() ? step.reason() : null;
        }
    }

    private static ApplyReport tornDown(ApplyReport report, List<Finding> findings) {
        List<ApplyReport.Step> steps = new ArrayList<>(report.steps().size());
        for (ApplyReport.Step step : report.steps()) {
            steps.add(
                step.outcome() == ApplyReport.Outcome.applied
                        ? new ApplyReport.Step(step.index(), ApplyReport.Outcome.restored, step.reason())
                        : step
            );
        }
        List<Finding> all = new ArrayList<>(report.findings().findings());
        all.addAll(findings);
        return new ApplyReport(
            findings.isEmpty() ? ApplyReport.Status.rolledBack : ApplyReport.Status.partiallyRolledBack,
            steps,
            report.itemsConsumed(),
            FindingBundle.of(all)
        );
    }

    private static List<Member> members(StructureSet set) {
        List<Member> members = new ArrayList<>(set.satellites().size() + 1);
        members.add(new Member(set.root().id(), set.root(), set.rootSiting()));
        for (StructureSet.Satellite satellite : set.satellites()) {
            members.add(new Member(satellite.id(), satellite.blueprint(), satellite.siting()));
        }
        return members;
    }

    private World world() {
        var location = capture.level().dimension().location();
        return World.of(NamespacedId.of(location.getNamespace(), location.getPath()));
    }

    private static CaptureReport reportOf(CaptureResult result) {
        return switch (result) {
            case CaptureResult.TooLarge tooLarge -> tooLarge.report();
            case CaptureResult.Unavailable unavailable -> unavailable.report();
            case CaptureResult.Captured captured -> captured.report();
        };
    }

    private static boolean hasErrors(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity() == Severity.error) {
                return true;
            }
        }
        return false;
    }

    private static Finding staleLease() {
        return Finding.builder(Codes.snapshotStale).phase(Phase.check)
                .message("the lease is not current; the set was not built").build();
    }

    private static StructureSetResult result(
        Map<NamespacedId, MatchOutcome> outcomes,
        List<Finding> setFindings,
        List<NamespacedId> settled,
        List<NamespacedId> rolledBack,
        List<Finding> rollbackFailures,
        Map<NamespacedId, List<ClaimRow>> claims,
        Map<NamespacedId, ApplyReport> builds
    ) {
        return new StructureSetResult(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, builds);
    }

    private record Member(NamespacedId key, Blueprint blueprint, Siting siting) {}

    private record Written(NamespacedId key, ApplyReport report) {}
}
