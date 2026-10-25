import type { DemoScenario, Stage } from './types';

export interface ResultArm {
  id: string;
  name: string;
  state: 'ok' | 'info' | 'fail' | 'unknown' | 'neutral';
  tagline: string;
  when: string;
  fields: { name: string; type: string; note: string }[];
}

export const RESULT_ARMS: ResultArm[] = [
  {
    id: 'admitted',
    name: 'Admitted',
    state: 'info',
    tagline: 'PROBE / FORM passed.',
    when: 'The fold is MATCH at PROBE or FORM. No claims are minted, there is no instance id, and derive() refuses the result.',
    fields: [
      { name: 'stage', type: 'Stage', note: 'PROBE or FORM; never SETTLE.' },
      { name: 'siting', type: 'Siting', note: 'The placement the attempt used.' },
      { name: 'outcome', type: 'MatchOutcome', note: 'What this attempt produced: the view it read and the values it published.' },
      { name: 'lease', type: 'SnapshotLease', note: 'Child handle; AutoCloseable.' },
      { name: 'findings', type: 'FindingBundle', note: 'Warnings and notes, never a blocking error.' },
    ],
  },
  {
    id: 'formed',
    name: 'Formed',
    state: 'ok',
    tagline: 'SETTLE passed and claims settled.',
    when: 'The fold is MATCH at SETTLE and the ledger transaction returned Settled. This is the only arm that is a structure.',
    fields: [
      { name: 'id', type: 'InstanceId', note: 'The instance the host registers.' },
      { name: 'siting', type: 'Siting', note: 'Frozen with the claims.' },
      { name: 'outcome', type: 'MatchOutcome', note: 'The outcome plus the context the attempt published.' },
      { name: 'claims', type: 'ClaimSet', note: 'Rows and the revision they settled at.' },
      { name: 'generation', type: 'long', note: 'Increments on every emitted Formed, including a re-emission.' },
      { name: 'contextRevision', type: 'long', note: 'The SETTLE frame revision.' },
      { name: 'ledgerRevision', type: 'long', note: 'The revision a later replacement is measured against.' },
      { name: 'lease', type: 'SnapshotLease', note: 'AutoCloseable; closing releases child handles.' },
      { name: 'findings', type: 'FindingBundle', note: 'Non-blocking findings only.' },
    ],
  },
  {
    id: 'failed',
    name: 'Failed',
    state: 'fail',
    tagline: 'The world really does not satisfy.',
    when: 'At least one MISMATCH, or a ClaimConflict at SETTLE. A repair may change the world; this is not a soft failure.',
    fields: [
      { name: 'findings', type: 'List<Finding>', note: 'One or more; codes come from the failing unit.' },
    ],
  },
  {
    id: 'undecided',
    name: 'Undecided',
    state: 'unknown',
    tagline: 'We stopped, or learned nothing.',
    when: 'No mismatch and no conflict, but unreadable / abstain / untested / no judging unit / cancelled / budget refused / ledger moved. A re-read is licensed.',
    fields: [
      {
        name: 'reason',
        type: 'UndecidedReason',
        note: 'CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS.',
      },
      { name: 'findings', type: 'List<Finding>', note: 'The findings that explain the stop, when there are any.' },
      { name: 'partial', type: 'MatchOutcome?', note: 'What was produced before the stop, if anything.' },
      { name: 'retry', type: 'RetryToken?', note: 'Present for LEDGER_MOVED and some stop reasons.' },
    ],
  },
  {
    id: 'deferred',
    name: 'Deferred',
    state: 'neutral',
    tagline: 'Nothing executed.',
    when: 'The request never ran: capacity, a closed lease or a policy refusal. Terminal with a retry token; only AUTO re-submits automatically.',
    fields: [
      { name: 'token', type: 'RetryToken', note: 'The only way back in; there is no partial outcome.' },
      { name: 'findings', type: 'List<Finding>', note: 'Why no unit was dispatched.' },
    ],
  },
];

export const ARM_EDGES = [
  { from: 'request', to: 'admitted', label: 'PROBE/FORM · MATCH', dash: false },
  { from: 'request', to: 'failed', label: 'MISMATCH ≥ 1', dash: false },
  { from: 'request', to: 'undecided', label: 'UNREADABLE / BUDGET / STALE', dash: false },
  { from: 'request', to: 'deferred', label: 'no dispatch', dash: true },
  { from: 'admitted', to: 'formed', label: 'later SETTLE · Settled', dash: true },
  { from: 'request', to: 'formed', label: 'SETTLE · Settled', dash: false },
];

export interface FindingComponent {
  key: string;
  type: string;
  example: string;
  note: string;
}

