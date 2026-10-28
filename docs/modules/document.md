# Module `kernel.document`

> Rank 6. The data form of a blueprint and the compiler that lowers it to the same `Blueprint` a
> Java builder produces. One schema, one version, one lowering function. No parser lives here; the
> host supplies a reader and a writer.

## 1. Scope

**Owns:** `Document`, `BlueprintSpec`, `BaySpec`, `SeamSpec`, `CheckSpec`, `ComputationSpec`,
`KeySpec`, `PlacementSpec`, `StructureSetDocument`, `StructureSetSpec`, `SatelliteSpec`, `LinkSpec`,
`PresenceSpec`, `LinkMode`, `DistanceMetric`, `CompileResult`, `StructureSetCompileResult`,
`CompiledStructureSet`, `CompiledSatellite`, `CompilerContext`, `DocumentCompiler`,
`StructureSetCompiler`, `DocumentReader`, `DocumentWriter`, `DocumentSource`.

**Does not own:** JSON/SNBT parsing (`mc` supplies a reader), catalogs (`spi`), the blueprint's
validation (`structure`), the `Value` tree (`core`), the run-time meaning of a factory (`structure`).

**Imports:** `core`, `space`, `finding`, `claim` (only `Footprint`/`Sharing`), `world`, `structure`,
`context`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `Document` | record | schema version + root spec + reusable definitions + extensions |
| `BlueprintSpec` | record | the data form of one blueprint |
| `BaySpec` | record | data form of a bay |
| `SeamSpec` | record | data form of a seam |
| `CheckSpec` | record | data form of a check (focus/expectation by kind + args) |
| `ComputationSpec` | record | data form of a computation |
| `KeySpec` | record | a declared context key |
| `PlacementSpec` | record | one definition placed under an alias at an offset |
| `StructureSetDocument` | record | schema version + set spec + extensions |
| `StructureSetSpec` | record | set id + root blueprint id + satellites |
| `SatelliteSpec` | record | id + blueprint + relative placement + presence + links |
| `LinkSpec` | record | a face/range/none link between a root cell and a satellite cell |
| `PresenceSpec` | record | the cells whose occupants prove a satellite is present |
| `LinkMode` | enum | `face`, `range`, `none` |
| `DistanceMetric` | enum | `chebyshev`, `manhattan`, `euclidean` |
| `CompileResult` | record | findings + optional blueprint |
| `StructureSetCompileResult` | record | findings + optional compiled set |
| `CompiledStructureSet` | record | compiled root blueprint + compiled satellites |
| `CompiledSatellite` | record | a `SatelliteSpec` + its effective blueprint |
| `CompilerContext` | record | the catalogs, type registry and document source the compiler needs |
| `DocumentCompiler` | final class | validation + lowering |
| `StructureSetCompiler` | final class | set resolution + argument merge + link/presence/duplicate validation |
| `DocumentReader` | interface | text → `Document`, host-supplied |
| `DocumentWriter` | interface | `Document` → text, host-supplied |
| `DocumentSource` | interface | id → document, for cross-document definitions and set resolution |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.document;

public record Document(int schemaVersion, BlueprintSpec root,
                       java.util.Map<NamespacedId, BlueprintSpec> definitions,
                       java.util.Map<String, Value> extensions) {
    public static final int currentVersion = 1;
    public Document { /* version >= 1; maps immutable and ordered */ }
}

public record BlueprintSpec(NamespacedId id, Cuboid bounds, boolean envelope, Value arguments,
                            java.util.List<BaySpec> bays,
                            java.util.List<SeamSpec> seams,
                            java.util.List<CheckSpec> checks,
                            java.util.List<ComputationSpec> computations,
                            java.util.List<KeySpec> keys,
                            java.util.List<DerivationSpec> derivations,
                            java.util.List<AnchorSpec> anchors,
                            @Nullable OrientationPolicy orientation,
                            java.util.List<PlacementSpec> placements,
                            java.util.Map<String, Value> extensions,
                            @Nullable SourceSpan source) { }

public record BaySpec(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                      Sharing sharing, @Nullable String description,
                      @Nullable SourceSpan source) { }

public record SeamSpec(SeamId id, Cuboid cells, SeamKind kind,
                       java.util.Map<String, String> properties, @Nullable SourceSpan source) { }

