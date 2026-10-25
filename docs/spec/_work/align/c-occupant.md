# C — U2 occupants (code ↔ spec)

Slice: every file under `src/kernel/java/dev/vfyjxf/cumulus/kernel/occupant/` against
`docs/spec/06-occupants.md` end to end.

**Headline.** The code is not a partial implementation of `06`; it is a *different* read model
(the **section model**) that `20` §1.3/§4.1, `22` §5.1/§5.4, `01` §319–325 and the whole
consuming kernel (`Focuses`, `BayView`, `MatchSession`, `DirtyIndex`, `ReadScope`) already
assume, while `06` and the glossary's `WorldView` row still describe the superseded per-cell
model (`bounds()`, `readStatus(LatticePoint)`, a per-cell `CapturedView`). So `06` is mostly
a lagging document, not a lagging code slice — except where the code itself is wrong
(`OccupantRef`'s identity, traits without values, the refusal's code type, the box-query
filter). Both directions are recorded below.

## 1. Scope (files read)

Code slice (all of it): `ChangeSet`, `EntityKey`, `EntityWindow`, `NestedLevelSnapshot`,
`OccupantDescriptor`, `OccupantFilter`, `OccupantKind`, `OccupantRef`, `OccupantSnapshot`,
`ReadStatus`, `SectionSlot`, `SectionSnapshot`, `WorldView`, `package-info`.

Code cross-references consulted because a claim is load-bearing there:
`predicate/{NestedView,FocusContext,ExpectationContext,BayView,Focuses,Expectations,ExpectationMeta}.java`,
`engine/{SnapshotId,SnapshotLease,DirtyIndex}.java`, `base/{InstanceId,ReadScope,FindingCode}.java`,
`blueprint/AnchorSpec.java`, `space/{SectionKey,ChunkGrid,LatticePoint}.java`,
`kernelTest/.../occupant/OccupantReadModelTest.java`,
`kernelTest/.../examples/TestWorld.java`, `kernelTest/.../examples/AbsenceScenarioTest.java`.
Stated for the record: `src/main/java` contains **no** adapter (no `mc` package), so every
adapter-side U2 name (`LevelSnapshot`, `LevelCapture`, `WorldOccupants`, `OccupantDescriptors`,
`OccupantTraits`) has no code at all.

Spec read: `06` end to end; `19` R8/R12/R15/R32/R36; `20` §1/§4/§5/§6/§8/§9/§10; `22` §4.4/§5;
`01` §60–101, §202–210, §295–325; `13` §"shipped allocation"; `02` §2; `03` §60–63; `16` §28–72;
`00` §5; `_index.md`.

## 2. Divergences (the table)

