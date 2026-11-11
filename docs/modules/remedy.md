# Module `kernel.remedy`

> Rank 8. Repair, preview and explanation as values over the same findings and loci. The kernel
> describes; the host applies. No transaction, no executor and no dry-run machinery lives here.

## 1. Scope

**Owns:** `Remedy`, `EditPlan`, `EditStep`, `Edit`, `OverlayModel`, `CellMark`, `Marker`,
`MarkerKind`, `RemedyProvider`, `RemedyContext`, `SolveResult`, `SolvePreferences` (D-86).

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
| `RemedyContext` | record | blueprint, siting, claims, outcome, context, cap, preferences (D-86) |
| `SolveResult` | record | remedies + findings from the solve pass |
| `SolvePreferences` | record | preferred cells and part pins; orders legal candidates, never changes applicability (D-86) |
| `StandardRepairProvider` | record | the built-in `structurecumulus:cell_repair` (D-85) |
| `StandardPreviewProvider` | record | the built-in `structurecumulus:preview` (D-85) |

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

public enum CellMark {
    expected, present, missing, extra, wrong, blocked, unknown;
    public static CellMark louder(CellMark left, CellMark right);
}

public record Marker(Locus locus, String label, MarkerKind kind) { }

public enum MarkerKind { error, warning, info, anchor, seam }

public interface RemedyProvider {
    NamespacedId id();
    java.util.List<Remedy> solve(Finding finding, RemedyContext context);
}

public record RemedyContext(Blueprint blueprint, Siting siting,
                            @Nullable ClaimView claims, long ledgerRevision,
                            @Nullable MatchOutcome outcome,
                            @Nullable StructureContext context, int maxResults,
                            @Nullable SolvePreferences preferences) {
    public RemedyContext { /* maxResults >= 1 */ }
}

public record SolveResult(java.util.List<Remedy> remedies, FindingBundle findings) { }

public record SolvePreferences(java.util.List<CuboidSet> preferredCells,
                               java.util.Map<NamespacedId, CuboidSet> partPins) {
    public SolvePreferences { /* a null list or map argument means empty; copies immutable; pins sorted */ }
}

public record StandardRepairProvider() implements RemedyProvider { }

