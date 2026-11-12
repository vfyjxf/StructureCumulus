package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

/**
 * The whole player story on one camera: the production {@code /cumulus preview} command opens the
 * ghost, the turn and flip keys re-pose it, and four confirms turn the ghosts into real stairs.
 *
 * <p>The demo isolates the preference memory on both sides: it forgets the probe's remembered choices
 * before the open (so an earlier session's confirmed pose cannot reorder this session's candidates)
 * and again after the last confirm (so this story cannot reorder a later scenario's candidates). The
 * memory is the production one and the purge is the production {@code /cumulus prefs forget}, never a
 * test-only shortcut.
 */
@ClientScenario(
    name = "preview.demo",
    group = "demo",
    tags = {"preview", "demo"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class PreviewDemoScenario {

    /** The probe is a 2x1x2 of oak stairs; a turn or flip keeps it inside this 3x3 footprint. */
    private static final int[] offsets = {-1, 0, 1};

    public void define(ScenarioBuilder build) {
        int[] turn = {-1};
        build = build.guiScale(2).hideGui(false).runCommand("gamemode creative @a").runCommand("cumulusvisual hud hide")
                .runCommand("cumulusvisual tutorial hide").fill(-8, 99, -8, 9, 99, 9, Blocks.STONE.defaultBlockState())
                .fill(-8, 100, -8, 9, 104, 9, Blocks.AIR.defaultBlockState()).teleportPlayer(-3.5, 100.0, 4.5)
                .yawPitch(-128.0F, 22.0F).awaitClientChunk(0, 0)
                .log("preview.demo: open via /cumulus preview, turn, flip, then four confirms");

        build = purgeMemory(build, "forget the probe's remembered choices before the story")
                .waitUntilServer("the probe's memory is empty", PreviewDemoScenario::memoryIsClean);
        build = openThroughCommand(build)
                .waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .check("the production command opened a session", PreviewProbe::hasSession)
                .check("the probe previews as its four stairs", () -> PreviewProbe.ghostCount() == 4)
                .check(
                    "the purged memory opens the pose unmirrored",
                    () -> !PreviewProbe.siting().placement().orientation().isMirror()
                )
                .check(
                    "the status box names the probe and the counter",
                    () -> PreviewProbe.hudHas("preview: structurecumulus:preview_probe · 0/4")
                ).check("the status box carries the key legend", () -> PreviewProbe.hudHas("keys: "))
                .waitUntil("the JEI catalog synced", PreviewJeiProbe::catalogReady)
                .timelineRecord("catalog", () -> PreviewJeiProbe.catalogIds().toString())
                .waitUntil("the session fade-in settled", () -> !PreviewProbe.fading()).frames(2)
                .screenshot("01-preview-open").checkCaptured("01-preview-open").runCommand("cumulusvisual hud show")
                .frames(2).screenshot("01b-preview-open-with-visual-hud")
                .checkCaptured("01b-preview-open-with-visual-hud").runCommand("cumulusvisual hud hide").frames(2)
                .action(
                    "record the opening turn",
                    context -> turn[0] = PreviewProbe.siting().placement().orientation().turn().ordinal()
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.turnCw))
                .waitUntil(
                    "the server re-synced a different pose",
                    () -> PreviewProbe.siting().placement().orientation().turn().ordinal() != turn[0]
                )
                .check(
                    "the turned pose stays upright",
                    () -> PreviewProbe.siting().placement().orientation().tilt() == Tilt.level
                ).check("the ghost set follows the pose", () -> PreviewProbe.ghostCount() == 4).frames(4)
                .screenshot("02-preview-turned").checkCaptured("02-preview-turned")
                .action(
                    "record the turned heading",
                    context -> turn[0] = PreviewProbe.siting().placement().orientation().turn().ordinal()
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.flip))
                .waitUntil(
                    "the server re-synced the mirrored pose",
                    () -> PreviewProbe.siting().placement().orientation().isMirror()
                )
                .check(
                    "the flip keeps the turn",
                    () -> PreviewProbe.siting().placement().orientation().turn().ordinal() == turn[0]
                ).frames(4).screenshot("03-preview-flipped").checkCaptured("03-preview-flipped");

        // one build run applies one repair, so the four-cell probe needs four confirms (design 14 §10)
        for (int press = 1; press <= 4; press++) {
            int expected = press;
            build = build.pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.confirm)).waitUntil(
                "confirm " + expected + " built one more expected cell",
                () -> builtStairs() >= expected
            );
        }
        build.waitUntil("the final sync reports the committed build", () -> PreviewProbe.hasMarker("build committed"))
                .check("four oak stairs stand where the ghosts were", () -> builtStairs() == 4)
                .waitUntil("the client drops the closed session", () -> !PreviewProbe.hasSession()).frames(6)
                .screenshot("04-preview-built").checkCaptured("04-preview-built").teleportPlayer(-1.5, 100.0, 3.5)
                .yawPitch(-135.0F, 28.0F).frames(8).screenshot("04b-preview-built-close")
                .checkCaptured("04b-preview-built-close");
        build = purgeMemory(build, "forget the probe's remembered choices after the story")
                .waitUntilServer("the demo leaves no remembered choice behind", PreviewDemoScenario::memoryIsClean);
        build.timelineRecord(
            "preview-demo",
            () -> "ghosts=" + PreviewProbe.ghostCount() + ", stairs=" + builtStairs() + ", session="
                    + PreviewProbe.hasSession()
        ).fill(-8, 99, -8, 9, 99, 9, Blocks.AIR.defaultBlockState())
                .fill(-8, 100, -8, 9, 104, 9, Blocks.AIR.defaultBlockState()).runCommand("cumulusvisual hud show");
    }

    /**
     * Forgets the probe's remembered siting choices and its part pins through the production verb, so
     * a session's frequency-weighted memory cannot survive into or out of this story (design 15 §9).
     */
    private static ScenarioBuilder purgeMemory(ScenarioBuilder build, String label) {
        return PreviewScenarioSupport
                .runAsPlayer(build, label, "cumulus prefs forget " + PreviewScenarioSupport.probe.text());
    }

    /** Reads the production memory on the server thread: the blueprint has no history and no pin. */
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
        return store.preferences(player.getUUID()).history(PreviewScenarioSupport.probe) == null
                && store.partPins(player.getUUID(), PreviewScenarioSupport.probe).isEmpty();
    }

    /** Runs the real command node as the local player; the dev twin is not part of this story. */
    private static ScenarioBuilder openThroughCommand(ScenarioBuilder build) {
        return PreviewScenarioSupport.runAsPlayer(
            build,
            "run /cumulus preview as the player",
            "cumulus preview " + PreviewScenarioSupport.probe.text() + " 0 100 0"
        );
    }

    /** Counts the probe's stairs around the origin; the pose only moves them inside this square. */
    private static int builtStairs() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return 0;
        }
        BlockPos origin = PreviewScenarioSupport.origin;
        int stairs = 0;
        for (int x : offsets) {
            for (int z : offsets) {
                if (level.getBlockState(origin.offset(x, 0, z)).is(Blocks.OAK_STAIRS)) {
                    stairs++;
                }
            }
        }
        return stairs;
    }
}
