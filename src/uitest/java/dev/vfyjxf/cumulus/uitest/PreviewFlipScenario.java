package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;

/** The flip key mirrors the pose in place and mirrors it back; the turn is untouched. */
@ClientScenario(
    name = "preview.flip",
    group = "preview",
    tags = {"preview", "flip"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewFlipScenario {

    public void define(ScenarioBuilder build) {
        int[] turn = {-1};
        build = PreviewScenarioSupport.stage(build)
                .log("preview.flip: flip the pose, assert the mirror and the unchanged turn, then flip back");
        PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .action(
                    "record the opening turn",
                    context -> turn[0] = PreviewProbe.siting().placement().orientation().turn().ordinal()
                )
                .check(
                    "the opening pose is not mirrored",
                    () -> !PreviewProbe.siting().placement().orientation().isMirror()
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.flip))
                .waitUntil(
                    "the server re-synced the mirrored pose",
                    () -> PreviewProbe.siting().placement().orientation().isMirror()
                )
                .check(
                    "the mirrored pose keeps its turn",
                    () -> PreviewProbe.siting().placement().orientation().turn().ordinal() == turn[0]
                )
                .check(
                    "the mirrored pose stays upright",
                    () -> PreviewProbe.siting().placement().orientation().tilt() == Tilt.level
                ).check("the ghost set survives the mirror", () -> PreviewProbe.ghostCount() == 4).screenshot("hud")
                .frames(3).checkCaptured("hud").pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.flip))
                .waitUntil(
                    "the second flip returns the upright pose",
                    () -> !PreviewProbe.siting().placement().orientation().isMirror()
                ).check(
                    "the turn never changed",
                    () -> PreviewProbe.siting().placement().orientation().turn().ordinal() == turn[0]
                );
        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
