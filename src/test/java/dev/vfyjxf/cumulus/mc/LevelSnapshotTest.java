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

    @Test
    void blockBeatsLargerEntityWhileTheEntityAnswersOverAir() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 1);
        LatticePoint foot = LatticePoint.origin;
        LatticePoint head = LatticePoint.of(0, 1, 0);
        SectionKey key = SectionKey.of(snapshot.world(), 0, foot, 16);
        OccupantSnapshot stone = occupant(snapshot, foot, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(foot, stone), 42L);
        OccupantSnapshot zombie = entity(snapshot, foot, "zombie", Cuboid.of(0, 0, 0, 1, 2, 1));
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 2, 3, 2),
            Map.of(key, new SectionSlot.Present(section)),
            List.of(zombie)
        );

        assertSame(stone, view.occupant(foot));
        assertSame(zombie, view.occupant(head));
        assertTrue(Objects.requireNonNull(view.occupant(LatticePoint.of(0, 2, 0)), "air").isAir());

        SectionSlot slot = view.slot(key);
        assertTrue(slot instanceof SectionSlot.Present);
        SectionSnapshot captured = ((SectionSlot.Present) slot).section();
        assertSame(stone, captured.occupantAt(foot));
        assertEquals(42L, view.contentDigest(key));

        assertSame(stone, view.occupant(stone.ref()));
        assertSame(zombie, view.occupant(zombie.ref()));
        assertNull(view.occupant(new OccupantRef(snapshot, 1, zombie.descriptor().type(), OccupantKind.entity, foot)));

        assertEquals(
            List.of(zombie),
            view.occupants(Cuboid.of(0, 0, 0, 2, 3, 2), OccupantFilter.ofKind(OccupantKind.entity))
        );
        assertEquals(List.of(stone, zombie), view.occupants(Cuboid.of(0, 0, 0, 2, 3, 2), OccupantFilter.any()));
    }

    @Test
    void equalFootprintsTieBreakByRef() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 2);
        LatticePoint foot = LatticePoint.origin;
        SectionKey key = SectionKey.of(snapshot.world(), 0, foot, 16);
        OccupantSnapshot stone = occupant(snapshot, foot, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(foot, stone), 1L);
        OccupantSnapshot chicken = entity(snapshot, foot, "chicken", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(key, new SectionSlot.Present(section)),
            List.of(chicken)
        );

        assertSame(stone, view.occupant(foot));
        assertSame(chicken, view.occupant(chicken.ref()));
        assertSame(stone, view.occupant(stone.ref()));
    }

    @Test
    void equalEntityFootprintsTieBreakByRef() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 3);
        LatticePoint foot = LatticePoint.origin;
        OccupantSnapshot alpha = entity(snapshot, foot, "alpha", Cuboid.of(0, 0, 0, 1, 1, 1));
        OccupantSnapshot beta = entity(snapshot, foot, "beta", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of(), List.of(beta, alpha));

        assertSame(alpha, view.occupant(foot));
    }

    @Test
    void entityWinsOverAir() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 4);
        OccupantSnapshot zombie = entity(snapshot, LatticePoint.origin, "zombie", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of(), List.of(zombie));

        assertSame(zombie, view.occupant(LatticePoint.origin));
        assertTrue(Objects.requireNonNull(view.occupant(LatticePoint.of(1, 0, 0)), "air").isAir());
    }

    @Test
    void entityFootprintLeavesTheCaptureWholeAndResolvesByRef() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 5);
        LatticePoint anchor = LatticePoint.of(-1, 0, 0);
        OccupantSnapshot zombie = entity(snapshot, anchor, "zombie", Cuboid.of(-1, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of(), List.of(zombie));

        assertSame(zombie, view.occupant(LatticePoint.origin));
        assertEquals(Cuboid.of(-1, 0, 0, 1, 1, 1), zombie.footprint());
        assertSame(zombie, view.occupant(zombie.ref()));
        assertNull(view.occupant(anchor));
        assertEquals(
            List.of(zombie),
            view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.ofKind(OccupantKind.entity))
        );
    }

    @Test
    void overlappingEntitiesPreferTheSmallerFootprint() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 6);
        LatticePoint foot = LatticePoint.origin;
        OccupantSnapshot big = entity(snapshot, foot, "big", Cuboid.of(0, 0, 0, 2, 2, 1));
        OccupantSnapshot small = entity(snapshot, foot, "small", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 3, 3, 2), Map.of(), List.of(big, small));

        assertSame(small, view.occupant(foot));
        assertSame(big, view.occupant(LatticePoint.of(1, 1, 0)));
        assertEquals(2, view.occupants(Cuboid.of(0, 0, 0, 3, 3, 2), OccupantFilter.any()).size());
    }

    private static OccupantSnapshot entity(SnapshotId snapshot, LatticePoint anchor, String type, Cuboid footprint) {
        NamespacedId id = NamespacedId.of("test", type);
        OccupantRef ref = new OccupantRef(snapshot, 0, id, OccupantKind.entity, anchor);
        return new OccupantSnapshot(
            ref,
            new OccupantDescriptor(id, OccupantKind.entity, Map.of(), List.of()),
            footprint,
            List.of()
        );
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
