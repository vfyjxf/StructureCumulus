# G2 — d-predicate, e-claim, f-blueprint

Rows keyed by report file + id (`d-`/`e-` both use D1–D15). Only `CODE` rows were written back.
Both files' anchors were re-opened and checked against `src/` before editing; no row whose code
anchor no longer matches the tree was applied (none found in this group).

Files touched: `07-expectations.md`, `08-claims.md`, `09-blueprints.md`, `13-findings.md`.

## 1. Applied

### d-predicate.md

- **d-D2** — `07` §4.6 focus table, `07` §5 sketch (`HatchExpectation`, the `anyOf` note). The table
  now pairs each kind with the subject the code binds: `bay` → `BayView`, `allCells` →
  `List<OccupantSnapshot>`, `occupant` → `OccupantSnapshot`, `entity` → `EntitySnapshot`, `nested` →
  `NestedView`, `context` → `T`, `door` → `DoorView`. `HatchExpectation` is
  `Expectation<OccupantSnapshot>` using `hasTrait(NamespacedId)`; the note that a hatch-or-bars bay
  must be two bays (because `OccupantView` and `BlockView` were different subjects) is replaced by
  the code's fact: one `OccupantSnapshot` subject, two element tests under one `occupant` focus.
- **d-D6** — `07` §4.6 focus table + rule table, `09` §5 sketch. The `block` row is gone (`block` is
  an expectation, not a focus kind); `door` is `ONE_SUBJECT`, not "`ONE_SUBJECT` or `EVERY_CELL`";
  two new rule rows state the code's split — element tests (`Expectations.block`/`air`,
  `Expectation<OccupantSnapshot>`) vs the bay test (`Expectations.eachCell(element)` on a `BayView`);
  `09` §5's `Expectations.door(mc("iron_door"))` (no such member) is replaced by an
  `Expectation<DoorView>` (`new OakDoorExpectation()`), the type 07 §5 defines.
- **d-D10** — `07` §3 `ExpectationMeta`, §3 prose, §4.5, §5 sketch metas, §8 dependencies. The record
  is `(NamespacedId id, int specificity, Set<ContextKey<?>> requires, FindingCode mismatchCode)`;
  `anonymous()` is `structurecumulus:anonymous, 0, {}, SC-EXP-0001`; `of(id, specificity,
  mismatchCode)` is the documented factory; `requires` is a `ContextKey` coverage demand (§4.5 says
  "an unserved `ContextKey`"); the sketches use `ExpectationMeta.of(...)`; `TraitKey` is dropped from
  the §8 dependency list (and `context` added), with "a trait's source" removed from §3's read list.
