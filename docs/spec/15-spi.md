# 15 — SPI and facade (U11, `kernel.spi`)

> Design draft v0.1, 2026-10-20. Serves R-10, R-14; enforces I-1, I-8, I-14, I-15. Prior art:
> ArchitectCumulus `41`, `05`, `docs/design/spi.md` — reference only; the foundations win.

## 1. Scope

| Owns | Does not own |
| --- | --- |
| `NamespacedId` — the only naming type, its grammar and total order | Cells, cuboids, sitings (`05`) |
| `Catalog<T>`, `Catalogs`, `ContextKeys` — the frozen extension snapshot and the only key factory | The stored types: `ExpectationFactory` (`07`), `FocusFactory`/`ComputationFactory` (`11`), `RemedyProvider` (`14`), `FindingCode`/`ExplainBook` (`13`) |
| `StructureKernel` — nine operations, their invariants, thread contract | What a match, a computation or a remedy *means* (`11`, `12`, `14`) |
| `Stability`, the shipped `Codes`, the I-8 line | The grammar (`10`), the code format (`13`), every host port |

Nothing here starts a thread, caches an attempt, reads a clock or holds a world: a kernel is
`(Catalogs, Host)` plus nine operations — `document`, `compile`, `siting`, `check`, `settle`,
`derive`, `solve`, `explain` (with `explainOrFallback` counted once with it) and `ledger`. `create`
is a static factory, not an operation.

## 2. Vocabulary

| Name | Shape | Definition |
| --- | --- | --- |
| `NamespacedId` | record `(namespace, path)`, `Comparable` | `ns:path`; the only naming type; ordered namespace then path |
| `Catalog<T>` / `.Builder<T>` | interface / nested builder | one frozen, namespaced, insertion-ordered family; `get` is `@Nullable`; the builder is the only mutable shape |
| `Catalogs` | interface | seven frozen families plus the explanation book, for one kernel |
| `ContextKeys` | final class | the only constructor of a `ContextKey`; registers into the `Catalogs` draft before `build()` (`SC-CTX-0102` on a re-registration) |
| `StructureKernel` | interface, static factories | nine operations: `document`, `compile`, `siting`, `check`, `settle`, `derive`, `solve`, `explain`, `ledger`; `create` is a static factory |
| `MatchResult` | sealed interface, `AutoCloseable` | `Admitted`, `Dropped`, `Formed`, `Failed`, `Undecided`, `Deferred`; only `Formed` carries claims and a revision, and only `settle` produces it |
| `Host` | nested record | read-only ports fixed at create: `DocumentSource`, `SegmentLibrary`, `@Nullable ClaimLedger` |
| `Stability` | enum + `of(name)` | `STABLE`, `EXPERIMENTAL`, `DEPRECATED`; unknown answers `EXPERIMENTAL` |
| `Codes` | final class, constants | the kernel-shipped `FindingCode` instances, one per row of `13` §4.3 |

## 3. API design

