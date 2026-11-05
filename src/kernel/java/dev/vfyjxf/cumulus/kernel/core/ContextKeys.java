package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

/** The only key factory. */
public final class ContextKeys {

    private static final Comparator<ContextKey<?>> ORDER =
            Comparator.<ContextKey<?>, NamespacedId>comparing(ContextKey::id)
                    .thenComparing(key -> key.type().getName());

    /** The only order a {@link ContextKey} set may be iterated in. */
    public static Comparator<ContextKey<?>> order() {
        return ORDER;
    }

    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention) {
        return new ContextKey<>(id, type, retention, null, null);
    }

    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention, @Nullable Merge<T> merge) {
        return new ContextKey<>(id, type, retention, merge, null);
    }

    public static <T> ContextKey<T> of(
        NamespacedId id,
        Class<T> type,
        Retention retention,
        @Nullable Merge<T> merge,
        @Nullable T whenAbsent
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(retention, "retention");
        return new ContextKey<>(id, type, retention, merge, whenAbsent);
    }

    private ContextKeys() {}
}
