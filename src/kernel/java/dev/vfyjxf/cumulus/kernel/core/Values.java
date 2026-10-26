package dev.vfyjxf.cumulus.kernel.core;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** Helpers for the neutral data tree. */
public final class Values {

    private static final Comparator<String> CODE_POINT = Values::compareCodePoints;

    public static Comparator<String> codePointOrder() {
        return CODE_POINT;
    }

    public static boolean contentEquals(Value left, Value right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.getClass() != right.getClass()) {
            return false;
        }
        return switch (left) {
            case Value.ObjectValue a -> {
                Value.ObjectValue b = (Value.ObjectValue) right;
                if (!a.members().keySet().equals(b.members().keySet())) {
                    yield false;
                }
                for (Map.Entry<String, Value> entry : a.members().entrySet()) {
                    if (!contentEquals(entry.getValue(), b.members().get(entry.getKey()))) {
                        yield false;
                    }
                }
                yield true;
            }
            case Value.ArrayValue a -> {
                Value.ArrayValue b = (Value.ArrayValue) right;
                if (a.elements().size() != b.elements().size()) {
                    yield false;
                }
                for (int i = 0; i < a.elements().size(); i++) {
                    if (!contentEquals(a.elements().get(i), b.elements().get(i))) {
                        yield false;
                    }
                }
                yield true;
            }
            case Value.StringValue a -> a.value().equals(((Value.StringValue) right).value());
            case Value.NumberValue a -> a.value() == ((Value.NumberValue) right).value();
            case Value.BooleanValue a -> a.value() == ((Value.BooleanValue) right).value();
            case Value.NullValue ignored -> true;
        };
    }

    public static boolean contentEquals(List<Value> left, List<Value> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!contentEquals(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int compareCodePoints(String left, String right) {
        int li = 0;
        int ri = 0;
        while (li < left.length() && ri < right.length()) {
            int leftCode = left.codePointAt(li);
            int rightCode = right.codePointAt(ri);
            if (leftCode != rightCode) {
                return Integer.compare(leftCode, rightCode);
            }
            li += Character.charCount(leftCode);
            ri += Character.charCount(rightCode);
        }
        return Integer.compare(left.length() - li, right.length() - ri);
    }

    private Values() {
    }
}
