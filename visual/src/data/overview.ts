import type { DemoScenario, ReadoutSpec } from './types';

export interface Concept {
  id: string;
  name: string;
  kind: string;
  oneLine: string;
}

export interface Layer {
  id: string;
  no: string;
  name: string;
  layerTag: string;
  question: string;
  owns: string;
  concepts: Concept[];
}

export const LAYERS: Layer[] = [
  {
    id: 'document',
    no: '01',
    name: 'Document',
    layerTag: 'L3 document',
    question: 'What is declared?',
    owns: 'the authored file: geometry, assertions, dataflow, placement',
    concepts: [
      {
        id: 'doc',
        name: 'BlueprintDocument',
        kind: 'schema 2',
        oneLine:
          'One file holds four concerns — geometry, assertions, dataflow and placement — under one schemaVersion.',
      },
      {
        id: 'bounds',
        name: 'bounds',
        kind: 'Cuboid',
        oneLine:
          '[x, y, z, sizeX, sizeY, sizeZ] is origin + size; the runtime Cuboid is half-open, so [0,0,0,3,3,3] is 27 cells.',
      },
      {
        id: 'expect',
        name: 'expect',
        kind: 'tagged union',
        oneLine:
          'One tag per object: block, air, trait, state, or a combinator. Two tags are SC-DOC-0605, not an implicit AND.',
      },
      {
        id: 'computations',
        name: 'computations[]',
        kind: 'dataflow',
        oneLine:
          'phase: section contributes a partial per section; phase: aggregate runs once after the barrier and writes the fold.',
      },
      {
        id: 'context',
        name: 'context[]',
        kind: 'ContextKey',
        oneLine:
          'A typed key with a Retention and an optional Merge; merge == null is a single writer, merge != null allows section contributions.',
      },
      {
        id: 'anchors',
        name: 'anchors[]',
        kind: 'AnchorSpec',
        oneLine:
          'role + property + value, read at the definition origin; the heading it yields fixes the siting, not the other way round.',
      },
    ],
  },
  {
    id: 'objects',
    no: '02',
    name: 'Object graph',
    layerTag: 'L1 kernel',
    question: 'What does the runtime hold?',
    owns: 'immutable values assembled at DESIGN; the JSON never survives into execution',
    concepts: [
      {
        id: 'blueprint',
        name: 'Blueprint',
        kind: 'record',
        oneLine: '(id, bounds, bays, anchors, derivations) — the complete declaration, immutable after build.',
      },
      {
        id: 'bay',
        name: 'Bay',
        kind: 'record',
        oneLine:
          '(id, cells, Coverage, ClaimMode, ClaimScope, Requirement) — a cuboid that can be judged, owned and staged on its own.',
      },
      {
        id: 'requirement',
        name: 'Requirement',
        kind: 'record',
        oneLine: 'List<Check<?>> + List<Computation<?>> for one bay; empty is illegal because a bay exists to assert.',
      },
      {
        id: 'check',
        name: 'Check<T>',
        kind: 'generic',
        oneLine:
          'Focus<T> + Expectation<T> + stage + admission; the constructor pins both sides to the same T (SC-CHK-0101).',
      },
      {
        id: 'computation',
        name: 'Computation<T>',
        kind: 'generic',
        oneLine:
          'One output ContextKey<T>, a region, inputs for the Kahn order, and a SECTION or AGGREGATE phase.',
      },
      {
        id: 'contextkey',
        name: 'ContextKey<T>',
        kind: 'record',
        oneLine:
          '(id, type, Retention, merge, whenAbsent); equality is (id, type), and a second registration is SC-CTX-0102.',
      },
    ],
  },
  {
    id: 'execution',
    no: '03',
    name: 'Execution',
    layerTag: 'L1 + L2',
    question: 'What runs, in what order?',
    owns: 'plan, capture, sections, the barrier, judging, publication',
    concepts: [
      {
        id: 'expand',
        name: 'expand(stage)',
        kind: 'plan',
        oneLine:
          'Section computations → barrier → aggregate computations and derivers in Kahn order → checks, after the units they read.',
      },
      {
        id: 'section',
        name: 'Section pass',
        kind: 'parallel',
        oneLine:
          'A section computation runs once per section in region ∩ section and contributes a partial; it cannot read a ContextKey.',
      },
      {
        id: 'fold',
        name: 'Barrier fold',
        kind: 'deterministic',
        oneLine:
          'Contributions sort by SectionKey (x, z, y) and combine left-to-right; no identity element, and a refusal makes the key absent.',
      },
      {
        id: 'judge',
        name: 'Judge',
        kind: 'predicate',
        oneLine:
          'focus.take returns Found or Missing; only Found reaches expectation.test. The attempt folds UNREADABLE > MISMATCH > ABSTAIN > MATCH.',
      },
      {
        id: 'publish',
        name: 'Publish',
        kind: 'CAS',
        oneLine: 'One draft per stage boundary; a stale baseRevision is SC-CTX-0108 and nothing is written.',
      },
      {
        id: 'settle',
        name: 'Settle',
        kind: 'CAS',
        oneLine: 'One ledger transaction replaces every row of (owner, siting); only Settled can produce Formed.',
      },
    ],
  },
  {
    id: 'result',
    no: '04',
    name: 'Result',
    layerTag: 'L5 diagnostic',
    question: 'What is true after the call?',
    owns: 'verdict, claims, context frames, findings',
    concepts: [
      {
        id: 'matchresult',
        name: 'MatchResult',
        kind: 'sealed',
        oneLine:
          'Admitted, Formed, Failed, Undecided, Deferred; only SETTLE + Settled produces Formed, and Admitted is not a structure.',
      },
      {
        id: 'claimset',
        name: 'ClaimSet',
        kind: 'record',
        oneLine:
          'The rows and the ledger revision Formed settled at; a later replacement is detectable from that number.',
      },
      {
        id: 'structurecontext',
        name: 'StructureContext',
        kind: 'record',
        oneLine:
          '(instance, revision, frames) — tier-stamped reads, so a PROBE reader never sees a FORM value.',
      },
      {
        id: 'findingbundle',
        name: 'FindingBundle',
        kind: 'ordered set',
        oneLine:
          'Deduplicated and capped; order is severity → stage → code → primary locus → insertion, never insertion alone.',
      },
    ],
  },
];

