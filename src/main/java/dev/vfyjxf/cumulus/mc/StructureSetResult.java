package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
 * claims and invalidates the settled drafts or instances through the bound structure manager.
 *
 * <p>{@code builds} is the set build's extension: one {@code ApplyReport} per member in build order,
 * empty for a check or a settle. A member the run tore down answers {@code rolledBack}.
 */
public record StructureSetResult(
    Map<NamespacedId, MatchOutcome> outcomes,
    List<Finding> setFindings,
    List<NamespacedId> settled,
    List<NamespacedId> rolledBack,
    List<Finding> rollbackFailures,
    Map<NamespacedId, List<ClaimRow>> claims,
    Map<NamespacedId, ApplyReport> builds
) {

    /** The check and settle answer: no member was built, so the build map is empty. */
    public StructureSetResult(
        Map<NamespacedId, MatchOutcome> outcomes,
        List<Finding> setFindings,
        List<NamespacedId> settled,
        List<NamespacedId> rolledBack,
        List<Finding> rollbackFailures,
        Map<NamespacedId, List<ClaimRow>> claims
    ) {
        this(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims, Map.of());
    }

    public StructureSetResult {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(setFindings, "setFindings");
        Objects.requireNonNull(settled, "settled");
        Objects.requireNonNull(rolledBack, "rolledBack");
        Objects.requireNonNull(rollbackFailures, "rollbackFailures");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(builds, "builds");
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        setFindings = List.copyOf(setFindings);
        settled = List.copyOf(settled);
        rolledBack = List.copyOf(rolledBack);
        rollbackFailures = List.copyOf(rollbackFailures);
        Map<NamespacedId, List<ClaimRow>> copied = new LinkedHashMap<>();
        claims.forEach((id, rows) -> copied.put(id, List.copyOf(rows)));
        claims = Collections.unmodifiableMap(copied);
        builds = Collections.unmodifiableMap(new LinkedHashMap<>(builds));
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

    /** One member's build report, or null when the run was a check or a settle. */
    public @Nullable ApplyReport buildOf(NamespacedId member) {
        Objects.requireNonNull(member, "member");
        return builds.get(member);
    }

    /** What the build run's commits took, member by member; empty for a check or a settle. */
    public List<ItemStack> itemsConsumed() {
        List<ItemStack> consumed = new ArrayList<>();
        for (ApplyReport report : builds.values()) {
            consumed.addAll(report.itemsConsumed());
        }
        return List.copyOf(consumed);
    }

    /** True when every member of a set build committed and the run kept no error or rollback failure. */
    public boolean built() {
        if (builds.isEmpty() || !rollbackFailures.isEmpty()) {
            return false;
        }
        for (Finding finding : setFindings) {
            if (finding.severity() == Severity.error) {
                return false;
            }
        }
        for (ApplyReport report : builds.values()) {
            if (report.status() != ApplyReport.Status.committed || hasError(report)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasError(ApplyReport report) {
        for (Finding finding : report.findings().findings()) {
            if (finding.severity() == Severity.error) {
                return true;
            }
        }
        return false;
    }
}
