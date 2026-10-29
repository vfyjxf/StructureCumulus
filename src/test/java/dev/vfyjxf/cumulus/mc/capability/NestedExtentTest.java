package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedExtentTest {

    @Test
    void keepsTheProviderDeclarationVerbatim() {
        NamespacedId type = NamespacedId.of("test", "structure");
        Cuboid bounds = Cuboid.of(-5, -70, -5, 11, 90, 11);
        LatticePoint origin = LatticePoint.of(100, 200, -300);
        NestedExtent extent = new NestedExtent(type, bounds, origin);

        assertEquals(type, extent.type());
        assertEquals(bounds, extent.bounds());
        assertEquals(origin, extent.origin());
        assertEquals(
            Cuboid.of(-105, -270, 295, -89, -110, 311),
            bounds.translate(-origin.x(), -origin.y(), -origin.z())
        );
    }

    @Test
    void rejectsAnEmptyDeclaration() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new NestedExtent(
                NamespacedId.of("test", "structure"),
                Cuboid.of(0, 0, 0, 0, 1, 1),
                LatticePoint.origin
            )
        );
    }

    @Test
    void rejectsTheReservedAirType() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new NestedExtent(OccupantSnapshot.air, Cuboid.of(0, 0, 0, 1, 1, 1), LatticePoint.origin)
        );
    }

    @Test
    void rejectsALocalTranslationThatLeavesTheLattice() {
        Cuboid bounds = Cuboid
                .of(LatticePoint.minXz, LatticePoint.minY, 0, LatticePoint.minXz + 1, LatticePoint.minY + 1, 1);
        LatticePoint origin = LatticePoint.of(LatticePoint.maxXz, LatticePoint.maxY, 0);

        assertThrows(
            IllegalArgumentException.class,
            () -> new NestedExtent(NamespacedId.of("test", "x"), bounds, origin)
        );
    }

    @Test
    void bothCapabilitiesCarryTheOneDocumentedIdentity() {
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath("structurecumulus", "nested_extent");

        assertEquals(expected, NestedExtents.BLOCK.name());
        assertEquals(expected, NestedExtents.ENTITY.name());
        assertEquals(NestedExtent.class, NestedExtents.BLOCK.typeClass());
        assertEquals(NestedExtent.class, NestedExtents.ENTITY.typeClass());
        assertEquals(void.class, NestedExtents.BLOCK.contextClass());
        assertEquals(void.class, NestedExtents.ENTITY.contextClass());
        assertNotSame(NestedExtents.BLOCK, NestedExtents.ENTITY);
    }

    @Test
    void holdersAreTypedIdsAndSortByTheirId() {
        assertEquals(NamespacedId.of("structurecumulus", "block"), NestedExtentHolders.block.id());
        assertEquals(NamespacedId.of("structurecumulus", "entity"), NestedExtentHolders.entity.id());
        assertNotEquals(NestedExtentHolders.block, NestedExtentHolders.entity);
        assertTrue(NestedExtentHolders.block.compareTo(NestedExtentHolders.entity) < 0);
    }

    @Test
    void lookupRegistersByHolderAndUnknownHoldersAreNull() {
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "nested"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            LatticePoint.origin
        );
        NestedExtentHolder<String> custom = NestedExtentHolder.of(NamespacedId.of("test", "custom"));
        NestedExtentLookup lookup = NestedExtentLookup.builder().add(new NestedExtentProvider<String>() {
            @Override
            public NestedExtentHolder<String> holder() {
                return custom;
            }

            @Override
            public @Nullable NestedExtent get(String actual) {
                return actual.equals("hit") ? extent : null;
            }
        }).build();

        assertEquals(extent, lookup.get(custom, "hit"));
        assertNull(lookup.get(custom, "miss"));
        assertTrue(lookup.knows(custom));
        assertNull(lookup.get(NestedExtentHolder.of(NamespacedId.of("test", "unknown")), "hit"));
        assertTrue(NestedExtentLookup.capabilities().knows(NestedExtentHolders.block));
        assertTrue(NestedExtentLookup.capabilities().knows(NestedExtentHolders.entity));
    }

    @Test
    void duplicateHolderRegistrationIsRejected() {
        NestedExtentHolder<String> custom = NestedExtentHolder.of(NamespacedId.of("test", "duplicate"));
        NestedExtentProvider<String> first = new NestedExtentProvider<>() {
            @Override
            public NestedExtentHolder<String> holder() {
                return custom;
            }

            @Override
            public @Nullable NestedExtent get(String actual) {
                return null;
            }
        };
        NestedExtentProvider<String> second = new NestedExtentProvider<>() {
            @Override
            public NestedExtentHolder<String> holder() {
                return custom;
            }

            @Override
            public @Nullable NestedExtent get(String actual) {
                return null;
            }
        };
        NestedExtentLookup.Builder builder = NestedExtentLookup.builder().add(first);
        assertThrows(IllegalArgumentException.class, () -> builder.add(second));
    }

    @Test
    void capabilityProviderDelegatesToTheAccessor() {
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "nested"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            LatticePoint.origin
        );
        NestedExtentHolder<String> custom = NestedExtentHolder.of(NamespacedId.of("test", "capability"));
        NestedExtentProvider<String> provider = CapabilityNestedExtentProvider
                .of(custom, NestedExtents.ENTITY, (capability, actual) -> actual.equals("hit") ? extent : null);

        assertEquals(extent, provider.get("hit"));
        assertNull(provider.get("miss"));
        assertEquals(custom, provider.holder());
    }

}
