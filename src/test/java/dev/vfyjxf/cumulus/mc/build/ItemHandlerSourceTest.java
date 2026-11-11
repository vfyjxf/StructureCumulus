package dev.vfyjxf.cumulus.mc.build;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemHandlerSourceTest {

    private static final ItemStack stone = new ItemStack(Items.STONE);
    private static final ItemStack dirt = new ItemStack(Items.DIRT);

    @Test
    void availableCountsTemplateMatchesOnly() {
        ItemHandlerSource source = new ItemHandlerSource(
            new FakeItemHandler(3, 64, stack(Items.STONE, 10), stack(Items.DIRT, 5), ItemStack.EMPTY)
        );
        assertEquals(10, source.available(stone));
        assertEquals(5, source.available(dirt));
        assertEquals(0, source.available(new ItemStack(Items.GOLD_INGOT)));
        assertEquals(0, source.available(ItemStack.EMPTY));
    }

    @Test
    void simulateLeavesTheSourceUntouched() {
        ItemHandlerSource source = new ItemHandlerSource(new FakeItemHandler(1, 64, stack(Items.STONE, 10)));
        ItemStack taken = source.extract(stone, 4, ExtractionMode.simulate);
        assertEquals(4, taken.getCount());
        assertEquals(10, source.available(stone), "a simulated extraction changes nothing");
    }

    @Test
    void executeDrainsAcrossSlotsInSlotOrder() {
        ItemHandlerSource source = new ItemHandlerSource(
            new FakeItemHandler(2, 64, stack(Items.STONE, 10), stack(Items.STONE, 7))
        );
        ItemStack taken = source.extract(stone, 12, ExtractionMode.execute);
        assertEquals(12, taken.getCount());
        assertEquals(5, source.available(stone));
        assertEquals(5, source.available(stone.copyWithCount(1)), "the template's count does not matter");
    }

    @Test
    void extractNeverTakesMoreThanTheSourceHolds() {
        ItemHandlerSource source = new ItemHandlerSource(new FakeItemHandler(1, 64, stack(Items.STONE, 3)));
        assertEquals(3, source.extract(stone, 10, ExtractionMode.execute).getCount());
        assertTrue(source.extract(stone, 1, ExtractionMode.execute).isEmpty());
    }

    @Test
    void insertFillsEarlierSlotsFirstAndReturnsTheRemainder() {
        ItemHandlerSource source = new ItemHandlerSource(new FakeItemHandler(1, 64));
        ItemStack remainder = source.insert(stack(Items.STONE, 100), ExtractionMode.execute);
        assertEquals(36, remainder.getCount(), "the slot holds 64, so 36 do not fit");
        assertEquals(64, source.available(stone));
    }

    @Test
    void simulatedInsertChangesNothing() {
        ItemHandlerSource source = new ItemHandlerSource(new FakeItemHandler(1, 64));
        assertEquals(36, source.insert(stack(Items.STONE, 100), ExtractionMode.simulate).getCount());
        assertEquals(0, source.available(stone));
    }

    @Test
    void creativeAcceptsEverythingItGives() {
        CreativeItemSource creative = new CreativeItemSource();
        assertEquals(Integer.MAX_VALUE, creative.available(stone));
        assertEquals(3, creative.extract(stone, 3, ExtractionMode.simulate).getCount());
        assertTrue(creative.insert(stack(Items.STONE, 64), ExtractionMode.execute).isEmpty());
    }

    @Test
    void compositeDrainsEarlierSourcesFirst() {
        ItemHandlerSource first = new ItemHandlerSource(new FakeItemHandler(1, 64, stack(Items.STONE, 5)));
        ItemHandlerSource second = new ItemHandlerSource(new FakeItemHandler(1, 64, stack(Items.STONE, 10)));
        CompositeItemSource composite = new CompositeItemSource(List.of(first, second));
        assertEquals(15, composite.available(stone));
        assertEquals(8, composite.extract(stone, 8, ExtractionMode.execute).getCount());
        assertEquals(0, first.available(stone), "the earlier source drains first");
        assertEquals(7, second.available(stone));
    }

    @Test
    void compositeInsertsIntoEarlierSourcesFirst() {
        ItemHandlerSource first = new ItemHandlerSource(new FakeItemHandler(1, 64, stack(Items.STONE, 60)));
        ItemHandlerSource second = new ItemHandlerSource(new FakeItemHandler(1, 64));
        CompositeItemSource composite = new CompositeItemSource(List.of(first, second));
        assertTrue(composite.insert(stack(Items.STONE, 10), ExtractionMode.execute).isEmpty());
        assertEquals(64, first.available(stone));
        assertEquals(6, second.available(stone));
    }

    @Test
    void aCompositeOfOneIsThatSource() {
        CreativeItemSource creative = new CreativeItemSource();
        assertSame(creative, CompositeItemSource.of(List.of(creative)));
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }
}
