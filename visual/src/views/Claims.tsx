import { useEffect, useMemo, useRef, useState } from 'react';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { StatusChip } from '../components/StatusChip';
import { Legend } from '../components/Legend';
import { DiagramSvg } from '../components/Diagram';
import { Segmented, Toggle, NumberStepper } from '../components/Controls';
import { PlaybackBar, usePlayback, useReducedMotion } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  CLAIM_RULES,
  CLAIM_STRUCTURES,
  CLAIMS_SCENARIOS,
  FOOTPRINT,
  GRID,
  MODE_NOTES,
  REVISION_NOTE,
  SCOPE_NOTES,
  type ClaimModeName,
  type ClaimScopeOption,
  type ClaimsScenarioId,
  type StructureSpec,
} from '../data/claims';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

const CELL = 48;
const OX = 108;
const OY = 84;

type ClaimVerdictView =
  | { kind: 'Settled'; revision: number; rows: number }
  | { kind: 'ClaimConflict'; rows: number; cells: string[] }
  | { kind: 'RevisionMoved'; expected: number; actual: number }
  | { kind: 'UndecidedLedger'; token: string };

interface StructState {
  mode: ClaimModeName;
  scope: ClaimScopeOption;
  offset: { x: number; z: number };
}

const initialStructs: Record<'A' | 'B', StructState> = {
  A: { mode: 'EXCLUSIVE', scope: 'Owned', offset: CLAIM_STRUCTURES[0].initialOffset },
  B: { mode: 'EXCLUSIVE', scope: 'Owned', offset: CLAIM_STRUCTURES[1].initialOffset },
};

const fullPattern: [number, number][] = [];
for (let x = 0; x < 3; x++) for (let z = 0; z < 3; z++) fullPattern.push([x, z]);

const cellsOf = (spec: StructureSpec, state: StructState): Set<string> => {
  const pattern = state.scope === 'Owned' ? fullPattern : FOOTPRINT[spec.pattern];
  const set = new Set<string>();
  for (const [dx, dz] of pattern) {
    const x = state.offset.x + dx;
    const z = state.offset.z + dz;
    if (x >= 0 && x < GRID && z >= 0 && z < GRID) set.add(`${x},${z}`);
  }
  return set;
};

