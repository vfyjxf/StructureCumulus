package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
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
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial lifecycle checks on the host-driven structure manager and the level claim ledger:
 * begin/commit/abort/replace, context binding, in-memory-only persistence, lifecycle events and
 * claim row/revision invariants.
 */
class StructureLifecycleAdversarialTest {

    private static final World world = World.of(NamespacedId.of("test", "adversarial_world"));
    private static final World otherWorld = World.of(NamespacedId.of("test", "adversarial_other_world"));
    private static final OwnerId ownerA = OwnerId.of(NamespacedId.of("test", "adversarial_owner_a"));
    private static final OwnerId ownerB = OwnerId.of(NamespacedId.of("test", "adversarial_owner_b"));
    private static final NamespacedId blueprint = NamespacedId.of("test", "adversarial_blueprint");
    private static final NamespacedId otherBlueprint = NamespacedId.of("test", "adversarial_other_blueprint");
    private static final NamespacedId bay = NamespacedId.of("test", "adversarial_bay");
    private static final Siting origin = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final Siting secondSiting = Siting.of(LatticePoint.of(1, 0, 0), Orientation.identity);
    private static final Siting movedOrigin = Siting.of(LatticePoint.of(10, 0, 0), Orientation.identity);
    private static final CuboidSet oneCell = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
    private static final Cuboid oneCellBox = Cuboid.of(0, 0, 0, 1, 1, 1);
    private static final Cuboid twoCellBox = Cuboid.of(0, 0, 0, 2, 1, 1);
    private static final Cuboid movedBox = Cuboid.of(10, 0, 0, 12, 1, 1);
    private static final Cuboid wideBox = Cuboid.of(-8, -8, -8, 24, 8, 24);
    private static final ContextKey<Long> count = ContextKeys
            .of(NamespacedId.of("test", "adversarial_count"), Long.class, Retention.instance);
    private static final ContextKey<Long> matchCount = ContextKeys
            .of(NamespacedId.of("test", "adversarial_match_count"), Long.class, Retention.match);

    @Test
    void beginCommitAbortReplaceAndFindByPlace() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draft = manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        assertEquals(new InstanceId(world, 1), draft.id());
        assertTrue(manager.instances().isEmpty(), "a draft is not a formed instance");
        assertEquals(0, manager.context(draft.id()).revision(), "a draft context starts empty");
        draft.publish(draft.id(), Map.of(count, 7L), FindingBundle.empty(), new Spend(2, 1));
        assertEquals(7L, manager.context(draft.id()).get(count), "the draft is visible through the manager");

        StructureInstance formed = draft.commit(oneCell);
        assertEquals(draft.id(), formed.id());
        assertEquals(1, formed.context().revision());
        assertEquals(formed, manager.find(formed.id()));
        assertEquals(formed, manager.find(ownerA, origin));
        assertEquals(List.of(formed), manager.findAt(LatticePoint.of(0, 0, 0)));
        assertTrue(manager.findAt(LatticePoint.of(5, 0, 5)).isEmpty(), "a cell outside the bounds is not found");
        assertEquals(1, manager.findIn(oneCell).size());
        assertTrue(
            manager.findIn(CuboidSet.of(Cuboid.of(9, 9, 9, 10, 10, 10))).isEmpty(),
            "a box outside the bounds is not found"
        );
        assertEquals(oneCell, formed.bounds(), "the bounds are stored as declared");
        assertThrows(IllegalStateException.class, () -> draft.commit(oneCell), "a committed attempt is closed");
        assertThrows(IllegalStateException.class, draft::abort, "a committed attempt cannot be aborted");

        StructureAttempt aborted = manager.begin(
            world,
            new StructureSpec(ownerA, Siting.of(LatticePoint.of(4, 0, 4), Orientation.identity), blueprint)
        );
        long abortedSerial = aborted.id().serial();
        aborted.abort();
        assertNull(manager.find(aborted.id()), "an aborted attempt never forms");
        assertEquals(0, manager.context(aborted.id()).revision(), "an aborted draft context is gone");
        StructureAttempt next = manager.begin(
            world,
            new StructureSpec(ownerA, Siting.of(LatticePoint.of(4, 0, 4), Orientation.identity), blueprint)
        );
        assertEquals(abortedSerial + 1, next.id().serial(), "an aborted attempt does not release the serial");