export const FINDING_EXAMPLE = {
  id: 'F-0004',
  code: 'SC-CHK-0106',
  severity: 'ERROR',
  message: 'SUBJECT coverage cannot own cells',
  stage: 'DESIGN' as Stage,
  labels: [{ role: 'PRIMARY', frame: 'DEFINITION', text: 'bay "mouth" declares SUBJECT + Owned' }],
  notes: ['Coverage=SUBJECT · ClaimScope=Owned'],
  suggestion: 'change ClaimScope to Occupied',
  related: ['F-0003'],
};

export const FINDING_COMPONENTS: FindingComponent[] = [
  {
    key: 'id',
    type: 'FindingId(int)',
    example: 'F-0004',
    note: 'Addressability inside one bundle, for related. Re-minted when a cached finding is reused.',
  },
  {
    key: 'code',
    type: 'FindingCode(Family, int)',
    example: 'SC-CHK-0106',
    note: 'Stable identity: family plus number. A well-formed code with no registry entry still orders and renders.',
  },
  {
    key: 'severity',
    type: 'ERROR | WARNING | INFO',
    example: 'ERROR',
    note: 'Rank 0..2. The first sort key, because a reader triages by how much a finding counts.',
  },
  {
    key: 'message',
    type: 'String',
    example: 'SUBJECT coverage cannot own cells',
    note: 'Prose. Excluded from dedup so a reword or a translation never splits one finding into two.',
  },
  {
    key: 'labels',
    type: 'List<Label>',
    example: 'PRIMARY · DEFINITION · bay "mouth"',
    note: 'Each Label is (role, locus, message). The constructor sorts them; the primary locus is the fourth sort key.',
  },
  {
    key: 'notes',
    type: 'List<Note>',
    example: 'Coverage=SUBJECT · ClaimScope=Owned',
    note: 'Values with no cell of their own. Included in dedup: two findings differing in a note are different facts.',
  },
  {
    key: 'suggestion',
    type: 'Suggestion?',
    example: 'change ClaimScope to Occupied · MACHINE_APPLICABLE',
    note: 'A hint, not a plan. Only the top applicability rung may auto-apply; U10 owns the edit.',
  },
  {
    key: 'related',
    type: 'List<FindingId>',
    example: 'F-0003',
    note: 'Other findings in the same bundle that share a cause. Excluded from dedup.',
  },
  {
    key: 'stage',
    type: 'Stage',
    example: 'DESIGN',
    note: 'When the failure became knowable. The second sort key: DESIGN before PROBE before FORM before SETTLE.',
  },
];

export const SORT_KEYS = [
  { id: 1, label: 'severity', note: 'ERROR 0 < WARNING 1 < INFO 2' },
  { id: 2, label: 'stage', note: 'DESIGN, PROBE, FORM, SETTLE, STALE' },
  { id: 3, label: 'code', note: 'family ordinal, then number' },
  { id: 4, label: 'locus', note: 'DEFINITION 0 < LOCAL/WORLD < KEY 3 < none 4' },
  { id: 5, label: 'insertion', note: 'the attempt append index; unique, last resort' },
];

export const FAMILY_ORDER = [
  'SPC',
  'OCC',
  'EXP',
  'FOC',
  'CLM',
  'BPT',
  'BAY',
  'CHK',
  'DOC',
  'ENG',
  'CTX',
  'DIA',
  'RMD',
  'SPI',
  'MC',
] as const;

export const STAGE_ORDER: Stage[] = ['DESIGN', 'PROBE', 'FORM', 'SETTLE', 'STALE'];

export interface DemoFinding {
  id: string;
  code: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
  stage: Stage;
  frame: 'DEFINITION' | 'LOCAL' | 'WORLD' | 'KEY';
  y: number;
  z: number;
  x: number;
  insertion: number;
  message: string;
}

export const DEMO_FINDINGS: DemoFinding[] = [
  { id: 'F-01', code: 'SC-DOC-0605', severity: 'ERROR', stage: 'DESIGN', frame: 'DEFINITION', y: 0, z: 0, x: 0, insertion: 1, message: 'two tags in one expect object' },
  { id: 'F-02', code: 'SC-CLM-0002', severity: 'ERROR', stage: 'SETTLE', frame: 'WORLD', y: 64, z: -5, x: 10, insertion: 2, message: 'incumbent owns an EXCLUSIVE cell' },
  { id: 'F-03', code: 'SC-CHK-0106', severity: 'ERROR', stage: 'DESIGN', frame: 'DEFINITION', y: 0, z: 0, x: 0, insertion: 3, message: 'SUBJECT coverage cannot own cells' },
  { id: 'F-04', code: 'SC-BAY-0107', severity: 'WARNING', stage: 'DESIGN', frame: 'DEFINITION', y: 0, z: 0, x: 0, insertion: 4, message: 'decision cells do not cover the bay' },
  { id: 'F-05', code: 'SC-FOC-0301', severity: 'ERROR', stage: 'SETTLE', frame: 'KEY', y: 0, z: 0, x: 0, insertion: 5, message: 'refused section makes the key absent' },
  { id: 'F-06', code: 'SC-MC-0404', severity: 'WARNING', stage: 'STALE', frame: 'WORLD', y: 64, z: -5, x: 10, insertion: 6, message: 'discard budget exhausted' },
  { id: 'F-07', code: 'SC-EXP-0100', severity: 'ERROR', stage: 'FORM', frame: 'LOCAL', y: 65, z: 3, x: 2, insertion: 7, message: 'the campfire is not lit' },
  { id: 'F-08', code: 'SC-CTX-0108', severity: 'WARNING', stage: 'PROBE', frame: 'KEY', y: 0, z: 0, x: 0, insertion: 8, message: 'stale base revision' },
];

