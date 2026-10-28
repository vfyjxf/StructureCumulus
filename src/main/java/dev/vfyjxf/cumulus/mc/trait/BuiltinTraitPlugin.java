package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginDependency;
import dev.vfyjxf.cumulus.mc.plugin.PluginMarker;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/** The built-in plugin that registers the standard block and entity providers. */
@NullMarked
@PluginMarker
public final class BuiltinTraitPlugin implements TraitPlugin, ModPlugin {

    public BuiltinTraitPlugin() {}

    @Override
    public NamespacedId pluginId() {
        return builtin;
    }

    @Override
    public Set<PluginDependency> dependencies() {
        return Set.of();
    }

    @Override
    public void registerTraits(TraitRegister register) {
        BlockTraitProviders.registerAll(register);
        EntityTraitProviders.registerAll(register);
    }
}
