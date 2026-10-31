package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.context",
    group = "visual",
    tags = {"visual", "feature", "context"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureContextScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.context: 上下文键值与消耗");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual context 0 100 0")
                .waitUntil(
                    "the context fixture is staged",
                    () -> VisualProbe.instanceCount() == 1 && VisualProbe.claimCount() == 1
                ).check("HUD 显示上下文修订与消耗", () -> VisualFeatureSupport.hasLine("上下文: 修订 1, 消耗 2/0, 键 9, 值 9（显示 0）"))
                .check("HUD 显示上下文键", () -> VisualFeatureSupport.hasLine("上下文键值: "))
                .check("HUD 显示选中的部件上下文", () -> VisualFeatureSupport.hasLine("上下文: "))
                .runCommand("cumulusvisual inspect 0 100 0")
                .waitUntil(
                    "the inspector shows context values",
                    () -> VisualProbe.inspectedPart() != null
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视上下文: "))
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视上下文值: "))
                )
                .check(
                    "the inspector reports revision, spend, keys and values",
                    () -> VisualProbe.hudLines().stream().anyMatch(
                        line -> line.startsWith("检视上下文: ")
                                && line.contains("修订 1")
                                && line.contains("消耗 2/0")
                                && line.contains("键 9")
                                && line.contains("值 9（显示 0）")
                    )
                )
                .check(
                    "the inspector hides generic context values",
                    () -> VisualProbe.hudLines().contains("检视上下文值: 无（仅通用键已隐藏）")
                            && VisualProbe.hudLines().stream()
                                    .noneMatch(line -> line.startsWith("检视上下文值: ") && line.contains("visual_budget="))
                ).check("the inspector reports findings", () -> VisualFeatureSupport.hasLine("检视发现: "))
                .runCommand("cumulusvisual inspect clear").screenshot("hud").frames(2).checkCaptured("hud")
                .screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
