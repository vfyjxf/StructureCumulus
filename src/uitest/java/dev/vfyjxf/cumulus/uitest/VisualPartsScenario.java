package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * V6 parts scenario: st年龄 the complex fixture and the grouped systems, then assert meaningful part
 * roles, descriptions, part-colored highlights and legend, selected-part HUD detail, the inspect card
 * via the deterministic command, and compact one-line part summaries.
 */
@ClientScenario(
    name = "visual.parts",
    group = "visual",
    tags = {"visual", "parts", "inspect"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class VisualPartsScenario {

    private static final BlockPos complexOrigin = VisualScenarioSupport.cell(0, 0, 0);
    private static final BlockPos groupOrigin = VisualScenarioSupport.cell(128, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualComplexFixtures.Fixture complex = VisualComplexFixtures
                .blastFurnace(VisualScenarioSupport.point(complexOrigin));
        VisualHierarchyFixtures.Fixture groups = VisualHierarchyFixtures
                .groups(VisualScenarioSupport.point(groupOrigin));
        build = build.guiScale(2).hideGui(false)
                .log("visual.parts: role colors, descriptions, selected-part detail and inspect card")
                .runCommand("cumulusvisual clearall");
        build = VisualComplexScenarioSupport.place(build, complex);
        LatticePoint complexRoot = complex.members().get(0).siting().origin();
        build = build.runCommand("gamemode creative @a").runCommand("cumulusvisual complex blast_furnace 0 100 0")
                .teleportPlayer(complexRoot.x() + 0.5D, complexRoot.y() + 2.0D, complexRoot.z() + 12.5D)
                .lookSweepFrames(
                    () -> VisualScenarioSupport.lookAt(new BlockPos(complexRoot.x(), complexRoot.y(), complexRoot.z())),
                    8,
                    null
                ).awaitClientChunk(complexOrigin.getX() >> 4, complexOrigin.getZ() >> 4).waitUntil(
                    "the complex fixture exposes every part",
                    () -> VisualProbe.instanceCount() == complex.members().size()
                            && VisualProbe.claimCount() == complex.claimRows()
                            && VisualProbe.parts().size() == complex.members().size()
                ).waitUntil(
                    "the HUD renders the part legend",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("部件 控制器 "))
                ).waitUntil("the crosshair selects a complex part", () -> VisualProbe.selectedInstance() != null).check(
                    "the complex fixture has one part per member",
                    () -> VisualProbe.parts().size() == complex.members().size()
                ).check("the complex parts use controller, preheater and port roles", () -> {
                    Set<VisualPartRole> roles = new HashSet<>();
                    for (VisualPart part : VisualProbe.parts()) {
                        roles.add(part.role());
                    }
                    return roles.contains(VisualPartRole.controller)
                            && roles.contains(VisualPartRole.preheater)
                            && roles.contains(VisualPartRole.port);
                }).check("every part has a meaningful description and a role color", () -> {
                    for (VisualPart part : VisualProbe.parts()) {
                        if (part.description().isBlank() || part.color() == 0) {
                            return false;
                        }
                    }
                    return true;
                }).check("the part legend names roles, colors and descriptions", () -> {
                    for (VisualPartRole role : Set
                            .of(VisualPartRole.controller, VisualPartRole.preheater, VisualPartRole.port)) {
                        String expected = "部件 " + role.displayName() + " " + VisualPartState.colorText(role.color())
                                + ": " + role.description();
                        if (!VisualProbe.hudLines().contains(expected)) {
                            return false;
                        }
                    }
                    return true;
                }).check("the highlight layer uses the part role colors", () -> {
                    Set<Integer> colors = new HashSet<>(VisualProbe.highlightColors());
                    for (VisualPart part : VisualProbe.parts()) {
                        if (!colors.contains(part.color())) {
                            return false;
                        }
                    }
                    return true;
                })
                .check(
                    "the selected instance reports its part detail",
                    () -> VisualProbe.hudLineStartsWith("部件: ") && VisualProbe.hudLineStartsWith("部件描述: ")
                )
                .check(
                    "the parts summary describes the role count",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("部件: " + complex.members().size() + " 个部件, "))
                ).screenshot("parts-hud").frames(2).checkCaptured("parts-hud").screenshot("parts-world").frames(2)
                .checkCaptured("parts-world")
                .runCommand("cumulusvisual inspect " + complexRoot.x() + " " + complexRoot.y() + " " + complexRoot.z())
                .timelineRecord("part-at-origin", () -> "partAt=" + VisualPartState.partAt(LatticePoint.of(0, 100, 0)))
                .timelineRecord(
                    "after-inspect",
                    () -> "inspected=" + VisualProbe.inspectedPart() + ", parts=" + VisualProbe.parts().size()
                ).check("the inspect command targets a part", () -> VisualProbe.inspectedPart() != null)
                .waitUntil(
                    "the inspect card reports the controller part",
                    () -> VisualProbe.inspectedPart() != null
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视: controller（控制器 "))
                )
                .check(
                    "the inspect card identifies the instance and claims",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视实例: "))
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视声明: "))
                )
                .check(
                    "the inspect card carries the part description",
                    () -> VisualProbe.hudLines().stream().anyMatch(
                        line -> line.startsWith("检视描述: ") && line.contains(VisualPartRole.controller.description())
                    )
                )
                .check(
                    "the inspect card reports the group and bounds",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视分组: "))
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视边界: "))
                ).screenshot("inspect-hud").frames(2).checkCaptured("inspect-hud").screenshot("inspect-world").frames(2)
                .checkCaptured("inspect-world").runCommand("cumulusvisual inspect clear")
                .waitUntil(
                    "the inspect card clears",
                    () -> VisualProbe.inspectedPart() == null
                            && VisualProbe.hudLines().stream().noneMatch(line -> line.startsWith("检视: "))
                ).runCommand("cumulusvisual hud compact").frames(3)
                .waitUntil(
                    "the compact HUD keeps a one-line part summary",
                    () -> VisualProbe.compact()
                            && VisualProbe.hudLines().stream()
                                    .anyMatch(line -> line.startsWith("部件: ") && line.contains(" 个部件, "))
                )
                .check(
                    "compact keeps the one-line part summary",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("部件: ") && line.contains(" 种角色"))
                ).screenshot("parts-compact").frames(2).checkCaptured("parts-compact")
                .runCommand("cumulusvisual hud detail").frames(3)
                .waitUntil("the detail HUD returns", () -> !VisualProbe.compact());
        build = VisualComplexScenarioSupport.clear(build, complex);
        build = VisualHierarchyScenarioSupport.place(build, groups);
        LatticePoint groupRoot = groups.members().get(0).siting().origin();
        build = build.runCommand("cumulusvisual clearall").runCommand("gamemode creative @a")
                .runCommand("cumulusvisual groups 128 100 0")
                .teleportPlayer(groupRoot.x() + 0.5D, groupRoot.y() + 2.0D, groupRoot.z() + 10.5D)
                .lookSweepFrames(
                    () -> VisualScenarioSupport.lookAt(new BlockPos(groupRoot.x(), groupRoot.y(), groupRoot.z())),
                    8,
                    null
                ).awaitClientChunk(groupOrigin.getX() >> 4, groupOrigin.getZ() >> 4)
                .waitUntil(
                    "the grouped fixture exposes root and member parts",
                    () -> VisualProbe.instanceCount() == groups.members().size()
                            && VisualProbe.parts().size() == groups.members().size()
                            && VisualProbe.groups().size() == 3
                ).check("the grouped fixture keeps three systems", () -> VisualProbe.groups().size() == 3)
                .check("the group parts include root and member roles", () -> {
                    Set<VisualPartRole> roles = new HashSet<>();
                    for (VisualPart part : VisualProbe.parts()) {
                        roles.add(part.role());
                    }
                    return roles.contains(VisualPartRole.root) && roles.contains(VisualPartRole.member);
                })
                .check(
                    "the grouped part legend is present",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("部件 根节点 "))
                )
                .check(
                    "the grouped part colors reach the highlights",
                    () -> VisualProbe.highlightColors().contains(VisualPartRole.root.color())
                ).screenshot("groups-hud").frames(2).checkCaptured("groups-hud").screenshot("groups-world").frames(2)
                .checkCaptured("groups-world")
                .runCommand("cumulusvisual inspect " + groupRoot.x() + " " + groupRoot.y() + " " + groupRoot.z())
                .waitUntil(
                    "the grouped inspect card identifies its group",
                    () -> VisualProbe.inspectedPart() != null
                            && VisualProbe.hudLines().stream()
                                    .anyMatch(line -> line.startsWith("检视分组: structurecumulus:alpha"))
                ).screenshot("groups-inspect").frames(2).checkCaptured("groups-inspect");
        build = VisualHierarchyScenarioSupport.clear(build, groups);
        build.runCommand("cumulusvisual clearall").runCommand("cumulusvisual hud detail");
    }
}
