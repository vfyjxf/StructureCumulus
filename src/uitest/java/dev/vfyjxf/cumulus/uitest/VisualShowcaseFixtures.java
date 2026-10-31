package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.ServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stages the large all-in-one free-roam showcase: zones, labels, roads and a central overlook. */
final class VisualShowcaseFixtures {

    static final LatticePoint spawn = LatticePoint.of(0, 108, 0);
    private static final int groundY = 99;

    private VisualShowcaseFixtures() {}

    record Zone(
        String id,
        String displayName,
        String kind,
        String description,
        LatticePoint origin,
        Cuboid area,
        int instanceCount,
        int claimCount,
        int partCount,
        int groupCount,
        VisualComplexFixtures.Fixture complex,
        VisualHierarchyFixtures.Fixture hierarchy,
        VisualIndustrialFixtures.Fixture industrial
    ) {
        Zone {
            int kinds = (complex == null ? 0 : 1) + (hierarchy == null ? 0 : 1) + (industrial == null ? 0 : 1);
            if (kinds != 1) {
                throw new IllegalArgumentException("a showcase zone has exactly one fixture kind");
            }
        }
    }

    record Showcase(List<Zone> zones, int instances, int claims, int parts, int groups) {
        Showcase {
            zones = List.copyOf(zones);
        }
    }

    /** 十项库能力展示区的确定性布局；命令与场景共用，不触碰服务器状态。 */
    static Showcase planned() {
        List<Zone> zones = new ArrayList<>();
        addComplex(
            zones,
            "lifecycle",
            "生命周期",
            "生命周期",
            "草稿→检查→结算→形成→声明→上下文→捕获的完整流程",
            LatticePoint.of(-90, 100, -90),
            VisualComplexFixtures.blastFurnace(LatticePoint.of(-90, 100, -90))
        );
        addComplex(
            zones,
            "claims",
            "声明账本",
            "声明账本",
            "共享与独占声明、修订号和预算额度",
            LatticePoint.of(-30, 100, -90),
            VisualComplexFixtures.portEnvelope(LatticePoint.of(-30, 100, -90))
        );
        addHierarchy(
            zones,
            "context",
            "上下文",
            "上下文",
            "实例上下文键、修订和消耗计量",
            LatticePoint.of(30, 100, -90),
            VisualHierarchyFixtures.satellites(LatticePoint.of(30, 100, -90))
        );
        addComplex(
            zones,
            "set_satellites",
            "结构集与卫星",
            "结构集与卫星",
            "结构集根节点、卫星成员和层级链接",
            LatticePoint.of(90, 100, -90),
            VisualComplexFixtures.logicalSubnetwork(LatticePoint.of(90, 100, -90))
        );
        addComplex(
            zones,
            "nested",
            "嵌套范围",
            "嵌套范围",
            "嵌套框架、子结构和多层边界范围",
            LatticePoint.of(-90, 100, -30),
            VisualComplexFixtures.nestedFrame(LatticePoint.of(-90, 100, -30))
        );
        addHierarchy(
            zones,
            "capture",
            "捕获与报告",
            "捕获与报告",
            "快照捕获、报告视图和实例统计",
            LatticePoint.of(-30, 100, -30),
            VisualHierarchyFixtures.groups(LatticePoint.of(-30, 100, -30))
        );
        addHierarchy(
            zones,
            "move_unload",
            "移动与卸载",
            "移动与卸载",
            "移动、卸载、重新加载与失效残影",
            LatticePoint.of(30, 100, -30),
            VisualHierarchyFixtures.satellites(LatticePoint.of(30, 100, -30))
        );
        addHierarchy(
            zones,
            "conflict",
            "冲突与共享",
            "冲突与共享",
            "共享声明、独占声明和冲突高亮",
            LatticePoint.of(90, 100, -30),
            VisualHierarchyFixtures.groups(LatticePoint.of(90, 100, -30))
        );
        addHierarchy(
            zones,
            "orientation",
            "方向与镜像",
            "方向与镜像",
            "旋转、倾斜、镜像和朝向覆盖",
            LatticePoint.of(-90, 100, 30),
            VisualHierarchyFixtures.satellites(LatticePoint.of(-90, 100, 30))
        );
        addHierarchy(
            zones,
            "groups_scale",
            "分组与规模",
            "分组与规模",
            "分组颜色、规模统计和高亮上限",
            LatticePoint.of(-30, 100, 30),
            VisualHierarchyFixtures.groups(LatticePoint.of(-30, 100, 30))
        );
        addIndustrial(
            zones,
            "smeltery",
            "仿匠魂冶炼炉",
            "多方块",
            "5x5 外壳、3x3x3 内腔、炉壁、双排液口与储罐容量/温度上下文",
            LatticePoint.of(-90, 100, 90),
            VisualIndustrialFixtures.smeltery(LatticePoint.of(-90, 100, 90))
        );
        addIndustrial(
            zones,
            "ebf",
            "仿 GT 高炉",
            "多方块",
            "3x3x4 外壳、1x1x4 内腔、两层线圈、能源/输入/输出接口与维护舱上下文",
            LatticePoint.of(-30, 100, 90),
            VisualIndustrialFixtures.ebf(LatticePoint.of(-30, 100, 90))
        );
        return new Showcase(
            zones,
            zones.stream().mapToInt(Zone::instanceCount).sum(),
            zones.stream().mapToInt(Zone::claimCount).sum(),
            zones.stream().mapToInt(Zone::partCount).sum(),
            zones.stream().mapToInt(Zone::groupCount).sum()
        );
    }

