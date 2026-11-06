# 01 — Constitution

> Twelve laws. Every module is reviewed against all twelve; a module that needs an exception is
> changing the constitution first, not adding a footnote.

## Law 1 — One concept, one type, one owner

A noun in the design has exactly one type and one package that owns it. Synonyms are defects, not
style. A type that only the engine needs is package-private; a cross-package name appears in the
module table before it appears in code.

The banned names from the old set still apply: `Predicate` → `Expectation`, `Diagnostic` →
`Finding`, `Solution` → `Remedy`, `Box` → `Cuboid`, `Region` → `CuboidSet`, `Transform` →
`Placement`, `Slot` → `Bay`, `Direction` → `Heading`, `Rotation` → `Turn`, `Mirror` → `Flip`,
`Codec` → `DocumentReader`/`DocumentWriter`.

## Law 2 — The kernel is a pure function

Under `kernel.**` there is no world handle, no I/O, no clock, no randomness, no `ThreadLocal`, no
static mutable field, no logger, no executor. Time, cancellation and storage are values or ports
passed in. A host failure is a finding or an `Undecided`, never an exception that unwinds a tick.

## Law 3 — One traversal, three phases

A match has exactly three phases:

- **compile** — document → blueprint; no world, no claims.
- **check** — blueprint + snapshot + siting → verdict + values + bindings; read-only.
- **settle** — admitted result → claims written; the only writer.

No unit declares a phase; the operation names it. There is no fourth stage, no stage enum on a
`Check`, and no `expand(stage)`.

## Law 4 — Absence is explicit, and it has four kinds

| Kind | Type | Meaning |
| --- | --- | --- |
| no value | `Outcome.Missing` | a lookup found nothing |
| no subject | `FocusResult.Unavailable` | the focus could not bind what the check needs |
| no read | `CellRead.Unreadable` / `ReadRefusal` | the host answered that it could not read this cell or trait |
| no verdict | `MatchResult.Undecided` / `Dropped` | the attempt could not conclude |

A `Reply` distinguishes `Mismatch` (refuted), `Unreadable` (not looked at), and `Abstain`
(declined). Collapsing any two is a bug, not a simplification. A refused read is a value, not a
`null`: `null` is reserved for a genuine optional lookup and never means "unreadable". A consumer
that receives `null` from a total read has a producer bug; a consumer that receives
`CellRead.Unreadable` has a fact it must report or propagate.

## Law 5 — One publication, at the end

A check computes values into its `MatchOutcome` and writes nothing. A successful settle publishes
the `Retention.instance` values once, at a new `contextRevision`. A derive publishes its own batch
once. There are no context tiers, no stage frames, and no per-phase store writes.

## Law 6 — Claims are derived, compared and replaced

A `ClaimRow` is produced only by `ClaimPlanner` from `(blueprint, siting, instance, bindings)`. The
caller cannot hand rows to `settle`. One ledger handle answers for one world, and it lives and dies
with that world's structure state. One predicate decides conflict: a cell is contested when either
row is `exclusive` and the two rows belong to different instances. One compare-and-set replaces
every row of one instance; a revision move is re-read once, then reported.

## Law 7 — Findings are the only report channel

A finding has a stable `FindingCode`, a severity, at least one located `Label` unless the code is
explicitly label-less, notes, and an optional suggestion. Severity lives on the finding; the
registry carries the canonical severity and one long-form explanation per code. Errors are never
capped. Message text is prose and is never parsed.

## Law 8 — The module graph is a DAG

Every package has a rank. A package imports only packages of a lower rank plus itself. `core` has no
kernel dependency. The edges are listed in `03-modules.md`; a new edge is a design change.

## Law 9 — Extensions are frozen values

Every extension point is an interface; every implementation is registered in a `Catalog` before a
kernel is created; a kernel holds no mutable registry and resolves no class by reflection. Missing
extension = a finding at the earliest phase that can know it, never a fallback.

## Law 10 — Data and code are one IR

A `Document` compiles to the same `Blueprint` a `BlueprintBuilder` produces. The document is data:
ids, boxes, coverage, sharing, footprints, focus/expectation/computation kinds, key declarations,
placements, extensions. Java carries the behaviour: a focus, an expectation, a computation, a
deriver, a remedy provider. There is no second semantic path and no "compact profile".

## Law 11 — Determinism is a public promise

For a fixed `(blueprint, snapshotId, siting, owner, budget, claims revision, catalog snapshot)` the
result value, the finding order and the spend are identical across runs and JVMs, for a check that
honours the purity contract (D-41). A check that dereferences a live host value accepts that its
verdict may differ between runs. All ordered collections are built in a defined order; ties are
broken by declaration index, never by hash. The `determinismCheck` gate proves the promise across
three separate JVM processes against one golden file (D-75).

## Law 12 — The design is normative

Every public signature in a module file is normative, and the code is corrected to it. A public
member in code that is absent from the set is a conformance defect; a signature in the set that does
not describe a compilable Java shape is a design defect. Nothing is regenerated: a drifted module is
corrected in place, never deleted and rebuilt from the set.

## What is explicitly not a law

- "Everything must be a record." A type with a private constructor and a cache is a class; a sealed
  family is an interface. Shape follows the job.
- "No mutable objects anywhere." A builder is mutable and single-threaded; a built value is not.
- "No null." `@Nullable` is the project's absence marker; `Optional` is not used in public API.
- "No exceptions ever." Construction of a malformed value throws `IllegalArgumentException`; a
  structural outcome never throws.
