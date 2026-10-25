# Round 1C — runtime, authoring, claims, remedies

Input: `23`, `08`, `10`, `16`, `20`, `21`; `07`, `11`, `14`, `22`, `01` for anchors. Claims are assumed
false until proved.

## D1 — The host's claim write is a weaker conflict predicate

**Scenario (shared hatch).** A settles `mymod:hatch` as `SHARED`; B's `SETTLE` froze `claimsIn` just
before and returns `Formed`. `SavedClaims.settle` applies `16` §4.6 — "any foreign `EXCLUSIVE` on a
claimed cell → `Blocked`" — sees a `SHARED` incumbent, blocks nothing, and writes B's `EXCLUSIVE` onto
A's cell. `08` §4.2 makes EXCL/SHARED a conflict either way.
**Violates** I-10, `08` P2, R-11.
**Proof fails.** P2 argues from the ledger API and matrix totality, both kernel-side; the writer is a
second implementation in the adapter and no file states its predicate. `Blocked` returns incumbents,
not `ClaimConflict`, so no `SC-CLM-0002` can be re-minted. Its scope is one `ClaimKey`, while `08` §4.7
replaces `(owner, siting)` — and D7 makes the bay-id set unstable.
**Fix.** One `ClaimConflict` function used by both; scope `(owner, siting)`.

## D2 — `at(formed)`, PROBE→FORM and "no capture pinned" cannot all hold

**Scenario (huge furnace, AUTO).** `20` §3.1: a passing probe "submits `FORM` as a separate request",
`at(formed)` "keeps the same snapshot as the prior `Formed`"; `11` §5 chains it. `20` §4.3:
"afterwards the host … drops the view. No formed structure pins a capture." Either the probe's view
survives into the submit — a pinned capture with no owner, release point or reservation — or
`at(formed)` cannot build a request; and every `onTerminal` consumer (§3.2) receives it, so a UI
tab pins sections after `pinnedBytes` was released.
**Violates** `20` §7 "one owner and one release point"; R-14.
**Proof fails.** Proof 7 is an ownership argument with no owner type: `WatchedInstance` holds a
`SnapshotId`, and nothing distinguishes a live view from a dropped one.
**Fix.** A closeable lease: `Formed` carries it, `at(formed)` fails closed when closed, `pinnedBytes`
follows it, and PROBE→FORM holds one lease or re-captures under the ticket.

## D3 — Entity movement has no key to re-query and no dirty witness

**Scenario (huge furnace pen).** A 7x4x7 interior is a valid pen; a cow walks in after `FORM`, and an
`entity` expectation or occupancy-derived `RUNTIME` value is never re-evaluated. `20` §5.1 says
dispatch "re-queries the entities in `ReadSet.entities`" — but `ReadSet` is
`(cells, chunks, occupants: Set<OccupantRef>)` (`01`:191), `occupants` is snapshot-scoped (`11` §4.6),
and the ref carries the capture's `SnapshotId` (`20` §4.1); no live UUID exists (`16` §4.3 lists "a
UUID" under *Never*). The query has no key set, and an entity outside the read cells at capture has no
ref at all. Footprints answer over `Air` cells (§4.1), but a walking cow writes no block and
`markEntityFootprint` exists only for join/leave (§5.1): the cell→occupant index has no dirty
witness.
**Violates** R-01.
**Fix.** `ReadSet` gains `Set<EntityKey(uuid, type, nbtDigest)>` and `entityWindows` inflated by
`MAX_ENTITY_SPAN`; re-query is spatial over the windows, comparing `(key, footprint)` sets.

## D4 — Claim footprint excludes every cell a global criterion judges

**Scenario.** Two 7x6x7 furnaces whose bays are only the wall ring; the interior is judged by a
`SECTION`+`MATCH` criterion (`22` §1). The sitings overlap by two interior cells. `ClaimPlan`
footprint is "union of claiming bays, not blueprint bounds" (`08` §4.3), so neither `claimsIn` sees
the other, both `Formed`, `/cumulus build` fills both.
**Violates** R-11; `08` §8 Q2 narrows I-10 to "settled cells", restating the hole.
**Proof fails.** P2 is total over the `Claim`s the planner emits, and `plan` is pure over
`(blueprint, siting)`; it cannot know which cells a criterion reads. `08` §4.4 re-settles "only the
bays meeting the `ChangeSet`", but a global criterion implicates every bay, so changed-cell →
re-settled-bay is undefined.
**Fix.** `ClaimScope` on `Bay` (`BAY`, `INTERIOR`, `READ`); footprint is the union of scopes; STALE
re-settles every bay whose criterion inputs intersect the change.

