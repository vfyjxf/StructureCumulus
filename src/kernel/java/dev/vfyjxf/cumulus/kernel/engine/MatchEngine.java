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
import dev.vfyjxf.cumulus.kernel.context.CachedContext;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
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
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        Phase phase = Phase.check;
        if (!request.lease().isCurrent()) {
            return dropped(phase, "the snapshot is no longer current");
        }
        Session session = new Session(request);
        Plan plan = request.blueprint().plan();
        if (plan.checks().isEmpty()) {
            return undecided(phase, UndecidedReason.noChecks, session);
        }

        for (SectionComputation<?> computation : plan.sections()) {
            if (session.cancelled()) {
                session.findings.add(code(Codes.cancelled, phase, "the attempt was cancelled", null));
                return undecided(phase, UndecidedReason.cancelled, session);
            }
            if (computation.output().merge() == null) {
                session.report(
                    code(
                        Codes.designDefect,
                        phase,
                        "section computation " + computation.id() + " has a single-writer key",
                        Locus.local(computation.region())
                    )
                );
                return undecided(phase, UndecidedReason.designDefect, session);
            }
            CuboidSet worldRegion = request.siting().toWorld(computation.region());
            if (worldRegion == null) {
                session.report(
                    code(
                        Codes.locusUnmapped,
                        phase,
                        "computation " + computation.id() + " leaves the lattice",
                        Locus.local(computation.region())
                    )
                );
                session.missing(computation.output(), computation.region(), true);
                continue;
            }
            for (SectionKey section : sectionsOf(worldRegion, request.lease().view())) {
                if (session.cancelled()) {
                    session.findings.add(code(Codes.cancelled, phase, "the attempt was cancelled", null));
                    return undecided(phase, UndecidedReason.cancelled, session);
                }
                CuboidSet worldSlice = worldRegion.intersect(sectionBounds(section, request.lease().view().cellSize()));
                if (worldSlice.isEmpty()) {
                    continue;
                }
                CuboidSet localSlice = request.siting().toLocal(worldSlice);
                if (localSlice == null) {
                    session.report(
                        code(
                            Codes.locusUnmapped,
                            phase,
                            "a section of " + computation.id() + " leaves the lattice",
                            Locus.local(computation.region())
                        )
                    );
                    session.missing(computation.output(), computation.region(), true);
                    continue;
                }
                SectionSlot slot = request.lease().view().slot(section);
                if (!slot.readable()) {
                    session.refuse(computation.output(), section, localSlice);
                    continue;
                }
                if (!session.charge(worldSlice.volume(), 1)) {
                    session.findings.add(
                        code(
                            Codes.budgetExhausted,
                            phase,
                            "the budget ran out after " + session.spend.cells() + " cells",
                            Locus.local(localSlice)
                        )
                    );
                    return undecided(phase, UndecidedReason.budgetExhausted, session);
                }
                int before = session.findings.size();
                SectionContext ctx = new SectionCtx(request, session, section, localSlice);
                try {
                    Outcome<?> result = computation.compute(ctx);
                    if (result instanceof Outcome.Found<?> found) {
                        session.contribute(computation.output(), section, found.value());
                    } else {
                        session.missing(computation.output(), localSlice, session.findings.size() > before);
                    }
                } catch (RuntimeException failure) {
                    session.report(
                        extensionFailure(phase, computation.id(), failure, Locus.local(computation.region()))
                    );
                    session.missing(computation.output(), localSlice, true);
                }
            }
        }
        for (SectionComputation<?> computation : plan.sections()) {
            session.fold(computation.output(), phase);
        }

        for (AggregateComputation<?> computation : plan.aggregates()) {
            if (session.cancelled()) {
                session.findings.add(code(Codes.cancelled, phase, "the attempt was cancelled", null));
                return undecided(phase, UndecidedReason.cancelled, session);
            }
            CuboidSet worldRegion = request.siting().toWorld(computation.region());
            if (worldRegion == null) {
                session.report(
                    code(
                        Codes.locusUnmapped,
                        phase,
                        "computation " + computation.id() + " leaves the lattice",
                        Locus.local(computation.region())
                    )
                );
                continue;
            }
            if (!session.charge(worldRegion.volume(), sectionsOf(worldRegion, request.lease().view()).size())) {
                session.findings.add(
                    code(
                        Codes.budgetExhausted,
                        phase,
                        "the budget ran out before aggregate " + computation.id(),
                        Locus.local(computation.region())
                    )
                );
                return undecided(phase, UndecidedReason.budgetExhausted, session);
            }
            int before = session.findings.size();
            AggregateContext ctx = new AggregateCtx(request, session, computation);
            try {
                Outcome<?> result = computation.compute(ctx);
                if (result instanceof Outcome.Found<?> found) {
                    if (!computation.output().type().isInstance(found.value())) {
                        session.report(
                            code(
                                Codes.designDefect,
                                phase,
                                "computation " + computation.id() + " returned " + found.value().getClass().getName(),
                                Locus.local(computation.region())
                            )
                        );
                    } else {
                        session.values.put(computation.output(), found.value());
                    }
                } else if (session.findings.size() == before) {
                    session.report(
                        code(
                            Codes.computationFailed,
                            phase,
                            "computation " + computation.id() + " produced no value",
                            Locus.local(computation.region())
                        )
                    );
                }
            } catch (RuntimeException failure) {
                session.report(extensionFailure(phase, computation.id(), failure, Locus.local(computation.region())));
            }
        }

        List<Run> runs = new ArrayList<>();
        for (Check<?> check : plan.checks()) {
            if (session.cancelled()) {
                session.findings.add(code(Codes.cancelled, phase, "the attempt was cancelled", null));
                return undecided(phase, UndecidedReason.cancelled, session);
            }
            if (!session.chargeScope(check.reads(), request.lease().view())) {
                session.findings.add(
                    code(
                        Codes.budgetExhausted,
                        phase,
                        "the budget ran out before check " + check.id(),
                        Locus.local(check.bay().cells())
                    )
                );
                return undecided(phase, UndecidedReason.budgetExhausted, session);
            }
            FindingBundle absent = missingKeys(request, session, check);
            if (!absent.isEmpty()) {
                session.findings.addAll(absent.findings());
                runs.add(new Run(check, new CheckResult(Reply.unreadable(absent), List.of(), FindingBundle.empty())));
                continue;
            }
            FocusContext ctx = new EngineFocusContext(request, session, check);
            try {
                CheckResult result = check.run(ctx);
                session.absorb(check, result);
                runs.add(new Run(check, result));
            } catch (RuntimeException failure) {
                FindingBundle bundle = FindingBundle
                        .of(List.of(extensionFailure(phase, check.id(), failure, Locus.local(check.bay().cells()))));
                session.findings.addAll(bundle.findings());
                runs.add(new Run(check, new CheckResult(Reply.unreadable(bundle), List.of(), FindingBundle.empty())));
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
                case Reply.Match ignored -> {}
                case Reply.Abstain ignored -> abstained = true;
            }
            session.findings.addAll(run.result().notes().findings());
        }
        MatchOutcome outcome = session.outcome();
        if (mismatched) {
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        if (unreadable) {
            return new MatchResult.Undecided(
                phase,
                UndecidedReason.unreadable,
                FindingBundle.of(session.findings),
                outcome
            );
        }
        if (abstained) {
            return new MatchResult.Undecided(
                phase,
                UndecidedReason.abstained,
                FindingBundle.of(session.findings),
                outcome
            );
        }

        boolean needsLedger = false;
        for (Bay bay : request.blueprint().bays()) {
            needsLedger |= !(bay.footprint() instanceof Footprint.None);
        }
        Finding nested = nestedSubject(outcome.bindings(), phase);
        if (nested != null) {
            session.findings.add(nested);
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        if (!needsLedger) {
            return new MatchResult.Admitted(
                phase,
                request.instance(),
                request.siting(),
                outcome,
                0,
                false,
                FindingBundle.of(session.findings)
            );
        }
        ClaimView claims = request.claims();
        if (claims == null || !claims.available()) {
            session.findings.add(
                code(Codes.ledgerUnavailable, phase, "the ledger cannot answer; no claim prediction is possible", null)
            );
            return new MatchResult.Undecided(
                phase,
                UndecidedReason.ledgerUnavailable,
                FindingBundle.of(session.findings),
                outcome
            );
        }
        Finding invalid = validateBindings(request, phase, outcome.bindings());
        if (invalid != null) {
            session.findings.add(invalid);
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        ClaimPlanner.Derived derived;
        try {
            derived = ClaimPlanner
                    .derive(request.blueprint(), request.siting(), request.instance(), outcome.bindings());
        } catch (IllegalArgumentException rejected) {
            session.findings.add(plannerFailure(phase, rejected, Locus.local(request.blueprint().bayCells())));
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        List<Finding> conflicts = conflicts(phase, derived.rows(), claims.rows());
        if (!conflicts.isEmpty()) {
            session.findings.addAll(conflicts);
            return new MatchResult.Failed(phase, FindingBundle.of(session.findings));
        }
        return new MatchResult.Admitted(
            phase,
            request.instance(),
            request.siting(),
            outcome,
            claims.revision(),
            true,
            FindingBundle.of(session.findings)
        );
    }

    public static MatchResult settle(
        MatchRequest request,
        MatchResult.Admitted admitted,
        @Nullable ClaimLedger ledger,
        StructureAttempt attempt
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(admitted, "admitted");
        Objects.requireNonNull(attempt, "attempt");
        Phase phase = Phase.settle;
        List<Finding> findings = new ArrayList<>(admitted.findings().findings());
        Finding target = validateAttempt(request, attempt, phase);
        if (target != null) {
            findings.add(target);
            return new MatchResult.Undecided(
                phase,
                UndecidedReason.designDefect,
                FindingBundle.of(findings),
                admitted.outcome()
            );
        }
        if (admitted.phase() != Phase.check
                || !admitted.instance().equals(request.instance())
                || !admitted.siting().equals(request.siting())
                || !admitted.outcome().view().snapshotId().equals(request.lease().id())) {
            findings.add(code(Codes.designDefect, phase, "the admitted result does not belong to this request", null));
            return new MatchResult.Undecided(
                phase,
                UndecidedReason.designDefect,
                FindingBundle.of(findings),
                admitted.outcome()
            );
        }
        if (!request.lease().isCurrent()) {
            findings.add(
                code(Codes.snapshotStale, phase, "the snapshot is no longer current; nothing is written", null)
            );
            return new MatchResult.Dropped(phase, FindingBundle.of(findings));
        }
        Finding invalid = validateBindings(request, phase, admitted.outcome().bindings());
        if (invalid != null) {
            findings.add(invalid);
            return new MatchResult.Failed(phase, FindingBundle.of(findings));
        }
        ClaimPlanner.Derived derived;
        try {
            derived = ClaimPlanner
                    .derive(request.blueprint(), request.siting(), request.instance(), admitted.outcome().bindings());
        } catch (IllegalArgumentException rejected) {
            findings.add(plannerFailure(phase, rejected, Locus.local(request.blueprint().bayCells())));
            return new MatchResult.Failed(phase, FindingBundle.of(findings));
        }
        long revision;
        if (ledger == null) {
            if (!derived.rows().isEmpty()) {
                findings.add(
                    code(Codes.ledgerUnavailable, phase, "no ledger is installed; the claims cannot be written", null)
                );
                return new MatchResult.Undecided(
                    phase,
                    UndecidedReason.ledgerUnavailable,
                    FindingBundle.of(findings),
                    admitted.outcome()
                );
            }
            revision = 0;
        } else {
            ClaimVerdict verdict = ledger
                    .settle(new SettleRequest(request.instance(), derived.rows(), admitted.ledgerRevision()));
            if (verdict instanceof ClaimVerdict.RevisionMoved) {
                long retryRevision;
                if (derived.rows().isEmpty()) {
                    retryRevision = ledger.revision();
                } else {
                    Cuboid readBox = derived.footprint().bounds();
                    ClaimView fresh = readBox == null ? ClaimView.unavailable() : ledger.read(readBox);
                    if (!fresh.available()) {
                        findings.add(
                            code(Codes.ledgerUnavailable, phase, "the ledger moved and cannot answer the re-read", null)
                        );
                        return new MatchResult.Undecided(
                            phase,
                            UndecidedReason.ledgerUnavailable,
                            FindingBundle.of(findings),
                            admitted.outcome()
                        );
                    }
                    List<Finding> retryConflicts = conflicts(phase, derived.rows(), fresh.rows());
                    if (!retryConflicts.isEmpty()) {
                        findings.addAll(retryConflicts);
                        return new MatchResult.Failed(phase, FindingBundle.of(findings));
                    }
                    retryRevision = fresh.revision();
                }
                verdict = ledger.settle(new SettleRequest(request.instance(), derived.rows(), retryRevision));
            }
            if (verdict instanceof ClaimVerdict.RevisionMoved moved) {
                findings.add(
                    code(
                        Codes.ledgerMoved,
                        phase,
                        "the ledger moved twice (" + moved.expected() + " -> " + moved.actual() + ")",
                        null
                    )
                );
                return new MatchResult.Undecided(
                    phase,
                    UndecidedReason.ledgerMoved,
                    FindingBundle.of(findings),
                    admitted.outcome()
                );
            }
            if (verdict instanceof ClaimVerdict.Conflict conflict) {
                findings.addAll(conflicts(phase, derived.rows(), conflict.incumbents()));
                return new MatchResult.Failed(phase, FindingBundle.of(findings));
            }
            ClaimVerdict.Settled settled = (ClaimVerdict.Settled) verdict;
            revision = settled.revision();
        }
        StructureContext context = publish(attempt, request, admitted.outcome(), findings);
        return new MatchResult.Formed(
            phase,
            request.instance(),
            request.siting(),
            admitted.outcome(),
            new ClaimSet(derived.rows(), revision),
            context.revision(),
            revision,
            FindingBundle.of(findings)
        );
    }

    public static StructureContext derive(
        MatchResult.Formed formed,
        List<DerivationSpec> derivations,
        Catalog<Deriver<?>> derivers,
        ContextStore contexts
    ) {
        Objects.requireNonNull(formed, "formed");
        Objects.requireNonNull(derivations, "derivations");
        Objects.requireNonNull(derivers, "derivers");
        Objects.requireNonNull(contexts, "contexts");
        StructureContext base = contexts.context(formed.instance());
        Map<ContextKey<?>, Object> values = new LinkedHashMap<>(base.values());
        List<Finding> findings = new ArrayList<>(formed.findings().findings());
        List<DerivationSpec> ordered = new ArrayList<>(derivations);
        ordered.sort(Comparator.comparingInt(DerivationSpec::order).thenComparing(spec -> spec.deriver()));
        for (DerivationSpec spec : ordered) {
            Deriver<?> deriver = derivers.get(spec.deriver());
            if (deriver == null) {
                findings.add(
                    code(
                        Codes.deriverMissing,
                        Phase.settle,
                        "no deriver is registered as " + spec.deriver(),
                        Locus.key(formed.instance(), spec.output())
                    )
                );
                continue;
            }
            if (!deriver.output().id().equals(spec.output())) {
                findings.add(
                    code(
                        Codes.deriverFailed,
                        Phase.settle,
                        "deriver " + spec.deriver() + " produces " + deriver.output().id() + ", not " + spec.output(),
                        Locus.key(formed.instance(), spec.output())
                    )
                );
                continue;
            }
            if (deriver.output().retention() != Retention.instance) {
                findings.add(
                    code(
                        Codes.deriverFailed,
                        Phase.settle,
                        "deriver " + spec.deriver() + " must produce an INSTANCE key",
                        Locus.key(formed.instance(), spec.output())
                    )
                );
                continue;
            }
            if (!values.keySet().containsAll(deriver.requires())) {
                findings.add(
                    code(
                        Codes.keyAbsent,
                        Phase.settle,
                        "deriver " + spec.deriver() + " misses a required key",
                        Locus.key(formed.instance(), spec.output())
                    )
                );
                continue;
            }
            MapContextView view = new MapContextView(values);
            int before = findings.size();
            DeriveCtx ctx = new DeriveCtx(base, view, findings);
            try {
                Outcome<?> result = deriver.derive(ctx, spec.arguments());
                if (result instanceof Outcome.Found<?> found) {
                    if (!deriver.output().type().isInstance(found.value())) {
                        findings.add(
                            code(
                                Codes.deriverFailed,
                                Phase.settle,
                                "deriver " + spec.deriver() + " returned " + found.value().getClass().getName(),
                                Locus.key(formed.instance(), spec.output())
                            )
                        );
                    } else {
                        values.put(deriver.output(), found.value());
                    }
                } else if (findings.size() == before) {
                    findings.add(
                        code(
                            Codes.deriverFailed,
                            Phase.settle,
                            "deriver " + spec.deriver() + " produced no value",
                            null
                        )
                    );
                }
            } catch (RuntimeException failure) {
                findings.add(
                    extensionFailure(Phase.settle, spec.deriver(), failure, Locus.key(formed.instance(), spec.output()))
                );
            }
        }
        Map<ContextKey<?>, Object> published = new LinkedHashMap<>();
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            if (entry.getKey().retention() == Retention.instance) {
                published.put(entry.getKey(), entry.getValue());
            }
        }
        return contexts.publish(formed.instance(), published, FindingBundle.of(findings), Spend.zero);
    }

    private static StructureContext publish(
        StructureAttempt attempt,
        MatchRequest request,
        MatchOutcome outcome,
        List<Finding> findings
    ) {
        Map<ContextKey<?>, Object> published = new LinkedHashMap<>();
        for (Map.Entry<ContextKey<?>, Object> entry : outcome.values().entrySet()) {
            if (entry.getKey().retention() == Retention.instance) {
                published.put(entry.getKey(), entry.getValue());
            }
        }
        return attempt.publish(request.instance(), published, FindingBundle.of(findings), outcome.spend());
    }

    private static @Nullable Finding validateAttempt(MatchRequest request, StructureAttempt attempt, Phase phase) {
        if (!attempt.isOpen()) {
            return code(Codes.designDefect, phase, "the attempt is already closed", null);
        }
        if (!attempt.id().equals(request.instance())) {
            return code(
                Codes.designDefect,
                phase,
                "the attempt " + attempt.id() + " does not belong to " + request.instance(),
                null
            );
        }
        StructureSpec spec = attempt.spec();
        if (!spec.owner().equals(request.owner())
                || !spec.siting().equals(request.siting())
                || !spec.structure().equals(request.blueprint().id())) {
            return code(Codes.designDefect, phase, "the attempt spec does not match the request", null);
        }
        return null;
    }

    private static @Nullable Finding nestedSubject(List<BayBinding> bindings, Phase phase) {
        for (BayBinding binding : bindings) {
            for (OccupantSnapshot subject : binding.subjects()) {
                if (subject.ref().frame() != 0 || subject.ref().kind().equals(OccupantKind.nested)) {
                    return code(
                        Codes.bindingInvalid,
                        phase,
                        "a nested or non-root subject cannot back a claim",
                        Locus.local(binding.bay().cells())
                    );
                }
            }
        }
        return null;
    }

    private static @Nullable Finding validateBindings(MatchRequest request, Phase phase, List<BayBinding> bindings) {
        Map<NamespacedId, BayBinding> byBay = new LinkedHashMap<>();
        for (BayBinding binding : bindings) {
            Bay bay = request.blueprint().bay(binding.bay().id());
            if (bay == null || !bay.equals(binding.bay())) {
                return code(
                    Codes.bindingInvalid,
                    phase,
                    "a binding names a bay this blueprint does not declare",
                    Locus.local(request.blueprint().bayCells())
                );
            }
            byBay.put(binding.bay().id(), binding);
        }
        for (Bay bay : request.blueprint().bays()) {
            BayBinding binding = byBay.get(bay.id());
            if (binding == null) {
                return code(Codes.bindingInvalid, phase, "no binding for bay " + bay.id(), Locus.local(bay.cells()));
            }
            CuboidSet bayWorld = request.siting().toWorld(bay.cells());
            if (bayWorld == null) {
                return code(
                    Codes.locusUnmapped,
                    phase,
                    "bay " + bay.id() + " leaves the lattice",
                    Locus.local(bay.cells())
                );
            }
            CuboidSet bound = CuboidSet.empty();
            for (OccupantSnapshot subject : binding.subjects()) {
                if (!subject.ref().snapshot().equals(request.lease().id()) || subject.ref().frame() != 0) {
                    return code(
                        Codes.bindingInvalid,
                        phase,
                        "a subject is not from this capture's root frame",
                        Locus.local(bay.cells())
                    );
                }
                if (subject.ref().kind().equals(OccupantKind.nested)) {
                    return code(
                        Codes.bindingInvalid,
                        phase,
                        "a nested subject cannot back a claim",
                        Locus.local(bay.cells())
                    );
                }
                if (!bayWorld.containsAll(subject.footprint().asSet())) {
                    return code(
                        Codes.bindingInvalid,
                        phase,
                        "a subject leaves bay " + bay.id(),
                        Locus.local(bay.cells())
                    );
                }
                if (!subject.isAir()) {
                    bound = bound.union(subject.footprint().asSet());
                }
            }
            if (bay.coverage() == Coverage.oneSubject && !bound.contentEquals(bayWorld)) {
                return code(
                    Codes.bindingInvalid,
                    phase,
                    "ONE_SUBJECT bay " + bay.id() + " is not equal to its bound footprint",
                    Locus.local(bay.cells())
                );
            }
            if (bay.sharing() == Sharing.exclusive && bay.footprint() instanceof Footprint.Bound && bound.isEmpty()) {
                return code(
                    Codes.boundEmpty,
                    phase,
                    "exclusive bay " + bay.id() + " bound no cell",
                    Locus.local(bay.cells())
                );
            }
        }
        return null;
    }

    private static FindingBundle missingKeys(MatchRequest request, Session session, Check<?> check) {
        List<Finding> missing = new ArrayList<>();
        for (ContextKey<?> key : check.expectation().meta().requires()) {
            if (!session.context.has(key)) {
                missing.add(
                    Finding.builder(Codes.keyAbsent).phase(Phase.check)
                            .message("required context key " + key.id() + " is absent")
                            .label(Label.primary(Locus.key(request.instance(), key.id()), "no value for " + key.id()))
                            .build()
                );
            }
        }
        return FindingBundle.of(missing);
    }

    private static List<Finding> conflicts(Phase phase, List<ClaimRow> requested, List<ClaimRow> held) {
        Map<String, Conflict> byPair = new LinkedHashMap<>();
        for (ClaimRow row : requested) {
            for (ClaimRow incumbent : held) {
                if (!ClaimMatrix.conflicts(row, incumbent)) {
                    continue;
                }
                String pair = row.bay() + "|" + incumbent.instance() + "|" + incumbent.bay();
                Conflict conflict = byPair
                        .computeIfAbsent(pair, ignored -> new Conflict(row, incumbent, CuboidSet.empty()));
                conflict.cells = conflict.cells.union(Cuboid.of(row.cell().point()));
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (Conflict conflict : byPair.values()) {
            Locus locus = Locus.world(conflict.cells);
            findings.add(
                Finding.builder(Codes.claimConflict).phase(phase)
                        .message(
                            conflict.incumbent.instance() + ":" + conflict.incumbent.bay() + " already holds "
                                    + conflict.cells.volume() + " cell(s) of " + conflict.requested.bay()
                        ).label(Label.primary(locus, "contested cells"))
                        .note("this bay asks " + conflict.requested.sharing(), locus)
                        .note("the incumbent holds " + conflict.incumbent.sharing(), null).build()
            );
        }
        return findings;
    }

    private static final class Conflict {
        private final ClaimRow requested;
        private final ClaimRow incumbent;
        private CuboidSet cells;

        private Conflict(ClaimRow requested, ClaimRow incumbent, CuboidSet cells) {
            this.requested = requested;
            this.incumbent = incumbent;
            this.cells = cells;
        }
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
        int minX = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, x0));
        int minY = (int) Math.max(LatticePoint.minY, Math.min(LatticePoint.maxY, y0));
        int minZ = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, z0));
        int maxX = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, x0 + size));
        int maxY = (int) Math.max(LatticePoint.minY, Math.min(LatticePoint.maxY, y0 + size));
        int maxZ = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, z0 + size));
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static MatchResult dropped(Phase phase, String message) {
        return new MatchResult.Dropped(
            phase,
            FindingBundle.of(List.of(code(Codes.snapshotStale, phase, message, null)))
        );
    }

    private static MatchResult undecided(Phase phase, UndecidedReason reason, Session session) {
        return new MatchResult.Undecided(phase, reason, FindingBundle.of(session.findings), session.outcome());
    }

    private static Finding plannerFailure(Phase phase, IllegalArgumentException rejected, Locus locus) {
        String message = rejected.getMessage() == null ? "claim planning failed" : rejected.getMessage();
        CodeEntry entry = message.contains("SC-SPC-0001")
                ? Codes.locusUnmapped
                : message.contains("SC-CLM-0005") ? Codes.boundEmpty : Codes.bindingInvalid;
        return code(entry, phase, message, locus);
    }

    private static Finding code(CodeEntry entry, Phase phase, String message, @Nullable Locus locus) {
        Finding.Builder builder = Finding.builder(entry).phase(phase).message(message);
        if (locus != null) {
            builder.label(Label.primary(locus, message));
        }
        return builder.build();
    }

    private static Finding extensionFailure(Phase phase, NamespacedId unit, RuntimeException failure, Locus locus) {
        String message = "extension " + unit + " failed: " + failure.getClass().getSimpleName();
        return Finding.builder(Codes.extensionFailed).phase(phase).message(message).label(Label.primary(locus, message))
                .note(failure.getMessage() == null ? "no message" : failure.getMessage()).build();
    }

    private record Run(Check<?> check, CheckResult result) {}

    private static final class Session {

        private final MatchRequest request;
        private final Map<ContextKey<?>, TreeMap<SectionKey, Object>> contributions = new LinkedHashMap<>();
        private final Set<ContextKey<?>> refused = new HashSet<>();
        private final Set<ContextKey<?>> missing = new HashSet<>();
        private final Map<ContextKey<?>, Object> values = new LinkedHashMap<>();
        private final List<Finding> findings = new ArrayList<>();
        private final Map<NamespacedId, LinkedHashMap<OccupantRef, OccupantSnapshot>> subjects = new LinkedHashMap<>();
        private final ContextView context;
        private ReadScope reads;
        private Spend spend = Spend.zero;

        private Session(MatchRequest request) {
            this.request = request;
            for (Bay bay : request.blueprint().bays()) {
                subjects.put(bay.id(), new LinkedHashMap<>());
            }
            reads = request.blueprint().reads();
            Plan plan = request.blueprint().plan();
            Set<KeyIdentity> eligible = new HashSet<>();
            for (Check<?> check : plan.checks()) {
                addInstanceIdentities(eligible, check.expectation().meta().requires());
            }
            for (SectionComputation<?> computation : plan.sections()) {
                addInstanceIdentities(eligible, computation.inputs());
            }
            for (AggregateComputation<?> computation : plan.aggregates()) {
                addInstanceIdentities(eligible, computation.inputs());
            }
            context = new CompositeContextView(values, request.cachedContext(), Set.copyOf(eligible));
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
            return new Budget((int) Math.min(Integer.MAX_VALUE, cells), (int) Math.min(Integer.MAX_VALUE, sections));
        }

        private void report(Finding finding) {
            findings.add(finding);
        }

        private void contribute(ContextKey<?> key, SectionKey section, Object value) {
            if (!key.type().isInstance(value)) {
                findings.add(
                    code(
                        Codes.designDefect,
                        Phase.check,
                        "a section of " + key.id() + " produced " + value.getClass().getName(),
                        Locus.local(computationRegion(key, section))
                    )
                );
                return;
            }
            contributions.computeIfAbsent(key, ignored -> new TreeMap<>()).put(section, value);
        }

        private CuboidSet computationRegion(ContextKey<?> key, SectionKey section) {
            for (Computation<?> computation : request.blueprint().computations()) {
                if (computation.output().equals(key)) {
                    CuboidSet slice = computation.region()
                            .intersect(sectionBounds(section, request.lease().view().cellSize()));
                    if (!slice.isEmpty()) {
                        return slice;
                    }
                }
            }
            return request.blueprint().bayCells();
        }

        private void refuse(ContextKey<?> key, SectionKey section, CuboidSet region) {
            if (refused.add(key)) {
                findings.add(
                    code(
                        Codes.foldRefused,
                        Phase.check,
                        "a section of " + key.id() + " is unreadable",
                        Locus.local(region)
                    )
                );
            }
        }

        private void missing(ContextKey<?> key, CuboidSet region, boolean reported) {
            if (missing.add(key) && !reported) {
                findings.add(
                    code(
                        Codes.computationFailed,
                        Phase.check,
                        "a section of " + key.id() + " produced no value",
                        Locus.local(region)
                    )
                );
            }
        }

        @SuppressWarnings("unchecked")
        private void fold(ContextKey<?> key, Phase phase) {
            if (refused.contains(key) || missing.contains(key)) {
                return;
            }
            TreeMap<SectionKey, Object> parts = contributions.get(key);
            if (parts == null || parts.isEmpty()) {
                if (key.whenAbsent() != null) {
                    values.put(key, key.whenAbsent());
                }
                return;
            }
            Merge<Object> merge = (Merge<Object>) key.merge();
            if (merge == null) {
                findings.add(code(Codes.designDefect, phase, "a contributed key has no merge: " + key.id(), null));
                return;
            }
            Object combined = null;
            for (Object part : parts.values()) {
                combined = combined == null ? part : merge.combine(combined, part);
            }
            values.put(key, combined);
        }

        private void absorb(Check<?> check, CheckResult result) {
            Map<OccupantRef, OccupantSnapshot> baySubjects = subjects
                    .computeIfAbsent(check.bay().id(), ignored -> new LinkedHashMap<>());
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
                    subjects.getOrDefault(bay.id(), new LinkedHashMap<>()).values()
                );
                bound.sort(Comparator.comparing(OccupantSnapshot::ref));
                bindings.add(new BayBinding(bay, bound));
            }
            return new MatchOutcome(request.lease().view(), values, bindings, reads, spend);
        }
    }

    private static List<OccupantSnapshot> occupantsAnchoredIn(WorldView view, CuboidSet cells) {
        Cuboid bounds = cells.bounds();
        if (bounds == null) {
            return List.of();
        }
        Map<OccupantRef, OccupantSnapshot> unique = new LinkedHashMap<>();
        for (OccupantSnapshot candidate : view.occupants(bounds, OccupantFilter.any())) {
            if (cells.contains(candidate.ref().anchor())) {
                unique.putIfAbsent(candidate.ref(), candidate);
            }
        }
        List<OccupantSnapshot> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(OccupantSnapshot::ref));
        return List.copyOf(result);
    }

    private static List<OccupantSnapshot> occupantsIn(WorldView view, CuboidSet cells) {
        Cuboid bounds = cells.bounds();
        if (bounds == null) {
            return List.of();
        }
        Map<OccupantRef, OccupantSnapshot> unique = new LinkedHashMap<>();
        for (OccupantSnapshot candidate : view.occupants(bounds, OccupantFilter.any())) {
            if (cells.containsAll(candidate.footprint().asSet())) {
                unique.putIfAbsent(candidate.ref(), candidate);
            }
        }
        List<OccupantSnapshot> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(OccupantSnapshot::ref));
        return List.copyOf(result);
    }

    private static List<LatticePoint> pointsOf(CuboidSet cells) {
        List<LatticePoint> points = new ArrayList<>();
        for (Cuboid box : cells.boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        points.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return points;
    }

    private static final class SectionCtx implements SectionContext {

        private final MatchRequest request;
        private final Session session;
        private final SectionKey section;
        private final CuboidSet cells;

        private SectionCtx(MatchRequest request, Session session, SectionKey section, CuboidSet cells) {
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
        public Phase phase() {
            return Phase.check;
        }

        @Override
        public CellRead read(LatticePoint local) {
            LatticePoint world = request.siting().toWorld(local);
            if (world == null) {
                return new CellRead.Unreadable(new ReadRefusal(ReadStatus.outOfBounds, Codes.unreadableRead.code()));
            }
            return request.lease().view().read(world);
        }

        @Override
        public Map<LatticePoint, ReadRefusal> unreadable() {
            SectionSlot slot = request.lease().view().slot(section);
            if (!(slot instanceof SectionSlot.Partial partial)) {
                return Map.of();
            }
            TreeMap<LatticePoint, ReadRefusal> local = new TreeMap<>();
            for (Map.Entry<LatticePoint, ReadRefusal> entry : partial.unreadable().entrySet()) {
                LatticePoint cell = request.siting().toLocal(entry.getKey());
                if (cell != null && cells.contains(cell)) {
                    local.put(cell, entry.getValue());
                }
            }
            return Collections.unmodifiableMap(local);
        }

        @Override
        public List<OccupantSnapshot> occupants() {
            CuboidSet world = request.siting().toWorld(cells);
            return world == null ? List.of() : occupantsAnchoredIn(request.lease().view(), world);
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid local) {
            Cuboid world = request.siting().toWorld(local);
            return world == null ? List.of() : occupantsAnchoredIn(request.lease().view(), world.asSet());
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
        private final Computation<?> computation;
        private @Nullable Map<LatticePoint, ReadRefusal> unreadable;

        private AggregateCtx(MatchRequest request, Session session, Computation<?> computation) {
            this.request = request;
            this.session = session;
            this.computation = computation;
        }

        @Override
        public ContextView context() {
            return session.context;
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
        public CuboidSet cells() {
            return computation.region();
        }

        @Override
        public CellRead read(LatticePoint local) {
            LatticePoint world = request.siting().toWorld(local);
            if (world == null) {
                return new CellRead.Unreadable(new ReadRefusal(ReadStatus.outOfBounds, Codes.unreadableRead.code()));
            }
            return request.lease().view().read(world);
        }

        @Override
        public Map<LatticePoint, ReadRefusal> unreadable() {
            Map<LatticePoint, ReadRefusal> refused = unreadable;
            if (refused == null) {
                TreeMap<LatticePoint, ReadRefusal> found = new TreeMap<>();
                for (LatticePoint cell : pointsOf(computation.region())) {
                    if (read(cell) instanceof CellRead.Unreadable unavailable) {
                        found.put(cell, unavailable.refusal());
                    }
                }
                refused = Collections.unmodifiableMap(found);
                unreadable = refused;
            }
            return refused;
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid local) {
            Cuboid world = request.siting().toWorld(local);
            return world == null ? List.of() : occupantsIn(request.lease().view(), world.asSet());
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

        private EngineFocusContext(MatchRequest request, Session session, Check<?> check) {
            this.request = request;
            this.session = session;
            this.check = check;
        }

        @Override
        public BayView bay() {
            return new BayView(check.bay().id(), check.bay().cells(), request.lease().view(), request.siting());
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
            return Phase.check;
        }

        @Override
        public Locus locus() {
            return Locus.local(check.bay().cells());
        }

        @Override
        public ContextView context() {
            return session.context;
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
            return read(local) instanceof CellRead.Found found ? found.occupant() : null;
        }

        @Override
        public CellRead read(LatticePoint local) {
            LatticePoint world = request.siting().toWorld(local);
            if (world == null) {
                return new CellRead.Unreadable(new ReadRefusal(ReadStatus.outOfBounds, Codes.unreadableRead.code()));
            }
            return request.lease().view().read(world);
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

    private record KeyIdentity(NamespacedId id, Class<?> type) {}

    private static void addInstanceIdentities(Set<KeyIdentity> target, Set<ContextKey<?>> keys) {
        for (ContextKey<?> key : keys) {
            if (key.retention() == Retention.instance) {
                target.add(new KeyIdentity(key.id(), key.type()));
            }
        }
    }

    private static final class CompositeContextView implements ContextView {

        private final Map<ContextKey<?>, Object> current;
        private final @Nullable CachedContext cached;
        private final Set<KeyIdentity> eligible;

        private CompositeContextView(
            Map<ContextKey<?>, Object> current,
            @Nullable CachedContext cached,
            Set<KeyIdentity> eligible
        ) {
            this.current = current;
            this.cached = cached;
            this.eligible = eligible;
        }

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            Object value = current.get(key);
            if (value != null) {
                return key.type().cast(value);
            }
            Object shadow = currentInstanceValue(key.id(), key.type());
            if (shadow != null) {
                return key.type().cast(shadow);
            }
            if (cached == null
                    || key.retention() != Retention.instance
                    || !eligible.contains(new KeyIdentity(key.id(), key.type()))) {
                return null;
            }
            Object cachedValue;
            try {
                cachedValue = cached.get(key.id(), key.type());
            } catch (RuntimeException ignored) {
                return null;
            }
            return cachedValue != null && key.type().isInstance(cachedValue) ? key.type().cast(cachedValue) : null;
        }

        private @Nullable Object currentInstanceValue(NamespacedId id, Class<?> type) {
            for (Map.Entry<ContextKey<?>, Object> entry : current.entrySet()) {
                ContextKey<?> candidate = entry.getKey();
                if (candidate.retention() == Retention.instance
                        && candidate.id().equals(id)
                        && candidate.type().equals(type)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            T value = get(key);
            return value == null ? Outcome.missing() : Outcome.found(value);
        }

        @Override
        public boolean has(ContextKey<?> key) {
            return get(key) != null;
        }

        @Override
        public Set<ContextKey<?>> keys() {
            TreeSet<ContextKey<?>> sorted = new TreeSet<>(
                Comparator.comparing((ContextKey<?> key) -> key.id()).thenComparing(key -> key.type().getName())
            );
            sorted.addAll(current.keySet());
            return Collections.unmodifiableSet(sorted);
        }
    }

    private static final class MapContextView implements ContextView {

        private final Map<ContextKey<?>, Object> values;
        private final Set<ContextKey<?>> keys;

        private MapContextView(Map<ContextKey<?>, Object> values) {
            this.values = values;
            TreeSet<ContextKey<?>> sorted = new TreeSet<>(
                Comparator.comparing((ContextKey<?> key) -> key.id()).thenComparing(key -> key.type().getName())
            );
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

    private MatchEngine() {}
}
