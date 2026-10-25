# Round 1B — typed predicate / state / context model

Attack on `23` §6–§6.3 against `07`, `09`, `12`, `22` (with `11`, `13`, `20` where they constrain the
same claim). Eight defects, most severe first.

## D1 — A check's verdict depends on its bay's chunk alignment (Critical)

**Scenario.** Bay `wall`, inside one chunk, carries a `Check<Long>` whose focus reads `IRON`
(`StatePhase.MATCH`, folded at commit). 20 §6.2 calls a BAY step *local* when its `ReadEnvelopeAware`
envelope fits one chunk; local steps run before the commit barrier, and a section view reads only
committed `MATCH` cells (22 §3). `read(IRON)` answers `null`, the focus fails, the check is
`UNREADABLE`, 11 §4.4 gives `Undecided`. Move the bay one block to straddle the boundary: global, after
commit, reads 64, `MATCH` — same blueprint, snapshot, siting.

**Violated.** I-4; 22 §5 ("the aggregate criterion runs once per attempt, never per section"); 22 §11.1;
`23` §6's premise that `Check<T>` is subject-typed, not schedule-typed.

**Proof fails.** 20 §6.2 keys on the *expectation's* envelope and cannot see that the *focus* reads an
unreached phase; 22 §11 argues from one aggregate context per attempt, which `23` breaks by placing the
criterion in a per-bay, hence per-partition, `Requirement`.

**Fix.** `Focus` declares the phases it reads; a step with a `MATCH`-reading check is global; 09 §4.8
refuses it in a local class at DESIGN instead of discovering `null`.

## D2 — Absence folds to `identity()`, so "did not read" becomes a wrong number (Critical)

**Scenario.** `IRON = StateKeys.sum(…, SECTION, FACTS)` over three sections; the middle chunk is
unloaded, slot `Refused(NOT_CAPTURED)`. 22 §5: the missing section "contributes nothing", the fold starts
at `merge.identity()` = `0`. `read` returns `0`, not `null`; `iron >= 64` yields `MISMATCH`; 11 §4.4
makes one `MISMATCH` decisive, and "`Failed` licenses a repair that changes the world".

