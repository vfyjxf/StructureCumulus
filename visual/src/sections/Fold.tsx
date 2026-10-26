import { useMemo, useState } from 'react';
import { CodeBlock, Reveal, SectionHead, Segmented } from '../components/ui';

const SECTIONS = [
  { id: 'S-00', value: 3 },
  { id: 'S-01', value: 5 },
  { id: 'S-02', value: 2 },
  { id: 'S-03', value: 4 },
];

export function Fold() {
  const [refused, setRefused] = useState<number[]>([1]);
  const [empty, setEmpty] = useState(false);
  const [merge, setMerge] = useState<'sum' | 'max'>('sum');
  const [whenAbsent, setWhenAbsent] = useState(true);

  const contributions = empty ? [] : SECTIONS.filter((_, index) => !refused.includes(index)).map((section) => section.value);
  const readableCount = empty ? 0 : SECTIONS.length - refused.length;
  const refusedAny = refused.length > 0;
  const val = refusedAny || empty ? null : merge === 'sum'
    ? contributions.reduce((sum, value) => sum + value, 0)
    : Math.max(...contributions);
  const capacity = val === null ? null : val * 10;
  const finding = refusedAny
    ? { code: 'SC-CTX-0006', label: 'a fold has a refused section' }
    : empty
      ? { code: 'SC-CTX-0001', label: 'a computation produced no value' }
      : val === null
        ? { code: 'SC-CTX-0007', label: 'the key is absent from the context' }
        : null;

  const snippet = `ContextKey<Long> air = ContextKeys.of(
        id("air_cells"), Long.class,
        Retention.INSTANCE, Merges.${merge}(), ${whenAbsent ? '0L' : 'null'});`;

  const toggleRefused = (index: number) => {
    setRefused((current) => current.includes(index) ? current.filter((value) => value !== index) : [...current, index]);
    setEmpty(false);
  };

  const reset = () => {
    setRefused([]);
    setEmpty(false);
    setMerge('sum');
    setWhenAbsent(true);
  };

  const sections = useMemo(() => SECTIONS.map((section, index) => ({
    ...section,
    refused: refused.includes(index),
    empty,
    contribution: !empty && !refused.includes(index) ? section.value : null,
  })), [refused, empty]);

  return (
    <section className="section" id="context" data-section>
      <SectionHead
        no="05"
        kicker="Context / the fold"
        title={<>Absence is a <em>value</em>. It is not a null.</>}
        note={<>A second specimen: four slices (3/5/2/4) and a capacity of 140; the campfire's one-cell air region is the same key in miniature. Refuse a slice — or empty all four — and the key is absent for the attempt.</>}
      />

      <Reveal>
        <div className="fold-layout">
          <div className="fold-main">
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, alignItems: 'baseline', flexWrap: 'wrap' }}>
              <div className="eyebrow">air_cells / second specimen · four slices</div>
              <div className="mono muted">{readableCount} contributing / {refused.length} refused{empty ? ' / 4 empty' : ''} · merge {merge}</div>
            </div>
            <div className="section-cells">
              {sections.map((section, index) => (
                <button
                  key={section.id}
                  type="button"
                  className="section-cell"
                  data-refused={section.refused}
                  data-empty={section.empty}
                  onClick={() => toggleRefused(index)}
                  aria-pressed={section.refused || section.empty}
                >
                  <span className="s">{section.id}</span>
                  <span className="v">{section.refused || section.empty ? '∅' : section.contribution ?? '—'}</span>
                  <span className="st">{section.refused ? 'refused' : section.empty ? 'empty' : 'readable'}</span>
                </button>
              ))}
            </div>

            <div className="fold-controls">
              <Segmented
                value={merge}
                options={[{ value: 'sum', label: 'sum' }, { value: 'max', label: 'max' }]}
                onChange={setMerge}
                label="Merge combinator"
              />
              <button type="button" className="btn btn-ghost" onClick={() => { setEmpty((value) => !value); setRefused([]); }}>
                {empty ? 'restore contributions' : 'empty all sections'}
              </button>
              <button type="button" className="btn btn-ghost" onClick={() => setWhenAbsent((value) => !value)} title="Used only when every section is readable">
                whenAbsent {whenAbsent ? '0L' : 'null'}
              </button>
              <button type="button" className="btn btn-ghost" onClick={reset}>reset</button>
            </div>

            <div className="fold-result">
              <div>
                <div className="mono muted">fold(partials)</div>
                <div className={`v ${val === null ? 'refused' : ''}`}>{val === null ? '∅' : val}</div>
              </div>
              <div className="meta">
                <span>published?</span>
                <b>{val === null ? 'no' : 'INSTANCE'}</b>
                <span style={{ marginTop: 4, display: 'block' }}>air_cells</span>
                <b>{val === null ? 'absent' : `${val} cells`}</b>
              </div>
            </div>

            {finding ? (
              <div style={{ marginTop: 14, display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <span className="tag tag-accent">{finding.code}</span>
                <span className="mono muted">{finding.label}</span>
              </div>
            ) : (
              <div style={{ marginTop: 14, display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <span className="tag tag-green">no finding</span>
                <span className="mono muted">every section readable; merge produced one value</span>
              </div>
            )}

            <div className="findings-mini" style={{ paddingTop: 12, marginTop: 16 }}>
              <div className="eyebrow" style={{ marginBottom: 8 }}>The declaration in this state</div>
              <CodeBlock code={snippet} language="java" />
            </div>
          </div>

          <aside className="fold-side">
            <div className="eyebrow">Context store / second specimen</div>
            <div className="fold-side-title">air_cells</div>
            <div className="key-stack">
              <div><span className="k">type</span><span className="v">Long</span></div>
              <div><span className="k">retention</span><span className="v">INSTANCE</span></div>
              <div><span className="k">merge</span><span className="v">{merge}</span></div>
              <div><span className="k">whenAbsent</span><span className="v">{whenAbsent ? '0L' : 'null'}</span></div>
              <div><span className="k">revision</span><span className="v">{val === null ? '—' : '1'}</span></div>
            </div>
            <div className="aside-rule" />
            <div className="eyebrow">Derive / one batch</div>
            <div className="derive-card">
              <div className="derive-line"><span>capacity</span><b>{capacity === null ? '—' : capacity}</b></div>
              <div className="derive-formula">capacity = air_cells × 10</div>
              <div className={`key-state ${val === null ? '' : 'key-state-ok'}`}>
                {val === null ? 'derive skipped: the key is absent' : 'published in the same derive batch'}
              </div>
              <div className="mono muted" style={{ marginTop: 8, fontSize: 8, lineHeight: 1.5 }}>
                campfire: air = 1 → capacity 10. this specimen: 14 → 140.
              </div>
            </div>
            <div className="aside-rule" />
            <p style={{ fontSize: 10, lineHeight: 1.55, color: 'var(--muted)' }}>
              A refused section is not a zero. It makes the key absent, and the check that requires it becomes
              unreadable. If every section is readable but contributes nothing, that is <code>SC-CTX-0001</code>,
              not <code>whenAbsent</code>.
            </p>
            <div className="figure-label">
              <span>Fig. 05 — fold, refuse, repeat</span>
              <span>{refused.length} refused{empty ? ' / 4 empty' : ''}</span>
            </div>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
