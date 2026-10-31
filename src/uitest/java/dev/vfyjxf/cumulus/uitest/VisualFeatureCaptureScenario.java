package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.capture",
    group = "visual",
    tags = {"visual", "feature", "capture"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureCaptureScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.capture: 快照与报告");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual context 0 100 0")
                .waitUntil(
                    "the capture fixture is staged",
                    () -> VisualProbe.instanceCount() == 1 && VisualProbe.claimCount() == 1
                ).check("HUD 显示快照年龄", () -> VisualFeatureSupport.hasLine("快照: 年龄 "))
                .check("HUD 显示报告结构集", () -> VisualFeatureSupport.hasLine("结构集: "))
                .check("HUD 显示高亮计数", () -> VisualFeatureSupport.hasLine("高亮: ")).screenshot("hud").frames(2)
                .checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
