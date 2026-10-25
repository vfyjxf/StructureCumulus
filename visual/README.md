# StructureCumulus — Visual Specification Suite

An interactive, NASA-punk technical drawing set for the StructureCumulus design. Thirteen sheets
explain the document → object graph → execution → result chain, the 48-element orientation group,
the furnace's bay split, the predicate sandbox, the context fold, the eight-step pipeline, claims,
the five result arms, an end-to-end mission, a 3D structure bay and a 2D slice inspector. All
diagram data lives in `src/data/*.ts`; every panel is rendered from that data.

The design contract is [`design/nasa-punk-spec.md`](design/nasa-punk-spec.md). Square corners,
hairline/structural/heavy borders, uppercase tracked labels, tabular numerals, orthogonal
connectors, no shadows, no interpolated gradients. The global **PRINT / CRT** polarity switch
swaps the paper palette for the phosphor palette; the CRT scanline is disabled under
`prefers-reduced-motion`.

## Run

```bash
cd visual
pnpm install
pnpm dev      # local dev server
pnpm build    # type-check + production build to dist/
pnpm preview  # serve the production build
```

Only `react`, `react-dom`, `vite`, `@vitejs/plugin-react`, `typescript` and `@types/*` are used.
There is no router, UI kit, chart library, remote font, or runtime network request; fonts use the
spec's system fallback chain.

## Scenario playback

Every process diagram can replay the process that produced its final state. A demo is a named
**scenario** plus a **step**; both live in the URL, so any frame is linkable and screenshotable.

```text
?page=context&scenario=refused-middle&step=2&lang=zh
?page=claims&scenario=exclusive-conflict&step=1
?page=space&scenario=spin&step=12
```

| Parameter | Meaning |
| --- | --- |
| `page` | Sheet id (`overview` … `glossary`). |
| `scenario` | Scenario id for that sheet. Unknown ids fall back to the sheet default. |
| `step` | Zero-based step index. Clamped to the scenario's range on load. |
| `lang` | `zh` (default) or `en`. Switching language keeps `scenario` and `step`. |
| `theme` | `print` (default) or `crt`; preserved when the URL is rewritten. |
| `explain` | Explain entry id; opens the drawer on load (e.g. `?explain=sc-ctx-0108`). |

Changing sheet clears `scenario` and `step`; changing language does not. The shared transport is
[`src/components/Playback.tsx`](src/components/Playback.tsx) (`usePlayback`, `PlaybackBar`); URL
state lives in [`src/components/ScenarioPicker.tsx`](src/components/ScenarioPicker.tsx)
(`useScenarioState`, `ScenarioPicker`). Typed scenario and step data live in `src/data/*.ts`.

Transport: play / pause, previous / next, `NN/NN`, and 0.5× / 1× / 2×. Timing is
`requestAnimationFrame`-driven and cancelled on unmount. Nothing autoplays on entry; Space starts
its 48-step cycle after a scenario is chosen, and a deep link carrying an explicit `step` starts
paused so the linked frame stays put. Under `prefers-reduced-motion: reduce` every demo starts
paused, keeps all step controls, and renders the final geometry without travel. Motion uses only
the shared tokens `--np-dur-fast` (120 ms), `--np-dur` (200 ms) and `--np-ease`; no spring, bounce,
glow or gradient was added.

### Scenarios by sheet

