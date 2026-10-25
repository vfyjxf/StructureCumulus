package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupantReadModelTest {

    private static final NamespacedId WORLD = NamespacedId.of("test", "world");
    private static final InstanceId INSTANCE = InstanceId.of(WORLD, 1L);
    private static final NamespacedId STONE = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId CHEST = NamespacedId.of("minecraft", "chest");
    private static final NamespacedId HOPPER = NamespacedId.of("minecraft", "hopper");
    private static final NamespacedId INVENTORY = NamespacedId.of("structurecumulus", "item_inventory");
    private static final SectionKey SECTION = new SectionKey(WORLD, 0, 0, 0);

    private static final LatticePoint P0 = LatticePoint.of(0, 0, 0);
    private static final LatticePoint P1 = LatticePoint.of(1, 0, 0);
    private static final LatticePoint P2 = LatticePoint.of(2, 0, 0);
    private static final Cuboid ALL = Cuboid.of(0, 0, 0, 16, 16, 16);

    @Test
    void airIsAReadableOccupant() {
        assertTrue(ReadStatus.READABLE.readable());
        assertTrue(new SectionSlot.Air(4L).readable());

        OccupantSnapshot air = OccupantSnapshot.air(INSTANCE, P0);
        assertTrue(air.isAir());
        assertEquals(OccupantKind.BLOCK, air.kind());
        assertEquals(OccupantSnapshot.AIR, air.descriptor().type());

        SectionSnapshot section = SectionSnapshot.builder(SECTION).put(P0, air).build();
        OccupantSnapshot read = section.occupantAt(P0);
        assertNotNull(read);
        assertTrue(read.isAir());
        assertNull(section.occupantAt(LatticePoint.of(5, 5, 5)));
    }

    @Test
    void aRefusedSlotIsNeverAir() {
        SectionSlot refused = new SectionSlot.Refused(ReadStatus.NOT_CAPTURED,
                NamespacedId.of("structurecumulus", "sc-occ-0102"));
        assertFalse(refused.readable());
        assertFalse(ReadStatus.DENIED.readable());
        assertFalse(ReadStatus.STALE.readable());
        assertThrows(IllegalArgumentException.class,
                () -> new SectionSlot.Refused(ReadStatus.READABLE, NamespacedId.of("structurecumulus", "sc-occ-0102")));

        SectionSnapshot section = SectionSnapshot.builder(SECTION).put(P0, OccupantSnapshot.air(INSTANCE, P0)).build();
        OccupantSnapshot atP0 = section.occupantAt(P0);
        assertNotNull(atP0);
        assertTrue(atP0.isAir());
        assertFalse(refused instanceof SectionSlot.Air);
    }

    @Test
    void boxQueriesFilterAndCoverAMultiCellOccupantOnce() {
        OccupantSnapshot air = OccupantSnapshot.air(INSTANCE, P0);
        OccupantSnapshot stone = block(P1, STONE, Set.of());
        OccupantSnapshot chest = block(P2, CHEST, Set.of(INVENTORY));
        OccupantSnapshot golem = entity(LatticePoint.of(4, 0, 0), HOPPER, 2);

        SectionSnapshot section = SectionSnapshot.builder(SECTION)
                .put(P2, chest)
                .put(P0, air)
                .put(LatticePoint.of(4, 0, 0), golem)
                .put(LatticePoint.of(5, 0, 0), golem)
                .put(P1, stone)
                .build();

        assertEquals(List.of(stone, chest, golem), section.occupants(ALL, OccupantFilter.any()));
        assertEquals(List.of(chest), section.occupants(ALL, OccupantFilter.ofTrait(INVENTORY)));
        assertEquals(List.of(stone), section.occupants(ALL, OccupantFilter.ofType(STONE)));
        assertEquals(List.of(golem), section.occupants(ALL, OccupantFilter.ofKind(OccupantKind.ENTITY)));
        assertEquals(List.of(golem), section.occupants(Cuboid.of(5, 0, 0, 6, 1, 1), OccupantFilter.any()));
        assertTrue(section.occupants(Cuboid.of(8, 0, 0, 9, 1, 1), OccupantFilter.any()).isEmpty());
        assertTrue(section.occupants(ALL, OccupantFilter.ofTrait(NamespacedId.of("test", "absent"))).isEmpty());
    }

    @Test
    void digestIsStableAcrossInsertionOrderAndFollowsContent() {
        Map<LatticePoint, OccupantSnapshot> forward = new HashMap<>();
        forward.put(P0, OccupantSnapshot.air(INSTANCE, P0));
        forward.put(P1, block(P1, STONE, Set.of()));
        forward.put(P2, block(P2, CHEST, Set.of(INVENTORY)));

        Map<LatticePoint, OccupantSnapshot> backward = new HashMap<>();
        backward.put(P2, block(P2, CHEST, Set.of(INVENTORY)));
        backward.put(P1, block(P1, STONE, Set.of()));
        backward.put(P0, OccupantSnapshot.air(INSTANCE, P0));

        long digest = SectionSnapshot.of(SECTION, forward).contentDigest();
        assertEquals(digest, SectionSnapshot.of(SECTION, backward).contentDigest());

        backward.put(P1, block(P1, NamespacedId.of("minecraft", "dirt"), Set.of()));
        assertNotEquals(digest, SectionSnapshot.of(SECTION, backward).contentDigest());
    }

    @Test
    void snapshotsRejectContradictions() {
        OccupantSnapshot chest = block(P2, CHEST, Set.of(INVENTORY));
        assertThrows(IllegalArgumentException.class, () -> new OccupantSnapshot(chest.ref(), chest.kind(),
                OccupantDescriptor.of(STONE), chest.footprint(), Set.of(), null));

        OccupantRef nestedRef = new OccupantRef(INSTANCE, NamespacedId.of("test", "hut"), OccupantKind.NESTED, P0,
                Cuboid.of(P0));
        assertThrows(IllegalArgumentException.class, () -> new OccupantSnapshot(nestedRef, OccupantKind.NESTED,
                OccupantDescriptor.of(nestedRef.type()), nestedRef.footprint(), Set.of(), null));
    }

    @Test
    void changeSetPlusUnionsAndKeepsTheLaterRevision() {
        ChangeSet empty = ChangeSet.empty();
        assertTrue(empty.isEmpty());

        ChangeSet first = new ChangeSet(Set.of(LatticeKey.of(P0)),
                Set.of(), Set.of(), Set.of(), Set.of(INVENTORY), 4L);
        ChangeSet merged = first.plus(new ChangeSet(Set.of(LatticeKey.of(P1)),
                Set.of(SECTION), Set.of(),
                Set.of(new EntityKey(java.util.UUID.randomUUID(), NamespacedId.of("minecraft", "cow"), 3L)),
                Set.of(STONE), 9L));

        assertEquals(2, merged.cells().size());
        assertEquals(Set.of(SECTION), merged.sections());
        assertEquals(1, merged.entities().size());
        assertEquals(Set.of(INVENTORY, STONE), merged.keys());
        assertEquals(9L, merged.revision());
        assertFalse(merged.isEmpty());
    }

    private static OccupantSnapshot block(LatticePoint cell, NamespacedId type, Set<NamespacedId> traits) {
        OccupantRef ref = new OccupantRef(INSTANCE, type, OccupantKind.BLOCK, cell, Cuboid.of(cell));
        return new OccupantSnapshot(ref, OccupantKind.BLOCK, new OccupantDescriptor(type, traits),
                ref.footprint(), traits, null);
    }

    private static OccupantSnapshot entity(LatticePoint min, NamespacedId type, int width) {
        Cuboid body = Cuboid.of(min.x(), min.y(), min.z(), min.x() + width, min.y() + 1, min.z() + 1);
        OccupantRef ref = new OccupantRef(INSTANCE, type, OccupantKind.ENTITY, min, body);
        return new OccupantSnapshot(ref, OccupantKind.ENTITY, OccupantDescriptor.of(type), body, Set.of(), null);
    }
}
