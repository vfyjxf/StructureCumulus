package dev.vfyjxf.cumulus.util;

import org.jspecify.annotations.Nullable;

/**
 * Precondition checks, in the shapes the null-safety pass expects:
 * {@code checkNotNull} is the {@code CastToNonNullMethod} NullAway is configured with, so a value
 * it returns is non-null for the rest of the expression.
 */
public final class Checks {

    private Checks() {}

    public static void checkArgument(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    public static <T> T checkNotNull(@Nullable T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " can't be null");
        }
        return value;
    }
}
