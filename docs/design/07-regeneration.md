# 07 — Correction

> How code that drifted from this set is brought back into agreement, in what order, and what "done"
> means. Nothing is rebuilt: the set is corrected first and the code follows in the same commit. The
> file name predates this rule; the protocol is correction, not regeneration.

## 1. Inputs and outputs

**Input:** this `docs/` set only. The archived baseline is evidence for a claim about the *old* code,
never a source of a new signature. A correction may read `src/kernelTest` only to reuse a fixture
idea, not an API.

**Output:** the existing tree, edited in place — `src/kernel/java/dev/vfyjxf/cumulus/kernel/{core,space,finding,world,claim,structure,
context,document,engine,remedy,spi}`, `src/kernelTest/java`, and the `src/main/java` mod entry with
the adapter. Committed: M1 (host + `/cumulus check` + capture-check: `LevelCapture`,
`LevelSnapshot`, `LevelLease`, block/block-entity descriptors, the `ServerState` lifecycle, in-memory
`BuiltinDocuments`), M2a (loaded-entity capture, policy windows, footprint indexing, entity-vs-block
precedence), M2b (upright orientation mapping), M3 at `d4b6d82` (`SavedClaims`; `SavedContexts` removed by D-40,
CAS/release/reload), M2c at `cb06aa8`/`64d04ce` (Model C, body identity, total read, four-state
traits, the five adapter trait keys) and M2d at `f3e0338`/`d6aefc2` (nested extents, one
`LevelSnapshot` + `FrameData`, child frames, cuts, child entities), M4a at `f2659c7` (JSON
reader/writer), M4b at `76a8e5c` (datapack reload) and the command slice at `4899d86` (settle, plus
compile/explain/claims); capture-revision lease invalidation landed at `0f0bf25`, the determinism
gate with `DeterminismTest`, the rotated-siting two-phase capture (P1) at `56c632c`, the
event-edged watched-region mark counter (M5a) at `d1fa42f`, the 40 non-upright orientations (M5d) at
`ac11d0c` and the capture report (M5e, D-28, `SC-MC-0003`/`0004`/`0005`) at `28d0334`. The sited-root
descriptor projection (the old S6 label, D-29) was implemented and is superseded by the typed host snapshot (D-33,
`10-host-data.md`): `OccupantDescriptor` is `(type, kind)`, the projection is retired, and
`OrientationAuditGameTest` covers the retained orientation audit. Kernel stress batch 1
(`kernel-stress-batch-1`, `110e797`) adds 44 characterization scenarios in 12 `engine` classes
(`06-verification.md` §3.1) and batch 2 (`kernel-stress-batch-2`, `3e60ed4`) adds 17 kernel scenarios
plus 11 adapter unit tests (§3.2), taking the kernel suite to 186 tests in 33 containers and the
adapter suite to 141 at that commit; batch 2's one code change is the `NestedFocus` non-binding fix.
Neither batch changes a type or a decision. M3 persistence is compatible with Model C.

The host-data round closes with H4: H1 adds `HostType`, `WorldView.get`, `Expectations.host` and
`OccupantDescriptor(type, kind)`; H2 adds one `SnapshotLevel` per frame and the capture wiring; H3
adds the adapter `property`/`state`/`tag`/`fluid` factories and `SC-MC-0006`…`0010`; H4 adds the
documented counts and the adapter host-data stress group (§3.4). T1–T3 (D-34) then replace the
`readBlock`/`readEntity` trait providers with the generic per-holder registry and direct capability
values. S5 of the structure-set close-out adds the production `set check`/`set settle` command, its
one union capture and the check-only host path (`09-structure-sets.md` §16). S6 closes the set layer:
`SC-SET-0008` same-siting rejection, `ContextStore.remove` with rollback invalidation, and one set
budget shared by presence and every member attempt. The tree registers
**382 kernel tests in 48 containers, 411 adapter unit tests in 43 containers and 126 `@GameTest`
methods**; `Codes.all` has 66 entries and the decision record has 40 decisions (D-40). Pending only:
the performance/cost pass (block-entity-lazy and entity sweep) and fresh visual review screenshots.
No regeneration milestone exists or is planned: the code is corrected against this set in place.

## 2. The order

1. **Name the drift.** For every divergence, decide which side is wrong. A public shape the set does
   not contain is a **conformance defect**: the code changes. A shape the set contains that cannot be
   compiled, or that cannot express its own behaviour, is a **design defect**: the set changes.
2. **Amend the set first when the set is wrong.** A design defect is recorded in `05-decisions.md`
   and fixed in the owning module file before any code changes. The amendment names the type, the old
   shape, the new shape, the consequence and the affected modules.
3. **Correct in dependency order.** `core` → `space` → `finding`/`world` → `claim` → `structure` →
   `context` → `document` → `engine` → `remedy` → `spi`, then `mc`. A module may not invent a public
   member to make a correction cheaper; a signature that cannot express its behaviour is an
   amendment, not a local workaround.
