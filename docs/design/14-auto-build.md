# 14. Host-side build: item sources, materialization, transactions and the executor

> Status: approved, design first (P0). The code lands with milestones P2 and P6 of
> [`docs/plan/autobuild-preview-plan.md`](../plan/autobuild-preview-plan.md); until then this
> document is specified, not implemented. It is the host-side half of D-16: the kernel keeps
> describing remedies as values, and the adapter owns execution, its report and its codes. No type
> here enters the kernel.

## 1. Problem

`kernel.remedy` answers "what would fix this finding" with an `EditPlan` (D-16, D-85). Nothing
answers "apply it". Applying needs four things the kernel deliberately does not have: an item
backend, a translation from `Edit` to world payloads, a transaction over live cells, and a policy
that can refuse. All four name Minecraft types, read the live world or consume real items, so all
four are host concerns.

## 2. Position

- New package `dev.vfyjxf.cumulus.mc.build`. It may name every kernel type; no kernel type names
  it, and the kernel gains no `EditExecutor`, `EditTransaction`, `PlanValidator` or `ApplyReport`
  (D-16 stands, D-87).
- Refusal is data, not control flow: every refusal path answers an `ApplyReport` with status
  `refused` and the findings that caused it, and writes nothing to the world.
- The executor runs on the server thread, like every command path (adapter §11).

## 3. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `ItemSource` | interface (SPI) | id, template-matched count, two-phase extract/insert |
| `ExtractionMode` | enum | `simulate` / `execute` |
| `CreativeItemSource` | final class | unlimited source; insert accepts everything |
| `PlayerInventorySource` | final class | one player's inventory |
| `ItemHandlerSource` | final class | wraps any `IItemHandler` |
| `CompositeItemSource` | final class | ordered aggregate; earlier sources drain first |
| `Materializer` | interface (SPI) | one `Edit` under one `Orientation` → `MaterializedEdit` or null |
| `MaterializeContext` | record | the host facts a materializer may read |
| `MaterializedEdit` | record | placements + item requirements + findings |
| `BlockPlacement` | record | target `BlockState` + optional block-entity NBT |
| `ItemRequirement` | record | template stack + count |
| `VanillaMaterializer` | final class | the built-in materializer |
| `EditTransaction` | final class | pre-image, ordered apply, reverse restore |
| `ApplyReport` | record | status + per-step outcomes + consumed items + findings |
| `BuildPolicy` | record | permission, volume ceiling, removal gate, claim rule |
| `BuildRequest` | record | blueprint, siting, source, policy, dry flag, preferences |
| `BuildExecutor` | final class | the capture → solve → validate → apply flow |
| `SetBuildHost` | final class | set-level build over one shared source and budget (P6) |
| `SetBuildRequest` | record | the placed set, its shared source, gate, player and the dry/settle flags (P6) |
| `BuildJournal` | final class | the pre-images one build run committed, in commit order (P6) |

## 4. Signatures

