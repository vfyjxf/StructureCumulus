package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;

/**
 * The turn keys walk the expressible upright poses of one origin: the server recomputes and re-syncs,
 * the ghost set follows the pose and the mirror is untouched until the flip key.
 */
@ClientScenario(
    name = "preview.turn",
    group = "preview",
    tags = {"preview", "turn"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewTurnScenario {

    public void define(ScenarioBuilder build) {
        int[] before = {-1};
        build = PreviewScenarioSupport.stage(build)
                .log("preview.turn: turn clockwise then counterclockwise, asserting a re-synced pose each time");
        PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .action(
                    "record the opening pose",
                    context -> before[0] = PreviewProbe.siting().placement().orientation().ordinal()
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.turnCw))
                .waitUntil(
                    "the server re-synced a different pose",
                    () -> PreviewProbe.siting().placement().orientation().ordinal() != before[0]
                )
                .check(
                    "the new pose stays upright",
                    () -> PreviewProbe.siting().placement().orientation().tilt() == Tilt.level
                )
                .check(
                    "the mirror flag did not move with the turn",
                    () -> PreviewProbe.siting().placement().orientation().isMirror() == false
                ).check("the ghost set followed the pose", () -> PreviewProbe.ghostCount() == 4)
                .check(
                    "the candidate counter is still inside the cycle",
                    () -> PreviewProbe.current() < PreviewProbe.candidateCount()
                )
                .timelineRecord(
                    "preview-turn",
                    () -> "current=" + PreviewProbe.current() + ", siting=" + PreviewProbe.siting()
                ).screenshot("hud").frames(3).checkCaptured("hud")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.turnCcw))
                .waitUntil(
                    "the counter-turn returns to the opening pose",
                    () -> PreviewProbe.siting().placement().orientation().ordinal() == before[0]
                ).check(
                    "the pose is back where it started",
                    () -> PreviewProbe.siting().placement().orientation().ordinal() == before[0]
                );
        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
