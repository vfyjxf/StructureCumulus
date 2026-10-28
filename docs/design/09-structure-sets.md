# 09. Structure sets and satellites

> Status: implemented (S1, S2, S3, S4 and S5); the H4 host-data round changes no set-layer rule. This
> document remains the source of truth for the structure-set layer; the document IR, JSON
> reader/dispatcher/writer, `StructureSetCompiler`, the host plan, the host orchestration, the SS4
> stress suite and the S5 command/capture path are on the tree. Visual review stays last and was not
> regenerated.

## 1. Purpose

A `Blueprint` is one structure: one bounds, one siting, one capture, one plan, one claim set.

Real multiblocks are often more than one structure:

- a blast furnace with optional preheaters on its faces;
- a reactor with solar generators anywhere within a range;
- a controller with a logical subnetwork;
- a wide attachment that touches several parent cells.

The structure-set layer describes those relationships without turning the kernel into a
multi-structure engine.

```text
StructureSet
  root:      one Blueprint, placed by the host
  satellites: zero or more Blueprints, placed relative to the root
  links:     optional spatial relationships between root cells and satellite cells
```

The kernel still checks and settles one blueprint at a time. The host owns capture, orchestration,
rollback, and claim/context lifecycle for the set.

## 2. Terms

| Term | Meaning |
| --- | --- |
| set | a `structure_set` document or Java `StructureSet` |
| root | the primary structure; placed by the host |
| satellite | an independent structure placed relative to the root |
| parent cell | a **local root cell** used as a link endpoint |
| satellite cell | a **local satellite cell** used as a link endpoint |
| face link | a link whose endpoints are face-adjacent (Manhattan distance 1) |
| range link | a link whose endpoints are within `max_distance` under a metric |
| presence | whether a satellite region contains at least one non-air block |

`parent_cell` is not a world coordinate, a bay id, or an `InstanceId`. It is a local cell in the
root blueprint, mapped to the world through the root siting.

## 3. Document kind

The existing blueprint document stays unchanged. A new root member `kind` discriminates the
document:

```json
{
  "schemaVersion": 1,
  "kind": "structure_set",
  "id": "mymod:blast_furnace_set",
  "root": "mymod:blast_furnace",
  "satellites": []
}
```

- `kind` is optional on blueprint documents; absence means `blueprint`.
- `kind: "structure_set"` selects the structure-set reader.
- `schemaVersion` stays `1`.
- The set document is root-only; definitions never carry a structure set.
- Old readers reject a set document with `SC-DOC-0002`; this is a pre-release forward break.

## 4. JSON schema

### 4.1 Set document

| Member | Required | Default | Meaning |
| --- | --- | --- | --- |
| `schemaVersion` | yes | — | exactly `1` |
| `kind` | yes | — | `structure_set` |
| `id` | yes | — | set id |
| `root` | yes | — | root blueprint id |
| `satellites` | yes | — | array, may be empty |
| `extensions` | no | `{}` | document-level extensions |

Unknown members are rejected with `SC-DOC-0002`.

### 4.2 Satellite

| Member | Required | Default | Meaning |
| --- | --- | --- | --- |
| `id` | yes | — | unique within the set |
| `blueprint` | yes | — | blueprint document id |
| `at` | yes | — | offset in the root local frame |
| `orientation` | no | `identity` | orientation relative to the root |
| `required` | no | `false` | whether absence is an error |
| `arguments` | no | `{}` | shallow override of the blueprint's arguments |
| `presence` | no | satellite bounds | presence region override |
| `links` | no | `[]` | link pairs |

### 4.3 Presence

```json
"presence": { "cell": [0, 0, 0] }
```

or:

```json
"presence": { "boxes": [[0, 0, 0, 2, 1, 2]] }
```

- exactly one of `cell` / `boxes`;
- must be inside the satellite bounds;
- omitted means the whole satellite bounds.

### 4.4 Links

`links` is an array. Each link is one pair:

```json
"links": [
  {
    "mode": "face",
    "parent_cell": [2, 0, 1],
    "satellite_cell": [0, 0, 1]
  },
  {
    "mode": "range",
    "parent_cell": [2, 0, 1],
    "satellite_cell": [0, 0, 0],
    "max_distance": 8,
    "metric": "chebyshev"
  }
]
```

