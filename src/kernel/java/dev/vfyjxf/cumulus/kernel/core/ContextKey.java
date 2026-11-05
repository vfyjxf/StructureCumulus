package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The typed identity of one context value.
 *
 * @param id        unique within a blueprint
 * @param type      the reifiable value type
 * @param retention whether the value is attempt-local or published
 * @param merge     non-null for a section-contributed key
 */
public record ContextKey<T>(
    NamespacedId id,
    Class<T> type,
    Retention retention,
    @Nullable Merge<T> merge
) {

    public ContextKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(retention, "retention");
    }

    public boolean contributed() {
        return merge != null;
    }
}
