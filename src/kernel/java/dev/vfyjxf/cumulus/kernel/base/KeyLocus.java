package dev.vfyjxf.cumulus.kernel.base;

public record KeyLocus(InstanceId instance, NamespacedId key) {

    public static KeyLocus of(InstanceId instance, NamespacedId key) {
        return new KeyLocus(instance, key);
    }
}
