# 06 — Occupants and the world window

> U2 · v0.1 · 2026-10-20 · R-01, R-02, R-08 · I-2, I-3, I-4, I-13.

## 1. Scope

**In:** `OccupantKind`, `OccupantRef`, `OccupantDescriptor`, `TraitKey`, `Trait`,
`OccupantSnapshot`, `SectionSlot`, `SectionSnapshot`, `NestedView`, `NestedLevelSnapshot`,
`WorldView`, `OccupantFilter`, `ReadStatus`, `EntityWindow`, `EntityKey`, `ChangeSet`,
`SnapshotId`, `SnapshotPolicy`, `WorldWindow`, `WorldCapture`, `OccupantResolver<R>`,
`ResolveContext`.

**Out:** space primitives (U1); `Verdict`/`Reply` (U3 — `expectation` depends on `occupant`);
`Census`/`ReadSet` (U7); `Finding` (U9 — U2 reserves codes only); the capture implementation
(A1); claims (U4).

**Claim:** a match reads one immutable, single-revision snapshot; every cell is answered or
explicitly unanswerable; four occupant sources answer through one shape.

## 2. Vocabulary

| Name | Contract |
| --- | --- |
| `OccupantKind` | The family a resolver serves: an id record, five constants. |
| `OccupantRef` | `(snapshot, type, kind, anchor)`; equality is identity in one capture; the kernel's only order. |
| `OccupantDescriptor` | `(type, kind, properties, tags)`; ids and strings only. |
| `TraitKey<T>`, `Trait<T>` | Typed key `(id, Class<T>)`; a `(key, @Nullable value)` pair. |
| `OccupantSnapshot` | Immutable read model: ref, descriptor, footprint, traits, optional nested view. |
| `NestedView` | A substructure's window: a full `WorldView`, same snapshot, own frame, bounded depth. |
| `NestedLevelSnapshot` | The frozen interior of one nested occupant, read in its own frame: `parent()`, `depth()`, `contentDigest()`, `occupantAt(LatticePoint)`, `occupants(Cuboid, OccupantFilter)`. |
| `WorldView` | The only world window: one `SectionSlot` per section, cells answered through `occupant`; never touches the world. |
| `SectionSlot` | A section's answer: `Air` (read and empty), `Present(section)`, `Refused(status, code)`; `readable()` is `Air` or `Present`. |
| `SectionSnapshot` | One frozen section: `key`, `contentDigest`, `occupantAt(cell)`, `occupants(box, filter)`; every enumerated cell answers. |
| `OccupantFilter` | `(type, kind, trait)` predicates for a box query; a `null` component does not constrain. |
| `EntityWindow` | `(Cuboid cells, NamespacedId type)`: the box one entity type may move in; `MAX_ENTITY_SPAN = 3`. |
| `EntityKey` | `(UUID id, NamespacedId type, long dataDigest)`: one entity's identity in a window diff. |
| `ChangeSet` | `(cells, sections, payloads, entities, keys, revision)`: what changed between two revisions; `empty()`, `plus(other)` keeps the later revision. |
| `ReadStatus` | `READABLE`, `OUT_OF_BOUNDS`, `NOT_CAPTURED`, `DENIED`, `STALE`. |
| `SnapshotId`, `SnapshotPolicy`, `WorldWindow` | Capture identity, its guarantees, the requested box. |
| `WorldCapture`, `OccupantResolver<R>`, `ResolveContext` | Capture SPI, resolver SPI, resolver context. |

## 3. API design

### 3.1 Identities, descriptors, traits

```java
record OccupantKind(NamespacedId id) implements Comparable<OccupantKind>
    // BLOCK, BLOCK_ENTITY, ENTITY, NESTED, OTHER (structurecumulus ids); of(id) = host family
record OccupantRef(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)
    implements Comparable<OccupantRef>          // order: snapshot, y, z, x, kind.id, type
    boolean from(SnapshotId id)                 // snapshot.equals(id)
record OccupantDescriptor(NamespacedId type, OccupantKind kind,
                          Map<String, String> properties, List<NamespacedId> tags)
    @Nullable String property(String key)       // null = the host published nothing
record TraitKey<T>(NamespacedId id, Class<T> type)
record Trait<T>(TraitKey<T> key, @Nullable T value)
```

- `anchor` is the owning cell **in the frame that minted the ref**: a child view mints
  child-frame refs, so only `snapshot` compares across frames.
