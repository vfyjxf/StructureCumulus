# 11 — Engine (U7, `kernel.engine`)

> One match: a request fixes the question, a session accumulates the answer, a sealed result
> reports it in six arms. Serves R-01, R-04, R-05, R-08, R-11, R-13; touches I-4, I-5, I-6, I-7,
> I-10, I-15.

## 1. Scope

Owns the attempt (`MatchRequest`, `MatchPolicy`, `MatchSession`, `MatchResult` + six arms,
`UndecidedReason`), detection output (`MatchOutcome`, `BayBinding`, `SeamBinding`, `Census`), the cheap
pre-read (`PrescreenIndex`, `AnchorCandidate`, `Fingerprint`), incremental re-check (`ReadSet`,
`ChangeSet`, `InvalidationIndex`, `InstanceRef`), unit order, the fold, the gate, the spend ledger,
the cache key and the next attempt. Not `Reply` semantics (`07`), `Bay` expectations (`07`, `09`),
conflicts (`08`), derivation (`12`), findings (`13`), remedies (`14`), the space algebra (`05`),
`CheckPlan` (`09`) or scheduling. It writes nothing except through `settle`, schedules nothing, reads
no clock and throws for no outcome. The host execution model, snapshot capture and dirty refresh are
`20`; this unit is the synchronous kernel call that layer invokes. `Budget` is not this unit's: the
slot owns the `InstanceBudget` and passes it by reference, so a request cannot open, reset or widen
one.

## 2. Vocabulary

Every name is defined once in `01-glossary.md` §I and this unit owns all of them, adding no synonym:
`MatchRequest` (blueprint, siting, lease, policy, budget, pins, record, owner, ticket),
`MatchPolicy`, `MatchSession`, `MatchResult` with `Admitted` / `Dropped` / `Formed` / `Failed` /
`Undecided` / `Deferred`,
`UndecidedReason`, `MatchOutcome`, `BayBinding`, `SeamBinding`, `Census`, `PrescreenIndex`,
`AnchorCandidate`, `Fingerprint`, `ReadSet`, `ChangeSet`, `InvalidationIndex`, `CacheKey`,
`InstanceRef`. `Spend` is a work-unit value (`20` §7) and the only ledger an attempt reports;
`Budget` is the slot's and never this unit's. No clock type exists.

Glossary deltas: the request carries the ticket, the resolved siting, the lease and a budget
reference; `Formed` carries `generation`, `contextRevision`, `ledgerRevision` and a child lease
handle; `Admitted` and `Formed` are `AutoCloseable` — forced by `04` §8 and `22` §5.5. The
contribution surface a unit drives is `ContextKey` with its `Merge`, a `Computation` with
`phase()`, `ContextDraft.contribute` and `ContextView.require` (`22` §6.4, §8.3).

## 3. API design

`build()` throws `IllegalArgumentException` only for programmer errors (`DESIGN` without a declared
plan, a null updater); every other wrong call is a finding. The session's mutators are
package-private, so no extension binds, reserves, reports or widens a ceiling (doctrine §10). The
request is the attempt's determinism tuple, so it is frozen; the session accumulates spend and
bindings, so it is not. `Formed` alone carries an outcome and claims, and `MatchOutcome` is ordered: bays
by id, seams by id, claims by cell then bay.

