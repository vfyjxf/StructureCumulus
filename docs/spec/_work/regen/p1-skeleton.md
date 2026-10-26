# P1 blind skeleton

Written from `01-glossary.md`, `05-space.md`, `06-occupants.md` only. No `src/` was read.
Bodies are `UnsupportedOperationException` stubs; empty `{}` record/interface bodies are empty
on purpose. Types referenced from other units (`FindingCode`, `InstanceId`) are stubbed at the
end so the block compiles in isolation.

## 1. `dev.vfyjxf.cumulus.kernel.base`

```java
package dev.vfyjxf.cumulus.kernel.base;

import java.util.List;
import java.util.SequencedSet;
import java.util.Set;

public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {
    public static NamespacedId of(String namespace, String path) { throw new UnsupportedOperationException(); }
    public static NamespacedId parse(String text) { throw new UnsupportedOperationException(); }
    @Override public int compareTo(NamespacedId other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public record Budget(int cells, int sections) {
    public static Budget of(int cells, int sections) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public record Spend(int cells, int sections) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public interface CancelToken {
    boolean isCancelled();
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public enum Stability {
    STABLE, EXPERIMENTAL, DEPRECATED
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public enum Stage {
    DESIGN, PROBE, FORM, SETTLE
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public record ParameterKey<T>(NamespacedId id, Class<T> type) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

import java.util.Set;

public final class Arguments {
    public static final Arguments EMPTY = null;

    private Arguments() {
        throw new UnsupportedOperationException();
    }

    public static Arguments of() { throw new UnsupportedOperationException(); }

    public <T> T get(ParameterKey<T> key) { throw new UnsupportedOperationException(); }

    public <T> Arguments with(ParameterKey<T> key, T value) { throw new UnsupportedOperationException(); }

    public Set<ParameterKey<?>> keys() { throw new UnsupportedOperationException(); }

    public int size() { throw new UnsupportedOperationException(); }

    public boolean isEmpty() { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public record SourceSpan(NamespacedId document, int start, int end) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

import java.util.List;
import java.util.SequencedSet;

public interface DataView {
    DataKind kind();

    DataView get(String key);

    List<DataView> list();

    SequencedSet<String> keys();

    String asString();

    int asInt(int fallback);

    boolean asBoolean(boolean fallback);

    SourceSpan span();
}
```

```java
package dev.vfyjxf.cumulus.kernel.base;

public enum DataKind {
    OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
}
```

## 2. `dev.vfyjxf.cumulus.kernel.space`

