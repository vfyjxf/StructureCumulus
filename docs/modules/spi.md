# Module `kernel.spi`

> Rank 9. The frozen catalogs, the host ports and the facade. Everything a user or an adapter starts
> from; nothing a lower module needs.

## 1. Scope

**Owns:** `Catalogs`, `Catalogs.Builder`, `CatalogRegistrar`, `Host`, `StructureKernel`.

**Does not own:** `Catalog<T>` (`core`), the factory interfaces (`structure`), the ports
(`document`, `world`, `claim`, `context`), the engine (`engine`), the built-in code table (`finding`).

**Imports:** every kernel package.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `Catalogs` | interface | six frozen families + `builtin()` + `builder()` |
| `Catalogs.Builder` | interface | merge families over built-ins, then freeze |
| `CatalogRegistrar` | interface | collect plugin contributions over the built-ins, then freeze |
| `Host` | record | documents, ledger, context store, custom type registry |
| `StructureKernel` | final class | the facade: four operations, three helpers, four lookups |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.spi;

public interface Catalogs {
    Catalog<ExpectationFactory> expectations();
    Catalog<FocusFactory> focuses();
    Catalog<ComputationFactory> computations();
    Catalog<Deriver<?>> derivers();
    Catalog<CodeEntry> codes();
    Catalog<RemedyProvider> remedies();

    static Builder builder();
    static Catalogs builtin();

    interface Builder {
        Builder expectations(Catalog<ExpectationFactory> catalog);
        Builder focuses(Catalog<FocusFactory> catalog);
        Builder computations(Catalog<ComputationFactory> catalog);
        Builder derivers(Catalog<Deriver<?>> catalog);
        Builder codes(Catalog<CodeEntry> catalog);
        Builder remedies(Catalog<RemedyProvider> catalog);
        Catalogs build();
    }
}

public record Host(DocumentSource documents, @Nullable ClaimLedger ledger,
                   StructureManager structures, java.util.Map<NamespacedId, Class<?>> types) {
    public Host { /* immutable; contexts non-null; types sorted */ }
    public ContextStore contexts();
    public static Host empty();
}

public interface CatalogRegistrar {
    CatalogRegistrar expectation(NamespacedId id, ExpectationFactory factory);
    CatalogRegistrar focus(NamespacedId id, FocusFactory factory);
    CatalogRegistrar computation(NamespacedId id, ComputationFactory factory);
    CatalogRegistrar deriver(NamespacedId id, Deriver<?> deriver);
    CatalogRegistrar code(CodeEntry entry);
    void fail(NamespacedId pluginId, RuntimeException failure);
    java.util.List<RegistrationFailure> failures();
    Catalogs catalogs();
    static CatalogRegistrar builtin();

    record RegistrationFailure(NamespacedId pluginId, RuntimeException cause) {}
}

public final class StructureKernel {
    public static StructureKernel create(Catalogs catalogs);
    public static StructureKernel create(Catalogs catalogs, Host host);

    public Catalogs catalogs();
    public @Nullable Document document(NamespacedId id);
    public @Nullable ClaimLedger ledger();
    public ContextStore contexts();
    public StructureManager structures();

