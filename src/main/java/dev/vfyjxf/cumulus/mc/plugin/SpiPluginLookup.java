package dev.vfyjxf.cumulus.mc.plugin;

import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Objects;
import java.util.ServiceLoader;

/** Discovers plugins through Java's {@link ServiceLoader}. */
@NullMarked
public final class SpiPluginLookup<T extends ModPlugin> implements PluginLookup<T> {

    private final Class<T> pluginClass;
    private final ClassLoader classLoader;

    private SpiPluginLookup(Class<T> pluginClass, ClassLoader classLoader) {
        this.pluginClass = pluginClass;
        this.classLoader = classLoader;
    }

    public static <T extends ModPlugin> SpiPluginLookup<T> of(Class<T> pluginClass) {
        return of(pluginClass, SpiPluginLookup.class.getClassLoader());
    }

    public static <T extends ModPlugin> SpiPluginLookup<T> of(Class<T> pluginClass, ClassLoader classLoader) {
        Objects.requireNonNull(pluginClass, "pluginClass");
        Objects.requireNonNull(classLoader, "classLoader");
        return new SpiPluginLookup<>(pluginClass, classLoader);
    }

    @Override
    public Collection<T> findPlugins() {
        return ServiceLoader.load(pluginClass, classLoader).stream().map(ServiceLoader.Provider::get).toList();
    }
}
