# Module `kernel.remedy`

> Rank 8. Repair, preview and explanation as values over the same findings and loci. The kernel
> describes; the host applies. No transaction, no executor and no dry-run machinery lives here.

## 1. Scope

**Owns:** `Remedy`, `EditPlan`, `EditStep`, `Edit`, `OverlayModel`, `CellMark`, `Marker`,
`MarkerKind`, `RemedyProvider`, `RemedyContext`, `SolveResult`.

**Does not own:** plan execution (`mc`), validation against a live world (host), rendering an
overlay (`mc`), finding text (`finding`).

**Imports:** `core`, `space`, `finding`, `world`, `claim`, `structure`, `context` (for
`StructureContext`).

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `Remedy` | sealed | `Repair(plan, applicability, rationale)` or `Preview(overlay, rationale)` |
| `EditPlan` | record | ordered steps + rationale |
| `EditStep` | record | `(Edit, Locus, Applicability)` |
| `Edit` | sealed | `Place`, `Remove`, `Replace`, `SetTrait` |
| `OverlayModel` | record | bounds + per-cell marks + markers |
| `CellMark` | enum | expected/present/missing/extra/wrong/blocked/unknown |
| `Marker` | record | `(Locus, String, MarkerKind)` |
| `MarkerKind` | enum | error/warning/info/anchor/seam |
| `RemedyProvider` | interface | `solve(finding, context)` → ordered remedies |
| `RemedyContext` | record | blueprint, siting, claims, outcome, context, cap |
| `SolveResult` | record | remedies + findings from the solve pass |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.remedy;

public sealed interface Remedy {
    record Repair(EditPlan plan, Applicability applicability, String rationale) implements Remedy { }
    record Preview(OverlayModel overlay, String rationale) implements Remedy { }
}

public record EditPlan(java.util.List<EditStep> steps, String rationale) {
    public EditPlan { /* immutable; non-empty? a no-op plan is allowed for a preview */ }
}

public record EditStep(Edit edit, Locus locus, Applicability applicability) { }

public sealed interface Edit {
    record Place(CuboidSet cells, NamespacedId type, java.util.Map<String, String> properties)
            implements Edit { }
    record Remove(CuboidSet cells) implements Edit { }
    record Replace(CuboidSet cells, NamespacedId type, java.util.Map<String, String> properties)
            implements Edit { }
    record SetTrait(CuboidSet cells, Trait<?> trait) implements Edit { }
}

public record OverlayModel(Cuboid bounds, java.util.Map<LatticeKey, CellMark> cells,
                           java.util.List<Marker> markers) {
    public OverlayModel { /* immutable; cells sorted by key */ }
}

public enum CellMark { EXPECTED, PRESENT, MISSING, EXTRA, WRONG, BLOCKED, UNKNOWN }

public record Marker(Locus locus, String label, MarkerKind kind) { }

public enum MarkerKind { ERROR, WARNING, INFO, ANCHOR, SEAM }

public interface RemedyProvider {
    NamespacedId id();
    java.util.List<Remedy> solve(Finding finding, RemedyContext context);
}

public record RemedyContext(Blueprint blueprint, Siting siting,
                            @Nullable ClaimView claims, long ledgerRevision,
                            @Nullable MatchOutcome outcome,
                            @Nullable StructureContext context, int maxResults) {
    public RemedyContext { /* maxResults >= 1 */ }
}

public record SolveResult(java.util.List<Remedy> remedies, FindingBundle findings) { }
```

## 4. Laws

- A provider is pure and re-entrant: it reads `RemedyContext` and returns candidate remedies. It
  never applies a plan, opens a transaction or reads a live world.
- `solve` iterates providers by id order, calls each once per finding, catches a `RuntimeException`
  and reports `SC-RMD-0001`; a malformed remedy reports `SC-RMD-0002`. Results are deduplicated by
  value and capped at `maxResults`; the cap applies to warnings/info-style suggestions, never to a
  machine-applicable repair? Keep it simple: cap the whole list at `maxResults`, preserving
  `MACHINE_APPLICABLE` first by `Applicability.rank`.
- `Applicability.MACHINE_APPLICABLE` is the only rung a host may auto-apply; a `HAS_PLACEHOLDERS`
  plan needs a human edit.
- An `EditPlan` is ordered; `EditStep.locus` is the same locus vocabulary as a finding. A plan that
  carries a `KEY` locus is not executable (no cell), which is why the step's `Edit` always has cells
  or a trait at a cell.
- `OverlayModel` is pure data: bounds, per-cell marks, markers. Rendering, colours and translation
  are the host's.
- The kernel never validates a plan against a live world and never reports an `ApplyReport`; the
  host's executor owns that report and may use `finding` values.

## 5. Pipeline role

`solve` is a facade helper after a result; it never runs inside `check`/`settle`. The kernel's
`StructureKernel.solve(findings, context)` collects remedies; the host applies a chosen repair
through its own executor.

## 6. Codes

`SC-RMD-0001` (provider threw), `SC-RMD-0002` (malformed remedy). Producers: `StructureKernel.solve`.

## 7. Tests

- Provider order/dedup/cap; a throwing provider yields `SC-RMD-0001` and no remedy.
- `EditPlan`/`Edit` immutability and cell sets.
- Overlay model order and marker kinds.

## 8. Dependencies

`core`, `space`, `finding`, `world`, `claim`, `structure`, `context`, `engine` (only the
`MatchOutcome` value in `RemedyContext`). It does not import `document` or `spi`.
