package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Shared group/hierarchy staging, clean flyover and assertions for the three grouped scenarios. */
final class VisualHierarchyScenarioSupport {

    private static final Block[] goldPalette = {Blocks.GOLD_BLOCK, Blocks.YELLOW_CONCRETE, Blocks.SANDSTONE,
            Blocks.YELLOW_TERRACOTTA};
    private static final Block[] cyanPalette = {Blocks.DIAMOND_BLOCK, Blocks.CYAN_CONCRETE, Blocks.PRISMARINE,
            Blocks.CYAN_TERRACOTTA};
    private static final Block[] magentaPalette = {Blocks.AMETHYST_BLOCK, Blocks.PURPUR_BLOCK, Blocks.MAGENTA_CONCRETE,
            Blocks.MAGENTA_TERRACOTTA};
    private static final Block[] orbitalPalette = {Blocks.IRON_BLOCK, Blocks.CHEST, Blocks.HOPPER, Blocks.OBSERVER,
            Blocks.FURNACE};
    private static final Block[] hullPalette = {Blocks.BRICKS, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS,
            Blocks.GLASS};

    private VisualHierarchyScenarioSupport() {}

    static ScenarioBuilder define(
        ScenarioBuilder build,
        VisualHierarchyFixtures.Fixture fixture,
        String command,
        String log
    ) {
        List<VisualHierarchyFixtures.GroupDef> defs = fixture.groupDefs();
        build = build.guiScale(2).hideGui(false).log(log).runCommand("cumulusvisual clearall");
        build = place(build, fixture);
        LatticePoint root = fixture.members().get(0).siting().origin();
        build = build.runCommand("gamemode creative @a").runCommand(command)
                .teleportPlayer(root.x() + 0.5D, root.y() + 3.0D, root.z() + 8.5D)
                .awaitClientChunk(root.x() >> 4, root.z() >> 4)
                .waitUntil(
                    "the snapshot holds every grouped member and claim",
                    () -> VisualProbe.instanceCount() == fixture.members().size()
                            && VisualProbe.claimCount() == fixture.claimRows()
                )
                .waitUntil(
                    "the HUD renders the group and hierarchy summaries",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("分组: "))
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("层级: "))
                )
                .waitUntil(
                    "the highlight layer submits every grouped instance",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == fixture.members().size()
                )
                .waitUntil(
                    "the highlight layer submits every grouped cuboid",
                    () -> VisualProbe.highlightSummary().boundsCuboids() == fixture.boundsBoxes()
                )
                .waitUntil(
                    "the hierarchy links reach the highlight layer",
                    () -> VisualProbe.highlightSummary().setLinks() == fixture.linkCount()
                )
                .waitUntil(
                    "the highlight layer knows every group color",
                    () -> VisualProbe.highlightSummary().groupColors() == defs.size()
                )
                .check(
                    "every member is represented once",
                    () -> VisualProbe.instanceCount() == fixture.members().size()
                ).check("every claim row is represented", () -> VisualProbe.claimCount() == fixture.claimRows())
                .check(
                    "the HUD summary reports the group count",
                    () -> VisualProbe.hudLines()
                            .contains("分组: " + defs.size() + " 组, " + fixture.members().size() + " 节点")
                )
                .check(
                    "the hierarchy section reports node count, depth and links",
                    () -> VisualProbe.hierarchy().size() == fixture.members().size()
                            && VisualProbe.hierarchyDepth() == fixture.maxDepth()
                            && VisualProbe.hierarchyLinkCount() == fixture.linkCount()
                            && VisualProbe.hudLines().contains(
                                "层级: " + fixture.members().size() + " 节点, 最大深度 " + fixture.maxDepth() + ", 链接 "
                                        + fixture.linkCount()
                            )
                )
                .check(
                    "the HUD hides the full group and depth lists",
                    () -> VisualProbe.hudLines().stream().noneMatch(line -> line.startsWith("层级 深度 "))
                            && VisualProbe.hudLines().stream().filter(line -> line.startsWith("分组 ")).count() == 0
                )
                .check(
                    "the highlight layer colors every group",
                    () -> VisualProbe.highlightSummary().groupColors() == defs.size()
                            && VisualProbe.groupColorCount() == defs.size()
                )
                .check(
                    "parent-to-child links are submitted",
                    () -> VisualProbe.highlightSummary().setLinks() == fixture.linkCount()
                )
                .check(
                    "the highlight layer uses each group palette",
                    () -> VisualProbe.highlightColors()
                            .containsAll(defs.stream().map(VisualHierarchyFixtures.GroupDef::color).toList())
                ).timelineRecord(
                    "group-state",
                    () -> "groups=" + VisualProbe.groups().size() + ", nodes=" + VisualProbe.hierarchy().size()
                            + ", links=" + VisualProbe.hierarchyLinkCount() + ", colors="
                            + VisualProbe.highlightColors() + ", groupColors="
                            + VisualProbe.groups().stream().map(VisualGroup::color).toList() + ", "
                            + VisualScenarioSupport.describe(VisualProbe.highlightSummary())
                );
        build = cleanFlyover(build, fixture);
        BlockPos focus = new BlockPos(root.x(), root.y(), root.z());
        build = build.teleportPlayer(root.x() + 0.5D, root.y() + 2.0D, root.z() + 7.5D)
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(focus), 8, null).frames(2)
                .waitUntil("the crosshair selects a hierarchy member", () -> VisualProbe.selectedInstance() != null)
                .waitUntil(
                    "the HUD reports the selected group and node",
                    () -> VisualProbe.hudLineStartsWith("选中分组: ") && VisualProbe.hudLineStartsWith("选中节点: ")
                )
                .check(
                    "the selection belongs to a group",
                    () -> VisualProbe.selectedInstance() != null
                            && VisualGroupState.node(VisualProbe.selectedInstance().id()) != null
                ).check("the HUD reports the selected group id", () -> {
                    VisualHierarchyNode node = VisualProbe.selectedInstance() == null
                            ? null
                            : VisualGroupState.node(VisualProbe.selectedInstance().id());
                    return node != null
                            && VisualProbe.hudLines().stream()
                                    .anyMatch(line -> line.startsWith("选中分组: ") && line.contains(node.groupId()));
                }).check("the HUD reports the selected node depth and parent", () -> {
                    VisualProbe.selectedInstance();
                    VisualHierarchyNode node = VisualProbe.selectedInstance() == null
                            ? null
                            : VisualGroupState.node(VisualProbe.selectedInstance().id());
                    return node != null
                            && VisualProbe.hudLines().stream().anyMatch(
                                line -> line.startsWith("选中节点: ")
                                        && line.contains(node.label())
                                        && line.contains("深度 " + node.depth())
                                        && line.contains("父节点 ")
                            );
                })
                .check(
                    "the group highlight colors remain",
                    () -> VisualProbe.highlightSummary().groupColors() == defs.size()
                ).screenshot("hud").frames(2).checkCaptured("hud");
        build = clear(build, fixture);
        return build.runCommand("cumulusvisual clearall");
    }

    static ScenarioBuilder place(ScenarioBuilder build, VisualHierarchyFixtures.Fixture fixture) {
        int memberIndex = 0;
        for (VisualHierarchyFixtures.Member member : fixture.members()) {
            Block block = paletteFor(member.groupId(), memberIndex++);
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
        }
        return build;
    }

    private static ScenarioBuilder cleanFlyover(ScenarioBuilder build, VisualHierarchyFixtures.Fixture fixture) {
        Cuboid region = region(fixture);
        int span = Math.max(
            Math.max(region.max().x() - region.min().x(), region.max().y() - region.min().y()),
            region.max().z() - region.min().z()
        );
        double distance = switch (fixture.id()) {
            case "substructures" -> Math.max(20.0D, span * 1.4D);
            case "satellites" -> Math.max(14.0D, span * 1.0D);
            default -> Math.max(18.0D, span * 0.8D);
        };
        LatticePoint point = fixture.members().get(0).siting().origin();
        double cameraY = point.y() + Math.max(4.0D, span * 0.2D);
        double z = point.z() + 0.5D + distance;
        double westX = point.x() + 0.5D - distance * 0.6D;
        double eastX = point.x() + 0.5D + distance * 0.6D;
        BlockPos target = new BlockPos(
            (region.min().x() + region.max().x()) / 2,
            (region.min().y() + region.max().y()) / 2,
            (region.min().z() + region.max().z()) / 2
        );
        float[] west = angles(westX, cameraY, z, target);
        float[] east = angles(eastX, cameraY - 1.0D, z, target);
        build = build.runCommand("gamemode spectator @a").hideGui(true).runCommand("cumulusvisual tutorial hide")
                .runCommand("cumulusvisual hud hide").runCommand("cumulusvisual highlights hide").frames(3)
                .teleportPlayer(westX, cameraY, z)
                .awaitClientChunk(Math.floorDiv((int) Math.floor(westX), 16), Math.floorDiv((int) Math.floor(z), 16))
                .yawPitch(west[0], west[1]).frames(4).screenshot("flyover-west").frames(2)
                .teleportPlayer(eastX, cameraY - 1.0D, z)
                .awaitClientChunk(Math.floorDiv((int) Math.floor(eastX), 16), Math.floorDiv((int) Math.floor(z), 16))
                .yawPitch(east[0], east[1]).frames(4).screenshot("flyover-east").frames(2).screenshot("world").frames(2)
                .checkCaptured("world").runCommand("cumulusvisual highlights show").frames(3).screenshot("highlights")
                .frames(2).checkCaptured("highlights").hideGui(false).runCommand("cumulusvisual hud show").frames(3)
                .waitUntil("the HUD returns for the group capture", VisualProbe::hudVisible).frames(2);
        return build;
    }

    static ScenarioBuilder clear(ScenarioBuilder build, VisualHierarchyFixtures.Fixture fixture) {
        Cuboid region = region(fixture);
        return build.clearArea(
            region.min().x(),
            region.min().y(),
            region.min().z(),
            region.max().x() + 1,
            region.max().y() + 1,
            region.max().z() + 1
        );
    }

    private static Cuboid focusRegion(VisualHierarchyFixtures.Fixture fixture, String groupId) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (VisualHierarchyFixtures.Member member : fixture.members()) {
            if (!member.groupId().equals(groupId)) {
                continue;
            }
            for (Cuboid box : member.bounds().boxes()) {
                minX = Math.min(minX, box.min().x());
                minY = Math.min(minY, box.min().y());
                minZ = Math.min(minZ, box.min().z());
                maxX = Math.max(maxX, box.max().x());
                maxY = Math.max(maxY, box.max().y());
                maxZ = Math.max(maxZ, box.max().z());
            }
        }
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Cuboid region(VisualHierarchyFixtures.Fixture fixture) {
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
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Block paletteFor(String groupId, int index) {
        Block[] palette = groupId.endsWith(":alpha")
                ? goldPalette
                : groupId.endsWith(":beta")
                        ? cyanPalette
                        : groupId.endsWith(":gamma")
                                ? magentaPalette
                                : groupId.endsWith(":orbital") ? orbitalPalette : hullPalette;
        return palette[Math.floorMod(index, palette.length)];
    }

    private static float[] angles(double x, double y, double z, BlockPos target) {
        double eyeY = y + 1.62D;
        double dx = target.getX() + 0.5D - x;
        double dy = target.getY() + 0.5D - eyeY;
        double dz = target.getZ() + 0.5D - z;
        double ground = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, ground));
        return new float[]{yaw, pitch};
    }

    private static String colorText(int color) {
        return String.format("#%08X", color);
    }
}
