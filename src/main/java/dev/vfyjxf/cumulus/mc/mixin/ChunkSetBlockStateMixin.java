package dev.vfyjxf.cumulus.mc.mixin;

import dev.vfyjxf.cumulus.mc.CumulusHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The observation funnel's write hook: a null return means the write changed nothing. */
@Mixin(LevelChunk.class)
public abstract class ChunkSetBlockStateMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void cumulusObserve(
        BlockPos pos,
        BlockState state,
        boolean isMoving,
        CallbackInfoReturnable<BlockState> cir
    ) {
        BlockState previous = cir.getReturnValue();
        if (previous == null) {
            return;
        }
        Level level = ((LevelChunk) (Object) this).getLevel();
        if (level instanceof ServerLevel serverLevel) {
            CumulusHooks.blockChanged(serverLevel, pos, previous, state, isMoving);
        }
    }
}
