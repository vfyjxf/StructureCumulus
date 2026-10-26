# P1 round 2 — blind regeneration vs `kernel.{base,space,occupant}`

Method: skeleton written from `01-glossary.md`, `05-space.md`, `06-occupants.md` only, into
`/tmp/cumulus-probe-p1` (deleted afterwards); the tree is frozen in
[`p1-round2-skeleton.md`](p1-round2-skeleton.md). No other spec file and no `src/` file was opened
before the skeleton compiled. `19-open-proposals.md` was read only in phase 3, to separate known
lags (R35, R38) from findings.

Labels, as the protocol defines them: **WRONG-DOC** — the code has something the document got
wrong in a way that would not compile or would not match (missing member, wrong parameter type, a
member that cannot exist as written). **CONTRADICTION** — two documents disagree, or one document
disagrees with itself. **SILENT** — the document never says; I invented it, and say what I chose.
Where a divergence is *code against a document that is internally consistent and not covered by any
ruling*, I file it under WRONG-DOC and say so, because that is what a regenerator hits: a member
the prose promises that has no carrier.

## Type count

| package | types the chapters assign | types in code |
| --- | --- | --- |
| `kernel.base` | 11 rows in `01 §B` (+ `Checks`, named only by `06 §8`) | **20** |
| `kernel.space` | 19 (14 signature rows in `05 §3`, 3 more named in `05 §2`, 2 only in `01 §C`) | **15** |
| `kernel.occupant` | 27 (`01 §D` + `06 §1/§3`) | **13** |
| elsewhere but doc-assigned here | — | `engine.SnapshotId`, `predicate.NestedView` |

Of the 50 kernel types I declared: **13 have no carrier at all** in the repo
(`LocusMap`, `Trait`, `TraitKey`, `World`, `SnapshotPolicy`, `WorldWindow`, `WorldCapture`,
`OccupantResolver`, `ResolveContext`, `CaptureRequest`, `CaptureLimits`, `CaptureResult`, plus
`occupant.NestedView`), **4 live in another package than the chapter puts them**
(`Locus`, `LocusFrame`, `KeyLocus` → `base`; `SnapshotId` → `engine`), and 33 exist under the
documented name — most of them member-for-member, with the member-set divergences listed below.

## Compile result

```
$ cd /tmp/cumulus-probe-p1 && find src -name '*.java' > sources.txt \
    && javac -Xlint:all -d out @sources.txt
$ echo $?
0
```

51 compilation units, **zero diagnostics, first attempt** — and that is itself the finding:
nothing in the prose *contradicted the compiler*. Every invention below came from a signature the
prose left unnamed, never from a compiler telling me the document was wrong. A document set can be
this compile-clean and still put 33 of 50 types out of step with its own code.

Stubs written, and why:

- `org.jspecify.annotations.Nullable` — the chapters say `@Nullable` but never name the package.
  The code also annotates the packages `@NullMarked` (`*/package-info.java`), which no chapter
  mentions; without it every other reference is nullable and the annotations reverse meaning.
- `kernel.base.InstanceId` — `05 §3` needs it for `KeyLocus`/`Locus.key`; `01 §I` assigns it to U7
  (engine). Putting it in `space` breaks `05 §8`, putting it in `engine` breaks it worse; I put it
  in `base`. The code does the same (`base/InstanceId.java`), which suggests the chapters are the
  ones out of date, not the code.
- `kernel.finding.FindingCode` — `06 §3.3` puts `FindingCode` in `SectionSlot.Refused` and
  `CaptureResult.Unavailable`, so `occupant` must name it; `01 §K` puts `FindingCode` in U9
  (`finding`). I stubbed it in `finding`, per `01 §K`. The code has it in `base`, and R38 says
  `base` is deliberate — see C1.

---

## 1. WRONG-DOC, worst first

