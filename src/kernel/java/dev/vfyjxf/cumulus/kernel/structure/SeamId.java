package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;

/** A seam id local to its blueprint. */
public record SeamId(NamespacedId id) implements Comparable<SeamId> {

    public SeamId {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public int compareTo(SeamId other) {
        return id.compareTo(other.id);
    }
}
