package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.Objects;

/** One property read at the definition origin; a spec never names a world cell. */
public record AnchorSpec(NamespacedId role, NamespacedId property, String value) implements Comparable<AnchorSpec> {

    public AnchorSpec {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("B-4: anchor " + role + " declares a blank value");
        }
    }

    @Override
    public int compareTo(AnchorSpec other) {
        int byRole = role.compareTo(other.role);
        if (byRole != 0) {
            return byRole;
        }
        int byProperty = property.compareTo(other.property);
        return byProperty != 0 ? byProperty : value.compareTo(other.value);
    }
}
