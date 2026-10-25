# Round 3, review round 2 — front A (vocabulary, names and codes): the verdict table

Read-only on the design set; nothing outside this file was edited. Line numbers are the English
originals; `zh/**` and `_work/**` are quoted only as history.

## 1. Method

1. Read the contract (`round3-sweep-contract.md`) and the brief's Set A, then `19`'s third-pass
   table (`19` :39 R25 … :83 R32, :81 R30).
2. Read end-to-end: `01`, `08`, `09`, `10`, `13`'s registry block (`:322-379`), `17`, `21`, `22`'s
   §6/§12/§16.1, `24` §4-§6; and the claim-relevant parts of `04`, `07`, `11`, `14`, `16`, `19`,
   `20`, `23`.
3. Verified every claim by whole-word scans over the 26 English originals (not by hand-picking):
   `ClaimRow`, `AnchorRole`/`AnchorRoles`, `AnchorSpec`, `BindAnchor`, `ReadEnvelopeAware`,
   `footprint`/`FootprintRef`, `Focus`'s members (`take`/`subject`/`decisionKind`/
   `staticDecisionCells`/`reads`/`describeMissing`/`desc`/`subjectKind`), `ONE_SUBJECT` × `Box` ×
   `SC-BAY-0107`, `plans`/`claims` as document keys, `orientation`/`turns`/`upright`, the nine codes
   of claim 10, `SC-CLM-0001/0002/BPT-0002`, `SectionStamp`/`StepResult`/`BindingSite`/`CacheKey`/
   `closureDigest`.
4. Every non-VERIFIED verdict was re-read in place before ranking. No Minecraft/NeoForge fact is
   load-bearing here, so `sourcescout` was not bound.
