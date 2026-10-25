package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

public record Placement(Orientation orientation, LatticePoint offset) {

    public static final Placement IDENTITY = new Placement(Orientation.IDENTITY, LatticePoint.ORIGIN);

    public @Nullable LatticePoint apply(LatticePoint local) {
        LatticePoint rotated = orientation.apply(local);
        return rotated == null ? null : rotated.offset(offset.x(), offset.y(), offset.z());
    }

    /** {@code other} first, then this placement. */
    public @Nullable Placement compose(Placement other) {
        LatticePoint shifted = orientation.apply(other.offset);
        if (shifted == null) {
            return null;
        }
        LatticePoint summed = shifted.offset(offset.x(), offset.y(), offset.z());
        if (summed == null) {
            return null;
        }
        return new Placement(orientation.compose(other.orientation), summed);
    }

    public @Nullable Placement inverse() {
        Orientation inverse = orientation.inverse();
        LatticePoint rotated = inverse.apply(offset);
        if (rotated == null) {
            return null;
        }
        LatticePoint negated = negate(rotated);
        return negated == null ? null : new Placement(inverse, negated);
    }

    private static @Nullable LatticePoint negate(LatticePoint point) {
        int x = -point.x();
        int y = -point.y();
        int z = -point.z();
        return LatticePoint.inLattice(x, y, z) ? LatticePoint.of(x, y, z) : null;
    }
}
