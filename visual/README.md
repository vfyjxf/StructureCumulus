# StructureCumulus — Design Review 1.0

An interactive, adversarial review of the kernel design. Not a feature tour: each chapter is a claim
the kernel must survive — a frame mapping, a write, a conflict, a missing value, a finding.

The design of record is [`../docs/README.md`](../docs/README.md). The adapter is at M1: the host,
`/cumulus check <id> <origin>` and the capture-check path (`LevelCapture`, `LevelSnapshot`,
`LevelLease`, block/block-entity descriptors, `ServerState`, in-memory `BuiltinDocuments`), proven by
two GameTests (capture-only; capture → compile → check). Claims/persistence, JSON/datapack, the rest
of the command surface, entities, nested views, traits, orientation mapping and windows are pending.
The UI opens in **中文**; the **EN** button in the top bar switches to the
English reading layer. Technical identifiers, code, JSON, Java and finding codes stay in their
original form in both languages.

## Run

```bash
cd visual
npm install
npm run dev       # http://127.0.0.1:5173
npm run build     # tsc --noEmit + production build
npm run preview
```

The app uses React, Vite, TypeScript, CSS and SVG only. No router, chart library, remote font or
network request.

## What is inside

97 kernel tests, 55 codes, 23 decisions, 11 ranked kernel packages, plus M1's two adapter GameTests.
One complete document and a Java builder excerpt target the same IR.

| Chapter | What you can do |
| --- | --- |
| **00 / Thesis** | Read the four laws; click a deleted concept to see its replacement. |
| **01 / Lattice** | Trace a package's imports through the rank graph; inspect its law and source file. |
| **02 / Object** | Walk `WorldView → Bay → Check → Blueprint → MatchOutcome`; switch a key between `MATCH` and `INSTANCE`. |
| **03 / Workbench** | Inspect the campfire shell/hollow/fire, switch bays, explode/assemble, step `COMPILE → CHECK → SETTLE` (then the derive aftermath), toggle JSON/Java, and move the siting through origin and quarter turns. |
| **04 / Ledger** | Move two owners' bays, change `EXCLUSIVE`/`SHARED`, settle, race the revision, and see the conflict matrix and rows. |
| **05 / Fold** | Refuse a section, change `sum`/`max`, toggle `whenAbsent`, and see the key become absent and a derive skip. |
| **06 / Codes** | Search/filter all 55 codes, inspect a code's producer, and play with the finding-bundle caps and truncation tail. |
| **07 / Proof** | Read the gate report, the falsifiers, the 23 decisions, the regeneration protocol and the document index. |

Press <kbd>⌘K</kbd> / <kbd>Ctrl K</kbd> for the index. Use **EN / 中文** in the top bar to switch languages. The **NIGHT** switch flips the same
information to a dark instrument palette. The layout is responsive down to 390 px.

## Data is generated, not hand-maintained

`codes`, `decisions` and `tests` are projected from the design set and the kernel test sources:

```bash
python3 scripts/extract-review-data.py
```

`campfire.ts` is the curated worked example (3×3×3, 27 cells, 27 claims, spend 28 reads / 4 sections, derive capacity
10). The numbers match the JUnit scenario.

## Historical note

This replaces the 2026-10-21 NASA-punk atlas. That atlas documented design v0.2 and is kept in git
history; this app documents v1.0 and is the one to run.

## Caveat

The review app, the kernel’s direct `javac`/JUnit harness, `./gradlew check` and the adapter’s two M1
GameTests (`runGameTestServer` 2/2) are green.
