import { useEffect, useMemo, useState } from 'react';
import { MODULES, MODULE_BY_ID, type ModuleId } from '../data/site';

const W = 1000;
const H = 520;

type Edge = { from: ModuleId; to: ModuleId; key: string };

function point(module: (typeof MODULES)[number]) {
  return { x: (module.x / 100) * W, y: (module.y / 100) * H };
}

function edgePath(from: (typeof MODULES)[number], to: (typeof MODULES)[number]) {
  const a = point(from);
  const b = point(to);
  const midX = (a.x + b.x) / 2;
  return `M ${a.x} ${a.y} C ${midX} ${a.y}, ${midX} ${b.y}, ${b.x} ${b.y}`;
}

export function LatticeGraph({ focus }: { focus?: ModuleId | null }) {
  const [selected, setSelected] = useState<ModuleId>('structure');
  const [trace, setTrace] = useState(true);

  useEffect(() => {
    if (focus) setSelected(focus);
  }, [focus]);

  const edges = useMemo<Edge[]>(() => {
    const list: Edge[] = [];
    for (const module of MODULES) {
      for (const imported of module.imports) {
        list.push({ from: imported, to: module.id, key: `${imported}->${module.id}` });
      }
    }
    return list;
  }, []);

  const selectedModule = MODULE_BY_ID[selected];
  const neighborIds = useMemo(() => {
    const ids = new Set<ModuleId>([selected]);
    for (const edge of edges) {
      if (edge.from === selected) ids.add(edge.to);
      if (edge.to === selected) ids.add(edge.from);
    }
    return ids;
  }, [edges, selected]);

  return (
    <div className="lattice-layout">
      <div className="lattice-stage">
        <div className="lattice-toolbar">
          <span className="mono">11 packages · 0 cycles · rank order</span>
          <div className="segmented" role="group" aria-label="Graph trace mode">
            <button type="button" data-active={trace} onClick={() => setTrace(true)}>trace</button>
            <button type="button" data-active={!trace} onClick={() => setTrace(false)}>all edges</button>
          </div>
        </div>
        <div className="lattice-canvas">
          <svg className="lattice-edges" viewBox={`0 0 ${W} ${H}`} aria-hidden="true">
            {edges.map((edge) => {
              const from = MODULE_BY_ID[edge.from];
              const to = MODULE_BY_ID[edge.to];
              const active = edge.from === selected || edge.to === selected;
              const dim = trace && !active;
              return (
                <path
                  key={edge.key}
                  d={edgePath(from, to)}
                  data-active={active}
                  data-dim={dim}
                  opacity={active ? 0.9 : 0.42}
                />
              );
            })}
          </svg>
          {MODULES.map((module) => {
            const active = module.id === selected;
            const dim = trace && !neighborIds.has(module.id);
            return (
              <button
                key={module.id}
                type="button"
                className="graph-node"
                data-active={active}
                data-dim={dim}
                style={{ left: `${module.x}%`, top: `${module.y}%`, opacity: dim ? 0.38 : 1 }}
                onClick={() => setSelected(module.id)}
                aria-label={`Select ${module.id}`}
              >
                <span className="rank">RANK {module.rank}</span>
                <span className="pkg">{module.id}</span>
                <span className="role">{module.owns.split(' · ')[0]}</span>
              </button>
            );
          })}
          <div className="lattice-axis" />
          {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((rank) => (
            <span key={rank} className="axis-label" style={{ left: `${5.5 + rank * 10.2}%`, bottom: '1.2%' }}>
              {String(rank).padStart(2, '0')}
            </span>
          ))}
        </div>
        <div className="figure-label">
          <span>Fig. 01 — the edge table, drawn</span>
          <span>Click a package to trace its neighbours</span>
        </div>
      </div>

      <aside className="lattice-side">
        <div className="selected-rank">Rank {selectedModule.rank} / 09</div>
        <h4>kernel.{selectedModule.id}</h4>
        <div className="law">“{selectedModule.law}”</div>
        <div className="meta-row">
          <div className="l">Owns</div>
          <div className="v">{selectedModule.owns}</div>
        </div>
        <div className="meta-row">
          <div className="l">May import</div>
          <div className="v">
            {selectedModule.imports.length === 0 ? 'nothing — the base' : selectedModule.imports.join(' · ')}
          </div>
        </div>
        <div className="meta-row">
          <div className="l">Why this rank</div>
          <div className="v">
            {selectedModule.rank === 0
              ? 'Only values and identity; no world, no geometry, no report.'
              : selectedModule.rank === 9
                ? 'The composition root: the only package that sees the whole pipeline.'
                : `Depends only on lower-ranked packages; a new edge is a design change.`}
          </div>
        </div>
        <div className="doc-path">
          <strong>Normative file</strong>
          {selectedModule.doc}
        </div>
      </aside>
    </div>
  );
}
