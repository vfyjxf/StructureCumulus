import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {
  EXPLAIN,
  EXPLAIN_BY_ID,
  EXPLAIN_CATEGORIES,
  type ExplainCategory,
} from '../data/explain';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

function readExplainParam(): string | null {
  try {
    const value = new URLSearchParams(window.location.search).get('explain');
    return value && EXPLAIN_BY_ID[value] ? value : null;
  } catch {
    return null;
  }
}

function writeExplainParam(id: string | null) {
  try {
    const params = new URLSearchParams(window.location.search);
    if (id) params.set('explain', id);
    else params.delete('explain');
    const query = params.toString();
    const next = `${window.location.pathname}${query ? `?${query}` : ''}${window.location.hash}`;
    window.history.replaceState(null, '', next);
  } catch {
    /* file:// may refuse history */
  }
}

interface ExplainValue {
  openId: string | null;
  open: (id: string) => void;
  close: () => void;
}

const ExplainContext = createContext<ExplainValue | null>(null);

export function useExplain(): ExplainValue {
  const value = useContext(ExplainContext);
  if (!value) throw new Error('useExplain must be used inside ExplainProvider');
  return value;
}

export function ExplainProvider({ children }: { children: ReactNode }) {
  const [openId, setOpenId] = useState<string | null>(readExplainParam);

  const open = useCallback((id: string) => {
    if (!EXPLAIN_BY_ID[id]) return;
    setOpenId(id);
    writeExplainParam(id);
  }, []);

  const close = useCallback(() => {
    setOpenId(null);
    writeExplainParam(null);
  }, []);

  useEffect(() => {
    const sync = () => setOpenId(readExplainParam());
    window.addEventListener('popstate', sync);
    window.addEventListener('hashchange', sync);
    return () => {
      window.removeEventListener('popstate', sync);
      window.removeEventListener('hashchange', sync);
    };
  }, []);

  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const host = target.closest('[data-explain]');
      const id = host?.getAttribute('data-explain');
      if (id && EXPLAIN_BY_ID[id]) open(id);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      if (!target.hasAttribute('data-explain')) return;
      if (target.tagName === 'BUTTON' || target.tagName === 'A') return;
      const id = target.getAttribute('data-explain');
      if (!id || !EXPLAIN_BY_ID[id]) return;
      event.preventDefault();
      open(id);
    };
    document.addEventListener('click', onClick);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('click', onClick);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const value = useMemo(() => ({ openId, open, close }), [openId, open, close]);

  return (
    <ExplainContext.Provider value={value}>
      {children}
      <ExplainDrawer />
    </ExplainContext.Provider>
  );
}

/** Inline cross-reference that opens the drawer without changing the sheet. */
export function ExplainLink({ id, children, className = '' }: { id: string; children?: ReactNode; className?: string }) {
  const entry = EXPLAIN_BY_ID[id];
  const { lang } = useLang();
  if (!entry) return null;
  return (
    <button
      type="button"
      className={`np-xref ${className}`.trim()}
      data-explain={id}
      title={lang === 'zh' ? `${entry.zh} · 解释` : `Explain ${entry.term}`}
    >
      {children ?? (lang === 'zh' ? entry.zh : entry.term)}
    </button>
  );
}

