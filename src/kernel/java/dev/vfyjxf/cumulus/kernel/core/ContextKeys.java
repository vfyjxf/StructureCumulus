package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

/** The only key factory. */
public final class ContextKeys {

    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention) {
        return new ContextKey<>(id, type, retention, null);
    }

    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention, @Nullable Merge<T> merge) {
        return new ContextKey<>(id, type, retention, merge);
    }

    private ContextKeys() {}
}
