import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { MODULES, MODULE_BY_ID, type ModuleId } from '../data/site';

const NODE_H = 34;
const STEP = 34;
const DIR_H = 24;
const TOP_PAD = 16;
const STAIR_DROP = 26;
const AXIS_GAP = 38;
const PAD_X = 14;
const N = MODULES.length;
const PLATE_MIN = 800;

const pad2 = (n: number) => String(n).padStart(2, '0');
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
const snap = (v: number) => Math.round(v * 10) / 10;

const ORDER = [...MODULES].sort((a, b) => a.rank - b.rank);
const LONGEST = ORDER.reduce((worst, m) => (m.id.length > worst.length ? m.id : worst), 'core');

const fontMetrics = (() => {
  const fallback = LONGEST.length * 0.57;
  if (typeof document === 'undefined') return fallback;
  const ctx = document.createElement('canvas').getContext('2d');
  if (!ctx) return fallback;
  ctx.font = '100px "SFMono-Regular", Menlo, Consolas, "Liberation Mono", monospace';
  const width = ctx.measureText(LONGEST).width;
  return (width - LONGEST.length * 3) / 100;
})();

const nameSize = (boxWidth: number) => clamp((boxWidth - 15) / fontMetrics, 9, 13);
const K = Object.fromEntries(ORDER.map((m, i) => [m.id, i])) as Record<ModuleId, number>;

type Edge = {
  key: string;
  from: ModuleId;
  to: ModuleId;
  fromK: number;
  toK: number;
  inIdx: number;
};

const EDGES: Edge[] = (() => {
  const list: Edge[] = [];
  for (const target of ORDER) {
    const sources = [...target.imports].sort((a, b) => K[a] - K[b]);
    sources.forEach((source, inIdx) => {
      list.push({
        key: `${source}->${target.id}`,
        from: source,
        to: target.id,
        fromK: K[source],
        toK: K[target.id],
        inIdx,
      });
    });
  }
  return list;
})();

const DEP_COUNT = Object.fromEntries(ORDER.map((m) => [m.id, m.imports.length])) as Record<ModuleId, number>;
const IMPORTED_BY = Object.fromEntries(ORDER.map((m) => [m.id, [] as ModuleId[]])) as Record<ModuleId, ModuleId[]>;
for (const edge of EDGES) IMPORTED_BY[edge.from].push(edge.to);
for (const id of Object.keys(IMPORTED_BY) as ModuleId[]) {
  IMPORTED_BY[id].sort((a, b) => K[a] - K[b]);
}

const COMPARABLE: [ModuleId, ModuleId][] = [];
for (const a of ORDER) for (const b of ORDER) if (a.rank < b.rank) COMPARABLE.push([a.id, b.id]);
const ABSENT = COMPARABLE.filter(([from, to]) => !MODULE_BY_ID[to].imports.includes(from));

const isLinked = (a: ModuleId, b: ModuleId) =>
  MODULE_BY_ID[a].imports.includes(b) || MODULE_BY_ID[b].imports.includes(a);

function entryOffset(index: number, count: number) {
  if (count <= 1) return 0;
  return (index - (count - 1) / 2) * Math.min(3.2, 22 / (count - 1));
}

type NodeGeo = {
  id: ModuleId;
  k: number;
  rank: number;
  cx: number;
  cy: number;
  w: number;
  h: number;
  nameFs: number;
  ownsFs: number;
};

type BandGeo = { rank: number; x0: number; x1: number; top: number; count: number };

type Geo = {
  mode: 'plate' | 'stack';
  w: number;
  h: number;
  nodeW: number;
  nodes: NodeGeo[];
  bands: BandGeo[];
  axis: number | null;
  unit: number;
  profile: string | null;
  stair: string;
  load: { id: ModuleId; x: number; h: number; n: number }[];
  ticks: { rank: number; x: number; count: number }[];
  railX: number;
  gutter: { rank: number; y: number; count: number }[];
  path: (edge: Edge) => string;
};