| Member | Required | Default | Meaning |
| --- | --- | --- | --- |
| `mode` | yes | — | `face`, `range`, `none` |
| `parent_cell` | yes for face/range | — | local root cell |
| `satellite_cell` | yes for face/range | — | local satellite cell |
| `max_distance` | yes for range | — | non-negative integer |
| `metric` | no | `chebyshev` | `chebyshev`, `manhattan`, `euclidean` |

`mode: none` has no cells and no distance; it is a logical link only. Omitting `links` is the same
as no spatial link. A reader rejects a member that the chosen mode does not use (`SC-DOC-0002`):
`max_distance`/`metric` on `face`, and any cell or distance on `none`.

### 4.5 Canonical example

```json
{
  "schemaVersion": 1,
  "kind": "structure_set",
  "id": "mymod:blast_furnace_set",
  "root": "mymod:blast_furnace",
  "satellites": [
    {
      "id": "left_preheater",
      "blueprint": "mymod:preheater",
      "at": [-1, 0, 0],
      "orientation": "identity",
      "required": false,
      "arguments": { "tier": 2 },
      "links": [
        {
          "mode": "face",
          "parent_cell": [2, 0, 1],
          "satellite_cell": [0, 0, 1]
        }
      ]
    },
    {
      "id": "right_preheater",
      "blueprint": "mymod:preheater",
      "at": [3, 0, 0],
      "orientation": "half",
      "required": false,
      "arguments": { "tier": 3 },
      "links": [
        {
          "mode": "face",
          "parent_cell": [2, 0, 2],
          "satellite_cell": [0, 0, 1]
        }
      ]
    },
    {
      "id": "solar",
      "blueprint": "mymod:solar_generator",
      "at": [8, 0, 0],
      "required": false,
      "links": [
        {
          "mode": "range",
          "parent_cell": [2, 0, 1],
          "satellite_cell": [0, 0, 0],
          "max_distance": 8,
          "metric": "chebyshev"
        }
      ]
    }
  ]
}
```

## 5. Placement

The host places the root:

```java
Siting rootSiting = Siting.of(origin, orientation);
```

A satellite's relative siting is:

```java
Siting relative = Siting.of(LatticePoint.of(atX, atY, atZ), relativeOrientation);
```

Its absolute siting is:

```java
Siting satelliteSiting = rootSiting.compose(relative);
```

`Siting.compose` is a new convenience over the existing `Placement.compose`:

```text
origin = root.toWorld(relative.origin)
placement = root.placement().compose(relative.placement())
```

- `at` is an origin in the root local frame;
- `orientation` is relative to the root;
- the root rotates, satellites rotate with it;
- a null mapping (lattice overflow) is a set finding, not a crash; the compiler reports `SC-SPC-0001`.

The set document never stores world coordinates. It is a portable placement graph.

## 6. Links

### 6.1 Face

```text
Manhattan distance(parentWorldCell, satelliteWorldCell) == 1
```

- same world cell is rejected;
- diagonal is rejected;
- the direction is one of the six faces of the parent cell.

### 6.2 Range

```text
distance(parentWorldCell, satelliteWorldCell) <= max_distance
```

Metrics:

| Metric | Distance |
| --- | --- |
| `chebyshev` | `max(|dx|, |dy|, |dz|)` |
| `manhattan` | `|dx| + |dy| + |dz|` |
| `euclidean` | `sqrt(dx² + dy² + dz²)` |

`euclidean` compares squared integers to avoid floating point.

### 6.3 Coverage

A link cell must be read by a check:

```text
parent_cell ∈ root.plan() checks' read scopes
satellite_cell ∈ satellite.plan() checks' read scopes
```

Bay membership is optional; it only adds claims. A bay that no check reads does **not** satisfy
coverage.

This rule exists because `Blueprint.reads()` is the union of check scopes and computation regions;
bounds-only validation would make the link inert.

### 6.4 Duplicate parent face

For `face` links, the pair:

```text
(parent_cell, face direction)
```

must be unique across the whole set.

- one parent cell may have up to six face links, one per direction;
- two satellites may not share the same parent cell and the same face;
- one satellite may have several face links, provided their parent cells differ;
- a wide satellite uses one link pair per contact cell.

`range` and `none` links do not have a face direction and may fan in to one parent cell.

### 6.5 Wide satellite

A satellite whose port spans several cells declares several link pairs:

```json
"links": [
  {"mode": "face", "parent_cell": [0,0,0], "satellite_cell": [0,0,0]},
  {"mode": "face", "parent_cell": [1,0,0], "satellite_cell": [1,0,0]},
  {"mode": "face", "parent_cell": [2,0,0], "satellite_cell": [2,0,0]}
]
```

