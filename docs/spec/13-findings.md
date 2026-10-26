# 13 — Findings

> Status: design draft v0.1, 2026-10-20. Unit **U9** (`dev.vfyjxf.cumulus.kernel.finding`).
> Serves R-07, R-12, R-14; enforces I-7, I-14. Prior art: ArchitectCumulus `34`–`37`,
> `08-prior-art-diagnostics.md`, `docs/design/diagnostics.md` — reference only, the lexicon
> and the foundation files win.

## 1. Scope

Owns the *value* of a failure report and the machinery that turns a set of reports into
something a human, a log and a CI job can each read: `Finding` and its parts, the stable code
vocabulary, the ordered/deduplicated/capped `FindingBundle`, the `FindingSink` contract, three
rendering projections, and the per-code long form (`ExplainBook`).

Does not own:

| Not here | Owner |
| --- | --- |
| Which check raises which code, and in which stage | U3, U4, U5, U7, U8 |
| `Locus`, `LocusFrame`, `CuboidSet`, their frame mapping | U1 |
| The code→`Explanation` registry instance set and `Codes` constants | U11 |
| `Remedy`, `EditPlan`, `OverlayModel`; a suggestion's concrete fix | U10 (forward reference: `Suggestion.remedy` only names a provider id) |
| Localisation catalogues, terminal detection, log appenders | host / A1 |
| Suppression (`#[allow]`-shaped), a suppression registry | not modelled (§4.1) |

The one structural rule this unit must not break: **a finding is a value, never an emitter
side effect.** Producers hand findings to a sink; the sink never decides; only `build()` orders,
dedups and caps.

## 2. Vocabulary

All rows are glossary §K. Two names this file introduces are U9-local but referenced by hosts;
both have rows.

| Name | Shape | Owns |
| --- | --- | --- |
| `Severity` | enum `ERROR, WARNING, INFO` | how much a finding counts; declaration order is descending strength |
| `FindingCode` | record `(Family family, int number)` | stable identity `SC-<FAMILY>-<NNNN>`; `Family` is a nested 15-case enum |
| `Finding` | record, 9 components (`id`, `code`, `severity`, `message`, `labels`, `notes`, `suggestion`, `related`, `stage`) | one report; validates and canonicalises itself |
| `FindingId` | record `(int value)` | addressability inside one bundle, for `related` |
| `Label` | record `(Locus, String, LabelRole)` | one located statement; the span lives in its `Locus` |
| `LabelRole` | enum `PRIMARY, SECONDARY, CONTEXT` | what a label asserts |
| `Note` | record `(String, @Nullable Locus)` | a fact with no cell of its own (a property name) |
| `Suggestion` | record `(String, Applicability, @Nullable NamespacedId)` | a proposed change, no plan |
| `Applicability` | enum, 4 rungs | how safe the proposal is; only the top rung may auto-apply |
| `SourceSpan` | record `(NamespacedId document, int start, int end)` | half-open character range in one document |
| `FindingBundle` | interface + `Builder` | the ordered, deduplicated, capped report of one attempt |
| `FindingSink` | interface `accept`, `acceptAll` | a push consumer that never decides |
| `FindingOptions` | record `(int maxTotal, int maxPerCode, boolean includeInfo, boolean includeNotes)` | the emission knobs |
| `FindingRenderer` | interface `render(Finding, RenderContext)` | pure projection to text |
| `ExplainBook` | interface `explain` | the code→long-form registry view |
| `Explanation` | record `(code, summary, body, seeAlso)` | one code's long form |
| `RenderContext` | record (U9-local) | sources, width, colour, locale — everything a render reads |
| `SourceText` | record (U9-local) | one document's text plus offset→line mapping |

`Locus`, `LocusFrame`, `CuboidSet`, `Stage`, `NamespacedId` are referenced, not owned. `Locus`
is assumed to be `(LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan span)`
with those accessors; `LocusFrame` is `DEFINITION, LOCAL, WORLD, KEY` in that declaration order.
A `KEY` locus carries `(InstanceId, NamespacedId)` — the instance and the `ContextKey` id — in
`key`, an **empty** `cells` and no span.

## 3. API design

```java
package dev.vfyjxf.cumulus.kernel.finding;

public enum Severity {
    ERROR, WARNING, INFO;
    public int rank();                              // ERROR 0, WARNING 1, INFO 2
    public boolean atLeast(Severity floor);
}

public record FindingCode(Family family, int number) implements Comparable<FindingCode> {
    public enum Family {
        SPC, OCC, EXP, FOC, CLM, BPT, BAY, CHK, DOC, ENG, CTX, DIA, RMD, SPI, MC;
        public String subject();                    // "space", "claims", "findings", …
    }
    public static FindingCode of(Family family, int number);   // 1..9999, else IAE
    public static FindingCode of(String text);                  // parse or IAE; UI/sketch convenience
    public static @Nullable FindingCode parse(String text);    // null when malformed
    public String text();                                      // "SC-EXP-0001"
    @Override public int compareTo(FindingCode other);         // family ordinal, then number
}

public record SourceSpan(NamespacedId document, int start, int end) {   // half-open, 0 <= start <= end
    public static SourceSpan of(NamespacedId document, int start, int end);
    public int length();
    public boolean contains(SourceSpan other);
    public boolean contains(int offset);
}

public enum LabelRole {
    PRIMARY, SECONDARY, CONTEXT;
    public int rank();                                         // 0, 1, 2
}

public record Label(Locus locus, String message, LabelRole role) {
    public static Label primary(Locus locus, String message);
    public static Label secondary(Locus locus, String message);
    public static Label context(Locus locus, String message);
}

public record Note(String message, @Nullable Locus locus) {
    public static Note of(String message);
}

public enum Applicability {
    MACHINE_APPLICABLE, MAYBE_INCORRECT, HAS_PLACEHOLDERS, UNSPECIFIED;
    public int rank();                                         // 0..3, ladder top first
}

public record Suggestion(String message, Applicability applicability,
                         @Nullable NamespacedId remedy) {}      // the id of a RemedyProvider

public record FindingId(int value) {
    public static final FindingId NONE = new FindingId(0);
    public static FindingId of(int value);                      // value > 0, else IAE
    public boolean assigned();
}

public record Finding(FindingId id, FindingCode code, Severity severity, String message, List<Label> labels,
                      List<Note> notes, @Nullable Suggestion suggestion,
                      List<FindingId> related, Stage stage) {
    public Finding { /* validates, copies, sorts labels — §4.2 */ }
    public static Builder builder(FindingCode code);
    public @Nullable Label primaryLabel();
    public List<Label> labelsOf(LabelRole role);
    public boolean hasPrimary();
    public Finding withSuggestion(Suggestion suggestion);
    public Finding withRelated(List<FindingId> related);
    public Finding withId(FindingId id);          // re-mint a cached finding

    public static final class Builder {
        public Builder id(FindingId id);
        public Builder severity(Severity severity);
        public Builder message(String message);
        public Builder label(Label label);
        public Builder label(Locus locus, String message);      // PRIMARY
        public Builder label(Locus locus, LabelRole role, String message);   // explicit role
        public Builder secondary(Locus locus, String message);
        public Builder note(String message);
        public Builder note(String message, Locus locus);
        public Builder suggest(String message, Applicability applicability, @Nullable NamespacedId remedy);
        public Builder related(FindingId id);
        public Builder stage(Stage stage);
        public Finding build();                                 // IAE on a defect — §4.2
    }
}

public interface FindingBundle {
    List<Finding> findings();                                   // canonical order
    int size();
    boolean isEmpty();
    @Nullable Finding primary();                                // findings().get(0), or null
    int elided();
    int filtered();
    FindingOptions options();
    List<Finding> ofCode(FindingCode code);
    List<Finding> ofSeverity(Severity severity);
    FindingBundle filter(FindingOptions narrower);

    static FindingBundle empty();
    static FindingBundle of(List<Finding> findings, FindingOptions options);   // list order = insertion order
    static Builder builder(FindingOptions options);

    interface Builder extends FindingSink {
        Builder add(Finding finding);
        FindingBundle build();                                  // idempotent
    }
}

public interface FindingSink {
    void accept(Finding finding);
    default void acceptAll(FindingBundle bundle);               // bundle order, no re-filtering
    static FindingSink noop();
    static FindingSink guarded(FindingSink delegate, java.util.function.Consumer<RuntimeException> onFailure);
}

public record FindingOptions(int maxTotal, int maxPerCode, boolean includeInfo, boolean includeNotes) {
    public static FindingOptions defaults();                    // MAX_VALUE, MAX_VALUE, true, true
    public FindingOptions narrowedBy(FindingOptions other);      // min of caps, both booleans by AND
    public FindingOptions withMaxTotal(int maxTotal);            // bounds WARNING/INFO, never ERROR
    public FindingOptions withMaxPerCode(int maxPerCode);        // bounds WARNING/INFO, never ERROR
}

public interface FindingRenderer {
    String render(Finding finding, RenderContext context);
    default String render(FindingBundle bundle, RenderContext context);   // newline join
    static FindingRenderer human();
    static FindingRenderer json();
    static FindingRenderer sarif();
}

public record RenderContext(List<SourceText> sources, @Nullable NamespacedId document,
                            int width, ColorMode color, Locale locale) {
    public enum ColorMode { PLAIN, ANSI }
    public static RenderContext plain();                        // no sources, width 0, PLAIN, Locale.ROOT
    public static RenderContext of(SourceText source);
    public @Nullable SourceText sourceFor(NamespacedId id);
    public RenderContext withWidth(int width);
}

public record SourceText(NamespacedId id, String text) {
    public static SourceText of(NamespacedId id, String text);
    public @Nullable String lineAt(int offset);                 // null outside [0, length]
    public int lineNumber(int offset);                          // 1-based
    public int column(int offset);                              // 1-based, in code points
}

public interface ExplainBook {
    @Nullable Explanation explain(FindingCode code);
    @Nullable Explanation explain(NamespacedId topic);
    List<FindingCode> codes();
    Explanation explainOrFallback(FindingCode code);             // never null — §4.8
}

public record Explanation(FindingCode code, String summary, String body,
                          List<NamespacedId> seeAlso) {}
```

