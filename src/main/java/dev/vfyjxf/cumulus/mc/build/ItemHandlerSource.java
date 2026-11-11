package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Any {@code IItemHandler} as a source: the container form of {@code /cumulus build with container}. */
public final class ItemHandlerSource implements ItemSource {

    private static final NamespacedId id = NamespacedId.of("structurecumulus", "item_handler");

    private final IItemHandler handler;

    public ItemHandlerSource(IItemHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /** The block's item handler at a position, or null when the block has none. */
    public static @Nullable ItemHandlerSource at(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        return handler == null ? null : new ItemHandlerSource(handler);
    }

    public IItemHandler handler() {
        return handler;
    }

    @Override
    public NamespacedId id() {
        return id;
    }

    @Override
    public int available(ItemStack template) {
        if (template.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (int slot = 0; slot < handler.getSlots() && total < Integer.MAX_VALUE; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    @Override
    public ItemStack extract(ItemStack template, int count, ExtractionMode mode) {
        if (template.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        int remaining = count;
        int taken = 0;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, mode.simulated());
            taken += extracted.getCount();
            remaining -= extracted.getCount();
        }
        return taken == 0 ? ItemStack.EMPTY : template.copyWithCount(taken);
    }

    @Override
    public ItemStack insert(ItemStack stack, ExtractionMode mode) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, mode.simulated());
        }
        return remainder;
    }
}
