# G1 — `a-base`, `b-space`, `c-occupant`

Files edited: `01-glossary.md`, `03-naming.md`, `04-api-doctrine.md`, `05-space.md`, `06-occupants.md`.
Every `CODE` row in the three reports was opened at both anchors and applied; the `DOC`/`RULE`/`BOTH`
rows were left alone (`A1`–`A9`, `A15`, `B-1`, `B-2`, `B-4`–`B-6`, `B-8`–`B-10`, `C-1`, `C-4`–`C-7`,
`C-9`–`C-15`).

## 1. Applied (9 rows)

- **A10** — `04` §6 first bullet — `Finding` joins `Blueprint` and `MatchRequest` in the "gets a
  builder" list and leaves the counter-list, because `Finding.builder(FindingCode)`/`Finding.Builder`
  exist (`Finding.java:49,178`) and five of its nine components are optional.
- **A11** — `04` §9 addon example (both `Codes.` call sites) — the two findings are built from
  `FindingCode.of(Family.FOC, 301)` and `FindingCode.of(Family.EXP, 2)`; `Codes` no longer occurs in
  `04`. `SC-OCC`/`SC-EXP` numbering follows `13` §4.3 (`0301` = an unreadable section through a focus,
  `0002` = the bay holds another type than declared).
- **A12** — `01` §C `Locus` row — the components are a frame, a **non-null** `CuboidSet` (empty when
  the frame carries no cells), an optional `KeyLocus` and an optional `SourceSpan`; a `DEFINITION`
  locus requires its span (`Locus.java:6,9-27`).
- **A13** — `01` §C new `Key locus` row + `03` §4 `record` row — `KeyLocus(InstanceId instance,
  NamespacedId key)` is a glossary name with an owner, and `03` §4 classifies it as a `record`
  (`KeyLocus.java:3`).
- **A14** — `03` §2 second bullet — the JDK half of the name ban is `java.util.function`,
  `java.util` collections and `java.time` values; `javax.tools`/`javax.swing`/`java.awt` is no longer
  banned outright, so the lead-in's "likely to be imported in the same adapter file" test governs and
  `Label` survives (`Label.java:5`).
- **B-7** — `05` §3 four rows — `LatticePoint` gains `MIN_XZ`, `MAX_XZ`, `MIN_Y`, `MAX_Y`;
  `LatticeKey` gains `x()`, `z()`, `y()`; `CuboidSet` gains `isEmpty()`; `Orientation` gains
  `then` (this element first) and `id()` (`LatticePoint.java:7-10`, `LatticeKey.java:15,19,23`,
  `CuboidSet.java:115`, `Orientation.java:60,131`).
- **C-2** — `06` §3.3 `WorldView` block, §3.2 last bullet, §4.1 table and third bullet, §5 sketch,
  §7.2; `01` §D `World view` row — the window is the section model: `snapshotId()`,
  `slot(SectionKey)`, `occupant(LatticePoint)`, `occupant(OccupantRef)`,
  `occupants(Cuboid, OccupantFilter)`, `nested(LatticePoint)`, `contentDigest(SectionKey)`. `bounds()`,
  `readStatus(LatticePoint)` and the `summary`/`chunkGrid`/`chunk` facades are gone; a status is a
  `SectionSlot` (`Air` = read and empty, `Present`, `Refused(status, code)`), and the five statuses,
  their precedence and the reserved codes are stated at section grain. `snapshotId()` is kept per
  C-1 (`DOC`: the code lag is the capture stamp, not the member).
- **C-3** — `06` §1 `In:` list, §2 vocabulary (seven new rows), §4 new §4.8, §6 host row, §8
  dependencies; `01` §D five new rows — `SectionSlot`, `SectionSnapshot`, `OccupantFilter`,
  `EntityWindow` (`MAX_ENTITY_SPAN = 3`) and `EntityKey` are named next to the existing
  `ChangeSet` row in §I, with the window as the per-tick outward-move witness and
  `SC-BAY-0110` as the wide-type refusal (`EntityWindow.java:11-13`, `EntityKey.java:11`,
  `ChangeSet.java:18-19`, `ReadScope.java:11`, `DirtyIndex.intersectsWindow`). `NestedLevelSnapshot`
  is named in `06` §1/§2 only — the child-view name is C-14's open ruling.
