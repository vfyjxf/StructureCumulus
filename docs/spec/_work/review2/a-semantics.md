# Review 2A — semantics and use cases

Object of attack: `23-consolidated-design.md` (revision 2). Claims were checked against
`00-overview.md`, `01-glossary.md`, `05-space.md`, `07-expectations.md`, `08-claims.md`,
`09-blueprints.md`, `10-documents.md`, `12-context.md`, `17-worked-example.md`, `21-authoring.md`,
and `_work/review1/requirements.md`. Section numbers without a file prefix are `23`'s.

Ten defects, ranked by damage. Each is either new or a `§15.1` row whose closure does not hold;
`§15.1`'s closing table is checked at the bottom.

---

## D1 — The one claim predicate has no exclusivity input: `ClaimMode` is gone from `Bay`, so the door and the shared hatch cannot both hold

**Scenario.** `§6.5`'s bay is `Bay(id, cells, coverage, requirement, claims, description)` — six
components, no mode. `§10.2` defines `ClaimScope` as *which cells* (`Owned`, `Occupied`,
`Declared`, `Audit`); none of the four says whether a row excludes another. `§10.1` then asserts
`ClaimMatrix.conflicts(a, b)` is "the only conflict function" over `ClaimRow`, a type `23` never
declares. Now run the two scenarios `§13` mandates on one hatch cell `(10,64,10)`:

- Door: two doors on cells `(3,0,0)` and `(3,1,0)` (bay `Cuboid.of(3,0,0,4,2,1)`), both
  `ClaimScope.Occupied`. `§13` promises "one settles `EXCLUSIVE`, the other gets
  `Failed(SC-CLM-0002)`".
- Shared hatch: A and B both declare `SHARED` on `(10,64,10)`. `§13` promises "both settle, both
  rows coexist".

Both blueprints are `Bay(... Occupied ...)`. If `conflicts` treats two rows on a cell as
conflicting, the shared hatch fails on a legal design and `SC-CLM-0002` fires in `§13`'s own
third walkthrough. If it does not, two doors coexist on the same two cells, `SC-CLM-0002` can never
fire between two structures, and `I-10` ("every settled cell carries a `ClaimMode`") plus R-11 are
vacuous. There is no third mapping: `Owned` is documented as "air interior must be protected",
`Occupied` as bound footprints, `Audit` as read-only — cell-selection semantics, all three.

**Violates.** R-11 (its `§2` row names `ClaimScope` as the whole mechanism), I-10, `§8`'s P2/P3
(`Formed` requires `Settled`, and `Settled` needs a predicate with inputs). `§15.3` D1 claims the
fix is "one `ClaimMatrix`, `(owner, siting)` scope, no adapter filter" — the scope was unified,
the mode was silently dropped.

**Why the stated proof fails.** P2 says "claims are total and conflict-checked — one matrix, one
predicate". The matrix's conflict decision is `mode(a) ∧ mode(b)` in `08-claims.md` §3
(`conflictsWith = !key.equals && !ClaimMode.coexist(mode, other.mode())`), and `08`'s matrix reads
`Bay.claimMode()`. `23` deletes that component from `Bay` and never reintroduces it on
`ClaimRow`, so the predicate has one input where it needs two. `§13`'s prose still branches on
`EXCLUSIVE`/`SHARED`/`RESERVED` at lines 690–695, i.e. the walkthrough uses a field the type
surface does not have.

**Fix.** Keep both dimensions and say they are orthogonal: `Bay(..., ClaimScope claims,
ClaimMode mode, ...)` with `ClaimMode = EXCLUSIVE | SHARED | RESERVED` as one component of
`ClaimRow`; `ClaimScope` chooses the cells, `ClaimMode` chooses exclusion. Declare `ClaimRow` in
`§10.1` (`(owner, siting, bay, cell, mode)`) so `ClaimMatrix` has a signature.

---

## D2 — A door is two occupants but `Focus.take` returns one `T`, and `ClaimScope.Occupied` cannot obtain a footprint from an `OccupantRef`

**Scenario.** Door bay `Cuboid.of(3,0,0,4,2,1)`, two cells: lower oak door at `(3,64,0)`, upper at
`(3,65,0)`. `07` §5's `OakDoorExpectation` binds `LOWER`/`UPPER` as two
`BindingKey<OccupantRef>`. `§6.5` says `decisionCells = both cells`, `Coverage.SUBJECT`,
`ClaimScope.Occupied` "→ two cells", and "`Occupied` is the union of bound footprints". Three
independent gaps:

