# StructureCumulus — design of record

This directory is the **source of truth** for StructureCumulus. The kernel under `src/kernel/java`
is generated from these files and is allowed to be deleted and regenerated at any time. A fact that
is not in this directory is not part of the design.

The design set is deliberately smaller than a specification suite: one constitution, one model,
one module graph, one API tour, one decision record, and one file per module. Every module file is
normative: its Java blocks define the public shape, its tables define the laws, and its producer
table defines who may emit each finding code.

## Reading order

1. [design/00-requirements.md](design/00-requirements.md) — what the library must do, and what it
   refuses to do.
2. [design/01-constitution.md](design/01-constitution.md) — the twelve laws every module must obey.
3. [design/02-model.md](design/02-model.md) — the one-page model, the three phases, the data flow.
4. [design/03-modules.md](design/03-modules.md) — the eleven kernel packages, their ranks and edges.
5. [design/04-api.md](design/04-api.md) — the surfaces and the worked examples a user writes.
6. [design/05-decisions.md](design/05-decisions.md) — every ruling that replaced an older one.
7. [design/06-verification.md](design/06-verification.md) — gates, scenario tests, code producers.
8. [design/07-regeneration.md](design/07-regeneration.md) — how the kernel is rebuilt from this set.

There is also an interactive, adversarial review of this design set in [`../visual/`](../visual/README.md). It is a reading aid; this directory remains the source of truth.

Then the module files, in dependency order:

| Rank | Module | File |
| --- | --- | --- |
| 0 | `kernel.core` | [modules/core.md](modules/core.md) |
| 1 | `kernel.space` | [modules/space.md](modules/space.md) |
| 2 | `kernel.finding` | [modules/finding.md](modules/finding.md) |
| 2 | `kernel.world` | [modules/world.md](modules/world.md) |
| 3 | `kernel.claim` | [modules/claim.md](modules/claim.md) |
| 4 | `kernel.structure` | [modules/structure.md](modules/structure.md) |
| 5 | `kernel.context` | [modules/context.md](modules/context.md) |
| 6 | `kernel.document` | [modules/document.md](modules/document.md) |
| 7 | `kernel.engine` | [modules/engine.md](modules/engine.md) |
| 8 | `kernel.remedy` | [modules/remedy.md](modules/remedy.md) |
| 9 | `kernel.spi` | [modules/spi.md](modules/spi.md) |
| A1 | `dev.vfyjxf.cumulus.mc` | [modules/adapter.md](modules/adapter.md) |

## Status

- Design revision: **v1.0**, 2026-10-26.
- Scope of this round: **core kernel + document compiler + thin remedy SPI**. The NeoForge adapter
  is specified but not implemented in this round.
- Language: English only. There is no mirrored translation. A Chinese quick-start may cite this
  set, but the English files are the design of record.
- Code status: `src/kernel/java` is generated from these files; `src/main/java` is the mod entry and
  the adapter. The adapter is the only place allowed to name Minecraft.
- Verification (2026-10-26): the kernel compiles with `javac --release 21` and JSpecify only; 95
  JUnit 5 tests pass; `tools/spec-check.py` is clean; the adapter is specified, not implemented.
  Gradle could not run in the generation sandbox, so `./gradlew check` is the first thing to run on
  a normal machine (it also applies the project formatter).

## The one rule

**If the code and this directory disagree, this directory wins and the code is regenerated.**
A change to a public shape is a change to a module file first; the code follows in the same commit,
or in a regeneration run.
