package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** An immutable typed host context handed to trait providers. */
@NullMarked
public final class TraitContext {

    private static final TraitContext empty = new TraitContext(Map.of());

    private final Map<NamespacedId, Entry<?>> entries;

    private TraitContext(Map<NamespacedId, Entry<?>> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static TraitContext empty() {
        return empty;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> @Nullable T get(TraitContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        Entry<?> entry = entries.get(key.id());
        if (entry == null) {
            return null;
        }
        if (!entry.key().type().equals(key.type())) {
            throw new IllegalArgumentException(
                "context key " + key.id() + " holds " + entry.key().type().getName() + ", not " + key.type().getName()
            );
        }
        return key.type().cast(entry.value());
    }

    public boolean has(TraitContextKey<?> key) {
        Objects.requireNonNull(key, "key");
        return entries.containsKey(key.id());
    }

    public Set<TraitContextKey<?>> keys() {
        return entries.values().stream().map(Entry::key).collect(Collectors.toUnmodifiableSet());
    }

    private record Entry<T>(TraitContextKey<T> key, T value) {}

    public static final class Builder {

        private final Map<NamespacedId, Entry<?>> entries = new LinkedHashMap<>();

        private Builder() {}

        public <T> Builder put(TraitContextKey<T> key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (entries.putIfAbsent(key.id(), new Entry<>(key, value)) != null) {
                throw new IllegalArgumentException("duplicate context key id: " + key.id());
            }
            return this;
        }

        public TraitContext build() {
            return new TraitContext(entries);
        }
    }
}
