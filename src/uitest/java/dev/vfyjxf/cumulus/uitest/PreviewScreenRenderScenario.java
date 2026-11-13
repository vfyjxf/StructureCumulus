package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.scene.BasePlate;
import dev.vfyjxf.cumulus.mc.preview.scene.CellOutline;
import dev.vfyjxf.cumulus.mc.preview.scene.DropIn;
import dev.vfyjxf.cumulus.mc.preview.scene.Fit;
import dev.vfyjxf.cumulus.mc.preview.scene.Hull;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen's rendering law (design 17 §4.1, §5.3, §7.6): the world-fixed directional shading of
 * every panel at every snap, the hover outline as the hovered cell's projected silhouette, and the
 * staged playback's drop-in from its stamp to its landing.
 */
@ClientScenario(
    name = "screen.render",
    group = "screen",
    tags = {"screen", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewScreenRenderScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final int mainPanel = 0;
    private static final float eps = 0.01F;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build)
                .log("screen.render: the world-fixed shading at six snaps, the silhouette outline, the drop-in");

        PreviewScenarioSupport.open(build)
                .waitUntil("a world session is open behind the screen", PreviewScenarioSupport::synced)
                .waitUntil(
                    "the catalog carries the probe page",
                    () -> PreviewCatalogClient.entries().stream().map(PreviewCatalogPayload.Entry::blueprintId)
                            .anyMatch(PreviewScenarioSupport.probe::equals)
                ).openScreen(() -> {
                    PreviewScreen.open(PreviewScenarioSupport.probe);
                    return Minecraft.getInstance().screen;
                }).awaitScreen(PreviewScreen.class).frames(3)
                .check(
                    "every panel shades a face by the world's lights, not by its camera",
                    () -> sameShading(0) && sameShading(1) && sameShading(2) && sameShading(3),
                    () -> describeShading()
                ).check("the shading is directional: up bright, the four sides two tones, down the floor", () -> {
                    PreviewScreen.Shading shading = screen().shading(mainPanel);
                    return shading != null
                            && Math.abs(shading.up() - 1.0F) < eps
                            && Math.abs(shading.north() - 0.74F) < eps
                            && Math.abs(shading.south() - 0.74F) < eps
                            && Math.abs(shading.east() - 0.497F) < eps
                            && Math.abs(shading.west() - 0.497F) < eps
                            && Math.abs(shading.down() - 0.4F) < eps;
                }, () -> describeShading())
                .check(
                    "every panel draws the base plate under the page, and no plate tile is a cell",
                    () -> screen().drawnPlateTiles() == 4 * screen().scene().plate().tiles()
                            && screen().drawnPlateTiles() > 0
                            && screen().drawnCells() == 16,
                    () -> "plate=" + screen().drawnPlateTiles() + " tiles=" + screen().scene().plate().tiles()
                            + " drawn=" + screen().drawnCells()
                ).check(
                    "the plate is a platform of two-cell boxes half a cell thick, one tile past the hull",
                    () -> slabPlatform(),
                    PreviewScreenRenderScenario::describePlate
                ).action("hover the nearest cell at the default iso", PreviewScreenRenderScenario::hoverMain).frames(3)
                .check(
                    "the iso outline is the cell's six-vertex silhouette",
                    () -> outline(mainPanel) != null && outline(mainPanel).points().size() == 6,
                    () -> describeOutline(mainPanel)
                ).check(
                    "the axis-locked panels outline the same cell as a four-vertex rectangle",
                    () -> outline(1) != null
                            && outline(1).points().size() == 4
                            && outline(2) != null
                            && outline(2).points().size() == 4
                            && outline(3) != null
                            && outline(3).points().size() == 4,
                    () -> "front=" + describeOutline(1) + " side=" + describeOutline(2) + " top=" + describeOutline(3)
                ).check(
                    "every outline vertex is a projected corner of the hovered cell",
                    () -> outlineMatchesCell(mainPanel)
                            && outlineMatchesCell(1)
                            && outlineMatchesCell(2)
                            && outlineMatchesCell(3),
                    () -> describeOutline(mainPanel)
                ).check("the stroke's vertices are the raw projections, never pixel-snapped", () -> {
                    CellOutline outline = outline(mainPanel);
                    if (outline == null) {
                        return false;
                    }
                    for (Vector2f point : outline.points()) {
                        if (Math.abs(point.x - Math.rint(point.x)) > 1.0E-3F
                                || Math.abs(point.y - Math.rint(point.y)) > 1.0E-3F) {
                            return true;
                        }
                    }
                    return false;
                }, () -> describeOutline(mainPanel)).screenshot("render-iso").frames(2).checkCaptured("render-iso")
                .key(GLFW.GLFW_KEY_1, 0).action("hover at the front snap", PreviewScreenRenderScenario::hoverMain)
                .frames(3)
                .check(
                    "the front snap still shades every face by the world",
                    () -> sameShading(0) && sameShading(1) && sameShading(2) && sameShading(3),
                    () -> describeShading()
                ).check(
                    "the front snap draws the hovered cell as a four-vertex rectangle",
                    () -> outline(mainPanel) != null && outline(mainPanel).points().size() == 4,
                    () -> describeOutline(mainPanel)
                ).screenshot("render-front").frames(2).checkCaptured("render-front").key(GLFW.GLFW_KEY_3, 0)
                .action("hover at the right snap", PreviewScreenRenderScenario::hoverMain).frames(3)
                .screenshot("render-right").frames(2).checkCaptured("render-right").key(GLFW.GLFW_KEY_7, 0)
                .action("hover at the top snap", PreviewScreenRenderScenario::hoverMain).frames(3)
                .check(
                    "the top snap still shades every face by the world",
                    () -> sameShading(0) && sameShading(1) && sameShading(2) && sameShading(3)
                ).screenshot("render-top").frames(2).checkCaptured("render-top")
                .action("Ctrl+7 sends the bottom snapshot", context -> {
                    var driver = context.driver();
                    var open = context.screenOrThrow();
                    driver.keyDown(open, GLFW.GLFW_KEY_7, 0, GLFW.GLFW_MOD_CONTROL);
                    driver.keyUp(open, GLFW.GLFW_KEY_7, 0, GLFW.GLFW_MOD_CONTROL);
                }).frames(3)
                .check(
                    "the bottom snapshot keeps the world's shading on every face",
                    () -> sameShading(0) && sameShading(1) && sameShading(2) && sameShading(3),
                    () -> describeShading()
                ).screenshot("render-bottom").frames(2).checkCaptured("render-bottom")
                .action("switch to the perspective arm", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    ScreenLayout.Rect rect = open.buttonRect("ortho");
                    if (rect == null) {
                        return;
                    }
                    context.driver().mouseDown(open, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, 0);
                    context.driver().mouseUp(open, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, 0);
                }).frames(3).action("hover in perspective", PreviewScreenRenderScenario::hoverMain).frames(3)
                .check(
                    "the perspective arm shades by the same world law and keeps the hovered outline",
                    () -> screen().camera().projection() == SceneCamera.Projection.perspective
                            && sameShading(0)
                            && sameShading(1)
                            && sameShading(2)
                            && sameShading(3)
                            && outline(mainPanel) != null,
                    () -> "projection=" + screen().camera().projection() + " outline=" + describeOutline(mainPanel)
                            + " shading=" + describeShading()
                )
                .check(
                    "the perspective arm still draws the page",
                    () -> screen().drawnCells() == 16,
                    () -> Integer.toString(screen().drawnCells())
                ).screenshot("render-perspective").frames(2).checkCaptured("render-perspective")
                .key(GLFW.GLFW_KEY_HOME, 0).frames(2)
                .action("open the staged page for the drop-in", context -> PreviewScreen.open(staged)).frames(3)
                .check(
                    "the staged page opens with nothing falling",
                    () -> screen().animatingCells() == 0 && largestDrop() == 0.0F,
                    () -> "animating=" + screen().animatingCells() + " highest=" + largestDrop()
                ).key(GLFW.GLFW_KEY_B, 0).frames(2)
                .check(
                    "a stepped cell falls into an empty slot: it alone is drawn, in every panel",
                    () -> screen().playback().cursor() == 0
                            && screen().playback().revealed(0) == 1
                            && screen().drawnCells() == 4
                            && screen().animatingCells() > 0
                            && largestDrop() <= DropIn.dropInCells,
                    () -> "drawn=" + screen().drawnCells() + " revealed=" + screen().playback().revealed(0)
                            + " animating=" + screen().animatingCells() + " highest=" + largestDrop()
                ).frames(1)
                .check(
                    "the falling cell hangs between the plate and its slot, never at either end",
                    () -> largestDrop() > 0.0F && largestDrop() < DropIn.dropInCells,
                    () -> "highest=" + largestDrop()
                ).waitUntil("the step's cell lands", () -> screen().animatingCells() == 0).key(GLFW.GLFW_KEY_SPACE, 0)
                .waitUntil("the play reveals the rest of the stage", () -> screen().animatingCells() > 0)
                .check(
                    "the play reveals the stage as one batch: every remaining cell falls together",
                    () -> screen().animatingCells() == 8 && largestDrop() <= DropIn.dropInCells,
                    () -> "animating=" + screen().animatingCells() + " highest=" + largestDrop()
                ).frames(1)
                .check(
                    "the cursor's stage carries the outline on every drawn cell in every panel",
                    () -> screen().drawnStageOutlines() == 36,
                    () -> "stageOutlines=" + screen().drawnStageOutlines()
                ).screenshot("render-play").frames(2).checkCaptured("render-play")
                .waitUntil("the batch lands five ticks after the reveal", () -> screen().animatingCells() == 0)
                .check(
                    "every cell is back on its slot",
                    () -> screen().animatingCells() == 0 && largestDrop() == 0.0F,
                    () -> "animating=" + screen().animatingCells() + " highest=" + largestDrop()
                )
                .timelineRecord(
                    "screen-render",
                    () -> "highest=" + largestDrop() + ", animating=" + screen().animatingCells()
                ).action("Esc closes the screen", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }).waitUntil("the page closed", () -> Minecraft.getInstance().screen == null).frames(2);

        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }

    private static boolean sameShading(int panel) {
        PreviewScreen.Shading reference = screen().shading(0);
        PreviewScreen.Shading candidate = screen().shading(panel);
        if (reference == null || candidate == null) {
            return false;
        }
        return Math.abs(reference.up() - candidate.up()) < 1.0E-4F
                && Math.abs(reference.north() - candidate.north()) < 1.0E-4F
                && Math.abs(reference.south() - candidate.south()) < 1.0E-4F
                && Math.abs(reference.east() - candidate.east()) < 1.0E-4F
                && Math.abs(reference.west() - candidate.west()) < 1.0E-4F
                && Math.abs(reference.down() - candidate.down()) < 1.0E-4F;
    }

    private static CellOutline outline(int panel) {
        return screen().hoverOutline(panel);
    }

    private static boolean outlineMatchesCell(int panel) {
        CellOutline outline = outline(panel);
        PreviewScene.Cell hovered = screen().hovered();
        if (outline == null || hovered == null) {
            return false;
        }
        List<Vector2f> corners = projectedCorners(hovered, panel);
        for (Vector2f point : outline.points()) {
            boolean found = corners.stream()
                    .anyMatch(corner -> Math.abs(corner.x - point.x) < 0.01F && Math.abs(corner.y - point.y) < 0.01F);
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static List<Vector2f> projectedCorners(PreviewScene.Cell cell, int panel) {
        ScreenLayout layout = screen().layout();
        ScreenLayout.Rect rect = switch (panel) {
            case 0 -> layout.main();
            case 1 -> layout.front();
            case 2 -> layout.side();
            default -> layout.top();
        };
        SceneCamera camera = screen().panelCamera(panel);
        if (rect == null || camera == null) {
            return List.of();
        }
        Fit fit = Fit.of(screen().scene().hull(), rect.x(), rect.y(), rect.width(), rect.height(), camera);
        LatticePoint at = cell.ghost().cell();
        List<Vector2f> corners = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            corners.add(
                fit.project(
                    at.x() + ((corner & 1) == 0 ? 0.0F : 1.0F),
                    at.y() + ((corner & 2) == 0 ? 0.0F : 1.0F),
                    at.z() + ((corner & 4) == 0 ? 0.0F : 1.0F)
                )
            );
        }
        return corners;
    }

    /** The plate's pure geometry: two-cell boxes, half a cell thick, one tile past the hull on every side. */
    private static boolean slabPlatform() {
        BasePlate plate = screen().scene().plate();
        Hull hull = screen().scene().hull();
        if (plate.isEmpty()) {
            return false;
        }
        Hull tile = plate.tile(plate.minX(), plate.minZ());
        return plate.y() == (int) hull.minY()
                && tile.maxY() == plate.y()
                && tile.minY() == plate.y() - BasePlate.thickness
                && (int) (tile.maxX() - tile.minX()) == BasePlate.tileSize
                && (int) (tile.maxZ() - tile.minZ()) == BasePlate.tileSize
                && plate.quads() == plate.tiles() * BasePlate.facesPerTile
                && Math.floorMod(plate.minX(), BasePlate.tileSize) == 0
                && Math.floorMod(plate.maxX(), BasePlate.tileSize) == 0
                && Math.floorMod(plate.minZ(), BasePlate.tileSize) == 0
                && Math.floorMod(plate.maxZ(), BasePlate.tileSize) == 0
                && plate.minX() < (int) Math.floor(hull.minX())
                && plate.maxX() > (int) Math.ceil(hull.maxX())
                && plate.minZ() < (int) Math.floor(hull.minZ())
                && plate.maxZ() > (int) Math.ceil(hull.maxZ());
    }

    private static String describePlate() {
        BasePlate plate = screen().scene().plate();
        return "plate [" + plate.minX() + "," + plate.minZ() + ".." + plate.maxX() + "," + plate.maxZ() + "] y="
                + plate.y() + " tiles=" + plate.tiles() + " quads=" + plate.quads();
    }

    private static float largestDrop() {
        float highest = 0.0F;
        for (PreviewScene.Cell cell : screen().scene().cells()) {
            highest = Math.max(highest, screen().dropOffsetOf(cell.ghost()));
        }
        return highest;
    }

    private static void hoverMain(TestContext context) {
        PreviewScreen open = (PreviewScreen) context.screenOrThrow();
        ScreenLayout.Rect main = open.layout().main();
        if (main == null || main.isEmpty()) {
            return;
        }
        Fit fit = Fit.of(open.scene().hull(), main.x(), main.y(), main.width(), main.height(), open.camera());
        GhostCell target = null;
        float best = -Float.MAX_VALUE;
        for (PreviewScene.Cell cell : open.scene().cells()) {
            if (cell.state() == null) {
                continue;
            }
            LatticePoint at = cell.ghost().cell();
            float depth = fit.depth(at.x() + 0.5F, at.y() + 0.5F, at.z() + 0.5F);
            if (depth > best) {
                best = depth;
                target = cell.ghost();
            }
        }
        if (target == null) {
            return;
        }
        Vector2f point = fit.project(target.cell().x() + 0.5F, target.cell().y() + 0.5F, target.cell().z() + 0.5F);
        context.driver().placeCursor(point.x, point.y);
        context.driver().moveTo(open, point.x, point.y);
    }

    private static String describeShading() {
        StringBuilder line = new StringBuilder();
        for (int panel = 0; panel < 4; panel++) {
            PreviewScreen.Shading shading = screen().shading(panel);
            line.append(panel).append('=').append(shading == null ? "none" : shading.up() + "/" + shading.north());
            line.append(' ');
        }
        return line.toString().trim();
    }

    private static String describeOutline(int panel) {
        CellOutline outline = outline(panel);
        return outline == null ? "none" : outline.points().size() + " points";
    }

    private static PreviewScreen screen() {
        return (PreviewScreen) Minecraft.getInstance().screen;
    }
}
