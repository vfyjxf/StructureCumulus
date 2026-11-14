package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.ClientConfig;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.scene.StagePlayback;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The player group's speed slider (design 17 §4.5, §7.5, D-114): it reads and writes the one
 * {@code animation.speed} key, its track clicks and drags snap to the quarter step and clamp at the
 * key's own ends, its label is the value, the file receives the write, and a play at 4.0x spends a
 * quarter of a play at 1.0x on the same stage.
 */
@ClientScenario(
    name = "screen.speed",
    group = "screen",
    tags = {"screen", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewScreenSpeedScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final double eps = 1.0e-6D;
    private static double speedBefore;
    private static double playStart;
    private static double oneSpan;
    private static double fourStart;
    private static double fourSpan;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "screen.speed: the slider's one key, its quarter steps and clamps, its file write and the 4.0x pace"
        );

        build.action(
            "remember the speed the suite arrived with",
            context -> speedBefore = ClientConfig.animationSpeed()
        ).action("set the baseline speed on the one key", context -> ClientConfig.setAnimationSpeed(1.0D)).waitUntil(
            "the catalog carries the staged page",
            () -> PreviewCatalogClient.entries().stream().map(PreviewCatalogPayload.Entry::blueprintId)
                    .anyMatch(staged::equals)
        ).openScreen(() -> {
            PreviewScreen.open(staged);
            return Minecraft.getInstance().screen;
        }).awaitScreen(PreviewScreen.class).frames(3)
                .check(
                    "the player group carries the speed slider, and its label reads the key",
                    () -> screen().sliderRect() != null
                            && screen().sliderTrack() != null
                            && screen().speedLabel().equals("1.0\u00D7")
                            && Math.abs(ClientConfig.animationSpeed() - 1.0D) < eps,
                    () -> "slider=" + screen().sliderRect() + " track=" + screen().sliderTrack() + " label="
                            + screen().speedLabel() + " key=" + ClientConfig.animationSpeed()
                ).check("the slider sits in the control strip and overlaps no button", () -> {
                    ScreenLayout.Rect slider = screen().sliderRect();
                    ScreenLayout.Rect strip = screen().layout().control();
                    if (slider == null || !inside(strip, slider)) {
                        return false;
                    }
                    for (String label : screen().buttonLabels()) {
                        ScreenLayout.Rect button = screen().buttonRect(label);
                        if (button != null && overlaps(slider, button)) {
                            return false;
                        }
                    }
                    return true;
                }, () -> {
                    ScreenLayout.Rect slider = screen().sliderRect();
                    StringBuilder overlapping = new StringBuilder();
                    for (String label : screen().buttonLabels()) {
                        ScreenLayout.Rect button = screen().buttonRect(label);
                        if (slider != null && button != null && overlaps(slider, button)) {
                            overlapping.append(label).append(' ');
                        }
                    }
                    return "slider=" + slider + " strip=" + screen().layout().control() + " overlaps=" + overlapping
                            + " buttons=" + screen().buttonLabels();
                }).action("park the pointer off the control strip", PreviewScreenSpeedScenario::park)
                .screenshot("screen-speed").frames(2).checkCaptured("screen-speed")
                .action("click the track a third of the way along", context -> clickTrack(context, 1.0D / 3.0D))
                .frames(2)
                .check(
                    "a track click writes the quarter step it names, and the label and the key agree",
                    () -> Math.abs(ClientConfig.animationSpeed() - 1.5D) < eps
                            && screen().speedLabel().equals("1.5\u00D7"),
                    () -> "key=" + ClientConfig.animationSpeed() + " label=" + screen().speedLabel()
                ).check(
                    "the click's write reached config/structurecumulus-client.toml",
                    () -> savedSpeed().contains("speed = 1.5"),
                    () -> "file=" + FMLPaths.CONFIGDIR.get().resolve("structurecumulus-client.toml") + " body="
                            + savedSpeed()
                ).action("park the pointer off the control strip", PreviewScreenSpeedScenario::park)
                .screenshot("screen-speed-15").frames(2).checkCaptured("screen-speed-15")
                .action("drag the slider past the track's right end", context -> dragTrack(context, 1.6D)).frames(2)
                .check(
                    "the drag clamps at the key's ceiling: 4.0x, not past it",
                    () -> Math.abs(ClientConfig.animationSpeed() - 4.0D) < eps
                            && screen().speedLabel().equals("4.0\u00D7"),
                    () -> "key=" + ClientConfig.animationSpeed() + " label=" + screen().speedLabel()
                ).action("park the pointer off the control strip", PreviewScreenSpeedScenario::park)
                .screenshot("screen-speed-40").frames(2).checkCaptured("screen-speed-40")
                .action("drag the slider past the track's left end", context -> dragTrack(context, -0.6D)).frames(2)
                .check(
                    "the drag clamps at the key's floor: 0.25x, not below it",
                    () -> Math.abs(ClientConfig.animationSpeed() - 0.25D) < eps
                            && screen().speedLabel().equals("0.25\u00D7"),
                    () -> "key=" + ClientConfig.animationSpeed() + " label=" + screen().speedLabel()
                ).action("park the pointer off the control strip", PreviewScreenSpeedScenario::park)
                .screenshot("screen-speed-025").frames(2).checkCaptured("screen-speed-025")
                .action("click the track's middle", context -> clickTrack(context, 0.5D)).frames(2)
                .check(
                    "a click at the middle jumps to the step there, not a pixel value",
                    () -> Math.abs(ClientConfig.animationSpeed() - 2.25D) < eps
                            && screen().speedLabel().equals("2.25\u00D7"),
                    () -> "key=" + ClientConfig.animationSpeed() + " label=" + screen().speedLabel()
                ).action("click the track back at the key's default", context -> clickTrack(context, 0.2D)).frames(2)
                .check(
                    "the default reads 1.0x on the label and in the key",
                    () -> Math.abs(ClientConfig.animationSpeed() - 1.0D) < eps
                            && screen().speedLabel().equals("1.0\u00D7"),
                    () -> "key=" + ClientConfig.animationSpeed() + " label=" + screen().speedLabel()
                ).action("press the whole-view button", context -> click("all", context)).frames(2)
                .action("start a play at 1.0x and note the clock", context -> {
                    playStart = screen().clock().time();
                    pressSpace(context);
                }).frames(1).waitUntil("the 1.0x play walks to the table's end", () -> !screen().playback().playing())
                .action("measure the 1.0x play's clock span", context -> oneSpan = screen().clock().time() - playStart)
                .check(
                    "a 1.0x play lasts the sum of its holds and dwells, measured in the screen's own ticks",
                    () -> oneSpan >= paceTicks(1.0D) && oneSpan <= paceTicks(1.0D) + 5.0D,
                    () -> "span=" + oneSpan + " pace=" + paceTicks(1.0D)
                ).action("click the track at the key's ceiling", context -> clickTrack(context, 1.0D)).frames(2)
                .check("the same stage's dwell at 4.0x is a quarter of its 1.0x dwell, rounded up", () -> {
                    int one = screen().playback().revealSpan(0, cadence(1.0D));
                    int four = screen().playback().revealSpan(0, cadence(4.0D));
                    return four == Math.max(1, (int) Math.ceil(one / 4.0D))
                            && screen().playback().pauseSpan(cadence(4.0D)) * 4
                                    >= screen().playback().pauseSpan(cadence(1.0D))
                            && screen().playback().pauseSpan(cadence(4.0D)) == 1;
                },
                    () -> "dwell1=" + screen().playback().revealSpan(0, cadence(1.0D)) + " dwell4="
                            + screen().playback().revealSpan(0, cadence(4.0D)) + " pause1="
                            + screen().playback().pauseSpan(cadence(1.0D)) + " pause4="
                            + screen().playback().pauseSpan(cadence(4.0D))
                ).action("start the 4.0x replay and note the clock", context -> {
                    fourStart = screen().clock().time();
                    pressSpace(context);
                }).frames(1).waitUntil("the 4.0x replay walks to the table's end", () -> !screen().playback().playing())
                .action("measure the 4.0x play's clock span", context -> fourSpan = screen().clock().time() - fourStart)
                .check(
                    "a 4.0x play spends about a quarter of the 1.0x play's clock",
                    () -> fourSpan >= paceTicks(4.0D)
                            && fourSpan <= paceTicks(4.0D) + 5.0D
                            && fourSpan * 2.0D < oneSpan,
                    () -> "span1=" + oneSpan + " pace1=" + paceTicks(1.0D) + " span4=" + fourSpan + " pace4="
                            + paceTicks(4.0D)
                ).action("restore the speed the suite arrived with", context -> {
                    ClientConfig.setAnimationSpeed(speedBefore);
                    ClientConfig.saveAnimationSpeed();
                }).frames(1)
                .check(
                    "the one key is back where the suite found it",
                    () -> Math.abs(ClientConfig.animationSpeed() - speedBefore) < eps,
                    () -> "key=" + ClientConfig.animationSpeed() + " arrived=" + speedBefore
                )
                .timelineRecord(
                    "screen-speed-pace",
                    () -> "span1=" + oneSpan + ", pace1=" + paceTicks(1.0D) + ", span4=" + fourSpan + ", pace4="
                            + paceTicks(4.0D)
                ).action("Esc closes the page", context -> {
                    var open = context.screenOrThrow();
                    context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }).waitUntil("the page closed", () -> Minecraft.getInstance().screen == null).frames(2);

        PreviewScenarioSupport.cleanup(build);
    }

    /** The whole play's span at one speed from the page's own table: every dwell plus every hold (§7.5). */
    private static int paceTicks(double speed) {
        StagePlayback playback = screen().playback();
        StagePlayback.Cadence cadence = cadence(speed);
        int total = 0;
        for (int stage = 0; stage < playback.stageCount(); stage++) {
            total += playback.revealSpan(stage, cadence) + playback.pauseSpan(cadence);
        }
        return total;
    }

    private static StagePlayback.Cadence cadence(double speed) {
        return StagePlayback.Cadence.of(speed, ClientConfig.animationEnabled());
    }

    /** The config file's text as the last save left it; a missing file reads empty. */
    private static String savedSpeed() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("structurecumulus-client.toml");
        try {
            return Files.readString(file);
        } catch (IOException missing) {
            return "";
        }
    }

    private static void pressSpace(TestContext context) {
        var open = context.screenOrThrow();
        context.driver().keyDown(open, GLFW.GLFW_KEY_SPACE, 0, 0);
        context.driver().keyUp(open, GLFW.GLFW_KEY_SPACE, 0, 0);
    }

    /** The title strip carries no panel and no widget, so a parked pointer shows no hover (§4.5). */
    private static void park(TestContext context) {
        PreviewScreen open = screen();
        context.driver().placeCursor(200.0F, 8.0F);
        context.driver().moveTo(open, 200.0F, 8.0F);
    }

    /** A press and release on the track: the click's own path, release included, so the write lands. */
    private static void clickTrack(TestContext context, double fraction) {
        ScreenLayout.Rect track = screen().sliderTrack();
        if (track == null) {
            throw new IllegalStateException("the page carries no speed slider: " + screen().buttonLabels());
        }
        float x = (float) (track.x() + fraction * track.width());
        float y = track.y();
        PreviewScreen open = screen();
        context.driver().mouseDown(open, x, y, 0);
        context.driver().mouseUp(open, x, y, 0);
    }

    /** A press on the track, one drag to a fraction (past either end for the clamps), then the release. */
    private static void dragTrack(TestContext context, double fraction) {
        ScreenLayout.Rect track = screen().sliderTrack();
        if (track == null) {
            throw new IllegalStateException("the page carries no speed slider: " + screen().buttonLabels());
        }
        PreviewScreen open = screen();
        float fromX = track.x() + track.width() / 2.0F;
        float toX = (float) (track.x() + fraction * track.width());
        float y = track.y();
        context.driver().mouseDown(open, fromX, y, 0);
        context.driver().dragTo(open, toX, y, 0, toX - fromX, 0.0F);
        context.driver().mouseUp(open, toX, y, 0);
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

    private static boolean inside(ScreenLayout.Rect outer, ScreenLayout.Rect inner) {
        return inner.x() >= outer.x()
                && inner.y() >= outer.y()
                && inner.x() + inner.width() <= outer.x() + outer.width()
                && inner.y() + inner.height() <= outer.y() + outer.height();
    }

    private static boolean overlaps(ScreenLayout.Rect first, ScreenLayout.Rect second) {
        return first.x() < second.x() + second.width()
                && second.x() < first.x() + first.width()
                && first.y() < second.y() + second.height()
                && second.y() < first.y() + first.height();
    }

    private static PreviewScreen screen() {
        return (PreviewScreen) Minecraft.getInstance().screen;
    }
}
