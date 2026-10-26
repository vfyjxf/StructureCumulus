# Stage-3 Role 3 — audit of the decided list and the question inventory

Scope: `alignment-report.md` §8.1's eleven items, L's four precision corrections, and the residue
that neither R1–R14 nor §8.1 holds. Every claim below carries a quoted anchor; code anchors were
read in the tree at the current working copy, doc anchors at their file and line today.

## 1. Decided-list audit

| # | Item (§8.1) | Evidence as cited | Exists and decides? | Note |
| --- | --- | --- | --- | --- |
| 1 | `check` does not publish; publishing belongs to `settle` or the engine's closing step | `19` R27, `I-10`, `G9` | **partly** | R27 and I-10 do forbid the write, but (a) `G9` is not evidence of a decision — its own verdict column reads `undecided` / `RULE` and it publishes three options; (b) `22` §16.1 item 8 still asks the question in the opposite direction and is not marked superseded; (c) the clause "or the engine's closing step" *licenses the defect*: `MatchSession.publish()` is exactly the engine's closing step and runs on every stage. Details below. |
| 2 | Internal bay overlap is a defect, never a silent de-dup | `I-10`, `D-4` | **no** | `09` §4.2 makes a *declared* `SHARED`+`SHARED` overlap explicitly legal ("legal; those cells are checked and charged twice (R-08)"), and the item does not carry that exception. Implemented literally it would report a legal pair as an error. It also depends on `F9`: the licence pair is discarded by `build()`, so nothing can test "declared". |
| 3 | `DecisionKind` and `Coverage`: keep one enum | `RULE-D2`, `F7` | **no** | `RULE-D2` recommends **two** enums: "Recommendation: **(a)** … the comparison in `Bay.validate` is meaningful only between two independently declared triads, and (b) would delete the only check". L reaches the same place: "RULE-D2 → (a) (`01`'s two triads) … If the lead wants one type, that is a `24` amendment, not a free option." `F7` does not discuss merging the two enums at all; it is about `Coverage × ClaimScope` legality rows. The item also duplicates the open R10 and answers it *against* its own evidence. |
| 4 | Delete `PrescreenIndex`/`Census`/`AnchorCandidate`/`Fingerprint` | `G12`, `11` §8 self-says replaced | **yes** (code grep empty; `11` §8 names them) | But `11` §8's sentence reads "…are **carried unchanged**: the `SC-ENG-*` family … and `PrescreenIndex` / `Census`, which `22` §7.1 replaces with `Check` units but does not rename" — a reader can take that as "keep the names". The item's ripple is unwritten: `18` C-13 (`ANCHOR`/`CENSUS`/`CLAIM` carriers), C-14 (`Fingerprint.of(WorldView, Blueprint)`), C-17 (bounds/census charge) and `SC-ENG-0104`'s only would-be producer all die with it. |
| 5 | `UndecidedReason`: `01`'s eight values; code adds `LEDGER_UNAVAILABLE` | `G11`, L | **half** | The count is right (`01:196` lists eight including `LEDGER_UNAVAILABLE`; `UndecidedReason.java:5-12` has seven). What it does not dispose of: `11` §4.4 ranks `CANCELLED > … > UNREADABLE > ABSTAINED > NO_STEPS`, four names in *no* enum anywhere, and `11:107` uses `NO_JUDGING_UNIT` as a reason while the code uses it as a `FindingCode`. It also does not name the producer: today "no ledger" is `DESIGN_DEFECT` at two sites. |
| 6 | Delete the two 2-arg `Locus` factories | `H-R5(A)`, L | **yes** (no callers) | `Locus.java:37` `local(CuboidSet, SourceSpan)` and `:45` `world(CuboidSet, SourceSpan)` have no call site anywhere in `src/`; every `Locus.*(` call is a 1-arg `local`/`world` or `definition`. But `H-R5(A)` is a two-part rule — "and make `withSource` refuse every frame but `DEFINITION`; 13 §4.4's … sentence is deleted" — and the item states only the deletion. Deleting the factories alone does **not** make `13` §4.2's `DEFINITION ⟺ source != null` true, because `Locus.withSource` (`:53-55`) still lets a `LOCAL`/`WORLD` locus carry a span. |
| 7 | `SC-CTX-0109`: keep only the draft-side exception channel | `H-R3(A)`, L | **no — inverted** | `H-R3(A)` says the opposite: "The engine's plan validation is the only producer … the draft's verb check becomes a programmer precondition that throws `IllegalArgumentException` with no code in the message". L's note quoted in the item ("draft 无法携带带标签的诊断") argues the same way. As written the item keeps the one channel that cannot satisfy `13:366`'s `✓` (a PRIMARY label is required) and drops the one that can. |
| 8 | `anyOf()` with an empty sub-array: require `n ≥ 1` | `RULE-D1(c)`, L | **yes** | `07:189`'s `anyOf` row refutes on `m = a = u = t = 0`, which a zero-child array satisfies; the code falls out of the loop to `Reply.abstain()` (`Expectations.java:106-136`). `RULE-D1(c)` is "keep the code's ABSTAIN for both and add `n ≥ 1` to `07` §4.3", i.e. a documented precondition, not a construction refusal — the item's verb "要求" should say which. |
| 9 | U6 `kernel.document`: create the package, do not merge into `blueprint` | `F1`, L | **yes** | `00:90` U6 row, `03:42` `kernel.document` row and `10:49` `package dev.vfyjxf.cumulus.kernel.document;` all commit to a separate unit; `F1` proves `blueprint` is not an absorber. Two caveats: the real competing option is *delete U6* (`F1` §4: "a tree that keeps today's shape has *no* document unit"), not "merge into blueprint"; and `DataView`/`DataKind`'s home — L §4.7's separately decided `(a) base` — is absent from §8.1, so the item cannot be written out without it (see MQ-3). |
| 10 | `OccupantRef` carries `SnapshotId`, loses `footprint` | `19` R8 〔已核〕 | **yes** | `19:22` "`OccupantRef` includes its `SnapshotId`"; `01:70` "`(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)`"; `06:40` the same four components; `06:62` puts `footprint` on `OccupantSnapshot`. Code is `(InstanceId, type, kind, anchor, footprint)` (`OccupantRef.java:13-14`). Ripple the item omits: `ClaimPlanner.java:78,113` derive the claim cells from `subject.footprint()`, and `08:160` says `Bound` comes "from `BayBinding.subjects()`" — so the planner must gain a ref→snapshot lookup. |
| 11 | `NestedView` granularity: not a full `WorldView`, per the code's `NestedLevelSnapshot` | `C-14(b)` | **substance yes, option mis-cited** | `C-14`'s recommendation is **(b)**, which *keeps* `NestedView` as U2's interface name; the item describes **(c)** ("keep the code's `NestedLevelSnapshot` class as the only U2 child type and delete all `WorldView`-inheritance claims"). Both kill `06:85`'s `interface NestedView extends WorldView`, but they differ on the surviving name (`01:75`/`01:325` pair the two names today) and on `ancestry()`, which `06:85` requires and `NestedLevelSnapshot` does not have. |

