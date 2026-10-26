package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.jspecify.annotations.Nullable;
final class BuiltinFocuses {

    private interface Body {
        @Nullable Focus<?> create(Value args, FactoryContext ctx);
    }

    private record Factory(NamespacedId id, Body body) implements FocusFactory {
        @Override
        public @Nullable Focus<?> create(Value args, FactoryContext context) {
            try {
                return body.create(args, context);
            } catch (RuntimeException failure) {
                BuiltinSupport.report(context, Codes.FACTORY_BAD,
                        "factory " + id + " failed: " + failure.getMessage());
                return null;
            }
        }
    }

    static Catalog<FocusFactory> catalog() {
        Catalog.Builder<FocusFactory> builder = Catalog.builder(family());
        builder.put(id("occupant"), new Factory(id("occupant"), (args, ctx) -> {
            var cell = BuiltinSupport.cell(args, "cell", ctx);
            return cell == null ? null : Focuses.occupant(cell);
        }));
        builder.put(id("bay"), new Factory(id("bay"), (args, ctx) -> Focuses.bay()));
        builder.put(id("all_cells"), new Factory(id("all_cells"), (args, ctx) -> Focuses.allCells()));
        builder.put(id("entity"), new Factory(id("entity"), (args, ctx) -> {
            NamespacedId type = BuiltinSupport.id(args, "type", ctx);
            return type == null ? null : Focuses.entity(type);
        }));
        builder.put(id("nested"), new Factory(id("nested"), (args, ctx) -> {
            var cell = BuiltinSupport.cell(args, "cell", ctx);
            return cell == null ? null : Focuses.nested(cell);
        }));
        builder.put(id("door"), new Factory(id("door"), (args, ctx) -> {
            var cell = BuiltinSupport.cell(args, "origin", ctx);
            return cell == null ? null : Focuses.door(cell);
        }));
        builder.put(id("context"), new Factory(id("context"), (args, ctx) -> {
            NamespacedId key = BuiltinSupport.id(args, "key", ctx);
            if (key == null) {
                return null;
            }
            var resolved = ctx.keys().apply(key);
            if (resolved == null) {
                BuiltinSupport.report(ctx, Codes.KEY_UNDECLARED,
                        "context focus names undeclared key " + key);
                return null;
            }
            return Focuses.context(resolved);
        }));
        return builder.build();
    }

    private static NamespacedId family() {
        return NamespacedId.of("structurecumulus", "focuses");
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("structurecumulus", path);
    }

    private BuiltinFocuses() {
    }
}
