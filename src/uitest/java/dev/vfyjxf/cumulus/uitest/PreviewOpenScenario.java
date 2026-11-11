package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;

/**
 * Opening the preview at an explicit origin syncs one session: every upright pose is expressible, the
 * four expected cells arrive as ghosts and the HUD carries the counter, the candidate and the marker.
 */
@ClientScenario(
    name = "preview.open",
    group = "preview",
    tags = {"preview", "open"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewOpenScenario {

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build)
                .log("preview.open: open a session and assert the synced cells, candidates and HUD")
                .log("no session survives a fresh scenario");

        PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .check(
                    "the probe blueprint is a 2x1x2 of oak stairs, so four cells are expected",
                    () -> PreviewProbe.ghostCount() == 4
                )
                .check(
                    "all eight upright poses are expressible at one explicit origin",
                    () -> PreviewProbe.candidateCount() == 8
                )
                .check(
                    "nothing is built yet, so every cell is missing",
                    () -> PreviewProbe.cells(CellMark.missing) == 4
                ).check("no cell is present", () -> PreviewProbe.cells(CellMark.present) == 0)
                .check(
                    "the HUD carries the counter, the candidate and the key legend",
                    () -> PreviewProbe.hudHas("preview: structurecumulus:preview_probe · 0/4")
                            && PreviewProbe.hudHas("candidate 1/8")
                            && PreviewProbe.hudHas("keys: ")
                )
                .check(
                    "the materials placeholder is reported, not invented",
                    () -> PreviewProbe.hasMarker("materials: ")
                ).check("there is no invalidation yet", () -> !PreviewProbe.hasMarker("invalidated"))
                .timelineRecord(
                    "preview-open",
                    () -> "candidates=" + PreviewProbe.candidateCount() + ", ghosts=" + PreviewProbe.ghostCount()
                            + ", siting=" + PreviewProbe.siting()
                ).screenshot("hud").frames(3).checkCaptured("hud").screenshot("world").frames(3).checkCaptured("world");

        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