- Descriptor: `properties` by key, `tags` sorted and deduped, copied unmodifiably;
  `property(k) == null` is silence, never `false`. Two adapter versions resolving one logical
  value must produce an equal descriptor.
- `TraitKey` equality is `(id, type)`: a wrong token misses, and one id under two types is
  rejected at construction. A trait value is a `String`, boxed primitive, `NamespacedId`,
  immutable record or unmodifiable collection of those; a live object or `Supplier` breaks I-4.

### 3.2 Occupant snapshot and read status

```java
record OccupantSnapshot(OccupantRef ref, OccupantDescriptor descriptor, Cuboid footprint,
                        List<Trait<?>> traits, @Nullable NestedView nested)
    static NamespacedId AIR                     // structurecumulus:air
    static OccupantSnapshot air(SnapshotId, LatticePoint)
    boolean isAir()                             // descriptor().type().equals(AIR)
    <T> @Nullable Trait<T> find(TraitKey<T>)    // null = not captured
    <T> @Nullable T trait(TraitKey<T>)          // null = not captured or valueless
    boolean has(TraitKey<?>)                    // present; value may be null
enum ReadStatus { READABLE, OUT_OF_BOUNDS, NOT_CAPTURED, DENIED, STALE; boolean readable() }
```

- `footprint()` is one half-open `Cuboid` (I-3: a union of one), the maximal axis-aligned body;
  a non-box group is several occupants.
- `nested() != null` iff the host resolved a child; `NESTED ⇒ nested() != null`, not the
  converse. Construction rejects ref/descriptor disagreement, an empty footprint, a `NESTED`
  without a child.
- `air(...)`: kind `BLOCK` (the id is the sentinel), one cell, no traits; the adapter maps every
  empty world value to it.
- `view.occupant(cell) != null ⇔ the section that holds `cell` is readable — `view.slot(key).readable()`
  for the key of that cell (§7.2). The key is the view's own: an implementation derives it from the
  world and cell size it was captured with, so a consumer never has to build one.

### 3.3 Nested view, world view, policy

```java
interface NestedView extends WorldView
    OccupantSnapshot parent()       // the occupant that exposed this view
    int depth()                     // root view = 0; a direct child = 1
    List<OccupantRef> ancestry()    // outermost first, ending with parent().ref()

interface WorldView
    SnapshotId snapshotId()
    SectionSlot slot(SectionKey key)
    @Nullable OccupantSnapshot occupant(LatticePoint cell)
    @Nullable OccupantSnapshot occupant(OccupantRef ref)
    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter)
    @Nullable NestedView nested(LatticePoint cell)
    long contentDigest(SectionKey key)

sealed interface SectionSlot
    record Air(long revision) implements SectionSlot
    record Present(SectionSnapshot section) implements SectionSlot
    record Refused(ReadStatus status, FindingCode code) implements SectionSlot
    default boolean readable()      // Air or Present

final class SectionSnapshot
    SectionKey key()
    long contentDigest()
    @Nullable OccupantSnapshot occupantAt(LatticePoint cell)   // null only outside the enumerated cells
    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter)
    static SectionSnapshot of(SectionKey key, Map<LatticePoint, OccupantSnapshot> occupants)
    static Builder builder(SectionKey key)                     // put, putAll, build

record OccupantFilter(@Nullable Predicate<NamespacedId> type, @Nullable Predicate<OccupantKind> kind,
                      @Nullable Predicate<NamespacedId> trait)
    static OccupantFilter any()                                // no component constrains
    static ofType(NamespacedId), ofKind(OccupantKind), ofTrait(NamespacedId)
    boolean matches(OccupantSnapshot occupant)

record SnapshotId(World world, long revision) implements Comparable<SnapshotId>
record WorldWindow(Cuboid bounds, int maxElements)
record SnapshotPolicy(CaptureMode capture, Set<OccupantKind> requiredKinds,
                      Set<TraitKey<?>> requiredTraits, int maxElements,
                      int maxNestedDepth, long maxStalenessNanos)
    enum CaptureMode { EXACT, REQUIRED }
    static SnapshotPolicy minimal(int maxElements)   // REQUIRED, depth 0, staleness 0