All pairs are validated independently.

## 7. Presence

### 7.1 Definition

A satellite is present when its presence region contains at least one non-air block-family
occupant:

```text
(OccupantKind.block || OccupantKind.blockEntity) && !OccupantSnapshot.isAir()
```

`blockEntity` is a block that also carries a block entity; it is still a block for presence.
Entities, fluids, and nested bodies do not prove presence.

### 7.2 Region

- default: the satellite bounds;
- override: `presence.cell` or `presence.boxes`;
- the override must be inside the satellite bounds.

### 7.3 Reading and budget

Presence is read through `WorldView.read`:

- every cell is charged against the run budget's `cells` ceiling, which the host tracks separately
  from the per-structure attempt budgets;
- an unreadable cell makes presence unprovable;
- presence never bypasses the engine budget.

The host seeds one presence ceiling from `Budget.cells()`; each structure check is a separate kernel
attempt and receives the full `Budget`, so a set's total reads can exceed `budget.cells()`
(characterized in `06-verification.md` §3.3).

### 7.4 Required / optional

| `required` | Present | Absent | Unknown |
| --- | --- | --- | --- |
| `true` | check satellite | `SC-SET-0001` | `SC-SET-0004` |
| `false` | check satellite | skip | `SC-SET-0004` |

Unknown is never treated as absent, even for optional satellites.

## 8. Arguments

```json
"arguments": { "tier": 3, "material": "invar" }
```

- optional;
- same `Value` schema as blueprint arguments;
- effective arguments:

```text
blueprint document root arguments
  <- shallow merge
satellite.arguments
```

- satellite wins on duplicate keys;
- no nested merge;
- no key removal.

Compilation:

- same blueprint with different effective arguments compiles separately;
- cache key is `(blueprintId, effectiveArguments)`;
- same blueprint and same effective arguments may reuse a compiled `Blueprint`, but each satellite
  still gets its own id and siting.

## 9. Identity and duplicates

- satellite ids are unique within the set; duplicate -> `SC-DOC-0004`;
- `(at, orientation)` pairs are unique within the set; duplicate -> `SC-SET-0005`;
- the same blueprint may appear several times with different ids/arguments;
- different sitings may overlap in bounds; claims decide whether that is legal;
- nested structure sets are not supported; a satellite must name a blueprint document.

## 10. Host orchestration

### 10.1 Check all

The host checks, in order:

1. presence of every satellite, required ones first and each group in declaration order (an absent
   required satellite is `SC-SET-0001`; an unreadable one is `SC-SET-0004`);
2. link distance/coverage;
3. root check;
4. every present satellite check.

Any failure means no settle. The host maps both link cells through the composed sitings before
measuring them: a null mapping is `SC-SPC-0001`, an endpoint outside its blueprint or outside every
check's read scope is `SC-SET-0003` (re-checked defensively), and a wrong face/range distance is
`SC-SET-0002`. Link validation covers every satellite, present or not; an absent optional
satellite only skips its check and settle.

### 10.2 Settle all

If every check passed, settle in a fixed order:

```text
root, satellites in declaration order
```

Each settle is an independent kernel `settle`.

### 10.3 Rollback

If a settle fails:

- release successfully settled structures in reverse order;
- report the original settle failure;
- report rollback failures with `SC-SET-0007`;
- do not auto-retry.

`release(owner, siting)` is coarse and advances the ledger revision. Kernel context is not rolled
back, and `ContextStore` has no removal operation: a rolled-back structure keeps its published
context, so the host owns whatever invalidation policy it applies (`06-verification.md` §3.3).

### 10.4 Result

The set result is per structure, not one merged `MatchOutcome`:

```java
record StructureSetResult(
    Map<NamespacedId, MatchOutcome> outcomes,
    List<Finding> setFindings,
    List<NamespacedId> settled,
    List<NamespacedId> rolledBack,
    List<Finding> rollbackFailures,
    Map<NamespacedId, List<ClaimRow>> claims
) { }
```

