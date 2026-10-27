package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginDependency;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoader;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoadingException;
import dev.vfyjxf.cumulus.mc.plugin.PluginLookup;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {

    private static final Logger logger = LoggerFactory.getLogger(PluginLoaderTest.class);

    @Test
    void kahnOrderIsDeterministic() {
        TestPlugin a = plugin("a");
        TestPlugin b = plugin("b");
        TestPlugin c = plugin("c", dependency("a", PluginDependency.Order.after, PluginDependency.Constraint.required));
        List<TestPlugin> sorted = PluginLoader.loadPlugin(logger, "test", lookup(a, b, c));
        assertEquals(List.of(a, b, c), sorted, "independent plugins sort by id, dependents follow");
    }

    @Test
    void aBeforeEdgeOrdersTheDependentAfter() {
        TestPlugin a = plugin(
            "a",
            dependency("c", PluginDependency.Order.before, PluginDependency.Constraint.required)
        );
        TestPlugin b = plugin("b");
        TestPlugin c = plugin("c");
        assertEquals(List.of(a, b, c), PluginLoader.loadPlugin(logger, "test", lookup(a, b, c)));
    }

    @Test
    void aDuplicatePluginIdIsFatal() {
        PluginLoadingException exception = assertThrows(
            PluginLoadingException.class,
            () -> PluginLoader.load(lookup(plugin("dup"), plugin("dup")))
        );
        assertTrue(Objects.requireNonNull(exception.getMessage()).contains("duplicate plugin id"));
    }

    @Test
    void aMissingRequiredDependencyIsFatal() {
        TestPlugin a = plugin(
            "a",
            dependency("missing", PluginDependency.Order.after, PluginDependency.Constraint.required)
        );
        PluginLoader.LoadingResult<TestPlugin> result = PluginLoader.load(lookup(a));
        assertTrue(result.plugins().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals(PluginLoader.FailureType.fatal, result.failures().get(0).type());
        assertThrows(PluginLoadingException.class, () -> PluginLoader.loadPlugin(logger, "test", lookup(a)));
    }

    @Test
    void aMissingOptionalDependencyIsAWarning() {
        TestPlugin a = plugin(
            "a",
            dependency("missing", PluginDependency.Order.after, PluginDependency.Constraint.optional)
        );
        PluginLoader.LoadingResult<TestPlugin> result = PluginLoader.load(lookup(a));
        assertEquals(List.of(a), result.plugins());
        assertEquals(PluginLoader.FailureType.warning, result.failures().get(0).type());
        assertEquals(List.of(a), PluginLoader.loadPlugin(logger, "test", lookup(a)));
    }

    @Test
    void aMissingOptionalRequiredDependencySkipsThePlugin() {
        TestPlugin a = plugin(
            "a",
            dependency("missing", PluginDependency.Order.after, PluginDependency.Constraint.optionalRequired)
        );
        TestPlugin b = plugin("b");
        PluginLoader.LoadingResult<TestPlugin> result = PluginLoader.load(lookup(a, b));
        assertEquals(List.of(b), result.plugins());
        assertEquals(PluginLoader.FailureType.warning, result.failures().get(0).type());
        assertEquals(List.of(b), PluginLoader.loadPlugin(logger, "test", lookup(a, b)));
    }

    @Test
    void aCycleIsFatal() {
        TestPlugin a = plugin("a", dependency("b", PluginDependency.Order.after, PluginDependency.Constraint.required));
        TestPlugin b = plugin("b", dependency("a", PluginDependency.Order.after, PluginDependency.Constraint.required));
        PluginLoadingException exception = assertThrows(
            PluginLoadingException.class,
            () -> PluginLoader.load(lookup(a, b))
        );
        assertTrue(Objects.requireNonNull(exception.getMessage()).contains("dependency cycle"));
    }

    private static TestPlugin plugin(String path, PluginDependency... dependencies) {
        return new TestPlugin(NamespacedId.of("test", path), Set.of(dependencies));
    }

    private static PluginDependency dependency(
        String path,
        PluginDependency.Order order,
        PluginDependency.Constraint constraint
    ) {
        return new PluginDependency(NamespacedId.of("test", path), order, constraint);
    }

    @SafeVarargs
    private static PluginLookup<TestPlugin> lookup(TestPlugin... plugins) {
        return () -> List.of(plugins);
    }

    private record TestPlugin(NamespacedId id, Set<PluginDependency> dependencies) implements ModPlugin {

        @Override
        public NamespacedId pluginId() {
            return id;
        }
    }
}
