# Module `kernel.document`

> Rank 6. The data form of a blueprint and the compiler that lowers it to the same `Blueprint` a
> Java builder produces. One schema, one version, one lowering function. No parser lives here; the
> host supplies a reader and a writer.

## 1. Scope

**Owns:** `Document`, `BlueprintSpec`, `BaySpec`, `SeamSpec`, `CheckSpec`, `ComputationSpec`,
`KeySpec`, `PlacementSpec`, `CompileResult`, `CompilerContext`, `DocumentCompiler`, `DocumentReader`,
`DocumentWriter`, `DocumentSource`.

**Does not own:** JSON/SNBT parsing (`mc` supplies a reader), catalogs (`spi`), the blueprint's
validation (`structure`), the `Value` tree (`core`), the run-time meaning of a factory (`structure`).

**Imports:** `core`, `space`, `finding`, `world`, `structure`, `context`.

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
| `CompileResult` | record | findings + optional blueprint |
| `CompilerContext` | record | the catalogs, type registry and document source the compiler needs |
| `DocumentCompiler` | final class | validation + lowering |
| `DocumentReader` | interface | text → `Document`, host-supplied |
| `DocumentWriter` | interface | `Document` → text, host-supplied |
| `DocumentSource` | interface | id → document, for cross-document definitions |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.document;

public record Document(int schemaVersion, BlueprintSpec root,
                       java.util.Map<NamespacedId, BlueprintSpec> definitions,
                       java.util.Map<String, Value> extensions) {
    public static final int CURRENT_VERSION = 1;
    public Document { /* version >= 1; maps immutable and ordered */ }
}

public record BlueprintSpec(NamespacedId id, Cuboid bounds, Value arguments,
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

public record CompileResult(FindingBundle findings, @Nullable Blueprint blueprint) {
    public boolean succeeded();
}

public record CompilerContext(Catalog<FocusFactory> focuses,
                              Catalog<ExpectationFactory> expectations,
                              Catalog<ComputationFactory> computations,
                              java.util.Map<NamespacedId, Class<?>> types,
                              DocumentSource documents) { }

public interface DocumentSource {
    @Nullable Document find(NamespacedId id);
    static DocumentSource of(java.util.Map<NamespacedId, Document> documents);
    static DocumentSource empty();
}

public final class DocumentCompiler {
    public static CompileResult compile(Document document, CompilerContext context);
    private DocumentCompiler() {}
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
merging is an error (`SC-BPT-0001`).

A box is `[x, y, z, sizeX, sizeY, sizeZ]` (origin + size). A bay has `box` or `boxes` plus optional
`minus`: `cells = union(boxes) - union(minus)`. A `CuboidSet` is a canonical set; the compiler
canonicalises again.

A definition is the same `BlueprintSpec` shape without `schemaVersion`; its `id` is the map key. A
placement is `{"definition": "mymod:shell", "as": "mymod:south", "at": [0,0,0]}`. The compiler
qualifies every placed bay/check/computation/seam id with the alias path: `alias.path + "/" + local`,
and translates every placed cell by `at`. An anchor and the orientation policy come only from the
root; a definition may not declare anchors (they would be duplicated or ambiguous).

A check names a bay by its merged id. Focus and expectation objects are `Value` trees; the `kind`
member selects the factory and the rest are its args. Kind ids are lowercase snake_case: the
built-in structural forms are `all_of`, `any_of`, `sequence`, `not`, `each_cell`, `at_least`,
`at_most`, `exactly`; the built-in leaves are `block`, `air`, `property`, `trait`, `any_occupant`;
the built-in focuses are `occupant`, `bay`, `all_cells`, `entity`, `nested`, `door`, `context`. A
built-in kind may be written without a namespace and resolves in `structurecumulus`; a custom kind
is `namespace:path`. A computation names its output key by id and either a bay (region = that bay's
cells) or a `region` box array.

A key declaration maps a JSON type name to a `Class`: `long`, `int`, `boolean`, `string`, `id`,
`cuboid`, `cuboid_set`. A custom type needs a `CompilerContext.types` entry; a document key is always
a built-in type in v1. `merge` names `sum`, `union`, `min`, `max`, `and`, `or`; absent means
single-writer. `whenAbsent` is decoded to the key type; a wrong kind is `SC-DOC-0008`.

### 4.2 Definitions in v1

A `definitions` entry contributes **bays, seams and placements** only. Checks, computations, keys,
derivations, anchors and the orientation policy are root-only; a definition that declares one is
`SC-DOC-0002`. This keeps a placed definition a pure geometry unit: its bays and seams are qualified
with the alias path (`alias.path + "/" + local`) and translated by the placement offset, while every
check and computation is written once in the root against the merged ids. Nested placements are
allowed; a cycle is `SC-DOC-0009`.

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
6. Call `Blueprint.builder(...).build()`. A build exception is caught and reported as
   `SC-BPT-0001` with a note; a clean compile never reaches that path.
7. Return `CompileResult(bundle, blueprint)`; `findings` may contain warnings even when the
   blueprint is non-null.

Lowering is a function of `(document, definitions, context)`; it reads no world, no clock and no
hash-ordered collection. Equal documents produce equal blueprints (I-13).

### 4.4 Findings

A `COMPILE` finding carries a `DEFINITION` label when the reader supplied a span, otherwise a
`LOCAL` label on the offending cells (or a label-less finding for a document-level failure). The
compiler never throws for bad data; a reader that cannot parse returns `null` and appends
`SC-DOC-0013`.

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

## 6. Tests

- JSON → `Document` → `Blueprint` for the campfire and furnace; the Java builder produces an equal
  blueprint (compare id, bounds, bays, checks, computations, anchors, orientation).
- Schema: missing/unknown/wrong-kind field; version 2; duplicate id; missing bay; missing key;
  type mismatch; cycle; missing definition; placement outside bounds.
- Placement: alias qualification, offset, two placements of one definition, duplicate ids.
- Keys: `long`/`int`/`boolean`/`string`/`id`/`cuboid`/`cuboid_set`; merge names; `whenAbsent`.
- Determinism: reordered object members and definition order produce equal blueprints.
- Reader/writer round-trip: a `Document` written by a test writer reads back equal (content, not
  spans).

## 7. Dependencies

`core`, `space`, `finding`, `world`, `structure`, `context`. It does not import `engine`, `remedy`,
`spi` or any parser library.
