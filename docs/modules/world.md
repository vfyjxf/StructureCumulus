# Module `kernel.world`

> Rank 2. What is in a cell, what a snapshot is, how a view answers, and the ports a host implements
> to capture a world. The kernel never reads a live world; a `WorldView` is frozen and every read is
> total: it answers with a `CellRead`, never with a bare `null`.

## 1. Scope

**Owns:** `OccupantKind`, `OccupantRef`, `OccupantDescriptor`, `TraitKey<T>`, `Trait<T>`,
`TraitSlot`, `OccupantSnapshot`, `OccupantFilter`, `ReadStatus`, `ReadRefusal`, `CellRead`,
`SectionSlot`, `SectionSnapshot`, `WorldView`, `SnapshotPolicy`, `CaptureRequest`, `CaptureResult`,
`WorldCapture`, `SnapshotLease`, `OccupantResolver<R>`, `ResolveContext`, `EntityWindow`.

**Does not own:** geometry (`space`), claims (`claim`), what an expectation says (`structure`), the
capture implementation (`mc`), dirty tracking or scheduling (host).

**Imports:** `core`, `space`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `OccupantKind` | record | open family id with constants `block`, `blockEntity`, `entity`, `nested` |
| `OccupantRef` | record | `(snapshot, frame, type, kind, anchor, @Nullable body)`; identity within one capture and frame, including the body |
| `OccupantDescriptor` | record | `(type, kind, properties, tags)`; ids and strings only |
| `TraitKey<T>` | record | `(id, Class<T>)`; a key is typed, a trait is identified by its id |
| `Trait<T>` | record | `(key, @Nullable value)`; present-with-null is a fact |
| `TraitSlot` | sealed | `Present(trait)`, `Absent(id)`, `Refused(id, refusal)`; one slot per trait id |
| `OccupantSnapshot` | record | ref + descriptor + footprint + trait slots; air is a real occupant |
| `OccupantFilter` | record | optional type/kind/trait predicates for a box query |
| `ReadStatus` | enum | `readable`, `outOfBounds`, `notCaptured`, `denied`, `failed` |
| `ReadRefusal` | record | `(status, code)`; the status is never `readable` |
| `CellRead` | sealed | `Found(occupant)` or `Unreadable(refusal)`; the total per-cell read |
| `SectionSlot` | sealed | `Air(revision)`, `Present(section)`, `Partial(section, unreadable)`, `Refused(status, code)` |
| `SectionSnapshot` | record | one frozen section's readable non-air occupants and content digest |
| `WorldView` | interface | recursive, section-based, immutable read face; `read` is the primitive |
| `SnapshotPolicy` | record | capture ceiling and required content; `traits` is a set of trait ids |
| `CaptureRequest` | record | bounds + policy |
| `CaptureResult` | sealed | `Captured(view, lease)`, `TooLarge`, `Unavailable` |
| `WorldCapture` | interface | host capture port, one per world |
| `SnapshotLease` | interface | view + `isCurrent()`; host-owned resource |
| `OccupantResolver<R>` | interface | raw host value → occupant snapshot, or decline |
| `ResolveContext` | record | what a resolver may see |
| `EntityWindow` | record | `(Cuboid cells, NamespacedId type)`, inflated by `maxEntitySpan` |

## 3. Signatures

> **Pending implementation (Model C).** `ReadStatus.failed`, `ReadRefusal`, `CellRead`,
> `SectionSlot.Partial`, `TraitSlot`, the `body` component of `OccupantRef`, and the new
> `WorldView`/`SnapshotPolicy` members are the approved shape. The committed kernel still has the
> section-only refusal model and a `Set<TraitKey<?>>` policy; until the rebase lands,
> `tools/spec-check.py` reports the new names as documented-but-not-in-code.

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
                          LatticePoint anchor, @Nullable NamespacedId body)
        implements Comparable<OccupantRef> {
    public OccupantRef(SnapshotId snapshot, int frame, NamespacedId type, OccupantKind kind,
                       LatticePoint anchor);
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
    public static <T> Trait<T> absent(TraitKey<T> key);   // present with a null value, not TraitSlot.Absent
}

