# 00 — Requirements

> What the library is for, what it guarantees, and what it deliberately refuses. A design decision
> that serves none of these requirements is out of scope.

## 1. The one-sentence contract

> A **blueprint** is tested against an immutable **snapshot** at a **siting**, producing a sealed
> **result**, a typed **context**, and — only through the writing entry point — a **claim** over the
> world cells the structure occupies.

Everything else is a consequence of that sentence.

## 2. The three audiences

| Audience | What they write | What they never see |
| --- | --- | --- |
| **Structure author** | a `Blueprint` (Java) or a `Document` (JSON) | a session, a plan, a lease, a claim row, a step order |
| **Addon author** | `Expectation`, `Focus`, `Computation`, `Deriver`, `RemedyProvider`, typed keys | the engine loop, the fold, the ledger CAS, the finding bundle's ordering |
| **Host / adapter** | `WorldCapture`, `WorldView`, `SnapshotLease`, `ClaimLedger`, `ContextStore`, document readers/writers | how a blueprint is expanded or a bay is judged |

The first-use path is nine lines of Java (a compile, a check, a switch) and one JSON file. The
extension path is one interface and one catalog registration. The host path is four ports.

## 3. Functional requirements

| Id | Requirement | Mechanism |
| --- | --- | --- |
| **F-01** | An element is not assumed to be a block. Blocks, block entities, entities and nested structures answer through one `OccupantSnapshot` shape. | `world`: `OccupantKind`, `OccupantSnapshot`, `WorldView`. |
| **F-02** | The kernel is version-independent. No `net.minecraft`, `net.neoforged` or `com.mojang` name appears under `kernel.**`. | `kernel` source set has no Minecraft on its classpath; `kernelPurity` scans text. |
| **F-03** | A match produces typed runtime context, not only a boolean. | `context`: `ContextKey<T>`, `Computation`, `Deriver`, `StructureContext`. |
| **F-04** | No controller entity is required. The orientation comes from a declared anchor property at the definition origin. | `structure`: `AnchorSpec`, `OrientationPolicy`; `space`: 48 orientations, `Siting`. |
| **F-05** | A bay is a rectangular region tested atomically; a door is the canonical two-cell case. | `structure`: `Bay`, `Focus`, `Expectation<DoorView>`, `Coverage.ONE_SUBJECT`. |
| **F-06** | Claims are explicit: shared vs exclusive, per cell, with one conflict predicate and one compare-and-set. | `claim`: `Sharing`, `ClaimRow`, `ClaimMatrix`, `ClaimLedger`. |
| **F-07** | Checking and claiming are separate entry points. `check` writes nothing; `settle` is the only writer. | `engine`: `check`, `settle`; `spi`: `StructureKernel`. |
| **F-08** | Structure definitions are data-driven and code-driven at once: one IR, two frontends. | `document`: `Document`, `BlueprintSpec`, `DocumentCompiler`; `structure`: `BlueprintBuilder`. |
| **F-09** | Findings are compiler-grade: stable codes, severity, located labels, notes, suggestions, deterministic order. | `finding`: `Finding`, `FindingBundle`, `Codes`, `FindingRenderer`. |
| **F-10** | Extensions are values frozen in catalogs; the kernel ships only trivial implementations. | `spi`: `Catalogs`, `Catalog<T>`; `structure`/`context` factories. |
| **F-11** | Repair, preview and explanation are values over the same findings and loci. | `remedy`: `Remedy`, `EditPlan`, `OverlayModel`; no execution in the kernel. |
| **F-12** | Every match is deterministic, budgeted and cancellable. No clock, randomness, I/O or global state. | `core`: `Budget`, `Spend`; `engine`; `context`. |

## 4. Non-functional requirements

