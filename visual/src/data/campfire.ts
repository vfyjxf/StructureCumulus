export type BayId = 'shell' | 'hollow' | 'fire';

export type CampfireCell = {
  x: number;
  y: number;
  z: number;
  bay: BayId;
};

export type Point3 = { x: number; y: number; z: number };
export type TurnQuarter = 0 | 1 | 2 | 3;
export type PhaseId = 'compile' | 'check' | 'settle';
export type MomentId = PhaseId | 'derive';
export type MomentKind = 'phase' | 'aftermath';

export const CAMPFIRE_CELLS: CampfireCell[] = [];
for (let y = 0; y < 3; y += 1) {
  for (let z = 0; z < 3; z += 1) {
    for (let x = 0; x < 3; x += 1) {
      let bay: BayId = 'shell';
      if (x === 1 && y === 1 && z === 1) bay = 'hollow';
      if (x === 1 && y === 0 && z === 1) bay = 'fire';
      CAMPFIRE_CELLS.push({ x, y, z, bay });
    }
  }
}

export const FIRE_CELL: CampfireCell = { x: 1, y: 0, z: 1, bay: 'fire' };
export const HOLLOW_CELL: CampfireCell = { x: 1, y: 1, z: 1, bay: 'hollow' };

export const CAMPFIRE = {
  id: 'mymod:campfire',
  bounds: '3×3×3',
  cells: 27,
  shell: 25,
  hollow: 1,
  fire: 1,
  bays: 3,
  checks: 3,
  claims: 27,
  spendReads: 28,
  sections: 4,
} as const;

export const BAYS = [
  { id: 'shell' as const, label: 'shell', count: 25, coverage: 'EVERY_CELL', footprint: 'Box', color: 'var(--stone)', note: 'asserts 25 blocks; owns the 3×3×3 shell' },
  { id: 'hollow' as const, label: 'hollow', count: 1, coverage: 'EVERY_CELL', footprint: 'Box', color: 'var(--air)', note: 'the cavity; owns one air cell' },
  { id: 'fire' as const, label: 'fire', count: 1, coverage: 'EVERY_CELL', footprint: 'Box', color: 'var(--fire)', note: 'the campfire; asserts lit=true' },
];

export const CHECKS = [
  { id: 'shell_bricks', bay: 'shell', focus: 'bay()', expect: 'each_cell(block stone_bricks)', result: 'match' },
  { id: 'hollow_air', bay: 'hollow', focus: 'bay()', expect: 'each_cell(air)', result: 'match' },
  { id: 'lit', bay: 'fire', focus: 'occupant(1,0,1)', expect: 'all_of(block campfire, property lit=true)', result: 'match' },
];

export type Moment = {
  id: MomentId;
  kind: MomentKind;
  no: string;
  label: string;
  title: string;
  result: string;
  note: string;
  readout: [string, string][];
  ledger: string;
  context: string;
  writes: string;
  publication: string;
  log: string;
};

