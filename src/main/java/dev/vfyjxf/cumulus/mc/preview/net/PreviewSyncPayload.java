package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * The server's whole answer for one preview session: the expressible cycle, the current pick, the
 * overlay marks, the expected states to draw and the session's own markers. A capped ghost list
 * carries an overflow marker, so a truncation is never silent.
 */
public record PreviewSyncPayload(
    NamespacedId blueprintId,
    List<SitingCandidate> candidates,
    int current,
    OverlayModel overlay,
    List<GhostCell> ghostCells,
    List<Marker> markers
) implements CustomPacketPayload {

    public static final Type<PreviewSyncPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.modId, "preview_sync")
    );

    public PreviewSyncPayload {
        Objects.requireNonNull(blueprintId, "blueprintId");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("a synced session has at least one candidate");
        }
        if (current < 0 || current >= candidates.size()) {
            throw new IllegalArgumentException("current must index the candidate list: " + current);
        }
        Objects.requireNonNull(overlay, "overlay");
        ghostCells = List.copyOf(ghostCells);
        markers = List.copyOf(markers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
