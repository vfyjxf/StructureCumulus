# 14 — Remedies (`kernel.remedy`)

> U10: how a `Finding` becomes something to do about it. Serves R-12, R-14; enforces I-10, I-11,
> I-14, I-15; keeps I-1 and I-4 by never writing a world.

## 1. Scope

| Owns | Refuses |
| --- | --- |
| `Remedy`, `RemedyKind`, `RemedyProvider`, `RemedyContext`, `RemedyOptions` | the finding model, codes, `Applicability` (U9) |
| `EditPlan`, `EditStep`, `Edit` + 7 cases | blueprints, bays, anchors, expectations (U5/U3) |
| `PlanValidator`, `ValidationReport`, `PlanDiff`, `PlanDigest` | claim semantics (U4): U10 reads a `ClaimView` |
| `EditExecutor`, `EditTransaction`, `ApplyReport` | the write mechanism (A1); permission, host border |
| `OverlayModel`, `CellMark`, `Marker`, `MarkerKind` | colour, geometry, transfer, draw order (A1/host) |

Edges follow `02-architecture.md` §2: `remedy ← blueprint, occupant, expectation, finding`. U10
imports no `engine` type; a provider never sees a `MatchSession`; a `Suggestion` never carries a plan.

**Frame rule.** Plans and overlays are **siting-relative**: an `Edit` addresses local `LatticeKey`s
and the `Siting` arrives at validate, apply and render time, so a plan is a function of `(finding,
blueprint)` alone and can be validated before the host commits. A `WORLD`-framed step locus is
rejected (`0204`).

## 2. Vocabulary

`01-glossary.md` §L is authoritative; this is the in-unit contract.

| Name | Contract |
| --- | --- |
| `Remedy` / `RemedyKind` | sealed `Repair`\|`Preview`\|`ExplainRemedy`; a proposal, never an application |
| `Repair` / `Preview` / `ExplainRemedy` | plan + rung / overlay only / explanation only |
| `RemedyProvider` | `solve(Finding, RemedyContext)`; total, empty list = "cannot help" |
| `RemedyContext` | the frozen pre-state a provider reads: the lease's capture, the blueprint, the siting, the claims at one revision, the promoted outcome and context, the options |
| `EditPlan` / `EditStep` | ordered steps / one edit, its rung, its pre-state requirement |
| `Edit` | sealed, 7 cases; a new case would carry new undo and validation semantics |
| `PlanDigest` | content digest of the plan's step multiset; the executor compares the digest it validated with the plan it applies |
| `PlanValidator` / `EditTransaction` / `EditExecutor` | dry run / write scope / host write SPI |
| `OverlayModel` / `CellMark` / `Marker` | marks and markers as data; no renderer type |

## 3. API design

### 3.1 The family

```java
public sealed interface Remedy permits Remedy.Repair, Remedy.Preview, Remedy.ExplainRemedy {
    RemedyKind kind();
    String rationale();
    record Repair(EditPlan plan, Applicability applicability, String rationale) implements Remedy {
        @Override public RemedyKind kind() { return RemedyKind.REPAIR; }
        public Preview preview(FindingBundle bundle) { … }   // the one sanctioned Preview path
        public Locus locus() { return plan().locus(); }
    }
    record Preview(OverlayModel overlay, String rationale) implements Remedy {
        @Override public RemedyKind kind() { return RemedyKind.PREVIEW; }
    }
    record ExplainRemedy(Explanation explanation) implements Remedy {
        @Override public RemedyKind kind() { return RemedyKind.EXPLAIN; }
        @Override public String rationale() { return explanation().summary(); }
    }
}
```

`Repair.preview(bundle)` is I-11 made mechanical: a picture is a projection of the plan an executor
would run, never a second derivation.

### 3.2 Solving

