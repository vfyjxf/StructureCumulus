# 15. Preview: sessions, payloads, rendering, orientation ergonomics and preferences

> Status: approved, design first (P0), landed with milestones P3, P4 and P5 of
> [`docs/plan/autobuild-preview-plan.md`](../plan/autobuild-preview-plan.md). It covers roadmap §7 and
> §8 with the ergonomics convergence of `09-ergonomics-expansion.md` §11: the item numbers cited
> below (1.1, 3.2, 4.6, …) are that document's. The P8 backlog lives only in the plan file; 3.5 (a
> lossy-orientation back door) is explicitly rejected (D-88).

## 1. Problem

A build that begins blind is a bad build. The user needs to see what the blueprint expects, where
it would go, in which pose, and what is missing — before anything is written. The kernel already
produces the data (`Remedy.Preview` with an `OverlayModel`, D-85); the adapter owns the session,
the synchronization, the rendering and the ergonomics.

## 2. Position

- New packages: `dev.vfyjxf.cumulus.mc.preview` (server and network),
  `dev.vfyjxf.cumulus.mc.preview.client` (`Dist.CLIENT`) and `dev.vfyjxf.cumulus.mc.preview.jei`
  (conditionally loaded).
- **A preview never writes the world.** The only write path out of a session is `confirm`, which is
  the build executor flow of design 14 §10.
- The server holds the session and every deterministic decision; the client renders and sends
  control input. No candidate, score or mark is computed client-side.

## 3. Payloads

Two payloads synchronize a session. The shapes below are the wire-relevant facts; the codecs are
an implementation detail.

```java
package dev.vfyjxf.cumulus.mc.preview.net;

public record PreviewSyncPayload(NamespacedId blueprintId,
                                 java.util.List<SitingCandidate> candidates, int current,
                                 OverlayModel overlay,
                                 java.util.List<GhostCell> ghostCells,
                                 java.util.List<Marker> markers) { }

// One legal siting; its orientation already passed the expressibility filter (§7).
public record SitingCandidate(Siting siting, int score) { }

// A client render description of one expected cell: block id + serialized properties.
public record GhostCell(LatticePoint cell, NamespacedId block,
                        java.util.Map<String, String> properties) { }

public record PreviewControlPayload(Control control, @Nullable LatticePoint target) {
    public enum Control {
        turnCw, turnCcw, flip, nextOrigin, prevOrigin, reset, resetIdentity, cyclePart, recall, confirm,
        cancel
    }
}
```

- `candidates` is generated deterministically server-side; `current` indexes it. A control payload
  never carries a siting — the server recomputes and re-syncs. `target` names the clicked ghost cell
  for `cyclePart` only (2.1: the server resolves the bay from its own blueprint); `reset` and
  `resetIdentity` are the tap and the long press of `R` (§5, 3.7), and `recall` is the recall key of
  §4. The enum here is the P3 correction of the P0 draft, which omitted the three controls its own
  key table requires.
- `ghostCells` is the compressed, capped render description of the expected states (block id +
  properties, no block-entity data). The caps are code constants in v1 — 512 ghost cells and 48
  markers per payload — and an overflow adds a WARNING marker that names the shown and expected
  counts, so nothing is truncated silently; moving the caps into the server config is P8 backlog
  ([the plan](../plan/autobuild-preview-plan.md)).
- The presentation pack extends this shape once, at E1: a presentation block (the blueprint's carried
  description, its stage table and the frontier stage) and one stage index per ghost cell travel with
  the same payload, and the catalog entry carries both as well
  ([16](16-presentation.md) §5, D-93). The two payloads here remain the session's; no third session
  payload appears.
- A marker whose locus is not world-frame re-anchors at the overlay bounds on the client: no source
  span crosses the wire, and the client needs no document to render a label.
- The session payload and every catalog entry also carry the blueprint's variant summary — the group
  id and its members in declaration order, each with the label the declaration carried (§12). The
  document schema bounds the declaration (16 members, 128-character labels), so the summary is
  bounded before it reaches the wire and needs no cap of its own; an over-cap declaration is a
  compile finding, never a truncated list (D-98).

## 4. Sessions

A session is keyed by `(player, level)` and lives on the server.

