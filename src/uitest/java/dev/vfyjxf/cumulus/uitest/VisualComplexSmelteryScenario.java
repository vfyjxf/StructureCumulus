package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;

/** Normal multiblock example: Tinkers-style smeltery with interior, tank and drain contexts. */
@ClientScenario(
    name = "visual.complex.smeltery",
    group = "visual",
    tags = {"visual", "complex", "industrial"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualComplexSmelteryScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualIndustrialFixtures.Fixture fixture = VisualIndustrialFixtures
                .smeltery(VisualScenarioSupport.point(origin));
        LatticePoint root = fixture.members().get(0).siting().origin();
        build = build.guiScale(2).hideGui(false)
                .log("visual.complex.smeltery: 3x3x3 interior, walls, drains and tank contexts")
                .runCommand("cumulusvisual clearall");
        build = VisualIndustrialScenarioSupport.place(build, fixture);
        build = VisualIndustrialScenarioSupport.view(build, fixture).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual complex smeltery 0 100 0")
                .waitUntil(
                    "the smeltery parts are staged",
                    () -> VisualProbe.instanceCount() == fixture.members().size()
                            && VisualProbe.claimCount() == fixture.claimRows()
                            && VisualProbe.parts().size() == fixture.members().size()
                ).waitUntil(
                    "the HUD reports the smeltery set and selection",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.selectedInstance() != null
                            && VisualProbe.hudLineStartsWith("结构集: ")
                ).check("the smeltery has nine parts", () -> VisualProbe.parts().size() == 9)
                .check("the smeltery HUD reports dimensions", () -> VisualProbe.hudContextValue("dimensions=3x3x3"))
                .check(
                    "the smeltery HUD reports the interior volume",
                    () -> VisualProbe.hudContextValue("interior_volume=27")
                )
                .check(
                    "the smeltery HUD reports tank capacity and fill",
                    () -> VisualProbe.hudContextValue("tank_capacity=4000")
                            && VisualProbe.hudContextValue("tank_fill=1200")
                )
                .check(
                    "the smeltery HUD reports smeltable and alloy flags",
                    () -> VisualProbe.hudContextValue("smeltable=是") && VisualProbe.hudContextValue("alloy=是")
                )
                .check(
                    "the smeltery context block is paired to five lines",
                    () -> VisualProbe.hudLines().stream().filter(line -> line.startsWith("上下文键值: ")).count() == 5
                )
                .check(
                    "the smeltery part colors reach the highlight layer",
                    () -> VisualProbe.highlightColors()
                            .containsAll(fixture.members().stream().map(member -> member.role().color()).toList())
                ).runCommand("cumulusvisual inspect 0 100 0")
                .waitUntil(
                    "the smeltery inspect card carries context",
                    () -> VisualProbe.inspectedPart() != null
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视上下文: "))
                )
                .check(
                    "the smeltery inspect card reports dimensions and volume",
                    () -> VisualProbe.hudInspectContextValue("dimensions=3x3x3")
                            && VisualProbe.hudInspectContextValue("interior_volume=27")
                )
                .check(
                    "the smeltery inspect card reports max temperature",
                    () -> VisualProbe.hudInspectContextValue("max_temperature=800")
                ).screenshot("inspect-hud").frames(2).checkCaptured("inspect-hud")
                .runCommand("cumulusvisual inspect clear").screenshot("hud").frames(2).checkCaptured("hud")
                .screenshot("world").frames(2).checkCaptured("world").runCommand("gamemode spectator @a").hideGui(true)
                .runCommand("cumulusvisual hud hide").runCommand("cumulusvisual highlights hide").frames(3)
                .teleportPlayer(root.x() - 4.5D, root.y() + 4.5D, root.z() - 6.5D)
                .lookSweepFrames(
                    () -> VisualScenarioSupport.lookAt(new BlockPos(root.x(), root.y() + 1, root.z() + 2)),
                    10,
                    null
                ).frames(6).screenshot("flyover").frames(2).checkCaptured("flyover").hideGui(false)
                .runCommand("cumulusvisual hud show").runCommand("cumulusvisual highlights show");
        build = VisualIndustrialScenarioSupport.clear(build, fixture).runCommand("cumulusvisual clearall");
    }
}
