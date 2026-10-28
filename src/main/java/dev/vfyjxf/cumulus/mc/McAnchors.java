package dev.vfyjxf.cumulus.mc;

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
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Resolves the anchor siting from the live anchor cell. */
@NullMarked
public final class McAnchors {

    public static SitingResult resolve(Blueprint blueprint, LatticePoint origin, ServerLevel level) {
        OrientationPolicy policy = blueprint.orientation();
        if (policy == null) {
            return failure(origin, Codes.anchorInvalid, "the blueprint has no orientation policy");
        }
        AnchorSpec spec = blueprint.anchor(policy.primaryRole());
        if (spec == null) {
            return failure(origin, Codes.anchorInvalid, "the primary role has no anchor");
        }
        BlockPos position = new BlockPos(origin.x(), origin.y(), origin.z());
        if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
            return failure(origin, AdapterCodes.notCapturedEntry, "the anchor chunk is not loaded");
        }
        BlockState state = level.getBlockState(position);
        if (state.isAir()) {
            return failure(origin, Codes.anchorInvalid, "the anchor cell is air");
        }
        String value = propertyValue(state, spec.property());
        if (value == null) {
            return failure(origin, Codes.anchorInvalid, "the anchor has no property " + spec.property());
        }
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

    private static @Nullable String propertyValue(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return valueName(state, property);
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static @Nullable Heading heading(String value) {
        try {
            return Heading.valueOf(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException rejected) {
            return null;
        }
    }

    private static SitingResult failure(LatticePoint origin, CodeEntry code, String message) {
        Locus locus = Locus.world(CuboidSet.of(Cuboid.of(origin)));
        Finding finding = Finding.builder(code).phase(Phase.compile).message(message)
                .label(Label.primary(locus, message)).build();
        return new SitingResult(Outcome.missing(), FindingBundle.of(List.of(finding)));
    }

    private McAnchors() {}
}
