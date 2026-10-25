package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One frozen, namespaced, insertion-ordered family; a built catalog has no mutator. */
public final class Catalog<T> {

    private final NamespacedId family;
    private final Map<NamespacedId, T> entries;
    private final List<NamespacedId> ids;

    private Catalog(NamespacedId family, Map<NamespacedId, T> entries) {
        this.family = family;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.ids = List.copyOf(entries.keySet());
    }

    public static <T> Catalog<T> empty(NamespacedId family) {
        return new Catalog<>(Objects.requireNonNull(family, "family"), Map.of());
    }

    public static <T> Builder<T> builder(NamespacedId family) {
        return new Builder<>(family);
    }

    public NamespacedId family() {
        return family;
    }

    public @Nullable T get(NamespacedId id) {
        return entries.get(Objects.requireNonNull(id, "id"));
    }

    public T require(NamespacedId id) {
        T value = get(id);
        if (value == null) {
            throw new IllegalArgumentException("SC-SPI-0002: " + family + " holds no entry " + id);
        }
        return value;
    }

    public boolean has(NamespacedId id) {
        return entries.containsKey(Objects.requireNonNull(id, "id"));
    }

    public List<NamespacedId> ids() {
        return ids;
    }

    public Map<NamespacedId, T> asMap() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public String toString() {
        return "Catalog[" + family + ", " + ids + "]";
    }

    public static final class Builder<T> {

        private final NamespacedId family;
        private final Map<NamespacedId, T> entries = new LinkedHashMap<>();

        private Builder(NamespacedId family) {
            this.family = Objects.requireNonNull(family, "family");
        }

        public Builder<T> put(NamespacedId id, T value) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
            T previous = entries.putIfAbsent(id, value);
            if (previous != null && previous != value) {
                throw new IllegalArgumentException("SC-SPI-0001: " + family + " already holds " + id);
            }
            return this;
        }

        public Builder<T> putAll(Catalog<T> catalog) {
            Objects.requireNonNull(catalog, "catalog");
            for (Map.Entry<NamespacedId, T> entry : catalog.asMap().entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder<T> fill(Map<NamespacedId, T> values) {
            Objects.requireNonNull(values, "values");
            for (Map.Entry<NamespacedId, T> entry : values.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Catalog<T> build() {
            return new Catalog<>(family, new LinkedHashMap<>(entries));
        }
    }
}