- **d-D11** — `07` §3 contexts + `07` §3 signature table + `07` §4.2 `run` sketch; `09` §3 `Check.run`
  sketch + `09` §8 dependency list. `FocusContext` is `bay(): BayView`, `view()`, `siting()`,
  `cells()`, `stage()`, `locus()` (plus `AggregateContext`'s `read`/`occupants`/`context()`);
  `ExpectationContext<T>` carries `withSubject(V, List<OccupantRef>)` (documented as the one way to
  re-type the subject for one cell of a bay test) and `ExpectationContext.of(...)`; the separate
  `CheckContext` type and its table row are gone; both `run` sketches are `run(FocusContext ctx)` +
  `ExpectationContext.of(ctx, found.value(), found.subjects())`.
- **d-D13** — `13` §"shipped allocation" `SC-EXP` rows + the `human()` example. Allocation is now
  0001 = element test failed (default `mismatchCode`), 0002 = a negated test matched, 0003 = bindings
  conflict, 0004 = threshold unreachable (new row); the rendered example that showed a type mismatch
  as `SC-EXP-0002` now shows `SC-EXP-0001`.
- **d-D15** — `07` §4.2's two `Missing` bullets. A context-key absence is `SC-FOC-0302` with its
  message naming the key and its label on a `LOCAL` locus that holds no cell and no key; the key is
  declared once, in `reads()`; and the same absence also travels as `ContextView.require`'s finding in
  `CheckResult.notes`, so it is reported twice, in two frames.

### e-claim.md

- **e-D8** — `08` §2 `ClaimConflict` row, §4.5. The carrier is the **held** rows that block, plus the
  request's own conflicting rows when the conflict is inside one request.
- **e-D9** — `08` §3 `ClaimVerdict.ClaimConflict`. `findings` is a `FindingBundle`, not a
  `List<Finding>`.
- **e-D10** — `08` §3 `ClaimView`. It is a record `(List<ClaimRow> rows, long revision, boolean
  available)` with the canonical constructor's "an unavailable view holds no rows" invariant,
  `unavailable()` as the factory for `available() == false`, and `rowsIn(Cuboid)`.
- **e-D15** — `08` §2 vocabulary, §3 `ClaimSet`/`Settled`/`ClaimConflict`, §4.1 order table. Every row
  carrier establishes canonical order in its constructor (`ClaimRow.ordered(rows)`); §4.1 names
  `ClaimRow.ordered(List<ClaimRow>)` as the one implementation of the `ClaimRow` order.

### f-blueprint.md

- **f-F6** — `09` §2 vocabulary (`BayBuilder` row), §3 `Bay`/`BayBuilder`/`Requirement`, §3 prose,
  §4.7 `String` sugar, §5 sketches. `Bay.of(…, Function<Bay, Requirement>)` is gone; the knot closes
  in two steps — `BayBuilder.start(...)`, `bayId()`, `attach(Requirement)` — and the "a bay that
  asserts nothing is a seam" rule lives on `Requirement`'s constructor (`SC-CHK-0107`), which is what
  makes the two-step form the only writable one.
- **f-F14** — `09` §2 `BlueprintBuilder` row, §3 builder block, §5 sketches. The builder takes a
  finished `Bay` (`bay(Bay bay)`), has no `check`/`compute` (checks and computations travel inside the
  `Bay`'s `Requirement`), and every sketch builds bays through `BayBuilder` and attaches a
  `Requirement`; the `specificity` int that the old `.check(...)` sketches passed as a sixth argument
  is gone, leaving the five-component `Check` the file declares. **Partial:** the `Check` record's
  `Stage` component is *not* written back (see §2).

## 2. Held by R

- **f-F14 (stage half)** — *not* one of R27/R35/R36/R37, but the open F2 ruling (`22` §16.1 item 16;
  `09` §4.8 "an author declares neither a stage nor a unit order" vs `23` §7). `Check` keeps its
  stage-free shape in `09` §3; only the builder/sketch half of F14 was applied, per the brief's
  "`Check` gained `Stage` but the builder signature should change" case. Needs the lead's F2 ruling
  (option (c) — the catalog stamps the stage — would leave `09` §3 unchanged).
- No row of this group's `CODE` set is held by R27/R35/R36/R37: e-claim's R-covered rows (D11 →
  R35, D13 → R36, D14 → `23` §5) and d-predicate's D4/D5/D7/D8/D9 (R27/R30 and open questions) are
  `DOC`/`BOTH`, so they were not candidates.

## 3. Stale rows

None in this group. Each `CODE` row's anchors were re-read: `Expectations`/`Focuses`/
`ExpectationMeta`/`FocusContext`/`ExpectationContext`/`Check`, `ClaimSet`/`ClaimVerdict`/`ClaimView`/
`InMemoryClaimLedger.blockingRows`/`ClaimMatrix.rowsOf`, `Bay`/`BayBuilder`/`Requirement`/
`BlueprintBuilder`/`Coverage` all still match the quoted lines.

## 4. Cross-file follow-ups (for the lead)

1. **`17-worked-example.md:277`** — a rendered `Failed` still says `SC-EXP-0002` for "expected the
   shell block, found air", i.e. a type mismatch. Under d-D13 that is `SC-EXP-0001`
   (`zh/17-worked-example.md:209` carries the same string).
2. **`01-glossary.md` §E** — needs the same facts this group could only apply inside `07`: the Focus
   row's kind list still names `block` (d-D6); its subject types still say `BlockView`/`OccupantView`/
   `EntityView` (d-D2); the `ExpectationMeta` row still says `(id, specificity, Set<TraitKey<?>>)`
   (d-D10); the Probe-session/Expectation-context rows still carry `bay(): NamespacedId` and
   `CheckContext` (d-D11); the Locus-frame row still says `KEY` carries a key for a context-key
   absence (d-D15). §F still has `ClaimConflict(List<Finding>)` and `ClaimView` as an interface
   (e-D9/e-D10).
3. **`22-consolidated-design.md`** — §6.2's `Check.reads` formula (d-D9, `DOC` row, not touched here);
   §6.3's `Focus<T> Focuses.door(LatticeKey)` return type should be `Focus<DoorView>` (d-D2's
   evidence); `22:528` `ClaimConflict(List<Finding>)` (e-D9); `22:626` `@Nullable ClaimView` competes
   with the record's `unavailable()` (e-D10).
4. **`00` §5 / `03` §3 / `01` §E header / `06` §1** — the `kernel.expectation` vs `kernel.predicate`
   package name (d-D1, `DOC`): `07`'s title still says `kernel.expectation`.
5. **`23` §7 and `10` §4.4** — both state that a `Check`/document carries no `stage`; that is F2's
   open ruling and blocks f-F14's stage half.
6. **`18-verification.md`** — C-08/C-09/C-27 (d-D7, `ProbeSession`/budget), F5 (d-D9 read closure;
   d-D14 entity focus declares no reads).
7. **`07` vs `09` projection** — `09` §3's `Check.reads` sketch (kept as shipped) still says
   `focus.reads(bayCells).withKeys(expectation.meta().requires())` while `07` §3 keeps the `DOC`
   union formula; d-D9 owns that reconciliation.
8. **`07` §2/§3 member lists** — `Expectations` still lists `sequence`, `atMost`, `exactly`, `trait`,
   `anyOccupant`, `nothing` and `custom`, none of which exists in the tree (d's §4 gap list, no row).
   `Expectations.eachCell` — absent from `07` entirely (d §4a) — is now *used* by `09` §5's
   sketches, so `07` should gain its row (`Expectation<OccupantSnapshot>` → `Expectation<BayView>`,
   `Focuses.bay()` as its focus).

## 5. Verification

- **Fences**: `07` 6, `08` 4, `09` 14, `13` 12 — all even, every block closed.
- **Table columns**: a pipe-count scan over every contiguous table block in the four files reports one
  mismatch, `13:525`, which is pre-existing (`(1|2, y, z, x)` inside a code span in the 13 §6 ordering
  table, untouched by this group). All edited tables keep their column count (`07` §4.6 3/2 columns,
  `07` §3 2, `08` §2 2, `08` §4.1 2, `13` allocations 4).
- **Referenced members exist** (checked against `src/kernel`): `BayView bay()`, `withSubject`,
  `ExpectationContext.of`, `hasTrait(NamespacedId)`, `ExpectationMeta(id, specificity,
  Set<ContextKey<?>>, FindingCode)` + `of`/`anonymous`, `ClaimRow.ordered`,
  `ClaimVerdict.ClaimConflict(rows, FindingBundle)`, `ClaimView(rows, revision, available)` +
  `unavailable()`/`rowsIn`, `BayBuilder.start/bayId/attach`, `Requirement`'s `SC-CHK-0107` throw,
  `BlueprintBuilder.bay(Bay)`, `Focuses.bay/occupant/door`, `Expectations.block/air/eachCell`,
  `LatticeKey.of(LatticePoint)`, `Finding.Builder.message/label/note`.
- **Symbols removed everywhere in the four files**: `BlockView`, `OccupantView`, `EntityView`,
  `TraitKey`, `Traits.`, `CheckContext`, `ctx.with(`, `Bay.of`, `Foci.`, `new ExpectationMeta(`,
  `Expectations.door`, `interface ClaimView`, `List<Finding>` — grep returns nothing.
- **Same-file self-consistency**: `09` §5's four sketches now use only the members `09` §3 declares
  (BayBuilder → Requirement → `bay(Bay)`), and `07` §5's sketch uses only `07` §3's signatures.
