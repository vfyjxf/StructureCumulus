import { useEffect, useMemo, useState } from 'react';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { StatusChip } from '../components/StatusChip';
import { Segmented, Toggle } from '../components/Controls';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  ABSENCE_RULES,
  CONTEXT_KEYS,
  CONTEXT_SCENARIOS,
  FOLD_RULES,
  FRAME_ORDER_NOTE,
  SECTION_ORDER,
  SECTIONS,
  TIERS,
  type ContextScenarioId,
} from '../data/context';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

type TierKey = 'PROBE' | 'FORM' | 'SETTLE';

const TIER_INDEX: Record<TierKey, number> = { PROBE: 1, FORM: 2, SETTLE: 3 };
const TIER_FRAMES: (typeof TIERS)[number]['tier'][] = ['BLUEPRINT', 'PROBE', 'FORM', 'SETTLE'];

const scenarioSteps = (id: ContextScenarioId): number =>
  id === 'standard' ? 8 : id === 'refused-middle' ? 3 : 5;
const scenarioFoldSteps = (id: ContextScenarioId): number =>
  id === 'standard' ? 4 : id === 'refused-middle' ? 3 : 1;

export function ContextView() {
  const [values, setValues] = useState<Record<string, number>>(
    Object.fromEntries(SECTIONS.map((s) => [s.id, s.initial])),
  );
  const [refused, setRefused] = useState<Record<string, boolean>>({});
  const [emptyRegion, setEmptyRegion] = useState(false);
  const [readerTier, setReaderTier] = useState<TierKey>('FORM');
  const { scenario, setScenario, step, setStep } = useScenarioState<ContextScenarioId>({
    page: 'context',
    scenarios: CONTEXT_SCENARIOS,
    defaultScenario: 'standard',
    defaultStep: 7,
    stepCount: scenarioSteps('standard'),
  });
  const steps = scenarioSteps(scenario);
  const foldSteps = scenarioFoldSteps(scenario);
  const activeStep = Math.min(step, steps - 1);
  const playback = usePlayback(steps, { step: activeStep, onStep: setStep });

  const t = useT();
  const c = useLang().c;

  const sorted = useMemo(
    () => [...SECTIONS].sort((a, b) => a.x - b.x || a.z - b.z || a.y - b.y),
    [],
  );

  const refusedAt = sorted.findIndex((section) => refused[section.id]);
  const aborted = refusedAt >= 0 && (scenario !== 'refused-middle' || activeStep >= refusedAt + 1);
  const contributions = emptyRegion ? [] : sorted.filter((s) => !refused[s.id]);
  const fullTotal = contributions.reduce((sum, s) => sum + (values[s.id] ?? 0), 0);
  const fullHeat = contributions.reduce((max, s) => Math.max(max, values[s.id] ?? 0), 0);

  const readableBeforeRefusal = refusedAt >= 0 ? sorted.slice(0, refusedAt).filter((s) => !refused[s.id]).length : Infinity;
  const consumed = Math.min(activeStep, contributions.length, readableBeforeRefusal);
  const running = contributions.slice(0, consumed).reduce((sum, s) => sum + (values[s.id] ?? 0), 0);
  let accumulation = 0;
  const cumulative = contributions.map((section) => {
    accumulation += values[section.id] ?? 0;
    return accumulation;
  });

  const foldComplete = consumed >= contributions.length;
  const volumeState: number | 'missing' = emptyRegion || aborted ? 'missing' : foldComplete ? fullTotal : running;
  const capacityState: number | 'missing' =
    volumeState === 'missing' ? 'missing' : volumeState * 4;
  const heatState: number | 'missing' = aborted
    ? 'missing'
    : emptyRegion
      ? 0
      : foldComplete
        ? fullHeat
        : contributions.slice(0, consumed).reduce((max, s) => Math.max(max, values[s.id] ?? 0), 0);

  const tierPhase = activeStep >= foldSteps;
  const litFrame = tierPhase ? Math.min(TIER_FRAMES.length - 1, activeStep - foldSteps) : -1;

  const display = (value: number | 'missing') => (value === 'missing' ? '—' : String(value));

  const activeAbsence = aborted ? 'refused' : emptyRegion ? 'declined' : null;

  const keyValue: Record<string, number | 'missing'> = {
    'mymod:volume': volumeState,
    'mymod:capacity': capacityState,
    'mymod:heat': heatState,
  };

  const tierValue = (keyId: string, tierIndex: number): string => {
    const value = keyValue[keyId];
    if (value === 'missing') return '—';
    if (tierIndex < 2) return '—';
    return String(value);
  };

  const applyScenario = (id: ContextScenarioId) => {
    const preset = CONTEXT_SCENARIOS.find((item) => item.id === id) ?? CONTEXT_SCENARIOS[0];
    setScenario(id, 0);
    setValues(Object.fromEntries(SECTIONS.map((section) => [section.id, section.initial])));
    setRefused(Object.fromEntries(preset.refused.map((sectionId) => [sectionId, true])));
    setEmptyRegion(preset.emptyRegion);
    setReaderTier('FORM');
    playback.pause();
  };

  useEffect(() => {
    const preset = CONTEXT_SCENARIOS.find((item) => item.id === scenario);
    if (!preset) return;
    setRefused(Object.fromEntries(preset.refused.map((sectionId) => [sectionId, true])));
    setEmptyRegion(preset.emptyRegion);
    // The preset is applied once per scenario; later edits belong to the reviewer.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenario]);

  const toggleRefused = (id: string, next: boolean) => {
    setRefused({ ...refused, [id]: next });
    if (next) {
      playback.pause();
      const index = sorted.findIndex((section) => section.id === id);
      if (index >= 0) setStep(index + 1);
    }
  };

  const toggleEmpty = (next: boolean) => {
    setEmptyRegion(next);
    playback.pause();
    setStep(0);
  };

  const caption = tierPhase
    ? `${t.demo.frame} ${TIER_FRAMES[litFrame] ?? TIER_FRAMES[0]}`
    : fmt(t.demo.foldOf, {
        n: consumed,
        total: contributions.length,
        value: aborted ? t.context.missing : String(running),
      });

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.context.why}</WhyNote>
      </div>

      <div className="c12 split-rail">
        <Panel
          title={t.context.contributions}
          count={fmt(t.context.contributionsCount, { n: SECTIONS.length })}
          actions={<span className="micro">{fmt(t.demo.stopOf, { n: activeStep + 1, total: steps })}</span>}
        >
          <div className="np-demo">
            <ScenarioPicker
              scenarios={CONTEXT_SCENARIOS}
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
              caption={caption}
            />
          </div>
          <div className="row-between">
            <span className="micro">{t.context.canonicalOrder}</span>
            <span className="micro">{t.context.valuesRefused}</span>
          </div>

          <div>
            {sorted.map((section, index) => {
              const isRefused = !emptyRegion && refused[section.id];
              const isConsumed =
                !isRefused && sorted.slice(0, index).filter((item) => !refused[item.id]).length < consumed;
              return (
                <div className="section-row" key={section.id} data-consumed={isConsumed} data-refused-point={isRefused && aborted}>
                  <div>
                    <div className="section-key">
                      {`(${section.x}, ${section.z}, ${section.y})`}
                      <span className="muted"> · S-{String(index + 1).padStart(2, '0')}</span>
                    </div>
                    <div className="micro" style={{ textTransform: 'none', letterSpacing: 0 }}>
                      x {section.x} · z {section.z} · y {section.y}
                    </div>
                  </div>
                  <label className="field">
                    <span className="field-label" style={{ textAlign: 'right' }}>
                      {t.context.partial}
                    </span>
                    <input
                      className="input input--num"
                      type="number"
                      min={0}
                      max={999}
                      value={values[section.id]}
                      disabled={emptyRegion || isRefused}
                      onChange={(event) =>
                        setValues({ ...values, [section.id]: Math.max(0, Math.min(999, Number(event.target.value))) })
                      }
                      aria-label={fmt(t.context.contributionAria, { x: section.x, z: section.z, y: section.y })}
                    />
                  </label>
                  <Toggle
                    pressed={Boolean(refused[section.id])}
                    onChange={(next) => toggleRefused(section.id, next)}
                    title={t.context.refuseTitle}
                  >
                    {t.context.refused}
                  </Toggle>
                  <StatusChip kind={isRefused ? 'fail' : emptyRegion ? 'unknown' : 'ok'} size="sm">
                    {isRefused ? t.context.refused : emptyRegion ? t.context.declined : t.context.readable}
                  </StatusChip>
                </div>
              );
            })}
          </div>

          <div className="row-between">
            <Toggle pressed={emptyRegion} onChange={toggleEmpty}>
              {t.context.emptyRegion}
            </Toggle>
            <span className="micro">{t.context.authoredOrder}</span>
          </div>

          <div className="fold-line">
            <span className="micro">{t.context.fold}</span>
            {contributions.length === 0 ? (
              <span className="muted">{t.context.noContribution}</span>
            ) : (
              <>
                {cumulative.slice(0, consumed).map((value, index) => (
                  <span key={contributions[index].id} className="row" style={{ gap: 4 }}>
                    <span className="mono">{value}</span>
                    {index < consumed - 1 ? <span className="muted">→</span> : null}
                  </span>
                ))}
                {consumed > 0 ? <span className="muted">=</span> : null}
                <span className="fold-value">{aborted ? t.context.missing : consumed === 0 ? '—' : running}</span>
                <span className="micro">
                  {fmt(t.context.consumed, {
                    step: consumed,
                    total: contributions.length,
                    order: SECTION_ORDER.replace('SectionKey ', ''),
                  })}
                </span>
              </>
            )}
          </div>

          <div className="np-fold-bar" aria-hidden="true">
            <span
              className="np-fold-bar-fill"
              style={{ transform: `scaleX(${fullTotal > 0 ? Math.min(1, running / fullTotal) : 0})` }}
            />
          </div>

          {aborted ? (
            <div className="error-line">
              <StatusChip kind="fail" size="sm" solid>
                SC-FOC-0301
              </StatusChip>
              <span>
                {t.context.refusedLine} <span className="mono">require()</span> {t.context.refusedLineTail}
              </span>
            </div>
          ) : null}

          <div className="note">{t.context.foldNote}</div>
        </Panel>

        <div className="stack">
          <Panel title={t.context.foldedResult} count={t.context.foldedResultCount}>
            <Readout
              label="mymod:volume"
              value={display(volumeState)}
              unit={volumeState === 'missing' ? '' : t.context.cellsUnit}
              state={volumeState === 'missing' ? 'unknown' : 'active'}
              primary
              delta={
                volumeState === 'missing'
                  ? t.context.missingNotZero
                  : fmt(t.demo.partialOf, { n: consumed, total: contributions.length })
              }
            />
            <div className="tbl-wrap">
              <table className="tbl">
                <tbody>
                  {CONTEXT_KEYS.map((key) => {
                    const value = keyValue[key.id];
                    return (
                      <tr key={key.id}>
                        <td className="mono-cell">{key.id}</td>
                        <td>
                          {value === 'missing' ? (
                            <StatusChip kind="fail" size="sm">
                              {t.context.missing}
                            </StatusChip>
                          ) : (
                            <span className="mono">{value}</span>
                          )}
                        </td>
                        <td className="mono-cell muted">
                          {key.merge ? `merge ${key.merge}` : t.context.singleWriter}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="note">
              <strong>whenAbsent</strong>
              {t.context.whenAbsentNote}
            </div>
          </Panel>

          <Panel title={t.context.reader} count="tier-stamped">
            <Segmented
              label={t.context.readAtTier}
              options={[
                { value: 'PROBE', label: 'Probe' },
                { value: 'FORM', label: 'Form' },
                { value: 'SETTLE', label: 'Settle' },
              ]}
              value={readerTier}
              onChange={(value) => setReaderTier(value as TierKey)}
            />
            <div className="formula formula--plain">
              {fmt(t.context.volumeAt, {
                tier: readerTier,
                value: TIER_INDEX[readerTier] < 2 || volumeState === 'missing' ? '—' : volumeState,
              })}
            </div>
            <p className="small muted">{t.context.probeNote}</p>
          </Panel>
        </div>
      </div>

      <div className="c12">
        <Panel
          title={t.context.tierFrames}
          count={fmt(t.context.tierFramesCount, { n: TIERS.length })}
          actions={<span className="micro">{fmt(t.context.readerAt, { tier: readerTier })}</span>}
        >
          <div className="tier-grid" role="group" aria-label={t.context.tierAria}>
            <div className="tier-hd">{t.context.thKey}</div>
            {TIERS.map((tier) => (
              <div className="tier-hd" key={tier.tier}>
                {tier.tier}
              </div>
            ))}
            {CONTEXT_KEYS.map((key) => (
              <div key={key.id} style={{ display: 'contents' }}>
                <div className="tier-key">{key.id}</div>
                {TIERS.map((tier, index) => {
                  const visible = index <= TIER_INDEX[readerTier];
                  const raw = tierValue(key.id, index);
                  const value = visible ? raw : '—';
                  const live = visible && value !== '—';
                  return (
                    <div
                      key={tier.tier}
                      className={`tier-val${value === '—' ? ' tier-val--ghost' : ''}${live ? ' tier-val--live' : ''}`}
                      data-frame-lit={tierPhase && index <= litFrame}
                      data-frame-active={tierPhase && index === litFrame}
                    >
                      {value}
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span className="micro">{t.context.published}</span>
            <span className="micro">{fmt(t.context.readerSees, { tier: readerTier })}</span>
          </div>
          <p className="note">{c(FRAME_ORDER_NOTE)}</p>
        </Panel>
      </div>

      <div className="c6">
        <Panel title={t.context.absencePaths} count={t.context.absenceCount}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.context.thCase}</th>
                  <th>{t.context.thResult}</th>
                  <th>{t.context.thCode}</th>
                  <th>{t.context.thWhy}</th>
                </tr>
              </thead>
              <tbody>
                {ABSENCE_RULES.map((rule) => (
                  <tr
                    key={rule.state}
                    data-selected={activeAbsence === rule.state || (activeAbsence === 'declined' && rule.state === 'empty')}
                  >
                    <td className="mono-cell">{rule.state}</td>
                    <td>
                      <StatusChip kind={rule.verdict === 'Missing' ? 'fail' : rule.verdict === 'whenAbsent' ? 'warn' : 'neutral'} size="sm">
                        {rule.verdict}
                      </StatusChip>
                    </td>
                    <td className="mono-cell">{c(rule.code)}</td>
                    <td className="wrap-cell">{c(rule.body)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      </div>

      <div className="c6">
        <Panel title={t.context.foldRules} count={t.context.foldRulesCount}>
          <ol className="stack-2">
            {FOLD_RULES.map((rule, index) => (
              <li key={rule} style={{ display: 'grid', gridTemplateColumns: '24px 1fr', gap: 8 }}>
                <span className="mono muted">{String(index + 1).padStart(2, '0')}</span>
                <span className="small">{c(rule)}</span>
              </li>
            ))}
          </ol>
          <div className="formula formula--plain">{t.context.foldFormula}</div>
          <p className="note">{c(CONTEXT_KEYS.find((k) => k.id === 'mymod:capacity')?.note ?? '')}</p>
        </Panel>
      </div>
    </div>
  );
}