## D5 — The gate is sound but not live, and nothing bounds admission

**Scenario (door).** A lamp beside a door toggles one block per 2 ticks inside the watch window.
`FORM` dispatches, the check takes 40 ms, a mark during it bumps `dirtyEpoch`, terms 4 and 5 fail
(`20` §5.4) → `DROPPED(STALE_VERSION)`, slot stays dirty, re-dispatch, forever: `cancel()` is
explicitly not "newer request wins" (§3.1), there is no `maxDiscards` or ageing, and X3 proves only
"no queue growth". Second: `DEFERRED` is a `MatchState` but not an `onTerminal` outcome (§3.2) and only
`AUTO` re-probes it, so a `COMMAND` build blocked on a cap never completes `done()`. Third: `AUTO` gets
`threads - 1` permits, so a one-worker scheduler starves it, silently disabling R-08.
Fourth: `tick()` re-probes AUTO with no per-tick budget or fairness (§11.5), so a 200-furnace spawn
copies ~800 sections per tick.
**Violates** `20` §7 proof 4 as liveness; §3.2; R-08; R-14.
**Fix.** Gate on the plan's read-set closure, not the whole watch window; a starvation budget settles
`Undecided(DIRTY_UNSTABLE)` while keeping the prior `Formed`; `DEFERRED` terminal-with-retry;
`max(1, threads - 1)`; one per-tick work budget.

## D6 — Cache, cost and content identity contradict each other

**Scenario.** Cold and warm runs of one furnace. `20` §6.3: a hit "charges the step's declared `cost`,
not zero … warm and cold runs therefore have the same `Spend`". `07` §3: `cost` is "a declared **upper
bound** … for ordering only". `11` §4.1: "a declared floor, never a charge", "reads bill as served".
Three meanings, so "a cache cannot turn `Undecided(BUDGET_EXHAUSTED)` into `Formed`" is unproven.
Second: by default it cannot hit — `16` §4.1 stamps `store.intern(key, states.copy(), rev)` with a
fresh `rev`, `20` §4.3's default is "no shared store", so `contentEpoch` never matches across
captures; and a no-op refresh needs `contentEquals` against prior `FrozenStates` (§5.2) that the watch
no longer holds. Third: parallel is gated three ways (`23` §7; `20` §6.4 `ChunkPolicy.parallel`; `22`
§8 `Merge.commutative()`), yet `ChunkPolicy` is absent from `MatchRequest` (`11` §3), so it changes
`Spend` and finding order outside P-1's tuple. Fourth: cache bytes, the watch index and
`InstanceRecord`s sit in no budget; `CacheKey` includes `Siting`, so entries grow with placements and
a reload's `PlanId` strands old ones.
**Violates** R-09; `20` §8.8 determinism; R-14.
**Fix.** One billing doctrine (`07`/`11` to match) or a `measured|declared` tag no budget reads;
content identity as a per-section digest on the snapshot; parallel inside `MatchPolicy`; one
`pinnedBytes` ledger with an eviction rule.

## D7 — `CheckSpec`/`ComputationSpec` have no data form, owner or subject

