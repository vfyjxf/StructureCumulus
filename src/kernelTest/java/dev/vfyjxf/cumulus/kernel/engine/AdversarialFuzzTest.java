package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.AggregateComputation;
import dev.vfyjxf.cumulus.kernel.structure.AggregateContext;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.FocusResult;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seeded adversarial fuzz: structure-manager event scripts, context publication, claims/coverage rows,
 * build rules, expectation classification and budget bounds. Every case is deterministic from a fixed
 * seed; failures carry the seed and step that produced them.
 */
class AdversarialFuzzTest {

    private static final long[] seeds = {1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L};
    private static final OwnerId owner = OwnerId.of(id("owner"));
    private static final OwnerId rivalOwner = OwnerId.of(id("rival"));
    private static final World world = TestWorld.world;
    private static final World otherWorld = World.of(id("other_world"));
    private static final NamespacedId blueprintId = id("blueprint");
    private static final NamespacedId bayId = id("bay");
    private static final NamespacedId rivalBayId = id("rival_bay");
    private static final ContextKey<Long> countA = ContextKeys.of(id("count_a"), Long.class, Retention.instance);
    private static final ContextKey<Long> countB = ContextKeys.of(id("count_b"), Long.class, Retention.instance);
    private static final ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);
    private static final ContextKey<Long> matchKey = ContextKeys.of(id("match"), Long.class, Retention.match);
    private static final Siting[] sitings = {Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity),
            Siting.of(LatticePoint.of(4, 0, 0), Orientation.identity),
            Siting.of(LatticePoint.of(8, 0, 0), Orientation.identity),
            Siting.of(LatticePoint.of(0, 0, 4), Orientation.identity)};
    private static final Orientation[] orientations = {Orientation.identity,
            Orientation.of(Turn.clockwise, Tilt.level, Flip.none), Orientation.of(Turn.half, Tilt.level, Flip.none),
            Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none)};

    @Test
    void managerScriptsPreserveTheRegistryLaws() {
        for (long seed : seeds) {
            new ManagerFuzz(seed).run();
        }
    }

    @Test
    void loadedClosesEveryOpenDraftWithoutLeakingIt() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = manager.begin(world, new StructureSpec(owner, sitings[0], blueprintId));
        InstanceId restored = new InstanceId(world, 77L);
        StructureInstance instance = new StructureInstance(
            restored,
            new StructureSpec(owner, sitings[1], blueprintId),
            StructureContext.empty(restored),
            boundsAt(sitings[1], 1)
        );
        manager.onEvent(new StructureEvent.Loaded(world, List.of(instance)));

        assertAll(
            "Loaded must close every open draft: a held attempt is one-shot and must not leak past a registry replace",
            () -> assertThrows(
                IllegalStateException.class,
                attempt::abort,
                "observed: abort() returned normally after Loaded; expected IllegalStateException because the draft was discarded"
            ),
            () -> assertThrows(
                IllegalStateException.class,
                () -> attempt.commit(boundsAt(sitings[2], 1)),
                "observed: commit() succeeded after Loaded; expected IllegalStateException (draft leak)"
            )
        );
    }

    @Test
    void contextPublicationSequencesStayMonotonicAndIsolated() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            ContextStore store = ContextStore.inMemory();
            InstanceId first = new InstanceId(world, 1L);
            InstanceId second = new InstanceId(otherWorld, 1L);
            Map<InstanceId, StructureContext> model = new LinkedHashMap<>();
            model.put(first, StructureContext.empty(first));
            model.put(second, StructureContext.empty(second));
            store.context(first);
            store.context(second);
            for (int step = 0; step < 80; step++) {
                String where = "seed=" + seed + " step=" + step;
                InstanceId target = random.nextBoolean() ? first : second;
                int op = random.nextInt(10);
                if (op < 6) {
                    Map<ContextKey<?>, Object> values = randomValues(random);
                    Spend spend = new Spend(random.nextInt(4), random.nextInt(3));
                    FindingBundle findings = randomFindings(random, where);
                    StructureContext expected = merge(
                        Objects.requireNonNull(model.get(target)),
                        values,
                        findings,
                        spend
                    );
                    StructureContext actual = store.publish(target, values, findings, spend);
                    assertEquals(expected, actual, where + " publish result");
                    assertEquals(expected, store.context(target), where + " stored context");
                    InstanceId other = target.equals(first) ? second : first;
                    assertEquals(model.get(other), store.context(other), where + " the other instance must not change");
                    model.put(target, expected);
                } else if (op < 8) {
                    StructureContext before = model.get(target);
                    StructureContext removed = store.remove(target);
                    assertEquals(before, removed, where + " first remove returns the stored context");
                    assertNull(store.remove(target), where + " second remove answers null");
                    StructureContext empty = StructureContext.empty(target);
                    assertEquals(empty, store.context(target), where + " the removed instance reads empty");
                    model.put(target, empty);
                } else {
                    StructureContext before = model.get(target);
                    assertThrows(
                        IllegalArgumentException.class,
                        () -> store.publish(target, Map.of(matchKey, 1L), FindingBundle.empty(), Spend.zero),
                        where + " a match key is never published"
                    );
                    assertEquals(before, store.context(target), where + " a rejected publish writes nothing");
                }
            }
        }
    }

    @Test
    void randomClaimsAndCoverageKeepTheRowLaws() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 25; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
                List<LatticePoint> localCells = pointsIn(bounds);
                CuboidSet bayCells = randomCells(random, localCells, 1 + random.nextInt(4));
                int footprintPick = random.nextInt(3);
                Footprint footprint = footprintPick == 0
                        ? Footprint.box()
                        : footprintPick == 1
                                ? Footprint.declared(randomCells(random, localCells, 1 + random.nextInt(4)))
                                : Footprint.none();
                Sharing sharing = footprint instanceof Footprint.None || random.nextBoolean()
                        ? Sharing.shared
                        : Sharing.exclusive;
                Bay bay = Bay.of(bayId, bayCells, Coverage.everyCell, footprint, sharing);
                Blueprint blueprint = Blueprint.builder(blueprintId).bounds(bounds)
                        .check(
                            Check.of(id("check"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()))
                        ).build();
                Siting siting = Siting
                        .of(LatticePoint.of(10, 0, 10), orientations[random.nextInt(orientations.length)]);
                TestWorld.View view = MagicFixtures.view();
                for (LatticePoint local : localCells) {
                    LatticePoint worldCell = Objects.requireNonNull(siting.toWorld(local), where + " mapped cell");
                    if (random.nextBoolean()) {
                        view.put(TestWorld.block(TestWorld.stone, worldCell));
                    } else {
                        view.air(worldCell);
                    }
                }
                boolean refused = random.nextInt(7) == 0;
                if (refused) {
                    LatticePoint local = new ArrayList<>(pointsOf(bayCells))
                            .get(random.nextInt((int) bayCells.volume()));
                    view.refuse(Objects.requireNonNull(siting.toWorld(local), where + " refused cell"));
                }
                ClaimLedger ledger = MagicFixtures.ledger();
                MatchRequest request = MatchRequest.of(
                    blueprint,
                    siting,
                    new TestWorld.Lease(view),
                    MagicFixtures.instance,
                    MagicFixtures.owner,
                    Budget.unbounded(),
                    new ClaimView(List.of(), 0, true)
                );
                MatchResult checked = MatchEngine.check(request);
                if (refused) {
                    assertInstanceOf(MatchResult.Undecided.class, checked, where + " a refused bay cell is unreadable");
                    assertEquals(0L, ledger.revision(), where + " a failed check writes no claim");
                    assertTrue(TestWorld.rows(ledger).isEmpty(), where + " a failed check leaves the ledger empty");
                    continue;
                }
                MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, checked, where);
                TestAttempt attempt = TestAttempt.of(request);
                MatchResult.Formed formed = assertInstanceOf(
                    MatchResult.Formed.class,
                    MatchEngine.settle(request, admitted, ledger, attempt),
                    where
                );
                List<ClaimRow> expected = expectedRows(blueprint, siting, MagicFixtures.instance);
                List<ClaimRow> own = ownRows(formed, blueprint, MagicFixtures.instance);
                assertEquals(expected, own, where + " rows for this structure");
                assertRowLaws(own, where);
                assertEquals(expected, sorted(TestWorld.rows(ledger)), where + " ledger rows");
                MatchResult.Formed again = assertInstanceOf(
                    MatchResult.Formed.class,
                    MatchEngine.settle(request, admitted, ledger, attempt),
                    where + " a re-settle replaces the same key"
                );
                List<ClaimRow> ownAgain = ownRows(again, blueprint, MagicFixtures.instance);
                assertEquals(expected, ownAgain, where + " re-settle row set");
                assertEquals(expected, sorted(TestWorld.rows(ledger)), where + " no duplicate rows after a re-settle");
                assertEquals(2L, ledger.revision(), where + " revision count");
                assertRowLaws(ownAgain, where);
            }
        }
    }

    @Test
    void exclusiveClaimsConflictAndSharedClaimsCoexistWithRivals() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 12; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
                Siting siting = sitings[random.nextInt(sitings.length)];
                LatticePoint worldCell = Objects.requireNonNull(siting.toWorld(LatticePoint.origin), where);
                Sharing ours = random.nextBoolean() ? Sharing.exclusive : Sharing.shared;
                Sharing rival = random.nextBoolean() ? Sharing.exclusive : Sharing.shared;
                Blueprint blueprint = occupantBlueprint(blueprintId, bayId, bounds, ours);
                Blueprint rivalBlueprint = occupantBlueprint(id("rival"), rivalBayId, bounds, rival);
                TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, worldCell));
                ClaimLedger ledger = MagicFixtures.ledger();
                MatchRequest rivalRequest = MatchRequest.of(
                    rivalBlueprint,
                    siting,
                    new TestWorld.Lease(view),
                    MagicFixtures.rivalInstance,
                    rivalOwner,
                    Budget.unbounded(),
                    new ClaimView(List.of(), 0, true)
                );
                MatchResult.Admitted rivalAdmitted = assertInstanceOf(
                    MatchResult.Admitted.class,
                    MatchEngine.check(rivalRequest),
                    where
                );
                assertInstanceOf(
                    MatchResult.Formed.class,
                    MatchEngine.settle(rivalRequest, rivalAdmitted, ledger, TestAttempt.of(rivalRequest))
                );
                ClaimView frozen = ledger.read(Objects.requireNonNull(siting.toWorld(bounds), where + " world bounds"));
                Siting oursSiting = Siting.of(worldCell, Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
                MatchRequest request = MatchRequest.of(
                    blueprint,
                    oursSiting,
                    new TestWorld.Lease(view),
                    MagicFixtures.instance,
                    MagicFixtures.owner,
                    Budget.unbounded(),
                    frozen
                );
                MatchResult checked = MatchEngine.check(request);
                boolean expectedConflict = ours == Sharing.exclusive || rival == Sharing.exclusive;
                if (expectedConflict) {
                    MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, checked, where);
                    assertTrue(
                        MagicFixtures.hasCode(failed, Codes.claimConflict),
                        where + " an exclusive side conflicts"
                    );
                    assertEquals(1L, ledger.revision(), where + " a conflict prediction writes nothing");
                } else {
                    MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, checked, where);
                    MatchResult.Formed formed = assertInstanceOf(
                        MatchResult.Formed.class,
                        MatchEngine.settle(request, admitted, ledger, TestAttempt.of(request)),
                        where
                    );
                    assertEquals(1, ownRows(formed, blueprint, MagicFixtures.instance).size(), where);
                    assertEquals(2, TestWorld.rows(ledger).size(), where + " shared/shared rows coexist");
                }
            }
        }
    }

    @Test
    void buildRulesRejectOverlapsUnclaimableSubjectsAndUndecidedCoverage() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 15; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
                List<LatticePoint> cells = pointsIn(bounds);
                CuboidSet firstCell = randomCells(random, cells, 1 + random.nextInt(3));
                LatticePoint sharedCell = new ArrayList<>(pointsOf(firstCell)).get(0);
                List<LatticePoint> secondPool = new ArrayList<>(cells);
                secondPool.remove(sharedCell);
                CuboidSet secondCells = randomCells(random, secondPool, random.nextInt(3))
                        .union(CuboidSet.of(Cuboid.of(sharedCell)));
                Sharing firstSharing = random.nextBoolean() ? Sharing.exclusive : Sharing.shared;
                Sharing secondSharing = random.nextBoolean() ? Sharing.exclusive : Sharing.shared;
                Bay firstBay = Bay.of(id("first_bay"), firstCell, Coverage.everyCell, Footprint.box(), firstSharing);
                Bay secondBay = Bay
                        .of(id("second_bay"), secondCells, Coverage.everyCell, Footprint.box(), secondSharing);
                BlueprintBuilder overlap = Blueprint.builder(id("overlap")).bounds(bounds)
                        .check(
                            Check.of(
                                id("first_check"),
                                firstBay,
                                Focuses.bay(),
                                Expectations.eachCell(Expectations.anyOccupant())
                            )
                        )
                        .check(
                            Check.of(
                                id("second_check"),
                                secondBay,
                                Focuses.bay(),
                                Expectations.eachCell(Expectations.anyOccupant())
                            )
                        );
                boolean expectedOverlapFailure = firstSharing == Sharing.exclusive
                        || secondSharing == Sharing.exclusive;
                if (expectedOverlapFailure) {
                    assertBuildFailure("SC-BPT-0002", overlap::build, where + " exclusive overlap");
                } else {
                    Blueprint blueprint = overlap.build();
                    TestWorld.View view = MagicFixtures.view();
                    ClaimLedger ledger = MagicFixtures.ledger();
                    MatchRequest request = MatchRequest.of(
                        blueprint,
                        sitings[0],
                        new TestWorld.Lease(view),
                        MagicFixtures.instance,
                        MagicFixtures.owner,
                        Budget.unbounded(),
                        new ClaimView(List.of(), 0, true)
                    );
                    MatchResult.Admitted admitted = assertInstanceOf(
                        MatchResult.Admitted.class,
                        MatchEngine.check(request),
                        where
                    );
                    MatchResult.Formed formed = assertInstanceOf(
                        MatchResult.Formed.class,
                        MatchEngine.settle(request, admitted, ledger, TestAttempt.of(request)),
                        where
                    );
                    List<ClaimRow> own = ownRows(formed, blueprint, MagicFixtures.instance);
                    assertRowLaws(own, where + " shared overlap");
                    assertEquals(
                        expectedRows(blueprint, sitings[0], MagicFixtures.instance),
                        own,
                        where + " shared rows"
                    );
                    assertTrue(
                        own.stream().anyMatch(row -> row.cell().equals(LatticeKey.of(sharedCell))),
                        where + " shared cell row"
                    );
                }

                boolean binds = random.nextBoolean();
                boolean claimable = random.nextBoolean();
                Footprint footprint = random.nextBoolean() ? Footprint.box() : Footprint.none();
                Sharing sharing = footprint instanceof Footprint.None ? Sharing.shared : Sharing.exclusive;
                Focus<OccupantSnapshot> custom = new DeclaredFocus(binds, claimable);
                Bay claimBay = Bay.of(
                    id("claim_bay"),
                    CuboidSet.of(Cuboid.of(LatticePoint.origin)),
                    Coverage.anySubject,
                    footprint,
                    sharing
                );
                BlueprintBuilder claimability = Blueprint.builder(id("claimability"))
                        .bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                        .check(Check.of(id("claim_check"), claimBay, custom, Expectations.anyOccupant()));
                boolean expectedClaimFailure = !(footprint instanceof Footprint.None) && binds && !claimable;
                if (expectedClaimFailure) {
                    assertBuildFailure("SC-BPT-0005", claimability::build, where + " unclaimable subject");
                } else {
                    assertDoesNotThrow(claimability::build, where + " claimability");
                }

                CuboidSet covered = randomCells(random, cells, 1 + random.nextInt(3))
                        .union(CuboidSet.of(Cuboid.of(LatticePoint.origin)));
                Bay coverageBay = Bay
                        .of(id("coverage_bay"), covered, Coverage.everyCell, Footprint.none(), Sharing.shared);
                BlueprintBuilder coverage = Blueprint.builder(id("coverage")).bounds(bounds).check(
                    Check.of(
                        id("coverage_check"),
                        coverageBay,
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.anyOccupant()
                    )
                );
                if (pointsOf(covered).size() > 1) {
                    assertBuildFailure("SC-BPT-0005", coverage::build, where + " undecided coverage");
                } else {
                    assertDoesNotThrow(coverage::build, where + " one covered cell");
                }

                Bay oneSubject = Bay.of(
                    id("one_bay"),
                    CuboidSet.of(Cuboid.of(LatticePoint.origin)),
                    Coverage.oneSubject,
                    Footprint.none(),
                    Sharing.shared
                );
                BlueprintBuilder noSubject = Blueprint.builder(id("one_subject")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                        .check(Check.of(id("one_check"), oneSubject, contextFocus(), customLongExpectation()));
                assertBuildFailure("SC-BPT-0005", noSubject::build, where + " oneSubject without a subject");

                Bay anySubject = Bay.of(
                    id("any_bay"),
                    CuboidSet.of(Cuboid.of(LatticePoint.origin)),
                    Coverage.anySubject,
                    Footprint.none(),
                    Sharing.shared
                );
                BlueprintBuilder perCell = Blueprint.builder(id("any_subject")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                        .check(
                            Check.of(
                                id("any_check"),
                                anySubject,
                                Focuses.occupant(LatticePoint.origin),
                                Expectations.anyOccupant()
                            )
                        );
                assertBuildFailure("SC-BPT-0005", perCell::build, where + " anySubject with per-cell checks");

                Bay ownsNothing = Bay.of(
                    id("nothing_bay"),
                    CuboidSet.of(Cuboid.of(LatticePoint.origin)),
                    Coverage.anySubject,
                    Footprint.none(),
                    Sharing.exclusive
                );
                BlueprintBuilder exclusiveNone = Blueprint.builder(id("exclusive_none"))
                        .bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                        .check(Check.of(id("nothing_check"), ownsNothing, contextFocus(), customLongExpectation()));
                assertBuildFailure("SC-BPT-0005", exclusiveNone::build, where + " exclusive bay owning nothing");
            }
        }
    }

    @Test
    void emptyNonFluidAndNotAirClassifyOccupantsExactly() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 18; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                OccupantSnapshot occupant = randomOccupant(random);
                boolean air = occupant.isAir();
                boolean fluid = occupant.descriptor().type().equals(MagicFixtures.mc("water"))
                        || occupant.descriptor().type().equals(MagicFixtures.mc("lava"));
                List<Map.Entry<Expectation<OccupantSnapshot>, Boolean>> expectations = List.of(
                    Map.entry(Expectations.empty(), air || fluid),
                    Map.entry(Expectations.nonFluid(), !fluid),
                    Map.entry(Expectations.not(Expectations.air()), !air)
                );
                for (Map.Entry<Expectation<OccupantSnapshot>, Boolean> entry : expectations) {
                    TestWorld.View view = MagicFixtures.view().put(occupant);
                    Blueprint blueprint = occupantBlueprint(
                        id("classify"),
                        id("classify_bay"),
                        Cuboid.of(0, 0, 0, 1, 1, 1),
                        Sharing.exclusive,
                        entry.getKey()
                    );
                    MatchRequest request = MatchRequest.of(
                        blueprint,
                        sitings[0],
                        new TestWorld.Lease(view),
                        MagicFixtures.instance,
                        MagicFixtures.owner,
                        Budget.unbounded(),
                        new ClaimView(List.of(), 0, true)
                    );
                    MatchResult result = MatchEngine.check(request);
                    String detail = where + " occupant=" + occupant.descriptor().type() + " expectation="
                            + entry.getKey().meta().id();
                    if (entry.getValue()) {
                        assertInstanceOf(MatchResult.Admitted.class, result, detail);
                    } else {
                        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, detail);
                        assertTrue(failed.findings().findings().size() > 0, detail + " a mismatch carries a finding");
                    }
                }
            }
        }
    }

    @Test
    void budgetsBoundAndDeterministicallyGateAttempts() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 20; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                boolean aggregate = random.nextBoolean();
                Budget budget = new Budget(random.nextInt(5), random.nextInt(4));
                boolean[] aggregateRan = {false};
                Blueprint blueprint = aggregate ? aggregateBlueprint(aggregateRan) : plainBlueprint();
                TestWorld.View view = MagicFixtures.view();
                MatchRequest request = MatchRequest.of(
                    blueprint,
                    sitings[0],
                    new TestWorld.Lease(view),
                    MagicFixtures.instance,
                    MagicFixtures.owner,
                    budget,
                    new ClaimView(List.of(), 0, true)
                );
                MatchResult first = MatchEngine.check(request);
                MatchResult second = MatchEngine.check(request);
                assertSameResult(first, second, where + " the same tuple repeats");
                boolean expectedAdmitted = aggregate
                        ? budget.cells() >= 2 && budget.sections() >= 1
                        : budget.cells() >= 1 && budget.sections() >= 1;
                if (expectedAdmitted) {
                    MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, first, where);
                    MatchOutcome outcome = admitted.outcome();
                    assertTrue(outcome.spend().cells() <= budget.cells(), where + " cells stay inside the budget");
                    assertTrue(
                        outcome.spend().sections() <= budget.sections(),
                        where + " sections stay inside the budget"
                    );
                    if (aggregate) {
                        assertTrue(aggregateRan[0], where + " the admitted aggregate ran");
                    }
                } else {
                    MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, first, where);
                    assertEquals(UndecidedReason.budgetExhausted, undecided.reason(), where);
                    MatchOutcome partial = Objects.requireNonNull(undecided.partial(), where + " partial outcome");
                    assertTrue(
                        partial.spend().cells() <= budget.cells(),
                        where + " partial spend stays inside the budget"
                    );
                    assertTrue(partial.spend().sections() <= budget.sections(), where);
                    assertFalse(
                        aggregate && aggregateRan[0],
                        where + " the aggregate must not run when its charge is refused"
                    );
                }
            }
        }
    }

    // ------------------------------------------------------------------ manager script

    private static final class ManagerFuzz {

        private final long seed;
        private final Random random;
        private final StructureManager manager = StructureManager.create();
        private final Map<String, InstanceId> drafts = new LinkedHashMap<>();
        private final Map<InstanceId, StructureAttempt> attempts = new LinkedHashMap<>();
        private final List<InstanceId> known = new ArrayList<>();
        private long watermark;

        private ManagerFuzz(long seed) {
            this.seed = seed;
            this.random = new Random(seed);
        }

        private void run() {
            for (int step = 0; step < 90; step++) {
                int op = random.nextInt(11);
                switch (op) {
                    case 0, 1 -> begin();
                    case 2 -> commit();
                    case 3 -> abort();
                    case 4 -> publishDraft();
                    case 5 -> publishFormed();
                    case 6 -> removeEvent();
                    case 7 -> unload();
                    case 8 -> loaded();
                    case 9 -> removeContext();
                    default -> reuseClosed();
                }
                assertRegistry(step);
            }
        }

        private void begin() {
            Siting siting = sitings[random.nextInt(sitings.length)];
            OwnerId specOwner = random.nextBoolean() ? owner : rivalOwner;
            World targetWorld = random.nextBoolean() ? world : otherWorld;
            StructureSpec spec = new StructureSpec(specOwner, siting, blueprintId);
            String slotKey = key(targetWorld, spec);
            String where = where();
            if (drafts.containsKey(slotKey)) {
                assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.begin(targetWorld, spec),
                    where + " duplicate draft siting"
                );
                return;
            }
            StructureAttempt attempt = manager.begin(targetWorld, spec);
            assertEquals(targetWorld, attempt.id().world(), where + " the instance belongs to the requested world");
            assertTrue(attempt.id().serial() > watermark, where + " serial monotonic");
            watermark = attempt.id().serial();
            drafts.put(slotKey, attempt.id());
            attempts.put(attempt.id(), attempt);
            known.add(attempt.id());
        }

        private void commit() {
            if (drafts.isEmpty()) {
                return;
            }
            Map.Entry<String, InstanceId> entry = randomDraft();
            StructureAttempt attempt = Objects.requireNonNull(attempts.get(entry.getValue()));
            CuboidSet bounds = boundsAt(attempt.spec().siting(), random.nextInt(3));
            StructureInstance instance = attempt.commit(bounds);
            drafts.remove(entry.getKey());
            assertEquals(entry.getValue(), instance.id(), where() + " commit keeps the id");
            assertEquals(bounds, instance.bounds(), where() + " commit stores the bounds");
            assertEquals(instance, manager.find(instance.id()), where() + " the committed instance is findable");
            assertNotNull(
                manager.find(instance.id().world(), instance.owner(), instance.siting()),
                where() + " the siting is registered"
            );
            assertThrows(IllegalStateException.class, attempt::abort, where() + " commit is one-shot");
        }

        private void abort() {
            if (drafts.isEmpty()) {
                return;
            }
            Map.Entry<String, InstanceId> entry = randomDraft();
            StructureAttempt attempt = Objects.requireNonNull(attempts.get(entry.getValue()));
            StructureSpec spec = attempt.spec();
            attempt.abort();
            drafts.remove(entry.getKey());
            assertNull(manager.find(entry.getValue()), where() + " an aborted draft leaves no instance");
            assertEquals(
                0L,
                manager.context(entry.getValue()).revision(),
                where() + " an aborted draft leaves no context"
            );
            assertThrows(IllegalStateException.class, attempt::abort, where() + " abort is one-shot");
            assertThrows(
                IllegalStateException.class,
                () -> attempt.commit(boundsAt(spec.siting(), 1)),
                where() + " commit after abort"
            );
            StructureAttempt replacement = manager.begin(attempt.id().world(), spec);
            assertTrue(replacement.id().serial() > watermark, where() + " the released siting allocates a new serial");
            watermark = replacement.id().serial();
            replacement.abort();
        }

        private void publishDraft() {
            if (drafts.isEmpty()) {
                return;
            }
            Map.Entry<String, InstanceId> entry = randomDraft();
            StructureAttempt attempt = Objects.requireNonNull(attempts.get(entry.getValue()));
            StructureContext before = manager.context(entry.getValue());
            Map<ContextKey<?>, Object> values = randomValues(random);
            Spend spend = new Spend(random.nextInt(3), random.nextInt(2));
            StructureContext after = attempt.publish(entry.getValue(), values, FindingBundle.empty(), spend);
            assertPublished(before, after, values, spend);
            assertEquals(
                after,
                manager.context(entry.getValue()),
                where() + " the draft is visible through the manager"
            );
            InstanceId other = new InstanceId(world, watermark + 500);
            assertThrows(
                IllegalArgumentException.class,
                () -> attempt.publish(other, values, FindingBundle.empty(), Spend.zero),
                where() + " a draft publishes only its own instance"
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> attempt.publish(entry.getValue(), Map.of(matchKey, 1L), FindingBundle.empty(), Spend.zero),
                where() + " a match key is never published"
            );
        }

        private void publishFormed() {
            if (manager.instances().isEmpty()) {
                return;
            }
            StructureInstance instance = randomElement(manager.instances());
            StructureContext before = instance.context();
            Map<ContextKey<?>, Object> values = randomValues(random);
            Spend spend = new Spend(random.nextInt(3), random.nextInt(2));
            StructureContext after = manager.publish(instance.id(), values, FindingBundle.empty(), spend);
            assertPublished(before, after, values, spend);
            StructureInstance updated = Objects.requireNonNull(manager.find(instance.id()), where());
            assertEquals(after, updated.context(), where() + " a formed publish replaces the whole context");
            assertEquals(instance.bounds(), updated.bounds(), where() + " a formed publish keeps the bounds");
        }

        private void removeEvent() {
            List<InstanceId> pool = new ArrayList<>(known);
            pool.add(new InstanceId(world, 10_000L + random.nextInt(100)));
            InstanceId id = randomElement(pool);
            boolean formed = manager.find(id) != null;
            boolean draft = drafts.containsValue(id);
            manager.onEvent(new StructureEvent.Broken(id));
            manager.onEvent(new StructureEvent.Invalidated(id, "again"));
            assertNull(manager.find(id), where() + " Broken removes the instance and is idempotent");
            assertEquals(0L, manager.context(id).revision(), where() + " a removed instance reads empty");
            if (draft) {
                drafts.entrySet().removeIf(entry -> entry.getValue().equals(id));
                StructureAttempt attempt = Objects.requireNonNull(attempts.get(id));
                assertThrows(IllegalStateException.class, attempt::abort, where() + " Broken closes the draft");
            }
            if (formed) {
                assertTrue(known.contains(id), where());
            }
        }

        private void unload() {
            World target = random.nextBoolean() ? world : otherWorld;
            List<StructureInstance> otherBefore = new ArrayList<>();
            for (StructureInstance instance : manager.instances()) {
                if (!instance.id().world().equals(target)) {
                    otherBefore.add(instance);
                }
            }
            List<InstanceId> unloadedDrafts = new ArrayList<>();
            for (InstanceId id : drafts.values()) {
                if (id.world().equals(target)) {
                    unloadedDrafts.add(id);
                }
            }
            manager.onEvent(new StructureEvent.Unloaded(target));
            List<StructureInstance> otherAfter = new ArrayList<>();
            for (StructureInstance instance : manager.instances()) {
                if (!instance.id().world().equals(target)) {
                    otherAfter.add(instance);
                }
            }
            assertEquals(otherBefore, otherAfter, where() + " Unloaded removes only one world");
            for (InstanceId id : unloadedDrafts) {
                drafts.entrySet().removeIf(entry -> entry.getValue().equals(id));
                StructureAttempt attempt = Objects.requireNonNull(attempts.get(id));
                assertThrows(IllegalStateException.class, attempt::abort, where() + " Unloaded closes the draft");
            }
        }

        private void loaded() {
            if (!drafts.isEmpty()) {
                return;
            }
            World targetWorld = random.nextBoolean() ? world : otherWorld;
            List<StructureInstance> preserved = new ArrayList<>();
            List<StructureInstance> restored = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            long maxSerial = watermark;
            for (StructureInstance instance : manager.instances()) {
                if (!instance.id().world().equals(targetWorld)) {
                    preserved.add(instance);
                } else if (random.nextBoolean()) {
                    restored.add(instance);
                    keys.add(key(targetWorld, instance.spec()));
                    maxSerial = Math.max(maxSerial, instance.id().serial());
                }
            }
            int extra = random.nextInt(3);
            long serial = maxSerial;
            for (int index = 0; index < extra; index++) {
                Siting siting = null;
                for (int attempt = 0; attempt < sitings.length; attempt++) {
                    Siting candidate = sitings[(index + attempt) % sitings.length];
                    if (!keys.contains(key(targetWorld, new StructureSpec(owner, candidate, blueprintId)))) {
                        siting = candidate;
                        break;
                    }
                }
                if (siting == null) {
                    break;
                }
                serial++;
                InstanceId id = new InstanceId(targetWorld, serial);
                StructureSpec spec = new StructureSpec(owner, siting, blueprintId);
                StructureContext context = StructureContext.empty(id);
                restored.add(new StructureInstance(id, spec, context, boundsAt(siting, 1)));
                keys.add(key(targetWorld, spec));
            }
            restored.sort(Comparator.comparing(StructureInstance::id));
            manager.onEvent(new StructureEvent.Loaded(targetWorld, restored));
            List<StructureInstance> expected = new ArrayList<>(preserved);
            expected.addAll(restored);
            expected.sort(Comparator.comparing(StructureInstance::id));
            assertEquals(expected, manager.instances(), where() + " Loaded replaces only its world");
            for (StructureInstance instance : restored) {
                assertEquals(instance, manager.find(instance.id()), where() + " a loaded instance is findable");
                assertEquals(
                    instance,
                    manager.find(targetWorld, instance.owner(), instance.siting()),
                    where() + " a loaded siting is registered"
                );
            }
            watermark = Math.max(watermark, maxSerial);
            Siting free = sitings[random.nextInt(sitings.length)];
            StructureAttempt probe = manager.begin(targetWorld, new StructureSpec(owner, free, blueprintId));
            assertTrue(probe.id().serial() > watermark, where() + " Loaded raises the serial watermark");
            watermark = probe.id().serial();
            probe.abort();
        }

        private void removeContext() {
            if (!manager.instances().isEmpty() && random.nextBoolean()) {
                StructureInstance formed = randomElement(manager.instances());
                StructureContext removed = manager.remove(formed.id());
                assertEquals(formed.context(), removed, where() + " remove returns the stored context");
                assertNull(manager.find(formed.id()), where() + " remove drops the instance");
                assertEquals(
                    0L,
                    manager.context(formed.id()).revision(),
                    where() + " the removed instance reads empty"
                );
                assertNull(manager.remove(formed.id()), where() + " a second remove answers null");
                assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.publish(formed.id(), Map.of(countA, 1L), FindingBundle.empty(), Spend.zero),
                    where() + " a removed formed instance cannot be published to"
                );
                return;
            }
            InstanceId unknown = new InstanceId(otherWorld, 20_000L + random.nextInt(100));
            assertNull(manager.remove(unknown), where() + " removing an absent instance answers null");
            assertEquals(0L, manager.context(unknown).revision(), where() + " an absent instance reads empty");
        }

        private void reuseClosed() {
            List<InstanceId> closed = new ArrayList<>();
            for (InstanceId id : attempts.keySet()) {
                if (!drafts.containsValue(id)) {
                    closed.add(id);
                }
            }
            if (closed.isEmpty()) {
                return;
            }
            InstanceId id = randomElement(closed);
            StructureAttempt attempt = Objects.requireNonNull(attempts.get(id));
            assertThrows(IllegalStateException.class, attempt::abort, where() + " a closed attempt is one-shot");
            assertThrows(
                IllegalStateException.class,
                () -> attempt.commit(CuboidSet.of(Cuboid.of(LatticePoint.origin))),
                where() + " a committed or aborted attempt never commits again"
            );
        }

        private void assertRegistry(int step) {
            String where = where() + " step=" + step;
            List<StructureInstance> instances = manager.instances();
            Set<InstanceId> ids = new HashSet<>();
            Set<String> keys = new HashSet<>();
            InstanceId previous = null;
            for (StructureInstance instance : instances) {
                assertTrue(ids.add(instance.id()), where + " duplicate instance id " + instance.id());
                assertTrue(
                    keys.add(key(instance.id().world(), instance.spec())),
                    where + " two instances share (owner, siting)"
                );
                assertEquals(instance.id(), instance.context().instance(), where + " context identity");
                assertEquals(instance, manager.find(instance.id()), where + " find(id)");
                assertEquals(
                    instance,
                    manager.find(instance.id().world(), instance.owner(), instance.siting()),
                    where + " find(world, owner, siting)"
                );
                for (Cuboid box : instance.bounds().boxes()) {
                    assertTrue(manager.findAt(box.min()).contains(instance), where + " findAt covers the bounds");
                }
                assertTrue(manager.findIn(instance.bounds()).contains(instance), where + " findIn covers the bounds");
                if (previous != null) {
                    assertTrue(previous.compareTo(instance.id()) < 0, where + " instances() is sorted by id");
                }
                previous = instance.id();
            }
            for (InstanceId id : known) {
                if (manager.find(id) == null && !drafts.containsValue(id)) {
                    assertEquals(0L, manager.context(id).revision(), where + " a removed instance reads empty");
                }
            }
        }

        private String where() {
            return "seed=" + seed;
        }

        private static String key(World world, StructureSpec spec) {
            return world.id().text() + "@" + spec.owner().id().text() + "@" + spec.siting();
        }

        private Map.Entry<String, InstanceId> randomDraft() {
            List<Map.Entry<String, InstanceId>> entries = new ArrayList<>(drafts.entrySet());
            return entries.get(random.nextInt(entries.size()));
        }

        private <T> T randomElement(List<T> values) {
            return values.get(random.nextInt(values.size()));
        }
    }

    // ------------------------------------------------------------------ builders and helpers

    private static void assertBuildFailure(String code, Executable build, String where) {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, build, where);
        String message = Objects.requireNonNull(rejected.getMessage(), where);
        assertTrue(message.contains(code), where + " expected " + code + ", observed: " + message);
    }

    private static Focus<Long> contextFocus() {
        return Focuses.context(countA);
    }

    private static Expectation<Long> customLongExpectation() {
        return Expectations.custom(Long.class, ExpectationMeta.anonymous(), context -> Reply.match());
    }

    private static Blueprint plainBlueprint() {
        Bay bay = Bay.of(
            bayId,
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        return Blueprint.builder(blueprintId).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("plain"), bay, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant()))
                .build();
    }

    private static Blueprint aggregateBlueprint(boolean[] ran) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay bay = Bay.of(
            bayId,
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.anySubject,
            Footprint.none(),
            Sharing.shared
        );
        ContextKey<Long> output = ContextKeys.of(id("aggregate"), Long.class, Retention.instance);
        AggregateComputation<Long> aggregate = new AggregateComputation<>() {
            @Override
            public NamespacedId id() {
                return output.id();
            }

            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of();
            }

            @Override
            public CuboidSet region() {
                return bounds.asSet();
            }

            @Override
            public Outcome<Long> compute(AggregateContext context) {
                ran[0] = true;
                return Outcome.found(1L);
            }
        };
        return Blueprint.builder(id("aggregate_blueprint")).bounds(bounds).computation(aggregate)
                .check(
                    Check.of(
                        id("aggregate_check"),
                        bay,
                        Focuses.context(output),
                        Expectations.context(output, value -> value == 1L)
                    )
                ).build();
    }

    private static Blueprint occupantBlueprint(NamespacedId id, NamespacedId bay, Cuboid bounds, Sharing sharing) {
        return occupantBlueprint(id, bay, bounds, sharing, Expectations.anyOccupant());
    }

    private static Blueprint occupantBlueprint(
        NamespacedId id,
        NamespacedId bay,
        Cuboid bounds,
        Sharing sharing,
        Expectation<OccupantSnapshot> expectation
    ) {
        Bay cell = Bay.of(bay, CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), sharing);
        return Blueprint.builder(id).bounds(bounds)
                .check(Check.of(id(id.path() + "_check"), cell, Focuses.occupant(LatticePoint.origin), expectation))
                .build();
    }

    private static List<ClaimRow> expectedRows(Blueprint blueprint, Siting siting, InstanceId rowInstance) {
        List<ClaimRow> rows = new ArrayList<>();
        for (Bay bay : blueprint.bays()) {
            CuboidSet owned = switch (bay.footprint()) {
                case Footprint.Box ignored -> mapped(siting, bay.cells());
                case Footprint.Declared declared -> mapped(siting, declared.cells());
                case Footprint.Bound ignored -> CuboidSet.empty();
                case Footprint.None ignored -> CuboidSet.empty();
            };
            for (Cuboid box : owned.boxes()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            rows.add(
                                new ClaimRow(
                                    LatticeKey.of(LatticePoint.of(x, y, z)),
                                    bay.sharing(),
                                    siting,
                                    bay.id(),
                                    rowInstance
                                )
                            );
                        }
                    }
                }
            }
        }
        rows.sort(null);
        return List.copyOf(rows);
    }

    private static CuboidSet mapped(Siting siting, CuboidSet cells) {
        CuboidSet world = siting.toWorld(cells);
        return Objects.requireNonNull(world, "the test siting maps every cell");
    }

    private static List<ClaimRow> ownRows(MatchResult.Formed formed, Blueprint blueprint, InstanceId rowInstance) {
        List<ClaimRow> rows = new ArrayList<>();
        for (ClaimRow row : formed.claims().rows()) {
            if (row.instance().equals(rowInstance) && blueprint.bay(row.bay()) != null) {
                rows.add(row);
            }
        }
        rows.sort(null);
        return List.copyOf(rows);
    }

    private static void assertRowLaws(List<ClaimRow> rows, String where) {
        assertEquals(rows, sorted(rows), where + " rows are canonically ordered");
        Set<ClaimRow> unique = new LinkedHashSet<>(rows);
        assertEquals(rows.size(), unique.size(), where + " duplicate full rows");
        Set<String> perBay = new HashSet<>();
        for (ClaimRow row : rows) {
            assertTrue(perBay.add(row.cell() + "|" + row.bay()), where + " duplicate (cell, bay) row");
        }
    }

    private static List<ClaimRow> sorted(List<ClaimRow> rows) {
        List<ClaimRow> sorted = new ArrayList<>(rows);
        sorted.sort(null);
        return List.copyOf(sorted);
    }

    private static List<LatticePoint> pointsIn(Cuboid bounds) {
        List<LatticePoint> points = new ArrayList<>();
        for (int x = bounds.min().x(); x < bounds.max().x(); x++) {
            for (int y = bounds.min().y(); y < bounds.max().y(); y++) {
                for (int z = bounds.min().z(); z < bounds.max().z(); z++) {
                    points.add(LatticePoint.of(x, y, z));
                }
            }
        }
        return points;
    }

    private static Set<LatticePoint> pointsOf(CuboidSet cells) {
        Set<LatticePoint> points = new TreeSet<>();
        for (Cuboid box : cells.boxes()) {
            points.addAll(pointsIn(box));
        }
        return points;
    }

    private static CuboidSet randomCells(Random random, List<LatticePoint> pool, int count) {
        List<LatticePoint> copy = new ArrayList<>(pool);
        List<Cuboid> boxes = new ArrayList<>();
        int wanted = Math.min(count, copy.size());
        for (int index = 0; index < wanted; index++) {
            LatticePoint point = copy.remove(random.nextInt(copy.size()));
            boxes.add(Cuboid.of(point));
        }
        return CuboidSet.of(boxes);
    }

    private static CuboidSet boundsAt(Siting siting, int size) {
        LatticePoint origin = siting.origin();
        return CuboidSet
                .of(Cuboid.of(origin, LatticePoint.of(origin.x() + 2 + size, origin.y() + 1, origin.z() + 2 + size)));
    }

    private static Map<ContextKey<?>, Object> randomValues(Random random) {
        Map<ContextKey<?>, Object> values = new LinkedHashMap<>();
        if (random.nextBoolean()) {
            values.put(countA, (long) random.nextInt(100));
        }
        if (random.nextBoolean()) {
            values.put(countB, (long) random.nextInt(100));
        }
        if (random.nextBoolean()) {
            values.put(label, "label-" + random.nextInt(100));
        }
        return values;
    }

    private static FindingBundle randomFindings(Random random, String where) {
        Finding finding = Finding.builder(Codes.computationFailed).phase(Phase.check)
                .message("fuzz finding " + random.nextInt(100)).note(where).build();
        return FindingBundle.of(List.of(finding));
    }

    private static StructureContext merge(
        StructureContext base,
        Map<ContextKey<?>, Object> values,
        FindingBundle findings,
        Spend spend
    ) {
        Map<ContextKey<?>, Object> merged = new LinkedHashMap<>(base.values());
        merged.putAll(values);
        return new StructureContext(base.instance(), base.revision() + 1, merged, findings, base.spend().plus(spend));
    }

    private static void assertPublished(
        StructureContext before,
        StructureContext after,
        Map<ContextKey<?>, Object> values,
        Spend spend
    ) {
        assertEquals(before.revision() + 1, after.revision(), "one publication is one revision");
        assertEquals(before.spend().plus(spend), after.spend(), "spend accumulates");
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            assertEquals(entry.getValue(), after.values().get(entry.getKey()), "a published value is stored");
        }
        for (Map.Entry<ContextKey<?>, Object> entry : before.values().entrySet()) {
            if (!values.containsKey(entry.getKey())) {
                assertEquals(entry.getValue(), after.values().get(entry.getKey()), "an unmentioned value is kept");
            }
        }
    }

    private static void assertSameResult(MatchResult first, MatchResult second, String where) {
        assertEquals(first.getClass(), second.getClass(), where);
        assertEquals(first.findings().findings(), second.findings().findings(), where + " finding order is stable");
        if (first instanceof MatchResult.Admitted admitted) {
            MatchResult.Admitted other = assertInstanceOf(MatchResult.Admitted.class, second, where);
            assertEquals(admitted.outcome().values(), other.outcome().values(), where + " values");
            assertEquals(admitted.outcome().spend(), other.outcome().spend(), where + " spend");
            assertEquals(admitted.outcome().reads(), other.outcome().reads(), where + " reads");
        } else if (first instanceof MatchResult.Undecided undecided) {
            MatchResult.Undecided other = assertInstanceOf(MatchResult.Undecided.class, second, where);
            assertEquals(undecided.reason(), other.reason(), where + " reason");
            if (undecided.partial() != null) {
                MatchOutcome partial = Objects.requireNonNull(undecided.partial());
                MatchOutcome otherPartial = Objects.requireNonNull(other.partial());
                assertEquals(partial.spend(), otherPartial.spend(), where + " partial spend");
            }
        }
    }

    private static OccupantSnapshot randomOccupant(Random random) {
        LatticePoint cell = LatticePoint.origin;
        return switch (random.nextInt(6)) {
            case 0 -> TestWorld.airOccupant(cell);
            case 1 -> TestWorld.block(MagicFixtures.mc("water"), cell);
            case 2 -> TestWorld.block(MagicFixtures.mc("lava"), cell);
            case 3 -> TestWorld.block(TestWorld.stone, cell);
            case 4 -> entity(MagicFixtures.mc("zombie"), cell);
            default -> TestWorld.block(MagicFixtures.mc("oak_log"), cell);
        };
    }

    private static OccupantSnapshot entity(NamespacedId type, LatticePoint cell) {
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, 0, type, OccupantKind.entity, cell, id("body"));
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.entity);
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), List.of());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("fuzz", path);
    }

    private record DeclaredFocus(boolean binds, boolean claimable) implements Focus<OccupantSnapshot> {

        @Override
        public FocusResult<OccupantSnapshot> take(FocusContext context) {
            OccupantSnapshot occupant = TestWorld.airOccupant(LatticePoint.origin);
            return new FocusResult.Taken<>(occupant, List.of(), FindingBundle.empty());
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            return CuboidSet.empty();
        }

        @Override
        public boolean bindsSubjects() {
            return binds;
        }

        @Override
        public boolean bindsClaimableSubject() {
            return claimable;
        }
    }
}
