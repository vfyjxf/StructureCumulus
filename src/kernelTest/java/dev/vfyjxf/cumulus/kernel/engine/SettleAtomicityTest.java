package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
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
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-42: the attempt is the only publish target, one compare-and-set per settle, exact compensation. */
class SettleAtomicityTest {

    private static final Siting siting = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);

    @Test
    void settleRejectsAClosedAttemptBeforeWriting() {
        Blueprint blueprint = boxBlueprint("atomicity/closed");
        TestWorld.View view = filledView();
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest request = MagicFixtures.request(blueprint, siting, view, ledger.read(bounds));
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        TestAttempt attempt = TestAttempt.of(request);
        attempt.abort();
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.settle(request, admitted, ledger, attempt)
        );
        assertEquals(UndecidedReason.designDefect, undecided.reason());
        assertEquals(0L, ledger.revision(), "a rejected settle moves no revision");
        assertTrue(ledger.read(bounds).rows().isEmpty(), "a rejected settle writes no rows");
    }

    @Test
    void settleRejectsAForeignAttemptBeforeWriting() {
        Blueprint blueprint = boxBlueprint("atomicity/foreign");
        TestWorld.View view = filledView();
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest request = MagicFixtures
                .request(blueprint, siting, view, ledger.read(bounds), MagicFixtures.owner, MagicFixtures.instance);
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        TestAttempt foreign = TestAttempt.of(withInstance(request, MagicFixtures.rivalInstance));
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.settle(request, admitted, ledger, foreign)
        );
        assertEquals(UndecidedReason.designDefect, undecided.reason());
        assertEquals(0L, ledger.revision(), "a rejected settle moves no revision");
        assertTrue(ledger.read(bounds).rows().isEmpty(), "a rejected settle writes no rows");
    }

    @Test
    void settleRejectsAnAttemptOfAnotherOwnerBeforeWriting() {
        Blueprint blueprint = boxBlueprint("atomicity/owner");
        TestWorld.View view = filledView();
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest request = MagicFixtures.request(blueprint, siting, view, ledger.read(bounds));
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        TestAttempt otherOwner = TestAttempt.of(withOwner(request, MagicFixtures.rival));
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.settle(request, admitted, ledger, otherOwner)
        );
        assertEquals(UndecidedReason.designDefect, undecided.reason());
        assertEquals(0L, ledger.revision(), "a rejected settle moves no revision");
        assertTrue(ledger.read(bounds).rows().isEmpty(), "a rejected settle writes no rows");
    }

    @Test
    void anEmptyPlanReleasesTheInstance() {
        Blueprint box = boxBlueprint("atomicity/box");
        Blueprint none = noneBlueprint("atomicity/none");
        TestWorld.View view = filledView();
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest first = MagicFixtures.request(box, siting, view, ledger.read(bounds));
        MatchResult.Formed firstFormed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                first,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(first)),
                ledger,
                TestAttempt.of(first)
            )
        );
        assertEquals(2, firstFormed.claims().rows().size());
        assertEquals(1L, ledger.revision());

        MatchRequest second = MagicFixtures.request(none, siting, view, ledger.read(bounds));
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                second,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(second)),
                ledger,
                TestAttempt.of(second)
            )
        );
        assertTrue(formed.claims().rows().isEmpty());
        assertTrue(TestWorld.rows(ledger).isEmpty(), "the empty plan releases the instance's key");
        assertEquals(2L, ledger.revision(), "the release of a held key bumps the revision");
        assertEquals(2L, formed.ledgerRevision(), "Formed carries the release revision");
    }

    @Test
    void releaseAfterFormedRemovesExactlyTheSettledRows() {
        Blueprint blueprint = boxBlueprint("atomicity/release");
        Siting rivalSiting = MagicFixtures.identity(LatticePoint.of(4, 0, 0));
        Cuboid rivalBounds = Cuboid.of(4, 0, 0, 6, 1, 1);
        TestWorld.View view = filledView();
        MagicFixtures.fill(view, rivalSiting, CuboidSet.of(bounds), TestWorld.stone);
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest request = MagicFixtures.request(blueprint, siting, view, ledger.read(bounds));
        assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                request,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request)),
                ledger,
                TestAttempt.of(request)
            )
        );
        MatchRequest rivalRequest = MagicFixtures.request(
            blueprint,
            rivalSiting,
            view,
            ledger.read(rivalBounds),
            MagicFixtures.rival,
            MagicFixtures.rivalInstance
        );
        assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                rivalRequest,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(rivalRequest)),
                ledger,
                TestAttempt.of(rivalRequest)
            )
        );
        assertEquals(4, TestWorld.rows(ledger).size(), "both instances hold two rows");

        ledger.release(MagicFixtures.instance);
        assertTrue(ledger.read(bounds).rows().isEmpty(), "release removes exactly the settled rows");
        assertEquals(2, ledger.read(rivalBounds).rows().size(), "the other owner's rows are untouched");
    }

    @Test
    void aConformingHostNeverLeavesRowsWithoutFormed() {
        assertRejectedSettleWritesNothing("closed", request -> {
            TestAttempt attempt = TestAttempt.of(request);
            attempt.abort();
            return attempt;
        });
        assertRejectedSettleWritesNothing(
            "foreign",
            request -> TestAttempt.of(withInstance(request, MagicFixtures.rivalInstance))
        );
        assertRejectedSettleWritesNothing("owner", request -> TestAttempt.of(withOwner(request, MagicFixtures.rival)));
        assertRejectedSettleWritesNothing(
            "structure",
            request -> TestAttempt.of(withBlueprint(request, noneBlueprint("atomicity/other_structure")))
        );
    }

    private static void assertRejectedSettleWritesNothing(String case_, Function<MatchRequest, TestAttempt> target) {
        Blueprint blueprint = boxBlueprint("atomicity/conforming_" + case_);
        TestWorld.View view = filledView();
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest request = MagicFixtures.request(blueprint, siting, view, ledger.read(bounds));
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        long revision = ledger.revision();
        int rows = TestWorld.rows(ledger).size();
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.settle(request, admitted, ledger, target.apply(request))
        );
        assertEquals(UndecidedReason.designDefect, undecided.reason(), case_);
        assertEquals(revision, ledger.revision(), case_ + ": the ledger revision is unmoved");
        assertEquals(rows, TestWorld.rows(ledger).size(), case_ + ": the ledger rows are unmoved");
    }

    private static MatchRequest withInstance(MatchRequest request, InstanceId instance) {
        return MatchRequest.of(
            request.blueprint(),
            request.siting(),
            request.lease(),
            instance,
            request.owner(),
            request.budget(),
            request.claims()
        );
    }

    private static MatchRequest withOwner(MatchRequest request, OwnerId owner) {
        return MatchRequest.of(
            request.blueprint(),
            request.siting(),
            request.lease(),
            request.instance(),
            owner,
            request.budget(),
            request.claims()
        );
    }

    private static MatchRequest withBlueprint(MatchRequest request, Blueprint blueprint) {
        return MatchRequest.of(
            blueprint,
            request.siting(),
            request.lease(),
            request.instance(),
            request.owner(),
            request.budget(),
            request.claims()
        );
    }

    private static TestWorld.View filledView() {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.fill(view, siting, CuboidSet.of(bounds), TestWorld.stone);
        return view;
    }

    private static Blueprint boxBlueprint(String path) {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id(path + "_bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(MagicFixtures.id(path)).bounds(bounds)
                .check(
                    Check.of(
                        MagicFixtures.id(path + "_check"),
                        bay,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.anyOccupant())
                    )
                ).build();
    }

    private static Blueprint noneBlueprint(String path) {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id(path + "_bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        return Blueprint.builder(MagicFixtures.id(path)).bounds(bounds)
                .check(
                    Check.of(
                        MagicFixtures.id(path + "_check"),
                        bay,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.anyOccupant())
                    )
                ).build();
    }
}
