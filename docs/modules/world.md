# Module `kernel.world`

> Rank 2. What is in a cell, what a snapshot is, how a view answers, and the four ports a host
> implements to capture a world. The kernel never reads a live world; a `WorldView` is frozen.

## 1. Scope

**Owns:** `OccupantKind`, `OccupantRef`, `OccupantDescriptor`, `TraitKey<T>`, `Trait<T>`,
`OccupantSnapshot`, `OccupantFilter`, `ReadStatus`, `SectionSlot`, `SectionSnapshot`, `WorldView`,
`SnapshotPolicy`, `CaptureRequest`, `CaptureResult`, `WorldCapture`, `SnapshotLease`,
`OccupantResolver<R>`, `ResolveContext`, `EntityWindow`.

**Does not own:** geometry (`space`), claims (`claim`), what an expectation says (`structure`), the
capture implementation (`mc`), dirty tracking or scheduling (host).

**Imports:** `core`, `space`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `OccupantKind` | record | open family id with constants `block`, `blockEntity`, `entity`, `nested` |
| `OccupantRef` | record | `(snapshot, frame, type, kind, anchor)`; identity within one capture and frame |
| `OccupantDescriptor` | record | `(type, kind, properties, tags)`; ids and strings only |
| `TraitKey<T>` | record | `(id, Class<T>)` |
| `Trait<T>` | record | `(key, @Nullable value)`; present-with-null is a fact |
| `OccupantSnapshot` | record | ref + descriptor + footprint + traits; air is a real occupant |
| `OccupantFilter` | record | optional type/kind/trait predicates for a box query |
| `ReadStatus` | enum | `readable`, `outOfBounds`, `notCaptured`, `denied` |
| `SectionSlot` | sealed | `Air(revision)`, `Present(section)`, `Refused(status, code)` |
| `SectionSnapshot` | record | one frozen section's non-air occupants and content digest |
| `WorldView` | interface | recursive, section-based, immutable read face |
| `SnapshotPolicy` | record | capture ceiling and required content |
| `CaptureRequest` | record | bounds + policy |
| `CaptureResult` | sealed | `Captured(view, lease)`, `TooLarge`, `Unavailable` |
| `WorldCapture` | interface | host capture port, one per world |
| `SnapshotLease` | interface | view + `isCurrent()`; host-owned resource |
| `OccupantResolver<R>` | interface | raw host value → occupant snapshot, or decline |
| `ResolveContext` | record | what a resolver may see |
| `EntityWindow` | record | `(Cuboid cells, NamespacedId type)`, inflated by `maxEntitySpan` |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.world;

public record OccupantKind(NamespacedId id) implements Comparable<OccupantKind> {
    public static final OccupantKind block;
    public static final OccupantKind blockEntity;
    public static final OccupantKind entity;
    public static final OccupantKind nested;
    public static OccupantKind of(NamespacedId id);
    @Override public int compareTo(OccupantKind other);
}

public record OccupantRef(SnapshotId snapshot, int frame, NamespacedId type, OccupantKind kind,
                          LatticePoint anchor) implements Comparable<OccupantRef> {
    public boolean from(SnapshotId snapshot);
    @Override public int compareTo(OccupantRef other);
}

public record OccupantDescriptor(NamespacedId type, OccupantKind kind,
                                 java.util.Map<String, String> properties,
                                 java.util.List<NamespacedId> tags) {
    public @Nullable String property(String key);
}

public record TraitKey<T>(NamespacedId id, Class<T> type) { }

public record Trait<T>(TraitKey<T> key, @Nullable T value) {
    public static <T> Trait<T> of(TraitKey<T> key, T value);
    public static <T> Trait<T> absent(TraitKey<T> key);
}

public record OccupantSnapshot(OccupantRef ref, OccupantDescriptor descriptor, Cuboid footprint,
                               java.util.List<Trait<?>> traits) {
    public static final NamespacedId air;
    public static OccupantSnapshot air(SnapshotId snapshot, int frame, LatticePoint cell);
    public boolean isAir();
    public <T> @Nullable Trait<T> find(TraitKey<T> key);
    public <T> @Nullable T trait(TraitKey<T> key);
    public boolean has(TraitKey<?> key);
}

public record OccupantFilter(java.util.function.Predicate<NamespacedId> type,
                             java.util.function.Predicate<OccupantKind> kind,
                             java.util.function.Predicate<NamespacedId> trait) {
    public static OccupantFilter any();
    public static OccupantFilter ofType(NamespacedId type);
    public static OccupantFilter ofKind(OccupantKind kind);
    public static OccupantFilter ofTrait(NamespacedId trait);
    public boolean matches(OccupantSnapshot occupant);
}

