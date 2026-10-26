# Module `kernel.finding`

> Rank 2. The value of a report, the stable code table, the ordered bundle, and two pure renderings.
> A finding is a value; only the bundle builder decides order, dedup and caps.

## 1. Scope

**Owns:** `Severity`, `FindingCode`, `Label`, `LabelRole`, `Note`, `Suggestion`, `Applicability`,
`Finding`, `FindingOptions`, `FindingBundle`, `FindingRenderer`, `RenderContext`, `SourceText`,
`Explanation`, `CodeEntry`, `Codes`.

**Does not own:** which unit emits a code (module producer tables), a remedy plan (`remedy`), a
source file's life cycle (host), localisation catalogues (host), the owning `Locus` (`space`).

**Imports:** `core`, `space`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `Severity` | enum | `ERROR`, `WARNING`, `INFO`; declaration order is strength |
| `FindingCode` | record (core) | `SC-<FAMILY>-<NNNN>`; identity only; declared in `core` so `world` can name it |
| `Label` | record | `(Locus, message, LabelRole)`; at most one primary per finding |
| `LabelRole` | enum | `PRIMARY`, `SECONDARY`, `CONTEXT` |
| `Note` | record | `(message, @Nullable Locus)`; a fact with no cell of its own |
| `Suggestion` | record | `(message, Applicability, @Nullable NamespacedId remedy)` |
| `Applicability` | enum | machine-applicable → unspecified, top first |
| `Finding` | record | 7 components, validates and sorts its labels |
| `FindingBundle` | final class | immutable canonical bundle with counters |
| `FindingOptions` | record | caps and floors, warnings/info only |
| `FindingRenderer` | final class | `human` and `json`, pure functions |
| `RenderContext` | record | sources, document, width, colour, locale |
| `SourceText` | record | one text plus offset→line/column |
| `Explanation` | record | one code's long form |
| `CodeEntry` | record | code + canonical severity + explanation |
| `Codes` | final class | one constant per shipped code; `ALL` is the registry source |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.finding;

public enum Severity {
    ERROR, WARNING, INFO;
    public int rank();
    public boolean atLeast(Severity floor);
}

// FindingCode lives in dev.vfyjxf.cumulus.kernel.core; this module consumes it.

public enum LabelRole {
    PRIMARY, SECONDARY, CONTEXT;
    public int rank();
}

public record Label(Locus locus, String message, LabelRole role) {
    public static Label primary(Locus locus, String message);
    public static Label secondary(Locus locus, String message);
    public static Label context(Locus locus, String message);
}

public record Note(String message, @Nullable Locus locus) {
    public static Note of(String message);
    public static Note at(String message, Locus locus);
}

public enum Applicability {
    MACHINE_APPLICABLE, MAYBE_INCORRECT, HAS_PLACEHOLDERS, UNSPECIFIED;
    public int rank();
}

public record Suggestion(String message, Applicability applicability,
                         @Nullable NamespacedId remedy) { }

public record Finding(FindingCode code, Severity severity, String message,
                      java.util.List<Label> labels, java.util.List<Note> notes,
                      @Nullable Suggestion suggestion, Phase phase) {
    public Finding { /* copies, sorts labels, validates §4.2 */ }
    public static Builder builder(FindingCode code, Severity severity);
    public static Builder builder(CodeEntry entry);
    public @Nullable Label primaryLabel();
    public java.util.List<Label> labelsOf(LabelRole role);
    public boolean hasPrimary();

    public static final class Builder {
        public Builder phase(Phase phase);
        public Builder message(String message);
        public Builder label(Label label);
        public Builder label(Locus locus, String message);                     // PRIMARY
        public Builder label(Locus locus, LabelRole role, String message);
        public Builder secondary(Locus locus, String message);
        public Builder context(Locus locus, String message);
        public Builder note(String message);
        public Builder note(String message, Locus locus);
        public Builder suggest(String message, Applicability applicability,
                               @Nullable NamespacedId remedy);
        public Finding build();
    }
}

public record FindingOptions(int maxTotal, int maxPerCode, boolean includeInfo,
                             boolean includeNotes) {
    public static FindingOptions defaults();
    public FindingOptions narrowedBy(FindingOptions other);
    public FindingOptions withMaxTotal(int maxTotal);
    public FindingOptions withMaxPerCode(int maxPerCode);
}