- **C-8** — `06` §3.3 `SectionSlot` block — `Refused(ReadStatus status, NamespacedId code)`, the
  code's carrier (`SectionSlot.java:27`). **Conflict flagged:** the row's own reason and
  `align/stage3-audit.md` §4 both argue the *code* side must change here (`FindingCode` is in `base`,
  and `OccupantReadModelTest.java:64` already passes an unpublished id). Applied as `CODE` per the
  brief; reverting is one line. `06` §3.4's `CaptureResult.Unavailable(ReadStatus, FindingCode)` was
  **not** touched — it is C-15's `RULE` and has no code at all.

## 2. Held by R

None. No `CODE` row in these three reports quotes a fact that `19` R27/R35/R36/R37 registers as lag:
`Finding`'s builder, `FindingCode.of`, `Locus.cells`/`KeyLocus`, `03` §2/§4, the five missing `space`
members, the section model, the seven unnamed `occupant` types and `SectionSlot.Refused`'s code type
are all outside the `Stage`-out-of-request, `ClaimRow`-with-`World`, `ClaimPlan`-without-rows,
`SnapshotInfo`-deleted and `SnapshotLease` items. The `SnapshotId` stamp (R8/R36) belongs to C-1's
`DOC` row and was not written back.

## 3. Stale rows

None. Each code anchor was reopened: `Finding.java:49,178`; `FindingCode.java:36-72` (`of`, `Family`);
`Locus.java:6,9-51`; `KeyLocus.java:3`; `Label.java:5`; `Orientation.java:60,131`;
`LatticeKey.java:15,19,23`; `LatticePoint.java:7-10`; `CuboidSet.java:115`; `WorldView.java:12-29`;
`SectionSlot.java:15-34`; `SectionSnapshot.java:44-146`; `OccupantFilter.java:13-30`;
`EntityWindow.java:11-13`; `EntityKey.java:11`; `ChangeSet.java:18-19`; `ReadScope.java:11`;
`DirtyIndex.java:94-101`; `NestedLevelSnapshot.java:15-53`. The reports' line numbers drift by one or
two inside `Locus.java`/`WorldView.java`; the quoted shapes are current.

## 4. Cross-file follow-ups

- **A11 — `Codes` still named outside `04`.** `12:230` (`Codes.CAVITY_TOO_SMALL.finding(...)` — no
  `finding(...)` member exists on `FindingCode`), `13:657,660,662,671,683,695,703,725`,
  `14:341,345`, `15:414,311` (`15` §32 is the only place `Codes` is declared). Each must become
  `FindingCode.of(Family.X, N)` or a `FindingCode` constant, and `15` §32's row must be withdrawn.
  If the lead prefers the other option in A11's reason (a real `Codes` type), `01` §K needs the row and
  `03` §4 a registry shape, and `04` §9 reverts to `Codes.UNREADABLE_CELL`.
- **C-8 — `20` §4.1** states `record Refused(SectionKey key, ReadStatus status, FindingCode code)`;
  under the applied `CODE` verdict the code's type is `NamespacedId`, so `20` §4.1 (and `22` §5.1's
  `Air|Present|Refused(status, code)`) must either follow or be the place the code side is fixed.
- **C-2 — the optional facades.** `06` no longer shows `OccupancySummary`, `ChunkContent`,
  `summary()`, `chunkGrid()`, `chunk(ChunkKey)`; `01` §D keeps the `Occupancy summary` and
  `Chunk content` rows (their `WorldView.summary` reference removed) and `16:53-54` still declares
  `summary`/`chunkGrid`/`chunk` on `LevelSnapshot`. Either the adapter keeps them without a kernel
  carrier or the two `01` rows are withdrawn.
- **C-2 — other `WorldView` member lists.** `22` §5.1's block (section model, `slot`) is already the
  code's; `20` §4.1/§10 and `19` R8 still mix the per-cell and section models. `16` §28-72's
  `LevelSnapshot` implements `WorldView`, so it inherits the new member set.