```java
package dev.vfyjxf.cumulus.kernel.space;

public enum Axis {
    X, Y, Z
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public record LatticePoint(int x, int y, int z) implements Comparable<LatticePoint> {
    public static final LatticePoint ORIGIN = null;

    public static LatticePoint of(int x, int y, int z) { throw new UnsupportedOperationException(); }

    public static boolean inLattice(int x, int y, int z) { throw new UnsupportedOperationException(); }

    public LatticePoint offset(int dx, int dy, int dz) { throw new UnsupportedOperationException(); }

    public LatticeKey key() { throw new UnsupportedOperationException(); }

    @Override public int compareTo(LatticePoint other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public record LatticeKey(long packed) implements Comparable<LatticeKey> {
    public static LatticeKey of(LatticePoint point) { throw new UnsupportedOperationException(); }

    public LatticePoint toPoint() { throw new UnsupportedOperationException(); }

    @Override public int compareTo(LatticeKey other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public record Cuboid(LatticePoint min, LatticePoint max) implements Comparable<Cuboid> {
    public static Cuboid of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) { throw new UnsupportedOperationException(); }

    public static Cuboid of(LatticePoint min, LatticePoint max) { throw new UnsupportedOperationException(); }

    public static Cuboid of(LatticePoint cell) { throw new UnsupportedOperationException(); }

    public boolean isEmpty() { throw new UnsupportedOperationException(); }

    public boolean contains(LatticePoint point) { throw new UnsupportedOperationException(); }

    public boolean contains(Cuboid other) { throw new UnsupportedOperationException(); }

    public boolean intersects(Cuboid other) { throw new UnsupportedOperationException(); }

    public Cuboid intersect(Cuboid other) { throw new UnsupportedOperationException(); }

    public Cuboid bounding(Cuboid other) { throw new UnsupportedOperationException(); }

    public Cuboid translate(int dx, int dy, int dz) { throw new UnsupportedOperationException(); }

    public long volume() { throw new UnsupportedOperationException(); }

    public CuboidSet asSet() { throw new UnsupportedOperationException(); }

    @Override public int compareTo(Cuboid other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

import java.util.Collection;
import java.util.List;

public final class CuboidSet {
    public static CuboidSet empty() { throw new UnsupportedOperationException(); }

    public static CuboidSet of(Cuboid... boxes) { throw new UnsupportedOperationException(); }

    public static CuboidSet of(Collection<Cuboid> boxes) { throw new UnsupportedOperationException(); }

    public List<Cuboid> boxes() { throw new UnsupportedOperationException(); }

    public int boxCount() { throw new UnsupportedOperationException(); }

    public long volume() { throw new UnsupportedOperationException(); }

    public Cuboid bounds() { throw new UnsupportedOperationException(); }

    public boolean isEmpty() { throw new UnsupportedOperationException(); }

    public boolean contains(LatticePoint point) { throw new UnsupportedOperationException(); }

    public boolean containsAll(CuboidSet other) { throw new UnsupportedOperationException(); }

    public boolean intersects(CuboidSet other) { throw new UnsupportedOperationException(); }

    public CuboidSet union(CuboidSet other) { throw new UnsupportedOperationException(); }

    public CuboidSet union(Cuboid box) { throw new UnsupportedOperationException(); }

    public CuboidSet intersect(CuboidSet other) { throw new UnsupportedOperationException(); }

    public CuboidSet subtract(CuboidSet other) { throw new UnsupportedOperationException(); }

    public CuboidSet subtract(Cuboid box) { throw new UnsupportedOperationException(); }

    public CuboidSet translate(int dx, int dy, int dz) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public enum Heading {
    NORTH, SOUTH, WEST, EAST, UP, DOWN;

    public Heading opposite() { throw new UnsupportedOperationException(); }

    public Axis axis() { throw new UnsupportedOperationException(); }

    public LatticePoint step() { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public enum Turn {
    NONE, CLOCKWISE, HALF, COUNTERCLOCKWISE;

    public Turn compose(Turn other) { throw new UnsupportedOperationException(); }

    public Turn inverse() { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public enum Tilt {
    LEVEL, PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, INVERTED;

    public Heading up() { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public enum Flip {
    NONE, LEFT_RIGHT, FRONT_BACK;

    public boolean isMirror() { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

import java.util.List;

public record Orientation(int ordinal) implements Comparable<Orientation> {
    public static final Orientation IDENTITY = null;

    public static Orientation of(Turn turn, Tilt tilt, Flip flip) { throw new UnsupportedOperationException(); }

    public Orientation compose(Orientation other) { throw new UnsupportedOperationException(); }

    public Orientation inverse() { throw new UnsupportedOperationException(); }

    public Heading apply(Heading heading) { throw new UnsupportedOperationException(); }

    public LatticePoint apply(LatticePoint point) { throw new UnsupportedOperationException(); }

    public Cuboid apply(Cuboid box) { throw new UnsupportedOperationException(); }

    public CuboidSet apply(CuboidSet cells) { throw new UnsupportedOperationException(); }

    public Turn turn() { throw new UnsupportedOperationException(); }

    public Tilt tilt() { throw new UnsupportedOperationException(); }

    public boolean isMirror() { throw new UnsupportedOperationException(); }

    public static List<Orientation> all() { throw new UnsupportedOperationException(); }

    @Override public int compareTo(Orientation other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public record Placement(Orientation orientation, LatticePoint offset) {
    public static final Placement IDENTITY = null;

    public Placement compose(Placement other) { throw new UnsupportedOperationException(); }

    public Placement inverse() { throw new UnsupportedOperationException(); }

    public LatticePoint apply(LatticePoint point) { throw new UnsupportedOperationException(); }

    public Cuboid apply(Cuboid box) { throw new UnsupportedOperationException(); }

    public CuboidSet apply(CuboidSet cells) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public record Siting(LatticePoint origin, Placement placement) {
    public static Siting of(LatticePoint origin, Orientation orientation) { throw new UnsupportedOperationException(); }

    public static Siting of(LatticePoint origin, Placement placement) { throw new UnsupportedOperationException(); }

    public Siting withOrigin(LatticePoint origin) { throw new UnsupportedOperationException(); }

    public Siting withPlacement(Placement placement) { throw new UnsupportedOperationException(); }

    public static Siting anchoring(LatticePoint anchorTarget, LatticePoint anchorLocal, Placement placement) { throw new UnsupportedOperationException(); }

    public LatticePoint toWorld(LatticePoint local) { throw new UnsupportedOperationException(); }

    public LatticePoint toLocal(LatticePoint world) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public enum LocusFrame {
    DEFINITION, LOCAL, WORLD, KEY
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.SourceSpan;
import dev.vfyjxf.cumulus.kernel.engine.InstanceId;

public record KeyLocus(InstanceId instance, NamespacedId key) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.SourceSpan;
import dev.vfyjxf.cumulus.kernel.engine.InstanceId;

public record Locus(LocusFrame frame, CuboidSet cells, KeyLocus key, SourceSpan source) {
    public static Locus definition(SourceSpan source) { throw new UnsupportedOperationException(); }

    public static Locus local(CuboidSet cells) { throw new UnsupportedOperationException(); }

    public static Locus local(CuboidSet cells, SourceSpan source) { throw new UnsupportedOperationException(); }

    public static Locus world(CuboidSet cells) { throw new UnsupportedOperationException(); }

    public static Locus key(InstanceId instance, NamespacedId key) { throw new UnsupportedOperationException(); }

    public Locus withSource(SourceSpan source) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public record LocusMap(Siting siting) {
    public static LocusMap of(Siting siting) { throw new UnsupportedOperationException(); }

    public Locus toWorld(Locus locus) { throw new UnsupportedOperationException(); }

    public Locus toLocal(Locus locus) { throw new UnsupportedOperationException(); }

    public Locus toDefinition(Locus locus) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

public record ChunkKey(int cx, int cy, int cz) implements Comparable<ChunkKey> {
    @Override public int compareTo(ChunkKey other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.space;

import java.util.List;

public record ChunkGrid(int cellSize, LatticePoint origin) {
    public ChunkKey chunkOf(LatticePoint cell) { throw new UnsupportedOperationException(); }

    public Cuboid boundsOf(ChunkKey chunk) { throw new UnsupportedOperationException(); }

    public int chunkCount() { throw new UnsupportedOperationException(); }

    public List<ChunkKey> chunksOf(Cuboid box) { throw new UnsupportedOperationException(); }
}
```