**W1 — `OccupantRef`: five components and no identity order, where the doc has four and is
`Comparable`.**
Anchor: `06 §3.1` — `record OccupantRef(SnapshotId snapshot, NamespacedId type, OccupantKind kind,
LatticePoint anchor) implements Comparable<OccupantRef>` with `boolean from(SnapshotId)`, order
`snapshot, y, z, x, kind.id, type`; `01 §D` repeats "equality is identity within one capture";
`19 R8` restates it. Code `occupant/OccupantRef.java`:
`record OccupantRef(InstanceId instance, NamespacedId type, OccupantKind kind, LatticePoint anchor,
Cuboid footprint)` — no `Comparable`, no `from`, `InstanceId` instead of `SnapshotId`, and a
footprint component. I wrote the doc's shape. Consequence a regenerator cannot recover from:
`06 §4.7`'s staleness test (`ref.from(view.snapshotId())`), the `06 §4.3` "one entry per occupant,
in `OccupantRef` order" rule, and `19 R36`'s `settle` validation (`OccupantRef.snapshot`) are all
unwritable, and every `Set<OccupantRef>`/`sort` in the kernel would silently become identity-hashed.
Decision I had to make: whether `footprint` belongs on the ref at all — the doc says footprint is a
component of `OccupantSnapshot` and *nothing* else, so I left it off.

**W2 — `OccupantDescriptor` lost `kind`, `properties` and `tags`, and the only accessor
`06 §4.2` permits (`property(..)`) does not exist.**
Anchor: `06 §3.1` — `(type, kind, Map<String,String> properties, List<NamespacedId> tags)`,
`@Nullable String property(String key)`; `06 §4.2`'s table fills `properties` for all four sources
("state as `name=value`", "identity, variant"); `06 §7.5` argues the descriptor's version
independence from those four components. Code: `record OccupantDescriptor(NamespacedId type,
Set<NamespacedId> traits)` plus `of(type)` and `hasTrait`. `kind` moved onto
`OccupantSnapshot`, so descriptor and snapshot now disagree about where the family lives. A
regenerator writes `property("facing")` — the single call `06 §4.2` says an algorithm may make —
and it does not compile.

**W3 — the trait types are gone: `Trait<T>`, `TraitKey<T>`, `find`, `trait`, `has` have no
carrier; `traits` is a set of ids.**
Anchor: `06 §3.1` (`record TraitKey<T>(NamespacedId, Class<T>)`, `record Trait<T>(TraitKey<T>,
@Nullable T)`), `06 §3.2` (`<T> @Nullable Trait<T> find`, `<T> @Nullable T trait`,
`boolean has(TraitKey<?>)`), `06 §4.4` (the whole missing-vs-null ruling), `01 §D` (`Trait`,
`TraitKey`, and `Traits` as the reserved-key holder). Code: `OccupantSnapshot.traits()` is
`Set<NamespacedId>` and `OccupantDescriptor.hasTrait(NamespacedId)` is the only reader; `Traits`
does not exist. `06 §4.4`'s central distinction — present-with-null value is a fact, missing trait
is `UNREADABLE` — cannot be expressed at all, so a regenerator would implement a rule the data
model forbids. This is the same design area as W1/W2: the code has retreated from typed traits to
id sets, and no chapter or ruling says so.

**W4 — `World` is a ghost type, and it is what pulls a cycle into the bottom package.**
Anchor: `01 §D` — `World`: `record World(NamespacedId id)`; used by `05 §3`
(`SectionKey(World world, …)`), `06 §3.3` (`SnapshotId(World world, long revision)`), `06 §3.4`
(`CaptureRequest`), `19 R35` ("a claim row names its `World`"). Code: **no `World` anywhere in
`src/`**; `engine/SnapshotId` is `(NamespacedId world, long revision)` and
`space/SectionKey` is `(NamespacedId dimension, int x, int y, int z)`. I had to decide three times
(`SnapshotId`, `SectionKey`, `KeyLocus`/`InstanceId`) what stands in for `World`, and each time I
picked differently — `World` in `occupant`, `InstanceId` in `base`. A regenerator has no way to
choose consistently, and the two choices produce *different* member types on `SectionKey` and
`SnapshotId`, i.e. a tree that does not compile against the rest of the kernel.