public record CheckSpec(NamespacedId id, NamespacedId bay,
                        NamespacedId focusKind, Value focusArgs,
                        NamespacedId expectationKind, Value expectationArgs,
                        @Nullable String description, @Nullable SourceSpan source) { }

public record ComputationSpec(NamespacedId id, NamespacedId kind, Value args,
                              @Nullable CuboidSet region, @Nullable NamespacedId bay,
                              NamespacedId output, java.util.Set<NamespacedId> inputs,
                              @Nullable SourceSpan source) { }

public record KeySpec(NamespacedId id, String type, Retention retention,
                      @Nullable NamespacedId merge, @Nullable Value whenAbsent,
                      @Nullable SourceSpan source) { }

public record PlacementSpec(NamespacedId definition, LatticePoint offset, NamespacedId alias,
                            @Nullable SourceSpan source) { }

public record StructureSetDocument(int schemaVersion, StructureSetSpec spec,
                                   java.util.Map<String, Value> extensions) {
    public static final int currentVersion = 1;
    public StructureSetDocument { /* version >= 1; extensions immutable and ordered */ }
    public StructureSetDocument withoutSpans();
}

public record StructureSetSpec(NamespacedId id, NamespacedId root,
                               java.util.List<SatelliteSpec> satellites,
                               @Nullable SourceSpan source) {
    public StructureSetSpec { /* id, root and satellites non-null; satellites immutable */ }
    public StructureSetSpec withoutSpans();
}

public record SatelliteSpec(NamespacedId id, NamespacedId blueprint, LatticePoint at,
                            Orientation orientation, boolean required, Value arguments,
                            @Nullable PresenceSpec presence, java.util.List<LinkSpec> links,
                            @Nullable SourceSpan source) {
    public SatelliteSpec { /* arguments is an object; links immutable */ }
    public SatelliteSpec withoutSpans();
}

public record PresenceSpec(CuboidSet cells, @Nullable SourceSpan source) {
    public static PresenceSpec cell(LatticePoint cell);
    public static PresenceSpec of(CuboidSet cells);
    public PresenceSpec withoutSpans();
}

public record LinkSpec(LinkMode mode, @Nullable LatticePoint parentCell,
                       @Nullable LatticePoint satelliteCell, int maxDistance,
                       DistanceMetric metric, @Nullable SourceSpan source) {
    public static LinkSpec face(LatticePoint parentCell, LatticePoint satelliteCell);
    public static LinkSpec range(LatticePoint parentCell, LatticePoint satelliteCell,
                                 int maxDistance, DistanceMetric metric);
    public static LinkSpec range(LatticePoint parentCell, LatticePoint satelliteCell, int maxDistance);
    public static LinkSpec none();
    public LinkSpec withoutSpans();
}

public enum LinkMode { face, range, none }

public enum DistanceMetric { chebyshev, manhattan, euclidean }

public record CompileResult(FindingBundle findings, @Nullable Blueprint blueprint) {
    public boolean succeeded();
}

public record StructureSetCompileResult(FindingBundle findings, @Nullable CompiledStructureSet set) {
    public boolean succeeded();
}

public record CompiledStructureSet(NamespacedId id, Blueprint root,
                                   java.util.List<CompiledSatellite> satellites) {
    public @Nullable CompiledSatellite satellite(NamespacedId id);
}

public record CompiledSatellite(SatelliteSpec spec, Blueprint blueprint) {
    public Siting relativeSiting();
}

public record CompilerContext(Catalog<FocusFactory> focuses,
                              Catalog<ExpectationFactory> expectations,
                              Catalog<ComputationFactory> computations,
                              java.util.Map<NamespacedId, Class<?>> types,
                              DocumentSource documents) { }

public interface DocumentSource {
    @Nullable Document find(NamespacedId id);
    default @Nullable StructureSetDocument findSet(NamespacedId id);
    static DocumentSource of(java.util.Map<NamespacedId, Document> documents);
    static DocumentSource of(java.util.Map<NamespacedId, Document> documents,
                             java.util.Map<NamespacedId, StructureSetDocument> sets);
    static DocumentSource empty();
}

public final class DocumentCompiler {
    public static CompileResult compile(Document document, CompilerContext context);
    private DocumentCompiler() {}
}

