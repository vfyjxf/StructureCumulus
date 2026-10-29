package dev.vfyjxf.cumulus.mc.capability;

import net.neoforged.neoforge.capabilities.BaseCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** One capability lookup as a nested-extent provider; a null capability value is an absent extent. */
@NullMarked
public final class CapabilityNestedExtentProvider<H, C extends @Nullable Object> implements NestedExtentProvider<H> {

    private final NestedExtentHolder<H> holder;
    private final BaseCapability<NestedExtent, C> capability;
    private final NestedExtentAccess<H, C> access;

    private CapabilityNestedExtentProvider(
        NestedExtentHolder<H> holder,
        BaseCapability<NestedExtent, C> capability,
        NestedExtentAccess<H, C> access
    ) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.access = Objects.requireNonNull(access, "access");
    }

    public static <H, C extends @Nullable Object> CapabilityNestedExtentProvider<H, C> of(
        NestedExtentHolder<H> holder,
        BaseCapability<NestedExtent, C> capability,
        NestedExtentAccess<H, C> access
    ) {
        return new CapabilityNestedExtentProvider<>(holder, capability, access);
    }

    @Override
    public NestedExtentHolder<H> holder() {
        return holder;
    }

    @Override
    public @Nullable NestedExtent get(H actual) {
        return access.get(capability, actual);
    }
}
