# 21 — Compact JSON authoring profile

> Status: design draft v0.2, 2026-10-20. This is a JSON profile, not a custom language. The
> compact profile is a second *shape* of the same `BlueprintDocument`; `DocumentSchema`
> normalises it to the canonical form before validation. Schema 2 is the only writable version;
> schema 1 is read through `DocumentUpgrade.v1ToV2`.

## 1. Why

The canonical `BlueprintDocument` is an IR, not an authoring format. A 7×6×7 furnace requires:

- `segments[]` with `bounds`, `parameters`, `bays[]`, `seams[]`;
- `assembly[]` with `as`, `segment`, `at`, `overlap`;
- `anchors[]`, `orientation`, `extensions`;
- an opaque expectation `DataView` grammar.

Most structures need none of that. They need a shell, a cavity, a few bays, a check or two, an
anchor and a facing.

The compact profile keeps only those, and derives the rest by documented defaults. It is still
JSON: no comments, no custom grammar, no second parser. The canonical profile remains available
for advanced features; a compact document is normalised into it, so `StructureKernel.compile`,
validation, findings and runtime semantics are unchanged.

## 2. Profiles, one document type

```java
public enum DocumentProfile { COMPACT, CANONICAL, AUTO }

public interface DocumentSchema {
    int MIN_VERSION = 1;
    int MAX_VERSION = 2;

    @Nullable BlueprintDocument read(NamespacedId source, DataView root,
                                     DocumentProfile profile, List<ValidationIssue> issues);
    DataView write(BlueprintDocument document, DocumentProfile profile);  // canonical or compact
}

public interface DocumentUpgrade {
    DataView v1ToV2(DataView root, List<ValidationIssue> issues);   // `size` → `bounds`, `claim` split
    static DocumentUpgrade defaults();
}
```

- `COMPACT` accepts omitted `segments`, `assembly`, `orientation`, `context`,
  `computations` and `checks`.
- `CANONICAL` is the explicit form.
- `AUTO` is the datapack default: a root with `segments`/`assembly` is canonical; a root with
  `shell`/`cavity`/`fills`/`bays` is compact. A document mixing both is a validation error.
- Both profiles produce the *same* `BlueprintDocument`; the only difference is what the author
  had to spell out.
- `read` accepts `1..MAX_VERSION`: version 1 is upgraded by `DocumentUpgrade.v1ToV2` before
  validation, and any other version is `SC-DOC-0001`. A document is never silently re-interpreted
  under a newer shape.
- `write` always emits `schemaVersion: 2`. `write(COMPACT)` is the tool/export path;
  `write(CANONICAL)` is the stable canonical form.
- A compact document is never stored in the runtime; it is normalised at load/compile time.

### 2.1 Datapack

Compact JSON is a first-class datapack format:

```
data/<namespace>/cumulus/structure/<path>.json
```

`DatapackBlueprints` reads every file with `DocumentProfile.AUTO`, normalises to a canonical
`BlueprintDocument`, validates it, and caches the compiled `Blueprint`. A reload swaps the whole
cache; a file that fails validation is absent, never silently replaced by its previous version.
`/cumulus lint` and `/cumulus build` can force `COMPACT` or `CANONICAL` to make a mistake explicit.

## 3. Compact shape

```json
{
  "schemaVersion": 2,
  "id": "mymod:huge_furnace",
  "bounds": [0, 0, 0, 7, 6, 7],

  "shell":  [{"box": [0,0,0,7,6,7], "block": "minecraft:blast_furnace"}],
  "cavity": [[1,1,1,5,4,5]],
  "fills":  [{"box": [1,1,1,5,1,5], "block": "minecraft:smooth_stone"}],

  "bays": [
    {"id": "mouth", "box": [3,1,0,1,2,1], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "bound",
     "expect": {"block": "minecraft:iron_bars"}}
  ],

  "checks": [
    {"id": "mouth_facing", "bay": "mouth",
     "focus": {"kind": "block", "cell": [3,1,0]},
     "expect": {"kind": "state", "property": "facing", "value": "east"}}
  ],
  "computations": [
    {"id": "volume", "phase": "section",
     "output": "mymod:volume", "region": [1,1,1,5,4,5],
     "kind": "count", "args": {"mode": "cells"}}
  ],
  "context": [
    {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"}
  ],

  "anchors": [{"role": "front", "property": "facing", "value": "east"}],
  "orientation": {"front": "north"}
}
```

