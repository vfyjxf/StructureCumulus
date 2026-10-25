package dev.vfyjxf.cumulus.kernel.context;

import org.jspecify.annotations.Nullable;

import java.util.Set;

public interface Deriver<T> {

    ContextKey<T> output();

    Set<ContextKey<?>> requires();

    ContextTier tier();

    @Nullable T derive(DeriveContext ctx);
}
