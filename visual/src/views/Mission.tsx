import { useMemo } from 'react';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import { VoxelCanvas, type Camera } from '../components/VoxelCanvas';
import { useExplain } from '../components/ExplainDrawer';
import { useLang } from '../i18n/LangContext';
import { EXTRA } from '../i18n/extra';
import { MISSION_SCENARIOS, MISSION_STEPS, type MissionScenarioId, type MissionStep } from '../data/mission';
import { VOXELS, cellKey, type BayId } from '../data/voxel';

const ALL_BAYS = new Set<BayId>(['wall', 'mouth', 'interior']);
const CAMERA: Camera = { az: 35, el: 0.62, zoom: 26 };

const same = (step: MissionStep, lang: 'zh' | 'en') => ({
  title: lang === 'zh' ? step.titleZh : step.title,
  detail: lang === 'zh' ? step.detailZh : step.detail,
  state: lang === 'zh' ? step.stateZh : step.state,
  rule: lang === 'zh' ? step.ruleZh : step.rule,
});

const RESULT_ZH: Record<MissionStep['result'], string> = {
  run: '进行中',
  ok: '通过',
  fail: '失败',
  unknown: '未定',
  skip: '跳过',
};
const RESULT_EN: Record<MissionStep['result'], string> = {
  run: 'running',
  ok: 'ok',
  fail: 'failed',
  unknown: 'undecided',
  skip: 'skipped',
};

export function MissionView() {
  const { lang } = useLang();
  const copy = EXTRA[lang].mission;
  const { open } = useExplain();
  const { scenario, setScenario, step, setStep } = useScenarioState<MissionScenarioId>({
    page: 'mission',
    scenarios: MISSION_SCENARIOS,
    defaultScenario: 'formed',
    stepCount: 15,
    defaultStep: 0,
  });
  const steps = MISSION_STEPS[scenario];
  const playback = usePlayback(steps.length, { step, onStep: setStep });
  const current = steps[Math.min(playback.step, steps.length - 1)];
  const text = same(current, lang);

  const highlight = useMemo(() => {
    const out = new Set<string>();
    for (const voxel of VOXELS) {
      const hit =
        current.highlight === 'all' ||
        (current.highlight === 'cavity' && voxel.bay === 'interior') ||
        (current.highlight === 'mouth' && voxel.bay === 'mouth') ||
        (current.highlight === 'shell' && voxel.bay === 'wall');
      if (hit) out.add(cellKey(voxel));
    }
    return out;
  }, [current.highlight]);

  const state = current.result === 'fail' ? 'fail' : current.result === 'unknown' ? 'unknown' : current.result === 'skip' ? 'unknown' : 'ok';

  return (
    <div className="g12">
      <div className="c8">
        <Panel title={copy.panel} count={copy.sub}>
          <div className="np-mission-canvas">
            <VoxelCanvas
              voxels={VOXELS}
              camera={CAMERA}
              onCamera={() => undefined}
              layer={5}
              bays={ALL_BAYS}
              overlay="bay"
              selected={null}
              onSelect={() => undefined}
              highlight={highlight}
              cutaway
              height={380}
            />
          </div>
          <div className="np-mission-controls">
            <ScenarioPicker
              scenarios={MISSION_SCENARIOS}
              value={scenario}
              onChange={(id) => setScenario(id)}
              label={copy.scenario}
            />
            <PlaybackBar
              step={playback.step}
              count={steps.length}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={setStep}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              caption={`${String(playback.step + 1).padStart(2, '0')} / ${String(steps.length).padStart(2, '0')}`}
            />
          </div>
        </Panel>
      </div>

      <div className="c4">
        <Panel title={`${String(current.no).padStart(2, '0')} · ${text.title}`} count={current.stage}>
          <div className="np-readout-grid">
            <Readout label={copy.stage} value={current.stage} />
            <Readout label={copy.state} value={text.state} state={state as 'ok' | 'fail' | 'unknown'} />
            <Readout label={copy.result} value={(lang === 'zh' ? RESULT_ZH : RESULT_EN)[current.result]} state={state as 'ok' | 'fail' | 'unknown'} />
            <Readout label={copy.spend} value={current.spend.join(' / ')} />
            <Readout label={copy.reads} value="294 / 1" />
            <Readout label={copy.generation} value={scenario === 'stale' && current.no === 15 ? '2' : '1'} />
          </div>
          <p className="np-mission-detail">{text.detail}</p>
          <div className="np-mission-rule">
            <span className="np-mission-rule-label">{copy.rule}</span>
            <code>{text.rule}</code>
          </div>
          <button type="button" className="np-mini-btn np-mini-btn--wide" onClick={() => open(current.explain)}>
            {copy.explain}
          </button>
        </Panel>
      </div>

      <div className="c12">
        <WhyNote>{copy.note}</WhyNote>
      </div>
    </div>
  );
}
