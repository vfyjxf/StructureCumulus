package dev.vfyjxf.cumulus.mc.build;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** One stack and how many of it an edit needs. */
public record ItemRequirement(ItemStack template, int count) {

    public ItemRequirement {
        Objects.requireNonNull(template, "template");
        if (template.isEmpty() || count < 1) {
            throw new IllegalArgumentException("an item requirement needs a non-empty template and count >= 1");
        }
    }
}
