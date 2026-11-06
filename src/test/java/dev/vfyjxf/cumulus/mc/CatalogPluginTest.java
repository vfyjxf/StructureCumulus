package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.spi.CatalogRegistrar;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginDependency;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoader;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoadingException;
import dev.vfyjxf.cumulus.mc.plugin.PluginLookup;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogPluginTest {

    private static final Logger logger = LoggerFactory.getLogger(CatalogPluginTest.class);

    private static final NamespacedId reactor = NamespacedId.of("mymod", "reactor");

    @Test
    void aPluginKindIsVisibleToTheCompiler() {
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        PluginLoader.register(logger, "catalog", lookup(new ReactorPlugin()), registrar);
        CompileResult result = DocumentCompiler.compile(reactorDocument(), context(registrar.catalogs()));
        assertNotNull(result.blueprint(), () -> result.findings().findings().toString());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
    }

    @Test
    void theDefaultRegisterAddsNothing() {
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        PluginLoader.register(logger, "catalog", lookup(new BarePlugin()), registrar);
        Catalogs catalogs = registrar.catalogs();
        Catalogs builtin = Catalogs.builtin();
        assertEquals(builtin.expectations().ids(), catalogs.expectations().ids());
        assertEquals(builtin.focuses().ids(), catalogs.focuses().ids());
        assertEquals(builtin.computations().ids(), catalogs.computations().ids());
        assertEquals(builtin.derivers().ids(), catalogs.derivers().ids());
        assertEquals(builtin.codes().ids(), catalogs.codes().ids());
        assertEquals(builtin.remedies().ids(), catalogs.remedies().ids());
    }

    @Test
    void registrationFollowsDependencyOrder() {
        List<String> order = new ArrayList<>();
        RecordingPlugin base = new RecordingPlugin("base", order);
        RecordingPlugin dependent = new RecordingPlugin(
            "dependent",
            order,
            new PluginDependency(base.pluginId(), PluginDependency.Order.after, PluginDependency.Constraint.required)
        );
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        PluginLoader.register(logger, "catalog", lookup(dependent, base), registrar);
        assertEquals(List.of("test:base", "test:dependent"), order);
    }

    @Test
    void aThrowingRegisterIsAFatalFailureNamingItself() {
        ThrowingPlugin broken = new ThrowingPlugin();
        CountingPlugin dependent = new CountingPlugin(
            new PluginDependency(broken.pluginId(), PluginDependency.Order.after, PluginDependency.Constraint.required)
        );
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        PluginLoadingException exception = assertThrows(
            PluginLoadingException.class,
            () -> PluginLoader.register(logger, "catalog", lookup(broken, dependent), registrar)
        );
        assertTrue(
            exception.failures().stream().anyMatch(failure -> failure.pluginId().equals(broken.pluginId())),
            () -> exception.failures().toString()
        );
        assertTrue(
            exception.failures().stream().anyMatch(failure -> failure.pluginId().equals(dependent.pluginId())),
            () -> exception.failures().toString()
        );
        assertFalse(dependent.registered, "a dependent of the broken plugin is skipped");
        assertEquals(1, registrar.failures().size());
        assertEquals(broken.pluginId(), registrar.failures().get(0).pluginId());
    }

    @Test
    void aLinkageErrorInRegisterIsAFatalFailureNamingItself() {
        MissingClassPlugin broken = new MissingClassPlugin();
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        PluginLoadingException exception = assertThrows(
            PluginLoadingException.class,
            () -> PluginLoader.register(logger, "catalog", lookup(broken), registrar)
        );
        assertTrue(
            exception.failures().stream().anyMatch(failure -> failure.pluginId().equals(broken.pluginId())),
            () -> exception.failures().toString()
        );
        assertEquals(1, registrar.failures().size());
        assertEquals(broken.pluginId(), registrar.failures().get(0).pluginId());
        assertInstanceOf(NoClassDefFoundError.class, registrar.failures().get(0).cause().getCause());
    }

    @Test
    void builtinIdsCannotBeReplaced() {
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        AdapterCatalogs.seed(registrar);
        PluginLoadingException exception = assertThrows(
            PluginLoadingException.class,
            () -> PluginLoader.register(logger, "catalog", lookup(new StateReplacingPlugin()), registrar)
        );
        assertTrue(
            exception.failures().stream().anyMatch(
                failure -> failure.pluginId().equals(NamespacedId.of("test", "state_replacer"))
                        && Objects.requireNonNull(failure.cause().getMessage()).contains("SC-SPI-0003")
            ),
            () -> exception.failures().toString()
        );
    }

    @Test
    void anUnregisteredKindIsStillACompileError() {
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        PluginLoader.register(logger, "catalog", lookup(new BarePlugin()), registrar);
        CompileResult result = DocumentCompiler.compile(reactorDocument(), context(registrar.catalogs()));
        assertNull(result.blueprint());
        assertTrue(
            result.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.documentFactory.code())),
            () -> result.findings().findings().toString()
        );
    }

    private static PluginLookup<ModPlugin> lookup(ModPlugin... plugins) {
        return () -> List.of(plugins);
    }

    private static CompilerContext context(Catalogs catalogs) {
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
    }

    private static Document reactorDocument() {
        String text = """
                {"schemaVersion":1,"id":"mymod:reactor_doc","bounds":[0,0,0,1,1,1],
                 "bays":[{"id":"mymod:core","box":[0,0,0,1,1,1],"coverage":"every_cell",
                          "footprint":"box","sharing":"shared"}],
                 "checks":[{"id":"mymod:core_check","bay":"mymod:core","focus":{"kind":"bay"},
                            "expect":{"kind":"mymod:reactor"}}]}
                """;
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(NamespacedId.of("mymod", "reactor_doc"), text, errors);
        assertTrue(errors.isEmpty(), () -> "unexpected findings: " + errors);
        assertNotNull(document, "the document resolves");
        return document;
    }

    private static ExpectationFactory reactorFactory() {
        return new ExpectationFactory() {
            @Override
            public NamespacedId id() {
                return reactor;
            }

            @Override
            public @Nullable Expectation<?> create(Value args, FactoryContext context) {
                return Expectations.eachCell(Expectations.air());
            }
        };
    }

    private static final class BarePlugin implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "bare");
        }
    }

    private static final class ReactorPlugin implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("mymod", "reactor_plugin");
        }

        @Override
        public void register(CatalogRegistrar registrar) {
            registrar.expectation(reactor, reactorFactory());
        }
    }

    private static final class StateReplacingPlugin implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "state_replacer");
        }

        @Override
        public void register(CatalogRegistrar registrar) {
            registrar.expectation(NamespacedId.of("structurecumulus", "state"), reactorFactory());
        }
    }

    private static final class ThrowingPlugin implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "broken");
        }

        @Override
        public void register(CatalogRegistrar registrar) {
            throw new IllegalStateException("the reactor exploded");
        }
    }

    private static final class MissingClassPlugin implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "missing_class");
        }

        @Override
        public void register(CatalogRegistrar registrar) {
            throw new NoClassDefFoundError("the optional mod is absent");
        }
    }

    private static final class CountingPlugin implements ModPlugin {

        private final PluginDependency dependency;
        boolean registered;

        private CountingPlugin(PluginDependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "dependent");
        }

        @Override
        public Set<PluginDependency> dependencies() {
            return Set.of(dependency);
        }

        @Override
        public void register(CatalogRegistrar registrar) {
            registered = true;
        }
    }

    private static final class RecordingPlugin implements ModPlugin {

        private final NamespacedId id;
        private final List<String> order;
        private final Set<PluginDependency> dependencies;

        private RecordingPlugin(String path, List<String> order, PluginDependency... dependencies) {
            this.id = NamespacedId.of("test", path);
            this.order = order;
            this.dependencies = Set.of(dependencies);
        }

        @Override
        public NamespacedId pluginId() {
            return id;
        }

        @Override
        public Set<PluginDependency> dependencies() {
            return dependencies;
        }

        @Override
        public void register(CatalogRegistrar registrar) {
            order.add(id.text());
        }
    }
}
