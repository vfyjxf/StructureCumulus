package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The data form of a context key. */
public record KeySpec(
    NamespacedId id,
    String type,
    Retention retention,
    @Nullable NamespacedId merge,
    @Nullable SourceSpan source
) {

    public KeySpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(retention, "retention");
    }
}
