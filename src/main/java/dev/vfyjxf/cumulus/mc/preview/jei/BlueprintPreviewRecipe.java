package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One JEI page as a recipe: the group it belongs to (declaration order) and the member it currently
 * shows, plus the view state the GUI mutates in place. A page is one entry however many variants the
 * group declares; the variant row switches the member in place and the button uses the selected id
 * (design 15 §12.3).
 */
final class BlueprintPreviewRecipe {

    /** One material line with its client item resolved; an item this client cannot name is dropped. */
    record Material(ItemStack stack, int count) {}

    /** The most part lines one entry's tooltip lists before it counts the rest. */
    private static final int maxPartLines = 8;

    /** The page's identity: the first member the catalog ships, in declaration order. */
    private final NamespacedId pageId;
    /** The shipped members of the page's group, in declaration order; a standalone page holds one. */
    private List<PreviewCatalogPayload.Entry> variants = List.of();
    private int variantIndex;
    private PreviewScene scene;
    private PreviewPresentation presentation = new PreviewPresentation(null, List.of(), -1, List.of(), 0);
    private List<Material> materials = List.of();
    private float yaw = 135.0F;
    private float pitch = 30.0F;
    private int slice = -1;
    private PreviewViewMode viewMode = PreviewViewMode.isometric;

    BlueprintPreviewRecipe(List<PreviewCatalogPayload.Entry> group) {
        if (group.isEmpty()) {
            throw new IllegalArgumentException("a preview page needs at least one catalog entry");
        }
        this.pageId = group.get(0).blueprintId();
        this.variants = List.copyOf(group);
        this.variantIndex = 0;
        applySelection();
    }

    /**
     * Applies a fresh catalog to this page: the group's entries are replaced, and the selected member
     * is kept by id when the reload still ships it (a reload never loses the user's variant).
     */
    void update(List<PreviewCatalogPayload.Entry> group) {
        if (group.isEmpty()) {
            return;
        }
        NamespacedId selected = variants.isEmpty() ? group.get(0).blueprintId() : selectedBlueprintId();
        this.variants = List.copyOf(group);
        int index = indexOf(selected);
        this.variantIndex = index < 0 ? 0 : index;
        applySelection();
    }

    /** The page's own id; the recipe identity JEI holds across reloads. */
    NamespacedId blueprintId() {
        return pageId;
    }

    /** The id the page currently shows: the member a "Ghost to world" opens (design 15 §12.4). */
    NamespacedId selectedBlueprintId() {
        return variants.get(variantIndex).blueprintId();
    }

    /** The shipped members of the group, in declaration order. */
    List<NamespacedId> variantIds() {
        List<NamespacedId> ids = new ArrayList<>(variants.size());
        for (PreviewCatalogPayload.Entry entry : variants) {
            ids.add(entry.blueprintId());
        }
        return List.copyOf(ids);
    }

    int variantIndex() {
        return variantIndex;
    }

    int variantCount() {
        return variants.size();
    }

    /** Switches left/right inside the page; false when the page holds one member. */
    boolean cycleVariant(int step) {
        if (variants.size() < 2) {
            return false;
        }
        variantIndex = Math.floorMod(variantIndex + step, variants.size());
        applySelection();
        return true;
    }

    /** Selects one member by id; false when the page does not ship it or it is already selected. */
    boolean selectVariant(NamespacedId blueprintId) {
        int index = indexOf(blueprintId);
        if (index < 0 || index == variantIndex) {
            return false;
        }
        variantIndex = index;
        applySelection();
        return true;
    }

    /** The selected member's variant line for the tooltip, or null for a standalone page. */
    @Nullable
    String variantLine() {
        if (variants.size() < 2) {
            return null;
        }
        return "variant " + (variantIndex + 1) + "/" + variants.size() + ": " + selectedText();
    }

    private String selectedText() {
        VariantSummary summary = variants.get(variantIndex).variants();
        VariantSummary.Member member = summary == null ? null : summary.member(selectedBlueprintId());
        return member == null ? selectedBlueprintId().text() : member.text();
    }

    private int indexOf(NamespacedId blueprintId) {
        for (int index = 0; index < variants.size(); index++) {
            if (variants.get(index).blueprintId().equals(blueprintId)) {
                return index;
            }
        }
        return -1;
    }

    private void applySelection() {
        PreviewCatalogPayload.Entry selected = variants.get(variantIndex);
        this.scene = PreviewScene.of(selected);
        this.presentation = selected.presentation();
        List<Material> resolved = new ArrayList<>(selected.materials().size());
        for (PreviewCatalogPayload.MaterialLine line : selected.materials()) {
            ItemStack stack = stack(line.item());
            if (!stack.isEmpty()) {
                resolved.add(new Material(stack, line.count()));
            }
        }
        this.materials = List.copyOf(resolved);
        this.slice = slice < 0 ? scene.layers() - 1 : Math.min(slice, scene.layers() - 1);
    }

    /** The page's own text: the selected member's root description, then one line per part. */
    List<String> lines() {
        List<String> lines = new ArrayList<>();
        String variant = variantLine();
        if (variant != null) {
            lines.add(variant);
        }
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
