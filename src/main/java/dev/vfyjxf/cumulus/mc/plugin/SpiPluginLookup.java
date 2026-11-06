package dev.vfyjxf.cumulus.mc.plugin;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Discovers plugins through Java's {@link ServiceLoader}. */
@NullMarked
public final class SpiPluginLookup<T extends ModPlugin> implements PluginLookup<T> {

    private static final Logger logger = LoggerFactory.getLogger(SpiPluginLookup.class);

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
        List<T> result = new ArrayList<>();
        for (ServiceLoader.Provider<T> provider : ServiceLoader.load(pluginClass, classLoader).stream().toList()) {
            try {
                result.add(provider.get());
            } catch (LinkageError | ServiceConfigurationError failure) {
                // a provider's instantiation failure arrives wrapped in ServiceConfigurationError, not raw
                logger.error(
                    "Plugin class {} could not be loaded: {}",
                    provider.type().getName(),
                    failure.getMessage()
                );
            }
        }
        return result;
    }
}