`outcomes` is keyed by the root blueprint id and each satellite id; it holds every admitted check,
including a structure later rolled back. `setFindings` holds the presence/link findings and every
check/settle result's findings (a formed structure's warnings included). `claims` holds the rows each `Formed` structure wrote,
filtered by `(owner, siting, bay)`; the ledger row set is ledger-wide and a `Formed.claims()` may
carry rows of other structures. `settled` is the settle order and `rolledBack` is the release
order. Rollback releases claims only: the kernel context published by a rolled-back `settle` stays,
and `ContextStore` exposes no removal, so the result names every id in `rolledBack` for the host's own
invalidation policy.

### 10.5 Production command and one capture (S5)

The production host driver is the pair of subcommands:

```text
/cumulus set check  <id> <origin>
/cumulus set settle <id> <origin>
```

Both resolve the set document, compile it, resolve the root siting, place the set, **capture once**
and then either run `StructureSetHost.check` or `StructureSetHost.run` with `Budget.unbounded()`, the
server owner and one instance per checked structure.

- The root siting is identity at the origin when the root has no anchors, and the minimal anchor
  capture otherwise (`10-host-data.md`; the anchor property still selects the orientation).
- The one capture region is the bounding box of the root bounds and every satellite's world bounds.
  Its policy is one `SnapshotPolicy` over the union of the root's and every satellite's
  `Blueprint.reads()`: the maximum depth, the union of kinds, traits and windows, and the adapter's
  element ceiling.
- The capture is sited with the root siting orientation, so the orientation audit of the capture
  covers the whole region. A satellite placed with a non-identity relative orientation may therefore
  be audited under the root orientation too and answer a `SC-MC-0004` warning that its own frame
  would not produce; a warning never changes the command's exit code.
- `set check` prints the set id and origin, the root and satellite outcomes, the empty settle
  lifecycle, the per-structure claim row counts and `firstFailure`; it writes no claim and exits 1
  only when the result carries no error finding and at least one admitted outcome.
- `set settle` prints the same shape plus `settled`, `rolledBack` and `rollbackFailures`, and exits 1
  only when the result is `formed()`.
- A missing document, compile failure, placement failure, capture failure or siting failure prints
  its findings and exits 0. A checked structure's findings are the set result's `setFindings`; the
  command does not merge them into a single `MatchResult`.

## 11. Java API

The document-layer records live in `kernel.document` and are produced by `StructureSetCompiler`:

```java
record StructureSetDocument(int schemaVersion, StructureSetSpec spec, Map<String, Value> extensions) { }
record StructureSetSpec(NamespacedId id, NamespacedId root, List<SatelliteSpec> satellites,
                        @Nullable SourceSpan source) { }
record SatelliteSpec(NamespacedId id, NamespacedId blueprint, LatticePoint at, Orientation orientation,
                     boolean required, Value arguments, @Nullable PresenceSpec presence,
                     List<LinkSpec> links, @Nullable SourceSpan source) { }
record PresenceSpec(CuboidSet cells, @Nullable SourceSpan source) { }
record LinkSpec(LinkMode mode, @Nullable LatticePoint parentCell,
                @Nullable LatticePoint satelliteCell, int maxDistance, DistanceMetric metric,
                @Nullable SourceSpan source) { }
record CompiledStructureSet(NamespacedId id, Blueprint root, List<CompiledSatellite> satellites) { }
record CompiledSatellite(SatelliteSpec spec, Blueprint blueprint) { public Siting relativeSiting(); }

enum LinkMode { face, range, none }
enum DistanceMetric { chebyshev, manhattan, euclidean }
```

The host plan lives in the adapter (`mc`), outside `kernel.engine`; the kernel does not execute it:

```java
record StructureSet(CompiledStructureSet compiled, Siting rootSiting, Cuboid rootBounds,
                    List<StructureSet.Satellite> satellites) {
    record Satellite(NamespacedId id, CompiledSatellite compiled, Siting siting,
                     Cuboid worldBounds, CuboidSet presenceCells) { }
    record Placement(@Nullable StructureSet set, List<Finding> findings) { boolean succeeded(); }
    static Placement place(CompiledStructureSet compiled, Siting rootSiting);
}

final class StructureSetHost {
    StructureSetHost(StructureKernel kernel);
    StructureSetResult check(StructureSet set, SnapshotLease lease, OwnerId owner,
                             Budget budget, Supplier<InstanceId> instances);
    StructureSetResult run(StructureSet set, SnapshotLease lease, OwnerId owner,
                           Budget budget, Supplier<InstanceId> instances);
}

record StructureSetResult(Map<NamespacedId, MatchOutcome> outcomes, List<Finding> setFindings,
                          List<NamespacedId> settled, List<NamespacedId> rolledBack,
                          List<Finding> rollbackFailures,
                          Map<NamespacedId, List<ClaimRow>> claims) { boolean formed(); }
```

