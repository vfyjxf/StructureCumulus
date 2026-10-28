package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The host's answer for one structure set: per-structure outcomes, set findings and the settle lifecycle.
 *
 * <p>{@code outcomes} holds one entry per structure whose check was admitted, keyed by the root
 * blueprint id and each satellite id. {@code claims} holds the rows each {@code Formed} structure wrote,
 * filtered by {@code (owner, siting, bay)} from the ledger-wide {@code ClaimSet}. Rollback releases
 * claims only; a rolled-back structure's kernel context stays published, so the host must invalidate it.
 */
public record StructureSetResult(
    Map<NamespacedId, MatchOutcome> outcomes,
    List<Finding> setFindings,
    List<NamespacedId> settled,
    List<NamespacedId> rolledBack,
    List<Finding> rollbackFailures,
    Map<NamespacedId, List<ClaimRow>> claims
) {

    public StructureSetResult {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(setFindings, "setFindings");
        Objects.requireNonNull(settled, "settled");
        Objects.requireNonNull(rolledBack, "rolledBack");
        Objects.requireNonNull(rollbackFailures, "rollbackFailures");
        Objects.requireNonNull(claims, "claims");
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        setFindings = List.copyOf(setFindings);
        settled = List.copyOf(settled);
        rolledBack = List.copyOf(rolledBack);
        rollbackFailures = List.copyOf(rollbackFailures);
        Map<NamespacedId, List<ClaimRow>> copied = new LinkedHashMap<>();
        claims.forEach((id, rows) -> copied.put(id, List.copyOf(rows)));
        claims = Collections.unmodifiableMap(copied);
    }

    /** True when every present structure settled and no error finding or rollback failure exists. */
    public boolean formed() {
        if (!rollbackFailures.isEmpty()) {
            return false;
        }
        for (Finding finding : setFindings) {
            if (finding.severity() == Severity.error) {
                return false;
            }
        }
        return !settled.isEmpty();
    }

    public @Nullable Finding firstFailure() {
        for (Finding finding : setFindings) {
            if (finding.severity() == Severity.error) {
                return finding;
            }
        }
        return null;
    }

    public List<ClaimRow> claimsOf(NamespacedId structure) {
        Objects.requireNonNull(structure, "structure");
        return claims.getOrDefault(structure, List.of());
    }
}
