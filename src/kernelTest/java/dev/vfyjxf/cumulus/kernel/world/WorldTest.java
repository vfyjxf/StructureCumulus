package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTest {

    private static final SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("w")), 1);

    @Test
    void airIsARealOccupant() {
        OccupantSnapshot air = OccupantSnapshot.air(snapshot, 0, LatticePoint.origin);
        assertTrue(air.isAir());
        assertEquals(OccupantSnapshot.air, air.descriptor().type());
        assertEquals(1, air.footprint().volume());
        assertSame(air, air);
    }

    @Test
    void traitsAreTypedAndPresenceIsById() {
        TraitKey<Long> energy = new TraitKey<>(NamespacedId.of("energy"), Long.class);
        OccupantRef ref = new OccupantRef(snapshot, 0, NamespacedId.of("m"), OccupantKind.block, LatticePoint.origin);
        OccupantSnapshot occupant = new OccupantSnapshot(
            ref,
            new OccupantDescriptor(
                NamespacedId.of("m"),
                OccupantKind.block,
                Map.of("lit", "true"),
                List.of(NamespacedId.of("tag"))
            ),
            Cuboid.of(LatticePoint.origin),
            List.of(Trait.of(energy, 7L))
        );
        assertEquals(7L, occupant.trait(energy));
        assertTrue(occupant.has(energy));
        assertEquals("true", occupant.descriptor().property("lit"));
        assertEquals(List.of(NamespacedId.of("tag")), occupant.descriptor().tags());
    }

    @Test
    void sectionSnapshotAnswersBoxQueries() {
        SectionKey key = SectionKey.of(World.of(NamespacedId.of("w")), 0, LatticePoint.origin, 16);
        OccupantSnapshot block = TestBlocks.block(snapshot, NamespacedId.of("stone"), LatticePoint.of(1, 0, 0));
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(block.ref().anchor(), block), 0);
        assertEquals(block, section.occupantAt(LatticePoint.of(1, 0, 0)));
        assertEquals(List.of(block), section.occupants(Cuboid.of(0, 0, 0, 2, 1, 1), OccupantFilter.any()));
        assertNull(section.occupantAt(LatticePoint.of(2, 0, 0)));
    }

    @Test
    void entityWindowInflatesAndClamps() {
        EntityWindow window = EntityWindow.of(Cuboid.of(0, 0, 0, 1, 1, 1), NamespacedId.of("mob"));
        assertEquals(Cuboid.of(-3, -3, -3, 4, 4, 4), Objects.requireNonNull(window, "window").cells());
        EntityWindow edge = EntityWindow.of(
            Cuboid.of(
                LatticePoint.minXz,
                LatticePoint.minY,
                LatticePoint.minXz,
                LatticePoint.minXz + 1,
                LatticePoint.minY + 1,
                LatticePoint.minXz + 1
            ),
            NamespacedId.of("mob")
        );
        assertEquals(LatticePoint.minXz, Objects.requireNonNull(edge, "edge").cells().min().x());
    }

    @Test
    void statusOfAndRefusal() {
        SectionSlot refused = new SectionSlot.Refused(ReadStatus.denied, FindingCode.of(FindingCode.Family.occ, 1));
        assertFalse(refused.readable());
        assertEquals(ReadStatus.denied, refused.status());
        assertTrue(new SectionSlot.Air(0).readable());
    }

    private static final class TestBlocks {
        private static OccupantSnapshot block(SnapshotId snapshot, NamespacedId type, LatticePoint cell) {
            OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.block, cell);
            return new OccupantSnapshot(
                ref,
                new OccupantDescriptor(type, OccupantKind.block, Map.of(), List.of()),
                Cuboid.of(cell),
                List.of()
            );
        }
    }
}
