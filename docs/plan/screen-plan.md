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
| SC0 | Design revision: [design 17](../design/17-preview-screen.md) (the whole roadmap), D-101…D-105, adapter §20, this plan, the README status, and the two-sentence correction of design 15 §6.1/§6.2 | — | `tools/spec-check.py` clean (both the name-level and the code/producer halves); `./gradlew compileJava -q` green; main-agent line review; `docs/README.md` agrees with this file | **pending** — the design landed with this commit, and the main agent's line review closes it |
| SC1 | The screen skeleton: the package migration to `mc.preview.scene`, `PreviewScreen`, the 45° orthographic default and the projection toggle, the main viewport plus three linked ones, the axis gizmo, the R11 camera, and the drawn-hull fit (R5) in both consumers (design 17 §3, §4, §5, §6, §10) | SC0 | Camera, projection, hull, layout and explode math unit tests (deterministic, no client); uitest `screen` group (open the page, four viewports, an orbit and a zoom assertion, the axes present, the key table's uniqueness and no world binding firing); the existing `preview`/`jei`/`stage` groups not regressed; `check` green | **pending** |
| SC2 | Playback and the exploded view: the whole view, play/pause/step/jump, the delta treatments, the summary line, the explode tween with bay labels, the per-stage framing (design 17 §7, §8) | SC1 | uitest (the play advances and stops, the delta's drawn count, the explode's offsets at t=0 and t=1, the labels present, a manual gesture disarms the framing); `check` green | **pending** |
| SC3 | The links: JEI's `open in screen`, `/cumulus preview screen <id>`, the `O` world key, ghost to world with the screen's heading, the in-screen variant switch (design 17 §9) | SC1 | uitest (one route per entry point, the variant switch keeping the camera, the open payload's heading reaching the session's cycle); `check` and `runGameTestServer` green | **pending** |
| SC4 | The demo content: the machine group (two members) and the tower in `src/main/resources`, the demo marking, the compile and catalogue tests, the count and documentation close-out (design 17 §11) | SC2 | Document compile unit tests over the three shipped files; the catalogue test that all three ids reach the payload; the uitest `demo` scene gains the screen; `check` and `runGameTestServer` green; `git status` clean | **pending** |

Order: SC0 first and alone; then SC1. SC2 and SC3 both need SC1 — SC2 builds the playback on the
scene, SC3 builds the links on the screen — and run one at a time (one worktree, one Gradle daemon,
one git index). SC4 needs SC2's playback and explode to have something to demo.

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
