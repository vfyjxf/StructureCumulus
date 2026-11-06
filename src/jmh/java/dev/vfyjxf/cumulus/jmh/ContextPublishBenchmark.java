package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** One whole-table publish into the in-memory context store. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class ContextPublishBenchmark {

    @Param({"8", "1024"})
    int keyCount;

    private ContextStore store;
    private InstanceId instance;
    private Map<ContextKey<?>, Object> wholeTable;

    @Setup
    public void setUp() {
        store = ContextStore.inMemory();
        instance = new InstanceId(World.of(NamespacedId.of("bench", "world")), 1);
        Map<ContextKey<?>, Object> table = new HashMap<>();
        for (int i = 0; i < keyCount; i++) {
            table.put(ContextKeys.of(NamespacedId.of("bench", "key_" + i), Long.class, Retention.instance), (long) i);
        }
        store.publish(instance, table, FindingBundle.empty(), Spend.zero);
        wholeTable = table;
    }

    @Benchmark
    public StructureContext publishWholeTable() {
        return store.publish(instance, wholeTable, FindingBundle.empty(), Spend.zero);
    }
}
