# G3 — `g-engine`, `h-context-findings`, `i-spi-adapter-tests`

Files edited: `11-engine.md`, `12-context.md`, `15-spi.md`, `16-adapter.md`, `18-verification.md`.
`13-findings.md` was **not** touched (another applier owns it this round); its required change is in §4.

## 1. Applied (12 rows)

- **G13** — `11` §3, the prose after the `MatchRequest` sketch — the request's first component is the
  `Blueprint` the catalog frozen at `apply` resolved, before unit order is computed. `BlueprintId`
  occurs nowhere in the spec any more.
- **H5** — `12` §3 contract table (new row *Absence is published*) and §4.2 (new publication bullet) —
  a frame carries an entry for every key its draft decided, and a decided key with no value is
  recorded as an absence marker, so the frame shadows an older value instead of letting a reader fall
  back to it.
- **H6** — `12` §2 `ContextDraft` row, §3 `ContextDraft` sketch, §4.3 first and third bullets, §5
  sketch comment — the draft exposes `forSection(SectionKey)`, `refuse`, `declined`, `read`,
  `publishes`, `keys`; `contribute` is refused on a draft with no section scope; `refuse`/`declined`
  are named as the producers of "a refused section makes the key absent" and of `whenAbsent`.
- **H8** — `12` §4.4 — `SC-CTX-0301` is a label-less `WARNING` whose message names the augment key.
  (The `P ✓` half of the row lives in `13` §4.3 — see §4.)
- **H12** — `12` §4.3 fifth bullet and §9 row 2 — a second write of a *different* value in one
  attempt is `SC-CTX-0107`; writing the same value twice is accepted.
- **H14** — `12` §3 `ContextView.at` comment and §4.2 readers bullet — `at` never widens: a tier above
  the view's own is clamped to it.
- **I-04** — `15` §2 `ContextKeys` row, §3 `ContextKeys` sketch, §3 paragraph after the block, §4.3
  last paragraph — `of(id, type, retention, merge, whenAbsent)` builds a key and
  `register(CatalogsDraft, ContextKey)` is the only binding; the draft is `kernel.context`'s and
  `Catalogs.Builder` implements it, so no `context` type imports `spi`; a repeated id is refused when
  it registers, not at `build()`.
- **I-05** — `15` §1 owns row and §3 (the sketch is now two blocks) — `NamespacedId` is `kernel.base`'s
  record, printed under `package dev.vfyjxf.cumulus.kernel.base`; this unit owns its grammar and total
  order, not the type.
- **I-08** — `15` §2 `Catalog` row, §3 `Catalog` sketch, §4.2 first paragraph, §9 F1 — `Catalog` is a
  final class with `family()`, `require`, `has`, `putAll`, `fill`; `put` refuses a second, *different*
  value for an id immediately and re-putting the identical instance is a no-op; `build()` only
  freezes.
- **I-13** — `16` §3 `WorldEdits` sketch and §5 sketch (4) —
  `EditTransaction begin(WorldView, Siting, ClaimView, long ledgerRevision, PlanDigest)`, matching
  `14` §3, called with `claims`, `ledger.revision()` and `wall.digest()`.
- **I-14** — `16` §4.7 gate row and blast-radius row — the four gate conditions that have a registry
  code carry it (`SC-MC-0301` off-thread, `0305` outside the border, `0401` gate denies, `0307` over
  `maxCells`); the blast-radius line is `SC-MC-0307` refused, not `0401`.
- **I-15** — `18` C-12, C-13, C-16, C-18, C-20, C-25, plus C-45 for the same rename — the
  counterexamples are stated at the check level over `CheckPlan` and
  `ComputationContext`/`DeriveContext`/`FocusContext` instead of the non-existent coarse step kinds;
  `ReadScope` replaces `ReadSet`; `DeriveContext` replaces `DerivationContext`; C-25 no longer names
  the non-existent `FindingBundle.none()`. The §2 layer-table part of this row is not applied — §4.

## 2. Held by R

None. No `CODE` row in these three reports quotes code that `19` R27/R35/R36/R37 registers as lag:
G13's quote is `MatchRequest.blueprint` (not the request's `Stage`), G14's is `SnapshotLease.view()`'s
return type (not the two added methods or the deleted `SnapshotInfo`), and the H/I rows quote context,
catalog, adapter and test names. I-13's `PlanDigest` is `14`'s *edit* plan, which R36 leaves in place
by name.