```

A nested view **is** a full `WorldView`: every question askable at the top is askable one level
down with the same cost class and absence rules (R-01); only the frame changes. A cell no
section of this view covers answers `null` from `occupant`, and its section's slot is
`Refused(OUT_OF_BOUNDS, …)` or `Refused(NOT_CAPTURED, …)`, never "empty". `revision >= 1`,
monotonic per world. Ceiling is `min(window.maxElements(), policy.maxElements)` — a request may
lower a host's promise, never raise it. `requiredTraits` must be answered; a host that cannot
compute one records it absent (⇒ unreadable), never a default.

### 3.4 Capture and resolution SPI

```java
interface WorldCapture
    CaptureResult capture(CaptureRequest request)   // Captured | TooLarge | Unavailable
    long latestRevision()                           // one WorldCapture serves exactly one world
record CaptureRequest(Cuboid bounds, ChunkGrid grid, SnapshotPolicy policy,
                      Set<NamespacedId> trackedTypes, Set<LatticeKey> trackedCells,
                      CaptureLimits limits)
record CaptureLimits(int maxCells, int maxSections, long maxBytes, int maxEntities, int maxPayloads)
sealed interface CaptureResult
    record Captured(WorldView view) implements CaptureResult
    record TooLarge(Cuboid bounds, int elements) implements CaptureResult
    record Unavailable(ReadStatus status, FindingCode code) implements CaptureResult
interface OccupantResolver<R>
    Set<OccupantKind> kinds()                   // dispatch: families this resolver claims
    @Nullable OccupantSnapshot resolve(R raw, ResolveContext context)      // null = decline
interface ResolveContext
    SnapshotId snapshotId()
    LatticePoint anchor()                       // in the frame being captured
    int remainingDepth()
    SnapshotPolicy policy()