public final class FindingBundle {
    public static FindingBundle empty();
    public static FindingBundle of(java.util.List<Finding> findings);
    public java.util.List<Finding> findings();
    public int size();
    public boolean isEmpty();
    public @Nullable Finding primary();
    public int elided();
    public int filtered();
    public FindingOptions options();
    public java.util.List<Finding> ofCode(FindingCode code);
    public java.util.List<Finding> ofSeverity(Severity severity);
    public FindingBundle filter(FindingOptions narrower);
    public static Builder builder(FindingOptions options);

    public interface Builder {
        Builder add(Finding finding);
        FindingBundle build();
    }
    private FindingBundle() {}
}

public final class FindingRenderer {
    public static String human(FindingBundle bundle, RenderContext context);
    public static String json(FindingBundle bundle);
    private FindingRenderer() {}
}

public record RenderContext(java.util.List<SourceText> sources, @Nullable NamespacedId document,
                            int width, ColorMode color, java.util.Locale locale) {
    public enum ColorMode { PLAIN, ANSI }
    public static RenderContext plain();
    public static RenderContext of(SourceText source);
    public @Nullable SourceText sourceFor(NamespacedId id);
    public RenderContext withWidth(int width);
}

public record SourceText(NamespacedId id, String text) {
    public static SourceText of(NamespacedId id, String text);
    public @Nullable String lineAt(int offset);
    public int lineNumber(int offset);
    public int column(int offset);
}

public record Explanation(FindingCode code, String summary, String body,
                          java.util.List<NamespacedId> seeAlso) { }

public record CodeEntry(FindingCode code, Severity severity, Explanation explanation) { }

