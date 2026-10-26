package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.Merge;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

import java.util.function.BinaryOperator;
/** The shipped merge combinators. */
public final class Merges {

    public static Merge<Long> sum() {
        return Long::sum;
    }

    public static Merge<CuboidSet> union() {
        return CuboidSet::union;
    }

    public static Merge<Long> min() {
        return Math::min;
    }

    public static Merge<Long> max() {
        return Math::max;
    }

    public static Merge<Boolean> and() {
        return (left, right) -> left && right;
    }

    public static Merge<Boolean> or() {
        return (left, right) -> left || right;
    }

    public static <T> Merge<T> of(BinaryOperator<T> operator) {
        return operator::apply;
    }

    private Merges() {
    }
}
