package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelSnapshotTest {

    @Test
    void airIsSynthesizedOnlyInsideTheCapturedBounds() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 1);
        SectionKey key = SectionKey.of(snapshot.world(), 0, LatticePoint.origin, 16);
        OccupantSnapshot stone = occupant(snapshot, LatticePoint.origin, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(LatticePoint.origin, stone), 42L);
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(key, new SectionSlot.Present(section))
        );

        assertSame(stone, view.occupant(LatticePoint.origin));
        assertEquals(
            OccupantSnapshot.air,
            Objects.requireNonNull(view.occupant(LatticePoint.of(1, 0, 0)), "air").descriptor().type()
        );
        assertNull(view.occupant(LatticePoint.of(2, 0, 0)));
        assertEquals(ReadStatus.outOfBounds, view.slot(view.sectionOf(LatticePoint.of(16, 0, 0))).status());
        assertTrue(view.slot(key).readable());
        assertEquals(42L, view.contentDigest(key));
        assertFalse(view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.any()).isEmpty());
    }

    private static OccupantSnapshot occupant(SnapshotId snapshot, LatticePoint cell, String type) {
        NamespacedId id = NamespacedId.of("test", type);
        OccupantRef ref = new OccupantRef(snapshot, 0, id, OccupantKind.block, cell);
        return new OccupantSnapshot(
            ref,
            new OccupantDescriptor(id, OccupantKind.block, Map.of(), List.of()),
            Cuboid.of(cell),
            List.of()
        );
    }
}
