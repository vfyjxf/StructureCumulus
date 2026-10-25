import { useMemo, useState } from 'react';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { Segmented, NumberStepper, Toggle } from '../components/Controls';
import { useExplain } from '../components/ExplainDrawer';
import { useLang } from '../i18n/LangContext';
import { EXTRA } from '../i18n/extra';
import { BAY_META, VOXEL_BOUNDS, cellKey, planeCells, type Voxel } from '../data/voxel';

type Axis = 'x' | 'y' | 'z';
const CELL = 30;
const PAD = 34;

const dims = (axis: Axis): { w: number; h: number; max: number } =>
  axis === 'y'
    ? { w: VOXEL_BOUNDS.sx, h: VOXEL_BOUNDS.sz, max: VOXEL_BOUNDS.sy - 1 }
    : axis === 'x'
      ? { w: VOXEL_BOUNDS.sz, h: VOXEL_BOUNDS.sy, max: VOXEL_BOUNDS.sx - 1 }
      : { w: VOXEL_BOUNDS.sx, h: VOXEL_BOUNDS.sy, max: VOXEL_BOUNDS.sz - 1 };

const toCell = (axis: Axis, gx: number, gy: number, index: number): [number, number, number] =>
  axis === 'y' ? [gx, index, gy] : axis === 'x' ? [index, gy, gx] : [gx, gy, index];

export function SliceView() {
  const { lang } = useLang();
  const copy = EXTRA[lang].slice;
  const { open } = useExplain();
  const [axis, setAxis] = useState<Axis>('y');
  const [index, setIndex] = useState(1);
  const [compare, setCompare] = useState(false);
  const [hover, setHover] = useState<Voxel | null>(null);
  const [picked, setPicked] = useState<Voxel | null>(null);

  const { w, h, max } = dims(axis);
  const plane = useMemo(() => planeCells(axis, index), [axis, index]);
  const byCell = useMemo(() => new Map(plane.map((v) => [cellKey(v), v])), [plane]);

  const conflicts = useMemo(() => {
    if (!compare) return new Set<string>();
    const out = new Set<string>();
    const present = new Set(plane.map(cellKey));
    for (const v of plane) {
      const bx = v.x - 2;
      const bz = v.z - 1;
      if (bx >= 0 && bx < VOXEL_BOUNDS.sx && bz >= 0 && bz < VOXEL_BOUNDS.sz && present.has(`${bx},${v.y},${bz}`)) {
        out.add(cellKey(v));
      }
    }
    return out;
  }, [compare, plane]);

  const selected = picked ?? hover;
  const kindLabel = selected
    ? selected.kind === 'shell'
      ? lang === 'zh'
        ? '外壳'
        : 'Shell'
      : selected.kind === 'air'
        ? lang === 'zh'
          ? '空气'
          : 'Air'
        : lang === 'zh'
          ? '门'
          : 'Door'
    : copy.none;
  const width = PAD * 2 + w * CELL;
  const height = PAD * 2 + h * CELL;

  return (
    <div className="g12">
      <div className="c7">
        <Panel title={copy.panel} count={copy.sub}>
          <div className="np-slice-svg">
            <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={copy.panel}>
              <defs>
                <pattern id="slice-air" width="6" height="6" patternUnits="userSpaceOnUse">
                  <path d="M0 6 L6 0" stroke="#0072CE" strokeWidth="0.6" opacity="0.35" />
                </pattern>
              </defs>
              {Array.from({ length: h }, (_, gy) =>
                Array.from({ length: w }, (_, gx) => {
                  const [x, y, z] = toCell(axis, gx, gy, index);
                  const voxel = byCell.get(`${x},${y},${z}`);
                  const displayY = h - 1 - gy;
                  const px = PAD + gx * CELL;
                  const py = PAD + displayY * CELL;
                  if (!voxel) {
                    return <rect key={`${gx}-${gy}`} x={px} y={py} width={CELL} height={CELL} fill="none" stroke="var(--np-rule-soft)" strokeWidth={0.5} />;
                  }
                  const fill =
                    voxel.kind === 'mouth'
                      ? 'var(--np-signal)'
                      : voxel.kind === 'air'
                        ? 'url(#slice-air)'
                        : 'var(--np-paper-deep)';
                  const isConflict = conflicts.has(cellKey(voxel));
                  const isSel = selected != null && selected.x === voxel.x && selected.y === voxel.y && selected.z === voxel.z;
                  return (
                    <g key={`${gx}-${gy}`} onPointerEnter={() => setHover(voxel)} onClick={() => setPicked(voxel)} className="interactive">
                      <rect
                        x={px}
                        y={py}
                        width={CELL}
                        height={CELL}
                        fill={fill}
                        stroke={isConflict ? 'var(--np-alert)' : isSel ? 'var(--np-signal)' : 'var(--np-rule)'}
                        strokeWidth={isConflict || isSel ? 2 : 0.8}
                        strokeDasharray={voxel.kind === 'air' ? '3 3' : undefined}
                      />
                      <text x={px + 4} y={py + 11} className="np-slice-coord">
                        {x},{z}
                      </text>
                    </g>
                  );
                }),
              )}
            </svg>
          </div>
        </Panel>
      </div>

      <div className="c5">
        <Panel title={copy.axis}>
          <div className="np-voxel-controls">
            <Segmented
              label={copy.axis}
              options={[
                { value: 'x', label: 'X' },
                { value: 'y', label: 'Y' },
                { value: 'z', label: 'Z' },
              ]}
              value={axis}
              onChange={(value) => {
                const next = value as Axis;
                setAxis(next);
                setIndex(Math.min(index, dims(next).max));
              }}
            />
            <NumberStepper label={copy.index} value={index} min={0} max={max} onChange={setIndex} />
            <Toggle pressed={compare} onChange={setCompare}>
              {copy.compare}
            </Toggle>
          </div>
        </Panel>

        <Panel title={copy.selected}>
          {selected ? (
            <>
              <div className="np-readout-grid">
                <Readout label="cell" value={`${selected.x}, ${selected.y}, ${selected.z}`} />
                <Readout label={copy.kind} value={kindLabel} />
                <Readout label={copy.bay} value={selected.bay} />
                <Readout label={copy.claim} value={BAY_META[selected.bay].claim} />
                <Readout label={copy.section} value="0,0,0" />
                <Readout label={copy.conflict} value={conflicts.size} />
              </div>
              <button type="button" className="np-mini-btn np-mini-btn--wide" onClick={() => open('claimscope')}>
                {copy.explain}
              </button>
            </>
          ) : (
            <p className="np-empty">{copy.none}</p>
          )}
        </Panel>
      </div>

      <div className="c12">
        <WhyNote>{copy.note}</WhyNote>
      </div>
    </div>
  );
}
