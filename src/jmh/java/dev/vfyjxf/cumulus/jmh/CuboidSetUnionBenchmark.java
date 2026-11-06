package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
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

/**
 * The conflict path's per-cell union against one bulk construction of the same edge-cubed cells;
 * 32-edge per-cell union re-canonicalizes a growing set once per cell and does not finish in
 * minutes, so the large input stops at 16.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class CuboidSetUnionBenchmark {

    @Param({"8", "16"})
    int edge;

    private LatticePoint[] cells;
    private List<Cuboid> unitBoxes;

    @Setup
    public void setUp() {
        cells = new LatticePoint[edge * edge * edge];
        unitBoxes = new ArrayList<>(cells.length);
        int index = 0;
        for (int y = 0; y < edge; y++) {
            for (int z = 0; z < edge; z++) {
                for (int x = 0; x < edge; x++) {
                    LatticePoint cell = LatticePoint.of(x, y, z);
                    cells[index++] = cell;
                    unitBoxes.add(Cuboid.of(cell));
                }
            }
        }
    }

    @Benchmark
    public CuboidSet perCellUnion() {
        CuboidSet set = CuboidSet.empty();
        for (LatticePoint cell : cells) {
            set = set.union(Cuboid.of(cell));
        }
        return set;
    }

    @Benchmark
    public CuboidSet bulkConstruction() {
        return CuboidSet.of(unitBoxes);
    }
}
