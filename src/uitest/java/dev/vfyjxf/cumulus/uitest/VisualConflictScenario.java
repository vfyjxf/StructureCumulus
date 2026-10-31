package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V2 conflict scenario: owner A holds an 独占 row on the cell owner B overlaps; the highlight
 * layer must paint that cell red, the HUD must report the conflict and its real finding code, and a
 * settle attempt must not form anything.
 */
@ClientScenario(
    name = "visual.conflict",
    group = "visual",
    tags = {"visual", "conflict"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualConflictScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        String conflictCode = Codes.claimConflict.code().text();
        build.guiScale(2).hideGui(false)
                .log("visual.conflict: two owners on one 独占 cell; red cells, conflict code, no false settle")
                .runCommand("cumulusvisual clearall")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual conflict 0 100 0").teleportPlayer(0.5, 101.0, 5.5).yawPitch(180.0F, 13.0F)
                .awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds the 独占 overlap",
                    () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 1
                ).waitUntil("the highlight layer paints the contested cell red", () -> {
                    VisualHighlightSummary summary = VisualProbe.highlightSummary();
                    return summary.conflictCells() == 1 && summary.conflictInstances() == 1;
                })
                .waitUntil(
                    "the HUD renders the conflict",
                    () -> VisualProbe.hudVisible() && VisualProbe.hudLines().contains("冲突: 1 格, 1 实例")
                ).check("one 独占 row exists", () -> VisualProbe.claimCount() == 1)
                .check("one cell is in conflict", () -> VisualProbe.highlightSummary().conflictCells() == 1)
                .check(
                    "one instance carries the conflict finding",
                    () -> VisualProbe.highlightSummary().conflictInstances() == 1
                ).check("the HUD reports the conflict", () -> VisualProbe.hudLines().contains("冲突: 1 格, 1 实例"))
                .check(
                    "the HUD reports the real conflict code",
                    () -> VisualProbe.hudLines().contains("错误码: " + conflictCode)
                )
                .waitUntil(
                    "the HUD claim summary reports one 独占 row",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("声明: 1 (修订 ") && line.contains("共享 0, 独占 1)"))
                )
                .check(
                    "the HUD summarises the 独占 row",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("声明: 1 (修订 ") && line.contains("共享 0, 独占 1)"))
                ).check("the HUD reports the snapshot age", () -> VisualProbe.hudLineStartsWith("快照: 年龄 "))
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(origin), 8, null).ticks(2)
                .waitUntil(
                    "the crosshair selects the 独占 holder",
                    () -> VisualProbe.selectionSource().equals("crosshair") && VisualProbe.selectedInstance() != null
                )
                .timelineRecord(
                    "conflict-before-settle",
                    () -> VisualScenarioSupport.describe(VisualProbe.highlightSummary())
                ).screenshot("conflict").frames(2).checkCaptured("conflict")
                .runCommand("cumulus settle structurecumulus:set_root 0 100 0").serverTicks(3).ticks(5)
                .check("the conflicting settle forms nothing", () -> VisualProbe.instanceCount() == 2)
                .check("the conflicting settle writes no row", () -> VisualProbe.claimCount() == 1)
                .check(
                    "the conflict survives the rejected settle",
                    () -> VisualProbe.highlightSummary().conflictCells() == 1
                )
                .timelineRecord(
                    "conflict-after-settle",
                    () -> VisualScenarioSupport.describe(VisualProbe.highlightSummary())
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.AIR.defaultBlockState());
    }
}
