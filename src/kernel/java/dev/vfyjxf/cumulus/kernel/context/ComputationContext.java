package dev.vfyjxf.cumulus.kernel.context;

import org.jspecify.annotations.Nullable;

public interface ComputationContext extends AggregateContext {

    @Nullable SectionContext section();
}
