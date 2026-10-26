package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Merge;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.AggregateComputation;
import dev.vfyjxf.cumulus.kernel.structure.AggregateContext;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.CheckResult;
import dev.vfyjxf.cumulus.kernel.structure.Computation;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.Plan;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.structure.SectionComputation;
import dev.vfyjxf.cumulus.kernel.structure.SectionContext;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
/** The three operations: check, settle, derive. */
public final class MatchEngine {

    public static MatchResult check(MatchRequest request) {
        Objects.requireNonNull(request, "request");
        Phase phase = Phase.CHECK;
        if (!request.lease().isCurrent()) {
            return dropped(phase, "the snapshot is no longer current");
        }
        Session session = new Session(request);
        Plan plan = request.blueprint().plan();
        if (plan.checks().isEmpty()) {
            return undecided(phase, UndecidedReason.NO_CHECKS, session);
        }

        for (SectionComputation<?> computation : plan.sections()) {
            if (session.cancelled()) {
                return undecided(phase, UndecidedReason.CANCELLED, session);
            }
            if (computation.output().merge() == null) {
                session.report(code(Codes.DESIGN_DEFECT, phase,
                        "section computation " + computation.id() + " has a single-writer key",
                        Locus.local(computation.region())));
                return undecided(phase, UndecidedReason.DESIGN_DEFECT, session);
            }
            for (SectionKey section : sectionsOf(computation.region(), request.lease().view())) {
                if (session.cancelled()) {
                    return undecided(phase, UndecidedReason.CANCELLED, session);
                }
                SectionSlot slot = request.lease().view().slot(section);
                if (!slot.readable()) {
                    session.refuse(computation.output(), section, computation.region());
                    continue;
                }
                CuboidSet slice = computation.region()
                        .intersect(sectionBounds(section, request.lease().view().cellSize()));
                if (slice.isEmpty()) {
                    continue;
                }
                if (!session.charge(slice.volume(), 1)) {
                    return undecided(phase, UndecidedReason.BUDGET_EXHAUSTED, session);
                }
                SectionContext ctx = new SectionCtx(request, session, section, slice);
                try {
                    Outcome<?> result = computation.compute(ctx);
                    if (result instanceof Outcome.Found<?> found) {
                        session.contribute(computation.output(), section, found.value());
                    } else {
                        session.refuse(computation.output(), section, computation.region());
                    }
                } catch (RuntimeException failure) {
                    session.report(extensionFailure(phase, computation.id(), failure));
                    session.refuse(computation.output(), section, computation.region());
                }
            }
        }
        for (SectionComputation<?> computation : plan.sections()) {
            session.fold(computation.output(), phase);
        }

        for (AggregateComputation<?> computation : plan.aggregates()) {
            if (session.cancelled()) {
                return undecided(phase, UndecidedReason.CANCELLED, session);
            }
            int before = session.findings.size();
            AggregateContext ctx = new AggregateCtx(request, session);
            try {
                Outcome<?> result = computation.compute(ctx);
                if (result instanceof Outcome.Found<?> found) {
                    session.values.put(computation.output(), found.value());
                } else if (session.findings.size() == before) {
                    session.report(code(Codes.COMPUTATION_FAILED, phase,
                            "computation " + computation.id() + " produced no value",
                            Locus.local(computation.region())));
                }
            } catch (RuntimeException failure) {
                session.report(extensionFailure(phase, computation.id(), failure));
            }
        }

        List<Run> runs = new ArrayList<>();
        for (Check<?> check : plan.checks()) {
            if (session.cancelled()) {
                return undecided(phase, UndecidedReason.CANCELLED, session);
            }
            if (!session.chargeScope(check.reads(), request.lease().view())) {
                return undecided(phase, UndecidedReason.BUDGET_EXHAUSTED, session);
            }
            FindingBundle absent = missingKeys(request, session, check);
            if (!absent.isEmpty()) {
                session.findings.addAll(absent.findings());
                runs.add(new Run(check, new CheckResult(Reply.unreadable(absent), List.of(),
                        FindingBundle.empty())));
                continue;
            }
            FocusContext ctx = new EngineFocusContext(request, session, check);
            try {
                CheckResult result = check.run(ctx);
                session.absorb(check, result);
                runs.add(new Run(check, result));
            } catch (RuntimeException failure) {
                FindingBundle bundle = FindingBundle.of(List.of(
                        extensionFailure(phase, check.id(), failure)));
                session.findings.addAll(bundle.findings());
                runs.add(new Run(check, new CheckResult(Reply.unreadable(bundle), List.of(),
                        FindingBundle.empty())));
            }
        }

        if (!request.lease().isCurrent()) {
            return dropped(phase, "the snapshot changed while the attempt was running");
        }

        boolean mismatched = false;
        boolean unreadable = false;
        boolean abstained = false;
        for (Run run : runs) {
            switch (run.result().reply()) {
                case Reply.Mismatch mismatch -> {
                    mismatched = true;
                    session.findings.addAll(mismatch.findings().findings());
                }
                case Reply.Unreadable unknown -> {
                    unreadable = true;
                    session.findings.addAll(unknown.findings().findings());
                }
                case Reply.Match ignored -> {
                }
                case Reply.Abstain ignored -> abstained = true;
            }
            session.findings.addAll(run.result().notes().findings());
        }
        MatchOutcome outcome = session.outcome();
        if (mismatched) {
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        if (unreadable) {
            return new MatchResult.Undecided(phase, UndecidedReason.UNREADABLE,
                    FindingBundle.of(session.findings), outcome);
        }
        if (abstained) {
            return new MatchResult.Undecided(phase, UndecidedReason.ABSTAINED,
                    FindingBundle.of(session.findings), outcome);
        }

        boolean needsLedger = false;
        for (Bay bay : request.blueprint().bays()) {
            needsLedger |= !(bay.footprint() instanceof Footprint.None);
        }
        if (!needsLedger) {
            return new MatchResult.Admitted(phase, request.instance(), request.siting(), outcome,
                    0, false, FindingBundle.of(session.findings));
        }
        ClaimView claims = request.claims();
        if (claims == null || !claims.available()) {
            session.findings.add(code(Codes.LEDGER_UNAVAILABLE, phase,
                    "the ledger cannot answer; no claim prediction is possible", null));
            return new MatchResult.Undecided(phase, UndecidedReason.LEDGER_UNAVAILABLE,
                    FindingBundle.of(session.findings), outcome);
        }
        Finding invalid = validateBindings(request, outcome.bindings());
        if (invalid != null) {
            session.findings.add(invalid);
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        ClaimPlanner.Derived derived;
        try {
            derived = ClaimPlanner.derive(request.blueprint(), request.siting(), request.owner(),
                    outcome.bindings());
        } catch (IllegalArgumentException rejected) {
            session.findings.add(plannerFailure(phase, rejected,
                    Locus.local(request.blueprint().bayCells())));
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        List<Finding> conflicts = conflicts(phase, derived.rows(), claims.rows());
        if (!conflicts.isEmpty()) {
            session.findings.addAll(conflicts);
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        return new MatchResult.Admitted(phase, request.instance(), request.siting(), outcome,
                claims.revision(), true, FindingBundle.of(session.findings));
    }

    public static MatchResult settle(MatchRequest request, MatchResult.Admitted admitted,
                                     @Nullable ClaimLedger ledger, ContextStore contexts) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(admitted, "admitted");
        Objects.requireNonNull(contexts, "contexts");
        Phase phase = Phase.SETTLE;
        List<Finding> findings = new ArrayList<>(admitted.findings().findings());
        if (admitted.phase() != Phase.CHECK
                || !admitted.instance().equals(request.instance())
                || !admitted.siting().equals(request.siting())
                || !admitted.outcome().view().snapshotId().equals(request.lease().id())) {
            findings.add(code(Codes.DESIGN_DEFECT, phase,
                    "the admitted result does not belong to this request", null));
            return new MatchResult.Undecided(phase, UndecidedReason.DESIGN_DEFECT,
                    FindingBundle.of(findings), admitted.outcome());
        }
        if (!request.lease().isCurrent()) {
            findings.add(code(Codes.SNAPSHOT_STALE, phase,
                    "the snapshot is no longer current; nothing is written", null));
            return new MatchResult.Dropped(phase, FindingBundle.of(findings));
        }
        Finding invalid = validateBindings(request, admitted.outcome().bindings());
        if (invalid != null) {
            findings.add(invalid);
            return new MatchResult.Failed(phase, FindingBundle.of(findings));
        }
        ClaimPlanner.Derived derived;
        try {
            derived = ClaimPlanner.derive(request.blueprint(), request.siting(), request.owner(),
                    admitted.outcome().bindings());
        } catch (IllegalArgumentException rejected) {
            findings.add(plannerFailure(phase, rejected,
                    Locus.local(request.blueprint().bayCells())));
            return new MatchResult.Failed(phase, FindingBundle.of(findings));
        }
        if (derived.rows().isEmpty()) {
            StructureContext context = publish(contexts, request, admitted.outcome(), findings);
            long revision = ledger == null ? 0 : ledger.revision();
            return new MatchResult.Formed(phase, request.instance(), request.siting(),
                    admitted.outcome(), new ClaimSet(List.of(), revision),
                    context.revision(), revision, FindingBundle.of(findings));
        }
        if (ledger == null) {
            findings.add(code(Codes.LEDGER_UNAVAILABLE, phase,
                    "no ledger is installed; the claims cannot be written", null));
            return new MatchResult.Undecided(phase, UndecidedReason.LEDGER_UNAVAILABLE,
                    FindingBundle.of(findings), admitted.outcome());
        }
        ClaimVerdict verdict = ledger.settle(new SettleRequest(request.owner(), request.siting(),
                derived.rows(), admitted.ledgerRevision()));
        if (verdict instanceof ClaimVerdict.RevisionMoved) {
            Cuboid readBox = derived.footprint().bounds();
            ClaimView fresh = readBox == null ? ClaimView.unavailable() : ledger.read(readBox);
            if (!fresh.available()) {
                findings.add(code(Codes.LEDGER_UNAVAILABLE, phase,
                        "the ledger moved and cannot answer the re-read", null));
                return new MatchResult.Undecided(phase, UndecidedReason.LEDGER_UNAVAILABLE,
                        FindingBundle.of(findings), admitted.outcome());
            }
            List<Finding> retryConflicts = conflicts(phase, derived.rows(), fresh.rows());
            if (!retryConflicts.isEmpty()) {
                findings.addAll(retryConflicts);
                return new MatchResult.Failed(phase, FindingBundle.of(findings));
            }
            verdict = ledger.settle(new SettleRequest(request.owner(), request.siting(),
                    derived.rows(), fresh.revision()));
        }
        if (verdict instanceof ClaimVerdict.RevisionMoved moved) {
            findings.add(code(Codes.LEDGER_MOVED, phase,
                    "the ledger moved twice (" + moved.expected() + " -> " + moved.actual() + ")",
                    null));
            return new MatchResult.Undecided(phase, UndecidedReason.LEDGER_MOVED,
                    FindingBundle.of(findings), admitted.outcome());
        }
        if (verdict instanceof ClaimVerdict.Conflict conflict) {
            findings.addAll(conflict.findings().findings());
            return new MatchResult.Failed(phase, FindingBundle.of(findings));
        }
        ClaimVerdict.Settled settled = (ClaimVerdict.Settled) verdict;
        StructureContext context = publish(contexts, request, admitted.outcome(), findings);
        return new MatchResult.Formed(phase, request.instance(), request.siting(),
                admitted.outcome(), new ClaimSet(settled.rows(), settled.revision()),
                context.revision(), settled.revision(), FindingBundle.of(findings));
    }

    public static StructureContext derive(MatchResult.Formed formed,
                                          List<DerivationSpec> derivations,
                                          Catalog<Deriver<?>> derivers, ContextStore contexts) {
        Objects.requireNonNull(formed, "formed");
        Objects.requireNonNull(derivations, "derivations");
        Objects.requireNonNull(derivers, "derivers");
        Objects.requireNonNull(contexts, "contexts");
        StructureContext base = contexts.context(formed.instance());
        Map<ContextKey<?>, Object> values = new LinkedHashMap<>(base.values());
        List<Finding> findings = new ArrayList<>(formed.findings().findings());
        List<DerivationSpec> ordered = new ArrayList<>(derivations);
        ordered.sort(Comparator.comparingInt(DerivationSpec::order)
                .thenComparing(spec -> spec.deriver()));
        for (DerivationSpec spec : ordered) {
            Deriver<?> deriver = derivers.get(spec.deriver());
            if (deriver == null) {
                findings.add(code(Codes.DERIVER_MISSING, Phase.SETTLE,
                        "no deriver is registered as " + spec.deriver(), null));
                continue;
            }
            if (!deriver.output().id().equals(spec.output())) {
                findings.add(code(Codes.DERIVER_FAILED, Phase.SETTLE,
                        "deriver " + spec.deriver() + " produces " + deriver.output().id()
                                + ", not " + spec.output(), null));
                continue;
            }
            if (deriver.output().retention() != Retention.INSTANCE) {
                findings.add(code(Codes.DERIVER_FAILED, Phase.SETTLE,
                        "deriver " + spec.deriver() + " must produce an INSTANCE key", null));
                continue;
            }
            if (!values.keySet().containsAll(deriver.requires())) {
                findings.add(code(Codes.KEY_ABSENT, Phase.SETTLE,
                        "deriver " + spec.deriver() + " misses a required key", null));
                continue;
            }
            MapContextView view = new MapContextView(values);
            int before = findings.size();
            DeriveCtx ctx = new DeriveCtx(base, view, findings);
            try {
                Outcome<?> result = deriver.derive(ctx, spec.arguments());
                if (result instanceof Outcome.Found<?> found) {
                    values.put(deriver.output(), found.value());
                } else if (findings.size() == before) {
                    findings.add(code(Codes.DERIVER_FAILED, Phase.SETTLE,
                            "deriver " + spec.deriver() + " produced no value", null));
                }
            } catch (RuntimeException failure) {
                findings.add(extensionFailure(Phase.SETTLE, spec.deriver(), failure));
            }
        }
        Map<ContextKey<?>, Object> published = new LinkedHashMap<>();
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            if (entry.getKey().retention() == Retention.INSTANCE) {
                published.put(entry.getKey(), entry.getValue());
            }
        }
        return contexts.publish(formed.instance(), published, FindingBundle.of(findings),
                formed.outcome().spend());
    }

    private static StructureContext publish(ContextStore contexts, MatchRequest request,
                                            MatchOutcome outcome, List<Finding> findings) {
        Map<ContextKey<?>, Object> published = new LinkedHashMap<>();
        for (Map.Entry<ContextKey<?>, Object> entry : outcome.values().entrySet()) {
            if (entry.getKey().retention() == Retention.INSTANCE) {
                published.put(entry.getKey(), entry.getValue());
            }
        }
        return contexts.publish(request.instance(), published, FindingBundle.of(findings),
                outcome.spend());
    }

    private static @Nullable Finding validateBindings(MatchRequest request,
                                                      List<BayBinding> bindings) {
        Map<NamespacedId, BayBinding> byBay = new LinkedHashMap<>();
        for (BayBinding binding : bindings) {
            Bay bay = request.blueprint().bay(binding.bay().id());
            if (bay == null || !bay.equals(binding.bay())) {
                return code(Codes.BINDING_INVALID, Phase.CHECK,
                        "a binding names a bay this blueprint does not declare", null);
            }
            byBay.put(binding.bay().id(), binding);
        }
        for (Bay bay : request.blueprint().bays()) {
            BayBinding binding = byBay.get(bay.id());
            if (binding == null) {
                return code(Codes.BINDING_INVALID, Phase.CHECK,
                        "no binding for bay " + bay.id(), null);
            }
            CuboidSet bayWorld = request.siting().toWorld(bay.cells());
            if (bayWorld == null) {
                return code(Codes.LOCUS_UNMAPPED, Phase.CHECK,
                        "bay " + bay.id() + " leaves the lattice", null);
            }
            CuboidSet bound = CuboidSet.empty();
            for (OccupantSnapshot subject : binding.subjects()) {
                if (!subject.ref().snapshot().equals(request.lease().id())
                        || subject.ref().frame() != 0) {
                    return code(Codes.BINDING_INVALID, Phase.CHECK,
                            "a subject is not from this capture's root frame", null);
                }
                if (!bayWorld.containsAll(subject.footprint().asSet())) {
                    return code(Codes.BINDING_INVALID, Phase.CHECK,
                            "a subject leaves bay " + bay.id(), null);
                }
                if (!subject.isAir()) {
                    bound = bound.union(subject.footprint().asSet());
                }
            }
            if (bay.coverage() == Coverage.ONE_SUBJECT
                    && !bound.contentEquals(bayWorld)) {
                return code(Codes.BINDING_INVALID, Phase.CHECK,
                        "ONE_SUBJECT bay " + bay.id() + " is not equal to its bound footprint",
                        null);
            }
            if (bay.sharing() == Sharing.EXCLUSIVE && bay.footprint() instanceof Footprint.Bound
                    && bound.isEmpty()) {
                return code(Codes.BOUND_EMPTY, Phase.CHECK,
                        "exclusive bay " + bay.id() + " bound no cell", null);
            }
        }
        return null;
    }

    private static FindingBundle missingKeys(MatchRequest request, Session session, Check<?> check) {
        List<Finding> missing = new ArrayList<>();
        for (ContextKey<?> key : check.expectation().meta().requires()) {
            if (!session.values.containsKey(key)) {
                missing.add(Finding.builder(Codes.KEY_ABSENT)
                        .phase(Phase.CHECK)
                        .message("required context key " + key.id() + " is absent")
                        .label(Label.primary(Locus.key(request.instance(), key.id()),
                                "no value for " + key.id()))
                        .build());
            }
        }
        return FindingBundle.of(missing);
    }

    private static List<Finding> conflicts(Phase phase, List<ClaimRow> requested,
                                           List<ClaimRow> held) {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClaimRow row : requested) {
            for (ClaimRow incumbent : held) {
                if (ClaimMatrix.conflicts(row, incumbent)) {
                    String pair = row.bay() + "|" + incumbent.owner().id() + "|" + incumbent.bay();
                    if (seen.add(pair)) {
                        Locus locus = Locus.world(CuboidSet.of(Cuboid.of(row.cell().point())));
                        findings.add(Finding.builder(Codes.CLAIM_CONFLICT)
                                .phase(phase)
                                .message(incumbent.owner().id() + ":" + incumbent.bay()
                                        + " already holds cell " + row.cell().point())
                                .label(Label.primary(locus, "contested cell"))
                                .note("this bay asks " + row.sharing(), locus)
                                .note("the incumbent holds " + incumbent.sharing(), null)
                                .build());
                    }
                }
            }
        }
        return findings;
    }

    private static List<SectionKey> sectionsOf(CuboidSet cells, WorldView view) {
        TreeSet<SectionKey> keys = new TreeSet<>();
        int size = view.cellSize();
        for (Cuboid box : cells.boxes()) {
            int x0 = Math.floorDiv(box.min().x(), size);
            int y0 = Math.floorDiv(box.min().y(), size);
            int z0 = Math.floorDiv(box.min().z(), size);
            int x1 = Math.floorDiv(box.max().x() - 1, size);
            int y1 = Math.floorDiv(box.max().y() - 1, size);
            int z1 = Math.floorDiv(box.max().z() - 1, size);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        keys.add(new SectionKey(view.snapshotId().world(), view.frame(), x, y, z));
                    }
                }
            }
        }
        return List.copyOf(keys);
    }

    private static Cuboid sectionBounds(SectionKey key, int size) {
        long x0 = (long) key.x() * size;
        long y0 = (long) key.y() * size;
        long z0 = (long) key.z() * size;
        int minX = (int) Math.max(LatticePoint.MIN_XZ, Math.min(LatticePoint.MAX_XZ, x0));
        int minY = (int) Math.max(LatticePoint.MIN_Y, Math.min(LatticePoint.MAX_Y, y0));
        int minZ = (int) Math.max(LatticePoint.MIN_XZ, Math.min(LatticePoint.MAX_XZ, z0));
        int maxX = (int) Math.max(LatticePoint.MIN_XZ, Math.min(LatticePoint.MAX_XZ, x0 + size));
        int maxY = (int) Math.max(LatticePoint.MIN_Y, Math.min(LatticePoint.MAX_Y, y0 + size));
        int maxZ = (int) Math.max(LatticePoint.MIN_XZ, Math.min(LatticePoint.MAX_XZ, z0 + size));
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static MatchResult dropped(Phase phase, String message) {
        return new MatchResult.Dropped(phase, FindingBundle.of(List.of(
                code(Codes.SNAPSHOT_STALE, phase, message, null))));
    }

    private static MatchResult undecided(Phase phase, UndecidedReason reason, Session session) {
        return new MatchResult.Undecided(phase, reason,
                FindingBundle.of(session.findings), session.outcome());
    }

    private static Finding plannerFailure(Phase phase, IllegalArgumentException rejected,
                                          Locus locus) {
        String message = rejected.getMessage() == null ? "claim planning failed"
                : rejected.getMessage();
        CodeEntry entry = message.contains("SC-SPC-0001") ? Codes.LOCUS_UNMAPPED
                : message.contains("SC-CLM-0005") ? Codes.BOUND_EMPTY
                : Codes.BINDING_INVALID;
        return code(entry, phase, message, locus);
    }

    private static Finding code(CodeEntry entry, Phase phase,
                                String message, @Nullable Locus locus) {
        Finding.Builder builder = Finding.builder(entry).phase(phase).message(message);
        if (locus != null) {
            builder.label(Label.primary(locus, message));
        }
        return builder.build();
    }

    private static Finding extensionFailure(Phase phase, NamespacedId unit, RuntimeException failure) {
        String message = "extension " + unit + " failed: " + failure.getClass().getSimpleName();
        return Finding.builder(Codes.EXTENSION_FAILED)
                .phase(phase)
                .message(message)
                .note(failure.getMessage() == null ? "no message" : failure.getMessage())
                .build();
    }

    private record Run(Check<?> check, CheckResult result) {
    }

    private static final class Session {

        private final MatchRequest request;
        private final Map<ContextKey<?>, TreeMap<SectionKey, Object>> contributions =
                new LinkedHashMap<>();
        private final Set<ContextKey<?>> refused = new HashSet<>();
        private final Map<ContextKey<?>, Object> values = new LinkedHashMap<>();
        private final List<Finding> findings = new ArrayList<>();
        private final Map<NamespacedId, LinkedHashMap<OccupantRef, OccupantSnapshot>> subjects =
                new LinkedHashMap<>();
        private ReadScope reads = ReadScope.none();
        private Spend spend = Spend.ZERO;

        private Session(MatchRequest request) {
            this.request = request;
            for (Bay bay : request.blueprint().bays()) {
                subjects.put(bay.id(), new LinkedHashMap<>());
            }
            for (Computation<?> computation : request.blueprint().computations()) {
                reads = reads.union(ReadScope.cells(computation.region()));
                reads = reads.withKeys(computation.inputs());
            }
        }

        private boolean cancelled() {
            return request.cancel().isCancelled();
        }

        private boolean charge(long cells, int sections) {
            int cellCount = (int) Math.min(Integer.MAX_VALUE, cells);
            Spend next = spend.plus(new Spend(cellCount, sections));
            if (!request.budget().admits(next)) {
                return false;
            }
            spend = next;
            return true;
        }

        private boolean chargeScope(ReadScope scope, WorldView view) {
            reads = reads.union(scope);
            int sections = sectionsOf(scope.cells(), view).size();
            for (var window : scope.windows()) {
                sections += sectionsOf(window.cells().asSet(), view).size();
            }
            long cells = scope.cells().volume();
            for (var window : scope.windows()) {
                cells += window.cells().volume();
            }
            return charge(cells, sections);
        }

        private Budget remaining() {
            long cells = Math.max(0, (long) request.budget().cells() - spend.cells());
            long sections = Math.max(0, (long) request.budget().sections() - spend.sections());
            return new Budget((int) Math.min(Integer.MAX_VALUE, cells),
                    (int) Math.min(Integer.MAX_VALUE, sections));
        }

        private void report(Finding finding) {
            findings.add(finding);
        }

        private void contribute(ContextKey<?> key, SectionKey section, Object value) {
            contributions.computeIfAbsent(key, ignored -> new TreeMap<>()).put(section, value);
        }

        private void refuse(ContextKey<?> key, SectionKey section, CuboidSet region) {
            if (refused.add(key)) {
                findings.add(code(Codes.FOLD_REFUSED, Phase.CHECK,
                        "a section of " + key.id() + " is unreadable", Locus.local(region)));
            }
        }

        @SuppressWarnings("unchecked")
        private void fold(ContextKey<?> key, Phase phase) {
            if (refused.contains(key)) {
                return;
            }
            TreeMap<SectionKey, Object> parts = contributions.get(key);
            if (parts == null || parts.isEmpty()) {
                if (key.whenAbsent() != null) {
                    values.put(key, key.whenAbsent());
                }
                return;
            }
            Merge<Object> merge =
                    (Merge<Object>) key.merge();
            if (merge == null) {
                findings.add(code(Codes.DESIGN_DEFECT, phase,
                        "a contributed key has no merge: " + key.id(), null));
                return;
            }
            Object combined = null;
            for (Object part : parts.values()) {
                combined = combined == null ? part : merge.combine(combined, part);
            }
            values.put(key, combined);
        }

        private void absorb(Check<?> check, CheckResult result) {
            Map<OccupantRef, OccupantSnapshot> baySubjects =
                    subjects.computeIfAbsent(check.bay().id(), ignored -> new LinkedHashMap<>());
            for (OccupantSnapshot subject : result.subjects()) {
                if (!subject.isAir()) {
                    baySubjects.put(subject.ref(), subject);
                }
            }
        }

        private MatchOutcome outcome() {
            List<BayBinding> bindings = new ArrayList<>();
            for (Bay bay : request.blueprint().bays()) {
                List<OccupantSnapshot> bound = new ArrayList<>(
                        subjects.getOrDefault(bay.id(), new LinkedHashMap<>()).values());
                bound.sort(Comparator.comparing(OccupantSnapshot::ref));
                bindings.add(new BayBinding(bay, bound));
            }
            return new MatchOutcome(request.lease().view(), values, bindings, reads, spend);
        }
    }

    private static final class SectionCtx implements SectionContext {

        private final MatchRequest request;
        private final Session session;
        private final SectionKey section;
        private final CuboidSet cells;

        private SectionCtx(MatchRequest request, Session session, SectionKey section,
                           CuboidSet cells) {
            this.request = request;
            this.session = session;
            this.section = section;
            this.cells = cells;
        }

        @Override
        public SectionKey section() {
            return section;
        }

        @Override
        public CuboidSet cells() {
            return cells;
        }

        @Override
        public WorldView view() {
            return request.lease().view();
        }

        @Override
        public Siting siting() {
            return request.siting();
        }

        @Override
        public @Nullable OccupantSnapshot occupantAt(LatticePoint local) {
            LatticePoint world = request.siting().toWorld(local);
            return world == null ? null : request.lease().view().occupant(world);
        }

        @Override
        public List<OccupantSnapshot> occupants() {
            CuboidSet world = request.siting().toWorld(cells);
            Cuboid bounds = world == null ? null : world.bounds();
            return bounds == null ? List.of()
                    : request.lease().view().occupants(bounds,
                            OccupantFilter.any());
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid local) {
            Cuboid world = request.siting().toWorld(local);
            return world == null ? List.of()
                    : request.lease().view().occupants(world,
                            OccupantFilter.any());
        }

        @Override
        public Budget remaining() {
            return session.remaining();
        }

        @Override
        public CancelToken cancel() {
            return request.cancel();
        }

        @Override
        public void report(Finding finding) {
            session.report(finding);
        }
    }

    private static final class AggregateCtx implements AggregateContext {

        private final MatchRequest request;
        private final Session session;

        private AggregateCtx(MatchRequest request, Session session) {
            this.request = request;
            this.session = session;
        }

        @Override
        public ContextView context() {
            return new MapContextView(session.values);
        }

        @Override
        public WorldView view() {
            return request.lease().view();
        }

        @Override
        public Siting siting() {
            return request.siting();
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid local) {
            Cuboid world = request.siting().toWorld(local);
            return world == null ? List.of()
                    : request.lease().view().occupants(world,
                            OccupantFilter.any());
        }

        @Override
        public Budget remaining() {
            return session.remaining();
        }

        @Override
        public CancelToken cancel() {
            return request.cancel();
        }

        @Override
        public void report(Finding finding) {
            session.report(finding);
        }
    }

    private static final class EngineFocusContext implements FocusContext {

        private final MatchRequest request;
        private final Session session;
        private final Check<?> check;
        private final MapContextView context;

        private EngineFocusContext(MatchRequest request, Session session, Check<?> check) {
            this.request = request;
            this.session = session;
            this.check = check;
            this.context = new MapContextView(session.values);
        }

        @Override
        public BayView bay() {
            return new BayView(check.bay().id(), check.bay().cells(), request.lease().view(),
                    request.siting());
        }

        @Override
        public WorldView view() {
            return request.lease().view();
        }

        @Override
        public Siting siting() {
            return request.siting();
        }

        @Override
        public InstanceId instance() {
            return request.instance();
        }

        @Override
        public CuboidSet cells() {
            return check.bay().cells();
        }

        @Override
        public Phase phase() {
            return Phase.CHECK;
        }

        @Override
        public Locus locus() {
            return Locus.local(check.bay().cells());
        }

        @Override
        public ContextView context() {
            return context;
        }

        @Override
        public Budget remaining() {
            return session.remaining();
        }

        @Override
        public CancelToken cancel() {
            return request.cancel();
        }

        @Override
        public @Nullable OccupantSnapshot occupantAt(LatticePoint local) {
            LatticePoint world = request.siting().toWorld(local);
            return world == null ? null : request.lease().view().occupant(world);
        }

        @Override
        public List<ClaimRow> claimsAt(LatticePoint local) {
            ClaimView claims = request.claims();
            if (claims == null || !claims.available()) {
                return List.of();
            }
            LatticePoint world = request.siting().toWorld(local);
            return world == null ? List.of() : claims.rowsIn(Cuboid.of(world));
        }
    }

    private static final class MapContextView implements ContextView {

        private final Map<ContextKey<?>, Object> values;
        private final Set<ContextKey<?>> keys;

        private MapContextView(Map<ContextKey<?>, Object> values) {
            this.values = values;
            TreeSet<ContextKey<?>> sorted = new TreeSet<>(Comparator
                    .comparing((ContextKey<?> key) -> key.id())
                    .thenComparing(key -> key.type().getName()));
            sorted.addAll(values.keySet());
            this.keys = Collections.unmodifiableSet(sorted);
        }

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            Object value = values.get(key);
            return value == null ? null : key.type().cast(value);
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            T value = get(key);
            return value == null ? Outcome.missing() : Outcome.found(value);
        }

        @Override
        public boolean has(ContextKey<?> key) {
            return values.containsKey(key);
        }

        @Override
        public Set<ContextKey<?>> keys() {
            return keys;
        }
    }

    private static final class DeriveCtx implements DeriveContext {

        private final StructureContext base;
        private final MapContextView view;
        private final List<Finding> findings;

        private DeriveCtx(StructureContext base, MapContextView view, List<Finding> findings) {
            this.base = base;
            this.view = view;
            this.findings = findings;
        }

        @Override
        public StructureContext base() {
            return base;
        }

        @Override
        public Budget remaining() {
            return Budget.unbounded();
        }

        @Override
        public CancelToken cancel() {
            return CancelToken.never();
        }

        @Override
        public void report(Finding finding) {
            findings.add(finding);
        }

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            return view.get(key);
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            return view.require(key);
        }

        @Override
        public boolean has(ContextKey<?> key) {
            return view.has(key);
        }

        @Override
        public Set<ContextKey<?>> keys() {
            return view.keys();
        }
    }

    private MatchEngine() {
    }
}
