# 03 — Naming and shape conventions

> The collision ban list lives in `01-glossary.md` §O. This file states the rules that
> generate names, so a module author can name a new type without asking.

## 1. One concept, one name

A concept has exactly one noun in the kernel. Synonyms are defects, not style choices. The
glossary is the authority; a module that needs a second word for an existing concept is
wrong. Examples of pairs that must not coexist: `Bay`/`Slot`, `Occupant`/`Element`,
`Expectation`/`Predicate`, `Finding`/`Diagnostic`, `Remedy`/`Solution`, `Cuboid`/`Box`.

## 2. Collision ban

No kernel type may share a simple name with a `net.minecraft.**` type or a commonly imported
JDK type. The practical rule:

- If a Minecraft class with that name is likely to be imported in the same adapter file, the
  name is banned.
- If the name is a `java.util.function` interface, a `java.util` collection or a `java.time`
  value, the name is banned.
- A banned concept gets a domain word, not a prefix. `Turn` beats `CumulusRotation`.
- The adapter package is the namespace. Name adapter classes for their Minecraft-side role
  (`LevelSnapshot`, `LevelCapture`, `WorldEdits`), never with a mechanical `Mc`/`Vanilla`
  prefix. A collision with a kernel type gets a role qualifier (`OccupantDescriptors`,
  `OccupantTraits`), not a prefix. The kernel never imports `mc`.

The ban list in `01-glossary.md` §O is normative. Add a row there before introducing a name
that is not already listed.

## 3. Package layout

| Package | Contents | May depend on |
| --- | --- | --- |
| `kernel.base` | ids, budget, spend, cancellation, stability, stage, arguments, source spans, data views | nothing |
| `kernel.space` | lattice, cuboid, heading, orientation, siting, locus | base |
| `kernel.occupant` | occupant model, world view, capture SPI | base, space |
| `kernel.finding` | finding model, bundle, renderer, explanation | base, space |
| `kernel.claim` | claim model, ledger, planner | base, space |
| `kernel.expectation` | expectation SPI, reply, bindings, combinators | base, space, occupant, claim, finding |
| `kernel.blueprint` | bay, seam, segment, anchor, blueprint, builder | base, space, expectation, claim |
| `kernel.document` | document model, data view, bridge, assembler, emitter | base, space, blueprint, expectation, finding |
| `kernel.engine` | request, session, result, outcome, prescreen, invalidation | all above |
| `kernel.context` | context keys, deriver, plan, augments, runtime state | engine + above |
| `kernel.remedy` | remedy, edit plan, overlay, validator, transaction | blueprint, occupant, expectation, finding |
| `kernel.spi` | catalogs, facade, stability policy | all kernel packages |
| `mc` | adapter (main source set) | all kernel packages + Minecraft |

One package per unit. No `internal` subpackage: a package-private
type is the internal marker.

## 4. Type-shape conventions

| Suffix / shape | Meaning | Example |
| --- | --- | --- |
| `record` | immutable value with structural equality | `LatticePoint`, `Finding`, `Claim`, `KeyLocus` |
| `sealed interface` + records | closed family the caller switches over | `MatchResult`, `Edit`, `Remedy` |
| plain `interface` | extension point or read-only view | `Expectation`, `WorldView`, `ClaimLedger` |
| `*Key<T>` | typed identity carrying a `Class<T>` token | `ContextKey<T>`, `TraitKey<T>` |
| `*Ref` | snapshot-scoped handle, equality by identity within a snapshot | `OccupantRef`, `InstanceRef` |
| `*Id` | stable logical identity, not a handle | `SeamId`, `FindingId`, `SnapshotId` |
| `*Snapshot` | immutable read model at one revision | `OccupantSnapshot` |
| `*View` | read-only live face over a snapshot | `WorldView`, `NestedView` |
| `*Spec` | declarative authoring form, resolved later | `AnchorSpec`, `ClaimSpec`, `DerivationSpec` |
| `*Plan` | ordered executable form compiled from a spec | `CheckPlan`, `DerivationPlan`, `EditPlan` |
| `*Policy` | a small set of choices that changes behaviour | `OrientationPolicy`, `MatchPolicy` |
| `*Mode` | an enum that selects one of a few semantics | `CacheMode` |
| `*Options` | knobs for a host-facing call, with defaults | `FindingOptions`, `SolveOptions` |
| `*Context` | what an extension may see during one call | `ExpectationContext`, `DeriveContext`, `RemedyContext` |
| `*Factory` | data-driven constructor registered in a catalog | `ExpectationFactory` |
| `*Provider` | extension that returns several candidate answers | `RemedyProvider` |
| `*Sink` | push consumer | `FindingSink` |
| `*Renderer` | pure projection to text/data | `FindingRenderer` |
| `*Catalog` / `Catalogs` | frozen registry snapshot | `Catalog<T>`, `Catalogs` |
| `*Builder` | mutable construction, `build()` validates | `BlueprintBuilder`, `MatchRequest.Builder` |

