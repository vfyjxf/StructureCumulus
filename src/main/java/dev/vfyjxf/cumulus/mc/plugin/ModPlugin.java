package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.spi.CatalogRegistrar;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/** A discoverable host extension with a stable id and dependency edges. */
@NullMarked
public interface ModPlugin {

    NamespacedId pluginId();

    default Set<PluginDependency> dependencies() {
        return Set.of();
    }

    /** Adds this plugin's document kinds; the default adds none. */
    default void register(CatalogRegistrar registrar) {}
}
