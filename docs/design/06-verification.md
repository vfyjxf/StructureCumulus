# 06 — Verification

> What must be true before the design is accepted, and how each claim is falsified. The design is a
> falsifiable draft; a module without a counterexample is not reviewed.

## 1. Gates

| Gate | Command | Pass means |
| --- | --- | --- |
| **Compile** | `javac --release 21` over `src/kernel/java` with JSpecify only | every public signature in the module files exists and compiles |
| **Purity** | `kernelPurity` text scan + no Minecraft on the kernel classpath | no `net.minecraft`, `net.neoforged`, `com.mojang` under `kernel.**` |
| **Tests** | JUnit 5 over `src/kernelTest/java` | every test in §3 passes; no `@Disabled` |
| **Spec index** | `tools/spec-check.py` | every documented public type and `SC-` code has a producer; no extra public type |
| **No stubs** | grep | no `TODO`, `FIXME`, `UnsupportedOperationException`, empty public method body |
| **Determinism** | golden test | two runs over one tuple produce equal values, orders and spend |

Gradle is the shipping build (`./gradlew compileKernelJava kernelPurity kernelTest`). The session that
regenerates the kernel may not be able to run Gradle (the wrapper cache is outside the sandbox); it
must still run `javac` and the JUnit launcher directly and record the commands.

## 2. Requirement → module → test

| Requirement | Modules | Test |
| --- | --- | --- |
| F-01 occupants are not blocks | world, structure | entity pen, nested |
| F-02 purity | all | purity gate |
| F-03 typed context | context, structure | furnace capacity, iron count |
| F-04 anchor/no controller | structure, space | anchor read; orientation policy |
| F-05 atomic bay/door | structure, claim | door scenario |
| F-06 claims | claim, engine | hatch, door, pipe |
| F-07 check/settle split | engine, spi | "check writes nothing"; settle conflict |
| F-08 data + code one IR | document | campfire/furnace parity |
| F-09 findings | finding | golden human text; bundle order |
| F-10 catalogs | spi, structure | builtin + addon factory |
| F-11 remedy values | remedy | provider order/cap |
| F-12 determinism/budget/cancel | core, engine | budget, duplicate run, cancel |

## 3. Scenario tests

These are end-to-end kernel tests with an in-memory `WorldView`, `ClaimLedger` and `ContextStore`.
They are the acceptance suite; a module test that does not feed one of them is optional.

1. **Campfire** — 3×3×3 shell + hollow + lit fire; 3 checks; `air` section computation; check →
   admitted → settle → formed → derive. Asserts the shell subtraction, air occupant, property test,
   the published `Long`, and that `check` wrote no claim.
2. **Furnace** — shell/cavity/mouth, `Box` cavity, door expectation, `volume` + capacity deriver;
   asserts claim rows, context revision, and a second check at the same siting does not self-conflict.
3. **Door** — one bay, two halves, `oneSubject + Bound`; missing half → `Undecided`; wrong half →
   `Failed` with one finding and two labels; both correct → two rows.
4. **Hatch** — two `shared` owners coexist; an `exclusive` owner conflicts; `release` lets a third
   settle; a revision moved once retries, twice is `Undecided`.
5. **Pipe corner** — `everyCell + Bound`; eight asserted, four owned; a neighbour's corner does not
   conflict over the four air cells.
6. **Entity pen** — `anySubject + Bound`, entity focus, window read; a footprint leaving the bay is
   `Failed`; two entities is ambiguous; a body wider than `maxEntitySpan` is a build error.
7. **Absence** — a refused middle section makes a contributed key absent; a check reading it is
   `Unreadable`, never a short sum; `whenAbsent` is used only when all sections are readable.
8. **Budget** — a budget too small for the section pass is `Undecided(budgetExhausted)`, no claims.
9. **Gate** — a lease that is no longer current at the start or after the units is `Dropped`, no
   claims, no context publish.
10. **Bindings** — a subject from another snapshot, a nested frame, a cell outside the bay, and an
    empty exclusive `Bound` are each `Failed(SC-CLM-0004/0005)`; nothing is written.
11. **Derive** — derivers run in `(order, id)`, one publish, missing deriver is `SC-CTX-0004`, a
    `match` output is rejected, a stale context is not possible by contract.
12. **Document parity** — the campfire/furnace as a `Document` and as a builder produce equal
    blueprints; a bad schema field/kind/placement produces the documented code and no blueprint.

## 4. Module tests

| Module | Must test |
| --- | --- |
| `core` | id grammar, catalog freeze, Value content equality, budget, key equality |
| `space` | packing bijection, cuboid edges, CuboidSet normalization and semantic-equality laws, 48 orientations, mirror `1-`, placement laws, section frame, locus frames |
| `finding` | code parse, `Codes.all` bijection, builder validation, bundle dedup/order/cap, renderers, source text |
| `world` | air vs refused, statusOf, multi-cell, nested frame/ref, section digest, descriptor/trait, window |
| `claim` | matrix four pairs, ledger CAS/release, canonical order, footprint resolution |
| `structure` | bay/coverage rules, reply combinators, focus kinds, build table, plan DAG, anchors, computations |
| `context` | context view, publish merge/revision, derive order, merges |
| `document` | schema validation, placement/alias, key types, compile parity, determinism |
| `engine` | §3 scenarios, gate, budget, fold, ledger retry |
| `remedy` | provider order/cap, edit/overlay values |
| `spi` | builtins, host empty, facade paths, two-kernel isolation |

## 5. Code producers

The authoritative table is `finding.md` §6. A test reads `Codes.all` and asserts:

- every entry has a non-blank summary and body;
- every code appears exactly once in the producer table (a hand-maintained `Map<FindingCode,String>`
  in the test, checked against `Codes.all`);
- no producer row names a code absent from `Codes.all`;
- `SC-MC-*` are absent from `Codes.all` (adapter-only).

A code with no producer is deleted, not reserved. An unknown addon code is legal; it is not in
`Codes.all`.

## 6. Falsification questions

The reviewer's job is to find a concrete structure or snapshot that breaks a claim:

1. Can a `Formed` exist without a `Settled`? (engine: no path)
2. Can a `check` write a claim or a context value? (engine: no writer)
3. Can a `Mismatch` and an `Unreadable` collapse in the fold? (structure/engine)
4. Can a `Bound` footprint own a cell no check asserted? (claim/structure)
5. Can a nested subject become a parent claim? (engine: frame check)
6. Can a `Bay` with a `CuboidSet` shell be split differently by the Java and JSON paths? (same `Bay`)
7. Can two `shared` bays of one blueprint conflict? (matrix: same owner/siting excluded)
8. Can a finding be ordered differently by two JVMs? (all orders are explicit)
9. Can a document with reordered members produce a different blueprint? (canonical compile)
10. Can a stale lease produce a `Formed`? (gate before and after)
11. Can a budget-exhausted attempt be turned into a `Formed` by a warm cache? (no cache)
12. Can a missing extension be silently replaced by a default? (no reflection, no fallback)

## 7. What is not verified in this round

- The rest of the NeoForge adapter and its GameTests: the M1 capture/snapshot/lease slice is executed
  by `runGameTestServer`; nested frames, entities/traits, claims/context persistence, JSON I/O,
  commands and the orientation/door/furnace GameTests are still reviewed, not executed.
- The scheduler, dirty index and per-tick window diff; host-side, out of v1.
- Rendering of overlays; the kernel produces data only.
- A real JSON parser; the kernel ships `Value` and the ports; a test writer/reader is test-only.
