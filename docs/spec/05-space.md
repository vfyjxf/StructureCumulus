# 05 — Space

> Where a cell is, what a box is, how a structure turns, which frame a diagnostic points into.
> Serves R-01, R-04, R-05, R-07; enforces I-3, I-5. Package `kernel.space`.

## 1. Scope

Owns the lattice (`LatticePoint`, `LatticeKey`), footprint algebra (`Cuboid`, `CuboidSet`), the
48-element group (`Heading`, `Turn`, `Tilt`, `Flip`, `Orientation`), placement (`Placement`,
`Siting`), diagnostic frames (`LocusFrame`, `Locus`, `LocusMap`), and the canonical cell and box
orders later units inherit. Not owned: `SourceSpan`, `Finding`, label ordering (U9);
`OrientationPolicy`, anchor roles, blueprint bounds (U5); `AnchorCandidate`, siting choice, world
reads, budgets (U7); segment assembly and provenance (U6).

- **Pure arithmetic**: no world access, clock, randomness or I/O (I-4, I-15).
- **No floating point, no half cells**: turns are about the local origin `(0,0,0)`, and
  `Placement.offset` is the only re-centring.
- **Lattice isometries only**: 48 orientations plus an integer translation.
- **Leaving the lattice is absence**: `@Nullable`, never a wrap, a clamp or an exception.

## 2. Vocabulary

| Term | Type | Contract |
| --- | --- | --- |
| Lattice point | `LatticePoint` | `int (x,y,z)` in the packing range; `(0,0,0)` is the declared origin |
| Lattice key | `LatticeKey` | One `long`, x:26 / z:26 / y:12; identity, comparable in cell order |
| Axis | `Axis` | `X`, `Y`, `Z` |
| Cuboid | `Cuboid` | Half-open `[min,max)`; empty when `min == max` |
| Cuboid set | `CuboidSet` | Canonical, disjoint, sorted, empty-free box list |
| Heading | `Heading` | `NORTH,SOUTH,WEST,EAST,UP,DOWN` |
| Turn | `Turn` | Yaw quarter turn about `Y`; cyclic, order 4 |
| Tilt | `Tilt` | Which way local up points; six rotations |
| Flip | `Flip` | `NONE` plus two reflections |
| Orientation | `Orientation` | One of exactly 48 signed axis permutations |
| Placement | `Placement` | `(Orientation, LatticePoint offset)` |
| Siting | `Siting` | `(LatticePoint origin, Placement)` |
| Locus frame | `LocusFrame` | `DEFINITION`, `LOCAL`, `WORLD`, `KEY` |
| Locus | `Locus` | Frame + `CuboidSet` + optional `KeyLocus` + optional `SourceSpan` |
| Locus map | `LocusMap` | Frame conversion for one `Siting` |

## 3. API design

Behaviour is §4's; a constructor throws only for a malformed value (`04` §5).

