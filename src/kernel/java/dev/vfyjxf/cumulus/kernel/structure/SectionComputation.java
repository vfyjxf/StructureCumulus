package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Outcome;
/** A computation that runs once per section and contributes a partial value. */
public interface SectionComputation<T> extends Computation<T> {

    Outcome<T> compute(SectionContext ctx);
}
