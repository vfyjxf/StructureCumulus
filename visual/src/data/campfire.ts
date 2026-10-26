export type BayId = 'shell' | 'hollow' | 'fire';

export type CampfireCell = {
  x: number;
  y: number;
  z: number;
  bay: BayId;
};

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

export const STAGES = [
  {
    id: 'compile',
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
      ['IR parity', 'same'],
    ],
  },
  {
    id: 'check',
    no: '02',
    label: 'CHECK',
    title: 'One immutable look',
    result: 'Admitted',
    note: 'The plan runs entirely: sections fold, aggregates read, checks meet. Claims are predicted but not written. The lease is the only gate.',
    readout: [
      ['result', 'Admitted'],
      ['checks', '3 / 3 matched'],
      ['air_cells', '1'],
      ['spend', '27 cells · 3 sections'],
      ['claims', 'predicted 27'],
    ],
  },
  {
    id: 'settle',
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
  },
  {
    id: 'derive',
    no: '04',
    label: 'DERIVE',
    title: 'One more pure fold',
    result: 'StructureContext',
    note: 'A deriver reads only the published context. capacity derives from air; the second publication keeps the settle spend and bumps the revision once.',
    readout: [
      ['air_cells', '1'],
      ['capacity', '10'],
      ['context', 'rev 2'],
      ['spend', '27 cells · 3 sections'],
      ['writes', 'one batch'],
    ],
  },
];

export const DOCUMENT_SNIPPET = `{
  "schemaVersion": 1,
  "id": "mymod:campfire",
  "bounds": [0, 0, 0, 3, 3, 3],
  "bays": [
    { "id": "shell", "boxes": [[0,0,0,3,3,3]],
      "minus": [[1,1,1,2,2,2], [1,0,1,2,1,2]] },
    { "id": "hollow", "boxes": [[1,1,1,2,2,2]] },
    { "id": "fire",   "boxes": [[1,0,1,2,1,2]] }
  ],
  "checks": [
    { "id": "lit", "bay": "fire",
      "focus": { "kind": "occupant", "cell": [1,0,1] },
      "expect": { "kind": "all_of", "children": [
        { "kind": "block", "id": "minecraft:campfire" },
        { "kind": "property", "key": "lit", "value": "true" }
      ] } }
  ],
  "computations": [
    { "id": "air_cells", "kind": "air",
      "region": [[1,1,1,2,2,2]], "output": "air_cells" }
  ],
  "keys": [
    { "id": "air_cells", "type": "long",
      "retention": "instance", "merge": "sum" }
  ]
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
  claims: 294,
  bays: [
    { id: 'wall', cells: 192, coverage: 'EVERY_CELL', footprint: 'Bound' },
    { id: 'cavity', cells: 100, coverage: 'EVERY_CELL', footprint: 'Box' },
    { id: 'mouth', cells: 2, coverage: 'ONE_SUBJECT', footprint: 'Bound' },
  ],
  note: 'The same model at 7×6×7. A door binds two cells; the cavity asserts 100 air cells and computes its own volume.',
};
