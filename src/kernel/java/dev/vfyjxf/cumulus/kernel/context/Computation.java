package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

import java.util.Set;

public interface Computation<T> {

    NamespacedId id();

    Stage stage();

    StatePhase phase();

    ContextKey<T> output();

    Set<ContextKey<?>> inputs();

    CuboidSet region();

    Outcome<T> compute(ComputationContext ctx);
}
