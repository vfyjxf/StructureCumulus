package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** V2.5 context scenario: one 共享 instance publishes nine scalar context keys and values. */
@ClientScenario(
    name = "visual.context",
    group = "visual",
    tags = {"visual", "context"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualContextScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);
    private static final String contextValuePrefix = "上下文键值: ";

    public void define(ScenarioBuilder build) {
        build.guiScale(2).hideGui(false)
                .log("visual.context: one instance with two context keys; HUD summary, detail and compact toggle")
                .runCommand("cumulusvisual clearall")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual context 0 100 0").teleportPlayer(0.5, 101.0, 5.5)
                .yawPitch(180.0F, 22.976944F).awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds the context fixture",
                    () -> VisualProbe.instanceCount() == 1 && VisualProbe.claimCount() == 1
                )
                .waitUntil(
                    "the HUD renders the context keys",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.hudLines().contains("上下文: 修订 1, 消耗 2/0, 键 9, 值 9（显示 0）")
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith(contextValuePrefix))
                ).check("the fixture has one instance", () -> VisualProbe.instanceCount() == 1)
                .check("the fixture has one 共享 row", () -> VisualProbe.claimCount() == 1)
                .check(
                    "the HUD summarises the 共享 row",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("声明: 1 (修订 ") && line.contains("共享 1, 独占 0)"))
                )
                .check(
                    "the HUD reports the full context counts and the displayed semantic count",
                    () -> VisualProbe.hudLines().contains("上下文: 修订 1, 消耗 2/0, 键 9, 值 9（显示 0）")
                )
                .check(
                    "the HUD hides every generic key and shows the placeholder",
                    () -> VisualProbe.hudLines().stream().filter(line -> line.startsWith(contextValuePrefix)).count()
                            == 1
                            && VisualProbe.hudLines().contains("上下文键值: 无（仅通用键已隐藏）")
                ).check("the HUD reports a snapshot age", () -> VisualProbe.hudLineStartsWith("快照: 年龄 "))
                .check(
                    "the HUD reports the snapshot tick and 选中 source",
                    () -> VisualProbe.hudLines().stream().anyMatch(
                        line -> line.startsWith("快照: 年龄 ") && line.contains(", 时刻 ") && line.contains(", 选中 ")
                    )
                )
                .check(
                    "the HUD reports the hovered cell",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("悬停: ") && line.contains(" 于 (0,100"))
                ).check("the HUD reports an events placeholder", () -> VisualProbe.hudLineStartsWith("事件: "))
                .check("the HUD reports that the fixture has no set", () -> VisualProbe.hudLines().contains("结构集: 无"))
                .runCommand("cumulusvisual hud compact").frames(3)
                .waitUntil(
                    "the compact HUD drops the detail column",
                    () -> VisualProbe.compact()
                            && VisualProbe.hudLines().contains("实例: 1")
                            && !VisualProbe.hudLineStartsWith("所有者: ")
                ).check("compact keeps the summary", () -> VisualProbe.hudModel().compact())
                .check("compact hides the context details", () -> !VisualProbe.hudLineStartsWith(contextValuePrefix))
                .screenshot("hud-compact").frames(2).checkCaptured("hud-compact").runCommand("cumulusvisual hud detail")
                .frames(3)
                .waitUntil(
                    "the detail HUD returns",
                    () -> !VisualProbe.compact()
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith(contextValuePrefix))
                ).check("detail restores the selected instance", () -> VisualProbe.hudLineStartsWith("所有者: "))
                .check(
                    "detail restores the context details",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith(contextValuePrefix))
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.AIR.defaultBlockState());
    }
}
