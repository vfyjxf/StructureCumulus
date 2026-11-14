# 19. The stage timeline, the grouped stages and the JEI face's parity

> Status: the contract for SC13 and SC14. Supersedes design 17 §7.3's single-row stage bar and
> design 18 §4's "stages are the members" (a set page now narrates each member's own stages too).
> Everything else keeps 17's laws: the timeline is chrome over the same payload, the same clock and
> the same playback.

## 1. Problem

Three readings of one complaint chain (the viewer, 2026-11-14): the stage bar at the bottom is a
single row of thin segments that says nothing about *which* part of the structure a stage belongs
to, so a set page's play — one stage per member — shows the station's whole ten-bay build as one
block appearing at once, and the main structure's assembly order is invisible. And the JEI face is
a generation behind the screen: no stage bar, no constraint overlay, no wheel zoom, no frame-all —
"in JEI it's basically unwatchable compared to the screen."

## 2. The grouped stage

A stage gains a **group**: the part of the document it belongs to.

- **Blueprint page: no group.** A document's stages are its own build order (16 §4.1's bays, or a
  single bay's Y layers) and they are peers: one lane, every stage side by side. Grouping a
  blueprint by bay would put one stage in each lane — a vertical list of single segments, which is
  worse than the flat bar it replaced (the SC13 screenshot: ten lanes of one).
- **Set page: the member is the group.** The stages inside a group are that **member's own stages**,
  flattened in build order, so a set page narrates the station's plinth, core tiers, arms, pods and
  crown, then the machine's bays, then the tower's — the composite's assembly, not three blocks
  switched on. The lane head carries the member; the segment carries **the member's own stage name
  only** — the head already says whose stages these are, so a segment never repeats it.
- **The flattening is the server's.** The set entry composes each member's `StageModel` (16 §4.1)
  and emits one stage per (member, member-stage), its cells carrying the flattened index; the wire
  carries a `group` label per stage (`null` on a blueprint page), and the client derives nothing
  (17 §2).
- **Playback is unchanged.** One flat stage list in build order, `hold → batch → dwell` per stage,
  the zero state, drop/rise and replay all as they are (17 §7.1/§7.3/§7.6). A set's play is simply
  longer and ordered like a build.

## 3. The timeline (17 §7.3's bar, rebuilt)

The stage bar becomes a **timeline** of grouped lanes:

- **Lanes are groups.** Consecutive stages of one group form a lane on its own row; the lane's head
  carries the group's label, then its stages as segments in order. Lanes stack top to bottom in
  build order (分层).
- **Segments are stages.** Width is proportional to `expectedCells` with a legible minimum, 1 px
  gaps, the name drawn when it fits. The cursor's segment is outlined as today; a hover names the
  stage, its group and its cell count.
- **A segment reads.** The strip's own ground, a segment's fill, the cursor's outline and the
  segment's text are four chrome tones that must separate from each other *and* from the blurred
  world the strip sits over (a screen is translucent): the fill is opaque enough that two adjacent
  segments differ, and the text keeps the chrome family's contrast. A bar whose fills vanish into
  the panel is not a bar.
- **A lane wraps when it overflows (换行).** The overflow continues on the lane's next row,
  indented under the label; a lane is never truncated and a segment never shrinks below the legible
  minimum.
- **The strip grows, then scrolls.** The control strip's height is the timeline's own for the
  frame, capped at `timelineMaxHeight` (code constant); a timeline taller than the cap scrolls with
  the wheel over it (a scrollbar rule on the right, chrome). Panels take the remaining height and
  the fit follows, as it already does.
- **A playhead rides the timeline.** During a play, a 1 px vertical line marks the boundary between
  the revealed and the unrevealed part of the cursor's segment; the segment itself keeps the cursor
  outline. Stopped, the playhead sits at the cursor's segment start.
- **It is still a table of contents, not a progress bar.** No segment is ever filled in as
  "satisfied" (a page satisfies nothing, 17 §7.3); the only marks are the cursor, the hover and the
  playhead.
- **Determinism.** `StageTimeline.of(stages, width, metrics)` is a pure function returning lanes,
  segments and the strip's height; unit tests pin wrapping, the minimum width, the lane order and
  the height cap.

## 4. Tooltips are above everything

A screen tooltip (a cell's, a link caption's, a button's) draws **above every element of the page**,
the scene included:

- **The scene pass restores the GUI's depth state.** Both arms (screen and JEI) enable the depth
  test, write depth and, in their `finally`, put the GUI back: `disableDepthTest` and
  `depthMask(false)` beside the existing lighting restore. A pass that leaves the depth test on
  makes every later 2D draw fail its depth comparison against the scene — which is exactly the
  reported "the tooltip is under all the elements".
- **Tooltips draw at vanilla's tooltip depth.** The tooltip's box and text are drawn with the pose
  translated to `tooltipZ` (`400`, the value vanilla's own tooltip uses, design 17 §5.3's screen
  tooltip included), so a tooltip is above panel chrome, the timeline and the scene by
  construction, not by draw order.
- **Order still matters, and stays.** Tooltips are the last thing a frame draws; the z restores the
  invariant if a future widget forgets.

## 5. The JEI face's parity (SC14)

The JEI face keeps its compact footprint and gains the screen's verbs, so a reader who learned one
surface can read the other:

- **The timeline, compact.** The same `StageTimeline` at a smaller lane height and font; clicking a
  segment selects that stage (the JEI face shows one stage at a time — the slice slider's sibling),
  hovering names it. No playhead, no play: a JEI page is a static page (15 §6.2), and its clock is
  none.
- **The constraint overlay, for sets.** A set page in JEI draws the same link strokes and member
  labels as the screen (18 §5), toggled by the same `constraints` control when the face has room;
  without room, the overlay is on and the toggle lives in the tooltip.
- **The camera verbs.** Drag orbits (already), the wheel zooms (clamped like the screen, 17 §4.4),
  a double click frames the drawn hull (17 §4.2's `F`).
- **The same light, the same fit.** SC8's world light (15 §6.2) and 17 §3.3's hull fit already hold
  and stay; parity adds no third rendering law.
- **What JEI still does not get.** A play, four viewports, an explode, an in-screen breadcrumb: the
  face is one small rectangle, and its job is to answer "what is this and how is it built", then
  hand off to the screen through "open in screen" (17 §9.1).

## 6. Codes and non-goals

No codes: the timeline is chrome, the grouping is a payload field, and both fail only as the
catalogue's existing caps and WARNING lines do. Non-goals: a timeline that edits the document, a
per-lane filter, a scrubbable playhead (a play is not a video, 17 §7.5's pace is the document's),
and a second renderer for JEI.

## 7. Milestones

- **SC13** — the grouped stage (payload `group`, server-side member-stage flattening), the timeline
  bar with lanes/wrapping/scroll/playhead, and the tooltip depth law. Gate: timeline math unit
  tests, set-page stage-order tests, uitest `screen` (lanes, wrap, cursor, playhead, tooltip above
  the scene), existing groups green.
- **SC14** — the JEI parity of §5. Gate: uitest `jei` (compact timeline, segment select, wheel zoom,
  double-click frame, the set overlay), the other groups green.
