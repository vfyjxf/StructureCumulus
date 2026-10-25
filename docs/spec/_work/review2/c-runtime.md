# Round 2C — runtime, lifecycle, claims, remedies

Input: `23` (revision 2) as the only object of attack; `08`, `11`, `14`, `16`, `20`, `21`, `22` for
mechanisms; `_work/review1/runtime-data.md` for the closure claims audited in §A.

Method: every claim is false until a named type makes the failure impossible. Ranked by damage.
Ten entries; each is scenario → violated clause → why the stated proof fails → minimal fix.

---

## R2-C1 — The accept gate is neither sound nor implementable, and its retry has no snapshot

**Clause.** §9.2: "The accept gate has four terms: `levelEpoch`, `ticket`, `readStamp` of every
unit, and `lease.isOpen()`. A unit whose `readStamp` is stale is re-run alone; the attempt is not
dropped wholesale (§9.4)." §8.2: "each plan unit additionally carries a `readStamp` (the tuple of
per-section revisions it read)."

**Why it cannot be right, in either reading.** `readStamp` is either a property of the snapshot
("the revisions *it read*") or a property of the watch (`DirtyIndex.contentStamp`, which advances
on a mark). The two readings are exhaustive and each kills one half of §9.2:

- *Snapshot reading.* A snapshot is immutable; its per-section revisions never change after
  capture. So `readStamp` can never go stale, and the gate degenerates to `ticket ∧ levelEpoch ∧
  isOpen()`. §20 §5.4's fifth term, `result.dirtyForCheck == false`, is exactly the term that
  rejects a mark which arrived after capture; §9.2 dropped it (along with `snapshotId` and
  `baseDirtyEpoch`). Concrete, and it is §20 §9 X1 verbatim: a furnace's hopper drains while
  `FORM` runs; the payload mark sets `dirtyForCheck`. All four §9.2 terms hold — the ticket did
  not change, the snapshot revisions did not change, the level did not unload, the lease is open —
  so the result settles, `Formed` returns, claims settle, and `derive` publishes a `CONTEXT`
  value computed from the pre-drain inventory. §20 X1 requires "term 5 fails; no settle". §20
  proof 3 ("No stale `Formed`") is false as consolidated.
- *Watch reading.* Then a single lamp toggle in the door's section makes the door unit's stamp
  stale, and "re-run alone" has nothing to re-run against: the unit reads immutable
  `LevelSnapshot` sections, whose content is still the old revision, so the re-read stamp is the
  same stale value. Iterating the unit without a new capture is a no-op that fails the gate
  forever; iterating it *with* a fresh section is a blend of two capture revisions, which §20 §4.3
  forbids by name ("`snapshot.section(k) -> store.get(k)` is forbidden, because a section could
  advance mid-check and blend revisions") and §20 proof 2 relies on. So the retry mechanism exists
  in neither reading.

**Scenario (walked, both readings).** Door bay `1×2×1` at `(5,64,5)..(5,66,5)`; a redstone lamp at
`(6,64,5)` — same 16³ section (`cy=4`, `sx=0`, `sz=0`). The lamp toggles one block every 2 ticks
(100 ms); the door's `FORM` check takes 40 ms. Dispatch at t=0, check completes at t=40; a toggle
lands at t=50, i.e. between capture and gate. Snapshot reading: the gate passes, the door is
`Formed` on a lamp state it did not read — harmless here, and equally harmless-looking in the X1
case where it is not. Watch reading: the stamp is stale, the retry reads the same snapshot, the
gate fails again, `maxDiscards` is reached, and the attempt settles `Undecided(DIRTY_UNSTABLE)`.
The door's own two cells were never written and the door is correct; the structure becomes
undetectable because a neighbour shares its section. Detection granularity is the *section*, while
the thing that matters is `decisionCells ∪ reads.cells`, whose per-cell revisions already exist in
`DirtyIndex`. §8.2's sentence "The accept gate compares the unit's `readStamp`, not the whole
watch (§9.3)" cites the coalescing section, not the gate.

**And the settled `Undecided(DIRTY_UNSTABLE)` keeps a `Formed` that is no longer valid.** §9.4:
"the previous `Formed` (if any) stays current." `Formed` carries `(InstanceId, Siting, MatchFacts,
ClaimSet, SnapshotLease, FindingBundle)` (§10.4) — no `generation`, no `contextRevision`, no
`ledgerRevision`. So:

- its lease was already closed by `InstanceRecord` "until `derive` completes, then closes it"
  (§5.5), so `StructureContext.at(previousFormed)` — the only stated way to read promoted values —
  throws → `Undecided(LEASE_CLOSED)` for the instance the system calls current;
- its `ClaimSet` may have been replaced by another owner's `(owner, siting)` settle (§10.1) while
  it stayed "current"; nothing in the type lets a consumer notice (§R2-C2);
- its `MatchFacts` describe a snapshot that the gate has just declared unstable.

A remedy provider handed this `Formed` (§11.3) sizes an `EditPlan` from stale facts and validates
it against stale claims.

**Violates** §20 §5.4 (gate terms), §20 §9 X1, §20 proof 3, R-08, §9.4's "it cannot spin" (it does
not spin; it never forms), I-10 (a settled claim set served after replacement).

