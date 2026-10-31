package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.ServerState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Realistic normal-multiblock examples: abstract Tinkers-style smeltery and GT-style EBF. */
final class VisualIndustrialFixtures {

    private static final OwnerId owner = OwnerId.of(NamespacedId.of("structurecumulus", "visual_industrial_owner"));
    private static final String groupId = "structurecumulus:industrial";
    private static final String groupName = "工业多方块";

    private VisualIndustrialFixtures() {}

    record Member(String label, VisualPartRole role, Siting siting, CuboidSet bounds) {}

    record Fixture(
        String id,
        String displayName,
        String system,
        String dimensions,
        List<Member> members,
        List<VisualSetLink> links,
        int budgetLimit,
        Map<ContextKey<?>, Object> semantics
    ) {
        Fixture {
            members = List.copyOf(members);
            links = List.copyOf(links);
            semantics = Map.copyOf(semantics);
        }

        int claimRows() {
            return Math.toIntExact(members.stream().mapToLong(member -> member.bounds().volume()).sum());
        }

        int boundsBoxes() {
            return members.stream().mapToInt(member -> member.bounds().boxCount()).sum();
        }
    }

    record Staged(List<VisualPart> parts, List<VisualGroup> groups, List<VisualHierarchyNode> nodes) {
        Staged {
            parts = List.copyOf(parts);
            groups = List.copyOf(groups);
            nodes = List.copyOf(nodes);
        }
    }

    static Fixture smeltery(LatticePoint origin) {
        List<Member> members = new ArrayList<>();
        members.add(member("冶炼炉控制器", VisualPartRole.controller, origin, box(origin, 1, 1, 1)));
        members.add(member("炉底", VisualPartRole.wall, at(origin, -2, -1, 0), box(at(origin, -2, -1, 0), 5, 1, 5)));
        members.add(member("后炉壁", VisualPartRole.wall, at(origin, -1, 0, 4), box(at(origin, -1, 0, 4), 3, 3, 1)));
        members.add(member("左炉壁", VisualPartRole.wall, at(origin, -2, 0, 0), box(at(origin, -2, 0, 0), 1, 3, 5)));
        members.add(member("右炉壁", VisualPartRole.wall, at(origin, 2, 0, 0), box(at(origin, 2, 0, 0), 1, 3, 5)));
        members.add(
            member(
                "前炉壁",
                VisualPartRole.wall,
                at(origin, -1, 1, 0),
                CuboidSet.of(Cuboid.of(at(origin, -1, 1, 0), at(origin, 2, 3, 1)))
            )
        );
        members.add(member("排液口甲", VisualPartRole.drain, at(origin, -1, 0, 0), box(at(origin, -1, 0, 0), 1, 1, 1)));
        members.add(member("排液口乙", VisualPartRole.drain, at(origin, 1, 0, 0), box(at(origin, 1, 0, 0), 1, 1, 1)));
        members.add(member("熔融储罐", VisualPartRole.tank, at(origin, -4, 0, 2), box(at(origin, -4, 0, 2), 2, 2, 2)));
        Map<ContextKey<?>, Object> semantics = new LinkedHashMap<>();
        semantics.put(VisualContexts.dimensionsKey, "3x3x3");
        semantics.put(VisualContexts.interiorVolumeKey, 27);
        semantics.put(VisualContexts.wallCountKey, 5);
        semantics.put(VisualContexts.drainCountKey, 2);
        semantics.put(VisualContexts.tankCapacityKey, 4000);
        semantics.put(VisualContexts.tankFillKey, 1200);
        semantics.put(VisualContexts.maxTemperatureKey, 800);
        semantics.put(VisualContexts.smeltableKey, true);
        semantics.put(VisualContexts.alloyKey, true);
        semantics.put(VisualContexts.controllerPosKey, point(origin));
        return new Fixture(
            "structurecumulus:smeltery",
            "仿匠魂冶炼炉",
            "匠魂冶炼",
            "5x5 外壳 / 3x3x3 内腔",
            members,
            links(origin, members),
            128,
            semantics
        );
    }