```java
package dev.vfyjxf.cumulus.mc.build;

public interface ItemSource {
    NamespacedId id();
    int available(ItemStack template);
    ItemStack extract(ItemStack template, int count, ExtractionMode mode);
    // Returns the remainder that did not fit back; an empty stack means fully returned.
    ItemStack insert(ItemStack stack, ExtractionMode mode);
}

public enum ExtractionMode { simulate, execute }

public interface Materializer {
    NamespacedId id();
    @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation,
                                           MaterializeContext context);
}

public record MaterializeContext(ServerLevel level) { }

public record MaterializedEdit(java.util.Map<LatticePoint, BlockPlacement> placements,
                               java.util.List<ItemRequirement> items,
                               java.util.List<Finding> findings) {
    public MaterializedEdit { /* immutable; placements sorted by cell */ }
}

public record BlockPlacement(BlockState state, @Nullable CompoundTag blockEntityData) { }

public record ItemRequirement(ItemStack template, int count) {
    public ItemRequirement { /* count >= 1 */ }
}

public final class EditTransaction implements AutoCloseable {
    // open captures the live pre-image (BlockState + block-entity NBT) of every touched cell.
    public static EditTransaction open(ServerLevel level, CuboidSet touchedCells);
    // One plan step per call, applied in plan order; a cell already at its target is skipped.
    public ApplyReport.Step apply(int stepIndex, java.util.Map<LatticePoint, BlockPlacement> writes);
    // Restores the pre-image of the given steps in reverse order; never retries silently.
    public java.util.List<ApplyReport.Step> rollback(java.util.List<ApplyReport.Step> applied);
    @Override public void close();
}

public record ApplyReport(Status status, java.util.List<Step> steps,
                          java.util.List<ItemStack> itemsConsumed, FindingBundle findings) {
    public ApplyReport { /* immutable; steps align with the plan's step order */ }

    public enum Status { committed, rolledBack, partiallyRolledBack, refused }

    public record Step(int index, Outcome outcome, @Nullable String reason) { }

    public enum Outcome { applied, skipped, failed, restored }
}

public record BuildPolicy(int permissionLevel, long maxTouchedVolume, boolean allowRemoval) {
    public static BuildPolicy defaults();
}

public record BuildRequest(NamespacedId blueprint, Siting siting, ItemSource source,
                           BuildPolicy policy, boolean dry,
                           @Nullable SolvePreferences preferences, ServerPlayer player) { }

public final class BuildExecutor {
    public BuildExecutor(LevelCapture capture, StructureKernel kernel, OwnerId owner,
                         BuildPolicy configured);
    public ApplyReport run(BuildRequest request);
}
```

`SolvePreferences` is the kernel preference value (D-86, `remedy` §5); `BuildRequest.preferences`
is the only preference channel the executor reads, so the build layer does not depend on the
preference store (design 15 §9).

The executor also takes the adapter's `OwnerId`: the check begins a draft, and a subpackage cannot
reach `ServerState.owner()`. Claims come from `StructureKernel.ledger()`. The command bodies sit in
`dev.vfyjxf.cumulus.mc.BuildCommand` because the command helpers (`CommandSupport`, the anchor
siting path) are package-scoped there; the build package owns the flow and the command is its thin
shell.

## 5. Item sources

An `ItemSource` is the host's answer to "where do the blocks come from". It is two-phase: the
validator simulates every extraction against the fresh capture and collects the shortage table;
the commit executes the same extractions; a rollback inserts what the transaction consumed.
`insert` returns the remainder that did not fit; a non-empty remainder is never voided silently —
it spawns as item entities at the affected cells and the report carries one `SC-BLD-0006` note per
remainder.

The shipped sources:

| Source | Backing | Notes |
| --- | --- | --- |
| `CreativeItemSource` | none | unlimited; `insert` accepts everything, so a rollback into it never spills |
| `PlayerInventorySource` | one `ServerPlayer`'s inventory | the default for non-creative players |
| `ItemHandlerSource` | any `IItemHandler` | the container form (`with container <pos>`) |
| `CompositeItemSource` | several sources | ordered; extraction drains earlier sources first, insertion fills them first |

Plugins contribute custom sources through `ModPlugin.registerItemSources` (a new default method,
P7); the same failure attribution law as every plugin contribution applies (adapter §7.5).

## 6. Materialization

`VanillaMaterializer` lowers one `Edit` into world payloads:

- `type` resolves through `BuiltInRegistries.BLOCK`; `properties` parse through the block's
  `StateDefinition`. An unknown block, property or value answers null, and the caller records
  `SC-BLD-0004` — materialization failure is a finding, never a default state.
- The siting orientation is applied with the **forward** `OrientationMapping.transform(orientation,
  state)` (definition frame → world pose; the capture audit uses the inverse, adapter §10). An
  `Unrepresentable` result answers null and the caller records `SC-BLD-0004`; there is no silent
  identity in either direction.
