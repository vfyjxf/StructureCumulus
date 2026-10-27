package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginDependency;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/** A plugin that contributes trait providers and loads after the built-in providers. */
@NullMarked
public interface TraitPlugin extends ModPlugin {

    NamespacedId builtin = NamespacedId.of(Constants.modId, "builtin");

    PluginDependency afterBuiltin = new PluginDependency(
        builtin,
        PluginDependency.Order.after,
        PluginDependency.Constraint.required
    );

    @Override
    default Set<PluginDependency> dependencies() {
        return Set.of(afterBuiltin);
    }

    default void registerTraits(TraitRegister register) {}
}
