package dev.vfyjxf.cumulus.mc.plugin;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.function.Consumer;

/** One named registration pass the loader runs for every loaded plugin. */
@NullMarked
public record PluginPass<T extends ModPlugin>(String name, Consumer<T> action) {

    public PluginPass {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a pass name is not blank");
        }
    }
}
