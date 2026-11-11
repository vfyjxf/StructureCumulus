package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.spi.CatalogRegistrar;
import dev.vfyjxf.cumulus.mc.build.ItemSourceRegistrar;
import dev.vfyjxf.cumulus.mc.build.MaterializerRegistrar;
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

    /** Adds this plugin's materializers; the default adds none. */
    default void registerMaterializers(MaterializerRegistrar registrar) {}

    /** Adds this plugin's item sources; the default adds none. */
    default void registerItemSources(ItemSourceRegistrar registrar) {}
}
