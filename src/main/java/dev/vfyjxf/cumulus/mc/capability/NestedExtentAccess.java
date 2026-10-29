package dev.vfyjxf.cumulus.mc.capability;

import net.neoforged.neoforge.capabilities.BaseCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One holder's nested-extent capability lookup, generic over the capability context type. */
@NullMarked
@FunctionalInterface
public interface NestedExtentAccess<H, C extends @Nullable Object> {

    @Nullable
    NestedExtent get(BaseCapability<NestedExtent, C> capability, H actual);
}
