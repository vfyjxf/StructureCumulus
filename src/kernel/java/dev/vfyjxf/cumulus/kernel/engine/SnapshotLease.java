package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;

/**
 * One capture's handle: the view every unit reads, and the child handles a result hands out. Closing
 * a handle releases that handle only and is idempotent; the instance keeps its own reference.
 */
public interface SnapshotLease extends AutoCloseable {

    SnapshotId id();

    WorldView view();

    /** Marks in the watched region since this capture. Zero means nothing was touched. */
    long changeCount();

    /** False once the capture's world was replaced or this handle was closed. */
    boolean isValid();

    /** Charges {@code retainedBytes}; {@code Missing(SC-MC-0502)} when the budget refuses. */
    Outcome<SnapshotLease> retain(PinBudget budget);

    boolean isOpen();

    @Override
    void close();
}