```java
public record MatchRequest(Blueprint blueprint, Siting siting,
                           SnapshotLease lease, MatchPolicy policy, InstanceBudget budget,
                           PinBudget pins, InstanceRecord record, OwnerId owner,
                           @Nullable Ticket ticket) {}   // ticket = slot state, not capture state

public MatchResult check(MatchRequest request);                       // read-only
public MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);  // the only writer

public record MatchPolicy(int maxSectionsPerUnit, CacheMode cache, boolean parallel) {
    public static MatchPolicy defaults();          // (8, OUTCOMES, false); a host may only narrow
}

public sealed interface MatchResult extends AutoCloseable
        permits MatchResult.Admitted, MatchResult.Dropped, MatchResult.Formed, MatchResult.Failed,
                MatchResult.Undecided, MatchResult.Deferred {
    Stage stage();  FindingBundle findings();

    @Override default void close() {}              // a result that holds no handle closes to nothing

    record Admitted(Stage stage, Siting siting, MatchOutcome outcome,
                    @Nullable ClaimPlan planned, long ledgerRevision,
                    SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }     // this child handle only
    }
    record Dropped(Stage stage, DropCause cause, List<Finding> findings) implements MatchResult {}
    record Formed(Stage stage, InstanceId id, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long generation, long contextRevision, long ledgerRevision,
                  SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }
        public ReadSet readSet();                  // union of outcome.stepReads()
    }
    record Failed(Stage stage, List<Finding> findings) implements MatchResult {}
    record Undecided(Stage stage, UndecidedReason reason, List<Finding> findings,
                     @Nullable MatchOutcome partial, @Nullable RetryToken retry) implements MatchResult {}
    record Deferred(Stage stage, RetryToken token, List<Finding> findings) implements MatchResult {}
}
// MatchSession: idempotent run(), spend(), remaining(), readSet(), probe(); writes are internal.
```

The siting is resolved before the request exists: there is no `Place` in the request and no
orientation search inside `run()` (`22` §4.4). `ticket` is the coalescing token, `lease` a move — a
request that is never dispatched receives no lease, and one that is dispatched owns it until the
result or the scheduler takes it back. `blueprint` is the `Blueprint` the catalog frozen at `apply`
resolved, before unit order is computed. The budget is the slot's object by
reference: `run()` spends against it and can never reset a counter. `MatchPolicy.defaults()` keeps
`maxSectionsPerUnit` at 8, which covers a unit whose region straddles a chunk corner; the limit
bounds the `(SectionKey, contentDigest)` list that `closureDigest` hashes (§4.7), not correctness.


## 4. Semantics

### 4.1 The algorithm