```

## 4. Semantics

### 4.1 The five statuses; empty is not unreadable

A status is a **section** answer: `slot(SectionKey)` returns `Air` (read, and nothing in it),
`Present` (one frozen `SectionSnapshot`) or `Refused(status, code)`, and a cell is answered
through `occupant(cell)`, which reads the cell's own section.

| Status | Slot that carries it | Cause |
| --- | --- | --- |
| `READABLE` | `Air` or `Present`; `occupant(cell)` is the indexed occupant, else air | captured at `snapshotId()` |
| `OUT_OF_BOUNDS` | `Refused`; `occupant(cell)` is `null` | outside the captured sections, or a point in another frame |
| `NOT_CAPTURED` | `Refused`; `occupant(cell)` is `null` | in bounds, no data: element cap, depth cut, unsupported region |
| `DENIED` | `Refused`; `occupant(cell)` is `null` | readable but declined: protection, permission, no-load policy |
| `STALE` | `Refused`; `occupant(cell)` is `null` | a value exists only from another revision |

A `Refused` slot cannot carry a readable status: the record rejects it at construction.
Precedence `OUT_OF_BOUNDS > DENIED > STALE > NOT_CAPTURED > READABLE`. Reserved codes (U3/U7
build the finding): `SC-OCC-0101` out of bounds, `0102` not captured, `0103` denied, `0104`
stale, `0201` footprint crosses the window, `0202` depth cut, `0203` cycle.

- **Empty is a fact:** a readable empty cell is an air occupant with a real ref and footprint;
  "no occupant" is never inferred from `null`, nor from an empty list without a status check.
- **Unreadable is not a negative answer:** `null` plus a non-readable status is `UNREADABLE`
  (U3), hence `Undecided` (U7), not `Failed`. A predicate returning mismatch on a stale chunk
  asserts a wall is missing when it never looked; `DENIED` laundered as air is the host bug that
  makes a correct kernel lie.
- Consumers read the slot for the status and the cell for the occupant, because `WorldView` is
  frozen; U3's `ExpectationContext.occupantAt` is where the safe path must become the short path.

### 4.2 One shape, four sources

| Source | `kind` | `type` | `properties` | Traits | `footprint` |
| --- | --- | --- | --- | --- | --- |
| Block state | `BLOCK` | block id | state as `name=value` | rarely | 1 cell |
| Block entity | `BLOCK_ENTITY` | block-entity id | state + body fields | inventory, energy | 1 cell |
| Entity | `ENTITY` | entity id | identity, variant | inventory, owner | bounding box |
| Substructure | `NESTED` | structure id | declared parameters | interior totals | declared extent |

A kernel algorithm may ask only `type()`, `kind()`, `property(..)`, `has(TraitKey)`,
`footprint()` and `nested()`; nothing above privileges a row.

### 4.3 Multi-cell occupants

| Situation | Rule |
| --- | --- |
| Cell inside a footprint | `occupant(cell)` returns that occupant whole: both cells of a 2×1 entity answer one ref, one descriptor. **A covered cell is `READABLE` even if its own read was denied or never attempted**, because the admitted occupant's footprint is the evidence; `DENIED` applies only where no admitted occupant covers the cell. |
| `occupants(box)` | One entry per occupant however many cells fall in the box, in `OccupantRef` order; dedup by `ref`, minted once per occupant. Air is excluded, so a box query is element-bounded, not volume-bounded. |
| Footprint past the captured sections | Allowed, **not clipped** (I-3); admission is by anchor. An anchor outside with a body inside is absent, and its inside cells are `NOT_CAPTURED`, never air. |
| Crossing a bay | U2-invisible: `occupants(bayCuboid)` returns the straddler whole, so `footprint().volume() > bay.volume()` is visible and the expectation decides. |
| Two occupants in one cell | Both exist; `occupant(cell)` narrows by non-air, then smaller volume, then ref order. |

### 4.4 Traits are capture-time only

Computed once by the resolver, stored, never recomputed: no U2 member reaches the world, so "no
live read during a match" is a property of the interface (§7.1). **Missing vs null:** `find ==
null` ⇒ `UNREADABLE`; present with `value() == null` ⇒ a definite fact a predicate may test, and
a host that cannot tell them apart must not publish the trait. **Required traits** come from the
policy (U7 derives them from `ExpectationMeta.requires`); skipping one yields `UNREADABLE`.
**Immutability** is the host's contract (§3.1): a trait's value is fixed for the snapshot's life,
and review plus the adapter's capture test carry that, because a `Class<T>` token cannot prove
deep immutability.

### 4.5 Nested views

| Question | Answer |
| --- | --- |
| Bounds | The child's captured sections are its bounds: a cell outside them answers `null` from `occupant` and its slot is `Refused(OUT_OF_BOUNDS)`; `parent().footprint()` is the parent-frame extent; `Siting`/`LocusMap` (U1) composes them. |
| Snapshot | `snapshotId()` equals the parent's for the whole chain: one capture, one id. |
| `slot` | The same five statuses; `OUT_OF_BOUNDS` is relative to the child's captured sections; `NOT_CAPTURED` also covers the depth cut and the element cap. |
| Recursion | `depth() = parent + 1`; capture refuses a child at `depth() > maxNestedDepth`, leaving `nested() == null` and the interior `NOT_CAPTURED`. |
| Cycles | Cyclic when `(parent().descriptor().type(), parent().ref().anchor())` repeats along `ancestry()`; capture refuses (`nested() == null`, `NOT_CAPTURED`, `SC-OCC-0203`). Fail-closed and heuristic — frames are not shared — so depth is the real guarantee. |
| Exposure | Only via `occupant`/`occupants`/`slot`; there is no "children of this structure" member. |

### 4.6 Capture protocol and host duties

```
host thread: capture(window, policy)
  1. intersect the window with the readable extent; split into capture units
  2. classify each unit readable / denied / unsupported     (no reads yet)
  3. admit elements in canonical order until the element cap
  4. per raw value: first resolver whose kinds() claims it → resolve(raw, ctx)
  5. NESTED → recurse with remainingDepth - 1, same policy
  6. mark cells under a rejected occupant NOT_CAPTURED
  7. mint one ref per occupant, stamp one SnapshotId
