package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Dev-only right-click inspect: inspect a staged part and cancel the block use, never printing chat. */
final class VisualInspectHandler {

    private static boolean registered;

    private VisualInspectHandler() {}

    static void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS
                .addListener(InputEvent.InteractionKeyMappingTriggered.class, VisualInspectHandler::onInteraction);
    }

    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            LatticePoint cell = LatticePoint
                    .of(block.getBlockPos().getX(), block.getBlockPos().getY(), block.getBlockPos().getZ());
            VisualPart part = VisualPartState.partAt(cell);
            if (part != null) {
                VisualInspectState.inspect(part, cell);
                event.setCanceled(true);
                return;
            }
        }
        VisualInspectState.clear();
    }
}
