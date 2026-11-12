package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * The pack tour beside the build story: the staged table and its banner, the playback and its tiered
 * bar, the cinema camera, the variant switch and the preset re-open, all through the production
 * commands and keys. It asserts models, never pixels, and the screenshots are the demo's presentation.
 *
 * <p>Boundary: the tour shows the pack's viewing surfaces. It confirms nothing, so it writes no world
 * and no preference memory; the one store it touches is one preset, which it deletes again. The build
 * story is {@code preview.demo}, and the E7 GameTests own the apply/preset safety laws.
 */
@ClientScenario(
    name = "preview.demo.tour",
    group = "demo",
    tags = {"preview", "demo", "tour"},
    timeoutMs = 150_000,
    scenarioTimeoutMs = 300_000
)
public final class PreviewDemoTourScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final String floorDescription = "the brick floor";
    private static final String floorHint = "lay the brick ring before the corners";
    private static final String documentDescription = "the staged demo: a brick floor with a chimney above it";
    /** The next name the preset store hands out in a throwaway world (design 15 §13.2). */
    private static final String presetName = "preset-1";

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).runCommand("cumulusvisual hud hide").runCommand(
            "cumulusvisual tutorial hide"
        ).action(
            // the shot frames the preview HUD; the build story's command output is not part of it
            "park the previous act's command output",
            context -> Minecraft.getInstance().gui.getChat().clearMessages(true)
        ).log(
            "preview.demo.tour: the staged table and its banner, the playback bar, the cinema, the variant switch, the preset re-open"
        );

        build = stagesAct(build);
        build = variantsAct(build);
        build = presetAct(build);
        PreviewScenarioSupport.cleanup(build).runCommand("cumulusvisual hud show");
    }

    /** The staged document: its table, the cell step, the played-through bar, the banner and C. */
    private static ScenarioBuilder stagesAct(ScenarioBuilder build) {
        build = PreviewScenarioSupport
                .runAsPlayer(
                    build,
                    "run /cumulus preview on the staged document as the player",
                    "cumulus preview " + staged.text() + " 0 100 0"
                ).waitUntil("the two-stage session synced", PreviewScenarioSupport::synced).check(
                    "the document declares two stages",
                    () -> PreviewStageProbe.stageCount() == 2,
                    () -> Integer.toString(PreviewStageProbe.stageCount())
                ).check(
                    "the fresh bar reads zero of twelve",
                    () -> PreviewProbe.hudHas("progress 0/12 (0%)"),
                    () -> PreviewProbe.hudLines().toString()
                ).check(
                    "the title line carries the document's description",
                    () -> PreviewProbe
                            .hudHas("preview: structurecumulus:preview_stages · 0/12 · " + documentDescription),
                    () -> PreviewProbe.hudLines().toString()
                ).check(
                    "the stage line names the first part and its hint",
                    () -> PreviewProbe.hudHas("stage 1/2: structurecumulus:base · " + floorHint),
                    () -> PreviewProbe.hudLines().toString()
                )
                .waitUntil(
                    "the opening stage finished its reveal",
                    () -> PreviewStageProbe.revealedCells(0) == PreviewStageProbe.stageCells(0)
                ).waitUntil("the session fade-in settled", () -> !PreviewProbe.fading()).frames(2)
                .screenshot("01-stages-open").checkCaptured("01-stages-open")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.stepCell))
                .waitUntil(
                    "the step opened the next stage with its first cell",
                    () -> PreviewStageProbe.selectedStage() == 1 && PreviewStageProbe.revealedCells(1) == 1
                ).waitUntil("the stage advance settled", () -> !PreviewCinematicProbe.stageAdvanceActive())
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.playPause))
                .waitUntil("the playback is running", PreviewStageProbe::playing)
                .waitUntil("the playback revealed a further cell", () -> PreviewStageProbe.revealedCells(1) > 1)
                .timelineRecord(
                    "preview-demo-tour-bar",
                    () -> "stages=" + PreviewStageProbe.stageCount() + ", selected=" + PreviewStageProbe.selectedStage()
                            + ", revealed=" + PreviewStageProbe.revealedCells(1) + "/" + PreviewStageProbe.stageCells(1)
                            + ", bar=" + hudLine("progress ")
                ).screenshot("02-stages-playing").frames(2).checkCaptured("02-stages-playing")
                .waitUntil(
                    "the playback played the second stage through",
                    () -> PreviewStageProbe.revealedCells(1) == PreviewStageProbe.stageCells(1)
                ).waitUntil("the playback stopped at the table's end", () -> !PreviewStageProbe.playing())
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.prevStage))
                .waitUntil("the cursor returned to the first stage", () -> PreviewStageProbe.selectedStage() == 0)
                .check(
                    "the banner names the stage and carries the bay's description",
                    () -> PreviewStageProbe.bannerLines().equals(List.of("structurecumulus:base", floorDescription)),
                    () -> PreviewStageProbe.bannerLines().toString()
                ).waitUntil("the stage advance settled", () -> !PreviewCinematicProbe.stageAdvanceActive()).frames(2)
                .screenshot("03-stage-banner").checkCaptured("03-stage-banner")
                // C enters the cinema on the open session; the second C is the hand-back
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.cinematic))
                .waitUntil("C detached the frame", PreviewCinematicProbe::active).frames(2)
                .check("the frame sits on the program's sample", PreviewCinematicProbe::cameraOnShot).ticks(9)
                .check(
                    "the letterbox faded in",
                    () -> PreviewCinematicProbe.letterboxAlpha() >= 0.99F,
                    () -> Float.toString(PreviewCinematicProbe.letterboxAlpha())
                ).screenshot("04-cinematic-orbit").frames(2).checkCaptured("04-cinematic-orbit")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.cinematic))
                .waitUntil("the second C handed the frame back", () -> !PreviewCinematicProbe.active()).frames(3)
                .check("the player's own camera is back", PreviewCinematicProbe::cameraReturnedToPlayer).timelineRecord(
                    "preview-demo-tour-cinema",
                    () -> "active=" + PreviewCinematicProbe.active() + ", program="
                            + PreviewCinematicProbe.programTicks()
                );
        return PreviewScenarioSupport.cancel(build);
    }

    /** The variant group: N re-opens the session on the next member and K stores the pose. */
    private static ScenarioBuilder variantsAct(ScenarioBuilder build) {
        int[] pose = new int[4];
        return PreviewScenarioSupport
                .runAsPlayer(
                    build,
                    "run /cumulus preview on the coil group as the player",
                    "cumulus preview " + PreviewScenarioSupport.coilBasic.text() + " 0 100 0"
                ).waitUntil("the coil session synced", PreviewScenarioSupport::synced)
                .check(
                    "the session carries the two-member group in declaration order",
                    () -> PreviewProbe.variants() != null && PreviewProbe.variants().members().size() == 2,
                    () -> String.valueOf(PreviewProbe.variants())
                )
                .check(
                    "the HUD names the first member",
                    () -> PreviewProbe.hudHas("variant 1/2: 基础线圈 · group structurecumulus:coil"),
                    () -> PreviewProbe.hudLines().toString()
                ).waitUntil("the variant fade-in settled", () -> !PreviewProbe.fading()).frames(2)
                .screenshot("05-variant-basic").checkCaptured("05-variant-basic")
                .action("record the coil pose before the switch", context -> recordPose(pose))
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.nextVariant))
                .waitUntil(
                    "N switched the session to the reinforced tier",
                    () -> PreviewScenarioSupport.coilReinforced.equals(PreviewProbe.blueprintId())
                )
                .check(
                    "the switch keeps the recorded pose",
                    () -> samePose(pose),
                    () -> "stored=" + describe(pose) + " now=" + String.valueOf(PreviewProbe.siting())
                )
                .check(
                    "the HUD names the second member",
                    () -> PreviewProbe.hudHas("variant 2/2: 强化线圈 · group structurecumulus:coil"),
                    () -> PreviewProbe.hudLines().toString()
                ).waitUntil("the variant crossfade settled", () -> !PreviewProbe.fading()).frames(2)
                .screenshot("06-variant-reinforced").checkCaptured("06-variant-reinforced")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.savePreset))
                .waitUntil(
                    "K stored the current context under the next preset name",
                    () -> PreviewProbe.hasMarker("preset " + presetName + " saved")
                ).timelineRecord(
                    "preview-demo-tour-variant",
                    () -> "blueprint=" + PreviewProbe.blueprintId() + ", ghosts=" + PreviewProbe.ghostCount()
                            + ", preset=" + presetName
                );
    }

    /** The preset: close the session, re-open it through the production verb, then delete it again. */
    private static ScenarioBuilder presetAct(ScenarioBuilder build) {
        int[] pose = new int[4];
        build = build.action("record the reinforced pose the preset stores", context -> recordPose(pose));
        build = PreviewScenarioSupport.cancel(build);
        build = PreviewScenarioSupport
                .runAsPlayer(
                    build,
                    "re-open the stored context with /cumulus build preset preview",
                    "cumulus build preset preview " + presetName
                ).waitUntil("the preset re-opened a session", PreviewProbe::hasSession)
                .waitUntil("the re-opened session synced", PreviewScenarioSupport::synced)
                .check(
                    "the preset re-opened the same blueprint",
                    () -> PreviewScenarioSupport.coilReinforced.equals(PreviewProbe.blueprintId()),
                    () -> String.valueOf(PreviewProbe.blueprintId())
                )
                .check(
                    "the preset re-opened the stored pose",
                    () -> samePose(pose),
                    () -> "stored=" + describe(pose) + " now=" + String.valueOf(PreviewProbe.siting())
                ).waitUntil("the preset fade-in settled", () -> !PreviewProbe.fading()).frames(2)
                .screenshot("07-preset-reopened").checkCaptured("07-preset-reopened").timelineRecord(
                    "preview-demo-tour-preset",
                    () -> "blueprint=" + PreviewProbe.blueprintId() + ", ghosts=" + PreviewProbe.ghostCount()
                            + ", session=" + PreviewProbe.hasSession()
                );
        build = PreviewScenarioSupport.cancel(build);
        return PreviewScenarioSupport
                .runAsPlayer(build, "delete the demo preset again", "cumulus build preset delete " + presetName)
                .waitUntilServer("the preset store is empty again", PreviewDemoTourScenario::presetDeleted);
    }

    /** The first HUD line with a prefix, or an empty string; the timeline records the live numbers. */
    private static String hudLine(String prefix) {
        for (String line : PreviewProbe.hudLines()) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return "";
    }

    /** Copies the live siting into the four-slot form the later comparison reads. */
    private static void recordPose(int[] into) {
        Siting siting = PreviewProbe.siting();
        if (siting == null) {
            return;
        }
        into[0] = siting.origin().x();
        into[1] = siting.origin().y();
        into[2] = siting.origin().z();
        into[3] = siting.placement().orientation().ordinal();
    }

    private static boolean samePose(int[] pose) {
        Siting siting = PreviewProbe.siting();
        return siting != null
                && siting.origin().x() == pose[0]
                && siting.origin().y() == pose[1]
                && siting.origin().z() == pose[2]
                && siting.placement().orientation().ordinal() == pose[3];
    }

    private static String describe(int[] pose) {
        return "(" + pose[0] + "," + pose[1] + "," + pose[2] + ") orientation " + pose[3];
    }

    /** Reads the production preset store on the server thread: the demo's preset is gone. */
    private static boolean presetDeleted() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return false;
        }
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        var player = players.get(0);
        return PreferencesSavedData.get(player.serverLevel()).preset(player.getUUID(), presetName) == null;
    }
}
