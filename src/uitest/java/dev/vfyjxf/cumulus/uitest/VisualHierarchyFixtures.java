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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dev-only grouped multiblock fixtures: systems, satellites and substructures with a hierarchy. */
final class VisualHierarchyFixtures {

    private static final OwnerId alphaOwner = OwnerId.of(NamespacedId.of("structurecumulus", "visual_group_alpha"));
    private static final OwnerId betaOwner = OwnerId.of(NamespacedId.of("structurecumulus", "visual_group_beta"));
    private static final OwnerId gammaOwner = OwnerId.of(NamespacedId.of("structurecumulus", "visual_group_gamma"));
    private static final OwnerId orbitalOwner = OwnerId.of(NamespacedId.of("structurecumulus", "visual_orbital"));
    private static final OwnerId hullOwner = OwnerId.of(NamespacedId.of("structurecumulus", "visual_hull"));

    private VisualHierarchyFixtures() {}

    record GroupDef(String id, String displayName, int color) {}

    record Member(
        String label,
        String groupId,
        OwnerId owner,
        Siting siting,
        CuboidSet bounds,
        @Nullable String parent,
        int depth
    ) {}

    record Fixture(String id, List<GroupDef> groupDefs, List<Member> members) {

        Fixture {
            Objects.requireNonNull(id, "id");
            groupDefs = List.copyOf(groupDefs);
            members = List.copyOf(members);
        }

        int claimRows() {
            long rows = 0;
            for (Member member : members) {
                rows += member.bounds().volume();
            }
            return Math.toIntExact(rows);
        }

        int boundsBoxes() {
            int boxes = 0;
            for (Member member : members) {
                boxes += member.bounds().boxCount();
            }
            return boxes;
        }

        int linkCount() {
            int links = 0;
            for (Member member : members) {
                if (member.parent() != null) {
                    links++;
                }
            }
            return links;
        }

        int maxDepth() {
            int depth = 0;
            for (Member member : members) {
                depth = Math.max(depth, member.depth());
            }
            return depth;
        }
    }

    static Fixture groups(LatticePoint origin) {
        List<GroupDef> groups = List.of(
            new GroupDef("structurecumulus:alpha", "阿尔法", 0xFFE0A020),
            new GroupDef("structurecumulus:beta", "贝塔", 0xFF40C0E0),
            new GroupDef("structurecumulus:gamma", "伽马", 0xFFE060C0)
        );
        List<Member> members = new ArrayList<>();
        for (int system = 0; system < 3; system++) {
            GroupDef group = groups.get(system);
            OwnerId owner = switch (system) {
                case 0 -> alphaOwner;
                case 1 -> betaOwner;
                default -> gammaOwner;
            };
            LatticePoint systemOrigin = at(origin, system * 10, 0, 0);
            String rootLabel = "system-" + system + "-root";
            for (int index = 0; index < 8; index++) {
                LatticePoint cell = at(systemOrigin, (index % 4) * 2, 0, (index / 4) * 2);
                String label = index == 0 ? rootLabel : "system-" + system + "-member-" + index;
                members.add(
                    new Member(
                        label,
                        group.id(),
                        owner,
                        siting(cell),
                        CuboidSet.of(Cuboid.of(cell, at(cell, 1, 2, 1))),
                        index == 0 ? null : rootLabel,
                        index == 0 ? 0 : 1
                    )
                );
            }
        }
        return new Fixture("groups", groups, members);
    }

    static Fixture satellites(LatticePoint origin) {
        GroupDef group = new GroupDef("structurecumulus:orbital", "轨道", 0xFF60E0A0);
        List<Member> members = new ArrayList<>();
        members.add(
            new Member(
                "orbital-root",
                group.id(),
                orbitalOwner,
                siting(origin),
                CuboidSet.of(
                    Cuboid.of(origin, at(origin, 3, 3, 3)),
                    Cuboid.of(at(origin, 3, 1, 1), at(origin, 5, 2, 2))
                ),
                null,
                0
            )
        );
        int[][] satelliteOffsets = {{4, 0, 0}, {4, 0, 4}, {0, 0, 4}, {-4, 0, 0}};
        for (int index = 0; index < satelliteOffsets.length; index++) {
            LatticePoint satellite = at(origin, satelliteOffsets[index][0], 0, satelliteOffsets[index][2]);
            members.add(
                new Member(
                    "satellite-" + index,
                    group.id(),
                    orbitalOwner,
                    siting(satellite),
                    CuboidSet.of(
                        Cuboid.of(satellite, at(satellite, 2, 2, 2)),
                        Cuboid.of(at(satellite, 2, 1, 0), at(satellite, 3, 2, 1))
                    ),
                    "orbital-root",
                    1
                )
            );
        }
        LatticePoint subA = at(origin, 6, 0, 0);
        LatticePoint subB = at(origin, 4, 0, 6);
        members.add(
            new Member(
                "sub-satellite-0",
                group.id(),
                orbitalOwner,
                siting(subA),
                CuboidSet.of(Cuboid.of(subA, at(subA, 1, 2, 1))),
                "satellite-0",
                2
            )
        );
        members.add(
            new Member(
                "sub-satellite-1",
                group.id(),
                orbitalOwner,
                siting(subB),
                CuboidSet.of(Cuboid.of(subB, at(subB, 1, 2, 1))),
                "satellite-1",
                2
            )
        );
        return new Fixture("satellites", List.of(group), members);
    }

