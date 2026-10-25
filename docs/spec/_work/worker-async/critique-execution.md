# Critique — W1 `execution-model.md`

> Against W2/W3/W4, `11`, `16`, `02`. Severity order: scenario, violated invariant and why the proof
> fails, fix.

## R1 — Accept gate lacks W3's change-log term: a `Formed` settles on a world that already moved

**Scenario.** FORM dispatched at `v=41`. Mid-run a chest is emptied (`setChanged`) and a bay block
replaced. W3's pump has not run, so `currentVersion` is still 41, `SnapshotId` matches, epoch
unchanged; the result is `Formed`, W1 accepts (P-2), `SavedClaims.settle` persists claims for a wall
that is gone.

**Proof failure.** W3 P-3 requires `log.intersects(sections, baseEpoch) == FRESH` (no mark at all
since submit) and states that "an unrefreshed mark leaves `version` untouched while the snapshot is
already stale". P-2 proves "result matches the slot token", not "snapshot is current"; the difference
is dirty-but-not-refreshed, and W1 owns the only settle call.

**Fix.** Capture `ChangeLog.epoch()` at dispatch; accept = token ∧ `intersects(readSet().cells(),
baseEpoch) == FRESH`, before `facts()` is read. Failure ⇒ `DROPPED(STALE_VERSION)`, slot dirty.

## R2 — `submit` always refreshes and captures: STALE loses its snapshot, every event pays a capture

**Scenario.** Flow is `permit → refresh → capture → pin → execute` for every submit, no exception for
`Stage.STALE`. A 20×/s clock forces 20 full-footprint captures per second, and
`MatchRequest.at(formed)` (`11` §4.6, "Pinned, same snapshot") can never be built: the snapshot the
prior `Formed` pinned was just superseded.

**Proof failure.** `11` §4.6 and P-6 reuse prior verdicts *at the same snapshot*, and W1's CE-1
promises "one run, ≤1 discard" but delivers one run plus one capture and copy set per window. P-5 is
silent on capture, and capture is hard-wired as a submission step, so nothing covers "reuse when
nothing intersects".

**Fix.** Reuse the slot's snapshot when `change ∩ refreshedSections = ∅` and it is younger than the
sweep horizon; capture only on first submit, intersecting change, or completed `REFRESHING`.

## R3 — Supersede is cooperative but the slot admits the replacement: two live tasks per key, two settles

**Scenario.** Two `/cumulus build` 200 ms apart. Submit #2 cancels #1 and proceeds; #1 is inside a
`CUSTOM` step, folds `CANCELLED` only at the next boundary, and posts. Both hold the same snapshot
when the second capture coalesces (W2 `SnapshotRegistry`, one tick) or the refresh is a no-op (W2
Q2: content-equal reuse keeps object *and* revision), so both pass W-4. #1 settles; #2 settles at the
same `expectedRevision`; CAS answers `RevisionMoved`, which W1 maps to
`Undecided(LEDGER_UNAVAILABLE)` + `SC-CLM-0004`, "no retry" — the newest correct answer reported as a
ledger failure.

**Proof failure.** W-3 ("at most one task per `CheckKey` is `QUEUED`/`RUNNING`/`POSTED`") and W-5: a
cooperative cancel cannot retire a `RUNNING` task, so the slot must refuse the replacement until the
old task is terminal. P-1 proves a per-task property and asserts a per-key one; nothing orders #2's
`execute` after #1's `finish`, and W-4 saves the value only via a capture bump W2's coalescing can
remove.

**Fix.** Per-slot `submitTicket`; accept requires `ticket == latestTicket`. A superseding submit
either waits for the prior task to reach `POSTED`/`SETTLED` (coalesce, as AUTO does) or declares
preemption abortive and forbids a shared snapshot.

## R4 — Cancellation is cancel-only and step-boundary-only: one wedged step holds a thread *and* a permit forever

**Scenario.** An addon `Expectation` loops without reading. The 10 s watchdog sets
`cancel(DEADLINE)`; the kernel polls only between steps and never sees it. At 20 s the key becomes
`SLOT_BROKEN`; the thread is still there. With `threads = 2`, two such keys hold both threads and two
of four permits; two more admitted tasks take the rest, and every later submit (`COMMAND` included)
answers `DEFERRED` for the server's life.

**Proof failure.** W-5 (every permit completes exactly once) and the liveness W-7 depends on: "a host
deadline may only cancel" leaves no reclamation path. P-4 derives leak-freedom from `finally`, which
runs only if the call returns; "no `Thread.stop`, no pool growth" is what makes the wedge permanent.

**Fix.** (i) Kernel polls `cancel` every N reads inside long steps. (ii) The watchdog may retire and
replace a worker after a second deadline, marking the scheduler degraded. (iii) Per-class permits, so
a wedged AUTO cannot starve INTERACTIVE.

## R5 — `FrozenLedger.readBox` makes a class of blueprints permanently `Undecided`, only on the async path

**Scenario.** A `SEAM` plan whose counterparts pair across the footprint edge, or a `CUSTOM` step
asking `claimsAt` one cell outside. `readBox` is `ClaimPlanner.plan(bp, siting).footprint()`, so the
query is outside it, `claimsIn` answers `null`, and `11` §4.1 maps `null` to
`Undecided(LEDGER_UNAVAILABLE)`: no settle, no retry, forever. Under the sync fallback (which W1
never says wraps its ledger) the same blueprint reads live and forms.

