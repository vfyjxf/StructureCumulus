package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The JEI catalog: every previewable blueprint of the merged document source and, trailing-optional,
 * one composed entry per structure set, so a client-side category can list them without a document
 * source of its own. Entries beyond a cap never travel — the server leaves them out and names them in
 * the log instead of truncating silently.
 */
public record PreviewCatalogPayload(List<Entry> entries, List<SetEntry> sets) implements CustomPacketPayload {

    public static final Type<PreviewCatalogPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.modId, "preview_catalog")
    );

    public PreviewCatalogPayload {
        entries = List.copyOf(entries);
        sets = List.copyOf(sets);
    }

    /** The blueprint-only payload an older server sends; no set bytes follow it (design 18 §3). */
    public PreviewCatalogPayload(List<Entry> entries) {
        this(entries, List.of());
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

    /**
     * One structure set, composed on the server into pure geometry: every member's cells are already
     * at their composed siting, so no client re-derives an orientation or an offset (design 18 §3).
     */
    public record SetEntry(NamespacedId setId, List<Member> members, List<MaterialLine> materials) {

        public SetEntry {
            Objects.requireNonNull(setId, "setId");
            members = List.copyOf(members);
            materials = List.copyOf(materials);
            if (members.isEmpty()) {
                throw new IllegalArgumentException("a set entry carries at least its root member");
            }
        }
    }

    /** One member: the root or a satellite, its label, its required flag and its composed facts. */
    public record Member(
        NamespacedId blueprintId,
        String label,
        boolean required,
        List<GhostCell> cells,
        List<Cuboid> presence,
        List<Link> links
    ) {

        public Member {
            Objects.requireNonNull(blueprintId, "blueprintId");
            Objects.requireNonNull(label, "label");
            cells = List.copyOf(cells);
            presence = List.copyOf(presence);
            links = List.copyOf(links);
        }
    }

    /** One declared link between a root cell and a satellite cell, in the set's composed frame. */
    public record Link(
        LinkMode mode,
        @Nullable LatticePoint parentCell,
        @Nullable LatticePoint satelliteCell,
        int maxDistance,
        DistanceMetric metric
    ) {

        public Link {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(metric, "metric");
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
