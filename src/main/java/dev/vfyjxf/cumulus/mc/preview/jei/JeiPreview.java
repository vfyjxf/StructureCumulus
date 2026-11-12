package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.jei.BlueprintPreviewCategory.PageView;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewOpenPayload;
import dev.vfyjxf.cumulus.mc.preview.scene.BlueprintPreviewRecipe;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewViewMode;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The JEI preview's public face: what the category lists, the page state its face mutates, what its
 * last frame covered, and the one action its button performs. Another renderer integration copies
 * this shape (roadmap §9).
 */
public final class JeiPreview {

    /**
     * One panel of the last drawn frame: how many cells it drew, the layer it highlights, and the
     * recipe-local rectangle it occupied.
     */
    public record Panel(int cells, int layer, int x, int y, int width, int height) {}

    /** A point of the recipe's own face, in pixels from its top-left corner. */
    public record CellPoint(double x, double y) {}

    private static volatile int drawnFrames;
    private static volatile int drawnCells;
    private static volatile List<Panel> drawnPanels = List.of();

    private JeiPreview() {}

    /** The blueprint ids the category lists, in the catalog's order. */
    public static List<NamespacedId> blueprintIds() {
        return JeiPreviewPlugin.listedIds();
    }

    /** Frames the category has drawn since the last world join. */
    public static int drawnFrames() {
        return drawnFrames;
    }

    /** The expected cells the last drawn frame covered, summed over its panels. */
    public static int drawnCells() {
        return drawnCells;
    }

    /** The panels the last drawn frame covered; one in the isometric view, three in the three-view. */
    public static List<Panel> drawnPanels() {
        return drawnPanels;
    }

    /** The entry's tooltip text: its root description and one line per part (design 16 §10). */
    public static List<String> lines(NamespacedId blueprintId) {
        return JeiPreviewPlugin.lines(blueprintId);
    }

    /** The page's view mode, or null when the category does not list the entry. */
    public static @Nullable PreviewViewMode viewMode(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return view == null ? null : view.mode();
    }

    /** The mode button's action: isometric → three-view → slice grid → isometric. */
    public static boolean cycleViewMode(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (view == null) {
            return false;
        }
        view.cycleMode();
        return true;
    }

    /** The page's group members in declaration order; a standalone page answers with one id. */
    public static List<NamespacedId> variants(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        return recipe == null ? List.of() : recipe.variantIds();
    }

    /** The selected member's index on the page, or -1 when the category does not list the page. */
    public static int variantIndex(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        return recipe == null ? -1 : recipe.variantIndex();
    }

    /** The member the page shows and the button opens; null when the category does not list the page. */
    public static @Nullable NamespacedId selectedBlueprintId(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        return recipe == null ? null : recipe.selectedBlueprintId();
    }

    /** Switches the page one member left ({@code -1}) or right ({@code +1}); false on a lone page. */
    public static boolean cycleVariant(NamespacedId blueprintId, int step) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        return recipe != null && recipe.cycleVariant(step);
    }

    /** The Y layer the page shows; the slider, the wheel and every panel read this one value. */
    public static int slice(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return view == null ? -1 : view.slice();
    }

    /** Moves the page's layer; false when the entry is unknown, clamped to the entry's own layers. */
    public static boolean setSlice(NamespacedId blueprintId, int layer) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (recipe == null || view == null) {
            return false;
        }
        view.slice(Math.max(0, Math.min(recipe.scene().layers() - 1, layer)));
        return true;
    }

    /** Where an expected cell's centre lands in one panel of the current mode, recipe-local. */
    public static Optional<CellPoint> cellPoint(NamespacedId blueprintId, int panel, int cellIndex) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return recipe == null || view == null ? Optional.empty() : view.point(recipe, panel, cellIndex);
    }

    /** The cell tooltip's lines under a recipe-local point; empty when no visible cell is under it. */
    public static List<String> hoverText(NamespacedId blueprintId, double mouseX, double mouseY) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return recipe == null || view == null ? List.of() : view.hoverLines(recipe, mouseX, mouseY);
    }

    /** The "Ghost to world" action: the server opens the ordinary session at the crosshair. */
    public static void openWorld(NamespacedId blueprintId) {
        PacketDistributor.sendToServer(new PreviewOpenPayload(blueprintId));
    }

    /** The page's "Ghost to world" button: the member the page shows is the blueprint it opens. */
    public static void openSelected(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        openWorld(recipe == null ? blueprintId : recipe.selectedBlueprintId());
    }

    /** Pages the category to one blueprint; false when JEI or the entry is not available. */
    public static boolean show(NamespacedId blueprintId) {
        return JeiPreviewPlugin.show(blueprintId);
    }

    static void recordFrame(List<Panel> panels) {
        int cells = 0;
        for (Panel panel : panels) {
            cells += panel.cells();
        }
        drawnPanels = List.copyOf(panels);
        drawnCells = cells;
        drawnFrames++;
    }

    static void resetFrames() {
        drawnFrames = 0;
        drawnCells = 0;
        drawnPanels = List.of();
    }
}
