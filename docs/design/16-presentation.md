# 16. Presentation: stages, playback, placement feedback, view modes and guided build

> Status: approved, design first (E0, 2026-11-12). It specifies the presentation pack on top of the
> preview of [15](15-preview.md) and the build of [14](14-auto-build.md), and lands with milestones
> E1–E5 of [`docs/plan/presentation-plan.md`](../plan/presentation-plan.md). The rulings are D-93…D-97;
> the document members are §3 (D-94). Nothing here is implemented yet: a milestone's gate is its
> statement of done, and no section of this file is written as if it had landed. A "roadmap" citation
> names the source plan's roadmap,
> `Data/StructureCumulus/plan-2026-11-12-presentation/00-roadmap.md`, and the item tags it uses
> (A.1, B.3, E.2, G.3 …) are that document's.
>
> The pack adds **no capability**: every world write, claim, plan and finding stays where design 14
> and 15 put it. It adds comprehension — a build with a middle, a before, an after and a reason.

## 1. Problem

The preview and the build are correct and silent. The ghosts appear in one frame, so a 40-block
tower and a 4-cell port look the same until the counter moves; a confirm happens in a single tick, so
"the world changed" has no visible cause; and the document's own description of a part — the one text
its author wrote for a human — never reaches a human, because the kernel carries it and no surface
reads it. A player who places a structure by hand gets a counter and no plan.

Four questions the pack answers, and nothing else:

- **Where am I in this build?** A stage model derived from the declaration, a progress bar, a stage
  banner (§4, §7, §8).
- **What did that confirm do?** A placement animation driven by the run's own report (§9).
- **What is this part, and how do I place it?** The document's description and hint, displayed at
  named points (§10).
- **How do I read this shape without flying around it?** Three view modes in the JEI preview (§11),
  and a guided mode that walks a player through the stages by hand (§12).

## 2. Position

- New code lands in the packages design 15 already owns: `mc.preview` (server: the stage model and
  the payload facts), `mc.preview.client` (playback, layering, HUD, banner, placement animation,
  guide), `mc.preview.jei` (view modes). No new package, no new module edge, no new kernel package.
- The kernel change is exactly one thing: presentation text carried as data (§3, D-94). It adds no
  type of its own, no code, no verdict and no decision input.
- Three iron laws, restated from D-93 and D-95 because every section depends on them:
  1. **Every animation, playback and banner is client presentation**: zero world writes, zero server
     state, zero kernel logic moved to the client.
  2. **Every order that reaches the screen is either declaration-derived or carried on the wire.**
     The stage table is a pure function of the blueprint; the play orders are payload fields; the
     client never sorts, re-derives or re-ranks.
  3. **The client renders and inputs.** It never re-derives a stage, a candidate, a score, a mark or
     a plan (15 §2 stands unchanged).
- What the pack may not touch: the executor flow (14 §10), the transaction (14 §7), the report
  (14 §8), the policy (14 §9), the code families (14 §14, 15 §10), the payload cap law (15 §3), and
  the session's one write exit (15 §2). A presentation feature that needs one of these changed is a
  defect in this document, not a licence.

## 3. The document's presentation members (C.1, D-94)

Four optional string members carry human text. They are data: the kernel copies them and never reads
their content.

| Member | Where | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `description` | root | string | absent | what the blueprint is; the preview title and the JEI entry lead with it |
| `description` | bay | string | absent | what the part is; the stage banner's second line |
| `hint` | bay | string | absent | how to place the part; the HUD's stage line and the JEI part list |
| `description` | check | string | absent | carried and **not displayed** by this pack (§10); a check is not a part |

`schemaVersion` does not move: every member is optional, absent means none, and the writer's canonical
form is unchanged (an absent member is not emitted, so the parse/write fixed point holds).

```json
{
  "schemaVersion": 1,
  "id": "structurecumulus:furnace",
  "description": "The iron furnace: hearth first, then the stack.",
  "bounds": [0, 0, 0, 3, 4, 3],
  "bays": [
    {
      "id": "hearth",
      "box": [0, 0, 0, 3, 1, 3],
      "coverage": "every_cell",
      "footprint": "none",
      "sharing": "shared",
      "description": "The firebox",
      "hint": "Lay the firebrick ring before the corners."
    },
    {
      "id": "stack",
      "box": [1, 1, 1, 1, 3, 1],
      "coverage": "every_cell",
      "footprint": "none",
      "sharing": "shared",
      "description": "The chimney",
      "hint": "Place it after the hearth is complete."
    }
  ],
  "checks": [
    {
      "id": "hearth_check",
      "bay": "hearth",
      "focus": {"kind": "bay"},
      "expect": {"kind": "each_cell", "element": {"kind": "block", "id": "minecraft:stone_bricks"}}
    },
    {
      "id": "stack_check",
      "bay": "stack",
      "focus": {"kind": "bay"},
      "expect": {"kind": "each_cell", "element": {"kind": "block", "id": "minecraft:iron_bars"}}
    }
  ]
}
```

### 3.1 The shapes

The module files document these components when the code lands (E3, same commit): documenting a
component before the code exists fails `SpecIndexTest`, so this section is the shape's home until
then.

