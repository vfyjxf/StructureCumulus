package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import dev.vfyjxf.cumulus.mc.preview.net.MaterialStatus;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * The stage pack on a two-bay document: the table's order and counts, the layered ghosts, the
 * cell step, the playback, the arrow switch, the banner and the three HUD tiers.
 */
@ClientScenario(
    name = "preview.stage",
    group = "stage",
    tags = {"preview", "stage"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 240_000
)
public final class PreviewStageScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final String floorDescription = "the brick floor";
    private static final String stackDescription = "the chimney above the floor";

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).runCommand("cumulusvisual hud hide").log(
            "preview.stage: two bays, a cell step, a playback, an arrow switch, the banner and the progress bars"
        ).check(
            "every preview action keeps its own default key",
            () -> PreviewStageProbe.keyConflict() == null,
            PreviewStageProbe::keyConflict
        );

        build = stagedSession(build);
        PreviewScenarioSupport.cancel(build);
        build.runCommand("gamemode survival @a");
        build = shortageSession(build);
        PreviewScenarioSupport.cancel(build);
        build.runCommand("gamemode creative @a");
        PreviewScenarioSupport.cleanup(build);
        build.runCommand("cumulusvisual hud show");
    }

    /** The creative half: the table, the layering, the playback and the two bar tiers. */
    private static ScenarioBuilder stagedSession(ScenarioBuilder build) {
        int[] turn = {-1};
        return PreviewScenarioSupport.open(build, staged)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the two-stage session synced", PreviewScenarioSupport::synced).check(
                    "the document declares two bays, so the table holds two stages",
                    () -> PreviewStageProbe.stageCount() == 2,
                    () -> "stages=" + PreviewStageProbe.stageCount()
                ).check(
                    "the stages keep the declaration order",
                    () -> "structurecumulus:base".equals(name(0)) && "structurecumulus:stack".equals(name(1)),
                    () -> name(0) + ", " + name(1)
                ).check(
                    "the floor expects nine cells and the chimney three",
                    () -> expectedCells(0) == 9 && expectedCells(1) == 3,
                    () -> expectedCells(0) + "/" + expectedCells(1)
                ).check(
                    "the ghosts arrive ordered by stage, nine then three",
                    () -> PreviewStageProbe.ghostsInStageOrder()
                            && PreviewStageProbe.stageCells(0) == 9
                            && PreviewStageProbe.stageCells(1) == 3,
                    () -> PreviewStageProbe.stageCells(0) + "/" + PreviewStageProbe.stageCells(1)
                ).check(
                    "a fresh session works on the first stage",
                    () -> PreviewStageProbe.currentStage() == 0 && PreviewStageProbe.selectedStage() == 0
                ).check(
                    "the total bar reads zero of twelve at zero percent",
                    () -> PreviewProbe.hudHas("progress 0/12 (0%)"),
                    () -> PreviewProbe.hudLines().toString()
                ).check(
                    "the stage line names the first part",
                    () -> PreviewProbe.hudHas("stage 1/2: structurecumulus:base")
                ).check(
                    "nothing is revealed, so the first stage draws faint",
                    () -> PreviewStageProbe.revealedCells(0) == 0 && PreviewStageProbe.alpha(0) < 0.1F
                ).screenshot("01-stage-open").frames(3).checkCaptured("01-stage-open")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.stepCell))
                .waitUntil("the step revealed one cell of the stage", () -> PreviewStageProbe.revealedCells(0) == 1)
                .check(
                    "the revealed cell draws as the current stage",
                    () -> PreviewStageProbe.alpha(0) == 1.0F,
                    () -> Float.toString(PreviewStageProbe.alpha(0))
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.playPause))
                .waitUntil("the playback is running", PreviewStageProbe::playing)
                .waitUntil("the playback revealed a further cell", () -> PreviewStageProbe.revealedCells(0) > 1)
                .action("record the pose under playback", context -> turn[0] = turnOrdinal())
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.turnCw))
                .waitUntil("a turn still re-syncs during the playback", () -> turnOrdinal() != turn[0])
                .check("the playback keeps running across the re-sync", PreviewStageProbe::playing)
                .waitUntil("the playback advanced to the second stage", () -> PreviewStageProbe.selectedStage() == 1)
                .check("the played-through stage draws as done", () -> PreviewStageProbe.alpha(0) == 0.25F)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.playPause))
                .waitUntil("the playback stopped", () -> !PreviewStageProbe.playing())
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.prevStage))
                .waitUntil("the cursor returned to the first stage", () -> PreviewStageProbe.selectedStage() == 0)
                .check(
                    "an arrow key shows the whole stage",
                    () -> PreviewStageProbe.revealedCells(0) == PreviewStageProbe.stageCells(0)
                )
                .check(
                    "the banner names the stage and carries the bay's description",
                    () -> PreviewStageProbe.bannerLines().equals(List.of("structurecumulus:base", floorDescription)),
                    () -> PreviewStageProbe.bannerLines().toString()
                ).screenshot("02a-stage-banner").frames(2).checkCaptured("02a-stage-banner")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.nextStage))
                .waitUntil("the cursor moved to the second stage", () -> PreviewStageProbe.selectedStage() == 1)
                .check(
                    "the done stage is faint and the current stage strong",
                    () -> PreviewStageProbe.alpha(0) == 0.25F && PreviewStageProbe.alpha(1) == 1.0F,
                    () -> PreviewStageProbe.alpha(0) + "/" + PreviewStageProbe.alpha(1)
                )
                .check(
                    "the stage line follows the cursor",
                    () -> PreviewProbe.hudHas("stage 2/2: structurecumulus:stack")
                )
                .check(
                    "the banner announces the second part",
                    () -> PreviewStageProbe.bannerLines().contains(stackDescription),
                    () -> PreviewStageProbe.bannerLines().toString()
                ).screenshot("02-stage-second").frames(3).checkCaptured("02-stage-second")
                .setBlock(0, 100, 0, Blocks.STONE_BRICKS.defaultBlockState())
                .waitUntil("the re-sync reported one satisfied cell", () -> PreviewProbe.cells(CellMark.present) == 1)
                .check(
                    "the total bar reads one of twelve at eight percent",
                    () -> PreviewProbe.hudHas("progress 1/12 (8%)"),
                    () -> PreviewProbe.hudLines().toString()
                ).check("the frontier stays on the first unfinished stage", () -> PreviewStageProbe.currentStage() == 0)
                .check(
                    "the first stage reports one of its nine cells satisfied",
                    () -> satisfiedCells(0) == 1 && expectedCells(0) == 9
                )
                .check(
                    "the creative source collapses the materials tier",
                    () -> PreviewStageProbe.materials().isEmpty()
                            && PreviewStageProbe.moreMaterials() == 0
                            && !PreviewProbe.hudHas("material ")
                ).timelineRecord(
                    "preview-stage",
                    () -> "stages=" + PreviewStageProbe.stageCount() + ", selected=" + PreviewStageProbe.selectedStage()
                            + ", revealed=" + PreviewStageProbe.revealedCells(0) + ", present="
                            + PreviewProbe.cells(CellMark.present)
                );
    }

    /** The survival half: the tier 3 shortage line comes from the session's own item simulation. */
    private static ScenarioBuilder shortageSession(ScenarioBuilder build) {
        return PreviewScenarioSupport.open(build, staged)
                .waitUntil("the survival session synced", PreviewScenarioSupport::synced)
                .check(
                    "an empty inventory leaves the next repair one item short",
                    () -> PreviewStageProbe.materials().size() == 1 && PreviewStageProbe.moreMaterials() == 0,
                    () -> PreviewStageProbe.materials().toString()
                ).check("the shortage names the floor's brick and its need", () -> {
                    for (MaterialStatus material : PreviewStageProbe.materials()) {
                        if (material.item().text().equals("minecraft:stone_bricks")) {
                            return material.need() == 1 && material.have() == 0;
                        }
                    }
                    return false;
                }, () -> PreviewStageProbe.materials().toString())
                .check(
                    "the HUD draws the material line with its have/need",
                    () -> PreviewProbe.hudHas("material minecraft:stone_bricks 0/1"),
                    () -> PreviewProbe.hudLines().toString()
                )
                .timelineRecord(
                    "preview-stage-materials",
                    () -> "materials=" + PreviewStageProbe.materials() + ", more=" + PreviewStageProbe.moreMaterials()
                ).screenshot("03-stage-materials").frames(3).checkCaptured("03-stage-materials");
    }

    private static int turnOrdinal() {
        var siting = PreviewProbe.siting();
        return siting == null ? -1 : siting.placement().orientation().turn().ordinal();
    }

    private static String name(int index) {
        Stage stage = PreviewStageProbe.stage(index);
        return stage == null ? "" : stage.name();
    }

    private static int expectedCells(int index) {
        Stage stage = PreviewStageProbe.stage(index);
        return stage == null ? -1 : stage.expectedCells();
    }

    private static int satisfiedCells(int index) {
        Stage stage = PreviewStageProbe.stage(index);
        return stage == null ? -1 : stage.satisfiedCells();
    }
}
