# 02 — Architecture

> Answers: which unit may depend on which, how one operation flows, what runs on which
> thread, and where the Minecraft boundary is. Read `00-overview.md` §5 first for the unit
> ids; this file assumes them.

## 1. Two layers, one direction

```
┌────────────────────────────── src/main/java ──────────────────────────────┐
│  dev.vfyjxf.cumulus            mod entry, FML wiring, commands            │
│  dev.vfyjxf.cumulus.mc         MC 1.21.1 / NeoForge 21.1.x adapter (A1)   │
└───────────────────────────────────┬───────────────────────────────────────┘
                                    │ depends on
┌───────────────────────────────────▼───────────────────────────────────────┐
│  dev.vfyjxf.cumulus.kernel.**   pure Java 21, no Minecraft on classpath   │
│  base · space · occupant · expectation · claim · blueprint ·              │
│  document · engine · context · finding · remedy · spi                     │
└───────────────────────────────────────────────────────────────────────────┘
```

The arrow is one-way. The kernel never references an adapter type, not even in a comment
that names one (I-1). The adapter is allowed to use every kernel package.

## 2. Package dependency graph

Edges point from consumer to provider. There is no cycle at package level; where two
concepts seem to need each other, one of them is a declarative id and the other owns the
compiled form (noted below).

```
base ──────────────────────────────────────────────────────────────┐
 ├── space ──────────────────────────────────────────────────────┐  │
 │    ├── finding ────────────────────────────────┐              │  │
 │    ├── occupant ───────────────────────────┐   │              │  │
 │    └── claim ──────────────────────────┐   │   │              │  │
 │                                        │   │   │              │  │
 │  expectation ◄── occupant, claim, finding, space, base         │  │
 │  blueprint   ◄── expectation, claim, space, base               │  │
 │  document    ◄── blueprint, expectation, finding, space        │  │
 │  engine      ◄── all of the above                              │  │
 │  context     ◄── engine, occupant, finding, space              │  │
 │  remedy      ◄── blueprint, occupant, expectation, finding     │  │
 └── spi        ◄── every package above ──────────────────────────┘  │
```

The three deliberate cycle-breaks:

| Tempting cycle | Break |
| --- | --- |
| `blueprint` ↔ `context`: a blueprint owns its stage plans | `CheckPlan` lives in `blueprint` with the blueprint it expands; `Computation` and `StatePhase` live in `context`, `ReadScope` in `base`. |
| `blueprint` ↔ `context`: a blueprint declares derivations | The blueprint stores `DerivationSpec(NamespacedId deriver, Arguments, int order)`; `context` compiles the plan through the catalog. |
| `finding` ↔ `remedy`: a finding carries a suggestion plan | `Suggestion` carries a message, an `Applicability` and an optional remedy id; the concrete `EditPlan` is produced by `kernel.solve`. |

`MatchOutcome`, `BayBinding`, `SeamBinding` and `Census` live in `engine`, not `context`: they
are detection output. `context` consumes them and adds `StructureContext`.

## 3. The flow of one match

```
host code
  │  BlueprintDocument (JSON/SNBT/codec)
  ▼
[document] canonicalise → compile (DESIGN: validate + assemble segments)
  │  Blueprint
  ▼
[space]    resolve anchor candidate + OrientationPolicy → Siting
  │
  ▼
[engine]   MatchRequest(blueprint, siting, lease, policy, budget,
                        pins, record, owner, ticket)
  │
  ├─ DESIGN  no view; document and geometry checks
  ├─ PROBE   bounds, census, anchor fingerprint; cheap rejection
  ├─ FORM    bays in cost/specificity/declaration order; claims deferred
  ├─ SETTLE  ledger conflicts, seams, legality; the write is the entry point's, not the stage's
  │
  └──► two entry points, one writer
         check  ──► Admitted(planned, ledgerRevision)            never writes
         settle ──► Formed(outcome, claims, findings)             writes the claims
                        │
                        ▼
                   [context] Computation/Deriver → ContextKey → StructureContext + RuntimeState
         settle ──► Failed / Undecided / Dropped(findings)
           │
           ▼
        [finding] bundle → human / JSON / SARIF renderer
           │
           ▼
        [remedy] solve(finding) → Repair(EditPlan) | Preview(OverlayModel) | Explain
```

