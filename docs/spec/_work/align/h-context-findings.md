# H — U8 `kernel.context` + U9 findings (code ↔ spec)

## 1. Scope (files read)

Code, read end to end:

- `src/kernel/java/dev/vfyjxf/cumulus/kernel/context/` — all 24 files: `AggregateContext`, `Augment`,
  `AugmentFold`, `AugmentKey`, `CatalogsDraft`, `Computation`, `ComputationContext`, `ContextDraft`,
  `ContextFrame`, `ContextKey`, `ContextKeys`, `ContextStore`, `ContextTier`, `ContextView`,
  `DeriveContext`, `Deriver`, `Merge`, `Merges`, `RuntimeState`, `SectionContext`, `StatePhase`,
  `StructureContext`, `package-info`.
- The finding types in `src/kernel/java/dev/vfyjxf/cumulus/kernel/base/` — `Finding`, `FindingBundle`,
  `FindingCode`, `FindingId`, `Severity`, `Label`, `LabelRole`, `Locus`, `LocusFrame`, `KeyLocus`,
  `Note`, `Suggestion`, `Applicability`, `SourceSpan`, `Stage`, `Outcome`, `ReadScope`.

Docs: `12-context.md` and `13-findings.md` end to end; `22-consolidated-design.md` §6.4, §7, §8, §11.1,
§14.

Cross-references read because a claim is load-bearing here, not by slice:

- `00-overview.md` §4/§5 (invariants I-6, I-14; the unit table) and `01-glossary.md` §K;
- `03-naming.md` §3 (package layout + dependency cells) and §1 (synonym ban);
- `02-architecture.md` §2 (the package graph and its "no cycle at package level" claim);
- `19-open-proposals.md` (R5, R6, R19, R27, R30 and the second-pass note on `Locus.withSource`),
  `23-claim-entry.md` §7, §9;
