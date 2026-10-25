import type { Stage } from './types';

export type StepState = 'done' | 'current' | 'todo' | 'failed' | 'skipped' | 'warn';
export type StageKey = 'PROBE' | 'FORM' | 'SETTLE';

export interface PipelineStep {
  id: string;
  no: string;
  name: string;
  does: string;
  why: string;
  omit: string;
  code: string;
  /** How each stage plan treats this step. */
  inStage: Record<StageKey, 'run' | 'skip' | 'partial'>;
}

export const PIPELINE_STEPS: PipelineStep[] = [
  {
    id: 'capture',
    no: '01',
    name: 'Capture',
    does: 'Freeze the read closure on the server thread; one server-thread call, getChunkNow, never Level.getChunk.',
    why: 'The check runs on a worker and may not read a live world.',
    omit: 'A half-changed world is read, and the same request produces a different verdict on every run.',
    code: 'capture → SnapshotLease',
    inStage: { PROBE: 'run', FORM: 'run', SETTLE: 'run' },
  },
  {
    id: 'expand',
    no: '02',
    name: 'Expand',
    does: 'blueprint.expand(stage) emits section computations → barrier → aggregates/derivers in Kahn order → checks.',
    why: 'One fixed order and one fixed read closure per stage; ties by admission ascending, specificity descending, declaration order.',
    omit: 'Results depend on execution order, the read closure is unknown, and no cache key can be formed.',
    code: 'SC-CHK-0102 on a cycle',
    inStage: { PROBE: 'partial', FORM: 'run', SETTLE: 'run' },
  },
  {
    id: 'section',
    no: '03',
    name: 'Section pass',
    does: 'Each SECTION computation runs once per section in region ∩ section and contributes a partial.',
    why: 'A large structure is split into sections that parallelise and cache independently.',
    omit: 'One walk over the whole structure: no parallelism, no incrementality, no per-section retry.',
    code: 'draft.forSection(key).contribute(...)',
    inStage: { PROBE: 'skip', FORM: 'run', SETTLE: 'run' },
  },
  {
    id: 'fold',
    no: '04',
    name: 'Barrier fold',
    does: 'Sort contributions by SectionKey (x, z, y) and combine left-to-right with merge.combine.',
    why: 'Cross-section criteria must be composed before any check reads them.',
    omit: 'Each section judges alone and the attempt reports mutually contradictory conclusions.',
    code: 'no identity; a refusal makes the key absent',
    inStage: { PROBE: 'skip', FORM: 'run', SETTLE: 'run' },
  },
  {
    id: 'aggregate',
    no: '05',
    name: 'Aggregate',
    does: 'AGGREGATE computations read folded values once and put the derived key; no world reads, no further contributions.',
    why: 'A complex value is computed once and shared by every predicate that needs it.',
    omit: 'Every predicate re-walks 100 cells and two predicates can compute different values for the same fact.',
    code: 'SC-CTX-0107 on two writers',
    inStage: { PROBE: 'skip', FORM: 'run', SETTLE: 'run' },
  },
  {
    id: 'judge',
    no: '06',
    name: 'Judge',
    does: 'focus.take → Found or Missing; only Found calls expectation.test. The attempt folds replies.',
    why: 'Predicates judge one typed subject and never scan the world, so they compose, cache and reuse.',
    omit: 'Predicates own world reads; no combinator, no read scope, no billing.',
    code: 'UNREADABLE > MISMATCH > ABSTAIN > MATCH',
    inStage: { PROBE: 'run', FORM: 'run', SETTLE: 'run' },
  },
  {
    id: 'publish',
    no: '07',
    name: 'Publish',
    does: 'One ContextDraft per stage boundary, published against the baseRevision it read.',
    why: 'Context values are versioned, and a stage frame is immutable once published.',
    omit: 'A slow old computation overwrites a newer result and readers cannot tell which is current.',
    code: 'SC-CTX-0108 when the base moved',
    inStage: { PROBE: 'run', FORM: 'run', SETTLE: 'run' },
  },
  {
    id: 'settle',
    no: '08',
    name: 'Settle',
    does: 'Derive ClaimRows from Found subjects and each bay’s mode × scope; one CAS replaces all rows of (owner, siting).',
    why: 'Two structures must not both own one cell, and Formed must exist only after Settled.',
    omit: 'Two overlapping furnaces both report Formed and the second silently steals the first’s cells.',
    code: 'SC-CLM-0002 / Undecided(LEDGER_MOVED)',
    inStage: { PROBE: 'skip', FORM: 'skip', SETTLE: 'run' },
  },
];

export interface StageSpec {
  id: StageKey;
  arm: string;
  does: string;
  detail: string;
  color: 'info' | 'ok' | 'warn';
}

