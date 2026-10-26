# 19 — Open design proposals (not in the kernel)

> These are features the first round deliberately leaves out. They are recorded here, with a
> minimal API shape and the decision that blocks them, so a later round does not rediscover
> them. Nothing in this file is frozen; a proposal becomes a module only by amending
> `01-glossary.md` and the owning unit.

## Reconciliation record (2026-10-20)

The twelve unit drafts were written independently. This pass fixed the cross-unit surface; a
module's local sketch may still be illustrative, but these names and shapes are now canonical.

| # | Ruling | Files touched |
| --- | --- | --- |
| R1 | `SourceSpan` is `(NamespacedId document, int start, int end)` in `kernel.base`; `space` depends on `base` only, and a `Label` carries one `Locus` (whose `source` is the only span), not a second span. | `01`, `05`, `09`, `13` |
| R2 | The facade's `validate` becomes `compile(BlueprintDocument) -> CompileResult(findings, @Nullable Blueprint)`. It is the DESIGN stage and the only document→blueprint path; the operation count is nine, with `settle` counted (`19` R31). | `00`, `02`, `04`, `10`, `15` |
| R3 | `DocumentValidator.of(library, expectations)` and `SegmentAssembler.of(library, expectations)`; `SegmentLibrary.Builder.put(SegmentDocument, Map<NamespacedId, ExpectationFactory>, issues)` and `SegmentAssembler.compile(SegmentDocument, issues)` own the declaration→`Segment` step. A `Map`, not `Catalog`, keeps U6 off U11 and the package graph acyclic. | `10` |
| R4 | Per-element authoring ranges travel as `Blueprint.sourceOf(NamespacedId): @Nullable SourceSpan`; `BlueprintBuilder.source(...)` stamps them. `DocumentProvenance` stays U6-local, so `blueprint` never imports `document`. | `09`, `10` |
| R5 | `Finding` carries `FindingId id`; add `FindingCode.of(String)` and `Finding.Builder.label(Locus, LabelRole, String)`. | `13`, `08` |
| R6 | `ExpectationContext` and `ProbeSession` expose `stage()`; a producer stamps `.stage(ctx.stage())` on every finding. | `07`, `04`, `13` |
| R7 | `MatchResult` carries `Stage` on every case. A `Formed` before `SETTLE` is a stage result; I-10 applies to settled cells, and only a `SETTLE` `Formed` is a structure. **Superseded by R27/R28/R31.** | `00`, `11`, `04`, `18` |
| R8 | `OccupantRef` includes its `SnapshotId`; `OccupantSnapshot` has no payload; `SnapshotPolicy` carries `requiredTraits`; `Traits` is the reserved well-known-key holder. A cell covered by an admitted occupant is `READABLE` even if its own read was denied. | `01`, `06`, `16` |
| R9 | `ClaimLedger` is `settle(SettleRequest)` and `read(Cuboid) -> ClaimView`; the view answers `rows()`, `revision()` and `available()`, where `available() == false` means "cannot answer", never an empty claim set. **Superseded by R27/R28/R31; `23` §5 adds `release`.** | `01`, `08`, `15`, `16` |
| R10 | `DerivationSpec.order` is unique and non-negative, not dense; U8 sorts `(order, cost, id)`. Augments are host-supplied; `Blueprint` does not bake an `Augment` into the template. | `09`, `12` |
| R11 | `MatchRequest` is `(ticket, blueprint, siting, stage, lease, policy, budget)`; the siting is resolved before the request exists; `MatchResult` is `Admitted`/`Formed`/`Failed`/`Undecided`/`Deferred`. **Superseded by R27/R28/R31.** | `01`, `11`, `04`, `15` |
| R12 | Glossary rows were added for `Axis`, `ReadStatus`, `ResolveContext`, `OccupantResolver<R>`, `Traits`, `RenderContext`, `SourceText`, `CompileResult`, `Place`; `OccupantRef`, `Finding`, `Label`, `DataView`, `ClaimLedger`, `MatchResult`, `CheckPlan` were corrected. | `01` |
| R13 | The golden example now uses `OrientationPolicy.builder(role).allowTurns(...)`, declarative `derive(id, Arguments)`, `kernel.compile`, and an explicit derivation budget. Interior volume for the 7×6×7 shell is 100 cells. | `04`, `18` |
| R14 | `DataView`/`DataKind` move to `kernel.base`. `ExpectationFactory.create(DataView, ...)` then closes no cycle: U6 may depend on U3, while U3 depends only on base. | `01`, `03`, `10` |
| R15 | Adapter classes drop the mechanical `Mc` prefix; the `mc` package is the namespace: `LevelCapture`, `LevelSnapshot`, `WorldOccupants`, `OccupantDescriptors`, `OccupantTraits`, `OrientationMapping`, `SavedClaims`, `WorldEdits`, `ClientOverlay`, `OverlayPayload`, `DatapackBlueprints`, `FindingOutput`, `ServerState`, `JsonFrontend`, `JsonBridge`. | `01`, `02`, `03`, `06`, `16` |
| R16 | Tick slicing is rejected. `StructureKernel.match` stays synchronous and pure; the host `MatchScheduler` owns a bounded worker pool. No `begin`/`advance`, no `TimeSource`, no `nanos`. | `02`, `11`, `16`, `18`, `20` |
| R17 | Snapshots are section-based: frozen `PalettedContainer` copies, `SectionSlot` `Air`/`Present`/`Refused`, payload cells only, with an optional shared `SectionStore` (default off). Capture is one server-thread call; `getChunkNow` never loads. | `06`, `16`, `20` |
| R18 | Dirty state is section/payload/entity; mixins on `LevelChunk.setBlockState` and `Level.blockEntityChanged`; one-shot server-thread refresh; `changeCount` participates in the snapshot check. | `02`, `16`, `18`, `20` |
| R19 | Section-chunked checking is not a scheduling split. A section `Computation` runs per section, the commit barrier folds contributions in canonical order, then aggregate units and checks run once. `ChunkGrid` remains a space utility. | `06`, `07`, `11`, `16`, `20`, `22` |
| R20 | One in-flight task per `(world, owner)`; newer requests coalesce by submit ticket; `AUTO` is deferred and re-probed by the scheduler; `INTERACTIVE` gets a reserved permit. Retained `InstanceRecord` drops the snapshot view. | `02`, `16`, `20` |
| R21 | Default is per-snapshot section copies. The shared `SectionStore` is opt-in and only enabled after profiling shows overlapping captures or repeated large copies are hot. | `20` |
| R22 | Entity movement is not tracked by a per-tick `setPos` hook. Join/leave mark the footprint; each inflated window is a watched region, and a bounded per-tick diff of its live entity set is the move witness. | `16`, `20`, `22` |
| R23 | `AUTO` runs `PROBE` only; on success the scheduler submits `FORM` as a separate request. `COMMAND`/`INTERACTIVE` may chain stages. | `16`, `20` |
| R24 | Parallel section computation is `MatchPolicy.parallel`. The fold order is canonical, so the result is unchanged; no `AGGREGATE`-only restriction remains. | `18`, `20`, `22` |
| R25 | `ReadEnvelopeAware` is deleted. Cacheability is `(unit, closureDigest)`, and an entry stores contributions or unit outcomes, never folded values. | `01`, `07`, `20`, `22` |
| R26 | Compact authoring is a normal JSON profile, not a DSL. It is a first-class datapack format (`cumulus/structure/*.json`) loaded with `DocumentProfile.AUTO`, and keeps `shell`/`cavity`/`fills`/`bays` as separate top-level fields. Capture/import emit this profile first. | `10`, `16`, `21` |

