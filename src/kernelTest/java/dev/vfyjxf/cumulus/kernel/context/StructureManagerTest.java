package dev.vfyjxf.cumulus.kernel.context;

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
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class StructureManagerTest {

    private static final World world = World.of(NamespacedId.of("test", "world"));
    private static final World otherWorld = World.of(NamespacedId.of("test", "other_world"));
    private static final OwnerId owner = OwnerId.of(NamespacedId.of("test", "owner"));
    private static final Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final NamespacedId blueprint = NamespacedId.of("test", "blueprint");
    private static final CuboidSet bounds = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 2));
    private static final ContextKey<Long> count = ContextKeys
            .of(NamespacedId.of("test", "count"), Long.class, Retention.instance);

    @Test
    void beginPublishesCommitsAndFindsByCell() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = manager.begin(world, new StructureSpec(owner, siting, blueprint));
        assertEquals(new InstanceId(world, 1), attempt.id());
        assertTrue(manager.instances().isEmpty());

        StructureContext draft = attempt
                .publish(attempt.id(), Map.of(count, 4L), FindingBundle.empty(), new Spend(2, 1));
        assertEquals(4L, draft.get(count));
        assertEquals(4L, manager.context(attempt.id()).get(count), "the draft is visible through the manager");

        StructureInstance instance = attempt.commit(bounds);
        assertEquals(attempt.id(), instance.id());
        assertEquals(4L, instance.context().get(count));
        assertEquals(1, manager.instances().size());
        assertEquals(instance, manager.find(attempt.id()));
        assertEquals(instance, manager.find(owner, siting));
        assertEquals(List.of(instance), manager.findAt(LatticePoint.of(1, 0, 1)));
        assertTrue(manager.findAt(LatticePoint.of(9, 0, 9)).isEmpty());
        assertEquals(1, manager.findIn(bounds).size());
    }

    @Test
    void abortDropsTheDraftAndReleasesTheSiting() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = manager.begin(world, new StructureSpec(owner, siting, blueprint));
        attempt.abort();
        assertTrue(manager.instances().isEmpty());
        assertNull(manager.find(attempt.id()));
        assertThrows(IllegalStateException.class, attempt::abort);
        assertThrows(IllegalStateException.class, () -> attempt.commit(bounds));

        StructureAttempt again = manager.begin(world, new StructureSpec(owner, siting, blueprint));
        assertEquals(2, again.id().serial(), "the aborted attempt does not release the serial");
    }

    @Test
    void aDraftSitingIsRejectedButAFormedInstanceCanBeReplaced() {
        StructureManager manager = StructureManager.create();
        StructureAttempt first = manager.begin(world, new StructureSpec(owner, siting, blueprint));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.begin(world, new StructureSpec(owner, siting, NamespacedId.of("test", "other")))
        );
        StructureInstance formed = first.commit(bounds);

        StructureAttempt replacement = manager.begin(world, new StructureSpec(owner, siting, blueprint));
        StructureInstance replaced = replacement.commit(bounds);
        assertTrue(manager.find(formed.id()) == null, "the old instance is replaced");
        assertEquals(replaced, manager.find(owner, siting));
    }

    @Test
    void movedKeepsIdAndContextAndRejectsACollision() {
        StructureManager manager = StructureManager.create();
        StructureInstance instance = manager.begin(world, new StructureSpec(owner, siting, blueprint)).commit(bounds);
        manager.publish(instance.id(), Map.of(count, 7L), FindingBundle.empty(), Spend.zero);

        Siting movedTo = Siting.of(LatticePoint.of(10, 64, 10), Orientation.identity);
        CuboidSet movedBounds = CuboidSet.of(Cuboid.of(10, 64, 10, 12, 65, 12));
        manager.onEvent(new StructureEvent.Moved(instance.id(), siting, movedTo, movedBounds));

        StructureInstance moved = manager.find(instance.id());
        assertNotNull(moved);
        assertEquals(movedTo, moved.siting());
        assertEquals(movedBounds, moved.bounds());
        assertEquals(7L, moved.context().get(count));
        assertEquals(moved, manager.find(owner, movedTo));
        assertNull(manager.find(owner, siting));

        StructureInstance other = manager.begin(
            world,
            new StructureSpec(
                owner,
                Siting.of(LatticePoint.of(20, 64, 20), Orientation.identity),
                NamespacedId.of("test", "other")
            )
        ).commit(CuboidSet.of(Cuboid.of(20, 64, 20, 21, 65, 21)));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(other.id(), other.siting(), movedTo, movedBounds))
        );
    }

    @Test
    void brokenAndInvalidatedAreIdempotent() {
        StructureManager manager = StructureManager.create();
        StructureInstance instance = manager.begin(world, new StructureSpec(owner, siting, blueprint)).commit(bounds);
        manager.onEvent(new StructureEvent.Broken(instance.id()));
        manager.onEvent(new StructureEvent.Broken(instance.id()));
        assertTrue(manager.instances().isEmpty());
        assertNull(manager.find(instance.id()));

        StructureInstance second = manager.begin(world, new StructureSpec(owner, siting, blueprint)).commit(bounds);
        manager.onEvent(new StructureEvent.Invalidated(second.id(), "test"));
        manager.onEvent(new StructureEvent.Invalidated(second.id(), "test"));
        assertTrue(manager.instances().isEmpty());
    }

    @Test
    void unloadedRemovesOnlyThatWorldAndLoadedRaisesTheSerial() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = manager.begin(world, new StructureSpec(owner, siting, blueprint)).commit(bounds);
        StructureInstance second = manager.begin(
            otherWorld,
            new StructureSpec(owner, Siting.of(LatticePoint.of(10, 64, 10), Orientation.identity), blueprint)
        ).commit(CuboidSet.of(Cuboid.of(10, 64, 10, 12, 65, 12)));

        manager.onEvent(new StructureEvent.Unloaded(world));
        assertTrue(manager.find(first.id()) == null);
        assertNotNull(manager.find(second.id()));

        manager.onEvent(new StructureEvent.Loaded(world, List.of(first)));
        manager.onEvent(new StructureEvent.Loaded(otherWorld, List.of(second)));
        assertEquals(2, manager.instances().size());
        assertEquals(
            3,
            manager.begin(
                world,
                new StructureSpec(owner, Siting.of(LatticePoint.of(30, 64, 30), Orientation.identity), blueprint)
            ).id().serial(),
            "loaded instances raise the watermark"
        );
    }

    @Test
    void loadedIsPerWorldAndValidatesBeforeReplacing() {
        StructureManager manager = StructureManager.create();
        StructureInstance inWorld = manager.begin(world, new StructureSpec(owner, siting, blueprint)).commit(bounds);
        StructureInstance inOther = manager.begin(otherWorld, new StructureSpec(owner, siting, blueprint))
                .commit(bounds);
        InstanceId restoredId = new InstanceId(world, 10);
        StructureInstance restored = new StructureInstance(
            restoredId,
            new StructureSpec(owner, Siting.of(LatticePoint.of(8, 0, 8), Orientation.identity), blueprint),
            StructureContext.empty(restoredId),
            bounds
        );

        manager.onEvent(new StructureEvent.Loaded(world, List.of(restored)));

        assertNull(manager.find(inWorld.id()), "Loaded replaces only the requested world");
        assertEquals(inOther, manager.find(inOther.id()), "another world is preserved");
        assertEquals(restored, manager.find(world, owner, restored.siting()));
        assertNull(manager.find(world, owner, siting));

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(world, List.of(restored, restored)))
        );
        assertEquals(restored, manager.find(world, owner, restored.siting()), "a rejected Loaded is atomic");
        assertEquals(inOther, manager.find(inOther.id()), "a rejected Loaded leaves other worlds alone");
    }

    @Test
    void findRequiresAWorldWhenTheSameTupleExistsInTwoWorlds() {
        StructureManager manager = StructureManager.create();
        StructureInstance inWorld = manager.begin(world, new StructureSpec(owner, siting, blueprint)).commit(bounds);
        StructureInstance inOther = manager.begin(otherWorld, new StructureSpec(owner, siting, blueprint))
                .commit(bounds);

        assertEquals(inWorld, manager.find(world, owner, siting));
        assertEquals(inOther, manager.find(otherWorld, owner, siting));
        assertThrows(IllegalArgumentException.class, () -> manager.find(owner, siting));
    }

    @Test
    void publishAfterCommitUpdatesTheBoundInstance() {
        StructureManager manager = StructureManager.create();
        StructureInstance instance = manager.begin(world, new StructureSpec(owner, siting, blueprint)).commit(bounds);
        manager.publish(instance.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        manager.publish(instance.id(), Map.of(count, 2L), FindingBundle.empty(), new Spend(1, 0));

        StructureInstance updated = manager.find(instance.id());
        assertNotNull(updated);
        assertEquals(2L, updated.context().get(count));
        assertEquals(2, updated.context().revision());
        assertEquals(instance.bounds(), updated.bounds());
        assertFalse(manager.instances().isEmpty());
    }
}