        StructureAttempt replacement = manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        replacement.publish(replacement.id(), Map.of(count, 9L), FindingBundle.empty(), Spend.zero);
        StructureInstance replaced = replacement.commit(oneCell);
        assertNull(manager.find(formed.id()), "a later commit at the same siting replaces the instance");
        assertEquals(1, manager.instances().size());
        assertEquals(replaced, manager.find(ownerA, origin));
        assertEquals(0, manager.context(formed.id()).revision(), "the replaced context is not left behind");
    }

    @Test
    void beginRejectsADuplicateDraftButAllowsReplacingAFormedInstance() {
        StructureManager manager = StructureManager.create();
        manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.begin(world, new StructureSpec(ownerA, origin, otherBlueprint)),
            "two drafts may not share one (owner, siting)"
        );

        StructureManager recreated = StructureManager.create();
        StructureInstance formed = form(recreated, ownerA, origin, oneCell);
        StructureAttempt replacement = recreated.begin(world, new StructureSpec(ownerA, origin, otherBlueprint));
        replacement.abort();
        assertNotNull(recreated.find(formed.id()), "aborting the replacement keeps the formed instance");

        StructureAttempt committed = recreated.begin(world, new StructureSpec(ownerA, origin, otherBlueprint));
        StructureInstance replaced = committed.commit(oneCell);
        assertNull(recreated.find(formed.id()));
        assertEquals(otherBlueprint, replaced.spec().structure());
        assertEquals(1, recreated.instances().size());
    }

    @Test
    void contextBindingRejectsForeignInstancesAndMatchValues() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draft = manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        assertThrows(
            IllegalArgumentException.class,
            () -> draft.publish(new InstanceId(world, 99), Map.of(count, 1L), FindingBundle.empty(), Spend.zero),
            "a draft only publishes for its own instance id"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> draft.publish(draft.id(), Map.of(matchCount, 1L), FindingBundle.empty(), Spend.zero),
            "a match value is never published"
        );
        StructureInstance formed = draft.commit(oneCell);
        assertEquals(attemptId(draft), formed.context().instance());

        assertThrows(
            IllegalArgumentException.class,
            () -> new StructureInstance(
                formed.id(),
                formed.spec(),
                StructureContext.empty(new InstanceId(world, 77)),
                oneCell
            ),
            "a context must belong to its instance"
        );

        assertEquals(0, formed.context().revision(), "both earlier publishes were rejected before commit");
        StructureContext updated = manager
                .publish(formed.id(), Map.of(count, 3L), FindingBundle.empty(), new Spend(1, 0));
        assertEquals(1, updated.revision(), "the first successful publish on the formed context is revision 1");
        assertEquals(3L, updated.get(count));

        manager.onEvent(new StructureEvent.Broken(formed.id()));
        assertNull(manager.find(formed.id()));
        assertEquals(0, manager.context(formed.id()).revision(), "broken removes the context with the instance");
        assertNull(manager.remove(formed.id()), "a second removal finds nothing");
    }

    @Test
    void managerRegistryIsInMemoryAndContextsAreNotPersisted() {
        StructureManager first = StructureManager.create();
        StructureInstance instance = form(first, ownerA, origin, oneCell);

        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, instance.id(), List.of(row(instance.id(), origin, LatticePoint.origin, Sharing.exclusive)));
        long revision = ledger.revision();
        assertEquals(1, ledger.read(wideBox).rows().size());

        StructureManager recreated = StructureManager.create();
        assertNull(recreated.find(instance.id()), "a recreated manager does not restore instances");
        assertTrue(recreated.instances().isEmpty());
        assertTrue(recreated.findAt(LatticePoint.origin).isEmpty());
        assertEquals(0, recreated.context(instance.id()).revision(), "a recreated manager has no context");
        StructureAttempt replayed = recreated.begin(world, new StructureSpec(ownerA, origin, blueprint));
        assertEquals(instance.id().serial(), replayed.id().serial(), "a fresh manager restarts its serial watermark");

        assertEquals(revision, ledger.revision(), "manager recreation never touches the ledger");
        assertFalse(ledger.read(wideBox).rows().isEmpty(), "claims are world state and outlive the manager");
    }

    @Test
    void claimReleaseBumpsTheRevisionOnlyWhenItRemovesARow() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        assertEquals(0, ledger.revision());
        InstanceId instanceA = new InstanceId(world, 1_000_000L);
        InstanceId instanceB = new InstanceId(world, 1_000_001L);
        settle(
            ledger,
            instanceA,
            List.of(
                row(instanceA, origin, LatticePoint.of(0, 0, 0), Sharing.exclusive),
                row(instanceA, origin, LatticePoint.of(1, 0, 0), Sharing.exclusive)
            )
        );
        assertEquals(1, ledger.revision(), "the settle bumps the revision once");
        assertEquals(2, ledger.read(wideBox).rows().size());

        ClaimVerdict released = ledger.release(instanceA);
        assertTrue(released instanceof ClaimVerdict.Settled);
        assertEquals(2, ((ClaimVerdict.Settled) released).revision());
        assertEquals(2, ledger.revision());
        assertTrue(ledger.read(wideBox).rows().isEmpty(), "release removes every row of the instance");

        ClaimVerdict again = ledger.release(instanceA);
        assertEquals(2, ((ClaimVerdict.Settled) again).revision(), "a no-op release does not bump");
        assertEquals(2, ledger.revision());
        ledger.release(instanceB);
        assertEquals(2, ledger.revision(), "releasing an absent instance does not bump");
    }

    @Test
    void claimTranslateSettlesNewRowsThenReleasesOldAndCountsThem() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        InstanceId moving = new InstanceId(world, 1_000_000L);
        InstanceId translatedInstance = new InstanceId(world, 1_000_001L);
        settle(
            ledger,
            moving,
            List.of(
                row(moving, origin, LatticePoint.of(0, 0, 0), Sharing.exclusive),
                row(moving, origin, LatticePoint.of(1, 0, 0), Sharing.exclusive)
            )
        );
        assertEquals(1, ledger.revision());

        ClaimView old = ledger.read(twoCellBox);
        assertEquals(2, old.rows().size());
        List<ClaimRow> translated = new ArrayList<>();
        for (ClaimRow row : old.rows()) {
            LatticePoint next = row.cell().point().offset(10, 0, 0);
            assertNotNull(next);
            translated.add(new ClaimRow(LatticeKey.of(next), row.sharing(), movedOrigin, row.bay(), translatedInstance));
        }
        settle(ledger, translatedInstance, translated);
        assertEquals(2, ledger.revision(), "the translated settle bumps once");
        assertEquals(2, ledger.read(twoCellBox).rows().size(), "the old rows remain until the release");
        assertEquals(2, ledger.read(movedBox).rows().size());

        ledger.release(moving);
        assertEquals(3, ledger.revision(), "the release of the old instance bumps once");
        assertEquals(2, ledger.read(movedBox).rows().size(), "the translated rows survive the old release");
        assertEquals(movedOrigin, ledger.read(movedBox).rows().get(0).siting());
        assertEquals(2, ledger.read(wideBox).rows().size(), "the translation never duplicates a row");

        assertThrows(
            IllegalArgumentException.class,
            () -> new SettleRequest(translatedInstance, List.of(), 1L),
            "an empty settle is rejected at construction; dropping a key is a release"
        );

        ClaimVerdict stale = ledger.settle(
            new SettleRequest(
                translatedInstance,
                List.of(row(translatedInstance, movedOrigin, LatticePoint.of(10, 0, 0), Sharing.exclusive)),
                1L
            )
        );
        assertEquals(new ClaimVerdict.RevisionMoved(1L, 3L), stale, "a stale non-empty settle is refused");
        assertEquals(3, ledger.revision(), "a stale settle writes nothing");
        assertEquals(2, ledger.read(wideBox).rows().size());
    }

    @Test
    void claimTranslateConflictLeavesBothSidesUntouchedUntilTheRelease() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        InstanceId incumbent = new InstanceId(world, 1_000_000L);
        InstanceId pending = new InstanceId(world, 1_000_001L);
        Siting incumbentSiting = Siting.of(LatticePoint.of(12, 0, 0), Orientation.identity);
        settle(
            ledger,
            incumbent,
            List.of(row(incumbent, incumbentSiting, LatticePoint.of(10, 0, 0), Sharing.exclusive))
        );
        settle(ledger, pending, List.of(row(pending, origin, LatticePoint.origin, Sharing.exclusive)));
        assertEquals(2, ledger.revision());

        ClaimVerdict verdict = ledger.settle(
            new SettleRequest(
                pending,
                List.of(row(pending, movedOrigin, LatticePoint.of(10, 0, 0), Sharing.exclusive)),
                2L
            )
        );
        assertTrue(verdict instanceof ClaimVerdict.Conflict, "an exclusive incumbents blocks the translation");
        assertEquals(2, ledger.revision(), "a conflict writes nothing");
        assertEquals(1, ledger.read(oneCellBox).rows().size(), "the pending row is untouched");
        ClaimView target = ledger.read(movedBox);
        assertEquals(1, target.rows().size());
        assertEquals(incumbent, target.rows().get(0).instance());
        assertTrue(
            target.rows().stream().noneMatch(row -> row.instance().equals(pending)),
            "the conflicting row is not inserted"
        );

        ledger.release(pending);
        assertEquals(3, ledger.revision());
        assertTrue(ledger.read(oneCellBox).rows().isEmpty());
        assertEquals(1, ledger.read(movedBox).rows().size(), "the incumbent survives the invalidation");
        assertEquals(incumbent, ledger.read(movedBox).rows().get(0).instance());
    }

    @Test
    void unknownIdsOutsideBoundsAndRejectedMovesDoNotCorruptTheRegistry() {
        StructureManager manager = StructureManager.create();
        StructureInstance instance = form(manager, ownerA, origin, oneCell);
        StructureInstance other = form(
            manager,
            ownerA,
            Siting.of(LatticePoint.of(10, 0, 10), Orientation.identity),
            CuboidSet.of(Cuboid.of(10, 0, 10, 11, 1, 11))
        );

        manager.onEvent(new StructureEvent.Broken(new InstanceId(world, 999)));
        manager.onEvent(new StructureEvent.Invalidated(new InstanceId(world, 999), "unknown"));
        manager.onEvent(new StructureEvent.Unloaded(otherWorld));
        assertNotNull(manager.find(instance.id()), "unknown ids and other worlds are no-ops");
        assertTrue(manager.findAt(LatticePoint.of(-4, 0, -4)).isEmpty());

        StructureInstance unchanged = manager.find(instance.id());
        assertNotNull(unchanged, "a rejected event never removes the instance");
        assertEquals(origin, unchanged.siting(), "a rejected event leaves the siting unchanged");
        assertEquals(oneCell, unchanged.bounds(), "a rejected event leaves the bounds unchanged");

        manager.onEvent(new StructureEvent.Broken(instance.id()));
        manager.onEvent(new StructureEvent.Broken(instance.id()));
        assertNull(manager.find(instance.id()), "a duplicate Broken is idempotent");
        assertNotNull(manager.find(other.id()), "the unrelated instance is untouched");
    }

    @Test
    void aRejectedLoadedEventMustNotLoseTheCurrentRegistry() {
        StructureManager manager = StructureManager.create();
        StructureInstance kept = form(manager, ownerA, origin, oneCell);
        InstanceId incomingId = new InstanceId(world, 50);
        StructureInstance incoming = new StructureInstance(
            incomingId,
            new StructureSpec(ownerB, Siting.of(LatticePoint.of(20, 0, 20), Orientation.identity), blueprint),
            StructureContext.empty(incomingId),
            CuboidSet.of(Cuboid.of(20, 0, 20, 21, 1, 21))
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(world, List.of(incoming, incoming))),
            "a Loaded event with duplicate instances is rejected"
        );
        assertNotNull(manager.find(kept.id()), "a rejected Loaded must keep the previous registry");
        assertNull(manager.find(incomingId), "a rejected Loaded must not install its partial list");
        assertEquals(1, manager.instances().size());
    }

    @Test
    void overlappingInstancesAreFoundTogetherAndRemovedIndependently() {
        StructureManager manager = StructureManager.create();
        StructureInstance first = form(manager, ownerA, origin, oneCell);
        StructureInstance second = form(manager, ownerB, secondSiting, oneCell);
        assertEquals(List.of(first, second), manager.findAt(LatticePoint.origin), "overlaps are found in id order");
        assertEquals(first, manager.find(ownerA, origin));
        assertEquals(second, manager.find(ownerB, secondSiting));

        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, first.id(), List.of(row(first.id(), origin, LatticePoint.origin, Sharing.shared)));
        settle(ledger, second.id(), List.of(row(second.id(), secondSiting, LatticePoint.origin, Sharing.shared)));
        assertEquals(2, ledger.read(oneCellBox).rows().size(), "two shared instances hold one cell each");
        long revision = ledger.revision();

        manager.onEvent(new StructureEvent.Broken(first.id()));
        assertEquals(List.of(second), manager.findAt(LatticePoint.origin));
        ledger.release(first.id());
        assertEquals(revision + 1, ledger.revision(), "releasing one instance bumps once");
        assertEquals(1, ledger.read(oneCellBox).rows().size());
        assertEquals(second.id(), ledger.read(oneCellBox).rows().get(0).instance());
    }

    @Test
    void attemptRejectsWritesAfterCommitOrAbort() {
        StructureManager manager = StructureManager.create();
        StructureAttempt committed = manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        committed.commit(oneCell);
        boolean publishAfterCommit;
        try {
            committed.publish(committed.id(), Map.of(count, 2L), FindingBundle.empty(), Spend.zero);
            publishAfterCommit = false;
        } catch (IllegalStateException closed) {
            publishAfterCommit = true;
        }
        boolean removeAfterCommit;
        try {
            committed.remove(committed.id());
            removeAfterCommit = false;
        } catch (IllegalStateException closed) {
            removeAfterCommit = true;
        }

        StructureAttempt aborted = manager.begin(
            world,
            new StructureSpec(ownerA, Siting.of(LatticePoint.of(3, 0, 3), Orientation.identity), blueprint)
        );
        aborted.abort();
        boolean publishAfterAbort;
        try {
            aborted.publish(aborted.id(), Map.of(count, 2L), FindingBundle.empty(), Spend.zero);
            publishAfterAbort = false;
        } catch (IllegalStateException closed) {
            publishAfterAbort = true;
        }
        boolean removeAfterAbort;
        try {
            aborted.remove(aborted.id());
            removeAfterAbort = false;
        } catch (IllegalStateException closed) {
            removeAfterAbort = true;
        }

        assertTrue(
            publishAfterCommit && removeAfterCommit && publishAfterAbort && removeAfterAbort,
            "a closed StructureAttempt must reject publish/remove: publishAfterCommit=" + publishAfterCommit
                    + ", removeAfterCommit=" + removeAfterCommit + ", publishAfterAbort=" + publishAfterAbort
                    + ", removeAfterAbort=" + removeAfterAbort
        );
    }

    @Test
    void removingADraftContextMustNotLetCommitFormAnEmptyContext() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draft = manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        draft.publish(draft.id(), Map.of(count, 11L), FindingBundle.empty(), Spend.zero);
        StructureContext removed = draft.remove(draft.id());
        assertNotNull(removed, "remove answers the published draft context");
        assertEquals(11L, removed.get(count));

        boolean rejected = false;
        StructureInstance formed = null;
        try {
            formed = draft.commit(oneCell);
        } catch (IllegalStateException closed) {
            rejected = true;
        }
        Long formedValue = formed == null ? null : formed.context().get(count);
        long formedRevision = formed == null ? -1 : formed.context().revision();
        assertTrue(
            rejected || (formedValue != null && formedValue == 11L),
            "remove-then-commit must not silently form an empty context: rejected=" + rejected + ", contextRevision="
                    + formedRevision + ", value=" + formedValue
        );
    }

    @Test
    void loadedMustReplaceOnlyTheRestoredWorlds() {
        StructureManager manager = StructureManager.create();
        StructureInstance inWorld = formIn(manager, world, ownerA, origin, oneCell);
        Siting otherSiting = Siting.of(LatticePoint.of(5, 0, 5), Orientation.identity);
        StructureInstance inOtherWorld = formIn(
            manager,
            otherWorld,
            ownerB,
            otherSiting,
            CuboidSet.of(Cuboid.of(5, 0, 5, 6, 1, 6))
        );

        manager.onEvent(new StructureEvent.Loaded(world, List.of(inWorld)));

        assertEquals(inWorld, manager.find(inWorld.id()), "the restored world instance is installed");
        assertNotNull(
            manager.find(inOtherWorld.id()),
            "Loaded is per World; restoring one world must not clear another world's instances"
        );
    }

    @Test
    void loadedMustCloseOpenDrafts() {
        StructureManager manager = StructureManager.create();
        StructureAttempt draft = manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        manager.onEvent(new StructureEvent.Loaded(world, List.of()));

        boolean commitRejected = false;
        StructureInstance formed = null;
        try {
            formed = draft.commit(oneCell);
        } catch (IllegalStateException closed) {
            commitRejected = true;
        }
        assertTrue(
            commitRejected,
            "Loaded replaces the registry and must close open drafts; observed commit formed=" + (formed != null)
                    + ", registry=" + manager.instances().size()
        );
    }

    @Test
    void unloadedClosesItsDraftsAndDropsOnlyThatWorld() {
        StructureManager manager = StructureManager.create();
        StructureAttempt inWorld = manager.begin(world, new StructureSpec(ownerA, origin, blueprint));
        StructureAttempt inOther = manager.begin(
            otherWorld,
            new StructureSpec(ownerA, Siting.of(LatticePoint.of(5, 0, 5), Orientation.identity), blueprint)
        );
        manager.onEvent(new StructureEvent.Unloaded(world));

        assertThrows(IllegalStateException.class, () -> inWorld.commit(oneCell), "an unloaded draft is closed");
        assertThrows(IllegalStateException.class, inWorld::abort, "an unloaded draft is closed");
        StructureInstance other = inOther.commit(CuboidSet.of(Cuboid.of(5, 0, 5, 6, 1, 6)));
        assertEquals(other, manager.find(other.id()), "another world's draft survives its own world's unload");
    }

    @Test
    void loadedRaisesTheSerialWatermarkAndInstallsTheRegistry() {
        StructureManager manager = StructureManager.create();
        InstanceId restoredId = new InstanceId(world, 50);
        StructureInstance restored = new StructureInstance(
            restoredId,
            new StructureSpec(ownerB, origin, blueprint),
            StructureContext.empty(restoredId),
            oneCell
        );

        manager.onEvent(new StructureEvent.Loaded(world, List.of(restored)));

        assertEquals(restored, manager.find(restoredId));
        assertEquals(List.of(restored), manager.instances());
        StructureAttempt next = manager.begin(
            world,
            new StructureSpec(ownerA, Siting.of(LatticePoint.of(7, 0, 7), Orientation.identity), blueprint)
        );
        assertEquals(51, next.id().serial(), "Loaded raises the session serial watermark");
    }

    @Test
    void duplicateRowsInOneSettleCollapseToTheInstance() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        InstanceId instance = new InstanceId(world, 1_000_000L);
        ClaimRow duplicate = row(instance, origin, LatticePoint.origin, Sharing.exclusive);
        ClaimVerdict verdict = ledger
                .settle(new SettleRequest(instance, List.of(duplicate, duplicate), ledger.revision()));
        assertTrue(verdict instanceof ClaimVerdict.Settled);
        assertEquals(1, ledger.revision());
        assertEquals(1, ledger.read(oneCellBox).rows().size(), "duplicate rows in one key collapse");
    }

    @Test
    void structureSetRollbackRemovesTheContextFromTheBoundManager() {
        Blueprint root = StructureSetTestSupport
                .singleBlueprint(StructureSetTestSupport.id("mismatch_root"), StructureSetTestSupport.casing);
        Blueprint satellite = StructureSetTestSupport
                .singleBlueprint(StructureSetTestSupport.id("mismatch_satellite"), StructureSetTestSupport.casing);
        NamespacedId satelliteId = StructureSetTestSupport.id("mismatch_member");
        SatelliteSpec spec = new SatelliteSpec(
            satelliteId,
            satellite.id(),
            LatticePoint.of(2, 0, 0),
            Orientation.identity,
            true,
            Value.object(),
            null,
            List.of(),
            null
        );
        CompiledStructureSet compiled = new CompiledStructureSet(
            StructureSetTestSupport.setId,
            root,
            List.of(new CompiledSatellite(spec, satellite))
        );
        StructureSet plan = StructureSetTestSupport.place(compiled);
        StructureSet.Satellite placed = plan.satellite(satelliteId);
        assertNotNull(placed);

        StructureSetTestSupport.TestView view = new StructureSetTestSupport.TestView();
        view.put(StructureSetTestSupport.world(plan.rootSiting(), LatticePoint.origin), StructureSetTestSupport.casing);
        view.put(StructureSetTestSupport.world(placed.siting(), LatticePoint.origin), StructureSetTestSupport.casing);

        StructureManager kernelManager = StructureManager.create();
        StructureManager draftManager = StructureManager.create();
        StructureSetTestSupport.FakeLedger ledger = new StructureSetTestSupport.FakeLedger();
        StructureKernel kernel = StructureKernel
                .create(Catalogs.builtin(), new Host(DocumentSource.empty(), ledger, kernelManager, Map.of()));
        List<InstanceId> draftIds = new ArrayList<>();
        Function<StructureSpec, StructureAttempt> drafts = structureSpec -> {
            StructureAttempt attempt = draftManager.begin(world, structureSpec);
            draftIds.add(attempt.id());
            return attempt;
        };
        ledger.movingSiting = placed.siting();

        StructureSetResult result = new StructureSetHost(kernel, draftManager).run(
            plan,
            new StructureSetTestSupport.TestLease(view),
            StructureSetTestSupport.owner,
            Budget.unbounded(),
            drafts
        );

        assertEquals(2, draftIds.size(), "the root and the satellite each reserve one draft");
        InstanceId rootId = draftIds.get(0);
        StructureInstance leaked = draftManager.find(rootId);
        assertTrue(
            leaked == null
                    && result.rollbackFailures().isEmpty()
                    && result.rolledBack().contains(StructureSetTestSupport.id("mismatch_root")),
            "rollback must remove the context from the manager bound to the host: leaked=" + (leaked != null)
                    + ", draftManagerInstances=" + draftManager.instances().size() + ", rollbackFailures="
                    + result.rollbackFailures().size() + ", rolledBack=" + result.rolledBack()
        );
    }

    private static StructureInstance form(StructureManager manager, OwnerId owner, Siting siting, CuboidSet bounds) {
        return formIn(manager, world, owner, siting, bounds);
    }

    private static StructureInstance formIn(
        StructureManager manager,
        World instanceWorld,
        OwnerId owner,
        Siting siting,
        CuboidSet bounds
    ) {
        StructureAttempt attempt = manager.begin(instanceWorld, new StructureSpec(owner, siting, blueprint));
        attempt.publish(attempt.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        return attempt.commit(bounds);
    }

    private static InstanceId attemptId(StructureAttempt attempt) {
        return attempt.id();
    }

    private static ClaimRow row(InstanceId instance, Siting siting, LatticePoint cell, Sharing sharing) {
        return new ClaimRow(LatticeKey.of(cell), sharing, siting, bay, instance);
    }

    private static void settle(ClaimLedger ledger, InstanceId instance, List<ClaimRow> rows) {
        ClaimVerdict verdict = ledger.settle(new SettleRequest(instance, rows, ledger.revision()));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            throw new AssertionError("the settle was rejected: " + verdict);
        }
    }
}
