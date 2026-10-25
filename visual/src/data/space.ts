export type Vec3 = [number, number, number];

import type { DemoScenario } from './types';

export const TURN_NAMES = ['NONE', 'CLOCKWISE', 'HALF', 'COUNTERCLOCKWISE'] as const;
export const TILT_NAMES = ['LEVEL', 'PITCH_UP', 'PITCH_DOWN', 'ROLL_LEFT', 'ROLL_RIGHT', 'INVERTED'] as const;
export const HEADING_NAMES = ['NORTH', 'EAST', 'SOUTH', 'WEST', 'UP', 'DOWN'] as const;

export type Turn = 0 | 1 | 2 | 3;
export type Tilt = 0 | 1 | 2 | 3 | 4 | 5;
export type Flip = 0 | 1;

export interface OrientationState {
  turn: Turn;
  tilt: Tilt;
  flip: Flip;
}

export interface Heading {
  name: (typeof HEADING_NAMES)[number];
  vec: Vec3;
}

export const HEADINGS: Record<(typeof HEADING_NAMES)[number], Vec3> = {
  NORTH: [0, 0, -1],
  EAST: [1, 0, 0],
  SOUTH: [0, 0, 1],
  WEST: [-1, 0, 0],
  UP: [0, 1, 0],
  DOWN: [0, -1, 0],
};

export const CARDINAL_HEADINGS: (typeof HEADING_NAMES)[number][] = ['NORTH', 'EAST', 'SOUTH', 'WEST'];

const turnVec = ([x, y, z]: Vec3, turn: Turn): Vec3 => {
  switch (turn) {
    case 0:
      return [x, y, z];
    case 1:
      return [-z, y, x];
    case 2:
      return [-x, y, -z];
    case 3:
      return [z, y, -x];
  }
};

const tiltVec = ([x, y, z]: Vec3, tilt: Tilt): Vec3 => {
  switch (tilt) {
    case 0:
      return [x, y, z];
    case 1:
      return [x, -z, y];
    case 2:
      return [x, z, -y];
    case 3:
      return [-y, x, z];
    case 4:
      return [y, -x, z];
    case 5:
      return [x, -y, -z];
  }
};

const flipVec = ([x, y, z]: Vec3, flip: Flip): Vec3 => (flip === 1 ? [-x, y, z] : [x, y, z]);

export const orientVec = (v: Vec3, state: OrientationState): Vec3 =>
  flipVec(tiltVec(turnVec(v, state.turn), state.tilt), state.flip);

export const ordinalOf = (state: OrientationState): number => (state.turn * 6 + state.tilt) * 2 + state.flip;

export const stateOfOrdinal = (ordinal: number): OrientationState => {
  const o = ((ordinal % 48) + 48) % 48;
  const flip = (o % 2) as Flip;
  const rest = Math.floor(o / 2);
  return { turn: Math.floor(rest / 6) as Turn, tilt: (rest % 6) as Tilt, flip };
};

export const headingOf = (v: Vec3): (typeof HEADING_NAMES)[number] | 'NONE' => {
  for (const name of HEADING_NAMES) {
    const h = HEADINGS[name];
    if (h[0] === v[0] && h[1] === v[1] && h[2] === v[2]) return name;
  }
  return 'NONE';
};

export const applyHeading = (heading: (typeof HEADING_NAMES)[number], state: OrientationState) =>
  headingOf(orientVec(HEADINGS[heading], state));

export const allStates = (): OrientationState[] =>
  Array.from({ length: 48 }, (_, i) => stateOfOrdinal(i));

export const satisfyingOrdinals = (
  authoredFront: (typeof HEADING_NAMES)[number],
  anchorHeading: (typeof HEADING_NAMES)[number],
): number[] => allStates().filter((s) => applyHeading(authoredFront, s) === anchorHeading).map(ordinalOf);

export const orientationId = (state: OrientationState): string =>
  `O-${String(ordinalOf(state)).padStart(2, '0')}`;

export const canonicalTriple = (state: OrientationState): string =>
  `(${TURN_NAMES[state.turn]}, ${TILT_NAMES[state.tilt]}, ${state.flip === 1 ? 'FRONT_BACK' : 'NONE'})`;

export const orientationNotes = [
  {
    title: 'Turn',
    body: 'Yaw about the world Y axis; cyclic of order 4. It fixes UP, so it never changes which face is up.',
  },
  {
    title: 'Tilt',
    body: 'A transversal of six proper rotations with six distinct up() images: UP, SOUTH, NORTH, WEST, EAST, DOWN. Six values, not four.',
  },
  {
    title: 'Parity',
    body: 'The determinant: FRONT_BACK is the canonical mirror. A mirrored placement is never reachable by turning.',
  },
  {
    title: 'Ordinal',
    body: 'ordinal = (turn × 6 + tilt) × 2 + parity. 4 × 6 × 2 = 48; the three-generator names are a surjection, not the storage.',
  },
];

export const SITING_EQUATION = 'candidate.apply(authoredFront) == anchorHeading';

export const SITING_NOTES = [
  'The anchor cell is the definition origin. It is read once, before any orientation is chosen, so there is no circularity.',
  'A candidate that does not satisfy the equation is not a candidate. There is no tie-break: orientation is a function of the anchor.',
  'For one authored front and one anchor heading, exactly 48 / 6 = 8 orientations satisfy the equation. The other 40 are rejected.',
  'A blueprint with no anchor takes a Siting directly — commands and tools bypass the resolver.',
];

export const CUBE = {
  size: 3,
  marker: { x: 1, y: 0, z: 1 },
  front: 'NORTH' as const,
  boundsLabel: '[0, 0, 0, 3, 3, 3]',
  markerLabel: '(1, 0, 1)',
};

/** The eight corners of a cuboid expressed as cells [0, size]. */
export const cuboidCorners = (sx: number, sy: number, sz: number): Vec3[] => {
  const out: Vec3[] = [];
  for (const x of [0, sx]) for (const y of [0, sy]) for (const z of [0, sz]) out.push([x, y, z]);
  return out;
};

export const imageCuboid = (size: Vec3, state: OrientationState) => {
  const corners = cuboidCorners(size[0], size[1], size[2]).map((c) => orientVec(c, state));
  const min: Vec3 = [Infinity, Infinity, Infinity];
  const max: Vec3 = [-Infinity, -Infinity, -Infinity];
  for (const c of corners) {
    for (let i = 0; i < 3; i++) {
      min[i] = Math.min(min[i], c[i]);
      max[i] = Math.max(max[i], c[i]);
    }
  }
  return { min, max };
};

export const fmtVec = (v: Vec3): string => `(${v[0]}, ${v[1]}, ${v[2]})`;

/* ---------- scenario playback: turn through the group ---------- */

export type SpaceScenarioId = 'spin' | 'satisfying';

export const SPACE_SCENARIOS: DemoScenario<SpaceScenarioId>[] = [
  {
    id: 'spin',
    label: 'Turn all 48',
    note: 'Cycles every ordinal at 600 ms per step, 4 turns × 6 tilts × 2 parities. Hover or focus the elevation to hold the frame.',
    steps: 48,
  },
  {
    id: 'satisfying',
    label: 'Only the 8 satisfying',
    note: 'Visits just the ordinals whose candidate maps the authored front onto the anchor heading; the other 40 never qualify.',
    steps: 8,
  },
];

export const SPACE_SPIN_INTERVAL = 600;
