package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.Constants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The preview's client wiring. The class is only loaded on the physical client, so no preview
 * renderer, key or HUD class ever reaches a dedicated server.
 */
@EventBusSubscriber(modid = Constants.modId, value = Dist.CLIENT)
public final class PreviewClientEvents {

    private PreviewClientEvents() {}

    @SubscribeEvent
    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        PreviewKeys.register(event);
    }

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        PreviewHud.register(event);
        PlacementHud.register(event);
    }

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        PreviewClientState.tick();
        PlacementAnimator.tick();
    }

    @SubscribeEvent
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            GhostRenderer.render(event);
            PlacementRenderer.render(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            PreviewOverlayRenderer.render(event);
            PreviewClientState.captureMatrices(
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                event.getCamera().getPosition()
            );
        }
    }

    @SubscribeEvent
    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || Minecraft.getInstance().screen != null) {
            return;
        }
        PreviewClientState.cyclePartAtCrosshair();
    }

    @SubscribeEvent
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PreviewClientState.clear();
        PreviewCatalogClient.clear();
        PlacementAnimator.clear();
    }
}
