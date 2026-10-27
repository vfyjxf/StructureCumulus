package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

/**
 * A host-owned read port for one instance's previously published context, keyed by value identity
 * {@code (id, type)} rather than by the producing {@code ContextKey}.
 */
public interface CachedContext {

    <T> @Nullable T get(NamespacedId id, Class<T> type);
}