| Type | Signature surface |
| --- | --- |
| `LatticePoint` | `record (int x, int y, int z) implements Comparable<LatticePoint>`; `static of(int,int,int)`, `ORIGIN`, `static boolean inLattice(int,int,int)`, `MIN_XZ`, `MAX_XZ`, `MIN_Y`, `MAX_Y` (the packing range), `@Nullable offset(int,int,int)`, `key()`, `compareTo` |
| `LatticeKey` | `record (long packed) implements Comparable<LatticeKey>`; `static of(LatticePoint)`, `x()`, `z()`, `y()`, `toPoint()`, `compareTo` delegating to `toPoint()` (`y,z,x`) |
| `Cuboid` | `record (LatticePoint min, LatticePoint max) implements Comparable<Cuboid>`; `static of(int minX,int minY,int minZ,int maxX,int maxY,int maxZ)`, `of(LatticePoint,LatticePoint)`, `of(LatticePoint)`; `isEmpty`, `contains(LatticePoint)`, `contains(Cuboid)`, `intersects`, `@Nullable intersect`, `bounding`, `@Nullable translate(int,int,int)`, `long volume()`, `asSet()` |
| `CuboidSet` | `static empty()`, `of(Cuboid...)`, `of(Collection<Cuboid>)`; `List<Cuboid> boxes()`, `boxCount()`, `isEmpty()`, `long volume()`, `@Nullable Cuboid bounds()`, `contains(LatticePoint)`, `containsAll(CuboidSet)`, `intersects(CuboidSet)`, `union`, `intersect`, `subtract` (set and cuboid forms), `@Nullable translate` |
| `Heading` | `enum { NORTH, SOUTH, WEST, EAST, UP, DOWN }`; `opposite()`, `axis()`, `step()` |
| `Turn` | `enum { NONE, CLOCKWISE, HALF, COUNTERCLOCKWISE }`; `compose(Turn)`, `inverse()` |
| `Tilt` | `enum { LEVEL, PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, INVERTED }`; `Heading up()` |
| `Flip` | `enum { NONE, LEFT_RIGHT, FRONT_BACK }`; `boolean isMirror()` |
| `Orientation` | `record (int ordinal) implements Comparable<Orientation>`; `IDENTITY`, `static of(Turn,Tilt,Flip)`, `compose`, `then` (this element first), `inverse`, `Heading apply(Heading)`, `@Nullable apply(LatticePoint)`, `@Nullable apply(Cuboid)`, `@Nullable apply(CuboidSet)`, `Turn turn()`, `Tilt tilt()`, `isMirror()`, `id()`, `static List<Orientation> all()` |
| `Placement` | `record (Orientation, LatticePoint offset)`; `IDENTITY`, `@Nullable compose(Placement)`, `@Nullable inverse()`, `@Nullable apply(...)` — the offset is a lattice point, so a composition can leave the lattice and answers `null` |
| `Siting` | `record (LatticePoint origin, Placement)`; `static of(origin, Orientation)`, `of(origin, Placement)`, `withOrigin`, `withPlacement`, `static @Nullable anchoring(anchorTarget, anchorLocal, Placement)`, `@Nullable toWorld(...)`, `@Nullable toLocal(...)` |
| `SectionKey` | `record (World world, int x, int y, int z) implements Comparable<SectionKey>`; `static of(World, LatticePoint cell, int cellSize)`, `SectionKey sectionOf(LatticePoint, int cellSize)` (an instance method: it reuses this key's world); compares by world, then `x`, then `z`, then `y`. The 16³ index of one world; not a cache key |
| `Locus` | `record (LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan)`; `static definition(SourceSpan)`, `local(CuboidSet)`, `local(CuboidSet, SourceSpan)`, `world(CuboidSet)`, `key(InstanceId, NamespacedId)`, `withSource(SourceSpan)` |
| `LocusMap` | `record (Siting)`; `static of(Siting)`, `@Nullable toWorld(Locus)`, `@Nullable toLocal(Locus)`, `toDefinition(Locus)` |

## 4. Semantics

### 4.1 The lattice, in one `long`

Minecraft's layout, no Minecraft type (I-1): three signed two's-complement fields in disjoint
slices.

| Bits | Width | Field | Range |
| --- | --- | --- | --- |
| 63–38 | 26 | `x` | `[-33_554_432, 33_554_431]` |
| 37–12 | 26 | `z` | same |
| 11–0 | 12 | `y` | `[-2_048, 2_047]` |

```java
long packed = ((long)(x & 0x3ff_ffff) << 38) | ((long)(z & 0x3ff_ffff) << 12) | (y & 0xfffL);
int x = (int)(packed >> 38);          // bit 63 is x's sign
int z = (int)(packed << 26 >> 38);    // move bit 37 up, then sign-extend
int y = (int)(packed << 52 >> 52);    // 12-bit signed
```

`LatticePoint` holds coordinates **inside this range**, and that check is what makes packing
injective: the mask discards bits, so `(33_554_432,0,0)` and `(-33_554_432,0,0)` share a 26-bit
pattern and only the second is a lattice point. A host's coordinates (border `±30_000_000`, height
`-64..320`) all fit; the range is a guard, not a limit.

