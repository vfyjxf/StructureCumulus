# Stage-3 fact sheets — R3…R14

> Role 2 of the stage-3 re-check. Read-only except this file. Baseline `d1bd920`.
> Every anchor below was re-read at the line quoted. The question set is
> `alignment-report.md` §8.2 (which supersedes §3); the option sources are the nine slice reports
> (`a`–`i`), the three re-checks (`J`, `K`, `L`), and that report.
> Nothing here re-hunts a divergence. Each sheet states only what is needed to *hand the question
> off*: the question, why it cannot wait, the quoted anchors, the options with their consequences,
> and the option the published set does not have.

## 1. Method

- All code anchors are this repo's own sources (`src/kernel/java`, `src/main/java`, `src/kernelTest/java`),
  read with `read_file`/`grep_search`. No dependency source is involved — no sheet asserts a Minecraft,
  NeoForge or other third-party fact — so `sourcescout` was not bound for this role.
- Every doc anchor was opened at the quoted line. Where a slice report's line number was off, the number
  I found is the one printed, and the sheet says so.
- **Writability standard** (L's, adopted here): an option is *not writable as written* when it
  contradicts a recorded decision (`19` R27–R37, `23` §7, `24` §5/§9), contradicts another unit's own
  statement, or cannot compile / cannot be expressed in the language (e.g. an interface static method
  being "package-private"). Ripple is a cost, not an unwritability.
- Three of the sheets record a **fact error in the published option set** rather than a missing option
  (`R11`'s "package-private `combine`", `R12`'s "`SC-CTX-0202` has no producer", `R10`'s "only one enum
  is decided by evidence"). Those are called out in place, because a hand-off built on them would write
  code that does not compile or a table that deletes a live producer.
- "Verified" lines are things I checked that the reports do not state, or state loosely.

---

## R3 — what carries a unit's stage

**Question.** Does a `Check` / `Computation` / `DerivationSpec` declare a `Stage`, or does something
else supply it, given that the spec forbids authors from declaring one and `expand(S)` must still select
"every `Check` of `S`"?

**Why it must be decided now.** `Stage` is a constructor component of three records and the selector of
`Blueprint.expand`. Any change moves `I-6`, `23` §7, `09` §4.8 step 4, B-11, `13`'s `SC-CHK-0109` row,
`12` §5 / `22` §6.4's unit sketches, `16`'s `PROBE`/`FORM` levels, and `13` §4.5's stage ordering key.
Nothing that builds a plan can be written before this is answered.

**Facts.**

- Doc — `00-overview.md:64`:
  > `| I-6 | Every check runs in a `Stage` the engine selects, with its own `CheckPlan`, budget and failure policy, including design-time validation; no author declares a stage and no caller chooses one. | U7, U9 |`
- Doc — `23-claim-entry.md:147-148`:
  > `| `Check` | `(id, bay, focus, expectation, description)`, no `stage` |`
  > `| document JSON | no `"stage"` field |`
- Doc — `23-claim-entry.md:152`:
  > `| settlement trigger | the `settle` entry point, no longer `stage=SETTLE` |`
- Doc — `09-blueprints.md:330-331`:
  > `The engine selects the stage and computes the order; an author declares neither a stage nor a unit order.`
- Doc — `09-blueprints.md:343`: `4. every `Check` of `S`, after the computations it reads;`
- Doc — `09-blueprints.md:296` (B-11): `a check never reads a `ContextKey` produced at a later stage (`SC-CHK-0109`)`
- Doc — `10-documents.md:338` (`SC-DOC-0604`, retired): `Retired: the document carries no `stage` field (`23` §7).`
- Doc — `22-consolidated-design.md:915` (§16.1 item 16), which states the question itself:
  > `16. **What replaces `Stage` in `I-6` and `R-08`.** With stages no longer declared, the checkpoint that `I-6` requires and the fast-fail that `R-08` names need another carrier — the engine's own phase order, or the plan's step structure?`
- Code — `predicate/Check.java:14`:
  ```java
  public record Check<T>(NamespacedId id, Stage stage, NamespacedId bay, Focus<T> focus,
                         Expectation<T> expectation, @Nullable String description) {
  ```
- Code — `context/Computation.java:14` (`Stage stage();`), `blueprint/DerivationSpec.java:19-20`:
  ```java
  public record DerivationSpec(NamespacedId deriver, @Nullable Map<String, Object> args,
                               NamespacedId output, Stage stage, int order)
  ```
- Code — `blueprint/Blueprint.java:95`, `:105`, `:121` are the three selectors:
  `if (derivation.stage() == stage)`, `if (computation.stage() != stage)`, `if (check.stage() == stage)`.
- Code — `blueprint/DerivationSpec.java:32-33`: `/** Uniqueness is {@code (stage, order)} inside a blueprint, so order sorts derivation intent. */`
- Verified myself: `Stage` has five constants (`base/Stage.java:1-9`, incl. `STALE`); `ContextTier` has
  four (`context/ContextTier.java`: `BLUEPRINT, PROBE, FORM, SETTLE`), so a tier→stage map exists 1:1 if
  `DESIGN` is renamed `BLUEPRINT`. `Deriver.tier()` (`context/Deriver.java:13`) already returns one.
  `ContextKey` carries `Retention` (`ATTEMPT, OUTCOME, CONTEXT`), **not** a stage or tier
  (`context/ContextKey.java:10-12`), so option (d) needs a new key component. The catalog families hold
  finished values, not factories — `Catalogs.Builder` fields are `Map<NamespacedId, Expectation<?>>` …
  `Map<NamespacedId, Deriver<?>>` (`spi/Catalogs.java:74-78`), and the class javadoc says
  `entries are values, never classes, so no lookup reflects` (`:14-16`).

**Options.**

- **(a) No stage anywhere.** Delete `stage()` from the three records; `expand()` takes no argument.
  → Forced ripple the report admits but does not cost: `09` §4.8's ordered list ("every `Computation`
  of `S`", "every `Check` of `S`") and `22` §4's stage table describe four different unit sets and have
  no input; B-11/`SC-CHK-0109` become unwritable (nothing says "later"); `13` §4.5's order key 2
  (`stage | Stage declaration order`) loses its population; `16`'s `PROBE`/`FORM` host levels lose
  their selector; `CheckPlan(Stage stage, …)` and `MatchResult`'s per-arm `Stage` have no producer
  either. Cheapest text, largest deletion.
- **(b) The unit declares it** (today's code). → Restore `stage` to `23` §7's `Check` row and to the
  document grammar's `"stage"` field (un-retiring `SC-DOC-0604`), amend `I-6` ("no author declares a
  stage" → "the author may pin one; the engine still chooses the pipeline"), delete `22` §16.1 item 16.
  Keeps `expand(S)`, B-11, `SC-CHK-0109` and `(stage, order)` uniqueness (`DerivationSpec.java:32-33`,
  `BlueprintBuilder`'s B-6 message) intact. One invariant and one decision table to amend.
- **(c) The catalog declares it.** Units stay stage-free; `expand(S)` still has a key because the
  resolved entry carries the stage. → `Catalog<T>` must gain a stage with each value (a
  `(Stage, T)` family entry or a `stage` parameter on the builder's four unit setters), `SpecResolver`
  (`10` §4.6) stamps the resolved unit, and one row per family is added to `09` §4.8. Keeps `I-6` and
  `23` §7 verbatim. Cost: every catalog construction site changes shape, and a document cannot override
  a catalog entry's stage (the document has no field — `10:338`).
- **(d) Derived from the output key's tier** (H-R6(B), `Deriver.tier()` as precedent). → `ContextKey`
  gains a stage/tier component, `Deriver.tier()` and the key's tier must be kept in step, `expand(S)`
  groups by the tier of `output()`, and a key with several consumers at different stages needs a
  tie-rule. `DerivationSpec.output()` exists (it names a key), but `Check` has no output at all — a
  check would have to take its stage from the keys it reads, which B-11 makes circular.

**Missing option?** **Yes, three things.**

1. **The `Requirement` / `Blueprint` declares one stage for a whole bay's unit set** (H-R6(A)'s actual
   wording: "a bay's `Requirement` (or the whole-plan `Blueprint.expand(stage)`) names the stage"). That
   is not (b) (no per-unit component), not (c) (not the catalog) and not (d) (no key tier): the stage
   becomes a blueprint-value stamp, so `expand(S)` filters bays by their declared stage. It should be
   offered separately because it is the only option that keeps `(stage, order)` uniqueness while leaving
   `Check` five components.
2. **Which stage an out-of-match finding carries.** `Finding.stage` is mandatory and `build()` throws
   without it (`base/Finding.java:253` "stage is required"), while nothing in `03`/`04` says what a
   finding raised outside a match carries (A7; K §6 says "Add (iv)"). Every option above leaves this
   open, and it is the same field the R12 exception-vs-diagnostic question lands on.
3. **Under (a), what replaces B-11 and `SC-CHK-0109`.** The option says "撤 `I-6`/`R-08`", but B-11 is a
   blueprint validation that survives in no other form; the option set must say whether the rule is
   deleted or re-expressed over dependency order.

---

## R4 — per-cell or per-section view model

**Question.** Is `WorldView` the per-cell window `06` describes (with a five-valued per-cell
`readStatus`) or the per-section window the code and `20`/`22` use?

**Why it must be decided now.** `06` is the module of record for U2 and is being rewritten wholesale;
`WorldView` is the type every focus, expectation, fold and the capture SPI read. `snapshotId()`,
`sectionOf(cell)`, the nested view and the capture SPI's success arm all move with the answer, so R4
gates R5, R6 and half of `06`.

**Facts.**

- Doc — `06-occupants.md:90-97`:
  ```java
  interface WorldView
      SnapshotId snapshotId()
      Cuboid bounds()
      @Nullable OccupantSnapshot occupant(LatticePoint cell)
      List<OccupantSnapshot> occupants(Cuboid box)
      ReadStatus readStatus(LatticePoint cell)
      default @Nullable OccupancySummary summary(Cuboid box) { return null; }   // palette-derived
      default @Nullable ChunkGrid chunkGrid() { return null; }                  // no chunking
      default @Nullable ChunkContent chunk(ChunkKey key) { return null; }       // palette facade
  ```
- Doc — `06-occupants.md:80`: `- `view.occupant(cell) != null ⇔ view.readStatus(cell) == READABLE` (§7.2).`
- Doc — `06-occupants.md:159`:
  > `Precedence `OUT_OF_BOUNDS > DENIED > STALE > NOT_CAPTURED > READABLE`. Reserved codes (U3/U7 build the finding): `SC-OCC-0101` out of bounds, `0102` not captured, `0103` denied, `0104` stale, …`
- Doc — `22-consolidated-design.md:107-124` (§5.1) prints the other set:
  ```java
  interface WorldView {
      SectionSlot slot(SectionKey key);
      OccupantSnapshot occupant(LatticeKey cell);
      @Nullable OccupantSnapshot occupant(OccupantRef ref);
      List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
      @Nullable NestedLevelSnapshot nested(LatticeKey cell);
      long contentDigest(SectionKey key);
  }
  ```
  with `- `ReadStatus` = `READABLE | OUT_OF_BOUNDS | NOT_CAPTURED | DENIED | STALE`; air is readable, a refused cell is never air.`
- Code — `occupant/WorldView.java:13-29`:
  ```java
  SectionSlot slot(SectionKey key);
  @Nullable OccupantSnapshot occupant(LatticePoint cell);
  @Nullable OccupantSnapshot occupant(OccupantRef ref);
  List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
  @Nullable NestedLevelSnapshot nested(LatticePoint cell);
  long contentDigest(SectionKey key);
  ```
- Code — `occupant/ReadStatus.java:6-11`: `READABLE, OUT_OF_BOUNDS, NOT_CAPTURED, DENIED, STALE`.
- Code — `occupant/SectionSlot.java:13-16`: `default boolean readable() { return this instanceof Air || this instanceof Present; }`; the `Refused` arm (per the report, `:27`) carries `(ReadStatus status, NamespacedId code)`.
- Verified myself: the code's view has **no** `snapshotId()`, no `bounds()`, no `readStatus(...)`, no
  `summary`/`chunkGrid`/`chunk`. `SectionKey.of(NamespacedId dimension, LatticePoint cell, int cellSize)`
  (`space/SectionKey.java:7`) needs a `cellSize` that the view exposes neither as `cellSize()` nor as
  `dimension()`, so "convert the cell yourself" is not implementable from a `WorldView` today. `22` §5.1
  spells the cell parameter `LatticeKey`, the code spells it `LatticePoint` (`WorldView.java:20`) —
  same disagreement, one more edit whichever way R4 goes.

**Options.**

- **(a) Section model is normative, `06` rewritten + `sectionOf(cell)` + `snapshotId()`.** → Rewrite
  `06` §3.3, §4.1, §4.5, §5, §7.2 from `22` §5.1; the five `ReadStatus` values become section-grain
  (reachable on `SectionSlot.Refused`), so a *cell* question collapses to `occupant(cell) == null` and
  an out-of-bay cell is reported as unreadable (`BayView.occupantAt` returns `null` for both, per C-13)
  — i.e. `13`'s `SC-FOC-0301` keeps a false positive it already has. `snapshotId()` is a new member
  (the same edit R5 needs, counted once). `16` §3's `LevelSnapshot` sketch loses four members.
- **(b) Per-cell model is normative.** → Rewrite the code (14 files in `occupant`, plus every reader:
  `Focuses`, `BayView`, `MatchSession`, `DirtyIndex`, `TestWorld`), plus `20` §4.1/§5, `22` §5.1/§5.4
  and the cache story (`SectionSlot`/`SectionSnapshot`/`contentDigest` are section-shaped). Deletes the
  section snapshot model the whole cache argument rests on.
- **(c) Both, per-cell as a convenience shell over sections.** → Two public read APIs with no owner; a
  mapping table (`ReadStatus` per cell ← which section status? `Air` is readable, `Refused` is not,
  `Present` may still refuse one cell) has to be written and kept truthful by every view implementation;
  C-13 already shows what happens when the mapping is left to each consumer.

**Missing option?** **Yes.**

1. **C-13(b)/K3.8's option is not in the set**: keep the section model *and* restate §4.1's contract as
   `default ReadStatus readStatus(LatticePoint)` derived from `slot(sectionOf(cell))`, plus
   `FindingCode refusedCode(SectionKey)` so the refusal's own code reaches the finding. That is
   distinct from (a) as written (which deletes §4.1's precedence table and drops `refusedCode`), and
   it is the option C-13 recommends — the consolidated adopted C-13(c)'s shape and called it (a).
2. **Which members the view gains**: `SectionKey sectionOf(LatticePoint)` *or*
   `dimension()` + `cellSize()` — L's C-13 correction shows the second half is needed by any
   implementation (the adapter's `LevelSnapshot` and `TestWorld` both hold a cell size). The option set
   names `sectionOf` only.
3. **The nested view's grain and name** (C-14): a child frame has no 16³ section grain, and two types
   are named `NestedView` (`occupant.NestedLevelSnapshot` vs `predicate.NestedView`). `§8.1` decides the
   *grain* ("按代码的 `NestedLevelSnapshot`") but not the **name** and not what a child answers for
   `slot`/`contentDigest`. R4's options say nothing about the child, although `06` §4.5 ("A nested view
   **is** a full `WorldView`") is the one statement that must be rewritten either way.

---

## R5 — `OccupantRef`'s identity: the chain, and `SnapshotId`'s home

**Question.** Does the code take `SnapshotId` onto `OccupantRef` (per `19` R8) and drop the ref's
`footprint` — and, if so, where does `SnapshotId` live, given that it is currently an `engine` type and
`WorldView` is an `occupant` type?

**Why it must be decided now.** `OccupantRef` is the subject type of every check result, every binding,
`SC-CLM-0005`'s capture validation and `22` §5.1's identity rule. Moving `SnapshotId` is a package move
that touches `SnapshotLease`, and `SC-CLM-0005` is unimplementable until the ref carries a capture stamp
(E §4.2). It also forces the `occupant`-side edit R4 needs (`snapshotId()`).

**Facts.**

- Doc — `06-occupants.md:40-42`:
  > `record OccupantRef(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)`
  > `    implements Comparable<OccupantRef>          // order: snapshot, y, z, x, kind.id, type`
  > `    boolean from(SnapshotId id)                 // snapshot.equals(id)`
- Doc — `06-occupants.md:247` (per C-1's anchor): `ref.from(view.snapshotId())` is false.
- Doc — `19-open-proposals.md:22` (R8):
  > `| R8 | `OccupantRef` includes its `SnapshotId`; `OccupantSnapshot` has no payload; `SnapshotPolicy` carries `requiredTraits`; `Traits` is the reserved well-known-key holder. A cell covered by an admitted occupant is `READABLE` even if its own read was denied. | `01`, `06`, `16` |`
- Doc — `22-consolidated-design.md:121-122` (§5.1):
  > `- `OccupantRef(snapshot, type, kind, anchor)`: the `SnapshotId` it was minted under is part of the identity; the footprint belongs to the `OccupantSnapshot`, so `Footprint.Bound` resolves from the capture already in hand.`
- Code — `occupant/OccupantRef.java:13-14`:
  ```java
  public record OccupantRef(InstanceId instance, NamespacedId type, OccupantKind kind,
                            LatticePoint anchor, Cuboid footprint) {
  ```
- Code — `occupant/OccupantSnapshot.java:15` also carries `Cuboid footprint`, and `:43`
  `new OccupantRef(instance, AIR, OccupantKind.BLOCK, cell, Cuboid.of(cell))` is the air mint.
- Code — `engine/SnapshotId.java:8`: `public record SnapshotId(NamespacedId world, long revision) implements Comparable<SnapshotId>`.
- Verified myself: `SnapshotId` is referenced by exactly two files — `engine/SnapshotId.java` and
  `engine/SnapshotLease.java` (`grep -rln SnapshotId src/kernel/java`). `SnapshotLease` is `engine` and
  already imports `occupant.WorldView`; `engine` imports `occupant`, so `occupant → engine` would be a
  package cycle. **Therefore `WorldView.snapshotId()` cannot be declared while `SnapshotId` stays in
  `engine`** — the move is not optional, it is a precondition of R4's `snapshotId()`. `InstanceId`
  (the component that replaces `SnapshotId` today) lives in `base` (`base/InstanceId.java:3`).

**Options.**

The consolidated (R5) offers **no option set** — it says "确认 `SnapshotId` 补进代码、删 `footprint`、视图加
`snapshotId()`" and then names "`base` 或 `space`" in prose. The writable choices are:

- **`SnapshotId` → `base`.** → Smallest diff: it is `(NamespacedId, long)` and `base` is the id home;
  `InstanceId` sits beside it, so `OccupantRef`'s old and new identity components are declared together;
  `SnapshotLease.id()` and every `MatchRequest`/`Ticket` reference keep compiling. `base` also must not
  gain an `occupant` edge — it does not.
- **`SnapshotId` → `space`.** → Defensible (it names a coordinate frame's revision) but puts a
  capture-lifetime value in the geometry unit; `05` §1/§3 gain a row that has nothing to do with
  lattices, and `space` currently imports only `base` (B §5), so the edge is fine but the ownership is
  wrong.
- **`SnapshotId` → `occupant`.** → Puts identity next to the view that returns it, no cycle
  (`occupant → base` only). Cost: `SnapshotLease` (engine) must import `occupant.SnapshotId`, which it
  already does for `WorldView`; `SnapshotId` stops being usable by a unit that wants a capture stamp but
  no occupants (none today).
- **`footprint`: delete from the ref** (the doc's rule). → Ripple the reports list: `OccupantSnapshot.air`,
  `subjectsOf`, `BayBinding`, `SectionSnapshot`'s index and every comparator that ordered on the ref.
  Also drops C §4 item 4's invariant ("`footprint.equals(ref.footprint())` is never checked, so one
  snapshot can carry two different footprints") without replacing it.
- **`footprint`: keep on both and state the invariant.** → One `Objects.requireNonNull` + one equality
  check in `OccupantSnapshot`'s canonical constructor; the ref keeps a redundant component but readers
  that only hold a ref keep working. C gives this as the other branch and the consolidated took the
  first without saying so.

**Missing option?** **Yes, and it is the one that decides the sheet.**

1. **The `SnapshotId` home** is a three-way choice (`base` / `space` / `occupant`) with different diffs;
   the published text says "`base` 或 `space`" without consequences and omits `occupant`, which is the
   only home that leaves `occupant` self-contained.
2. **The `footprint` branch** (delete vs keep-with-invariant) is a second question hidden inside a
   "confirmation"; C §4 item 4 states both branches and D-6 picks one silently (K3.10).
3. **`OccupantRef`'s missing `Comparable`** (C-9: `06` §3.1/§7.6 require one canonical order; the code
   has two local, disagreeing comparators) is a third edit in the same constructor window and appears in
   no option.
4. **The air-minting rule** (K D-6 (iii)): `OccupantSnapshot.air` mints a ref from an `InstanceId` today;
   once the ref carries a capture stamp, `air` needs the `SnapshotId` — i.e. every view that materialises
   air must hold it. No option states this.

---

## R6 — which capture SPI is the one to regenerate from

**Question.** `06` §3.4, `16` §47/§397, `20` §4.2 and `22` §5.2 describe the capture signature four
different ways; which one is normative, and what carries the issues (a sink, or a value in the result)?

**Why it must be decided now.** Nothing of the capture SPI exists in code (`grep` empty), so the SPIs are
pure spec; the adapter (`16`, the largest absent unit) cannot be written from four texts, and the lease
that `MatchRequest` needs is produced by this call. R6 also decides `IssueSink`'s vocabulary row and
whether the resolver is two members or four.

**Facts.**

- Doc — `06-occupants.md:127-145`:
  ```java
  interface WorldCapture
      CaptureResult capture(CaptureRequest request)   // Captured | TooLarge | Unavailable
      long latestRevision()                           // one WorldCapture serves exactly one world
  …
  sealed interface CaptureResult
      record Captured(WorldView view) implements CaptureResult
      record TooLarge(Cuboid bounds, int elements) implements CaptureResult
      record Unavailable(ReadStatus status, FindingCode code) implements CaptureResult
  interface OccupantResolver<R>
      Set<OccupantKind> kinds()                   // dispatch: families this resolver claims
      @Nullable OccupantSnapshot resolve(R raw, ResolveContext context)      // null = decline
  ```
- Doc — `16-adapter.md:47`:
  > `public CaptureResult capture(CaptureRequest req, IssueSink issues);   // lease + issues, never null`
- Doc — `16-adapter.md:411` (the body): `return new CaptureResult(snapshot, leaseOf(snapshot, options.pinBudget()), issues.list());`
- Doc — `20-execution-and-sections.md` §4.2, first code block:
  > `capture(req, issues) -> Captured(LevelSnapshot, CaptureIssues) | TooLarge | Unavailable`
- Doc — `22-consolidated-design.md:129-133` (§5.2): `- `CaptureResult(snapshot, CaptureIssues)`; a missing section is `Refused(NOT_CAPTURED)`.`
- Doc — `01-glossary.md:323`: `| Capture request / limits | `CaptureRequest`, `CaptureLimits` | Bounds, policy, grid, tracked types/cells, caps (cells, sections, bytes, entities, payloads). | — |`
- Code — **nothing**: `grep -rn "CaptureResult\|CaptureRequest\|CaptureLimits\|IssueSink" src/` returns no
  hits in any source set.
- Verified myself: `IssueSink` appears in the spec only in `16` (four sites), `21` (two) and `22` (two);
  it has **no** `01` glossary row and no vocabulary row in `06` §2 — so whichever option wins, the name
  needs a row or replacement. `CaptureLimits` appears only in `01:323` and `06:131-132`; **`20` §4.2
  does not contain it** (L's citation correction holds — I re-read §4.2 and the caps are inline
  `section/cap/byte limit exceeded`), so C-15's "`20` §4.2 replaces the caps with `CaptureLimits`" is
  inaccurate.

**Options.**

- **(a) `06` §3.4's three-arm `CaptureResult` wins.** → `Captured(WorldView)` has **no lease**, so either
  the caller fabricates a `SnapshotLease` (contradicting `20` §4.1's "one owner: `InstanceRecord`") or
  the arm gains one (the consolidated's own footnote). Every refusal becomes a `ReadStatus`+code: a
  top-level "TooLarge"/"Unavailable" is expressible, but a *per-section* loss has nowhere to go except
  `SectionSlot.Refused` — which is where the code already puts it. `16`/`20`/`22` rewritten; `16`'s
  `SC-MC-05xx` reporting loses its channel (`IssueSink` deleted from the winning signature while `01`
  was supposed to gain a row — L's reconciliation point).
- **(b) `16`'s lease+issues form wins.** → `06` §3.4 becomes a cross-reference; `IssueSink` becomes real
  API (needs a row: what it accepts, whether it can refuse, whether it is `FindingSink`'s alias — I-12
  notes `16` §2 lists `FindingOutput` = `FindingSink` instead); `OccupantResolver` gains `extent` /
  `resolveNested` (four members); the lease's producer and owner must be stated (`20` §4.1 says
  `InstanceRecord`); `CaptureLimits` needs one home (`01`, since `20` has no such name).
- **(c) `22` §5.2's immutable `CaptureResult(snapshot, CaptureIssues)`.** *Not in the published set.* →
  No callback, so a loss is only reported after the whole capture returns (fine for a synchronous
  server-thread call, which `20` §4.2 says this is) and `CaptureIssues` can be a value with the same
  ordering guarantees as `FindingBundle`; but there is no streaming/diagnostic channel during a long
  capture, and A1's `SC-MC-0302` ("a required trait is missing") is only reported at the end.

**Missing option?** **Yes.**

1. **`22` §5.2's issues-as-value form** (option (c) above) is a third shape, and it is the only one that
   keeps `WorldView`/`SnapshotLease` out of the capture's own signature. The consolidated's (a)/(b) are
   "`06` wins" / "`16` wins" and never mention that `20` and `22` already agree with each other against
   both.
2. **The issues carrier** — `IssueSink` (callback, mutable, no ordering rule) vs `CaptureIssues`
   (immutable value) — is a decision inside (b) that no option states; it is also the reason `01` is
   missing a row.
3. **`requiredTraits`' carrier.** `19` R8/A3 put the demand list on `SnapshotPolicy`, and `06` §3.4 has
   one, but `20` retires `SnapshotPolicy` for `MatchPolicy`/`SchedulerOptions` (C-15), and the demand
   side in code is `ExpectationMeta.requires: Set<ContextKey<?>>` (D10). Whichever capture signature
   wins, the trait demand has no home; C-15's own recommendation says so ("either the trait demand is
   dropped or `ExpectationMeta` gains a `Set<TraitKey<?>>` back") and the option set omits it.
4. **`OccupantResolver`'s member count** (two vs four) is a separate binary: `extent`/`resolveNested`
   are what makes nested capture possible, and `06`'s two-member form has no way to express a
   block-entity's interior.

---

## R7 — the facade and its ports (four questions)

> The consolidated says "分开答" and then gives one recommendation per leg. Each leg is a separate
> decision with its own unwritable option, so each gets its own sheet.

### R7.1 — the second port: `Host` or the ledger

**Question.** Does `create` take a `Host` record (documents + segments + ledger), a bare
`@Nullable ClaimLedger` (code), or both?

**Why it must be decided now.** Six of the nine facade operations (`document`, `compile`, `siting`,
`solve`, `explain`, and `settle`'s writer) need a port the current signature cannot carry. Every one of
the 162 tests builds a kernel through the ledger-only form, so the choice fixes the test helper and
`15` §3/§4.4 at once.

**Facts.**

- Doc — `15-spi.md:96-98`:
  ```java
  static StructureKernel create(Catalogs catalogs);               // no documents, no ledger
  static StructureKernel create(Catalogs catalogs, Host host);
  ```
- Doc — `15-spi.md:114`:
  > `record Host(DocumentSource documents, SegmentLibrary segments, @Nullable ClaimLedger ledger) {  public static Host empty(); }`
- Code — `spi/StructureKernel.java:53`, `:57`, `:61`:
  ```java
  public static StructureKernel create(Catalogs catalogs) { return new StructureKernel(catalogs, null); }
  public static StructureKernel create(Catalogs catalogs, @Nullable ClaimLedger ledger) { … }
  public Catalogs catalogs() { return catalogs; }
  ```
- Verified myself: no `Host`, `DocumentSource` or `SegmentLibrary` type exists in `src/`
  (`grep -rn "class Host\|interface Host\|record Host" src/` → empty). `catalogs()` is a public member
  that is **not** one of the nine operations (I-01/I-02).

**Options.**

- **(a) `Host` only.** → `15` §3 stands; the 162 tests' `StructureKernel.create(Catalogs.empty(), ledger)`
  helper changes to `Host.empty()`-with-a-ledger (a second factory on `Host`), and a caller with no
  documents must name a three-field record. One type to define, one test helper to touch.
- **(b) Ledger overload only.** → Today's code and tests hold, but `document`/`compile` have no port, so
  `15` §4.4's first two rows become unimplementable and `02` §3's flow has no carrier; U6 loses its entry
  point entirely.
- **(c) Both, `Host` canonical + a documented `create(Catalogs, ClaimLedger)` convenience** (I's
  recommendation, which the consolidated's wording "`Host` 唯一加账本重载" also implies). → Both
  call paths exist; `Host` gains glossary row (`01`), and `catalogs()` still needs a home (in or out of
  the operation table).

**Missing option?** **Yes, two small ones.** (i) The consolidated's *enumeration* is two options ("`Host`
唯一 + 重载，还是只有账本形态") while its recommendation is the third — a hand-off reader sees a binary
and must invent the middle. (ii) `catalogs()`: keep it (then the nine-operation table is wrong and it is
a tenth) or delete it (then `derive`'s stated "resolve from `catalogs.derivers()`" has no accessor) —
no option covers the accessor, and I-08's `Catalog` interface-vs-final-class decision rides on it
(a `Host`/facade that names `Catalog` values freezes them).

### R7.2 — `derive`'s contract

**Question.** Is `derive` the doc's `(Formed, List<DerivationSpec>, Budget)` or the code's
`(Formed, ContextStore, List<Deriver<?>>)` — and what does it do when it cannot publish?

**Why it must be decided now.** The signature decides whether the facade hands out a mutable store (P2/P5,
`17:257`) and whether a derivation read is budgeted at all; the failure mode does not exist anywhere in
the doc, and it is a `throw` the invariants forbid.

**Facts.**

- Doc — `15-spi.md:104`: `StructureContext derive(MatchResult.Formed formed, List<DerivationSpec> derivations, Budget budget);`
- Doc — `15` §4.4, `derive` row: `the formed instance's frames, `Host.ledger()` (read-only, nullable), `catalogs.computations()`/`contextKeys()`/`derivers()``
- Code — `spi/StructureKernel.java:82`:
  ```java
  public StructureContext derive(MatchResult.Formed formed, ContextStore store, List<Deriver<?>> derivers) {
  ```
- Code — `spi/StructureKernel.java:101-118` (`publishTier`): two attempts, then
  `throw new IllegalStateException("derive at " + tier + " could not publish: " + failure);`
- Verified myself: the constructor's `catalogs` field is read by **no** member except `catalogs()` — so
  the code's `derive` resolves nothing from the catalog the kernel was built with; the caller supplies
  both the store and the derivers.

**Options.**

- **(a) Doc's form.** → `derive` resolves `Deriver`s from `catalogs.derivers()` and takes a `Budget`; the
  caller cannot publish into an arbitrary store. Cost: `Budget`'s two legs (`cells`, `sections`: G §5,
  `22` §9.5) have no derivation leg, so either a third leg or a charge rule is added; U6's spec compiler
  must exist to turn `DerivationSpec` into a `Deriver`; `ContextStore` must be reachable from the
  `Formed` for the publish step (option (c)'s premise).
- **(b) Code's form.** → No catalog lookup, no budget; the caller owns the publish target, which
  contradicts P2/P5 and `17:257` ("`StructureContext.at(instance)` reads published frames only"), and
  makes "a kernel is a value" false for this one member.
- **(c) Middle** (I's recommendation; the consolidated also recommends it): doc's signature, store taken
  from the instance the `Formed` names, `List<Deriver<?>>` only as an internal seam. → Needs an instance
  registry (`InstanceRecord`?) to own the store, and `22` §9.5's two legs stay as they are.

**Missing option?** **Yes.** The **failure contract** is in no option: two stale-base attempts then a
`throw` (`StructureKernel.java:117`) against `15` §3's "**Never throw**: only `NamespacedId.of` and the
two `build()`s throw" and `00` I-14. A regenerated `derive` from any of (a)/(b)/(c) has no stated
behaviour on a moved base — it could return a stale context, an `Outcome.Missing`, or throw, and `18`
C-18's test cannot be written until one is picked. (I-10 filed this as a DOC defect with no decision;
K3.9 notes D-8 drops it.)

### R7.3 — `ContextKeys.register`'s direction

**Question.** Does `ContextKeys.register` live in `kernel.context` (taking a draft interface) or on
`Catalogs.Builder` in `kernel.spi` as `15` §3 writes it?

**Why it must be decided now.** `15`'s form makes `kernel.context` import `kernel.spi`, which `02` §2's
graph forbids — a facade regenerated from `15` §3 does not compile. The refusal phase and exception type
(the same event: a duplicate id) are stated three different ways in the spec and both ways in the code.

**Facts.**

- Doc — `15-spi.md:88-93`:
  ```java
  public static <T> ContextKey<T> register(Catalogs.Builder draft, NamespacedId id, Class<T> type,
                                           Retention retention, @Nullable Merge<T> merge,
                                           @Nullable T whenAbsent);
  …
  // A repeated id is refused by draft.build() as an IAE whose message names SC-CTX-0102 (§4.8).
  ```
- Code — `context/ContextKeys.java:13-27`: `of(NamespacedId, Class<T>, Retention, @Nullable Merge<T>, @Nullable T)` and
  `public static <T> ContextKey<T> register(CatalogsDraft draft, ContextKey<T> key)`.
- Code — `context/CatalogsDraft.java:10-19` owns the draft interface; `spi/Catalogs.java:70`
  `public static final class Builder implements CatalogsDraft`, and `:101-105`:
  ```java
  throw new IllegalStateException("SC-CTX-0102: context key " + key.id() + " is already declared");
  ```
  i.e. **at registration**, not at `build()`.
- Doc that agrees with the code — `13-findings.md:363`: `| `SC-CTX-0102` | ERROR | – | two registrations of one id; the second is refused at registration |`; and `15-spi.md:311-313` says "Every refusal here is an `IllegalArgumentException`, not a finding" while the code throws `IllegalStateException` for this family and `IllegalArgumentException` for the other four (I-07).

**Options.**

- **(a) Keep it in `context`, add `spi → context` to `02` §2.** → One edge drawn; `Catalogs.Builder`
  implements `context.CatalogsDraft`; `15` §3 rewritten to the two-argument form; `15` §8's own
  dependency row and `22` §6.4 already show the code's shape, so two files change instead of a package.
- **(b) Move it to `Catalogs.Builder` (doc's shape).** → `context` gains an `spi` edge or `ContextKeys`
  moves to `spi` (and then `01` §E's U8 ownership is wrong); `02` §2's ranking has `spi` above every
  package, so `context → spi` is a cycle by construction.
- **Both need a second fix the options omit**: the refusal is `IllegalStateException` for `contextKey`
  and `IllegalArgumentException` for the other four families at `put` (I-07), and `15` §4.8 says "Every
  refusal here is an `IllegalArgumentException`". The doc must state one phase and one type.

**Missing option?** **Yes.** (i) The **refusal phase/type** (`build()` IAE vs registration ISE) is a
decision inside R7.3 and appears in no option; `13:363` and `15:93` contradict each other on it today.
(ii) A third shape — move `ContextKeys` to `spi` and let it own both factories and the draft interface —
is not considered, and it is the only one that makes `15` §3's six-argument form compile without a
`spi → context` edge.

### R7.4 — the five operations with no code

**Question.** Do `document`, `compile`, `siting`, `solve` and `explain` (plus `explainOrFallback`) enter
the migration list, or leave the facade?

**Why it must be decided now.** It is an inventory fact that decides the size of the regeneration: five of
nine operations have no type, no test, and no R-record. Whichever way it goes, `15` §4.4, `02` §3's flow,
`17`, `18` C-rows and the U6/U10 scoping in R13 all move.

**Facts.**

- Doc — `15-spi.md`, §4.4's table rows: `document`, `compile`, `siting`, `check`, `settle`, `derive`,
  `solve`, `explain`, `explainOrFallback`, `ledger` — plus `15:12`: "a kernel is `(Catalogs, Host)` plus nine operations".
- Code — `spi/StructureKernel.java`: the public members are `create`×2, `catalogs()`, `ledger()`,
  `check(MatchRequest)`, `derive(Formed, ContextStore, List<Deriver<?>>)`. `settle` has **no member**;
  it runs inline inside `check` when `request.stage() == Stage.SETTLE` (`:73-80`, `MatchSettler.inline`).
- Verified myself: `grep -rn "BlueprintDocument\|CompileResult\|Remedy\|Explanation" src/` → no hits.
  So the count is **three of nine** (`check`, `derive`, `ledger`), plus an undocumented `catalogs()` —
  L's correction, not I's "four of nine".

**Options.**

- **(a) Migration list for all five.** → `23` §9 gains five items; U6 and U10 are in-scope work (R13
  cannot then say "exclude"); `16` must implement `document`/`compile`; `17` §1's runner and `18`'s
  document rows keep their subject; the regeneration is much larger.
- **(b) Delete them from the facade.** → `15` §4.4 shrinks to the four operations the code has (+
  `settle`, now a real member per R27); `02` §3's flow, `17`, `18` C-12/C-13/C-16/C-18/C-20/C-25 lose
  their entry point; U6/U10 must be reached some other way or be dropped.

**Missing option?** **Yes.** (i) **Re-home** rather than delete: `document`/`compile` on `Host` /
`SegmentLibrary`, `siting` as a U5 function, `solve` on a remedy service, `explain` on the `ExplainBook`
(`13` §3) — the capability stays, the facade shrinks. Neither published option allows this, and it is the
only one that keeps U6/U10 in the spec while satisfying "the facade is nine operations the code can
hold". (ii) `catalogs()`: in or out of the table (see R7.1). (iii) `settle` as a member: the doc and R27
say yes, the code has it inline; this leg is *not* one of the five, and no option says when it becomes a
member.

---

## R8 — `LocusMap`

**Question.** Does `LocusMap` exist (and if so, does a `DEFINITION` locus carry the authored cells), or is
frame conversion deleted?

**Why it must be decided now.** `LocusMap` is the only carrier of LOCAL↔WORLD conversion for *sets*, and
`SC-SPC-0001` is defined over its failure. `05`, `01`, `06`, `13` all publish the name; nothing in `src/`
implements it. The `DEFINITION`-cells half is the same contract as `Locus`'s shape (`Locus.java:29-31`)
and `13` §4.2's span iff, so a regeneration that picks wrong builds an unconstructible `Locus`.

**Facts.**

- Doc — `05-space.md:39`: `| Locus map | `LocusMap` | Frame conversion for one `Siting` |`
- Doc — `05-space.md:59`:
  > `| `LocusMap` | `record (Siting)`; `static of(Siting)`, `@Nullable toWorld(Locus)`, `@Nullable toLocal(Locus)`, `toDefinition(Locus)` |`
- Doc — `05-space.md:212` (L-1): `- **L-1** `DEFINITION` implies `source != null` **and carries the authored cells**; `KEY` implies …`
- Doc — `05-space.md:264`: `toWorld` maps `LOCAL → WORLD` and fixes `WORLD`; `toLocal` the reverse; both preserve `source`
  and answer `null` if the result would leave the lattice.
- Doc — `13-findings.md:322`: `| `SC-SPC-0001` | ERROR | ✓ | a `Locus` cannot be mapped into the requested frame for this siting |`
- Code — `grep -rn LocusMap src/` → **zero hits**.
- Code — `base/Locus.java:29-31`:
  ```java
  public static Locus definition(SourceSpan source) {
      return new Locus(LocusFrame.DEFINITION, CuboidSet.empty(), null, source);
  }
  ```
  and `:20-22` `if (frame == LocusFrame.DEFINITION && source == null) throw new IllegalArgumentException("a DEFINITION locus needs a source span");`
- Verified myself: `Locus` has **four** factories that take cells (`local`, `local(cells, source)`,
  `world`, `world(cells, source)`) and no `definition(cells, source)`; so L-1's "carries the authored
  cells" is unimplementable without a new factory, and `05:59`'s `toDefinition(Locus)` is declared
  non-null while B-2(A) needs it to answer `null` for a null source.

**Options.**

- **(a) Build `LocusMap`, `DEFINITION` carries the authored cells (L-1).** → New factory
  `definition(CuboidSet, SourceSpan)`; `toDefinition` re-derives cells by inverse-mapping (needs the
  source's own LOCAL locus, which nothing holds) **or** is only total for loca that already are
  `DEFINITION`; `05:59` gains `@Nullable`; `13` §4.2/§4.4/F9 and H-R5(A) must be reconciled (an iff
  "DEFINITION ⇔ source != null" plus "carries authored cells" is a stronger constructor guard than the
  code has).
- **(b) Delete `LocusMap`.** → `05` §1/§3/§4.7/§5/§6, `01`, `06`, `13` lose the name; `SC-SPC-0001` loses
  its only producer (so the code is a dead row in R12's table); no `--explain` or renderer can convert a
  LOCAL locus to WORLD for display. Cheapest text, largest capability loss.
- **(c) Keep the frames, move conversion onto `Siting`/`Placement`** (R-B1(C), *not in the published
  set*). → `Siting.toWorld(CuboidSet)` / `@Nullable`, `Placement.apply(CuboidSet)`; `SC-SPC-0001`'s
  condition rewrites to "the siting maps the cells outside the lattice"; `LocusMap` disappears as a type
  but the capability survives; `Orientation.apply(CuboidSet)` already exists (`05:55`, code
  `Orientation.java:107-117` per B §5), so this is the smallest real implementation.

**Missing option?** **Yes.**

1. **R-B1(C)** (conversion on `Siting`) is absent; the set is "build the type" / "delete the feature".
2. **The `DEFINITION`-cells binary** is named in prose ("并定 … 是否携带作者格子") but not offered as an
   option with consequences: empty (B-2(A), matches `Locus.definition`) vs authored cells (L-1, needs a
   factory and a re-derivation source). L-1 and `05` §4.7's own `toDefinition` cannot both survive, and
   the choice also decides whether `13` §4.2's iff becomes a constructor guard.
3. **`toDefinition`'s nullness** (`05:59` non-null vs B-2(A) `null` for a null source) is a one-line
   contract fix that belongs to whichever option wins.

---

## R9 — `Placement.inverse` and the §4.6 laws

**Question.** Does §4.6's inverse law become conditional (`inverse` is `@Nullable`), does `inverse`
return an `Outcome`/`Optional`, or is the domain restricted so the law holds?

**Why it must be decided now.** §4.6's laws are quoted as identities and `Siting.toLocal` is built on
them; `05` §3 annotates only `apply`, so a regenerated `Placement` is non-nullable and every call site
forgets a check. The same edit must include B-8 (`Cuboid.of(LatticePoint)`'s throw and the real coverage
range) or the two documents disagree about what a legal placement is.

**Facts.**

- Doc — `05-space.md:56`:
  > `| `Placement` | `record (Orientation, LatticePoint offset)`; `IDENTITY`, `compose`, `inverse`, `@Nullable apply(...)` |`
- Doc — `05-space.md:190`:
  > `| Inverse | `P.inverse() = (o⁻¹, −o⁻¹(t))`; `P.inverse().compose(P) == IDENTITY` |`
- Code — `space/Placement.java:15` and `:27`:
  ```java
  public @Nullable Placement compose(Placement other) {
  public @Nullable Placement inverse() {
  ```
  with `:40-46` `negate` returning `null` when `!LatticePoint.inLattice(x, y, z)`.
- Code — `space/LatticePoint.java:7-8`: `MIN_XZ = -33_554_432`, `MAX_XZ = 33_554_431`.
- Verified myself: `-MIN_XZ = 33_554_432 > MAX_XZ`, so a **legal** offset has no legal negation and
  `inverse()` is `null` — the probe in B-3 reports exactly this. `compose` also overflows for two legal
  offsets (`MAX_XZ + MAX_XZ`). So `inverse` total ⇔ offsets restricted to `[-MAX_XZ, MAX_XZ]` (a
  half-range), which also makes `compose` total.

**Options.**

- **(b) Qualify the laws** ("holds wherever the terms are non-null") and annotate `compose`/`inverse` in
  §3. → Doc-only; every call site keeps a null check (`Siting.toWorld`/`toLocal`, `anchoring`), and §4.6's
  Re-siting law must be qualified too.
- **(c) `inverse` returns `Outcome<Placement>` / `Optional<Placement>`.** → Forces handling at compile
  time; changes `Siting`, `anchoring`, `LocusMap`'s implementation, `SitingTest`; `Outcome` is `base` and
  `Placement` is `space`, so `space → base` (fine) but the type is heavier than the fact.
- **(a′) The corrected (a) — restrict the domain to placements whose negation is representable**
  (*the published (a) says "only placements that leave the origin in range", which L refuted*). →
  A constructor guard (`negate(offset) != null`) makes `inverse` total and `inverse().compose(P) ==
  IDENTITY` true for every constructible `Placement`; `compose` still needs a guard, so either offsets
  are restricted to the half-range (then both are total) or `compose` stays `@Nullable`. Cost: a
  `Placement` with `offset.x() == MIN_XZ` becomes unconstructible although the point is legal — which is
  the same asymmetry B-8 finds in `Cuboid.of(LatticePoint)`.

**Missing option?** **Yes.**

1. The **corrected (a)** is not in the set: the published (a) is refuted ("leave the origin in range" is
   not the condition), and the surviving binary (b)/(c) omits the only option that restores the law
   rather than qualifying it.
2. **B-8** (`Cuboid.of(LatticePoint)` throws for the top cell of every axis; the coverage range is
   `[MIN_XZ, MAX_XZ)`, `[-2_048, 2_047)` on `y`) is not in R9's option set, and K §6 says D-10 "must be
   ruled with B-8". A `Placement` domain restriction and a `Cuboid` range rule are the same decision
   seen twice.
3. **`compose`'s nullability** is not addressed by (b) as worded: §4.6's Composition row is an identity
   too, and it is false for two legal offsets.

---

## R10 — `DecisionKind` vs `Coverage`

**Question.** Are there two enums (a focus *claims* a triad, a bay *demands* one) or one — and if one,
which name survives and where does `Coverage.accepts(ClaimScope)` go?

**Why it must be decided now.** `Bay.validate` compares the two at build time and is the only thing that
catches a `PRESENCE` bay holding a cell-deciding focus. `07` §3/§4.6, `01` §E, `24` §4.1/§9.1 and
`22` §16.1 item 14 all name the same triad, and the code spells it twice with the same three words.

**Facts.**

- Doc — `07-expectations.md:37`: `DecisionKind decisionKind();  // EVERY_CELL | ONE_SUBJECT | ANY_SUBJECT`
- Doc — `07-expectations.md:50`: `public enum DecisionKind { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }`
- Doc — `01-glossary.md:107`:
  > `| Decision kind | `DecisionKind` | `EVERY_CELL`, `ONE_SUBJECT`, `ANY_SUBJECT`: what a focus can assert at DESIGN, validated against the bay's `Coverage`. | — |`
- Doc — `01-glossary.md:116`: `| Coverage | `Coverage` | `EVERY_CELL`, `ONE_SUBJECT`, `ANY_SUBJECT`: what a match on a bay must establish … Legality is rules R1–R4 (`24`). |`
- Doc — `24-claim-vocabulary.md:149` (§9.1 item 1): `1. `Coverage`: rename values, rewrite `accepts` into R1/R4.`
- Doc — `07-expectations.md:231` (the `block` row of §4.6): `` | `block` | `BlockView` | `ONE_SUBJECT` or `EVERY_CELL`, depending on whether its cells cover the bay | ``
- Code — `predicate/DecisionKind.java:5-7`: `CUBIC, SUBJECT, PRESENCE`; `blueprint/Coverage.java:7-9`: the same three words.
- Code — `blueprint/Coverage.java:11-17`:
  ```java
  /** The legal {@code coverage} × {@code scope} rows; any other pair is {@code SC-CHK-0106}. */
  public boolean accepts(ClaimScope scope) {
      return switch (this) {
          case CUBIC -> true;
          case SUBJECT -> !(scope instanceof ClaimScope.Owned);
          case PRESENCE -> scope instanceof ClaimScope.Audit || scope instanceof ClaimScope.Declared;
      };
  }
  ```
- Code — `blueprint/Bay.java:55` `DecisionKind kind = check.focus().decisionKind();` compared with the
  enclosing `coverage`, throwing `SC-CHK-0106` (`:66-70`, `:82-84`) and `SC-BAY-0107` (`:58-60`, `:70-74`).
- Code — `predicate/Focus.java:16`: `DecisionKind decisionKind();` — **no parameters**.

**Options.**

- **(a) One enum named `Coverage`, `24`'s three value names.** → `07` §3's `DecisionKind` row and `01`
  §E's row are deleted (one of the two glossary rows survives), `Focus.decisionKind()` returns the
  survivor or is deleted; `24` §9.1 item 1 must be amended (it assumes two types); `Coverage.accepts`
  must move (L: the merge deletes the coverage×scope legality pair unless it is re-homed), and the
  `block` row of §4.6 must go regardless (a no-arg member cannot be bay-dependent).
- **(b) Two types with a stated mapping.** → Keeps `accepts` and `Bay.validate`'s cross-check; `07` §3,
  `07` §4.6, `01` §E keep two rows and both are renamed from `CUBIC/SUBJECT/PRESENCE` to
  `EVERY_CELL/ONE_SUBJECT/ANY_SUBJECT`; `24` needs no code-column edit. This is D's RULE-D2
  recommendation and L's "otherwise (b)".
- **(c) One enum named `DecisionKind`** (*not published*). → Deletes `Coverage` from `blueprint`; `Bay`
  holds a `DecisionKind`; legality moves to `24`'s R1–R4 predicate set and `accepts` is deleted outright
  (its three rows are R1/R4 restated). `24` §4.1's type name becomes wrong, so it is an amendment either
  way — but it is the *smaller* one (one name survives in `predicate`, which owns the kind).

**Missing option?** **Yes, and the sheet also records a contradiction in the "decided" list.**

1. **The surviving name** (`Coverage` vs `DecisionKind`) is a real choice with different ripples and no
   option states it.
2. **`accepts`' home** — merged into the survivor, kept on `Coverage` only, or deleted in favour of
   R1/R4 — is the consequence L says D-11 must carry, and no option carries it.
3. **Whether `Bay.validate`'s cross-check survives.** K3.1: the consolidated's recommendation is D's
   *rejected* (b) — "would delete the only check that catches a `PRESENCE` bay holding a cell-deciding
   focus". The option set cannot be answered while (a) as worded silently deletes a producer.
4. **Fact/consistency error:** `§8.1` lists "`DecisionKind` 与 `Coverage` — 只留一个枚举" as *decided by
   evidence* citing `RULE-D2`, but RULE-D2's own recommendation is (a) = **two** enums, and L §4 records
   that one enum "is a `24` amendment, not a free option". As published, the decided list and R10's
   option set recommend opposite things; a hand-off reader will implement whichever they read first.

---

## R11 — `Reply`'s shape

**Question.** Is `Reply` the sealed four-arm family with a `combine` fold (and a notes channel on
`MATCH`), or the doc's `record Reply(Verdict, Bindings, FindingBundle)` with a private constructor?

**Why it must be decided now.** `Reply` is the return type of every `Expectation.test` and the attempt
fold's operand (`MatchSession` calls `combine`); `Verdict` exists nowhere else. The shape decides whether
"MISMATCH binds nothing" is structural or a comment, and whether a MATCH can carry `07` §4.1's INFO/WARNING
annotation — which `13` §4.5's merge order then has to honour.

**Facts.**

- Doc — `07-expectations.md:56-62`:
  ```java
  public enum Verdict { MATCH, MISMATCH, ABSTAIN, UNREADABLE }

  /** Four states, never generic: one check answers one Reply, whatever its subject type is. */
  public record Reply(Verdict verdict, Bindings bindings, FindingBundle findings) {  // ctor is private
      public static Reply match();  public static Reply match(Bindings b);
      public static Reply match(Bindings b, FindingBundle notes);    // no ERROR findings
      …
  ```
- Doc — `07-expectations.md:116`: `` | `MATCH` | 0..n | 0..n, none `ERROR` | a witness; INFO/WARNING may annotate it | ``
- Doc — `07-expectations.md:121`: `The package-private `Reply` constructor is the one gate: only `match*` stores bindings.`
- Code — `predicate/Reply.java:9-11`:
  ```java
  public sealed interface Reply permits Reply.Match, Reply.Mismatch, Reply.Unreadable, Reply.Abstain {
      record Match(Bindings bindings) implements Reply {
  ```
  `Mismatch`/`Unreadable` throw on an empty bundle (`:23-25`, `:34-36`); `combine` is at `:72-95`.
- Code — `engine/MatchSession.java:291`: `combined = Reply.combine(combined, reply);` and `:89`
  `private Reply combined = Reply.match();` — **both from a different package**.
- **Verified, correcting L:** `Reply` is an *interface*, so `static Reply combine(…)—`written without a
  modifier is **implicitly `public`** (JLS §9.4), and the cross-package call at
  `MatchSession.java:291` proves it. L §2.1 and §3 item 17 ("`Reply.combine` is package-private, not
  public") are **wrong**; the same holds for `match`/`mismatch`/`unreadable`/`abstain`. What the code
  actually lacks is the **notes parameter**, not public access. The consolidation's R11 ("包私有的
  `combine`") inherits the error.
- Verified myself: `Bindings.merge` mints its conflict finding with a hard-coded stage
  (`Bindings.java:106` `.stage(Stage.SETTLE)`) even when it is called from a `FORM` check — so the sealed
  family inherits a stage lie that `07` §4.4's "one finding naming both loci" also cannot express.

**Options.**

- **(a) Sealed family + a MATCH notes channel + `combine` documented as the attempt fold.** → `Match`
  gains a second component, `combine`'s Match∧Match leg merges notes in `13` §4.5's order, `Verdict` is
  deleted (no second user: `grep Verdict src/` → none), `07` §3/§4.1/§7 and `01` §E are rewritten.
  Because `combine` is already public, "package-private" must not be written into the spec.
- **(b) The doc's record.** → Loses the structural "MISMATCH/UNREADABLE carry ≥1 finding" (it becomes a
  constructor invariant), keeps `Verdict` with one user, and makes `combine` a static factory the engine
  can still call — but `Reply` would then need a public constructor, contradicting `07:121`'s "the one
  gate".

**Missing option?** **Yes.**

1. **How `Match` carries notes** — a second component vs a `Note` bundle on the interface vs a fifth arm
   — is the whole substance of "补回 §4.1 的 MATCH 备注通道" and no option says which.
2. **`combine`'s home and visibility.** It is called from `engine`, so it must stay public (see the fact
   correction); the option as published asks for the opposite. A hand-off that writes "package-private"
   produces code that does not compile.
3. **`Bindings.merge`'s stage** (`Stage.SETTLE` hard-coded) is named as a file bullet in §4 (`07` §3
   `Bindings.merge`) but is not inside R11's option set, although the same constructor edit touches it and
   I-6/`23` §7 forbid the hard-coded stage.

---

## R12 — one code registry, one producer per code, and DESIGN's channel

**Question.** Who owns the `SC-*` registry (one table in `13`, or each family in its own file), is a
DESIGN-time violation an exception or a labelled finding, and does every allocated code have exactly one
producer?

**Why it must be decided now.** `D-4`, `D-7`, `D-11` and R13 all depend on it (K5.2: the decision behind
the dropped `D-13` is in no list). Eight of the fifteen families have **zero** producers in a tree that
compiles and passes 162 tests, and two spec-reassigned codes are still emitted under their old family —
so a regeneration from `13` produces dead rows, and one from the unit files produces unregistered codes.

**Facts.**

- Doc — `13-findings.md:290-294` (§4.3):
  > `Format: `SC-` `<FAMILY>` `-` `<NNNN>`, family a closed 15-case enum, number `1..9999` zero-padded. `FindingCode.parse` accepts exactly that grammar (plus the numeric range) and returns `null` otherwise`
- Doc — `13-findings.md:296-311` is the family table ("`| Family | Subject | Raiser |`"), so `13` claims the registry.
- Doc — `10-documents.md:296`: `severity comes from the code's registry entry (U9 owns the table), and the column below is the proposal U9 adopts.` — `10` proposes `0101–0606` while claiming `13` owns the table.
- Doc — `14-remedies.md` §8: `Proposed `SC-RMD` codes for U9 to accept or renumber: plan `0201`–`0205` … dry run `0301` … apply `0401`–`0404`; gate `0601`.` — and `13` registers exactly two RMD rows (`:374-375`).
- Doc — `16-adapter.md` §8's table (per I-14) assigns `0401`/`0307` to different conditions than §4.7's gate row; `21` uses `SC-DOC-0001/0002/0503/0504/0505/0605/0606`.
- Doc — `15-spi.md:310-313`:
  > `Every refusal here is an `IllegalArgumentException`, not a finding: the snapshot is built before any attempt exists, so there is no stage or locus to attach one to (failing fast beats a silent winner).`
- Code — 60 code rows in `13` `| `SC-`…`; the tree's mints (`grep -rn "FindingCode.of(" src/kernel/java`):
  `FOC 101/201/301/302`; `EXP 1/2/3/4`; `CTX 107/108/109/202/301`; `ENG 1/3/4/6/102/103/201/401/402`;
  `MC 404`; `BAY 107`; `CLM 1/2`. **No mint** of any `SPC`, `OCC`, `BPT`, `DOC`, `DIA`, `RMD`, `SPI`,
  `CHK` code, nor of `BAY 0108–0111`, `CLM 0003–0007/0104`, `ENG 0002/0004/0005/0104`.
- Code — the `Bay.java` "codes" are exception *text*, not codes: `throw new IllegalArgumentException(
  "SC-BAY-0107: " + check.id() + " decides cells outside bay " + id)` (`:56-59`), `"SC-CHK-0106: PRESENCE bay …"` (`:66-69`), `"SC-CHK-0107: …"` (`:46-49`).
- Code — **two producers for one number, two meanings**: `MatchSettler.java:31` `LEDGER_MOVED = FindingCode.of(FindingCode.Family.ENG, 3)` and `MatchSession.java:62` `SNAPSHOT_CHANGED = FindingCode.of(FindingCode.Family.ENG, 3)`.
- **Verified, correcting the report:** the consolidated's R12 says `SC-CTX-0202` "没有" a producer; it
  has three — `ContextView.java:37` (`KEY_ABSENT`), `MatchSession.java:71` (same constant) and
  `StructureKernel.java:190` (a fresh `FindingCode.of(CTX, 202)`). The dead-code list is
  `SPC`/`OCC`/`BPT`/`DOC`/`DIA`/`RMD`/`SPI`/`CHK` plus the BAY/CLM/ENG numbers above, not `CTX-0202`.

**Options.**

- **Registry owner: `13` unique (recommended) vs per-family tables.**
  → `13` unique means `10` §4.4, `14` §8, `16` §8, `21` §3/§4, `06` §4.1 lose their tables and become
  cross-references (five files edited, one place to look up a code). Per-family means `13` becomes an
  index and each unit file is authoritative for its numbers — then the three disjoint DOC allocations
  (`13:0001-0003` / `10:0101-0606` / `21:0001-0606`) are three authorities again.
- **DESIGN channel: all exceptions (recommended) vs all diagnostics.**
  → All exceptions means `13`'s `P ✓` rows for `CHK`/`BAY`/`SPI`/`DOC` are unreachable as findings and
  those families' rows must state "exception only" (then `SC-CHK-0106`'s "P ✓" is false); all findings
  means `build()` must return a bundle instead of throwing, contradicting `04` §5 and I-14's
  "constructors throw for a malformed value", and `Bay.validate`/`Requirement`'s constructor must change
  shape.
- **One producer per code.** → Needs a documented producer table (family → the one member that may mint);
  today `ENG-0003` has two meanings, `CTX-0202` has three producers, and `CTX-0109` has two channels
  (draft `IllegalStateException` + `MatchSession`'s label-less `failed(WRONG_VERB)` — H7). Pick one
  producer per code and the draft's exception loses its code text (H-R3(A)).

**Missing option?** **Yes.**

1. **Family ownership of a *condition*** is a separate decision bundled in: R3-kind vs R4-completeness
   (`24` §5 says `BAY-0107` for R3 and `CHK-0106` for R4; the code splits the opposite way, E D6/RULE-4)
   and "a check decides cells outside its bay" has **no row at all** (F8). Neither the registry-owner nor
   the DESIGN-channel option answers which family owns which condition.
2. **Which dead codes are deleted vs given producers** is not in the set: `SC-OCC-01xx/02xx` (`06` §4.1
   reserves seven, `13` publishes two, none raised), `SC-ENG-0104` (anchor absent — its carrier
   `PrescreenIndex`/`AnchorCandidate` is deleted by the `§8.1` decision), `SC-DOC-0101–0606`
   (`10`'s proposal, unregistered), `SC-RMD-02xx…0601` (R13's subject). "One producer per code" as
   written implies they are all written; deleting them is the cheaper path and needs a ruling.
3. **The `14` §8 numbering defect** (`0206` used in §4.4 row 5, outside its own `0201–0205` range) is a
   doc edit only if the RMD table survives — it belongs to R13's answer, not R12's, but no option links
   them.

---

## R13 — U10's ownership

**Question.** Does `14-remedies.md` (U10, ~20 types, ~20 codes, no code and no test) get a review slice,
or is it marked "not built, excluded from regeneration"?

**Why it must be decided now.** It is the largest single unimplemented unit in the spec set and the only
one with **no owner at all** (J1–J4). `00` §5's U10 row, `14`'s file entry, `22` §11.1's `SC-RMD` family,
`22` §11.3's `RemedyContext`, `13`'s two RMD rows and `I-11` all assert it exists. A regeneration that
includes it produces ~20 types with no producer; one that excludes it must say so in five places or the
next reader re-opens it.

**Facts.**

- Doc — `00-overview.md:93`: `| U10 | `kernel.remedy` | How do we repair, preview and explain a finding? | `14-remedies.md` |`
- Doc — `14-remedies.md:7-13` (§1 Owns): `Remedy`, `RemedyKind`, `RemedyProvider`, `RemedyContext`,
  `RemedyOptions`; `EditPlan`, `EditStep`, `Edit` + 7 cases; `PlanValidator`, `ValidationReport`,
  `PlanDiff`, `PlanDigest`; `EditExecutor`, `EditTransaction`, `ApplyReport`; `OverlayModel`,
  `CellMark`, `Marker`, `MarkerKind`.
- Doc — `22-consolidated-design.md:609`: `- Families: `SC-DOC`, `SC-BAY`, `SC-CHK`, `SC-EXP`, `SC-FOC`, `SC-CLM`, `SC-CTX`, `SC-MC`, `SC-RMD`.`
- Doc — `22-consolidated-design.md:622` (§11.3) prints `record RemedyContext(Blueprint blueprint, Siting siting, @Nullable ClaimView claims, long ledgerRevision, @Nullable MatchOutcome outcome, @Nullable StructureContext context, RemedyOptions options) {}`.
- Doc — `13-findings.md:374-375` registers exactly two RMD rows (`0001`, `0309`).
- Doc — `00-overview.md:72` (I-11): `Repair, preview and explanation share one `Remedy` substrate and one `Locus`/`Applicability` vocabulary. | U10`
- Code — `grep -rl` over `src/` for `Remedy`, `EditPlan`, `PlanValidator`, `EditExecutor`, `OverlayModel`,
  `EditTransaction`, `PlanDigest` → **0 files each** (verified).
- Verified myself: the test tree has no `remedy` and no `document` package
  (`ls src/kernelTest/java/dev/vfyjxf/cumulus/kernel` → `base blueprint claim context engine examples occupant predicate space`), so U10 has no test seam either.

**Options.**

- **(a) Add a review slice for `14` + `22` §11–§15.** → J1–J4 get verdicts; costs a full pass; and because
  U10 is a *third* consumer of two already-known holes (`TraitKey`, the descriptor property channel:
  C-4/C-5/D10), the slice will largely re-find them. Output is a report, not a decision — the regeneration
  question is still open after it.
- **(b) Mark "not built, excluded from regeneration".** → `00` §5's U10 row, `14`'s file list entry,
  `22` §11.1's family list (drop `SC-RMD`), `13`'s two RMD rows and `01` §L's rows must all say so; `I-11`
  loses its subject; `22` §11.3/§12/§13's proofs ("a plan that changes a `ContextKey` any provider read is
  `SC-RMD-0309`") become evidence-free. Smallest work, largest set of documents to annotate.

**Missing option?** **Yes.**

1. **Treat U10 as in-scope design and regenerate it** — `14` is the spec of record and the kernel gains
   `kernel.remedy`; requires R12 to settle the RMD table first and C-4/C-5/D10 to land. This is what
   "not built" actually contradicts (an unimplemented unit is normal in this tree; `10`'s U6 is the same
   and the `§8.1` decision says *build the package*), and neither option offers it.
2. **Delete U10 from the spec set** — drop `14`, `00` §5's row, `22` §11.1's family and `I-11`. That is
   the only option that leaves no dangling reference, and it is not offered.
3. The published pair only chooses whether to **review** U10; it does not choose whether U10 is **in the
   regeneration**, which is what R13's "why now" is about.

---

## R14 — the receiver-side documents

**Question.** Which document set is authoritative where `guide.zh.md`, `docs/README.md`,
`_index.md`'s gates, `_translation-guide.md` and `19`'s unowned R-records disagree with `docs/spec/` —
and who fixes them before regeneration?

**Why it must be decided now.** `docs/README.md:4` sends every newcomer to `guide.zh.md` first, and that
file states the *pre-R27* facade as present tense (§J21–J30). A regeneration that satisfies `docs/spec`
still leaves the entry document lying, and `19` carries twelve R-records no report has judged (two
contradict the tree). It is also the cheapest place to prevent the next audit from re-finding everything.

**Facts.**

- Doc — `00-overview.md:62` (I-4): `A match reads only an immutable `WorldView` snapshot; `(blueprint, snapshot, siting, policy)` fully determines the result. | U2, U7`
- Doc — `00-overview.md:68` (I-10, same line as the "only `settle` settles" rule):
  > `Every **settled** cell carries a sharing value, and only `settle` settles: a `Formed` is a structure that has already been written. A conflict is a `Finding`, never a silent overwrite. | U4, U7`
- Code — `engine/MatchSession.java:325` `Outcome<Void> published = store.publish(draft);` (on every stage, including `PROBE`/`FORM`) and `:338` `var previous = instanceRecord.reemit(dirty); if (previous != null) { return previous; }` — so both invariants are false as written.
- Doc — `19-open-proposals.md:30` (R16): `Tick slicing is rejected. `StructureKernel.match` stays synchronous and pure; the host `MatchScheduler` owns a bounded worker pool.` — the member is `check` (`StructureKernel.java:73`); `19` was never re-titled.
- Doc — `19-open-proposals.md:81` (R29): `The worker's terminal value is `MatchCompletion`; `MatchOutcome` is only the attempt product. | `01`, `20`` — `01-glossary.md:314` gives it a row; `grep -rn MatchCompletion src/` → **no hits**.
- Doc — `docs/README.md:20-21`: `The Minecraft 1.21.1 NeoForge adapter is `src/main/java/dev/vfyjxf/cumulus/mc`.` — verified: `find src/main/java -name "*.java"` returns 7 files, none under `mc/`.
- Doc — `docs/README.md:26-27`: `…there is no separate ledger of the distance, and a claim about the kernel is only as good as the code it points at.` — while three in-source gap markers exist (`base/FindingBundle.java:12`, `blueprint/DerivationSpec.java:16`, `MatchPolicy`'s "no field of this record is read yet").
- Doc — `docs/guide.zh.md:4`: `内容对应当前代码，不写尚未存在的 API。` and `:9`: `` `src/kernelTest/.../examples/`：31 个编译错误，全部是旧示例与严格签名的差集，**预期为红**。`` while `build/test-results/kernelTest/*.xml` is 162 tests / 0 failures (I §1), and `:130-131` builds `SnapshotInfo` and calls `kernel.check(request, () -> info)` / `kernel.settle(request, admitted.planned(), () -> info)` — the two-supplier facade `19` R27 and `23` §9 removed.
- Doc — `docs/spec/zh/_translation-guide.md:123`: `| invariant | 不变量 | I-1…I-15 保留编号 |` while `00-overview.md:74` is `I-16`.
- Doc — `22-consolidated-design.md:609` lists **nine** families; `13:296-311` and `base/FindingCode.java:8-25` both carry **fifteen** (J5).

**Options.** *The published set has none.* `§8.2`'s R14 is a paragraph: "这些要一并修，否则重生成后文档仍会误导"
(these must be fixed together, otherwise the documents still mislead after regeneration). The decisions
it hides are:

- **Authority when `guide.zh.md` and `docs/spec` disagree** — (i) spec wins and the guide is rewritten to
  it; (ii) the guide is authoritative for signatures (it is the only end-to-end runnable example) and the
  spec catches up; (iii) the guide is marked "illustrative, not normative" and keeps its own drift.
  Consequence: (i) rewrites 611 lines; (ii) makes `15`/`11`/`20`/`23` follow a tutorial; (iii) leaves the
  newcomer's first document lying by declaration.
- **`guide.md`** — write an English original (then the mirror rule applies) or extend
  `_translation-guide` §1 to cover a `zh`-only original. J34 states the hole; no option chooses.
- **The twelve unowned R-records** — (i) each gets a verdict, extending the audit by one pass; (ii) R16/R29
  are struck as superseded and the other ten deferred with a named owner; (iii) they are accepted as
  design of record and the *code* will catch up. Consequence: (i) costs a slice; (ii) shrinks `19`'s
  authority; (iii) makes `StructureKernel.match` and `MatchCompletion` real types the regenerator must
  invent.
- **`00` §3–§4 / `_index.md`'s gates** — treat the requirement/invariant tables as a gate (gate 4, J13/J14
  show I-4 and I-10 fail today) or as documentation. Consequence: gate → `_index.md` becomes
  unsatisfiable until I-4/I-10 are rewritten; documentation → the tables stay aspirational.

**Missing option?** **Yes — the entire option set.** R14 has no options, no consequence column and no
writability test, so it cannot be handed off as a question: as written it is a work item ("fix the docs"),
and the only thing a receiving agent can do with it is guess which document is authoritative. It should
either be split into the four decisions above, or be reclassified as a doc-edit backlog item and taken out
of the decision list (see the next section's note on R14).

---

## Rulings whose option set is incomplete

Every R in this role's range is incomplete except where noted; the pattern is that `§8.2` gives a
recommendation and calls it an option set. The list, one line each, is also the hand-off's defect list.

| R | What is missing | Severity of the gap |
| --- | --- | --- |
| R3 | the `Requirement`/`Blueprint`-declares-one-stage option; which stage an out-of-match `Finding` carries (`Finding.stage` is mandatory); what replaces B-11/`SC-CHK-0109` under (a) | missing option + open sub-fact |
| R4 | C-13(b)'s `default readStatus(cell)` + `refusedCode(SectionKey)` (the consolidated adopted C-13(c) and called it (a)); `sectionOf` vs `dimension()+cellSize()`; the nested view's **name** and `slot`/`contentDigest` answer | missing option |
| R5 | `SnapshotId`'s home as a three-way choice with consequences (`base`/`space`/`occupant`; `engine` is impossible — cycle); the `footprint` keep-with-invariant branch; `OccupantRef`'s `Comparable`; the air-minting rule | set is a "confirmation", not options |
| R6 | `22` §5.2's issues-as-value form (a third shape); the issues carrier (`IssueSink` vs `CaptureIssues`); `requiredTraits`' home; `OccupantResolver`'s member count | missing option |
| R7.1 | the middle option (both ports) is the recommendation but not enumerated; `catalogs()` in or out of the nine | enumeration gap |
| R7.2 | the failure contract (retry-twice-then-`throw` vs I-14); `Budget`'s missing derivation leg | missing option |
| R7.3 | the refusal phase/type (`build()` IAE vs registration ISE — both live); the "move `ContextKeys` to `spi`" shape | missing option |
| R7.4 | re-homing the five operations instead of delete-or-migrate; `settle`'s promotion to a member; the correct count is three of nine, not four | missing option + count error |
| R8 | R-B1(C) (conversion on `Siting`); the `DEFINITION`-cells binary (empty vs authored cells); `toDefinition`'s nullness | missing option |
| R9 | the corrected (a) (restrict to representable **negation**); B-8 (`Cuboid.of` throw, coverage range); `compose`'s nullability | missing option + refuted premise |
| R10 | the surviving enum name; `Coverage.accepts`' home; whether `Bay.validate`'s cross-check survives; **and `§8.1` calls this decided while RULE-D2 recommends the opposite** | missing option + contradiction with the decided list |
| R11 | how `Match` carries notes; `combine` is **public** (interface static method, proven by `MatchSession`'s cross-package call) so "package-private" is unwritable; `Bindings.merge`'s hard-coded `Stage.SETTLE` | fact error + missing option |
| R12 | family ownership of a condition (R3/R4 split, the rowless "decides cells outside its bay"); which dead codes are deleted vs given producers; **`SC-CTX-0202` has three producers, not none** | missing option + fact error |
| R13 | "regenerate U10 as designed" and "delete U10 from the spec set" — the two published options only choose whether to review it | missing option |
| R14 | the whole option set (authority, `guide.md`, the twelve unowned R-records, the gate-vs-documentation status of `00` §3/§4) | no options published |
