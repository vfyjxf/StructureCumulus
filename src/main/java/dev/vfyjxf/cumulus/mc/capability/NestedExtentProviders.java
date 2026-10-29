package dev.vfyjxf.cumulus.mc.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The block and entity standard nested-extent providers as capability declarations. */
@NullMarked
public final class NestedExtentProviders {

    public static final CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction> block = CapabilityNestedExtentProviders
            .of(NestedExtentHolders.block, NestedExtents.BLOCK, CapabilityNestedExtentAccesses.block());
    public static final CapabilityNestedExtentProvider<Entity, @Nullable Direction> entity = CapabilityNestedExtentProviders
            .of(NestedExtentHolders.entity, NestedExtents.ENTITY, CapabilityNestedExtentAccesses.entity());

    private NestedExtentProviders() {}
}
