package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** One declared relation to another plugin. */
@NullMarked
public record PluginDependency(NamespacedId pluginId, Order order, Constraint constraint) {

    public PluginDependency {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(constraint, "constraint");
    }

    public enum Order {
        before, after, none
    }

    public enum Constraint {
        required, optional, optionalRequired
    }

    public boolean required() {
        return constraint == Constraint.required;
    }

    public boolean optional() {
        return constraint == Constraint.optional;
    }

    public boolean optionalRequired() {
        return constraint == Constraint.optionalRequired;
    }
}