    static boolean stage(ServerLevel level) {
        ServerState state = VisualFixtureSupport.state();
        if (state == null) {
            return false;
        }
        Showcase plan = planned();
        VisualFixtureSupport.clearAll(level);
        List<VisualPart> parts = new ArrayList<>();
        List<VisualGroup> groups = new ArrayList<>();
        List<VisualHierarchyNode> nodes = new ArrayList<>();
        for (Zone zone : plan.zones()) {
            if (zone.complex() != null) {
                parts.addAll(VisualComplexFixtures.stageOnly(state, level, zone.complex()));
            } else if (zone.industrial() != null) {
                VisualIndustrialFixtures.Staged staged = VisualIndustrialFixtures
                        .stageOnly(state, level, zone.industrial());
                parts.addAll(staged.parts());
                groups.addAll(staged.groups());
                nodes.addAll(staged.nodes());
            } else {
                VisualHierarchyFixtures.Staged staged = VisualHierarchyFixtures
                        .stageOnly(state, level, zone.hierarchy());
                parts.addAll(staged.parts());
                groups.addAll(staged.groups());
                nodes.addAll(staged.nodes());
            }
        }
        buildEnvironment(level, plan.zones());
        placeParts(level, parts, nodes);
        for (Zone zone : plan.zones()) {
            sign(level, zone);
        }
        signOverlook(level);
        VisualPartState.set(parts);
        VisualGroupState.set(groups, nodes);
        VisualClientState.setSetSummary(null);
        VisualShowcaseState.activate(
            plan.zones().stream()
                    .map(
                        zone -> new VisualShowcaseState.Zone(
                            zone.id(),
                            zone.displayName(),
                            zone.kind(),
                            zone.origin(),
                            zone.area(),
                            zone.instanceCount(),
                            zone.claimCount(),
                            zone.partCount()
                        )
                    ).toList()
        );
        VisualClientState.setCompact(true);
        return true;
    }

    private static void addIndustrial(
        List<Zone> zones,
        String id,
        String display,
        String kind,
        String description,
        LatticePoint origin,
        VisualIndustrialFixtures.Fixture fixture
    ) {
        Cuboid area = region(fixture.members().stream().map(VisualIndustrialFixtures.Member::bounds).toList());
        zones.add(
            new Zone(
                id,
                display,
                kind,
                description,
                origin,
                area,
                fixture.members().size(),
                fixture.claimRows(),
                fixture.members().size(),
                1,
                null,
                null,
                fixture
            )
        );
    }

