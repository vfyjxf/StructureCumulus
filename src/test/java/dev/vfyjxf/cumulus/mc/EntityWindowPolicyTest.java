package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
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

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityWindowPolicyTest {

    private static final NamespacedId cow = NamespacedId.of("test", "cow");

    @Test
    @DisplayName("known gap D4: CommandSupport.policy copies the blueprint-local entity window")
    void policyCopiesTheLocalEntityWindow() {
        Blueprint blueprint = entityBlueprint();
        SnapshotPolicy policy = CommandSupport.policy(blueprint);
        assertEquals(blueprint.reads().windows(), policy.windows(), "windows are copied unchanged");
        assertEquals(1, policy.windows().size(), "one entity window");
        EntityWindow window = policy.windows().get(0);
        assertEquals(cow, window.type(), "the window names the entity type");
        assertEquals(
            new Cuboid(LatticePoint.of(-3, -3, -3), LatticePoint.of(4, 4, 4)),
            window.cells(),
            "the current window is blueprint-local and inflated by three"
        );
        assertTrue(policy.kinds().contains(OccupantKind.entity), "the entity kind is copied");
    }

    @Test
    @DisplayName("known gap D4: a nonzero siting never reaches the policy window, so no world mapping happens")
    void aNonzeroSitingStillLeavesThePolicyWindowLocal() {
        Blueprint blueprint = entityBlueprint();
        SnapshotPolicy policy = CommandSupport.policy(blueprint);
        EntityWindow local = policy.windows().get(0);
        Siting siting = Siting.of(LatticePoint.of(10, 64, 10), Orientation.identity);
        Cuboid worldWindow = Objects.requireNonNull(siting.toWorld(local.cells()), "the window maps");

        assertEquals(LatticePoint.of(7, 61, 7), worldWindow.min(), "the desired world window starts at 7,61,7");
        assertEquals(LatticePoint.of(14, 68, 14), worldWindow.max(), "the desired world window ends at 14,68,14");
        assertEquals(LatticePoint.of(-3, -3, -3), local.cells().min(), "the policy keeps the local min");
        assertEquals(LatticePoint.of(4, 4, 4), local.cells().max(), "the policy keeps the local max");
        assertNotEquals(worldWindow, local.cells(), "the desired world image is not what the policy carries");
    }

    private static Blueprint entityBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(
            NamespacedId.of("test", "entity_bay"),
            CuboidSet.of(bounds),
            Coverage.anySubject,
            Footprint.bound(),
            Sharing.shared
        );
        Check<OccupantSnapshot> check = Check
                .of(NamespacedId.of("test", "entity_cow"), bay, Focuses.entity(cow), Expectations.anyOccupant());
        return Blueprint.builder(NamespacedId.of("test", "entity_window")).bounds(bounds).check(check).build();
    }
}
