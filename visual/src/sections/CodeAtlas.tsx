import { useEffect, useMemo, useState } from 'react';
import { CODE_COUNT, codes } from '../data/codes';
import { Reveal, SectionHead } from '../components/ui';
import { BundleLab } from '../components/BundleLab';
import { CODE_ZH } from '../data/codes.zh';
import { useLanguage } from '../i18n/Language';

const FAMILY_ORDER = ['SPC', 'OCC', 'EXP', 'CLM', 'BPT', 'CHK', 'DOC', 'CTX', 'ENG', 'DIA', 'RMD', 'SPI', 'SET'];

const HOST_CODES = [
  { code: 'SC-MC-0006', label: 'property mismatch', zh: 'property 不匹配' },
  { code: 'SC-MC-0007', label: 'state mismatch', zh: 'state 不匹配' },
  { code: 'SC-MC-0008', label: 'tag mismatch', zh: 'tag 不匹配' },
  { code: 'SC-MC-0009', label: 'fluid mismatch', zh: 'fluid 不匹配' },
  { code: 'SC-MC-0010', label: 'host value not captured', zh: 'host 值未捕获' },
];

function severityClass(severity: string) {
  if (severity === 'ERROR') return 'tag-accent';
  if (severity === 'WARNING') return 'tag-ochre';
  return 'tag-blue';
}

