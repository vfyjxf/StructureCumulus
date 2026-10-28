# Module `kernel.space`

> Rank 1. Integer lattice, canonical box sets, the 48-element orientation group, placements,
> sitings, and the frames a finding points into. Pure arithmetic; no world, no occupant, no report.

## 1. Scope

**Owns:** `LatticePoint`, `LatticeKey`, `Axis`, `Cuboid`, `CuboidSet`, `SectionKey`, `Heading`,
`Turn`, `Tilt`, `Flip`, `Orientation`, `Placement`, `Siting`, `LocusFrame`, `KeyLocus`, `Locus`,
`LocusMap`.

**Does not own:** what is in a cell (`world`), what a bay expects (`structure`), which cells a
structure owns (`claim`), the text a source span points into (`core`).

**Imports:** `core` only.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `LatticePoint` | record | in-range `(x,y,z)`; origin is `(0,0,0)`; ordered `(y,z,x)` |
| `LatticeKey` | record | one `long`, x:26 / z:26 / y:12; `Comparable` by point order |
| `Axis` | enum | `x`, `y`, `z` |
| `Cuboid` | record | half-open `[min,max)`; empty is legal; no union/difference |
| `CuboidSet` | final class | normalized, disjoint, sorted, empty-free boxes; equality by cells |
| `SectionKey` | record | `(world, frame, x, y, z)`; the view's section index |
| `Heading` | enum | `north,south,west,east,up,down`; anchor text is matched case-insensitively |
| `Turn` | enum | yaw quarter turn, cyclic order 4 |
| `Tilt` | enum | the six proper rotations with distinct `up()` |
| `Flip` | enum | `none` plus two reflections |
| `Orientation` | record | one of 48 signed axis permutations |
| `Placement` | record | `(orientation, offset)`; nullable composition/inverse |
| `Siting` | record | `(origin, placement)`; local↔world mapping |
| `LocusFrame` | enum | `definition, local, world, key`; `wireName()` is the uppercase tag |
| `KeyLocus` | record | `(InstanceId, NamespacedId)`; one key of one instance |
| `Locus` | record | frame + cells + optional key + optional source span |
| `LocusMap` | record | local↔world conversion for one `Siting` |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.space;

public record LatticePoint(int x, int y, int z) implements Comparable<LatticePoint> {
    public static final int minXz = -33_554_432, maxXz = 33_554_431;
    public static final int minY = -2_048, maxY = 2_047;
    public static final LatticePoint origin;
    public static LatticePoint of(int x, int y, int z);
    public static boolean inLattice(int x, int y, int z);
    public @Nullable LatticePoint offset(int dx, int dy, int dz);
    public LatticeKey key();
    @Override public int compareTo(LatticePoint other);           // y, then z, then x
}

public record LatticeKey(long packed) implements Comparable<LatticeKey> {
    public static LatticeKey of(LatticePoint point);
    public LatticePoint point();
    public int x();  public int y();  public int z();
    @Override public int compareTo(LatticeKey other);             // point order, never packed order
}

public enum Axis { x, y, z }

public record Cuboid(LatticePoint min, LatticePoint max) implements Comparable<Cuboid> {
    public Cuboid { /* min <= max on every axis; both in lattice */ }
    public static Cuboid of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    public static Cuboid ofSize(int x, int y, int z, int sizeX, int sizeY, int sizeZ);
    public static Cuboid of(LatticePoint min, LatticePoint max);
    public static Cuboid of(LatticePoint cell);
    public boolean isEmpty();
    public boolean contains(LatticePoint cell);
    public boolean contains(Cuboid other);
    public boolean intersects(Cuboid other);
    public @Nullable Cuboid intersect(Cuboid other);
    public Cuboid bounding(Cuboid other);
    public @Nullable Cuboid translate(int dx, int dy, int dz);
    public long volume();
    public CuboidSet asSet();
    public int sizeX();  public int sizeY();  public int sizeZ();
    @Override public int compareTo(Cuboid other);
}

