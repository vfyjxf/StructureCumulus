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
- **A preview never writes the world.** The only write path out of a session is CONFIRM, which is
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

public record PreviewControlPayload(Control control) {
    public enum Control { TURN_CW, TURN_CCW, FLIP, NEXT_ORIGIN, PREV_ORIGIN, CONFIRM, CANCEL }
}
```

- `candidates` is generated deterministically server-side; `current` indexes it. A control payload
  never carries a siting — the server recomputes and re-syncs.
- `ghostCells` is the compressed, capped render description of the expected states (block id +
  properties, no block-entity data); the cap is a server config and overflow is reported in the
  HUD, never silently truncated.

## 4. Sessions

A session is keyed by `(player, level)` and lives on the server.

- **Opening.** `/cumulus preview <id> [origin]`, the JEI "ghost to world" button (§6), or the
  anchor-block gesture. Without an origin the server ray-traces the crosshair block (with face) and
  takes the hit as the origin candidate (1.1). Right-clicking (or sneak-right-clicking) a formed
  anchor block infers the blueprint candidates and the siting from that cell — the
  place-controller-first flow (1.2). If
  the already-built cells are consistent with exactly one candidate siting, the session locks to it
  and offers no cycling (1.5).
- **Open flow.** Capture → check → solve (design 14 §10 steps 1–2) →
  `PreviewSyncPayload`.
- **Invalidation.** A world change inside the footprint (the M5a `LevelWatch` mark counter,
  adapter §4) invalidates the session with `SC-BLD-0008`; the server recomputes and re-syncs, or
  closes when no candidate survives.
- **Timeout.** A timeout retracts the ghosts but does not kill the session; the recall key (P)
  brings it back, and a `SC-BLD-0008` invalidation recalls automatically after the recompute (4.6).
- **Resume.** A refused or failed confirm leaves the plan in the session (5.1); the next CONFIRM
  re-validates against a fresh capture without re-picking the siting, and the HUD says "can
  continue" once the shortage is resupplied (10.1).
- **Confirm.** CONFIRM runs the build flow of design 14 §10 with the session's current siting and
  item-source choice. CANCEL closes the session and writes nothing.

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

public enum OrientationMode { AUTO_ANCHOR, FACE_PLAYER, FIXED_HEADING }
public enum RemovalPolicy { FORBID, ASK, ALLOW } // ASK allows removal only with --allow-removal.
public enum PlacementWeight { FACING_PLAYER, FEWEST_REMOVALS, MOST_PROGRESS,
                              NEAREST_TO_PLAYER, REMEMBERED, SUPPORT, ALIGN /* P8 backlog */ }

// The frequency-weighted memory (7.1): the last eight choices, mode wins, ties break recent.
public record ChoiceHistory(java.util.List<Siting> recent) {
    public ChoiceHistory { /* most recent last; capacity 8 */ }
    public @Nullable Siting preferred();
}
```

- **Scoring.** `SitingCandidateScorer` sums the ordered weights over the enumerated candidates;
  `MOST_PROGRESS` counts the cells already satisfied under a siting, read from the dry-run capture
  (never a second world walk); `REMEMBERED` pins the remembered choice to the top. Ties fall back
  to the canonical order — scoring is deterministic.
- **Memory.** The remembered history replaces single-value memory (7.1): each key keeps the last
  eight choices, the mode wins, ties break to the most recent; both the single-value and the mode
  rule are deterministic. Writing is implicit (7.2): confirming a siting or pinning a part is the
  memory write; there is no explicit "save". `/cumulus prefs forget` clears.
- **Persistence.** `PreferencesSavedData` is a per-level `SavedData` keyed by player UUID with the
  claims lifecycle law (adapter §5); the server config supplies the defaults. The store also keeps
  the per-part pin memories of §8.
- **Command.** `/cumulus prefs` views, sets and clears preferences and memories.
- **Kernel bridge.** The only preference value that crosses into the kernel is `SolvePreferences`
  (D-86): cell-level ordering enters `RemedyContext`; siting-level ordering stays in the adapter —
  the kernel does not know players.

## 10. Codes

A session invalidation is `SC-BLD-0008` (design 14 §14). Preview allocates no code family of its
own; everything else it shows is a finding the check, solve or build already produced.

## 11. Non-goals (v1)

Preset scenes or hand-authored preview content; web `visual/` tooling changes; multiplayer
co-editing of one plan; lossy orientation fallbacks (3.5); the P8 backlog listed in
[`docs/plan/autobuild-preview-plan.md`](../plan/autobuild-preview-plan.md).
