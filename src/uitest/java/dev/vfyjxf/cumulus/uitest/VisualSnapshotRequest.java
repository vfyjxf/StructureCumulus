package dev.vfyjxf.cumulus.uitest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The client's poll for a fresh read-only visual snapshot from the integrated server. */
public record VisualSnapshotRequest() implements CustomPacketPayload {

    public static final Type<VisualSnapshotRequest> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UiTestMod.modId, "visual_snapshot_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VisualSnapshotRequest> streamCodec = StreamCodec
            .of((buffer, value) -> {}, buffer -> new VisualSnapshotRequest());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
