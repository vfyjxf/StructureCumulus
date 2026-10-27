package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Resolves plugin dependencies deterministically with Kahn's algorithm. */
@NullMarked
public final class PluginLoader {

    private static final Comparator<NamespacedId> idOrder = Comparator.comparing(NamespacedId::text);

    private PluginLoader() {}

    public static <T extends ModPlugin> LoadingResult<T> load(PluginLookup<T> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        Map<NamespacedId, T> discovered = new TreeMap<>(idOrder);
        for (T plugin : lookup.findPlugins()) {
            T value = Objects.requireNonNull(plugin, "plugin");
            NamespacedId id = Objects.requireNonNull(value.pluginId(), "pluginId");
            if (discovered.putIfAbsent(id, value) != null) {
                throw new PluginLoadingException(
                    List.of(
                        new PluginLoadingException.Failure(id, new IllegalStateException("duplicate plugin id: " + id))
                    )
                );
            }
        }

        List<LoadingFailure<T>> failures = new ArrayList<>();
        Set<NamespacedId> skipped = new TreeSet<>(idOrder);
        for (T plugin : discovered.values()) {
            for (PluginDependency dependency : dependencies(plugin)) {
                if (discovered.containsKey(dependency.pluginId())) {
                    continue;
                }
                String reason = "missing dependency " + dependency.pluginId() + " (" + dependency.constraint() + ")";
                switch (dependency.constraint()) {
                    case required -> {
                        failures.add(new LoadingFailure<>(plugin, FailureType.fatal, reason));
                        skipped.add(plugin.pluginId());
                    }
                    case optional -> failures.add(new LoadingFailure<>(plugin, FailureType.warning, reason));
                    case optionalRequired -> {
                        failures.add(new LoadingFailure<>(plugin, FailureType.warning, reason));
                        skipped.add(plugin.pluginId());
                    }
                }
            }
        }

        Map<NamespacedId, Set<NamespacedId>> outgoing = new TreeMap<>(idOrder);
        Map<NamespacedId, Integer> inDegree = new TreeMap<>(idOrder);
        for (NamespacedId id : discovered.keySet()) {
            outgoing.put(id, new TreeSet<>(idOrder));
            inDegree.put(id, 0);
        }
        for (T plugin : discovered.values()) {
            for (PluginDependency dependency : dependencies(plugin)) {
                if (dependency.order() == PluginDependency.Order.none
                        || !discovered.containsKey(dependency.pluginId())) {
                    continue;
                }
                NamespacedId from = dependency.order() == PluginDependency.Order.before
                        ? plugin.pluginId()
                        : dependency.pluginId();
                NamespacedId to = dependency.order() == PluginDependency.Order.before
                        ? dependency.pluginId()
                        : plugin.pluginId();
                if (Objects.requireNonNull(outgoing.get(from), "from node").add(to)) {
                    inDegree.merge(to, 1, Integer::sum);
                }
            }
        }

        PriorityQueue<NamespacedId> ready = new PriorityQueue<>(idOrder);
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        List<T> sorted = new ArrayList<>(discovered.size());
        while (!ready.isEmpty()) {
            NamespacedId id = ready.poll();
            sorted.add(Objects.requireNonNull(discovered.get(id), "discovered plugin"));
            for (NamespacedId next : Objects.requireNonNull(outgoing.get(id), "node")) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        if (sorted.size() != discovered.size()) {
            NamespacedId first = inDegree.entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            throw new PluginLoadingException(
                List.of(
                    new PluginLoadingException.Failure(
                        first,
                        new IllegalStateException("dependency cycle involving " + first)
                    )
                )
            );
        }

        List<T> loaded = sorted.stream().filter(plugin -> !skipped.contains(plugin.pluginId())).toList();
        failures.sort(
            Comparator.<LoadingFailure<T>, NamespacedId>comparing(failure -> failure.instance().pluginId(), idOrder)
                    .thenComparing(LoadingFailure::type).thenComparing(LoadingFailure::reason)
        );
        return new LoadingResult<>(List.copyOf(loaded), List.copyOf(failures));
    }

    public static <T extends ModPlugin> List<T> loadPlugin(
        Logger logger,
        String pluginCategory,
        PluginLookup<T> lookup
    ) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(pluginCategory, "pluginCategory");
        LoadingResult<T> result = load(lookup);
        List<PluginLoadingException.Failure> fatal = new ArrayList<>();
        for (LoadingFailure<T> failure : result.failures()) {
            if (failure.type() == FailureType.warning) {
                logger.warn(
                    "{}: {} is skipped because: {}",
                    pluginCategory,
                    failure.instance().pluginId(),
                    failure.reason()
                );
            } else {
                fatal.add(
                    new PluginLoadingException.Failure(
                        failure.instance().pluginId(),
                        new IllegalStateException(failure.reason())
                    )
                );
            }
        }
        if (!fatal.isEmpty()) {
            throw new PluginLoadingException(List.copyOf(fatal));
        }
        return result.plugins();
    }

    private static <T extends ModPlugin> List<PluginDependency> dependencies(T plugin) {
        List<PluginDependency> dependencies = new ArrayList<>(plugin.dependencies());
        dependencies.sort(
            Comparator.comparing(PluginDependency::pluginId, idOrder).thenComparing(PluginDependency::order)
                    .thenComparing(PluginDependency::constraint)
        );
        return dependencies;
    }

    /** The loaded plugins plus every warning/fatal dependency failure. */
    public record LoadingResult<T extends ModPlugin>(List<T> plugins, List<LoadingFailure<T>> failures) {

        public LoadingResult {
            plugins = List.copyOf(plugins);
            failures = List.copyOf(failures);
        }
    }

    /** One plugin-level loading failure. */
    public record LoadingFailure<T extends ModPlugin>(T instance, FailureType type, String reason) {

        public LoadingFailure {
            Objects.requireNonNull(instance, "instance");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public enum FailureType {
        fatal, warning
    }
}
