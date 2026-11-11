package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.EditStep;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.CatalogRegistrar;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.JsonReader;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.build.ItemSourceProvider;
import dev.vfyjxf.cumulus.mc.build.ItemSourceRegistrar;
import dev.vfyjxf.cumulus.mc.build.ItemSources;
import dev.vfyjxf.cumulus.mc.build.MaterializeContext;
import dev.vfyjxf.cumulus.mc.build.MaterializedEdit;
import dev.vfyjxf.cumulus.mc.build.Materializer;
import dev.vfyjxf.cumulus.mc.build.MaterializerRegistrar;
import dev.vfyjxf.cumulus.mc.build.Materializers;
import dev.vfyjxf.cumulus.mc.build.VanillaMaterializer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin parity: a plugin's materializers and item sources reach the session's tables through
 * the loader's one ordering and failure policy, and a plugin's remedy provider reaches {@code solve}.
 */
class PluginParityTest {

    private static final Logger logger = LoggerFactory.getLogger(PluginParityTest.class);
    private static final NamespacedId vanilla = NamespacedId.of("structurecumulus", "vanilla");

    @Test
    void theDefaultPassesAddNothing() {
        PluginTables tables = load(new BarePlugin());
        assertEquals(List.of(vanilla), tables.materializers().ids());
        assertTrue(tables.itemSources().ids().isEmpty(), "a bare plugin registers no item source");
        assertTrue(tables.failures().isEmpty(), () -> tables.failures().toString());
    }

    @Test
    void aPluginMaterializerPrecedesTheBuiltInChain() {
        NamespacedId id = NamespacedId.of("test", "chest_loader");
        PluginTables tables = load(new MaterializerPlugin(id));
        assertEquals(List.of(id, vanilla), tables.materializers().ids(), "a plugin answers before vanilla");
        Materializer registered = tables.materializers().get(id);
        assertNotNull(registered);
        assertEquals(id, registered.id());
        assertInstanceOf(VanillaMaterializer.class, tables.materializers().get(vanilla));
    }

    @Test
    void aPluginItemSourceKindIsResolvableById() {
        NamespacedId id = NamespacedId.of("test", "network");
        PluginTables tables = load(new ItemSourcePlugin(id));
        assertEquals(List.of(id), tables.itemSources().ids());
        assertTrue(tables.itemSources().has(id));
        assertFalse(tables.itemSources().has(NamespacedId.of("test", "absent")));
    }

    @Test
    void aDuplicateMaterializerIdIsRejectedAndAttributed() {
        NamespacedId id = NamespacedId.of("test", "duplicate_materializer");
        Materializer first = new NoopMaterializer(id);
        Materializer second = new NoopMaterializer(id);
        PluginTables tables = load(new MaterializerPlugin(first), new SecondMaterializerPlugin(second));
        PluginLoader.LoadingFailure<ModPlugin> failure = failure(
            tables,
            NamespacedId.of("test", "second_materializer")
        );
        assertTrue(failure.reason().contains("SC-SPI-0003"), failure::reason);
        assertSame(first, tables.materializers().get(id), "the first registration stays");
    }

    @Test
    void aDuplicateItemSourceIdIsRejectedAndAttributed() {
        NamespacedId id = NamespacedId.of("test", "duplicate_source");
        PluginTables tables = load(new ItemSourcePlugin(id), new SecondItemSourcePlugin(id));
        PluginLoader.LoadingFailure<ModPlugin> failure = failure(tables, NamespacedId.of("test", "second_source"));
        assertTrue(failure.reason().contains("SC-SPI-0003"), failure::reason);
    }

    @Test
    void aThrowingExtensionPassIsAttributedAndSkipsLaterPasses() {
        ThrowingMaterializerPlugin plugin = new ThrowingMaterializerPlugin();
        List<String> logs = new ArrayList<>();
        try (LogCapture capture = LogCapture.attach(PluginParityTest.class)) {
            PluginTables tables = load(plugin);
            logs.addAll(capture.messages());
            assertEquals(List.of(vanilla), tables.materializers().ids());
            PluginLoader.LoadingFailure<ModPlugin> failure = failure(tables, plugin.pluginId());
            assertTrue(failure.reason().contains("materializers pass failed"), failure::reason);
            assertFalse(plugin.itemSourcesCalled, "a plugin that failed a pass contributes no later pass");
            assertTrue(tables.itemSources().ids().isEmpty());
        }
        assertTrue(
            logs.stream().anyMatch(line -> line.contains(plugin.pluginId().text()) && line.contains("materializers")),
            () -> "the failure is logged with the plugin id: " + logs
        );
    }

