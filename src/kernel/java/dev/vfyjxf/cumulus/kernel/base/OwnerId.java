package dev.vfyjxf.cumulus.kernel.base;

public record OwnerId(NamespacedId world, NamespacedId owner) implements Comparable<OwnerId> {

    public static OwnerId of(NamespacedId world, NamespacedId owner) {
        return new OwnerId(world, owner);
    }

    @Override
    public int compareTo(OwnerId other) {
        int byWorld = world.compareTo(other.world);
        return byWorld != 0 ? byWorld : owner.compareTo(other.owner);
    }

    @Override
    public String toString() {
        return world + "/" + owner;
    }
}