- The item requirement is one of the block's item per placed cell.
- `Edit.Remove` materializes to air placements with no item requirements; the removal gate is the
  policy's, not the materializer's (§9).
- `Edit.SetTrait` is not materialized in v1: it stays advisory (`maybeIncorrect`, `remedy` §5).
  Plugin materializers open it at P7.

## 7. Transactions

`EditTransaction` is the D-16 transaction, host-side:

- `open` captures the live pre-image (`BlockState` + block-entity NBT) of every touched cell on the
  server thread.
- Application order is plan order. Before each write the live state is compared with the target; an
  already-correct cell is `skipped`, not rewritten. Idempotence is a verifier property: re-running
  a build against an already-built structure applies nothing.
- When step *n* fails, the already-written steps are restored from their pre-images in reverse
  order. A restore that itself fails ends the report as `partiallyRolledBack` and names the
  remaining cells (`SC-BLD-0006`); nothing is retried silently.
- A removal write is `setAir` with no drops; v1 never collects drops (§15), and removal steps are
  policy-gated by default (§9).

## 8. Reports

`ApplyReport` is the executor's only answer. Its `steps` align with the plan's step order, so a
caller can juxtapose "what the plan asked" with "what the world answered" without re-walking the
plan. `itemsConsumed` is what the commit actually extracted; `findings` carries every refusal and
warning code the run produced.

A set build answers the set result of §13: `StructureSetResult.builds` carries one `ApplyReport` per
member, so a caller juxtaposes the set lifecycle with what each member's plan asked and got.

Two report-text laws (expansion 6.3, 9.2):

