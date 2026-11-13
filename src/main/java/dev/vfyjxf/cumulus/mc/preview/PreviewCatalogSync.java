package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.stream.Stream;

/** Pushes the JEI catalog on the datapack-sync hook, which fires on both a join and a reload. */
final class PreviewCatalogSync {

    private PreviewCatalogSync() {}

    static void send(Stream<ServerPlayer> players) {
        StructureKernel kernel = LevelAccess.kernel();
        if (kernel == null) {
            return;
        }
        PreviewCatalogPayload payload = PreviewCatalog.build(kernel, LevelAccess.documentIds(), LevelAccess.setIds());
        players.forEach(player -> sendTo(player, payload));
    }

    private static void sendTo(ServerPlayer player, PreviewCatalogPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException refused) {
            // a listener that never negotiated the channel (a GameTest mock player) rejects the send
            StructureCumulus.logger.debug(
                "preview catalog not sent to {}: {}",
                player.getGameProfile().getName(),
                refused.getMessage()
            );
        }
    }
}
