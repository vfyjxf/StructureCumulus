package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The "ghost to world" request: the client names a blueprint and, from the screen, the heading its
 * camera looks along; the server takes the origin from the player's crosshair and opens the ordinary
 * preview session. The heading only orders the session's own candidates (design 17 §3.2, §9.4).
 */
public record PreviewOpenPayload(NamespacedId blueprintId, @Nullable Heading facing) implements CustomPacketPayload {

    public static final Type<PreviewOpenPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.modId, "preview_open")
    );

    public PreviewOpenPayload {
        Objects.requireNonNull(blueprintId, "blueprintId");
    }

    /** The JEI button's form: the player's own facing decides the cycle, as it always did. */
    public PreviewOpenPayload(NamespacedId blueprintId) {
        this(blueprintId, null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