That is the whole furnace: no segments, no assembly, no plans, no claims block, no turn list.

### 3.1 Root fields

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schemaVersion` | int | yes | `1` or `2`; `2` is written, `1` is upgraded, anything else is `SC-DOC-0001` |
| `id` | namespaced id | yes | blueprint id |
| `bounds` | `[x, y, z, sx, sy, sz]` | yes | the space every declaration lives in; origin plus size, half-open |
| `shell` | array of `{box, block, thickness?}` | no | the outer one-cell (or `thickness`) shell of each box |
| `cavity` | array of boxes or `{box, block}` | no | an inset box; a bare box means air, which is the common case |
| `fills` | array of `{box, block}` or `{layer, block}` | no | ordered explicit fills |
| `bays` | array | no | named, atomic overlays; each is one `Cuboid` with coverage, sharing and footprint |
| `checks` | array | no | one `Check`: the bay it belongs to, a focus, an expectation |
| `computations` | array | no | one computation: phase, output key, region (section) or inputs (aggregate) |
| `context` | array of `{id, type, retention, merge?, whenAbsent?}` | no | the keys the computations write; `merge` present means section contributions, absent means a single writer |
| `anchors` | array of `{role, property, value}` | no | the anchor read at the definition origin |
| `orientation` | object | no | `front`, the authored front vector, with the optional `turns` and `uprightOnly` of the canonical object; omitted, every `Turn` is admissible and no upright promise is made (`10` §4.1) |
| `plans` | object | no | not a document key: the engine expands the declared units (`09` §4.8) |
| `segments` | array | no | advanced: explicit segments; disables implicit `main` |
| `assembly` | array | no | advanced: explicit placement; required with `segments` |
| `extensions` | object | no | host extensions, unchanged; `cumulus:capture` is preserved verbatim |

Boxes are `[originX, originY, originZ, sizeX, sizeY, sizeZ]` everywhere — in `bounds`, `shell`,
`cavity`, `fills` and every bay. A minimum/maximum corner pair is not a box, and a JSON object
with `min`/`max` members is `SC-DOC-0002`.

An anchor is a **role plus the property and value to read at the definition origin**, not a box:
the origin is what makes orientation non-circular (`22` §4.4). An anchor with cells is
`SC-DOC-0002`.

### 3.2 Expectations

A compact expectation is a tagged JSON object. It normalises to the canonical opaque
`expectation` node.

```json
{ "block": "minecraft:iron_bars" }
{ "air": true }
{ "any": true }
{ "entity": "minecraft:cow" }
{ "trait": "structurecumulus:item_inventory" }
{ "allOf": [{ "block": "minecraft:iron_bars" }, { "trait": "structurecumulus:item_inventory" }] }
{ "anyOf": [{ "block": "minecraft:stone_bricks" }, { "block": "minecraft:deepslate_bricks" }] }
{ "not": { "air": true } }
{ "kind": "factory", "id": "mymod:door", "args": { "facing": "north", "half": "left" } }
```

- **One tag per object.** The key is the tag, the value is its arguments: `{"block": "x"}` is
  exactly `Spec(kind = block, args = "x")`. An object with two tags is `SC-DOC-0605`, never an
  implicit AND.
- `allOf`/`anyOf` take a non-empty array of `expect` objects; `not` takes exactly one object. An
  empty array is `SC-DOC-0606`.
- All children of one `allOf`/`anyOf` must share the subject type. `block` and `trait` both test
  an `OccupantSnapshot`, so they may be mixed; `state` tests `T` and cannot join them — write two
  `checks[]` entries instead.
- OR over cells is `{"eachCell": {"anyOf": [...]}}`; OR over whole bays is `anyOf` of
  `BayView` expectations.
- `block`/`air`/`trait`/`allOf`/`anyOf`/`not` are the shipped expectations (`22` §6.7).
- `factory` resolves through `Catalog<ExpectationFactory>`; an unknown id is `SC-SPI-0002` at
  `DESIGN`, never reflection.
- A bay may omit `expect`. That is legal and it is not "any": an expectation-less bay is either
  an audit bay (`Footprint.None`, asserting nothing) or a bay whose assertions come from
  `checks[]` entries that name it. An expectation is never inferred, and a missing one never
  matches everything.

### 3.3 Bays, checks, anchors, orientation

```json
"bays": [
  {"id": "door", "box": [3,0,0,1,2,1], "coverage": "one_subject",
   "sharing": "exclusive", "footprint": "bound",
   "expect": {"kind": "factory", "id": "mymod:door", "args": {"facing": "north"}}}
],
"checks": [
  {"id": "door_hinge", "bay": "door",
   "focus": {"kind": "door", "cell": [3,0,0]},
   "expect": {"kind": "state", "property": "hinge", "value": "left"}}
],
"anchors": [{"role": "front", "property": "facing", "value": "east"}],
"orientation": {"front": "north"}
```

- A bay is one rectangular `Cuboid`; multi-cell predicates such as a door are one bay (R-06).
- `coverage` is required: `every_cell`, `one_subject` or `any_subject`; `sharing` is `exclusive`
  (default) or `shared`; `footprint` is `box`, `bound` (default), `declared` or `none`.
  The three are validated together at DESIGN by R1 and R4 (`24`): `exclusive` with `none` is
  `SC-CHK-0106`, and so are checks that do not match the coverage — a per-cell check set short of an
  `every_cell` bay, or per-cell checks under `any_subject`.
- A check **names its bay**; a check with no `bay` is `SC-CHK-0107`, because a structure-level
  check contradicts itself.
- A check's `focus` may name a cell (`{"kind": "door", "cell": [...]}`) or a key the computations
  write (`{"kind": "context", "key": "mymod:volume"}`). The second is `Focuses.context(key)`, so an
  absent key is `Outcome.Missing` and the check is `UNREADABLE`, never a zero.
- `orientation.front` is the authored front vector; the siting is the unique candidate whose
  `apply(front)` equals the heading the anchor read. `turns` and `uprightOnly` are optional here as
  in the canonical form; a document that spells no turn list admits every `Turn`, and an omitted
  `uprightOnly` promises nothing (`10` §4.1).
- No controller entity exists; the anchor is a role read at the origin (R-04).

### 3.4 Fills and layers

```json
"fills": [
  { "box": [0, 0, 6, 7, 1, 1], "block": "minecraft:bricks" },
  { "layer": { "y": 0, "legend": { "#": "minecraft:bricks", ".": "minecraft:air" },
               "rows": ["#######", "##...##", "#######"] } }
]
```

- `fills` are ordered; each entry is a `box` or a `layer`.
- A layer is a 2D slice at one `y`; rows are equal length; legend maps one character to one
  block/air. Multi-cell expectations/anchors are separate `bays`/`checks`.
- Layer maps are exact but verbose; they are for small/irregular pieces and capture output.

### 3.5 Computations, keys and parameters

```json
"computations": [
  {"id": "volume", "phase": "section",
   "output": "mymod:volume", "region": [1,1,1,5,4,5],
   "kind": "count", "args": {"mode": "cells"}},
  {"id": "capacity", "phase": "aggregate",
   "output": "mymod:capacity", "inputs": ["mymod:volume"],
   "kind": "mymod:scale", "args": {"factor": 4}}
],
"context": [
  {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
  {"id": "mymod:capacity", "type": "long", "retention": "context"}
]
```

- A computation names its `phase`: `section` requires the `output` key to carry `merge`,
  `aggregate` forbids it. A `phase`/`merge` mismatch is `SC-DOC-0002`, not a silent coercion.
- A section computation names `region` (a bay id, a box, or a segment); an aggregate names
  `inputs` (the keys it reads) — that list is what Kahn order is built from, so an aggregate with
  no `inputs` and a non-empty body is `SC-DOC-0002`.
- `region` on an aggregate computation is optional and only narrows its read scope; a section
  computation without `region` covers `bounds`.
- `kind` is a catalog id like any other, and a bare word means the default namespace: the kernel
  ships `count`, `sum`, `volume`, `distinct` and `bind`, so an addon computation must be written
  with its namespace (`mymod:scale`). An unregistered `kind` is `SC-CTX-0101` at `DESIGN`.
- `context[]` is a declaration only: the runtime constructs keys through
  `ContextKeys.register(draft, …)` (`15` §3), and the compiler checks that the document's
  declaration and the catalog's agree (`SC-CTX-0101`).
- `whenAbsent` is opt-in and explicit. Omitted means "a missing section makes the key `Missing`",
  which is the safe default (`22` §7.3).
- `args` is a normal JSON object; numbers are integers in the compact profile (same as `DataView`).
- `params`/`$param` from the canonical schema remain available; compact authors usually omit them.

## 4. Geometry lowering

Compact geometry assigns every cell exactly one **declaring surface**. The phases are applied in
this order, and a later phase's box takes the cells it names from the earlier surface:

1. `shell` (the outer layer of each box, `thickness` deep);
2. `cavity` (an inset box, air by default);
3. `fills` in array order (each a `box` or a `layer`);
4. `bays` in array order, each a named atomic overlay.

The result is a local **cell → declaration** map with no cell written twice, which is what makes
expansion a function: scanning `y,z,x` and growing x, then z, then y partitions it into disjoint
`Cuboid`s, and each cuboid becomes one `Bay`.

Rules:

- **A named bay is emitted whole.** A named bay that is not one cuboid is `SC-DOC-0503`, and the
  author splits it. The compiler never splits a named bay.
- **Synthetic ids come from the box**: `#box@x,y,z:sx,sy,sz`, never a counter and never one id
  over several boxes. Two boxes with one id is unrepresentable.
- **A named bay's cells are final.** A later phase may not take cells from a named bay, and two
  named bays may not name one cell: that is `SC-DOC-0504`, not a demotion and not a silent merge.
- **`SC-DOC-0505` is `EmptyCuboid`**: a `box` with a zero or negative size in any axis. It is a
  declaration error at the field, never an empty bay the compiler drops.

`shell`/`cavity`/`fills` are applied before `bays`, which is why `shell → cavity → mouth` never
produces overlapping exclusive bays; the author never writes the split.

## 5. Defaults

| Field | Compact default |
| --- | --- |
| `schemaVersion` | required; `2` is written, `1` is upgraded |
| `bounds` | required; never inferred from boxes, and never zero-size |
| segments/assembly | one implicit `main` segment at `bounds.origin` |
| bay `coverage` / `sharing` / `footprint` | `coverage` is required; `sharing` `exclusive`, `footprint` `bound` |
| `anchors` | empty: a compact document that names no anchor declares no role to read at the origin |
| `orientation` | `front` = `north` (identity); the anchor's heading decides the placement |
| `computations`, `context` | empty |
| `extensions` | empty |

Defaults are materialised into the canonical `BlueprintDocument`; the runtime never applies a
compact-profile default.

## 6. Tooling (first priority)

The first implementation surface is capture/import, not hand-written files.

| Tool | Input | Output |
| --- | --- | --- |
| `/cumulus capture <id> <from> <to>` | world selection | compact JSON |
| `/cumulus import <schem/nbt>` | Sponge `.schem` / vanilla `.nbt` | compact JSON |
| `/cumulus lint <file>` | compact or canonical JSON | parse + design findings with line/column |
| `/cumulus build <file>` | compact JSON | canonical JSON / preview |
| `/cumulus export <id> compact` | registered blueprint | compact JSON, `extensions` kept |

Capture heuristics, in order:

1. selection becomes `bounds`; `orientation.front` is the authored `north`, and the player's
   facing becomes the anchor's `value`;
2. contiguous same-block runs become `fills.box` or `shell`/`cavity`;
3. irregular slices become `fills.layer`;
4. block entities and entities become a `bay` only when a predicate is obvious; their contents
   are written into the capture extension (§7), never dropped and never replaced by an empty
   block entity.

## 7. API

```java
public enum DocumentProfile { COMPACT, CANONICAL, AUTO }

public interface DocumentSchema {
    int MIN_VERSION = 1;
    int MAX_VERSION = 2;
    @Nullable BlueprintDocument read(NamespacedId source, DataView root,
                                     DocumentProfile profile, List<ValidationIssue> issues);
    DataView write(BlueprintDocument document, DocumentProfile profile);
}

public interface DocumentUpgrade {
    DataView v1ToV2(DataView root, List<ValidationIssue> issues);
    static DocumentUpgrade defaults();
}

public interface CompactWriter {
    DataView write(BlueprintDocument document);          // shorthand for schema.write(COMPACT)
}

public interface CaptureTool {
    @Nullable DataView capture(CaptureRequest request, IssueSink issues);   // null = any loss
}

public interface SchematicImport {
    @Nullable DataView read(NamespacedId source, byte[] bytes, IssueSink issues);
}
```

- The JSON frontend is the existing `DataFrontend` (`JsonFrontend` in the adapter); there is no
  custom parser and no DSL.
- `read(COMPACT)` normalises to `BlueprintDocument`; `read(CANONICAL)` is the existing schema.
- Source spans come from the JSON frontend; a compact field reports its line/column.
- `capture` returns `null` when anything was lost; every loss is an issue in the sink, never a
  quiet omission. A non-null result carries `extensions["cumulus:capture"]`:

  ```json
  { "stateLossy": false, "issues": [], "toolVersion": "0.2" }
  ```

  The compiler ignores that extension; a round-trip through `write(COMPACT)` preserves it
  verbatim.
- A block entity whose contents cannot be read is an issue with `SC-DOC-0521` and the import
  **refuses that block entity** rather than placing an empty one. Readable contents are written as
  `{"kind": "container", "items": {...}}` on the bay or fill that covers the cell.
- `CaptureTool` and `SchematicImport` emit `DataView`; the host writes JSON.

## 8. Proofs

1. **One type.** Both profiles produce a `BlueprintDocument`; `check` never sees a profile.
2. **Defaults are explicit.** The normalised document contains `bounds`, the implicit `main`,
   sharing and footprint; no downstream component re-applies a compact default.
3. **Round-trip.** `read(write(doc, COMPACT), COMPACT)` equals `doc` after canonicalisation for
   the compact subset; a field outside the subset forces `write(CANONICAL)` rather than being
   dropped.
4. **Diagnostics.** Every compact field keeps its JSON span; an unknown factory/computation/key
   points at the field, not at a generated document offset.
5. **No code execution.** `expect`, `computations` and `anchors` are tagged data; `factory` and
   every `kind` are catalog ids; no reflection, no script.
6. **Determinism.** Phase order, fill/bay array order and cuboid partitioning are fixed; JSON
   object member order never affects the result.
7. **One declaration per cell.** The expansion assigns each cell one declaring surface, so the
   canonical document has disjoint bays and `CuboidSet` normal form is the identity on it.

## 9. Open questions

Every earlier question here is decided by `22` §12 (versions, `bounds`, boxes, named bays, the
extension) or has moved to `22` §15. One of §16's questions is still this file's:

| # | Question |
| --- | --- |
| OQ-1 (`22` §16 Q5) | `context[]` + `computations[]` is correct but verbose for the common `count`/`volume` case. Does it need a shorthand — a computation that declares its key inline and lets `phase` imply `merge` — or does the shorthand hide the fold that revision 5 made explicit? |

## 10. Falsification

- The furnace compact JSON normalises to the same `Blueprint` as the canonical JSON; a diff is a
  defect.
- A compact document with `shell` but no `bounds` is rejected; it never infers a zero-size box,
  and a zero-size `box` is `SC-DOC-0505`.
- An unknown `factory` id produces `SC-SPI-0002` at the `expect` field, not a class-load error.
- A `min`/`max` corner pair in any `box` position is `SC-DOC-0002`: boxes are origin+size.
- A schema-1 document is upgraded, not rejected, and `write` emits `schemaVersion: 2`; a schema-3
  document is `SC-DOC-0001`.
- A named bay whose cells a later `fills` entry takes is `SC-DOC-0504`; a non-rectangular named
  bay is `SC-DOC-0503` and is never split.
- A bay with no `expect` and no `checks[]` entry naming it is an audit bay (legal, claims
  nothing); a bay with no `expect` never matches everything.
- `read(write(doc, COMPACT), COMPACT)` is byte-identical after canonicalisation for a document
  inside the compact subset.
- A capture of a region with an unreadable block entity emits `SC-DOC-0521` and refuses that
  block entity, and `capture` returns `null` rather than a document with a silent hole.