`StructureSet.place` maps the root bounds, each satellite's composed siting, its world bounds and
its world-space presence region; a mapping failure is `SC-SPC-0001`. The plan's structure keys are
the root blueprint id and the satellite ids, so a satellite id equal to the root blueprint id is
`SC-DOC-0004`. `StructureSetHost.run` takes the lease, owner, one budget and an instance supplier;
the instances are allocated in check order root-first. `StructureSetHost.check` takes the same
arguments, runs the same presence/link/check sequence and stops before the settle pass: it returns
outcomes and set findings only, with empty `settled`, `rolledBack`, `rollbackFailures` and `claims`.
The existing `run` signature is preserved.

## 12. Document pipeline

| Layer | Addition |
| --- | --- |
| document IR | `StructureSetDocument`, `StructureSetSpec`, `SatelliteSpec`, `LinkSpec`, `PresenceSpec` |
| reader | `StructureSetReader`; kind dispatcher before `JsonReader` |
| compiler | `StructureSetCompiler`; resolves root/satellite documents, merges arguments, compiles blueprints, validates links/presence/duplicates |
| writer | `StructureSetWriter`; canonical member order |
| spans | set-member spans for findings |
| adapter | host `StructureSet` plan (`StructureSet`, `StructureSet.Satellite`, `StructureSet.Placement`), presence/link/check/settle/rollback (`StructureSetHost`, `StructureSetResult`), `DatapackBlueprints` set loading and `ServerState` set compile, the built-in demo set (`set_root`/`set_wing`/`set_demo`), and the `/cumulus set check`/`set settle` command with its one set capture |

`JsonReader` remains blueprint-only. A small dispatcher reads `kind` and selects the reader.

## 13. Codes

A `set` family was added to `FindingCode.Family` with seven entries in `Codes.all` (57 -> 64; the
registry now has 64):

| Code | Meaning | Producer |
| --- | --- | --- |
| `SC-SET-0001` | required satellite is absent | host presence |
| `SC-SET-0002` | satellite link distance is invalid | compiler/host link |
| `SC-SET-0003` | link cell is outside its blueprint or not covered by a check | compiler/host link |
| `SC-SET-0004` | satellite presence is unreadable | host presence |
| `SC-SET-0005` | duplicate satellite placement | compiler |
| `SC-SET-0006` | duplicate parent face link | compiler |
| `SC-SET-0007` | structure set rollback failed | host settle |

Document errors reuse existing codes:

| Situation | Code |
| --- | --- |
| unknown kind/member | `SC-DOC-0002` |
| duplicate satellite id | `SC-DOC-0004` |
| missing root/satellite blueprint | `SC-DOC-0011` |
| satellite names a structure_set | `SC-DOC-0002` |
| invalid `at`/orientation/arguments | `SC-DOC-0002`/factory code |

## 14. Non-goals

- kernel multi-structure transaction;
- nested structure sets;
- satellite-of-satellite;
- automatic retry;
- context rollback;
- world-coordinate placement in the document;
- claim transaction across structures;
- link direction beyond the six faces;
- per-link arguments;
- a shared budget across the members of one set, a `ContextStore` removal operation and same-siting
  rejection at compile time (S6, out of this round).

## 15. Test obligations

- JSON round-trip and Java/JSON parity for a set.
- Relative siting under identity/half/clockwise, negative offsets, relative orientation.
- Face adjacency, range metrics, `none`.
- Link coverage: bounds-only cell rejected; bay-only unread cell rejected; check-read cell accepted.
- Duplicate parent face, wide satellite, multiple range fan-in.
- Presence: block present, all air, unreadable, optional unknown, required absent.
- Arguments shallow merge and per-instance compile.
- Duplicate id/placement rejection.
- Host check-all/settle-all/rollback, ledger-wide claims filtering; context stays published on
  rollback because the kernel has no removal (06 §3.3).
- Command: compile/placement/capture findings, the set summary and first failure, the two exit
  codes, and a check that writes no row; one real capture over a root and a satellite with the real
  ledger and contexts.
- Check-only host path: admitted outcomes, no settle, no claim, no rollback.
- Document errors: unknown kind, missing blueprint, nested set, duplicate id, invalid link/presence.
- Codes family and count.

## 16. Implementation slices

