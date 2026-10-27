package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The package-private nested-provider seam: fake in tests, capabilities in production. */
@NullMarked
interface NestedExtentLookup {

    @Nullable
    NestedExtent block(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);

    @Nullable
    NestedExtent entity(Entity entity);

    static NestedExtentLookup capabilities() {
        return CapabilityLookup.instance;
    }
}
