package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/** Optional type/kind/trait predicates for a box query. */
public record OccupantFilter(
    @Nullable Predicate<NamespacedId> type,
    @Nullable Predicate<OccupantKind> kind,
    @Nullable Predicate<NamespacedId> trait
) {

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
        return new OccupantFilter(null, null, id -> id.equals(trait));
    }

    public boolean matches(OccupantSnapshot occupant) {
        if (type != null && !type.test(occupant.descriptor().type())) {
            return false;
        }
        if (kind != null && !kind.test(occupant.descriptor().kind())) {
            return false;
        }
        if (trait != null) {
            for (TraitSlot slot : occupant.traits()) {
                if (slot instanceof TraitSlot.Present present && trait.test(present.trait().key().id())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
