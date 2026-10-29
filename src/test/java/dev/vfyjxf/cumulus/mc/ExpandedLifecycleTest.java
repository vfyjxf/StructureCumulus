package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
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
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expanded multi-instance lifecycle checks: overlapping instances hit by one break, explosion or
 * move; per-instance claims; rejected events; multi-world load/unload; serial/findAt and no leaks.
 */
class ExpandedLifecycleTest {

    private static final World worldA = World.of(NamespacedId.of("test", "expanded_world_a"));
    private static final World worldB = World.of(NamespacedId.of("test", "expanded_world_b"));
    private static final World worldC = World.of(NamespacedId.of("test", "expanded_world_c"));
    private static final OwnerId ownerA = OwnerId.of(NamespacedId.of("test", "expanded_owner_a"));
    private static final OwnerId ownerB = OwnerId.of(NamespacedId.of("test", "expanded_owner_b"));
    private static final OwnerId ownerC = OwnerId.of(NamespacedId.of("test", "expanded_owner_c"));
    private static final OwnerId ownerD = OwnerId.of(NamespacedId.of("test", "expanded_owner_d"));
    private static final OwnerId ownerE = OwnerId.of(NamespacedId.of("test", "expanded_owner_e"));
    private static final NamespacedId blueprint = NamespacedId.of("test", "expanded_blueprint");
    private static final NamespacedId otherBlueprint = NamespacedId.of("test", "expanded_other_blueprint");
    private static final NamespacedId claimBay = NamespacedId.of("test", "expanded_claim_bay");
    private static final Siting atOrigin = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final LatticePoint cellA = LatticePoint.of(0, 0, 0);
    private static final LatticePoint cellB = LatticePoint.of(4, 0, 0);
    private static final LatticePoint cellC = LatticePoint.of(8, 0, 0);
    private static final LatticePoint cellD = LatticePoint.of(1, 0, 0);
    private static final LatticePoint cellE = LatticePoint.of(2, 0, 0);
    private static final LatticePoint cellF = LatticePoint.of(3, 0, 0);
    private static final ContextKey<Long> count = ContextKeys
            .of(NamespacedId.of("test", "expanded_count"), Long.class, Retention.instance);