- `space/LatticePoint.java`, `space/SectionKey.java` (the two comparators the U8/U9 orders rest on);
- `engine/MatchSession.java` lines 55–90, 140–270, 425–445, 500–545 (the only implementer of
  `ComputationContext`, and the engine's SC-CTX-0109/0202 producers);
- tests: `kernelTest/.../context/ContextStoreTest.java`, `base/FindingTest.java`,
  `base/FindingBundleTest.java` (evidence of intent where the spec is silent).

Minecraft/NeoForge was not touched; no `sourcescout` call was needed. Read-only; this file is the only write.

## 2. Divergences (the table)

| id | doc anchor | code anchor | what the doc claims | what the code does | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| H1 | `22` §14 P6 "A section unit cannot read aggregate state \| `SectionContext` has no `read(ContextKey)` (§7.3)"; `22` §7.3; `12` §8 dep row ("a section context has no `read(ContextKey)`; a section computation can only `contribute`"); `12` §9 falsification row (`A SectionContext calling read(key)` → "does not compile: no such member") | `context/ComputationContext.java:5` `public interface ComputationContext extends AggregateContext {`; `context/AggregateContext.java:11` `<T> Outcome<T> read(ContextKey<T> key);`; `Computation.compute(ComputationContext ctx)` is the one parameter for both phases (`context/Computation.java:22`); `engine/MatchSession.java:234` `new SessionComputationContext(context, view(), sectionContext)` and `:536-537` `read → return view.require(key);` | a section unit cannot read aggregate state; only the `SectionContext` view lacks `read` | a SECTION-phase computation is handed a `ComputationContext`, which *is* an `AggregateContext`, so `ctx.read(key)` compiles and returns the value published by the **previous** revision (`view.require` reads the attempt view, not the current draft) | BOTH | P6's mechanism is honoured (`SectionContext` has no `read`) but its claim is false in spec and code alike: the context handed to a section unit is read-capable, and what it reads is a stale generation, not a refusal — the rule that replaces both is "the context a SECTION-phase computation receives exposes no `read`" (see H-R2) |
| H2 | `13` §3 (whole block: `FindingBundle` interface + `Builder extends FindingSink`, `FindingSink`, `FindingOptions`, `FindingRenderer`, `RenderContext`, `SourceText`, `ExplainBook`, `Explanation`) and §4.5–§4.8 | none of those types exists anywhere under `src/kernel/java` or `src/main/java` (grep); `base/FindingBundle.java:12` `// FindingOptions, Builder, filter and the SC-DIA-0001 truncation finding are not built yet.`; `:13` `public final class FindingBundle {` with `of(List)`, `of(Finding...)`, `plus(Finding)`, `plus(FindingBundle)` | a value is emitted through a sink, ordered/deduped/capped only by `build()`, then projected by one of three pure renderers, with an `ExplainBook` per code | only the value half exists: dedup + total order, no sink, no options, no counters (`elided`/`filtered`), no truncation finding, no cap (so §4.5 steps 5–8 and P3 are unimplemented), no renderer, no book | DOC | the spec is the design of record and this is a lagging implementation — but **no R-record and no `23` §9 item covers it**; the code's own comment is the only trace |
| H3 | `00` §5 "| U9 \| `kernel.finding` \|"; `01` §K "## K. U9 — findings (`kernel.finding`)"; `03` §3 "| `kernel.base` \| ids, budget, spend, cancellation, stability, stage, arguments, source spans, data views \| nothing |" and "| `kernel.finding` \| finding model, bundle, renderer, explanation \| base, space |"; `13` header and `13` §3 `package dev.vfyjxf.cumulus.kernel.finding;`; `02` §2 (edge set, "There is no cycle at package level") | `base/Finding.java:1` `package dev.vfyjxf.cumulus.kernel.base;` (same for `FindingBundle`, `FindingCode`, `FindingId`, `Severity`, `Label`, `LabelRole`, `Note`, `Suggestion`, `Applicability`, `Locus`, `LocusFrame`, `KeyLocus`); `kernel/finding` does not exist; `base/Finding.java:3-5` imports `kernel.space.Cuboid/CuboidSet/LatticePoint`, `base/Locus.java:3` imports `CuboidSet`, `base/Outcome.java:3` imports `kernel.occupant.OccupantRef`, `base/ReadScope.java:3` imports `kernel.context.ContextKey` | the finding model is U9's own package, above `space`; `base` depends on nothing | the whole finding model plus `Locus`/`LocusFrame`/`KeyLocus` (U1's, `13` §8) sit in `base`, and `base` imports `space`, `occupant` and `context` — `base → context` and `space → base` are package cycles | DOC | the spec states the package four independent times; the code's tree is a packaging defect, not an intended `base` role, because no placement of the finding types alone removes `base`'s upward imports (see H-R1) |
| H4 | `12` §3 `Set<ContextKey<?>> keys(); // ascending (id, type name)` (the only statement about `keys()`; `22` §8.3 declares none) | `context/StructureContext.java:74-84` `getAt` (newest frame at or below the tier that publishes the key; the `ABSENT` marker → `null`) vs `:87-97` `keysAt` (unions every frame at or below the tier, skipping only `ContextFrame.isAbsent`); test `ContextStoreTest.java:54` `refusedSectionDefeatsTheFoldAndShadowsAnOlderValue` | nothing — the population of `keys()` is never defined | after a refusal shadows an earlier value, `get(key)` is `null` and `has(key)` is false while `keys()` still contains the key | BOTH | the code contradicts itself and the spec is silent: `keys()` must be exactly the set `has()` answers true for at that tier |
| H5 | `12` §3 "Tier stamping \| a reader at tier `t` sees values stamped at or below `t`"; `22` §8.3 "A reader at tier `t` sees the newest frame at or below `t`" | `context/ContextFrame.java:16,31,34` `private static final Object ABSENT = new Object();` with package-private `absent()`/`isAbsent()`; `ContextDraft.buildFrame` writes the marker for every decided key with no value; `StructureContext.getAt:82` `return ContextFrame.isAbsent(value) ? null : key.type().cast(value);`; `ContextFrame` javadoc "so this frame shadows an older value instead of letting the reader fall back to a stale one" | a frame holds the values the stage published | a publication also records *absence*, as an internal sentinel that shadows an older frame's value, so `get`→`null`, `require`→`SC-CTX-0202` | CODE | the code is right (12 §4.3's "any refused section makes the key absent" is unreachable across publications without it, and `ContextStoreTest.java:54` tests it) but the marker is nowhere in `12`/`22`, so a regenerated kernel falls back to the stale value |
| H6 | `12` §3 `ContextDraft` block (`tier`, `baseRevision`, `put`, `contribute`) and §4.3 "A section computation calls `draft.contribute(key, partial)` once per section in its region"; `22` §7.2, §8.3 `interface ContextDraft { put; contribute; }` | `context/ContextDraft.java:60-68` `contribute` requires a section scope (`if (section == null) throw new IllegalStateException("contribute needs a section; call forSection(...) first")`), `:75` `forSection(SectionKey)`, `:85` `refuse(ContextKey)`, `:94` `declined(ContextKey)`, `:135` `read`, `:142` `publishes`, `:148` `keys` | the draft has four members; a section computation contributes directly | `contribute` is unusable without `forSection`, the barriers' `SectionKey` ordering comes from that scope, and `refuse`/`declined` are the only producers of §4.3's "refused section" and of `whenAbsent` | CODE | the section scope is load-bearing (the fold order is `SectionKey` order) and the spec never mentions it, so the documented `contribute`+fold cannot be written from the spec; six public members are undocumented (class-1) |
| H7 | `13` §4.3 `SC-CTX-0109 \| ERROR \| ✓ \| the wrong verb for a key … the write is refused` (`✓` = a `PRIMARY` label is required) and `12` §3 verb row | `context/ContextDraft.java:53,64,191,198` throw `IllegalStateException("SC-CTX-0109: …")` (an API object with no sink) and `engine/MatchSession.java:208-214` returns `failed(WRONG_VERB, …)` where `WRONG_VERB = FindingCode.of(Family.CTX, 109)` and `failed(:426-433)` builds `.message(...)` with **no label** | one code, one channel: an ERROR finding with a `PRIMARY` label | the same code is raised twice, as an `IllegalStateException` (whose message carries the code text) and as a label-less `Failed` finding | BOTH | `SC-CTX-0109` has a `KEY` locus in reach (`computation.output()`), so 13's `P ✓` is satisfiable, but only the engine can build a finding — the draft's exception must either drop the code or become the sole producer |
| H8 | `13` §4.3 `SC-CTX-0301 \| WARNING \| ✓`; `12` §4.4 "a non-finite *total* from finite inputs is omitted with `SC-CTX-0301`"; `13` §2 (a `KEY` locus carries "`(InstanceId, NamespacedId)` — the instance and the `ContextKey` id") | `context/ContextView.java:61-64` `nonFiniteTotal(AugmentKey key)` → `Finding.builder(NON_FINITE_TOTAL).severity(WARNING).message(...).stage(Stage.SETTLE)` — no label | the finding carries a `PRIMARY` label | the finding is label-less, and the subject of the fact is an `AugmentKey`, whose id is not a `ContextKey` id, so no `KEY` locus exists for it | CODE | 13 marks the code `P ✓` while the only reachable locus is illegal; either the `P` column flips to `–` (with the key named in a `Note`) or augment keys gain a locus shape (see H-R4) |
| H9 | `13` §4.3 `SC-CTX-0201 \| WARNING \| ✓ \| a computation declined to produce its key (U8 owns the `SC-CTX` block)`; `12` §4.1 `whenAbsent` "honoured only when every section in the region was readable" | `context/ContextDraft.java:94-96` `declined(ContextKey)` records the decision and emits nothing; `ContextDraft.valueOf:174` `return state.declined.contains(key) ? key.whenAbsent() : null;`; grep finds no `SC-CTX-0201` producer anywhere in `src/kernel/java` | a declined key is a WARNING finding with a `PRIMARY` label | the decline is silent: the key becomes `whenAbsent` or absent, with no finding | DOC | a specified, labelled code has no producer (class-1/2 at once); the `P ✓` needs the same `KEY`-locus decision as H8 |
| H10 | `12` §5 sketch (`Computation` with `output`/`phase`/`region`/`inputs`/`compute` — no `stage()`); `22` §6.4 `interface Computation<T>` (same, "`phase` lives on the computation"); `00` I-6 "no author declares a stage and no caller chooses one"; `23` §7 "stages are no longer declared by anyone" | `context/Computation.java:14` `Stage stage();`, used as the stage selector: `blueprint/Blueprint.java:100` `if (computation.stage() != stage) continue;` and `:153` `Map<ContextKey<?>, Stage> produced` | a computation declares no stage; the engine owns the pipeline | an author declares a stage on every computation and `Blueprint.expand(stage)` filters by it | DOC | a real lag, but **no R-record names `Computation.stage()`** and `23` §9 item 6 covers only `MatchRequest`/`Stage` — and the spec assigns the computation no other stage source, so deleting `stage()` is not yet writable (see H-R6) |
| H11 | `13` §4.5 total order key 2 "stage \| `Stage` declaration order `DESIGN, PROBE, FORM, SETTLE`"; `12` §4.1 stage/tier table; `19` R27 "`Stage.STALE` is deleted"; `23` §7/§150 "`Stage.STALE` \| deleted" | `base/Stage.java:1-9` `DESIGN, PROBE, FORM, SETTLE, STALE`; `engine/MatchSession.java:518` `case SETTLE, STALE -> ContextTier.SETTLE;`; `blueprint/Blueprint.java:169` `case STALE -> Integer.MAX_VALUE;` | four stages; a finding's `stage` is one of them | five constants; an `STALE` finding sorts after every `SETTLE` finding and is mapped to the `SETTLE` tier | DOC | known lag covered by R27 — recorded here only because §4.5's order key is a *declaration-order* comparison, so a regenerated comparator over a four-case enum is not the code's |
| H12 | `12` §4.2 "A single-writer key (`merge == null`) is written once with `put` … a second write in one attempt is `SC-CTX-0107`" and §9 `Two units put one key in one draft \| §4.2 \| SC-CTX-0107, no frame written` | `context/ContextDraft.java:213-217` `putIfAbsent` + `if (existing != null && !existing.equals(value)) conflicts.add(key);`; test `ContextStoreTest.java:96` `twoDifferentValuesInOneFrameAreRefusedButAnIdenticalDuplicateIsNot` | any second write for one key in one frame is a conflict | a second write of an **equal** value is accepted (the frame is written); only a *different* value conflicts | CODE | `13` §4.3's own wording ("two *different* values for one key in one frame") is right and the code implements it; `12` §4.2's sentence and its falsification row must change |
| H13 | `13` §4.2 span/frame rule "`locus.frame() == DEFINITION` ⟺ `locus.source() != null`"; §4.4 table (`LOCAL`/`WORLD`/`KEY` span column: "never"); `13` §9 F9 (a `WORLD` locus with a non-null `source` must raise) versus §4.4's own sentence "`Locus.source` is the *refined* highlight" | `base/Locus.java:37,45` `public static Locus local(CuboidSet cells, SourceSpan source)` / `world(CuboidSet cells, SourceSpan source)`, `:53` `withSource(SourceSpan)`; the constructor only guards the other direction (`:20` `frame == DEFINITION && source == null` → IAE, `:15-19` KEY → no cells/key non-null/no span) | only a `DEFINITION` locus carries a span | a `LOCAL`/`WORLD` locus can carry a span through three public paths, and `Finding.build()` accepts it | BOTH | 13 contradicts itself (the iff vs "refined highlight"), the code implements a third rule, and the comparator/renderer both assume "span ⇒ DEFINITION" (`base/Finding.java:120-124`) |
| H14 | `12` §3 `ContextView at(ContextTier tier); // narrow to tier, same frames`; §4.2 "`StructureContext.at(t)` narrows the instance context to `t`" | `context/StructureContext.java:133-136` `public ContextView at(ContextTier narrower) { ContextTier clamped = narrower.ordinal() > tier.ordinal() ? tier : narrower; … }` | `at` narrows | a *widening* request is silently clamped to the current tier, so `form.at(SETTLE)` returns the `FORM` view | CODE | the clamp is deliberate (P8 relies on it) but unstated: `12` §3/§4.2 must say `at` never widens, or a regenerated kernel lets a `FORM` reader see the `SETTLE` frame |
| H15 | `03` §1 "A concept has exactly one noun in the kernel. Synonyms are defects"; `12` §3 catalog row (declarations bound with `ContextKeys.register(draft, key)`); `13` §4.3 `SC-CTX-0102` (`P –`) | `context/ContextKeys.java:22-25` `register(CatalogsDraft draft, ContextKey<T> key) { return draft.registerKey(key); }`; `context/CatalogsDraft.java:12,16` two members `registerKey` and `putKey`, where `registerKey` merely calls `putKey`, and `MapDraft.putKey` throws `IllegalStateException("SC-CTX-0102: …")` | one registration funnel, `ContextKeys.register` | two public synonyms for one operation, one of them with no caller, and the refusal channel is an exception whose message carries the code | DOC | `putKey` is the synonym 03 §1 bans; separately, whether a second registration throws (and which exception) or produces `SC-CTX-0102` as a finding is unspecified — 13 §4.2's boundary paragraph covers only `of(...)`/`build()` |

