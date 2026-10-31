package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V2 overlap scenario: three overlapping instances on one 共享 cell with per-instance claim rows;
 * the HUD/highlight model counts them, the crosshair selects one, and breaking the right-only cell
 * removes exactly one instance and its claims while the highlight layer keeps an invalidated ghost.
 */
@ClientScenario(
    name = "visual.overlap",
    group = "visual",
    tags = {"visual", "overlap"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualOverlapScenario {

    private static final BlockPos shared = VisualScenarioSupport.cell(0, 0, 0);
    private static final BlockPos breakCell = VisualScenarioSupport.cell(1, 0, 0);
    private static final BlockPos backCell = VisualScenarioSupport.cell(0, 0, 1);

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false)
                .log("visual.overlap: 3 overlapping instances, select one, break the right-only cell")
                .runCommand("cumulusvisual clearall")
                .setBlock(shared.getX(), shared.getY(), shared.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .setBlock(
                    breakCell.getX(),
                    breakCell.getY(),
                    breakCell.getZ(),
                    Blocks.EMERALD_BLOCK.defaultBlockState()
                ).setBlock(backCell.getX(), backCell.getY(), backCell.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual overlap 0 100 0").teleportPlayer(0.5, 101.0, 5.5).yawPitch(180.0F, 13.0F)
                .awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds three overlapping instances and five claim rows",
                    () -> VisualProbe.instanceCount() == 3 && VisualProbe.claimCount() == 5
                ).waitUntil("the highlight layer submits three bounds, three cells and no conflict", () -> {
                    VisualHighlightSummary summary = VisualProbe.highlightSummary();
                    return summary.boundsBoxes() == 3 && summary.claimCells() == 3 && summary.conflicts() == 0;
                })
                .timelineRecord(
                    "hud-state",
                    () -> "visible=" + VisualProbe.hudVisible() + ", lines=" + VisualProbe.hudLines()
                ).waitUntil(
                    "the HUD renders the three-instance snapshot",
                    () -> VisualProbe.hudVisible() && VisualProbe.hudLines().contains("实例: 3")
                ).check("the snapshot holds three instances", () -> VisualProbe.instanceCount() == 3).check(
                    "five 共享 rows cover three cells",
                    () -> VisualProbe.claimCount() == 5 && VisualProbe.highlightSummary().claimCells() == 3
                ).check("three bounds are highlighted", () -> VisualProbe.highlightSummary().boundsBoxes() == 3)
                .check("the overlap is shared, not conflicting", () -> VisualProbe.highlightSummary().conflicts() == 0)
                .check("the HUD shows the instance count", () -> VisualProbe.hudLines().contains("实例: 3"))
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(shared), 8, null).ticks(2)
                .timelineRecord("camera-state", () -> VisualScenarioSupport.cameraState())
                .waitUntil(
                    "the crosshair selects the 共享 instance",
                    () -> VisualProbe.selectionSource().equals("crosshair") && VisualProbe.selectedInstance() != null
                ).check("the 选中 exposes one highlighted instance", () -> VisualProbe.selectedInstance() != null)
                .check("the HUD reports the crosshair selection", () -> VisualProbe.hudLineStartsWith("选中: 准星 "))
                .check("the HUD reports a hovered instance", () -> VisualProbe.hudLineStartsWith("悬停: "))
                .check("the HUD reports a snapshot age", () -> VisualProbe.hudLineStartsWith("快照: 年龄 "))
                .timelineRecord(
                    "overlap-before-break",
                    () -> VisualScenarioSupport.describe(VisualProbe.highlightSummary())
                ).screenshot("selection").frames(2).checkCaptured("selection")
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(breakCell), 8, null).ticks(2)
                .waitUntil(
                    "the crosshair targets the right-only break cell",
                    () -> VisualScenarioSupport.hitBlock(breakCell)
                ).attack()
                .waitUntil(
                    "the break leaves two instances and three claim rows",
                    () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 3
                ).waitUntil("the highlight layer drops the broken bounds but keeps an invalidated ghost", () -> {
                    VisualHighlightSummary summary = VisualProbe.highlightSummary();
                    return summary.boundsBoxes() == 2
                            && summary.claimCells() == 2
                            && summary.invalidatedInstances() >= 1;
                })
                .waitUntil("the HUD renders the two-instance snapshot", () -> VisualProbe.hudLines().contains("实例: 2"))
                .check("exactly one instance was removed", () -> VisualProbe.instanceCount() == 2)
                .check(
                    "only the broken instance's rows were released",
                    () -> VisualProbe.claimCount() == 3 && VisualProbe.highlightSummary().claimCells() == 2
                )
                .check(
                    "the surviving highlights carry no conflict",
                    () -> VisualProbe.highlightSummary().conflicts() == 0
                ).check("the HUD shows the reduced count", () -> VisualProbe.hudLines().contains("实例: 2"))
                .timelineRecord(
                    "overlap-after-break",
                    () -> VisualScenarioSupport.describe(VisualProbe.highlightSummary())
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(shared.getX(), shared.getY(), shared.getZ(), Blocks.AIR.defaultBlockState())
                .setBlock(breakCell.getX(), breakCell.getY(), breakCell.getZ(), Blocks.AIR.defaultBlockState())
                .setBlock(backCell.getX(), backCell.getY(), backCell.getZ(), Blocks.AIR.defaultBlockState());
    }
}