public record StandardPreviewProvider() implements RemedyProvider { }
```

## 4. Laws

- A provider is pure and re-entrant: it reads `RemedyContext` and returns candidate remedies. It
  never applies a plan, opens a transaction or reads a live world.
- `solve` iterates providers by id order, calls each once per finding, catches a `RuntimeException`
  and reports `SC-RMD-0001`; a malformed remedy reports `SC-RMD-0002`. Results are deduplicated by
  value and capped at `maxResults`; the cap applies to warnings/info-style suggestions, never to a
  machine-applicable repair? Keep it simple: cap the whole list at `maxResults`, preserving
  `machineApplicable` first by `Applicability.rank`.
- `Applicability.machineApplicable` is the only rung a host may auto-apply; a `hasPlaceholders`
  plan needs a human edit.
- Applicability law (D-85): a `Place`/`Remove`/`Replace` whose type and properties are fully
  determined is `machineApplicable`; a plan that chose among equally legal candidates stays
  `machineApplicable` only when the provider offers the unchosen candidates as sibling repairs;
  `SetTrait` and any edit whose host realizability is unknown is at most `maybeIncorrect`.
- Preference law (D-86): a `SolvePreferences` only orders or picks among equally legal candidates.
  It never changes an applicability rung, never fabricates an edit, and a null or empty preference
  is the canonical order. The same input with the same preferences solves byte-identically; the
  determinism test pins this.
- An `EditPlan` is ordered; `EditStep.locus` is the same locus vocabulary as a finding. A plan that
  carries a `key` locus is not executable (no cell), which is why the step's `Edit` always has cells
  or a trait at a cell.
- `OverlayModel` is pure data: bounds, per-cell marks, markers. Rendering, colours and translation
  are the host's.
- The kernel never validates a plan against a live world and never reports an `ApplyReport`; the
  host's executor owns that report and may use `finding` values.

## 5. Built-in providers (D-85)

Two built-in providers join the `Catalogs.builtin()` remedies family (spi §4.1):
`structurecumulus:cell_repair` (`StandardRepairProvider`) and `structurecumulus:preview`
(`StandardPreviewProvider`). Both are pure and re-entrant and read only `RemedyContext` (blueprint,
siting, claims, outcome, context) — never a live world. The solve pass merges one provider's
previews into a single overlay (§6); the exact signatures live in §3.

### 5.1 Finding → edit mapping

Dispatch is by finding code plus the blueprint's declared expectations at the siting; a provider
never parses finding message text (D-66). It may classify the finding's cells against the captured
snapshot in `RemedyContext.outcome` (absent / different occupant / forbidden presence); that view
is a capture, not a live read. A `machineApplicable` `Place`/`Replace` derives only from a leaf the
declaration description port can describe (`structure` §4.12, D-91); an expectation the port
cannot describe is always advisory, never `machineApplicable`. A negated trait leaf (`SC-EXP-0002`)
follows the mirror rule: only a leaf that states presence is broken by removing the trait, and a
leaf that states absence has no machine edit.

| Finding (codes) | Remedy | Applicability |
| --- | --- | --- |
| expected occupant absent at the cell — `SC-EXP-0001` | `Edit.Place(cells, type, properties)` | machineApplicable |
| the cell holds a different occupant — `SC-EXP-0001` | `Edit.Replace(...)` | machineApplicable |
| an occupant where the expectation wants none — `SC-EXP-0001` (air leaf), `SC-EXP-0002`, `SC-EXP-0006` | `Edit.Remove(cells)` | machineApplicable; execution is policy-gated (design 14 §9) |
| threshold deficit — `SC-EXP-0003` with child `SC-EXP-0001`s | `Edit.Place` at candidate cells inside the region, chosen by `SolvePreferences.preferredCells`/`partPins`, canonical lattice order without one; the unchosen candidates are offered as sibling repairs | machineApplicable |
| threshold excess (`atMost`/`exactly` over) — `SC-EXP-0003` | no machine edit; advisory — which cells to remove is not decidable per cell, and the default policy forbids removal | hasPlaceholders |
| trait absent expected but present — `SC-EXP-0001` (`traitAbsent` leaf) | `Edit.SetTrait(cells, Trait.absent(key))` | maybeIncorrect: the kernel cannot know whether the host can write it, and v1 never auto-applies it |
| trait present expected but absent or mismatched — `SC-EXP-0001` (`trait`/`trait(value)` leaf) | no machine edit; advisory — the declaration carries no expected value, so the kernel does not fabricate a payload | hasPlaceholders |
| claim conflict — `SC-CLM-0001` | no machine edit; advisory, and the preview marks the cells `blocked` | hasPlaceholders |
| orientation / anchor / budget / cancellation — `SC-MC-0004`, `SC-BPT-0006`, `SC-ENG-0001`, `SC-ENG-0005` | no edit; a `Marker` (anchor/seam/error/warning/info) | — |
| unreadable or unjudged cells — `SC-OCC-0001`…`0004`, `SC-ENG-0002` | no edit; the preview marks the cells `unknown` | — |

The built-in providers emit only `error`, `warning` and `info` markers; `anchor` and `seam` are the
host's vocabulary (design 15 §4/§7) and have no kernel producer.

### 5.2 Overlay derivation

A mark derives only from the findings' cell loci plus the blueprint's expected coverage at the
siting; the overlay never walks the world for a mark.

| CellMark | Rule |
| --- | --- |
| `present` | an expected cell that was judged and carries no finding |
| `missing` | an `SC-EXP-0001` locus cell whose expected occupant is absent |
| `wrong` | an `SC-EXP-0001` locus cell holding a different occupant |
| `extra` | an air-leaf `SC-EXP-0001` / `SC-EXP-0002` / `SC-EXP-0006` locus cell |
| `blocked` | an `SC-CLM-0001` locus cell |
| `unknown` | an expected cell that was not judged (undecided, outside the read scope) or is unreadable (`SC-OCC-*`, `SC-ENG-0002`); an unreadable cell is never drawn `missing` |

One cell with several findings takes the loudest mark: `blocked > wrong > missing > extra >
present`.

### 5.3 `SolvePreferences` (D-86)

The signature is in §3. `RemedyContext` carries it as the final component
`@Nullable SolvePreferences preferences`. All cell sets in the record are world-frame at the solve
siting; the providers map them through `Siting` to reach the blueprint's local declarations.

- `preferredCells`: earlier is more preferred; orders only same-rung legal candidates.
- `partPins`: bay/focus id → the user's pinned legal cell set (design 15 §8). A pin is the
  strongest preference rank: the pinned cells come first, canonically ordered among themselves,
  and every displaced legal candidate is offered as a sibling repair — never silently. A pin that
  names an illegal cell is ignored, never obeyed, and the preview carries one info `Marker` naming
  the part. The full order is user pin > memory > preference score > canonical lattice order.
- The iron law of §4 applies to every component.

## 6. Pipeline role

`solve` is a facade helper after a result; it never runs inside `check`/`settle`. The kernel's
`StructureKernel.solve(findings, context)` collects remedies; the host applies a chosen repair
through its own executor.

**Preview merge.** The provider interface answers one finding at a time, but a bundle describes one
overlay. `solve` therefore merges the `Remedy.Preview`s one provider returns over the whole bundle
into one: cells union by `CellMark.louder` (the loudest mark wins, §5.2), markers concatenate in
processing order, bounds are the union of the input bounds and the rationale is the first
preview's. Previews never merge across providers, and a merge never happens inside a provider.

## 7. Codes

`SC-RMD-0001` (provider threw), `SC-RMD-0002` (malformed remedy). Producers: `StructureKernel.solve`.

## 8. Tests

- Provider order/dedup/cap; a throwing provider yields `SC-RMD-0001` and no remedy.
- `EditPlan`/`Edit` immutability and cell sets.
- Overlay model order and marker kinds.
- Built-in providers (P1): the mapping table per code family; the overlay derivation and the
  loudest-mark rule; the merge of one provider's previews into one overlay (loudest cell, markers
  in processing order); preference determinism (same input, same preferences → byte-identical
  solve); pin legality (an illegal pin is ignored and annotated), the conflict order and the
  displaced sibling offer; a null/empty preference is the canonical order.

## 9. Dependencies

`core`, `space`, `finding`, `world`, `claim`, `structure`, `context`, `engine` (only the
`MatchOutcome` value in `RemedyContext`). It does not import `document` or `spi`.
