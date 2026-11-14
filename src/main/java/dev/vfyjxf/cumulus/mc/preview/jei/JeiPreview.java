package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.StageTimeline;
import dev.vfyjxf.cumulus.mc.preview.jei.BlueprintPreviewCategory.PageView;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewOpenPayload;
import dev.vfyjxf.cumulus.mc.preview.scene.BlueprintPreviewRecipe;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene.Shading;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewViewMode;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The JEI preview's public face: what the category lists, the page state its face mutates, what its
 * last frame covered, and the two actions its buttons perform. Another renderer integration copies
 * this shape (roadmap §9).
 */
public final class JeiPreview {

    /**
     * One panel of the last drawn frame: how many cells it drew, the layer it highlights, the
     * recipe-local rectangle it occupied, and the fitted scale it drew at (the zoom's read-back).
     */
    public record Panel(int cells, int layer, int x, int y, int width, int height, float scale) {}

    /** A point of the recipe's own face, in pixels from its top-left corner. */
    public record CellPoint(double x, double y) {}

    private static volatile int drawnFrames;
    private static volatile int drawnCells;
    private static volatile List<Panel> drawnPanels = List.of();
    private static volatile List<PreviewScene.Shading> drawnShading = List.of();
    private static volatile int drawnLinks;
    private static volatile int drawnProbes;
    private static volatile int drawnMemberLabels;

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

    /** Link strokes drawn in the last frame; the set page's constraint overlay (design 18 §5). */
    public static int drawnLinks() {
        return drawnLinks;
    }

    /** Presence-probe crosses drawn in the last frame, summed over the panels (design 18 §5). */
    public static int drawnProbes() {
        return drawnProbes;
    }

    /** Member labels drawn in the last frame, summed over the panels (design 18 §5, 19 §5). */
    public static int drawnMemberLabels() {
        return drawnMemberLabels;
    }

    /** The compact timeline of one page as its face lays it out, or null when it does not list the page. */
    public static @Nullable StageTimeline timeline(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return recipe == null || view == null ? null : view.timeline(recipe);
    }

    /** A segment's or a head's hover line at a strip-local point; null when the cursor is elsewhere. */
    public static @Nullable String timelineHover(NamespacedId blueprintId, double mouseX, double mouseY) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return recipe == null || view == null ? null : view.timelineHover(recipe, mouseX, mouseY);
    }

    /** One link caption's hover line (its metric), or null when the cursor is not on a caption. */
    public static @Nullable String linkHover(NamespacedId blueprintId, double mouseX, double mouseY) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return recipe == null || view == null ? null : view.linkHover(recipe, mouseX, mouseY);
    }

    /** The stage the page shows, or -1 for the whole page under its slice (design 19 §5). */
    public static int stage(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return view == null ? -1 : view.stage();
    }

    /** Selects one stage ({@code -1} clears); false when the page does not carry that stage. */
    public static boolean selectStage(NamespacedId blueprintId, int stage) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (recipe == null || view == null) {
            return false;
        }
        int stages = recipe.presentation().stages().size();
        if (stage < -1 || stage >= stages) {
            return false;
        }
        view.selectStage(stage);
        return true;
    }

    /** A click at one strip-local point: the segment under it becomes the page's one stage. */
    public static boolean clickTimeline(NamespacedId blueprintId, int x, int y) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return recipe != null && view != null && view.selectTimeline(view.timeline(recipe), x, y);
    }

    /** The face's wheel zoom over the fitted scale; 1 is the fit itself (design 17 §4.4). */
    public static float zoom(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return view == null ? Float.NaN : view.zoom();
    }

    /** Turns the wheel's zoom by {@code notches}, clamped to the camera's range. */
    public static boolean zoomBy(NamespacedId blueprintId, float notches) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (view == null) {
            return false;
        }
        view.zoomBy(notches);
        return true;
    }

    /** The double click's frame-all: the drawn hull, the zoom back to one (design 17 §4.2). */
    public static boolean frame(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (view == null) {
            return false;
        }
        view.frame();
        return true;
    }

    /** One click on the scene, without a drag; two inside the threshold frame the hull. */
    public static boolean clickScene(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return view != null && view.clickScene(Util.getMillis());
    }

    /** The scroll of the timeline under the cursor, clamped to the strip; false when it does not scroll. */
    public static boolean scrollTimeline(NamespacedId blueprintId, double notches) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (recipe == null || view == null) {
            return false;
        }
        StageTimeline timeline = view.timeline(recipe);
        if (!timeline.scrollable()) {
            return false;
        }
        view.timelineScroll(
            timeline.clampScroll(view.timelineScroll() - (int) Math.round(notches * StageTimeline.scrollStepPx))
        );
        return true;
    }

    /** The set page's constraint overlay state; false on a blueprint page (design 18 §5). */
    public static boolean constraints(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return recipe != null && view != null && recipe.isSet() && view.constraints();
    }

    /** Toggles the set page's constraint overlay; false on a blueprint page. */
    public static boolean toggleConstraints(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (recipe == null || view == null || !recipe.isSet()) {
            return false;
        }
        view.toggleConstraints();
        return true;
    }

    /**
     * The shading one panel installed in the last drawn frame, or null before its first draw: the six
     * world faces in {@code Direction}'s order, the same law the preview screen keeps (design 15 §6.2).
     */
    public static @Nullable Shading shading(int panel) {
        List<Shading> shadings = drawnShading;
        return panel < 0 || panel >= shadings.size() ? null : shadings.get(panel);
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

    /** The page's yaw in degrees; the drag mutates it, never a payload field (design 15 §6.2). */
    public static float yaw(NamespacedId blueprintId) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        return view == null ? Float.NaN : view.yaw();
    }

    /** Sets the page's yaw, the value a horizontal drag writes; false when the page is unknown. */
    public static boolean setYaw(NamespacedId blueprintId, float yaw) {
        PageView view = JeiPreviewPlugin.view(blueprintId);
        if (view == null) {
            return false;
        }
        view.yaw(yaw);
        return true;
    }

    /** Moves the page's layer, returning it to the slice view from a stage selection; false if unknown. */
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
        if (recipe != null && recipe.isSet()) {
            // a set is a catalogue view: no session mechanism exists for it (design 18 §8)
            return;
        }
        openWorld(recipe == null ? blueprintId : recipe.selectedBlueprintId());
    }

    /** The page's "open in screen" button: the client screen opens on the member the page shows. */
    public static void openScreen(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = JeiPreviewPlugin.recipe(blueprintId);
        if (recipe == null) {
            PreviewScreen.open(blueprintId);
        } else if (recipe.isSet()) {
            PreviewScreen.openSet(recipe.screenId());
        } else {
            PreviewScreen.open(recipe.screenId());
        }
    }

    /** Pages the category to one blueprint; false when JEI or the entry is not available. */
    public static boolean show(NamespacedId blueprintId) {
        return JeiPreviewPlugin.show(blueprintId);
    }

    static void recordFrame(List<Panel> panels, List<PreviewScene.Shading> shading, int links, int probes, int labels) {
        int cells = 0;
        for (Panel panel : panels) {
            cells += panel.cells();
        }
        drawnPanels = List.copyOf(panels);
        drawnShading = List.copyOf(shading);
        drawnCells = cells;
        drawnLinks = links;
        drawnProbes = probes;
        drawnMemberLabels = labels;
        drawnFrames++;
    }

    static void resetFrames() {
        drawnFrames = 0;
        drawnCells = 0;
        drawnPanels = List.of();
        drawnShading = List.of();
        drawnLinks = 0;
        drawnProbes = 0;
        drawnMemberLabels = 0;
    }
}
