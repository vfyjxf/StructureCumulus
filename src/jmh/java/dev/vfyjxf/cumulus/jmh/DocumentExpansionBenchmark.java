package dev.vfyjxf.cumulus.jmh;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.BaySpec;
import dev.vfyjxf.cumulus.kernel.document.BlueprintSpec;
import dev.vfyjxf.cumulus.kernel.document.CheckSpec;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.PlacementSpec;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * DocumentCompiler over the two pathological placement graphs: a 10 000-layer chain that recurses
 * once per layer, and a 22-layer diamond whose two definitions per layer are flattened once per
 * path, 8 388 606 definition instances in total. Definitions contribute placements only, so the
 * number isolates the expansion walk: merged bays would spend it in the built blueprint's pairwise
 * overlap validation instead.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx2g", "-Xss16m"})
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class DocumentExpansionBenchmark {

    private static final int chainLayers = 10_000;

    private static final int diamondLayers = 22;

    @State(Scope.Benchmark)
    public static class Chain {

        private final CompilerContext context = context();

        private final Document document = chainDocument();

        @Setup
        public void setUp() {
            requireExpansion(context, document, "chain");
        }
    }

    @State(Scope.Benchmark)
    public static class Diamond {

        private final CompilerContext context = context();

        private final Document document = diamondDocument();

        @Setup
        public void setUp() {
            requireExpansion(context, document, "diamond");
        }
    }

    @Benchmark
    public CompileResult compileChain(Chain chain) {
        return DocumentCompiler.compile(chain.document, chain.context);
    }

    @Benchmark
    public CompileResult compileDiamond(Diamond diamond) {
        return DocumentCompiler.compile(diamond.document, diamond.context);
    }

    private static void requireExpansion(CompilerContext context, Document document, String name) {
        CompileResult result = DocumentCompiler.compile(document, context);
        if (!result.succeeded()) {
            throw new IllegalStateException(
                "the " + name + " benchmark must measure a successful expansion, not the findings path: "
                        + result.findings().findings()
            );
        }
    }

    private static CompilerContext context() {
        Catalogs catalogs = Catalogs.builtin();
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
    }

    private static Document chainDocument() {
        BlueprintSpec root = rootSpec(List.of(new PlacementSpec(id("c1"), LatticePoint.origin, id("next"), null)));
        Map<NamespacedId, BlueprintSpec> definitions = new LinkedHashMap<>();
        for (int layer = 1; layer <= chainLayers; layer++) {
            List<PlacementSpec> placements = layer == chainLayers
                    ? List.of()
                    : List.of(new PlacementSpec(id("c" + (layer + 1)), LatticePoint.origin, id("next"), null));
            definitions.put(id("c" + layer), definition(id("c" + layer), placements));
        }
        return new Document(Document.currentVersion, root, definitions, Map.of());
    }

    private static Document diamondDocument() {
        BlueprintSpec root = rootSpec(
            List.of(
                new PlacementSpec(id("l1"), LatticePoint.origin, id("l"), null),
                new PlacementSpec(id("r1"), LatticePoint.origin, id("r"), null)
            )
        );
        Map<NamespacedId, BlueprintSpec> definitions = new LinkedHashMap<>();
        for (int layer = 1; layer <= diamondLayers; layer++) {
            List<PlacementSpec> placements = layer == diamondLayers
                    ? List.of()
                    : List.of(
                        new PlacementSpec(id("l" + (layer + 1)), LatticePoint.origin, id("l"), null),
                        new PlacementSpec(id("r" + (layer + 1)), LatticePoint.origin, id("r"), null)
                    );
            definitions.put(id("l" + layer), definition(id("l" + layer), placements));
            definitions.put(id("r" + layer), definition(id("r" + layer), placements));
        }
        return new Document(Document.currentVersion, root, definitions, Map.of());
    }

    private static BlueprintSpec rootSpec(List<PlacementSpec> placements) {
        return new BlueprintSpec(
            id("root"),
            cell(),
            false,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    id("bay"),
                    cell().asSet(),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null,
                    null
                )
            ),
            List.of(),
            List.of(rootCheck()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            placements,
            Map.of(),
            null,
            null
        );
    }

    private static BlueprintSpec definition(NamespacedId id, List<PlacementSpec> placements) {
        return new BlueprintSpec(
            id,
            cell(),
            false,
            false,
            Value.object(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            placements,
            Map.of(),
            null,
            null
        );
    }

    private static Cuboid cell() {
        return Cuboid.of(0, 0, 0, 1, 1, 1);
    }

    private static CheckSpec rootCheck() {
        return new CheckSpec(
            id("root_check"),
            id("bay"),
            NamespacedId.of("bay"),
            Value.object(),
            NamespacedId.of("each_cell"),
            Value.object(Map.of("element", Value.object(Map.of("kind", Value.string("air"))))),
            null,
            null
        );
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("bench", path);
    }
}