| Contract | Rule |
| --- | --- |
| `Finding` is a record, not a builder-built opaque type | structural equality is what dedup and `related`-free tests read; 9 components, most required, so 03 §4 keeps it a record |
| `Finding.Builder` exists for ergonomics only | it produces exactly one `Finding` and is discarded; it is not a second value type and adds no state a `Finding` can hold |
| No member throws except `of(…)`/`build()` on a violated programmer precondition | I-14; there is no throwing stub and no `UnsupportedOperationException` anywhere in this unit |
| Every list-returning member returns an immutable copy, never `null` | 04 §4 |
| `RenderContext` is the *only* input to a renderer besides the value | nothing ambient, no clock, no environment, no catalogue lookup (§4.7) |

## 4. Semantics

### 4.1 The rustc mapping, and what is refused

| rustc | StructureCumulus | Ruling |
| --- | --- | --- |
| level `error`/`warning`/`help`/`note` | `Severity` (3 values) | BORROW-SHAPE: `help` and `note` are fields (`Suggestion`, `Note`), not levels — a note is not a milder error |
| code `E0123`, optional for lints | `FindingCode`, mandatory | REJECT optionality: no consumer may ever need the message text (I-7), so every finding names its code |
| flat global code | `SC-<FAMILY>-<NNNN>` | REJECT flat: fifteen families let a reader see which unit failed, and let a family's numbers be allocated by one owner |
| message | `Finding.message` | one line, prose, never parsed, never part of a key except the note case in §4.5 |
| primary span + label | `Label(PRIMARY)` | at most one (§4.2) |
| secondary span + label | `Label(SECONDARY)` | many; adds what the primary cannot say |
| a span's own child level | — | REJECT: a label has no severity; severity is the finding's |
| `note:` children | `Note` | with an optional `Locus` — a note may point somewhere or nowhere |
| `help:` | `Suggestion` + `Applicability` | the fix is a *hint*; the plan is U10's |
| `span_suggestion` (replacement text on a span) | `Suggestion.remedy` | REJECT the shape: a repair is a plan over cells, not a string over characters (R-12) |
| related diagnostics (clippy/ESLint) | `Finding.related` | ids, never a nested object graph (04 §2) |
| `--error-format=json` object | §4.7 JSON projection | ADOPT with a version, unlike rustc's unpromised schema |
| `--explain CODE` | `ExplainBook` | ADOPT: one entry per shipped code |
| macro expansion backtrace | — | REFUSED: the kernel has no macro system; a finding names the cell and the document, never "expanded from" |
| spans into generated code | — | REFUSED *as a span chain*: a finding about a generated segment points into the generated text, and `DocumentProvenance` (U6) is the only bridge back to whatever generated it |
| incremental query cache (`-Zquery-dep-graph`) | — | REFUSED: findings are not cached. A targeted re-check is `check` with a `ChangeSet` on the request (U7); a cached finding would be a second source of truth |
| `#[allow]`/lint levels | — | REFUSED: no suppression exists in the kernel. A host filters through `FindingOptions`, and a filtered finding is counted, never silent (§4.5) |
| `rendered` string beside the data | — | REFUSED: prose on the wire invites string-matching; the human form is a function of the value plus a context |

### 4.2 Construction and the invariants it can enforce

`Finding`'s compact constructor validates and canonicalises; `build()` returns the record, so
every `Finding` in existence satisfies the local invariants. Three kinds of rule:

| Kind | Rule | Enforced by |
| --- | --- | --- |
| local, unconditional | `code`, `severity`, `stage`, `message` non-null; `message` non-blank after `strip()`; at most one `PRIMARY` label; every label's `message` non-blank; `related` contains no `FindingId.NONE`; `suggestion.message` non-blank; a `KEY` locus has no cells | `Finding` ctor → `IllegalArgumentException` |
| local, span/frame agreement | `locus.frame() == DEFINITION` ⟺ `locus.source() != null`; a `DEFINITION` locus always names its document and range; `locus.frame() == KEY` ⟺ `locus.key() != null`; a `KEY` locus has no span | `Finding` ctor via `Locus` |
| textual, cannot be local | a `MISMATCH` or `UNREADABLE` reply's findings must each carry a `PRIMARY` label, and an `SC-FOC` finding's primary is over a `KEY` or a cell locus, never a message-only report | U3's `Reply` contract; the engine re-checks at collect and raises `SC-SPI-0001` (§4.3) |

