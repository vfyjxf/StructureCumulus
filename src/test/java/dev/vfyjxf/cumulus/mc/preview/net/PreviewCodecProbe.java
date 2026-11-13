package dev.vfyjxf.cumulus.mc.preview.net;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Test seam: the catalogue codec's bytes, so a determinism test compares bytes, not only records. */
public final class PreviewCodecProbe {

    private PreviewCodecProbe() {}

    public static byte[] encode(PreviewCatalogPayload payload) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            RegistryAccess.EMPTY,
            ConnectionType.OTHER
        );
        PreviewCodecs.catalog.encode(buffer, payload);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
