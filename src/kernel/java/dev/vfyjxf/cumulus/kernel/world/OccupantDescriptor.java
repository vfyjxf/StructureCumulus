package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;

/** A version-independent identity: type and kind. */
public record OccupantDescriptor(NamespacedId type, OccupantKind kind) {

    public OccupantDescriptor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
    }
}