```java
package dev.vfyjxf.cumulus.kernel.spi;                              // @NullMarked

public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {
    public static final String DEFAULT_NAMESPACE = "structurecumulus";
    public static NamespacedId of(String namespace, String path);   // IAE on a violated grammar
    public static NamespacedId of(String path);                     // default namespace
    public static @Nullable NamespacedId parse(String text);        // null on malformed; no partial id
    public String text();                                           // "ns:path"
    @Override public int compareTo(NamespacedId other);             // namespace, then path, code point
}

public interface Catalog<T> {
    NamespacedId id();                                              // the family
    @Nullable T get(NamespacedId id);                               // null = not registered
    List<NamespacedId> ids();                                       // frozen, registration order
    SequencedMap<NamespacedId, T> asMap();                          // unmodifiable, same order
    int size();  boolean isEmpty();
    static <T> Builder<T> builder(NamespacedId family);
    static <T> Catalog<T> empty(NamespacedId family);
    interface Builder<T> {
        Builder<T> put(NamespacedId id, T value);                   // records a duplicate for build() to refuse
        Catalog<T> build();                                         // IAE on a repeated id; idempotent
    }
}

public enum Stability {
    STABLE, EXPERIMENTAL, DEPRECATED;
    public static Stability of(NamespacedId name);                  // kernel-owned table
}

public interface Catalogs {
    Catalog<ExpectationFactory> expectations();   Catalog<FocusFactory> focuses();
    Catalog<ComputationFactory> computations();   Catalog<ContextKey<?>> contextKeys();
    Catalog<Deriver<?>> derivers();               Catalog<RemedyProvider> remedies();
    Catalog<FindingCode> codes();
    ExplainBook explanations();
    static Builder builder();                                       // starts from builtin()
    static Catalogs builtin();                                      // kernel entries only
    interface Builder {
        Builder expectations(Catalog<ExpectationFactory> catalog);  // merged over the kernel's family
        Builder focuses(Catalog<FocusFactory> catalog);
        Builder computations(Catalog<ComputationFactory> catalog);
        Builder contextKeys(Catalog<ContextKey<?>> catalog);
        Builder derivers(Catalog<Deriver<?>> catalog);
        Builder remedies(Catalog<RemedyProvider> catalog);
        Builder codes(Catalog<FindingCode> catalog);
        Builder explanations(List<Explanation> entries);            // merged; kernel entries win
        Catalogs build();                                           // IAE on a clash, naming both sides
    }
}

public final class ContextKeys {
    public static <T> ContextKey<T> register(Catalogs.Builder draft, NamespacedId id, Class<T> type,
                                             Retention retention, @Nullable Merge<T> merge,
                                             @Nullable T whenAbsent);
    public static <T> ContextKey<T> register(Catalogs.Builder draft, ContextKey<T> declared);
    // A repeated id is refused by draft.build() as an IAE whose message names SC-CTX-0102 (§4.8).
}

public interface StructureKernel {
    static StructureKernel create(Catalogs catalogs);               // no documents, no ledger
    static StructureKernel create(Catalogs catalogs, Host host);
    @Nullable BlueprintDocument document(NamespacedId id);          // null = this host lacks it
    CompileResult compile(BlueprintDocument document);              // DESIGN: validate + assemble
    Outcome<Siting> siting(Blueprint blueprint, LatticePoint origin, WorldView view);   // anchor read
    MatchResult check(MatchRequest request);                        // read-only: no claim, no frame
    MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);  // the only writer; closes `admitted`
    StructureContext derive(MatchResult.Formed formed, List<DerivationSpec> derivations, Budget budget);
    List<Remedy> solve(FindingBundle findings, RemedyContext context);   // ordered, deduped, capped
    @Nullable Explanation explain(FindingCode code);                // null = not in this build
    Explanation explainOrFallback(FindingCode code);                // total; family fallback (13 §4.8)
    @Nullable ClaimLedger ledger();                                 // the handle for the world being matched; null when absent

    record CompileResult(FindingBundle findings, @Nullable Blueprint blueprint) {
        public boolean succeeded() { return blueprint != null; }    // findings may still warn
    }

    record Host(DocumentSource documents, SegmentLibrary segments, @Nullable ClaimLedger ledger) {
        public static Host empty();
    }
    @FunctionalInterface interface DocumentSource {
        @Nullable BlueprintDocument find(NamespacedId id);
        static DocumentSource of(Map<NamespacedId, BlueprintDocument> documents);   // immutable copy
        static DocumentSource empty();
    }
}

public sealed interface MatchResult extends AutoCloseable {
    record Admitted(Stage stage, Siting siting, MatchOutcome outcome,
                    @Nullable ClaimPlan planned, long ledgerRevision,
                    SnapshotLease lease, FindingBundle findings) implements MatchResult, AutoCloseable { }
    record Dropped(Stage stage, DropCause cause, List<Finding> findings) implements MatchResult { }
    record Formed(Stage stage, InstanceId id, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long generation, long contextRevision, long ledgerRevision,
                  SnapshotLease lease, FindingBundle findings) implements MatchResult, AutoCloseable { }
    record Failed(Stage stage, List<Finding> findings) implements MatchResult { }
    record Undecided(Stage stage, UndecidedReason reason, List<Finding> findings,
                     @Nullable MatchOutcome partial, @Nullable RetryToken retry) implements MatchResult { }
    record Deferred(Stage stage, RetryToken token, List<Finding> findings) implements MatchResult { }

    @Override default void close() { }        // Failed/Undecided/Deferred hold nothing
}
```

`create` fixes `(Catalogs, Host)`; no member mutates either and nothing else is stored. `asMap()`
exists because `Catalog` is also consumed by `context`'s plan compiler, which takes
`Map<NamespacedId, …>` and may not import `spi` (`02` §2). `ContextKeys` lives here rather than with
the key types for the same reason: it is *registration* — it takes the draft and returns the key —
and the draft type is this unit's.