public final class CuboidSet {
    public static CuboidSet empty();
    public static CuboidSet of(Cuboid... boxes);
    public static CuboidSet of(java.util.Collection<Cuboid> boxes);
    public java.util.List<Cuboid> boxes();
    public int boxCount();
    public boolean isEmpty();
    public long volume();
    public @Nullable Cuboid bounds();
    public boolean contains(LatticePoint cell);
    public boolean containsAll(CuboidSet other);
    public boolean intersects(CuboidSet other);
    public CuboidSet union(CuboidSet other);
    public CuboidSet union(Cuboid box);
    public CuboidSet intersect(CuboidSet other);
    public CuboidSet intersect(Cuboid box);
    public CuboidSet subtract(CuboidSet other);
    public CuboidSet subtract(Cuboid box);
    public @Nullable CuboidSet translate(int dx, int dy, int dz);
    public boolean contentEquals(CuboidSet other);
    private CuboidSet() {}
}

public record SectionKey(World world, int frame, int x, int y, int z)
        implements Comparable<SectionKey> {
    public static SectionKey of(World world, int frame, LatticePoint cell, int cellSize);
    public SectionKey sectionOf(LatticePoint cell, int cellSize);
    @Override public int compareTo(SectionKey other);             // world, frame, x, z, y
}

public enum Heading {
    north, south, west, east, up, down;
    public Heading opposite();
    public Axis axis();
    public int stepX();  public int stepY();  public int stepZ();
    public static Heading ofStep(int x, int y, int z);
}

public enum Turn {
    none, clockwise, half, counterclockwise;
    public Turn compose(Turn other);       // this after other
    public Turn inverse();
}

public enum Tilt {
    level, pitchUp, pitchDown, rollLeft, rollRight, inverted;
    public Heading up();
}

public enum Flip {
    none, leftRight, frontBack;
    public boolean isMirror();
}

public record Orientation(int ordinal) implements Comparable<Orientation> {
    public static final Orientation identity;
    public static Orientation of(Turn turn, Tilt tilt, Flip flip);
    public static java.util.List<Orientation> all();               // 48, canonical order
    public Orientation compose(Orientation other);                // this after other
    public Orientation inverse();
    public Heading apply(Heading heading);
    public @Nullable LatticePoint apply(LatticePoint point);
    public @Nullable Cuboid apply(Cuboid cuboid);
    public @Nullable CuboidSet apply(CuboidSet cells);
    public Turn turn();  public Tilt tilt();  public boolean isMirror();
    @Override public int compareTo(Orientation other);
}

public record Placement(Orientation orientation, LatticePoint offset) {
    public static final Placement identity;
    public @Nullable Placement compose(Placement other);           // this after other
    public @Nullable Placement inverse();
    public @Nullable LatticePoint apply(LatticePoint point);
    public @Nullable Cuboid apply(Cuboid cuboid);
    public @Nullable CuboidSet apply(CuboidSet cells);
}

public record Siting(LatticePoint origin, Placement placement) {
    public static Siting of(LatticePoint origin, Orientation orientation);
    public static Siting of(LatticePoint origin, Placement placement);
    public Siting withOrigin(LatticePoint origin);
    public Siting withPlacement(Placement placement);
    public @Nullable Siting compose(Siting relative);
    public @Nullable LatticePoint toWorld(LatticePoint local);
    public @Nullable Cuboid toWorld(Cuboid local);
    public @Nullable CuboidSet toWorld(CuboidSet local);
    public @Nullable LatticePoint toLocal(LatticePoint world);
    public @Nullable Cuboid toLocal(Cuboid world);
}

public enum LocusFrame {
    definition, local, world, key;
    public int rank();
    public String wireName();
}

public record KeyLocus(InstanceId instance, NamespacedId key) { }

public record Locus(LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key,
                    @Nullable SourceSpan source) {
    public static Locus definition(SourceSpan source);
    public static Locus definition(CuboidSet cells, SourceSpan source);
    public static Locus local(CuboidSet cells);
    public static Locus world(CuboidSet cells);
    public static Locus key(InstanceId instance, NamespacedId key);
    public boolean hasCells();
}

