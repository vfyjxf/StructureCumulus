import { useMemo, useState } from 'react';
import { CodeBlock, Reveal, SectionHead, Segmented } from '../components/ui';

const SECTIONS = [
  { id: 'S-00', value: 3 },
  { id: 'S-01', value: 5 },
  { id: 'S-02', value: 2 },
  { id: 'S-03', value: 4 },
];

const SNIPPET = `ContextKey<Long> air = ContextKeys.of(
        id("air_cells"), Long.class,
        Retention.INSTANCE, Merges.sum(), null);`;

export function Fold() {
  const [refused, setRefused] = useState<number[]>([]);
  const [empty, setEmpty] = useState(false);
  const [merge, setMerge] = useState<'sum' | 'max'>('sum');
  const [whenAbsent, setWhenAbsent] = useState(true);

  const contributions = empty ? [] : SECTIONS.filter((_, index) => !refused.includes(index)).map((section) => section.value);
  const readableCount = SECTIONS.length - refused.length;
  const refusedAny = refused.length > 0;
  const val = refusedAny ? null : contributions.length === 0 ? (whenAbsent ? 0 : null) : merge === 'sum'
    ? contributions.reduce((sum, value) => sum + value, 0)
    : Math.max(...contributions);
  const capacity = val === null ? null : val * 10;
  const finding = refusedAny
    ? { code: 'SC-CTX-0006', label: 'a fold has a refused section' }
    : val === null
      ? { code: 'SC-CTX-0007', label: 'the key is absent from the context' }
      : null;

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
    contribution: !empty && !refused.includes(index) ? section.value : null,
  })), [refused, empty]);

  return (
    <section className="section" id="context" data-section>
      <SectionHead
        no="05"
        kicker="Context / the fold"
        title={<>Absence is a <em>value</em>. It is not a null.</>}
        note={<>A section computation contributes a partial; the merge folds it. Refuse a section and the key is absent for the attempt — one located code, no value patch, no second publication.</>}
      />

      <Reveal>
        <div className="fold-layout">
          <div className="fold-main">
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, alignItems: 'baseline', flexWrap: 'wrap' }}>
              <div className="eyebrow">air_cells / one section per slice</div>
              <div className="mono muted">{readableCount} readable / {refused.length} refused · merge {merge}</div>
            </div>
            <div className="section-cells">
              {sections.map((section, index) => (
                <button
                  key={section.id}
                  type="button"
                  className="section-cell"
                  data-refused={section.refused}
                  onClick={() => toggleRefused(index)}
                  aria-pressed={section.refused}
                >
                  <span className="s">{section.id}</span>
                  <span className="v">{section.refused ? '∅' : section.contribution ?? '—'}</span>
                  <span className="st">{section.refused ? 'refused' : section.contribution === null ? 'empty' : 'readable'}</span>
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
              <button type="button" className="btn btn-ghost" onClick={() => setEmpty((value) => !value)}>
                {empty ? 'restore contributions' : 'empty all sections'}
              </button>
              <button type="button" className="btn btn-ghost" onClick={() => setWhenAbsent((value) => !value)}>
                whenAbsent {whenAbsent ? 'on' : 'off'}
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
              <div className="eyebrow" style={{ marginBottom: 8 }}>The code path</div>
              <CodeBlock code={SNIPPET} language="java" />
            </div>
          </div>

          <aside className="fold-side">
            <div className="eyebrow">Context store / one instance</div>
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
            </div>
            <div className="aside-rule" />
            <p style={{ fontSize: 10, lineHeight: 1.55, color: 'var(--muted)' }}>
              A refused section is not a zero. It makes the key absent, and the check that requires it becomes
              unreadable. A <code>Missing</code> section contribution is a different code: <code>SC-CTX-0001</code>.
            </p>
            <div className="figure-label">
              <span>Fig. 05 — fold, refuse, repeat</span>
              <span>{refused.length} refused</span>
            </div>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
