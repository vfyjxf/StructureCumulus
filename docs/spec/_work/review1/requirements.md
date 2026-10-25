# Round 1A — attack on `23`'s coverage of R-01…R-15

Read: `23`, `00`, `01`, `02`, `06`, `07`, `09`, `11`, `12`, `15`, `19`, `21`; "the design" = `23`.

## D1 — R-03/R-08/R-10/R-13: a deferred proposal is promoted, leaving two computation models

**Scenario.** An addon ships `interior_volume` as a `Deriver` in `Catalogs.derivers()` (`15` §4.3), named by
`derive`. A `Check<Boolean>` needing it during FORM cannot see it: `12` derives from facts after `Formed`.
The alternative `Computation → StateKey` (§6.2) has no catalog family (§10.9), no glossary row
(`01` §P), and five undefined names (`ComputationSpec`, `ComputationContext`, `ComputationResult`,
`FocusContext`, `ContextStore`).
**Violates.** R-03, R-08, R-10, R-13; `19` P-1's graduation rule and its unresolved sharing question.
**Why it fails.** The R-03 row names `Computation → StateKey → ContextTier`, but no frozen signature carries
it and the derivation path is not marked superseded. Two owners, one value, no precedence.
**Fix.** Rule P-1: one model, `Catalogs` extended, `12` rewritten — or delete §6.2–6.3.

## D2 — R-06/R-13: `MATCH` claims the whole bay, but nothing ties a check to its cells

**Scenario.** An L-shaped pipe corner as a 2×2×2 bounding box with four must-be-air cells (`07` §4.5's
sanctioned workaround) MATCHes and claims all eight cells `EXCLUSIVE`; the neighbouring corner's four air
cells are then owned, so it fails `SC-CLM-0002` on a correct build. As `allOf` bays, `09` §4.2 forces
`SHARED` + declared overlap, so neither is exclusive. `BayBinding(bay, OccupantRef, Bindings)` must name one
occupant for 42 bricks or a two-half door.
**Violates.** R-06, R-11, I-2/I-3, `07` F4.
**Why it fails.** `SpanExpectation` as `Expectation<BayView>` "(or a marker)" is `07` §3's empty marker, so
the type carries no span-ness; claims follow the bay cuboid, not what the test read. Coverage is assumed, and
a one-cell check returning MATCH claims a two-cell bay.
**Fix.** Mandatory `coverage(Cuboid): CuboidSet`; refuse MATCH unless coverage ⊇ bay cells; `BayBinding`
carries `List<OccupantRef>`.

## D3 — R-08: checks have no stage or identity, so per-bay staging and STALE reuse are impossible

**Scenario.** A mouth bay needs a cheap `block(iron_bars)` at PROBE and an inventory trait at SETTLE.
`Requirement` holds both, but the bay is one `CheckStep(BAY, bay)` in one stage (`09` §3; BAY ∈ {FORM,
SETTLE}); splitting needs two coincident bays (`09` §4.2). `Check<T>` has no id, while
`stepReads` is keyed by step id and `11` §4.6 reuses on disjoint read sets.
**Violates.** R-08, R-11, R-13.
**Why it fails.** Ordering exists only across `CheckStep`s (`09` §7.4). A `Requirement`'s replies have
different `T`, so `07` §4.2's per-verdict fold cannot combine them and no heterogeneous fold is defined; two
checks binding one key become `SC-EXP-0003`.
**Fix.** Make `Check` the planned unit `(id, Stage, Focus<T>, Expectation<T>, cost)`; a bay step expands into
its checks; `stepReads` keys on check id.

## D4 — R-05/R-04: the controller's facing never reaches the kernel

**Scenario.** A mouth of iron bars with `facing=east`. `09` §4.5 step 3 sets
`target = candidate.implied().apply(NORTH)` where `implied` is the placement under test (`11` §4.2), then
takes the first `o` with `o.apply(NORTH) == target` — true for `NONE`; `select(Heading)` is never fed a
heading, `AnchorSpec` has no facing field, and `21` §3.3's `orientation.facing` names a role.
**Violates.** R-05, R-04, I-5.
**Why it fails.** R-05's row claims an "anchor implied heading" and an adapter `Heading`↔`Direction` map, but
no frozen type takes a heading. `09` §7.5 proves determinism of "first allowed", not orientation.
**Fix.** `AnchorSpec` declares `(propertyKey, expectedValue)`; `AnchorCandidate` carries the resolved
heading; the policy rejects any turn that does not map it to the authored front.

