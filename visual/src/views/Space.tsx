import { useEffect, useMemo, useRef, useState } from 'react';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { DiagramSvg } from '../components/Diagram';
import { StatusChip } from '../components/StatusChip';
import { Segmented, Toggle } from '../components/Controls';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  CARDINAL_HEADINGS,
  CUBE,
  HEADING_NAMES,
  HEADINGS,
  SITING_NOTES,
  SPACE_SCENARIOS,
  SPACE_SPIN_INTERVAL,
  TILT_NAMES,
  TURN_NAMES,
  canonicalTriple,
  fmtVec,
  headingOf,
  imageCuboid,
  orientVec,
  orientationId,
  ordinalOf,
  satisfyingOrdinals,
  stateOfOrdinal,
  type Flip,
  type OrientationState,
  type SpaceScenarioId,
  type Tilt,
  type Turn,
  type Vec3,
} from '../data/space';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

const S = 46;

const project = (v: Vec3, centre: Vec3) => {
  const dx = v[0] - centre[0];
  const dy = v[1] - centre[1];
  const dz = v[2] - centre[2];
  return {
    x: 292 + (dx - dz) * 0.866 * S,
    y: 196 - dy * S + (dx + dz) * 0.5 * S,
  };
};

const CORNERS: Vec3[] = [];
for (const x of [0, 3]) for (const y of [0, 3]) for (const z of [0, 3]) CORNERS.push([x, y, z]);

const cornerIndex = (x: number, y: number, z: number) => (x === 3 ? 4 : 0) + (y === 3 ? 2 : 0) + (z === 3 ? 1 : 0);

const CUBE_EDGES: [number, number][] = [];
for (let i = 0; i < 8; i++) {
  for (const bit of [1, 2, 4]) {
    if ((i & bit) === 0) CUBE_EDGES.push([i, i | bit]);
  }
}

const WORLD_AXES: { label: string; v: Vec3 }[] = [
  { label: 'X', v: [1, 0, 0] },
  { label: 'Y', v: [0, 1, 0] },
  { label: 'Z', v: [0, 0, 1] },
];

const axisPoint = (v: Vec3, length = 34) => ({
  x: 690 + (v[0] - v[2]) * 0.866 * length,
  y: 312 - v[1] * length + (v[0] + v[2]) * 0.5 * length,
});

