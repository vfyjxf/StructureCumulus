package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V3 error scenario: a missing-blueprint attempt publishes an error finding and aborts, so the HUD
 * must report the error state while no instance or claim is formed.
 */
@ClientScenario(
    name = "visual.error",
    group = "visual",
    tags = {"visual", "error", "lifecycle"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualErrorScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false).log("visual.error: a missing blueprint leaves no instance or claim")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState())
                .runCommand("gamemode creative @a").runCommand("cumulusvisual error 0 100 0")
                .teleportPlayer(0.5, 101.0, 5.5).yawPitch(180.0F, 22.976944F).awaitClientChunk(0, 0)
                .waitUntil(
                    "the HUD reports the missing-blueprint error",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.instanceCount() == 0
                            && VisualProbe.claimCount() == 0
                            && VisualProbe.hudLineStartsWith("错误: ")
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.contains("缺少蓝图"))
                ).check("the failed path forms no instance", () -> VisualProbe.instanceCount() == 0)
                .check("the failed path writes no claim", () -> VisualProbe.claimCount() == 0)
                .check(
                    "the HUD carries the error message",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("错误: ") && line.contains("缺少蓝图"))
                ).check("the failed path publishes no finding codes", () -> VisualProbe.hudLines().contains("错误码: 无"))
                .check(
                    "the failed path highlights nothing",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == 0
                            && VisualProbe.highlightSummary().claimCells() == 0
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState());
    }
}
