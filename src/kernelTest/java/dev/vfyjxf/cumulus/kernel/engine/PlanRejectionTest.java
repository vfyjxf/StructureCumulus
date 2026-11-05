package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
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
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A planner refusal carries its code entry; an unrelated IAE stays a programmer error. */
class PlanRejectionTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final CuboidSet oneCell = CuboidSet.of(Cuboid.of(origin));

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    @Test
    void aPlannerRejectionCarriesItsCode() {
        Siting identity = MagicFixtures.identity(origin);

        Bay box = MagicFixtures.bay(
            id("lattice_bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Blueprint atTheEdge = Blueprint.builder(id("edge")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(Check.of(id("edge_cells"), box, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant())))
                .build();
        PlanRejected offLattice = assertThrows(
            PlanRejected.class,
            () -> ClaimPlanner.derive(
                atTheEdge,
                MagicFixtures.identity(LatticePoint.of(LatticePoint.maxXz, 0, 0)),
                MagicFixtures.instance,
                List.of()
            )
        );
        assertEquals(Codes.locusUnmapped, offLattice.code());

        Bay bound = MagicFixtures.bay(id("empty_bound"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Blueprint boundBlueprint = Blueprint.builder(id("empty_bound")).bounds(Cuboid.of(origin))
                .check(Check.of(id("empty_check"), bound, Focuses.occupant(origin), Expectations.anyOccupant()))
                .build();
        PlanRejected empty = assertThrows(
            PlanRejected.class,
            () -> ClaimPlanner.derive(boundBlueprint, identity, MagicFixtures.instance, List.of())
        );
        assertEquals(Codes.boundEmpty, empty.code());

        OccupantSnapshot block = TestWorld.block(TestWorld.stone, origin);
        Bay left = MagicFixtures.bay(id("conflict_left"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Bay right = MagicFixtures.bay(id("conflict_right"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.shared);
        Blueprint conflictBlueprint = Blueprint.builder(id("conflict")).bounds(Cuboid.of(origin))
                .check(Check.of(id("left_check"), left, Focuses.occupant(origin), Expectations.anyOccupant()))
                .check(Check.of(id("right_check"), right, Focuses.occupant(origin), Expectations.anyOccupant()))
                .build();
        PlanRejected conflict = assertThrows(
            PlanRejected.class,
            () -> ClaimPlanner.derive(
                conflictBlueprint,
                identity,
                MagicFixtures.instance,
                List.of(new BayBinding(left, List.of(block)), new BayBinding(right, List.of(block)))
            )
        );
        assertEquals(Codes.claimConflict, conflict.code());

        OccupantSnapshot nested = new OccupantSnapshot(
            new OccupantRef(TestWorld.snapshot, 0, TestWorld.stone, OccupantKind.nested, origin),
            new OccupantDescriptor(TestWorld.stone, OccupantKind.nested),
            Cuboid.of(origin),
            List.of()
        );
        Bay nestedBay = MagicFixtures.bay(id("nested_bay"), oneCell, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Blueprint nestedBlueprint = Blueprint.builder(id("nested")).bounds(Cuboid.of(origin))
                .check(Check.of(id("nested_check"), nestedBay, Focuses.occupant(origin), Expectations.anyOccupant()))
                .build();
        PlanRejected nestedRejection = assertThrows(
            PlanRejected.class,
            () -> ClaimPlanner.derive(
                nestedBlueprint,
                identity,
                MagicFixtures.instance,
                List.of(new BayBinding(nestedBay, List.of(nested)))
            )
        );
        assertEquals(Codes.bindingInvalid, nestedRejection.code());
        String message = Objects.requireNonNull(nestedRejection.getMessage(), "message");
        assertTrue(message.startsWith("SC-CLM-0004:"), message);
    }

    @Test
    void theEngineSurfacesTheTypedPlannerRefusal() {
        Bay bound = MagicFixtures.bay(id("air_bound"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("air_bound")).bounds(Cuboid.of(origin))
                .check(Check.of(id("air_bound_check"), bound, Focuses.occupant(origin), Expectations.air()))
                .build();
        TestWorld.View view = MagicFixtures.view().put(TestWorld.airOccupant(origin));
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MagicFixtures.check(blueprint, MagicFixtures.identity(origin), view, new ClaimView(List.of(), 0, true))
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.boundEmpty), () -> failed.findings().findings().toString());
    }
}
