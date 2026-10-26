# 03 — Modules

> Eleven kernel packages, one adapter. A package is a unit of ownership and a rank in the dependency
> order. The graph is a DAG; an edge that closes a cycle is a design error, not a build detail.

## 1. The graph

```
rank 0   core
rank 1   space
rank 2   finding        world
rank 3   claim
rank 4   structure
rank 5   context
rank 6   document
rank 7   engine
rank 8   remedy
rank 9   spi

        core ──► space ──► finding
          │        │  └──► world ──► claim
          │        └────► structure ◄── claim
          │                   │
          │                context
          │                   │
          │               document
          │                   │
          └──────────────► engine ◄── structure, claim, context, document
                              │
                           remedy
                              │
                             spi
```

The edges, exactly:

| Package | May import |
| --- | --- |
| `core` | JDK, JSpecify |
| `space` | `core` |
| `finding` | `core`, `space` |
| `world` | `core`, `space` |
| `claim` | `core`, `space`, `finding` |
| `structure` | `core`, `space`, `finding`, `world`, `claim` |
| `context` | `core`, `space`, `finding`, `world`, `structure` |
| `document` | `core`, `space`, `finding`, `claim`, `world`, `structure`, `context` |
| `engine` | `core`, `space`, `finding`, `world`, `claim`, `structure`, `context`, `document` |
| `remedy` | `core`, `space`, `finding`, `world`, `claim`, `structure`, `context`, `engine` |
| `spi` | all kernel packages |
| `mc` (main) | all kernel packages + Minecraft |

Notes:

- `structure` depends on `claim` for `Sharing` and `Footprint`, and `claim` does not depend on
  `structure`. `claim` uses `finding` only for the `FindingBundle` a conflict carries; the planner
  lives in `engine`, which may depend on both.
- `context` depends on `structure` for the `Computation`/`Plan` types; a `Computation` is declared
  by a blueprint but its contexts (`SectionContext`/`AggregateContext`) live in `structure`, so the
  store can run them without a cycle.
- `document` does not depend on `spi`. The compiler receives the catalogs it needs as a
  `CompilerContext`, whose entries are the factory interfaces declared in `structure`/`context`
  and `Catalog<T>` from `core`.
- `engine` is the only package that knows the whole pipeline. `spi` is the only package that knows
  `StructureKernel`.
- `mc` is a separate Gradle source set (`main`), not a kernel package. In v1 it was specified in
  `modules/adapter.md` only; the M1 capture slice (capture, root snapshot, lease, one GameTest) is now
  implemented, while commands, persistence and the full adapter remain pending. The main source set
  also compiles the mod entry.

## 2. Ownership

| Package | Owns | Does not own |
| --- | --- | --- |
| `core` | ids, world/instance/snapshot/owner, phase, source spans, budgets, typed keys, `Outcome`, `FindingCode`, `Catalog<T>`, `Value`, `CancelToken` | any geometry, any report, any traversal |
| `space` | lattice, boxes, orientations, sitings, loci | what is in a cell, what a bay expects |
| `finding` | finding values, bundles, renderers, explanations, the code table | which unit emits which code |
| `world` | occupants, traits, sections, views, capture SPI, lease | claims, expectations, the engine |
| `claim` | rows, sharing, footprint, ledger SPI, conflict matrix | how rows are derived (engine), how a bay is checked (structure) |
| `structure` | bay, focus, expectation, check, blueprint, anchors, plan, read scope | context values, claims rows, the engine loop |
| `context` | keys' semantics, computations, derivers, fold, store, structure context | blueprint shape, matching |
| `document` | the data IR, the compiler, reader/writer ports | Java-side factories, catalogs |
| `engine` | request, outcome, result, gate, planner, check/settle/derive bodies | scheduling, persistence, rendering |
| `remedy` | remedy/edit/overlay values, provider SPI | applying a plan |
| `spi` | catalogs, facade, host ports, code registry, built-ins | anything a lower module already owns |

## 3. Package-private is the default for internals

A type is public only when another package or a host must name it. The following are intentionally
package-private and must not leak:

- the match session/accumulator, the tally, the draft, the folded context builder;
- the topological sort, the section iteration, the conflict grouping;
- the internal `MapContextView`, the internal `ClaimPlanner.Derived` fields beyond its accessors;
- the concrete `InMemoryClaimLedger`, `InMemoryContextStore` (tests may use them through a public
  static factory).

## 4. Source sets

```
src/kernel/java      dev.vfyjxf.cumulus.kernel.**   pure Java 21 + JSpecify
src/kernelTest/java  kernel tests                   JUnit 5, no Minecraft
src/main/java        dev.vfyjxf.cumulus.**          mod entry + mc adapter
src/main/resources   metadata + data
src/test/java        adapter tests
```

`kernel` has no Minecraft on its compile classpath. `main` depends on `kernel`. The kernel has no
`@Mod`, no loader metadata and no service file.

## 5. Naming rules that generate names

- One concept, one noun; the glossary in `02-model.md` is the authority.
- `*Key<T>` for typed identity; `*Ref` for a snapshot-scoped handle; `*Id` for stable logical
  identity; `*Snapshot` for an immutable read model; `*View` for a read face; `*Spec` for a
  declarative form; `*Plan` for an ordered executable form; `*Policy` for a small set of choices;
  `*Options` for host knobs; `*Context` for what an extension sees during one call; `*Factory` for
  a data-driven constructor; `*Provider` for an extension returning candidates.
- No `Impl`, `Default`, `Abstract`, `Base`, `Simple` in a public name. A trivial implementation is a
  static factory or a nested record with a domain name.
- `of(...)` is a validating factory; `get` returns `@Nullable`; `require` assumes a prior proof;
  `at`/`on` select spatially; `with` copies; `test` returns a `Reply`; `check` is one read-only
  attempt; `settle` writes claims; `derive` produces context; `solve` collects remedies;
  `compile` produces a blueprint; `apply` is never a kernel verb.

## 6. Doc shape for a module file

Every module file has exactly these sections:

1. **Scope** — owns / does not own.
2. **Types** — every public type, kind, and one-line contract.
3. **Signatures** — one normative Java block with every public member.
4. **Laws** — invariants, algebra, ordering, failure rules.
5. **Pipeline** — how this module participates in compile/check/settle/derive.
6. **Codes** — the codes this module produces, one row each.
7. **Tests** — the falsification obligations.
8. **Dependencies** — the exact edges, and what is deliberately not imported.

A signature in section 3 that is absent from code is a regeneration defect; a public member in code
that is absent from section 3 is a design defect.
