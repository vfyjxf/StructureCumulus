package dev.vfyjxf.cumulus.kernel.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;

public final class Merges {

    private Merges() {
    }

    public static <T> Merge<T> combine(BinaryOperator<T> operator) {
        Objects.requireNonNull(operator, "operator");
        return operator::apply;
    }

    /** Sum preserves the runtime number type of the left contribution. */
    public static <N extends Number> Merge<N> sum() {
        return Merges::add;
    }

    public static <T> Merge<Set<T>> union() {
        return (left, right) -> {
            Set<T> combined = new LinkedHashSet<>(left);
            combined.addAll(right);
            return Collections.unmodifiableSet(combined);
        };
    }

    public static <T extends Comparable<? super T>> Merge<T> min() {
        return (left, right) -> left.compareTo(right) <= 0 ? left : right;
    }

    public static <T extends Comparable<? super T>> Merge<T> max() {
        return (left, right) -> left.compareTo(right) >= 0 ? left : right;
    }

    public static Merge<Boolean> and() {
        return (left, right) -> left && right;
    }

    public static Merge<Boolean> or() {
        return (left, right) -> left || right;
    }

    @SuppressWarnings("unchecked")
    private static <N extends Number> N add(N left, N right) {
        if (left instanceof Long) {
            return (N) Long.valueOf(left.longValue() + right.longValue());
        }
        if (left instanceof Integer) {
            return (N) Integer.valueOf(left.intValue() + right.intValue());
        }
        if (left instanceof Double) {
            return (N) Double.valueOf(left.doubleValue() + right.doubleValue());
        }
        if (left instanceof Float) {
            return (N) Float.valueOf(left.floatValue() + right.floatValue());
        }
        if (left instanceof Short) {
            return (N) Short.valueOf((short) (left.shortValue() + right.shortValue()));
        }
        if (left instanceof Byte) {
            return (N) Byte.valueOf((byte) (left.byteValue() + right.byteValue()));
        }
        throw new IllegalArgumentException("sum over an unsupported number: " + left.getClass().getName());
    }
}
