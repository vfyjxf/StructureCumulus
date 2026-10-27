package dev.vfyjxf.cumulus.mc.trait;

import net.minecraft.core.Direction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The capture-wide capability context and typed host context. */
@NullMarked
public record TraitCaptureContext(@Nullable Direction capabilityContext, TraitContext hostContext) {

    public TraitCaptureContext {
        Objects.requireNonNull(hostContext, "hostContext");
    }

    public static TraitCaptureContext empty() {
        return new TraitCaptureContext(null, TraitContext.empty());
    }
}