    /** A scale fixture: eight systems of equal size, each with a depth-three hierarchy chain. */
    static Fixture scale(LatticePoint origin, int count) {
        if (count != 128 && count != 256) {
            throw new IllegalArgumentException("scale count must be 128 or 256, got " + count);
        }
        List<GroupDef> groups = List.of(
            new GroupDef("structurecumulus:alpha_foundry", "阿尔法铸造厂", 0xFFE0A020),
            new GroupDef("structurecumulus:beta_nexus", "贝塔枢纽", 0xFF40C0E0),
            new GroupDef("structurecumulus:gamma_bastion", "伽马堡垒", 0xFFE060C0),
            new GroupDef("structurecumulus:delta_spire", "德尔塔尖塔", 0xFF60E0A0),
            new GroupDef("structurecumulus:epsilon_platform", "伊普西龙平台", 0xFFFF9040),
            new GroupDef("structurecumulus:zeta_network", "泽塔网络", 0xFF8080FF),
            new GroupDef("structurecumulus:eta_ring", "伊塔环", 0xFFFF6080),
            new GroupDef("structurecumulus:theta_vault", "西塔宝库", 0xFFA0A0A0)
        );
        OwnerId[] owners = {OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_alpha")),
                OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_beta")),
                OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_gamma")),
                OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_delta")),
                OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_epsilon")),
                OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_zeta")),
                OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_eta")),
                OwnerId.of(NamespacedId.of("structurecumulus", "visual_scale_theta"))};
        int perGroup = count / groups.size();
        List<Member> members = new ArrayList<>(count);
        for (int group = 0; group < groups.size(); group++) {
            LatticePoint groupOrigin = at(origin, group * 40, 0, 0);
            String root = "group-" + group + "-root";
            String satellite = "group-" + group + "-satellite";
            String subSatellite = "group-" + group + "-sub-satellite";
            for (int index = 0; index < perGroup; index++) {
                LatticePoint cell = at(groupOrigin, (index % 4) * 2, 0, (index / 4) * 3);
                String label = index == 0
                        ? root
                        : index == 1 ? satellite : index == 2 ? subSatellite : "group-" + group + "-member-" + index;
                String parent = index == 0
                        ? null
                        : index == 1 ? root : index == 2 ? satellite : index == 3 ? subSatellite : root;
                int depth = index == 0 ? 0 : Math.min(index, 3);
                members.add(
                    new Member(
                        label,
                        groups.get(group).id(),
                        owners[group],
                        siting(cell),
                        CuboidSet.of(Cuboid.of(cell, at(cell, 1, 1, 2))),
                        parent,
                        depth
                    )
                );
            }
        }
        return new Fixture("scale-" + count, groups, members);
    }

    static Fixture substructures(LatticePoint origin) {
        GroupDef group = new GroupDef("structurecumulus:hull", "船体", 0xFFFFA040);
        LatticePoint child0 = at(origin, 5, 0, 0);
        LatticePoint child1 = at(origin, 0, 0, 5);
        LatticePoint child2 = at(origin, 5, 0, 5);
        LatticePoint nested0 = at(origin, 5, 0, 2);
        LatticePoint nested1 = at(origin, 5, 2, 0);
        List<Member> members = List.of(
            new Member(
                "substructure-root",
                group.id(),
                hullOwner,
                siting(origin),
                CuboidSet.of(
                    Cuboid.of(origin, at(origin, 5, 3, 5)),
                    Cuboid.of(at(origin, 5, 0, 0), at(origin, 8, 4, 3))
                ),
                null,
                0
            ),
            new Member(
                "child-frame-0",
                group.id(),
                hullOwner,
                siting(child0),
                CuboidSet.of(
                    Cuboid.of(child0, at(child0, 3, 4, 3)),
                    Cuboid.of(at(child0, 0, 0, 0), at(child0, 1, 2, 1))
                ),
                "substructure-root",
                1
            ),
            new Member(
                "child-frame-1",
                group.id(),
                hullOwner,
                siting(child1),
                CuboidSet.of(
                    Cuboid.of(child1, at(child1, 3, 4, 3)),
                    Cuboid.of(at(child1, 0, 0, 3), at(child1, 2, 2, 4))
                ),
                "substructure-root",
                1
            ),
            new Member(
                "child-frame-2",
                group.id(),
                hullOwner,
                siting(child2),
                CuboidSet.of(Cuboid.of(child2, at(child2, 3, 4, 3))),
                "substructure-root",
                1
            ),
            new Member(
                "nested-frame-0",
                group.id(),
                hullOwner,
                siting(nested0),
                CuboidSet.of(
                    Cuboid.of(nested0, at(nested0, 2, 2, 2)),
                    Cuboid.of(at(nested0, 2, 0, 0), at(nested0, 4, 1, 2))
                ),
                "child-frame-0",
                2
            ),
            new Member(
                "nested-frame-1",
                group.id(),
                hullOwner,
                siting(nested1),
                CuboidSet.of(Cuboid.of(nested1, at(nested1, 2, 1, 2))),
                "child-frame-1",
                2
            ),
            new Member(
                "deep-frame-0",
                group.id(),
                hullOwner,
                siting(at(origin, 7, 0, 2)),
                CuboidSet.of(Cuboid.of(at(origin, 7, 0, 2), at(origin, 8, 2, 3))),
                "nested-frame-0",
                3
            ),
            new Member(
                "deep-frame-1",
                group.id(),
                hullOwner,
                siting(at(origin, 5, 0, 3)),
                CuboidSet.of(Cuboid.of(at(origin, 5, 0, 3), at(origin, 6, 1, 4))),
                "nested-frame-1",
                3
            )
        );
        return new Fixture("substructures", List.of(group), members);
    }

