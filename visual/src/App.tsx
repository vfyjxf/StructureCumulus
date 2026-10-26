import { useCallback, useEffect, useState } from 'react';
import { NAV, type ModuleId, type SectionId } from './data/site';
import { Hero } from './components/Hero';
import { CommandPalette } from './components/CommandPalette';
import { Thesis } from './sections/Thesis';
import { Lattice } from './sections/Lattice';
import { ObjectModel } from './sections/ObjectModel';
import { Workbench } from './sections/Workbench';
import { Ledger } from './sections/Ledger';
import { Fold } from './sections/Fold';
import { CodeAtlas } from './sections/CodeAtlas';
import { Proof } from './sections/Proof';

type Theme = 'paper' | 'night';

const isSection = (value: string | null): value is SectionId => NAV.some((entry) => entry.id === value);

export default function App() {
  const [active, setActive] = useState<SectionId>('thesis');
  const [theme, setTheme] = useState<Theme>('paper');
  const [palette, setPalette] = useState(false);
  const [moduleFocus, setModuleFocus] = useState<ModuleId | null>(null);

  useEffect(() => {
    try {
      const param = new URLSearchParams(window.location.search).get('theme');
      const stored = window.localStorage.getItem('sc.review.theme');
      if (param === 'night' || param === 'paper') setTheme(param);
      else if (stored === 'night' || stored === 'paper') setTheme(stored);
    } catch {
      /* optional */
    }
    const hash = window.location.hash.replace('#', '');
    if (isSection(hash)) {
      window.setTimeout(() => document.getElementById(hash)?.scrollIntoView({ block: 'start' }), 10);
    }
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      window.localStorage.setItem('sc.review.theme', theme);
    } catch {
      /* optional */
    }
  }, [theme]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPalette((open) => !open);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      const offset = 120;
      let current: SectionId = 'thesis';
      for (const entry of NAV) {
        const node = document.getElementById(entry.id);
        if (node && node.getBoundingClientRect().top <= offset) current = entry.id;
      }
      setActive(current);
    };
    const onScroll = () => {
      if (!frame) frame = window.requestAnimationFrame(update);
    };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  const go = useCallback((id: SectionId) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    try {
      window.history.replaceState(null, '', `#${id}`);
    } catch {
      /* optional */
    }
  }, []);

  const goModule = useCallback((id: ModuleId) => {
    setModuleFocus(id);
    go('lattice');
  }, [go]);

  const activeIndex = NAV.findIndex((entry) => entry.id === active);
  const progress = ((activeIndex + 1) / NAV.length) * 100;
  const activeEntry = NAV[activeIndex] ?? NAV[0];

  return (
    <div className="app">
      <a className="skip" href="#thesis">Skip to the review</a>

      <aside className="rail" aria-label="Review index">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <div>SC<span>//</span><br />REVIEW</div>
        </div>
        <div className="rail-meta">
          StructureCumulus<br />
          design of record / v1.0<br />
          26 oct 2026
        </div>
        <nav className="rail-nav">
          {NAV.map((entry) => (
            <button
              key={entry.id}
              type="button"
              className="rail-link"
              data-active={entry.id === active}
              onClick={() => go(entry.id)}
            >
              <span className="rail-no">{entry.no}</span>
              <span>
                <span className="rail-label">{entry.label}</span>
                <span className="rail-sub">{entry.sub}</span>
              </span>
            </button>
          ))}
        </nav>
        <div className="rail-progress">
          <div className="rail-progress-head">
            <span>{activeEntry.no} / 07</span>
            <span>{Math.round(progress)}%</span>
          </div>
          <div className="rail-bar"><span style={{ width: `${progress}%` }} /></div>
          <div className="rail-gates">
            <div className="rail-gate">tests<strong>94/94</strong></div>
            <div className="rail-gate">codes<strong>55</strong></div>
            <div className="rail-gate">purity<strong>0</strong></div>
            <div className="rail-gate">gates<strong>4/4</strong></div>
          </div>
        </div>
      </aside>

      <div className="shell">
        <header className="topbar">
          <div className="topbar-id">
            <span className="topbar-dot" aria-hidden="true" />
            <strong>SC//REVIEW</strong>
            <span className="hide-sm">/ {activeEntry.no} {activeEntry.label}</span>
            <span className="hide-sm" style={{ color: 'var(--faint)' }}>— {activeEntry.sub}</span>
          </div>
          <div className="topbar-actions">
            <button type="button" className="icon-btn" onClick={() => setPalette(true)}>
              index <kbd>⌘K</kbd>
            </button>
            <button type="button" className="icon-btn" onClick={() => setTheme(theme === 'paper' ? 'night' : 'paper')}>
              {theme === 'paper' ? 'night' : 'paper'} <span aria-hidden="true">{theme === 'paper' ? '◐' : '◑'}</span>
            </button>
          </div>
        </header>

        <main className="main">
          <Hero onNavigate={go} />
          <Thesis />
          <Lattice focus={moduleFocus} />
          <ObjectModel />
          <Workbench />
          <Ledger />
          <Fold />
          <CodeAtlas />
          <Proof />

          <footer className="footer">
            <div className="footer-rule rule-strong" />
            <div className="footer-grid">
              <h3>
                The code is generated.<br />
                The <em>design</em> is the source.
              </h3>
              <div className="footer-meta">
                StructureCumulus / design review 1.0<br />
                8 design files · 12 module files<br />
                kernel 11 packages · 55 codes · 94 tests<br />
                commit 0d1218e
              </div>
            </div>
            <div className="footer-rule rule" />
            <div className="footer-small">
              <span>English only · no mirrored spec</span>
              <span>Adapter specified, not implemented</span>
              <span>If they disagree, the design wins.</span>
            </div>
          </footer>
        </main>
      </div>

      <CommandPalette
        open={palette}
        onClose={() => setPalette(false)}
        onSection={go}
        onModule={goModule}
      />
    </div>
  );
}
