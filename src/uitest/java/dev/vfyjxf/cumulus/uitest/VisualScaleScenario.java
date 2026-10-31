package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * V5 scale scenario: 128 instances in eight systems, all with group context, a depth-three hierarchy,
 * truthful compact counts and highlight-cap lines, one focused group, and a clean world flyover.
 */
@ClientScenario(
    name = "visual.scale",
    group = "visual",
    tags = {"visual", "scale", "groups"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class VisualScaleScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(256, 0, 0);
    private static final String focusGroup = "structurecumulus:alpha_foundry";
    private static final Block[] groupBlocks = {Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.AMETHYST_BLOCK,
            Blocks.EMERALD_BLOCK, Blocks.ORANGE_CONCRETE, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.IRON_BLOCK};
    private static final int instanceCount = 128;
    private static final int perGroup = instanceCount / 8;
    private static final int claimRows = instanceCount * 2;
    private static final int boundsCap = 48;
    private static final int claimCellCap = 128;

    public void define(ScenarioBuilder build) {
        VisualHierarchyFixtures.Fixture fixture = VisualHierarchyFixtures
                .scale(VisualScenarioSupport.point(origin), instanceCount);
        build = build.guiScale(2).hideGui(false)
                .log("visual.scale: 128 instances in 8 groups with caps, compact group summary and focus")
                .runCommand("cumulusvisual clearall");
        build = place(build, fixture);
        LatticePoint root = fixture.members().get(0).siting().origin();
        BlockPos center = new BlockPos(origin.getX() + 140, origin.getY(), origin.getZ() + 5);
        build = build.runCommand("gamemode creative @a").runCommand("cumulusvisual scale 128 256 100 0")
                .teleportPlayer(root.x() + 0.5D, root.y() + 1.0D, root.z() + 12.5D)
                .lookSweepFrames(
                    () -> VisualScenarioSupport.lookAt(new BlockPos(root.x(), root.y(), root.z())),
                    4,
                    null
                ).awaitClientChunk(origin.getX() >> 4, origin.getZ() >> 4)
                .waitUntil(
                    "the snapshot holds all 128 scale instances and 256 claims",
                    () -> VisualProbe.instanceCount() == instanceCount && VisualProbe.claimCount() == claimRows
                )
                .waitUntil(
                    "the HUD renders the group and hierarchy summaries",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.groups().size() == 8
                            && VisualProbe.hudLines().contains("分组: 8 组, 128 节点")
                            && VisualProbe.hudLineStartsWith("层级: ")
                ).waitUntil("the highlight caps stay bounded", () -> {
                    VisualHighlightSummary summary = VisualProbe.highlightSummary();
                    return summary.boundsBoxes() == boundsCap && summary.claimCells() == claimCellCap;
                })
                .check(
                    "the fixture has eight groups",
                    () -> VisualProbe.groups().size() == 8 && VisualProbe.groupColorCount() == 8
                ).check("every scale instance is present", () -> VisualProbe.instanceCount() == instanceCount)
                .check("every scale claim row is present", () -> VisualProbe.claimCount() == claimRows)
                .check("every group has equal instances and claims", () -> {
                    int instances = 0;
                    int claims = 0;
                    for (VisualGroup group : VisualProbe.groups()) {
                        instances += group.instanceCount();
                        claims += group.claimCount();
                    }
                    return instances == instanceCount && claims == claimRows;
                })
                .check(
                    "the group summary reports the exact counts",
                    () -> VisualProbe.hudLines().contains("分组: 8 组, 128 节点")
                )
                .check(
                    "the HUD does not list every group",
                    () -> VisualProbe.hudLines().stream().noneMatch(line -> line.startsWith("分组 "))
                )
                .check(
                    "the hierarchy is depth three with one link per non-root",
                    () -> VisualProbe.hierarchy().size() == instanceCount
                            && VisualProbe.hierarchyDepth() == 3
                            && VisualProbe.hierarchyLinkCount() == instanceCount - 8
                )
                .check(
                    "the hierarchy HUD line reports depth and links",
                    () -> VisualProbe.hudLines().contains("层级: 128 节点, 最大深度 3, 链接 " + (instanceCount - 8))
                )
                .check(
                    "the highlight caps are truthful",
                    () -> VisualProbe.hudLines().contains(
                        "高亮上限: 边界 " + boundsCap + "/" + instanceCount + ", 声明格 " + claimCellCap + "/" + claimRows
                    )
                )
                .check(
                    "the bounds cap stays under the instance count",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == boundsCap
                )
                .check(
                    "the claim cap stays under the claim count",
                    () -> VisualProbe.highlightSummary().claimCells() == claimCellCap
                ).check("every group context value is present", () -> {
                    Set<String> groups = new HashSet<>();
                    for (VisualInstance instance : VisualProbe.snapshot().instances()) {
                        if (instance.contextValueCount() != 9
                                || instance.contextKeys().size() != 9
                                || !instance.contextKeys().contains("structurecumulus:visual_group")) {
                            return false;
                        }
                        for (String key : instance.contextKeys()) {
                            if (key.equals("structurecumulus:visual_group")) {
                                groups.add(key);
                            }
                        }
                    }
                    return groups.size() == 1;
                }).screenshot("hud-detail").frames(2).checkCaptured("hud-detail")
                .runCommand("cumulusvisual hud compact").frames(3)
                .waitUntil(
                    "the compact HUD shows scale groups",
                    () -> VisualProbe.compact()
                            && VisualProbe.hudLines().contains("实例: 128")
                            && VisualProbe.hudLines().stream()
                                    .anyMatch(line -> line.startsWith("分组: 8 组, 128 实例, 声明 256；层级 128 节点, 最大深度 3"))
                )
                .check(
                    "compact keeps the count lines",
                    () -> VisualProbe.hudLines().contains("实例: 128")
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("声明: 256 "))
                )
                .check(
                    "compact reports the group/hierarchy summary in one line",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("分组: 8 组, 128 实例, 声明 256；层级 128 节点, 最大深度 3"))
                )
                .check(
                    "compact keeps the cap line truthful",
                    () -> VisualProbe.hudLines().contains("高亮上限: 边界 48/128, 声明格 128/256")
                ).screenshot("hud-compact").frames(2).checkCaptured("hud-compact")
                .runCommand("cumulusvisual hud detail").frames(3)
                .waitUntil("the detail HUD returns", () -> !VisualProbe.compact());
        BlockPos rootBlock = new BlockPos(root.x(), root.y(), root.z());
        build = build.teleportPlayer(root.x() + 0.5D, root.y() + 2.0D, root.z() + 7.5D)
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(rootBlock), 8, null).frames(2)
                .waitUntil("the crosshair selects a member of the focused group", () -> {
                    VisualInstance selected = VisualProbe.selectedInstance();
                    if (selected == null) {
                        return false;
                    }
                    VisualHierarchyNode node = VisualGroupState.node(selected.id());
                    return node != null && node.groupId().equals(focusGroup);
                }).check("the selected node belongs to one group", () -> {
                    VisualInstance selected = VisualProbe.selectedInstance();
                    VisualHierarchyNode node = selected == null ? null : VisualGroupState.node(selected.id());
                    return node != null && node.groupId().equals(focusGroup) && node.depth() <= 3;
                })
                .check(
                    "the HUD reports only the selected group",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("选中分组: ") && line.contains(focusGroup))
                            && VisualProbe.hudLines().stream().noneMatch(line -> line.startsWith("分组 "))
                )
                .check(
                    "the HUD reports the selected hierarchy node and parent",
                    () -> VisualProbe.hudLines().stream().anyMatch(
                        line -> line.startsWith("选中节点: ") && line.contains("深度 ") && line.contains("父节点 ")
                    )
                ).check("the focused group keeps its counts", () -> {
                    VisualGroup group = VisualProbe.groups().stream()
                            .filter(candidate -> candidate.id().equals(focusGroup)).findFirst().orElse(null);
                    return group != null && group.instanceCount() == perGroup && group.claimCount() == perGroup * 2;
                }).check("focus keeps all group colors", () -> VisualProbe.highlightSummary().groupColors() == 8)
                .screenshot("hud-focus").frames(2).checkCaptured("hud-focus").runCommand("gamemode spectator @a")
                .hideGui(true).runCommand("cumulusvisual hud hide").runCommand("cumulusvisual highlights hide")
                .frames(3).teleportPlayer(center.getX() + 0.5D, center.getY() + 26.0D, center.getZ() + 55.5D)
                .awaitClientChunk(center.getX() >> 4, center.getZ() >> 4)
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(center), 8, null).frames(4).screenshot("flyover")
                .frames(2).checkCaptured("flyover").runCommand("cumulusvisual highlights show").frames(3)
                .screenshot("world").frames(2).checkCaptured("world").hideGui(false)
                .runCommand("cumulusvisual hud show").frames(3)
                .waitUntil("the HUD returns after the flyover", VisualProbe::hudVisible).screenshot("hud").frames(2)
                .checkCaptured("hud");
        build = clear(build, fixture);
        build.runCommand("cumulusvisual clearall").runCommand("cumulusvisual hud detail");
    }

    private static ScenarioBuilder place(ScenarioBuilder build, VisualHierarchyFixtures.Fixture fixture) {
        int count = 0;
        for (VisualHierarchyFixtures.Member member : fixture.members()) {
            Block block = groupBlocks[count / perGroup % groupBlocks.length];
            for (Cuboid box : member.bounds().boxes()) {
                build = build.fill(
                    box.min().x(),
                    box.min().y(),
                    box.min().z(),
                    box.max().x() - 1,
                    box.max().y() - 1,
                    box.max().z() - 1,
                    block.defaultBlockState()
                );
            }
            count++;
        }
        return build;
    }

    private static ScenarioBuilder clear(ScenarioBuilder build, VisualHierarchyFixtures.Fixture fixture) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (VisualHierarchyFixtures.Member member : fixture.members()) {
            for (Cuboid box : member.bounds().boxes()) {
                minX = Math.min(minX, box.min().x());
                minY = Math.min(minY, box.min().y());
                minZ = Math.min(minZ, box.min().z());
                maxX = Math.max(maxX, box.max().x());
                maxY = Math.max(maxY, box.max().y());
                maxZ = Math.max(maxZ, box.max().z());
            }
        }
        return build.clearArea(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
