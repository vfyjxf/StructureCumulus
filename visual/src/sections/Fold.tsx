import { useMemo, useState } from 'react';
import { CodeBlock, Reveal, SectionHead, Segmented } from '../components/ui';
import { useLanguage } from '../i18n/Language';

const SECTIONS = [
  { id: 'S-00', value: 3 },
  { id: 'S-01', value: 5 },
  { id: 'S-02', value: 2 },
  { id: 'S-03', value: 4 },
];

export function Fold() {
  const { zh } = useLanguage();
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
        Retention.instance, Merges.${merge}(), ${whenAbsent ? '0L' : 'null'});`;

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
        kicker={zh ? 'context / 折叠' : 'Context / the fold'}
        title={zh ? <>缺席是一个<em>值</em>。它不是 null。</> : <>Absence is a <em>value</em>. It is not a null.</>}
        note={zh ? <>第二个样本：四个 slice（3/5/2/4），capacity 140；营火的单 cell air 区是同一个 key 的微缩版。拒绝一个 slice——或让四个都 empty——这个 key 对本次 attempt 就是 absent。</> : <>A second specimen: four slices (3/5/2/4) and a capacity of 140; the campfire's one-cell air region is the same key in miniature. Refuse a slice — or empty all four — and the key is absent for the attempt.</>}
      />

      <Reveal>
        <div className="fold-layout">
          <div className="fold-main">
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, alignItems: 'baseline', flexWrap: 'wrap' }}>
              <div className="eyebrow">air_cells / {zh ? '第二个样本 · 四个 slice' : 'second specimen · four slices'}</div>
              <div className="mono muted">{readableCount} {zh ? '个贡献' : 'contributing'} / {refused.length} {zh ? '个拒绝' : 'refused'}{empty ? (zh ? ' / 4 个 empty' : ' / 4 empty') : ''} · merge {merge}</div>
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
                  <span className="st">{section.refused ? (zh ? '拒绝' : 'refused') : section.empty ? (zh ? '空' : 'empty') : (zh ? '可读' : 'readable')}</span>
                </button>
              ))}
            </div>

            <div className="fold-controls">
              <Segmented
                value={merge}
                options={[{ value: 'sum', label: 'sum' }, { value: 'max', label: 'max' }]}
                onChange={setMerge}
                label={zh ? 'merge 组合器' : 'Merge combinator'}
              />
              <button type="button" className="btn btn-ghost" onClick={() => { setEmpty((value) => !value); setRefused([]); }}>
                {empty ? (zh ? '恢复贡献' : 'restore contributions') : (zh ? '清空所有 section' : 'empty all sections')}
              </button>
              <button type="button" className="btn btn-ghost" onClick={() => setWhenAbsent((value) => !value)} title="Used only when every section is readable">
                {zh ? 'whenAbsent' : 'whenAbsent'} {whenAbsent ? '0L' : 'null'}
              </button>
              <button type="button" className="btn btn-ghost" onClick={reset}>{zh ? '重置' : 'reset'}</button>
            </div>

            <div className="fold-result">
              <div>
                <div className="mono muted">{zh ? 'fold(partials)' : 'fold(partials)'}</div>
                <div className={`v ${val === null ? 'refused' : ''}`}>{val === null ? '∅' : val}</div>
              </div>
              <div className="meta">
                <span>{zh ? '已发布？' : 'published?'}</span>
                <b>{val === null ? 'no' : 'INSTANCE'}</b>
                <span style={{ marginTop: 4, display: 'block' }}>air_cells</span>
                <b>{val === null ? (zh ? 'absent' : 'absent') : `${val} cells`}</b>
              </div>
            </div>

            {finding ? (
              <div style={{ marginTop: 14, display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <span className="tag tag-accent">{finding.code}</span>
                <span className="mono muted">{zh ? (finding.code === 'SC-CTX-0006' ? 'fold 中有被拒绝的 section' : finding.code === 'SC-CTX-0001' ? 'computation 没有产生值' : '这个 key 从 context 中缺席') : finding.label}</span>
              </div>
            ) : (
              <div style={{ marginTop: 14, display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <span className="tag tag-green">{zh ? '没有 finding' : 'no finding'}</span>
                <span className="mono muted">{zh ? '每个 section 都可读；merge 产生了一个值' : 'every section readable; merge produced one value'}</span>
              </div>
            )}

            <div className="findings-mini" style={{ paddingTop: 12, marginTop: 16 }}>
              <div className="eyebrow" style={{ marginBottom: 8 }}>{zh ? '这个状态下的声明' : 'The declaration in this state'}</div>
              <CodeBlock code={snippet} language="java" />
            </div>
          </div>

          <aside className="fold-side">
            <div className="eyebrow">{zh ? 'context store / 第二个样本' : 'Context store / second specimen'}</div>
            <div className="fold-side-title">air_cells</div>
            <div className="key-stack">
              <div><span className="k">type</span><span className="v">Long</span></div>
              <div><span className="k">retention</span><span className="v">INSTANCE</span></div>
              <div><span className="k">merge</span><span className="v">{merge}</span></div>
              <div><span className="k">whenAbsent</span><span className="v">{whenAbsent ? '0L' : 'null'}</span></div>
              <div><span className="k">revision</span><span className="v">{val === null ? '—' : '1'}</span></div>
            </div>
            <div className="aside-rule" />
            <div className="eyebrow">{zh ? 'derive / 一个 batch' : 'Derive / one batch'}</div>
            <div className="derive-card">
              <div className="derive-line"><span>capacity</span><b>{capacity === null ? '—' : capacity}</b></div>
              <div className="derive-formula">capacity = air_cells × 10</div>
              <div className={`key-state ${val === null ? '' : 'key-state-ok'}`}>
                {val === null ? (zh ? 'derive 跳过：key 缺席' : 'derive skipped: the key is absent') : (zh ? '在同一个 derive batch 中发布' : 'published in the same derive batch')}
              </div>
              <div className="mono muted" style={{ marginTop: 8, fontSize: 8, lineHeight: 1.5 }}>
                {zh ? '营火：air = 1 → capacity 10。这个样本：14 → 140。' : 'campfire: air = 1 → capacity 10. this specimen: 14 → 140.'}
              </div>
            </div>
            <div className="aside-rule" />
            <p style={{ fontSize: 10, lineHeight: 1.55, color: 'var(--muted)' }}>
              {zh ? <>被拒绝的 section 不是 0。它让 key 缺席，依赖它的 check 变成 unreadable。如果每个 section 都可读但没有贡献，那是 <code>SC-CTX-0001</code>，不是 <code>whenAbsent</code>。</> : <>A refused section is not a zero. It makes the key absent, and the check that requires it becomes unreadable. If every section is readable but contributes nothing, that is <code>SC-CTX-0001</code>, not <code>whenAbsent</code>.</>}
            </p>
            <div className="figure-label">
              <span>{zh ? '图 05 — fold、拒绝、重复' : 'Fig. 05 — fold, refuse, repeat'}</span>
              <span>{refused.length} {zh ? '个拒绝' : 'refused'}{empty ? (zh ? ' / 4 个 empty' : ' / 4 empty') : ''}</span>
            </div>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