    static Fixture ebf(LatticePoint origin) {
        List<Member> members = new ArrayList<>();
        members.add(member("高炉控制器", VisualPartRole.controller, origin, box(origin, 1, 1, 1)));
        members.add(member("输入接口", VisualPartRole.hatch, at(origin, -1, 0, 0), box(at(origin, -1, 0, 0), 1, 1, 1)));
        members.add(member("输出接口", VisualPartRole.hatch, at(origin, 1, 0, 0), box(at(origin, 1, 0, 0), 1, 1, 1)));
        members.add(member("能量接口", VisualPartRole.hatch, at(origin, -1, 0, 1), box(at(origin, -1, 0, 1), 1, 1, 1)));
        members.add(member("维护舱", VisualPartRole.maintenance, at(origin, 1, 0, 1), box(at(origin, 1, 0, 1), 1, 1, 1)));
        members.add(
            member(
                "底部外壳",
                VisualPartRole.casing,
                at(origin, -1, 0, 2),
                cells(at(origin, -1, 0, 2), at(origin, 0, 0, 2), at(origin, 1, 0, 2))
            )
        );
        members.add(member("下层线圈", VisualPartRole.coil, at(origin, -1, 1, 0), coilRing(at(origin, 0, 1, 1))));
        members.add(member("上层线圈", VisualPartRole.coil, at(origin, -1, 2, 0), coilRing(at(origin, 0, 2, 1))));
        members.add(
            member("观察窗", VisualPartRole.window, at(origin, 0, 1, 0), cells(at(origin, 0, 1, 0), at(origin, 0, 2, 0)))
        );
        members.add(member("顶部外壳", VisualPartRole.casing, at(origin, -1, 3, 0), ring(at(origin, 0, 3, 1))));
        members.add(member("消音器", VisualPartRole.muffler, at(origin, 0, 4, 1), box(at(origin, 0, 4, 1), 1, 1, 1)));
        Map<ContextKey<?>, Object> semantics = new LinkedHashMap<>();
        semantics.put(VisualContexts.dimensionsKey, "3x3x4");
        semantics.put(VisualContexts.interiorVolumeKey, 4);
        semantics.put(VisualContexts.coilTierKey, 3);
        semantics.put(VisualContexts.maxTemperatureKey, 2700);
        semantics.put(VisualContexts.energyTierKey, "MV");
        semantics.put(VisualContexts.parallelsKey, 8);
        semantics.put(VisualContexts.mufflerCountKey, 1);
        semantics.put(VisualContexts.maintenanceRequiredKey, true);
        semantics.put(VisualContexts.controllerPosKey, point(origin));
        return new Fixture(
            "structurecumulus:ebf",
            "仿 GT 高炉",
            "GT 高炉",
            "3x3x4 外壳 / 1x1x4 内腔",
            members,
            links(origin, members),
            256,
            semantics
        );
    }

