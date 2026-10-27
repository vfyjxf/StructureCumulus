# Adapter milestone plan (working document, not normative)

> This plan tracks execution. The normative adapter contract remains
> [`docs/modules/adapter.md`](../modules/adapter.md); if this file and the module file disagree, the
> module file wins.

## Scope

Adapter implementation for Minecraft 1.21.1 / NeoForge 21.1.x, plus GameTests, release gates and a
clean kernel regeneration. No client overlay, network payload, schematic importer, GUI or scheduler.

## Order and state at 2026-10-27

The commit order was **M1 → M2a → M2b → M3**. The semantic order from here is **M2c (Model C) →
M2d (nested capability) → M3 rebase → M4**, because M3's persisted findings must round-trip
`SC-OCC-0004` and the adapter's trait/refusal code must speak the total read. M3 is **not** reverted;
only its findings/refusal edge is adapted. A milestone is complete only when its code and its named
tests are both on the tree; a committed class without re-run evidence is not a completion claim.

| Milestone | Deliverable | State |
| --- | --- | --- |
| M0 | Freeze `kernel-v1`; adapter type/code/GameTest checklist | done |
| M1 | Capture/lease/snapshot, block/block-entity descriptors, `ServerState`, in-memory documents, `/cumulus check` | committed at `a8760dc`; two GameTests |
| M2a | Loaded-entity capture, policy windows, footprint indexing, entity-vs-block precedence | committed at `0cbc2ed`; capture-entity-over-block GameTest |
| M2b | Upright orientation mapping; non-level tilt returns `UnsupportedTilt` | committed at `b749876`; `OrientationMappingTest` |
| M2c | **Model C**: `OccupantRef.body`, total `CellRead`/`ReadRefusal`, `SectionSlot.Partial`, `TraitSlot`, `OccupantTraits`, `SnapshotPolicy.traits` by id, `Blueprint.reads`-derived policy, `SC-OCC-0004`, `SC-MC-0002` relay | **specified, pending implementation** |
| M2d | **Nested capability**: `NestedExtent`/`NestedExtents`, one `LevelSnapshot` + `FrameData`, child frames, budgets/cuts, child entities, fake-provider seam | **specified, pending implementation** |
| M3 | `SavedClaims`, `SavedContexts`, CAS/release/reload, per-level `ServerState` | committed at `d4b6d82`; rebase onto M2c/M2d pending |
| M4 | JSON reader/writer, datapack reload, remaining commands (`compile`, `settle`, `explain`, `claims`) | pending |
| M5 | Required GameTests, determinism golden test, Gradle gates | partial: 97 kernel + 43 adapter unit + 4/4 GameTests recorded; determinism pending |
| M6 | Clean delete/regenerate on a branch | pending |

## Acceptance

| Milestone | Acceptance |
| --- | --- |
| M2c | Kernel and adapter unit tests for body identity, partial sections, trait four states, policy ids and `SC-OCC-0004`; `tools/spec-check.py` clean again; existing M1/M2a GameTests still pass. |
| M2d | Package-private `LevelCapture(ServerLevel, NestedExtentLookup)` unit test with fake extents: frame/depth/parent/origin, one snapshot, cut reasons, child build-height partial sections, block-over-entity precedence; no built-in provider; no chunk load. |
| M3 rebase | `SavedClaimsTest` and `SavedContextsTest` still pass; a persisted `Formed` with `SC-OCC-0004` and a relayed host code reloads; `ClaimsGameTest` passes. |
| M4 | Reader/writer round-trip, datapack reload, settle/claims command. |
| M5 | `./gradlew check` green and `runGameTestServer` all required tests pass. |
| M6 | Delete → regenerate → gates green on a branch. |

## Owners

- Main: contract arbitration, docs amendments, integration, commits, final gates.
- A: `mc/capture`, `mc/world`, `mc/orientation`, `mc/capability` (M2c/M2d).
- B: `mc/claim`, `mc/context`, M3 rebase.
- C: `mc/document`, `mc/host`, `mc/command`, mod entry (M4).
- D: GameTests and test resources.
- E: build/CI, determinism, regeneration branch.

## Rules

- The kernel public ports are frozen after the Model C rebase; an adapter gap is a docs amendment
  first.
- Model C and the nested capability are specified in `modules/world.md`, `modules/structure.md`,
  `modules/engine.md` and `modules/adapter.md`; an implementation that changes a signature stops and
  amends the design first.
- Dependency source reads go through SourceScout.
- One file owner at a time; at most two implementation subagents concurrent.
- Every milestone is a green checkpoint, not a partial merge. No milestone is marked complete on a
  committed class alone; its named tests must be on the tree and re-run.
