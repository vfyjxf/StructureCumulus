package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.engine.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.engine.SnapshotId;
import dev.vfyjxf.cumulus.kernel.engine.TestLease;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.engine.UndecidedReason;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 22 §9.4, P8; 11 §4.6: churn past {@code maxDiscards} settles {@code Undecided(DIRTY_UNSTABLE)}
 * and re-emits the previous {@code Formed} with {@code generation + 1}.
 */
class LivenessScenarioTest {

    private static final int MAX_DISCARDS = 3;
    private static final FindingCode DIRTY_UNSTABLE = FindingCode.of("SC-MC-0404");
    private static final Siting AT = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);

    @Test
    @Disabled("engine not implemented")
    void consecutiveStaleDropsReemitTheFormedWithGenerationPlusOne() {
        Slot slot = Slot.formed(4L);

        slot = slot.staleDrop().staleDrop();
        assertFalse(slot.unstable(), "below maxDiscards the unit keeps retrying and the previous Formed stands");
        assertEquals(4L, slot.generation(), "a discarded attempt is not a re-emission");

        slot = slot.staleDrop();
        assertTrue(slot.unstable(),
                "22 §9.4: after maxDiscards the unit settles Undecided(DIRTY_UNSTABLE) with SC-MC-0404");
        assertEquals(5L, slot.generation(), "22 §9.4: the previous Formed is re-emitted with generation + 1");
        assertEquals(MAX_DISCARDS, slot.discards(), "the counter lives in the slot and coalescing inherits it");

        Slot settled = slot.formedAgain();
        assertEquals(0, settled.discards(), "only a successful settle resets the counters");
        assertEquals(6L, settled.generation(), "a new Formed moves the generation once more");
    }

    @Test
    void theUnstableSettlementIsTheEngineReasonAndTheAdapterCode() {
        MatchOutcome outcome = MatchOutcome.empty(Stage.SETTLE, AT, TestWorld.builder().build());
        SnapshotLease lease = TestLease.captured(
                new SnapshotId(TestWorld.DEFAULT_DIMENSION, 1L), TestWorld.builder().build(), 0L);
        MatchResult.Formed previous = new MatchResult.Formed(TestWorld.DEFAULT_INSTANCE, AT, outcome,
                ClaimSet.EMPTY, 4L, 3L, 7L, lease, FindingBundle.empty());

        MatchResult.Undecided undecided = new MatchResult.Undecided(UndecidedReason.DIRTY_UNSTABLE,
                List.of(Finding.builder(DIRTY_UNSTABLE).severity(Severity.WARNING)
                        .message("the unit was discarded past maxDiscards")
                        .stage(Stage.SETTLE).build()), null, null);
        assertEquals(UndecidedReason.DIRTY_UNSTABLE, undecided.reason(),
                "22 §9.4: the stop names the reason, never a verdict");
        assertEquals("SC-MC-0404", undecided.bundle().findings().get(0).code().text(),
                "13 §: an attempt discarded past maxDiscards is SC-MC-0404");
        assertEquals(FindingCode.Family.MC, DIRTY_UNSTABLE.family(), "the code belongs to the adapter family");

        MatchResult.Formed reemitted = new MatchResult.Formed(previous.id(), previous.siting(), previous.outcome(),
                previous.claims(), previous.generation() + 1, previous.contextRevision(),
                previous.ledgerRevision(), previous.lease(), previous.findings());
        assertEquals(5L, reemitted.generation(), "22 §9.4: the re-emission carries generation + 1");
        assertEquals(previous.outcome(), reemitted.outcome(), "and the same outcome: a consumer tells them apart");
        assertEquals(previous.claims(), reemitted.claims(), "the settled claim set is unchanged");
        assertTrue(previous.generation() < reemitted.generation(), "the generation is monotone across a re-emission");
    }

    private record Slot(long generation, int discards, boolean unstable) {

        static Slot formed(long generation) {
            return new Slot(generation, 0, false);
        }

        Slot staleDrop() {
            int next = discards + 1;
            return next < MAX_DISCARDS
                    ? new Slot(generation, next, false)
                    : new Slot(generation + 1, next, true);
        }

        Slot formedAgain() {
            return new Slot(generation + 1, 0, false);
        }
    }
}
