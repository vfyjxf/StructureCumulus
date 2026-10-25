# Round 2B — types, generics, data path

Attack on `23` revision 2, §6 / §7 / §8.3 / §12 and P5, P6, P9, P10, P11. Eight defects,
ranked by damage. Section refs are to `23` unless prefixed.

---

## D1 — A refused section drops out of the fold with no finding; `P5` holds only for the all-absent case (Critical)

**Scenario.** `mymod:iron_count` = `StateKey<Long>(SECTION, FACTS, merge=sum)`, computation
`count{region: wall, mode: iron}`, `reads().cells = wall`. The wall spans three sections; the middle
one is unloaded, so `SectionSlot.Refused(NOT_CAPTURED)` (§5.1/§5.2). §7.2: "A `SECTION` unit runs
once per section in `region ∩ section`; each run writes a partial… The fold starts at the first
**present** partial… Zero present sections → `whenAbsent` if declared, else `Missing(SC-FOC-0103)`."
Two of three sections are present, so the fold is `partial₁ + partial₃`. No `Missing`, no finding,
no `Undecided`. `iron >= 64` → `MISMATCH` → `Failed`, and `11` §4.4 makes `Failed` "a repair that
changes the world": the remedy rewrites a wall that was merely unloaded.

**Violated.** P5 ("absence is never a number", §14); R-07; review-1 D2, which §15.2 rules
**Fixed**; `22` §5 ("marks the key's coverage incomplete") and `22` §11.5, whose obligation §23
silently drops.

**Why the proof fails.** The proof of P5 is the *absence of `identity()`* plus a fold that starts at
the first present partial — but "present" is defined only as "not zero contributions". Nothing in
§6.4, §6.6 or §7.2 makes a refused section a finding, and the only channel that could carry it,
`Missing`, is reachable only when *every* section is absent. `whenAbsent` then reintroduces the
ruled-out identity through the front door: it fires exactly when nothing was read, including when
nothing was read *because nothing could be read*. `§16` OQ3 concedes the hole and leaves it open.
`SC-FOC-03xx` ("subject unreadable (section refused)", §6.6) is a *focus* code — a `SECTION`
computation has no focus, so the one code allocated to this failure is on the wrong side of the
`Focus`/`Computation` split. The types cannot express the distinction either: `Merge<T>.combine`
returns `T`, so a partial has no way to be "refused", and `Outcome<T>` is returned by the *unit*,
once, not per section.

A second hole sits under the same sentence: `region` has no declared source. §7.2 says "in
`region ∩ section`", but neither `Computation` nor `PlanUnit` has a region — the nearest thing is
`reads().cells`, a *declared read scope*. If the fold's domain is inferred from it, a computation
that reads one probe cell per section while summing over a whole bay folds over the wrong section
set, and if it is not inferred the type carries no way to state the domain at all. Which sections
must be present is therefore not derivable from any declaration.

**Minimal fix.** `Computation.region()` (a `CuboidSet`, in the plan unit, not in `reads()`) becomes
the fold domain. `SECTION` fold yields `Outcome<T>`: `Missing` unless every section in
`region ∩ section` was `Present` **and** its partial was `Found`. `whenAbsent` is renamed
`absentWhenEmpty` and applies only when `region` intersects zero sections; a separate per-key
`partialOk: boolean` is required to accept a short fold, and when set the refusals must appear as
`SC-FOC-0301` findings in the bundle (WARNING, not silent). `Merge` stays `T→T`.

---

## D2 — `Coverage` is a property of the bay but its evidence is per check, and `ClaimScope.Owned` restores the hole `decisionCells` was added to close; the DESIGN check cannot be evaluated (Critical)

**Scenario A (P11 defeated by a legal declaration).** Bay `cavity`, `cells` = 100 cells,
`coverage: "subject"`, `claims: "owned"`, one check `focus: block([1,1,1])` deciding one cell.
§6.5 `CUBIC` would reject it, so the author writes `SUBJECT`. At match time the union of
`decisionCells` (one cell) must equal "the union of the resolved subjects' footprints" — the one
block's footprint, one cell. Equal. The bay MATCHes, and §10.2 `Owned` = "bay cells" claims **100**
cells on the strength of a one-cell assertion. `SC-BAY-0107` is CUBIC-only; `SC-BAY-0108` covers
*reads* outside the scope, and the read is inside. `§13`'s door only looks safe because it happens
to use `Occupied`.

