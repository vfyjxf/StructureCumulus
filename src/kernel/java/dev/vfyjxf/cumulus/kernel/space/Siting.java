package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

public record Siting(LatticePoint origin, Placement placement) {

    public static Siting of(LatticePoint origin, Orientation orientation) {
        return new Siting(origin, new Placement(orientation, LatticePoint.ORIGIN));
    }

    public static Siting of(LatticePoint origin, Placement placement) {
        return new Siting(origin, placement);
    }

    public static @Nullable Siting anchoring(LatticePoint target, LatticePoint local, Placement placement) {
        LatticePoint mapped = placement.apply(local);
        if (mapped == null) {
            return null;
        }
        LatticePoint origin = target.offset(-mapped.x(), -mapped.y(), -mapped.z());
        return origin == null ? null : new Siting(origin, placement);
    }

    public Siting withOrigin(LatticePoint origin) {
        return new Siting(origin, placement);
    }

    public Siting withPlacement(Placement placement) {
        return new Siting(origin, placement);
    }

    public @Nullable LatticePoint toWorld(LatticePoint local) {
        LatticePoint mapped = placement.apply(local);
        return mapped == null ? null : origin.offset(mapped.x(), mapped.y(), mapped.z());
    }

    public @Nullable LatticePoint toLocal(LatticePoint world) {
        LatticePoint fromOrigin = world.offset(-origin.x(), -origin.y(), -origin.z());
        if (fromOrigin == null) {
            return null;
        }
        LatticePoint fromOffset = fromOrigin.offset(
                -placement.offset().x(), -placement.offset().y(), -placement.offset().z());
        if (fromOffset == null) {
            return null;
        }
        return placement.orientation().inverse().apply(fromOffset);
    }
}
