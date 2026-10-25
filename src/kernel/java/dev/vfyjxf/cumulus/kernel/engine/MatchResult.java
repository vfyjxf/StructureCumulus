package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Six arms, one verdict each; only {@link Formed} exists after a settle returned {@code Settled}.
 * {@link Admitted} and {@link Formed} carry a child lease handle, and {@code close()} releases that
 * handle only. {@link Dropped} means no verdict exists: the snapshot moved after capture, so the
 * next attempt must read it again.
 */
public sealed interface MatchResult extends AutoCloseable
        permits MatchResult.Admitted, MatchResult.Dropped, MatchResult.Formed, MatchResult.Failed,
                MatchResult.Undecided, MatchResult.Deferred {

    FindingBundle bundle();

    /** A result that holds no handle closes to nothing, so every arm works in try-with-resources. */
    @Override
    default void close() {}

    record Admitted(Stage stage, Siting siting, MatchOutcome outcome, SnapshotLease lease,
                    FindingBundle findings) implements MatchResult {

        public Admitted {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(siting, "siting");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(findings, "findings");
        }

        @Override
        public FindingBundle bundle() {
            return findings;
        }

        @Override
        public void close() {
            lease.close();
        }
    }

    record Formed(InstanceId id, Siting siting, MatchOutcome outcome, ClaimSet claims, long generation,
                  long contextRevision, long ledgerRevision, SnapshotLease lease, FindingBundle findings)
            implements MatchResult {

        public Formed {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(siting, "siting");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(claims, "claims");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(findings, "findings");
            if (generation < 1L) {
                throw new IllegalArgumentException("generation must be at least 1: " + generation);
            }
            if (contextRevision < 0L || ledgerRevision < 0L) {
                throw new IllegalArgumentException("a revision must not be negative: "
                        + contextRevision + "/" + ledgerRevision);
            }
        }

        public Stage stage() {
            return outcome.stage();
        }

        @Override
        public FindingBundle bundle() {
            return findings;
        }

        @Override
        public void close() {
            lease.close();
        }
    }

    /** No verdict: the snapshot moved after capture, so nothing here may be published or settled. */
    record Dropped(DropCause cause, List<Finding> findings) implements MatchResult {

        public Dropped {
            Objects.requireNonNull(cause, "cause");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("a Dropped result carries at least one finding");
            }
        }

        @Override
        public FindingBundle bundle() {
            return FindingBundle.of(findings);
        }
    }

    record Failed(List<Finding> findings) implements MatchResult {
        public Failed {
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("a Failed result carries at least one finding");
            }
        }

        @Override
        public FindingBundle bundle() {
            return FindingBundle.of(findings);
        }
    }

    record Undecided(UndecidedReason reason, List<Finding> findings, @Nullable MatchOutcome partial,
                     @Nullable RetryToken retry) implements MatchResult {

        public Undecided {
            Objects.requireNonNull(reason, "reason");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }

        @Override
        public FindingBundle bundle() {
            return FindingBundle.of(findings);
        }
    }

    record Deferred(RetryToken token, List<Finding> findings) implements MatchResult {

        public Deferred {
            Objects.requireNonNull(token, "token");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }

        @Override
        public FindingBundle bundle() {
            return FindingBundle.of(findings);
        }
    }
}
