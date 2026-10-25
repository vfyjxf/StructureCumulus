package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.occupant.NestedLevelSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantKind;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** The shipped focuses; each one binds its subject once and never fabricates one. */
public final class Focuses {

    static final FindingCode ABSENT = FindingCode.of(FindingCode.Family.FOC, 101);
    static final FindingCode AMBIGUOUS = FindingCode.of(FindingCode.Family.FOC, 201);
    static final FindingCode UNREADABLE_CELL = FindingCode.of(FindingCode.Family.FOC, 301);
    static final FindingCode MISSING_CONTRIBUTION = FindingCode.of(FindingCode.Family.FOC, 302);

    private static final Class<List<OccupantSnapshot>> CELL_LIST = cellList();
    private static final CuboidSet NO_CELLS = CuboidSet.empty();

    private Focuses() {
    }

    public static Focus<OccupantSnapshot> occupant(LatticeKey cell) {
        Objects.requireNonNull(cell, "cell");
        LatticePoint point = cell.toPoint();
        CuboidSet cells = CuboidSet.of(Cuboid.of(point));
        Locus locus = Locus.world(cells);
        String message = "no readable snapshot at " + point;
        return focus(OccupantSnapshot.class, DecisionKind.SUBJECT, ignored -> new ReadScope(cells, Set.of(), List.of()),
                bay -> NO_CELLS, declared(UNREADABLE_CELL, locus, message), ctx -> {
                    OccupantSnapshot snapshot = ctx.view().occupant(point);
                    if (snapshot == null) {
                        return missing(ctx, UNREADABLE_CELL, locus, message);
                    }
                    return new Outcome.Found<>(snapshot, subjectsOf(snapshot), FindingBundle.empty());
                });
    }

    public static Focus<BayView> bay() {
        Locus locus = Locus.local(CuboidSet.empty());
        return focus(BayView.class, DecisionKind.CUBIC, cells -> new ReadScope(CuboidSet.of(cells), Set.of(), List.of()), Focuses::bayCells,
                declared(ABSENT, locus, "the bay binds no subject"), ctx ->
                        new Outcome.Found<>(ctx.bay(), List.of(), FindingBundle.empty()));
    }

    public static Focus<List<OccupantSnapshot>> allCells() {
        Locus locus = Locus.local(CuboidSet.empty());
        return focus(CELL_LIST, DecisionKind.CUBIC, cells -> new ReadScope(CuboidSet.of(cells), Set.of(), List.of()), Focuses::bayCells,
                declared(UNREADABLE_CELL, locus, "a bay cell has no readable snapshot"), ctx -> {
                    List<OccupantSnapshot> snapshots = new ArrayList<>();
                    List<OccupantRef> subjects = new ArrayList<>();
                    for (LatticePoint point : ctx.bay().points()) {
                        OccupantSnapshot snapshot = ctx.bay().occupantAt(point);
                        if (snapshot == null) {
                            return missing(ctx, UNREADABLE_CELL, Locus.world(CuboidSet.of(Cuboid.of(point))),
                                    "no readable snapshot at " + point);
                        }
                        snapshots.add(snapshot);
                        OccupantRef ref = snapshot.ref();
                        if (!snapshot.isAir() && !subjects.contains(ref)) {
                            subjects.add(ref);
                        }
                    }
                    return new Outcome.Found<>(List.copyOf(snapshots), List.copyOf(subjects),
                            FindingBundle.empty());
                });
    }

    public static <T> Focus<T> context(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        Locus locus = Locus.local(CuboidSet.empty());
        return focus(key.type(), DecisionKind.PRESENCE, ignored -> ReadScope.none().withKeys(Set.of(key)),
                bay -> NO_CELLS, declared(MISSING_CONTRIBUTION, locus, "context key " + key.id() + " is absent"),
                ctx -> ctx.context().require(key));
    }

    public static Focus<EntitySnapshot> entity(UUID id) {
        Objects.requireNonNull(id, "id");
        Locus locus = Locus.local(CuboidSet.empty());
        String absent = "no entity " + id + " in the bay";
        return focus(EntitySnapshot.class, DecisionKind.SUBJECT, ignored -> ReadScope.none(), bay -> NO_CELLS,
                declared(ABSENT, locus, absent), ctx -> {
                    List<OccupantSnapshot> candidates =
                            ctx.view().occupants(ctx.cells(), OccupantFilter.ofKind(OccupantKind.ENTITY));
                    if (candidates.isEmpty()) {
                        return missing(ctx, ABSENT, locus, absent);
                    }
                    if (candidates.size() > 1) {
                        return missing(ctx, AMBIGUOUS, Locus.world(ctx.cells().asSet()),
                                candidates.size() + " entities compete for " + id);
                    }
                    OccupantSnapshot only = candidates.get(0);
                    return new Outcome.Found<>(new EntitySnapshot(id, only), List.of(only.ref()),
                            FindingBundle.empty());
                });
    }