1. `Focus<T>.take` returns `Outcome<T>` — one value. `Check<T>` fixes one `Class<T>`
   (`§6.2`'s only constructor check compares `focus.subject()` with `expectation.subject()`).
   With `T = OccupantRef` the focus can bind exactly one half, so `BayBinding.subjects`
   (`§6.5`) holds one ref, `Occupied` is one cell, and `SUBJECT` coverage compares
   `|decisionCells| = 2` against `|union of subject footprints| = 1` → `§6.5`'s "shortfall is
   `Undecided(DESIGN_DEFECT)` with the same code" fires on every settle of a *correct* door: the
   door can never form.
2. If `T` is instead a compound `DoorSubject(lower, upper)`, `Occupied` has no rule that unpacks a
   compound `T` into refs, and nothing declares who fills `BayBinding.subjects` at all. `§6.2`
   produces `Reply`/`Bindings` per check; `§7.4` builds "a `MatchFacts.bindings` map per bay" — a
   `BindingKey`→value map, which is not a `List<OccupantRef>` and may not hold `OccupantRef` at
   all. The typed path from a check's result to the claim's subject list does not exist.
3. Even with a ref, there is no footprint. `01` §70: `OccupantRef = (SnapshotId, type, kind,
   anchorCell)`. `§5.1`'s `WorldView` offers `occupant(LatticeKey)`, `occupants(Cuboid, filter)`,
   `nested(LatticeKey)` — no `occupant(OccupantRef)`. `OccupantSnapshot.footprint` exists, the ref
   cannot reach it. For a 2×1 golem (`§5.4`/round-1 D5) the anchor is one cell and the footprint
   two: `Occupied` claims 1 cell and the golem's second cell is owned by nobody, silently.

**Violates.** R-06 (`§2` row: "door is one bay with a two-cell focus"), R-01 (entity footprints),
R-11/I-10 (the two-cell claim is not atomic if one half is unclaimed). `§15.1` D2 is recorded as
"Fixed: `decisionCells` + `Coverage`; `BayBinding` holds `List<OccupantRef>`" — the list is
declared, its producer and its footprint source are not.

**Why the stated proof fails.** P11 ("a short check cannot claim a whole bay") is enforced by
`decisionCells`, but `decisionCells` is a *declaration on the focus*; `§6.2`'s constructor compares
only `subject()` classes. So `Focus` metadata can claim two cells while the expectation reads one,
and nothing witnesses what `test` actually read (`ExpectationContext` has no read log). P11 is an
argument about a number the addon writes, not about what the check decided.

**Fix.** `Focus<T>` gains `List<OccupantRef> subjects(T value)` (or `Outcome.Found` carries
`List<OccupantRef>` beside the value), `BayBinding.subjects` is derived from it, and `WorldView`
gains `@Nullable OccupantSnapshot occupant(OccupantRef)`. `Occupied` then means
`⋃ occupant(ref).footprint()` and the 2×1 golem case is expressible. `Coverage.SUBJECT` must also
state which of the two failure modes it produces (shortfall ⇒ which `UndecidedReason`, and that
the bay claims nothing).

---

## D3 — `§12.3`'s furnace document is not a compact document, and `§12.4`'s lowering rule destroys its named bay

**Scenario.** `§12.3`: `"shell": [{"box": [0,0,0,7,6,7], "block": "minecraft:blast_furnace"}]`,
`"cavity": [[1,1,1,6,5,6]]`, `bays[1] = {"id": "interior", "box": [1,1,1,6,5,6], ...}`,
`computations[0].args.region = "interior"`. Four failures, all before any world read:

1. **Grammar.** `21` §3.1 defines `shell` as an *object* `{block, thickness}` and `cavity` as
   `{block, inset}`, and `21` §4 rejects `shell`/`cavity` without `size`. `§12.3` has no `size`
   and uses arrays. `DocumentProfile.AUTO` selects compact from the presence of `shell`/`cavity`,
   so a reader following `21` sees `shell` as a type error.
2. **Box encoding.** `21` §3.3's `box` is origin+size (`{"box": [3,0,0,1,2,1]}` is the 1×2×1
   door); `§12.3`'s `box` is min/max (`[3,1,0,4,3,1]`, `§13`'s 1×2×1 door). Read `§12.3`'s
   interior under `21`'s encoding and it is 6×5×6 = 180 cells, not 100. One JSON key, two
   meanings.
3. **Lowering.** `§12.4`: "a later write over an earlier cell merges into the bay that owns the
   cell". The cavity writes 100 cells owned by the shell bay, then the named `interior` bay writes
   the same 100 cells — so the named bay is absorbed into the shell. `region: "interior"` and
   `bays[1].claims: owned` then name a bay that does not exist (unknown-bay DESIGN error), and the
   surviving shell bay holds `blast_furnace` and `air` on the same 100 cells, which can never
   match. `21` §4's partition rule (cell→expectation map, then partition into disjoint cuboids,
   named bay keeps the cuboid containing its origin) is exactly what prevented this; `§12.4`
   replaced it and reintroduced it.
