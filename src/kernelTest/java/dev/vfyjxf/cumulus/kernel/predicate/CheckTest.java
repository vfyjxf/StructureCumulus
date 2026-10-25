package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.context.ContextDraft;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextKeys;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.ContextTier;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.context.Retention;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantKind;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckTest {

    private static final NamespacedId WORLD = NamespacedId.of("test", "world");
    private static final InstanceId INSTANCE = InstanceId.of(WORLD, 1L);
    private static final NamespacedId BAY_ID = NamespacedId.of("test", "bay");
    private static final NamespacedId STONE = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId OAK_DOOR = NamespacedId.of("minecraft", "oak_door");

    private static final LatticePoint P0 = LatticePoint.of(0, 0, 0);
    private static final LatticePoint P1 = LatticePoint.of(1, 0, 0);
    private static final LatticePoint ABOVE_P0 = LatticePoint.of(0, 1, 0);
    private static final Cuboid CELLS = Cuboid.of(0, 0, 0, 2, 1, 1);
    private static final Cuboid DOOR_CELLS = Cuboid.of(0, 0, 0, 1, 2, 1);

    private static final ContextKey<Integer> COUNT = ContextKeys.of(
            NamespacedId.of("test", "count"), Integer.class, Retention.CONTEXT, null, null);

    @Test
    void aMissingContextKeyNeverReachesTheTest() {
        AtomicBoolean called = new AtomicBoolean();
        Check<Integer> check = check(NamespacedId.of("test", "count_check"), CELLS, Coverage.PRESENCE,
                new ClaimScope.Declared(CuboidSet.empty()), Focuses.context(COUNT), expectation(Integer.class, ctx -> {
                    called.set(true);
                    return Reply.match();
                }));

        Reply reply = check.run(context(TestWorld.create(), CELLS, StructureContext.empty(INSTANCE))).reply();

        assertTrue(reply instanceof Reply.Unreadable);
        assertFalse(called.get());
        assertEquals(FindingCode.of("SC-FOC-0302"),
                ((Reply.Unreadable) reply).findings().findings().get(0).code(),
                "22 §6.6: the focus's declared code surfaces, not the context's own finding");
    }

    @Test
    void aPresentContextKeyBecomesTheSubject() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, store.revision());
        draft.put(COUNT, 7);
        store.publish(draft);

        Check<Integer> check = check(NamespacedId.of("test", "count_check"), CELLS, Coverage.PRESENCE,
                new ClaimScope.Declared(CuboidSet.empty()), Focuses.context(COUNT), expectation(Integer.class, ctx -> {
                    assertEquals(Integer.valueOf(7), ctx.subject());
                    return Reply.match();
                }));

        assertTrue(check.run(context(TestWorld.create(), CELLS, store.context(INSTANCE))).reply() instanceof Reply.Match);
    }

    @Test
    void eachCellDecidesEveryCellOfTheBay() {
        Check<BayView> check = check(NamespacedId.of("test", "every_cell"), CELLS, Coverage.CUBIC,
                new ClaimScope.Owned(), Focuses.bay(), Expectations.eachCell(Expectations.block(STONE)));

        TestWorld stone = TestWorld.create().put(P0, block(P0, STONE)).put(P1, block(P1, STONE));
        assertTrue(check.run(context(stone, CELLS, StructureContext.empty(INSTANCE))).reply() instanceof Reply.Match);

        TestWorld mixed = TestWorld.create().put(P0, block(P0, STONE)).put(P1, air(P1));
        assertTrue(check.run(context(mixed, CELLS, StructureContext.empty(INSTANCE))).reply() instanceof Reply.Mismatch);

        TestWorld refused = TestWorld.create().put(P0, block(P0, STONE)).unreadable(P1);
        Reply reply = check.run(context(refused, CELLS, StructureContext.empty(INSTANCE))).reply();
        assertTrue(reply instanceof Reply.Unreadable);
        assertEquals(FindingCode.of("SC-FOC-0301"),
                ((Reply.Unreadable) reply).findings().findings().get(0).code());
    }

    @Test
    void aDoorBindsBothHalvesInOneCheck() {
        AtomicReference<List<OccupantRef>> subjects = new AtomicReference<>();
        Check<DoorView> check = check(NamespacedId.of("test", "door"), DOOR_CELLS, Coverage.SUBJECT,
                new ClaimScope.Occupied(), Focuses.door(P0.key()), expectation(DoorView.class, ctx -> {
                    subjects.set(ctx.subjects());
                    assertEquals(OAK_DOOR, ctx.subject().first().type());
                    assertEquals(OAK_DOOR, ctx.subject().second().type());
                    return Reply.match();
                }));
        TestWorld world = TestWorld.create()
                .put(P0, block(P0, OAK_DOOR))
                .put(ABOVE_P0, block(ABOVE_P0, OAK_DOOR));

        assertTrue(check.run(context(world, DOOR_CELLS, StructureContext.empty(INSTANCE))).reply() instanceof Reply.Match);
        assertEquals(2, Objects.requireNonNull(subjects.get()).size());
    }

    @Test
    void aMissingHalfIsUnreadableAndNotAMismatch() {
        Check<DoorView> check = check(NamespacedId.of("test", "door"), DOOR_CELLS, Coverage.SUBJECT,
                new ClaimScope.Occupied(), Focuses.door(P0.key()),
                expectation(DoorView.class, ctx -> Reply.match()));
        TestWorld world = TestWorld.create().put(P0, block(P0, OAK_DOOR)).put(ABOVE_P0, air(ABOVE_P0));

        Reply reply = check.run(context(world, DOOR_CELLS, StructureContext.empty(INSTANCE))).reply();

        assertTrue(reply instanceof Reply.Unreadable);
        assertEquals(FindingCode.of("SC-FOC-0101"),
                ((Reply.Unreadable) reply).findings().findings().get(0).code());
    }

    @Test
    void oneCellFocusReportsTheOccupantAndItsRefAsSubject() {
        AtomicReference<OccupantRef> ref = new AtomicReference<>();
        Expectation<OccupantSnapshot> stone = Expectations.block(STONE);
        Check<OccupantSnapshot> check = check(NamespacedId.of("test", "stone"), CELLS, Coverage.SUBJECT,
                new ClaimScope.Occupied(), Focuses.occupant(P0.key()),
                expectation(OccupantSnapshot.class, ctx -> {
                    ref.set(ctx.subjects().get(0));
                    return stone.test(ctx);
                }));

        CheckResult result = check.run(context(TestWorld.create().put(P0, block(P0, STONE)), CELLS,
                StructureContext.empty(INSTANCE)));
        assertTrue(result.reply() instanceof Reply.Match);
        assertEquals(P0, Objects.requireNonNull(result.subjects().get(0)).anchor());
        assertEquals(P0, Objects.requireNonNull(ref.get()).anchor());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aFocusAndAnExpectationMustShareOneSubjectType() {
        Bay bay = presenceBay(CELLS);
        Expectation<BayView> cells = Expectations.eachCell(Expectations.block(STONE));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new Check(NamespacedId.of("test", "mixed"), Stage.FORM, bay.id(),
                        Focuses.occupant(P0.key()), cells, null));

        assertTrue(Objects.requireNonNull(refused.getMessage()).contains("SC-CHK-0101"), refused.getMessage());
    }

    @Test
    @SuppressWarnings("NullAway")
    void aCheckBelongsToExactlyOneBay() {
        NullPointerException refused = assertThrows(NullPointerException.class,
                () -> new Check<>(NamespacedId.of("test", "orphan"), Stage.FORM, null,
                        Focuses.occupant(P0.key()), Expectations.block(STONE), null));

        assertTrue(Objects.requireNonNull(refused.getMessage()).contains("SC-CHK-0107"), refused.getMessage());
    }

    @Test
    void aFocusDeclaresItsSubjectKindAndDecisionCells() {
        focusDeclares(Focuses.occupant(P0.key()), CELLS, DecisionKind.SUBJECT, 0L);
        focusDeclares(Focuses.nested(P0.key()), CELLS, DecisionKind.SUBJECT, 0L);
        focusDeclares(Focuses.entity(UUID.randomUUID()), CELLS, DecisionKind.SUBJECT, 0L);
        focusDeclares(Focuses.context(COUNT), CELLS, DecisionKind.PRESENCE, 0L);
        focusDeclares(Focuses.bay(), CELLS, DecisionKind.CUBIC, CELLS.volume());
        focusDeclares(Focuses.allCells(), CELLS, DecisionKind.CUBIC, CELLS.volume());
        focusDeclares(Focuses.door(P0.key()), DOOR_CELLS, DecisionKind.SUBJECT, DOOR_CELLS.volume());
    }

    @Test
    void aBayFocusReadsEveryCellItDecides() {
        Cuboid bay = Cuboid.of(0, 0, 0, 2, 2, 2);

        assertEquals(CuboidSet.of(bay), Focuses.bay().reads(bay).cells(),
                "22 §6.3: Focuses.bay reads the whole bay, not nothing");
        assertTrue(Focuses.allCells().reads(bay).cells().containsAll(CuboidSet.of(bay)),
                "Focuses.allCells reads every cell it decides");
    }

    private static <T> void focusDeclares(Focus<T> focus, Cuboid cells, DecisionKind kind, long decisionVolume) {
        assertEquals(kind, focus.decisionKind(), focus.subject().getSimpleName());
        assertEquals(decisionVolume, focus.staticDecisionCells(cells).volume(),
                focus.subject().getSimpleName());
        assertFalse(focus.describeMissing().isEmpty(), "a focus declares how it misses");
    }

    /**
     * A check needs its bay and the bay needs its checks, so the bay is created first and sealed after
     * its one check exists.
     */
    private static <T> Check<T> check(NamespacedId id, Cuboid cells, Coverage coverage, ClaimScope scope,
                                      Focus<T> focus, Expectation<T> expectation) {
        BayBuilder builder = BayBuilder.start(BAY_ID, cells, coverage, ClaimMode.EXCLUSIVE, scope);
        Check<T> check = new Check<>(id, Stage.FORM, builder.bayId(), focus, expectation, null);
        builder.attach(new Requirement(List.of(check), List.of()));
        return check;
    }

    private static Bay presenceBay(Cuboid cells) {
        BayBuilder builder = BayBuilder.start(BAY_ID, cells, Coverage.PRESENCE, ClaimMode.EXCLUSIVE,
                new ClaimScope.Declared(CuboidSet.empty()), "the test bay");
        Check<Integer> anchor = new Check<>(NamespacedId.of("test", "presence"), Stage.FORM, builder.bayId(),
                Focuses.context(COUNT), expectation(Integer.class, ctx -> Reply.match()), null);
        return builder.attach(new Requirement(List.of(anchor), List.of()));
    }

    private static <T> Expectation<T> expectation(Class<T> subject,
                                                  Function<ExpectationContext<T>, Reply> body) {
        return new Expectation<>() {
            @Override
            public Reply test(ExpectationContext<T> ctx) {
                return body.apply(ctx);
            }

            @Override
            public Class<T> subject() {
                return subject;
            }
        };
    }

    private static FocusContext context(WorldView world, Cuboid cells, ContextView view) {
        BayView bay = new BayView(BAY_ID, cells, world);
        return FocusContext.of(bay, Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY), view, Stage.FORM,
                Locus.world(cells.asSet()));
    }

    private static OccupantSnapshot block(LatticePoint cell, NamespacedId type) {
        OccupantRef ref = new OccupantRef(INSTANCE, type, OccupantKind.BLOCK, cell, Cuboid.of(cell));
        return new OccupantSnapshot(ref, OccupantKind.BLOCK, OccupantDescriptor.of(type), Cuboid.of(cell),
                Set.of(), null);
    }

    private static OccupantSnapshot air(LatticePoint cell) {
        return OccupantSnapshot.air(INSTANCE, cell);
    }
}
