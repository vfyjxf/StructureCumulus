package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Identity is {@code (id, type)}; every other component is a property of that one declaration. */
public record ContextKey<T>(NamespacedId id, Class<T> type, Retention retention,
                            @Nullable Merge<T> merge, @Nullable T whenAbsent)
        implements Comparable<ContextKey<?>> {

    public ContextKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(retention, "retention");
    }

    public boolean contributed() {
        return merge != null;
    }

    @Override
    public int compareTo(ContextKey<?> other) {
        int byId = id.compareTo(other.id);
        return byId != 0 ? byId : type.getName().compareTo(other.type.getName());
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof ContextKey<?> key && id.equals(key.id) && type.equals(key.type);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + type.hashCode();
    }
}
