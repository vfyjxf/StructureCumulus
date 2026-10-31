package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.move",
    group = "visual",
    tags = {"visual", "feature", "move"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureMoveScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.move: 移动并平移声明");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual pair 0 100 0")
                .waitUntil(
                    "the pair is staged",
                    () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 2
                ).runCommand("cumulusvisual move 0 100 0")
                .waitUntil(
                    "the move lifecycle reaches the HUD",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("事件: ") && line.contains("最近 移动"))
                ).check("移动后实例数不变", () -> VisualProbe.instanceCount() == 2)
                .check("移动后声明数不变", () -> VisualProbe.claimCount() == 2)
                .check("HUD 显示移动事件", () -> VisualFeatureSupport.hasLine("事件: 移动 ")).screenshot("hud").frames(2)
                .checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
