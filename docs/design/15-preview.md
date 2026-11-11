# 15. Preview: sessions, payloads, rendering, orientation ergonomics and preferences

> Status: approved, design first (P0). The code lands with milestones P3, P4 and P5 of
> [`docs/plan/autobuild-preview-plan.md`](../plan/autobuild-preview-plan.md); until then this
> document is specified, not implemented. It covers roadmap §7 and §8 with the ergonomics
> convergence of `09-ergonomics-expansion.md` §11: the item numbers cited below (1.1, 3.2, 4.6, …)
> are that document's. The P8 backlog lives only in the plan file; 3.5 (a lossy-orientation back
> door) is explicitly rejected (D-88).

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
- A marker whose locus is not world-frame re-anchors at the overlay bounds on the client: no source
  span crosses the wire, and the client needs no document to render a label.

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

| Key | Action |
| --- | --- |
| `[` / `]` | Turn CW / CCW (§7) |
| `;` | Flip |
| `,` / `.` | previous / next origin candidate |
| `R` | reset to the first candidate; long-press returns identity (3.7) |
| `P` | recall a retracted session (4.6) |
| PageUp / PageDown | Y-slice up / down (4.2) |
| Enter | confirm |
| Esc | cancel |

Every key is a NeoForge `KeyMapping` in the settings screen; the defaults avoid vanilla's
high-frequency keys, and full keyboard reach is required (8.1).

Guidance, not noise:

- A progress counter (`5/27`) and the next placeable group highlighted in plan dependency order
  (4.3; the hand-aware pulse is P8 backlog).
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
  generically generated — no preset scenes.
- The view is an isometric, drag-rotatable 3D rendering with a Y-slice slider and tooltips that
  summarize the expected blocks (id, properties, counts).
- A "Ghost to world" button opens an in-world session (§4) at the player's crosshair.

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
own; everything else it shows is a finding the check, solve or build already produced.

## 11. Non-goals (v1)

Preset scenes or hand-authored preview content; web `visual/` tooling changes; multiplayer
co-editing of one plan; lossy orientation fallbacks (3.5); the P8 backlog listed in
[`docs/plan/autobuild-preview-plan.md`](../plan/autobuild-preview-plan.md).
