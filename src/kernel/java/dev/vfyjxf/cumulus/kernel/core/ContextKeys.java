package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** The only key factory. */
public final class ContextKeys {

    private static final Comparator<ContextKey<?>> keyOrder = Comparator
            .<ContextKey<?>, NamespacedId>comparing(ContextKey::id).thenComparing(key -> key.type().getName());

    /** The only order a {@link ContextKey} set may be iterated in. */
    public static Comparator<ContextKey<?>> order() {
        return keyOrder;
    }

    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention) {
        return new ContextKey<>(id, type, retention, null);
    }

    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention, @Nullable Merge<T> merge) {
        return new ContextKey<>(id, type, retention, merge);
    }

    private ContextKeys() {}
}
