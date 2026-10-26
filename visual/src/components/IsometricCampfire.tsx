import { useMemo } from 'react';
import { CAMPFIRE_CELLS, FIRE_CELL, HOLLOW_CELL, type BayId, type CampfireCell } from '../data/campfire';

type Props = {
  selectedBay: BayId | 'all';
  focusBay: BayId | null;
  hoverCell: CampfireCell | null;
  onHoverCell: (cell: CampfireCell | null) => void;
  onSelectBay: (bay: BayId) => void;
  exploded: boolean;
  cutaway: boolean;
};

type P = { x: number; y: number };
type Box = { x0: number; y0: number; x1: number; y1: number };

const W = 68;
const H = 33;
const V = 68;
const GAP = 30;
const CX = 260;
const CY = 300;

function p(x: number, y: number, z: number): P {
  return { x: CX + (x - z) * W, y: CY + (x + z) * H - y * V };
}

const pts = (points: P[]) => points.map((point) => `${point.x.toFixed(2)},${point.y.toFixed(2)}`).join(' ');

type Geometry = {
  top: string;
  front: string;
  side: string;
  under: string;
  backX: string;
  backZ: string;
  topCenter: P;
  box: Box;
};

function geometry({ x, y, z }: CampfireCell): Geometry {
  const tA = p(x, y + 1, z);
  const tB = p(x + 1, y + 1, z);
  const tC = p(x + 1, y + 1, z + 1);
  const tD = p(x, y + 1, z + 1);
  const bA = p(x, y, z);
  const bB = p(x + 1, y, z);
  const bC = p(x + 1, y, z + 1);
  const bD = p(x, y, z + 1);
  return {
    top: pts([tA, tB, tC, tD]),
    front: pts([tD, tC, bC, bD]),
    side: pts([tB, tC, bC, bB]),
    under: pts([bA, bB, bC, bD]),
    backX: pts([bA, bD, tD, tA]),
    backZ: pts([bA, bB, tB, tA]),
    topCenter: p(x + 0.5, y + 1, z + 0.5),
    box: { x0: tD.x, y0: tA.y, x1: tB.x, y1: bC.y },
  };
}

type Cell = { cell: CampfireCell; g: Geometry };

const CELLS: Cell[] = CAMPFIRE_CELLS
  .map((cell) => ({ cell, g: geometry(cell) }))
  .sort((a, b) => {
    const depth = a.cell.x + a.cell.y + a.cell.z - (b.cell.x + b.cell.y + b.cell.z);
    if (depth !== 0) return depth;
    return a.cell.z - b.cell.z || a.cell.x - b.cell.x;
  });

const keyOf = (cell: { x: number; y: number; z: number }) => `${cell.x}-${cell.y}-${cell.z}`;

function unionBox(boxes: Box[]): Box {
  return boxes.reduce(
    (acc, box) => ({
      x0: Math.min(acc.x0, box.x0),
      y0: Math.min(acc.y0, box.y0),
      x1: Math.max(acc.x1, box.x1),
      y1: Math.max(acc.y1, box.y1),
    }),
    boxes[0],
  );
}

function bracket(box: Box, pad = 7, arm = 13): string {
  const x0 = box.x0 - pad;
  const y0 = box.y0 - pad;
  const x1 = box.x1 + pad;
  const y1 = box.y1 + pad;
  return [
    `M${x0} ${y0 + arm} L${x0} ${y0} L${x0 + arm} ${y0}`,
    `M${x1 - arm} ${y0} L${x1} ${y0} L${x1} ${y0 + arm}`,
    `M${x1} ${y1 - arm} L${x1} ${y1} L${x1 - arm} ${y1}`,
    `M${x0 + arm} ${y1} L${x0} ${y1} L${x0} ${y1 - arm}`,
  ].join(' ');
}

function isGhost(cell: CampfireCell): boolean {
  return cell.bay === 'shell' && cell.x >= 1 && cell.y >= 1 && cell.z >= 1;
}

function faceClass(bay: BayId): string {
  if (bay === 'fire') return 'wb-face wb-fire-face';
  if (bay === 'hollow') return 'wb-face wb-air-face';
  return 'wb-face wb-stone-face';
}

const FLOOR = [p(0, 0, 0), p(3, 0, 0), p(3, 0, 3), p(0, 0, 3)];
const FLOOR_LINES = Array.from({ length: 4 }, (_, index) => [
  [p(index, 0, 0), p(index, 0, 3)],
  [p(0, 0, index), p(3, 0, index)],
]).flat();

const FIRE = geometry(FIRE_CELL);
const HOLLOW = geometry(HOLLOW_CELL);

