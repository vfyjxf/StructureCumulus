package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** The data form of a check. */
public record CheckSpec(NamespacedId id, NamespacedId bay, NamespacedId focusKind, Value focusArgs,
                        NamespacedId expectationKind, Value expectationArgs,
                        @Nullable String description, @Nullable SourceSpan source) {

    public CheckSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(focusKind, "focusKind");
        Objects.requireNonNull(focusArgs, "focusArgs");
        Objects.requireNonNull(expectationKind, "expectationKind");
        Objects.requireNonNull(expectationArgs, "expectationArgs");
    }
}
