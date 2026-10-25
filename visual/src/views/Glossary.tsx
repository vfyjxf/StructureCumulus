import { useMemo, useState } from 'react';
import { Panel } from '../components/Panel';
import { WhyNote } from '../components/WhyNote';
import { GLOSSARY, GLOSSARY_GROUPS, type GlossaryGroup } from '../data/glossary';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

export function GlossaryView() {
  const [query, setQuery] = useState('');
  const [group, setGroup] = useState<GlossaryGroup | 'All'>('All');

  const t = useT();
  const { lang, c } = useLang();

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return GLOSSARY.filter((entry) => {
      if (group !== 'All' && entry.group !== group) return false;
      if (!q) return true;
      return [entry.term, entry.zh, entry.what, entry.why, entry.group].some((field) =>
        field.toLowerCase().includes(q),
      );
    }).sort((a, b) => GLOSSARY_GROUPS.indexOf(a.group) - GLOSSARY_GROUPS.indexOf(b.group));
  }, [query, group]);

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.glossary.why}</WhyNote>
      </div>

      <div className="c12">
        <Panel
          title={t.glossary.termIndex}
          count={fmt(t.glossary.termIndexCount, { shown: filtered.length, total: GLOSSARY.length })}
        >
          <div className="searchbar">
            <label className="field" style={{ flex: '1 1 280px' }}>
              <span className="field-label">{t.glossary.searchTerms}</span>
              <input
                className="input input--wide"
                type="search"
                value={query}
                placeholder={t.glossary.searchPlaceholder}
                onChange={(event) => setQuery(event.target.value)}
                aria-label={t.glossary.searchAria}
              />
            </label>
            <div className="row" role="group" aria-label={t.glossary.groupAria}>
              <button
                type="button"
                className="btn btn--sm"
                aria-pressed={group === 'All'}
                onClick={() => setGroup('All')}
              >
                {t.glossary.all}
              </button>
              {GLOSSARY_GROUPS.map((item) => (
                <button
                  key={item}
                  type="button"
                  className="btn btn--sm"
                  aria-pressed={group === item}
                  onClick={() => setGroup(item)}
                >
                  {c(item)}
                </button>
              ))}
            </div>
          </div>
          <div className="row-between">
            <span className="micro">{t.glossary.indexNote}</span>
            <button
              type="button"
              className="btn btn--sm"
              onClick={() => {
                setQuery('');
                setGroup('All');
              }}
            >
              {t.glossary.clear}
            </button>
          </div>
        </Panel>
      </div>

      <div className="c12">
        {filtered.length === 0 ? (
          <div className="empty-state">{t.glossary.empty}</div>
        ) : (
          <div className="gloss-grid">
            {filtered.map((entry) => (
              <article className="gloss-card" key={entry.id}>
                <div className="gloss-hd">
                  {lang === 'zh' ? (
                    <>
                      <span className="gloss-term">{entry.zh}</span>
                      <span className="gloss-zh">{entry.term}</span>
                    </>
                  ) : (
                    <>
                      <span className="gloss-term">{entry.term}</span>
                      <span className="gloss-zh">{entry.zh}</span>
                    </>
                  )}
                </div>
                <span className="gloss-group">{c(entry.group)}</span>
                <p className="gloss-body">{c(entry.what)}</p>
                <div>
                  <div className="micro">{t.glossary.whyCard}</div>
                  <p className="gloss-why" style={{ borderTop: 0, paddingTop: 0, marginTop: 2 }}>
                    {c(entry.why)}
                  </p>
                </div>
                <span className="gloss-src">{entry.source}</span>
              </article>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