- **Opening.** `/cumulus preview <id> [origin]`, the JEI "ghost to world" button (§6), or the
  anchor-block gesture. Without an origin the server ray-traces the crosshair block (with face) and
  takes the hit as the origin candidate (1.1). Right-clicking (or sneak-right-clicking) a formed
  anchor block infers the blueprint candidates and the siting from that cell — the
  place-controller-first flow (1.2). If exactly one candidate siting satisfies strictly more of the
  already-built cells than every other candidate, the session locks to it and offers no cycling
  (1.5).
- **Open flow.** Capture → check → solve (design 14 §10 steps 1–2) →
  `PreviewSyncPayload`.
- **Invalidation.** A world change inside the footprint (the M5a `LevelWatch` mark counter,
  adapter §4) invalidates the session with `SC-BLD-0008`; the server recomputes and re-syncs, or
  closes when no candidate survives.
- **Timeout.** A timeout retracts the ghosts but does not kill the session; the recall key (P)
  brings it back, and a `SC-BLD-0008` invalidation recalls automatically after the recompute (4.6).
- **Resume.** A refused or failed confirm leaves the plan in the session (5.1); the next `confirm`
  re-validates against a fresh capture without re-picking the siting, and the HUD says "can
  continue" once the shortage is resupplied (10.1).
- **Variant switch.** `nextVariant` (the `N` key) re-opens the session on the group's next member, in
  declaration order and wrapping, at the session's current origin (§12.2). The new blueprint id brings
  its own preference memory, so the siting is re-derived for that member; the old session closes and
  the new one opens through the ordinary open path. A member that cannot be opened at that origin
  closes the session and the message names the reason, like any failed open. A standalone blueprint
  answers with a note and keeps its session.
- **Confirm.** `confirm` runs the build flow of design 14 §10 with the session's current siting and
  item-source choice. `cancel` closes the session and writes nothing.

## 5. Client rendering

`mc.preview.client` renders the session. Zero world writes, ever.

- `GhostRenderer` draws the expected blocks as translucent models in `RenderLevelStageEvent` (after
  the translucent phase), resolving models through the `ModelManager`; alpha and depth behaviour
  are client config.
- `OverlayRenderer` draws the `CellMark` boxes and the `Marker` billboards.

Layered rendering (4.1): a `missing` cell gets the strong treatment (translucent model + box),
`wrong` a frame only, `extra` a red frame, `blocked` a purple frame, `unknown` a grey dot, and
`present` is not drawn by default; a key toggles "all / problems only". The Y-slice (4.2) raises
and lowers the current layer with PageUp/PageDown, fading other layers with layer distance — the
same mental model as the JEI slice (§6). Distance culling (4.8) renders only ghosts and frames
within reach plus a config radius; counters are not culled.

The presentation pack composes with this pass rather than replacing it ([16](16-presentation.md) §6):
the stage relation and the playback reveal multiply into the ghost alpha and the box treatment, the Y
slice stays a drawn/not-drawn filter, problems-only stays a mark filter, and distance culling is
unchanged. The Y-slice keys keep their meaning there; the stage keys are new.

| Key | Action |
| --- | --- |
| `[` / `]` | Turn CW / CCW (§7) |
| `;` | Flip |
| `,` / `.` | previous / next origin candidate |
| `R` | reset to the first candidate; long-press returns identity (3.7) |
| `P` | recall a retracted session (4.6) |
| PageUp / PageDown | Y-slice up / down (4.2) |
| `N` | next variant of the group; a switch is a re-open at the current origin (§12.2) |
| `K` | save this session's build context as a preset (§13) |
| Enter | confirm |
| Esc | cancel |

The presentation pack adds five keys beside these — `↑`/`↓` the stage, `V` play/pause, `B` one cell
step, `G` the guided mode of [16](16-presentation.md) §12 and `N` the next variant (§12.2). No key
above changes meaning, and no stage key reaches the server ([16](16-presentation.md) §6.3); `N` does
reach it, because a switch is a server-side re-open. E7 adds `K` beside them, and it reaches the
server for the same reason: the preset the session saves is server state (§13).

Every key is a NeoForge `KeyMapping` in the settings screen; the defaults avoid vanilla's
high-frequency keys, and full keyboard reach is required (8.1).

Guidance, not noise:

- A progress counter (`5/27`) and the next placeable group highlighted in plan dependency order
  (4.3; the hand-aware pulse is P8 backlog, and the presentation pack's guided mode is its home —
  [16](16-presentation.md) §12).