Why the span rule is the frame rule: a `SourceSpan` is an offset into a document. A `WORLD`
locus is a set of cells and a `LOCAL` locus is a set of grid cells; neither has an offset, so a
source span on such a label can only be a bug — typically a producer that kept the definition
span while switching the locus to the cell it maps to. The rule makes that unrepresentable
rather than merely discouraged, and it is why `FindingRenderer` may assume a `DEFINITION` label
is the only one that can draw a code frame.

Why `KEY` carries no cells: a computation's absence, a refused pin and an unstable attempt have
an identity but no occupied cell — the subject is one key of one instance. Writing a synthetic
cell for them would render a world coordinate that looks actionable and is not, which is the
same failure the budget finding refuses (§4.4). So the frame carries exactly its key, and the
two directions of the iff keep a cell in every cell frame.

Why `build()` raises rather than returning a result: a malformed finding is a *programmer*
error in a producer, the same category as `Cuboid.of(min > max)` (03 §5). It is not a
structural outcome — the engine's structural outcome is `Reply`/`MatchResult`. The boundary
that keeps this from unwinding a tick is the extension guard: the engine calls extensions
under a guard that converts an `IllegalArgumentException` into `SC-SPI-0001` (`SC-SPI-0002`
for a thrown non-IAE) and continues. U9 owns the codes; U7 owns the guard.

Message defaulting: if `message` was never set, `build()` uses the `PRIMARY` label's message;
with no primary label the producer must set one, because a label-less finding (budget,
truncation, ledger) has no other channel for its subject. This is what makes the
`Finding.builder(code).label(…).build()` form of `04-api-doctrine.md` §9 compile.

### 4.3 Codes

Format: `SC-` `<FAMILY>` `-` `<NNNN>`, family a closed 15-case enum, number `1..9999`
zero-padded. `FindingCode.parse` accepts exactly that grammar (plus the numeric range) and
returns `null` otherwise; `text()` and `parse` are inverse on well-formed input. Two codes
compare by family ordinal in declaration order
`SPC OCC EXP FOC CLM BPT BAY CHK DOC ENG CTX DIA RMD SPI MC`, then by number.

| Family | Subject | Raiser |
| --- | --- | --- |
| `SPC` | space, orientation, frame mapping | U1 |
| `OCC` | occupants and snapshot reads | U2 |
| `EXP` | expectations and bays | U3 |
| `FOC` | focuses: which subject a check bound, and why none | U3 |
| `CLM` | claims, reservations, ledger | U4 |
| `BPT` | blueprint shape, anchors, derivations | U5 |
| `BAY` | bay coverage, footprint, entity span | U5 |
| `CHK` | check declaration: bay ownership, coverage match, stage reads | U3 |
| `DOC` | documents and assembly | U6 |
| `ENG` | the attempt: budget, steps, cancellation | U7 |
| `CTX` | computations, keys, frames | U8 |
| `DIA` | the finding layer itself | U9 |
| `RMD` | remedy planning and application | U10 |
| `SPI` | catalog/extension contract violations | U11 |
| `MC` | the adapter | A1 |

The shipped allocation, with the severity and the primary-label requirement a producer copies.
`P` = the code requires a `PRIMARY` label; a `–` marks the label-less codes, which are the only
ones for which `FindingBundle`'s "no primary" ordering rank is reachable.

| Code | Sev | P | Meaning |
| --- | --- | --- | --- |
| `SC-SPC-0001` | ERROR | ✓ | a `Locus` cannot be mapped into the requested frame for this siting |
| `SC-OCC-0001` | ERROR | ✓ | the cell is `UNREADABLE`; the snapshot cannot answer, and "absent" is not "air" |
| `SC-OCC-0002` | WARNING | ✓ | an occupant's footprint leaves the snapshot; only the intersection was compared |
| `SC-EXP-0001` | ERROR | ✓ | an element test failed: the subject is not the declared type, or is not air where air was declared; the default `mismatchCode` of every shipped test |
| `SC-EXP-0002` | ERROR | ✓ | a negated test matched, so `not(child)` refutes |
| `SC-EXP-0003` | ERROR | ✓ | two matches of one check bound one key to two different values: a bindings conflict, never an exception |
| `SC-EXP-0004` | ERROR | ✓ | a threshold cannot be met: `atLeast(k)` with `k > n`, or fewer matching children than `k` can reach |
| `SC-FOC-0101` | ERROR | ✓ | a focus found no subject where the bay requires one; primary over the bay's cell or the key |
| `SC-FOC-0201` | ERROR | ✓ | more than one occupant could be the subject, so the binding is ambiguous |
| `SC-FOC-0301` | ERROR | ✓ | a section in the unit's region was refused (`Refused(status, code)`); the value is `Missing`, never a short sum |
| `SC-FOC-0302` | ERROR | ✓ | one section's partial contribution is missing, so the fold has no value |
| `SC-CLM-0001` | ERROR | ✓ | two `EXCLUSIVE` claims of different owners name the same cell of one world |
| `SC-CLM-0002` | ERROR | ✓ | a settle conflicts with an incumbent claim; the finding carries the incumbent rows |
| `SC-CLM-0003` | ERROR | ✓ | this `SHARED` bay meets another owner's `EXCLUSIVE` |
| `SC-CLM-0004` | WARNING | – | the ledger cannot answer, or the revision moved twice: no conclusion |
| `SC-CLM-0005` | ERROR | ✓ | a settle's bindings are not valid for this request: a bay that is not this blueprint's, a subject of another capture, a kind that does not match, or cells outside the bay; nothing is written |
| `SC-CLM-0006` | INFO | – | claims produced by a settle were returned unused because the attempt did not form |
| `SC-CLM-0007` | ERROR | – | a settle's parts disagree on their world — a row, the request, or the ledger handle; refused before anything is written |
| `SC-CLM-0104` | WARNING | ✓ | a blueprint's bounds are not covered by any claim footprint (`None` or a partial `Bound`) |
| `SC-BPT-0001` | ERROR | ✓ | a bay's cuboid is empty or escapes the blueprint bounds |
| `SC-BPT-0002` | ERROR | ✓ | two bays overlap with sharing values that cannot coexist |
| `SC-BAY-0107` | ERROR | ✓ | `ONE_SUBJECT` without subject-kind checks, or a bound footprint that does not equal the bay (rule R3) |
| `SC-BAY-0108` | ERROR | ✓ | a non-`None` bay's `reads().cells` leave `bay.cells ∪ declared footprint` (SETTLE) |
| `SC-BAY-0109` | ERROR | ✓ | a bound entity's footprint left `Bay.cells` at SETTLE |
| `SC-BAY-0110` | ERROR | ✓ | an entity type is wider than `MAX_ENTITY_SPAN`; the bay cannot be matched |
| `SC-BAY-0111` | ERROR | ✓ | an `EXCLUSIVE` bay with a `Bound` footprint bound nothing, so the claim would be empty (R2) |
| `SC-CHK-0101` | ERROR | ✓ | a check pairs a focus and an expectation whose `subject()` differ |
| `SC-CHK-0102` | ERROR | ✓ | the computation/derivation DAG has a cycle |
| `SC-CHK-0106` | ERROR | ✓ | a declaration that contradicts itself: `EXCLUSIVE` with a `None` footprint (R1), or checks that do not match the coverage (R4) |
| `SC-CHK-0107` | ERROR | ✓ | a check belongs to no bay, or a bay asserts nothing: a structure-level check and a bay with an empty `Requirement` are both seams, not declarations |
| `SC-CHK-0109` | ERROR | ✓ | a check reads a context key produced at a later stage |
| `SC-DOC-0001` | ERROR | ✓ | `schemaVersion` is outside the supported range |
| `SC-DOC-0002` | ERROR | ✓ | a required field is absent or of the wrong data kind |
| `SC-DOC-0003` | ERROR | ✓ | an assembly names a segment the library does not hold |
| `SC-ENG-0001` | ERROR | – | the budget ran out before the plan finished (points at the step, not a cell — §4.4) |
| `SC-ENG-0002` | WARNING | – | the attempt was cancelled at a step boundary |
| `SC-ENG-0003` | WARNING | – | the watched region changed after capture, so the attempt is `Dropped(SNAPSHOT_CHANGED)` |
| `SC-ENG-0004` | INFO | – | steps were never reached because the plan stopped |
| `SC-ENG-0005` | WARNING | – | retired: slot supersession is the scheduler's decision and is reported as `MatchCompletion.Discarded`, so the kernel has no producer (`19` R37) |
| `SC-ENG-0006` | WARNING | – | the capture is no longer valid: its world was replaced, or the lease was closed; `Dropped(CAPTURE_INVALID)` |
| `SC-ENG-0104` | ERROR | ✓ | the anchor origin cell is readable but yields no heading that satisfies `candidate.apply(authoredFront) == anchorHeading`; the attempt is `Failed` |
| `SC-CTX-0101` | ERROR | ✓ | a document names a unit, key or deriver the catalog does not hold; unresolved at DESIGN |
| `SC-CTX-0102` | ERROR | – | two registrations of one id; the second is refused at registration |
| `SC-CTX-0107` | ERROR | ✓ | two different values for one key in one frame; the publish is refused |
| `SC-CTX-0108` | ERROR | ✓ | a publish's `baseRevision` is stale; nothing is published and a slow `derive` cannot overwrite a newer generation |
| `SC-CTX-0109` | ERROR | ✓ | the wrong verb for a key: a `put` targets a section-contributed key (`merge != null`), or a `contribute` targets a single-writer key; the write is refused |
| `SC-CTX-0201` | WARNING | ✓ | a computation declined to produce its key (U8 owns the `SC-CTX` block) |
| `SC-CTX-0202` | ERROR | ✓ | a reader required a key a frame does not hold; the value is absent, never a default |
| `SC-CTX-0301` | WARNING | – | a fold produced a non-finite total from finite inputs; the key is omitted |
| `SC-DIA-0001` | WARNING | – | the bundle was truncated: findings were elided by a cap or filtered by the floor |
| `SC-DIA-0002` | WARNING | ✓ | a finding's severity disagrees with its code's registered severity |
| `SC-DIA-0003` | WARNING | – | two findings shared a `FindingId`, so `related` is ambiguous |
| `SC-DIA-0004` | INFO | ✓ | a code in this bundle has no `ExplainBook` entry in this build |
| `SC-RMD-0001` | ERROR | ✓ | a repair plan failed the dry run; the specific row is one of `SC-RMD-02xx`/`03xx` |
| `SC-RMD-0309` | ERROR | ✓ | the plan's post-state changes a `ContextKey` a provider read; the plan is refused |
| `SC-SPI-0001` | ERROR | ✓ | an extension returned a finding the code's shape requires a primary label for |
| `SC-SPI-0002` | ERROR | ✓ | a document names an extension id this catalog snapshot does not hold |
| `SC-MC-0001` | WARNING | ✓ | the capture skipped an unloaded region; its cells are `NOT_CAPTURED` |
| `SC-MC-0404` | WARNING | ✓ | an attempt was discarded past `maxDiscards` and settled `Undecided(DIRTY_UNSTABLE)`; primary over the instance's key |
| `SC-MC-0502` | ERROR | ✓ | `retain(PinBudget)` refused the pin (`PIN_REFUSED`); the attempt cannot proceed on this lease |
| `SC-MC-0601` | ERROR | ✓ | the worker raised an unexpected `RuntimeException`; the attempt is `Undecided(WORKER_FAILED)` and the worker survives |