public final class StructureSetCompiler {
    public static StructureSetCompileResult compile(StructureSetDocument document, CompilerContext context);
    private StructureSetCompiler() {}
}

public interface DocumentReader {
    @Nullable Document read(NamespacedId source, String text, java.util.List<Finding> errors);
}

public interface DocumentWriter {
    String write(Document document);
}
```

## 4. The schema

### 4.1 Envelope

| Member | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schemaVersion` | int | yes | exactly `1` |
| `id` | id | yes | the root blueprint id |
| `bounds` | box | yes | the root local frame; every compiled cell is inside |
| `envelope` | boolean | no | `true` derives the closed-world air pair over cells no bay or declared footprint covers; absent or `false` is off; root-only |
| `definitions` | object id → spec | no | reusable specs, compiled on placement |
| `placements` | placement[] | no | definitions placed into the root |
| `bays` | bay[] | yes* | the root's own bays |
| `checks` | check[] | yes* | the root's own checks |
| `computations` | computation[] | no | the root's own computations |
| `keys` | key[] | no | the root's declared keys |
| `derivations` | derivation[] | no | deriver references |
| `anchors` | anchor[] | no | anchor specs |
| `orientation` | object | no | required iff `anchors` is non-empty |
| `arguments` | object | no | factory/deriver arguments |
| `extensions` | object | no | opaque vendor data; never interpreted |

\* A root may be empty and get all bays/checks from placements. A root with no checks after
merging is an error (`SC-BPT-0001`), unless `envelope` is `true` and the uncovered set is non-empty:
the compiler passes the flag to `BlueprintBuilder.envelope()` and the builder appends the synthetic
check before the "at least one check" rule (`structure` §4.11). An envelope-only root with
`"bays": []` and `"checks": []` is therefore legal.

A box is `[x, y, z, sizeX, sizeY, sizeZ]` (origin + size). A bay has `box` or `boxes` plus optional
`minus`: `cells = union(boxes) - union(minus)`. A `CuboidSet` is a normalized set; the compiler
canonicalises again.

A definition is the same `BlueprintSpec` shape without `schemaVersion`; its `id` is the map key. A
placement is `{"definition": "mymod:shell", "as": "mymod:south", "at": [0,0,0]}`. The compiler
qualifies every placed bay/check/computation/seam id with the alias path: `alias.path + "/" + local`,
and translates every placed cell by `at`. An anchor and the orientation policy come only from the
root; a definition may not declare anchors (they would be duplicated or ambiguous).

