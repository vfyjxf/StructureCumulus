package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.capability.NestedExtents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The production lookup: the four-argument block query and the context-null entity query. */
@NullMarked
enum CapabilityLookup implements NestedExtentLookup {
    instance;

    @Override
    public @Nullable NestedExtent block(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        return level.getCapability(NestedExtents.BLOCK, pos, state, blockEntity);
    }

    @Override
    public @Nullable NestedExtent entity(Entity entity) {
        return entity.getCapability(NestedExtents.ENTITY, null);
    }
}