### Second pass (translation review)

The Chinese translators flagged internal contradictions in the English originals; each was
checked and fixed in both languages rather than carried as a translation note.

- **U9** — §2 now says 9 `Finding` components, a 3-component `Label` (the span lives in `Locus`)
  and a 3-component `SourceSpan`; `Locus.withSource` is the one span setter; `Label.source` and
  `withSource` are gone.
- **U5** — pose selection uses `candidate.implied().apply(Heading.NORTH)`; `build()` throws for
  B-1…B-10 plus the `allowed` precondition; `SEAM` is a `FORM` kind.
- **U14** — `BindAnchor` has a postcondition; the `Configure` placeholders phrase is complete;
  support uses its own `0206` rather than `0202`.
- **U10** — `SC-DOC-0301` is limited to duplicate keys with *different* content.
- **U11** — `step.cost` is a scheduling floor, never a charge; budget authorises each read.
- **U16** — the `ClaimSettlement` reference points at U4 OQ-4.
- **U18** — a settled `Formed` requires a `CLAIM` step; I-5 says `Siting` is anchor+policy, not
  that the two are the `Siting` record.
- **U00/U02** — twelve units, three gates, two production source sets plus their tests.
- **U17** — `Integer tier`, an iron-bars mouth, `SC-REM-` codes, and a 1×2×1 two-cell door.
- **U05** — `toLocal` is `@Nullable`; the affine composition is `(π₁∘π₂, s₁ + π₁(s₂))`.

