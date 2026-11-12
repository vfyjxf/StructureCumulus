package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.Constants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

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
    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(PreviewClientCommand.root());
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
        if (CinematicController.active() || !event.isUseItem() || Minecraft.getInstance().screen != null) {
            return;
        }
        PreviewClientState.cyclePartAtCrosshair();
    }

    @SubscribeEvent
    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        CinematicController.onCameraAngles(event);
    }

    @SubscribeEvent
    private static void onMovementInput(MovementInputUpdateEvent event) {
        CinematicController.onMovementInput(event);
    }

    @SubscribeEvent
    private static void onInteractionTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        CinematicController.onInteraction(event);
    }

    @SubscribeEvent
    private static void onMouseButton(InputEvent.MouseButton.Pre event) {
        CinematicController.onMouseButton(event);
    }

    @SubscribeEvent
    private static void onKey(InputEvent.Key event) {
        CinematicController.onKey(event);
    }

    @SubscribeEvent
    private static void onScreenOpening(ScreenEvent.Opening event) {
        CinematicController.onScreenOpening(event);
    }

    @SubscribeEvent
    private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        CinematicController.onRenderPlayer(event);
    }

    @SubscribeEvent
    private static void onRenderHand(RenderHandEvent event) {
        if (CinematicController.active()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PreviewClientState.clear();
        PreviewCatalogClient.clear();
        PlacementAnimator.clear();
    }
}