## D5 — R-01/R-11: entities are read but not owned, and capture demand is specified twice

**Scenario.** A 2×1 golem straddles two hatch bays; `06` §4.3 returns it whole to both, both MATCH, each
claims its half. Claims are per cell, `ClaimConflict` needs a shared cell, so two instances own one entity
with no finding. A golem walking inside the window marks nothing (`19` R22; no `ChangeSet` entity member).
Demand is doubled (`requiredKinds` vs `trackedTypes`), neither derived from the blueprint.
**Violates.** R-01, R-11, I-2, `06` §4.6.
**Why it fails.** R-01's row is a read model; ownership, freshness and budget are cell-shaped. "Re-query cost
unproven" is a type gap, not an evidence gap.
**Fix.** Reject a match whose footprint crosses its bay, or give claims occupant identity; add
`ChangeSet.entities`; name one demand field.

## D6 — R-07: a focus failure has no channel, and a non-spatial subject has no locus

**Scenario.** `Check<Double>` with `Focus.state(CAPACITY)`; the producer was dropped (`SC-CTX-0103`), so the
key is absent. `Focus.take` returns null with no finding, leaving `ABSTAIN` (silent ⇒ `Undecided(ABSTAINED)`
forever) or `UNREADABLE` (asserting a snapshot failure, false). Two matched cows cannot carry two labels.
`Label` needs `Locus` = frame + `CuboidSet` (+ span); a state key has no cells, a data check no span.
**Violates.** R-07, R-13, I-7.
**Why it fails.** `FocusDesc` is "sketched, not specified" (§10.3) and `Reply` is non-generic, so "subject
absent" cannot be separated from "world unreadable". Four states cover world predicates, not subjects.
**Fix.** Sealed `FocusResult<T>` (`Found`/`Absent`/`Ambiguous`/`Unreadable`), a `SUBJECT` locus frame, and a
code family for absent subjects.

## D7 — R-03/R-12: promotion has four knobs and no rule; remedies cannot see the result

**Scenario.** `StateKey` carries `phase`, `retention`, `promoteTo`, `promoteAt`, and `Computation` adds
`tier()`: four unranked knobs for one decision. §6.3 keeps intermediate frames "for the instance lifetime and
for diagnostics/remedies" while "`ContextLifetime.MATCH` values are not persisted" (`01` lacks the type).
`/cumulus build` on a cobble-filled furnace must clear exactly `INTERIOR_FREE`, but
`RemedyContext(view, blueprint, siting, ledger, options)` has no `StructureContext`, and `allowWorldReads`
defaults false.
**Violates.** R-03, R-12, I-9/I-11.
**Why it fails.** §8 concedes `RemedyContext` "should gain read access" — that is the whole R-12 mechanism
for R-03's headline case.
**Fix.** `phase` schedules, `Retention` persists, `promoteTo` publishes (delete `promoteAt`/`tier()`); add
`StructureContext` to `RemedyContext`.

## D8 — R-08: three counters, two invalidation rules

**Scenario.** A decorative block inside the bounds but outside every read set changes: `11` §4.6 reuses all
steps, re-forms, `generation + 1`; §6.3 republishes a tier revision and invalidates higher tiers. A host
keying `RuntimeState` by generation regenerates and re-derives RUNTIME for an irrelevant block. A read-set
change that still MATCHes also bumps generation.
**Violates.** R-08, the determinism tuple, `12` §4.5.
**Why it fails.** `ContextFrame.revision`, `InstanceRef.generation` and `dirtyEpoch` have no stated relation,
and `Stage`/`ContextTier` have different members (`DESIGN`/`STALE` vs `BLUEPRINT`/`RUNTIME`), so "which tier
re-runs" is undefined for two stages.
**Fix.** One `Stage → ContextTier` table; bump generation only when a promoted key's producer re-runs.

## D9 — R-09/R-10: no data form for `Check`/`Computation`, and the catalogs contradict `15`

