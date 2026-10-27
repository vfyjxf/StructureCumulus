package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
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

/** An immutable trait-provider registry built by built-in and discovered plugins. */
@NullMarked
public final class TraitLookup {

    private static final TraitLookup builtin = buildBuiltin();

    private final Map<NamespacedId, TraitProvider<?>> providers;

    private TraitLookup(Map<NamespacedId, TraitProvider<?>> providers) {
        this.providers = Map.copyOf(providers);
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

    public @Nullable TraitProvider<?> provider(NamespacedId id) {
        Objects.requireNonNull(id, "id");
        return providers.get(id);
    }

    private static TraitLookup buildBuiltin() {
        Builder builder = builder();
        new BuiltinTraitPlugin().registerTraits(register(builder));
        return builder.build();
    }

    private static TraitRegister register(Builder builder) {
        return new TraitRegister() {
            @Override
            public <T> void register(TraitProvider<T> provider) {
                builder.add(provider);
            }
        };
    }

    public static final class Builder {

        private final Map<NamespacedId, TraitProvider<?>> providers = new LinkedHashMap<>();

        private Builder() {}

        public <T> Builder add(TraitProvider<T> provider) {
            Objects.requireNonNull(provider, "provider");
            NamespacedId id = Objects.requireNonNull(provider.key().id(), "trait id");
            if (providers.putIfAbsent(id, provider) != null) {
                throw new IllegalArgumentException("duplicate trait provider id: " + id);
            }
            return this;
        }

        public TraitLookup build() {
            return new TraitLookup(providers);
        }
    }
}
