package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** A rectangular region tested atomically, with its coverage, ownership and sharing. */
public record Bay(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                  Sharing sharing, @Nullable String description) {

    public Bay {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(sharing, "sharing");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a bay needs at least one cell");
        }
    }

    public static Bay of(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                         Sharing sharing) {
        return new Bay(id, CuboidSet.of(cells), coverage, footprint, sharing, null);
    }

    public static Bay of(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                         Sharing sharing) {
        return new Bay(id, cells, coverage, footprint, sharing, null);
    }

    public static Bay of(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                         Sharing sharing, String description) {
        return new Bay(id, cells, coverage, footprint, sharing, description);
    }
}
