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
import dev.vfyjxf.cumulus.kernel.world.CellRead;
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
            return failure(origin, Codes.anchorInvalid, "the blueprint has no orientation policy");
        }
        AnchorSpec spec = blueprint.anchor(policy.primaryRole());
        if (spec == null) {
            return failure(origin, Codes.anchorInvalid, "the primary role has no anchor");
        }
        CellRead read = view.read(origin);
        if (read instanceof CellRead.Unreadable unreadable) {
            Locus locus = Locus.world(CuboidSet.of(Cuboid.of(origin)));
            Finding finding = Check.relay(Phase.compile, unreadable.refusal(), locus, "the anchor cell is unreadable");
            return new SitingResult(Outcome.missing(), FindingBundle.of(List.of(finding)));
        }
        OccupantSnapshot occupant = ((CellRead.Found) read).occupant();
        if (occupant.isAir()) {
            return failure(origin, Codes.anchorInvalid, "the anchor cell is air");
        }
        String value = occupant.descriptor().property(spec.property());
        if (value == null) {
            return failure(origin, Codes.anchorInvalid, "the anchor has no property " + spec.property());
        }
        @Nullable
        Heading heading = heading(value);
        if (heading == null) {
            return failure(origin, Codes.anchorInvalid, "the property is not a heading: " + value);
        }
        Orientation orientation = policy.select(heading);
        if (orientation == null) {
            return failure(origin, Codes.anchorInvalid, "no allowed orientation faces " + heading);
        }
        return new SitingResult(Outcome.found(Siting.of(origin, orientation)), FindingBundle.empty());
    }

    private static SitingResult failure(LatticePoint origin, CodeEntry code, String message) {
        Locus locus = Locus.world(CuboidSet.of(Cuboid.of(origin)));
        Finding finding = Finding.builder(code).phase(Phase.compile).message(message)
                .label(Label.primary(locus, message)).build();
        return new SitingResult(Outcome.missing(), FindingBundle.of(List.of(finding)));
    }

    private static @Nullable Heading heading(String value) {
        try {
            return Heading.valueOf(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException rejected) {
            return null;
        }
    }

    private Anchors() {}
}
