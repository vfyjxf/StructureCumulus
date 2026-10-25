export type ClaimModeName = 'EXCLUSIVE' | 'SHARED' | 'RESERVED';
export type ClaimScopeOption = 'Owned' | 'Occupied';

import type { DemoScenario } from './types';

export interface StructureSpec {
  id: 'A' | 'B';
  name: string;
  size: number;
  pattern: 'corner' | 'full';
  initialOffset: { x: number; z: number };
  initialMode: ClaimModeName;
  initialScope: ClaimScopeOption;
  note: string;
}

export const GRID = 6;

export const CLAIM_STRUCTURES: StructureSpec[] = [
  {
    id: 'A',
    name: 'mymod:pipe_corner',
    size: 3,
    pattern: 'corner',
    initialOffset: { x: 0, z: 0 },
    initialMode: 'EXCLUSIVE',
    initialScope: 'Owned',
    note: 'A CUBIC 3×3 bay whose non-air footprint is a 4-cell corner. Owned owns all 9; Occupied owns the 4 solid cells.',
  },
  {
    id: 'B',
    name: 'mymod:hatch_frame',
    size: 3,
    pattern: 'full',
    initialOffset: { x: 2, z: 1 },
    initialMode: 'EXCLUSIVE',
    initialScope: 'Owned',
    note: 'A solid 3×3 frame: the footprint is all 9 cells, so Owned and Occupied own the same set.',
  },
];

export const FOOTPRINT: Record<'corner' | 'full', [number, number][]> = {
  corner: [
    [0, 0],
    [1, 0],
    [2, 0],
    [2, 1],
  ],
  full: [
    [0, 0],
    [1, 0],
    [2, 0],
    [0, 1],
    [1, 1],
    [2, 1],
    [0, 2],
    [1, 2],
    [2, 2],
  ],
};

export const MODE_NOTES: { mode: ClaimModeName; rule: string }[] = [
  { mode: 'EXCLUSIVE', rule: 'Conflicts with any other mode on the same cell; the second settle fails.' },
  { mode: 'SHARED', rule: 'Never conflicts with SHARED; two furnaces can both hold the same hatch cell.' },
  { mode: 'RESERVED', rule: 'Reads like SHARED and blocks EXCLUSIVE until the host’s reservation expires.' },
];

export const SCOPE_NOTES: { scope: ClaimScopeOption; rule: string }[] = [
  { scope: 'Owned', rule: 'Owns bay.cells, including empty ones. A cavity must use this.' },
  { scope: 'Occupied', rule: 'Owns the union of non-air bound footprints. The pipe corner asserts 9 cells and owns 4.' },
];

export const CLAIM_RULES = [
  'ClaimRow(cell, mode, owner, siting, bay) is the only row shape; the ledger has no second opinion.',
  'ClaimMatrix.conflicts(a, b) is the only conflict predicate. Rows of one (owner, siting) are excluded there.',
  'settle replaces every row of (owner, siting) in one CAS. A partial replacement does not exist.',
  'Occupied owns non-air bound footprints; Owned owns bay.cells; Declared owns an explicit CuboidSet; Audit owns nothing and reads anywhere.',
  'RevisionMoved is retried once inside the transaction, then Undecided(LEDGER_MOVED) with a retry token.',
  'Uncovered blueprint bounds are SC-CLM-0104 (WARNING); a non-Audit read outside cells ∪ footprint is SC-BAY-0108 at SETTLE.',
];

export const CLAIM_READOUTS = [
  { label: 'Conflicting cells', value: '0', state: 'unknown' as const, primary: true },
  { label: 'Rows settled', value: '0', unit: 'rows' },
  { label: 'Ledger revision', value: '4', unit: 'rev' },
];

export const REVISION_NOTE =
  'The attempt captured revision 4. A settle carrying 4 against a ledger at 5 is RevisionMoved, retried once inside the call. A second movement is Undecided(LEDGER_MOVED).';

export const CLAIM_OUTCOME_NOTES = {
  conflictCode: 'SC-CLM-0002',
  movedReason: 'LEDGER_MOVED',
  settledArm: 'Formed',
};

/* ---------- scenario playback: settle three steps ---------- */

export type ClaimsScenarioId = 'exclusive-conflict' | 'shared' | 'reserved' | 'revision-moved';

export interface ClaimPlacement {
  mode: ClaimModeName;
  scope: ClaimScopeOption;
  offset: { x: number; z: number };
}

export interface ClaimsScenario extends DemoScenario<ClaimsScenarioId> {
  a: ClaimPlacement;
  b: ClaimPlacement;
  ledgerRevision: number;
  capturedRevision: number;
}

export const CLAIMS_SCENARIOS: ClaimsScenario[] = [
  {
    id: 'exclusive-conflict',
    label: 'EXCLUSIVE overlap',
    note: 'Both structures claim EXCLUSIVE + Owned on the same two cells. Rows are written, the matrix compares them, and the second settle fails.',
    steps: 3,
    a: { mode: 'EXCLUSIVE', scope: 'Owned', offset: { x: 0, z: 0 } },
    b: { mode: 'EXCLUSIVE', scope: 'Owned', offset: { x: 2, z: 1 } },
    ledgerRevision: 4,
    capturedRevision: 4,
  },
  {
    id: 'shared',
    label: 'SHARED overlap',
    note: 'The same footprint, both SHARED. The overlap is real, the matrix records it, and Settled is returned: shared rows never conflict with each other.',
    steps: 3,
    a: { mode: 'SHARED', scope: 'Owned', offset: { x: 0, z: 0 } },
    b: { mode: 'SHARED', scope: 'Owned', offset: { x: 2, z: 1 } },
    ledgerRevision: 4,
    capturedRevision: 4,
  },
  {
    id: 'reserved',
    label: 'RESERVED blocks EXCLUSIVE',
    note: 'A is EXCLUSIVE, B is RESERVED. RESERVED reads like SHARED but blocks EXCLUSIVE until the host’s reservation expires, so the settle fails.',
    steps: 3,
    a: { mode: 'EXCLUSIVE', scope: 'Owned', offset: { x: 0, z: 0 } },
    b: { mode: 'RESERVED', scope: 'Owned', offset: { x: 2, z: 1 } },
    ledgerRevision: 4,
    capturedRevision: 4,
  },
  {
    id: 'revision-moved',
    label: 'Revision moved',
    note: 'No cell conflict, but the attempt captured revision 4 while the ledger is at 5. RevisionMoved is retried once, then Undecided(LEDGER_MOVED).',
    steps: 3,
    a: { mode: 'EXCLUSIVE', scope: 'Owned', offset: { x: 0, z: 0 } },
    b: { mode: 'EXCLUSIVE', scope: 'Owned', offset: { x: 4, z: 3 } },
    ledgerRevision: 5,
    capturedRevision: 4,
  },
];
