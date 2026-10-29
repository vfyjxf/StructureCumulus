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
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial lifecycle tests for the host-driven structure manager: one-shot attempts, replacement,
 * serials, events, loaded validation and bounds lookup.
 */
class AdversarialManagerTest {

    private static final World worldA = World.of(id("a_world"));
    private static final World worldB = World.of(id("b_world"));
    private static final OwnerId owner = OwnerId.of(id("owner"));
    private static final ContextKey<Long> count = ContextKeys.of(id("count"), Long.class, Retention.instance);
    private static final ContextKey<Long> other = ContextKeys.of(id("other"), Long.class, Retention.instance);
    private static final ContextKey<Long> match = ContextKeys.of(id("match"), Long.class, Retention.match);
    private static final CuboidSet bounds = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 2));

    @Test
    void commitAndAbortAreOneShot() {
        StructureManager manager = StructureManager.create();
        StructureAttempt committed = begin(manager, worldA, siting(0, 0, 0));
        StructureInstance formed = committed.commit(bounds);

        assertThrows(IllegalStateException.class, () -> committed.commit(bounds));
        assertThrows(IllegalStateException.class, committed::abort);
        assertNotNull(manager.find(formed.id()), "the formed instance survives the rejected second commit");

        StructureAttempt aborted = begin(manager, worldA, siting(8, 0, 0));
        aborted.abort();
        assertThrows(IllegalStateException.class, aborted::abort);
        assertThrows(IllegalStateException.class, () -> aborted.commit(bounds));
        assertNull(manager.find(aborted.id()));
    }

    @Test
    void aRejectedDuplicateDraftDoesNotConsumeASerialAndAbortReleasesTheSiting() {
        StructureManager manager = StructureManager.create();
        StructureAttempt first = begin(manager, worldA, siting(0, 0, 0));
        assertEquals(1, first.id().serial());

        IllegalArgumentException duplicate = assertThrows(
            IllegalArgumentException.class,
            () -> begin(manager, worldA, siting(0, 0, 0))
        );
        assertTrue(
            Objects.requireNonNull(duplicate.getMessage(), "message").contains("occupies"),
            "the duplicate rejection names the occupied siting"
        );

        first.abort();
        StructureAttempt second = begin(manager, worldA, siting(0, 0, 0));
        assertEquals(2, second.id().serial(), "the rejected begin allocated no serial");
        second.abort();
    }

    @Test
    void abortingADraftDoesNotTouchAFormedInstanceAtTheSameSiting() {
        StructureManager manager = StructureManager.create();
        StructureInstance formed = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        StructureAttempt draft = begin(manager, worldA, siting(0, 0, 0));
        draft.publish(draft.id(), Map.of(count, 9L), FindingBundle.empty(), Spend.zero);

        draft.abort();

        assertEquals(formed, manager.find(formed.id()), "abort leaves the formed instance untouched");
        assertEquals(formed, manager.find(owner, siting(0, 0, 0)));
        assertEquals(0, manager.context(draft.id()).revision(), "the aborted draft context is gone");
        assertNotNull(begin(manager, worldA, siting(0, 0, 0)), "the siting is available again after abort");
    }

    @Test
    void commitReplacesTheFormedInstanceAtTheSameSiting() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        manager.publish(first.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);

        StructureAttempt replacement = begin(manager, worldA, siting(0, 0, 0));
        replacement.publish(replacement.id(), Map.of(other, 2L), FindingBundle.empty(), Spend.zero);
        StructureInstance second = replacement.commit(bounds);

        assertNull(manager.find(first.id()), "the replaced instance is gone");
        assertEquals(0, manager.context(first.id()).revision(), "the replaced context reads empty");
        assertEquals(second, manager.find(owner, siting(0, 0, 0)));
        assertEquals(2L, second.context().get(other));
        assertEquals(1, manager.instances().size());
        assertTrue(manager.findAt(LatticePoint.of(0, 0, 0)).contains(second));
    }

    @Test
    void serialsAreMonotonicAcrossAbortCommitUnloadedAndLoaded() {
        StructureManager manager = StructureManager.create();
        manager.begin(worldA, spec(siting(0, 0, 0))).abort();
        manager.begin(worldA, spec(siting(2, 0, 0))).commit(bounds);
        manager.begin(worldA, spec(siting(4, 0, 0))).commit(bounds);

        manager.onEvent(new StructureEvent.Unloaded(worldA));
        StructureAttempt afterUnload = begin(manager, worldA, siting(6, 0, 0));
        assertEquals(4, afterUnload.id().serial(), "unload does not reset the session serial");
        afterUnload.abort();

        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored(worldA, 10, siting(8, 0, 0), bounds))));
        StructureAttempt afterLoad = begin(manager, worldA, siting(10, 0, 0));
        assertEquals(11, afterLoad.id().serial(), "loaded instances raise the watermark");
        afterLoad.abort();

        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored(worldA, 2, siting(12, 0, 0), bounds))));
        StructureAttempt afterLowerLoad = begin(manager, worldA, siting(14, 0, 0));
        assertEquals(12, afterLowerLoad.id().serial(), "a lower restored serial never lowers the watermark");
        afterLowerLoad.abort();
    }

    @Test
    void publishMergesRevisionSpendAndFindingsAndPublishAfterCommitUpdatesTheInstance() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        StructureContext first = attempt.publish(attempt.id(), Map.of(count, 1L), findings("first"), new Spend(2, 1));
        assertEquals(1, first.revision());
        assertEquals(new Spend(2, 1), first.spend());

        StructureContext second = attempt.publish(attempt.id(), Map.of(other, 2L), findings("second"), new Spend(3, 4));
        assertEquals(2, second.revision(), "one publish is one revision");
        assertEquals(1L, second.get(count), "unmentioned values are kept");
        assertEquals(2L, second.get(other), "mentioned values are replaced in");
        assertEquals(new Spend(5, 5), second.spend(), "spend is cumulative");
        assertEquals("second", Objects.requireNonNull(second.findings().primary(), "primary").message());

        StructureInstance formed = attempt.commit(bounds);
        StructureContext third = manager.publish(formed.id(), Map.of(count, 5L), findings("third"), new Spend(1, 1));
        assertEquals(3, third.revision());
        assertEquals(5L, third.get(count));
        assertEquals(2L, third.get(other));
        assertEquals(new Spend(6, 6), third.spend());
        assertEquals("third", Objects.requireNonNull(third.findings().primary(), "primary").message());

        StructureInstance updated = manager.find(formed.id());
        assertNotNull(updated);
        assertEquals(third, updated.context(), "the instance owns the latest context");
        assertEquals(formed.bounds(), updated.bounds());
        assertEquals(formed.spec(), updated.spec());
        assertNotEquals(formed, updated, "the whole instance value is replaced on publish");
    }

    @Test
    void emptyPublishStillBumpsTheRevisionAndKeepsValues() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        attempt.publish(attempt.id(), Map.of(count, 7L), findingOf("kept"), new Spend(1, 2));
        StructureContext empty = attempt.publish(attempt.id(), Map.of(), findings("latest"), Spend.zero);

        assertEquals(2, empty.revision());
        assertEquals(7L, empty.get(count));
        assertEquals(new Spend(1, 2), empty.spend());
        assertEquals("latest", Objects.requireNonNull(empty.findings().primary(), "primary").message());
    }

    @Test
    void matchValuesAreRejectedEverywhereAndLeaveTheStateUntouched() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> attempt.publish(attempt.id(), Map.of(match, 1L), FindingBundle.empty(), Spend.zero)
        );
        assertEquals(0, attempt.context(attempt.id()).revision(), "a rejected publish writes nothing");

        StructureInstance formed = attempt.commit(bounds);
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.publish(formed.id(), Map.of(match, 1L), FindingBundle.empty(), Spend.zero)
        );
        assertEquals(0, Objects.requireNonNull(manager.find(formed.id()), "formed").context().revision());
    }

    @Test
    void publishRejectsUnknownInstancesSinceBrokenAndInvalidatedIds() {
        StructureManager manager = StructureManager.create();
        InstanceId unknown = new InstanceId(worldA, 999);
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.publish(unknown, Map.of(count, 1L), FindingBundle.empty(), Spend.zero)
        );
        assertNull(manager.remove(unknown));
        assertEquals(0, manager.context(unknown).revision(), "an absent context reads empty at revision 0");

        StructureInstance formed = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        manager.onEvent(new StructureEvent.Broken(formed.id()));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.publish(formed.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero)
        );
    }

    @Test
    @SuppressWarnings("NullAway")
    void publishRejectsNullValuesAndWrongTypedValuesWithoutWriting() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));

        Map<ContextKey<?>, Object> nullValue = new HashMap<>();
        nullValue.put(count, null);
        assertThrows(
            NullPointerException.class,
            () -> attempt.publish(attempt.id(), nullValue, FindingBundle.empty(), Spend.zero)
        );

        Map<ContextKey<?>, Object> wrongType = new HashMap<>();
        wrongType.put(count, "not a long");
        assertThrows(
            IllegalArgumentException.class,
            () -> attempt.publish(attempt.id(), wrongType, FindingBundle.empty(), Spend.zero)
        );

        Map<ContextKey<?>, Object> nullKey = new HashMap<>();
        nullKey.put(null, 1L);
        assertThrows(
            NullPointerException.class,
            () -> attempt.publish(attempt.id(), nullKey, FindingBundle.empty(), Spend.zero)
        );
        assertEquals(0, attempt.context(attempt.id()).revision(), "failed publishes leave the context untouched");
    }

    @Test
    void removeFormedReturnsTheContextAndClearsTheSiting() {
        StructureManager manager = StructureManager.create();
        StructureInstance formed = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        manager.publish(formed.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        StructureContext stored = Objects.requireNonNull(manager.find(formed.id()), "formed").context();

        assertEquals(stored, manager.remove(formed.id()));
        assertNull(manager.find(formed.id()));
        assertNull(manager.find(owner, siting(0, 0, 0)));
        assertTrue(manager.findAt(LatticePoint.of(0, 0, 0)).isEmpty());
        assertNull(manager.remove(formed.id()), "a second remove finds nothing");
        assertEquals(0, manager.context(formed.id()).revision(), "the context is empty after remove");
        assertNotNull(begin(manager, worldA, siting(0, 0, 0)), "the siting is free after remove");
    }

    @Test
    void attemptIsolationAndDraftContextRemoval() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        InstanceId foreign = new InstanceId(worldB, 4);
        assertEquals(0, attempt.context(foreign).revision(), "a draft answers empty for a foreign id");
        assertNull(attempt.remove(foreign), "removing a foreign id does not touch the draft");
        assertThrows(
            IllegalArgumentException.class,
            () -> attempt.publish(foreign, Map.of(count, 1L), FindingBundle.empty(), Spend.zero)
        );

        attempt.publish(attempt.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        attempt.publish(attempt.id(), Map.of(other, 2L), FindingBundle.empty(), Spend.zero);
        StructureContext removed = attempt.remove(attempt.id());
        assertNotNull(removed);
        assertEquals(2L, removed.get(other), "remove returns the published context");
        assertEquals(0, attempt.context(attempt.id()).revision(), "the removed draft reads empty");
        assertEquals(0, manager.context(attempt.id()).revision(), "the manager no longer sees the removed draft");
        assertNull(manager.find(attempt.id()));
        assertThrows(
            IllegalStateException.class,
            () -> attempt.publish(attempt.id(), Map.of(count, 3L), FindingBundle.empty(), Spend.zero),
            "a removed attempt is closed"
        );
        assertThrows(IllegalStateException.class, () -> attempt.remove(attempt.id()), "a removed attempt is closed");
        assertThrows(IllegalStateException.class, () -> attempt.commit(bounds), "remove-then-commit must not form");
        assertThrows(IllegalStateException.class, attempt::abort);
    }

    @Test
    void attemptRemoveClosesTheDraftReleasesTheSitingAndFormsNothing() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        attempt.publish(attempt.id(), Map.of(count, 7L), FindingBundle.empty(), Spend.zero);

        StructureContext removed = attempt.remove(attempt.id());
        assertNotNull(removed);
        assertEquals(7L, removed.get(count));

        assertNull(manager.find(attempt.id()), "a removed attempt never becomes an instance");
        assertNull(manager.find(owner, siting(0, 0, 0)), "the removed attempt releases its siting");
        assertThrows(IllegalStateException.class, () -> attempt.commit(bounds));
        assertNull(manager.remove(attempt.id()), "the draft is already gone from the manager");

        StructureAttempt replacement = begin(manager, worldA, siting(0, 0, 0));
        assertEquals(2, replacement.id().serial(), "the released siting can be reserved again");
        replacement.abort();
    }

    @Test
    void brokenAndInvalidatedRemoveFormedInstancesAndDraftsAndStayIdempotent() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draft = begin(manager, worldA, siting(0, 0, 0));
        draft.publish(draft.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        manager.onEvent(new StructureEvent.Broken(draft.id()));
        assertThrows(IllegalStateException.class, () -> draft.commit(bounds), "a broken draft is closed");
        assertNull(manager.find(draft.id()));

        StructureInstance formed = begin(manager, worldA, siting(2, 0, 0)).commit(bounds);
        manager.onEvent(new StructureEvent.Broken(formed.id()));
        manager.onEvent(new StructureEvent.Broken(formed.id()));
        manager.onEvent(new StructureEvent.Invalidated(formed.id(), "already gone"));
        assertTrue(manager.instances().isEmpty());

        StructureInstance invalid = begin(manager, worldA, siting(4, 0, 0)).commit(bounds);
        manager.onEvent(new StructureEvent.Invalidated(invalid.id(), "corrupt"));
        manager.onEvent(new StructureEvent.Invalidated(invalid.id(), "corrupt again"));
        assertNull(manager.find(invalid.id()));
    }

    @Test
    void movedPreservesIdentityAndContextAndRejectsStaleOrCollidingFrom() {
        StructureManager manager = StructureManager.create();
        StructureInstance formed = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        manager.publish(formed.id(), Map.of(count, 7L), FindingBundle.empty(), Spend.zero);
        StructureContext before = Objects.requireNonNull(manager.find(formed.id()), "formed").context();

        Siting movedTo = siting(10, 64, 10);
        CuboidSet movedBounds = CuboidSet.of(Cuboid.of(10, 64, 10, 12, 66, 12));
        manager.onEvent(new StructureEvent.Moved(formed.id(), siting(0, 0, 0), movedTo, movedBounds));

        StructureInstance moved = manager.find(formed.id());
        assertNotNull(moved);
        assertEquals(formed.id(), moved.id());
        assertEquals(before, moved.context(), "Moved preserves the id and context");
        assertEquals(movedTo, moved.siting());
        assertEquals(movedBounds, moved.bounds());
        assertEquals(moved, manager.find(owner, movedTo));
        assertNull(manager.find(owner, siting(0, 0, 0)));
        assertTrue(manager.findAt(LatticePoint.of(10, 64, 10)).contains(moved));
        assertTrue(manager.findAt(LatticePoint.of(0, 0, 0)).isEmpty());

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(formed.id(), siting(0, 0, 0), siting(20, 0, 0), bounds)),
            "a stale from is rejected"
        );

        StructureInstance other = begin(manager, worldA, siting(20, 0, 0)).commit(bounds);
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(formed.id(), movedTo, other.siting(), bounds)),
            "a move onto another instance's siting is rejected"
        );
        assertEquals(
            movedTo,
            Objects.requireNonNull(manager.find(formed.id()), "formed").siting(),
            "a rejected move leaves the instance in place"
        );

        CuboidSet secondBounds = CuboidSet.of(Cuboid.of(10, 64, 10, 11, 65, 11));
        manager.onEvent(new StructureEvent.Moved(formed.id(), movedTo, movedTo, secondBounds));
        assertEquals(
            secondBounds,
            Objects.requireNonNull(manager.find(formed.id()), "formed").bounds(),
            "moving to the same siting only updates bounds"
        );
    }

    @Test
    void unloadedIsolatesWorldsAndInvalidatesThatWorldsDrafts() {
        StructureManager manager = StructureManager.create();
        StructureInstance formedA = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        StructureAttempt draftA = begin(manager, worldA, siting(2, 0, 0));
        StructureInstance formedB = begin(manager, worldB, siting(4, 0, 0)).commit(bounds);
        StructureAttempt draftB = begin(manager, worldB, siting(6, 0, 0));

        manager.onEvent(new StructureEvent.Unloaded(worldA));

        assertNull(manager.find(formedA.id()));
        assertThrows(IllegalStateException.class, () -> draftA.commit(bounds), "the unloaded draft is closed");
        assertNotNull(manager.find(formedB.id()), "the other world is untouched");
        assertNotNull(draftB.commit(bounds), "the other world's draft still commits");
        assertNull(manager.find(draftA.id()));

        manager.onEvent(new StructureEvent.Unloaded(worldA));
        manager.onEvent(new StructureEvent.Unloaded(World.of(id("never_loaded"))));
        assertEquals(2, manager.instances().size());
        assertNotNull(begin(manager, worldA, siting(4, 0, 0)), "the unloaded world accepts new drafts");
    }

    @Test
    void loadedReplacesTheRegistryRaisesTheWatermarkAndClearsDrafts() {
        StructureManager manager = StructureManager.create();
        StructureInstance current = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        StructureAttempt draft = begin(manager, worldA, siting(2, 0, 0));
        draft.publish(draft.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);

        StructureInstance restored = restored(worldA, 5, siting(4, 0, 0), bounds);
        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored)));

        assertNull(manager.find(current.id()), "Loaded replaces the registry");
        assertEquals(restored, manager.find(restored.id()));
        assertEquals(1, manager.instances().size());
        assertEquals(0, manager.context(draft.id()).revision(), "Loaded drops drafts and their contexts");
        StructureAttempt next = begin(manager, worldA, siting(6, 0, 0));
        assertEquals(6, next.id().serial(), "Loaded raises the watermark to max(restored)+1");
        next.abort();
    }

    @Test
    void loadedCopiesTheRestoredListBeforeUse() {
        StructureManager manager = StructureManager.create();
        StructureInstance restored = restored(worldA, 3, siting(0, 0, 0), bounds);
        List<StructureInstance> mutable = new ArrayList<>();
        mutable.add(restored);
        StructureEvent.Loaded event = new StructureEvent.Loaded(worldA, mutable);

        mutable.clear();
        manager.onEvent(event);

        assertEquals(restored, manager.find(restored.id()), "the event owns an immutable copy of the list");
    }

    @Test
    void loadedClosesOnlyTheLoadedWorldsDrafts() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draftA = begin(manager, worldA, siting(0, 0, 0));
        StructureAttempt draftB = begin(manager, worldB, siting(0, 0, 0));

        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored(worldA, 5, siting(4, 0, 0), bounds))));

        assertThrows(IllegalStateException.class, () -> draftA.commit(bounds));
        assertThrows(IllegalStateException.class, draftA::abort);
        assertNotNull(draftB.commit(bounds), "another world's draft survives the Loaded event");
    }

    @Test
    void loadedRejectsAnotherWorldsRestoredInstanceWithoutMutating() {
        StructureManager manager = StructureManager.create();
        StructureInstance current = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        StructureAttempt draft = begin(manager, worldA, siting(2, 0, 0));
        StructureInstance foreign = restored(worldB, 9, siting(4, 0, 0), bounds);

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(worldA, List.of(foreign)))
        );

        assertEquals(current, manager.find(current.id()), "a rejected Loaded leaves the world's instances intact");
        assertEquals(1, manager.instances().size());
        draft.publish(draft.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        assertEquals(1L, manager.context(draft.id()).get(count), "a rejected Loaded leaves drafts open");
    }

    @Test
    void aRejectedLoadedDuplicateIdMustNotDestroyTheCurrentRegistry() {
        StructureManager manager = StructureManager.create();
        StructureInstance current = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        StructureInstance restored = restored(worldA, 7, siting(4, 0, 0), bounds);

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored, restored)))
        );

        assertEquals(current, manager.find(current.id()), "a rejected Loaded validates before replacing");
        assertEquals(current, manager.find(owner, siting(0, 0, 0)), "the old siting index is intact");
    }

    @Test
    void aRejectedLoadedDuplicateSitingMustNotDestroyTheCurrentRegistry() {
        StructureManager manager = StructureManager.create();
        StructureInstance current = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        StructureInstance first = restored(worldA, 7, siting(4, 0, 0), bounds);
        StructureInstance second = restored(worldA, 8, siting(4, 0, 0), bounds);

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(worldA, List.of(first, second)))
        );

        assertEquals(current, manager.find(current.id()), "a rejected Loaded leaves the registry untouched");
        assertNull(manager.find(first.id()), "no partially inserted restored instance remains");
    }

    @Test
    void loadedInvalidatesStaleAttempts() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draft = begin(manager, worldA, siting(0, 0, 0));
        draft.publish(draft.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        StructureInstance restored = restored(worldA, 9, siting(0, 0, 0), bounds);

        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored)));

        assertThrows(
            IllegalStateException.class,
            () -> draft.commit(bounds),
            "a loaded replacement invalidates drafts"
        );
        assertThrows(IllegalStateException.class, draft::abort, "a loaded replacement invalidates drafts");
        assertEquals(restored, manager.find(restored.id()), "a stale attempt cannot replace a loaded instance");
        assertEquals(1, manager.instances().size());
    }

    @Test
    void closedAttemptsCannotWriteThroughTheDraftIntoTheRegistry() {
        StructureManager manager = StructureManager.create();
        StructureAttempt committed = begin(manager, worldA, siting(0, 0, 0));
        StructureInstance formed = committed.commit(bounds);

        assertThrows(
            IllegalStateException.class,
            () -> committed.publish(formed.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero),
            "a committed attempt rejects a later publish"
        );
        assertThrows(IllegalStateException.class, () -> committed.remove(formed.id()));
        assertEquals(
            0,
            Objects.requireNonNull(manager.find(formed.id()), "formed").context().revision(),
            "publishing through a committed attempt must not reach the formed instance"
        );
        assertNull(manager.context(formed.id()).get(count), "no value leaked");
    }

    @Test
    void boundsLookupIsHalfOpenAndOrdersByIdentity() {
        StructureManager manager = StructureManager.create();
        CuboidSet twoBoxes = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1), Cuboid.of(5, 0, 5, 6, 1, 6));
        StructureInstance first = begin(manager, worldA, siting(0, 0, 0)).commit(twoBoxes);
        CuboidSet overlap = CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1));
        StructureInstance second = begin(manager, worldB, siting(10, 0, 10)).commit(overlap);
        StructureInstance empty = begin(manager, worldA, siting(20, 0, 20)).commit(CuboidSet.empty());

        assertEquals(List.of(first), manager.findAt(LatticePoint.of(0, 0, 0)));
        assertEquals(List.of(first, second), manager.findAt(LatticePoint.of(1, 0, 0)), "two boxes can cover one cell");
        assertTrue(manager.findAt(LatticePoint.of(2, 0, 0)).isEmpty(), "the max edge is exclusive");
        assertEquals(List.of(first), manager.findAt(LatticePoint.of(5, 0, 5)));
        assertTrue(manager.findAt(LatticePoint.of(5, 0, 6)).isEmpty(), "the second box's max z is exclusive");
        assertTrue(
            manager.findAt(LatticePoint.of(1, 0, 0)).stream().noneMatch(instance -> instance.id().equals(empty.id()))
        );

        assertEquals(List.of(first, empty, second), manager.instances(), "instances are ordered by identity");
        assertEquals(
            List.of(first),
            manager.findIn(CuboidSet.of(Cuboid.of(5, 0, 5, 6, 1, 6))),
            "findIn intersects the bounds"
        );
        assertEquals(List.of(first, second), manager.findIn(CuboidSet.of(Cuboid.of(0, 0, 0, 10, 1, 10))));
        assertTrue(manager.findIn(CuboidSet.empty()).isEmpty());
        assertTrue(
            manager.findIn(CuboidSet.of(Cuboid.of(2, 0, 0, 3, 1, 1))).isEmpty(),
            "touching boxes do not intersect"
        );
        assertTrue(
            manager.findIn(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))).contains(first),
            "the empty-bounds instance never matches"
        );
        assertThrows(UnsupportedOperationException.class, () -> manager.instances().add(first));
        assertThrows(UnsupportedOperationException.class, () -> manager.findAt(LatticePoint.origin).add(second));
    }

    @Test
    @SuppressWarnings("NullAway")
    void argumentsAndEventsValidateTheirInputs() {
        StructureManager manager = StructureManager.create();
        StructureSpec spec = spec(siting(0, 0, 0));
        assertThrows(NullPointerException.class, () -> manager.begin(null, spec));
        assertThrows(NullPointerException.class, () -> manager.begin(worldA, null));
        assertThrows(NullPointerException.class, () -> manager.find((InstanceId) null));
        assertThrows(NullPointerException.class, () -> manager.find(owner, null));
        assertThrows(NullPointerException.class, () -> manager.findAt(null));
        assertThrows(NullPointerException.class, () -> manager.findIn(null));
        assertThrows(NullPointerException.class, () -> manager.onEvent(null));
        assertThrows(NullPointerException.class, () -> begin(manager, worldA, siting(0, 0, 0)).commit(null));

        InstanceId id = new InstanceId(worldA, 1);
        StructureContext foreign = StructureContext.empty(new InstanceId(worldA, 2));
        assertThrows(
            IllegalArgumentException.class,
            () -> new StructureInstance(id, spec, foreign, bounds),
            "an instance whose context belongs to another id is rejected"
        );
        assertThrows(
            NullPointerException.class,
            () -> new StructureInstance(null, spec, StructureContext.empty(id), bounds)
        );
        assertThrows(NullPointerException.class, () -> new StructureSpec(null, siting(0, 0, 0), id("blueprint")));
        assertThrows(NullPointerException.class, () -> new StructureSpec(owner, null, id("blueprint")));
        assertThrows(NullPointerException.class, () -> new StructureSpec(owner, siting(0, 0, 0), null));
        assertThrows(NullPointerException.class, () -> new StructureEvent.Broken(null));
        assertThrows(
            NullPointerException.class,
            () -> new StructureEvent.Moved(null, siting(0, 0, 0), siting(1, 0, 0), bounds)
        );
        assertThrows(NullPointerException.class, () -> new StructureEvent.Loaded(null, List.of()));
        assertThrows(NullPointerException.class, () -> new StructureEvent.Loaded(worldA, null));
        assertThrows(NullPointerException.class, () -> new StructureEvent.Unloaded(null));
        assertThrows(NullPointerException.class, () -> new StructureEvent.Invalidated(null, "reason"));
        assertThrows(NullPointerException.class, () -> new StructureEvent.Invalidated(id, null));
    }

    @Test
    void structureContextsAreSortedImmutableAndTyped() {
        ContextKey<Long> zeta = ContextKeys.of(id("zeta"), Long.class, Retention.instance);
        ContextKey<Long> alpha = ContextKeys.of(id("alpha"), Long.class, Retention.instance);
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));

        StructureContext context = attempt
                .publish(attempt.id(), Map.of(zeta, 2L, alpha, 1L), FindingBundle.empty(), Spend.zero);
        assertEquals(List.of(alpha, zeta), List.copyOf(context.keys()), "keys sort by (id, type name)");
        assertEquals(1L, context.get(alpha));
        assertTrue(context.has(alpha));
        assertTrue(context.require(alpha) instanceof Outcome.Found<Long>);
        assertTrue(context.require(match) instanceof Outcome.Missing);
        assertThrows(UnsupportedOperationException.class, () -> context.values().put(alpha, 3L));
        assertThrows(UnsupportedOperationException.class, () -> context.keys().clear());

        StructureContext empty = StructureContext.empty(attempt.id());
        assertEquals(0, empty.revision());
        assertEquals(Spend.zero, empty.spend());
        assertTrue(empty.findings().isEmpty());
        assertTrue(empty.keys().isEmpty());
        assertNull(empty.get(alpha));
        assertTrue(empty.require(alpha) instanceof Outcome.Missing);
    }

    @Test
    void closedAttemptsRejectPublishAfterCommit() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        StructureInstance formed = attempt.commit(bounds);

        assertThrows(
            IllegalStateException.class,
            () -> attempt.publish(formed.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero),
            "a committed attempt must not accept a publish"
        );
    }

    @Test
    void closedAttemptsRejectRemoveAfterCommit() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        StructureInstance formed = attempt.commit(bounds);

        assertThrows(
            IllegalStateException.class,
            () -> attempt.remove(formed.id()),
            "a committed attempt must not accept a remove"
        );
    }

    @Test
    void closedAttemptsRejectPublishAfterAbort() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        attempt.abort();

        assertThrows(
            IllegalStateException.class,
            () -> attempt.publish(attempt.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero),
            "an aborted attempt must not accept a publish"
        );
    }

    @Test
    void closedAttemptsRejectPublishAfterUnloaded() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        manager.onEvent(new StructureEvent.Unloaded(worldA));

        assertThrows(
            IllegalStateException.class,
            () -> attempt.publish(attempt.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero),
            "an unloaded attempt must not accept a publish"
        );
    }

    @Test
    void closedAttemptsRejectPublishAfterLoaded() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored(worldA, 8, siting(2, 0, 0), bounds))));

        assertThrows(
            IllegalStateException.class,
            () -> attempt.publish(attempt.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero),
            "a loaded replacement must invalidate draft writes"
        );
    }

    @Test
    void removingADraftIdInvalidatesTheAttempt() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        attempt.publish(attempt.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);

        assertNotNull(manager.remove(attempt.id()), "the manager removes the draft context");
        assertThrows(
            IllegalStateException.class,
            () -> attempt.commit(bounds),
            "an attempt whose draft was removed by the manager must not commit"
        );
        assertNull(manager.find(attempt.id()), "no instance is formed by the stale attempt");
    }

    @Test
    void theSameOwnerAndSitingInTwoWorldsMustCoexist() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);

        StructureAttempt secondAttempt = assertDoesNotThrow(
            () -> begin(manager, worldB, siting(0, 0, 0)),
            "a draft reservation is per world, not per manager"
        );
        StructureInstance second = secondAttempt.commit(bounds);

        assertNotNull(manager.find(first.id()), "the first world's instance is not replaced by another world");
        assertNotNull(manager.find(second.id()));
        assertNotEquals(first.id(), second.id());
        assertEquals(2, manager.instances().size());
    }

    @Test
    void aDraftSitingReservationIsPerWorld() {
        StructureManager manager = StructureManager.create();
        begin(manager, worldA, siting(0, 0, 0));

        StructureAttempt second = assertDoesNotThrow(
            () -> begin(manager, worldB, siting(0, 0, 0)),
            "two worlds may reserve the same owner and siting"
        );
        second.abort();
        manager.onEvent(new StructureEvent.Unloaded(worldA));
    }

    @Test
    void loadedKeepsInstancesOfWorldsItDoesNotRestore() {
        StructureManager manager = StructureManager.create();
        StructureInstance worldAInstance = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        StructureInstance worldBInstance = begin(manager, worldB, siting(4, 0, 0)).commit(bounds);
        StructureInstance restoredA = restored(worldA, 9, siting(8, 0, 0), bounds);

        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restoredA)));

        assertEquals(restoredA, manager.find(restoredA.id()));
        assertNull(manager.find(worldAInstance.id()), "Loaded replaces the restored world's instances");
        assertNotNull(manager.find(worldBInstance.id()), "Loaded is per world: another world must survive");
    }

    @Test
    void loadedAcceptsTheSameSitingInDifferentWorlds() {
        StructureManager manager = StructureManager.create();
        StructureInstance restoredA = restored(worldA, 5, siting(0, 0, 0), bounds);
        StructureInstance restoredB = restored(worldB, 6, siting(0, 0, 0), bounds);

        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restoredA)));
        manager.onEvent(new StructureEvent.Loaded(worldB, List.of(restoredB)));

        assertNotNull(manager.find(worldA, owner, siting(0, 0, 0)), "two worlds may hold the same owner and siting");
        assertNotNull(manager.find(worldB, owner, siting(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> manager.find(owner, siting(0, 0, 0)));
        assertEquals(2, manager.instances().size());
    }

    @Test
    void boundsAreOpaqueWorldFrameDataNotDerivedFromTheSiting() {
        StructureManager manager = StructureManager.create();
        CuboidSet farBounds = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        StructureInstance instance = begin(manager, worldA, siting(100, 64, 100)).commit(farBounds);

        assertEquals(List.of(instance), manager.findAt(LatticePoint.of(0, 0, 0)), "findAt reads the supplied bounds");
        assertTrue(
            manager.findAt(LatticePoint.of(100, 64, 100)).isEmpty(),
            "the manager never derives bounds from the siting"
        );
        assertEquals(List.of(instance), manager.findIn(farBounds));

        CuboidSet movedBounds = CuboidSet.of(Cuboid.of(50, 0, 50, 51, 1, 51));
        manager.onEvent(
            new StructureEvent.Moved(instance.id(), siting(100, 64, 100), siting(100, 64, 100), movedBounds)
        );
        StructureInstance moved = Objects.requireNonNull(manager.find(instance.id()), "moved");
        assertEquals(List.of(moved), manager.findAt(LatticePoint.of(50, 0, 50)));
    }

    @Test
    void anEmptyCommitBoundsFormsAnInstanceThatNoLookupFinds() {
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = begin(manager, worldA, siting(0, 0, 0));
        StructureInstance instance = attempt.commit(CuboidSet.empty());

        assertEquals(List.of(instance), manager.instances());
        assertTrue(manager.findAt(LatticePoint.origin).isEmpty());
        assertTrue(manager.findIn(CuboidSet.empty()).isEmpty());
        assertTrue(manager.findIn(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))).isEmpty());
        assertEquals(instance, manager.find(instance.id()));
        assertEquals(List.of(), manager.findIn(CuboidSet.empty()));
    }

    @Test
    void movedRejectsUnknownIdsAndDraftIds() {
        StructureManager manager = StructureManager.create();
        InstanceId unknown = new InstanceId(worldA, 999);
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(unknown, siting(0, 0, 0), siting(1, 0, 0), bounds)),
            "a move for an unknown id is rejected"
        );

        StructureAttempt draft = begin(manager, worldA, siting(0, 0, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(draft.id(), siting(0, 0, 0), siting(1, 0, 0), bounds)),
            "a draft is not a formed instance and cannot move"
        );
        draft.abort();
    }

    @Test
    void replacementCommitLeavesNoOldContextForPublication() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);
        manager.publish(first.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);

        StructureInstance second = begin(manager, worldA, siting(0, 0, 0)).commit(bounds);

        assertNull(manager.find(first.id()));
        assertEquals(0, manager.context(first.id()).revision());
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.publish(first.id(), Map.of(count, 2L), FindingBundle.empty(), Spend.zero),
            "the replaced instance id cannot be published"
        );
        assertEquals(second, manager.find(owner, siting(0, 0, 0)));
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("adversarial_manager", path);
    }

    private static Siting siting(int x, int y, int z) {
        return Siting.of(LatticePoint.of(x, y, z), Orientation.identity);
    }

    private static StructureSpec spec(Siting siting) {
        return new StructureSpec(owner, siting, id("blueprint"));
    }

    private static StructureAttempt begin(StructureManager manager, World world, Siting siting) {
        return manager.begin(world, spec(siting));
    }

    private static StructureInstance restored(World world, long serial, Siting siting, CuboidSet bounds) {
        InstanceId id = new InstanceId(world, serial);
        return new StructureInstance(
            id,
            new StructureSpec(owner, siting, id("blueprint")),
            StructureContext.empty(id),
            bounds
        );
    }

    private static FindingBundle findings(String message) {
        return FindingBundle.of(List.of(Finding.builder(Codes.expected).phase(Phase.settle).message(message).build()));
    }

    private static FindingBundle findingOf(String message) {
        return findings(message);
    }
}