```java
MatchResult check(MatchRequest request) {          // read-only; worker over one snapshot
    Stage stage = stageOf(request);           // the engine's pipeline assigns it; no field names one
    CheckPlan plan;
    try { plan = request.blueprint().expand(stage); }                   // 1 expand the stage's plan, pure
    catch (IllegalArgumentException rejected) { return seal(Failed, SC_ENG_0102); }
    if (plan.checks().isEmpty()) return seal(Undecided, NO_JUDGING_UNIT);  // nothing judges
    PrescreenIndex index = PrescreenIndex.of(request.lease().view(), plan, request.siting());
    runSectionPass(plan);                                             // 2 sections, then the barrier
    runAggregatePass(plan);                                           // 3 aggregates, Kahn order
    for (Check<?> check : plan.checks()) {                            // 4 checks, declaration order
        ReadScope scope = check.reads(owner(check).cells());
        if (!slot.tryCharge((int) scope.cells().volume(), sectionCount(scope.cells())))
            return seal(Undecided, BUDGET_EXHAUSTED);
        tally.add(check, check.run(focusContext(check, index)));
    }
    switch (CaptureGate.evaluate(request.lease())) {                  // 5 the one check
        case CHANGED    -> return seal(Dropped(SNAPSHOT_CHANGED), SC_ENG_0003);
        case SUPERSEDED -> return seal(Dropped(CAPTURE_INVALID), SC_ENG_0006);
        case CURRENT    -> { }
    }
    Tally.Fold fold = tally.fold();                                   // 6 fold
    if (fold.failed())    return seal(Failed, fold.findings());
    if (fold.undecided()) return seal(Undecided, fold.reason(), fold.findings());
    ClaimPlan planned = fold.plan();                                  // 7 the plan: bindings + revision, no rows
    ClaimPlan.Matched matched = (ClaimPlan.Matched) planned;
    ClaimPlanner.Derived d = ClaimPlanner.derive(request.world(), request.blueprint(), request.siting(),
            request.owner(), matched.bindings());                      // 8 derive the rows, purely
    ClaimView held = ledger.read(d.staticFootprint());                 // 9 the ledger, read only
    if (!held.available()) return seal(Undecided, LEDGER_UNAVAILABLE);
    if (ClaimMatrix.conflicts(d.rows(), held.rows()))                  // a prediction, not a conclusion
        return seal(Failed, SC_CLM_0002, held.rows());
    return seal(Admitted, planned, held.revision());                   // nothing written
}

MatchResult settle(MatchRequest request, MatchResult.Admitted admitted) {  // server thread
    try (admitted) {                                                   // the admitted handle dies here, always
        switch (CaptureGate.evaluate(request.lease())) {               // 3a the capture still holds
            case CHANGED    -> return seal(Dropped(SNAPSHOT_CHANGED), SC_ENG_0003);
            case SUPERSEDED -> return seal(Dropped(CAPTURE_INVALID), SC_ENG_0006);
            case CURRENT    -> { }
        }
        if (!(admitted.planned() instanceof ClaimPlan.Matched matched))
            return seal(Undecided, LEDGER_UNAVAILABLE);                // 3b no ledger to write into
        if (!bindingsHold(request, matched)) return seal(Failed, SC_CLM_0005);   // 3b bays, capture, kinds, cells
        ClaimPlanner.Derived d = ClaimPlanner.derive(request.world(), request.blueprint(), request.siting(),
                request.owner(), matched.bindings());                  // 3c the only row factory, pure
        ClaimVerdict v = facade.settle(new SettleRequest(request.world(), request.owner(), request.siting(),
                d.rows(), admitted.ledgerRevision()));                 // 3d one CAS over (world, owner, siting)
        if (v instanceof RevisionMoved moved)                          // one re-read, inside the call
            v = retrySettle(d, moved);
        if (v instanceof RevisionMoved) return seal(Undecided, LEDGER_MOVED);
        if (v instanceof ClaimConflict c) return seal(Failed, SC_CLM_0002, c.rows());
        return seal(Formed, d, (Settled) v);                           // Formed only after Settled
    }
}
```

```
scheduler ─request(ticket,siting,lease)─► check(worker) ─fold─► Admitted(planned, ledgerRevision)
                                                       │
         MatchResult ◄── seal ◄── settle(request, admitted)(server thread, frozen revision)
```

A plan unit is one `Computation` or one `Check`; a computation's `phase()` decides whether it runs
per section or once after the barrier. The engine owns the pipeline and picks the stage it runs
(`stage`), so neither the request nor a `Check` declares one and `Stage` is filled into the results;
within a stage, units are ordered `(phase, declaration index)`: the section pass completes, the barrier folds
its contributions, then aggregates run in Kahn order over `inputs()` and the checks follow the units
they read (`22` §7.1). Independent units keep declaration order; cost is not a priority. `check`
never writes and never settles; `settle` never matches.

Billing: the budget has two legs, `cells` and `sections`. A check charges its declared read closure
and the sections it touches; a section computation charges the slice volume plus one section; an
aggregate charges nothing. Every charge happens on dispatch, cached or not. `Spend(cells, sections)`
is the whole ledger — no per-unit admission, no checks leg, no retries leg.


### 4.2 Anchors and orientation

The anchor cell is the **definition origin**, always: the world cell read is `siting.origin()`,
supplied by the caller, so orientation is not circular (`22` §4.4). No candidate is scored and no
orientation is searched.

```java
record AnchorSpec(NamespacedId role, NamespacedId property, String value) {}

interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
}
```

| Rule | Contract |
| --- | --- |
| Read | One cell, the origin; one cell and one section are charged. `AnchorResolver` has that read scope and no other. |
| Map | The cell's property/value yields a `Heading`; the siting is the unique placement whose `apply(authoredFront) == anchorHeading`. One direction, one formula — a candidate that fails it is not a candidate. |
| Input | The request carries a resolved `Siting`; `facade.siting(bp, origin)` is the only path that resolves one, and it runs on the server thread over a capture. |
| None | Readable but no satisfying mapping → `Failed(SC-ENG-0104)`; the cell unreadable → `Undecided(UNREADABLE)`. |

