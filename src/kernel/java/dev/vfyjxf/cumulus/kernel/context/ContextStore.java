package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** The per-instance value store. One writer per instance. */
public interface ContextStore {

    StructureContext context(InstanceId instance);

    /** Removes one instance context, returning the stored context or {@code null} when absent. */
    @Nullable
    StructureContext remove(InstanceId instance);

    StructureContext publish(
        InstanceId instance,
        Map<ContextKey<?>, Object> values,
        FindingBundle findings,
        Spend spend
    );

    static ContextStore inMemory() {
        return new ContextStore() {
            private static final Comparator<ContextKey<?>> keyOrder = Comparator
                    .comparing((ContextKey<?> key) -> key.id()).thenComparing(key -> key.type().getName());
            private final ConcurrentHashMap<InstanceId, StructureContext> contexts = new ConcurrentHashMap<>();

            @Override
            public StructureContext context(InstanceId instance) {
                Objects.requireNonNull(instance, "instance");
                return contexts.computeIfAbsent(instance, StructureContext::empty);
            }

            @Override
            public @Nullable StructureContext remove(InstanceId instance) {
                Objects.requireNonNull(instance, "instance");
                return contexts.remove(instance);
            }

            @Override
            public StructureContext publish(
                InstanceId instance,
                Map<ContextKey<?>, Object> values,
                FindingBundle findings,
                Spend spend
            ) {
                Objects.requireNonNull(instance, "instance");
                Objects.requireNonNull(values, "values");
                Objects.requireNonNull(findings, "findings");
                Objects.requireNonNull(spend, "spend");
                for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
                    Objects.requireNonNull(entry.getKey(), "key");
                    Objects.requireNonNull(entry.getValue(), "value");
                    if (entry.getKey().retention() != Retention.instance) {
                        throw new IllegalArgumentException(
                            "only INSTANCE values are published: " + entry.getKey().id()
                        );
                    }
                }
                return contexts.compute(instance, (ignored, current) -> {
                    StructureContext base = current == null ? StructureContext.empty(instance) : current;
                    TreeMap<ContextKey<?>, Object> merged = new TreeMap<>(keyOrder);
                    merged.putAll(base.values());
                    merged.putAll(values);
                    return new StructureContext(
                        instance,
                        base.revision() + 1,
                        Collections.unmodifiableMap(merged),
                        findings,
                        base.spend().plus(spend)
                    );
                });
            }
        };
    }
}
