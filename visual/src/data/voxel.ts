export type VoxelKind = 'shell' | 'air' | 'mouth';
export type BayId = 'wall' | 'mouth' | 'interior';

export interface Voxel {
  x: number;
  y: number;
  z: number;
  kind: VoxelKind;
  bay: BayId;
}

export const VOXEL_BOUNDS = { sx: 7, sy: 6, sz: 7 } as const;
export const CAVITY = { x0: 1, y0: 1, z0: 1, x1: 6, y1: 5, z1: 6 } as const;
export const MOUTH: ReadonlyArray<readonly [number, number, number]> = [
  [3, 1, 0],
  [3, 2, 0],
];

export const BAY_META: Record<BayId, { cells: number; coverage: string; subject: string; stage: string; claim: string; enabled: boolean }> = {
  wall: { cells: 192, coverage: 'CUBIC', subject: 'OccupantSnapshot', stage: 'FORM', claim: 'Owned', enabled: true },
  mouth: { cells: 2, coverage: 'SUBJECT', subject: 'DoorView', stage: 'FORM', claim: 'Occupied', enabled: true },
  interior: { cells: 100, coverage: 'CUBIC', subject: 'OccupantSnapshot', stage: 'SETTLE', claim: 'Owned', enabled: true },
};

const inCavity = (x: number, y: number, z: number) =>
  x >= CAVITY.x0 && x < CAVITY.x1 && y >= CAVITY.y0 && y < CAVITY.y1 && z >= CAVITY.z0 && z < CAVITY.z1;

const inMouth = (x: number, y: number, z: number) => MOUTH.some(([mx, my, mz]) => mx === x && my === y && mz === z);

export function buildVoxels(): Voxel[] {
  const out: Voxel[] = [];
  for (let y = 0; y < VOXEL_BOUNDS.sy; y += 1) {
    for (let z = 0; z < VOXEL_BOUNDS.sz; z += 1) {
      for (let x = 0; x < VOXEL_BOUNDS.sx; x += 1) {
        if (inMouth(x, y, z)) out.push({ x, y, z, kind: 'mouth', bay: 'mouth' });
        else if (inCavity(x, y, z)) out.push({ x, y, z, kind: 'air', bay: 'interior' });
        else out.push({ x, y, z, kind: 'shell', bay: 'wall' });
      }
    }
  }
  return out;
}

export const VOXELS = buildVoxels();
export const VOXEL_INDEX = new Map(VOXELS.map((v) => [`${v.x},${v.y},${v.z}`, v]));

export function voxelAt(x: number, y: number, z: number): Voxel | undefined {
  return VOXEL_INDEX.get(`${x},${y},${z}`);
}

export function cellKey(v: { x: number; y: number; z: number }): string {
  return `${v.x},${v.y},${v.z}`;
}

export function planeCells(axis: 'x' | 'y' | 'z', index: number): Voxel[] {
  return VOXELS.filter((v) => (axis === 'x' ? v.x === index : axis === 'y' ? v.y === index : v.z === index));
}

export const VOXEL_COUNTS = {
  total: VOXELS.length,
  shell: VOXELS.filter((v) => v.kind === 'shell').length,
  air: VOXELS.filter((v) => v.kind === 'air').length,
  mouth: VOXELS.filter((v) => v.kind === 'mouth').length,
};