public final class Codes {
    public static final CodeEntry LOCUS_UNMAPPED = ...;            // SC-SPC-0001
    public static final CodeEntry CUBOID_OFF_LATTICE = ...;        // SC-SPC-0002
    public static final CodeEntry UNREADABLE_CELL = ...;           // SC-OCC-0001
    public static final CodeEntry FOOTPRINT_CLIPPED = ...;         // SC-OCC-0002
    public static final CodeEntry NESTED_CUT = ...;                // SC-OCC-0003
    public static final CodeEntry EXPECTED = ...;                  // SC-EXP-0001
    public static final CodeEntry NEGATION_MATCHED = ...;          // SC-EXP-0002
    public static final CodeEntry THRESHOLD_IMPOSSIBLE = ...;      // SC-EXP-0003
    public static final CodeEntry FACTORY_UNKNOWN = ...;           // SC-EXP-0004
    public static final CodeEntry FACTORY_BAD = ...;               // SC-EXP-0005
    public static final CodeEntry CLAIM_CONFLICT = ...;            // SC-CLM-0001
    public static final CodeEntry LEDGER_UNAVAILABLE = ...;        // SC-CLM-0002
    public static final CodeEntry LEDGER_MOVED = ...;              // SC-CLM-0003
    public static final CodeEntry BINDING_INVALID = ...;           // SC-CLM-0004
    public static final CodeEntry BOUND_EMPTY = ...;               // SC-CLM-0005
    public static final CodeEntry BLUEPRINT_INVALID = ...;         // SC-BPT-0001
    public static final CodeEntry BAYS_OVERLAP = ...;              // SC-BPT-0002
    public static final CodeEntry BAY_OUTSIDE = ...;               // SC-BPT-0003
    public static final CodeEntry DUPLICATE_ID = ...;              // SC-BPT-0004
    public static final CodeEntry COVERAGE_INVALID = ...;          // SC-BPT-0005
    public static final CodeEntry ANCHOR_INVALID = ...;            // SC-BPT-0006
    public static final CodeEntry PLAN_INVALID = ...;              // SC-BPT-0007
    public static final CodeEntry DERIVATION_INVALID = ...;        // SC-BPT-0008
    public static final CodeEntry SEAM_OUTSIDE = ...;              // SC-BPT-0009
    public static final CodeEntry FOOTPRINT_OUTSIDE = ...;         // SC-BPT-0010
    public static final CodeEntry SUBJECT_MISMATCH = ...;          // SC-CHK-0001
    public static final CodeEntry CHECK_READS_DERIVATION = ...;    // SC-CHK-0002
    public static final CodeEntry FOCUS_OUTSIDE = ...;             // SC-CHK-0003
    public static final CodeEntry SCHEMA_VERSION = ...;            // SC-DOC-0001
    public static final CodeEntry DOCUMENT_FIELD = ...;            // SC-DOC-0002
    public static final CodeEntry DOCUMENT_FACTORY = ...;          // SC-DOC-0003
    public static final CodeEntry DOCUMENT_DUPLICATE = ...;        // SC-DOC-0004
    public static final CodeEntry PLACEMENT_OUTSIDE = ...;         // SC-DOC-0005
    public static final CodeEntry BAY_UNRESOLVED = ...;            // SC-DOC-0006
    public static final CodeEntry KEY_UNDECLARED = ...;            // SC-DOC-0007
    public static final CodeEntry KEY_TYPE = ...;                  // SC-DOC-0008
    public static final CodeEntry DEFINITION_CYCLE = ...;          // SC-DOC-0009
    public static final CodeEntry DERIVE_DUPLICATE = ...;          // SC-DOC-0010
    public static final CodeEntry DEFINITION_MISSING = ...;        // SC-DOC-0011
    public static final CodeEntry NO_POSITIONS = ...;              // SC-DOC-0012
    public static final CodeEntry READER_FAILED = ...;             // SC-DOC-0013
    public static final CodeEntry COMPUTATION_FAILED = ...;        // SC-CTX-0001
    public static final CodeEntry DERIVER_MISSING = ...;           // SC-CTX-0004
    public static final CodeEntry DERIVER_FAILED = ...;            // SC-CTX-0005
    public static final CodeEntry FOLD_REFUSED = ...;              // SC-CTX-0006
    public static final CodeEntry KEY_ABSENT = ...;                // SC-CTX-0007
    public static final CodeEntry BUDGET_EXHAUSTED = ...;          // SC-ENG-0001
    public static final CodeEntry SNAPSHOT_STALE = ...;            // SC-ENG-0002
    public static final CodeEntry DESIGN_DEFECT = ...;             // SC-ENG-0004
    public static final CodeEntry CANCELLED = ...;                 // SC-ENG-0005
    public static final CodeEntry BUNDLE_TRUNCATED = ...;          // SC-DIA-0001
    public static final CodeEntry REMEDY_FAILED = ...;             // SC-RMD-0001
    public static final CodeEntry REMEDY_MALFORMED = ...;          // SC-RMD-0002
    public static final CodeEntry EXTENSION_FAILED = ...;          // SC-SPI-0001
    public static final CodeEntry EXTENSION_UNKNOWN = ...;         // SC-SPI-0002
    public static final CodeEntry CATALOG_DUPLICATE = ...;         // SC-SPI-0003
    public static final java.util.List<CodeEntry> ALL;
    private Codes() {}
}
```

## 4. Laws

### 4.1 The code

`SC-<FAMILY>-<NNNN>`, family one of the 13, number `1..9999` zero-padded. `parse` returns `null`
for a malformed string; `text()` and `parse` are inverse on well-formed input. A code is allocated
once and never reused, renumbered or re-pointed. A code's canonical severity and explanation live in
its `CodeEntry`; a producer copies the severity. An unknown but well-formed code still constructs,
orders and renders; the registry has no entry, so `explain` returns `null` and
`explainOrFallback` synthesises the family text.

### 4.2 Finding validation

- `code`, `severity`, `phase`, `message` non-null; `message` non-blank after `strip()`.
- at most one `PRIMARY` label; every label message non-blank; every note message non-blank.
- a `KEY` locus carries no cells (enforced by `Locus`); a `DEFINITION` locus carries a source.
- `message` defaults to the primary label's message when it was not set; a finding with no primary
  label must set a message.
- labels are sorted by `(role.rank, frame.rank, source.start or canonical first cell, insertion)`.
  `frame.rank` is `DEFINITION 0, LOCAL 1, WORLD 2, KEY 3`. A `KEY` label sorts by insertion.
- The constructor throws `IllegalArgumentException` for a violated local rule. This is a producer
  bug; the engine guards extensions and converts it to `SC-SPI-0001`.

### 4.3 The bundle

Builder order: stamp phase (already present), floor (`includeInfo=false` drops `INFO`), dedup by
`(severity, phase, code, labels, notes)` keeping the first insertion, sort by
`(severity.rank, phase.rank, code, primary locus, insertion)`, per-code cap and total cap over
`WARNING`/`INFO` only, strip notes if `includeNotes=false`, append `SC-DIA-0001` when
`elided + filtered > 0`. `ERROR` is never capped. A smaller cap yields a prefix of a larger one.
`filter` re-applies floors/caps against the stored findings and counts what this narrowing dropped.
`primary()` is `findings().get(0)` or null.

`FindingOptions.defaults()` is unbounded, include info and notes. `narrowedBy` takes the smaller cap
and ANDs the booleans; a sink can narrow but never widen.

### 4.4 Rendering

`human` is rustc-shaped: `severity[code]: message`, a `-->` line for the primary word/local frame, a
source frame with carets when a `DEFINITION` label has a `SourceText`, one `= frame ...` line per
local/world label, `= note:` and `= help:` lines, a blank line between findings, and a
`= N elided, M filtered` trailer. It never fabricates a source line. `json` is one object per
finding; the schema is versioned additive with `schema`, `findings`, `elided`, `filtered`; fields are
`code`, `severity`, `phase`, `message`, `labels` (role, frame, cells, source, message), `notes`,
`suggestion`. Both are pure functions of `(bundle, context)`; they never reorder or filter.

### 4.5 Explanations

`CodeEntry.explanation` is one `Explanation` per shipped code: a one-sentence summary, a body that
says what triggers the code and what to check, and `seeAlso` topic ids (never code text). The
registry is `Codes.ALL`; `Catalogs.codes()` is its frozen catalog; `StructureKernel.explain` looks
up by code. A missing entry is legal for an addon code; `explainOrFallback` synthesises
`no explanation is registered for SC-…` plus the family subject.

## 5. Pipeline role

Findings are produced in all three phases. `COMPILE` findings carry a `DEFINITION` primary with a
source span when the document has one; `CHECK`/`SETTLE` findings carry `LOCAL`/`WORLD`/`KEY` labels.
The engine collects a bundle; it never renders. `FindingBundle` is the only aggregator.

## 6. Code producers

`P` = a primary label is required; `–` = label-less allowed.

| Code | Sev | P | Meaning | Producer |
| --- | --- | --- | --- | --- |
| `SC-SPC-0001` | ERROR | ✓ | a locus/cell cannot be mapped into the requested frame | `structure`/`engine` when `LocusMap`/`Siting` returns null |
| `SC-SPC-0002` | ERROR | ✓ | a box operation leaves the lattice | `document`/`structure` build |
| `SC-OCC-0001` | ERROR | ✓ | the cell is unreadable | `Focuses` when `occupant` returns null |
| `SC-OCC-0002` | WARNING | ✓ | a footprint leaves the snapshot | `Focuses` |
| `SC-OCC-0003` | ERROR | ✓ | a nested view was cut by depth | `Focuses`/`WorldView` consumer |
| `SC-EXP-0001` | ERROR | ✓ | the default element/state mismatch | `Expectations` leaf tests |
| `SC-EXP-0002` | ERROR | ✓ | a negated test matched | `Expectations.not` |
| `SC-EXP-0003` | ERROR | ✓ | a threshold cannot be met | `Expectations.atLeast/atMost/exactly` |
| `SC-EXP-0004` | ERROR | ✓ | a factory kind is unknown | `DocumentCompiler` |
| `SC-EXP-0005` | ERROR | ✓ | a factory returned null or bad args | `DocumentCompiler` |
| `SC-CLM-0001` | ERROR | ✓ | two claims conflict | `engine.check`/`settle` |
| `SC-CLM-0002` | WARNING | – | the ledger cannot answer | `engine.check` |
| `SC-CLM-0003` | ERROR | – | the revision moved twice | `engine.settle` |
| `SC-CLM-0004` | ERROR | ✓ | a settle binding is invalid | `engine.settle` |
| `SC-CLM-0005` | ERROR | ✓ | an exclusive bound footprint is empty | `ClaimPlanner`/`engine.settle` |
| `SC-BPT-0001` | ERROR | ✓ | a blueprint has no checks/bounds | `Blueprint.build` |
| `SC-BPT-0002` | ERROR | ✓ | two bays overlap with an exclusive side | `Blueprint.build` |
| `SC-BPT-0003` | ERROR | ✓ | a bay is empty or outside bounds | `Blueprint.build` |
| `SC-BPT-0004` | ERROR | ✓ | a bay/check/seam/anchor id is duplicated | `Blueprint.build` |
| `SC-BPT-0005` | ERROR | ✓ | coverage/footprint rules R1–R4 fail | `Blueprint.build` |
| `SC-BPT-0006` | ERROR | ✓ | anchor/orientation policy is invalid | `Blueprint.build` |
| `SC-BPT-0007` | ERROR | ✓ | a computation plan is cyclic or has a missing/duplicate producer | `Blueprint.build` |
| `SC-BPT-0008` | ERROR | ✓ | a derivation order/output is invalid | `Blueprint.build` |
| `SC-BPT-0009` | ERROR | ✓ | a seam is outside every bay | `Blueprint.build` |
| `SC-BPT-0010` | ERROR | ✓ | a declared footprint is outside bounds | `Blueprint.build` |
| `SC-CHK-0001` | ERROR | ✓ | a check pairs different subject types | `Check` constructor |
| `SC-CHK-0002` | ERROR | ✓ | a check reads a derivation output | `Blueprint.build` |
| `SC-CHK-0003` | ERROR | ✓ | a focus's static cells leave its bay | `Blueprint.build` |
| `SC-DOC-0001` | ERROR | ✓ | unsupported schema version | `DocumentCompiler` |
| `SC-DOC-0002` | ERROR | ✓ | a field is missing or of the wrong kind | `DocumentCompiler` |
| `SC-DOC-0003` | ERROR | ✓ | an unknown factory kind | `DocumentCompiler` |
| `SC-DOC-0004` | ERROR | ✓ | a duplicate id in a document | `DocumentCompiler` |
| `SC-DOC-0005` | ERROR | ✓ | a placement leaves the root bounds | `DocumentCompiler` |
| `SC-DOC-0006` | ERROR | ✓ | a check names no merged bay | `DocumentCompiler` |
| `SC-DOC-0007` | ERROR | ✓ | a spec names an undeclared key | `DocumentCompiler` |
| `SC-DOC-0008` | ERROR | ✓ | a key's declared type does not match its producer | `DocumentCompiler` |
| `SC-DOC-0009` | ERROR | ✓ | a definition cycle | `DocumentCompiler` |
| `SC-DOC-0010` | ERROR | ✓ | two derivations share an output/order | `DocumentCompiler` |
| `SC-DOC-0011` | ERROR | ✓ | a placement names a missing definition | `DocumentCompiler` |
| `SC-DOC-0012` | INFO | – | the reader produced no spans | `DocumentReader` implementation |
| `SC-DOC-0013` | ERROR | – | the reader/writer failed to parse/format | `DocumentReader`/`DocumentWriter` |
| `SC-CTX-0001` | ERROR | ✓ | a computation produced no value | `engine.check` |
| `SC-CTX-0004` | ERROR | ✓ | a derivation names a missing deriver | `engine.derive` |
| `SC-CTX-0005` | ERROR | ✓ | a deriver failed | `engine.derive` |
| `SC-CTX-0006` | ERROR | ✓ | a fold has a refused section | `engine.check` |
| `SC-CTX-0007` | ERROR | ✓ | a required key is absent | `Focuses.context` |
| `SC-ENG-0001` | ERROR | – | the budget ran out | `engine.check` |
| `SC-ENG-0002` | WARNING | – | the snapshot is no longer current | `engine.check`/`settle` |
| `SC-ENG-0004` | ERROR | – | an internal plan invariant failed | `engine.check` |
| `SC-ENG-0005` | WARNING | – | the attempt was cancelled | `engine.check` |
| `SC-DIA-0001` | WARNING | – | the bundle was truncated | `FindingBundle` |
| `SC-RMD-0001` | ERROR | ✓ | a remedy provider threw | `StructureKernel.solve` guard |
| `SC-RMD-0002` | ERROR | ✓ | a provider returned a malformed remedy | `StructureKernel.solve` |
| `SC-SPI-0001` | ERROR | ✓ | an extension threw or returned a malformed finding | `engine` guard |
| `SC-SPI-0002` | ERROR | ✓ | a document names an unregistered factory | `DocumentCompiler` |
| `SC-SPI-0003` | ERROR | – | a catalog build found a duplicate/different entry | `Catalogs.Builder.build` |
| `SC-MC-*` | — | — | adapter-only; not produced by the kernel | adapter |

## 7. Tests

- Code parse/text round-trip; malformed parse returns null; `Codes.ALL` has one entry per code and
  every entry has an explanation; every code in `ALL` appears in the producer table.
- Finding builder: primary label requirement, message defaulting, label sort, blank rejection.
- Bundle: dedup, order, caps (errors uncapped), truncation note, idempotent build, filter prefixes.
- Renderers: golden human text for a door/finding; JSON schema fields; no fabricated source line.
- `SourceText` line/column for CRLF, no trailing newline, first/last offset.

## 8. Dependencies

`core` (`NamespacedId`, `Phase`, `SourceSpan`, `@Nullable`) and `space` (`Locus`, `CuboidSet`,
`LatticePoint`). It never imports `world`, `structure`, `claim`, `context`, `engine` or `spi`.
