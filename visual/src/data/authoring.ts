export type JsonKind = 'open' | 'close' | 'field' | 'item';

import type { DemoScenario } from './types';

export interface JsonRow {
  id: string;
  depth: number;
  kind: JsonKind;
  key?: string;
  value?: string;
  mapsTo?: string;
  path: string;
}

export const CAMPFIRE_ROWS: JsonRow[] = [
  { id: 'r1', depth: 0, kind: 'open', path: '$', value: '{' },
  { id: 'r2', depth: 1, kind: 'field', key: 'schemaVersion', value: '2', path: 'schemaVersion' },
  { id: 'r3', depth: 1, kind: 'field', key: 'id', value: '"mymod:campfire"', path: 'id' },
  {
    id: 'r4',
    depth: 1,
    kind: 'field',
    key: 'bounds',
    value: '[0, 0, 0, 3, 3, 3]',
    mapsTo: 'cuboid',
    path: 'bounds',
  },
  { id: 'r5', depth: 1, kind: 'open', key: 'shell', path: 'shell' },
  {
    id: 'r6',
    depth: 2,
    kind: 'item',
    value: '{"box": [0,0,0,3,3,3], "block": "minecraft:stone_bricks"}',
    mapsTo: 'bay',
    path: 'shell[0]',
  },
  { id: 'r7', depth: 1, kind: 'close', value: '],', path: 'shell' },
  { id: 'r8', depth: 1, kind: 'open', key: 'bays', path: 'bays' },
  {
    id: 'r9',
    depth: 2,
    kind: 'item',
    value: '{"id": "hollow", "box": [1,1,1,1,1,1], "coverage": "cubic", "mode": "exclusive", "scope": "owned"}',
    mapsTo: 'baycells',
    path: 'bays[0]',
  },
  {
    id: 'r10',
    depth: 3,
    kind: 'field',
    key: 'expect',
    value: '{"air": true}',
    mapsTo: 'check',
    path: 'bays[0].expect',
  },
  {
    id: 'r11',
    depth: 2,
    kind: 'item',
    value: '{"id": "fire", "box": [1,0,1,1,1,1], "coverage": "cubic", "mode": "exclusive", "scope": "owned"}',
    mapsTo: 'baycells',
    path: 'bays[1]',
  },
  {
    id: 'r12',
    depth: 3,
    kind: 'field',
    key: 'expect',
    value: '{"block": "minecraft:campfire"}',
    mapsTo: 'check',
    path: 'bays[1].expect',
  },
  { id: 'r13', depth: 1, kind: 'close', value: '],', path: 'bays' },
  { id: 'r14', depth: 1, kind: 'open', key: 'checks', path: 'checks' },
  {
    id: 'r15',
    depth: 2,
    kind: 'item',
    value: '{"id": "lit", "stage": "FORM", "bay": "fire", "focus": {"kind": "occupant", "cell": [1,0,1]}}',
    mapsTo: 'check',
    path: 'checks[0]',
  },
  {
    id: 'r16',
    depth: 3,
    kind: 'field',
    key: 'expect',
    value: '{"kind": "state", "property": "lit", "value": "true"}',
    mapsTo: 'expectation',
    path: 'checks[0].expect',
  },
  { id: 'r17', depth: 1, kind: 'close', value: '],', path: 'checks' },
  { id: 'r18', depth: 1, kind: 'open', key: 'computations', path: 'computations' },
  {
    id: 'r19',
    depth: 2,
    kind: 'item',
    value:
      '{"id": "air_cells", "stage": "FORM", "phase": "section", "bay": "hollow", "output": "mymod:air_cells"}',
    mapsTo: 'computation',
    path: 'computations[0]',
  },
  {
    id: 'r20',
    depth: 3,
    kind: 'field',
    key: 'kind',
    value: '"count"',
    mapsTo: 'computation',
    path: 'computations[0].kind',
  },
  { id: 'r21', depth: 1, kind: 'close', value: '],', path: 'computations' },
  { id: 'r22', depth: 1, kind: 'open', key: 'context', path: 'context' },
  {
    id: 'r23',
    depth: 2,
    kind: 'item',
    value: '{"id": "mymod:air_cells", "type": "long", "retention": "context", "merge": "sum"}',
    mapsTo: 'contextkey',
    path: 'context[0]',
  },
  { id: 'r24', depth: 1, kind: 'close', value: '],', path: 'context' },
  { id: 'r25', depth: 1, kind: 'open', key: 'anchors', path: 'anchors' },
  {
    id: 'r26',
    depth: 2,
    kind: 'item',
    value: '{"role": "front", "property": "facing", "value": "north"}',
    mapsTo: 'anchor',
    path: 'anchors[0]',
  },
  { id: 'r27', depth: 1, kind: 'close', value: '],', path: 'anchors' },
  {
    id: 'r28',
    depth: 1,
    kind: 'field',
    key: 'orientation',
    value: '{"front": "north"}',
    mapsTo: 'siting',
    path: 'orientation',
  },
  { id: 'r29', depth: 0, kind: 'close', value: '}', path: '$' },
];

