import { useState } from 'react';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { DiagramSvg } from '../components/Diagram';
import { StatusChip } from '../components/StatusChip';
import { Legend } from '../components/Legend';
import { Toggle } from '../components/Controls';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  ANATOMY_GIANT_STEPS,
  ANATOMY_LEGAL_STEPS,
  ANATOMY_SCENARIOS,
  CELL_LEDGER,
  FURNACE,
  FURNACE_BAYS,
  GIANT_CHECK_REASONS,
  PRESENCE_RULE,
  REJECTED_BAY,
  type AnatomyRegion,
  type AnatomyScenarioId,
  type AnatomyStep,
  type BayAnatomy,
} from '../data/anatomy';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

const GRID_W = 7;
const GRID_H = 6;
const CELL = 22;
const PANEL_W = 236;
const PANEL_H = 236;
const PANEL_X = [24, 312, 600];
const PANEL_Y = 74;

const cellXY = (panelIndex: number, x: number, y: number) => ({
  x: PANEL_X[panelIndex] + 24 + x * CELL,
  y: PANEL_Y + 64 + (GRID_H - 1 - y) * CELL,
});

function GridGhost({ panel }: { panel: number }) {
  const { x: gx, y: gy } = cellXY(panel, 0, 5);
  return (
    <g aria-hidden="true">
      <rect x={gx} y={gy} width={GRID_W * CELL} height={GRID_H * CELL} fill="none" stroke="var(--np-rule)" strokeWidth={1} />
      {Array.from({ length: GRID_W - 1 }, (_, i) => (
        <line
          key={`v${i}`}
          x1={gx + (i + 1) * CELL}
          y1={gy}
          x2={gx + (i + 1) * CELL}
          y2={gy + GRID_H * CELL}
          className="tick"
        />
      ))}
      {Array.from({ length: GRID_H - 1 }, (_, i) => (
        <line
          key={`h${i}`}
          x1={gx}
          y1={gy + (i + 1) * CELL}
          x2={gx + GRID_W * CELL}
          y2={gy + (i + 1) * CELL}
          className="tick"
        />
      ))}
    </g>
  );
}

const isWallCell = (x: number, y: number) => x === 0 || x === 6 || y === 0 || y === 5;
const isInteriorCell = (x: number, y: number) => x >= 1 && x <= 5 && y >= 1 && y <= 4;
const MOUTH_CELLS = [
  { x: 3, y: 1 },
  { x: 3, y: 2 },
] as const;
const isMouthCell = (x: number, y: number) => MOUTH_CELLS.some((cell) => cell.x === x && cell.y === y);

