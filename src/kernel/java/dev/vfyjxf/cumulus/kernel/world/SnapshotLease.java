package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
/** A host-owned capture handle. The kernel never closes it. */
public interface SnapshotLease extends AutoCloseable {

    SnapshotId id();

    WorldView view();

    boolean isCurrent();

    @Override
    void close();
}