## 3. Rulings needed

**H-R1 — `base`'s boundary, and therefore where the finding model lives.** The spec says the finding
model is `kernel.finding` (H3) and that `base` depends on nothing, but `02` §2 places `ReadScope` *in*
`base` while `ReadScope.java:3` needs `context.ContextKey`, and `Outcome` (in `base`) needs
`occupant.OccupantRef`. So even after moving U9 out, `base` still imports upward.

- (A) `base` holds only `NamespacedId`, `Stage`, `SourceSpan`, `Stability`, budgets, spend,
  cancellation and `DataView`; `Locus`/`LocusFrame`/`KeyLocus` move to `kernel.space` (`13` §8 says
  `KeyLocus` is U1's); `Outcome` moves to `kernel.engine` or `kernel.finding`; `ReadScope` moves to
  `kernel.context`. Regeneration then produces an acyclic graph matching 03 §3/02 §2 — but `02` §2's
  cycle-break row ("`ReadScope` in `base`") must be rewritten, because that row is what the code obeys.
- (B) Keep the code's tree and rewrite 03 §3's `kernel.base` row (`contents` gains the finding model,
  `may depend on` becomes `space, occupant, context`), delete the `kernel.finding` row, and accept that
  `base` is no longer the bottom of the graph — which makes 02 §2's "no cycle at package level" false
  (`base ↔ context`).

Recommendation: (A). It is the only option under which the spec's own dependency gate
(`_index.md` "Dependency gate — a module imports only packages above it in `02-architecture.md` §2")
can be checked at all; (B) requires deleting a gate.

**H-R2 — what a SECTION-phase computation context may expose.** P6's claim is false today (H1) and the
fix changes a U7/U8 interface.

- (A) Split by `phase()`: `SectionComputationContext` (section, cells, occupants, no `read`) and
  `AggregateComputationContext extends AggregateContext`; `Computation.compute` keeps one parameter
  only if the engine downcasts, so in practice `Computation` grows a second type parameter or the
  context is passed as `AggregateContext` plus a `section()` accessor that is non-null only for a
  section unit. Restores P6 and 12 §9's compile-failure test.
- (B) Keep one interface and make `read` on a section-scoped context return
  `Missing(SC-CTX-… )` before the barrier — P6's *test* ("does not compile") must then become a runtime
  test.
- (C) Keep the interface and weaken P6/12 §8 to "`SectionContext` has no `read`" — which says nothing
  about the unit, i.e. the proof is retired rather than repaired.

Recommendation: (A); if the interface must stay single, (B) with 12 §9's row rewritten from a compile
test to a runtime test. Independently, `12` §5's `IronSection` sketch calls `ctx.cells()`
(`12-context.md:209`), a member neither `ComputationContext` nor `AggregateContext` declares — the
sketch must become `ctx.section().cells()`.

**H-R3 — who owns `SC-CTX-0109`.** (A) The engine's plan validation is the only producer (it has the
instance, the key and the stage, so it can carry a `KEY` primary); the draft's verb check becomes a
programmer precondition that throws `IllegalArgumentException` with no code in the message (matching
03 §5 and 13 §4.2's extension guard, which converts an IAE into `SC-SPI-0001`). (B) The draft gains a
sink and returns an outcome. (C) Keep both channels and downgrade 13's `P ✓` to `–`. Recommendation:
(A); (C) leaves a code whose producer cannot satisfy its own contract.

**H-R4 — the shape of a label-less fold finding.** `SC-CTX-0301`'s subject is an `AugmentKey` and
`SC-CTX-0201`'s is a `ContextKey` that was declined. (A) Flip both `P` columns to `–` in 13 §4.3 and
state that the augment key is named in a `Note` or in the message (the ledger/budget precedent, 13
§4.4). (B) Give augment keys a `KEY`-shaped locus by widening `KeyLocus` to a `NamespacedId` subject
without a `ContextKey` claim — which weakens 13 §2's "the `ContextKey` id". (C) Add a
`LabelRole`/frame case for augment facts. Recommendation: (A) for `SC-CTX-0301` and `SC-CTX-0201`
together, since 13 §4.4 already has a label-less shape for findings whose subject has no cell.

**H-R5 — the `Locus` span rule.** (A) Keep the iff (13 §4.2 + F9): delete `Locus.local(cells, source)`
and `Locus.world(cells, source)`, and make `withSource` refuse every frame but `DEFINITION`;
13 §4.4's "`Locus.source` is the refined highlight" sentence is deleted. (B) Drop the iff: any frame
may carry a span, F9's `WORLD`+`source` case is deleted, and `Finding.locusOrder`/the label comparator
must key on the span before the cells (so the "span ⇒ DEFINITION" assumption in the renderer goes too).
Recommendation: (A) — it is the rule §4.2 already argues for, and it is what the code's *comparator*
implements (H13).

**H-R6 — how a computation learns its stage once `Computation.stage()` is deleted** (`23` §7's premise,
H10). (A) The stage lives on the declaring unit: a bay's `Requirement` (or the whole-plan
`Blueprint.expand(stage)`) names the stage, mirroring `CheckPlan(stage, …)`. (B) The stage is derived
from the key: a computation's stage is the tier its output is first required at — the mechanism
`Deriver.tier()` already uses (`context/Deriver.java:12`), with `ContextKey` gaining a tier or the plan
resolving it from the readers. (C) Keep `Computation.stage()` and soften I-6 to "the caller does not
choose a stage". Recommendation: (B) if U8 owns the key's tier (it already owns `Retention`, which is
the tier-placement fact); otherwise (A). The spec must pick one before `23` §9 item 6 can be extended
with `Computation`.

