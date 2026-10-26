package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.world.WorldView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
/** What one attempt produced. */
public record MatchOutcome(WorldView view, Map<ContextKey<?>, Object> values,
                           List<BayBinding> bindings, ReadScope reads, Spend spend) {

    private static final Comparator<ContextKey<?>> KEY_ORDER = Comparator
            .comparing((ContextKey<?> key) -> key.id())
            .thenComparing(key -> key.type().getName());

    public MatchOutcome {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(reads, "reads");
        Objects.requireNonNull(spend, "spend");
        TreeMap<ContextKey<?>, Object> sorted = new TreeMap<>(KEY_ORDER);
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            sorted.put(Objects.requireNonNull(entry.getKey(), "key"),
                    Objects.requireNonNull(entry.getValue(), "value"));
        }
        values = Collections.unmodifiableMap(sorted);
        bindings = List.copyOf(bindings);
    }

    public <T> Outcome<T> value(ContextKey<T> key) {
        Object value = values.get(key);
        return value == null ? Outcome.missing() : Outcome.found(key.type().cast(value));
    }
}
