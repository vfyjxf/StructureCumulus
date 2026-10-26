package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** One definition placed under an alias at an offset. */
public record PlacementSpec(NamespacedId definition, LatticePoint offset, NamespacedId alias,
                            @Nullable SourceSpan source) {

    public PlacementSpec {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(alias, "alias");
    }
}
