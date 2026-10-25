package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.predicate.Bindings;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** What one attempt produced: the view it read, the values it published and everything it touched. */
public record MatchOutcome(Stage stage, Siting siting, WorldView view,
                           Map<ContextKey<?>, Object> context, Bindings bindings, ReadScope reads) {

    public MatchOutcome {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(reads, "reads");
        Set<ContextKey<?>> keys = new TreeSet<>(context.keySet());
        Map<ContextKey<?>, Object> ordered = new LinkedHashMap<>();
        for (ContextKey<?> key : keys) {
            ordered.put(key, Objects.requireNonNull(context.get(key), "value"));
        }
        context = Collections.unmodifiableMap(ordered);
    }

    public static MatchOutcome empty(Stage stage, Siting siting, WorldView view) {
        return new MatchOutcome(stage, siting, view, Map.of(), Bindings.empty(), ReadScope.none());
    }

    public <T> @Nullable T value(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        Object value = context.get(key);
        return value == null ? null : key.type().cast(value);
    }
}
