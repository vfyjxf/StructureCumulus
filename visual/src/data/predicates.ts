export type FocusId = 'occupant' | 'bay' | 'context';
export type ExpectId = 'block' | 'air' | 'trait' | 'state';
export type ReplyState = 'MATCH' | 'MISMATCH' | 'UNREADABLE' | 'ABSTAIN';
export type PredNodeKind = ExpectId | 'allOf' | 'anyOf' | 'not';

import type { DemoScenario } from './types';

export interface FocusSpec {
  id: FocusId;
  label: string;
  subject: string;
  expr: string;
  reads: string;
  line: string;
}

export const FOCUSES: FocusSpec[] = [
  {
    id: 'occupant',
    label: 'Occupant',
    subject: 'OccupantSnapshot',
    expr: 'Focuses.occupant(LatticeKey.of(1, 0, 1))',
    reads: 'one cell',
    line: 'The common case: one cell, one occupant, one predicate.',
  },
  {
    id: 'bay',
    label: 'Bay',
    subject: 'BayView',
    expr: 'Focuses.bay()',
    reads: 'bay.cells',
    line: 'A door or a multi-cell bay arrives as one subject; expectation never re-reads the world.',
  },
  {
    id: 'context',
    label: 'Context',
    subject: 'Long',
    expr: 'Focuses.context(CAPACITY)',
    reads: 'the key, not cells',
    line: 'Exactly ctx.context().require(key); absence is Missing, never a zero.',
  },
];

export interface ExpectationSpec {
  id: ExpectId;
  label: string;
  subject: (focus: FocusSpec) => string;
  expr: (focus: FocusSpec) => string;
  line: string;
}

export const EXPECTATIONS: ExpectationSpec[] = [
  {
    id: 'block',
    label: 'Block',
    subject: () => 'OccupantSnapshot',
    expr: () => 'Expectations.block(NamespacedId.of("minecraft", "campfire"))',
    line: 'Compares the occupant descriptor; a block expectation never looks at a bay or a Long.',
  },
  {
    id: 'air',
    label: 'Air',
    subject: () => 'OccupantSnapshot',
    expr: () => 'Expectations.air()',
    line: 'Air is a readable occupant; a refused section is never air.',
  },
  {
    id: 'trait',
    label: 'Trait',
    subject: () => 'OccupantSnapshot',
    expr: () => 'Expectations.trait(NamespacedId.of("structurecumulus", "item_inventory"))',
    line: 'A priced capability test; the meta requires the trait so the plan can charge for it.',
  },
  {
    id: 'state',
    label: 'State',
    subject: (focus) => focus.subject,
    expr: () => 'Expectations.state("lit", "true")',
    line: 'Generic over T: the same interface serves an occupant property, a bay property and a Long.',
  },
];

export interface Sample {
  block: string;
  traits: string[];
  lit: boolean;
  refused: boolean;
}

export const BLOCK_OPTIONS = ['minecraft:campfire', 'minecraft:stone_bricks', 'minecraft:iron_bars', 'AIR'];

export const DEFAULT_SAMPLE: Sample = {
  block: 'minecraft:campfire',
  traits: ['structurecumulus:lit'],
  lit: true,
  refused: false,
};

export const SUBJECTS_BY_TYPE: Record<string, string> = {
  OccupantSnapshot: 'the frozen occupant: kind, descriptor, footprint, traits',
  BayView: 'the bay: its cells, its bindings, its subject kind',
  Long: 'a typed context value; 0 is a value, absence is not',
  DoorView: 'both halves of a door, one subject',
};

export const REPLY_ORDER = ['MATCH', 'ABSTAIN', 'MISMATCH', 'UNREADABLE'] as const;

export const REPLY_LADDER = [
  { state: 'UNREADABLE', note: 'A subject or key is missing; nothing was judged.', code: 'SC-FOC-0301 / SC-FOC-0101' },
  { state: 'MISMATCH', note: 'The subject was read and violates the expectation; one finding.', code: 'finding from the expectation' },
  { state: 'ABSTAIN', note: 'The predicate declines to judge; not a pass.', code: 'no finding' },
  { state: 'MATCH', note: 'The subject satisfies the expectation; bindings only here.', code: 'no finding' },
];

export const COMBINATOR_TABLE = [
  { op: 'allOf', match: 'all children MATCH', mismatch: 'any child MISMATCH is decisive', unknown: 'UNREADABLE if any, else ABSTAIN' },
  { op: 'anyOf', match: 'any child MATCH', mismatch: 'all children MISMATCH', unknown: 'UNREADABLE if any, else ABSTAIN' },
  { op: 'not', match: 'child MISMATCH', mismatch: 'child MATCH', unknown: 'UNREADABLE and ABSTAIN pass through' },
];

export const SUBJECT_MISMATCH_CODE = 'SC-CHK-0101';
export const SUBJECT_MISMATCH_MESSAGE =
  'Check<T> constructor: focus subject and expectation subject differ. The check is not constructed.';

export const MISMATCH_FINDING_CODE = 'SC-EXP-0100';

/* ---------- simulation ---------- */

export interface PredNode {
  id: string;
  kind: PredNodeKind;
  children: PredNode[];
  label?: string;
}

let nodeSeq = 0;
export const newNodeId = () => `pn-${++nodeSeq}`;

