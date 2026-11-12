# Visual client-test plan for StructureCumulus multiblocks

> Status: the harness and the HUD/highlight scenarios are implemented (V0–V4, `group:visual`);
> the preview and JEI scenario groups landed with the in-world preview (P3) and the JEI category
> (P4) — see §10. This plan borrows the client-test architecture from CloudLib-InworldUI /
> NimbusProjection and defines the HUD + world-highlight delivery for StructureCumulus multiblock
> scenarios.

## 1. Goal

Build a repeatable client-side visual test suite for StructureCumulus multiblock scenarios. A run
must:

- place and drive real structures through the normal server/command path;
- show all diagnostic information in an **in-game HUD** and **world-space highlights**, never as
  chat/panel chat output;
- let a scenario assert the HUD model and the highlight set, and export screenshots/timelines;
- keep harness code and test fixtures out of the production jar.

Non-goals for the first pass: multiplayer visual sync, production player-facing HUD polish,
golden-image CI on arbitrary displays, and replacing the existing server GameTests.

## 2. Reference architecture

### 2.1 Client scenario harness

CloudLib-InworldUI ships a dev-only harness mod, `cloudlibuitest`:

- scenarios are annotated classes (`@ClientScenario`) with a `define(ScenarioBuilder)` body;
- the builder has world/server steps, input steps, assertions, captures, timelines and teardown;
- `runClientTests` stages the harness mod into a dedicated `runs/client-tests/mods` folder and
  boots a client with `-PclTest`; the report/screenshots land under `build/cloudlib-uitest`;
- `ScenarioBuilder` exposes `screenshot`, `screenshotSurface`, `screenshotSequence`,
  `timelineRecord`, `checkCaptured`, `assertTree`, `check*` and `serverTicks`/`waitUntil`;
- scenarios run in a loaded world, so `setBlock`/`fill`/`runCommand`/`serverTicks` can set up real
  multiblocks and then wait for the client.

Reference:
`ProjectDir/CloudMods-Inworld/CloudLib-InworldUI/docs/kb-uitest/{02,04,05,07}-*.md`.

### 2.2 In-world rendering and HUD

CloudLib's in-world UI stack provides:

- `WorldUiPanel` (logical canvas + per-frame `Placer` + `Painter`, optional line emitter);
- `WorldOverlay` for non-panel geometry in the same far-to-near pass;
- `Projection`, `QuadBasis`, `OffscreenProjector`, `OcclusionProbe` for world↔screen work;
- `WorldUiRenderer.get().overlays()` / `panels()` for per-frame registration;
- `TraceScene`/`Tracer` and Nimbus's `SelectionBrackets`/`ScanFrames` as working examples of
  selection brackets and block/entity scan frames.

For the HUD, CloudLib's `Scene`/`SceneCanvas` and widgets can be rendered from a NeoForge
`RegisterGuiLayersEvent` layer (the same bus CloudLib uses for `VanillaHudSampler`). This is the
HUD path; it is not the chat path.

## 3. StructureCumulus integration

### 3.1 Build and source sets

Add a dev-only client-test composition modeled on Nimbus:

```
src/uitest/java        @ClientScenario classes + visual probes
src/uitestMod          structurecumulusuitest mods.toml
src/uitest/resources   fixture documents / lang / optional datapack
```

- Add `cloudlib` (implementation/localRuntime) and `cloudlib-uitest` (`uitestCompileOnly`) at the
  CloudLib version used by the reference tree (currently `0.1.11`).
- Publish CloudLib-InworldUI to mavenLocal first, or add a composite `includeBuild` with dependency
  substitution; the local m2 is currently empty.
- Add `runClientTests` / `galleryClient` runs and a `cloudlibUiTestMod` staging configuration copied
  from Nimbus's `gradle/cloudlib-uitest.gradle`.
- Add `verifyHarnessIsolation`/`verifyRunIsolation` gates so no `uitest` class or harness jar reaches
  the production jar or a normal client run.
- Keep the `uitest` source set out of `jar` and `check`'s production classpath; add
  `compileUitestJava` to `check` only.

### 3.2 Client visualization module

New client-only package `dev.vfyjxf.cumulus.client.visual` (gated by a dev system property and by
`Dist.CLIENT`):

- `VisualClientState` — latest immutable `VisualSnapshot`, selected instance/claim, HUD visibility;
- `VisualSnapshot` — instances, bounds, siting, context revision/spend/findings, claim rows,
  set membership/status, draft/invalidated/moved state;