export function IsometricCampfire({
  selectedBay,
  focusBay,
  hoverCell,
  onHoverCell,
  onSelectBay,
  exploded,
  cutaway,
}: Props) {
  const focusCells = useMemo(
    () => (focusBay ? CELLS.filter((entry) => entry.cell.bay === focusBay) : []),
    [focusBay],
  );
  const focusBox = focusBay && focusCells.length
    ? unionBox(focusCells.map((entry) => entry.g.box))
    : null;

  const voids = useMemo(() => {
    const set = new Set<string>();
    for (const { cell } of CELLS) {
      if (cell.bay === 'hollow') set.add(keyOf(cell));
      else if (cutaway && !exploded && isGhost(cell)) set.add(keyOf(cell));
    }
    return set;
  }, [cutaway, exploded]);

  const isVoid = (x: number, y: number, z: number) =>
    x >= 0 && x < 3 && y >= 0 && y < 3 && z >= 0 && z < 3 && voids.has(`${x}-${y}-${z}`);

  const hoverKey = hoverCell ? keyOf(hoverCell) : null;

  const chamberLabel = exploded
    ? 'Exploded isometric view of the campfire blueprint, three layers separated, fire cell pulled out'
    : cutaway
      ? 'Cutaway isometric view of the campfire blueprint, near octant shown as x-ray'
      : 'Assembled isometric view of the campfire blueprint';

  const renderCube = (
    cell: CampfireCell,
    g: Geometry,
    options: { ghost?: boolean; transform?: string } = {},
  ) => {
    const key = keyOf(cell);
    const ghost = options.ghost ?? false;
    const dim = focusBay !== null && cell.bay !== focusBay;
    const isHover = hoverKey === key;
    const locked = selectedBay !== 'all'
      && cell.bay === selectedBay
      && (focusBay === null || focusBay === selectedBay);
    const face = faceClass(cell.bay);
    const stone = cell.bay === 'shell';
    const cutTop = stone && isVoid(cell.x, cell.y + 1, cell.z);
    const cutFront = stone && isVoid(cell.x, cell.y, cell.z + 1);
    const cutSide = stone && isVoid(cell.x + 1, cell.y, cell.z);
    return (
      <g
        key={key}
        className="wb-cube"
        data-bay={cell.bay}
        data-ghost={ghost}
        data-state={dim ? 'dim' : 'base'}
        data-focus={focusBay !== null && cell.bay === focusBay}
        data-solo={selectedBay !== 'all' && focusBay === selectedBay}
        data-hover={isHover}
        data-locked={locked}
        style={{ transform: `${options.transform ?? ''} translateY(${exploded ? -cell.y * GAP : 0}px)` }}
        onMouseEnter={() => {
          onHoverCell(cell);
        }}
        onMouseLeave={() => {
          onHoverCell(null);
        }}
        onClick={() => onSelectBay(cell.bay)}
        role="presentation"
      >
        <polygon points={g.under} className={`${face} wb-face-inner`} />
        <polygon points={g.backX} className={`${face} wb-face-inner`} />
        <polygon points={g.backZ} className={`${face} wb-face-inner`} />
        <polygon points={g.top} className={`${face} wb-face-top${cutTop ? ' wb-face-cut' : ''}`} />
        <polygon points={g.front} className={`${face} wb-face-front${cutFront ? ' wb-face-cut' : ''}`} />
        <polygon points={g.side} className={`${face} wb-face-side${cutSide ? ' wb-face-cut' : ''}`} />
        {cutTop && !ghost ? <polygon points={g.top} className="wb-cut-hatch" /> : null}
        {cutFront && !ghost ? <polygon points={g.front} className="wb-cut-hatch" /> : null}
        {cutSide && !ghost ? <polygon points={g.side} className="wb-cut-hatch" /> : null}
      </g>
    );
  };

  const pull = { x: -30, y: 42 };
  const pullTransform = `translate(${pull.x}px, ${pull.y}px)`;
  const pulled = { x: FIRE.topCenter.x + pull.x, y: FIRE.topCenter.y + pull.y };

  return (
    <svg className="wb-svg" viewBox="0 0 520 560" role="group" aria-label={chamberLabel}>
      <defs>
        <radialGradient id="wb-fire-pool" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#ff8a54" stopOpacity="0.22" />
          <stop offset="55%" stopColor="#ff6b3d" stopOpacity="0.1" />
          <stop offset="100%" stopColor="#ff6b3d" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="wb-ground" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#0a0d0e" stopOpacity="0.5" />
          <stop offset="70%" stopColor="#0a0d0e" stopOpacity="0.16" />
          <stop offset="100%" stopColor="#0a0d0e" stopOpacity="0" />
        </radialGradient>
        <pattern id="wb-cut-hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <line x1="0" y1="0" x2="0" y2="6" stroke="rgba(238,232,218,0.1)" strokeWidth="0.55" />
        </pattern>
        <pattern id="wb-ghost-hatch" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <line x1="0" y1="0" x2="0" y2="7" stroke="rgba(238,232,218,0.1)" strokeWidth="0.5" />
        </pattern>
      </defs>

      <g className="wb-scope" aria-hidden="true">
        <circle cx="260" cy="318" r="236" className="wb-scope-ring" />
        <circle cx="260" cy="318" r="182" className="wb-scope-ring wb-scope-dash" />
        <path d="M260 50 L260 552" className="wb-scope-axis" />
        <path d="M22 318 L498 318" className="wb-scope-axis" />
        {[0, 45, 90, 135, 180, 225, 270, 315].map((angle) => {
          const rad = (angle * Math.PI) / 180;
          return (
            <line
              key={angle}
              x1={260 + Math.cos(rad) * 226}
              y1={318 + Math.sin(rad) * 226}
              x2={260 + Math.cos(rad) * 234}
              y2={318 + Math.sin(rad) * 234}
              className="wb-scope-tick"
            />
          );
        })}
      </g>

      <g className="wb-floor" aria-hidden="true">
        <polygon points={pts(FLOOR)} className="wb-floor-plate" />
        {FLOOR_LINES.map((line, index) => (
          <line
            key={index}
            x1={line[0].x}
            y1={line[0].y}
            x2={line[1].x}
            y2={line[1].y}
            className="wb-floor-line"
          />
        ))}
        <polygon points={pts(FLOOR)} className="wb-floor-edge" />
        <ellipse cx="260" cy="440" rx="196" ry="70" fill="url(#wb-ground)" />
      </g>

      <circle
        cx={FIRE.topCenter.x}
        cy={FIRE.topCenter.y}
        r="66"
        fill="url(#wb-fire-pool)"
        className="wb-fire-pool"
        aria-hidden="true"
      />

      <g className="wb-drop-guides" data-on={exploded} aria-hidden="true">
        {[p(0, 0, 0), p(3, 0, 0), p(3, 0, 3), p(0, 0, 3)].map((corner, index) => (
          <line
            key={index}
            x1={corner.x}
            y1={corner.y}
            x2={corner.x}
            y2={corner.y - V * 3 - GAP * 2}
            className="wb-drop-line"
          />
        ))}
        <line x1="260" y1="36" x2="260" y2="498" className="wb-drop-spine" />
      </g>

      <g className="wb-object">
        {CELLS
          .filter((entry) => entry.cell.bay !== 'hollow')
          .filter((entry) => !(exploded && entry.cell.bay === 'fire'))
          .map(({ cell, g }) => renderCube(cell, g, {
            ghost: cutaway && !exploded && isGhost(cell),
          }))}
      </g>

      {exploded || cutaway ? (
        <g
          className="wb-air"
          style={{ transform: `translateY(${exploded ? -HOLLOW_CELL.y * GAP : 0}px)` }}
          aria-hidden="true"
        >
          <polygon points={HOLLOW.top} className="wb-air-face" />
          <polygon points={HOLLOW.front} className="wb-air-face" />
          <polygon points={HOLLOW.side} className="wb-air-face" />
          <circle cx={HOLLOW.topCenter.x} cy={HOLLOW.topCenter.y} r="2.2" className="wb-air-pip" />
          <path
            d={`M${HOLLOW.topCenter.x - 7} ${HOLLOW.topCenter.y} L${HOLLOW.topCenter.x + 7} ${HOLLOW.topCenter.y} M${HOLLOW.topCenter.x} ${HOLLOW.topCenter.y - 4} L${HOLLOW.topCenter.x} ${HOLLOW.topCenter.y + 4}`}
            className="wb-air-cross"
          />
        </g>
      ) : null}

      {!exploded && cutaway ? (
        <g className="wb-fire-mark" aria-hidden="true">
          <polygon
            points={`${FIRE.topCenter.x},${FIRE.topCenter.y - 7} ${FIRE.topCenter.x + 11},${FIRE.topCenter.y} ${FIRE.topCenter.x},${FIRE.topCenter.y + 7} ${FIRE.topCenter.x - 11},${FIRE.topCenter.y}`}
            className="wb-ember"
          />
          <polygon
            points={`${FIRE.topCenter.x},${FIRE.topCenter.y - 3.4} ${FIRE.topCenter.x + 5.4},${FIRE.topCenter.y} ${FIRE.topCenter.x},${FIRE.topCenter.y + 3.4} ${FIRE.topCenter.x - 5.4},${FIRE.topCenter.y}`}
            className="wb-ember-core"
          />
        </g>
      ) : null}

      {exploded ? (
        <g className="wb-fire-detail">
          <polygon points={FIRE.top} className="wb-fire-socket" />
          <path
            d={`M${FIRE.topCenter.x} ${FIRE.topCenter.y} L${pulled.x} ${pulled.y}`}
            className="wb-fire-leader"
          />
          <circle cx={pulled.x} cy={pulled.y} r="48" fill="url(#wb-fire-pool)" />
          {renderCube(FIRE_CELL, FIRE, { transform: pullTransform })}
          <g style={{ transform: pullTransform }} aria-hidden="true">
            <polygon
              points={`${FIRE.topCenter.x},${FIRE.topCenter.y - 7} ${FIRE.topCenter.x + 11},${FIRE.topCenter.y} ${FIRE.topCenter.x},${FIRE.topCenter.y + 7} ${FIRE.topCenter.x - 11},${FIRE.topCenter.y}`}
              className="wb-ember"
            />
            <polygon
              points={`${FIRE.topCenter.x},${FIRE.topCenter.y - 3.4} ${FIRE.topCenter.x + 5.4},${FIRE.topCenter.y} ${FIRE.topCenter.x},${FIRE.topCenter.y + 3.4} ${FIRE.topCenter.x - 5.4},${FIRE.topCenter.y}`}
              className="wb-ember-core"
            />
          </g>
        </g>
      ) : null}

      {!exploded ? (
        <g className="wb-bay-tags" aria-hidden="true">
          <g className="wb-bay-tag" data-bay="shell">
            <rect x="60" y="156" width="5" height="5" />
            <text x="70" y="161">SHELL 25</text>
            <path d="M70 164 L118 194" />
          </g>
          {cutaway ? (
            <>
              <g className="wb-bay-tag" data-bay="hollow">
                <rect x="330" y="236" width="5" height="5" />
                <text x="340" y="241">HOLLOW 1</text>
                <path d="M336 246 L302 257" />
              </g>
              <g className="wb-bay-tag" data-bay="fire">
                <rect x="330" y="352" width="5" height="5" />
                <text x="340" y="357">FIRE 1</text>
                <path d="M336 358 L300 342" />
              </g>
            </>
          ) : null}
        </g>
      ) : null}

      {focusBox && !exploded ? (
        <path
          d={bracket(focusBox)}
          className="wb-focus-bracket"
          data-locked={selectedBay !== 'all' && selectedBay === focusBay}
          aria-hidden="true"
        />
      ) : null}

      {hoverCell && !exploded ? (
        <g className="wb-cell-tag" aria-hidden="true">
          <circle cx={geometry(hoverCell).topCenter.x} cy={geometry(hoverCell).topCenter.y - 12} r="1.6" />
          <path
            d={`M${geometry(hoverCell).topCenter.x} ${geometry(hoverCell).topCenter.y - 12} L${geometry(hoverCell).topCenter.x + 16} ${geometry(hoverCell).topCenter.y - 24} L${geometry(hoverCell).topCenter.x + 21} ${geometry(hoverCell).topCenter.y - 24}`}
          />
          <text x={geometry(hoverCell).topCenter.x + 25} y={geometry(hoverCell).topCenter.y - 21}>
            {`(${hoverCell.x},${hoverCell.y},${hoverCell.z})`}
          </text>
        </g>
      ) : null}

      <g className="wb-axis-rose" aria-hidden="true" transform="translate(30 500)">
        <path d="M0 0 L0 -38" />
        <path d="M0 0 L34 19" />
        <path d="M0 0 L-34 19" />
        <path d="M-3 -32 L0 -38 L3 -32" />
        <path d="M28 15 L34 19 L27 20" />
        <path d="M-28 15 L-34 19 L-27 20" />
        <text x="0" y="-44" textAnchor="middle">Y</text>
        <text x="42" y="26">X</text>
        <text x="-42" y="26" textAnchor="end">Z</text>
        <text x="0" y="36" textAnchor="middle" className="wb-axis-cap">LOCAL / 3×3×3</text>
      </g>

      <g className="wb-registration" aria-hidden="true">
        <path d="M16 30 L16 16 L30 16" />
        <path d="M490 16 L504 16 L504 30" />
        <path d="M504 530 L504 544 L490 544" />
        <path d="M30 544 L16 544 L16 530" />
        <text x="260" y="552" textAnchor="middle" className="wb-scene-mark">
          LOCAL FRAME · 27 CELLS · 25 + 1 + 1
        </text>
      </g>
    </svg>
  );
}