4. **Correct the tests in the same commit.** Each module's §7 obligations and the §3 scenarios the
   module feeds. A correction that leaves an obligation unproven is not done.
5. **Never keep two shapes.** No compatibility shim, no deprecated overload, no flag: the old shape
   is removed in the commit that lands the new one. Git history keeps the evidence.
6. **Run the gates.** Compile, purity, tests, spec index, no stubs.

## 3. What a module implementer gets

A brief contains:

- the module file and the two neighbouring files it depends on;
- the current code in that package and the exact drift to correct;
- the exact package, `@NullMarked`, and the JSpecify rule;
- the constraint: no new public type, no new public member, no signature change; report a gap;
- the test obligations from the module file and the scenarios it feeds;
- the SourceScout rule is irrelevant here (this is this repo's own code); no other project is read.

The implementer does not "improve" a signature. The lead reviews every public diff against the
module file.

## 4. Definition of done

| # | Check | Evidence |
| --- | --- | --- |
| 1 | `javac --release 21` over `src/kernel/java`, JSpecify only | exit 0, zero errors |
| 2 | no Minecraft/NeoForge/Mojang name under `kernel.**` | purity scan; compile classpath |
| 3 | `src/kernelTest/java` compiles and all tests pass | JUnit launcher; 0 failures, 0 skipped |
| 4 | the scenarios in `06-verification.md` §3 and the §3.1–§3.7 stress groups pass | named test classes |
| 5 | every public type in code is in a module file, and vice versa | `tools/spec-check.py`; member review against §3 |
| 6 | every `SC-` code in code is in `Codes.all`, and every `Codes.all` entry is in a producer row | registry test |
| 7 | no `TODO`/`FIXME`/`UnsupportedOperationException`/empty public body | grep |
| 8 | two runs over one tuple are equal | golden test |
| 9 | the campfire/furnace examples in `04-api.md` compile as test sources, and the host kinds compile only through the adapter catalog | `ScenarioTest`; the document form in `DocumentCompilerTest`/`AdapterExpectationsTest` |
| 10 | no second shape survives: no deprecated alias, no compatibility overload, no `TODO` in a corrected area | `git diff`; grep |

The spec index is green at v1.2: every documented type is implemented, every `SC-` code in code is
in `Codes.all`, and every `Codes.all` entry has a producer row. The M5e capture-report amendment was
applied docs-first and its carrier landed, so the index is green again; there is no pending signature
block and no suppressed gate (`06-verification.md` §1).

## 5. The set is amended, not frozen

The design is expected to change. The rule is: **amend the module file and the decision record first,
then correct the affected module and its tests.** A code patch that outruns the document is the exact
failure this set exists to prevent; a document that outruns the code is a pending block, and a pending
block is not allowed to survive a commit.

## 6. The fallback if a signature is wrong

1. Stop the module; do not add a second type or a temporary overload.
2. Write a one-paragraph amendment in `05-decisions.md`: the type, the old shape, the new shape, the
   consequence, the affected modules.
3. Update the module file and the type index.
4. Correct the affected type and every call site; compile the whole kernel.
5. Remove the old code path in the same commit; it is never kept behind a flag.

## 7. What the next round owes

- **Performance/cost pass.** The block-entity-lazy and entity sweep, plus the known costs: claim-row
  volume for large structures, the per-cell `read`/`statusOf` cost, the section pass and the
  nested-frame budget. If a row form other than per-cell is needed, it is a `ClaimRow`/ledger
  amendment, not a hidden optimization. The JMH pass (D-73) has measured the kernel-side debts —
  latest run in quick mode (`-f 1 -wi 1 -i 3 -w 1s -r 1s`), 2026-11-06,
  `Data/StructureCumulus/bench/2026-11-06-jmh-quick.json`: a 32³ claim derivation costs 0.83 ms/op
  (32 768 rows, the sort included) against 0.015 ms/op at 8³; sorting 32 768 shuffled claim rows
  costs 4.8 ms/op; scanning 32 768 request rows against 32 768 held rows costs ≈950 ms/op; the
  nested-frame budget is 15.7 ms/op for a 10 000-layer chain and 606 ms/op for a 22-layer diamond
  (8 388 606 flattened definition instances); per-cell `CuboidSet.union` over a 16³ box costs
  583 ms/op against 2.3 ms/op for one bulk construction of the same cells; and a 1024-key
  whole-table context publish costs 0.46 ms/op. The per-cell `read`/`statusOf` cost, the section
  pass and the entity sweep need a live level and are reviewed by reading, not benchmarked (D-73).
- **Visual review screenshots.** `visual/` trails this design set until its data and screenshots are
  regenerated; it is a reading aid, never a source.
- A host scheduler with coalescing, dirty windows and a per-world ledger stays outside v1; this round
  does not owe it.

The siting-aware value question is now answered by the typed host snapshot (D-33), not by D-29: a
host expectation reads the captured snapshot in its own frame, and the adapter projects no
definition-frame state. A host that wants definition-frame values captures them itself; a missing
value is the host's `SC-MC-0010`, never a kernel mismatch.