| Slice | Scope | Gate |
| --- | --- | --- |
| S1 | `Siting.compose`, `FindingCode.Family.set`, seven `Codes` entries, `CodesTest` | focused space/finding tests |
| S2 | set document IR, reader/dispatcher/writer/spans, compiler, argument merge, duplicate/link/presence validation | focused document tests |
| S3 | `StructureSet`/`SatelliteSpec`/`LinkSpec`/`PresenceSpec`, host placement/link/presence/check/settle/rollback, adapter result | focused adapter/kernel tests |
| S4 | docs, counts, stress scenarios | docs + focused regression |
| S5 | production `set check`/`set settle`, one set capture, check-only host path, built-in demo set, real-level GameTests | focused adapter tests + `check` + `runGameTestServer` |

### 16.1 Progress

- **S1** landed at `9cf2794`: `Siting.compose`, `FindingCode.Family.set`, the seven `Codes` entries
  (64 total).
- **S2** landed at `00cf6c4` (based on `9cf2794`). `kernel.document` gains
  `StructureSetDocument`/`StructureSetSpec`/`SatelliteSpec`/`LinkSpec`/`PresenceSpec`/`LinkMode`/
  `DistanceMetric`, `StructureSetCompiler`/`StructureSetCompileResult`,
  `CompiledStructureSet`/`CompiledSatellite`, and `DocumentSource.findSet`; `mc` gains
  `StructureSetReader`, the `JsonDocument` kind dispatcher, `StructureSetWriter` and the shared
  `JsonValues` encoder. `JsonReader` accepts an optional `kind: "blueprint"`. The focused tests are
  `StructureSetCompilerTest` (13 tests) and `JsonStructureSetTest` (10 tests); `document.md` §4.5
  owns the set schema.
- **S3** landed at `93f5941` (based at `00cf6c4`). `mc` gains the host plan
  `StructureSet`/`StructureSet.Satellite`/`StructureSet.Placement`, the orchestration
  `StructureSetHost`, and `StructureSetResult`; the result carries the per-structure filtered `claims`
  map. `DatapackBlueprints` loads `structure_set` files beside blueprints and `ServerState` serves
  `findSet` and compiles a set. Focused tests are `StructureSetPlanTest` (6 tests),
  `StructureSetHostTest` (22 tests) and `DatapackStructureSetTest` (5 tests).
- **S4** is implemented in the working tree after `93f5941`: six adapter stress classes (33 tests) in
  `src/test/.../mc` cover IE preheaters, TEP range fan-in, AE2 logical subnetworks, controller/port
  envelopes, rotation and rollback (`06-verification.md` §3.3). The counts at H4 (2026-10-28,
  before T1–T3) are **258 kernel tests (39 containers), 276 adapter unit tests (36 containers), 64
  `@GameTest` methods**, `Codes.all` 64 and 33 decisions (D-33; D-29 is superseded). S4 adds no kernel type and
  regenerates no visual; the H4 host-data group (`06-verification.md` §3.4) touches the same
  adapter unit suite and changes no set-layer type or rule.
- **S5** is implemented in the working tree after `9e363ad`. `mc` gains `SetCommand` and the
  `/cumulus set check`/`set settle` literals, `CommandSupport.rootSiting` and `CommandSupport.policy(ReadScope)`,
  `StructureSetHost.check` (the existing `run` signature is preserved) and the built-in demo set
  (`structurecumulus:set_demo` with the `set_root`/`set_wing` blueprints plus the `ServerState.findSet`
  builtin fallback). The command prints compile/placement/capture findings and the set summary (id and
  origin, root and satellite outcomes, `settled`, `rolledBack`, `rollbackFailures`, per-structure claim
  rows, `firstFailure`); `set check` exits 1 only with no error finding and at least one admitted
  outcome, `set settle` only with `formed()`. Tests: two new `StructureSetHostTest` cases (24 total) and
  the real-capture `StructureSetHostGameTest` plus the dispatcher `SetCommandGameTest` (four GameTests;
  69 total). Counts on the S5 tree are **258 kernel tests (39 containers), 286 adapter unit tests (37
  containers), 69 `@GameTest` methods**; `Codes.all` stays 64 and the decision record 34. No kernel
  type and no visual changes.
- **Out of this round (S6):** a shared budget across a set's members, a `ContextStore` removal
  operation and same-siting rejection. The command and the one-capture executor are S5,
  not S6.

Visual review stays last.
