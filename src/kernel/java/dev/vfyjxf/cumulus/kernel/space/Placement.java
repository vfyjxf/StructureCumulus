package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** An orientation plus an integer offset. */
public record Placement(Orientation orientation, LatticePoint offset) {

    public static final Placement IDENTITY = new Placement(Orientation.IDENTITY, LatticePoint.ORIGIN);

    public Placement {
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(offset, "offset");
    }

    public @Nullable Placement compose(Placement other) {
        Objects.requireNonNull(other, "other");
        Orientation orientation = this.orientation.compose(other.orientation);
        LatticePoint rotated = this.orientation.apply(other.offset);
        if (rotated == null) {
            return null;
        }
        LatticePoint translated = rotated.offset(offset.x(), offset.y(), offset.z());
        return translated == null ? null : new Placement(orientation, translated);
    }

    public @Nullable Placement inverse() {
        LatticePoint rotated = orientation.inverse().apply(offset);
        if (rotated == null) {
            return null;
        }
        LatticePoint negated = negate(rotated);
        return negated == null ? null : new Placement(orientation.inverse(), negated);
    }

    public @Nullable LatticePoint apply(LatticePoint point) {
        LatticePoint rotated = orientation.apply(point);
        if (rotated == null) {
            return null;
        }
        return rotated.offset(offset.x(), offset.y(), offset.z());
    }

    public @Nullable Cuboid apply(Cuboid cuboid) {
        Cuboid rotated = orientation.apply(cuboid);
        if (rotated == null) {
            return null;
        }
        return rotated.translate(offset.x(), offset.y(), offset.z());
    }

    public @Nullable CuboidSet apply(CuboidSet cells) {
        CuboidSet rotated = orientation.apply(cells);
        if (rotated == null) {
            return null;
        }
        return rotated.translate(offset.x(), offset.y(), offset.z());
    }

    private static @Nullable LatticePoint negate(LatticePoint point) {
        if (point.x() == LatticePoint.MIN_XZ || point.y() == LatticePoint.MIN_Y
                || point.z() == LatticePoint.MIN_XZ) {
            return null;
        }
        return LatticePoint.of(-point.x(), -point.y(), -point.z());
    }
}
