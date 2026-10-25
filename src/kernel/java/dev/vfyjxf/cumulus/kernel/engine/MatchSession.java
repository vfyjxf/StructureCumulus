package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.Label;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.CheckPlan;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.context.ComputationContext;
import dev.vfyjxf.cumulus.kernel.context.ContextDraft;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.ContextTier;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.context.Retention;
import dev.vfyjxf.cumulus.kernel.context.SectionContext;
import dev.vfyjxf.cumulus.kernel.context.StatePhase;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.predicate.BayView;
import dev.vfyjxf.cumulus.kernel.predicate.Bindings;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.CheckResult;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.FocusContext;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.ChunkGrid;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.TreeSet;

/**
 * One synchronous attempt: expand the stage plan, run the section pass and the barrier, run the
 * aggregates and the checks, publish one frame, then settle when the stage is {@code SETTLE}. Every
 * value the attempt computes is read back from its one {@link ContextDraft}.
 */
public final class MatchSession {

    static final FindingCode BUDGET_EXHAUSTED = FindingCode.of(FindingCode.Family.ENG, 1);
    static final FindingCode SNAPSHOT_CHANGED = FindingCode.of(FindingCode.Family.ENG, 3);
    static final FindingCode CAPTURE_INVALID = FindingCode.of(FindingCode.Family.ENG, 6);
    static final FindingCode PLAN_REJECTED = FindingCode.of(FindingCode.Family.ENG, 102);
    static final FindingCode NO_LEDGER = FindingCode.of(FindingCode.Family.ENG, 103);
    static final FindingCode NO_JUDGING_UNIT = FindingCode.of(FindingCode.Family.ENG, 201);
    static final FindingCode LEASE_CLOSED = FindingCode.of(FindingCode.Family.ENG, 401);
    static final FindingCode WORKER_FAILED = FindingCode.of(FindingCode.Family.ENG, 402);
    static final FindingCode DIRTY_UNSTABLE = FindingCode.of(FindingCode.Family.MC, 404);
    static final FindingCode STALE_BASE = FindingCode.of(FindingCode.Family.CTX, 108);
    static final FindingCode KEY_ABSENT = FindingCode.of(FindingCode.Family.CTX, 202);
    static final FindingCode WRONG_VERB = FindingCode.of(FindingCode.Family.CTX, 109);

    private static final int CELL_SIZE = ChunkGrid.ADAPTER_CELL_SIZE;

    private final MatchRequest request;
    private final @Nullable MatchSettler settler;
    private final InstanceRecord instanceRecord;
    private final InstanceId instance;
    private final ContextStore store;
    private final ContextTier tier;
    private final ContextDraft draft;
    private final AttemptView context;
    private final PinBudget pins;

    private final Set<ContextKey<?>> absentKeys = new TreeSet<>();
    private final Map<NamespacedId, BayBinding> bayBindings = new LinkedHashMap<>();

    private Reply combined = Reply.match();
    private FindingBundle mismatches = FindingBundle.empty();
    private FindingBundle unreadables = FindingBundle.empty();
    private FindingBundle notes = FindingBundle.empty();
    private ReadScope reads = ReadScope.none();
    private int mismatched;
    private int unreadable;
    private int abstained;
    private @Nullable MatchResult result;

    public MatchSession(MatchRequest request, @Nullable MatchSettler settler) {
        this(request, settler, new ContextStore(request.record().id()));
    }

    MatchSession(MatchRequest request, @Nullable MatchSettler settler, ContextStore store) {
        this.request = Objects.requireNonNull(request, "request");
        this.settler = settler;
        this.store = Objects.requireNonNull(store, "store");
        this.instanceRecord = request.record();
        this.pins = request.pins();
        this.instance = store.instance();
        if (!instanceRecord.id().equals(instance)) {
            throw new IllegalArgumentException("record " + instanceRecord.id() + " is not instance " + instance);
        }
        this.tier = tierOf(request.stage());
        this.draft = store.draft(tier, store.revision());
        this.context = new AttemptView(instance, store.context(instance).at(tier), draft, absentKeys, tier);
    }

    /**
     * One attempt. The capture is checked once, after the units and before anything is published; a
     * second call returns the first result and never checks it again.
     */
    public MatchResult run() {
        MatchResult done = result;
        if (done != null) {
            return done;
        }
        MatchResult computed = execute();
        result = computed;
        return computed;
    }

