import { useEffect, useState } from 'react';
import { PAGES } from './data/pages';
import type { PageId } from './data/types';
import { HeaderBlock } from './components/HeaderBlock';
import { FooterBlock } from './components/FooterBlock';
import { TabNav } from './components/TabNav';
import { Segmented } from './components/Controls';
import { PatternDefs } from './components/Diagram';
import { ExplainProvider, useExplain } from './components/ExplainDrawer';
import { LangProvider, useLang, useT } from './i18n/LangContext';
import type { Lang } from './i18n/dict';
import { OverviewView } from './views/Overview';
import { MissionView } from './views/Mission';
import { SpaceView } from './views/Space';
import { AuthoringView } from './views/Authoring';
import { AnatomyView } from './views/Anatomy';
import { VoxelView } from './views/Voxel';
import { SliceView } from './views/Slice';
import { PredicatesView } from './views/Predicates';
import { ContextView } from './views/ContextView';
import { PipelineView } from './views/Pipeline';
import { ClaimsView } from './views/Claims';
import { ResultsView } from './views/Results';
import { GlossaryView } from './views/Glossary';

type Palette = 'print' | 'crt';

const VIEWS: Record<PageId, () => React.JSX.Element> = {
  overview: OverviewView,
  mission: MissionView,
  space: SpaceView,
  authoring: AuthoringView,
  anatomy: AnatomyView,
  voxel: VoxelView,
  slice: SliceView,
  predicates: PredicatesView,
  context: ContextView,
  pipeline: PipelineView,
  claims: ClaimsView,
  results: ResultsView,
  glossary: GlossaryView,
};

const isLang = (value: string | null): value is Lang => value === 'zh' || value === 'en';

const pageFromLocation = (): PageId => {
  try {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('page') ?? window.location.hash.replace('#', '');
    if (PAGES.some((page) => page.id === id)) return id as PageId;
  } catch {
    /* file:// has no search params */
  }
  return 'overview';
};

const initialLang = (): Lang => {
  try {
    const param = new URLSearchParams(window.location.search).get('lang');
    if (isLang(param)) return param;
    const stored = window.localStorage.getItem('structurecumulus.lang');
    if (isLang(stored)) return stored;
  } catch {
    /* storage is optional */
  }
  return 'zh';
};

export default function App() {
  const [palette, setPalette] = useState<Palette>('print');
  const [active, setActive] = useState<PageId>(pageFromLocation);
  const [lang, setLangState] = useState<Lang>(initialLang);

  useEffect(() => {
    try {
      const param = new URLSearchParams(window.location.search).get('theme');
      const stored = window.localStorage.getItem('structurecumulus.palette');
      if (param === 'crt' || param === 'print') setPalette(param);
      else if (stored === 'crt' || stored === 'print') setPalette(stored);
    } catch {
      /* storage is optional */
    }
  }, []);

  useEffect(() => {
    const syncFromLocation = () => {
      const params = new URLSearchParams(window.location.search);
      const param = params.get('page');
      const id = param ?? window.location.hash.replace('#', '');
      if (PAGES.some((p) => p.id === id)) setActive(id as PageId);
      const langParam = params.get('lang');
      if (isLang(langParam)) setLangState(langParam);
    };
    syncFromLocation();
    window.addEventListener('hashchange', syncFromLocation);
    window.addEventListener('popstate', syncFromLocation);
    return () => {
      window.removeEventListener('hashchange', syncFromLocation);
      window.removeEventListener('popstate', syncFromLocation);
    };
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = palette === 'crt' ? 'crt' : 'paper';
    try {
      window.localStorage.setItem('structurecumulus.palette', palette);
    } catch {
      /* storage is optional */
    }
  }, [palette]);

  useEffect(() => {
    document.documentElement.dataset.lang = lang;
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    try {
      window.localStorage.setItem('structurecumulus.lang', lang);
    } catch {
      /* storage is optional */
    }
  }, [lang]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [active]);

  const writeUrl = (page: PageId, next: Lang, keepPlayback = false) => {
    try {
      const params = new URLSearchParams(window.location.search);
      params.set('page', page);
      params.set('lang', next);
      if (!keepPlayback) {
        params.delete('scenario');
        params.delete('step');
      }
      const query = params.toString();
      window.history.replaceState(null, '', `?${query}`);
    } catch {
      /* file:// may refuse history */
    }
  };

  const selectPage = (id: PageId) => {
    setActive(id);
    writeUrl(id, lang, false);
  };

  const setLang = (next: Lang) => {
    setLangState(next);
    writeUrl(active, next, true);
  };

  const page = PAGES.find((p) => p.id === active) ?? PAGES[0];
  const View = VIEWS[page.id];

  return (
    <LangProvider lang={lang} setLang={setLang}>
      <ExplainProvider>
        <Suite palette={palette} setPalette={setPalette} active={active} page={page} View={View} selectPage={selectPage} />
      </ExplainProvider>
    </LangProvider>
  );
}

