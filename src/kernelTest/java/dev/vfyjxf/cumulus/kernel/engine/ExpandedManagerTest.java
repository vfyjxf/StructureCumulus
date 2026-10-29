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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expanded N-instance manager stress: overlap, worlds, serials, lookups, lifecycle and contexts. */
class ExpandedManagerTest {

    private static final NamespacedId blueprint = NamespacedId.of("expanded", "blueprint");
    private static final OwnerId owner = OwnerId.of(NamespacedId.of("expanded", "owner"));
    private static final ContextKey<Long> count = ContextKeys
            .of(NamespacedId.of("expanded", "count"), Long.class, Retention.instance);
    private static final World world0 = World.of(NamespacedId.of("expanded", "world_0"));

    @Test
    void aHundredOverlappingInstancesAreAllFoundInIdentityOrder() {
        StructureManager manager = StructureManager.create();
        CuboidSet sharedBounds = box(0, 0, 0, 2, 2, 2);
        for (int i = 0; i < 100; i++) {
            form(manager, world0, owner, siting(i), sharedBounds);
        }

        assertEquals(100, manager.instances().size());
        List<StructureInstance> at = manager.findAt(LatticePoint.of(1, 1, 1));
        assertEquals(100, at.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(new InstanceId(world0, i + 1), at.get(i).id(), "findAt is in id order");
        }
        assertEquals(ids(at), ids(manager.findIn(sharedBounds)));
        assertEquals(100, manager.findIn(sharedBounds).size());
        assertEquals(100, manager.findIn(box(1, 1, 1, 2, 2, 2)).size());
    }