export const MOMENTS: Moment[] = [
  {
    id: 'compile' as PhaseId,
    kind: 'phase',
    no: '01',
    label: 'COMPILE',
    title: 'Data becomes one blueprint',
    result: 'Blueprint',
    note: 'Definitions flatten; keys, bays, checks and computations lower to the same value the Java DSL builds. A bad document returns findings, never a half-built blueprint.',
    readout: [
      ['schema', '1'],
      ['bays', '3'],
      ['checks', '3'],
      ['computations', '1'],
      ['lowering', 'one blueprint'],
    ],
    ledger: '—',
    context: '—',
    writes: '0 rows',
    publication: 'none',
    log: 'document + java → one blueprint',
  },
  {
    id: 'check' as PhaseId,
    kind: 'phase',
    no: '02',
    label: 'CHECK',
    title: 'One immutable look',
    result: 'Admitted',
    note: 'The plan runs entirely: sections fold, aggregates read, checks meet. Claims are predicted but not written. The lease is the only gate.',
    readout: [
      ['result', 'Admitted'],
      ['checks', '3 / 3 matched'],
      ['air_cells', '1'],
      ['spend', '28 reads · 4 sections'],
      ['claims', 'predicted 27'],
    ],
    ledger: '—',
    context: '—',
    writes: '0 rows',
    publication: 'none',
    log: '27 claims predicted, not written',
  },
  {
    id: 'settle' as PhaseId,
    kind: 'phase',
    no: '03',
    label: 'SETTLE',
    title: 'One write, one publication',
    result: 'Formed',
    note: 'The ledger compare-and-set writes rows and bumps the revision. The context publishes INSTANCE values once. The result is a pure value.',
    readout: [
      ['result', 'Formed'],
      ['claim rows', '27'],
      ['ledger', 'rev 1'],
      ['context', 'rev 1'],
      ['published', 'air_cells = 1'],
    ],
    ledger: 'rev 1',
    context: 'rev 1',
    writes: '27 rows',
    publication: '1 batch',
    log: 'compare-and-set · publish air_cells',
  },
  {
    id: 'derive' as const,
    kind: 'aftermath',
    no: '↳',
    label: 'DERIVE',
    title: 'One more pure fold',
    result: 'StructureContext',
    note: 'A deriver reads only the published context. Derive is an operation on Formed — not a fourth phase. capacity derives from air; the second publication keeps the settle spend and bumps the revision once.',
    readout: [
      ['air_cells', '1'],
      ['capacity', '10'],
      ['context', 'rev 2'],
      ['spend', '28 reads · 4 sections'],
      ['writes', 'one batch'],
    ],
    ledger: 'rev 1',
    context: 'rev 2',
    writes: '0 rows',
    publication: '1 batch',
    log: 'capacity 10 from air_cells = 1',
  },
];

export type PhaseMoment = Moment & { id: PhaseId };

export const PHASES = MOMENTS.filter((entry): entry is PhaseMoment => entry.kind === 'phase');
export const DERIVE = MOMENTS[MOMENTS.length - 1];

export const INVARIANTS = [
  {
    id: 'lease',
    no: 'i',
    title: 'The lease is the only gate',
    detail: 'Host-scoped; isCurrent() is checked at entry and exit. A close mid-run is Dropped, never a partial result.',
    code: 'SC-ENG-0002',
    moments: ['check', 'settle'],
  },
  {
    id: 'writes',
    no: 'ii',
    title: 'Check writes nothing',
    detail: '27 claim rows are predicted from the bindings, then discarded with the request. Only settle may write.',
    code: 'writes = 0',
    moments: ['check'],
  },
  {
    id: 'value',
    no: 'iii',
    title: 'The result is a pure value',
    detail: 'Admitted and Formed carry a view, values and spend. The lease is never retained and no handle escapes.',
    code: 'MatchResult',
    moments: ['check', 'settle', 'derive'],
  },
  {
    id: 'publish',
    no: 'iv',
    title: 'One publication at settle',
    detail: 'Settle publishes INSTANCE once; derive adds one batch after Formed and bumps the revision once. Nothing else writes.',
    code: 'ContextStore',
    moments: ['settle'],
  },
  {
    id: 'spend',
    no: 'v',
    title: 'One spend, one snapshot',
    detail: '28 reads across 4 sections: shell 25 + hollow 1 + fire 1 checks, plus the 1-cell air section. Claims stay 27.',
    code: '28 · 4',
    moments: ['check', 'settle', 'derive'],
  },
];

export const SITING = {
  origin: { x: 9, y: 64, z: 8 },
  quarter: 0 as TurnQuarter,
  min: 3,
  max: 9,
  y: 64,
  fire: { x: 1, y: 0, z: 1 },
  localPoint: 'LOCAL (1,0,1)',
};

export const QUARTER_TURNS = [
  { quarter: 0 as const, label: '0°', turn: 'Turn.NONE', note: 'identity' },
  { quarter: 1 as const, label: '90°', turn: 'Turn.CLOCKWISE', note: 'clockwise' },
  { quarter: 2 as const, label: '180°', turn: 'Turn.HALF', note: 'half turn' },
  { quarter: 3 as const, label: '270°', turn: 'Turn.COUNTERCLOCKWISE', note: 'counterclockwise' },
];

