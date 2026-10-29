package dev.vfyjxf.cumulus.mc.capability;

import net.neoforged.neoforge.capabilities.BaseCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Binds one capability and one access into the provider the lookup stores. */
@NullMarked
public final class CapabilityNestedExtentProviders {

    public static <H, C extends @Nullable Object> CapabilityNestedExtentProvider<H, C> of(
        NestedExtentHolder<H> holder,
        BaseCapability<NestedExtent, C> capability,
        CapabilityNestedExtentAccess<H, C> access
    ) {
        return new Bound<>(holder, capability, access);
    }

    private record Bound<H, C extends @Nullable Object>(
        NestedExtentHolder<H> holder,
        BaseCapability<NestedExtent, C> capability,
        CapabilityNestedExtentAccess<H, C> access
    ) implements CapabilityNestedExtentProvider<H, C> {

        private Bound {
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(access, "access");
        }

        @Override
        public @Nullable NestedExtent get(H actual, @Nullable C context) {
            return access.get(capability, actual, context);
        }
    }

    private CapabilityNestedExtentProviders() {}
}