export function ClaimsView() {
  const { scenario, setScenario, step, setStep } = useScenarioState<ClaimsScenarioId>({
    page: 'claims',
    scenarios: CLAIMS_SCENARIOS,
    defaultScenario: 'exclusive-conflict',
    defaultStep: 2,
    stepCount: 3,
  });
  const preset = CLAIMS_SCENARIOS.find((item) => item.id === scenario) ?? CLAIMS_SCENARIOS[0];
  const [structs, setStructs] = useState<Record<'A' | 'B', StructState>>(() => ({
    A: { mode: preset.a.mode, scope: preset.a.scope, offset: { ...preset.a.offset } },
    B: { mode: preset.b.mode, scope: preset.b.scope, offset: { ...preset.b.offset } },
  }));
  const [ledgerRevision, setLedgerRevision] = useState(preset.ledgerRevision);
  const [capturedRevision, setCapturedRevision] = useState(preset.capturedRevision);
  const [doubleMove, setDoubleMove] = useState(false);
  const [pulsing, setPulsing] = useState(false);
  const [pulsePending, setPulsePending] = useState(false);
  const [dragging, setDragging] = useState(false);
  const dragRef = useRef({ active: false, dx: 0, dz: 0 });
  const svgRef = useRef<SVGSVGElement | null>(null);
  const reduced = useReducedMotion();

  const activeStep = Math.min(step, 2);
  const playback = usePlayback(3, { step: activeStep, onStep: setStep });

  const t = useT();
  const c = useLang().c;

  const aSpec = CLAIM_STRUCTURES[0];
  const bSpec = CLAIM_STRUCTURES[1];

  const ownedA = useMemo(() => cellsOf(aSpec, structs.A), [aSpec, structs.A]);
  const ownedB = useMemo(() => cellsOf(bSpec, structs.B), [bSpec, structs.B]);

  const overlap = useMemo(() => [...ownedA].filter((cell) => ownedB.has(cell)), [ownedA, ownedB]);
  const conflicts = useMemo(
    () => overlap.filter(() => structs.A.mode === 'EXCLUSIVE' || structs.B.mode === 'EXCLUSIVE'),
    [overlap, structs.A.mode, structs.B.mode],
  );
  const rows = ownedA.size + ownedB.size;

  const showRows = activeStep >= 1;
  const showVerdict = activeStep >= 2;

  useEffect(() => {
    setStructs({
      A: { mode: preset.a.mode, scope: preset.a.scope, offset: { ...preset.a.offset } },
      B: { mode: preset.b.mode, scope: preset.b.scope, offset: { ...preset.b.offset } },
    });
    setLedgerRevision(preset.ledgerRevision);
    setCapturedRevision(preset.capturedRevision);
    setDoubleMove(false);
    setPulsing(false);
    setDragging(false);
    dragRef.current.active = false;
  }, [preset]);

  useEffect(() => {
    if (!pulsePending) return;
    setPulsePending(false);
    if (!conflicts.length || reduced) return;
    setPulsing(false);
    const frame = window.requestAnimationFrame(() => setPulsing(true));
    const timer = window.setTimeout(() => setPulsing(false), 220);
    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(timer);
    };
  }, [pulsePending, conflicts, reduced]);

  const update = (id: 'A' | 'B', next: Partial<StructState>) => {
    setStructs((current) => ({ ...current, [id]: { ...current[id], ...next } }));
    playback.pause();
    setStep(0);
  };

  const claimVerdict: ClaimVerdictView | null = !showVerdict
    ? null
    : ledgerRevision !== capturedRevision
      ? doubleMove
        ? { kind: 'UndecidedLedger', token: 'RT-7f3a91c' }
        : { kind: 'RevisionMoved', expected: capturedRevision, actual: ledgerRevision }
      : conflicts.length > 0
        ? { kind: 'ClaimConflict', rows, cells: conflicts }
        : { kind: 'Settled', revision: ledgerRevision, rows };

  const bumpLedger = () => {
    setLedgerRevision((r) => r + 1);
    playback.pause();
    setStep(2);
  };

  const retry = () => {
    setCapturedRevision(ledgerRevision);
    playback.pause();
    setStep(2);
  };

  const view = (v: ClaimVerdictView): { arm: string; state: 'ok' | 'fail' | 'unknown'; detail: string; code: string } => {
    switch (v.kind) {
      case 'Settled':
        return {
          arm: 'Settled',
          state: 'ok',
          code: 'ClaimVerdict.Settled',
          detail: fmt(t.claims.verdictSettled, { revision: v.revision, rows: v.rows }),
        };
      case 'ClaimConflict':
        return {
          arm: 'ClaimConflict',
          state: 'fail',
          code: 'Failed · SC-CLM-0002',
          detail: fmt(t.claims.verdictConflict, {
            n: v.cells.length,
            cells: `${v.cells.slice(0, 6).join(' · ')}${v.cells.length > 6 ? ' …' : ''}`,
          }),
        };
      case 'RevisionMoved':
        return {
          arm: 'RevisionMoved',
          state: 'unknown',
          code: t.claims.verdictMovedCode,
          detail: fmt(t.claims.verdictMoved, { expected: v.expected, actual: v.actual }),
        };
      case 'UndecidedLedger':
        return {
          arm: 'Undecided',
          state: 'unknown',
          code: 'LEDGER_MOVED',
          detail: fmt(t.claims.verdictLedger, { token: v.token }),
        };
    }
  };

  const verdictView = claimVerdict ? view(claimVerdict) : null;

  const toSvg = (event: React.PointerEvent<SVGSVGElement>) => {
    const svg = svgRef.current;
    if (!svg) return null;
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const matrix = svg.getScreenCTM();
    if (!matrix) return null;
    const local = point.matrixTransform(matrix.inverse());
    return { x: local.x, y: local.y };
  };

  const startDrag = (event: React.PointerEvent<SVGRectElement>) => {
    if (event.button !== 0) return;
    const point = toSvg(event as unknown as React.PointerEvent<SVGSVGElement>);
    if (!point) return;
    dragRef.current = {
      active: true,
      dx: (point.x - OX) / CELL - structs.B.offset.x,
      dz: (point.y - OY) / CELL - structs.B.offset.z,
    };
    setDragging(true);
    playback.pause();
    setStep(0);
    event.currentTarget.setPointerCapture(event.pointerId);
    event.preventDefault();
  };

  const moveDrag = (event: React.PointerEvent<SVGSVGElement>) => {
    if (!dragRef.current.active) return;
    const point = toSvg(event);
    if (!point) return;
    const x = Math.max(0, Math.min(GRID - 3, Math.round((point.x - OX) / CELL - dragRef.current.dx)));
    const z = Math.max(0, Math.min(GRID - 3, Math.round((point.y - OY) / CELL - dragRef.current.dz)));
    setStructs((current) =>
      current.B.offset.x === x && current.B.offset.z === z
        ? current
        : { ...current, B: { ...current.B, offset: { x, z } } },
    );
  };

  const endDrag = () => {
    if (!dragRef.current.active) return;
    dragRef.current.active = false;
    setDragging(false);
    setStep(0);
    setPulsePending(true);
  };

  const applyScenario = (id: ClaimsScenarioId) => {
    setScenario(id, 0);
    playback.pause();
  };

  const cellFill = (key: string) => {
    if (!showRows) return { fill: 'var(--np-paper)', stroke: 'var(--np-rule-soft)', width: 0.75 };
    if (showVerdict && conflicts.includes(key)) return { fill: 'url(#np-hatch-conflict)', stroke: 'var(--np-alert)', width: 1.5 };
    if (overlap.includes(key)) return { fill: 'url(#np-hatch-overlap)', stroke: 'var(--np-rule)', width: 1 };
    if (ownedA.has(key)) return { fill: 'var(--np-tint-signal)', stroke: 'var(--np-signal-ink)', width: 0.75 };
    if (ownedB.has(key)) return { fill: 'var(--np-tint-caution)', stroke: 'var(--np-caution-ink)', width: 0.75 };
    return { fill: 'var(--np-paper)', stroke: 'var(--np-rule-soft)', width: 0.75 };
  };

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.claims.why}</WhyNote>
      </div>

      <div className="c12 split-rail">
        <Panel
          title={t.claims.plane}
          count={fmt(t.claims.planeCount, { grid: GRID })}
          actions={
            <span className={`chip chip--${showVerdict ? (conflicts.length ? 'fail' : 'ok') : 'neutral'} chip--sm`}>
              {showVerdict ? (conflicts.length ? t.claims.conflict : t.claims.clear) : t.demo.settlePhases[activeStep]}
            </span>
          }
        >
          <DiagramSvg
            viewBox="0 0 720 440"
            minWidth={600}
            label={t.claims.diagramLabel}
            title={t.claims.diagramTitle}
            desc={t.claims.diagramDesc}
            svgRef={svgRef}
            svgStyle={{ touchAction: 'none' }}
            onPointerMove={moveDrag}
            onPointerUp={endDrag}
            onPointerCancel={endDrag}
          >
            <text className="t-label t-muted" x={24} y={24}>
              {fmt(t.claims.matrixRev, { rev: ledgerRevision })}
            </text>
            <text className="t-mono t-muted" x={24} y={40} fontSize={9}>
              {t.claims.matrixKey}
            </text>

            {Array.from({ length: GRID }, (_, i) => (
              <g key={`coord-${i}`} aria-hidden="true">
                <text className="t-mono t-muted" x={OX + i * CELL + CELL / 2} y={OY - 8} fontSize={9} textAnchor="middle">
                  {i}
                </text>
                <text className="t-mono t-muted" x={OX - 10} y={OY + i * CELL + CELL / 2 + 3} fontSize={9} textAnchor="end">
                  {i}
                </text>
              </g>
            ))}

            {Array.from({ length: GRID }, (_, z) =>
              Array.from({ length: GRID }, (_, x) => {
                const key = `${x},${z}`;
                const fill = cellFill(key);
                return (
                  <rect
                    key={key}
                    x={OX + x * CELL}
                    y={OY + z * CELL}
                    width={CELL}
                    height={CELL}
                    fill={fill.fill}
                    stroke={fill.stroke}
                    strokeWidth={fill.width}
                  />
                );
              }),
            )}

            <rect
              x={OX + structs.A.offset.x * CELL}
              y={OY + structs.A.offset.z * CELL}
              width={3 * CELL}
              height={3 * CELL}
              fill="none"
              stroke="var(--np-ink)"
              strokeWidth={2.5}
            />
            <rect
              x={OX + structs.B.offset.x * CELL}
              y={OY + structs.B.offset.z * CELL}
              width={3 * CELL}
              height={3 * CELL}
              fill="none"
              stroke="var(--np-ink)"
              strokeWidth={2.5}
              strokeDasharray="8 4"
            />
            <rect
              className={`claim-drag${dragging ? ' claim-drag--active' : ''}`}
              x={OX + structs.B.offset.x * CELL}
              y={OY + structs.B.offset.z * CELL}
              width={3 * CELL}
              height={3 * CELL}
              fill="transparent"
              pointerEvents="all"
              onPointerDown={startDrag}
              aria-hidden="true"
            >
              <title>{t.demo.dragHint}</title>
            </rect>
            <text className="t-mono" x={OX + structs.A.offset.x * CELL + 4} y={OY + structs.A.offset.z * CELL - 6} fontSize={10}>
              A
            </text>
            <text className="t-mono" x={OX + structs.B.offset.x * CELL + 4} y={OY + structs.B.offset.z * CELL - 6} fontSize={10}>
              B
            </text>
            {showRows ? (
              <g aria-hidden="true">
                <text
                  className="t-mono t-muted"
                  x={OX + structs.A.offset.x * CELL + 4}
                  y={OY + structs.A.offset.z * CELL + 3 * CELL + 11}
                  fontSize={8}
                >
                  {ownedA.size} {t.claims.rowsUnit}
                </text>
                <text
                  className="t-mono t-muted"
                  x={OX + structs.B.offset.x * CELL + 4}
                  y={OY + structs.B.offset.z * CELL + 3 * CELL + 11}
                  fontSize={8}
                >
                  {ownedB.size} {t.claims.rowsUnit}
                </text>
              </g>
            ) : null}
            {pulsing
              ? conflicts.map((key) => {
                  const [x, z] = key.split(',').map(Number);
                  return (
                    <rect
                      key={`pulse-${key}`}
                      className="np-conflict-pulse"
                      x={OX + x * CELL + 1}
                      y={OY + z * CELL + 1}
                      width={CELL - 2}
                      height={CELL - 2}
                      fill="none"
                      stroke="var(--np-alert)"
                      strokeWidth={2}
                      pointerEvents="none"
                    />
                  );
                })
              : null}

            <g aria-hidden="true">
              <text className="t-label t-muted" x={OX} y={OY + GRID * CELL + 28}>
                {t.claims.axisX}
              </text>
              <text className="t-label t-muted" x={OX - 84} y={OY + 10}>
                {t.claims.axisZ}
              </text>
              <text className="t-mono t-muted" x={OX} y={OY + GRID * CELL + 46} fontSize={9}>
                {fmt(t.claims.gridSummary, {
                  rows,
                  overlap: overlap.length,
                  conflicts: conflicts.length,
                })}
              </text>
            </g>
          </DiagramSvg>
          <div className="np-demo">
            <ScenarioPicker
              scenarios={CLAIMS_SCENARIOS}
              value={scenario}
              onChange={applyScenario}
            />
            <PlaybackBar
              step={activeStep}
              count={3}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={setStep}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              startLabel={t.claims.settle}
              caption={
                <>
                  <span className="mono">{t.demo.settlePhases[activeStep]}</span>
                  <span className="np-playback-sep"> · </span>
                  {showVerdict && verdictView
                    ? `${verdictView.arm} · ${verdictView.code}`
                    : fmt(t.demo.stopOf, { n: activeStep + 1, total: 3 })}
                </>
              }
            />
            <p className="np-demo-hint">{t.demo.dragHint}</p>
          </div>
          <Legend
            title={t.claims.ownership}
            items={[
              { label: t.claims.ownedA, kind: 'rect', color: 'var(--np-tint-signal)', count: `${ownedA.size}` },
              { label: t.claims.ownedB, kind: 'rect', color: 'var(--np-tint-caution)', count: `${ownedB.size}` },
              { label: t.claims.sharedCell, kind: 'overlap', count: `${overlap.length}` },
              { label: t.claims.conflictCell, kind: 'hatch', count: `${conflicts.length}` },
            ]}
          />
        </Panel>

        <div className="stack">
          <Panel title={t.claims.placeDeclare} count={t.claims.placeDeclareCount}>
            <div className="claim-controls">
              {CLAIM_STRUCTURES.map((spec) => {
                const state = structs[spec.id];
                return (
                  <div className="claim-struct" key={spec.id}>
                    <div className="claim-struct-hd">
                      <span className="mono strong">{spec.id}</span>
                      <span className="micro">{spec.name}</span>
                    </div>
                    <Segmented
                      label={t.claims.mode}
                      options={[
                        { value: 'EXCLUSIVE', label: 'EXC', title: 'EXCLUSIVE' },
                        { value: 'SHARED', label: 'SHR', title: 'SHARED' },
                        { value: 'RESERVED', label: 'RES', title: 'RESERVED' },
                      ]}
                      value={state.mode}
                      onChange={(mode) => update(spec.id, { mode: mode as ClaimModeName })}
                    />
                    <Segmented
                      label={t.claims.scope}
                      options={[
                        { value: 'Owned', label: 'OWNED' },
                        { value: 'Occupied', label: 'OCC' },
                      ]}
                      value={state.scope}
                      onChange={(scope) => update(spec.id, { scope: scope as ClaimScopeOption })}
                    />
                    <div className="row" style={{ gap: 8 }}>
                      <NumberStepper label="X" value={state.offset.x} min={0} max={3} onChange={(x) => update(spec.id, { offset: { ...state.offset, x } })} />
                      <NumberStepper label="Z" value={state.offset.z} min={0} max={3} onChange={(z) => update(spec.id, { offset: { ...state.offset, z } })} />
                    </div>
                  </div>
                );
              })}
            </div>
          </Panel>

          <Panel title={t.claims.ledger} count={fmt(t.claims.ledgerCount, { rev: capturedRevision })} error={claimVerdict?.kind === 'ClaimConflict'}>
            <Readout
              label={t.claims.conflicts}
              value={showVerdict ? String(conflicts.length) : '—'}
              unit={showVerdict ? t.claims.cellsUnit : ''}
              state={showVerdict ? (conflicts.length ? 'fail' : 'ok') : 'unknown'}
              primary
            />
            <div className="readout-grid readout-grid--2">
              <Readout
                label={t.claims.rows}
                value={showRows ? String(rows) : '—'}
                unit={showRows ? t.claims.rowsUnit : ''}
              />
              <Readout
                label={t.claims.ledgerRev}
                value={String(ledgerRevision)}
                unit={t.claims.revUnit}
                state={ledgerRevision !== capturedRevision ? 'unknown' : 'nominal'}
              />
            </div>
            <div className="row">
              <button type="button" className="btn btn--solid" onClick={playback.playFromStart}>
                {t.claims.settle}
              </button>
              <button type="button" className="btn" onClick={bumpLedger}>
                {t.claims.bumpLedger}
              </button>
              {claimVerdict?.kind === 'RevisionMoved' || claimVerdict?.kind === 'UndecidedLedger' ? (
                <button type="button" className="btn" onClick={retry}>
                  {t.claims.retry}
                </button>
              ) : null}
            </div>
            <Toggle
              pressed={doubleMove}
              onChange={(next) => {
                setDoubleMove(next);
                if (next) setStep(2);
              }}
            >
              {t.claims.secondMove}
            </Toggle>
            <p className="note">{c(REVISION_NOTE)}</p>
            {verdictView ? (
              <div className="verdict" data-state={verdictView.state}>
                <div className="verdict-bar" aria-hidden="true" />
                <div className="verdict-body">
                  <div className="micro">ClaimVerdict</div>
                  <div className="verdict-arm" style={{ fontSize: 22, lineHeight: '26px' }}>
                    {verdictView.arm}
                  </div>
                  <div className="verdict-detail">
                    {verdictView.code} · {verdictView.detail}
                  </div>
                </div>
              </div>
            ) : (
              <div className="empty-state">{t.claims.emptyState}</div>
            )}
          </Panel>
        </div>
      </div>

      <div className="c6">
        <Panel title={t.claims.declarationRules} count={t.claims.declarationRulesCount}>
          <ol className="stack-2">
            {CLAIM_RULES.map((rule, index) => (
              <li key={rule} style={{ display: 'grid', gridTemplateColumns: '24px 1fr', gap: 8 }}>
                <span className="mono muted">{String(index + 1).padStart(2, '0')}</span>
                <span className="small">{c(rule)}</span>
              </li>
            ))}
          </ol>
        </Panel>
      </div>

      <div className="c6">
        <Panel title={t.claims.modeScope} count={t.claims.modeScopeCount}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.claims.thMode}</th>
                  <th>{t.claims.thRule}</th>
                </tr>
              </thead>
              <tbody>
                {MODE_NOTES.map((row) => (
                  <tr key={row.mode}>
                    <td className="mono-cell">{row.mode}</td>
                    <td className="wrap-cell">{c(row.rule)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.claims.thScope}</th>
                  <th>{t.claims.thOwnedCells}</th>
                </tr>
              </thead>
              <tbody>
                {SCOPE_NOTES.map((row) => (
                  <tr key={row.scope}>
                    <td className="mono-cell">{row.scope}</td>
                    <td className="wrap-cell">{c(row.rule)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="row">
            <StatusChip kind={structs.A.mode === 'EXCLUSIVE' || structs.B.mode === 'EXCLUSIVE' ? 'warn' : 'ok'} size="sm">
              {structs.A.mode === 'EXCLUSIVE' || structs.B.mode === 'EXCLUSIVE'
                ? t.claims.exclusivePresent
                : t.claims.sharedOnly}
            </StatusChip>
            <span className="small muted">
              {fmt(t.claims.conflictSummary, {
                conflicts: conflicts.length,
                shared: overlap.length - conflicts.length,
              })}
            </span>
          </div>
          <p className="note">{t.claims.modeScopeNote}</p>
        </Panel>
      </div>
    </div>
  );
}