    private MatchResult execute() {
        if (!request.lease().isOpen()) {
            return undecided(UndecidedReason.LEASE_CLOSED, LEASE_CLOSED,
                    "the snapshot lease was closed before " + request.stage(), null);
        }
        if (request.stage() == Stage.SETTLE && settler == null) {
            return undecided(UndecidedReason.DESIGN_DEFECT, NO_LEDGER,
                    "SETTLE needs a claim ledger and this call has none", null);
        }
        CheckPlan plan;
        try {
            plan = request.blueprint().expand(request.stage());
        } catch (IllegalArgumentException rejected) {
            return failed(PLAN_REJECTED, message(rejected));
        }
        MatchResult refusedPlan = validatePlan(plan);
        if (refusedPlan != null) {
            return refusedPlan;
        }
        if (plan.checks().isEmpty()) {
            return undecided(UndecidedReason.DESIGN_DEFECT, NO_JUDGING_UNIT,
                    "the " + request.stage() + " plan has no judging unit", null);
        }
        for (Computation<?> computation : plan.computations()) {
            if (computation.phase() != StatePhase.SECTION) {
                continue;
            }
            MatchResult stopped = runSections(computation);
            if (stopped != null) {
                return stopped;
            }
        }
        for (Computation<?> computation : plan.computations()) {
            if (computation.phase() != StatePhase.AGGREGATE) {
                continue;
            }
            MatchResult stopped = runAggregate(computation);
            if (stopped != null) {
                return stopped;
            }
        }
        for (Check<?> check : plan.checks()) {
            MatchResult stopped = runCheck(check);
            if (stopped != null) {
                return stopped;
            }
        }
        MatchResult moved = captureMoved();
        if (moved != null) {
            return moved;
        }
        MatchResult unpublished = publish();
        if (unpublished != null) {
            return unpublished;
        }
        if (combined instanceof Reply.Unreadable refused) {
            unreadable = Math.max(unreadable, 1);
            unreadables = unreadables.plus(refused.findings());
        }
        if (combined instanceof Reply.Mismatch conflict) {
            mismatched = Math.max(mismatched, 1);
            mismatches = mismatches.plus(conflict.findings());
        }
        if (mismatched > 0) {
            return new MatchResult.Failed(mismatches.plus(notes).findings());
        }
        if (unreadable > 0 || abstained > 0) {
            return new MatchResult.Undecided(UndecidedReason.SUBJECT_ABSENT,
                    unreadables.plus(notes).findings(), outcome(), null);
        }
        return admit();
    }

    private @Nullable MatchResult validatePlan(CheckPlan plan) {
        for (Computation<?> computation : plan.computations()) {
            if (computation.phase() == StatePhase.SECTION && !computation.output().contributed()) {
                return failed(WRONG_VERB, "section computation " + computation.id()
                        + " outputs " + computation.output().id() + " which has no merge");
            }
            if (computation.phase() == StatePhase.AGGREGATE && computation.output().contributed()) {
                return failed(WRONG_VERB, "aggregate computation " + computation.id()
                        + " outputs the contributed key " + computation.output().id());
            }
        }
        return null;
    }

    private @Nullable MatchResult runSections(Computation<?> computation) {
        ContextKey<?> output = computation.output();
        reads = reads.union(new ReadScope(computation.region(), computation.inputs(), List.of()));
        for (SectionKey section : sectionsOf(computation.region())) {
            CuboidSet slice = computation.region().intersect(sectionBounds(section));
            if (!charge(slice.volume(), 1)) {
                return budgetExhausted();
            }
            if (!readable(section)) {
                refuse(output);
                continue;
            }
            try {
                SectionContext sectionContext = new SectionSlice(section, slice, view());
                Outcome<?> outcome = computation.compute(
                        new SessionComputationContext(context, view(), sectionContext));
                if (outcome instanceof Outcome.Found<?> found) {
                    contribute(output, section, found.value());
                    notes = notes.plus(found.notes());
                } else if (outcome instanceof Outcome.Missing<?> missing) {
                    refuse(output);
                    notes = notes.plus(missing.findings());
                }
            } catch (RuntimeException failed) {
                return workerFailed(failed);
            }
        }
        return null;
    }