A structure whose controller sits elsewhere puts its origin there; no controller entity exists (I-5).

### 4.3 Prescreen soundness and `Fingerprint`

| Check | Rejects when | Outcome and why a valid structure survives |
| --- | --- | --- |
| Bounds | `footprint ⊄ view.bounds()` | `Undecided(UNREADABLE)` — outside the capture nothing is known, not even that it is wrong. |
| Anchor | The origin cell is readable but yields no heading satisfying the one mapping direction | `Failed` — the anchor is the blueprint's own declaration of what sits at the origin. |
| Census | `observed < demand` for a declared type | `Failed` — `demand` is a declared lower bound; any placement satisfying the bays supplies that many. |

A rejection is `Failed` only when it negates something the **blueprint declared**, and `Undecided`
when it negates a condition of *knowing*; a heuristic ("fewer bricks than last time") is neither: the
census is never compared with another observation. No frozen type carries a
per-type demand (`ExpectationMeta.requires` is traits, not counts), so the census check today only
observes.

`Fingerprint` covers the template — id, bounds, bay cuboids with their `Coverage`, `Footprint` and `Sharing`, anchor specs, orientation policy, demand, and each plan's unit ids — digesting canonical
content, so it is stable across hosts. It is a cache key, not a gate: a hit still compares id and
bounds, so a collision costs a miss.

### 4.4 Verdict folding

```
Failed     ⇔  mismatch ≥ 1 ∨ claimConflict ≥ 1
Undecided  ⇔  mismatch = 0 ∧ claimConflict = 0 ∧ ( unreadable > 0 ∨ abstain > 0 ∨ untested > 0
                             ∨ no judging unit ∨ cancelled ∨ budget refused )
Formed     ⇔  mismatch = 0 ∧ claimConflict = 0 ∧ unreadable = 0 ∧ abstain = 0 ∧ untested = 0
              ∧ ≥1 judging unit
```

| Rule | Contract |
| --- | --- |
| Precedence | Refutation outranks ignorance: one `MISMATCH` gives `Failed` even beside unreadable cells. |
| Reason | `CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS` — the first five *we stopped*, last three *we learned nothing*; independent of unit order. |
| Modes | There is no failure mode: a `MISMATCH` never truncates the fold, so `MatchPolicy` has no knob whose value could change the verdict, the bundle or `Spend` (§4.7). |
| Not a soft failure | `Failed` licenses a repair that changes the world; `Undecided` licenses a re-read, and collapsing them builds repairs for structures that may be correct. |

### 4.5 Claims and settlement

| Moment | Action | Failure |
| --- | --- | --- |
| Worker fold | The matcher emits one `BayBinding` per bay that bound a subject and one `ClaimPlan.Matched` carrying them with the revision it read; it derives no row, writes nothing and sees no ledger. | — |
| Fold not `MATCH` | No `ClaimPlan` leaves the attempt; no claim is minted. | — |
| `check` reads | The frozen claim view over the derived `staticFootprint` answers the incumbent rows and the revision the plan is built against. | `ClaimConflict` → `Failed(SC-CLM-0002)` with the rows read now — a prediction, not a conclusion |
| Server step | `settle(request, admitted)` is the only writing entry point: it runs on the server thread, evaluates `CaptureGate` on the request's lease, checks the bindings, derives the rows with `ClaimPlanner`, then one CAS over `(world, owner, siting)` at the revision the admitted result carries. It closes `admitted` on every path. | `ClaimConflict` → `Failed(SC-CLM-0002)` with the incumbent rows; `RevisionMoved` → one retry inside the call, then `Undecided(LEDGER_MOVED)` with a token |

`ClaimMatrix.conflicts(a, b)` is the only conflict predicate and lives in `08`; the kernel and the
adapter both call it and neither adds a test of its own. `SHARED`/`SHARED` never conflicts,
`Formed.claims()`
carries the revision it settled at, so a later replacement is detectable.