Stability: a code's meaning is frozen at allocation. A code is never reused, never renumbered,
and never re-pointed; a retired code stays orphaned with its `Explanation` intact and no raiser.
The rows above are representative, not the full allocation: each unit file (`05`–`16`) owns its
`SC-<FAMILY>` block and enumerates its codes there, and `SC-CTX` belongs to U8, so a sample here
must not mint a second meaning for a number U8 already uses. The newer families are allocated the
same way: `SC-FOC` and `SC-CHK` to U3, `SC-BAY` to U5, `SC-RMD` to U10.
A behaviour change that alters what an existing code *means* is a new code. Numbers are
append-only per family, so a diff of the allocation table shows additions only. `Stability`
(U0) rides on the registry entry, not on the finding: a `DEPRECATED` code renders exactly like a
stable one — deprecation is advice to a producer, never a reason for a consumer to stop reading.

Two consequences of the revision-3 consolidation, both recorded here rather than applied
silently. `FOC`, `BAY`, `CHK` and `RMD` are **inserted** into the family order at fixed
positions, which leaves the relative order of every earlier family unchanged, so no existing
bundle re-sorts across families. `RMD` takes the slot `REM` held: the remedy family is named
`SC-RMD` everywhere (`22` §11.1, §11.3), `SC-REM` never carried a shipped code, and the rows in
`14` are the renumbered block; a code that had shipped under `REM` would have had to stay
orphaned instead.

Unknown-code behaviour has three distinct answers, and none of them is an exception:

| Situation | Behaviour |
| --- | --- |
| `parse` of a malformed string (`SC-XXX-0001`, `SC-EXP-0`, `SC-EXP-00000`) | returns `null`; nothing in the kernel dereferences a `@Nullable` parse result without a null check |
| a well-formed code with no registry entry | constructs, orders and renders normally — severity and labels come from the finding, so the report is complete; `JSON` keeps the code text and `SC-DIA-0004` is emitted once per such code when the bundle is built against a book |
| `ExplainBook.explain` on that code | `null`; `explainOrFallback` synthesises the family-level explanation (§4.8) |

Registry ownership: U11's `Catalogs` holds the code inventory (allocation table, canonical
severity, `Explanation` per code) and exposes one constant per shipped code through `Codes`. U9
owns the *format*, the family list, the ordering and the semantics above; U11 owns the
*instances* and refuses a duplicate or a code without an explanation at catalogue build
(`SC-SPI-…`). A producer copies the severity from the registry; a mismatch is `SC-DIA-0002`,
raised by the catalogue self-check in `DESIGN`, never by the engine (a `Finding` must render
without a catalogue in hand).

### 4.4 Locus and Label

| Frame | Points at | `CuboidSet` | `SourceSpan` |
| --- | --- | --- | --- |
| `DEFINITION` | the authoring document and the declared cells | the bay/segment cells as declared | required by L-1: `Locus.source` names the document and range |
| `LOCAL` | the blueprint's grid | cells in blueprint coordinates | never |
| `WORLD` | the snapshot | cells in world coordinates at one `Siting` | never |
| `KEY` | one `ContextKey` id of one instance | empty, by construction | never |

`Locus.source` is the *refined* highlight: the locus says which cells are meant, the source span
says which characters of the declaration are underlined. A code-built blueprint has no text
position, so it has no `DEFINITION` locus; it uses `LOCAL`. Role semantics:

| Role | Asserts | Count | Example |
| --- | --- | --- | --- |
| `PRIMARY` | *here is the fault* | at most one | the world cell holding the wrong block |
| `SECONDARY` | *and this is the other half of the story* | any | the second door part; the declaration |
| `CONTEXT` | *this is the neighbourhood, not the fault* | any | the anchor cell, the seam the bay should have met |

Canonical label order inside one finding — the list is sorted by the constructor, so a producer's
call order can never leak into the value:

```
( role.rank(), frame.rank(), position(), source.start(), insertion )
```
`frame.rank()` is `DEFINITION 0, LOCAL 1, WORLD 2, KEY 3`; `position()` is `span.start()` for a
`DEFINITION` label with a span, `0` for a `KEY` label, else the canonical first cell of the locus
(lowest `y`, then `z`, then `x`); `insertion` is the producer's declaration index and is a stable
tail, never a primary key. Frame rank precedes position because comparing a document offset with
a world coordinate — or a key with either — is meaningless (the same argument the bundle's order
key makes in §4.5). Two `KEY` labels of one finding therefore tie on every component but
insertion, which is why the tail must be there.

The five cases the design must show:

**Block mismatch (one primary, one secondary).** `PRIMARY` on the `WORLD` cell, message
`expected minecraft:bricks, found minecraft:stone_bricks`; `SECONDARY` on the `DEFINITION` bay
with `source` on the expectation literal, message `declared here`. The primary names the fault;
the secondary names the promise.

**Door mismatch (one primary, one secondary, both world).** One expectation tests a two-part
bay atomically and one property disagrees, so one finding carries `PRIMARY` on the first
disagreeing part and `SECONDARY` on the second, plus one `Note` per disagreeing property. It is
deliberately *not* two findings: two findings would share a code and a pair of labels and would
be split by any later reword of a note, whereas one finding with two labels states the
relationship. The definition label, when the producer has one, is a third (`SECONDARY`).

**Claim conflict (two owners on one cell).** `PRIMARY` on the contested `WORLD` cell — the
conflict's subject is the cell, not either owner, which is why there is still exactly one
primary — two `SECONDARY` labels on the two owners' `DEFINITION` loci, and one `Note` per owner
carrying the owner id and its sharing value. Two owners on one cell are therefore expressible without a
second primary and without a synthetic "owner" type: the owner is named in the note, the
location is a label, and a consumer that needs the owner machine-readably reads the claim ledger
(U4), not the finding.

**Budget exhaustion (no cell).** `SC-ENG-0001` carries **no label**: the subject is a
`CheckPlan` unit, and a unit has no cells. Its message names the unit for a human, and the
machine-readable step accounting is `Spend`'s (U7) — the finding's machine channel is its code,
severity and stage. Pointing at "the last cell read" instead, as the prior art did, is the
failure mode this refuses: it renders a world coordinate that looks actionable and is not.

**Missing computation (one primary over a key).** `SC-FOC-0101` when a focus found no subject is
the cell case — primary on the bay's first cell. The same code over `Focuses.context(key)` is the
`KEY` case: `PRIMARY` on `(instance, mymod:capacity)` with the message naming the key, plus a
`SECONDARY` on the `DEFINITION` declaration of that key. `SC-MC-0404` and `SC-MC-0502` are the
same shape for an instance-level fact: one `KEY` primary, never a fabricated cell, and the
message carries the count or the budget that was refused.

### 4.5 The bundle pipeline

`Builder.build()` runs one pass, in this order, and is idempotent:

| # | Step | Effect | Counter |
| --- | --- | --- | --- |
| 1 | stamp | every finding's `stage` must be non-null (a `Finding` always has one; this is a re-check) | — |
| 2 | floor | drop `INFO` when `!includeInfo` (a floor of `WARNING`) | `filtered` |
| 3 | dedup | collapse equal values, keep the first **inserted**; merge `suggestion` and `related` | — |
| 4 | sort | the total order below, over the stored insertion indices | — |
| 5 | per-code cap | keep the first `maxPerCode` of each code **among `WARNING` and `INFO`**; `ERROR` is never capped | `elided` |
| 6 | total cap | keep the first `maxTotal` of the surviving `WARNING`/`INFO` subsequence; every `ERROR` survives | `elided` |
| 7 | note strip | when `!includeNotes`, rebuild each kept finding without notes | — |
| 8 | truncation | if `elided + filtered > 0`, append `SC-DIA-0001` naming both counts, past the cap | — |

Dedup sits before sort so its survivor does not depend on the comparator, and the caps sit after
sort so a cap truncates the documented order instead of a hash order. Two properties follow and
are proved in §7: a smaller cap yields a prefix of a larger one, and a capped bundle is never
`isEmpty()` with zero counters.

**The caps bound noise, never errors.** `ERROR` says "this structure cannot form; fix this cell",
so eliding one hides work the player must do. The cap therefore applies to `WARNING` and `INFO`
only, and a bundle may exceed `maxTotal` by the number of `ERROR`s it carries. A host that wants
to drop errors anyway does so through its own filtering, and `SC-DIA-0001` still records the count.

**The kernel never caps.** `MatchResult.bundle()` is the full canonical bundle under
`FindingOptions.defaults()`; capping happens only when a caller asks for it with
`filter(options)` (or builds with options), so chat, logs and exports can each pick their own
size from one result. The kernel's promise is "every error, in the documented order"; the size of
the report is the host's call.

The total order — five keys, applied once:

| # | Key | Comparison |
| --- | --- | --- |
| 1 | severity rank | `ERROR 0 < WARNING 1 < INFO 2` |
| 2 | stage | `Stage` declaration order `DESIGN, PROBE, FORM, SETTLE` |
| 3 | code | `FindingCode.compareTo`: family ordinal, then number |
| 4 | primary locus | `(frameRank, a, b, c)`; label-less findings rank last with `(4,0,0,0)`; for a `DEFINITION` primary `(0, span.start, span.end, 0)`, for `LOCAL`/`WORLD` `(1|2, y, z, x)` of the canonical first cell, for `KEY` `(3, 0, 0, 0)` |
| 5 | insertion | the attempt's append index, unique within the bundle |

Why severity first: a reader triages by how much the finding counts before anything else, and a
report whose errors are scattered through world coordinates is unreadable. Why stage before
code: the stages are the order in which failures became knowable (DESIGN before PROBE before
FORM), so a diff of two bundles over one blueprint lines up. Why the primary locus before
insertion: within one stage and code, a reader walks the structure in cell order, and a
label-less finding sorts after every located one of its code.

Insertion order is a *legitimate* key only because it is not the walk's incident: the step order
is itself a documented total order (`02-architecture.md` §4), so for a fixed
`(blueprint, snapshotId, siting, policy, catalog, frozen ledger revision, cancellations)` the append
sequence is fixed. Keys 1–4 are
content, so they are also stable across two attempts that discovered the same findings in
different orders; key 5 is the tiebreak of last resort, and it is what makes the comparator
total rather than merely consistent (the door case, and two findings differing only in notes,
both tie on 1–4).

Dedup key — value equality on exactly:

```
( severity, stage, code, labels as [(role, locus, source)] in canonical order, notes as [message] )
```
Excluded: `message`, every label message, `suggestion`, `related`. A message is prose (I-7), so a
reword or a translation must never split one finding into two. A **note** is included, and this
is the one place where prose carries a fact: `Note` is the only channel for a fact that has no
cell (the property name and its two values), so two findings that agree on every label and
differ in a note are different facts and must both survive. The kernel compares note text only
for equality — it never parses it, never branches on it, and no consumer may read it as data.

