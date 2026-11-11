package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.world.item.ItemStack;

/**
 * Where a build's blocks come from. The dry run simulates every extraction, the commit
 * executes the same extractions, and a rollback inserts what the commit consumed.
 */
public interface ItemSource {

    NamespacedId id();

    /** The template-matched count this source can still give. */
    int available(ItemStack template);

    /**
     * Takes up to {@code count} template-matching items. The answer carries the taken count; it need
     * not be an inventory-legal stack.
     */
    ItemStack extract(ItemStack template, int count, ExtractionMode mode);

    /** Puts items back and answers the remainder that did not fit; an empty stack means fully returned. */
    ItemStack insert(ItemStack stack, ExtractionMode mode);
}
