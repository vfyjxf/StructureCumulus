package dev.vfyjxf.cumulus.mc.capability;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One capability source bound to exactly one holder id; the context type is the capability's own {@code C}. */
@NullMarked
public interface CapabilityNestedExtentProvider<H, C extends @Nullable Object> {

    NestedExtentHolder<H> holder();

    @Nullable
    NestedExtent get(H actual, @Nullable C context);
}