Nothing in the engine writes the world. `EditExecutor` is the only write path, and it is a
host SPI called after `PlanValidator` has approved a plan.

## 4. Stages and ordering

`Stage` is the unit of "what needs checking now", not a time unit.

| Stage | Reads world | Typical plan | Fast-fail contract |
| --- | --- | --- | --- |
| `DESIGN` | no | schema, references, geometry, anchor roles, claim overlaps, derivation cycles | every error is `ERROR`; no `UNREADABLE` can occur |
| `PROBE` | yes, coarse | `BOUNDS`, `CENSUS`, `ANCHOR` | a cheap miss returns `Failed` without opening a bay |
| `FORM` | yes, fine | `BAY`, `SEAM` | every check runs; a bay's reply is one `Reply`, never a partial pass |
| `SETTLE` | yes, ledger | `CLAIM`, legality, derivation preconditions | conflicts are findings; only `settle` writes the claims and produces `Formed`, and a conflict there is `Failed(SC-CLM-0002)` with nothing written |

A targeted re-check is not a stage: `check` with a `ChangeSet` on the request re-runs the steps whose
`ReadSet` intersects it, and the rest reuse their prior verdict.

Ordering inside a stage is a total order: section computations, the barrier, aggregate
computations in Kahn order over `inputs()`, then checks — each group in declaration order.
The total order is part of the determinism contract (I-4, I-7).

`Stage` is in `base` because findings record the stage that produced them; `CheckPlan` is in
`blueprint` because `expand(S)` produces it from what the blueprint declares.

## 5. Threading and determinism

| Concern | Rule |
| --- | --- |
| Capture | `LevelCapture.capture` runs on the server thread only; it copies 16³ sections (`PalettedContainer.copy`) into an immutable `LevelSnapshot`; unloaded chunks are `NOT_CAPTURED`, never air. |
| Matching | Pure over the snapshot; runs on a host worker. No world access, no ledger writes, no `ThreadLocal`, no clock. `StructureKernel.check` is synchronous and writes nothing; `settle` is the only writer, and the host scheduler owns the executor (`20`). |
| Scheduler | One `MatchScheduler` per server in `ServerState`; one in-flight task per `(world, owner)`; newer requests coalesce; settle and callbacks run on the server thread. |
| Ledger | Frozen at dispatch (`FrozenLedger`) and read-only during a match; settlement is a server-thread CAS after the capture gate (`20` §5.4). |
| Snapshot store | Per-world `SectionStore` of immutable `SectionSnapshot`s; snapshots hold the strong refs, the store is a weak cache, and a section is never lazily resolved mid-check. |
| Dirty state | `LevelChunk.setBlockState` / `Level.blockEntityChanged` / entity/chunk events mark section or payload; a bounded one-shot refresh runs on the server thread before dispatch. |
| Derivation | Pure over `MatchOutcome` + snapshot + frozen ledger; may run on the same worker. A retained `InstanceRecord` drops the view. |
| Findings | Immutable; a sink may be called from one thread only. |
| Budget | A coarse resource cap on two legs: `cells` and `sections`. No per-unit price, no `Budget.nanos`, no `TimeSource`; a host watchdog may cancel via `CancelToken`. |
| Randomness | none. |
| Iteration | Any public `List`/`Set` with a defined order is built with that order; `HashMap` iteration never reaches a result. |

Determinism statement: for fixed `(blueprint, snapshot, siting, policy, catalog snapshot,
frozen ledger, cancellations)`, the `MatchResult`, the `FindingBundle` order and the `Spend` are
identical across runs and JVMs. Thread choice, queue order and the size of any refresh do not enter
the tuple.

## 6. Source-set layout and build gates

The project is one Gradle module with two production Java source sets (`kernel`, `main`) plus
their test source sets (`kernelTest`, `test`) in this design round. The kernel
is not a separate published artifact yet; it is a source set with **no Minecraft on its
compile classpath**, which is stronger than a package convention.

