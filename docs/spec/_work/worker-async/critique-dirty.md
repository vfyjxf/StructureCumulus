# Critique — W3 dirty/refresh (adversarial)

> W3 assumed wrong. MC facts via SourceScout on `neoforge-21.1.219`.

## 1. (Critical) The payload channel has neither a witness nor a hook

**Scenario.** A hopper removes a stack from a tracked chest (`setChanged` → `Level.blockEntityChanged`,
`BlockEntity.java:191-198`). `markPayload(p)` logs a mark and no section, so
`stale(i) = store.rev(k) != revOf(i)` is false everywhere: the pump copies nothing, `dirtyForCheck`
stays raised, dispatch sees the mark before `baseEpoch` with `version`, digest and `Siting` unchanged
— P-3 holds, the stale inventory publishes `Formed`, `SETTLE` claims it. The hooks are as narrow: §2
injects `BlockEntity.setChanged`, which a mod override without `super` never reaches and direct
`level.blockEntityChanged` callers (`BeaconMenu:170`) bypass; `loadWithComponents` (`:82-89`) calls
neither, and `16` §4.7 restores rollback payloads through it. Open question (1) concedes the hook was
never verified, though it is knowable: `setChanged` is a narrower wrapper over
`Level.blockEntityChanged` + `updateNeighbourForOutputSignal` (`:191-198`), and
`Level.blockEntityChanged` only calls `setUnsaved(true)` (`Level.java:902`).

**Violated / proof fails.** D-2, D-3, P-1, P-3. P-1 and P-3's term (3) both quantify over `sections`,
so neither can see the mark; CE-2's "log marks ⇒ discard" is a discard, not a refresh — nothing turns
a payload mark into new payload bytes. §5's coverage table is a per-row assertion whose BE path set is
a strict subset of the real one.

**Fix.** A payload revision inside `stale()`, all payloads re-read on a dirty watch; inject only at
`Level.blockEntityChanged(BlockPos)`; cover `loadWithComponents`, or oblige `16` §4.7 to `markPayload`
after restore.

## 2. (Critical) Refresh has two owners and no atomic seal; dispatch sees neither

**Scenario.** A 40-section machine goes `REFRESHING`; the pump copies three sections, hits the
deadline, publishes via `refresh(replaced, rev)`, leaves 37 stale. W1's pipeline
(`submit → refresh(key) → capture → execute`) has no `REFRESHING`/`Deferred` branch, so it matches 3
fresh + 37 old; each partial publish bumps `version`, so the result discards next tick. With one
continuing mark inside the box the watch is never all-fresh: no check publishes, no claim settles.

**Violated / proof fails.** D-3, D-5, W1 W-4, W2 S-5. P-2 proves pump progress, not dispatch
admissibility, and D-3 has no enforcement point: `state()` says `REFRESHING` and W1 never reads it.
W1's ask ("`refresh(key)` bounded") is satisfied by exactly this partial refresh; `ensureClean`'s 4
sections also exceed W1's 4096-cell sync cap.

**Fix.** One entry point owned by W3, atomic over the whole watch (sections + payloads + entity
window), returning `Ready | Deferred`, called once by W1 at submit; the pump pre-warms only.

## 3. (Critical) `ChangeLog` cannot witness what P-3 asks of it

**Scenario.** (a) No retention floor: W1's deadline is 10 s, so a mark aged out before the result
returns makes `intersects(sections, baseEpoch)` answer `FRESH` — publish. (b) `install` stamps
`++captureEpoch` while submit carries `ChangeLog.epoch()`: one counter means the refresh's own
installs read `CHANGED` and every check discards forever; two are never declared. (c) Filtering by
`sections` hides payload and entity marks — precisely CE-2. (d) Overflow ⇒ discard is safe but not
live: churn makes every check `Undecided`. (e) W4 D2 needs `contentStamp(ChunkKey)` read atomically
with `copy()`; W3 has per-section `rev` only.

**Violated / proof fails.** P-3 (3), D-3, W4 P2. "The log over-approximates" confuses safety with
liveness; aging under-approximates over exactly the window a long check occupies.

**Fix.** One `dirtyEpoch` per watch, bumped by any intersecting mark including payload/entity, compared
by equality, never aged below twice the longest check; overflow sets `unlocalised`; name both counters;
add `contentStamp`.

## 4. (High) Store revision read at seal; pins counted per key, not per holder

**Scenario.** Two watches share `k`; A seals `k`@7, B installs `k`@8. `revOf(i)` is a **store** read,
so taken at seal after B's install, A's snapshot advertises 8 over bytes@7: `stale` stays false and
every check matches the old wall — though W2 already carries `captureRevision()`. Pins are per key, so
releasing the superseded snapshot's pin frees `k` under the new one and `evictUnpinned` drops live
content; `{key, rev, null, pins}` metadata is never reclaimed.

