package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import org.jspecify.annotations.Nullable;

/** A data-driven leaf expectation constructor. */
public interface ExpectationFactory {

    NamespacedId id();

    @Nullable
    Expectation<?> create(Value args, FactoryContext context);
}