`Formed` is the only arm that carries a `ClaimSet`, and only `settle` produces it: `Admitted` has no
id and no claims (`derive` refuses it) and carries instead the planned claim and the ledger revision
`check` read, so a caller can preview or register. The three revisions are carried, not recomputed:
`generation` (the instance's), `contextRevision` (the frame it published) and `ledgerRevision` (what
the settle settled at), so a consumer can tell a stale `Formed` from a current one without asking
the host.

## 4. Semantics

### 4.1 `NamespacedId`

- **Shape** — exactly `namespace:path`; `parse` also takes a bare path under the default namespace;
  two colons is malformed. Charset: namespace `[a-z0-9_.-]{1,64}`, path `[a-z0-9/._-]{1,256}`, `/`
  never leading, trailing or doubled.
- **Case** — uppercase refused, never folded; folding would merge two hosts' names into one key.
- **Default** — `parse("hatch")` is `structurecumulus:hatch` (`03` §7); `of(ns, path)` has no default.
- **Failure** — `parse` answers `null`: one channel, no exception, no partial value. The *position* of
  a bad id is the `DataView.span()` the caller already holds (U6), so the parser carries no offset.
- **Order** — record equality, so `parse(text(id)).equals(id)`; `compareTo` is `String.compareTo` on
  namespace then path — code point, locale-free, never a `Collator` or `equalsIgnoreCase`.

Not `ResourceLocation`: I-1 keeps `net.minecraft.**` off the kernel classpath, its charset and
uppercase handling have moved between Minecraft versions (the axis R-02 needs still), and it would
tie every document, catalog id and code to a version although none names a block.

### 4.2 `Catalog<T>`: freeze, and why not a static registry

`put` stores the instance by reference, never cloned; `build()` copies ids into a frozen list plus an
index in one pass and refuses a repeated id with `IllegalArgumentException` naming both values. The
frozen type has no mutator, so `get`/`ids`/`asMap` are `O(1)` and cannot fail: freezing is a type
fact, not a runtime flag.

A static registry would be global mutable state (I-15), depend on class-load order, allow one
snapshot per JVM (no golden comparison, no parallel tests) and have no freeze, adding a hidden
argument to I-4's tuple. A catalog is a value.

### 4.3 `Catalogs`: exactly seven families

| Family | Entry | Resolved by | An empty `builtin()` family means |
| --- | --- | --- | --- |
| `:expectations` | `ExpectationFactory` (`07`) | `compile`, assembly | every bay body of `factory` shape unresolvable: `SC-SPI-0002` at `DESIGN` |
| `:focuses` | `FocusFactory` (`11`) | `compile`, plan construction | every bay falls back to the shipped kinds only; a declared focus kind outside them is `SC-SPI-0002` |
| `:computations` | `ComputationFactory` (`11`) | `compile`, `check`'s `SECTION`/`AGGREGATE` phases | every `computations[]` entry outside the shipped five is unresolved: `SC-CTX-0101` |
| `:contextKeys` | `ContextKey<?>` (`12`) | `compile` (declaration), `check`/`derive` (read) | no `context[]` declaration can resolve: `SC-CTX-0101` at `DESIGN` |
| `:derivers` | `Deriver<?>` (`12`) | `compile`, `derive` | every `derives[]` entry outside the shipped set is unresolved: `SC-CTX-0101` |
| `:remedies` | `RemedyProvider` (`14`) | `solve` | `solve` answers the empty list |
| `:codes` | `FindingCode` (`13`) | `compile`, `explain`, `Codes` | unreachable: ships every row of `13` §4.3 |

Segments are **documents, not a catalog** (`SegmentLibrary` is host data with a revision, arrives in
`Host`, reloads with the datapack); capture, occupant resolution, claims and the edit executor are
**host ports**, passed as objects. An eighth family is a glossary and code-table event.

`builtin()` ships the kernel's own entries in all seven: the leaf and combinator expectations
(`block`, `air`, `trait`, `allOf`, `anyOf`, `not`), the focus kinds of `22` §6.3, the computations
`count`, `sum`, `volume`, `distinct`, `bind`, the `contextKeys` the kernel's own declarations use,
the `derivers` it ships, no remedy provider, and every code. What it cannot ship is an
addon-shaped factory: an id an addon invented stays unresolvable until that addon registers it.

