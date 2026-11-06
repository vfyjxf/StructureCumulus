package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** One sort of shuffled claim rows through the settle request's canonical order. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class ClaimSortBenchmark {

    @Param({"512", "32768"})
    int rowCount;

    private InstanceId instance;
    private List<ClaimRow> shuffled;

    @Setup
    public void setUp() {
        instance = new InstanceId(World.of(NamespacedId.of("bench", "world")), 1);
        Siting siting = Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity);
        NamespacedId bay = NamespacedId.of("bench", "bay");
        int edge = (int) Math.round(Math.cbrt(rowCount));
        List<ClaimRow> rows = new ArrayList<>(rowCount);
        for (int y = 0; y < edge; y++) {
            for (int z = 0; z < edge; z++) {
                for (int x = 0; x < edge; x++) {
                    rows.add(new ClaimRow(LatticePoint.of(x, y, z).key(), Sharing.exclusive, siting, bay, instance));
                }
            }
        }
        Collections.shuffle(rows, new Random(42));
        shuffled = rows;
    }

    @Benchmark
    public SettleRequest sortOnce() {
        return new SettleRequest(instance, shuffled, 0);
    }
}
