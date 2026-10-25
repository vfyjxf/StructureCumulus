# Front C — the Chinese mirrors (review round 1)

Project: `/Users/vfyjxf/ModdingGround/Project/StructureCumulus`
Scope: `docs/spec/zh/**` (25 units + `_index.md` + `_translation-guide.md`), `docs/guide.zh.md`.
Read-only on both language sets; the only write is this report.

## 1. Method

Mechanical pass over every EN/ZH pair, then reading only where the pass flagged a difference.

1. **Heading sets.** Parsed every `^#{1,6}` line outside fences. Level sequence and heading count
   match in **all** 25 unit files (only the `_index.md` preamble/rows differ in content, not shape).
2. **Fenced blocks, byte-for-byte.** `_translation-guide.md` §2.1 makes code blocks verbatim
   (`Java、JSON、伪代码…一律不翻译`), so any fence-content byte difference is a defect candidate.
   Script: `/Users/vfyjxf/ModdingGround/.qwen/tmp/c-mirror-scan.mjs` → `c-scan.json`.
   Result: **5 fences differ** (in `02`, `08`, `11`, `22`×2). Every one is inspected below.
3. **Backticked tokens.** Symmetric difference of the inline-code token sets per file, CJK-only
   tokens filtered out (translation legitimately puts Chinese inside backticks). 13 files flagged;
   each inspected.
4. **Tables.** Per-section table-row counts (script `/Users/vfyjxf/ModdingGround/.qwen/tmp/c-sections.mjs`)
   plus explicit `rows`/fence counts per `##`/`###` section. **Two** sections differ in row count:
   `01` §I (39→37) and `02` §4 (6→7).
5. **Codes and identifiers.** `SC-*`, `R-xx`, `I-xx`, `OQ-*` code sets — **identical per file**; the
   only code-level divergence found is a prose one (`13` §4.3/§4.5, §3 below). `StructureKernel.*`
   verb histogram differs (EN 4×`check`, ZH 2×`check` + 3×`match`).
6. **Legacy vocabulary and dead pointers.** `ReadEnvelopeAware`, `SpanExpectation`,
   `SettlementIntent`, `CheckResult.Plan`, `ClaimMode`, `ClaimScope`, `docs/status.md`,
   `TODO(GAP`, `Occupied`/`Owned`/`Audit` as a footprint, `"stage"` as a JSON key, `stage=`:
   all clean in `zh/**` **except** `Occupied` (`08`) and `STAGE`/`stage` residue (§3).
   `SettlementIntent`/`ReadEnvelopeAware`/`docs/status.md` appear only where EN also has them
   (`19` R25/R28/R32/R33 historical rows, `01:92`, `23:4`/`23:171`) — correct.
7. **Contract §H.** Each of §H's seven bullets re-derived against the files; result in §4 below.

Section numbering is preserved everywhere (heading level sequences identical), so every finding
below is anchored by line, not by section number.

## 2. What I checked

- All 25 `docs/spec/zh/NN-*.md` against their EN originals: headings, fences, tables, backticks,
  code families, determinism tuples, `StructureKernel.*` verbs, `Stage`/`STALE` occurrences
  (in fences, in backticks and in prose that names a code or a type).
- `docs/spec/zh/_index.md` against `docs/spec/_index.md` (routing table, unit table, gates).
- `docs/guide.zh.md` end to end against the English set it claims to mirror (its status block,
  §1.3 sketch, §2 pipeline table, §3.5–§3.7 shapes, §6 pitfalls).
- Cross-checked the mirror side of contract §H, and `docs/README.zh.md` as a same-class mirror
  outside this front's file list.

## 3. Ranked defects

### C1 — Critical · `zh/01-glossary.md` §I (U7 engine) is a whole generation stale

The vocabulary gate in `_index.md` reads "every cross-unit name in a module appears in
`01-glossary.md` with the same owner", so this table is the oracle a ZH reader consults.

`zh/01-glossary.md:182` vs `en/01-glossary.md:183` — `MatchSettler`: EN is "one revision read, one
CAS, and at most one in-call re-read when the ledger moved"; ZH is "一次门面调用内部…的步骤；
`Formed` 只在 `Settled` 之后才存在" — a different description (ZH repeats the `Formed`/`Settled`
fact that EN has moved to the `MatchResult` row) and drops the CAS/re-read facts that `SC-CLM-0004`
and `23` §7 depend on.