Still open after reconciliation (owners in the unit files): addon code namespace (`OQ-6`, U9/U11),
`ClaimSettlement` as a kernel type (`16` Q-3), `FindingOptions` severity floor (`OQ-5`, U9),
census demand in `ExpectationMeta` (`11` Q-3, U3),
`SegmentFamily` (`09` Q5-3), and `Seam.required`/outward heading (`09` Q5-3). The round-3 residue
(`22` §16.1) is open on the same terms. The claim row's world identity was on this list as
"(`16` OQ-4)", a row that never existed; it is decided instead — see R35.

### Third pass (round 3)

The round-3 pass attacked revision 5 together with `23` and `24`, on three fronts plus a
spec-against-code audit. The reports are `_work/round3/a-semantics.md`, `b-types-data.md`,
`c-runtime.md` and `d-drift.md`; the lead ruled as follows. What the pass did *not* decide is
`22` §16.1.

| # | Ruling | Files touched |
| --- | --- | --- |
| R27 | Two entry points: `check` reads and writes nothing, `settle` is the only writer. `MatchResult.Admitted` carries `@Nullable ClaimPlan planned` and `long ledgerRevision`; `MatchResult` has six arms including `Dropped`, and each arm carries its `Stage`; no `MatchRequest` carries a stage, and `Stage.STALE` is deleted. The host's `CheckRequest` stage list is `22` §16.1 item 16's question. | `22`, `23`, `00`, `01`, `03`, `04`, `08`, `09`, `10`, `11`, `15`, `16`, `17`, `18`, `20` |
| R28 | The plan type is `ClaimPlan`. `23` §2's `CheckResult.Plan` collided with the per-check `CheckResult`. | `23`, `01` |
| R29 | The worker's terminal value is `MatchCompletion`; `MatchOutcome` is only the attempt product. | `01`, `20` |
| R30 | Code allocation, with no code re-pointed: `SC-CLM-0003` keeps `08` §4.6's meaning, `SC-CLM-0004` is the ledger that cannot answer or moved twice, `SC-CLM-0005` is a settle whose bindings are not valid for its request (`19` R36) — it was allocated here as "a plan that does not match its request", which R36 showed had no carrier; the meaning moves onto what is checkable, and the code is not reused for anything else — `SC-CLM-0006` is the old INFO row that had borrowed 0003, `SC-BAY-0107` is R3 only, and `SC-CTX-0202`/`SC-CTX-0301` are registered in `13`. The ledger cases never use `SC-ENG-0003`/`SC-ENG-0004`. | `13`, `08`, `12` |
| R31 | The facade has nine operations. The remedy operation is `solve`, so `15`'s `repair` is withdrawn; `MatchPolicy` is the three-component record R34 fixes; `MatchRequest` carries neither `Place` nor a stage. | `00`, `01`, `02`, `03`, `04`, `15`, `20`, `22` |
| R32 | One definition per name: one `Footprint` (`Box`/`Bound`/`Declared`/`None`), one `Sharing` exposing `blocks()`, `ClaimRow`'s component named `sharing`, one `Focus<T>` member set, no `ReadEnvelopeAware`, and `Coverage` belongs to the bay alone — `SnapshotPolicy`'s older enum of that name becomes `CaptureMode` (`EXACT`, `REQUIRED`). | `01`, `06`, `08`, `09`, `10`, `16`, `21` |
| R33 | The gap ledger `docs/status.md` is deleted, and neither a document nor a source comment points at it or at a `TODO(GAP-xx)` marker. | `docs/`, `src/` |
| R34 | `MatchPolicy` is `(int maxSectionsPerUnit, CacheMode cache, boolean parallel)` and nothing else: `FailureMode`, `StopPolicy` and `deriveOnForm`, which only its glossary row declared, are withdrawn, because no component may change a verdict, a bundle or a `Spend` (`11` §4.7). | `01`, `03`, `07`, `22` |

### Fourth pass — the design rulings

