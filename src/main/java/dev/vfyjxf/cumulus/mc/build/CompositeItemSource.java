package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/** An ordered aggregate: extraction drains earlier sources first, insertion fills them first. */
public final class CompositeItemSource implements ItemSource {

    private static final NamespacedId id = NamespacedId.of("structurecumulus", "composite");

    private final List<ItemSource> sources;

    public CompositeItemSource(List<ItemSource> sources) {
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    /** One source answers alone; two or more are aggregated in argument order. */
    public static ItemSource of(List<ItemSource> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("a composite needs at least one source");
        }
        return sources.size() == 1 ? sources.get(0) : new CompositeItemSource(sources);
    }

    public List<ItemSource> sources() {
        return sources;
    }

    @Override
    public NamespacedId id() {
        return id;
    }

    @Override
    public int available(ItemStack template) {
        long total = 0;
        for (ItemSource source : sources) {
            total += source.available(template);
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    @Override
    public ItemStack extract(ItemStack template, int count, ExtractionMode mode) {
        int remaining = count;
        int taken = 0;
        for (ItemSource source : sources) {
            if (remaining <= 0) {
                break;
            }
            ItemStack extracted = source.extract(template, remaining, mode);
            taken += extracted.getCount();
            remaining -= extracted.getCount();
        }
        return taken == 0 ? ItemStack.EMPTY : template.copyWithCount(taken);
    }

    @Override
    public ItemStack insert(ItemStack stack, ExtractionMode mode) {
        ItemStack remainder = stack;
        for (ItemSource source : sources) {
            if (remainder.isEmpty()) {
                break;
            }
            remainder = source.insert(remainder, mode);
        }
        return remainder;
    }
}
