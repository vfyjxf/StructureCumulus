import { useEffect, useRef, useState } from 'react';
import { Panel } from '../components/Panel';
import { WhyNote } from '../components/WhyNote';
import { StatusChip, stageKind } from '../components/StatusChip';
import { DiagramSvg, NodeBox } from '../components/Diagram';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  BUNDLE_CAP_NOTE,
  BUNDLE_NOTE,
  DEMO_FINDINGS,
  FINDING_COMPONENTS,
  FINDING_EXAMPLE,
  REEMIT_STEPS,
  RESULT_ARMS,
  RESULTS_SCENARIOS,
  SORT_KEYS,
  sortDemand,
  type ResultsScenarioId,
} from '../data/results';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

const ARM_POS = [
  { id: 'admitted', y: 16 },
  { id: 'formed', y: 92 },
  { id: 'failed', y: 168 },
  { id: 'undecided', y: 244 },
  { id: 'deferred', y: 320 },
];

const scenarioSteps = (id: ResultsScenarioId): number =>
  id === 'arm-path' ? RESULT_ARMS.length : id === 'dirty-unstable' ? 2 : 6;

export function ResultsView() {
  const [selectedComponent, setSelectedComponent] = useState('code');
  const { scenario, setScenario, step, setStep } = useScenarioState<ResultsScenarioId>({
    page: 'results',
    scenarios: RESULTS_SCENARIOS,
    defaultScenario: 'arm-path',
    defaultStep: 1,
    stepCount: 6,
  });
  const steps = scenarioSteps(scenario);
  const activeStep = Math.min(step, steps - 1);
  const playback = usePlayback(steps, { step: activeStep, onStep: setStep });

  const t = useT();
  const c = useLang().c;

  const selectedArm = scenario === 'arm-path' ? RESULT_ARMS[activeStep].id : 'formed';
  const arm = RESULT_ARMS.find((a) => a.id === selectedArm) ?? RESULT_ARMS[1];
  const component = FINDING_COMPONENTS.find((comp) => comp.key === selectedComponent) ?? FINDING_COMPONENTS[1];
  const sortDepth = scenario === 'reorder' ? activeStep : 5;
  const sorted = sortDemand(sortDepth);
  const reemit = REEMIT_STEPS[Math.min(scenario === 'dirty-unstable' ? activeStep : 0, REEMIT_STEPS.length - 1)];

  const [previousArm, setPreviousArm] = useState<string | null>(null);
  const [transitionKey, setTransitionKey] = useState(0);
  const armRef = useRef(selectedArm);
  useEffect(() => {
    if (armRef.current === selectedArm) return;
    setPreviousArm(armRef.current);
    armRef.current = selectedArm;
    setTransitionKey((key) => key + 1);
  }, [selectedArm]);

  const armY = (id: string) => ARM_POS.find((pos) => pos.id === id)?.y ?? 0;
  const showPath = scenario === 'arm-path' && previousArm !== null && previousArm !== selectedArm;
  const pathY = armY(previousArm ?? '');
  const pathY2 = armY(selectedArm);

  const applyScenario = (id: ResultsScenarioId) => {
    setScenario(id, 0);
    setPreviousArm(null);
    playback.pause();
  };

  const startReorder = () => {
    setScenario('reorder', 0);
    playback.playFromStart();
  };

  const componentValue = (key: string): string => {
    switch (key) {
      case 'id':
        return FINDING_EXAMPLE.id;
      case 'code':
        return FINDING_EXAMPLE.code;
      case 'severity':
        return FINDING_EXAMPLE.severity;
      case 'message':
        return c(FINDING_EXAMPLE.message);
      case 'labels':
        return `${FINDING_EXAMPLE.labels[0].role} · ${FINDING_EXAMPLE.labels[0].frame} · ${c(FINDING_EXAMPLE.labels[0].text)}`;
      case 'notes':
        return FINDING_EXAMPLE.notes[0];
      case 'suggestion':
        return c(FINDING_EXAMPLE.suggestion);
      case 'related':
        return FINDING_EXAMPLE.related.join(', ');
      case 'stage':
        return FINDING_EXAMPLE.stage;
      default:
        return '—';
    }
  };

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.results.why}</WhyNote>
      </div>

      <div className="c8">
        <Panel
          title={t.results.stateMachine}
          count={t.results.stateMachineCount}
          actions={<span className="micro">{t.results.stateMachineAction}</span>}
        >
          <div className="np-demo">
            <ScenarioPicker
              scenarios={RESULTS_SCENARIOS}
              value={scenario}
              onChange={applyScenario}
            />
            <PlaybackBar
              step={activeStep}
              count={steps}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={setStep}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              startLabel={scenario === 'reorder' ? t.results.reorder : t.demo.play}
              caption={
                scenario === 'arm-path' ? (
                  <>
                    <span className="mono">{arm.name}</span>
                    <span className="np-playback-sep"> · </span>
                    {c(arm.tagline)}
                  </>
                ) : scenario === 'dirty-unstable' ? (
                  <>
                    <span className="mono">{c(reemit.outcome)}</span>
                    <span className="np-playback-sep"> · </span>
                    {fmt(t.results.generation, { n: reemit.generation })}
                  </>
                ) : (
                  <>
                    <span className="mono">{sortDepth === 0 ? '—' : SORT_KEYS[sortDepth - 1]?.label}</span>
                    <span className="np-playback-sep"> · </span>
                    {fmt(t.results.bundleCount, { n: DEMO_FINDINGS.length, depth: sortDepth })}
                  </>
                )
              }
            />
          </div>
          <div className="arm-list" style={{ marginBottom: 12 }}>
            {RESULT_ARMS.map((item, index) => (
              <button
                key={item.id}
                type="button"
                className="arm-btn"
                aria-pressed={selectedArm === item.id}
                onClick={() => {
                  setScenario('arm-path', index);
                  playback.pause();
                }}
              >
                <span className="arm-name">{item.name}</span>
                <span className="arm-note">{c(item.tagline)}</span>
              </button>
            ))}
          </div>
          <DiagramSvg
            viewBox="0 0 840 400"
            minWidth={720}
            interactive
            label={t.results.diagramLabel}
            title={t.results.diagramTitle}
            desc={t.results.diagramDesc}
          >
            <text className="t-label t-muted" x={20} y={12}>
              {t.results.caption}
            </text>

            <NodeBox x={20} y={172} w={150} h={56} title="MatchRequest" sub={t.results.requestSub} />
            <g>
              <polygon
                points="274,156 318,204 274,252 230,204"
                fill="var(--np-paper)"
                stroke="var(--np-rule)"
                strokeWidth={1.5}
              />
              <text x={274} y={208} textAnchor="middle" fontSize={10} fontFamily="var(--np-font-body)" fontWeight={600}>
                JUDGE
              </text>
            </g>
            <g>
              <polygon
                points="420,156 464,204 420,252 376,204"
                fill="var(--np-paper)"
                stroke="var(--np-rule)"
                strokeWidth={1.5}
              />
              <text x={420} y={208} textAnchor="middle" fontSize={10} fontFamily="var(--np-font-body)" fontWeight={600}>
                SETTLE
              </text>
            </g>

            <line x1={170} y1={200} x2={228} y2={200} className="wire" vectorEffect="non-scaling-stroke" />
            <polygon className="arrow" points="230,200 223,196.5 223,203.5" />
            <line x1={318} y1={200} x2={374} y2={200} className="wire" vectorEffect="non-scaling-stroke" />
            <polygon className="arrow" points="376,200 369,196.5 369,203.5" />
            <text className="t-mono t-muted" x={346} y={190} fontSize={8} textAnchor="middle" paintOrder="stroke" stroke="var(--np-paper)" strokeWidth={3}>
              SETTLE
            </text>

            <line x1={464} y1={204} x2={540} y2={204} className="wire wire--signal" vectorEffect="non-scaling-stroke" />
            <line x1={540.5} y1={44} x2={540.5} y2={348} className="wire wire--signal" vectorEffect="non-scaling-stroke" />
            <text className="t-mono t-muted" x={548} y={198} fontSize={8}>
              {t.results.verdictEdge}
            </text>
            {showPath ? (
              <path
                key={`arm-path-${transitionKey}`}
                className="np-arm-path"
                d={`M 584 ${pathY + 28} H 540.5 V ${pathY2 + 28} H 584`}
                fill="none"
                stroke="var(--np-signal-ink)"
                strokeWidth={2}
                vectorEffect="non-scaling-stroke"
                aria-hidden="true"
              />
            ) : null}

            {ARM_POS.map((pos) => {
              const item = RESULT_ARMS.find((a) => a.id === pos.id);
              if (!item) return null;
              const selected = selectedArm === item.id;
              return (
                <g key={pos.id}>
                  <line x1={540} y1={pos.y + 28} x2={582} y2={pos.y + 28} className="wire" vectorEffect="non-scaling-stroke" />
                  <polygon className="arrow" points={`584,${pos.y + 28} 577,${pos.y + 24.5} 577,${pos.y + 31.5}`} />
                  <NodeBox
                    x={584}
                    y={pos.y}
                    w={236}
                    h={56}
                    title={item.name}
                    sub={c(item.tagline)}
                    selected={selected}
                    onSelect={() => setScenario('arm-path', RESULT_ARMS.findIndex((entry) => entry.id === item.id))}
                    ariaLabel={`${item.name}: ${c(item.tagline)}`}
                  />
                </g>
              );
            })}

            <path
              d="M 95 228 V 376 H 540 V 348"
              fill="none"
              stroke="var(--np-rule)"
              strokeWidth={1.5}
              strokeDasharray="4 3"
              vectorEffect="non-scaling-stroke"
            />
            <text className="t-mono t-muted" x={110} y={368} fontSize={8}>
              {t.results.noDispatch}
            </text>

            <text className="t-mono t-muted" x={20} y={396} fontSize={8}>
              {t.results.footer}
            </text>
          </DiagramSvg>
        </Panel>
      </div>

      <div className="c4">
        <div className="stack">
          <Panel title={fmt(t.results.armTitle, { name: arm.name })} count={arm.state.toUpperCase()}>
          <div className="row">
            <StatusChip
              kind={arm.state === 'ok' ? 'ok' : arm.state === 'fail' ? 'fail' : arm.state === 'info' ? 'info' : arm.state === 'unknown' ? 'unknown' : 'neutral'}
              size="lg"
            >
              {arm.name}
            </StatusChip>
            <span className="small muted">{c(arm.tagline)}</span>
          </div>
          <p className="small">{c(arm.when)}</p>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th style={{ width: 130 }}>{t.results.thField}</th>
                  <th>{t.results.thType}</th>
                </tr>
              </thead>
              <tbody>
                {arm.fields.map((field) => (
                  <tr key={field.name}>
                    <td className="mono-cell">{field.name}</td>
                    <td className="wrap-cell">
                      <span className="mono">{field.type}</span>
                      <div className="micro" style={{ textTransform: 'none', letterSpacing: 0 }}>
                        {c(field.note)}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>

        <Panel title={t.results.reemit} count={`gen ${reemit.generation}`}>
          <div className="np-demo-readout" data-state={reemit.generation > 1 ? 'active' : undefined} aria-live="polite">
            <div className="row-between">
              <StatusChip kind="ok" size="sm">
                Formed
              </StatusChip>
              <span className="mono small">{fmt(t.results.generation, { n: reemit.generation })}</span>
            </div>
            <div className="micro">{t.results.outcome}</div>
            <div className="np-demo-object mono">{c(reemit.outcome)}</div>
            <dl className="kv">
              <dt>{t.results.generationLabel}</dt>
              <dd>{reemit.generation}</dd>
              <dt>contextRevision</dt>
              <dd>{reemit.contextRevision}</dd>
              <dt>ledgerRevision</dt>
              <dd>{reemit.ledgerRevision}</dd>
            </dl>
            <p className="small">{c(reemit.note)}</p>
          </div>
        </Panel>
        </div>
      </div>

      <div className="c12">
        <Panel
          title={t.results.findingAnatomy}
          count={t.results.findingAnatomyCount}
          actions={<span className="micro">{t.results.findingAnatomyAction}</span>}
        >
          <div className="finding-split">
            <div className="finding-list">
              <div className="finding-record" role="group" aria-label={t.results.findingAria}>
                <div className="finding-record-hd" aria-hidden="true">
                  <span>{t.results.thHash}</span>
                  <span>{t.results.thComponent}</span>
                  <span>{t.results.thType}</span>
                  <span>{t.results.thExample}</span>
                </div>
                {FINDING_COMPONENTS.map((item, index) => {
                  const selected = item.key === selectedComponent;
                  const value = componentValue(item.key);
                  return (
                    <button
                      key={item.key}
                      type="button"
                      className="finding-record-row"
                      aria-pressed={selected}
                      aria-label={`${item.key}, ${item.type}, ${value}`}
                      title={value}
                      onClick={() => setSelectedComponent(item.key)}
                    >
                      <span className="mono muted fr-no">{String(index + 1).padStart(2, '0')}</span>
                      <span className="mono fr-key">{item.key}</span>
                      <span className="mono fr-type">{item.type}</span>
                      <span className="fr-example">{value}</span>
                    </button>
                  );
                })}
              </div>
              <div className="note" style={{ marginTop: 6 }}>
                {fmt(t.results.findingNote, { id: FINDING_EXAMPLE.id, severity: FINDING_EXAMPLE.severity })}
              </div>
            </div>
            <div className="finding-detail">
              <div className="stack-2">
                <div className="row-between">
                  <span className="mono strong">{component.key}</span>
                  <StatusChip kind="neutral" size="sm">
                    {component.type}
                  </StatusChip>
                </div>
                <div>
                  <div className="micro">{t.results.example}</div>
                  <p className="mono small" style={{ overflowWrap: 'anywhere' }}>
                    {componentValue(component.key)}
                  </p>
                </div>
                <div>
                  <div className="micro">{t.results.whyComponent}</div>
                  <p className="small">{c(component.note)}</p>
                </div>
                <div className="formula formula--plain">
                  Finding(id, code, severity, message, labels, notes, suggestion, related, stage)
                </div>
                <div className="note">{t.results.findingValueNote}</div>
              </div>
            </div>
          </div>
        </Panel>
      </div>

      <div className="c12">
        <Panel
          title={t.results.bundle}
          count={fmt(t.results.bundleCount, { n: DEMO_FINDINGS.length, depth: sortDepth })}
          actions={
            <div className="row" style={{ gap: 4 }}>
              {SORT_KEYS.map((key) => (
                <button
                  key={key.id}
                  type="button"
                  className="btn btn--sm"
                  aria-pressed={sortDepth >= key.id}
                  title={c(key.note)}
                  onClick={() => setScenario('reorder', key.id)}
                >
                  {key.label}
                </button>
              ))}
              <button type="button" className="btn btn--sm" onClick={() => setScenario('reorder', 0)}>
                {t.results.reset}
              </button>
              <button type="button" className="btn btn--sm" onClick={startReorder}>
                {t.results.reorder}
              </button>
            </div>
          }
        >
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th className="num" style={{ width: 40 }}>
                    {t.results.thHash}
                  </th>
                  <th>{t.results.thId}</th>
                  <th>{t.results.thSeverity}</th>
                  <th>{t.results.thStage}</th>
                  <th>{t.results.thCode}</th>
                  <th>{t.results.thLocus}</th>
                  <th className="num">{t.results.thInsert}</th>
                  <th>{t.results.thMessage}</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((finding, index) => (
                  <tr
                    key={`${finding.id}-${scenario === 'reorder' ? activeStep : 'static'}`}
                    className={scenario === 'reorder' && activeStep > 0 ? 'np-row-flash' : undefined}
                  >
                    <td className="num">{String(index + 1).padStart(2, '0')}</td>
                    <td className="mono-cell">{finding.id}</td>
                    <td>
                      <StatusChip kind={finding.severity === 'ERROR' ? 'fail' : finding.severity === 'WARNING' ? 'warn' : 'neutral'} size="sm">
                        {finding.severity}
                      </StatusChip>
                    </td>
                    <td>
                      <StatusChip kind={stageKind(finding.stage)} size="sm">
                        {finding.stage}
                      </StatusChip>
                    </td>
                    <td className="mono-cell">{finding.code}</td>
                    <td className="mono-cell">
                      {finding.frame}
                      {finding.frame === 'LOCAL' || finding.frame === 'WORLD' ? ` (${finding.y},${finding.z},${finding.x})` : ''}
                    </td>
                    <td className="num">{finding.insertion}</td>
                    <td className="wrap-cell muted">{c(finding.message)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="g12" style={{ gap: 12 }}>
            <div className="c6">
              <div className="note">{c(BUNDLE_NOTE)}</div>
            </div>
            <div className="c6">
              <div className="note">{c(BUNDLE_CAP_NOTE)}</div>
            </div>
          </div>
          <div className="row" style={{ gap: 8 }}>
            <span className="micro">{t.results.activeKeys}</span>
            {SORT_KEYS.map((key) => (
              <StatusChip key={key.id} kind={sortDepth >= key.id ? 'info' : 'unknown'} size="sm">
                {key.id} {key.label}
              </StatusChip>
            ))}
          </div>
        </Panel>
      </div>
    </div>
  );
}
