package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * The frozen read model of one element; air is an ordinary snapshot of this type, never {@code null}.
 */
public record OccupantSnapshot(OccupantRef ref, OccupantKind kind, OccupantDescriptor descriptor,
                               Cuboid footprint, Set<NamespacedId> traits,
                               @Nullable NestedLevelSnapshot nested) {

    public static final NamespacedId AIR = NamespacedId.of("structurecumulus", "air");

    public OccupantSnapshot {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(footprint, "footprint");
        traits = OccupantDescriptor.immutableIds(traits);
        if (!kind.equals(ref.kind())) {
            throw new IllegalArgumentException("kind disagrees with the ref: " + kind.id() + " vs " + ref.kind().id());
        }
        if (!descriptor.type().equals(ref.type())) {
            throw new IllegalArgumentException("type disagrees with the ref: " + descriptor.type() + " vs " + ref.type());
        }
        if (footprint.isEmpty()) {
            throw new IllegalArgumentException("empty footprint for " + ref.type());
        }
        if (kind.equals(OccupantKind.NESTED) && nested == null) {
            throw new IllegalArgumentException("nested occupant without a child view: " + ref.type());
        }
    }

    public static OccupantSnapshot air(InstanceId instance, LatticePoint cell) {
        Objects.requireNonNull(cell, "cell");
        OccupantRef ref = new OccupantRef(instance, AIR, OccupantKind.BLOCK, cell, Cuboid.of(cell));
        return new OccupantSnapshot(ref, OccupantKind.BLOCK, OccupantDescriptor.of(AIR), ref.footprint(),
                Set.of(), null);
    }

    public boolean isAir() {
        return descriptor.type().equals(AIR);
    }

    public boolean hasTrait(NamespacedId trait) {
        return traits.contains(trait);
    }
}
