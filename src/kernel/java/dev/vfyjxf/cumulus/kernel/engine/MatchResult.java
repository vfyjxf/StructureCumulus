package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** The five answers of one attempt. */
public sealed interface MatchResult {

    Phase phase();

    FindingBundle findings();

    record Admitted(Phase phase, InstanceId instance, Siting siting, MatchOutcome outcome,
                    long ledgerRevision, boolean ledgerAvailable,
                    FindingBundle findings) implements MatchResult {
        public Admitted {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(instance, "instance");
            Objects.requireNonNull(siting, "siting");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(findings, "findings");
            if (ledgerRevision < 0) {
                throw new IllegalArgumentException("ledgerRevision >= 0");
            }
        }
    }

    record Formed(Phase phase, InstanceId instance, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long contextRevision, long ledgerRevision,
                  FindingBundle findings) implements MatchResult {
        public Formed {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(instance, "instance");
            Objects.requireNonNull(siting, "siting");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(claims, "claims");
            Objects.requireNonNull(findings, "findings");
            if (contextRevision < 1 || ledgerRevision < 0) {
                throw new IllegalArgumentException("revisions out of range");
            }
        }
    }

    record Failed(Phase phase, FindingBundle findings) implements MatchResult {
        public Failed {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(findings, "findings");
        }
    }

    record Undecided(Phase phase, UndecidedReason reason, FindingBundle findings,
                     @Nullable MatchOutcome partial) implements MatchResult {
        public Undecided {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(findings, "findings");
        }
    }

    record Dropped(Phase phase, FindingBundle findings) implements MatchResult {
        public Dropped {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(findings, "findings");
        }
    }
}