## 3. `dev.vfyjxf.cumulus.kernel.occupant`

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

public record OccupantKind(NamespacedId id) implements Comparable<OccupantKind> {
    public static final OccupantKind BLOCK = null;
    public static final OccupantKind BLOCK_ENTITY = null;
    public static final OccupantKind ENTITY = null;
    public static final OccupantKind NESTED = null;
    public static final OccupantKind OTHER = null;

    public static OccupantKind of(NamespacedId id) { throw new UnsupportedOperationException(); }

    @Override public int compareTo(OccupantKind other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

public record OccupantRef(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)
        implements Comparable<OccupantRef> {
    public boolean from(SnapshotId id) { throw new UnsupportedOperationException(); }

    @Override public int compareTo(OccupantRef other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import java.util.List;
import java.util.Map;

public record OccupantDescriptor(NamespacedId type, OccupantKind kind,
                                Map<String, String> properties, List<NamespacedId> tags) {
    public String property(String key) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

public record TraitKey<T>(NamespacedId id, Class<T> type) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

public record Trait<T>(TraitKey<T> key, T value) {
    public static <T> Trait<T> of(TraitKey<T> key, T value) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import java.util.List;

public record OccupantSnapshot(OccupantRef ref, OccupantDescriptor descriptor, Cuboid footprint,
                               List<Trait<?>> traits, NestedView nested) {
    public static final NamespacedId AIR = null;

    public static OccupantSnapshot air(SnapshotId snapshot, LatticePoint cell) { throw new UnsupportedOperationException(); }

    public boolean isAir() { throw new UnsupportedOperationException(); }

    public <T> Trait<T> find(TraitKey<T> key) { throw new UnsupportedOperationException(); }

    public <T> T trait(TraitKey<T> key) { throw new UnsupportedOperationException(); }

    public boolean has(TraitKey<?> key) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

public enum ReadStatus {
    READABLE, OUT_OF_BOUNDS, NOT_CAPTURED, DENIED, STALE;

    public boolean readable() { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import java.util.List;

public interface WorldView {
    SnapshotId snapshotId();

    Cuboid bounds();

    OccupantSnapshot occupant(LatticePoint cell);

    List<OccupantSnapshot> occupants(Cuboid box);

    ReadStatus readStatus(LatticePoint cell);

    default OccupancySummary summary(Cuboid box) { throw new UnsupportedOperationException(); }

    default ChunkGrid chunkGrid() { throw new UnsupportedOperationException(); }

    default ChunkContent chunk(ChunkKey key) { throw new UnsupportedOperationException(); }
}
```

Note: `ChunkGrid`/`ChunkKey` are `kernel.space` types, imported above in the real file.

```java
package dev.vfyjxf.cumulus.kernel.occupant;

public interface NestedView extends WorldView {
    OccupantSnapshot parent();

    int depth();

    List<OccupantRef> ancestry();
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import java.util.Map;

public record OccupancySummary(Map<NamespacedId, Integer> byType, int total) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

public interface ChunkContent {
    Cuboid bounds();

    ReadStatus status();

    boolean allAir();

    boolean maybeHasBlock(NamespacedId type);

    int countBlock(NamespacedId type);

    OccupantSnapshot occupant(LatticePoint cell);

    ReadStatus readStatus(LatticePoint cell);
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

public record World(NamespacedId id) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

public record OwnerId(NamespacedId owner) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

public record SnapshotId(World world, long revision) implements Comparable<SnapshotId> {
    @Override public int compareTo(SnapshotId other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

public record WorldWindow(Cuboid bounds, int maxElements) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.Set;

public record SnapshotPolicy(CaptureMode capture, Set<OccupantKind> requiredKinds,
                             Set<TraitKey<?>> requiredTraits, int maxElements,
                             int maxNestedDepth, long maxStalenessNanos) {
    public enum CaptureMode {
        EXACT, REQUIRED
    }

    public static SnapshotPolicy minimal(int maxElements) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

public interface WorldCapture {
    CaptureResult capture(CaptureRequest request);

    long latestRevision();
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.ChunkGrid;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import java.util.Set;

public record CaptureRequest(Cuboid bounds, ChunkGrid grid, SnapshotPolicy policy,
                             Set<NamespacedId> trackedTypes, Set<LatticeKey> trackedCells,
                             CaptureLimits limits) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

public record CaptureLimits(int maxCells, int maxSections, long maxBytes, int maxEntities, int maxPayloads) {
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.finding.FindingCode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

public sealed interface CaptureResult
        permits CaptureResult.Captured, CaptureResult.TooLarge, CaptureResult.Unavailable {

    record Captured(WorldView view) implements CaptureResult {
    }

    record TooLarge(Cuboid bounds, int elements) implements CaptureResult {
    }

    record Unavailable(ReadStatus status, FindingCode code) implements CaptureResult {
    }
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import java.util.Set;

public interface OccupantResolver<R> {
    Set<OccupantKind> kinds();

    OccupantSnapshot resolve(R raw, ResolveContext context);
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

public interface ResolveContext {
    SnapshotId snapshotId();

    LatticePoint anchor();

    int remainingDepth();

    SnapshotPolicy policy();
}
```

```java
package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

public final class Traits {
    public static final TraitKey<?> ITEM_INVENTORY = null;
    public static final TraitKey<?> ENERGY = null;
    public static final TraitKey<?> FLUID = null;
    public static final TraitKey<?> TIER = null;

    private Traits() {
        throw new UnsupportedOperationException();
    }
}
```

## 4. Foreign types the three docs reference but do not define here

```java
package dev.vfyjxf.cumulus.kernel.finding;

public record FindingCode(String value) implements Comparable<FindingCode> {
    @Override public int compareTo(FindingCode other) { throw new UnsupportedOperationException(); }
}
```

```java
package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.occupant.World;

public record InstanceId(World world, long serial) {
}
```
