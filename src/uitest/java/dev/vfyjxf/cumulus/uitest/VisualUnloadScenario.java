package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V3 unload scenario: two fixture instances with claims; the dev fixture unloads the level so the HUD
 * shows no instances while the claim rows persist, then loads the saved instances back without
 * inventing invalidated ghosts.
 */
@ClientScenario(
    name = "visual.unload",
    group = "visual",
    tags = {"visual", "unload", "lifecycle"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualUnloadScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(100, 0, 0);
    private static final BlockPos back = VisualScenarioSupport.cell(100, 0, 2);

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false).log("visual.unload: unload keeps claims, load restores the instances")
                .runCommand("cumulusvisual clearall")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .setBlock(back.getX(), back.getY(), back.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(99, 100, 5, 101, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual pair 100 100 0").teleportPlayer(100.5, 101.0, 5.5)
                .yawPitch(180.0F, 22.976944F).awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds the pair before unload",
                    () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 2
                ).runCommand("cumulusvisual unload")
                .waitUntil(
                    "the unloaded level has no instances but keeps its claims",
                    () -> VisualProbe.instanceCount() == 0 && VisualProbe.claimCount() == 2
                )
                .waitUntil(
                    "the HUD reports the unload without invalidated ghosts",
                    () -> VisualProbe.hudLineStartsWith("事件: ")
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.contains("最近 卸载"))
                            && VisualProbe.highlightSummary().invalidatedInstances() == 0
                ).check("unload keeps both claim rows", () -> VisualProbe.claimCount() == 2)
                .check("unload removes every instance", () -> VisualProbe.instanceCount() == 0)
                .check(
                    "unload clears the instance bounds but keeps the claim cells",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == 0
                            && VisualProbe.highlightSummary().claimCells() == 2
                )
                .check(
                    "unload creates no invalidated ghosts",
                    () -> VisualProbe.highlightSummary().invalidatedInstances() == 0
                )
                .check(
                    "the HUD reports the unload event",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("事件: ") && line.contains("最近 卸载"))
                ).screenshot("unloaded-hud").frames(2).checkCaptured("unloaded-hud").screenshot("unloaded-world")
                .frames(2).checkCaptured("unloaded-world").runCommand("cumulusvisual load")
                .waitUntil(
                    "the loaded level restores both instances and keeps the claims",
                    () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 2
                )
                .waitUntil(
                    "the HUD reports the load without ghosts",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.contains("最近 加载"))
                            && VisualProbe.highlightSummary().invalidatedInstances() == 0
                ).check("load restores both instances", () -> VisualProbe.instanceCount() == 2)
                .check("load keeps both claim rows", () -> VisualProbe.claimCount() == 2)
                .check("load invents no ghosts", () -> VisualProbe.highlightSummary().invalidatedInstances() == 0)
                .screenshot("loaded-hud").frames(2).checkCaptured("loaded-hud").screenshot("loaded-world").frames(2)
                .checkCaptured("loaded-world").runCommand("cumulusvisual clearall")
                .fill(99, 100, 5, 101, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.AIR.defaultBlockState())
                .setBlock(back.getX(), back.getY(), back.getZ(), Blocks.AIR.defaultBlockState());
    }
}