| Sheet | Scenario id | What it plays | Steps |
| --- | --- | --- | --- |
| Overview | `trace` | A campfire check crosses document → object graph → execution → result; each stop names the object and what it carries. | 4 |
| Overview | `failure` | The same route with an unlit campfire; the token ends on `Failed`. | 4 |
| Space | `spin` | Auto-cycles all 48 orientations at 600 ms per step; hovering or focusing the elevation holds the frame. | 48 |
| Space | `satisfying` | Visits only the eight ordinals that satisfy `candidate.apply(authoredFront) == anchorHeading`. | 8 |
| Authoring | `lowering` | Highlights `bounds → Cuboid`, `bays[] → Bay`, `checks[] → Check`, `context[] → ContextKey` and pulses the created node. | 4 |
| Authoring | `anchors` | `anchors[0] → AnchorSpec`, `orientation → Siting`, then the whole `Blueprint`. | 3 |
| Anatomy | `giant-check` | Accrues four contradictions (subject type, stage, ownership, chunking), then splits the elevation into three bays. | 5 |
| Anatomy | `legal-split` | Lights wall, mouth and interior one at a time with the coverage, scope and stage each can carry. | 3 |
| Predicates | `match` | Focus → subject found → combinator children in order → `MATCH`. | 3 + children |
| Predicates | `mismatch` | The same tree with stone bricks; the leaf is decisive and `SC-EXP-0100` follows. | 3 + children |
| Predicates | `refused` | A refused section makes `focus.take` return Missing, so the reply folds to `UNREADABLE`. | 3 + children |
| Context | `standard` | Three contributions fold by `SectionKey (x, z, y)`, then the tier frames light in order. | 8 |
| Context | `refused-middle` | The second canonical section is refused; the fold aborts to `Missing` and the run stops. | 3 |
| Context | `whenAbsent` | Every section is readable and none contributes: volume stays `Missing`, heat honours `whenAbsent = 0`. | 5 |
| Pipeline | `formed` | The happy path through all eight steps to `Formed`. | 8 |
| Pipeline | `refused-section` | `SC-FOC-0301` stops at Judge with `Undecided(UNREADABLE)`. | 8 (stops at 06) |
| Pipeline | `budget` | `SC-ENG-0001` stops at Aggregate with `Undecided(BUDGET_EXHAUSTED)`. | 8 (stops at 05) |
| Pipeline | `conflict` | `SC-CLM-0002` fails at Settle with `Failed`. | 8 (stops at 08) |
| Claims | `exclusive-conflict` | Rows written → matrix compared → `ClaimConflict`. | 3 |
| Claims | `shared` | The same overlap with both rows SHARED settles. | 3 |
| Claims | `reserved` | RESERVED blocks the EXCLUSIVE incumbent. | 3 |
| Claims | `revision-moved` | No cell conflict, but revision 4 against ledger 5 gives `RevisionMoved` → `Undecided(LEDGER_MOVED)`. | 3 |
| Results | `arm-path` | Selects each arm and draws the orthogonal transition from the previous arm. | 5 |
| Results | `dirty-unstable` | Re-emits `Formed` at generation 1 → 2 with a byte-identical facts digest. | 2 |
| Results | `reorder` | Adds the five bundle sort keys one at a time; rows only highlight. | 6 |
| Mission | `formed` | Document → capture → PROBE → FORM contributions → fold → aggregate → publish → SETTLE → claims → `Formed`. | 15 |
| Mission | `refused` | A refused section makes the key `Missing` and the run stops at `Undecided(SC-FOC-0301)`. | 15 |
| Mission | `conflict` | Claims are planned, then the ledger returns `ClaimConflict` (`SC-CLM-0002`); no `Formed`. | 15 |
| Mission | `stale` | A dirty mark keeps the prior `Formed` and re-emits it at `generation + 1`. | 15 |

Claims also accepts a pointer drag of structure B on the 6×6 plane; the position snaps to whole
cells, the X / Z steppers remain the keyboard alternative, and conflict cells pulse once on drop
(pulse suppressed under reduced motion).

### Scenario data

Each page exports a typed `…_SCENARIOS` array plus its step records from `src/data/`:
`OVERVIEW_TRACE`, `SPACE_SCENARIOS`, `AUTHORING_LOWERING` / `AUTHORING_ANCHORS`,
`ANATOMY_GIANT_STEPS` / `ANATOMY_LEGAL_STEPS`, `PREDICATE_SCENARIOS`, `CONTEXT_SCENARIOS`,
`PIPELINE_SCENARIOS`, `MISSION_SCENARIOS`, `CLAIMS_SCENARIOS`, `RESULTS_SCENARIOS` / `REEMIT_STEPS`. Chinese copy for
those records is keyed by its English source in `COPY.zh` in
[`src/i18n/dict.ts`](src/i18n/dict.ts); transport and phase labels are in the `demo` section of the
same dictionary.

## Page map

| # | Sheet | Page | Interaction |
| --- | --- | --- | --- |
| 01 | `SC-OVR-001` | System overview | Click a layer band to highlight the concepts it owns and read their one-line definitions. |
| 01A | `SC-MSN-011` | End-to-end mission | Play 15 steps from document load to `Formed` and `RuntimeState`; scenarios inject a refused section, a claim conflict or a stale re-check. The 3D view follows the active bay. |
| 02 | `SC-SPC-002` | Space & orientation | Turn / tilt / parity controls generate one of the 48 orientations; the readout shows the ordinal, the transformed marker cell, the heading map, and the siting check `candidate.apply(authoredFront) == anchorHeading`. |
| 03 | `SC-AUT-003` | Structure authoring | Hover or click a JSON field to trace it to the runtime object it creates; click an object-graph node to jump back. Toggle compact vs canonical `expect` form. |
| 04 | `SC-BAY-004` | Bay anatomy | Select wall / mouth / interior; toggle **One giant Check** to overlay the contradiction and reveal the four reasons it cannot work. |
| 04A | `SC-VOX-012` | 3D structure bay | Drag to orbit, wheel to zoom, Y-layer clip, cutaway, bay toggles, and kind / bay / claim overlays. Hover a voxel for its coordinate, bay, stage and claim; click to open the explain drawer. |
| 04B | `SC-SLC-013` | 2D slice inspector | Switch X / Y / Z and the slice index; hover or click a cell for its occupant, bay, claim and section; compare a second 7×6×7 placing at (+2, +1) and see the conflicting cells. |
| 05 | `SC-PRD-005` | Predicate sandbox | Choose a focus and an expectation, edit the declared subject `T` (a mismatch raises `SC-CHK-0101`), add `allOf` / `anyOf` / `not` children, change the specimen, and read the simulated four-state `Reply`. |
| 06 | `SC-CTX-006` | Context dataflow | Edit section contributions or refuse a section; step the `(x, z, y)` fold; switch the reader tier and watch values appear at or below it. A refused section is `Missing`, never 0. The empty-region toggle exercises `whenAbsent`. |
| 07 | `SC-ENG-007` | Pipeline | Select PROBE / FORM / SETTLE and inject refusal, budget, stale publish, mismatch or claim conflict; the stepper and verdict show the resulting `Admitted` / `Formed` / `Failed` / `Undecided` with its code. |
| 08 | `SC-CLM-008` | Claims | Place two 3×3 structures on a 6×6 plane, choose mode (EXCLUSIVE / SHARED / RESERVED) and scope (Owned / Occupied), settle, bump the ledger revision, and read `Settled`, `ClaimConflict` (`SC-CLM-0002`) or `RevisionMoved` / `Undecided(LEDGER_MOVED)`. |
| 09 | `SC-RES-009` | Results & diagnostics | Click a result arm for its fields and production rule; select one of the nine Finding components; step the five bundle sort keys to re-order eight findings. |
| 10 | `SC-GLO-010` | Glossary | Search and filter 48 terms, each with what it is and why it exists. |

