import { useEffect, useMemo, useState } from 'react';
import { BAYS, CAMPFIRE_CELLS, CHECKS, DOCUMENT_SNIPPET, FURNACE, JAVA_SNIPPET, STAGES, type BayId } from '../data/campfire';
import { CodeBlock, Reveal, SectionHead, Segmented } from '../components/ui';
import { IsometricCampfire } from '../components/IsometricCampfire';

type StageId = 'compile' | 'check' | 'settle' | 'derive';
type SourceId = 'document' | 'java';

const STAGE_ORDER: StageId[] = ['compile', 'check', 'settle', 'derive'];

export function Workbench() {
  const [stage, setStage] = useState<StageId>('check');
  const [source, setSource] = useState<SourceId>('document');
  const [bay, setBay] = useState<BayId | 'all'>('all');
  const [exploded, setExploded] = useState(true);
  const [playing, setPlaying] = useState(false);
  const [showAllClaims, setShowAllClaims] = useState(false);

  const stageIndex = STAGE_ORDER.indexOf(stage);
  const stageData = STAGES.find((entry) => entry.id === stage)!;
  const activeBay = bay === 'all' ? null : BAYS.find((entry) => entry.id === bay)!;

  useEffect(() => {
    if (!playing) return;
    const timer = window.setInterval(() => {
      setStage((current) => {
        const index = STAGE_ORDER.indexOf(current);
        return STAGE_ORDER[(index + 1) % STAGE_ORDER.length];
      });
    }, 1900);
    return () => window.clearInterval(timer);
  }, [playing]);

  const claims = useMemo(
    () => CAMPFIRE_CELLS.map((cell) => ({ ...cell, key: `${cell.x}-${cell.y}-${cell.z}` })),
    [],
  );

  return (
    <section className="section" id="workbench" data-section>
      <SectionHead
        no="03"
        kicker="Worked example / campfire"
        title={<>One attempt, <em>four states</em>, no stage machine.</>}
        note={<>The same 3×3×3 campfire appears as JSON, as a Java builder and as one blueprint. Press through the attempt; the declaration never changes.</>}
      />

      <Reveal>
        <div className="workbench">
          <div className="workbench-top">
            <div className="scene">
              <div className="scene-head">
                <strong>kernel.structure / campfire</strong>
                <span className="mono">27 cells · 3 bays · 3 checks</span>
              </div>
              <div className="scene-note">
                <span>Hover to inspect · click a cell to select its bay</span>
                <button
                  type="button"
                  className="link-btn"
                  style={{ color: 'var(--scene-ink)' }}
                  onClick={() => setExploded((value) => !value)}
                >
                  {exploded ? 'assemble' : 'explode'}
                </button>
              </div>
              <IsometricCampfire activeBay={bay} onBay={setBay} exploded={exploded} />
              <div className="bay-picker">
                <button type="button" className="bay-pick" data-active={bay === 'all'} onClick={() => setBay('all')}>
                  all bays
                </button>
                {BAYS.map((entry) => (
                  <button
                    key={entry.id}
                    type="button"
                    className="bay-pick"
                    data-active={bay === entry.id}
                    onClick={() => setBay(entry.id)}
                  >
                    <span className="swatch" style={{ color: entry.id === 'shell' ? 'var(--cube-stone-top)' : entry.id === 'hollow' ? 'var(--cube-air-top)' : 'var(--cube-fire-top)' }} />
                    {entry.label} <span style={{ opacity: .55 }}>{entry.count}</span>
                  </button>
                ))}
              </div>
              <div className="scene-explain">
                <p>
                  {activeBay
                    ? <><strong>{activeBay.coverage}</strong> / {activeBay.footprint} / {activeBay.note}</>
                    : <><strong>27 cells</strong>; shell 25, hollow 1, fire 1. The fire cell is <code>(1,0,1)</code>; the hollow is <code>(1,1,1)</code>.</>}
                </p>
                <div className="big">{activeBay ? activeBay.count : 27}</div>
              </div>
            </div>

            <div className="workbench-side">
              <div className="stage-tabs" role="tablist" aria-label="Attempt stage">
                {STAGES.map((entry) => (
                  <button
                    key={entry.id}
                    type="button"
                    className="stage-tab"
                    role="tab"
                    aria-selected={entry.id === stage}
                    data-active={entry.id === stage}
                    onClick={() => { setStage(entry.id as StageId); setPlaying(false); }}
                  >
                    <strong>{entry.no}</strong>
                    {entry.label}
                  </button>
                ))}
              </div>

              <div className="stage-copy">
                <div className="stage-label">
                  <span className="kicker">{stageData.label} / {stageData.no}</span>
                  <span className="tag tag-accent">{stageData.result}</span>
                </div>
                <h4>{stageData.title}</h4>
                <p>{stageData.note}</p>
              </div>

              <div className="readout">
                {stageData.readout.map(([key, value]) => (
                  <div key={key}>
                    <span className="k">{key}</span>
                    <span className="v" data-ok={value === 'match' || value === 'same' || value === 'Formed' || value === 'Admitted' || value === '10'}>{value}</span>
                  </div>
                ))}
              </div>

              <div className="stage-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setPlaying((value) => !value)}>
                  {playing ? 'pause attempt' : 'run attempt'} <span aria-hidden="true">{playing ? 'Ⅱ' : '▶'}</span>
                </button>
                <div className="stage-dots" aria-label={`Stage ${stageIndex + 1} of 4`}>
                  {STAGE_ORDER.map((item, index) => <span key={item} className="stage-dot" data-on={index <= stageIndex} />)}
                </div>
              </div>
            </div>
          </div>

          <div className="workbench-bottom">
            <div className="source-panel">
              <div className="source-head">
                <span className="mono muted">The declaration / unchanged by the attempt</span>
                <div className="source-tabs">
                  <button type="button" className="source-tab" data-active={source === 'document'} onClick={() => setSource('document')}>document</button>
                  <button type="button" className="source-tab" data-active={source === 'java'} onClick={() => setSource('java')}>java</button>
                </div>
              </div>
              <CodeBlock
                className="source-code"
                code={source === 'document' ? DOCUMENT_SNIPPET : JAVA_SNIPPET}
                language={source === 'document' ? 'json' : 'java'}
              />
              <div className="source-foot">
                <span>same IR · same blueprint · same plan</span>
                <span>campfire / 3 bays / 3 checks</span>
              </div>
            </div>

            <aside className="workbench-aside">
              <h5>Claimed cells / {activeBay ? activeBay.count : 27}</h5>
              <div className="mini-claims">
                {claims
                  .filter((cell) => !activeBay || cell.bay === activeBay.id)
                  .map((cell) => <span key={cell.key} className="mini-claim" data-bay={cell.bay} title={`${cell.bay} (${cell.x},${cell.y},${cell.z})`} />)}
              </div>
              <div className="mini-legend">
                <span><i style={{ background: 'var(--stone)' }} />shell</span>
                <span><i style={{ background: 'var(--air)' }} />hollow</span>
                <span><i style={{ background: 'var(--fire)' }} />fire</span>
              </div>
              <div className="aside-rule" />
              <h5>Checks</h5>
              <div className="aside-list">
                {CHECKS.map((check) => (
                  <div key={check.id}>
                    <b>{check.id}</b> / {check.bay}<br />
                    {check.expect}
                  </div>
                ))}
              </div>
              <div className="aside-rule" />
              <button type="button" className="link-btn" onClick={() => setShowAllClaims((value) => !value)}>
                {showAllClaims ? 'hide claim rows' : 'show all 27 claim rows'}
              </button>
              {showAllClaims ? (
                <div className="mini-claims" style={{ marginTop: 10 }}>
                  {claims.map((cell) => <span key={cell.key} className="mini-claim" data-bay={cell.bay} title={`${cell.bay} (${cell.x},${cell.y},${cell.z})`} />)}
                </div>
              ) : null}
            </aside>
          </div>
        </div>
      </Reveal>

      <div className="pipeline-strip">
        {STAGES.map((entry) => (
          <div className="pipeline-stop" key={entry.id}>
            <span className="n">{entry.no}</span>
            <span className="l">{entry.label}</span>
            <span className="d">{entry.title}</span>
          </div>
        ))}
      </div>

      <Reveal delay={80}>
        <div className="furnace">
          <div className="furnace-id">
            <div className="mono">Second specimen</div>
            <h4>{FURNACE.title}</h4>
            <div className="mono" style={{ marginTop: 6, color: 'var(--faint)' }}>{FURNACE.id}</div>
          </div>
          <div className="furnace-bars">
            {FURNACE.bays.map((entry) => (
              <div className="furnace-bar" data-kind={entry.id} key={entry.id}>
                <span>{entry.id}</span>
                <span className="track"><span className="fill" style={{ display: 'block', width: `${(entry.cells / FURNACE.claims) * 100}%` }} /></span>
                <span style={{ textAlign: 'right' }}>{entry.cells}</span>
              </div>
            ))}
            <div className="mono muted" style={{ marginTop: 10, lineHeight: 1.5 }}>{FURNACE.note}</div>
          </div>
          <div className="furnace-total">
            <div className="n">{FURNACE.claims}</div>
            <div className="l">claim rows · one per cell</div>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
