package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The document form of the cells whose occupants prove a satellite is present. */
public record PresenceSpec(CuboidSet cells, @Nullable SourceSpan source) {

    public PresenceSpec {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a presence region needs at least one cell");
        }
    }

    public static PresenceSpec cell(LatticePoint cell) {
        Objects.requireNonNull(cell, "cell");
        return of(CuboidSet.of(Cuboid.of(cell)));
    }

    public static PresenceSpec of(CuboidSet cells) {
        return new PresenceSpec(cells, null);
    }

    public PresenceSpec withoutSpans() {
        return this.source == null ? this : new PresenceSpec(cells, null);
    }
}
