package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A frozen, namespaced, insertion-ordered family. */
public final class Catalog<T> {

    private final NamespacedId family;
    private final Map<NamespacedId, T> values;
    private final List<NamespacedId> ids;

    private Catalog(NamespacedId family, Map<NamespacedId, T> values) {
        this.family = family;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.ids = List.copyOf(values.keySet());
    }

    public NamespacedId family() {
        return family;
    }

    public @Nullable T get(NamespacedId id) {
        return values.get(id);
    }

    public T require(NamespacedId id) {
        T value = values.get(id);
        if (value == null) {
            throw new IllegalArgumentException("catalog " + family + " has no entry " + id);
        }
        return value;
    }

    public boolean has(NamespacedId id) {
        return values.containsKey(id);
    }

    public List<NamespacedId> ids() {
        return ids;
    }

    public Map<NamespacedId, T> asMap() {
        return values;
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public static <T> Catalog<T> empty(NamespacedId family) {
        return new Catalog<>(Objects.requireNonNull(family, "family"), Map.of());
    }

    public static <T> Builder<T> builder(NamespacedId family) {
        return new Builder<>(Objects.requireNonNull(family, "family"));
    }

    public static final class Builder<T> {

        private final NamespacedId family;
        private final Map<NamespacedId, T> values = new LinkedHashMap<>();

        private Builder(NamespacedId family) {
            this.family = family;
        }

        public Builder<T> put(NamespacedId id, T value) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
            T existing = values.putIfAbsent(id, value);
            if (existing != null && !existing.equals(value)) {
                throw new IllegalArgumentException(
                    "SC-SPI-0003: catalog " + family + " already holds a different value for " + id
                );
            }
            return this;
        }

        public Builder<T> putAll(Catalog<T> catalog) {
            Objects.requireNonNull(catalog, "catalog");
            for (NamespacedId id : catalog.ids()) {
                put(id, catalog.require(id));
            }
            return this;
        }

        public Catalog<T> build() {
            return new Catalog<>(family, values);
        }
    }
}
