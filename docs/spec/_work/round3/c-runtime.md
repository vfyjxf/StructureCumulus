# Round 3C — runtime, threading, lifecycle, budget

## 1. Scope and method

Input: `22` r5 (`§5`, `§8`, `§9`, `§10.3`, `§11`, `§13`, `§14`) plus the two files decided today that
move its call surface — `23` (check/settle split, `release`, three lifecycle points, three conflict
layers) and `24` (`Coverage`/`Footprint`/`Sharing`, R2 at settle). Code read: `StructureKernel`,
`MatchSession`, `MatchSettler`, `InstanceRecord`, `ContextStore`, `SnapshotInfo`, `MatchResult`,
`MatchOutcome`, `ClaimPlan`, `ClaimMatrix`, `InMemoryClaimLedger`, `ClaimSet`, `ClaimLedger`. `§15`
and `19 R1–R26` are closed; the registration I overturn is named where it falls. Vanilla facts via
SourceScout after `refresh_index`.

Method: `23` splits one call into two, so every mechanism `22` justified by "inside one attempt" is
re-derived as two calls with a caller-owned gap. A defect is reported only when I walked a machine, a
world state and a thread, and the failure is a contradiction inside the canon. Ordered by damage: 1
loses a load-bearing mechanism, 2–5 make the write path unverifiable or unsafe, 6–9 are lifecycle and
budget leaks.

## 2. What I checked

The gate against `11` §4.6; the ledger's CAS/revision arithmetic against `§10.1` and `23` §3.3;
`MatchSettler` against `23` §3.1/§3.2; the `(owner, siting)` exclusion; the store's write path and
frame list; `InstanceRecord.reemit`; `release`'s key against the row shape and who could enumerate a
dimension's rows at unload; every thread statement about the ledger in `23` §5 and `16` §4.6/§4.10;
what a settle can be checked against; `Spend`'s two legs against the work `23` adds. SourceScout:
`SavedData`'s dirty flag is a plain `boolean` (`SavedData.java:18`, `:21`);
`net.neoforged.neoforge.event.level.LevelEvent` exists.

## 3. Ranked defects

### 1 — `SETTLE`-tier units have no runner: `RUNTIME` loses its producer

**Scenario.** A datapack declares a `capacity` computation at `SETTLE` (`§12.3`; `§8.1`: Stage
SETTLE → Tier SETTLE → Projection `RUNTIME`). The host calls `check` (`23` §2: reads, writes
nothing) then `settle(request, plan, live)`, whose `§3.1` body is 3a snapshot, 3b revision, 3c ledger
CAS. **No unit runs**, so no match-derived value reaches the SETTLE frame and
`Formed.contextRevision` names another tier; under the other reading the unit is silently dead
(`§13` has `derive` read the *FORM* frame) and `23` §7 deletes `stage`, so the author cannot see it.

**Violated.** R-03, `§8.1`, `§6.4` (`Retention.CONTEXT` "into the stage's tier frame" — no stage
boundary left), `§10.4`, `§13`.

**Why the stated proof fails.** `§10.3` "settlement inside the call" made SETTLE a stage; `23` §7
asserts "the engine owns the pipeline" while deleting the field the pipeline is indexed by, and
neither its §9.6 nor GAP-15 mentions `ContextTier` — `tierOf(request.stage())` still reads the
deleted field (`MatchSession.java:113`). `derive` cannot stand in: `Deriver` reads no world (`§6.4`).

**Minimal fix.** Keep the stage as an engine-owned step plan (validation → world pass → settlement
step, each publishing its tier), or delete the SETTLE tier, `§8.1`'s row and the `RUNTIME` claim in
one change.

### 2 — A read-only `check` writes instance state, consumes a generation, and can return a `Formed`

