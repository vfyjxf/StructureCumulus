package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            new OccupantDescriptor(NamespacedId.of("m"), OccupantKind.block),
            Cuboid.of(LatticePoint.origin),
            List.of(new TraitSlot.Present(Trait.of(energy, 7L)))
        );
        assertEquals(7L, occupant.trait(energy));
        assertTrue(occupant.has(energy));
        assertEquals(new OccupantDescriptor(NamespacedId.of("m"), OccupantKind.block), occupant.descriptor());
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
    void sectionSnapshotSortsBoxQueriesByRefAndCarriesItsDigest() {
        SectionKey key = SectionKey.of(World.of(NamespacedId.of("w")), 0, LatticePoint.origin, 16);
        OccupantSnapshot atFirstCell = TestBlocks.block(snapshot, NamespacedId.of("z"), LatticePoint.of(1, 0, 0));
        OccupantSnapshot atSecondCell = TestBlocks.block(snapshot, NamespacedId.of("a"), LatticePoint.of(2, 0, 0));
        SectionSnapshot section = SectionSnapshot.of(
            key,
            Map.of(atFirstCell.ref().anchor(), atFirstCell, atSecondCell.ref().anchor(), atSecondCell),
            7L
        );
        assertEquals(7L, section.contentDigest());
        assertEquals(
            List.of(atFirstCell, atSecondCell),
            List.copyOf(section.occupants().values()),
            "the member map iterates by cell"
        );
        assertEquals(
            List.of(atSecondCell, atFirstCell),
            section.occupants(Cuboid.of(0, 0, 0, 3, 1, 1), OccupantFilter.any()),
            "a box query answers in ref order"
        );
        assertEquals(
            section,
            SectionSnapshot.of(
                key,
                new LinkedHashMap<>(
                    Map.of(atSecondCell.ref().anchor(), atSecondCell, atFirstCell.ref().anchor(), atFirstCell)
                ),
                7L
            ),
            "the member map is canonical"
        );
        assertNotEquals(
            section,
            SectionSnapshot.of(
                key,
                Map.of(atFirstCell.ref().anchor(), atFirstCell, atSecondCell.ref().anchor(), atSecondCell),
                8L
            )
        );
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
        EntityWindow ceiling = EntityWindow.of(
            Cuboid.of(
                LatticePoint.maxXz - 1,
                LatticePoint.maxY - 1,
                LatticePoint.maxXz - 1,
                LatticePoint.maxXz,
                LatticePoint.maxY,
                LatticePoint.maxXz
            ),
            NamespacedId.of("mob")
        );
        assertEquals(LatticePoint.maxXz, Objects.requireNonNull(ceiling, "ceiling").cells().max().x());
        assertEquals(LatticePoint.maxY, ceiling.cells().max().y());
        assertNull(EntityWindow.of(Cuboid.of(0, 0, 0, 0, 1, 1), NamespacedId.of("mob")));
    }

    @Test
    void statusOfAndRefusal() {
        SectionSlot refused = new SectionSlot.Refused(ReadStatus.denied, FindingCode.of(FindingCode.Family.occ, 1));
        assertFalse(refused.readable());
        assertEquals(ReadStatus.denied, refused.status());
        assertTrue(new SectionSlot.Air(0).readable());
        assertTrue(
            new SectionSlot.Partial(
                SectionSnapshot.of(sectionKey(), Map.of(), 0),
                Map.of(
                    LatticePoint.origin,
                    new ReadRefusal(ReadStatus.notCaptured, FindingCode.of(FindingCode.Family.occ, 4))
                )
            ).readable()
        );
    }

    @Test
    void partialSectionRejectsEmptyAndOccupiedRefusals() {
        SectionKey key = sectionKey();
        OccupantSnapshot block = occupantWith(List.of());
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(LatticePoint.origin, block), 0);
        ReadRefusal refusal = new ReadRefusal(ReadStatus.denied, FindingCode.of(FindingCode.Family.occ, 4));
        assertThrows(IllegalArgumentException.class, () -> new SectionSlot.Partial(section, Map.of()));
        assertThrows(
            IllegalArgumentException.class,
            () -> new SectionSlot.Partial(section, Map.of(LatticePoint.origin, refusal))
        );
        SectionSlot.Partial valid = new SectionSlot.Partial(section, Map.of(LatticePoint.of(1, 0, 0), refusal));
        assertEquals(Set.of(LatticePoint.of(1, 0, 0)), valid.unreadable().keySet());
    }

    @Test
    void bodyIsPartOfIdentityAndNullOrdersFirst() {
        NamespacedId type = NamespacedId.of("m", "cow");
        OccupantRef plain = new OccupantRef(snapshot, 0, type, OccupantKind.entity, LatticePoint.origin);
        OccupantRef explicit = new OccupantRef(snapshot, 0, type, OccupantKind.entity, LatticePoint.origin, null);
        OccupantRef first = new OccupantRef(
            snapshot,
            0,
            type,
            OccupantKind.entity,
            LatticePoint.origin,
            NamespacedId.of("u", "1")
        );
        OccupantRef second = new OccupantRef(
            snapshot,
            0,
            type,
            OccupantKind.entity,
            LatticePoint.origin,
            NamespacedId.of("u", "2")
        );
        assertEquals(plain, explicit);
        assertEquals(plain.hashCode(), explicit.hashCode());
        assertNotEquals(first, second);
        assertEquals(2, Set.of(first, second).size());
        assertTrue(plain.compareTo(first) < 0);
        assertTrue(first.compareTo(second) < 0);
        assertTrue(plain.from(snapshot));
        assertFalse(plain.from(new SnapshotId(World.of(NamespacedId.of("w")), 2)));
        assertEquals(NamespacedId.of("u", "2"), second.body());
        assertNull(plain.body());
    }

    @Test
    void traitSlotsHaveFourStates() {
        NamespacedId id = NamespacedId.of("m", "energy");
        TraitKey<Long> typed = new TraitKey<>(id, Long.class);
        TraitKey<Object> json = new TraitKey<>(id, Object.class);

        OccupantSnapshot present = occupantWith(List.of(new TraitSlot.Present(Trait.of(typed, 7L))));
        assertInstanceOf(TraitSlot.Present.class, present.slot(id));
        assertEquals(7L, present.trait(typed));
        assertTrue(present.has(typed));
        Trait<Object> asObject = present.find(json);
        assertNotNull(asObject);
        assertEquals(7L, asObject.value());

        OccupantSnapshot presentNull = occupantWith(List.of(new TraitSlot.Present(Trait.absent(typed))));
        assertInstanceOf(TraitSlot.Present.class, presentNull.slot(id));
        assertNull(presentNull.trait(typed));
        assertTrue(presentNull.has(typed));
        Trait<Long> nullTrait = presentNull.find(typed);
        assertNotNull(nullTrait);
        assertNull(nullTrait.value());
        assertNotNull(presentNull.find(json));

        OccupantSnapshot absent = occupantWith(List.of(new TraitSlot.Absent(id)));
        assertInstanceOf(TraitSlot.Absent.class, absent.slot(id));
        assertNull(absent.find(typed));
        assertNull(absent.trait(typed));
        assertFalse(absent.has(typed));

        OccupantSnapshot refused = occupantWith(
            List.of(
                new TraitSlot.Refused(id, new ReadRefusal(ReadStatus.failed, FindingCode.of(FindingCode.Family.occ, 4)))
            )
        );
        assertInstanceOf(TraitSlot.Refused.class, refused.slot(id));
        assertNull(refused.find(typed));
        assertFalse(refused.has(typed));

        OccupantSnapshot unasked = occupantWith(List.of());
        assertNull(unasked.slot(id));
        assertNull(unasked.find(typed));
        assertFalse(unasked.has(typed));

        OccupantSnapshot wrongType = occupantWith(
            List.of(new TraitSlot.Present(Trait.of(new TraitKey<>(id, String.class), "x")))
        );
        assertNotNull(wrongType.slot(id));
        assertNull(wrongType.find(typed));

        assertTrue(OccupantFilter.ofTrait(id).matches(present));
        assertTrue(OccupantFilter.ofTrait(id).matches(presentNull));
        assertFalse(OccupantFilter.ofTrait(id).matches(absent));
        assertFalse(OccupantFilter.ofTrait(id).matches(refused));
        assertFalse(OccupantFilter.ofTrait(id).matches(unasked));

        assertThrows(
            IllegalArgumentException.class,
            () -> occupantWith(
                List.of(new TraitSlot.Present(Trait.of(typed, 1L)), new TraitSlot.Present(Trait.of(json, 2L)))
            )
        );
    }

    @Test
    void snapshotPolicyCopiesTraitIds() {
        NamespacedId energy = NamespacedId.of("m", "energy");
        NamespacedId lit = NamespacedId.of("m", "lit");
        LinkedHashSet<NamespacedId> traits = new LinkedHashSet<>(List.of(energy, lit, energy));
        SnapshotPolicy policy = new SnapshotPolicy(10, 2, Set.of(OccupantKind.entity), traits, List.of());
        assertEquals(Set.of(energy, lit), policy.traits());
        assertEquals(2, policy.traits().size());
        assertEquals(2, policy.maxDepth());
        assertTrue(SnapshotPolicy.minimal().traits().isEmpty());
        assertEquals(SnapshotPolicy.defaultMaxDepth, SnapshotPolicy.minimal().maxDepth());
    }

    @Test
    void readableIsTheOnlyNonRefusalStatus() {
        assertFalse(ReadStatus.failed.readable());
        assertFalse(ReadStatus.denied.readable());
        assertFalse(ReadStatus.notCaptured.readable());
        assertFalse(ReadStatus.outOfBounds.readable());
        assertTrue(ReadStatus.readable.readable());
        assertThrows(
            IllegalArgumentException.class,
            () -> new ReadRefusal(ReadStatus.readable, FindingCode.of(FindingCode.Family.occ, 4))
        );
        ReadRefusal refusal = new ReadRefusal(ReadStatus.failed, FindingCode.of(FindingCode.Family.occ, 4));
        assertEquals(ReadStatus.failed, refusal.status());
    }

    private static SectionKey sectionKey() {
        return SectionKey.of(World.of(NamespacedId.of("w")), 0, LatticePoint.origin, 16);
    }

    private static OccupantSnapshot occupantWith(List<TraitSlot> traits) {
        OccupantRef ref = new OccupantRef(snapshot, 0, NamespacedId.of("m"), OccupantKind.block, LatticePoint.origin);
        return new OccupantSnapshot(
            ref,
            new OccupantDescriptor(NamespacedId.of("m"), OccupantKind.block),
            Cuboid.of(LatticePoint.origin),
            traits
        );
    }

    private static final class TestBlocks {
        private static OccupantSnapshot block(SnapshotId snapshot, NamespacedId type, LatticePoint cell) {
            OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.block, cell);
            return new OccupantSnapshot(
                ref,
                new OccupantDescriptor(type, OccupantKind.block),
                Cuboid.of(cell),
                List.of()
            );
        }
    }
}
