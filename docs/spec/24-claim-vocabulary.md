# 24. The claim vocabulary: three questions, three answers

> Decision record, 2026-10-23. Status: **decided**; the unit files it touches are updated, and the
> code migration is §9. It settles how a claim is described: three questions, three types.

## 1. Why claims exist

Two players can build the same machine in the same place. The blocks are one set, but each
controller believes they belong to it: drops come out twice, energy counts twice, and breaking one
machine leaves the other reporting "formed" about blocks that are gone.

A claim is the record that stops this. One row says: *these cells belong to this instance*. The
table of rows is the ledger. "Which machine owns this cell" must have exactly one answer, and the
ledger is where that answer lives.

## 2. Why checking and claiming are separate

A player walks past a spot; the host asks every tick "could a machine go here?". That question must
not write anything: if each probe registered a claim, the ledger would fill with thousands of
"maybe"s in seconds, and each one would really reserve the ground for its asker.

So there are two actions with different natures: **check** reads and answers, **settle** writes.
`23` covers that split. This file is about a different question: once we do write, *what exactly is
written, and how do two claims meet?*

## 3. What went wrong with the old vocabulary

The model grew one value at a time, each for a new machine, and the values were never designed
together:

| Demand |
| --- |
| a furnace shell must own a whole box |
| a pipe corner may own only its pipes, not the air between them |
| a two-cell door must bind exactly the bay |
| a mob pen must own nothing |

Each addition multiplied the combinations, so legality became a whitelist: twelve rows, checked one
by one. The whitelist is the symptom: **when an illegal combination has to be listed and rejected,
the questions were wrong.** Four of those rows were nonsense — a claim that blocks every other
owner while owning no cell — and five were legal but used by nothing.

One value carried a second, unrelated meaning: besides "own nothing" it also granted permission to
read outside the bay. A check that needed to read one block behind a door could only get that
permission by giving up ownership of the whole bay. That is a trap, not a feature.

## 4. The three questions

A bay answers three questions. Each question gets its own type, and each type answers only that
question.

### Question 1: what must the checks establish? — `Coverage`

| Value | What it means | Example |
| --- | --- | --- |
| `EVERY_CELL` | every cell of the bay is decided by some check | a furnace wall: 194 cells, each must be the right block |
| `ONE_SUBJECT` | the checks bind one thing, and its footprint is exactly the bay | a two-cell door: two halves, no more, no less |
| `ANY_SUBJECT` | at least one thing is present; cells are not decided one by one | a mob pen: a cow inside is enough |

The engine needs this to know whether a check set is *complete*. Without it, "three of the 194 wall
cells were checked" would look like a pass.

### Question 2: which cells does the instance own? — `Footprint`

| Value | Owned cells | Known when |
| --- | --- | --- |
| `Box` | the bay cuboid, air included | blueprint build |
| `Bound` | the non-air footprints of the things the checks bound | after the match |
| `Declared(cells)` | a set the author writes down | blueprint build |
| `None` | nothing | immediately |

Owning and checking are different. A pipe corner is the clearest case: eight cells must each be
right, but only four are pipes. Owning all eight would make two neighbouring corners collide over
the four air cells they share, even though they never touch.

`Box` is what protects an empty volume: a furnace cavity owns 100 air cells, so nobody may put a
chest inside it.

### Question 3: how does this claim meet another? — `Sharing`

| Value | Effect on another claim over the same cell |
| --- | --- |
| `EXCLUSIVE` | conflicts with every other owner |
| `SHARED` | conflicts with nobody |

Two claims meet in exactly one of two ways: one of them excludes, or neither does. That is all the
conflict rule needs, and it is why the type has two values.

## 5. The rules

Only one combination is a genuine contradiction, so only one rule is needed for combinations, plus
three invariants that were previously hidden inside the whitelist:

