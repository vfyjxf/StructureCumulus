package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** Typed identity of the subject a nested-extent provider reads; the type is a token, never a {@code Class<?>}. */
@NullMarked
public record NestedExtentHolder<H>(NamespacedId id) implements Comparable<NestedExtentHolder<?>> {

    public NestedExtentHolder {
        Objects.requireNonNull(id, "id");
    }

    public static <H> NestedExtentHolder<H> of(NamespacedId id) {
        return new NestedExtentHolder<>(id);
    }

    @Override
    public int compareTo(NestedExtentHolder<?> other) {
        return id.compareTo(other.id);
    }
}
