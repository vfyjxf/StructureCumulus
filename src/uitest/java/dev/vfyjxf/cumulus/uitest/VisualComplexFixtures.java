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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dev-only complex multiblock fixtures with real multi-cuboid instance bounds and claim rows. */
final class VisualComplexFixtures {

    static final OwnerId owner = OwnerId.of(NamespacedId.of("structurecumulus", "visual_complex_owner"));

    private VisualComplexFixtures() {}

    record Member(String name, Siting siting, CuboidSet bounds) {}

    record Fixture(String id, List<Member> members, List<VisualSetLink> links, int budgetLimit) {

        Fixture {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(members, "members");
            Objects.requireNonNull(links, "links");
            if (members.isEmpty()) {
                throw new IllegalArgumentException("a complex fixture needs at least one member");
            }
            members = List.copyOf(members);
            links = List.copyOf(links);
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

        LatticePoint rootOrigin() {
            return members.get(0).siting().origin();
        }

        LatticePoint center() {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Member member : members) {
                for (Cuboid box : member.bounds().boxes()) {
                    minX = Math.min(minX, box.min().x());
                    minY = Math.min(minY, box.min().y());
                    minZ = Math.min(minZ, box.min().z());
                    maxX = Math.max(maxX, box.max().x());
                    maxY = Math.max(maxY, box.max().y());
                    maxZ = Math.max(maxZ, box.max().z());
                }
            }
            return LatticePoint.of((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        }
    }

    static Fixture blastFurnace(LatticePoint origin) {
        LatticePoint left = at(origin, -4, 0, 0);
        LatticePoint right = at(origin, 4, 0, 0);
        List<Member> members = new ArrayList<>();
        members.add(
            new Member(
                "controller",
                siting(origin),
                CuboidSet
                        .of(Cuboid.of(origin, at(origin, 3, 3, 3)), Cuboid.of(at(origin, 3, 1, 1), at(origin, 4, 3, 2)))
            )
        );
        members.add(
            new Member(
                "left_preheater",
                siting(left),
                CuboidSet.of(Cuboid.of(left, at(left, 3, 4, 3)), Cuboid.of(at(left, 1, 4, 1), at(left, 2, 5, 2)))
            )
        );
        members.add(
            new Member(
                "right_preheater",
                siting(right),
                CuboidSet.of(Cuboid.of(right, at(right, 3, 4, 3)), Cuboid.of(at(right, 1, 4, 1), at(right, 2, 5, 2)))
            )
        );
        for (int index = 0; index < 4; index++) {
            LatticePoint port = switch (index) {
                case 0 -> at(origin, 0, 0, 3);
                case 1 -> at(origin, 3, 0, 0);
                case 2 -> at(origin, 1, 0, -2);
                default -> at(origin, 2, 0, 2);
            };
            members.add(new Member("port_" + index, siting(port), CuboidSet.of(Cuboid.of(port, at(port, 1, 2, 1)))));
        }
        return new Fixture("structurecumulus:visual_complex_blast_furnace", members, linksFrom(origin, members), 256);
    }

    static Fixture portEnvelope(LatticePoint origin) {
        List<Member> members = new ArrayList<>();
        members.add(
            new Member(
                "controller",
                siting(origin),
                CuboidSet.of(
                    Cuboid.of(origin, at(origin, 3, 3, 3)),
                    Cuboid.of(at(origin, -1, 1, -1), at(origin, 0, 3, 1))
                )
            )
        );
        int[][] ports = {{3, 0, 0}, {-1, 0, 0}, {0, 0, 3}, {0, 0, -1}, {2, 0, 3}, {3, 0, -1}};
        for (int index = 0; index < ports.length; index++) {
            LatticePoint port = at(origin, ports[index][0], ports[index][1], ports[index][2]);
            members.add(
                new Member(
                    "envelope_port_" + index,
                    siting(port),
                    CuboidSet.of(Cuboid.of(port, at(port, 1, 2, 1)), Cuboid.of(at(port, 1, 0, 0), at(port, 2, 1, 1)))
                )
            );
        }
        return new Fixture("structurecumulus:visual_complex_port", members, linksFrom(origin, members), 256);
    }

    static Fixture logicalSubnetwork(LatticePoint origin) {
        List<Member> members = new ArrayList<>();
        members.add(
            new Member(
                "controller",
                siting(origin),
                CuboidSet
                        .of(Cuboid.of(origin, at(origin, 3, 3, 3)), Cuboid.of(at(origin, 3, 1, 1), at(origin, 4, 2, 2)))
            )
        );
        int[][] satellites = {{10, 0, 10}, {10, 0, -10}, {-10, 0, 10}, {-10, 0, -10}};
        for (int index = 0; index < satellites.length; index++) {
            LatticePoint satellite = at(origin, satellites[index][0], satellites[index][1], satellites[index][2]);
            members.add(
                new Member(
                    "logical_" + index,
                    siting(satellite),
                    CuboidSet.of(
                        Cuboid.of(satellite, at(satellite, 2, 2, 2)),
                        Cuboid.of(at(satellite, 2, 1, 0), at(satellite, 3, 3, 1))
                    )
                )
            );
        }
        return new Fixture("structurecumulus:visual_complex_logical", members, linksFrom(origin, members), 256);
    }

    static Fixture nestedFrame(LatticePoint origin) {
        LatticePoint child = at(origin, 4, 0, 0);
        LatticePoint entity = at(origin, 4, 0, 4);
        List<Member> members = List.of(
            new Member(
                "root",
                siting(origin),
                CuboidSet
                        .of(Cuboid.of(origin, at(origin, 3, 3, 3)), Cuboid.of(at(origin, 4, 0, 0), at(origin, 7, 4, 3)))
            ),
            new Member(
                "child_frame",
                siting(child),
                CuboidSet.of(Cuboid.of(child, at(child, 3, 4, 3)), Cuboid.of(at(child, 0, 0, 4), at(child, 2, 2, 6)))
            ),
            new Member(
                "entity_window",
                siting(entity),
                CuboidSet
                        .of(Cuboid.of(entity, at(entity, 2, 2, 2)), Cuboid.of(at(entity, 3, 0, 0), at(entity, 5, 1, 2)))
            )
        );
        List<VisualSetLink> links = List.of(
            new VisualSetLink(origin, child),
            new VisualSetLink(origin, entity),
            new VisualSetLink(child, entity)
        );
        return new Fixture("structurecumulus:visual_complex_nested", members, links, 256);
    }

    static boolean stage(ServerLevel level, Fixture fixture) {
        ServerState state = VisualFixtureSupport.state();
        if (state == null) {
            return false;
        }
        VisualFixtureSupport.clearAll(level);
        List<VisualPart> parts = stageOnly(state, level, fixture);
        VisualPartState.set(parts);
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

    /** Forms and claims one fixture without clearing or publishing the client model. */
    static List<VisualPart> stageOnly(ServerState state, ServerLevel level, Fixture fixture) {
        List<VisualPart> parts = new ArrayList<>();
        for (Member member : fixture.members()) {
            VisualPartRole role = VisualPartRole.forMember(member.name(), 0);
            Map<ContextKey<?>, Object> context = VisualContexts
                    .forMember(role, fixture.id(), fixture.id(), 0, fixture.budgetLimit(), "已捕获");
            StructureInstance instance = VisualFixtureSupport
                    .form(state, level, owner, member.siting(), member.bounds(), context, new Spend(1, 0));
            VisualContexts.record(instance, context);
            parts.add(
                new VisualPart(instance.id(), member.name(), role, role.color(), role.description(), member.bounds())
            );
            VisualFixtureSupport.claim(state, level, owner, member.siting(), member.bounds(), Sharing.shared);
        }
        return List.copyOf(parts);
    }

    private static List<VisualSetLink> linksFrom(LatticePoint root, List<Member> members) {
        List<VisualSetLink> links = new ArrayList<>(members.size() - 1);
        for (int index = 1; index < members.size(); index++) {
            links.add(new VisualSetLink(root, members.get(index).siting().origin()));
        }
        return links;
    }

    private static Siting siting(LatticePoint origin) {
        return Siting.of(origin, Orientation.identity);
    }

    private static LatticePoint at(LatticePoint origin, int dx, int dy, int dz) {
        return Objects.requireNonNull(origin.offset(dx, dy, dz), "fixture cell");
    }
}
