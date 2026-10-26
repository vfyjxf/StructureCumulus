import { useCallback, useEffect, useState } from 'react';
import { NAV, type ModuleId, type SectionId } from './data/site';
import { ZH_NAV } from './data/site.zh';
import { useLanguage } from './i18n/Language';
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
  const { zh, setLang } = useLanguage();
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
    let debounce = 0;
    const onScroll = () => {
      if (!frame) frame = window.requestAnimationFrame(update);
      window.clearTimeout(debounce);
      debounce = window.setTimeout(update, 90);
    };
    const settle = () => update();
    const timers = [250, 700, 1400, 2200].map((delay) => window.setTimeout(update, delay));
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('scrollend', settle);
    window.addEventListener('resize', settle);
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('scrollend', settle);
      window.removeEventListener('resize', settle);
      window.clearTimeout(debounce);
      timers.forEach((timer) => window.clearTimeout(timer));
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
  const navLabel = (id: SectionId) => zh ? ZH_NAV[id].label : NAV.find((entry) => entry.id === id)!.label;
  const navSub = (id: SectionId) => zh ? ZH_NAV[id].sub : NAV.find((entry) => entry.id === id)!.sub;

  return (
    <div className="app">
      <a className="skip" href="#thesis">{zh ? '跳到 review' : 'Skip to the review'}</a>

      <aside className="rail" aria-label={zh ? 'Review 目录' : 'Review index'}>
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <div>SC<span>//</span><br />REVIEW</div>
        </div>
        <div className="rail-meta">
          StructureCumulus<br />
          {zh ? '设计之书 / v1.0' : 'design of record / v1.0'}<br />
          {zh ? '2026 年 10 月 26 日' : '26 oct 2026'}
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
                <span className="rail-label">{zh ? ZH_NAV[entry.id].label : entry.label}</span>
                <span className="rail-sub">{zh ? ZH_NAV[entry.id].sub : entry.sub}</span>
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
            <div className="rail-gate">{zh ? '测试' : 'tests'}<strong>97/97</strong></div>
            <div className="rail-gate">{zh ? '代码' : 'codes'}<strong>55</strong></div>
            <div className="rail-gate">{zh ? '纯度' : 'purity'}<strong>0</strong></div>
            <div className="rail-gate">{zh ? '裁决' : 'rulings'}<strong>23</strong></div>
          </div>
        </div>
      </aside>

      <div className="shell">
        <header className="topbar">
          <div className="topbar-id">
            <span className="topbar-dot" aria-hidden="true" />
            <strong>SC//REVIEW</strong>
            <span className="hide-sm">/ {activeEntry.no} {navLabel(activeEntry.id)}</span>
            <span className="hide-sm" style={{ color: 'var(--faint)' }}>— {navSub(activeEntry.id)}</span>
            <span className="topbar-active-mobile">{activeEntry.no} / {navLabel(activeEntry.id)}</span>
          </div>
          <div className="topbar-actions">
            <button type="button" className="icon-btn" onClick={() => setPalette(true)}>
              {zh ? '索引' : 'index'} <kbd>⌘K</kbd>
            </button>
            <button type="button" className="icon-btn" onClick={() => setLang(zh ? 'en' : 'zh')} aria-label={zh ? 'Switch to English' : '切换到中文'}>
              {zh ? 'EN' : '中文'}
            </button>
            <button type="button" className="icon-btn" onClick={() => setTheme(theme === 'paper' ? 'night' : 'paper')}>
              {theme === 'paper' ? (zh ? '夜间' : 'night') : (zh ? '日间' : 'paper')} <span aria-hidden="true">{theme === 'paper' ? '◐' : '◑'}</span>
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
                {zh ? '代码是生成的。' : 'The code is generated.'}<br />
                {zh ? <>真正的地基是 <em>设计</em>。</> : <>The <em>design</em> is the source.</>}
              </h3>
              <div className="footer-meta">
                StructureCumulus / {zh ? '设计 review 1.0' : 'design review 1.0'}<br />
                {zh ? '8 份设计文件 · 11 个 kernel 模块 + adapter 规范' : '8 design files · 11 kernel modules + adapter spec'}<br />
                {zh ? 'kernel 11 个 package · 55 个 code · 97 个测试' : 'kernel 11 packages · 55 codes · 97 tests'}<br />
                kernel commit 0d1218e
              </div>
            </div>
            <div className="footer-rule rule" />
            <div className="footer-small">
              <span>{zh ? '英文设计是唯一权威 · 中文是阅读层' : 'English only · no mirrored spec'}</span>
              <span>{zh ? 'Adapter M1 采集；完整 adapter 待完成' : 'Adapter M1 capture; full adapter pending'}</span>
              <span>{zh ? '如果它们冲突，以设计为准。' : 'If they disagree, the design wins.'}</span>
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
