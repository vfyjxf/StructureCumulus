package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.structure.Computation;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import org.jspecify.annotations.Nullable;
final class BuiltinComputations {

    private record Factory(NamespacedId id, Mode mode) implements ComputationFactory {
        private enum Mode { COUNT, VOLUME, AIR }

        @Override
        @SuppressWarnings("unchecked")
        public @Nullable Computation<?> create(Value args, ContextKey<?> output, CuboidSet region,
                                              FactoryContext context) {
            if (!output.type().equals(Long.class)) {
                BuiltinSupport.report(context, Codes.KEY_TYPE,
                        "computation " + id + " needs a long output, got "
                                + output.type().getName());
                return null;
            }
            ContextKey<Long> key = (ContextKey<Long>) output;
            return switch (mode) {
                case COUNT -> Computations.count(key, region);
                case VOLUME -> Computations.volume(key, region);
                case AIR -> Computations.air(key, region);
            };
        }
    }

    static Catalog<ComputationFactory> catalog() {
        Catalog.Builder<ComputationFactory> builder = Catalog.builder(family());
        builder.put(id("count"), new Factory(id("count"), Factory.Mode.COUNT));
        builder.put(id("volume"), new Factory(id("volume"), Factory.Mode.VOLUME));
        builder.put(id("air"), new Factory(id("air"), Factory.Mode.AIR));
        return builder.build();
    }

    private static NamespacedId family() {
        return NamespacedId.of("structurecumulus", "computations");
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("structurecumulus", path);
    }

    private BuiltinComputations() {
    }
}
