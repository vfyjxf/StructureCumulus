package dev.vfyjxf.cumulus.mc.trait;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The immutable input of one block trait read. */
@NullMarked
public record BlockTraitContext(
    ServerLevel level,
    BlockPos pos,
    BlockState state,
    @Nullable BlockEntity blockEntity,
    TraitCaptureContext capture
) {

    public BlockTraitContext {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(capture, "capture");
    }
}
