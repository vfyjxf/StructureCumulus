package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.engine.ClaimPlanner;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
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

/** One claim derivation of an edge-cubed box bay, the sorted rows included. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class ClaimPlanningBenchmark {

    @Param({"8", "32"})
    int edge;

    private Blueprint blueprint;
    private Siting siting;
    private InstanceId instance;

    @Setup
    public void setUp() {
        Cuboid cube = Cuboid.of(0, 0, 0, edge, edge, edge);
        Bay bay = Bay.of(NamespacedId.of("bench", "bay"), cube, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        blueprint = Blueprint.builder(NamespacedId.of("bench", "blueprint")).bounds(cube).check(
            Check.of(NamespacedId.of("bench", "check"), bay, Focuses.bay(), Expectations.eachCell(Expectations.air()))
        ).build();
        siting = Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity);
        instance = new InstanceId(World.of(NamespacedId.of("bench", "world")), 1);
    }

    @Benchmark
    public ClaimPlanner.Derived deriveOneStructure() {
        return ClaimPlanner.derive(blueprint, siting, instance, List.of());
    }
}
