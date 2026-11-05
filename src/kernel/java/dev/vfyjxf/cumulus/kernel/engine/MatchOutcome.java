package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.world.WorldView;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** What one attempt produced. */
public record MatchOutcome(
    WorldView view,
    Map<ContextKey<?>, Object> values,
    List<BayBinding> bindings,
    ReadScope reads,
    Spend spend
) {

    public MatchOutcome {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(reads, "reads");
        Objects.requireNonNull(spend, "spend");
        TreeMap<ContextKey<?>, Object> sorted = new TreeMap<>(ContextKeys.order());
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            ContextKey<?> key = Objects.requireNonNull(entry.getKey(), "key");
            Object value = Objects.requireNonNull(entry.getValue(), "value");
            if (!key.type().isInstance(value)) {
                throw new IllegalArgumentException("value for " + key.id() + " is not a " + key.type().getName());
            }
            sorted.put(key, value);
        }
        values = Collections.unmodifiableMap(sorted);
        bindings = List.copyOf(bindings);
    }

    public <T> Outcome<T> value(ContextKey<T> key) {
        Object value = values.get(key);
        return value == null ? Outcome.missing() : Outcome.found(key.type().cast(value));
    }
}
