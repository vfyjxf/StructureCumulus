package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The only argument assertion helper. */
public final class Checks {

    public static void checkArgument(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static <T> T checkNotNull(@Nullable T value, String message) {
        return Objects.requireNonNull(value, message);
    }

    private Checks() {}
}