4. **Loading.** `21` §5's default `PROBE` plan is `BOUNDS`, `CENSUS`, `ANCHOR`, but `§6.2`'s
   `Check` has no kind and `§6.7` ships no `bounds`/`census`/`anchor` focus; `computations[0]`
   needs `state[]` for its key (present); `mouth_facing`'s `"expect": {"kind": "state", ...}` names
   an expectation `§6.7` does not ship (`block, air, trait, allOf, anyOf, not`), and
   `Catalogs` in `15` §4.3 is still `expectations/derivers/remedies/codes` with no `focuses` or
   `computations` family, and `§15.4` does not list `15`. So the check resolves to nothing at
   `apply` (`SC-SPI-0002`/`SC-DOC`), on the design's own document.

**Violates.** R-09 (its `§2` row: "canonical + compact JSON; `DocumentProfile.AUTO`; one `Spec`
family"), R-06/R-11 for the shell/cavity decomposition, `§12.1`'s "both profiles are lossless:
every canonical field has a compact spelling" (there are now two compact grammars).

**Why the stated proof fails.** `§0.11` says "compact lowering preserves bay identity"; identity
is preserved for *named* bays only by refusing to split them, and the merge rule preserves nothing:
a later write annexes the earlier bay. `21` §4's proof ("this is why `shell → cavity → mouth`
never produces overlapping exclusive bays") no longer applies because the partition step is gone.

**Fix.** Restore `21` §4 verbatim (partition after the cell→expectation map; named bays keep their
id and their cells are removed from the map first), keep `21`'s `shell`/`cavity` object shapes and
`size`, and give `§12.3` one box encoding (origin+size, matching `21`). Add `15` (catalogs) to
`§15.4`.

---

## D4 — The furnace's promoted value cannot be produced by the declared computation: 150 is not 100, `non_air` over an air bay is 0, and `merge` on an `AGGREGATE` key is refused

**Scenario.** `§13`: "the `interior_volume` section computation, the fold ... (`volume = 150`, frame
FORM revision 1) ... `derive` reads the FORM frame, publishes `capacity = 600` at SETTLE."
Interior bay `[1,1,1,6,5,6)` = 5×4×5 = **100** cells (17 §1, 12 §4.3, and `§12.3` itself).
150 = 6×5×5, i.e. reading the max corner as inclusive — the exact off-by-one `05` §4.2 warns
about. Then:

- `computations[0]` is `{"kind": "count", "args": {"region": "interior", "mode": "non_air"}}`.
  The same bay is `expect: {"air": true}` over the whole box; if the bay MATCHed, `non_air` = 0, so
  the promoted value is 0 and `capacity = 0`. A non-air count can only be non-zero for a bay that
  failed.
- `state[]` declares `{"phase": "aggregate", "merge": "sum", "whenAbsent": 0}`, but `§6.4` says
  "registration validates `phase`, `merge` non-null iff `SECTION`". An `AGGREGATE` key with
  `merge` is refused at registration. `§13` calls the same thing "the `interior_volume` section
  computation, the fold" — a `SECTION` unit — so the document and the walkthrough disagree about
  the phase, and only one of them can be implemented.
- `whenAbsent: 0`: `§7.2` folds to `whenAbsent` when zero sections are present, i.e. when every
  interior section was `Refused(NOT_CAPTURED)`. The result is a legal `Formed` whose promoted
  capacity is 0 — P5 ("absence is never a number") and `§0.3` ("No fold returns an identity")
  contradicted by the only shipped `CONTEXT` example. `§16` Q3 asks whether a `Refused` section
  should refuse the default; the example has already answered "no".

**Violates.** R-03 (its `§2` row is `Computation → StateKey(CONTEXT) → tier frame → RuntimeState`,
i.e. this exact chain), R-08 (phase), P5, `§6.4`'s registration rule.

**Why the stated proof fails.** P5 is argued from "no identity; fold starts at the first present
partial", which is true of the *fold* and false of the *key*: `whenAbsent` is an identity by another
name, and the only shipped document declares one. There is no proof at all for the value's
arithmetic: 150 appears nowhere except `§13`, and `12`/`17`/`19` R13 all say 100.

**Fix.** Delete `whenAbsent` from the example (absence ⇒ `Missing` ⇒ the check maps to
`UNREADABLE`), make the key `SECTION` with `merge: sum` if the fold is wanted (or `AGGREGATE`
with no merge and one region read), change `mode` to `air`/`free` so a MATCHed interior counts
100, and correct 150/600 to 100/400 in `§13`.

---

## D5 — The pipe corner: `§10.2` closes `07` §4.5's only workaround, so the L-shaped corner either conflicts with its neighbour or claims nothing

