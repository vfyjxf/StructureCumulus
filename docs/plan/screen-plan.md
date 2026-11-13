# Preview screen pack milestone plan (working document, not normative)

> This plan tracks execution. The normative contract is
> [`docs/design/17-preview-screen.md`](../design/17-preview-screen.md) plus the decision record
> (D-101…D-105) and the module files; if this file and those disagree, they win. Source plan:
> `Data/StructureCumulus/plan-2026-11-12-preview-screen/00-roadmap.md`.

## Scope

The preview screen pack over the existing preview and presentation layers — a client-only `Screen`
over the catalogue, the camera and its projections, four linked viewports, the axis gizmo, the staged
playback and its delta, the per-bay exploded view, the five entry links and the demo content — for
Minecraft 1.21.1 / NeoForge 21.1.x. It adds no capability: no new world write, no new claim path, no
new finding family, no new cap (design 17 §2, §12). Non-goals are design 17 §14.

## Milestones and state at 2026-11-12

Every milestone starts `pending` and is complete only when its gate is green on the tree. The `State`
cell names the commit the milestone landed at; a milestone is closed by its own gate, not by its main
commit.

| Milestone | Deliverable | Depends on | Gate | State |
| --- | --- | --- | --- | --- |
| SC0 | Design revision: [design 17](../design/17-preview-screen.md) (the whole roadmap), D-101…D-105, adapter §20, this plan, the README status, and the two-sentence correction of design 15 §6.1/§6.2 | — | `tools/spec-check.py` clean (both the name-level and the code/producer halves); `./gradlew compileJava -q` green; main-agent line review; `docs/README.md` agrees with this file | **done** at `cdf000b`; the main agent's line review closed it (one dead KB link corrected to 16's plain-text form) |
| SC1 | The screen skeleton: the package migration to `mc.preview.scene`, `PreviewScreen`, the 45° orthographic default and the projection toggle, the main viewport plus three linked ones, the axis gizmo, the R11 camera, the drawn-hull fit (R5) in both consumers, and the `Stage.bay` carried fact on both payloads (design 17 §3, §4, §5, §6, §10; `PreviewOpenPayload.facing` lands with its SC3 consumer) | SC0 | Camera, projection, hull and layout math unit tests (deterministic, no client; the explode math is SC2's, design 17 §15); uitest `screen` group (open the page, four viewports, an orbit and a zoom assertion, the axes present, the key table's uniqueness and no world binding firing); the existing `preview`/`jei`/`stage` groups not regressed; `check` green | **done** at `0eb4ee9` |
| SC2 | Playback and the exploded view: the whole view, play/pause/step/jump, the delta treatments, the summary line, the explode tween with bay labels, the per-stage framing (design 17 §7, §8) | SC1 | uitest (the play advances and stops, the delta's drawn count, the explode's offsets at t=0 and t=1, the labels present, a manual gesture disarms the framing); `check` green | **done** at `9ddd59d`; the `screen` group now runs `screen.preview` and `screen.playback` (2 scenarios, 45 checks), the other seven groups and the 173-test GameTest baseline are green, and the scene's GUI depth layer was corrected under this milestone |
| SC3 | The links: JEI's `open in screen`, `/cumulus preview screen <id>`, the `O` world key, ghost to world with the screen's heading, the in-screen variant switch (design 17 §9) | SC1 | uitest (one route per entry point, the variant switch keeping the camera, the open payload's heading reaching the session's cycle); `check` and `runGameTestServer` green | **done** at `58970b3`; the `screen` group now runs `screen.preview`, `screen.playback` and `screen.links` (3 scenarios, 64 checks), the other seven groups and the 173-test GameTest baseline are green, and the open payload's heading travels as a trailing optional byte so an older client's open still reads as absent |
| SC4 | The demo content: the machine group (two members) and the tower in `src/main/resources`, the demo marking, the compile and catalogue tests, the count and documentation close-out (design 17 §11) | SC2 | Document compile unit tests over the three shipped files; the catalogue test that all three ids reach the payload; the uitest `demo` scene gains the screen; `check` and `runGameTestServer` green; `git status` clean | **done** at `2f0980a`; the three documents ship under `data/structurecumulus/cumulus/structure/` (`demo_machine` and `demo_machine_compact` as one variant group, the standalone `demo_tower`), `DemoDocumentsTest` proves the compile, the identity projection and the bay/stage/variant facts, `PreviewCatalogTest` proves the payload, and the `demo` group now opens the catalogue page on all three from `preview.demo.tour` (2 scenarios, 45 checks); the other seven groups and the 173-test GameTest baseline are green |
| SC5 | The screen rendering fixes: the directional lighting, the hover outline's projection, and the pop-in placement animation of the staged playback (design 17 §4.1/§7, D-106; brief `15-brief-sc5-render-fixes.md`) | SC1, SC2 | uitest `screen` group: multi-angle shading assertions, the outline inside the hovered cell's projected hull, the mid-play pop-in state and its settle; the existing groups not regressed; `check` green | **done** at `cffcf65`; the `screen` group now runs four scenarios (86 checks) including `screen.render` (the multi-angle shading, the silhouette outline inside the hovered cell's hull, the mid-play pop-in and its settle), the other seven groups and the 173-test GameTest baseline are green, and `visual.feature.move`'s authlib network-timeout error is recorded as an environment flake (the move event never fired, the signature identical across two runs) |
| SC6 | The presentation rework the user set after watching SC5: a play reveals a stage as one batch into empty slots (no unrevealed alpha, the span becomes the dwell), the outlines become smooth vector strokes that read through the models, the cursor's stage carries the stroke as a set, and the gizmo moves to the main viewport's top-right corner as a fixed-pixel compass (design 17 §6/§7.2/§7.5/§5.3, D-107; brief `17-brief-sc6-presentation.md`) | SC5 | Stage playback and gizmo unit tests updated (batch stamps, no unrevealed alpha, compass a pure function of camera+rect); uitest `screen` group: the batch reveal state, the empty slot before reveal, the stage outline count, the compass pinned under orbit/zoom; the existing groups not regressed; `check` and `runGameTestServer` green | **done** at `1b0f296`; the `screen` group now runs 90 checks (the batch pop of the remaining stage cells, the stepped cell out of an empty slot, the stage outline set in all four panels, the compass pinned under zoom and turning under orbit), the other seven groups and the 173-test GameTest baseline are green; the first implementer died mid-work and the main agent rescued the tree (NullAway, the compass's normalized axis, two self-contradicting tests) and finished the milestone itself |
| SC7 | The third presentation pass: the base plate under every page (§4.7), the reveal drop-in replacing the scale pop (§7.6), the Ponder pace (§7.5's constants retuned) and the compass drawn as vector strokes (§6) (D-108; brief `18-brief-sc7-base-drop-pace.md`) | SC6 | Unit tests: the plate's extent and its exclusion from the hull, the drop-in's fall and alpha legs, the retuned spans; uitest `screen` group: the plate present in all four panels and absent from the cell counts, a falling cell's offset and settle, the play's end-to-end ticks within the new pace; the existing groups not regressed; `check` and `runGameTestServer` green | **done** at `529d56d`; the `screen` group now runs 93 checks (the plate in all four panels and out of the cell counts, the mid-fall offset and the settle, the whole play's clock span against the new pace), the other seven groups and the 173-test GameTest baseline are green; the plate's look was revised afterwards — see SC8 |
| SC8 | The fourth pass's rendering truths: the JEI face leaves vanilla's item lighting for the world-fixed law (design 15 §6.2, 17 §4.1 — all three JEI view modes), and the base plate becomes a platform of two-cell slab blocks with a visible rim instead of the flat checkerboard (design 17 §4.7, D-109; brief `19-brief-sc8-jei-light-slab-plate.md`) | SC7 | Unit tests: the JEI pass's normal matrix and light directions, the slab grid's extent and box geometry; uitest `jei` group: a face's brightness equal across a drag and matching the screen's law; uitest `screen` group: the slab platform's rim and thickness; the existing groups not regressed; `check` and `runGameTestServer` green | **done** at `5f6a10e`; the `jei` group now runs 38 checks (the six world faces hold the screen's law across a yaw drag with the camera proven turned, and all three axis panels shade alike), the `screen` group 94 (the slab platform's grid, box geometry and rim thickness), the other six groups and the 173-test GameTest baseline are green, and the main agent re-ran `check` itself green before this mark |
| SC9 | The play's zero state and the reveal comparison, both from the viewer's fourth pass: a running play opens on the empty page — the base plate alone — and the first batch lands only after the opening hold (`hold → batch → dwell` per stage, §7.1/§7.5); and the rise narration beside the drop — a revealed cell climbs from the slab platform's underside to its slot — the stamp carrying the direction, plus the screen's drop/rise button as view state with no key, no config and no memory (§7.6/§4.5; brief `21-brief-sc9-rise-toggle.md`) | SC8 | Unit tests: the opening hold (the first batch is unrevealed for `stagePauseTicks`, then lands), the per-stage hold before every batch, a resumed play not re-running the hold; the rise leg starts at the plate's underside and settles at the slot, one batch's shared span with per-cell leg lengths, the stamp's direction surviving a toggle, the zero-duration leg in both directions; uitest `screen` group: the play's first frames draw zero cells, the drop is the default, a rising cell sits below its slot mid-leg and settles after, the toggle flips the next reveal's direction and flips back; the existing groups not regressed (their end-to-end tick counts gain the opening hold); `check` and `runGameTestServer` green | **done** at `beb45b3`; the `screen` group now runs five scenarios (107 checks) including `screen.reveal` (the opening hold's empty first frames, the default drop, a risen cell below its slot mid-leg and settled, the toggle flipping the next reveal and back) and a new `screen.links` check that a variant switch never re-narrates a kept stage (a real deviation the milestone caught and fixed, inherited from SC7), the other seven groups and the 173-test GameTest baseline are green, and the main agent re-ran `check` itself green before this mark; the drop/rise default awaits the viewer's pick, recorded in the decision record afterwards |
| SC10 | The flagship demo content (the viewer's "bigger and more complex — satellites, recursive sub-structures", direction delegated to the main agent): the `demo_station` variant group — a 13×11×13 relay station near the catalogue cap, ten stages, four satellite pods that are one generated module at four turns — the generator law (`tools/gen_demo_station.py`, deterministic, `--check` wired into `check`), and the `demo_campus` structure set as the in-world `/cumulus set build` exhibit (design 17 §11, D-111; brief `22-brief-sc10-flagship-demo.md`) | SC9 | The generator's `--check` green inside `check`; the document compile tests over the five shipped blueprints (the station's bay/stage counts, its 450–512 expected-cell band, its four pods byte-identical before orientation, a clean identity projection, each group's declaration repeat); the campus set's compile-and-validate test; the catalogue test names the new ids; uitest `demo` group opens the station's page; the other seven groups, `check` and `runGameTestServer` green | **pending** |


Order: SC0 first and alone; then SC1. SC2 and SC3 both need SC1 — SC2 builds the playback on the
scene, SC3 builds the links on the screen — and run one at a time (one worktree, one Gradle daemon,
one git index). SC4 needs SC2's playback and explode to have something to demo. SC6 needs SC5's
lighting and outline code to rework.

## Backlog (design 17 §14, no dates promised)

- **A Y slice in the screen** — the slice answers a world question (design 15 §5) and the screen
  answers the same question with four viewports; a fifth projection is not free.
- **A material list on the screen** — the JEI entry already lists materials (design 15 §6.2) and the
  stage summary line names the one item a stage asks for (design 17 §7.4).
- **Camera memory across opens, per blueprint** — every open is the default isometric frame; it would
  need a store the screen deliberately does not have.
- **Exporting a view as an image**, and any share or upload (design 16 §16's D.4).
- **A catalogue browser**: search, filter, a list panel, a per-document tree.
- **Returning to the JEI screen** on close, or restoring any previous screen.
- **A second renderer integration** (EMI or another): the scene core is the seam; a second face
  reopens design 15 §6.5's question.
- **Localisation** of the screen's vocabulary (design 16 §16's C.5).
- **A display mechanism for "a small entry looks empty"** — attributed to frame-all and to the demo
  content (design 17 §3.3, §14), not to a new layout rule.
- **The JEI page's legacy picking takes the farthest cell** — its pick ray resolves the maximum exit
  against a camera that sits on the opposite side (noted at SC1; the screen path takes the nearest,
  design 17 §5.3). Kept untouched at SC1 for zero regression; fixing it changes the JEI hover, so it
  gets its own milestone rather than riding along.

## Explicitly not done

- A session behind the screen, a server-side stage, camera or explode state, an S2C "open the screen"
  packet or a server-side `screen` command verb.
- A second scene renderer or JS-side tooling; a second client config file or a screen-specific key.
- Any world-space work for the screen — a `RenderLevelStageEvent` handler, a stage-number billboard, a
  screen overlay in the world.
- Opting the screen out of the pause to borrow the game tick.

## Open items for the main agent

- **OQ-1 (R5's third cause, resolved at SC0).** The "a pile of empty structures" complaint has three
  readings: air block leaves that pad the hull, an entry that is all air, and a small structure in a
  large page area. The first two are mechanisms with laws (design 17 §3.3, D-101); the third is
  attributed to frame-all and to the demo content and is not "fixed" by a rendering rule
  (design 17 §14). The doc records the uncertainty rather than claiming the complaint is fully
  explained.
- **OQ-2 (the roadmap's "collinear fallback", resolved at SC0).** The exploded view's direction is the
  zero case only: the fallback fires when a bay's centroid equals the structure's centroid, component
  by component, with no epsilon (design 17 §8.1, D-104). Two bays on opposite sides of the centre
  separate in opposite directions and are not a special case.
- **OQ-3 (the screen's clock, resolved at SC0).** A screen pauses the game, so the screen keeps a
  real-time tick accumulator and samples it; opting out of the pause was rejected (design 17 §4.6,
  D-103).
- **OQ-4 (the command's home, resolved at SC0).** `/cumulus preview screen <id>` is a client command,
  so the pack adds no S2C packet and no permission model for a window; the one shadowing consequence
  (a blueprint whose unqualified id is literally `screen`) is recorded in design 17 §9.2 and D-101.
