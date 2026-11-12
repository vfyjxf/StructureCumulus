# 17. The preview screen: catalogue view, camera, four viewports, stage playback and the exploded view

> Status: approved, design first (SC0, 2026-11-12). It specifies the preview screen pack on top of the
> preview of [15](15-preview.md) and the presentation pack of [16](16-presentation.md), and lands with
> milestones SC1–SC4 of [`docs/plan/screen-plan.md`](../plan/screen-plan.md). The rulings are
> D-101…D-105. A "roadmap" citation names the source plan's roadmap,
> `Data/StructureCumulus/plan-2026-11-12-preview-screen/00-roadmap.md`, and the item tags it uses
> (R1…R12) are that document's.
>
> The pack adds **no capability**: every world write, claim, plan, finding and session stays where
> design 14, 15 and 16 put them. It adds a room in which to read a blueprint: a screen that is nothing
> but the client's own catalogue, seen through a camera the viewer owns.

## 1. Problem

The catalogue is right and has nowhere to be read. A player who wants to understand a blueprinted
machine has three surfaces and none of them is a room: the world ghosts (15 §5) need a placed siting
in a live world, the JEI page (15 §6) is a thumbnail inside another mod's browser, and the progress
HUD (16 §7) talks about a build that is already running. The blueprint itself — its parts, their
order, their shape — has no full screen anywhere, and a 5×5×4 machine and a 4-cell port read the same
in a 160-pixel recipe page.

The user said so, on 2026-11-12, in the order they hit the walls:

