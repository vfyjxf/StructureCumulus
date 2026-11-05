package dev.vfyjxf.cumulus.mc.mixin;

import dev.vfyjxf.cumulus.mc.CumulusHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds block-entity data changes into the watch; instances are never invalidated from here. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityChangedMixin {

    @Inject(method = "setChanged()V", at = @At("RETURN"))
    private void cumulusChanged(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel serverLevel) {
            CumulusHooks.blockEntityChanged(serverLevel, self.getBlockPos());
        }
    }
}