The survivor keeps its own prose and receives the union of `related` (sorted by value) and, when
it has none, the dropped duplicate's `suggestion`; when both have one, the higher
`Applicability` rung wins and a tie keeps the survivor's. That is the whole of "equivalence
preserving": every key component is equal by construction, and the only things that can differ
are prose and additive hints, which merge rather than vanish.

`FindingOptions` narrowing: `narrowedBy` takes the smaller cap and ANDs the booleans, so a sink
may narrow a bundle's options but never widen them. `includeInfo=false` is read as a
`WARNING` floor; the glossary fixes this boolean, so a three-valued floor would be an additive
change (§8 OQ-5). `includeNotes=false` is applied last so it can never influence which findings
survive; it is irreversible on a built bundle, and `filter` documents that.

`filter(options)`: removes any existing `SC-DIA-0001`, re-applies steps 2, 5–8 against the stored
findings and their stored insertion indices, and returns a new bundle. Counters of the result
count what *this* narrowing dropped, not the source's history; a caller that needs the total
keeps both bundles.

### 4.6 Sinks and threading

| Rule | Statement |
| --- | --- |
| Thread contract | a sink is called from one thread only — the attempt's. `FindingBundle.Builder` is not thread-safe and is not shared; a built bundle is immutable and may be read from any thread |
| No decisions | `accept` appends. A sink may not sort, dedup, filter, cap, or reorder. Only `build()` decides, so a streaming sink and a bundle can never disagree |
| No exceptions | `accept` must not throw. The kernel attaches `FindingSink.guarded(delegate, onFailure)` around every host sink; a throw is counted through `onFailure` and the attempt continues. A sink that unwinds a tick is a defect, not a failure mode |
| No re-entry | a sink must not call back into the engine (no `match`, no `solve`, no `explain`-driven re-match). Re-entry would make the report depend on the reporter |
| No message parsing | a sink reads `code()`, `severity()`, `stage()`, `labels()`, `related()`. A sink that branches on `message()` is broken by construction |
| No blocking | `accept` does not block: no I/O, no lock a producer could hold, no future join. A host that needs a queue drains it after `build()` |
| Emission is outside the budget | `Budget` charges reads, not reports; the cap is the report's budget (`maxTotal`), spent on `WARNING`/`INFO` only |

### 4.7 Rendering

Three shipped renderers, one interface, one rule: **a renderer is a pure function of
`(value, RenderContext)` and never changes the bundle it was handed.** `render` does not sort,
filter, mutate, cache or memoise, and calling it twice with equal arguments returns equal
strings.

| Renderer | Per finding | Per bundle | Determinism notes |
| --- | --- | --- | --- |
| `human()` | a rustc-shaped block: header `severity[code]: message`, a `-->` line naming the primary frame, a source frame with carets for a `DEFINITION` label that has a `SourceText`, one `= frame …` line per `LOCAL`/`WORLD` label, then `= note:` and `= help:` lines | blocks joined by a blank line, then a `= N elided, M filtered` trailer when counters are non-zero | reads only the label list and the context; wraps at `width` when `width > 0` |
| `json()` | one object per finding, one line | `{"schema":1,"findings":[…],"elided":n,"filtered":m}` | ignores `width`, `color` and `locale`: machine output is neither wrapped, coloured nor translated |
| `sarif()` | one SARIF `result` with `ruleId` = code text | a `run` whose `rules[]` is built in first-appearance order over the bundle | `rules[]` is a `LinkedHashMap` keyed by code, populated by one ordered scan — never a hash iteration |

`RenderContext` is the entire environment: `sources` (the document texts this report may cite),
`document` (the id a `DEFINITION` label is assumed to belong to when exactly one source is
present), `width` (0 = no wrap), `color` (`PLAIN` or `ANSI`; there is no `AUTO`, because the
kernel cannot detect a terminal — I-15 — so the host decides), `locale` (affects decoration text
only, never the code, the labels or the structure). The human block for a wall mismatch:

```text
error[SC-EXP-0001]: expected minecraft:bricks, found minecraft:stone_bricks
  --> structurecumulus:huge_furnace 14:21
   |
 14 |         "wall": { "expect": "minecraft:bricks" }
    |                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ declared here
   |
   = world (112, 64, 208) .. (113, 65, 209)  the wall bay, 1 occupant of another type
   = note: the bay declared one type and matched another
   = help: replace it (machine applicable, remedy structurecumulus:replace_block)
```

The JSON schema is versioned and additive: fields are added, never removed and never retyped, and
`schema` is bumped only for a removal or a retype. Fields: `code`, `severity` (`error`/`warning`/
`info`), `stage` (declaration name), `message`, `labels[]` (`role`, `frame`, `cells` as
`"x,y,z"` strings or cuboid triples, `source` as `{start,end}` or absent, `message`), `notes[]`
(`message`, optional `locus`), `suggestion` (`message`, `applicability`, optional `remedy`),
`related[]` (ints). The SARIF projection maps `ERROR→error`, `WARNING→warning`, `INFO→note`;
a `DEFINITION` label becomes a `physicalLocation` when a source resolves, a `LOCAL`/`WORLD` label
becomes a `logicalLocation` plus a `properties.cells` array, because SARIF has no world
coordinate concept and inventing a fake URI would be a false anchor. SARIF's `rules[].id` is the
code text and its `shortDescription` is the `Explanation.summary` when the book has one.

A renderer never synthesises a source line: with no `SourceText` for a `DEFINITION` label it
prints the offsets and the cells it was given. A fabricated excerpt is a false anchor, and a
false anchor looks actionable.

### 4.8 `ExplainBook` and `--explain`

One entry per shipped code, mandatory: `Explanation(code, summary, body, seeAlso)` where
`summary` is one sentence (the `shortDescription`), `body` is the long form (what triggers it,
what a reader should check, one worked case) and `seeAlso` is documentation topic ids in the
`structurecumulus` namespace (`structurecumulus:doc/claims`), never code text — a cross-code
link inside a report is `related`'s job.

| Member | Contract |
| --- | --- |
| `explain(FindingCode)` | `@Nullable`; `null` for a code this build does not ship |
| `explain(NamespacedId)` | resolves a `seeAlso` topic; `@Nullable` |
| `codes()` | the shipped inventory, sorted by `FindingCode.compareTo` |
| `explainOrFallback(FindingCode)` | never null: the registered entry, or a synthesised `Explanation` whose summary is `no explanation is registered for SC-…`, whose body is the family's `subject()` sentence, and whose `seeAlso` is empty |

`--explain` (A1's command, U11's facade `StructureKernel.explain`) prints an `Explanation`,
never a `Finding`: summary, blank line, body, then `see also:` lines. A registered code exits 0;
`explainOrFallback` is what a command prints for an unknown one, with exit code 1. This is the
whole relationship between the two: the book is data, a finding cites a code, and the command
reads the book without rendering anything.

## 5. Reference sketch

