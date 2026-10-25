package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A query filter; a {@code null} component does not constrain the result.
 */
public record OccupantFilter(@Nullable Predicate<NamespacedId> type,
                             @Nullable Predicate<OccupantKind> kind,
                             @Nullable Predicate<NamespacedId> trait) {

    public static OccupantFilter any() {
        return new OccupantFilter(null, null, null);
    }

    public static OccupantFilter ofType(NamespacedId type) {
        Objects.requireNonNull(type, "type");
        return new OccupantFilter(type::equals, null, null);
    }

    public static OccupantFilter ofKind(OccupantKind kind) {
        Objects.requireNonNull(kind, "kind");
        return new OccupantFilter(null, kind::equals, null);
    }

    public static OccupantFilter ofTrait(NamespacedId trait) {
        Objects.requireNonNull(trait, "trait");
        return new OccupantFilter(null, null, trait::equals);
    }

    public boolean matches(OccupantSnapshot occupant) {
        Objects.requireNonNull(occupant, "occupant");
        Predicate<NamespacedId> typeTest = type;
        if (typeTest != null && !typeTest.test(occupant.descriptor().type())) {
            return false;
        }
        Predicate<OccupantKind> kindTest = kind;
        if (kindTest != null && !kindTest.test(occupant.kind())) {
            return false;
        }
        Predicate<NamespacedId> traitTest = trait;
        return traitTest == null || occupant.traits().stream().anyMatch(traitTest);
    }
}
