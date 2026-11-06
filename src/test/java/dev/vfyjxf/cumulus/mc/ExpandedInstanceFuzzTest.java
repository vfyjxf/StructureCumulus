package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
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
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.engine.MatchEngine;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expanded seeded fuzz of the host-driven instance lifecycle: many instances with overlapping bounds and
 * the same {@code (owner, siting)} in different worlds, real check/settle claim rows, per-world ledgers,
 * context isolation, lifecycle events, per-world unload/load and no draft leaks. Failures name the seed
 * and step that produced them.
 */
class ExpandedInstanceFuzzTest {

    private static final long[] seeds = {3L, 5L, 7L, 11L, 13L};
    private static final NamespacedId blueprintId = id("blueprint");
    private static final NamespacedId bayId = id("bay");
    private static final ContextKey<Long> countA = ContextKeys.of(id("count_a"), Long.class, Retention.instance);
    private static final ContextKey<Long> countB = ContextKeys.of(id("count_b"), Long.class, Retention.instance);
    private static final ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);
    private static final OwnerId[] owners = {OwnerId.of(id("owner_a")), OwnerId.of(id("owner_b"))};
    private static final World[] worlds = {World.of(id("level_a")), World.of(id("level_b"))};
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 2);
    private static final Siting[] sitings = {Siting.of(LatticePoint.of(100, 10, 100), Orientation.identity),
            Siting.of(LatticePoint.of(101, 10, 100), Orientation.identity),
            Siting.of(LatticePoint.of(102, 10, 100), Orientation.identity),
            Siting.of(LatticePoint.of(101, 10, 101), Orientation.identity),
            Siting.of(LatticePoint.of(104, 10, 104), Orientation.identity),
            Siting.of(LatticePoint.of(103, 10, 104), Orientation.identity)};
    private static final Blueprint sharedBlueprint = blueprint(Sharing.shared);
    private static final Blueprint exclusiveBlueprint = blueprint(Sharing.exclusive);

    @Test
    void expandedInstanceScriptsKeepClaimsContextsAndLifecycleConsistent() {
        for (long seed : seeds) {
            new ExpandedInstanceFuzz(seed).run();
        }
    }

    @Test
    void exclusiveClaimsRejectOverlappingFormationsAndKeepRowsUntouched() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            StructureManager manager = StructureManager.create();
            StructureSetTestSupport.TestView view = new StructureSetTestSupport.TestView();
            StructureSetTestSupport.FakeLedger ledger = new StructureSetTestSupport.FakeLedger();
            OwnerId owner = owners[random.nextInt(owners.length)];
            Siting first = sitings[0];
            Siting second = sitings[1];
            StructureInstance formed = form(manager, ledger, view, exclusiveBlueprint, worlds[0], owner, first, seed);
            assertNotNull(formed, "seed=" + seed + " the first exclusive instance forms");
            List<ClaimRow> firstRows = List.copyOf(ledger.rows());
            int settleCalls = ledger.settleCalls;
            long revision = ledger.revision();

            StructureAttempt attempt = manager
                    .begin(worlds[0], new StructureSpec(owner, second, exclusiveBlueprint.id()));
            MatchRequest request = MatchRequest.of(
                exclusiveBlueprint,
                second,
                new StructureSetTestSupport.TestLease(view),
                attempt.id(),
                owner,
                Budget.unbounded(),
                ledger.read(Objects.requireNonNull(second.toWorld(bounds)))
            );
            MatchResult checked = MatchEngine.check(request);
            MatchResult.Failed failed = assertInstanceOf(
                MatchResult.Failed.class,
                checked,
                "seed=" + seed + " overlapping exclusive claims conflict at check"
            );
            assertTrue(hasCode(failed, Codes.claimConflict), "seed=" + seed + " claim conflict code");
            attempt.abort();
            assertEquals(firstRows, ledger.rows(), "seed=" + seed + " a failed check writes no row");
            assertEquals(revision, ledger.revision(), "seed=" + seed + " a failed check leaves the revision");
            assertEquals(settleCalls, ledger.settleCalls, "seed=" + seed + " no settle after a failed check");
            assertEquals(1, manager.instances().size(), "seed=" + seed + " only the first instance exists");
            assertEquals(
                0L,
                manager.context(attempt.id()).revision(),
                "seed=" + seed + " the failed draft has no context"
            );
        }
    }

    @Test
    void unloadedWorldsKeepClaimsAndLoadedRestoresInstancesAndContexts() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            Map<World, StructureSetTestSupport.FakeLedger> ledgers = new LinkedHashMap<>();
            for (World world : worlds) {
                ledgers.put(world, new StructureSetTestSupport.FakeLedger());
            }
            StructureManager manager = StructureManager.create();
            StructureSetTestSupport.TestView view = new StructureSetTestSupport.TestView();
            Map<World, List<StructureInstance>> formed = new LinkedHashMap<>();
            for (World world : worlds) {
                formed.put(world, new ArrayList<>());
            }
            for (int index = 0; index < 10; index++) {
                World world = worlds[index % worlds.length];
                OwnerId owner = owners[index % owners.length];
                Siting siting = sitings[(index / worlds.length) % sitings.length];
                StructureInstance instance = form(
                    manager,
                    Objects.requireNonNull(ledgers.get(world)),
                    view,
                    sharedBlueprint,
                    world,
                    owner,
                    siting,
                    seed
                );
                assertNotNull(instance, "seed=" + seed + " formation " + index);
                Objects.requireNonNull(formed.get(world)).add(instance);
            }
            assertEquals(
                10,
                manager.instances().size(),
                "seed=" + seed + " every formation is one instance "
                        + manager.instances().stream()
                                .map(
                                    instance -> instance.id().world().id().text() + "|" + instance.owner().id().text()
                                            + "|" + instance.siting()
                                ).toList()
            );
            for (World target : worlds) {
                Map<World, List<StructureInstance>> others = new LinkedHashMap<>();
                for (World other : worlds) {
                    if (!other.equals(target)) {
                        others.put(
                            other,
                            manager.instances().stream().filter(instance -> instance.id().world().equals(other))
                                    .toList()
                        );
                    }
                }
                StructureSetTestSupport.FakeLedger targetLedger = Objects.requireNonNull(ledgers.get(target));
                List<ClaimRow> targetRows = List.copyOf(targetLedger.rows());
                long targetRevision = targetLedger.revision();
                Map<InstanceId, StructureContext> contexts = new LinkedHashMap<>();
                for (StructureInstance instance : Objects.requireNonNull(formed.get(target))) {
                    contexts.put(instance.id(), instance.context());
                }

                manager.onEvent(new StructureEvent.Unloaded(target));
                assertTrue(
                    manager.instances().stream().noneMatch(instance -> instance.id().world().equals(target)),
                    "seed=" + seed
                );
                assertOtherWorlds(manager, others, "seed=" + seed + " unload");
                assertEquals(targetRows, targetLedger.rows(), "seed=" + seed + " unload keeps claim rows");
                assertEquals(targetRevision, targetLedger.revision(), "seed=" + seed + " unload does not bump claims");
                for (Map.Entry<World, List<StructureInstance>> entry : others.entrySet()) {
                    assertEquals(
                        entry.getValue(),
                        manager.instances().stream().filter(instance -> instance.id().world().equals(entry.getKey()))
                                .toList(),
                        "seed=" + seed + " unload keeps the other world"
                    );
                }
                for (StructureInstance instance : Objects.requireNonNull(formed.get(target))) {
                    assertEquals(
                        0L,
                        manager.context(instance.id()).revision(),
                        "seed=" + seed + " unload clears contexts"
                    );
                }

                manager.onEvent(new StructureEvent.Loaded(target, Objects.requireNonNull(formed.get(target))));
                for (Map.Entry<World, List<StructureInstance>> entry : others.entrySet()) {
                    assertEquals(
                        entry.getValue(),
                        manager.instances().stream().filter(instance -> instance.id().world().equals(entry.getKey()))
                                .toList(),
                        "seed=" + seed + " load keeps the other world"
                    );
                }
                for (StructureInstance instance : Objects.requireNonNull(formed.get(target))) {
                    StructureInstance restored = manager.find(instance.id());
                    assertNotNull(restored, "seed=" + seed + " loaded instance is findable");
                    assertEquals(
                        contexts.get(instance.id()),
                        restored.context(),
                        "seed=" + seed + " loaded context survives"
                    );
                }
                assertEquals(targetRows, targetLedger.rows(), "seed=" + seed + " load keeps claim rows");
                StructureAttempt probe = manager.begin(
                    target,
                    new StructureSpec(owners[random.nextInt(owners.length)], sitings[4], blueprintId)
                );
                probe.abort();
            }
        }
    }

    // ------------------------------------------------------------------ expanded script

    private static final class ExpandedInstanceFuzz {

        private final long seed;
        private final Random random;
        private final StructureManager manager = StructureManager.create();
        private final StructureSetTestSupport.TestView view = new StructureSetTestSupport.TestView();
        private final Map<World, StructureSetTestSupport.FakeLedger> ledgers = new LinkedHashMap<>();
        private final Map<World, Long> revisions = new LinkedHashMap<>();
        private final Map<World, Map<String, List<ClaimRow>>> claims = new LinkedHashMap<>();
        private final Map<World, List<StructureInstance>> loaded = new LinkedHashMap<>();
        private final Map<InstanceId, StructureAttempt> attempts = new LinkedHashMap<>();
        private final List<String> trace = new ArrayList<>();
        private final Set<World> unloaded = new HashSet<>();
        private long lastSerial;

        private ExpandedInstanceFuzz(long seed) {
            this.seed = seed;
            this.random = new Random(seed);
            for (World world : worlds) {
                ledgers.put(world, new StructureSetTestSupport.FakeLedger());
                revisions.put(world, 0L);
                claims.put(world, new LinkedHashMap<>());
                loaded.put(world, new ArrayList<>());
            }
        }

        private void run() {
            for (int index = 0; index < 14; index++) {
                World world = worlds[random.nextInt(worlds.length)];
                OwnerId owner = owners[random.nextInt(owners.length)];
                Siting siting = sitings[random.nextInt(sitings.length)];
                formInstance(world, owner, siting, sharedBlueprint);
            }
            for (int step = 0; step < 45; step++) {
                int op = random.nextInt(9);
                trace.add("op" + op + ":" + manager.instances().size());
                switch (op) {
                    case 0, 1 -> publishContext();
                    case 2 -> publishContext();
                    case 3, 4 -> brokenOrInvalidated();
                    case 5 -> unloadWorld();
                    case 6 -> loadWorld();
                    case 7 -> removeInstance();
                    default -> formRandom();
                }
                assertInvariants(step);
            }
            for (World world : worlds) {
                manager.onEvent(new StructureEvent.Unloaded(world));
            }
            assertTrue(manager.instances().isEmpty(), where() + " final unload");
            for (Map.Entry<InstanceId, StructureAttempt> entry : attempts.entrySet()) {
                assertThrows(IllegalStateException.class, entry.getValue()::abort, where() + " final draft closure");
            }
            for (World world : worlds) {
                assertLedger(world);
            }
            for (InstanceId id : attempts.keySet()) {
                assertEquals(0L, manager.context(id).revision(), where() + " every context is gone");
            }
        }

        private void formRandom() {
            List<World> available = new ArrayList<>();
            for (World candidate : worlds) {
                if (!unloaded.contains(candidate)) {
                    available.add(candidate);
                }
            }
            if (available.isEmpty()) {
                return;
            }
            World world = available.get(random.nextInt(available.size()));
            OwnerId owner = owners[random.nextInt(owners.length)];
            Siting siting = sitings[random.nextInt(sitings.length)];
            formInstance(world, owner, siting, sharedBlueprint);
        }

        private @Nullable StructureInstance formInstance(
            World world,
            OwnerId owner,
            Siting siting,
            Blueprint blueprint
        ) {
            StructureAttempt attempt = manager.begin(world, new StructureSpec(owner, siting, blueprint.id()));
            attempts.put(attempt.id(), attempt);
            StructureSetTestSupport.FakeLedger ledger = Objects.requireNonNull(ledgers.get(world));
            Cuboid worldBounds = Objects.requireNonNull(siting.toWorld(blueprint.bounds()));
            MatchRequest request = MatchRequest.of(
                blueprint,
                siting,
                new StructureSetTestSupport.TestLease(view),
                attempt.id(),
                owner,
                Budget.unbounded(),
                ledger.read(worldBounds)
            );
            MatchResult checked = MatchEngine.check(request);
            if (!(checked instanceof MatchResult.Admitted admitted)) {
                attempt.abort();
                return null;
            }
            MatchResult settled = MatchEngine.settle(request, admitted, ledger, attempt);
            if (!(settled instanceof MatchResult.Formed formed)) {
                attempt.abort();
                return null;
            }
            assertTrue(attempt.id().serial() > lastSerial, where() + " serial monotonic");
            lastSerial = attempt.id().serial();
            StructureInstance previous = manager.find(world, owner, siting);
            StructureInstance instance = attempt.commit(worldBounds.asSet());
            assertEquals(worldBounds.asSet(), instance.bounds(), where() + " commit uses world-frame bounds");
            if (previous != null) {
                assertNull(manager.find(previous.id()), where() + " commit replaces the formed instance");
                assertEquals(
                    0L,
                    manager.context(previous.id()).revision(),
                    where() + " the replaced context is removed"
                );
            }
            revision(world, 1);
            List<ClaimRow> expected = expectedRows(blueprint, siting, attempt.id());
            assertEquals(expected, ownRows(formed, blueprint, siting), where() + " settled rows");
            claimModel(world).put(tuple(attempt.id()), expected);
            List<StructureInstance> saved = Objects.requireNonNull(loaded.get(world));
            saved.removeIf(
                candidate -> candidate.id().equals(instance.id())
                        || (candidate.owner().equals(owner) && candidate.siting().equals(siting))
            );
            saved.add(instance);
            return instance;
        }

        private void publishContext() {
            List<InstanceId> live = liveIds();
            if (live.isEmpty()) {
                return;
            }
            InstanceId target = live.get(random.nextInt(live.size()));
            Map<InstanceId, StructureContext> before = new LinkedHashMap<>();
            for (InstanceId id : live) {
                before.put(id, manager.context(id));
            }
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
            Spend spend = new Spend(random.nextInt(3), random.nextInt(2));
            StructureContext base = Objects.requireNonNull(before.get(target));
            StructureContext after = manager.publish(target, values, FindingBundle.empty(), spend);
            assertEquals(base.revision() + 1, after.revision(), where() + " revision increments");
            assertEquals(base.spend().plus(spend), after.spend(), where() + " spend accumulates");
            for (InstanceId other : live) {
                if (!other.equals(target)) {
                    assertEquals(before.get(other), manager.context(other), where() + " context isolation " + other);
                }
            }
        }

        private void brokenOrInvalidated() {
            List<InstanceId> live = liveIds();
            if (live.isEmpty()) {
                return;
            }
            InstanceId id = live.get(random.nextInt(live.size()));
            StructureInstance instance = Objects.requireNonNull(manager.find(id), where() + " broken target is formed");
            World world = id.world();
            StructureSetTestSupport.FakeLedger ledger = Objects.requireNonNull(ledgers.get(world));
            ClaimVerdict released = ledger.release(instance.id());
            assertInstanceOf(ClaimVerdict.Settled.class, released, where() + " remove releases the instance rows");
            revision(world, 1);
            claimModel(world).remove(tuple(instance.id()));
            manager.onEvent(new StructureEvent.Broken(id));
            manager.onEvent(new StructureEvent.Invalidated(id, "again"));
            assertNull(manager.find(id), where() + " Broken removes and is idempotent");
            assertEquals(0L, manager.context(id).revision(), where() + " the context is removed");
            assertTrue(
                Objects.requireNonNull(loaded.get(world)).removeIf(candidate -> candidate.id().equals(id)),
                where() + " the saved instance is dropped"
            );
        }

        private void unloadWorld() {
            World world = worlds[random.nextInt(worlds.length)];
            StructureSetTestSupport.FakeLedger ledger = Objects.requireNonNull(ledgers.get(world));
            List<ClaimRow> rowsBefore = List.copyOf(ledger.rows());
            long revisionBefore = ledger.revision();
            manager.onEvent(new StructureEvent.Unloaded(world));
            unloaded.add(world);
            assertTrue(
                manager.instances().stream().noneMatch(instance -> instance.id().world().equals(world)),
                where() + " unload removes only its world"
            );
            assertEquals(rowsBefore, ledger.rows(), where() + " unload keeps claim rows");
            assertEquals(revisionBefore, ledger.revision(), where() + " unload does not bump claims");
        }

        private void loadWorld() {
            World world = worlds[random.nextInt(worlds.length)];
            StructureSetTestSupport.FakeLedger ledger = Objects.requireNonNull(ledgers.get(world));
            List<ClaimRow> rowsBefore = List.copyOf(ledger.rows());
            long revisionBefore = ledger.revision();
            List<StructureInstance> restored = new ArrayList<>(Objects.requireNonNull(loaded.get(world)));
            restored.sort(Comparator.comparing(StructureInstance::id));
            manager.onEvent(new StructureEvent.Loaded(world, restored));
            unloaded.remove(world);
            for (StructureInstance instance : restored) {
                assertEquals(instance, manager.find(instance.id()), where() + " Loaded restores the instance");
            }
            assertEquals(rowsBefore, ledger.rows(), where() + " Loaded keeps claim rows");
            assertEquals(revisionBefore, ledger.revision(), where() + " Loaded does not bump claims");
        }

        private void removeInstance() {
            List<StructureInstance> instances = manager.instances();
            if (instances.isEmpty()) {
                return;
            }
            StructureInstance instance = instances.get(random.nextInt(instances.size()));
            World world = instance.id().world();
            StructureSetTestSupport.FakeLedger ledger = Objects.requireNonNull(ledgers.get(world));
            ClaimVerdict released = ledger.release(instance.id());
            assertInstanceOf(ClaimVerdict.Settled.class, released, where() + " removal releases the instance rows");
            revision(world, 1);
            claimModel(world).remove(tuple(instance.id()));
            StructureContext removed = manager.remove(instance.id());
            assertEquals(instance.context(), removed, where() + " remove returns the formed context");
            assertNull(manager.find(instance.id()), where() + " remove drops the instance");
            assertTrue(
                Objects.requireNonNull(loaded.get(world)).removeIf(candidate -> candidate.id().equals(instance.id())),
                where() + " the saved instance is dropped"
            );
        }

        private void assertInvariants(int step) {
            String where = where() + " step=" + step;
            List<StructureInstance> instances = manager.instances();
            Set<InstanceId> ids = new HashSet<>();
            Set<String> tuples = new HashSet<>();
            InstanceId previous = null;
            for (StructureInstance instance : instances) {
                assertTrue(ids.add(instance.id()), where + " duplicate id");
                assertTrue(
                    tuples.add(
                        instance.id().world().id().text() + "|" + instance.owner().id().text() + "|" + instance.siting()
                    ),
                    where + " one formed instance per (world, owner, siting)"
                );
                assertEquals(instance.id(), instance.context().instance(), where + " context identity");
                assertEquals(instance, manager.find(instance.id()), where + " find(id)");
                assertEquals(
                    instance,
                    manager.find(instance.id().world(), instance.owner(), instance.siting()),
                    where + " world-scoped find"
                );
                CuboidSet expectedWorldBounds = Objects.requireNonNull(instance.siting().toWorld(bounds)).asSet();
                assertEquals(expectedWorldBounds, instance.bounds(), where + " bounds are world-frame");
                LatticePoint worldOrigin = Objects.requireNonNull(instance.siting().toWorld(LatticePoint.origin));
                assertTrue(instance.bounds().contains(worldOrigin), where + " bounds contain the translated origin");
                assertTrue(
                    manager.findAt(worldOrigin).stream().anyMatch(candidate -> candidate.id().equals(instance.id())),
                    where + " findAt distinguishes the translated siting"
                );
                assertTrue(
                    manager.findIn(CuboidSet.of(Cuboid.of(worldOrigin))).stream()
                            .anyMatch(candidate -> candidate.id().equals(instance.id())),
                    where + " findIn distinguishes the translated siting"
                );
                assertFalse(instance.bounds().contains(LatticePoint.origin), where + " bounds stay in world frame");
                if (previous != null) {
                    assertTrue(previous.compareTo(instance.id()) < 0, where + " instances() sorted by id");
                }
                previous = instance.id();
            }
            for (int probe = 0; probe < 10; probe++) {
                LatticePoint cell = LatticePoint
                        .of(99 + random.nextInt(7), 10 + random.nextInt(2), 99 + random.nextInt(7));
                assertEquals(bruteFindAt(instances, cell), manager.findAt(cell), where + " findAt " + cell);
                assertEquals(
                    bruteFindAt(instances, cell),
                    manager.findIn(CuboidSet.of(Cuboid.of(cell))),
                    where + " findIn single cell"
                );
            }
            for (int probe = 0; probe < 5; probe++) {
                Cuboid box = Cuboid.of(
                    99 + random.nextInt(4),
                    10,
                    99 + random.nextInt(4),
                    103 + random.nextInt(4),
                    11 + random.nextInt(2),
                    103 + random.nextInt(4)
                );
                assertEquals(bruteFindIn(instances, box.asSet()), manager.findIn(box.asSet()), where + " findIn box");
            }
            for (World world : worlds) {
                assertLedger(world);
                Map<String, List<ClaimRow>> worldClaims = claimModel(world);
                List<StructureInstance> saved = Objects.requireNonNull(loaded.get(world));
                if (!unloaded.contains(world)) {
                    Set<InstanceId> savedIds = new HashSet<>();
                    for (StructureInstance savedInstance : saved) {
                        savedIds.add(savedInstance.id());
                    }
                    Set<InstanceId> formedIds = new HashSet<>();
                    for (StructureInstance instance : manager.instances()) {
                        if (instance.id().world().equals(world)) {
                            formedIds.add(instance.id());
                        }
                    }
                    assertEquals(
                        savedIds,
                        formedIds,
                        where + " saved and formed instances diverged in " + world + " trace=" + trace
                    );
                }
                for (StructureInstance savedInstance : saved) {
                    assertNotNull(
                        worldClaims.get(tuple(savedInstance.id())),
                        where + " saved instance lost its claim key: " + savedInstance.id() + "@"
                                + savedInstance.siting() + " keys=" + worldClaims.keySet() + " trace=" + trace
                    );
                }
                for (StructureInstance instance : instances) {
                    if (!instance.id().world().equals(world)) {
                        continue;
                    }
                    List<ClaimRow> expected = worldClaims.get(tuple(instance.id()));
                    assertNotNull(
                        expected,
                        where + " a formed instance has a claim key: " + instance.id() + " "
                                + instance.owner().id().text() + "@" + instance.siting() + " keys="
                                + worldClaims.keySet() + " trace=" + trace
                    );
                    assertEquals(
                        expectedRows(sharedBlueprint, instance.siting(), instance.id()),
                        expected,
                        where + " claim rows match the current siting"
                    );
                }
            }
            for (Map.Entry<InstanceId, StructureAttempt> entry : attempts.entrySet()) {
                if (manager.find(entry.getKey()) == null) {
                    assertThrows(
                        IllegalStateException.class,
                        entry.getValue()::abort,
                        where + " closed attempt is one-shot"
                    );
                }
            }
        }

        private void assertLedger(World world) {
            StructureSetTestSupport.FakeLedger ledger = Objects.requireNonNull(ledgers.get(world));
            List<ClaimRow> expected = new ArrayList<>();
            for (List<ClaimRow> rows : claimModel(world).values()) {
                expected.addAll(rows);
            }
            List<ClaimRow> actual = new ArrayList<>(ledger.rows());
            assertEquals(canonical(expected), canonical(actual), where() + " ledger rows of " + world);
            assertEquals(
                Objects.requireNonNull(revisions.get(world)),
                ledger.revision(),
                where() + " ledger revision of " + world
            );
            Set<ClaimRow> unique = new HashSet<>(expected);
            assertEquals(expected.size(), unique.size(), where() + " no duplicate claim rows");
        }

        private List<InstanceId> liveIds() {
            List<InstanceId> live = new ArrayList<>();
            for (StructureInstance instance : manager.instances()) {
                live.add(instance.id());
            }
            return live;
        }

        private void revision(World world, long delta) {
            revisions.put(world, Objects.requireNonNull(revisions.get(world)) + delta);
        }

        private Map<String, List<ClaimRow>> claimModel(World world) {
            return Objects.requireNonNull(claims.get(world));
        }

        private String where() {
            return "seed=" + seed;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Blueprint blueprint(Sharing sharing) {
        Bay bay = Bay.of(bayId, bounds.asSet(), Coverage.everyCell, Footprint.box(), sharing);
        return Blueprint.builder(blueprintId).bounds(bounds)
                .check(Check.of(id("check"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant())))
                .build();
    }

    private static @Nullable StructureInstance form(
        StructureManager manager,
        StructureSetTestSupport.FakeLedger ledger,
        StructureSetTestSupport.TestView view,
        Blueprint blueprint,
        World world,
        OwnerId owner,
        Siting siting,
        long seed
    ) {
        StructureAttempt attempt = manager.begin(world, new StructureSpec(owner, siting, blueprint.id()));
        Cuboid worldBounds = Objects.requireNonNull(siting.toWorld(blueprint.bounds()));
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new StructureSetTestSupport.TestLease(view),
            attempt.id(),
            owner,
            Budget.unbounded(),
            ledger.read(worldBounds)
        );
        MatchResult checked = MatchEngine.check(request);
        if (!(checked instanceof MatchResult.Admitted admitted)) {
            attempt.abort();
            throw new AssertionError("seed=" + seed + " form check failed: " + checked);
        }
        MatchResult settled = MatchEngine.settle(request, admitted, ledger, attempt);
        if (!(settled instanceof MatchResult.Formed)) {
            attempt.abort();
            throw new AssertionError("seed=" + seed + " form settle failed: " + settled);
        }
        StructureInstance instance = attempt.commit(worldBounds.asSet());
        assertEquals(worldBounds.asSet(), instance.bounds(), "seed=" + seed + " commit uses world-frame bounds");
        return instance;
    }

    private static boolean hasCode(MatchResult result, CodeEntry code) {
        return result.findings().findings().stream().anyMatch(finding -> finding.code().equals(code.code()));
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("expanded_instances", path);
    }

    private static String tuple(InstanceId instance) {
        return instance.world().id().text() + "#" + instance.serial();
    }

    private static List<ClaimRow> expectedRows(Blueprint blueprint, Siting siting, InstanceId instance) {
        List<ClaimRow> rows = new ArrayList<>();
        for (Bay bay : blueprint.bays()) {
            for (Cuboid box : bay.cells().boxes()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            LatticePoint point = Objects.requireNonNull(siting.toWorld(LatticePoint.of(x, y, z)));
                            rows.add(new ClaimRow(LatticeKey.of(point), bay.sharing(), siting, bay.id(), instance));
                        }
                    }
                }
            }
        }
        rows.sort(null);
        return List.copyOf(rows);
    }

    private static List<ClaimRow> ownRows(MatchResult.Formed formed, Blueprint blueprint, Siting siting) {
        List<ClaimRow> rows = new ArrayList<>();
        for (ClaimRow row : formed.claims().rows()) {
            if (row.instance().equals(formed.instance()) && row.siting().equals(siting) && row.bay().equals(bayId)) {
                rows.add(row);
            }
        }
        rows.sort(null);
        return List.copyOf(rows);
    }

    private static List<ClaimRow> canonical(List<ClaimRow> rows) {
        List<ClaimRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(ClaimRow::toString));
        return List.copyOf(sorted);
    }

    private static void assertOtherWorlds(
        StructureManager manager,
        Map<World, List<StructureInstance>> expected,
        String where
    ) {
        for (Map.Entry<World, List<StructureInstance>> entry : expected.entrySet()) {
            assertEquals(
                entry.getValue(),
                manager.instances().stream().filter(instance -> instance.id().world().equals(entry.getKey())).toList(),
                where + " " + entry.getKey()
            );
        }
    }

    private static List<StructureInstance> bruteFindAt(List<StructureInstance> instances, LatticePoint cell) {
        List<StructureInstance> found = new ArrayList<>();
        for (StructureInstance instance : instances) {
            if (instance.bounds().contains(cell)) {
                found.add(instance);
            }
        }
        found.sort(Comparator.comparing(StructureInstance::id));
        return List.copyOf(found);
    }

    private static List<StructureInstance> bruteFindIn(List<StructureInstance> instances, CuboidSet cells) {
        List<StructureInstance> found = new ArrayList<>();
        for (StructureInstance instance : instances) {
            if (cells.intersects(instance.bounds())) {
                found.add(instance);
            }
        }
        found.sort(Comparator.comparing(StructureInstance::id));
        return List.copyOf(found);
    }

}
