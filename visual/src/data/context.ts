export interface SectionContribution {
  id: string;
  x: number;
  z: number;
  y: number;
  initial: number;
}

/* Authored order is deliberately not canonical; the fold re-sorts it. */
export const SECTIONS: SectionContribution[] = [
  { id: 's-a', x: 1, z: 0, y: 0, initial: 32 },
  { id: 's-b', x: 0, z: 1, y: 0, initial: 20 },
  { id: 's-c', x: 0, z: 0, y: 0, initial: 48 },
];

export interface ContextKeySpec {
  id: string;
  type: string;
  retention: 'ATTEMPT' | 'OUTCOME' | 'CONTEXT';
  merge: string | null;
  whenAbsent: string | null;
  note: string;
}

export const CONTEXT_KEYS: ContextKeySpec[] = [
  {
    id: 'mymod:volume',
    type: 'Long',
    retention: 'CONTEXT',
    merge: 'sum',
    whenAbsent: null,
    note: 'Three section contributions fold left-to-right. A refused section makes the whole key absent; there is no short sum.',
  },
  {
    id: 'mymod:capacity',
    type: 'Long',
    retention: 'CONTEXT',
    merge: null,
    whenAbsent: null,
    note: 'Single writer: an aggregate reads the folded volume and puts 4 × volume once, after the barrier.',
  },
  {
    id: 'mymod:heat',
    type: 'Long',
    retention: 'CONTEXT',
    merge: 'max',
    whenAbsent: '0',
    note: 'whenAbsent is honoured only when every section was readable and nothing contributed; a refusal never becomes 0.',
  },
];

export const SECTION_ORDER = 'SectionKey (x, z, y)';
export const KEY_ORDER = 'ContextKey (id, type name)';

export const TIERS = [
  { tier: 'BLUEPRINT', stage: 'DESIGN', producer: 'blueprint constants', persisted: true },
  { tier: 'PROBE', stage: 'PROBE', producer: 'cheap census only', persisted: false },
  { tier: 'FORM', stage: 'FORM', producer: 'section fold + aggregates', persisted: false },
  { tier: 'SETTLE', stage: 'SETTLE', producer: 'settled outcome + capacity', persisted: true },
];

export const FOLD_RULES = [
  'Contributions are sorted by SectionKey (x, z, y) and combined left-to-right with merge.combine.',
  'The fold starts at the first present contribution. There is no identity element and no zero seed.',
  'Any refused section in the region makes the key absent; a short sum is unrepresentable.',
  'Parallel section passes are allowed: the fold order is fixed, so the value is a function of the contribution set, not the schedule.',
];

export const ABSENCE_RULES = [
  {
    state: 'refused',
    verdict: 'Missing',
    code: 'SC-FOC-0301',
    body: 'One section is Refused. draft.refuse(key) is called; the key is absent at the barrier; require() returns Missing; the predicate is never invoked and reports UNREADABLE.',
  },
  {
    state: 'declined',
    verdict: 'whenAbsent',
    code: 'declared value',
    body: 'Every section was readable and no contribution was produced: draft.declined(key). Only here does whenAbsent apply.',
  },
  {
    state: 'empty',
    verdict: 'Missing',
    code: 'no declared fallback',
    body: 'Same readable/declined path, but a key with whenAbsent == null stays absent. Absence is a fact, not a zero.',
  },
  {
    state: 'stale',
    verdict: 'Missing',
    code: 'SC-CTX-0108',
    body: 'publish() with a baseRevision the store has moved past writes nothing. The newer frame stays intact.',
  },
];

export const FRAME_ORDER_NOTE =
  'A reader at tier t sees the newest frame at or below t. A PROBE reader returns null for a FORM value; it never looks above its tier.';

export const CONTEXT_READOUT_LABEL = 'Folded volume';

/* ---------- scenario playback: replay the fold ---------- */

export type ContextScenarioId = 'standard' | 'refused-middle' | 'whenAbsent';

export interface ContextScenarioSetup {
  id: ContextScenarioId;
  label: string;
  note: string;
  refused: string[];
  emptyRegion: boolean;
  steps: number;
}

export const CONTEXT_SCENARIOS: ContextScenarioSetup[] = [
  {
    id: 'standard',
    label: 'Three sections, one fold',
    note: 'Three contributions arrive out of authored order; the fold sorts by SectionKey (x, z, y), adds them one at a time, then lights the tier frames.',
    refused: [],
    emptyRegion: false,
    steps: 8,
  },
  {
    id: 'refused-middle',
    label: 'A refused middle section',
    note: 'The second canonical section is refused. The fold aborts at that contribution, mymod:volume becomes Missing, and no predicate is ever called.',
    refused: ['s-b'],
    emptyRegion: false,
    steps: 3,
  },
  {
    id: 'whenAbsent',
    label: 'Declined, not refused',
    note: 'Every section is readable and none contributes: volume stays Missing, while heat honours whenAbsent = 0. This is the only path where a fallback applies.',
    refused: [],
    emptyRegion: true,
    steps: 5,
  },
];
