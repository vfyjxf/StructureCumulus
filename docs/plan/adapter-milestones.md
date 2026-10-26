# Adapter milestone plan (working document, not normative)

> This plan tracks execution. The normative adapter contract remains
> [`docs/modules/adapter.md`](../modules/adapter.md); if this file and the module file disagree, the
> module file wins.

## Scope

Adapter implementation for Minecraft 1.21.1 / NeoForge 21.1.x, plus GameTests, release gates and a
clean kernel regeneration. No client overlay, network payload, schematic importer, GUI or scheduler.

## Milestones

| Milestone | Deliverable | Acceptance |
| --- | --- | --- |
| M0 | Freeze `kernel-v1`; adapter type/code/GameTest checklist | tag + checklist |
| M1 | Minimal capture/lease/snapshot, in-memory host, `/cumulus check`, two GameTests | `compileJava`, capture-only + capture→check GameTests |
| M2 | Nested frames, entities, traits, orientation, capture findings | capture/orientation/nested/entity GameTests |
| M3 | `SavedClaims`, `SavedContexts`, CAS/release/reload | conflict/release/reload tests |
| M4 | JSON reader/writer, datapack reload, remaining commands | roundtrip, datapack reload, settle/claims command |
| M5 | Required GameTests, determinism golden test, Gradle gates | `./gradlew check` green |
| M6 | Clean delete/regenerate on a branch | reproducible delete→regenerate→green |

## Owners

- Main: contract arbitration, docs amendments, integration, commits, final gates.
- A: `mc/capture`, `mc/world`, `mc/orientation`.
- B: `mc/claim`, `mc/context`.
- C: `mc/document`, `mc/host`, `mc/command`, mod entry.
- D: GameTests and test resources.
- E: build/CI, determinism, regeneration branch.

## Rules

- Kernel public ports stay frozen; an adapter gap is a docs amendment first.
- Dependency source reads go through SourceScout.
- One file owner at a time; at most two implementation subagents concurrent.
- Every milestone is a green checkpoint, not a partial merge.