public sealed interface TraitSlot {
    record Present(Trait<?> trait) implements TraitSlot { }
    record Absent(NamespacedId id) implements TraitSlot { }
    record Refused(NamespacedId id, ReadRefusal refusal) implements TraitSlot { }
    default NamespacedId id();
}

public record OccupantSnapshot(OccupantRef ref, OccupantDescriptor descriptor, Cuboid footprint,
                               java.util.List<TraitSlot> traits) {
    public static final NamespacedId air;
    public static OccupantSnapshot air(SnapshotId snapshot, int frame, LatticePoint cell);
    public boolean isAir();
    public @Nullable TraitSlot slot(NamespacedId id);
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
    readable, outOfBounds, notCaptured, denied, failed;
    public boolean readable();
}

public record ReadRefusal(ReadStatus status, FindingCode code) {
    public ReadRefusal { /* status non-null and not readable; code non-null */ }
}

public sealed interface CellRead {
    record Found(OccupantSnapshot occupant) implements CellRead { }
    record Unreadable(ReadRefusal refusal) implements CellRead { }
}

public sealed interface SectionSlot {
    record Air(long revision) implements SectionSlot { }
    record Present(SectionSnapshot section) implements SectionSlot { }
    record Partial(SectionSnapshot section, java.util.Map<LatticePoint, ReadRefusal> unreadable)
            implements SectionSlot { }
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
    Cuboid bounds();
    SnapshotPolicy policy();
    SectionKey sectionOf(LatticePoint cell);
    SectionSlot slot(SectionKey key);
    CellRead read(LatticePoint cell);
    default @Nullable OccupantSnapshot occupant(LatticePoint cell);
    default @Nullable ReadRefusal refusalAt(LatticePoint cell);
    default ReadStatus statusOf(LatticePoint cell);
    @Nullable OccupantSnapshot occupant(OccupantRef ref);
    java.util.List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
    @Nullable WorldView nested(LatticePoint cell);
    long contentDigest(SectionKey key);
}

