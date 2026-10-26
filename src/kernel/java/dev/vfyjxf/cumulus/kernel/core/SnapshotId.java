package dev.vfyjxf.cumulus.kernel.core;

import java.util.Objects;

/** The identity of one capture. */
public record SnapshotId(World world, long revision) implements Comparable<SnapshotId> {

    public SnapshotId {
        Objects.requireNonNull(world, "world");
        if (revision < 1) {
            throw new IllegalArgumentException("revision >= 1");
        }
    }

    @Override
    public int compareTo(SnapshotId other) {
        int byWorld = world.compareTo(other.world);
        return byWorld != 0 ? byWorld : Long.compare(revision, other.revision);
    }
}
