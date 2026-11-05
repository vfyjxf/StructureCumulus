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
| `Severity` | enum | `error`, `warning`, `info`; declaration order is strength; `wireName()` is the uppercase tag |
| `FindingCode` | record (core) | `SC-<FAMILY>-<NNNN>`; the family carries an explicit uppercase tag; identity only; declared in `core` so `world` can name it |
| `Label` | record | `(Locus, message, LabelRole)`; at most one primary per finding |
| `LabelRole` | enum | `primary`, `secondary`, `context`; `wireName()` is the uppercase tag |
| `Note` | record | `(message, @Nullable Locus)`; a fact with no cell of its own |
| `Suggestion` | record | `(message, Applicability, @Nullable NamespacedId remedy)` |
| `Applicability` | enum | machine-applicable → unspecified, top first; `wireName()` is the uppercase tag |
| `Finding` | record | 7 components, validates and sorts its labels |
| `FindingBundle` | final class | immutable canonical bundle with counters |
| `FindingOptions` | record | caps and floors, warnings/info only |
| `FindingRenderer` | final class | `human` and `json`, pure functions |
| `RenderContext` | record | sources, document, width, colour, locale |
| `SourceText` | record | one text plus offset→line/column |
| `Explanation` | record | one code's long form |
| `CodeEntry` | record | code + canonical severity + explanation |
| `Codes` | final class | one constant per shipped code; `all` is the registry source |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.finding;

public enum Severity {
    error, warning, info;
    public int rank();
    public boolean atLeast(Severity floor);
    public String wireName();
}

// FindingCode lives in dev.vfyjxf.cumulus.kernel.core; this module consumes it.