```java
// document: the data form
public record BlueprintSpec(NamespacedId id, Cuboid bounds, boolean envelope, boolean envelopeEntities,
                            Value arguments,
                            java.util.List<BaySpec> bays,
                            java.util.List<SeamSpec> seams,
                            java.util.List<CheckSpec> checks,
                            java.util.List<ComputationSpec> computations,
                            java.util.List<KeySpec> keys,
                            java.util.List<DerivationSpec> derivations,
                            java.util.List<AnchorSpec> anchors,
                            @Nullable OrientationPolicy orientation,
                            java.util.List<PlacementSpec> placements,
                            java.util.Map<String, Value> extensions,
                            @Nullable String description,   // inserted here, before the span
                            @Nullable SourceSpan source) { }

public record BaySpec(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                      Sharing sharing, @Nullable String description, @Nullable String hint,
                      @Nullable SourceSpan source) { }

// structure: the compiled value carries them too, and its builder copies them
public record Bay(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                  Sharing sharing, @Nullable String description, @Nullable String hint) { }

public final class BlueprintBuilder {
    public BlueprintBuilder description(String description);   // null clears
}

// Blueprint gains `public @Nullable String description()`
```

### 3.2 The carry law

- **Type only.** The reader accepts a string and rejects any other JSON type with `SC-DOC-0002`, the
  existing bad-field law. The compiler copies the text; it never parses, trims, formats, defaults,
  localizes or length-limits it.
- **Nothing depends on it.** No finding, no code entry, no check verdict, no remedy applicability, no
  plan step and no ranking reads these strings. A host that disagrees can ignore them entirely
  (D-94).
- **It is part of the value.** The metadata participates in `Blueprint`'s equality and hash, exactly
  as it participates in the records' equality. Two documents that differ only in a description
  compile to two different blueprints, so a cache keyed on blueprint equality (the catalog's change
  detection, the determinism gate) sees the edit instead of silently keeping the old text.
- **A definition carries no root description.** The root-shaped members of a definition are already
  `SC-DOC-0002` (`document` §4.2, the geometry law); `description` joins that list. A bay's
  description and hint are not root members — they travel with the bay through placement
  qualification, so a placed part keeps its own text.
- **Absent is absence.** No default sentence, no placeholder, no fallback to the id where a surface
  would show text: a surface that has no text omits its line (§10).

## 4. The stage model (A.1, D-93)

A **stage** is a presentation grouping of the blueprint's expected cells. It is not a plan step, not
a capture phase, not a check and not a claim: a stage exists for every expected cell of every
blueprint before any solve runs, while a plan exists only for what is missing after one.

### 4.1 The derivation law

A pure, total function of the compiled `Blueprint` (`StageModel`):

1. **The partition.** The cells are the expected cell set the preview draws as ghosts (the
   declaration-derived expected states at a siting, 15 §3). Every cell belongs to exactly one stage.
2. **A cell's stage is the bay that asked for it.** Each expected cell is produced by a check, and a
   check names a bay; the cell belongs to that bay's stage. The assignment is provenance, not
   geometry: a cell expected by a check of the port bay is the port's even when an earlier-declared
   body bay contains the same coordinates, so a strip inside a body keeps its own part. A cell
   expected by several checks belongs to the earliest of their bays in the bay order below.
3. **Bay mode — two or more bays:** the stages are the blueprint's bays in the order
   `Blueprint.bays()` presents them, filtered to the bays some expected cell belongs to. That order is
   the compiler's: the bays of the check declarations in check order, with the derived envelope bay
   appended last (D-30) — so a document's bay order is its first check's order, never an
   alphabetical or geometric sort.
4. **Layer mode — exactly one bay:** the stages are that bay's **distinct definition-frame Y values,
   ascending** (bottom-up), over the bay's expected cells. A cell's stage is the rank of its Y, so a
   hollow tower with gaps has one stage per occupied layer, not one per unit of height.
5. **Degenerate:** one non-empty stage is one stage (the partition collapses).
6. **Only stages with cells exist.** There is no synthesized stage and no catch-all: every expected
   cell has a producing check, so every cell has a bay, and a bay no expectation belongs to
   contributes nothing — a stage with nothing to place would announce itself, fill a progress
   segment and teach nothing. A blueprint that expects no state at all has an **empty table**, and
   every stage surface is then omitted rather than reading `0/0` (§6.1, §7.1, §12.3).
7. **Names.** Bay mode: the bay id's `text()` (`structurecumulus:hearth`). Layer mode: `layer <n>`,
   n the 1-based rank. Names are server-side strings on the wire, not client-side formatting.
8. **Cap.** `maxStages` is 256 (a code constant). An overflow keeps the first 256 in order and adds
   one WARNING marker naming the shown and expected counts — 15 §3's no-silent-truncation law.
9. **The table.** For each stage the wire carries its name, its expected cell count and its bay's
   carried hint (§5). Indices are 0-based on the wire and 1-based in every text.
