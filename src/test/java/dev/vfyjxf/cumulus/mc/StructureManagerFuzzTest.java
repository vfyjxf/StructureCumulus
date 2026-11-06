package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.compiled;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.fillPreheater;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.fillRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.id;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftParent;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.owner;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.port;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.preheaterBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightParent;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootSiting;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seeded adversarial fuzz over the host structure-set driver and the structure manager: satellite order,
 * presence/required ordering, one shared set budget, rollback/context removal, one instance per siting and
 * no draft leak. Every case is deterministic from a fixed seed.
 */
class StructureManagerFuzzTest {

    private static final long[] seeds = {2L, 3L, 5L, 7L, 11L, 13L, 17L, 19L};
    private static final NamespacedId wrongBlock = id("fuzz_wrong_block");

    @Test
    void randomSatelliteOrdersSettleInDeclarationOrder() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 10; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                boolean swap = random.nextBoolean();
                boolean leftRequired = random.nextBoolean();
                boolean rightRequired = random.nextBoolean();
                CompiledStructureSet set = compiledSet(swap, leftRequired, rightRequired);
                StructureSetTestSupport.Fixture fixture = filledFixture(set);
                List<StructureAttempt> attempts = new ArrayList<>();
                StructureSetResult result = run(
                    fixture,
                    Budget.unbounded(),
                    new StructureSetTestSupport.TestLease(fixture.view),
                    attempts
                );
                assertTrue(result.formed(), () -> where + " " + result.setFindings());
                List<NamespacedId> declaration = declaration(fixture);
                List<NamespacedId> expected = new ArrayList<>();
                expected.add(rootId);
                expected.addAll(declaration);
                assertEquals(expected, result.settled(), where + " settle order is root then declaration order");
                assertEquals(expected, new ArrayList<>(result.outcomes().keySet()), where + " outcome order");
                assertEquals(expected, new ArrayList<>(result.claims().keySet()), where + " claims order");
                assertEquals(
                    expected.size(),
                    fixture.structures.instances().size(),
                    where + " one instance per structure"
                );
                for (NamespacedId key : expected) {
                    Blueprint blueprint = blueprintOf(fixture, key);
                    Siting siting = sitingOf(fixture, key);
                    InstanceId rowInstance = attempts.get(expected.indexOf(key)).id();
                    List<ClaimRow> expectedRows = expectedRows(blueprint, siting, rowInstance);
                    List<ClaimRow> actual = ownRows(result, key, blueprint, siting);
                    assertEquals(expectedRows, actual, where + " claim rows of " + key);
                    assertRowLaws(actual, where + " " + key);
                }
                assertManagerInvariants(fixture.structures, where);
                assertAttemptsClosed(attempts, where);
                assertContextsPublished(fixture, expected, where);

