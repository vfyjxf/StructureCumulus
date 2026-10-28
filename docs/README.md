# StructureCumulus — design of record

This directory is the **source of truth** for StructureCumulus. The kernel under `src/kernel/java`
is generated from these files and is allowed to be deleted and regenerated at any time. A fact that
is not in this directory is not part of the design.

The design set is deliberately smaller than a specification suite: one constitution, one model,
one module graph, one API tour, one decision record, and one file per module. Every module file is
normative: its Java blocks define the public shape, its tables define the laws, and its producer
table defines who may emit each finding code.

## Reading order

1. [design/00-requirements.md](design/00-requirements.md) — what the library must do, and what it
   refuses to do.
2. [design/01-constitution.md](design/01-constitution.md) — the twelve laws every module must obey.
3. [design/02-model.md](design/02-model.md) — the one-page model, the three phases, the data flow.
4. [design/03-modules.md](design/03-modules.md) — the eleven kernel packages, their ranks and edges.
5. [design/04-api.md](design/04-api.md) — the surfaces and the worked examples a user writes.
6. [design/05-decisions.md](design/05-decisions.md) — every ruling that replaced an older one.
7. [design/06-verification.md](design/06-verification.md) — gates, scenario tests, code producers.
8. [design/07-regeneration.md](design/07-regeneration.md) — how the kernel is rebuilt from this set.
9. [design/08-context-cache.md](design/08-context-cache.md) — the host-owned cached-context port.
10. [design/09-structure-sets.md](design/09-structure-sets.md) — root/satellite structure sets and links.
11. [design/10-host-data.md](design/10-host-data.md) — host data capture and MC-specific checks.
12. [design/11-trait-providers.md](design/11-trait-providers.md) — generic per-holder trait providers.

There is also an interactive, adversarial review of this design set in [`../visual/`](../visual/README.md). It is a reading aid; this directory remains the source of truth. The visual data is regenerated separately and trails this set until its review screenshots are refreshed.

Then the module files, in dependency order:

| Rank | Module | File |
| --- | --- | --- |
| 0 | `kernel.core` | [modules/core.md](modules/core.md) |
| 1 | `kernel.space` | [modules/space.md](modules/space.md) |
| 2 | `kernel.finding` | [modules/finding.md](modules/finding.md) |
| 2 | `kernel.world` | [modules/world.md](modules/world.md) |
| 3 | `kernel.claim` | [modules/claim.md](modules/claim.md) |
| 4 | `kernel.structure` | [modules/structure.md](modules/structure.md) |
| 5 | `kernel.context` | [modules/context.md](modules/context.md) |
| 6 | `kernel.document` | [modules/document.md](modules/document.md) |
| 7 | `kernel.engine` | [modules/engine.md](modules/engine.md) |
| 8 | `kernel.remedy` | [modules/remedy.md](modules/remedy.md) |
| 9 | `kernel.spi` | [modules/spi.md](modules/spi.md) |
| A1 | `dev.vfyjxf.cumulus.mc` | [modules/adapter.md](modules/adapter.md) |

## Status

- Design revision: **v1.2**, 2026-10-27, plus the [08 context-cache](design/08-context-cache.md),
  [09 structure-set](design/09-structure-sets.md), [10 host-data](design/10-host-data.md) and
  [11 trait-providers](design/11-trait-providers.md) extension documents. The v1.1 round specified
  Model C (body identity, the narrowest-read refusal, four-state traits) and the nested capability;
  v1.2 records them as implemented, and its M5a/M5d/M5e slices close the adapter round. 08/09
  specify the host-owned cached-context port (D-31) and the root/satellite set layer (D-32); 10
  specifies the typed host snapshot (D-33), implemented as H1–H4 and superseding the descriptor
  projection (D-29); 11 specifies the generic per-holder trait providers and direct capability
  values (D-34), implemented as T1–T3.
