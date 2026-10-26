package dev.vfyjxf.cumulus.kernel.core;

import java.util.Objects;
/** One level, named by its id. */
public record World(NamespacedId id) implements Comparable<World> {

    public World {
        Objects.requireNonNull(id, "id");
    }

    public static World of(NamespacedId id) {
        return new World(id);
    }

    @Override
    public int compareTo(World other) {
        return id.compareTo(other.id);
    }
}
