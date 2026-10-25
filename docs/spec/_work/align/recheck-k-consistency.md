# K — consistency across the nine slice reports (re-check)

## 1. Method

- Read end to end: `docs/spec/_work/align/a-base.md`, `b-space.md`, `c-occupant.md`, `d-predicate.md`,
  `e-claim.md`, `f-blueprint.md`, `g-engine.md`, `h-context-findings.md`, `i-spi-adapter-tests.md`;
  then `docs/design-notes/alignment-report.md`.
- Read-only; this file is the only write. No code or spec file was opened, with one exception: a grep of
  `^### D-` over the consolidated report, to settle the id question in §5.1. Its result is quoted there.
- Every row below names the report and row id, and quotes the line it rests on. Where two reports'
  verdicts disagree, the row says which one the quoted anchors support — the anchors are the reports'
  own, so a claim is only as good as the slice that filed it.
- This stage does not re-open the nine slices' code/spec findings; it only checks the review.

## 2. Same root, different verdicts

**K2.1 — `base`'s upward imports and the home of `ReadScope` / `Outcome` / `Locus` / the finding model.**
Verdicts filed: `DOC` (A4 "Spec right, code lagging, no R-record covers it"; D8 "the move the spec already
asks for is what repairs the dependency table"; H3 "the code's tree is a packaging defect, not an intended
`base` role"), `BOTH` (B-6 "the rule that replaces both: `base` is the *value-record floor* and may
reference `space`/`occupant`/`context` value types"), `RULE` (A1, A5, H-R1).
The quoted evidence decides only the *existence* half: A1 "`base` is a *middle* node: it imports `space`,
`occupant` and `context`, all three of which import `base` back (`space/SectionKey.java:3` …) → three
package-level cycles", and B-6 "the frames live in `kernel.base`, so `base → space` (Locus, Finding,
ReadScope) and `space → base` (SectionKey) hold simultaneously — a cycle". Once the cycle is admitted, the
verdicts split on *what may be changed*: B-6 wants the spec re-written to bless the code (`base` = value
floor), A4/D8/H3 want the code re-arranged to match the spec, A1 wants the components or the home changed
("either the components, or the home, must change"). A5 compounds it in the opposite direction from
H-R1(A): A5 recommends "Both to `01` §B (`base`) … **Recommended.**" for `OwnerId`/`InstanceId`, H-R1(A)
recommends "`base` holds only `NamespacedId`, `Stage`, `SourceSpan`, `Stability`, budgets, spend,
cancellation and `DataView`". **Needs the lead to merge**: placement of types (a doc edit) and the
dependency question `base` answers (a rule) are two decisions, and A5 sits on the far side of the second.

**K2.2 — `Stage` on `Check` / `Computation` / `DerivationSpec`.**
`DOC`: D4 "`19` R27 + `23` §7/§9.6 delete it", H10 "a real lag, but no R-record names
`Computation.stage()` … deleting `stage()` is not yet writable". `RULE`: F2 "both sides cannot hold at
once: either units carry a stage (the code) or `expand(S)`/B-11 have no input". `CODE` (spec internally
wrong): F14 "the spec's §3 record, §3 builder signature and its own §5 sketches disagree three ways …
the fifth slot is F2's open question". The anchors support F2's RULE: `Blueprint.java:95` `if
(derivation.stage() == stage)`, `:105` `if (computation.stage() != stage)`, `:121` `if (check.stage() ==
stage)` (F2, F14), plus H10's own qualifier that the spec "assigns the computation no other stage source".
D4's `DOC` reading of the same fact cannot drive the edit while F2/H10 stand. **Needs merge.**

**K2.3 — `ExpectationMeta.requires`: `Set<TraitKey<?>>` vs `Set<ContextKey<?>>`.**
`CODE`: D10 "the code + `22` win and `07` must change", whose own evidence says "`TraitKey` does not exist
in the kernel at all (the occupant model carries `Set<NamespacedId> traits`, `OccupantSnapshot.java:16`)".
`DOC`: C-5 "R8 and `06` §4.4/§5/§6 make the value the point … no R-record covers the id-only
substitution", with C-4 "there is **no property channel at all**". The quoted evidence supports C on the
channel and D on the one record's component — the two are not the same finding, and C-15 states the
reconciliation the lead owes: "either the trait demand is dropped or `ExpectationMeta` gains a
`Set<TraitKey<?>>` back (C-5)". **Needs merge.**

