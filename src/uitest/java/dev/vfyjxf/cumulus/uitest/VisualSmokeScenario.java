package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V1 smoke: place a visible 1-cell marker and settle the built-in claimable 1x1x1 structure through
 * the real server command path, then wait for the visual snapshot and the CloudLib HUD and assert the
 * model, capturing both the HUD frame and the world frame.
 *
 * <p>No world highlights and no chat output: the HUD is the only diagnostic surface.
 */
@ClientScenario(
    name = "visual.smoke",
    group = "visual",
    tags = {"visual", "smoke"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualSmokeScenario {

    private static final BlockPos origin = new BlockPos(0, 100, 0);
    private static final CuboidSet expectedBounds = CuboidSet
            .of(Cuboid.of(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())));

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false).log("visual.smoke: settle one 1x1x1 structure and wait for the visual HUD")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .runCommand(
                    "cumulus settle structurecumulus:set_root " + origin.getX() + " " + origin.getY() + " "
                            + origin.getZ()
                ).teleportPlayer(0.5, origin.getY(), origin.getZ() + 5.5).yawPitch(180.0F, 11.5F)
                .awaitClientChunk(origin.getX() >> 4, origin.getZ() >> 4)
                .waitUntil("the client sees the marker cell", () -> {
                    var level = Minecraft.getInstance().level;
                    return level != null && level.getBlockState(origin).is(Blocks.EMERALD_BLOCK);
                })
                .waitUntil(
                    "the visual snapshot holds the settled instance",
                    () -> VisualProbe.instanceCount() == 1 && VisualProbe.claimCount() == 1
                )
                .waitUntil(
                    "the CloudLib HUD renders the settled snapshot",
                    () -> VisualProbe.hudVisible() && VisualProbe.hudLines().contains("实例: 1")
                ).check("the snapshot holds exactly one instance", () -> VisualProbe.instanceCount() == 1)
                .check(
                    "the instance bounds match the settled cell",
                    () -> expectedBounds.equals(VisualProbe.firstBounds())
                ).check("the snapshot holds exactly one claim row", () -> VisualProbe.claimCount() == 1)
                .check("the HUD shows the instance count", () -> VisualProbe.hudLines().contains("实例: 1"))
                .check(
                    "the HUD reports the context summary",
                    () -> VisualProbe.hudLineStartsWith("上下文: 修订 1") && VisualProbe.hudLineStartsWith("上下文键值: ")
                ).check("the HUD reports a snapshot age", () -> VisualProbe.hudLineStartsWith("快照: 年龄 "))
                .check("the HUD reports the hover state", () -> VisualProbe.hudLineStartsWith("悬停: ")).ticks(2)
                .screenshot("hud").frames(3).checkCaptured("hud").screenshot("world").frames(3).checkCaptured("world");
    }
}