```java
public interface RemedyProvider {
    NamespacedId id();
    List<Remedy> solve(Finding finding, RemedyContext ctx);      // never null, never throws
    static List<Remedy> solveAll(List<RemedyProvider> providers, Finding finding, RemedyContext ctx);
}
public record RemedyContext(Blueprint blueprint, Siting siting,
                            @Nullable ClaimView claims, long ledgerRevision,
                            @Nullable MatchOutcome outcome, @Nullable StructureContext context,
                            RemedyOptions options) {
    public Set<RemedyKind> kinds();        // options().kinds()
    public int maxResults();               // options().maxResults()
}
public record RemedyOptions(Set<RemedyKind> kinds, int maxResults, int planDepth) {
    public static RemedyOptions defaults();     // all kinds, 8, 1
}
```

`blueprint`, `siting` and `ledgerRevision` are always present; `claims`, `outcome` and `context` are
present exactly when the attempt reached the point that produced them, which is why they are
`@Nullable` and why a provider tests them before reading. The rows behind `claims` were written by
`settle` — the only writing entry point — so a provider reads them at `ledgerRevision` and never
writes. A `ClaimView` the host could not supply
is `null`, and that is the row-8 "claims not checked" case, not an empty ledger.

**Providers read promoted values, never recompute them.** The only sanctioned sources are
`ctx.outcome().context()` (a `OUTCOME` retention) and `ctx.context()` (a `CONTEXT` retention, read with
`context.require(key)`). The provider never receives the snapshot lease: the executor holds it for
the apply step (§3.5). A key whose `require` answers `Missing`, and that the outcome does not hold
either, means "this provider cannot help": it returns `List.of()`, and it does **not** walk the
capture to derive the same number privately — a second derivation is a second source of truth, it
is invisible to the post-state check (§4.4 row 9), and it is exactly how a repair comes to size
itself from a value its own plan changes. `kinds()` is the filter, an empty set meaning "offer
nothing", never "offer everything"; `null` from a provider is a programmer error failed at the
boundary.

### 3.3 Plan substrate

```java
public record EditPlan(List<EditStep> steps, String rationale) {
    public static EditPlan empty();
    public CuboidSet touched();            // union of step cells: blast radius, from the plan alone
    public Locus locus();                  // LOCAL, touched()
    public Applicability applicability();  // join over steps (§4.3)
    public PlanDigest digest();            // canonical step multiset, no object identity
    public EditPlan merge(EditPlan other); // concat, dedup, canonical order
    public PlanDiff diff(EditPlan other);
}
public record EditStep(Edit edit, Locus locus, Applicability applicability,
                       @Nullable Expectation precondition) {
    public Cuboid cells();                 // edit.cells()
}
public record PlanDigest(String hex) { }   // lowercase hex of the canonical step multiset, never rendered
```

`merge` is commutative, associative and idempotent: a plan is a function of its step *multiset*.

### 3.4 The seven edits

```java
public sealed interface Edit permits Place, Remove, Replace, SetTrait, Configure, BindAnchor, NestedEdit {
    Cuboid cells();                                             // LOCAL frame
}
record Place(Cuboid cells, OccupantDescriptor expected, @Nullable NamespacedId bay) implements Edit
record Remove(Cuboid cells, @Nullable NamespacedId bay) implements Edit
record Replace(LatticeKey cell, OccupantDescriptor expected, OccupantDescriptor observed) implements Edit
record Configure(LatticeKey cell, Map<String, String> properties) implements Edit
record SetTrait<T>(LatticeKey cell, TraitKey<T> trait, @Nullable T value) implements Edit
record BindAnchor(NamespacedId role, LatticeKey cell) implements Edit
record NestedEdit(LatticeKey cell, EditPlan plan) implements Edit
```

The five cell edits implement `cells()` as the unit cuboid of `cell`.

