package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorsTest {

    @Test
    void headingPropertyAcceptsBothCases() {
        LatticePoint origin = LatticePoint.of(0, 0, 0);
        Blueprint blueprint = anchoredBlueprint();
        SitingResult lower = Anchors.resolve(blueprint, origin, view(origin, "north"));
        SitingResult upper = Anchors.resolve(blueprint, origin, view(origin, "NORTH"));
        assertTrue(lower.succeeded());
        assertTrue(upper.succeeded());
        assertTrue(lower.findings().isEmpty());
        assertEquals(Outcome.found(Siting.of(origin, Orientation.identity)), lower.outcome());
        assertEquals(lower.outcome(), upper.outcome());
        assertEquals(lower.findings(), upper.findings());
    }

    private static Blueprint anchoredBlueprint() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> check = Check.of(id("cells"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        return Blueprint.builder(id("anchored")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                .anchor(new AnchorSpec(AnchorRoles.primary, "facing", "north"))
                .orientation(OrientationPolicy.builder(AnchorRoles.primary).allowTurns(Turn.none).build()).build();
    }

    private static WorldView view(LatticePoint origin, String facing) {
        SnapshotId snapshot = new SnapshotId(World.of(id("world")), 1);
        OccupantRef ref = new OccupantRef(snapshot, 0, id("anchor"), OccupantKind.block, origin);
        OccupantSnapshot occupant = new OccupantSnapshot(
            ref,
            new OccupantDescriptor(id("anchor"), OccupantKind.block, Map.of("facing", facing), List.of()),
            Cuboid.of(origin),
            List.of()
        );
        return new SingleOccupantView(snapshot, origin, occupant);
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static final class SingleOccupantView implements WorldView {

        private final SnapshotId snapshot;
        private final LatticePoint origin;
        private final OccupantSnapshot occupant;

        private SingleOccupantView(SnapshotId snapshot, LatticePoint origin, OccupantSnapshot occupant) {
            this.snapshot = snapshot;
            this.origin = origin;
            this.occupant = occupant;
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
            return origin;
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
        public SectionKey sectionOf(LatticePoint cell) {
            return SectionKey.of(snapshot.world(), frame(), cell, cellSize());
        }

        @Override
        public SectionSlot slot(SectionKey key) {
            return new SectionSlot.Air(0);
        }

        @Override
        public @Nullable OccupantSnapshot occupant(LatticePoint cell) {
            return origin.equals(cell) ? occupant : null;
        }

        @Override
        public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
            return ref.snapshot().equals(snapshot) && ref.anchor().equals(origin) ? occupant : null;
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
            return box.contains(origin) && filter.matches(occupant) ? List.of(occupant) : List.of();
        }

        @Override
        public @Nullable WorldView nested(LatticePoint cell) {
            return null;
        }

        @Override
        public long contentDigest(SectionKey key) {
            return 0;
        }
    }
}
