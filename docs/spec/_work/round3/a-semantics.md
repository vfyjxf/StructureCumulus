# Round 3 — semantics and use cases (22 rev 5, with 23 and 24 folded in)

## 1. Scope and method

Object: `22` rev 5, with `23` (check/settle split) and `24` (Coverage / Footprint / Sharing, R1–R4)
as decided. For each claim I asked what a machine does — which unit emits which code, which call
writes the ledger, which stage selects which unit, which document field carries which value — and
compared answers across files. A defect is two documents disagreeing, or a stated proof (rule, code
row, gate) that cannot deliver what it claims; each names the requirement (`00` §3) or invariant
(`00` §4) it breaks. Code not opened.

## 2. What I checked

`22` §0–§16, `23`, `24` in full. Walked `24` §6's eight machines against `07` §4.6, `08` §2–§4.7,
`09` §4.1–§4.8 + B-1…B-12, `10` §4.1/§4.5, `11` §4.1/§4.5/§5, `13` §4.1–§4.3, `16` §4.10,
`17` §2–§10, `18` C-11/C-28, `20` §5.3/§5.4, `21` §3, `00` R-08/R-11/R-14/I-6/I-7/I-10/I-12,
`19` R2/R7, `status.md` B2/C. Executed every document on `23` §9's and `24` §9's migration lists.

## 3. Ranked defects

### D1. The design of record still makes `check` write

**Scenario.** `22` §10.3 runs the worker match, then the server-thread `MatchSettler`, then returns,
and §9.1's `MatchRequest` carries `Stage`; a watcher therefore probes with `stage = SETTLE` — `23`
§1's third row, "look but do not register" is inexpressible — and a preview registers a claim. A
host implementing `23` §2 instead finds `MatchSettler`, the scheduler's `POSTED → SETTLED` step
(`16` §4.10), `/cumulus build`'s `SETTLE` argument and `18`'s gates C-11/C-28 still in place.
**Violated.** `22` §2's R-11 mechanism ("settle inside the call") and R-14's ("facade
`check/derive/repair`; `Formed` only at SETTLE") name mechanisms that no longer exist; `23` §9's
list is executed nowhere (`22` §10.3, §15.1 D10, `08` §4.5, `11` §4.1, `15` §3) and omits
`02`/`16`/`18`.
**Why the stated proof fails.** D10's disposition proves only that a write follows a match, never who
asks for it, so `23` §1's first two rows (conflicts surface after the materials are spent; no
`release`) stay unrepaired while the canon offers a writing `check`. Of the two readings — `check`
writes (canon) / reads (23) — the first breaks `23` §2 and `24` §2.
**Minimal fix.** Execute `23` §9; add `02`, `16` §4.10, `18` C-11/C-28 to it.

### D2. Deleting `Check.stage` breaks I-6, R-08's fast-fail, and every stage-shaped mechanism

