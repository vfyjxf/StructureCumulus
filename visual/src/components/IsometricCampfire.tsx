import { useMemo, useState } from 'react';
import { BAYS, CAMPFIRE_CELLS, type BayId } from '../data/campfire';

type Props = {
  activeBay: BayId | 'all';
  onBay: (bay: BayId | 'all') => void;
  exploded: boolean;
};

type Point = { x: number; y: number };

const CX = 320;
const CY = 212;

function project(x: number, y: number, z: number, exploded: boolean): Point {
  const lift = exploded ? 22 : 0;
  return {
    x: CX + (x - z) * 34,
    y: CY + (x + z) * 17 - y * (38 + lift),
  };
}

function cubeFaces(cell: { x: number; y: number; z: number }, exploded: boolean) {
  const p = project(cell.x, cell.y, cell.z, exploded);
  const top = { x: p.x, y: p.y - 14 };
  const left = { x: p.x - 28, y: top.y + 14 };
  const right = { x: p.x + 28, y: top.y + 14 };
  const bottom = { x: p.x, y: top.y + 28 };
  const h = 27;
  return {
    top: `${top.x},${top.y} ${right.x},${right.y} ${bottom.x},${bottom.y} ${left.x},${left.y}`,
    left: `${left.x},${left.y} ${bottom.x},${bottom.y} ${bottom.x},${bottom.y + h} ${left.x},${left.y + h}`,
    right: `${bottom.x},${bottom.y} ${right.x},${right.y} ${right.x},${right.y + h} ${bottom.x},${bottom.y + h}`,
    center: top,
    sort: (cell.x + cell.z) * 10 + cell.y,
  };
}

function bayClass(bay: BayId, face: 'top' | 'left' | 'right') {
  if (bay === 'shell') return `cube-shell-${face}`;
  if (bay === 'hollow') return `cube-air-${face}`;
  return `cube-fire-${face}`;
}

export function IsometricCampfire({ activeBay, onBay, exploded }: Props) {
  const [hover, setHover] = useState<string | null>(null);
  const cells = useMemo(
    () => CAMPFIRE_CELLS.map((cell) => ({ cell, faces: cubeFaces(cell, exploded) }))
      .sort((a, b) => a.faces.sort - b.faces.sort),
    [exploded],
  );

  const box = useMemo(() => {
    const corners = [
      [0, 0, 0], [3, 0, 0], [3, 0, 3], [0, 0, 3],
      [0, 3, 0], [3, 3, 0], [3, 3, 3], [0, 3, 3],
    ].map(([x, y, z]) => project(x, y, z, exploded));
    return {
      bottom: corners.slice(0, 4),
      top: corners.slice(4),
      corners,
    };
  }, [exploded]);

  const line = (a: Point, b: Point) => `M${a.x} ${a.y} L${b.x} ${b.y}`;
  const active = hover ?? (activeBay === 'all' ? null : activeBay);

  return (
    <svg className="campfire-svg" viewBox="0 0 640 430" role="img" aria-label="Exploded isometric view of the campfire blueprint">
      <defs>
        <pattern id="scene-grid" width="20" height="20" patternUnits="userSpaceOnUse">
          <path d="M 20 0 L 0 0 0 20" fill="none" stroke="rgba(238,232,218,.08)" strokeWidth="0.5" />
        </pattern>
      </defs>
      <rect x="0" y="0" width="640" height="430" fill="url(#scene-grid)" opacity="0.7" />

      <g opacity="0.52">
        <path d={line(box.bottom[0], box.bottom[1]) + ' ' + line(box.bottom[1], box.bottom[2]) + ' ' + line(box.bottom[2], box.bottom[3]) + ' ' + line(box.bottom[3], box.bottom[0])} fill="none" stroke="rgba(238,232,218,.36)" strokeWidth="0.7" strokeDasharray="3 4" />
        <path d={line(box.top[0], box.top[1]) + ' ' + line(box.top[1], box.top[2]) + ' ' + line(box.top[2], box.top[3]) + ' ' + line(box.top[3], box.top[0])} fill="none" stroke="rgba(238,232,218,.24)" strokeWidth="0.7" strokeDasharray="3 4" />
        {[0, 1, 2, 3].map((index) => (
          <path key={index} d={line(box.bottom[index], box.top[index])} fill="none" stroke="rgba(238,232,218,.2)" strokeWidth="0.7" strokeDasharray="2 5" />
        ))}
      </g>

      <g>
        {cells.map(({ cell, faces }) => {
          const isActive = active === cell.bay;
          const dim = active !== null && !isActive;
          return (
            <g
              key={`${cell.x}-${cell.y}-${cell.z}`}
              className="cube"
              data-dim={dim}
              data-active={isActive}
              onMouseEnter={() => setHover(cell.bay)}
              onMouseLeave={() => setHover(null)}
              onClick={() => onBay(cell.bay)}
              role="button"
              tabIndex={0}
              aria-label={`${cell.bay} cell ${cell.x},${cell.y},${cell.z}`}
              onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') onBay(cell.bay); }}
            >
              <polygon points={faces.top} className={bayClass(cell.bay, 'top')} stroke="rgba(9,12,12,.5)" strokeWidth="0.5" />
              <polygon points={faces.left} className={bayClass(cell.bay, 'left')} stroke="rgba(9,12,12,.5)" strokeWidth="0.5" />
              <polygon points={faces.right} className={bayClass(cell.bay, 'right')} stroke="rgba(9,12,12,.5)" strokeWidth="0.5" />
              {cell.bay !== 'shell' ? (
                <circle cx={faces.center.x} cy={faces.center.y + 10} r="2.4" fill={cell.bay === 'fire' ? '#ffd9a1' : '#eef8f3'} opacity="0.92" />
              ) : null}
            </g>
          );
        })}
      </g>

      <g className="scene-labels">
        <text x={project(3.1, 0, 0, exploded).x + 8} y={project(3.1, 0, 0, exploded).y + 4}>X</text>
        <text x={project(0, 0, 3.1, exploded).x - 12} y={project(0, 0, 3.1, exploded).y + 4}>Z</text>
        <text x={project(0, 3.15, 0, exploded).x - 4} y={project(0, 3.15, 0, exploded).y - 6}>Y</text>
        <text x="26" y="34" className="scene-caption">FIG. 03 / CAMPFIRE</text>
        <text x="26" y="48" className="scene-caption">3 × 3 × 3 · 27 cells</text>
        <text x="614" y="34" textAnchor="end" className="scene-caption">EXPLODED {exploded ? '1.6×' : 'OFF'}</text>
        <text x="614" y="48" textAnchor="end" className="scene-caption">{active ? active.toUpperCase() : 'ALL BAYS'}</text>
      </g>

      <g className="scene-labels">
        <path d="M 34 388 L 34 366 L 56 366" fill="none" stroke="rgba(238,232,218,.3)" strokeWidth="0.7" />
        <text x="62" y="370" className="scene-caption">one snapshot / one plan / one result</text>
        <text x="606" y="388" textAnchor="end" className="scene-caption">SHELL 25 · HOLLOW 1 · FIRE 1</text>
      </g>
    </svg>
  );
}
