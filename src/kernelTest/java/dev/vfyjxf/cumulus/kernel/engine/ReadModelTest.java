package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.FocusResult;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadModelTest {

    private static final Siting atOrigin = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final OwnerId owner = OwnerId.of(id("owner"));
    private static final InstanceId instance = new InstanceId(TestWorld.world, 1);

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId mc(String path) {
        return NamespacedId.of("minecraft", path);
    }

    private static FindingCode hostCode(int number) {
        return FindingCode.of(FindingCode.Family.mc, number);
    }

    private static MatchRequest request(Blueprint blueprint, TestWorld.View view) {
        return MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
    }

    private static Bay bay(CuboidSet cells, Coverage coverage, Footprint footprint) {
        return Bay.of(id("bay"), cells, coverage, footprint, Sharing.exclusive);
    }

    @Test
    void readIsTotalAcrossBoundsAndDerivedAccessors() {
        TestWorld.View view = new TestWorld.View().bounds(Cuboid.of(0, 0, 0, 2, 1, 1)).air(LatticePoint.origin);

        CellRead inside = view.read(LatticePoint.origin);
        CellRead.Found found = assertInstanceOf(CellRead.Found.class, inside);
        assertTrue(found.occupant().isAir());
        assertNull(view.refusalAt(LatticePoint.origin));
        assertEquals(ReadStatus.readable, view.statusOf(LatticePoint.origin));
        assertEquals(found.occupant(), view.occupant(LatticePoint.origin));

        LatticePoint outside = LatticePoint.of(2, 0, 0);
        CellRead edge = view.read(outside);
        ReadRefusal refusal = assertInstanceOf(CellRead.Unreadable.class, edge).refusal();
        assertEquals(ReadStatus.outOfBounds, refusal.status());
        assertEquals(Codes.unreadableRead.code(), refusal.code());
        assertNull(view.occupant(outside));
        assertEquals(refusal, view.refusalAt(outside));
        assertEquals(ReadStatus.outOfBounds, view.statusOf(outside));
    }

    @Test
    void occupantRefMustMatchSnapshotFrameAndBody() {
        OccupantSnapshot block = TestWorld.block(mc("stone_bricks"), LatticePoint.origin);
        TestWorld.View view = new TestWorld.View().put(block);
        assertEquals(block, view.occupant(block.ref()));
        assertNull(
            view.occupant(
                new OccupantRef(
                    TestWorld.snapshot,
                    0,
                    mc("stone_bricks"),
                    OccupantKind.block,
                    LatticePoint.origin,
                    NamespacedId.of("test", "body")
                )
            )
        );
        assertNull(
            view.occupant(
                new OccupantRef(
                    new SnapshotId(TestWorld.world, 2),
                    0,
                    mc("stone_bricks"),
                    OccupantKind.block,
                    LatticePoint.origin
                )
            )
        );
        assertNull(
            view.occupant(
                new OccupantRef(TestWorld.snapshot, 1, mc("stone_bricks"), OccupantKind.block, LatticePoint.origin)
            )
        );
    }

    @Test
    void partialSectionRefusesOnlyItsCells() {
        LatticePoint origin = LatticePoint.origin;
        LatticePoint refused = LatticePoint.of(1, 0, 0);
        LatticePoint readable = LatticePoint.of(2, 0, 0);
        ReadRefusal refusal = new ReadRefusal(ReadStatus.denied, hostCode(2));
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(mc("stone_bricks"), origin))
                .refuse(refused, refusal);

        SectionKey key = view.sectionOf(origin);
        assertEquals(key, view.sectionOf(readable));
        SectionSlot.Partial partial = assertInstanceOf(SectionSlot.Partial.class, view.slot(key));
        assertTrue(partial.readable());
        assertEquals(Map.of(refused, refusal), partial.unreadable());
        assertFalse(partial.unreadable().isEmpty());
        assertFalse(partial.section().occupants().containsKey(refused));

        assertNull(view.occupant(refused));
        assertEquals(refusal, view.refusalAt(refused));
        assertEquals(ReadStatus.denied, view.statusOf(refused));
        assertTrue(Objects.requireNonNull(view.occupant(readable), "air").isAir());
        assertEquals(mc("stone_bricks"), Objects.requireNonNull(view.occupant(origin), "stone").descriptor().type());
        assertEquals(ReadStatus.readable, view.statusOf(readable));
    }

    @Test
    void viewDigestCoversContentAndRefusalStatusNotHostCode() {
        LatticePoint cell = LatticePoint.of(1, 0, 0);
        TestWorld.View first = new TestWorld.View().put(TestWorld.block(mc("stone_bricks"), LatticePoint.origin))
                .refuse(cell, new ReadRefusal(ReadStatus.denied, hostCode(2)));
        TestWorld.View relabelled = new TestWorld.View().put(TestWorld.block(mc("stone_bricks"), LatticePoint.origin))
                .refuse(cell, new ReadRefusal(ReadStatus.denied, hostCode(3)));
        TestWorld.View otherStatus = new TestWorld.View().put(TestWorld.block(mc("stone_bricks"), LatticePoint.origin))
                .refuse(cell, new ReadRefusal(ReadStatus.notCaptured, hostCode(2)));

        SectionKey key = first.sectionOf(LatticePoint.origin);
        SectionSlot.Partial partial = assertInstanceOf(SectionSlot.Partial.class, first.slot(key));
        assertEquals(first.contentDigest(key), relabelled.contentDigest(key));
        assertTrue(first.contentDigest(key) != otherStatus.contentDigest(key));
        assertTrue(first.contentDigest(key) != partial.section().contentDigest());

        TestWorld.View otherCell = new TestWorld.View().put(TestWorld.block(mc("stone_bricks"), LatticePoint.origin))
                .refuse(LatticePoint.of(2, 0, 0), new ReadRefusal(ReadStatus.denied, hostCode(2)));
        assertTrue(first.contentDigest(key) != otherCell.contentDigest(key));
    }

    @Test
    void refusedComputationReportsAndIsMissingInsteadOfShortSum() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 1, 1));
        CuboidSet bayCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class, Retention.instance, Merges.sum(), null);
        Bay bay = bay(bayCells, Coverage.everyCell, Footprint.box());
        Blueprint blueprint = Blueprint.builder(id("partial")).bounds(Cuboid.of(0, 0, 0, 3, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air()))
                .computation(Computations.air(air, cells)).build();
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(mc("stone_bricks"), LatticePoint.of(2, 0, 0)))
                .refuse(LatticePoint.of(1, 0, 0), new ReadRefusal(ReadStatus.failed, hostCode(2)));

        MatchResult.Admitted admitted = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(blueprint, view))
        );
        assertFalse(admitted.outcome().value(air).isPresent());
        assertTrue(admitted.findings().ofCode(hostCode(2)).size() == 1);
        assertTrue(
            admitted.findings().ofCode(hostCode(2)).get(0).notes().stream()
                    .anyMatch(note -> note.message().contains("failed"))
        );
        assertTrue(admitted.findings().ofCode(Codes.computationFailed.code()).isEmpty());
        assertTrue(admitted.findings().ofCode(Codes.foldRefused.code()).isEmpty());
        assertEquals(hostCode(2), admitted.findings().ofCode(hostCode(2)).get(0).code());
    }

    @Test
    void relayedKernelCodeBecomesRequiredReadUnavailable() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 1, 1));
        CuboidSet bayCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class, Retention.instance, Merges.sum(), null);
        Bay bay = bay(bayCells, Coverage.everyCell, Footprint.box());
        Blueprint blueprint = Blueprint.builder(id("kernel_code")).bounds(Cuboid.of(0, 0, 0, 3, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air()))
                .computation(Computations.air(air, cells)).build();
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(mc("stone_bricks"), LatticePoint.of(2, 0, 0)))
                .refuse(LatticePoint.of(1, 0, 0), new ReadRefusal(ReadStatus.denied, Codes.claimConflict.code()));

        MatchResult.Admitted admitted = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(blueprint, view))
        );
        List<Finding> relayed = admitted.findings().ofCode(Codes.unreadableRead.code());
        assertEquals(1, relayed.size());
        assertTrue(relayed.get(0).notes().stream().anyMatch(note -> note.message().contains("kernel code")));
        assertTrue(admitted.findings().ofCode(Codes.claimConflict.code()).isEmpty());
    }

    @Test
    void unreadableFocusRelaysAndAggregatesByStatusAndCode() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 4, 1, 1));
        Bay bay = bay(cells, Coverage.everyCell, Footprint.box());
        Expectation<List<OccupantSnapshot>> expectation = Expectations
                .custom(listClass(), ExpectationMeta.anonymous(), ctx -> Reply.match());
        Blueprint blueprint = Blueprint.builder(id("relay")).bounds(Cuboid.of(0, 0, 0, 4, 1, 1))
                .check(Check.of(id("cells"), bay, Focuses.allCells(), expectation)).build();
        TestWorld.View view = new TestWorld.View()
                .refuse(LatticePoint.of(0, 0, 0), new ReadRefusal(ReadStatus.denied, hostCode(2)))
                .refuse(LatticePoint.of(1, 0, 0), new ReadRefusal(ReadStatus.denied, hostCode(2)))
                .refuse(LatticePoint.of(2, 0, 0), new ReadRefusal(ReadStatus.notCaptured, hostCode(3)));

        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, view))
        );
        List<Finding> first = undecided.findings().ofCode(hostCode(2));
        List<Finding> second = undecided.findings().ofCode(hostCode(3));
        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(2, Objects.requireNonNull(first.get(0).primaryLabel(), "first label").locus().cells().volume());
        assertEquals(1, Objects.requireNonNull(second.get(0).primaryLabel(), "second label").locus().cells().volume());
        assertTrue(first.get(0).notes().stream().anyMatch(note -> note.message().contains("denied")));
    }

    @Test
    void nestedSubjectIsRejectedAtCheckAndPlanner() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.anySubject, Footprint.bound(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("nested")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("nested"), bay, bind(LatticePoint.origin), Expectations.anyOccupant())).build();
        OccupantSnapshot nested = occupant(OccupantKind.nested, 0, mc("nested_structure"));
        TestWorld.View view = new TestWorld.View().put(nested);

        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MatchEngine.check(request(blueprint, view))
        );
        assertEquals(
            Codes.bindingInvalid.code(),
            Objects.requireNonNull(failed.findings().primary(), "primary").code()
        );
        assertTrue(Objects.requireNonNull(failed.findings().primary(), "primary").message().contains("nested"));

        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> ClaimPlanner.derive(blueprint, atOrigin, owner, List.of(new BayBinding(bay, List.of(nested))))
        );
        assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-CLM-0004"));
    }

    @Test
    void nonRootSubjectIsRejectedAtSettle() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.anySubject, Footprint.bound(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("child")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("child"), bay, bind(LatticePoint.origin), Expectations.anyOccupant())).build();
        OccupantSnapshot child = occupant(OccupantKind.block, 1, mc("stone_bricks"));
        TestWorld.View view = new TestWorld.View().put(child);
        MatchRequest request = request(blueprint, view);
        MatchOutcome outcome = new MatchOutcome(
            view,
            Map.of(),
            List.of(new BayBinding(bay, List.of(child))),
            blueprint.reads(),
            Spend.zero
        );
        MatchResult.Admitted forged = new MatchResult.Admitted(
            Phase.check,
            instance,
            atOrigin,
            outcome,
            0,
            true,
            FindingBundle.empty()
        );

        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MatchEngine.settle(request, forged, new TestWorld.Ledger(), ContextStore.inMemory())
        );
        assertEquals(
            Codes.bindingInvalid.code(),
            Objects.requireNonNull(failed.findings().primary(), "primary").code()
        );
    }

    @Test
    void traitExpectationsKeepFourStatesAndNegationUnreadable() {
        NamespacedId energyId = id("energy");
        TraitKey<Long> energy = new TraitKey<>(energyId, Long.class);
        OccupantSnapshot present = traitOccupant(new TraitSlot.Present(Trait.of(energy, 7L)));
        OccupantSnapshot presentNull = traitOccupant(new TraitSlot.Present(Trait.absent(energy)));
        OccupantSnapshot absent = traitOccupant(new TraitSlot.Absent(energyId));
        OccupantSnapshot refused = traitOccupant(
            new TraitSlot.Refused(energyId, new ReadRefusal(ReadStatus.failed, hostCode(2)))
        );
        OccupantSnapshot unasked = traitOccupant();
        OccupantSnapshot wrongType = traitOccupant(
            new TraitSlot.Present(Trait.of(new TraitKey<>(energyId, Object.class), "seven"))
        );

        assertInstanceOf(
            MatchResult.Admitted.class,
            checkTrait(present, Expectations.trait(energy, value -> value > 5))
        );
        assertInstanceOf(MatchResult.Failed.class, checkTrait(present, Expectations.trait(energy, value -> value > 9)));
        assertInstanceOf(MatchResult.Admitted.class, checkTrait(present, Expectations.trait(energy)));
        assertInstanceOf(MatchResult.Failed.class, checkTrait(present, Expectations.traitAbsent(energy)));

        assertInstanceOf(MatchResult.Admitted.class, checkTrait(presentNull, Expectations.trait(energy)));
        assertInstanceOf(
            MatchResult.Admitted.class,
            checkTrait(presentNull, Expectations.trait(energy, value -> false))
        );
        assertInstanceOf(MatchResult.Failed.class, checkTrait(presentNull, Expectations.traitAbsent(energy)));

        MatchResult.Failed absentFailure = assertInstanceOf(
            MatchResult.Failed.class,
            checkTrait(absent, Expectations.trait(energy))
        );
        assertEquals(
            Codes.expected.code(),
            Objects.requireNonNull(absentFailure.findings().primary(), "primary").code()
        );
        assertInstanceOf(MatchResult.Admitted.class, checkTrait(absent, Expectations.traitAbsent(energy)));

        assertUnreadable(checkTrait(refused, Expectations.trait(energy)), hostCode(2));
        assertUnreadable(checkTrait(refused, Expectations.not(Expectations.trait(energy))), hostCode(2));
        assertUnreadable(checkTrait(refused, Expectations.traitAbsent(energy)), hostCode(2));
        assertUnreadable(checkTrait(unasked, Expectations.trait(energy)), Codes.unreadableRead.code());
        assertUnreadable(
            checkTrait(unasked, Expectations.not(Expectations.trait(energy))),
            Codes.unreadableRead.code()
        );
        assertUnreadable(checkTrait(unasked, Expectations.traitAbsent(energy)), Codes.unreadableRead.code());
        assertUnreadable(checkTrait(wrongType, Expectations.trait(energy, value -> true)), Codes.unreadableRead.code());
    }

    private static void assertUnreadable(MatchResult result, FindingCode code) {
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertEquals(1, undecided.findings().ofCode(code).size());
    }

    private static MatchResult checkTrait(OccupantSnapshot occupant, Expectation<OccupantSnapshot> expectation) {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = bay(cells, Coverage.everyCell, Footprint.box());
        Blueprint blueprint = Blueprint.builder(id("trait")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("trait"), bay, Focuses.occupant(LatticePoint.origin), expectation)).build();
        return MatchEngine.check(request(blueprint, new TestWorld.View().put(occupant)));
    }

    private static OccupantSnapshot traitOccupant(TraitSlot... slots) {
        return TestWorld.block(mc("stone_bricks"), LatticePoint.origin, List.of(slots));
    }

    private static OccupantSnapshot occupant(OccupantKind kind, int frame, NamespacedId type) {
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, frame, type, kind, LatticePoint.origin);
        return new OccupantSnapshot(ref, new OccupantDescriptor(type, kind), Cuboid.of(LatticePoint.origin), List.of());
    }

    private static Focus<OccupantSnapshot> bind(LatticePoint cell) {
        return new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot occupant = ctx.occupantAt(cell);
                if (occupant == null || occupant.isAir()) {
                    Finding finding = Finding.builder(Codes.unreadableCell).phase(Phase.check).message("missing")
                            .label(Label.primary(ctx.locus(), "missing")).build();
                    return new FocusResult.Unavailable<>(FindingBundle.of(List.of(finding)));
                }
                return new FocusResult.Taken<>(occupant, List.of(occupant), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public boolean bindsClaimableSubject() {
                return true;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Class<List<OccupantSnapshot>> listClass() {
        return (Class<List<OccupantSnapshot>>) (Class<?>) List.class;
    }
}
