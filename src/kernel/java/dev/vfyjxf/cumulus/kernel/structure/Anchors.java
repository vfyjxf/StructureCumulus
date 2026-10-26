package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
/** The one anchor read: one cell, one property, one mapping. */
public final class Anchors {

    public static SitingResult resolve(Blueprint blueprint, LatticePoint origin, WorldView view) {
        OrientationPolicy policy = blueprint.orientation();
        if (policy == null) {
            return failure(origin, Codes.ANCHOR_INVALID, "the blueprint has no orientation policy");
        }
        AnchorSpec spec = blueprint.anchor(policy.primaryRole());
        if (spec == null) {
            return failure(origin, Codes.ANCHOR_INVALID, "the primary role has no anchor");
        }
        OccupantSnapshot occupant = view.occupant(origin);
        if (occupant == null) {
            return failure(origin, Codes.UNREADABLE_CELL, "the anchor cell is unreadable");
        }
        if (occupant.isAir()) {
            return failure(origin, Codes.ANCHOR_INVALID, "the anchor cell is air");
        }
        String value = occupant.descriptor().property(spec.property());
        if (value == null) {
            return failure(origin, Codes.ANCHOR_INVALID,
                    "the anchor has no property " + spec.property());
        }
        @Nullable Heading heading = heading(value);
        if (heading == null) {
            return failure(origin, Codes.ANCHOR_INVALID,
                    "the property is not a heading: " + value);
        }
        Orientation orientation = policy.select(heading);
        if (orientation == null) {
            return failure(origin, Codes.ANCHOR_INVALID, "no allowed orientation faces " + heading);
        }
        return new SitingResult(Outcome.found(Siting.of(origin, orientation)),
                FindingBundle.empty());
    }

    private static SitingResult failure(LatticePoint origin, CodeEntry code,
                                        String message) {
        Locus locus = Locus.world(CuboidSet.of(Cuboid.of(origin)));
        Finding finding = Finding.builder(code).phase(Phase.COMPILE).message(message)
                .label(Label.primary(locus, message)).build();
        return new SitingResult(Outcome.missing(), FindingBundle.of(List.of(finding)));
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