Rulings taken while working through the residue one item at a time. Each is a decision, not a sweep.

| # | Ruling | Files touched |
| --- | --- | --- |
| R35 | **A claim row names its world.** A `ClaimRow` carries its `World`; the ledger handle is bound to one world (`World world()`, so `revision()` is per world); `SettleRequest` carries the world and every row must agree with it (`SC-CLM-0007`); `ClaimMatrix.conflicts` compares the world first, so the same coordinates in two worlds are two different cells; `OwnerId` loses its world. Kernel vocabulary is `world`, the Minecraft side's is `level` (`ServerLevel`, `LevelEvent.Unload` are quoted as they are), and the claim space's old "two dimensions" are the two `axes`. | `01`, `08`, `09`, `11`, `13`, `15`, `16`, `19`, `20`, `22`, `23`, `24` |
| R36 | **A plan carries no rows.** `ClaimPlan.Matched` is `(long ledgerRevision, List<BayBinding> bindings)`; `ClaimPlanner.derive(...)` is the only row factory and is pure; `settle` runs it itself, after checking each binding structurally — a bay of this blueprint, subjects of **this** capture (`OccupantRef.snapshot`), matching kinds, cells inside the bay (exactly equal for `ONE_SUBJECT`) — and a failure is `SC-CLM-0005`. `settle` does not re-run the expectations; that is `check`'s verdict and the capture gate pins the world to it. No `PlanDigest` in this layer (with no rows there is nothing to authenticate), unlike `14`'s edit plan, which travels across a human decision. New invariant `00` I-16. | `00`, `01`, `08`, `11`, `13`, `19`, `20`, `22`, `23` |
| R37 | **The capture check reads the lease, and the slot's ticket is not one of its facts.** `SnapshotInfo` is deleted: `MatchRequest` carries the lease and, for the scheduler, an optional `Ticket`. `CaptureGate.evaluate(lease)` answers `CHANGED` when `changeCount() != 0`, `SUPERSEDED` when `!isValid()`, and `check` / `settle` take no supplier, so a host cannot answer with a fresh capture. `MatchResult` is `AutoCloseable` with a no-op `close()` on the arms that hold no handle. Slot coalescing never closes a lease that produced `Admitted`; `settle(MatchRequest, Admitted)` closes that handle on every path, and a caller that never settles closes it itself. `SC-ENG-0005` is retired (supersession is reported as `MatchCompletion.Discarded`), `SC-ENG-0006` covers `CAPTURE_INVALID`. | `01`, `04`, `11`, `13`, `15`, `16`, `17`, `19`, `20`, `22`, `23` |
| R38 | **A refused section names a validated finding code, and a read is answered per section only.** `SectionSlot` is `Air(long revision) \| Present(SectionSnapshot) \| Refused(ReadStatus, FindingCode)` — no `SectionKey` component, because the caller asked by key; a bare `NamespacedId` would let a typo become a silent, unorderable code, and `FindingCode` lives in `base`, which `occupant` may name. The per-chunk facades (`ChunkContent`, `OccupancySummary`, `WorldView.summary/chunkGrid/chunk`) are withdrawn: `ChunkGrid`/`ChunkKey` size the lattice, and `slot(SectionKey)` answers every read. `06` §3.3, `16` §3 and `20` §4.1/§6.1 now agree; the code lags (`SectionSlot.java:27` takes a `NamespacedId`, and `OccupantReadModelTest.java:64` passes `sc-occ-0102`, which no table publishes). | `01`, `06`, `13`, `16`, `19`, `20` |

## P-1 — Computed state shared inside one attempt (accepted)

**Problem.** A `FORM` or `SETTLE` predicate may need a value that is derived rather than read:
the furnace's interior occupancy, a census ratio, whether a declared interior is actually
empty. Without sharing, the same 100-cell interior is scanned once per consumer, and a
legality predicate cannot see the value at all.

**Resolution.** Accepted and specified in `22` §6.4, §7 and §8, not as a separate unit. There is one
key type: `ContextKey<T>` carries `merge`, and a non-null `merge` marks the key as section
contributed. A section `Computation` calls `ContextDraft.contribute(key, partial)` once per section;
a commit barrier sorts the contributions by `SectionKey` and folds them left to right with
`Merge.combine`; aggregates and checks read the folded value with `ContextView.require(key)`, and a
check reaches it through `Focuses.context(key)`. `Retention.CONTEXT` publishes the key into the
stage's tier frame. A refused section makes the key absent, which reaches the focus as
`Outcome.Missing` and becomes `UNREADABLE`.