**K2.4 — which family owns R3-kind / R4-completeness, and the unlisted `Bay.java:58` case.**
`RULE`: E's D6/RULE-4 "`13`'s own family table says `BAY` = 'bay coverage' *and* `CHK` = 'coverage match',
so the spec cannot say which family owns an R3-kind / R4-completeness violation". `DOC`: F8 "R30 assigns
`SC-BAY-0107` to R3 alone and `SC-CHK-0106` to R4/B-10 (`09` §9 F-7 asserts exactly this); the
outside-cells condition additionally has **no row at all**". F's anchor (`19` R30 "`SC-BAY-0107` is R3
only") is a registered decision, so the coverage half is lag, not a rule; E's RULE-4 bundles it with the
genuinely rowless case and reassigns R3-kind to `CHK-0106`, which `13:342`'s kind clause (E's own doc
anchor) forbids. **Needs split**: one doc-lag row + one new-code/registry decision.

**K2.5 — does `check` write?**
`RULE`: G9 "the spec explicitly leaves it open (§16.1 item 8) … `11` §1 'It writes nothing except through
`settle`' is contradicted by the code the spec itself records". `BOTH`: G10 "the two spec claims cannot
both hold: a re-emission is not a settlement, and `check` is not a writer". Same two lines,
`MatchSession.java:325` `store.publish(draft)` and `:338` `instanceRecord.reemit(dirty)`. I adds the
missing evidence that nothing pins the claim ("no test asserts '`check` writes nothing'"). The pinned code
decides the fact; the lead still owes the policy (G9's options vs G10's new arm).

**K2.6 — `OccupantRef`'s capture identity.**
`DOC` + open decision: C-1 "the substitution is covered by no R-record, so the fix (add `snapshotId()`
back to `WorldView` …) is itself an open decision". Registered lag, no decision: consolidated D-6 "这不是
文档缺陷，是**代码滞后于 R8**" and §6 "R8：`OccupantRef` 带 `SnapshotId`（D-6）". Blocking: E §4.2 "The
check is writable only after R8; until then `SC-CLM-0005` is unimplementable". C-1's own anchors ("no view
carries a capture id, so nothing can tell two captures apart"; `22` §5.1 "dropped the member that `06`
§3.3/§20 §4.1/`01` §76 keep") put the missing accessor in the doc, not in the registered-lag list.
**Needs merge.**

**K2.7 — `Locus`'s span rule.**
`BOTH`: H13 "13 contradicts itself (the iff vs 'refined highlight'), the code implements a third rule".
`CODE` (for `cells`): A12 "'Optional' must be reworded … or a regenerated `Locus` is built with a null
`cells`". `RULE` (frame matrix): B-2 "the spec never states the full frame matrix (what
`DEFINITION`/`KEY` answer from `toWorld`, whether a `source` survives a `LOCAL→WORLD` round trip)". Three
verdict columns on one type; the file-level edits in the consolidated (§4 `01-glossary` and `13-findings`
with no decision) do not add up to one contract.

## 3. Conflicting recommendations

**K3.1 — D-11 (`D-11 只留一个枚举`) deletes what slice D proved load-bearing.** D's RULE-D2 rejects
exactly this option: "(b) would delete the only check that catches 'a `PRESENCE` bay holding a
cell-deciding focus'". D's alignment row names the site: "`decisionKind()`/`staticDecisionCells()` are used
exactly where the spec says: at DESIGN in `blueprint/Bay.validate` (`Bay.java:52-67` … including
`SC-BAY-0107` when a `CUBIC` focus decides outside its bay)". Consolidated D-11's recommendation is
"(a) **只留一个枚举**" — D's rejected (b). F7's doc anchor quotes the same object as the legality gate
("`blueprint/Coverage.java:11-19` … The legal coverage × scope rows; any other pair is `SC-CHK-0106`").