**Violated / proof fails.** P-1, P-4, W2 S-3. "Computed, so it cannot drift" compares two stores, not
the store against the bytes the snapshot holds.

**Fix.** Revision from `captureRevision()`; per-key pin refcount; evict only at zero; reclaim on last
unpin plus `unwatch`.

## 5. (High) No-op refresh, `dirtyForCheck`, and P-3 term (2) contradict CE-1

**Scenario.** The piston fires and retracts. The mark raises `dirtyForCheck`; the pump refreshes,
content is equal, and §3 both draws `equal content → DIRTY` and forbids clearing `dirtyForCheck`. The
watch sits `DIRTY` with nothing stale, and the only clearer is dispatch, so W1 checks identical bytes;
terms (1)(2)(4) hold and (3) is `FRESH` because the mark precedes `baseEpoch`. CE-1's "zero checks" is
false. CE-3's fix is scoped to `ensureClean`: pump order, `dirtyAge` and `/cumulus` still read
`state()`, so a same-tick mark is not a candidate until next tick.

**Violated / proof fails.** D-1, CE-1, D-3. Term (2) reads as "the world did not change", but `version`
moves only on content change, so "never checked at this content" and "checked" are indistinguishable.

**Fix.** Derive `state()` from `(staleSets, checkInFlight, broken, unknown)`; an all-equal refresh
clears `dirtyForCheck` and returns `CLEAN`; drop term (2), gate acceptance on `dirtyForCheck == 0`.

## 6. (Medium) Every non-block dirty source is flattened onto block sections

**Scenario.** (a) `markEntityFootprint` must bump section revs, so a cow in a barn dirties 2–4 sections
per `setPos` (20/s): full recopies, W4 cache misses, every bay re-executed; `setPos` also fires on
spawn and teleport. (b) Claim settle bumps block sections for unchanged blocks, and §8 dilates to
`changed = sections ∩ readSet().cells()`, so one piston re-runs every step touching that section,
defeating `11` §4.6's disjointness proof. (c) `churnScore`'s scope is unstated: per section, a clock
next door throttles a static correct watch; per watch, a hopper inside does. Throttling COMMAND/SETTLE
lets another owner settle `EXCLUSIVE` claims first, so "delays a `Formed`, never falsifies one" fails
once claims exist.

**Violated / proof fails.** D-2, `11` §4.6, `08` §4.5. P-1/P-4 reason over one section map; payload,
entity and claim dirt have no carrier, so correctness is bought with over-invalidation and lost in §1.

**Fix.** Separate `entityRev`/`payloadRev`; mark entities only when the footprint's section set changes;
claim dirt as `changedTypes = {SC:claim}` re-running `CLAIM` only; per-watch churn with a COMMAND
carve-out and bounded deferrals.

## 7. (Medium) The pump's stop condition and terminal states are not work-unit accountable

**Scenario.** The budget is "a section, payload or `System.nanoTime` deadline", so a clock decides what
is fresh and two identical servers diverge. Un-budgeted passes dominate — one `saveWithId` per BE (500
chests in a section), one `saveWithoutId` per entity plus an AABB query, neither sectioned nor
resumable — so a deadline checked between sections cannot bound a tick. Leftover budget funds
`agedSweep`, `evictUnpinned` and `log.age()`, starved exactly when copies are slow, which is when pins
and log pressure peak. `UNKNOWN` exits only on `ChunkEvent.Load`, so a loaded-but-no-longer-ticking
chunk never re-enters; `BROKEN` merges a data fault (fixed by `rebind`) with a code fault for which
`rebind` is wrong. Both keep index rows and metadata without progress.

**Violated / proof fails.** Core #7, D-5, P-2, P-4. P-2 quantifies over calls that copy a stale section;
a call that cannot start and a watch parked in `UNKNOWN`/`BROKEN` fall outside the quantifier.

**Fix.** Budget `(sections, payloads, entities, bytes)` with per-pass caps and a resumable payload/entity
pass; no clock in the pump; `UNKNOWN` re-enters on load or first ticking tick, `BROKEN_DATA` on
`rebind`, `BROKEN_CODE` on operator action, each with a retry count and its own finding.

## Rulings for the lead

1. **One refresh owner, one atomicity rule.** W3 owns `refresh(watch) → Ready | Deferred`, called by W1
   at submit; the tick hook pre-warms only. Reject W1's independent `refresh(key)`.
2. **One freshness predicate.** `captureRevision()` is the only revision source; payload and entity
   revisions join `stale()`; replace `intersects(sections, …)` with a per-watch `dirtyEpoch` never aged
   below the check horizon; delete stored `DIRTY` and P-3 term (2); overflow means unlocalised
   recapture.
3. **Settle hook set and budget unit before coding.** Only `Level.blockEntityChanged` for payloads, plus
   `LevelChunk.setBlockState`, entity join/leave with a footprint-set test, and a `markPayload` duty on
   `16` §4.7's restore path; replace `nanoTime` and "4 sections / 32 KiB" with work units.