**Proof failure.** `11` P-2/P-3 on the async path, and W1's P-5, since the verdict depends on which
executor path ran. §2 asserts "dispatch reads exactly the box `SETTLE` will query", but `SETTLE` is
not the only reader: anchors, seams and CUSTOM read claims too, and `11` Q-5 still lists seam dilation.

**Fix.** `readBox` = union of every cell the plan, anchor resolution and settle scope can query,
closed under seam dilation; a query outside it is a host assertion failure, not
`LEDGER_UNAVAILABLE`. Freeze identically on the sync path, or drop it.

## R6 — Memory is bounded per snapshot, never in aggregate; the OOM handler cannot work

**Scenario.** Four admitted snapshots at the 64 MiB ceiling, plus W2's `maxInFlight + maxInstances`
retained `Formed` views, plus store copies. An allocation fails in a worker; W1's handler "drop pin,
release permit, pre-built bundle" allocates a finding bundle and a `MatchResult` on a JVM that just
failed to allocate, while the server thread allocates the next capture.

**Proof failure.** W-5 and the bounded-memory premise behind CE-1, which names OOM as the failure to
prevent and does not. `maxSnapshotBytes` is a per-snapshot admission check read as a memory bound:
nothing sums pinned bytes across tasks or counts retained views — and a retained `Formed` carries a
pinned `WorldView` (`11` §3) that W1's `finish` claims to clear, so the pin either dies early on
evictable bytes or is never released. Per-task OOM handling assumes one confined allocation.

**Fix.** One aggregate `pinnedBytes` reservation, released only at the true end of ownership (task
`finish` for `DROPPED`/`FAILED`, `InstanceRecord` re-form/removal for `SETTLED`); refusal is
`DEFERRED` with the slot dirty. OOM marks the scheduler degraded, all keys `BROKEN`, and is rethrown.
Replace `maxSnapshotBytes`-for-COMMAND `DEFERRED(DEFERRED_BACKPRESSURE)` with `TooLarge` plus a
defined escalation.

## R7 — AUTO's retry has no owner, and the FIFO puts INTERACTIVE behind AUTO work

**Scenario.** An AUTO watcher submits during an INTERACTIVE burst, gets `DEFERRED`, slot "stays
dirty, a later event re-submits". The clock stops; no mark arrives. W3's pump is the only tick hook
and its D-3 says "refresh never starts a check". `autoReprobeTicks = 10` has no caller: the structure
is never detected. `ArrayBlockingQueue` is FIFO, so two admitted AUTO tasks ahead of a player's probe
delay it by two full matches; `Priority` gates admission only.

**Proof failure.** The `Priority` contract implies AUTO is deferred, not dropped, and W1 states no
interactive latency bound although its admission design implies one. P-1 proves the queue cannot
overfill, not that a deferred AUTO is re-armed: the retry is asserted as a property with no component
performing it, and W3 is forbidden to.

**Fix.** W1 owns a bounded re-probe queue drained from its own tick hook, or D-3 is reworded to grant
the pump `redispatchDeferred()`. Admission becomes per class: AUTO holds at most `threads - 1`
permits; INTERACTIVE reserves one and publishes a worst-case latency in work units.

## R8 — `CheckKey` embeds a `Siting` the kernel must derive; one slot serves PROBE, FORM and SETTLE

**Scenario.** A wand click is `Place.Open(origin)`, and `11` §4.2 says orientation is "derived by the
kernel, not minted by the host". W1 requires `submit` with `CheckKey(world, owner, siting)` already
built, so the host must pre-resolve (contradicting `11` §4.2) or key by origin — then two origins
resolving to one siting are two slots, i.e. two concurrent checks of one instance where W-3 matters
most. Second: PROBE→FORM→SETTLE share a key, so a supersede of the PROBE kills the chain W1 defines.

**Proof failure.** W-3, the `11` §4.2 ownership rule and W1 §3's own chain contract. P-1/P-2 assume
the key is an instance identity available at submit; for the commonest request shape it is not
computable, and it is too coarse for the chain.

**Fix.** Key admission by `(world, owner, Place)` with `Siting` resolved in-task and recorded at
acceptance; or split `InstanceKey` from `ChainKey`, stating that supersede never cancels a different
stage of the same chain.

## Questions the lead must rule before integration

1. **Preemptive or coalescing supersede?** R3 and R8 cannot both be settled by prose: if a `COMMAND`
   submit may cancel and immediately replace a running task, W-3 is false and the accept token must
   be a submit ticket; if it may not, `Priority` buys admission only and INTERACTIVE has no freshness
   guarantee.
2. **Who owns the accept gate, and does the sync path use the same inputs?** R1 and R5 each make
   async and sync disagree. One gate function, one owner (W1), stated once for both, before `11`
   §4.6 and `16` §4.6 are rewritten.
3. **Is the scheduler bounded by threads or by bytes?** R4 and R6 end in the same undecided number:
   is `maxSnapshotBytes` per snapshot (current text) or an aggregate reservation, and may a wedged
   task cost a worker thread permanently? Until ruled, `threads`, `backlog`, `maxInFlight` and the
   deadline are four numbers chosen against three accounting models.
