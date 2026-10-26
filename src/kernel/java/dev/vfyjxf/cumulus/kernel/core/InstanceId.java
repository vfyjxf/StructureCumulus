package dev.vfyjxf.cumulus.kernel.core;

import java.util.Objects;

/** The identity of one placed structure. */
public record InstanceId(World world, long serial) implements Comparable<InstanceId> {

    public InstanceId {
        Objects.requireNonNull(world, "world");
        if (serial < 1) {
            throw new IllegalArgumentException("serial >= 1");
        }
    }

    @Override
    public int compareTo(InstanceId other) {
        int byWorld = world.compareTo(other.world);
        return byWorld != 0 ? byWorld : Long.compare(serial, other.serial);
    }
}
