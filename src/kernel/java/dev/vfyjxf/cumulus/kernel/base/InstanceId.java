package dev.vfyjxf.cumulus.kernel.base;

public record InstanceId(NamespacedId world, long serial) implements Comparable<InstanceId> {

    public InstanceId {
        if (serial <= 0) {
            throw new IllegalArgumentException("serial must be positive: " + serial);
        }
    }

    public static InstanceId of(NamespacedId world, long serial) {
        return new InstanceId(world, serial);
    }

    @Override
    public int compareTo(InstanceId other) {
        int byWorld = world.compareTo(other.world);
        return byWorld != 0 ? byWorld : Long.compare(serial, other.serial);
    }

    @Override
    public String toString() {
        return world + "#" + serial;
    }
}
