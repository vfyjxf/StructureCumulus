package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

/** A data-driven computation constructor. */
public interface ComputationFactory {

    NamespacedId id();

    @Nullable
    Computation<?> create(Value args, ContextKey<?> output, CuboidSet region, FactoryContext context);
}