**Minimal fix.** Keep all five §20 terms; `readStamp` may *narrow* a drop to one unit but may never
replace `dirtyForCheck`. Define re-run as a *new attempt over a fresh capture* that reuses prior
verdicts for units whose `decisionCells ∪ reads.cells` cell stamps are unchanged — partial reuse
inside an attempt, never partial recursion inside a snapshot. Stamp per unit's cells, not per
section. Give `Formed` `(generation, contextRevision, ledgerRevision)` and make "stays current" mean
re-emitting `Formed` with `generation + 1` and the same facts, so consumers can compare.

---

## R2-C2 — `settle` has no representation for a moved revision, and it is placed on the wrong thread

**Clause.** §10.1: `ClaimVerdict permits Settled, ClaimConflict`; "`settle` replaces all rows of
`(owner, siting)` in one CAS. A conflict returns the incumbent rows, and the same call mints
`SC-CLM-0002`." `SettleRequest(owner, siting, rows, expectedRevision)`. §10.3: "At `SETTLE`, the
matcher calls `claimWriter.settle(...)` before returning."

**Scenario 1 — two overlapping furnaces, one tick.** A and B both reach `SETTLE` for
`mymod:huge_furnace` with overlapping interior cells. Both read revision 10; neither is in the
ledger yet, so neither `settle` sees an incumbent. A's CAS commits, revision 11. B's
`settle(..., 10)` fails the CAS. `ClaimVerdict` has no case for that: B returns either `Settled`
(nothing was written — a lie the caller then persists as a claim set) or `ClaimConflict` (a
conflict minted from A's rows, which is *correct here by luck*, because the re-read would find A).
Substitute two **disjoint** furnaces settling in the same tick — the convoy case — and
`ClaimConflict` becomes a false `SC-CLM-0002` naming rows that intersect none of B's cells.
`Failed` is not a soft answer: §11 §4.4, restated in §14's dry run, says "`Failed` licenses a
repair that changes the world". A valid, non-colliding structure is told it collides and is
eligible for an auto-repair. §16 §4.10 papers over this with "`RevisionMoved` is
`Undecided(LEDGER_UNAVAILABLE)`, no retry" — a fourth outcome that §10.1's `ClaimVerdict` cannot
express, and "no retry" makes a transient convoy permanent.

Consequently the §13 walkthrough's advertised outcome — "one settles `EXCLUSIVE`, the other gets
`Failed(SC-CLM-0002)` with the incumbent rows" — is unreachable for the overlapping pair (the
loser sees a CAS failure, not the incumbent rows) and is *wrong* for the disjoint pair.

**Scenario 2 — a third `RESERVED` row with an expiry.** A stale `RESERVED` row on the hatch expires
at tick `T` (host policy, 08 OQ-6, no kernel clock). A build's settle at `T-1` reads rev 42, mints
`SC-CLM-0002`, `Failed`; the identical attempt at `T+1` reads rev 43 (the sweep bumped it) and
settles. Same world, same placement, opposite verdicts, decided by a TTL no kernel type sees; and
every in-flight attempt straddling the sweep fails its CAS with an unrepresentable outcome.

