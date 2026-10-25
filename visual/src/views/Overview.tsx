import { useEffect, useState } from 'react';
import {
  BAND_CENTRES,
  FLOW,
  LAYERS,
  LAYER_EDGES,
  OVERVIEW_READOUTS,
  OVERVIEW_SCENARIOS,
  OVERVIEW_TRACE,
} from '../data/overview';
import type { Layer, OverviewScenarioId } from '../data/overview';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { DiagramSvg, truncate } from '../components/Diagram';
import { StatusChip } from '../components/StatusChip';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

const BAND_X = 12;
const BAND_W = 776;
const BAND_H = 64;
const BAND_GAP = 24;

function LayerBand({
  layer,
  index,
  selected,
  onSelect,
}: {
  layer: Layer;
  index: number;
  selected: boolean;
  onSelect: () => void;
}) {
  const t = useT();
  const c = useLang().c;
  const y = 16 + index * (BAND_H + BAND_GAP);
  return (
    <g
      className="interactive"
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      aria-label={fmt(t.overview.layerAria, {
        no: layer.no,
        name: c(layer.name),
        question: c(layer.question),
      })}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onSelect();
        }
      }}
    >
      <rect
        className="node"
        data-state={selected ? 'selected' : 'default'}
        x={BAND_X}
        y={y}
        width={BAND_W}
        height={BAND_H}
        vectorEffect="non-scaling-stroke"
        fill={selected ? 'var(--np-tint-signal)' : 'var(--np-paper)'}
      />
      {selected ? (
        <g stroke="var(--np-signal-ink)" strokeWidth={1} aria-hidden="true">
          <line x1={BAND_X + 3} y1={y + 3} x2={BAND_X + 9} y2={y + 3} />
          <line x1={BAND_X + 3} y1={y + 3} x2={BAND_X + 3} y2={y + 9} />
          <line x1={BAND_X + BAND_W - 9} y1={y + 3} x2={BAND_X + BAND_W - 3} y2={y + 3} />
          <line x1={BAND_X + BAND_W - 3} y1={y + 3} x2={BAND_X + BAND_W - 3} y2={y + 9} />
          <line x1={BAND_X + 3} y1={y + BAND_H - 3} x2={BAND_X + 9} y2={y + BAND_H - 3} />
          <line x1={BAND_X + 3} y1={y + BAND_H - 9} x2={BAND_X + 3} y2={y + BAND_H - 3} />
          <line x1={BAND_X + BAND_W - 9} y1={y + BAND_H - 3} x2={BAND_X + BAND_W - 3} y2={y + BAND_H - 3} />
          <line x1={BAND_X + BAND_W - 3} y1={y + BAND_H - 9} x2={BAND_X + BAND_W - 3} y2={y + BAND_H - 3} />
        </g>
      ) : null}
      <text className="t-mono t-muted" x={BAND_X + 12} y={y + 20} fontSize={10}>
        {layer.no}
      </text>
      <text className="t-title" x={BAND_X + 40} y={y + 22} fontSize={15}>
        {c(layer.name)}
      </text>
      <text className="t-body t-muted" x={BAND_X + 40} y={y + 40} fontSize={11}>
        {c(layer.question)}
      </text>
      <text className="t-mono t-muted" x={BAND_X + 40} y={y + 55} fontSize={9}>
        {truncate(layer.concepts.map((c) => c.name).join(' · '), BAND_W - 180, 5)}
      </text>
      <text className="t-mono t-muted" x={BAND_X + BAND_W - 12} y={y + 20} fontSize={9} textAnchor="end">
        {layer.concepts.length} {t.overview.layerObjects}
      </text>
      <text className="t-mono t-muted" x={BAND_X + BAND_W - 12} y={y + 40} fontSize={9} textAnchor="end">
        {layer.layerTag.toUpperCase()}
      </text>
    </g>
  );
}

