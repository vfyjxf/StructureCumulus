package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.engine.BayBinding;
import dev.vfyjxf.cumulus.kernel.engine.ClaimPlanner;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
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

/** One bound-footprint derivation over scattered single-cell subjects. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class BoundFootprintBenchmark {

    @Param({"128", "512"})
    int subjectCount;

    private Blueprint blueprint;
    private Siting siting;
    private InstanceId instance;
    private List<BayBinding> bindings;

    @Setup
    public void setUp() {
        World world = World.of(NamespacedId.of("bench", "world"));
        NamespacedId type = NamespacedId.of("bench", "subject");
        SnapshotId snapshot = new SnapshotId(world, 1);
        List<Cuboid> cells = new ArrayList<>(subjectCount);
        List<OccupantSnapshot> subjects = new ArrayList<>(subjectCount);
        for (int i = 0; i < subjectCount; i++) {
            LatticePoint cell = LatticePoint.of(2 * i, 0, 0);
            cells.add(Cuboid.of(cell));
            subjects.add(
                new OccupantSnapshot(
                    new OccupantRef(snapshot, 0, type, OccupantKind.block, cell),
                    new OccupantDescriptor(type, OccupantKind.block),
                    Cuboid.of(cell),
                    List.of()
                )
            );
        }
        Bay bay = Bay.of(
            NamespacedId.of("bench", "bay"),
            CuboidSet.of(cells),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        blueprint = Blueprint.builder(NamespacedId.of("bench", "blueprint"))
                .bounds(Cuboid.of(0, 0, 0, 2 * subjectCount, 1, 1))
                .check(
                    Check.of(
                        NamespacedId.of("bench", "check"),
                        bay,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.anyOccupant())
                    )
                ).build();
        siting = Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity);
        instance = new InstanceId(world, 1);
        bindings = List.of(new BayBinding(bay, subjects));
    }

    @Benchmark
    public ClaimPlanner.Derived deriveBoundFootprint() {
        return ClaimPlanner.derive(blueprint, siting, instance, bindings);
    }
}