    private @Nullable MatchResult runAggregate(Computation<?> computation) {
        reads = reads.union(new ReadScope(CuboidSet.empty(), computation.inputs(), List.of()));
        if (!charge(0L, 0)) {
            return budgetExhausted();
        }
        try {
            Outcome<?> outcome = computation.compute(
                    new SessionComputationContext(context, view(), null));
            if (outcome instanceof Outcome.Found<?> found) {
                put(computation.output(), found.value());
                notes = notes.plus(found.notes());
            } else if (outcome instanceof Outcome.Missing<?> missing) {
                absentKeys.add(computation.output());
                notes = notes.plus(missing.findings());
            }
        } catch (RuntimeException failed) {
            return workerFailed(failed);
        }
        return null;
    }

    private @Nullable MatchResult runCheck(Check<?> check) {
        Bay bay = request.blueprint().bay(check.bay());
        if (bay == null) {
            throw new IllegalStateException("SC-CHK-0107: no bay " + check.bay() + " in blueprint");
        }
        ReadScope scope = check.reads(bay.cells());
        if (!charge(scope.cells().volume(), sectionCount(scope.cells()))) {
            return budgetExhausted();
        }
        BayView bayView = new BayView(bay.id(), bay.cells(), view());
        FocusContext focus = FocusContext.of(bayView, request.siting(), context, check.stage(),
                Locus.local(CuboidSet.of(bay.cells())));
        CheckResult result;
        try {
            result = check.run(focus);
        } catch (RuntimeException failed) {
            return workerFailed(failed);
        }
        List<OccupantRef> subjects = result.subjects();
        notes = notes.plus(result.notes());
        Reply reply = result.reply();
        combined = Reply.combine(combined, reply);
        reads = reads.union(scope);
        switch (reply) {
            case Reply.Match match -> bind(bay.id(), subjects, match.bindings());
            case Reply.Mismatch mismatch -> {
                mismatched++;
                mismatches = mismatches.plus(mismatch.findings());
            }
            case Reply.Unreadable refused -> {
                unreadable++;
                unreadables = unreadables.plus(refused.findings());
            }
            case Reply.Abstain abstain -> abstained++;
        }
        return null;
    }

    private void bind(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings) {
        BayBinding existing = bayBindings.get(bay);
        if (existing == null) {
            bayBindings.put(bay, new BayBinding(bay, subjects, bindings));
            return;
        }
        List<OccupantRef> merged = new ArrayList<>(existing.subjects());
        for (OccupantRef subject : subjects) {
            if (!merged.contains(subject)) {
                merged.add(subject);
            }
        }
        Bindings kept = existing.bindings().isEmpty() ? bindings : existing.bindings();
        bayBindings.put(bay, new BayBinding(bay, merged, kept));
    }

    private @Nullable MatchResult publish() {
        Outcome<Void> published = store.publish(draft);
        if (!(published instanceof Outcome.Missing<?> missing)) {
            return null;
        }
        FindingBundle findings = missing.findings();
        if (findings.ofCode(STALE_BASE).isEmpty()) {
            return new MatchResult.Failed(findings.findings());
        }
        FindingBundle dirty = findings.plus(Finding.builder(DIRTY_UNSTABLE)
                .severity(Severity.ERROR)
                .message("the " + request.stage() + " attempt cannot publish over a moved base revision")
                .stage(request.stage())
                .build());
        var previous = instanceRecord.reemit(dirty);
        if (previous != null) {
            return previous;
        }
        return new MatchResult.Undecided(UndecidedReason.DIRTY_UNSTABLE, dirty.findings(), outcome(),
                request.retryToken());
    }

    private MatchResult admit() {
        MatchOutcome outcome = outcome();
        Outcome<SnapshotLease> retained = request.lease().retain(pins);
        if (retained instanceof Outcome.Missing<?> missing) {
            return new MatchResult.Undecided(UndecidedReason.BUDGET_EXHAUSTED, missing.findings().findings(),
                    outcome, null);
        }
        SnapshotLease child = ((Outcome.Found<SnapshotLease>) retained).value();
        if (request.stage() != Stage.SETTLE) {
            return new MatchResult.Admitted(request.stage(), request.siting(), outcome, child, notes);
        }
        ClaimPlan plan = ClaimPlanner.plan(request.blueprint(), request.siting(),
                bayBindings.values(), request.owner());
        if (plan instanceof ClaimPlan.Invalid invalid) {
            child.close();
            return new MatchResult.Undecided(UndecidedReason.DESIGN_DEFECT,
                    invalid.findings().findings(), outcome, null);
        }
        List<ClaimRow> rows = ((ClaimPlan.Rows) plan).rows();
        MatchResult settled = Objects.requireNonNull(settler, "settler")
                .settle(request, outcome, store.revision(), rows, instanceRecord, child);
        if (!(settled instanceof MatchResult.Formed)) {
            child.close();
        }
        return settled;
    }

