package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** The blueprints, findings and captured world the built-in remedy providers read. */
final class RemedyFixtures {

    static final NamespacedId stone = NamespacedId.of("minecraft", "stone_bricks");
    static final NamespacedId dirt = NamespacedId.of("minecraft", "dirt");
    static final TraitKey<Integer> rotation = new TraitKey<>(NamespacedId.of("test", "rotation"), Integer.class);

    static final World world = World.of(NamespacedId.of("test", "overworld"));
    static final SnapshotId snapshot = new SnapshotId(world, 1);

    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 2, 2, 2);

    private RemedyFixtures() {}

    static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    static CuboidSet oneCell(LatticePoint cell) {
        return CuboidSet.of(Cuboid.of(cell));
    }

    static CuboidSet box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return CuboidSet.of(Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ));
    }

    static Siting identity() {
        return Siting.of(LatticePoint.origin, Orientation.identity);
    }

    static Bay bay(String path, CuboidSet cells) {
        return Bay.of(id(path), cells, Coverage.everyCell, Footprint.box(), Sharing.shared);
    }

    static Blueprint occupantBlueprint(Bay bay, LatticePoint focusCell, Expectation<OccupantSnapshot> expectation) {
        return Blueprint.builder(id("blueprint")).bounds(bounds)
                .check(Check.of(id("check"), bay, Focuses.occupant(focusCell), expectation)).build();
    }

    static Blueprint occupantBlueprint(Expectation<OccupantSnapshot> expectation) {
        return occupantBlueprint(bay("bay", oneCell(LatticePoint.origin)), LatticePoint.origin, expectation);
    }

    static Blueprint bayBlueprint(Bay bay, Expectation<BayView> expectation) {
        return Blueprint.builder(id("blueprint")).bounds(bounds)
                .check(Check.of(id("check"), bay, Focuses.bay(), expectation)).build();
    }

    /** A per-cell threshold over two block kinds, so a cell holding neither is a deficit. */
    static Blueprint deficitBlueprint(CuboidSet bayCells) {
        return bayBlueprint(
            bay("bay", bayCells),
            Expectations.eachCell(Expectations.atLeast(1, Expectations.block(stone), Expectations.block(dirt)))
        );
    }

    static Blueprint deficitBlueprint() {
        return deficitBlueprint(box(0, 0, 0, 2, 1, 1));
    }

    /** Two checks, each its own bay and threshold, so one finding can hit two threshold sites. */
    static Blueprint twoThresholdBlueprint() {
        Expectation<BayView> threshold = Expectations
                .eachCell(Expectations.atLeast(1, Expectations.block(stone), Expectations.block(dirt)));
        return Blueprint.builder(id("blueprint")).bounds(bounds).check(
            Check.of(id("first_check"), bay("first", oneCell(LatticePoint.origin)), Focuses.bay(), threshold)
        ).check(
            Check.of(id("second_check"), bay("second", oneCell(LatticePoint.of(1, 0, 0))), Focuses.bay(), threshold)
        ).build();
    }

    static Finding finding(CodeEntry code, LatticePoint worldCell) {
        return finding(code, oneCell(worldCell));
    }

    static Finding finding(CodeEntry code, CuboidSet worldCells) {
        String message = code.code().text() + " at the world cell";
        return Finding.builder(code).phase(Phase.check).message(message)
                .label(Label.primary(Locus.world(worldCells), message)).build();
    }

    static Finding finding(FindingCode code, LatticePoint worldCell) {
        String message = code.text() + " at the world cell";
        return Finding.builder(code, Severity.error).phase(Phase.check).message(message)
                .label(Label.primary(Locus.world(oneCell(worldCell)), message)).build();
    }

    static Finding localFinding(CodeEntry code, LatticePoint localCell) {
        String message = code.code().text() + " at the local cell";
        return Finding.builder(code).phase(Phase.check).message(message)
                .label(Label.primary(Locus.local(oneCell(localCell)), message)).build();
    }

    /** A finding whose primary label carries no cell: a key locus. */
    static Finding keyedFinding(CodeEntry code) {
        return keyedFinding(code.code());
    }

    static Finding keyedFinding(FindingCode code) {
        String message = code.text() + " without a cell";
        Locus locus = Locus.key(new InstanceId(world, 1), id("key"));
        return Finding.builder(code, Severity.error).phase(Phase.check).message(message)
                .label(Label.primary(locus, message)).build();
    }

    static RemedyContext context(
        Blueprint blueprint,
        Siting siting,
        int maxResults,
        @Nullable WorldView view,
        @Nullable SolvePreferences preferences
    ) {
        MatchOutcome outcome = view == null
                ? null
                : new MatchOutcome(view, Map.of(), List.of(), ReadScope.none(), Spend.zero);
        return new RemedyContext(blueprint, siting, null, 0, outcome, null, maxResults, preferences);
    }

    static RemedyContext context(Blueprint blueprint, @Nullable WorldView view) {
        return context(blueprint, identity(), 8, view, null);
    }

    static RemedyContext context(
        Blueprint blueprint,
        int maxResults,
        @Nullable WorldView view,
        @Nullable SolvePreferences preferences
    ) {
        return context(blueprint, identity(), maxResults, view, preferences);
    }

    static RemedyContext context(Blueprint blueprint, Siting siting, @Nullable WorldView view) {
        return context(blueprint, siting, 8, view, null);
    }

    static RemedyContext preferred(Blueprint blueprint, WorldView view, SolvePreferences preferences) {
        return context(blueprint, identity(), 8, view, preferences);
    }

    static RemedyContext withoutCapture(Blueprint blueprint) {
        return context(blueprint, identity(), 8, null, null);
    }

    /** A capture holding one occupant per cell, plus the cells it cannot read. */
    static final class View implements WorldView {

        private static final Cuboid captureBounds = Cuboid.of(-16, -16, -16, 16, 16, 16);

        private final Map<LatticePoint, OccupantSnapshot> occupants = new TreeMap<>();
        private final Set<LatticePoint> unreadableCells = new TreeSet<>();

        View block(NamespacedId type, LatticePoint cell) {
            OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.block, cell);
            occupants.put(
                cell,
                new OccupantSnapshot(ref, new OccupantDescriptor(type, OccupantKind.block), Cuboid.of(cell), List.of())
            );
            return this;
        }

        View air(LatticePoint cell) {
            occupants.put(cell, OccupantSnapshot.air(snapshot, 0, cell));
            return this;
        }

        View unreadable(LatticePoint cell) {
            unreadableCells.add(cell);
            return this;
        }

        @Override
        public SnapshotId snapshotId() {
            return snapshot;
        }

        @Override
        public int frame() {
            return 0;
        }

        @Override
        public int depth() {
            return 0;
        }

        @Override
        public LatticePoint origin() {
            return LatticePoint.origin;
        }

        @Override
        public @Nullable WorldView parent() {
            return null;
        }

        @Override
        public int cellSize() {
            return 16;
        }

        @Override
        public Cuboid bounds() {
            return captureBounds;
        }

        @Override
        public SnapshotPolicy policy() {
            return SnapshotPolicy.minimal();
        }

        @Override
        public SectionKey sectionOf(LatticePoint cell) {
            return SectionKey.of(world, 0, cell, cellSize());
        }

        @Override
        public SectionSlot slot(SectionKey key) {
            Map<LatticePoint, OccupantSnapshot> present = new TreeMap<>();
            Map<LatticePoint, ReadRefusal> refused = new TreeMap<>();
            for (Map.Entry<LatticePoint, OccupantSnapshot> entry : occupants.entrySet()) {
                if (!entry.getValue().isAir() && sectionOf(entry.getKey()).equals(key)) {
                    present.put(entry.getKey(), entry.getValue());
                }
            }
            for (LatticePoint cell : unreadableCells) {
                if (sectionOf(cell).equals(key)) {
                    refused.put(cell, refusal());
                }
            }
            if (!refused.isEmpty()) {
                return new SectionSlot.Partial(SectionSnapshot.of(key, present, 0L), refused);
            }
            return present.isEmpty()
                    ? new SectionSlot.Air(0)
                    : new SectionSlot.Present(SectionSnapshot.of(key, present, 0L));
        }

        @Override
        public CellRead read(LatticePoint cell) {
            if (!captureBounds.contains(cell) || unreadableCells.contains(cell)) {
                return new CellRead.Unreadable(refusal());
            }
            OccupantSnapshot occupant = occupants.get(cell);
            return new CellRead.Found(occupant == null ? OccupantSnapshot.air(snapshot, 0, cell) : occupant);
        }

        @Override
        public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
            OccupantSnapshot occupant = occupants.get(ref.anchor());
            return occupant != null && occupant.ref().equals(ref) ? occupant : null;
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
            List<OccupantSnapshot> found = new ArrayList<>();
            for (OccupantSnapshot occupant : occupants.values()) {
                if (!occupant.isAir() && occupant.footprint().intersects(box) && filter.matches(occupant)) {
                    found.add(occupant);
                }
            }
            return List.copyOf(found);
        }

        @Override
        public @Nullable WorldView nested(LatticePoint cell) {
            return null;
        }

        @Override
        public long contentDigest(SectionKey key) {
            return 0L;
        }

        private static ReadRefusal refusal() {
            return new ReadRefusal(ReadStatus.notCaptured, Codes.unreadableRead.code());
        }
    }
}
