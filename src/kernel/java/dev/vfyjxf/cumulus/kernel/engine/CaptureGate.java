package dev.vfyjxf.cumulus.kernel.engine;

import java.util.Objects;

/**
 * The whole capture check: two facts read off the request's lease, after the units ran and before
 * anything is published or settled.
 *
 * <p>Nothing here is read from a supplier and nothing re-captures. The world identity lives in the
 * lease ({@link SnapshotLease#isValid()}), the watch counter in the lease
 * ({@link SnapshotLease#changeCount()}), and the slot's coalescing ticket is the scheduler's business,
 * never a capture fact.
 */
public final class CaptureGate {

    public enum Verdict {
        /** Same world, same capture, and no mark since it was taken. */
        CURRENT,
        /** The watched region was marked after capture. */
        CHANGED,
        /** The world was replaced, or the handle was closed. */
        SUPERSEDED
    }

    private CaptureGate() {}

    public static Verdict evaluate(SnapshotLease lease) {
        Objects.requireNonNull(lease, "lease");
        if (!lease.isValid()) {
            return Verdict.SUPERSEDED;
        }
        return lease.changeCount() == 0L ? Verdict.CURRENT : Verdict.CHANGED;
    }
}
