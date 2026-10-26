package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** The allowed orientations and the one role that supplies a heading. */
public record OrientationPolicy(NamespacedId primaryRole, List<Orientation> allowed, boolean uprightOnly) {

    public OrientationPolicy {
        Objects.requireNonNull(primaryRole, "primaryRole");
        Objects.requireNonNull(allowed, "allowed");
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("allowed must not be empty");
        }
        List<Orientation> sorted = new ArrayList<>(new TreeSet<>(allowed));
        if (sorted.size() != allowed.size()) {
            throw new IllegalArgumentException("allowed must not contain duplicates");
        }
        allowed = List.copyOf(sorted);
        if (uprightOnly) {
            for (Orientation orientation : allowed) {
                if (orientation.apply(Heading.up) != Heading.up) {
                    throw new IllegalArgumentException("uprightOnly promises every allowed maps UP to UP");
                }
            }
        }
    }

    public static Builder builder(NamespacedId primaryRole) {
        return new Builder(primaryRole);
    }

    public @Nullable Orientation select(Heading anchorHeading) {
        for (Orientation orientation : allowed) {
            if (orientation.apply(Heading.north) == anchorHeading) {
                return orientation;
            }
        }
        return null;
    }

    public static final class Builder {

        private final NamespacedId primaryRole;
        private final List<Orientation> allowed = new ArrayList<>();
        private boolean uprightOnly;

        private Builder(NamespacedId primaryRole) {
            this.primaryRole = Objects.requireNonNull(primaryRole, "primaryRole");
        }

        public Builder allow(Orientation... orientations) {
            for (Orientation orientation : orientations) {
                allowed.add(Objects.requireNonNull(orientation, "orientation"));
            }
            return this;
        }

        public Builder allowTurns(Turn... turns) {
            for (Turn turn : turns) {
                allowed.add(Orientation.of(turn, Tilt.level, Flip.none));
            }
            return this;
        }

        public Builder uprightOnly() {
            uprightOnly = true;
            return this;
        }

        public OrientationPolicy build() {
            return new OrientationPolicy(primaryRole, allowed, uprightOnly);
        }
    }
}