`builder()` starts from `builtin()`, so an unset family is the kernel's — never `null`, never
accidentally empty — and a supplied family is merged **over** it, kernel entries first, so a shipped
id cannot be shadowed (refused, not honoured). An addon is added by calling its
`Catalog.builder(...).put(...).build()` methods and passing them to `Catalogs.builder()` before the
single `create`; the addon never sees the kernel. `ContextKeys.register(draft, …)` is how a key
enters `:contextKeys`: the draft is the only place a declaration can be validated against the
catalog that will freeze with it, and a second registration of one id is refused at `build()`
(`SC-CTX-0102`), whatever the two records' other components.

### 4.4 The nine operations

| Operation | Reads | Must not touch | Determinism inputs |
| --- | --- | --- | --- |
| `document` | `Host.documents` | filesystem, cache | id + host snapshot |
| `compile` | document, `Host.segments`, the five declaration families | `WorldView`, claims | document bytes + snapshot |
| `siting` | exactly one cell — `origin` — through the given view | the orientation being chosen | blueprint + origin + capture |
| `check` | `request.lease().view()`, the frozen claim view the host made at dispatch (`Host.ledger()`), `catalogs.focuses()`/`computations()` | writing a claim, a context frame or the world; any other world handle, any mutator | the request (I-4) |
| `settle` | the request, the admitted result (its plan and its child handle), `Host.ledger` | the world; a settle off the server thread; bindings that are not this request's | request + admitted + ledger revision |
| `derive` | the formed instance's frames, `Host.ledger()` (read-only, nullable), `catalogs.computations()`/`contextKeys()`/`derivers()` | re-walking the world; mutating a settled match (I-9); publishing over a newer `baseRevision` | formed + specs + budget |
| `solve` | `context` (lease, claims, outcome, context, options), `catalogs.remedies()` | applying anything, opening a transaction (I-11) | findings + context |
| `explain`, `explainOrFallback` | `catalogs.explanations()` | filesystem, locale default | the code |
| `ledger` | `Host.ledger` | the world, the ledger's write API | host snapshot |

- **`check` reads, `settle` writes.** The match runs on a host worker over the sealed snapshot and
  reads the claim view; `check` returns the verdict, the plan and the revision it read
  (`Admitted.planned()`, `Admitted.ledgerRevision()`) and writes no claim and no context frame.
  `settle(request, admitted)` is the only writing entry point: it runs on the server thread, checks
  the capture with `CaptureGate.evaluate(request.lease())`, checks the bindings, derives the rows,
  and returns `Formed` only after `Settled`. A conflict is `Failed(SC-CLM-0002)` with the incumbent
  rows, and a `RevisionMoved` is retried once inside the transaction before it becomes `Undecided`.
- **`check` and `settle` are the only members that hold a lease.** `Admitted` and `Formed` are
  `AutoCloseable`. A caller that never calls `settle` closes `Admitted` itself; a caller that calls
  `settle` does not touch it again, because `settle` closes it on every path, and closes `Formed`
  only when the caller is done with `derive`. `try (var r = kernel.check(request))` compiles for
  every arm, and a handle left open is a leak the host counts.
- **`siting` is the anchor's one read.** `AnchorResolver.resolve` reads `origin` only and returns the
  unique `Siting` whose `candidate.apply(authoredFront) == anchorHeading`; a caller with a siting
  already (a command, a blueprint with no anchor) passes it in `MatchRequest` and never calls this.
- **Absence is an answer.** No document → `null`; no ledger at create → `ledger()` is `null`; an
  unshipped code → `explain` is `null`, `explainOrFallback` synthesises the family text. Never throw:
  only `NamespacedId.of` and the two `build()`s throw, and only on a start-up programmer error.
- **A missing definition is a finding at the earliest stage that can know it.** `compile` reports an
  unresolvable factory as `SC-SPI-0002` (`DESIGN`), a missing segment as `SC-DOC-0003`, an
  unregistered computation or key as `SC-CTX-0101` in the document's findings. `check` never meets
  one: a `Blueprint` already holds its `Expectation`/`Focus`/`Computation` instances, so **`check`
  resolves no factory at all**.
