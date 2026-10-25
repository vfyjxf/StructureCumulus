# StructureCumulus — a version-independent multiblock kernel

> Status: design draft v0.1, 2026-10-20. This set is a *design*, not an implementation
> commitment. Every module file is allowed to carry open questions; a claim without a
> falsification path is a defect.

## 1. What we are building

StructureCumulus is a **structure kernel**: a pure-Java engine that matches a declarative
blueprint against a lattice of **occupants** (blocks, block entities, entities, and nested
structures alike), reports rustc-grade findings, and derives a typed runtime context from a
successful match. It never names Minecraft. A thin adapter binds the kernel to Minecraft
1.21.1 / NeoForge 21.1.x; that adapter is the only code allowed to import Minecraft types.

The kernel is deliberately **one large, closed core** with a small set of extension points.
Predicates, derivers, remedies, occupant resolvers and world capture are interfaces; the
kernel ships only trivial implementations of them. This is the "one kernel, many
extensions" rule (R-10), not a plugin framework with a kernel per loader.

## 2. One-sentence model

> A **blueprint** is matched against an immutable **world snapshot** at a **siting**, in one
> **stage**, producing a sealed **match result** plus a **finding bundle**; a formed result
> can be **derived** into a typed **structure context**; findings can be turned into
> **remedies** (repair plans, overlays, explanations) over one shared plan substrate.

Every other document elaborates one noun in that sentence. If a proposal cannot be placed
on that sentence, it belongs outside the kernel.

## 3. Requirements

The commissioning statement is fixed. R-01..R-15 are the kernel's acceptance criteria; a
module file names the requirements it serves, and a decision that serves none is refused.

