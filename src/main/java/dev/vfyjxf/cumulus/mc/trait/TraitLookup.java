package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.mc.plugin.AnnotationPluginLookup;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoader;
import dev.vfyjxf.cumulus.mc.plugin.PluginLookup;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** An immutable trait-provider registry keyed by (trait id, holder id). */
@NullMarked
public final class TraitLookup {

    private static final TraitLookup builtin = buildBuiltin();

    private final Map<ProviderKey, TraitProvider<?, ?>> providers;
    private final Set<NamespacedId> traits;

    private TraitLookup(Map<ProviderKey, TraitProvider<?, ?>> providers) {
        this.providers = Map.copyOf(providers);
        this.traits = providers.keySet().stream().map(ProviderKey::trait).collect(Collectors.toUnmodifiableSet());
    }

    public static TraitLookup builtin() {
        return builtin;
    }

    public static TraitLookup load(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        PluginLookup<TraitPlugin> discovered = AnnotationPluginLookup.of(TraitPlugin.class);
        PluginLookup<TraitPlugin> lookup = () -> {
            Collection<TraitPlugin> plugins = new ArrayList<>(discovered.findPlugins());
            if (plugins.stream().noneMatch(plugin -> TraitPlugin.builtin.equals(plugin.pluginId()))) {
                plugins.add(new BuiltinTraitPlugin());
            }
            return plugins;
        };
        List<TraitPlugin> plugins = PluginLoader.loadPlugin(logger, "trait", lookup);
        Builder builder = builder();
        TraitRegister register = register(builder);
        for (TraitPlugin plugin : plugins) {
            plugin.registerTraits(register);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T, H> @Nullable TraitProvider<T, H> provider(TraitKey<T> key, TraitHolder<H> holder) {
        Objects.requireNonNull(key, "key");
        @SuppressWarnings("unchecked")
        TraitProvider<T, H> provider = (TraitProvider<T, H>) provider(key.id(), holder);
        return provider;
    }

    @SuppressWarnings("unchecked")
    public <H> @Nullable TraitProvider<?, H> provider(NamespacedId trait, TraitHolder<H> holder) {
        Objects.requireNonNull(trait, "trait");
        Objects.requireNonNull(holder, "holder");
        return (TraitProvider<?, H>) providers.get(new ProviderKey(trait, holder.id()));
    }

    public boolean knows(NamespacedId trait) {
        Objects.requireNonNull(trait, "trait");
        return traits.contains(trait);
    }

    private static TraitLookup buildBuiltin() {
        Builder builder = builder();
        new BuiltinTraitPlugin().registerTraits(register(builder));
        return builder.build();
    }

    private static TraitRegister register(Builder builder) {
        return new TraitRegister() {
            @Override
            public <T, H> void register(TraitProvider<T, H> provider) {
                builder.add(provider);
            }
        };
    }

    private record ProviderKey(NamespacedId trait, NamespacedId holder) {}

    public static final class Builder {

        private final Map<ProviderKey, TraitProvider<?, ?>> providers = new LinkedHashMap<>();

        private Builder() {}

        public <T, H> Builder add(TraitProvider<T, H> provider) {
            Objects.requireNonNull(provider, "provider");
            NamespacedId trait = Objects.requireNonNull(provider.key().id(), "trait id");
            NamespacedId holder = Objects.requireNonNull(provider.holder().id(), "holder id");
            if (providers.putIfAbsent(new ProviderKey(trait, holder), provider) != null) {
                throw new IllegalArgumentException("duplicate trait provider for " + trait + " on " + holder);
            }
            return this;
        }

        public TraitLookup build() {
            return new TraitLookup(providers);
        }
    }
}