- A refusal message names the policy and the config key that gates it ("which rule, who can change
  it"), never a bare refusal (6.3).
- A post-build re-check that still fails reports the new findings next to the old ones with the
  satisfied-count delta — what the build fixed and what remains — instead of repeating the full
  bundle (9.2).

## 9. Policy

`BuildPolicy` is the host's gate. `defaults()` ships the code defaults;
`config/structurecumulus-server.toml` overrides them per server:

- Permission: `permissionLevel` defaults to `Commands.LEVEL_GAMEMASTERS` (2), the same gate the
  `/cumulus` root already applies (adapter §11).
- Blast radius: `maxTouchedVolume` defaults to 4096 cells; a plan touching more is refused
  (`SC-BLD-0002`).
- Removal: `allowRemoval` defaults to false; a plan containing a `Remove` step is refused
  (`SC-BLD-0002`) unless the config or the command's `--allow-removal` flag lifts the gate for this
  run.
- Claims: a touched cell held by a foreign exclusive claim refuses the run (`SC-BLD-0002`); a
  shared claim allows it and the report names the incumbents.
- Items: a non-empty simulated shortage table refuses the run (`SC-BLD-0003`) with the per-item
  counts.

## 10. The executor flow

`BuildExecutor.run(request)` on the server thread:

1. Fresh capture through the two-phase anchored siting of the check path (adapter §11), then
   `check`. A matched check is the idempotent empty report: status `committed`, no steps. The match
   arrives as `Admitted`, because `Formed` is what a *settle* answers; a build that takes no claims
   therefore never sees one and treats both as "already built".
2. `solve(findings, RemedyContext{…, preferences})` collects remedies; the executor picks one
   `Repair`, `machineApplicable` first, preference-scored among ties (design 15 §9). No
   `machineApplicable` remedy is `SC-BLD-0001` (a warning, not a refusal: the run stops and asks a
   human).
3. Validate against the fresh capture: satisfied counts, materialization, item simulation, policy.
   This is the host-side dry run; the kernel has no `PlanValidator` (D-16).
4. A failed validation is a `refused` report with the findings and zero writes.
5. Commit: open the transaction, apply in plan order, close. A `committed` run captures fresh and
   re-checks the same request: a matched check is success; a still-failing check returns the same
   code the pre-build check returned (deterministic, `SC-BLD-0007`). v1 never auto-retries; the user
   re-runs build.
6. A committed transaction enters the undo history (§11).

**Build is not settle.** A build never takes claims; claim semantics are unchanged by the build
path. With `--settle`, a `Formed` re-check chains the existing settle path — a single structure
settles through the kernel `settle` with a `StructureAttempt` (the adapter §11 path), a set through
`StructureSetHost.run` (§13) — and only on that path does rollback cover both world writes and
claims. A settle failure after a committed build writes no claim rows (the existing settle law: a
failed check writes nothing) and the build stays committed; the report carries the settle findings.

A refusal or a failed run leaves the session's plan in place (5.1): the confirm path re-enters at
step 3 with a fresh capture and validation, without re-picking the siting. An item exhaustion is a
pause, not a loss (10.1): the `SC-BLD-0003` refusal keeps the plan, and resupplying makes the same
confirm validate clean.

## 11. Undo

`/cumulus build undo` replays the stored pre-image in reverse as its own transaction (the undo is
itself reportable and rollback-safe). A set build publishes one merged entry per run, so its undo
restores every member in reverse commit order; the rows a `--settle` run wrote are not part of a
pre-image — they stay with their instances until the next settle takes them over (D-79). v1 keeps a
one-entry history per `(player, level)`; the bounded undo/redo stack is P8 backlog (expansion 5.3).
The history is in-memory and is not persisted across a restart; this document is the warning.

## 12. Commands

All three verbs inherit the `/cumulus` root permission gate and the `CommandSupport.guarded`
boundary (adapter §11); exit codes follow the existing convention — a mutating verb returns 1 only
when its mutation succeeded.

- `/cumulus build <id> <origin> [with creative|player|container <pos>] [--dry] [--allow-removal]
  [--settle]` —
  the default source is `creative` for a creative-mode player, else `player`. `--dry` runs steps
  1–4 only and sends the preview payload (design 15 §3): zero writes, and the dry report feeds the
  refusal visualisation. `--settle` chains the settle path after a `Formed` re-check (§10); a
  default build takes no claims. Returns 1 on `committed` (or on a clean dry validation), 0 otherwise.
  Chat prints a summary only; the detail lives in the preview HUD, not in chat spam.
- `/cumulus build undo` — §11; same permission gate; returns 1 on a committed undo.
- `/cumulus set build <id> <origin> [with creative|player|container <pos>] [--dry] [--allow-removal]
  [--settle]` — §13 (P6). Its exit code is `built()` without `--settle` and `formed()` with it.

## 13. Structure-set build (P6)

`SetBuildHost` lives in `mc.build` beside the executor and reuses the `StructureSetHost` shape
(adapter §13.4, D-89):

```java
public final class SetBuildHost {
    public SetBuildHost(LevelCapture capture, StructureKernel kernel, OwnerId owner);
    public StructureSetResult run(SetBuildRequest request, SnapshotLease lease, Budget budget);
}

public record SetBuildRequest(StructureSet set, ItemSource source, BuildPolicy policy,
                              ServerPlayer player, boolean dry, boolean settle) { }
```

The one set capture the caller already took drives every set-layer gate: `StructureSet.validate`
(the same-siting `SC-SET-0008` and every other placement refusal), the presence probe — every
presence cell charged `(1, 0)` against the set's one budget (D-35) — and the link distance and
coverage findings. An unprovable presence (`SC-SET-0004`) or a stale lease stops the build before
any write, exactly as it stops a check. Absence is not a refusal here: an absent member is the build
target, so `SC-SET-0001` stays a check/settle finding and never a build one.

The members then run the §10 flow in declaration order, root first, one member at a time, and the
whole set shares **one** `ItemSource` and **one** budget (D-35). The source is live, so each
member's dry run simulates against what the earlier members actually took and its commit extracts
from the same remainder: a member that runs the source dry reports its `SC-BLD-0003` shortage with
the per-item counts before it writes anything. A member whose presence probe answered present and
whose §10 check already matches is skipped — the executor's idempotent empty report, no items, no
writes — while a present member that does not match is repaired by the same flow. Each member's
§10 flow captures fresh (its check, its commit's pre-image and its post-build re-check);
the caller's single set capture is the set-layer read, not the member writes (§13.5 governs
check/settle, whose members write nothing). `--dry` runs every member's steps 1–4 and writes
nothing; a dry run consumes no item, so each member is validated against the same live count — a
dry set build proves each member's feasibility, not the set's combined bill.

Build is not settle (§10), so a default set build takes no claims. Any member failure — a refusal or
a rolled-back commit — tears the members this run already wrote down through their own pre-images in
**reverse commit order**, returns to the shared source what they consumed (an unreturned remainder
spills at that member's cells as `SC-BLD-0006`, never silently), and reports the original finding.
Nothing the failed run wrote stays in the world, and the undo entry the run published is dropped with
it. A member that committed but whose post-build re-check still reports `SC-BLD-0007` is not a
failure: the write stays (§10), the set is simply not `built()`, and the user re-runs — a member
whose check needs several repairs therefore takes one repair per run, exactly like the single build.

With `--settle`, a run whose members all committed chains the existing set settle path:
`SetBuildHost` re-captures the region and policy of the set capture after the writes and runs
`StructureSetHost.run` over the whole set (takeover, presence, links, check-all, settle-all). A
failure in that pass is the set host's own rollback — release plus `StructureEvent.Broken` dispatch
(adapter §13.4) — and the set build then tears its own world writes down in reverse, so a settle
failure rolls back both the world and the claims the run took. A run that is not `built()` never
reaches the settle pass (an incomplete member keeps its write and waits for the next run), and
`--dry` never settles.

The report extends `StructureSetResult` with a per-member `ApplyReport` in build order. `builds`
holds each member's final report: a member torn down by the run answers `rolledBack`, a skipped one
the idempotent empty report. `itemsConsumed()` is what the members' commits took,
`setFindings` carries every member's findings next to the set-layer ones, and `rolledBack` names the
members this run tore down before the ids the settle pass released. `built()` is the build success
criterion — every member committed, no error finding, no rollback failure — and it is the plain set
build's exit code; a `--settle` run exits on `formed()` instead. The `SC-SET-0008` same-siting
refusal and every other set-layer refusal apply unchanged.

## 14. Codes

`SC-BLD` is the adapter's build family: registered through `AdapterCodes` with the same law as the
`SC-MC-*` family (adapter §12) — each entry carries its canonical severity and explanation, and no
entry ever enters `Codes.all`.

| Code | Severity | Meaning |
| --- | --- | --- |
| `SC-BLD-0001` | WARNING | no `machineApplicable` remedy; a human must choose |
| `SC-BLD-0002` | ERROR | policy refusal (permission, volume, removal, foreign claim) |
| `SC-BLD-0003` | ERROR | item shortage; the report carries the per-item counts |
| `SC-BLD-0004` | ERROR | materialization failed (unrepresentable orientation, unknown block/property) |
| `SC-BLD-0005` | ERROR | the transaction failed at step *n* |
| `SC-BLD-0006` | WARNING | rollback incomplete (remaining cells, unreturned items) |
| `SC-BLD-0007` | ERROR | the post-build re-check is still not matched (deterministic, same code) |
| `SC-BLD-0008` | WARNING | preview session invalidated; the candidates are stale (design 15 §4) |

## 15. Non-goals (v1)

Survival recipe decomposition or experience costs; entity placement (an entity spawn is not an
`Edit`); web `visual/` tooling; multiplayer co-editing of one plan; golden-image CI; drop
collection for removed blocks (v1 removal is void and policy-forbidden by default).
