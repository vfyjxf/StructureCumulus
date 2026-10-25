package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.Outcome;

public interface DeriveContext {

    <T> Outcome<T> read(ContextKey<T> key);

    ContextView context();
}