`LatticeKey` equality is `packed` equality and `key()` is a bijection. It **is** `Comparable`, and
`compareTo` delegates to `LatticePoint.compareTo` — `(y,z,x)` ascending, layer then row then column;
`Cuboid` sorts the same way. The numeric `packed` order is **not** that order: `y` is sign-extended
in the low bits, so `of(0,0,0)=0`, `of(0,1,0)=1`, `of(0,-1,0)=4095`, `of(1,0,0)=2^38`; never sort
by `packed`. (`08` requires the natural order for claim rows; the fold uses `SectionKey`, not this.)

### 4.2 Cuboid

`[min,max)` on every axis; `min == max` contributes nothing, so a cuboid is *empty* but legal.
Containment is set containment: an empty cuboid holds no point and sits inside every cuboid.

| Operation | Rule |
| --- | --- |
| `contains(p)` / `contains(b)` | `min ≤ p < max` (`false` if empty) / `b` empty, or `min ≤ b.min`, `b.max ≤ max` |
| `intersects(b)` / `intersect(b)` | `min < b.max` and `b.min < max` on all axes; `intersect` is `null` once `lo ≥ hi` |
| `bounding(b)` / `translate(d)` | min of mins, max of maxes / both corners `+ d`, `null` off-lattice |
| `volume()` | `∏ (max − min)`, saturating |

No `Cuboid.union` and no `Cuboid.subtract`: a union of two cuboids is not a cuboid, a difference is
up to six of them, so both live on `CuboidSet` and the cuboid keeps only the hull, `bounding` — one
name for both operations widens a footprint by a shell (I-10). Volume saturates because the
lattice's own volume is `2^26 · 2^12 · 2^26 = 2^64`, one past `Long.MAX_VALUE`. A host box
inclusive at both ends `[a,b]` is `[a,b+1)` here: copying `b` as `max` loses the last cell.

### 4.3 CuboidSet: the canonical form

No cell enumeration; every member is box arithmetic (I-3). `of` is the only door and the body of
every algebra member:

1. **Drop empties.**
2. **Cut at x, then z** at every distinct boundary, leaving slab-columns.
3. **Merge along y inside a column** where intervals overlap; touching ones stay apart.
4. **Sort** by the canonical box order; never merge two boxes into their hull.

**CS-1** no empty box, **CS-2** pairwise disjoint, **CS-3** canonical order, **CS-4** idempotent,
**CS-5** irredundant. Irredundance, not minimality: a minimum cover is not unique, so it cannot be
canonical.

**Equality is structural**: two inputs denoting the same cells may canonicalise differently, and
recovering a decomposition from a cell set would mean enumerating cells. Nothing needs it — every
set comes from `of`, `union`, `intersect`, `subtract` or `translate`, so equal derivations give
equal values; semantic equality is `a.containsAll(b) && b.containsAll(a)`. Cost `O(n log n)`
boundaries, `O(n²)` pieces worst case, nothing per cell.

### 4.4 Headings and the three generator families

```java
// step: NORTH(0,0,-1) SOUTH(0,0,1) WEST(-1,0,0) EAST(1,0,0) UP(0,1,0) DOWN(0,-1,0)
Turn  NONE(x,y,z) CLOCKWISE(-z,y,x) HALF(-x,y,-z) COUNTERCLOCKWISE(z,y,-x)   // fixes UP
Tilt  LEVEL(x,y,z) PITCH_UP(x,-z,y) PITCH_DOWN(x,z,-y)                      // up: UP, SOUTH, NORTH
      ROLL_LEFT(-y,x,z) ROLL_RIGHT(y,-x,z) INVERTED(x,-y,-z)                // up: WEST, EAST, DOWN
Flip  NONE(x,y,z) LEFT_RIGHT(x,y,-z) FRONT_BACK(-x,y,z)                     // the two improper
```

