import { useRef, type PointerEvent, type WheelEvent } from 'react';
import type { BayId, Voxel } from '../data/voxel';
import { VOXEL_BOUNDS, cellKey } from '../data/voxel';

export interface Camera {
  az: number;
  el: number;
  zoom: number;
}

interface Point {
  X: number;
  Y: number;
  depth: number;
}

const rad = (deg: number) => (deg * Math.PI) / 180;
const CENTER = { x: VOXEL_BOUNDS.sx / 2, y: VOXEL_BOUNDS.sy / 2, z: VOXEL_BOUNDS.sz / 2 };

function project(x: number, y: number, z: number, cam: Camera, cx: number, cy: number): Point {
  const a = rad(cam.az);
  const xr = (x - CENTER.x) * Math.cos(a) - (z - CENTER.z) * Math.sin(a);
  const zr = (x - CENTER.x) * Math.sin(a) + (z - CENTER.z) * Math.cos(a);
  const lift = y - CENTER.y;
  return {
    X: cx + xr * cam.zoom,
    Y: cy + (-lift + zr * Math.sin(cam.el) * 0.9) * cam.zoom,
    depth: zr * Math.cos(cam.el) + lift * 0.8,
  };
}

function faceFill(v: Voxel, face: 'top' | 'side' | 'dark', overlay: 'kind' | 'bay' | 'claim'): string {
  if (overlay === 'bay') {
    const bay: Record<BayId, string> = { wall: '#B6AF9C', mouth: '#FF8200', interior: '#0072CE' };
    return bay[v.bay];
  }
  if (overlay === 'claim') {
    return v.kind === 'air' ? 'rgba(0,104,63,0.08)' : 'rgba(0,104,63,0.28)';
  }
  if (v.kind === 'mouth') return face === 'top' ? '#FF8200' : face === 'side' ? '#D96A00' : '#A84600';
  if (v.kind === 'air') return 'rgba(0,114,206,0.06)';
  return face === 'top' ? '#E6E2D4' : face === 'side' ? '#CFC9B8' : '#B6AF9C';
}

interface VoxelCanvasProps {
  voxels: Voxel[];
  camera: Camera;
  onCamera: (camera: Camera) => void;
  layer: number;
  bays: Set<BayId>;
  overlay: 'kind' | 'bay' | 'claim';
  selected: Voxel | null;
  onSelect: (voxel: Voxel) => void;
  highlight?: Set<string>;
  cutaway?: boolean;
  height?: number;
}

export function VoxelCanvas({
  voxels,
  camera,
  onCamera,
  layer,
  bays,
  overlay,
  selected,
  onSelect,
  highlight,
  cutaway = false,
  height = 460,
}: VoxelCanvasProps) {
  const width = 640;
  const cx = width / 2;
  const cy = height * 0.62;
  const drag = useRef<{ x: number; y: number } | null>(null);

  const visible = voxels
    .filter((v) => v.y <= layer && bays.has(v.bay))
    .filter((v) => (cutaway ? v.z <= 3 || v.x <= 3 : true))
    .map((v) => {
      const corners = [
        project(v.x, v.y, v.z, camera, cx, cy),
        project(v.x + 1, v.y, v.z, camera, cx, cy),
        project(v.x, v.y, v.z + 1, camera, cx, cy),
        project(v.x + 1, v.y, v.z + 1, camera, cx, cy),
        project(v.x, v.y + 1, v.z, camera, cx, cy),
        project(v.x + 1, v.y + 1, v.z, camera, cx, cy),
        project(v.x, v.y + 1, v.z + 1, camera, cx, cy),
        project(v.x + 1, v.y + 1, v.z + 1, camera, cx, cy),
      ];
      return { v, corners, depth: (corners[0].depth + corners[7].depth) / 2 };
    })
    .sort((a, b) => a.depth - b.depth);

  const a = rad(camera.az);
  const useXHigh = Math.cos(a) >= 0;
  const useZHigh = Math.sin(a) >= 0;

  const onPointerDown = (event: PointerEvent<SVGSVGElement>) => {
    drag.current = { x: event.clientX, y: event.clientY };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const onPointerMove = (event: PointerEvent<SVGSVGElement>) => {
    if (!drag.current) return;
    const dx = event.clientX - drag.current.x;
    const dy = event.clientY - drag.current.y;
    drag.current = { x: event.clientX, y: event.clientY };
    onCamera({
      az: camera.az + dx * 0.5,
      el: Math.max(0.22, Math.min(1.25, camera.el + dy * 0.004)),
      zoom: camera.zoom,
    });
  };
  const endDrag = () => {
    drag.current = null;
  };
  const onWheel = (event: WheelEvent<SVGSVGElement>) => {
    event.preventDefault();
    onCamera({ ...camera, zoom: Math.max(14, Math.min(64, camera.zoom * Math.exp(-event.deltaY * 0.0012))) });
  };

  return (
    <svg
      className="np-voxel"
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label="3D voxel view of the furnace"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onPointerLeave={endDrag}
      onWheel={onWheel}
    >
      <defs>
        <pattern id="voxel-air" width="6" height="6" patternUnits="userSpaceOnUse">
          <path d="M0 6 L6 0" stroke="#0072CE" strokeWidth="0.7" opacity="0.5" />
        </pattern>
      </defs>
      {visible.map(({ v, corners }) => {
        const p = (i: number) => `${corners[i].X.toFixed(2)},${corners[i].Y.toFixed(2)}`;
        const top = `${p(4)} ${p(5)} ${p(7)} ${p(6)}`;
        const sideX = useXHigh ? `${p(1)} ${p(5)} ${p(7)} ${p(3)}` : `${p(0)} ${p(4)} ${p(6)} ${p(2)}`;
        const sideZ = useZHigh ? `${p(2)} ${p(6)} ${p(7)} ${p(3)}` : `${p(0)} ${p(4)} ${p(5)} ${p(1)}`;
        const isSelected = selected != null && selected.x === v.x && selected.y === v.y && selected.z === v.z;
        const isHighlighted = highlight?.has(cellKey(v)) ?? false;
        const stroke = isSelected ? '#FF8200' : v.kind === 'air' ? '#0072CE' : '#14160F';
        const strokeWidth = isSelected ? 1.8 : v.kind === 'air' ? 0.5 : 0.7;
        const dash = v.kind === 'air' ? '3 3' : undefined;
        return (
          <g
            key={cellKey(v)}
            className="np-voxel-cell"
            onPointerEnter={() => onSelect(v)}
            role="presentation"
          >
            <polygon points={top} fill={v.kind === 'air' ? 'url(#voxel-air)' : faceFill(v, 'top', overlay)} stroke={stroke} strokeWidth={strokeWidth} strokeDasharray={dash} />
            <polygon points={sideX} fill={faceFill(v, 'side', overlay)} stroke={stroke} strokeWidth={strokeWidth} strokeDasharray={dash} />
            <polygon points={sideZ} fill={faceFill(v, 'dark', overlay)} stroke={stroke} strokeWidth={strokeWidth} strokeDasharray={dash} />
            {isHighlighted ? (
              <g pointerEvents="none">
                <polygon points={top} fill="#FF8200" fillOpacity={0.24} />
                <polygon points={sideX} fill="#FF8200" fillOpacity={0.16} />
                <polygon points={sideZ} fill="#FF8200" fillOpacity={0.1} />
              </g>
            ) : null}
          </g>
        );
      })}
    </svg>
  );
}