function plateLayout(w: number): Geo {
  const unit = (w - PAD_X * 2) / N;
  const nodeW = unit - 11;
  const nameFs = nameSize(nodeW);
  const ownsFs = clamp(nameFs * 0.6, 7, 8.2);
  const top = DIR_H + TOP_PAD + NODE_H / 2;
  const cyOf = (k: number) => top + (N - 1 - k) * STEP;

  const nodes: NodeGeo[] = ORDER.map((m, k) => ({
    id: m.id,
    k,
    rank: m.rank,
    cx: PAD_X + unit * (k + 0.5),
    cy: cyOf(k),
    w: nodeW,
    h: NODE_H,
    nameFs,
    ownsFs,
  }));
  const byId = new Map(nodes.map((n) => [n.id, n]));

  const bands: BandGeo[] = [];
  for (let rank = 0; rank < 10; rank += 1) {
    const members = ORDER.filter((m) => m.rank === rank);
    const first = K[members[0].id];
    const last = K[members[members.length - 1].id];
    bands.push({
      rank,
      x0: PAD_X + unit * first,
      x1: PAD_X + unit * (last + 1),
      top: 0,
      count: members.length,
    });
  }

  for (const band of bands) {
    const first = ORDER.find((m) => m.rank === band.rank)!;
    band.top = cyOf(K[first.id]) - STAIR_DROP;
  }

  const axis = cyOf(0) + NODE_H / 2 + AXIS_GAP;
  const load = nodes.map((n) => ({
    id: n.id,
    x: n.cx,
    h: DEP_COUNT[n.id] * 2.9,
    n: DEP_COUNT[n.id],
  }));

  const topPts: [number, number][] = [];
  for (let k = 0; k < N; k += 1) {
    const y = cyOf(k) - STAIR_DROP;
    topPts.push([PAD_X + unit * k, y], [PAD_X + unit * (k + 1), y]);
  }
  const toPath = (pts: [number, number][]) => pts.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${snap(x)} ${snap(y)}`).join(' ');
  const stair = toPath(topPts);
  let profile = `M ${snap(PAD_X)} ${snap(axis)}`;
  for (let k = 0; k < N; k += 1) {
    const h = DEP_COUNT[ORDER[k].id] * 2.9;
    profile += ` L ${snap(PAD_X + unit * k)} ${snap(axis - h)} L ${snap(PAD_X + unit * (k + 1))} ${snap(axis - h)}`;
  }
  profile += ` L ${snap(w - PAD_X)} ${snap(axis)} Z`;

  const path = (edge: Edge) => {
    const source = byId.get(edge.from)!;
    const target = byId.get(edge.to)!;
    const sx = source.cx + nodeW / 2;
    const trunk = sx + 3.5;
    const ty = target.cy + entryOffset(edge.inIdx, DEP_COUNT[edge.to]);
    const tx = target.cx - nodeW / 2 - 2.5;
    return `M ${snap(sx)} ${snap(source.cy)} H ${snap(trunk)} V ${snap(ty)} H ${snap(tx)}`;
  };

  return {
    mode: 'plate',
    w,
    h: axis + 18,
    nodeW,
    unit,
    profile,
    stair,
    nodes,
    bands,
    axis,
    load,
    ticks: bands.map((b) => ({ rank: b.rank, x: (b.x0 + b.x1) / 2, count: b.count })),
    railX: 0,
    gutter: [],
    path,
  };
}

function stackLayout(w: number): Geo {
  const gutterW = 34;
  const railW = clamp(w * 0.3, 74, 190);
  const rowLeft = PAD_X + gutterW;
  const rowRight = w - PAD_X - railW;
  const rowW = Math.max(120, rowRight - rowLeft);
  const rowH = 38;
  const gap = 6;
  const top = 30;
  const cyOf = (k: number) => top + k * (rowH + gap) + rowH / 2;
  const nameFs = nameSize(rowW);
  const ownsFs = clamp(nameFs * 0.62, 7.4, 8.2);

  const nodes: NodeGeo[] = ORDER.map((m, k) => ({
    id: m.id,
    k,
    rank: m.rank,
    cx: rowLeft + rowW / 2,
    cy: cyOf(k),
    w: rowW,
    h: rowH,
    nameFs,
    ownsFs,
  }));

  const gutter: Geo['gutter'] = [];
  for (let rank = 0; rank < 10; rank += 1) {
    const members = ORDER.filter((m) => m.rank === rank);
    const ys = members.map((m) => cyOf(K[m.id]));
    gutter.push({
      rank,
      y: ys.reduce((a, b) => a + b, 0) / ys.length,
      count: members.length,
    });
  }

  const path = (edge: Edge) => {
    const y0 = cyOf(edge.fromK);
    const y1 = cyOf(edge.toK);
    const span = Math.min(1, Math.abs(edge.toK - edge.fromK) / 8);
    const dx = railW * (0.44 + 0.46 * span);
    const dy = clamp(Math.abs(y1 - y0) * 0.42, 9, 30) * (y1 < y0 ? -1 : 1);
    return `M ${snap(rowRight)} ${snap(y0)} C ${snap(rowRight + dx)} ${snap(y0)} ${snap(rowRight)} ${snap(y1 - dy)} ${snap(rowRight)} ${snap(y1)}`;
  };

  return {
    mode: 'stack',
    w,
    h: cyOf(N - 1) + rowH / 2 + 16,
    nodeW: rowW,
    unit: 0,
    profile: null,
    stair: '',
    nodes,
    bands: [],
    axis: null,
    load: [],
    ticks: [],
    railX: rowRight,
    gutter,
    path,
  };
}

function EdgeLedger({ flow = false }: { flow?: boolean }) {
  return (
    <div className={flow ? 'lg-ledger lg-ledger-flow' : 'lg-ledger'}>
      <p className="lg-ledger-title">
        Edge ledger <span>03-modules.md §1</span>
      </p>
      <p className="lg-ledger-count">
        <b>{EDGES.length}</b> imports of <b>{COMPARABLE.length}</b> comparable pairs
      </p>
      <p className="lg-ledger-sub">absent — no import runs this way</p>
      <ul className="lg-absent">
        {ABSENT.map(([from, to]) => (
          <li key={`${from}-${to}`}>
            <i>×</i>
            <span>{from}</span>
            <em>→</em>
            <span>{to}</span>
          </li>
        ))}
      </ul>
      <p className="lg-ledger-note">0 cycles · edge table checked</p>
    </div>
  );
}

function Mark({ kind }: { kind: 'dep' | 'rev' | 'ghost' }) {
  const stroke = `var(--lg-mark)`;
  return (
    <svg width="20" height="8" viewBox="0 0 20 8" aria-hidden="true" focusable="false">
      <path d="M0 4 H13" stroke={stroke} strokeWidth="1.4" />
      {kind === 'ghost' ? (
        <path d="M13 4 H19" stroke={stroke} strokeWidth="1.4" />
      ) : (
        <path d="M13.4 1.2 L18.4 4 L13.4 6.8" fill="none" stroke={stroke} strokeWidth="1.4" />
      )}
    </svg>
  );
}

function Chip({ id, dir, onSelect }: { id: ModuleId; dir: 'dep' | 'rev'; onSelect: (id: ModuleId) => void }) {
  const m = MODULE_BY_ID[id];
  return (
    <button type="button" className="lg-chip" data-dir={dir} onClick={() => onSelect(id)}>
      <span className="lg-chip-rank">{pad2(m.rank)}</span>
      <span className="lg-chip-name">{m.id}</span>
    </button>
  );
}

function Inspector({ id, onSelect }: { id: ModuleId; onSelect: (id: ModuleId) => void }) {
  const m = MODULE_BY_ID[id];
  const imports = [...m.imports].sort((a, b) => K[a] - K[b]);
  const importers = IMPORTED_BY[id];
  return (
    <div className="lg-inspector">
      <div className="lg-insp-rank">
        <p className="lg-label">Rank</p>
        <p className="lg-rank-big">
          {pad2(m.rank)}
          <span> / 09</span>
        </p>
        <p className="lg-rank-fig">
          {imports.length} imports · {importers.length} importers
        </p>
      </div>
      <div className="lg-insp-main">
        <h3 className="lg-name">
          <span>kernel.</span>
          {m.id}
        </h3>
        <p className="lg-law">“{m.law}”</p>
        <p className="lg-owns">
          <span className="lg-label">Owns</span>
          {m.owns}
        </p>
        <p className="lg-doc">
          <span className="lg-label">Normative file</span>
          {m.doc}
        </p>
      </div>
      <div className="lg-insp-edges" data-dir="dep">
        <p className="lg-label">
          Imports <b>{imports.length}</b>
        </p>
        <div className="lg-chips">
          {imports.length === 0 ? (
            <p className="lg-none">nothing — the base</p>
          ) : (
            imports.map((x) => <Chip key={x} id={x} dir="dep" onSelect={onSelect} />)
          )}
        </div>
      </div>
      <div className="lg-insp-edges" data-dir="rev">
        <p className="lg-label">
          Imported by <b>{importers.length}</b>
        </p>
        <div className="lg-chips">
          {importers.length === 0 ? (
            <p className="lg-none">nothing — the root</p>
          ) : (
            importers.map((x) => <Chip key={x} id={x} dir="rev" onSelect={onSelect} />)
          )}
        </div>
      </div>
    </div>
  );
}

export function LatticeGraph({ focus }: { focus?: ModuleId | null }) {
  const [selected, setSelected] = useState<ModuleId>('structure');
  const [hovered, setHovered] = useState<ModuleId | null>(null);
  const [traceOnly, setTraceOnly] = useState(true);
  const [width, setWidth] = useState(0);
  const shellRef = useRef<HTMLDivElement>(null);
  const nodeRefs = useRef(new Map<ModuleId, HTMLButtonElement>());

  useEffect(() => {
    if (focus) setSelected(focus);
  }, [focus]);

  useLayoutEffect(() => {
    const el = shellRef.current;
    if (!el) return;
    const measure = () => {
      const raw = el.getBoundingClientRect().width - 2;
      setWidth(raw > 1 ? raw : 0);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const geo = useMemo(
    () => (width > 0 ? (width >= PLATE_MIN ? plateLayout(width) : stackLayout(width)) : null),
    [width],
  );

  const activeId = hovered ?? selected;
  const activeModule = MODULE_BY_ID[activeId];
  const activeDeps = DEP_COUNT[activeId];
  const activeRev = IMPORTED_BY[activeId].length;

  const select = (id: ModuleId) => {
    setSelected(id);
  };

  const onNodeKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>, id: ModuleId) => {
    const k = K[id];
    let next: number | null = null;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = Math.min(N - 1, k + 1);
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = Math.max(0, k - 1);
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = N - 1;
    if (next === null) return;
    event.preventDefault();
    const nextId = ORDER[next].id;
    setSelected(nextId);
    nodeRefs.current.get(nextId)?.focus();
  };

  const selectedModule = MODULE_BY_ID[selected];

  return (
    <div className="lg-shell" ref={shellRef}>
      <div className="lg-panel">
        <div className="lg-head">
          <p className="lg-meta">
            <span>
              <b>{N}</b> packages
            </span>
            <span className="lg-sep" />
            <span>
              <b>{EDGES.length}</b> edges
            </span>
            <span className="lg-sep" />
            <span className="lg-ok">0 cycles / edge table checked</span>
          </p>
          <div className="lg-seg" role="group" aria-label="Edge display">
            <button type="button" aria-pressed={traceOnly} data-active={traceOnly} onClick={() => setTraceOnly(true)}>
              trace
            </button>
            <button type="button" aria-pressed={!traceOnly} data-active={!traceOnly} onClick={() => setTraceOnly(false)}>
              all edges
            </button>
          </div>
        </div>

        <div
          className="lg-plate"
          data-mode={geo?.mode ?? 'plate'}
          data-edges={traceOnly ? 'trace' : 'all'}
          style={geo ? { height: geo.h } : { minHeight: 560 }}
          onMouseLeave={() => setHovered(null)}
        >
          {geo ? (
            <>
              <svg
                className="lg-canvas"
                width={geo.w}
                height={geo.h}
                viewBox={`0 0 ${geo.w} ${geo.h}`}
                aria-hidden="true"
                focusable="false"
              >
                <defs>
                  <marker id="lg-arrow-dep" viewBox="0 0 7 7" refX="6.4" refY="3.5" markerWidth="7" markerHeight="7" markerUnits="userSpaceOnUse" orient="auto">
                    <path d="M0.6 0.9 L6 3.5 L0.6 6.1" className="lg-arrow lg-arrow-dep" />
                  </marker>
                  <marker id="lg-arrow-rev" viewBox="0 0 7 7" refX="6.4" refY="3.5" markerWidth="7" markerHeight="7" markerUnits="userSpaceOnUse" orient="auto">
                    <path d="M0.6 0.9 L6 3.5 L0.6 6.1" className="lg-arrow lg-arrow-rev" />
                  </marker>
                </defs>

                {geo.mode === 'plate' ? (
                  <g className="lg-bays">
                    <rect
                      className="lg-column"
                      x={PAD_X + geo.unit * K[selected]}
                      y={geo.nodes[K[selected]].cy - STAIR_DROP}
                      width={geo.unit}
                      height={geo.axis! - (geo.nodes[K[selected]].cy - STAIR_DROP)}
                    />
                    {geo.bands.slice(1).map((band) => (
                      <g key={band.rank}>
                        <line
                          className="lg-bay-rule"
                          data-zone="sky"
                          x1={band.x0}
                          x2={band.x0}
                          y1={DIR_H}
                          y2={band.top}
                        />
                        <line
                          className="lg-bay-rule"
                          data-zone="cut"
                          x1={band.x0}
                          x2={band.x0}
                          y1={band.top}
                          y2={geo.axis ?? 0}
                        />
                      </g>
                    ))}
                    <line
                      className="lg-bay-rule"
                      data-zone="cut"
                      x1={PAD_X}
                      x2={PAD_X}
                      y1={geo.bands[0].top}
                      y2={geo.axis ?? 0}
                    />
                    <line
                      className="lg-bay-rule"
                      data-zone="cut"
                      x1={geo.w - PAD_X}
                      x2={geo.w - PAD_X}
                      y1={geo.bands[geo.bands.length - 1].top}
                      y2={geo.axis ?? 0}
                    />
                    <path className="lg-stair-line" d={geo.stair} />
                    <line className="lg-frame" x1={PAD_X} x2={geo.w - PAD_X} y1={DIR_H} y2={DIR_H} />
                    <path
                      className="lg-frame-arrow"
                      d={`M ${geo.w - PAD_X - 9} ${DIR_H - 3.5} L ${geo.w - PAD_X} ${DIR_H} L ${geo.w - PAD_X - 9} ${DIR_H + 3.5} Z`}
                    />
                  </g>
                ) : null}

                <g className="lg-wires">
                  {EDGES.map((edge) => {
                    const touches = edge.from === activeId || edge.to === activeId;
                    const active = touches;
                    const dim = traceOnly && !touches;
                    const dir = active ? (edge.to === activeId ? 'dep' : 'rev') : null;
                    return (
                      <path
                        key={active ? `${edge.key}|${activeId}` : edge.key}
                        className="lg-wire"
                        pathLength={100}
                        d={geo.path(edge)}
                        data-dim={dim}
                        data-active={active}
                        data-dir={dir ?? undefined}
                        markerEnd={
                          active
                            ? dir === 'dep'
                              ? 'url(#lg-arrow-dep)'
                              : 'url(#lg-arrow-rev)'
                            : undefined
                        }
                        style={active ? ({ '--lg-delay': `${Math.min(6, Math.abs(edge.toK - edge.fromK)) * 14}ms` } as CSSProperties) : undefined}
                      />
                    );
                  })}
                </g>

                {geo.mode === 'plate' && geo.axis !== null ? (
                  <g className="lg-ledge">
                    <text className="lg-axis-caption" x={PAD_X} y={geo.axis! - 26}>
                      imports / pkg
                    </text>
                    <line className="lg-axis" x1={PAD_X} x2={geo.w - PAD_X} y1={geo.axis} y2={geo.axis} />
                    {geo.bands.map((band) => (
                      <line
                        key={`t${band.rank}`}
                        className="lg-axis-tick"
                        x1={band.x0}
                        x2={band.x0}
                        y1={geo.axis! - 3}
                        y2={geo.axis! + 3}
                      />
                    ))}
                    {geo.profile ? <path className="lg-profile" d={geo.profile} /> : null}
                    {geo.load.map((bar) => (
                      <line
                        key={`b${bar.id}`}
                        className="lg-load"
                        data-on={bar.id === selected}
                        x1={bar.x - geo.unit / 2}
                        x2={bar.x + geo.unit / 2}
                        y1={geo.axis! - bar.h}
                        y2={geo.axis! - bar.h}
                      />
                    ))}
                    {geo.load.map((bar) => (
                      <text
                        key={`n${bar.id}`}
                        className="lg-count"
                        data-on={bar.id === selected}
                        x={bar.x}
                        y={geo.axis! - bar.h - 5}
                      >
                        {bar.n}
                      </text>
                    ))}
                    {geo.ticks.map((tick) => (
                      <text
                        key={`r${tick.rank}`}
                        className="lg-rank-label"
                        data-on={MODULE_BY_ID[selected].rank === tick.rank}
                        x={tick.x}
                        y={geo.axis! + 13}
                      >
                        {pad2(tick.rank)}
                        {tick.count > 1 ? <tspan className="lg-rank-count"> ×{tick.count}</tspan> : null}
                      </text>
                    ))}
                  </g>
                ) : null}
              </svg>

              {geo.mode === 'plate' ? (
                <div className="lg-dir" aria-hidden="true">
                  <span>
                    <b>imports only</b>
                  </span>
                  <span>rank 00 → 09</span>
                </div>
              ) : (
                <div className="lg-dir lg-dir-stack" aria-hidden="true">
                  <span>imports ↑</span>
                </div>
              )}

              {geo.mode === 'stack'
                ? geo.gutter.map((g) => (
                    <span
                      key={g.rank}
                      className="lg-gutter"
                      data-on={MODULE_BY_ID[selected].rank === g.rank}
                      style={{ top: g.y - 7 }}
                    >
                      {pad2(g.rank)}
                    </span>
                  ))
                : null}

              {geo.nodes.map((node) => {
                const module = MODULE_BY_ID[node.id];
                const owns = module.owns.split(' · ');
                const extra = owns.length - 1;
                const active = node.id === selected;
                const hover = node.id === hovered && !active;
                const dim = traceOnly && node.id !== activeId && !isLinked(node.id, activeId);
                const style: CSSProperties = {
                  left: node.cx - node.w / 2,
                  top: node.cy - node.h / 2,
                  width: node.w,
                  height: node.h,
                };
                return (
                  <button
                    key={node.id}
                    type="button"
                    ref={(el) => {
                      if (el) nodeRefs.current.set(node.id, el);
                      else nodeRefs.current.delete(node.id);
                    }}
                    className="lg-node"
                    data-active={active}
                    data-hover={hover}
                    data-dim={dim}
                    style={style}
                    onMouseEnter={() => setHovered(node.id)}
                    onFocus={() => setHovered(node.id)}
                    onBlur={() => setHovered(null)}
                    onClick={() => select(node.id)}
                    onKeyDown={(event) => onNodeKeyDown(event, node.id)}
                    aria-pressed={active}
                    aria-label={`${node.id}, rank ${node.rank}, ${DEP_COUNT[node.id]} imports, ${IMPORTED_BY[node.id].length} importers`}
                  >
                    <span className="lg-node-name" style={{ fontSize: node.nameFs }}>
                      {node.id}
                    </span>
                    <span className="lg-node-owns" style={{ fontSize: node.ownsFs }}>
                      {owns[0]}
                      {extra > 0 ? ` +${extra}` : ''}
                    </span>
                  </button>
                );
              })}

              {geo.mode === 'plate' ? <EdgeLedger /> : null}
            </>
          ) : null}
        </div>

        <div className="lg-foot">
          <p>
            Fig. 01 — the edge table, drawn
            <span className="lg-foot-sep">/</span>
            <span className="lg-trace-read">
              trace <b>{activeModule.id}</b> <span className="lg-foot-dim">{activeDeps} imports · {activeRev} importers</span>
            </span>
          </p>
          <ul className="lg-legend">
            <li data-k="dep">
              <Mark kind="dep" />
              <span>imports</span>
            </li>
            <li data-k="rev">
              <Mark kind="rev" />
              <span>imported by</span>
            </li>
            <li data-k="ghost">
              <Mark kind="ghost" />
              <span>other edge</span>
            </li>
          </ul>
        </div>

        {geo?.mode === 'stack' ? <EdgeLedger flow /> : null}

        <Inspector id={selected} onSelect={select} />
      </div>

      <p className="lg-sr" aria-live="polite">
        {selectedModule.id}, rank {selectedModule.rank}, {DEP_COUNT[selected]} imports,{' '}
        {IMPORTED_BY[selected].length} importers
      </p>
    </div>
  );
}
