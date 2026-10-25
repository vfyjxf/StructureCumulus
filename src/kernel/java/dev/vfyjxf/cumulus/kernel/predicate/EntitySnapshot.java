package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;

import java.util.Objects;
import java.util.UUID;

/** One entity as the snapshot bound it: the authored id, the occupant it resolved to, nothing derived. */
public record EntitySnapshot(UUID id, OccupantSnapshot occupant) {

    public EntitySnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occupant, "occupant");
    }

    public OccupantRef ref() {
        return occupant.ref();
    }

    public NamespacedId type() {
        return occupant.descriptor().type();
    }
}