public record SnapshotPolicy(int maxElements, int maxDepth,
                             java.util.Set<OccupantKind> kinds,
                             java.util.Set<NamespacedId> traits,
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

### 4.1 One total read

`WorldView.read(cell)` is the only read primitive; `occupant(cell)`, `refusalAt(cell)` and
`statusOf(cell)` are derived and never a second source of truth:

```
read(cell) = Found(o)    ⟺ occupant(cell) == o, refusalAt(cell) == null, statusOf(cell) == readable
read(cell) = Unreadable(r) ⟺ occupant(cell) == null, refusalAt(cell) == r, statusOf(cell) == r.status()
```

`Found` carries a non-null occupant; `Unreadable` carries a non-readable `ReadRefusal`. There is no
third arm, no nullable result and no exception.

**Bounds.** A cell outside `bounds()` is `Unreadable(outOfBounds, SC-OCC-0004)`, never air and never
`null`. **Inside `bounds()`:**

| Slot of `sectionOf(cell)` | `read(cell)` |
| --- | --- |
| `Air` | `Found(air)` |
| `Present` | `Found` the section's occupant, or `Found(air)` when the cell is absent from the section |
| `Partial` | `Unreadable` the map's refusal when the cell is a key, else as `Present` |
| `Refused` | `Unreadable(new ReadRefusal(refused.status(), refused.code()))` |

A cell in a refused section returns `null` from `occupant`, never air; a readable empty cell is a real
air occupant. A predicate that sees air as "nothing there" is wrong; it sees a definite occupant whose
type is air.

`failed` is a host read error (a malformed capture, an exception while reading one carried trait). It
is distinct from `denied` (a host decision), `notCaptured` (a cap, depth, cycle or unloaded chunk) and
`outOfBounds` (outside `bounds()` or a key of another frame). The kernel never invents a `failed`
answer; it carries one. `readable` is the only status a `ReadRefusal` may not have.

The kernel's default refusal code is `SC-OCC-0004` (required read unavailable). A host may put its
own code in a `ReadRefusal` (for example `SC-MC-0002`); the kernel never branches on it, never mints
it and never allocates it in `Codes.all`. A report site relays the host code, except that a code
already present in `Codes.all` is replaced by `SC-OCC-0004` plus a note: a host cannot impersonate a
kernel code. §6 owns the producer table.

### 4.2 A footprint is evidence, not a permission

`OccupantSnapshot.footprint` is the whole body in the view's frame. `occupants(box, filter)` returns
an occupant if its footprint intersects `box`; the whole occupant is returned, never a clip. The
footprint does **not** make a refused cell readable: a consumer that walks the footprint must ask
`read` (or `occupant`) for each cell and stop at `Unreadable`. A check over a multi-cell body whose
tail is `notCaptured` is `Unreadable`, never a clipped match.

### 4.3 One occupant per cell

Two occupants may overlap only through a host-resolved body: an entity over a block, a nested
structure over a cell, or an entity inside a nested frame. `occupant(cell)` narrows by: non-air, then
smaller footprint volume, then `OccupantRef` order (which includes `body`). A block section's cell
maps to its block or block-entity snapshot; an entity footprint maps every covered cell to the entity
snapshot; a nested body's footprint maps every covered cell to the nested snapshot. A host that cannot
decide is `denied`, not a guess.

`occupants(box, filter)` is the union of readable section occupants, entity bodies and nested bodies
whose footprint intersects the query, deduplicated by ref and ordered by ref. A body whose frame is
not this view's frame is not answerable here.

`nested(cell)` is anchor-only and independent of this narrowing: it answers the child view registered
at the anchor cell even when `occupant(cell)` narrows to a block or an entity over the same cell. A
cell that a nested footprint covers but that is not its anchor answers `null`.

### 4.4 Identity and frames

`OccupantRef` carries the `SnapshotId`, the `frame` it was minted in, and an opaque `body` identity.
`body` distinguishes two bodies of the same type at the same anchor; blocks, air and nested bodies may
use `null`, and the adapter gives an entity the UUID-derived id. Equality and `compareTo` use the full
record, including `body`; the order is `(snapshot, frame, kind, type, anchor, body)` and the nullable
component orders `null` first. The five-argument constructor is the compatibility form and passes
`null`. This closes the same-anchor entity collapse: two entities of one type at one anchor are two
occupants, not one.

`from(snapshot)` compares only the snapshot. A ref from another snapshot or frame is not answerable:
`occupant(ref)` returns `null` unless `ref.snapshot().equals(snapshotId())`, `ref.frame() == frame()`
and the ref matches the indexed body exactly.

There is one `SnapshotId` per capture, shared by every frame. A nested view has
`depth = parent.depth + 1`, a unique `frame`, the same `snapshotId`, the exposing parent and its own
local origin. `parent()` is null at the root. **The root** has frame `0`, depth `0`, `origin`
`(0,0,0)` and world bounds/cells. **A child** has `origin` = the extent's origin (the world position
of child-local `(0,0,0)`), local bounds = the extent's absolute bounds translated by `-origin`, and
local cells; every world cell is `origin + local`. `policy()` is the same capture policy in every
frame; `maxDepth` is the nested ceiling, so a view at `depth == policy().maxDepth()` cannot nest
further.

There is no `nested` field on `OccupantSnapshot`; a child view is reached through
`WorldView.nested(cell)` and a nested body is an ordinary parent-frame occupant with kind `nested`,
its anchor at the capability cell and its footprint translated into the parent frame.

### 4.5 Sections

`sectionOf(cell)` uses the view's `cellSize` and `frame`; `slot(key)` answers only keys of this frame.
`Air(revision)` means read and empty. `Present` holds a `SectionSnapshot` of non-air occupants and
every cell of the section is readable; a missing cell is air. `Partial` holds the readable occupants
plus a non-empty map of the section's unreadable cells to their refusals: a cell in the map is
`Unreadable`, a cell outside it is readable (occupant or air). The map's keys lie in the section and
never name an occupant in the snapshot. `Refused` carries a status and a code; every cell of the
section is unreadable. `readable()` is true for `Air`, `Present` and `Partial`; `status()` is the
status of a `Refused` slot, else `readable`.

`contentDigest(key)` is the host's stable digest of the frozen readable content; it is `0` for `Air`
and `Refused` and the readable content's digest for `Present` and `Partial`. A view answers a key of
another frame or world with `Refused(outOfBounds, SC-OCC-0004)`.

