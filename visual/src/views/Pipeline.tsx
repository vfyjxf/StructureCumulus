import { useEffect, useState } from 'react';
import { Panel } from '../components/Panel';
import { WhyNote } from '../components/WhyNote';
import { StatusChip } from '../components/StatusChip';
import { Stepper, type StepItem, type StepVisualState } from '../components/Stepper';
import { Segmented, Toggle } from '../components/Controls';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  PHASE_NOTES,
  PIPELINE_SCENARIOS,
  PIPELINE_STEPS,
  PIPELINE_TOGGLES,
  STAGES,
  VERDICT_RULES,
  type PipelineScenarioId,
  type StageKey,
  type ToggleId,
  type Verdict,
} from '../data/pipeline';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

const initialToggles: Record<ToggleId, boolean> = {
  mismatch: false,
  refused: false,
  budget: false,
  stale: false,
  conflict: false,
};

const computeVerdict = (stage: StageKey, toggles: Record<ToggleId, boolean>): Verdict => {
  if (stage === 'SETTLE' && toggles.conflict) {
    return {
      arm: 'Failed',
      code: 'SC-CLM-0002',
      detail: 'An incumbent holds an EXCLUSIVE cell; the ClaimConflict verdict is returned with the incumbent rows.',
      state: 'fail',
      atStep: 'settle',
    };
  }
  if (toggles.mismatch) {
    return {
      arm: 'Failed',
      code: 'SC-EXP-0100',
      detail: 'One Check returned MISMATCH. Refutation outranks ignorance, even beside unreadable cells.',
      state: 'fail',
      atStep: 'judge',
    };
  }
  if (toggles.stale) {
    return {
      arm: 'Undecided',
      reason: 'DIRTY_UNSTABLE',
      code: 'SC-CTX-0108',
      detail: 'The publish base revision moved; no frame was written. The attempt stopped before a verdict.',
      state: 'unknown',
      atStep: 'publish',
    };
  }
  if (toggles.budget) {
    return {
      arm: 'Undecided',
      reason: 'BUDGET_EXHAUSTED',
      code: 'SC-ENG-0001',
      detail: 'The instance budget refused a unit. A re-read is licensed; a repair is not.',
      state: 'unknown',
      atStep: 'aggregate',
    };
  }
  if (toggles.refused) {
    return {
      arm: 'Undecided',
      reason: 'UNREADABLE',
      code: 'SC-FOC-0301',
      detail: 'A refused section made a key absent; the predicate was never called, so nothing was learned.',
      state: 'unknown',
      atStep: 'judge',
    };
  }
  if (stage === 'SETTLE') {
    return {
      arm: 'Formed',
      code: 'Settled',
      detail: 'Claims settled in one CAS: 294 rows, generation 1, contextRevision 7, ledgerRevision 4.',
      state: 'ok',
      atStep: 'settle',
    };
  }
  return {
    arm: 'Admitted',
    code: `${stage} · MATCH`,
    detail:
      stage === 'PROBE'
        ? 'PROBE passed: bounds, anchor and census. No claims, no instance id; derive() refuses it.'
        : 'FORM passed: bays asserted, computations folded, checks judged. Still no claims; this is not a structure.',
    state: 'info',
    atStep: 'judge',
  };
};