**Scenario.** A datapack door wants a cheap check plus a tier computation. `21` §3.2's `expect` vocabulary is
`block/air/any/entity/trait/allOf/anyOf/not/factory`; neither it nor §9's compact fields have checks, state or
computations. Even if added, `StateKey<T>` needs a `Class<T>` and `DataView` offers only
`asString/asInt/asBoolean`. `Catalogs` is frozen at `expectations/derivers/remedies/codes` (`15` §4.3), while
§2 names two other families and drops two.
**Violates.** R-09, R-10, R-13, `01` §P.
**Why it fails.** R-09's row says `CheckSpec`/`ComputationSpec` "are the data form of typed requirements";
neither is defined and no factory resolves a focus. The R-10 list is not the frozen one, so "trivial only" is
unevaluable.
**Fix.** Freeze `checks[]`/`computations[]` with focus/expect/state/stage; add a `TypeRef`; add both factories
and state what happens to `codes`/`derivers`.

## D10 — R-11: settlement sits outside the match, so `Formed` can be non-committable

**Scenario.** Two players place overlapping 3×3 furnaces in one tick. Both SETTLE against one `FrozenLedger`
revision and return `Formed` with claims; `SavedClaims.settle` CAS accepts one. The loser already holds a
`Formed`, a derived context and a stored `RuntimeState` (`11` §5), and no kernel value says "rejected".
Reservations are invisible — `07` §3 "private", `11` §4.5 session-local — yet `isReserved` reports "open
reservations" (`01` §F): either always false or host-written.
**Violates.** R-11, I-10.
**Why it fails.** The CAS is host code after the kernel call, so the sealed result cannot describe the
contested outcome.
**Fix.** Commit at SETTLE via a `ClaimLedgerWriter` SPI, or carry reservations and return a
`SettlementResult` convertible to `Failed`.

## D11 — R-14/R-08: a `Formed` at PROBE or FORM is type-identical to a settled structure

**Scenario.** `AUTO` = PROBE only (§7) returns `Formed(stage=PROBE)` with no bay facts and no claims; `19`
R7 says "only a `SETTLE` `Formed` is a structure". A host following `11` §5 stores the instance, commits empty
claims (`08` §4.4) and derives a context from an unsettled match — exactly the first-use example R-14 asks
for.
**Violates.** R-14, R-08, I-10.
**Why it fails.** Stage is a field, not a capability: one record covers three guarantees and
`derive`/`commit` accept all three; the eight facade operations have no "settle".
**Fix.** Split `ProbePass` from `Formed`, or add `Formed.settled()` and require it; document a one-call
`check()`.

## D12 — R-15/R-02: no sketch compiles against `23`, and purity is an import scan

**Scenario.** `07` §3 freezes non-generic `Expectation`, `09` §3 freezes `Bay(..., Expectation, ...)`, `12`
freezes `Deriver`; §6 declares `Expectation<T>`, `Bay(..., Requirement, ...)`, `Check<T>`. Every sketch predates the consolidation; `Check`, `Focus`, `StateKey`, `Computation`,
`ContextTier` have no sketch and `kernelTest` is NO-SOURCE. R-02 is `compileKernelJava` + an import scan,
while `SnapshotPolicy.maxStalenessNanos` is a clock (against I-15 and §7's "no nanos") and `Budget.sections`
is the host's chunk unit.
**Violates.** R-15, R-02, I-15, `00`'s "claim without a falsification path".
**Why it fails.** The only falsification evidence type-checks against nothing; an import scan proves absence
of three prefixes, not host neutrality.
**Fix.** One `kernelTest` builds the furnace with `Check`/`Computation` in CI; delete 23-only names; move
staleness to the host.

## Questions the lead must rule before round 2

1. **P-1 in or out?** If in, do `StateKey`/`Computation` replace `ContextKey`/`Deriver` outright, or do both
   survive with a written division? D1, D7, D9 turn on this.
2. **Does `MATCH` mean "the check asserted every cell of the bay's cuboid"?** If yes, where is coverage
   declared so a partial check can be refused (D2)? If no, what stops a one-occupant check from claiming a
   two-cell door?
3. **Is `Check<T>` a planned step** with its own id, stage, budget and read set (D3, D8), or an element folded
   inside one bay step? This decides whether R-08's staged checks and STALE reuse exist.
4. **Is `Stage` the only scheduling axis**, with `ContextTier` a projection of it (D8, D11)? If so, what do
   `DESIGN` and `STALE` map to?
5. **Who commits claims** — the kernel at SETTLE through a host write SPI (D10), or the host, leaving `Formed`
   advisory with a settlement token and a reject path?
