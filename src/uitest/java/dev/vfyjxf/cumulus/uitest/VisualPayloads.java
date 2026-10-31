package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.mc.ServerState;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers and serves the dev-only visual snapshot request/response pair. */
public final class VisualPayloads {

    private static final String protocolVersion = "1";
    private static boolean registered;

    private VisualPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        if (registered) {
            return;
        }
        registered = true;
        PayloadRegistrar registrar = event.registrar(protocolVersion);
        registrar.playToServer(
            VisualSnapshotRequest.type,
            VisualSnapshotRequest.streamCodec,
            VisualPayloads::serveSnapshot
        );
        registrar.playToClient(
            VisualSnapshotPayload.type,
            VisualSnapshotPayload.streamCodec,
            VisualPayloads::acceptSnapshot
        );
    }

    private static void serveSnapshot(VisualSnapshotRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerState state = ServerState.current();
        if (state == null) {
            return;
        }
        VisualSnapshot snapshot = state.visualSnapshot(player.serverLevel());
        context.reply(new VisualSnapshotPayload(snapshot));
    }

    private static void acceptSnapshot(VisualSnapshotPayload payload, IPayloadContext context) {
        VisualClientState.accept(payload.snapshot());
    }
}
