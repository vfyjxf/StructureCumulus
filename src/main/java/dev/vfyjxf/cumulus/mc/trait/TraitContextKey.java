package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** Typed identity of one host context value. */
@NullMarked
public record TraitContextKey<T>(NamespacedId id, Class<T> type) {

    public TraitContextKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    public static <T> TraitContextKey<T> of(NamespacedId id, Class<T> type) {
        return new TraitContextKey<>(id, type);
    }
}