export const STAGES: StageSpec[] = [
  {
    id: 'PROBE',
    arm: 'Admitted',
    does: 'Cheap pass: bounds, anchor, census.',
    detail: 'No claims and no instance id. derive() refuses an Admitted result.',
    color: 'info',
  },
  {
    id: 'FORM',
    arm: 'Admitted',
    does: 'Assert the bays and run the computations.',
    detail: 'Still no claims. A structure that passes FORM is admissible, not formed.',
    color: 'info',
  },
  {
    id: 'SETTLE',
    arm: 'Formed',
    does: 'Judge, then settle claims in one ledger transaction.',
    detail: 'Formed carries claims, generation, contextRevision and ledgerRevision; only this stage can produce it.',
    color: 'ok',
  },
];

export type ToggleId = 'mismatch' | 'refused' | 'budget' | 'stale' | 'conflict';

export interface PipelineToggle {
  id: ToggleId;
  label: string;
  step: string;
  note: string;
}

export const PIPELINE_TOGGLES: PipelineToggle[] = [
  { id: 'mismatch', label: 'World mismatch', step: 'judge', note: 'One Check returns MISMATCH → Failed, even beside unreadable cells.' },
  { id: 'refused', label: 'Refused section', step: 'section', note: 'A section slot is Refused → key absent → reply UNREADABLE.' },
  { id: 'budget', label: 'Budget exhausted', step: 'aggregate', note: 'The instance budget refuses a unit → SC-ENG-0001 → Undecided(BUDGET_EXHAUSTED).' },
  { id: 'stale', label: 'Stale publish', step: 'publish', note: 'The base revision moved → SC-CTX-0108, no frame written → Undecided(DIRTY_UNSTABLE).' },
  { id: 'conflict', label: 'Claim conflict', step: 'settle', note: 'An incumbent holds an EXCLUSIVE cell → Failed(SC-CLM-0002).' },
];

export interface Verdict {
  arm: 'Admitted' | 'Formed' | 'Failed' | 'Undecided';
  reason?: string;
  code: string;
  detail: string;
  state: 'ok' | 'fail' | 'unknown' | 'info';
  atStep: string;
}

export const VERDICT_RULES = [
  'Failed ⇔ mismatch ≥ 1 ∨ claimConflict ≥ 1. Refutation outranks ignorance.',
  'Undecided ⇔ no mismatch, no conflict, and unreadable ∨ abstain ∨ untested ∨ no judging unit ∨ cancelled ∨ budget refused.',
  'Formed ⇔ no mismatch, no conflict, no unreadable, no abstain, no untested, and at least one judging unit.',
  'Reason precedence: CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS.',
  'Only SETTLE + Settled produces Formed. PROBE and FORM success produce Admitted.',
];

export const PHASE_NOTES: Record<StageKey, string> = {
  PROBE: 'PROBE reads bounds and the anchor, runs the census, and stops. No section pass, no claims.',
  FORM: 'FORM runs sections, the fold, aggregates and the bay checks, then publishes the FORM frame.',
  SETTLE: 'SETTLE re-runs the plan against the snapshot, folds, publishes, then settles claims once.',
};

export const PIPELINE_STAGE_ORDER: Stage[] = ['DESIGN', 'PROBE', 'FORM', 'SETTLE', 'STALE'];

/* ---------- scenario playback: run the eight steps ---------- */

export type PipelineScenarioId = 'formed' | 'refused-section' | 'budget' | 'conflict';

export interface PipelineScenario {
  id: PipelineScenarioId;
  label: string;
  note: string;
  steps: number;
  stage: StageKey;
  toggles: Record<ToggleId, boolean>;
  /** Last step the token may reach before the verdict stops the run. */
  stopAt: number;
}

const noToggles: Record<ToggleId, boolean> = {
  mismatch: false,
  refused: false,
  budget: false,
  stale: false,
  conflict: false,
};

export const PIPELINE_SCENARIOS: PipelineScenario[] = [
  {
    id: 'formed',
    label: 'SETTLE · Formed',
    note: 'The happy path. Capture, expand, sections, fold, aggregate, judge, publish, then one ledger CAS returns Settled.',
    steps: 8,
    stage: 'SETTLE',
    toggles: noToggles,
    stopAt: 7,
  },
  {
    id: 'refused-section',
    label: 'SC-FOC-0301 · Undecided',
    note: 'A section slot is refused. The contributions stop at the barrier, mymod:volume is absent, and Judge never invokes the predicate.',
    steps: 8,
    stage: 'FORM',
    toggles: { ...noToggles, refused: true },
    stopAt: 5,
  },
  {
    id: 'budget',
    label: 'SC-ENG-0001 · Undecided',
    note: 'The instance budget refuses one aggregate unit. The run stops at step 05 with BUDGET_EXHAUSTED; a re-read is licensed, a repair is not.',
    steps: 8,
    stage: 'FORM',
    toggles: { ...noToggles, budget: true },
    stopAt: 4,
  },
  {
    id: 'conflict',
    label: 'SC-CLM-0002 · Failed',
    note: 'Every step passes, then SETTLE finds an incumbent on an EXCLUSIVE cell. The attempt fails with the incumbent rows attached.',
    steps: 8,
    stage: 'SETTLE',
    toggles: { ...noToggles, conflict: true },
    stopAt: 7,
  },
];
