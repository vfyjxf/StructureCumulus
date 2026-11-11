package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** One catalog entry as a JEI recipe: the scene plus the view state the GUI mutates in place. */
final class BlueprintPreviewRecipe {

    /** One material line with its client item resolved; an item this client cannot name is dropped. */
    record Material(ItemStack stack, int count) {}

    private final NamespacedId blueprintId;
    private PreviewScene scene;
    private List<Material> materials = List.of();
    private float yaw = 135.0F;
    private float pitch = 30.0F;
    private int slice = -1;

    BlueprintPreviewRecipe(PreviewCatalogPayload.Entry entry) {
        this.blueprintId = entry.blueprintId();
        update(entry);
    }

    void update(PreviewCatalogPayload.Entry entry) {
        this.scene = PreviewScene.of(entry);
        List<Material> resolved = new ArrayList<>(entry.materials().size());
        for (PreviewCatalogPayload.MaterialLine line : entry.materials()) {
            ItemStack stack = stack(line.item());
            if (!stack.isEmpty()) {
                resolved.add(new Material(stack, line.count()));
            }
        }
        this.materials = List.copyOf(resolved);
        this.slice = slice < 0 ? scene.layers() - 1 : Math.min(slice, scene.layers() - 1);
    }

    NamespacedId blueprintId() {
        return blueprintId;
    }

    PreviewScene scene() {
        return scene;
    }

    List<Material> materials() {
        return materials;
    }

    float yaw() {
        return yaw;
    }

    void yaw(float value) {
        yaw = value;
    }

    float pitch() {
        return pitch;
    }

    void pitch(float value) {
        pitch = value;
    }

    int slice() {
        return slice;
    }

    void slice(int value) {
        slice = value;
    }

    private static ItemStack stack(NamespacedId id) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
