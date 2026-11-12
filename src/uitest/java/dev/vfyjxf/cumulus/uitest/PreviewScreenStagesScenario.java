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
import dev.vfyjxf.cumulus.mc.preview.scene.ExplodeModel;
import dev.vfyjxf.cumulus.mc.preview.scene.Fit;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The staged page end to end (design 17 §7, §8): the whole view opens, the stage bar's segments, the
 * delta counts and factors, the play that stops at the table's end, the future-cell toggle, the
 * explode's offsets and labels, the framing hand-back and the single-bay refusal.
 */
@ClientScenario(
    name = "screen.playback",
    group = "screen",
    tags = {"screen", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewScreenStagesScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final NamespacedId probe = NamespacedId.of("structurecumulus", "preview_probe");
    private static final String floorName = "structurecumulus:base";
    private static final String stackName = "structurecumulus:stack";

    private static int drawnAfterDrag;
    private static float targetXAfterDrag;
    private static float targetYAfterDrag;
    private static float targetZAfterDrag;
    private static LatticePoint displacedCell;
    private static float zoomBeforeExplode;
    private static float displacedX;
    private static float displacedY;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "screen.playback: the whole view, the stage bar, the delta counts, the play, hidden/outline, the explode and one part"
        );

        build.waitUntil(
            "the catalog carries the staged page",
            () -> PreviewCatalogClient.entries().stream().map(PreviewCatalogPayload.Entry::blueprintId)
                    .anyMatch(staged::equals)
        ).openScreen(() -> {
            PreviewScreen.open(staged);
            return Minecraft.getInstance().screen;
        }).awaitScreen(PreviewScreen.class).frames(3).check(
            "the page opens in the whole view with every stage drawn",
            () -> screen().playback().wholeView() && screen().drawnCells() == 48 && screen().summaryLine() == null,
            () -> "whole=" + screen().playback().wholeView() + " drawn=" + screen().drawnCells() + " summary="
                    + screen().summaryLine()
        ).check("the stage bar is a table of contents: two proportional segments with a one-pixel gap", () -> {
            var segments = screen().stageBar().segments();
            return segments.size() == 2
                    && segments.get(0).width() > segments.get(1).width()
                    && segments.get(1).width() >= 1
                    && segments.get(1).x() == segments.get(0).x() + segments.get(0).width() + 1;
        }, () -> String.valueOf(screen().stageBar().segments()))
                .check(
                    "the control strip carries the whole view, the player, the step, the arrows and the future toggle",
                    () -> {
                        var labels = screen().buttonLabels();
                        return labels.containsAll(List.of("all", "explode", "play", "step", "<", ">", "hidden"));
                    },
                    () -> String.valueOf(screen().buttonLabels())
                ).action("click the stage bar's second segment", context -> {
                    var open = context.screenOrThrow();
                    var segment = screen().stageBar().segments().get(1);
                    int y = screen().stageBar().y() + screen().stageBar().height() / 2;
                    context.driver().mouseDown(open, segment.x() + segment.width() / 2, y, 0);
                    context.driver().mouseUp(open, segment.x() + segment.width() / 2, y, 0);
                }).frames(2)
                .check(
                    "a bar click enters the staged view with that stage whole and paused",
                    () -> screen().playback().cursor() == 1
                            && screen().playback().revealed(1) == 3
                            && !screen().playback().playing(),
                    () -> "cursor=" + screen().playback().cursor() + " revealed=" + screen().playback().revealed(1)
                ).check("the summary line is the pack's next-step line for that stage", () -> {
                    String line = screen().summaryLine();
                    return line != null
                            && line.startsWith("place 3\u00D7 minecraft:iron_bars (stage 2/2: " + stackName + ")")
                            && line.endsWith("place it after the floor is complete");
                }, () -> String.valueOf(screen().summaryLine()))
                .action("click the stage bar's first segment", context -> {
                    var open = context.screenOrThrow();
                    var segment = screen().stageBar().segments().get(0);
                    int y = screen().stageBar().y() + screen().stageBar().height() / 2;
                    context.driver().mouseDown(open, segment.x() + segment.width() / 2, y, 0);
                    context.driver().mouseUp(open, segment.x() + segment.width() / 2, y, 0);
                }).frames(2)
                .check(
                    "the delta hides the future stage and draws the cursor's own at full alpha",
                    () -> screen().playback().cursor() == 0
                            && screen().drawnCells() == 36
                            && screen().alphaOf(firstGhost(1)) == 0.0F
                            && !screen().drawn(firstGhost(1))
                            && screen().alphaOf(firstGhost(0)) == 1.0F,
                    () -> "drawn=" + screen().drawnCells() + " futureAlpha=" + screen().alphaOf(firstGhost(1))
                            + " futureDrawn=" + screen().drawn(firstGhost(1))
                ).check("the summary line names the floor's own count and hint", () -> {
                    String line = screen().summaryLine();
                    return line != null
                            && line.startsWith("place 9\u00D7 minecraft:stone_bricks (stage 1/2: " + floorName + ")")
                            && line.endsWith("lay the brick ring before the corners");
                }, () -> String.valueOf(screen().summaryLine())).screenshot("screen-stages").frames(2)
                .checkCaptured("screen-stages")
                .check(
                    "hidden is the default and draws no box for the future cells",
                    () -> !screen().outlineMode() && screen().drawnFutureBoxes() == 0,
                    () -> "outline=" + screen().outlineMode() + " boxes=" + screen().drawnFutureBoxes()
                ).action("press the future-cells toggle", context -> click("hidden", context)).frames(2).check(
                    "outline mode boxes each future cell in every panel",
                    () -> screen().outlineMode() && screen().drawnFutureBoxes() == 12,
                    () -> "outline=" + screen().outlineMode() + " boxes=" + screen().drawnFutureBoxes()
                ).action("press the future-cells toggle again", context -> click("outline", context)).frames(2).check(
                    "the same button exits outline mode",
                    () -> !screen().outlineMode() && screen().drawnFutureBoxes() == 0
                ).action("press the whole-view button", context -> click("all", context)).frames(2).check(
                    "all drops the cursor and draws every stage again",
                    () -> screen().playback().wholeView()
                            && screen().drawnCells() == 48
                            && screen().summaryLine() == null
                ).key(GLFW.GLFW_KEY_SPACE, 0).frames(1).check(
                    "a play enters the staged view at the first stage and starts the stage framing",
                    () -> screen().playback().playing()
                            && screen().playback().cursor() == 0
                            && screen().framingActive()
                            && screen().framingArmed(),
                    () -> "playing=" + screen().playback().playing() + " cursor=" + screen().playback().cursor()
                            + " framing=" + screen().framingActive()
                ).action("orbit the main viewport during the play", context -> {
                    var open = context.screenOrThrow();
                    var driver = context.driver();
                    driver.mouseDown(open, 150, 100, 0);
                    driver.dragTo(open, 190, 100, 0, 40, 0);
                    driver.mouseUp(open, 190, 100, 0);
                }).frames(2)
                .check(
                    "the first manual gesture disarms the framing and never pauses the play",
                    () -> !screen().framingActive()
                            && !screen().framingArmed()
                            && screen().playback().playing()
                            && Math.abs(screen().camera().yaw() - 45.0F) > 20.0F,
                    () -> "framing=" + screen().framingActive() + " armed=" + screen().framingArmed() + " playing="
                            + screen().playback().playing() + " yaw=" + screen().camera().yaw()
                ).action("record the camera the hand left", context -> {
                    targetXAfterDrag = screen().camera().targetX();
                    targetYAfterDrag = screen().camera().targetY();
                    targetZAfterDrag = screen().camera().targetZ();
                    drawnAfterDrag = screen().drawnCells();
                }).waitUntil("the play stops at the table's end", () -> !screen().playback().playing()).frames(2)
                .check(
                    "the play walked both stages and stopped with everything revealed",
                    () -> screen().playback().cursor() == 1
                            && screen().playback().revealed(0) == 9
                            && screen().playback().revealed(1) == 3
                            && screen().drawnCells() == 48,
                    () -> "cursor=" + screen().playback().cursor() + " revealed=" + screen().playback().revealed(0)
                            + "/" + screen().playback().revealed(1)
                ).check(
                    "the disarmed framing did not move the camera when the next stage began",
                    () -> screen().camera().targetX() == targetXAfterDrag
                            && screen().camera().targetY() == targetYAfterDrag
                            && screen().camera().targetZ() == targetZAfterDrag,
                    () -> "target=" + screen().camera().targetX() + "," + screen().camera().targetY() + ","
                            + screen().camera().targetZ() + " drag=" + targetXAfterDrag + "," + targetYAfterDrag + ","
                            + targetZAfterDrag + " drawnAtDrag=" + drawnAfterDrag
                ).check(
                    "the exploded-view button is armed on a two-bay page",
                    () -> screen().canExplode() && screen().explodeT() == 0.0F && screen().drawnLabels() == 0
                )
                .action("record the camera before the explode", context -> zoomBeforeExplode = screen().camera().zoom())
                .action("press the explode button", context -> click("explode", context)).frames(1)
                .check(
                    "the explode starts a tween rather than jumping",
                    () -> screen().explodeT() > 0.0F && screen().explodeT() < 1.0F,
                    () -> Float.toString(screen().explodeT())
                ).waitUntil("the explode reaches t = 1", () -> screen().explodeT() == 1.0F).frames(2)
                .check("every part is displaced by the half-diagonal along its own direction", () -> {
                    ExplodeModel model = screen().explode();
                    float distance = model.distance();
                    ExplodeModel.Offset floor = model.offset(firstGhost(0), 1.0F);
                    ExplodeModel.Offset stack = model.offset(firstGhost(1), 1.0F);
                    return model.separable()
                            && distance > 0.0F
                            && Math.abs(floor.y() + distance) < 1.0E-4F
                            && Math.abs(stack.y() - distance) < 1.0E-4F
                            && model.offset(firstGhost(0), 0.0F).equals(ExplodeModel.Offset.zero);
                }, () -> {
                    ExplodeModel model = screen().explode();
                    return "distance=" + model.distance() + " floor=" + model.offset(firstGhost(0), 1.0F) + " stack="
                            + model.offset(firstGhost(1), 1.0F);
                })
                .check(
                    "arming the explode does not refit the camera",
                    () -> screen().camera().zoom() == zoomBeforeExplode,
                    () -> Float.toString(screen().camera().zoom()) + " vs " + zoomBeforeExplode
                )
                .check(
                    "each part's name is drawn on its moved centroid in all four panels",
                    () -> screen().drawnLabels() == 8
                            && screen().explode().labels(1.0F).stream().map(ExplodeModel.Label::text).toList()
                                    .equals(List.of(floorName, stackName)),
                    () -> "labels=" + screen().drawnLabels() + " texts="
                            + screen().explode().labels(1.0F).stream().map(ExplodeModel.Label::text).toList()
                ).action("frame all while exploded", context -> click("frame", context)).frames(2)
                .check(
                    "frame all fits the exploded hull instead of the page hull",
                    () -> screen().camera().zoom() < 1.0F,
                    () -> Float.toString(screen().camera().zoom())
                ).screenshot("screen-explode").frames(2).checkCaptured("screen-explode")
                .action("hover the displaced chimney top where it is drawn", context -> {
                    var open = screen();
                    ScreenLayout.Rect main = open.layout().main();
                    Fit fit = Fit
                            .of(open.scene().hull(), main.x(), main.y(), main.width(), main.height(), open.camera());
                    GhostCell top = highestGhost(1);
                    ExplodeModel.Offset offset = open.explode().offset(top, open.explodeT());
                    LatticePoint at = top.cell();
                    Vector2f point = fit.project(
                        at.x() + 0.5F + offset.x(),
                        at.y() + 0.5F + offset.y(),
                        at.z() + 0.5F + offset.z()
                    );
                    displacedCell = at;
                    displacedX = point.x;
                    displacedY = point.y;
                    context.driver().placeCursor(point.x, point.y);
                    context.driver().moveTo(open, point.x, point.y);
                }).frames(2)
                .check(
                    "the exploded cell is picked where it is drawn",
                    () -> screen().hovered() != null && screen().hovered().ghost().cell().equals(displacedCell),
                    () -> "cursor=" + displacedX + "," + displacedY + " hovered="
                            + (screen().hovered() == null ? "none" : screen().hovered().ghost().cell())
                ).action("press the whole-view button while exploded", context -> click("all", context)).frames(2)
                .check(
                    "the whole view explodes too: every stage at full alpha and both parts apart",
                    () -> screen().playback().wholeView()
                            && screen().drawnCells() == 48
                            && screen().drawnLabels() == 8
                            && screen().explodeT() == 1.0F,
                    () -> "whole=" + screen().playback().wholeView() + " drawn=" + screen().drawnCells() + " labels="
                            + screen().drawnLabels()
                ).screenshot("screen-explode-whole").frames(2).checkCaptured("screen-explode-whole")
                .action("collapse the explode", context -> click("explode", context))
                .waitUntil("the collapse reaches t = 0", () -> screen().explodeT() == 0.0F).frames(2)
                .check("a collapsed page draws no part labels", () -> screen().drawnLabels() == 0)
                .action("open the single-bay page", context -> {
                    PreviewScreen.open(probe);
                }).frames(3)
                .check(
                    "a single-bay page reports one part and keeps t at 0",
                    () -> !screen().canExplode()
                            && screen().buttonLabels().contains("one part")
                            && screen().explodeT() == 0.0F,
                    () -> "separate=" + screen().canExplode() + " labels=" + screen().buttonLabels()
                ).action("press the one-part button", context -> click("one part", context)).frames(2)
                .check(
                    "the one-part refusal leaves the picture whole",
                    () -> screen().explodeT() == 0.0F && screen().drawnLabels() == 0
                )
                .timelineRecord(
                    "screen-playback",
                    () -> "explode=" + screen().explodeT() + ", drawn=" + screen().drawnCells() + ", onePart="
                            + !screen().canExplode()
                ).action("Esc closes the screen", context -> {
                    var open = context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }).waitUntil("Esc closes the page", () -> Minecraft.getInstance().screen == null).frames(2);

        PreviewScenarioSupport.cleanup(build);
    }

    private static void click(String label, TestContext context) {
        PreviewScreen open = screen();
        ScreenLayout.Rect rect = open.buttonRect(label);
        if (rect == null) {
            throw new IllegalStateException("no " + label + " button in " + open.buttonLabels());
        }
        context.driver().mouseDown(open, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, 0);
        context.driver().mouseUp(open, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, 0);
    }

    private static GhostCell highestGhost(int stage) {
        GhostCell highest = null;
        for (PreviewScene.Cell cell : screen().scene().cells()) {
            if (cell.ghost().stageIndex() != stage) {
                continue;
            }
            if (highest == null || cell.ghost().cell().y() > highest.cell().y()) {
                highest = cell.ghost();
            }
        }
        if (highest == null) {
            throw new IllegalStateException("the page carries no cell of stage " + stage);
        }
        return highest;
    }

    private static GhostCell firstGhost(int stage) {
        for (PreviewScene.Cell cell : screen().scene().cells()) {
            if (cell.ghost().stageIndex() == stage) {
                return cell.ghost();
            }
        }
        throw new IllegalStateException("the page carries no cell of stage " + stage);
    }

    private static PreviewScreen screen() {
        return (PreviewScreen) Minecraft.getInstance().screen;
    }
}