**Scenario.** `23` §7 drops the declared stage from `Check` and the document, ordering the plan
"dependency → read-closure volume ascending → declaration index"; but `09` §4.8's
`Blueprint.expand(Stage)`/`CheckPlan(Stage, …)`, `22` §7.1 and `11` §4.1's `expand(request.stage())`
are the only selectors of which checks run. A `PROBE` then either runs every check — `17` §2's
"cheap `PROBE`" bills the full `FORM` closure over 194 wall cells (`22` §9.5) — or runs `23` §7's
three engine-filled values (blueprint validation / world pass / settlement), which cannot separate
cheap from expensive world passes. Computations keep `stage()`.
**Violated.** I-6 ("Every check belongs to an explicit `Stage` with its own `CheckPlan`, budget and
failure policy") and R-08; `22` §8.1's Stage→Tier table and `Retention.CONTEXT` ("the stage's tier
frame") lose their producer; `13`'s bundle order key 2 (`Stage` incl. `STALE`) and its dedup key
read an unproduced value, while `22` §11.1 orders by "severity, locus, code, insertion".
**Why the stated proof fails.** `23` §7's engine-filled values are not the five-value `Stage` enum,
and its cost ordering contradicts `22` §7.1 ("cost is not a priority … no per-unit tie-break score")
and `09` §4.8 ("`specificity` … never a plan key"); `10` §4.5 still requires `checks[].stage`.
**Minimal fix.** Keep a per-unit stage (delete only `Stage.STALE`), or name the replacement selector
(a `probe: true` flag) and the tier a stage-less attempt publishes into, reconciling `22` §7.1 /
`09` §4.8 with `23` §7 in one place.

### D3. The new claim vocabulary reaches the code sketch but not the document grammar

**Scenario.** `10` §4.5 — owner of the grammar — says a bay is
`{id, box, coverage, mode?, scope?, description?}` ("`mode` defaults to `exclusive`, `footprint` to
`bound`"), and `10` §4.1 declares `FootprintRef.ScopeKind{OWNED, OCCUPIED, DECLARED, AUDIT}`: the
pen of `24` §6, written as `21` §3/`22` §12.3 spell it (`"sharing":"shared","footprint":"bound"`),
has no `sharing` key, no `none` and no `bound` value. `08` §3 agrees (`Footprint permits Owned,
Occupied, Declared, Audit`; `ClaimRow(cell, mode, …)`; §8's `Bay…mode()/scope()`), and `21` §3.1 says
"coverage, mode and scope" above a JSON that says `sharing`/`footprint`.
**Violated.** R-09 and I-12 (a blueprint is a `BlueprintDocument`): none of `24` §6's eight machines
is authorable — the new keys are `SC-DOC-0002` or silently default to `scope: occupied` (= `Bound`).
Cause: `24` §9's Documents list omits **`10`** and leaves `08` half-migrated (§2/§4.3 updated, §3
not).
**Why the stated proof fails.** `24` §7.4 and "the document JSON changes" hold only where §9 says,
so `22` §12.3's compact example is uncompilable against `10` §4.5, and `10` §4.1's `ScopeKind`
re-introduces the "one value, two jobs" that `24` §7.2 deleted.
**Minimal fix.** Add `10`, `08` §3/§4.6, `21` §3.1 to `24` §9; keys `coverage`/`sharing`/`footprint`
with `every_cell|one_subject|any_subject`, `exclusive|shared`, `box|bound|declared|none`; delete
`FootprintRef.ScopeKind`; rename `08` §3's arms and `ClaimRow`'s `mode`.

### D4. R2/R3 have no footprint guard, so `24` §6's own machines are refused or unchecked

**Scenario.** The two-cell door is declared three ways: `24` §6 "`ONE_SUBJECT` + `Bound`"; `09` §4.1
"`ONE_SUBJECT` + `Box`: the door case"; `17` §9's `Coverage.ONE_SUBJECT … Footprint.occupied()`, with
`17` §2 adding "`ONE_SUBJECT` + `Box` would be `SC-CHK-0106`". Read R3 ("the bound footprint must
equal the bay") unconditionally: only `Bound` resolves a bound footprint (`08` §4.3), so a
`Box`/`None` bay compares ∅ with `bay.cells` and both the door and machine 8
(`ONE_SUBJECT` + `None` + `SHARED`) are refused at settle, making `24` §6 illegal under §5. Read R3
as `Bound`-only: nothing compares the bound subjects with the bay for `Box`, so a half-bound door
settles a `Box` claim over both cells.
**Violated.** R-06 (the door is the canonical heteromorphic case) and `22` §14's P11 ("a short check
cannot claim a whole bay").
**Why the stated proof fails.** R3's two enforcement sites (`build + settle`) never say which
footprint a `Box` bay compares, and `ONE_SUBJECT` completeness is a *match* obligation (`24` §4 Q1)
placed at settle by §5, at build by `09` §4.1, and at build with another code by `17` §2.
**Minimal fix.** Guard R2/R3 with `footprint == Bound`; make `ONE_SUBJECT` completeness for
`Box`/`Declared`/`None` a `Coverage` match obligation reported `MISMATCH`/`SC-EXP-*`; delete
`17` §2's `SC-CHK-0106` sentence.

### D5. R1–R4 wrongly allow overlapping `Declared` ownership outside the bay

**Scenario.** Two disjoint bays, A `cells [0..1]` and B `cells [10..11]`, both `EXCLUSIVE`, A
`Declared({5..8})`, B `Declared({6..7})`: `24` §4 Q2 never bounds a `Declared` set to `bay.cells`,
R1–R4 say nothing about static footprints, and `09` §4.2/B-7 compares bay cuboids only, so
`build()`/`compile()` pass. At settle both bays of one `(owner, siting)` write cells 6 and 7, and
`ClaimMatrix.conflicts` "returns false for two rows of one `(owner, siting)`" (`08` §4.2) — one cell
held twice by one owner under two bay ids.
**Violated.** `24` §1's premise ("must have exactly one answer"), R-11, I-10.
**Why the stated proof fails.** `08` §4.2 claims "the static half (`Box`, `Declared`) is decided at
`DESIGN`", but the only DESIGN rule compares bay cells via `Overlap(low, high)` on bay ids, so
declared sets are never compared and the self-conflict exclusion hides the collision. I overturn
`24` §8 row 1's framing: it registers this as an `SC-BAY-0108` question, yet `13` §4.3 and `08`
§4.3/`22` §10.2 define 0108 over `reads().cells`, a read rule that cannot close an ownership hole.
**Minimal fix.** Extend B-7 to sweep every bay's static owned set (`Box` = `bay.cells`, `Declared` =
the set; skip `Bound`/`None`), requiring a declared `Overlap` for intersecting pairs even when
cuboids are disjoint, plus "`Declared ⊆ bounds`" with `SC-BPT-0002`.

### D6. `MatchResult.Dropped` has no arm in either canonical signature

**Scenario.** Gate term 4 fires: `22` §9.2 says `Dropped(SNAPSHOT_CHANGED)`, `20` §5.4 says "either
verdict returns `MatchResult.Dropped`", `11` §3 declares and returns it, and `23` §4 keeps it
("`settle` returns it too", §3.1 3a). But `22` §10.4's sealed family permits only `Admitted`,
`Formed`, `Failed`, `Undecided`, `Deferred`, and `15` §3 lists the same five.
**Violated.** R-13 (sealed, exhaustively switchable results) and I-14: an implementer must invent an
arm or fold a dropped attempt into `Undecided`, and `23` §3.1 step 3a has nothing to return.
**Why the stated proof fails.** §9.2's mechanism and §10.4's result family sit in one file
unreconciled, so P8's gate names an outcome the type cannot carry and `23` §4's "Dropped: unchanged"
row is false.
**Minimal fix.** Add `Dropped(cause, findings)` to `22` §10.4 and `15` §3, or delete the arm
everywhere (gate → `Undecided`) and update `20` §5.4, `11` §3/§4.1, `23` §3.1/§4.

### D7. `SC-BAY-0107` is re-pointed; `SC-CHK-0106` now means three things

**Scenario.** A door bay binds one half at settle: `24` §5 R3 and `09` §4.1 emit `SC-BAY-0107` for
"the bound footprint must equal the bay", while `13` §4.3 and `09` B-10 make the same number mean
"the checks' static decision cells do not cover `bay.cells` for `EVERY_CELL`", raised by `build()`
before a world exists — one code, two failures, two layers. `SC-CHK-0106` is R1, R4, and `22` §12.2's
"`focus.subjectKind != expectation.subjectKind`", while `07` §4.6 gives that design failure
`SC-CHK-0101` and `15` §4.3 gives a document's version `SC-SPI-0002`; `13` §4.3 has no 0101 row.
**Violated.** R-07 and I-7, plus `13` §4.3's stability rule ("a behaviour change that alters what an
existing code *means* is a new code"), which `24` §5's row breaks by re-pointing 0107.
**Why the stated proof fails.** `24` §9 lists `13` as migrated, so 0107's pre-`24` row was never
updated, and 0106's third statement is in neither `13` nor `15`: two units (U3, U5) and two layers
emit one number with three meanings.
**Minimal fix.** Mint a new `SC-BAY` code for R3's shortfall (or narrow 0107 to R3 and mint one for
the `EVERY_CELL` rule); give 0106 exactly R1 and R4; move `22` §12.2's mismatch to `SC-CHK-0101`
with a `13` row.

### D8. An internal overlap is an exception, a finding and a code, and B-7 has no code

**Scenario.** A datapack author writes two overlapping `EXCLUSIVE` bays. `23` §6 says the
inside-blueprint layer fails with "`SC-BAY-*` / `SC-CLM-0001` (thrown at build)"; `09` §4.7 makes
B-7 a `build()` `IllegalArgumentException` for callers that are code with "no document position to
hang a `Label` on", while `10`'s validator "runs the same table first and reports `Finding`s with
`Locus` and `SourceSpan`"; `08` §4.6 lists `SC-CLM-0001` as a DESIGN/SETTLE ERROR over "the
intersection cells" although `13` §4.3 requires a PRIMARY label for it and its own overlap code is
`SC-BPT-0002` ("`mode`s that cannot coexist", dead vocabulary). B-7 is the only row of B-1…B-12 with
no code in `09` §4.6.
**Violated.** R-07/I-7: the failure a document author hits most often has three mechanisms and no
code, so `/cumulus lint` cannot report it and a consumer cannot key on it.
**Why the stated proof fails.** `09` §4.7's "proof obligation, not a validator" split needs a code
and locus for every `build()` row; B-7 has neither, and `08` §4.6's DESIGN row asserts a claim locus
for a conflict no ledger exists to detect.
**Minimal fix.** Assign B-7 → `SC-BPT-0002` (reworded without `mode`); mark `SC-CLM-0001` SETTLE-only
in `08` §4.6; delete `23` §6's "`SC-CLM-0001` (thrown at build)".

### D9. The door's focus and subject kind are settled twice, with opposite answers

**Scenario.** `07` §4.6 requires `focus: door` → `DoorView` and says a door expectation consumes
`DoorView` "and never a `BayView`, so pairing it with a `BayView` focus is `SC-CHK-0101`"; `17` §9
repeats "never against a `BayView`". `22` §6.3 ships `Focuses.door` and `Focuses.bay()`, and `22`
§13's door walkthrough FORMs with "`Focuses.bay()` + `Expectation<BayView>`". `22` §15.4's "door is
one `BayView`" closed the *claim*, not the focus kind, so both paths survive.
**Violated.** R-06 and R-07: the two readings give different codes for one author input, and
`07` §4.6's `DecisionKind` column keys coverage off the focus kind and has no `bay` row, so R4's
"checks that do not match the coverage" has no table for the canonical door.
**Why the stated proof fails.** The round-2 closure named a claim shape, not a check path; the
pairing rule and the walkthrough cannot both be the mechanism, and `13` has no `SC-CHK-0101` row for
whichever survives.
**Minimal fix.** Delete the `door`/`DoorView` path from `07` §4.6 and `22` §6.3 (or delete `22` §13's
BayView door), and add a `bay` row to `07` §4.6's `DecisionKind` table.

### D10. The facade's operation count is frozen at eight and `settle` is a tenth

**Scenario.** `00` R-14 ("the facade has eight operations") and `01`'s `StructureKernel` row
("eight operations: `create`, `document`, `compile`, `match`, `derive`, `solve`, `explain`,
`ledger`"), with `19` R2 ruling "the operation count stays eight". `15` §3 shows `create`,
`document`, `compile`, `siting`, `check`, `derive`, `repair`, `explain`, `explainOrFallback`,
`ledger` — ten, with no `match`/`solve` — and `23` §2 adds `settle`.
**Violated.** R-14: three counts coexist and none matches the code, so a reviewer cannot tell
whether `siting`, `explainOrFallback` or `settle` is an operation or a helper.
**Why the stated proof fails.** `19` R2 predates `23`, and neither `23` §9 nor `24` §9 lists `00`,
`01` or `19`, so the frozen count became wrong silently instead of being re-ruled.
**Minimal fix.** Fold the write into the ledger port (`ledger().settle(…)`) to keep eight, or record
a fresh count in `19` and update `00` R-14 and `01`'s row in the same change.

## 4. Checked and cleared

- `SC-BAY-0111` has a rule and a producer path (`13` §4.3, `08` §4.6, `09` §4.1 R2).
- `SC-BAY-0108`'s missing producer is registered (`status.md` C, `24` §8) — not counted (see D5).
- `24` §4 Q3's "`SHARED` conflicts with nobody" is consistent with `08` §4.2's matrix read as "the
  `SHARED` value adds no conflict" (`EXCLUSIVE` conflicts with every owner on its own row); the
  `SHARED` cases in `22` §13/`08` §6 hold.
- The mob pen (`ANY_SUBJECT` + `Bound` + `SHARED`) survives `22` §16.6 (R1 asks only for a non-`None`
  footprint); `23` §8's overlapping-window item stays registered.
- Pipe corner (eight asserted / four owned) and furnace cavity (`Box`, 100 air cells) agree across
  `22` §10.2/§13, `08` §4.3, `24` §4 Q2.
- `22` §12.3's furnace JSON matches `17` §3 and `21` §3 (`10` is the outlier — D3).
- `zh/**` is registered pending (`22` §15.5), so its `mode`/`scope` and `Stage` text is not counted.

## 5. Could not rule

- `23` §3.2 requires `settle` to re-read and re-judge; `02`'s threading table says the ledger is
  "frozen at dispatch (`FrozenLedger`) and read-only during a match" and `16` §4.10 keeps a frozen
  revision. Whether the re-read hits the live store or the frozen view — hence whether
  `RevisionMoved` is observable — needs the host SPI.
- `20` §5.1's "claim settle/release → `SavedClaims.settle` → claim cells ∩ windows" may re-dirty a
  `Bound` entity bay forever (settle → mark → `SNAPSHOT_CHANGED` → discard → `DIRTY_UNSTABLE`);
  `status.md` GAP-06 says no dirty index exists, so it cannot be settled on paper.
