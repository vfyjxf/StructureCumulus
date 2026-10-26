package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import org.jspecify.annotations.Nullable;
final class BuiltinSupport {

    static @Nullable NamespacedId id(Value args, String key, FactoryContext ctx) {
        Value value = args.get(key);
        String text = value == null ? null : value.asString(null);
        NamespacedId id = NamespacedId.parse(text);
        if (id == null) {
            report(ctx, Codes.DOCUMENT_FIELD, "expected an id at " + key);
        }
        return id;
    }

    static @Nullable LatticePoint cell(Value args, String key, FactoryContext ctx) {
        Value value = args.get(key);
        if (value == null || !value.isArray() || value.elements().size() != 3) {
            report(ctx, Codes.DOCUMENT_FIELD, "expected a cell at " + key);
            return null;
        }
        long x = value.elements().get(0).asNumber(Long.MIN_VALUE);
        long y = value.elements().get(1).asNumber(Long.MIN_VALUE);
        long z = value.elements().get(2).asNumber(Long.MIN_VALUE);
        if (x == Long.MIN_VALUE || y == Long.MIN_VALUE || z == Long.MIN_VALUE
                || x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE
                || !LatticePoint.inLattice((int) x, (int) y, (int) z)) {
            report(ctx, Codes.CUBOID_OFF_LATTICE, "cell out of the lattice at " + key);
            return null;
        }
        return LatticePoint.of((int) x, (int) y, (int) z);
    }

    static @Nullable String string(Value args, String key, FactoryContext ctx) {
        Value value = args.get(key);
        String text = value == null ? null : value.asString(null);
        if (text == null) {
            report(ctx, Codes.DOCUMENT_FIELD, "expected a string at " + key);
        }
        return text;
    }

    static void report(FactoryContext ctx, CodeEntry entry,
                       String message) {
        Finding.Builder builder = Finding.builder(entry).phase(Phase.COMPILE).message(message);
        if (ctx.source() != null) {
            Locus locus = Locus.definition(ctx.source());
            builder.label(Label.primary(locus, message));
        }
        ctx.report().accept(builder.build());
    }

    private BuiltinSupport() {
    }
}