The earlier sketch (`ContextKey` + `Deriver` + `computed(key)` in `ProbeSession`) is retired: it put
the read behind a session convenience and gave promotion a second writer. `Deriver` remains as the
context-only producer (`22` §6.4).

## P-2 — Cross-instance structure graph

**Problem.** Two independently placed structures can be adjacent, share a seam, or depend on
each other's claims ("this wall is mine; the neighbour may not build here"). The kernel models
one blueprint at one siting; it has no edge between two formed instances. A neighbour placing
or removing an exclusive claim does not invalidate this instance unless the changed cell is in
its `ReadSet`.

**Shape.**

```java
record InstanceEdge(InstanceRef from, InstanceRef to, SeamKind kind, Locus contact) {}
interface InstanceGraph {
    List<InstanceRef> neighbours(InstanceRef instance);
    List<InstanceEdge> edgesOf(InstanceRef instance);
}
```

`InvalidationIndex.affectedBy(ChangeSet)` gains a second source: a claim change on an edge's
contact cells. The host owns the graph; the kernel only consumes it.

**Why it is deferred.** It needs a persistence story (the host already owns `RuntimeState`),
and it risks turning the kernel into a world-scale registry. The `Seam` and claim-row model
already carries enough identity to build it outside the kernel.

## P-3 — A read-demand channel and prefetch

**Problem.** `ReadStatus` tells a predicate *that* a cell is `NOT_CAPTURED` or `DENIED`, not
*what* would be needed to answer it. A host that could re-capture a missing region has no
machine-readable request.

**Shape.** A `CaptureHint` carried by `MatchRequest` or returned in `Undecided`:

```java
record CaptureHint(CuboidSet missing, Set<TraitKey<?>> traits, int priority) {}
```

The host may use it to capture a larger window on the next attempt. The kernel never captures
and never blocks on a hint.

**Why it is deferred.** It is a host optimisation, not a semantic; `ReadStatus` already
preserves correctness. The first round keeps `Undecided` honest rather than actionable.

## P-4 — A reference scheduler

**Problem.** The host must decide when to capture, when to match, how to spread work across
ticks, and how to queue stale re-checks. Every host would re-implement this.

**Shape.** A host-side `StructureScheduler` with a per-tick budget, a queue of `MatchRequest`s
and a suspension/resume protocol. It is **not** kernel code: it holds a `StructureKernel`, a
`WorldCapture`, and a clock; the kernel stays pure.

**Why it is deferred.** The user asked for a kernel, not a server mod. The adapter chapter
(`16-adapter.md`) gives the reference loop; a standalone scheduler is a follow-up host library.

## P-5 — Addon code and family allocation

**Problem.** `SC-<FAMILY>-<NNNN>` is stable and human-searchable, but two addons can mint the
same code, and a new family changes a closed enum (U9).

**Options.**

1. Keep the closed family enum; allocate `0001–0999` to the kernel and `1000–9999` to addons
   per family, with a reviewed registry table. Simple, but two unreviewed addons can collide.
2. Namespace the family: `SC-<family>-<ns>-<nnnn>`. Collision-free, but longer and not
   rustc-shaped.
3. Keep the enum and add a `NamespacedId` owner to `FindingCode`; render `SC-EXP-0001` for the
   kernel and `SC-EXP-mymod-0001` for an addon. A format change.

The first round uses option 1 and records the collision risk in `13-findings.md`; the owner
should rule before an addon ecosystem exists.

## P-6 — Localisation of player-facing text

**Problem.** `Finding.message` is English prose in the first round; a player sees a long
compiler-style message. The host wants short localised strings.

**Shape.** `FindingCode` already carries the identity; a `FindingText` localisation table keyed
by `(code, args)` can render a short form, while `FindingRenderer.human()` keeps the long form
for logs and `/cumulus explain`. The kernel does not ship a locale catalogue.

**Why it is deferred.** It is a host concern and does not change the finding value.

## How a proposal graduates

1. It gets a row in `01-glossary.md` with an owner and a stability value.
2. Its owner writes a nine-section module file and cites the invariant it extends.
3. The counterexample catalogue in `18-verification.md` gains at least one row for it.
4. A reviewer checks that no existing invariant (especially I-4, I-9, I-15) is weakened.