| Rule | When | Statement | Code |
| --- | --- | --- | --- |
| R1 | build | `EXCLUSIVE` requires a footprint other than `None` | `SC-CHK-0106` |
| R2 | settle | `EXCLUSIVE` + `Bound` must resolve to at least one cell | `SC-BAY-0111` (new) |
| R3 | build + settle | `ONE_SUBJECT` requires subject-kind checks, and the bound footprint must equal the bay | `SC-BAY-0107` |
| R4 | build | `EVERY_CELL` requires the per-cell checks to cover the bay; `ANY_SUBJECT` forbids per-cell checks | `SC-CHK-0106` |

Everything else is allowed, because everything else is a coherent intention rather than a
contradiction.

R2 exists because a bound footprint can come out empty: a door that never bound (its two halves
were not both read) would otherwise settle as "exclusive, owning nothing", which is R1's
contradiction arriving one step later.

## 6. The machines the model must still describe

| Machine | Declaration |
| --- | --- |
| furnace shell, 194 cells | `EVERY_CELL` + `Box` + `EXCLUSIVE` |
| furnace cavity, 100 air cells | `EVERY_CELL` + `Box` + `EXCLUSIVE` |
| two-cell door, atomic | `ONE_SUBJECT` + `Bound` + `EXCLUSIVE` |
| pipe corner, 8 asserted / 4 owned | `EVERY_CELL` + `Bound` + `EXCLUSIVE` |
| mob pen, own the animal's footprint | `ANY_SUBJECT` + `Bound` + `SHARED` |
| observation bay, own nothing | `EVERY_CELL` + `None` + `SHARED` |
| declared ground | `ANY_SUBJECT` + `Declared` + `EXCLUSIVE` |
| shared frame, nobody owns it | `ONE_SUBJECT` + `None` + `SHARED` |

The mob pen is the one case the previous vocabulary could not express: its bay could own nothing, or
own a declared box unrelated to the animal, and nothing in between.

## 7. What this buys, and what it costs

Gains:

1. **Contradictions become unrepresentable.** One rule replaces a twelve-row whitelist.
2. **One word, one job.** `None` no longer smuggles in a read permission; `Footprint` no longer
   mixes "can be computed now" with "can be computed after the match".
3. **A real gap closes**: a pen can own the animal's footprint.
4. **The names explain themselves**: `EVERY_CELL` needs no glossary.

Costs: `Bay` takes `footprint` + `sharing` instead of `mode` + `scope`; the document JSON changes;
about twenty tests and six documents follow. There is no compatibility layer — the library has not
shipped, so the vocabulary changes once.

## 8. Still open

| Question | Why it is open |
| --- | --- |
| may `Declared` cells leave the bay? | today they may, silently; `SC-BAY-0108` has no producer |
| what does an entity claim mean when the animal moves? | the footprint is resolved at settlement; movement after that is not tracked |

## 9. Migration

Code, in order:

1. `Coverage`: rename values, rewrite `accepts` into R1/R4.
2. One sealed `Footprint` (with `resolve` and the known-when information) and one `Sharing` enum
   (`blocks()` true only for `EXCLUSIVE`).
3. `Bay`/`BayBuilder`: components and validation; `BlueprintBuilder` B-7 uses `EXCLUSIVE` only.
4. `ClaimPlanner`: resolve the footprint, then apply R2.
5. `ClaimMatrix`: the conflict predicate calls `blocks()`; messages name the sharing value.
6. `ClaimRow`: field renamed, order key unchanged.
7. Tests: matrix, ledger, planner, the eight machines above, plus R1/R2 refusals.

Documents: `08` §2/§3/§4.3/§4.5, `09` bay chapter and legality table, `10` §4.1 (the IR) and §4.5
(the JSON keys and their defaults), `21` schema, `16` §4.6 (the row shape it stores), `22` §6.5/§10,
`17` JSON and prose, `23` §8, `02`, `18`, `13` codes, `00`'s claim requirement row.
