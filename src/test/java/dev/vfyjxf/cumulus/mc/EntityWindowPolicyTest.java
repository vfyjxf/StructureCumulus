package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityWindowPolicyTest {

    private static final NamespacedId cow = NamespacedId.of("test", "cow");

    @Test
    @DisplayName("the policy maps the blueprint-local entity window through the siting")
    void policyMapsTheLocalEntityWindowThroughTheSiting() {
        Blueprint blueprint = entityBlueprint();
        Cuboid local = new Cuboid(LatticePoint.of(-3, -3, -3), LatticePoint.of(4, 4, 4));
        assertEquals(local, blueprint.reads().windows().get(0).cells(), "the read scope stays blueprint-local");

        SnapshotPolicy identity = CommandSupport
                .policy(blueprint, Siting.of(LatticePoint.origin, Orientation.identity));
        assertEquals(1, identity.windows().size(), "one entity window");
        assertEquals(cow, identity.windows().get(0).type(), "the window names the entity type");
        assertEquals(local, identity.windows().get(0).cells(), "an identity siting maps the window onto itself");
        assertTrue(identity.kinds().contains(OccupantKind.entity), "the entity kind is copied");

        Siting siting = Siting.of(LatticePoint.of(10, 64, 10), Orientation.identity);
        SnapshotPolicy sited = CommandSupport.policy(blueprint, siting);
        assertEquals(1, sited.windows().size(), "one entity window");
        assertEquals(
            new Cuboid(LatticePoint.of(7, 61, 7), LatticePoint.of(14, 68, 14)),
            sited.windows().get(0).cells(),
            "a nonzero siting moves the window into the world frame"
        );
        assertEquals(local, blueprint.reads().windows().get(0).cells(), "the read scope stays blueprint-local");

        SnapshotPolicy unmapped = CommandSupport
                .policy(blueprint, Siting.of(LatticePoint.of(LatticePoint.maxXz, 0, 0), Orientation.identity));
        assertTrue(unmapped.windows().isEmpty(), "a window that leaves the lattice is dropped");
        assertTrue(unmapped.kinds().contains(OccupantKind.entity), "the rest of the policy survives");
    }

    @Test
    @DisplayName("a set policy maps the root and satellite windows through their own sitings")
    void aSetPolicyMapsRootAndSatelliteWindowsThroughTheirOwnSitings() {
        NamespacedId satelliteBlueprintId = NamespacedId.of("test", "entity_wing_blueprint");
        SatelliteSpec spec = new SatelliteSpec(
            NamespacedId.of("test", "entity_wing"),
            satelliteBlueprintId,
            LatticePoint.of(4, 0, 1),
            Orientation.of(Turn.half, Tilt.level, Flip.none),
            true,
            Value.object(),
            null,
            List.of(),
            null
        );
        CompiledStructureSet compiled = new CompiledStructureSet(
            NamespacedId.of("test", "entity_set"),
            entityBlueprint(NamespacedId.of("test", "entity_root"), Cuboid.of(0, 0, 0, 1, 1, 1), LatticePoint.origin),
            List.of(
                new CompiledSatellite(
                    spec,
                    entityBlueprint(satelliteBlueprintId, Cuboid.of(0, 0, 0, 2, 1, 2), LatticePoint.of(1, 0, 0))
                )
            )
        );
        StructureSet plan = StructureSetTestSupport.place(compiled);

        SnapshotPolicy policy = SetCommand.policy(plan);
        assertEquals(2, policy.windows().size(), "one window per member");
        assertEquals(
            List.of(Cuboid.of(97, 7, 97, 104, 14, 104), Cuboid.of(100, 7, 98, 107, 14, 105)),
            policy.windows().stream().map(EntityWindow::cells).toList(),
            "the root window maps by the root siting and the wing window by its own composed siting"
        );
        assertEquals(List.of(cow, cow), policy.windows().stream().map(EntityWindow::type).toList());
        assertEquals(Set.of(OccupantKind.entity), policy.kinds(), "the union supplies the kinds");
    }

    private static Blueprint entityBlueprint() {
        return entityBlueprint(
            NamespacedId.of("test", "entity_window"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            LatticePoint.origin
        );
    }

    private static Blueprint entityBlueprint(NamespacedId id, Cuboid bounds, LatticePoint cell) {
        Bay bay = Bay.of(
            NamespacedId.of(id.namespace(), id.path() + "_bay"),
            CuboidSet.of(Cuboid.of(cell)),
            Coverage.anySubject,
            Footprint.bound(),
            Sharing.shared
        );
        Check<OccupantSnapshot> check = Check.of(
            NamespacedId.of(id.namespace(), id.path() + "_cow"),
            bay,
            Focuses.entity(cow),
            Expectations.anyOccupant()
        );
        return Blueprint.builder(id).bounds(bounds).check(check).build();
    }
}