Do not add `Impl`, `Default`, `Abstract`, `Base` or `Simple` to a public type. The kernel's
trivial implementation is a static factory or a nested record with a domain name
(`Expectations.air()`), not `DefaultExpectation`.

## 5. Method naming

| Verb | Contract |
| --- | --- |
| `of(...)` | static factory; validates arguments and throws `IllegalArgumentException` only on programmer error |
| `from(...)` | conversion that may lose information; the loss is documented |
| `toX()` | projection to another representation |
| `get(...)` | lookup that returns `@Nullable`; never creates |
| `require(...)` | lookup that is guaranteed by a prior check; returns non-null or throws a programming-error exception |
| `at(...)`, `on(...)` | spatial selection (`occupantAt`, `claimsAt`) |
| `with(...)` | copy with one field changed; returns a new value |
| `test(...)` | a predicate-style boolean or a `Reply`; an `Expectation` uses `test` |
| `check(...)` | one read-only engine attempt; never a boolean |
| `settle(...)` | write the claims a checked plan names; the only claim-writing verb |
| `derive(...)` | turn an outcome into context; never mutates |
| `solve(...)` | collect remedies; never applies |
| `compile(...)` | turn a document into a blueprint, collecting findings; never throws on invalid input |
| `apply(...)` | mutate the world through a transaction; a world edit is never implicit |
| `explain(...)` | long-form text for a code |

A method that can produce a structural failure returns a value (`Reply`, `MatchResult`,
`FindingBundle`), never throws. A method that throws means the caller violated a
precondition; `Checks.checkNotNull` is the only sanctioned assertion helper.

## 6. Generic and variance rules

- A typed key is `record XKey<T>(NamespacedId id, Class<T> type)`. The `Class<T>` token is
  mandatory: it is what makes a heterogeneous map safe without unchecked casts at the call
  site.
- A lookup returns `@Nullable T`; it never returns `Optional<T>` (project nullness rule).
- A family that the caller must switch over is `sealed`; adding a case is a deliberate
  source-incompatible change and is recorded in the module's *Open questions*.
- Wildcards: `Catalog<? extends T>` only at a read boundary; a `Catalog<T>` is invariant.
- No raw types in a public signature. No `Object` parameter where `DataView`, `Arguments`
  or a sealed type expresses the intent.

## 7. Constants, codes and ids

- Constants are `static final` with `UPPER_SNAKE` names, matching the glossary
  (`Expectations.AIR`, `TraitKeys.ITEM_INVENTORY`); a factory method stays `lowerCamelCase`
  (`Expectations.air()`).
- A `FindingCode` is `SC-<FAMILY>-<NNNN>`: `SC` is the project, `FAMILY` is a three-to-six
  letter unit mnemonic (`SPC`, `OCC`, `EXP`, `FOC`, `CLM`, `BPT`, `BAY`, `CHK`, `DOC`,
  `ENG`, `CTX`, `DIA`, `RMD`, `SPI`, `MC`), `NNNN` is a zero-padded number. A code is never
  reused.
- A `NamespacedId` is `namespace:path`; the default namespace is the mod id
  (`structurecumulus`), and a built-in code uses `structurecumulus` or the owning addon's
  id.
- A `UnitId`, `SeamId` and `FindingId` are local identities; do not reuse a
  `NamespacedId` where a local id is enough.

## 8. Documentation conventions

Each module file uses the same nine sections, in this order:

1. **Scope** — what the unit owns and does not own.
2. **Vocabulary** — the names it owns, linked to the glossary.
3. **API design** — types and signatures, with a table of contracts.
4. **Semantics** — algorithms, ordering, invariants, failure behaviour.
5. **Reference sketch** — Java that is shaped like compilable code; no stub that throws.
6. **Use cases** — at least one addon-author case and one host case.
7. **Proofs** — why the invariants hold; a counterexample for each risky claim.
8. **Dependencies and open questions.**
9. **Falsification** — the concrete test or review question that would break the module.

Code blocks are Java 21. A sketch may omit imports and bodies, but a signature shown in a
sketch is normative; prose must not contradict it.
