package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An immutable nested-extent-provider registry keyed by holder id. */
@NullMarked
public final class NestedExtentLookup {

    private static final NestedExtentLookup capabilities = buildCapabilities();

    private final Map<NamespacedId, NestedExtentProvider<?>> providers;

    private NestedExtentLookup(Map<NamespacedId, NestedExtentProvider<?>> providers) {
        this.providers = Map.copyOf(providers);
    }

    public static NestedExtentLookup capabilities() {
        return capabilities;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public <H> @Nullable NestedExtent get(NestedExtentHolder<H> holder, H actual) {
        Objects.requireNonNull(holder, "holder");
        NestedExtentProvider<H> provider = (NestedExtentProvider<H>) providers.get(holder.id());
        return provider == null ? null : provider.get(actual);
    }

    public boolean knows(NestedExtentHolder<?> holder) {
        Objects.requireNonNull(holder, "holder");
        return providers.containsKey(holder.id());
    }

    private static NestedExtentLookup buildCapabilities() {
        return builder().add(NestedExtentProviders.block).add(NestedExtentProviders.entity).build();
    }

    public static final class Builder {

        private final Map<NamespacedId, NestedExtentProvider<?>> providers = new LinkedHashMap<>();

        private Builder() {}

        public <H> Builder add(NestedExtentProvider<H> provider) {
            Objects.requireNonNull(provider, "provider");
            NamespacedId holder = Objects.requireNonNull(provider.holder().id(), "holder id");
            if (providers.putIfAbsent(holder, provider) != null) {
                throw new IllegalArgumentException("duplicate nested extent provider for " + holder);
            }
            return this;
        }

        public NestedExtentLookup build() {
            return new NestedExtentLookup(providers);
        }
    }
}
