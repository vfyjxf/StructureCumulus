package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.lifecycle",
    group = "visual",
    tags = {"visual", "feature", "lifecycle"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureLifecycleScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.lifecycle: 草稿到捕获的流程");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual context 0 100 0")
                .waitUntil("the context part is staged", () -> VisualProbe.instanceCount() == 1)
                .runCommand("cumulusvisual flow draft").runCommand("cumulusvisual flow settle")
                .runCommand("cumulusvisual flow capture")
                .waitUntil(
                    "the flow reaches capture",
                    () -> VisualProbe.flow() != null
                            && VisualProbe.flow().current() == VisualFlowModel.Phase.capture
                            && VisualProbe.flow().completedCount() == 6
                ).check("七阶段流程已到捕获", () -> VisualProbe.flow().phaseCount() == 7)
                .check(
                    "HUD 显示中文流程条",
                    () -> VisualFeatureSupport.hasLine("流程: ") && VisualFeatureSupport.hasLine("流程阶段: 捕获 ")
                ).check("HUD 显示流程颜色说明", () -> VisualFeatureSupport.hasLine("流程颜色: 草稿=琥珀")).screenshot("hud").frames(2)
                .checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