| The user's words | The need it names |
| --- | --- |
| "怎么没有复杂结构" — there is no complex structure | R1: real demo content, not probes |
| "操作方式很难用" — the controls are hard to use | R2: buttons and the mouse, not a key table |
| "为什么不能多个视图同时存在" — why can't several views exist at once | R3: four viewports, live-linked |
| "默认应该 45 度正交视角" — the default should be 45° orthographic | R4: isometric by default |
| "一堆空结构 / JEI 里有空白条目" — a pile of empty structures / blank JEI entries | R5: the fit and the blank entry |
| "单独做一个多方块预览界面，不仅仅局限于 JEI" — its own screen, not only JEI | R6: an independent `Screen` |
| "把坐标轴补上去" — add the axes | R7: the axis gizmo |
| "类似 ponder 那种动画播放" — playback like Ponder | R8: staged playback |
| "默认进去看到完整结构，有拆解按钮进入分解部分" — open on the whole structure, with an explode button | R9: the whole view and the exploded view |
| "按 stage 自动播放，可暂停，分阶段，阶段变化了什么" — auto-play by stage; what changed at each stage | R10: the staged view and its delta |
| "支持缩放，类似常规 3D 建模软件的镜头操作和快捷方式" — camera and shortcuts like a 3D modelling tool | R11: orbit, pan, zoom, frame all, snaps |
| (the user's choice) the screen and the world linked both ways | R12: the entry points and ghost to world |

R8 **reverses an earlier ruling** (2026-11-12, morning: no Ponder-style screen of its own). This pack
follows the later feedback, and the reversal is narrow: the in-world staged playback of 16 §6 stays
exactly as it is, and the screen's playback is a second consumer of the same cine core (§7.5), never a
replacement for it.

Four questions the screen answers, and nothing else:

- **What is this blueprint, whole?** Every part, in the declaration's own order, drawn as it will be
  built (§7.1).
- **What changes at each stage?** The staged view's delta and one summary line per stage (§7).
- **How do the parts come apart?** A per-bay exploded view with the parts named (§8).
- **Where am I looking, and how do I get there?** One camera the viewer owns, four viewports, three
  axes and the modelling tool's own gestures (§4–§6).

What the screen is not asked to answer — and does not — is what is missing, what is claimed, what a
plan would do or what a build reported: those are a session's questions (15 §4, 16 §7–§9), and this
screen holds no session (§2).

## 2. Position

- **The screen is a pure client catalogue view.** It reads the last catalogue this client received
  (`PreviewCatalogClient.entries()`, 15 §6.1) and nothing else: no session, no world, no level, no
  block entity, no server round trip to open it and no server state to close it. It is the first
  preview surface that needs no siting, no player, no JEI and no world at all — a client that holds the
  catalogue can always show it, on any server, whether or not the server's half knows the screen
  exists.
- **It determines nothing.** Every fact it draws arrives in the entry: the cells with their stage
  indices (16 §5), the stage table (16 §4), the variant summary (15 §12.1) and — added by this pack —
  the bay each stage derives from (§3.2). The screen may aggregate what it was sent (a centroid, a
  bounding hull, the largest material of one stage, the projection of a point); it may never derive a
  stage, a candidate, a score, a mark or a plan (15 §2, D-93).
- **It writes nothing.** No `setBlock`, no claim, no session, no control payload. The one packet it
  can send is the existing "ghost to world" open (§9.4) — 15 §6.3's channel unchanged — and it leaves
  the screen for a session the world already owns.
- **It adds two packages and two carried fields, and no module edge.** `mc.preview.scene` (the
  JEI-free scene core, moved out of `mc.preview.jei`) and `mc.preview.client.screen` (the screen
  itself) are adapter packages on the physical client (§10); the wire gains one bay id per stage and
  one optional heading on the open payload (§3.2). The kernel is untouched, no config file is added,
  and the settings gain one key binding (§9.3).
- **What it may not touch:** the session (15 §4), the payload caps (15 §3), the stage derivation
  (16 §4, D-93), the presentation cadence (16 §6.4), the mark colouring (15 §5), the confirm and the
  executor (14 §10, 15 §4), the code families (14 §14, 15 §10, 16 §14) and the cinema's ownership of
  the frame (16 §6.7). A screen feature that needs one of these changed is a defect in this document,
  not a licence.
- **The law that makes it cheap to trust:** with the screen open, the world is exactly as it was when
  it opened. Nothing here can move a block, spend an item or open a session except the one button that
  says it will.

## 3. The data source

One entry of the last catalogue, plus the two carried facts the catalogue is missing for this screen.
The catalogue is 15 §6.1's: one entry per blueprint that compiles, projected at the identity
orientation, pushed on join and on `/reload`, capped and left out rather than truncated.

### 3.1 What the screen reads

| Fact | Where it comes from | What the screen uses it for |
| --- | --- | --- |
| the blueprint id | `Entry.blueprintId` | the title line, the command argument, the open request |
| the expected cells (block id, properties, stage index) | `Entry.cells()` | every model, the picking, the hull, the axes |
| the stage table (names, expected counts, carried text) | `Entry.presentation().stages()` | the stage bar, the cursor, the delta, the summary line |
| the bay of each stage | `Stage.bay` (§3.2) | the exploded view's groups and labels |
| the variant group and its member labels | `Entry.variants()` (15 §12.1) | the variant row |
| the material lines | `Entry.materials()` | not displayed by this screen (§14) |

- **A page, not a session.** The entry is one blueprint at the identity orientation: no candidates, no
  origin, no pose, no marks, no counters and no confirm. `presentation.currentStage` and
  `satisfiedCells` are read as zero facts and never displayed — a page satisfies nothing and is
  working on nothing (§7.1).
- **The catalogue is this screen's whole world.** `PreviewCatalogClient` is a read-only snapshot with
  a listener list (adapter §17); the screen registers one listener and reconciles in place (§3.4). It
  parses no document and recomputes no kernel decision (15 §6.1).
- **No second source.** The screen reads no `PreviewClientState`, no open session and no preference
  store, and of the client config only the `animation.*` switches of 16 §13 (§7.5). A screen opened
  while a session is running shows the catalogue's page for the same id, not the session's siting: a
  page has no pose to show.

### 3.2 The two carried facts the screen adds

Two facts this screen cannot compute, and their caps. Nothing else on the wire moves: the cell shape
stays 16 §5's `GhostCell`, the presentation block keeps its components and the catalogue keeps one
entry per blueprint id.

| Shape | New component | Direction | Cap |
| --- | --- | --- | --- |
| `net.Stage` | `NamespacedId bay` — the bay this stage derives from | both payloads (session and catalogue) | none of its own: a bay with a drawn cell owns at least one stage, and the table is capped at `maxStages` = 256 (16 §4.1.8), so the distinct bays can never exceed 256 |
| `net.PreviewOpenPayload` | `@Nullable Heading facing` — the direction the viewer faces | C2S open | an enum, never a list: the four horizontal values are accepted and `up`/`down` read as absent |

- **Why the bay rides on the stage.** §8 separates *parts*, and a part is a bay; the catalogue's cells
  carry a stage index and nothing else (16 §5). A cell's bay is the bay of the stage that owns it —
  16 §4.1.2 makes provenance exact (one cell, one producing check, one bay) — so one id per stage
  carries the whole relation: one stage per bay in bay mode, and every layer stage of a single-bay
  document naming that same bay. The smallest total carrier wins: 256 ids against 512 per-cell
  indices, and the id is what a label needs anyway.
- **Why not a per-cell `bayIndex`.** It re-sends one integer per cell where one id per stage
  suffices, and it would change `GhostCell`, the one shape the session, the catalogue and the JEI
  tooltip share (16 §5: one cell type must mean one thing).
- **Why not a bay list beside `ghostCells`.** A parallel list whose alignment no type enforces is the
  shape 16 §5 already rejected for the stage index; `Stage.bay` needs no alignment rule, because the
  stage is the cell's own group.
- **Why the session carries it too.** The session and the catalogue build one table from one
  derivation (16 §4.1) and `Stage` is one record; filling the field on both sides keeps one shape with
  one meaning. No session surface reads it in v1, and this document does not pretend otherwise.
- **No new cap, no new truncation.** `maxStages` bounds the distinct bays; the cell list stays capped
  at 512, the marker list at 48 and the whole catalogue at 4096 cells (15 §3, §6.1). An over-full
  stage table is 16 §4.1.8's WARNING marker, and a stage the cap turned away contributes no cells, so
  no cell can name a bay that did not travel.
- **The heading is a request, never a pose.** §9.4's ghost to world sends the direction the screen's
  camera looks, rounded to a cardinal (§9.4); the session passes it where the player's own facing goes
  (15 §7's turn snap, 15 §9's facing weight), so it can only order the session's own candidates. A
  heading the blueprint cannot express at that origin changes nothing, and the session's lock, cycle
  and scoring are unchanged.

### 3.3 The hull that draws (R5)

The frame is fitted to the cells that draw something — not to every expected cell, and not to a
declared box.

- **What is in the hull.** A cell contributes a corner when its expected state resolves on this client
  to a block that draws a model. A cell whose state is air (`BlockState.isAir()`: `air`, `cave_air`,
  `void_air`) draws nothing, so it contributes no corner, no label anchor and no picking target; the
  hull is the bounding box of the cells that are left.
- **Why it matters, precisely.** A document carves space by naming a block leaf whose id is an air
  block: `{"kind":"block","id":"minecraft:air"}` is a legal `BlockLeaf` and the projection keeps the
  cell (D-91), so the catalogue ships a `GhostCell` for it. The renderer draws nothing for it, but the
  fit counts it: a 5×5×4 machine that pads its bounds with air cells is drawn at the size of its
  padding, and a page with a handful of real cells reads as empty. The kernel's own `air` marker arm
  is **not** this case — `ExpectationDeclaration.MarkerLeaf.air` (and `envelopeAir`) declares no block
  state at all, so an envelope document has no air cells and its hull is already its blocks. The two
  mechanisms are different, and this law addresses the one that exists.
- **Both consumers fit the same hull.** The JEI isometric view's fit and every screen viewport use
  this hull; the three-view mode's per-panel fit (16 §11) fits the same hull in each panel's plane.
  15 §6.2's "fitted to the blueprint's bounds" is corrected to this sentence — an entry has no bounds
  field, and a fit to a declared box would re-introduce exactly the empty page this law removes.
- **The blank entry (R5's second half).** An entry with no cell that draws — every cell air, or no
  cells at all — draws nothing at all: it is left out of the catalogue at build time with the WARNING
  line that names it, in the same family as a blueprint that does not compile, one over 512 cells or
  one over 64 material lines (15 §6.1). A page that draws nothing is worse than a missing page, and
  both consumers — JEI and this screen — see one catalogue, so they agree by construction. The
  material lines already drop air (`Items.AIR` yields no line), so an air cell never added an item
  count; this law adds only the absence of a scene.
- **The third cause, attributed and not "fixed".** The user's "a pile of empty structures" also has a
  plain reading that no mechanism here addresses: the current gallery is 3 pages of 4 documents, and a
  small structure inside a large page area reads as empty, which no hull rule changes. That belongs to
  R11's frame-all (§4.4) and to R1's richer demo content (§11); §14 records it as such instead of
  inventing a display mechanism for it. This document states the uncertainty rather than claiming the
  complaint is fully explained.
- **Determinism.** The hull is a pure function of the entry's cells and this client's registry: equal
  cells with equal states give an equal box, and a block id this client cannot resolve contributes no
  corner — a client rendering fact, never a server one.

### 3.4 Open, refresh and absence

- **One page per open.** The screen is constructed on an id (from JEI, §9.1; the command, §9.2; or the
  world key, §9.3) and looks it up in the snapshot. An id the snapshot does not carry — left out by a
  cap, not compiled, or never shipped — does not open a screen: the game stays where it was and one
  client-side message names the id. That message is local (`LocalPlayer.displayClientMessage`), never
  a chat packet; a client view's failure is the client's.
- **The catalogue pushes while the screen is open.** The screen listens (15 §6.1's snapshot has a
  listener list) and reconciles in place: the shown entry is replaced, its scene rebuilt, and the
  camera, the explode state and the stage cursor are kept and clamped to the new table. A page whose
  cells changed under the cursor keeps the cursor where it was while the table still has that index.
- **A vanished id closes the screen.** A `/reload` that removes the entry, or replaces it with one the
  catalogue no longer carries, closes the screen and says why: a stale page would be a lie about the
  document source.
- **The screen outlives nothing.** It has no session to expire, no timeout, no invalidation channel and
  no world to watch. Closing it (Esc, the close button, or another screen the game opens over it) ends
  every piece of state it kept; nothing is remembered across opens (§14).

## 4. Projection and the camera

The camera is a value, the projection is a field of it, and every gesture is a pure function of the
value plus a number.

### 4.1 The projection

- **Default: orthographic, true isometric.** Yaw 45°, pitch `atan(1/√2)` ≈ 35.26438968° — the
  projection in which a unit cell's three axes have equal apparent length. It is R4's default and the
  screen's reset state (§4.2), and it is the projection a blueprint is *read* in: parallel edges stay
  parallel, so a 3×3 square reads as a square and a one-cell offset cannot hide behind perspective.
- **The alternate: perspective.** A pinhole camera on the orbit's sphere, 16 §6.7.2's fixed 70°
  vertical fov and §4.3's fit distance. It exists because a viewer asked for it, not because the
  default is wrong: perspective sells the depth of a big machine, and the isometric read stays one
  button away.
- **The toggle is a button, not a key.** §4.4's table has no projection key and §4.5 has the button: a
  projection is view state, it never reaches the wire, and it is not worth a binding (D-103).
- **Both projections use the same camera.** Flipping the projection changes nothing but the
  projection — target, yaw, pitch, distance and zoom stay — so a framed page stays framed. An
  orthographic zoom and a perspective distance are not the same framing and the toggle does not
  pretend they are: the perspective arm refits `distance` from the drawn hull, the orthographic arm
  keeps its zoom (§4.2).
- **The scene pass is 15 §6.2's pass, moved not rewritten.** One block model per cell, resolved from
  the wire's block id and property strings against the client's registry, at full brightness, drawn
  far-to-near by depth inside the GUI pass, flushed with `BufferSource.endBatch()` and **not**
  `GuiGraphics.flush()` (whose contract disables the depth test around the batch). The screen adds no
  renderer of its own: it hands the scene a rectangle and a camera.

### 4.2 `SceneCamera`

```java
package dev.vfyjxf.cumulus.mc.preview.scene;

// A box in cell units: the drawn hull of §3.3, never a declared bounds.
public record Hull(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    public Hull union(Hull other);
    public boolean isEmpty();
}

// One viewport's camera: an orbit pose about a target, the projection it feeds and the orthographic
// zoom. A pure value; every operation returns a new camera and no operation reads the world.
public record SceneCamera(
    float targetX, float targetY, float targetZ,
    float yaw, float pitch,
    float distance, float zoom,
    Projection projection
) {
    public enum Projection { orthographic, perspective }

    // The viewer's three snapshots beyond the three natural sides (1/3/7 and Ctrl).
    public enum Snap { front, back, right, left, top, bottom }

    public static final float defaultYaw = 45.0F;
    public static final float defaultPitch = 35.26439F;   // atan(1/sqrt(2)) in degrees
    public static final float minZoom = 0.25F;
    public static final float maxZoom = 8.0F;
    public static final float minDistance = 1.0F;
    public static final float maxDistance = 512.0F;
    public static final float fovDegrees = 70.0F;

    public static SceneCamera isometric();                  // the default, about the origin
    public SceneCamera orbit(float deltaYaw, float deltaPitch);
    public SceneCamera pan(float right, float up);          // the target moves in the view plane
    public SceneCamera zoom(float factor);                  // ortho: zoom; perspective: distance
    public SceneCamera frame(Hull hull, float aspect);      // target and fit for one hull
    public SceneCamera snap(Snap snap);                     // the camera's axis snapshot
    public SceneCamera reset(Hull hull, float aspect);      // isometric(), then frame(...)
}
```

- **The clamps.** Pitch is clamped to (−89.9°, 89.9°) so the orbit never reaches the pole where yaw
  stops meaning anything; yaw wraps into [0°, 360°); zoom clamps to [`minZoom`, `maxZoom`]; a
  perspective zoom multiplies `distance` and clamps it to [`minDistance`, `maxDistance`]. Every
  operation is total — no gesture has a refused arm, and no clamp is a hidden mode.
- **`pan` is in the view plane.** A drag of `(dx, dy)` pixels moves the target by `dx ×
  unitsPerPixel` along the camera's right vector and `−dy × unitsPerPixel` along its up vector, where
  `unitsPerPixel` is §4.3's fit scale inverted. The target stays an arbitrary point, not a cell.
- **`frame`** moves the target to the hull's centre, sets `zoom` to 1.0 and applies §4.3's fit for the
  given aspect — "show me everything" cannot mean "everything, still three times too large". `reset`
  is `isometric()` then `frame(...)`, so a lost viewer gets the map's own starting state back.
- **`snap`** is the camera's own snapshot, not the session's turn: `front` looks along +Z at the X/Y
  plane, `right` along +X at the Z/Y plane, `top` along −Y at the X/Z plane — 16 §11's front law, one
  vocabulary for both surfaces — and `back`/`left`/`bottom` are the negated poses of the same three.
  A snap never touches the target or the zoom: it moves the eye, not the page (§5.2).
- **No clock, no world, no registry, no packet.** The record is floats and enums, so a unit test
  constructs it, orbits it, snaps it and compares it without a client.
- **The screen owns one camera, not four.** The four viewports share the value (§5.2) and each
  projection is computed from it; a small viewport never mutates it.

### 4.3 The fit

`fit(hull, viewport, camera)` is one pure function with three arms:

| Arm | Scale |
| --- | --- |
| orthographic | `min(width × margin / hullWidth, height × margin / hullHeight) × zoom`, `margin = 0.86` (the scene's own constant, 15 §6.2's fitted scale) |
| perspective | the eye sits at `distance` on the orbit ray; the fit distance is `hullRadius / tan(fov/2) × 1.2` (16 §6.7.2's overscan) and the zoom is unused |
| a degenerate hull (no drawn cell, one cell, one column) | the one-cell box, so a fit never divides by zero and a single cell still fills the frame |

- **When it recomputes.** On a page change, a viewport resize, a projection switch and an explicit
  frame-all — not on every frame. A page's hull is fixed, and a manual zoom belongs to the viewer:
  a redraw must never undo it.
- **Everything outside the hull is not drawn.** A cell the hull does not contain cannot exist (the hull
  is the union of the drawn cells), so the fit is total over the page; a cell the client cannot
  resolve draws nothing and is not counted twice.
- **Each viewport fits to its own aspect.** The main rect and the three small ones have different
  shapes, so the three small views fit the same hull to a different ratio and each keeps the whole
  hull inside its own frame (15 §6.2's "never paints over a neighbouring recipe" rule, now four
  rects). The shared value is the *camera*, not the pixels.
- **The refit rule under the exploded view.** §8's offset is not a hull change: a frame-all fits the
  current, exploded hull, and arming the explode does not refit an already-framed view. Collapsing it
  the same way leaves the camera where the viewer put it.

### 4.4 The input table (R11)

The screen owns the keyboard and the mouse while it is open: a `Screen` receives every key press
(`Screen.keyPressed`), its mouse is released from the game's grab (`Minecraft.setScreen`, KB 03),
and vanilla does not poll a `KeyMapping` while a screen is open. Every world preview key of 15 §5 and
16 §6.3 is therefore inert here, and every key below is the screen's own — the two tables never meet,
which is R11's conflict check stated as a law and asserted by the SC1 `screen` uitest group.

| Input | Action |
| --- | --- |
| left drag | orbit about the target |
| Shift + left drag, or middle drag | pan the target in the view plane |
| wheel | zoom: the orthographic factor or the perspective distance, clamped (§4.2) |
| double click (two clicks within `GuiEventListener.DOUBLE_CLICK_THRESHOLD_MS` = 250 ms with no drag between), or `F` | frame all: `frame(hull)` |
| `Home` | reset: the default 45° isometric, framed |
| `1` / `3` / `7` (number row; the keypad's own keys are aliases) | the main viewport snaps to front / right / top |
| `Ctrl` + `1` / `3` / `7` | back / left / bottom |
| `Space` | play / pause (§7.3) |
| `↑` / `↓` | previous / next stage: shows that stage whole and pauses (§7.3) |
| `B` | one cell step (§7.3) |
| `Esc` | close the screen (`shouldCloseOnEsc`) |

- **`Space` is the screen's, `V` is the world's.** 16 §6.3's play/pause key stays `V` in a session; a
  KeyMapping never fires while a screen is open, so `Space` breaks nothing and needs no second
  binding. No screen action is a `KeyMapping`: this pack adds no `PreviewKeys.Action` for a
  screen-only gesture, and its one new binding (`O`, §9.3) belongs to the world.
- **A gesture belongs to the main viewport.** Dragging, scrolling, double-clicking and every key act
  on the shared camera of §5.2. A drag that starts on a small viewport is ignored — the small views
  are read-only — and a left press that never moves is a click, not an orbit.
- **`Esc` is the screen's, and closing is not a cancel.** A screen owns Esc (KB 03); here it closes the
  view and nothing else. No session closes, no packet is sent and no page is forgotten — there is
  nothing to forget.
- **`Space` is also not the jump key here.** The jump binding is a `KeyMapping`; with the screen open
  it is not polled, so the space bar cannot move the player out of the page.

### 4.5 Buttons, and the mouse as the primary surface (R2)

Every action of §4.4 has a button, and every button's action is also a row above: the two lists are
one list read twice, and SC1's `screen` uitest counts them.

| Button | Action |
| --- | --- |
| close | close the screen (`Esc`) |
| iso | `Home`: the default isometric, framed |
| frame | `F` / double click: frame all |
| front · side · top | the three snaps (`1`, `3`, `7`) |
| ortho / perspective | the projection toggle (§4.1) |
| all · explode | the whole view / the exploded view (§7.1, §8) |
| play / pause · step · ‹ · › | the stage player (§7.3) |
| hidden · outline | the future cells' treatment (§7.2) |
| ‹ variant · variant › | the group's members (§9.5) |
| ghost to world | the open request (§9.4) |

- **The mouse is the primary surface (R2).** A viewer who never learns a key can open the page, rotate
  it, zoom it, play it, explode it and send it to the world with the pointer alone; the key table is
  the same actions accelerated, and a button's tooltip names its key when it has one.
- **No toggle without an exit.** Every toggle — projection, explode, future outline, play — has the
  same button back, and `Home` returns the camera to the state a new open would produce, so a lost
  viewer is one button from the beginning.
- **The button strip is the screen's, not a HUD.** It is drawn inside the screen; the world's HUD
  layers (16 §7) are not involved, and no HUD config key governs it.

### 4.6 The screen's clock

A screen is a pause: `Screen.isPauseScreen()` is true by default, so in single-player the world stops
ticking behind the screen and `ClientTickEvent` — the clock every animation of 16 uses — does not
fire. The screen therefore keeps one clock of its own:

- **One accumulator.** Each frame adds the frame's real-time delta in ticks
  (`DeltaTracker.getRealtimeDeltaTicks()`, which answers 0.5 for a delta above 7 ticks — the client's
  own hitch guard) to a fractional accumulator; the accumulator's integer part is the screen's tick
  cursor and its fraction is the frame's partial tick. Every animation of this screen samples
  `cursor + fraction`, exactly the shape 16 §6.4 and 16 §6.7 already use, so their durations keep
  their meaning at 20 ticks per second.
- **Why not the game tick.** A paused game has no game tick, and a screen that refuses the pause
  (`isPauseScreen() = false`) would let a mob hit a player who is reading a manual. This document will
  not buy animation with the player's safety.
- **Why not a millisecond timeline.** 16 §6.4's constants are ticks and stay ticks; the screen's clock
  is a tick clock at the same rate, not a second cadence in another unit. The accumulator advances by
  a clamped real-time delta and then behaves exactly like a tick cursor (KB 04's "the tick owns the
  time, the frame owns the blend").
- **Determinism.** The clock is two numbers the screen owns. Every animated surface is a pure function
  of them plus payload values, so a test advances the accumulator by hand and compares a frame; no
  test waits for wall time.
- **The world keeps its own clock.** The in-world playback, the placement animator and the cinema
  (16 §6, §9) still run on the game tick and are untouched; a session behind the screen simply does
  not advance while the screen pauses it.

## 5. The viewports (R3)

One main viewport and three axis-locked ones, all four drawn from one page and one shared camera
state. R3's answer is not four copies of the scene: it is one scene, four rectangles and four
projections.

### 5.1 The four panels

`layout(width, height)` is a pure function of the screen size (a unit test pins it):

| Constant | Value | Meaning |
| --- | --- | --- |
| `titleHeight` | 20 | the title strip at the top |
| `controlHeight` | 56 | the control strip at the bottom: the stage player and the button row |
| `panelGap` | 4 | the gap between any two viewports and the gap to the strip edges |
| `mainFraction` | 0.72 | the main viewport's share of the viewport area's width |

- **The main viewport** takes the left `mainFraction` of the viewport area; **the side column** takes
  the rest and is split into three equal-height panels, top to bottom: `front`, `side`, `top` (16 §11's
  front law: front looks along +Z at the X/Y plane, side along +X at the Z/Y plane, top along −Y at
  the X/Z plane). Each panel draws its own name in a corner as chrome text.
- **Each viewport owns its rectangle and its depth is the GUI pass's.** A panel's pass is scissored to
  its rect (`GuiGraphics.enableScissor`), so no panel's pixels are touched by another's draw and no
  panel can paint over a neighbour (15 §6.2's bounds rule, four times). The main viewport draws first,
  the three small ones after it.
- **A degenerate size collapses honestly.** Below the room the strip and four rectangles need, the
  layout keeps the main viewport and drops the small ones (they reappear when the room returns); it
  never draws a zero-area rect and never overlaps two panels.
- **The scene is built once per page**, not once per viewport: four panels draw the same resolved cell
  list through four projections (§5.2).

### 5.2 The linkage law

What is shared and what is not, stated once:

| State | Main viewport | Front / side / top panels |
| --- | --- | --- |
| the target | shared | shared: the same point, so all four look at the same place |
| the fit scale and `zoom` | shared | shared: the same units per pixel |
| yaw, pitch | the viewer's | fixed: axis-locked, never rotated by the orbit |
| projection | the toggle's (§4.1) | always orthographic: a drafting view, not a perspective one |
| the page's cells | drawn | drawn |
| the stage treatment | drawn | drawn |
| the explode offset | drawn | drawn |
| the hover outline | drawn | drawn |

- **Moving together:** pan, zoom, frame all, reset and a page change move the shared target and
  scale, so all four follow.
- **Moving alone:** orbit turns the main viewport only — the panels are axis-locked and never rotate —
  the three snaps (`1`/`3`/`7`, §4.4) set the main viewport's pose and leave the panels untouched, and
  the projection toggle changes the main viewport's projection only. That is the roadmap's "the main
  view jumps, the small views do not" as a law.
- **The small views are read-only.** A press, a drag or a wheel over a panel changes nothing except
  the hover (§5.3); there is no promote-to-main, no per-panel camera and no per-panel slice (v1 has no
  slice at all, §14).
- **One page, four projections, no second scene.** A resolved cell list, a stage cursor and an explode
  offset are computed once and handed to each panel with its own `SceneCamera`; a panel's projection
  is `fit(hull, panelRect, panelCamera)` (§4.3). Nothing about a panel is stored: the layout and the
  projections are recomputed from the screen size and the shared value.

### 5.3 Hover, picking and the tooltip

- **One hover, resolved once.** The cursor is unprojected inside the viewport it is over — main or
  small — against the cells that are *currently drawn* (the staged view's future cells are hidden and
  therefore not pickable, exactly as 15 §6.2 refuses to pick a hidden slice layer). The nearest
  visible cell whose box the ray crosses wins (15 §6.2's rule), using the cells' current positions,
  so an exploded cell is picked where it is drawn.
- **The outline is drawn in all four.** The hovered cell gets a 1-px chrome outline in every viewport
  it appears in (a panel that does not contain it draws nothing), because the point of four views is
  to find one cell in three planes at once.
- **The tooltip is 15 §6.2's content plus the owning stage's name** — block id, property values, and
  the stage that owns the cell (16 §11's own tooltip) — drawn near the cursor inside the screen. It is
  a rendering of payload fields and a stage name, not document text: no 16 §10 display point is added.
- **The axes are not pickable.** A press on the gizmo orbits the main viewport like a press on empty
  space; nothing in the scene has a click action except the screen's own widgets.
- **Nothing is selected.** The screen has no selection model, no part pin and no click-to-cycle: a
  page is read, not edited (15 §8's pins are a session's).

## 6. The axes (R7)

R7's gizmo is scene geometry, not a screen-space compass: three unit axes drawn from the structure's
own frame origin, through the same camera as the cells, with the axis names at their projected ends.

- **The frame it shows is the frame being drawn.** A catalogue page is the identity projection
  (15 §6.1), so the gizmo is the declaration frame: X, Y, Z as the document's axes, not world
  north/east/up. If a surface later draws a posed blueprint, the gizmo reads the drawing's own frame
  and rotates with it — the gizmo never shows a frame the picture does not use.
- **It is drawn, so it orbits.** The axes are geometry in the scene, transformed by the viewport's
  camera: orbiting the view turns the axes, zooming scales them with the cells, and they never sit
  screen-fixed. That is the whole difference between a gizmo and a legend, and it is what makes a
  rotated view readable.
- **Where and how big.** The origin is the drawn hull's minimum corner (the structure's own zero), and
  each axis runs `max(1, 0.25 × diagonal)` cells along its direction. The length follows the structure,
  not the viewport, so the axes shrink into the picture rather than growing over it; a one-cell page
  gets one-cell axes.
- **The labels.** `X`, `Y`, `Z` are drawn as GUI text at each axis's projected end, after the models,
  with no depth test — a label must be readable through the block in front of it, and a depth-tested
  label is unreadable exactly where the gizmo is dense (the same choice 16 §10's billboards avoid by
  drawing in screen space).
- **The colours are chrome.** The three axes take the conventional red/green/blue of a 3D editor's
  gizmo. They are scene chrome, never a cell mark colour: 15 §5 owns the mark palette and this document
  adds nothing to it.
- **The gizmo is the main viewport's.** The three small panels are axis-locked views and are named by
  their corner label (`front`, `side`, `top`); a second gizmo in a second axis-locked view would be
  the same three arrows three more times. R7 asks for the axes, and one honest gizmo is one.
- **`AxisGizmo` is a pure function of (hull, camera, rect)**: it returns three segments and three label
  anchors, so a unit test projects a known hull through a known camera and compares the pixels without
  a client.

## 7. Stage playback (R8, R10)

Two presentations of one page: the **whole view**, which is what a page opens on, and the **staged
view**, which is what a play, a step or a stage click enters. The whole view is R9's "the complete
structure first"; the staged view is R10's "what changed at this stage".

### 7.1 The whole view and the staged view

- **The opening frame is the whole structure (R9).** Every drawn cell, at full alpha, no cursor, no
  stage relation, no summary line: the page as the blueprint declares it, in one picture. The stage
  bar and the player are already there (a page always has at least one stage, §3.3), so the staged
  view is one click away; nothing is emphasized until the viewer asks.
- **A play enters the staged view.** `Space`, the play button or a stage click starts (or resumes) the
  staged view with the cursor at the first stage and no cell revealed yet; a stage click or `↑`/`↓`
  enters it with that stage whole and paused. The whole view is not a third state of the playback — it
  is the absence of a cursor.
- **`all` returns to the whole view.** The button drops the cursor and the reveal counts and draws
  every stage again; the playback stops. `Home` does not: it resets the camera and leaves the
  presentation alone.
- **The whole view is a fresh page's state.** Opening the screen, switching a variant (§9.5) and
  returning with `all` all land here; the staged view is entered, never remembered (§3.4).
- **No progress is narrated.** A page satisfies nothing (15 §6.1), so the screen shows no counter, no
  percentage and no materials list; the stage bar is a table of contents, not a progress bar (§7.3).

### 7.2 The delta treatments

A cell's alpha is a factor on the scene's own base alpha (§4.1). Earlier stages are dimmed rather than
hidden — a page must stay legible — and later ones are hidden, because the delta is the lesson.

| Cell | Whole view | Staged view |
| --- | --- | --- |
| any stage | × 1.0 | — |
| a stage before the cursor (done) | — | × 0.25, the done factor of 16 §6.2 |
| the cursor's stage, revealed | — | × 1.0 |
| the cursor's stage, not yet revealed | — | × 0.08, the unrevealed factor of 16 §6.2 |
| a stage after the cursor (future) | — | not drawn; in **outline** mode a 1-px chrome box at the cell instead |

- **The reveal set is 16 §6.1's.** The cursor plus the reveal counts decide what is drawn and at which
  factor; a play advances the counts at §7.5's cadence, a stage jump reveals the whole stage, and a
  step reveals one more cell — the same three facts the world's playback keeps, seeded differently
  (§7.1).
- **Where this table differs from the session's, and why.** 16 §6.2 keeps future cells at 0.08 so the
  blueprint "stays faintly visible"; the screen hides them, because R10's question is *what changed at
  this stage* and a future cell that is merely faint still gives the answer away. The difference is
  deliberate, is confined to this screen, and changes nothing in 16 §6.2 — a session's HUD is about a
  build in progress, a page is about a document.
- **The outline is chrome, not a mark.** A future cell's box in outline mode is a 1-px box in the same
  neutral chrome as the axes and the panel frames; 15 §5's mark palette is untouched, and a page has
  no marks to colour.
- **The treatment composes with the explode.** The offset of §8 applies to whichever cells are drawn;
  a hidden cell has no position to offset. It also composes with the hover outline: a hovered cell is
  outlined in every panel, and the outline does not change a cell's alpha.
- **The toggle is `hidden` / `outline`**, a button (§4.5); the default is hidden. The choice is view
  state and dies with the screen.

### 7.3 Controls

| Control | Key | Button | Action |
| --- | --- | --- | --- |
| play / pause | `Space` | play / pause | from the whole view: enter the staged view at stage 1 and play; otherwise toggle the running play |
| previous / next stage | `↑` / `↓` | ‹ / › | move the cursor and show that stage whole; pauses a running play |
| one cell step | `B` | step | reveal the next unrevealed cell of the cursor's stage; past its end, move to the next stage and reveal its first cell (16 §6.3's `B`) |
| jump to a stage | — | the stage bar's segment | set the cursor to that stage, show it whole, pause |
| the whole view | — | all | drop the cursor, draw every stage, stop the play |

- **The stage bar is a table of contents.** One segment per stage, left to right in index order
  (16 §7.2's layout), wide in proportion to that stage's `expectedCells` with a 1 px minimum and a
  1 px gap, labelled with the stage name when it fits, and outlined at the cursor. It is not a
  progress bar: a page satisfies nothing, so no segment is ever filled inside; the only fill is the
  cursor's outline. Hovering a segment names the stage and its count.
- **The play is a cursor walk, not a clock of its own.** A play advances the reveal counts at §7.5's
  cadence and moves the cursor when a stage ends; at the table's end it stops with everything
  revealed (16 §6.3's "playback never leaves the session" is about a session — here it stops on the
  page).
- **Every control is view state.** Nothing here sends a packet, changes a candidate or writes a
  block; the controls move a cursor and two counters.
- **No input is locked.** The camera keeps working during a play, a stage jump and an explode, and a
  camera gesture never pauses the play.
- **A play does not persist.** Closing the screen (or switching the variant) drops the cursor and the
  play; a new open starts in the whole view (§7.1).

### 7.4 The summary line

One line under the stage bar, and it is **16 §12.3's next-step line, unchanged** — the pack composes
no second vocabulary:

```
<verb> <count>× <item> (stage <i>/<n>: <name>)
```

- **The verb is `place`.** A page's cells are all unsatisfied and none is wrong (there is no world to
  be wrong about), so 16 §12.3's rule selects `place` — a page's line reads exactly as a session's
  line for a stage with nothing built.
- **The count and the item** are the expected states of the cursor's stage: one material group per
  distinct item, the largest group wins (16 §12.3). Count and item are payload arithmetic on the cells
  the stage carries, the same arithmetic the world's formatter already does, not a server field.
- **The stage reference** is the payload's stage table, and a stage whose bay carries a `hint` appends
  that text verbatim as the *why* (16 §12.3, D-94). Nothing else is appended: no "new in this stage",
  no percentage, no arrow.
- **The line appears only with a cursor.** The whole view has none (§7.1); the summary belongs to the
  staged view, where "stage i/n" is a fact.
- **This is the sanctioned display point, not a new one.** 16 §10's table lists the surfaces that
  display document text, and the next-step line is the pack's one formatter (16 §10's last bullet);
  the screen reads that formatter, so no 16 §10 row is added and no prose is composed anywhere.

### 7.5 Cadence and the clock

The durations are 16 §6.4's, unchanged, and the clock is the screen's own §4.6 accumulator.

| Constant | Value | Meaning here |
| --- | --- | --- |
| `cellRevealTicks` | 2 | the nominal gap between two cells of one stage at speed 1.0 |
| `stageRevealMinTicks` | 10 | the floor for revealing one stage |
| `stageRevealMaxTicks` | 100 | the ceiling for one stage, so a 400-cell stage does not crawl |
| `stagePauseTicks` | 10 | the hold between a finished stage and the next one |
| `explodeTicks` | 12 | one full explode or collapse (§8.1) |
| `stageFocusTicks` | 12 | the stage-framing leg below |

- **A stage is revealed over a span, not a per-cell rate.**
  `clamp(stageCells × cellRevealTicks, stageRevealMinTicks, stageRevealMaxTicks)`, the stage's cells
  spread evenly across it (16 §6.4's formula, verbatim). The playback's total time is bounded by the
  stage count, not the cell count.
- **`animation.speed` divides every span** (`ceil(span / speed)`), and `animation.enabled = false`
  reveals a stage, a step and a whole play at once with no fade. Neither key changes a cap, a count or
  an order (16 §13's law; the screen adds no config key of its own).
- **The stage framing is the cine core's, and it yields to the hand.** On a stage advance during a
  play, the main viewport flies to the current stage's framing — 16 §6.7.2's `stageShot` over the
  stage's own drawn cells, with 16 §6.7.3's 12-tick `inOutCubic` leg — while the viewer has not
  touched the camera since the play started. The first manual camera gesture disarms stage framing for
  the rest of that play-through: a camera that yanks itself back is a camera fighting the hand
  (16 §6.7.5's law, read for a screen). The three small viewports never follow a stage framing: they
  are axis-locked (§5.2).
- **The framing is a view.** It moves the camera value only; no cell, no fit, no table and no
  projection changes, and `Home` takes the camera back at any time.
- **The clock is advanced once per frame from the real-time delta** (§4.6), never from a render count:
  a frame that consumed no game tick still advances the screen's accumulator, which is exactly why
  the screen has one.

## 8. The exploded view (R9)

The exploded view separates the page's **parts**, and a part is a bay (§3.2): each bay's cells move
away from the structure's centre along the bay's own direction, the rest of the picture holds still,
and every bay's name is drawn where its part went.

### 8.1 The offset law

`ExplodeModel` is a pure function of (the stage table with its bays, the drawn cells, the hull) that
answers, for one cell and one `t` in [0, 1], a displacement:

1. **The groups.** One group per bay, holding every drawn cell of the bays' stages (§3.2 note: a bay
   with several layer stages is still one group).
2. **The centroids.** A group's centroid and the structure's centroid are the means of the group's
   and of all the drawn cells' centres, summed in the kernel's canonical cell order (`LatticePoint`'s
   Y, then Z, then X) — one summation order, one result.
3. **The direction.** `d = groupCentroid − structureCentroid`. When the two centroids are equal,
   component by component, the direction is exactly `+Y` — the degenerate case, not a near-degenerate
   one: a single-bay document has one group holding every cell, so its two centroids are the same sum
   by construction and `d` is exactly zero. No epsilon is needed and none is written (D-104). A group
   whose direction is collinear with another's is not special: two parts on opposite sides of the
   centre separate in opposite directions, which is what they should do.
4. **The distance.** `0.5 × diagonal(drawnHull)`, the same for every group, so the parts keep their
   relative layout and the picture grows by a half-diagonal instead of exploding to the horizon.
5. **The offset.** `offset(cell, t) = normalize(d) × distance × t`; a bay with no drawn cell
   contributes nothing, and every cell of a bay carries its bay's offset.
6. **`t` is animated, never stored.** A toggle starts a `Tween` from the current `t` to the other end
   over `explodeTicks` (12) with the cine core's `Ease.inOutCubic`, sampled at §4.6's clock
   (`cursor + fraction`); a second press reverses the same tween from wherever it is, so a toggle
   mid-flight never jumps. `animation.enabled = false` sets `t` at once and `animation.speed` divides
   the span (16 §6.4's law).

- **Nothing is separated when there is nothing to separate.** A page whose drawn cells belong to one
  bay has no second group: the explode button reports `one part` and `t` stays 0. §14 lists this as a
  document-authoring matter (the demo gives each part its own bay, §11).
- **Pure and testable.** The type reads the payload's cells and no world, no clock and no registry;
  a unit test at `t = 0`, `t = 1` and a mid-tween step compares the exact offsets for a known hull.
- **Zero server state.** The offsets are computed on the client from payload cells; no payload field,
  no session and no server code knows the view exists (D-104, D-95's law).

### 8.2 The labels

- **The text is the bay's name**: `bay.text()` — the same string 16 §4.1.7 gives a bay-mode stage.
  No document `description` or `hint` is shown here: those belong to the surfaces of 16 §10, and this
  is a name, not a sentence.
- **The anchor is the group's own centroid**, projected through each panel's camera after the group's
  offset, so a label sits on the part it names — the world billboard's anchoring rule (16 §10) applied
  to a moving part.
- **When.** Only while `t > 0`, with the label's alpha following `t` (a fade in and out with the
  movement), drawn after the models as GUI text with no depth test and clipped to the panel's rect.
  At `t = 0` there is no label: the whole view stays a picture.
- **A group with no drawn cell has no label**, because it has no anchor. A label wider than its panel
  is clipped, not wrapped, and v1 has no collision avoidance between labels — two parts whose
  centroids project onto the same point get two overlapping labels, which is a known v1 limit rather
  than a rule to be invented here.

### 8.3 Composition and the refit rule

- **The stage treatment still decides what is drawn.** A future cell is hidden by §7.2, so its part
  is drawn without it; the offset of a cell that is not drawn does not matter, and the screen does not
  offset a ghost it does not draw.
- **Picking follows the offset.** §5.3's ray test runs against the cells' current positions, so the
  cell under the cursor is the one drawn there (§4.3's refit rule and this one are the same
  requirement: what is drawn is what is picked).
- **The axes stay at the structure's origin.** The gizmo is the structure's frame (§6), not a part's;
  exploding moves parts away from the axes, which is exactly what makes the axes readable as the
  frame.
- **Arming the explode does not refit.** §4.3 keeps the camera where the viewer put it; a part that
  leaves the frame is brought back by `F` / double click (frame all fits the current, exploded hull).
  This is R11's frame-all doing R9's work, and it is why neither feature needs the other.
- **The whole view and the staged view both explode.** The explode is orthogonal to the stage
  presentation: a viewer can read the whole page apart, or watch stage 2 apart.

## 9. The links (R12)

Five links, each of which enters or leaves the screen through a channel that already exists: JEI's
face, a command, a world key, the open payload and the group's own member list.

### 9.1 The JEI entry point

- **One button in the category's face**: `open in screen`, beside the mode button and the existing
  `Ghost to world` (15 §6.2, 16 §11). It opens the screen on the **selected member's id** — the same
  selection the page's variant row and its `Ghost to world` already follow (15 §12.3) — with no packet
  and no session: the catalogue the page is drawn from is the catalogue the screen reads.
- **Nothing carries over.** The page's yaw, pitch, slice and mode are per-recipe view state
  (16 §11); the screen opens at its own default camera (§4.2). A camera is a viewer's, and the two
  surfaces are two viewers.
- **Closing lands in the world.** The screen always returns to the game (`setScreen(null)`), not to
  the JEI screen it was opened from: a restored JEI screen would be a stale instance holding a stale
  recipe table. JEI is one key away, and that is the whole cost.
- **JEI absent changes nothing here.** §10.3's law: neither the screen nor the scene names a JEI type,
  so the button exists only where a face exists and the screen exists either way.

### 9.2 The client command

- **`/cumulus preview screen <id>` is a client command**, registered through NeoForge's
  `RegisterClientCommandsEvent`: the client's own dispatcher executes it locally
  (`ClientCommandHandler.runCommand` runs the client tree first and hands an unknown command to the
  server), so no packet and no permission are involved, and a dedicated server never hears of it.
- **It is completing, not parsing.** The argument completes from `PreviewCatalogClient`'s ids, and an
  unknown id answers with §3.4's client message and opens nothing. Exit code 1 on opened and 0
  otherwise follows the adapter's command convention even though a client source ignores it.
- **One shadowing consequence, stated instead of hidden.** In the `preview` node the client's literal
  `screen` followed by an `<id>` wins over the server's `<id>` argument, so a blueprint whose
  unqualified id is literally `screen` must be typed with its namespace (`structurecumulus:screen`)
  from a client that has this pack. The bare form tells the truth about the mechanism: a client-side
  parse that ends right after the literal (`/cumulus preview screen` with no id) is not the client
  tree's command at all — brigadier reports the missing argument, NeoForge hands the input to the
  server as an unknown command, and the server reads `screen` as the `<id>` and answers that the
  document does not exist. Nothing opens and nothing starts a session, which is the safe half of the
  overlap; every other id is unaffected, and the server's own tree still has its `preview <id>`.
- **Why not a server command plus an S2C open payload.** A screen is a client view with client data;
  a server-initiated one would add a payload, a permission question and a server code path to open a
  window that the client can open for itself. This pack adds no S2C packet at all.

### 9.3 The world session key

- **One new binding: `openScreen`, default `O`.** It is a `KeyMapping` in the preview category, polled
  with every other preview action (16 §6.3's mechanism), and it does one thing: open the screen on the
  open session's blueprint id, client-side, with the session left exactly as it is behind the screen.
  The session is the screen's *source of an id*, not its data: the page comes from the catalogue.
- **`O` is free.** No preview action uses it, vanilla binds nothing important to it, and the SC3 uitest
  asserts the binding's uniqueness against the preview's own table (16 §6.7.6's conflict check, one
  action wider).
- **No session, no screen.** The key does nothing without an open session, and a session whose
  blueprint the catalogue left out (§3.3) answers with §3.4's message rather than an empty page.
- **The reverse link is not a key.** There is no screen key that opens a world session; the screen's
  one world action is the button that says `ghost to world` (§9.4). One direction per surface keeps
  the accidental cases out.

### 9.4 Ghost to world

- **It is 15 §6.3's channel, one field wider.** The button sends the existing `PreviewOpenPayload`
  with the page's blueprint id and — this pack's addition — `facing`: the direction the screen's
  camera looks (§3.2). The server takes the crosshair origin exactly as it does for the JEI button
  (15 §6.3) and hands `facing` where the player's own facing goes, so the screen's side is the
  session's **preferred** candidate: the cycle starts there and 15 §9's facing weight favours it, and
  the session's own lock and scoring still decide (a heading the blueprint cannot express changes
  nothing).
- **The heading is a cardinal reading of the camera.** The camera's horizontal look direction (from
  the eye toward the target) rounded to the nearest of the four horizons; a 45° look is an exact tie
  and the tie resolves to the earlier value in `Heading`'s declaration order (north, south, west,
  east), so the same camera always sends the same heading. A `up`/`down` value is never sent and reads
  as absent (§3.2).
- **The screen closes on the click.** The ghosts are in the world and the player is looking at them;
  a screen on top of them would be a wall in front of the only thing the button promised.
- **Nothing else changes.** The origin, the candidate cycle, the progress lock, the marks, the confirm
  and the executor are 15 §4 and 14 §10, untouched; the confirm after a ghost-to-world is the ordinary
  build (R12's "the in-world system does not move").

### 9.5 The variant switch

- **‹ variant / variant ›** moves the page to the group's previous / next member in declaration order,
  wrapping (`VariantSummary.members()`, 15 §12.1, D-98). A standalone page has no row: one member is
  not a group (`SC-DOC-0002`).
- **It is client-side and payload-free.** Every shipped member is already an entry in the catalogue
  (15 §12.3); the switch replaces the page's entry, rebuilds the scene and keeps the camera, the
  explode `t` and the whole/staged presentation, clamping the cursor to the new table. Nothing is
  re-opened, nothing is sent, and the group is not re-indexed on the server.
- **A member the catalogue left out is not switchable**, and the arrow answers with §3.4's message:
  15 §12.3's shortened-page law, without a page that pretends the member exists.
- **The title names the member** with 15 §12.2's own line — `variant <index>/<count>: <label>` — so the
  screen and the world HUD say the same words about the same fact. No new vocabulary is minted for the
  screen.

## 10. The package migration (D-102)

The scene core moves out of the JEI package and becomes a package of its own: the screen must work
with JEI absent (D-90's conditional law), and code two surfaces share cannot live in the optional
one. This is a public-shape change, so it is specified here and lands with SC1.

### 10.1 What moves

| Type | Today | Moves to | Why |
| --- | --- | --- | --- |
| `PreviewScene` (with its nested `Cell`, `LayerRule`, `View`, `Panel`) | `preview.jei` | `preview.scene` | a GUI-pass block renderer that names no JEI type; both surfaces draw through it |
| `BlueprintPreviewRecipe` | `preview.jei` | `preview.scene` | the page model: the shipped members, the selected index, the scene, the materials, the presentation |
| `PreviewViewMode` | `preview.jei` | `preview.scene` | the vocabulary of the scene's projections; the *selection* stays per-recipe view state (16 §11) |
| `CellStates` | `preview.client` | `preview.scene` | payload id + properties → `BlockState`; `PreviewScene` already carries a private duplicate of it, and the duplicate is deleted |
| `TintedBuffers` | `preview.client` | `preview.scene` | the forced-alpha pass the delta treatments of §7.2 need; one alpha mechanism for the world ghosts and the screen |

- **Raised to public where a second package consumes them.** Member-level signatures are not promised
  here: they join adapter §15 at the milestone that lands the code (SC1), as 16 §3.1 documented its
  shapes until E3's code existed. This section promises the types, their homes and their laws.
- **The page model moves; the per-surface view state does not.** The JEI page's yaw, pitch, slice and
  mode are the JEI face's own state (16 §11 says the mode is per-recipe view state), so they leave the
  shared recipe at SC1 rather than making a screen inherit another surface's camera. The screen's
  camera is `SceneCamera` (§4.2) and nothing else.
- **A move, not a rewrite.** 15 §6.2's rendering facts stay true of the scene in its new home — one
  block model per cell, the GUI pass's orthographic 3D path, far-to-near depth, `endBatch` instead of
  `GuiGraphics.flush()` — and the JEI face keeps drawing exactly what it drew before the move.
- **One direction of dependency.** `preview.jei` → `preview.scene` and `preview.client.screen` →
  `preview.scene`; neither consumer names the other, and `preview.scene` names neither.

### 10.2 The JEI shell

The JEI package keeps exactly the mezz.jei half, plus one button:

- `BlueprintPreviewCategory` — the face: the scene call, the slider, the material summary, the mode
  and variant rows and its buttons.
- `PreviewInputHandler` — the one input surface of the page (`IJeiInputHandler`).
- `JeiPreviewPlugin` / `JeiPreviewPlugins` — the conditional built-in plugin and its loader pass
  (D-90, adapter §17).
- `JeiPreview` — the public face the harness asserts on (listed ids, frame counters, `show`,
  `openWorld`, the variant selection, `openSelected`).
- `JeiPreviewClientEvents` — the JEI-side client wiring.
- **New:** one `open in screen` button (§9.1) that calls the screen's client entry point. It is the
  only new JEI-side code, and it is a button: no payload, no plugin, no second category.

### 10.3 The client-only law

- **Both packages are client-only, and the common side names neither.** Every type in
  `mc.preview.scene` and `mc.preview.client.screen` names a Minecraft client type; neither is ever
  loaded on a dedicated server. The only common-side additions of this pack are the two wire
  components of §3.2 and the one `KeyMapping` action of §9.3.
- **`scene` is renderer-neutral, not side-neutral.** The package's name says it draws no JEI type and
  serves any surface; it does not say it is the common side. Its two consumers are both physical-client
  code.
- **Neither package names a `mezz.jei` type**, so both load with JEI absent — a compile-time fact a
  name scan pins, and the reason `PreviewScene` could not stay where it was (§10.1).
- **No server code names the screen.** A screen is opened by the JEI face, the client command or the
  world key (§9), all of which are client code; there is no server-side "open a preview screen" verb,
  no S2C packet and no permission model for a window.
- **The isolation check keeps its teeth.** The existing dedicated-server runs must remain free of
  these classes (16 §15's law, adapter §17), and the SC1 uitest adds the name-scan assertion so a
  future `import mezz.jei` in the scene fails a gate instead of a launch.

## 11. The demo content (R1, D-105)

R1 is content, not code: two themes of real blueprint documents, shipped in the mod's own resources so
every player's catalogue carries them. Three documents, because the machine theme is a variant group
and a group of one is not a group (D-98, `SC-DOC-0002`).

| Id | Theme | Role | Shape |
| --- | --- | --- | --- |
| `structurecumulus:demo_machine` | the machine | member 1 of its group (label `full`) | the 5×5×4-ish machine: four bays, four stages, a direction-sensitive part, a full material set |
| `structurecumulus:demo_machine_compact` | the machine | member 2 (label `compact`) | the same group, a visibly different shape — a variant switch must change the picture, not only the item list |
| `structurecumulus:demo_tower` | the tower | standalone | the three-bay tower: three stages, a shell, a doorway the front law can see |

- **Where.** `src/main/resources/data/structurecumulus/cumulus/structure/demo_machine.json`,
  `.../demo_machine_compact.json` and `.../demo_tower.json` — the builtin datapack the adapter's
  `DatapackBlueprints` already scans (`data/<ns>/cumulus/structure/**`), so the documents load on a
  normal install, appear in every player's catalogue and are previewable by the ordinary command. This
  is the pack's **only** production-resource change (roadmap §2).
- **The marking law.** The id carries the `demo_` segment, and each document's root `description`
  opens by saying it is a demonstration document. Nothing else marks them: no new schema member, no
  code, no filter, no hidden flag, no separate source. The marking is therefore visible exactly where
  a description is displayed (16 §10), and a host that wants them gone removes the files, not a flag.
- **Authoring laws, each of which a screen feature depends on:**
  1. **Identity-expressible.** The catalogue keeps the identity projection only (15 §6.1), so a demo
     whose declarations cannot express the identity orientation would be absent from every page.
  2. **One bay per part.** The machine's four parts and the tower's three are four and three bays, so
     a stage is a bay and §8's exploded view separates them. A tower authored as one bay with three
     layers would have nothing to explode (§8.1).
  3. **A direction-sensitive part.** At least one block with a directional property, so the front law
     (15 §9) and the identity projection are exercised by the content itself.
  4. **The whole screen is exercised.** At least three stages, at least two bays, a non-trivial
     material set, and a group whose two members differ in shape — every screen feature has a document
     to be seen on.
- **The test law (SC4's gate).** A document compile unit test over the three shipped files: each
  compiles, its identity projection reports no filter, its bay and stage counts are the ones above, and
  the group's two members repeat the group declaration identically (`SC-DOC-0017`); a catalogue test
  that all three ids reach the built payload; and the uitest `demo` scene opens the screen on them.
  Counts and documentation are refreshed in the landing commit.

## 12. Codes

The pack allocates **no finding code and no code family.** Every failure it can produce is already
owned: a catalogue entry left out for a cap or a compile finding is 15 §6.1's WARNING log line, and a
stage table over `maxStages` is 16 §4.1.8's WARNING marker. A session that closes while the screen is
open produces nothing here: the screen never read the session, only its blueprint id (§3.1, §9.3).

The screen's one refusal — an id the catalogue does not carry (§3.4) — is a client message, not a
code: a missing page is a fact about one client's snapshot, and a code entry would be a finding with
no locus, no severity and no producer outside `preview.client.screen`. A milestone that believes it
needs a screen code has found a defect in this document and reports it.

The demo documents allocate nothing either: they are ordinary documents, and a malformed one is the
document module's own `SC-DOC-*` family (15 §12, D-98).

## 13. Determinism, isolation and what a reviewer attacks

- **The camera, the fit, the hull, the explode and the layout are pure functions.** `SceneCamera`'s
  operations, `fit`, `Hull`, `ExplodeModel.of` and `layout` read their arguments and nothing else; a
  test constructs each with known values and compares numbers, with no client, no world and no clock.
- **The clock is the screen's two numbers.** The accumulator and its fraction are the only time the
  screen has (§4.6); the same accumulator and the same fraction give the same frame, and nothing on
  the screen reads `System.currentTimeMillis` or a frame count.
- **No world read.** The screen names no `Level`, no `ClientLevel`, no `getBlockState` and no camera
  entity: its only inputs are the catalogue snapshot and its own view state. A reviewer rejects any
  `level` reference in `preview.scene` or `preview.client.screen`.
- **No server state.** No session, no map, no tick handler, no server-side stage, and exactly one
  packet in the pack's direction (the open payload's new field, §3.2) plus one field on the catalogue's
  stage record. Nothing else on the wire moves.
- **Client-only loading.** §10.3's law; the dedicated-server runs stay free of both packages.
- **No second renderer.** The scene pass is 15 §6.2's, moved not rewritten; the screen adds no
  `RenderLevelStageEvent` handler, no world-space geometry and no HUD layer.
- **Caps unchanged, truncation never silent.** The catalogue's caps (512 cells, 64 material lines,
  4096 cells, 15 §6.1), the session's caps (512 ghosts, 48 markers, 15 §3) and the stage cap
  (256, 16 §4.1.8) are untouched; the screen introduces no cap of its own because it introduces no
  list of its own (§3.2).
- **The camera never fights the hand, and no animation gates input.** A stage framing disarms on the
  first manual gesture (§7.5); every key, drag and button works during a play, a tween and an explode.
- **The delta is an emphasis, and a hidden cell is not picked.** What is drawn is what the ray test
  sees (§5.3, §8.3); the explode is a drawing offset, not a second copy of the scene.
- **The screen never guesses a page.** An id the snapshot does not carry opens nothing (§3.4); a
  vanished id closes the screen; a member the catalogue left out is not switchable (§9.5). A stale
  page would be a lie about the document source, which is the one thing this surface claims to show.
- **The screen is a pause, and says so.** `isPauseScreen()` stays the default (§4.6); a reviewer
  should reject a change that opts out of the pause to make an animation easier.
- **One camera, not four.** The four viewports share the value and derive their projections (§5.2); a
  per-panel camera, a per-panel slice or a promote-to-main would be four cameras in a trench coat and
  is not in v1.

## 14. Non-goals (v1)

A **session** on this screen: no candidates, no siting, no marks, no counters, no confirm and no
plan. The screen is not a second preview surface — it is the catalogue, and the world's session
stays the one place a build is planned (15 §4, 16 §2).

- A **Y slice** in the screen. The slice answers "what is on this layer" in a world (15 §5) and in the
  JEI grid (16 §11); the screen answers the spatial question with four viewports and a camera, and a
  second slice control would be a fifth projection. Backlog.
- A **material list, recipe or item count** on the screen: the JEI entry already lists materials
  (15 §6.2), and the stage summary line names the one item a stage asks for (§7.4).
- A **camera remembered across opens**, per-blueprint camera memory or camera presets: every open is
  the default isometric frame (§4.2). Backlog, and it would need a store the screen deliberately does
  not have (§3.4).
- **Exporting a view** as an image or a screenshot (16 §16's D.4), and any upload or share.
- **Editing** from the screen: no placing, removing, claiming, pinning or part cycling (15 §8's pins
  are a session's), and no way to reach the executor.
- A **catalogue browser**: no search, no filter, no list panel, no per-document tree. The id in the
  title is the entry, and `open in screen` / the command are the way in (15 §6.1's table is JEI's).
- **Returning to the JEI screen** on close (§9.1), or restoring a previous screen of any kind.
- A **second renderer integration** (EMI or another): the scene core is the seam a later renderer
  reuses; a second face reopens the question (15 §6.5).
- **Localising** the screen's vocabulary (16 §16's C.5): the button labels and the summary line keep
  the pack's literal English.
- A **second client config file** or a screen-only config key (16 §13's law): the screen obeys
  `animation.enabled` and `animation.speed` and adds none.
- **A display mechanism for "a small entry looks empty".** That reading of R5 is attributable to the
  frame (R11's frame-all, §4.4) and to the content (R1, §11) — §3.3 records it and this list keeps it
  out of the rendering law.
- **Part selection, multi-select, click-to-cycle, tooltips with document prose**: the screen shows a
  cell's block id, its properties and its stage's name (§5.3), and no `description` or `hint` (16 §10's
  display points do not include this screen).
- The screen's playback or camera **on the server**, a per-stage network event, a server-side
  "open the screen" verb (§9.2's rejection, §10.3's law).
- **Stage numbers or screen overlays in the world**: the screen is a screen (16 §16).
- Making the screen **pause differently** (`isPauseScreen() = false`) to borrow the game tick (§4.6's
  rejection).

## 15. Milestones

| Milestone | Sections |
| --- | --- |
| SC1 | §3 the data source and the carried facts (with the hull law applied to the JEI view as well), §4 the projection and the camera, §5 the four viewports, §6 the axes, §10 the package migration |
| SC2 | §7 the stage playback, §8 the exploded view |
| SC3 | §9 the links: the JEI button, the client command, the world key, ghost to world and the variant switch |
| SC4 | §11 the demo content, and the count and documentation close-out |

SC0 is this file; the gates are the milestone table of
[`docs/plan/screen-plan.md`](../plan/screen-plan.md), and this file owns the contracts they test.
