# StructureCumulus specification — index

> Design draft v0.2, 2026-10-20, with the consolidated design at revision 5 and the `23`/`24` claim
> decision records of 2026-10-23. The five foundation files, the twelve unit files, the consolidated
> design (`22`) and the two decision records are drafted; a reconciliation pass has fixed the
> cross-unit signatures (see `19`). Remaining open questions are recorded per unit and in `22` §16.
> This is a falsifiable design, not an implementation.

## Routing table

| If you need… | Read |
| --- | --- |
| The vision, the fifteen requirements, the invariants, the non-goals | [00-overview.md](00-overview.md) |
| The meaning of any kernel name, and the banned-name list | [01-glossary.md](01-glossary.md) |
| The package graph, data flow, stages, threading and source sets | [02-architecture.md](02-architecture.md) |
| The naming rules and the nine-section module template | [03-naming.md](03-naming.md) |
| Composition, typed keys, sealed results, the golden example | [04-api-doctrine.md](04-api-doctrine.md) |
| Cells, cuboids, the 48 orientations, siting, loci | [05-space.md](05-space.md) |
| Occupants, snapshots, the world-view SPI, unreadable cells | [06-occupants.md](06-occupants.md) |
| Expectations, verdicts, combinator laws, the door case | [07-expectations.md](07-expectations.md) |
| Claims, sharing, exclusivity, reservations, the ledger | [08-claims.md](08-claims.md) |
| Bays, seams, segments, anchors, blueprints, stage plans | [09-blueprints.md](09-blueprints.md) |
| Document grammar, canonicalisation, assembly, generation | [10-documents.md](10-documents.md) |
| The matcher, prescreen, budgets, invalidation, generations | [11-engine.md](11-engine.md) |
| Derivers, typed context, augments, runtime state, upgrades | [12-context.md](12-context.md) |
| Findings, codes, labels, ordering, renderers, `--explain` | [13-findings.md](13-findings.md) |
| Repair plans (the `solve` operation), overlays, dry-run, transactions, auto-build | [14-remedies.md](14-remedies.md) |
| Catalogs, the facade, extension registration, stability | [15-spi.md](15-spi.md) |
| The 1.21.1 NeoForge adapter: capture, orientation, reload, commands | [16-adapter.md](16-adapter.md) |
| One huge furnace walking the whole pipeline | [17-worked-example.md](17-worked-example.md) |
| The verification plan, gates and counterexamples | [18-verification.md](18-verification.md) |
| Features deliberately left out, and the reconciliation record | [19-open-proposals.md](19-open-proposals.md) |
| Worker execution, section snapshots, dirty state, section cache | [20-execution-and-sections.md](20-execution-and-sections.md) |
| Compact JSON authoring profile and capture/import tools | [21-authoring.md](21-authoring.md) |
| Consolidated design, including shared intermediate state and cross-section criteria | [22-consolidated-design.md](22-consolidated-design.md) |
| Claim entry: read-only `check` vs writing `settle`, the release path, and why stages are no longer declared | [23-claim-entry.md](23-claim-entry.md) |
| The claim vocabulary: three questions (what to prove, what to own, how claims meet), R1–R4 | [24-claim-vocabulary.md](24-claim-vocabulary.md) |

## Units

| Unit | File | Status | Owner |
| --- | --- | --- | --- |
| U0 foundations | `00`–`04` | reviewed | main |
| U1 space | `05-space.md` | drafted | subagent |
| U2 occupants | `06-occupants.md` | drafted | subagent |
| U3 expectations | `07-expectations.md` | drafted | subagent |
| U4 claims | `08-claims.md` | drafted | subagent |
| U5 blueprints + plans | `09-blueprints.md` | drafted | subagent |
| U6 documents | `10-documents.md` | drafted | subagent |
| U7 engine | `11-engine.md` | drafted | subagent |
| U8 context | `12-context.md` | drafted | subagent |
| U9 findings | `13-findings.md` | drafted | subagent |
| U10 remedies | `14-remedies.md` | drafted | subagent |
| U11 SPI / facade | `15-spi.md` | drafted | subagent |
| A1 adapter | `16-adapter.md` | drafted | subagent |
| X execution | `20-execution-and-sections.md` | reviewed | main |
| A2 authoring | `21-authoring.md` | drafted | main |
| R consolidated | `22-consolidated-design.md` | review | main |
| cross-unit | `17`, `18`, `19`, `20` | reviewed | main |

## Review gates

1. **Vocabulary gate** — every cross-unit name in a module appears in `01-glossary.md` with
   the same owner; no synonym is introduced.
2. **Dependency gate** — a module imports only packages above it in `02-architecture.md` §2.
3. **Shape gate** — the nine sections of `03-naming.md` §8 are present, and the reference
   sketch obeys `04-api-doctrine.md` §11.
4. **Invariant gate** — each of `00-overview.md` §4's invariants that the unit touches is
   either proved or explicitly deferred.
5. **Falsification gate** — the module names a concrete blueprint, world state or test that
   would break it.

## Document status vocabulary

- **drafted** — written, not reviewed.
- **reviewed** — passed the five gates; open questions are ruled or deferred.
- **frozen** — no further name changes without a glossary amendment.
