package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.Objects;

/** One provider declaration: a nested structure's type, absolute bounds and child-local origin. */
public record NestedExtent(NamespacedId type, Cuboid bounds, LatticePoint origin) {

    public NestedExtent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(origin, "origin");
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("a nested extent needs non-empty bounds");
        }
        if (type.equals(OccupantSnapshot.air)) {
            throw new IllegalArgumentException("the reserved air type cannot name a nested extent");
        }
        if (bounds.translate(-origin.x(), -origin.y(), -origin.z()) == null) {
            throw new IllegalArgumentException("the local translation leaves the lattice");
        }
    }
}
