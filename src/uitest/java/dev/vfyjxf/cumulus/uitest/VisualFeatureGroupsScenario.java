package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.groups",
    group = "visual",
    tags = {"visual", "feature", "groups"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureGroupsScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.groups: 分组、层级与部件");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual groups 0 100 0")
                .waitUntil(
                    "the groups fixture is staged",
                    () -> VisualProbe.instanceCount() == 24 && VisualProbe.parts().size() == 24
                ).check("HUD 显示分组统计", () -> VisualProbe.hudLines().contains("分组: 3 组, 24 节点"))
                .check("HUD 显示层级统计", () -> VisualFeatureSupport.hasLine("层级: "))
                .check(
                    "HUD 显示部件与角色",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("部件: 24 个部件"))
                ).check("分组颜色已提交", () -> VisualProbe.highlightSummary().groupColors() == 3).screenshot("hud").frames(2)
                .checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