- Scope of the v1 round: **core kernel + document compiler + thin remedy SPI**. The NeoForge adapter
  was specified only in that round. Current status: M1 is the host + command + capture-check slice —
  `LevelCapture`, `LevelSnapshot`, `LevelLease`, block/block-entity descriptors, the `ServerState`
  lifecycle, in-memory `BuiltinDocuments` (`structurecumulus:air_cell`) and
  `/cumulus check <id> <origin>` (identity siting for anchorless blueprints). M2a adds loaded-entity
  capture with policy windows, full footprint indexing and entity-vs-block precedence (`world.md`
  §4.3). M2b adds `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror` and an upright
  mirror-then-rotate `BlockState` transform.
  M2c implements Model C, body identity, the total read and trait capture at `cb06aa8`/`64d04ce`.
  M2d implements nested extents, frames, cuts and child entities at `f3e0338`/`d6aefc2`. M3 is
  committed at `d4b6d82`; its `SavedClaims`/`SavedContexts` persistence is compatible with Model C and
  its CAS/release/reload tests are green. M4a JSON reader/writer (`f2659c7`), M4b datapack reload
  (`76a8e5c`) and the command slice (`settle` at `4899d86`, plus `compile`, `explain`, `claims`) are
  implemented; capture-revision lease invalidation landed at `0f0bf25`, and the determinism gate
  (`DeterminismTest`: canonical bytes, parse/write fixed point, equal compile runs, stable finding
  order) is green. M5a adds the event-edged watched-region mark counter (`LevelWatch`/`WatchEvents`,
  lazy entity and block-entity fingerprints) at `d1fa42f`; P1 fixes the rotated-siting capture bounds
  with the two-phase anchored capture at `56c632c`; M5d remaps the 40 non-upright orientations at
  `ac11d0c`; M5e adds the capture report (D-28: `world.CaptureReport` on every `CaptureResult` arm,
  `SC-MC-0003` cut / `SC-MC-0004` orientation / `SC-MC-0005` over-cap, the host code catalog and the
  command report block) at `28d0334`. S6 implemented the sited-root descriptor projection (D-29),
  which H1 retired and H4 records as superseded by the typed host snapshot (D-33): `OccupantDescriptor`
  is `(type, kind)`, the projection is retired, `OrientationAuditGameTest` keeps the orientation audit
  and the adapter's value checks read the captured snapshot. Kernel
  stress batch 1 (tag `kernel-stress-batch-1`, `110e797`) adds 44 characterization scenarios across
  12 `engine` classes, and batch 2 (tag `kernel-stress-batch-2`, `3e60ed4`) adds 17 kernel scenarios
  plus 11 adapter unit tests and fixes `NestedFocus` to bind no subject; the documented gaps are in
  `design/06-verification.md` §3.1/§3.2. D-30 adds the derived envelope: `BlueprintBuilder.envelope()`
  / the root `"envelope": true` member closes the bounds with a reserved
  `structurecumulus:envelope` bay and check, a foreign occupant is `SC-EXP-0006`, and an empty
  uncovered set is a no-op. Its batch adds 22 kernel tests (19 in two new containers plus three
  `DocumentCompilerTest` cases) and four `JsonDocumentTest` cases. D-31 adds the optional
  `MatchRequest.cachedContext` port: a host-owned `(id, type)` value cache composed with the attempt
  context, current attempt authoritative, no kernel writes or invalidation (`681c505`). D-32 adds the
  structure-set layer: the `structure_set` document IR/reader/writer/compiler (`9cf2794`,
  `00cf6c4`), the host `StructureSet` plan and `StructureSetHost` orchestration with rollback
  (`93f5941`), and the `SC-SET` codes. SS4 adds the six-class/33-test structure-set stress
  suite (`design/06-verification.md` §3.3). H1–H4 add the host-data round: `HostType`,
  `WorldView.get`, `Expectations.host` and `OccupantDescriptor(type, kind)`; one `SnapshotLevel` per
  frame with copied states, fluids, block entities and entities; the adapter
  `property`/`state`/`tag`/`fluid` factories and `SC-MC-0006`…`0010`; and the `HostDataScenarioTest`
  group (§3.4) over a rotated structure set, a nested child frame, a waterlogged slab and a missing
  snapshot. T1–T3 (D-34, `design/11-trait-providers.md`) replace the `readBlock`/`readEntity` trait
  providers with the generic `TraitProvider<T, H>`/`TraitHolder<H>` pair registry, one
  `CapabilityTraitProvider<T, H, C>` with block/entity accessors, the direct `IItemHandler`/
  `IEnergyStorage`/`IFluidHandler` trait values and the NBT-trait removal. S5 of the 09 structure-set
  close-out adds the production set command (`/cumulus set check`, `/cumulus set settle`), its one
  union capture and check-only host path, the built-in `set_root`/`set_wing`/`set_demo` documents and
  the real-level set GameTests (`design/06-verification.md` §3.5). S6 closes the set layer: the
  `SC-SET-0008` same-siting rejection, `ContextStore.remove` with rollback invalidation, and one set
  budget shared by presence and every member attempt (D-35). The tree registers 261
  kernel tests in 39 containers, 292 adapter unit tests in 37 containers and 69 `@GameTest` methods;
  a full `./gradlew check` and `runGameTestServer` on the S6 working tree are both green (261
  kernel tests, 292 adapter tests, 69/69 GameTests). Pending only: the
  performance/cost pass (block-entity-lazy and entity sweep) and fresh visual review screenshots.
  There is no regeneration milestone.
- Language: English only. There is no mirrored translation. A Chinese quick-start may cite this
  set, but the English files are the design of record.
- Code status: `src/kernel/java` is generated from these files; `src/main/java` is the mod entry and
  the adapter. The adapter is the only place allowed to name Minecraft.
- Verification (2026-10-29, S6 working tree): the kernel compiles with
  `javac --release 21` and JSpecify only. The tree registers 261 kernel JUnit 5 tests (39 test
  containers), 292 adapter unit tests (37 containers) and 69 `@GameTest` methods; a full
  `./gradlew check` and `runGameTestServer` both pass at this revision (261 kernel tests, 292 adapter
  tests, 69/69 GameTests, 0 failures, 0 skipped). `Codes.all` has 65 entries
  and the decision record has 35 decisions (D-35; D-29 superseded).

## The one rule

**If the code and this directory disagree, this directory wins and the code is regenerated.**
A change to a public shape is a change to a module file first; the code follows in the same commit,
or in a regeneration run.
