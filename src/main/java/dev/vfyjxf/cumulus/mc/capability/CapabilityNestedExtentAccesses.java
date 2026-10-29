package dev.vfyjxf.cumulus.mc.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The block and entity capability get signatures as nested-extent accessors. */
@NullMarked
public final class CapabilityNestedExtentAccesses {

    public static CapabilityNestedExtentAccess<BlockNestedExtentContext, @Nullable Direction> block() {
        return new CapabilityNestedExtentAccess<>() {
            @Override
            public @Nullable NestedExtent get(
                BaseCapability<NestedExtent, @Nullable Direction> capability,
                BlockNestedExtentContext actual,
                @Nullable Direction context
            ) {
                @SuppressWarnings("unchecked")
                BlockCapability<NestedExtent, @Nullable Direction> block = (BlockCapability<NestedExtent, @Nullable Direction>) capability;
                return actual.level().getCapability(block, actual.pos(), actual.state(), actual.blockEntity(), context);
            }
        };
    }

    public static CapabilityNestedExtentAccess<Entity, @Nullable Direction> entity() {
        return new CapabilityNestedExtentAccess<>() {
            @Override
            public @Nullable NestedExtent get(
                BaseCapability<NestedExtent, @Nullable Direction> capability,
                Entity actual,
                @Nullable Direction context
            ) {
                @SuppressWarnings("unchecked")
                EntityCapability<NestedExtent, @Nullable Direction> entity = (EntityCapability<NestedExtent, @Nullable Direction>) capability;
                return actual.getCapability(entity, context);
            }
        };
    }

    public static CapabilityNestedExtentAccess<Entity, @Nullable Void> entityVoid() {
        return new CapabilityNestedExtentAccess<>() {
            @Override
            public @Nullable NestedExtent get(
                BaseCapability<NestedExtent, @Nullable Void> capability,
                Entity actual,
                @Nullable Void context
            ) {
                @SuppressWarnings("unchecked")
                EntityCapability<NestedExtent, @Nullable Void> entity = (EntityCapability<NestedExtent, @Nullable Void>) capability;
                return actual.getCapability(entity, null);
            }
        };
    }

    private CapabilityNestedExtentAccesses() {}
}
