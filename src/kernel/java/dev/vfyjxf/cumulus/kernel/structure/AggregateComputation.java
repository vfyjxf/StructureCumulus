package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Outcome;
/** A computation that runs once after the fold. */
public interface AggregateComputation<T> extends Computation<T> {

    Outcome<T> compute(AggregateContext ctx);
}