    @Test
    void findAtAndFindInMatchHalfOpenBoundsAtScale() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> formed = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            int x = (i % 5) * 2;
            int y = (i / 5) % 3;
            int sx = 1 + i % 3;
            int sy = 1 + (i / 3) % 2;
            CuboidSet bounds = box(x, y, 0, x + sx, y + sy, 1 + i % 2);
            formed.add(form(manager, world0, owner, siting(i), bounds));
        }
        StructureInstance empty = form(manager, world0, owner, siting(60), CuboidSet.empty());

        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 4; y++) {
                List<InstanceId> expected = new ArrayList<>();
                for (StructureInstance instance : formed) {
                    if (instance.bounds().contains(LatticePoint.of(x, y, 0))) {
                        expected.add(instance.id());
                    }
                }
                assertEquals(expected, ids(manager.findAt(LatticePoint.of(x, y, 0))), "findAt at " + x + "," + y);
            }
        }
        List<InstanceId> expectedIntersect = new ArrayList<>();
        for (StructureInstance instance : formed) {
            if (instance.bounds().intersects(box(0, 0, 0, 10, 3, 2))) {
                expectedIntersect.add(instance.id());
            }
        }
        assertEquals(expectedIntersect, ids(manager.findIn(box(0, 0, 0, 10, 3, 2))));
        assertTrue(manager.findIn(CuboidSet.empty()).isEmpty());
        assertTrue(manager.findAt(LatticePoint.of(50, 50, 50)).isEmpty());
        assertTrue(
            manager.findAt(LatticePoint.of(1, 1, 1)).stream().noneMatch(instance -> instance.id().equals(empty.id()))
        );
    }

    @Test
    void replacementAtScaleKeepsOneEntryPerTupleAndInvalidatesOldContexts() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> old = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            StructureInstance instance = form(manager, world0, owner, siting(i), box(i, 0, 0, i + 1, 1, 1));
            manager.publish(instance.id(), Map.of(count, (long) i), FindingBundle.empty(), Spend.zero);
            old.add(instance);
        }

        List<StructureInstance> replacement = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            StructureAttempt attempt = manager.begin(world0, spec(owner, siting(i)));
            replacement.add(attempt.commit(box(100 + i, 0, 0, 101 + i, 1, 1)));
        }

        assertEquals(50, manager.instances().size());
        for (int i = 0; i < 50; i++) {
            assertNull(manager.find(old.get(i).id()), "the old id is replaced");
            assertEquals(0, manager.context(old.get(i).id()).revision(), "the old context is gone");
            assertEquals(replacement.get(i), manager.find(world0, owner, siting(i)));
            assertEquals(1, manager.findAt(LatticePoint.of(100 + i, 0, 0)).size());
            assertTrue(manager.findAt(LatticePoint.of(i, 0, 0)).isEmpty());
        }
    }

    @Test
    void movedBrokenAndInvalidatedAtScaleLeaveExactlyTheExpectedRegistry() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> formed = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            formed.add(form(manager, world0, owner, siting(i), box(i, 0, 0, i + 1, 1, 1)));
        }
        for (int i = 0; i < 10; i++) {
            manager.onEvent(
                new StructureEvent.Moved(
                    formed.get(i).id(),
                    siting(i),
                    siting(100 + i),
                    box(100 + i, 0, 0, 101 + i, 1, 1)
                )
            );
        }
        for (int i = 10; i < 20; i++) {
            manager.onEvent(new StructureEvent.Broken(formed.get(i).id()));
        }
        for (int i = 20; i < 30; i++) {
            manager.onEvent(new StructureEvent.Invalidated(formed.get(i).id(), "scale"));
        }

        assertEquals(20, manager.instances().size());
        for (int i = 0; i < 10; i++) {
            StructureInstance moved = Objects.requireNonNull(manager.find(formed.get(i).id()), "moved");
            assertEquals(siting(100 + i), moved.siting());
            assertEquals(1, manager.findAt(LatticePoint.of(100 + i, 0, 0)).size());
            assertTrue(manager.findAt(LatticePoint.of(i, 0, 0)).isEmpty());
        }
        for (int i = 10; i < 30; i++) {
            assertNull(manager.find(formed.get(i).id()));
        }
        for (int i = 30; i < 40; i++) {
            assertNotNull(manager.find(formed.get(i).id()));
        }
    }

    @Test
    void perWorldLoadedAndUnloadedAtScaleTouchOnlyTheirWorld() {
        StructureManager manager = StructureManager.create();
        List<World> worlds = new ArrayList<>();
        List<List<StructureInstance>> instancesByWorld = new ArrayList<>();
        for (int w = 0; w < 10; w++) {
            World world = world(w);
            worlds.add(world);
            List<StructureInstance> list = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list.add(form(manager, world, owner, siting(w * 100 + i), box(w * 20 + i, 0, 0, w * 20 + i + 1, 1, 1)));
            }
            instancesByWorld.add(list);
        }
        List<StructureAttempt> drafts = new ArrayList<>();
        for (int w = 0; w < 10; w++) {
            drafts.add(manager.begin(worlds.get(w), spec(owner, siting(5000 + w))));
        }

        List<StructureInstance> restored = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            InstanceId id = new InstanceId(worlds.get(0), 1000 + i);
            restored.add(
                new StructureInstance(
                    id,
                    new StructureSpec(owner, siting(7000 + i), blueprint),
                    StructureContext.empty(id),
                    box(900 + i, 0, 0, 901 + i, 1, 1)
                )
            );
        }
        manager.onEvent(new StructureEvent.Loaded(worlds.get(0), restored));

        assertEquals(3 + 9 * 5, manager.instances().size());
        assertEquals(
            5,
            instancesByWorld.get(0).stream().filter(instance -> manager.find(instance.id()) == null).count()
        );
        for (int w = 1; w < 10; w++) {
            for (StructureInstance instance : instancesByWorld.get(w)) {
                assertEquals(instance, manager.find(instance.id()), "world " + w + " is preserved");
            }
        }
        assertThrows(IllegalStateException.class, () -> drafts.get(0).commit(CuboidSet.empty()));
        assertThrows(IllegalStateException.class, drafts.get(0)::abort);
        assertNotNull(drafts.get(1).commit(CuboidSet.empty()), "other worlds' drafts stay open");

        StructureInstance high = restored.get(2);
        StructureAttempt after = manager.begin(worlds.get(1), spec(owner, siting(9000)));
        assertTrue(after.id().serial() > high.id().serial(), "Loaded raises the shared watermark");
        after.abort();

        manager.onEvent(new StructureEvent.Unloaded(worlds.get(2)));
        assertEquals(3 + 8 * 5 + 1, manager.instances().size(), "the committed world-1 draft is extra");
        for (StructureInstance instance : instancesByWorld.get(2)) {
            assertNull(manager.find(instance.id()));
        }
        assertThrows(IllegalStateException.class, () -> drafts.get(2).commit(CuboidSet.empty()));
        assertNotNull(drafts.get(3).commit(CuboidSet.empty()));
    }

    @Test
    void loadedValidatesEveryEntryBeforeReplacingAtScale() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> existing = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            existing.add(form(manager, world0, owner, siting(i), box(i, 0, 0, i + 1, 1, 1)));
        }
        StructureAttempt draft = manager.begin(world0, spec(owner, siting(500)));
        draft.publish(draft.id(), Map.of(count, 7L), FindingBundle.empty(), Spend.zero);

        List<StructureInstance> restored = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            InstanceId id = new InstanceId(world0, 2000 + i);
            restored.add(
                new StructureInstance(
                    id,
                    new StructureSpec(owner, siting(8000 + i), blueprint),
                    StructureContext.empty(id),
                    box(3000 + i, 0, 0, 3001 + i, 1, 1)
                )
            );
        }
        List<StructureInstance> duplicateId = new ArrayList<>(restored);
        duplicateId.add(restored.get(0));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(world0, duplicateId))
        );
        for (StructureInstance instance : existing) {
            assertEquals(instance, manager.find(instance.id()), "rejected duplicate id leaves the registry intact");
        }
        assertEquals(7L, manager.context(draft.id()).get(count), "rejected Loaded leaves drafts open");

        List<StructureInstance> duplicateSiting = new ArrayList<>(restored);
        InstanceId extra = new InstanceId(world0, 3000);
        duplicateSiting.add(
            new StructureInstance(
                extra,
                new StructureSpec(owner, restored.get(0).siting(), blueprint),
                StructureContext.empty(extra),
                box(4000, 0, 0, 4001, 1, 1)
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(world0, duplicateSiting))
        );
        for (StructureInstance instance : existing) {
            assertNotNull(manager.find(instance.id()));
        }

        manager.onEvent(new StructureEvent.Loaded(world0, restored));
        assertEquals(restored.size(), manager.instances().size());
        for (StructureInstance instance : restored) {
            assertEquals(instance, manager.find(instance.id()));
        }
        assertNull(manager.find(existing.get(0).id()));
    }

    @Test
    void serialIsUniqueAndMonotonicAcrossManyAbortsReplacesAndWorlds() {
        StructureManager manager = StructureManager.create();
        Set<Long> serials = new HashSet<>();
        long previous = 0;
        for (int i = 0; i < 100; i++) {
            StructureAttempt attempt = manager.begin(world(i % 3), spec(owner, siting(i)));
            assertTrue(attempt.id().serial() > previous, "serials strictly increase");
            previous = attempt.id().serial();
            assertTrue(serials.add(attempt.id().serial()));
            if (i % 2 == 0) {
                attempt.abort();
            } else {
                attempt.commit(box(i, 0, 0, i + 1, 1, 1));
            }
        }

        List<StructureInstance> restored = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            InstanceId id = new InstanceId(world(9), 50_000 + i);
            restored.add(
                new StructureInstance(
                    id,
                    new StructureSpec(owner, siting(20_000 + i), blueprint),
                    StructureContext.empty(id),
                    box(10_000 + i, 0, 0, 10_001 + i, 1, 1)
                )
            );
        }
        manager.onEvent(new StructureEvent.Loaded(world(9), restored));
        StructureAttempt afterLoad = manager.begin(world(8), spec(owner, siting(30_000)));
        assertTrue(afterLoad.id().serial() > 50_004, "Loaded raises the watermark by the restored max");
        previous = afterLoad.id().serial();
        afterLoad.abort();

        manager.onEvent(new StructureEvent.Unloaded(world(9)));
        StructureAttempt afterUnload = manager.begin(world(9), spec(owner, siting(30_001)));
        assertTrue(afterUnload.id().serial() > previous, "Unloaded never lowers the serial");
        previous = afterUnload.id().serial();
        afterUnload.abort();

        manager.onEvent(new StructureEvent.Loaded(world(9), List.of(restored.get(0))));
        StructureAttempt afterLowerLoad = manager.begin(world(9), spec(owner, siting(30_002)));
        assertTrue(afterLowerLoad.id().serial() > previous, "a lower restored serial never lowers the watermark");
    }

    @Test
    void contextsStayIsolatedAcrossOneHundredInstances() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> formed = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            formed.add(form(manager, world0, owner, siting(i), box(i, 0, 0, i + 1, 1, 1)));
            manager.publish(formed.get(i).id(), Map.of(count, (long) i), FindingBundle.empty(), new Spend(i, i % 3));
        }
        for (int i = 0; i < 100; i++) {
            StructureContext context = manager.context(formed.get(i).id());
            assertEquals((long) i, context.get(count));
            assertEquals(1, context.revision());
            assertEquals(new Spend(i, i % 3), context.spend());
        }

        for (int i = 0; i < 50; i++) {
            manager.publish(formed.get(i).id(), Map.of(count, 1000L + i), FindingBundle.empty(), Spend.zero);
        }
        for (int i = 0; i < 50; i++) {
            assertEquals(1000L + i, manager.context(formed.get(i).id()).get(count));
            assertEquals((long) (50 + i), manager.context(formed.get(50 + i).id()).get(count));
        }

        manager.publish(formed.get(0).id(), Map.of(count, 77L), FindingBundle.empty(), Spend.zero);
        assertEquals(77L, manager.context(formed.get(0).id()).get(count));
        manager.remove(formed.get(99).id());
        assertEquals(0, manager.context(formed.get(99).id()).revision());
        assertEquals(99, manager.instances().size());
    }

    @Test
    void draftLifecycleAtScaleKeepsReservationsWorldScoped() {
        StructureManager manager = StructureManager.create();
        List<StructureAttempt> drafts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            drafts.add(manager.begin(world(i % 3), spec(owner, siting(i))));
        }
        assertThrows(IllegalArgumentException.class, () -> manager.begin(world(0), spec(owner, siting(0))));
        StructureAttempt otherWorldDraft = manager.begin(world(1), spec(owner, siting(0)));
        assertNotNull(otherWorldDraft, "same tuple in another world is free");
        otherWorldDraft.abort();

        for (StructureAttempt draft : drafts) {
            draft.publish(draft.id(), Map.of(count, draft.id().serial()), FindingBundle.empty(), Spend.zero);
        }
        for (int i = 0; i < drafts.size(); i++) {
            StructureAttempt draft = drafts.get(i);
            boolean formed;
            if (i % 3 == 0) {
                draft.abort();
                formed = false;
            } else if (i % 3 == 1) {
                draft.commit(box(i, 0, 0, i + 1, 1, 1));
                formed = true;
            } else {
                assertNotNull(manager.remove(draft.id()));
                formed = false;
            }
            assertThrows(
                IllegalStateException.class,
                () -> draft.publish(draft.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero)
            );
            assertThrows(IllegalStateException.class, () -> draft.remove(draft.id()));
            assertThrows(IllegalStateException.class, () -> draft.commit(box(0, 0, 0, 1, 1, 1)));
            assertThrows(IllegalStateException.class, draft::abort);
            if (formed) {
                assertNotNull(manager.find(draft.id()));
            } else {
                assertNull(manager.find(draft.id()));
            }
        }
        assertEquals(10, manager.instances().size());
    }

    @Test
    void movedCollisionsStaleFromAndUnknownIdsAreRejectedAtScale() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> formed = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            formed.add(form(manager, world0, owner, siting(i), box(i, 0, 0, i + 1, 1, 1)));
        }
        StructureInstance moving = formed.get(0);
        assertThrows(
            IllegalArgumentException.class,
            () -> manager
                    .onEvent(new StructureEvent.Moved(moving.id(), siting(0), siting(1), box(200, 0, 0, 201, 1, 1))),
            "a move onto an occupied tuple is rejected"
        );
        manager.onEvent(new StructureEvent.Moved(moving.id(), siting(0), siting(100), box(300, 0, 0, 301, 1, 1)));
        assertEquals(siting(100), Objects.requireNonNull(manager.find(moving.id()), "moved").siting());
        assertThrows(
            IllegalArgumentException.class,
            () -> manager
                    .onEvent(new StructureEvent.Moved(moving.id(), siting(0), siting(101), box(302, 0, 0, 303, 1, 1))),
            "a stale from is rejected"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(
                new StructureEvent.Moved(new InstanceId(world0, 999), siting(0), siting(102), box(0, 0, 0, 1, 1, 1))
            ),
            "an unknown id is rejected"
        );
        StructureInstance otherWorld = form(manager, world(5), owner, siting(100), box(400, 0, 0, 401, 1, 1));
        manager.onEvent(
            new StructureEvent.Moved(otherWorld.id(), siting(100), siting(1_000), box(500, 0, 0, 501, 1, 1))
        );
        assertEquals(siting(1_000), Objects.requireNonNull(manager.find(otherWorld.id()), "other").siting());
    }

    @Test
    void worldScopedFindDisambiguatesTheSameTupleAtScale() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> formed = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            formed.add(form(manager, world(i), owner, siting(0), box(0, 0, 0, 20, 1, 1)));
        }
        for (int i = 0; i < 20; i++) {
            assertEquals(formed.get(i), manager.find(world(i), owner, siting(0)));
        }
        assertThrows(IllegalArgumentException.class, () -> manager.find(owner, siting(0)));
        assertEquals(20, manager.findAt(LatticePoint.of(0, 0, 0)).size(), "findAt is world-frame agnostic");
        assertEquals(20, manager.findIn(box(0, 0, 0, 20, 1, 1)).size());
        manager.remove(formed.get(7).id());
        assertNull(manager.find(world(7), owner, siting(0)));
        assertEquals(19, manager.findAt(LatticePoint.of(0, 0, 0)).size());
    }

    @Test
    void instancesAndLookupResultsAreSortedAndImmutableAtScale() {
        StructureManager manager = StructureManager.create();
        for (int i = 0; i < 60; i++) {
            form(manager, world(i % 4), owner, siting(i), box(i % 10, 0, 0, i % 10 + 1, 1, 1));
        }
        List<StructureInstance> instances = manager.instances();
        for (int i = 1; i < instances.size(); i++) {
            assertTrue(instances.get(i - 1).id().compareTo(instances.get(i).id()) < 0);
        }
        List<StructureInstance> at = manager.findAt(LatticePoint.of(0, 0, 0));
        for (int i = 1; i < at.size(); i++) {
            assertTrue(at.get(i - 1).id().compareTo(at.get(i).id()) < 0);
        }
        assertThrows(UnsupportedOperationException.class, () -> instances.add(instances.get(0)));
        assertThrows(UnsupportedOperationException.class, () -> at.add(at.get(0)));
        assertFalse(at.isEmpty());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("expanded", path);
    }

    private static World world(int index) {
        return World.of(id("world_" + index));
    }

    private static Siting siting(int index) {
        return Siting.of(LatticePoint.of(index * 2, 0, 0), Orientation.identity);
    }

    private static StructureSpec spec(OwnerId owner, Siting siting) {
        return new StructureSpec(owner, siting, blueprint);
    }

    private static CuboidSet box(int x, int y, int z, int maxX, int maxY, int maxZ) {
        return CuboidSet.of(Cuboid.of(x, y, z, maxX, maxY, maxZ));
    }

    private static StructureInstance form(
        StructureManager manager,
        World world,
        OwnerId owner,
        Siting siting,
        CuboidSet bounds
    ) {
        return manager.begin(world, spec(owner, siting)).commit(bounds);
    }

    private static List<InstanceId> ids(List<StructureInstance> instances) {
        return instances.stream().map(StructureInstance::id).toList();
    }
}