| Id | Requirement |
| --- | --- |
| **N-01** | A value is immutable; a public collection is an immutable copy with a defined order. |
| **N-02** | A structural failure is a value (`Reply`, `MatchResult`, `Outcome`), never an exception. An exception means a programmer precondition was violated. |
| **N-03** | Absence, unreadability and refutation are three distinct answers. Merging any two is a defect. |
| **N-04** | All iteration that can reach a result is over a defined order; no hash order, no identity hash. |
| **N-05** | The kernel has no static mutable state; a kernel is a value created from frozen catalogs. |
| **N-06** | A public type is reachable from a worked example or a host port. A type used only by the engine is package-private. |
| **N-07** | Every finding code has exactly one producer and one explanation; an unproduced code is deleted. |
| **N-08** | The design is the source: a public signature in code that is not in a module file is a regeneration defect. |

## 5. Invariants

These are the falsifiable statements. A module that contradicts one is wrong even if it is
internally consistent.

| Id | Invariant | Enforced by |
| --- | --- | --- |
| **I-01** | Nothing under `kernel.**` names Minecraft, NeoForge or Mojang. | `compileKernelJava` (no MC on classpath) + `kernelPurity` scan. |
| **I-02** | A match reads only an immutable `WorldView`; `(blueprint, snapshot, siting, owner)` fully determines the verdict. | `engine` signature; `world` has no live handle. |
| **I-03** | Every footprint is a `CuboidSet`; no voxel enumeration in the kernel. | `space`; `CuboidSet` has no cell stream. |
| **I-04** | `check` writes nothing: no claim, no context frame, no world edit. | `engine.check` has no writer parameter; `StructureKernel.check` has no ledger write. |
| **I-05** | `settle` is the only claim writer; `Formed` exists only after `Settled`. | `claim` API; `engine.settle`. |
| **I-06** | Every claim row is derived by the pure planner from `(blueprint, siting, owner, bindings)`; a caller cannot hand in rows. | `ClaimPlanner`; `Admitted` carries bindings, not rows. |
| **I-07** | A `MISMATCH` is refutation, `UNREADABLE` is ignorance, `ABSTAIN` is refusal; they do not collapse. | `Reply` four-state family; `MatchResult` fold. |
| **I-08** | Findings are the only report channel; a machine consumer reads a code and a locus, never a message. | `Finding` shape; renderers are pure projections. |
| **I-09** | Detection and context production are separate: a `Computation` reads the snapshot, a `Deriver` reads the result and context, neither mutates the match. | `context` interfaces; `derive` takes a `Formed`. |
| **I-10** | A blueprint declares no world cell, siting, entity, clock, token or finding. | `structure` types; no such fields. |
| **I-11** | The kernel ships no I/O, clock, randomness or host handle; time and cancellation are injected. | `core`/`engine`; `CancelToken` is a host-supplied value. |
| **I-12** | Two instances publishing one key never collide; context is per instance. | `InstanceId` key in `ContextStore`; `StructureContext`. |
| **I-13** | A document and a Java builder produce equal blueprints for equal content. | `document` compile tests; `Blueprint` structural equality. |
| **I-14** | Errors are never capped; only warnings and info may be elided, and the elision is counted. | `FindingBundle` pipeline. |
| **I-15** | The design set is the only source of public shape. | `07-regeneration.md` gates. |

## 6. Non-goals

- A loader abstraction (Fabric/Forge/NeoForge). One baseline, one adapter.
- A voxel world model. A predicate that needs a flood fill writes a budgeted `Computation`, not a
  kernel voxel type.
- A GUI framework. Overlays are pure data; rendering is the host's.
- A recipe/energy/capability system. Those are traits and context keys.
- Network synchronisation. The host decides what a client needs.
- Compatibility with the earlier ArchitectCumulus names or APIs.
- A second translation of this design set. English is the source; a guide may cite it.

## 7. What changed from the earlier requirement set

The earlier set had fifteen requirements and sixteen invariants, with several restatements of the
same mechanism. This set keeps the substance and removes the duplicates:

- R-03 and R-08's "stages" are now the three phases of one operation: compile, check, settle. No
  unit declares a stage.
- R-09's segment pipeline is now the document IR; `Segment` is no longer a runtime type.
- R-12's remedy machinery is now a value SPI; execution stays in the host.
- R-15 ("write reference code during design") is now the regeneration protocol, not a requirement on
  every module.