export const leafText = (node: PredNode): string => {
  switch (node.kind) {
    case 'block':
      return 'block("minecraft:campfire")';
    case 'air':
      return 'air()';
    case 'trait':
      return 'trait("structurecumulus:item_inventory")';
    case 'state':
      return 'state("lit", "true")';
    case 'allOf':
      return 'allOf';
    case 'anyOf':
      return 'anyOf';
    case 'not':
      return 'not';
  }
};

const combineAll = (replies: ReplyState[]): ReplyState => {
  if (replies.some((r) => r === 'MISMATCH')) return 'MISMATCH';
  if (replies.some((r) => r === 'UNREADABLE')) return 'UNREADABLE';
  if (replies.some((r) => r === 'ABSTAIN')) return 'ABSTAIN';
  return 'MATCH';
};

const combineAny = (replies: ReplyState[]): ReplyState => {
  if (replies.some((r) => r === 'MATCH')) return 'MATCH';
  if (replies.some((r) => r === 'UNREADABLE')) return 'UNREADABLE';
  if (replies.some((r) => r === 'ABSTAIN')) return 'ABSTAIN';
  return 'MISMATCH';
};

export const evalLeaf = (kind: PredNodeKind, focus: FocusId, sample: Sample): ReplyState => {
  if (sample.refused) return 'UNREADABLE';
  const isAir = sample.block === 'AIR';
  switch (kind) {
    case 'block':
      if (isAir) return 'MISMATCH';
      return sample.block === 'minecraft:campfire' ? 'MATCH' : 'MISMATCH';
    case 'air':
      return isAir ? 'MATCH' : 'MISMATCH';
    case 'trait':
      if (isAir) return 'ABSTAIN';
      return sample.traits.includes('structurecumulus:item_inventory') ? 'MATCH' : 'MISMATCH';
    case 'state':
      if (focus === 'occupant') {
        if (isAir) return 'ABSTAIN';
        return sample.lit ? 'MATCH' : 'MISMATCH';
      }
      if (focus === 'bay') return 'MATCH';
      if (focus === 'context') return 'MATCH';
      return 'ABSTAIN';
    default:
      return 'ABSTAIN';
  }
};

export const evalNode = (node: PredNode, focus: FocusId, sample: Sample): ReplyState => {
  if (node.kind === 'allOf') {
    if (node.children.length === 0) return 'ABSTAIN';
    return combineAll(node.children.map((c) => evalNode(c, focus, sample)));
  }
  if (node.kind === 'anyOf') {
    if (node.children.length === 0) return 'ABSTAIN';
    return combineAny(node.children.map((c) => evalNode(c, focus, sample)));
  }
  if (node.kind === 'not') {
    const inner = node.children[0] ? evalNode(node.children[0], focus, sample) : 'ABSTAIN';
    if (inner === 'MATCH') return 'MISMATCH';
    if (inner === 'MISMATCH') return 'MATCH';
    return inner;
  }
  return evalLeaf(node.kind, focus, sample);
};

export const makeLeaf = (kind: ExpectId): PredNode => ({
  id: newNodeId(),
  kind,
  children: [],
});

export const makeCombinator = (kind: 'allOf' | 'anyOf' | 'not', seed: ExpectId): PredNode => ({
  id: newNodeId(),
  kind,
  children: kind === 'not' ? [makeLeaf(seed)] : [makeLeaf(seed), makeLeaf(seed === 'air' ? 'block' : 'air')],
});

export const cloneNode = (node: PredNode): PredNode => ({
  ...node,
  id: node.id,
  children: node.children.map(cloneNode),
});

/* ---------- scenario playback: execute one judgement ---------- */

export type PredicateScenarioId = 'match' | 'mismatch' | 'refused';
export type PredicatePhase = 'focus' | 'subject' | 'child' | 'reply';

export function isPredicatePhase(value: string | null): value is PredicatePhase {
  return value === 'focus' || value === 'subject' || value === 'child' || value === 'reply';
}

export interface PredicateScenario extends DemoScenario<PredicateScenarioId> {
  sample: Sample;
  refused: boolean;
}

export const PREDICATE_SCENARIOS: PredicateScenario[] = [
  {
    id: 'match',
    label: 'Campfire · MATCH',
    note: 'Occupant focus, one block leaf. The subject is Found, the leaf reads the frozen snapshot, and the attempt returns MATCH.',
    steps: 4,
    sample: { block: 'minecraft:campfire', traits: ['structurecumulus:lit'], lit: true, refused: false },
    refused: false,
  },
  {
    id: 'mismatch',
    label: 'Stone → MISMATCH',
    note: 'The same predicate with stone bricks in the cell. The subject is still Found, the leaf is decisive, and one SC-EXP-0100 finding follows.',
    steps: 4,
    sample: { block: 'minecraft:stone_bricks', traits: [], lit: false, refused: false },
    refused: false,
  },
  {
    id: 'refused',
    label: 'Refused section → UNREADABLE',
    note: 'A refused section makes the cell unreadable before any predicate runs. focus.take returns Missing and UNREADABLE wins the reply fold.',
    steps: 4,
    sample: { block: 'minecraft:campfire', traits: ['structurecumulus:lit'], lit: true, refused: true },
    refused: true,
  },
];

export const PREDICATE_PHASE_ORDER: PredicatePhase[] = ['focus', 'subject', 'child', 'reply'];