- **One claims source per use.** `ledger()` is the host's handle for the world being matched, and
  `check` reads the frozen view the host made for that dispatch (`02` §5, `20` §2), never a live ledger. The facade never injects one
  source into the other — hidden state — and `11` §4.6 already owns
  `Undecided(LEDGER_UNAVAILABLE)`.
- **Threading:** catalogs, `Host` and a kernel are immutable, so `check`/`derive`/`compile`/`solve`/
  `explain` are safe concurrently given thread-safe ports; builders are single-threaded. `settle` is
  the server thread's. `derive` takes only a `Formed` (I-9); `solve` only aggregates — providers in
  `id()` order, U10's dedup, order and cap.

### 4.5 Trivial implementations (R-10, I-8)

May ship: the `Expectations` combinators (`allOf`, `anyOf`, `not`, `sequence`, `atLeast`, `atMost`,
`exactly`, `custom`), pure composition of `Expectation` values; the descriptor tests `air()`,
`anyOccupant()`, `nothing()`, `block(NamespacedId)`, `trait(TraitKey)`, which compare `OccupantSnapshot`
facts — type, empty footprint, a trait's presence — and never a host payload; the focus kinds of
`22` §6.3 and the computations `count`, `sum`, `volume`, `distinct`, `bind`; and the document
grammar, canonicaliser, validator, assembler, code table and `ExplainBook`.

Must not ship: a predicate reading `payload()`, a block state, a capability, a fluid or energy handler
(adapter/addon knowledge, R-01/R-02); a `WorldView`, `WorldCapture`, `ClaimLedger`, `EditExecutor` or
renderer (host ports); **a census key** — the census is *detection* output (`MatchOutcome.census()`),
and a `ContextKey` republishing it would claim to be a computation for zero capability, while an addon
writes it in ten lines with the shipped `count`. So `builtin()` ships the generic leaf expectations,
focuses and computations, the kernel's own keys, no remedy provider and every code: a name that
encodes *one* structure's meaning is always an addon's.

### 4.6 `Stability`

- `STABLE` — signature and semantics frozen; removal needs a `DEPRECATED` cycle and a major version.
  Recorded in the kernel-owned table and in `Codes`.
- `EXPERIMENTAL` — usable, may change or vanish in a minor; nothing may rely on it across a port. It
  is **the default**: every name absent from the table answers this.
- `DEPRECATED` — still serving, behaviour unchanged, removal scheduled, replacement named. Recorded by
  `@Deprecated(since, forRemoval = true)` **and** a table row **and** an `Explanation` whose `seeAlso`
  names the replacement.

The five U11 names are `STABLE`; entry types carry their unit's status (`expectation_factory`,
`focus_factory`, `computation_factory`, `state_key`, `remedy_provider`, `finding_code`,
`explain_book` stay `EXPERIMENTAL` until those units freeze); an addon's names are unmarked,
therefore `EXPERIMENTAL`. Deprecation is advice, not a runtime state: a deprecated entry resolves as
before and raises no finding for being used.

### 4.7 Lifecycle

Registration happens before `create`, on one thread, through builders. `create` keeps the built values,
so a retained builder can only produce another catalog. Two kernels share no mutable object: the same
bytes compile and match differently under two snapshots, which is the golden-test fixture. A late
registration is impossible by type (no mutator) and by value (a new kernel is a new snapshot); a running
attempt keeps its own. Reload: documents and segments are *data* — new `Host`, new kernel, retire the
old when in-flight attempts end; catalogs are *code* — a new build. No hot-swap; entries are shared
across concurrent attempts, re-entrant, never cloned.

### 4.8 Code registry

`Catalog<FindingCode>` is keyed by a `NamespacedId` whose path is the code text lowercased
(`structurecumulus:sc-exp-0002` → `SC-EXP-0002`), namespaced by the registering mod, so key↔value is
derived and checkable.

- **one key per code** — `build()` refuses a path that is not `code.text().toLowerCase(Locale.ROOT)`.
- **one code per number** — `build()` refuses two entries with one `(family, number)`, naming both keys.
- **every shipped code** — `builtin()` carries one entry per row of `13` §4.3, bijective with `Codes`; a
  `kernelTest` asserts it, and `build()` refuses a code its book does not answer. Every refusal here is
  an `IllegalArgumentException`, not a finding: the snapshot is built before any attempt exists, so
  there is no stage or locus to attach one to (failing fast beats a silent winner).
