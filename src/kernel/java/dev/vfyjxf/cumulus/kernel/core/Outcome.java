package dev.vfyjxf.cumulus.kernel.core;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
/** A lookup that either found a value or did not. */
public sealed interface Outcome<T> permits Outcome.Found, Outcome.Missing {

    record Found<T>(T value) implements Outcome<T> {
        public Found {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public <R> R fold(Function<T, R> onFound, Supplier<R> onMissing) {
            return onFound.apply(value);
        }
    }

    record Missing<T>() implements Outcome<T> {
        @Override
        public <R> R fold(Function<T, R> onFound, Supplier<R> onMissing) {
            return onMissing.get();
        }
    }

    <R> R fold(Function<T, R> onFound, Supplier<R> onMissing);

    default boolean isPresent() {
        return this instanceof Found<T>;
    }

    static <T> Outcome<T> found(T value) {
        return new Found<>(value);
    }

    static <T> Outcome<T> missing() {
        return new Missing<>();
    }
}
