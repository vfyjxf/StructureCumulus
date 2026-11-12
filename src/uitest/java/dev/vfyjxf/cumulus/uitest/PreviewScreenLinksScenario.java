package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout;
import dev.vfyjxf.cumulus.mc.preview.jei.JeiPreview;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import dev.vfyjxf.cumulus.mc.preview.net.SitingCandidate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The five links end to end (design 17 §9): the JEI button, the client command, the world key, ghost
 * to world with the screen's heading, and the in-screen variant switch. One route per entry point.
 */
@ClientScenario(
    name = "screen.links",
    group = "screen",
    tags = {"screen", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewScreenLinksScenario {

    private static final NamespacedId probe = PreviewScenarioSupport.probe;
    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final NamespacedId coil = PreviewScenarioSupport.coilBasic;
    private static final NamespacedId coilReinforced = PreviewScenarioSupport.coilReinforced;

    private static float yawBeforeSwitch;
    private static float pitchBeforeSwitch;
    private static float targetXBeforeSwitch;
    private static float targetYBeforeSwitch;
    private static float targetZBeforeSwitch;
    private static float zoomBeforeSwitch;
    private static float explodeBeforeSwitch;
    private static Heading eastReading;
    private static Heading southReading;
    private static boolean bareIdFallsThrough;
    private static boolean incompleteScreenFallsThrough;
    private static boolean screenOwnedWithId;
    private static int openedCode;
    private static int absentCode;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build)
                .log("screen.links: the O key, the client command, the JEI button, the variant switch, ghost to world");

        build = purgeMemory(build, "forget the probe's remembered choices before the links run");
        build = build.waitUntilServer("the probe has no remembered choice", PreviewScreenLinksScenario::memoryIsClean);

        build = PreviewScenarioSupport.open(build).waitUntil("a world session is open", PreviewScenarioSupport::synced)
                .check(
                    "the key table stays unique with the screen key, and it is O",
                    () -> PreviewCinematicProbe.keyConflict() == null && openScreenKey() == GLFW.GLFW_KEY_O,
                    () -> "conflict=" + PreviewCinematicProbe.keyConflict() + " key=" + openScreenKey()
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.openScreen))
                .waitUntil(
                    "O opens the screen on the session's blueprint",
                    () -> Minecraft.getInstance().screen instanceof PreviewScreen
                ).frames(3)
                .check(
                    "the page is the session's id, read from the catalogue",
                    () -> screen().scene().cellCount() == 4 && screen().variantCount() == 1,
                    () -> "cells=" + screen().scene().cellCount() + " variants=" + screen().variantCount()
                ).check("the session stays exactly as it is behind the screen", PreviewProbe::hasSession)
                .action("Esc closes the screen the key opened", PreviewScreenLinksScenario::escape).frames(2)
                .waitUntil("the world is the frame again", () -> Minecraft.getInstance().screen == null)
                .check("closing lands in the world and keeps the session", PreviewProbe::hasSession);

        build = build
                .action(
                    "type the client command as a chat command",
                    context -> Minecraft.getInstance().getConnection()
                            .sendCommand("cumulus preview screen " + staged.text())
                )
                .waitUntil(
                    "the command opens the staged page",
                    () -> Minecraft.getInstance().screen instanceof PreviewScreen
                ).frames(3)
                .check(
                    "the command's page is the id it named, not the session's",
                    () -> screen().scene().cellCount() == 12 && screen().playback().stageCount() == 2,
                    () -> "cells=" + screen().scene().cellCount()
                ).action("Esc closes the command's page", PreviewScreenLinksScenario::escape).frames(2)
                .waitUntil("the world is the frame again", () -> Minecraft.getInstance().screen == null)
                .action("ask the client tree what it owns", context -> {
                    // an id the client tree does not carry is handed to the server (design 17 §9.2)
                    bareIdFallsThrough = !ClientCommandHandler.runCommand("cumulus preview preview_probe");
                    // an incomplete "screen" parses nowhere either, so NeoForge forwards it as unknown
                    incompleteScreenFallsThrough = !ClientCommandHandler.runCommand("cumulus preview screen");
                    // with an id the client literal owns the command and opens the page locally
                    screenOwnedWithId = ClientCommandHandler.runCommand("cumulus preview screen " + probe.text());
                    openedCode = execute("cumulus preview screen " + staged.text());
                    absentCode = execute("cumulus preview screen structurecumulus:no_such_page");
                }).frames(2)
                .waitUntil(
                    "the exit-code probe opened the staged page",
                    () -> Minecraft.getInstance().screen instanceof PreviewScreen
                )
                .check(
                    "the client tree owns screen with an id and opens with 1, an absent id answers 0",
                    () -> bareIdFallsThrough
                            && incompleteScreenFallsThrough
                            && screenOwnedWithId
                            && openedCode == 1
                            && absentCode == 0,
                    () -> "bareId=" + bareIdFallsThrough + " incomplete=" + incompleteScreenFallsThrough + " owned="
                            + screenOwnedWithId + " opened=" + openedCode + " absent=" + absentCode
                ).action("Esc closes the exit-code page", PreviewScreenLinksScenario::escape).frames(2)
                .waitUntil("the world is the frame again", () -> Minecraft.getInstance().screen == null);

        build = PreviewScenarioSupport.cancel(build).frames(2);

        build = build.action("page the JEI category to the coil group and select its second member", context -> {
            JeiPreview.show(coil);
            JeiPreview.cycleVariant(coil, 1);
        }).frames(4)
                .check(
                    "the JEI page's selection is the reinforced coil",
                    () -> coilReinforced.equals(JeiPreview.selectedBlueprintId(coil)),
                    () -> String.valueOf(JeiPreview.selectedBlueprintId(coil))
                ).action("press open in screen", context -> JeiPreview.openScreen(coil))
                .waitUntil(
                    "the JEI button opens the screen",
                    () -> Minecraft.getInstance().screen instanceof PreviewScreen
                ).frames(3)
                .check(
                    "the JEI button opens the selected member, its own page and no session",
                    () -> screen().variantIndex() == 1
                            && screen().scene().cells().get(0).ghost().block().path().equals("iron_block")
                            && "variant 2/2: \u5F3A\u5316\u7EBF\u5708".equals(screen().variantLine()),
                    () -> "index=" + screen().variantIndex() + " line=" + screen().variantLine()
                )
                .check(
                    "the control strip carries the variant row on a group page",
                    () -> screen().buttonLabels().containsAll(List.of("\u2039 variant", "variant \u203A")),
                    () -> String.valueOf(screen().buttonLabels())
                ).action("record the camera and the explode state before the switch", context -> {
                    yawBeforeSwitch = screen().camera().yaw();
                    pitchBeforeSwitch = screen().camera().pitch();
                    targetXBeforeSwitch = screen().camera().targetX();
                    targetYBeforeSwitch = screen().camera().targetY();
                    targetZBeforeSwitch = screen().camera().targetZ();
                    zoomBeforeSwitch = screen().camera().zoom();
                    explodeBeforeSwitch = screen().explodeT();
                }).action("press variant next", context -> click("variant \u203A", context)).frames(3)
                .check(
                    "the switch wraps to the first member and keeps the camera and the explode state",
                    () -> screen().variantIndex() == 0
                            && screen().scene().cells().get(0).ghost().block().path().equals("copper_block")
                            && screen().camera().yaw() == yawBeforeSwitch
                            && screen().camera().pitch() == pitchBeforeSwitch
                            && screen().camera().targetX() == targetXBeforeSwitch
                            && screen().camera().targetY() == targetYBeforeSwitch
                            && screen().camera().targetZ() == targetZBeforeSwitch
                            && screen().camera().zoom() == zoomBeforeSwitch
                            && screen().explodeT() == explodeBeforeSwitch,
                    () -> "index=" + screen().variantIndex() + " yaw=" + screen().camera().yaw() + " zoom="
                            + screen().camera().zoom() + " line=" + screen().variantLine()
                )
                .check(
                    "the title names the member in the world HUD's own words",
                    () -> "variant 1/2: \u57FA\u7840\u7EBF\u5708".equals(screen().variantLine())
                ).screenshot("screen-links-variant").frames(2).checkCaptured("screen-links-variant")
                .action("press variant previous", context -> click("\u2039 variant", context)).frames(2)
                .check(
                    "the switch wraps the other way without re-opening",
                    () -> screen().variantIndex() == 1,
                    () -> "index=" + screen().variantIndex()
                ).action("Esc closes the JEI page", PreviewScreenLinksScenario::escape).frames(2)
                .waitUntil("the world is the frame again", () -> Minecraft.getInstance().screen == null)
                .check("the JEI page opened and closed with no session at all", () -> !PreviewProbe.hasSession());

        build = PreviewScenarioSupport.open(build)
                .waitUntil("the probe session is open again", PreviewScenarioSupport::synced)
                .action("open the probe page", context -> PreviewScreen.open(probe)).frames(3)
                .waitUntil("the page is open", () -> Minecraft.getInstance().screen instanceof PreviewScreen)
                .key(GLFW.GLFW_KEY_3, 0).frames(1)
                .check(
                    "the side snapshot reads east",
                    () -> screen().heading() == Heading.east,
                    () -> String.valueOf(screen().heading())
                ).action("press ghost to world", context -> click("ghost to world", context)).frames(2)
                .waitUntil("the click closes the screen", () -> Minecraft.getInstance().screen == null)
                .waitUntil("the server opened the session at the crosshair", PreviewScenarioSupport::synced).frames(2)
                .check(
                    "the heading reaches the session's cycle: the first candidate faces the screen's side",
                    () -> firstCandidateFacing() == Heading.east,
                    () -> "facing=" + firstCandidateFacing() + " candidates=" + PreviewProbe.candidateCount()
                ).action("record the east reading", context -> eastReading = firstCandidateFacing());

        build = build.action("open the probe page again", context -> PreviewScreen.open(probe)).frames(3)
                .waitUntil("the page is open", () -> Minecraft.getInstance().screen instanceof PreviewScreen)
                .key(GLFW.GLFW_KEY_1, 0).frames(1)
                .check(
                    "the front snapshot reads south",
                    () -> screen().heading() == Heading.south,
                    () -> String.valueOf(screen().heading())
                ).action("press ghost to world again", context -> click("ghost to world", context)).frames(2)
                .waitUntil("the click closes the screen", () -> Minecraft.getInstance().screen == null)
                .waitUntil("the server re-opened the session", PreviewScenarioSupport::synced).frames(2)
                .check(
                    "a different camera sends a different heading, so the payload decides",
                    () -> firstCandidateFacing() == Heading.south && eastReading == Heading.east,
                    () -> "facing=" + firstCandidateFacing() + " east=" + eastReading
                ).action("record the south reading", context -> southReading = firstCandidateFacing())
                .screenshot("screen-links").frames(2).checkCaptured("screen-links").timelineRecord(
                    "screen-links",
                    () -> "east=" + eastReading + ", south=" + southReading + ", candidates="
                            + PreviewProbe.candidateCount()
                );

        build = PreviewScenarioSupport.cancel(build);
        build = purgeMemory(build, "forget the probe's remembered choices after the links run");
        build = PreviewScenarioSupport.cleanup(build);
    }

    /** The production client dispatcher's own execute: the source and the tree the chat path uses. */
    private static int execute(String command) {
        try {
            return ClientCommandHandler.getDispatcher().execute(command, ClientCommandHandler.getSource());
        } catch (Exception failure) {
            return -1;
        }
    }

    /** The first candidate's declared-front heading, read the scorer's own way (design 15 §9). */
    private static @Nullable Heading firstCandidateFacing() {
        PreviewSyncPayload session = PreviewProbe.session();
        if (session == null || session.candidates().isEmpty()) {
            return null;
        }
        SitingCandidate first = session.candidates().get(0);
        return first.siting().placement().orientation().apply(Heading.north);
    }

    private static int openScreenKey() {
        KeyMapping mapping = PreviewKeys.binding(PreviewKeys.Action.openScreen);
        return mapping == null ? -1 : mapping.getDefaultKey().getValue();
    }

    private static void escape(TestContext context) {
        var open = context.screenOrThrow();
        context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
        context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
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

    private static ScenarioBuilder purgeMemory(ScenarioBuilder build, String label) {
        return PreviewScenarioSupport.runAsPlayer(build, label, "cumulus prefs forget " + probe.text());
    }

    private static boolean memoryIsClean() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return false;
        }
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        var player = players.get(0);
        PreferencesSavedData store = PreferencesSavedData.get(player.serverLevel());
        return store.preferences(player.getUUID()).history(probe) == null
                && store.partPins(player.getUUID(), probe).isEmpty();
    }

    private static PreviewScreen screen() {
        return (PreviewScreen) Minecraft.getInstance().screen;
    }
}
