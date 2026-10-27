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
public record OccupantSnapshot(
    OccupantRef ref,
    OccupantDescriptor descriptor,
    Cuboid footprint,
    List<TraitSlot> traits
) {

    public static final NamespacedId air = NamespacedId.of("air");

    private static final Comparator<TraitSlot> traitOrder = Comparator.comparing(TraitSlot::id);

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
        List<TraitSlot> sorted = new ArrayList<>(traits);
        for (TraitSlot slot : sorted) {
            Objects.requireNonNull(slot, "trait slot");
        }
        sorted.sort(traitOrder);
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).id().equals(sorted.get(i).id())) {
                throw new IllegalArgumentException("duplicate trait " + sorted.get(i).id());
            }
        }
        traits = List.copyOf(sorted);
    }

    public static OccupantSnapshot air(SnapshotId snapshot, int frame, LatticePoint cell) {
        OccupantRef ref = new OccupantRef(snapshot, frame, air, OccupantKind.block, cell);
        return new OccupantSnapshot(
            ref,
            new OccupantDescriptor(air, OccupantKind.block, Map.of(), List.of()),
            Cuboid.of(cell),
            List.of()
        );
    }

    public boolean isAir() {
        return descriptor.type().equals(air);
    }

    public @Nullable TraitSlot slot(NamespacedId id) {
        Objects.requireNonNull(id, "id");
        for (TraitSlot slot : traits) {
            if (slot.id().equals(id)) {
                return slot;
            }
        }
        return null;
    }

    public <T> @Nullable Trait<T> find(TraitKey<T> key) {
        Objects.requireNonNull(key, "key");
        TraitSlot slot = slot(key.id());
        if (!(slot instanceof TraitSlot.Present present)) {
            return null;
        }
        Object value = present.trait().value();
        if (value != null && !key.type().isInstance(value)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return new Trait<>(key, typed);
    }

    public <T> @Nullable T trait(TraitKey<T> key) {
        Trait<T> trait = find(key);
        return trait == null ? null : trait.value();
    }

    public boolean has(TraitKey<?> key) {
        Objects.requireNonNull(key, "key");
        return slot(key.id()) instanceof TraitSlot.Present;
    }
}
