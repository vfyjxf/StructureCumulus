package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.set",
    group = "visual",
    tags = {"visual", "feature", "set"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureSetScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.set: 结构集、卫星与链接");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual set 0 100 0").waitUntil(
            "the set fixture is staged",
            () -> VisualProbe.instanceCount() == 7
        ).check(
            "HUD 显示结构集摘要",
            () -> VisualFeatureSupport.hasLine("结构集: structurecumulus:visual_set")
                    && VisualProbe.hudLines().stream().anyMatch(line -> line.contains("成员 7") && line.contains("链接 6"))
        ).check(
            "HUD 显示声明与预算",
            () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("结构集: ") && line.contains("预算"))
        ).check("结构集链接已提交", () -> VisualProbe.highlightSummary().setLinks() == 6).screenshot("hud").frames(2)
                .checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