### 4.6 Snapshot check, discards and the next attempt

`CaptureGate.evaluate(lease)` is the whole check, run after the units and before the outcome is
published or settled. It reads two facts off the request's own lease:

| Fact | Read from | `Verdict` when it fails |
| --- | --- | --- |
| the watch is clean | `lease.changeCount() == 0` | `CHANGED`: the watched region was marked since capture |
| the capture is valid | `lease.isValid()` | `SUPERSEDED`: its world was replaced, or the handle was closed |

The kernel keeps one counter per watch, so a mark anywhere in it drops the whole attempt: a capture
never blends two revisions, and nothing re-runs a unit in the capture it was taken from.

```
Dropped(SNAPSHOT_CHANGED):   // changeCount -> SC-ENG-0003
    nothing is published; the slot stays dirty
Dropped(CAPTURE_INVALID):    // isValid -> SC-ENG-0006
    nothing is published; the next attempt captures again
```

Slot supersession is not a kernel fact. The scheduler owns the `Ticket`, compares it when the worker
posts, and reports the abandonment as `MatchCompletion.Discarded`; a capture carries no ticket, so a
preview can never invalidate a plan that is waiting to be settled (`19` R37).

The per-unit form (`cellStamp(unit)`, so only the units that read a marked cell are dropped and the
rest are reused) needs the host's dirty index and the attempt cache; until both exist the
kernel-wide counter is the conservative form, and it can never publish a `Formed` on a stale capture.

Counters live in the slot, keyed by unit id — `discards`, `attempts`, and the `InstanceBudget` — so a
coalesced submit inherits them and only a successful settle or `dropInstance` resets them. After
`maxDiscards` (slot policy, default 3) the attempt settles `Undecided(DIRTY_UNSTABLE)` with
`SC-MC-0404`, and the previous `Formed` is re-emitted with `generation + 1` and the same outcome, so a
consumer can tell a re-emission from a new match.

```java
final class InstanceBudget {                 // owned by the slot; the request holds the reference
    long discards(NamespacedId unit); long attempts(NamespacedId unit);
    Spend spent();                           // Spend(cells, sections), cumulative
    boolean tryCharge(int cells, int sections);   // false ⇒ the slot cannot afford this unit
}
```

Permits are the host's arithmetic, stated here because the kernel reads the slot: `INTERACTIVE` holds
one reserved permit, `AUTO` gets `max(0, threads - 1)`, and on a one-worker host `AUTO` is disabled
rather than starved — `/cumulus workers` says so. `COMMAND` shares the remainder. `Deferred` is a
terminal outcome with a retry token, and only `AUTO` re-submits it, with a fresh token.

`InstanceRef.generation` is *instance* generation, derived from `contextRevision` plus the settled
match, never bumped by hand and never *data* generation (R-09 emission), which the engine never sees.
`ReadSet.cells` is durable, `occupants` snapshot-scoped, and a `ChangeSet` with no changed cell but
non-empty `changedTypes` is *unlocalised*: the next attempt reuses nothing.

### 4.7 Cache and reuse

```java
record CacheKey(PlanId plan, Siting siting, UnitId unit, long closureDigest) {}
```

