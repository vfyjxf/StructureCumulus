package dev.vfyjxf.cumulus.mc.plugin;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;

/** Discovers plugin instances; ordering and validation belong to {@link PluginLoader}. */
@NullMarked
@FunctionalInterface
public interface PluginLookup<T extends ModPlugin> {

    Collection<T> findPlugins();

    default PluginLookup<T> combine(PluginLookup<T> other) {
        return () -> {
            Collection<T> result = new ArrayList<>(findPlugins());
            result.addAll(other.findPlugins());
            return result;
        };
    }
}