    private static void addComplex(
        List<Zone> zones,
        String id,
        String display,
        String kind,
        String description,
        LatticePoint origin,
        VisualComplexFixtures.Fixture fixture
    ) {
        Cuboid area = region(fixture.members().stream().map(VisualComplexFixtures.Member::bounds).toList());
        zones.add(
            new Zone(
                id,
                display,
                kind,
                description,
                origin,
                area,
                fixture.members().size(),
                fixture.claimRows(),
                fixture.members().size(),
                0,
                fixture,
                null,
                null
            )
        );
    }

    private static void addHierarchy(
        List<Zone> zones,
        String id,
        String display,
        String kind,
        String description,
        LatticePoint origin,
        VisualHierarchyFixtures.Fixture fixture
    ) {
        Cuboid area = region(fixture.members().stream().map(VisualHierarchyFixtures.Member::bounds).toList());
        zones.add(
            new Zone(
                id,
                display,
                kind,
                description,
                origin,
                area,
                fixture.members().size(),
                fixture.claimRows(),
                fixture.members().size(),
                fixture.groupDefs().size(),
                null,
                fixture,
                null
            )
        );
    }

    private static void placeParts(ServerLevel level, List<VisualPart> parts, List<VisualHierarchyNode> nodes) {
        Map<InstanceId, String> groupByInstance = new HashMap<>();
        for (VisualHierarchyNode node : nodes) {
            groupByInstance.put(node.id(), node.groupId());
        }
        Map<String, Block> groupBlocks = Map.of(
            "structurecumulus:alpha",
            Blocks.GOLD_BLOCK,
            "structurecumulus:beta",
            Blocks.DIAMOND_BLOCK,
            "structurecumulus:gamma",
            Blocks.AMETHYST_BLOCK,
            "structurecumulus:orbital",
            Blocks.PRISMARINE_BRICKS,
            "structurecumulus:hull",
            Blocks.BRICKS
        );
        for (VisualPart part : parts) {
            Cuboid first = part.bounds().boxes().get(0);
            level.getChunk(Math.floorDiv(first.min().x(), 16), Math.floorDiv(first.min().z(), 16));
            Block block;
            if (part.role() == VisualPartRole.member) {
                String group = groupByInstance.get(part.instance());
                block = group == null ? Blocks.STONE : groupBlocks.getOrDefault(group, Blocks.STONE);
            } else {
                block = switch (part.role()) {
                    case controller -> Blocks.BLAST_FURNACE;
                    case preheater -> Blocks.BRICKS;
                    case port -> Blocks.HOPPER;
                    case root -> Blocks.EMERALD_BLOCK;
                    case satellite -> Blocks.PRISMARINE_BRICKS;
                    case subSatellite -> Blocks.PURPUR_BLOCK;
                    case substructure -> Blocks.DEEPSLATE_TILES;
                    case member -> Blocks.STONE;
                    case wall -> Blocks.POLISHED_BLACKSTONE_BRICKS;
                    case casing -> Blocks.IRON_BLOCK;
                    case window -> Blocks.GLASS;
                    case drain -> Blocks.HOPPER;
                    case tank -> Blocks.COPPER_BLOCK;
                    case coil -> part.name().contains("上") ? Blocks.COPPER_BLOCK : Blocks.MAGMA_BLOCK;
                    case hatch -> Blocks.COPPER_BLOCK;
                    case muffler -> Blocks.HOPPER;
                    case maintenance -> Blocks.IRON_TRAPDOOR;
                };
            }
            for (Cuboid box : part.bounds().boxes()) {
                fill(
                    level,
                    box.min().x(),
                    box.min().y(),
                    box.min().z(),
                    box.max().x() - 1,
                    box.max().y() - 1,
                    box.max().z() - 1,
                    block
                );
            }
        }
    }