export const FLOW: { from: string; to: string; label: string; detail: string }[] = [
  {
    from: 'document',
    to: 'objects',
    label: 'compile / build',
    detail: 'Read text, validate, lower to records. The four JSON groups all land in one Blueprint graph.',
  },
  {
    from: 'objects',
    to: 'execution',
    label: 'expand(stage)',
    detail: 'One ordered plan per stage; ties by admission ascending, specificity descending, declaration order.',
  },
  {
    from: 'execution',
    to: 'result',
    label: 'seal / settle',
    detail: 'Findings are values; the verdict is sealed once and never mutated. Formed exists only after Settled.',
  },
];

export const OVERVIEW_READOUTS: ReadoutSpec[] = [
  { label: 'Layers', value: '4', state: 'active', primary: true, delta: 'document → result' },
  { label: 'Pipeline steps', value: '08', unit: 'steps' },
  { label: 'Reply states', value: '4', unit: 'states' },
  { label: 'Result arms', value: '5', unit: 'arms' },
];

export const LAYER_EDGES = [
  { from: 'document', to: 'objects' },
  { from: 'objects', to: 'execution' },
  { from: 'execution', to: 'result' },
];

/* ---------- scenario playback: trace one assertion ---------- */

export type OverviewScenarioId = 'trace' | 'failure';

export interface TraceStep {
  id: string;
  layerId: string;
  object: string;
  carries: string;
  code: string;
}

export const OVERVIEW_SCENARIOS: DemoScenario<OverviewScenarioId>[] = [
  {
    id: 'trace',
    label: 'Trace one assertion',
    note: 'A campfire check travels from the authored file to Formed. Each stop names the object the token is inside and what it carries.',
    steps: 4,
  },
  {
    id: 'failure',
    label: 'Trace a refutation',
    note: 'The same route with one unlit campfire: the token stops on Failed and no claim row is ever written.',
    steps: 4,
  },
];

export const OVERVIEW_TRACE: Record<OverviewScenarioId, TraceStep[]> = {
  trace: [
    {
      id: 'doc',
      layerId: 'document',
      object: 'BlueprintDocument',
      carries: 'bounds + expect{"block":"minecraft:campfire"} + checks[] + context[]',
      code: 'schema 2',
    },
    {
      id: 'check',
      layerId: 'objects',
      object: 'Check<OccupantSnapshot>',
      carries: 'Focus<T> and Expectation<T> pinned to the same T at construction',
      code: 'SC-CHK-0101 if T differs',
    },
    {
      id: 'judge',
      layerId: 'execution',
      object: 'Judge',
      carries: 'focus.take → Found → expectation.test → MATCH, folded into one attempt reply',
      code: 'UNREADABLE > MISMATCH > ABSTAIN > MATCH',
    },
    {
      id: 'formed',
      layerId: 'result',
      object: 'Formed',
      carries: 'MatchResult + ClaimSet (294 rows) + FindingBundle, sealed after Settled',
      code: 'generation 1 · ledgerRevision 4',
    },
  ],
  failure: [
    {
      id: 'doc',
      layerId: 'document',
      object: 'expect.state("lit", "true")',
      carries: 'one tag, one property, one expected value on the fire bay check',
      code: 'checks[0].expect',
    },
    {
      id: 'check',
      layerId: 'objects',
      object: 'Check<OccupantSnapshot>',
      carries: 'focus occupant(1, 0, 1); expectation state("lit") judges the frozen snapshot',
      code: 'same T on both sides',
    },
    {
      id: 'judge',
      layerId: 'execution',
      object: 'MISMATCH · SC-EXP-0100',
      carries: 'the subject was read and violated the expectation; refutation outranks every unreadable cell',
      code: 'one finding, no bindings',
    },
    {
      id: 'failed',
      layerId: 'result',
      object: 'Failed',
      carries: 'FindingBundle with one ERROR; no claims, no instance id, derive() refuses it',
      code: 'repair may change the world',
    },
  ],
};

/** Projected vertical centres of the four bands, used by the travelling token. */
export const BAND_CENTRES = [48, 136, 224, 312];