```

**Must:** immutability (values fixed before `capture` returns; no world, mutable cache or
clock); coverage truthfulness (one status per cell, `READABLE` only where read, air only where
verified uncovered); revision monotonicity (strictly greater each time, changed whenever a
captured value changes); determinism (canonical order, equal inputs equal outputs).
**May not:** read lazily from a view; mutate the world (only `EditExecutor` writes, U10); turn
`DENIED`, a failed read or an unloaded chunk into air; hand out a mutable collection or live
instance; mix revisions inside one view.

No `Budget` reaches capture: it runs before the attempt, bounded only by `maxElements` and
`maxNestedDepth`, on whatever thread the host chooses (the adapter uses the server thread). A
resolver runs on that same thread, and a child capture gets `remainingDepth() - 1`.

### 4.7 Identity, staleness, reproducibility

| Question | Answer |
| --- | --- |
| View older than the world? | `revision() < capture.latestRevision()`; allowed within `maxStalenessNanos`, still one coherent revision. |
| Cell older than the view? | Must not be: a cell servable only from another revision answers `STALE`, never a blend. A door half at revision 5 and half at 7 is this failure. |
| Ref from another capture? | `ref.from(view.snapshotId())` is false and `occupant(ref.anchor())` returns a non-equal ref: detected, not repaired. |

A `MatchRequest` carries an existing view; the kernel never captures. Once capture returns,
later world changes are unreachable, so `(blueprint, snapshot, siting, stage, policy)` fixes the
result (I-4). A change during a match affects only a later attempt, which sees a different
`SnapshotId` — a different input, not a different answer. A `STALE` re-check captures a new
view; it never refreshes a running one. Ids are never persisted, so persisted state keeps values
(U8), never refs.

### 4.8 Entity windows and change sets

A section-grained read is blind to an entity that moves out of its cell while staying inside the
same section, so a reader that depends on entities declares an `EntityWindow(Cuboid cells,
NamespacedId type)`: the box one entity type may move in. Windows travel in the read closure
(`ReadScope.entities`), next to the cells and the context keys.

- `MAX_ENTITY_SPAN = 3`; a type whose body is wider than three cells on any axis is refused at
  DESIGN (`SC-BAY-0110`), so a window is never blind to part of its occupant. A window is the
  declared box inflated by that span.
- Each tick, per window, the host compares the live `cell → EntityKey` set with the captured one;
  a difference dirties every unit whose read closure names the window. Join and leave mark the
  footprint. This is the outward-move witness `22` §5.4 names.
- `ChangeSet(Set<LatticeKey> cells, Set<SectionKey> sections, Set<LatticeKey> payloads,
  Set<EntityKey> entities, Set<NamespacedId> keys, long revision)` is what changed between two
  revisions: `empty()` is the no-change value, `plus(other)` unions the five sets and keeps the
  later revision, and `isEmpty()` is true only when all five are empty. `keys` is held sorted by
  id so iteration is deterministic.

## 5. Reference sketch

```java
// id("x") = NamespacedId.of("structurecumulus", "x"); addon trait value: immutable, captured, no host handle
record StackCount(NamespacedId item, int count) {}
record ItemInventory(List<StackCount> stacks) { ItemInventory { stacks = List.copyOf(stacks); } }
static final TraitKey<ItemInventory> ITEM_INVENTORY =
        new TraitKey<>(id("item_inventory"), ItemInventory.class);

final class CapturedView implements WorldView {      // built by capture; sections and order precomputed
    private final NamespacedId dimension;
    private final SnapshotId id;
    private final int cellSize;                                 // 16 from the adapter
    private final Map<SectionKey, SectionSlot> slots;           // Air, Present or Refused
    private final List<OccupantSnapshot> elements;              // canonical order, air excluded

    @Override public SnapshotId snapshotId() { return id; }

    @Override public SectionSlot slot(SectionKey key) {
        SectionSlot slot = slots.get(key);
        return slot != null ? slot
                : new SectionSlot.Refused(ReadStatus.OUT_OF_BOUNDS, FindingCode.of(Family.OCC, 101));
    }

    @Override public @Nullable OccupantSnapshot occupant(LatticePoint cell) {
        SectionSlot slot = slots.get(SectionKey.of(dimension, cell, cellSize));
        if (slot == null || slot instanceof SectionSlot.Refused) return null;     // the biconditional
        if (slot instanceof SectionSlot.Present present) {
            OccupantSnapshot found = present.section().occupantAt(cell);
            if (found != null) return found;
        }
        return OccupantSnapshot.air(id, cell);
    }

    @Override public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
        for (OccupantSnapshot e : elements) {
            if (e.ref().equals(ref)) return e;
        }
        return null;
    }

    @Override public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        List<OccupantSnapshot> out = new ArrayList<>();
        for (OccupantSnapshot e : elements) {    // sorted by ref once, in the capture constructor
            if (e.footprint().intersects(box) && readable(e.ref().anchor()) && filter.matches(e)) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }

    @Override public @Nullable NestedView nested(LatticePoint cell) {
        OccupantSnapshot occupant = occupant(cell);
        return occupant == null ? null : occupant.nested();
    }

    @Override public long contentDigest(SectionKey key) {
        return slots.get(key) instanceof SectionSlot.Present present
                ? present.section().contentDigest() : 0L;
    }

    private boolean readable(LatticePoint cell) {
        return slot(SectionKey.of(dimension, cell, cellSize)).readable();
    }
}