export function OverviewView() {
  const [selectedId, setSelectedId] = useState('document');
  const { scenario, setScenario, step, setStep } = useScenarioState<OverviewScenarioId>({
    page: 'overview',
    scenarios: OVERVIEW_SCENARIOS,
    defaultScenario: 'trace',
    stepCount: OVERVIEW_TRACE.trace.length,
  });
  const trace = OVERVIEW_TRACE[scenario];
  const current = trace[Math.min(step, trace.length - 1)];
  const playback = usePlayback(trace.length, { step, onStep: setStep });
  const layerIndex = Math.max(0, LAYERS.findIndex((item) => item.id === current.layerId));
  const tokenY = BAND_CENTRES[layerIndex] ?? BAND_CENTRES[0];
  const layer = LAYERS.find((l) => l.id === selectedId) ?? LAYERS[0];
  const t = useT();
  const c = useLang().c;

  useEffect(() => {
    setSelectedId(current.layerId);
  }, [current]);

  const selectLayer = (id: string, index: number) => {
    setSelectedId(id);
    if (index < trace.length) setStep(index);
  };

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.overview.why}</WhyNote>
      </div>

      <div className="c8">
        <Panel
          title={t.overview.layerStack}
          count={t.overview.layerStackCount}
          actions={<span className="micro">{t.overview.layerOrder}</span>}
        >
          <DiagramSvg
            viewBox="0 0 800 372"
            minWidth={620}
            interactive
            label={t.overview.diagramLabel}
            title={t.overview.diagramTitle}
            desc={t.overview.diagramDesc}
          >
            {LAYERS.map((item, index) => (
              <LayerBand
                key={item.id}
                layer={item}
                index={index}
                selected={item.id === selectedId}
                onSelect={() => selectLayer(item.id, index)}
              />
            ))}
            {LAYERS.slice(0, -1).map((item, index) => {
              const y1 = 16 + index * (BAND_H + BAND_GAP) + BAND_H;
              const y2 = y1 + BAND_GAP;
              const flow = FLOW[index];
              return (
                <g key={`edge-${item.id}`} aria-hidden="true">
                  <line className="wire" x1={400.5} y1={y1} x2={400.5} y2={y2 - 2} vectorEffect="non-scaling-stroke" />
                  <polygon className="arrow" points={`400.5 ${y2} 396.5 ${y2 - 7} 404.5 ${y2 - 7}`} />
                  <text className="t-mono t-muted" x={412} y={y1 + 15} fontSize={9}>
                    {flow.label}
                  </text>
                </g>
              );
            })}
            <g className="np-token" style={{ transform: `translate(400.5px, ${tokenY}px)` }} aria-hidden="true">
              <rect x={-5} y={-5} width={10} height={10} fill="var(--np-signal)" stroke="var(--np-signal-ink)" strokeWidth={2} />
              <title>{c(current.object)}</title>
            </g>
          </DiagramSvg>
          <div className="np-demo">
            <ScenarioPicker
              scenarios={OVERVIEW_SCENARIOS}
              value={scenario}
              onChange={(id) => setScenario(id, 0)}
            />
            <PlaybackBar
              step={step}
              count={trace.length}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={setStep}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              caption={
                <>
                  <span className="mono">{c(current.object)}</span>
                  <span className="np-playback-sep"> · </span>
                  {c(current.carries)}
                  <span className="mono muted"> · {current.code}</span>
                </>
              }
            />
          </div>
        </Panel>
      </div>

      <div className="c4">
        <div className="stack">
          <Panel title={t.demo.currentStop} count={fmt(t.demo.stopOf, { n: layerIndex + 1, total: trace.length })}>
            <div className="np-demo-readout" aria-live="polite">
              <div className="micro">{t.demo.object}</div>
              <div className="np-demo-object mono">{c(current.object)}</div>
              <div className="micro" style={{ marginTop: 6 }}>
                {t.demo.carries}
              </div>
              <p className="small">{c(current.carries)}</p>
              <div className="row-between">
                <span className="micro">{t.demo.code}</span>
                <span className="mono small">{current.code}</span>
              </div>
            </div>
          </Panel>
          <Panel title={t.overview.readouts} count={t.overview.readoutsCount}>
            {OVERVIEW_READOUTS.filter((r) => r.primary).map((r) => (
              <Readout
                key={r.label}
                label={c(r.label)}
                value={r.value}
                unit={r.unit ? c(r.unit) : undefined}
                delta={r.delta ? c(r.delta) : undefined}
                state={r.state}
                primary
              />
            ))}
            <div className="readout-grid">
              {OVERVIEW_READOUTS.filter((r) => !r.primary).map((r) => (
                <Readout
                  key={r.label}
                  label={c(r.label)}
                  value={r.value}
                  unit={r.unit ? c(r.unit) : undefined}
                  delta={r.delta ? c(r.delta) : undefined}
                  state={r.state}
                />
              ))}
            </div>
          </Panel>

          <Panel title={fmt(t.overview.selected, { name: c(layer.name) })} count={layer.layerTag}>
            <div>
              <div className="micro">{t.overview.owns}</div>
              <p className="small" style={{ marginTop: 2 }}>
                {c(layer.owns)}
              </p>
            </div>
            <dl className="kv">
              <dt>{t.overview.question}</dt>
              <dd style={{ fontFamily: 'var(--np-font-body)' }}>{c(layer.question)}</dd>
              <dt>{t.overview.layerTag}</dt>
              <dd>{layer.layerTag}</dd>
              <dt>{t.overview.objects}</dt>
              <dd>{layer.concepts.length}</dd>
            </dl>
            <div className="note">{t.overview.layerNote}</div>
          </Panel>
        </div>
      </div>

      <div className="c12">
        <Panel
          title={fmt(t.overview.conceptsOwned, { name: c(layer.name) })}
          count={fmt(t.overview.conceptsCount, { n: layer.concepts.length })}
          actions={<span className="micro">{t.overview.conceptsAction}</span>}
        >
          <div className="concept-grid">
            {layer.concepts.map((concept) => (
              <div className="concept-card" key={concept.id}>
                <div className="concept-kicker">
                  <span className="concept-name">{concept.name}</span>
                  <span className="concept-kind">{concept.kind}</span>
                </div>
                <p className="concept-line">{c(concept.oneLine)}</p>
              </div>
            ))}
          </div>
        </Panel>
      </div>

      <div className="c12">
        <Panel title={t.overview.handoffs} count={fmt(t.overview.handoffsCount, { n: LAYER_EDGES.length })}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th style={{ width: 140 }}>{t.overview.thHandoff}</th>
                  <th style={{ width: 140 }}>{t.overview.thOperation}</th>
                  <th>{t.overview.thCrosses}</th>
                </tr>
              </thead>
              <tbody>
                {FLOW.map((flow) => (
                  <tr key={flow.label}>
                    <td className="mono-cell">
                      {c(flow.from)} → {c(flow.to)}
                    </td>
                    <td>
                      <StatusChip kind="info">{flow.label}</StatusChip>
                    </td>
                    <td className="wrap-cell">{c(flow.detail)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      </div>
    </div>
  );
}
