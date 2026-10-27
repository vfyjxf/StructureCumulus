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
`LevelSnapshot` + `FrameData`, child frames, cuts, child entities). M3 persistence is compatible with
Model C; only its settle command and lease invalidation are missing. Still pending: M4 JSON/datapack
and the remaining commands, non-upright pitch/roll state mapping, `SC-MC-0004`/`0005` emission,
window/mark-counter lease invalidation and the deferred `SC-MC-0003` channel.

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
| 4 | the fourteen scenarios in `06-verification.md` §3 pass | named test classes |
| 5 | every public type in code is in a module file, and vice versa | `tools/spec-check.py` |
| 6 | every `SC-` code in code is in `Codes.all`, and every `Codes.all` entry is in a producer row | registry test |
| 7 | no `TODO`/`FIXME`/`UnsupportedOperationException`/empty public body | grep |
| 8 | two runs over one tuple are equal | golden test |
| 9 | the campfire/furnace examples in `04-api.md` compile as test sources | `ScenarioTest`; the document form in `DocumentCompilerTest` |
| 10 | the old `docs/spec`, `docs/design-notes`, `docs/guide.zh.md`, `docs/README.zh.md` and the old
    kernel sources are gone | `git status`; only the new set remains |

The spec index is green in v1.2: every documented type is implemented, every `SC-` code in code is
in `Codes.all`, and every `Codes.all` entry has a producer row. There is no pending signature block
and no suppressed gate (`06-verification.md` §1).

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

- **M4.** JSON reader/writer, datapack reload and the remaining commands (`compile`, `settle`,
  `explain`, `claims`), including the settle command on top of the committed `SavedClaims`/
  `SavedContexts` stores.
- **M5.** The remaining host gates: determinism golden test, lease invalidation by epoch/mark
  counter, and the command/GameTest coverage that M4 adds. The kernel and adapter suites are already
  green (116 kernel, 92 adapter, 22 GameTests).
- **Non-upright tilt.** The 40 pitch/roll/inverted orientations, capability-aware state remapping
  and `SC-MC-0004` emission.
- **`SC-MC-0005`** capture-over-cap emission at the host boundary, and the deferred `SC-MC-0003`
  capture-finding channel for a cut child; the adapter currently returns `CaptureResult.TooLarge`
  and the consumer reports `SC-OCC-0003`.
- A host scheduler with coalescing, dirty windows and a per-world ledger, outside the kernel.
- A performance pass: claim-row volume for large structures, the per-cell `read`/`statusOf` cost, the
  section pass and the nested-frame budget. If a row form other than per-cell is needed, it is a
  `ClaimRow`/ledger amendment, not a hidden optimization.