`zh/01-glossary.md:183` vs `en/01-glossary.md:184` — `MatchResult`: EN "every case but `Dropped`
carries the `Stage` it was run in, and only `settle` produces `Formed`"; ZH "每个分支都携带它运行时
所处的 `Stage`" — ZH's universal claim is false for `Dropped` and it omits the `settle`-only rule.
Breaks `23` §1/§6 and `24` §2.

`zh/01-glossary.md:184` vs `en/01-glossary.md:186` — `Formed`: ZH still
`(Stage, Blueprint, Siting, WorldView, InstanceRef, MatchOutcome, FindingBundle, Spend)`; EN is
`(InstanceId, Siting, MatchOutcome, ClaimSet, long generation, long contextRevision,
long ledgerRevision, SnapshotLease, FindingBundle)`. ZH's extra sentence "只有当计划在 `SETTLE`
中包含 `CLAIM` 时，声明才会被结算" is the pre-`23` settlement-trigger story.

`zh/01-glossary.md:185` vs `en/01-glossary.md:187` — `Failed`: ZH
`(Stage, Blueprint, @Nullable Siting, FindingBundle, Spend)`; EN `(List<Finding> findings)`.

`zh/01-glossary.md:186` vs `en/01-glossary.md:188` — `Undecided`: ZH
`(Stage, Blueprint, @Nullable Siting, UndecidedReason, FindingBundle, Spend)`; EN
`(UndecidedReason reason, List<Finding> findings, @Nullable MatchOutcome partial,
@Nullable RetryToken retry)`.

`zh/01-glossary.md:187` vs `en/01-glossary.md:190` — `UndecidedReason` values diverge completely:
ZH `UNREADABLE、ABSTAINED、ONE_SUBJECT_ABSENT、BUDGET_EXHAUSTED、CANCELLED、NO_STEPS、
LEDGER_UNAVAILABLE`; EN `BUDGET_EXHAUSTED、DIRTY_UNSTABLE、LEDGER_MOVED、LEDGER_UNAVAILABLE、
SUBJECT_ABSENT、LEASE_CLOSED、WORKER_FAILED、DESIGN_DEFECT`.

**Missing rows.** ZH has 37 table lines in §I where EN has 39: the `Admitted` row
(`en/01-glossary.md:185` — `(Stage, Siting, MatchOutcome, @Nullable ClaimPlan planned,
long ledgerRevision, SnapshotLease, FindingBundle)`, "what a read-only `check` returns") and the
`Deferred` row (`en/01-glossary.md:189` — `(RetryToken token, List<Finding> findings)`) exist only
in EN. `Admitted` is the arm contract §B.8–9 changes; a ZH reader cannot see it at all.

Minimal fix: re-translate `zh/01-glossary.md:182–187` from `en/01-glossary.md:183–190` and insert
the `Admitted` and `Deferred` rows in EN's order.

### C2 — Critical · `zh/02-architecture.md:69` puts a `stage` back on the request

```
zh/02-architecture.md:69  [engine]   MatchRequest(snapshotInfo, blueprint, siting, stage, lease, policy)
en/02-architecture.md:71  [engine]   MatchRequest(snapshotInfo, blueprint, siting, lease, policy)
```
Breaks `19` R27 ("no request carries a stage") and `23` §1/§6, and contradicts the same file's own
§5 "Matching" rule at `zh/02-architecture.md:117` (EN `en/02:121` puts no stage on the request
either). Note `zh/23-claim-entry.md:138` already states "| 文档 JSON | 没有 `"stage"` 字段 |", so
the ZH set contradicts itself.
Fence content must be byte-identical to EN (guide §2.1). Fix: delete `stage, `.

### C3 — High · `zh/22-consolidated-design.md` keeps `Stage` on the two purged data types

```
zh/22-consolidated-design.md:202  record Check<T>(NamespacedId id, Stage stage, NamespacedId bay,
en/22-consolidated-design.md:211  record Check<T>(NamespacedId id, NamespacedId bay,

zh/22-consolidated-design.md:601      @Nullable List<KeyRef> inputs, @Nullable Stage stage) {}
en/22-consolidated-design.md:634      @Nullable List<KeyRef> inputs) {}
```
The sweep's stage purge removed `stage` from `Check` and from the document IR `Spec` (front B's
check list: "`Check` has no stage"). `zh/09-blueprints.md:65` already matches EN
(`record Check<T>(NamespacedId id, NamespacedId bay, …`), so the ZH set is also self-inconsistent.
Fix: delete `Stage stage,` / `, @Nullable Stage stage`.

### C4 — High · `zh/02-architecture.md` keeps the `STALE` stage alive