    private static void buildEnvironment(ServerLevel level, List<Zone> zones) {
        fill(level, -12, groundY, -12, 12, groundY, 12, Blocks.POLISHED_ANDESITE);
        for (Zone zone : zones) {
            Block pad = switch (zone.kind()) {
                case "结构集与卫星", "嵌套范围" -> Blocks.STONE_BRICKS;
                case "捕获与报告", "分组与规模" -> Blocks.SMOOTH_STONE;
                case "上下文", "方向与镜像" -> Blocks.POLISHED_DIORITE;
                default -> Blocks.DEEPSLATE_TILES;
            };
            fill(
                level,
                zone.area().min().x() - 2,
                groundY,
                zone.area().min().z() - 2,
                zone.area().max().x() + 2,
                groundY,
                zone.area().max().z() + 2,
                pad
            );
            road(
                level,
                zone.area().min().x() + (zone.area().max().x() - zone.area().min().x()) / 2,
                zone.area().min().z() + (zone.area().max().z() - zone.area().min().z()) / 2
            );
        }
        fill(level, -7, 107, -7, 7, 107, 7, Blocks.POLISHED_DEEPSLATE);
        fill(level, -7, 108, -7, 7, 108, -7, Blocks.GLASS);
        fill(level, -7, 108, 7, 7, 108, 7, Blocks.GLASS);
        fill(level, -7, 108, -7, -7, 108, 7, Blocks.GLASS);
        fill(level, 7, 108, -7, 7, 108, 7, Blocks.GLASS);
        for (int[] pillar : new int[][]{{-6, -6}, {-6, 6}, {6, -6}, {6, 6}}) {
            fill(level, pillar[0], groundY, pillar[1], pillar[0], 106, pillar[1], Blocks.QUARTZ_PILLAR);
        }
        level.setBlockAndUpdate(new BlockPos(0, 108, 0), Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void road(ServerLevel level, int x, int z) {
        int fromX = Math.min(x, 0);
        int toX = Math.max(x, 0);
        int fromZ = Math.min(z, 0);
        int toZ = Math.max(z, 0);
        fill(level, fromX - 1, groundY, z - 1, toX + 1, groundY, z + 1, Blocks.SMOOTH_STONE);
        fill(level, x - 1, groundY, fromZ - 1, x + 1, groundY, toZ + 1, Blocks.SMOOTH_STONE);
    }

    private static void sign(ServerLevel level, Zone zone) {
        int x = zone.area().max().x() + 3;
        int z = zone.area().max().z() + 3;
        level.setBlockAndUpdate(new BlockPos(x, groundY + 1, z), Blocks.OAK_FENCE.defaultBlockState());
        BlockPos signPos = new BlockPos(x, groundY + 2, z);
        level.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
        setSign(
            level,
            signPos,
            zone.displayName(),
            zone.description(),
            "实例 " + zone.instanceCount() + " 部件 " + zone.partCount(),
            "右键部件查看详情"
        );
    }

    private static void signOverlook(ServerLevel level) {
        BlockPos signPos = new BlockPos(0, 108, -6);
        level.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
        setSign(level, signPos, "可视化总览", "十项库能力演示", "右键部件查看详情", "/cumulusvisual showcase 重置");
    }

    private static void setSign(
        ServerLevel level,
        BlockPos pos,
        String line0,
        String line1,
        String line2,
        String line3
    ) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return;
        }
        SignText text = new SignText().setMessage(0, Component.literal(line0)).setMessage(1, Component.literal(line1))
                .setMessage(2, Component.literal(line2)).setMessage(3, Component.literal(line3));
        sign.setText(text, true);
        sign.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    private static Cuboid region(List<CuboidSet> bounds) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (CuboidSet set : bounds) {
            for (Cuboid box : set.boxes()) {
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

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 0);
                }
            }
        }
    }
}
