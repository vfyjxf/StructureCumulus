package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;

/** A typed host-read token: the kernel carries the id, never the read type. */
public record HostType<H>(NamespacedId id) implements Comparable<HostType<?>> {

    public HostType {
        Objects.requireNonNull(id, "id");
    }

    public static <H> HostType<H> of(NamespacedId id) {
        return new HostType<>(id);
    }

    @Override
    public int compareTo(HostType<?> other) {
        return id.compareTo(other.id);
    }
}