const familyOf = (code: string) => code.split('-')[1] ?? '';

const familyRank = (code: string) => {
  const idx = FAMILY_ORDER.indexOf(familyOf(code) as (typeof FAMILY_ORDER)[number]);
  return idx === -1 ? 99 : idx;
};

const numberRank = (code: string) => Number(code.split('-')[2] ?? 0);

const severityRank = (s: DemoFinding['severity']) => (s === 'ERROR' ? 0 : s === 'WARNING' ? 1 : 2);

const frameRank = (f: DemoFinding['frame']) =>
  f === 'DEFINITION' ? 0 : f === 'LOCAL' ? 1 : f === 'WORLD' ? 2 : 3;

export const compareFinding = (depth: number) => (a: DemoFinding, b: DemoFinding): number => {
  const keys: number[] = [];
  if (depth >= 1) keys.push(severityRank(a.severity) - severityRank(b.severity));
  if (depth >= 2) keys.push(STAGE_ORDER.indexOf(a.stage) - STAGE_ORDER.indexOf(b.stage));
  if (depth >= 3) keys.push(familyRank(a.code) - familyRank(b.code), numberRank(a.code) - numberRank(b.code));
  if (depth >= 4) {
    keys.push(frameRank(a.frame) - frameRank(b.frame));
    if (a.frame === 'LOCAL' || a.frame === 'WORLD') {
      keys.push(a.y - b.y, a.z - b.z, a.x - b.x);
    } else if (a.frame === 'DEFINITION') {
      keys.push(0, 0, 0);
    } else {
      keys.push(0, 0, 0);
    }
  }
  if (depth >= 5) keys.push(a.insertion - b.insertion);
  for (const k of keys) if (k !== 0) return k;
  return 0;
};

export const sortDemand = (depth: number): DemoFinding[] =>
  [...DEMO_FINDINGS].sort(compareFinding(depth));

export const BUNDLE_NOTE =
  'Dedup is value equality on (severity, stage, code, labels as [(role, locus, source)], notes as [message]). Message, label prose, suggestion and related are excluded.';

export const BUNDLE_CAP_NOTE =
  'After dedup the bundle sorts once, then applies maxPerCode and maxTotal, so a cap truncates the documented order, never a hash order.';

/* ---------- scenario playback: arm path, re-emission, reorder ---------- */

export type ResultsScenarioId = 'arm-path' | 'dirty-unstable' | 'reorder';

export const RESULTS_SCENARIOS: DemoScenario<ResultsScenarioId>[] = [
  {
    id: 'arm-path',
    label: 'Arm transitions',
    note: 'Each step selects one arm and draws the orthogonal path it was reached by: request → bus → arm, or Admitted → later SETTLE → Formed.',
    steps: 5,
  },
  {
    id: 'dirty-unstable',
    label: 'DIRTY_UNSTABLE re-emission',
    note: 'The same outcome is emitted twice. generation increments, ledgerRevision increments, and the outcome digest stays byte-identical.',
    steps: 2,
  },
  {
    id: 'reorder',
    label: 'Reorder by five keys',
    note: 'The bundle sorts by severity, stage, code, primary locus and insertion, one key at a time; rows only highlight, they never fly.',
    steps: 6,
  },
];

export const REEMIT_STEPS = [
  {
    generation: 1,
    contextRevision: 7,
    ledgerRevision: 4,
    outcome: 'outcome 8f31·c0de',
    note: 'First emission after Settled. Claims carry generation 1 and the SETTLE frame revision 7.',
  },
  {
    generation: 2,
    contextRevision: 7,
    ledgerRevision: 5,
    outcome: 'outcome 8f31·c0de',
    note: 'A re-read produced the same outcome and the same context frame; only generation and ledgerRevision moved.',
  },
];
