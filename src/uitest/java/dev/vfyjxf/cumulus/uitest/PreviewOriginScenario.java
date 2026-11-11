package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;

/**
 * An originless open ray-traces the crosshair block (1.1): the hit and the face-against cell are the
 * two origin candidates, so the cycle doubles. Scoring leads with the supported face origin, which
 * draws the ghost models; the hit origin sits inside the stone floor, so its cells draw frames only.
 */
@ClientScenario(
    name = "preview.origin",
    group = "preview",
    tags = {"preview", "origin"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewOriginScenario {

    public void define(ScenarioBuilder build) {
        LatticePoint[] before = {null};
        build = PreviewScenarioSupport.stage(build)
                .log("preview.origin: no origin argument, so the crosshair hit and its face are the candidates");
        build.yawPitch(180.0F, 65.0F).ticks(3).runCommand("cumuluspreview " + PreviewScenarioSupport.probe.text())
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                )
                .waitUntil(
                    "the ray-traced session reached the client with both origins",
                    () -> PreviewProbe.hasSession() && PreviewProbe.candidateCount() == 16
                ).check("the ray-traced siting starts at one of the hit cells", () -> PreviewProbe.siting() != null)
                .check(
                    "the supported face origin has the higher score, so it draws the ghost models (3.2)",
                    () -> PreviewProbe.cells(CellMark.wrong) == 0 && PreviewProbe.ghostCount() == 4,
                    PreviewOriginScenario::marks
                ).action("record the opening origin", context -> before[0] = PreviewProbe.siting().origin())
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.nextOrigin))
                .waitUntil("the next origin candidate synced", () -> !PreviewProbe.siting().origin().equals(before[0]))
                .check(
                    "the two origins are the hit cell and its face neighbour",
                    () -> PreviewProbe.siting().origin().equals(before[0])
                            || Math.abs(PreviewProbe.siting().origin().x() - before[0].x())
                                    + Math.abs(PreviewProbe.siting().origin().y() - before[0].y())
                                    + Math.abs(PreviewProbe.siting().origin().z() - before[0].z())
                                    == 1
                )
                .check(
                    "the hit cells hold the stone floor, so they are wrong marks and no ghost is drawn (4.1)",
                    () -> PreviewProbe.ghostCount() == 0 && PreviewProbe.cells(CellMark.wrong) == 4,
                    PreviewOriginScenario::marks
                )
                .timelineRecord(
                    "preview-origin",
                    () -> "origin=" + PreviewProbe.siting().origin() + ", candidates=" + PreviewProbe.candidateCount()
                ).screenshot("world").frames(3).checkCaptured("world")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.prevOrigin)).waitUntil(
                    "the previous origin returns the opening cell",
                    () -> PreviewProbe.siting().origin().equals(before[0])
                );
        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }

    /** The whole mark tally, so a failure names which cell state the server actually chose. */
    private static String marks() {
        return "ghosts=" + PreviewProbe.ghostCount() + " cells=" + PreviewProbe.cellCount() + " present="
                + PreviewProbe.cells(CellMark.present) + " missing=" + PreviewProbe.cells(CellMark.missing) + " wrong="
                + PreviewProbe.cells(CellMark.wrong) + " extra=" + PreviewProbe.cells(CellMark.extra) + " blocked="
                + PreviewProbe.cells(CellMark.blocked) + " unknown=" + PreviewProbe.cells(CellMark.unknown);
    }
}
