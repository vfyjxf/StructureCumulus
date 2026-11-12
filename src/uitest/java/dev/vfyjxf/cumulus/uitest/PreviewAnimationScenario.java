package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import dev.vfyjxf.cumulus.mc.preview.net.BuildCommitPayload;
import net.minecraft.world.level.block.Blocks;

/**
 * The placement feedback: a confirm pops its cell in with one cue and no celebration, four builds
 * finish the probe so the last one celebrates the formed run, and the undo fades its cell back
 * without a second celebration.
 */
@ClientScenario(
    name = "preview.animation",
    group = "animation",
    tags = {"preview", "animation"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewAnimationScenario {

    private static final String buildCommand = "cumulusbuild run structurecumulus:preview_probe 0 100 0";
    /** The probe is 2x1x2 stairs at the origin; an upright turn keeps it inside this square. */
    private static final int resetFrom = -2;
    private static final int resetTo = 2;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).runCommand("cumulusvisual hud hide")
                .log("preview.animation: the pop-in of a confirm, the formed celebration and the undo fade").check(
                    "the animator starts empty",
                    () -> !PlacementProbe.animating()
                            && PlacementProbe.startedRuns() == 0
                            && PlacementProbe.celebrations() == 0,
                    () -> "runs=" + PlacementProbe.startedRuns()
                );
        build = previewConfirm(build);
        PreviewScenarioSupport.cancel(build);
        build = resetProbe(build);
        build = formedRuns(build);
        build = undoFade(build);
        PreviewScenarioSupport.cleanup(build).runCommand("cumulusvisual hud show");
    }

    /** The preview's own confirm path: one changed cell, one cue, no celebration yet. */
    private static ScenarioBuilder previewConfirm(ScenarioBuilder build) {
        return PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.confirm))
                .waitUntil("the confirm started a placement animation", PlacementProbe::animating)
                .check(
                    "the animation names the confirm's commit",
                    () -> PlacementProbe.lastStatus() == BuildCommitPayload.Status.committed,
                    () -> String.valueOf(PlacementProbe.lastStatus())
                )
                .check(
                    "one changed cell animates, never a cell the run skipped",
                    () -> PlacementProbe.animatingCells() == 1,
                    () -> Integer.toString(PlacementProbe.animatingCells())
                )
                .check(
                    "the run requested a place cue",
                    () -> PlacementProbe.soundRequests() > 0,
                    () -> Integer.toString(PlacementProbe.soundRequests())
                )
                .check(
                    "a committed run that did not form does not celebrate",
                    () -> PlacementProbe.celebrations() == 0,
                    () -> Integer.toString(PlacementProbe.celebrations())
                ).check("a small run carries no truncation note", () -> PlacementProbe.noteLines().isEmpty())
                .waitUntil(
                    "the animation ran to its end",
                    () -> !PlacementProbe.animating() && PlacementProbe.finishedRuns() >= 1
                ).screenshot("01-animation-commit").frames(2).checkCaptured("01-animation-commit");
    }

    /** The confirm's pose is not the command's identity pose, so the square goes back to air. */
    private static ScenarioBuilder resetProbe(ScenarioBuilder build) {
        return build.fill(resetFrom, 100, resetFrom, resetTo, 100, resetTo, Blocks.AIR.defaultBlockState());
    }

    /** Four command builds finish the probe; the last one is the formed run the celebration belongs to. */
    private static ScenarioBuilder formedRuns(ScenarioBuilder build) {
        for (int run = 2; run <= 5; run++) {
            int expected = run;
            build = build.runCommand(buildCommand)
                    .waitUntil("build " + expected + " started a placement animation", PlacementProbe::animating)
                    .check(
                        "build " + expected + " animates the one cell it changed",
                        () -> PlacementProbe.lastStatus() == BuildCommitPayload.Status.committed
                                && PlacementProbe.animatingCells() == 1,
                        () -> PlacementProbe.lastStatus() + " " + PlacementProbe.animatingCells()
                    ).waitUntil(
                        "build " + expected + " finished its animation",
                        () -> !PlacementProbe.animating() && PlacementProbe.finishedRuns() >= expected
                    );
        }
        return build
                .check(
                    "the formed run celebrated exactly once",
                    () -> PlacementProbe.celebrations() == 1,
                    () -> Integer.toString(PlacementProbe.celebrations())
                )
                .check(
                    "every run requested at least one cue",
                    () -> PlacementProbe.soundRequests() >= 5,
                    () -> Integer.toString(PlacementProbe.soundRequests())
                ).timelineRecord(
                    "preview-animation-formed",
                    () -> "runs=" + PlacementProbe.startedRuns() + ", cues=" + PlacementProbe.soundRequests()
                            + ", celebrations=" + PlacementProbe.celebrations()
                );
    }

    /** The undo is a removal: its cell fades out, no celebration repeats. */
    private static ScenarioBuilder undoFade(ScenarioBuilder build) {
        return build.runCommand("cumulusbuild undo")
                .check(
                    "the dev command dispatched the undo",
                    () -> BuildDevCommand.lastResult.equals("cumulus build undo"),
                    () -> BuildDevCommand.lastResult
                )
                .waitUntil(
                    "the undo started a fade-out",
                    () -> PlacementProbe.animating() && PlacementProbe.lastStatus() == BuildCommitPayload.Status.undone
                )
                .check(
                    "the fade animates the restored cell",
                    () -> PlacementProbe.animatingCells() == 1,
                    () -> Integer.toString(PlacementProbe.animatingCells())
                )
                .waitUntil(
                    "the fade ran to its end",
                    () -> !PlacementProbe.animating() && PlacementProbe.finishedRuns() >= 6
                )
                .check(
                    "the undo does not celebrate again",
                    () -> PlacementProbe.celebrations() == 1,
                    () -> Integer.toString(PlacementProbe.celebrations())
                )
                .timelineRecord(
                    "preview-animation-undo",
                    () -> "runs=" + PlacementProbe.startedRuns() + ", finished=" + PlacementProbe.finishedRuns()
                            + ", status=" + PlacementProbe.lastStatus()
                ).screenshot("02-animation-undo").frames(2).checkCaptured("02-animation-undo");
    }
}
