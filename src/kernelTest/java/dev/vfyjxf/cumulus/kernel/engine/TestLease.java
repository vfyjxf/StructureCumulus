package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;

import java.util.List;

/**
 * An in-memory lease: a capture is "open" and "valid" until closed or invalidated, counts its marks,
 * and a child handle charges its bytes.
 */
public final class TestLease implements SnapshotLease {

    private static final FindingCode PIN_REFUSED = FindingCode.of(FindingCode.Family.MC, 502);

    private final SnapshotId id;
    private final WorldView view;
    private final long retainedBytes;
    private boolean open = true;
    private boolean valid = true;
    private long changeCount;

    private TestLease(SnapshotId id, WorldView view, long retainedBytes, long changeCount) {
        this.id = id;
        this.view = view;
        this.retainedBytes = retainedBytes;
        this.changeCount = changeCount;
    }

    public static TestLease captured(SnapshotId id, WorldView view, long retainedBytes) {
        return new TestLease(id, view, retainedBytes, 0L);
    }

    /** Records one mark in the watched region, as the host's watch would. */
    public TestLease mark() {
        changeCount++;
        return this;
    }

    /** Simulates the capture's world being replaced while the handle stays open. */
    public TestLease invalidate() {
        valid = false;
        return this;
    }

    @Override
    public SnapshotId id() {
        return id;
    }

    @Override
    public WorldView view() {
        return view;
    }

    @Override
    public long changeCount() {
        return changeCount;
    }

    @Override
    public boolean isValid() {
        return open && valid;
    }

    @Override
    public Outcome<SnapshotLease> retain(PinBudget budget) {
        if (!open) {
            return new Outcome.Missing<>(FindingBundle.of(refusal("the capture is closed")));
        }
        if (!budget.tryCharge(retainedBytes)) {
            return new Outcome.Missing<>(FindingBundle.of(refusal(
                    "pinning " + retainedBytes + " bytes would pass the ceiling")));
        }
        return new Outcome.Found<>(new TestLease(id, view, retainedBytes, changeCount), List.of(),
                FindingBundle.empty());
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public String toString() {
        return "TestLease[" + id + (open ? ", open" : ", closed")
                + (valid ? "" : ", invalid") + ", marks=" + changeCount + "]";
    }

    private static Finding refusal(String message) {
        return Finding.builder(PIN_REFUSED)
                .severity(Severity.ERROR)
                .message(message)
                .stage(Stage.SETTLE)
                .build();
    }
}