```
zh/02-architecture.md:75   └─ STALE   ChangeSet → InvalidationIndex → re-run only affected steps
zh/02-architecture.md:104  | `STALE` | 是，定向 | `ReadSet` 与某个 `ChangeSet` 相交的那些步骤 | 未变化的步骤复用先前的判定 |
```
EN deleted both: its flow fence (`en/02-architecture.md:61–90`) has no `STALE` line, its §4 table
(`en/02:99–104`) has 4 rows, and the targeted re-check became prose at `en/02:106`: "A targeted
re-check is not a stage: `check` with a `ChangeSet` on the request re-runs the steps whose `ReadSet`
intersects it, and the rest reuse their prior verdict." Breaks `19` R27 and `23` §6
(`Stage.STALE` deleted). Fix: drop the flow line and the table row; add the EN sentence.

### C5 — High · `zh/00-overview.md:44` R-08 still lists `STALE` in the `Stage` enum

```
zh/00-overview.md:44  … `Stage`（`DESIGN`、`PROBE`、`FORM`、`SETTLE`、`STALE`）、按阶段的 `CheckPlan` …
en/00-overview.md:44  … `Stage` (`DESIGN`, `PROBE`, `FORM`, `SETTLE`, selected by the engine), per-stage `CheckPlan` …
```
Breaks `19` R27 (`Stage.STALE` 已删除) and contract §B.10. Fix: drop `、`STALE`` and add EN's
"selected by the engine".

### C6 — High · `zh/11-engine.md:298` determinism tuple still names `stage`

```
zh/11-engine.md:298  … 固定 `(blueprint, snapshotId, siting, stage, policy, catalog, frozen ledger revision, cancellations)` …
en/11-engine.md:315  `(blueprint, snapshotId, siting, policy, catalog, frozen ledger revision, cancellations)`
```
A request-carried stage would enter the determinism tuple; EN removed it. (§H bullet 5, lower
half.) Fix: delete `stage, `.

### C7 — High · `zh/02-architecture.md:117` and `zh/04-api-doctrine.md:10` still call the attempt `StructureKernel.match`

```
zh/02-architecture.md:117  | 匹配 | … `StructureKernel.match` 是同步的；执行器归宿主调度器所有（`20`）。 |
en/02-architecture.md:121  | Matching | … `StructureKernel.check` is synchronous and writes nothing; `settle` is the only writer, and the host scheduler owns the executor (`20`). |

zh/04-api-doctrine.md:10   | **首次使用者（First-use caller）** | `Blueprint` 构建器、`StructureKernel.match`、sealed `MatchResult` …
en/04-api-doctrine.md:11   | **First-use caller** | `Blueprint` builder, `StructureKernel.check`/`settle`, sealed `MatchResult` …
```
`StructureKernel.match` survives in EN only in `19` R16's historical row. `02` additionally drops
the operative clause "writes nothing; `settle` is the only writer". Breaks `23` §1 (two entry
points) and contract §E.20. Fix: `check` (and `settle` where EN has it); restore the dropped clause.

### C8 — High · `zh/22-consolidated-design.md:604` runs `SpecResolver` at a different time and place

```
zh/22-consolidated-design.md:604  `SpecResolver` 在 `StructureKernel.apply(registries)` 中运行：…
en/22-consolidated-design.md:637  `SpecResolver` runs during `compile` (DESIGN): …
```
`StructureKernel.apply(registries)` exists nowhere in EN (EN's `StructureKernel.*` verbs are
`check`/`settle`/`create`/`compile`/`derive`/`explain`). The ZH set contradicts itself: its own
`zh/22-consolidated-design.md:762` (D4 row) says "`SpecResolver` 在 DESIGN", matching EN's D4 at
`en/22:802`. Breaks `19` R2 (DESIGN is the only document→blueprint path) and the nine-operation
facade (§E.20 — no tenth operation). Fix: replace with EN's "runs during `compile` (DESIGN)".

### C9 — Medium · `zh/13-findings.md` still calls the second claim dimension `mode`