- A materials HUD listing shortage items with icon and count, naming the source container's
  position from the `ItemSource`; a creative source collapses the list to zero (4.4).
- Refusal visualisation (4.5): a dry-run `SC-BLD-0002`/`SC-BLD-0003` paints the problem into the
  world — foreign-claimed cells get a purple frame, missing items are red in the materials list.
- A `blocked` cell's tooltip names the claim's owner and structure id — it guides coordination and
  never offers a force-break (6.2; the foreign-exclusive refusal is the existing claim law).
- A failed build step pulses the failed cell red and shows one next-step suggestion taken from the
  finding's code entry, never hand-written prose (9.1).

## 6. The JEI preview (P4)

`mc.preview.jei` registers one "Multiblock Preview" category through a conditional built-in plugin
(D-90): only when JEI is present, through the existing `PluginLoader` discovery, ordering and
failure policy (adapter §7.4/§7.5). JEI absent means the category is simply not registered; a JEI
version incompatibility is a plugin load failure attributed by the existing policy and never takes
the mod down.

- Entries are every blueprint id of the merged document source (built-in plus datapack),
  generically generated — no preset scenes. The wire keeps one entry per blueprint id; the category
  shows one page per group, keyed by the first member the catalog ships in declaration order and
  switching among the shipped members in place (§12.3), so the pages a player sees never grow with
  the number of variants.
- The view is an isometric, drag-rotatable 3D rendering with a Y-slice slider and tooltips that
  summarize the expected blocks (id, properties, counts).
- A "Ghost to world" button opens an in-world session (§4) at the player's crosshair.

### 6.1 The catalog (server → client)

A JEI page has no player, world or siting, so a session's candidate machinery does not apply: the
entry is the blueprint's expected states under the **identity** orientation, resolved server-side
from the declarations exactly as a session resolves them, and shipped as one sync-time payload.

- **When.** The catalog is built on NeoForge's datapack-sync hook, which fires on a player's join
  and on `/reload` (before the recipes are sent). A datapack document that changes therefore reaches
  an open client without a reconnect, and nothing polls.