                StructureSetTestSupport.Fixture twin = filledFixture(set);
                List<StructureAttempt> twinAttempts = new ArrayList<>();
                StructureSetResult again = run(
                    twin,
                    Budget.unbounded(),
                    new StructureSetTestSupport.TestLease(twin.view),
                    twinAttempts
                );
                assertEquals(result.settled(), again.settled(), where + " deterministic settled order");
                assertEquals(result.rolledBack(), again.rolledBack(), where + " deterministic rollback order");
                assertEquals(
                    new ArrayList<>(result.claims().keySet()),
                    new ArrayList<>(again.claims().keySet()),
                    where
                );
                for (NamespacedId key : expected) {
                    assertEquals(result.claimsOf(key), again.claimsOf(key), where + " deterministic rows of " + key);
                }
                assertEquals(outcomeSpend(result), outcomeSpend(again), where + " deterministic spend");
                assertEquals(findingCodes(result), findingCodes(again), where + " deterministic findings");
            }
        }
    }

    @Test
    void presenceRequiredOrderAndBudgetAreBounded() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 12; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                boolean swap = random.nextBoolean();
                boolean leftRequired = random.nextBoolean();
                boolean rightRequired = random.nextBoolean();
                boolean fillLeft = random.nextBoolean();
                boolean fillRight = random.nextBoolean();
                Budget budget = randomBudget(random);
                StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
                    compiledSet(swap, leftRequired, rightRequired)
                );
                fillRoot(fixture);
                if (fillLeft) {
                    fillPreheater(fixture, fixture.plan.satellite(leftId));
                }
                if (fillRight) {
                    fillPreheater(fixture, fixture.plan.satellite(rightId));
                }
                List<StructureAttempt> attempts = new ArrayList<>();
                StructureSetResult result = run(
                    fixture,
                    budget,
                    new StructureSetTestSupport.TestLease(fixture.view),
                    attempts
                );
                assertAttemptsClosed(attempts, where);
                assertOutcomeOrder(fixture, result, where);
                assertBudget(result, budget, where);
                if (result.settled().isEmpty()) {
                    assertEquals(0, fixture.ledger.settleCalls, where + " no settle after a failure");
                    assertTrue(fixture.structures.instances().isEmpty(), where + " a failed set leaves no instance");
                    for (InstanceId id : fixture.instances) {
                        assertEquals(0L, fixture.structures.context(id).revision(), where + " no context leak");
                    }
                } else {
                    assertManagerInvariants(fixture.structures, where);
                    assertContextsPublished(fixture, result.settled(), where);
                }
                boolean absentRequired = (leftRequired && !fillLeft) || (rightRequired && !fillRight);
                if (budget.cells() == 0) {
                    assertTrue(
                        codes(result).contains(Codes.presenceUnreadable.code()),
                        () -> where + " a zero-cell budget cannot prove presence: " + result.setFindings()
                    );
                } else if (absentRequired && !codes(result).contains(Codes.presenceUnreadable.code())) {
                    assertTrue(
                        codes(result).contains(Codes.satelliteAbsent.code()),
                        () -> where + " a required absent satellite stops the set: " + result.setFindings()
                    );
                }
            }
        }

        // Required satellites are probed before optional ones even when declared later. Every presence
        // cell is non-air so the required probe costs exactly one cell; the optional probe then exhausts.
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(compiledSet(true, false, true));
        fillRoot(fixture);
        fillPresence(fixture, Objects.requireNonNull(fixture.plan.satellite(rightId)));
        fillPresence(fixture, Objects.requireNonNull(fixture.plan.satellite(leftId)));
        List<StructureAttempt> attempts = new ArrayList<>();
        StructureSetResult result = run(
            fixture,
            Budget.of(1, 0),
            new StructureSetTestSupport.TestLease(fixture.view),
            attempts
        );
        assertEquals(0, result.settled().size(), "a one-cell budget cannot pass presence");
        assertTrue(codes(result).contains(Codes.presenceUnreadable.code()), () -> "observed " + result.setFindings());
        Finding finding = result.firstFailure();
        assertTrue(
            finding != null && finding.message().contains(leftId.text()),
            () -> "required-first order: the optional " + leftId + " should be the exhausted probe, observed " + finding
        );
        assertTrue(attempts.isEmpty(), "a failed presence probe begins no draft");
    }

    @Test
    void failedSetsNeverSettleAndLeakNoDrafts() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 10; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                int failure = random.nextInt(6);
                boolean swap = random.nextBoolean();
                boolean requiredAbsent = failure == 0;
                StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
                    compiledSet(swap, requiredAbsent, false)
                );
                fillRoot(fixture);
                if (!requiredAbsent) {
                    fillPreheater(fixture, fixture.plan.satellite(leftId));
                }
                fillPreheater(fixture, fixture.plan.satellite(rightId));
                Budget budget = Budget.unbounded();
                switch (failure) {
                    case 1 -> fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.of(0, 0, 0)), wrongBlock);
                    case 2 -> fixture.view.put(
                        world(Objects.requireNonNull(fixture.plan.satellite(leftId), where).siting(), port),
                        wrongBlock
                    );
                    case 4 -> budget = Budget.of(0, 0);
                    case 5 -> fixture.ledger.seed(
                        new ClaimRow(
                            LatticeKey.of(world(fixture.plan.rootSiting(), LatticePoint.of(0, 0, 0))),
                            Sharing.exclusive,
                            fixture.plan.rootSiting(),
                            id("rival_bay"),
                            new InstanceId(world, 900)
                        )
                    );
                    default -> {}
                }
                StructureSetTestSupport.TestLease lease = new StructureSetTestSupport.TestLease(fixture.view);
                if (failure == 3) {
                    lease.invalidate();
                }
                List<StructureAttempt> attempts = new ArrayList<>();
                StructureSetResult result = run(fixture, budget, lease, attempts);
                assertFalse(result.formed(), where + " a failed set never forms");
                assertTrue(result.settled().isEmpty(), where + " no settle after a failure");
                assertTrue(result.claims().isEmpty(), where + " no claim row after a failure");
                assertEquals(0, fixture.ledger.settleCalls, where + " the ledger is never written");
                assertTrue(fixture.structures.instances().isEmpty(), where + " no instance after a failure");
                assertAttemptsClosed(attempts, where);
                for (InstanceId id : fixture.instances) {
                    assertEquals(0L, fixture.structures.context(id).revision(), where + " no context leak");
                }
            }
        }
    }

    @Test
    void settleFailuresRollBackClaimsAndRemoveContexts() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int iteration = 0; iteration < 9; iteration++) {
                String where = "seed=" + seed + " iteration=" + iteration;
                boolean swap = random.nextBoolean();
                StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
                    compiledSet(swap, false, false)
                );
                fillRoot(fixture);
                fillPreheater(fixture, fixture.plan.satellite(leftId));
                fillPreheater(fixture, fixture.plan.satellite(rightId));
                List<NamespacedId> declaration = declaration(fixture);
                List<NamespacedId> attemptOrder = new ArrayList<>();
                attemptOrder.add(rootId);
                attemptOrder.addAll(declaration);
                int failingIndex = random.nextInt(attemptOrder.size());
                NamespacedId failingKey = attemptOrder.get(failingIndex);
                Siting failingSiting = sitingOf(fixture, failingKey);
                fixture.ledger.movingSiting = failingSiting;
                boolean throwingRelease = failingIndex > 0 && random.nextBoolean();
                if (throwingRelease) {
                    fixture.ledger.throwingRelease = rootSiting();
                }

                List<StructureAttempt> attempts = new ArrayList<>();
                StructureSetResult result = run(
                    fixture,
                    Budget.unbounded(),
                    new StructureSetTestSupport.TestLease(fixture.view),
                    attempts
                );
                assertFalse(result.formed(), where + " a settle failure rolls the set back");
                List<NamespacedId> expectedSettled = new ArrayList<>(attemptOrder.subList(0, failingIndex));
                assertEquals(expectedSettled, result.settled(), where + " the prefix formed before the failure");
                List<NamespacedId> expectedRolledBack = new ArrayList<>(expectedSettled);
                Collections.reverse(expectedRolledBack);
                if (throwingRelease) {
                    expectedRolledBack.remove(rootId);
                    assertEquals(1, result.rollbackFailures().size(), where + " a failed release is reported");
                    assertEquals(Codes.rollbackFailed.code(), result.rollbackFailures().get(0).code(), where);
                } else {
                    assertTrue(result.rollbackFailures().isEmpty(), where + " a clean rollback reports no failure");
                }
                assertEquals(expectedRolledBack, result.rolledBack(), where + " rollback is reverse settle order");
                assertEquals(
                    attemptOrder,
                    new ArrayList<>(result.outcomes().keySet()),
                    where + " every admitted check is kept"
                );
                assertEquals(
                    expectedSettled,
                    new ArrayList<>(result.claims().keySet()),
                    where + " claims only for formed structures"
                );
                assertAttemptsClosed(attempts, where);
                assertTrue(fixture.structures.instances().isEmpty(), where + " rollback removes every instance");
                for (InstanceId id : fixture.instances) {
                    assertNull(fixture.structures.find(id), where + " a rolled-back instance is gone");
                    assertEquals(
                        0L,
                        fixture.structures.context(id).revision(),
                        where + " a rolled-back context is removed"
                    );
                }
                if (throwingRelease) {
                    assertTrue(fixture.ledger.rows().size() > 0, where + " a failed release leaves its rows");
                } else {
                    assertTrue(fixture.ledger.rows().isEmpty(), where + " a clean rollback releases every row");
                }
            }
        }
    }

    @Test
    void managerEventsKeepOneInstancePerSitingAndOneShotDrafts() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            StructureSetTestSupport.Fixture fixture = filledFixture(compiledSet(random.nextBoolean(), false, false));
            List<StructureAttempt> attempts = new ArrayList<>();
            StructureSetResult result = run(
                fixture,
                Budget.unbounded(),
                new StructureSetTestSupport.TestLease(fixture.view),
                attempts
            );
            assertTrue(result.formed(), () -> "seed=" + seed + " " + result.setFindings());
            List<Siting> spares = List.of(
                Siting.of(LatticePoint.of(600, 64, 600), Orientation.identity),
                Siting.of(LatticePoint.of(620, 64, 620), Orientation.identity),
                Siting.of(LatticePoint.of(640, 64, 640), Orientation.identity)
            );
            for (int step = 0; step < 40; step++) {
                String where = "seed=" + seed + " step=" + step;
                StructureManager manager = fixture.structures;
                if (manager.instances().isEmpty()) {
                    StructureAttempt attempt = manager.begin(
                        world,
                        new StructureSpec(owner, spares.get(random.nextInt(spares.size())), id("restart"))
                    );
                    attempt.commit(
                        CuboidSet.of(Cuboid.of(LatticePoint.of(600, 64, 600), LatticePoint.of(602, 65, 602)))
                    );
                }
                List<StructureInstance> current = manager.instances();
                StructureInstance instance = current.get(random.nextInt(current.size()));
                int op = random.nextInt(6);
                switch (op) {
                    case 0, 1 -> {
                        manager.onEvent(new StructureEvent.Broken(instance.id()));
                        manager.onEvent(new StructureEvent.Broken(instance.id()));
                        assertNull(manager.find(instance.id()), where + " Broken is idempotent");
                        assertEquals(
                            0L,
                            manager.context(instance.id()).revision(),
                            where + " Broken removes the context"
                        );
                    }
                    case 4 -> {
                        manager.onEvent(new StructureEvent.Invalidated(instance.id(), "fuzz"));
                        assertNull(manager.find(instance.id()), where + " Invalidated removes the instance");
                    }
                    default -> {
                        StructureAttempt attempt = manager.begin(
                            world,
                            new StructureSpec(owner, spares.get(random.nextInt(spares.size())), id("probe"))
                        );
                        attempt.abort();
                        assertThrows(IllegalStateException.class, attempt::abort, where + " abort is one-shot");
                        assertEquals(
                            0L,
                            manager.context(attempt.id()).revision(),
                            where + " an aborted draft leaves no context"
                        );
                    }
                }
                assertManagerInvariants(manager, where);
            }
            managerUnloadAndLoad(seed, fixture);
        }
    }

    private static void managerUnloadAndLoad(long seed, StructureSetTestSupport.Fixture fixture) {
        StructureManager manager = fixture.structures;
        List<StructureInstance> before = manager.instances();
        manager.onEvent(new StructureEvent.Unloaded(world));
        assertTrue(manager.instances().isEmpty(), "seed=" + seed + " Unloaded clears the world");
        manager.onEvent(new StructureEvent.Loaded(world, before));
        assertEquals(before.size(), manager.instances().size(), "seed=" + seed + " Loaded restores the same instances");
        assertEquals(before, manager.instances(), "seed=" + seed + " Loaded keeps ids and contexts");
        long maxSerial = before.stream().mapToLong(instance -> instance.id().serial()).max().orElse(0L);
        StructureAttempt probe = manager.begin(
            world,
            new StructureSpec(owner, Siting.of(LatticePoint.of(700, 64, 700), Orientation.identity), id("watermark"))
        );
        assertTrue(probe.id().serial() > maxSerial, "seed=" + seed + " Loaded raises the serial watermark");
        probe.abort();
        assertManagerInvariants(manager, "seed=" + seed + " after load");
    }

    // ------------------------------------------------------------------ helpers

    private static CompiledStructureSet compiledSet(boolean swap, boolean leftRequired, boolean rightRequired) {
        CompiledSatellite left = compiled(
            leftSatellite(leftRequired, LinkSpec.face(leftParent, port)),
            preheaterBlueprint()
        );
        CompiledSatellite right = compiled(
            rightSatellite(rightRequired, LinkSpec.face(rightParent, port)),
            preheaterBlueprint()
        );
        return swap ? compiled(right, left) : compiled(left, right);
    }

    private static StructureSetTestSupport.Fixture filledFixture(CompiledStructureSet set) {
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(set);
        fillRoot(fixture);
        fillPreheater(fixture, fixture.plan.satellite(leftId));
        fillPreheater(fixture, fixture.plan.satellite(rightId));
        return fixture;
    }

    private static void fillPresence(StructureSetTestSupport.Fixture fixture, StructureSet.Satellite satellite) {
        Cuboid box = satellite.worldBounds();
        for (int x = box.min().x(); x < box.max().x(); x++) {
            for (int y = box.min().y(); y < box.max().y(); y++) {
                for (int z = box.min().z(); z < box.max().z(); z++) {
                    fixture.view.put(LatticePoint.of(x, y, z), id("presence_block"));
                }
            }
        }
    }

    private static StructureSetResult run(
        StructureSetTestSupport.Fixture fixture,
        Budget budget,
        SnapshotLease lease,
        List<StructureAttempt> attempts
    ) {
        StructureSetHost host = new StructureSetHost(fixture.kernel);
        return host.run(fixture.plan, lease, owner, budget, spec -> {
            StructureAttempt attempt = fixture.structures.begin(world, spec);
            attempts.add(attempt);
            return attempt;
        });
    }

    private static Budget randomBudget(Random random) {
        return switch (random.nextInt(7)) {
            case 0 -> Budget.of(0, 0);
            case 1 -> Budget.of(1, 0);
            case 2 -> Budget.of(2, 0);
            case 3 -> Budget.of(3, 0);
            case 4 -> Budget.of(8, 1);
            case 5 -> Budget.of(20, 2);
            default -> Budget.unbounded();
        };
    }

    private static List<NamespacedId> declaration(StructureSetTestSupport.Fixture fixture) {
        List<NamespacedId> order = new ArrayList<>();
        for (StructureSet.Satellite satellite : fixture.plan.satellites()) {
            order.add(satellite.id());
        }
        return order;
    }

    private static Blueprint blueprintOf(StructureSetTestSupport.Fixture fixture, NamespacedId key) {
        if (key.equals(rootId)) {
            return fixture.plan.root();
        }
        StructureSet.Satellite satellite = Objects.requireNonNull(fixture.plan.satellite(key), "satellite " + key);
        return satellite.blueprint();
    }

    private static Siting sitingOf(StructureSetTestSupport.Fixture fixture, NamespacedId key) {
        if (key.equals(rootId)) {
            return fixture.plan.rootSiting();
        }
        StructureSet.Satellite satellite = Objects.requireNonNull(fixture.plan.satellite(key), "satellite " + key);
        return satellite.siting();
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
            for (Cuboid box : owned.decomposition()) {
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
        return Objects.requireNonNull(world, "the fixture siting maps every cell");
    }

    private static List<ClaimRow> ownRows(
        StructureSetResult result,
        NamespacedId key,
        Blueprint blueprint,
        Siting siting
    ) {
        List<ClaimRow> rows = new ArrayList<>();
        for (ClaimRow row : result.claimsOf(key)) {
            if (row.siting().equals(siting) && blueprint.bay(row.bay()) != null) {
                rows.add(row);
            }
        }
        rows.sort(null);
        return List.copyOf(rows);
    }

    private static void assertRowLaws(List<ClaimRow> rows, String where) {
        assertEquals(rows, sorted(rows), where + " rows are canonically ordered");
        Set<ClaimRow> unique = new HashSet<>(rows);
        assertEquals(rows.size(), unique.size(), where + " duplicate rows");
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

    private static void assertOutcomeOrder(
        StructureSetTestSupport.Fixture fixture,
        StructureSetResult result,
        String where
    ) {
        List<NamespacedId> expected = new ArrayList<>();
        if (result.outcomes().containsKey(rootId)) {
            expected.add(rootId);
        }
        for (StructureSet.Satellite satellite : fixture.plan.satellites()) {
            if (result.outcomes().containsKey(satellite.id())) {
                expected.add(satellite.id());
            }
        }
        List<NamespacedId> actual = new ArrayList<>(result.outcomes().keySet());
        assertEquals(expected, actual, where + " outcome order");
    }

    private static void assertManagerInvariants(StructureManager manager, String where) {
        List<StructureInstance> instances = manager.instances();
        Set<InstanceId> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        InstanceId previous = null;
        for (StructureInstance instance : instances) {
            assertTrue(ids.add(instance.id()), where + " duplicate instance id");
            assertTrue(
                keys.add(instance.owner().id().text() + "@" + instance.siting()),
                where + " two instances share (owner, siting)"
            );
            assertEquals(instance.id(), instance.context().instance(), where + " context identity");
            assertEquals(instance, manager.find(instance.id()), where + " find(id)");
            assertEquals(instance, manager.find(instance.owner(), instance.siting()), where + " find(owner, siting)");
            for (Cuboid box : instance.bounds().decomposition()) {
                assertTrue(manager.findAt(box.min()).contains(instance), where + " findAt covers the bounds");
            }
            if (previous != null) {
                assertTrue(previous.compareTo(instance.id()) < 0, where + " instances() is sorted by id");
            }
            previous = instance.id();
        }
    }

    private static void assertAttemptsClosed(List<StructureAttempt> attempts, String where) {
        for (StructureAttempt attempt : attempts) {
            assertThrows(
                IllegalStateException.class,
                attempt::abort,
                where + " draft leak: attempt " + attempt.id() + " was neither committed nor aborted"
            );
        }
    }

    private static void assertContextsPublished(
        StructureSetTestSupport.Fixture fixture,
        List<NamespacedId> settled,
        String where
    ) {
        for (NamespacedId key : settled) {
            Siting siting = sitingOf(fixture, key);
            StructureInstance instance = Objects
                    .requireNonNull(fixture.structures.find(owner, siting), where + " " + key);
            assertTrue(instance.context().revision() >= 1, where + " " + key + " published a context");
        }
    }

    private static void assertBudget(StructureSetResult result, Budget budget, String where) {
        long cells = 0;
        long sections = 0;
        for (MatchOutcome outcome : result.outcomes().values()) {
            cells += outcome.spend().cells();
            sections += outcome.spend().sections();
        }
        if (budget.cells() != Integer.MAX_VALUE) {
            assertTrue(
                cells <= budget.cells(),
                where + " admitted spend " + cells + " exceeds cells " + budget.cells()
            );
        }
        if (budget.sections() != Integer.MAX_VALUE) {
            assertTrue(sections <= budget.sections(), where + " admitted spend " + sections + " exceeds sections");
        }
    }

    private static Spend outcomeSpend(StructureSetResult result) {
        int cells = 0;
        int sections = 0;
        for (MatchOutcome outcome : result.outcomes().values()) {
            cells += outcome.spend().cells();
            sections += outcome.spend().sections();
        }
        return new Spend(cells, sections);
    }

    private static List<FindingCode> findingCodes(StructureSetResult result) {
        List<FindingCode> codes = new ArrayList<>();
        for (Finding finding : result.setFindings()) {
            codes.add(finding.code());
        }
        return codes;
    }

    private static List<FindingCode> codes(StructureSetResult result) {
        return findingCodes(result);
    }
}
