package dev.vfyjxf.cumulus.mc.capability;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One typed nested-extent provider for exactly one holder id. */
@NullMarked
public interface NestedExtentProvider<H> {

    NestedExtentHolder<H> holder();

    @Nullable
    NestedExtent get(H actual);
}