export function CodeAtlas() {
  const { zh } = useLanguage();
  const [query, setQuery] = useState('');
  const [family, setFamily] = useState('all');
  const [selected, setSelected] = useState('SC-CLM-0001');

  const ordered = useMemo(() => [...codes].sort((a, b) => {
    const familyDelta = FAMILY_ORDER.indexOf(a.family) - FAMILY_ORDER.indexOf(b.family);
    return familyDelta !== 0 ? familyDelta : a.number - b.number;
  }), []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return ordered.filter((entry) => {
      if (family !== 'all' && entry.family !== family) return false;
      if (!q) return true;
      const zhText = CODE_ZH[entry.code];
      return `${entry.code} ${entry.summary} ${entry.body} ${entry.producer} ${zhText?.summary ?? ''} ${zhText?.body ?? ''}`.toLowerCase().includes(q);
    });
  }, [query, family, ordered, zh]);

  const selectedCode = filtered.find((entry) => entry.code === selected) ?? filtered[0] ?? null;

  useEffect(() => {
    if (filtered.length > 0 && !filtered.some((entry) => entry.code === selected)) {
      setSelected(filtered[0].code);
    }
  }, [filtered, selected]);
  const familyCounts = useMemo(() => {
    const map = new Map<string, number>();
    for (const entry of codes) map.set(entry.family, (map.get(entry.family) ?? 0) + 1);
    return map;
  }, []);

  return (
    <section className="section" id="codes" data-section>
      <SectionHead
        no="06"
        kicker={zh ? 'finding / 报告层' : 'Findings / the report layer'}
        title={zh ? <>finding 是一个<em>值</em>，不是事件。</> : <>A finding is a <em>value</em>, not an event.</>}
        note={zh ? <>一张 code 表，一个 severity，一个 producer。bundle 负责排序、去重和截断；engine 从不打印，renderer 从不做决定。</> : <>One code table, one severity, one producer. The bundle sorts, dedupes and caps; the engine never prints and the renderer never decides.</>}
      />

      <Reveal>
        <BundleLab />
      </Reveal>

      <Reveal>
        <div className="code-layout">
          <div className="code-main">
            <div className="atlas-controls">
              <div className="atlas-families">
                <button type="button" className="family-pill" data-active={family === 'all'} onClick={() => setFamily('all')}>
                  all <span style={{ opacity: .5 }}>{CODE_COUNT}</span>
                </button>
                {FAMILY_ORDER.map((entry) => (
                  <button
                    key={entry}
                    type="button"
                    className="family-pill"
                    data-active={family === entry}
                    onClick={() => setFamily(entry)}
                  >
                    {entry} <span style={{ opacity: .5 }}>{familyCounts.get(entry) ?? 0}</span>
                  </button>
                ))}
              </div>
              <div style={{ minWidth: 190, flex: '1 1 190px' }}>
                <input
                  className="input"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder={zh ? '搜索 code、消息、producer…' : 'search code, message, producer…'}
                  aria-label={zh ? '搜索 finding code' : 'Search finding codes'}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, paddingTop: 9, flexWrap: 'wrap' }}>
              <div className="ledger-legend">
                <span><i style={{ background: 'var(--accent)' }} />{zh ? '错误' : 'error'}</span>
                <span><i style={{ background: 'var(--ochre)' }} />{zh ? '警告' : 'warning'}</span>
                <span><i style={{ background: 'var(--blue)' }} />{zh ? '信息' : 'info'}</span>
              </div>
              <span className="mono muted">{filtered.length} / {CODE_COUNT} {zh ? `个 code · ${FAMILY_ORDER.length} 个 kernel family` : `codes · ${FAMILY_ORDER.length} kernel families`}</span>
            </div>

            <div className="atlas-list">
              {filtered.map((entry) => (
                <button
                  key={entry.code}
                  type="button"
                  className="code-item"
                  data-active={entry.code === selected}
                  onClick={() => setSelected(entry.code)}
                >
                  <span className="code">
                    <i className="sev" data-sev={entry.severity} />
                    {entry.code}
                  </span>
                  <span className="sum">{zh ? CODE_ZH[entry.code]?.summary ?? entry.summary : entry.summary}</span>
                </button>
              ))}
            </div>
            {filtered.length === 0 ? <div className="code-empty">{zh ? '没有 code 匹配这个筛选。' : 'No code matches this filter.'}</div> : null}

            <div className="figure-label">
              <span>{zh ? '图 06 — code 图谱' : 'Fig. 06 — the code atlas'}</span>
              <span>SC-&lt;family&gt;-&lt;NNNN&gt; · {zh ? '每行一个 producer' : 'one producer per row'}</span>
            </div>
          </div>

          <aside className="code-side">
            {selectedCode ? (
              <>
            <div className="eyebrow">{zh ? '选中的 code' : 'Selected code'}</div>
            <div className="big-code" style={{ marginTop: 8 }}>{selectedCode.code}</div>
            <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
              <span className={`tag ${severityClass(selectedCode.severity)}`}>{selectedCode.severity}</span>
              <span className="tag">{selectedCode.family}</span>
            </div>
            <div className="code-anatomy">
              <span>SC</span><span>{selectedCode.family}</span><span>{String(selectedCode.number).padStart(4, '0')}</span>
            </div>
            <div className="code-detail">
              <h4>{zh ? CODE_ZH[selectedCode.code]?.summary ?? selectedCode.summary : selectedCode.summary}</h4>
              <p>{zh ? CODE_ZH[selectedCode.code]?.body ?? selectedCode.body : selectedCode.body}</p>
              <div className="producer">
                <strong>{zh ? '产生者' : 'Producer'}</strong>{" "}
                {selectedCode.producer || 'documented host boundary'}
              </div>
              <div className="producer">
                <strong>{zh ? '稳定身份' : 'Stable identity'}</strong>{" "}
                {selectedCode.code} · {zh ? '永不复用，永不重编号' : 'never reused, never renumbered'}
              </div>
              <p style={{ marginTop: 8, fontSize: 10, lineHeight: 1.5, color: 'var(--muted)' }}>
                {zh ? 'D-37 重用 SC-BPT-0005，D-38 重用 SC-SET-0007；D-39 和 D-40 不新增 code。表仍是 65 条。' : 'D-37 reuses SC-BPT-0005 and D-38 reuses SC-SET-0007; D-39 and D-40 add no code. The table stays at 65.'}
              </p>
            </div>
            <div className="aside-rule" />
            <div className="eyebrow">{zh ? 'adapter host codes / 不在 Codes.all' : 'Adapter host codes / outside Codes.all'}</div>
            <div className="key-stack" style={{ marginTop: 8 }}>
              {HOST_CODES.map((entry) => (
                <div key={entry.code}>
                  <span className="k">{entry.code}</span>
                  <span className="v">{zh ? entry.zh : entry.label}</span>
                </div>
              ))}
            </div>
            <p style={{ marginTop: 8, fontSize: 10, lineHeight: 1.5, color: 'var(--muted)' }}>
              {zh ? <>这五个是 adapter 自己的 code，通过 <code>Expectations.host</code> 的 absent/mismatch code 传入；kernel 从不 mint。缺失值是 <code>SC-MC-0010</code>（unreadable），永远不是 mismatch。</> : <>These five are the adapter's own codes, passed to <code>Expectations.host</code> as mismatch or absent codes; the kernel never mints them. A missing value is <code>SC-MC-0010</code> (unreadable), never a mismatch.</>}
            </p>
            <div className="aside-rule" />
            <div className="mono muted" style={{ lineHeight: 1.7, fontSize: 8 }}>
              {zh ? <>BUNDLE<br />ERROR 从不截断 · warning 可能省略<br />一条 SC-DIA-0001 截断尾巴<br />human 与 JSON 投影共享同一个值</> : <>THE BUNDLE<br />ERROR never capped · warnings may elide<br />one SC-DIA-0001 truncation tail<br />human and JSON projections share one value</>}
            </div>
            <div style={{ marginTop: 18 }}>
              <div className="tag tag-green"><span className="dot" /> {zh ? `${CODE_COUNT} 个已注册 · 一张表` : `${CODE_COUNT} registered · one table`}</div>
            </div>
              </>
            ) : (
              <div className="code-empty" style={{ marginTop: 0 }}>{zh ? '没有 code 匹配这个筛选。' : 'No code matches this filter.'}</div>
            )}
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