| Edit | Postcondition | Precondition (pre-state) | Rung ceiling |
| --- | --- | --- | --- |
| `Place` | every cell holds `expected` | cells readable | `MACHINE_APPLICABLE` |
| `Remove` | every cell empty | cells readable | `MACHINE_APPLICABLE` |
| `Replace` | holds `expected`, was `observed` | holds `observed` | `MACHINE_APPLICABLE` |
| `Configure` | type kept, `properties` gained | keys observed in the capture | `HAS_PLACEHOLDERS` if a key was not observed |
| `SetTrait<T>` | carries `trait = value` | `cell` readable | `MAYBE_INCORRECT`: writability is host fact |
| `BindAnchor` | `role` resolves to `cell` | `cell` in blueprint bounds | `MACHINE_APPLICABLE` |
| `NestedEdit` | child steps hold inside the occupant | `cell` holds a nested occupant | `min(own, child)` |

Region edits are content-independent, cell edits address one occupant: a wall repair is one step,
and multi-cell rollback (§4.5) becomes a real obligation.

### 3.5 Dry run, diff, apply

```java
public final class PlanValidator {
    public static ValidationReport validate(EditPlan plan, SnapshotLease lease, Siting siting,
                                            ClaimView claims, RemedyContext ctx);
    public static ValidationReport validate(EditPlan plan, RemedyContext ctx);
}
public record ValidationReport(List<Finding> findings, boolean applicable) { }
public record PlanDiff(List<EditStep> added, List<EditStep> removed, List<EditStep> changed) { }
public interface EditTransaction extends AutoCloseable {
    enum State { OPEN, FAILED, COMMITTED, ROLLED_BACK, REFUSED }
    State state(); void apply(EditStep step); void commit(); void rollback();
    ApplyReport report(); @Override void close();      // rollback when OPEN or FAILED
}
public interface EditExecutor {
    EditTransaction begin(WorldView view, Siting siting, ClaimView claims,
                          long ledgerRevision, PlanDigest plan);   // REFUSED when the host declines
    default ApplyReport applyIfMachineApplicable(Remedy.Repair repair, RemedyContext ctx);
    default ApplyReport applyConfirmed(Remedy.Repair repair, RemedyContext ctx);
}
public record ApplyReport(boolean success, List<Finding> findings, RemedyContext context) {
    public RemedyContext postState();      // == context(), the fresh context for the next round
}
```

The protocol lives in the default methods, so every adapter runs one sequence: validate → gate on
rung → `begin` → fold steps → commit or roll back → report. The kernel owns the order, the host the
writes. `begin` is not a lock on the world: it re-reads the three facts the dry run relied on —
the claims at `ledgerRevision`, the plan digest and the cells — and refuses as `REFUSED` when any
of them moved, so the window between validate and apply is closed by refusal, never by an
overwrite. `ApplyReport.context` is a **fresh** `RemedyContext` built from a post-apply capture:
a second provider round, and the caller's own follow-up, start from post-state or not at all.

## 4. Semantics

### 4.1 One family, three projections (Q1)

One solved state — the `EditPlan` — has three consumers (executor, renderer, human) that differ in
output, not input, and all speak the `Locus` vocabulary `Finding` already uses.

| Kind | Promises | Does not promise |
| --- | --- | --- |
| `REPAIR` | a plan whose post-state answers the finding, with a rung and a dry run able to refuse it | that the host can realise it; that the world still matches |
| `PREVIEW` | a per-cell picture of that post-state, plus markers for what has no cell | that a cell is occupied; that it came from a read |
| `EXPLAIN` | the code's meaning and why the finding is hand-repair only | a plan of any kind |

**Projection rule.** `Preview` is built by `Repair.preview(bundle)`, so both carry the same plan and
bundle. `ExplainRemedy` is offered exactly when no repair can be: a claim conflict, an unreadable
cell, a `SetTrait`-only difference, or a rung below the caller's floor.

### 4.2 `solve`: order, dedup, cap, purity (Q2)

