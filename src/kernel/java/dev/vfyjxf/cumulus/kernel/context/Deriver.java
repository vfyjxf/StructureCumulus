package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Value;

import java.util.Set;

/** A context-only producer of one value. It never reads a world. */
public interface Deriver<T> {

    ContextKey<T> output();

    Set<ContextKey<?>> requires();

    Outcome<T> derive(DeriveContext ctx);

    default Outcome<T> derive(DeriveContext ctx, Value arguments) {
        return derive(ctx);
    }
}