    record Staged(List<VisualGroup> groups, List<VisualHierarchyNode> nodes, List<VisualPart> parts) {
        Staged {
            groups = List.copyOf(groups);
            nodes = List.copyOf(nodes);
            parts = List.copyOf(parts);
        }
    }

    static boolean stage(ServerLevel level, Fixture fixture) {
        ServerState state = VisualFixtureSupport.state();
        if (state == null) {
            return false;
        }
        VisualFixtureSupport.clearAll(level);
        Staged staged = stageOnly(state, level, fixture);
        VisualGroupState.set(staged.groups(), staged.nodes());
        VisualPartState.set(staged.parts());
        VisualClientState.setSetSummary(null);
        return true;
    }

    /** Forms and claims one hierarchy fixture without clearing or publishing client model state. */
    static Staged stageOnly(ServerState state, ServerLevel level, Fixture fixture) {
        Map<String, StructureInstance> formed = new LinkedHashMap<>();
        for (Member member : fixture.members()) {
            VisualPartRole role = VisualPartRole.forMember(member.label(), member.depth());
            String system = member.groupId();
            for (GroupDef definition : fixture.groupDefs()) {
                if (definition.id().equals(member.groupId())) {
                    system = definition.displayName();
                    break;
                }
            }
            Map<ContextKey<?>, Object> context = VisualContexts
                    .forMember(role, member.groupId(), system, member.depth(), 64, "已捕获");
            StructureInstance instance = VisualFixtureSupport
                    .form(state, level, member.owner(), member.siting(), member.bounds(), context, new Spend(1, 0));
            VisualContexts.record(instance, context);
            formed.put(member.label(), instance);
            VisualFixtureSupport.claim(state, level, member.owner(), member.siting(), member.bounds(), Sharing.shared);
        }
        List<VisualGroup> groups = new ArrayList<>();
        for (GroupDef definition : fixture.groupDefs()) {
            int instances = 0;
            int claims = 0;
            for (Member member : fixture.members()) {
                if (member.groupId().equals(definition.id())) {
                    instances++;
                    claims += Math.toIntExact(member.bounds().volume());
                }
            }
            groups.add(
                new VisualGroup(definition.id(), definition.displayName(), definition.color(), instances, claims)
            );
        }
        List<VisualHierarchyNode> nodes = new ArrayList<>();
        List<VisualPart> parts = new ArrayList<>();
        for (Member member : fixture.members()) {
            StructureInstance instance = Objects.requireNonNull(formed.get(member.label()), "formed member");
            StructureInstance parent = member.parent() == null ? null : formed.get(member.parent());
            nodes.add(
                new VisualHierarchyNode(
                    instance.id(),
                    parent == null ? null : parent.id(),
                    member.depth(),
                    member.label(),
                    member.groupId(),
                    member.siting().origin()
                )
            );
            VisualPartRole role = VisualPartRole.forMember(member.label(), member.depth());
            parts.add(
                new VisualPart(instance.id(), member.label(), role, role.color(), role.description(), member.bounds())
            );
        }
        return new Staged(groups, nodes, parts);
    }

    private static Siting siting(LatticePoint origin) {
        return Siting.of(origin, Orientation.identity);
    }

    private static LatticePoint at(LatticePoint origin, int dx, int dy, int dz) {
        return Objects.requireNonNull(origin.offset(dx, dy, dz), "fixture cell");
    }
}