export function ExplainDrawer() {
  const { openId, open, close } = useExplain();
  const t = useT();
  const { lang, c } = useLang();
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<ExplainCategory | 'All'>('All');
  const asideRef = useRef<HTMLElement | null>(null);
  const restoreRef = useRef<HTMLElement | null>(null);
  const isOpen = openId !== null;

  useEffect(() => {
    if (!isOpen) return;
    restoreRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = window.requestAnimationFrame(() => {
      const target = asideRef.current?.querySelector<HTMLElement>('[data-autofocus]');
      const first = target ?? asideRef.current?.querySelector<HTMLElement>('button, input');
      first?.focus();
    });
    return () => {
      window.cancelAnimationFrame(frame);
      restoreRef.current?.focus?.();
    };
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close();
        return;
      }
      if (event.key !== 'Tab' || !asideRef.current) return;
      const nodes = asideRef.current.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
      );
      const items = Array.from(nodes).filter((node) => node.offsetParent !== null || node === document.activeElement);
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [isOpen, close]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return EXPLAIN.filter((entry) => {
      if (category !== 'All' && entry.category !== category) return false;
      if (!q) return true;
      return [entry.term, entry.zh, entry.what, entry.why, entry.rule, entry.source, entry.id].some((field) =>
        field.toLowerCase().includes(q),
      );
    });
  }, [query, category]);

  if (!isOpen || !openId) return null;
  const entry = EXPLAIN_BY_ID[openId];
  const label = lang === 'zh' ? entry.zh : entry.term;
  const sublabel = lang === 'zh' ? entry.term : entry.zh;

  return (
    <div className="np-drawer-root">
      <div className="np-drawer-scrim" onClick={close} aria-hidden="true" />
      <aside
        className="np-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="np-explaintitle"
        ref={asideRef}
        tabIndex={-1}
      >
        <header className="np-drawer-hd">
          <span className="np-drawer-kicker">
            {t.explain.kicker} · {String(EXPLAIN.findIndex((item) => item.id === entry.id) + 1).padStart(2, '0')}/
            {String(EXPLAIN.length).padStart(2, '0')}
          </span>
          <span className="np-drawer-cat">{t.explain.categories[entry.category]}</span>
          <button type="button" className="np-drawer-close" onClick={close} aria-label={t.explain.close} title={t.explain.close}>
            ✕
          </button>
        </header>

        <div className="np-drawer-title">
          <h2 id="np-explaintitle">{label}</h2>
          <span className="np-drawer-sub">{sublabel}</span>
        </div>

        <div className="np-drawer-search">
          <label className="field">
            <span className="field-label">{t.explain.search}</span>
            <input
              className="input input--wide"
              type="search"
              value={query}
              placeholder={t.explain.searchPlaceholder}
              onChange={(event) => setQuery(event.target.value)}
              aria-label={t.explain.searchAria}
              data-autofocus
            />
          </label>
          <div className="np-drawer-filters" role="group" aria-label={t.explain.filter}>
            <button
              type="button"
              className="btn btn--sm"
              aria-pressed={category === 'All'}
              onClick={() => setCategory('All')}
            >
              {t.explain.all}
            </button>
            {EXPLAIN_CATEGORIES.map((item) => (
              <button
                key={item}
                type="button"
                className="btn btn--sm"
                aria-pressed={category === item}
                onClick={() => setCategory(item)}
              >
                {t.explain.categories[item]}
              </button>
            ))}
          </div>
        </div>

        <div className="np-drawer-scroll">
          <dl className="np-explain-dl">
            <dt>{t.explain.what}</dt>
            <dd>{c(entry.what)}</dd>
            <dt>{t.explain.why}</dt>
            <dd>{c(entry.why)}</dd>
            <dt>{t.explain.rule}</dt>
            <dd className="np-explain-rule">{c(entry.rule)}</dd>
            <dt>{t.explain.source}</dt>
            <dd className="mono np-explain-src">{entry.source}</dd>
          </dl>

          {entry.related.length ? (
            <div className="np-explain-related">
              <span className="micro">{t.explain.related}</span>
              <div className="row">
                {entry.related.map((id) => {
                  const rel = EXPLAIN_BY_ID[id];
                  if (!rel) return null;
                  return (
                    <button
                      key={id}
                      type="button"
                      className="np-scenario"
                      aria-pressed={id === entry.id}
                      onClick={() => open(id)}
                    >
                      <span className="np-scenario-id np-num">{rel.category.slice(0, 3).toUpperCase()}</span>
                      <span className="np-scenario-name">{lang === 'zh' ? rel.zh : rel.term}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          ) : null}

          <div className="np-drawer-index">
            <div className="np-scenarios-hd">
              <span className="np-scenarios-label">{t.explain.index}</span>
              <span className="np-scenarios-count">
                {fmt(t.explain.count, { shown: filtered.length, total: EXPLAIN.length })}
              </span>
            </div>
            {filtered.length === 0 ? (
              <p className="note" style={{ marginTop: 6 }}>
                {t.explain.empty}
              </p>
            ) : (
              <ul className="np-explain-list">
                {filtered.map((item) => (
                  <li key={item.id}>
                    <button
                      type="button"
                      className="np-explain-item"
                      aria-pressed={item.id === entry.id}
                      onClick={() => open(item.id)}
                    >
                      <span className="np-explain-item-term">{lang === 'zh' ? item.zh : item.term}</span>
                      <span className="np-explain-item-sub mono">{lang === 'zh' ? item.term : item.zh}</span>
                      <span className="np-explain-item-cat">{t.explain.categories[item.category]}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <p className="np-drawer-hint">{t.explain.hint}</p>
        </div>
      </aside>
    </div>
  );
}