export interface Mapping {
  id: string;
  jsonPath: string;
  jsonValue: string;
  javaType: string;
  runtimeRole: string;
  status: 'yes' | 'partial' | 'no';
}

export const MAPPINGS: Mapping[] = [
  {
    id: 'cuboid',
    jsonPath: 'bounds',
    jsonValue: '[0, 0, 0, 3, 3, 3]',
    javaType: 'Cuboid',
    runtimeRole: 'Blueprint bounds; half-open min/max; it is not automatically any bay.',
    status: 'yes',
  },
  {
    id: 'bay',
    jsonPath: 'shell[0]',
    jsonValue: '{box, block}',
    javaType: 'Bay (one per face box)',
    runtimeRole: 'Lowered to one CUBIC bay per face box, ids #box@x,y,z:sx,sy,sz; not one id spanning six boxes.',
    status: 'no',
  },
  {
    id: 'baycells',
    jsonPath: 'bays[].box',
    jsonValue: '[1, 0, 1, 1, 1, 1]',
    javaType: 'Bay.cells (Cuboid)',
    runtimeRole: 'The cells a bay asserts and maybe owns; the cells of a named bay are carved out of the shell.',
    status: 'yes',
  },
  {
    id: 'coverage',
    jsonPath: 'bays[].coverage',
    jsonValue: '"cubic"',
    javaType: 'Coverage',
    runtimeRole: 'How many cells one MATCH asserts: CUBIC every cell, SUBJECT footprint equality, PRESENCE none.',
    status: 'yes',
  },
  {
    id: 'scope',
    jsonPath: 'bays[].mode / scope',
    jsonValue: '"exclusive" / "owned"',
    javaType: 'ClaimMode / ClaimScope',
    runtimeRole: 'Exclusivity and owned-cell set; SUBJECT + Owned is SC-CHK-0106.',
    status: 'yes',
  },
  {
    id: 'check',
    jsonPath: 'bays[].expect',
    jsonValue: '{"block": "minecraft:campfire"}',
    javaType: 'Expectation<?> + Check',
    runtimeRole: 'A bay-level predicate becomes a Check whose focus is the bay or each cell.',
    status: 'partial',
  },
  {
    id: 'focus',
    jsonPath: 'checks[].focus',
    jsonValue: '{"kind": "occupant", "cell": [1,0,1]}',
    javaType: 'Focus<T>',
    runtimeRole: 'Takes a typed subject; absence is Outcome.Missing with an SC-FOC code, never null.',
    status: 'partial',
  },
  {
    id: 'expectation',
    jsonPath: 'checks[].expect',
    jsonValue: '{"kind": "state", "property": "lit"}',
    javaType: 'Expectation<T>',
    runtimeRole: 'Judges the subject; the check constructor pins it to the focus subject type (SC-CHK-0101).',
    status: 'partial',
  },
  {
    id: 'stage',
    jsonPath: 'checks[].stage',
    jsonValue: '"FORM"',
    javaType: 'Stage',
    runtimeRole: 'Which stage plan the check belongs to: DESIGN, PROBE, FORM, SETTLE, STALE.',
    status: 'yes',
  },
  {
    id: 'computation',
    jsonPath: 'computations[]',
    jsonValue: '{"phase": "section", ...}',
    javaType: 'Computation<T>',
    runtimeRole: 'Reads a region, returns an Outcome<T>, writes one ContextKey; SECTION or AGGREGATE.',
    status: 'yes',
  },
  {
    id: 'output',
    jsonPath: 'computations[].output',
    jsonValue: '"mymod:air_cells"',
    javaType: 'ContextKey<T>',
    runtimeRole: 'The single key this computation writes; two outputs means two computations.',
    status: 'yes',
  },
  {
    id: 'inputs',
    jsonPath: 'computations[].inputs',
    jsonValue: '["mymod:volume"]',
    javaType: 'Set<ContextKey<?>>',
    runtimeRole: 'Kahn dependency edges; a cycle is SC-CHK-0102.',
    status: 'yes',
  },
  {
    id: 'region',
    jsonPath: 'computations[].region',
    jsonValue: '"interior"',
    javaType: 'CuboidSet',
    runtimeRole: 'The cells the computation reads; sections intersect it, and the read closure is cached.',
    status: 'yes',
  },
  {
    id: 'retention',
    jsonPath: 'context[].retention',
    jsonValue: '"context"',
    javaType: 'Retention',
    runtimeRole: 'ATTEMPT dropped with the attempt, OUTCOME in MatchOutcome, CONTEXT in the stage frame.',
    status: 'yes',
  },
  {
    id: 'merge',
    jsonPath: 'context[].merge',
    jsonValue: '"sum"',
    javaType: 'Merge<T>',
    runtimeRole: 'Non-null allows per-section contributions and one left fold; null is a single writer.',
    status: 'yes',
  },
  {
    id: 'contextkey',
    jsonPath: 'context[0]',
    jsonValue: '{"id": "mymod:air_cells", "type": "long"}',
    javaType: 'ContextKey<T>',
    runtimeRole: '(id, type, Retention, merge, whenAbsent); equality is (id, type).',
    status: 'yes',
  },
  {
    id: 'anchor',
    jsonPath: 'anchors[0]',
    jsonValue: '{"role": "front", "property": "facing", "value": "north"}',
    javaType: 'AnchorSpec',
    runtimeRole: 'Read at the definition origin to obtain a heading; the resolver is not implemented.',
    status: 'no',
  },
  {
    id: 'siting',
    jsonPath: 'orientation',
    jsonValue: '{"front": "north"}',
    javaType: 'Siting policy',
    runtimeRole: 'Maps the authored front onto the anchor heading; still a spec, no resolver code.',
    status: 'no',
  },
];