- `VisualSync` — server→client payload carrying the snapshot on request and after lifecycle events;
- `VisualHud` — CloudLib `Scene`/widgets mounted on a `RegisterGuiLayersEvent` layer;
- `VisualHighlights` — `WorldOverlay`/`WorldUiPanel` registration from a client level-stage listener;
- `VisualProbe` — public static read-only snapshot API used by scenarios.

Server side:

- `VisualSnapshotService` builds a snapshot from `ServerState` (`StructureManager`, `SavedClaims`,
  set host state) on a client request and after manager/claim lifecycle events;
- a dev command `/cumulus visual` toggles HUD/selection/legend and requests a resync; it must not
  print diagnostics to chat. Test scenarios may call it only for setup.
- For the first pass, single-player integrated-server direct access is acceptable as a fallback; the
  payload path is the production-shaped design and should be preferred.

### 3.3 HUD

A screen-space HUD panel, not a chat panel. Suggested layout:

- top-right `StructureCumulus` card (CloudLib widgets):
  - world/owner, instance count, draft count, claim row count;
  - selected instance: id, siting/orientation, bounds, context revision/spend, finding codes;
  - set summary: member count, formed/rolled-back, shared budget used/limit;
  - last lifecycle event (broken/moved/invalidated/loaded/unloaded);
- bottom-left legend: colors for instance bounds, claim rows, set links, nested extents, conflicts;
- optional compact mode for N>32: counts + selected detail only.

No `sendSystemMessage`/chat output for any of this. The HUD reads `VisualClientState`, not chat.

### 3.4 World highlights

World-space highlights, in the shared far-to-near world pass:

- **structure bounds** — box outline per instance, selected box brighter/thicker;
- **claim cells** — translucent cell fills; exclusive vs shared color; conflicts red;
- **set members/links** — root→satellite connector strokes + member anchor markers;
- **context anchors** — small billboard panel at the siting origin with revision/spend/code;
- **nested extents** — frame boxes for child frames/entity windows;
- **draft/moved/invalidated** — amber / blue / gray states;
- **crosshair selection** — ray hit against bounds/claim cells, with HUD detail update.

Implementation: `WorldOverlay` for lines/boxes/brackets, `WorldUiPanel` for labels/anchors,
`Projection`/`QuadBasis` for placement; reuse CloudLib's `SelectionBrackets`/`ScanFrames` patterns
rather than drawing an unsorted side pass.

## 4. Scenario catalog

All scenarios use `@ClientScenario`, `hideGui=false`, `guiScale` pinned, no chat assertions, and
capture at least one `hud` + one `world` frame.

| Scenario | Setup | HUD / highlight assertion |
| --- | --- | --- |
| `visual.smoke` | one claimable 1-cell structure | HUD shows 1 instance/1 claim; bounds+cell highlight; screenshot |
| `visual.overlap` | 3 overlapping instances | HUD count 3; all 3 bounds/cells visible; break one → count 2, old claim gone |
| `visual.set` | root + 6 satellites, shared budget | HUD set card member count/budget; root→satellite links; claim volume |
| `visual.conflict` | two owners, exclusive overlap | conflict red cells; HUD conflict code/status; no false settle |
| `visual.move` | full/partial piston move | highlight follows new siting; old/new bounds; HUD event=moved/invalidated |
| `visual.context` | publish context + finding after settle | HUD revision/spend/code updates; anchor label |
| `visual.orientation` | rotated siting | world bounds correct; HUD orientation label; no local-frame drift |
| `visual.nested` | nested frames/entity windows/attachment | nested boxes/anchors; HUD nested counts |
| `visual.unload` | unload/load level | HUD empty after unload, claims persist; after load no ghost instances |
| `visual.large` | 16/64 instances | compact HUD counts; highlight culling; no frame-time blow-up |
| `visual.error` | missing blueprint/over-budget/conflict | HUD error/finding state; no chat output |
| `visual.hudToggle` | any fixture | HUD show/hide and legend; screenshots prove no chat dependency |

Scenario assertions use `VisualProbe` model checks and `checkCaptured`/`timelineRecord`; a visual
regression is a model/highlight mismatch, not a screenshot diff in the first pass.

## 5. Fixtures

Visual fixtures are dev-only documents/sets, built from the public kernel document APIs in
`src/uitest`:

- `visual:blast_furnace_set` — controller + two preheaters, face links;
- `visual:port_envelope_set` — controller + port satellites;
- `visual:logical_subnetwork_set` — controller + logical satellites;
- `visual:grid_64` — 64-member grid for the large scenario;
- `visual:conflict_pair` — two owners at the same cells.

