package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
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
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The entity focus binds exactly one entity of its type: zero and many are both unavailable. */
class EntityFocusScenarioTest {

    private static final NamespacedId cow = NamespacedId.of("minecraft", "cow");
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);

    @Test
    void noEntityIsUndecidedWithOcc0001() {
        TestWorld.View view = new TestWorld.View().air(LatticePoint.origin).air(LatticePoint.of(1, 0, 0));
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, check(view));
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(
            undecided.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.unreadableCell.code())),
            "zero entities of the type is SC-OCC-0001"
        );
        assertTrue(
            undecided.findings().findings().stream().noneMatch(finding -> finding.message().startsWith("SC-")),
            "the message carries no code text"
        );
    }

    @Test
    void twoEntitiesAreUndecidedWithOcc0001() {
        TestWorld.View view = new TestWorld.View()
                .put(TechFixtures.entity(cow, LatticePoint.origin, Cuboid.of(LatticePoint.origin)))
                .put(TechFixtures.entity(cow, LatticePoint.of(1, 0, 0), Cuboid.of(LatticePoint.of(1, 0, 0))));
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, check(view));
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(
            undecided.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.unreadableCell.code())),
            "more than one entity of the type is SC-OCC-0001"
        );
        assertTrue(
            undecided.findings().findings().stream().noneMatch(finding -> finding.message().startsWith("SC-")),
            "the message carries no code text"
        );
    }

    @Test
    void exactlyOneEntityIsAdmittedAndBindsIt() {
        OccupantSnapshot sole = TechFixtures.entity(cow, LatticePoint.origin, Cuboid.of(LatticePoint.origin));
        TestWorld.View view = new TestWorld.View().air(LatticePoint.of(1, 0, 0)).put(sole);
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, check(view));
        List<BayBinding> bindings = admitted.outcome().bindings();
        assertEquals(1, bindings.size());
        assertEquals(List.of(sole), bindings.get(0).subjects());
    }

    private static MatchResult check(TestWorld.View view) {
        Bay bay = Bay.of(
            MagicFixtures.id("entity/pen"),
            CuboidSet.of(bounds),
            Coverage.anySubject,
            Footprint.bound(),
            Sharing.shared
        );
        Blueprint blueprint = Blueprint.builder(MagicFixtures.id("entity/pen_blueprint")).bounds(bounds)
                .check(
                    Check.of(MagicFixtures.id("entity/pen_check"), bay, Focuses.entity(cow), Expectations.anyOccupant())
                ).build();
        MatchRequest request = MatchRequest.of(
            blueprint,
            Siting.of(LatticePoint.origin, Orientation.identity),
            new TestWorld.Lease(view),
            new InstanceId(TestWorld.world, 1),
            OwnerId.of(MagicFixtures.id("entity/owner")),
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        return MatchEngine.check(request);
    }
}