Deep links use `?page=<id>` (e.g. `?page=claims`); tab selection is also stored in the URL.

## 3D, 2D and explain

The 3D sheet (`?page=voxel`) is a pure-JS projection of the 294-voxel furnace: no WebGL, no
`three.js`. Drag orbits, the wheel zooms, the Y slider clips layers, and the cutaway removes the
near quadrant so the cavity is visible. The overlay switch recolours by occupant kind, bay, or
claim; hovering reads a cell and clicking opens the explain drawer. Data comes from
[`src/data/voxel.ts`](src/data/voxel.ts) (`VOXELS`, `BAY_META`, `VOXEL_COUNTS`).

The 2D sheet (`?page=slice`) renders one X / Y / Z plane as a grid with air hatching, bay colours,
and the mouth. `Compare B` offsets a second 7×6×7 placing by (+2, +1) and outlines the cells both
structures would claim.

The explain layer is a right-side drawer over the sheet: 40 entries in
[`src/data/explain.ts`](src/data/explain.ts), each with `what`, `why`, the normative `rule`, a
`source` citation into `22`/the guide, and `related` links. It opens from the masthead `?` button,
from any explainable element, from a 3D/2D cell, and from the mission's “explain this frame”
button. `?explain=<id>` pins an entry on load, e.g. `?explain=sc-ctx-0108`.

## Language

The suite ships in Chinese and English. Chinese is the default; the masthead carries a
**中文 / EN** switch next to the palette polarity, and the choice is mirrored in the URL so a deep
link can pin it: `?page=context&lang=zh`, `?page=context&lang=en`. Switching language keeps the
`?page=` value, the selected tab and every in-page control state (selected bay, pipeline stage,
claim mode, predicate tree); only the copy changes.

All UI strings live in [`src/i18n/dict.ts`](src/i18n/dict.ts): chrome, tab titles and page
subtitles, then one section per page id (`overview`, `space`, `authoring`, `anatomy`, `predicates`,
`context`, `pipeline`, `claims`, `results`, `glossary`), with `zh` and `en` kept structurally
identical. Long prose that belongs to the data files (`src/data/*.ts` — layer questions, concept
one-liners, step explanations, glossary bodies, finding messages) is translated in the same file
under `COPY.zh`, keyed by its English source string, so the data files stay the single structural
source. `LangProvider` exposes `useLang()` (`lang`, `setLang`, `c()` for that data copy) and
`useT()` (the page dictionary for the active language).

Identifiers stay in English in both languages: type and field names (`ContextKey`, `ClaimScope`),
enum values (`MATCH`, `ABSTAIN`, `EXCLUSIVE`, `Owned`, `SETTLE`), codes (`SC-CTX-0108`,
`SC-CHK-0106`), bay ids (`wall`, `mouth`, `interior`), sheet values (`NTS`, `SC-ENG-007`) and every
diagram formula. Title-block labels are localized in Chinese mode (`图号`, `版本`, `比例`, `日期`,
`分区`); their values are not. Glossary cards show the Chinese term first and the English term second
in Chinese mode (`上下文键 ContextKey`), and the reverse in English mode.

## Notes on two deliberate readings

- **Tilt has six values, not four.** A 48-element orientation is `4 turns × 6 tilts × 2 parities`;
  `4 × 4 × 2` would only reach 32, so the control offers all six tilts from `Tilt` (UP, SOUTH,
  NORTH, WEST, EAST, DOWN — one per face-up direction), keeping the 4×6×2 decomposition intact.
- **The furnace cavity is `CUBIC + Owned`, not `PRESENCE + Owned`.** `PRESENCE` asserts no cells and
  is legal only with `Audit` or `Declared`; `PRESENCE + Owned` is `SC-CHK-0106`. The anatomy sheet
  shows the legal split and explains the rejected combination explicitly. The two-cell mouth is
  shown as the `SUBJECT + DoorView` variant the brief requested; its note points at the door's
  subject and footprint rule.
