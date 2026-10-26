package dev.vfyjxf.cumulus.kernel.core;

/** Host-injected cooperative cancellation. */
@FunctionalInterface
public interface CancelToken {
    boolean isCancelled();

    static CancelToken never() {
        return () -> false;
    }
}
