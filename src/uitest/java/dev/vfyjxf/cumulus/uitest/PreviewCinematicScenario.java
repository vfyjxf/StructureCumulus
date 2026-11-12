package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The cinema mode on a two-stage document: C detaches the frame and flies the program, a stage
 * advance and a turntable animate the ghosts under it, the first movement key hands the frame back,
 * G and C kick each other, and a closing session leaves the camera with the player.
 */
@ClientScenario(
    name = "preview.cinematic",
    group = "cinematic",
    tags = {"preview", "cinematic"},
    timeoutMs = 150_000,
    scenarioTimeoutMs = 300_000
)
public final class PreviewCinematicScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).runCommand("cumulusvisual hud hide").log(
            "preview.cinematic: C in, the program flies, stage and turntable transitions, W hands back, G/C kick"
        ).check(
            "every preview action owns one key and C shadows no vanilla control",
            () -> PreviewCinematicProbe.keyConflict() == null,
            PreviewCinematicProbe::keyConflict
        ).check(
            "C is the cinema binding",
            () -> PreviewCinematicProbe.cinematicKey() == GLFW.GLFW_KEY_C,
            () -> Integer.toString(PreviewCinematicProbe.cinematicKey())
        );

        build = cinematicSession(build);
        PreviewScenarioSupport.cancel(build);
        build.runCommand("gamemode creative @a");
        PreviewScenarioSupport.cleanup(build).runCommand("cumulusvisual hud show");
    }

    private static ScenarioBuilder cinematicSession(ScenarioBuilder build) {
        double[] orbitRadius = {-1.0D};
        int[] programTicks = {-1};
        int[] oldTurn = {-1};
        double[] stageDistance = {-1.0D};
        return PreviewScenarioSupport.open(build, staged)
                .waitUntil("the two-stage session synced", PreviewScenarioSupport::synced)
                .check("no cinema mode before C", () -> !PreviewCinematicProbe.active())
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.cinematic))
                .waitUntil("C detached the camera", PreviewCinematicProbe::active).frames(2)
                .check(
                    "the mode claims its key and the creative hotbar save it shadows",
                    () -> !PreviewCinematicProbe.toolbarSavePending()
                ).check(
                    "the camera follows an anchor, not the player",
                    () -> PreviewCinematicProbe.cameraEntity() != null
                            && PreviewCinematicProbe.cameraEntity() != Minecraft.getInstance().player
                            && PreviewCinematicProbe.cameraEntity() == PreviewCinematicProbe.mainCameraEntity()
                ).check(
                    "the frame sits on the program's sample",
                    PreviewCinematicProbe::cameraOnShot,
                    () -> "camera=" + PreviewCinematicProbe.cameraPosition() + " shot="
                            + PreviewCinematicProbe.sampledShot()
                ).check("the frame turns with the sample", PreviewCinematicProbe::cameraOnShotAngles).check(
                    "the framing looks down at the subject, not at the sky",
                    () -> PreviewCinematicProbe.sampledShot() != null
                            && PreviewCinematicProbe.sampledShot().pitch() > 0.0F,
                    () -> PreviewCinematicProbe.sampledShot() == null
                            ? "none"
                            : Float.toString(PreviewCinematicProbe.sampledShot().pitch())
                ).check(
                    "the opening orbit looks at the subject's centre",
                    PreviewCinematicProbe::shotTargetsOverallCentre,
                    () -> "target="
                            + (PreviewCinematicProbe.sampledShot() == null
                                    ? "none"
                                    : PreviewCinematicProbe.sampledShot().target())
                            + " centre=" + PreviewCinematicProbe.overallCentre()
                ).action(
                    "record the orbit's radius",
                    context -> orbitRadius[0] = PreviewCinematicProbe.eyeDistanceToOverallCentre()
                ).ticks(3).check(
                    "the orbit keeps its distance while it turns",
                    () -> orbitRadius[0] > 0.0D
                            && Math.abs(PreviewCinematicProbe.eyeDistanceToOverallCentre() - orbitRadius[0])
                                    < orbitRadius[0] * 0.05D,
                    () -> orbitRadius[0] + " -> " + PreviewCinematicProbe.eyeDistanceToOverallCentre()
                ).action("record the program cursor", context -> programTicks[0] = PreviewCinematicProbe.programTicks())
                .ticks(4)
                .check(
                    "the program cursor advances every tick",
                    () -> PreviewCinematicProbe.programTicks() > programTicks[0] + 2,
                    () -> programTicks[0] + " -> " + PreviewCinematicProbe.programTicks()
                ).ticks(9).check(
                    "the letterbox fades in over eight ticks",
                    () -> PreviewCinematicProbe.letterboxAlpha() >= 0.99F,
                    () -> Float.toString(PreviewCinematicProbe.letterboxAlpha())
                ).check("the HUD keeps its key legend in the cinema", () -> PreviewProbe.hudHas("keys: "))
                .screenshot("01-cinematic-orbit").frames(2).checkCaptured("01-cinematic-orbit")
                // a cursor move reframes: the camera flies to the stage's own shot over twelve ticks
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.nextStage))
                .waitUntil("the cursor moved to the second stage", () -> PreviewStageProbe.selectedStage() == 1)
                .waitUntil("the stage advance started", PreviewCinematicProbe::stageAdvanceActive)
                .check(
                    "the stage advance spans twelve ticks",
                    () -> PreviewCinematicProbe.stageAdvanceDurationTicks() == 12,
                    () -> Integer.toString(PreviewCinematicProbe.stageAdvanceDurationTicks())
                ).check(
                    "the arriving stage starts small",
                    () -> PreviewCinematicProbe.ghostScale(firstGhost(1)) < 0.95F,
                    () -> Float.toString(PreviewCinematicProbe.ghostScale(firstGhost(1)))
                ).check(
                    "the leaving stage is still strong at the start",
                    () -> PreviewCinematicProbe.ghostAlpha(firstGhost(0)) > 0.8F,
                    () -> Float.toString(PreviewCinematicProbe.ghostAlpha(firstGhost(0)))
                ).check(
                    "the stage banner still announces the advance in the cinema",
                    () -> !PreviewStageProbe.bannerLines().isEmpty(),
                    () -> PreviewStageProbe.bannerLines().toString()
                ).check(
                    "the camera follows the cursor",
                    () -> PreviewCinematicProbe.focusedStage() == 1,
                    () -> Integer.toString(PreviewCinematicProbe.focusedStage())
                ).waitUntil("the advance is under way", () -> PreviewCinematicProbe.stageAdvanceTicks() >= 4).ticks(10)
                .waitUntil("the advance ended", () -> !PreviewCinematicProbe.stageAdvanceActive())
                .check(
                    "the arriving stage settled at full scale",
                    () -> PreviewCinematicProbe.ghostScale(firstGhost(1)) == 1.0F
                ).check(
                    "the leaving stage settled at the done alpha",
                    () -> PreviewCinematicProbe.ghostAlpha(firstGhost(0)) == 0.25F,
                    () -> Float.toString(PreviewCinematicProbe.ghostAlpha(firstGhost(0)))
                ).waitUntil(
                    "the camera flew to the stage's framing shot",
                    () -> PreviewCinematicProbe.shotTargetsStage(1)
                ).check(
                    "the framing shot is the stage's own centre",
                    () -> PreviewCinematicProbe.sampledShot() != null
                            && PreviewCinematicProbe.stageCentre(1) != null
                            && PreviewCinematicProbe.sampledShot().target()
                                    .distanceTo(PreviewCinematicProbe.stageCentre(1))
                                    < 1.0E-6D,
                    () -> "target="
                            + (PreviewCinematicProbe.sampledShot() == null
                                    ? "none"
                                    : PreviewCinematicProbe.sampledShot().target())
                            + " centre=" + PreviewCinematicProbe.stageCentre(1)
                ).screenshot("02-cinematic-stage").frames(2).checkCaptured("02-cinematic-stage")
                // a candidate switch swings the ghosts on the shortest arc; the model keeps the old pose
                .action(
                    "record the turn the swing leaves",
                    context -> oldTurn[0] = PreviewProbe.siting().placement().orientation().turn().ordinal()
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.turnCw))
                .waitUntil("the turn re-synced", () -> PreviewCinematicProbe.turntableActive())
                .check(
                    "the swing spans six ticks",
                    () -> PreviewCinematicProbe.turntableDurationTicks() == 6,
                    () -> Integer.toString(PreviewCinematicProbe.turntableDurationTicks())
                ).check(
                    "the baked model keeps the old pose until the last tick",
                    () -> PreviewCinematicProbe.turntableModelTurn() != null
                            && PreviewCinematicProbe.turntableModelTurn().ordinal() == oldTurn[0],
                    () -> "model=" + PreviewCinematicProbe.turntableModelTurn() + " old=" + oldTurn[0] + " payload="
                            + PreviewProbe.siting().placement().orientation().turn()
                ).check(
                    "the new ghost set is hidden while the old pose swings",
                    () -> PreviewCinematicProbe.ghostAlpha(firstGhost(1)) == 0.0F,
                    () -> Float.toString(PreviewCinematicProbe.ghostAlpha(firstGhost(1)))
                ).waitUntil("the swing has started to turn", () -> PreviewCinematicProbe.turntableTicks() >= 2).check(
                    "the swing is a quarter arc in the short direction",
                    () -> Math.abs(PreviewCinematicProbe.turntableAngleDeg()) > 0.0D
                            && Math.abs(PreviewCinematicProbe.turntableAngleDeg()) < 90.0D,
                    () -> Double.toString(PreviewCinematicProbe.turntableAngleDeg())
                ).check(
                    "the swing still holds the old pose at half way",
                    () -> PreviewCinematicProbe.turntableModelTurn() != null
                            && PreviewCinematicProbe.turntableModelTurn().ordinal() == oldTurn[0]
                ).ticks(5).waitUntil("the swing ended", () -> !PreviewCinematicProbe.turntableActive()).check(
                    "the final tick swaps the pose to the new turn",
                    () -> PreviewProbe.siting().placement().orientation().turn().ordinal() != oldTurn[0]
                            && PreviewCinematicProbe.ghostAlpha(firstGhost(1)) > 0.0F,
                    () -> "payload=" + PreviewProbe.siting().placement().orientation().turn() + " alpha="
                            + PreviewCinematicProbe.ghostAlpha(firstGhost(1))
                ).screenshot("03-cinematic-turntable").frames(2).checkCaptured("03-cinematic-turntable")
                // the playback's end plays the closing pull-back
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.playPause))
                .waitUntil("the playback started", PreviewStageProbe::playing)
                .waitUntil("the playback ran out", () -> !PreviewStageProbe.playing())
                .waitUntil("the closing pull-back started", PreviewCinematicProbe::closing)
                .action(
                    "record the framing distance",
                    context -> stageDistance[0] = PreviewCinematicProbe.eyeDistanceToOverallCentre()
                ).ticks(21)
                .check(
                    "the closing leg pulls the frame back",
                    () -> stageDistance[0] > 0.0D
                            && PreviewCinematicProbe.eyeDistanceToOverallCentre() > stageDistance[0] * 1.2D,
                    () -> stageDistance[0] + " -> " + PreviewCinematicProbe.eyeDistanceToOverallCentre()
                ).screenshot("04-cinematic-closing").frames(2).checkCaptured("04-cinematic-closing")
                // W is the viewer taking the keys: a quarter-second flight back, then the player's camera
                .pressBind(options -> options.keyUp)
                .waitUntil("the camera flew back to the player", () -> !PreviewCinematicProbe.active()).frames(3)
                .check(
                    "the player's own camera is back, at the player's eye and angles",
                    PreviewCinematicProbe::cameraReturnedToPlayer,
                    () -> "camera=" + PreviewCinematicProbe.cameraPosition() + " eye="
                            + PreviewCinematicProbe.playerEye() + " yaw=" + PreviewCinematicProbe.cameraYaw() + "/"
                            + PreviewCinematicProbe.playerYaw()
                )
                .check(
                    "the camera type is the one the mode recorded",
                    () -> PreviewCinematicProbe.cameraType().isFirstPerson()
                ).screenshot("05-cinematic-restored").frames(2).checkCaptured("05-cinematic-restored")
                // the two viewing modes kick each other in both directions
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.guide))
                .waitUntil("G armed the guide", PreviewGuideProbe::guided)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.cinematic))
                .waitUntil("C entered the cinema", PreviewCinematicProbe::active)
                .check("C disarms the guide", () -> !PreviewGuideProbe.guided())
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.guide))
                .waitUntil(
                    "G left the cinema and armed the guide",
                    () -> !PreviewCinematicProbe.active() && PreviewGuideProbe.guided()
                ).check("the guide owns the session again", () -> PreviewGuideProbe.nextStep() != null)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.guide))
                .waitUntil("the guide is off again", () -> !PreviewGuideProbe.guided())
                // a session that closes under the mode leaves the frame with the player and fades its ghosts
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.cinematic))
                .waitUntil("C entered the cinema again", PreviewCinematicProbe::active)
                .action(
                    "cancel the preview session",
                    context -> PreviewScenarioSupport.send(PreviewControlPayload.Control.cancel)
                ).waitUntil("the client dropped the session", () -> !PreviewProbe.hasSession())
                .check("the mode leaves with the session", () -> !PreviewCinematicProbe.active())
                .check(
                    "the last ghost set fades out",
                    PreviewCinematicProbe::sessionFadeActive,
                    () -> Integer.toString(PreviewCinematicProbe.sessionFadeTicks())
                ).waitUntil("the fade ended", () -> !PreviewCinematicProbe.sessionFadeActive()).timelineRecord(
                    "preview-cinematic",
                    () -> "active=" + PreviewCinematicProbe.active() + ", stage=" + PreviewStageProbe.selectedStage()
                            + ", program=" + PreviewCinematicProbe.programTicks()
                );
    }

    /** The first ghost of a stage; the staged document has three cells in each. */
    private static GhostCell firstGhost(int stage) {
        var ghosts = PreviewStageProbe.ghosts(stage);
        return ghosts.isEmpty() ? null : ghosts.get(0);
    }
}