`Turn` is cyclic of order 4 (`HALF` self-inverse). `Tilt` is a **transversal**: six proper
rotations with distinct `up()` images, one per coset of the yaw subgroup. Neither `Tilt` nor `Flip`
is closed under composition (`ROLL_LEFT²` is a half turn about `Z`; `FRONT_BACK ∘ LEFT_RIGHT` is
`Turn.HALF`), which is why they are generators, not group values.

### 4.5 Orientation: one element of 48

An orientation is a **signed axis permutation** `world(a) = s_a · local(π(a))`: a permutation of
`{X,Y,Z}` plus one sign per world axis. There are `6 · 8 = 48`, and that is the whole group (P3).

```
ordinal = (turn.ordinal() * 6 + tilt.ordinal()) * 2 + (isMirror() ? 1 : 0)      // 0..47
```

Turn-major, then tilt, then parity; `IDENTITY = 0`, `all()` ascending, `compareTo` by ordinal. An
element's canonical name is `(turn(), tilt(), isMirror() ? FRONT_BACK : NONE)`. Parity is the
determinant: `isMirror()` is true exactly when the flip is not `NONE`, so `INVERTED` — a half turn
about `X` — is proper, and a mirrored placement is never reachable by turning.

**The generators are not a unique name for the 48.** `4 · 6 · 3 = 72` triples over 48 elements: one
name per proper element (parity `NONE`), two per improper — `of(HALF, LEVEL, LEFT_RIGHT)` equals
`of(NONE, LEVEL, FRONT_BACK)` — a surjection with 24 doubled fibres, and the canonical triple is
the section that makes it a bijection. Storage is never the triple: there
`(π₁,s₁) ∘ (π₂,s₂) = (π₁∘π₂, s₁ + π₁(s₂))` is one step, while the triple does not compose
componentwise.

**Image rules.** Point `world(a) = s_a · local(π(a))`; cuboid per world axis, source `c = π(a)`:

```
s_a = +1:  lo = min[c]        hi = max[c]
s_a = -1:  lo = 1 - max[c]    hi = 1 - min[c]
```

The `1 −` is the whole difference between correct and one cell short: negation sends `c` to `−c`,
so `[min,max)` maps to `[-max+1,-min+1)`; mapping the stored corners gives `[-max,-min)`, which
holds a cell nobody's image is and drops the image of `min`. Extents survive, so **an orientation
never changes a volume**. For a set: one image per box, then `of(...)` — a bijection preserves
disjointness, so it only sorts.

### 4.6 Placement and siting

```
Placement.apply(q) = o(q) + t                    // o = orientation, t = offset
Siting.toWorld(p)  = origin + o(p) + t           // componentwise, checked
Siting.toLocal(w)  = o⁻¹(w − origin − t)
```

| Law | Statement |
| --- | --- |
| Composition | `P.compose(Q) = (o_P ∘ o_Q, o_P(t_Q) + t_P)` — `Q` first; `null` when the sum leaves the lattice |
| Inverse | `P.inverse() = (o⁻¹, −o⁻¹(t))`; when both are non-null, `P.inverse().compose(P) == IDENTITY` |
| Linearity | `o(p.offset(d)) == o(p).offset(o(d))` |
| Affine equivariance | `s.toWorld(p.offset(d)) == s.toWorld(p).offset(o(d))` |
| Re-siting | `s.withPlacement(P.compose(Q)).toWorld(q) == s.withPlacement(P).toWorld(Q.apply(q))` |

The fourth law bites: **a local delta must be oriented before it is added to a world point.** A
hinge at world `(10,64,-5)` with a local `+X` delta of 2 moves along `+Z` under `Turn.CLOCKWISE`;
`toWorld` is affine, never linear.

`anchoring(target, local, P)` uses `origin = target − (o(local) + t)` so one cell lands on another
(I-5); which cell and which orientation are U5's. A `7 × 6 × 7` blueprint turned `CLOCKWISE` about
the origin lands in `x ∈ [-6,1)`, `z ∈ [0,7)`.

### 4.7 Frames, loci, conversion

