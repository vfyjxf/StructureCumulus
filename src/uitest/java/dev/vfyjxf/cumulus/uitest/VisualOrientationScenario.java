package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * A half-turned siting proves the HUD and the bounds highlight use world-frame geometry: the local
 * 2x1x1 blueprint maps to the west-straddling world box, and the HUD reports the non-identity
 * orientation next to the world bounds.
 */
@ClientScenario(
    name = "visual.orientation",
    group = "visual",
    tags = {"visual", "orientation"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualOrientationScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);
    private static final BlockPos west = VisualScenarioSupport.cell(-1, 0, 0);
    private static final CuboidSet expectedBounds = CuboidSet
            .of(Cuboid.of(LatticePoint.of(-1, 100, 0), LatticePoint.of(1, 101, 1)));

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false)
                .log("visual.orientation: half-turned siting, world-frame bounds and HUD orientation")
                .runCommand("cumulusvisual clearall")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .setBlock(west.getX(), west.getY(), west.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual orientation 0 100 0").teleportPlayer(0.5, 101.0, 5.5).yawPitch(180.0F, 16.0F)
                .awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds one half-turned instance and two world claim cells",
                    () -> VisualProbe.instanceCount() == 1 && VisualProbe.claimCount() == 2
                )
                .waitUntil(
                    "the HUD reports the half-turned orientation and world bounds",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.hudLineStartsWith("朝向: ")
                            && VisualProbe.hudLines().stream()
                                    .anyMatch(line -> line.startsWith("朝向: ") && line.contains("序数="))
                            && VisualProbe.hudLines().contains("边界: [-1,100,0..1,101,1]")
                )
                .check(
                    "the siting orientation is not identity",
                    () -> VisualProbe.firstInstance() != null
                            && VisualProbe.firstInstance().siting().placement().orientation().ordinal() != 0
                )
                .check(
                    "the instance bounds are the world-frame box",
                    () -> expectedBounds.equals(VisualProbe.firstBounds())
                )
                .check(
                    "the highlight submits one world-frame box",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == 1
                )
                .check(
                    "the highlight submits both world-frame claim cells",
                    () -> VisualProbe.highlightSummary().claimCells() == 2
                )
                .check(
                    "the HUD orientation line names the technical half/level siting",
                    () -> VisualProbe.hudLines().stream().anyMatch(
                        line -> line.startsWith("朝向: ") && line.contains("half/level") && line.contains("序数=")
                    )
                )
                .check(
                    "the HUD bounds line is world-frame",
                    () -> VisualProbe.hudLines().contains("边界: [-1,100,0..1,101,1]")
                )
                .timelineRecord(
                    "orientation-state",
                    () -> "siting=" + VisualProbe.firstInstance().siting() + ", bounds=" + VisualProbe.firstBounds()
                ).screenshot("hud").frames(3).checkCaptured("hud").screenshot("world").frames(3).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.AIR.defaultBlockState())
                .setBlock(west.getX(), west.getY(), west.getZ(), Blocks.AIR.defaultBlockState());
    }
}
