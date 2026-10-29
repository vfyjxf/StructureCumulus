package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** An entity window declared in the blueprint's local frame; the host maps it into the world frame (D4). */
class EntityWindowScenarioTest {

    private static final LatticePoint worldOrigin = LatticePoint.of(10, 64, 10);

    @Test
    @DisplayName("the kernel keeps the entity read-scope window blueprint-local; the host maps it")
    void sitedEntityWindowIsPublishedInTheLocalFrame() {
        Siting siting = TechFixtures.identity(worldOrigin);
        Blueprint blueprint = entityBlueprint("entity/local_window");
        LatticePoint worldCell = TechFixtures.world(siting, LatticePoint.origin);
        TestWorld.View view = new TestWorld.View()
                .put(TechFixtures.entity(TechFixtures.cow, worldCell, Cuboid.of(worldCell)));
        MatchResult.Admitted admitted = assertInstanceOf(
            MatchResult.Admitted.class,
            TechFixtures.check(blueprint, siting, view),
            "the entity check is admitted"
        );
        assertEquals(1, blueprint.reads().windows().size(), "the entity focus declares one window");
        EntityWindow window = blueprint.reads().windows().get(0);
        assertEquals(TechFixtures.cow, window.type(), "the window carries the entity type");
        assertEquals(LatticePoint.of(-3, -3, -3), window.cells().min(), "the current window is blueprint-local");
        assertEquals(LatticePoint.of(4, 4, 4), window.cells().max(), "the local window is inflated by three");
        assertEquals(
            admitted.outcome().reads().windows(),
            blueprint.reads().windows(),
            "the outcome relays the same local window"
        );
        Cuboid worldImage = worldWindow(siting, window.cells());
        assertEquals(LatticePoint.of(7, 61, 7), worldImage.min(), "the siting would place the window at 7,61,7");
        assertNotEquals(worldImage, window.cells(), "the published window is not the world-frame image");
    }

    @Test
    void theEntityWindowDoesNotChangeTheClaim() {
        Siting siting = TechFixtures.identity(worldOrigin);
        Blueprint blueprint = entityBlueprint("entity/claim");
        LatticePoint worldCell = TechFixtures.world(siting, LatticePoint.origin);
        TestWorld.View view = new TestWorld.View()
                .put(TechFixtures.entity(TechFixtures.cow, worldCell, Cuboid.of(worldCell)));
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(blueprint, siting, view, ledger),
            "the entity pen forms"
        );
        assertEquals(1, formed.claims().rows().size(), "the one-cell entity owns one claim row");
        assertEquals(worldCell.key(), formed.claims().rows().get(0).cell(), "the claim row is in the world frame");
    }

    private static Blueprint entityBlueprint(String path) {
        CuboidSet cells = TechFixtures.cells(TechFixtures.cell(LatticePoint.origin));
        Bay bay = TechFixtures
                .bay(TechFixtures.id(path + "/bay"), cells, Coverage.anySubject, Footprint.bound(), Sharing.shared);
        Check<OccupantSnapshot> check = Check
                .of(TechFixtures.id(path + "/cow"), bay, Focuses.entity(TechFixtures.cow), Expectations.anyOccupant());
        return Blueprint.builder(TechFixtures.id(path)).bounds(TechFixtures.box(0, 0, 0, 1, 1, 1)).check(check).build();
    }

    private static Cuboid worldWindow(Siting siting, Cuboid local) {
        return Objects.requireNonNull(siting.toWorld(local), "the window maps");
    }
}
