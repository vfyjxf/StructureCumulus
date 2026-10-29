package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void bothCapabilitiesCarryTheOneDocumentedIdentityAndTheRealContextType() {
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath("structurecumulus", "nested_extent");
        assertEquals(expected, NestedExtents.BLOCK.name());
        assertEquals(expected, NestedExtents.ENTITY.name());
        assertEquals(NestedExtent.class, NestedExtents.BLOCK.typeClass());
        assertEquals(NestedExtent.class, NestedExtents.ENTITY.typeClass());
        assertEquals(Direction.class, NestedExtents.BLOCK.contextClass());
        assertEquals(Direction.class, NestedExtents.ENTITY.contextClass());
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
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<String, @Nullable Void>() {
                    @Override
                    public NestedExtentHolder<String> holder() {
                        return custom;
                    }

                    @Override
                    public @Nullable NestedExtent get(String actual, @Nullable Void context) {
                        return actual.equals("hit") ? extent : null;
                    }
                }).build();

        assertEquals(extent, lookup.get(custom, "hit", null));
        assertNull(lookup.get(custom, "miss", null));
        assertTrue(lookup.knows(custom));
        assertNull(lookup.get(NestedExtentHolder.of(NamespacedId.of("test", "unknown")), "hit", null));
        assertTrue(NestedExtentLookup.capabilities().knows(NestedExtentHolders.block));
        assertTrue(NestedExtentLookup.capabilities().knows(NestedExtentHolders.entity));
    }

    @Test
    void duplicateHolderRegistrationIsRejected() {
        NestedExtentHolder<String> custom = NestedExtentHolder.of(NamespacedId.of("test", "duplicate"));
        CapabilityNestedExtentProvider<String, @Nullable Void> first = provider(custom);
        CapabilityNestedExtentProvider<String, @Nullable Void> second = provider(custom);
        NestedExtentLookup.Builder builder = NestedExtentLookup.builder().add(first);

        assertThrows(IllegalArgumentException.class, () -> builder.add(second));
    }

    @Test
    void aDirectionSensitiveCapabilitySeesTheContextAndTheBoundCapability() {
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "nested"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            LatticePoint.origin
        );
        NestedExtentHolder<String> custom = NestedExtentHolder.of(NamespacedId.of("test", "capability"));
        BlockCapability<NestedExtent, @Nullable Direction> capability = BlockCapability
                .createSided(id("sensitive"), NestedExtent.class);
        AtomicReference<BaseCapability<NestedExtent, ?>> seenCapability = new AtomicReference<>();
        AtomicReference<@Nullable Direction> seenContext = new AtomicReference<>();
        CapabilityNestedExtentAccess<String, @Nullable Direction> access = (bound, actual, context) -> {
            seenCapability.set(bound);
            seenContext.set(context);
            return actual.equals("hit") ? extent : null;
        };
        CapabilityNestedExtentProvider<String, @Nullable Direction> provider = CapabilityNestedExtentProviders
                .of(custom, capability, access);
        NestedExtentLookup lookup = NestedExtentLookup.builder().add(provider).build();

        assertEquals(extent, lookup.get(custom, "hit", Direction.WEST));
        assertSame(capability, seenCapability.get());
        assertEquals(Direction.WEST, seenContext.get());
        assertNull(lookup.get(custom, "miss", Direction.EAST));
        assertEquals(Direction.EAST, seenContext.get());
        assertEquals(custom, provider.holder());
    }

    @Test
    void aVoidContextCapabilityReceivesNullContextWhenTheCapabilityHasNoContext() {
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "nested"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            LatticePoint.origin
        );
        NestedExtentHolder<String> custom = NestedExtentHolder.of(NamespacedId.of("test", "void_context"));
        EntityCapability<NestedExtent, @Nullable Void> capability = EntityCapability
                .createVoid(id("void_context"), NestedExtent.class);
        AtomicReference<BaseCapability<NestedExtent, ?>> seenCapability = new AtomicReference<>();
        boolean[] seenNull = {false};
        CapabilityNestedExtentAccess<String, @Nullable Void> access = (bound, actual, context) -> {
            seenCapability.set(bound);
            seenNull[0] = context == null;
            return extent;
        };
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(CapabilityNestedExtentProviders.of(custom, capability, access)).build();

        assertEquals(extent, lookup.get(custom, "hit", null));
        assertSame(capability, seenCapability.get());
        assertTrue(seenNull[0], "a Void capability context is null");
    }

    @Test
    void theStandardAccessesAreTheCapabilitySignaturesOverTheStandardCapabilities() {
        assertEquals(Direction.class, NestedExtents.BLOCK.contextClass());
        assertEquals(Direction.class, NestedExtents.ENTITY.contextClass());
        assertNotNull(CapabilityNestedExtentAccesses.block());
        assertNotNull(CapabilityNestedExtentAccesses.entity());
        assertNotNull(CapabilityNestedExtentAccesses.entityVoid());
        assertEquals(NestedExtentHolders.block, NestedExtentProviders.block.holder());
        assertEquals(NestedExtentHolders.entity, NestedExtentProviders.entity.holder());
    }

    private static CapabilityNestedExtentProvider<String, @Nullable Void> provider(NestedExtentHolder<String> holder) {
        return new CapabilityNestedExtentProvider<>() {
            @Override
            public NestedExtentHolder<String> holder() {
                return holder;
            }

            @Override
            public @Nullable NestedExtent get(String actual, @Nullable Void context) {
                return null;
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("structurecumulus_test", path);
    }
}