export function orientPoint(x: number, y: number, z: number, quarter: number): [number, number, number] {
  switch (((quarter % 4) + 4) % 4) {
    case 1:
      return [-z, y, x];
    case 2:
      return [-x, y, -z];
    case 3:
      return [z, y, -x];
    default:
      return [x, y, z];
  }
}

export function sitingImage(origin: Point3, quarter: number, local: Point3): Point3 {
  const [dx, dy, dz] = orientPoint(local.x, local.y, local.z, quarter);
  return { x: origin.x + dx, y: origin.y + dy, z: origin.z + dz };
}

export const DOCUMENT_SNIPPET = `{
  "schemaVersion": 1,
  "id": "mymod:campfire",
  "bounds": [0, 0, 0, 3, 3, 3],
  "bays": [
    { "id": "shell", "boxes": [[0,0,0,3,3,3]],
      "minus": [[1,1,1,2,2,2], [1,0,1,2,1,2]],
      "coverage": "every_cell", "footprint": "box", "sharing": "exclusive" },
    { "id": "hollow", "box": [1,1,1,2,2,2], "coverage": "every_cell",
      "footprint": "box", "sharing": "exclusive" },
    { "id": "fire", "box": [1,0,1,2,1,2], "coverage": "every_cell",
      "footprint": "box", "sharing": "exclusive" }
  ],
  "checks": [
    { "id": "shell_bricks", "bay": "shell", "focus": { "kind": "bay" },
      "expect": { "kind": "each_cell",
        "element": { "kind": "block", "id": "minecraft:stone_bricks" } } },
    { "id": "hollow_air", "bay": "hollow", "focus": { "kind": "bay" },
      "expect": { "kind": "each_cell",
        "element": { "kind": "air" } } },
    { "id": "lit", "bay": "fire",
      "focus": { "kind": "occupant", "cell": [1,0,1] },
      "expect": { "kind": "all_of", "children": [
        { "kind": "block", "id": "minecraft:campfire" },
        { "kind": "property", "key": "lit", "value": "true" } ] } }
  ],
  "computations": [
    { "id": "air_cells", "kind": "air", "region": [[1,1,1,2,2,2]],
      "output": "mymod:air_cells" }
  ],
  "keys": [
    { "id": "mymod:air_cells", "type": "long",
      "retention": "instance", "merge": "sum" }
  ],
  "anchors": [
    { "role": "front", "property": "facing", "value": "north" }
  ],
  "orientation": {
    "primary": "front",
    "allowed": ["identity", "clockwise", "half", "counterclockwise"],
    "upright": true
  }
}`;

export const JAVA_SNIPPET = `ContextKey<Long> air = ContextKeys.of(id("air_cells"),
        Long.class, Retention.INSTANCE, Merges.sum(), null);

Blueprint campfire = Blueprint.builder(id("campfire"))
        .bounds(Cuboid.of(0, 0, 0, 3, 3, 3))
        .check(Check.of(id("shell_bricks"), shell, Focuses.bay(),
                Expectations.eachCell(Expectations.block(mc("stone_bricks")))))
        .check(Check.of(id("hollow_air"), hollow, Focuses.bay(),
                Expectations.eachCell(Expectations.air())))
        .check(Check.of(id("lit"), fire,
                Focuses.occupant(LatticePoint.of(1, 0, 1)),
                Expectations.allOf(
                        Expectations.block(mc("campfire")),
                        Expectations.property("lit", "true"))))
        .computation(Computations.air(air, hollow.cells()))
        .derive(capacitySpec)
        .build();`;

export const FURNACE = {
  title: 'Huge furnace',
  id: 'mymod:huge_furnace',
  bounds: '7×6×7',
  volume: 294,
  claims: 294,
  bays: [
    { id: 'wall', cells: 192, coverage: 'EVERY_CELL', footprint: 'Bound', note: 'four outer walls' },
    { id: 'cavity', cells: 100, coverage: 'EVERY_CELL', footprint: 'Box', note: '5×4×5 air' },
    { id: 'mouth', cells: 2, coverage: 'ONE_SUBJECT', footprint: 'Bound', note: 'one door, two cells' },
  ],
  note: 'The same model at 7×6×7. A door binds two cells; the cavity asserts 100 air cells and computes its own volume. 192 + 100 + 2 = 294 rows, one per owned cell, no overlap.',
};
