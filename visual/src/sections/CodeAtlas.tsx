import { useMemo, useState } from 'react';
import { CODE_COUNT, codes } from '../data/codes';
import { Reveal, SectionHead } from '../components/ui';

const FAMILY_ORDER = ['SPC', 'OCC', 'EXP', 'CLM', 'BPT', 'CHK', 'DOC', 'CTX', 'ENG', 'DIA', 'RMD', 'SPI'];

function severityClass(severity: string) {
  if (severity === 'ERROR') return 'tag-accent';
  if (severity === 'WARNING') return 'tag-ochre';
  return 'tag-blue';
}

export function CodeAtlas() {
  const [query, setQuery] = useState('');
  const [family, setFamily] = useState('all');
  const [selected, setSelected] = useState('SC-CLM-0001');

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return codes.filter((entry) => {
      if (family !== 'all' && entry.family !== family) return false;
      if (!q) return true;
      return `${entry.code} ${entry.summary} ${entry.body} ${entry.producer}`.toLowerCase().includes(q);
    });
  }, [query, family]);

  const selectedCode = codes.find((entry) => entry.code === selected) ?? codes[0];
  const familyCounts = useMemo(() => {
    const map = new Map<string, number>();
    for (const entry of codes) map.set(entry.family, (map.get(entry.family) ?? 0) + 1);
    return map;
  }, []);

  return (
    <section className="section" id="codes" data-section>
      <SectionHead
        no="06"
        kicker="Findings / the report layer"
        title={<>A finding is a <em>value</em>, not an event.</>}
        note={<>One code table, one severity, one producer. The bundle sorts, dedupes and caps; the engine never prints and the renderer never decides.</>}
      />

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
                  placeholder="search code, message, producer…"
                  aria-label="Search finding codes"
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, paddingTop: 9, flexWrap: 'wrap' }}>
              <div className="ledger-legend">
                <span><i style={{ background: 'var(--accent)' }} />error</span>
                <span><i style={{ background: 'var(--ochre)' }} />warning</span>
                <span><i style={{ background: 'var(--blue)' }} />info</span>
              </div>
              <span className="mono muted">{filtered.length} / {CODE_COUNT} codes · 12 families + host</span>
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
                  <span className="sum">{entry.summary}</span>
                </button>
              ))}
            </div>
            {filtered.length === 0 ? <div className="code-empty">No code matches this filter.</div> : null}

            <div className="figure-label">
              <span>Fig. 06 — the code atlas</span>
              <span>SC-&lt;family&gt;-&lt;NNNN&gt; · one producer per row</span>
            </div>
          </div>

          <aside className="code-side">
            <div className="eyebrow">Selected code</div>
            <div className="big-code" style={{ marginTop: 8 }}>{selectedCode.code}</div>
            <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
              <span className={`tag ${severityClass(selectedCode.severity)}`}>{selectedCode.severity}</span>
              <span className="tag">{selectedCode.family}</span>
            </div>
            <div className="code-detail">
              <h4>{selectedCode.summary}</h4>
              <p>{selectedCode.body}</p>
              <div className="producer">
                <strong>Producer</strong>
                {selectedCode.producer || 'documented host boundary'}
              </div>
              <div className="producer">
                <strong>Stable identity</strong>
                {selectedCode.code} · never reused, never renumbered
              </div>
            </div>
            <div className="aside-rule" />
            <div className="mono muted" style={{ lineHeight: 1.7, fontSize: 8 }}>
              THE BUNDLE<br />
              ERROR never capped · warnings may elide<br />
              one SC-DIA-0001 truncation tail<br />
              human and JSON projections share one value
            </div>
            <div style={{ marginTop: 18 }}>
              <div className="tag tag-green"><span className="dot" /> 55 registered · 0 dead</div>
            </div>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