Providers are enumerated by `id()` ascending, then in each provider's documented order; duplicates
collapse on `(kind, payload)` — the step multiset, the mark map or the code — keeping the lowest
provider then list index; the result is ordered by `(kind order, applicability ordinal, provider id,
provider index)` and truncated to `maxResults()`, where `size == maxResults` is the only truncation
signal. `solve` reads no world of its own, mutates no finding, writes no ledger, opens no
transaction, never calls `PlanValidator`; a provider reads only the promoted values in
`ctx.context()` and `ctx.outcome()`. A provider that cannot find its key in either bag returns
`List.of()`.

### 4.3 Plan construction: order, determinism, self-containment (Q3)

Major key = class rank; minor = canonical cell order (`y`, `z`, `x`); third = `Edit` declaration
order; last = originating finding index. Rank: `BindAnchor` (0, the role makes cells meaningful) <
`Remove` (1, the obstruction goes first) < `Replace`, `Place` (2, the occupant later edits write to)
< `Configure`, `SetTrait` (3, needs an occupant) < `NestedEdit` (4, inner cells live inside it).

| Rule | Statement | Violation |
| --- | --- | --- |
| P1 | `step.locus.cells()` equals `step.edit.cells()` | `0205` |
| P2 | frame `LOCAL` or `DEFINITION`, never `WORLD` | `0204` |
| P3 | **last writer** fixes the plan's postcondition per cell | `0201` |
| P4 | rank order holds pairwise on every shared cell | `0202` |
| P5 | depth ≤ `planDepth`; no `NestedEdit` contains itself | `0203` |

**Self-containment.** A step names cells, a descriptor, a trait, a role, a property map, a child plan
and a precondition over the *pre-state*; no snapshot, no `Siting`, no world handle, no other step's
result. Applying the list is a plain fold.

**Rung join.** `EditPlan.applicability()` is the step rungs' **maximum ordinal** under
`MACHINE_APPLICABLE < MAYBE_INCORRECT < HAS_PLACEHOLDERS < UNSPECIFIED`: the least certain step
decides, so a plan is never more applicable than its weakest edit.

### 4.4 The dry run (Q4)

| # | Check | Reads | Code |
| --- | --- | --- | --- |
| 1 | shape: non-empty, depth, no cycle, P1/P2 | plan only | `0307` `0203` `0205` `0204` |
| 2 | P3 last writer; P4 rank order on shared cells | plan only | `0201` `0202` |
| 3 | cell `READABLE`, not `OUT_OF_BOUNDS`/`DENIED` | `readStatus` | `0301` |
| 4 | precondition per step, on the pre-state | `occupant` | `0302` (`0301` if unreadable) |
| 5 | support: no step needs a cell another step clears | plan + row 4 | `0206` |
| 6 | claims: touched cell held `EXCLUSIVE` by another claimant | `claims` | `0303`; shared ⇒ `0304` W |
| 7 | no-op: pre-state already satisfies the step | `occupant` | `0305` I |
| 8 | permission, payload: the kernel names no principal and inspects no payload | none | `0306` I, to `begin` |
| 9 | **post-state**: fold the plan over the captured view, re-run every `ContextKey` the provider read from `ctx.context()`/`ctx.outcome()`, and compare | `lease` + `context` + `outcome` | `0309`, refuse the plan |

`applicable` ⇔ no `ERROR`. Warnings and infos never block — except that claims the host could not
supply (a `ClaimView` whose `read` answers `null`) emit `0308` ("claims not checked") and **never
authorise a write**; only a `validate(plan, ctx)` whose `ctx.claims().read(plan.touched())`
answered gates an apply. Out-of-band cells are refused, not read as air; a host border appears
only as `OUT_OF_BOUNDS`. Nothing mutates: immutable view, read-only claims, no transaction, no
executor — the ledger's only writer is `settle`, and no row of this table calls it. Cost
`O(m log m + v + p + f)` — a host reads `plan.touched().volume()` first, and row 9
costs the keys the provider actually read, not the whole context.