public enum LabelRole {
    primary, secondary, context;
    public int rank();
    public String wireName();
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
    machineApplicable, maybeIncorrect, hasPlaceholders, unspecified;
    public int rank();
    public String wireName();
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
        public Builder label(Locus locus, String message);                     // primary
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
    public enum ColorMode { plain, ansi }
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
    public static final CodeEntry locusUnmapped = ...;             // SC-SPC-0001
    public static final CodeEntry cuboidOffLattice = ...;          // SC-SPC-0002
    public static final CodeEntry unreadableCell = ...;            // SC-OCC-0001
    public static final CodeEntry footprintClipped = ...;          // SC-OCC-0002
    public static final CodeEntry nestedCut = ...;                 // SC-OCC-0003
    public static final CodeEntry unreadableRead = ...;            // SC-OCC-0004
    public static final CodeEntry expected = ...;                  // SC-EXP-0001
    public static final CodeEntry negationMatched = ...;           // SC-EXP-0002
    public static final CodeEntry thresholdImpossible = ...;       // SC-EXP-0003
    public static final CodeEntry factoryUnknown = ...;            // SC-EXP-0004
    public static final CodeEntry factoryBad = ...;                // SC-EXP-0005
    public static final CodeEntry envelopeOpen = ...;              // SC-EXP-0006
    public static final CodeEntry claimConflict = ...;             // SC-CLM-0001
    public static final CodeEntry ledgerUnavailable = ...;         // SC-CLM-0002
    public static final CodeEntry ledgerMoved = ...;               // SC-CLM-0003
    public static final CodeEntry bindingInvalid = ...;            // SC-CLM-0004
    public static final CodeEntry boundEmpty = ...;                // SC-CLM-0005
    public static final CodeEntry blueprintInvalid = ...;          // SC-BPT-0001
    public static final CodeEntry baysOverlap = ...;               // SC-BPT-0002
    public static final CodeEntry bayOutside = ...;                // SC-BPT-0003
    public static final CodeEntry duplicateId = ...;               // SC-BPT-0004
    public static final CodeEntry coverageInvalid = ...;           // SC-BPT-0005
    public static final CodeEntry anchorInvalid = ...;             // SC-BPT-0006
    public static final CodeEntry planInvalid = ...;               // SC-BPT-0007
    public static final CodeEntry derivationInvalid = ...;         // SC-BPT-0008
    public static final CodeEntry seamOutside = ...;               // SC-BPT-0009
    public static final CodeEntry footprintOutside = ...;          // SC-BPT-0010
    public static final CodeEntry subjectMismatch = ...;           // SC-CHK-0001
    public static final CodeEntry checkReadsDerivation = ...;      // SC-CHK-0002
    public static final CodeEntry focusOutside = ...;              // SC-CHK-0003
    public static final CodeEntry schemaVersion = ...;             // SC-DOC-0001
    public static final CodeEntry documentField = ...;             // SC-DOC-0002
    public static final CodeEntry documentFactory = ...;           // SC-DOC-0003
    public static final CodeEntry documentDuplicate = ...;         // SC-DOC-0004
    public static final CodeEntry placementOutside = ...;          // SC-DOC-0005
    public static final CodeEntry bayUnresolved = ...;             // SC-DOC-0006
    public static final CodeEntry keyUndeclared = ...;             // SC-DOC-0007
    public static final CodeEntry keyType = ...;                   // SC-DOC-0008
    public static final CodeEntry definitionCycle = ...;           // SC-DOC-0009
    public static final CodeEntry deriveDuplicate = ...;           // SC-DOC-0010
    public static final CodeEntry definitionMissing = ...;         // SC-DOC-0011
    public static final CodeEntry noPositions = ...;               // SC-DOC-0012
    public static final CodeEntry readerFailed = ...;              // SC-DOC-0013
    public static final CodeEntry documentDeclared = ...;          // SC-DOC-0014
    public static final CodeEntry computationFailed = ...;         // SC-CTX-0001
    public static final CodeEntry deriverMissing = ...;            // SC-CTX-0004
    public static final CodeEntry deriverFailed = ...;             // SC-CTX-0005
    public static final CodeEntry foldRefused = ...;               // SC-CTX-0006
    public static final CodeEntry keyAbsent = ...;                 // SC-CTX-0007
    public static final CodeEntry budgetExhausted = ...;           // SC-ENG-0001
    public static final CodeEntry snapshotStale = ...;             // SC-ENG-0002
    public static final CodeEntry designDefect = ...;              // SC-ENG-0004
    public static final CodeEntry cancelled = ...;                 // SC-ENG-0005
    public static final CodeEntry bundleTruncated = ...;           // SC-DIA-0001
    public static final CodeEntry remedyFailed = ...;              // SC-RMD-0001
    public static final CodeEntry remedyMalformed = ...;           // SC-RMD-0002
    public static final CodeEntry extensionFailed = ...;           // SC-SPI-0001
    public static final CodeEntry catalogDuplicate = ...;          // SC-SPI-0003
    public static final CodeEntry satelliteAbsent = ...;           // SC-SET-0001
    public static final CodeEntry linkDistanceInvalid = ...;       // SC-SET-0002
    public static final CodeEntry linkCoverageInvalid = ...;       // SC-SET-0003
    public static final CodeEntry presenceUnreadable = ...;        // SC-SET-0004
    public static final CodeEntry duplicatePlacement = ...;        // SC-SET-0005
    public static final CodeEntry duplicateFaceLink = ...;         // SC-SET-0006
    public static final CodeEntry rollbackFailed = ...;            // SC-SET-0007
    public static final CodeEntry rootPlacementCollision = ...;    // SC-SET-0008
    public static final java.util.List<CodeEntry> all;
    private Codes() {}
}
```

## 4. Laws

### 4.1 The code

`SC-<FAMILY>-<NNNN>`, family one of the 14, number `1..9999` zero-padded. `parse` returns `null`
for a malformed string; `text()` and `parse` are inverse on well-formed input. The `Family` constants
are lowerCamel and each carries an explicit uppercase tag: `text()` renders that tag and `parse`
accepts only an uppercase tag from the table, so the wire text stays `SC-SPC-0001`, never
`SC-spc-0001`. A code is allocated once and never reused, renumbered or re-pointed. A code's canonical
severity and explanation live in its `CodeEntry`; a producer copies the severity. An unknown but
well-formed code still constructs, orders and renders; the registry has no entry, so `explain`
returns `null` and `explainOrFallback` synthesises the family text.

`Codes.all` is the kernel's table. An adapter or addon code is a `FindingCode` outside it and never
enters `Codes.all`; `SC-MC-*` is the adapter family. A host may give such a code a `CodeEntry` in
its own code catalog (§4.5), which is where its severity and explanation live; the kernel's table is
untouched. A `ReadRefusal` or a `CaptureReport` entry may carry an opaque host code; the kernel
relays it at the report site (it never branches on it, mints it or allocates it) unless the code is
already in `Codes.all`, in which case the site substitutes `SC-OCC-0004` and adds a note (`world`
§4.1, §4.10). `Expectations.host` takes its absent-value code from the caller (`world` §4.11), so
the kernel mints no host code even there; the adapter's `SC-MC-0006`…`0010` are its own expectation
codes (`adapter` §3.2).

### 4.2 Finding validation

- `code`, `severity`, `phase`, `message` non-null; `message` non-blank after `strip()`.
- at most one `primary` label; every label message non-blank; every note message non-blank.
- a `key` locus carries no cells (enforced by `Locus`); a `definition` locus carries a source.
- `message` defaults to the primary label's message when it was not set; a finding with no primary
  label must set a message.
- labels are sorted by `(role.rank, frame.rank, source.start or canonical first cell, insertion)`.
  `frame.rank` is `definition 0, local 1, world 2, key 3`. A `key` label sorts by insertion.
- The constructor throws `IllegalArgumentException` for a violated local rule. This is a producer
  bug; the engine guards extensions and converts it to `SC-SPI-0001`.

### 4.3 The bundle

Builder order: stamp phase (already present), floor (`includeInfo=false` drops `info`), dedup by
`(severity, phase, code, message, labels, notes)` keeping the first insertion — a different message
is a different finding, even under one code (D-67) — sort by
`(severity.rank, phase.rank, code, primary locus, insertion)`, per-code cap and total cap over
`warning`/`info` only, strip notes if `includeNotes=false`, append `SC-DIA-0001` when
`elided + filtered > 0`. `error` is never capped. A smaller cap yields a prefix of a larger one.
`filter` re-applies floors/caps against the stored findings and counts what this narrowing dropped.
`primary()` is `findings().get(0)` or null.

`FindingOptions.defaults()` is unbounded, include info and notes. `narrowedBy` takes the smaller cap
and ANDs the booleans; a sink can narrow but never widen.

### 4.4 Rendering

`human` is rustc-shaped: `severity[code]: message`, a `-->` line for the primary word/local frame, a
source frame with carets when a `definition` label has a `SourceText`, one `= frame ...` line per
local/world label, `= note:` and `= help:` lines, a blank line between findings, and a
`= N elided, M filtered` trailer. It never fabricates a source line. `json` is one object per
finding; the schema is versioned additive with `schema`, `findings`, `elided`, `filtered`; fields are
`code`, `severity`, `phase`, `message`, `labels` (role, frame, cells, source, message), `notes`,
`suggestion`. `json` writes `wireName()` for `severity`, `phase`, label `role`, locus `frame` and
suggestion `applicability`, and the `SC-*` text for `code`; the serialized tags stay
`ERROR`/`WARNING`/`INFO`, `COMPILE`/`CHECK`/`SETTLE`, `PRIMARY`/`SECONDARY`/`CONTEXT`,
`DEFINITION`/`LOCAL`/`WORLD`/`KEY` and
`MACHINE_APPLICABLE`/`MAYBE_INCORRECT`/`HAS_PLACEHOLDERS`/`UNSPECIFIED`, never the lowerCamel
constant names. Both are pure functions of `(bundle, context)`; they never reorder or filter.

### 4.5 Explanations

`CodeEntry.explanation` is one `Explanation` per shipped code: a one-sentence summary, a body that
says what triggers the code and what to check, and `seeAlso` topic ids (never code text). The
kernel registry is `Codes.all`; `Catalogs.codes()` is its frozen built-in projection, and a host may
merge its own entries into its own catalog through `Catalogs.Builder.codes` — that is how a host code
(for example `SC-MC-0003`) gets a canonical severity and an explanation without entering `Codes.all`.
`StructureKernel.explain` looks up by code, so a registered host code is explainable. A missing entry
is legal for an addon code; `explainOrFallback` synthesises `no explanation is registered for SC-…`
plus the family subject.

## 5. Pipeline role

Findings are produced in all three phases. `compile` findings carry a `definition` primary with a
source span when the document has one; `check`/`settle` findings carry `local`/`world`/`key` labels.
The engine collects a bundle; it never renders. `FindingBundle` is the only aggregator.

A code has one meaning and one owning module, and the producer table below lists every class that
emits that meaning (D-69). One meaning emitted from both `check` and `settle`, or relayed by the
adapter, stays one code: N-07's "exactly one producer" reads as one meaning with one owner, not one
emit site. A class name, never a line number, is the anchor. A code that only ever appears inside a
programmer-failure exception is marked `T` and still lists its throw site.

## 6. Code producers

`P` = a primary label is required; `–` = label-less allowed; `T` = thrown in a programmer-failure
exception, never a finding value (D-69). Owner names the module that owns the meaning; Producer
sites lists every class that emits it.

| Code | Sev | Label | Meaning | Owner | Producer sites |
| --- | --- | --- | --- | --- | --- |
| `SC-SPC-0001` | ERROR | P | a locus/cell cannot be mapped into the requested frame | space | `MatchEngine` check and settle, `ClaimPlanner` (`PlanRejected`), `StructureSetCompiler`, adapter `StructureSet`, `StructureSetHost` |
| `SC-SPC-0002` | ERROR | P | a declared cell or box would leave the lattice | space | `BuiltinSupport` (the built-in focus factories) |
| `SC-OCC-0001` | ERROR | P | a focus could not bind the subject it needs (none, more than one, or a missing part) | world | `Focuses` (built by `Check.missing`) |
| `SC-OCC-0002` | WARNING | P | a footprint leaves the snapshot | world | `Focuses` |
| `SC-OCC-0003` | ERROR | P | a nested view was cut by depth, an element cap, a cycle or an unloaded chunk | world | `Focuses.nested` |
| `SC-OCC-0004` | ERROR | P | a required read is unavailable (default refusal; also the substitute for a relayed kernel code) | world | `Check.unreadable`/`Check.relay`, adapter `CaptureReports`; refusal default in `BayView`, `Expectations`, `MatchEngine`, `LevelSnapshot` |
| `SC-EXP-0001` | ERROR | P | the default element/state mismatch | structure | `Expectations` leaf tests (default absent code in `ExpectationMeta`) |
| `SC-EXP-0002` | ERROR | P | a negated test matched | structure | `Expectations.not` |
| `SC-EXP-0003` | ERROR | P | a threshold cannot be met | structure | `Expectations` thresholds |
| `SC-EXP-0004` | ERROR | P | a focus or expectation declaration names no kind (an unregistered kind is `SC-DOC-0003`) | document | `DocumentCompiler` |
| `SC-EXP-0005` | ERROR | P | a factory returned null or bad args | document | `DocumentCompiler`, `BuiltinSupport` (for `BuiltinFocuses`/`BuiltinExpectations`), adapter `AdapterExpectations` |
| `SC-EXP-0006` | ERROR | P | a cell in the derived envelope holds a non-air occupant | structure | `Expectations.envelopeAir` (the synthetic envelope check) |
| `SC-CLM-0001` | ERROR | P | two claims conflict | claim | `MatchEngine` check and settle, `ClaimPlanner` (`PlanRejected`, planned rows overlap), `InMemoryClaimLedger` (settle verdict, forwarded by `MatchEngine`) |
| `SC-CLM-0002` | WARNING | – | the ledger cannot answer | claim | `MatchEngine` check and settle |
| `SC-CLM-0003` | ERROR | – | the revision moved twice | claim | `MatchEngine` settle |
| `SC-CLM-0004` | ERROR | P | a binding names a nested or non-root subject, a bay this blueprint does not declare, or a ONE_SUBJECT footprint that is not its cells; a bay with no binding | claim | `MatchEngine` (validate), `ClaimPlanner` (`PlanRejected`) |
| `SC-CLM-0005` | ERROR | P | an exclusive bay's resolved footprint is empty (a Bound footprint whose subjects are absent or air) | claim | `MatchEngine` (validate), `ClaimPlanner` (`PlanRejected`) |
| `SC-BPT-0001` | ERROR | P | a blueprint has no checks/bounds | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0002` | ERROR | P | two bays overlap with an exclusive side | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0003` | ERROR | P | a bay is empty or outside bounds | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0004` | ERROR | P | a bay/check/seam/anchor id is duplicated | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0005` | ERROR | P | coverage/footprint rules R1–R4 fail | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0006` | ERROR | P | anchor/orientation policy is invalid | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel), adapter `McAnchors` |
| `SC-BPT-0007` | ERROR | P | a computation plan is cyclic or has a missing/duplicate producer | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0008` | ERROR | P | a derivation order/output is invalid | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0009` | ERROR | P | a seam is outside every bay | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-BPT-0010` | ERROR | P | a declared footprint is outside bounds | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-CHK-0001` | ERROR | T | a check pairs different subject types (the document path reports `SC-DOC-0002`) | structure | `Check` constructor |
| `SC-CHK-0002` | ERROR | P | a check reads a derivation output | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-CHK-0003` | ERROR | P | a focus's static cells leave its bay | structure | `BlueprintBuilder.build`, `DocumentCompiler` (the `BlueprintRejected` channel) |
| `SC-DOC-0001` | ERROR | P | unsupported schema version | document | `DocumentCompiler`, `StructureSetCompiler`, adapter `JsonReader`, `StructureSetReader` |
| `SC-DOC-0002` | ERROR | P | a field is missing or of the wrong kind (ignored declarations and uncaught build refusals included) | document | `DocumentCompiler`, `StructureSetCompiler`, `BuiltinSupport`, adapter `JsonReader`, `StructureSetReader`, `JsonDocument`, `StructureSet.place`, `AdapterExpectations` |
| `SC-DOC-0003` | ERROR | P | an unknown factory kind | document | `DocumentCompiler` |
| `SC-DOC-0004` | ERROR | P | a duplicate id in a document | document | `DocumentCompiler`, adapter `StructureSet.place`, `StructureSetReader`, `DatapackBlueprints` |
| `SC-DOC-0005` | ERROR | P | a placement leaves the root bounds or the lattice | document | `DocumentCompiler` |
| `SC-DOC-0006` | ERROR | P | a check names no merged bay | document | `DocumentCompiler` |
| `SC-DOC-0007` | ERROR | P | a spec names an undeclared key | document | `DocumentCompiler`, `BuiltinFocuses` (the context focus factory) |
| `SC-DOC-0008` | ERROR | P | a key's declared type does not match its producer | document | `DocumentCompiler`, `BuiltinComputations` |
| `SC-DOC-0009` | ERROR | P | a definition cycle | document | `DocumentCompiler` |
| `SC-DOC-0010` | ERROR | P | two derivations share an output/order | document | `DocumentCompiler` |
| `SC-DOC-0011` | ERROR | P | a placement or a set member names a missing definition | document | `DocumentCompiler`, `StructureSetCompiler` |
| `SC-DOC-0012` | INFO | – | the reader produced no spans | document | adapter `JsonReader`, `StructureSetReader` |
| `SC-DOC-0013` | ERROR | – | the reader failed to parse or format | document | adapter `JsonReader`, `DatapackBlueprints` |
| `SC-DOC-0014` | ERROR | P | a declaration disagrees with the factory that produces it | document | `DocumentCompiler` |
| `SC-CTX-0001` | ERROR | P | a computation produced no value | context | `MatchEngine` check |
| `SC-CTX-0004` | ERROR | P | a derivation names a missing deriver | context | `MatchEngine` derive |
| `SC-CTX-0005` | ERROR | P | a deriver produced no usable value (no value, wrong type, wrong output or a non-INSTANCE key; a deriver that throws is `SC-SPI-0001`) | context | `MatchEngine` derive |
| `SC-CTX-0006` | ERROR | P | a fold has a refused section | context | `MatchEngine` check |
| `SC-CTX-0007` | ERROR | P | a required key is absent | context | `Focuses.context`, `MatchEngine` check and derive |
| `SC-ENG-0001` | ERROR | – | the budget ran out | engine | `MatchEngine` check |
| `SC-ENG-0002` | WARNING | – | the snapshot is no longer current | engine | `MatchEngine` check and settle, adapter `StructureSetHost` |
| `SC-ENG-0004` | ERROR | – | an internal plan invariant failed | engine | `MatchEngine` check and settle |
| `SC-ENG-0005` | WARNING | – | the attempt was cancelled | engine | `MatchEngine` check |
| `SC-DIA-0001` | WARNING | – | the bundle was truncated | finding | `FindingBundle` (only the builder mints it) |
| `SC-RMD-0001` | ERROR | P | a remedy provider threw | remedy | `StructureKernel.solve` guard |
| `SC-RMD-0002` | ERROR | P | a provider returned a malformed remedy | remedy | `StructureKernel.solve` |
| `SC-SPI-0001` | ERROR | P | an extension threw or returned a malformed finding | spi | `MatchEngine` extension guard (check, derive, fold) |
| `SC-SPI-0003` | ERROR | T | a catalog registration found a duplicate or a different value | spi | `Catalog.Builder.put` |
| `SC-SET-0001` | ERROR | – | a required satellite is absent | adapter | `StructureSetHost` presence |
| `SC-SET-0002` | ERROR | – | a satellite link distance is invalid | document | `StructureSetCompiler`, `StructureSetHost` link re-check |
| `SC-SET-0003` | ERROR | – | a link cell is outside its blueprint or not covered by a check | document | `StructureSetCompiler`, `StructureSetHost` re-check |
| `SC-SET-0004` | ERROR | – | satellite presence is unreadable | adapter | `StructureSetHost` presence |
| `SC-SET-0005` | ERROR | – | duplicate satellite placement | document | `StructureSetCompiler`, adapter `StructureSet.place` |
| `SC-SET-0006` | ERROR | – | duplicate parent face link | document | `StructureSetCompiler`, adapter `StructureSet.place` |
| `SC-SET-0007` | ERROR | – | structure set rollback failed | adapter | `StructureSetHost` settle |
| `SC-SET-0008` | ERROR | – | root/satellite placement collision | document | `StructureSetCompiler`, adapter `StructureSet.place`, `StructureSetHost` re-check |
| `SC-MC-0006` | ERROR | P | a block property does not match | adapter | `property` expectation (mismatch) |
| `SC-MC-0007` | ERROR | P | a block state does not match | adapter | `state` expectation (mismatch) |
| `SC-MC-0008` | ERROR | P | a block/fluid/entity tag does not match | adapter | `tag` expectation (mismatch) |
| `SC-MC-0009` | ERROR | P | a fluid does not match | adapter | `fluid` expectation (mismatch) |
| `SC-MC-0010` | ERROR | P | a host snapshot value was not captured | adapter | host expectations (absent) and `McAnchors` (missing anchor state), always unreadable, never a mismatch |
| `SC-MC-*` | — | — | adapter-only family; never minted by the kernel, never in `Codes.all`; the capture paths (`0001`–`0005`) relay through a `ReadRefusal` or a `CaptureReport` entry (`world` §4.10), and the expectation paths (`0006`–`0010`) pass `SC-MC-0010` as the absent code of `Expectations.host` (`world` §4.11) | adapter | the adapter |

`Codes.all` has 66 entries in this revision, matching the registry; the capture-report and host
expectation channels add none of them. `SC-MC-0003` (a nested cut), `SC-MC-0004` (a block state cannot
express the siting orientation) and `SC-MC-0005` (the root is over the element ceiling) are produced by
the adapter in a `CaptureReport` (`adapter` §12): they are host codes, never kernel registry entries,
and the adapter's own catalog gives them their severity (`0005` ERROR, `0003`/`0004` WARNING) and their
explanation. `SC-MC-0006`…`0010` are the adapter's expectation codes (`adapter` §3.2, §12): `0006`–`0009`
are mismatches and `0010` is the unreadable absent value (a missing anchor state is the same code);
the adapter catalog gives each its ERROR
severity and explanation, and none enters `Codes.all`. A cut child is still reported by the check as
`SC-OCC-0003` (`adapter` §8); the capture entry explains the cut, the check finding judges it, and
neither channel suppresses the other. Where a producer refuses by exception (`structure` build,
`engine` planning), the code rides on the rejection type — `BlueprintRejected`/`PlanRejected` carry
the `CodeEntry` — and no caller parses a message (D-66).

## 7. Tests

- Code parse/text round-trip; malformed parse returns null; `Codes.all` has 66 entries, one per code,
  every entry has an explanation; every code in `all` appears in the producer table; `SC-MC-*` are
  absent from `all`.
- Wire tags: every `wireName()` and every `Family` tag is the uppercase form; `text()`/`parse`
  round-trip `SC-<TAG>-<NNNN>`, and the JSON renderer emits tags rather than constant names.
- Host code: an opaque `SC-MC-*` refusal renders and orders without a `CodeEntry`; it never enters
  `Codes.all`; a relayed code already in `Codes.all` is replaced by `SC-OCC-0004` with a note.
- Host catalog: a host's own `CodeEntry` carries that code's severity and makes it explainable
  through `StructureKernel.explain`, and the code still never enters `Codes.all`; a `CaptureReport`
  entry relays under the same trust guard as a `ReadRefusal`. The adapter's `SC-MC-0006`…`0010`
  entries are explainable the same way, and `0006`–`0009` surface as mismatches while `0010`
  surfaces as unreadable.
- Finding builder: primary label requirement, message defaulting, label sort, blank rejection.
- Bundle: dedup, order, caps (errors uncapped), truncation note, idempotent build, filter prefixes.
- Renderers: golden human text for a door/finding; JSON schema fields; no fabricated source line.
- `SourceText` line/column for CRLF, no trailing newline, first/last offset.

## 8. Dependencies

`core` (`NamespacedId`, `Phase`, `SourceSpan`, `@Nullable`) and `space` (`Locus`, `CuboidSet`,
`LatticePoint`). It never imports `world`, `structure`, `claim`, `context`, `engine` or `spi`.
