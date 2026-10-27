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
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
            CellRead read = ctx.read(cell);
            if (read instanceof CellRead.Unreadable unreadable) {
                return new FocusResult.Unavailable<>(
                    FindingBundle.of(
                        List.of(
                            Check.relay(ctx.phase(), unreadable.refusal(), ctx.locus(), "required read unavailable")
                        )
                    )
                );
            }
            OccupantSnapshot occupant = ((CellRead.Found) read).occupant();
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
            Map<LatticePoint, ReadRefusal> unreadable = new TreeMap<>();
            for (LatticePoint cell : cellsOf(ctx.cells())) {
                CellRead read = ctx.read(cell);
                if (read instanceof CellRead.Unreadable refused) {
                    unreadable.put(cell, refused.refusal());
                    continue;
                }
                OccupantSnapshot occupant = ((CellRead.Found) read).occupant();
                occupants.add(occupant);
                if (!occupant.isAir()) {
                    subjects.add(occupant);
                }
            }
            if (!unreadable.isEmpty()) {
                return new FocusResult.Unavailable<>(
                    Check.relayAll(ctx.phase(), unreadable, Locus::local, "required read unavailable")
                );
            }
            return new FocusResult.Taken<>(List.copyOf(occupants), List.copyOf(subjects), FindingBundle.empty());
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

    private record EntityFocus(NamespacedId type) implements Focus<OccupantSnapshot> {

        @Override
        public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
            CuboidSet worldCells = ctx.siting().toWorld(ctx.cells());
            if (worldCells == null) {
                return new FocusResult.Unavailable<>(missing(ctx, "SC-OCC-0001: empty bay"));
            }
            Cuboid bounds = worldCells.bounds();
            if (bounds == null) {
                return new FocusResult.Unavailable<>(missing(ctx, "SC-OCC-0001: empty bay"));
            }
            Map<OccupantRef, OccupantSnapshot> found = new LinkedHashMap<>();
            for (OccupantSnapshot candidate : ctx.view().occupants(bounds, OccupantFilter.ofType(type))) {
                if (candidate.ref().kind().equals(OccupantKind.entity)
                        && worldCells.containsAll(candidate.footprint().asSet())) {
                    found.putIfAbsent(candidate.ref(), candidate);
                }
            }
            List<OccupantSnapshot> entities = new ArrayList<>(found.values());
            entities.sort(Comparator.comparing(OccupantSnapshot::ref));
            if (entities.isEmpty()) {
                return new FocusResult.Unavailable<>(missing(ctx, "SC-OCC-0001: no " + type + " in the bay"));
            }
            if (entities.size() > 1) {
                return new FocusResult.Unavailable<>(
                    missing(ctx, "SC-OCC-0001: more than one " + type + " in the bay")
                );
            }
            OccupantSnapshot entity = entities.get(0);
            FindingBundle gap = footprintGap(ctx, entity);
            if (!gap.isEmpty()) {
                return new FocusResult.Unavailable<>(gap);
            }
            return new FocusResult.Taken<>(entity, List.of(entity), FindingBundle.empty());
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
            ReadScope scope = ReadScope.cells(bayCells).withKinds(Set.of(OccupantKind.entity));
            Cuboid bounds = bayCells.bounds();
            if (bounds == null) {
                return scope;
            }
            EntityWindow window = EntityWindow.of(bounds, type);
            return window == null ? scope : scope.withWindows(List.of(window));
        }
    }

    private record NestedFocus(LatticePoint cell) implements Focus<WorldView> {

        @Override
        public FocusResult<WorldView> take(FocusContext ctx) {
            LatticePoint world = ctx.siting().toWorld(cell);
            WorldView child = world == null ? null : ctx.view().nested(world);
            if (child == null) {
                Locus locus = world == null
                        ? Locus.local(CuboidSet.of(Cuboid.of(cell)))
                        : Locus.world(CuboidSet.of(Cuboid.of(world)));
                Finding finding = Finding.builder(Codes.nestedCut).phase(ctx.phase())
                        .message("the nested view at " + cell + " was cut")
                        .label(Label.primary(locus, "nested view cut")).build();
                return new FocusResult.Unavailable<>(FindingBundle.of(List.of(finding)));
            }
            return new FocusResult.Taken<>(child, List.of(), FindingBundle.empty());
        }

        @Override
        public Class<WorldView> subject() {
            return WorldView.class;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return CuboidSet.of(Cuboid.of(cell));
        }

        @Override
        public ReadScope reads(CuboidSet bayCells) {
            return ReadScope.cells(staticCells(bayCells)).withKinds(Set.of(OccupantKind.nested)).withDepth(1);
        }
    }

    private record DoorFocus(LatticePoint origin) implements Focus<DoorView> {

        @Override
        public FocusResult<DoorView> take(FocusContext ctx) {
            LatticePoint upper = origin.offset(0, 1, 0);
            if (upper == null) {
                return new FocusResult.Unavailable<>(
                    FindingBundle.of(
                        List.of(
                            Check.unreadable(ctx.phase(), ctx.locus(), "the door's upper half leaves the lattice", null)
                        )
                    )
                );
            }
            Map<LatticePoint, ReadRefusal> unreadable = new TreeMap<>();
            CellRead firstRead = ctx.read(origin);
            CellRead secondRead = ctx.read(upper);
            OccupantSnapshot first = null;
            OccupantSnapshot second = null;
            if (firstRead instanceof CellRead.Found found) {
                first = found.occupant();
            } else {
                unreadable.put(origin, ((CellRead.Unreadable) firstRead).refusal());
            }
            if (secondRead instanceof CellRead.Found found) {
                second = found.occupant();
            } else {
                unreadable.put(upper, ((CellRead.Unreadable) secondRead).refusal());
            }
            if (!unreadable.isEmpty()) {
                return new FocusResult.Unavailable<>(
                    Check.relayAll(ctx.phase(), unreadable, Locus::local, "required read unavailable")
                );
            }
            if (first == null || second == null || first.isAir() || second.isAir()) {
                return new FocusResult.Unavailable<>(
                    missing(ctx, "SC-OCC-0001: a door needs two readable non-air halves")
                );
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
            Finding finding = Finding.builder(FindingCode.of(FindingCode.Family.ctx, 7), Severity.error)
                    .phase(ctx.phase()).message("context key " + key.id() + " is absent")
                    .label(Label.primary(locus, "no value for " + key.id())).build();
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

    private static FindingBundle footprintGap(FocusContext ctx, OccupantSnapshot occupant) {
        Map<ReadRefusal, CuboidSet> groups = new LinkedHashMap<>();
        for (Cuboid box : occupant.footprint().asSet().boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        CellRead read = ctx.view().read(cell);
                        if (read instanceof CellRead.Unreadable refused) {
                            groups.merge(refused.refusal(), Cuboid.of(cell).asSet(), CuboidSet::union);
                        }
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            return FindingBundle.empty();
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<ReadRefusal, CuboidSet> group : groups.entrySet()) {
            Finding.Builder builder = Finding.builder(Codes.footprintClipped).phase(ctx.phase())
                    .message("the subject footprint leaves the snapshot")
                    .label(Label.primary(Locus.world(group.getValue()), "only the readable part was compared"))
                    .note("refusal: " + group.getKey().status() + " (" + group.getKey().code().text() + ")");
            findings.add(builder.build());
        }
        return FindingBundle.of(findings);
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

    private Focuses() {}
}
