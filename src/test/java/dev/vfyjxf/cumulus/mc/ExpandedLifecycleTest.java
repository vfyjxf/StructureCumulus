package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
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
        StructureInstance second = form(manager, worldA, ownerB, siting(1), cellA);
        StructureInstance third = form(manager, worldA, ownerC, siting(2), cellA);
        assertEquals(List.of(first, second, third), manager.findAt(cellA), "findAt is in instance id order");
        assertEquals(List.of(first, second, third), manager.instances());

        ClaimLedger ledger = ClaimLedger.inMemory(worldA);
        settle(ledger, first.id(), List.of(row(first.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledger, second.id(), List.of(row(second.id(), siting(1), cellA, Sharing.shared)));
        settle(ledger, third.id(), List.of(row(third.id(), siting(2), cellA, Sharing.shared)));
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
        StructureInstance sharedSecond = form(manager, worldA, ownerB, siting(1), cellA);
        StructureInstance far = form(manager, worldA, ownerC, siting(2), cellB);
        ClaimLedger ledger = ClaimLedger.inMemory(worldA);
        settle(ledger, sharedFirst.id(), List.of(row(sharedFirst.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledger, sharedSecond.id(), List.of(row(sharedSecond.id(), siting(1), cellA, Sharing.shared)));
        settle(ledger, far.id(), List.of(row(far.id(), siting(2), cellB, Sharing.shared)));
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
        StructureInstance second = form(manager, worldA, ownerB, siting(1), cellA);
        StructureInstance third = form(manager, worldA, ownerC, siting(2), cellB);
        StructureInstance far = form(manager, worldA, ownerD, siting(3), cellC);
        ClaimLedger ledger = ClaimLedger.inMemory(worldA);
        settle(ledger, first.id(), List.of(row(first.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledger, second.id(), List.of(row(second.id(), siting(1), cellA, Sharing.shared)));
        settle(ledger, third.id(), List.of(row(third.id(), siting(2), cellB, Sharing.shared)));
        settle(ledger, far.id(), List.of(row(far.id(), siting(3), cellC, Sharing.shared)));
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

        ClaimLedger ledgerA = ClaimLedger.inMemory(worldA);
        ClaimLedger ledgerB = ClaimLedger.inMemory(worldB);
        settle(ledgerA, inWorldA.id(), List.of(row(inWorldA.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledgerB, inWorldA.id(), List.of(row(inWorldA.id(), atOrigin, cellA, Sharing.shared)));
        assertEquals(1, ledgerA.revision());
        assertEquals(1, ledgerB.revision());

        ledgerA.release(inWorldA.id());
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
        ClaimLedger ledgerA = ClaimLedger.inMemory(worldA);
        ClaimLedger ledgerB = ClaimLedger.inMemory(worldB);
        settle(ledgerA, first.id(), List.of(row(first.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledgerB, sibling.id(), List.of(row(sibling.id(), atOrigin, cellA, Sharing.shared)));
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
        StructureInstance second = form(manager, worldA, ownerB, siting(1), cellA);
        StructureInstance far = form(manager, worldA, ownerC, siting(2), cellB);
        ClaimLedger ledger = ClaimLedger.inMemory(worldA);
        settle(ledger, first.id(), List.of(row(first.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledger, second.id(), List.of(row(second.id(), siting(1), cellA, Sharing.shared)));
        settle(ledger, far.id(), List.of(row(far.id(), siting(2), cellB, Sharing.shared)));
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
        StructureInstance peer = form(manager, worldA, ownerB, siting(1), cellA);
        ClaimLedger ledger = ClaimLedger.inMemory(worldA);
        settle(ledger, old.id(), List.of(row(old.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledger, peer.id(), List.of(row(peer.id(), siting(1), cellA, Sharing.shared)));
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
        assertEquals(2, ledger.revision(), "a replacement owns no rows until the host settles them");
        releaseInstance(ledger, old);
        assertEquals(3, ledger.revision());
        assertEquals(1, ledger.read(readBox(cellA)).rows().size(), "only the replaced instance's row is released");
        assertEquals(peer.id(), ledger.read(readBox(cellA)).rows().get(0).instance());
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
    void worldUnloadDropsInstancesAndDraftsButLeavesTheHostLedgerAlone() {
        StructureManager manager = StructureManager.create();
        StructureInstance inWorldA = form(manager, worldA, ownerA, atOrigin, cellA);
        StructureInstance inWorldB = form(manager, worldB, ownerB, atOrigin, cellB);
        StructureAttempt draftA = manager.begin(worldA, new StructureSpec(ownerC, atOrigin, blueprint));
        StructureAttempt draftB = manager.begin(worldB, new StructureSpec(ownerD, atOrigin, blueprint));
        ClaimLedger ledgerA = ClaimLedger.inMemory(worldA);
        ClaimLedger ledgerB = ClaimLedger.inMemory(worldB);
        settle(ledgerA, inWorldA.id(), List.of(row(inWorldA.id(), atOrigin, cellA, Sharing.shared)));
        settle(ledgerB, inWorldB.id(), List.of(row(inWorldB.id(), atOrigin, cellB, Sharing.shared)));
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
        assertEquals(revisionA, ledgerA.revision(), "a manager unload never touches the host ledger");
        assertEquals(1, ledgerA.read(readBox(cellA)).rows().size());
        assertEquals(revisionB, ledgerB.revision(), "another world's ledger is untouched");

        releaseInstance(ledgerA, inWorldA);
        assertEquals(revisionA + 1, ledgerA.revision(), "the host releases the rows by instance");
        assertTrue(ledgerA.read(readBox(cellA)).rows().isEmpty());
        assertEquals(1, ledgerB.read(readBox(cellB)).rows().size());
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

    private static Siting siting(int x) {
        return Siting.of(LatticePoint.of(x, 0, 0), Orientation.identity);
    }

    private static ClaimRow row(InstanceId instance, Siting siting, LatticePoint cell, Sharing sharing) {
        return new ClaimRow(LatticeKey.of(cell), sharing, siting, claimBay, instance);
    }

    private static void settle(ClaimLedger ledger, InstanceId instance, List<ClaimRow> rows) {
        ClaimVerdict verdict = ledger.settle(new SettleRequest(instance, rows, ledger.revision()));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            throw new AssertionError("the settle was rejected: " + verdict);
        }
    }

    private static void releaseInstance(ClaimLedger ledger, StructureInstance instance) {
        ledger.release(instance.id());
    }

    private static void applyBroken(StructureManager manager, ClaimLedger ledger, List<LatticePoint> cells) {
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

    private static void applyInvalidated(StructureManager manager, ClaimLedger ledger, List<LatticePoint> cells) {
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

    private static CuboidSet box(LatticePoint cell) {
        return CuboidSet.of(Cuboid.of(cell));
    }

    private static Cuboid readBox(LatticePoint cell) {
        return Cuboid.of(cell);
    }
}
