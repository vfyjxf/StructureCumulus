# StructureCumulus — Design Review 1.2

An interactive, adversarial review of the kernel design. Not a feature tour: each chapter is a claim
the kernel must survive — a frame mapping, a write, a conflict, a missing value, a finding.

The design of record is [`../docs/README.md`](../docs/README.md). The adapter is complete through the
H1–H4 host-data round, the T1–T3 trait-provider round, the N1 nested-extent lookup round and the
S5/S6 structure-set close-out. M1 is the host, `/cumulus check <id> <origin>` and the capture-check
path (`LevelCapture`, `LevelSnapshot`, `LevelLease`, block/block-entity occupant descriptors,
`ServerState`, in-memory `BuiltinDocuments`);
M2a adds loaded-entity capture with policy windows, full footprint indexing and entity-vs-block
precedence; M2b adds upright orientation mapping
(`Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror`, mirror-then-rotate); M2c adds Model C —
body identity, the total read with `SC-OCC-0004`, partial sections and four-state traits; M2d adds the
nested capability (`NestedExtent`/`NestedExtents`, frames, cuts, child entities); M3 adds
`SavedClaims`/`SavedContexts` at `d4b6d82`. M4a JSON (`JsonReader`/`JsonWriter`) is at `f2659c7`, M4b
datapack reload at `76a8e5c`, and the command slice (settle at `4899d86`, plus compile, explain,
claims) is green. M5a's watched-region mark counter, M5d's non-upright pitch/roll mapping and M5e's
capture report (D-28) are complete, and the determinism gate (`DeterminismTest`) covers canonical
bytes, the parse/write fixed point, equal compile runs and stable finding order.

S5 ships the production set command (`/cumulus set check`, `/cumulus set settle`), its one union
capture over the root and satellite bounds, the check-only host path (`StructureSetHost.check`), the
built-in `set_demo` set (`set_root`/`set_wing` and the `ServerState.findSet` fallback) and the
real-level `StructureSetHostGameTest`/`SetCommandGameTest` group (six methods). The set capture is
unsited and runs no orientation audit, so a satellite cell does not get a spurious `SC-MC-0004`. S6
closes the set layer: the A+ same-siting rejection `SC-SET-0008` at the compiler, `StructureSet.place`
and the host check; `ContextStore.remove` with rollback invalidation of every formed structure; and
one set budget shared by presence and every member attempt (D-35). The S6 follow-up adds two
real-level cases: `aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence` and
`aOneCellSetBudgetLeavesTheRootCheckUndecided`.

The current layers are the typed host snapshot (D-33, implemented as H1–H4, superseding the old
sited-root descriptor projection D-29), the derived envelope (D-30), the host-owned cached-context
port (D-31), the structure-set layer (D-32: root/satellites, links, presence, the S5 command and its
one unsited capture; D-35: same-siting rejection, `ContextStore.remove`, one shared set budget) and
the generic per-holder providers (D-34: `TraitProvider<T, H>`, `TraitHolder`,
`CapabilityTraitProvider`, direct `IItemHandler`/`IEnergyStorage`/`IFluidHandler`;
`blockEntityNbt`/`entityNbt` are removed and there is no item holder; D-36:
`NestedExtentHolder`/`NestedExtentProvider`/`NestedExtentLookup`, one
`CapabilityNestedExtentProvider`, the standard `NestedExtentProviders.block`/`entity`; the old
aggregate `mc.NestedExtentLookup` and `CapabilityLookup` are deleted). `OccupantDescriptor` is
`(type, kind)` and carries no properties or tags; a property/state/tag/fluid check is an adapter
factory over `Expectations.host`.

261 kernel tests (39 containers), 296 adapter unit tests (37 containers) and 71/71 GameTests are green
at `f84dba8`; a full `./gradlew check` and `runGameTestServer` pass there. The UI opens in **中文**;
the **EN** button in the top bar switches to the English reading layer. Technical identifiers, code,
JSON, Java and finding codes stay in their original form in both languages.

## Run

```bash
cd visual
npm install
npm run dev       # http://127.0.0.1:5173
npm run build     # tsc --noEmit + production build
npm run preview
```

The app uses React, Vite, TypeScript, CSS and SVG only. No router, chart library, remote font or
network request.

## What is inside

261 kernel tests in 39 containers, 296 adapter unit tests in 37 containers, 71 GameTests, 65 codes,
36 decisions and 11 ranked kernel packages. One complete document and a Java builder excerpt build
the same campfire blueprint.

| Chapter | What you can do |
| --- | --- |
| **00 / Thesis** | Read the five laws; click a deleted concept to see its replacement. |
| **01 / Lattice** | Trace a package's imports through the rank graph; inspect its law and source file. |
| **02 / Object** | Walk `WorldView → Bay → Check → Blueprint → MatchOutcome`; see the typed host read (`HostType`/`WorldView.get`) and switch a key between `MATCH` and `INSTANCE`. |
| **03 / Workbench** | Inspect the campfire shell/hollow/fire, switch bays, explode/assemble, step `COMPILE → CHECK → SETTLE` (then the derive aftermath), toggle JSON/Java, and move the siting through origin and quarter turns; the `property` leaf reads the typed snapshot. |
| **04 / Ledger** | Move two owners' bays, change `EXCLUSIVE`/`SHARED`, settle, race the revision, and see the conflict matrix and rows; the set card records host orchestration (D-32), the S5 command and its unsited union capture (no orientation audit), and S6's same-siting rejection, shared budget and context removal (D-35), including the real-level same-siting and one-cell-budget cases. |
| **05 / Fold** | Refuse a section, change `sum`/`max`, toggle `whenAbsent`, and see the key become absent and a derive skip; the cached-context card records the host port (D-31). |
| **06 / Codes** | Search/filter all 65 codes, inspect a code's producer, and play with the finding-bundle caps and truncation tail; the side panel lists the adapter-only `SC-MC-0006`…`0010` codes. |
| **07 / Proof** | Read the gate report, 19 falsifiers (including the S5 command, the S6 same-siting, shared-budget and context-removal claims, and the two real-level S6 cases), the 36 decisions, the current D-30…D-36 layers, the regeneration protocol and the document index. |

Press <kbd>⌘K</kbd> / <kbd>Ctrl K</kbd> for the index. Use **EN / 中文** in the top bar to switch languages. The **NIGHT** switch flips the same
information to a dark instrument palette. The layout is responsive down to 390 px.

## Data is generated, not hand-maintained

`codes`, `decisions` and `tests` are projected from the design set and the kernel test sources:

```bash
cd visual
npm run data
```

The generated counts are 65 codes (13 kernel families, including `SET` with `SC-SET-0008`), 36
decisions (D-36) and 261 kernel tests in 39 containers. `campfire.ts` is the curated worked example
(3×3×3, 27 cells, 27 claims, spend 28 reads / 4 sections, derive capacity 10). The numbers match the
JUnit scenario.

## Historical note

This replaces the 2026-10-21 NASA-punk atlas. That atlas documented design v0.2 and is kept in git
history; this app documents design v1.2 and the 08–11 extension documents, and is the one to run.

## Caveat

The review app, the kernel's direct `javac`/JUnit harness, `./gradlew check`, the adapter's 296 unit
tests and its 71 GameTests (`runGameTestServer` 71/71) are green at `f84dba8`. The PNGs under
[`review/`](review/) were captured before this copy update; the repository ships no screenshot
tooling, so they were not regenerated. No screenshot server was started for this refresh.
