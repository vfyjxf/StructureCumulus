package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.Constants;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.jspecify.annotations.Nullable;

/** The one block and one entity capability a nested provider registers against. */
public final class NestedExtents {

    @SuppressWarnings("PMD.ConstantNaming")
    public static final BlockCapability<NestedExtent, @Nullable Void> BLOCK;
    @SuppressWarnings("PMD.ConstantNaming")
    public static final EntityCapability<NestedExtent, @Nullable Void> ENTITY;

    static {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.modId, "nested_extent");
        BLOCK = BlockCapability.createVoid(id, NestedExtent.class);
        ENTITY = EntityCapability.createVoid(id, NestedExtent.class);
    }

    private NestedExtents() {}
}
