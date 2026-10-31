package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V3 HUD toggle scenario: the dev command switches the card between compact and detail, and the
 * model/lines plus captures prove the HUD is the only diagnostic surface (no chat).
 */
@ClientScenario(
    name = "visual.hudToggle",
    group = "visual",
    tags = {"visual", "hud", "lifecycle"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualHudToggleScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false).log("visual.hudToggle: switch compact/detail through the dev command")
                .runCommand("cumulusvisual clearall").runCommand("cumulusvisual hud detail")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual context 0 100 0").teleportPlayer(0.5, 101.0, 5.5)
                .yawPitch(180.0F, 22.976944F).awaitClientChunk(0, 0)
                .waitUntil(
                    "the detail HUD renders the context fixture",
                    () -> VisualProbe.hudVisible() && !VisualProbe.compact() && VisualProbe.hudLineStartsWith("上下文键值: ")
                ).check("detail starts in the detail mode", () -> !VisualProbe.compact())
                .check("detail shows the context key row", () -> VisualProbe.hudLineStartsWith("上下文键值: "))
                .runCommand("cumulusvisual hud compact").frames(3)
                .waitUntil(
                    "the compact HUD hides the detail column",
                    () -> VisualProbe.compact()
                            && VisualProbe.hudLines().contains("实例: 1")
                            && !VisualProbe.hudLineStartsWith("上下文键值: ")
                ).check("compact keeps the summary", () -> VisualProbe.hudModel().compact())
                .check("compact keeps the instance count", () -> VisualProbe.hudLines().contains("实例: 1"))
                .check("compact hides the context details", () -> !VisualProbe.hudLineStartsWith("上下文键值: "))
                .screenshot("hud-compact").frames(2).checkCaptured("hud-compact").runCommand("cumulusvisual hud detail")
                .frames(3)
                .waitUntil(
                    "the detail HUD returns",
                    () -> !VisualProbe.compact() && VisualProbe.hudLineStartsWith("上下文键值: ")
                ).check("detail restores the key row", () -> VisualProbe.hudLineStartsWith("上下文键值: "))
                .check("detail reports the snapshot age", () -> VisualProbe.hudLineStartsWith("快照: 年龄 "))
                .screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.AIR.defaultBlockState());
    }
}
