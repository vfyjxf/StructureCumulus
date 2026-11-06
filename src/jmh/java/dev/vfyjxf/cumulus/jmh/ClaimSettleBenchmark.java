package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A conflict-free settle of fresh rows onto a ledger that already holds the other side. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class ClaimSettleBenchmark {

    @Param({"512", "32768"})
    int rowCount;

    private ClaimLedger ledger;
    private SettleRequest reSettle;

    @Setup
    public void setUp() {
        World world = World.of(NamespacedId.of("bench", "world"));
        InstanceId holder = new InstanceId(world, 1);
        InstanceId requester = new InstanceId(world, 2);
        Siting holderSiting = Siting.of(LatticePoint.of(1 << 20, 0, 0), Orientation.identity);
        Siting requesterSiting = Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity);
        NamespacedId holderBay = NamespacedId.of("bench", "holder_bay");
        NamespacedId requesterBay = NamespacedId.of("bench", "requester_bay");
        List<ClaimRow> holderRows = rows(holder, holderSiting, holderBay, 1 << 20);
        List<ClaimRow> requesterRows = rows(requester, requesterSiting, requesterBay, 0);
        ledger = ClaimLedger.inMemory(world);
        ledger.settle(new SettleRequest(holder, holderRows, 0));
        ledger.settle(new SettleRequest(requester, requesterRows, 1));
        reSettle = new SettleRequest(requester, requesterRows, ledger.revision());
    }

    private List<ClaimRow> rows(InstanceId instance, Siting siting, NamespacedId bay, int xBase) {
        int edge = (int) Math.round(Math.cbrt(rowCount));
        List<ClaimRow> rows = new ArrayList<>(rowCount);
        for (int y = 0; y < edge; y++) {
            for (int z = 0; z < edge; z++) {
                for (int x = 0; x < edge; x++) {
                    rows.add(
                        new ClaimRow(LatticePoint.of(xBase + x, y, z).key(), Sharing.exclusive, siting, bay, instance)
                    );
                }
            }
        }
        return rows;
    }

    @Benchmark
    public ClaimVerdict settleOntoHeldLedger() {
        return ledger.settle(reSettle);
    }
}