Row 9 is the repair's own consistency check. A plan is allowed to change the world; it is not
allowed to invalidate the number it was sized from. The concrete oscillation it prevents: a
provider reads `mymod:cavity_volume = 100` and computes a floor at `y = 1` sized to 100 cells; the
plan's `Place` covers the floor, the post-state fold re-runs `cavity_volume` as 75 (the placed
fill is no longer air), so the next round sizes a smaller floor at 75, the round after that at 100
again, and the world oscillates between two shapes with no fixed point. Because the fold happens
*inside* the dry run, the first such plan is refused with `0309` and the provider must produce a
plan whose post-state leaves every key it read unchanged — or read no such key at all.

### 4.5 Applying (Q5)

| Aspect | Contract |
| --- | --- |
| open | one `begin(view, siting, claims, ledgerRevision, digest)` per apply, after a passing dry run; `REFUSED` writes nothing |
| refusal at open | a settle that landed between validate and apply moves the ledger revision, so `begin` refuses with `0303` and names the incumbent rows; it never overwrites another owner's cell |
| apply | one step; captures the **first-touch** pre-image of every cell written, state and payload; a refusal sets `FAILED` with `0402` |
| blast radius | exactly `plan.touched()`; a host writing outside it breaks the contract silently |
| commit / close | commit from `OPEN` is durable; close rolls back `OPEN`/`FAILED`, leaves `COMMITTED` |
| rollback | restores each pre-image in inverse step order, payload after its block; failure is `0404`, `success == false` |
| consistency | the host re-captures and re-matches after an apply; a returning finding is the expectation's, and the kernel retries nothing |
| post-state | `ApplyReport.context` is a fresh `RemedyContext` over the post-apply capture; the pre-state context is never handed back |
| audit trail | `findings`: `0303` on a refusal at open, `0402` per failed step, `0403` on complete rollback, `0404` naming cells left changed, `0401` on a host decline |

### 4.6 The picture (Q6)

| # | `CellMark` | When |
| --- | --- | --- |
| 1 | `BLOCKED` | a claim finding (`0303`) covers the cell: the plan may not write it |
| 2 | `WRONG` | last writer `Replace`/`Configure`/`SetTrait`, bundle names the cell |
| 3 | `MISSING` | last writer `Place`, bundle names the cell |
| 4 | `EXTRA` | last writer `Remove`, bundle names the cell |
| 5 | `UNKNOWN` | bundle names the cell unreadable (`0301`), no write intended |
| 6 | `EXPECTED` | `of(blueprint, siting)`: a bay cell, no report consulted |
| 7 | `PRESENT` | the plan names the cell and the bundle says nothing against it |

`PRESENT` states the plan's post-state, not a census. A cell absent from `cells()` is **unjudged**:
`markAt` returns `null`, never `UNKNOWN`. `of(EditPlan, FindingBundle)` previews a broken structure;
`of(Blueprint, Siting)` previews one that does not exist — bay cells `EXPECTED`, `ANCHOR` markers per
role, `SEAM` markers per seam. `Marker` carries whatever has no cell; `MarkerKind` is the severity
(`ERROR`/`WARNING`/`INFO`) or `ANCHOR`/`SEAM`.

### 4.7 Auto-build safety (Q7)

| Gate | Rule |
| --- | --- |
| rung | `applyIfMachineApplicable` writes only for `MACHINE_APPLICABLE`; otherwise `0601`, nothing written; the join means one `SetTrait` demotes the plan |
| confirmation | `applyConfirmed` is the only path below the auto floor, and a separate call |
| dry run first | both drivers validate before `begin`; a non-applicable report opens nothing |
| post-state first | row 9 runs in the same dry run: a repair is never sized from a value it then invalidates (`0309`), so auto-build cannot oscillate between two shapes |
| fresh context | a second round starts from `ApplyReport.context`, never from the context that produced the plan |
| rollback | any step failure rolls the whole transaction back; no partial success |
| foreign claim | `EXCLUSIVE` by another claimant ⇒ `0303` ⇒ not applicable ⇒ never applied; a `SHARED` incumbent (`0304`) is named first, and a settle between validate and apply is caught by `begin`'s revision check, not by luck |

