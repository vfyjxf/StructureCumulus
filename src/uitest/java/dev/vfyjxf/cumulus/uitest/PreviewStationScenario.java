package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout;
import dev.vfyjxf.cumulus.mc.preview.client.screen.StageTimeline;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The flagship page of design 17 §11: the relay station opens in the whole view with its ten stages,
 * all four viewports draw its 473 cells, and the in-screen switch changes the silhouette to the
 * seven-stage, two-pod compact member. It asserts models, never pixels; the screenshots are the
 * demo's presentation, and the page creates no world session.
 */
@ClientScenario(
    name = "preview.station.demo",
    group = "demo",
    tags = {"preview", "demo", "station"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class PreviewStationScenario {

    private static final NamespacedId station = NamespacedId.of("structurecumulus", "demo_station");
    private static final NamespacedId stationCompact = NamespacedId.of("structurecumulus", "demo_station_compact");
    /** The full member's expected cells: the four viewports draw every one of them. */
    private static final int fullCells = 473;
    /** The compact member's expected cells; its silhouette is also two pods shorter and three tiers lower. */
    private static final int compactCells = 323;

    public void define(ScenarioBuilder build) {
        float[] camera = new float[2];
        build = PreviewScenarioSupport.stage(build).runCommand("cumulusvisual hud hide").runCommand(
            "cumulusvisual tutorial hide"
        ).action(
            "park the previous act's command output",
            context -> Minecraft.getInstance().gui.getChat().clearMessages(true)
        ).log(
            "preview.station.demo: the relay station page - ten stages, four viewports, the variant switch to the compact member"
        ).waitUntil(
            "the catalogue carries the station group",
            () -> PreviewJeiProbe.catalogIds().containsAll(List.of(station, stationCompact))
        ).check("opening the page creates no world session before it", () -> !PreviewProbe.hasSession())
                .openScreen(() -> {
                    PreviewScreen.open(station);
                    return Minecraft.getInstance().screen;
                }).awaitScreen(PreviewScreen.class).frames(3)
                .check(
                    "the station page opens in the whole view with its ten stages",
                    () -> !screen().staged()
                            && screen().playback().wholeView()
                            && screen().summaryLine() == null
                            && screen().timeline().segments().size() == 10
                            && screen().drawnCells() == 4 * fullCells,
                    () -> "staged=" + screen().staged() + " summary=" + screen().summaryLine() + " stages="
                            + screen().timeline().segments().size() + " drawn=" + screen().drawnCells()
                ).check("the ten bays stack ten lanes past the cap, so the strip scrolls", () -> {
                    var timeline = screen().timeline();
                    return timeline.lanes().size() == 10
                            && timeline.scrollable()
                            && timeline.height() == StageTimeline.timelineMaxHeight;
                },
                    () -> "lanes=" + screen().timeline().lanes().size() + " scrollable="
                            + screen().timeline().scrollable() + " height=" + screen().timeline().height()
                ).action("wheel down over the timeline", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    ScreenLayout.Rect control = open.layout().control();
                    float x = 20.0F;
                    float y = control.y() + 3.0F + StageTimeline.laneHeight;
                    context.driver().placeCursor(x, y);
                    context.driver().scroll(open, x, y, -1.0D);
                }).frames(2)
                .check(
                    "the wheel scrolls the strip and the first lane rides out of the cap",
                    () -> screen().timelineScroll() > 0,
                    () -> Integer.toString(screen().timelineScroll())
                ).action("wheel back up over the timeline", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    ScreenLayout.Rect control = open.layout().control();
                    float x = 20.0F;
                    float y = control.y() + 3.0F + StageTimeline.laneHeight;
                    context.driver().placeCursor(x, y);
                    context.driver().scroll(open, x, y, 1.0D);
                }).frames(2)
                .check(
                    "the strip comes back to its top",
                    () -> screen().timelineScroll() == 0,
                    () -> Integer.toString(screen().timelineScroll())
                )
                .check(
                    "the page names its group member",
                    () -> "variant 1/2: full".equals(screen().variantLine()),
                    () -> String.valueOf(screen().variantLine())
                ).action("capture the page camera before the switch", context -> {
                    camera[0] = screen().camera().yaw();
                    camera[1] = screen().camera().zoom();
                }).frames(2).screenshot("01-station-full").checkCaptured("01-station-full")
                .action("click the group's next member", context -> clickButton(context, "variant \u203A")).frames(2)
                .check(
                    "the switch changes the silhouette to the compact member and keeps the camera",
                    () -> screen().variantIndex() == 1
                            && "variant 2/2: compact".equals(screen().variantLine())
                            && screen().timeline().segments().size() == 7
                            && screen().drawnCells() == 4 * compactCells
                            && screen().camera().yaw() == camera[0]
                            && screen().camera().zoom() == camera[1],
                    () -> "index=" + screen().variantIndex() + " line=" + screen().variantLine() + " stages="
                            + screen().timeline().segments().size() + " drawn=" + screen().drawnCells() + " yaw="
                            + screen().camera().yaw() + "/" + camera[0]
                ).screenshot("02-station-compact").frames(2).checkCaptured("02-station-compact")
                .action("Esc closes the station page", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }).waitUntil("the station page closed without a session", () -> Minecraft.getInstance().screen == null)
                .check("opening the page created no world session", () -> !PreviewProbe.hasSession()).timelineRecord(
                    "preview-station-demo",
                    () -> "station=" + station.text() + ", compact=" + stationCompact.text() + ", session="
                            + PreviewProbe.hasSession()
                );
        PreviewScenarioSupport.cleanup(build).runCommand("cumulusvisual hud show");
    }

    /** Clicks a control-strip button by its label; the rect comes from the screen's own layout. */
    private static void clickButton(TestContext context, String label) {
        PreviewScreen open = (PreviewScreen) context.screenOrThrow();
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