### 4.6 Capture and lease

`WorldCapture` is a host port. It returns `Captured(view, lease)`, `TooLarge` or `Unavailable`; it
never returns a partial root view treated as complete. `SnapshotLease.isCurrent()` is the single gate:
true only while the view is a faithful capture of a live world at its revision and the handle is
open. `close()` releases the host's resource; the kernel never closes a lease. `currentRevision()` is
the host's monotonic per-world counter; `SnapshotId.revision` is stamped from it.

`SnapshotPolicy` is a ceiling, not a request to over-read: `maxElements` bounds the total captured
elements (root plus every child frame); `maxDepth` bounds the nested frames below the root; `kinds`
and `traits` say what must be resolved; `windows` names the entity windows a read scope needs. The
host derives the policy from `Blueprint.reads()` (`structure` §4.5). A required trait that cannot be
resolved is a `TraitSlot.Refused` on the occupant, never a fabricated default and never a refusal of
the whole cell.

A trait that was never asked for has no slot. That is "unasked", not `Absent`: a consumer that needs
the trait reports an unreadable trait, never a mismatch. The same rule holds for a `Refused` slot.

### 4.7 Traits and descriptors

`OccupantDescriptor` is version-independent: four id/string components, no state id, ordinal or class
name. Properties are a sorted immutable map; tags are sorted and deduped. `property(k)` returns
`null` when the host published nothing, never `false`.

`OccupantSnapshot.traits` is one `TraitSlot` per trait id, sorted by id. The four states are
exhaustive:

| Slot | `trait(key)` (presence) | `trait(key, test)` (value) | `traitAbsent(key)` |
| --- | --- | --- | --- |
| `Present(Trait(key, value))` | `Match` | `Match` when `test(value)`, else `Mismatch` | `Mismatch` |
| `Present(Trait(key, null))` | `Match` | `Match` (present but valueless; the predicate is not applied) | `Mismatch` |
| `Absent(id)` | `Mismatch(SC-EXP-0001)` | `Mismatch(SC-EXP-0001)` | `Match` |
| `Refused(id, refusal)` or no slot | `Unreadable` | `Unreadable` | `Unreadable` |

`Present(null)` is a positive fact, not an absence. `Absent` is a positive fact of no trait. A missing
slot is "unasked" and is unreadable, never `Absent` and never a `Mismatch`. `not(trait(...))` maps
`Match` to `Mismatch(SC-EXP-0002)` and `Mismatch` to `Match`, but leaves `Unreadable` unchanged; it
must not turn a failed read into a `Match` (`structure` §4.3).

`slot(id)` returns the slot or `null` when unasked. `find(key)` finds by id and returns a present
trait only when the value is `null` or `key.type().isInstance(value)`; the value-null case returns a
present-null `Trait<T>`. A JSON factory's `TraitKey<Object>` therefore reads a typed capture. `trait`
returns the present value (null for present-null) or null when not present, so it cannot distinguish
present-null from unasked; `has` is true for any present slot and false for absent/refused/unasked.
A four-state test uses `slot` or `find`.

`TraitKey` equality is `(id, type)`; one id under two types is a capture error. A trait's identity is
its id, so two keys with one id denote one slot. A trait is captured once and never recomputed.
Trait values are immutable values (a string, boxed primitive, `NamespacedId`, an immutable
record/collection); a live host object is a host bug. `OccupantFilter.ofTrait(id)` matches a
`Present` slot only; `Absent`, `Refused` and an unasked slot are not matches, so a filtered query is
never a totality proof.

### 4.8 Entity windows

An entity can move within a section without a block change, so an entity check declares a window:
the bay box inflated by `maxEntitySpan` on every axis, clamped to the lattice. The window is a
read-scope entry, not a capture bound; `SnapshotPolicy.windows` asks the host to include it. The
kernel has no type-body data, so a body wider than one section is the host's capture concern, not a
blueprint build error. The body identity of §4.4 is what keeps two entities in one window distinct
when they share a type and an anchor.

### 4.9 Nested frames

