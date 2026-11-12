package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One catalog entry as a JEI recipe: the scene plus the view state the GUI mutates in place. */
final class BlueprintPreviewRecipe {

    /** One material line with its client item resolved; an item this client cannot name is dropped. */
    record Material(ItemStack stack, int count) {}

    /** The most part lines one entry's tooltip lists before it counts the rest. */
    private static final int maxPartLines = 8;

    private final NamespacedId blueprintId;
    private PreviewScene scene;
    private PreviewPresentation presentation = new PreviewPresentation(null, List.of(), -1, List.of(), 0);
    private List<Material> materials = List.of();
    private float yaw = 135.0F;
    private float pitch = 30.0F;
    private int slice = -1;
    private PreviewViewMode viewMode = PreviewViewMode.isometric;

    BlueprintPreviewRecipe(PreviewCatalogPayload.Entry entry) {
        this.blueprintId = entry.blueprintId();
        update(entry);
    }

    void update(PreviewCatalogPayload.Entry entry) {
        this.scene = PreviewScene.of(entry);
        this.presentation = entry.presentation();
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

    /** The entry's own text: the root description, then one line per part with its hint (design 16 §10). */
    List<String> lines() {
        List<String> lines = new ArrayList<>();
        if (presentation.description() != null) {
            lines.add(presentation.description());
        }
        List<Stage> stages = presentation.stages();
        int shown = Math.min(stages.size(), maxPartLines);
        for (int index = 0; index < shown; index++) {
            Stage stage = stages.get(index);
            String line = (index + 1) + ". " + stage.name() + " (" + stage.expectedCells() + ")";
            if (stage.hint() != null) {
                line = line + " · " + stage.hint();
            }
            lines.add(line);
        }
        if (stages.size() > shown) {
            lines.add("+" + (stages.size() - shown) + " more part(s)");
        }
        return List.copyOf(lines);
    }

    PreviewScene scene() {
        return scene;
    }

    PreviewViewMode viewMode() {
        return viewMode;
    }

    void cycleViewMode() {
        viewMode = viewMode.next();
    }

    /** The cell tooltip under a recipe-local point in the current mode; a panel answers only for itself. */
    List<String> hoverLines(double mouseX, double mouseY) {
        for (PreviewScene.View view : BlueprintPreviewCategory.views(viewMode, yaw, pitch)) {
            PreviewScene.Cell cell = scene.hover(mouseX, mouseY, view, slice);
            if (cell != null) {
                return cellLines(cell);
            }
        }
        return List.of();
    }

    /**
     * The tooltip's cell lines: the block id, each property in key order, then the stage that owns the
     * cell (design 16 §10, §11). Ordering matters because the payload's map has none.
     */
    List<String> cellLines(PreviewScene.Cell cell) {
        List<String> lines = new ArrayList<>(cell.ghost().properties().size() + 2);
        lines.add(cell.ghost().block().text());
        cell.ghost().properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(property -> lines.add(property.getKey() + ": " + property.getValue()));
        List<Stage> stages = presentation.stages();
        int index = cell.ghost().stageIndex();
        if (index >= 0 && index < stages.size()) {
            Stage stage = stages.get(index);
            lines.add("part: " + stage.name() + (stage.hint() == null ? "" : " · " + stage.hint()));
        }
        return List.copyOf(lines);
    }

    /** Where one expected cell's centre lands in one panel of the current mode. */
    Optional<JeiPreview.CellPoint> point(int panel, int cellIndex) {
        PreviewScene.View[] views = BlueprintPreviewCategory.views(viewMode, yaw, pitch);
        if (panel < 0 || panel >= views.length || cellIndex < 0 || cellIndex >= scene.cells().size()) {
            return Optional.empty();
        }
        Vector2f point = scene.point(views[panel], slice, scene.cells().get(cellIndex).ghost().cell());
        return point == null ? Optional.empty() : Optional.of(new JeiPreview.CellPoint(point.x, point.y));
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