export function PipelineView() {
  const { scenario, setScenario, step, setStep } = useScenarioState<PipelineScenarioId>({
    page: 'pipeline',
    scenarios: PIPELINE_SCENARIOS,
    defaultScenario: 'formed',
    defaultStep: 7,
    stepCount: PIPELINE_STEPS.length,
  });
  const preset = PIPELINE_SCENARIOS.find((item) => item.id === scenario) ?? PIPELINE_SCENARIOS[0];
  const [stage, setStage] = useState<StageKey>(preset.stage);
  const [toggles, setToggles] = useState<Record<ToggleId, boolean>>(preset.toggles);

  const activeStep = Math.min(step, preset.stopAt);
  const reached = activeStep >= preset.stopAt;
  const playback = usePlayback(preset.stopAt + 1, {
    step: activeStep,
    onStep: (next) => setStep(Math.min(next, preset.stopAt)),
    loop: false,
  });

  const t = useT();
  const c = useLang().c;

  useEffect(() => {
    setStage(preset.stage);
    setToggles(preset.toggles);
    if (step > preset.stopAt) setStep(preset.stopAt);
  }, [preset, step, setStep]);

  const verdict = computeVerdict(stage, toggles);
  const selected = PIPELINE_STEPS[activeStep] ?? PIPELINE_STEPS[5];

  const affected = new Set<string>([verdict.atStep]);
  if (toggles.refused) affected.add('section');
  if (toggles.budget) affected.add('aggregate');
  if (toggles.stale) affected.add('publish');
  if (stage === 'SETTLE' && toggles.conflict) affected.add('settle');
  if (toggles.mismatch) affected.add('judge');

  const steps: StepItem[] = PIPELINE_STEPS.map((stepSpec, index) => {
    let state: StepVisualState = stepSpec.inStage[stage] === 'skip' ? 'skipped' : 'todo';
    if (index < activeStep) state = stepSpec.inStage[stage] === 'skip' ? 'skipped' : 'done';
    if (index === activeStep) {
      if (reached && verdict.arm === 'Failed') state = 'failed';
      else if (reached && verdict.arm === 'Undecided') state = 'warn';
      else state = 'current';
    }
    return { id: stepSpec.id, no: stepSpec.no, label: c(stepSpec.name), state };
  });

  const stageSpec = STAGES.find((s) => s.id === stage) ?? STAGES[1];
  const runStopped = reached && (verdict.arm === 'Failed' || verdict.arm === 'Undecided');
  const stepVerdict = reached
    ? `${verdict.arm}${verdict.reason ? ` · ${verdict.reason}` : ''}`
    : t.pipeline.running;

  const selectStep = (id: string) => {
    const index = PIPELINE_STEPS.findIndex((item) => item.id === id);
    if (index >= 0) setStep(index);
  };

  const applyScenario = (id: PipelineScenarioId) => {
    const next = PIPELINE_SCENARIOS.find((item) => item.id === id) ?? PIPELINE_SCENARIOS[0];
    setScenario(id, 0);
    setStage(next.stage);
    setToggles(next.toggles);
    playback.pause();
  };

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.pipeline.why}</WhyNote>
      </div>

      <div className="c12">
        <Panel
          title={t.pipeline.eightStep}
          count={c(PHASE_NOTES[stage])}
          actions={
            <Segmented
              label={t.pipeline.stagePlan}
              hideLabel
              ariaLabel={t.pipeline.stagePlan}
              options={STAGES.map((s) => ({ value: s.id, label: s.id }))}
              value={stage}
              onChange={(value) => setStage(value as StageKey)}
            />
          }
        >
          <div className="np-demo">
            <ScenarioPicker
              scenarios={PIPELINE_SCENARIOS}
              value={scenario}
              onChange={applyScenario}
            />
            <PlaybackBar
              step={activeStep}
              count={PIPELINE_STEPS.length}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={(next) => setStep(Math.min(next, preset.stopAt))}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              caption={
                <>
                  <span className="mono">
                    {selected.no} {c(selected.name)}
                  </span>
                  <span className="np-playback-sep"> · </span>
                  {stepVerdict}
                  {runStopped ? <span className="mono muted"> · {t.pipeline.stoppedHere}</span> : null}
                </>
              }
            />
          </div>
          <div className="np-stage-rail" aria-hidden="true">
            <svg viewBox="0 0 760 30" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
              <line x1={20} y1={18} x2={740} y2={18} className="grid-line" vectorEffect="non-scaling-stroke" />
              {PIPELINE_STEPS.map((item, index) => {
                const x = 20 + (index * 720) / (PIPELINE_STEPS.length - 1);
                return (
                  <g key={item.id}>
                    <line
                      x1={x}
                      y1={13}
                      x2={x}
                      y2={23}
                      stroke={index <= activeStep ? 'var(--np-ink)' : 'var(--np-rule)'}
                      vectorEffect="non-scaling-stroke"
                    />
                    <text
                      className="t-mono"
                      x={x}
                      y={10}
                      fontSize={8}
                      textAnchor="middle"
                      fill={index <= activeStep ? 'var(--np-ink)' : 'var(--np-ink-muted)'}
                    >
                      {item.no}
                    </text>
                  </g>
                );
              })}
              <g
                className="np-token"
                style={{ transform: `translate(${20 + (activeStep * 720) / (PIPELINE_STEPS.length - 1)}px, 18px)` }}
              >
                <rect x={-5} y={-5} width={10} height={10} fill="var(--np-signal)" stroke="var(--np-signal-ink)" strokeWidth={2} />
              </g>
            </svg>
          </div>
          <Stepper
            steps={steps}
            selectedId={selected.id}
            onSelect={selectStep}
            ariaLabel={t.pipeline.stepperAria}
          />
          <div className="row" style={{ gap: 12 }}>
            <StatusChip kind={!reached ? 'neutral' : verdict.state === 'ok' ? 'ok' : verdict.state === 'fail' ? 'fail' : verdict.state === 'info' ? 'info' : 'warn'}>
              {!reached ? t.pipeline.running : verdict.arm}
            </StatusChip>
            <span className="mono small">{!reached ? c(PHASE_NOTES[stage]) : verdict.code}</span>
            <span className="micro">
              {reached
                ? fmt(t.pipeline.atStep, { step: c(PIPELINE_STEPS.find((s) => s.id === verdict.atStep)?.name ?? '') })
                : fmt(t.demo.stopOf, { n: activeStep + 1, total: preset.stopAt + 1 })}
            </span>
          </div>
        </Panel>
      </div>

      <div className="c4">
        <div className="stack">
          <Panel title={t.pipeline.verdict} count={fmt(t.pipeline.verdictCount, { stage })} error={reached && verdict.arm === 'Failed'}>
            <div className="np-step-readout" aria-live="polite">
              <div className="row-between">
                <span className="micro">{fmt(t.demo.stopOf, { n: activeStep + 1, total: PIPELINE_STEPS.length })}</span>
                <span className="mono small">{selected.no}</span>
              </div>
              <p className="small">
                <span className="strong">{c(selected.name)}</span>
                <span className="muted"> · {c(selected.code)}</span>
              </p>
            </div>
            <div
              className="verdict"
              data-state={!reached ? 'unknown' : verdict.state === 'ok' ? 'ok' : verdict.state === 'fail' ? 'fail' : 'unknown'}
            >
              <div className="verdict-bar" aria-hidden="true" />
              <div className="verdict-body">
                <div className="micro">MatchResult</div>
                <div className="verdict-arm" style={{ fontSize: 32, lineHeight: '32px', letterSpacing: '-0.02em' }}>
                  {reached ? verdict.arm : '—'}
                </div>
                <div className="verdict-detail">
                  {reached ? `${verdict.reason ? `${verdict.reason} · ` : ''}${c(verdict.code)}` : t.pipeline.running}
                </div>
              </div>
            </div>
            <p className="small">{reached ? c(verdict.detail) : c(selected.does)}</p>
            {reached && verdict.arm === 'Formed' ? (
              <dl className="kv">
                <dt>{t.pipeline.claimsField}</dt>
                <dd>{t.pipeline.claimsValue}</dd>
                <dt>{t.pipeline.generationField}</dt>
                <dd>1</dd>
                <dt>{t.pipeline.contextRevisionField}</dt>
                <dd>7</dd>
                <dt>{t.pipeline.leaseField}</dt>
                <dd>{t.pipeline.leaseValue}</dd>
              </dl>
            ) : null}
            {reached && verdict.arm === 'Admitted' ? (
              <div className="note">
                {t.pipeline.admittedNote}
                <span className="mono">derive()</span> {t.pipeline.admittedNoteTail}
              </div>
            ) : null}
            {reached && verdict.arm === 'Failed' ? <div className="note">{t.pipeline.failedNote}</div> : null}
          </Panel>

          <Panel title={t.pipeline.stage} count={stageSpec.arm} compact>
            <p className="small">{c(stageSpec.does)}</p>
            <p className="note">{c(stageSpec.detail)}</p>
            {runStopped ? (
              <div className="note">
                <span className="mono">{t.pipeline.stoppedHere}</span> · {fmt(t.demo.stopOf, { n: activeStep + 1, total: PIPELINE_STEPS.length })}
              </div>
            ) : null}
          </Panel>
        </div>
      </div>

      <div className="c8">
        <Panel
          title={t.pipeline.conditions}
          count={t.pipeline.conditionsCount}
          actions={
            <button type="button" className="btn btn--sm" onClick={() => setToggles(initialToggles)}>
              {t.pipeline.clear}
            </button>
          }
        >
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.pipeline.thCondition}</th>
                  <th>{t.pipeline.thInjectedAt}</th>
                  <th>{t.pipeline.thEffect}</th>
                </tr>
              </thead>
              <tbody>
                {PIPELINE_TOGGLES.map((toggle) => (
                  <tr key={toggle.id}>
                    <td style={{ minWidth: 150 }}>
                      <Toggle pressed={toggles[toggle.id]} onChange={(next) => setToggles({ ...toggles, [toggle.id]: next })}>
                        {c(toggle.label)}
                      </Toggle>
                    </td>
                    <td className="mono-cell">{toggle.step}</td>
                    <td className="wrap-cell">{c(toggle.note)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {stage !== 'SETTLE' ? <div className="note">{fmt(t.pipeline.conflictOnly, { stage })}</div> : null}
          <div className="note">{t.pipeline.stopOrder}</div>
        </Panel>
      </div>

      <div className="c12">
        <Panel
          title={fmt(t.pipeline.stepDetail, { name: c(selected.name) })}
          count={fmt(t.pipeline.stepDetailCount, { treatment: selected.inStage[stage].toUpperCase(), stage })}
        >
          <div className="g12" style={{ gap: 12 }}>
            <div className="c6">
              <div className="stack-2">
                <div>
                  <div className="micro">{t.pipeline.does}</div>
                  <p className="small">{c(selected.does)}</p>
                </div>
                <div>
                  <div className="micro">{t.pipeline.whyIt}</div>
                  <p className="small">{c(selected.why)}</p>
                </div>
              </div>
            </div>
            <div className="c6">
              <div className="stack-2">
                <div>
                  <div className="micro">{t.pipeline.removeIt}</div>
                  <p className="small">{c(selected.omit)}</p>
                </div>
                <div className="row-between">
                  <span className="micro">{t.pipeline.codeContract}</span>
                  <span className="mono small">{selected.code}</span>
                </div>
                <div className="formula formula--plain">{c(PHASE_NOTES[stage])}</div>
              </div>
            </div>
          </div>
        </Panel>
      </div>

      <div className="c12">
        <Panel title={t.pipeline.matrix} count={t.pipeline.matrixCount}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.pipeline.thStep}</th>
                  <th data-stage={stage === 'PROBE'}>PROBE</th>
                  <th data-stage={stage === 'FORM'}>FORM</th>
                  <th data-stage={stage === 'SETTLE'}>SETTLE</th>
                  <th>{t.pipeline.thWhy}</th>
                </tr>
              </thead>
              <tbody>
                {PIPELINE_STEPS.map((step) => (
                  <tr key={step.id}>
                    <td className="mono-cell">
                      {step.no} · {c(step.name)}
                    </td>
                    {(['PROBE', 'FORM', 'SETTLE'] as StageKey[]).map((s) => {
                      const treatment = step.inStage[s];
                      return (
                        <td key={s} data-selected={s === stage && treatment !== 'skip'}>
                          <StatusChip kind={treatment === 'run' ? 'ok' : treatment === 'partial' ? 'warn' : 'unknown'} size="sm">
                            {treatment}
                          </StatusChip>
                        </td>
                      );
                    })}
                    <td className="wrap-cell muted">{c(step.why)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      </div>

      <div className="c12">
        <Panel title={t.pipeline.verdictFold} count={t.pipeline.verdictFoldCount}>
          <ol className="stack-2">
            {VERDICT_RULES.map((rule, index) => (
              <li key={rule} style={{ display: 'grid', gridTemplateColumns: '24px 1fr', gap: 8 }}>
                <span className="mono muted">{String(index + 1).padStart(2, '0')}</span>
                <span className="small">{c(rule)}</span>
              </li>
            ))}
          </ol>
        </Panel>
      </div>
    </div>
  );
}