## 4. Documentation gaps that block regeneration

Undocumented behaviour the code relies on (spec must gain it or the regenerated code loses it):

1. **Section-scoped contributions** — `ContextDraft.forSection(SectionKey)` and the `contribute`
   precondition (H6); without it the `SectionKey` fold order of 12 §4.3/22 §7.2 is unimplementable.
2. **The absence marker in a frame** — `ContextFrame.ABSENT` and the shadowing read (H5); 12 §3/§4.2
   and 22 §8.3 never mention that a publication can record "no value here".
3. **`refuse`/`declined`** — the only producers of §4.3's "any refused section makes the key absent"
   and of `whenAbsent`; 12 asserts both behaviours with no member.
4. **`ContextDraft.read`/`publishes`/`keys`** — the barrier's read-back surface, unlisted in 12 §3.
5. **`ContextKey.contributed()`**, **`Finding.primaryLocus()`** (used by `FindingBundle`'s order key),
   **`Finding.mergeDuplicate`**, **`ContextFrame.publishes/isEmpty/size`**, **`ContextStore.instance()`/
   `revision()`** and **`StructureContext.empty`**-adjacent accessors: public or package-visible members
   the spec never names.
6. **`CatalogsDraft`** — a new exported type in `kernel.context` with two members (`registerKey`,
   `putKey`), undocumented in 12/22 beyond `ContextKeys.register` (H15).
7. **`DeriveContext`** — declared in 22 §6.4 and implemented here, but 12 §3 omits both `Deriver` and
   its context, and 03 §4/18 C-20 call the type `DerivationContext` (H-note): the spec names it twice,
   differently. Code follows 22 §6.4; 03 §4 and 18 C-20 should change.
8. **Family subjects** — `FindingCode.Family.subject()` exists with 15 single-word strings
   (`SPC("space")` … `MC("adapter")`); 13 §3 shows three of them and 13 §4.8 calls the value "the
   family's `subject()` sentence", so twelve values and the shape (word vs sentence) are unspecified.
9. **`12` §4.4's Identity column is dead** — the code starts each augment fold at the first contribution
   (`RuntimeState.java:34-40`), exactly as §4.3 requires ("there is no identity"), and P6 says
   "documented identities"; the column either goes or §4.3 changes. Related: 12 §4.1 calls the augment
   group order "(id, type name)" while §4.4 and the code use "(id, fold ordinal)".
10. **`12` §4.3's "(x, z, y)"** is a description of `SectionKey.compareTo`, whose real key is
    `(dimension, x, z, y)` (`space/SectionKey.java:28-39`); a regeneration must say "sorted by
    `SectionKey.compareTo`", not "by `(x, z, y)`".
11. **Delete this rule pair:** `12` §3's "No raw `Map` … a frame's map is never handed out" versus its
    own `public record ContextFrame(ContextTier tier, long revision, Map<ContextKey<?>, Object> values)`
    — a record hands the map out, and the code's map can contain the private `ABSENT` sentinel, which no
    reader outside the package can decode. Either the record keeps the map and the ruling is rewritten
    (with `publishes`/`valueAt` as the only read path), or the map goes behind an accessor.

Unimplementable as stated:

1. **H1/H-R2** — P6's claim and 12 §9's compile-failure test cannot hold while
   `ComputationContext extends AggregateContext`; one of the two must change.
2. **`12` §5's sketches** call `ctx.cells()` (`:209`) on a `ComputationContext` that declares only
   `section()`.
3. **H8/H9** — `SC-CTX-0301` and `SC-CTX-0201` are marked `P ✓` while no legal locus exists for their
   subjects.

Package/unit mismatch (summary): the spec claims `kernel.finding` for U9 (00 §5, 01 §K, 03 §3, 13
header + §3) and `kernel.space` for `Locus`/`LocusFrame`/`KeyLocus` (03 §3, 13 §2/§8). The code has
`kernel.finding` **absent** and those fifteen types in `kernel.base`. A regenerated tree under the
spec would be `kernel/{base, space, finding, context, …}` with `base` at the bottom; under the code it
is today's tree with a `base` that imports `space`, `occupant` and `context` (two package cycles) —
i.e. the code's arrangement cannot be regenerated from 03 §3 or checked by the review gates as they
stand.

## 5. Checked and aligned

- **`Finding`'s nine components**, in the declared order: `id, code, severity, message, labels, notes,
  suggestion, related, stage` — `13` §2/§3 and `01` §K agree with `base/Finding.java:9-11`, and R5's
  `FindingId id` is present. Validation matches §4.2's local rules: non-null components, non-blank
  message, at most one `PRIMARY`, no `FindingId.NONE` in `related`, label messages non-blank, labels
  sorted by the canonical comparator, message defaulting from the `PRIMARY` label in `Builder.build()`.
- **`FindingCode`**: fifteen families in exactly 13 §4.3's order `SPC OCC EXP FOC CLM BPT BAY CHK DOC
  ENG CTX DIA RMD SPI MC`, `compareTo` = family ordinal then number, `text()` = `SC-%s-%04d`, `parse`
  rejects the four F7 strings and `of(Family.EXP, 0)` raises IAE.
- **Bundle order** (`13` §4.5): `base/FindingBundle.java:130-152` = severity rank → stage ordinal →
  code → primary locus `(frameRank, a, b, c)` with label-less `(4,0,0,0)`, `DEFINITION` `(0, start, end,
  0)`, `KEY` `(3,0,0,0)`, `LOCAL`/`WORLD` `(1|2, y, z, x)` → insertion; dedup precedes the sort, keeps
  the first-inserted and merges `related` (sorted) and the higher `Applicability` rung — exactly §4.5
  steps 3–4 and its survivor rule. `LatticePoint.compareTo` is `(y, z, x)`, matching "the canonical
  first cell"; `LocusFrame.rank()` is `DEFINITION 0, LOCAL 1, WORLD 2, KEY 3`.
- **Retention/tier**: `ATTEMPT, OUTCOME, CONTEXT` ✓; `ContextTier` = `BLUEPRINT, PROBE, FORM, SETTLE`
  ✓; tier stamping is tested (`ContextStoreTest.readsAreTierStamped`: a `FORM` reader cannot see a
  `SETTLE` value) and `StructureContext.tier()` is `SETTLE` as `12` §3 declares.
- **`ContextKey`**: components `(id, type, retention, merge, whenAbsent)` ✓; equality and hash are
  `(id, type)` ✓; `compareTo` is `(id, type.getName())` ✓; `whenAbsent` is only reachable through
  `declined` (i.e. "every section readable and nothing contributed", 22 §6.4's wording) ✓.
- **`Merge`/`Merges`**: `combine(T,T)`; `sum/union/min/max/and/or` are exactly 22 §6.7's merge list.
- **Augments**: `AugmentFold` = `SUM, MAX, MIN, MULTIPLY, SET` in that order with the code's own
  `combine`; `AugmentKey.compareTo` = `(id, fold ordinal)` ✓; `RuntimeState.of` stable-sorts and folds
  left to right, omits a non-finite total and records `SC-CTX-0301` ✓; a non-finite `amount` raises at
  construction ✓; `Augment`/`AugmentKey.of`/`RuntimeState.of`/`upgrade` signatures match `12` §3.
- **Publication**: `publish` is `synchronized`, refuses a stale `baseRevision` with
  `Missing(SC-CTX-0108)` and writes nothing, refuses a conflicting frame with `Missing(SC-CTX-0107)`
  and writes nothing, appends one frame at `revision + 1`, and never mutates an older
  `StructureContext` (tested). `require` on a missing key is `Missing` with `SC-CTX-0202` and a `KEY`
  locus. `ContextKeys.of` validates the token and `whenAbsent`'s type.
- **`Deriver`**: `output/requires/tier/derive(@Nullable T)` matches 22 §6.4 verbatim, including the
  context-only contract and the nullable result.
- **`ContextFindings.stage(tier)`** maps `BLUEPRINT→DESIGN, PROBE→PROBE, FORM→FORM, SETTLE→SETTLE`,
  matching `12` §4.1's stage/tier table (with `STALE` folded into `SETTLE` — see H11).
- **Naming**: `Severity.atLeast` (`rank() <= floor.rank()`), `Applicability` (4 rungs,
  `MACHINE_APPLICABLE` first), `SourceSpan.of/length/contains` (half-open), `FindingId.NONE/assigned`
  match 13's §3 sketches.
- Not re-reported as new: `MatchRequest` still carries `Stage`, `OwnerId`/`ClaimRow` world questions and
  the single-call facade (`R27`, `R32`, `R35`, `R36`, `R37`, `23` §9) — out of this slice, seen only in
  passing.
