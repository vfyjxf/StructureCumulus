package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;
/** Typed identity of one queryable trait. */
public record TraitKey<T>(NamespacedId id, Class<T> type) {

    public TraitKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
