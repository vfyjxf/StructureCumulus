package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** The typed values of one instance at one revision. */
public record StructureContext(
    InstanceId instance,
    long revision,
    Map<ContextKey<?>, Object> values,
    FindingBundle findings,
    Spend spend
) implements ContextView {

    private static final Comparator<ContextKey<?>> KEY_ORDER = Comparator.comparing((ContextKey<?> key) -> key.id())
            .thenComparing(key -> key.type().getName());

    public StructureContext {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(spend, "spend");
        if (revision < 0) {
            throw new IllegalArgumentException("revision >= 0");
        }
        TreeMap<ContextKey<?>, Object> sorted = new TreeMap<>(KEY_ORDER);
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            ContextKey<?> key = Objects.requireNonNull(entry.getKey(), "key");
            Object value = Objects.requireNonNull(entry.getValue(), "value");
            if (!key.type().isInstance(value)) {
                throw new IllegalArgumentException("value for " + key.id() + " is not a " + key.type().getName());
            }
            sorted.put(key, value);
        }
        values = Collections.unmodifiableMap(sorted);
    }

    public static StructureContext empty(InstanceId instance) {
        return new StructureContext(instance, 0, Map.of(), FindingBundle.empty(), Spend.ZERO);
    }

    @Override
    public <T> @Nullable T get(ContextKey<T> key) {
        Object value = values.get(key);
        return value == null ? null : key.type().cast(value);
    }

    @Override
    public <T> Outcome<T> require(ContextKey<T> key) {
        T value = get(key);
        return value == null ? Outcome.missing() : Outcome.found(value);
    }

    @Override
    public boolean has(ContextKey<?> key) {
        return values.containsKey(key);
    }

    @Override
    public Set<ContextKey<?>> keys() {
        return values.keySet();
    }
}