5. **Verdict rule used.** `VERIFIED` = every place the claim names says the claim. `FAILED` = the
   claim is absent/wrong at a site it names. `NEIGHBOUR` = the named fix is present, but the same
   name/code/shape now disagrees in a place the fix did not reach (the brief's "round 1 fixed `A` and
   left `B` saying the old thing") — this front's dominant failure mode, 5 of 15 claims.

## 2. Verdict table

1 — VERIFIED — `08` :20 (`ClaimRow` = `(cell, sharing, owner, siting, bay)`), `08` :30-32 ("A
`ClaimRow` carries its siting … the settlement key `(owner, siting)` is read off the rows
themselves"), `08` :48, `01` :115, `16` :274, `22` :526, `24` :155.

2 — VERIFIED — `01` :137, `09` :37/:100, `17` :44/:49, `22` :91 (and `11` :174) all declare
`AnchorSpec(NamespacedId role, NamespacedId property, String value)`; `04` :157-158 uses
`AnchorRoles.FRONT`; `14` :136 declares `BindAnchor(NamespacedId role, LatticeKey cell)` and `14` :441
cites U5 as "the anchor role ids"; `AnchorRole` occurs as a type in no English original (only the
Prior column `01` :136 and the code name `DuplicateAnchorRole` at `10` :342, neither a type).

3 — NEIGHBOUR — the six-member set is identical in `01` :103, `07` :34-41 and `22` :209-212, but the
fix did not reach `22`'s own call sites: `22` :224 calls `ctx.withSubject(found.value(),
found.subjects())` under `run(FocusContext ctx)` (`:221`), while `07` :66-72 declares
`CheckContext.with(V, List<OccupantRef>)` and `09` :75-82 shows the same body as `run(CheckContext
ctx)` + `ctx.with(...)`; and `22` :641 has `SpecResolver` "checks `focus.subjectKind ==
expectation.subjectKind`", a member no `Focus` declaration has (`07` uses `subject()`).

4 — NEIGHBOUR — `SC-BPT-0002` is right in `09` :294 (B-7) and `23` :130, and `SC-CLM-0001` = "two
`EXCLUSIVE` claims … name the same cell" in `08` :215 and `13` :332; but the fix narrowed `08` :215 to
*"of different owners"* and left `08` :144-145 assigning an internal (one owner, one siting)
`EXCLUSIVE` overlap to `SC-CLM-0001` — a case the same file's exclusion (`08` :30-32, :64, :113-114)
makes undetectable at the ledger. The claim's own anchor is also a slip: `08` states `SC-BPT-0002` in
§4.2 (:144) and §4.6 (:222), not §4.3.

5 — NEIGHBOUR — `09` :294 (B-9) names both codes exactly as claimed and `09` :295 (B-10) keeps
`SC-CHK-0106`; but dropping B-9's "decision kind matches the coverage" clause left `09` :554 (F-1:
`ANY_SUBJECT` + `EXCLUSIVE` + `Bound` must make "`build()` throw B-9 naming `SC-CHK-0106`") and `08`
:187 ("`ANY_SUBJECT` … cannot be `EXCLUSIVE` with a non-`Declared` scope (`SC-CHK-0106`)") asserting a
refusal that R1-R4 no longer state (`24` :96-99, `09` :185-188, `22` :330-334) — while `22` §16 Q6
and `08` :324 still keep the very question open.

6 — VERIFIED — `17` no longer pairs `ONE_SUBJECT` with `Box` (`17` :333 uses `Footprint.Bound`), `09`
:554's F-1 is the `ANY_SUBJECT` case, and no English original attributes `ONE_SUBJECT` + `Box` to
`SC-BAY-0107` (`13` :341 is R3-only); `22` :895-897 (§16.1 item 17) is the item asking whether that
combination should be refused.

7 — NEIGHBOUR — `10` :200 and :231 refuse `plans`, `21` :109/:45/:294/:375 contain neither key, and
`22` §12.1 (:621-628) never lists them; but `01` :163's `DocumentProfile` row still says "`COMPACT`
accepts omitted segments/assembly/plans/claims and top-level bays/fills/layers" — both retracted keys
are kept alive in the glossary, and the row's omittable set disagrees with `21` :45's
(`segments`, `assembly`, `orientation`, `context`, `computations`, `checks`).

8 — NEIGHBOUR — `10` :199 (`orientation` required in canonical, "`{"front": …}` … with optional
`turns` and `uprightOnly`"), `10` :233 (compact default `{"front": "north"}`) and `10` :452's sketch
make the shape one for schema 2, and `17` :126 / `22` :678 use it; but `21` :126 and :204 still say the
profile has only `front` — "There is no turn list in schema 2" — so the claim's "with optional
turns/upright-only in … `21`" is false and the two files still disagree about a field of the same
schema version.

9 — VERIFIED — `17` :87-91 states it is the compact profile and that bare names resolve inside the
implicit `main` (`mouth` compiles to `main:mouth`, `10` §4.5 step 4, Q-D3); its JSON uses bare `bay` /
`region` names accordingly (`17` :108, :125-126).

10 — VERIFIED — `SC-DOC-0521` `10` :341; `SC-ENG-0104` `13` :359; `SC-MC-0505` `16` :529-533 (16
§8's `SC-MC-*` registry); `SC-MC-0601` `13` :379; `SC-SPI-0002` `13` :375; `SC-CTX-0101/0102/0202/0301`
`13` :360/:361/:366/:367.

11 — VERIFIED — `08` :81-84 declares `ClaimPlan.Rows(rows, overlaps, staticFootprint, ledgerRevision)`
with `resolve(Collection<BayBinding>)` and `ClaimPlan.Invalid`; `08` :267-270 calls exactly
`plan.staticFootprint()` and `plan.resolve(List.of(...))`; `ClaimSet` is minted once, `08` :282,
inside the `Settled` arm.

12 — VERIFIED — `09` :365: "`Sharing` names two semantics, not two levels" (no "three semantics"
survives in an English original).

13 — VERIFIED — `01` §I (:206-215) has no `ReadEnvelopeAware` row; the name survives only in `19`'s
historical rows (`19` :39 R25, `19` :83 R32) and `_work/**`, as contract §A.5 allows.

14 — VERIFIED (with a ranked neighbour, §3.5) — `01` :119-121 carry the `ClaimMatrix`, `SettleRequest`
and `ClaimView` rows; `10` :263 explains `FootprintRef`.

15 — VERIFIED — `08` :219-220 carry `SC-CLM-0005` (plan/request mismatch, ERROR, SETTLE) and
`SC-CLM-0006` (rows returned unused, INFO), matching `13` :336-337 and contract §D.15/§D.16.

## 3. Neighbour damage (ranked)

1. **`22` §6.2 cannot compile against the declaration it was just given** (claim 3). The rewritten
   `Focus` block is correct, but the body below it (`22` :221-229) still takes `FocusContext` and calls
   `ctx.withSubject(...)`; `07` :66-72's `CheckContext` has `with(V, List<OccupantRef>)`, and `09`
   :75-82 (the same body) uses it. `22` :641 additionally reads `focus.subjectKind`, which no `Focus`
   declares — and gives that DESIGN failure `SC-CHK-0106` where `09` :69's constructor check and `13`
   :346 give `SC-CHK-0101` (`_work/round3/a-semantics.md` D7/D9 named both; only D7's code half
   landed).

2. **F-1 asserts a refusal with no rule** (claim 5). `09` :554's trigger (`ANY_SUBJECT` + `EXCLUSIVE` +
   `Bound`) satisfies R1 (`Bound` ≠ `None`) and R4 (`ANY_SUBJECT` forbids *per-cell* checks only), so
   `build()` accepts it; the only surviving statements of the prohibition are `08` :187's sentence and
   `22` §16 Q6 / `08` :324, which record it as an open question. Before the fix B-9 carried it.

3. **`SC-CLM-0001` now has two scopes** (claim 4). `08` :215 + `13` :332 = two `EXCLUSIVE` claims
   (the fix added "of different owners" at :215), while `08` :144-145 still routes a one-owner internal
   overlap to `SC-CLM-0001`; `08` :30-32/:64's `(owner, siting)` exclusion means `ClaimMatrix` cannot see
   that pair at all, so the sentence describes an outcome with no producer. Decide: internal `Bound` +
   `EXCLUSIVE` → `SC-BPT-0002` (DESIGN is gone by then) or a fifth code — either way `:144-145` and
   `:215` must agree, and today the claim's "neither code changed meaning" half does not hold.

4. **`21` denies a schema-2 field `10` permits** (claim 8). `21` :204's "There is no turn list in
   schema 2" and `21` :126's `front`-only row contradict `10` :199/:452's optional
   `turns`/`uprightOnly` for the same version. One of the two files must state which profile owns the
   turns, the way `10` :186 and :233 already state requiredness.

5. **`FootprintRef` explains nothing** (claim 14). `10` :89 declares it with the new
   `Kind {BOX, BOUND, DECLARED, NONE}` and `10` :263 says the geometry records of §3 are
   "document-side references … `FootprintRef` is how a document states a `Footprint`", but `10`
   :73-74's `SegmentDocument.Bay` declares `Footprint footprint` directly and no other file names
   `FootprintRef`. Round 1's disposition ("delete `FootprintRef` or make `SegmentDocument.Bay` use
   it") is still half-applied, and the sentence added in its place is false of §3.

6. **`01` :163 keeps the retracted document keys alive** (claim 7). See the claim-7 line: a glossary
   row is the one place a name must not survive after both owners dropped it.

7. **`16` :243 still gives `OrientationPolicy.select` a fallback heading** (front B's claim 12
   surface). "…`AXIS` is not a heading source …; `null` lets `OrientationPolicy.select` use its
   fallback" contradicts `09` :107/:150 (`select(Heading)` returns "no admissible orientation", "never
   identity") and `09` :266 ("never `Heading.NORTH` by default"); the round-1 fix removed the fallback
   from `04` and `09` but not from `16`. I did not change it; it belongs to front B's file list.

## 4. Residue decision (§I — `01` §I's cache cluster, `01` :210-215)

Reconciled against `20` §6.3 (`:434-462`), `20` :512 (P6), `11` :294/:300 and `22` :508. No edit was
made (round 2 is read-only); this is the disposition the lead applies.

- **`SectionStamp` (`01` :211) — keep, re-shape.** Something named `SectionStamp(content)` still
  exists: `20` :269 defines it and `20` :219/:221's `contentEpoch` is the value (`20` :206/:267 calls
  the same thing `SectionSnapshot.captureRevision`). What is dead is the role clause: there is no per-section
  cached *result* any more, and the cache never compares an epoch (`20` :458-459, P6). New row:
  "`SectionStamp` | `(long content)`: one captured section's content epoch
  (`SectionSnapshot.captureRevision`); `closureDigest` hashes the `(SectionKey, contentDigest)` pairs
  built from it, and no cache hit compares an epoch (`20` §6.3)." **`20` :269 must move with it** — it
  still calls this "the cache stamp", the one wording left from the per-section cache; say "the section
  content stamp".
- **`StepResult` (`01` :212) — delete the row.** Nothing outside `01` and `_work/**` names it; the
  shape `(stepIndex, Verdict, findings, List<BindingSite>, cacheable, chargedCost)` is the round-2
  per-chunk step cache (`_work/worker-async/section-checking.md` :31-32, `SectionResult(key, stamp,
  Map<Integer, StepResult>, …)`). The live cache is per *unit* (`CachedUnit`), charging is `Spend` per
  dispatch (`20` :481-483), and cacheability is `CacheMode` + `closureDigest` — no step index, no
  `cacheable` flag, no `chargedCost` survives.
- **`BindingSite` (`01` :213) — delete the row.** A cached match keeps `OccupantRef`s in
  `CachedOutcome.subjects` and re-stamps them with the current capture's id on reuse (`20` :461-462),
  so a `(BindingKey<?>, LatticePoint)` "binding to re-project" describes nothing; the live path is
  `BayBinding` / `Bindings` (`01` :193-197).
- **Observed, not touched** (the brief excludes `AttemptCache`/`CachedUnit`): `01` :210's `CacheKey` row
  is still the pre-§6.3 generation — `(world, PlanId plan, Stage stage, Siting, ChunkGrid, ChunkKey,
  long contentEpoch)` against `11` :294 and `22` :508's `CacheKey(PlanId plan, Siting siting, UnitId
  unit, long closureDigest)` (no `Stage`, no `ChunkKey`, no epoch; `UnitId`/`closureDigest` absent) —
  and `01` :214's `CachedOutcome(ContextKey<?> key, @Nullable Object value)` disagrees with `20`
  :438-439's `CachedOutcome(Reply reply, List<Finding> findings, List<OccupantRef> subjects)`. The
  brief calls `CachedUnit` already reconciled; the component list says otherwise, so the lead should
  check before closing the cluster.

## 5. Out of scope (noted, not hunted)

- `22` §6.4's `Computation` has no `stage()` (with the engine assigning it, `22` :271), yet `22` :367
  (§7.1) orders a stage by "every unit with `stage() == S`" and `01` :149 lists `stage()` as a member.
  Front B's claim 15 surface; either `01` :149 and `22` :367 lose it or §6.4 keeps it.
- `21` :367/:414 raise `SC-DOC-0521` while `10` :341 owns its registration — consistent, but the
  registration lives in `10` §4.4 and the raise-site note in `21` §7; no disagreement found.
- `zh/14` :434 still cites U5 as `AnchorRole` (a type) where `14` :441 says "anchor role ids": §H
  mirror drift, front C's.
- `10` §4.5 step 5 ("a `Spec`'s `bay`, `output` and `inputs` are authored in compiled ids") versus the
  bare names in `17` :108 and `21` :179/:184; `17` :87-91 explains the compact resolution, and no file
  states the normalisation step that implements it. Left as prose looseness, not a code/name clash.
- New semantic findings were not hunted; none of the above is a new mechanism, only a name or code
  disagreeing with its own declaration.
