# Presentation pack milestone plan (working document, not normative)

> This plan tracks execution. The normative contract is
> [`docs/design/16-presentation.md`](../design/16-presentation.md) plus the decision record
> (D-93…D-98) and the module files; if this file and those disagree, they win. Source plan:
> `Data/StructureCumulus/plan-2026-11-12-presentation/00-roadmap.md`.

## Scope

The presentation pack over the existing preview and build layers — the stage model, staged playback,
the progress HUD, the stage banner, the placement feedback animation, the document's carried part text
and its display points, the JEI view modes and the guided build — for Minecraft 1.21.1 / NeoForge
21.1.x. It adds no capability: no new world write, no new claim path, no new finding family (design 16
§2, §14). Non-goals are design 16 §16.

## Milestones and state at 2026-11-12

Every milestone starts `pending` and is complete only when its gate is green on the tree. The `State`
cell names the commit the milestone landed at; a milestone is closed by its own gate, not by its main
commit.

| Milestone | Deliverable | Depends on | Gate | State |
| --- | --- | --- | --- | --- |
| E0 | Design revision: design 16, D-93…D-97, the document members in `document` §4/§6/§8, adapter §14/§15/§19, this plan, the README status | — | `tools/spec-check.py` clean (both the name-level and the code/producer halves); main-agent line review; `docs/README.md` agrees with this file | **pending** — the design landed with this commit, and the review closes it |
| E1 | The stage model, the payload extension (the presentation block with the stage table and the frontier, one stage index per ghost, on both the session payload and the catalog), staged playback and stage switching, the three HUD tiers, the stage banner, the client config file with the `hud.*` keys and `animation.enabled`/`animation.speed` (design 16 §4–§8, §13) | E0 | The stage derivation's determinism and invariance unit tests (scrambled input, re-posed siting, empty and degenerate tables); uitest `stage` group (open a multi-stage document, count stages, switch, play forward, read the bar); existing `preview`/`jei` groups not regressed; `check` green | **done** at `5b89140`; the gate closed it on that tree |
| E2 | The commit feedback payload, pop-in, the sound rate limit, the rollback/undo fade, the formed celebration, and the `animation.sound`/`animation.celebrate` keys (design 16 §9, §13) | E1 | uitest assertions on the animator's own state (a run animating within N frames, finishing, an undo fading, a sound request count above zero — model assertions, never pixels); `check` and `runGameTestServer` green; dedicated-server isolation unchanged | **done** at `39e23b3`; the gate closed it on that tree |
| E3 | The document members `description`/`hint` and the carry law, the display points in the HUD, the JEI entry and the part-hint billboard in the world (design 16 §3, §10) | E0 | Document compile tests (absent, all four members, a non-string, a definition-level root description, equality of two documents differing only in a description); HUD, catalog and billboard display uitest; `kernelTest` and `check` green; `document` §3 and adapter §15 updated in the landing commit | **done** at `930b35c`; the gate closed it on that tree |
| E4 | The JEI view modes — three-view and slice grid beside the isometric view — with per-panel tooltips and the shared slice (design 16 §11) | E0 | uitest `jei` group: the mode button cycles, all three panels render a non-empty scene, a tooltip resolves, the slice moves all three panels; isometric not regressed; a no-JEI `check` green | **done** at `8aea22c`; the gate closed it on that tree |
| E5 | Guided mode (entry, emphasis, next-step line, hand awareness, auto-advance) and the pack close-out (design 16 §12) | E1 + E3 | The full guided uitest chain (enter, hand-place a stage's blocks through the dev command, auto-advance with the banner, the hand pulse, a hybrid confirm); all gates green; the count claims and this file refreshed; `tools/spec-uncovered.txt` only shrinks | **done** at `ee679ee`; the gate closed it on that tree |
| E6 | Variant groups: the document `variants` block, the payload summary, the preview/JEI switching and the build pass-through (brief `19-brief-e6-variants.md`; the design fix lands in the main commit) | E1 + E3 | Document compile unit tests (absent, members, bad member, self-reference); uitest: the variant switch re-opens the session, the JEI in-entry switch, a confirm on the selected variant builds; `check` and `runGameTestServer` green | **done** at `7c4ad57`; the gate closed it on that tree |
| E7 | Build presets: save/apply/run/preview/list/delete over `PreferencesSavedData`, with the apply-equals-typed-command safety law (brief `20-brief-e7-build-presets.md`) | E1–E6 | `BuildPreset` serialisation round-trip and version gate unit tests; GameTests: apply after a world change refuses, apply on a sound world commits, capacity refusal, the routing exit codes; `check` and `runGameTestServer` green | **done** at `23f0e49`; the gate closed it on that tree |
| E8 | The cinematics integration: `CinematicController` rig wiring, the camera program from the stage table, the turntable and stage transitions, guided/cinematic mutual exclusion, the `cinematics.*` config keys (brief `23-brief-e8-cinematics.md`; the rig/coupling design lands in design 16 in the main commit) | E1 + E2 + E5 | uitest: enter/exit with the player camera restored, the program sampling advances, the turntable frame count and the final-tick pose swap, the C/G mutual kick, the keybind conflict check; `check` green; dedicated-server isolation unchanged | pending |
| E9 | The final demo: the `preview.demo` scenario (seed already in the tree) run green with its screenshot/timeline evidence filed under `Data/StructureCumulus/` (brief `24-brief-e9-demo.md`) | E4–E8 | uitest group `demo` green; the evidence set filed; `docs/plan/visual-client-tests.md` carries the demo row; `check` green | pending |

Order: E0 first and alone; then E1, E2 in sequence (E2 animates what E1's payloads carry); E3 and E4
are independent of each other and of E1/E2 in files, but they run one at a time — one worktree, one
Gradle daemon, one git index (roadmap §4). E5 needs E1's stage model and E3's hint channel. E6 needs
E1/E3 in place; E7 needs E1–E6; E8 needs E1's playback, E2's atoms and E5's guided mode (the C/G
mutual exclusion); E9 demos the landed pack.

## Backlog (roadmap §2, no dates promised)

- **A.6** 3D billboard stage numbers in the world (E3's part `hint` billboard is design 16 §10's
  display point, not a stage number).
- **B.5** broadcasting another player's placement animation (v1 animates the confirming player only).
- **C.5** localising the HUD vocabulary and the next-step formatter (v1 keeps literal English; every
  other UI string is already literal, so this is one round when it is taken).
- **F.5** the half-automatic "shall I build the rest?" dialogue between guided mode and the executor.
- **Hints on a focus-id part** — v1 hints are bay-scoped, because a bay is what a stage is derived from
  (design 16 §3, §4).
- **A parameterized variant form** — v1 groups are static combinations of blueprint documents (E6,
  design 15 §12, D-98). One declaration plus per-instance arguments that change geometry stays
  backlog; it would move the variant into the compiler, the catalog and the build request at once.

Absorbed into the pack and therefore no longer backlog: P8 **5.6** rollback visibility (E2's fade) and
P8 **4.3** hand-aware pulse (E5's hand awareness). P8 **8.2** quiet HUD mode stays backlog: the pack's
tiers are quiet-compatible (design 16 §7.4) and depend on nothing from it.

## Explicitly not done

- **B.6** particle trails and beams (decoration without information).
- **D.4** exporting a view as an image.
- Any web `visual/` synchronisation of the staged playback (design 16 §16).
- A server-side stage or playback state, a server-side animation clock, or a second client config
  file.

## Open items for the main agent

- **OQ-1 (payload shape, resolved at E0).** The stage table and one stage index per ghost cell travel
  on the existing session payload and on the catalog entry (design 16 §5): the shape changes once, at
  E1, and E3 only fills the two text fields it already carries. Propagating the fields to the catalog
  is part of E1, not a separate milestone, because one cell type must mean one thing.
- **OQ-2 (banner text, resolved at E0).** A bay's `description` already reached the compiled blueprint
  before this pack, so E1's banner can show it; E3 adds the root description and the bay hint. The
  roadmap's "E3 后带描述" is therefore a subset of what E1 can do, not a cap on it.
- **OQ-3 (guided completion, resolved at E0).** A fully satisfied session's confirm is the idempotent
  empty report, so the guide's completion state names settle as the claim step instead of promising a
  formation (design 16 §12.3).