**Scenario B (a check that belongs to no bay).** §12.3's own compact example has
`{"id": "mouth_facing", "stage": "FORM", "focus": {"kind": "block", "cell": [3,1,0]}, "expect": …}`
with **no `bay`**. §12.3 says such structure-level units are legal ("`checks[]` … that a bay may
reference"), so a check may decide a cell in a bay's cuboid while belonging to no bay: it is outside
every coverage union, outside every `SC-BAY-0107/0108`, and §7.4 builds "a `MatchFacts.bindings` map
… per bay" — so its `Bindings` have no slot at all and are dropped.

**Scenario C (DESIGN cannot call the function).** §6.5 states the CUBIC check happens "at DESIGN".
The only signature is `CuboidSet decisionCells(FocusContext ctx)`, and `FocusContext` exposes
`read`, `occupant`, `occupants`, `bay`, `published`, `Siting` (§6.3). DESIGN has no snapshot, no
siting, no tier (§8.1) and is "not dispatched" (§6.1). For `occupant`/`entity`/`nested` focuses the
decision cells *are* the resolved footprint, so `decisionCells` is a world query by construction —
and for exactly those kinds `SUBJECT`'s match-time test is a tautology (the union of a
footprint-focus's decisions is the footprint it just resolved).

**Violated.** P11; R-06, R-11; §6.2's claim that a bay's checks are the unit of coverage;
`§15.1` D2's ruling.

**Why the proof fails.** P11's argument is "`decisionCells` + `Coverage`". `Coverage` constrains the
union of decisions, `ClaimScope` independently names what is claimed, and no clause ties them: the
design constrains the *predicate* and then lets the *claim* be declared to be larger. A proof needs
`claimed ⊆ ⋃ decisions`, which is a different statement from `⋃ decisions = bay.cells`.

**Minimal fix.** (a) `Owned` is legal only for a bay whose DESIGN-time `decisionRegion()` equals
`bay.cells`; otherwise `SC-BAY-0110`. (b) Split the focus signature: `CuboidSet decisionRegion()`
(world-free, for DESIGN) and `CuboidSet decided(Siting, ContextView)` (for the match-time check);
a focus whose `decisionRegion` is not a constant is `SC-BAY-0107` at DESIGN rather than CUBIC by
default. (c) A check with no `bay` must declare `claims: audit` or name a bay; its bindings go to
`MatchFacts.orphanBindings` or are refused (`SC-BAY-0111`).

---

## D3 — `P6`'s "type split" is not in the type: `read(StateKey<T>)` accepts any phase, and `reads()` is a declaration nobody checks (Critical)

**Scenario.** `K = StateKey<Long>(AGGREGATE, …)`. `X` is a `SECTION` computation whose body calls
`ctx.read(K)` (or `ctx.published(ContextTier.FORM)`, §6.3 — which is worse). `X.reads()` returns
`keys = {}`, `phases = {SECTION}`. §7.3's DESIGN rule keys on `reads().phases()`, so `SC-CHK-0105`
does not fire; the barrier is crossed at *runtime*, which §7.3 explicitly claims cannot happen ("a
type split (`StatePhase`), not a runtime guard"). What `read(K)` returns depends on a fact the spec
never fixes: if it reads the current stage's draft, `K` is absent (not yet written) and the partial
silently folds a null-as-zero; if it goes through `ContextView.get` at tier ≤ FORM, it returns the
**previous attempt's** FORM frame (§8.3 "a reader at tier `t` sees the newest frame at or below
`t`"). Same blueprint, siting, snapshot and stage then give two different values depending on
whether the structure has been checked before — I-4 broken, and `Undecided`/`Failed` flip with it.

**Second front (the data path).** §12.2 `Spec` has `kind`, `args`, `focus`, `subject`, `output`,
`retention`, `merge`, `stage`, `claims` — **no `inputs`**. `Computation.inputs()` must therefore be
reconstructed from opaque `args` by each factory, but `inputs()` is what §7.1 step 2 uses for Kahn
order and what §6.4's `SC-CHK-0103` overlap rule reads. `SpecResolver` validates "kind, args,
subject … and the key declaration" and nothing about inputs. A datapack computation that reads a key
its factory forgot to report is unorderable, unguarded and un-designer-checked, and the whole
`SC-CHK-0102` cycle proof is a proof about a set the factory is free to under-report.

**Violated.** P6; R-08; I-4; §7.3; `22` §4's `StateView`, whose `StateScopeException` was the only
mechanical enforcement and which §6.4 deleted without replacement.

**Why the proof fails.** `StatePhase` is a *component of the key*, not a capability of the reader:
`FocusContext.read(StateKey<T>)` and `ContextView.get(StateKey<T>)` are total over all phases, so
the "split" is a naming convention. `reads()` is a hand-written `PlanUnit` method on an SPI type;
nothing derives it from behaviour and nothing compares it with the reads actually performed.
`§15.2` D1 is recorded **Fixed** on this basis.

**Minimal fix.** Two reader types, not one: `SectionReads.read(SectionKey<T>)` where `SectionKey`
can only be produced from a `SECTION` key, and `MatchReads.read(StateKey<T>)`; `FocusContext` has
no `published(tier)`. Keep a run-time backstop (`SC-CHK-0106`, unbilled) for a key whose phase
exceeds the context phase, because the SPI is open. Add `Spec.inputs: [keyRef]`; for spec-built
computations `inputs()` returns the resolver's set and the factory cannot widen it.

---

## D4 — `StateKey` identity is `(id, type)` while four behavioural components sit outside it, and the "registration" that is supposed to reconcile them does not exist (Critical)

**Scenario A.** Addon A publishes `StateKey<Long> IRON` with `merge=sum, retention=CONTEXT`; addon B
(§12 §4.6's hierarchy seed copy, or a blueprint reading a key it does not own) builds the same
`ns:path` + `Long` with `merge=null, retention=FACTS`. `equals` is true, so the two are one entry in
`MatchFacts.state`, one entry in `ContextFrame.values`, and one element of `ReadScope.keys`. Their
*behaviour* is decided by whichever declaration the writing unit carries: §7.2 folds with that
unit's `merge`, §6.4 gives `SECTION`/`AGGREGATE` by that unit's `phase`, §8.3 publishes to that
unit's tier. One cell, two authorities, no finding. `SC-CHK-0103` is scoped "one plan may not
declare two computations writing the same key with different declarations" — per plan, so it cannot
see two `StateKeys` values minted in two addons, nor a reader and a writer that disagree.

**Scenario B (the site the fix names does not exist).** §6.4: "Construction is only through
`StateKeys`; a key built elsewhere is `SC-CTX-0101`. Registration validates `phase`, `merge`
non-null iff `SECTION`, and `whenAbsent` type." `StateKeys` is *called* in `22` §6
(`StateKeys.sum(id, Long.class, SECTION, FACTS)`) and never declared anywhere — not as a type, not
as a member, not in `12`, `15` or `23`, and §15's `Catalogs` has exactly four families
(`expectations`, `derivers`, `remedies`, `codes`), with `15` §4.3 ruling that "a fifth family is a
glossary and code-table event". So the validation is assigned to a registry that no signature
declares, and enforcing "a key built elsewhere is `SC-CTX-0101`" additionally requires making the
record's canonical constructor inaccessible, which contradicts `record StateKey<T>(…)` being public.
The family count is already inconsistent: §6.7 lists five families (`expectations`, `focuses`,
`computations`, `remedies`, `codes`), so "exactly four" is stale, and a key/proof registry would be a
sixth — the point is not the number but that no document defines the one §6.4 relies on.

**Scenario C (a datapack expresses the conflict, and `§12.3`'s own example is illegal).** `state[]`
declares `{"id","type","phase","retention","merge","whenAbsent"}`; `Spec` (§12.2) carries
`@Nullable MergeRef merge` and `@Nullable Retention retention` too, and §12.3 permits "a `Spec` may
reference a declared key instead of repeating it" — so one key has two declaration sites and no
precedence rule, while `Spec` has **no `phase`** and therefore cannot make a complete inline
declaration at all. And the flagship example declares
`{"id": "mymod:volume", "type": "long", "phase": "aggregate", …, "merge": "sum", "whenAbsent": 0}`
— an `AGGREGATE` key with a non-null `merge`, which §6.4's own rule ("`merge` non-null iff
`SECTION`") refuses at registration; `whenAbsent` has no meaning for an aggregate either, since
§7.2's default rule is a section-fold rule and no aggregate fold is defined anywhere.

**Violated.** R-13; I-13; §6.4's single-writer claim; `15` §4.3; review-1 D3, which §15.2 rules
**Fixed**.

**Why the proof fails.** Narrowing equality to `(id, type)` was the right move for map safety and the
wrong move for identity: it made the *cell* identifiable and left the *declaration* unidentifiable.
"Validated at registration" is an appeal to an authority that the document set does not contain, so
the invariant is unfalsifiable — and §12.3 falsifies it on the first page of JSON.

**Minimal fix.** Make the declaration the identity: a frozen `KeyDecl(id, type, phase, retention,
merge, absentWhenEmpty)` created only by a `Catalog<KeyDecl>` (the fifth family, accepted
deliberately), with `StateKey<T>` a handle equal on `id` alone and `decl()` reachable. A second
declaration for one id is `SC-CTX-0109` at `apply`, regardless of which document or addon wrote it.
Delete `merge`/`retention` from `Spec`; a `Spec` may only *reference* `state[]`. Fix the example to
`phase: "section"` (or drop `merge`).

---

## D5 — Lowering: "a later write … merges into the bay that owns the cell" is undefined for two block assertions, and the code that is supposed to catch a split is already taken (High)

**Walk.** Document: `shell: [{box:[0,0,0,7,6,7], block:"blast_furnace"}]`,
`cavity: [[1,1,1,6,5,6]]`, compact `bays: [{"id":"mouth","box":[3,1,0,4,3,1],
"expect":{"block":"iron_bars"}}]` — the mouth sits on the shell's `z=0` face.

1. `shell` minus `cavity` is six face slabs, not a cuboid. `Bay.cells` is one `Cuboid` (R-06; `09`
   §3), so the synthetic bay cannot exist as one bay; §12.4 says only that "the compiler never
   splits a **named** bay", which silently licenses splitting a synthetic one — into six pieces that
   all carry the single content-derived id `#box@0,0,0:7,6,7`. Six bays with one id is a `build()`
   IAE by B-2, and `10` §3 says "nothing in this unit throws for a document". No `SC-DOC` code
   covers it.
2. The mouth is a later stage than shell, so the mouth's cells are owned by the shell bay. §12.4:
   the write "merges into the bay that owns the cell". A merge of two *predicates* is not defined:
   under `09` a bay has exactly one `Expectation`; under §6.5 a bay's `Requirement` is a list, so
   the merge would add a second check asserting `iron_bars` over cells the shell's check asserts are
   `blast_furnace`. Both must `MATCH` → permanently `Failed`, with no finding explaining that the
   document contradicts itself. The alternative reading — the mouth takes the cells — silently
   splits a synthetic bay, which §12.4 permits by silence.
3. Now a `fills` entry on one door cell, written *after* `bays` in the file. The staged order
   (`shell → cavity → fills → bays`) makes the fill apply **first**, so the door bay takes the cell
   and the fill's `smooth_stone` assertion loses it without a finding: the fill's synthetic bay
   simply shrank, and `Coverage.CUBIC` compares each bay's checks against that bay's own
   already-reduced `cells`, so both bays pass. Read as file order, the same document means the
   opposite. §12.4 asserts both ("a later write" *and* a fixed stage order) and the two readings
   never coincide.

**Second defect in the same clause.** The synthetic id is derived from the source box, not the cell
set: adding a `mouth` bay changes the shell bay's cells while its id, and therefore its
`ClaimKey(owner, siting, bay)` (`09` §4.1), `MatchFacts.bays` entry and cache identity, stay
identical. "Content-derived" is false precisely when the document is edited, which is when identity
matters. And `SC-DOC-0503` is `EmptyCuboid` in `10` §4.4 (`min == max`, `min > max`), so one code
now means both "empty box" and "cannot be one cuboid" — I-7 requires a code to have one meaning.

**Violated.** R-06, R-09, R-07; I-7; `10` §4.5's doctrine ("Equal `as` is refused, not tie-broken");
§12.1's losslessness claim, since lowering is a partial function on documents.

**Why the proof fails.** §15.3 D8 is recorded **Fixed** with "named bays whole, content-derived
synthetic ids", but the proof only covers the *named* case; every cell that two stages both claim is
left to a "merge" whose type does not exist (a bay has predicates, not cell values). The one code
allocated to overlap resolution is spent on a different condition.

**Minimal fix.** A stage-pair precedence table where every (stage, stage) pair with a non-empty
intersection is either an ERROR naming both sources or a defined union — and a write over an
*occupied* cell is always the ERROR, because two block assertions over one cell have no merge. Give
the clash its own codes (`SC-DOC-0510` later stage over an occupied cell, `SC-DOC-0511` duplicate
synthetic id). Keep `SC-DOC-0503` for `EmptyCuboid`. Lower a holed box to one synthetic bay per
maximal cuboid with id `#bay@<digest of the final normalised cell set>`.

---

## D6 — Schema 2 is not lossless and its compact→canonical expansion is not a function (High)

**Canonical constructs with no compact spelling** (`10` §4.1's field table against §12.3's keys):

- `bounds`. Compact derives it from the content, so a blueprint whose bounds are larger than its
  bays (legal: B-1 only requires containment, and `10` §4.4 `SC-DOC-0501` exists to police the gap)
  cannot be written. `SC-DOC-0501` is therefore unreachable for every compact document — the field
  the error is about does not exist.
- `plans`. `CheckPlan(failure, budget, stop, pinned)` has no compact field; `checks[].stage` yields
  `FIRST`/`HOLD`/no-cap/unpinned and nothing else. `failure: AGGREGATE` changes `Spend` and the
  bundle, and `pinned` changes evaluation order by declaration (`09` §4.8, `02` §4). A compact
  document cannot say what it means.
- `extensions`. Absent from §12.3 entirely, yet §12.5's capture tool writes
  `extensions["cumulus:capture"]`. The capture tool's own output has no compact spelling, so the
  round trip in §12.5 and the "both profiles lossless" claim in §12.1 cannot both hold.
- `segments` / `assembly`. No compact spelling at all; compact is a strict subset. "Lossless" in
  §12.1 can only mean "canonical → canonical", which is not a property worth stating.

**Compact constructs whose expansion is ambiguous:**

- `bays[].expect` carries no `stage`, while `checks[].stage` is mandatory and every `PlanUnit` must
  have one (§6.1). The same check can also be spelled as `checks: [{"bay": "mouth", "expect": …}]`
  (§12.3 says a bay "may reference" the structure-level form). Nothing says which stage the
  `expect`-derived check lands in (PROBE runs it as a cheap reject, FORM lets claims depend on it),
  and nothing says whether the two spellings are one check (deduplicated by what key?) or two (in
  which case the bay's CUBIC union counts the same cell twice and `Requirement.checks` holds a
  duplicate predicate with no `SC-CHK` code for it).
- `derive[]` has no `order`, while `DerivationSpec` requires a unique non-negative order (B-8,
  `SC-DOC-0206`) and `10` §4.3 sorts `derivations` by `order` for canonical equality. If order is
  array position, then reordering the array is a *different* document, yet §4.3's normal form sorts
  `order`-insensitive arrays only and the array is exactly `order`-sensitive — two documents that a
  canonicaliser must keep distinct are interchangeable to every reader of the set.
- `derive[].key = "mymod:capacity"` is not declared in the example's `state[]`. Expansion must
  either mint a key (forbidden by §6.4, "construction only through `StateKeys`") or report
  `SC-CTX-0101`; §12.3 picks neither, so the flagship example has an undeclared output.

**Violated.** R-09; I-12; §12.1; I-7 (one code per meaning is meaningless if one document has two
meanings).

**Why the proof fails.** §15.1 D9 is recorded **Fixed** on "one `Spec` family". One family is not one
normal form: `Spec` is a union of nine optional fields whose meaning depends on `kind`, used for
three different unit species (check, computation, key), so "every canonical field has a compact
spelling" was never checked field by field, and no rule fixes the expansion where two spellings
overlap.

**Minimal fix.** Add `bounds`, `plan` (per stage: `failure`, `stop`, `budget`, `pinned`), `inputs`
and `extensions` to compact. Define `bays[].expect` as sugar for exactly one `checks[]` entry with a
mandatory `stage` and id `#expect@<bayId>`, and make a second check naming that bay
`SC-DOC-0514`. Require explicit `order` in `derive[]` (or derive it from `key` and declare the key).
State the losslessness claim as the pair of functions it actually is: canonical → canonical is
identity, compact → canonical is total, canonical → compact is partial with a published set of
refused fields.

---

## D7 — `Merge`'s two law-defaults are unenforceable, and the cache gates on the wrong law (High)

**Scenario A (the fold is partition-dependent).** `Merge<Long> AVG = (a, b) -> (a + b) / 2;` — a
lambda, therefore `associative() == true` and `commutative() == false`. The same wall spans three
sections and folds to one value; move the siting one block so it spans four and, with identical
block content, the folded value changes. Section boundaries are chunk-aligned and the plan's
`region` is in local cells, so the *verdict* of a blueprint depends on where its origin falls in the
world — review-1 D1's failure mode, returning through `Merge` after being closed in scheduling.
§7.2's defence ("the order is canonical, so the result is deterministic even for a non-commutative
merge") answers a question nobody asked: determinism is preserved, partition invariance is not, and
`Coverage.CUBIC`/`ReadScope` are cell-based while the fold is section-based.

**Scenario B (the cache admits the unsound merge and refuses the sound one).** §9.6: "consulted only
for units whose `Merge` is `commutative()`". `CacheKey` already carries the section's
`contentDigest`, and §7.2's fold position is fixed by `SectionKey`, so a cached partial sits in its
canonical slot: reuse is sound for any pure `combine`, associative or not. Commutativity is neither
necessary (an associative non-commutative merge such as section-order concatenation is perfectly
cacheable) nor sufficient (`AVG` with a hand-written `commutative() { return true; }` passes the gate
and double-counts across attempts). A lambda can never pass the gate at all, since the default is
`false` and a lambda cannot override a default — so the only merges that are cacheable are
anonymous classes, and the only ones that *should* be refused are cacheable too.

**Violated.** P9; I-4; R-08; `22` §8 ("A `Merge.commutative()` promise enables parallel …"), whose
promise §7.2 makes unnecessary by fixing the fold order.

**Why the proof fails.** A law declared by a default method is an assertion by the implementor, and
the design provides no registration point (D4) and no test that could contradict it. `commutative()`
was carried over from the parallel-commit model of `22` §5; once §7.2 fixed a canonical fold order,
parallelism needs no law at all, leaving `commutative()` doing only the cache gate — where the
relevant law is purity and digest match, not commutativity.

**Minimal fix.** Delete `associative()`/`commutative()`. A `SECTION` key's fold is a closed
`FoldSpec { SUM, MAX, MIN, PRODUCT, UNION, INTERSECTION, SET, SECTION_ORDER_FIRST }`, whose algebra
the kernel knows; a `CUSTOM` fold is admitted only with a `kernelTest` property check registered
through the same catalog that owns keys, and is never cacheable. The cache gate becomes
`(unit, section, contentDigest)` alone.

---

## D8 — `Check` cannot be executed through any declared signature, `PlanUnit` cannot legally be sealed, and the subject token cannot express the pair the door case needs (High)

**(a) The unit hierarchy does not compile as stated.** `sealed interface PlanUnit permits Check,
Computation`. `Check` is a record — fine. `Computation<T>` is declared
`interface Computation<T> extends PlanUnit` with no `sealed`/`non-sealed` modifier, which is a
compile error for a permitted subtype. Adding `sealed` is impossible: `Computation` is the addon
extension point (`Catalogs.computations`, R-10), so every addon implementation would need naming in
`permits`. Adding `non-sealed` makes `PlanUnit` open, so no `switch` over it is exhaustive and the
sealing buys nothing. §14's "compile-level proof first … a signature that cannot express the case
fails the build" rests on a hierarchy the document forbids.

**(b) Nothing runs a check.** `Check` has components, a compact constructor and `reads()`. The
engine (§7.1: "`Check` units have no outputs, so they sit at the end of their phase") must call
`focus.take(ctx)` and hand the result to `expectation.test(...)`, and neither context type is
declared here: `FocusContext` appears only in §6.3's method list, `ExpectationContext<T>` nowhere at
all, and `07` §3 declares the non-generic `ExpectationContext` with `cells(): Cuboid` and no
`read(StateKey)`. So the only member connecting a focus to its expectation is the constructor's
comparison, and a `List<Check<?>>` plan cannot be executed without a capture-generic
`static <T> Reply eval(Check<T> c, …)` that no document declares. Two further consequences of the
missing `read` on the `07`-shaped context: an `Expectation<T>` cannot see a `StateKey` at all, so
`Focus.state(key)` cannot feed anything but its own `Outcome`; and `ExpectationContext.cells()` is a
non-null `Cuboid`, which a `state` focus cannot supply (§6.5: `state(key)` decides no cells; `09`
§3 refuses an empty `Cuboid`, and `10` §4.4 spends `SC-DOC-0503` on exactly that).

**(c) The subject question, answered by the type system.** `Expectation<BayView>` does **not**
type-check against a `Focus<DoorView>`, at either level. At compile time both are invariant, so the
`Check<DoorView>` constructor rejects it. At run time
`focus.subject().equals(expectation.subject())` compares `Class` tokens, which are equal only when
`T` is the same class — i.e. exactly the case the compiler already enforced — so the check is
redundant, **except** for the two ways to defeat it: both sides declaring `Object.class` (then any
pair passes, including a `block` focus with a `state` expectation), or a bay-typed expectation that
must widen `T` to `Object` to be usable at all. §12.2 makes this structural: `Spec` has one
`@Nullable ClassRef subject` for focus *and* expectation, so a data-declared check cannot express a
door focus with a bay-subject expectation even if the Java types allowed it. R-13's residual row
("Java erasure at `List<Check<?>>` is contained by the DESIGN resolver") is therefore false in both
directions: the resolver can only compare one token with itself.

**(d) A computation's `Missing` has no honest locator.** `Outcome.Missing(FocusDesc, FindingBundle)`
with `FocusDesc(code, locus, keys, labels)` and `Locus.SUBJECT = (frameId, keyId)`. A computation has
no focus and no frame: frames are created at publish (§8.3), after the unit runs. So a `Missing`
from `compute` must invent a `frameId` or name the output key — a cell that, by construction, has no
value and no world position. `07` §4.1's `UNREADABLE` invariant ("the unreadable cell is the repair
target") is then unsatisfiable, and `15` §5's provider shape (`if (finding.primaryLabel() == null)
return List.of();`) makes every such finding unrepairable. The code family is wrong too:
`SC-FOC-0103` (zero present sections, §7.2) is minted from a family whose meaning is "subject absent
/ ambiguous / unreadable" (§6.6), not "no section contributed".

**Violated.** R-13; I-13; R-07; I-14; §6.2, §6.6, §7.3, §12.2.

**Why the proof fails.** §15.2 D4 and D7 are recorded **Fixed** on "the constructor plus the DESIGN
resolver" and "`Missing` → `unreadable` with `SC-FOC` findings". Both mechanisms were placed at a
seam that does not exist: the constructor is redundant with the compiler for the case it can catch
and vacuous for the case it cannot, and the resolver has one token where the design needs two. The
`Outcome` proof covers the *channel* and not the *addressing*.

**Minimal fix.** (a) `PlanUnit` is `sealed` over `Check` only, and computations are a separate
`sealed`/open pair with a declared `Executor` interface; or drop the seal and state the switch
requirement. (b) Declare `CheckDriver`: `static <T> Reply eval(Check<T>, FocusContext, ExpectationContext<T>)`
on the engine, and define both contexts in `23` (or move `07`'s to generic with `read`). (c) Two
subject tokens on `Spec` (`focusSubject`, `expectSubject`) and a declared bridge rule, or delete
`subject()` from both interfaces and let the focus's type parameter be the only contract. (d)
`Missing(FailureDesc)` with `FailureDesc permits SubjectDesc, CellDesc`; a `CellDesc` labels from the
key's `KeyDecl` provenance (authoring span), and the code family is `SC-STATE`.

Also in `Check.reads()`: `ReadScope.entities` is a `List<EntityWindow>`, so
`focus.reads().union(expectation.reads())` has no defined normal form (duplicate windows, windows of
different types that overlap). `ReadScope` feeds DESIGN comparisons and dirty-set intersection, so
its union must be total and canonical or the determinism claim in §9.6 has an unordered input.

