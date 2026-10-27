package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import org.jspecify.annotations.Nullable;

final class BuiltinExpectations {

    private interface Body {
        @Nullable
        Expectation<?> create(Value args, FactoryContext ctx);
    }

    private record Factory(NamespacedId id, Body body) implements ExpectationFactory {
        @Override
        public @Nullable Expectation<?> create(Value args, FactoryContext context) {
            try {
                return body.create(args, context);
            } catch (RuntimeException failure) {
                BuiltinSupport.report(context, Codes.factoryBad, "factory " + id + " failed: " + failure.getMessage());
                return null;
            }
        }
    }

    static Catalog<ExpectationFactory> catalog() {
        Catalog.Builder<ExpectationFactory> builder = Catalog.builder(family());
        builder.put(id("block"), new Factory(id("block"), (args, ctx) -> {
            NamespacedId type = BuiltinSupport.id(args, "id", ctx);
            return type == null ? null : Expectations.block(type);
        }));
        builder.put(id("air"), new Factory(id("air"), (args, ctx) -> Expectations.air()));
        builder.put(id("property"), new Factory(id("property"), (args, ctx) -> {
            String key = BuiltinSupport.string(args, "key", ctx);
            String value = BuiltinSupport.string(args, "value", ctx);
            return key == null || value == null ? null : Expectations.property(key, value);
        }));
        builder.put(id("trait"), new Factory(id("trait"), (args, ctx) -> {
            NamespacedId type = BuiltinSupport.id(args, "id", ctx);
            return type == null ? null : Expectations.trait(new TraitKey<Object>(type, Object.class));
        }));
        builder.put(id("trait_absent"), new Factory(id("trait_absent"), (args, ctx) -> {
            NamespacedId type = BuiltinSupport.id(args, "id", ctx);
            return type == null ? null : Expectations.traitAbsent(new TraitKey<Object>(type, Object.class));
        }));
        builder.put(id("any_occupant"), new Factory(id("any_occupant"), (args, ctx) -> Expectations.anyOccupant()));
        return builder.build();
    }

    private static NamespacedId family() {
        return NamespacedId.of("structurecumulus", "expectations");
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("structurecumulus", path);
    }

    private BuiltinExpectations() {}
}
