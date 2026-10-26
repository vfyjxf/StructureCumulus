package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
/** The one anchor read: one cell, one property, one mapping. */
public final class Anchors {

    public static Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view) {
        OrientationPolicy policy = blueprint.orientation();
        if (policy == null) {
            return Outcome.missing();
        }
        AnchorSpec spec = blueprint.anchor(policy.primaryRole());
        if (spec == null) {
            return Outcome.missing();
        }
        OccupantSnapshot occupant = view.occupant(origin);
        if (occupant == null || occupant.isAir()) {
            return Outcome.missing();
        }
        String value = occupant.descriptor().property(spec.property());
        if (value == null) {
            return Outcome.missing();
        }
        @Nullable Heading heading = heading(value);
        if (heading == null) {
            return Outcome.missing();
        }
        Orientation orientation = policy.select(heading);
        if (orientation == null) {
            return Outcome.missing();
        }
        return Outcome.found(Siting.of(origin, orientation));
    }

    private static @Nullable Heading heading(String value) {
        try {
            return Heading.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException rejected) {
            return null;
        }
    }

    private Anchors() {
    }
}