| Frame | Coordinates mean | Cells | Producer |
| --- | --- | --- | --- |
| `DEFINITION` | character offsets in authored text | the authored cells | U6 validation, design checks |
| `LOCAL` | the blueprint's own grid | the cells the check touched | bay and seam checks (U7) |
| `WORLD` | absolute lattice points at one siting | the same cells, placed | ledger conflicts, repairs (U4, U10) |
| `KEY` | one typed context key of one instance | always empty | state computations, context diagnostics |

- **L-1** `DEFINITION` implies `source != null` and carries the authored cells; `KEY` implies `key != null`, `cells.isEmpty()` and no span.
- **L-2** otherwise every cell lies in that frame (trivially `LOCAL`; `WORLD` by construction from `Siting.toWorld`); containment
  in blueprint bounds is U5's check.
- **L-3** a locus is not a claim and authorises nothing; only a `Claim` carries an obligation.

`toWorld` maps `LOCAL → WORLD` and fixes `WORLD`; `toLocal` the reverse; both preserve `source`
and answer `null` if the result would leave the lattice.
`toDefinition` answers the carried source's `DEFINITION` locus for any frame; every other pair —
geometry from `DEFINITION`, a source never recorded — answers `null`. A label holds one locus in
the frame its check ran in and the renderer projects it: a projection duty, not three fields.

