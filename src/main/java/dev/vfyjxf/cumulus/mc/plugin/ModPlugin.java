package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/** A discoverable host extension with a stable id and dependency edges. */
@NullMarked
public interface ModPlugin {

    NamespacedId pluginId();

    default Set<PluginDependency> dependencies() {
        return Set.of();
    }
}
