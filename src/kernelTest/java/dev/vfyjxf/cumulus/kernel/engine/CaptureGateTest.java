package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The two lease facts map onto the three verdicts the drop causes depend on. */
class CaptureGateTest {

    private static final SnapshotId SNAPSHOT = SnapshotId.of(NamespacedId.of("minecraft", "overworld"), 7L);

    private static TestLease lease() {
        return TestLease.captured(SNAPSHOT, EngineWorld.builder().build(), 8L);
    }

    @Test
    void aFreshCaptureIsCurrent() {
        assertEquals(CaptureGate.Verdict.CURRENT, CaptureGate.evaluate(lease()));
    }

    @Test
    void aMarkAfterCaptureIsChanged() {
        assertEquals(CaptureGate.Verdict.CHANGED, CaptureGate.evaluate(lease().mark()));
    }

    @Test
    void aReplacedWorldIsSuperseded() {
        assertEquals(CaptureGate.Verdict.SUPERSEDED, CaptureGate.evaluate(lease().invalidate()));
    }

    @Test
    void aClosedHandleIsSuperseded() {
        TestLease lease = lease();
        lease.close();

        assertEquals(CaptureGate.Verdict.SUPERSEDED, CaptureGate.evaluate(lease));
    }
}