**W5 — `WorldView` does not expose `snapshotId()`, and `nested()`'s type is not `NestedView`.**
Anchor: `06 §3.3` lists `SnapshotId snapshotId()` first in `WorldView`; `06 §4.7` reads
`view.snapshotId()`; `06 §3.2`'s biconditional is stated *in terms of the view's own section keys*.
Code `occupant/WorldView.java`: `slot`, `occupant(LatticePoint)`, `occupant(OccupantRef)`,
`occupants(Cuboid, OccupantFilter)`, `nested(LatticePoint) → @Nullable NestedLevelSnapshot`,
`contentDigest(SectionKey)`. No `snapshotId`, and no way for a consumer to build the
`SectionKey.of(dimension, cell, cellSize)` that `06 §3.2`, `06 §4.1` and `06 §5` all write — the
dimension and cell size are not on the interface. I wrote `snapshotId()`; a regenerator who writes
it produces a view nobody can implement from the code's own `NestedLevelSnapshot`.

**W6 — `NestedView` is not the type `06 §3.3` describes; the name exists in `predicate` for
something else.**
Anchor: `06 §3.3` — `interface NestedView extends WorldView` with `parent()`, `depth()`,
`ancestry()`; `06 §2` "a full `WorldView`, same snapshot, own frame"; `06 §4.5` "every question
askable at the top is askable one level down". Code: `occupant/` has no `NestedView`;
`predicate/NestedView.java` is `record NestedView(NestedLevelSnapshot level, BayView bay)` — a
different concept wearing the banned-name-free name. So the doc's interface is absent *and* the
name is taken, which is worse than absent: `import …occupant.NestedView` fails, and the
"nested view **is** a full `WorldView`" guarantee (R-01's uniformity claim) is simply not true of
`NestedLevelSnapshot`, which has no `slot`, no `occupant(cell)`, no `snapshotId`.

**W7 — `NestedLevelSnapshot` cannot be what `06 §2` says it is: the member list is a strict subset
of `WorldView`.**
Anchor: `06 §2` — "The frozen interior of one nested occupant … `parent()`, `depth()`,
`contentDigest()`, `occupantAt(LatticePoint)`, `occupants(Cuboid, OccupantFilter)`", while `06 §3.3`
and `01 §N` both call it a `NestedView`. Written as a `NestedView` it does not compile (missing
`snapshotId`, `slot`, `occupant(OccupantRef)`, `nested`, `contentDigest(SectionKey)`); written as a
standalone class it contradicts `06 §3.3`. I declared it as an interface extending `NestedView` and
therefore had to *leave out* the four `WorldView` members, i.e. my declaration is not what the doc
says either. The code resolves it by making it a `final class` implementing nothing. This is the
clearest "member that cannot exist as written" in the three chapters.

**W8 — the whole capture SPI of `06 §3.4` is absent, and with it every host entry point of U2.**
Anchor: `06 §1` "In:" lists `SnapshotPolicy`, `WorldWindow`, `WorldCapture`, `OccupantResolver<R>`,
`ResolveContext`; `06 §3.3` gives `SnapshotPolicy` six components with `CaptureMode { EXACT,
REQUIRED }` and `minimal(int)`; `06 §3.4` gives `CaptureRequest`, `CaptureLimits`, sealed
`CaptureResult { Captured | TooLarge | Unavailable }`, `WorldCapture`, `OccupantResolver<R>`,
`ResolveContext`. Code: none of the eight exist (verified by name search across `src/kernel`). A
regenerator writes the file the project's own scope section calls the module's entry point,
`WorldCapture`, and there is no consumer, no implementation, and no test that would notice. I wrote
all eight from the prose; `CaptureLimits` and `CaptureRequest` are component-for-component guesses
because only `06 §3.4`'s sketch names them.

**W9 — `Stage` has a fifth constant in code that no chapter admits.**
Anchor: `01 §A` ("`DESIGN`, `PROBE`, `FORM`, `SETTLE`. The engine selects it"), `01 §B`
(`DESIGN, PROBE, FORM, SETTLE`), also `00 R-08` and `13` §stage order. Code `base/Stage.java`:
`DESIGN, PROBE, FORM, SETTLE, STALE`, and it is live — `engine/MatchSession` switches
`case SETTLE, STALE ->`, `blueprint/Blueprint` has `case STALE`. `01 §B` opens with "The **Type**
column is the Java name" and calls the vocabulary closed; a regenerator writes a four-value enum
that does not compile against the engine. (Not R35/R38.)

**W10 — `OccupantKind.OTHER` has no constant.**
Anchor: `01 §D` — "the constants `BLOCK`, `BLOCK_ENTITY`, `ENTITY`, `NESTED`, `OTHER`; … `OTHER`
carries a host id"; `06 §3.1` repeats "five constants". Code: four (`BLOCK`, `BLOCK_ENTITY`,
`ENTITY`, `NESTED`). The "carries a host id" phrasing is also unimplementable against
`record OccupantKind(NamespacedId id)` — there is one id component total, so `OTHER` can carry a
host id only by being the host id. I wrote five constants and no host-id field; the doc's clause has
no honest reading.

