package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

import java.util.Set;
/** A declared producer of one context key. */
public interface Computation<T> {

    NamespacedId id();

    ContextKey<T> output();

    Set<ContextKey<?>> inputs();

    CuboidSet region();
}
