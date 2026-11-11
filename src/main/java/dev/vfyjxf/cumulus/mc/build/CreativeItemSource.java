package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.world.item.ItemStack;

/** An unlimited source: it always answers a full extraction and accepts every inserted stack. */
public final class CreativeItemSource implements ItemSource {

    private static final NamespacedId id = NamespacedId.of("structurecumulus", "creative");

    @Override
    public NamespacedId id() {
        return id;
    }

    @Override
    public int available(ItemStack template) {
        return template.isEmpty() ? 0 : Integer.MAX_VALUE;
    }

    @Override
    public ItemStack extract(ItemStack template, int count, ExtractionMode mode) {
        return template.isEmpty() || count <= 0 ? ItemStack.EMPTY : template.copyWithCount(count);
    }

    @Override
    public ItemStack insert(ItemStack stack, ExtractionMode mode) {
        return ItemStack.EMPTY;
    }
}