**Scenario 3 — the thread.** `MatchRequest` carries `@Nullable ClaimLedgerWriter claimWriter`
(§9.1) and §10.3 has the matcher call it. The matcher runs on a worker (§20 §2: "match / derive /
solve / validate | worker"), while `SavedClaims` is `SavedData` and §20 §2's table puts "settle |
server" and "`SavedData`" on the server thread. §08 §4.8 and `11` §1 both state the kernel writes
nothing, and I-4 freezes the kernel's inputs. A worker-thread `settle` mutates `SavedData` and calls
`setDirty()` off-thread. The consolidation bought "settle inside the match" (round-1 D10) by
deleting the very separation that made the write safe.

**Does `Formed` carry enough to detect replacement?** No. `ClaimSet` is named in §10.4 and never
declared (§10.1 declares `ClaimVerdict`); `Formed` carries no `ledgerRevision`; `expectedRevision`
is consumed by the call and not returned. A `Formed` held for 30 s cannot tell that its rows were
replaced, so any consumer (`commit`, a remedy's `ClaimView`, a UI showing ownership) reads a claim
set that may already be another owner's.

**Violates** R-11, I-10, P2 ("claims are total and conflict-checked"), P3, §20 §2, 08 §4.8.

**Minimal fix.** `ClaimVerdict permits Settled, ClaimConflict, RevisionMoved(long expected, long
actual)`; on `RevisionMoved` re-read and re-check *inside the write transaction* (bounded, one
retry), so the disjoint case settles and the overlapping case returns real incumbents; only a
second movement becomes `Undecided` with a retry token. Move the write off the matcher: the kernel
returns the `SettleRequest` it would issue, a server-thread `MatchSettler` chained by the scheduler
performs it, and `Formed` is produced only after `Settled` — P3 then rests on a call graph, not on a
hope. `Formed` carries `ClaimSet(rows, revision)`.

---

## R2-C3 — `ClaimScope.Occupied` is match-time, `SC-BAY-0108` is DESIGN-time, so the default scope has no footprint check at all

**Clause.** §10.2: "`Occupied()` // union of bound footprints"; "The claim footprint of a match is
the union of the bays' scopes. A bay with a non-`Audit` scope whose check reads outside its scope is
`SC-BAY-0108` at DESIGN." §6.5 defines `decisionCells` for `block`, `allCells`, `door`, `state`
only.

**Scenario.** A pen bay declares `focus: entity(cow)`, `count >= 1`, `Coverage.SUBJECT`, and the
default `ClaimScope.Occupied`. Its `ReadScope` is the set of interrogated cells, the `focus: entity`
filter's `EntityWindow` inflated by `MAX_ENTITY_SPAN`, and the `reads()` of the count expectation —
i.e. the whole 7×4×7 pen. Its footprint at settle is the bound cow's footprint: one cell. Two such
pens whose sitings overlap by 6×3×6 each claim one cell, those cells differ, the matrix sees no pair,
both `Formed`, and `/cumulus build` fills both pens into the same volume. `SC-BAY-0108` cannot fire:
at DESIGN there are no bindings, so `Occupied`'s scope is the empty set, and the check is vacuous
for every `Occupied` bay — which is the default. `SC-BAY-0109` checks the opposite direction
(footprint outside scope) and is equally static. `SC-CLM-0104` — the uncovered-bounds hole — is a
WARNING by decision (§10.2, §16 Q6), so the hole is *legal*.

This is round-1 D4 with the names changed: the judged cells are still unclaimed, now because the
default scope is computed from bindings instead of from what the checks read.

**Second hole, same clause.** `decisionCells` is undefined for `entity`, `occupant` and `nested`
(§6.5 lists four of the eight `Focus` kinds), so even `Coverage.CUBIC`/`SC-BAY-0107` has no union to
compare for those kinds; a bay with one such focus and `Coverage.CUBIC` passes vacuously.

**Violates** R-11, I-10, round-1 D4, §6.5.

**Minimal fix.** `Occupied` claims `bay.cells ∪ boundFootprints` (the union, never the bindings
alone); `SC-BAY-0108` becomes a SETTLE test: for every non-`Audit` scope,
`claims.rows ⊇ decisionCells ∪ reads.cells`, ERROR, minted with the same authority as
`SC-CLM-0002`; `decisionCells` is mandatory for all eight focus kinds (an `entity` focus decides
the cells it read). Keep the DESIGN warning as a preview, not as the gate.

---

## R2-C4 — The lease has three owners, one of whom cannot close it, and `retain()` is unbudgeted

**Clause.** §5.5: "`Formed` carries it and owns one reference"; "`retain()` is the only way a second
holder (a UI tab, a remedy) keeps the view; every holder closes; `pinnedBytes` counts live
references, not captures"; "`StructureContext.at(formed)` retains the lease for the duration of the
call and fails closed … when the lease is already closed"; "`InstanceRecord` holds one reference
until `derive` completes, then closes it. A forced close after `maxLeaseTicks` is a host policy and
emits `SC-MC-0501`." §10.4: `Formed … implements AutoCloseable`; `Admitted` does not.

**Walk: a UI tab retains a `Formed` for 30 s while the structure re-forms three times.**

| t | event | refs on L1 |
| --- | --- | --- |
| 0 | `FORM` settles, `Formed` F1 owns L1 | 1 |
| 1 | the tab (`onTerminal` consumer) calls `retain()` | 2 |
| 2 | `derive(F1)` finishes, `InstanceRecord` closes its reference | 1 (tab) |
| 3 | block change → re-form → capture L2, F2; second tab retains | L1=1, L2=2 |
| 4 | `maxLeaseTicks` reached for L1 | **?** |

At t=4 the forced close has no reference it may close: the record's reference is gone, and the only
live one belongs to the tab. If `close()` closes the *lease*, the tab's `view()` now reads a released
snapshot (nothing in `LevelSnapshot`/`WorldView` checks the lease — the guard type is not the one
handed around, so `derive`, `FocusContext`, `PlanValidator` and `RemedyContext` all receive a bare
`LevelSnapshot`). If only an owner's reference can be force-closed, there is no owner, so no forced
close happens and L1's bytes are pinned for as long as the tab lives. §5.5's two sentences cannot
both hold; §16 Q1 ("Is a per-owner pin cap with forced close enough, or does `Formed` need a
copy-on-retain?") admits the mechanism is undecided, so P4 is owed, not proved.

**Answer to "is `pinnedBytes` bounded?"** No. `retain()` neither reserves nor refuses: §5.5 gives no
budget and I-14 leaves no refusal case (`SnapshotLease retain()` cannot fail). Worse, the same
counter serves three consumers — lease references (§5.5), the `AttemptCache`'s LRU
("entries are evicted LRU under `pinnedBytes`", §9.6), and pre-capture reservation ("Scheduler-wide
`pinnedBytes` is reserved before capture; refusal is `DEFERRED`", §20 §7). So a viewer holding three
tab leases inflates the ledger that capture reserves against and can push a 200-furnace spawn's next
capture to `DEFERRED`. A read-only consumer can starve detection.

**A remedy provider that retains during `repair`.** §5.5 names remedies as a legitimate second
holder; `RemedyProvider`s live in a catalog "frozen at `apply`" (§6.7) and have no `close`, no
unload hook and no budget. A provider that caches `ctx.lease().retain()` in a field pins that
snapshot for the life of the server; a datapack reload does not release it.

**What closes the lease when the caller drops the result without closing?** For `Formed`, nothing
unless the consumer uses try-with-resources *and* the `InstanceRecord` does not also hold a
reference; `Formed.close()` has no stated effect and would close the record's reference if it did.
For `Admitted`, the caller *cannot* close it in a `try`: `Admitted` is not `AutoCloseable`, so
`try (var r = kernel.match(...))` does not compile, and the only path is
`instanceof Admitted a -> a.lease().close()`, which every call site must remember. A `/cumulus
preview` that receives `Admitted(PROBE)` and renders it leaks its capture.

**Also:** `SC-MC-0501` is already the capture-over-cap code in §20 §4.2
(`Refused(NOT_CAPTURED, SC-MC-0501)`). Two unrelated events, one code, and I-7 says machine consumers
read codes.

**Violates** P4, R-14 (whose "residual risk" column already reads "lease lifetime is a host
obligation"), §20 proof 7.

**Minimal fix.** One owner: `InstanceRecord` holds the only lease; `Formed` holds a `retain()`ed
child handle whose `close()` releases only that handle; `Admitted` carries no lease (the scheduler
holds it) or implements `AutoCloseable` identically. `retain()` takes a `PinBudget` and returns a
sealed `Pinned | PinRefused` (I-14-compliant). Delete the forced close; refuse the `retain()` when
the per-owner cap is reached. Split `pinnedBytes` into `retainedBytes` (leases, never evicted) and
`cacheBytes` (LRU). `at(formed)` reads frames only, never the lease. Give remedies a `ContextView`,
not a lease. Give the forced/refused paths distinct codes.

---

## R2-C5 — `PROBE → FORM` transfer leaves the lease owned by nobody when the ticket coalesces the submit away

**Clause.** §5.5: "`PROBE → FORM` transfers the lease with the submit; if the submit cannot take it,
FORM re-captures under the same ticket."

**Scenario.** t0: AUTO `PROBE` returns `Admitted(PROBE, lease=L)`; the scheduler's rule (§20 §3.1)
is that a passing probe "submits `FORM` as a separate request". The caller hands L to the
`MatchRequest` and treats the handoff as a transfer. t1 (one tick): a lamp toggles, W3 marks the
slot, and a newer AUTO submit coalesces. §20 §3.1 gives the coalescer two moves — "marks the slot
dirty with the new request as `pending`, or, if the current task is still `QUEUED`, replaces its
pending request". In the replace case the `MatchRequest` holding L is dropped. Nothing closes it:
`MatchRequest` is a record with no `close`, and `MatchScheduler` has no step that closes a request
(`submit`, `markDirty`, `dropInstance`, `levelUnloaded`, `tick`, `close` — none takes a
`MatchRequest`). `dropInstance` releases *the watch's* view, not a pending request's lease. So one
reference leaks per coalesced FORM, i.e. up to one per 2 ticks on a churning structure. The caller
no longer holds it ("transferred"), `Admitted` is garbage, and no `InstanceRecord` exists yet. The
lease is owned by nobody and never closed.

**Between `Admitted` and the request, ownership is ambiguous, not transferred.** `Admitted` is an
immutable record that still exposes `lease()`, and the request holds the same object. Both sides are
readable and closable, so the double-close failure is reachable: the caller closes the `Admitted`
after a successful submit (a reasonable reading of "transfer"), the FORM match then runs on a
released view, and §9.2's `lease.isOpen()` fails at the gate — `DROPPED(STALE_VERSION)`, the slot
stays dirty, and the re-capture loop starts. That is the livelock §9.4 was added to remove,
reintroduced by the handoff.

**Violates** P4, §20 proof 7 ("one owner and one release point"), round-1 D2 (claimed fixed).

**Minimal fix.** Make the handoff a move: `Admitted.releaseLease()` returns a handle and invalidates
the field (`Admitted` becomes non-readable for the lease, or `lease()` returns
`@Nullable` and clears). `MatchRequest` owns a `retain()`ed handle that the scheduler closes in a
`finally` on every non-dispatch path (supersede, defer, cancel, stop), and the worker closes it after
the gate — one release point per path, asserted by a `Spend`/`lease` counter in `metrics()`.

---

## R2-C6 — `maxDiscards` is per request, so a coalescing churn never accumulates discards; the permit arithmetic contradicts the reserved permit

**Clause.** §9.4: "After `policy.maxDiscards` consecutive stale drops of the same unit, the attempt
settles `Undecided(DIRTY_UNSTABLE)`"; "**Permits.** `AUTO` gets `max(1, threads - 1)` permits, so a
single worker still runs it." §20 §3.1: "one permit is reserved for `INTERACTIVE`".

**Scenario 1 — the bound is unreachable.** `maxDiscards` is a `MatchPolicy` field (§9.1), and
`MatchPolicy` is a field of `MatchRequest`. Every retry after a coalesce is a *new* `MatchRequest`
(§20 §3.1: the newer submit "replaces its pending request"), built by the same caller with the same
policy. The counter that §9.4 describes ("of the same unit") therefore has no holder: nothing in
§9.1 or §9.4 keeps it across requests, and the only available container resets it. So a door with a
toggling lamp beside it — one stale drop per attempt, a new attempt per tick — never reaches
`maxDiscards` and never settles `DIRTY_UNSTABLE`. §9.4's "A structure that churns can be undecided;
it cannot spin" is false for exactly the churn path §9.3 created: coalescing is the *only* way a
newer request wins ("`cancel()` is cooperative and does not make a newer request win; the
coalescer does"), and coalescing is what resets the counter.

**Scenario 2 — permits at one thread.** With `threads = 1`, `max(1, 1-1) = 1`: AUTO holds the only
permit, and the "one permit is reserved for `INTERACTIVE`" rule of §20 §3.1 is unsatisfiable. The
sentence "so a single worker still runs it" is true only by taking the reservation away. Conversely
with `threads = 2` AUTO gets 1 — meaning the config with *no* spare capacity keeps AUTO fully
permitted and the config with one spare gives it half.

**Violates** P8, R-08, §9.4, §20 §3.1.

**Minimal fix.** Put both counters in the slot, not the request:
`Map<NamespacedId unit, int> discards` and `long generation`, reset on a successful settle and on
`dropInstance`; a coalesced submit inherits them. Permits: define `autoPermits = threads >= 2 ?
threads - 1 : 0` and state that AUTO is disabled (not starved) on a one-worker host, or reserve no
permit and give `Priority` a strict-priority queue with a per-owner in-flight cap of 1 (which the
slot already enforces).

---

## R2-C7 — Entity movement out of the window has no witness, and the per-dispatch re-query is unbilled server-thread work

**Clause.** §5.4: "`ReadScope.entityWindows` is inflated by `MAX_ENTITY_SPAN` on every axis";
"Movement marks nothing; dispatch re-queries each window and compares `Set<EntityKey>` and
footprints with the captured set. A difference is a dirty entity fact even though no block changed."
§20 §5.1: "entity move | **no per-tick hook**".

**Bound the cost.** `MAX_ENTITY_SPAN` has **no value anywhere in the spec** — it appears only as a
name (§20 §4.2, §5.4, §16) and §16 Q5 asks what should bound it. Take the smallest value that admits
any entity that can touch the pen (`s=1`): a 7×4×7 pen yields a 9×6×9 = 486-cell window, touching up
to 8 sections (`s=2` → 11×8×11 = 968). Each dispatch performs one `getEntitiesOfClass` over that
AABB, on the **server thread** (§5.4 "dispatch re-queries"; §20 §2 puts world reads on the server).
200 furnaces × 1 pen bay = 200 queries per dispatch round, each touching up to 8 section entity lists
and allocating a result list; 200 instances × 4 entity bays = 800, i.e. up to 6400 section scans per
tick. None of it is billed: §9.5 says "No other work is billed. `Spend(checks, cells, sections)` is
the whole ledger" — a window re-query is neither a capture nor a unit dispatch, so
`autoSectionsPerTick` cannot see it and a plan with `Spend.checks = 1` can cost 8 section scans. The
cost also scales with `bays × instances`, not with the plan, so the DESIGN-time budget (§9.1
`Budget`) cannot express it.

**Boundary move, and the witness.** Cow at the pen's edge, anchor on the last cell inside the
inflated window; it moves one block outward, through an existing hole. Three facts compose into a
silent failure:

1. `EntityKey(UUID, type, dataDigest)` cannot change on a move: `dataDigest` is `ENTITY_NBT`, which
   §16 §4.3 defines as `saveWithoutId` **minus `Pos`/`Motion`/`Rotation`** precisely so that "one cow
   captured twice compares equal". Detection rests entirely on the footprint diff.
2. The footprint diff runs only inside a re-query, and a re-query runs only at a dispatch.
3. No dispatch is scheduled: §20 §5.1's hooks are block state, BE payload, entity **join/leave**, and
   chunk load/unload. Leaving a read *window* is not a world leave, and a move produces no mark. The
   `DirtyIndex` has no window→instance map ("Three `long -> set` maps: section, payload position,
   entity footprint" — keyed by entity, not by window), so nothing turns "the cow is now outside
   every window" into a mark.

So the cow is out, the `Formed` stands, `entity: cow, count >= 1` stays satisfied, and any
`RUNTIME` value derived from occupancy keeps the departed cow. The "aged revalidation sweep" (§20
§5.1) is the only backstop and is explicitly "not claimed as exact", with no period, budget or scope
→ not a witness.

**Answer:** no witness for the outward move. Round-1 D3's fix ("dispatch re-queries") is a *pull*
with no *trigger*.

**Violates** R-01, §20 §9 X1–X2's spirit (a mutation with no dirty witness), §5.4.

**Minimal fix.** Register the inflated window in `DirtyIndex` as a fourth map
`window → set<InstanceRef>` and run a per-tick occupancy diff *inside the window only* (compare the
captured `cell → entity` set with the live one; cost `O(entities in window)`, not `O(cells)`), or
re-admit the `setPos` hook for watched entities. Charge the window query as `sections` in `Spend`
(`sectionsTouched(window)` computed at DESIGN), fold windows per instance, and fix
`MAX_ENTITY_SPAN` per `OccupantKind` as a constant.

---

## R2-C8 — A remedy is validated against the pre-repair world it is about to change, and `begin` cannot re-check claims

**Clause.** §11.3: "Providers read promoted values through `context`"; "`PlanValidator` receives the
same context, so a repair and its validation read the same values." §14 P3: the validate/apply gap
is "closed by `begin` re-reading, then rollback". §14 §4.5: "the host re-captures and re-matches
after an apply".

**Which facts does `PlanValidator` see?** Pre-repair, by construction: one `RemedyContext` is built
once (lease, claims, facts, context) and both `solve` and `validate` take it. A repair that changes
the world therefore invalidates the context it was sized from, and nothing re-derives it between
steps or after commit.

**Scenario.** `CAVITY_VOLUME = 150` promoted from a 5×4×5 interior, supplied by a **section**
computation whose input region is the interior bay (§12.3's own compact example has `volume` over
`region: interior`). The provider places a floor sized from the promoted value: `Place(Cuboid 5×1×5)`
inside the interior. The dry run validates against the pre-state (air there, `0302` passes, `0305`
no-op absent, `0303` no claim), the apply commits, and the interior is now 5×3×5 = 75. The next match
reports the volume expectation's mismatch → a new finding → a repair sized from 75 → and if the
volume computation itself is what defines "correct", the two states alternate forever. The re-match
required by §14 §4.5 reports the *expectation's* finding, so the loop looks like world damage, never
like a plan defect; no `SC-REM-03xx` code exists for "this plan changes a key it was sized from".
`EditPlan` is a plain fold of self-contained edits (§14 §4.3), so the post-state is computable — the
design simply never asks for it.

**`begin` cannot re-check claims.** `EditExecutor.begin(WorldView view, Siting siting)` (§14 §3.5)
takes no `ClaimLedger` and no `ValidationReport`. §14 §4.7 row 6 checks claims in the **dry run**
against `ctx.claims()`; §16 §4.7's `begin` gate list is thread/view/border/unloaded/gate/volume — no
claims. Scenario: A's repair is validated at t=0 with the hatch cell free; at t=1 a furnace settles
`EXCLUSIVE` on it (a `SETTLE` at another siting); at t=2 A's `Place` writes it. §14 §4.7's
"`EXCLUSIVE` by another `ClaimKey` ⇒ `0303` ⇒ not applicable ⇒ never applied" is false at apply
time; the world write happens and I-10 is violated at the write path, by the same split P3 relies
on. `rollback` does not help: the foreign owner's claim is not a failed step.

**Violates** R-12, I-10, §14 P3, §11.3.

**Minimal fix.** Add a dry-run row 9 (`0309`): fold the plan over the captured view, re-run the
`FACTS`/`CONTEXT` keys any provider read, and refuse when one changes. Extend `begin` to
`begin(WorldView, Siting, ClaimView, long ledgerRevision)` and refuse on a revision mismatch
(`0303`) or on a stale `ValidationReport` (a plan digest). Return a fresh `RemedyContext` with each
`ApplyReport` so a second provider round cannot reuse pre-state.

---

## R2-C9 — `admission` is author-declared with no lower bound, and P1 fails on its own third `Spend` leg

**Clause.** §9.5: "`admission` is the only per-unit number. It is charged when the unit is
dispatched, cached or not, and it is an upper bound on the work the unit may do. Over-charging is
allowed; under-charging is not." "Consequence: a warm run and a cold run of the same plan have the
same `Spend`, so a cache can never turn `Undecided(BUDGET_EXHAUSTED)` into `Formed`."

**Scenario 1 — the upper bound is an honour system.** `Check.admission` is a constructor component
(§6.2) and `ExpectationMeta.admission` is a field (§6.3); the compact profile (§12.3) does not even
carry one, so the resolver supplies a default. Nothing compares it with the work the unit can do.
Declare `admission: 1` on `focus: allCells` over the 5×4×5 interior (100 cells) or a 7×6×7 shell
(294): `Spend.checks = 1`, real work 100–294 occupant reads, and the unit is *scheduled earlier*
because §7.1 orders ties by `admission` ascending. The per-tick AUTO budget (§9.4
`autoChecksPerTick`) admits N such units, i.e. N×294 reads, so a datapack can multiply tick cost by
~300 inside its budget. "Under-charging is not [allowed]" has no check, no code, and no resolver
rule.

**Scenario 2 — budget scope.** `Budget` is a field of `MatchRequest` (§9.1). Every retry is a new
request with a full budget, so the budget is **per attempt**, never per structure lifetime. `Spend`
is reported per result; the scheduler's only aggregate is `metrics()`'s `queued/admitted/discarded`,
which is not a budget. Combined with R2-C6 (`maxDiscards` unreachable) a churning structure consumes
`autoChecksPerTick` every tick and never terminates; a quiet structure's single submit waits behind
it, and no fairness rule exists — §9.4 claims "starvation is observable", not prevented. So yes: a
churn loop exhausts the shared per-tick budget and a quiet structure is starved, and the churn costs
nothing that any ledger records.

**Scenario 3 — P1's third leg is the cache.** `Spend(checks, cells, sections)`. A furnace's `FORM`
closure touches 18 sections. With `Budget(4096, 512, 16)`: cold, the 18 captures are charged and the
attempt is refused → `Undecided(BUDGET_EXHAUSTED)`. Warm, with identical content, the digest
comparison finds equality and no copy occurs; nothing says a no-op refresh is charged (§9.5's
"refreshes charge the same" does not settle whether "the same" applies when zero sections were
refreshed, and §20 §5.3 says a no-op refresh "yields zero checks" without saying zero sections) →
0 charged → the same plan `Formed`. P1's construction argument covers the `checks` leg only, while
its own conclusion is about `Spend` as a whole, and the third leg is literally the cache.

**Violates** P1, §9.5, R-08's budget claim.

**Minimal fix.** Compute admission at DESIGN from `decisionCells().volume() +
reads().cells.volume() + Σ sectionsTouched(entityWindows)`; an authored value may only raise it
(`SC-CHK-0106` when lower). Move `Budget` into the slot and charge it per *generation*, resetting
only on a successful settle, so a churn loop is a single depleting budget. Charge `sections` at
plan-closure size whether or not a copy happens, and charge a discard (a `retry` term) so a stale
drop is not free.

---

## R2-C10 — `derive` publishes into a newer revision, so a superseded `Formed` overwrites the current context and `RuntimeState`

**Clause.** §8.3: `void publish(ContextDraft draft); // the only writer`; §8.1: "a reader at tier `t`
sees the newest frame at or below `t`". §13: "`derive` reads the FORM frame, publishes `capacity =
600` at SETTLE, persists `RuntimeState`."

**Scenario.** F1 settles at `contextRevision` 1. `derive(F1)` is dispatched on a worker (§20 §2).
At t+1 the structure re-forms; F2 settles and `derive(F2)` publishes SETTLE revision 2 with
`capacity = 420`. The slow `derive(F1)` then publishes revision 3 with `capacity = 600`. "Newest
frame at or below `t`" now serves 600; `RuntimeState` persists 600; the instance the host tracks is
F2. P10 ("Promotion has one writer") is a statement about *types* — there is one `publish` — not
about order: a second `derive` is a second write, `ContextDraft` carries no expected revision, and
`Formed` carries no `contextRevision`/`generation` (§10.4), so `publish` cannot know it is stale even
in principle. `SC-CTX-0107` covers two values for one key *in one frame*, not two derives of one key
in two frames.

**Violates** P10, R-03 (`RUNTIME`/context is the structure's context), §8.2's
`instance.generation` rule ("`contextRevision` changes → invalidates").

**Minimal fix.** `Formed` carries the `contextRevision` it settled at; `ContextDraft` carries
`baseRevision`; `publish` refuses a draft whose base is not current (`SC-CTX-0108`); the scheduler
drops a derive result whose `InstanceId` generation is superseded before publish, and `derive` is
serialized per instance.

---

## A. `§15.3` closure audit (verified, not trusted)

| # | Claim | Verdict | Where it fails |
| --- | --- | --- | --- |
| D1 | one predicate, `(owner, siting)`, no adapter filter | **Partly** | §16 §4.6 (the only implementation) still settles per `ClaimKey` with `Settlement.Blocked` and its own "any foreign `EXCLUSIVE`" test, so five bays are five CAS operations and `(owner,siting)` atomicity is unexpressible; `§15.4` defers `16`. Also `11` §4.5 still says two `RESERVED` under different owners conflict, opposite to §10.1's matrix. → R2-C2 |
| D2 | closeable lease, transfer or re-capture, `pinnedBytes` per reference | **Not closed** | `Admitted` not closeable; `Formed`/`InstanceRecord`/`at(formed)` claim three owners; `retain()` unbudgeted; forced close cannot reach a third-party reference; one `pinnedBytes` for leases + cache + capture. → R2-C4, R2-C5 |
| D3 | `EntityKey`, inflated windows, spatial re-query, `ChangeSet.entities` | **Not closed** | the re-query is a pull with no trigger; `dataDigest` excludes `Pos` by design; outward movement marks nothing; `MAX_ENTITY_SPAN` has no value. → R2-C7 |
| D4 | `ClaimScope`, DESIGN checks, `SC-CLM-0104` warning | **Not closed** | `Occupied` (the default) resolves at match time while `SC-BAY-0108` is DESIGN-time, so the check is vacuous; the residue is a WARNING by decision. → R2-C3 |
| D5 | bounded discards, terminal `DEFERRED`, `max(1, threads-1)`, per-tick budget | **Partly** | `DEFERRED` terminal is real; the discard bound has no holder across coalesced requests; the permit formula contradicts the reserved `INTERACTIVE` permit at `threads=1`. → R2-C6 |
| D6 | admission billing, `contentDigest`, `MatchPolicy.parallel`, one LRU | **Partly** | the `checks` leg is cache-independent, the `sections` leg is not, so P1's conclusion over `Spend` is false; admission has no floor. → R2-C9 |
| D7 | one `Spec`, `SpecResolver` in `apply`, schema 2, subject per check | **Partly** | `23` §12.2 has it; `21` §3.1's compact root table still has no `checks`/`computations`/`state` fields and still requires `schemaVersion: 1`, so the furnace in §12.3 is `SC-DOC-0104` under the file that owns compact authoring. `§15.4` defers `21`. |
| D8 | named bays whole, content-derived synthetic ids | **Partly** | `23` §12.4 fixes it; `21` §4 still prescribes "a named bay keeps its id on the cuboid containing its declared origin" and `shell`/`shell#1` counter ids — the exact split and the exact instability round 1 attacked. |
| D9 | `IssueSink`, `extensions["cumulus:capture"]`, `container`, refuse instead of empty BE | **Partly** | `23` §12.5 has it; `21` §7 still declares `DataView capture(CaptureRequest)` with no issues channel, and §6 still prescribes "a TODO comment field". |
| D10 | `facts` + `context` + lease on `RemedyContext` | **Partly** | `23` §11.3 has it; `14` §3.2 — the file that owns `RemedyContext` and `PlanValidator` — still declares `(view, blueprint, siting, ledger, options)`. The mechanism therefore does not exist where `P3` is proved. → R2-C8 |

## B. `§14` P-verdicts (the five in scope)

| P | Claim | Verdict | Refuted by |
| --- | --- | --- | --- |
| P1 | warm cannot form where cold was exhausted | **False** | R2-C9: the `sections` leg is the cache; a warm no-op refresh charges 0 where cold charges the closure |
| P2 | claims total and conflict-checked | **False** | R2-C2: `ClaimVerdict` cannot express `RevisionMoved`, so the disjoint-convoy case mints a false `SC-CLM-0002` and the overlapping case cannot produce the documented incumbent rows; the write is on a worker thread |
| P3 | a `Formed` is committable | **False** | R2-C2 (no `ledgerRevision`; `ClaimSet` undeclared), R2-C1(d) a `Formed` stays "current" while its rows are replaced, R2-C8 `begin` cannot re-check claims |
| P4 | a snapshot is released exactly once | **False** | R2-C4 (three owners, one uncloseable, `retain()` unbudgeted, forced close vs "every holder closes"), R2-C5 (coalesced-away request leaks its lease) |
| P8 | the gate is live | **False in both directions** | R2-C1: unsound under the snapshot reading (X1 settles), unimplementable under the watch reading (retry has no fresh snapshot); R2-C6: the discard bound resets with every coalesced request |
