# Adapter milestone plan (working document, not normative)

> This plan tracks execution. The normative adapter contract remains
> [`docs/modules/adapter.md`](../modules/adapter.md); if this file and the module file disagree, the
> module file wins.

## Scope

Adapter implementation for Minecraft 1.21.1 / NeoForge 21.1.x, plus GameTests and release gates. No
client overlay, network payload, schematic importer, GUI or scheduler.

## Order and state at 2026-10-28

The commit order was **M1 → M2a → M2b → M3 → M2c → M2d**, then **M4a → M4b → the command slice**,
then **M5a → M5d → M5e → S6**, then the **H1–H4** host-data round (D-33). M3
was committed before Model C landed; its `SavedClaims`/`SavedContexts` code is compatible as-is, so the
"rebase" is a checkpoint, not a rewrite. The kernel was regenerated from the design set at `4a6e093`,
so the regeneration objective is historical, not pending; there is no regeneration milestone and no
M6. Every milestone is implemented, and a milestone is complete only when its code and its named
tests are both on the tree and re-run. The kernel stress batches `kernel-stress-batch-1` (`110e797`,
44 kernel scenarios in 12 `engine` classes) and `kernel-stress-batch-2` (`3e60ed4`, 17 kernel
scenarios plus 11 adapter unit tests) are kernelTest/adapter-test-only and add no adapter milestone;
batch 2's one code change is the `NestedFocus` non-binding fix; the envelope, cached-context and
structure-set slices (`a5f7084`, `681c505`, `9cf2794`, `00cf6c4`, `93f5941`) and the SS4 stress batch
follow, and the H1–H4 host-data round closes the adapter's typed-snapshot work. S6's descriptor
projection is superseded by the typed host snapshot (D-33): the descriptor has no properties, the
projection is retired and the orientation audit remains (`OrientationAuditGameTest`). Current counts
(2026-10-28, H4 close-out): **258 kernel tests in 39 containers, 276 adapter unit tests in 36
containers, 64 registered GameTests**, `Codes.all` 64 entries, 33 decisions (D-33; D-29 superseded).
A full `./gradlew check` and `runGameTestServer` pass at `d70ecd7` (258 kernel tests, 276 adapter
tests, 64/64 GameTests). The T1–T3 trait-provider refactor is the next working slice; after it the
counts are refreshed. Pending after T1–T3: the performance/cost pass and fresh review screenshots.

