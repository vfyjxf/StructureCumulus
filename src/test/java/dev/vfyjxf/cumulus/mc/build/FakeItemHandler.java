package dev.vfyjxf.cumulus.mc.build;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/** A slot list with the handler's insertion and extraction laws, for the item source tests. */
final class FakeItemHandler implements IItemHandler {

    private final List<ItemStack> slots;
    private final int limit;

    FakeItemHandler(int slots, int limit, ItemStack... initial) {
        this.limit = limit;
        this.slots = new ArrayList<>(slots);
        for (int index = 0; index < slots; index++) {
            this.slots.add(index < initial.length ? initial[index].copy() : ItemStack.EMPTY);
        }
    }

    @Override
    public int getSlots() {
        return slots.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slots.get(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }
        ItemStack current = slots.get(slot);
        int room = limit - current.getCount();
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }
        int inserted = Math.min(room, stack.getCount());
        if (inserted <= 0) {
            return stack;
        }
        if (!simulate) {
            if (current.isEmpty()) {
                slots.set(slot, stack.copyWithCount(inserted));
            } else {
                current.setCount(current.getCount() + inserted);
            }
        }
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack current = slots.get(slot);
        if (current.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, current.getCount());
        ItemStack result = current.copyWithCount(taken);
        if (!simulate) {
            slots.set(
                slot,
                current.getCount() == taken ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - taken)
            );
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return limit;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
