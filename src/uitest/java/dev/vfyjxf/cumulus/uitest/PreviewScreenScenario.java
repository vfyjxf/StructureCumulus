package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.scene.AxisGizmo;
import dev.vfyjxf.cumulus.mc.preview.scene.Fit;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The catalogue page end to end (design 17 §3–§6): opening it on the probe, the four linked viewports
 * and their drawn cells, the orbit and the wheel, the axes, the screen's own key table and the world
 * bindings that must not fire through it.
 */
@ClientScenario(
    name = "screen.preview",
    group = "screen",
    tags = {"screen", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewScreenScenario {

    private static final int mainDragFrom = 150;
    private static final int mainDragTo = 190;
    private static final int mainY = 100;

    private static int candidatesBefore;
    private static Siting sitingBefore;
    private static int currentBefore;
    private static float yawBefore;
    private static float zoomBefore;
    private static float movedX;
    private static float movedY;
    private static @Nullable AxisGizmo gizmoBeforeZoom;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build)
                .log("screen.preview: open the catalog page, four viewports, orbit/zoom, the axes, the key table");

        PreviewScenarioSupport.open(build)
                .waitUntil("a world session is open behind the screen", PreviewScenarioSupport::synced)
                .action("capture the session state before the screen", context -> {
                    candidatesBefore = PreviewProbe.candidateCount();
                    sitingBefore = PreviewProbe.siting();
                    currentBefore = PreviewProbe.current();
                })
                .waitUntil(
                    "the catalog carries the probe page",
                    () -> PreviewCatalogClient.entries().stream().map(PreviewCatalogPayload.Entry::blueprintId)
                            .anyMatch(PreviewScenarioSupport.probe::equals)
                ).openScreen(() -> {
                    PreviewScreen.open(PreviewScenarioSupport.probe);
                    return Minecraft.getInstance().screen;
                }).awaitScreen(PreviewScreen.class).frames(3)
                .check(
                    "the page builds one scene and the four viewports draw its four cells each",
                    () -> screen().drawnCells() == 16,
                    () -> Integer.toString(screen().drawnCells())
                ).check("the layout keeps the main viewport and the three axis-locked panels", () -> {
                    ScreenLayout layout = screen().layout();
                    return !layout.main().isEmpty()
                            && layout.front() != null
                            && !layout.front().isEmpty()
                            && layout.side() != null
                            && !layout.side().isEmpty()
                            && layout.top() != null
                            && !layout.top().isEmpty();
                }, () -> String.valueOf(screen().layout()))
                .check("the main viewport carries the corner compass", () -> {
                    AxisGizmo gizmo = screen().gizmo();
                    ScreenLayout.Rect main = screen().layout().main();
                    if (gizmo == null || main == null) {
                        return false;
                    }
                    float centreX = main.x() + main.width() - AxisGizmo.insetPx;
                    float centreY = main.y() + AxisGizmo.insetPx;
                    return gizmo.x().start().equals(gizmo.y().start())
                            && gizmo.y().start().equals(gizmo.z().start())
                            && Math.abs(gizmo.x().start().x - centreX) < 0.01F
                            && Math.abs(gizmo.x().start().y - centreY) < 0.01F
                            && gizmo.ordered().size() == 3;
                }).action("zoom in with the wheel and read the compass again", context -> {
                    gizmoBeforeZoom = screen().gizmo();
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    ScreenLayout.Rect main = open.layout().main();
                    context.driver().placeCursor(main.x() + main.width() / 2, main.y() + main.height() / 2);
                    context.driver().scroll(open, main.x() + main.width() / 2, main.y() + main.height() / 2, 1.0D);
                }).frames(2).check("a zoom turns the picture and never the compass", () -> {
                    AxisGizmo after = screen().gizmo();
                    return gizmoBeforeZoom != null
                            && after != null
                            && after.x().start().equals(gizmoBeforeZoom.x().start())
                            && after.x().end().equals(gizmoBeforeZoom.x().end())
                            && after.z().end().equals(gizmoBeforeZoom.z().end());
                }).action("put the cursor on the cell nearest the viewer", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    ScreenLayout.Rect main = open.layout().main();
                    Fit fit = Fit
                            .of(open.scene().hull(), main.x(), main.y(), main.width(), main.height(), open.camera());
                    PreviewScene.Cell nearest = null;
                    int nearestSum = Integer.MIN_VALUE;
                    for (PreviewScene.Cell cell : open.scene().cells()) {
                        if (cell.state() == null) {
                            continue;
                        }
                        var at = cell.ghost().cell();
                        int sum = at.x() + at.y() + at.z();
                        if (sum > nearestSum) {
                            nearestSum = sum;
                            nearest = cell;
                        }
                    }
                    if (nearest == null) {
                        return;
                    }
                    var at = nearest.ghost().cell();
                    var point = fit.project(at.x() + 0.5F, at.y() + 0.5F, at.z() + 0.5F);
                    movedX = point.x;
                    movedY = point.y;
                    context.driver().placeCursor(point.x, point.y);
                    context.driver().moveTo(open, point.x, point.y);
                }).frames(2).check("a cell under the cursor is hovered and named", () -> {
                    PreviewScene.Cell hovered = screen().hovered();
                    return hovered != null && hovered.ghost().block().text().endsWith("oak_stairs");
                }, () -> {
                    PreviewScene.Cell hovered = screen().hovered();
                    return "cursor=" + movedX + "," + movedY + " hovered="
                            + (hovered == null ? "none" : hovered.ghost().block().text()) + " cells="
                            + screen().scene().cellCount() + " main=" + screen().layout().main();
                }).action("capture the camera before the gestures", context -> {
                    yawBefore = screen().camera().yaw();
                    zoomBefore = screen().camera().zoom();
                }).action("orbit with a left drag in the main viewport", context -> {
                    var driver = context.driver();
                    var open = context.screenOrThrow();
                    driver.mouseDown(open, mainDragFrom, mainY, 0);
                    driver.dragTo(open, mainDragTo, mainY, 0, mainDragTo - mainDragFrom, 0);
                    driver.mouseUp(open, mainDragTo, mainY, 0);
                }).frames(2)
                .check(
                    "the drag turned the main viewport and only that camera",
                    () -> Math.abs(screen().camera().yaw() - yawBefore) > 30.0F,
                    () -> Float.toString(screen().camera().yaw())
                )
                .action(
                    "zoom the main viewport with the wheel",
                    context -> context.driver().scroll(context.screenOrThrow(), mainDragFrom, mainY, 1.0D)
                ).frames(2)
                .check(
                    "the wheel multiplied the orthographic zoom",
                    () -> screen().camera().zoom() > zoomBefore,
                    () -> Float.toString(screen().camera().zoom())
                ).key(GLFW.GLFW_KEY_1, 0).frames(1)
                .check(
                    "1 snaps the main viewport to the front",
                    () -> screen().camera().yaw() == 180.0F && screen().camera().pitch() == 0.0F,
                    () -> screen().camera().yaw() + "/" + screen().camera().pitch()
                ).key(GLFW.GLFW_KEY_3, 0).check("3 snaps it to the right", () -> screen().camera().yaw() == 270.0F)
                .key(GLFW.GLFW_KEY_7, 0)
                .check("7 snaps it to the top", () -> screen().camera().pitch() == SceneCamera.maxPitch)
                .action("Ctrl+1 sends the back snapshot", context -> {
                    var driver = context.driver();
                    var open = context.screenOrThrow();
                    driver.keyDown(open, GLFW.GLFW_KEY_1, 0, GLFW.GLFW_MOD_CONTROL);
                    driver.keyUp(open, GLFW.GLFW_KEY_1, 0, GLFW.GLFW_MOD_CONTROL);
                }).check("Ctrl+1 is the back snapshot", () -> screen().camera().yaw() == 0.0F)
                .key(GLFW.GLFW_KEY_HOME, 0)
                .check(
                    "Home returns the default 45 degree isometric, framed",
                    () -> screen().camera().yaw() == SceneCamera.defaultYaw
                            && screen().camera().pitch() == SceneCamera.defaultPitch
                            && screen().camera().zoom() == 1.0F
                ).key(GLFW.GLFW_KEY_F, 0)
                .check("F frames all without touching the pose", () -> screen().camera().zoom() == 1.0F)
                .key(GLFW.GLFW_KEY_R, 0).key(GLFW.GLFW_KEY_V, 0).key(GLFW.GLFW_KEY_G, 0).frames(2)
                .check("the world's preview keys do not reach the session behind the screen", () -> {
                    Siting siting = PreviewProbe.siting();
                    return PreviewProbe.candidateCount() == candidatesBefore
                            && PreviewProbe.current() == currentBefore
                            && (siting == null ? sitingBefore == null : siting.equals(sitingBefore));
                }, () -> PreviewProbe.current() + "/" + PreviewProbe.candidateCount())
                .check("the screen is still the frame", () -> Minecraft.getInstance().screen instanceof PreviewScreen)
                .screenshot("screen").frames(2).checkCaptured("screen").action("Esc closes the screen", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }).waitUntil("Esc closes the screen and not the session", () -> Minecraft.getInstance().screen == null)
                .frames(2).check("the session survives the screen's Esc", PreviewProbe::hasSession).timelineRecord(
                    "screen-preview",
                    () -> "viewports=" + (Minecraft.getInstance().screen instanceof PreviewScreen ? 4 : 0)
                            + ", candidates=" + PreviewProbe.candidateCount()
                );

        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }

    private static PreviewScreen screen() {
        return (PreviewScreen) Minecraft.getInstance().screen;
    }
}
