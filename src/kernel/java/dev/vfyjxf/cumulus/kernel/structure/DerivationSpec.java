package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;

import java.util.Objects;
/** A reference to one deriver and its output key. */
public record DerivationSpec(NamespacedId deriver, Value arguments, NamespacedId output, int order) {

    public DerivationSpec {
        Objects.requireNonNull(deriver, "deriver");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(output, "output");
        if (order < 0) {
            throw new IllegalArgumentException("order >= 0");
        }
    }
}
