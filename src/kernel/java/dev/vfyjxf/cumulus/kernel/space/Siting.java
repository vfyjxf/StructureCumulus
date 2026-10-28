package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Where a blueprint stands: an origin plus a placement. */
public record Siting(LatticePoint origin, Placement placement) {

    public Siting {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(placement, "placement");
    }

    public static Siting of(LatticePoint origin, Orientation orientation) {
        return new Siting(origin, new Placement(orientation, LatticePoint.origin));
    }

    public static Siting of(LatticePoint origin, Placement placement) {
        return new Siting(origin, placement);
    }

    public Siting withOrigin(LatticePoint origin) {
        return new Siting(origin, placement);
    }

    public Siting withPlacement(Placement placement) {
        return new Siting(origin, placement);
    }

    public @Nullable Siting compose(Siting relative) {
        Objects.requireNonNull(relative, "relative");
        LatticePoint origin = toWorld(relative.origin());
        if (origin == null) {
            return null;
        }
        Placement placement = this.placement.compose(relative.placement());
        return placement == null ? null : new Siting(origin, placement);
    }

    public @Nullable LatticePoint toWorld(LatticePoint local) {
        LatticePoint placed = placement.apply(local);
        if (placed == null) {
            return null;
        }
        return placed.offset(origin.x(), origin.y(), origin.z());
    }

    public @Nullable Cuboid toWorld(Cuboid local) {
        Cuboid placed = placement.apply(local);
        if (placed == null) {
            return null;
        }
        return placed.translate(origin.x(), origin.y(), origin.z());
    }

    public @Nullable CuboidSet toWorld(CuboidSet local) {
        CuboidSet placed = placement.apply(local);
        if (placed == null) {
            return null;
        }
        return placed.translate(origin.x(), origin.y(), origin.z());
    }

    public @Nullable LatticePoint toLocal(LatticePoint world) {
        LatticePoint translated = world.offset(-origin.x(), -origin.y(), -origin.z());
        if (translated == null) {
            return null;
        }
        Placement inverse = placement.inverse();
        return inverse == null ? null : inverse.apply(translated);
    }

    public @Nullable Cuboid toLocal(Cuboid world) {
        Cuboid translated = world.translate(-origin.x(), -origin.y(), -origin.z());
        if (translated == null) {
            return null;
        }
        Placement inverse = placement.inverse();
        return inverse == null ? null : inverse.apply(translated);
    }

    public @Nullable CuboidSet toLocal(CuboidSet world) {
        CuboidSet translated = world.translate(-origin.x(), -origin.y(), -origin.z());
        if (translated == null) {
            return null;
        }
        Placement inverse = placement.inverse();
        return inverse == null ? null : inverse.apply(translated);
    }
}
