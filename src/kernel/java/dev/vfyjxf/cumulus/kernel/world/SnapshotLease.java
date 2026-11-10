package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;

/** A host-owned capture handle. The kernel never closes it. isCurrent runs on the server thread; only view() may run on any thread. */
public interface SnapshotLease extends AutoCloseable {

    SnapshotId id();

    WorldView view();

    boolean isCurrent();

    @Override
    void close();
}
