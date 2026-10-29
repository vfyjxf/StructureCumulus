package dev.vfyjxf.cumulus.mc.capability;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The block and entity capability get signatures as nested-extent accessors. */
@NullMarked
public final class NestedExtentAccesses {

    public static NestedExtentAccess<BlockNestedExtentContext, @Nullable Void> block() {
        return new NestedExtentAccess<>() {
            @Override
            public @Nullable NestedExtent get(
                BaseCapability<NestedExtent, @Nullable Void> capability,
                BlockNestedExtentContext actual
            ) {
                @SuppressWarnings("unchecked")
                BlockCapability<NestedExtent, @Nullable Void> block = (BlockCapability<NestedExtent, @Nullable Void>) capability;
                return actual.level().getCapability(block, actual.pos(), actual.state(), actual.blockEntity());
            }
        };
    }

    public static NestedExtentAccess<Entity, @Nullable Void> entity() {
        return new NestedExtentAccess<>() {
            @Override
            public @Nullable NestedExtent get(BaseCapability<NestedExtent, @Nullable Void> capability, Entity actual) {
                @SuppressWarnings("unchecked")
                EntityCapability<NestedExtent, @Nullable Void> entity = (EntityCapability<NestedExtent, @Nullable Void>) capability;
                return actual.getCapability(entity, null);
            }
        };
    }

    private NestedExtentAccesses() {}
}
