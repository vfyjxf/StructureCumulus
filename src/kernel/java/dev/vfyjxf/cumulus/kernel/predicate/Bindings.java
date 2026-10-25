package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable typed map of the values one check bound, kept in insertion order. */
public final class Bindings {

    static final FindingCode CONFLICT = FindingCode.of(FindingCode.Family.EXP, 3);

    public static final Bindings EMPTY = new Bindings(Map.of());

    private final Map<BindingKey<?>, Object> values;

    private Bindings(Map<BindingKey<?>, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Bindings empty() {
        return EMPTY;
    }

    public static <T> Bindings of(BindingKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return new Bindings(Map.of(key, value));
    }

    public static Bindings of(Binding<?>... bindings) {
        Objects.requireNonNull(bindings, "bindings");
        Map<BindingKey<?>, Object> values = new LinkedHashMap<>();
        for (Binding<?> binding : bindings) {
            Objects.requireNonNull(binding, "binding");
            if (values.putIfAbsent(binding.key(), binding.value()) != null) {
                throw new IllegalArgumentException("duplicate binding key: " + binding.key());
            }
        }
        return values.isEmpty() ? EMPTY : new Bindings(values);
    }

    public record Binding<T>(BindingKey<T> key, T value) {

        public Binding {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(BindingKey<T> key) {
        Objects.requireNonNull(key, "key");
        return (T) values.get(key);
    }

    public boolean has(BindingKey<?> key) {
        return values.containsKey(Objects.requireNonNull(key, "key"));
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Set<BindingKey<?>> keys() {
        return values.keySet();
    }

    /**
     * The union of both maps, or a {@code MISMATCH} carrying one {@code SC-EXP-0003} finding when a
     * key or an id holds two values; no merged map is ever visible without a verdict.
     */
    public Reply merge(Bindings other) {
        Objects.requireNonNull(other, "other");
        Map<BindingKey<?>, Object> merged = new LinkedHashMap<>(values);
        for (Map.Entry<BindingKey<?>, Object> entry : other.values.entrySet()) {
            Object existing = merged.putIfAbsent(entry.getKey(), entry.getValue());
            if (existing != null && !existing.equals(entry.getValue())) {
                return conflict(entry.getKey(), existing, entry.getValue());
            }
        }
        Map<NamespacedId, Object> byId = new LinkedHashMap<>();
        for (Map.Entry<BindingKey<?>, Object> entry : merged.entrySet()) {
            Object existing = byId.putIfAbsent(entry.getKey().id(), entry.getValue());
            if (existing != null) {
                return conflict(entry.getKey(), existing, entry.getValue());
            }
        }
        return merged.isEmpty() ? Reply.match() : Reply.match(new Bindings(merged));
    }

    private static Reply conflict(BindingKey<?> key, Object left, Object right) {
        String message = "bindings disagree on " + key.id() + ": " + left + " and " + right;
        Finding finding = Finding.builder(CONFLICT)
                .severity(Severity.ERROR)
                .message(message)
                .label(Locus.local(CuboidSet.empty()), message)
                .stage(Stage.SETTLE)
                .build();
        return Reply.mismatch(finding);
    }

    @Override
    public String toString() {
        return "Bindings" + values;
    }
}
