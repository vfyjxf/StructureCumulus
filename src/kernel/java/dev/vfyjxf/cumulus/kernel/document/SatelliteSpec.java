package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** The document form of one satellite. */
public record SatelliteSpec(
    NamespacedId id,
    NamespacedId blueprint,
    LatticePoint at,
    Orientation orientation,
    boolean required,
    Value arguments,
    @Nullable PresenceSpec presence,
    List<LinkSpec> links,
    @Nullable SourceSpan source
) {

    public SatelliteSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(links, "links");
        if (!arguments.isObject()) {
            throw new IllegalArgumentException("satellite arguments must be an object");
        }
        links = List.copyOf(links);
    }

    public SatelliteSpec withoutSpans() {
        List<LinkSpec> stripped = links.stream().map(LinkSpec::withoutSpans).toList();
        PresenceSpec strippedPresence = presence == null ? null : presence.withoutSpans();
        boolean unchanged = source == null && strippedPresence == presence;
        if (unchanged && stripped.equals(links)) {
            return this;
        }
        return new SatelliteSpec(id, blueprint, at, orientation, required, arguments, strippedPresence, stripped, null);
    }
}
