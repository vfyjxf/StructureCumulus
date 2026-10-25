package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.Objects;

/** Capture identity: one id per capture, never reused, and never a content digest. */
public record SnapshotId(NamespacedId world, long revision) implements Comparable<SnapshotId> {

    public SnapshotId {
        Objects.requireNonNull(world, "world");
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative: " + revision);
        }
    }

    public static SnapshotId of(NamespacedId world, long revision) {
        return new SnapshotId(world, revision);
    }

    @Override
    public int compareTo(SnapshotId other) {
        int byWorld = world.compareTo(other.world);
        return byWorld != 0 ? byWorld : Long.compare(revision, other.revision);
    }

    @Override
    public String toString() {
        return world + "@" + revision;
    }
}