public enum ReadStatus {
    readable, outOfBounds, notCaptured, denied;
    public boolean readable();
}

public sealed interface SectionSlot {
    record Air(long revision) implements SectionSlot { }
    record Present(SectionSnapshot section) implements SectionSlot { }
    record Refused(ReadStatus status, FindingCode code) implements SectionSlot { }
    default boolean readable();
    default ReadStatus status();
}

public record SectionSnapshot(SectionKey key, java.util.Map<LatticePoint, OccupantSnapshot> occupants,
                              long contentDigest) {
    public static SectionSnapshot of(SectionKey key,
                                     java.util.Map<LatticePoint, OccupantSnapshot> occupants,
                                     long contentDigest);
    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    public java.util.List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
}

public interface WorldView {
    SnapshotId snapshotId();
    int frame();
    int depth();
    LatticePoint origin();
    @Nullable WorldView parent();
    int cellSize();
    SectionKey sectionOf(LatticePoint cell);
    SectionSlot slot(SectionKey key);
    @Nullable OccupantSnapshot occupant(LatticePoint cell);
    @Nullable OccupantSnapshot occupant(OccupantRef ref);
    java.util.List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
    @Nullable WorldView nested(LatticePoint cell);
    long contentDigest(SectionKey key);
    default ReadStatus statusOf(LatticePoint cell);
}

public record SnapshotPolicy(int maxElements, int maxDepth,
                             java.util.Set<OccupantKind> kinds,
                             java.util.Set<TraitKey<?>> traits,
                             java.util.List<EntityWindow> windows) {
    public static SnapshotPolicy minimal();
}

public record CaptureRequest(Cuboid bounds, SnapshotPolicy policy) { }

public sealed interface CaptureResult {
    record Captured(WorldView view, SnapshotLease lease) implements CaptureResult { }
    record TooLarge(Cuboid bounds, int elements) implements CaptureResult { }
    record Unavailable(ReadStatus status, FindingCode code) implements CaptureResult { }
}

public interface WorldCapture {
    CaptureResult capture(CaptureRequest request);
    long currentRevision();
}

public interface SnapshotLease extends AutoCloseable {
    SnapshotId id();
    WorldView view();
    boolean isCurrent();
    @Override void close();
}

public interface OccupantResolver<R> {
    java.util.Set<OccupantKind> kinds();
    @Nullable OccupantSnapshot resolve(R raw, ResolveContext context);
}

public record ResolveContext(SnapshotId snapshot, int frame, LatticePoint anchor,
                             int remainingDepth, SnapshotPolicy policy) { }

