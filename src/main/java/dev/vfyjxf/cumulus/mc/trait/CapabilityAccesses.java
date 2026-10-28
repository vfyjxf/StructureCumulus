package dev.vfyjxf.cumulus.mc.trait;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The block and entity capability get signatures as holder accessors. */
@NullMarked
public final class CapabilityAccesses {

    public static CapabilityAccess<BlockTraitContext, @Nullable Direction> block() {
        return new CapabilityAccess<>() {
            @Override
            public <T> @Nullable T get(
                BaseCapability<T, @Nullable Direction> capability,
                BlockTraitContext actual,
                TraitCaptureContext capture
            ) {
                @SuppressWarnings("unchecked")
                BlockCapability<T, @Nullable Direction> block = (BlockCapability<T, @Nullable Direction>) capability;
                return actual.level().getCapability(
                    block,
                    actual.pos(),
                    actual.state(),
                    actual.blockEntity(),
                    capture.capabilityContext()
                );
            }
        };
    }

    public static CapabilityAccess<EntityTraitContext, @Nullable Direction> entity() {
        return new CapabilityAccess<>() {
            @Override
            public <T> @Nullable T get(
                BaseCapability<T, @Nullable Direction> capability,
                EntityTraitContext actual,
                TraitCaptureContext capture
            ) {
                @SuppressWarnings("unchecked")
                EntityCapability<T, @Nullable Direction> entity = (EntityCapability<T, @Nullable Direction>) capability;
                return actual.entity().getCapability(entity, capture.capabilityContext());
            }
        };
    }

    public static CapabilityAccess<EntityTraitContext, @Nullable Void> entityVoid() {
        return new CapabilityAccess<>() {
            @Override
            public <T> @Nullable T get(
                BaseCapability<T, @Nullable Void> capability,
                EntityTraitContext actual,
                TraitCaptureContext capture
            ) {
                @SuppressWarnings("unchecked")
                EntityCapability<T, @Nullable Void> entity = (EntityCapability<T, @Nullable Void>) capability;
                return actual.entity().getCapability(entity, null);
            }
        };
    }

    private CapabilityAccesses() {}
}
