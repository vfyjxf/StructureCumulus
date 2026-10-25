package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Label;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.predicate.BayView;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.DoorView;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focus;
import dev.vfyjxf.cumulus.kernel.predicate.FocusContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 22 §6.3, §6.5, §10.1; 17 §9: one two-cell bay, one door focus, one atomic claim. */
class DoorScenarioTest {

    private static final NamespacedId OAK_DOOR = NamespacedId.of("minecraft", "oak_door");
    private static final LatticePoint LOWER = LatticePoint.of(3, 0, 0);
    private static final LatticePoint UPPER = LatticePoint.of(3, 1, 0);
    private static final Cuboid BAY_CELLS = Cuboid.of(3, 0, 0, 4, 2, 1);
    private static final Siting AT = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);
    private static final OwnerId OWNER = OwnerId.of(NamespacedId.of("mymod", "overworld"),
            NamespacedId.of("mymod", "furnace_a"));
    private static final OwnerId NEIGHBOUR = OwnerId.of(NamespacedId.of("mymod", "overworld"),
            NamespacedId.of("mymod", "furnace_b"));

    @Test
    void oneBayTwoHalvesAndOneDoorFocus() {
        TestWorld world = TestWorld.builder()
                .putBlock(LOWER, OAK_DOOR)
                .putBlock(UPPER, OAK_DOOR)
                .build();
        assertEquals(2L, BAY_CELLS.volume(), "one 1x2x1 bay, not two one-cell bays");

        List<OccupantSnapshot> halves = world.occupants(BAY_CELLS, OccupantFilter.ofType(OAK_DOOR));
        assertEquals(2, halves.size(), "both halves are candidates of the one bay");

        CuboidSet union = CuboidSet.empty();
        for (OccupantSnapshot half : halves) {
            union = union.union(half.footprint());
        }
        assertEquals(BAY_CELLS.asSet().volume(), union.volume(),
                "22 §6.5: SUBJECT asserts bay.cells equals the union of the bound halves' footprints");
        assertTrue(union.containsAll(BAY_CELLS.asSet()) && BAY_CELLS.asSet().containsAll(union),
                "the two halves cover the bay exactly: " + union + " is the same region as " + BAY_CELLS);

        Focus<DoorView> focus = Focuses.door(LOWER.key());
        assertEquals(DoorView.class, focus.subject(),
                "22 §6.3: Focuses.door yields a DoorView carrying both halves, so the test never re-reads the world");

        FocusContext ctx = context(world, Stage.FORM);
        Outcome.Found<DoorView> found = assertInstanceOf(Outcome.Found.class, focus.take(ctx),
                "22 §6.3: both halves present, so the focus binds one DoorView");
        DoorView door = found.value();
        assertNotEquals(door.first(), door.second(), "a DoorView is two distinct halves");
        assertEquals(Set.of(LatticeKey.of(LOWER), LatticeKey.of(UPPER)),
                Set.of(door.first().anchor().key(), door.second().anchor().key()),
                "the door origin is the lower half and the second half is the cell above it");
        assertEquals(2, found.subjects().size(),
                "22 §6.2: Found.subjects is the only way the claim footprint is built");
        assertEquals(Set.copyOf(found.subjects()), new HashSet<>(door.halves()),
                "the subjects are exactly the two halves, in one Outcome");

        Bay doorBay = doorBay(focus);
        Check<DoorView> check = checkOf(doorBay);
        assertInstanceOf(Reply.Match.class, check.run(ctx).reply(), "the door test answers one Reply for the whole bay");
        assertInstanceOf(ClaimScope.Occupied.class, doorBay.scope());
        assertEquals(Coverage.SUBJECT, doorBay.coverage(), "22 §6.5: the bay is asserted through its subjects");
        assertEquals(DoorView.class, check.focus().subject(), "the focus and the expectation share one subject type");
    }

    @Test
    void bothCellsAreClaimedTogetherAndAHalfDoorCannotBecomeARow() {
        List<ClaimRow> rows = List.of(
                new ClaimRow(LatticeKey.of(LOWER), ClaimMode.EXCLUSIVE, OWNER, AT, id("door")),
                new ClaimRow(LatticeKey.of(UPPER), ClaimMode.EXCLUSIVE, OWNER, AT, id("door")));
        ClaimSet claims = new ClaimSet(rows, 7L);

        assertEquals(2, claims.rows().size(), "17 §9: a half-door cannot be half-claimed");
        assertEquals(Set.of(LatticeKey.of(LOWER), LatticeKey.of(UPPER)),
                new HashSet<>(claims.rows().stream().map(ClaimRow::cell).toList()), "both cells or neither");
        assertFalse(ClaimMatrix.conflicts(rows.get(0), rows.get(1)),
                "22 §10.1: rows of one (owner, siting) are never compared against their own claimant");

        ClaimRow neighbour = new ClaimRow(LatticeKey.of(UPPER), ClaimMode.EXCLUSIVE, NEIGHBOUR,
                Siting.of(LatticePoint.of(1, 0, 0), Orientation.IDENTITY), id("door"));
        assertTrue(ClaimMatrix.conflicts(rows.get(1), neighbour),
                "17 §9: a neighbour's door on one half is Failed(SC-CLM-0002) — the other half is not licit");

        CuboidSet oneHalf = CuboidSet.of(Cuboid.of(LOWER));
        assertNotEquals(BAY_CELLS.asSet(), oneHalf,
                "22 §6.5: one half never equals bay.cells, so a one-subject binding cannot settle the bay");
    }

    private static FocusContext context(TestWorld world, Stage stage) {
        return FocusContext.of(new BayView(id("door"), BAY_CELLS, world), AT,
                StructureContext.empty(TestWorld.DEFAULT_INSTANCE), stage, Locus.world(BAY_CELLS.asSet()));
    }

    @SuppressWarnings("unchecked")
    private static <T> Check<T> checkOf(Bay bay) {
        return (Check<T>) bay.requirement().checks().get(0);
    }

    private static Bay doorBay(Focus<DoorView> focus) {
        BayBuilder builder = BayBuilder.start(id("door"), BAY_CELLS, Coverage.SUBJECT,
                ClaimMode.EXCLUSIVE, new ClaimScope.Occupied(), "the door");
        Check<DoorView> check = new Check<>(id("oak_door"), Stage.FORM, builder.bayId(),
                focus, new DoorExpectation(), null);
        return builder.attach(new Requirement(List.of(check), List.of()));
    }

    private static final class DoorExpectation implements Expectation<DoorView> {

        @Override
        public Class<DoorView> subject() {
            return DoorView.class;
        }

        @Override
        public Reply test(ExpectationContext<DoorView> ctx) {
            DoorView door = ctx.subject();
            if (OAK_DOOR.equals(door.first().type()) && OAK_DOOR.equals(door.second().type())) {
                return Reply.match();
            }
            return Reply.mismatch(Finding.builder(FindingCode.of("SC-EXP-0001"))
                    .severity(Severity.ERROR)
                    .message("expected both halves of " + OAK_DOOR.text())
                    .label(ctx.locus(), "the bay is claimed only when both halves match")
                    .stage(ctx.stage()).build());
        }
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("mymod", path);
    }
}