export interface GraphNode {
  id: string;
  name: string;
  sub: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export const GRAPH_NODES: GraphNode[] = [
  { id: 'cuboid', name: 'Cuboid', sub: 'min / max · half-open', x: 48, y: 28, w: 176, h: 48 },
  { id: 'blueprint', name: 'Blueprint', sub: 'id · bounds · bays · anchors', x: 348, y: 28, w: 196, h: 48 },
  { id: 'bay', name: 'Bay', sub: 'cells · coverage · mode', x: 656, y: 28, w: 176, h: 48 },
  { id: 'requirement', name: 'Requirement', sub: 'checks[] + computations[]', x: 656, y: 124, w: 176, h: 48 },
  { id: 'focus', name: 'Focus<T>', sub: 'takes the subject', x: 236, y: 300, w: 160, h: 48 },
  { id: 'check', name: 'Check<T>', sub: 'same T on both sides', x: 452, y: 212, w: 168, h: 48 },
  { id: 'expectation', name: 'Expectation<T>', sub: 'returns Reply', x: 692, y: 212, w: 160, h: 48 },
  { id: 'computation', name: 'Computation<T>', sub: 'one output key', x: 452, y: 316, w: 168, h: 48 },
  { id: 'contextkey', name: 'ContextKey<T>', sub: 'merge · whenAbsent', x: 692, y: 316, w: 160, h: 48 },
  { id: 'anchor', name: 'AnchorSpec', sub: 'role · property · value', x: 48, y: 212, w: 160, h: 48 },
  { id: 'siting', name: 'Siting', sub: 'origin + placement', x: 48, y: 316, w: 160, h: 48 },
  { id: 'baycells', name: 'Bay.cells', sub: 'Cuboid of asserted cells', x: 348, y: 124, w: 180, h: 48 },
];

export const GRAPH_EDGES: {
  from: string;
  to: string;
  label: string;
  fromSide?: 'left' | 'right' | 'top' | 'bottom';
  toSide?: 'left' | 'right' | 'top' | 'bottom';
}[] = [
  { from: 'cuboid', to: 'blueprint', label: 'bounds', fromSide: 'right', toSide: 'left' },
  { from: 'bay', to: 'blueprint', label: 'bays[]', fromSide: 'left', toSide: 'right' },
  { from: 'baycells', to: 'bay', label: 'cells', fromSide: 'right', toSide: 'left' },
  { from: 'requirement', to: 'bay', label: 'holds', fromSide: 'top', toSide: 'bottom' },
  { from: 'check', to: 'requirement', label: 'checks[]', fromSide: 'right', toSide: 'left' },
  { from: 'computation', to: 'requirement', label: 'computes', fromSide: 'right', toSide: 'left' },
  { from: 'focus', to: 'check', label: '', fromSide: 'top', toSide: 'left' },
  { from: 'expectation', to: 'check', label: '', fromSide: 'left', toSide: 'right' },
  { from: 'contextkey', to: 'computation', label: 'output', fromSide: 'left', toSide: 'right' },
  { from: 'anchor', to: 'blueprint', label: 'anchors[]', fromSide: 'right', toSide: 'left' },
  { from: 'siting', to: 'anchor', label: 'resolves', fromSide: 'top', toSide: 'bottom' },
];

export const EXPECT_RULES = [
  {
    id: 'one-tag',
    verdict: 'fail',
    label: 'One tag per object',
    code: 'SC-DOC-0605',
    snippet: '{"block": "minecraft:stone_bricks", "trait": "structurecumulus:item_inventory"}',
    note: 'Two tags is not an implicit AND. Write {"allOf": [...]} if both must hold.',
  },
  {
    id: 'anyof',
    verdict: 'ok',
    label: 'anyOf: one of two blocks',
    code: 'MATCH',
    snippet:
      '{"anyOf": [\n  {"block": "minecraft:stone_bricks"},\n  {"block": "minecraft:deepslate_bricks"}\n]}',
    note: 'Both children test OccupantSnapshot, so they may share one combinator.',
  },
  {
    id: 'allof',
    verdict: 'ok',
    label: 'allOf: block plus trait',
    code: 'MATCH',
    snippet:
      '{"allOf": [\n  {"block": "minecraft:iron_bars"},\n  {"trait": "structurecumulus:item_inventory"}\n]}',
    note: 'Block and trait both read OccupantSnapshot; state reads T and cannot join this group.',
  },
  {
    id: 'not',
    verdict: 'ok',
    label: 'not: anything but air',
    code: 'MATCH',
    snippet: '{"not": {"air": true}}',
    note: 'Negation preserves ABSTAIN and UNREADABLE; it only swaps MATCH and MISMATCH.',
  },
  {
    id: 'empty',
    verdict: 'fail',
    label: 'Empty combinator',
    code: 'SC-DOC-0606',
    snippet: '{"anyOf": []}',
    note: 'The array cannot be empty: a vacuous all-true / all-false hides the authoring error.',
  },
  {
    id: 'mixed',
    verdict: 'fail',
    label: 'Different subjects in one anyOf',
    code: 'SC-CHK-0101',
    snippet: '{"anyOf": [\n  {"block": "minecraft:campfire"},\n  {"kind": "state", "property": "lit", "value": "true"}\n]}',
    note: 'state is judged against T, not OccupantSnapshot. Split it into a second Check with its own focus.',
  },
];

export const COMPACT_CANONICAL = {
  compact: '{"block": "minecraft:campfire"}',
  canonical: '{"kind": "block", "args": "minecraft:campfire"}',
};

/* ---------- scenario playback: lowering ---------- */

export type AuthoringScenarioId = 'lowering' | 'anchors';

export interface LoweringStep {
  id: string;
  rowId: string;
  nodeId: string;
  title: string;
  carries: string;
  code: string;
}

export const AUTHORING_SCENARIOS: DemoScenario<AuthoringScenarioId>[] = [
  {
    id: 'lowering',
    label: 'Lowering, line by line',
    note: 'Four JSON lines, four runtime values. Each step highlights the line and pulses the object the builder creates from it.',
    steps: 4,
  },
  {
    id: 'anchors',
    label: 'Anchor → Siting',
    note: 'The two placement lines at the end of the file: an AnchorSpec is read at the definition origin, and a Siting consumes the heading it yields.',
    steps: 3,
  },
];

export const AUTHORING_LOWERING: LoweringStep[] = [
  {
    id: 'bounds',
    rowId: 'r4',
    nodeId: 'cuboid',
    title: 'bounds → Cuboid',
    carries: '[0,0,0,3,3,3] becomes min/max and stays half-open: 27 cells, and not automatically any bay.',
    code: 'Cuboid(min, max)',
  },
  {
    id: 'bays',
    rowId: 'r9',
    nodeId: 'bay',
    title: 'bays[] → Bay',
    carries: 'One array entry becomes one bay with ids, cells, Coverage, ClaimMode and ClaimScope; a shell face is not one bay.',
    code: 'Bay(id, cells, coverage, mode, scope)',
  },
  {
    id: 'checks',
    rowId: 'r15',
    nodeId: 'check',
    title: 'checks[] → Check',
    carries: 'focus and expect are read together, and the constructor pins both sides to one T; a bay-level expect lowers separately.',
    code: 'Check<T>(focus, expectation, stage, admission)',
  },
  {
    id: 'context',
    rowId: 'r23',
    nodeId: 'contextkey',
    title: 'context[] → ContextKey',
    carries: '(id, type, Retention, merge, whenAbsent); the computation that writes this key is lowered from computations[].',
    code: 'ContextKey<T>',
  },
];

export const AUTHORING_ANCHORS: LoweringStep[] = [
  {
    id: 'anchor',
    rowId: 'r26',
    nodeId: 'anchor',
    title: 'anchors[0] → AnchorSpec',
    carries: 'role + property + value; it is read once at the definition origin, before any orientation is chosen.',
    code: 'AnchorSpec(role, property, value)',
  },
  {
    id: 'siting',
    rowId: 'r28',
    nodeId: 'siting',
    title: 'orientation → Siting',
    carries: 'The authored front is mapped onto the heading the anchor yielded; the resolver itself is still a spec.',
    code: 'candidate.apply(authoredFront) == anchorHeading',
  },
  {
    id: 'blueprint',
    rowId: 'r1',
    nodeId: 'blueprint',
    title: '$ → Blueprint',
    carries: 'The four groups land in one immutable record: bounds, bays, checks and computations, anchors and the siting.',
    code: 'Blueprint(id, bounds, bays, anchors, derivations)',
  },
];
