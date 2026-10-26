package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** The immutable read model of one occupant. */
public record OccupantSnapshot(OccupantRef ref, OccupantDescriptor descriptor, Cuboid footprint,
                               List<Trait<?>> traits) {

    public static final NamespacedId AIR = NamespacedId.of("air");

    private static final Comparator<Trait<?>> TRAIT_ORDER =
            Comparator.comparing(trait -> trait.key().id());

    public OccupantSnapshot {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(traits, "traits");
        if (!ref.type().equals(descriptor.type()) || !ref.kind().equals(descriptor.kind())) {
            throw new IllegalArgumentException("ref and descriptor disagree");
        }
        if (footprint.isEmpty()) {
            throw new IllegalArgumentException("an occupant needs a footprint");
        }
        List<Trait<?>> sorted = new ArrayList<>(traits);
        sorted.sort(TRAIT_ORDER);
        for (int i = 0; i < sorted.size(); i++) {
            Objects.requireNonNull(sorted.get(i), "trait");
            if (i > 0 && sorted.get(i - 1).key().id().equals(sorted.get(i).key().id())) {
                throw new IllegalArgumentException("duplicate trait " + sorted.get(i).key().id());
            }
        }
        traits = List.copyOf(sorted);
    }

    public static OccupantSnapshot air(SnapshotId snapshot, int frame, LatticePoint cell) {
        OccupantRef ref = new OccupantRef(snapshot, frame, AIR, OccupantKind.BLOCK, cell);
        return new OccupantSnapshot(ref, new OccupantDescriptor(AIR, OccupantKind.BLOCK,
                Map.of(), List.of()), Cuboid.of(cell), List.of());
    }

    public boolean isAir() {
        return descriptor.type().equals(AIR);
    }

    public <T> @Nullable Trait<T> find(TraitKey<T> key) {
        for (Trait<?> trait : traits) {
            if (trait.key().equals(key)) {
                @SuppressWarnings("unchecked")
                Trait<T> found = (Trait<T>) trait;
                return found;
            }
        }
        return null;
    }

    public <T> @Nullable T trait(TraitKey<T> key) {
        Trait<T> trait = find(key);
        return trait == null ? null : trait.value();
    }

    public boolean has(TraitKey<?> key) {
        for (Trait<?> trait : traits) {
            if (trait.key().id().equals(key.id())) {
                return true;
            }
        }
        return false;
    }
}
