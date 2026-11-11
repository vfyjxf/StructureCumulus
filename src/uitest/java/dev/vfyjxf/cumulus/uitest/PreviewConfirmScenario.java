package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;

/**
 * confirm reaches the placeholder handler, which reports the executor call and refuses: the plan
 * stays in the session for another try (5.1) and cancel is the only way out.
 */
@ClientScenario(
    name = "preview.confirm",
    group = "preview",
    tags = {"preview", "confirm"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewConfirmScenario {

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build)
                .log("preview.confirm: Enter reaches the placeholder, the plan survives the refusal, Esc cancels");
        PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.confirm))
                .waitUntil(
                    "the refused confirm keeps the plan in the session",
                    () -> PreviewProbe.hasMarker("the build refused")
                ).check("the session is still alive after the refusal", () -> PreviewProbe.hasSession())
                .check("the ghosts survived the refusal", () -> PreviewProbe.ghostCount() == 4)
                .check(
                    "the HUD tells the user how to continue",
                    () -> PreviewProbe.hudLines().stream()
                            .anyMatch(line -> line.contains("confirm again once the shortage"))
                )
                .timelineRecord(
                    "preview-confirm",
                    () -> "markers=" + PreviewProbe.markerCount() + ", ghosts=" + PreviewProbe.ghostCount()
                ).screenshot("hud").frames(3).checkCaptured("hud");
        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