    public CompileResult compile(Document document);
    public MatchResult check(MatchRequest request);
    public MatchResult settle(MatchRequest request, MatchResult.Admitted admitted,
                              StructureAttempt attempt);
    public StructureContext derive(MatchResult.Formed formed,
                                   java.util.List<DerivationSpec> derivations);
    public SolveResult solve(FindingBundle findings, RemedyContext context);
    public @Nullable Explanation explain(FindingCode code);
    public Explanation explainOrFallback(FindingCode code);
    private StructureKernel() {}
}
```

## 4. Semantics

### 4.1 Catalogs

Six frozen families, each a `Catalog<T>` keyed by `NamespacedId` and insertion-ordered:

| Family | Entry | Used by |
| --- | --- | --- |
| `structurecumulus:expectations` | `ExpectationFactory` | `DocumentCompiler` |
| `structurecumulus:focuses` | `FocusFactory` | `DocumentCompiler` |
| `structurecumulus:computations` | `ComputationFactory` | `DocumentCompiler` |
| `structurecumulus:derivers` | `Deriver<?>` | `derive` |
| `structurecumulus:codes` | `CodeEntry` | `explain` |
| `structurecumulus:remedies` | `RemedyProvider` | `solve` |

`builtin()` ships the leaf factories (`block`, `air`, `trait`, `any_occupant`), the focus
factories (`occupant`, `bay`, `all_cells`, `entity`, `nested`, `door`, `context`), the computations
(`count`, `volume`, `air`), every shipped `CodeEntry`, no deriver and no remedy provider. The
structural expectation forms (`all_of`, `any_of`, `not`, `sequence`, `at_least`, `at_most`, `exactly`,
`each_cell`) are grammar, not catalog entries; the compiler resolves them and calls the leaf
factories for the children.

`builder()` starts from `builtin()`; a supplied catalog is merged over the built-in family, kernel
entries first. Two entries with one id and different values throw `IllegalArgumentException` at
`build()`. A catalog is frozen at `create`; there is no late registration.

### 4.2 Host

`Host` is the only set of ports a kernel holds:

- `documents` — id → `Document` for `document(id)` and cross-document definitions.
- `ledger` — the per-world claim store; null means "this kernel cannot settle claims".
- `contexts` — the per-instance value store; never null.
- `types` — custom document key types (`NamespacedId` → `Class<?>`); built-in types (`long`, `int`,
  `boolean`, `string`, `id`, `cuboid`, `cuboid_set`) are handled by the compiler.

`Host.empty()` is `(DocumentSource.empty(), null, StructureManager.create(), Map.of())`. A kernel
created without a host can compile and check, but `settle` with claimable rows is
`Undecided(ledgerUnavailable)`.

### 4.3 The facade

- **`compile(document)`** builds a `CompilerContext` from the catalogs, the host's types and the
  host's documents, then calls `DocumentCompiler.compile`. It never throws for bad data.
- **`check(request)`** calls `MatchEngine.check`. The request carries its own frozen `ClaimView`; the
  facade's ledger is not read.
- **`settle(request, admitted, attempt)`** calls `MatchEngine.settle` with the host's ledger and the
  attempt that reserved the instance. The attempt is the only accepted publish target: a closed
  attempt, a foreign `InstanceId` and a mismatched `StructureSpec` are each `Undecided(designDefect)`
  and write nothing. It takes the admitted result as a value; there is no lease transfer and no
  `close`.
- **`derive(formed, derivations)`** calls `MatchEngine.derive` with the derivers catalog and the
  host's context store.
- **`solve(findings, context)`** iterates the remedy catalog by id, calls each provider once per
  finding, catches `RuntimeException` (`SC-RMD-0001`), dedupes by value and caps at
  `context.maxResults()`; the cap puts `machineApplicable` first.
- **`explain(code)`** looks up `structurecumulus:<code-text-lowercase>` in the code catalog; an addon
  code is not found, which is legal.
- **`explainOrFallback(code)`** returns the registered explanation or a synthesised family-level one.
- **`document(id)`** is the host's document source; **`ledger()`** is the host's ledger;
  **`structures()`** is the host's structure manager (the set rollback dispatches `Broken` for a formed attempt's
  context through it).

The facade holds no thread, clock, cache or mutable registry. `create` is the only construction path.
A kernel is safe for concurrent `compile`/`check`/`derive`/`solve`/`explain` given thread-safe ports;
`settle` is the server thread's because the host's ledger is.

### 4.4 Built-in factories

| Kind | Args | Creates |
| --- | --- | --- |
| focus `occupant` | `cell: [x,y,z]` | `Focuses.occupant` |
| focus `bay` | `{}` | `Focuses.bay` |
| focus `all_cells` | `{}` | `Focuses.allCells` |
| focus `entity` | `type: id` | `Focuses.entity` |
| focus `nested` | `cell: [x,y,z]` | `Focuses.nested` |
| focus `door` | `origin: [x,y,z]` | `Focuses.door` |
| focus `context` | `key: id` | `Focuses.context` (via `FactoryContext.keys`) |
| expectation `block` | `id: id` | `Expectations.block` |
| expectation `air` | `{}` | `Expectations.air` |
| expectation `trait` | `id: id` | `Expectations.trait` (presence) |
| expectation `any_occupant` | `{}` | `Expectations.anyOccupant` |
| computation `count` | `{}` | `Computations.count` |
| computation `volume` | `{}` | `Computations.volume` |
| computation `air` | `{}` | `Computations.air` |

A factory that cannot parse its args calls `ctx.report(...)` with `SC-DOC-0002` and returns null; the
compiler turns a null into `SC-DOC-0003` if no finding was reported.

### 4.5 CatalogRegistrar

`CatalogRegistrar` is the one collection point for catalog contributions. `builtin()` starts from the
six built-in tables; `expectation`/`focus`/`computation`/`deriver` add one entry each and return the
registrar, `code` adds a `CodeEntry` keyed by its code text, and `catalogs()` freezes the six
families. Remedies have no contribution method. A host calls the contributions at assembly time —
the adapter through its plugin entry point (`ModPlugin.register`, adapter §7.5), once per plugin in
the loader's resolved order — and every later compile reads the frozen snapshot. The kernel itself
does no discovery, ordering or failure policy.

Duplicates follow `Catalog.Builder.put`: one id with an equal value collapses, a different value
throws `SC-SPI-0003` at the contribution call, so a built-in (or any already-registered) id cannot
be replaced. `fail(pluginId, cause)` attributes one plugin's register failure to its id;
`failures()` lists them in registration order for a host that runs its own loading.

## 5. Pipeline role

`spi` is the composition root. It creates nothing at match time except the engine's request context;
it resolves no class by reflection and holds no static mutable state. Catalog assembly
(`CatalogRegistrar`, §4.5) belongs to that root: it runs once while the host composes the kernel,
before any document is compiled.

## 6. Codes

`SC-SPI-0001` (extension threw / malformed finding; engine guard), `SC-SPI-0003` (a catalog build
found a duplicate/different entry; `Catalogs.Builder.build` and the `CatalogRegistrar` contribution
call). A document naming an unknown factory is `SC-DOC-0003` (the document compiler owns that
surface).

## 7. Tests

- `builtin()` resolves the leaf/grammar kinds; a custom factory added by a builder wins only for its
  own id; a duplicate throws.
- `CatalogRegistrar`: an empty registrar freezes to `Catalogs.builtin()`; contributions enter the
  frozen families; a builtin id and a duplicate id with a different value throw `SC-SPI-0003`; an
  equal re-put collapses; `fail`/`failures` attribute a register failure to its plugin id.
- `Host.empty()`: compile/check work; settle with claims is `Undecided`; settle without claims is
  `Formed` through an attempt.
- The attempt is the only publish target: a closed attempt, a foreign instance id and a mismatched
  spec are each `Undecided(designDefect)` with an unmoved ledger.
- An empty derived plan with a ledger releases the instance's key instead of settling; `Formed` then
  carries the release's revision (D-60).
- Facade: each operation's absent/error path; `explain` null vs fallback; `solve` cap/order.
- Two kernels over two catalogs do not share state; a late builder mutation does not change a built
  kernel.

## 8. Dependencies

All kernel packages. It is the only package a host or an addon imports for the facade, and the only
package that composes a kernel.