| id | doc anchor | code anchor | what the doc claims | what the code does | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| C-1 | `06` §3.1 (`record OccupantRef(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)`, `boolean from(SnapshotId id)`), §4.7 ("Ref from another capture? `ref.from(view.snapshotId())` is false"), §3.2 `air(SnapshotId, …)`; `22` §5.1; `01` §70; `19` R8, R36 | `occupant/OccupantRef.java:13` `public record OccupantRef(InstanceId instance, NamespacedId type, OccupantKind kind, LatticePoint anchor, Cuboid footprint)`; `OccupantSnapshot.java:43` `new OccupantRef(instance, AIR, OccupantKind.BLOCK, cell, Cuboid.of(cell))`; `WorldView.java:12-29` has **no** `snapshotId()` | the ref is stamped with the capture it was minted under; a ref from another capture is detected, never repaired | the ref is stamped with the **placed instance** (`base.InstanceId(NamespacedId world, long serial)`, `serial` = the structure's id), which is equal across every capture of one instance; no view carries a capture id, so nothing can tell two captures apart | `DOC` | R8/R36 both rule that the capture stamp is `OccupantRef`'s identity component and `22` §5.1 repeats it; the substitution is covered by no R-record, so the fix (add `snapshotId()` back to `WorldView`, mint air with it — `22` §5.1 dropped the member that `06` §3.3/§20 §4.1/`01` §76 keep) is itself an open decision. |
| C-2 | `06` §3.3 (`snapshotId`, `bounds`, `occupant(LatticePoint)`, `occupants(Cuboid)`, `readStatus(LatticePoint)` + optional `summary/chunkGrid/chunk`), §4.1, §5 sketch, §7.2; `01` §76; `20` §4.1/§10 | `occupant/WorldView.java:12-29` = `slot(SectionKey)`, `occupant(LatticePoint)`, `occupant(OccupantRef)`, `occupants(Cuboid, OccupantFilter)`, `nested(LatticePoint)`, `contentDigest(SectionKey)`; `SectionSlot.java:15-33` `Air(long revision) \| Present(SectionSnapshot) \| Refused(ReadStatus status, NamespacedId code)` | one per-cell total window: `bounds()` + a five-valued `readStatus(LatticePoint)`, `occupant` then `readStatus` as two calls | status lives at **section** grain on `slot(...)` (`Air` is readable-and-empty), cells answer through `occupant`; there is no `bounds()`, no `readStatus(LatticePoint)`, no `summary/chunkGrid/chunk` | `CODE` | `20` §1.3 ("No per-cell `OccupantSnapshot` array for block states"), `22` §5.1 (byte-for-byte the code's set) and every consumer (`MatchSession.java:455` `view().slot(section).readable()`, `Focuses.occupant`, `BayView`) are on the section model, so `06` §3.3/§4.1/§5/§7.2 must be rewritten — restoring the one member `22` §5.1 wrongly dropped (`snapshotId()`, C-1). |
| C-3 | `06` §1 Scope (fifteen names, none of them `SectionSlot`, `SectionSnapshot`, `NestedLevelSnapshot`, `OccupantFilter`, `EntityWindow`, `EntityKey`, `ChangeSet`), §6/§4.2 (no entity window); `01` glossary has **no row** for `EntityWindow`, `EntityKey`, `OccupantFilter` | `occupant/EntityWindow.java:11` `public record EntityWindow(Cuboid cells, NamespacedId type)`, `:13` `MAX_ENTITY_SPAN = 3`; `EntityKey.java:11` `record EntityKey(UUID id, NamespacedId type, long dataDigest)`; `ReadScope.java:11` `record ReadScope(CuboidSet cells, Set<ContextKey<?>> keys, List<EntityWindow> entities)`; `DirtyIndex.java:95` iterates `reads.entities()` | nothing: `06` has no entity-window concept and the vocabulary table never names these types | seven of the fourteen files in the package are load-bearing (`ReadScope` is U0, `DirtyIndex`/`ChangeSet` are the dirty path) and are named only by `22` §5.4, which defines the two records verbatim | `CODE` | the entity window is `20` §5.1/§5.2's per-tick outward-move witness and `SC-BAY-0110`'s `MAX_ENTITY_SPAN`, and it is implemented; a U2 regenerated from `06` alone deletes `EntityWindow`, `EntityKey`, `OccupantFilter`, `SectionSlot`, `SectionSnapshot` and `ChangeSet` and breaks `ReadScope`, `DirtyIndex`, `Focuses` and `BayView`. |
| C-4 | `06` §3.1 (`record OccupantDescriptor(NamespacedId type, OccupantKind kind, Map<String,String> properties, List<NamespacedId> tags)`, `@Nullable String property(String key)`), §4.2 row 1 ("state as `name=value`"), §3.1 ("`property(k) == null` is silence, never `false`"); `01` §71 | `occupant/OccupantDescriptor.java:16` `public record OccupantDescriptor(NamespacedId type, Set<NamespacedId> traits)` | a descriptor publishes the host's properties by key, plus sorted/deduped tags and its kind | there is **no property channel at all**; the field that exists carries a *trait* id set, so `kind` is absent from the descriptor and no string-valued state can be read | `DOC` | `blueprint/AnchorSpec.java:8` `record AnchorSpec(NamespacedId role, NamespacedId property, String value)` already declares "one property read at the definition origin" and `22` §4.4's `AnchorResolver` "reads one cell, checks property/value, obtains a `Heading`" — R-04/R-05 are unwritable without this channel, so it is a hole, not a taste question. |
| C-5 | `06` §3.1 (`TraitKey<T>(id, Class<T>)`, `Trait<T>(key, @Nullable value)`), §3.2 (`find`/`trait`/`has`), §4.4 ("present with `value() == null` is a definite fact"), §5 (`ItemInventory`, `ITEM_INVENTORY`), §6 ("a hatch needing an inventory"); `01` §72/§73/§88; `19` R8 (`Traits` reserved holder, `SnapshotPolicy.requiredTraits`); `20` §4.1 `CellPayload(…, List<Trait<?>> traits)` | `OccupantSnapshot.java:15` `… Set<NamespacedId> traits …` + `hasTrait(NamespacedId)`; `OccupantDescriptor.java:16` `Set<NamespacedId> traits`; `OccupantFilter.java:30` `ofTrait(NamespacedId)`; `predicate/ExpectationMeta.java` `Set<ContextKey<?>> requires` | traits are typed keys with values; a missing trait is `UNREADABLE`, a null value is a fact; required traits come from the policy | traits are **ids only**: presence or absence, no value, no key type, no `Traits` holder, and the demand side is a set of `ContextKey<?>`, not `TraitKey<?>` | `DOC` | R8 and `06` §4.4/§5/§6 make the value the point (`hasTrait` is "the free test", `trait(...)` "the priced one"), and `01` §72/§73/§88/§101 plus `20` §4.1's `CellPayload` all carry `Trait<?>`; no R-record covers the id-only substitution. |
| C-6 | `06` §4.3 (`occupants(box)` = one entry per occupant, air excluded), §5 sketch (`if (e.footprint().intersects(box) && readStatus(e.ref().anchor()) == ReadStatus.READABLE)`), §7.3; `20` §4.1 (same filter line) | `SectionSnapshot.java:121` `if (occupant.footprint().intersects(box) && filter.matches(occupant))`; `examples/TestWorld.java:107-114` identical, over an `elements` list built from every cell the builder was given | a box query answers only occupants whose anchor was read | a box query has **no readability test**, so an occupant whose anchor sits in a refused section is still returned | `DOC` | binding a subject the capture refused (`DENIED`, `NOT_CAPTURED`) is the laundering `06` §4.1 forbids ("`DENIED` laundered as air is the host bug that makes a correct kernel lie"); the fix must live in the view, since a bare `SectionSnapshot` owns no slots. |
| C-7 | `06` §4.3 row 1 ("both cells of a 2×1 entity answer one ref") vs §4.3 row "Two occupants in one cell" ("narrows by non-air, then **smaller volume**, then ref order" — the 1-cell block then wins over the 2-cell cow) | `SectionSnapshot.java:94-102` indexes `Map<LatticeKey, OccupantSnapshot>` (one occupant per cell); `examples/TestWorld.java:286-293` `place(...)` → `specs.put(...)`, i.e. the **last declared** occupant wins the cell | both a block and an entity can occupy one cell, and a query narrows between them by volume | one occupant per cell, chosen by declaration order, unpinned by any rule | `BOTH` | `06` §4.3 contradicts itself (its own row 1 is defeated by its narrowing rule) and the code's order-dependent shadowing is a determinism hole; the rule that replaces both is `20` §4.2: one occupant per cell, an admitted entity's footprint shadows the cell's block read, and the shadowed block read is `NOT_CAPTURED`. |
| C-8 | `20` §4.1 `record Refused(SectionKey key, ReadStatus status, FindingCode code)`; `06` §3.4 `record Unavailable(ReadStatus status, FindingCode code)`; `13` §298 ("`FindingCode.parse` accepts exactly that grammar") + the shipped-allocation table | `SectionSlot.java:27` `record Refused(ReadStatus status, NamespacedId code)`; `examples/TestWorld.java:24` `FOC_REFUSED_SECTION = NamespacedId.of("structurecumulus", "sc-foc-0301")`, `:153` `new SectionSlot.Refused(status, FOC_REFUSED_SECTION)`; `OccupantReadModelTest.java:64` passes `NamespacedId.of("structurecumulus", "sc-occ-0102")` | a refusal carries a validated `FindingCode` | a refusal carries a bare `NamespacedId`, and the tests already pass an id no table publishes (`sc-occ-0102`) | `CODE` | `FindingCode` is in `kernel.base`, so U2 may name it, and every other code carrier in the kernel is a `FindingCode`; a raw id lets a typo become a silent, unorderable finding code and forfeits `13`'s grammar. |
| C-9 | `06` §3.1 (`BLOCK, BLOCK_ENTITY, ENTITY, NESTED, OTHER`; both records `implements Comparable`; order `snapshot, y, z, x, kind.id, type`), §7.6 ("occupants by `OccupantRef`"); `01` §69 | `OccupantKind.java:10-15` four constants, `public record OccupantKind(NamespacedId id) {` — no `Comparable`; `OccupantRef.java:13` not `Comparable`; `SectionSnapshot.java:83-88` local `ORDER = comparing(anchor).thenComparing(type.ns).thenComparing(type.path).thenComparing(kind…)`; `examples/TestWorld.java:164-165` `comparing(anchor).thenComparing(type)` | five kinds with `of(id)` = host family; one canonical order on the ref, used for every listing | `OTHER` does not exist and the order is implemented **twice, differently** (the test fixture's copy has no kind term), with no order on the ref at all | `DOC` | `01` §69 and `06` §3.1/§7.6 are right and a single `Comparable` is the fix; two local comparators that already disagree is exactly the "`HashMap.values()` flaps findings" counterexample — no R-record covers the dropped `Comparable`. |
| C-10 | `22` §5.1 ("a section snapshot holds a frozen container copy and a `contentDigest`"); `20` §6.3 ("the digest hashes the sorted `(SectionKey, contentDigest)` pairs the unit read") and §8.6 P6 ("a section whose frozen content differs is a different digest", "no cache false hit"); `AttemptCache` validity = `closureDigest` equality | `SectionSnapshot.java:145-146` `hash = 31L * hash + entry.getKey().packed(); hash = 31L * hash + entry.getValue().descriptor().type().hashCode();` | the digest names the section's content | the digest hashes only *(packed cell, occupant type)*: a chest's traits, its properties and its footprint are invisible; `occupant.type()` is the only occupant term, and `WorldView.contentDigest(SectionKey)` exposes it as API | `DOC` | an inventory-only change leaves the digest equal, so a `closureDigest`-validated cached attempt still matches — the P6 false hit; the digest must cover descriptor properties, traits and footprint, or the claim must be withdrawn. |
| C-11 | `06` §4.3 row 1 ("A covered cell is `READABLE` even if its own read was denied… `DENIED` applies only where no admitted occupant covers the cell"); `19` R8 (same sentence) | `WorldView.java:14-16` javadoc: "or `null` when the cell cannot be read — outside the view **or behind a refused slot**"; `examples/TestWorld.java:83-85` resolves the slot **before** consulting the footprint, so a covered cell in a refused section answers `null` | an admitted occupant's footprint is the evidence, so it beats the cell's own denied read | a refused slot wins over an admitted footprint, so an entity straddling into a refused section loses half its body | `DOC` | R8 states it explicitly; under the section model it must be restated at section grain ("a refused slot does not shadow a cell an admitted occupant's footprint covers") and enforced in the view, not in `SectionSnapshot`. |
| C-12 | `06` §4.1 ("Reserved codes (U3/U7 build the finding): `SC-OCC-0101` out of bounds, `0102` not captured, `0103` denied, `0104` stale, `0201` footprint crosses the window, `0202` depth cut, `0203` cycle"), §4.5, §9 | no `SC-OCC-01xx`/`02xx` string or `FindingCode(Family.OCC, …)` anywhere under `src/`; the unreadable cell is `Focuses.UNREADABLE_CELL = FindingCode.of(Family.FOC, 301)` (`Focuses.java:35`) and `13` §330 defines `SC-FOC-0301` as "a section in the unit's region was refused"; refusals' causes are `SC-MC-0306/0302/0501/0505` (`20` §4.2) | U2 allocates seven codes for these situations | `13`'s shipped allocation publishes only `SC-OCC-0001` ("the cell is `UNREADABLE`") and `SC-OCC-0002` ("an occupant's footprint leaves the snapshot"), and **no producer raises either** | `BOTH` | `06` reserves codes `13` does not publish and no producer raises, while `13` publishes two codes with no producer; the rule should be one table: U2 raises `SC-OCC-0001/0002` from the read model, U3 raises `SC-FOC-0301/0302` when a refusal propagates through a focus or a fold, A1 raises `SC-MC-05xx/03xx` for the capture-side causes, and `06` §4.1's `0101–0203` are deleted or given producers. |
| C-13 | `06` §3.3 (`ReadStatus readStatus(LatticePoint cell)`, `Cuboid bounds()`), §4.1 ("Consumers use two calls (`occupant`, then `readStatus`)"), §4.1's precedence row `OUT_OF_BOUNDS > DENIED > STALE > NOT_CAPTURED > READABLE`, §4.5 (`OUT_OF_BOUNDS` relative to the child `bounds()`) | `WorldView.java` has neither member; `readStatus` is reachable only as `slot(SectionKey).status()` on `SectionSlot.Refused`, and a consumer cannot build the `SectionKey` (`WorldView` exposes neither the dimension nor the cell size); `BayView.occupantAt` (`BayView.java:24-27`) returns `null` both for a cell outside the bay and for a refused cell, and `Focuses`/`Expectations.eachCell` report both as `SC-FOC-0301` | the five statuses are per cell, distinct and ordered | `occupant(cell) == null` is the only signal a consumer has: five statuses collapse into readable/unreadable, and an out-of-bay cell is mislabelled an unreadable world | `RULE` | needs a ruling (C-13) — the model kept `slot` but `06`'s precedence and `13`'s diagnostics are unreachable without a cell→status accessor. |
| C-14 | `06` §3.3 (`interface NestedView extends WorldView` + `parent()/depth()/ancestry()`), §4.5 ("A nested view **is** a full `WorldView`: every question askable at the top is askable one level down with the same cost class and absence rules (R-01)", "`snapshotId()` equals the parent's", "`readStatus` — the same five"), §3.2 (`nested() != null iff…`); `01` §75/§325; `20` §10 ("`NestedLevelSnapshot` naming"); `03` §63 (`*View` = "`WorldView`, `NestedView`") | `occupant/NestedLevelSnapshot.java:15-53` final class, `parent()/depth()/contentDigest()/occupantAt(LatticePoint)/occupants(Cuboid, OccupantFilter)` — implements nothing, no `snapshotId()`, no `bounds()`, no slot, no `ancestry()`; `predicate/NestedView.java:9` `public record NestedView(NestedLevelSnapshot level, BayView bay)` — U3 reuses the U2 name for its focus binding | the child is a full `WorldView` under the same name, with `ancestry()` for the cycle heuristic | the child is a near-copy with **renamed** members (`occupantAt` vs `occupant`), no top-level questions (`slot`, `contentDigest`), no ancestry, and the name `NestedView` belongs to an unrelated U3 record | `RULE` | needs a ruling (C-14) — a child frame has no 16³ section grain, so "is a full `WorldView`" and the section model may be incompatible, and two types answer to `NestedView`. |
| C-15 | `06` §3.4 en bloc: `WorldCapture.capture(CaptureRequest) → CaptureResult` + `latestRevision()`, `sealed CaptureResult {Captured(WorldView) \| TooLarge(Cuboid,int) \| Unavailable(ReadStatus, FindingCode)}`, `CaptureRequest(bounds, grid, policy, trackedTypes, trackedCells, limits)`, `CaptureLimits(maxCells, maxSections, maxBytes, maxEntities, maxPayloads)`, `SnapshotPolicy(CaptureMode{EXACT,REQUIRED}, requiredKinds, requiredTraits, maxElements, maxNestedDepth, maxStalenessNanos)` + `minimal(int)`, `WorldWindow(Cuboid,int)`, `ResolveContext(snapshotId, anchor, remainingDepth, policy)`, `OccupantResolver.kinds()/resolve(raw, ctx)`; `01` §83–87, §323/§324; `19` R32 | none: `SnapshotPolicy`, `CaptureMode`, `WorldWindow`, `WorldCapture`, `CaptureRequest`, `CaptureLimits`, `CaptureResult`, `OccupantResolver`, `ResolveContext`, `OccupancySummary`, `ChunkContent` exist nowhere under `src/` (grep is empty), and `src/main/java` has no adapter at all | one SPI shape | another shape: `16` §47/§397-414 `CaptureResult capture(CaptureRequest req, IssueSink issues)` with `CaptureResult(snapshot, lease, issues)`, `CaptureResult.unavailable(OFF_THREAD)`; `22` §5.2 `CaptureResult(snapshot, CaptureIssues)`; `16` §64-65 `OccupantResolver` with **four** members (`kinds`, `resolve`, `extent(Raw)`, `resolveNested(Raw, ResolveContext, NestedView)`); `20` §4.2 replaces the caps with `CaptureLimits` and `MatchPolicy`/`SchedulerOptions` | `RULE` | needs a ruling (C-15) — four documents describe the capture/resolver SPI three different ways and the `requiredTraits` demand list `19` R8/A3 puts on `SnapshotPolicy` has no carrier (`ExpectationMeta.requires` is `Set<ContextKey<?>>`). |

## 3. Rulings needed

**C-13. Is a cell's `ReadStatus` part of U2's read API, or only a section's?**
The section model keeps `slot(SectionKey).readable()` (used by `MatchSession.java:455`), and a
consumer answers every cell question through `occupant(cell)`; the five statuses, `06` §4.1's
precedence and `13`'s "reason" diagnostics are unreachable for a cell, and `BayView.occupantAt`
collapses "not in my bay" into "unreadable".
Options: (a) keep `slot` only and state in `06` that U2 owes a *boolean* readability per cell,
deleting §4.1's precedence table — cheapest, but an out-of-bay cell keeps being reported as
`SC-FOC-0301`; (b) add `default ReadStatus readStatus(LatticePoint)` to `WorldView`, derived from
`slot(SectionKey.of(dimension, cell, cellSize))` where the *view* supplies the dimension and cell
size it already knows, plus a `FindingCode refusedCode(SectionKey)` accessor so the refusal's own
code reaches the finding; (c) expose `SectionKey sectionOf(LatticePoint)` and leave the status
logic to each consumer.
Recommendation: (b) — it restores `06` §4.1's contract at zero capture cost, is implementable by
every existing view (each already holds the dimension and cell size), and keeps one producer for
the status.

**C-14. Is the child view a `WorldView`, and what is it called?**
`06` §3.3/§4.5/R-01 want "every question askable at the top askable one level down"; the section
model gives a child frame *no* section grain (a nested coordinate `(0,1,0)` is the parent's
interior, so `SectionKey.of(dimension, childCell, cellSize)` is meaningless), and the code
renames the members instead (`occupantAt` for `occupant`). Separately, `01` §75/`03` §63 give the
name `NestedView` to the child view while the code gives it to U3's `record NestedView(level, bay)`.
Options: (a) `interface NestedLevelSnapshot extends WorldView` (the name `20` §10 asks for),
defining `slot` in a child frame as the child's own grid with a stated origin mapping, and keeping
`NestedView` for U3's binding; (b) a distinct U2 interface `NestedView` with only the cell and
nested reads (`occupant`, `occupants`, `nested`, `parent`, `depth`, `ancestry`) and an explicit
statement that a nested interior has no section grain, so a subtree's unreadability is per-cell
`null` and never a `Refused` slot; (c) keep the code's `NestedLevelSnapshot` class as the only U2
child type and delete all `WorldView`-inheritance claims from `06`.
Recommendation: (b) — it is the only option that both keeps R-01's "same absence rules" for every
question a child can be asked and avoids inventing a section mapping that has no producer; it also
resolves the name clash without renaming a U3 type.

**C-15. Which of the four capture/resolver SPI descriptions is the one to regenerate from?**
`06` §3.4 (sealed `CaptureResult`, `latestRevision()`), `16` §397-414 (`CaptureResult` as a class
carrying snapshot+lease+issues, `IssueSink` instead of `FindingCode`), `22` §5.2
(`CaptureResult(snapshot, CaptureIssues)`), and `20` §4.2 (caps moved to `CaptureLimits`, policy
split into `MatchPolicy`/`SchedulerOptions`) all differ, and none of it exists in code.
Options: (a) adopt `16` verbatim (it is the only text with a body: `getChunkNow`, the section
walk, `SC-MC-05xx` refusals, `OccupantResolver` with `extent`/`resolveNested`) and make `06` §3.4
a cross-reference rather than a second definition; (b) keep `06` §3.4's two-member
`OccupantResolver` and fold footprint and recursion into `resolve`'s returned `OccupantSnapshot`
(which already carries a footprint and `nested()`), deleting `16`'s `extent`/`resolveNested`;
(c) keep both and let A1 own the extra members.
Recommendation: (a), with the `requiredTraits` demand list re-pointed: it cannot live on a
`SnapshotPolicy` that `20` has retired, and `ExpectationMeta.requires` is now
`Set<ContextKey<?>>`, so either the trait demand is dropped or `ExpectationMeta` gains a
`Set<TraitKey<?>>` back (C-5).

## 4. Documentation gaps that block regeneration

1. **Seven types unnamed by `06`** (C-3): `SectionSlot`, `SectionSnapshot`, `NestedLevelSnapshot`,
   `OccupantFilter`, `EntityWindow`, `EntityKey`, `ChangeSet`. `EntityWindow`/`EntityKey`/
   `OccupantFilter` have **no `01` glossary row** either, yet `base.ReadScope` (U0) holds
   `List<EntityWindow>` and `engine.DirtyIndex` (U7) holds `Map<EntityKey, EntityMark>`. The
   vocabulary gate (`_index.md`, "Review gates" 1) fails on the package as it stands.
2. **`EntityWindow.MAX_ENTITY_SPAN = 3`** (`EntityWindow.java:13`) and the `SC-BAY-0110` refusal
   live in `20` §5.1/`22` §5.4 but nowhere in `06` — yet the brief's question "does the
   entity-window model in `06` correspond to any code?" answers: `06` has no entity-window model
   at all; the model is `20` §5.1/§5.2 + `22` §5.4 + the code, and `06` must gain it (this is a
   `CODE`-side gap, i.e. `06` changes — not an unimplementable claim).
3. **`ChangeSet`'s shape has two definitions.** `01` §207 `(Set<LatticeKey> changed, Set<ChunkKey>
   chunks, Set<NamespacedId> changedTypes, long revision)` vs code (`ChangeSet.java:18-19`) and
   `22` §5.4 `(cells, sections, payloads, entities, keys)` + `long revision`. The code also has
   undocumented members `empty()` and `plus(ChangeSet)` (union + `Math.max` of the revisions);
   `plus` is exercised only by `OccupantReadModelTest.java:135`, and nothing under `src/kernel`
   consumes `ChangeSet` at all — the dirty path uses `DirtyIndex`'s own marks — so `22` §5.4's
   "`DirtyIndex` keeps `window → Set<InstanceRef>`" is also unbuilt.
4. **`OccupantRef`'s fifth component.** The code adds `Cuboid footprint` to the ref
   (`OccupantRef.java:13`, javadoc "the footprint is part of it") where `06` §3.1, `01` §70,
   `22` §5.1 ("the footprint belongs to the `OccupantSnapshot`") and `03` §60 (`*Ref` =
   "snapshot-scoped handle, equality by identity within a snapshot") all give four components;
   and `OccupantSnapshot`'s constructor validates kind, type, empty footprint and
   NESTED-without-child but never `footprint.equals(ref.footprint())`, so one snapshot can carry
   two different footprints while `occupants` dedups on the ref's. Either the component goes or
   the invariant is stated and checked.
5. **`OccupantSnapshot`'s redundant `kind`.** `22` §5.1 gives `(ref, kind, descriptor, footprint,
   traits, nested)` and the code matches (with a consistency check), while `01` §74 gives
   `(ref, descriptor, footprint, traits, nested)`. Harmless, but the regenerator needs one of the
   two.
6. **`06`'s codes (C-12)** are unimplementable as written: no carrier (`FindingCode` vs the
   code's `NamespacedId`), no producer, and no row in `13`'s shipped allocation.
7. **`06` §3.4's SPI (C-15)** and `06` §2's `WorldWindow`/`SnapshotPolicy`/`CaptureMode` are
   definitions with no code, no consumer and three competing versions elsewhere; a regenerator
   told "implement `06` §3.4" will produce an SPI the adapter text names differently.

## 5. Checked and aligned

- `ReadStatus`: exactly the five constants `READABLE, OUT_OF_BOUNDS, NOT_CAPTURED, DENIED, STALE`
  plus `readable()` in both (`ReadStatus.java:6-15` ↔ `06` §2/§3.2). **The brief's premise that
  `ReadStatus` also carries `UNKNOWN` and `BROKEN` is wrong**: those belong to
  `WatchState {CLEAN, DIRTY, CHECKING, BROKEN, UNKNOWN}` (`01` §318, `20` §5.2) and to
  `CellMark`/`UNKNOWN` (`01` §277, `14` §294). A regenerated U2 must be given five, not seven.
- `air`: `OccupantSnapshot.AIR = structurecumulus:air`, kind `BLOCK`, `Cuboid.of(cell)`, no
  traits, `nested == null`, `isAir()` on the descriptor type — matches `06` §3.2 exactly except
  for the identity parameter (C-1).
- Canonicalising constructor rejections: kind/ref disagreement, type/descriptor disagreement,
  empty footprint, `NESTED` without a child (`OccupantSnapshot.java:23-38`) ↔ `06` §3.2. The
  descriptor's tag/id copies are sorted, deduped and unmodifiable (`OccupantDescriptor.java:34-46`,
  `immutableIds`) ↔ `06` §3.1/§7.6 defensive-copy proof (`18` C-05).
- `OccupantKind.of(id)` present; the family ids are `structurecumulus:block|block_entity|entity|
  nested` ↔ `06` §3.1/§2 (only `OTHER` and `Comparable` are missing, C-9).
- `occupants(box)` dedups by ref and lists a multi-cell occupant once, air excluded
  (`SectionSnapshot.java:128-141`) ↔ `06` §4.3/§7.3; box queries are element-bounded
  (`OccupantReadModelTest.java:88-94`).
- Air is readable and a refused slot is never air: `SectionSlot.readable()`,
  `Refused`'s constructor rejects a readable status (`SectionSlot.java:15-34`),
  `AbsenceScenarioTest.java:68-72` asserts `occupant(MIDDLE) == null` ↔ `06` §4.1/§4.4, `22` §5.1,
  `20` §4.1 ("`Air` is read and empty: `READABLE`… It is not `NOT_CAPTURED`").
- Nested recursion floor: `NestedLevelSnapshot.of(parent, depth, …)` rejects `depth < 1`
  (`NestedLevelSnapshot.java:28-31`) ↔ `06` §3.3 ("root view = 0; a direct child = 1").
- `NESTED ⇒ nested() != null` is enforced (`OccupantSnapshot.java:36-37`) and not the converse —
  `06` §3.2's exact wording.
- `SectionKey` uses `Math.floorDiv` and `compareTo` orders `(dimension, x, z, y)`
  (`SectionKey.java:9-40`, `:34-40`) ↔ `20` §6.1's `floorDiv` rule and §6.2's "sections sorted
  `(x, z, y)`"; `ChunkGrid` (U1) also uses `floorDiv` ↔ `20` §6.1/X8.
- `OccupantRef`'s anchor is the owning cell in the minting frame and only the identity compares
  across frames (`06` §3.1) — semantically held by the code, since nothing in the ref is frame
  dependent once C-1 is fixed.