    /**
     * The one comparison that decides whether this attempt may publish: the snapshot it read against the
     * snapshot that holds now. Runs after the units and before anything is written or settled.
     */
    private @Nullable MatchResult captureMoved() {
        return switch (CaptureGate.evaluate(request.lease())) {
            case CURRENT -> null;
            case CHANGED -> dropped(DropCause.SNAPSHOT_CHANGED, SNAPSHOT_CHANGED,
                    "the watched region changed after this " + request.stage() + " capture");
            case SUPERSEDED -> dropped(DropCause.CAPTURE_INVALID, CAPTURE_INVALID,
                    "this " + request.stage() + " capture was closed, or its world was replaced");
        };
    }

    private MatchOutcome outcome() {
        Map<ContextKey<?>, Object> values = new LinkedHashMap<>();
        for (ContextKey<?> key : context.keys()) {
            if (key.retention() == Retention.ATTEMPT) {
                continue;
            }
            Object value = context.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        Bindings bindings = combined instanceof Reply.Match match ? match.bindings() : Bindings.empty();
        return new MatchOutcome(request.stage(), request.siting(), view(), values, bindings, reads);
    }

    private MatchResult budgetExhausted() {
        return undecided(UndecidedReason.BUDGET_EXHAUSTED, BUDGET_EXHAUSTED,
                "the budget ran out before the " + request.stage() + " plan finished", null);
    }

    private MatchResult undecided(UndecidedReason reason, FindingCode code, String message,
                                  @Nullable RetryToken token) {
        Finding finding = Finding.builder(code)
                .severity(Severity.ERROR)
                .message(message)
                .stage(request.stage())
                .build();
        return new MatchResult.Undecided(reason, List.of(finding), outcome(), token);
    }

    private MatchResult dropped(DropCause cause, FindingCode code, String message) {
        Finding finding = Finding.builder(code)
                .severity(Severity.ERROR)
                .message(message)
                .stage(request.stage())
                .build();
        return new MatchResult.Dropped(cause, List.of(finding));
    }

    private MatchResult failed(FindingCode code, String message) {
        Finding finding = Finding.builder(code)
                .severity(Severity.ERROR)
                .message(message)
                .stage(request.stage())
                .build();
        return new MatchResult.Failed(List.of(finding));
    }

    private MatchResult workerFailed(RuntimeException failure) {
        Finding finding = Finding.builder(WORKER_FAILED)
                .severity(Severity.ERROR)
                .message("a unit threw: " + message(failure))
                .stage(request.stage())
                .build();
        return new MatchResult.Undecided(UndecidedReason.WORKER_FAILED, List.of(finding), outcome(), null);
    }

    private static String message(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getName() : message;
    }

    private boolean charge(long cells, int sections) {
        long bounded = Math.min(Integer.MAX_VALUE, Math.max(0L, cells));
        return request.budget().tryCharge((int) bounded, sections);
    }

    private boolean readable(SectionKey section) {
        return view().slot(section).readable();
    }

    private WorldView view() {
        return request.lease().view();
    }

    private <T> void put(ContextKey<T> key, Object value) {
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        draft.put(key, typed);
        absentKeys.remove(key);
    }

    private <T> void contribute(ContextKey<T> key, SectionKey section, Object partial) {
        @SuppressWarnings("unchecked")
        T typed = (T) partial;
        draft.forSection(section).contribute(key, typed);
        absentKeys.remove(key);
    }

    private <T> void refuse(ContextKey<T> key) {
        draft.refuse(key);
        absentKeys.remove(key);
    }

    private Set<SectionKey> sectionsOf(CuboidSet region) {
        TreeSet<SectionKey> sections = new TreeSet<>();
        for (Cuboid box : region.boxes()) {
            if (box.isEmpty()) {
                continue;
            }
            int minX = Math.floorDiv(box.min().x(), CELL_SIZE);
            int maxX = Math.floorDiv(box.max().x() - 1, CELL_SIZE);
            int minY = Math.floorDiv(box.min().y(), CELL_SIZE);
            int maxY = Math.floorDiv(box.max().y() - 1, CELL_SIZE);
            int minZ = Math.floorDiv(box.min().z(), CELL_SIZE);
            int maxZ = Math.floorDiv(box.max().z() - 1, CELL_SIZE);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        sections.add(new SectionKey(request.owner().world(), x, y, z));
                    }
                }
            }
        }
        return sections;
    }

    private int sectionCount(CuboidSet cells) {
        return cells.isEmpty() ? 0 : sectionsOf(cells).size();
    }

    private Cuboid sectionBounds(SectionKey section) {
        return Cuboid.of(section.x() * CELL_SIZE, section.y() * CELL_SIZE, section.z() * CELL_SIZE,
                (section.x() + 1) * CELL_SIZE, (section.y() + 1) * CELL_SIZE, (section.z() + 1) * CELL_SIZE);
    }

    private static ContextTier tierOf(Stage stage) {
        return switch (stage) {
            case DESIGN -> ContextTier.BLUEPRINT;
            case PROBE -> ContextTier.PROBE;
            case FORM -> ContextTier.FORM;
            case SETTLE, STALE -> ContextTier.SETTLE;
        };
    }

    private static Stage stageOf(ContextTier tier) {
        return switch (tier) {
            case BLUEPRINT -> Stage.DESIGN;
            case PROBE -> Stage.PROBE;
            case FORM -> Stage.FORM;
            case SETTLE -> Stage.SETTLE;
        };
    }

    private record SessionComputationContext(ContextView view, WorldView world,
                                             @Nullable SectionContext section)
            implements ComputationContext {

        @Override
        public <T> Outcome<T> read(ContextKey<T> key) {
            return view.require(key);
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box) {
            return world.occupants(box, OccupantFilter.any());
        }

        @Override
        public ContextView context() {
            return view;
        }
    }

    /** One computation's slice of a section; a query never leaves the slice's cells. */
    private record SectionSlice(SectionKey section, CuboidSet cells, WorldView view) implements SectionContext {

        @Override
        public List<OccupantSnapshot> occupants() {
            Cuboid bounds = cells.bounds();
            return bounds == null ? List.of() : occupants(bounds);
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box) {
            CuboidSet overlap = cells.intersect(box);
            Cuboid bounds = overlap.bounds();
            if (bounds == null) {
                return List.of();
            }
            List<OccupantSnapshot> found = new ArrayList<>();
            for (OccupantSnapshot occupant : view.occupants(bounds, OccupantFilter.any())) {
                for (Cuboid piece : overlap.boxes()) {
                    if (piece.intersects(occupant.footprint())) {
                        found.add(occupant);
                        break;
                    }
                }
            }
            return List.copyOf(found);
        }
    }

    /**
     * The attempt's view: the draft decides every key it touched, folded once, and the newest published
     * frame answers the rest.
     */
    private static final class AttemptView implements ContextView {

        private final InstanceId instance;
        private final ContextView published;
        private final ContextDraft draft;
        private final Set<ContextKey<?>> absent;
        private final ContextTier tier;

        private AttemptView(InstanceId instance, ContextView published, ContextDraft draft,
                            Set<ContextKey<?>> absent, ContextTier tier) {
            this.instance = instance;
            this.published = published;
            this.draft = draft;
            this.absent = absent;
            this.tier = tier;
        }

        @Override
        public ContextTier tier() {
            return tier;
        }

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            Objects.requireNonNull(key, "key");
            if (absent.contains(key)) {
                return null;
            }
            if (draft.publishes(key)) {
                return draft.read(key);
            }
            return published.get(key);
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            T value = get(key);
            if (value != null) {
                return new Outcome.Found<>(value, List.of(), FindingBundle.empty());
            }
            String message = "context key " + key.id() + " has no value at " + tier;
            Finding finding = Finding.builder(KEY_ABSENT)
                    .severity(Severity.ERROR)
                    .message(message)
                    .label(Label.primary(Locus.key(instance, key.id()), message))
                    .stage(stageOf(tier))
                    .build();
            return new Outcome.Missing<>(FindingBundle.of(finding));
        }

        @Override
        public boolean has(ContextKey<?> key) {
            return get(key) != null;
        }

        @Override
        public Set<ContextKey<?>> keys() {
            Set<ContextKey<?>> keys = new TreeSet<>(published.keys());
            keys.addAll(draft.keys());
            return Collections.unmodifiableSet(keys);
        }

        @Override
        public ContextView at(ContextTier narrower) {
            Objects.requireNonNull(narrower, "narrower");
            return narrower.ordinal() >= tier.ordinal() ? this : published.at(narrower);
        }
    }
}
