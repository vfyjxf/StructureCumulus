# 10 — Documents and data (`kernel.document`)

> Unit U6. R-09, I-12: how a blueprint becomes data and back. Serves R-02, R-07, R-14; I-1, I-6,
> I-7, I-12, I-14, I-15.

## 1. Scope

Owns the authoring form and the compile from it: the value model and grammar (§4.1–4.2),
canonicalisation (§4.3), DESIGN validation (§4.4), segment lookup and placement (§4.5–4.6), lowering
(§4.7), the generation contract, and the two host seams. Not owned: what a field *means* once
compiled — a bay's checks (`Focus`/`Expectation`, U3), `Segment` construction and the stage plan (U5),
claim coexistence (U4), computation bodies (U8). A bay's body is a **`Spec`**: this unit stores it,
validates its shape, and never interprets `kind` — `SpecResolver` (§4.6) resolves it against `Catalogs`
when the library is installed, on the server thread. The kernel ships no parser.
Dependencies: `base`, `space`, `blueprint`, `context`, `expectation`, `finding`.

## 2. Vocabulary

| Name | Shape | Contract |
| --- | --- | --- |
| `DataView` / `DataKind` | interface / enum (in `kernel.base`) | Immutable value node; `OBJECT`, `ARRAY`, `STRING`, `NUMBER`, `BOOLEAN`, `NULL`. Defined in `base` so `expectation` can read factory arguments without importing `document`. |
| `DataFrontend` | SPI | text ↔ `DataView`; JSON and SNBT are adapter-supplied. |
| `DataBridge<T>` | SPI | host tree ↔ `DataView`; `T` never names a kernel type. |
| `BlueprintDocument` | record | Typed authoring form; canonical by construction. |
| `SegmentDocument` | record | One segment declaration (nested `Bay`, `Seam`, `Parameter`). |
| `AssemblyDocument` | record | One placement: `as`, `SegmentKey`, `at`, `overlap`. |
| `Spec` | record | One declared unit or key: `kind` + `args` + optional focus/expectation/subject/output/phase/retention/inputs. |
| `SpecResolver` | interface (here) | Resolves a `Spec` through `Catalogs`; the only reader of `kind`. Implemented in `spi`; unknown kind or arg is a finding. |
| `KeyDeclaration` | record | A declared `ContextKey`: `{id, type, retention, merge?, whenAbsent?}`; a `merge` marks a section-contributed key. |
| `DocumentUpgrade` | interface | One version step; `v1ToV2` is the only shipped instance. |
| `DocumentSchema` | interface | One version's closed grammar, defaults, `read`/`write`. |
| `Canonicalizer` | interface | Value normal form; pure, total, idempotent. |
| `DocumentValidator` | interface | DESIGN checks over document + library; never throws. |
| `ValidationIssue` | record | `(code, severity, message, @Nullable locus)`. |
| `DocumentProvenance` | record | `(source, @Nullable SourceSpan)`: where a compiled value came from. |
| `SegmentLibrary` | interface | Immutable keyed segment set with a host revision, installed once per reload. |
| `SegmentAssembler` | interface | `(document, library) → Blueprint` or issues; pure. |
| `SegmentEmitter` | SPI | External tool writing one segment back out as `DataView`. |

Referenced: `NamespacedId`, `Arguments`, `ParameterKey`, `Retention` (U0); `Cuboid`, `CuboidSet`,
`LatticePoint`, `Locus`, `Heading`, `Turn` (U1); `Segment`, `SegmentKey`, `Bay`, `Seam`, `Coverage`,
`Sharing`, `Footprint`, `AnchorSpec`, `OrientationPolicy`, `CheckPlan`, `Blueprint`
(U5); `Check`, `ContextKey`, `StatePhase` (U3, U8); `Catalogs` (U11); `FindingCode`,
`Severity`, `SourceSpan`, `Finding` (U9).

## 3. API design

