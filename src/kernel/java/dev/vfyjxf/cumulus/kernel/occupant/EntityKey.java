package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity of one entity in a window diff: an unchanged three-tuple means an unchanged entity.
 */
public record EntityKey(UUID id, NamespacedId type, long dataDigest) {

    public EntityKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