    public static Focus<NestedView> nested(LatticeKey cell) {
        Objects.requireNonNull(cell, "cell");
        LatticePoint point = cell.toPoint();
        CuboidSet cells = CuboidSet.of(Cuboid.of(point));
        Locus locus = Locus.world(cells);
        String absent = "no nested level at " + point;
        return focus(NestedView.class, DecisionKind.SUBJECT, ignored -> new ReadScope(cells, Set.of(), List.of()),
                bay -> NO_CELLS, declared(ABSENT, locus, absent), ctx -> {
                    NestedLevelSnapshot level = ctx.view().nested(point);
                    if (level != null) {
                        return new Outcome.Found<>(new NestedView(level, ctx.bay()), List.of(level.parent()),
                                FindingBundle.empty());
                    }
                    if (ctx.view().occupant(point) == null) {
                        return missing(ctx, UNREADABLE_CELL, locus, "no readable snapshot at " + point);
                    }
                    return missing(ctx, ABSENT, locus, absent);
                });
    }

    public static Focus<DoorView> door(LatticeKey origin) {
        Objects.requireNonNull(origin, "origin");
        LatticePoint point = origin.toPoint();
        LatticePoint upper = point.offset(0, 1, 0);
        CuboidSet doors = upper == null
                ? CuboidSet.of(Cuboid.of(point))
                : CuboidSet.of(Cuboid.of(point), Cuboid.of(upper));
        Locus locus = Locus.world(doors);
        String absent = "no door at " + point;
        return focus(DoorView.class, DecisionKind.SUBJECT, ignored -> new ReadScope(doors, Set.of(), List.of()),
                cells -> doors.intersect(CuboidSet.of(cells)), declared(ABSENT, locus, absent), ctx -> {
                    OccupantSnapshot lower = ctx.view().occupant(point);
                    if (lower == null) {
                        return missing(ctx, UNREADABLE_CELL, Locus.world(CuboidSet.of(Cuboid.of(point))),
                                "no readable snapshot at " + point);
                    }
                    if (upper == null) {
                        return missing(ctx, ABSENT, locus, "no room for an upper half above " + point);
                    }
                    OccupantSnapshot top = ctx.view().occupant(upper);
                    if (top == null) {
                        return missing(ctx, UNREADABLE_CELL, Locus.world(CuboidSet.of(Cuboid.of(upper))),
                                "no readable snapshot at " + upper);
                    }
                    if (lower.isAir() || top.isAir()) {
                        return missing(ctx, ABSENT, locus, absent);
                    }
                    return new Outcome.Found<>(new DoorView(lower.ref(), top.ref(), ctx.bay()),
                            List.of(lower.ref(), top.ref()), FindingBundle.empty());
                });
    }

    private static CuboidSet bayCells(Cuboid bayCells) {
        return CuboidSet.of(bayCells);
    }

    private static <T> Focus<T> focus(Class<T> subject, DecisionKind kind, Function<Cuboid, ReadScope> reads,
                                      Function<Cuboid, CuboidSet> decisionCells, FindingBundle missing,
                                      Function<FocusContext, Outcome<T>> body) {
        return new Carrier<>(subject, kind, reads, decisionCells, missing, body);
    }

    private static FindingBundle declared(FindingCode code, Locus locus, String message) {
        return FindingBundle.of(Finding.builder(code)
                .severity(Severity.ERROR)
                .message(message)
                .label(locus, message)
                .stage(Stage.DESIGN)
                .build());
    }

    private static <T> Outcome<T> missing(FocusContext ctx, FindingCode code, Locus locus, String message) {
        Finding finding = Finding.builder(code)
                .severity(Severity.ERROR)
                .message(message)
                .label(locus, message)
                .stage(ctx.stage())
                .build();
        return new Outcome.Missing<>(FindingBundle.of(finding));
    }

    private static List<OccupantRef> subjectsOf(OccupantSnapshot snapshot) {
        return snapshot.isAir() ? List.of() : List.of(snapshot.ref());
    }

    @SuppressWarnings("unchecked")
    private static Class<List<OccupantSnapshot>> cellList() {
        return (Class<List<OccupantSnapshot>>) (Class<?>) List.class;
    }

    private record Carrier<T>(Class<T> subject, DecisionKind decisionKind, Function<Cuboid, ReadScope> reads,
                              Function<Cuboid, CuboidSet> decisionCells, FindingBundle missing,
                              Function<FocusContext, Outcome<T>> body) implements Focus<T> {

        @Override
        public Outcome<T> take(FocusContext ctx) {
            return body.apply(Objects.requireNonNull(ctx, "ctx"));
        }

        @Override
        public ReadScope reads(Cuboid bayCells) {
            return reads.apply(Objects.requireNonNull(bayCells, "bayCells"));
        }

        @Override
        public CuboidSet staticDecisionCells(Cuboid bayCells) {
            Objects.requireNonNull(bayCells, "bayCells");
            return decisionCells.apply(bayCells);
        }

        @Override
        public FindingBundle describeMissing() {
            return missing;
        }
    }
}