- **addon codes** — families are U9's closed 15-case enum, so an addon adds *codes*, not a family:
  `structurecumulus` owns `0001–0999`, addons `1000–9999`, never re-pointed (`13` §4.3). No
  auto-allocation: allocation order would make a code's meaning depend on load order.
- **unknown code** — still constructs, orders and renders from the finding (`13` §4.3); `explain` →
  `null`, `explainOrFallback` → family text, `SC-DIA-0004` when a bundle is built against a book.

U11 mints one code: `SC-SPI-0002`, *a document names an extension id this snapshot does not hold*.

## 5. Reference sketch

```java
// ---- addon code (dev.vfyjxf.cumulus.example), against kernel types only ----
final class HatchFactory implements ExpectationFactory {
    static final NamespacedId ID = NamespacedId.of("mymod", "hatch");
    @Override public NamespacedId id() { return ID; }
    @Override public Expectation create(DataView args, FactoryContext ctx) {
        return Expectations.allOf(Expectations.block(NamespacedId.of("minecraft", "iron_bars")),
                                  Expectations.trait(Traits.ITEM_INVENTORY));
    }
}
final class Scale implements ComputationFactory {   // §12.3's aggregate, an addon computation
    static final NamespacedId ID = NamespacedId.of("mymod", "scale");
    @Override public NamespacedId id() { return ID; }
    @Override public Computation<Long> create(DataView args, FactoryContext ctx) {
        return /* factor = args.asInt("factor"); phase = AGGREGATE; output/inputs from the Spec */;
    }
}
final class ReplaceBlock implements RemedyProvider {
    static final NamespacedId ID = NamespacedId.of("mymod", "replace_block");
    @Override public NamespacedId id() { return ID; }
    @Override public List<Remedy> solve(Finding finding, RemedyContext ctx) {
        if (finding.primaryLabel() == null) { return List.of(); }    // cannot locate it, cannot help
        EditPlan plan = /* one Replace per primary cell, U10 */;
        return List.of(new Remedy.Repair(plan, Applicability.MACHINE_APPLICABLE, "swap the wall"));
    }
}

// ---- host: compose a snapshot, then create; no kernel internals touched ----
Catalogs catalogs = Catalogs.builder()
        .expectations(Catalog.<ExpectationFactory>builder(NamespacedId.of("expectations"))
                .put(HatchFactory.ID, new HatchFactory()).build())
        .computations(Catalog.<ComputationFactory>builder(NamespacedId.of("computations"))
                .put(Scale.ID, new Scale()).build())
        .remedies(Catalog.<RemedyProvider>builder(NamespacedId.of("remedies"))
                .put(ReplaceBlock.ID, new ReplaceBlock()).build())
        .codes(Catalog.<FindingCode>builder(NamespacedId.of("codes"))
                .put(NamespacedId.of("mymod", "sc-exp-1234"),
                     FindingCode.of(FindingCode.Family.EXP, 1234)).build())
        .explanations(addonExplanations)                                // one per addon code
        .build();

StructureKernel kernel = StructureKernel.create(catalogs,
        new StructureKernel.Host(StructureKernel.DocumentSource.of(documents), library, ledger));

BlueprintDocument doc = kernel.document(NamespacedId.of("mymod", "huge_furnace"));  // (a) absent?
if (doc == null) { command.reply("no such blueprint"); }                           // null, not a throw

CompileResult compiled = kernel.compile(doc);                                      // (b) DESIGN
if (!compiled.findings().isEmpty()) { command.reply(FindingRenderer.human().render(compiled.findings(), text)); }
if (compiled.blueprint() == null) { return; }                                      // errors, no artifact
Blueprint furnace = compiled.blueprint();

Outcome<Siting> siting = kernel.siting(furnace, LatticePoint.of(10, 64, 10), view); // one cell read
if (siting instanceof Outcome.Missing<Siting> missing) {                            // anchor absent
    missing.findings().forEach(sink::accept);
    return;
}
Siting at = ((Outcome.Found<Siting>) siting).value();
SnapshotLease lease = capture.lease(furnace, at);                                  // the record owns it

MatchRequest request = MatchRequest.at(lease, furnace, at)                           // (c) one call
        .budget(Budget.of(4_096, 64)).build();

// (d) `check` reads: verdict, plan and the revision it read — nothing is written
MatchResult probe = kernel.check(request);
if (!(probe instanceof MatchResult.Admitted admitted) || admitted.planned() == null) {
    command.reply("no conclusion: " + probe);   // no ledger, or no registerable bay: preview only
    probe.close();                              // preview only: the caller releases the handle
    return;
}
// (e) `settle` is the only writer and the only source of `Formed`; it consumes `admitted`
try (MatchResult result = kernel.settle(request, admitted)) {
        switch (result) {
            case MatchResult.Failed f    -> command.reply(FindingRenderer.human().render(f.findings(), text));
            case MatchResult.Undecided u -> command.reply("no conclusion: " + u.reason());
            case MatchResult.Dropped d   -> scheduler.resubmit();                    // the snapshot moved
            case MatchResult.Deferred d  -> scheduler.retry(d.token());             // AUTO only
            case MatchResult.Admitted a  -> { }       // `settle` never returns it: exhaustiveness only
            case MatchResult.Formed formed -> {
                StructureContext values = kernel.derive(formed, furnace.derivations(),  // (f) derive
                        Budget.of(1_024, 16));
                Outcome<Long> capacity = values.require(ExampleKeys.CAPACITY);              // Missing if unproduced
                ClaimSet settled = formed.claims();                                     // claims come from Formed
                long generation = formed.generation();
                long ledgerRevision = formed.ledgerRevision();                          // what the settle settled at
                ClaimView claims = ledger.read(furnace.bounds().translate(at));         // the same revision
                List<Remedy> offered = kernel.solve(formed.findings(),                  // (g) solve
                        new RemedyContext(furnace, formed.siting(), claims,
                                ledgerRevision, formed.outcome(), values, RemedyOptions.defaults()));
                Explanation known = kernel.explain(Codes.CLAIM_CONFLICT);                // (h) explain
                Explanation any = kernel.explainOrFallback(FindingCode.of(FindingCode.Family.SPI, 9999));
            }
        }
    }
}
// ledger(): kernel.ledger() is the view the requests carried; null when create took none.
```