| Id | Requirement | Design consequence |
| --- | --- | --- |
| R-01 | Do not assume the predicate's base element is a block. Entities and nested substructures are elements too; the coordinate system and grid stay Minecraft-shaped. | `OccupantKind` is an open family; `WorldView` resolves every kind through the same `OccupantSnapshot`; the lattice uses Minecraft's x:26 / z:26 / y:12 packing. |
| R-02 | Do not assume Minecraft, but ship a baseline: 1.21.1 NeoForge. | `src/kernel/java` has no Minecraft/NeoForge/Mojang import; `src/main/java` is the only adapter; a purity gate fails the build on a violation. |
| R-03 | Convert structure information into runtime context, not only detect. Error analysis and structural features (e.g. a huge furnace's interior volume) are context; upgrades are context too. | `Computation`/`Deriver` → `ContextKey<T>` → tier frame; computation reads the snapshot, derivation reads `MatchOutcome` and the context, neither re-walks the structure; every value is typed and absent-able. |
| R-04 | There is a computational centre, but no controller entity is assumed. | `AnchorSpec` declares a role on cells; `OrientationPolicy` selects a placement from anchor candidates; no `Entity` type is required anywhere. |
| R-05 | Support orientation changes: the controller's facing determines the whole structure's orientation; "entity" here is not `net.minecraft.world.entity.Entity`. | The 48-element orientation group (`Turn` × `Tilt` × `Flip`), `Placement`, `Siting`; the placed blueprint is a `MatchResult.Formed` with a `Siting`, not an MC entity. |
| R-06 | Support heteromorphic predicates (a door is the canonical case); rectangles are sufficient. | A `Bay` owns a `Cuboid`, not one cell; an `Expectation` tests the whole bay atomically and may return per-cell marks; no voxel-set predicates. |
| R-07 | Detailed, correct error analysis and logging; rustc is the quality bar. | `Finding` with stable `FindingCode`, `Severity`, primary/secondary `Label`s over `Locus`, notes, help, related findings, deterministic ordering, caps, and an `ExplainBook`. |
| R-08 | Generational computation: different stages need different checks (fast-fail before forming, legality after forming); predicate order matters. | `Stage` (`DESIGN`, `PROBE`, `FORM`, `SETTLE`, selected by the engine), per-stage `CheckPlan`, unit order by keys produced → read-closure volume → declaration index, `ReadSet` + `ChangeSet` + `InvalidationIndex`. |
| R-09 | Data-driven generation of structure segments and definitions. | `Segment` + `BlueprintDocument` + `DataView` + `SegmentLibrary` + `SegmentAssembler` + `SegmentEmitter`; the document grammar is kernel-owned and versioned. |
| R-10 | One large universal kernel; concrete predicates and contexts are extensions or trivial implementations of interfaces. | `Catalogs` freezes extension instances; the kernel ships only `Expectations` combinators and no Minecraft-specific predicate. |
| R-11 | Shared and exclusive claim detection. | `Sharing` (`EXCLUSIVE`, `SHARED`), `ClaimLedger`, per-cell conflict detection; a conflict is a finding, never a silent overwrite. |
| R-12 | Visualization and auto-build, as different remedy kinds serving the same purpose as error analysis and fix. | `Remedy` = `Repair(EditPlan)` \| `Preview(OverlayModel)` \| `ExplainRemedy`; both projections share `Locus` and `Applicability`. |
| R-13 | Compositional API, type-safe. | `Expectation` combinators return `Expectation`; typed `BindingKey<T>`, `ContextKey<T>`, `TraitKey<T>`, `ParameterKey<T>`; sealed `MatchResult` and sealed `Edit` for exhaustive switches. |
| R-14 | Complex where the domain is complex (findings, auto-build), simple for the API user. | The facade has nine operations, counted in `01` and drawn in `15` §3; the builder is fluent; internal phases, prescreen indexes and transaction machinery never leak into the first-use path. |
| R-15 | Write reference code during design to test whether the API is reasonable. | Every module carries a compile-shaped sketch, a use case, and a falsification section; the consolidated sketches live under `src/kernel/java` once approved. |

## 4. Invariants

A module file that contradicts an invariant is wrong even if it is internally consistent.

| Id | Invariant | Enforced by |
| --- | --- | --- |
| I-1 | Nothing under `kernel.**` may reference `net.minecraft`, `net.neoforged`, `com.mojang` or any loader type. | `kernelPurity` scan + `compileKernelJava` (no MC on the kernel classpath) |
| I-2 | No occupant kind is privileged. A block, a block entity, an entity and a nested structure are resolved through the same `OccupantSnapshot` contract. | U2 |
| I-3 | Every footprint is a union of axis-aligned `Cuboid`s. There is no voxel-set arithmetic in the kernel. | U1 |
| I-4 | A match reads only an immutable `WorldView` snapshot; `(blueprint, snapshot, siting, policy)` fully determines the result. | U2, U7 |
| I-5 | A `Siting` is derived from an `AnchorCandidate` plus the blueprint's `OrientationPolicy`; no controller entity is ever required. | U1, U9 |
| I-6 | Every check runs in a `Stage` the engine selects, with its own `CheckPlan`, budget and failure policy, including design-time validation; no author declares a stage and no caller chooses one. | U7, U9 |
| I-7 | Findings are the only result channel. Machine consumers read stable codes and loci, never message text. | U9 |
| I-8 | The kernel ships only trivial implementations; real predicates, derivers, remedies and resolvers are extensions. | U11 |
| I-9 | Detection and context production are separated: a `Computation`/`Deriver` reads the snapshot and context (plus `MatchOutcome` when deriving) and never mutates the match. | U8 |
| I-10 | Every **settled** cell carries a sharing value, and only `settle` settles: a `Formed` is a structure that has already been written. A conflict is a `Finding`, never a silent overwrite. | U4, U7 |
| I-11 | Repair, preview and explanation share one `Remedy` substrate and one `Locus`/`Applicability` vocabulary. | U10 |
| I-12 | A blueprint is a `BlueprintDocument` with a `schemaVersion`, canonicalisable, generatable by external tools, and translatable to host codec objects without a kernel dependency on them. | U6 |
| I-13 | Public kernel APIs are compositional and type-safe: no stringly-typed value channel where a `*Key<T>` can exist; sealed families for closed sets. | U0, all |
| I-14 | No public member throws unconditionally; absence is `@Nullable`, a finding, or an explicit sealed case. | all |
| I-15 | The kernel reads no wall clock, no randomness, no I/O and no global state. Time and cancellation are injected. | U7, U11 |
| I-16 | The ledger receives only derived rows: every row a `settle` writes comes out of the pure planner over the request's `(world, blueprint, siting, owner)` and the plan's bindings, and a plan carries no rows to forge. `settle` checks the bindings structurally (each names a bay of this blueprint, its subjects were observed in this capture, their kinds match, and their cells lie inside the bay — exactly equal for `ONE_SUBJECT`); it does not re-run the expectations, because that verdict is `check`'s and the capture gate pins the world to it. | U4, U7 |

## 5. Units

The kernel has twelve units plus one adapter. Names in this table are the only names a
module may add to; a module that needs a new cross-unit name proposes it in its "Open
questions" section rather than inventing a private synonym.

| Unit | Package | Question it answers | File |
| --- | --- | --- | --- |
| U0 | `kernel.base` | What are ids, budgets, cancellation and stability? | `03-naming.md`, `04-api-doctrine.md` |
| U1 | `kernel.space` | Where is a cell, what is a box, how does a structure turn? | `05-space.md` |
| U2 | `kernel.occupant` | What is in a cell, and what may the kernel read? | `06-occupants.md` |
| U3 | `kernel.expectation` | What does a bay expect, and how do expectations compose? | `07-expectations.md` |
| U4 | `kernel.claim` | Who owns which cells, and which claims conflict? | `08-claims.md` |
| U5 | `kernel.blueprint` | What is a reusable structure, and how is it checked? | `09-blueprints.md` |
| U6 | `kernel.document` | How does a blueprint become data and back? | `10-documents.md` |
| U7 | `kernel.engine` | How does one match run, stage by stage? | `11-engine.md` |
| U8 | `kernel.context` | How does a match become typed runtime state? | `12-context.md` |
| U9 | `kernel.finding` | How is a failure explained like a compiler explains it? | `13-findings.md` |
| U10 | `kernel.remedy` | How do we repair, preview and explain a finding? | `14-remedies.md` |
| U11 | `kernel.spi` | What is frozen, what is extendable, what is the facade? | `15-spi.md` |
| A1 | `mc` | How does 1.21.1 NeoForge bind to the kernel? | `16-adapter.md` |

## 6. Source sets and the purity boundary

```
src/kernel/java      dev.vfyjxf.cumulus.kernel.**   pure Java 21, JSpecify only
src/kernelTest/java  kernel tests                   JUnit 5, no Minecraft
src/main/java        dev.vfyjxf.cumulus.**          mod entry + dev.vfyjxf.cumulus.mc.**
src/main/resources   mod metadata, datapack assets
src/test/java        adapter tests
```

`kernel` is a separate Gradle source set with no Minecraft on its compile classpath. `main`
depends on `kernel` output. The kernel has no mod metadata and no loader entry point. The
adapter has no subpackage per loader or per Minecraft version: the baseline is 1.21.1
NeoForge, and a port changes `main` only. See `02-architecture.md` §6 for the exact Gradle
wiring and the three gates (`compileKernelJava`, `kernelPurity`, `kernelTest`).

## 7. Non-goals

- A loader abstraction layer (Fabric/Forge/NeoForge). One baseline, one adapter.
- A voxel-set world model. Footprints are `Cuboid` unions; a predicate that needs a flood
  fill does it through a budgeted deriver, not through a kernel voxel type.
- A GUI framework. `OverlayModel` is pure data; rendering belongs to the host.
- A recipe/energy/capability system. Those are traits and derivers, not kernel nouns.
- Network synchronisation. The host decides what a client needs; the kernel emits data.
- Migration from ArchitectCumulus. ArchitectCumulus is prior art; its names and code are
  not imported, and no compatibility shim is promised.

## 8. Reading order

1. `01-glossary.md` — the closed vocabulary. Read it once end to end.
2. `02-architecture.md` — units, dependency direction, data flow, threading.
3. `03-naming.md` — the collision ban list and suffix conventions.
4. `04-api-doctrine.md` — composition, sealed results, nullness, the golden example.
5. The unit file you need, then `17-worked-example.md` for the end-to-end walk.

## 9. How this set is falsified

Each module file ends with **Open questions** and **Falsification**. The reviewer's job is
to find a concrete blueprint or world state that makes the module's claims contradict an
invariant or a sibling module. A module is accepted when its sketch compiles against the
frozen names, its worked case runs on paper, and its open questions are either ruled or
explicitly deferred. There is no "implementation-ready" claim in this round.
