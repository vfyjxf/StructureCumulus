package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.mc.preview.PreviewService;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewClientBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the preview payloads once per side. Both handlers hop to their own thread first: the
 * network thread never touches a session or the client render state.
 */
public final class PreviewPayloads {

    private static final String protocolVersion = "1";

    private static boolean registered;

    private PreviewPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        if (registered) {
            return;
        }
        registered = true;
        PayloadRegistrar registrar = event.registrar(protocolVersion);
        registrar.playToClient(PreviewSyncPayload.type, PreviewCodecs.sync, PreviewPayloads::acceptSync);
        registrar.playToClient(PreviewCatalogPayload.type, PreviewCodecs.catalog, PreviewPayloads::acceptCatalog);
        registrar.playToServer(PreviewControlPayload.type, PreviewCodecs.control, PreviewPayloads::acceptControl);
        registrar.playToServer(PreviewOpenPayload.type, PreviewCodecs.open, PreviewPayloads::acceptOpen);
    }

    private static void acceptSync(PreviewSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PreviewClientBridge.accept(payload));
    }

    private static void acceptCatalog(PreviewCatalogPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PreviewClientBridge.acceptCatalog(payload));
    }

    private static void acceptControl(PreviewControlPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> PreviewService.control(player, payload.control(), payload.target()));
    }

    private static void acceptOpen(PreviewOpenPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> PreviewService.open(player, payload.blueprintId()));
    }
}