Install them through a dev-only `VisualFixtureDocuments` source registered before the client-test
world starts (add a public dev hook on `ServerState` or a `VisualFixtureCommand` that installs the
source and reloads). Do not ship fixture documents in the production builtin source.

## 6. Harness orchestration

- `./gradlew runClientTests -PclTest=group:visual -PclTestGuiScale=2`
- `./gradlew runClientTests -PclTest=visual.set` for one scenario
- the `-PclTest` selector takes comma-separated terms, and each term is one of `all`/`*`, an exact
  scenario name, `tag:<tag>`, `group:<group>`, `mod:<modid>` or `regex:<expr>`; there is no glob, so
  `visual.*` matches nothing and `group:preview` (not `preview.*`) is how a whole group runs;
- output: `build/cloudlib-uitest/report.json`, `screenshots/visual.<name>/`,
  `timeline/visual.<name>.jsonl`
- a `visualGallery` run boots the same composition with no selection, so a human can walk the
  fixtures with HUD/highlights on;
- CI runs the suite under Xvfb, excludes goldens initially, and archives screenshots/timelines.

## 7. Milestones

1. **V0 — feasibility/integration**: publish/link CloudLib, add `uitest`/`uitestMod`, `runClientTests`,
   isolation gates, one `visual.smoke` scenario.
2. **V1 — HUD skeleton**: `VisualSnapshot`, sync, `VisualHud`, `VisualProbe`; HUD renders counts and
   selected instance; smoke scenario asserts it.
3. **V2 — highlights**: bounds, claim cells, selection; overlap/conflict scenarios; screenshots.
4. **V3 — set/context/lifecycle**: set links, context anchors, move/unload/nested scenarios.
5. **V4 — scale + CI**: large/64 scenario, gallery run, Xvfb CI, artifact publishing; optional goldens.

## 8. Acceptance gates

- `compileUitestJava` + `runClientTests -PclTest=visual.*` green;
- every scenario has `report.json` pass + at least one screenshot/timeline;
- HUD assertions prove no chat dependency;
- `verifyHarnessIsolation` and `verifyRunIsolation` green; production `check`/jar unchanged;
- visual suite does not require a server GameTest and does not add harness classes to the jar.

## 9. The preview scenario groups (landed with P3/P4)

The in-world preview and the JEI category ship their own scenario groups in `src/uitest`; they are
data-driven over the dev-only `structurecumulus:preview_probe` datapack document (never a production
builtin) and drive the production `PreviewService` through the dev command `/cumuluspreview`.

| Group | Scenarios | What they assert |
| --- | --- | --- |
| `preview` | `preview.open`, `preview.turn`, `preview.flip`, `preview.origin`, `preview.reset`, `preview.invalidated`, `preview.confirm` | the session opens with its overlay and ghosts, a turn/flip/origin/reset control re-syncs the pose, a world change inside the footprint invalidates the session (`SC-BLD-0008`), and a confirm runs the build path and leaves the session |
| `jei` | `jei.preview` | the category lists the merged blueprint ids, the scene draws its frames, and "ghost to world" opens the same in-world session |
| `stage` | `preview.stage` | a two-bay datapack document opens with its two stages in declaration order, the ghosts carry their stage index, the cell step and the playback advance the reveal counts, the arrow keys switch the cursor and fire the banner, and the progress and materials bars read the payload's numbers |

Run them with `./gradlew runClientTests -PclTest=group:preview`,
`./gradlew runClientTests -PclTest=group:jei` and `./gradlew runClientTests -PclTest=group:stage`
(the selector takes `group:`, not a glob); the
scenario-harness rules of §6 apply unchanged, and `verifyHarnessIsolation`/`verifyRunIsolation`
cover the harness staging.

## 10. Approved decisions

- **Harness dependency**: consume CloudLib-InworldUI's official `cloudlib-uitest` harness as a
  development prerequisite; do not build a parallel harness. CloudLib's widget/scene stack is also
  the HUD implementation.
- **HUD ownership**: dev-only visual test mod; no player-facing HUD in the production jar for now.
- **Data path**: server→client payload snapshot; integrated-server direct access may be a temporary
  fallback only.
- **Fixture installation**: dev-only visual fixture source/hook, not production builtin documents.
- **Baselines**: model/highlight assertions + screenshot/timeline artifacts first; golden images
  optional later.
- **Scenario depth**: all 12 scenarios, phased V0–V4; first checkpoint is V0 smoke.
