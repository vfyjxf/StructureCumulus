package dev.vfyjxf.cumulus.kernel.core;

/** An associative fold with no identity. */
@FunctionalInterface
public interface Merge<T> {
    T combine(T left, T right);
}