**W11 — `SectionKey`'s first component is `World` in the doc and `NamespacedId dimension` in the
code, and the two orders disagree.**
Anchor: `05 §3` — `record (World world, int x, int y, int z)`, "compares by world, then `x`, `y`,
`z`". Code `space/SectionKey.java`: `(NamespacedId dimension, int x, int y, int z)` and
`compareTo` is dimension → `x` → **`z`** → `y`. A regenerator following the chapter gets a legal,
compiling, *differently ordered* `Comparable`, which changes section iteration order everywhere
(`05 §4.1` insists order is load-bearing; `05 §3` here contradicts its own `LatticePoint` order
`y,z,x` by writing `x,y,z`). One of the two doc statements is wrong and the chapter does not say
which. (Consequence: I could not tell whether `SectionKey` order is `(x,z,y)` by design, like
`LatticePoint`'s `(y,z,x)`, or a typo.)

**W12 — `SectionKey.sectionOf(LatticePoint, int)` cannot exist as written.**
Anchor: `05 §3` lists, in one signature cell, `static of(World, LatticePoint cell, int cellSize)`,
`sectionOf(LatticePoint, int cellSize)` — the second with no world, so it can neither construct the
record nor be static. Code makes it an **instance** method (`sectionOf` delegating to
`of(dimension, …)`). I wrote it `static` to match the table's formatting and had to throw
`UnsupportedOperationException` in a body that cannot produce a `SectionKey`. A regenerator copying
the table gets a method that cannot compile.

**W13 — `Placement.compose`/`inverse` are total in the chapter and nullable in the code.**
Anchor: `05 §3` marks only `apply(...)` `@Nullable` and `05 §4.6` states both laws as equations
(`P.compose(Q) = (o_P ∘ o_Q, o_P(t_Q) + t_P)`, `P.inverse().compose(P) == IDENTITY`). Code:
`@Nullable Placement compose(Placement)` and `@Nullable Placement inverse()` — both can fail at the
lattice edge *because the offset is a lattice point*. Following the doc, a regenerator writes
`p.inverse().compose(p)` as an assertion of equality with `IDENTITY` and gets an NPE at the
boundary; the `05 §5` sketch's `assert tipped.inverse().compose(tipped).equals(Orientation.IDENTITY)`
is about `Orientation` (total), so the asymmetry between the two types is invisible unless you read
both signatures twice.

**W14 — `Locus`, `LocusFrame`, `KeyLocus` live in `base`, not `space`, and `LocusMap` does not
exist.**
Anchor: `05 §3` defines all four under "Package `kernel.space`", and `05 §5`/`§6` use
`LocusMap.of(siting).toWorld(...)` and `map.toDefinition(...)`. Code: `base/Locus.java`,
`base/LocusFrame.java`, `base/KeyLocus.java`; **no `LocusMap` anywhere**. `05 §8`'s "space depends on
`base` only" is narrated as if `Locus` were in `space`. A regenerator writes `space.Locus` and the
whole "frames a diagnostic points into" surface lands in the wrong package, so `base` (which the
chapter calls the bottom) ends up importing `space.CuboidSet` — an inversion `01 §B`'s own rationale
("Lives in `base` so `space` never depends on `finding`") is written to prevent.

---

## 2. CONTRADICTION

**C1 — `FindingCode` is in three places at once: `01 §K` says U9 (`finding`), `06 §3.3` puts it in
`SectionSlot.Refused`/`CaptureResult.Unavailable`, `06 §7.1` promises `occupant` imports only
`base` and `space`.**
Only one reading compiles: `FindingCode` must live in `base`. `19 R38` says exactly that
("`FindingCode` lives in `base`, which `occupant` may name") and `01 §K` was never updated. I put
the stub in `finding` — the glossary's answer — and then had to add an import that `06 §7.1`
forbids. Code: `base/FindingCode.java` (a `record FindingCode(Family family, int number)` with a
nested `Family` enum). Also note `06 §3.4`'s `CaptureResult.Unavailable(ReadStatus, FindingCode)`
makes the same cross-package claim, and `06 §1`'s "Out: `Finding` (U9 — U2 reserves codes only)"
does not help: `FindingCode` *is* U9.

**C2 — `05 §8` says `space` depends on `base` only, and `05 §3` makes it depend on U2 and U7.**
`SectionKey(World world, …)` names `World`, which `01 §D` assigns to `occupant`; `KeyLocus`/`Locus`
name `InstanceId`, which `01 §I` assigns to `engine`. As written the chapter has a package cycle to
`occupant` (which `06 §7.1` says imports `space`) and an edge to `engine`. I resolved it by
importing `occupant.World` from `space` — legal Java, forbidden layering — and by moving
`InstanceId` into `base`. Every choice here is a decision the chapters do not authorise.

**C3 — `06 §4.5` and `06 §6` still talk about `bounds()` and `readStatus`, which `06 §3.3` does not
declare.**
`06 §4.5` row "Bounds | `bounds()` is the child grid in the child frame"; row "readStatus | The same
five"; `06 §6` "Exposure | Only via `occupant`/`occupants`/`readStatus`"; and `06 §8 Q7` says "No
member added; if U7 measures it hot, propose `readStatus(Cuboid)`" — i.e. Q7 says the member does
not exist while §4.5/§6 use it. `06 §3.3`'s `WorldView` has neither. This is residue of the facade
design R38 withdrew; the chapter was rewritten in one place only. Consequence: I could not tell
whether a nested view owes a `bounds()` (if it does, `06 §3.3`'s interface is incomplete; if not,
`06 §4.5`'s `OUT_OF_BOUNDS` ruling has no input).

**C4 — `06 §1` puts `NestedLevelSnapshot` in U2; `01 §N` puts it in the adapter.**
`06 §1` "In:" lists it; `01 §N` (A1) lists "Nested level snapshot | `NestedLevelSnapshot` | Child
`NestedView` over the same frozen bytes as its parent". A type in the kernel cannot also be an
adapter class, and the two rows disagree about its supertype as well (`NestedView` vs nothing).
I put it in `occupant` (the chapter that spends a § on it) and declared it an interface, which
neither document says.

**C5 — `01 §I` and `05 §3` both own `SectionKey`.**
`01 §I` row: "Section key | `SectionKey` | `(World world, int x, int y, int z)`: the 16³ section
index of one world" — a U7 (engine) row. `05 §3` defines the same record inside `space` and `05 §2`
lists it in the space vocabulary. `01 §C` (U1) does *not* list it. So the glossary's own unit table
and the space chapter disagree, and `01 §D`'s `World`-typed form is the one the code abandoned.

**C6 — `01 §D` `OwnerId` "(NamespacedId owner). The world is not part of it; a claim names its world
separately" vs the code's `OwnerId(NamespacedId world, NamespacedId owner)`.**
This one is a *ruling lag*, not a doc defect — `19 R35` says "`OwnerId` loses its world" and the
code still has it — so it is excluded from the count; see §4. I list it here only because the
contradiction a *reader* sees is glossary-vs-source, with no pointer from `01 §D` to R35.

**C7 — `06 §3.2`'s biconditional cannot be evaluated through `06 §3.3`'s interface.**
`06 §3.2`: `view.occupant(cell) != null ⇔ view.slot(SectionKey.of(dimension, cell, cellSize)).readable()`.
The right-hand side needs `dimension` and `cellSize`, which `06 §3.3`'s `WorldView` no longer
exposes (no `snapshotId()`, no dimension accessor). The formula is true of `CapturedView` in the
`06 §5` sketch — which holds both as private fields — and false as a statement about `WorldView`.
I wrote the interface without a dimension accessor and left the biconditional unimplementable.

---

## 3. SILENT — invented, with what I chose

**S1 — the 4th component of `Locus` has no name.** `05 §3` writes
`record (LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan)`; the
source span is unnamed in the header while `05 §6` calls `ctx.locus().source()` and `05 §4.7` says
both conversions "preserve `source`". I named it `source` (the code agrees). This is a one-token
hole in the only record whose component names are load-bearing for four other chapters.

**S2 — `ChunkGrid`/`ChunkKey` have no signature surface anywhere in `05`.**
`01 §C` gives prose only (`chunkOf`, `boundsOf`, `chunkCount`, `chunksOf`), and `05 §2`/`§3` never
mention them, although `06 §3.4` needs `ChunkGrid` in `CaptureRequest` and `06 §8` lists it as a U1
dependency. I invented: `ChunkKey` (record, `(cy,cz,cx)` order — the glossary does say this),
`ChunkGrid.cellSize`/`origin` components (glossary), `chunkOf(LatticePoint) → ChunkKey`,
`boundsOf(ChunkKey) → Cuboid`, `chunkCount() → int` (**wrong**: code is `chunkCount(Cuboid)`),
`chunksOf(Cuboid) → List<ChunkKey>` (right). The code also has `ADAPTER_CELL_SIZE = 16`, which no
chapter names. Of everything in `space`, this pair is where a regenerator is most likely to be
silently wrong rather than fail to compile.

**S3 — `Heading.step()`'s return type.** `05 §3` names `step()` only; `05 §4.4`'s comment gives a
vector. I chose `LatticePoint` (code agrees). Same class of hole as S2 but harmless.

**S4 — `Orientation.id()`'s return type.** `05 §3` names `id()` only. I chose `int` (= ordinal;
code agrees). Given `01 §C`'s "the only naming type in the kernel" is `NamespacedId`, a reader has
real grounds to choose `NamespacedId` here and be wrong.

**S5 — `Axis` has no members at all.** `01 §C` and `05 §2` name three constants and stop. I wrote a
bare enum. The code is also bare, so nothing is lost — but `Heading.axis()` and `05 §4.5`'s "one
sign per world axis" both suggest an axis-indexed accessor (`CuboidSet` internally has one) that no
chapter sanctions.

**S6 — `Placement.apply(...)`'s overload set.** `05 §3` writes `@Nullable apply(...)` with no
parameter. I wrote three overloads (`LatticePoint`, `Cuboid`, `CuboidSet`, the last two invented)
because `05 §4.6`'s "a local delta must be oriented" and `LocusMap` imply set-level application. The
code has only the point overload and puts set mapping in `Orientation`, so two thirds of what I
invented is dead weight.

**S7 — `Siting.toWorld`/`toLocal`'s overload set.** Same anchor, same problem (`@Nullable
toWorld(...)`). I wrote point-only; the code is point-only; `05 §5`'s "one bay, three frames" flow
goes through `LocusMap` instead — whose conversion members are `@Nullable toWorld(Locus)` etc. So
the chapter has two conversion surfaces with one signature spelled out and one not.

**S8 — who parses `ns:path`.** `01 §B` says "Parsed from `ns:path`, validated" and names no member.
I invented `static NamespacedId parse(String)`, non-null. The code is `static @Nullable
NamespacedId parse(String)` — null on a malformed id, no exception. A regenerator choosing the
"constructor throws only for a malformed value" rule of `04 §5` (which `05 §3` cites) will choose
throwing, as I did, and every caller's error path flips.

**S9 — `OccupantKind`'s constant ids.** `06 §3.1` says only "(structurecumulus ids)". I guessed
`structurecumulus:block`, `:block_entity`, `:entity`, `:nested`, `:other`; the code's four match
except `OTHER` (W10). Harmless here, but ids are persistent data elsewhere, so the silence is
expensive.

**S10 — `Trait.of(...)` exists in a sketch and in no signature.** `06 §5` writes
`Trait.of(ITEM_INVENTORY, new ItemInventory(stacks))`; `06 §3.1` declares only the record. I added
`static <T> Trait<T> of(...)` marked `// INVENTED`. In the code the whole type is gone, so this
invention is unreachable either way.

**S11 — `SectionSnapshot.Builder`'s members.** `06 §3.3` gives `put, putAll, build` by name and no
signatures. I invented all three (`(LatticePoint, OccupantSnapshot)`, `(Map<…>)`,
`() → SectionSnapshot`) and the return-`this` style; the code matches on all three, which is luck
rather than information.

**S12 — package-level nullness.** No chapter mentions `@NullMarked`. The code annotates all three
packages. This is the single invention with the widest blast radius: under `@NullMarked` every
unannotated reference is non-null, so `@Nullable` placement is the whole contract, and a
regenerator who omits the package annotation gets the opposite default in every file.

**S13 — base members the chapters never enumerate but the code has.** `SourceSpan.of/length/contains`,
`NamespacedId.text()`, `LocusFrame.rank()`, `Locus.world(CuboidSet, SourceSpan)`,
`OccupantSnapshot.hasTrait` (vs the documented `has`). `01 §B` gives base one line per type, so
these are "missing member" defects in the glossary rather than inventions of mine; I list them here
because I could not have written them from the prose and did not.

**S14 — `Checks`.** `06 §8` opens "**Depends on.** U0: `NamespacedId`, `Checks`." — a U0 type that
`01 §B` does not list, that no chapter defines, and that does not exist in the code either
(`03 §`/`04 §` say `Checks.checkNotNull` is "the only sanctioned assertion helper"). A regenerator
who takes the dependency line seriously writes a base type the glossary never registers, and
`05 §3`'s "a constructor throws only for a malformed value" rule has no helper to use.

---

## 4. Known lags hit (excluded from the counts)

- **R35** — `OwnerId` in the code still carries its world (`base/OwnerId.java`:
  `(NamespacedId world, NamespacedId owner)`), against `01 §D` and R35's "`OwnerId` loses its
  world". Hit while writing base.
- **R38** — `SectionSlot.Refused(ReadStatus, NamespacedId)` in the code, against `06 §3.3` and
  R38's `Refused(ReadStatus, FindingCode)`. Hit immediately, because it decides whether `occupant`
  may name `FindingCode` at all (C1). R38's withdrawn facades are also what left C3's residue
  behind, so the doc's own cleanup is incomplete rather than merely lagging.
- **R36** — not one of the two named, but I hit it: the ruling's `settle` validation reads
  "subjects of **this** capture (`OccupantRef.snapshot`)", and neither the code's `OccupantRef` nor
  the doc's has a `snapshot()` accessor (the doc's component is named `snapshot`, so `.snapshot()`
  exists by record accessor — the *code* is what fails). Worth flagging because W1 and R36 together
  mean the claim layer cannot be built on the current ref.

---

## 5. Delta against round 1, where it matters

Round 1 recorded `Locus`/`LocusFrame`/`LocusMap` as "doc-declared but code-absent" and did not
count them. They are not absent: `Locus`/`LocusFrame`/`KeyLocus` sit in **`base`**, and that is
what makes `base` import `space` and `occupant` (W14) — a layering fact, not a missing file.
Round 1 also saw a `WorldView` with `readStatus`/`bounds`/`summary`/`chunkGrid` in the document;
`06 §3.3` has since been rewritten to the section-slot shape and now matches the code except for
`snapshotId()` and `nested()`'s type (W5) — but `06 §2`, `§4.5` and `§6` were not rewritten with it
(C3, C7). So the R38 rewrite closed the largest gap and opened two holes beside it; the trait and
ref shapes (W1–W3) are untouched in both rounds.