public record LocusMap(Siting siting) {
    public static LocusMap of(Siting siting);
    public @Nullable Locus toWorld(Locus local);      // local → world; world unchanged; else null
    public @Nullable Locus toLocal(Locus world);      // world → local; local unchanged; else null
}
```

## 4. Laws

### 4.1 The lattice

`LatticePoint` is only constructible inside Minecraft's bit layout: `x,z ∈ [-2^25, 2^25)`,
`y ∈ [-2^11, 2^11)`. `of` and every constructor reject an out-of-range value; `inLattice` answers.
`LatticeKey` packs `x:26 | z:26 | y:12` exactly as Minecraft does, but the kernel names no Minecraft
type. `LatticePoint.compareTo` is `(y, z, x)`; `LatticeKey.compareTo` delegates to `point()`. The
numeric `packed` order is never used as a cell order.

### 4.2 Cuboid

Half-open on every axis. `min == max` is empty but legal. `contains(p)` is
`min ≤ p < max` on all axes and is false for an empty box. `contains(b)` includes every empty box.
`intersects(b)` is `min < b.max && b.min < max` on all axes. `intersect(b)` is `null` when the
intersection is empty. `translate` is `null` if either corner leaves the lattice. `volume` is the
product of extents, saturating at `Long.MAX_VALUE`. `Cuboid` has no union and no subtract: a union is
not a cuboid, and a difference is up to six.

`CuboidSet.containsAll(other)` means every cell of `other` is in `this` (`other ⊆ this`), not the
reverse; `containsAll` of an empty set is true.

`Cuboid.ofSize(x,y,z,sx,sy,sz)` is the authoring form (origin + size); `Cuboid.of(minX,...,maxZ)` is
the kernel form (min/max). The document compiler converts; a user who writes `of` with a size is
wrong and the javadoc says so.

### 4.3 CuboidSet

`of` canonicalises:

1. drop empty boxes;
2. cut at every distinct `x` and `z` boundary, producing slab-columns;
3. merge overlapping or touching `y` intervals inside a column;
4. sort by `(min.y, min.z, min.x, max.y, max.z, max.x)`.

The result has no empty box, no overlap, no mergeable pair **inside one slab-column** (touching
`y` intervals are merged), and is idempotent. Boxes in neighbouring columns may be adjacent (a
union that happens to be a box is not re-cut into one); irredundance is not minimality. `equals`
and `contentEquals` are both semantic (`containsAll` both ways); the box decomposition is not part
of value equality. Every algebra member returns `of(...)`, so the box list is normalised again.
`volume` saturates. No member enumerates cells; `ClaimPlanner` may
iterate `boxes()` and nested coordinates to emit rows, which is the one bounded enumeration in the
kernel.

### 4.4 Orientations

An orientation is a signed permutation: `world(a) = s_a · local(π(a))`, with `π` a permutation of
`{x,y,z}` and one sign per world axis. There are `6 × 8 = 48`. Storage is the ordinal
`(turn × 6 + tilt) × 2 + parity`; `of(turn,tilt,flip)` normalises the triple through the 48-element
group, so `of(half, level, leftRight)` equals `of(none, level, frontBack)`. `all()` is the 48 in
ordinal order.

`Turn` is a generator about the `y` axis; `Tilt` is a transversal (six proper rotations with distinct
`up()`); `Flip` is a reflection. They are not a unique name for 48 elements: 72 triples map onto 48.
`isMirror` is the determinant sign; `inverted` is proper (a half turn about the `x` axis).

`Heading` constants are lowerCamel and have no wire tag: the anchor read in `structure` lowercases
the block-state property text before `Heading.valueOf`, so the accepted strings stay `north`, `south`,
`west`, `east`, `up`, `down` (case-insensitive), and `ofStep` remains the only numeric construction.

Point image: `world(a) = s_a · local(π(a))`. Cuboid image per world axis with source axis `c`:
`+1: [min[c], max[c])`, `-1: [1 - max[c], 1 - min[c])`. The `1 -` is required; using the stored
corners directly loses the image of `min` and adds a cell nobody's image is. Volume is invariant.

### 4.5 Placement and siting

`Placement.apply(p) = o(p) + t`. `compose` is `(o_P ∘ o_Q, o_P(t_Q) + t_P)` (Q first) and returns
`null` when the offset sum leaves the lattice. `inverse` is `(o⁻¹, -o⁻¹(t))` and is `null` when the
negated offset leaves the lattice. The inverse law holds when both are non-null:
`P.inverse().compose(P) == identity`. `Siting.toWorld(p) = origin + o(p) + t`, checked; `toLocal` is
the inverse. A local delta must be oriented before it is added to a world point: `toWorld` is affine,
not linear.

`Siting.compose(relative)` places a relative siting in this siting's frame:
`origin = toWorld(relative.origin())` and `placement = placement().compose(relative.placement())`, so
the relative origin is a cell in this siting's local frame. It returns `null` when the origin image or
the placement composition leaves the lattice.

### 4.6 SectionKey

`SectionKey` is the view's section index: `(world, frame, x, y, z)`, ordered by world, frame, `x`,
`z`, `y`. `of` uses `Math.floorDiv(cell, cellSize)`. The `frame` distinguishes the root view (`0`)
from nested views of the same snapshot; it is assigned by the capture and is unique within a
snapshot. A `SectionKey` is not a cache key and carries no content.

### 4.7 Locus

| Frame | Coordinates | Cells | Key | Source |
| --- | --- | --- | --- | --- |
| `definition` | authoring document | optional | absent | required |
| `local` | blueprint grid | non-empty | absent | absent |
| `world` | absolute lattice | non-empty | absent | absent |
| `key` | one typed key of one instance | empty | required | absent |

`Locus`'s constructor enforces the table. A label's locus is one frame; a producer that has both a
cell and a definition writes two labels, never one locus with two sources. `LocusMap.toWorld` maps
`local → world` and fixes `world`; `toLocal` maps `world → local` and fixes `local`; both return
`null` for `definition`/`key` and when the image leaves the lattice. There is no
`toDefinition`: a definition label is created where the source is in hand.

`LocusFrame.wireName()` returns `DEFINITION`/`LOCAL`/`WORLD`/`KEY`; the finding JSON renderer writes
that tag for a label's `frame`, so the wire text does not follow the lowerCamel constants.

## 5. Pipeline role

- `compile`: validates cuboids, orientation policy inputs, seam cells, and document placements.
- `check`: converts a siting to world cells, builds loci for findings, and maps a bay's cells.
- `settle`: `ClaimPlanner` translates `Box`/`Declared` footprints to world cells.
- No phase reads a world; `Siting.toWorld` is arithmetic.

## 6. Codes

`SC-SPC-0001` (ERROR): a local cell or box cannot be mapped into the requested frame for this siting.
Producer: `LocusMap.toWorld/toLocal`, `Siting.toWorld`, `ClaimPlanner` when a footprint leaves the
lattice. `SC-SPC-0002` (ERROR): a `CuboidSet` operation would leave the lattice. Producer:
`CuboidSet.translate` callers that need a finding (the method itself returns `null`).

## 7. Tests

- Pack/unpack is a bijection over the in-range set; out-of-range is rejected; `compareTo` is point
  order, not packed order.
- Cuboid edge cases: empty, across the origin, mirrored image, `ofSize` vs `of`.
- `CuboidSet`: idempotence; disjointness; subtraction; union; semantic `equals` and content hash;
  no merge of touching boxes; box order total.
- Orientation: `all().size() == 48`; distinct; inverse; composition; volume invariant; the
  `1 -` mirror case; `of` normalises the 72 triples.
- Placement: compose/inverse when non-null; off-lattice returns null; affine equivariance.
- `Siting.compose`: identity, half-turn and clockwise roots, negative and positive relative origins,
  a relative orientation, a nonzero root origin, and null on lattice overflow.
- `SectionKey`: floorDiv for negative coordinates; frame separates nested views; order.
- Locus: frame/field table enforced; `LocusMap` round-trip and null cases.
- Wire tags: `LocusFrame.wireName()` is the uppercase tag; a heading property parses from `north`
  and `NORTH` alike.

## 8. Dependencies

`core` only: `NamespacedId`, `World`, `InstanceId`, `SourceSpan`, `@Nullable`. It never imports
`finding`, `world`, `structure` or `claim`.
