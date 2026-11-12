package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The JEI catalog: every previewable blueprint of the merged document source, so a client-side
 * category can list them without a document source of its own. Entries beyond a cap never travel —
 * the server leaves them out and names them in the log instead of truncating silently.
 */
public record PreviewCatalogPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<PreviewCatalogPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.modId, "preview_catalog")
    );

    public PreviewCatalogPayload {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }

    /** One blueprint, projected to the expected states of its identity orientation. */
    public record Entry(
        NamespacedId blueprintId,
        List<GhostCell> cells,
        List<MaterialLine> materials,
        PreviewPresentation presentation,
        @Nullable VariantSummary variants
    ) {

        public Entry {
            Objects.requireNonNull(blueprintId, "blueprintId");
            cells = List.copyOf(cells);
            materials = List.copyOf(materials);
            Objects.requireNonNull(presentation, "presentation");
        }
    }

    /** One material line: the item a block yields and how many expected cells ask for it. */
    public record MaterialLine(NamespacedId item, int count) {

        public MaterialLine {
            Objects.requireNonNull(item, "item");
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
        }
    }
}
