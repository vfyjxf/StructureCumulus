package dev.vfyjxf.cumulus.kernel.world;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A present trait; its value may be null as a definite fact. */
public record Trait<T>(TraitKey<T> key, @Nullable T value) {

    public Trait {
        Objects.requireNonNull(key, "key");
    }

    public static <T> Trait<T> of(TraitKey<T> key, T value) {
        return new Trait<>(key, value);
    }

    public static <T> Trait<T> absent(TraitKey<T> key) {
        return new Trait<>(key, null);
    }
}