10. **The bay of a cell is decided before the orientation.** The provenance walk runs in the
    definition frame — the same declaration walk that produces the expected states, before the
    orientation transform is applied to them (D-91's port) — so a pose changes a cell's state, never
    its bay. The ghost list and the table therefore come from one walk and cannot disagree.

### 4.2 Worked examples

| Blueprint | Bays (`Blueprint.bays()`) | Stages |
| --- | --- | --- |
| the sample `build_stone_pair` (one bay `cells`, one layer) | 1 | 1: `structurecumulus:cells` |
| a furnace whose checks name `hearth` first and `stack` second | 2 | 2: `…:hearth`, `…:stack` |
| a 3-layer tower, one bay | 1 | 3: `layer 1`, `layer 2`, `layer 3` |
| a tower whose middle layer holds no expected cells | 1 | 2: `layer 1`, `layer 2` (the empty rank is dropped) |
| a two-bay blueprint with one bay that expects nothing | 2 | 1: the non-empty bay (one stage) |
| a body bay and a port strip inside it, each with its own check | 2 | 2: `body`, then `port` — the strip's cells stay in the port stage although the body's box contains them |
| an envelope root with one declared bay (D-30's derived bay) | 2 | 2: the declared bay, then `structurecumulus:envelope` — its bay description is the builder's own sentence, so its banner line is a full sentence (§8) |

### 4.3 Determinism (roadmap G.3)

- **The function reads no world, no siting, no clock and no hash-ordered collection.** Its inputs are
  the compiled bay list (`Blueprint.bays()`, §4.1.3) and each bay's cell set, walked in the kernel's
  canonical cell order (`LatticePoint.compareTo`: Y, then Z, then X). Equal blueprints produce equal
  tables; two calls produce equal tables.
- **A scrambled input produces the same assignment.** Each cell's stage comes from its producing check
  and the tie-break is the bay order, so the order in which cells happen to arrive cannot change a
  stage index.
- **The table is siting-invariant.** A rotation, a flip, an origin move or a candidate change moves
  world positions, never a cell's stage: the layer axis is the *definition* frame. This is why a
  session computes the table once and why a re-sync cannot change it (§5).
- **The layer axis is not the world-Y slice.** A stage is a declaration order; the slice (15 §5,
  15 §6.2) is a viewing filter in world Y. They compose (§6.5) and are never substituted for each
  other.

## 5. The payload extension (A.1 wire facts, D-93, D-95)

```java
package dev.vfyjxf.cumulus.mc.preview.net;

// E1: the session payload carries the presentation block; the shapes below are the wire contract.
public record PreviewSyncPayload(NamespacedId blueprintId, java.util.List<SitingCandidate> candidates,
                                 int current, OverlayModel overlay,
                                 java.util.List<GhostCell> ghostCells,
                                 java.util.List<Marker> markers,
                                 PreviewPresentation presentation) { }

// One presentation block: the blueprint's own text, its stage table in index order, the stage the
// world is working on right now (-1 when the table is empty), and the plan's shortage lines.
public record PreviewPresentation(@Nullable String description, java.util.List<Stage> stages,
                                  int currentStage, java.util.List<MaterialStatus> materials,
                                  int moreMaterials) { }

// One stage: its display name, its expected cell count, how many of them the world already holds, the
// bay's carried hint (E3) and the bay's carried description (the banner's second line, §8).
public record Stage(String name, int expectedCells, int satisfiedCells,
                    @Nullable String hint, @Nullable String description) { }

// One item the open plan still lacks: what it asks for and what the item source can give (§7.3).
public record MaterialStatus(NamespacedId item, int need, int have) { }

public record GhostCell(LatticePoint cell, NamespacedId block,
                        java.util.Map<String, String> properties, int stageIndex) { }
```

- **The shape lands once, at E1.** `Stage` carries both text members from the first payload: its
  `description` is filled from the bay the stage derives from, because §8's banner shows that line
  from E1; its `hint` stays null until E3 fills it, and `PreviewPresentation.description` (the root
  text) stays null the same way. E3 fills values and never changes this shape.
- **`satisfiedCells` is what the segmented bar fills by.** §7.2 fills each segment by its stage's
  satisfied fraction, and the client holds no stage index for a marked cell that draws no ghost
  (`present`, `wrong`, `blocked`), so the counting happens where the marks are: the server.
- **`materials` is the third tier's source.** §7.3 draws one line per shortage with its `have/need`;
  the dry run's item simulation is server-only, so the lines travel (at most three, the rest counted
  in `moreMaterials`) instead of being re-simulated or parsed out of a marker's text.
- **`currentStage` is the frontier, computed by the server.** The lowest stage index with an
  unsatisfied expected cell, or the last stage when every cell is satisfied; `-1` when the table is
  empty (§6.1). The server computes it from the marks it ships in the same payload, so the client
  derives nothing (§15). It is A.3's "already built + buildable now" stage, and it is what the
  banner announces when it moves (§8, §12.5).
- **The catalog entry carries the same records.** `PreviewCatalogPayload.Entry` ships the same
  `PreviewPresentation`, the same `Stage` and the same `GhostCell`, because one cell type must mean
  one thing (§11's tooltips and stage grouping read it). A catalog is not a session: there is no live world under it,
  so the same frontier definition yields `0` whenever it has a stage (nothing is satisfied in a page),
  and its table is the identity-orientation table of §5's derivation.
- **`ghostCells` are emitted ordered by `(stageIndex, definition-frame canonical cell order)`**, and
  that list order **is** the play order (§6): the client consumes the order, it never sorts. The
  server is the only side that knows the definition frame, so the order belongs on the wire.
- **Every `stageIndex` names an existing stage**, 0-based, non-decreasing along the list. A cell
  without a stage cannot exist: every expected cell has a producing check with a bay (§4.1.2,
  §4.1.10), and every bay a cell belongs to is in the table.
- **The stage table is not capped by the ghost cap.** `expectedCells` counts the overlay's expected
  cells, not the capped ghost list, so a payload that had to cap its render list (512, 15 §3) never
  moves a progress number. The ghost overflow still adds its WARNING marker.
- **The table is identical on every sync of one session**, because it is a function of the blueprint
  (§4.3) — a re-sync, an invalidation, a recall and a pose change all ship the same table. A client
  may therefore keep its cursor across a re-sync. The table may be **empty** (a blueprint that
  expects no state, §4.1.6): `stages` is then empty and `currentStage` is `-1`.
- **Rejected: a parallel index list** beside `ghostCells` — alignment would be a rule no type
  enforces. **Rejected: a sentinel index** (−1 for the catalog) — one field would carry two meanings
  and the catalog would ship a lie it never needs.
- **What stays server-side:** the table, the indices, the frontier, the marks, the candidates.
  **What is client-only:** the selection cursor, the playback cursor, the reveal set, the camera, the
  view mode and the banner (§6, §9, §11).

## 6. Playback and stage switching (A.2, A.3, A.5)

### 6.1 Three facts: the table, the frontier and the cursor

Per open session the client keeps: the presentation block it last received, `selected` (the view
cursor the emphasis follows) and a `reveal` count per stage (how many of that stage's cells the
playback has revealed). Nothing else. All three die with the session (15 §4's close, cancel, level
unload).

- **The table and the frontier arrive; they are not computed.** `presentation.stages` and
  `presentation.currentStage` come from the server (§5). The frontier is A.3's "already built +
  buildable now" stage, computed once per sync from the marks in the same payload — which is why the
  client derives nothing at all (§15, D-88).
- **The cursor is the client's.** `selected` is a view cursor like the slice index of 15 §5: it says
  which stage is emphasised, it is moved by `↑`/`↓`, and it is a render-side choice the server never
  needs. It is seeded from `currentStage`, clamped to `[0, stageCount)`, and never sent back.
- **Tracking.** A cursor that equalled the previous payload's `currentStage` follows a moving
  frontier — so a stage finished by hand advances the view. A cursor the player moved away stays put:
  the HUD keeps showing the stage the player is looking at, and the segment bar's frontier marker
  keeps showing where the world is.
- **Seeding.** On the first sync of a session, `selected` = `currentStage` and every stage before it
  counts as fully revealed. On later syncs, `selected` and the reveal counts are kept (subject to
  tracking and clamping); they are never reset by a re-sync, a recall or a pose change.
- A sync for a *different* blueprint id is a different session and re-seeds.
- An **empty table** (`stages` empty, `currentStage` -1) leaves no stage facts at all: no cursor, no
  stage line, no banner and no segments, and every ghost draws with the current-stage treatment
  (§6.2) because there is no relation to emphasise. The playback keys then do nothing except `V`,
  which plays the one implicit group in the wire order.

### 6.2 The reveal set and the four treatments

A cell is **revealed** when its stage index is below `selected`, or when its stage is `selected` and
its position within that stage is below the stage's reveal count, or when its overlay mark is
`present`. The treatment composes the stage relation (against the cursor `selected`) with the reveal
state (A.5). Alpha is a factor on the ghost's base alpha (15 §5); a box is the overlay frame (15 §5).

| Stage relation | Revealed | Ghost model | Box |
| --- | --- | --- | --- |
| before the cursor (done) | always | alpha × 0.25 | thin frame |
| the cursor's stage (current) | yes | alpha × 1.0 | normal frame |
| the cursor's stage (current) | no | alpha × 0.08 | none |
| after the cursor (future) | — | alpha × 0.08 | none |
| any stage, cell `present` | — | not drawn (15 §5) | per the mark |

The shape therefore stays faintly visible from the first frame: the pack emphasises progress, it does
not hide the blueprint.

### 6.3 Controls

| Key | Action | Milestone |
| --- | --- | --- |
| `↑` / `↓` | previous / next stage: moves the cursor and shows that stage whole; fires the banner (§8) | E1 |
| `V` | play / pause the staged playback | E1 |
| `B` | one cell step: reveals the next unrevealed cell of the cursor's stage; past its end, moves to the next stage and reveals its first cell | E1 |
| `G` | guided mode on / off (§12) | E5 |

The keys of 15 §5 are unchanged and keep their meanings: `[` `]` turn, `;` flip, `,` `.` the origin
cycle, `R` reset, `P` recall, `PgUp`/`PgDn` the Y slice, `T` all/problems, `Enter` confirm, `Esc`
cancel. `↑`/`↓`, `V`, `B` and `G` were all unbound in the mod; the uitest conflict check
(roadmap §5) asserts the combination rather than trusting this paragraph.

- **A key is a view move, never a control payload.** No stage key reaches the server, and no stage
  key changes a candidate, a mark or a plan.
- **Input is never locked.** Playback is a render-time state: the keys are read before the animation
  update, and every existing key — turn, flip, slice, confirm, cancel — responds during an
  animation (roadmap §5). An animation may not delay a packet, a plan or a write.
- **Playback never leaves the session.** Play/pause/step act on the cursor and the reveal counts;
  at the last stage's end, play stops with everything revealed.

### 6.4 Cadence

| Constant | Value | Meaning |
| --- | --- | --- |
| `cellRevealTicks` | 2 | the nominal delay between two cells of one stage at speed 1.0 |
| `stageRevealMinTicks` | 10 | the floor for revealing one stage (a 4-cell stage is still watchable) |
| `stageRevealMaxTicks` | 100 | the ceiling for one stage (5 s), so a huge stage never crawls |
| `stagePauseTicks` | 10 | the hold between a finished stage and the next one |
| `bannerHoldTicks` | 24 | the banner's full life (1.2 s) |
| `bannerFadeTicks` | 3 | the banner's fade in and out (150 ms each) |

- **A stage is revealed over a span, not a per-cell rate.** The span is
  `clamp(stageCells × cellRevealTicks, stageRevealMinTicks, stageRevealMaxTicks)` and the stage's
  cells are spread evenly across it, so a 4-cell stage takes 10 ticks, a 12-cell stage 24, and a
  400-cell stage the 100-tick ceiling (several cells per tick). Playback is for watching a build, so
  its total time is bounded by the number of stages, not by the number of cells.
- `animation.speed` (§13) divides every tick constant: the effective span is
  `ceil(span / speed)`. A speed change never changes a cap, a count or an order.
- `animation.enabled = false` (§13) removes the reveal span: a stage jump, a step and a play all
  reveal their cells at once, and the playback keys keep their meaning (they move the cursor and
  the reveal counts; there is simply no fade to watch).

### 6.5 Composition with the existing switches

- **The Y slice is a filter, the stage is an emphasis.** A cell outside the slice is not drawn at
  all, whatever its stage; inside the slice the stage factors apply. The slice wins because it is a
  spatial question and the stage is a temporal one.
- **Problems-only mode (15 §5) composes the same way**: it filters by mark, the stage filters by
  emphasis, and the two are orthogonal — an emphasised cell can still be filtered out by the mode.
- **Distance culling (15 §5) is unchanged**: counters and bars are never culled, ghosts and frames
  are.

## 7. The progress HUD (E.1, E.2, E.3)

Three tiers of information, of which the first is always drawn and the other two add detail. All
three read data the session already ships: the overlay marks, the presentation block (the stage table
and the frontier) and the dry run's item simulation (4.4's `BuildMaterials.shortages`). Nothing here
is estimated, sampled or invented.

### 7.1 Tier 1 — the total bar (always)

- **Data.** `satisfied / expected` where expected is the overlay's expected cell count and satisfied
  is its `present` count; the percentage is `floor(100 * satisfied / expected)`, and it reads 100%
  only when `satisfied == expected` (a rounded-up 100% would be a lie about a build).
- **Two narrations, one bar.** In a preview the counts are the dry-run result — what the world would
  have to change; after a confirm, an invalidation or a recall the session re-syncs and the same
  counts describe the live world. Nothing switches mode: the bar always describes the world the
  session last saw (roadmap E.1).
- **It carries the stage line** `stage i/n: name` from the cursor (`selected`), so the number and
  the stage read together (§10). The line is omitted when the stage table is empty (§4.1.6), never
  drawn as `0/0`.

### 7.2 Tier 2 — the segmented bar (preview and guided, multi-stage only)

- One segment per stage, left to right in index order, width proportional to that stage's
  `expectedCells` with a 1 px minimum and a 1 px gap, and filled by that stage's satisfied fraction.
- The cursor (`selected`) is outlined, the payload's `currentStage` is marked with a caret, and a
  segment that is fully satisfied is filled. With one stage the tier is not drawn — tier 1 already
  says everything it could.

### 7.3 Tier 3 — the materials bar (non-creative, shortage only)

- The lines of 4.4 keep their content (item icon, name, missing count) and gain their fraction
  `have/need` from the same simulation, plus one bar per line.
- At most three lines, then `+k more`; a creative source collapses the tier to nothing (4.4's law),
  and a satisfied plan shows no lines at all.
- **The lines are payload facts, not a client calculation.** The session reads them from the dry
  run's item simulation (`BuildMaterials.missing`) and ships at most three as `MaterialStatus`
  entries, counting the rest in `moreMaterials` (§5); a page has no item source, so a catalog entry's
  list is empty.

### 7.4 Layout and quiet compatibility

The three tiers live inside the existing corner panel (15 §5), directly under the title line: tier 1
3 px tall, tier 2 2 px tall, tier 3 one line per material. They are three independent draws, so a
future quiet HUD (P8 backlog 8.2) that hides the panel hides all three together; **this pack does not
implement quiet mode and depends on nothing from it**.

## 8. The stage banner (A.4)

- **What.** The cursor's stage name on one line, and — when that stage's bay carries a
  `description` — that text on a second line. A stage with no description draws one line. The banner says *where you
  are*; the HUD stage line says *what to do* (§10).
- **When.** On a cursor change (`↑`/`↓`, a step that advances a stage, playback advancing a stage)
  and on a frontier advance — the payload's `currentStage` moving ahead, which is the world finishing
  a stage. At most one banner exists; a new one replaces it in place (no queue and no stack), and the
  same stage cannot re-announce itself within `bannerHoldTicks`.
- **Where and how long.** Horizontally centred, vertically at 25% of the screen height — above the
  crosshair band and clear of the corner panel; `bannerHoldTicks` total, `bannerFadeTicks` fade in and
  out (roadmap §5). A description wider than the screen is wrapped at the panel's edge; the derived
  envelope bay's description is a full sentence (§4.2), and the banner must not clip it.
- **Off.** `hud.stageBanner = false` (§13) draws nothing; the HUD stage line stays, so the
  information is never lost with the banner.

## 9. Placement feedback (E2, B.1–B.4, D-95)

### 9.1 The commit payload

```java
package dev.vfyjxf.cumulus.mc.preview.net;

// E2: one committed transaction's changed cells, in the order the transaction performed them.
public record BuildCommitPayload(java.util.List<CommitCell> cells, Status status, boolean formed,
                                 int truncated) {

    public enum Status { committed, rolledBack, undone }

    // The state the cell holds now: the new state for a commit, the restored pre-image for a rollback.
    public record CommitCell(LatticePoint cell, NamespacedId block,
                             java.util.Map<String, String> properties) { }
}
```

- **The list is the play order.** The server emits the cells in the order the transaction performed
  them (plan order for a commit, restore order for a rollback, which is the reverse of the apply
  order); the animator plays the list front to back and never re-sorts. This is the second order of
  the pack and it is not the stage order: the stage table is declaration-derived, the play order is
  what the world actually did (§4.1, D-93).
- **Only changed cells.** The transaction knows which cells it wrote; a `skipped` cell (already at
  its target, 14 §7) is not a change and does not travel, so the animation can never claim a write
  that did not happen.
- **Three statuses.** `committed` (the run's writes), `rolledBack` (the cells a failed run restored)
  and `undone` (`/cumulus build undo`, 14 §11). A run that writes nothing — a refusal, a validation
  failure, an idempotent empty report — sends nothing: there is no cause to show.
- **`formed`** is true only when the run's own post-build re-check reported the structure formed (the
  `--settle` chain, 14 §10); it gates the celebration (§9.5).
- **Send sites.** One per write path, each to the invoking player only: the preview's confirm path,
  `/cumulus build` on a committed run, `/cumulus set build` (one payload per member, in commit order),
  and `/cumulus build undo`.
- **Cap.** `maxAnimatedCells` is 512 cells per payload. An overflow ships the first 512 in play
  order and sets `truncated` to the number left out; the client names the played and expected counts
  in one line on the animator's own overlay for `bannerHoldTicks` — the same no-silent-truncation law
  as 15 §3. That overlay must not require a preview session (a `/cumulus build` run has none) and must
  not put the note in chat (14 §12).
- **Dedicated server.** The payload is plain data; its registration and its handler resolve client
  classes only inside the enqueued work (15 §6.4, adapter §17). A server never animates anything.

### 9.2 Pop-in

| Constant | Value | Meaning |
| --- | --- | --- |
| `popInTicks` | 6 | one cell's own scale-and-fade duration |
| `popInStaggerTicks` | 2 | the nominal gap between consecutive cells |
| `popInSpanTicks` | 40 | the ceiling on the gaps of one run (2 s) |
| `fadeOutTicks` | 8 | one cell's fade-out on a rollback or an undo |

- One cell grows from 0.5 to 1.0 scale and fades from alpha 0 to the ghost base alpha over
  `popInTicks`, and the cells are staggered from the payload's order. The stagger is
  `min(popInStaggerTicks, popInSpanTicks / max(1, cells - 1))`, so a 12-cell run takes
  `11 × 2 + 6 = 28` ticks (1.4 s) and a 512-cell run plays all of its cells at once over 6 ticks
  instead of queueing for a minute. A run's animation is bounded by its span, never by its cell count.
- The animation draws an *extra* translucent copy of the model at the cell; it never re-colours the
  real block, never touches the world and never re-opens a session.
- Distance culling applies as it does to ghosts (15 §5): a run behind the player does not animate
  into the void. The bar and the counters never cull.

### 9.3 Sound

- One place sound per cell would be an assault: the animator plays at most one place sound per
  `soundCooldownTicks` = 4 ticks per player, using the changed cell's own block sound
  (`BlockState.getSoundType().getPlaceSound()`), at volume 0.5 and pitch `0.9 + 0.4 × fraction`,
  where `fraction` is the run's progress — so a long run rises and a small one stays flat.
- A rollback plays the block's break sound under the same rate limit and the same pitch rule; an
  undo sounds like a rollback because it is one.
- `animation.sound = false` (§13) silences both; nothing else changes.

### 9.4 Rollback and undo fade

- A `rolledBack` or `undone` payload fades its cells out in list order (restore order, so the visual
  is the reverse of the pop-in) over `fadeOutTicks`, staggered by the same span rule as the pop-in.
  The colour is the removal treatment, not the placement one — the point is causal visibility for
  "the world changed back" (roadmap §5, B.3).
- The fade draws over the restored state, which is why the payload carries the post-run state of each
  cell (§9.1): the animator has nothing to look up and nothing to guess.

### 9.5 The formed celebration

- When `status == committed && formed`, the client plays one particle burst at the structure's
  bounds centre (a ring of vanilla particles over a few ticks) and one vanilla level-up cue, once per
  run — the celebration marks the transition, it does not repeat.
- `animation.celebrate = false` (§13) turns it off. A run that is committed but not formed gets no
  celebration: "the world now matches the plan" and "the structure is formed" are different facts
  (14 §10), and the pack must not conflate them.

### 9.6 What an animation may never do

Change a session, a mark, a plan, a claim or a block; block an input; delay the server; be required
for correctness. With `animation.enabled = false` the world is exactly as right as with it on — the
pack narrates, it does not participate. Animations are per-player: v1 never broadcasts another
player's run (B.5 backlog), and a second player sees the world change instantly with no animation.

## 10. Part metadata display points (C.3, C.4)

The kernel carries the text (§3); these are the only surfaces that display it. A surface not in this
table displays none of it.

| Surface | Shows | Milestone |
| --- | --- | --- |
| preview HUD, title line | `preview: <id>` plus the root `description` when present | E3 |
| preview HUD, stage line | `stage i/n: name` plus the current stage's bay `hint` when present | E1 (name), E3 (hint) |
| stage banner (§8) | the stage name plus its bay `description` when present | E1 |
| JEI entry, tooltip and side list | the root `description`, and per stage its name, its count and its bay `hint` | E3 |
| JEI view tooltip (§11) | the cell's block id, its properties, and the stage (part) that owns it (the stage name is E1's data) | E4 |

- **The banner's second line works from E1**, because a bay's `description` is already carried by
  `Bay` and reached the compiled blueprint before this pack. E3 adds the root description and the
  bay hint; it does not re-route the bay description.
- **A check's `description` is not displayed.** A check is not a part; its text stays carried for a
  later surface, and inventing one here would put a check's label where a part's belongs.
- **`/cumulus explain` prints findings, never presentation text.** The explain chain is the
  document's diagnosis surface; a description has no code, no locus and no severity, and putting it
  there would blur the two.
- **Absent means the line is omitted** — no placeholder, no `description: <none>`, no fallback to the
  id in a text slot. Text is displayed verbatim: no wrapping rules of its own, no truncation marker;
  the panel wraps it like any other HUD line (15 §5).
- **No prose is composed anywhere.** What a surface shows is either a document string or a field of
  the payload; §12's next-step line is the one formatter, and its vocabulary is fixed.

## 11. JEI view modes (E4, D.1–D.3, D-96)

The category has three view modes. The mode is **view state of the recipe**, like the yaw and pitch
of 15 §6.2, not a field on the catalog entry: two players' pages never fight over it and no payload
carries it.

| Mode | What it draws |
| --- | --- |
| isometric (default) | the existing drag-rotatable 3D scene (15 §6.2) |
| three-view | three orthographic panels in one row: front, side, top |
| slice grid | one layer, top-down, as a 2D grid; the existing slider chooses the layer |

- **The switching control is a button in the category's own face** that names the current mode and
  cycles isometric → three-view → slice grid. No key is added: a global `KeyMapping` would fire
  outside the GUI, and JEI's own conventions are buttons and tooltips.
- **The three planes come from the front law of 15 §9**: front looks along +Z at the X/Y plane, side
  looks along +X at the Z/Y plane, top looks along −Y at the X/Z plane. Each panel is fitted to the
  blueprint's bounds by 15 §6.2's fit rule and labelled with its view name, so a 1×1×1 cell and a
  40-block tower both fill their panel.
- **One slice, all three panels.** The front and side panels highlight the current layer as a band;
  the top panel draws only that layer. The three panels then agree on one Y, which is what makes
  them a drafting triple rather than three pictures (D.3).
- **The grid is the top panel alone**, at full panel size, with the slider as its only layer control;
  a one-layer blueprint pins the slider (15 §6.2's rule).
- **Hover is per panel.** The cursor unprojects inside the hovered panel; the nearest visible cell
  whose box the ray crosses names its block id and property values (15 §6.2) and — from the same
  payload — the stage (part) that owns it, with the stage's hint at E3. The same cell may be hovered
  in two panels; each panel answers for itself.
- **No new caps.** A catalog entry is already capped at 512 expected cells or left out entirely
  (15 §6.1); the modes draw the entry they were given, and the whole entry is the ceiling.
- **Nothing is exported** (D.4 non-goal): the modes are read on screen, not written to a file.

## 12. Guided build (E5, F.1–F.4, D-97)

Guided mode is a **view of an open preview session**. It changes emphasis and text; it never writes,
never picks a siting and never becomes a builder.

### 12.1 Entry and exit

- **Enter** with `G` at any time while a session is open, and automatically by a confirm that leaves
  the plan in the session (14 §10's partial repair, 5.1) — after that confirm the user is expected to
  finish by hand, and the mode is a reversible view, so entering costs nothing.
- **Leave** with `G`, with `Esc` (which closes the session), or with the session's close: the mode is
  session-scoped and dies with it. A retraction keeps the mode armed — the session is alive, so the
  emphasis resumes on recall.
- **Unchanged by the mode:** the candidate cycle, the siting, the progress lock (15 §9, 1.5), the
  pins, the marks and the plan. Entering guided mode changes no server state at all (D-97).

### 12.2 What the mode changes on screen

- The current stage takes the emphasis treatment of §6.2; the next group — the current stage's
  unrevealed or unsatisfied cells, in the wire order — is highlighted as the next thing to place
  (15 §5's "next placeable group", extended to the cursor's stage).
- The next-step line (§12.3) and the hand pulse (§12.4) join the HUD. Tier 3 of the HUD keeps 4.4's
  law unchanged (non-creative, shortage only): the guide's material guidance is the item and count in
  the next-step line, which is useful in every game mode, while a shortage list in creative would be
  a list of zeroes.

### 12.3 The next-step line

One line, composed by a formatter with a fixed vocabulary — the *data* comes from the payload, the
*wording* is the formatter's literal English (C.5 later replaces the literals; no milestone adds
prose to the formatter):

```
<verb> <count>× <item> (stage <i>/<n>: <name>)
```

- **The verb** comes from the mark kind of the group's cells: `place` when every unsatisfied cell of
  the group is `missing`, `replace` when any of them is `wrong` — a wrong block must be broken first,
  and saying "place" there would misdirect the player.
- **The count and the item** come from the group's expected states (one material line per distinct
  required item; the largest group wins when a stage has several).
- **The stage reference** comes from the payload's stage table; **when the stage's bay has a hint**
  (§3) it is appended as the *why*, verbatim. With an empty table the parenthetical is omitted and
  the line is the verb, the count and the item alone.
- **When nothing is left to place**, the line reports the empty group and names the claim path: a
  confirm on a fully satisfied session is the idempotent empty report (14 §10 step 1), and a claim is
  taken by settle, not by a build. The mode must not promise a formation a confirm cannot produce.

### 12.4 Hand awareness (F.4)

When the player's main-hand item can place a block that one of the current stage's unsatisfied cells
expects, those ghosts pulse (a slow alpha oscillation); when it cannot, nothing pulses and no message
appears — a wrong hand adds no noise. The match is by item, against the expected state's block item,
computed on the client from payload data only.

### 12.5 Auto-advance (F.2)

- A hand-placed block inside the session's footprint is a world change; the session's existing
  invalidation channel sees it (`LevelWatch`'s mark counter, 15 §4), the server recomputes, and the
  re-sync carries a higher `currentStage` — that is the automatic advance, with the stage banner
  (§8) announcing it and a cursor that was tracking the frontier moving with it (§6.1). **No polling,
  no second watch, no new server flag**: guided mode rides the channel that already exists, and the
  server pays one recompute per world change, exactly as it does without the mode.
- When every expected cell is satisfied the guide reaches its completion state (§12.3): the structure
  is complete, and settling is the claim step.

### 12.6 The hybrid build (F.5 backlog)

`Enter` stays confirm and still runs the executor of 14 §10 with the session's siting, source and
assembled preferences (15 §9). Hand-placing some cells and confirming the rest is legal and
encouraged; the progress lock, the removal policy and the item source are unchanged. What is
**not** in v1 is the guide asking "shall I build the rest?" — that is F.5, backlog.

## 13. Client config (G.1)

One file: `config/structurecumulus-client.toml`, registered as `ModConfig.Type.CLIENT` beside the two
server files (`structurecumulus-server.toml`, `structurecumulus-preferences.toml`, adapter §16.1/§18).
No second client config file is ever registered.

| Key | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `animation.enabled` | bool | `true` | — | master switch for the reveal fade, the pop-in, the fade-out and the celebration; `false` shows every state change and every reveal at once |
| `animation.speed` | double | `1.0` | 0.25–4.0 | divides every animation cadence (§6.4, §9.2); never a cap and never a count |
| `animation.sound` | bool | `true` | — | place and break cues (§9.3) |
| `animation.celebrate` | bool | `true` | — | the formed celebration (§9.5) |
| `hud.progress` | bool | `true` | — | the three tiers (§7); `false` leaves the stage line |
| `hud.stageBanner` | bool | `true` | — | the banner (§8) |

- **The file lands with E1**: `animation.enabled` and `animation.speed` govern the staged playback,
  which E1 ships, together with the two `hud.*` keys. **E2 adds** `animation.sound` and
  `animation.celebrate` beside them — the two milestones share one file, so the second extends the
  first and neither registers a second one.
- **A client key changes nothing but pixels.** No key here can change a plan, a mark, a candidate, a
  stage index or a world write — a disabled animation is a narration turned off, not a feature
  removed (§9.6).
- **A config that has not loaded answers the shipped defaults** (the same law as the preference
  config, 15 §9).
- **The ghost knobs of 15 §5 belong here** — that sentence's "alpha and depth behaviour are client
  configuration" is this file's, and the two keys join it when they land. They are not part of this
  pack's acceptance, and naming them here is the reason no other file may appear.

## 14. Codes

The pack allocates **no code family and no code**. What it shows is a finding, a mark, a stage name or
a document string that already exists; a session invalidation stays `SC-BLD-0008` (14 §14, 15 §10); a
cap overflow is a WARNING marker (15 §3) or, for a non-session payload, a note on the animator's own
overlay naming the shown and expected counts (§9.1) — never a new code. A milestone that believes it
needs a code has found a defect in this document and reports it.

## 15. Determinism, isolation and what a reviewer attacks

- **The stage table is a function of the blueprint.** Two calls, two servers and two clients agree;
  a scrambled ghost list agrees; a rotation, flip, origin move, candidate change, re-sync, recall or
  timeout cannot change it (§4.3, §5). A test that reorders the input and compares the table pins it.
- **Two orders, never one.** The never-mixed pair: the stage order (declaration-derived, exists for
  every expected cell before any solve) and the play order (the transaction's own order, exists only
  after it ran). A cell's stage index is not a plan step and a plan step is not a stage (§4.1, §9.1).
- **The client derives nothing.** It may keep view state (the cursor, the reveal counts, the camera,
  the mode, the banner) and it may draw what it was sent; every stage index, name, count, order and
  the frontier itself arrive on the wire (§5, §6.1). It compares and it draws, nothing more.
- **Zero world writes.** Every surface of this file is render state plus, at the one confirm exit,
  the executor that already existed. A reviewer should reject any change that adds a `setBlock`, a
  tick handler on the server, or a new map on `ServerState` for presentation.
- **Dedicated server.** Payloads are plain data; handlers resolve client classes inside the enqueued
  work; no client class loads (15 §6.4, adapter §17). A presentation type on the common side must be
  a record of primitives and kernel values.
- **Input is never locked and no animation is on the critical path.** A confirm, a cancel, a turn and
  a slice respond during playback and during a pop-in (§6.3, §9.6).
- **Caps never truncate silently.** Three cap families, each with a named overflow: the stage table
  (256, §4.1.8), the session's render lists (512 ghosts and 48 markers, 15 §3) and the animated cells
  (512, §9.1).
- **The text is data.** No branch in the kernel, the solver or the check path reads a description or
  a hint; equality of compiled blueprints includes them (§3.2, D-94).

## 16. Non-goals (v1)

3D billboard stage numbers (A.6); broadcasting another player's placement animation (B.5); particle
trails or beams (B.6); localisation of the UI vocabulary (C.5 — the formatter and the HUD keep literal
English); exporting a view as an image (D.4); the half-automatic "shall I build it" dialogue (F.5);
hints on a part that is a focus id rather than a bay (v1 hints are bay-scoped, §3); a stage or
playback state on the server; a second client config file; and any synchronisation of the staged
playback into the web `visual/` traces (roadmap §6).

## 17. Milestones

| Milestone | Sections |
| --- | --- |
| E1 | §4 the stage model, §5 the payload (both payloads and the catalog), §6 playback and switching, §7 the HUD, §8 the banner, §13's `hud.*` keys |
| E2 | §9 placement feedback, §13's `animation.*` keys |
| E3 | §3 the document members and the carry law, §10 the display points |
| E4 | §11 the view modes |
| E5 | §12 guided mode, plus the count and plan close-out |

The gates are the milestone table of [`docs/plan/presentation-plan.md`](../plan/presentation-plan.md);
this file owns the contracts they test.