Both drivers write the world and nothing else: the claim rows they read were written by `settle`,
and `settle` is the only call that may write or rewrite one, so an apply never registers, releases
or repairs a claim.

### 4.8 `RemedyOptions` (Q8)

| Knob | Default | Meaning |
| --- | --- | --- |
| `kinds` | all three | the only kind filter; "include previews" is `kinds ∋ PREVIEW`, not a second boolean |
| `maxResults` | `8` | cap after ordering and dedup; `0` = uncapped |
| `planDepth` | `1` | one nested frame; `0` forbids `NestedEdit` |

There is no `allowWorldReads` knob: a provider sees the promoted `outcome` and `context` only, and
the executor — not the provider — holds the capture and folds the post-state at validation time
(§4.4 row 9, §3.5). The narrowing the knob used to express is `RemedyOptions.kinds`, which decides
whether a provider is asked at all.

## 5. Reference sketch

```java
// Host-supplied: lease, claims, context, outcome, bundle, executor, sink, renderer.
// (1) A missing-wall repair, its preview, and a claim conflict that explains instead of repairing.
public final class CasingRemedyProvider implements RemedyProvider {
    @Override public NamespacedId id() { return NamespacedId.of("structurecumulus", "casing"); }
    @Override public List<Remedy> solve(Finding finding, RemedyContext ctx) {
        if (finding.code().equals(Codes.CLAIM_CONFLICT)) {
            return List.of(new Remedy.ExplainRemedy(new Explanation(finding.code(),
                    "a claim conflict cannot be built away", "release the foreign claim", List.of())));
        }
        if (!finding.code().equals(Codes.MISSING)) return List.of();
        Long volume = ctx.context() == null ? null
                : ctx.context().require(CapacityKeys.CAVITY_VOLUME)
                        .fold((value, subjects) -> value, missing -> null);
        if (volume == null) return List.of();       // cannot help; never recompute it here
        Cuboid wall = finding.labels().getFirst().locus().cells().bounds();
        EditStep step = new EditStep(
                new Place(wall, OccupantDescriptor.of(NamespacedId.of("minecraft", "bricks")),
                        NamespacedId.of("structurecumulus", "wall")),
                new Locus(LocusFrame.LOCAL, CuboidSet.of(wall), null, null),
                Applicability.MACHINE_APPLICABLE, Expectations.anyOccupant());
        Remedy.Repair repair = new Remedy.Repair(new EditPlan(List.of(step), "place the casing wall"),
                Applicability.MACHINE_APPLICABLE, "the wall bay is empty");
        return List.of(repair, repair.preview(FindingBundle.of(finding)));   // one plan, two projections
    }
}

// (2) Exhaustive over Remedy — no default branch (04 §3.2).
static String describe(Remedy remedy) {
    return switch (remedy) {
        case Remedy.Repair r -> "repair (" + r.applicability() + ", " + r.plan().steps().size() + ")";
        case Remedy.Preview p -> "preview of " + p.overlay().cells().size() + " cells";
        case Remedy.ExplainRemedy e -> "explain " + e.explanation().code();
    };
}

// (3) Exhaustive over Edit: may an executor write it unattended?
static boolean machineWritable(Edit edit) {
    return switch (edit) {
        case Place p -> true;
        case Remove r -> true;
        case Replace r -> true;
        case Configure c -> false;          // true only when every key was observed
        case SetTrait<?> s -> false;        // writability is a host fact
        case BindAnchor b -> true;
        case NestedEdit n -> n.plan().applicability() == Applicability.MACHINE_APPLICABLE;
    };
}

// (4) A blocked dry run: the wall is free, one cell has another owner.       // 0303 / 0309, nothing opened
ValidationReport dry = PlanValidator.validate(repair.plan(), ctx);
if (!dry.applicable()) { dry.findings().forEach(sink::accept); return; }

// (5) A rotated-furnace preview: the same plan, drawn instead of applied.     // no extra read
renderer.draw(OverlayModel.of(repair.plan(), bundle), siting);

// (6) Mid-plan failure: 0402 at step 2, 0403 for step 1, world byte-identical.
ApplyReport applied = executor.applyIfMachineApplicable(repair, ctx);
if (!applied.success()) sink.acceptAll(applied.findings());
RemedyContext postState = applied.postState();          // a fresh context, never ctx
```

