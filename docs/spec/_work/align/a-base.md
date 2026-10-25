# A — U0 `kernel.base` (code ↔ spec)

> Slice: every file under `src/kernel/java/dev/vfyjxf/cumulus/kernel/base/` (20 files).
> Doc slice: `03-naming.md`, `04-api-doctrine.md`, the `01-glossary.md` rows whose type the
> code puts in `base` (§B, §C, §D, §E, §I, §K), plus the `01` revision list.
> Read-only. Findings types (`Finding`, `FindingBundle`, `FindingCode`, `Severity`, `Label`,
> `Locus`) are H's subject; I judge them only where `03`/`04` make a naming or doctrine claim
> or where their *package placement* contradicts `03` §3.

## 1. Scope (files read)

- Code: `src/kernel/java/dev/vfyjxf/cumulus/kernel/base/*.java` end to end (all 20 files,
  979 lines), plus every `base` import edge found by `grep -rn "^import dev.vfyjxf"` over the
  kernel tree, plus `src/kernelTest/java/dev/vfyjxf/cumulus/kernel/{blueprint,examples}/*` only
  where a `base` member's consumer mattered.
- Spec: `03-naming.md`, `04-api-doctrine.md`, `01-glossary.md` (§A–§P).
- Cross-references read because they are load-bearing (**named as required**):
  `00-overview.md` §4–§5 (unit table, invariants), `02-architecture.md` §1, §2, §4, §5, §9
  (package graph, "no cycle at package level", the cycle-break table), `13-findings.md`
  lines 1–40 (U9's `package dev.vfyjxf.cumulus.kernel.finding` and its §2 vocabulary),
  `19-open-proposals.md` R1, R14, R27, R35, `23-claim-entry.md` §7,
  `10-documents.md:21` (`DataView`/`DataKind` in `kernel.base`), `15-spi.md:13,32` (`Codes`),
  `build.gradle:47-54, 116-121, 216` (source sets, kernel classpath, NullAway option),
  `src/main/java/dev/vfyjxf/cumulus/util/Checks.java:10`.
- No Minecraft type is involved in this slice; `sourcescout` was not needed.

Package inventory, for reference:

| Spec (§3 table) | Code |
| --- | --- |
| `kernel.base` (ids, budget, spend, cancellation, stability, stage, arguments, source spans, data views) | `base`: `NamespacedId`, `Stage`, `SourceSpan` (of the 11 `01` §B rows) **+** the whole U9 finding model **+** `Outcome`/`ReadScope` (§E) **+** `OwnerId` (§D) **+** `InstanceId` (§I) |
| `kernel.finding`, `kernel.document`, `kernel.remedy`, `kernel.expectation` | absent (`expectation` is spelled `kernel.predicate`) |

## 2. Divergences

| id | doc anchor | code anchor | what the doc claims | what the code does | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| A1 | `03` §3 base row "May depend on **nothing**"; `02` §2 "There is no cycle at package level"; `02` §9.1 "A module may import only packages above it in §2, plus `base`" | `base/ReadScope.java:11` `public record ReadScope(CuboidSet cells, Set<ContextKey<?>> keys, List<EntityWindow> entities)` with `:3-5` imports of `context.ContextKey`, `occupant.EntityWindow`, `space.CuboidSet`; `base/Outcome.java:8,10` `sealed interface Outcome<T>` with `Found<T>(T value, List<OccupantRef> subjects, FindingBundle notes)` and `:3` import of `occupant.OccupantRef` | `base` is the dependency sink every other unit may import; the package graph is acyclic; `ReadScope` is deliberately in `base` to break `blueprint` ↔ `context` (`02` §2 break table) | `base` is a *middle* node: it imports `space`, `occupant` and `context`, all three of which import `base` back (`space/SectionKey.java:3` — the only `space`→`base` edge; 9 of 13 `occupant/*`; 12 of 23 `context/*`) → three package-level cycles | **RULE** | The source of both members — `Set<ContextKey<?>>` and `List<EntityWindow>` — is owned above `base` for *callers* of `ReadScope` (`predicate/Focuses.java:93` `ReadScope.none().withKeys(Set.of(key))`), so `02` §2's stated cycle-break cannot be realised with `01` §E's component list; either the components, or the home, must change. |
| A2 | `00` §5 "U9 \| `kernel.finding`"; `03` §3 `kernel.finding` = "finding model, bundle, renderer, explanation"; `13-findings.md:3,65` "Unit **U9** (`dev.vfyjxf.cumulus.kernel.finding`)", `package dev.vfyjxf.cumulus.kernel.finding;` | `base/Finding.java:12`, `FindingBundle.java:13`, `FindingCode.java:6`, `FindingId.java:3`, `Severity.java:3`, `Label.java:5`, `LabelRole.java:3`, `Note.java:6`, `Suggestion.java:6`, `Applicability.java:3` — no `kernel/finding` directory exists (`find src/kernel/java -type d` → `base blueprint claim context engine occupant predicate space spi`) | The finding *value* model is U9's, in `kernel.finding`; `03` §3's base row lists no finding type | Ten public U9 types live in `base`, and `base`'s own content row (`03` §3) reads "ids, budget, spend, cancellation, stability, stage, arguments, source spans, data views" | **DOC** | The spec is right and the code is a one-package shortcut; **no R-record or `23`/`24` §9 migration item covers the move** (R35's touched-file list names `13` only for the `world`/`level` vocabulary) — this is itself the finding the lead must file. |
| A3 | `03` §3 `kernel.claim` "May depend on: base, space"; `02` §2 graph (no `claim`→`finding` edge) | `claim/ClaimVerdict.java`, `claim/InMemoryClaimLedger.java`, `claim/SettleRequest.java` all `import dev.vfyjxf.cumulus.kernel.base.Finding…` | `claim` may depend only on `base` and `space` | The claim model carries findings (`01` §F `ClaimConflict(rows, findings)`, `ClaimPlan.Invalid(FindingBundle findings)`), so once A2 is fixed, `claim` needs a `finding` edge and the graph as drawn forbids it | **RULE** | A2 cannot be repaired without amending `03` §3 (`claim` gains `finding`) and `02` §2 (the edge is acyclic: `finding` depends on base+space only, `13` §1 "Does not own … which check raises which code"); my recommendation is exactly that one-line amendment. |
| A4 | `01` §C `LocusFrame`, `Locus`, `LocusMap` are U1 `kernel.space`; `01` §B `SourceSpan` "Lives in `base` so `space` never depends on `finding`" (R1) | `base/Locus.java:6` `public record Locus(LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan source)` with `:3` `import dev.vfyjxf.cumulus.kernel.space.CuboidSet`; `base/LocusFrame.java:3`; `base/KeyLocus.java:3` | `Locus`/`LocusFrame` are `space`; `base` holds only the span | The whole locus family is in `base` and is the single edge that makes `base` → `space` | **DOC** | Spec right, code lagging, no R-record covers it; moving the three types to `space` removes `base`→`space` (i.e. half of A1's first cycle) with no new edge, because `finding` already may depend on `space`. Caveat: it forces the companion RULE A5. |
| A5 | `01` §D `Owner`/`OwnerId` "`(NamespacedId owner)`: who holds a claim"; `01` §I `InstanceId` "`(World world, long serial)`" | `base/OwnerId.java:3` `public record OwnerId(NamespacedId world, NamespacedId owner)`; `base/InstanceId.java:3` `public record InstanceId(NamespacedId world, long serial)`; consumers `base/KeyLocus.java:3`, `claim/ClaimRow.java`, `engine/MatchRequest.java` | `OwnerId` is U2 (`kernel.occupant`), `InstanceId` is U7 (`kernel.engine`) | Both local ids live in `base` | **RULE** | Neither `01` home is reachable: `03` §3's `claim` row lacks `occupant` although `ClaimRow` carries `OwnerId`, and `Locus ∈ space` (A4) forces `InstanceId ≤ space`; my recommendation is to move both rows to `01` §B — base's charter is "ids", and that also satisfies `03` §3 unchanged. |
| A6 | `01` §B rows `Budget`, `Spend`, `CancelToken`, `Stability`, `Arguments`, `ParameterKey`, `DataView`, `DataKind`; `03` §3 base row "budget, spend, cancellation, stability, … arguments, … data views"; `04` §8 `Budget.of(4_096, 64)` / `Budget.of(1_024, 16)`; `10-documents.md:21`, R14 (`DataView`/`DataKind` in `kernel.base`) | `grep -rl` over `src/` returns ∅ for `CancelToken`, `Stability`, `ParameterKey`, `DataView`, `DataKind`; `Arguments` occurs once, and only inside a comment admitting the deviation (`blueprint/DerivationSpec.java:16` "Deviation: `args` is a `Map<String, Object>` placeholder until the `Arguments` …"); `Budget` is `engine/Budget.java:4` `public record Budget(int cells, int sections)` and `Spend` is `engine/Spend.java:4`, both in `engine`, neither in `base` | These are U0 `base` types, and `04` §8's golden example constructs `Budget` twice | Six of the eleven `01` §B rows (`CancelToken`, `Stability`, `Arguments`, `ParameterKey`, `DataView`, `DataKind`) have no type anywhere in `src/`; two more (`Budget`, `Spend`) exist in `engine` with base-shaped components; `Arguments` is replaced by a `Map<String, Object>` — the exact shape `04` §10 bans | **DOC** | R1 and R14 only fix *where* `SourceSpan`/`DataView` live; **no decision and no `23`/`24` §9 item records that the U0 budget/spend/cancellation/stability/arguments/data-view half was never written**, so a regeneration must be told whether these are in scope now. |
| A7 | `01` §B `Stage` "`DESIGN`, `PROBE`, `FORM`, `SETTLE`"; `00` R-08; `02` §4 stage table; `19` R27 "`Stage.STALE` is deleted"; `23` §7 "`Stage.STALE` \| deleted; a targeted re-check is `check` plus a `ChangeSet`" | `base/Stage.java:8` `STALE` (5th constant); live uses: `blueprint/Blueprint.java:169` `case STALE -> Integer.MAX_VALUE;` and `engine/MatchSession.java:518` `case SETTLE, STALE -> ContextTier.SETTLE;` | Four stages; the engine selects one per check | Five; `STALE` is a real "last stage" ordering value and maps onto `ContextTier.SETTLE` | **DOC** | Covered by R27 + `23` §7, so not a new decision — but the doc gap is in §4 below: after the deletion nothing in `03`/`04` says which stage a finding raised outside a match carries, while `Finding.stage` is mandatory (`Finding.java:253-256`). |
| A8 | `04` §4 "`Checks.checkNotNull` is the only assertion helper"; `03` §5 "`Checks.checkNotNull` is the only sanctioned assertion helper" | `src/main/java/dev/vfyjxf/cumulus/util/Checks.java:10` `public final class Checks` — a **`main`-source-set** class; `build.gradle:117` kernel classpath = `kernelCompileOnly 'org.jspecify:jspecify:1.0.1'` (nothing else), `:116` `implementation sourceSets.kernel.output` (main depends on kernel, not the reverse); the kernel's actual helper is `java.util.Objects.requireNonNull` (22 call sites in 5 `base` files: `Finding` 15, `Label` 3, `Suggestion` 2, `Note` 1, `FindingBundle` 1) | The kernel's assertion helper is `Checks.checkNotNull` | The kernel cannot compile against `Checks`: the class is in `src/main/java`, which is not on the kernel classpath, and no kernel file references it | **BOTH** | The rule must be "one helper, named by the spec, owned by the kernel" — either move `Checks` to `kernel.base` (it is Minecraft-free; `build.gradle:216` already points NullAway at it by name) or delete the sentence and sanction `Objects.requireNonNull`; leaving the sentence as written makes a regenerated kernel fail its own classpath gate. |
| A9 | `04` §9 addon example `Finding.builder(Codes.UNREADABLE_CELL).stage(ctx.stage()).label(ctx.locus(), "…").build()`; `01` §K `Finding` row lists `severity` as a component | `base/Finding.java:250-268` `build()`: `:253` `throw new IllegalArgumentException("severity is required: " + code)`, `:256` "stage is required", `:258-268` message is derived from the first `PRIMARY` label and only throws "message is required when there is no PRIMARY label" | The example is a valid reference sketch (the spec's own error path) | The example never sets `severity`, so `build()` throws at run time; and the label→message fallback that makes the example's `.label(...)`-only style work is documented nowhere | **BOTH** | The rule to write: `severity` is either a required builder call (then `04` §9 must set it) or a defaulted component with a stated source; the code's silent `message` fallback must be specified either way. |
| A10 | `04` §6 "`Blueprint` and `MatchRequest` qualify; `LatticePoint`, `Cuboid`, `Claim` and **`Finding`** do not" | `base/Finding.java:49` `public static Builder builder(FindingCode code)`; `:178` `public static final class Builder`; used by `04` §9 itself | `Finding` gets no builder | `Finding` has a builder, and it is the only way `04` §9 constructs a finding | **CODE** | §6's counter-list contradicts §6's own rule ("only if most components are optional": 5 of `Finding`'s 9 components are optional) and contradicts §9; drop `Finding` from the list. |
| A11 | `04` §9 `Codes.UNREADABLE_CELL`, `Codes.EXPECTED_INVENTORY`; `04` §11.1 "Every type it names is in the glossary or is declared in the same section"; also `12-context.md:230`, `14-remedies.md:341`, `15-spi.md:32` "`Codes` \| final class, constants" | `grep -rl "\\bCodes\\b" --include=*.java src` → ∅; no `Codes` row in `01-glossary.md` (only `Catalogs.codes` as a *field*) | `Codes` is a frozen constants holder of the kernel-shipped codes | No `Codes` type and no code constant exists; `01` §K/§M have no row for it, so `04` §9 names a type the glossary does not define | **CODE** | `15` §32 declares the name but `01` — the closed vocabulary `04` §11.1 appeals to — never gains the row; add the `Codes` row to `01` §K **and** a `03` §4 shape (`*Codes`/`Catalogs` are the only registry shapes there), or rewrite the examples to literal `FindingCode.of(Family, N)`. |
| A12 | `01` §C "`Locus` … a frame + an **optional** `CuboidSet` + an optional `SourceSpan`; a `KEY` locus carries `(InstanceId, NamespacedId)` and no cells" | `base/Locus.java:6` `CuboidSet cells` (no `@Nullable`); `:14` `if (!cells.isEmpty()) throw … "a KEY locus carries no cells"`; `:24` a `DEFINITION` locus **needs** a span | Both `cells` and `source` are optional components | `cells` is mandatory and non-null: the empty set is the encoding for "no cells" | **CODE** | "Optional" must be reworded as "non-null `CuboidSet`, empty when the frame carries none" plus the DEFINITION-needs-span rule (`:24`), or a regenerated `Locus` is built with a null `cells` at every `Locus.definition(...)`. |
| A13 | `01` §C (the pair is described inline, never named); `03` §P "any name that another module must reference is a cross-unit name and is added here, with an owner, before the module file is accepted"; `03` §4 shape table has no row for it | `base/KeyLocus.java:3` `public record KeyLocus(InstanceId instance, NamespacedId key)`; reachable from outside the package via `Locus.java:6`'s `key` component and `:49` `public static Locus key(InstanceId, NamespacedId)` | The KEY locus carries `(InstanceId, NamespacedId)` | A public type named `KeyLocus` carries it, with no glossary row and no `03` §4 shape | **CODE** | `01` §C must name `KeyLocus` (it is what `Locus.key()` returns to a renderer) and `03` §4 must say which shape it is, otherwise the name is re-invented per regeneration. |
| A14 | `03` §2 "If the name is a `java.util.function` interface, a `java.util` collection, a `java.time` value, or a `javax.tools`/`javax.swing`/`java.awt` UI type, the name is banned"; §2's own lead-in "No kernel type may share a simple name with a `net.minecraft.**` type or a **commonly imported** JDK type" | `base/Label.java:5` `public record Label(Locus locus, String message, LabelRole role)` — `java.awt.Label` exists | Every `java.awt` UI type name is banned outright | `base` ships `Label` (and `01` §K blesses it, with R1/R12 depending on it) | **CODE** | The bullet must be narrowed to the lead-in's test ("likely to be imported in the same adapter file"): `java.awt.Label` is never imported by an MC adapter, and the general rule would also ban `Note`-class names arbitrarily. |
| A15 | `04` §2 "Findings compose: a bundle is a **filtered**/ordered set"; `01` §K `FindingBundle` "Immutable, ordered, deduplicated, **capped** set; `primary()`, `isEmpty()`, `filter(...)`"; `01` §K `FindingOptions` `(maxTotal, maxPerCode, includeInfo, includeNotes)` | `base/FindingBundle.java:13` — 100-line class with `empty/of/plus/findings/size/isEmpty/primary/ofCode/ofSeverity`; `:11-12` the source itself states "capping is a caller's `filter(options)`" and "**FindingOptions, Builder, filter and the SC-DIA-0001 truncation finding are not built yet**" | A bundle is filtered and capped, and `FindingOptions` is one of the three configuration objects `04` §2 names | No `filter`, no `FindingOptions`, no cap, no `Builder`; the two published selectors are `ofCode`/`ofSeverity` | **DOC** | Spec right, code lagging by its own admission, and **no R-record or §9 item covers it**; `03`/`04` must also say whether `filter(FindingOptions)` is *the* cap entry point or the caller caps. |

## 3. Rulings needed

**1 — A1: where do `ReadScope` and `Outcome` live, and what may `ReadScope` reference?**
`02` §2 keeps `ReadScope` in `base` to break `blueprint` ↔ `context`, but `01` §E defines it as
"cells, keys and entity windows" and the code's `keys`/`entities` are `context.ContextKey` /
`occupant.EntityWindow` (`ReadScope.java:3-5,11`). Any answer must restore `base`'s
may-depend-on-nothing property or delete it.

- (a) Keep both in `base` and let `base` depend on `space`/`occupant`/`context` (today's code).
  Cost: `03` §3's base row and `02` §2's "There is no cycle at package level" are both false,
  and `02` §9.1's review rule ("only packages above it, plus `base`") loses its meaning.
- (b) Keep `ReadScope` in `base` but express its components in base vocabulary
  (`Set<NamespacedId> keys`, and `EntityWindow` moved down or folded into
  `(Cuboid, NamespacedId)`); move `Outcome` to `predicate`, whose row already allows
  `occupant` and `finding` (`03` §3). Cost: `withKeys(Set.of(key))` call sites
  (`predicate/Focuses.java:93`) and `computation.inputs()` sites convert id↔key at the boundary.
- (c) Move both to the units that own their components (`ReadScope` → `engine`, `Outcome` →
  `predicate`) and re-draw `02` §2, accepting `predicate` → `engine`.

**Recommendation: (b).** It is the only option that keeps `02` §2's stated cycle-break, keeps
`Outcome` in the package whose glossary section already lists it, and confines the change to two
component types plus one class move. Note that `Outcome`'s home is *not* actually stated by
`02` at all.

**2 — A3: does `claim` gain the `finding` edge?**
- (a) `03` §3's `claim` row becomes "base, space, finding"; `02` §2 draws `claim ── finding`.
  Recommendation: **yes** — `finding` depends on base+space only (`13` §1: it does not own
  "which check raises which code"), so the edge is acyclic and it is the minimum change that
  makes A2's move possible.
- (b) `ClaimConflict`/`ClaimPlan.Invalid` shrink to carried codes/a separate result type so the
  claim model needs no `Finding`. Cost: contradicts `01` §F and I-10 ("a conflict is a
  `Finding`").
- (c) Keep the finding model in `base` (A2's code) and rewrite `03` §3's base row to say so,
  accepting that U9 owns no package of its own.

**3 — A5: where do the local id types live?**
- (a) Both to `01` §B (`base`): base's charter is "ids, budget, spend, …"; `03` §3 then holds
  unchanged, and `ClaimRow`'s `OwnerId` and `Locus`'s `InstanceId` both resolve below their
  users. **Recommended.**
- (b) `OwnerId` → `claim` (first consumer), `InstanceId` → `space` (a sibling of `Locus`), and
  `01` §D/§I move those rows. Cost: two id types in two packages, and `engine.CheckKey`/
  `MatchRequest` then read ids from three packages.
- (c) Keep `01`'s homes (occupant/engine) and add `occupant` to `claim`'s row and `engine` to
  `space`'s row. Cost: `engine` is defined as "all of the above", so `space` → `engine` is a
  cycle by construction.

## 4. Documentation gaps that block regeneration

**Undocumented behaviour (code has it; the spec never mentions it — the regenerated kernel
loses it unless it is written down):**

1. `Finding.Builder.build()`'s **`message` fallback**: when no message is set, the first
   `PRIMARY` label's message becomes it, and only "no PRIMARY label and no message" fails
   (`Finding.java:258-268`). This is what makes `04` §9's example style legal.
2. `Finding`'s own canonicalisation: at most one `PRIMARY` label
   (`Finding.java:31-38`, `throw … "at most one PRIMARY label, found " + primaries`), labels
   re-sorted into a canonical order (`:40` `canonical.sort(LABEL_ORDER)`), and
   `related` must not contain `FindingId.NONE` (`:41-42`). None of the three is in `01` §K.
   (Ordering/dedup detail is H's; the *existence* of these validations is U0's.)
3. `Stage` is the one ordering enum in `base` **without** `rank()`: `Severity.rank()`,
   `LabelRole.rank()`, `LocusFrame.rank()` and `Applicability.rank()` all return `ordinal()`
   with a descending-strength convention (`Severity.java:8-13` `atLeast`, whose `rank() <= floor.rank()`
   encodes "ERROR is 0"), while `FindingBundle.compareEntries` sorts by `a.stage().ordinal()`
   (`FindingBundle.java:126`). `03`/`01` fix neither the rank convention nor the bundle order.
4. `FindingCode`'s nested `Family` **counts and range rules**: 15 constants with a `subject()`
   display noun (`FindingCode.java:8-25`), `number` restricted to `1..9999` (`:36-38`) and
   `parse` requiring exactly four digits (`:64`). `03` §7 fixes only the `SC-<FAMILY>-<NNNN>`
   shape and the mnemonic set; `13` §2 documents `Family` but not the numeric range.
   (`13` is H's; flagged here because the type is base-resident.)
5. The verb `parse` is missing from `03` §5's table although two public base members implement
   it with two different policies: `NamespacedId.parse(String)` returns `@Nullable` (`:26`),
   `FindingCode.parse(String)` returns `@Nullable` (`:54`) while `FindingCode.of(String)` throws
   `IllegalArgumentException("not a finding code: " + text)` (`:46-49`) for the same input.
   §5's `of(...)`/`get(...)` rows classify neither.
6. `NamespacedId.DEFAULT_NAMESPACE` (`:8`) is the only carrier of `03` §7's "the default
   namespace is the mod id (`structurecumulus`)"; every one of the 13 call sites writes
   `NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "…")`, so a regenerated API must be told
   whether a one-argument `of(path)` exists or the constant is only for code construction.

**Unimplementable as written (the spec names something with no definition or no carrier):**

- **A11**: `Codes` (`04` §9, `12-context.md:230`, `14-remedies.md:341`) is declared only in
  `15-spi.md:32`; `01` has no row, and `03` §4 has no registry-constant shape, so the spec's own
  §11.1 test ("every type it names is in the glossary") fails on its own example.
- **A8**: `Checks.checkNotNull` (`03` §5, `04` §4/§5, and `build.gradle:216`'s NullAway
  `CastToNonNullMethod`) has no carrier inside `kernel.**`; the only implementation is in the
  adapter source set, which the kernel must not depend on (`build.gradle:116-117`).
- **A6**: `04` §8's golden example calls `Budget.of(4_096, 64)` and `Budget.of(1_024, 16)`, and
  `04` §7 requires `Budget` to be "passed explicitly"; no `Budget` exists in `base` (the
  `engine` one is a different, request-level type). The example cannot compile against a
  regenerated `base` until U0 owns `Budget`.
- **A9** (as an example defect, not a type gap): `04` §9's `build()` chain omits the mandatory
  `severity`.

**Package/unit mismatch (class 3 of the brief):** `kernel.finding`, `kernel.document` and
`kernel.remedy` do not exist under `src/kernel/java`, and `kernel.expectation` is spelled
`kernel.predicate`. Under `00` §5 as written, a regenerated tree has 12 kernel packages, with
`Finding*`/`Severity`/`Label`/`LabelRole`/`Note`/`Suggestion`/`Applicability` in `kernel.finding`,
`Locus`/`LocusFrame`/`KeyLocus` in `kernel.space`, `OwnerId` in `kernel.occupant`,
`InstanceId` in `kernel.engine`, and `base` holding only `NamespacedId`, `Stage`, `SourceSpan`
(+ `ReadScope` per `02` §2). Under the code's choice, `base` is the aggregate sink and 4 of
`00` §5's units (U6, U9, U10, plus U3 under its spec name) have no package at all. That
divergence is A1–A5; it is the largest single regeneration risk in this slice because *every*
other module's imports change with it.

## 5. Checked and aligned

- `Finding`'s nine components, order and nullness match `01` §K character for character
  (`Finding.java:12-14` vs `01` §K `Finding` row) — including `@Nullable Suggestion suggestion`.
- `Label(Locus, String, LabelRole)` matches `01` §K and R1's "a `Label` carries one `Locus`
  (whose `source` is the only span), not a second span" (`Label.java:5`; `Locus.source` is the
  only span component in the file).
- `SourceSpan(NamespacedId document, int start, int end)`, half-open, in `base`, matches `01` §B
  and R1; `length()`/`contains(...)` are additive and harmless (`SourceSpan.java:3,18,22,26`).
- `NamespacedId` satisfies `03` §4 (`record`), §7 (`namespace:path`, "ordered by namespace then
  path" — `compareTo` at `:51` does exactly that), and the `04` §8 call
  `NamespacedId.of("mymod", "huge_furnace")` compiles against the real two-argument factory
  (`NamespacedId.java:22`).
- `FindingCode`'s 15 families are exactly `03` §7's mnemonic list (`SPC, OCC, EXP, FOC, CLM,
  BPT, BAY, CHK, DOC, ENG, CTX, DIA, RMD, SPI, MC`), and every code quoted across
  `01`/`13`/`19` parses (`SC-SPC-0001`, `SC-CLM-0002`, `SC-CLM-0007`, `SC-CTX-0102`,
  `SC-CTX-0109`, `SC-CTX-0202`, `SC-CTX-0301`, `SC-BAY-0107`, `SC-DIA-0001`, `SC-ENG-0003`,
  `SC-ENG-0006`, `SC-MC-0502`).
- `04` §4's nullness doctrine: zero occurrences of `Optional` in `base`; `@Nullable` used for
  absence (`Locus.source`, `Note.locus`, `Suggestion.remedy`, `Finding.primaryLabel`,
  `Finding.primaryLocus`, `FindingBundle.primary`, `NamespacedId.parse`); every
  collection-returning member returns a copy, never `null` (`List.copyOf` in `Finding`,
  `FindingBundle`, `Outcome.Found`, `ReadScope`).
- `04` §4's "every kernel package is `@NullMarked`" holds for `base`
  (`base/package-info.java`).
- `04` §3.3's "no stringly-typed channel": `Severity`, `Stage`, `LabelRole`, `LocusFrame`,
  `Applicability`, `FindingCode.Family` are enums; strings appear only as human messages and
  inside `NamespacedId`, both explicitly allowed by §3.3.
- `03` §4's "Do not add `Impl`, `Default`, `Abstract`, `Base`, `Simple` to a public type": no
  `base` type carries any of them (the `base` *package* name is mandated by `00` §5 / `03` §3,
  so it is not a violation of the type rule).
- `03` §4 `*Id` = "stable logical identity, not a handle": `FindingId(int)` with `NONE` and
  `assigned()` matches `01` §K's "`NONE` means 'not addressable'".
- `04` §6's `withX(...)` rule: `Finding.withId/withRelated/withSuggestion`, `Locus.withSource`
  return new values; the mutable `Finding.Builder` is the sanctioned exception, and `build()`
  validates once.
- `04` §1's "a package-private type is the internal marker" is honoured for members:
  `Finding.mergeDuplicate` (`Finding.java:93`), `Finding.locusOrder` (`:111`) and
  `Finding.firstCell` (`:131`) are package-private, and `FindingBundle.Entry`/`Key`/`LabelKey`
  are private nested records (`FindingBundle.java:166,173,176`).
- `04` §5's "`IllegalArgumentException` … the message names the argument": `NamespacedId`
  `"namespace: " + namespace`, `SourceSpan` `"end " + end + " precedes start " + start`,
  `FindingId` `"value must not be negative"`, `FindingCode` `"number must be within 1..9999"`,
  `Label`/`Note`/`Suggestion`/`Finding` `"… must not be blank"`.
