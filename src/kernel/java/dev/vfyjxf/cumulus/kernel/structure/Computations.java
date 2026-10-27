package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** The shipped section computations. */
public final class Computations {

    public static SectionComputation<Long> count(ContextKey<Long> output, CuboidSet region) {
        return new Builtin(output, region, Mode.count, null);
    }

    /**
     * Counts non-air occupants matching {@code predicate} whose anchor is in the section region.
     *
     * @throws IllegalArgumentException when the output has no merge or the region is empty
     */
    public static SectionComputation<Long> count(
        ContextKey<Long> output,
        CuboidSet region,
        Predicate<OccupantSnapshot> predicate
    ) {
        Objects.requireNonNull(predicate, "predicate");
        if (output.merge() == null) {
            throw new IllegalArgumentException("a count output needs a merge: " + output.id());
        }
        if (region.isEmpty()) {
            throw new IllegalArgumentException("a count region must not be empty");
        }
        return new Builtin(output, region, Mode.count, predicate);
    }

    public static SectionComputation<Long> volume(ContextKey<Long> output, CuboidSet region) {
        return new Builtin(output, region, Mode.volume, null);
    }

    public static SectionComputation<Long> air(ContextKey<Long> output, CuboidSet region) {
        return new Builtin(output, region, Mode.air, null);
    }

    private enum Mode {
        count, volume, air
    }

    private record Builtin(
        ContextKey<Long> output,
        CuboidSet region,
        Mode mode,
        @Nullable Predicate<OccupantSnapshot> predicate
    ) implements SectionComputation<Long> {

        private Builtin {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(mode, "mode");
            if (output.retention() != Retention.instance && output.retention() != Retention.match) {
                throw new IllegalArgumentException("unsupported retention");
            }
        }

        @Override
        public NamespacedId id() {
            return output.id();
        }

        @Override
        public ContextKey<Long> output() {
            return output;
        }

        @Override
        public Set<ContextKey<?>> inputs() {
            return Set.of();
        }

        @Override
        public CuboidSet region() {
            return region;
        }

        @Override
        public Outcome<Long> compute(SectionContext ctx) {
            return switch (mode) {
                case count -> countOccupants(ctx);
                case volume -> Outcome.found(ctx.cells().volume());
                case air -> countAir(ctx);
            };
        }

        private Outcome<Long> countOccupants(SectionContext ctx) {
            if (!ctx.unreadable().isEmpty()) {
                return refuse(ctx);
            }
            long occupants = 0;
            for (OccupantSnapshot occupant : ctx.occupants()) {
                if (occupant.isAir()) {
                    continue;
                }
                if (predicate != null && !predicate.test(occupant)) {
                    continue;
                }
                occupants++;
            }
            return Outcome.found(occupants);
        }

        private Outcome<Long> countAir(SectionContext ctx) {
            if (!ctx.unreadable().isEmpty()) {
                return refuse(ctx);
            }
            long air = 0;
            for (Cuboid box : ctx.cells().boxes()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            CellRead read = ctx.read(LatticePoint.of(x, y, z));
                            if (read instanceof CellRead.Found found && found.occupant().isAir()) {
                                air++;
                            }
                        }
                    }
                }
            }
            return Outcome.found(air);
        }

        private Outcome<Long> refuse(SectionContext ctx) {
            for (Finding finding : Check
                    .relayAll(ctx.phase(), ctx.unreadable(), Locus::local, "a required read is unavailable")
                    .findings()) {
                ctx.report(finding);
            }
            return Outcome.missing();
        }
    }

    private Computations() {}
}
