package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.nested",
    group = "visual",
    tags = {"visual", "feature", "nested"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureNestedScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.nested: 嵌套边界与实体窗口");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual complex nested 0 100 0")
                .waitUntil("the nested fixture is staged", () -> VisualProbe.instanceCount() == 3)
                .check("HUD 显示嵌套结构集", () -> VisualFeatureSupport.hasLine("结构集: "))
                .check("HUD 显示嵌套边界", () -> VisualFeatureSupport.hasLine("边界: "))
                .check("嵌套边界已高亮", () -> VisualProbe.highlightSummary().boundsCuboids() >= 3).screenshot("hud").frames(2)
                .checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
