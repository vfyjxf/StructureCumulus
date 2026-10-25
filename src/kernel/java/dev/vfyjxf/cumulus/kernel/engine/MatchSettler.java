package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

/**
 * The settle half of one call: one revision read, one CAS, and at most one in-call retry when the
 * ledger moved. A caller-supplied executor keeps the host's thread contract outside the kernel.
 */
public final class MatchSettler {

    static final FindingCode LEDGER_MOVED = FindingCode.of(FindingCode.Family.ENG, 3);
    static final FindingCode SETTLE_UNAVAILABLE = FindingCode.of(FindingCode.Family.ENG, 4);

    private final ClaimLedger ledger;
    private final Executor executor;

    private MatchSettler(ClaimLedger ledger, Executor executor) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static MatchSettler inline(ClaimLedger ledger) {
        return new MatchSettler(ledger, Runnable::run);
    }

    public static MatchSettler on(ClaimLedger ledger, Executor executor) {
        return new MatchSettler(ledger, executor);
    }

    public MatchResult settle(MatchRequest request, MatchOutcome outcome, long contextRevision,
                              List<ClaimRow> rows, InstanceRecord record, SnapshotLease lease) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(lease, "lease");
        Outcome<Long> revision = call(ledger::revision,
                "the ledger revision could not be read");
        return revision.fold(
                (expected, subjects) -> attempt(request, outcome, contextRevision, rows, record, lease,
                        expected, 1),
                findings -> unavailable(request, outcome, findings, null));
    }

    private MatchResult attempt(MatchRequest request, MatchOutcome outcome, long contextRevision, List<ClaimRow> rows,
                                InstanceRecord record, SnapshotLease lease, long expectedRevision,
                                int retriesLeft) {
        SettleRequest settle = new SettleRequest(request.owner(), request.siting(), rows, expectedRevision);
        Outcome<ClaimVerdict> answer = call(() -> ledger.settle(settle), "the claim ledger did not answer");
        return answer.fold(
                (verdict, subjects) -> verdict(verdict, request, outcome, contextRevision, rows, record, lease,
                        retriesLeft),
                findings -> unavailable(request, outcome, findings, request.retryToken()));
    }

    private MatchResult verdict(ClaimVerdict verdict, MatchRequest request, MatchOutcome outcome,
                                long contextRevision, List<ClaimRow> rows, InstanceRecord record,
                                SnapshotLease lease, int retriesLeft) {
        if (verdict instanceof ClaimVerdict.RevisionMoved moved) {
            if (retriesLeft > 0) {
                return attempt(request, outcome, contextRevision, rows, record, lease, moved.actual(),
                        retriesLeft - 1);
            }
            Finding finding = Finding.builder(LEDGER_MOVED)
                    .severity(Severity.ERROR)
                    .message("the ledger moved from revision " + moved.expected() + " to " + moved.actual()
                            + " again after one retry")
                    .stage(Stage.SETTLE)
                    .build();
            return new MatchResult.Undecided(UndecidedReason.LEDGER_MOVED, List.of(finding), outcome,
                    request.retryToken());
        }
        if (verdict instanceof ClaimVerdict.ClaimConflict conflict) {
            return new MatchResult.Failed(conflict.findings().findings());
        }
        ClaimVerdict.Settled settled = (ClaimVerdict.Settled) verdict;
        MatchResult.Formed formed = new MatchResult.Formed(record.id(), request.siting(), outcome,
                new ClaimSet(settled.rows(), settled.revision()), record.nextGeneration(), contextRevision,
                settled.revision(), lease, FindingBundle.empty());
        record.remember(formed);
        return formed;
    }

    private MatchResult unavailable(MatchRequest request, MatchOutcome outcome, FindingBundle findings,
                                    @Nullable RetryToken token) {
        Finding finding = Finding.builder(SETTLE_UNAVAILABLE)
                .severity(Severity.ERROR)
                .message("the claim ledger could not answer on the calling thread")
                .stage(Stage.SETTLE)
                .build();
        return new MatchResult.Undecided(UndecidedReason.WORKER_FAILED,
                findings.isEmpty() ? List.of(finding) : findings.findings(), outcome, token);
    }

    private <T> Outcome<T> call(Callable<T> task, String failure) {
        FutureTask<T> future = new FutureTask<>(task);
        try {
            executor.execute(future);
        } catch (RuntimeException rejected) {
            return missing(failure);
        }
        try {
            T value = future.get();
            return value == null
                    ? missing(failure)
                    : new Outcome.Found<>(value, List.of(), FindingBundle.empty());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return missing(failure);
        } catch (ExecutionException failed) {
            return missing(failure);
        }
    }

    private static <T> Outcome<T> missing(String failure) {
        Finding finding = Finding.builder(SETTLE_UNAVAILABLE)
                .severity(Severity.ERROR)
                .message(failure)
                .stage(Stage.SETTLE)
                .build();
        return new Outcome.Missing<>(FindingBundle.of(finding));
    }
}
