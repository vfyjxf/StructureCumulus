package dev.vfyjxf.cumulus.kernel.context;

@FunctionalInterface
public interface Merge<T> {

    T combine(T left, T right);
}
