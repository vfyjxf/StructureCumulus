# 18. The set preview: the composite scene, the constraint overlay and the member drill-down

> Status: the contract for SC12. Supersedes one sentence of [15](15-preview.md) §6.1 ("structure sets
> are not entries") and the matching refusal in D-111 — the viewer asked to watch a set's satellites
> and constraints in the preview (2026-11-13), and the docs follow the viewer. Everything not named
> here keeps [17](17-preview-screen.md)'s laws: the set page is one more page over the same scene,
> camera, clock, playback and chrome.

## 1. Problem

A structure set is the model's composition answer (09): a root blueprint plus satellites at relative
placements and orientations, with required flags, presence probes and link constraints. Until now no
preview surface could show one: the catalogue carried blueprints only, so the set's own story —
*what stands where, what must exist, how far apart the parts may be* — was readable only in JSON.
The viewer's ask is to watch exactly that, in the same room as the blueprint pages.

## 2. Position

The catalogue gains a second entry kind, the **set entry**, composed server-side into pure geometry;
the screen gains the **set page**, a composite scene of every member at its composed siting, with
the set's constraints drawn as chrome and a drill-down into each member's own blueprint page. The
JEI face lists set entries beside blueprint entries and opens the same page. No world read, no
session, no edit: the set page is a catalogue view, and every screen law that holds for a blueprint
page holds here — zero state, hold→batch→dwell, drop/rise, four viewports, one clock.

## 3. The catalogue's set entries

- **Composition is the server's.** For each member, the server resolves the member blueprint's
  expected cells under the identity orientation exactly as 15 §6.1 does, then applies the member's
  placement — the kernel's `Placement`/`Siting` compose, orientation mapping included — and ships
  the *composed* cells. The wire carries pure geometry: no client ever re-derives an orientation or
  an offset, which keeps the screen's no-kernel law intact.
- **One entry per set that compiles and validates.** A set that fails `StructureSet.validate` is
  left out with 15 §6.1's WARNING line, never partially entered.
- **What the entry carries.** The set id; per member: its blueprint id, its label (the satellite id
  or `root`), its required flag, its composed cells, its presence probes (cell/boxes), and its links
  (mode, parent cell, satellite cell, max distance, metric). The entry is a trailing-optional
  addition to the catalogue payload (the heading precedent, SC3): an older client reads the
  blueprint entries and never sees the sets.
- **Caps.** A set entry's composite expected cells cap at **1024** (a code constant, double the
  blueprint band because a campus is bigger than a machine), its material union at 64 lines, and the
  whole-catalogue cap of 4096 expected cells is unchanged and counts set entries — an entry that
  crosses its cap is absent with the WARNING line, in catalog order, never truncated. A member whose
  own blueprint entry is absent for a cap still composes into its set entries: the set's scene is
  resolved from the document, not from the member's entry.

## 4. The set page

- **The composite scene.** Every member's composed cells draw in the one scene, the hull and fit are
  the composite's, and §4.7's slab platform sits under the whole campus. A member's cells know their
  member; nothing else distinguishes them.
- **Members are the parts.** The explode separates *members* — direction = member centroid minus set
  centroid, §8.1's law with the member standing in for the bay — and the labels name the member ids.
- **Stages are the members.** The stage table is root first, then satellites in declaration order —
  the same order `set build` walks them — so a play narrates the set the way the host builds it, and
  the summary line names the member and its cell count. A member's internal stages live on its own
  page, one drill-down away.
- **Everything else is unchanged.** The zero state, the reveal directions, the camera, the
  projection toggle, the hidden/outline treatment and the variant row (a member group's variants
  belong to the member's page, not the set's) all behave as on a blueprint page.

## 5. The constraint overlay

A set page adds one chrome layer, toggled by a `constraints` button (on by default — it is the
page's point), view state, dies with the screen:

- **Links are lines.** Each link draws a stroke between its parent cell and its satellite cell,
  labelled with the distance and the max distance (`actual ≤ max` reads at a glance); the metric
  names itself in the tooltip. A face-mode link's stroke meets the two faces, not the cell centres.
- **Required and optional.** An optional satellite's cells carry a dashed silhouette instead of the
  solid one; required members are bare. Presence probes mark their cells with the seam marker's
  chrome (15 §5's palette is untouched — these are document facts, not world findings).
- **No marks from the world.** The page shows the set as declared: nothing here reads a world, so
  nothing is green or red. Whether the built campus satisfies a link is a question for
  `/cumulus set check`, and the page says so in the footer line rather than pretending.

## 6. The drill-down

A click on a member's cell (or its explode label) opens that member's blueprint page. A
**breadcrumb button** — `‹ <set id>` — returns to the set page with its camera and stage kept. The
breadcrumb is one deep, is view state, and dies with the screen: the set page remembers where it
was, and nothing else navigates. This is the one sanctioned exception to the no-return backlog item
(design 17's plan): it is not a navigation stack, it is a parent pointer.

## 7. The JEI face and the entry points

- Set entries appear in the JEI category beside blueprint entries (the category's entry list simply
  grows); the JEI face draws the composite scene through the same `PreviewScene`, and **open in
  screen** opens the set page.
- `/cumulus preview screen <id>` accepts a set id and opens the set page; a blueprint id that names
  both a blueprint and a set resolves to the blueprint, and the set is reachable as
  `<id>#set` — a qualifier the command parser owns, never the wire.

## 8. Codes and non-goals

The pack allocates no codes: a set entry left out for a cap or a validation finding is 15 §6.1's
WARNING line. Non-goals: editing a set from the page, dragging members, re-solving placements,
presence *checking* against a world (that is `set check`'s job, in a world), and any nested-extent
display (D-111's stand: no shipped provider, nothing to show).

## 9. Milestone

SC12 in [`docs/plan/screen-plan.md`](../plan/screen-plan.md): the catalogue's set entries, the set
page with the constraint overlay, the drill-down, the JEI face, and `demo_campus` as the first
exhibit.
