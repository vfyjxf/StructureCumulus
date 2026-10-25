package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Blueprint;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantKind;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.predicate.Bindings;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.DoorView;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClaimPlannerTest {

    private static final NamespacedId WORLD = NamespacedId.of("minecraft", "overworld");
    private static final NamespacedId BLUEPRINT = NamespacedId.of("test", "door_machine");
    private static final NamespacedId DOOR_BAY = NamespacedId.of("test", "door");
    private static final NamespacedId BLOCK = NamespacedId.of("minecraft", "oak_door");
    private static final Cuboid CELLS = Cuboid.of(0, 0, 0, 1, 2, 1);
    private static final Siting AT = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);
    private static final OwnerId OWNER = OwnerId.of(WORLD, NamespacedId.of("test", "player"));
    private static final InstanceId INSTANCE = InstanceId.of(WORLD, 1L);

    @Test
    void aSubjectBayMustBindExactlyItsCells() {
        Bay bay = doorBay();
        Blueprint blueprint = Blueprint.builder(BLUEPRINT).bounds(CELLS).bay(bay).build();
        OccupantRef half = new OccupantRef(INSTANCE, BLOCK, OccupantKind.BLOCK,
                LatticePoint.ORIGIN, Cuboid.of(0, 0, 0, 1, 1, 1));
        BayBinding binding = new BayBinding(DOOR_BAY, List.of(half), Bindings.empty());

        ClaimPlan plan = ClaimPlanner.plan(blueprint, AT, List.of(binding), OWNER);

        ClaimPlan.Invalid invalid = assertInstanceOf(ClaimPlan.Invalid.class, plan,
                "a one-cell subject cannot satisfy a two-cell SUBJECT bay");
        assertEquals("SC-BAY-0107", invalid.findings().findings().get(0).code().text());
    }

    private static Bay doorBay() {
        BayBuilder builder = BayBuilder.start(DOOR_BAY, CELLS, Coverage.SUBJECT,
                ClaimMode.EXCLUSIVE, new ClaimScope.Occupied());
        Expectation<DoorView> door = new Expectation<>() {
            @Override
            public Class<DoorView> subject() {
                return DoorView.class;
            }

            @Override
            public Reply test(ExpectationContext<DoorView> ctx) {
                return Reply.match();
            }
        };
        Check<DoorView> check = new Check<>(NamespacedId.of("test", "both_halves"), Stage.SETTLE,
                builder.bayId(), Focuses.door(LatticeKey.of(LatticePoint.ORIGIN)), door, null);
        return builder.attach(new Requirement(List.of(check), List.of()));
    }
}
