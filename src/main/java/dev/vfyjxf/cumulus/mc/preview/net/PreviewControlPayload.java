package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One control input of a preview session. A control never carries a siting: the server recomputes
 * and re-syncs. {@code target} names the clicked ghost cell, and only {@link Control#cyclePart} uses it.
 */
public record PreviewControlPayload(Control control, @Nullable LatticePoint target) implements CustomPacketPayload {

    public static final Type<PreviewControlPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.modId, "preview_control")
    );

    public enum Control {
        turnCw, turnCcw, flip, nextOrigin, prevOrigin, reset, resetIdentity, cyclePart, recall, confirm, cancel, nextVariant, savePreset
    }

    public PreviewControlPayload {
        Objects.requireNonNull(control, "control");
    }

    public static PreviewControlPayload of(Control control) {
        return new PreviewControlPayload(control, null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
