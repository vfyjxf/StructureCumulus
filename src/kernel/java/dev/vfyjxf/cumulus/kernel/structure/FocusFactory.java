package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import org.jspecify.annotations.Nullable;

/** A data-driven focus constructor. */
public interface FocusFactory {

    NamespacedId id();

    @Nullable
    Focus<?> create(Value args, FactoryContext context);
}
