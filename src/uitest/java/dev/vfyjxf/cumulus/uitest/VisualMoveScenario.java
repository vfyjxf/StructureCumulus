package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V3 move scenario: two fixture instances; the dev fixture moves the first one and translates its
 * claims, so the HUD must show the moved lifecycle event, the highlight must follow the new bounds,
 * and the old claim cell must be gone while the new one appears.
 */
@ClientScenario(
    name = "visual.move",
    group = "visual",
    tags = {"visual", "move", "lifecycle"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualMoveScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);
    private static final BlockPos back = VisualScenarioSupport.cell(0, 0, 2);
    private static final LatticePoint oldCell = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
    private static final LatticePoint newCell = LatticePoint.of(origin.getX() + 1, origin.getY(), origin.getZ());

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false).log("visual.move: move the first of two instances and translate its claims")
                .runCommand("cumulusvisual clearall")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .setBlock(back.getX(), back.getY(), back.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual pair 0 100 0").teleportPlayer(0.5, 101.0, 5.5).yawPitch(180.0F, 22.976944F)
                .awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds two instances and two claim cells",
                    () -> VisualProbe.instanceCount() == 2
                            && VisualProbe.claimCount() == 2
                            && VisualProbe.claimCells().contains(oldCell)
                ).runCommand("cumulusvisual move 0 100 0")
                .waitUntil(
                    "the snapshot shows the moved instance and translated claims",
                    () -> VisualProbe.instanceCount() == 2
                            && VisualProbe.claimCount() == 2
                            && VisualProbe.claimCells().contains(newCell)
                            && !VisualProbe.claimCells().contains(oldCell)
                )
                .waitUntil(
                    "the highlight layer marks the moved instance",
                    () -> VisualProbe.highlightSummary().movedInstances() >= 1
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.contains("最近 移动"))
                ).check("the instance count is unchanged", () -> VisualProbe.instanceCount() == 2)
                .check("the old claim cell was released", () -> !VisualProbe.claimCells().contains(oldCell))
                .check("the new claim cell was written", () -> VisualProbe.claimCells().contains(newCell))
                .check(
                    "the HUD reports the moved lifecycle event",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("事件: 移动 ") && line.contains("最近 移动"))
                )
                .check(
                    "the HUD highlights the moved instance",
                    () -> VisualProbe.highlightSummary().movedInstances() == 1
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.AIR.defaultBlockState())
                .setBlock(back.getX(), back.getY(), back.getZ(), Blocks.AIR.defaultBlockState());
    }
}
