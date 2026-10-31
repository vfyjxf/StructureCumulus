package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V4 large scenario: 64 one-cell instances on a grid, compact HUD counts, and the highlight layer's
 * bounds cap. The scenario proves the per-frame overlay count stays bounded while every instance and
 * claim remains visible in the snapshot.
 */
@ClientScenario(
    name = "visual.large",
    group = "visual",
    tags = {"visual", "large", "scale"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualLargeScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);
    private static final BlockPos center = VisualScenarioSupport.cell(7, 0, 7);
    private static final int boundsCap = 48;
    private static final int expectedInstances = 64;

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false)
                .log("visual.large: 64 instances with compact HUD counts and a bounded highlight overlay")
                .runCommand("cumulusvisual clearall")
                .fill(0, 100, 0, 14, 100, 14, Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(0, 100, 18, 14, 100, 19, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual large 0 100 0").teleportPlayer(7.5, 101.0, 19.5)
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(center), 4, null).awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds all 64 instances and claim rows",
                    () -> VisualProbe.instanceCount() == expectedInstances
                            && VisualProbe.claimCount() == expectedInstances
                ).runCommand("cumulusvisual hud compact").frames(3)
                .waitUntil(
                    "the compact HUD reports the 64-instance counts",
                    () -> VisualProbe.compact()
                            && VisualProbe.hudLines().contains("实例: 64")
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("声明: 64 "))
                ).waitUntil("the highlight layer caps the submitted bounds", () -> {
                    VisualHighlightSummary summary = VisualProbe.highlightSummary();
                    return summary.boundsBoxes() > 0 && summary.boundsBoxes() <= boundsCap;
                })
                .check(
                    "the compact HUD keeps both count lines",
                    () -> VisualProbe.compact()
                            && VisualProbe.hudLines().contains("实例: 64")
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("声明: 64 "))
                )
                .check(
                    "the HUD highlight line reports the capped boxes",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("高亮: ") && line.contains(" 边界, "))
                )
                .check(
                    "the bounds highlight is capped below the 64 instances",
                    () -> VisualProbe.highlightSummary().boundsBoxes() <= boundsCap
                )
                .check(
                    "every claim cell remains in the snapshot",
                    () -> VisualProbe.claimCount() == expectedInstances
                            && VisualProbe.claimCells().size() == expectedInstances
                )
                .timelineRecord(
                    "large-state",
                    () -> "instances=" + VisualProbe.instanceCount() + ", claims=" + VisualProbe.claimCount()
                            + ", summary=" + VisualScenarioSupport.describe(VisualProbe.highlightSummary())
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world")
                .runCommand("cumulusvisual clearall").runCommand("cumulusvisual hud detail")
                .fill(0, 100, 0, 14, 100, 14, Blocks.AIR.defaultBlockState())
                .fill(0, 100, 18, 14, 100, 19, Blocks.AIR.defaultBlockState());
    }
}