**K3.2 — D-10 (a) (restrict `Placement`'s domain) contradicts B-3's verdict.** B-3 (DOC): "the code's
`@Nullable` is forced by `05` §1's own 'leaving the lattice is absence'; §3 must annotate both members and
§4.6 must qualify each law 'wherever the terms are non-null'", resting on the probe
(`new Placement(Orientation.IDENTITY, LatticePoint.of(LatticePoint.MIN_XZ,0,0)).inverse()` is `null`).
B-8 (DOC) confirms the spec sentence governs the same edge: "the spec must state the coverage range and
either keep the throwing factory (documented) or make it `@Nullable`". D-10(a) instead changes the code
("只有把原点留在可表示范围内的摆放才可构造"), which B-3 refused to do and which requires rewriting B-8's
cleared reading ("the range is a **guard, not a limit**").

**K3.3 — D-1(b) keeps `Locus` (and `Finding`) in `base`, so the cycles D-1 exists to remove survive.**
H-R1(A) moves "`Locus`/`LocusFrame`/`KeyLocus` … to `kernel.space`"; A4 concludes the same; consolidated
D-1(b) keeps them ("诊断的*值*（… `Locus`）留在 `base`"). H3's anchor shows a second `base → space` edge
that stays regardless: "`base/Finding.java:3-5` imports `kernel.space.Cuboid/CuboidSet/LatticePoint`"
(corroborated by B-6's identical list), while "`space/SectionKey.java:3` imports `base.NamespacedId`".
Consequence for A4's own text — "The whole locus family is in `base` and is **the single edge** that makes
`base` → `space` … moving the three types to `space` removes `base`→`space`" — is contradicted by those
two anchors: the recommended move does not remove the edge unless A2's `Finding` move happens with it, a
dependency A4 does not state.

**K3.4 — `Outcome`'s destination has three answers.** A1(b) "move `Outcome` to `predicate`, whose row
already allows `occupant` and `finding`"; D8 "both types belong to U3"; H-R1(A) "`Outcome` moves to
`kernel.engine` or `kernel.finding`"; D-1(b) "`Outcome`/`ReadScope` 移到 `finding`". D-1(a) compounds it by
deleting `kernel.finding` from the unit table ("把 `finding` 从单元表删掉"), which makes D-1(b) unavailable
if (a) is chosen.

**K3.5 — D-4(b) invents a new code, E's own recommendation forbids it.** E's RULE-1: "(A) — it is the only
option that needs no new code, matches what the code already does, and keeps `DESIGN` free of a claim
row"; E's D1 rule keeps two predicates ("Rule: two predicates — `conflicts(a, b)` … ; `overlapsWithin(rows)`").
D-4(b): "在结算阶段报一条新码（例如 `SC-CLM-0008`）… 把它并回矩阵之外的显式检查". E's D4 tally ("the
tree can emit exactly **`SC-CLM-0001` and `SC-CLM-0002`**; 0003/0004/0005/0006/0007/0104 have no
producer") says the unit cannot absorb another code, and the registry decision that would authorise one is
the dropped D-13 (§5.2).

**K3.6 — D-2's ripple removes `Stage` from `DerivationSpec`; F3's rule needs it.** F3's recommended rule
is quoted in the consolidated's own action list: "`DerivationSpec(deriver, Arguments args, NamespacedId
output, Stage stage, int order)` with `(stage, order)` unique", resting on "`BlueprintBuilder.java:107`
`"B-6: derivation order " + derivation.order() + " is used twice in " + derivation.stage()`". D-2's ripple
is "`Check`/`Computation`/`DerivationSpec` 去掉 `Stage` 分量" — the `(stage, order)` domain disappears with
the component, and D-2 does not say where the stage comes from.

**K3.7 — D-9(a) ("规定 `DEFINITION` 的格子来源") vs H-R5(A) and A12.** H-R5(A): "Keep the iff … delete
`Locus.local(cells, source)` and `Locus.world(cells, source)`, and make `withSource` refuse every frame but
`DEFINITION`". B-2's ruling (A) is written over a matrix where "`toWorld`/`toLocal` … **preserve
`source`**", and `13` §4.4 (H13's doc anchor) says "`Locus.source` is the *refined* highlight" — i.e. a
non-`DEFINITION` span is meaningful. A12 (CODE) adds "`cells` is mandatory and non-null: the empty set is
the encoding for 'no cells'" (`Locus.java:14`). D-9 decides only presence of `LocusMap` and leaves the
`DEFINITION`-cells question open, while B's R-B1(A) decides it by dropping L-1's "carries the authored
cells".

**K3.8 — D-5(a) picks a status mechanism C-13 did not recommend.** C-13's recommendation: "(b) — it
restores `06` §4.1's contract at zero capture cost", i.e. "add `default ReadStatus readStatus(LatticePoint)`
to `WorldView` … plus a `FindingCode refusedCode(SectionKey)` accessor so the refusal's own code reaches
the finding". D-5(a) supplies only "`cell→SectionKey` 的换算（`view.sectionOf(cell)`）" (C-13's option (c)
shape) and drops the `refusedCode` accessor, so `13`'s refusal diagnostics — the reason C-13 is a RULE —
still have no carrier.

**K3.9 — D-8 takes the spec's `derive` while I's ruling recommended the middle.** I's R2: "(c)
**Middle.** … the facade must not expose a mutable store". D-8: "`derive` 取文档形态（store 从 `Formed`
指向的实例取）". D-8 also leaves out I-10's defect on the same member ("`publishTier` retries the stale
base **twice** and then `throw new IllegalStateException`") and I's `catalogs`-field fact ("no member reads
the `catalogs` field"), so a `derive` regenerated from D-8 has no stated failure mode.

**K3.10 — D-6's "delete `footprint`" drops the invariant C attached to it.** C §4 item 4 gives two
branches: "Either the component goes or the invariant is stated and checked" — the code "never
`footprint.equals(ref.footprint())`, so one snapshot can carry two different footprints while `occupants`
dedups on the ref's". D-6 takes the first branch and silently drops the check. C-11's DOC rule ("a refused
slot does not shadow a cell an admitted occupant's footprint covers") also has no home in D-5 or D-6.

## 4. Contradicted clearances

**K4.1** — I §5 clears: "The verb rule `SC-CTX-0109` (`12:94`, `13:366`) is enforced on both directions
(`ContextStoreTest:113-123`)." H7 files the same code as `BOTH`: "the same code is raised twice, as an
`IllegalStateException` (whose message carries the code text) and as a label-less `Failed` finding", with
H-R3 asking for one producer.

**K4.2** — I §5 clears: "`22` §5.4's window→instances `DirtyIndex` is exercised through
`EntityPenScenarioTest`'s `markEntity`/`cellStamp`." C §4 item 3: "nothing under `src/kernel` consumes
`ChangeSet` at all — the dirty path uses `DirtyIndex`'s own marks — so `22` §5.4's '`DirtyIndex` keeps
`window → Set<InstanceRef>`' is also unbuilt", corroborated by G §4 ("`InstanceRef`, `ReadSet` … none
exists in `src/kernel/java`").

**K4.3** — C §5 clears: "`SectionKey` uses `Math.floorDiv` and `compareTo` orders `(dimension, x, z, y)`
(`SectionKey.java:9-40`, `:34-40`) ↔ `20` §6.1's `floorDiv` rule and §6.2's 'sections sorted `(x, z, y)`'."
B-4 (DOC): "`05` never names `SectionKey`, `ChunkGrid` or `ChunkKey` — the only mention is §4.1's
parenthetical 'the fold uses `SectionKey`, not this'", and H §4 item 10: "`12` §4.3's '(x, z, y)' is a
description of `SectionKey.compareTo`, whose real key is `(dimension, x, z, y)` … a regeneration must say
'sorted by `SectionKey.compareTo`', not 'by `(x, z, y)`'."

**K4.4** — G §5 clears: "`DropCause` … `MatchSession` maps them to `SC-ENG-0003`/`SC-ENG-0006` exactly as
`11` §4.6 / `20` §5.4 state." E D4 shows the same number has a second meaning in the tree:
"`MatchSettler.java:37-38` `LEDGER_MOVED = (ENG,3)`, `SETTLE_UNAVAILABLE = (ENG,4)`", and I §5 lists
"`SC-ENG-0003`-for-`LEDGER_MOVED`" among the registered lags. Neither report notices that one code carries
both events.

**K4.5** — F §5 clears: "**Answers `g-engine.md` §4's open question**: `CheckPlan.computations()` *is*
Kahn-ordered … so `MatchSession`'s declaration-order walk is consistent with the plan." G §4's requirement
is narrower than "a topological order": "`11` §4.1 step 3 / `22` §7.1 require Kahn order over `inputs()`",
and F15's own anchor shows the tie-break differs: "`expand` sorts stage derivations by `(order, deriver)`
before assigning declaration indices, so the tie key is `order`, not declaration order". The clearance
states consistency; the two anchors it rests on state something else the spec does not ask for.

**K4.6** — E §5 clears: "**One conflict predicate in the whole tree**: `grep` finds `ClaimMatrix`
referenced only from `InMemoryClaimLedger`; the adapter has no predicate of its own (`08` §4.2's invariant
holds …)." E's own D1 anchor is "a **second, unstated** predicate: same cell ∧ same `(owner, siting)` ∧
different bay ∧ one `EXCLUSIVE`" (`ClaimMatrix.java:60`, `:85-92`), and the consolidated's D-4 depends on
it ("代码另造了第二个谓词（`ClaimMatrix.java:85-92`）"). F7's evidence on the same object ("`ClaimPlanner.java:96-118`
treats `Audit` as 'owns nothing', i.e. `Footprint.None` … which is what makes F7's hole reachable") is the
consequence the clearance hides.

**K4.7** — B §5 clears: "`space` imports only `base` (`SectionKey.java:3`), as §8 says." A1's evidence is
that the pair is cyclic in both directions ("three package-level cycles" with "`space/SectionKey.java:3` —
the only `space`→`base` edge"), and B's own B-6 says so ("`base → space` … and `space → base` … hold
simultaneously — a cycle"). The clearance is true only of `space`'s out-edges and reads as a clean bill
for the pair.

## 5. Duplicates and merge errors

**K5.1 — the id space is not unique; a decision is referenced that does not exist.**
`d-predicate.md` and `e-claim.md` both number their tables `D1…D15`, and the consolidated numbers its
decisions `D-1…D-12`. Consolidated §4 (`08-claims`) says "§4.8/§7 的 `SC-CLM` 表（E D4/E D6）：与 `13`
合一，见 **D-13** 之后的一节" — a grep of `^### D-` over the consolidated returns exactly twelve headings
(`D-1` … `D-12`), so `D-13` does not exist and the sentence points at a decision that was dropped. The
same ambiguity affects the unqualified "D15 相关" in that bullet (E's D15 = `ClaimSet`'s constructor, D's
D15 = `Focuses.entity`'s missing reads) and any bare "D4"/"D6" outside the `E D4` form.

**K5.2 — the decision behind D-13 (the `SC-*` registry and its producers) is in no list.** E D4, E
D6/RULE-4, F4, F8, C-12, I-11, I-14 and D13 each file a variant of "one condition, one code, one
producer" (E D4: "the tally is nowhere: 6 of 9 codes in the table are unimplemented and two
spec-reassigned codes are still emitted under their old family"; F4: "three disjoint allocations"; C-12:
"the rule should be one table"; I-14: "the same file's registry assigns `0401` to 'permission denied' and
`0307` to 'blast radius', and the gate has no code at all"). Forwarding them to a nonexistent D-13 leaves
the registry/producer question undecided, and D-4/D-7/D-11 all depend on it.

**K5.3 — the same root is counted as three hits while eight reports file it.** D-1's evidence line is
"三个分片独立命中（A1/A4/A5、B-6、H-R1/H3）". The same root also carries D8 (DOC, omitted), E §4.3 ("no
`kernel.expectation` (`predicate`), no `kernel.document`, no `kernel.finding` …"), F §4 ("A regenerated
tree must pick one: create `kernel.expectation`/`kernel.finding`/`kernel.remedy` … or rewrite `00` §5"),
G §4, I's unit table ("Either the spec's table (and `03` §3, `18` §2, `02` §2) is corrected to
`predicate`/`base` … or every code package is renamed") and I-05 (CODE: "`15` §1/§3 are the outlier").
D-1's own option set cannot be answered without D8's and I-05's verdicts.

**K5.4 — rows dropped from the consolidated's action and gap lists.** Each has a slice row id and a
verdict, and appears in no §4 bullet and no §5 gap line:
A8 (BOTH: "`Checks.checkNotNull` … has no carrier inside `kernel.**`", with "`build.gradle:117` kernel
classpath = `kernelCompileOnly 'org.jspecify:jspecify:1.0.1'` (nothing else)"); A3 (RULE: "does `claim`
gain the `finding` edge?" — "**yes** — … the minimum change that makes A2's move possible"); C-6 (DOC:
"the laundering `06` §4.1 forbids"), C-8 (CODE: "a refusal carries a bare `NamespacedId`"), C-9 (DOC: "the
order is implemented **twice, differently**"), C-10 (DOC: "an inventory-only change leaves the digest
equal, so a `closureDigest`-validated cached attempt still matches — the P6 false hit"), C-11 (DOC per R8:
"a refused slot wins over an admitted footprint"), C §4 items 3–5 (`ChangeSet`'s two shapes; the unchecked
footprint invariant; `OccupantSnapshot`'s redundant `kind`); E RULE-2 (self-conflict vs revision check),
E RULE-3 (planner vs ledger), E D8 (`ClaimConflict.rows`' meaning), E D12 (`RESERVED`: "a value the spec
never had, produced by no kernel path"); F9 (DOC: "the code's stated reason … is false against `09`
§3/Q5-2"), F10 (BOTH: duplicate producers under `SC-CTX-0102`), F §4's two-sites row ("the same row now
throws at two different sites in two different phases") and its `09` §8 list ("`09` §8's 'Assumed, to be
confirmed' list is still the load-bearing contract for regeneration and none of it is decided"); G15
("`maxDiscards`→`DIRTY_UNSTABLE` has no counter behind it"), G §4's `Formed.readSet()`/`stepReads()`,
`InstanceRecord`'s ownership rule, `20` §6.4's "four counters" leftover, G §5's `AnchorSpec`/`AnchorResolver`
mismatch; H14 ("`12` §3/§4.2 must say `at` never widens"), H15 ("`putKey` is the synonym 03 §1 bans"),
H §4 items 7–11; A11 (BOTH: "No `Codes` type and no code constant exists") and I-03 ("no `*Factory`,
`RemedyProvider`, `ExplainBook`, `Codes` or `Stability` type exists in `src/`") / I-4(b)5 ("`Stability`,
`Codes`, `ExplainBook` and the code-book bijection … have no code and no test"),
I-4(b)1 (`FindingBundle.none()`: "demands a test for a factory that does not exist"), I-4(b)2
(`ReadSet` vs `ReadScope`: "two glossary rows for one concept"), I-4(b)4 (the `BOUNDS`/`CENSUS`/`ANCHOR`
step vocabulary: "no unit declares a step type"), I-4(b)6 ("`specCheck` gate does not exist in
`build.gradle`"), I-4(b)7 (`Gate`: "cannot express five of the six refusal conditions §4.7 demands of it"),
I-07 (one event, two refusal kinds/phases), I's §4(d) evidence gaps (three tests that "assert a mechanism
**they implement themselves**"; `MatchSession.publish()` emitting two codes for one event).

**K5.5 — rows merged incorrectly (the consolidated's paraphrase changes the finding).**
- `01-glossary`: "§D `OwnerId`、§I `InstanceId` 的位置与使用者不相邻（A5）；按 D-1 移到同一节". A5 is not a
  layout nit; its finding is "Neither `01` home is reachable: `03` §3's `claim` row lacks `occupant`
  although `ClaimRow` carries `OwnerId`, and `Locus ∈ space` (A4) forces `InstanceId ≤ space`", verdict
  RULE, recommendation "Both to `01` §B (`base`) … **Recommended.**" Re-pointing it at D-1 (whose (b) keeps
  the finding values in `base` but moves `Outcome`/`ReadScope` to `finding`) loses both the RULE and the
  recommendation.
- `08-claims`: "§4.8/§7 的 `SC-CLM` 表（E D4/E D6）：与 `13` 合一". E D6 is RULE-4 (family ownership) and
  E D4 is the producer tally; merging them into "与 `13` 合一" drops E's finding that the code must
  *widen* (E's alignment: "R3's footprint half is implemented and carries the right code") and E's RULE-1
  (code + stage).
- Appendix: "A6 说 `04` 的 `Budget.of(4_096, 64)` 不存在" — A6's row says "two more (`Budget`, `Spend`)
  exist in `engine` with base-shaped components" and its §4 item asks "whether these are in scope now";
  the claim the appendix corrects is not A6's.
- Appendix: "A11 说 `UNREADABLE_CELL` 没有类型" — A11's finding is "No `Codes` type and no code constant
  exists; `01` §K/§M have no row for it, so `04` §9 names a type the glossary does not define", and A11
  itself cites "`15` §32 declares the name". Both over-corrections weaken A6/A11 on claims they did not
  make, while their real items (`Budget`/`Spend`'s home; the missing `Codes` row) are absent from the §4
  and §5 lists.
- D-5's option (a) and D-6 both carry "补上 `snapshotId()`" / "`WorldView` 必须提供 `snapshotId()`" — one
  edit counted in two decisions; D-6's citation ("`06` §3.3") also drifts from C-1's ("`06` §3.3/§20
  §4.1/`01` §76" with the row itself in `06` §3.1).

**K5.6 — findings on one object that no bullet links.** C-12 (`SC-OCC-*`) is kept, E D4/I §5
(`SC-ENG-0003`'s double meaning) is filed as registered lag, F4 (`SC-DOC`) and I-14 (`SC-MC`) are carried
as separate file bullets — four instances of the same root (K5.2) with four different dispositions.

## 6. Rulings to re-scope

`Rulings that must be merged before the user decides` — which of the twelve bundle independent questions,
or are scoped wrongly:

- **D-1 — six questions.** (i) does `kernel.finding` exist, and does the U9 value/emission split stay in
  `base` (A2, H2) or split out (A2's "no R-record or `23`/`24` §9 migration item covers the move");
  (ii) `Locus`/`LocusFrame`/`KeyLocus` home (A4/B-6/H-R1(A) vs D-1(b) — §3.3); (iii) `Outcome`'s home
  (four answers, §3.4); (iv) `ReadScope`'s components — A1's premise is "either the components, or the
  home, must change", which D-1 never states; (v) does `claim` gain the `finding` edge (A3, dropped);
  (vi) `OwnerId`/`InstanceId` homes (A5, mis-paraphrased per K5.5). Split into at least (i)+(ii)+(iv).
- **D-2 — three questions.** (i) units vs catalog as the stage source (F2/H-R6); (ii) the document
  grammar's `stage` field and un-retiring `SC-DOC-0604` (F2's (b)); (iii) `DerivationSpec.order`'s
  `(stage, order)` domain (F3 — §3.6). (`MatchRequest.stage` is R27-registered, G7.) Add (iv) which stage
  an out-of-match finding carries (A7: "nothing in `03`/`04` says which stage a finding raised outside a
  match carries, while `Finding.stage` is mandatory").
- **D-3 — one question, plus one doc edit.** H-R2's second half ("`12` §5's `IronSection` sketch calls
  `ctx.cells()` … a member neither `ComputationContext` nor `AggregateContext` declares") is separate.
- **D-4 — four questions.** (i) legality of intra-blueprint overlap; (ii) code and stage (E RULE-1 vs
  D-4(b) — §3.5) — blocked on the missing registry decision (K5.2); (iii) one predicate or two (E D1 keeps
  two; D-4(b) says "删掉第二个谓词"); (iv) E RULE-2 (self-conflict before the revision check) is the same
  `settle` transaction and is not in the decision at all.
- **D-5 — five questions.** (i) which model is normative; (ii) the cell→status accessor (C-13 — §3.8);
  (iii) `snapshotId()` (double-counted with D-6, K5.5); (iv) the nested view's grain *and* its name
  (C-14: "two types answer to `NestedView`"); (v) C-6's box-query readability filter and C-11's
  refused-slot-vs-footprint precedence (both DOC, neither in D-5's options).
- **D-6 — three questions, and a re-classification.** (i) `SnapshotId` vs `InstanceId`; (ii) `footprint`'s
  carrier *and* the missing invariant (C §4 item 4 — §3.10); (iii) the air-minting rule; and C-1's verdict
  is DOC-needing-a-decision, not (§6 of the consolidated) pure registered lag (K2.6).
- **D-7 — four questions.** (i) which document's capture SPI wins; (ii) `IssueSink`'s glossary row and
  whether it replaces `FindingCode`; (iii) lease production/ownership, including I-12/G14's
  `LevelSnapshot`→`WorldView` return (a U7 question placed inside D-7); (iv) `SnapshotPolicy`/`CaptureMode`/
  `CaptureLimits` shapes and `requiredTraits`' carrier (C-5/D10 — the same root as K2.3).
- **D-8 — four questions, and the consolidated says so itself** ("四项同源"): `Host` (I-02/R1);
  `derive` (I-09/R2, plus its failure mode, I-10 — §3.9); `ContextKeys.register`'s direction / the new
  `spi → context` edge (I-04); the five missing operations (I-01 — an inventory item, not a shape choice).
  I-08 (`Catalog` interface vs final class, the identical-duplicate no-op) rides on the first and is not in
  D-8.
- **D-9 — two questions.** Presence of `LocusMap`; and what a `DEFINITION` locus carries (B-2's L-1 vs
  `Locus.definition(SourceSpan)`), which is the same type contract as A12 and H13/H-R5 (§3.7). Rule it
  with those two, not separately.
- **D-10 — one question, but not self-contained.** It must be ruled with B-8 (`Cuboid.of(LatticePoint)`'s
  throw, the coverage range `[MIN_XZ, MAX_XZ)`), which the consolidated leaves in the file-level
  `05-space` bullet; D-10(a) as written overrides B-8 (§3.2).
- **D-11 — four questions.** One enum vs two; the value names; `07` §4.6's `block` row (a focus-kind
  question, D6); and whether `Bay.validate`'s comparison survives (the `SC-CHK-0107`/`SC-BAY-0107`
  producers — §3.1). Rule the last one explicitly or the decision deletes a producer.
- **D-12 — one question with two riders that must be worded in it.** Sealed family vs record; the
  MATCH-notes channel; `Verdict`'s deletion; `combine`'s home and semantics (D §4: "no unit claims the
  member, so a regenerator has no home for it"); `Bindings.merge`'s hard-coded `Stage.SETTLE` (D12) is
  carried only as a separate file bullet although `MatchSession` already calls `combine`.
- **Add before the twelve are answered:** the `SC-*` registry/producer decision (K5.2 — D-4, D-7, D-11
  depend on it); `ReadSet` vs `ReadScope` (K5.4 — D-3/D-7 depend on the read-closure type); the
  coarse-step vocabulary vs G12's prescreen (K5.4); `Codes`/`Stability`/`ExplainBook` (K5.4); the
  assertion helper and the kernel classpath (A8); and the `contentDigest` coverage (C-10 — D-5's
  `closureDigest`/cache claims rest on it).
