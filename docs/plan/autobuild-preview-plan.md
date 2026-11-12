# Auto-build + preview milestone plan (working document, not normative)

> This plan tracks execution. The normative contracts are
> [`docs/design/14-auto-build.md`](../design/14-auto-build.md),
> [`docs/design/15-preview.md`](../design/15-preview.md) and the module files; if this file and
> those disagree, they win. Source plan:
> `Data/StructureCumulus/plan-2026-11-11-autobuild-preview/00-roadmap.md`, with the ergonomics
> convergence of `09-ergonomics-expansion.md` §11.

## Scope

Kernel remedy built-ins and solve preferences, the adapter build executor with item sources and
transactions, the in-world and JEI previews, the preference system, structure-set build, and the
plugin extension points — for Minecraft 1.21.1 / NeoForge 21.1.x. Non-goals are design 14 §15 and
design 15 §11.

## Milestones and state at 2026-11-11

Every milestone starts `pending` and is complete only when its gate is green on the tree.

| Milestone | Deliverable | Depends on | Gate | State |
| --- | --- | --- | --- | --- |
| P0 | Design revision (roadmap §10: designs 14/15, remedy/spi/adapter module updates, D-84…D-91, this plan) | — | `tools/spec-check.py` + `Documents/tools/kb_check.py` green; main-agent line review | **done at `1e050b6`** |
| P1 | Kernel: `CatalogRegistrar.remedy`, the two built-in providers, `SolvePreferences` + `RemedyContext.preferences`, the D-91 declaration description port, kernel tests | P0 | `kernelTest` green (mapping/derivation/preference/determinism cases); `check` green | **done at `40ea891`** |
| P2 | Adapter build core, single structure (design 14 §4–§12) | P1 | new GameTests (place/replace/removal gate/rollback/idempotence/shortage/undo) + `check` + `runGameTestServer` green | **done at `00f129f`** |
| P3 | In-world preview (design 15 §3–§5, §7–§8) | P1 | uitest scenarios green (open/turn/flip/invalidate/confirm); isolation gates unchanged | **done at `3bf6a23`** |
| P4 | JEI GUI preview (design 15 §6) | P3 | uitest JEI scenario + a no-JEI `check` green | **done at `8b6ffcf`** |
| P5 | Preferences (design 15 §9) | P2 + P3 | scoring determinism unit tests + memory GameTest + `/cumulus prefs` routing test | **done at `f3bbd64`** |
| P6 | `/cumulus set build` (design 14 §13) | P2 + P5 | set-build GameTests (skip present/member-failure rollback/shared source) | **done at `298e6fd`** |
| P7 | Plugin extension tables (`registerMaterializers`/`registerItemSources`), the command's `with <kind-id>` form, the single build's `--settle` chain, and the docs/count close-out (roadmap §9, §10 remainder) | P2–P6 | plugin parity test + all existing gates + count refresh | **done at `dd58b6c`** |

Concurrency discipline (roadmap §11): at most two implementation subagents at a time; file
ownership is unique (P2 owns `mc.build`, P3 owns `mc.preview`; the two may run in parallel). P1
lands alone first. P3 depends only on P1 (the `OverlayModel` derivation), not on P2.

## P8 backlog (from `09-ergonomics-expansion.md` §11, no dates promised)

1.3 area box selection, 1.4 held-block inference, 2.3 swap gesture, 3.3 neighbour alignment
(`ALIGN`), 4.7 dual-encoding for colour blindness, 5.2 persistent ghosts, 5.3 bounded undo/redo
stack, 5.4 per-layer delivery, 5.5 skeleton-first build, 5.6 rollback visibility, 6.1 foreign
ghost de-saturation, 7.3 named preference profiles, 7.4 server default profile, 8.2 quiet HUD
mode, 8.3 command completion, 10.2 leave-the-last-block mode.

Outside the ergonomics numbering: config-izing the preview payload caps (design 15 §3; v1 keeps
the code constants, 512 ghost cells and 48 markers per payload).

Two entries above have moved into the presentation pack and are tracked there instead: 4.3's
hand-aware pulse and 5.6's rollback visibility are E5 and E2 of
[presentation-plan.md](presentation-plan.md) (design 16 §12.4, §9.4).

Explicitly not done: 3.5, a lossy orientation back door (D-88).

## Open questions for the main agent

- **OQ-1 (kernel port for D-85) — resolved by D-91.** The built-in providers derive
  `Place`/`Replace` payloads from the blueprint's declared expectations through the new total
  declaration description port (`structure` §4.12, D-91); `custom` expectations are undescribable
  and degrade to advisory. P1 implements the port (and, if the cell→expectation resolution also
  lacks a public port on `Blueprint`, that port too — inside D-91's scope, reported, no new
  number).
- **OQ-2 (set build and claims) — resolved.** Build is not settle: a default build/set build takes
  no claims and its rollback covers world writes only; the `--settle` flag chains the existing
  settle path after the `Formed` re-check (kernel `settle` + attempt for one structure,
  `StructureSetHost.run` for a set), and only then does rollback cover claims (design 14 §10/§13).