    @Test
    void aLinkageErrorInAnExtensionPassIsAttributed() {
        MissingClassMaterializerPlugin plugin = new MissingClassMaterializerPlugin();
        PluginTables tables = load(plugin);
        PluginLoader.LoadingFailure<ModPlugin> failure = failure(tables, plugin.pluginId());
        assertTrue(failure.reason().contains("materializers pass failed"), failure::reason);
        assertEquals(List.of(vanilla), tables.materializers().ids());
    }

    @Test
    void aDependentOfAFailedExtensionPluginIsSkipped() {
        ThrowingMaterializerPlugin broken = new ThrowingMaterializerPlugin();
        DependentPlugin dependent = new DependentPlugin(
            new PluginDependency(broken.pluginId(), PluginDependency.Order.after, PluginDependency.Constraint.required)
        );
        PluginTables tables = load(broken, dependent);
        PluginLoader.LoadingFailure<ModPlugin> failure = failure(tables, dependent.pluginId());
        assertTrue(failure.reason().contains("dependency " + broken.pluginId()), failure::reason);
        assertFalse(dependent.materializersCalled, "a dependent of a failed plugin is skipped");
    }

    @Test
    void aPluginRemedyProviderAppearsInSolve() {
        NamespacedId providerId = NamespacedId.of("test", "plugin_remedy");
        RemedyProvider provider = new PluginRemedyProvider(providerId);
        CatalogRegistrar catalogs = CatalogRegistrar.builtin();
        PluginLoader.register(logger, "catalog", lookup(new RemedyPlugin(providerId, provider)), catalogs);
        StructureKernel kernel = StructureKernel.create(catalogs.catalogs());
        Blueprint blueprint = compile(catalogs.catalogs());
        Finding finding = Finding.builder(Codes.expected).phase(Phase.check).message("the cell is missing").build();
        RemedyContext context = new RemedyContext(
            blueprint,
            Siting.of(LatticePoint.origin, Orientation.identity),
            null,
            0,
            null,
            null,
            16,
            null
        );
        SolveResult result = kernel.solve(FindingBundle.of(List.of(finding)), context);
        assertTrue(result.findings().findings().isEmpty(), () -> result.findings().findings().toString());
        assertTrue(
            result.remedies().stream().anyMatch(
                remedy -> remedy instanceof Remedy.Repair repair && repair.rationale().equals("from the plugin")
            ),
            () -> result.remedies().toString()
        );
    }

    private static PluginTables load(ModPlugin... plugins) {
        CatalogRegistrar catalogs = CatalogRegistrar.builtin();
        List<ModPlugin> loaded = PluginLoader.register(logger, "catalog", lookup(plugins), catalogs);
        MaterializerRegistrar materializers = MaterializerRegistrar.builtin();
        ItemSourceRegistrar itemSources = ItemSourceRegistrar.empty();
        List<PluginLoader.LoadingFailure<ModPlugin>> failures = PluginLoader.registerExtensions(
            logger,
            "extension",
            loaded,
            List.of(
                new PluginPass<>("materializers", plugin -> plugin.registerMaterializers(materializers)),
                new PluginPass<>("item sources", plugin -> plugin.registerItemSources(itemSources))
            )
        );
        return new PluginTables(
            catalogs.catalogs(),
            materializers.materializers(),
            itemSources.itemSources(),
            failures
        );
    }

    private static PluginLoader.LoadingFailure<ModPlugin> failure(PluginTables tables, NamespacedId pluginId) {
        return tables.failures().stream().filter(failure -> failure.instance().pluginId().equals(pluginId)).findFirst()
                .orElseThrow(() -> new AssertionError("no failure for " + pluginId + ": " + tables.failures()));
    }

    private static PluginLookup<ModPlugin> lookup(ModPlugin... plugins) {
        return () -> List.of(plugins);
    }

