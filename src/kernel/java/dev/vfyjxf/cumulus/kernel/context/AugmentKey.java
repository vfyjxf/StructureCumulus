package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.Objects;

public record AugmentKey(NamespacedId id, AugmentFold fold) implements Comparable<AugmentKey> {

    public AugmentKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fold, "fold");
    }

    public static AugmentKey of(NamespacedId id, AugmentFold fold) {
        return new AugmentKey(id, fold);
    }

    @Override
    public int compareTo(AugmentKey other) {
        int byId = id.compareTo(other.id);
        return byId != 0 ? byId : Integer.compare(fold.ordinal(), other.fold.ordinal());
    }
}