public record EntityWindow(Cuboid cells, NamespacedId type) {
    public static final int maxEntitySpan = 3;
    public static @Nullable EntityWindow of(Cuboid declared, NamespacedId type); // inflates by 3
}
```

## 4. Laws

### 4.1 Air and unreadability are different

`occupant(cell)` returns a non-null `OccupantSnapshot` exactly when the cell's section is readable
(`Air` or `Present`). A cell in a refused section returns `null`, never air. An air cell is a real
`OccupantSnapshot` with `ref.type == air`, kind `block`, footprint one cell, no traits. A predicate
that sees air as "nothing there" is wrong; it sees a definite occupant whose type is air.

`statusOf(cell)` is the section's status: `readable` for `Air`/`Present`, otherwise the `Refused`
status. `denied` is a host decision; `notCaptured` is a cap/depth/unloaded decision;
`outOfBounds` is outside the captured bounds or a key of another frame.

### 4.2 A footprint is evidence, not a permission

`OccupantSnapshot.footprint` is the whole body in the view's frame. `occupants(box, filter)` returns
an occupant if its footprint intersects `box`; the whole occupant is returned, never a clip. The
footprint does **not** make a refused cell readable: a consumer that walks the footprint must ask
`occupant` for each cell and stop at `null`. A check over a multi-cell body whose tail is
`notCaptured` is `Unreadable`, never a clipped match.

### 4.3 One occupant per cell

Two occupants may overlap only through a host-resolved body (an entity over a block, a nested
structure over a cell). `occupant(cell)` narrows by: non-air, then smaller footprint volume, then
`OccupantRef` order. A block section's cell maps to its block snapshot; an entity footprint maps
every covered cell to the entity snapshot; a nested structure's anchor maps to the nested occupant.
A host that cannot decide is `denied`, not a guess.

### 4.4 Identity and frames

`OccupantRef` carries the `SnapshotId` and the `frame` it was minted in. Equality is the full
record; `from(snapshot)` compares only the snapshot. A ref from another frame is not answerable by
this view: `occupant(ref)` returns null unless `ref.snapshot().equals(snapshotId())` and
`ref.frame() == frame()`. A nested view has `depth = parent.depth + 1`, the same `snapshotId`, a
unique `frame` and its own local origin. `parent()` is null at the root. There is no `nested` field
on `OccupantSnapshot`; the child view is reached through `WorldView.nested(cell)`.

### 4.5 Sections

`sectionOf(cell)` uses the view's `cellSize` and `frame`; `slot(key)` answers only keys of this
frame. `Air(revision)` means read and empty. `Present` holds a `SectionSnapshot` of non-air
occupants; a missing cell in a present section is air. `Refused` carries a status and a code; its
`occupantAt` is null. `contentDigest(key)` is the host's stable digest of the frozen content; it is
`0` for `Air` and `Refused`. A view answers a key of another frame/world with
`Refused(outOfBounds, SC-OCC-0003)`.

### 4.6 Capture and lease

`WorldCapture` is a host port. It returns `Captured(view, lease)`, `TooLarge` or `Unavailable`; it
never returns a partial view treated as complete. `SnapshotLease.isCurrent()` is the single gate:
true only while the view is a faithful capture of a live world at its revision and the handle is
open. `close()` releases the host's resource; the kernel never closes a lease. `currentRevision()`
is the host's monotonic per-world counter; `SnapshotId.revision` is stamped from it.

`SnapshotPolicy` is a ceiling, not a request to over-read: `maxElements` and `maxDepth` bound the
capture; `kinds` and `traits` say what must be resolved; `windows` names the entity windows a read
scope needs. A required trait that cannot be resolved makes the cell `notCaptured`, never a default.

### 4.7 Traits and descriptors

`OccupantDescriptor` is version-independent: four id/string components, no state id, ordinal or
class name. Properties are a sorted immutable map; tags are sorted and deduped. `property(k)`
returns `null` when the host published nothing, never `false`.
`TraitKey` equality is `(id, type)`; one id under two types is a capture error. A trait is captured
once and never recomputed; `find == null` means "not captured" (unreadable for a required trait),
present with `value == null` is a definite fact. Trait values are immutable values (a string, boxed
primitive, `NamespacedId`, an immutable record/collection); a live host object is a host bug.

### 4.8 Entity windows

An entity can move within a section without a block change, so an entity check declares a window:
the bay box inflated by `maxEntitySpan` on every axis, clamped to the lattice. The window is a
read-scope entry, not a capture bound; `SnapshotPolicy.windows` asks the host to include it. The
kernel has no type-body data, so a body wider than one section is the host's capture concern, not a
blueprint build error.

## 5. Pipeline role

- `compile`: a document names occupant kinds/traits; no view.
- `check`: every read goes through the view; a missing cell becomes `SC-OCC-0001` at the focus.
- `settle`: the lease gate is the only world fact; claim rows come from the check's bindings.
- `derive`: no view; only `StructureContext`.

## 6. Codes

`SC-OCC-0001` (ERROR, primary): unreadable cell. Producer: `Focuses`/`Focus` when `occupant` is
null. `SC-OCC-0002` (WARNING, primary): a footprint leaves the snapshot; only the intersection was
compared. Producer: `Focuses` when a footprint is not fully readable. `SC-OCC-0003` (ERROR, primary):
a nested view was cut (depth, cap or cycle). Producer: `Focuses`/`WorldView` consumer.

The `Refused` slot carries a `FindingCode` from the host; the kernel's own code for a refused read is
chosen by the consumer, not by the slot.

## 7. Tests

- `occupant` returns air for a readable empty cell, null for a refused section; `statusOf` matches.
- Multi-cell occupant: one ref, whole footprint, one entry in `occupants`; tail unreadable ⇒ null.
- Nested view: same snapshot, distinct frame, depth/parent, `occupant(ref)` rejects a foreign frame.
- `SectionSnapshot`: canonical occupant order, `contentDigest`, box query.
- Descriptor: properties/tags immutable, sorted; trait find/has/absent.
- Lease gate: `isCurrent` false after the host invalidates; `check` returns `Dropped`.
- `EntityWindow.of` inflation and lattice clamp; `maxEntitySpan` rejection.

## 8. Dependencies

`core` (`NamespacedId`, `World`, `InstanceId`, `SnapshotId`, `SourceSpan`, `FindingCode`,
`@Nullable`) and `space` (`LatticePoint`, `Cuboid`, `CuboidSet`, `SectionKey`). It never imports
`finding`, `claim`, `structure`, `context`, `engine` or `spi`.
