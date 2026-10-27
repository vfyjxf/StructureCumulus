package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
