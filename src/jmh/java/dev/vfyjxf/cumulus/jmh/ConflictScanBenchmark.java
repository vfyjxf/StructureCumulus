package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** The request-rows-by-held-rows pair scan the settle path runs through ClaimMatrix. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class ConflictScanBenchmark {

    @Param({"512", "32768"})
    int rowCount;

    private ClaimRow[] requested;
    private ClaimRow[] held;
    private ClaimLedger ledger;
    private SettleRequest conflictingSettle;

    @Setup
    public void setUp() {
        InstanceId requester = new InstanceId(World.of(NamespacedId.of("bench", "world")), 1);
        InstanceId holder = new InstanceId(World.of(NamespacedId.of("bench", "world")), 2);
        // a distinct siting keeps the holder's rows incumbent, so every call re-runs the contest
        Siting requesterSiting = Siting.of(LatticePoint.of(1, 0, 0), Orientation.identity);
        requested = rowsOverCube(requester, requesterSiting);
        held = rowsOverCube(holder, Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity));
        ledger = ClaimLedger.inMemory(World.of(NamespacedId.of("bench", "world")));
        ledger.settle(new SettleRequest(holder, List.of(held), 0));
        conflictingSettle = new SettleRequest(requester, List.of(requested), ledger.revision());
    }

    private ClaimRow[] rowsOverCube(InstanceId instance, Siting siting) {
        NamespacedId bay = NamespacedId.of("bench", "bay");
        int edge = (int) Math.round(Math.cbrt(rowCount));
        ClaimRow[] rows = new ClaimRow[rowCount];
        int index = 0;
        for (int y = 0; y < edge; y++) {
            for (int z = 0; z < edge; z++) {
                for (int x = 0; x < edge; x++) {
                    rows[index++] = new ClaimRow(
                        LatticePoint.of(x, y, z).key(),
                        Sharing.exclusive,
                        siting,
                        bay,
                        instance
                    );
                }
            }
        }
        return rows;
    }

    @Benchmark
    public long scanRowsAgainstHolders() {
        long conflicts = 0;
        for (ClaimRow request : requested) {
            for (ClaimRow incumbent : held) {
                if (ClaimMatrix.conflicts(request, incumbent)) {
                    conflicts++;
                }
            }
        }
        return conflicts;
    }

    /** The same contest settled through the ledger, the path a settle conflict actually takes. */
    @Benchmark
    public ClaimVerdict settleAgainstHeldRows() {
        return ledger.settle(conflictingSettle);
    }
}
