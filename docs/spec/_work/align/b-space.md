# B — U1 `kernel.space` (code ↔ spec)

## 1. Scope (files read)

Code (slice, end to end):
`src/kernel/java/dev/vfyjxf/cumulus/kernel/space/` — `Axis`, `ChunkGrid`, `ChunkKey`, `Cuboid`,
`CuboidSet`, `Flip`, `Heading`, `LatticeKey`, `LatticePoint`, `Orientation`, `Placement`,
`SectionKey`, `Siting`, `Tilt`, `Turn`, `package-info` (1071 lines).

Tests (evidence for what the code pins down): `src/kernelTest/.../space/{CuboidSetTest,
LatticeKeyTest, OrientationTest, SitingTest}.java`.

Spec (slice): `05-space.md` end to end (`03-naming.md` §packages skimmed for the package rule).

Cross-references read where load-bearing (not other slices' module files): `00-overview.md` §3 R-01/
R-05, §4 I-3/I-5/I-14, §5 units; `01-glossary.md` lines 58–63, 80, 117, 122, 206, 213; `02-architecture.md`
§2, §9; `08-claims.md` §2, §3, §4.1, §342; `12-context.md` §142; `13-findings.md` §824–829, §524;
`19-open-proposals.md` R1, R5, R19, R35; `20-execution-and-sections.md` §199.
Code outside the slice, cited only as evidence of the frames' home / of U1's consumers:
`kernel/base/{Locus,LocusFrame,KeyLocus,Finding,ReadScope,NamespacedId}.java`,
`kernel/claim/ClaimRow.java`.

Verification harness: an independent Java probe (`/tmp/align-probe/Probe.java`) compiled against
`build/classes/java/kernel`, exercising the compiled kernel — 48×4 cuboid images enumerated cell by
cell, 400 random box multisets against pointwise set algebra, 20 000 packing round-trips, the
reflective `MATRIX`/`ORDINAL` tables, and the §5/§9 sketch arithmetic. **It reports 0 failures**;
every numeric claim below marked "verified" comes from it, not from the code's own tests.

## 2. Divergences (the table)

| id | doc anchor | code anchor | what the doc claims | what the code does | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| B-1 | `05` §1 (owns), §2 "Locus map" row, §3 `LocusMap` row, §4.7, §5 sketch 3, §6 (both use cases); `01` line 61; `06` §209; `13` §322 | `space/` — **no file**; `grep -rn LocusMap src/` → 0 hits; only `Siting.toWorld/toLocal(LatticePoint)` (`Siting.java:32-48`) and `Orientation.apply(CuboidSet)` (`Orientation.java:107-117`) exist | `LocusMap` is `record (Siting)` with `static of(Siting)`, `@Nullable toWorld(Locus)`, `@Nullable toLocal(Locus)`, `toDefinition(Locus)`; it is the only carrier of LOCAL↔WORLD conversion for sets, and `SC-SPC-0001` is defined over its failure | absent from the kernel entirely; nothing converts a `Locus` between frames | **DOC** (no decision covers it) | spec is right and the code lags, but no R-record and no `23`/`24` §9 migration item names frame conversion, so this is not "deliberately behind" — regeneration must add the type. |
| B-2 | `05` §3 `LocusMap` row, §4.7, L-1, §5 sketch 3 ("`definition.frame() == DEFINITION`") | `base/Locus.java:29-31` `definition(SourceSpan)` → `CuboidSet.empty()`; `:49-51` `key(...)`; no `LocusMap` to check | `toWorld` maps `LOCAL → WORLD` and fixes `WORLD`, `toLocal` the reverse; `toDefinition` "answers the carried source's `DEFINITION` locus for any frame"; **L-1** a `DEFINITION` locus "carries the authored cells" | the only `DEFINITION` factory takes a span and no cells, so a re-derived `DEFINITION` locus has `cells().isEmpty()` and contradicts L-1; the spec never states the full frame matrix (what `DEFINITION`/`KEY` answer from `toWorld`, whether a `source` survives a `LOCAL→WORLD` round trip), nor which type carries set-level placement (`Placement.apply(...)` is an ellipsis in §3; the code has `apply(LatticePoint)` only) | **RULE** | the sketch asserts an equality (`toDefinition(...).frame() == DEFINITION`) whose result is under-specified, and L-1 cannot hold for any implementation that has only `definition(SourceSpan)`. |
| B-3 | `05` §3 `Placement` row (`compose`, `inverse` unannotated), §4.6 Composition / Inverse / Re-siting laws | `space/Placement.java:15` `public @Nullable Placement compose(Placement other)`, `:27` `public @Nullable Placement inverse()` | `P.compose(Q) = (o_P ∘ o_Q, o_P(t_Q) + t_P)`; `P.inverse() = (o⁻¹, −o⁻¹(t))`; "`P.inverse().compose(P) == IDENTITY`"; the re-siting law is an identity | both are nullable for in-lattice-but-overflowing inputs: probe → `new Placement(Orientation.IDENTITY, LatticePoint.of(LatticePoint.MIN_XZ,0,0)).inverse()` is `null` (because `−MIN_XZ = 33_554_432` is off-lattice) and `new Placement(IDENTITY, (MAX_XZ,0,0)).compose(same)` is `null`; `SitingTest:62-64` only asserts the safe case (`assertNotNull(back)`) | **DOC** | the code's `@Nullable` is forced by `05` §1's own "leaving the lattice is absence"; §3 must annotate both members and §4.6 must qualify each law "wherever the terms are non-null" (no R-record covers it). |
| B-4 | `05` §1 (ownership list) and §3 (no row for any of the three); `01` lines 62, 63, 213; `19` R19; `20` §199; `12` §142 | `space/SectionKey.java:5` `record SectionKey(NamespacedId dimension, int x, int y, int z)`, `:7` `static SectionKey of(NamespacedId, LatticePoint, int cellSize)`, `:16` `sectionOf`; `space/ChunkGrid.java:6` `record ChunkGrid(int cellSize, LatticePoint origin)`, `:8` `ADAPTER_CELL_SIZE = 16`, `:16/23/36/48` `chunkOf/boundsOf/chunkCount/chunksOf`; `space/ChunkKey.java:3` `record ChunkKey(int cx, int cy, int cz)` ordered `(cy, cz, cx)` | `05` never names `SectionKey`, `ChunkGrid` or `ChunkKey` — the only mention is §4.1's parenthetical "(the fold uses `SectionKey`, not this)"; `01` line 213 says `SectionKey` is "`(World world, int x, int y, int z)`: the 16³ section index", and `20` §199 fixes it at `x >> 4` | all three ship in `kernel.space` with a **`cellSize` parameter** (`Math.floorDiv(cell, size)`) and a `NamespacedId` in place of `World`; `boundsOf` throws `IllegalArgumentException` off-lattice, `chunkCount` saturates at `Integer.MAX_VALUE`, `chunksOf` returns keys in `ChunkKey` order | **DOC** (no decision covers it) | the homes are already blessed (`19` R19 "`ChunkGrid` remains a space utility"; `22` §82 lists `SectionKey` with `LatticePoint`/`LatticeKey`) but `05`, the module of record for U1, does not define them, so a U1 regenerated from `05` loses the fold's ordering type, the adapter's `chunkGrid()`/`chunk(ChunkKey)` and `16` §53–54/§176; the `cellSize` parameter and the `World`→`NamespacedId` lag (R35) must be written down. |
| B-5 | `05` §6 ("expands `boxes()` in canonical cell order"), §8 Q-S4 option (A) | `space/CuboidSet.java:107-109` `boxes()` (returns the canonical box list, no `cells()` anywhere in the package) | a walk "expands `boxes()` in canonical cell order" and charges one read per cell; Q-S4(A) repeats "callers expand `boxes()` in `LatticePoint` order" | canonical order is a **box** order; concatenating each box's cells leaves the global cell order broken — for `{[(0,0,0),(1,2,1)), [(1,0,0),(2,1,1))}` the box order gives cells `(0,0,0),(0,1,0),(1,0,0)` while the set's `(y,z,x)` order is `(0,0,0),(1,0,0),(0,1,0)` | **RULE** | the sentence is false as a concatenation (only the total `volume()` charge survives), so the walk's order — which `ReadSet`/`closureDigest` in `11`/`12` may depend on — cannot be regenerated from `05`. |
| B-6 | `05` §1 ("Owns … diagnostic frames (`LocusFrame`, `Locus`, `LocusMap`)"), §8 ("`space` depends on `base` only"); `02` §2 ("There is no cycle at package level"), §9.1; `02` line 51 ("`ReadScope` in `base`"); `13` §824–829 | `base/Locus.java:3` `import dev.vfyjxf.cumulus.kernel.space.CuboidSet;`, `base/LocusFrame.java`, `base/KeyLocus.java`; `base/Finding.java:3-5` and `base/ReadScope.java:3-5` (`context.ContextKey`, `occupant.EntityWindow`, `space.CuboidSet`); `space/SectionKey.java:3` imports `base.NamespacedId` | U1 owns the frames; `13` says "Depends on … **U1** (`Locus`, `LocusFrame` …, `KeyLocus` is U1's)"; `02` §2 forbids a package cycle and §9.1 lets a module import only packages above it plus `base` | the frames live in `kernel.base`, so `base → space` (Locus, Finding, ReadScope) and `space → base` (SectionKey) hold simultaneously — a cycle — and `base → occupant`/`context` hold too | **BOTH** | the rule that replaces both: `base` is the *value-record floor* and may reference `space`/`occupant`/`context` value types (`02` line 51 already places `ReadScope` in `base`, and the code's `base.ReadScope` carries `CuboidSet`/`ContextKey`/`EntityWindow`), so `02` §2's "no cycle" sentence and §9.1 must be restated; and either `05` §1 records the frames as `base` values (code's choice, with `Label`/`Finding`) or `Finding`/`Label` move to `kernel.finding` per `00` §5 and the frames to `space` per `05` §1/`13` — today the spec asserts both homes at once. |
| B-7 | `05` §3 rows for `Orientation`, `LatticeKey`, `LatticePoint`, `CuboidSet` | `space/Orientation.java:60` `public Orientation then(Orientation other)`, `:131` `public int id()`; `space/LatticeKey.java:15/19/23` `x()`, `z()`, `y()`; `space/LatticePoint.java:7-10` `MIN_XZ`, `MAX_XZ`, `MIN_Y`, `MAX_Y`; `space/CuboidSet.java:115` `public boolean isEmpty()` | §3's surface is the type's whole public surface; it lists none of these | all five exist and are load-bearing: `OrientationTest:30-31,45,54-56,109` call `then` (`assertEquals(right.compose(left), left.then(right))`) and `:23` calls `id()`; `CuboidSetTest:19,101` and `05` §6 call `isEmpty()`; `ChunkGrid`/tests use `LatticePoint.MIN_XZ`…; the range constants are the only public statement of the packing range | **CODE** | the code is right — `compose` reads backwards ("other first"), so a forward-composing `then` is needed and the tests depend on it; §3 must gain `then`, `id`, `LatticeKey.x()/y()/z()`, `CuboidSet.isEmpty()` and the four range constants. |
| B-8 | `05` §4.1 ("the range is a **guard, not a limit**"), §3 `Cuboid` row `of(LatticePoint)` | `space/Cuboid.java:21-27` `of(LatticePoint cell)` throws `IllegalArgumentException("cell has no successor inside the lattice: " + cell)`; `:33-38` `contains` needs `p < max` | `LatticePoint` holds coordinates in the range; packing is a guard, not a limit on coverage | probe: `LatticePoint.of(LatticePoint.MAX_XZ,0,0)` is legal, `Cuboid.of(thatPoint)` throws IAE, and no non-empty `Cuboid` can contain that cell (`contains` requires `p < max ≤ MAX_XZ`) — the top cell of every axis is unreachable for every footprint, so the coverage range is `[MIN_XZ, MAX_XZ)` (and `[-2048, 2047)` on `y`), one narrower than the point range | **DOC** | the spec must state the coverage range and either keep the throwing factory (documented) or make it `@Nullable`; today `of(LatticePoint)` is listed as an ordinary factory and "a guard, not a limit" is only true for points. |
| B-9 | `05` §8 Q-S2 (open), §2/§3/§4.5 (three constants, `of(Turn,Tilt,Flip)`, "canonical name") | `space/Flip.java:3-10` `enum {NONE, LEFT_RIGHT, FRONT_BACK}` + `isMirror()`; `Orientation.java:46` `of(Turn, Tilt, Flip)`; `:127-129` `isMirror()` from the ordinal's low bit | Q-S2 offers (A) "keep three plus the canonical triple" or (B) "two-valued `Flip` with absence for 'no reflection'" | ships (A) exactly, and the 72→48 surjection with 24 doubled fibres is real: probe reads `MATRIX` (48 distinct codes), `ORDINAL` (48 slots) and enumerates all 72 triples → 24 fibres of size 1, 24 of size 2 | **RULE** | an open question that changes the *shape* of a type cannot be left open in a file used as the sole regeneration input, and option (B) contradicts §3's third parameter and §4.5's canonical name. |
| B-10 | `05` §9 row 1 (P1 falsification) | `space/LatticeKey.java:8-13` `of(LatticePoint)` masks `x & 0x3ff_ffff`; `space/LatticePoint.java:14-18` the ctor refuses out-of-range; `LatticeKeyTest:36-40` | "Pack from raw ints rather than a `LatticePoint`: `(33_554_432,0,0)` and `(-33_554_432,0,0)` both mask to `0x2000000` … Test `key(2^25) != key(-2^25)`" | the discriminating pair cannot be built: `LatticePoint.of(33_554_432,0,0)` throws IAE (probe), and the test that is in the tree compares ±33_554_431 (which do *not* collide under a raw-mask mutation either), so nothing falsifies the mutant | **DOC** | restate the falsification on the raw constructor (`new LatticeKey(((long)(33_554_432 & 0x3ff_ffff) << 38))` must differ from the encoding of `−33_554_432`) — `of`'s masking is injective *only because* `LatticePoint` refuses `+2^25`. |

Load-bearing quotes the table compresses:

- `LatticePoint.java:14-18` — `public LatticePoint { if (!inLattice(x, y, z)) throw new IllegalArgumentException("point leaves the lattice: " + x + ", " + y + ", " + z); }`
- `LatticeKey.java:9-11` — `long packed = ((point.x() & FIELD_MASK) << 38) | ((point.z() & FIELD_MASK) << 12) | (point.y() & Y_MASK);` with `FIELD_MASK = 0x3ff_ffffL`, `Y_MASK = 0xfffL`.
- `Siting.java:38-47` — `toLocal` subtracts `origin` **and** `placement.offset()` before `inverse().apply(...)`, i.e. `o⁻¹(w − origin − t)`, exactly §4.6's formula.

## 3. Rulings needed

**R-B1 — what `LocusMap` answers, and what a `DEFINITION` locus carries (from B-1/B-2).**
The spec requires the type but never fixes its frame matrix.
- (A) `toDefinition(l) = Locus.definition(l.source())`, `null` when `source == null`; L-1 becomes
  "`DEFINITION` implies `source != null`" and drops "carries the authored cells";
  `toWorld`/`toLocal` accept `LOCAL` and `WORLD` only, fix their own frame, preserve `source`, answer
  `null` for `KEY`/`DEFINITION` and off-lattice.
- (B) `DEFINITION` gains a `cells` component (factories `definition(CuboidSet, SourceSpan)`), and
  `toDefinition` re-derives those cells by inverse-mapping when the locus is `LOCAL`/`WORLD` and the
  source's own `LOCAL` locus is known, else `null`.
- (C) keep L-1 and give the conversion to `Siting`/`Placement` instead (`Siting.toWorld(CuboidSet)`,
  `@Nullable`), deleting `LocusMap`.

*Recommendation: (A)* — it is the only option implementable from the members that exist
(`Locus.definition`, `CuboidSet`, `Orientation.apply(CuboidSet)`, `CuboidSet.translate`), it keeps
`Locus` a 4-component value, and it makes §5 sketch 3's assertion exact; (B) buys a cell-carrying
`DEFINITION` no sketch asks for, (C) drops a name `01` and `13` already publish.

**R-B2 — the canonical cell order of a walk (from B-5).**
- (A) `05` §6 says: boxes in canonical **box** order, cells inside a box in `(y,z,x)`, and *no* claim
  of a global order; the charge stays one read per cell, `volume()`.
- (B) add `CuboidSet.cells()` (Q-S4(B)) returning a `Stream<LatticePoint>`/`List` in global `(y,z,x)`
  order, with a glossary row and the per-cell charge made explicit.
- (C) keep the sentence but move the obligation to U7 ("the walk merges the boxes").

*Recommendation: (A), adding (B) only if a unit's digest ends up depending on the global cell
sequence* — (A) is what a caller can already do with today's `boxes()` and it matches the code;
(B) is the only way to get a canonical **cell** order without a heap in every caller; (C) keeps the
sentence that the counterexample disproves, so it should be replaced rather than reinterpreted.

**R-B3 — close Q-S2 (from B-9).** The code and three sections of `05` already answer it.
*Recommendation: mark Q-S2 resolved as (A)* ("three constants plus the canonical triple"), delete
option (B), and state in §4.5 that `Flip` is improper-valued while `Tilt`/`Turn` are proper, so
`of(Turn,Tilt,Flip)` is total on 72 triples over 48 elements. Q-S4/Q-S5 should be closed at the same
time — the code answers Q-S5(A) (`LatticePoint` bounded, defensive nulls, ctor throws on out-of-range
input) and Q-S4 "no `cells()` member exists in the kernel".

## 4. Documentation gaps that block regeneration

1. **Undocumented public members and constants** (from B-7): `Orientation.then`, `Orientation.id`,
   `LatticeKey.x()/y()/z()`, `LatticePoint.MIN_XZ/MAX_XZ/MIN_Y/MAX_Y`, `CuboidSet.isEmpty()` — plus
   `Cuboid.of(LatticePoint)`'s `IllegalArgumentException` (B-8) and `ChunkGrid.boundsOf`'s.
2. **Three shipped types absent from the owning module** (from B-4): `SectionKey`, `ChunkGrid`,
   `ChunkKey`, with `cellSize` parameters and the `NamespacedId`-in-place-of-`World` lag; `05` §1's
   ownership list and §3's table need a row each, and `01` line 213's "16³" must become "`cellSize`
   (16 from the adapter)".
3. **`Turn.compose`'s direction is never stated** (`Turn.java:11-13` is index addition and yaw is
   abelian, so no reading is observable) while `Orientation.compose`'s direction *is* stated and
   opposite in reading — a regenerated `Turn` could be written either way; one sentence in §4.4
   ("`t.compose(u)` applies `u` after `t`") removes the ambiguity.
4. **Unimplementable as written:** B-2 (L-1 vs `definition(SourceSpan)`), B-5 (the concatenation),
   B-10 (the falsification's test cannot be written against the frozen surface).
5. **Q-S4/Q-S5/Q-S2 left open** in §8 while §2/§3/§4/§6 already assume an answer; a regeneration
   input must not contain an open question that changes a type's shape (B-9, R-B3).
6. **Package/unit mismatch (class 3, this slice's share):** `kernel.space` exists as `00` §5 says;
   the other units do not line up — `kernel.expectation` ships as `kernel.predicate`, `kernel.document`
   (U6) and `kernel.remedy` (U10) have no code at all, and U9's types live in `kernel.base`. U1's own
   contribution to the mismatch is the frames (B-6): `05` §1 and `13` §824 both say U1 owns
   `LocusFrame`/`Locus`/`KeyLocus`, the code puts all three in `base`, and `base` also needs `space`
   (Locus, Finding, ReadScope carry `CuboidSet`/`LatticePoint`/`ChunkKey`) so the two homes cannot both
   be true. A tree regenerated under `05`/`13` would put `Locus`, `LocusFrame`, `KeyLocus` and
   `LocusMap` in `space` and move `Label`/`Finding` to `kernel.finding`, with only `space → base`
   left; a tree regenerated under `02` line 51 would keep them in `base` and must then explain the
   `base → space`/`occupant`/`context` edges in `02` §2.

## 5. Checked and aligned

Verified against the compiled kernel (probe, 0 failures) or by reading the producers:

- **`LatticeKey` packing ≡ §4.1**: slices `63–38 x` / `37–12 z` / `11–0 y` with `0x3ff_ffff`/`0xfff`
  masks; decoders `packed >> 38`, `packed << 26 >> 38`, `packed << 52 >> 52`; ranges
  `[-33_554_432, 33_554_431]`, `[-2_048, 2_047]`; the published values are exact — `of(0,0,0)=0`,
  `of(0,1,0)=1`, `of(0,-1,0)=4095`, `of(1,0,0)=2^38`; 20 000 in-range round trips; `compareTo` is
  `(y,z,x)` and agrees with `LatticePoint.compareTo` on 5 000 random pairs, while `packed` order
  disagrees on 1 261 of them (so §4.1's "never sort by `packed`" is verified, and P1's bijection
  holds — the masks are no-ops on validated points).
- **Every claimed operation exists**: `LatticePoint` (`of`, `ORIGIN`, `inLattice`, `offset`, `key`,
  `compareTo`), `LatticeKey` (`of`, `toPoint`, `compareTo`), `Cuboid` (`of`×3, `isEmpty`, `contains`×2,
  `intersects`, `intersect`, `bounding`, `translate`, `volume`, `asSet`), `CuboidSet` (`empty`,
  `of`×2, `boxes`, `boxCount`, `volume`, `bounds`, `contains`, `containsAll`, `intersects`,
  `union`×2, `intersect`×2, `subtract`×2, `translate`), `Heading` (`opposite`, `axis`, `step`),
  `Turn` (`compose`, `inverse`), `Tilt` (`up`), `Flip` (`isMirror`), `Orientation` (all 13 listed
  members, plus the two in B-7), `Placement` (`IDENTITY`, `compose`, `inverse`, `apply`),
  `Siting` (`of`×2, `withOrigin`, `withPlacement`, `anchoring`, `toWorld`, `toLocal`), and `Locus`'s
  six factories/`withSource` (in `base`, B-6). Nullability is as stated everywhere except B-3.
- **The 48 are all reachable and `apply` obeys the stated law.** Reflective read: `MATRIX` holds 48
  **distinct** codes and `ORDINAL` 48 filled slots, so `of(Turn,Tilt,Flip)` is total on all 72 triples
  and normalises to the canonical ordinal — probe enumerates them: 24 elements with one name, 24 with
  two (one per improper element), total 72, and `of(turn,tilt,NONE).ordinal() == turn*12 + tilt*2`
  for all 24 proper names (`OrientationTest.canonicalTripleReproducesEveryElement` covers the mirrored
  half). §4.4's matrices are the code's: `CLOCKWISE (1,2,3)→(-3,2,1)`, `HALF→(-1,2,-3)`,
  `COUNTERCLOCKWISE→(3,2,-1)`, all four fixing `UP`; the six `Tilt.up()` table entries equal
  `apply(Heading.UP)`; `ROLL_LEFT²=(−1,−2,3)` (half turn about `Z`), `FRONT_BACK.compose(LEFT_RIGHT)`
  is `Turn.HALF/LEVEL/false` — exactly §4.4's two claims. `apply(Cuboid)` matches §4.5's image rule
  **cell-wise**: for all 48 orientations × 4 boxes the cells of the image box are exactly the images of
  the source cells (`1 − max` / `1 − min` on negative axes), extents and `volume()` survive, and
  `apply(CuboidSet)` canonicalises one image per box. `compose`/`inverse` are total on `Orientation`.
- **Placement/Siting laws**: `P.compose(Q).apply(p) == P.apply(Q.apply(p))` for one non-trivial pair
  over a 10³ grid of points (the 48×48 closure of `Orientation.compose` is covered by
  `OrientationTest.compositionCloses`); `inverse().compose(P) == IDENTITY` in the safe case;
  `Siting.toWorld` round-trips, is affine not linear (§4.6's "+X delta of 2 lands on +Z" claim), and
  answers `null` at the lattice edge (`SitingTest.leavingTheLatticeIsAbsence`, and probe:
  `Siting.of((MAX_XZ,0,0), yaw).toWorld((0,0,-1)) == null`); `anchoring` puts one cell on another;
  §4.6's `7×6×7` CLOCKWISE range claim (`x ∈ [-6,1)`, `z ∈ [0,7)`) follows from the verified
  CLOCKWISE matrix, and §5 sketch 3's world cells (`[10,11)×[64,65)×[-2,0)`) were reproduced by the
  probe through `Orientation.apply` + `Siting.toWorld`.
- **`CuboidSet` is canonical and its algebra is exact.** CS-1/CS-2/CS-3/CS-4 checked over 400 random
  multisets (no empty box, pairwise disjoint on all three axes, strictly increasing canonical order,
  `of(of(x).boxes()) == of(x)` — "touching ones stay apart" on `x`, `y` and `z`), and `union`,
  `intersect`, `subtract`, `translate`, `contains`, `containsAll`, `volume`, `bounds` agree pointwise
  with the corresponding set algebra on the same 400 cases; empty is dropped and `contains` is false
  inside it; `asSet()`/`equals` are structural as §4.3 says; §5's door sketch is exact
  (`[3,0,0,5,2,1)` = 4 cells, minus `[3,0,0,5,1,1)` = 1 box of 2 cells) and §9's CS-2 pair
  (`boxCount() == 2`) holds.
- **Canonical-order claims are implementable from the comparators that exist**: cell order `(y,z,x)`
  is `LatticePoint.compareTo`/`LatticeKey.compareTo`; box order is `(min, max)` in that same cell
  order and `CuboidSet.of` sorts by it; `SectionKey` orders `dimension → x → z → y`, matching `12`
  §142; `ChunkKey` orders `(cy, cz, cx)`, matching `01` line 62; U9's "canonical first cell" is
  `min` over `boxes()` (`base/Finding.java:131-139`), which is what the box order gives. `Orientation`'s
  equality is matrix equality (the ordinal table is a bijection), so `Siting` is a sound scope value
  for `08` §4.1's `(world, owner, siting)` key and `01` line 58's row.
- **`Siting` vs `01`/`08`**: matches `01` line 58 (`(LatticePoint origin, Placement placement)`,
  `toWorld(local)`/`toLocal(world)`), and `08` §3/§342 consume exactly `origin()`/`placement()` as
  components of `ClaimRow`/`SettleRequest` (`claim/ClaimRow.java:12`), with value equality; the only
  lag there is the known R35 one (`ClaimRow` has no world), not a U1 defect.
- **`05` §7's proofs P2/P4/P6** are the properties the probe re-ran (canonical set, volume
  invariance, round trip); P3/P5's size and determinism claims are the `MATRIX`/`ORDINAL`/`all()`
  results above. `space` imports only `base` (`SectionKey.java:3`), as §8 says.