**Scenario.** Instance `I` was `Formed` (rev 5). An AUTO check for that owner runs on a worker while
a `/cumulus` handler calls `settle` for `I` on the facade (both legal under `23` §2; `§9.3` covers
requests, not this pair — see 6). The worker's `store.publish` finds its base moved
(`MatchSession.java:183`) → `SC-CTX-0108` → `publish()` calls `instanceRecord.reemit(dirty)`
(`:338`) → the **previous `Formed`** comes back with `generation + 1` (`InstanceRecord.java:57`).
A read-only check returned a settled structure. `publish()` also runs *before* the reply fold
(`:183` before `:187`), so a check that finds nothing still publishes, and `ContextStore.publish`
appends frames forever (`ContextStore.java:64-66`): an overlay at 20 Hz adds 20 frames/s per instance.

**Violated.** `23` §2, `23` §4 ("`Formed` produced only by `settle`"), `§8.2`, `§8.3`, `§15.1`
D10/D11 (closed as "`Formed` after `Settled`" — the closure fails), P10, R-14.

**Why the stated proof fails.** `§9.4`'s re-emission rule was written for a pipeline the engine
owns; the split left the publisher inside `check`. `§8.3` also asserts one store per instance of a
family whose reader takes the store as an argument while `check` mints its own (3).

**Minimal fix.** `check` gets an attempt/outcome-scoped draft and writes no tier frame; the
`DIRTY_UNSTABLE` re-emission moves into `settle`, which alone may return a `Formed`.

### 3 — The per-instance store has no owner on the check path and no lifetime

**Scenario.** `StructureKernel.check` builds `new MatchSession(request, …)`
(`StructureKernel.java:82`), whose constructor calls `new ContextStore(request.record().id())`
(`MatchSession.java:100`) — a **fresh** store per call, so the FORM frame `§13` says `derive` reads
cannot be the frame `derive` gets: `derive(formed, store, derivers)` (`StructureKernel.java:85`)
wants the caller's store and `§9.1`'s `MatchRequest` carries none. If the host injects one store
instead, nothing ever removes it — `release`, `dropInstance` and a level unload leave an instance's
frames readable — and `ContextFrame(tier, revision, values)` carries no `mapGeneration`, so `§8.2`'s
"`mapGeneration` — dimension reload | everything" cannot reach it.

**Violated.** P10, `§8.3`, `§8.2`, `§13`, R-03, `23` §5 (release has no context-side counterpart).

**Why the stated proof fails.** P10 proves *types* ("one `publish`"), not the published thing's life
cycle; the green tests pass a store through the package-private constructor
(`FurnaceScenarioTest.java:183`), exactly the path the facade lacks.

**Minimal fix.** Put the store on `InstanceRecord` (or the request) so `check`, `settle` and `derive`
share one; drop it on `dropInstance`/unload; stamp `mapGeneration` on the frame.

### 4 — The `Plan` is an unwitnessed, unauthenticated write token

**Scenario.** A UI tab keeps the `Admitted` it got (`23` §2: "the caller decides … or register") and
calls `settle(request, plan, live)` ten minutes later. `§3.1` re-runs nothing, so
`plan.rows()` (`ClaimPlan.java:11` — no digest, blueprint, siting or binding) is written as given:
a plan from attempt A can be settled with B's request, and the CAS checks only conflicts among the
rows presented. `§3.2` step 1's "re-read the same box" has no box either — a `Bound`/`Declared`
footprint is a cell set, the plan stores no box, and `read` takes a `Cuboid` (`ClaimLedger.java:10`)
that filters the whole row list (`InMemoryClaimLedger.java:66`).

**Violated.** R-11, P2, `§10.1`, `23` §6's authoritative layer; `§11.3` already solved this for
remedies with `PlanDigest`.

**Why the stated proof fails.** P2 is totality over the rows *presented*; pre-`23` the rows were made
by the writing call, so provenance was structural. `23` §9.5 names "split into validate and write"
but §3.1 validates only the ledger.

**Minimal fix.** Give `Plan` `(blueprint, siting, snapshot id, box, rows, ledgerRevision)` and
re-resolve the footprint at settle, or refuse a plan whose digest does not match the request.

### 5 — The five-term gate cannot certify a settle that is not the attempt it gates