```java
package dev.vfyjxf.cumulus.kernel.document;      // @NullMarked

public enum DataKind { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

public interface DataView {                      // statics + nested Builder construct
    DataKind kind();
    @Nullable DataView get(String key);          // null iff the member is absent
    List<DataView> list();                       // empty unless ARRAY; never null
    SequencedSet<String> keys();                 // code-point order; empty unless OBJECT
    @Nullable SourceSpan span();                 // position, not content
    String asString(String fallback);            // fallback unless the kind matches
    int asInt(int fallback);
    boolean asBoolean(boolean fallback);
}

public record BlueprintDocument(int schemaVersion, NamespacedId id, Cuboid bounds,
        List<SegmentDocument> segments, List<AssemblyDocument> assembly,
        Map<String, DataView> parameters, List<AnchorSpec> anchors, OrientationPolicy orientation,
        List<Spec> checks, List<Spec> computations,
        List<Spec> derives, List<KeyDeclaration> context,
        Map<String, DataView> extensions) {}

public record SegmentDocument(NamespacedId id, Cuboid bounds, Map<String, Parameter> parameters,
        List<Bay> bays, List<Seam> seams) {
    public record Bay(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                      Sharing sharing, @Nullable String description) {}   // JSON key "box"
    public record Seam(NamespacedId id, Cuboid cells, SeamKind kind, Map<String, String> props) {}
    public record Parameter(String name, @Nullable DataView defaultValue) {}
}

public record Spec(NamespacedId kind, DataView args,
                   @Nullable SpecRef focus, @Nullable SpecRef expectation,
                   @Nullable ClassRef subject, @Nullable KeyRef output,
                   @Nullable StatePhase phase, @Nullable Retention retention,
                   @Nullable List<KeyRef> inputs) {}
public record KeyDeclaration(NamespacedId id, ClassRef type, @Nullable Retention retention,
                             @Nullable NamespacedId merge, @Nullable DataView whenAbsent) {}
public record SpecRef(NamespacedId id) {}          // a catalog id, never an object
public record KeyRef(NamespacedId id) {}           // a key id, never a key instance
public record ClassRef(String name) {}             // "long", "int", "cumulus:block_view", …
public record AssemblyDocument(String as, SegmentKey key, LatticePoint at, boolean overlap) {}
public record ValidationIssue(FindingCode code, Severity severity, String message,
                              @Nullable Locus locus) {}
public record DocumentProvenance(NamespacedId source, @Nullable SourceSpan range) {}

public enum DocumentProfile { COMPACT, CANONICAL, AUTO }

public interface DocumentSchema {
    int MIN_VERSION = 1, MAX_VERSION = 2;
    int version(); SequencedSet<String> fields(); static DocumentSchema current();
    @Nullable BlueprintDocument read(NamespacedId source, DataView root,
                                     DocumentProfile profile, List<ValidationIssue> issues);
    DataView write(BlueprintDocument document, DocumentProfile profile);  // COMPACT or CANONICAL
}
public interface DocumentUpgrade {                     // one step, never a chain of dialects
    int from(); int to();
    @Nullable DataView upgrade(DataView root, List<ValidationIssue> issues);
    static DocumentUpgrade v1ToV2();                   // the only shipped step
}
public interface SpecResolver {            // implemented in spi; this side never names Catalogs
    @Nullable Check<?> check(Spec spec, List<ValidationIssue> issues);
    @Nullable Computation<?> computation(Spec spec, List<ValidationIssue> issues);
    @Nullable ContextKey<?> key(KeyDeclaration declaration, List<ValidationIssue> issues);
}
public interface CompactWriter {
    DataView write(BlueprintDocument document);    // schema.write(COMPACT), then span-free
}
public interface Canonicalizer {
    static Canonicalizer of(); DataView canonical(DataView value); boolean isCanonical(DataView value);
}
public interface DataFrontend {
    NamespacedId id();
    @Nullable DataView read(String text, List<ValidationIssue> issues);   // attaches spans
    @Nullable String write(DataView value, List<ValidationIssue> issues);
    default @Nullable String write(BlueprintDocument d, List<ValidationIssue> out);
}
public interface DataBridge<T> {
    NamespacedId id();
    @Nullable DataView fromHost(T hostValue, List<ValidationIssue> issues);
    @Nullable T toHost(DataView value, List<ValidationIssue> issues);
    default @Nullable T toHost(BlueprintDocument d, List<ValidationIssue> out);  // write, then toHost
}
public interface SegmentLibrary {
    long revision();                              // host-supplied, never a clock
    @Nullable Segment lookup(SegmentKey key);
    List<SegmentKey> keys();                      // id, then argument names
    static Builder builder(long revision);
    interface Builder {
        Builder put(Segment s, List<ValidationIssue> issues);
        Builder put(SegmentDocument document, List<ValidationIssue> issues);   // stores the specs
        SegmentLibrary apply(SpecResolver resolver, List<ValidationIssue> issues);  // §4.6
    }
}
public interface SegmentAssembler {
    static SegmentAssembler of(SegmentLibrary library);
    @Nullable Blueprint assemble(BlueprintDocument document, List<ValidationIssue> issues);
    @Nullable Segment compile(SegmentDocument document, List<ValidationIssue> issues);
}
public interface SegmentEmitter {
    NamespacedId id(); int schemaVersion(); void emit(Segment segment, DataView.Builder sink);
}
public interface DocumentValidator {
    static DocumentValidator of(SegmentLibrary library);
    List<ValidationIssue> validate(BlueprintDocument document);   // ordered; never throws
}
```

`DocumentSchema` has two profiles over the same `BlueprintDocument`: `CANONICAL` is the explicit
IR above; `COMPACT` omits what a default can supply — `segments`, `assembly`, `bounds`,
`parameters` — spells the geometry as `shell`/`cavity`/`fills`/`bays` (§4.7), and materialises the
same explicit value. `AUTO` is the datapack default: `segments`/`assembly` selects canonical,
top-level `shell`/`cavity`/`fills`/`bays` selects compact, and a document mixing the two is an error.
Both profiles are lossless: `bounds`, `segments`, `assembly` and `extensions` each have a
compact spelling, so a compact document writes back as one. `MIN_VERSION = 1` and `MAX_VERSION = 2`:
a v1 document is accepted and passed through `DocumentUpgrade.v1ToV2` before the grammar reads it, so
an old pack keeps loading while the canonical form moves on.

`Spec` is the only body form: `checks[]`, `computations[]` and `derives[]` are lists of it, and
`SpecResolver` is the only reader of `kind`. Resolution happens once, in
`SegmentLibrary.Builder.apply(resolver, issues)` (§4.6), on the server thread, against frozen
`Catalogs`; an unknown `kind` or a bad `args` is a finding on that spec's own node, never a
reflective lookup and never a half-built unit.

A `null` from `read`, `assemble`, `fromHost`, `toHost` or `write` holds **iff** an ERROR was
appended to the caller's list, ordered by §4.4's rule. That list is the unit's only refusal
channel: the success type (`Blueprint`) belongs to U5 and carries no findings, and inventing a
second result family here would duplicate `Finding` (I-7, I-14). Nothing in this unit throws for a
document.

