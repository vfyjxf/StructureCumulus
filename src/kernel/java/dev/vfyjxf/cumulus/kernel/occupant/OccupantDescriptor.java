package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A version-independent description: ids and strings only, never a state id or a class name.
 */
public record OccupantDescriptor(NamespacedId type, Set<NamespacedId> traits) {

    private static final Comparator<NamespacedId> ID_ORDER =
            Comparator.comparing(NamespacedId::namespace).thenComparing(NamespacedId::path);

    public OccupantDescriptor {
        Objects.requireNonNull(type, "type");
        traits = immutableIds(traits);
    }

    public static OccupantDescriptor of(NamespacedId type) {
        return new OccupantDescriptor(type, Set.of());
    }

    public boolean hasTrait(NamespacedId trait) {
        return traits.contains(trait);
    }

    static Set<NamespacedId> immutableIds(Set<NamespacedId> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<NamespacedId> ordered = new ArrayList<>(ids.size());
        for (NamespacedId id : ids) {
            ordered.add(Objects.requireNonNull(id, "id"));
        }
        ordered.sort(ID_ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }
}