static OccupantSnapshot chest(SnapshotId id, LatticePoint cell, List<StackCount> stacks) {
    OccupantRef ref = new OccupantRef(id, NamespacedId.of("example", "reinforced_chest"),
            OccupantKind.BLOCK_ENTITY, cell);
    OccupantDescriptor d = new OccupantDescriptor(ref.type(), ref.kind(),
            Map.of("facing", "north", "waterlogged", "false"), List.of(NamespacedId.of("example", "reinforced")));
    return new OccupantSnapshot(ref, d,
            Cuboid.of(cell.x(), cell.y(), cell.z(), cell.x() + 1, cell.y() + 1, cell.z() + 1),
            List.of(Trait.of(ITEM_INVENTORY, new ItemInventory(stacks))), null);
}

static OccupantSnapshot golem(SnapshotId id, LatticePoint min) {   // 2x1 entity
    OccupantRef ref = new OccupantRef(id, NamespacedId.of("example", "hauling_golem"), OccupantKind.ENTITY, min);
    Cuboid body = Cuboid.of(min.x(), min.y(), min.z(), min.x() + 2, min.y() + 1, min.z() + 1);
    return new OccupantSnapshot(ref, new OccupantDescriptor(ref.type(), ref.kind(),
            Map.of("variant", "iron"), List.of()), body, List.of(), null);
}