A check names a bay by its merged id. Focus and expectation objects are `Value` trees; the `kind`
member selects the factory and the rest are its args. Kind ids are lowercase snake_case: the
built-in structural forms are `all_of`, `any_of`, `sequence`, `not`, `each_cell`, `at_least`,
`at_most`, `exactly`; the built-in leaves are `block`, `air`, `trait`, `trait_absent`,
`any_occupant`; the built-in focuses are `occupant`, `bay`, `all_cells`, `entity`, `nested`, `door`,
`context`. A built-in kind may be written without a namespace and resolves in `structurecumulus`; a
custom kind is `namespace:path`. A host payload check (`property`, `state`, `tag`, `fluid`) is a host
catalog entry that lowers to `Expectations.host`, not a kernel kind
([design/10](../design/10-host-data.md) §7). The four kinds are ordinary `checks` entries: there is no
`mc_checks` member and no separate MC JSON check type; the adapter's expectation catalog registers
them, and the kernel's built-in catalog alone rejects them as `SC-DOC-0003`. The `state` kind resolves
its `block` + `properties` to a `BlockState` at compile time; a malformed declaration is a compile
finding, not a runtime error. A computation names its output key by id and either a
bay (region = that bay's cells) or a `region` box array.

A `trait`/`trait_absent` declaration names a trait by `NamespacedId` and adds that id to the
expectation's `ReadScope.traits`; the factory builds a `TraitKey<Object>` so the capture's typed
value is read through `find`'s id + `isInstance` rule (`world` §4.7). A `nested` focus adds the
`nested` kind and one level of read depth; an `entity` focus adds the `entity` kind and its window.
The document path and the Java builder therefore produce the same `Blueprint.reads()`. No declaration
silently widens the host's capture policy: a missing trait id is a `TraitSlot.Refused`/no slot at
check time, never a default.

A key declaration maps a JSON type name to a `Class`: `long`, `int`, `boolean`, `string`, `id`,
`cuboid`, `cuboid_set`. A custom type needs a `CompilerContext.types` entry; a document key is always
a built-in type in v1. `merge` names `sum`, `union`, `min`, `max`, `and`, `or`; absent means
single-writer. `whenAbsent` is decoded to the key type; a wrong kind is `SC-DOC-0008`.

### 4.2 Definitions in v1

A `definitions` entry contributes **bays, seams and placements** only. Checks, computations, keys,
derivations, anchors, the orientation policy and `envelope` are root-only; a definition that
declares one is `SC-DOC-0002`. This keeps a placed definition a pure geometry unit: its bays and
seams are qualified with the alias path (`alias.path + "/" + local`) and translated by the placement
offset, while every check and computation is written once in the root against the merged ids. Nested
placements are allowed; a cycle is `SC-DOC-0009`.

### 4.3 Lowering

1. Validate the envelope, ids, boxes and ranges.
2. Resolve placements recursively. A cycle is `SC-DOC-0009`; a missing definition is `SC-DOC-0011`;
   a placed cell outside the root bounds is `SC-DOC-0005`.
3. Merge definitions into the root as bays and seams (definitions are geometry only); duplicate ids
   with different content are `SC-DOC-0004`, equal duplicates collapse. Root checks, computations,
   keys, derivations and anchors are then built against the merged ids.
4. Build keys (`KeySpec` → `ContextKey`), then bays (`BaySpec` → `Bay`), seams, computations
   (factory + key + region), derivations, anchors and the orientation policy.
5. Build checks: resolve the focus factory and the expectation factory, check
   `focus.subject() == expectation.subject()`, then `Check.of`.
6. Call `Blueprint.builder(...)`; when the root carries `envelope`, call `.envelope()` before
   `build()`. A build exception is caught and reported as its own code (for example `SC-BPT-0004`
   for a reserved envelope id); a clean compile never reaches that path.
7. Return `CompileResult(bundle, blueprint)`; `findings` may contain warnings even when the
   blueprint is non-null.

Lowering is a function of `(document, definitions, context)`; it reads no world, no clock and no
hash-ordered collection. Equal documents produce equal blueprints (I-13).

### 4.4 Findings

A `compile` finding carries a `definition` label when the reader supplied a span, otherwise a
`local` label on the offending cells (or a label-less finding for a document-level failure). The
compiler never throws for bad data; a reader that cannot parse returns `null` and appends
`SC-DOC-0013`.

### 4.5 Structure sets

A blueprint document carries no `kind`, or `"kind": "blueprint"`. A set document carries
`"kind": "structure_set"`; the host JSON layer reads that member before choosing the reader, so
`JsonReader` stays blueprint-only. The set document is root-only: it has no `definitions`,
`placements`, `bays` or `checks`.

| Member | Required | Default | Meaning |
| --- | --- | --- | --- |
| `schemaVersion` | yes | — | exactly `1` |
| `kind` | yes | — | `structure_set` |
| `id` | yes | — | set id |
| `root` | yes | — | root blueprint id |
| `satellites` | yes | — | array, may be empty |
| `extensions` | no | `{}` | document-level extensions |

A satellite:

| Member | Required | Default | Meaning |
| --- | --- | --- | --- |
| `id` | yes | — | unique within the set (`SC-DOC-0004`) |
| `blueprint` | yes | — | blueprint document id |
| `at` | yes | — | `[x, y, z]` offset in the root local frame |
| `orientation` | no | `identity` | orientation relative to the root |
| `required` | no | `false` | whether absence is an error |
| `arguments` | no | `{}` | shallow override of the blueprint's root arguments |
| `presence` | no | satellite bounds | presence region override |
| `links` | no | `[]` | link array |

`presence` is exactly one of `{"cell": [x, y, z]}` and `{"boxes": [[x, y, z, sx, sy, sz]]}`; the
region must be non-empty and inside the satellite bounds (`SC-DOC-0002` on the bounds violation).
A link names `mode` (`face`, `range`, `none`) and, for `face`/`range`, `parent_cell` and
`satellite_cell`; `range` also requires `max_distance` (non-negative) and accepts `metric`
(`chebyshev` default, `manhattan`, `euclidean`). A `none` link carries no cells and no distance.
A member the chosen mode does not use is `SC-DOC-0002`.

An effective argument set is the satellite blueprint document's root `arguments` shallow-merged
with the satellite `arguments`; the satellite wins on duplicate keys, there is no nested merge and
no key removal. `StructureSetCompiler` caches a compiled blueprint by
`(blueprint id, effective arguments)`; each satellite keeps its own id and relative siting.

Validation after lowering:

| Rule | Code |
| --- | --- |
| a link cell is outside its blueprint bounds or outside every check's read scope | `SC-SET-0003` |
| a face link is not face-adjacent, or a range link exceeds its distance/metric | `SC-SET-0002` |
| two satellites share `(at, orientation)` | `SC-SET-0005` |
| two face links share a parent cell and face direction | `SC-SET-0006` |
| a root or satellite id names no blueprint document | `SC-DOC-0011` |
| a root or satellite id names a structure set | `SC-DOC-0002` |
| a satellite cell cannot be mapped into the root frame | `SC-SPC-0001` |

A link is validated in the root local frame: the satellite cell is mapped by
`Siting.of(at, orientation)` and the face direction is the unit delta from the parent cell; rotation
by a root siting preserves both adjacency and every metric. Link findings label the link's set
document span (or the satellite's), with a secondary label on the offending blueprint document.
Bounds-only cells and bay-only cells do not satisfy coverage; only a check read scope does.

The host writer emits the canonical member order `schemaVersion`, `kind`, `id`, `root`,
`satellites`, `extensions`, and never writes spans. A single 1×1×1 presence region is written as a
`cell`, anything else as `boxes`; `orientation` and `required` are always written, empty
`arguments`/`links` and a null `presence` are omitted. `StructureSetDocument.withoutSpans()` (and the
same method on `StructureSetSpec`, `SatelliteSpec`, `LinkSpec` and `PresenceSpec`) is the
content-only form used for round-trip comparison; the blueprint writer's absence of `kind` is the
canonical blueprint form.

## 5. Codes

| Code | Sev | Meaning | Producer |
| --- | --- | --- | --- |
| `SC-DOC-0001` | ERROR | unsupported `schemaVersion` | `DocumentCompiler` |
| `SC-DOC-0002` | ERROR | missing/wrong field or kind | `DocumentCompiler` |
| `SC-DOC-0003` | ERROR | unknown focus/expectation/computation kind | `DocumentCompiler` |
| `SC-DOC-0004` | ERROR | duplicate id with different content | `DocumentCompiler` |
| `SC-DOC-0005` | ERROR | placement/box outside root bounds | `DocumentCompiler` |
| `SC-DOC-0006` | ERROR | a check names no merged bay | `DocumentCompiler` |
| `SC-DOC-0007` | ERROR | a spec names an undeclared key | `DocumentCompiler` |
| `SC-DOC-0008` | ERROR | key type/`whenAbsent` mismatch | `DocumentCompiler` |
| `SC-DOC-0009` | ERROR | definition cycle | `DocumentCompiler` |
| `SC-DOC-0010` | ERROR | derivation output/order duplicate | `DocumentCompiler` |
| `SC-DOC-0011` | ERROR | placement names a missing definition | `DocumentCompiler` |
| `SC-DOC-0012` | INFO | reader supplied no spans | `DocumentReader` |
| `SC-DOC-0013` | ERROR | reader/writer parse/format failure | `DocumentReader`/`Writer` |

`StructureSetCompiler` reuses `SC-DOC-0002` for a malformed or nested set reference,
`SC-DOC-0004` for a duplicate satellite id (raised by the reader) and `SC-DOC-0011` for a root or
satellite that names no document. Its link, placement and parent-face failures use the `set` family
(`SC-SET-0002`, `SC-SET-0003`, `SC-SET-0005`, `SC-SET-0006`); the family is defined in
[design/09](../design/09-structure-sets.md) and [`finding`](finding.md).

## 6. Tests

- JSON → `Document` → `Blueprint` for the campfire and furnace; the Java builder produces an equal
  blueprint (compare id, bounds, bays, checks, computations, anchors, orientation).
- Schema: missing/unknown/wrong-kind field; version 2; duplicate id; missing bay; missing key;
  type mismatch; cycle; missing definition; placement outside bounds.
- Placement: alias qualification, offset, two placements of one definition, duplicate ids.
- Keys: `long`/`int`/`boolean`/`string`/`id`/`cuboid`/`cuboid_set`; merge names; `whenAbsent`.
- Reads: a `trait`/`trait_absent` declaration contributes its trait id, a `nested` focus its kind and
  depth; the document path and the Java builder produce equal `Blueprint.reads()`.
- Host kinds: `property`, `state`, `tag` and `fluid` read as ordinary `checks` and compile only
  through the adapter catalog; the same declarations built through the adapter factories produce the
  same check shape and the same match/mismatch verdicts. There is no `mc_checks` member.
- Determinism: reordered object members and definition order produce equal blueprints.
- Reader/writer round-trip: a `Document` written by a test writer reads back equal (content, not
  spans).
- Envelope: `"envelope": true` is read, written after `bounds`, and round-trips as a fixed point;
  absent and `false` stay off; a definition-level member and a non-boolean value are `SC-DOC-0002`;
  an envelope root compiles to the same `Blueprint`, `Plan` and `ReadScope` as the Java `.envelope()`;
  an envelope-only root (`"bays": []`, `"checks": []`) yields one synthetic bay/check.
- Structure set: a valid set (root + two preheaters) reads, compiles and writes back equal content;
  `kind` selects the reader and is omitted on a canonically written blueprint; unknown members,
  wrong kind, bad `at`/`orientation`/`arguments`/link/presence members and a duplicate satellite id
  are located document errors; the JSON set and a Java-built set are equal.
- Relative siting: an identity/half/clockwise satellite and negative offsets map their local cells
  through `Siting.of(at, orientation)` as the root would see them.
- Links: face adjacency accepts only Manhattan distance one; range honors `chebyshev`, `manhattan`
  and `euclidean`; `none` carries no cells; a bounds-only or bay-only cell is rejected but a
  check-read cell is accepted; duplicate parent faces and duplicate `(at, orientation)` pairs are
  rejected.
- Arguments: shallow merge over the satellite blueprint's root arguments; equal
  `(blueprint id, effective arguments)` reuse one compiled blueprint while ids and sitings stay
  per satellite.
- Missing root/satellite documents are `SC-DOC-0011`; a set naming a set is `SC-DOC-0002`; a
  presence region outside the satellite bounds is `SC-DOC-0002`.

## 7. Dependencies

`core`, `space`, `finding`, `world`, `structure`, `context`. It does not import `engine`, `remedy`,
`spi` or any parser library.

## 8. Laws

- One schema version. A reader that sees anything else emits `SC-DOC-0001`; it never guesses.
- A definition is geometry: bays, seams and placements only. Every check, computation, key and
  derivation is written once in the root.
- `envelope` is a root boolean. A definition cannot declare it, and an absent member is off; the
  schema version does not move for it.
- Kind ids are lowercase snake_case. Built-in structural forms are grammar, not catalog entries;
  an unqualified kind resolves in `structurecumulus`.
- The declared output key is authoritative. A factory that returns a different output key is
  `SC-DOC-0003`; the compiler never silently retargets.
- Document content never throws. Every malformed field, cycle, unknown factory or invalid blueprint
  becomes a located finding; `CompileResult.blueprint()` is null whenever an error exists.
- A blueprint document is kindless or `blueprint`; a set document is `structure_set`. A set names
  only blueprints, never another set (no nesting).
- A set compile is a pure function of `(set document, DocumentSource, CompilerContext)`. The kernel
  never executes a set; it owns no host placement, presence read, claim or rollback.

## 9. Pipeline

`DocumentCompiler.compile(document, context)` flattens placements, builds keys, bays, seams,
checks, computations and derivations, validates them through `BlueprintBuilder`, and returns the
same `Blueprint` a Java builder would. The result is either one blueprint or a `FindingBundle`; the
engine never sees a half-built blueprint.

`StructureSetCompiler.compile(document, context)` resolves the root and every satellite through
`DocumentSource`, compiles each with the effective arguments, then validates presence bounds, links,
duplicate placements and parent faces. The result is either one `CompiledStructureSet` or a
`FindingBundle`; the host owns placement composition, capture, check, settle and rollback.
