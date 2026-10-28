package dev.vfyjxf.cumulus.mc.trait;

import net.neoforged.neoforge.capabilities.BaseCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One holder's capability lookup, generic over the capability value type. */
@NullMarked
@FunctionalInterface
public interface CapabilityAccess<H, C extends @Nullable Object> {

    <T> @Nullable T get(BaseCapability<T, C> capability, H actual, TraitCaptureContext capture);
}