## 6. Use cases

| Audience | Case |
| --- | --- |
| Addon author | Ships `HatchFactory`, `Scale`, `ReplaceBlock`, a key, a code and its `Explanation`: one `Catalog.builder(...).put(...).build()` per family it touches, then one `ContextKeys.register(draft, …)`. Never sees a session, a step order, a lock or a kernel subclass. |
| Host | `/reload` rebuilds `SegmentLibrary` + `DocumentSource` and calls `create` again, in-flight attempts finishing on the old kernel; `/cumulus explain` uses `explain` for a registered code (exit 0) and `explainOrFallback` otherwise (exit 1); a test builds two kernels over two snapshots in one method, the one without `mymod:hatch` reporting `SC-SPI-0002` for the same bytes. |

## 7. Proofs

| # | Claim | Argument (and what breaks it) |
| --- | --- | --- |
| P1 | freeze | `Catalog` has no mutator, so a built value cannot change by any path; `build()` copies ids into a frozen list plus index; a retained builder writes only to itself; `Catalogs.Builder.build()` never mutates its inputs. *Breaks if* a member exposes live builder state or `asMap()` is mutable. |
| P2 | facade purity | `Host` holds documents, segments, ledger — no `WorldView`, `WorldCapture`, `EditExecutor` — so no operation reaches a world except through a caller value (`request.lease().view()` for `check`, `context.lease().view()` for `solve`, the view argument of `siting`); `check` reads only the frozen claim view the host made for that dispatch and writes nothing, and `settle` is the ledger's one writer. *Breaks if* a `Host` field is a `WorldView`, or a `check` call writes a claim, a context frame or the world. |
| P3 | no static mutable state | every static in `kernel.spi` is `static final` or a pure factory; the stability table is an immutable map built once; a kernel is a value, not a singleton; a reflective test asserts zero non-final statics. *Breaks if* a memo or builder cache appears. |
| P4 | lookup totality | `get`, `document`, `explain`, `ledger` are total into `@Nullable`; `explainOrFallback`, `compile`, `solve`, `ids`, `asMap` are total into values; only `of` and `build()` throw, only on a precondition. *Breaks if* any facade member throws for an absent document, ledger or code. |
| P5 | per-kernel isolation | two kernels share only the immutable values the caller passed and store nothing else; both are safe for concurrent calls given thread-safe ports, so distinct snapshots give distinct results. *Breaks if* a kernel memoises a resolved extension in a field. |
| P6 | stable id ordering | `ids()` is a frozen list, never derived from a hash container; the facade sorts provider ids for `solve` (`14` §4.2) and `compile` sorts `(order, cost, id)` (`12` §4.1), so results are registration-order independent; `ids()` differs only as presentation. *Breaks if* a consumer iterates `asMap().values()` to pick a winner. |
| P7 | one writer, one thread | `settle` is the only member that produces an instance, and it does so only after the ledger answered `Settled`; `check` writes no claim and no frame, so no facade member takes a plan or a `ClaimSet` and writes it. *Breaks if* an addon can write a claim without a match, or if a `Formed` exists before `settle` returns. |

