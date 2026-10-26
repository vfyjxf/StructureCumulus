package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** The shipped focus kinds. */
public final class Focuses {

    public static Focus<OccupantSnapshot> occupant(LatticePoint cell) {
        return new OccupantFocus(cell);
    }

    public static Focus<BayView> bay() {
        return new BayFocus();
    }

    public static Focus<List<OccupantSnapshot>> allCells() {
        return new AllCellsFocus();
    }

    public static Focus<OccupantSnapshot> entity(NamespacedId type) {
        return new EntityFocus(type);
    }

    public static Focus<WorldView> nested(LatticePoint cell) {
        return new NestedFocus(cell);
    }

    public static Focus<DoorView> door(LatticePoint origin) {
        return new DoorFocus(origin);
    }

    public static <T> Focus<T> context(ContextKey<T> key) {
        return new ContextFocus<>(key);
    }

    private record OccupantFocus(LatticePoint cell) implements Focus<OccupantSnapshot> {

        @Override
        public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
            OccupantSnapshot occupant = ctx.occupantAt(cell);
            if (occupant == null) {
                return new FocusResult.Unavailable<>(missing(ctx, "SC-OCC-0001: unreadable cell " + cell));
            }
            List<OccupantSnapshot> subjects = occupant.isAir() ? List.of() : List.of(occupant);
            return new FocusResult.Taken<>(occupant, subjects, FindingBundle.empty());
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return CuboidSet.of(Cuboid.of(cell));
        }
    }

    private record BayFocus() implements Focus<BayView> {

        @Override
        public FocusResult<BayView> take(FocusContext ctx) {
            BayView view = ctx.bay();
            return new FocusResult.Taken<>(view, view.occupants(), FindingBundle.empty());
        }

        @Override
        public Class<BayView> subject() {
            return BayView.class;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return bayCells;
        }
    }

    private record AllCellsFocus() implements Focus<List<OccupantSnapshot>> {

        @Override
        public FocusResult<List<OccupantSnapshot>> take(FocusContext ctx) {
            List<OccupantSnapshot> occupants = new ArrayList<>();
            List<OccupantSnapshot> subjects = new ArrayList<>();
            for (LatticePoint cell : cellsOf(ctx.cells())) {
                OccupantSnapshot occupant = ctx.occupantAt(cell);
                if (occupant == null) {
                    return new FocusResult.Unavailable<>(
                            missing(ctx, "SC-OCC-0001: unreadable cell " + cell));
                }
                occupants.add(occupant);
                if (!occupant.isAir()) {
                    subjects.add(occupant);
                }
            }
            return new FocusResult.Taken<>(List.copyOf(occupants), List.copyOf(subjects),
                    FindingBundle.empty());
        }

        @Override
        public Class<List<OccupantSnapshot>> subject() {
            @SuppressWarnings("unchecked")
            Class<List<OccupantSnapshot>> type = (Class<List<OccupantSnapshot>>) (Class<?>) List.class;
            return type;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return bayCells;
        }
    }

    private record EntityFocus(NamespacedId type)
            implements Focus<OccupantSnapshot> {

        @Override
        public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
            CuboidSet worldCells =
                    ctx.siting().toWorld(ctx.cells());
            Cuboid bounds = worldCells == null ? null : worldCells.bounds();
            if (bounds == null) {
                return new FocusResult.Unavailable<>(missing(ctx, "SC-OCC-0001: empty bay"));
            }
            Map<OccupantRef, OccupantSnapshot> found = new LinkedHashMap<>();
            for (OccupantSnapshot candidate : ctx.view().occupants(bounds, OccupantFilter.ofType(type))) {
                if (candidate.ref().kind().equals(OccupantKind.ENTITY)
                        && worldCells.intersects(candidate.footprint().asSet())) {
                    found.putIfAbsent(candidate.ref(), candidate);
                }
            }
            List<OccupantSnapshot> entities = new ArrayList<>(found.values());
            entities.sort(Comparator.comparing(OccupantSnapshot::ref));
            if (entities.isEmpty()) {
                return new FocusResult.Unavailable<>(missing(ctx,
                        "SC-OCC-0001: no " + type + " in the bay"));
            }
            if (entities.size() > 1) {
                return new FocusResult.Unavailable<>(missing(ctx,
                        "SC-OCC-0001: more than one " + type + " in the bay"));
            }
            OccupantSnapshot entity = entities.get(0);
            return new FocusResult.Taken<>(entity, List.of(entity), footprintWarning(ctx, entity));
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return CuboidSet.empty();
        }

        @Override
        public ReadScope reads(CuboidSet bayCells) {
            Cuboid bounds = bayCells.bounds();
            if (bounds == null) {
                return ReadScope.none();
            }
            EntityWindow window = EntityWindow.of(bounds, type);
            return window == null ? ReadScope.none()
                    : ReadScope.none().withWindows(List.of(window));
        }
    }

    private record NestedFocus(LatticePoint cell) implements Focus<WorldView> {

        @Override
        public FocusResult<WorldView> take(FocusContext ctx) {
            OccupantSnapshot occupant = ctx.occupantAt(cell);
            LatticePoint world = ctx.siting().toWorld(cell);
            WorldView child = world == null ? null : ctx.view().nested(world);
            if (occupant == null || child == null) {
                return new FocusResult.Unavailable<>(missing(ctx,
                        "SC-OCC-0001: no nested structure at " + cell));
            }
            return new FocusResult.Taken<>(child, List.of(occupant), FindingBundle.empty());
        }

        @Override
        public Class<WorldView> subject() {
            return WorldView.class;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return CuboidSet.of(Cuboid.of(cell));
        }
    }

    private record DoorFocus(LatticePoint origin) implements Focus<DoorView> {

        @Override
        public FocusResult<DoorView> take(FocusContext ctx) {
            OccupantSnapshot first = ctx.occupantAt(origin);
            LatticePoint upper = origin.offset(0, 1, 0);
            OccupantSnapshot second = upper == null ? null : ctx.occupantAt(upper);
            if (first == null || second == null || first.isAir() || second.isAir()) {
                return new FocusResult.Unavailable<>(missing(ctx,
                        "SC-OCC-0001: a door needs two readable non-air halves"));
            }
            DoorView door = new DoorView(first, second, ctx.bay());
            return new FocusResult.Taken<>(door, List.of(first, second), FindingBundle.empty());
        }

        @Override
        public Class<DoorView> subject() {
            return DoorView.class;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return bayCells;
        }
    }

    private record ContextFocus<T>(ContextKey<T> key) implements Focus<T> {

        @Override
        public FocusResult<T> take(FocusContext ctx) {
            Outcome<T> value = ctx.context().require(key);
            if (value instanceof Outcome.Found<T> found) {
                return new FocusResult.Taken<>(found.value(), List.of(), FindingBundle.empty());
            }
            Locus locus = Locus.key(ctx.instance(), key.id());
            Finding finding = Finding.builder(FindingCode.of(FindingCode.Family.CTX, 7),
                            Severity.ERROR)
                    .phase(ctx.phase())
                    .message("context key " + key.id() + " is absent")
                    .label(Label.primary(locus, "no value for " + key.id()))
                    .build();
            return new FocusResult.Unavailable<>(FindingBundle.of(List.of(finding)));
        }

        @Override
        public Class<T> subject() {
            return key.type();
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return CuboidSet.empty();
        }

        @Override
        public boolean bindsSubjects() {
            return false;
        }

        @Override
        public ReadScope reads(CuboidSet bayCells) {
            return ReadScope.none().withKeys(Set.of(key));
        }
    }

    private static FindingBundle missing(FocusContext ctx, String message) {
        return Check.missing(ctx, message);
    }

    private static FindingBundle footprintWarning(FocusContext ctx, OccupantSnapshot occupant) {
        for (Cuboid box : occupant.footprint().asSet().boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        if (ctx.view().statusOf(LatticePoint.of(x, y, z)) != ReadStatus.READABLE) {
                            Finding warning = Finding.builder(Codes.FOOTPRINT_CLIPPED)
                                    .phase(ctx.phase())
                                    .message("the subject footprint leaves the snapshot")
                                    .label(Label.primary(Locus.world(occupant.footprint().asSet()),
                                            "only the readable part was compared"))
                                    .build();
                            return FindingBundle.of(List.of(warning));
                        }
                    }
                }
            }
        }
        return FindingBundle.empty();
    }

    static List<LatticePoint> cellsOf(CuboidSet cells) {
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

    private Focuses() {
    }
}