- **What.** One entry per blueprint that compiles: its id, its expected cells in the session's
  `GhostCell` shape (block id plus serialized properties), and its material lines (the item the block
  yields and how many expected cells ask for it). Structure sets are entries too — one composite
  entry per set, composed server-side into pure geometry ([18](18-set-preview.md) §3, a 2026-11-13
  reversal of this bullet's first reading). The presentation pack adds the same presentation block (§3, design 16 §5) —
  the carried description and the stage table — so the entry's text and stage grouping come from the
  one place a session's do ([16](16-presentation.md) §10, §11).
- **Caps.** Every cap is a code constant and none truncates silently, the same law as the session's
  payload caps (§3): a blueprint that does not compile, or whose projection exceeds 512 expected
  cells, or whose summary exceeds 64 distinct material items, or whose every expected cell is air and
  would draw nothing ([17](17-preview-screen.md) §3.3), is **left out of the catalog entirely**
  and named in a WARNING log line. A set entry's composite cap is 1024 cells over the same material
  and catalogue caps ([18](18-set-preview.md) §3). A partial scene or a partial material list would lie about the
  blueprint, so the entry is absent rather than wrong. One whole-catalog cap (4096 expected cells)
  keeps a single payload inside the network frame limit; the ids it leaves out are named the same
  way, in catalog order, so the same source always drops the same entries.
- **Client table.** The last catalog is a read-only client snapshot; it parses no document and
  recomputes no kernel decision. The JEI recipe table is seeded from it at `registerRecipes` and
  reconciled on every later push: a new id is `addRecipes`, a changed id updates the existing recipe
  instance in place, a vanished id is `hideRecipes` and a returning one is `unhideRecipes` — JEI has
  no removal, so a reload hides instead. Recipe identity is the instance, so a reload never
  duplicates a page; every recipe handed to JEI is tracked once.

### 6.2 The isometric view

`IRecipeCategory.draw` is handed the recipe's area with the pose already translated to its origin,
and the category owns the whole face: a scene, a slider, the material summary and the button. It is
**one of three view modes** once the presentation pack lands ([16](16-presentation.md) §11, D-96):
the isometric view stays the default and keeps everything below, the three-view and slice-grid modes
add two projections of the same cells, and the mode itself is per-recipe view state — like the yaw
and pitch below, never a payload field.

- **Camera.** Yaw and pitch are per-recipe view state; dragging the viewport rotates them, the pitch
  clamps just short of the poles, and the scale is fitted to the drawn cells' hull on every frame
  ([17](17-preview-screen.md) §3.3 — an entry carries no bounds, and a fit to them would draw the
  padding instead of the structure), so a 1×1×1 cell and a 40-block tower both fill the viewport.
- **Geometry.** One expected cell is one block model rendered by `BlockRenderDispatcher` at full
  brightness, resolved from the wire block id plus its property strings against the client's block
  registry (an id or value this client does not know leaves the property at its default). Rendering
  happens inside the GUI pass: Minecraft's GUI pass is an orthographic 3D pass with the depth buffer
  cleared at its start, which is the same path vanilla uses to draw a block model as an item icon.
- **Light.** The scene is lit by the world, not by the GUI's item frame: the pass installs the level
  pass's two world diffuse directions rotated into the view's own camera and the camera's rotation
  (with the GUI's y-down mirror) as the pass's normal matrix — the same law the preview screen keeps
  ([17](17-preview-screen.md) §4.1), so a face's brightness is a property of the face in JEI too.
  Vanilla's `Lighting.setupFor3DItems` pins the light to the inventory-item frame and would shade a
  face by the camera's accident — flat and shifting as the view is dragged, which is the defect SC8
  fixes; the JEI face used it until then.
- **Depth.** Cells are drawn far-to-near by their camera depth and the depth buffer resolves the
  opaque cubes; the batch is flushed with `BufferSource.endBatch()` and **not** `GuiGraphics.flush()`,
  whose contract temporarily disables the depth test around the batch and would drop the scene's
  occlusion.
- **Y slice.** The slider (and the wheel over the viewport) chooses how many layers from the
  blueprint's bottom are shown — the same mental model as the in-world slice (§5, 4.2). A one-layer
  blueprint pins the slider; a hidden layer is not drawn and not picked.
- **Tooltip.** The cursor is unprojected into the scene's orthographic view; the nearest visible cell
  whose box the ray crosses names its block id and each of its property values. The material list's
  icons answer with the item name and count.
- **Variants.** A page whose group ships more than one member draws a variant row under the mode
  button: the arrows switch the member in place, the row names the selected label, and the scene, the
  materials, the tooltip and "Ghost to world" follow the selection. The selection is per-recipe view
  state, like the yaw and the slice, and a reload keeps it while the selected member still ships
  (§12.3).
- **Bounds.** The fitted scale keeps every projected corner inside the viewport, so the scene never
  paints over a neighbouring recipe.

### 6.3 "Ghost to world"

The button sends one C2S payload carrying only the blueprint id; the server opens the ordinary
session of §4 with the crosshair origin (1.1) and the crosshair face's neighbour as the second
candidate. There is no second session mechanism: a preview opened from JEI, from the command or from
the anchor gesture is the same session object, and the same confirm path (§4) leaves it.

### 6.4 Failure isolation

- **JEI absent.** The client setup pass checks the mod list before resolving any JEI type; nothing
  is scanned, registered or loaded.
- **JEI incompatible.** The built-in plugin is also a `ModPlugin`, so the adapter's loader pass
  reports its id, dependencies and failures through `PluginLoader` (fatal failures logged, never
  thrown, D-81); JEI's own plugin finder logs the class it cannot link and keeps loading the rest.
  Either way the mod keeps running, without the category.
- **Dedicated server.** The package is client-only: its event subscriber is `Dist.CLIENT`, its
  payload handlers resolve client classes only inside their enqueued work (adapter §17), and the
  catalog payload itself is plain data any client can receive.

### 6.5 The GUI-plugin evaluation (P7)

A GUI-integration plugin subtype (`GuiPlugin`, the shape a second renderer such as EMI was supposed
to copy) was P7's evaluation item; the ruling is **not to introduce it in v1**. There is one GUI
integration, its interface is JEI's own `IModPlugin` rather than a renderer-neutral contract, and
`ModPlugin` already supplies what a subtype would carry — the id, the dependency edges and the
loader's failure attribution — so a `GuiPlugin` with one implementer and no second consumer would
add a public type and no behaviour. P4's conditional built-in stays one `@JeiPlugin` +
`@PluginMarker` class, and there is no public registrar for GUI contributions; the P7 extension
points are the build tables (adapter §7.5/§7.6). A second renderer re-opens the question — its own
plugin API and its own discovery pass are the parts that differ, not the subtype.

## 7. Orientation ergonomics

The base four laws, then the v1 convergence (expansion §3):

1. **Expressibility filter.** Candidate orientations are enumerated and each is simulated through
   materialization; any orientation with one `Unrepresentable` cell leaves the cycle — the user
   never previews a pose that cannot be built — and one marker records the filter. The HUD notes
   how many orientations were filtered and for which block, inspectably (3.4). There is no lossy
   back door (3.5, D-88).
2. **Default orientation.** An anchored blueprint follows the existing anchor law (adapter §10/§11);
   an anchorless one faces its door focus toward the player when it declares a door; otherwise the
   identity orientation starts and the highest-scored candidate comes first.
3. **Cycle set.** The upright `Turn`×`Flip` set (4×2) cycles first; non-upright `Tilt` poses enter
   the cycle only when the blueprint declares support for them.
4. **Memory.** The remembered choice (§9) leads the candidate order.

Convergence additions:

- **Snap (3.1).** Default candidates snap to the four upright Turns of the player's horizontal
  facing; sneaking releases the cycle to the full expressible set.
- **Support (3.2).** `PlacementWeight.SUPPORT` scores a siting whose footprint bottom is supported
  and penalizes a floating one; a blueprint that declares hanging (inverted) support inverts the
  weight.
- **Flip memory (3.6).** The remembered choice carries the flip state and the next session opens
  with it.

## 8. Part pins

A part is a bay or focus id with several legal cells (a port strip, an interface face). A pin is
the user's per-part pick (2.1): right-clicking a ghost part cycles it within its legal set (drag is
the same gesture). Pins reach the kernel through `SolvePreferences.partPins` (`remedy` §5, D-86);
the kernel law — pins select only inside the legal set, an illegal pin is ignored and annotated, a
conflict resolves user pin > memory > preference score > canonical order, and the displaced part is
offered as a sibling remedy — is kernel law, not presentation.

The presentation half is adapter law (2.2): a displaced part is marked "yielded" in the HUD and its
sibling remedy stays one click away; a conflict is never silent. Per-part memory is keyed
`(player, blueprintId, partId)` (2.4): the same part id in another blueprint does not migrate —
the profile-based explicit migration is P8 backlog (7.3).

## 9. Preferences and memory

New package `dev.vfyjxf.cumulus.mc.pref`.

```java
package dev.vfyjxf.cumulus.mc.pref;

public record BuildPreferences(
    OrientationMode orientationMode,
    @Nullable Heading fixedHeading,
    RemovalPolicy removalPolicy,
    java.util.List<PlacementWeight> weights,
    java.util.Map<NamespacedId, ChoiceHistory> remembered
) {
    public BuildPreferences { /* immutable; remembered is a bounded LRU, capacity 64 */ }
}

public enum OrientationMode { autoAnchor, facePlayer, fixedHeading }
public enum RemovalPolicy { forbid, ask, allow } // ask allows removal only with --allow-removal.
public enum PlacementWeight { facingPlayer, fewestRemovals, mostProgress,
                              nearestToPlayer, remembered, support, align /* P8 backlog */ }

// The frequency-weighted memory (7.1): the last eight choices, mode wins, ties break recent.
public record ChoiceHistory(java.util.List<Siting> recent) {
    public ChoiceHistory { /* most recent last; capacity 8 */ }
    public @Nullable Siting preferred();
}
```

- **Scoring.** `SitingCandidateScorer` sums the ordered weights over the enumerated candidates and
  ranks by descending score; a tie falls back to the candidate's canonical enumeration index, so an
  unchanged input ranks identically and even a scrambled input list ranks identically.
  `mostProgress` and `fewestRemovals` read the live cells under the pose (already satisfied; occupied
  but wrong); `remembered` outranks every other term. The magnitudes are adapter constants:

  | Weight | Term |
  | --- | --- |
  | `facingPlayer` | `+100` when the pose's front heading is the player's horizontal facing |
  | `fewestRemovals` | `-10` per cell the pose has to clear first |
  | `mostProgress` | `+10` per cell the pose already satisfies |
  | `nearestToPlayer` | `-10` per block of horizontal distance, capped at 1000 blocks |
  | `remembered` | `+1_000_000` when the pose is the mode of the blueprint's history |
  | `support` | the percentage of the pose's support face that has a neighbour |
  | `align` | declared for P8; it contributes nothing yet |

- **The front heading.** A pose's front is its declaration frame's front side carried by the
  orientation. A blueprint that declares a door (a check whose focus subject is `DoorView`) puts its
  front on the side its door cells sit on; otherwise the front is the identity `-Z` face. This is the
  same convention the turn snap uses (§7), so the facing weight and the snap agree, and no kernel
  focus-id port is needed.
- **Support face (3.2).** `support` measures the world underside of the sited bounds by default and
  the world top side when the blueprint declares an inverted pose (`OrientationPolicy.allowed()`
  contains an `inverted` tilt): a hanging structure is scored on its ceiling side, not with a
  negated weight.
- **Progress lock (1.5).** When exactly one pose satisfies strictly more built cells than every other
  candidate, the session opens locked to it, says so in the HUD, and refuses every cycling control
  (`turn`, `flip`, `origin`, `reset`, `resetIdentity`). The lock is recomputed on each recompute, so a
  world change can release it.
- **Memory.** The remembered history replaces single-value memory (7.1): each key keeps the last
  eight choices, the mode wins, ties break to the most recent; both the single-value and the mode
  rule are deterministic. The remembered value is the whole `Siting`, so the flip and the turn of the
  remembered choice come back with it (3.6). Writing is implicit (7.2): a confirm that forms the
  structure writes the siting and a part cycle writes the part pin; there is no explicit "save".
  A confirm that applies one repair without forming the structure keeps the plan in the session, so
  the next confirm continues it (5.1). `/cumulus prefs
  forget` clears. Per-part memory is the `(player, blueprintId, partId)` key of §8 and never migrates
  to another blueprint.
- **Persistence.** `PreferencesSavedData` is a per-level `SavedData` (file id
  `structurecumulus_preferences`) keyed by player UUID, warmed by `ServerState.openLevel` at
  `LevelEvent.Load` and read through `level.getDataStorage()` on every access, so nothing holds a
  store across an unload (adapter §18). The file carries a format version; an unknown version is
  dropped with a WARNING rather than guessed at. The store keeps the explicit settings apart from the
  memory, so a config default that changes later still reaches a player who never ran `set`. The
  blueprint LRU capacity and the part-pin capacity are one number,
  `rememberedCapacity` (default 64) in `config/structurecumulus-preferences.toml`; the same file
  supplies `defaults.orientationMode`, `defaults.fixedHeading`, `defaults.removalPolicy` and
  `defaults.weights` (the weight list uses the snake_case spelling; a value is read either way).
- **Command.** `/cumulus prefs` views the effective preferences, `show` does the same explicitly,
  `set <key> <value>` writes one setting with `key` in `orientation | heading | removal | weights | all`
  (values are `auto|face|fixed`, a heading or `none`, `forbid|ask|allow`, a comma-ordered weight
  list or `default`, and `all` resets every setting to the config), and `forget [id]` clears the
  memory of one blueprint or of all of them. Exit code 1 on success, 0 on an unknown key or value or
  without a player.
- **Kernel bridge.** The only preference value that crosses into the kernel is `SolvePreferences`
  (D-86): cell-level ordering enters `RemedyContext`; siting-level ordering stays in the adapter —
  the kernel does not know players. `SolvePreferenceAssembler` maps the current pose's still-needed
  declaration cells (canonical order, capped at 64) to world-frame cell preferences and the live part
  pins plus the remembered part pins to world-frame box pins; a pin the siting cannot place is
  dropped, so the adapter never invents a cell. The player's `RemovalPolicy` narrows or widens the
  server gate (`FORBID` forces removal off, `ASK` leaves the server gate, `ALLOW` lifts it); it never
  turns a plan into one the policy would otherwise refuse on another ground.

## 10. Codes

A session invalidation is `SC-BLD-0008` (design 14 §14). Preview allocates no code family of its
own; everything else it shows is a finding the check, solve or build already produced. The
presentation pack allocates none either: its stage, HUD and animation surfaces show marks, names and
document text, and its cap overflows stay markers ([16](16-presentation.md) §14). E6's variant
validity codes are the `document` module's own (`SC-DOC-0016`/`SC-DOC-0017`, document §4.7), not a
presentation code.

## 11. Non-goals (v1)

Preset scenes or hand-authored preview content; web `visual/` tooling changes; multiplayer
co-editing of one plan; lossy orientation fallbacks (3.5); the P8 backlog listed in
[`docs/plan/autobuild-preview-plan.md`](../plan/autobuild-preview-plan.md). A parameterized document
language, per-instance variant parameters and variant groups that are not static documents stay
backlog (§12, D-98). The presentation pack's own non-goals are [16](16-presentation.md) §16; the two
lists do not overlap.

## 12. Variant groups (E6, light)

A variant group is a static combination of blueprint documents: independent documents that compile on
their own, declared in one group by the document `variants` block ([document](../modules/document.md)
§4.7, D-98). There is no parameterized document language and no scene generator; the group adds a
*switch* between documents the source already has.

### 12.1 The carry

The declaration is parsed into the compiled value — `Blueprint.variants()`: the group id and the
members in declaration order, each with the label the declaration carried — and travels on the session
payload and on every catalog entry as one summary beside the presentation block (design 16 §5):

```java
package dev.vfyjxf.cumulus.mc.preview.net;

// The group a session or a catalog entry belongs to; absent for a standalone blueprint. Bounded by
// the document schema (16 members, 128-character labels), so it needs no wire cap of its own.
public record VariantSummary(NamespacedId group, java.util.List<Member> members) {
    public @Nullable Member member(NamespacedId id);
    public int indexOf(NamespacedId id);

    public record Member(NamespacedId id, @Nullable String label) {
        public String text();   // the label, or the id when the declaration carried none
    }
}
```

The kernel never enumerates, orders, formats or switches the group; the summary is carried text-like
data and part of the compiled blueprint's equality (D-94, D-98). The declaration's bounds are compile
findings (document §4.7), so the summary is bounded before it reaches the wire and the payload codecs
need no variant cap of their own.

### 12.2 The switch (in-world)

`N` cycles the session to the next member of its group, in declaration order, wrapping. A switch is
the ordinary open of the new blueprint id at the session's current origin: the siting is re-derived
for that id, its own preference memory and part pins apply (they are keyed by blueprint id), and the
previous session is closed. Origin semantics are preserved — the user's chosen cell does not move —
while everything remembered per blueprint stays with the blueprint. A member whose pose is not
expressible at that origin closes the session with the open's own failure message, exactly like any
failed open (§4). A standalone blueprint answers with a note and keeps its session. The HUD names the
current member: `variant <index>/<count>: <label> · group <id>`.

### 12.3 The switch (JEI)

The category lists one page per group, keyed by the first member the catalog ships in declaration
order; the group's other members are not pages, so the entries a player pages through never grow with
the number of variants. The page's variant row switches left/right in place, and the page's scene,
materials, tooltip lines and "Ghost to world" target follow the selected member. The selection is
per-recipe view state, never a payload field; a reload keeps it while the selected member still ships.

The catalog payload still carries one entry per blueprint id — every member is an ordinary document
with its own cells and materials — so the per-entry and whole-catalog caps keep their meaning. A
member left out by a cap is not switchable from the page, and the catalog's WARNING log names it like
every other left-out entry, so a shortened page is never silent.

### 12.4 The build pass-through

"Ghost to world" opens the session on the *selected* member's id, and the in-world confirm hands the
session's own blueprint id to the build executor. The executor, its policy gate, its item source and
its report are unchanged: a variant changes which declaration is built, never how (§4, design 14 §10).
A confirm that forms the selected member writes that member's id into the orientation memory, so the
next session of that id starts where the last one ended (§9).


## 13. Build presets (E7)

A **build preset** is one saved build context: the blueprint id, the pose, the item-source form and the
removal policy a preview session held. It is the "save this preview and build it later" half of the
autobuild story, and it is explicit user data, not memory.

```java
package dev.vfyjxf.cumulus.mc.pref;

public record BuildPreset(
    String name,
    NamespacedId blueprintId,
    Siting siting,
    BuildPreset.Source source,
    RemovalPolicy removalPolicy
) {
    public static final int formatVersion = 1;
    public static final int capacity = 32;
    public static final int maxNameLength = 32;

    public record Source(Kind kind, @Nullable LatticePoint container, @Nullable NamespacedId provider) {
        public enum Kind { creative, player, container, registered }
    }
}
```

The pose is stored, not re-derived: `siting` carries the origin and the placement (orientation and
offset), so a preset is the whole `/cumulus build <id> <origin> …` request, and `apply` and
`preview <name>` read the same pose. The source is stored as the build command's own four forms, not
as a resolved `ItemSource`: a `container <pos>` or a registered kind id is resolved against the
applying player's level at apply time, so a moved container or an uninstalled provider refuses instead
of building from somewhere else. A variant member is its own document id (§12.4, D-98), so
`blueprintId` covers the variant choice.

### 13.1 Storage

`PreferencesSavedData` (adapter §18) keeps the presets in the same per-level, per-player row as the
preferences: `"Presets"` is a list of preset tags, sorted by name, each carrying its own `"Version"`.
A tag of an unknown preset-shape version, or a malformed one, is skipped and the row's other presets
survive. The file's `Version` covers the row layout; the preset's own version covers the preset shape.

**Explicit data, not memory.** The remembered siting choices and the part pins are an implicit bounded
LRU (`rememberedCapacity`, default 64): a new choice evicts the oldest. A preset is the user's own
artifact and is never evicted: the bound is `capacity = 32` per player, the next new name is refused on
a full store with the delete verb named, and a known name is replaced in place. `/cumulus prefs forget`
clears the memory and the pins only; it never deletes a preset.

### 13.2 Saving

Two entry points, one shape:

- `/cumulus build preset save <name> [with creative|player|container <pos>|<kind-id>]` copies the
  player's live session — its blueprint id, its current pose, its removal policy — and the session's
  own source form unless `with` names one. No active session is refused with the hint to open one; the
  `with` form resolves through the build command's own source path, so its refusals match a build's.
- **`K` in a session** (`PreviewControlPayload.Control.savePreset`). The key carries no text input, so
  the name is the first free default, `preset-1`, `preset-2`, …; a player who wants a chosen name uses
  the command. The session HUD carries the hint line and answers with the saved name or the capacity
  refusal; the session itself is unchanged, so a refusal costs no plan. A chat-input naming flow is
  backlog.

### 13.3 Applying and previewing

- `/cumulus build preset apply <name>` (`run` is the same verb) resolves the stored form, compiles the
  stored blueprint and runs `BuildExecutor` on the stored pose. **Applying a preset is exactly the full
  command typed by hand** (D-99): the policy is the server's `BuildConfig.policy()` narrowed or widened
  by the preset's recorded `RemovalPolicy` (the same mapping the confirm path uses), the executor's dry
  validation runs, and a changed world, a missing item, a foreign claim or a removal the policy forbids
  refuses the run and writes nothing. Nothing is ever blind-written from a preset. The one difference
  from typing the line is the pose: a typed command lets an anchored blueprint resolve its siting from
  the formed anchor at the origin, while a preset carries the pose the session resolved at save time and
  hands it over unchanged — the store's pose is the request the user saved, and the executor's
  validation, not a second anchor read, is what decides whether it still applies.
- `/cumulus build preset preview <name>` opens the ordinary session pinned to the stored pose and the
  stored source form, so the preview shows what `apply` would build. The session is a single-pose
  cycle: the cycling controls answer with a note, `confirm` is the ordinary build, and a variant switch
  is still the ordinary re-open at the origin — a switch is a deliberate departure from the preset's
  document, and the new member brings its own memory and pose (§12.2).
- `/cumulus build preset list` prints the player's presets, sorted by name, with the pose and source
  each apply would use, and the `n/32` count; `/cumulus build preset delete <name>` removes one. Exit
  codes follow the adapter convention: `save`, `preview`, `list` and `delete` answer 1 when they did
  their work, and `apply` answers 1 only on a committed run.
- Name completion suggests the player's stored names; the `with` kind-id completion suggests the
  registered item sources.

The command shape is `build preset` rather than a new root verb because an apply *is* a build: one
executor, one report, one policy gate, and the same chat summary line (`build <id> at <origin>: …`).