A nested extent is a declaration from the host: `(type, absolute bounds, origin)`. The adapter owns
the capture and builds one child frame for it, or cuts it; the kernel never queries a capability. The
child frame has the same `SnapshotId`, a unique `frame`, `depth + 1`, the exposing view as `parent`,
the extent origin and the translated local bounds. A nested body is indexed in the **parent** frame
with kind `nested`, anchor at the capability cell and footprint = the extent's bounds translated into
that parent's frame; it is not in any `SectionSnapshot` and does not enter a section digest.

A child that cannot be built (depth, element budget, cycle, unloaded chunk) is cut: the nested body
stays, no child exists, `nested(cell)` returns `null`, and the consumer reports `SC-OCC-0003`. A cut
is never a partial child and never air. A child whose bounds leave the level's build height keeps the
child but its outside sections are `Refused`/`Partial`; those cells are never air. The nested body and
child view are host-capture facts; the kernel treats a `null` `nested(cell)` as "not available here",
not as an empty structure.

## 5. Pipeline role

- `compile`: a document names occupant kinds/traits; no view.
- `check`: every read goes through `read`; a needed unreadable cell is reported with the refusal's
  code (relayed through the trust guard) or `SC-OCC-0001` when the focus has no refusal to relay.
- `settle`: the lease gate is the only world fact; claim rows come from the check's bindings; a
  nested subject is never a claim binding.
- `derive`: no view; only `StructureContext`.

## 6. Codes

`SC-OCC-0001` (ERROR, primary): a focus needed a subject or cell that is absent for a reason other
than a read refusal (for example no entity of the requested type in the bay). Producer: `Focuses`.
`SC-OCC-0002` (WARNING, primary): a footprint leaves the snapshot; only the intersection was
compared. Producer: `Focuses`. `SC-OCC-0003` (ERROR, primary): a nested view was cut (depth, cap,
cycle or unloaded chunk). Producer: `Focuses.nested`/`WorldView` consumer. `SC-OCC-0004` (ERROR,
primary): a required read is unavailable; the kernel's default `ReadRefusal` code and the substitute
for a relayed code that is already a kernel code. Producer: `WorldView.read`, `Focuses`,
`SectionContext`/`StructureContext` consumers.

A host code in a `ReadRefusal` is relayed by the report site and is not part of `Codes.all`; the
kernel never branches on it. §4.1 owns the trust guard.

## 7. Tests

- `read` outside `bounds` is `outOfBounds`, never air; inside, `Found`/`Unreadable` agree with the
  derived `occupant`/`refusalAt`/`statusOf`.
- `Partial`: a refused cell is unreadable, its neighbours are readable, the map is non-empty and lies
  in the section; a missing cell outside the map is air.
- Refused section: every cell unreadable with the slot's code; `occupant` null for all of them.
- Trait slots: present, present-null, absent and refused; one slot per id; `slot`/`find`/`trait`/
  `has` agree; a JSON `TraitKey<Object>` reads a typed capture; no slot is not `Absent`.
- Four-state expectations: present/present-null/absent/refused; `not` never promotes a refused read;
  a missing slot is never a mismatch.
- Body identity: two entities of one type at one anchor are two refs and two occupants; the five-arg
  constructor equals a six-arg ref with null; ordering puts null first.
- Nested view: same snapshot, distinct frame, depth/parent, root origin `(0,0,0)`, local bounds and
  `world = origin + local`; `occupant(ref)` rejects a foreign frame or body; `nested(cell)` is
  anchor-only.
- `SectionSnapshot`: canonical occupant order, `contentDigest`, box query.
- Descriptor: properties/tags immutable, sorted; trait find/has/absent.
- Lease gate: `isCurrent` false after the host invalidates; `check` returns `Dropped`.
- `EntityWindow.of` inflation and lattice clamp; `maxEntitySpan` rejection.
- `SnapshotPolicy`: `traits` is a set of ids; a non-empty set stays non-empty; duplicate ids collapse
  to one entry; `maxDepth` is the child ceiling.

## 8. Dependencies

`core` (`NamespacedId`, `World`, `InstanceId`, `SnapshotId`, `SourceSpan`, `FindingCode`,
`@Nullable`) and `space` (`LatticePoint`, `Cuboid`, `CuboidSet`, `SectionKey`). It never imports
`finding`, `claim`, `structure`, `context`, `engine` or `spi`.