- The entry is a contribution (a section `Computation`'s per-section output) or a unit outcome (a
  check's reply) — never a folded value, so no `Merge` is consulted and reuse cannot change a
  result.
- `closureDigest` hashes the sorted `(SectionKey, contentDigest)` pairs the unit read; a differing
  digest is a miss, and there is no epoch comparison and no blending of half a unit's sections.
- `CacheMode` is `OFF | PARTIALS | OUTCOMES`: `OFF` stores nothing, `PARTIALS` stores section
  contributions, `OUTCOMES` stores contributions and unit outcomes.
- A hit changes neither the value nor `Spend`: cells and sections are charged on dispatch regardless,
  and the capture and refresh legs are closure-sized regardless. `parallel` is part of `MatchPolicy`, so it
  is inside the determinism tuple and changes neither `Spend` nor finding order.
- The store is `20` §6.3's `AttemptCache`, one LRU under `cacheBytes`, never under `retainedBytes`.

### 4.8 Threading and determinism

Capture, dirty refresh and claim settlement run on the server thread; `check` runs on a worker over
the lease's immutable `WorldView` (`20` §2–§5). No live world, ledger write, `ThreadLocal`, I/O,
clock or randomness enters the kernel; `Expectation`s are shared across concurrent attempts, so they
must be re-entrant and see only `ProbeSession`. `MatchSession` is the internal accumulator of one
synchronous `run()`; it is not a public resumable session and has no `advance`. Determinism: fixed
`(blueprint, snapshotId, siting, policy, catalog, frozen ledger revision, cancellations)` ⇒
identical value, bundle order and `Spend`; a host input only weakens. A result reaches the world only
after the capture gate in §4.6; the kernel itself has no opinion about which task should win.

## 5. Reference sketch

```java
Siting siting = kernel.siting(furnace, LatticePoint.of(10, 64, 10), lease);   // one anchor cell
MatchResult probe = kernel.check(new MatchRequest(FURNACE, siting, lease,
        MatchPolicy.defaults(), slot.budget(), pins, record, owner, ticket));   // (a) slot-owned
switch (probe) {                                                    // exhaustive, no default
    case MatchResult.Dropped d   -> scheduler.resubmit();           // the snapshot moved
    case MatchResult.Failed f    -> renderer.render(f.findings());  // SC-ENG-0104: anchor absent
    case MatchResult.Undecided u -> log(u.reason());                // (c) UNREADABLE
    case MatchResult.Deferred d  -> scheduler.reprobe(d.token());   // AUTO only, fresh token
    case MatchResult.Formed p    -> index.record(p.id(), p.readSet());  // settle produced it
    case MatchResult.Admitted a  -> scheduler.submitForm(a);        // (b) the lease moves
}
// The scheduler closes a lease it took back (supersede, defer, cancel, stop) in its finally only when
// the request produced no result; a lease behind an `Admitted` belongs to whoever holds that result,
// and `settle` closes it.

// FORM (b) registers the admitted result: `settle(request, a)` runs on the server thread and answers
// the same six arms; on `Formed`:
//     index.record(full.id(), full.readSet());                         // settlement already ran
//     StructureContext ctx = kernel.derive(full, furnace.derivations());   // (e) U8, after Settled
ChangeSet change = new ChangeSet(Set.of(LatticeKey.of(LatticePoint.of(12, 65, 10))), Set.of(), 41L);
@Nullable List<InstanceRef> stale = index.affectedBy(change);       // (d) null: re-check everything
for (InstanceRef ref : stale == null ? index.instances() : stale) {
    scheduler.markDirty(keyOf(ref), change);                        // W3; the next attempt captures
}
```

The shared-hatch conflict (e) is what the settle entry point exists for: A's `EXCLUSIVE` claim and
this attempt's `SHARED` hatch bay are incompatible under `ClaimMatrix`, so the result is
`Failed(SC-CLM-0002)` with one finding carrying both cells — not a silent overwrite, and not
`Undecided`, because the ledger answered.

## 6. Use cases

| Audience | Case |
| --- | --- |
| Addon author | A furnace declares a `PRIMARY` anchor at its origin, a `SHARED` hatch bay and a plan of bounds plus anchor units; the author never sees the session, index or unit order. |
| Host | A build wand: `PROBE` on click, `FORM` on confirm, `Formed` after settlement, `Undecided` rendering `UNKNOWN` not `MISSING`. |

## 7. Proofs

| Claim | Argument |
| --- | --- |
| P-1 Determinism | Only the frozen request tuple, the lease's immutable view, the frozen blueprint and catalog and the ledger revision are read; a fixed tuple with an open lease and a stable ledger yields an identical value, bundle order and `Spend`. No clock is read. |
| P-2 Soundness | `Failed` needs a `MISMATCH` or a `ClaimConflict`; `Formed` requires every unit `MATCH` and a `Settled` verdict; `Undecided` names a stop or a gap; `Deferred` names a token. |
| P-3 No false negative | Bounds holds or the attempt is `Undecided`, the anchor maps or the cell is unreadable, and the census dominates a declared demand. |
| P-4 Billing is cache-independent (`22` P1) | `cells` and `sections` are charged on dispatch, cached or not; a check charges its read closure, a section computation its slice plus one section, an aggregate nothing; `Spend(cells, sections)` is the whole ledger, so warm and cold runs of one plan spend the same and a cache cannot turn `BUDGET_EXHAUSTED` into `Formed`. |
| P-5 Child-handle release (`22` P4) | `InstanceRecord` owns the capture; `Admitted` and `Formed` hold child handles that release only their own reference. `Admitted` is closed by the caller when it never calls `settle`, and by `settle` itself in every case once it is called; `Formed` is closed by the caller after `derive`. A handle left open shows up as `metrics().openHandles`, never as a closed view, and never closes the instance's reference twice. |
| P-6 Invalidation completeness | A mark in the watched region bumps `lease.changeCount()`, so the gate answers `CHANGED` and the attempt is `Dropped(SNAPSHOT_CHANGED)`; a replaced world or a closed handle answers `SUPERSEDED` and it is `Dropped(CAPTURE_INVALID)`. Slot supersession belongs to the scheduler (`19` R37). No unit is ever re-run against the capture it read, and nothing is published on a mismatch. |

## 8. Dependencies and open questions

Needs U0–U5 and U9 for the names its signatures show (`Ticket`, `Siting`, `SnapshotLease`,
`InstanceBudget`, `ClaimPlan`, `ClaimVerdict`, `SettleRequest`, `CacheMode`, `Reply`), plus the
unfrozen `Blueprint.expand(Stage)`.

| Id | Question | Proposal |
| --- | --- | --- |
| Q-1 | `RevisionMoved` after the one in-transaction retry (`22` §16 Q4): a retry token or a hard `Undecided`? | current: `Undecided(LEDGER_MOVED)` carrying a token, per `22` §10.1 — a second movement is never silent |

`22` §16 leaves nothing else open for this unit. Two names it uses are outside `22`'s lists and are
carried unchanged: the `SC-ENG-*` family (`0101`–`0104`, `0201`, `0401`), and `PrescreenIndex` /
`Census`, which `22` §7.1 replaces with `Check` units but does not rename.

## 9. Falsification

| # | Broken claim | Test that breaks it |
| --- | --- | --- |
| F-1 | Prescreen soundness | A valid 8×7×8 furnace plus a decorative block outside every bay but inside the footprint: a census heuristic says `Failed`, where the answer must be `Formed` or `Undecided`. |
| F-2 | Unreadable ≠ mismatch | Unload the interior chunk, run `FORM`: any path mapping `ReadStatus != READABLE` to `MISMATCH` reports `Failed`. |
| F-3 | Billing equality | Run one plan cold, then warm with `CacheMode.OUTCOMES`: the two `Spend` records are equal on both legs, and neither turns `Undecided(BUDGET_EXHAUSTED)` into `Formed`. |
| F-4 | Next-attempt reuse | Mark one wall cell after capture: the attempt is `Dropped(SNAPSHOT_CHANGED)`, no `Formed` carries an old verdict, and the next attempt captures again. |
| F-5 | Settlement ordering | A second owner claims the same `EXCLUSIVE` cells between `check` and `settle`: the result is `Failed(SC-CLM-0002)` or `Undecided(LEDGER_MOVED)`, never `Formed` with the first verdict's claims. |

The reviewer's sharpest question: **can any path reach `Formed` without every planned unit returning
`MATCH` over readable inputs and a `Settled` verdict, or reach `Failed` from an unreadable cell?** If
yes, §4.4, §4.5 and P-2 are wrong.
