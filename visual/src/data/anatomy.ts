export type Coverage = 'CUBIC' | 'SUBJECT' | 'PRESENCE';
export type ClaimScopeName = 'Owned' | 'Occupied' | 'Declared' | 'Audit';

import type { DemoScenario } from './types';

export interface BayAnatomy {
  id: string;
  name: string;
  cells: number;
  coverage: Coverage;
  mode: 'EXCLUSIVE' | 'SHARED' | 'RESERVED';
  scope: ClaimScopeName;
  subject: string;
  stage: string;
  focus: string;
  owned: string;
  note: string;
}

export const FURNACE = {
  id: 'mymod:huge_furnace',
  bounds: [0, 0, 0, 7, 6, 7] as const,
  cells: 294,
  cavity: 100,
  shell: 194,
  mouth: 2,
  cavityBox: 'Cuboid.of(1, 1, 1, 5, 4, 5)',
  capacity: '4 × 100 = 400',
  boundsLabel: '[0, 0, 0, 7, 6, 7]',
};

export const FURNACE_BAYS: BayAnatomy[] = [
  {
    id: 'wall',
    name: 'wall',
    cells: 194,
    coverage: 'CUBIC',
    mode: 'EXCLUSIVE',
    scope: 'Owned',
    subject: 'OccupantSnapshot',
    stage: 'FORM',
    focus: 'Focuses.occupant(cell) × 194',
    owned: '194 cells',
    note: 'The shell is a holed box, so it is not one bay: lowering emits one bay per face box, ids #box@x,y,z:sx,sy,sz. 294 − 100 = 194.',
  },
  {
    id: 'mouth',
    name: 'mouth',
    cells: 2,
    coverage: 'SUBJECT',
    mode: 'EXCLUSIVE',
    scope: 'Occupied',
    subject: 'DoorView',
    stage: 'FORM',
    focus: 'Focuses.door(origin)',
    owned: '2 non-air bound footprints',
    note: 'Both halves are one subject. SUBJECT asserts the bay cells equal the union of bound footprints; Occupied owns the non-air footprint, not the empty cell. Shown here as the two-cell door variant; the worked example’s mouth is an iron-bars CUBIC bay.',
  },
  {
    id: 'interior',
    name: 'interior',
    cells: 100,
    coverage: 'CUBIC',
    mode: 'EXCLUSIVE',
    scope: 'Owned',
    subject: 'Long (capacity) + OccupantSnapshot (air)',
    stage: 'FORM → SETTLE',
    focus: 'Focuses.context(CAPACITY) / occupant per cell',
    owned: '100 cells',
    note: 'The cavity owns empty cells. CUBIC is asserted by per-cell air checks; a separate Long check reads the folded capacity. The capacity computation is a FORM aggregate; its reader may run at SETTLE.',
  },
];

export const REJECTED_BAY = {
  coverage: 'PRESENCE' as Coverage,
  scope: 'Owned' as ClaimScopeName,
  code: 'SC-CHK-0106',
  title: 'Why the interior is not PRESENCE',
  body: 'PRESENCE asserts no cells; it is a presence/state criterion, legal only with Audit or Declared. Pairing PRESENCE with Owned would claim 100 cells while deciding none — SC-CHK-0106. The cavity must be CUBIC + Owned precisely because its 100 cells are empty.',
};

export const PRESENCE_RULE = [
  { coverage: 'CUBIC', legal: ['Owned', 'Occupied', 'Declared', 'Audit'], asserts: 'Every cell of bay.cells is decided' },
  { coverage: 'SUBJECT', legal: ['Occupied', 'Declared', 'Audit'], asserts: 'bay.cells equals the union of bound footprints' },
  { coverage: 'PRESENCE', legal: ['Audit', 'Declared'], asserts: 'No cell asserted; a presence/state criterion' },
];

export interface GiantCheckReason {
  no: string;
  title: string;
  code: string;
  body: string;
}