## 6. Use cases

| Who | Case | What U10 gives them |
| --- | --- | --- |
| Addon author | a furnace three casing cells short; another mod owns the mouth exclusively | one `Place` plus its preview; `ExplainRemedy` and no plan for `0303` |
| Host (command) | `/cumulus build <id> <anchor>` | `check` → `settle` → `solve` → `applyIfMachineApplicable` → report as chat |
| Host (GameTest) | a forced failure at step 2 | rollback restores states and payloads, byte-identical |

## 7. Proofs

**P1 — `solve` and `validate` mutate nothing.** The only write verb is `EditTransaction.apply`,
reachable only from an `EditExecutor`; neither `solveAll` nor `validate` names one, and both take
immutable arguments. Residual: cost, not state.

**P2 — a plan is deterministic and self-contained.** The order is total over `(rank, y, z, x, kind,
finding index)`, dedup keeps the minimal index, and no map iteration, clock or randomness
participates (I-15); `merge` is commutative, associative and idempotent. Self-containment is P1–P5
plus the pre-state rule, so apply is a fold; two providers disagreeing on one cell is `0201`.

**P3 — dry-run soundness relative to executor preconditions.** Rows 1–7 of §4.4 are exactly the
predicates the executor's pre-write check evaluates over the same immutable `(view, claims, siting)`,
so a reported `ERROR` at step *i* implies refusal at step *i*. The implication is one-way by design:
the executor also refuses what the kernel cannot express (permission, an unrealisable descriptor,
physics). The window between validate and apply is closed by `begin` comparing the ledger revision
and the plan digest it was validated against: a settle that landed in between is `0303` and the
transaction never opens, so the executor refuses rather than overwrites.

**P4 — rollback completeness.** Each `apply` captures the *first-touch* pre-image of every cell it
writes — all *n* cells of a region edit — and nothing outside `touched()` is written; rollback writes
each pre-image back in inverse step order, payload after its block. `Remove(3)` → `Place(2 of them)`
→ failure: naive per-step undo restores intermediate air, first-touch capture restores all three.

**P5 — preview and repair agree.** `Repair.preview` hands the same plan to `OverlayModel.of(plan,
bundle)`, so the mark table is a function of `(plan, bundle)` alone. After a successful apply and a
fresh capture the re-match yields no finding for a repaired cell (I-4), so `of(samePlan,
emptyBundle)` marks exactly those cells `PRESENT` — the preview state, not a claim of occupancy.

**P6 — no auto-apply below the top rung.** `solve` returns values and cannot reach an executor;
`applyIfMachineApplicable` refuses anything but `MACHINE_APPLICABLE` (`0601`); `applyConfirmed` needs
a distinct call by a holder of the repair; one `SetTrait` demotes the whole plan. A host calling
`begin` directly is outside the contract, which is why the write path is an SPI.