## 3. Stale rows

None. Every `CODE` row's code anchor was opened and still matches the tree: `MatchRequest.java:19`,
`SnapshotLease.java:12`, `ContextFrame.ABSENT` + `StructureContext.getAt`, `ContextDraft`'s seven
members, `ContextView.nonFiniteTotal`, `DraftState.put`, `StructureContext.View.at`,
`ContextKeys`/`CatalogsDraft`/`Catalogs.Builder`, `NamespacedId`'s package, `Catalog`/`Catalog.Builder`,
`16:109`/`16:444` (still two-argument in the doc), `16` §4.7/§8 code assignment,
`FindingBundle`/`ReadScope`/`DeriveContext`/`CheckPlan`.

## 4. Cross-file follow-ups

- **G14** — `20` §4.1 and `22` §5.5: `SnapshotLease` is sketched with `LevelSnapshot view()`; the
  kernel interface returns `WorldView` (`SnapshotLease.java:12`), because `LevelSnapshot` lives in
  `dev.vfyjxf.cumulus.mc.capture` and I-1 forbids the edge. Not applied: `20`/`22` are outside this
  group's files.
- **I-13** — `01` §L, the `EditExecutor` row (line 275) still reads
  `EditTransaction begin(WorldView, Siting)`; it must become
  `begin(WorldView, Siting, ClaimView, long ledgerRevision, PlanDigest)`, which `14:171-172` and
  `17:301` already use.
- **I-15** — `18` §2's test-layer table still names `.../expectation`, `.../finding`, `.../remedy` and
  `src/kernelTest/resources`. Left untouched on purpose: the U9 path is H3's `DOC` verdict
  (`kernel.finding` is the spec's, the code's `base` is the packaging defect) and the U3 path is the
  package decision `03` §3 / `00` §5 own.
- **I-15** — `01` §I carries two rows for one concept (`ReadScope` at :108, `ReadSet` at :206); `18`
  now says `ReadScope`, so the `ReadSet` row should be withdrawn.
- **H5, H6** — `22` §8.3 still sketches `interface ContextDraft { put; contribute; }` and neither `22`
  §5.5 nor §8.3 mentions that a frame records absence.
- **H8** — `13` §4.3, the `SC-CTX-0301` row: the `P` cell is `✓`, but the code builds that finding with
  no label and its subject is an `AugmentKey`, so the cell must read `–` (the message names the key).
  `13` untouched this round.
- **Naming** — `03` §4's suffix table still says `DerivationContext`; `22` and the code say
  `DeriveContext`, which `18` C-20 now uses.

## 5. Verification

- `11-engine.md` — no table touched; fence count 18 (even); `BlueprintId` no longer occurs in any
  `docs/spec/*.md`.
- `12-context.md` — fence count 6; §2 and §3 contract tables stay two columns; every member named in
  prose now exists in the sketch (`forSection`, `refuse`, `declined`, `read`, `publishes`, `keys`), and
  every sketch member exists in `context/ContextDraft.java`; §5's sketch comment now calls `forSection`
  before `contribute`; §7 P3 and §8's dependency row still say "one registration funnel".
- `15-spi.md` — fence count 6 after §3 was split into a `kernel.base` block and a `kernel.spi` block;
  §1 two columns, §2 three columns, §9 three columns; no `SequencedMap` and no `id()` left; §3's
  commentary, §4.2, §7 P1 and §9 F1 now all put the refusal on `put`/registration; `ContextKeys`'s two
  methods are the code's `of`/`register`.
- `16-adapter.md` — fence count 6; §4.7 stays two columns; `begin`'s five-argument form is consistent
  across §3, §5 and the gate row; `PlanDigest` is defined in `14` §3/§4.2 and `EditPlan.digest()`
  exists there; every `SC-MC` code used in §4.7 appears in §8's registry.
- `18-verification.md` — §3 keeps five columns on all 51 rows; no fence; `ReadSet` no longer occurs in
  `18`; every type named in the edited rows (`CheckPlan`, `ComputationContext`, `DeriveContext`,
  `FocusContext`, `ReadScope`, `FindingBundle.of(...)`) exists in `src/kernel/java`.
