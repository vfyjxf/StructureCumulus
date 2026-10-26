# P1 round 2 — regenerated skeleton (docs only)

Written from `01-glossary.md`, `05-space.md`, `06-occupants.md` only, before any look at
`src/`. One fenced block per public type, plus the three stubs the documents forced.
`// INVENTED:` marks a member or a shape the documents never name.
Packages: `kernel.base`, `kernel.space`, `kernel.occupant` (+ `kernel.finding` and
`org.jspecify` stubs).

## `dev/vfyjxf/cumulus/kernel/base/NamespacedId.java`

```java
package dev.vfyjxf.cumulus.kernel.base;

/** 01-glossary §B. Immutable (namespace, path); ordered by namespace then path. */
public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {

    public static NamespacedId of(String namespace, String path) {
        throw new UnsupportedOperationException();
    }

    // INVENTED: member name for the "parsed from ns:path" step; 01 §B names no factory.
    public static NamespacedId parse(String text) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(NamespacedId other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/base/SourceSpan.java`

```java
package dev.vfyjxf.cumulus.kernel.base;

/** 01-glossary §B. Half-open character range in one document. */
public record SourceSpan(NamespacedId document, int start, int end) {}
```

## `dev/vfyjxf/cumulus/kernel/base/InstanceId.java`

```java
package dev.vfyjxf.cumulus.kernel.base;

/**
 * STUB FORCED BY A GAP: 05-space needs InstanceId for KeyLocus / Locus.key, but 01-glossary §I
 * assigns it to U7 (engine) and 05 §8 says space depends on base only.
 * Shape is 01-glossary §I's: (World world, long serial).
 */
public record InstanceId(dev.vfyjxf.cumulus.kernel.occupant.World world, long serial) {}
```

## `dev/vfyjxf/cumulus/kernel/space/LatticePoint.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

/** 05 §3. Integer cell in the blueprint's local grid. */
public record LatticePoint(int x, int y, int z) implements Comparable<LatticePoint> {

    public static final LatticePoint ORIGIN = new LatticePoint(0, 0, 0);

    public static final int MIN_XZ = -33_554_432;
    public static final int MAX_XZ = 33_554_431;
    public static final int MIN_Y = -2_048;
    public static final int MAX_Y = 2_047;

    public static LatticePoint of(int x, int y, int z) {
        throw new UnsupportedOperationException();
    }

    public static boolean inLattice(int x, int y, int z) {
        throw new UnsupportedOperationException();
    }

    public @Nullable LatticePoint offset(int dx, int dy, int dz) {
        throw new UnsupportedOperationException();
    }