```java
// Wall bay: expected the declared type, found another. Primary = world, secondary = definition.
static Finding wallMismatch(Locus worldCell, Locus bayDeclaration, SourceSpan expectLiteral,
                            OccupantDescriptor found) {
    return Finding.builder(Codes.EXPECTED_TYPE)
            .stage(Stage.FORM)
            .severity(Severity.ERROR)
            .label(Label.primary(worldCell, "expected " + Codes.EXPECTED_TYPE + ", found " + found.type()))
            .label(Label.secondary(bayDeclaration.withSource(expectLiteral), "declared here"))
            .note("the bay declared " + Codes.EXPECTED_TYPE + " and matched another occupant type")
            .suggest("replace it", Applicability.MACHINE_APPLICABLE,
                     NamespacedId.of("structurecumulus", "replace_block"))   // a RemedyProvider id (U10)
            .build();
}

// Door: one expectation, two parts, one property. One finding, two labels.
static Finding doorDisagree(Locus lower, Locus upper, Locus declaration, String property,
                            String found, String expected) {
    return Finding.builder(Codes.PARTS_DISAGREE)
            .stage(Stage.FORM)
            .label(Label.primary(lower, property + " is " + found + ", expected " + expected))
            .label(Label.secondary(upper, "the other half of the same bay"))
            .label(Label.secondary(declaration, "declared here"))
            .note(property + " differs between the two parts")
            .build();
}

// Claim conflict: one primary cell, two owners' declarations, one note each.
static Finding claimConflict(Locus contestedCell, Locus declarationA, Locus declarationB,
                             NamespacedId ownerA, NamespacedId ownerB) {
    return Finding.builder(Codes.CLAIM_CONFLICT)
            .stage(Stage.SETTLE)
            .label(Label.primary(contestedCell, "claimed by " + ownerA + " and " + ownerB))
            .label(Label.secondary(declarationA, "first claim"))
            .label(Label.secondary(declarationB, "second claim"))
            .note(ownerA + " holds EXCLUSIVE", declarationA)
            .note(ownerB + " holds EXCLUSIVE", declarationB)
            .build();
}

// Budget: no cell. The step is named for a human; Spend carries it for a machine (U7).
static Finding budgetExhausted(NamespacedId step, long reads) {
    return Finding.builder(Codes.BUDGET_EXHAUSTED)
            .stage(Stage.FORM)
            .message("the budget ran out after " + reads + " reads in step " + step)
            .build();
}

// A focus that found no subject: the primary is a KEY over (instance, ContextKey id), never a cell.
static Finding keyAbsent(InstanceId instance, NamespacedId key, Locus declaration) {
    return Finding.builder(Codes.FOCUS_ABSENT)
            .stage(Stage.FORM)
            .label(Label.primary(Locus.key(instance, key), "no value for " + key))
            .label(Label.secondary(declaration, "declared here"))
            .build();
}

// One bundle, capped per code and in total, then read.
FindingBundle.Builder collecting = FindingBundle.builder(
        FindingOptions.defaults().withMaxPerCode(8).withMaxTotal(64));
FindingSink.guarded(collecting, failure -> { /* counted by the caller's adapter */ })
        .accept(wallMismatch(worldCell, bayDecl, expectLiteral, found));
collecting.accept(doorDisagree(lower, upper, bayDecl, "hinge", "right", "left"));
collecting.accept(claimConflict(cell, declA, declB, ownerA, ownerB));
collecting.accept(budgetExhausted(stepId, 4096L));

FindingBundle bundle = collecting.build();
Finding first = bundle.primary();               // non-null here: severity then stage then code
String human = FindingRenderer.human().render(bundle, RenderContext.of(documentText).withWidth(100));
String json = FindingRenderer.json().render(bundle, RenderContext.plain());

// ExplainBook lookup: registered, then an unregistered but well-formed code.
Explanation known = book.explainOrFallback(Codes.CLAIM_CONFLICT);       // registry entry
Explanation unknown = book.explainOrFallback(FindingCode.of(FindingCode.Family.EXP, 999)); // family fallback
```

```text
error[SC-CLM-0001]: claimed by structurecumulus:door_a and structurecumulus:door_b
  --> structurecumulus:huge_furnace 22:9
   |
 22 |         "mouth": { "claim": "exclusive" }
    |         ^^^^^^^^ first claim
   |
   = note: structurecumulus:door_a holds EXCLUSIVE
   = note: structurecumulus:door_b holds EXCLUSIVE
   = world (112, 64, 208)  the contested cell
```

## 6. Use cases

**Addon author — the door expectation.** One `test` call over a two-part bay returns
`Reply.mismatch(doorDisagree(…))`. The author writes two labels and one note per disagreeing
property and never sees a bundle, a code table or a renderer; the code comes from `Codes`, the
stage from the context, and the ordering happens later. If the author forgets the primary label,
`build()` raises inside the extension and the engine turns it into `SC-SPI-0001` rather than
losing the report.

**Host — a `/cumulus why` command.** The command builds a `RenderContext` from the blueprint
document's text with `width = 60`, colour `PLAIN`, and calls
`FindingRenderer.human().render(bundle, ctx)`, then sends each line to chat. It never sorts, never
reads a message, and its output equals what a CI log shows for the same bundle modulo width and
colour.

**Host — a datapack reload.** `DocumentValidator` (U6) emits `SC-DOC-*` findings in `Stage.DESIGN`
through a `FindingSink` that writes JSON lines to a log file. Because DESIGN findings carry a
`DEFINITION` primary with a source span, the log's carets point at the exact field; because the
JSON schema is versioned, a dashboard that has never seen this document can render it.

**Host — a build wand.** A `RemedyProvider` collects `Suggestion.remedy` ids to decide which
previews to offer; it reads `applicability()` and only auto-applies the
`MACHINE_APPLICABLE` rung. U9 supplies the hint; U10 supplies the plan.

## 7. Proofs

**P1 — the order is total.** Every key is a function of stored state: `severity.rank()`,
`stage.ordinal()`, `code.compareTo`, the primary label's frame/position (label list order is
canonical, so "the primary" is unambiguous), and the insertion index. Each of the first four is a
total preorder on values; the fifth is a total order on indices because `build()` assigns each
surviving finding a distinct index and never renumbers. A lexicographic product of total
preorders refined by a total order on a unique final component is a total order: for distinct
`x, y` with identical keys 1–4, `x.index() != y.index()` decides. Antisymmetry and transitivity
follow from the componentwise ones. Counterexample check: two findings with equal severity,
stage, code and primary locus *and* equal insertion index cannot exist — `add` is the only
append path and increments the index.

**P2 — dedup is equivalence-preserving.** The dedup key is tuple equality over immutable
components, hence reflexive, symmetric and transitive; the collapse is a quotient by that
relation, and each survivor's key still equals every member's key, so no key component is lost.
The only differences a duplicate group can hide are outside the key — `message`, label messages,
`suggestion`, `related` — and those merge (suggestion and related) or are prose that the survivor
already states (messages). Therefore the *set of facts* the key encodes is unchanged, and a
consumer's reading of `(code, severity, stage, labels, notes)` cannot distinguish "one finding
collected twice" from "one finding collected once" — which is exactly what dedup must guarantee.

**P3 — a cap preserves every error and at least one notice, or says so.** `ERROR` is outside both
caps, so no error can be elided at any setting. Among `WARNING`/`INFO`, the per-code cap keeps the
first `maxPerCode` of each code and the global cap keeps the first `maxTotal` of that remaining
subsequence; for `maxPerCode >= 1` and `maxTotal >= 1` the subsequence's first element survives
both. The only configurations that leave no notice are `maxTotal = 0` and an all-`INFO` bundle
under `includeInfo = false`; in both cases `elided + filtered > 0`, so step 8 appends
`SC-DIA-0001` and `isEmpty()` is `true` only if the truncation finding is itself filtered — which
it cannot be, since `SC-DIA-0001` is `WARNING`. A capped bundle never reads as a complete clean
report, and a bundle of errors is never reduced by a cap.

