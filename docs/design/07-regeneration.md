# 07 — Regeneration

> How the kernel is rebuilt from this set, in what order, and what "done" means. The old kernel is
> deleted before the first line of the new one is written; there is no migration.

## 1. Inputs and outputs

**Input:** this `docs/` set only. The archived baseline is evidence for a claim about the *old* code,
never a source of a new signature. A generator may read `src/kernelTest` only to reuse a fixture
idea, not an API.

**Output:** `src/kernel/java/dev/vfyjxf/cumulus/kernel/{core,space,finding,world,claim,structure,
context,document,engine,remedy,spi}`, `src/kernelTest/java`, and the `src/main/java` mod entry with
the adapter. Committed: M1 (host + `/cumulus check` + capture-check: `LevelCapture`,
`LevelSnapshot`, `LevelLease`, block/block-entity descriptors, the `ServerState` lifecycle, in-memory
`BuiltinDocuments`), M2a (loaded-entity capture, policy windows, footprint indexing, entity-vs-block
precedence), M2b (upright orientation mapping), M3 at `d4b6d82` (`SavedClaims`, `SavedContexts`,
CAS/release/reload), M2c at `cb06aa8`/`64d04ce` (Model C, body identity, total read, four-state
traits, the five adapter trait keys) and M2d at `f3e0338`/`d6aefc2` (nested extents, one
`LevelSnapshot` + `FrameData`, child frames, cuts, child entities), M4a at `f2659c7` (JSON
reader/writer), M4b at `76a8e5c` (datapack reload) and the command slice at `4899d86` (settle, plus
compile/explain/claims); capture-revision lease invalidation landed at `0f0bf25`, the determinism
gate with `DeterminismTest`, the rotated-siting two-phase capture (P1) at `56c632c`, the
event-edged watched-region mark counter (M5a) at `d1fa42f`, the 40 non-upright orientations (M5d) at
`ac11d0c` and the capture report (M5e, D-28, `SC-MC-0003`/`0004`/`0005`) at `28d0334`. The sited-root
descriptor projection (S6, D-29) was implemented and is superseded by the typed host snapshot (D-33,
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
values. The tree registers **258 kernel tests in 39 containers, 284 adapter unit tests in 37
containers and 65 `@GameTest` methods**; `Codes.all` has 64 entries and the decision record has 34
decisions (D-34). Pending only: the performance/cost pass (block-entity-lazy and entity sweep) and
fresh visual review screenshots. There is no regeneration milestone.

## 2. The order

1. **Freeze the type index.** Every module file's section 3 is the contract. Extract the public and
   public-nested type names; compare across files for duplicates; freeze.
2. **Write the skeleton.** One file per type, package-correct, JSpecify `@NullMarked`, all signatures,
   bodies returning `null`/`0`/`List.of()` or a package-private placeholder. Compile with
   `javac --release 21` and JSpecify only. This proves the set is self-consistent before behaviour.
3. **Implement in dependency order.** `core` → `space` → `finding`/`world` → `claim` → `structure` →
   `context` → `document` → `engine` → `remedy` → `spi`. A module may not change a signature from the
   skeleton; a signature that cannot express its behaviour is a **design amendment**, recorded in
   `05-decisions.md` before the code changes.
4. **Test as you go.** Each module's §7 tests; then the §3 scenarios. A scenario that needs a
   signature change stops the run and amends the design.
5. **Delete the old code and tests before step 2.** Git history and the archive keep the evidence.
6. **Run the gates.** Compile, purity, tests, spec index, no stubs.

## 3. What a module implementer gets

A brief contains:

- the module file and the two neighbouring files it depends on;
- the skeleton files already on disk (compile them first);
- the exact package, `@NullMarked`, and the JSpecify rule;
- the constraint: no new public type, no new public method, no signature change; report a gap;
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
| 4 | the sixteen scenarios in `06-verification.md` §3 and the §3.1/§3.2/§3.3/§3.4 stress groups pass | named test classes |
| 5 | every public type in code is in a module file, and vice versa | `tools/spec-check.py` |
| 6 | every `SC-` code in code is in `Codes.all`, and every `Codes.all` entry is in a producer row | registry test |
| 7 | no `TODO`/`FIXME`/`UnsupportedOperationException`/empty public body | grep |
| 8 | two runs over one tuple are equal | golden test |
| 9 | the campfire/furnace examples in `04-api.md` compile as test sources, and the host kinds compile only through the adapter catalog | `ScenarioTest`; the document form in `DocumentCompilerTest`/`AdapterExpectationsTest` |
| 10 | the old `docs/spec`, `docs/design-notes`, `docs/guide.zh.md`, `docs/README.zh.md` and the old
    kernel sources are gone | `git status`; only the new set remains |

The spec index is green at v1.2: every documented type is implemented, every `SC-` code in code is
in `Codes.all`, and every `Codes.all` entry has a producer row. The M5e capture-report amendment was
applied docs-first and its carrier landed, so the index is green again; there is no pending signature
block and no suppressed gate (`06-verification.md` §1).

## 5. Regeneration is not a one-way door

The design is expected to be amended. The rule is: **amend the module file and the decision record
first, then regenerate the affected skeleton and module.** A code patch that outruns the document is
the exact failure this set exists to prevent.

## 6. The fallback if a signature is wrong

1. Stop the module; do not add a second type or a temporary overload.
2. Write a one-paragraph amendment in `05-decisions.md`: the type, the old shape, the new shape, the
   consequence, the affected modules.
3. Update the module file and the type index.
4. Re-extract the skeleton for the affected type; compile the whole kernel.
5. Resume the implementation; the old code path is deleted, not kept behind a flag.

## 7. What the next round owes

- **Performance/cost pass.** The block-entity-lazy and entity sweep, plus the known costs: claim-row
  volume for large structures, the per-cell `read`/`statusOf` cost, the section pass and the
  nested-frame budget. If a row form other than per-cell is needed, it is a `ClaimRow`/ledger
  amendment, not a hidden optimization.
- **Visual review screenshots.** `visual/` trails this design set until its data and screenshots are
  regenerated; it is a reading aid, never a source.
- A host scheduler with coalescing, dirty windows and a per-world ledger stays outside v1; this round
  does not owe it.

The siting-aware value question is now answered by the typed host snapshot (D-33), not by D-29: a
host expectation reads the captured snapshot in its own frame, and the adapter projects no
definition-frame state. A host that wants definition-frame values captures them itself; a missing
value is the host's `SC-MC-0010`, never a kernel mismatch.