function BayPanel({
  index,
  bay,
  selected,
  giant,
  focus,
  region,
  split,
  onSelect,
}: {
  index: number;
  bay: BayAnatomy;
  selected: boolean;
  giant: boolean;
  focus: boolean;
  region: AnatomyRegion;
  split: boolean;
  onSelect: () => void;
}) {
  const t = useT();
  const c = useLang().c;
  const px = PANEL_X[index];
  const py = PANEL_Y;
  const cells = [];
  for (let y = 0; y < GRID_H; y++) {
    for (let x = 0; x < GRID_W; x++) {
      const p = cellXY(index, x, y);
      let fill = 'none';
      let stroke = 'none';
      if (bay.id === 'wall' && isWallCell(x, y)) {
        fill = 'var(--np-tint-neutral)';
        stroke = 'var(--np-ink)';
      }
      if (bay.id === 'interior' && isInteriorCell(x, y)) {
        fill = 'var(--np-tint-caution)';
        stroke = 'var(--np-caution-ink)';
      }
      if (bay.id === 'mouth' && isMouthCell(x, y)) {
        fill = 'var(--np-signal)';
        stroke = 'var(--np-signal-ink)';
      }
      cells.push(
        <rect
          key={`${bay.id}-${x}-${y}`}
          x={p.x}
          y={p.y}
          width={CELL}
          height={CELL}
          fill={fill}
          stroke={stroke}
          strokeWidth={stroke === 'none' ? 0 : 1}
        />,
      );
    }
  }
  const inScope = focus && (region === 'all' || region === 'split' || region === bay.id);
  const gridTop = cellXY(index, 0, 5);
  const ownRegion = region === 'all' || region === 'split' ? (bay.id as AnatomyRegion) : region;
  const regional =
    ownRegion === 'mouth'
      ? { x: cellXY(index, 3, 2).x, y: cellXY(index, 3, 2).y, w: CELL, h: CELL * 2 }
      : ownRegion === 'interior'
        ? { x: cellXY(index, 1, 4).x, y: cellXY(index, 1, 4).y, w: CELL * 5, h: CELL * 4 }
        : { x: gridTop.x, y: gridTop.y, w: GRID_W * CELL, h: GRID_H * CELL };

  return (
    <g
      className="interactive"
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      aria-label={fmt(t.anatomy.bayAria, {
        name: bay.name,
        cells: bay.cells,
        coverage: bay.coverage,
        scope: bay.scope,
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
        x={px}
        y={py}
        width={PANEL_W}
        height={PANEL_H}
        fill={selected ? 'var(--np-tint-signal)' : 'var(--np-paper)'}
        stroke={selected ? 'var(--np-signal-ink)' : giant ? 'var(--np-alert)' : 'var(--np-rule)'}
        strokeWidth={selected ? 2 : 1}
        strokeDasharray={giant && !selected ? '6 3' : undefined}
      />
      <text className="t-mono" x={px + 10} y={py + 18} fontSize={10}>
        {String(index + 1).padStart(2, '0')}
      </text>
      <text className="t-title" x={px + 30} y={py + 18} fontSize={13}>
        {bay.name}
      </text>
      <text className="t-mono" x={px + PANEL_W - 10} y={py + 18} fontSize={11} textAnchor="end">
        {bay.cells} {t.anatomy.cellsUnit}
      </text>
      <GridGhost panel={index} />
      {cells}
      {inScope ? (
        <g className={`np-focus-cells${split ? ' np-focus-cells--split' : ''}`} aria-hidden="true">
          <rect
            x={regional.x - 2}
            y={regional.y - 2}
            width={regional.w + 4}
            height={regional.h + 4}
            fill="none"
            stroke="var(--np-signal-ink)"
            strokeWidth={2}
            strokeDasharray={split ? '3 3' : undefined}
            vectorEffect="non-scaling-stroke"
          />
          <text className="t-mono" x={regional.x + regional.w - 4} y={regional.y + 11} fontSize={9} textAnchor="end">
            {split ? `#${index + 1}` : bay.coverage}
          </text>
        </g>
      ) : null}
      <text className="t-mono t-muted" x={px + 10} y={py + PANEL_H - 30} fontSize={9}>
        {bay.coverage} · {bay.scope.toUpperCase()} · {bay.mode}
      </text>
      <text className="t-mono t-muted" x={px + 10} y={py + PANEL_H - 14} fontSize={9}>
        {bay.stage}
      </text>
      {bay.id === 'mouth' ? (
        <g>
          <rect
            x={cellXY(index, 3, 2).x - 2}
            y={cellXY(index, 3, 2).y - 2}
            width={CELL + 4}
            height={CELL * 2 + 4}
            fill="none"
            stroke="var(--np-signal-ink)"
            strokeWidth={2}
          />
          <text className="t-mono" x={px + 30} y={py + 34} fontSize={9}>
            {t.anatomy.mouthCells}
          </text>
        </g>
      ) : null}
      {selected ? (
        <g stroke="var(--np-signal-ink)" strokeWidth={1} aria-hidden="true">
          <line x1={px + 3} y1={py + 3} x2={px + 9} y2={py + 3} />
          <line x1={px + 3} y1={py + 3} x2={px + 3} y2={py + 9} />
          <line x1={px + PANEL_W - 9} y1={py + 3} x2={px + PANEL_W - 3} y2={py + 3} />
          <line x1={px + PANEL_W - 3} y1={py + 3} x2={px + PANEL_W - 3} y2={py + 9} />
          <line x1={px + 3} y1={py + PANEL_H - 3} x2={px + 9} y2={py + PANEL_H - 3} />
          <line x1={px + 3} y1={py + PANEL_H - 9} x2={px + 3} y2={py + PANEL_H - 3} />
          <line x1={px + PANEL_W - 9} y1={py + PANEL_H - 3} x2={px + PANEL_W - 3} y2={py + PANEL_H - 3} />
          <line x1={px + PANEL_W - 3} y1={py + PANEL_H - 9} x2={px + PANEL_W - 3} y2={py + PANEL_H - 3} />
        </g>
      ) : null}
    </g>
  );
}

export function AnatomyView() {
  const [selected, setSelected] = useState('wall');
  const { scenario, setScenario, step, setStep } = useScenarioState<AnatomyScenarioId>({
    page: 'anatomy',
    scenarios: ANATOMY_SCENARIOS,
    defaultScenario: 'giant-check',
    stepCount: ANATOMY_GIANT_STEPS.length,
  });
  const steps = scenario === 'giant-check' ? ANATOMY_GIANT_STEPS : ANATOMY_LEGAL_STEPS;
  const activeStep = Math.min(step, steps.length - 1);
  const current: AnatomyStep = steps[activeStep];
  const giant = scenario === 'giant-check' && current.id !== 'split';
  const split = scenario === 'giant-check' && current.id === 'split';
  const playback = usePlayback(steps.length, { step: activeStep, onStep: setStep });
  const bay = FURNACE_BAYS.find((b) => b.id === selected) ?? FURNACE_BAYS[0];
  const t = useT();
  const c = useLang().c;

  const selectBay = (id: string) => {
    setSelected(id);
    if (scenario === 'legal-split') {
      const index = ANATOMY_LEGAL_STEPS.findIndex((item) => item.id === id);
      if (index >= 0) setStep(index);
    }
  };

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.anatomy.why}</WhyNote>
      </div>

      <div className="c8">
        <Panel
          title={t.anatomy.study}
          count={fmt(t.anatomy.studyCount, { bounds: FURNACE.boundsLabel })}
          actions={<span className="micro">{t.anatomy.studyAction}</span>}
        >
          <DiagramSvg
            viewBox="0 0 860 330"
            minWidth={700}
            interactive
            label={t.anatomy.diagramLabel}
            title={t.anatomy.diagramTitle}
            desc={t.anatomy.diagramDesc}
          >
            <text className="t-label t-muted" x={24} y={24}>
              {t.anatomy.sectionLabel}
            </text>
            <text className="t-mono t-muted" x={24} y={42} fontSize={9}>
              {t.anatomy.patternKey}
            </text>
            {FURNACE_BAYS.map((b, index) => (
              <BayPanel
                key={b.id}
                index={index}
                bay={b}
                selected={b.id === selected}
                giant={giant}
                focus={current.panels.includes(b.id)}
                region={current.region}
                split={split}
                onSelect={() => selectBay(b.id)}
              />
            ))}
            {giant ? (
              <g aria-hidden="true">
                <rect x={12} y={62} width={836} height={252} fill="none" stroke="var(--np-alert)" strokeWidth={2.5} strokeDasharray="10 4" />
                <rect x={600} y={28} width={236} height={18} fill="var(--np-alert)" />
                <text x={608} y={41} fill="var(--np-paper)" fontSize={10} fontFamily="var(--np-font-body)" fontWeight={600}>
                  {t.anatomy.impossible}
                </text>
                {[0, 1, 2].map((i) => (
                  <rect key={`conflict-${i}`} x={PANEL_X[i] - 6} y={PANEL_Y - 6} width={12} height={12} fill="var(--np-alert)" />
                ))}
              </g>
            ) : null}
            <text className="t-mono t-muted" x={24} y={324} fontSize={9}>
              {t.anatomy.arithmetic}
            </text>
          </DiagramSvg>
          <Legend
            title={t.anatomy.legendTitle}
            items={[
              { label: t.anatomy.legendWall, kind: 'rect', color: 'var(--np-tint-neutral)', count: '194' },
              { label: t.anatomy.legendMouth, kind: 'rect', color: 'var(--np-signal)', count: '2' },
              { label: t.anatomy.legendInterior, kind: 'rect', color: 'var(--np-tint-caution)', count: '100' },
              { label: t.anatomy.legendSelected, kind: 'line', count: t.anatomy.legendSelectedCount },
            ]}
          />
          <div className="np-demo">
            <ScenarioPicker
              scenarios={ANATOMY_SCENARIOS}
              value={scenario}
              onChange={(id) => setScenario(id, 0)}
            />
            <PlaybackBar
              step={activeStep}
              count={steps.length}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={setStep}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              caption={
                <>
                  <span className="mono">{current.code}</span>
                  <span className="np-playback-sep"> · </span>
                  {c(current.title)}
                </>
              }
            />
          </div>
          {split ? (
            <div className="np-split-strip" role="list" aria-label={t.anatomy.register}>
              {FURNACE_BAYS.map((item, index) => (
                <button
                  key={item.id}
                  type="button"
                  role="listitem"
                  className="np-split-bay"
                  aria-pressed={item.id === selected}
                  onClick={() => selectBay(item.id)}
                >
                  <span className="mono">#{index + 1}</span>
                  <span className="strong">{item.name}</span>
                  <span className="mono muted">
                    {item.coverage} · {item.scope.toUpperCase()} · {item.cells}
                  </span>
                </button>
              ))}
            </div>
          ) : null}
        </Panel>
      </div>

      <div className="c4">
        <div className="stack">
          <Panel
            title={t.anatomy.oneCheck}
            count={giant ? t.anatomy.overlayOn : t.anatomy.overlayOff}
            error={giant}
          >
            <Toggle pressed={giant} onChange={(next) => setScenario(next ? 'giant-check' : 'legal-split', 0)}>
              {t.anatomy.giantCheck}
            </Toggle>
            <div className="np-step-readout" aria-live="polite">
              <div className="row-between">
                <span className="micro">{fmt(t.demo.stopOf, { n: activeStep + 1, total: steps.length })}</span>
                <span className="mono small">{current.code}</span>
              </div>
              <p className="small">{c(current.title)}</p>
            </div>
            <Readout label={t.anatomy.boundsVolume} value="294" unit={t.anatomy.cellsUnit} state="active" primary />
            <div className="readout-grid">
              <Readout label={t.anatomy.wall} value="194" unit={t.anatomy.cellsUnit} />
              <Readout label={t.anatomy.cavity} value="100" unit={t.anatomy.cellsUnit} />
              <Readout label={t.anatomy.mouth} value="2" unit={t.anatomy.cellsUnit} />
            </div>
            <div className="tbl-wrap">
              <table className="tbl">
                <tbody>
                  {CELL_LEDGER.map((row) => (
                    <tr key={row.label}>
                      <td>{c(row.label)}</td>
                      <td className="num">{row.value}</td>
                      <td className="mono-cell muted">{c(row.note)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>

          <Panel title={fmt(t.anatomy.selected, { name: bay.name })} count={fmt(t.anatomy.selectedCount, { n: bay.cells })}>
            <dl className="bay-dl">
              <dt>{t.anatomy.coverage}</dt>
              <dd>{bay.coverage}</dd>
              <dt>{t.anatomy.mode}</dt>
              <dd>{bay.mode}</dd>
              <dt>{t.anatomy.scope}</dt>
              <dd>{bay.scope}</dd>
              <dt>{t.anatomy.subject}</dt>
              <dd>{bay.subject}</dd>
              <dt>{t.anatomy.stage}</dt>
              <dd>{bay.stage}</dd>
              <dt>{t.anatomy.focus}</dt>
              <dd>{bay.focus}</dd>
              <dt>{t.anatomy.owns}</dt>
              <dd>{c(bay.owned)}</dd>
            </dl>
            <p className="note">{c(bay.note)}</p>
          </Panel>
        </div>
      </div>

      <div className="c12">
        {giant ? (
          <div className="hazard-banner" style={{ marginBottom: 12 }}>
            <div className="stripe" aria-hidden="true" />
            <div className="stripe-text">{t.anatomy.hazard}</div>
          </div>
        ) : null}
        <Panel
          title={t.anatomy.whyGiant}
          count={t.anatomy.whyGiantCount}
          error={giant}
          actions={<span className="micro">{t.anatomy.whyGiantAction}</span>}
        >
          <div className="g12" style={{ gap: 12 }}>
            {GIANT_CHECK_REASONS.map((reason) => (
              <div className="c6" key={reason.no}>
                <div
                  className="stack-2 np-reason"
                  data-active={giant && reason.no === current.reasonId}
                  style={{
                    borderLeft: `3px solid ${giant ? 'var(--np-alert)' : 'var(--np-rule)'}`,
                    paddingLeft: 10,
                    minHeight: 96,
                  }}
                >
                  <div className="row-between">
                    <span className="mono muted">{reason.no}</span>
                    <StatusChip kind={giant ? 'fail' : 'neutral'} size="sm">
                      {reason.code}
                    </StatusChip>
                  </div>
                  <div className="strong small">{c(reason.title)}</div>
                  <p className="small muted">{c(reason.body)}</p>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </div>

      <div className="c12">
        <Panel title={t.anatomy.register} count={fmt(t.anatomy.registerCount, { n: FURNACE_BAYS.length })}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.anatomy.thBay}</th>
                  <th className="num">{t.anatomy.thCells}</th>
                  <th>{t.anatomy.thCoverage}</th>
                  <th>{t.anatomy.thMode}</th>
                  <th>{t.anatomy.thScope}</th>
                  <th>{t.anatomy.thSubject}</th>
                  <th>{t.anatomy.thStage}</th>
                  <th>{t.anatomy.thOwned}</th>
                </tr>
              </thead>
              <tbody>
                {FURNACE_BAYS.map((b) => (
                  <tr key={b.id} data-selected={b.id === selected}>
                    <td className="mono-cell">{b.name}</td>
                    <td className="num">{b.cells}</td>
                    <td>{b.coverage}</td>
                    <td>{b.mode}</td>
                    <td>{b.scope}</td>
                    <td className="mono-cell">{b.subject}</td>
                    <td>{b.stage}</td>
                    <td className="mono-cell">{c(b.owned)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="note">{t.anatomy.registerNote}</div>
        </Panel>
      </div>

      <div className="c6">
        <Panel title={t.anatomy.legality} count="SC-CHK-0106">
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.anatomy.thCoverage}</th>
                  <th>{t.anatomy.thLegal}</th>
                  <th>{t.anatomy.thAssertion}</th>
                </tr>
              </thead>
              <tbody>
                {PRESENCE_RULE.map((rule) => (
                  <tr key={rule.coverage}>
                    <td className="mono-cell">{rule.coverage}</td>
                    <td className="mono-cell">
                      {rule.legal.join(' / ')}
                    </td>
                    <td className="wrap-cell">{c(rule.asserts)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="note">{t.anatomy.legalityNote}</p>
        </Panel>
      </div>

      <div className="c6">
        <Panel title={t.anatomy.whyNotPresence} count={REJECTED_BAY.code}>
          <div className="formula formula--plain">
            {fmt(t.anatomy.presenceFormula, {
              coverage: REJECTED_BAY.coverage,
              scope: REJECTED_BAY.scope,
              code: REJECTED_BAY.code,
            })}
          </div>
          <p className="small">{c(REJECTED_BAY.body)}</p>
          <p className="note">{t.anatomy.presenceNote}</p>
        </Panel>
      </div>
    </div>
  );
}