    private static Blueprint compile(Catalogs catalogs) {
        String text = """
                {"schemaVersion":1,"id":"test:remedy_doc","bounds":[0,0,0,1,1,1],
                 "bays":[{"id":"cell","box":[0,0,0,1,1,1],"coverage":"every_cell",
                          "footprint":"none","sharing":"shared"}],
                 "checks":[{"id":"cell_check","bay":"cell","focus":{"kind":"bay"},
                          "expect":{"kind":"each_cell","element":{"kind":"air"}}}]}
                """;
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(NamespacedId.of("test", "remedy_doc"), text, errors);
        assertNotNull(document, () -> "the document resolves: " + errors);
        CompilerContext context = new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
        var result = DocumentCompiler.compile(document, context);
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint, () -> "the document compiles: " + result.findings().findings());
        return blueprint;
    }

    private record PluginTables(
        Catalogs catalogs,
        Materializers materializers,
        ItemSources itemSources,
        List<PluginLoader.LoadingFailure<ModPlugin>> failures
    ) {}

    private static final class BarePlugin implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "bare");
        }
    }

    private static final class MaterializerPlugin implements ModPlugin {

        private final Materializer materializer;

        private MaterializerPlugin(NamespacedId id) {
            this(new NoopMaterializer(id));
        }

        private MaterializerPlugin(Materializer materializer) {
            this.materializer = materializer;
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "materializer");
        }

        @Override
        public void registerMaterializers(MaterializerRegistrar registrar) {
            registrar.materializer(materializer);
        }
    }

    private static final class SecondMaterializerPlugin implements ModPlugin {

        private final Materializer materializer;

        private SecondMaterializerPlugin(Materializer materializer) {
            this.materializer = materializer;
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "second_materializer");
        }

        @Override
        public void registerMaterializers(MaterializerRegistrar registrar) {
            registrar.materializer(materializer);
        }
    }

    private static final class ItemSourcePlugin implements ModPlugin {

        private final NamespacedId id;

        private ItemSourcePlugin(NamespacedId id) {
            this.id = id;
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "item_source");
        }

        @Override
        public void registerItemSources(ItemSourceRegistrar registrar) {
            registrar.itemSource(new NoopProvider(id, "first"));
        }
    }

    private static final class SecondItemSourcePlugin implements ModPlugin {

        private final NamespacedId id;

        private SecondItemSourcePlugin(NamespacedId id) {
            this.id = id;
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "second_source");
        }

        @Override
        public void registerItemSources(ItemSourceRegistrar registrar) {
            registrar.itemSource(new NoopProvider(id, "second"));
        }
    }

    private static final class ThrowingMaterializerPlugin implements ModPlugin {

        boolean itemSourcesCalled;

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "broken_materializer");
        }

        @Override
        public void registerMaterializers(MaterializerRegistrar registrar) {
            throw new IllegalStateException("the materializer exploded");
        }

        @Override
        public void registerItemSources(ItemSourceRegistrar registrar) {
            itemSourcesCalled = true;
        }
    }

    private static final class MissingClassMaterializerPlugin implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "missing_materializer_class");
        }

        @Override
        public void registerMaterializers(MaterializerRegistrar registrar) {
            throw new NoClassDefFoundError("the optional mod is absent");
        }
    }

    private static final class DependentPlugin implements ModPlugin {

        private final PluginDependency dependency;
        boolean materializersCalled;

        private DependentPlugin(PluginDependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "dependent_extension");
        }

        @Override
        public Set<PluginDependency> dependencies() {
            return Set.of(dependency);
        }

        @Override
        public void registerMaterializers(MaterializerRegistrar registrar) {
            materializersCalled = true;
        }
    }

    private static final class RemedyPlugin implements ModPlugin {

        private final NamespacedId remedy;
        private final RemedyProvider provider;

        private RemedyPlugin(NamespacedId remedy, RemedyProvider provider) {
            this.remedy = remedy;
            this.provider = provider;
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "remedy_plugin");
        }

        @Override
        public void register(CatalogRegistrar registrar) {
            registrar.remedy(remedy, provider);
        }
    }

    private static final class PluginRemedyProvider implements RemedyProvider {

        private final NamespacedId id;

        private PluginRemedyProvider(NamespacedId id) {
            this.id = id;
        }

        @Override
        public NamespacedId id() {
            return id;
        }

        @Override
        public List<Remedy> solve(Finding finding, RemedyContext context) {
            if (!finding.code().equals(Codes.expected.code())) {
                return List.of();
            }
            LatticePoint origin = context.siting().origin();
            CuboidSet cells = CuboidSet
                    .of(Cuboid.of(origin.x(), origin.y(), origin.z(), origin.x() + 1, origin.y() + 1, origin.z() + 1));
            EditStep step = new EditStep(new Edit.Remove(cells), Locus.world(cells), Applicability.machineApplicable);
            return List.of(
                new Remedy.Repair(
                    new EditPlan(List.of(step), "plugin plan"),
                    Applicability.machineApplicable,
                    "from the plugin"
                )
            );
        }
    }

    private static final class NoopMaterializer implements Materializer {

        private final NamespacedId id;

        private NoopMaterializer(NamespacedId id) {
            this.id = id;
        }

        @Override
        public NamespacedId id() {
            return id;
        }

        @Override
        public @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context) {
            return null;
        }
    }

    private record NoopProvider(NamespacedId id, String tag) implements ItemSourceProvider {

        @Override
        public @Nullable ItemSource create(ServerPlayer player, ServerLevel level) {
            return null;
        }
    }
}
