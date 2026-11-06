package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The typed identity of one context value.
 *
 * @param id         unique within a blueprint
 * @param type       the reifiable value type
 * @param retention  whether the value is attempt-local or published
 * @param merge      non-null for a section-contributed key
 * @param whenAbsent the value used when every section was readable and nothing was contributed
 */
public record ContextKey<T>(
    NamespacedId id,
    Class<T> type,
    Retention retention,
    @Nullable Merge<T> merge,
    @Nullable T whenAbsent
) {

    public ContextKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(retention, "retention");
        if (whenAbsent != null && !type.isInstance(whenAbsent)) {
            throw new IllegalArgumentException("whenAbsent is not a " + type.getName() + ": " + whenAbsent);
        }
    }

    public boolean contributed() {
        return merge != null;
    }

    /** Hashes the slot, {@code (id, type)}; equality stays the full record, so equal keys hash alike. */
    @Override
    public int hashCode() {
        return Objects.hash(id, type.getName());
    }
}