## 8. Dependencies and open questions

| Dependency | Assumption here |
| --- | --- |
| U3 `ExpectationFactory`, `FactoryContext`, `Expectations` | `id()` + `create(DataView, FactoryContext)`; combinators are static methods, not entries |
| U11/U8 `FocusFactory`, `ComputationFactory`, `Computation` | focus and computation kinds are catalog entries; `open` is where the `Spec` is resolved into a typed unit |
| U8 `ContextKey`, `Merge`, `Retention`, `StructureContext` | `ContextKeys.of(...)` builds a `ContextKey`; `ContextKeys.register(draft, key)` is the only catalog binding; a duplicate id is `SC-CTX-0102` |
| U10 `RemedyProvider`, `RemedyContext`, `RemedyOptions`, `Remedy` | providers declared by `id()`; `solve` is `solveAll` behind the facade (order, dedup, cap) |
| U6 `SegmentLibrary`, `BlueprintDocument`, `SegmentAssembler`; U9 codes | validation is `List<ValidationIssue>`, wrapped into a `FindingBundle`; `Codes` lives here |

1. **`Host` / `DocumentSource` are new names.** Nested, so no glossary row is owed; promoting either to
   top-level (A1 implements it) needs a row and a `Stability` value.
2. **`derive` needs a `Budget`.** Resolved: `04` §8 now passes `Budget.of(...)`; `StructureKernel.derive`
   requires it explicitly, and there is no hidden default (I-15).
3. **The compile path.** Resolved by the `compile` operation: it runs U6's `DocumentValidator` and
   `SegmentAssembler` with the declaration families, returns `CompileResult(findings, @Nullable
   Blueprint)`, and does not add a member. U6 must accept the factory catalog in both APIs (`10` Q-D6).
4. **`SC-SPI-0002`** needs a row and an `Explanation` in `13` §4.3; the route is now `15-spi.md`
   everywhere (`00` §5, `_index.md`).

None of `22` §16's six questions is this unit's. One carried defect is not a §16 question and is
recorded here so it is not lost: **canonical severity has no home.** `Catalog<FindingCode>` carries
identity and `Explanation` carries prose, so `SC-DIA-0002` still has nothing to compare a finding's
severity against — either `Explanation` gains `Severity` (U9) or the entry becomes
`CodeEntry(code, severity, explanation)`; `22` does not decide it.

## 9. Falsification

| # | Claim | Test that breaks it |
| --- | --- | --- |
| F1 | a clash is refused, never resolved | two catalogs register `mymod:hatch`, two `ContextKeys.register` calls take one id, or two namespaces register `SC-EXP-1234`: `build()` throws naming both keys and no kernel holds either pair. Keeping the first silently is the bug. |
| F2 | a kernel never changes after catalog mutation | retain both builders, build the kernel, `put` more entries and build again: `check`, `settle`, `derive`, `solve`, `explain` are identical to a fresh kernel's. |
| F3 | no facade operation reads the world | count reads on the `WorldView`; call `document`, `compile`, `explain`, `explainOrFallback`, `ledger`: count stays zero. Then `check` with a *different* stub as the request's view: only that one is read, and `siting` reads exactly one cell of its own argument. |
| F4 | no addon needs a kernel subclass | write an addon that changes the engine's step order, adds a tenth operation, or shadows a shipped code. If any is expressible without editing `kernel.spi`, "one kernel, many extensions" is false; today all three are refused by the frozen types. |
| F5 | `builtin()` matches only what the kernel ships | create over `Catalogs.builtin()`: a document whose bay body is `allOf`/`block` compiles and matches; the same document with `mymod:hatch` is `SC-SPI-0002` at `Stage.DESIGN`, no `WorldView` involved. A `builtin()` that resolves `mymod:hatch` has shipped an addon. |
