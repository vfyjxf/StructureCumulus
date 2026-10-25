# F — U5 `kernel.blueprint` + U6 `kernel.document` (code ↔ spec)

## 1. Scope (files read)

Code, all of `src/kernel/java/dev/vfyjxf/cumulus/kernel/blueprint/`:
`AnchorSpec.java`, `Bay.java`, `BayBuilder.java`, `Blueprint.java`, `BlueprintBuilder.java`,
`CheckPlan.java`, `Coverage.java`, `DerivationSpec.java`, `Requirement.java`,
`package-info.java`.

`src/kernelTest/java/dev/vfyjxf/cumulus/kernel/blueprint/BlueprintTest.java` (the only test of this
package).

Cross-references read because they are load-bearing for the questions asked or because the divergence
is on the U5/U6 boundary: `kernel/claim/ClaimMode.java`, `ClaimScope.java`,
`kernel/engine/ClaimPlanner.java` (the only consumer of `Bay.coverage()/mode()/scope()`),
`kernel/predicate/Check.java`, `Focus.java`, `DecisionKind.java`, `kernel/context/Computation.java`,
`StatePhase.java`, `kernel/base/Stage.java`, `FindingCode.java`, `kernel/spi/StructureKernel.java`
(R2's document→blueprint path), `kernel/spi/Catalogs.java`, and `docs/spec/_work/align/g-engine.md`
(the adjacent slice; one open U5 fact it names is answered in §5 below).

Spec: `09-blueprints.md`, `10-documents.md` and `21-authoring.md` end to end; `00-overview.md` §3–§5,
`01-glossary.md` rows for `Bay`/`Coverage`/`Footprint`/`Sharing`/`DerivationSpec`/`Check`/`Stage plan`/
`DecisionKind`/`Arguments`, `13-findings.md` §"shipped allocation", `19-open-proposals.md`
R3/R4/R10/R13/R26/R27/R30/R32/R35, `22-consolidated-design.md` §9/§12/§16.1,
`23-claim-entry.md` §6–§9, `24-claim-vocabulary.md` §5/§7/§8/§9, `02-architecture.md` §2 line 52,
`04-api-doctrine.md` line 150–163, `08-claims.md` §4.3.

Minecraft/NeoForge: not needed. Every fact in this slice is kernel-internal (no `net.minecraft` type
appears in U5's contract, and U6's frontends are named but not defined), so SourceScout was not
invoked and no external source was consulted.

## 2. Divergences

| id | doc anchor | code anchor | what the doc claims | what the code does | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| F1 | `00` §5 U6 row "`kernel.document` — How does a blueprint become data and back?"; `10` §3 "`package dev.vfyjxf.cumulus.kernel.document;`" + ~25 named types; `10` §2 `DataView`/`DataKind` "in `kernel.base`"; `10` §1 "The kernel ships no parser" but "the adapter supplies JSON"; `21` §2.1 `data/<namespace>/cumulus/structure/<path>.json` via `DatapackBlueprints`; `00` I-12 | no package `dev.vfyjxf.cumulus.kernel.document` anywhere; `base/` has no `DataView`/`DataKind`; a grep for the eleven names `DataView`, `BlueprintDocument`, `DocumentSchema`, `SegmentLibrary`, `CompactWriter`, `Canonicalizer`, `DataBridge`, `JsonFrontend`, `SchematicImport`, `CaptureTool`, `DocumentProfile` over `src/` returns **zero** files; `src/main/java` is `Constants`, `StructureCumulus`, `data.lang.LangEntry`, `util.Checks` (no `mc` package); `src/main/resources` holds one `accesstransformer.cfg` and no `data/*/cumulus/structure/` | U6 exists with a value model, two JSON profiles, a validator, a library, an assembler, an emitter, a host bridge and a datapack loader; `DataView` lives in `base` so U3 can read factory args; `StructureKernel.compile(BlueprintDocument)` (R2) is the only document→blueprint path | U6 is **absent**, not absorbed: `blueprint` imports only `base`/`claim`/`predicate`/`space`/`context` and `Blueprint` has no serialisation or provenance member; the adapter that `10` §4.2 says supplies the frontend does not exist; the facade has no `compile` (only `check`/`settle`/`derive` over a `MatchRequest`) | DOC | the whole unit is unimplemented and **no R-record or §9 item covers it** (R2/R3/R4/R14/Q-D1…Q-D8 are document-internal decisions, not a migration entry) — the largest regeneration gap in this slice. |
| F2 | `09` §3 `Check<T>(NamespacedId id, NamespacedId bay, …)`; `09` §4.6 B-11, §4.8 step 4 "every `Check` of `S`", §9 F-8; `23` §7 table "`Check` \| `(id, bay, focus, expectation, description)`, no `stage`", "document JSON \| no `"stage"` field"; `10` §4.4 retired `SC-DOC-0604` "the document carries no `stage` field"; `00` I-6; `22` §16.1 item 16 | `predicate/Check.java:14` `public record Check<T>(NamespacedId id, Stage stage, NamespacedId bay, …)`; `context/Computation.java:25` `Stage stage();`; `Blueprint.java:95` `for (DerivationSpec derivation : derivations) { if (derivation.stage() == stage)`; `:105` `if (computation.stage() != stage)`; `:121` `if (check.stage() == stage)` | no unit declares a stage (`I-6`: "no author declares a stage and no caller chooses one") and the document has no `stage` field, yet `expand(S)` must select "every `Check` of `S`" and B-11 compares a check's read against keys produced "at a later stage" | the stage is a constructor argument of every unit and every unit is selected by comparing it | RULE | both sides cannot hold at once: either units carry a stage (the code) or `expand(S)`/B-11 have no input (`23` §7) — `22` §16.1 item 16 states this is open; options in §3. |
| F3 | `09` §2/§3 `public record DerivationSpec(NamespacedId deriver, Arguments args, NamespacedId output, int order)`; `01` §144 `(deriver, args, order)`; `02` §52 `DerivationSpec(NamespacedId deriver, Arguments, int order)`; `09` §4.6 B-6 "`order` unique and non-negative"; `09` §4.8 "every `DerivationSpec` assigned to `S`"; `04` line 163 `.derive(NamespacedId.of("mymod","interior_volume"), Arguments.empty())` | `blueprint/DerivationSpec.java:19-21` `public record DerivationSpec(NamespacedId deriver, @Nullable Map<String, Object> args, NamespacedId output, Stage stage, int order)`; `:40` `int byStage = Integer.compare(stage.ordinal(), other.stage.ordinal())`; `BlueprintBuilder.java:107` `"B-6: derivation order " + derivation.order() + " is used twice in " + derivation.stage()` | `DerivationSpec` is `(deriver, args, output, order)` — no stage; `order` is unique across the blueprint | the record has a `stage` component and `order` uniqueness is per `(stage, order)`; `args` is a `Map<String,Object>` placeholder instead of `Arguments`, and §4.8's own tie rule (declaration order) is replaced by `compareTo`'s stage-major order | BOTH | the spec's shape cannot be stage-selected by `expand(S)` (F2), and the code silently changed B-6's uniqueness domain and the args type; the rule is `DerivationSpec(deriver, Arguments args, NamespacedId output, Stage stage, int order)` with `(stage, order)` unique — pending F2's ruling on where the stage comes from. |
| F4 | `10` §4.4 table (`SC-DOC-0101` UnsupportedVersion, `0104` UnknownField, `0105` WrongKind, `0106` MissingField, …) and its preamble "the column below is the proposal U9 adopts"; `13` §"shipped allocation" (`SC-DOC-0001` version outside range, `0002` "a required field is absent or of the wrong data kind", `0003` assembly names a missing segment — and nothing else in the `DOC` family); `21` §2/§3.1/§10 (`SC-DOC-0001` for the version, `SC-DOC-0002` for a min/max box, for a `phase`/`merge` mismatch and for an aggregate with no `inputs`), `21` §3.2 `SC-DOC-0605`/`0606`, `21` §4 `SC-DOC-0503`/`0504`/`0505` | `base/FindingCode.java` (family `DOC` exists; `parse` accepts any `SC-DOC-####`); no document code has a producer anywhere in `src/` | one registry, owned by U9, gives every document condition exactly one code | three disjoint allocations: `13` owns `0001–0003`, `10` proposes `0101–0606`, `21` uses both blocks and gives `SC-DOC-0002` three different meanings | BOTH | a regenerated U6 emits codes the registry does not hold (`0104`) while `13` registers codes no unit file uses, and the version condition has two codes (`0001`/`0101`) — one table must replace all three statements. |
| F5 | `21` §5 "`bounds` \| required; never inferred from boxes, and never zero-size", `21` §10 "A compact document with `shell` but no `bounds` is rejected; it never infers a zero-size box"; vs `10` §4.1 compact table "`bounds` \| `bounds: [ox, oy, oz, sx, sy, sz]`, or inferred from the explicit boxes (§4.7)", `10` §4.7 "An inferred `bounds` that would be empty is the same code" | no code (`10`/`21` are unimplemented) | the compact profile's `bounds` | — | BOTH | the two profiles' rules are mutually exclusive and decide whether `read(COMPACT)` can satisfy B-1 at all ("`bounds` non-empty; every bay … inside it"); rule: `bounds` is required in both profiles, and `10` §4.1/§4.7 lose the inference clause. |
| F6 | `09` §3 `public record Bay(…, Requirement requirement, @Nullable String description) { public static Bay of(…, Function<Bay, Requirement> units); }` ("the check/bay knot, one pass — it hands the author a bay value to hang checks on"), `09` §3 prose "its `requirement` is non-empty — a bay that asserts nothing is a seam"; `09` §5 sketch `new Check<>(id("floor_bricks"), b, Foci.block(…), Expectations.block(mc("bricks")), 25, null)`; `09` §3 `public record Check<T>(NamespacedId id, NamespacedId bay, …)`; `09` §3 `BlueprintBuilder.check(NamespacedId bay, NamespacedId id, Focus<T>, Expectation<T>, @Nullable String description)` | `blueprint/BayBuilder.java:38` `public static BayBuilder start(…); :46 public NamespacedId bayId(); :50 public Bay attach(Requirement requirement)`; `blueprint/Requirement.java:19-21` `if (checks.isEmpty() && computations.isEmpty()) throw new IllegalArgumentException("SC-CHK-0107: a bay that asserts nothing is a seam, not a bay")` | the knot is closed by one static factory that hands the author a `Bay` value | the knot is closed by a two-step builder that hands the author a `NamespacedId` (`bayId()`) and takes the finished `Requirement` in `attach(...)` | CODE | `Bay.of`'s lambda argument cannot be built: a `Bay` needs a `Requirement`, and the only `Requirement` available before the lambda returns is the empty one the same spec refuses; and the §5 sketch passes a `Bay` where the declared `Check` component is a `NamespacedId` — the code's `BayBuilder` is the only writable form. |
| F7 | `24` §5 R1 "`EXCLUSIVE` requires a footprint other than `None`", §3 "legality became a whitelist: twelve rows, checked one by one. The whitelist is the symptom", §7 "`Bay` takes `footprint` + `sharing` instead of `mode` + `scope`", §9 items 1–3 "`Coverage`: rename values, rewrite `accepts` into R1/R4 … `Bay`/`BayBuilder`: components and validation"; `09` §4.1 R1/R4 table, §4.6 B-9/B-10, §9 F-1; `23` §8 row "`EXCLUSIVE` with a `None` footprint \| accepted by code; zero rows \| answered by `24` R1: refused at build"; `22` §16.1 item 17 | `blueprint/Coverage.java:11-19` `/** The legal coverage × scope rows; any other pair is SC-CHK-0106. */` … `case CUBIC -> true; case SUBJECT -> !(scope instanceof ClaimScope.Owned); case PRESENCE -> scope instanceof ClaimScope.Audit or ClaimScope.Declared;`; `Bay.java:32-40` `if (!coverage.accepts(scope)) throw … ; if (coverage == Coverage.PRESENCE && mode == ClaimMode.EXCLUSIVE && !(scope instanceof ClaimScope.Declared)) throw new IllegalArgumentException("SC-CHK-0106: PRESENCE bay " + id + " cannot be EXCLUSIVE without a Declared scope")` | `Bay` carries `Coverage` + `Sharing` + `Footprint`; build refuses `EXCLUSIVE` + `None` (R1) and `EVERY_CELL` short of full cell coverage, and forbids per-cell checks under `ANY_SUBJECT` (R4) | `Bay` carries `Coverage` + `ClaimMode` + `ClaimScope` and `accepts` accepts **any** scope for `CUBIC` and any non-`Owned` scope for `SUBJECT`, so `Coverage.CUBIC` + `ClaimMode.EXCLUSIVE` + `ClaimScope.Audit` (R1's contradiction, owning nothing) is accepted; R1 is enforced only inside the `PRESENCE` branch, together with an extra rule the spec leaves open | DOC | `24` §9 items 1–3 are the migration and `23` §8 records the R1 hole as known; the extra `PRESENCE ⇒ Declared` clause is not R1 (it also forbids `Owned`/`Occupied`) and resolves `22` §16.1 item 17 by accident — see §4. |
| F8 | `09` §4.6 B-10 "a `EVERY_CELL` bay's static decision cells cover `bay.cells` (`SC-CHK-0106`)" and B-9/R4 (`SC-CHK-0106`); `13` `SC-BAY-0107` row "`ONE_SUBJECT` without subject-kind checks, or a bound footprint that does not equal the bay (rule R3)"; `19` R30 "`SC-BAY-0107` is R3 only"; `09` §7.2 "every `throw` in `build()` carries its row" | `Bay.java:56-59` `throw new IllegalArgumentException("SC-BAY-0107: " + check.id() + " decides cells outside bay " + id)`; `Bay.java:72-75` `throw new IllegalArgumentException("SC-BAY-0107: CUBIC bay " + id + " leaves " + … + " undecided")` | the coverage shortfall is `SC-CHK-0106` (R4/B-10) and `SC-BAY-0107` means R3 only | both the "a check decides cells outside its bay" refusal and the `CUBIC` coverage shortfall are reported as `SC-BAY-0107` | DOC | R30 assigns `SC-BAY-0107` to R3 alone and `SC-CHK-0106` to R4/B-10 (`09` §9 F-7 asserts exactly this); the outside-cells condition additionally has **no row at all** in §4.6 or R1–R4 (§4). |
| F9 | `09` §3 `public List<Overlap> overlaps();` and `public record Overlap(NamespacedId low, NamespacedId high) implements Comparable<Overlap>`; `09` §8 Q5-2 "**Resolved**: `Blueprint.overlaps()` has a glossary row; the declaration lives on the blueprint, not on a claim spec"; `09` §4.2 | `BlueprintBuilder.java:23-24` javadoc "Overlap declarations are checked at `build()` but not retained: the mandated `Blueprint` shape carries no overlap list, so the licence pair cannot be re-read from the value"; `:32` `private final List<OverlapDecl> overlaps`, `:48-66` `OverlapDecl`/`overlap(low, high)`/`BayPair` are private, `Blueprint.java` has no `overlaps()` | the accepted licence pair is part of the `Blueprint` value and re-readable | the declarations are discarded after `build()`; the only public trace is the `overlap(...)` builder call | DOC | the code's stated reason ("the mandated `Blueprint` shape carries no overlap list") is false against `09` §3/Q5-2, and `10` §4.5 step 3 needs the pair re-readable to leave the cells "to shared claims at match time". |
| F10 | `09` §9 F-9 "two computations in one stage producing the same key \| `build()` refuses it (B-6)"; `09` §4.6 B-6 "derivation `output` keys unique"; `09` §7.2 "(⇐) every `throw` in `build()` carries its row and every row is in §4.6"; `13` `SC-CTX-0102` "two registrations of one id; the second is refused at registration"; `13` `SC-CHK-0102` "the computation/derivation DAG has a cycle" | `Blueprint.java:180` `throw new IllegalArgumentException("SC-CTX-0102: " + node.outputId() + " has two producers in " + stage)` (inside `resolve`, called by `expand`); `:152-157` `producedAt.putIfAbsent(computation.output(), computation.stage())` → `throw … "SC-CHK-0109: " + computation.output().id() + " is produced at both " + previous + " and " + …`; `BlueprintBuilder.java:101-107` checks only **derivation** outputs and orders | duplicate producers are refused by `build()` (B-6) and `expand` throws only for B-5/B-11/cycle | `build()` refuses duplicate *derivation* outputs only; two *computations* producing one key in one stage are refused by `expand` under `SC-CTX-0102` (a code `13` gives to key re-registration), and one key produced at two different stages is refused by an undocumented `SC-CHK-0109` rule | BOTH | F-9 contradicts `09` §4.6/§7.2, and the code's chosen code collides with `13`'s meaning; rule: `build()` owns B-6 for derivations, `expand` owns duplicate computation producers under a `CHK` DAG code, and the cross-stage-producer rule is written down or deleted. |
| F11 | `09` §4.4 "`bay.id` and `seam.id` are re-homed through `Segment.qualify(alias, local) = NamespacedId.of(alias.namespace(), alias.path() + "/" + local.path())`" and §5 kiln comment "// bays mymod:south/floor, mymod:south/hollow, mymod:north/floor, …"; vs `10` §4.1 "A compiled bay id is global (`as + ":" + localName`)", §4.5 step 4 "rewrite ids to `as + ":" + localName`", §4.7 "`main:<localName>`", Q-D3 "**Resolved**: a compiled bay id is `<placement>:<localName>`"; `21` §3 `"bay": "main:mouth"` | no code (`place`/`Segment`/`qualify` absent) | the id of a placed bay | — | BOTH | the two schemes produce different strings for the same placement (`mymod:south/floor` vs `south:floor`), and the id is the claim/`Locus` identity (`01` `Locus.KEY`), so a regenerator cannot pick one without silently invalidating the other document's check references. |
| F12 | `10` §3 `public interface DocumentUpgrade { int from(); int to(); @Nullable DataView upgrade(DataView root, List<ValidationIssue> issues); static DocumentUpgrade v1ToV2(); }`; vs `21` §2 `public interface DocumentUpgrade { DataView v1ToV2(DataView root, List<ValidationIssue> issues); static DocumentUpgrade defaults(); }` and `21` §7 (same body again) | no code | one `DocumentUpgrade` shape | — | BOTH | same type, two signatures and two factory names (`v1ToV2` as instance method vs as static factory), and only `10`'s is nullable/step-shaped (`from`/`to`); rule: `10` §3 is the interface and `21` calls `DocumentUpgrade.v1ToV2().upgrade(root, issues)`. |
| F13 | `19` R3 "`DocumentValidator.of(library, expectations)` and `SegmentAssembler.of(library, expectations)`; `SegmentLibrary.Builder.put(SegmentDocument, Map<NamespacedId, ExpectationFactory>, issues)` and `SegmentAssembler.compile(SegmentDocument, issues)` own the declaration→`Segment` step"; vs `10` §3 `DocumentValidator.of(SegmentLibrary)`, `SegmentAssembler.of(SegmentLibrary)`, `Builder put(SegmentDocument document, List<ValidationIssue> issues)`, `SegmentLibrary apply(SpecResolver resolver, …)`, and §4.6/Q-D6/Q-D7 ("the `SpecResolver` interface is declared here … so `document` names no catalog type") | no code | the library's declaration→`Segment` step and the validator/assembler factories | — | BOTH | R3 (which lists `10` as its file) was never marked superseded while `10` §3/§4.6 and `22` §12.2 ("`SpecResolver` runs during `compile` (DESIGN)") state a different, newer shape; rule: `10` §3 + Q-D6 stand and `19` R3 is struck as superseded. |
| F14 | `09` §3 `public record Check<T>(NamespacedId id, NamespacedId bay, Focus<T> focus, Expectation<T> expectation, @Nullable String description)` and `BlueprintBuilder.check(NamespacedId bay, NamespacedId id, Focus<T>, Expectation<T>, @Nullable String description)` (five parameters); `09` §4.8 "`specificity` remains a finding-order hint"; `01` §101 `ExpectationMeta` carries `specificity`; `09` §5 sketches `.check(id("door"), id("door_planks"), Foci.block(...), Expectations.block(mc("oak_planks")), 8, null)` (six) and `new Check<>(id("floor_bricks"), b, …, Expectations.block(mc("bricks")), 25, null)` (six, with a `Bay` in slot 2) | `predicate/Check.java:14` `public record Check<T>(NamespacedId id, Stage stage, NamespacedId bay, Focus<T> focus, Expectation<T> expectation, @Nullable String description)`; `blueprint/BlueprintBuilder.java:44-47` `public BlueprintBuilder bay(Bay bay)` (no `check`, no `compute`, no `place`) | one shape for `Check` and one builder signature | the record's fifth slot is the stage, the sixth the description; the builder takes a finished `Bay` | CODE | the spec's §3 record, §3 builder signature and its own §5 sketches disagree three ways (five args vs six, `Bay` vs `NamespacedId`, an int the record lacks, `specificity` which `01` §101 puts on `ExpectationMeta`) — the int must go from the sketches and the fifth slot is F2's open question. |
| F15 | `09` §4.8 step 3 "every `Computation` of `S` whose `phase()` is `StatePhase.AGGREGATE`, and every `DerivationSpec` assigned to `S`, in Kahn order over `inputs()`/`requires()` … ties within 1, 3 and 4: declaration order"; `09` §4.8 "U8 sorts `(order, id)` inside the plan"; `19` R10 "`DerivationSpec.order` is unique and non-negative, not dense; U8 sorts `(order, cost, id)`" | `Blueprint.java:95-96` `stageDerivations.sort(Comparator.comparingInt(DerivationSpec::order).thenComparing(DerivationSpec::deriver))` then `graph.add(Node.ofDerivation(derivation, declaration++))` | the plan's tie key is declaration order and `order`-based sorting is U8's job | `expand` sorts stage derivations by `(order, deriver)` before assigning declaration indices, so the tie key is `order`, not declaration order | DOC | no `19` R-record moves sorting into U5 and the key is neither §4.8's (declaration order) nor R10's (`(order, cost, id)`, a cost U5 cannot know); the sort belongs in U8 or §4.8 must state it. |

## 3. Rulings needed

1. **F2 — what carries a unit's stage, and does a unit declare it?** (`22` §16.1 item 16; the code puts
   `Stage` on `Check`, `Computation` and `DerivationSpec`; `23` §7 says the opposite and `10`'s grammar
   has no `stage` field)
   - (a) **No declared stage anywhere** (`23` §7 as written): delete `stage()` from `Check`/
     `Computation`/`DerivationSpec`, and rewrite `09` §4.8 step 4, §4.6 B-11, §9 F-8 and `13`'s
     `SC-CHK-0109` row. Cheapest for `I-6`, but then nothing selects "every `Check` of `S`" and the
     "reads a key produced at a later stage" check has no input — B-11 becomes unwritable.
   - (b) **The unit declares it** (what the code does): restore the `Stage` component, give the document
     a `"stage"` field (un-retiring `SC-DOC-0604`), and amend `I-6`/`23` §7 to say the *author* pins the
     stage while the engine still chooses the pipeline. Narrowest change; contradicts `I-6` as written.
   - (c) **The catalog declares it**: keep units stage-free, and let the resolved factory supply it —
     `FocusFactory`/`ComputationFactory`/deriver catalog entries gain a `Stage` (or a `StatePhase` that
     implies one), so `SpecResolver` stamps the resolved `Check`/`Computation` and `expand(S)` still has
     a key. Preserves `I-6` and `23` §7 verbatim, gives B-11 a computable input, and needs one row per
     catalog family.
   Recommendation: **(c)** — it is the only option that keeps both `I-6` ("no author declares a stage")
   and a working `expand(S)`/B-11, and the catalog is already the place `kind` is resolved (`10` §4.6).
   If a narrower change is wanted, (b) with `I-6` amended.
2. **F1 side question — does `DataView`/`DataKind` belong in `base` (as `10` §2, `19` R14 and `01` say)
   or in `document`?** There is no code either way. (a) `base` (spec as written) keeps U3 able to read
   factory arguments without an `expectation → document` edge; (b) `document` makes `base` smaller but
   forces U3 to depend on U6, which `02` §2's rank order forbids. Recommendation: **(a)**, unchanged —
   the spec is right and this is only a note for the regenerator, since `base`'s package-info and
   `00` §5's "where is a value node" question have no row.
3. **F6 side question — is the "a bay that asserts nothing is a seam" rule a `Requirement` invariant or a
   `build()` rule?** `09` §3 puts it in `Bay`'s contract and the code puts it in `Requirement`'s
   canonical constructor, which is what makes `Bay.of` unwritable. If `Bay.of` is kept, the rule must
   move to `BlueprintBuilder.build()` and the placeholder `Requirement` becomes legal; if the code's
   two-step builder is adopted, `Requirement` may keep it. Recommendation: adopt the two-step builder
   (F6) and keep the rule where it is.

## 4. Documentation gaps that block regeneration

**Undocumented behaviour (the code has it, no spec sentence — a regenerator loses it)**

- `blueprint.BayBuilder` is a public type of `kernel.blueprint` that `09` §3 never names (`start`,
  `bayId`, `attach`); with `Bay.of` (F6) unwritable it is the only knot-closing path.
- `Bay`'s coverage validation is a coverage×scope whitelist (`Coverage.accepts`,
  `Coverage.java:11-19`) whose rows appear in no spec file; `24` §6 lists eight machines instead.
