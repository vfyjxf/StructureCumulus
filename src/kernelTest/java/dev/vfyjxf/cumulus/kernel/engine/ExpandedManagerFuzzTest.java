package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expanded seeded fuzz of the world-scoped structure manager: many instances with overlapping bounds,
 * the same {@code (owner, siting)} in different worlds, drafts at every stage, lifecycle events,
 * per-world {@code Loaded}/{@code Unloaded}, context publication isolation and {@code findAt}/{@code findIn}
 * consistency. Every failure names the seed and step that produced it.
 */
class ExpandedManagerFuzzTest {

    private static final long[] seeds = {2L, 3L, 5L, 7L, 11L, 13L};
    private static final World[] worlds = {TestWorld.world, World.of(id("world_b")), World.of(id("world_c"))};
    private static final OwnerId[] owners = {OwnerId.of(id("owner_a")), OwnerId.of(id("owner_b"))};
    private static final NamespacedId[] blueprints = {id("blueprint_a"), id("blueprint_b"), id("blueprint_c")};
    private static final Siting[] sitings = {Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity),
            Siting.of(LatticePoint.of(1, 0, 0), Orientation.identity),
            Siting.of(LatticePoint.of(2, 0, 0), Orientation.identity),
            Siting.of(LatticePoint.of(0, 0, 1), Orientation.identity),
            Siting.of(LatticePoint.of(1, 0, 1), Orientation.identity),
            Siting.of(LatticePoint.of(20, 0, 20), Orientation.identity),
            Siting.of(LatticePoint.of(21, 0, 20), Orientation.identity)};
    private static final ContextKey<Long> countA = ContextKeys.of(id("count_a"), Long.class, Retention.instance);
    private static final ContextKey<Long> countB = ContextKeys.of(id("count_b"), Long.class, Retention.instance);
    private static final ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);
    private static final ContextKey<Long> matchKey = ContextKeys.of(id("match"), Long.class, Retention.match);

    @Test
    void expandedManagerScriptsKeepTheWorldScopedRegistryLaws() {
        for (long seed : seeds) {
            new ExpandedFuzz(seed).run(170);
        }
    }

    @Test
    void manyOverlappingInstancesAnswerFindAtFindInAndAmbiguityConsistently() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            StructureManager manager = StructureManager.create();
            Map<InstanceId, StructureAttempt> drafts = new LinkedHashMap<>();
            Set<String> expectedTuples = new HashSet<>();
            for (int index = 0; index < 24; index++) {
                String where = "seed=" + seed + " instance=" + index;
                World world = worlds[random.nextInt(worlds.length)];
                OwnerId owner = owners[random.nextInt(owners.length)];
                Siting siting = sitings[random.nextInt(4)];
                StructureAttempt attempt = manager.begin(world, new StructureSpec(owner, siting, blueprints[0]));
                drafts.put(attempt.id(), attempt);
                StructureContext context = attempt.publish(
                    attempt.id(),
                    Map.of(countA, (long) index),
                    FindingBundle.empty(),
                    new Spend(index % 3, 0)
                );
                assertEquals(1L, context.revision(), where + " one publish per draft");
                StructureInstance instance = attempt.commit(boundsAt(siting, random.nextInt(3)));
                expectedTuples.add(world.id().text() + "|" + owner.id().text() + "|" + siting);
                assertEquals(world, instance.id().world(), where);
                assertEquals(context, instance.context(), where + " commit carries the draft context");
                assertEquals(new Spend(index % 3, 0), instance.context().spend(), where + " draft spend");
                assertOnePerWorldTuple(manager, instance, where);
            }
            assertEquals(
                expectedTuples.size(),
                manager.instances().size(),
                "seed=" + seed + " one formed instance per (world, owner, siting) tuple"
            );
            for (int probe = 0; probe < 40; probe++) {
                LatticePoint cell = LatticePoint.of(random.nextInt(24), 0, random.nextInt(24));
                List<StructureInstance> expected = bruteFindAt(manager.instances(), cell);
                assertEquals(expected, manager.findAt(cell), "seed=" + seed + " findAt " + cell);
                assertEquals(
                    expected,
                    manager.findIn(CuboidSet.of(Cuboid.of(cell))),
                    "seed=" + seed + " findIn single cell " + cell
                );
            }
            for (int probe = 0; probe < 20; probe++) {
                Cuboid box = Cuboid.of(
                    random.nextInt(10),
                    0,
                    random.nextInt(10),
                    random.nextInt(10) + 12,
                    1,
                    random.nextInt(10) + 12
                );
                assertEquals(
                    bruteFindIn(manager.instances(), box.asSet()),
                    manager.findIn(box.asSet()),
                    "seed=" + seed + " findIn box " + box
                );
            }
            for (StructureInstance instance : manager.instances()) {
                assertEquals(instance, manager.find(instance.id()), "seed=" + seed + " find(id)");
                assertEquals(
                    instance,
                    manager.find(instance.id().world(), instance.owner(), instance.siting()),
                    "seed=" + seed + " find(world, owner, siting)"
                );
            }
            Map<String, List<StructureInstance>> byTuple = new HashMap<>();
            for (StructureInstance instance : manager.instances()) {
                byTuple.computeIfAbsent(
                    instance.owner().id().text() + "@" + instance.siting(),
                    ignored -> new ArrayList<>()
                ).add(instance);
            }
            for (Map.Entry<String, List<StructureInstance>> entry : byTuple.entrySet()) {
                List<StructureInstance> tuple = entry.getValue();
                StructureInstance first = tuple.get(0);
                if (tuple.size() == 1) {
                    assertEquals(
                        first,
                        manager.find(first.owner(), first.siting()),
                        "seed=" + seed + " unambiguous find"
                    );
                } else {
                    assertTrue(tuple.size() > 1, "seed=" + seed + " multi-world tuple");
                    assertThrows(
                        IllegalArgumentException.class,
                        () -> manager.find(first.owner(), first.siting()),
                        "seed=" + seed + " ambiguous find(owner, siting) must throw"
                    );
                }
            }
            for (Map.Entry<InstanceId, StructureAttempt> entry : drafts.entrySet()) {
                assertThrows(
                    IllegalStateException.class,
                    entry.getValue()::abort,
                    "seed=" + seed + " commit is one-shot"
                );
            }
            manager.onEvent(new StructureEvent.Unloaded(TestWorld.world));
            manager.onEvent(new StructureEvent.Unloaded(worlds[1]));
            manager.onEvent(new StructureEvent.Unloaded(worlds[2]));
            assertTrue(manager.instances().isEmpty(), "seed=" + seed + " unload clears every world");
        }
    }

    @Test
    void contextPublicationIsIsolatedAcrossManyInstancesAndDrafts() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            StructureManager manager = StructureManager.create();
            Map<InstanceId, StructureAttempt> attempts = new LinkedHashMap<>();
            Set<InstanceId> live = new HashSet<>();
            for (int index = 0; index < 14; index++) {
                World world = worlds[index % worlds.length];
                Siting siting = sitings[index % sitings.length];
                StructureAttempt attempt = manager.begin(
                    world,
                    new StructureSpec(owners[index % owners.length], siting, blueprints[index % blueprints.length])
                );
                attempts.put(attempt.id(), attempt);
                if (index % 3 == 0) {
                    live.add(attempt.id());
                } else {
                    attempt.commit(boundsAt(siting, index % 3));
                    live.add(attempt.id());
                }
            }
            for (int round = 0; round < 30; round++) {
                String where = "seed=" + seed + " round=" + round;
                InstanceId target = new ArrayList<>(live).get(random.nextInt(live.size()));
                Map<InstanceId, StructureContext> before = snapshotContexts(manager, live);
                StructureContext base = Objects.requireNonNull(before.get(target));
                Map<ContextKey<?>, Object> values = randomValues(random);
                Spend spend = new Spend(random.nextInt(3), random.nextInt(2));
                StructureContext after = manager.publish(target, values, FindingBundle.empty(), spend);
                assertEquals(base.revision() + 1, after.revision(), where + " revision");
                assertEquals(base.spend().plus(spend), after.spend(), where + " spend");
                for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
                    assertEquals(entry.getValue(), after.values().get(entry.getKey()), where + " merged value");
                }
                for (InstanceId other : live) {
                    if (!other.equals(target)) {
                        assertEquals(before.get(other), manager.context(other), where + " context isolation " + other);
                    }
                }
                StructureContext locked = manager.context(target);
                assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.publish(target, Map.of(matchKey, 1L), FindingBundle.empty(), Spend.zero),
                    where + " match values are never published"
                );
                assertEquals(locked, manager.context(target), where + " a rejected publish writes nothing");
                for (Map.Entry<InstanceId, StructureAttempt> entry : attempts.entrySet()) {
                    InstanceId draftId = entry.getKey();
                    boolean draftOpen = live.contains(draftId) && manager.find(draftId) == null;
                    if (draftOpen && !draftId.equals(target)) {
                        StructureAttempt draft = entry.getValue();
                        assertThrows(
                            IllegalArgumentException.class,
                            () -> draft.publish(target, values, FindingBundle.empty(), Spend.zero),
                            where + " a draft publishes only its own instance"
                        );
                        break;
                    }
                }
            }
            for (StructureInstance instance : manager.instances()) {
                assertEquals(instance.context(), manager.context(instance.id()), "seed=" + seed + " formed context");
            }
        }
    }

    @Test
    void loadedAndUnloadedTouchOnlyTheirWorldAndCloseItsDrafts() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            StructureManager manager = StructureManager.create();
            Map<InstanceId, StructureAttempt> attempts = new LinkedHashMap<>();
            Map<World, List<StructureInstance>> formed = new LinkedHashMap<>();
            for (World world : worlds) {
                formed.put(world, new ArrayList<>());
            }
            for (int index = 0; index < 12; index++) {
                World world = worlds[index % worlds.length];
                Siting siting = sitings[index % 4];
                StructureAttempt attempt = manager
                        .begin(world, new StructureSpec(owners[index % owners.length], siting, blueprints[0]));
                attempts.put(attempt.id(), attempt);
                StructureInstance instance = attempt.commit(boundsAt(siting, index % 3));
                Objects.requireNonNull(formed.get(world)).add(instance);
            }
            World target = worlds[random.nextInt(worlds.length)];
            Map<World, List<StructureInstance>> otherBefore = new LinkedHashMap<>();
            for (World world : worlds) {
                if (!world.equals(target)) {
                    otherBefore.put(
                        world,
                        List.copyOf(
                            manager.instances().stream().filter(instance -> instance.id().world().equals(world))
                                    .toList()
                        )
                    );
                }
            }
            StructureAttempt openDraft = manager.begin(
                target,
                new StructureSpec(owners[0], Siting.of(LatticePoint.of(40, 0, 40), Orientation.identity), blueprints[0])
            );
            attempts.put(openDraft.id(), openDraft);
            List<InstanceId> closedDraftIds = new ArrayList<>();
            closedDraftIds.add(openDraft.id());

            manager.onEvent(new StructureEvent.Unloaded(target));
            assertTrue(
                manager.instances().stream().noneMatch(instance -> instance.id().world().equals(target)),
                "seed=" + seed + " unload removes only the target world"
            );
            for (Map.Entry<World, List<StructureInstance>> entry : otherBefore.entrySet()) {
                assertEquals(
                    entry.getValue(),
                    manager.instances().stream().filter(instance -> instance.id().world().equals(entry.getKey()))
                            .toList(),
                    "seed=" + seed + " unload keeps the other world"
                );
            }
            for (InstanceId closed : closedDraftIds) {
                assertThrows(
                    IllegalStateException.class,
                    Objects.requireNonNull(attempts.get(closed))::abort,
                    "seed=" + seed + " unloaded draft is closed"
                );
            }

            List<StructureInstance> restored = new ArrayList<>(formed.get(target));
            List<StructureInstance> keep = new ArrayList<>();
            for (StructureInstance instance : restored) {
                if (random.nextBoolean()) {
                    keep.add(instance);
                }
            }
            keep.sort(Comparator.comparing(StructureInstance::id));
            manager.onEvent(new StructureEvent.Loaded(target, keep));
            List<StructureInstance> expected = new ArrayList<>(keep);
            for (World world : worlds) {
                if (!world.equals(target)) {
                    expected.addAll(otherBefore.get(world));
                }
            }
            expected.sort(Comparator.comparing(StructureInstance::id));
            assertEquals(expected, manager.instances(), "seed=" + seed + " Loaded replaces only the target world");
            for (StructureInstance instance : keep) {
                assertEquals(instance, manager.find(instance.id()), "seed=" + seed + " restored instance is findable");
                assertEquals(
                    instance,
                    manager.find(target, instance.owner(), instance.siting()),
                    "seed=" + seed + " restored siting is registered"
                );
            }
            long loadedMax = keep.stream().mapToLong(instance -> instance.id().serial()).max().orElse(0L);
            Siting free = Siting.of(LatticePoint.of(60, 0, 60), Orientation.identity);
            StructureAttempt probe = manager.begin(target, new StructureSpec(owners[1], free, blueprints[1]));
            assertTrue(probe.id().serial() > loadedMax, "seed=" + seed + " Loaded raises the serial watermark");
            probe.abort();

            List<StructureInstance> beforeReject = manager.instances();
            StructureInstance foreign = new StructureInstance(
                new InstanceId(worlds[0].equals(target) ? worlds[1] : worlds[0], loadedMax + 40L),
                new StructureSpec(owners[0], sitings[0], blueprints[0]),
                StructureContext
                        .empty(new InstanceId(worlds[0].equals(target) ? worlds[1] : worlds[0], loadedMax + 40L)),
                boundsAt(sitings[0], 0)
            );
            List<StructureInstance> rejected = new ArrayList<>(keep);
            rejected.add(foreign);
            assertThrows(
                IllegalArgumentException.class,
                () -> manager.onEvent(new StructureEvent.Loaded(target, rejected)),
                "seed=" + seed + " a loaded instance from another world is rejected"
            );
            assertEquals(beforeReject, manager.instances(), "seed=" + seed + " a rejected Loaded is atomic");
        }
    }

    @Test
    void expandedEventSequencesLeakNoDraftsAndStayOneShot() {
        for (long seed : seeds) {
            Random random = new Random(seed);
            StructureManager manager = StructureManager.create();
            Map<InstanceId, StructureAttempt> attempts = new LinkedHashMap<>();
            Set<InstanceId> open = new HashSet<>();
            for (int index = 0; index < 20; index++) {
                World world = worlds[index % worlds.length];
                Siting siting = sitings[index % sitings.length];
                StructureAttempt attempt = manager
                        .begin(world, new StructureSpec(owners[index % owners.length], siting, blueprints[0]));
                attempts.put(attempt.id(), attempt);
                int choice = random.nextInt(4);
                if (choice == 0) {
                    open.add(attempt.id());
                } else if (choice == 1) {
                    attempt.commit(boundsAt(siting, index % 3));
                } else if (choice == 2) {
                    attempt.abort();
                } else {
                    manager.remove(attempt.id());
                }
            }
            for (Map.Entry<InstanceId, StructureAttempt> entry : attempts.entrySet()) {
                if (!open.contains(entry.getKey())) {
                    assertThrows(
                        IllegalStateException.class,
                        entry.getValue()::abort,
                        "seed=" + seed + " closed attempt is one-shot"
                    );
                }
            }
            for (InstanceId draft : open) {
                manager.onEvent(new StructureEvent.Broken(draft));
                assertThrows(
                    IllegalStateException.class,
                    Objects.requireNonNull(attempts.get(draft))::abort,
                    "seed=" + seed + " Broken closes the draft"
                );
            }
            open.clear();
            for (World world : worlds) {
                manager.onEvent(new StructureEvent.Unloaded(world));
            }
            assertTrue(manager.instances().isEmpty(), "seed=" + seed + " unload clears the registry");
            for (Map.Entry<InstanceId, StructureAttempt> entry : attempts.entrySet()) {
                assertThrows(
                    IllegalStateException.class,
                    entry.getValue()::abort,
                    "seed=" + seed + " no draft survives unload"
                );
            }
        }
    }

    // ------------------------------------------------------------------ expanded random script

    private static final class ExpandedFuzz {

        private final long seed;
        private final Random random;
        private final StructureManager manager = StructureManager.create();
        private final Map<InstanceId, StructureAttempt> attempts = new LinkedHashMap<>();
        private final Map<InstanceId, StructureSpec> open = new LinkedHashMap<>();
        private final Set<InstanceId> known = new LinkedHashSet<>();
        private long lastSerial;

        private ExpandedFuzz(long seed) {
            this.seed = seed;
            this.random = new Random(seed);
        }

        private void run(int steps) {
            for (int step = 0; step < steps; step++) {
                int op = random.nextInt(13);
                switch (op) {
                    case 0, 1, 2, 3 -> beginDraft();
                    case 4, 5, 6 -> commitDraft();
                    case 7 -> abortDraft();
                    case 8 -> brokenOrInvalidated();
                    case 9 -> unloadWorld();
                    case 10 -> loadedWorld();
                    case 11 -> removeTarget();
                    default -> publishTarget();
                }
                assertRegistry(step);
            }
            for (World world : worlds) {
                manager.onEvent(new StructureEvent.Unloaded(world));
            }
            assertTrue(manager.instances().isEmpty(), "seed=" + seed + " final unload");
            for (Map.Entry<InstanceId, StructureAttempt> entry : attempts.entrySet()) {
                assertThrows(
                    IllegalStateException.class,
                    entry.getValue()::abort,
                    "seed=" + seed + " final draft closure"
                );
            }
        }

        private void beginDraft() {
            if (open.size() >= 4) {
                return;
            }
            World world = worlds[random.nextInt(worlds.length)];
            OwnerId owner = owners[random.nextInt(owners.length)];
            Siting siting = sitings[random.nextInt(sitings.length)];
            StructureSpec spec = new StructureSpec(owner, siting, blueprints[random.nextInt(blueprints.length)]);
            boolean duplicateDraft = open.entrySet().stream().anyMatch(
                entry -> entry.getKey().world().equals(world)
                        && entry.getValue().owner().equals(owner)
                        && entry.getValue().siting().equals(siting)
            );
            String where = where();
            if (duplicateDraft) {
                assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.begin(world, spec),
                    where + " duplicate draft tuple"
                );
                return;
            }
            StructureAttempt attempt = manager.begin(world, spec);
            assertEquals(world, attempt.id().world(), where + " instance world");
            assertTrue(attempt.id().serial() > lastSerial, where + " serial monotonic");
            lastSerial = attempt.id().serial();
            open.put(attempt.id(), spec);
            attempts.put(attempt.id(), attempt);
            known.add(attempt.id());
        }

        private void commitDraft() {
            if (open.isEmpty()) {
                return;
            }
            InstanceId id = randomElement(new ArrayList<>(open.keySet()));
            StructureAttempt attempt = Objects.requireNonNull(attempts.get(id));
            StructureSpec spec = Objects.requireNonNull(open.get(id));
            StructureInstance previous = manager.find(id.world(), spec.owner(), spec.siting());
            StructureContext context = attempt.context(id);
            StructureInstance instance = attempt.commit(boundsAt(spec.siting(), random.nextInt(3)));
            open.remove(id);
            assertEquals(id, instance.id(), where() + " commit keeps the id");
            assertEquals(context, instance.context(), where() + " commit moves the draft context");
            if (previous != null) {
                assertNull(manager.find(previous.id()), where() + " commit replaces the previous formed instance");
                assertEquals(
                    0L,
                    manager.context(previous.id()).revision(),
                    where() + " the replaced context is removed"
                );
            }
            assertEquals(
                instance,
                manager.find(id.world(), spec.owner(), spec.siting()),
                where() + " the tuple is registered"
            );
            assertThrows(IllegalStateException.class, attempt::abort, where() + " commit is one-shot");
        }

        private void abortDraft() {
            if (open.isEmpty()) {
                return;
            }
            InstanceId id = randomElement(new ArrayList<>(open.keySet()));
            StructureAttempt attempt = Objects.requireNonNull(attempts.get(id));
            StructureSpec spec = Objects.requireNonNull(open.get(id));
            attempt.abort();
            open.remove(id);
            assertNull(manager.find(id), where() + " an aborted draft leaves no instance");
            assertEquals(0L, manager.context(id).revision(), where() + " an aborted draft leaves no context");
            assertThrows(IllegalStateException.class, attempt::abort, where() + " abort is one-shot");
            StructureAttempt replacement = manager.begin(id.world(), spec);
            assertTrue(replacement.id().serial() > lastSerial, where() + " the released tuple allocates a new serial");
            lastSerial = replacement.id().serial();
            replacement.abort();
        }

        private void publishTarget() {
            List<InstanceId> live = liveIds();
            if (live.isEmpty()) {
                return;
            }
            InstanceId target = randomElement(live);
            Map<InstanceId, StructureContext> before = snapshotContexts(live);
            StructureContext base = Objects.requireNonNull(before.get(target));
            Map<ContextKey<?>, Object> values = randomValues();
            Spend spend = new Spend(random.nextInt(3), random.nextInt(2));
            StructureContext after = manager.publish(target, values, FindingBundle.empty(), spend);
            assertEquals(base.revision() + 1, after.revision(), where() + " revision increments");
            assertEquals(base.spend().plus(spend), after.spend(), where() + " spend accumulates");
            for (InstanceId other : live) {
                if (!other.equals(target)) {
                    assertEquals(before.get(other), manager.context(other), where() + " context isolation " + other);
                }
            }
            StructureContext locked = manager.context(target);
            assertThrows(
                IllegalArgumentException.class,
                () -> manager.publish(target, Map.of(matchKey, 1L), FindingBundle.empty(), Spend.zero),
                where() + " match values are never published"
            );
            assertEquals(locked, manager.context(target), where() + " a rejected publish writes nothing");
        }

        private void brokenOrInvalidated() {
            InstanceId id = known.isEmpty() || random.nextInt(6) == 0
                    ? new InstanceId(worlds[random.nextInt(worlds.length)], lastSerial + 500L + random.nextInt(100))
                    : randomElement(new ArrayList<>(known));
            boolean draft = open.containsKey(id);
            StructureInstance formed = manager.find(id);
            StructureContext context = manager.context(id);
            manager.onEvent(new StructureEvent.Broken(id));
            manager.onEvent(new StructureEvent.Invalidated(id, "again"));
            assertNull(manager.find(id), where() + " Broken removes the instance and is idempotent");
            assertEquals(0L, manager.context(id).revision(), where() + " a removed instance reads empty");
            if (draft) {
                open.remove(id);
                assertThrows(
                    IllegalStateException.class,
                    Objects.requireNonNull(attempts.get(id))::abort,
                    where() + " Broken closes the draft"
                );
            }
            if (formed != null) {
                assertEquals(formed.context(), context, where() + " the removed context is the formed one");
            }
        }

        private void unloadWorld() {
            World target = worlds[random.nextInt(worlds.length)];
            List<StructureInstance> otherBefore = new ArrayList<>();
            for (StructureInstance instance : manager.instances()) {
                if (!instance.id().world().equals(target)) {
                    otherBefore.add(instance);
                }
            }
            List<InstanceId> closedDrafts = new ArrayList<>();
            for (InstanceId id : open.keySet()) {
                if (id.world().equals(target)) {
                    closedDrafts.add(id);
                }
            }
            manager.onEvent(new StructureEvent.Unloaded(target));
            List<StructureInstance> otherAfter = new ArrayList<>();
            for (StructureInstance instance : manager.instances()) {
                if (!instance.id().world().equals(target)) {
                    otherAfter.add(instance);
                }
            }
            assertEquals(otherBefore, otherAfter, where() + " Unloaded touches only its world");
            for (InstanceId id : closedDrafts) {
                open.remove(id);
                assertThrows(
                    IllegalStateException.class,
                    Objects.requireNonNull(attempts.get(id))::abort,
                    where() + " Unloaded closes the draft"
                );
            }
        }

        private void loadedWorld() {
            World target = worlds[random.nextInt(worlds.length)];
            List<StructureInstance> otherBefore = new ArrayList<>();
            for (StructureInstance instance : manager.instances()) {
                if (!instance.id().world().equals(target)) {
                    otherBefore.add(instance);
                }
            }
            List<StructureInstance> restored = new ArrayList<>();
            Set<InstanceId> ids = new HashSet<>();
            Set<String> keys = new HashSet<>();
            long maxSerial = lastSerial;
            for (StructureInstance instance : manager.instances()) {
                if (instance.id().world().equals(target) && random.nextBoolean()) {
                    restored.add(instance);
                    ids.add(instance.id());
                    keys.add(tuple(instance.spec()));
                    maxSerial = Math.max(maxSerial, instance.id().serial());
                }
            }
            int extra = random.nextInt(3);
            for (int index = 0; index < extra; index++) {
                long serial = maxSerial + 1 + index;
                Siting siting = null;
                for (int probe = 0; probe < sitings.length; probe++) {
                    Siting candidate = sitings[(index + probe) % sitings.length];
                    if (!keys.contains(tuple(new StructureSpec(owners[0], candidate, blueprints[0])))) {
                        siting = candidate;
                        break;
                    }
                }
                if (siting == null) {
                    break;
                }
                InstanceId id = new InstanceId(target, serial);
                StructureSpec spec = new StructureSpec(owners[0], siting, blueprints[0]);
                restored.add(new StructureInstance(id, spec, StructureContext.empty(id), boundsAt(siting, 1)));
                ids.add(id);
                keys.add(tuple(spec));
            }
            restored.sort(Comparator.comparing(StructureInstance::id));
            List<StructureInstance> expected = new ArrayList<>(restored);
            expected.addAll(otherBefore);
            expected.sort(Comparator.comparing(StructureInstance::id));
            List<InstanceId> closedDrafts = new ArrayList<>();
            for (InstanceId id : open.keySet()) {
                if (id.world().equals(target)) {
                    closedDrafts.add(id);
                }
            }
            manager.onEvent(new StructureEvent.Loaded(target, restored));
            assertEquals(expected, manager.instances(), where() + " Loaded replaces only its world");
            for (InstanceId id : closedDrafts) {
                open.remove(id);
                assertThrows(
                    IllegalStateException.class,
                    Objects.requireNonNull(attempts.get(id))::abort,
                    where() + " Loaded closes the world's drafts"
                );
            }
            lastSerial = Math.max(lastSerial, maxSerial);
            StructureAttempt probe = manager.begin(
                target,
                new StructureSpec(owners[1], Siting.of(LatticePoint.of(80, 0, 80), Orientation.identity), blueprints[1])
            );
            assertTrue(probe.id().serial() > lastSerial, where() + " Loaded raises the watermark");
            lastSerial = probe.id().serial();
            probe.abort();
        }

        private void removeTarget() {
            InstanceId id = known.isEmpty() || random.nextInt(5) == 0
                    ? new InstanceId(worlds[random.nextInt(worlds.length)], lastSerial + 700L + random.nextInt(100))
                    : randomElement(new ArrayList<>(known));
            boolean draft = open.containsKey(id);
            StructureInstance formed = manager.find(id);
            StructureContext before = manager.context(id);
            StructureContext removed = manager.remove(id);
            if (formed != null) {
                assertEquals(formed.context(), removed, where() + " remove returns the formed context");
                assertNull(manager.find(id), where() + " remove drops the formed instance");
                assertEquals(0L, manager.context(id).revision(), where() + " the removed instance reads empty");
            } else if (draft) {
                assertEquals(before, removed, where() + " remove returns the draft context");
                open.remove(id);
                assertThrows(
                    IllegalStateException.class,
                    Objects.requireNonNull(attempts.get(id))::abort,
                    where() + " remove closes the draft"
                );
                assertEquals(0L, manager.context(id).revision(), where() + " the removed draft reads empty");
            } else {
                assertNull(removed, where() + " removing an absent instance answers null");
            }
        }

        private void assertRegistry(int step) {
            String where = where() + " step=" + step;
            List<StructureInstance> instances = manager.instances();
            Set<InstanceId> ids = new HashSet<>();
            Map<String, List<StructureInstance>> byTuple = new HashMap<>();
            InstanceId previous = null;
            for (StructureInstance instance : instances) {
                assertTrue(ids.add(instance.id()), where + " duplicate instance id");
                assertEquals(instance.id(), instance.context().instance(), where + " context identity");
                assertEquals(instance, manager.find(instance.id()), where + " find(id)");
                assertEquals(
                    instance,
                    manager.find(instance.id().world(), instance.owner(), instance.siting()),
                    where + " world-scoped find"
                );
                byTuple.computeIfAbsent(
                    instance.owner().id().text() + "@" + instance.siting(),
                    ignored -> new ArrayList<>()
                ).add(instance);
                if (previous != null) {
                    assertTrue(previous.compareTo(instance.id()) < 0, where + " instances() sorted by id");
                }
                previous = instance.id();
            }
            for (Map.Entry<String, List<StructureInstance>> entry : byTuple.entrySet()) {
                List<StructureInstance> tuple = entry.getValue();
                assertTrue(tuple.size() <= worlds.length, where + " one formed instance per (world, owner, siting)");
                for (int worldIndex = 0; worldIndex < worlds.length; worldIndex++) {
                    int count = 0;
                    for (StructureInstance instance : tuple) {
                        if (instance.id().world().equals(worlds[worldIndex])) {
                            count++;
                        }
                    }
                    assertTrue(count <= 1, where + " two formed instances share a world tuple");
                }
                StructureInstance first = tuple.get(0);
                if (tuple.size() == 1) {
                    assertEquals(first, manager.find(first.owner(), first.siting()), where + " unambiguous tuple find");
                } else {
                    assertThrows(
                        IllegalArgumentException.class,
                        () -> manager.find(first.owner(), first.siting()),
                        where + " ambiguous tuple find throws"
                    );
                }
            }
            for (InstanceId draft : open.keySet()) {
                assertNull(manager.find(draft), where + " an open draft is not formed");
                assertEquals(draft, manager.context(draft).instance(), where + " draft context identity");
            }
            for (Map.Entry<InstanceId, StructureAttempt> entry : attempts.entrySet()) {
                if (!open.containsKey(entry.getKey()) && manager.find(entry.getKey()) == null) {
                    assertThrows(
                        IllegalStateException.class,
                        entry.getValue()::abort,
                        where + " closed attempt one-shot"
                    );
                }
            }
            for (InstanceId id : known) {
                if (!open.containsKey(id) && manager.find(id) == null) {
                    assertEquals(0L, manager.context(id).revision(), where + " removed context is empty");
                }
            }
            for (int probe = 0; probe < 6; probe++) {
                LatticePoint cell = LatticePoint.of(random.nextInt(26), 0, random.nextInt(26));
                assertEquals(bruteFindAt(instances, cell), manager.findAt(cell), where + " findAt " + cell);
                assertEquals(
                    bruteFindAt(instances, cell),
                    manager.findIn(CuboidSet.of(Cuboid.of(cell))),
                    where + " findIn single cell"
                );
            }
            for (int probe = 0; probe < 3; probe++) {
                Cuboid box = Cuboid
                        .of(random.nextInt(8), 0, random.nextInt(8), random.nextInt(8) + 10, 1, random.nextInt(8) + 10);
                assertEquals(bruteFindIn(instances, box.asSet()), manager.findIn(box.asSet()), where + " findIn box");
            }
        }

        private List<InstanceId> liveIds() {
            List<InstanceId> live = new ArrayList<>();
            for (StructureInstance instance : manager.instances()) {
                live.add(instance.id());
            }
            live.addAll(open.keySet());
            return live;
        }

        private Map<InstanceId, StructureContext> snapshotContexts(List<InstanceId> live) {
            Map<InstanceId, StructureContext> snapshot = new LinkedHashMap<>();
            for (InstanceId id : live) {
                snapshot.put(id, manager.context(id));
            }
            return snapshot;
        }

        private Map<ContextKey<?>, Object> randomValues() {
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

        private String where() {
            return "seed=" + seed;
        }

        private <T> T randomElement(List<T> values) {
            return values.get(random.nextInt(values.size()));
        }

        private static String tuple(StructureSpec spec) {
            return spec.owner().id().text() + "@" + spec.siting();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Map<InstanceId, StructureContext> snapshotContexts(StructureManager manager, Set<InstanceId> live) {
        Map<InstanceId, StructureContext> snapshot = new LinkedHashMap<>();
        for (InstanceId id : live) {
            snapshot.put(id, manager.context(id));
        }
        return snapshot;
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

    private static void assertOnePerWorldTuple(StructureManager manager, StructureInstance instance, String where) {
        List<StructureInstance> all = manager.instances();
        int count = 0;
        for (StructureInstance candidate : all) {
            if (candidate.owner().equals(instance.owner())
                    && candidate.siting().equals(instance.siting())
                    && candidate.id().world().equals(instance.id().world())) {
                count++;
            }
        }
        assertEquals(1, count, where + " one formed instance per (world, owner, siting)");
        assertEquals(instance, manager.find(instance.id().world(), instance.owner(), instance.siting()), where);
    }

    private static CuboidSet boundsAt(Siting siting, int variant) {
        LatticePoint origin = siting.origin();
        return CuboidSet.of(
            Cuboid.of(
                origin,
                LatticePoint.of(origin.x() + 2 + variant % 2, origin.y() + 1, origin.z() + 2 + variant % 2)
            )
        );
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("expanded", path);
    }
}