**Scenario.** Term 2 is "`request` — a newer submit took the slot" (`11` §4.6; `SnapshotInfo` answers
`SUPERSEDED` on a ticket difference) and the slot is `(world, owner)` (`§9.3`, `16` §4.10), shared by
**all** of one owner's machines. Pre-`23` the settle ran inside the attempt whose ticket was current
at the gate; post-`23` the gap is "until the caller decides" (`23` §2), so with 30 auto machines a
sibling submit between check and settle gives `Dropped(REQUEST_SUPERSEDED)`, a silently lost
registration and a slot left dirty. Rebuilding the request with the current `SnapshotInfo` is the
only escape and makes terms 2 and 4 trivially true — nothing then compared the world the plan was
built on. No `Admitted`/`MatchOutcome`/`Plan` carries a `SnapshotInfo`, so a result holder cannot
reproduce the terms either way.

**Violated.** `§9.2`, `§9.3`, `11` §4.6, P8, and `23` §2/§3.1's own "look, then register" flow.

**Why the stated proof fails.** P8 was proved for one call ("after the units and before anything is
published or settled"); `§10.3` was its premise and `23` removes it without restating the gate's
subject.

**Minimal fix.** Say whose attempt the gate describes: `Plan` carries the producing `SnapshotInfo`
(or a digest) and 3a compares *that* against `live`.

### 6 — The writing call is outside the slot and outside `Spend`

**Scenario.** 400 auto pens in the nether; the last player leaves; `LevelEvent.Unload` fires and
`23` §5 requires `release(owner, siting)` per instance. Each call walks the rows (the ledger's loops
are `O(rows)`, `InMemoryClaimLedger.java:35-55`) and bumps the revision — and with the kernel's
single ledger (`StructureKernel.create(catalogs, ledger)`) that revision is shared across dimensions,
so every bump invalidates in-flight settles elsewhere and `§3.2`'s retry re-runs a full matrix
(`MatchSettler.java:60-75`). None of it is in `Spend(cells, sections)` (`§9.5`), which is also why a
caller can loop `settle` (an `O(all rows)` re-read) on the server thread. `§9.3`/`§9.4`'s slot state
is keyed to units and requests and says nothing about a call that is syntactically a `MatchRequest`
(`23` §2) yet not admitted by the slot.

**Violated.** R-08's budget claim, `§9.5`, `§9.4`, P1's scope.

**Why the stated proof fails.** `§9.5`/`§9.4` enumerate a *unit's* work; `23` adds work with no unit,
no slot and no charge. `§16.4`'s open question is the retry count, not the cost or locality of
`release`, so this is not a re-list.

**Minimal fix.** Make `settle`/`release` slot operations under the same one-in-flight rule, charge
the read by rows touched, and make unload one batch (`releaseAll(world)`).

### 7 — `release` then `settle` (a move) is not atomic; the failure state is "EXCLUSIVE owning nothing"

**Scenario.** `23` §5's third point: on a move, `release` at the old key, then `settle` at the new
Siting. The rows are gone (revision +1); the new settle conflicts with a neighbour, or is moved
twice, → `Failed(SC-CLM-0002)`. The blocks still stand, the instance still matches, and it owns
**zero** rows — the contradiction `24` §5's R2 refuses at settle time, one step later. The signal is
indistinguishable from "someone else holds the target", so the host cannot know its old region is
unprotected, and a third party may take those cells: `ClaimMatrix.conflicts` excludes only the same
`(owner, siting)` (`ClaimMatrix.java:37-40`).

**Violated.** I-10, R-11, `24` §5 R2, `§10.1` (one key per CAS is what a move is not).

**Why the stated proof fails.** P2 covers one key's CAS; a move is two keys and two CASes, which
`§10.1`'s "one CAS on `(owner, siting)`" cannot express. `23` §8's registered row (the old key is
never released automatically) is a different question from rollback.

**Minimal fix.** One move transaction: `settle` takes the previous key (or a `release` token it must
consume) and does delete-and-insert in one CAS.

### 8 — `ClaimMatrix.conflicts` ignores the owner's world, so the kernel ledger mints conflicts the adapter's cannot

**Scenario.** An overworld furnace holds `(cell(0,64,0), EXCLUSIVE, OwnerId(overworld, p), …)`; its
twin is built in the nether at its own `(0,64,0)`. `LatticeKey` carries no world and `conflicts`
compares the cell, the `(owner, siting)` self-exclusion and `excludes()` — never
`a.owner().world()` (`ClaimMatrix.java:35-45`). With the kernel's ledger (one row set for the server)
the nether machine gets `Failed(SC-CLM-0002)` and never forms; with the adapter's ledger
(`16` §4.6: one `SavedData` per dimension) both form. One predicate, two answers — and `release` is
the only member of the claim layer that reads the world, which is how we know the world was meant to
be part of claim identity.

**Violated.** P2, `16` §7 proof 7 and `§4.6` (kernel planner and `SavedClaims` call the same
function), `§10.1`, `23` §5's key.

**Registration overturned.** `19`'s open list keeps "dimension identity in a claim row (`16` OQ-4)".
The row *has* its dimension — `ClaimRow.owner()` is an `OwnerId(world, owner)` (`OwnerId.java:3`); the
gap is that the one predicate never consults it. A one-line fix, not an open design decision.

**Minimal fix.** `conflicts` returns false unless the two owners' worlds are equal; the
per-dimension partition is an optimisation, not the semantics.

### 9 — The ledger read moves into the caller's thread

**Scenario.** `23` §2 wants `check` "at any rate: auto-probing, previews, commands" and `§3.1`
step 1 runs it on the caller thread, reading the ledger over the box. The AUTO pipeline runs on a
worker (`20` §2; `16` §4.10 freezes the claim view for a worker), so an AUTO pre-check performs a
`SavedData`-backed `read(Cuboid)` off the server thread — while `23` §5 says the ledger is server
thread only and `16` §4.6/§4.10 say the server thread freezes the view and "the matcher never touches
`SavedData`". The write side is unsynchronised: `SavedData`'s dirty flag is a plain `boolean`
(`SavedData.java:18`, `setDirty` `:21`) and `16` §4.6's write is "+1, `setDirty()`".

**Violated.** `23` §2/§3.1 against `23` §5, `16` §4.6/§4.10, `11` §4.8, `16` §7's proof 5 extended to
the new read.

**Why the stated proof fails.** The frozen claim view was what kept the ledger off the workers; `23`
§1 deletes it by design and §3.1 re-introduces a read without saying who freezes it or on which
thread. The kernel cannot gate it (I-1, I-15).

**Minimal fix.** Declare the ledger read a server-thread step (the host supplies the frozen
`ClaimView`, as `16` §4.10 does), or declare `check` server-thread-only and restate `§2`'s rate as a
rate of submissions.

## 4. Checked and cleared

- `SnapshotInfo.agreesWith` matches `11` §4.6's term table exactly (`held == false` → `SUPERSEDED`;
  moved `changeCount` → `CHANGED`); 5 is about whose attempt the terms describe.
- `23` §3.3's "of two holders of 41, exactly one wins" holds: the CAS loop re-scans conflicts inside
  the transaction and `RevisionMoved(expected, actual)` exists (`InMemoryClaimLedger.java:45-56`).
- `release`'s absent-key no-op/no-bump is consistent with the revision arithmetic; the defect is its
  cost and locality (6), not the rule.
- The `(owner, siting)` self-exclusion is the only exclusion, as `§10.1` says; the one way an
  instance loses to itself is a move (7).
- A level unload between capture and settle is answered by terms 1 and 5, so I raise no "settle into
  a dead `SavedData`" defect.

## 5. Could not rule

- The frame growth of 2: no bound on frames per instance is stated in `§8` or `20` §7, but that
  section is thin, so it may be an unwritten host budget rather than a design error.
- The entity-window footprint (`24` §8's "what does an entity claim mean when the animal moves"): I
  could not turn it into a defect independent of 6; the outward-move witness is round-2 `R2-C7`'s
  question, closed in `§5.4`/`16` §4.10.
- `ANY_SUBJECT` + `Bound` + `EXCLUSIVE` still protects only the animal's footprint; read as `24` §8's
  open item, not a new defect.