- `Bay.java:36-40` resolves `22` §16.1 item 17 / `09` Q5-4 / `08` §4.3 by rule: a `PRESENCE` bay may be
  `EXCLUSIVE` only with a `Declared` scope (`09` §4.1's R1–R4 table has no such row).
- `Bay.java:56-59` refuses a check whose static decision cells leave `bay.cells`
  (`SC-BAY-0107`), a condition no B-row, R-row or `13` row states.
- `AnchorSpec.java:14-16` adds a third B-4 clause — a blank `value` is refused
  (`"B-4: anchor " + role + " declares a blank value"`) — while `09` §4.6 B-4 is "anchor roles unique;
  the policy's `primaryRole` has a spec"; §7.2's "a throw without a row is a review defect" applies.
- `Bay.java:44-51` validates the check→bay ownership link inside `Bay`'s constructor, so `SC-CHK-0107`
  escapes from `BayBuilder.attach`, not from `build()` — `09` §4.6 B-5 and §4.7 say `expand(S)` is "the
  first place a check meets its bay" (the code also has that check in `expand`, `Blueprint.java:117-119`),
  so the same row now throws at two different sites in two different phases.
- `Blueprint.expand` skips design-time derivations entirely (`Blueprint.java:88` `if (stage != Stage.DESIGN)`) —
  `09` §4.8 step 3 has no such carve-out, and the class javadoc carries it only as "non-`DESIGN` derivations".
- `Blueprint.resolve` refuses a key produced at two stages (`SC-CHK-0109`, `Blueprint.java:152-157`), a
  rule `09` reserves for "a check reads a later stage's key".
- `AnchorSpec implements Comparable<AnchorSpec>` (role, property, value order) — §3 declares the record
  with no `Comparable`, and no spec statement needs the order (`09` §4.3 sorts `anchors` by role only in
  the document factory, `10` §4.3). Same for `BayBuilder`'s class-level "not retained" overlap note (F9).
- `Coverage` (renamed `CUBIC`/`SUBJECT`/`PRESENCE`) duplicates `DecisionKind`'s value set exactly, which
  is `22` §16.1 item 14's question reproduced rather than answered; the spec's own two enums use one set
  of names (`EVERY_CELL`/`ONE_SUBJECT`/`ANY_SUBJECT`).

**Unimplementable spec (a stated type/member/behaviour with no carrier — blocks regeneration even with no
code present)**

- F2/F3: no stage channel in the document grammar, while `expand(S)` selects by stage and B-11 compares
  stages.
- F6: `Bay.of(..., Function<Bay, Requirement>)` cannot be implemented together with "`requirement` is
  non-empty"; `Check.bay`'s declared `NamespacedId` contradicts the §5 sketch's `Bay`.
- F4/F5/F12/F13/F11: one condition, code or id with two statements (`SC-DOC-0002`, compact `bounds`,
  `DocumentUpgrade`, `SegmentLibrary.Builder`, a placed bay's id).
- `09` §8's "Assumed, to be confirmed" list is still the load-bearing contract for regeneration and none
  of it is decided: `Arguments` order-free value equality, `Arguments.of(ParameterKey<T>, T)`,
  `Budget.min`, `Cuboid.isEmpty/intersects/translate`, `Orientation.of(Turn,Tilt,Flip)`, the
  `Orientation.all()` order. `Arguments` and `ParameterKey` do **not** exist in `kernel.base` at all
  (grep empty), so `DerivationSpec.args`, `SegmentKey` and `SegmentDocument.parameters` have no value
  type; `DerivationSpec` substitutes `@Nullable Map<String, Object>` and says so in a comment.
- `10` §4.6/§4.6 `SegmentEmitter`'s round trip and `21` §6's `CaptureTool`/`SchematicImport` name
  `IssueSink` (undefined in `10`; used in `16` and `22` §12.5 but given no shape) and `CaptureRequest`
  (defined in `06` §127 only, with a component list that `16` line 47's `capture(req, issues)` must
  satisfy); `CompactWriter.write(BlueprintDocument)` appears in both `10` §3 and `21` §7, so that one is
  duplicated rather than missing.
- `09` §4.5's `OrientationPolicy.select` has no carrier and no test seam: `OrientationScenarioTest.java:47-64`
  reimplements the equation inline and takes `candidates.get(0)` with the comment "uniqueness comes from
  `OrientationPolicy.select` scanning allowed in `Orientation.all()` order (09 §4.5)" — the test asserts
  the rule it cannot call.
- `09` §1's ownership rows name ten types that exist nowhere in `src/` (`Seam`, `SeamId`, `SeamKind`,
  `Segment`, `SegmentKey`, `AnchorRoles`, `AnchorResolver`, `OrientationPolicy`, plus `Seam` handling in
  B-2/B-3/B-8 and `place`), together with `Blueprint.seams()/seam()/anchor(role)/orientation()/
  arguments()/provenance()/sourceOf()/bayCells()/overlaps()` and `BlueprintBuilder`'s
  `check`/`compute`/`seam`/`orientation`/`place`/`arguments`/`provenance`/`source` members. None of these
  is contradicted — they are simply unwritten, so this list is what a regenerated U5 gains and what no
  test currently exercises.

**Package/unit mismatch (`00` §5 vs the tree)**

- `00` §5: U6 `kernel.document`. The tree has no `kernel.document`, no `DataView` in `kernel.base`, and
  the code side of U6 is empty (F1). A regenerated tree following `10` §3 creates
  `dev.vfyjxf.cumulus.kernel.document` with `DataView`/`DataKind` in `kernel.base`; a tree that keeps
  today's shape has *no* document unit, so `00` §5's U6 row, `10`, `21`, `00` I-12 and `02` §2's
  `blueprint ⇄ document` note ("`blueprint` never imports `document`") would all have to be deleted
  rather than moved.
- The same table names U3 `kernel.expectation`, U9 `kernel.finding`, U10 `kernel.remedy`. In the tree the
  types U5 imports are in `kernel.predicate` (`Check`, `Focus`, `DecisionKind`, `Expectation`) and the
  finding types (`Finding`, `FindingBundle`, `FindingCode`, `Severity`, `Label`, `Locus`) are in
  `kernel.base`; there is no `kernel.expectation`. `09` §7.3's rank proof ("Rank `base` 0, `space` 1,
  `claim` 3, `expectation` 4, `blueprint` 5. `blueprint` consumes the other four") is written over the
  missing package, and `09` §1's "Does not own" column attributes `Check` to U3 while §3 declares it as a
  U5 member. A regenerated tree must pick one: create `kernel.expectation`/`kernel.finding`/`kernel.remedy`
  and move `predicate`'s and `base`'s types, or rewrite `00` §5, `01`, `02` §2 and `09` §7.3 to the
  existing package names. U6's absence is unambiguous; this rename is the one that would silently
  invalidate every import in the regenerated U5.

## 5. Checked and aligned

- `CheckPlan` (`CheckPlan.java:15-19`) — `(Stage stage, List<Computation<?>> computations,
  List<DerivationSpec> derivations, List<Check<?>> checks)`, `List.copyOf`, no `stage`-keyed cache:
  exactly `09` §3/§4.8, and `expand` is recomputed per call as §7.1 requires. **Answers `g-engine.md`
  §4's open question**: `CheckPlan.computations()` *is* Kahn-ordered — `Blueprint.resolve`
  (`Blueprint.java:171-225`) returns one topological order over aggregate computations and derivations
  (declaration-index tie-break via a `PriorityQueue`), and sections are prepended in declaration order;
  checks come last in declaration order (`orderChecks` is a `List.copyOf`), so `MatchSession`'s
  declaration-order walk is consistent with the plan.
- `Requirement` (`Requirement.java:8-23`) vs `09` §2/§3 — `(List<Check<?>> checks, List<Computation<?>>
  computations)`, copies, non-null members, empty refused with `SC-CHK-0107`, which is `09` §9 F-2's
  stated expectation ("a bay with no unit → `IllegalArgumentException` naming `SC-CHK-0107`").
- `AnchorSpec` (`AnchorSpec.java:8`) vs `09` §3/§2 — `(role, property, value)`, all three non-null, a
  cell is the definition origin, no box. Exact.
- B-1/B-2/B-4(first half)/B-5/B-6(derivation half)/B-7/B-9/B-10/B-11 all have a throw site carrying the
  row id in the message (`BlueprintBuilder.java:69-153`, `Blueprint.java:117-128`), and §4.2 holds in
  both directions (declared-but-disjoint and undeclared-intersecting are both refused,
  `BlueprintBuilder.java:117-153`).
- `Blueprint` is a `final class` with a package-private constructor and `builder(id)` as the only public
  path (`Blueprint.java:19,49`), fields `private final`, collections copied once, `equals`/`hashCode` over
  `(id, bounds, bays, anchors, derivations)` — matches `09` §4.6's private-constructor rationale and §7.1.
- `DerivationSpec.order < 0` refused (`DerivationSpec.java:24-26`); `bay(id)`/`bays()` are declaration
  order with a `LinkedHashMap` index (`Blueprint.java:31-45`) — matches `09` §3/§7.4's determinism claim.
- `Coverage`'s value set vs the spec: `CUBIC`/`SUBJECT`/`PRESENCE` is a rename of `EVERY_CELL`/
  `ONE_SUBJECT`/`ANY_SUBJECT` (F7/`24` §9 item 1); no third semantics is implied by any code path
  (`ClaimPlanner.java:96-118` treats `Audit` as "owns nothing", i.e. `Footprint.None`, so R1's `None` and
  `ClaimScope.Audit` are the same thing — which is what makes F7's hole reachable).
- `Stage.STALE` still exists (`base/Stage.java`) and `Blueprint.rank` maps it to `Integer.MAX_VALUE`
  (`Blueprint.java:169`) — the pre-declared R27 lag, recorded here only so it is not re-reported: `19`
  R27 deletes `Stage.STALE`.
- `10` §4.2's "`DataView` … moves to `kernel.base`" (R14) is consistent with `10` §3 and with `09` §7.3's
  acyclicity argument; nothing in the tree contradicts it, there is simply no `DataView`.
- `21`'s compact shape, defaults table and lowering order (`shell → cavity → fills → bays`, `SC-DOC-0503`
  for a split named bay, `SC-DOC-0504` for a second declaration, `#box@x,y,z:sx,sy,sz` synthetic ids) are
  internally consistent and match `10` §4.7 except where F4/F5/F11 say otherwise.
