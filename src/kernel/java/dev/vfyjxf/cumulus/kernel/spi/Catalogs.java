package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.context.CatalogsDraft;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.Focus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The five frozen extension families one kernel resolves against; entries are values, never classes,
 * so no lookup reflects.
 */
public final class Catalogs {

    public static final NamespacedId EXPECTATIONS = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "expectations");
    public static final NamespacedId FOCUSES = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "focuses");
    public static final NamespacedId COMPUTATIONS = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "computations");
    public static final NamespacedId CONTEXT_KEYS = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "context_keys");
    public static final NamespacedId DERIVERS = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "derivers");

    private final Catalog<Expectation<?>> expectations;
    private final Catalog<Focus<?>> focuses;
    private final Catalog<Computation<?>> computations;
    private final Catalog<ContextKey<?>> contextKeys;
    private final Catalog<Deriver<?>> derivers;

    private Catalogs(Catalog<Expectation<?>> expectations, Catalog<Focus<?>> focuses,
                     Catalog<Computation<?>> computations, Catalog<ContextKey<?>> contextKeys,
                     Catalog<Deriver<?>> derivers) {
        this.expectations = expectations;
        this.focuses = focuses;
        this.computations = computations;
        this.contextKeys = contextKeys;
        this.derivers = derivers;
    }

    public static Catalogs empty() {
        return builder().freeze();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Catalog<Expectation<?>> expectations() {
        return expectations;
    }

    public Catalog<Focus<?>> focuses() {
        return focuses;
    }

    public Catalog<Computation<?>> computations() {
        return computations;
    }

    public Catalog<ContextKey<?>> contextKeys() {
        return contextKeys;
    }

    public Catalog<Deriver<?>> derivers() {
        return derivers;
    }

    public static final class Builder implements CatalogsDraft {

        private final Map<NamespacedId, Expectation<?>> expectations = new LinkedHashMap<>();
        private final Map<NamespacedId, Focus<?>> focuses = new LinkedHashMap<>();
        private final Map<NamespacedId, Computation<?>> computations = new LinkedHashMap<>();
        private final Map<NamespacedId, ContextKey<?>> contextKeys = new LinkedHashMap<>();
        private final Map<NamespacedId, Deriver<?>> derivers = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder expectation(NamespacedId id, Expectation<?> value) {
            put(expectations, id, value);
            return this;
        }

        public Builder focus(NamespacedId id, Focus<?> value) {
            put(focuses, id, value);
            return this;
        }

        public Builder computation(NamespacedId id, Computation<?> value) {
            put(computations, id, value);
            return this;
        }

        public Builder deriver(NamespacedId id, Deriver<?> value) {
            put(derivers, id, value);
            return this;
        }

        public Builder contextKey(ContextKey<?> key) {
            Objects.requireNonNull(key, "key");
            ContextKey<?> previous = contextKeys.putIfAbsent(key.id(), key);
            if (previous != null) {
                throw new IllegalStateException("SC-CTX-0102: context key " + key.id() + " is already declared");
            }
            return this;
        }

        public Builder expectations(Catalog<Expectation<?>> family) {
            merge(expectations, family);
            return this;
        }

        public Builder focuses(Catalog<Focus<?>> family) {
            merge(focuses, family);
            return this;
        }

        public Builder computations(Catalog<Computation<?>> family) {
            merge(computations, family);
            return this;
        }

        public Builder contextKeys(Catalog<ContextKey<?>> family) {
            merge(contextKeys, family);
            return this;
        }

        public Builder derivers(Catalog<Deriver<?>> family) {
            merge(derivers, family);
            return this;
        }

        @Override
        public <T> ContextKey<T> registerKey(ContextKey<T> key) {
            contextKey(key);
            return key;
        }

        @Override
        public <T> ContextKey<T> putKey(ContextKey<T> key) {
            contextKey(key);
            return key;
        }

        public Catalogs freeze() {
            return new Catalogs(
                    Catalog.<Expectation<?>>builder(EXPECTATIONS).fill(expectations).build(),
                    Catalog.<Focus<?>>builder(FOCUSES).fill(focuses).build(),
                    Catalog.<Computation<?>>builder(COMPUTATIONS).fill(computations).build(),
                    Catalog.<ContextKey<?>>builder(CONTEXT_KEYS).fill(contextKeys).build(),
                    Catalog.<Deriver<?>>builder(DERIVERS).fill(derivers).build());
        }

        private static void merge(Map<NamespacedId, ?> into, Catalog<?> family) {
            for (Map.Entry<?, ?> entry : family.asMap().entrySet()) {
                putRaw(into, (NamespacedId) entry.getKey(), entry.getValue());
            }
        }

        private static void put(Map<NamespacedId, ?> into, NamespacedId id, Object value) {
            putRaw(into, id, value);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void putRaw(Map<NamespacedId, ?> into, NamespacedId id, Object value) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
            Map raw = into;
            Object previous = raw.putIfAbsent(id, value);
            if (previous != null && previous != value) {
                throw new IllegalArgumentException("SC-SPI-0001: " + id + " is already registered");
            }
        }
    }
}
