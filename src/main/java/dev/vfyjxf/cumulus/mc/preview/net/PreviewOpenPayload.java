package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * The JEI "ghost to world" request: the client names a blueprint, the server takes the origin from
 * the player's crosshair and opens the ordinary preview session.
 */
public record PreviewOpenPayload(NamespacedId blueprintId) implements CustomPacketPayload {

    public static final Type<PreviewOpenPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.modId, "preview_open")
    );

    public PreviewOpenPayload {
        Objects.requireNonNull(blueprintId, "blueprintId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
