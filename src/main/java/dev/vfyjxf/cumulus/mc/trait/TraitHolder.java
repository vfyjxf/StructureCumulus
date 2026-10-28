package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** Typed identity of the subject a provider reads; the type is a token, never a {@code Class<?>}. */
@NullMarked
public record TraitHolder<H>(NamespacedId id) implements Comparable<TraitHolder<?>> {

    public TraitHolder {
        Objects.requireNonNull(id, "id");
    }

    public static <H> TraitHolder<H> of(NamespacedId id) {
        return new TraitHolder<>(id);
    }

    @Override
    public int compareTo(TraitHolder<?> other) {
        return id.compareTo(other.id);
    }
}