**Scenario.** The furnace's `mouth` needs `block` ∧ `trait`; its interior needs `interior_volume`
promoted at `RUNTIME`. `23` §6 makes `Requirement(checks, computations)` the unit and §9 asserts the
Specs are its data form; no file gives a field list, kind id, args grammar, `Focus` reference, or the
`StateKey`/`Merge`/`Retention`/`promoteTo` encoding. `SegmentDocument.Bay` has one opaque
`expectation: DataView` (`10` §3), a compact bay one `expect` (`21` §3.2), and the
`SectionedExpectation`/`AggregateCriterion` pair (`22` §4) no form at all. Adding `checks` breaks the
grammar: `MIN_VERSION = MAX_VERSION = 1`, "an unnamed field is `SC-DOC-0104`". The id spaces
(`ExpectationFactory`, `Deriver`, the absent `FocusFactory`/`ComputationFactory`, `23` §10.9) have no
owner: `10` P4 calls the body "opaque here" while Q-D6 resolves it in `SegmentLibrary.Builder.put`,
called from off-thread `prepare` (`16` §4.5) before `apply` has registry access. And no subject
check exists: a two-cell hatch with `{"type": "trait"}` names no cell, `entity`/`state`/`nested`
focuses have no node, and "the compiler enforces the same `T`" is false — `create` returns raw
`@Nullable Expectation` (`07` §3), so `allOf` of a block and an entity child validates.
**Same profile, geometry.** Lowering a cell→expectation map and keeping "its id on the cuboid containing
its declared origin" (`21` §4) splits a door after one later write — no atomicity (`08` §4.5 "both
cells or neither") — and a shell with a door hole becomes 6–8 `EXCLUSIVE` cuboids with counter ids, so
`ClaimKey.bay` (`08` §2) changes on any edit and idempotence (D1) fails.
**Violates** R-09, R-10, R-13.
**Fix.** One `Spec(kind, args, focus?, output?, retention?, merge?, promoteAt?)` family, one
`SpecResolver` in `apply`, `schemaVersion 2`, a declared subject `Class` per check; named bays emit
whole, synthetic ids are content-derived.

## D8 — Capture cannot avoid loss and has no channel to report it

**Scenario (furnace with a hopper and a chest).** `21` §6 prescribes "a TODO comment field, never a
silent loss" — but "it is still JSON: no comments" (§1), any extra field is `SC-DOC-0104` ERROR (`10`
§4.1), and the bay without `expect` is an error (`21` §3.2). The outcomes are: drop the payload, emit
an invalid document that is then *absent* from the catalog (`16` §4.5), or invent a predicate.
Further: `CaptureTool.capture(CaptureRequest): DataView` (`21` §7) takes no issues list, so `10` §3's
"`null` iff an ERROR was appended" cannot hold; `expect` asserts trait presence, nothing says "12
iron"; and import loses BE NBT by construction (`16` §4.7: a placed BE "starts empty").
**Violates** R-09, R-14.
**Fix.** Capture writes a declared `extensions["cumulus:capture"]` block, takes an issues list, nulls
on loss; or capture is declared state-lossy and import refuses BEs it cannot express.

## D9 — `RemedyContext` cannot see the value its repair is sized by

**Scenario.** Auto-build must place a floor sized by `CAVITY_VOLUME` (promoted from
`interior_volume`, `21` §3.5, `22` §10.3). `RemedyContext` is `(view, blueprint, siting, ledger,
options)` (`14` §3.2): no `MatchFacts`, no `StructureContext`. `22` §3's table says it reads them;
§13.4 leaves it open ("only derivers"); `23` §8 records the gap without a scope. The provider then
recomputes the cavity from `view` in a different order than the canonical commit (`22` §5), and
`PlanValidator` shares the context, so it cannot detect it. After `derive` the view is
dropped (D2), so `view()` is null; `allowWorldReads` defaults false, so the view is unreadable by policy.
**Violates** R-12; `22` §7; `23` §8's shared-`EditPlan` claim.
**Fix.** Add `@Nullable MatchFacts facts` and `@Nullable StructureContext context`; providers read
promoted values only, a missing key means "cannot help"; reconcile `14` and `22`.

## Rulings for the lead

1. **Claims get one predicate.** Kernel matrix only; `settle` scoped to `(owner, siting)` returning
   `ClaimConflict`; delete the adapter filter. Take the interior scope (D4) now, or state that I-10
   covers bay cells only.
2. **Snapshot lifetime becomes a type.** `Formed` carries a closeable lease and `pinnedBytes` follows
   it, or carries no view and `derive` re-captures under the ticket. That decides D2, D6's no-op
   refresh, D9's post-derive view and PROBE→FORM.
3. **Liveness is a requirement.** Adopt the starvation budget with a settleable
   `Undecided(DIRTY_UNSTABLE)`, terminal `DEFERRED` with retry, and a per-tick AUTO budget (D5).
4. **One spec family, schema 2.** The three Specs as one record, one resolver in `apply`, both
   profiles lossless, a subject per check, capture with an issues channel; until then "compact is
   first-class" is false for any structure needing a computation (D7, D8).
5. **Entity freshness: pick a mechanism.** Spatial windows plus a live identity in `ReadSet`, or
   re-admit the `setPos` hook. Today the text keeps the saving and drops the coverage (D3).
