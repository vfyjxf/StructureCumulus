package dev.vfyjxf.cumulus.mc.capability;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/** The block and entity standard nested-extent providers as capability declarations. */
@NullMarked
public final class NestedExtentProviders {

    public static final NestedExtentProvider<BlockNestedExtentContext> block = CapabilityNestedExtentProvider
            .of(NestedExtentHolders.block, NestedExtents.BLOCK, NestedExtentAccesses.block());

    public static final NestedExtentProvider<Entity> entity = CapabilityNestedExtentProvider
            .of(NestedExtentHolders.entity, NestedExtents.ENTITY, NestedExtentAccesses.entity());

    private NestedExtentProviders() {}
}
