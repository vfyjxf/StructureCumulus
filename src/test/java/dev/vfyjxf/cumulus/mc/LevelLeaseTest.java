package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelLeaseTest {

    private static final SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "lease")), 1);

    @Test
    void closeInvalidatesTheHandleEvenWhileTheHostIsLive() {
        LevelLease lease = new LevelLease(snapshot, view(), () -> true);

        assertTrue(lease.isCurrent());
        lease.close();
        assertFalse(lease.isCurrent(), "a closed handle is never current");
        lease.close();
        assertFalse(lease.isCurrent(), "close is idempotent");
    }

    @Test
    void theHostPredicateDecidesLiveness() {
        AtomicLong revision = new AtomicLong(1);
        AtomicBoolean closed = new AtomicBoolean();
        LevelSnapshot view = view();
        LevelLease lease = new LevelLease(snapshot, view, () -> !closed.get() && revision.get() == 1);

        assertTrue(lease.isCurrent());
        revision.set(2);
        assertFalse(lease.isCurrent(), "a newer capture invalidates the lease");
        assertSame(view, lease.view());
        assertEquals(snapshot, lease.id());
    }

    private static LevelSnapshot view() {
        return new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 1, 1, 1), Map.of());
    }
}
