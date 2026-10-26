package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Set;
/** A typed read face over a key to value map. */
public interface ContextView {

    <T> @Nullable T get(ContextKey<T> key);

    <T> Outcome<T> require(ContextKey<T> key);

    boolean has(ContextKey<?> key);

    Set<ContextKey<?>> keys();
}
