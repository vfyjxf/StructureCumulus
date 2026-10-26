package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
/** A format-neutral data tree. Spans are provenance; {@link Values#contentEquals} ignores them. */
public sealed interface Value permits Value.ObjectValue, Value.ArrayValue, Value.StringValue,
        Value.NumberValue, Value.BooleanValue, Value.NullValue {

    @Nullable SourceSpan span();

    record ObjectValue(Map<String, Value> members, @Nullable SourceSpan span) implements Value {
        public ObjectValue {
            Objects.requireNonNull(members, "members");
            TreeMap<String, Value> sorted = new TreeMap<>(Values.codePointOrder());
            for (Map.Entry<String, Value> entry : members.entrySet()) {
                sorted.put(Objects.requireNonNull(entry.getKey(), "key"),
                        Objects.requireNonNull(entry.getValue(), "value"));
            }
            members = Collections.unmodifiableMap(sorted);
        }
    }

    record ArrayValue(List<Value> elements, @Nullable SourceSpan span) implements Value {
        public ArrayValue {
            Objects.requireNonNull(elements, "elements");
            List<Value> copy = new ArrayList<>(elements.size());
            for (Value element : elements) {
                copy.add(Objects.requireNonNull(element, "element"));
            }
            elements = List.copyOf(copy);
        }
    }

    record StringValue(String value, @Nullable SourceSpan span) implements Value {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }
    }

    record NumberValue(long value, @Nullable SourceSpan span) implements Value {
    }

    record BooleanValue(boolean value, @Nullable SourceSpan span) implements Value {
    }

    record NullValue(@Nullable SourceSpan span) implements Value {
    }

    static Value object(Map<String, Value> members) {
        return new ObjectValue(members, null);
    }

    static Value object() {
        return new ObjectValue(Map.of(), null);
    }

    static Value array(List<Value> elements) {
        return new ArrayValue(elements, null);
    }

    static Value array() {
        return new ArrayValue(List.of(), null);
    }

    static Value string(String value) {
        return new StringValue(value, null);
    }

    static Value number(long value) {
        return new NumberValue(value, null);
    }

    static Value bool(boolean value) {
        return new BooleanValue(value, null);
    }

    static Value nil() {
        return new NullValue(null);
    }

    static Value withSpan(Value value, SourceSpan span) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        return switch (value) {
            case ObjectValue node -> new ObjectValue(node.members(), span);
            case ArrayValue node -> new ArrayValue(node.elements(), span);
            case StringValue node -> new StringValue(node.value(), span);
            case NumberValue node -> new NumberValue(node.value(), span);
            case BooleanValue node -> new BooleanValue(node.value(), span);
            case NullValue node -> new NullValue(span);
        };
    }

    default boolean isObject() {
        return this instanceof ObjectValue;
    }

    default boolean isArray() {
        return this instanceof ArrayValue;
    }

    default boolean isString() {
        return this instanceof StringValue;
    }

    default boolean isNumber() {
        return this instanceof NumberValue;
    }

    default boolean isBoolean() {
        return this instanceof BooleanValue;
    }

    default boolean isNull() {
        return this instanceof NullValue;
    }

    default @Nullable Value get(String member) {
        return this instanceof ObjectValue object ? object.members().get(member) : null;
    }

    default List<Value> elements() {
        return this instanceof ArrayValue array ? array.elements() : List.of();
    }

    default Map<String, Value> members() {
        return this instanceof ObjectValue object ? object.members() : Map.of();
    }

    default String asString(String fallback) {
        return this instanceof StringValue string ? string.value() : fallback;
    }

    default long asNumber(long fallback) {
        return this instanceof NumberValue number ? number.value() : fallback;
    }

    default boolean asBoolean(boolean fallback) {
        return this instanceof BooleanValue bool ? bool.value() : fallback;
    }
}