    public LatticeKey key() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(LatticePoint other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/LatticeKey.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/** 05 §3, §4.1. Packed long in Minecraft's layout: x:26 / z:26 / y:12. */
public record LatticeKey(long packed) implements Comparable<LatticeKey> {

    public static LatticeKey of(LatticePoint point) {
        throw new UnsupportedOperationException();
    }

    public int x() {
        throw new UnsupportedOperationException();
    }

    public int z() {
        throw new UnsupportedOperationException();
    }

    public int y() {
        throw new UnsupportedOperationException();
    }

    public LatticePoint toPoint() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(LatticeKey other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Axis.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/**
 * 01-glossary §C / 05 §2. X, Y, Z.
 * INVENTED: neither chapter names a single member of this enum (no ordinal lookup, no step).
 */
public enum Axis {
    X,
    Y,
    Z
}
```

## `dev/vfyjxf/cumulus/kernel/space/Cuboid.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

/** 05 §3, §4.2. Half-open [min, max); min == max is empty but legal. */
public record Cuboid(LatticePoint min, LatticePoint max) implements Comparable<Cuboid> {

    public static Cuboid of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        throw new UnsupportedOperationException();
    }

    public static Cuboid of(LatticePoint min, LatticePoint max) {
        throw new UnsupportedOperationException();
    }

    public static Cuboid of(LatticePoint cell) {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    public boolean contains(LatticePoint point) {
        throw new UnsupportedOperationException();
    }

    public boolean contains(Cuboid other) {
        throw new UnsupportedOperationException();
    }

    public boolean intersects(Cuboid other) {
        throw new UnsupportedOperationException();
    }

    public @Nullable Cuboid intersect(Cuboid other) {
        throw new UnsupportedOperationException();
    }

    public Cuboid bounding(Cuboid other) {
        throw new UnsupportedOperationException();
    }

    public @Nullable Cuboid translate(int dx, int dy, int dz) {
        throw new UnsupportedOperationException();
    }

    public long volume() {
        throw new UnsupportedOperationException();
    }

    public CuboidSet asSet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Cuboid other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/CuboidSet.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 05 §3, §4.3. Canonical, disjoint, sorted, empty-free box list. */
public final class CuboidSet {

    private CuboidSet() {
        throw new UnsupportedOperationException();
    }

    public static CuboidSet empty() {
        throw new UnsupportedOperationException();
    }

    public static CuboidSet of(Cuboid... boxes) {
        throw new UnsupportedOperationException();
    }

    public static CuboidSet of(Collection<Cuboid> boxes) {
        throw new UnsupportedOperationException();
    }

    public List<Cuboid> boxes() {
        throw new UnsupportedOperationException();
    }

    public int boxCount() {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    public long volume() {
        throw new UnsupportedOperationException();
    }

    public @Nullable Cuboid bounds() {
        throw new UnsupportedOperationException();
    }

    public boolean contains(LatticePoint point) {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(CuboidSet other) {
        throw new UnsupportedOperationException();
    }

    public boolean intersects(CuboidSet other) {
        throw new UnsupportedOperationException();
    }

    public CuboidSet union(CuboidSet other) {
        throw new UnsupportedOperationException();
    }

    // INVENTED: 05 §3 says "set and cuboid forms" without naming the cuboid overloads.
    public CuboidSet union(Cuboid box) {
        throw new UnsupportedOperationException();
    }

    public CuboidSet intersect(CuboidSet other) {
        throw new UnsupportedOperationException();
    }

    public CuboidSet intersect(Cuboid box) {
        throw new UnsupportedOperationException();
    }

    public CuboidSet subtract(CuboidSet other) {
        throw new UnsupportedOperationException();
    }

    public CuboidSet subtract(Cuboid box) {
        throw new UnsupportedOperationException();
    }

    public @Nullable CuboidSet translate(int dx, int dy, int dz) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Heading.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/** 05 §3, §4.4. */
public enum Heading {
    NORTH,
    SOUTH,
    WEST,
    EAST,
    UP,
    DOWN;

    public Heading opposite() {
        throw new UnsupportedOperationException();
    }

    public Axis axis() {
        throw new UnsupportedOperationException();
    }

    // INVENTED return type: 05 §3 lists "step()" with no signature; §4.4 gives it a vector.
    public LatticePoint step() {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Turn.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/** 05 §3, §4.4. Yaw quarter turn, cyclic of order 4. */
public enum Turn {
    NONE,
    CLOCKWISE,
    HALF,
    COUNTERCLOCKWISE;

    public Turn compose(Turn other) {
        throw new UnsupportedOperationException();
    }

    public Turn inverse() {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Tilt.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/** 05 §3, §4.4. Six proper rotations, one per coset of the yaw subgroup. */
public enum Tilt {
    LEVEL,
    PITCH_UP,
    PITCH_DOWN,
    ROLL_LEFT,
    ROLL_RIGHT,
    INVERTED;

    public Heading up() {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Flip.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/** 05 §3, §4.4. */
public enum Flip {
    NONE,
    LEFT_RIGHT,
    FRONT_BACK;

    public boolean isMirror() {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Orientation.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** 05 §3, §4.5. One element of the 48-element group, stored as a signed axis permutation. */
public record Orientation(int ordinal) implements Comparable<Orientation> {

    public static final Orientation IDENTITY = new Orientation(0);

    public static Orientation of(Turn turn, Tilt tilt, Flip flip) {
        throw new UnsupportedOperationException();
    }

    public Orientation compose(Orientation other) {
        throw new UnsupportedOperationException();
    }

    public Orientation then(Orientation other) {
        throw new UnsupportedOperationException();
    }

    public Orientation inverse() {
        throw new UnsupportedOperationException();
    }

    public Heading apply(Heading heading) {
        throw new UnsupportedOperationException();
    }

    public @Nullable LatticePoint apply(LatticePoint point) {
        throw new UnsupportedOperationException();
    }

    public @Nullable Cuboid apply(Cuboid box) {
        throw new UnsupportedOperationException();
    }

    public @Nullable CuboidSet apply(CuboidSet set) {
        throw new UnsupportedOperationException();
    }

    public Turn turn() {
        throw new UnsupportedOperationException();
    }

    public Tilt tilt() {
        throw new UnsupportedOperationException();
    }

    public boolean isMirror() {
        throw new UnsupportedOperationException();
    }

    // INVENTED return type: 05 §3 lists "id()" with no type.
    public int id() {
        throw new UnsupportedOperationException();
    }

    public static List<Orientation> all() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Orientation other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Placement.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

/** 05 §3, §4.6. apply(q) = o(q) + t. */
public record Placement(Orientation orientation, LatticePoint offset) {

    public static final Placement IDENTITY = new Placement(Orientation.IDENTITY, LatticePoint.ORIGIN);

    public Placement compose(Placement other) {
        throw new UnsupportedOperationException();
    }

    public Placement inverse() {
        throw new UnsupportedOperationException();
    }

    public @Nullable LatticePoint apply(LatticePoint point) {
        throw new UnsupportedOperationException();
    }

    // INVENTED: 05 §3 writes only "@Nullable apply(...)".
    public @Nullable Cuboid apply(Cuboid box) {
        throw new UnsupportedOperationException();
    }

    public @Nullable CuboidSet apply(CuboidSet set) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/Siting.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

/** 05 §3, §4.6. toWorld(p) = origin + o(p) + t. */
public record Siting(LatticePoint origin, Placement placement) {

    public static Siting of(LatticePoint origin, Orientation orientation) {
        throw new UnsupportedOperationException();
    }

    public static Siting of(LatticePoint origin, Placement placement) {
        throw new UnsupportedOperationException();
    }

    public Siting withOrigin(LatticePoint origin) {
        throw new UnsupportedOperationException();
    }

    public Siting withPlacement(Placement placement) {
        throw new UnsupportedOperationException();
    }

    // INVENTED parameter types: 05 §3 gives only "anchoring(anchorTarget, anchorLocal, Placement)".
    public static @Nullable Siting anchoring(LatticePoint anchorTarget, LatticePoint anchorLocal, Placement placement) {
        throw new UnsupportedOperationException();
    }

    public @Nullable LatticePoint toWorld(LatticePoint local) {
        throw new UnsupportedOperationException();
    }

    public @Nullable LatticePoint toLocal(LatticePoint world) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/SectionKey.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.occupant.World;

/** 05 §3. The 16^3 section index of one world. */
public record SectionKey(World world, int x, int y, int z) implements Comparable<SectionKey> {

    public static SectionKey of(World world, LatticePoint cell, int cellSize) {
        throw new UnsupportedOperationException();
    }

    // AS WRITTEN THIS CANNOT EXIST: no World parameter, yet the record needs one.
    public static SectionKey sectionOf(LatticePoint cell, int cellSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(SectionKey other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/LocusFrame.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/** 05 §3, §4.7. */
public enum LocusFrame {
    DEFINITION,
    LOCAL,
    WORLD,
    KEY
}
```

## `dev/vfyjxf/cumulus/kernel/space/KeyLocus.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

/** 01-glossary §C. The one instance key a KEY locus names. */
public record KeyLocus(InstanceId instance, NamespacedId key) {}
```

## `dev/vfyjxf/cumulus/kernel/space/Locus.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.SourceSpan;
import org.jspecify.annotations.Nullable;

/** 05 §3, §4.7. */
public record Locus(LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan source) {

    public static Locus definition(SourceSpan source) {
        throw new UnsupportedOperationException();
    }

    public static Locus local(CuboidSet cells) {
        throw new UnsupportedOperationException();
    }

    public static Locus local(CuboidSet cells, @Nullable SourceSpan source) {
        throw new UnsupportedOperationException();
    }

    public static Locus world(CuboidSet cells) {
        throw new UnsupportedOperationException();
    }

    public static Locus key(InstanceId instance, NamespacedId id) {
        throw new UnsupportedOperationException();
    }

    public Locus withSource(SourceSpan source) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/LocusMap.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

/** 05 §3, §4.7. */
public record LocusMap(Siting siting) {

    public static LocusMap of(Siting siting) {
        throw new UnsupportedOperationException();
    }

    public @Nullable Locus toWorld(Locus locus) {
        throw new UnsupportedOperationException();
    }

    public @Nullable Locus toLocal(Locus locus) {
        throw new UnsupportedOperationException();
    }

    public @Nullable Locus toDefinition(Locus locus) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/ChunkKey.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

/** 01-glossary §C. (cx, cy, cz) chunk index, ordered (cy, cz, cx). */
public record ChunkKey(int cx, int cy, int cz) implements Comparable<ChunkKey> {

    @Override
    public int compareTo(ChunkKey other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/space/ChunkGrid.java`

```java
package dev.vfyjxf.cumulus.kernel.space;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 01-glossary §C only; 05 §3 never lists this type.
 * INVENTED: every return type below ("chunkOf", "boundsOf", "chunkCount", "chunksOf").
 */
public record ChunkGrid(int cellSize, LatticePoint origin) {

    public ChunkKey chunkOf(LatticePoint cell) {
        throw new UnsupportedOperationException();
    }

    public Cuboid boundsOf(ChunkKey chunk) {
        throw new UnsupportedOperationException();
    }

    public int chunkCount() {
        throw new UnsupportedOperationException();
    }

    public List<ChunkKey> chunksOf(@Nullable Cuboid box) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/World.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

/** 01-glossary §D. One Minecraft level as the kernel sees it. */
public record World(NamespacedId id) {}
```

## `dev/vfyjxf/cumulus/kernel/occupant/OwnerId.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

/** 01-glossary §D. Who holds a claim — a player or a team. */
public record OwnerId(NamespacedId owner) {}
```

## `dev/vfyjxf/cumulus/kernel/occupant/OccupantKind.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

/** 06 §3.1. The family a resolver serves. */
public record OccupantKind(NamespacedId id) implements Comparable<OccupantKind> {

    // INVENTED path strings: 06 says "structurecumulus ids" but never spells them.
    public static final OccupantKind BLOCK = new OccupantKind(NamespacedId.of("structurecumulus", "block"));
    public static final OccupantKind BLOCK_ENTITY = new OccupantKind(NamespacedId.of("structurecumulus", "block_entity"));
    public static final OccupantKind ENTITY = new OccupantKind(NamespacedId.of("structurecumulus", "entity"));
    public static final OccupantKind NESTED = new OccupantKind(NamespacedId.of("structurecumulus", "nested"));
    public static final OccupantKind OTHER = new OccupantKind(NamespacedId.of("structurecumulus", "other"));

    /** "of(id) = host family". */
    public static OccupantKind of(NamespacedId id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(OccupantKind other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/OccupantRef.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

/** 06 §3.1. Snapshot-scoped handle; order: snapshot, y, z, x, kind.id, type. */
public record OccupantRef(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)
        implements Comparable<OccupantRef> {

    public boolean from(SnapshotId id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(OccupantRef other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/OccupantDescriptor.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.List;
import java.util.Map;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import org.jspecify.annotations.Nullable;

/** 06 §3.1. Version-independent description. */
public record OccupantDescriptor(NamespacedId type, OccupantKind kind,
                                 Map<String, String> properties, List<NamespacedId> tags) {

    /** null = the host published nothing. */
    public @Nullable String property(String key) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/TraitKey.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

/** 06 §3.1. Typed identity of a queryable trait. */
public record TraitKey<T>(NamespacedId id, Class<T> type) {}
```

## `dev/vfyjxf/cumulus/kernel/occupant/Trait.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import org.jspecify.annotations.Nullable;

/** 06 §3.1. A (TraitKey, @Nullable value) pair. */
public record Trait<T>(TraitKey<T> key, @Nullable T value) {

    // INVENTED: used by the 06 §5 sketch, absent from 06 §3.1's surface.
    public static <T> Trait<T> of(TraitKey<T> key, @Nullable T value) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/ReadStatus.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

/** 06 §3.2. */
public enum ReadStatus {
    READABLE,
    OUT_OF_BOUNDS,
    NOT_CAPTURED,
    DENIED,
    STALE;

    public boolean readable() {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/OccupantSnapshot.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.List;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

/** 06 §3.2. Immutable read model. */
public record OccupantSnapshot(OccupantRef ref, OccupantDescriptor descriptor, Cuboid footprint,
                               List<Trait<?>> traits, @Nullable NestedView nested) {

    public static final NamespacedId AIR = NamespacedId.of("structurecumulus", "air");

    public static OccupantSnapshot air(SnapshotId snapshot, LatticePoint cell) {
        throw new UnsupportedOperationException();
    }

    public boolean isAir() {
        throw new UnsupportedOperationException();
    }

    public <T> @Nullable Trait<T> find(TraitKey<T> key) {
        throw new UnsupportedOperationException();
    }

    public <T> @Nullable T trait(TraitKey<T> key) {
        throw new UnsupportedOperationException();
    }

    public boolean has(TraitKey<?> key) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/SectionSlot.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.finding.FindingCode;

/** 06 §3.3. A section's answer. */
public sealed interface SectionSlot
        permits SectionSlot.Air, SectionSlot.Present, SectionSlot.Refused {

    default boolean readable() {
        throw new UnsupportedOperationException();
    }

    record Air(long revision) implements SectionSlot {}

    record Present(SectionSnapshot section) implements SectionSlot {}

    record Refused(ReadStatus status, FindingCode code) implements SectionSlot {}
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/SectionSnapshot.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.List;
import java.util.Map;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

/** 06 §3.3. One frozen section; every enumerated cell answers. */
public final class SectionSnapshot {

    private SectionSnapshot() {
        throw new UnsupportedOperationException();
    }

    public SectionKey key() {
        throw new UnsupportedOperationException();
    }

    public long contentDigest() {
        throw new UnsupportedOperationException();
    }

    /** null only outside the enumerated cells. */
    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        throw new UnsupportedOperationException();
    }

    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        throw new UnsupportedOperationException();
    }

    public static SectionSnapshot of(SectionKey key, Map<LatticePoint, OccupantSnapshot> occupants) {
        throw new UnsupportedOperationException();
    }

    public static Builder builder(SectionKey key) {
        throw new UnsupportedOperationException();
    }

    /** INVENTED shapes: 06 §3.3 names put/putAll/build but no return types. */
    public static final class Builder {

        private Builder() {
            throw new UnsupportedOperationException();
        }

        public Builder put(LatticePoint cell, OccupantSnapshot occupant) {
            throw new UnsupportedOperationException();
        }

        public Builder putAll(Map<LatticePoint, OccupantSnapshot> occupants) {
            throw new UnsupportedOperationException();
        }

        public SectionSnapshot build() {
            throw new UnsupportedOperationException();
        }
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/OccupantFilter.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.function.Predicate;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import org.jspecify.annotations.Nullable;

/** 06 §3.3. A null component does not constrain. */
public record OccupantFilter(@Nullable Predicate<NamespacedId> type, @Nullable Predicate<OccupantKind> kind,
                             @Nullable Predicate<NamespacedId> trait) {

    public static OccupantFilter any() {
        throw new UnsupportedOperationException();
    }

    public static OccupantFilter ofType(NamespacedId type) {
        throw new UnsupportedOperationException();
    }

    public static OccupantFilter ofKind(OccupantKind kind) {
        throw new UnsupportedOperationException();
    }

    public static OccupantFilter ofTrait(NamespacedId trait) {
        throw new UnsupportedOperationException();
    }

    public boolean matches(OccupantSnapshot occupant) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/WorldView.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.List;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

/** 06 §3.3. The kernel's only world window. */
public interface WorldView {

    SnapshotId snapshotId();

    SectionSlot slot(SectionKey key);

    @Nullable OccupantSnapshot occupant(LatticePoint cell);

    @Nullable OccupantSnapshot occupant(OccupantRef ref);

    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);

    @Nullable NestedView nested(LatticePoint cell);

    long contentDigest(SectionKey key);
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/NestedView.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.List;

/** 06 §3.3. A substructure's window: a full WorldView in its own frame. */
public interface NestedView extends WorldView {

    OccupantSnapshot parent();

    int depth();

    List<OccupantRef> ancestry();
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/NestedLevelSnapshot.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.List;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

/**
 * 06 §1 lists this in U2; 01-glossary §N assigns it to the adapter (A1).
 * INVENTED: whether it is a class or an interface, and the unchecked no-arg contentDigest().
 */
public interface NestedLevelSnapshot extends NestedView {

    OccupantSnapshot parent();

    int depth();

    long contentDigest();

    @Nullable OccupantSnapshot occupantAt(LatticePoint cell);

    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/SnapshotId.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

/** 06 §3.3. Revision is monotonic per world. */
public record SnapshotId(World world, long revision) implements Comparable<SnapshotId> {

    @Override
    public int compareTo(SnapshotId other) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/SnapshotPolicy.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.Set;

/** 06 §3.3. Capture mode, required kinds and traits, caps, staleness tolerance. */
public record SnapshotPolicy(CaptureMode capture, Set<OccupantKind> requiredKinds,
                             Set<TraitKey<?>> requiredTraits, int maxElements,
                             int maxNestedDepth, long maxStalenessNanos) {

    public enum CaptureMode {
        EXACT,
        REQUIRED
    }

    /** REQUIRED, depth 0, staleness 0. */
    public static SnapshotPolicy minimal(int maxElements) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/WorldWindow.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

/** 06 §3.3. What to capture. */
public record WorldWindow(Cuboid bounds, int maxElements) {}
```

## `dev/vfyjxf/cumulus/kernel/occupant/EntityWindow.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

/** 06 §1, §4.8. The box one entity type may move in. */
public record EntityWindow(Cuboid cells, NamespacedId type) {

    public static final int MAX_ENTITY_SPAN = 3;
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/EntityKey.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.UUID;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

/** 06 §1, §4.8. One entity's identity in a window diff. */
public record EntityKey(UUID id, NamespacedId type, long dataDigest) {}
```

## `dev/vfyjxf/cumulus/kernel/occupant/ChangeSet.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.Set;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;

/** 06 §4.8. What changed between two revisions. */
public record ChangeSet(Set<LatticeKey> cells, Set<SectionKey> sections, Set<LatticeKey> payloads,
                        Set<EntityKey> entities, Set<NamespacedId> keys, long revision) {

    public static ChangeSet empty() {
        throw new UnsupportedOperationException();
    }

    /** Unions the five sets and keeps the later revision. */
    public ChangeSet plus(ChangeSet other) {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/CaptureLimits.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

/** 06 §3.4. */
public record CaptureLimits(int maxCells, int maxSections, long maxBytes, int maxEntities, int maxPayloads) {}
```

## `dev/vfyjxf/cumulus/kernel/occupant/CaptureRequest.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.Set;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.ChunkGrid;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;

/** 06 §3.4. */
public record CaptureRequest(Cuboid bounds, ChunkGrid grid, SnapshotPolicy policy,
                             Set<NamespacedId> trackedTypes, Set<LatticeKey> trackedCells,
                             CaptureLimits limits) {}
```

## `dev/vfyjxf/cumulus/kernel/occupant/CaptureResult.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.finding.FindingCode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

/** 06 §3.4. Never a partial snapshot treated as complete. */
public sealed interface CaptureResult
        permits CaptureResult.Captured, CaptureResult.TooLarge, CaptureResult.Unavailable {

    record Captured(WorldView view) implements CaptureResult {}

    record TooLarge(Cuboid bounds, int elements) implements CaptureResult {}

    record Unavailable(ReadStatus status, FindingCode code) implements CaptureResult {}
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/WorldCapture.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

/** 06 §3.4. One WorldCapture serves exactly one world. */
public interface WorldCapture {

    CaptureResult capture(CaptureRequest request);

    long latestRevision();
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/OccupantResolver.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/** 06 §3.4. Host SPI; kinds() dispatches, resolve may decline. */
public interface OccupantResolver<R> {

    Set<OccupantKind> kinds();

    @Nullable
    OccupantSnapshot resolve(R raw, ResolveContext context);
}
```

## `dev/vfyjxf/cumulus/kernel/occupant/ResolveContext.java`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

/** 06 §3.4. What a resolver may see. */
public interface ResolveContext {

    SnapshotId snapshotId();

    LatticePoint anchor();

    int remainingDepth();

    SnapshotPolicy policy();
}
```

## `dev/vfyjxf/cumulus/kernel/finding/FindingCode.java`

```java
package dev.vfyjxf.cumulus.kernel.finding;

/**
 * STUB FORCED BY A CROSS-PACKAGE REFERENCE: 06 §3.3 gives SectionSlot.Refused and
 * CaptureResult.Unavailable a FindingCode, so kernel.occupant must see kernel.finding.
 * Only the shape 06 §5 uses is present; `Family` has no definition anywhere in the three chapters.
 */
public record FindingCode(String text) {
    public static FindingCode of(Family family, int number) {
        throw new UnsupportedOperationException();
    }

    /** INVENTED: 06 §5 writes Family.OCC; no chapter defines this type. */
    public enum Family { OCC, SPC, CTX, CLM, BAY, ENG, MC }
}
```

## `org/jspecify/annotations/Nullable.java`

```java
package org.jspecify.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** STUB: the three chapters say "@Nullable" but never name the annotation package. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface Nullable {}
```