export function SpaceView() {
  const [anchorHeading, setAnchorHeading] = useState<(typeof HEADING_NAMES)[number]>('NORTH');
  const [hold, setHold] = useState(false);
  const [mirroring, setMirroring] = useState(false);
  const [autoStart, setAutoStart] = useState(() => {
    try {
      return !new URLSearchParams(window.location.search).has('step');
    } catch {
      return true;
    }
  });
  const {
    scenario,
    setScenario,
    step,
    setStep,
  } = useScenarioState<SpaceScenarioId>({
    page: 'space',
    scenarios: SPACE_SCENARIOS,
    defaultScenario: 'spin',
    stepCount: 48,
  });

  const satisfying = useMemo(() => satisfyingOrdinals('NORTH', anchorHeading), [anchorHeading]);
  const stepCount = scenario === 'spin' ? 48 : satisfying.length;
  const activeStep = Math.min(step, Math.max(0, stepCount - 1));
  const targetOrdinal =
    scenario === 'spin' ? activeStep : (satisfying[activeStep] ?? 0);
  const state: OrientationState = stateOfOrdinal(targetOrdinal);
  const playback = usePlayback(stepCount, {
    step: activeStep,
    onStep: setStep,
    intervalMs: SPACE_SPIN_INTERVAL,
    autoStart,
    loop: scenario === 'spin',
    hold,
  });

  const t = useT();
  const c = useLang().c;

  const ordinal = ordinalOf(state);
  const applied = headingOf(orientVec(HEADINGS.NORTH, state));
  const pass = applied === anchorHeading;
  const image = imageCuboid([CUBE.size, CUBE.size, CUBE.size], state);

  const orientedCorners = CORNERS.map((corner) => orientVec(corner, state));
  const centre: Vec3 = [0, 1, 2].map((axis) => {
    const values = orientedCorners.map((corner) => corner[axis]);
    return (Math.min(...values) + Math.max(...values)) / 2;
  }) as Vec3;

  const P = (v: Vec3) => project(orientVec(v, state), centre);

  const cubePoints = CORNERS.map(P);
  const markerCell = [
    [1, 0, 1],
    [2, 0, 1],
    [2, 0, 2],
    [1, 0, 2],
  ] as Vec3[];
  const markerPoints = markerCell.map(P);
  const markerCentre = {
    x: markerPoints.reduce((sum, p) => sum + p.x, 0) / 4,
    y: markerPoints.reduce((sum, p) => sum + p.y, 0) / 4,
  };
  const frontA = P([1.5, 1.5, 0]);
  const frontB = P([1.5, 1.5, -1.8]);

  const floorLines = [];
  for (let i = 0; i <= 3; i++) {
    floorLines.push({ a: P([i, 0, 0]), b: P([i, 0, 3]) });
    floorLines.push({ a: P([0, 0, i]), b: P([3, 0, i]) });
  }

  const markerWorld = orientVec([CUBE.marker.x, CUBE.marker.y, CUBE.marker.z], state);
  const markerMaxX = Math.max(...markerPoints.map((p) => p.x));
  const markerMaxY = Math.max(...markerPoints.map((p) => p.y));
  const markerLabelX = markerMaxX + 48;
  const markerLabelY = markerMaxY + 32;
  const frontDx = frontB.x - frontA.x;
  const frontDy = frontB.y - frontA.y;
  const frontLen = Math.max(1, Math.hypot(frontDx, frontDy));
  const ux = frontDx / frontLen;
  const uy = frontDy / frontLen;
  const arrowPoints = [
    `${frontB.x},${frontB.y}`,
    `${frontB.x - ux * 8 - uy * 4},${frontB.y - uy * 8 + ux * 4}`,
    `${frontB.x - ux * 8 + uy * 4},${frontB.y - uy * 8 - ux * 4}`,
  ].join(' ');

  const volume =
    Math.abs(image.max[0] - image.min[0]) *
    Math.abs(image.max[1] - image.min[1]) *
    Math.abs(image.max[2] - image.min[2]);

  const cubeCentreScreen = {
    x: cubePoints.reduce((sum, point) => sum + point.x, 0) / cubePoints.length,
    y: cubePoints.reduce((sum, point) => sum + point.y, 0) / cubePoints.length,
  };

  const previousFlip = useRef(state.flip);
  useEffect(() => {
    if (previousFlip.current === state.flip) return;
    previousFlip.current = state.flip;
    if (playback.playing) return;
    setMirroring(true);
    const timer = window.setTimeout(() => setMirroring(false), 220);
    return () => window.clearTimeout(timer);
  }, [state.flip, playback.playing]);

  const applyState = (next: OrientationState) => {
    setScenario('spin', ordinalOf(next));
  };

  const markHold = (value: boolean) => () => setHold(value);

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.space.why}</WhyNote>
      </div>

      <div
        className="c8 np-hold-zone"
        onMouseEnter={markHold(true)}
        onMouseLeave={markHold(false)}
        onFocusCapture={markHold(true)}
        onBlurCapture={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setHold(false);
        }}
      >
        <Panel
          title={t.space.axonometric}
          count={t.space.axonometricCount}
          actions={<span className="micro">{t.space.axonometricAction}</span>}
        >
          <DiagramSvg
            viewBox="0 0 760 380"
            minWidth={620}
            label={t.space.diagramLabel}
            title={t.space.diagramTitle}
            desc={t.space.diagramDesc}
          >
            <text className="t-label t-muted" x={16} y={20}>
              {fmt(t.space.boundsLabel, { bounds: CUBE.boundsLabel })}
            </text>
            <text className="t-label t-muted" x={16} y={36}>
              {fmt(t.space.orientationLabel, { id: orientationId(state), triple: canonicalTriple(state) })}
            </text>

            {floorLines.map((line, index) => (
              <line key={`floor-${index}`} className="tick" x1={line.a.x} y1={line.a.y} x2={line.b.x} y2={line.b.y} />
            ))}

            <g
              className={mirroring ? 'np-mirroring' : 'np-cube'}
              style={{ transformOrigin: `${cubeCentreScreen.x}px ${cubeCentreScreen.y}px` }}
            >
              {CUBE_EDGES.map(([a, b], index) => (
                <line
                  key={`edge-${index}`}
                  x1={cubePoints[a].x}
                  y1={cubePoints[a].y}
                  x2={cubePoints[b].x}
                  y2={cubePoints[b].y}
                  stroke="var(--np-ink)"
                  strokeWidth={1.25}
                  vectorEffect="non-scaling-stroke"
                />
              ))}

              {CORNERS.map((corner, index) => {
                const p = cubePoints[cornerIndex(corner[0], corner[1], corner[2])];
                return <rect key={`v-${index}`} x={p.x - 1.5} y={p.y - 1.5} width={3} height={3} fill="var(--np-ink)" />;
              })}

              <polygon
                points={markerPoints.map((p) => `${p.x},${p.y}`).join(' ')}
                fill="var(--np-signal)"
                stroke="var(--np-signal-ink)"
                strokeWidth={2}
              />
            </g>
            <path
              className="callout-leader"
              d={`M ${markerCentre.x} ${markerCentre.y} H ${markerLabelX - 10} V ${markerLabelY}`}
              vectorEffect="non-scaling-stroke"
            />
            <rect x={markerLabelX - 10} y={markerLabelY} width={3} height={3} fill="var(--np-ink)" />
            <text className="t-mono" x={markerLabelX} y={markerLabelY + 6} fontSize={9}>
              {fmt(t.space.markerLabel, { marker: CUBE.markerLabel, world: fmtVec(markerWorld) })}
            </text>

            <line x1={frontA.x} y1={frontA.y} x2={frontB.x} y2={frontB.y} stroke="var(--np-ink)" strokeWidth={2} />
            <polygon className="arrow" points={arrowPoints} />
            <text className="t-mono" x={frontB.x + 8} y={frontB.y + 4} fontSize={9}>
              {fmt(t.space.frontLabel, { heading: applied })}
            </text>

            <g aria-label={t.space.worldAxes}>
              <text className="t-label t-muted" x={652} y={348}>
                {t.space.worldAxes}
              </text>
              {WORLD_AXES.map((axis) => {
                const p = axisPoint(axis.v);
                return (
                  <g key={axis.label}>
                    <line x1={690} y1={312} x2={p.x} y2={p.y} stroke="var(--np-ink)" strokeWidth={1.5} />
                    <text className="t-mono" x={p.x + 4} y={p.y + (axis.label === 'Y' ? -4 : 6)} fontSize={10}>
                      {axis.label}
                    </text>
                  </g>
                );
              })}
              <rect x={688} y={310} width={4} height={4} fill="var(--np-ink)" />
            </g>

            <g aria-label={t.space.fireBayCell}>
              <rect x={16} y={344} width={10} height={10} fill="var(--np-signal)" stroke="var(--np-signal-ink)" />
              <text className="t-mono t-muted" x={32} y={353} fontSize={9}>
                {t.space.fireBayCell}
              </text>
              <rect x={130} y={344} width={10} height={10} fill="none" stroke="var(--np-ink)" />
              <text className="t-mono t-muted" x={146} y={353} fontSize={9}>
                {t.space.cubeEdge}
              </text>
              <line x1={232} y1={349} x2={256} y2={349} stroke="var(--np-ink)" />
              <text className="t-mono t-muted" x={262} y={353} fontSize={9}>
                {t.space.authoredFront}
              </text>
            </g>
          </DiagramSvg>
          <div className="np-demo">
            <ScenarioPicker
              scenarios={SPACE_SCENARIOS}
              value={scenario}
              onChange={(id) => {
                setAutoStart(true);
                setScenario(id, 0);
              }}
            />
            <PlaybackBar
              step={activeStep}
              count={stepCount}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={setStep}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              caption={
                <>
                  <span className="mono">O-{String(ordinal).padStart(2, '0')}</span>
                  <span className="np-playback-sep"> · </span>
                  <span className="mono">{canonicalTriple(state)}</span>
                  <span className="np-playback-sep"> · </span>
                  {fmt(t.space.candidateFormula, { applied, heading: anchorHeading })}
                  <span className="mono muted">
                    {' '}
                    · {satisfying.length} {t.space.candidatesUnit}
                  </span>
                </>
              }
            />
            <p className="np-demo-hint">{t.demo.holdHint}</p>
          </div>
        </Panel>
      </div>

      <div className="c4">
        <div className="stack">
          <Panel title={t.space.controls} count={t.space.controlsCount}>
            <Segmented
              label={t.space.turnLabel}
              options={TURN_NAMES.map((name, index) => ({ value: index, label: ['NONE', 'CW', 'HALF', 'CCW'][index], title: name }))}
              value={state.turn}
              onChange={(turn) => applyState({ ...state, turn: turn as Turn })}
            />
            <Segmented
              label={t.space.tiltLabel}
              options={TILT_NAMES.map((name, index) => ({
                value: index,
                label: ['UP', 'SOUTH', 'NORTH', 'WEST', 'EAST', 'DOWN'][index],
                title: name,
              }))}
              value={state.tilt}
              onChange={(tilt) => applyState({ ...state, tilt: tilt as Tilt })}
            />
            <p className="note">{t.space.tiltNote}</p>
            <Toggle pressed={state.flip === 1} onChange={(next) => applyState({ ...state, flip: (next ? 1 : 0) as Flip })}>
              {t.space.flip}
            </Toggle>
            <Readout label={t.space.ordinal} value={String(ordinal).padStart(2, '0')} unit={t.space.ordinalUnit} state="active" primary />
            <Readout
              label={t.space.candidates}
              value={String(satisfying.length)}
              unit={t.space.candidatesUnit}
              state={pass ? 'ok' : 'unknown'}
            />
            <div className="formula formula--plain">{canonicalTriple(state)}</div>
          </Panel>

          <Panel title={t.space.anchor} count={t.space.anchorCount}>
            <Segmented
              label={t.space.anchorHeading}
              options={CARDINAL_HEADINGS.map((h) => ({ value: h, label: h[0] }))}
              value={anchorHeading}
              onChange={(h) => setAnchorHeading(h)}
            />
            <div className="formula">authoredFront = NORTH</div>
            <div className="row-between">
              <span className="micro">{t.space.candidate}</span>
              <StatusChip kind={pass ? 'ok' : 'fail'}>{pass ? t.space.pass : t.space.noMatch}</StatusChip>
            </div>
            <div className="formula formula--plain">{fmt(t.space.candidateFormula, { applied, heading: anchorHeading })}</div>
          </Panel>
        </div>
      </div>

      <div className="c6">
        <Panel title={t.space.headingMap} count={t.space.headingMapCount}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.space.thLocal}</th>
                  <th>{t.space.thWorld}</th>
                  <th>{t.space.thVector}</th>
                </tr>
              </thead>
              <tbody>
                {HEADING_NAMES.map((heading) => {
                  const v = orientVec(HEADINGS[heading], state);
                  return (
                    <tr key={heading}>
                      <td className="mono-cell">{heading}</td>
                      <td>
                        <StatusChip kind={heading === 'NORTH' ? 'info' : 'neutral'}>{headingOf(v)}</StatusChip>
                      </td>
                      <td className="num">{fmtVec(v)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="note">
            {fmt(t.space.headingNote, {
              x0: image.min[0],
              x1: image.max[0],
              y0: image.min[1],
              y1: image.max[1],
              z0: image.min[2],
              z1: image.max[2],
              volume,
            })}
          </div>
        </Panel>
      </div>

      <div className="c6">
        <Panel title={t.space.sitingFunction} count={fmt(t.space.sitingCount, { n: satisfying.length })}>
          <ol className="stack-2">
            {SITING_NOTES.map((note, index) => (
              <li key={note} style={{ display: 'grid', gridTemplateColumns: '20px 1fr', gap: 8 }}>
                <span className="mono muted">{String(index + 1).padStart(2, '0')}</span>
                <span className="small">{c(note)}</span>
              </li>
            ))}
          </ol>
          <div>
            <div className="micro" style={{ marginBottom: 4 }}>
              {fmt(t.space.satisfyingFor, { heading: anchorHeading })}
            </div>
            <div className="row" style={{ gap: 4 }}>
              {satisfying.map((o) => (
                <span
                  key={o}
                  className="chip chip--neutral"
                  style={{ fontFamily: 'var(--np-font-mono)', letterSpacing: 0 }}
                  title={fmt(t.space.orientationTitle, { o })}
                >
                  {String(o).padStart(2, '0')}
                </span>
              ))}
            </div>
          </div>
          <div className="note">{t.space.sitingNote}</div>
        </Panel>
      </div>
    </div>
  );
}