- **C-3 — `ChangeSet` has two shapes.** `01:207` still says `(Set<LatticeKey> changed, Set<ChunkKey>
  chunks, Set<NamespacedId> changedTypes, long revision)`; `06` §4.8 and `22` §5.4 now say the code's
  six components. That is MQ-11 (`align/stage3-audit.md` §3), undecided — one row must follow the
  other.
- **C-3 — `NestedLevelSnapshot` vs `NestedView`.** `06` §3.3 keeps `interface NestedView extends
  WorldView` and `nested()` returns `NestedView`, while the code returns `NestedLevelSnapshot` and
  `01` §75/§325 and `03` §4's `*View` row still name the former. Unchanged on purpose: C-14 is a
  `RULE` row. Whichever option wins renames `06` §3.3's block, §4.5's five rows (which still say
  `bounds()` and `readStatus`) and `01` §D's `Nested view` row.
- **C-15 — `06` §3.4 is untouched** (four competing SPI texts, no code), so its
  `CaptureResult.Unavailable(ReadStatus, FindingCode)` and `SnapshotPolicy.requiredTraits` still sit
  next to the applied `Refused(… NamespacedId)`.
- **A12/A13 — `01` §70 `OccupantRef` / §74 `OccupantSnapshot` / §69 `OccupantKind`** still carry the
  four-component ref, the payload-less snapshot without `kind`, and `OTHER`; those are C-1/C-4/C-5/C-9
  (`DOC`), deliberately not applied.
- **A1/A4/A5/A6/B-2/B-4/B-6** leave `03` §3's `base`-row dependency claim, `02` §2's acyclicity,
  `Locus`/`LocusFrame`/`KeyLocus`'s home, `OwnerId`/`InstanceId`'s home and the three missing
  `space` types (`SectionKey`, `ChunkGrid`, `ChunkKey`) untouched; `05` §1/§3 therefore still have no
  row for the three, although `06` §3.3 now uses `SectionKey`.

## 5. Verification

- `04-api-doctrine.md` — fence count 14 (even); §6 stays one bullet list; §9's two findings still
  carry `.stage(...)`, `.label(...)`, `.build()`; no `Codes` and no undefined type remains in `04`
  (`FindingCode` is `01:243`'s row, `Family` its nested enum, as `13:850` already writes it).
- `03-naming.md` — §2's four bullets keep their lead-in test; §4's table keeps three columns on every
  row and `KeyLocus` is one of the `record` examples; §4's `*View` row still names `NestedView`
  (C-14 open) and its `DerivationContext` example is G3's note, not this group's.
- `01-glossary.md` — no fences; §C rows all four columns (5 pipes), §D rows all four columns; the new
  `Locus` row names only members that exist in `Locus.java`, every new §D type is a public
  `kernel.occupant` type with the quoted components, and `WorldView`'s row matches `06` §3.3 member
  for member. `Nested view` no longer claims `bounds()`; `Occupancy summary` no longer references
  `WorldView.summary`.
- `05-space.md` — fence count 12 (even); §3 stays two columns; every member added (`then`, `id`,
  `x()`, `z()`, `y()`, `isEmpty`, the four constants) exists in `space/*.java` and `§6`'s existing
  `a.subtract(b).isEmpty()` now has a declared member; §4.1's range table still matches
  `LatticePoint.MIN_XZ…MAX_Y`.
- `06-occupants.md` — fence count 12 (even, six blocks); §2 two columns, §4.1 and §4.3 three columns,
  §6 three columns; every member named in §3.3/§4.1/§5/§7.2 exists in the code's `WorldView`,
  `SectionSlot`, `SectionSnapshot`, `OccupantFilter`, `NestedLevelSnapshot`, `OccupantSnapshot` or
  `SectionKey`; §4.1's reserved-code sentence (C-12) and §4.3/§4.5 (C-11/C-13/C-14) are untouched;
  §8's dependency list now names `SectionKey`/`ChunkGrid`, both used in §3.3/§3.4; §7.1's "imports
  only `base` and `space`" still holds (`occupant/*.java` imports nothing else).
