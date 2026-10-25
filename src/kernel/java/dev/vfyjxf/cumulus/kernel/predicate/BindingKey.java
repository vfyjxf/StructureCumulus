package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.Objects;

/** Identity is {@code (id, type)}: one id names one value inside one check. */
public record BindingKey<T>(NamespacedId id, Class<T> type) {

    public BindingKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    public static <T> BindingKey<T> of(NamespacedId id, Class<T> type) {
        return new BindingKey<>(id, type);
    }

    public static <T> BindingKey<T> of(String id, Class<T> type) {
        NamespacedId parsed = NamespacedId.parse(Objects.requireNonNull(id, "id"));
        if (parsed == null) {
            throw new IllegalArgumentException("not a namespaced id: " + id);
        }
        return new BindingKey<>(parsed, type);
    }

    @Override
    public String toString() {
        return id + "#" + type.getSimpleName();
    }
}