**P4 — the code format is stable and round-trips.** The grammar is
`SC-(SPC|OCC|EXP|FOC|CLM|BPT|BAY|CHK|DOC|ENG|CTX|DIA|RMD|SPI|MC)-[0-9]{4}` with
`1 <= number <= 9999`.
`text()` emits exactly that shape (fixed prefix, enum name, `%04d`); `parse` accepts only that
shape and range, so `parse(text(c)) == c` for every constructible `c`, and `text(parse(s)) == s`
for every `s` the grammar accepts. The family is an enum, so a typo (`SC-EXPX-0001`) is
unrepresentable rather than merely unregistered; the four-digit padding plus the range check
means no code has two spellings. Adding a family is a source-incompatible enum change and
therefore a review event, not a silent widening.

**P5 — the renderer is deterministic.** `render` reads the bundle's `findings()` in order, each
finding's canonical label list, and the context. No map iteration reaches the output except in
`sarif()`, where `rules[]` is populated by one ordered scan into a `LinkedHashMap` and looked up
by key only for indices. No clock, hash code, identity, locale default (`locale` is in the
context) or environment is read. Hence equal `(value, context)` yields equal strings, and two
hosts that built equal bundles render equal bytes.

**P6 — no kernel branch reads prose.** The only textual operations are: equality on note
messages (dedup key), and appending strings. No comparison selects a code, a severity, a role or
a branch; `FindingCode` is parsed only from its own grammar, never from a message. The falsifying
test is mechanical: reword every `message` and every label message in a fixture and assert the
bundle's `size()`, `elided()`, `filtered()`, the ordered `code` list and every `(role, locus)` list
are unchanged. The one honest exception is stated in §4.5: a note's text is a dedup component, so
rewording a *note* may change dedup — which is why no consumer may treat note text as data.

## 8. Dependencies and open questions

Depends on: **U0** (`NamespacedId`, `Stage`, `Stability`, `Checks`); **U1** (`Locus`,
`LocusFrame` with its four frames, `CuboidSet` and its canonical order); **U3** (`Reply`
requiring a primary label on `MISMATCH`/`UNREADABLE`); **U4** (`Claim`, for a conflict's notes);
**U7** (`Spend`, the step accounting a label-less budget finding points past); **U11**
(`Catalogs`, `Codes`, the `ExplainBook` instance and the code inventory); **U10** (`Remedy` —
referenced only as a `NamespacedId`). `KeyLocus` is U1's; U9 only requires that a `KEY` frame
carries `(InstanceId, NamespacedId)` and no cells.

Every earlier question in this table is settled by `22` §15 (the resolved rows are gone with it).
One of §16's questions lands on this unit's codes:

| # | Question | Options | Consequence |
| --- | --- | --- | --- |
| OQ-1 (`22` §16 Q1) | Empty-volume protection: is a per-blueprint `sealed: true` flag better than a warning plus a per-bay `Box`? | (A) keep the warning `SC-CLM-0104` plus the per-bay `Footprint.Box` choice; (B) add `sealed` to the blueprint and make the warning unreachable | (A) ships in this unit and keeps the choice visible per bay; (B) needs a glossary row and turns a WARNING into a compile-time guarantee, so the code stays allocated for `Declared`-only blueprints either way |

## 9. Falsification

| # | Claim under test | Concrete test that breaks it |
| --- | --- | --- |
| F1 | the order is total | two findings with equal severity, stage, code and primary locus but different notes, appended in one bundle: if `compare` returns 0 the assertion `findings()` has distinct positions fails; a `TreeSet` built from the bundle must keep both |
| F2 | the cap never drops a notice's primary | 40 `SC-MC-0001` (`WARNING`) findings, `maxTotal = 1`: `primary()` must equal the pre-cap `primary()`, and `size()` must be 1 with `elided() == 39` |
| F2b | no cap drops an error | 40 `SC-BAY-0101` (`ERROR`) findings plus 5 `SC-MC-0001`, `maxTotal = 1`, `maxPerCode = 1`: `size()` must be 40 + 1 + the `SC-DIA-0001`, `ofSeverity(ERROR)` must be 40 and byte-equal to the uncapped errors, `elided() == 4` |
| F3 | the cap is a prefix | build 40 `SC-MC-0001` at `maxTotal = 16` and `64` from one producer run: the first 16 of the 64-bundle must equal the 16-bundle's `findings()`, and both must share `primary()` |
| F4 | a capped bundle is never silently clean | `maxTotal = 0` with 5 findings: `isEmpty()` is false (the `SC-DIA-0001` finding is present), `elided() == 5` |
| F5 | no renderer depends on hash order | a SARIF render over a bundle with codes in the order `EXP, CLM, EXP, ENG`: `rules[].id` must be `[SC-EXP-…, SC-CLM-…, SC-ENG-…]`; run under two JVM `-XX:hashCode` seeds and diff |
| F6 | an unknown code cannot crash a renderer | `FindingCode.of(Family.EXP, 999)` with an empty `ExplainBook`: human, JSON, SARIF and `explainOrFallback` must all produce output naming the code, with no `NPE` and no `Optional` unwrap |
| F7 | a malformed code cannot enter | `FindingCode.parse("SC-XXX-0001")`, `"SC-EXP-0000"`, `"SC-EXP-10000"`, `"sc-exp-0001"` all return `null`; `FindingCode.of(Family.EXP, 0)` raises `IAE` |
| F8 | prose is not the contract | reword every message in a fixture; `size`, counters, ordered codes and ordered `(role, locus)` lists must be byte-identical |
| F9 | a span belongs to its frame | construct a `Label` with a `WORLD` locus and a non-null `source`: `Finding.build()` must raise `IAE`; a `DEFINITION` label whose `source` escapes `locus.span()` likewise |
| F10 | a label-less finding is legal only where the table says so | `budgetExhausted(…)` builds without labels; every `P ✓` code in §4.3 must produce `SC-SPI-0001` when a producer omits the primary, and no `P –` code may |
| F11 | dedup does not swallow a bad stage | two findings identical except `stage` (PROBE vs FORM) must both survive: the dedup key includes stage |
| F12 | a sink cannot unwind the attempt | a sink that throws `RuntimeException` on every `accept`, wrapped by `FindingSink.guarded`: the bundle still builds and the counter increments |
| F13 | a key finding has no cell to point at | `keyAbsent(instance, key, declaration)` in a bundle: `primary().frame() == KEY`, `primary().cells().isEmpty()`, `primary().key()` equals the pair, and a `KEY` label built with a cell or a `SourceSpan` raises `IAE` |
| F14 | a key finding still sorts | one `SC-FOC-0101` over a cell and one over a `KEY`, same severity, stage and code: the cell finding ranks first, and a label-less finding of the same code ranks after both |

The review question that would break this module: *name one finding the kernel ships that needs
two `PRIMARY` labels, or one whose machine-readable content lives only in its message.* If either
exists, §4.2's "at most one primary" or I-7's "codes and loci are the channel" is wrong.