**Scenario.** Two pipe corners facing each other with a one-block gap. Corner A: box
`[10,64,10,12,66,12)`, pipe cells at x=10, four must-be-air cells at x=11. Corner B: box
`[11,64,10,13,66,12)`, four must-be-air cells at x=11, pipe cells at x=12. Both are correct
builds; the four x=11 cells are air in both and belong to neither. Then:

| scope | cells claimed | outcome |
| --- | --- | --- |
| `Owned` | 8 (A's box) | A and B both claim the four x=11 cells → B settles `Failed(SC-CLM-0002)` on a correct build |
| `Occupied` (the default) | 8 if air is bound (air is "a readable occupant", `§5.1`), 4 if it is not | 8 → same conflict; 4 → the shared air cells are claimed by nobody, and a third structure may fill them while both corners stay `Formed` |
| `Declared(4)` | 4 | `§10.2`: "a bay with a non-`Audit` scope whose check reads outside its scope is `SC-BAY-0108` at DESIGN" — the air check reads the other four → DESIGN error |
| `Audit` | 0 | R-11 unserved: nothing protects the corner |

`07` §4.5 sanctions the workaround ("it becomes several `allOf` bays or one bounding box whose
test rejects the must-be-empty cells") and `§10.2` reopened it: the bounding box is the only way to
express "must be air", and the box is exactly what the claim takes.

**Violates.** R-06 ("heteromorphic predicates, rectangles only"), R-11, `07` F4, `§15.3` D4
("claim footprint excludes globally judged cells — fixed: `ClaimScope`, DESIGN checks"), which
substitutes a *smaller* scope and then forbids reading outside it.

**Why the stated proof fails.** `§10.2`'s own sentence pairs two rules that cannot both hold for a
negative predicate: the check's read set is a superset of its claim (it must read the cells it
forbids) and `SC-BAY-0108` requires claim ⊇ read. `SC-CLM-0104` (WARNING for "blueprint cells
inside the bounds but outside every footprint") makes the same hole visible instead of closing it.

**Fix.** Scope `SC-BAY-0108` to `bay.cells`, not to the claim footprint: a check may read any cell
of its own bay, and `ClaimScope.Declared` may be a strict subset. Add the negative-predicate case
to `§10.2` explicitly ("must-be-air cells are read and not claimed"), and say whether an unclaimed
must-be-air cell inside `bay.cells` may be filled by another structure (with a third structure the
answer must be no, which needs a claim kind weaker than `EXCLUSIVE` but non-zero).

---

## D6 — `ContextStore`/`ContextView` have no instance identity, so two furnaces either collide on `SC-CTX-0107` or the context cannot be addressed at all

**Scenario.** Furnace A at `(10,64,10)` with `volume = 150`, furnace B at `(30,64,10)` with
`volume = 200`; both reach FORM in the same tick.

```java
interface ContextStore { ContextDraft draft(ContextTier tier); void publish(ContextDraft draft); }
record ContextFrame(ContextTier tier, long revision, Map<StateKey<?>, Object> values) {}
interface ContextView { <T> @Nullable T get(StateKey<T> key); @Nullable ContextFrame frame(ContextTier tier); }
```

No owner, siting, attempt or instance appears in `draft`, `publish`, `get`, `frame` or
`ContextFrame`. `§8.3` then says "publishing two different values for one key in one frame is
`SC-CTX-0107`, never last-write-wins". If the store is shared by tier (the only key it has), two
legal furnaces publishing `mymod:volume` at FORM produce `SC-CTX-0107` and one cannot form. If the
store is per attempt, then `§11.3`'s `RemedyContext.context` and `§5.5`'s "`StructureContext.at(formed)`
retains the lease" refer to a context for a specific `Formed` with nothing in the type that says
which one; `StructureContext.at(ContextTier)` (`§8.3`) has no `Formed`/`InstanceId` parameter at all,
and `at(formed)` exists only in `§5.5`'s prose. `RUNTIME` is "the persisted projection of `SETTLE`"
(`§8.1`) and `RuntimeState.upgrade(key, value)` is per instance (`12` §3), so the *store* must be
per instance while its declared key is a tier.

**Violates.** R-03 (context is a property of a formed structure), P10 ("promotion has one
writer" — it does, but the writer has no subject), `§8.2`'s `contextRevision` ("a frame publishes a
new revision" — which frame, of which instance?).

**Why the stated proof fails.** P10 is a single-writer argument about a `ContextStore.publish`
whose receiver is unnamed; uniqueness per key proves nothing when the key omits the instance.
`§8.1`'s tier ordering `BLUEPRINT < PROBE < FORM < SETTLE < RUNTIME` is per structure, not global:
a PROBE reader must not see another structure's FORM estimate.

**Fix.** Key the store by instance: `ContextStore.forInstance(InstanceId)` (or
`draft(InstanceId, ContextTier)`), `ContextFrame(InstanceId, tier, revision, values)`, and
`StructureContext.of(Formed)` as the only constructor. Then `at(tier)` is a narrowing of one
instance's own frames, and `SC-CTX-0107` means "two writers for one instance", which is the
invariant P10 actually wants.

---

## D7 — The entity pen is rejected by `Coverage.CUBIC` and cannot satisfy `Coverage.SUBJECT`: `decisionCells` is undefined for every non-cell focus

**Scenario.** Pen bay `Cuboid.of(0,0,0,5,1,5)` (25 floor cells), one check at SETTLE:
`entity: cow, count >= 1`, `Focus.entity` with `decisionCells = ∅` — `§6.5`'s enumeration is
"`block(cell)` → one cell; `allCells` → the bay's cuboid; `door(origin)` → both door cells;
`state(key)` → empty", and `entity`/`occupant`/`nested` are absent from it. Then:

- `Coverage.CUBIC`: "the union of `decisionCells` of the bay's checks must equal `bay.cells` at
  DESIGN" → ∅ ≠ 25 → `SC-BAY-0107` at DESIGN on a legal pen. The pen's only valid coverage is
  therefore `SUBJECT`.
- `Coverage.SUBJECT`: the union must equal "the union of the resolved subjects' footprints at
  match time" → ∅ vs the cow's 1×1 footprint → shortfall → `Undecided(DESIGN_DEFECT)` at every
  settle. A legal pen can never form under either coverage.
- If one defines `entity`'s `decisionCells` as the footprint ∩ bay, the value is unknown at DESIGN,
  so the `CUBIC` comparison — a DESIGN check — is unevaluable for that bay; the coverage assertion
  then only exists for `allCells`/`block`/`door`.
- The absent-subject path needs `Locus.SUBJECT`, "a frame with no cells: `(frameId, keyId)`"
  (`§6.6`), which `05`'s `Locus(LocusFrame, CuboidSet, @Nullable SourceSpan)` cannot carry (no
  key id, and `LocusFrame` has three values). `§15.4` does not list `05`, so nothing owns the
  change; `§15.1` records D6 as fixed.
- `SC-BAY-0109` ("a bay whose bound entity footprint leaves its `ClaimScope` is a design defect")
  fires on the cow walking out of the pen — a normal event reported with a DESIGN code — and with
  `Occupied`, whose value *is* "the union of bound footprints", the condition is unsatisfiable.
- `§5.4` sends the reader to "`§10.5`" for overlapping entity bays; `§10` has `10.1`–`10.4`.

**Violates.** R-01 (its `§2` row names `Focus.entity` and `ReadScope.entityWindows` as the
mechanism, with no coverage story), R-06 (`Coverage` "asserted"), R-07 (`Locus.SUBJECT` for a
subject with no cells).

**Why the stated proof fails.** P11 assumes every check decides cells. A predicate about an entity
decides a *subject*, not a cell set, so `decisionCells` is the wrong currency for it and CUBIC's
DESIGN equality cannot include it. `§16` Q6 flags only the `allCells`/`Occupied` variant, not the
empty-decision-set case, so the open question does not cover the pen.

**Fix.** Give `Coverage` a third value (or a per-check `decides: CELLS | SUBJECT | NONE`) so a bay
with a match-time subject is not required to enumerate cells at DESIGN; make `SUBJECT`'s equality
compare only checks that decide cells; define `SC-BAY-0109` as a runtime `Undecided` reason bound to
the ledger read, not a DESIGN code; add an optional `SubjectKey` to `Locus`/`FocusDesc` and list
`05`, `06`, `15` in `§15.4`.

---

## D8 — Orientation: a `Heading` and an `Orientation` are different kinds, the stated mapping is the inverse of the frozen one, and no unit produces the `Siting` that `MatchRequest` requires

**Scenario.** Mouth of iron bars, block state `facing=east`, authored front = local `Heading.NORTH`
(`09` §4.5.4). `§4.4`: "The default is 'the orientation that maps the anchor heading onto the
authored front'", i.e. `o(EAST) = NORTH`. `09` §4.5.3–4, the only frozen signature
(`select(@Nullable Heading impliedHeading)`), computes `target = implied.apply(NORTH)` and picks
`o` with `o(NORTH) = target`, i.e. `o(NORTH) = EAST`. Under `05` §4.4, `o(NORTH) = EAST` is
`Turn.CLOCKWISE`; `o(EAST) = NORTH` is `Turn.COUNTERCLOCKWISE`. The two readings differ by 180°:
the same furnace is sited with its 7×6×7 footprint and its 294 claimed cells rotated half a turn,
so the claims and any repair land on the region opposite the real structure.

Further, per the same clause:

- **Different kinds.** `Heading` is 6 values, `Orientation` 48 (`05` §4.5). `§4.4` says the adapter
  reads a property/value and "the resulting `Heading` is the anchor heading"; `09` §4.5 needs an
  `Orientation` from the candidate (`implied()`), because `05`'s `Tilt`/`Flip` exist so that a
  wall-mounted or upward-facing anchor can be posed at all. `facing` is one property; `axis=x`
  (a pillar), `rotation=0..15` (a sign) and `hinge=left` have no `Heading` value, and `§4.4`'s
  `String value` comparison accepts any typo (`"easet"`) as "no candidate" — a `Failed`
  match instead of the DESIGN finding R-07 asks for. `§4.4`'s policy signature
  `(authoredFront, anchorHeading)` also drops `uprightOnly` and `fallbackHeading`, which `09`
  §3/`§4.5` and `17` §2 use.
- **Anchor is a separate lookup with its own scope.** `§4.4`'s `AnchorSpec(role, cell, property,
  value)` holds one local `LatticePoint`; the read is "the block state at `cell`". A local cell has
  no world address until the placement is chosen, and the placement is what the read is supposed to
  determine: for the furnace's anchor `(3,1,0)` (inside the mouth, not at the origin) the world
  cell is `origin + o(3,1,0)`, i.e. it depends on the answer. `09` avoids this with an
  `AnchorCandidate` supplied by U7; `23` deleted `CheckKind.ANCHOR`, so no unit produces
  candidates. Each candidate read is also outside every `ReadScope` (`§6.1` has cells/keys/phases/
  entity windows, no anchor cells), so it is not in the capture and not billed — while `I-4`
  requires every match read to come from the snapshot. Meanwhile `§9.1`'s `MatchRequest` already
  takes a resolved `Siting`, so the anchor search cannot happen inside `match()` at all, and no
  facade operation (`check/derive/settle/repair`, R-14) returns one. `§13`'s "PROBE runs two
  `block` checks" is the only PROBE activity left, and `§12.3` declares no PROBE check.
- `§4.4`'s "the remaining `Turn` freedom resolved by declaration order" contradicts `09` §4.5.4
  ("scan `allowed` in `Orientation.all()` order") and `09` §3 (which stores `allowed` sorted). An
  anchor whose heading leaves a roll freedom (8 orientations map EAST→NORTH) then yields two
  different `Siting`s, hence two different `(owner, siting)` claim keys and `RuntimeState` keys.

**Violates.** R-04, R-05, I-5, `§8`'s P7 ("orientation is a function of the anchor"), R-14.
`§15.1` D4 is recorded as fixed by `AnchorSpec(property, value)`; the replacement drops `cells`,
`priority` and `required`, which B-4/B-5 depend on, and no type carries the resolved heading into
`select`.

**Why the stated proof fails.** P7 says "policy consumes the resolved heading". The resolved
heading is a `Heading`; the selection needs an `Orientation` plus a candidate enumeration that
`23` has no unit for, and the mapping direction in the proof's own sentence is the inverse of
`09`'s. A function whose argument is not produced anywhere is not a proof.

**Fix.** Restore `AnchorSpec(role, Cuboid cells, priority, required, property, value)`, add a
`siting`-producing unit (`SitingResolver` returning `Outcome<Siting>` with `SC-FOC-*` for no
candidate) that runs at PROBE over `ReadScope.cells = ⋃ all 48 images of the anchor cells`, let
`MatchRequest` take either a `Siting` or the blueprint, and fix the mapping sentence to
`o.apply(authoredFront) == anchorHeading`, matching `09` §4.5.

---

## D9 — `derive` has no stage, writes an undeclared key, publishes before the settle that can fail, and is never dirtied when its input key republishes

**Scenario.** `§12.3`: `"derive": [{"key": "mymod:capacity", "from": ["mymod:volume"], "kind":
"scale", "factor": 4}]`, and `state[]` declares only `mymod:volume`.

- `§7.1`: a stage's plan includes "the blueprint-level `derive` computations assigned to `S` by
  their `Retention` target". `Retention` is `ATTEMPT | FACTS | CONTEXT` (§6.4) — no stage. The
  sentence is not a function, so `capacity`'s stage is undefined; `§13` asserts SETTLE without a
  rule.
- `mymod:capacity` is not declared in `state[]`. `§6.4`: "Construction is only through `StateKeys`;
  a key built elsewhere is `SC-CTX-0101`", and `§12.2`'s `SpecResolver` "validates ... the key
  declaration" at `apply`. Either the document is refused, or the derive publishes an undeclared
  key at runtime; both are the same document.
- Order: `§7.1` puts `AGGREGATE` computations before checks in the stage; `§10.3` settles inside
  the match and a conflict returns `Failed(SC-CLM-0002)`; `§8.3` publishes one draft per stage
  boundary. So `capacity` is promoted into the SETTLE draft before the verdict exists, and
  `§8.1`'s `RUNTIME` is "the persisted projection of SETTLE" — a failed match can publish and
  persist a runtime value. If instead nothing is published on failure, `§13`'s "returns `Formed`;
  `derive` reads the FORM frame, publishes `capacity = 600` at SETTLE" puts `derive` after the
  match, contradicting `§7.1` and `17` §6 (`kernel.derive(full, ...)` as a separate call).
- Staleness: `§5.4`'s `ChangeSet` is exactly `{cells, sections, payloads, entities}` — there is no
  member for "a `StateKey` republished". `capacity`'s `ReadScope` is `keys = {volume}` with no
  cells, so a change that republishes `volume` (remove 4 chests from a cavity of 100: volume
  10 → 6) does not intersect `capacity`'s closure and `§8.1`'s "STALE re-runs only the units whose
  read closure intersects the change" leaves `capacity` at 40 while `volume` is 6 and the correct
  value is 24. `§8.2`'s `contextRevision` is not one of the accept gate's four terms (§9.2).

**Violates.** R-03, R-08 (`§2` row: "`STALE` re-run"), P10, `§0.12` ("remedies read the promoted
context" — they read a stale one).

**Why the stated proof fails.** P10 covers the writer, not the reader's freshness. The `readStamp`
that decides re-runs is "the tuple of per-section revisions it read" (`§8.2`), so a unit that reads
only keys has an empty stamp and is never stale; the only mechanism that could dirty it
(`contextRevision`) is documented as invalidating "downstream promotions" with no rule tying a
consumer to a producer's key.

**Fix.** Declare `mymod:capacity` in `state[]`; make the derive's stage explicit in the document
(`stage: SETTLE`) or derive it from the promoted key's stage; publish the stage draft only when the
stage's verdict is `Settled`; add `keys` to `ChangeSet` (or a per-key revision compared as part of
`readStamp`) so a consumer of a republished key is re-run.

---

## D10 — The PROBE→FORM handoff: a lease that is not transferred by any type, an `Admitted` that cannot be closed, and a capture that does not cover FORM's sections

**Scenario.** `§13`: "the read closure is one shell section, capture copies it ... `Admitted(PROBE)`.
The lease is transferred to the `FORM` submit; `FORM` runs the shell checks, the `interior_volume`
section computation, the fold, the `air` criterion ... (`volume = 150`)".

- One shell section is arithmetically impossible: bounds `[0,0,0,7,6,7)` at `(10,64,10)` covers
  x 10..16 and z 10..16, which is section x ∈ {0,1} and z ∈ {0,1} — **four** sections (y 64..69 is
  one). `17` §4 says BOUNDS "covers all 294 cells". The interior `[1,1,1,6,5,6)` covers the same
  four sections. So either the capture is four sections (and `§13`'s sentence is false) or it is
  one, and FORM's volume computation reads three `Refused(NOT_CAPTURED)` sections → `§7.2` "zero
  present sections → `whenAbsent`" (0) or `Missing`, i.e. `volume` is 0 or `UNREADABLE`, never 150.
- "transferred" is prose. `SnapshotLease` (`§5.5`) has `retain()`, `isOpen()`, `close()` — no
  ownership or transfer token. `Admitted` carries a `SnapshotLease` and is **not** `AutoCloseable`;
  `Formed` is; `Undecided` carries no lease at all. So a sealed `MatchResult` has one closeable arm,
  `try (MatchResult r = kernel.match(...))` does not compile, and the PROBE-only path (`17` §4)
  has a lease with no typed owner. P4 ("released exactly once") is an argument about a refcount
  whose first reference has no closer.
- The two stages share one lease and therefore one `maxLeaseTicks` (`§5.5`). A PROBE attempt whose
  host expiry fires while FORM is in flight force-closes the lease (`SC-MC-0501`), and `§9.2`
  lists `lease.isOpen()` as one of the four accept-gate terms: FORM is dropped, with no retry
  token (only `DEFERRED` carries one, `§9.4`) and no stated re-capture. `§5.5`'s fallback ("if the
  submit cannot take it, FORM re-captures under the same ticket") is not reachable from the gate.

**Violates.** R-14 (its `§2` row: "`Formed` only at SETTLE", "lease lifetime is a host
obligation" — with no owner named), P4, `§5.2` (a missing section must be `Refused`), I-4.

**Why the stated proof fails.** P4 assumes every holder closes its own reference; `Admitted` cannot
be closed through the result type, and the transfer has no representation, so "the lease is
transferred" and "the PROBE attempt still owns a reference" are indistinguishable values. The
sections claim is contradicted by `§13`'s own geometry.

**Fix.** Make the attempt, not the stage, own the lease: one `Ticket` per logical match, stages
join it, `§5.5`'s transfer and re-capture sentences are deleted, and the capture policy is the
union of every stage's read closure at first capture (or FORM states that it re-captures and pays
`cells`/`sections` again). Give `MatchResult` either a `close()` on every arm or none (an
`Attempt` handle returned beside the result), and let a closed lease produce
`Undecided(LEASE_CLOSED)` with a retry token rather than a silent gate drop.

---

## `§2` traceability rows contradicted

| Row | Its stated mechanism | Why it is not a mechanism | Defect |
| --- | --- | --- | --- |
| R-01 | `Focus.entity/occupant/nested`; `ReadScope.entityWindows` | no `decisionCells`/`Coverage` meaning for a subject that decides no cells; no footprint from an `OccupantRef` | D2, D7 |
| R-03 | `Computation → StateKey(CONTEXT) → tier frame → RuntimeState` | the value is wrong/unproducible (D4), the frame has no instance (D6), the derive has no stage and no key-level dirtiness (D9) | D4, D6, D9 |
| R-04, R-05 | `AnchorSpec(property, value)`; policy consumes a `Heading` | no unit produces a `Siting`; `Heading` ≠ `Orientation`; mapping inverted vs `09` §4.5 | D8 |
| R-06 | one `Cuboid`, `decisionCells`, `Coverage`, "door is one bay with a two-cell focus" | the two-cell subject has no typed producer (D2); must-be-air cells cannot be both read and unclaimed (D5); the shell/cavity decomposition does not lower (D3) | D2, D3, D5 |
| R-08 | `PlanUnit.stage`; commit barrier; `STALE` re-run | `derive` has no stage; `ChangeSet` has no key member so a derived consumer is never stale | D9 |
| R-10 | `Catalog<ExpectationFactory/FocusFactory/ComputationFactory/RemedyProvider>`; "kernel ships trivial tests" | `15` §4.3's frozen set is `expectations/derivers/remedies/codes` (no `focuses`, no `computations`); `§6.7` ships a `state` *focus* but no `state` *expectation*, which `§12.3`'s `mouth_facing` uses; `§15.4` does not list `15` | D3 |
| R-11 | "one matrix, one predicate; `ClaimScope`; settle inside the match" | the predicate has no exclusivity input (`ClaimMode` deleted) | D1 |
| R-14 | "facade `check/derive/settle/repair`; `Formed` only at SETTLE" | no facade operation yields a `Siting`; one `MatchResult` arm is closeable and two are not | D8, D10 |

Rows not attacked by this front: R-02, R-07 (text quality), R-09 (grammar details beyond `§12.3`),
R-12, R-13, R-15.

## `§15.1` closure check (numbers are that table's)

| Row | Recorded as | Holds? |
| --- | --- | --- |
| D2 (`MATCH` claims the whole bay; one occupant per bay) | Fixed: `decisionCells` + `Coverage`; `BayBinding` holds `List<OccupantRef>` | **No** — the list has no producer and `OccupantRef` has no footprint (defect D2); `decisionCells` is self-declared metadata, so P11 is unenforced |
| D4 (anchor facing never reaches the kernel) | Fixed: `AnchorSpec(property, value)`; policy consumes a `Heading` | **No** — the field set `09` requires is dropped, the mapping is inverted, and no unit produces the `Siting` (D8) |
| D5 (entities read but not owned; demand specified twice) | Partly fixed: `SC-BAY-0109`, ownership stays cell-based | **No** — `SC-BAY-0109` is either unsatisfiable (`Occupied`) or fires on a walking cow (`Owned`); `§10.5` does not exist (D7) |
| D6 (focus failure has no channel; non-spatial subject has no locus) | Fixed: `Outcome`, `SC-FOC`, `Locus.SUBJECT` | **No** — `Locus.SUBJECT` cannot be constructed from `05`'s `Locus`, and `05` is absent from `§15.4` (D7) |
| D10 (settlement outside the match) | Fixed: settle inside `SETTLE`; `Formed` requires `Settled` | **Partly** — settlement is inside, but the row's predicate has no mode (D1), and the promotion it guards is published before the verdict (D9) |
| `§15.2` D5 (two promotion authorities) | Fixed: one key universe, one writer | **No** — one writer with no instance subject (D6) |
| `§15.3` D4 (footprint excludes globally judged cells) | Fixed: `ClaimScope`, DESIGN checks, `SC-CLM-0104` warning | **No** — `SC-BAY-0108` forbids the read that `Declared` would need (D5) |