static void inspect(WorldView view, Cuboid hallBox) {
    NamespacedId overworld = NamespacedId.of("minecraft", "overworld");

    view.occupant(LatticePoint.of(3, 64, 4));                                    // one ref for both cells
    view.occupants(Cuboid.of(3, 64, 4, 5, 65, 5), OccupantFilter.any());          // the golem, listed once

    SectionSlot denied = view.slot(SectionKey.of(overworld, LatticePoint.of(4, 64, 9), 16));      // Refused(DENIED, …)
    OccupantSnapshot none = view.occupant(LatticePoint.of(4, 64, 9));            // null, never air
    SectionSlot outside = view.slot(SectionKey.of(overworld, LatticePoint.of(-1, 64, 0), 16));    // Refused(OUT_OF_BOUNDS, …)

    for (OccupantSnapshot o : view.occupants(hallBox, OccupantFilter.ofKind(OccupantKind.NESTED))) {
        NestedView inner = o.nested();
        if (inner == null || inner.depth() > 2) continue;
        boolean sameCapture = inner.snapshotId().equals(view.snapshotId());      // true
        OccupantSnapshot core = inner.occupant(LatticePoint.of(0, 1, 0));        // child-frame point
    }
}
```

## 6. Use cases

| Who | Case | What U2 gives them |
| --- | --- | --- |
| Addon | a hatch needing an inventory; a furnace bay wanting a substructure's interior | `has(...)` as the free test, `trait(...)` as the priced one, `nested()` for the interior |
| Addon | a door: two cells, two occupants, one expectation over the bay | `occupants(bayCuboid)` returns both whole; no voxel shape (R-06) |
| Host | `LevelCapture` for `/cumulus validate`; a kernelTest fake view | the two SPIs and a `CapturedView` over a map: one code path for server and test |
| Host | a watch after an entity walked out of its cell | the window diff marks the units whose read closure names the window; the next `check` re-runs only those |
| Host | a build wand preview | deterministic `occupants` order: two previews of one snapshot are identical |

## 7. Proofs

1. **No live reads.** Every view and snapshot member takes and returns values; the only
   world-touching signatures are `capture` and `resolve`, and `kernel.occupant` imports only
   `base` and `space`. *Counterexample:* a host delegating to a live level satisfies the
   interface and breaks I-4 — a host promise, caught by the adapter's test that a view still
   answers after the level closes.
2. **Immutability and the status invariant.** Canonicalising constructors, no setters; `occupant`
   is `slot(SectionKey.of(dimension, cell, cellSize)) instanceof SectionSlot.Refused ? null :
   section.occupantAt(cell)` with air for an unanswered cell of a readable section.
   *Counterexample:* a descriptor handing out its caller's map would change a running match.
3. **Multi-cell consistency.** One ref per occupant, mapped to every covered cell; `occupants`
   filters the element list, so dedup is by identity. *Counterexample:* a ref per cell ⇒ two
   elements, a doubled census, a double-booked claim.
4. **Nested recursion bounded.** `depth = parent + 1`, refused above `maxNestedDepth`, so a
   chain has at most `maxNestedDepth + 1` views. *Counterexample:* a self-resolving structure
   block; at depth 3 the fourth level is `NOT_CAPTURED`, never a stack overflow.
5. **Descriptor version independence.** Four id/string components, no ordinal, state id or
   class name. *Counterexample:* publishing `state=1234` instead of `facing=north` breaks every
   blueprint across a port.
6. **Deterministic iteration.** Traits by key id, properties by key, tags deduped by id,
   occupants by `OccupantRef`. *Counterexample:* returning `HashMap.values()` flaps findings.

## 8. Dependencies and open questions

**Depends on.** U0: `NamespacedId`, `Checks`. U1: `LatticePoint`, `LatticeKey.of/toPoint`,
`Cuboid.of/contains/intersects/volume`, `CuboidSet.contains`, `SectionKey.of`/`sectionOf`,
`ChunkGrid`. **Consumed by.** U3, U4, U7, U8,
U10, U11, A1.

| # | Question | Position |
| --- | --- | --- |
| Q1 | The glossary's ref tuple omits the snapshot stamp. | **Amendment A1:** `OccupantRef(SnapshotId, NamespacedId, OccupantKind, LatticePoint)`; without it `from(...)` and stale detection are impossible. |
| Q2 | The glossary gives `OccupantSnapshot` an "optional payload", but `kernel.occupant` may not depend on `kernel.document.DataView` (03 §3) and an untyped payload breaks I-13. | **Amendment A2:** drop `payload`; a host body is a trait (`TraitKey<HostBody>`, declared by A1). |
| Q3 | A trait read is a coin flip without a capture-time demand list. | **Amendment A3:** add `Set<TraitKey<?>> requiredTraits` to `SnapshotPolicy`, filled by U7. |
| Q4 | `footprint()` is one `Cuboid`; an L-shaped host group is inexpressible. | Deferred: split the group. If a real case appears, amend to `CuboidSet` and re-prove §7.3. |
| Q5 | Who owns the well-known trait holder (`Traits.ITEM_INVENTORY`, 04 §8–§9)? | U3 or U11; `structurecumulus:item_inventory` is reserved here. |
| Q6 | 03 §7 says constants are lowerCamelCase, but its example is `Expectations.AIR` and the glossary writes `BLOCK`. | U2 follows the glossary; 03 §7 should be corrected. |
| Q7 | Per-cell coverage checking makes a large blueprint's BOUNDS check O(volume) status calls. | No member added: the box query is `occupants(box, filter)`. If U7 measures a per-box status hot, propose a `slot`-summary member, not a per-cell `readStatus`. |

## 9. Falsification

| Scenario | What must happen | What would prove U2 wrong |
| --- | --- | --- |
| A chunk holding one bay unloads mid-match. | The match is unaffected: it reads the view captured before. A later capture answers `NOT_CAPTURED`, so a re-check is `Undecided`, never "the wall vanished". | Any member reaching the world: the match would throw, block or return a revision-mixed answer. |
| An entity straddles a bay edge. | `occupants(bay)` returns it whole; the expectation sees `footprint().volume() > bay.volume()` and decides; the other bay sees it too. | A clipped footprint: a straddle would look like a fit, and claims would silently overlap. |
| A host mutates a chest's inventory without bumping the revision. | Two captures differ under an equal `SnapshotId` — a host violation of §4.6, undetectable by the kernel; the adapter's capture test asserts the bump. | If the kernel cached content by id (it does not — refs are values) this would silently corrupt a match. |
| A nested structure contains itself. | Capture stops at `maxNestedDepth` (and at the repeat when seen); the deepest level is `NOT_CAPTURED` with `SC-OCC-0202`/`0203`; at worst `Undecided`. | A stack overflow, or an "empty" deepest level — the latter would turn a cycle into a match. |
| A protected cell (`DENIED`), an element cap, or an anchor outside the window. | `null` plus the status and code; `NOT_CAPTURED` for dropped units and for cells under a rejected occupant; at worst `Undecided`, never a truncated "definite" answer. | Air or a softer status: a missing gate, a dropped occupant, or an occupant's tail reading as empty floor. |
