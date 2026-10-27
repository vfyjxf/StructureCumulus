package dev.vfyjxf.cumulus.mc.trait;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** The immutable input of one entity trait read. */
@NullMarked
public record EntityTraitContext(Entity entity, TraitCaptureContext capture) {

    public EntityTraitContext {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(capture, "capture");
    }
}
