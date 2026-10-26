package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;
/** One property read at the definition origin. */
public record AnchorSpec(NamespacedId role, String property, String value) {

    public AnchorSpec {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");
    }
}
