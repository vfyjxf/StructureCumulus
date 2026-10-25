package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.engine.DirtyIndex;
import dev.vfyjxf.cumulus.kernel.occupant.ChangeSet;
import dev.vfyjxf.cumulus.kernel.occupant.EntityKey;
import dev.vfyjxf.cumulus.kernel.occupant.EntityWindow;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.predicate.BayView;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.EntitySnapshot;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focus;
import dev.vfyjxf.cumulus.kernel.predicate.FocusContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 22 §5.4, §6.5; 06 §: an entity window moves without a block change, and that alone dirties its unit. */
class EntityPenScenarioTest {

    private static final NamespacedId COW_TYPE = NamespacedId.of("minecraft", "cow");
    private static final UUID COW_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c0");
    private static final EntityKey COW = new EntityKey(COW_ID, COW_TYPE, 42L);
    private static final Cuboid PEN = Cuboid.of(0, 0, 0, 3, 2, 3);
    private static final Cuboid INSIDE = Cuboid.of(0, 0, 0, 2, 1, 1);
    private static final Cuboid ALSO_INSIDE = Cuboid.of(1, 1, 1, 3, 2, 2);
    private static final Cuboid OUTSIDE = Cuboid.of(4, 0, 1, 6, 1, 2);
    private static final Cuboid WINDOW = Cuboid.of(-EntityWindow.MAX_ENTITY_SPAN, -EntityWindow.MAX_ENTITY_SPAN,
            -EntityWindow.MAX_ENTITY_SPAN, 3 + EntityWindow.MAX_ENTITY_SPAN, 2 + EntityWindow.MAX_ENTITY_SPAN,
            3 + EntityWindow.MAX_ENTITY_SPAN);
    private static final Cuboid NEIGHBOUR_CELLS = Cuboid.of(8, 0, 0, 10, 1, 1);
    private static final Siting AT = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);

    @Test
    void aPresenceBayBindsOneEntitySubjectWithATwoCellFootprint() {
        TestWorld world = penWorld(INSIDE);
        Focus<EntitySnapshot> focus = Focuses.entity(COW_ID);
        assertEquals(EntitySnapshot.class, focus.subject(), "22 §6.3: Focuses.entity names its subject type");

        FocusContext ctx = context(world, PEN);
        Outcome.Found<EntitySnapshot> found = assertInstanceOf(Outcome.Found.class, focus.take(ctx),
                "one cow in the pen, so the focus binds it");
        EntitySnapshot cow = found.value();
        assertEquals(COW_ID, cow.id(), "the authored identity is the UUID the focus was given");
        assertEquals(COW_TYPE, cow.ref().type());
        assertEquals(2L, cow.occupant().footprint().volume(), "17: a cow is a two-cell footprint, not a point");
        assertEquals(List.of(cow.ref()), found.subjects(), "one entity, one bound subject");

        Bay pen = penBay(focus);
        assertEquals(Coverage.PRESENCE, pen.coverage(), "22 §6.5: an entity criterion asserts no cell");
        assertInstanceOf(ClaimScope.Audit.class, pen.scope(),
                "22 §6.5: PRESENCE cannot be EXCLUSIVE without Declared, so the legal reading owns nothing");
        assertInstanceOf(Reply.Match.class, pen.requirement().checks().get(0).run(ctx).reply(),
                "the presence criterion holds while the cow is inside the pen");
        assertEquals(3, EntityWindow.MAX_ENTITY_SPAN, "22 §5.4: a type wider than the span is SC-BAY-0110");
    }

    @Test
    void twoEntitiesCompetingForOnePresenceBayAreAmbiguous() {
        TestWorld world = TestWorld.builder()
                .putEntity(INSIDE.min(), COW_TYPE, INSIDE)
                .putEntity(ALSO_INSIDE.min(), COW_TYPE, ALSO_INSIDE)
                .build();

        Outcome.Missing<EntitySnapshot> missing = assertInstanceOf(Outcome.Missing.class,
                Focuses.entity(COW_ID).take(context(world, PEN)),
                "22 §6.6: more than one candidate and none decisive is SC-FOC-0201, not a silent pick");
        assertFalse(missing.findings().ofCode(FindingCode.of("SC-FOC-0201")).isEmpty(),
                "the read model carries no UUID, so the focus reports ambiguity instead of guessing");
    }

    @Test
    void aPresenceBayCannotBeExclusiveOverAnOccupiedScope() {
        IllegalArgumentException exclusive = assertThrows(IllegalArgumentException.class,
                () -> {
                    BayBuilder builder = BayBuilder.start(id("pen"), PEN, Coverage.PRESENCE,
                            ClaimMode.EXCLUSIVE, new ClaimScope.Occupied());
                    Check<EntitySnapshot> check = new Check<>(id("one_cow"), Stage.FORM, builder.bayId(),
                            Focuses.entity(COW_ID), presence(), null);
                    builder.attach(new Requirement(List.of(check), List.of()));
                },
                "SC-CHK-0106: PRESENCE asserts no cell, so it cannot hold EXCLUSIVE with a non-Declared scope");
        assertTrue(exclusive.getMessage().contains("SC-CHK-0106"), "the refusal names its code");

        assertThrows(IllegalArgumentException.class,
                () -> BayBuilder.start(id("pen"), PEN, Coverage.SUBJECT, ClaimMode.EXCLUSIVE,
                        new ClaimScope.Owned()),
                "SC-CHK-0106: SUBJECT + Owned — a subject assertion cannot own a whole bay");

        assertThrows(IllegalArgumentException.class,
                () -> {
                    BayBuilder other = BayBuilder.start(id("other_pen"), PEN, Coverage.PRESENCE,
                            ClaimMode.SHARED, new ClaimScope.Audit());
                    BayBuilder pen = BayBuilder.start(id("pen"), PEN, Coverage.PRESENCE,
                            ClaimMode.SHARED, new ClaimScope.Audit());
                    Check<EntitySnapshot> check = new Check<>(id("one_cow"), Stage.FORM, other.bayId(),
                            Focuses.entity(COW_ID), presence(), null);
                    pen.attach(new Requirement(List.of(check), List.of()));
                },
                "SC-CHK-0107: a check belongs to exactly one bay and names it");
    }

    @Test
    void anOutwardMoveWithoutABlockChangeDirtiesTheUnitThroughItsWindow() {
        TestWorld before = penWorld(INSIDE);
        TestWorld after = penWorld(OUTSIDE);
        assertTrue(PEN.asSet().intersect(OUTSIDE.asSet()).isEmpty(), "the cow left the pen: no bay cell changed");

        Map<LatticePoint, EntityKey> captured = entityCells(before, WINDOW, COW_TYPE, COW);
        Map<LatticePoint, EntityKey> live = entityCells(after, WINDOW, COW_TYPE, COW);
        assertEquals(2, captured.size(), "the captured window holds the two cells the cow stood on");
        assertEquals(2, live.size(), "the live window holds the two cells it stands on now");
        assertNotEquals(captured.keySet(), live.keySet(), "a walk moves cells, not the EntityKey");

        Set<EntityKey> moved = movedEntities(captured, live);
        assertEquals(Set.of(COW), moved,
                "a pure move keeps UUID, type and dataDigest; the cell map is the outward-move witness");

        ChangeSet change = new ChangeSet(Set.of(), Set.of(), Set.of(), moved, Set.of(), 7L);
        assertTrue(change.cells().isEmpty(), "no block changed");
        assertEquals(1, change.entities().size(), "the entity set is what carries the movement");
        assertFalse(change.isEmpty(), "22 §5.4: an entity-only change is not an empty change");

        ReadScope penUnit = new ReadScope(PEN.asSet(), Set.of(), List.of(new EntityWindow(WINDOW, COW_TYPE)));
        ReadScope blockUnit = new ReadScope(NEIGHBOUR_CELLS.asSet(), Set.of(), List.of());

        DirtyIndex index = new DirtyIndex(TestWorld.DEFAULT_DIMENSION);
        assertEquals(0L, index.cellStamp(penUnit), "a fresh index has marked nothing");
        index.markEntity(COW, OUTSIDE);

        assertTrue(index.cellStamp(penUnit) > 0L,
                "22 §5.4: the window diff dirties the unit that read it even though no cell of the bay moved");
        assertEquals(0L, index.cellStamp(blockUnit),
                "a unit whose read closure is disjoint from the change keeps its stamp and is reused");
    }

    private static TestWorld penWorld(Cuboid footprint) {
        return TestWorld.builder().putEntity(footprint.min(), COW_TYPE, footprint).build();
    }

    private static FocusContext context(TestWorld world, Cuboid cells) {
        return FocusContext.of(new BayView(id("pen"), cells, world), AT,
                StructureContext.empty(TestWorld.DEFAULT_INSTANCE), Stage.FORM, Locus.world(cells.asSet()));
    }

    private static Expectation<EntitySnapshot> presence() {
        return new Expectation<>() {
            @Override
            public Class<EntitySnapshot> subject() {
                return EntitySnapshot.class;
            }

            @Override
            public Reply test(ExpectationContext<EntitySnapshot> ctx) {
                return Reply.match();
            }
        };
    }

    private static Bay penBay(Focus<EntitySnapshot> focus) {
        BayBuilder builder = BayBuilder.start(id("pen"), PEN, Coverage.PRESENCE,
                ClaimMode.SHARED, new ClaimScope.Audit(), "the pen");
        Check<EntitySnapshot> check = new Check<>(id("one_cow"), Stage.FORM, builder.bayId(),
                focus, presence(), null);
        return builder.attach(new Requirement(List.of(check), List.of()));
    }

    private static Map<LatticePoint, EntityKey> entityCells(TestWorld world, Cuboid box, NamespacedId type,
                                                            EntityKey key) {
        Map<LatticePoint, EntityKey> cells = new TreeMap<>();
        for (OccupantSnapshot occupant : world.occupants(box, OccupantFilter.ofType(type))) {
            for (LatticePoint cell : TestWorld.cellsOf(occupant.footprint())) {
                cells.put(cell, key);
            }
        }
        return cells;
    }

    private static Set<EntityKey> movedEntities(Map<LatticePoint, EntityKey> captured,
                                                Map<LatticePoint, EntityKey> live) {
        Set<EntityKey> moved = new HashSet<>();
        for (Map.Entry<LatticePoint, EntityKey> entry : captured.entrySet()) {
            if (!entry.getValue().equals(live.get(entry.getKey()))) {
                moved.add(entry.getValue());
            }
        }
        for (Map.Entry<LatticePoint, EntityKey> entry : live.entrySet()) {
            if (!entry.getValue().equals(captured.get(entry.getKey()))) {
                moved.add(entry.getValue());
            }
        }
        return moved;
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("mymod", path);
    }
}