export const GIANT_CHECK_REASONS: GiantCheckReason[] = [
  {
    no: '01',
    title: 'The subject type differs',
    code: 'SC-CHK-0101',
    body: 'One Check<T> has exactly one T. The shell judges 194 OccupantSnapshots, the mouth judges one DoorView with two halves, and the capacity is a Long. There is no T that is all three; forcing one is the constructor error, not a workaround.',
  },
  {
    no: '02',
    title: 'The stage differs',
    code: 'Stage: FORM vs SETTLE',
    body: 'A door and an air cell are knowable at FORM. The cavity volume must be contributed per section, folded at the barrier, then scaled. A single check declares one stage; run it early and the value is Missing, run it late and the shell is not protected until SETTLE.',
  },
  {
    no: '03',
    title: 'The ownership differs',
    code: 'ClaimScope',
    body: 'The shell owns its solid cells, the mouth owns non-air bound footprints (Occupied), and the cavity must Own 100 empty cells. One scope cannot express three owned sets; SUBJECT + Owned is SC-CHK-0106, and Occupied would leave the cavity unprotected.',
  },
  {
    no: '04',
    title: 'Chunking needs boundaries',
    code: 'SC-BAY-0107',
    body: 'Sections, read closures and cache entries are keyed by rectangular regions. One predicate over 294 cells has no section boundary, cannot be split across workers, and cannot be cached. CUBIC coverage must also prove that decision cells cover bay.cells at DESIGN.',
  },
];

export const CELL_LEDGER = [
  { label: 'Bounds 7 × 6 × 7', value: 294, note: 'half-open volume' },
  { label: 'Cavity 5 × 4 × 5', value: 100, note: 'empty, Owned' },
  { label: 'Shell', value: 194, note: '294 − 100' },
  { label: 'Mouth', value: 2, note: 'inside the shell face' },
];

export const ANATOMY_READOUTS = [
  { label: 'Bounds volume', value: '294', unit: 'cells', state: 'active' as const, primary: true },
  { label: 'Wall', value: '194', unit: 'cells' },
  { label: 'Interior', value: '100', unit: 'cells' },
  { label: 'Mouth', value: '2', unit: 'cells' },
];

/* ---------- scenario playback: how one giant check breaks ---------- */

export type BayId = BayAnatomy['id'];
export type AnatomyScenarioId = 'giant-check' | 'legal-split';
export type AnatomyRegion = BayId | 'all' | 'split';

export interface AnatomyStep {
  id: string;
  reasonId?: string;
  title: string;
  code: string;
  panels: BayId[];
  region: AnatomyRegion;
}

export const ANATOMY_SCENARIOS: DemoScenario<AnatomyScenarioId>[] = [
  {
    id: 'giant-check',
    label: 'How one giant Check breaks',
    note: 'Four contradictions accrue one at a time — subject type, stage, ownership, chunking — then the elevation splits into the three bays that can actually be judged.',
    steps: 5,
  },
  {
    id: 'legal-split',
    label: 'The legal split',
    note: 'The same cavity read as three bays: each step lights one bay and states the coverage, scope and stage it alone can carry.',
    steps: 3,
  },
];

export const ANATOMY_GIANT_STEPS: AnatomyStep[] = [
  {
    id: 'subject',
    reasonId: '01',
    title: 'T mismatch',
    code: 'SC-CHK-0101',
    panels: ['wall', 'mouth', 'interior'],
    region: 'all',
  },
  {
    id: 'stage',
    reasonId: '02',
    title: 'Stage mismatch',
    code: 'Stage: FORM vs SETTLE',
    panels: ['wall', 'mouth', 'interior'],
    region: 'all',
  },
  {
    id: 'ownership',
    reasonId: '03',
    title: 'Ownership mismatch',
    code: 'ClaimScope',
    panels: ['wall', 'mouth', 'interior'],
    region: 'all',
  },
  {
    id: 'chunking',
    reasonId: '04',
    title: 'Chunking needs boundaries',
    code: 'SC-BAY-0107',
    panels: ['wall', 'mouth', 'interior'],
    region: 'all',
  },
  {
    id: 'split',
    title: 'Split into three bays',
    code: '3 bays · 194 + 2 + 100',
    panels: ['wall', 'mouth', 'interior'],
    region: 'split',
  },
];

export const ANATOMY_LEGAL_STEPS: AnatomyStep[] = [
  {
    id: 'wall',
    title: 'wall · CUBIC + Owned',
    code: 'FORM · 194 cells',
    panels: ['wall'],
    region: 'wall',
  },
  {
    id: 'mouth',
    title: 'mouth · SUBJECT + Occupied',
    code: 'FORM · 2 cells',
    panels: ['mouth'],
    region: 'mouth',
  },
  {
    id: 'interior',
    title: 'interior · CUBIC + Owned',
    code: 'FORM → SETTLE · 100 cells',
    panels: ['interior'],
    region: 'interior',
  },
];