```
StructureCumulus/
├── src/kernel/java      dev.vfyjxf.cumulus.kernel.**      pure Java 21
├── src/kernelTest/java  kernel unit tests                 JUnit 5 only
├── src/main/java        dev.vfyjxf.cumulus.**             mod + mc adapter
├── src/main/resources   META-INF/neoforge.mods.toml, assets, data
└── src/test/java        adapter tests
```

Gradle wiring (target shape; the build file is updated when the first reference code
lands):

```groovy
sourceSets {
    kernel {
        java.srcDir 'src/kernel/java'
    }
    kernelTest {
        java.srcDir 'src/kernelTest/java'
        compileClasspath += sourceSets.kernel.output
        runtimeClasspath += sourceSets.kernel.output
    }
}

dependencies {
    kernelCompileOnly 'org.jspecify:jspecify:1.0.1'
    kernelTestImplementation 'org.junit.jupiter:junit-jupiter:5.7.1'
    implementation sourceSets.kernel.output
}

tasks.register('kernelPurity') {
    // scan src/kernel/java; fail on net.minecraft / net.neoforged / com.mojang imports
}

tasks.register('kernelTest', Test) {
    testClassesDirs = sourceSets.kernelTest.output.classesDirs
    classpath = sourceSets.kernelTest.runtimeClasspath
}
```

Two gates, both on `check`:

1. **`compileKernelJava`** — the compiler itself proves no Minecraft type is reachable.
2. **`kernelPurity`** — a textual scan catches a fully-qualified name or an import in a file
   that is not compiled on this machine. The scan is the backstop, not the primary gate.

A third gate, `kernelTest`, runs the purity tests (space algebra, orientation group,
combinator laws, finding ordering) without starting Minecraft. `main`'s `test` covers the
adapter and the GameTest harness.

The kernel has no `@Mod`, no `neoforge.mods.toml`, no service file that names a loader. The
facade is constructed by the adapter's mod entry point.

## 7. Extension points

The complete inventory. Every one is an interface or a record of interfaces; the kernel
ships only trivial implementations (I-8).

| Point | Interface | Owner | Typical implementation |
| --- | --- | --- | --- |
| World capture | `WorldCapture` | host | `LevelCapture` |
| World view | `WorldView` | host | `LevelSnapshot` |
| Occupant resolution | `OccupantResolver` | host | `WorldOccupants` |
| Expectation | `Expectation` / `ExpectationFactory` | addon | block, trait, door, hatch predicates |
| Claim ledger | `ClaimLedger` | host | `SavedClaims` |
| Deriver | `Deriver` | addon | interior volume, tier, census |
| Remedy provider | `RemedyProvider` | addon | auto-build, overlay, explain |
| Plan execution | `EditExecutor` | host | `WorldEdits` |
| Finding sink | `FindingSink` | host | logger, chat, JSON |
| Data translation | `DataBridge<T>` / `DataFrontend` | host/addon | codec, JSON, SNBT |
| Segment generation | `SegmentEmitter` | external tool | datapack generator |

Catalogs freeze instances at `StructureKernel.create(Catalogs)`. Adding an extension after
that point requires a new kernel; a kernel holds no mutable registry.

## 8. What the adapter must not do

- It must not teach the kernel about blocks. `OccupantDescriptor` is the only shape the
  kernel sees; a block state is an adapter-side raw value.
- It must not move a policy into the adapter. Orientation, claim policy, stage plans and
  finding ordering are kernel data; the adapter only realises them.
- It must not hide an unreadable cell. A chunk not loaded, a permission-denied read and a
  snapshot bound are `UNREADABLE`, never "air" (see `06-occupants.md` §5).
- It must not mutate the world from a matcher or a deriver. Only `EditExecutor` writes.

## 9. Cross-unit review rules

1. A module may import only packages above it in §2, plus `base`.
2. A cross-unit name is defined in `01-glossary.md`; a new one is proposed, not coined.
3. A module's sketch uses only frozen names; a name it needs but does not own is a
   dependency note, not a private duplicate.
4. A module that finds a missing concept reports it in **Open questions** and stops; it does
   not silently widen its scope.