    private static CuboidSet coilRing(LatticePoint center) {
        int x = center.x();
        int y = center.y();
        int z = center.z();
        List<Cuboid> boxes = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0 && dz == 0) || (dx == 0 && dz == -1)) {
                    continue;
                }
                boxes.add(Cuboid.of(LatticePoint.of(x + dx, y, z + dz)));
            }
        }
        return CuboidSet.of(boxes);
    }

    private static CuboidSet ring(LatticePoint center) {
        int x = center.x();
        int y = center.y();
        int z = center.z();
        List<Cuboid> boxes = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                boxes.add(Cuboid.of(LatticePoint.of(x + dx, y, z + dz)));
            }
        }
        return CuboidSet.of(boxes);
    }

    private static CuboidSet cells(LatticePoint... cells) {
        List<Cuboid> boxes = new ArrayList<>();
        for (LatticePoint cell : cells) {
            boxes.add(Cuboid.of(cell));
        }
        return CuboidSet.of(boxes);
    }

    static boolean stage(ServerLevel level, Fixture fixture) {
        ServerState state = VisualFixtureSupport.state();
        if (state == null) {
            return false;
        }
        VisualFixtureSupport.clearAll(level);
        Staged staged = stageOnly(state, level, fixture);
        VisualPartState.set(staged.parts());
        VisualGroupState.set(staged.groups(), staged.nodes());
        VisualClientState.setSetSummary(
            new VisualSetSummary(
                fixture.id(),
                fixture.members().size(),
                fixture.members().size(),
                fixture.claimRows(),
                fixture.claimRows(),
                fixture.budgetLimit(),
                fixture.links()
            )
        );
        return true;
    }

    static Staged stageOnly(ServerState state, ServerLevel level, Fixture fixture) {
        List<VisualPart> parts = new ArrayList<>();
        List<VisualHierarchyNode> nodes = new ArrayList<>();
        StructureInstance controller = null;
        for (Member member : fixture.members()) {
            Map<ContextKey<?>, Object> context = new LinkedHashMap<>(
                VisualContexts.forMember(member.role(), groupId, fixture.displayName(), 0, fixture.budgetLimit(), "已捕获")
            );
            context.putAll(fixture.semantics());
            StructureInstance instance = VisualFixtureSupport
                    .form(state, level, owner, member.siting(), member.bounds(), context, new Spend(1, 0));
            VisualContexts.record(instance, context);
            VisualFixtureSupport.claim(state, level, owner, member.siting(), member.bounds(), Sharing.shared);
            if (controller == null) {
                controller = instance;
            }
            parts.add(
                new VisualPart(
                    instance.id(),
                    member.label(),
                    member.role(),
                    member.role().color(),
                    member.role().description(),
                    member.bounds()
                )
            );
            nodes.add(
                new VisualHierarchyNode(
                    instance.id(),
                    controller == null ? null : controller.id(),
                    0,
                    member.label(),
                    groupId,
                    member.siting().origin()
                )
            );
        }
        List<VisualGroup> groups = List
                .of(new VisualGroup(groupId, groupName, 0xFFFF8040, fixture.members().size(), fixture.claimRows()));
        return new Staged(parts, groups, nodes);
    }

    static Block blockFor(VisualPartRole role) {
        return switch (role) {
            case controller -> Blocks.BLAST_FURNACE;
            case wall -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case casing -> Blocks.IRON_BLOCK;
            case window -> Blocks.GLASS;
            case drain -> Blocks.HOPPER;
            case tank -> Blocks.COPPER_BLOCK;
            case coil -> Blocks.MAGMA_BLOCK;
            case hatch -> Blocks.COPPER_BLOCK;
            case muffler -> Blocks.HOPPER;
            case maintenance -> Blocks.IRON_TRAPDOOR;
            default -> Blocks.STONE;
        };
    }

    private static Member member(String label, VisualPartRole role, LatticePoint origin, CuboidSet bounds) {
        return new Member(label, role, Siting.of(origin, Orientation.identity), bounds);
    }

    private static List<VisualSetLink> links(LatticePoint root, List<Member> members) {
        List<VisualSetLink> links = new ArrayList<>();
        for (int index = 1; index < members.size(); index++) {
            links.add(new VisualSetLink(root, members.get(index).siting().origin()));
        }
        return links;
    }

    private static CuboidSet box(LatticePoint origin, int dx, int dy, int dz) {
        return CuboidSet.of(Cuboid.of(origin, at(origin, dx, dy, dz)));
    }

    private static LatticePoint at(LatticePoint origin, int dx, int dy, int dz) {
        return Objects.requireNonNull(origin.offset(dx, dy, dz), "industrial cell");
    }

    private static String point(LatticePoint origin) {
        return "(" + origin.x() + "," + origin.y() + "," + origin.z() + ")";
    }
}
