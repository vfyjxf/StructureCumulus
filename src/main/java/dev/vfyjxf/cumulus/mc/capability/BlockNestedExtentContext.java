package dev.vfyjxf.cumulus.mc.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The immutable input of one block nested-extent read. */
@NullMarked
public record BlockNestedExtentContext(
    ServerLevel level,
    BlockPos pos,
    BlockState state,
    @Nullable BlockEntity blockEntity
) {

    public BlockNestedExtentContext {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
    }
}
