# StructureCumulus design set

Entry point: [spec/_index.md](spec/_index.md). New to the project? Read
[guide.zh.md](guide.zh.md) first: it builds one structure end to end and then tours the systems.

The design is split into a foundation layer, the unit files, and one consolidated design. Start with:

1. [spec/00-overview.md](spec/00-overview.md) — requirements, invariants, units.
2. [spec/01-glossary.md](spec/01-glossary.md) — the closed vocabulary.
3. [spec/02-architecture.md](spec/02-architecture.md) — package graph, stages, source sets.
4. [spec/03-naming.md](spec/03-naming.md) — collision bans and module template.
5. [spec/04-api-doctrine.md](spec/04-api-doctrine.md) — composition, type safety, golden example.
6. [spec/17-worked-example.md](spec/17-worked-example.md) — the huge furnace end to end.
7. [spec/18-verification.md](spec/18-verification.md) — gates and counterexamples.
8. [spec/19-open-proposals.md](spec/19-open-proposals.md) — deferred features and the reconciliation record.
9. [spec/20-execution-and-sections.md](spec/20-execution-and-sections.md) — worker execution, section snapshots, dirty state, section cache.
10. [spec/21-authoring.md](spec/21-authoring.md) — compact JSON authoring profile and capture/import tools.
11. [spec/22-consolidated-design.md](spec/22-consolidated-design.md) — consolidated design, including shared intermediate state and cross-section criteria.

The kernel source set is `src/kernel/java`; it compiles with JSpecify only. The Minecraft
1.21.1 NeoForge adapter is `src/main/java/dev/vfyjxf/cumulus/mc`. No file under `src/kernel`
may name Minecraft, NeoForge or Mojang; `./gradlew compileKernelJava kernelPurity` proves it.
The design is a falsifiable draft: module open questions are listed in each unit file.

The design runs ahead of the kernel. Where a unit file describes a type or a path the source does
not have yet, the design states the intent and `src/**` states what is built; there is no separate
ledger of the distance, and a claim about the kernel is only as good as the code it points at.
