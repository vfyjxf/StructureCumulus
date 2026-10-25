# J — re-check: coverage (code, spec, and the nine-slice split)

> Role J of the stage-2 re-check. Baseline `0ee39b9`. Read-only except this file.
> Method: every file under `src/kernel/java`, `src/main/java`, `src/kernelTest/java` was matched
> against the `## 1. Scope` section of each of the nine reports (a package-glob claim — "all 24
> files", "every file under X" — counts as coverage); every `docs/spec/*.md` was matched against
> citations anywhere in the nine reports and in their own scope lines; the sections no slice owned
> were then read end to end and checked against the code. Where a report and a file disagree, the
> file wins.

## 1. Code coverage inventory

Files per source set (`find … -type f`): `src/kernel/java` 143 (142 in the nine packages + the
root `kernel/package-info.java`), `src/main/java` 7, `src/kernelTest/java` 32.

| Package | files | slice that claims it (scope wording) |
| --- | --- | --- |
| `kernel.base` | 21 | **A** ("all 20 files"); the finding subset also **H** |
| `kernel.space` | 16 | **B** (all 16 named) |
| `kernel.occupant` | 14 | **C** ("Code slice (all of it)") |
| `kernel.predicate` | 18 | **D** (all 18 named) |
| `kernel.claim` | 11 | **E** (all 11 named) |
| `kernel.blueprint` | 10 | **F** ("Code, all of …") |
| `kernel.engine` | 24 | **G** (all 24 named) |
| `kernel.context` | 24 | **H** (all 24 claimed, 23 named) |
| `kernel.spi` | 4 | **I** (all 4 named) |
| `kernel/package-info.java` | 1 | **none** |
| `src/main/java` | 7 | **I** (all 7 named) + `src/test/.../ChecksTest.java` |
| `src/kernelTest/java` | 32 | **I** ("all 32 files, 162 `@Test` (1 `@Disabled`)") |

Every file of the nine packages, all of `src/main/java` and all 32 test files are inside some
slice. The only file outside every scope is `src/kernel/java/dev/vfyjxf/cumulus/kernel/package-info.java`
(4 lines: `@NullMarked` + the import) — **trivial**.

Two counting slips inside the scopes themselves (not coverage holes, but they are why the file
census does not close):

- **A** says "all 20 files, 979 lines" for a 21-file package. `base/package-info.java` is never in
  A's file list; it is only *cited* once, in A §5 (`a-base.md:183`, "`04` §4's 'every kernel
  package is `@NullMarked`' holds for `base` (`base/package-info.java`)"), so it is read but not
  enumerated.
- **H** says "all 24 files" and then names 23: `context/Retention.java` is missing from the list.
  It is not unexamined — H §5 verifies it ("`Retention/tier`: `ATTEMPT, OUTCOME, CONTEXT` ✓",
  `h-context-findings.md:196`) — the file itself is the trivial 3-constant enum.

Test-file citation density (how often a test file is named *anywhere* in the nine reports, not just
in a scope line — the metric that shows which evidence was actually used):

| never cited by name | cited by name |
| --- | --- |
| `claim/ClaimPlannerTest`, `engine/EngineWorld`, `engine/TestLease`, `context/RuntimeStateTest`, `examples/DoorScenarioTest`, `examples/PhaseScenarioTest`, `examples/PipeCornerScenarioTest`, `examples/PublishScenarioTest` (8 of 32) | the other 24, led by `examples/HatchScenarioTest` (e, g, i), `examples/TestWorld` (c, d), `context/ContextStoreTest` (h, i, r→I), `FurnaceScenarioTest`/`LivenessScenarioTest`/`MatchSessionTest`/`CaptureGateTest`/`EntityPenScenarioTest` (I) |

All eight are inside slice **I**'s scope ("all 32 files"), so no test file is unwritten; the gap is
that the two shared fixtures (`EngineWorld`, `TestLease`) and four of the scenario tests 22 §14's
falsification program names are asserted-only-in-the-scope-line.

## 2. Spec coverage inventory

Nine reports = one per kernel package, so a spec file's owner is whoever owns its package. Citations
are counted anywhere in the report; **bold** marks a slice whose scope says "end to end".

| spec file | owned end to end by | also cited / partially read by | sections no slice read |
| --- | --- | --- | --- |
| `00-overview.md` | — | a (§4–§5), b (§3 R-01/R-05, §4 I-3/I-5/I-14), c, d, e, f (§3–§5), g (§5), h (§4/§5), i (§5) | **§1–§2**, §6–§9; §3 rows R-04/R-08/R-13, §4 rows I-4/I-5/I-10 unchecked |
| `01-glossary.md` | rows only: A (§B/C/D/E/I/K), **D (§E)**, **G (§I)**, **H (§K)** | all nine | §1–§A, §L–§N, §O–§P |
| `02-architecture.md` | — | a (§1/§2/§4/§5/§9), b (§2/§9), f, h (§2), i (§2/§5/§6) | §3, §7, §8, §10 |
| `03-naming.md` | **A** (§1–§7 for `base`) | b, c, d, e, h, i | — |
| `04-api-doctrine.md` | **A** | f (§11/§12), g (grep) | §9–§10 beyond A |
| `05-space.md` | **B** | — | — |
| `06-occupants.md` | **C** | b, d (§1), f, i | — |
| `07-expectations.md` | **D** | f, g (grep), i | — |
| `08-claims.md` | **E** | b, f, g | — |
| `09-blueprints.md` | **F** | d, e | §7.3 rank proof (housekeeping) |
| `10-documents.md` | **F** | a (line 21), i | — |
| `11-engine.md` | **G** | b, d, h (grep), i | — |
| `12-context.md` | **H** | a (line 230), b, e | — |
| `13-findings.md` | **H** | a, b, c, d, e, f, i | not cited by **G** at all (the only code-registry file G never opens) |
| `14-remedies.md` | **none** | a (1 line: `Codes`), c (1 line), i (2 lines: `EditTransaction.begin` signature) | §3 (all 20 types), §4 (the ten dry-run rows), §5, §6, §7, §9 |
| `15-spi.md` | **I** | a (lines 13, 32), e (§3), g (grep) | — |
| `16-adapter.md` | **I** | b, c (§28–72), e, f | §6–§8 beyond I |
| `17-worked-example.md` | — (I reads it, not "end to end") | i | throughout |
| `18-verification.md` | — (I reads it, not "end to end") | c, d (§3), i | §1–§2 |
| `19-open-proposals.md` | **none** | all nine cite R-numbers (a 3, b 3, c 6, d 3, e 7, f 8, g 11, h 2, i 4) | 12 of the 37 R-records are cited by **no** report by name |
| `20-execution-and-sections.md` | **G** ("end to end") | b (line 199), c, i | — |
| `21-authoring.md` | **F** | i (1 line) | — |
| `22-consolidated-design.md` | **none** (cited piecemeal by all nine) | §4.4/§5 (c), §6.1–§6.3 (d), §6.4/§8/§9/§10 (i), §6.4/§7/§8/§11.1/§14 (h), §9/§12/§16.1 (f), §9.x/§10.x/§16.1 (g), §10/§6.5 (e) | **§11.2, §11.3, §12.1, §12.3–§12.5, §13, §14, §15** (and §11.1 by H alone); §16 items 1–7, 9–15 |
| `23-claim-entry.md` | **G** ("end to end") | a (§7), b, d (§7/§9), e, f, h, i | — |
| `24-claim-vocabulary.md` | **E** ("end to end") | d (§4/§7/§9), f (§5/§7/§8/§9), i | — |
| `_index.md` | none (cited by C's scope line only) | — | gates 2/4/5 unverified (see §4) |
| `docs/README.md` | **none** (a pointer file, never read by a slice) | — | all of it |
| `docs/spec/zh/_translation-guide.md` | **none** | — | all of it (the `zh/` mirrors are never sampled either) |
| `docs/guide.zh.md` | **none** (mentioned only by `docs/README.md:4` and `README.zh.md:4`) | — | all 611 lines |

R-record sweep against the code (`19`'s own subject). Cited by at least one report: R1–R15, R19,
R26–R28, R30–R32, R35–R37. **Cited by no report at all:** R16, R17, R18, R20, R21, R22, R23, R24,
R25, R29, R33, R34. Of those, the consolidated report's §6 ("registered lag, not defects") lists
none, and it lists only R27/R37, R35, R36, R32, R8, R2 in total — so the twelve are unowned by both
the nine slices and the consolidation. Checked here one by one (§4, J13–J17): R24, R25, R33, R34
hold verbatim in the tree; R16 and R29 contradict it; R17/R18/R20/R21/R22/R23 are adapter/scheduler
rules whose whole substrate is absent.

## 3. Uncovered files and sections

**Files not covered by any slice.** One, and it is trivial:
`src/kernel/java/dev/vfyjxf/cumulus/kernel/package-info.java` (4 lines, `@NullMarked`). The
"24 files / 23 named" and "20 files / 21 present" slips in H and A (§1 above) do not hide anything
material: `Retention.java` is a 3-constant enum that H verifies in §5.

**Spec surface materially unexamined — four blocks, all of them outside the "one package, one
slice" axis:**

1. **`14-remedies.md` (U10) — nothing.** Three incidental citations exist (A11 cites `14:341` for
   `Codes`; C cites `14` once; I-13 cites `14:171-172`/`276` for `EditTransaction.begin`'s
   five arguments), and the consolidated report's §4 must-fix list has **no `14-remedies` heading**
   (its headings run 00, 01, 02, 05, 06, 07, 08, 09, 10, 11, 12, 13, 15, 16, 17/18, 19,
   21/22/23/24) and no §3 ruling about it. So U10's ~20 types, its ten dry-run rows, its ~20
   `SC-RMD` codes and its seven edits have never been read against the tree by anyone.
2. **`22` §11–§15.** §11.1 (H only), §11.2/§11.3/§12.1/§12.3–§12.5/§13/§14/§15 by nobody. This
   block holds the family list, the `RemedyContext`/`EditExecutor` frame, the compact-profile
   arithmetic, the twelve proofs and the nine-scenario falsification program.
3. **`00` §3–§4** (the requirement and invariant tables) — read as context by A/b/f but never
   audited row by row; §4's gate is `_index.md` gate 4 ("each invariant the unit touches is either
   proved or explicitly deferred"), and no report runs that gate.
4. **The receiver-side docs: `docs/README.md`, `docs/spec/_index.md`,
   `docs/spec/zh/_translation-guide.md`, `docs/guide.zh.md`.** `guide.zh.md` is the file
   `docs/README.md:4` tells a newcomer to read first, and it was cited by **zero** reports.

Judgement per block: (1) and (4) are materially unexamined, not trivial — each yields live
divergences (§4). (2) is materially unexamined. (3) is half-examined: the requirements/invariants
that a unit happens to touch were checked inside that unit, the tables as tables were not, and two
invariants (I-4, I-10) have no owner at all.

## 4. Divergences found in the uncovered surface

Verdict vocabulary as in the nine reports: **DOC** (the document is wrong and must change), **CODE**
(the code is wrong), **BOTH**, **RULE** (undecidable from the current text; a ruling is needed).
Every row quotes the anchor it rests on.

### 4.1 `14-remedies.md` (U10, no owner)

| id | doc anchor | code anchor | what the doc claims | what the tree has | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| J1 | `14` §4.4 row 1–9 and §4.5/§4.7 use `0201`–`0206`, `0301`–`0309`, `0401`–`0404`, `0601`; §8 "Proposed `SC-RMD` codes for U9 to accept or renumber: plan `0201`–`0205` … apply `0401`, `0402`, `0403`, `0404`; gate `0601`. The umbrella code … is `SC-RMD-0001`" | `13:374-375` registers exactly two rows: `SC-RMD-0001` and `SC-RMD-0309` | one registry owns U10's vocabulary | ~20 codes are used by U10's own tables and 2 exist in the registry | DOC | the `SC-DOC` analogue was sliced (F4: "one table must replace all three statements"); `SC-RMD` was not, because no slice read `14` — so a regenerated U10 emits `0303`/`0401`/`0601` with no row anywhere (`13`'s own §398 says "`SC-RMD` everywhere (`22` §11.1, §11.3)") |
| J2 | `14` §4.4 row 5 uses `0206` ("support: no step needs a cell another step clears \| `0206`"); `14` §8's proposal list stops at "plan `0201`–`0205`"; `19:59-60` (second pass) says U14 "support uses its own `0206` rather than `0202`" | — (no code) | the plan-code range is closed at `0205` | `0206` is used in §4.4 and 19 names it, but §8's range excludes it | DOC | the range sentence and the row table disagree inside one file |
| J3 | `14` §3.4 `record SetTrait<T>(LatticeKey cell, TraitKey<T> trait, @Nullable T value)`; §3.2/§4.4 row 4 read a descriptor's properties (`Configure`), §4.6's marks | `grep -rn "Trait\|TraitKey" src` → only `Set<NamespacedId> traits` (`OccupantSnapshot.java:15-16`, `OccupantDescriptor.java:16`) | U10 needs typed trait keys and a property channel | neither exists; `TraitKey` is absent from the whole kernel (D10) and the descriptor has no property channel (C-4) | DOC | U10 is a **third** consumer of both holes (after `07`/`06`) and it is the one nobody counted: `14` cannot be written even if the trait question is settled elsewhere |
| J4 | `14` §8 OQ-1 "`RevisionMoved`: one retry inside the settle transaction. A second movement is either a retry token (`Deferred`) or a hard `Undecided` — and at apply time the same event is `0303` with no retry at all, so the two paths must agree on the word" (= `22` §16 Q4) | `MatchSettler.java:78-81` retries once with `moved.actual()` and no re-read (G8) | one word for one event across settle and apply | the settle half is ruled nowhere and the apply half has no code | RULE | E RULE-2 rules the *order* of self-overlap vs revision and G8 flags the retry's content, but no report connects them to `14` OQ-1 / `22` §16 Q4 — the registry-crossing decision has no owner |

### 4.2 `22` §11–§15

| id | doc anchor | code anchor | what the doc claims | what the tree has | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| J5 | `22:609-610` (§11.1) "Families: `SC-DOC`, `SC-BAY`, `SC-CHK`, `SC-EXP`, `SC-FOC`, `SC-CLM`, `SC-CTX`, `SC-MC`, `SC-RMD`." | `base/FindingCode.java` `Family` carries 15 constants; A §5 verifies them against `03` §7's mnemonic list: `SPC, OCC, EXP, FOC, CLM, BPT, BAY, CHK, DOC, ENG, CTX, DIA, RMD, SPI, MC` | nine families | fifteen, in both `03` §7 and the code | DOC | the consolidated design is the regeneration input of record and it drops six families (`SPC`, `OCC`, `BPT`, `ENG`, `DIA`, `SPI`); H's §4 item 8 counts 15 values but does not compare the two lists |
| J6 | `22:611` (§11.1) "Bundle order: severity, stage, code, locus, insertion; dedup by `(code, primary label)`" | `base/FindingBundle.java:119-137` `compareEntries` = `severity().rank()` → `stage().ordinal()` → `code()` → `locusOrder()` → `insertion()`; `:100-110` dedup by `Key.of(finding)` + `mergeDuplicate` | — | matches | *(aligned)* | recorded because this is the one §11.1 claim that is exact — the order is the code's, so §11.1's defect is its family list, not its ordering |
| J7 | `22:765` (§14) P6 "A section unit cannot read aggregate state \| `SectionContext` has no `read(ContextKey)` (§7.3) \| argument" and §14's scenario 8 "`PhaseScenario` — a `SectionContext` cannot call `read` (compile failure is the test)" | `Computation.compute(ComputationContext)` (`Computation.java:22`) where `ComputationContext extends AggregateContext` (`ComputationContext.java:5`) with `read(ContextKey)` (`AggregateContext.java:11`) — H1's finding; `examples/PhaseScenarioTest.java:22-26` javadoc ("this reflection assertion is the runnable stand-in until a sketch that fails to compile can live in the build") and `:29` `aSectionContextCannotReadAggregateState`, which reflects on `SectionContext` only | the scenario proves P6 | the test cannot see the context a SECTION computation actually receives | DOC | H1 refutes P6 and H-R2 asks for the ruling, but neither H nor `22` notes that the named scenario test asserts a different type — so P6's "argument" row has no test behind it and the row must move to `Falsification`-incomplete or the type must change |

### 4.3 `19` R-records against the code

| id | doc anchor | code anchor | what the doc claims | what the tree has | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| J8 | `19:81` R29 "The worker's terminal value is `MatchCompletion`; `MatchOutcome` is only the attempt product." (files `01`, `20`) | no `MatchCompletion` anywhere under `src/`; `01-glossary.md:314` gives it a row ("the worker's terminal completion value, one per submit"); `13:359` retires `SC-ENG-0005` *into* it ("reported as `MatchCompletion.Discarded`") | one terminal value per submit | none | DOC | an R-record with a glossary row that the code table already cites has no slice, no carrier and is not in the consolidated §6 lag list — the whole worker/scheduler half of `20` is unowned |
| J9 | `19:30` R16 "`StructureKernel.match` stays synchronous and pure; the host `MatchScheduler` owns a bounded worker pool. No `begin`/`advance`, no `TimeSource`, no `nanos`." | `StructureKernel.java:73` `check(MatchRequest)` (the nine-operation table in `15` §4.4 and R31 name it `check`); grep for `TimeSource`/`nanos`/`begin` in `src/kernel/java` → ∅ | the facade entry point is `match` | it is `check`, and every other document says so | DOC | the "no nanos/no TimeSource" half is verified (I-15 below); the name is stale and no report lists R16, so a regenerator reading `19` alone invents `StructureKernel.match` |
| J10 | `19:31` R17 (section-based snapshots, `SectionSlot` `Air`/`Present`/`Refused`), `19:35` R21 (opt-in `SectionStore`), `19:32` R18 (mixins, `changeCount` in the snapshot check), `19:37` R23 (`AUTO` runs `PROBE` only), `19:34`/`19:36` R20/R22 (one in-flight task per `(world, owner)`; the entity window as move witness) | `occupant/SectionSlot.java:15-33` exists (C-2); `SectionStore`, any mixin, `PalettedContainer`, `LevelChunk` → ∅; `CaptureGate.evaluate(lease)` is implemented (G §5); `EntityWindow` exists (C-3) | adapter/scheduler rules | the kernel-side halves hold; the adapter halves are absent with the whole adapter | *(no new defect)* | recorded because these are the R-records no slice cites: `16`/`20` are read end to end by I/G, and "the `mc` package does not exist" (I-12) is the reason, but no report says so per record — a re-checker cannot tell "absent because the adapter is absent" from "unnoticed" |
| J11 | `19:85` R33 "The gap ledger `docs/status.md` is deleted, and neither a document nor a source comment points at it or at a `TODO(GAP-xx)` marker." | `docs/status.md` does not exist; `grep -rn "status\.md\|TODO(GAP" docs src` matches only R33's own line | — | holds verbatim | *(aligned)* | the one uncited R-record that is exactly true today |
| J12 | `19:86` R34 "`MatchPolicy` is `(int maxSectionsPerUnit, CacheMode cache, boolean parallel)` and nothing else" | `MatchPolicy` — G §5 verifies the components, `defaults() == (8, OUTCOMES, false)` | — | holds verbatim | *(aligned)* | R34 is uncited but verified as a side effect of G's engine slice |

### 4.4 `00` §3–§4

| id | doc anchor | code anchor | what the doc claims | what the tree has | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| J13 | `00:62` I-4 "A match reads only an immutable `WorldView` snapshot; `(blueprint, snapshot, siting, policy)` fully determines the result." (enforced by "U2, U7") | `MatchSession.java:325` `Outcome<Void> published = store.publish(draft);` on every stage (G9); `MatchSession.java:338` `instanceRecord.reemit(dirty)` returns a previous `Formed` (G10); at `SETTLE` the ledger is read through `MatchSettler` (G3) | four inputs determine the result | the per-instance `ContextStore`, `InstanceRecord.previous` and the ledger are also inputs | DOC | G9/G10 anchor their findings to `11` §1/§4.6 and `01` §I, never to I-4 — but I-4 is the invariant the *dependency gate's* sibling (`_index.md` gate 4) asks about, and as written it is false; the sentence must gain the store, the record and the ledger, or `check` must stop publishing |
| J14 | `00:68` I-10 "… and only `settle` settles: a `Formed` is a structure that has already been written." | the same `MatchSession.java:338` path returns a `Formed` from `check` before any settle | `Formed` implies a write | `check` returns `Formed` | DOC | duplicated with `01` §I/`22` §10.4 vs `11` §4.6 (G10, BOTH) but I-10 is the invariant-gate row, so the fix has to be written into `00` §4 as well, not only into `11` |
| J15 | `00:40` R-04 "`AnchorSpec` declares a role on cells; `OrientationPolicy` selects a placement from anchor candidates"; `00:63` I-5 "A `Siting` is derived from an `AnchorCandidate` plus the blueprint's `OrientationPolicy`" | `AnchorSpec` exists (`blueprint/AnchorSpec.java`); `OrientationPolicy`, `AnchorCandidate`, `AnchorResolver` → ∅ (F §4 "have no carrier and no test seam"; the name survives only in `OrientationScenarioTest.java:52-53`, a test asserting the rule it cannot call); `SC-ENG-0104` (anchor yields no heading) has no producer (G12) | the siting's derivation path | only `Siting.of` + `Orientation` exist | DOC | F and G12 report the missing types against `09` §4.5 and `11` §4.1/§4.3; neither anchors them to `00` §3 R-04 / §4 I-5, which state the *requirement* — so a reader of `00` alone still believes the derivation exists |
| J16 | `00:44` R-08 "`Stage` (`DESIGN`, `PROBE`, `FORM`, `SETTLE`, selected by the engine)"; "`ReadSet` + `ChangeSet` + `InvalidationIndex`" | `Stage` has 5 constants incl. `STALE` (A7, R27 covers it); `ReadSet`/`InvalidationIndex` → ∅, the live name is `ReadScope` (I §4(b)2: "two glossary rows for one concept") | four stages, one read closure type | five constants; two competing names | *(covered by A7 + I-15)* | listed for completeness: the R-08 half is already reported, by a slice that read `11`/`01`, not by anyone reading `00` |
| J17 | `00:49` R-13 "typed `BindingKey<T>`, `ContextKey<T>`, `TraitKey<T>`, `ParameterKey<T>`; sealed `MatchResult` and sealed `Edit`" | `BindingKey`/`ContextKey` exist; `TraitKey`/`ParameterKey` → ∅ (D10, A6); `MatchResult` is sealed (6 arms, G6); sealed `Edit` is U10's (J1–J3) | four typed keys, two sealed families | two keys; `Edit` has no code | *(covered by A6 + D10 + J1)* | R-13's `Edit` half is only reachable through `14`, i.e. through the unowned block |
| J18 | `00:73` I-15 "The kernel reads no wall clock, no randomness, no I/O and no global state." | `grep -rnE "System\.(currentTimeMillis\|nanoTime)\|Math\.random\|new Random\|randomUUID\|Instant\.now\|ThreadLocal\|System\.out\|Files\.\|new File" src/kernel/java` → ∅ | — | holds verbatim, kernel-wide | *(aligned)* | G §5 verified it only for `MatchSession`; this is the whole source set |
| J19 | `_index.md:65` "Dependency gate — a module imports only packages above it in `02-architecture.md` §2"; `:68` gate 4 "each of `00-overview.md` §4's invariants that the unit touches is either proved or explicitly deferred" | A1/B-6/H-R1: `base` imports `space`/`occupant`/`context` and `SectionKey` imports `base` (three package cycles) | two gates | gate 2 cannot be checked at all against the current graph, and gate 4 has no run (I-4/I-10/J13/J14) | RULE | A/B/H each propose a `base` boundary, but no report notes that `_index.md`'s own gates are unsatisfiable as written, which is why the review could not simply "run the gates" |
| J20 | `_index.md:66` "Shape gate — the nine sections of `03-naming.md` §8 are present" | all twelve module files `05`–`16` carry all nine (checked mechanically: `Scope … Falsification`); `20`/`21` carry a different structure | every module file | holds for every module file; `20`/`21` are "reviewed" in the unit table without an `03` §8 shape | *(aligned, one wording note)* | `03` §8 says "Each module file"; `_index.md`'s unit table also lists `X`/`A2` rows for `20`/`21`, so the gate's scope should say "unit files" explicitly |

### 4.5 The receiver-side docs (never read by any slice)

| id | doc anchor | code anchor | what the doc claims | what the tree has | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| J21 | `guide.zh.md:4` "内容对应当前代码，不写尚未存在的 API" (matches the current code; no API that does not exist), then `:130-131` `SnapshotInfo.of(0L, Ticket.of(1), new SnapshotId(…), 0L, true)`, `:152-153` `kernel.check(new MatchRequest(info, …), () -> info)` and `kernel.settle(request, admitted.planned(), () -> info)`, `:151` `Admitted.planned()`/`Admitted.ledgerRevision()` | no `SnapshotInfo` under `src/` (grep ∅); `StructureKernel.java:73` `check(MatchRequest)` takes one argument and no `settle` member exists (I-01); `MatchResult.Admitted` has no `planned`/`ledgerRevision` (G2) | the runner's code compiles today | it is the pre-R27 facade, byte for byte | DOC | this is a fourth independent statement of the withdrawn facade (`15` §4.4, `11` §4.1, `demo` in `17`), and it is the file a newcomer is told to read first (`docs/README.md:4`) |
| J22 | `guide.zh.md:9` "`src/kernelTest/.../examples/`：31 个编译错误，全部是旧示例与严格签名的差集，**预期为红**" and `:584` `./gradlew kernelTest  # 测试红是预期` | `build/test-results/kernelTest/*.xml` = 162 tests, 1 skipped, **0 failures/errors**; `build/classes/java/kernelTest` exists | the examples are red by design | the corpus compiles and passes | DOC | the status box is dated 2026-10-21 and has been overtaken; the "10 · 示例当前是红的" bullet (`:568-569`) is the same claim and is used there to justify the signatures |
| J23 | `guide.zh.md:559-561` "`Check` 持有 `Bay`，`Bay` 又持有 `Requirement(Check)`…`Bay` 因此不是 record" | `predicate/Check.java:14` holds `NamespacedId bay` (F14), and `blueprint/Bay.java:19` is `public record Bay(…)`, whose javadoc (`:15-17`) says "A check names its bay by id, so the bay and its requirement are an ordinary acyclic record graph" | the knot forces `Bay` to be a class | `Bay` is a record and the knot is closed by the id indirection | DOC | the same file's §1.3 sketch is `BayBuilder.start(...)…attach(...)` — the builder is real (F6), the stated reason is not |
| J24 | `guide.zh.md:566` "`Focuses.bay()/allCells()` 的 `ReadScope` 是空的，所以 EVERY_CELL 的读范围不会触发 `DirtyIndex.cellStamp`" | `predicate/Focuses.java:62` and `:69` both build `new ReadScope(CuboidSet.of(cells), Set.of(), List.of())`; the empty one is `Focuses.entity` (`:102`), which is D14's subject | the bay-wide and all-cells focuses bill nothing | they bill their cells; only the entity focus declares nothing | DOC | D14 names the right type and the wrong set of neighbours; the guide names the wrong types, and both are believed by a reader who wants to know what an entity check costs |
| J25 | `guide.zh.md:381` "环或重复产出者 → `Failed(SC-ENG-0102)`" | `13` has no `SC-ENG-0102` (rows are `0001`–`0006`, `0104`); the cycle is `SC-CHK-0102` (`13:348`), and the code refuses duplicate producers as `SC-CTX-0102` (`Blueprint.java:180`) and a cross-stage producer as `SC-CHK-0109` (`:152-157`) — F10 | one code for plan-construction failure | three different registered meanings | DOC | an unregistered code in the tutorial's pipeline table |
| J26 | `guide.zh.md:272` "折叠/遍历的规范序是 `(x, z, y)`" and `:383` "按 `SectionKey (x,z,y)` 排序" | cell/point order is `(y, z, x)` (`LatticePoint.compareTo`, B §5; `14` §4.3 "minor = canonical cell order (`y`, `z`, `x`)"); `SectionKey.compareTo`'s key is `(dimension, x, z, y)` (H §4 item 10) | one canonical order, `(x, z, y)` | two different orders, and neither is `(x, z, y)` | DOC | the guide states the ambiguity H reports in `12` as fact, in a section headed "坐标顺序固定" |
| J27 | `guide.zh.md:230` (field table) "`sharing` / `footprint` \| `Sharing` / `Footprint` \| … \| 是" and `:453` "`Bay` = `(id, cells, Coverage, Footprint, Sharing, Requirement, description)`" | `grep -rn "Sharing\|Footprint" --include=*.java src` → no type (only a test method name `…WithATwoCellFootprint`); `blueprint/Bay.java:19-20` = `(id, cells, coverage, mode, scope, requirement, description)` with `ClaimMode`/`ClaimScope` | both types exist and run today | neither exists; R32's rename is pending | DOC | R32's rename is registered lag (the consolidated §6 lists it), but the guide asserts the *post*-rename names as present-tense for a column headed "现在是否真的执行" |
| J28 | `guide.zh.md:564` "直接 `draft.contribute` 只存在于文档" | `context/ContextDraft.java:60-68` has `contribute(ContextKey, T)`, refusing a single-writer key with `SC-CTX-0109` and requiring `forSection(...)` first | bare `contribute` is doc-only | the member exists and is callable; it just needs a section scope | DOC | the true statement is H6's ("`contribute` requires a section scope"); the guide's version would send a reader looking for a missing member |
| J29 | `guide.zh.md:41` JSON `{"id": "fire", "box": [1,0,1,1,1,1], …}` and `{"id": "hollow", "box": [1,1,1,1,1,1]}` vs `:108` `BayBuilder.start(…, Cuboid.of(1, 0, 1, 2, 1, 2), …)` and `:98` `Cuboid.of(1, 0, 1, 2, 3, 2)` | box arithmetic is origin+size (`22` §12.1, `guide.zh.md:240-260`): `[1,0,1,1,1,1]` = 1 cell, `Cuboid.of(1,0,1,2,1,2)` = 4, `Cuboid.of(1,0,1,2,3,2)` = 12 | the JSON and the Java builder are "同样的结构" | the fire bay is 1 cell in JSON and 4 in Java; the hollow is 1 vs 12 | DOC | the guide's own §1.4 says the two halves of the document describe one object graph, so a reader comparing the two listings cannot reconcile them |
| J30 | `guide.zh.md:410` "`OccupantRef` 自带它被铸造时所处的 `SnapshotId`，足迹属于 `OccupantSnapshot`" | `occupant/OccupantRef.java:13` `record OccupantRef(InstanceId instance, NamespacedId type, OccupantKind kind, LatticePoint anchor, Cuboid footprint)` | the ref carries the capture; the footprint lives on the snapshot | the ref carries the *instance* and the footprint | DOC | C-1 covers the `SnapshotId` half and C §4 item 4 the footprint-in-the-ref half; the guide adds a third statement — the footprint-device sentence is the exact inverse of the code, so it belongs in C's list |
| J31 | `docs/README.md:21` "…the 1.21.1 NeoForge adapter is `src/main/java/dev/vfyjxf/cumulus/mc`." | `src/main/java` holds `Constants`, `StructureCumulus`, `util/Checks`, `data/lang/LangEntry` + 3 `package-info`; no `mc` package (I-12) | the adapter exists at a path | the path does not exist | DOC | `docs/README.md` is the doc set's own door and it points at the largest absent unit (`16`), while I-12 reports the absence from the `15`/`16` side only |
| J32 | `docs/README.md:26-27` "…there is no separate ledger of the distance, and a claim about the kernel is only as good as the code it points at." | the tree records distance in source comments: `base/FindingBundle.java:12` "FindingOptions, Builder, filter and the SC-DIA-0001 truncation finding are not built yet"; `MatchPolicy`'s "No field of this record is read yet" (G §4); `DerivationSpec.java:16` "Deviation: `args` is a `Map<String, Object>` placeholder until the `Arguments`…" | no gap ledger anywhere | three in-source gap markers exist (and R33 deleted the one that existed) | DOC | the sentence is a promise the tree does not keep; either the markers go, or `docs/README.md` names them |
| J33 | `docs/spec/zh/_translation-guide.md` §3 (fixed terminology) "invariant \| 不变量 \| I-1…I-15 保留编号" | `00-overview.md:74` `| I-16 | The ledger receives only derived rows…` (added by R36, `19:95`) | the invariant set stops at I-15 | it stops at I-16, in both languages (`zh/00-overview.md:74` mirrors it) | DOC | one-token fix; it matters because the memo is what a translator reads for numbering |
| J34 | `docs/README.md:4` "New to the project? Read [guide.zh.md](guide.zh.md) first" + `22` §15.5 "Every English original is mirrored under `zh/`, and the English set is the design of record wherever the two differ" + `zh/_translation-guide.md` §1 (a file-by-file mapping table with no row for `guide.zh.md`) | `docs/` holds `guide.zh.md` with no English counterpart; `docs/README.zh.md` is its only mention | the design of record is the English set | the entry-point guide exists only in Chinese, and the mirror rule says nothing about a Chinese-only original | RULE | either `guide.md` is written or §15.5/`_translation-guide` §1 is extended to cover `zh`-only files; the doc set currently has a hole the mirror discipline cannot see |
| J35 | `docs/spec/zh/_translation-guide.md` §2 rule 5 "表格结构不变 … 表格行数与列数不变" and `22` §15.5 | the mirrors exist for all 25 spec files + `_index.md`, and the sampled facts that matter for this re-check mirror correctly (`I-16` in `zh/00:74`; `R35`–`R37` present in `zh/19`) | mirrors stay in step | holds on every sampled fact | *(aligned)* | recorded so the mirror discipline is not listed as an open risk: the deltas are translation-sized, not content-sized, on the sample checked |

## 5. Judgement: is the nine-slice split sufficient?

**As a review of the code, yes; as a review of the design set, no — and the difference is
structural, not a matter of effort.**

*Sufficient:* the partition is by `src/kernel/java` package, and every code file in the three source
sets falls inside a slice (§1). The corpus is dense where code exists — the finding model is checked
three times over (A §1/§5, H §3/§5, B §5 on `Locus`), `Candidate`-free items are cross-verified
against independent probes (B's 48×4 enumeration, the 162-test corpus), and two of the three
"trivial orphans" I found (`base/package-info.java`, `Retention.java`) are read or verified under a
count slip rather than omitted.

*Not sufficient:* because ownership is the package axis, **anything with no code has no owner**.
Three consequences are visible in the artifacts, not just in principle:

1. **U10 is unowned.** `14-remedies.md` is ~20 types and ~20 codes; three reports cite three lines
   of it, in passing. The consolidation inherits the gap: its §4 must-fix list has no `14-remedies`
   heading and no §3 ruling touches remedies, so the largest single unimplemented unit in the spec
   set is absent from both the findings and the plan (J1–J4).
2. **The consolidated design is only read where a unit quotes it.** `22` §11.2/§11.3/§12.1/
   §12.3–§12.5/§13/§14/§15 were read by nobody, and the one claim I could check inside them — the
   nine-family list (J5) — is wrong against both `03` §7 and the code, while the P6 row (J7) is
   refuted by a report from *another* slice (H1) that never connected to `22` §14.
3. **The documents of record outside `docs/spec/*.md` are invisible.** `docs/guide.zh.md` (611
   lines, the newcomer's entry point, ~10 live divergences: J21–J30), `docs/README.md` (J31, J32),
   `_index.md`'s gates (J19, J20) and the translation memo (J33, J34) were cited by zero reports.
   `00` §3/§4's tables escape by the same mechanism: a unit checks the requirements it touches, so
   I-4 (J13), I-10 (J14), I-5 (J15) were each left to a slice that had already anchored its finding
   somewhere else.

**What to change, concretely.** (a) Add one slice for U10 (`14` end to end against the tree), which
also settles J3/J4 and gives `Edit`/`RemedyProvider` a carrier or a record. (b) Add one slice for
the receiver-side documents (`00` §3–§4 as tables, `19`'s R-records one by one, `14`'s codes,
`docs/README.md`, `_index.md`, `guide.zh.md`, `_translation-guide.md`) — it is cheap and independent
of any package. (c) Make "every R-record has a verdict in some report" a gate: twelve of
thirty-seven currently have none, and two of them (R16, R29) contradict the tree. (d) Extend `22`
coverage from "quoted by a unit" to "every numbered section read by at least one slice"; the
unquoted block is exactly §11–§15.

**Counts.** Code: 1 uncovered file, trivial; no uncovered test file (8 of 32 never cited by name).
Spec: 4 files (`14`, plus the three docs) and `22`'s §11–§15 materially unexamined; 3 of 16
invariants (I-4, I-5, I-10) and 2 of 15 requirements (R-04, R-13's `Edit` half) without an owner;
12 of 37 R-records without a verdict. This report's 35 rows: **23 DOC**, **3 RULE** (J4, J19, J34), 3
coverage notes for records that are absent only because the adapter is (J10, J16, J17), and 6 rows
recording an alignment (J6, J11, J12, J18, J20, J35) so the next pass does not re-open them.