**Violated.** 22 §11.5 and its §12 counterexample ("a missing section is treated as zero → must return
`UNREADABLE`"); 07 §4.1; 11 §4.3.

**Proof fails.** `identity()` is both the fold's unit and an admissible result, so `@Nullable` cannot
express "no contribution". 22 §5's escape ("unless the key has a documented identity") is vacuous: §4
requires a non-null `Merge` for every `SECTION` key and every `Merge` has an `identity()`. The §12 test
is unwritable — nothing in the type can fail it.

**Fix.** Fold over present cells only, `@Nullable` when none contributed; keep `identity()` inside
`combine` or drop it; a default is declared by name.

## D3 — `StateKey` is a record whose equality includes a lambda (Critical)

**Scenario.** Addon A holds `StateKey<Long> IRON`; addon B — or the host copying a child context into a
parent seed (12 §4.6 relies on "the `ContextValue` key is the same record on both sides") — builds the
same declaration to read it. `Merge` is a functional interface, so the instances differ by identity and
the derived record `equals` says the keys differ. `MatchFacts.state` is `Map<StateKey<?>, Object>`: two
entries for one logical cell, or `read(IRON_B)` misses and returns `null` → `UNREADABLE` for a present
cell. Meanwhile 22 §4.1's A3/A4 identify a cell by `id` while the map keys on the whole record, so two
registrations sharing an id with different `retention` are neither refused nor merged.

**Violated.** I-13 and 03 §6; 12 §4.6; `23` §6.2; I-7.

**Proof fails.** Heterogeneous-map safety needs equality on `(id, type)`; every extra component must be
canonical, and `Merge<T>` is not. `StateKey` is a record, so the rule is derived and stated nowhere,
while 22 §4.1 states the opposite rule for the same identity.

**Fix.** `equals/hashCode` on `(id, type)` only; validate `phase`, `retention`, `merge`, `promoteTo` at
registration; forbid construction outside `StateKeys`.

## D4 — The compiler does not enforce `Check<T>`'s `T` on the paths that matter (Critical)

**Scenario.** A document names `{focus: "cumulus:state", expectation: "mymod:iron_at_least"}`.
`CheckSpec` (§9) has no subject and `Requirement.checks` is `List<Check<?>>`, so `T` is erased at both
sites; at run time `Expectation<Long>` gets a subject built from another key, the cast fails, and 20 §3.3
maps the `ClassCastException` to `Undecided(WORKER_FAILED)` + `SC-MC-0601` — a datapack typo reported as
a worker crash, which 07 §5.1 forbids for ids. Composition has the same hole: `allOf` over two
`Check<?>` of different `T` is legal, and `Bindings.merge` then merges subjects nobody related.

**Violated.** R-13, I-13, `23` §6 ("the compiler enforces the same `T`"), 03 §6.

**Proof fails.** The guarantee is inference at `new Check<>(focus, exp)`, which exists only in Java
source. `Check<T>` is invariant in both positions (the focus produces `T`; the expectation consumes it
and emits findings about it), so no wildcard container recovers `T`; `List<Check<?>>` is the only usable
container and reading it erases. `CheckSpec` and addon-built `Bay`s are outside every proof.

**Fix.** `Focus` exposes `Class<T> subject()`; validate it against the expectation's in `Check`'s
constructor and in the `CheckSpec` resolver at DESIGN; claim only that.

## D5 — Two promotion authorities over one `ContextKey`; no derivation means no promotion (High)

**Scenario.** `StateKey` carries `promoteTo` and `promoteAt`; 12 has `Deriver.put(ContextKey, T)`, and
A3/A4 refuse a second *deriver* per key id — but `DerivationPlan.compile(specs, derivers, external)`
cannot see a computation's promotion. A computation promoting `INTERIOR_VOLUME` plus a deriver producing
it both write, and `StructureContext` is a bag of `ContextValue`, so the later write wins silently, while
`23` §6.1 says for the other typed map that "same key + different value is a finding". Promotion *is*
performed by derive (22 §9), so a blueprint with computations and `derivations() == []` compiles to an
empty plan, derive never runs, and `RuntimeState` is empty: R-03 delivers nothing without a dummy deriver.

**Violated.** R-03, R-13, 12 §4.1 A3/A4, `23` §6.1.

**Proof fails.** Typed-key uniqueness is proved in 12 over derivation steps only; `23` adds a second
writer class without extending the proof, and `ContextStore.draft/publish` has no owner or duplicate
check at all.

**Fix.** Delete `StateKey.promoteTo/promoteAt`; one owned `ContextStore.publish` per key returning a
finding on same-key-different-value (as `Bindings.merge` does); derive runs whenever `FACTS`/`CONTEXT`
cells exist; `Computation.tier()` is the only tier authority.

## D6 — "Latest at or below" erases tier lineage, and STALE invalidation has no referent (High)

**Scenario.** PROBE publishes `TIER_ESTIMATE = 2`, FORM publishes `3`; a SETTLE check reads 3, a PROBE
reader reads 2, and nothing marks authority. 23 §6.3 says "`STALE` republishes the tier it re-ran with a
new revision; higher tiers are invalidated" — but `STALE` is a `Stage`, not a `ContextTier`
(`BLUEPRINT → PROBE → FORM → SETTLE → RUNTIME`), so "the tier it re-ran with" has no referent (11 §4.6
runs `plan(STALE)`). The persisted `StructureContext` holds `ContextValue` with no tier or revision, so
after a STALE invalidating FORM/SETTLE the old `RuntimeState.context` still answers 3; 12 §4.5's only
freshness check is `generation`, which 11 §4.6 increments only on a re-forming STALE. `ContextLifetime`
also gives `MATCH` a third meaning beside `StatePhase.MATCH` and `Verdict.MATCH`.

**Violated.** R-08, 23 §10.2 (admitted), I-4.

**Proof fails.** "Latest at or below" is a history rule; determinism needs the frame sequence to be a
function of the tuple, which the STALE rules deny when one tier re-runs against an older revision.

**Fix.** Frames carry `(tier, revision)`; `get(key, tier)` returns the stamped value or `null`;
`StructureContext` records frame refs plus a `contextRevision` compared by `RuntimeState`; align Stage
and ContextTier; delete `ContextLifetime`, rename `StatePhase.MATCH` to `AGGREGATE`.

## D7 — Focus failure has no channel into `Reply`; `FocusDesc` cannot carry a state diagnosis (High)

**Scenario.** `Check<Long>` on `state(mymod:iron_count)`; the cell is absent because the computation
returned `ComputationResult(null, …)` under a spent budget. `Focus.take` answers `null`, and the text
picks none of three readings: synthesise `unreadable(...)` — `test` never ran, so no finding exists,
while 07 §4.1 wants ≥ 1 and 13 §4.2 a PRIMARY label, and no `SC-FOC` family exists in 03 §7 or 13 §5;
`abstain()` — silent, the "why" lost as 12 §8 already concedes; `test(null)` — implied by `@Nullable T
take`, forbidden by a non-null `T`. The facts a state diagnosis needs (key id, contributing sections,
which were `NOT_CAPTURED`, the fold so far) have no home: `FocusDesc.desc()` is one descriptor, while
13's multi-label rule lives on a `Finding`.

**Violated.** R-07, 23 §10.3 (admitted), 07 §4.1, 13 §4.2.

**Proof fails.** Failure channels are spread over three types (`take` nullness, `test`'s `Reply`,
`ComputationResult.value` nullness) with no precedence, and 07's invariants cover only the middle one.

**Fix.** `FocusResult<T> = Found(T) | Missing(FocusDesc, FindingBundle)`; `FocusDesc` carries
`(code, Locus, Set<StateKey<?>>)` with labels; `Check` maps `Missing` to `unreadable(desc.findings())`;
13 allocates `SC-FOC-0xxx`; a null `ComputationResult` becomes `Missing`.

## D8 — The step cache and the state cache are incompatible, and a hit has no fold cost (High)

**Scenario.** 20 §6.3 caches per chunk `SectionResult(key, epoch, Map<Integer, StepResult>, …)`; 22 §8
caches per section `Map<StateKey<?>, Object>` inside `SectionResult`, cacheable when the expectation is
`ReadEnvelopeAware` and the envelope fits the section. A `SectionedExpectation` is `ReadEnvelopeAware` by
declaration (22 §4) and its envelope covers every contributing section — the whole bay — so by 20 §6.2
it is never local while by 22 §8 it is section-cacheable. A hit charges "the step's declared `cost`", but
`ExpectationMeta.cost` bounds *occupant reads* (07 §3) while the work is folds, the aggregate criterion
and promotion, so a cold run can be `Undecided(BUDGET_EXHAUSTED)` where a warm run forms — the inversion
20 §6.3 forbids.

**Violated.** 11 P-4 (budget monotonicity), 20 §6.3's own claim, 22 §11.6, I-4.

**Proof fails.** The unit of `cost` is frozen and does not name the new work, and 23 §7's units
(`cellReads`, `occupantReads`, `sections`, `steps`) define no per-fold charge, so hit and miss cannot be
shown to cost the same.

**Fix.** Define fold/aggregate/promotion charges in those units, extend the metadata with contribution
and fold cost, cache one object covering step results and state cells keyed `(stepIndex, StateKey)` under
one `PlanId`.

## Rulings for the lead

1. **Keep `Reply` non-generic; make subject correctness a construction check.** `Reply<T>` would make
   `allOf`/`anyOf` invariant over heterogeneous children and still fail `CheckSpec`; adopt the subject
   token plus `FocusResult`, and call the guarantee constructor-time + DESIGN (D4, D7).
2. **One owner per typed key in both bags.** `Bindings.merge`'s no-silent-overwrite rule governs
   `ContextKey` and `StateKey` writes; `StateKey` equality narrows to `(id, type)` (D3, D5).
3. **Absence must be representable.** No fold returns `identity()` for a cell no section contributed;
   `read` is `@Nullable` and a default is declared by name (D2).
4. **Phase, not geometry, classifies a step.** A `MATCH` reader is global; a section-phase computation
   may not read committed state; the barrier is a type split, not a runtime exception (D1).
5. **One table for `phase × retention` and one tier authority.** Publish writer, reader, lifetime and
   `promoteTo` legality per cell; delete `ContextLifetime`; `RuntimeState` carries a `contextRevision`
   (D6).
