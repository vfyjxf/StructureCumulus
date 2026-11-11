package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;

/**
 * 3.7: a tap on R returns the first candidate and a hold returns the identity pose; the two gestures
 * must not collapse into one another in either direction.
 */
@ClientScenario(
    name = "preview.reset",
    group = "preview",
    tags = {"preview", "reset"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewResetScenario {

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build)
                .log("preview.reset: turn away, tap R back to the first candidate, then hold R to identity");
        PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.turnCw))
                .waitUntil("a turn left the first candidate behind", () -> PreviewProbe.current() != 0)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.reset))
                .waitUntil("a tap on R returns the first candidate", () -> PreviewProbe.current() == 0)
                .check("the tapped pose is the opening pose", () -> PreviewProbe.ghostCount() == 4)
                .holdBind(options -> PreviewKeys.binding(PreviewKeys.Action.reset)).ticks(20)
                .releaseBind(options -> PreviewKeys.binding(PreviewKeys.Action.reset))
                .waitUntil(
                    "a hold on R returns the identity pose",
                    () -> PreviewProbe.siting().placement().orientation().equals(Orientation.identity)
                )
                .check(
                    "the identity pose is still expressible with the ghost set",
                    () -> PreviewProbe.ghostCount() == 4
                )
                .timelineRecord(
                    "preview-reset",
                    () -> "siting=" + PreviewProbe.siting() + ", current=" + PreviewProbe.current()
                ).screenshot("hud").frames(3).checkCaptured("hud");
        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
