package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.scene.BasePlate;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import dev.vfyjxf.cumulus.mc.preview.scene.Reveal;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.TreeMap;

/**
 * The play's zero state and the reveal's two directions (design 17 §7.1, §7.5, §7.6): a running play
 * opens on the base plate alone, the first batch lands after the hold, the default drop falls from
 * above while the rise climbs out of the plate's underside, and the screen's button toggles the
 * direction as view state that dies with the screen.
 */
@ClientScenario(
    name = "screen.reveal",
    group = "screen",
    tags = {"screen", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewScreenRevealScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final float eps = 1.0e-3F;
    /** The stage-1 cells' offsets and own leg starts, sampled on the frame their batch lands. */
    private static final Map<Integer, Float> risen = new TreeMap<>();
    private static float plateBottom;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "screen.reveal: the empty page's opening hold, the drop's default, the rise's per-cell legs, the toggle"
        );

        build.waitUntil(
            "the catalog carries the staged page",
            () -> PreviewCatalogClient.entries().stream().map(PreviewCatalogPayload.Entry::blueprintId)
                    .anyMatch(staged::equals)
        ).openScreen(() -> {
            PreviewScreen.open(staged);
            return Minecraft.getInstance().screen;
        }).awaitScreen(PreviewScreen.class).frames(3)
                .check(
                    "the page opens in the whole view with the reveal on its default",
                    () -> screen().playback().wholeView()
                            && screen().revealDirection() == Reveal.Direction.drop
                            && screen().buttonLabels().contains("drop")
                            && !screen().buttonLabels().contains("rise"),
                    () -> "direction=" + screen().revealDirection() + " labels=" + screen().buttonLabels()
                ).action("start the play with the space bar", context -> {
                    var open = context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_SPACE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_SPACE, 0, 0);
                }).frames(1)
                .check(
                    "a running play opens on the empty page: the base plate alone, no cell, no fade",
                    () -> screen().playback().playing()
                            && screen().playback().cursor() == 0
                            && screen().playback().revealed(0) == 0
                            && screen().playback().pause() > 0.0F
                            && screen().drawnCells() == 0
                            && screen().drawnPlateTiles() == 4 * screen().scene().plate().tiles()
                            && screen().animatingCells() == 0,
                    () -> "revealed=" + screen().playback().revealed(0) + " pause=" + screen().playback().pause()
                            + " drawn=" + screen().drawnCells() + " plate=" + screen().drawnPlateTiles() + " animating="
                            + screen().animatingCells()
                )
                .waitUntil(
                    "the opening hold runs out and the first batch lands",
                    () -> screen().playback().revealed(0) == 9
                )
                .check(
                    "the default drop falls from above the slots, no higher than the drop's own height",
                    () -> screen().revealDirection() == Reveal.Direction.drop
                            && screen().drawnCells() == 36
                            && largestOffset() > 0.0F
                            && largestOffset() <= Reveal.dropCells,
                    () -> "drawn=" + screen().drawnCells() + " highest=" + largestOffset()
                ).waitUntil("the first batch settles", () -> screen().animatingCells() == 0)
                .check(
                    "every dropped cell is back on its slot",
                    () -> screen().animatingCells() == 0 && largestOffset() == 0.0F,
                    () -> "animating=" + screen().animatingCells() + " highest=" + largestOffset()
                ).action("press the whole-view button", context -> click("all", context)).frames(2)
                .check(
                    "all returns to the whole view and stops the play",
                    () -> screen().playback().wholeView() && screen().drawnCells() == 48
                ).action("press the drop button to compare the rise", context -> click("drop", context)).frames(2)
                .check(
                    "the direction button flips to rise and the reveal mode follows it",
                    () -> screen().revealDirection() == Reveal.Direction.rise
                            && screen().buttonLabels().contains("rise")
                            && !screen().buttonLabels().contains("drop"),
                    () -> "direction=" + screen().revealDirection() + " labels=" + screen().buttonLabels()
                ).action("start the second play with the space bar", context -> {
                    var open = context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_SPACE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_SPACE, 0, 0);
                }).frames(1)
                .check(
                    "the rise plays its own opening hold on the same empty page",
                    () -> screen().playback().playing()
                            && screen().playback().revealed(0) == 0
                            && screen().drawnCells() == 0
                            && screen().drawnPlateTiles() > 0,
                    () -> "revealed=" + screen().playback().revealed(0) + " drawn=" + screen().drawnCells() + " plate="
                            + screen().drawnPlateTiles()
                ).waitUntil("the second play's first batch climbs out of the plate", () -> {
                    if (screen().playback().revealed(0) == 9 && risen.isEmpty()) {
                        plateBottom = screen().scene().plate().y() - BasePlate.thickness;
                        for (PreviewScene.Cell cell : screen().scene().cells()) {
                            if (cell.ghost().stageIndex() == 0) {
                                risen.put(cell.ghost().cell().y(), screen().revealOffsetOf(cell.ghost()));
                            }
                        }
                    }
                    return screen().playback().revealed(0) == 9;
                })
                .check(
                    "a rising cell starts at the plate's underside, below its slot",
                    () -> firstRiseHolds(),
                    () -> "bottom=" + plateBottom + " offsets=" + risen
                ).waitUntil("the second play's second batch climbs", () -> {
                    if (screen().playback().revealed(1) == 3 && risen.size() == 1) {
                        for (PreviewScene.Cell cell : screen().scene().cells()) {
                            if (cell.ghost().stageIndex() == 1) {
                                risen.put(100 + cell.ghost().cell().y(), screen().revealOffsetOf(cell.ghost()));
                            }
                        }
                    }
                    return screen().playback().revealed(1) == 3;
                })
                .check(
                    "one batch's higher cell climbs the longer way, both legs sharing the span",
                    () -> secondRiseHolds(),
                    () -> "bottom=" + plateBottom + " offsets=" + risen
                ).waitUntil("the second play stops at the table's end", () -> !screen().playback().playing())
                .waitUntil("the risen batch settles", () -> screen().animatingCells() == 0)
                .check(
                    "every risen cell is seated on its slot",
                    () -> screen().animatingCells() == 0 && largestOffset() == 0.0F && smallestOffset() == 0.0F,
                    () -> "animating=" + screen().animatingCells() + " highest=" + largestOffset() + " lowest="
                            + smallestOffset()
                ).action("press the rise button to go back to the drop", context -> click("rise", context)).frames(2)
                .check(
                    "the same button flips back to drop",
                    () -> screen().revealDirection() == Reveal.Direction.drop
                            && screen().buttonLabels().contains("drop"),
                    () -> "direction=" + screen().revealDirection() + " labels=" + screen().buttonLabels()
                ).action("Esc closes the screen", context -> {
                    var open = context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }).waitUntil("the page closed", () -> Minecraft.getInstance().screen == null).frames(2)
                .action("open the staged page again", context -> PreviewScreen.open(staged)).frames(3)
                .check(
                    "the direction dies with the screen: a fresh page is the default drop again",
                    () -> screen().revealDirection() == Reveal.Direction.drop
                            && screen().buttonLabels().contains("drop"),
                    () -> "direction=" + screen().revealDirection() + " labels=" + screen().buttonLabels()
                ).timelineRecord("screen-reveal", () -> "direction=" + screen().revealDirection())
                .action("Esc closes the second page", context -> {
                    var open = context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }).waitUntil("the second page closed", () -> Minecraft.getInstance().screen == null).frames(2);

        PreviewScenarioSupport.cleanup(build);
    }

    /** The first batch is all ground floor: every cell's leg starts exactly at the plate's underside. */
    private static boolean firstRiseHolds() {
        if (risen.size() != 1) {
            return false;
        }
        float offset = risen.values().iterator().next();
        return offset < 0.0F && offset >= plateBottom - eps;
    }

    /**
     * One batch shares one stamp, so every cell's remaining fraction of its own leg is the same: the
     * legs differ only by their start, {@code plateBottom - cellY}, and all of them sit below the slots.
     */
    private static boolean secondRiseHolds() {
        if (risen.size() != 4) {
            return false;
        }
        Float shared = null;
        for (Map.Entry<Integer, Float> entry : risen.entrySet()) {
            if (entry.getKey() < 100) {
                continue;
            }
            int y = entry.getKey() - 100;
            float start = plateBottom - y;
            float offset = entry.getValue();
            if (offset >= 0.0F || offset < start - eps) {
                return false;
            }
            float fraction = offset / start;
            if (shared == null) {
                shared = fraction;
            } else if (Math.abs(fraction - shared) > 1.0e-3F) {
                return false;
            }
        }
        return shared != null;
    }

    private static float largestOffset() {
        float largest = 0.0F;
        for (PreviewScene.Cell cell : screen().scene().cells()) {
            largest = Math.max(largest, screen().revealOffsetOf(cell.ghost()));
        }
        return largest;
    }

    private static float smallestOffset() {
        float smallest = 0.0F;
        for (PreviewScene.Cell cell : screen().scene().cells()) {
            smallest = Math.min(smallest, screen().revealOffsetOf(cell.ghost()));
        }
        return smallest;
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

    private static PreviewScreen screen() {
        return (PreviewScreen) Minecraft.getInstance().screen;
    }
}
