# Round 3B — types and data

Attack on `22` r5 §0/§6–§8/§10/§12, `24`, `23`, their consumers (`01`,`04`,`07`,`08`,`09`,`10`,`12`,`13`,`16`,`17`,`18`,`21`) and
`kernel/**`. Ten defects, most damaging first. Section refs are to `22` unless prefixed.

## 1. Scope and method

Whether the declared types express the cases the prose claims: consumers that must switch on
`Footprint`; the `ContextKey`/`Merge`/`Retention`/`whenAbsent` distinction; `Focus`/`Expectation`/
`Bindings`; the schema and its lowering; `MatchResult`. I read the four canon files end to end, then
each document `24` §9 names, then the same types in `src/kernel/java`. Code gaps registered as
`GAP-*` in `status.md` are not re-listed; where a *document* contradicts a claim nothing registers,
that is. `zh/**` was not read (§5).

## 2. What I checked

Every `Footprint` switch site (build rules `09` B-9/B-10 and R1/R3/R4, `ClaimPlanner`, `ClaimMatrix`,
`SC-CLM-0104`/`SC-BAY-0108`, the remedy pre-state, `10` §4.1, `21` §3.3, `13`'s table); `Bay`'s
components across all fourteen documents and `blueprint/**`,`claim/**`; the key/draft/store trio in
`12`,`17` §2 and `context/**` plus `PhaseScenarioTest`; `Focus<T>` in `§6.2`, `01` §E, `07` §3/§4.6,
`predicate/Focus.java`; `MatchResult` in `04`,`11`,`13`–`16`,`18`,`22` §9–§10, `23` §2–§4,
`engine/**`; schema 2 in `10` §4.1/§4.3/§4.5, `21` §3, `§12.1–12.3`, `17` §3; provenance per `19` R4.

## 3. Ranked defects

### B1 — A section unit can read aggregate state: P6's "compile-level proof" is not one (Critical)

**Scenario.** `compute(ComputationContext)` (`§6.4`, `context/Computation.java:27`) and
`interface ComputationContext extends AggregateContext` (`context/ComputationContext.java:3`). A
`SECTION` computation writing `ctx.read(VOLUME)` compiles; the only phase marker is `@Nullable
SectionContext section()`, whose "non-null iff `phase == SECTION`" is runtime, while the read it was
meant to bar comes from `AggregateContext` (`context/AggregateContext.java:11`).

**Violated.** `§7.3`, the stated mechanism of `P6`; falsifier 8 (`§14`, "compile failure is the
test"); `§15.4`'s ruling "phase by convention → `SectionContext` has no `read`".

**Why the proof fails.** `SectionContext` indeed has no `read`, and `PhaseScenarioTest:39-42`
passes — but no unit body ever receives a `SectionContext`. The proof is about a type that is never
the entry point. Same hole on the write side: a section-scoped `ContextDraft` exposes `put`, so a
section unit can `put` a single-writer key once per section — silent when the values agree,
`SC-CTX-0107` when they do not.

**Minimal fix.** Split by phase: `compute(AggregateContext)` and
`compute(SectionContext, SectionDraft)`, `SectionDraft` exposing only `contribute`; delete
`ComputationContext`; falsifier 8 becomes the compile failure of `ctx.read(…)` in that body.

### B2 — `Footprint` has one type but two frames, and "known when" is prose (Critical)

**Scenario.** `sealed interface Footprint permits Box, Bound, Declared, None` (`§6.5` line 316,
`24` §4) is to carry a `resolve` (`24` §9 step 2). `Box`/`Declared(cells)` are **local**
(`09` B-12 forbids a siting in a blueprint; `ClaimPlanner` calls `siting.toWorld`,
`engine/ClaimPlanner.java:112-140`); `Bound` is the union of bound footprints, i.e. **world** cells
(`§5.1` puts the footprint in `OccupantRef`'s identity; `08` §4.3 resolves it from
`BayBinding.subjects()`). One `resolve` returning an untagged `CuboidSet` lets a caller translate
uniformly: `Bound` doubled, or `Declared` never, then written as valid rows in the wrong frame.

**Violated.** R-11's one-matrix claim; `08` §4.3's per-scope resolution table; B-12; I-4.

**Why the proof fails.** `24` §4's "Known when" column states *when*, but no case and no member
distinguishes local from world, and `24` §9 step 2 never fixes `resolve`'s signature — enforcement
is prose plus a code comment, the pattern round 2 struck down elsewhere.

**Minimal fix.** Make the frame part of the case: `Box`/`Declared(local)` resolve through `Siting`,
`Bound` through `BayBinding`; replace one `resolve` with `@Nullable CuboidSet knownLocalCells()`
and `CuboidSet resolveBound(BayBinding, Siting)` plus `KnownAt knownAt()`, so DESIGN code cannot
ask a `Bound` for cells.

### B3 — `Focus<T>` is declared three times and `§6.2`'s version cannot evaluate R4/B-9/B-10 (Critical)

**Scenario.** `§6.2` declares `Focus<T>` = `take`,`subject`,`reads`,`desc`. `01` §E and `07` §3
declare it with `DecisionKind decisionKind()` and `CuboidSet staticDecisionCells(Cuboid)` (and
`describeMissing()`); `predicate/Focus.java:14-21` matches `07`. But `09` B-9/B-10, `§6.5` R4 and
`13`'s `SC-BAY-0107` row are stated over "the checks' **static decision cells**", and `07` §4.6 says
a dynamic kind (`occupant`,`entity`,`nested`,`context`) "has none" — a fact only that member
carries. `07` §4.6 also says `block`'s kind "depends on whether its cells cover the bay", so the
kind is a function of (focus, bay) and cannot be a parameterless member; its three names are
`Coverage`'s three names.

**Violated.** R-13 (same-`T` pairing is claimed as *the* mechanism); `09` B-9/B-10; I-7; `§6.5` R4.

**Why the proof fails.** "The compiler enforces the same `T`" proves pairing only. That a
`EVERY_CELL` bay has every cell decided is a claim about a set no member of `§6.2`'s `Focus`
computes; each document needing it declares its own `Focus`, so `22` alone cannot write B-10.

**Minimal fix.** Restore `decisionKind()` + `staticDecisionCells(Cuboid)` on `§6.2`'s `Focus`,
delete `desc()`, rename `DecisionKind` so it shares no value name with `Coverage`, and require every
shipped focus to declare a constant decision set (empty for dynamic kinds) so `EVERY_CELL` +
`entity` is refused at build rather than vacuous.

### B4 — "May this call write" is not type-level: `Plan` is forgeable and the result contract is stated twice (High)

**Scenario.** `23` §2 `settle(MatchRequest, CheckResult.Plan, Supplier)`, §3.1 step 3 validates only
snapshot and revision before `ledger.settle(new SettleRequest(owner, siting, rows, 41))`. Nothing
binds a `Plan` to its blueprint/siting/owner and no document fixes `Plan`'s construction, so
`new CheckResult.Plan(anyRows, 41)` registers arbitrary cells. And `check` returns `CheckResult`
(`23` §2) while §4 says `MatchResult.Admitted`, and `CheckResult` already means the per-check reply
(`§6.2`, `09` §3, `01` §E, `predicate/CheckResult.java`). `§10.4` lists `Admitted` without
`planned`/`ledgerRevision` and its `permits` clause omits `Dropped`, which `§9.2` produces, so a
`switch` written from it is not exhaustive. `§9.1` still carries `Stage` in `MatchRequest` with one
`check` reaching `MatchSettler` (`§10.3`), so the document of record still selects the write by a
field.

**Violated.** `23` §2/§7; R-14; I-7; `§10.4`'s completeness.

**Why the proof fails.** "Which method was called" holds for the two *names*, not for the value that
carries the write; and `Plan` is provenance-free data, so `settle` cannot tell a plan it produced
from one a caller typed.

**Minimal fix.** `Plan` gets one non-public factory plus `PlanDigest(blueprint, siting, owner,
closureDigest)`, verified in `settle` (`SC-CLM-0005`, promoted from `23` §8); rename the
entry-point result or make it a second sealed family; update `§9.1`/`§10.3`/`§10.4` (drop `Stage`,
add `planned`/`ledgerRevision`, permit `Dropped`).

### B5 — P5's hole is closed only for a `Refused` section; the hole state has no producer (High)

**Scenario.** A `SECTION` unit's region spans three `Present` sections; B's run returns
`Outcome.Missing` (one cell `DENIED`; the slot is present, so `§7.2`'s refusal clause does not
apply). B contributes nothing, A and C do, and the fold is a short sum with B's bundle dropped —
`compute` returns one `Outcome` for the unit and the declared `ContextDraft` (`§6.4`, `12` §3) has
only `put`/`contribute`. If both sections return `Missing`, no contribution exists and `whenAbsent`
fires, handing the check the identity value the design forbids.

**Violated.** P5; `§0.4`/`§7.2` bullet 4 ("a short sum is unrepresentable"); R-07.

**Why the proof fails.** The proof covers `SectionSlot.Refused`, which the kernel can detect — but
only a *declared* operation lets it mark a key defeated, and neither `§7.2` nor `12` §4.3 names
that call. The code has both missing calls with exactly this semantics
(`context/ContextDraft.java:71-92`, `refuse`/`declined`); no document mentions either, so the
driver's obligation is unstated and the short fold is representable today.

**Minimal fix.** Declare the hole: the section driver calls `draft.refuse(key)` for every key of a
refused slot **and** for a run that returned `Missing`; `declined(key)` (all readable, no partial)
is the only route to `whenAbsent` and is refused once any `refuse` is recorded; both publish
findings (`SC-FOC-0301`/`0302`); `§7.2` adds "a fold over fewer than `region ∩ sections` inputs is
`Missing`, never a sum".

### B6 — Three codes mean two things each (High)

**Scenario.** (a) `SC-BAY-0107`: `13` line 338 = the `EVERY_CELL` coverage shortfall (so do `09`
B-10 and F-7); `09` §4.1 R3, `08` §4.3, `§6.5` R3 and `24` §5 R3 give the same number to
`ONE_SUBJECT`'s bound-footprint rule, while `24` R4 gives the `EVERY_CELL` shortfall
`SC-CHK-0106` (as `13`'s own `SC-CHK-0106` row does). (b) `SC-CLM-0003`: `13` line 334 = INFO
"claims were returned unused"; `08` §4.6 = ERROR/SETTLE "a `SHARED` bay meets another owner's
`EXCLUSIVE`" (agreed by `08` §9, `23` §8) — so every real `0003` also trips `SC-DIA-0002`.
(c) `SC-DOC`: `13` (`0001` version, `0002` required field, `0003` missing segment) vs `10` §4.4
(`0101`,`0106`,`0302`) for the same meanings, with `21` and `17` §3 citing `13`'s numbers while `10`
owns the family. `13`'s own falsifier then cites `SC-BAY-0101`, which no table allocates.

**Violated.** `13`'s stability rule ("a code is never renumbered, never re-pointed"); I-7; R-07.

**Why the proof fails.** `13` calls its table both "the shipped allocation" and "representative",
so neither table can refute the other, and no rule maps to exactly one code.

**Minimal fix.** Make `13` a sample and give each rule one owning row: `0107` = R3 only, `0106` =
R1/R4; `SC-CLM-0003` = `08`'s meaning (renumber `13`'s INFO row); allocate or replace
`SC-BAY-0101`; renumber `13`'s `SC-DOC-000x` rows into `10`'s block.

### B7 — The `Footprint`/`Sharing` migration is incomplete in the documents `24` §9 names (High)

**Scenario.** Survivors: `08` §1 (`Bay.mode()`, `Bay.scope()`), §2 (`ClaimRow` = `(cell, mode,
owner, bay)`), §3 (`Footprint permits Owned, Occupied, Declared, Audit`, field `Sharing mode`, and
a `Siting siting` its own §2 says the row does not have), §4.1, §5, §8
(`Bay.id()/cells()/mode()/scope()`) — while §4.3's table already says `Box`/`Bound`/`None`.
`09` §3 lines 58–63: `permits Footprint.Box, Footprint.Bound, Footprint.Declared, Footprint.None
{ record Owned() … Occupied() … Audit() }` — permitted types undeclared, declared types
unpermitted, so the flagship hierarchy does not compile. `17` §2 uses
`Footprint.owned()`/`occupied()` while `17` §3's JSON says `"box"`/`"bound"`. `21` §3.3 line 193
says "`coverage` is `cubic` (default), `subject` or `presence`" and the next sentence says
"`every_cell` with `none` is `SC-CHK-0106`" (its own JSON at line 183 says `every_cell`).
`10` §3 line 89 `FootprintRef(Kind kind, …)` with `ScopeKind { OWNED, OCCUPIED, DECLARED, AUDIT }`,
and §4.1 line 207 `{id, box, coverage, mode?, scope?, description?}` defaulting "`footprint` to
`bound`". `16` line 274 and `18` line 131 keep `mode` (neither is in `24` §9's list).

**Violated.** `24` §7; R-09; I-7; `01`'s closed-vocabulary gate (contradicted by `08`/`09`/`10`).

**Why the proof fails.** `24` §9 lists documents, not fields; each file was migrated where prose
mentioned new names and left where a code block did. Nothing compiles the set, so `09` §3 is
undetected.

**Minimal fix.** Migrate `08` §1/§2/§3/§4.1/§5/§8, `09` §3/§5, `16` §4, `17` §2, `18` I-10,
`10` §3/§4.1, `21` §3.1/§3.3/§5, and add `16`/`18` to `24` §9's list.

### B8 — `ContextKey` keeps a public constructor, so an unregistered twin equals the registered key (High)

**Scenario.** `record ContextKey<T>(id, type, retention, @Nullable Merge<T>, @Nullable T whenAbsent)`
with `equals` narrowed to `(id, type)` (`context/ContextKey.java:8-37`); `ContextKeys.of` is the
only `whenAbsent` check and `register` only refuses a second id (`context/ContextKeys.java:14-32`;
`CatalogsDraft` also offers `putKey`, a public path with no refusal, `:26-40`). `17` §2 — the
example whose equality with `17` §3 a test asserts — builds keys with `new ContextKey<>(…)`. Mint
the twin with `merge = null` and call `draft.put(twin, 7)`: the verb check reads the instance's
field so `put` is legal, the frame map is keyed by the narrowed `equals` so twin and registered key
are one entry, and `valueOf` returns `state.puts` before the contributions
(`context/ContextDraft.java:145-155`) — the single write silently outranks the fold.

**Violated.** `§6.4`/`12` §3 ("built with `ContextKeys.of`"; "equality is `(id, type)`"); `12` P3;
`§15.4`'s "key identity" closure, which rests on `of`/`register` existing.

**Why the proof fails.** P3 proves registration is unique, not that a usable key was registered:
the handle is a public record, equality ignores the declaration, and the validating factory is
optional — with the flagship example as the counterexample.

**Minimal fix.** `KeyDecl(id, type, retention, merge, absentWhen)` owned by the catalog;
`ContextKey<T>` becomes a final class (or a record with a private canonical constructor) whose only
factory is `ContextKeys.register(draft, id, type, retention, merge, whenAbsent)`; delete `putKey`.

### B9 — The compact schema spells one field twice and still claims a refused one (Medium)

**Scenario.** A section unit's domain is `bay` in `§12.3`, `region` in `17` §3 and `21` §3/§3.5, and
`10` §4.1 line 230 lists both (`inputs?, bay?, region?`) with no precedence and no synonym rule;
`21` §3.5 makes `region` polymorphic (bay id / box / segment), so two documents differing only in
spelling cannot be compared by the canonicaliser and the fold domain `§6.4` puts in
`Computation.region()` has two document routes. Separately `§12.1` claims `bounds`, `plans`,
`segments`, `assembly`, `extensions` "all have compact spellings", while `10` §4.1 (line 228) marks
`plans` a refused key and `23` §7 removes `stage` from `Check`, so a plan is not a document concept.

**Violated.** R-09/I-12 (`§12.1` "Expansion is a function"); `10` §4.3's canonical equality;
`§15.4`'s "schema losslessness".

**Why the proof fails.** `§12.1` was not revised with the rest of §12, and §4.1's field table grew
without deciding which key is real; neither claim names a conversion direction, which is what
"lossless" would have to mean.

**Minimal fix.** Keep `region` (the member's name) and make `bay` a refused key with an upgrade
note, or vice versa with a precedence rule; rewrite `§12.1` as the functions it is —
canonical→canonical identity, compact→canonical total, canonical→compact partial with a published
refused set (`plans`, `segments`, `assembly`).

### B10 — Lowering keeps no authoring range for a spec, so `19` R4 stops at bays and seams (Medium)

**Scenario.** `10` §4.5 step 6: "each bay and seam and each `Spec` carries `DocumentProvenance(…)`".
`Spec` (`10` §3) has no span component and holds `kind` as a parsed id with `args` a child node, so
the node's range is gone once the record exists (`10` §4.2: `write` emits span-free nodes). On the
blueprint side only `provenance()` and `sourceOf(NamespacedId bayOrSeam)` exist (`09` §3), and with
`stage` removed from `Check` (`23` §7) a check derived from `bays[].expect` has a generated id and
no `sourceOf` key.

**Violated.** `19` R4; R-07 (`/cumulus explain` points at authoring positions); `10` §4.5 step 6.

**Why the proof fails.** R4's slot type is `NamespacedId` bay-or-seam, while the units that need a
range (`checks[]`, `computations[]`, `derives[]`, an `expect`-derived check) are neither — and the
one document stating the per-spec obligation is the one whose `Spec` cannot hold it.

**Minimal fix.** Add `@Nullable SourceSpan span` to `Spec`/`KeyDeclaration` (or a
`UnitRef(unit, span)` on `Check`/`Computation`/`DerivationSpec`), extend `sourceOf` to every
declared unit id including derived ones, and say in `10` §4.5 that a spec's range comes from its own
node, not from `args`.

## 4. Checked and cleared

- `§6.4`'s `Computation.region()` plus `12` §4.3 close round 2B-D1's fold-domain gap; `§12.2`'s
  `@Nullable List<KeyRef> inputs` closes 2B-D3's data-path front.
- `SC-DOC-0503/0504/0505` now agree between `10` §4.4 and `§12.1` (2B-D5's `0503` collision is gone).
- `Merges` needs no associativity law in the current design: the fold order is fixed by
  `SectionKey` (`§7.2`) and caching is gated on `closureDigest` (`§9.6`), so partition invariance
  rests on the fold, not on `Merge`'s algebra — `§15.4`'s "`Merge` associative by contract" is not
  load-bearing, and `Merges.union` stays deterministic under the canonical order.
- `Requirement`'s `List<Check<?>>` erasure is a registered residual risk (`§2`, R-13); I found no
  new case it admits, and no combination of `Focus<T>`/`Expectation<T>` that compiles across
  mismatched subjects without an explicit unchecked cast.
- `Bay`'s component order agrees across `§6.5`, `09` §3, `10` §3 (apart from B7), and the move of
  `SC-BAY-0108` to SETTLE (`08` §4.3) matches `Bound`'s known-when.

## 5. Could not rule

- `zh/**` was not read, so every claim above is verified against the English set only.
- Which of `10`'s `SC-DOC` block and `13`'s rows is authoritative: `13` says the unit file owns its
  block (which makes `10` right) and also calls its own table the shipped allocation. Ranked as
  B6c rather than resolved.
- Whether `21` §4's cell→declaration lowering yields the ids `§12.4` and `17` §2 use for a
  `shell` minus `cavity` pair: `17`'s shell is not one cuboid and the two documents do not state
  whether the six faces and the `#box@…` naming agree. Lowering front, not types.
- `24` §8's two open questions (a `Declared` cell outside the bay; an entity claim after movement)
  stay registered; I found no new case in them and do not re-list them.
