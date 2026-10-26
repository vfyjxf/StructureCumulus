package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;
/** One typed key of one instance. */
public record KeyLocus(InstanceId instance, NamespacedId key) {

    public KeyLocus {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(key, "key");
    }
}
