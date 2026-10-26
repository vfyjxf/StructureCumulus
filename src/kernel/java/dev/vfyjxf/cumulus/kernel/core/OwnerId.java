package dev.vfyjxf.cumulus.kernel.core;

import java.util.Objects;

/** Who holds a claim: a player or a team. */
public record OwnerId(NamespacedId id) {

    public OwnerId {
        Objects.requireNonNull(id, "id");
    }

    public static OwnerId of(NamespacedId id) {
        return new OwnerId(id);
    }
}
