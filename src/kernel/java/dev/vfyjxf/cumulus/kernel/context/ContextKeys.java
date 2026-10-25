package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class ContextKeys {

    private ContextKeys() {
    }

    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention,
                                       @Nullable Merge<T> merge, @Nullable T whenAbsent) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(retention, "retention");
        if (whenAbsent != null && !type.isInstance(whenAbsent)) {
            throw new IllegalArgumentException("whenAbsent");
        }
        return new ContextKey<>(id, type, retention, merge, whenAbsent);
    }

    public static <T> ContextKey<T> register(CatalogsDraft draft, ContextKey<T> key) {
        Objects.requireNonNull(draft, "draft");
        return draft.registerKey(key);
    }
}
