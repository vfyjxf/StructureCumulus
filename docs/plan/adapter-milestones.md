# Adapter milestone plan (working document, not normative)

> This plan tracks execution. The normative adapter contract remains
> [`docs/modules/adapter.md`](../modules/adapter.md); if this file and the module file disagree, the
> module file wins.

## Scope

Adapter implementation for Minecraft 1.21.1 / NeoForge 21.1.x, plus GameTests, release gates and a
clean kernel regeneration. No client overlay, network payload, schematic importer, GUI or scheduler.

## Order and state at 2026-10-27

The commit order was **M1 → M2a → M2b → M3 → M2c → M2d**. M3 was committed before Model C landed;
its `SavedClaims`/`SavedContexts` code is compatible as-is, so the "rebase" is a checkpoint, not a
rewrite. M4 is the next milestone. A milestone is complete only when its code and its named tests are
both on the tree and re-run; the current counts are 116 kernel tests, 56 adapter unit tests and 22
GameTests, with `./gradlew check` and `tools/spec-check.py` green.

| Milestone | Deliverable | State |
| --- | --- | --- |
| M0 | Freeze `kernel-v1`; adapter type/code/GameTest checklist | done |
| M1 | Capture/lease/snapshot, block/block-entity descriptors, `ServerState`, in-memory documents, `/cumulus check` | committed at `a8760dc`; two GameTests |
| M2a | Loaded-entity capture, policy windows, footprint indexing, entity-vs-block precedence | committed at `0cbc2ed`; capture-entity-over-block GameTest |
| M2b | Upright orientation mapping; non-level tilt returns `UnsupportedTilt` | committed at `b749876`; `OrientationMappingTest` |
| M2c | **Model C + traits**: `OccupantRef.body`, total `CellRead`/`ReadRefusal`, `SectionSlot.Partial`, `TraitSlot`, the five `OccupantTraits` keys, `SnapshotPolicy.traits` by id, `Blueprint.reads`-derived policy, `SC-OCC-0004`, `SC-MC-0002` relay | committed at `cb06aa8`/`64d04ce`; kernel and adapter tests green |
| M2d | **Nested capability**: `NestedExtent`/`NestedExtents`, one `LevelSnapshot` + `FrameData`, child frames, budgets/cuts, child entities, fake-provider seam | committed at `f3e0338`/`d6aefc2`; nested unit + GameTests green |
| M3 | `SavedClaims`, `SavedContexts`, CAS/release/reload, per-level `ServerState` | committed at `d4b6d82`; compatible checkpoint passed; settle command/lease pending |
| M4 | JSON reader/writer, datapack reload, remaining commands (`compile`, `settle`, `explain`, `claims`) | **next; pending** |
| M5 | Remaining GameTests, determinism golden test, lease invalidation, Gradle gates | partial: 116 kernel + 56 adapter + 22/22 GameTests and `check` green; determinism and lease invalidation pending |
| M6 | Clean delete/regenerate on a branch | pending |

## Acceptance

| Milestone | Acceptance |
| --- | --- |
| M2c | Met: body identity, partial sections, trait four states, policy ids, `SC-OCC-0004` and relay covered by `ReadModelTest`/`WorldTest`/`OccupantTraitsTest`; spec-check green; existing GameTests pass. |
| M2d | Met: `NestedCaptureGameTest`/`LevelSnapshotTest` cover frame/depth/parent/origin, one snapshot, cut reasons, child build-height partial sections, block-over-entity precedence, body identity and entity-hosted extents; no built-in provider; no chunk load. |
| M3 checkpoint | Met: `SavedClaimsTest`/`SavedContextsTest` and the claims GameTest pass after Model C; `SC-OCC-0004`/relayed host codes persist. The `settle` command is M4. |
| M4 | Reader/writer round-trip, datapack reload, settle/claims command. |
| M5 | `./gradlew check` green and `runGameTestServer` all required tests pass; determinism golden and lease invalidation added. |
| M6 | Delete → regenerate → gates green on a branch. |

## Owners

- Main: contract arbitration, docs amendments, integration, commits, final gates.
- A: `mc/capture`, `mc/world`, `mc/orientation`, `mc/capability` (M2c/M2d complete).
- B: `mc/claim`, `mc/context`, M3 checkpoint and the settle command.
- C: `mc/document`, `mc/host`, `mc/command`, mod entry (M4).
- D: GameTests and test resources.
- E: build/CI, determinism, lease invalidation, regeneration branch.

## Rules

- The kernel public ports are frozen after Model C; a change to a public shape is a docs amendment
  and a decision record first.
- Model C, traits and the nested capability are normative in `modules/world.md`,
  `modules/structure.md`, `modules/engine.md` and `modules/adapter.md`; an implementation change stops
  and amends the design first.
- Dependency source reads go through SourceScout.
- One file owner at a time; at most two implementation subagents concurrent.
- Every milestone is a green checkpoint, not a partial merge. No milestone is marked complete on a
  committed class alone; its named tests must be on the tree and re-run.