### 1.1 What each failure costs the hand-off

- **#1**: as written, an implementer can keep `store.publish(draft)` on the `check` path and still claim
  compliance ("the engine's closing step did it"). The allowed publisher must be named as *the settle
  path*, and `22` §16.1 item 8 must be struck in the same edit. Separately, the same `publish()` path
  re-emits a `Formed` (`MatchSession.java:338` → `InstanceRecord.reemit`, `:57-65`, `generation + 1`),
  which `01:192` ("produced only by `settle`") and `22` §10.4 forbid — that is G10, an *undecided*
  question sitting inside the item's territory (MQ-8).
- **#2**: the item is not implementable until `F9` is answered, because `09` §4.2's legality test is a
  pairwise *declaration*, and `BlueprintBuilder` discards the declarations after `build()`
  (`BlueprintBuilder.java:23-24`, "the mandated `Blueprint` shape carries no overlap list"). The code's
  predicate is also narrower than the item: `internalConflict` (`ClaimMatrix.java:79-87`) requires
  `!a.bay().equals(b.bay())` and `a.mode().excludes() || b.mode().excludes()`, so the legal
  `SHARED`/`SHARED` pair is already silent, and `D-4(b)`'s "delete the second predicate" would break
  that silence.
- **#3**: R10 must be rewritten, not just R1–R14's neighbours: its option (a) ("只留 `Coverage`") is
  what §8.1 now asserts, and L says taking it requires amending `24` §4.1/§9.1 item 1 and `01:116`.
  Whichever way it goes, `07:231`'s `block` row has to go (a no-arg `decisionKind()` cannot be
  bay-dependent) — that part *is* decided.
