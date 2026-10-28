package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** The document form of one structure set: an id, a root blueprint and its satellites. */
public record StructureSetSpec(
    NamespacedId id,
    NamespacedId root,
    List<SatelliteSpec> satellites,
    @Nullable SourceSpan source
) {

    public StructureSetSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(satellites, "satellites");
        satellites = List.copyOf(satellites);
    }

    public StructureSetSpec withoutSpans() {
        List<SatelliteSpec> stripped = satellites.stream().map(SatelliteSpec::withoutSpans).toList();
        if (source == null && stripped.equals(satellites)) {
            return this;
        }
        return new StructureSetSpec(id, root, stripped, null);
    }
}