**DEFINITION under segment assembly.** A `SourceSpan` is a range in *one* document. Assembly
composes but never rewrites text, so an assembled blueprint has one definition space per source
document: a `DEFINITION` locus points into one segment's text and is interpretable only with the
provenance naming it (U6's `DocumentProvenance`). U1 never renumbers a range.

## 5. Reference sketch

```java
// 1. Compose and invert; INVERTED is a rotation, not a reflection.
Orientation yaw    = Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE);   // NORTH ↦ EAST
Orientation cap    = Orientation.of(Turn.NONE, Tilt.INVERTED, Flip.NONE);     // UP ↦ DOWN
Orientation tipped = yaw.compose(cap);        // cap first, then the yaw: NORTH ↦ WEST
assert tipped.apply(Heading.NORTH) == Heading.WEST && tipped.apply(Heading.UP) == Heading.DOWN;
assert !tipped.isMirror();                    // half turn about X, determinant +1
assert tipped.inverse().compose(tipped).equals(Orientation.IDENTITY);
assert Orientation.all().size() == 48;

// 2. A door: the opening is the bay minus its frame.
CuboidSet door    = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 2, 1));   // [3,5)x[0,2)x[0,1): 4 cells
CuboidSet frame   = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1));   // lower half: 2 cells
CuboidSet opening = door.subtract(frame);                       // [3,5)x[1,2)x[0,1)
assert opening.boxCount() == 1 && opening.volume() == 2;

// 3. One bay, three frames; the siting turns the south face east.
Siting siting = Siting.of(LatticePoint.of(10, 64, -5), yaw);
Locus authored = Locus.local(CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1)), sourceSpan);
LocusMap map = LocusMap.of(siting);
Locus inWorld = map.toWorld(authored);          // @Nullable: null only off-lattice
assert inWorld != null
        && inWorld.cells().equals(CuboidSet.of(Cuboid.of(10, 64, -2, 11, 65, 0)));
assert map.toLocal(inWorld).equals(authored);   // exact inverse, source span intact
Locus definition = map.toDefinition(inWorld);   // @Nullable
assert definition != null && definition.frame() == LocusFrame.DEFINITION;

// 4. Edges: empty cuboid, a cuboid across the origin, the lattice boundary.
Cuboid empty = Cuboid.of(2, 0, 2, 2, 5, 5);                     // min.x == max.x
assert empty.isEmpty() && !empty.contains(LatticePoint.of(2, 1, 2)) && CuboidSet.of(empty).isEmpty();

Cuboid across = Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.FRONT_BACK)
        .apply(Cuboid.of(-1, 0, -1, 1, 1, 1));                  // @Nullable
assert across != null && across.equals(Cuboid.of(0, 0, -1, 2, 1, 1));  // x is [0,2), not [-1,1)

Siting atEdge = Siting.of(LatticePoint.of(33_554_431, 0, 0), yaw);
assert atEdge.toWorld(LatticePoint.of(0, 0, -1)) == null;       // image is 2^25: absent, not wrapped
```

## 6. Use cases

**Addon author, a two-leaf door.** The bay is `Cuboid.of(3,0,0,5,2,1)`; the leaves are the
`subtract` result above and its mirror image inside the bay. When only the upper leaf is wrong the
expectation replies `Reply.mismatch(...)` with
`Locus.local(openingCells, ctx.locus().source())` — one locus, the authoring range beside it, and
the renderer derives world cells from the attempt's siting; the addon never computes a world
coordinate.

**Host, `/cumulus preview`.** After a `Repair`, each bay becomes world cells via
`LocusMap.of(formed.siting()).toWorld(Locus.local(bay.cells()))`; a `null` answer is a siting that
does not fit the lattice — a finding, not a crash.

**U7, a bounded walk.** A step that must read every cell expands `boxes()` in canonical cell order
and charges one read per cell, total `volume()`; a claim check asks `a.subtract(b).isEmpty()`.

## 7. Proofs

**P1 — packing is invertible.** The fields are disjoint slices whose widths sum to 64, so the
encoder concatenates fixed-width two's-complement fields: distinct in-range triples differ in some
field by a non-zero value in `(-2^26,2^26)` (`x`, `z`) or `(-2^12,2^12)` (`y`), hence in that
field's residue, hence in some bit. The in-range set has `2^64` elements, exactly the number of
`long`s, so the map is a bijection onto all 64-bit patterns and `key()`/`toPoint()` are inverses
everywhere. Without the range check `x` and `x ± 2^26` would mask to one field.

**P2 — the canonical set.** CS-1: step 1 removes exactly the cell-free boxes. CS-2: after step 2 a
piece sits in one slab-column, intervals merge only where they overlap, so survivors are disjoint
and columns are disjoint in `x`–`z`. CS-3: the sort key is total, since two boxes sharing
`min.y, min.z, min.x` are not disjoint. CS-4: on its own output no box spans a boundary and no
interval is mergeable, so the pipeline is the identity. CS-5: a column's intervals are maximal and
each cell belongs to one column. `union` and `subtract` return `of(...)` of explicit box lists,
hence canonical by construction; their cell sets are union and difference because `subtract`
removes one cuboid at a time and the six pieces of `a \ b` (two `x` slabs, then two `y` and two `z`
slabs inside the intersection) are disjoint and cover exactly `a` minus it.

**P3 — the group.** *Closure*: a signed permutation composed with one is one. *Associativity*:
matrix composition. *Identity*: `π = id`, all signs `+`. *Inverse*: `π⁻¹[π[i]] = i`,
`s⁻¹[i] = s[π⁻¹[i]]`; substituting both into the product gives the identity in each coordinate,
both ways round. *Size*: proper rotations are `6 × 4` (where `+Y` goes × residual yaw), and
`Turn × Tilt` realises them bijectively because the six tilts have distinct `up()` images, hence
distinct cosets of the yaw subgroup; with the fixed reflection `FRONT_BACK` that yields the 24
improper elements, and the halves are disjoint, so the group has exactly `24 + 24 = 48`.

**P4 — volume invariance.** Per axis the image extent is `max[c] − min[c]` for `+1` and
`(1 − min[c]) − (1 − max[c]) = max[c] − min[c]` for `−1`; extents are permuted, so the product is
unchanged, and a negative extent cannot arise because `min ≤ max`.

**P5 — determinism.** `ordinal` is `12·turn + 2·tilt + parity` over finite ranges, a total function
onto `0..47` given P3; `all()` is that range in order; `of`, `compose` and `inverse` come from the
twelve generator matrices by formula in a static initialiser. No set iteration, hash order,
identity hash or clock participates, so one source yields the same 48 values on every run and JVM.

**P6 — round trip.** For in-lattice points,
`toLocal(toWorld(p)) = o⁻¹(origin + o(p) + t − origin − t) = p`. For a canonical set a signed
permutation maps boundaries to boundaries and box interiors to interiors, so no image box spans a
boundary; disjointness and interval overlap survive, so re-canonicalising cuts and merges nothing
and only sorts — mapping back restores the list. Canonical sets are likewise closed under
translation.

**P7 — affine equivariance.** `toWorld(p.offset(d)) = origin + o(p) + o(d) + t =
toWorld(p).offset(o(d))`, because an orientation is linear; the placement and re-siting laws follow
by the same expansion.

## 8. Dependencies and open questions

Depends on `base` only: `SourceSpan` now lives in `base` (Q-S1 resolved), so `space` never imports
`finding`.

| Open | Question | Options |
| --- | --- | --- |
| **Q-S1 (resolved)** | `SourceSpan` moved to `base`; `space` depends on `base` only, and `Locus`/`Label` both use the base value. | — |
| **Q-S2** | `Flip` has three constants; the brief counts two. That is what makes 72 names for 48 elements. | (A) keep three plus the canonical triple; (B) two-valued `Flip` with absence for "no reflection". |
| **Q-S3 (resolved)** | `Label` no longer carries a second `SourceSpan`; `Locus.source()` is the only source. | — |
| **Q-S4** | Nothing here iterates cells (I-3), yet U7's walk and U10's overlay need an order. | (A) callers expand `boxes()` in `LatticePoint` order; (B) add `Stream<LatticePoint> cells()` — needs a glossary row and a per-cell charge. |
| **Q-S5** | `LatticePoint` is lattice-bounded with nullable arithmetic; the alternative is free `int` with the range checked only where host data enters. | (A) as written, one invariant kernel-wide; (B) free `int` plus a checked `toWorld`, pushing `null` into U2/U6. |
| **Q-S6 (resolved)** | `Axis { X, Y, Z }` is now a U1 row in `01-glossary.md` §C. | — |

## 9. Falsification

| Claim | The case that breaks it |
| --- | --- |
| P1 | Pack from raw ints rather than a `LatticePoint`: `(33_554_432,0,0)` and `(-33_554_432,0,0)` both mask to `0x2000000` and give one `long`. Test `key(2^25) != key(-2^25)`, and the same pair on `z` and `y ± 2^11`. |
| §4.5, `all()` is 48 distinct | `all().size() == 48 && Set.copyOf(all()).size() == 48`. It breaks as soon as the ordinal is `(turn·6 + tilt)·3 + flip.ordinal()`: 72 indices, `of(HALF, LEVEL, LEFT_RIGHT)` colliding with `of(NONE, LEVEL, FRONT_BACK)`, `all()` repeating. |
| P4, volume never changes | `o.apply(Cuboid.of(0,0,0,1,2,4)).volume() == 8` for all 48, plus the image box itself (`1 × 2 × 4` under `CLOCKWISE` is `4 × 2 × 1`): a cube hides a wrong axis wiring, and a `1 −` correction on a `+1` axis gives extent `max − min + 1`. |
| CS-2 / CS-3 | `of(Cuboid.of(0,0,0,1,1,1), Cuboid.of(1,0,0,2,1,1)).boxCount() == 2`; a merge pass answers 1 and a one-cell claim silently becomes a two-cell claim. Symmetrically, `of(A,B)` with `B ⊂ A` must hold no overlapping pair. |
| §4.5, the image is cell-wise | `Cuboid.of(-1,0,-1,1,1,1)` under `FRONT_BACK` is `[0,2)×[0,1)×[-1,1)`. A corner map answers `[-1,1)`, which holds `-1` — nobody's image — and drops the image of `-1`. |
| P6 | A siting at the lattice edge with a negative offset over a set spanning negative `x`: `toLocal(toWorld(s))` must equal `s`. It fails if `toWorld` clamps or wraps instead of answering `null`, or if `toLocal` adds the offset before inverting. |
| §4.6, `toWorld` is affine | A local `+X` delta of 2 at world `(10,64,-5)` under `Turn.CLOCKWISE` must land on `+Z`. Adding the delta to the world point passes at `IDENTITY` and fails at every other turn. |
