package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPayloads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The preview's server wiring: payload registration, the session tick and the three lifecycle exits. */
@EventBusSubscriber(modid = Constants.modId)
public final class PreviewEvents {

    private PreviewEvents() {}

    @SubscribeEvent
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PreviewPayloads.register(event);
    }

    @SubscribeEvent
    private static void onServerTick(ServerTickEvent.Post event) {
        PreviewService.tick(event.getServer());
    }

    @SubscribeEvent
    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PreviewService.levelUnloaded(level);
        }
    }

    @SubscribeEvent
    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PreviewService.playerLoggedOut(player);
        }
    }

    @SubscribeEvent
    private static void onServerStopped(ServerStoppedEvent event) {
        PreviewService.closeAll();
    }
}
