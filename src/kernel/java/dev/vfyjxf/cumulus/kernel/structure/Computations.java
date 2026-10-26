package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.Objects;
import java.util.Set;

/** The three shipped section computations. */
public final class Computations {

    public static SectionComputation<Long> count(ContextKey<Long> output, CuboidSet region) {
        return new Builtin(output, region, Mode.COUNT);
    }

    public static SectionComputation<Long> volume(ContextKey<Long> output, CuboidSet region) {
        return new Builtin(output, region, Mode.VOLUME);
    }

    public static SectionComputation<Long> air(ContextKey<Long> output, CuboidSet region) {
        return new Builtin(output, region, Mode.AIR);
    }

    private enum Mode {
        COUNT, VOLUME, AIR
    }

    private record Builtin(ContextKey<Long> output, CuboidSet region, Mode mode) implements SectionComputation<Long> {

        private Builtin {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(mode, "mode");
            if (output.retention() != Retention.INSTANCE && output.retention() != Retention.MATCH) {
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
                case COUNT -> Outcome.found((long) ctx.occupants().size());
                case VOLUME -> Outcome.found(ctx.cells().volume());
                case AIR -> Outcome.found(countAir(ctx));
            };
        }

        private static long countAir(SectionContext ctx) {
            long air = 0;
            for (Cuboid box : ctx.cells().boxes()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            OccupantSnapshot occupant = ctx.occupantAt(LatticePoint.of(x, y, z));
                            if (occupant != null && occupant.isAir()) {
                                air++;
                            }
                        }
                    }
                }
            }
            return air;
        }
    }

    private Computations() {}
}