**P7 — a repair cannot invalidate its own input.** Row 9 folds the plan over the same captured
view the provider read and re-runs every key the provider took from `ctx.context()`/`ctx.outcome()`.
The keys are pure functions of the captured content and the plan is a value, so the fold is
deterministic and needs no world write; a changed key is `0309` and `applicable` is false, so the
plan never reaches `begin`. A provider that reads no promoted key cannot fail this row, and a
provider that recomputes privately is out of contract (§3.2) — which is the residual, and why the
rule is stated on both sides.

## 8. Dependencies and open questions

U10 sits on U9 (every input and emitted `Finding`), U4 (`ClaimView`, the claim row: the `0303`/`0304`
gate), U5 (`Blueprint`, `Bay`, the anchor role ids), U3 (`Expectation`, for `EditStep.precondition`), U1
(`Locus`, `LatticeKey`, `Cuboid`, `CuboidSet`), `11`/`12` (`MatchOutcome`, `StructureContext`, the
keys a provider reads) and A1 (the executor, which also builds the post-apply context). Sharp
edges: a `ClaimView` that cannot say "none" vs "cannot answer" makes `0304`-free silence unsafe; a
`Bay` id unstable across reloads breaks reproducibility; a precondition needing a `MatchSession`
cannot be validated; `CuboidSet.of(..).bounds()`, a unit-`Cuboid` factory, and a `Locus` with a null
span are assumed, unspelled in `05`.

Proposed `SC-RMD` codes for U9 to accept or renumber: plan `0201`–`0205` (writers, order, depth,
frame, locus mismatch); dry run `0301` unreadable, `0302` precondition, `0303` foreign claim, `0304`
shared incumbent, `0305` already satisfied, `0306` permission, `0307` empty, `0308` claims
unchecked, `0309` post-state changes a `ContextKey` the provider read; apply `0401` refused, `0402` step
failed, `0403` rolled back, `0404` rollback incomplete; gate `0601` auto-apply below the rung. The
umbrella code for "the dry run refused" is `SC-RMD-0001` (`13` §4.3).

One of `22` §16's questions is this unit's, and it lands exactly on `begin`:

| Id | Question |
| --- | --- |
| OQ-1 (`22` §16 Q4) | `RevisionMoved`: one retry inside the settle transaction. A second movement is either a retry token (`Deferred`) or a hard `Undecided` — and at apply time the same event is `0303` with no retry at all, so the two paths must agree on the word. |

## 9. Falsification

| Falsifier | What it breaks | How to run it |
| --- | --- | --- |
| a plan that validates then fails mid-apply | P3's one-way claim, if the cause were visible | claim one cell `EXCLUSIVE` between validate and apply; `begin` must refuse with `0303` and name the cell, and nothing may be written |
| a plan sized from a number it changes | P7 / §4.4 row 9 | a provider reads `mymod:cavity_volume` and places a fill inside the cavity: the dry run must return `0309`, `applicable == false`, and `begin` must never be reached |
| a second round reusing pre-state | §3.5 `ApplyReport` | apply a plan, then call a provider with `applied.postState()`: the promoted values must be the post-apply ones, not the ones the plan was built from |
| a preview disagreeing with the repair | I-11 / P5 | build a `Preview` by hand from another plan's cells; `PlanDiff` is non-empty, and `plan().digest()` differs from the repair's |
| a rollback that misses a block entity | P4 | payload-bearing occupant, `Remove` a 3-cell region around it, fail step 2, assert the payload is byte-identical |
| an auto-build overwriting a shared claim | §4.7 | two blueprints sharing a cell; the second `applyIfMachineApplicable` must fail with `0303`/`0304` |
| a `SetTrait`-only plan auto-applied | P6 | one-step plan; refuse with `0601` |
| a provider recomputing a missing key | §3.2 | a context with the key absent and a view that would answer it: `solve` must return the empty list, and the remedy's plan must not exist |
| an overlay built by reading the world | §4.6 | a `WorldView` whose reads all throw; `of(plan, bundle)` must still return |
| a `WORLD`-framed step that validates | P2 | one step with a world `Locus`; expect `0204`, never a write |