- **#5**: without the precedence-list and producer halves, the regenerated engine has a reason constant
  nobody raises and a ranking sentence over four constants that do not exist.
- **#7**: as written it makes `13:366`'s `P ✓` unsatisfiable and leaves the code's
  `failed(WRONG_VERB, …)` (`MatchSession.java:72,208,212`) as the only labelled producer — the opposite of
  the item.
- **#11**: the surviving name is public API; picking (c) silently deletes `NestedView` from U2 and makes
  the U3 record the only type of that name.

### 1.2 Items L §4 calls decided that §8.1 does not carry

L §4 enumerates ten evidence-decided rulings; §8.1 overlaps six of them (G9, D-4, RULE-D2, G11, G12,
D-6) — and the RULE-D2 one is stated backwards. Four are absent:

| L §4 | Ruling | Anchor |
| --- | --- | --- |
| 4 | **RULE-4 → (B)**: `24` §5's code column stands (`R3 → SC-BAY-0107`, `R4 → SC-CHK-0106`); `13:342`/`13:349` are rewritten to match, and the code's third case ("a check decides cells outside bay", `Bay.java:58-59`) gets a home. | `24` §5; `13:342,349`; `Bay.java:58,74,82`; `ClaimPlanner.java:40` (`BOUNDS_MISMATCH = (BAY,107)`, the code's only *finding* producer for that code) |
| 6 | **A5 → (a)**: `OwnerId` and `InstanceId` both to `01` §B (`base`). | `a-base.md` A5; `03` §3's `claim` row lacks `occupant` while `ClaimRow` carries `OwnerId` |
| 7 | **F1-side → (a)**: `DataView`/`DataKind` stay in `kernel.base`, not `document`. | `10` §2; `19` R14; `01`; `f-blueprint.md` §4 item 2 ("`base` (spec as written) keeps U3 able to read factory arguments") |

`RULE-4` is not mentioned anywhere in the consolidated report (grep for `RULE-4` is empty), so it is not
even in §8.5. `A5` and the `DataView` home are in §4 only as file edits ("按 D-1 移到同一节", "要么实现，
要么从词汇表删除"), which loses the decision. L §4.5's other decided item — "D-1's `ReadScope` leg → keep
it in `base` with base-only components" — is *inside* R2 but R2's option set does not contain it (R2(a)
is "留在 `base` 并允许三分量"; L's version forbids the `ContextKey`/`EntityWindow` components), so R2
must be re-cut before it can be answered.

## 2. Precision corrections (L's four)

| L's claim | Quoted evidence | Verdict | Consequence |
| --- | --- | --- | --- |
| `Reply.combine` is package-private, not public (D-12/D3) | `Reply.java:73` `static Reply combine(Reply left, Reply right) {` — the line sits inside `sealed interface Reply permits …` (`:9`), so the member is **implicitly public**; `engine/MatchSession.java:291` `combined = Reply.combine(combined, reply);` calls it from another package and the tree builds | **refuted** | L's correction is backwards. Consequence for R11: "包私有的 `combine`" is a *new restriction* the code does not have and that no doc asks for; if R11 keeps it, `MatchSession` loses its fold and must be given another door. The underlying D3 report claim ("`combine` … public") was right about effect, wrong about the reason (it is public because it is an interface member, not because of a modifier). |
| `Bay.java`'s `CHK`/`BAY` literals are exception message text, not diagnostics | `Bay.java:34` `"SC-CHK-0106: " + coverage + …`; `:58` `"SC-BAY-0107: " + check.id() …`; `:74`, `:38`, `:67`, `:82` the same shape; `grep 'Family.CHK' src/` empty; `13:225` allows `build()` to throw, `13:282` routes an extension's IAE to `SC-SPI-0001` | **confirmed** | It does not by itself settle RULE-4: whether a DESIGN-time violation is an exception or a labelled finding is the remaining half (L §3.8 says so). The one real `SC-BAY-0107` finding producer is `ClaimPlanner.java:40,88-93` (R3's footprint equality, at `SETTLE`), so after RULE-4(B) the code needs `BAY-0107` at `Bay.java:82` (kind) and `CHK-0106` at `:74` (completeness) — i.e. the two sites swap families. |
| Three, not four, of the nine facade operations exist (`check`, `derive`, `ledger`; `settle` is an inline path inside `check`) | `15:28` lists nine (`document`, `compile`, `siting`, `check`, `settle`, `derive`, `solve`, `explain`, `ledger`); `StructureKernel.java` has `check(MatchRequest)`, `derive(Formed, ContextStore, List<Deriver<?>>)`, `ledger()` (and `catalogs()`); the `SETTLE` path is `MatchSettler.inline(claims)` passed by `check` (`StructureKernel.java:73-79`) | **confirmed** | Keep R7's "五个缺失" as-is (5 missing + 3 present + `settle` inline = 9). Do not let the count drift into "four": `catalogs()` is not one of the nine, and `settle` is not a member. |
| `20` §4.2 contains no `CaptureLimits` | `20:214-244` (the whole §4.2) uses "section/cap/byte limit exceeded", `Captured(LevelSnapshot, CaptureIssues)`, `Refused(...)`; `CaptureLimits` appears only at `01:323` and `06:131-132` | **confirmed** | C-15's row in `c-occupant.md` ("`20` §4.2 replaces the caps with `CaptureLimits` and `MatchPolicy`/`SchedulerOptions`") is wrong about §4.2: `MatchPolicy`/`SchedulerOptions` are at `20:60-64`/`:433`, and `CaptureLimits` is in `01`/`06` only. Any R6 fact sheet that repeats C-15's attribution is quoting something that is not there. |

## 3. Missing questions

Ordered by how much they block. "Decided by L" means the user does not have to choose, but the ruling is
absent from §8.1, so nothing downstream knows it.

### MQ-1 — Which family owns an R3-kind and an R4-completeness violation, and what code does "a check decides cells outside its bay" carry? (RULE-4; decided by L §4.4, absent from the report)

`24` §5: "R3 → `SC-BAY-0107`", "R4 → `SC-CHK-0106`"; `13:342` gives `BAY-0107` R3's *kind* half;
`13:349` gives `CHK-0106` "checks that do not match the coverage (R4)"; code is `CHK-0106` for kind
(`Bay.java:82`), `BAY-0107` for completeness (`:74`) and for outside-cells (`:58`).
L's ruling is (B): follow `24` §5 literally, rewriting `13:342`/`349`. Still open inside it: the
outside-cells case has no row in `13` at all, and `Bay.java:37-40`'s `PRESENCE ⇒ Declared` clause is an
extra rule `24` never authorised (`f-blueprint.md` F7 calls it an accident). Both must be written or
both deleted.

### MQ-2 — Does `claim` gain the `finding` edge? (A3)

Doc: `03` §3's `claim` row; code: `ClaimRow`/`ClaimVerdict`/`ClaimPlan.Invalid` all carry `Finding`
(`claim/ClaimVerdict.java:21` `record ClaimConflict(List<ClaimRow> rows, FindingBundle findings)`).
This is not a free-standing question — it is the edge set that follows from R1's answer, and Role 1's
move set must produce it. Today `03` §3's `claim` row is wrong twice over: no `finding`, no `occupant`
(see MQ-4). If it is left out of R1's write-out, a regenerated `claim` cannot name `Finding`.

### MQ-3 — Where do `DataView`/`DataKind` live: `base` or `document`? (F1-side; decided by L §4.7, absent from §8.1)

`10` §2 and `19` R14 put them in `kernel.base`; `03:42`'s `kernel.document` row lists `base` among its
dependencies either way. L rules (a) `base`, because (b) needs `expectation → document`, which
`02` §2's rank forbids. This is the one decision §8.1's item 9 cannot be written out without, and §8.1
item 9 does not mention it.

### MQ-4 — Where do `OwnerId` and `InstanceId` live? (A5; decided by L §4.6, absent from §8.1)

`01` §D/§I scatter them; `03` §3's `claim` row lacks `occupant` although `ClaimRow` carries `OwnerId`,
and `Locus ∈ space` forces `InstanceId ≤ space`. L rules (a): both to `01` §B. §4's `01-glossary` bullet
folds this into "按 D-1 移到同一节", which loses the RULE and the recommendation (`recheck-k-consistency.md`
K5.5 says exactly that).

### MQ-5 — Are `ReadScope` and `ReadSet` one type or two, and who produces the "actually read" set?

`01:108` "Read scope | `ReadScope` | A unit's declared read closure"; `01:206` "Read set | `ReadSet` |
`(CuboidSet cells, Set<ChunkKey> chunks, Set<OccupantRef> occupants)` actually read." Those are different
concepts, so this is not a spelling fix. `11:77` requires `public ReadSet readSet(); // union of
outcome.stepReads()`, and `stepReads()` is defined nowhere; `11:306` needs `ReadSet` for the
`closureDigest`/`CacheKey` validity rule, while the code's only feed is
`MatchOutcome(… ReadScope reads)` (`01:199`) and `CacheMode.java:3-4` says "Nothing reads this and no
attempt cache exists yet". Options: (a) one type — delete the `ReadSet` row and write C-16/C-18 against
`ReadScope`; (b) two types — define `stepReads()`/`readSet()` and say which member the cache reads;
(c) delete `ReadSet` **and** the attempt cache (`11` §5, `20` §8) from the regeneration scope. Each
choice rewrites `18` C-16 and C-18 and one of `11`/`20`.

### MQ-6 — Is the coarse step vocabulary (`BOUNDS`/`CENSUS`/`ANCHOR`/`BAY`/`SEAM`/`CLAIM`) a kernel type at all?

`02:103-104` "`PROBE` | yes, coarse | `BOUNDS`, `CENSUS`, `ANCHOR`"; `17:174` "`BOUNDS` passes …
`CENSUS` observes … `ANCHOR` reads the origin cell"; `18:61-62` C-12 "every step kind has a carrier" and
C-13 "`ANCHOR`, `CENSUS` and `CLAIM` steps receive their carriers explicitly". No unit declares a step
type; `09:144`/`CheckPlan.java` are `(stage, computations, derivations, checks)`. Options: (a) define a
`Step`/`StepKind` and a carrier per kind (new types in U5/U7); (b) delete the vocabulary from `02`/`17`
and rewrite C-12/C-13 at the check level (the code's `FocusContext`/`ComputationContext` already carry
them); (c) keep the words as prose names for plan phases, with C-12/C-13 deleted. This is entangled with
decided item 4 (the prescreen *is* `BOUNDS`/`CENSUS`/`ANCHOR`) — deleting the prescreen silently deletes
C-13's three carriers and C-17's charge rule.

### MQ-7 — Do `Codes`, `Stability` and `ExplainBook` exist as types, and where do they live?

- `Codes`: `15:32` "`Codes` | final class, constants | the kernel-shipped `FindingCode` instances, one
  per row of `13` §4.3"; used at `12:230` (`Codes.CAVITY_TOO_SMALL.finding(...)`), `14:341`
  (`Codes.CLAIM_CONFLICT`), `14:345` (`Codes.MISSING`), `15:414`; `15:311` demands it be "bijective with
  `Codes`". No `Codes` type, no `01` row (A11), and `FindingCode` has no `finding(...)` member
  (`FindingCode.java`: `of`, `of(String)`, `parse`, `text`, `compareTo`, `toString`), so
  `Codes.CAVITY_TOO_SMALL.finding(...)` asks for an API nothing describes; `CAVITY_TOO_SMALL` is also
  not a `13` §4.3 row.
- `Stability`: `01:35` "the promise attached to an SPI name"; `13:390` "`Stability` (U0) rides on the
  registry entry, not on the finding"; `15:13` lists `Stability` under U11's *Owns* column and
  `15:280` says it is "Recorded in the kernel-owned table **and in `Codes`**". Three statements, two
  homes (`base` per `13`, `spi` per `15`), two subjects (SPI names per `01`/`15`, codes per `13`), and
  no row in `02` §2 or `03` §3.
- `ExplainBook`: `13:210` declares the interface, `01:250` has a row, and §5's blank list already
  carries it as unbuilt.

Options: build the three (`Codes` in `spi` with one constant per `13` row; `Stability` where the table
it decorates lives; `ExplainBook` per `13:210`), or delete them from `15`/`04`/`12`/`14` and rewrite
every `Codes.X` sketch as `FindingCode.of(Family, N)`. R12 covers the *registry table*'s ownership and
producers; it does not cover whether these three types exist.

### MQ-8 — May a non-settle call re-emit a `Formed`? (G10; coupled to decided item 1)

`01:192` "produced only by `settle`"; `22` §10.4 the same; `11` §4.6 "the previous `Formed` is
re-emitted with `generation + 1`"; code: `MatchSession.publish()` → `instanceRecord.reemit(dirty)`
(`MatchSession.java:338`, `InstanceRecord.java:57-65`) returns a `Formed` from any stage's attempt.
§4 lists this under doc edits ("需要定'重发射是独立动作'"), but L §3.12 shows option (a) adds a seventh
`MatchResult` arm where R27 records six. Options: (a) a seventh arm produced by the scheduler's liveness
path (needs R27 amended); (b) `Formed` stays settle-only and the re-emission is expressed as
`Admitted`/`Deferred` carrying the previous `ClaimSet`; (c) delete `reemit` and let the next `settle`
produce the `Formed`.

### MQ-9 — Does `contentDigest` have to be a content digest? (C-10)

`22` §5.1 "a section snapshot holds a frozen container copy and a `contentDigest`"; `20` §6.3 "the
digest hashes the sorted `(SectionKey, contentDigest)` pairs the unit read"; P6 "a section whose frozen
content differs is a different digest". Code: `SectionSnapshot.java:140-148` hashes only
`entry.getKey().packed()` and `descriptor().type().hashCode()` — traits, properties and footprint are
invisible, and an inventory change leaves the digest equal. Options: (a) widen the digest to the
descriptor, traits and footprint (cost: the digest becomes a real walk, and `20` §8.6's "same cost
class" claim needs checking); (b) keep it as a type-and-cell digest and withdraw P6's no-false-hit
claim. `F5`-style code lag with no R record.

### MQ-10 — Is the blueprint's overlap declaration part of the `Blueprint` value? (F9)

`09:112` "`public List<AnchorSpec> anchors(); public List<Overlap> overlaps();`" and `09` §8 Q5-2
("**Resolved**: `Blueprint.overlaps()` has a glossary row; the declaration lives on the blueprint");
`ClaimMatrix`/`internalOverlaps` cannot see it, and `BlueprintBuilder.java:23-24` says so in javadoc
("Overlap declarations are checked at `build()` but not retained"). Options: (a) retain the accepted
pairs on `Blueprint` and expose `overlaps()` (a shape change to the most-copied value type, and the only
way decided item 2 can distinguish licensed from unlicensed overlap); (b) drop the licence and make
every intersecting bay pair a DESIGN error (contradicts `09` §4.2's table). This became load-bearing the
moment item 2 was called decided, and it is in no R.

### MQ-11 — `ChangeSet`: one type or two? (C §4 item 3)

`01:206` "`ChangeSet` | `(Set<LatticeKey> changed, Set<ChunkKey> chunks, Set<NamespacedId> changedTypes,
long revision)`"; code `occupant/ChangeSet.java` is `(cells, sections, payloads, entities, keys,
revision)` and is produced by the view, while `01`'s shape is the scheduler's invalidation input
(`11` §5, `20` §7). Same name, two shapes, two consumers — the same failure mode as MQ-5. Options:
(a) one record covering both (superset, six components); (b) two names (`ChangeSet` for the view,
`Invalidation`/`Dirty` for the scheduler) with a stated conversion; (c) delete the scheduler half with
the attempt cache (see MQ-5(c)).

### MQ-12 — Is a DESIGN-time violation an exception or a labelled finding? (R12's middle bullet, still unowned per code)

R12 states the question and recommends "DESIGN 期一律异常（`IAE`/`ISE`），SETTLE 期一律诊断". The evidence
for a single answer is not complete: `13:225` forbids members from throwing except `of`/`build`, `13:263`
requires an `SC-FOC` finding's primary to be a `KEY`/cell locus, `13:366`'s `P ✓` column presumes a
finding, and `Bay.java`'s six sites are IAE text (L2, confirmed). If R12's recommendation is taken, then
`13`'s `P ✓` column must read `–` for every DESIGN-only code (`SC-CHK-0106`, `SC-BAY-0107`'s kind half,
`SC-BPT-*`, `SC-DOC-*`, `SC-OCC-01xx`) — that column rewrite is a separate edit R12 does not name, and
it collides with MQ-1's (B) (which keeps those codes as the *decision record* mandates them).

## 4. Doc edits that need no decision

Not in §4's file lists unless marked; all verified in the tree.

- `06` §4.3 row 1 + `19` R8 ("a covered cell is `READABLE` even if its own read was denied") vs
  `WorldView.java:14-16` ("`null` when the cell cannot be read — outside the view **or behind a refused
  slot**") and `examples/TestWorld.java:83-85` (slot resolved before the footprint). Restate at section
  grain and make the view obey it. (C-11; §8.5-listed, absent from §4.)
- `20` §4.1's `occupants(box)` filter has no readability test (`SectionSnapshot.java:121`
  `occupant.footprint().intersects(box) && filter.matches(occupant)`), so an occupant whose anchor sits
  in a refused section is still returned — the laundering `06` §4.1 forbids. (C-6; absent from §4.)
- `SectionSlot.Refused(status, NamespacedId code)` (`SectionSlot.java:27`) vs `20` §4.1 /
  `06` §3.4 / `13`'s grammar (`FindingCode`): change the code side; `OccupantReadModelTest.java:64`
  already hands a `Refused` the id `sc-occ-0102`, which no table publishes. (C-8; absent from §4.)
- `OccupantKind.OTHER` does not exist (`OccupantKind.java:10-16`, four constants), `OccupantRef` is not
  `Comparable`, and the canonical order is implemented twice and differently
  (`SectionSnapshot.java:83-88` has a kind term, `examples/TestWorld.java:164-165` does not). Fix
  `06` §3.1/§7.6 or the code; `01:69` is right. (C-9; absent from §4.)
- `12:230` `Codes.CAVITY_TOO_SMALL.finding(...)`: no `finding(...)` member exists on `FindingCode` and
  `CAVITY_TOO_SMALL` is not a `13` row; rewrite the sketch once MQ-7 lands.
- `18` C-13/C-14/C-17 must be deleted or rewritten with decided item 4 (the `ANCHOR`/`CENSUS` carriers
  and `Fingerprint.of(WorldView, Blueprint)` are the prescreen).
- `22` §16.1 item 8 must be struck once item 1's direction is recorded as a ruling (otherwise the doc
  keeps the open branch that item 1 claims to close).
- `11` §8's "carried unchanged … `PrescreenIndex`/`Census`" sentence must be rewritten with item 4, or
  it reads as "keep the names".
- `19` R27's "six arms" (`11:4` repeats it) must be reconciled with whichever MQ-8 answer is taken.
- `01:107`/`01:116` (`DecisionKind` and `Coverage` both spelled `EVERY_CELL`/`ONE_SUBJECT`/
  `ANY_SUBJECT`) are *correct* under RULE-D2(a); only `07` §3/§4.6 and the `block` row change — do not
  "merge" them in `01` while item 3 is unresolved.

## 5. What I could not check

- I did not run the build, so "the tree compiles" is inferred from the cross-package `Reply.combine` call
  (`MatchSession.java:291` into an interface member) rather than from a compile.
- `docs/spec/zh/*` was only spot-checked (`zh/12` still says `SC-CTX-0109` "DESIGN 时报"); translation
  drift is J's item, not re-audited here.
- The two baseline commits (`0ee39b9`, `d1bd920`) were not diffed; all anchors are the working copy.