| Milestone | Deliverable | State |
| --- | --- | --- |
| M0 | Freeze `kernel-v1`; adapter type/code/GameTest checklist | done |
| M1 | Capture/lease/snapshot, block/block-entity descriptors, `ServerState`, in-memory documents, `/cumulus check` | committed at `a8760dc`; two GameTests |
| M2a | Loaded-entity capture, policy windows, footprint indexing, entity-vs-block precedence | committed at `0cbc2ed`; capture-entity-over-block GameTest |
| M2b | Upright orientation mapping | committed at `b749876`; `OrientationMappingTest` |
| M2c | **Model C + traits**: `OccupantRef.body`, total `CellRead`/`ReadRefusal`, `SectionSlot.Partial`, `TraitSlot`, the five `OccupantTraits` keys, `SnapshotPolicy.traits` by id, `Blueprint.reads`-derived policy, `SC-OCC-0004`, `SC-MC-0002` relay | committed at `cb06aa8`/`64d04ce`; kernel and adapter tests green |
| M2d | **Nested capability**: `NestedExtent`/`NestedExtents`, one `LevelSnapshot` + `FrameData`, child frames, budgets/cuts, child entities, fake-provider seam | committed at `f3e0338`/`d6aefc2`; nested unit + GameTests green |
| M3 | `SavedClaims`, `SavedContexts`, CAS/release/reload, per-level `ServerState` | committed at `d4b6d82`; compatible checkpoint passed; settle command implemented at `4899d86` |
| M4 | JSON reader/writer (`f2659c7`), datapack reload (`76a8e5c`), commands (`compile`, `settle`, `explain`, `claims`; settle at `4899d86`) | **implemented**; `CommandGameTest`/`SettleCommandGameTest` green |
| M5 | Determinism gate, Gradle gates, capture-revision lease invalidation | **met**: determinism gate (`DeterminismTest`), capture-revision lease invalidation at `0f0bf25` |
| M5a | **Mark counter**: `LevelWatch`/`WatchEvents`, refcounted watched chunks, event-edged marks, lazy entity and block-entity fingerprints, `LevelWatchTest` + `LeaseMarkCounterGameTest` | **implemented at `d1fa42f`** |
| M5d | **Non-upright mapping**: the 40 pitch/roll/inverted orientations, capability-aware state remap, `MappingResult.Unrepresentable` as the `SC-MC-0004` payload | **implemented at `ac11d0c`**; `OrientationMappingTest` |
| M5e | **Capture report (D-28)**: `world.CaptureReport` on every `CaptureResult` arm, `SC-MC-0005` over-cap, `SC-MC-0003` cut, the sited-capture `SC-MC-0004` audit, the host code catalog, `CaptureReports` + the command report block | **implemented at `28d0334`** (P1's two-phase capture at `56c632c`) |
| S6 | **Sited-root descriptor projection (D-29)**: root-frame `BlockState` properties projected into the definition frame before `OccupantDescriptors`; unrepresentable states keep the raw descriptor and report `SC-MC-0004`; child/nested frames and the phase-1 anchor capture stay world-frame | **superseded by D-33 (H1)**; the projection is retired and the audit stays as `OrientationAuditGameTest` |
| H1–H4 | **Host snapshot and typed reads (D-33)**: `HostType`, `WorldView.get`, `Expectations.host`, `OccupantDescriptor(type, kind)`, one `SnapshotLevel` per frame, the adapter `property`/`state`/`tag`/`fluid` factories, `SC-MC-0006`…`0010`, and the H4 docs/counts/stress group | **implemented**; `HostDataScenarioTest` and the focused host-data units green |
| T1–T3 | **Generic trait providers (D-34)**: `TraitProvider<T,H>`, `TraitHolder<H>`, `(trait, holder)` `TraitLookup`, `CapabilityTraitProvider<T,H,C>` + `CapabilityAccesses`, direct `IItemHandler`/`IEnergyStorage`/`IFluidHandler` values, NBT traits removed, standard providers split by holder | **in progress**; design at `11-trait-providers.md` |

## Acceptance

| Milestone | Acceptance |
| --- | --- |
| M2c | Met: body identity, partial sections, trait four states, policy ids, `SC-OCC-0004` and relay covered by `ReadModelTest`/`WorldTest`/`OccupantTraitsTest`; spec-check green; existing GameTests pass. |
| M2d | Met: `NestedCaptureGameTest`/`LevelSnapshotTest` cover frame/depth/parent/origin, one snapshot, cut reasons, child build-height partial sections, block-over-entity precedence, body identity and entity-hosted extents; no built-in provider; no chunk load. |
| M3 checkpoint | Met: `SavedClaimsTest`/`SavedContextsTest` and the claims GameTest pass after Model C; `SC-OCC-0004`/relayed host codes persist. The `settle` command is implemented at `4899d86`. |
| M4 | Met: reader/writer round-trip, datapack reload and the five commands; `SettleCommandGameTest`/`CommandGameTest` cover the command surface. |
| M5 | Met: `./gradlew check` green and `runGameTestServer` all required tests pass; the determinism gate is met (`DeterminismTest`); capture-revision lease invalidation is in place. |
| M5a | Met: `LevelWatchTest` covers the refcounted watched chunks, the epoch and the lazy fingerprints; `LeaseMarkCounterGameTest` covers set-block, outside-chunk, revert, entity join/leave/move, a 10k high-frequency move sweep with no dispatch, chunk unload and level epoch; a stale lease is never current and no chunk is loaded. |
| M5d | Met: `OrientationMappingTest` covers the 8 upright and 40 non-upright orientations, the mapped and unrepresentable fixtures and the inverse round-trip; no silent identity. |
| M5e | Met: `CaptureReportTest` covers the payload and the compatibility constructors; `CaptureReportsTest` covers severity, the trust guard, the world label and notes; `NestedCaptureGameTest` covers the over-cap entry and a cut reason with the unchanged `nested(cell) == null`; `CaptureAuditGameTest` covers the grouped orientation audit and no chunk load; `CommandCaptureReportGameTest` covers the print order and the `SC-MC-0005` command surfacing. `Codes.all` is 64 and the focused gates are green. |
| S6 | Superseded by H1/D-33. The retained audit is `OrientationAuditGameTest`: an upright siting reports nothing, an unrepresentable state reports one grouped `SC-MC-0004` entry, and child frames are not audited. `world` §4.7 owns the typed-read law; the kernel keeps no transform. |
| H1–H4 | Met: `HostExpectationTest` (the bridge and caller-supplied absent code), `McAnchorsTest` (the captured anchor and `SC-MC-0010`), `SnapshotLevelTest`/`LevelSnapshotTest` (copied states, fluids, entities and typed dispatch), `AdapterExpectationsTest` (the four factories, waterlogged slab, JSON/Java parity) and `HostDataScenarioTest` (rotated structure set, nested child frame, waterlogged slab, missing snapshot). `Codes.all` is 64 and the decision record is 33. |
| T1–T3 | Met when: generic provider/holder registry tests pass; the capability provider covers block and entity through one class; standard traits return direct capabilities; NBT traits are absent; block/entity capability absence, null block entity and malformed capability keep their old states; plugin and built-in lookup parity; focused `check` and GameTest regressions green. |

## Owners

- Main: contract arbitration, docs amendments, integration, commits, final gates.
- A: `mc/capture`, `mc/world`, `mc/orientation`, `mc/capability` (M2c/M2d complete).
- B: `mc/claim`, `mc/context`, M3 checkpoint and the settle command.
- C: `mc/document`, `mc/host`, `mc/command`, mod entry (M4).
- D: GameTests and test resources.
- E: build/CI, determinism, lease invalidation and the mark counter (M5/M5a).
- M5d/M5e/S6 slices: mapping (`ac11d0c`), capture report (`28d0334`, with P1 at `56c632c`) and the
  sited-root descriptor projection (D-29, now superseded by D-33); the docs amendment (S0) and the
  evidence refresh (S5) are part of the same close-out. H1–H4: the typed host snapshot and the
  adapter value factories (`HostDataScenarioTest`, `OrientationAuditGameTest`). T1–T3: trait-provider
  refactor (`11-trait-providers.md`, D-34).

## Rules

- The kernel public ports are frozen after Model C; a change to a public shape is a docs amendment
  and a decision record first. M5e amended `world` under D-28 in that order: the module file and the
  decision record landed before the carrier.
- Model C, traits, the nested capability and the capture report are normative in `modules/world.md`,
  `modules/structure.md`, `modules/engine.md`, `modules/adapter.md` and `modules/finding.md`; an
  implementation change stops and amends the design first.
- Dependency source reads go through SourceScout.
- One file owner at a time; at most two implementation subagents concurrent.
- Every milestone is a green checkpoint, not a partial merge. No milestone is marked complete on a
  committed class alone; its named tests must be on the tree and re-run.
- No regeneration milestone and no M6: the regeneration at `4a6e093` is historical.