interface SuiteProps {
  palette: Palette;
  setPalette: (palette: Palette) => void;
  active: PageId;
  page: (typeof PAGES)[number];
  View: () => React.JSX.Element;
  selectPage: (id: PageId) => void;
}

function Suite({ palette, setPalette, active, page, View, selectPage }: SuiteProps) {
  const t = useT();
  const { lang, setLang } = useLang();
  const { open } = useExplain();

  return (
    <>
      {palette === 'crt' ? <div className="np-scanline" aria-hidden="true" /> : null}
      <PatternDefs />
      <div className="sheet-frame">
        <span className="reg-mark reg-mark--tl" aria-hidden="true" />
        <span className="reg-mark reg-mark--tr" aria-hidden="true" />
        <span className="reg-mark reg-mark--bl" aria-hidden="true" />
        <span className="reg-mark reg-mark--br" aria-hidden="true" />
        <div className="zones-top" aria-hidden="true">
          {Array.from({ length: 8 }, (_, i) => (
            <span key={i}>{i + 1}</span>
          ))}
        </div>
        <div className="zones-left" aria-hidden="true">
          {['A', 'B', 'C', 'D', 'E', 'F'].map((letter) => (
            <span key={letter}>{letter}</span>
          ))}
        </div>

        <div className="np-sheet">
          <div className="masthead">
            <div className="brand">
              <span className="brand-name">StructureCumulus</span>
              <span className="brand-sub">{t.chrome.brandSub}</span>
            </div>
            <div className="polarity">
              <button
                type="button"
                className="np-explain-open"
                onClick={() => open('blueprint')}
                title={t.explain.openTitle}
              >
                <span className="np-explain-open-glyph" aria-hidden="true">
                  ?
                </span>
                {t.explain.open}
              </button>
              <span className="polarity-label">{t.chrome.language}</span>
              <Segmented
                label={t.chrome.language}
                hideLabel
                ariaLabel={t.chrome.langAria}
                options={[
                  { value: 'zh', label: t.chrome.zhOption, title: t.chrome.zhTitle },
                  { value: 'en', label: t.chrome.enOption, title: t.chrome.enTitle },
                ]}
                value={lang}
                onChange={(value) => setLang(value as Lang)}
              />
              <span className="polarity-label">{t.chrome.polarity}</span>
              <Segmented
                label={t.chrome.palette}
                hideLabel
                ariaLabel={t.chrome.paletteAria}
                options={[
                  { value: 'print', label: t.chrome.print, title: t.chrome.printTitle },
                  { value: 'crt', label: t.chrome.crt, title: t.chrome.crtTitle },
                ]}
                value={palette}
                onChange={(value) => setPalette(value as Palette)}
              />
            </div>
          </div>

          <HeaderBlock page={page} />
          <TabNav pages={PAGES} active={active} onChange={selectPage} />

          <main
            id={`panel-${page.id}`}
            role="tabpanel"
            aria-labelledby={`tab-${page.id}`}
            tabIndex={0}
            key={page.id}
          >
            <View />
          </main>

          <FooterBlock
            sources={page.sources}
            pageNo={page.no}
            pageCount={PAGES.length}
            date={page.date}
          />
        </div>
      </div>
    </>
  );
}
