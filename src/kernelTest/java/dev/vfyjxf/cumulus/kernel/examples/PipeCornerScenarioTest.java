package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.predicate.BayView;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.Expectations;
import dev.vfyjxf.cumulus.kernel.predicate.FocusContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 22 §10.2; 08 §4.3 and §9: eight cells asserted, four owned, a neighbour that does not conflict. */
class PipeCornerScenarioTest {

    private static final NamespacedId PIPE = NamespacedId.of("mymod", "pipe");
    private static final Cuboid CORNER = Cuboid.of(0, 0, 0, 2, 2, 2);
    private static final Cuboid NEIGHBOUR = Cuboid.of(1, 0, 0, 3, 2, 2);
    private static final OwnerId OWNER_A = OwnerId.of(NamespacedId.of("mymod", "overworld"),
            NamespacedId.of("mymod", "pipe_a"));
    private static final OwnerId OWNER_B = OwnerId.of(NamespacedId.of("mymod", "overworld"),
            NamespacedId.of("mymod", "pipe_b"));
    private static final Siting AT_A = Siting.of(LatticePoint.of(0, 0, 0), Orientation.IDENTITY);
    private static final Siting AT_B = Siting.of(LatticePoint.of(1, 0, 0), Orientation.IDENTITY);

    @Test
    void cubicOccupiedAssertsEightCellsAndOwnsFour() {
        TestWorld world = pipeWorld();
        Bay corner = bay(id("corner"), CORNER);
        assertEquals(1, corner.requirement().checks().size());
        assertEquals(id("corner"), corner.requirement().checks().get(0).bay(),
                "09: a check names the bay it sits in, and the bay refuses a check that names another");

        assertEquals(Coverage.CUBIC, corner.coverage());
        assertInstanceOf(ClaimScope.Occupied.class, corner.scope(),
                "22 §10.2: the pipe corner is CUBIC + Occupied, not Owned and not SHARED");
        assertEquals(8L, corner.cells().volume(), "a 2x2x2 bay");

        assertInstanceOf(Reply.Match.class, checkOf(bay(id("corner"), CORNER)).run(context(world, id("corner"), CORNER, AT_A)).reply(),
                "CUBIC: every cell of bay.cells is decided — four pipes and four readable air cells");

        assertEquals(4, world.occupants(CORNER, OccupantFilter.ofType(PIPE)).size(),
                "four pipe cells and four air cells");
        CuboidSet owned = owned(world, CORNER);
        assertEquals(4L, owned.volume(), "08 §4.3: Occupied resolves from the bound footprints, never the bay box");
        assertEquals(4L, CORNER.volume() - owned.volume(), "the four air cells are asserted and not owned");
    }

    @Test
    void aNeighbourCornerClaimsTheOtherFourAndDoesNotConflict() {
        TestWorld world = pipeWorld();
        CuboidSet ownedA = owned(world, CORNER);
        CuboidSet ownedB = owned(world, NEIGHBOUR);

        assertEquals(4L, ownedA.volume());
        assertEquals(4L, ownedB.volume());
        assertTrue(ownedA.intersect(ownedB).isEmpty(),
                "08 §4.3: the neighbour's corner owns the other four cells of its box");

        CuboidSet shared = CORNER.asSet().intersect(NEIGHBOUR.asSet());
        assertEquals(4L, shared.volume(), "adjacent corners share their air face, not their pipes");
        assertTrue(ownedA.intersect(shared).isEmpty(),
                "CUBIC + Occupied asserts the shared face and claims none of it");

        Check<BayView> neighbourCheck = checkOf(bay(id("neighbour"), NEIGHBOUR));
        assertInstanceOf(Reply.Match.class, neighbourCheck.run(context(world, id("neighbour"), NEIGHBOUR, AT_B)).reply(),
                "the neighbour's own corner is asserted the same way over its own eight cells");

        List<ClaimRow> rowsA = rows(ownedA, OWNER_A, AT_A, "corner");
        List<ClaimRow> rowsB = rows(ownedB, OWNER_B, AT_B, "corner");
        for (ClaimRow left : rowsA) {
            for (ClaimRow right : rowsB) {
                assertFalse(ClaimMatrix.conflicts(left, right),
                        "22 §13: the neighbour owns its own four cells, so the settlements do not conflict");
            }
        }

        LatticeKey sharedCell = LatticeKey.of(shared.boxes().get(0).min());
        ClaimRow wholeBox = new ClaimRow(sharedCell, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, id("corner"));
        ClaimRow neighbourCell = new ClaimRow(sharedCell, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, id("corner"));
        assertTrue(ClaimMatrix.conflicts(wholeBox, neighbourCell),
                "08 §9: owning the whole box would conflict on the shared face — the falsifier for Owned");
    }

    @SuppressWarnings("unchecked")
    private static <T> Check<T> checkOf(Bay bay) {
        return (Check<T>) bay.requirement().checks().get(0);
    }

    private static Bay bay(NamespacedId id, Cuboid cells) {
        BayBuilder builder = BayBuilder.start(id, cells, Coverage.CUBIC, ClaimMode.EXCLUSIVE,
                new ClaimScope.Occupied(), "pipe corner");
        Check<BayView> check = new Check<>(id("corner_cells"), Stage.FORM, builder.bayId(),
                Focuses.bay(), Expectations.eachCell(Expectations.anyOf(
                        Expectations.block(PIPE), Expectations.air())), null);
        return builder.attach(new Requirement(List.of(check), List.of()));
    }

    private static FocusContext context(TestWorld world, NamespacedId bay, Cuboid cells, Siting siting) {
        return FocusContext.of(new BayView(bay, cells, world), siting,
                StructureContext.empty(TestWorld.DEFAULT_INSTANCE), Stage.FORM, Locus.world(cells.asSet()));
    }

    private static TestWorld pipeWorld() {
        TestWorld.Builder builder = TestWorld.builder();
        for (LatticePoint cell : TestWorld.cellsOf(Cuboid.of(0, 0, 0, 1, 2, 2))) {
            builder.putBlock(cell, PIPE);
        }
        for (LatticePoint cell : TestWorld.cellsOf(Cuboid.of(1, 0, 0, 2, 2, 2))) {
            builder.putAir(cell);
        }
        for (LatticePoint cell : TestWorld.cellsOf(Cuboid.of(2, 0, 0, 3, 2, 2))) {
            builder.putBlock(cell, PIPE);
        }
        return builder.build();
    }

    private static CuboidSet owned(TestWorld world, Cuboid bay) {
        CuboidSet owned = CuboidSet.empty();
        for (OccupantSnapshot occupant : world.occupants(bay, OccupantFilter.any())) {
            owned = owned.union(occupant.footprint());
        }
        return owned.intersect(bay);
    }

    private static List<ClaimRow> rows(CuboidSet cells, OwnerId owner, Siting siting, String bay) {
        List<ClaimRow> rows = new ArrayList<>();
        for (Cuboid box : cells.boxes()) {
            for (LatticePoint cell : TestWorld.cellsOf(box)) {
                rows.add(new ClaimRow(LatticeKey.of(cell), ClaimMode.EXCLUSIVE, owner, siting,
                        NamespacedId.of("mymod", bay)));
            }
        }
        return rows;
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("mymod", path);
    }
}
