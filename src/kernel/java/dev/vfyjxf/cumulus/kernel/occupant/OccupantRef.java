package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Objects;

/**
 * The handle of one occupant; equality is identity within one instance, and the footprint is part of it.
 */
public record OccupantRef(InstanceId instance, NamespacedId type, OccupantKind kind,
                          LatticePoint anchor, Cuboid footprint) {

    public OccupantRef {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(footprint, "footprint");
    }
}