## 4. Semantics

### 4.1 Grammar

`schemaVersion` is the envelope, read first so an unsupported version has a node to point at.
`bounds` and `orientation` are missing from the brief's list but required by `Blueprint` and
default badly (an absent policy silently means "identity only"), so both are required in this
canonical grammar; the compact spelling defaults them (§4.1's table below).

| Field | Type | Req. | Default | Semantics | Survives |
| --- | --- | --- | --- | --- | --- |
| `schemaVersion` | int | yes | — | Envelope; within `[MIN, MAX]`, v1 upgraded to v2. | no |
| `id` | id | yes | — | Blueprint identity. | `id` |
| `bounds` | box | yes | — | Local frame; all compiled cuboids inside. | `bounds` |
| `parameters` | obj name→scalar | no | `{}` | Formal parameter defaults; `{"$param":"n"}` resolves here. | `parameters` |
| `segments` | declaration[] | no | `[]` | Segment sources; read at library build. | merged |
| `assembly` | placement[] | yes | — | Which segment, where, under which name. | no |
| `anchors` | `AnchorSpec[]` | yes | — | `{role, property, value}`; the cell is the definition origin. | `anchors` |
| `orientation` | object | yes | — | `{"front": …}`: the authored front, with optional `turns` and `uprightOnly`. | yes |
| `plans` | — | no | — | Refused key: a stage's order comes from the declaration, not from the document. | no |
| `checks` | `Spec[]` | no | `[]` | One unit per entry; `bay`, `focus`, `expectation`, `subject` required. | yes |
| `computations` | `Spec[]` | no | `[]` | `phase` (`section`/`aggregate`), `output`, `inputs`, and exactly one of `bay` (a bay's cells) or `region` (a box); no `merge` — that lives on the key. | yes |
| `derives` | `Spec[]` | no | `[]` | Shorthand for one `aggregate` computation plus its `context` key. | yes |
| `context` | key[] | no | `[]` | `{id, type, retention, merge?, whenAbsent?}`; a `merge` marks a section-contributed key. | yes |
| `extensions` | obj id→value | no | `{}` | Vendor data: canonicalised, never interpreted. | no (Q-D5) |

Nested: `segments[].id`/`.bounds` required; `.parameters` is `name → {default?: scalar}`; `.bays[]`
is `{id, box, coverage, footprint?, sharing?, description?}` (`coverage` is `every_cell`,
`one_subject` or `any_subject`; `footprint` is `box`, `bound`, `declared` or `none` and defaults to
`bound`; `sharing` is `exclusive` or `shared` and defaults to `exclusive`); `.seams[]` is
`{id, box, kind, properties?}`.
`assembly[].as` matches `[a-z0-9_.-]+`, `.segment` is `{id, arguments?}`, `.at` defaults to
`[0,0,0]`, `.overlap` to `false`.

A box is `[originX, originY, originZ, sizeX, sizeY, sizeZ]` in both profiles — origin plus size,
never min/max corners. `bounds`, `bays[].box`, `seams[].box`, `checks[].focus.cell`, `fills[].box`
and `cavity[]` all use it; an anchor declares no box, because its cell is the definition origin
(`09` §4.5).

`segments`, `assembly`, `extensions` and the `$param` form die at compilation; `Blueprint` keeps
what a match reads plus one `DocumentProvenance` per value. A compiled bay id is global
(`as + ":" + localName`) because a claim names its bay by `NamespacedId` and one segment may be
placed twice; seam ids likewise.

**The compact spellings of a v2 document.**

| Canonical field | Compact spelling |
| --- | --- |
| `bounds` | `bounds: [ox, oy, oz, sx, sy, sz]`, or inferred from the explicit boxes (§4.7) |
| `segments` + `assembly` | inline `shell` / `cavity` / `fills` / `bays` in one implicit `main` placement |
| `plans` | not a document key: a stage's plan is the expansion of what `bays` declare (§4.8) |
| `anchors` | defaults to none: the compact form declares no role to read at the origin, so a policy naming one is refused (`09` B-4) |
| `orientation` | one object in both profiles (§4.1's canonical row): spellable in full, and defaulted here to `{"front": "north"}` — `front` is the identity heading, an omitted `turns` admits every `Turn` (the anchor's heading picks the placement), an omitted `uprightOnly` promises nothing |
| `checks[]` | `{id, bay, focus: {kind, cell?}, expect: {kind, property?, value?}}`; `expect` materialises into `args` |
| `computations[]` | `{id, phase, output, inputs?, bay?, region?}` |
| `derives[]` | `derive: [{kind, output, order}]` — no `phase`, no `inputs`; the resolver fills them |
| `context[]` | unchanged: `{id, type, retention, merge?}` is already its own compact form |
| `extensions` | unchanged; every key namespaced |

A `merge` on a key means section contributions; `phase: section` requires it, `phase: aggregate`
forbids it.

A bay's `expect` is one check whose id is derived from the bay and the index:
`{"expect": {"block": "minecraft:iron_bars"}}`. `bays[].expect` is a `Spec` body, so
nothing in it is interpreted here. A `derives[]` entry is the same kind of `Spec`; assembly lowers
it to a `DerivationSpec(deriver, args, output, order)` (`09` §4.8), so the document carries
the uniform body and the blueprint carries the reference.

### 4.2 DataView: a value model, not JSON

Six kinds, no floating point: `NUMBER` is a 64-bit integer, so a decimal is authored as a string
(`"0.75"`) and round-trips exactly. `get(k)` is `null` **iff** `k` is absent; a present `null` is
a node of kind `NULL` (`keys()` contains it, `asInt(f)` returns `f`), the grammar never writes
one, and where a value is required it is `SC-DOC-0105`. `list()`/`keys()` are empty on a kind
mismatch (`04` §4).

Frontends are SPIs the kernel does not implement (I-8, `02` §7): the adapter supplies JSON over
the host's JSON library, SNBT over its tag parser; `kernelTest` supplies a throwaway one. `read`
attaches a `SourceSpan` where it can; a source that cannot (a host tree) leaves `span()` null —
degradation `SC-DOC-0403`, never a different verdict. `equals` ignores `span()`, and `write` emits
span-free nodes.

### 4.3 Canonicalisation

`Canonicalizer.canonical` is grammar-free, so it also normalises an `extensions` body the kernel
has never heard of:

1. object members ascending by Unicode code point, recursively (a code-point comparator, not
   `String.compareTo`, which orders UTF-16 units and disagrees above the BMP);
2. `NUMBER` in shortest decimal: no `+`, no leading zeros, `-0` is `0`; one `long` per value, so
   `1` and `01` are one node and `1.0` is not a `NUMBER`;
3. `STRING` bytes untouched — no Unicode normalisation, no case or escape folding; internal
   whitespace is content, so only a frontend decides whitespace;
4. `ARRAY` order untouched; `NULL`/`BOOLEAN` already normal; `span()` preserved.

`DocumentSchema.write` adds the grammar layer: same key order, **every field equal to its default
elided**, only order-insensitive arrays emitted sorted. Sorted *inside the `BlueprintDocument`
factory*, so `equals` is content equality — `segments` by `SegmentKey`, a segment's `bays` by id,
`assembly` by `as`, `anchors` by role, `context` by key id, `derives` by `order`,
every map by key. Preserved verbatim: `checks[]` and `computations[]` — declaration order is a plan
tie-break (`09` §4.8), so sorting them would change the plan — and each box's origin/size pairing.

An unnamed field is `SC-DOC-0104`, never dropped: a silently ignored field is how a typo becomes a
working document, and a newer pack's field may change the meaning of one we did read, so
best-effort parsing is unsound. The one open map is `extensions` — namespaced keys
(`SC-DOC-0108`), opaque values. A version outside the range is `SC-DOC-0101` and nothing past the
envelope is read; each supported version keeps its own `DocumentSchema`.

Whitespace is frontend contract only: `write` is a function of the canonical value alone,
`write(canonical(v))` is byte-identical to `write(v)`, `read(write(v))` is content-equal to `v`.
The JSON frontend fixes one style (two-space indent, one member per line, `": "`, ascending keys,
scalar arrays inline, `\n`, one trailing newline) so a regenerated document is a line diff.

### 4.4 Validation

`ValidationIssue` is the compile-phase report, `Finding` the match-phase one: one-way conversion
(U9 wraps an issue as a `DESIGN` finding, its `Locus` as the primary label), never the reverse.
Order is span start, then code, then message; severity comes from the code's registry entry (U9
owns the table), and the column below is the proposal U9 adopts.

Pipeline (`StructureKernel.compile` composes 1–3): 1. frontend `read` (syntax → `SC-DOC-0402`,
stop); an envelope in `(MIN, MAX]` is upgraded here. 2. grammar `read` (any ERROR stops; an
ungrammatical document has no value to check). 3. `validate` references, geometry, anchors,
checks, computations, keys, presence (to the cap). 4. `assemble` (ERROR stops, `null`).
5. `write` + `canonical` for golden files and reloads.

| Code | Check | Sev. | When |
| --- | --- | --- | --- |
| `SC-DOC-0101` | UnsupportedVersion | ERROR | `schemaVersion` outside the range. |
| `SC-DOC-0102` | MissingEnvelope | ERROR | Root not an object, or no integer version. |
| `SC-DOC-0103` | MalformedId | ERROR | An id is not `ns:path`. |
| `SC-DOC-0104` | UnknownField | ERROR | A member the schema does not name. |
| `SC-DOC-0105` | WrongKind | ERROR | Kind mismatch, or `NULL` for a value. |
| `SC-DOC-0106` | MissingField | ERROR | A required member is absent. |
| `SC-DOC-0107` | Truncated | WARNING | The cap (512) was hit; the tail is unreported. |
| `SC-DOC-0108` | BadExtensionKey | ERROR | An `extensions` key is not a `NamespacedId`. |
| `SC-DOC-0201` | UnresolvedParameter | ERROR | `{"$param":"x"}` names no parameter. |
| `SC-DOC-0202` | UndeclaredArgument | ERROR | The segment declares no such parameter. |
| `SC-DOC-0203` | MissingArgument | ERROR | A parameter with no default got no value. |
| `SC-DOC-0204` | ArgumentKindMismatch | ERROR | Argument kind ≠ the default's kind. |
| `SC-DOC-0205` | UnknownBayReference | ERROR | A `checks[].bay` names no bay of the blueprint. |
| `SC-DOC-0206` | DeriveOrderDuplicate | ERROR | Two `derives` share an `order`. |
| `SC-DOC-0207` | DuplicateDerive | ERROR | Two `derives` declare one `output` key. |
| `SC-DOC-0208` | OutputKeyUndeclared | ERROR | A spec's `output` names no `context` entry for its phase. |
| `SC-DOC-0209` | KeyTypeMismatch | ERROR | A spec's produced type differs from its key declaration's `type`. |
| `SC-DOC-0301` | DuplicateSegmentKey | ERROR | Two declarations or entries for one key with different content. |
| `SC-DOC-0302` | SegmentMissing | ERROR | An assembly key resolves to no library segment. |
| `SC-DOC-0303` | DuplicatePlacement | ERROR | Two assembly entries share `as`. |
| `SC-DOC-0401` | HostConversionFailed | ERROR | A `DataBridge` cannot translate a node. |
| `SC-DOC-0402` | SyntaxError | ERROR | A `DataFrontend` cannot parse the text. |
| `SC-DOC-0403` | NoPositions | INFO | The source produced no spans. |
| `SC-DOC-0501` | OutsideBounds | ERROR | A placement or cuboid leaves `bounds`. |
| `SC-DOC-0502` | SegmentOverlap | ERROR | Two placements intersect, not both opted in. |
| `SC-DOC-0503` | NamedBaySplit | ERROR | A named bay is not one cuboid; the author splits it. |
| `SC-DOC-0504` | DuplicateDeclaration | ERROR | A cell is declared twice by the expansion order (§4.7). |
| `SC-DOC-0505` | EmptyCuboid | ERROR | A size component is zero or negative. |
| `SC-DOC-0521` | UnreadableBlockEntity | ERROR | A captured block entity's contents cannot be read; the import refuses that block entity (`21` §7). |
| `SC-DOC-0601` | DuplicateAnchorRole | ERROR | Two anchors declare one role. |
| `SC-DOC-0602` | AnchorUnresolved | ERROR | The policy's `primaryRole` has no `anchors[]` entry. |
| `SC-DOC-0603` | MissingStagePlan | retired | `PROBE` or `FORM` has no plan. Retired: an author declares no stage and the document names no plan, so nothing here can raise it (`23` §7). |
| `SC-DOC-0604` | SpecStageMismatch | retired | A spec's `stage` cannot carry its kind (`DESIGN` dispatches nothing). Retired: the document carries no `stage` field (`23` §7). |
| `SC-DOC-0605` | AmbiguousExpectTag | ERROR | One `expect` object carries two tags; the shorthand admits exactly one. |
| `SC-DOC-0606` | EmptyCombinator | ERROR | `allOf`/`anyOf` has no child; a combinator must assert something. |

Computation *cycles* are refused by `inputs`: `09` §4.8's Kahn order admits no cycle, and the DESIGN
twin of `SC-CHK-0102` points at the spec that closes it. What stays invisible here is a catalog
entry's own declared inputs and types, because this unit never imports the registry (`02` §2);
`SpecResolver` checks those where the catalog is in hand (§4.6).

### 4.5 Assembly

`SegmentAssembler.of(library).assemble(document, issues)` is pure: no view, no file, no clock, no
cache.

1. Substitute `$param` references from `parameters`; build the `SegmentKey` (a document scalar's
   token is `String`, `Long` or `Boolean`, Q-D4); `lookup`. A miss is `SC-DOC-0302` with a note
   listing the library's argumentisations of that id (sorted, capped at 8) and returns `null` —
   never a throw, never a hole.
2. Check `as` uniqueness (`SC-DOC-0303`) and `bounds` containment (`SC-DOC-0501`).
3. Footprint disjointness by a sweep over x-intervals. An intersecting pair is `SC-DOC-0502`
   unless **both** entries set `overlap: true`; then the cells are left to shared claims
   at match time (I-10). Equal `as` is refused, not tie-broken.
4. Rebase each bay and seam cuboid by `at`, rewrite ids to `as + ":" + localName`, and re-point
   each bay's checks at the placed bay with ids derived the same way; a translation leaving
   `bounds` is `SC-DOC-0501`.
5. Merge in (placement order, local order): bays, seams, anchors, checks, computations,
   derives, key declarations. A `Spec`'s `bay`, `output` and `inputs` are authored in compiled ids,
   so they are validated, not rewritten.
6. Stamp provenance: each bay and seam and each `Spec` carries the provenance of its declaring node —
   `DocumentProvenance(document.id(), span)` — and the blueprint carries the root node's. A
   bay of a segment declared in *this* document takes that declaration's span; one from elsewhere
   takes the placement entry's — a range into another file would address wrong bytes.
7. `Blueprint.builder(id)` … `build()`: every precondition `build()` checks has already passed, so
   a refusal there is a kernel defect, asserted in tests, not a document outcome.

The only orderings are those the document's factory fixed, no step iterates a hash-ordered
structure, and the sweep runs over sorted intervals — so the blueprint depends on
`(document, library)` alone, not on the source map's key order (P6).

### 4.6 Library, emitter, bridge, provenance

**`SegmentLibrary`** is immutable after `apply(...)`, shareable, and labelled for humans by a
host-supplied `revision()` (never a clock, never part of equality). A `SegmentKey`'s identity
*includes* its `Arguments`, so two argumentisations are two entries, not a version. A duplicate
key with equal content merges; one with different content is `SC-DOC-0301` and the key is
**absent** from the applied library (neither wins). A reload swaps the whole reference.

**`SpecResolver`** is the registry-installed phase. `put(SegmentDocument, issues)` only stores the
`Spec`s it parsed; `apply(resolver, issues)` runs once per reload, on the server thread, with the
frozen `Catalogs` the `spi` implementation holds, and is the single place a `kind` is read. It
validates the kind, the args against the kind's schema, the `subject` against the resolved
focus/expectation pair, the key declaration and the `inputs` that Kahn order needs, and returns
`null` for a spec it cannot resolve — the ERROR is already in `issues`. No member of this unit looks
a type up reflectively, so a pack cannot reach a class the host did not register.

**`SegmentEmitter`** is the generation contract: one call, one segment, one subtree written into a
`DataView.Builder`, with `schemaVersion` equal to the current version, stable ordering (an
unordered collection driving output is a defect) and a body satisfying `isCanonical`. The round
trip is a law: read an emitted document, assemble it, and the single placement's bays equal the
emitted segment's, prefix aside (P6).

**`DataBridge<T>`** moves a host tree in and out without the kernel naming it: `fromHost` yields
span-less values, `toHost` receives a canonical view, failures are `SC-DOC-0401`, and the kernel
calls only these two methods — no reflection. A host bridge may use reflection internally; the
shipped adapter does a direct typed walk.

**`DocumentProvenance`** = `(source, @Nullable range)`, `range` the half-open character range the
value occupied in `source`: it lets a `FORM` finding say "this is the bay you wrote at
[368, 455)". A span-less source keeps `source` and a null `range`, and U9's labels degrade to a
`Locus` without text.

### 4.7 Lowering

Lowering is a function: one document yields one value, and the expansion order is fixed —
`shell` → `cavity` → `fills` → `bays`. A cell is declared by exactly one surface; a second
declaration of the same cell is `SC-DOC-0504`, never a silent overwrite and never a later-wins
merge. `shell`/`cavity`/`fills` are applied before `bays`, so a fill reaching into a named bay is an
ERROR, not a demotion.

- A **named bay** is emitted whole. A named bay whose document form is several boxes is
  `SC-DOC-0503` and the author splits it; the compiler never splits a named bay.
- An unnamed **holed shell** lowers to several synthetic bays, each id derived from its own box:
  `#box@x,y,z:sx,sy,sz`. Never a counter — a second box must not shift the first box's id — and
  never one id over six boxes.
- `SC-DOC-0505` is `EmptyCuboid`: a box whose size component is zero or negative. An inferred
  `bounds` that would be empty is the same code, never "an empty structure".
- `SC-DOC-0503` is reserved for the named-bay split; `SC-DOC-0504` for the second declaration.

The compact profile's single implicit placement is named `main`, so a lowered bay is
`main:<localName>`. Everything downstream sees the `Blueprint` a canonical document produces:
lowering happens inside the grammar's `read`, and no later stage re-applies a compact default.

## 5. Reference sketch

```java
static final String FURNACE = """
{ "schemaVersion": 2, "id": "mymod:huge_furnace",
  "parameters": {"mouthWidth": 3},
  "bounds": [0, 0, 0, 9, 7, 9],
  "shell": [{"box": [0, 0, 0, 9, 7, 9], "block": "minecraft:bricks"}],
  "cavity": [[1, 1, 1, 7, 5, 7]],
  "bays": [{"id": "wall", "box": [0, 0, 0, 9, 1, 9], "coverage": "every_cell",
            "sharing": "shared", "footprint": "bound",
            "expect": {"block": "minecraft:bricks"}},
           {"id": "mouth", "box": [3, 1, 0, 1, 2, 1], "coverage": "one_subject",
            "sharing": "exclusive", "footprint": "bound"}],
  "anchors": [{"role": "mymod:primary", "property": "minecraft:facing", "value": "east"}],
  "orientation": {"front": "north", "uprightOnly": true,
                  "turns": ["NONE","CLOCKWISE","HALF","COUNTERCLOCKWISE"]},
  "checks": [{"id": "mymod:door", "bay": "main:mouth",
              "focus": {"kind": "door", "cell": [3, 1, 0]},
              "expect": {"kind": "state", "property": "facing", "value": "east"}}],
  "computations": [{"id": "mymod:volume", "phase": "section",
                    "output": "mymod:volume", "bay": "main:wall",
                    "kind": "count", "args": {"mode": "cells"}},
                   {"id": "mymod:capacity", "phase": "aggregate",
                    "output": "mymod:capacity", "inputs": ["mymod:volume"],
                    "kind": "scale", "args": {"factor": 4}}],
  "context": [{"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
              {"id": "mymod:capacity", "type": "long", "retention": "context"}],
  "extensions": {"mymod:display": {"name": "Huge Furnace"}} }
""";

void compile(DataFrontend json, SegmentLibrary library) {
    List<ValidationIssue> issues = new ArrayList<>();
    DataView root = json.read(FURNACE, issues);                     // 1. text → value
    BlueprintDocument doc = root == null ? null : DocumentSchema.current()
            .read(NamespacedId.of("mymod", "huge_furnace"), root,
                    DocumentProfile.AUTO, issues);
    if (doc == null) { report(issues); return; }                    // grammar refused

    DataView canonical = Canonicalizer.of().canonical(
            DocumentSchema.current().write(doc, DocumentProfile.CANONICAL));
    assert Canonicalizer.of().isCanonical(canonical);               // P2, P3
    assert DocumentSchema.current().read(doc.id(), canonical, DocumentProfile.CANONICAL,
            new ArrayList<>()).equals(doc);                         // P1

    issues.addAll(DocumentValidator.of(library).validate(doc));     // 2. design checks
    Blueprint bp = SegmentAssembler.of(library).assemble(doc, issues);
    if (bp == null) {                                               // 3. the failure path
        // e.g. SC-DOC-0208 "mymod:capacity names no context key", locus [412, 470)
        report(issues);
        return;
    }
    Bay wall = bp.bay(NamespacedId.of("main", "wall"));              // 4. the implicit placement
    SourceSpan at = bp.sourceOf(wall.id());                         // Q-D2: the declaration's span
}

// The library is installed once, with the registry in hand, on the server thread.
SegmentLibrary reload(SegmentDocument shellDoc, SpecResolver resolver) {
    List<ValidationIssue> issues = new ArrayList<>();
    SegmentLibrary library = SegmentLibrary.builder(41L)             // host revision, not a clock
            .put(shellDoc, issues)                                   // stores specs; resolves nothing
            .apply(resolver, issues);                                // §4.6: kind, args, outputs
    if (!issues.isEmpty()) report(issues);                           // e.g. SC-SPI-0002
    return library;
}

// A host bridge. HostNode is the host's tree; no kernel type names it.
sealed interface HostNode permits HostObject, HostArray, HostScalar {}
record HostObject(Map<String, HostNode> m) implements HostNode {}
record HostArray(List<HostNode> e) implements HostNode {}
record HostScalar(DataKind kind, String text, long number, boolean flag) implements HostNode {}

record HostBridge() implements DataBridge<HostNode> {
    @Override public NamespacedId id() { return NamespacedId.of("mymod", "host_tree"); }

    @Override public @Nullable DataView fromHost(HostNode n, List<ValidationIssue> issues) {
        return switch (n) {
            case HostObject o -> {
                var b = DataView.builder();
                o.m().forEach((k, v) -> b.put(k, fromHost(v, issues)));   // null member → issue
                yield b.build();
            }
            case HostArray a -> {
                var b = DataView.builder();
                a.e().forEach(e -> b.add(fromHost(e, issues)));
                yield b.build();
            }
            case HostScalar s -> switch (s.kind()) {
                case STRING -> DataView.string(s.text());
                case NUMBER -> DataView.number(s.number());      // no float in the model (Q-D4)
                case BOOLEAN -> DataView.bool(s.flag());
                case NULL -> DataView.nil();
                case OBJECT, ARRAY -> null;                      // a host float or byte array
            };
        };
    }

    @Override public @Nullable HostNode toHost(DataView v, List<ValidationIssue> issues) {
        return switch (v.kind()) {
            case OBJECT -> new HostObject(v.keys().stream().collect(LinkedHashMap::new,
                    (m, k) -> m.put(k, toHost(v.get(k), issues)), Map::putAll));
            case ARRAY -> new HostArray(v.list().stream().map(e -> toHost(e, issues)).toList());
            case STRING -> new HostScalar(DataKind.STRING, v.asString(""), 0, false);
            case NUMBER -> new HostScalar(DataKind.NUMBER, "", v.asInt(0), false);
            case BOOLEAN -> new HostScalar(DataKind.BOOLEAN, "", 0, v.asBoolean(false));
            case NULL -> new HostScalar(DataKind.NULL, "", 0, false);
        };
    }
}
```

## 6. Use cases

**Addon author.** Writes `mymod:shell` once with a `width` parameter, places it three times with
different `$param` values, runs `/cumulus validate`, and gets `SC-DOC-0602` at `anchors[0]` — the
policy's `mymod:primary` role has no `anchors[]` entry — with the entry's character range.

**Host / adapter.** A datapack reload builds a `SegmentLibrary` through `put` + `apply(resolver)`,
registers the JSON frontend and the host bridge, and caches blueprints keyed by
`(id, library.revision())`. A client-side editor round-trips a document through
`toHost`/`fromHost`; the two compare `equals`, and the editor's field order never reaches a
blueprint.

**External tool.** A structure exporter emits one segment per discovered region through a
`SegmentEmitter` as canonical JSON; the loader reads, validates and assembles it unedited.

## 7. Proofs

**P1 — round-trip identity.** `read(source, write(d), ∅)` equals `d`: `write` emits every
non-default field in one exact spelling, `read` applies the same field set, default table (§4.1)
and canonicalising factories, so each inverts the other. `write` elides only immutable defaults
(a shared `CheckPlan` instance, an empty key list), so no elided value can differ after re-reading.
A v1 document is not `d`: it is `v1ToV2`'s output, and P1 applies to the upgraded value.

**P2 — canonical uniqueness.** If `d1 ≅ d2` then `write(d1).equals(write(d2))`: `≅` *is* record
equality, and `write` is a total function of it — no map iteration order (every map is a sorted
copy), no branch on input spelling, `checks[]`/`computations[]` in declaration order. `canonical(write(d))`
equals `write(d)` because `write` already emits ordered keys and normal numbers: P3 applied to its
output.

**P3 — idempotence.** By cases on `DataKind`. `OBJECT`: sorting a sorted key sequence is identity,
recursion idempotent by induction. `ARRAY`: order untouched, recursion by induction. `NUMBER`: the
shortest-decimal form of a shortest-decimal form is itself. `STRING`/`BOOLEAN`/`NULL`: untouched.
`canonical` adds and removes no member. A counterexample would need a rule reading the input
*spelling*; none does.

**P4 — validation soundness and completeness.** *Sound*: each check is a pure predicate over
`(document, library)` and a code is emitted only in the branch where it holds, so none is
spurious. *Complete* per listed check: grammar (0101–0108), references (0201–0209, 0302,
0601–0602), geometry (0501–0503, 0505) and presence (0301, 0303) are decided over data the document and
library fully expose, so a violation reaches its branch. Three stamped boundaries rather than
passes: a `Spec`'s `kind` is resolved only in `SpecResolver` (§4.6), a catalog entry's own inputs
and types need `spi` (`02` §2), and expansion is a function (§4.7) whose duplicate rule is
`SC-DOC-0504` rather than a validation pass.

**P5 — no host dependency.** The kernel names `DataView`, `DataBridge<T>` (with `T` erased) and
`DataFrontend`; it imports no `com.google.gson`, `net.minecraft.nbt` or
`com.mojang.serialization` type, touches no reflected member, and holds `T` only as a type
variable. `SpecResolver`'s implementation sits in `spi` with `Catalogs`, so `document` imports no
registry type.
`compileKernelJava` proves it, `kernelPurity` backstops it.

**P6 — deterministic assembly, emitter round trip.** By induction over §4.5 and §4.7: the compile
reads only its arguments; every collection iterated was sorted by the document's factory or is in
declaration order; the expansion order is fixed and the sweep and merge have stated tie-breaks; the
library is immutable with conflicted keys absent. So two runs — and two documents differing only in
source map order — yield `equals` blueprints. For the emitter: its subtree is canonical by
contract, so `read` of it is a one-placement document whose key and bays are `s`'s by P1, and
assembling that yields a blueprint whose placement's bays equal `s.bays()` under the prefix.

## 8. Dependencies and open questions

| # | Requirement | Owner |
| --- | --- | --- |
| Q-D1 | `DataView` gains `span()` and `keys()` returns `SequencedSet`; the glossary row must follow. | U0 |
| Q-D2 | **Resolved**: `Blueprint.sourceOf(NamespacedId)` returns the per-bay/per-seam `@Nullable SourceSpan`, and `BlueprintBuilder.source(...)` stamps it. `Bay`/`Seam` stay free of a document type, so no `blueprint → document` cycle. | U5 |
| Q-D3 | **Resolved**: a compiled bay id is `<placement>:<localName>` — `main` for the compact profile's implicit placement — and a `Spec`'s `bay` addresses that id. | U5 |
| Q-D4 | Document scalars map to `String`/`Long`/`Boolean`; a segment declaring an `Integer` `ParameterKey` can never be placed from a document. Canonical numeric token, or `asLong`? | U0, U5 |
| Q-D5 | **Resolved**: `extensions` does not survive compilation; `write` round-trips it and nothing else reads it. | U5 |
| Q-D6 | **Resolved**: `SegmentLibrary.Builder.put(SegmentDocument, issues)` stores specs and `apply(SpecResolver, issues)` resolves them; the `SpecResolver` interface is declared here and implemented in `spi`, so `document` names no catalog type. | U3, U5, U11 |
| Q-D7 | **Resolved**: `SpecResolver` validates `kind`, `args`, `subject`, the key declaration and `inputs`; an unknown kind is `SC-SPI-0002`, an arg of the wrong kind `SC-DOC-0105`/`0106`, an undeclared output `SC-DOC-0208`. | U11 |
| Q-D8 | §4.3's JSON whitespace and the SNBT numeric/escape conventions are contract text here, implementation text in `16`; the `kernelTest` frontend is test-only. | A1 |

Open, from `22` §16:

- **Q-D9** Empty-volume protection (`§16` Q1): is one blueprint-wide `sealed: true` better than a
  per-bay `footprint: box` plus the `SC-CLM-0104` WARNING? A flag would be the first document field
  that rewrites a bay's declared `footprint`.
- **Q-D10** `whenAbsent` (`§16` Q3): it is a `ContextKey` field (`22` §6.4) spelled on a `context[]`
  entry and decoded against `type`; honoured only when every section of the region was readable. Is
  that too strict for an optional decorative region?
- **Q-D11** Compact computations (`§16` Q5): `context[]` + `computations[]` takes two entries for one
  number. `derive` is the only shorthand so far — does `count`/`volume` deserve one too?

## 9. Falsification

| # | Test | Breaks if it fails |
| --- | --- | --- |
| F1 | Two documents, identical content, every object's members in reverse order: assert both `read`s are `equals` and both `write`s equal. | P2, the premise of canonical files. |
| F2 | `assembly` names `mymod:missing`: assert `assemble` returns `null`, `issues` holds `SC-DOC-0302`, no exception escapes. | I-14, §4.5 step 1. |
| F3 | A bay carries `"bayColour": "red"`: assert `SC-DOC-0104`, `read` returns `null`, no compiled value contains it. | The no-silent-drop rule. |
| F4 | `"schemaVersion": 3`: assert `SC-DOC-0101`, `read` returns `null`, nothing past the envelope is read. | Envelope-first reading, closed range. |
| F5 | A v1 document with min/max boxes and `expectation` bodies: assert `v1ToV2` runs, `read` succeeds, and the value equals the v2 spelling of the same structure. | Accepted v1 documents, §4.1's version row. |
| F6 | A `fill` whose box overlaps a named bay, or two `shell` boxes over one cell: assert `SC-DOC-0504`, `read` returns `null`. | Expansion is a function (§4.7). |
| F7 | A named bay written as two boxes: assert `SC-DOC-0503`, never two bays and never one merged bay. | Named-bay identity. |
| F8 | `checks[0].kind` names a class no catalog registers: assert `SC-SPI-0002` from `apply`, no reflective lookup, and no library entry. | §4.6, `SpecResolver` as the only `kind` reader. |
| F9 | An emitter fed `HashMap`-backed parameters: assert two runs are byte-identical after a canonical write. | §4.6's stable-ordering obligation. |
| F10 | Assemble one document twice against libraries applied in different insertion orders: assert `equals` blueprints and equal bay order. | P6, library immutability. |

Cheapest reviewer attack: produce a document whose meaning depends on the `segments` array order.
If one exists, §4.3's order-insensitivity is wrong and `segments` must join the preserved lists.