    @Test
    void overlappingInstancesAreListedInIdOrderAndBrokenIndependently() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance second = form(manager, worldA, ownerB, atOrigin, cellA);
        StructureInstance third = form(manager, worldA, ownerC, atOrigin, cellA);
        assertEquals(List.of(first, second, third), manager.findAt(cellA), "findAt is in instance id order");
        assertEquals(List.of(first, second, third), manager.instances());

        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerC, atOrigin, List.of(row(ownerC, atOrigin, cellA, Sharing.shared)));
        assertEquals(3, ledger.revision(), "each shared row settles its own key");
        assertEquals(3, ledger.read(readBox(cellA)).rows().size(), "three owners share one cell");

        applyBroken(manager, ledger, List.of(cellA));

        assertTrue(manager.findAt(cellA).isEmpty(), "one break removes every overlapping instance");
        assertTrue(manager.instances().isEmpty());
        assertEquals(6, ledger.revision(), "each broken instance releases its own key once");
        assertTrue(ledger.read(readBox(cellA)).rows().isEmpty(), "every overlapping claim row is released");
        assertEquals(0, manager.context(first.id()).revision(), "a broken context is gone");

        applyBroken(manager, ledger, List.of(cellA, cellA));
        manager.onEvent(new StructureEvent.Broken(first.id()));
        assertEquals(6, ledger.revision(), "a duplicate break releases nothing more");
        assertTrue(manager.instances().isEmpty());
    }

    @Test
    void oneBrokenCellReleasesOnlyTheInstancesWhoseBoundsContainIt() {
        StructureManager manager = StructureManager.create();
        StructureInstance sharedFirst = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance sharedSecond = form(manager, worldA, ownerB, atOrigin, cellA);
        StructureInstance far = form(manager, worldA, ownerC, atOrigin, cellB);
        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerC, atOrigin, List.of(row(ownerC, atOrigin, cellB, Sharing.shared)));
        long base = ledger.revision();

        applyBroken(manager, ledger, List.of(cellA));

        assertNull(manager.find(sharedFirst.id()));
        assertNull(manager.find(sharedSecond.id()));
        StructureInstance survivor = manager.find(far.id());
        assertNotNull(survivor, "an instance outside the affected cell survives");
        assertEquals(1, survivor.context().revision(), "the surviving context is untouched");
        assertEquals(1, ledger.read(readBox(cellB)).rows().size(), "the far claim row stays");
        assertEquals(base + 2, ledger.revision(), "only the two overlapping keys are released");
    }

    @Test
    void oneExplosionDeduplicatesRepeatedAffectedPositions() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance second = form(manager, worldA, ownerB, atOrigin, cellA);
        StructureInstance third = form(manager, worldA, ownerC, atOrigin, cellB);
        StructureInstance far = form(manager, worldA, ownerD, atOrigin, cellC);
        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerC, atOrigin, List.of(row(ownerC, atOrigin, cellB, Sharing.shared)));
        settle(ledger, ownerD, atOrigin, List.of(row(ownerD, atOrigin, cellC, Sharing.shared)));
        long base = ledger.revision();

        List<LatticePoint> affected = List.of(cellA, cellA, cellB, cellB, cellA);
        applyBroken(manager, ledger, affected);

        assertNull(manager.find(first.id()));
        assertNull(manager.find(second.id()));
        assertNull(manager.find(third.id()));
        assertNotNull(manager.find(far.id()), "a cell outside the explosion is unaffected");
        assertEquals(base + 3, ledger.revision(), "each touched instance releases exactly once");
        assertTrue(ledger.read(readBox(cellA)).rows().isEmpty());
        assertTrue(ledger.read(readBox(cellB)).rows().isEmpty());
        assertEquals(1, ledger.read(readBox(cellC)).rows().size());

        applyBroken(manager, ledger, affected);
        assertEquals(base + 3, ledger.revision(), "a duplicate explosion releases nothing more");
    }

    @Test
    void onePistonMoveTranslatesEveryOverlappingInstance() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance second = form(manager, worldA, ownerB, atOrigin, cellA);
        manager.publish(first.id(), Map.of(count, 7L), FindingBundle.empty(), Spend.zero);
        manager.publish(second.id(), Map.of(count, 9L), FindingBundle.empty(), Spend.zero);
        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellA, Sharing.shared)));
        long base = ledger.revision();
        LatticePoint moved = Objects.requireNonNull(cellA.offset(1, 0, 0), "moved cell");

        assertEquals(MoveOutcome.moved, applyMove(manager, ledger, first, 1, 0, 0));
        assertEquals(MoveOutcome.moved, applyMove(manager, ledger, second, 1, 0, 0));

        StructureInstance movedFirst = manager.find(first.id());
        StructureInstance movedSecond = manager.find(second.id());
        assertNotNull(movedFirst);
        assertNotNull(movedSecond);
        assertEquals(moved, movedFirst.siting().origin());
        assertEquals(moved, movedSecond.siting().origin());
        assertEquals(box(moved), movedFirst.bounds());
        assertEquals(7L, movedFirst.context().get(count), "the id and context survive the move");
        assertEquals(9L, movedSecond.context().get(count));
        assertEquals(2, movedFirst.context().revision(), "the move never rewrites the context");
        assertTrue(ledger.read(readBox(cellA)).rows().isEmpty(), "both old claim rows are released");
        List<ClaimRow> translated = ledger.read(readBox(moved)).rows();
        assertEquals(2, translated.size(), "both overlapping instances have a translated row");
        assertEquals(base + 4, ledger.revision(), "each moved instance settles once and releases once");

        assertTrue(manager.findIn(box(cellA)).isEmpty(), "the stale piston cell finds no instance now");
        assertEquals(base + 4, ledger.revision(), "a duplicate piston pass translates nothing more");
    }

    @Test
    void pistonMoveRejectsOneInstanceWithoutTouchingItsClaims() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance second = form(manager, worldA, ownerB, atOrigin, cellA);
        StructureInstance occupant = form(manager, worldA, ownerA, Siting.of(cellD, Orientation.identity), cellD);
        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellA, Sharing.shared)));
        long base = ledger.revision();

        assertEquals(MoveOutcome.rejected, applyMove(manager, ledger, first, 1, 0, 0));
        assertEquals(MoveOutcome.moved, applyMove(manager, ledger, second, 1, 0, 0));

        StructureInstance rejected = manager.find(first.id());
        assertNotNull(rejected, "a rejected move leaves the instance at its old place");
        assertEquals(atOrigin, rejected.siting());
        assertEquals(box(cellA), rejected.bounds());
        assertEquals(1, rejected.context().revision(), "a rejected move leaves the context alone");
        assertEquals(1, ledger.read(readBox(cellA)).rows().size(), "the rejected instance keeps its claim row");
        assertTrue(ledger.read(readBox(cellD)).rows().stream().noneMatch(row -> row.owner().equals(ownerA)));
        assertEquals(1, ledger.read(readBox(cellD)).rows().size(), "the moved peer translates its row");
        assertEquals(base + 2, ledger.revision(), "only the accepted move touches claims");
        assertNotNull(manager.find(occupant.id()), "the blocking occupant is untouched");
        assertEquals(List.of(rejected), manager.findAt(cellA));
        assertEquals(3, manager.instances().size(), "no instance leaked out of the registry");
    }

    @Test
    void pistonMoveConflictInvalidatesOnlyTheConflictingInstance() {
        StructureManager manager = StructureManager.create();
        StructureInstance conflicting = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance moving = form(manager, worldA, ownerB, Siting.of(cellE, Orientation.identity), cellE);
        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(
            ledger,
            ownerB,
            Siting.of(cellE, Orientation.identity),
            List.of(row(ownerB, Siting.of(cellE, Orientation.identity), cellE, Sharing.shared))
        );
        Siting incumbentSiting = Siting.of(cellD, Orientation.identity);
        settle(ledger, ownerC, incumbentSiting, List.of(row(ownerC, incumbentSiting, cellD, Sharing.exclusive)));
        long base = ledger.revision();
        assertEquals(1, ledger.read(readBox(cellD)).rows().size());

        assertEquals(MoveOutcome.invalidated, applyMove(manager, ledger, conflicting, 1, 0, 0));
        assertEquals(MoveOutcome.moved, applyMove(manager, ledger, moving, 1, 0, 0));

        assertNull(manager.find(conflicting.id()), "a conflicting translation invalidates the instance");
        assertTrue(ledger.read(readBox(cellA)).rows().isEmpty(), "the conflicting old row is released");
        assertEquals(1, ledger.read(readBox(cellD)).rows().size(), "the incumbent row survives the invalidation");
        assertEquals(ownerC, ledger.read(readBox(cellD)).rows().get(0).owner());
        assertTrue(
            ledger.read(readBox(cellD)).rows().stream().noneMatch(row -> row.owner().equals(ownerA)),
            "no translated row from the invalidated instance remains"
        );
        StructureInstance moved = manager.find(moving.id());
        assertNotNull(moved);
        assertEquals(cellF, moved.siting().origin());
        assertEquals(1, ledger.read(readBox(cellF)).rows().size(), "the accepted peer translates its row");
        assertEquals(base + 3, ledger.revision(), "one invalidation release plus one move settle/release");
    }

    @Test
    void siblingSitingsInTwoWorldsStayIndependentAndFindIsAmbiguous() {
        StructureManager manager = StructureManager.create();
        StructureInstance inWorldA = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance inWorldB = form(manager, worldB, ownerA, atOrigin, cellA);
        assertNotEquals(inWorldA.id(), inWorldB.id());
        assertEquals(inWorldA, manager.find(worldA, ownerA, atOrigin));
        assertEquals(inWorldB, manager.find(worldB, ownerA, atOrigin));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.find(ownerA, atOrigin),
            "the convenience find is ambiguous across worlds"
        );
        assertEquals(manager.instances(), manager.findAt(cellA), "findAt lists both worlds' instances");

        SavedClaims ledgerA = new SavedClaims(worldA);
        SavedClaims ledgerB = new SavedClaims(worldB);
        settle(ledgerA, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledgerB, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        assertEquals(1, ledgerA.revision());
        assertEquals(1, ledgerB.revision());

        ledgerA.release(ownerA, atOrigin);
        assertEquals(2, ledgerA.revision());
        assertTrue(ledgerA.read(readBox(cellA)).rows().isEmpty());
        assertEquals(1, ledgerB.revision(), "the other world's ledger is untouched");
        assertEquals(1, ledgerB.read(readBox(cellA)).rows().size());

        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(inWorldA)));
        assertEquals(inWorldA, manager.find(inWorldA.id()));
        assertNotNull(manager.find(inWorldB.id()), "Loaded of one world leaves the sibling world alone");

        manager.onEvent(new StructureEvent.Unloaded(worldB));
        assertNull(manager.find(inWorldB.id()), "Unloaded drops only the named world");
        assertNotNull(manager.find(inWorldA.id()));
        assertEquals(1, ledgerB.revision(), "an unload never touches persisted claim rows");
        assertEquals(1, ledgerB.read(readBox(cellA)).rows().size());
    }

    @Test
    void multiWorldLoadedIsAtomicAndKeepsForeignWorldsAndClaims() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance second = form(manager, worldA, ownerB, atOrigin, cellB);
        StructureInstance sibling = form(manager, worldB, ownerC, atOrigin, cellA);
        StructureAttempt draftA = manager.begin(worldA, new StructureSpec(ownerD, atOrigin, blueprint));
        StructureAttempt draftB = manager.begin(worldB, new StructureSpec(ownerE, atOrigin, blueprint));
        SavedClaims ledgerA = new SavedClaims(worldA);
        SavedClaims ledgerB = new SavedClaims(worldB);
        settle(ledgerA, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledgerB, ownerC, atOrigin, List.of(row(ownerC, atOrigin, cellA, Sharing.shared)));
        long revisionA = ledgerA.revision();
        long revisionB = ledgerB.revision();

        InstanceId restoredId = new InstanceId(worldA, 50);
        StructureInstance restored = instance(restoredId, ownerD, Siting.of(cellC, Orientation.identity), box(cellC));
        manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored)));

        assertNull(manager.find(first.id()), "Loaded replaces the whole world's registry");
        assertNull(manager.find(second.id()));
        assertEquals(restored, manager.find(restoredId));
        assertNotNull(manager.find(sibling.id()), "a foreign world's instance survives");
        assertThrows(
            IllegalStateException.class,
            () -> draftA.commit(box(cellD)),
            "Loaded closes the restored world's open drafts"
        );
        draftB.publish(draftB.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        draftB.abort();
        assertEquals(revisionA, ledgerA.revision(), "Loaded never writes claim rows");
        assertEquals(revisionB, ledgerB.revision(), "another world's ledger stays untouched");
        assertEquals(1, ledgerA.read(readBox(cellA)).rows().size());

        InstanceId foreignId = new InstanceId(worldB, 60);
        StructureInstance foreign = instance(foreignId, ownerE, Siting.of(cellD, Orientation.identity), box(cellD));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(worldA, List.of(restored, foreign))),
            "a Loaded list must belong to its world"
        );
        assertEquals(restored, manager.find(restoredId), "a rejected Loaded keeps the registry atomic");
        assertNotNull(manager.find(sibling.id()));

        manager.onEvent(new StructureEvent.Loaded(worldB, List.of()));
        assertNull(manager.find(sibling.id()), "an empty Loaded clears only its world");
        assertEquals(restored, manager.find(restoredId));
        assertThrows(
            IllegalStateException.class,
            () -> draftB.commit(box(cellD)),
            "an empty Loaded closes that world's open drafts"
        );
        assertEquals(revisionB, ledgerB.revision());

        StructureAttempt next = manager.begin(worldA, new StructureSpec(ownerE, atOrigin, blueprint));
        assertEquals(51, next.id().serial(), "Loaded raises the session serial watermark");
        next.abort();
    }

    @Test
    void serialIsSessionScopedAndBulkRemovalNeverReusesIt() {
        StructureManager manager = StructureManager.create();
        List<StructureInstance> formed = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            formed.add(form(manager, worldA, owner("bulk_" + index), atOrigin, cellA));
        }
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), formed.stream().map(instance -> instance.id().serial()).toList());
        for (StructureInstance instance : formed) {
            manager.onEvent(new StructureEvent.Broken(instance.id()));
        }
        assertTrue(manager.instances().isEmpty());
        assertEquals(0, manager.context(formed.get(0).id()).revision());
        StructureAttempt next = manager.begin(worldA, new StructureSpec(ownerA, atOrigin, blueprint));
        assertEquals(6, next.id().serial(), "the serial continues after bulk removal");
        next.abort();

        InstanceId restoredId = new InstanceId(worldA, 100);
        manager.onEvent(
            new StructureEvent.Loaded(
                worldA,
                List.of(instance(restoredId, ownerB, Siting.of(cellB, Orientation.identity), box(cellB)))
            )
        );
        StructureAttempt afterLoad = manager.begin(worldA, new StructureSpec(ownerC, atOrigin, blueprint));
        assertEquals(101, afterLoad.id().serial(), "Loaded raises the serial watermark");
        afterLoad.abort();
        assertNull(manager.find(formed.get(4).id()), "a removed id is never resurrected");
        assertEquals(0, manager.context(formed.get(4).id()).revision());
    }

    @Test
    void duplicateBreakAndPlacementEventsNeverDoubleRelease() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance second = form(manager, worldA, ownerB, atOrigin, cellA);
        StructureInstance far = form(manager, worldA, ownerC, atOrigin, cellB);
        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerC, atOrigin, List.of(row(ownerC, atOrigin, cellB, Sharing.shared)));
        long base = ledger.revision();

        applyBroken(manager, ledger, List.of(cellA));
        applyBroken(manager, ledger, List.of(cellA));
        assertEquals(base + 2, ledger.revision(), "a duplicate break releases nothing more");
        assertNull(manager.find(first.id()));
        assertNull(manager.find(second.id()));

        applyInvalidated(manager, ledger, List.of(cellB));
        applyInvalidated(manager, ledger, List.of(cellB));
        assertEquals(base + 3, ledger.revision(), "a duplicate placement invalidation releases once");
        assertNull(manager.find(far.id()));
        assertTrue(ledger.read(readBox(cellA)).rows().isEmpty());
        assertTrue(ledger.read(readBox(cellB)).rows().isEmpty());
        assertTrue(manager.instances().isEmpty());
    }

    @Test
    void sameSitingReplacementKeepsOverlappingPeersAndClaimKeys() {
        StructureManager manager = StructureManager.create();
        StructureInstance old = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance peer = form(manager, worldA, ownerB, atOrigin, cellA);
        SavedClaims ledger = new SavedClaims(worldA);
        settle(ledger, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledger, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellA, Sharing.shared)));
        assertEquals(2, ledger.revision());

        StructureAttempt replacement = manager.begin(worldA, new StructureSpec(ownerA, atOrigin, otherBlueprint));
        replacement.publish(replacement.id(), Map.of(count, 2L), FindingBundle.empty(), Spend.zero);
        StructureInstance replacementInstance = replacement.commit(box(cellA));

        assertNull(manager.find(old.id()), "the previous instance at the siting is replaced");
        assertEquals(0, manager.context(old.id()).revision(), "the replaced context is gone");
        assertNotNull(manager.find(peer.id()), "an overlapping peer of another owner survives");
        assertEquals(2L, replacementInstance.context().get(count));
        assertEquals(List.of(peer, replacementInstance), manager.findAt(cellA), "findAt is in id order");
        assertEquals(2, ledger.read(readBox(cellA)).rows().size(), "replacement never rewrites the claim key");
        assertEquals(2, ledger.revision(), "a manager replacement never touches the ledger");

        releaseInstance(ledger, replacementInstance);
        assertEquals(3, ledger.revision());
        assertEquals(1, ledger.read(readBox(cellA)).rows().size(), "only the replaced key's row is released");
        assertEquals(ownerB, ledger.read(readBox(cellA)).rows().get(0).owner());
    }

    @Test
    void rejectedMovesLeaveTheRegistryAndContextsIdentical() {
        StructureManager manager = StructureManager.create();
        StructureInstance instance = form(manager, worldA, ownerA, atOrigin, cellA);
        manager.publish(instance.id(), Map.of(count, 5L), FindingBundle.empty(), Spend.zero);
        Siting target = Siting.of(cellD, Orientation.identity);
        StructureInstance occupant = form(manager, worldA, ownerA, target, cellD);
        StructureInstance before = manager.find(instance.id());
        assertNotNull(before);
        assertEquals(2, before.context().revision());

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(instance.id(), atOrigin, target, box(cellD))),
            "a move onto an occupied siting is rejected"
        );
        assertEquals(before, manager.find(instance.id()), "a rejected move never mutates the instance");
        assertEquals(occupant, manager.find(occupant.id()));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(instance.id(), target, atOrigin, box(cellA))),
            "a move whose from siting does not match is rejected"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Moved(new InstanceId(worldA, 999), atOrigin, target, box(cellD))),
            "a move for an unknown id is rejected"
        );
        manager.onEvent(new StructureEvent.Unloaded(worldC));
        manager.onEvent(new StructureEvent.Broken(new InstanceId(worldA, 999)));
        manager.onEvent(new StructureEvent.Invalidated(new InstanceId(worldA, 999), "unknown"));
        assertEquals(before, manager.find(instance.id()), "unknown events are no-ops");
        assertEquals(2, manager.instances().size());

        manager.onEvent(new StructureEvent.Broken(occupant.id()));
        manager.onEvent(new StructureEvent.Moved(instance.id(), atOrigin, target, box(cellD)));
        StructureInstance moved = manager.find(instance.id());
        assertNotNull(moved);
        assertEquals(target, moved.siting());
        assertEquals(2, moved.context().revision(), "an accepted move preserves the context");
        assertEquals(5L, moved.context().get(count));
        assertTrue(manager.findAt(cellA).isEmpty());
        assertEquals(List.of(moved), manager.findAt(cellD));
    }

    @Test
    void removedDraftsAndFormedInstancesAreNotResurrected() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draft = manager.begin(worldA, new StructureSpec(ownerA, atOrigin, blueprint));
        draft.publish(draft.id(), Map.of(count, 3L), FindingBundle.empty(), Spend.zero);
        StructureContext removed = draft.remove(draft.id());
        assertNotNull(removed, "remove answers the draft context");
        assertEquals(3L, removed.get(count));
        assertNull(manager.remove(draft.id()), "a second remove finds nothing");
        assertThrows(IllegalStateException.class, () -> draft.commit(box(cellA)), "the draft is closed");
        assertThrows(IllegalStateException.class, draft::abort);
        assertThrows(
            IllegalStateException.class,
            () -> draft.publish(draft.id(), Map.of(count, 4L), FindingBundle.empty(), Spend.zero)
        );
        manager.onEvent(new StructureEvent.Broken(draft.id()));
        manager.onEvent(new StructureEvent.Invalidated(draft.id(), "unknown"));
        assertNull(manager.find(draft.id()), "an event never resurrects a removed draft");
        assertEquals(0, manager.context(draft.id()).revision());

        StructureInstance formed = form(manager, worldA, ownerB, atOrigin, cellA);
        StructureContext formedContext = manager.remove(formed.id());
        assertNotNull(formedContext);
        assertEquals(1, formedContext.revision());
        assertNull(manager.find(formed.id()));
        assertEquals(0, manager.context(formed.id()).revision());
        manager.onEvent(new StructureEvent.Broken(formed.id()));
        assertTrue(manager.instances().isEmpty(), "a broken unknown id leaves no registry entry");
    }

    @Test
    void worldUnloadDropsInstancesAndDraftsButKeepsPersistedClaims() {
        StructureManager manager = StructureManager.create();
        StructureInstance inWorldA = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance inWorldB = form(manager, worldB, ownerB, atOrigin, cellB);
        StructureAttempt draftA = manager.begin(worldA, new StructureSpec(ownerC, atOrigin, blueprint));
        StructureAttempt draftB = manager.begin(worldB, new StructureSpec(ownerD, atOrigin, blueprint));
        SavedClaims ledgerA = new SavedClaims(worldA);
        SavedClaims ledgerB = new SavedClaims(worldB);
        settle(ledgerA, ownerA, atOrigin, List.of(row(ownerA, atOrigin, cellA, Sharing.shared)));
        settle(ledgerB, ownerB, atOrigin, List.of(row(ownerB, atOrigin, cellB, Sharing.shared)));
        long revisionA = ledgerA.revision();
        long revisionB = ledgerB.revision();

        manager.onEvent(new StructureEvent.Unloaded(worldA));

        assertNull(manager.find(inWorldA.id()), "an unload drops that world's formed instances");
        assertNotNull(manager.find(inWorldB.id()), "another world's instance survives");
        assertThrows(
            IllegalStateException.class,
            () -> draftA.commit(box(cellD)),
            "an unload closes that world's drafts"
        );
        draftB.publish(draftB.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        draftB.abort();
        assertEquals(revisionA, ledgerA.revision(), "an unload never releases persisted claims");
        assertEquals(1, ledgerA.read(readBox(cellA)).rows().size());
        assertEquals(revisionB, ledgerB.revision(), "another world's ledger is untouched");

        StructureInstance recreated = form(manager, worldA, ownerA, atOrigin, cellA);
        assertNotNull(manager.find(recreated.id()));
        releaseInstance(ledgerA, recreated);
        assertEquals(revisionA + 1, ledgerA.revision(), "the persisted rows are still releasable after reload");
        assertTrue(ledgerA.read(readBox(cellA)).rows().isEmpty());
        assertEquals(1, ledgerB.read(readBox(cellB)).rows().size());
    }

    private enum MoveOutcome {
        moved, rejected, invalidated
    }

    private static StructureInstance form(
        StructureManager manager,
        World world,
        OwnerId owner,
        Siting siting,
        LatticePoint cell
    ) {
        return formBounds(manager, world, owner, siting, box(cell));
    }

    private static StructureInstance formBounds(
        StructureManager manager,
        World world,
        OwnerId owner,
        Siting siting,
        CuboidSet bounds
    ) {
        StructureAttempt attempt = manager.begin(world, new StructureSpec(owner, siting, blueprint));
        attempt.publish(attempt.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        return attempt.commit(bounds);
    }

    private static StructureInstance instance(InstanceId id, OwnerId owner, Siting siting, CuboidSet bounds) {
        return new StructureInstance(
            id,
            new StructureSpec(owner, siting, blueprint),
            StructureContext.empty(id),
            bounds
        );
    }

    private static OwnerId owner(String path) {
        return OwnerId.of(NamespacedId.of("test", "expanded_" + path));
    }

    private static ClaimRow row(OwnerId owner, Siting siting, LatticePoint cell, Sharing sharing) {
        return new ClaimRow(LatticeKey.of(cell), sharing, owner, siting, claimBay);
    }

    private static void settle(SavedClaims ledger, OwnerId owner, Siting siting, List<ClaimRow> rows) {
        ClaimVerdict verdict = ledger.settle(new SettleRequest(owner, siting, rows, ledger.revision()));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            throw new AssertionError("the settle was rejected: " + verdict);
        }
    }

    private static void releaseInstance(SavedClaims ledger, StructureInstance instance) {
        ledger.release(instance.owner(), instance.siting());
    }

    private static void applyBroken(StructureManager manager, SavedClaims ledger, List<LatticePoint> cells) {
        Set<InstanceId> seen = new HashSet<>();
        for (LatticePoint cell : cells) {
            for (StructureInstance instance : manager.findAt(cell)) {
                if (seen.add(instance.id())) {
                    manager.onEvent(new StructureEvent.Broken(instance.id()));
                    releaseInstance(ledger, instance);
                }
            }
        }
    }

    private static void applyInvalidated(StructureManager manager, SavedClaims ledger, List<LatticePoint> cells) {
        Set<InstanceId> seen = new HashSet<>();
        for (LatticePoint cell : cells) {
            for (StructureInstance instance : manager.findAt(cell)) {
                if (seen.add(instance.id())) {
                    manager.onEvent(new StructureEvent.Invalidated(instance.id(), "a block was placed"));
                    releaseInstance(ledger, instance);
                }
            }
        }
    }

    private static MoveOutcome applyMove(
        StructureManager manager,
        SavedClaims ledger,
        StructureInstance instance,
        int dx,
        int dy,
        int dz
    ) {
        CuboidSet movedBounds = Objects.requireNonNull(instance.bounds().translate(dx, dy, dz), "translated bounds");
        LatticePoint movedOrigin = Objects.requireNonNull(instance.siting().origin().offset(dx, dy, dz));
        Siting to = instance.siting().withOrigin(movedOrigin);
        try {
            manager.onEvent(new StructureEvent.Moved(instance.id(), instance.siting(), to, movedBounds));
        } catch (IllegalArgumentException rejected) {
            return MoveOutcome.rejected;
        }
        Cuboid oldBounds = Objects.requireNonNull(instance.bounds().bounds(), "old bounds");
        List<ClaimRow> moved = new ArrayList<>();
        for (ClaimRow row : ledger.read(oldBounds).rows()) {
            if (!row.owner().equals(instance.owner()) || !row.siting().equals(instance.siting())) {
                continue;
            }
            LatticePoint next = Objects.requireNonNull(row.cell().point().offset(dx, dy, dz));
            moved.add(new ClaimRow(LatticeKey.of(next), row.sharing(), row.owner(), to, row.bay()));
        }
        if (moved.isEmpty()) {
            return MoveOutcome.moved;
        }
        ClaimVerdict verdict = ledger.settle(new SettleRequest(instance.owner(), to, moved, ledger.revision()));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            manager.onEvent(new StructureEvent.Invalidated(instance.id(), "the moved claims conflict"));
            ledger.release(instance.owner(), instance.siting());
            ledger.release(instance.owner(), to);
            return MoveOutcome.invalidated;
        }
        ledger.release(instance.owner(), instance.siting());
        return MoveOutcome.moved;
    }

    private static CuboidSet box(LatticePoint cell) {
        return CuboidSet.of(Cuboid.of(cell));
    }

    private static Cuboid readBox(LatticePoint cell) {
        return Cuboid.of(cell);
    }
}