```
zh/13-findings.md:331  | `SC-BPT-0002` | ERROR | ✓ | 两个舱位以不可共存的模式重叠 |
en/13-findings.md:340  | `SC-BPT-0002` | ERROR | ✓ | two bays overlap with sharing values that cannot coexist |

zh/13-findings.md:444  … 每个归属方一条 `Note`，携带归属方 id 与模式。…
en/13-findings.md:464  … carrying the owner id and its sharing value. …
```
(`13` §4.4 in §H's numbering; the code table row is the second site and §H does not list it.)
Breaks contract §A.2 (`sharing` is the component name; `mode` is not live vocabulary) and §A.3.
Fix: 共享值 in both places.

### C10 — Medium · `zh/08-claims.md:268` says "no forced `SHARED` mode"

```
zh/08-claims.md:268  单元格，且不强制 `SHARED` 模式。**宿主，一个建造棒** …
en/08-claims.md:280  four owned ones, with no forced `SHARED` sharing value. **Host, a build wand** …
```
The same sentence was already fixed in ZH §4.3 (`zh/08-claims.md:151` reads "也不强制 `SHARED` 共享
值"), so §6 is the only hold-out. (§H bullet 2.) Fix: 共享值.

### C11 — Medium · `zh/08-claims.md:242–243` reference-block comments still say `Occupied`

```
zh/08-claims.md:242  …   // two Occupied rows, two cells
en/08-claims.md:254  …   // two Bound rows, two cells
zh/08-claims.md:243  …   // EVERY_CELL + Occupied: eight asserted, four owned
en/08-claims.md:255  …   // EVERY_CELL + Bound: eight asserted, four owned
```
`Occupied` occurs **nowhere** in the EN set; contract §A.1 says the older `permits Owned, Occupied,
Declared, Audit` form does not exist. (§H bullet 1.) Fix: `Bound`.

### C12 — Medium · `zh/09-blueprints.md:295` still lists `B-12` among what `build()` throws

```
zh/09-blueprints.md:295  `build()` 恰对 §4.6 中由 `build()` 执行的行（B-1…B-4、B-6…B-10、B-12）以及 …
en/09-blueprints.md:308  `build()` throws `IllegalArgumentException` for exactly the `build()` rows of §4.6 (B-1…B-4, B-6…B-10)
en/09-blueprints.md:310  … and B-12 is held by the types.
```
ZH's `build()` therefore throws for a row EN says the types hold —
`en/09-blueprints.md:297` registers `B-12 | … | the type`. (§H bullet 4.) Fix: delete `、B-12`,
add EN's "B-12 由类型本身持有".

### C13 — Medium · `zh/11-engine.md:96` expands a `step` where EN expands a `stage`

```
zh/11-engine.md:96   try { plan = request.blueprint().expand(step); }   // 1 expand the step's plan, pure
en/11-engine.md:102  try { plan = request.blueprint().expand(stage); }   // 1 expand the stage's plan, pure
```
A fence-content byte difference (guide §2.1) and a semantic one: `expand(S)` takes a stage
(`en/02:114`, `en/09 §4.8`), and `zh/guide` itself writes `expand(stage)` twice
(`guide.zh.md:381`, `guide.zh.md:455`). (§H bullet 5, upper half.) Fix: `expand(stage)`.

### C14 — Low · `zh/_index.md:24` loses the pointer that fixes the remedy verb

```
zh/_index.md:24  | 修复计划、叠加层、试运行、事务、自动建造 | [14-remedies.md](14-remedies.md) |
en/_index.md:27  | Repair plans (the `solve` operation), overlays, dry-run, transactions, auto-build | [14-remedies.md](14-remedies.md) |
```
Contract §E.20 makes `solve` the remedy operation and calls `15`'s `repair(...)` the odd one out;
the ZH routing table drops the only hint that `14` is about `solve`. Fix: add "（`solve` 操作）".

### C15 — Low · `zh/_index.md:3–4` loses the design-set revision front matter

EN `_index.md:3–7` names "the consolidated design at revision 5 and the `23`/`24` claim decision
records of 2026-10-23", that "the consolidated design (`22`) and the two decision records are
drafted", and that open questions are recorded "per unit and in `22` §16". ZH keeps only
"五个基础文件与全部十二个单元文件已草稿". Fix: re-translate the blockquote.

### C16 — Low · `guide.zh.md:180` and `guide.zh.md:603` still give `Check` a stage

```
guide.zh.md:180  | `Check<T>` | `Focus<T>` + `Expectation<T>` + 阶段 + 所属舱位 | 编译期钉死两边同一个 `T` |
guide.zh.md:603  | 检查 `Check` | 焦点 + 谓词 + 阶段 + 所属舱位，编译期同型 |
```
The English set defines `Check<T>` as `(id, bay, focus, expectation, description)`
(`en/09-blueprints.md:65`, `en/22-consolidated-design.md:211`), and the guide's own sketch builds a
5-argument `Check` (`guide.zh.md:114`) — so the guide contradicts both the English set and itself.
It is the third artefact carrying the same pre-purge shape as C3. Fix: drop 阶段; if the intent was
the `Requirement`'s stage grouping, say that instead.

### C17 — Low, outside this front's file list · `docs/README.zh.md` is missing the gap-ledger paragraph

`docs/README.md` (mtime today) ends with "The design runs ahead of the kernel… there is no separate
ledger of the distance, and a claim about the kernel is only as good as the code it points at."
`docs/README.zh.md` (untouched since 2026-10-21) has no equivalent paragraph. This is the §F (R33)
replacement for the deleted `docs/status.md`, so the ZH entry point never got it. Report as a
mirror defect even though the brief's file list stops at `zh/**` + `guide.zh.md`: the same guide
(`zh/_translation-guide.md` §1) maps `docs/README.md` → `docs/README.zh.md`.

## 4. Checked and cleared

**Contract §H, bullet by bullet** — the seven bullets are *not* all still behind:

| §H bullet | State |
| --- | --- |
| `08` §5 reference-block comments (`two Bound rows`, `EVERY_CELL + Bound`) | **still behind** → C11 |
| `08` §6 "no forced `SHARED` sharing value" (was `mode`) | **still behind** → C10 |
| `08` §9's falsifier row "A `Bound` bay owning its whole box" | **already carried** — `zh/08-claims.md:308` = `en/08-claims.md:321` |
| `09` §4.7 `build()` throw list `(B-1…B-4, B-6…B-10)`, `B-12` held by types | **still behind** → C12 |
| `11` §3 `expand(stage)` + the determinism tuple | **still behind** → C13, C6 |
| `13` §4.4's claim-conflict note "the owner id and its sharing value" | **still behind, and in two places** → C9 |
| `18` §5's `I-6` row, `19` R2, `21` §3.1's three rows | **already carried** — `zh/18-verification.md:124` = `en/18:127`; `zh/19-open-proposals.md:15` = `en/19:16`; `zh/21-authoring.md:117` = `en/21:121` |

So five of the seven are real remaining deltas; two were already dispatched and have landed.
Everything else in §3 above is **outside** §H — the `STALE`/`Stage` residue in `00`/`02`/`11`/`22`
(C2–C6), the `StructureKernel.match` residue (C7), the `SpecResolver` regression (C8), and the
`01` §I vocabulary table (C1) were **not** on the known-delta list.

**Also verified clean** (no finding): all 25 files' heading structure; fence counts; `SC-*`, `R-xx`,
`I-xx` code sets per file; no `ReadEnvelopeAware`/`SpanExpectation`/`SettlementIntent`/
`CheckResult.Plan`/`docs/status.md`/`TODO(GAP` pointer outside the EN-identical historical rows;
no `"stage"` JSON key and no `stage=` besides the two EN-identical `23` rows; `zh/22`'s three
D-tables and `19`'s R-table have EN's row counts; `zh/14` §4.2 and `zh/22` §5 threading table match
(their apparent differences were line wrapping); `zh/08` §4.3/§4.4/§4.5, `zh/10`, `zh/16`, `zh/17`,
`zh/12`, `zh/15`, `zh/20`, `zh/24` fences byte-identical.

## 5. Could not confirm

- **Not a mirror defect, but in the same sentences.** Several English originals are themselves
  internally inconsistent where ZH faithfully follows one side:
  `en/02-architecture.md:140` still has `place`/`stage` in §5's determinism tuple (ZH copies it —
  C6 is the §4.8 tuple only); `en/22:784` (D3) says "`Check` carries `id` and `stage`" while
  `en/22:211` does not (ZH 22 copies the D3 claim *and* keeps the field, so C3 stands, but the EN
  contradiction is front A/B's); `en/01-glossary.md` `Focus<T>` row lists `decisionKind()`/
  `staticDecisionCells()` while `en/22:204–208` declares `desc()`; `en/08:20` says `ClaimRow` is
  `(cell, sharing, owner, bay)` while `en/08:48` declares a `Siting siting` component — `guide.zh.md`
  §3.6 follows the record form. Reporting these under the English anchors, not as C-findings.
- **`zh/_translation-guide.md` has no EN original**, so nothing to diff it against; its file map and
  hard rules were used only as the criteria above. It has no stale pointer of its own.
- **`guide.zh.md` §6 pitfall 6** ("`Coverage.EVERY_CELL` 的格子目前不进脏标记") and §6 pitfall 10
  ("31 个错误") are code-state assertions, not design-set assertions; I did not open `src/**` to
  verify them, and they conflict with no file in the English set.
