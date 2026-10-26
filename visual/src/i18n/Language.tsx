import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Lang = 'zh' | 'en';

type LanguageValue = {
  lang: Lang;
  zh: boolean;
  setLang: (lang: Lang) => void;
};

const LanguageContext = createContext<LanguageValue | null>(null);

function initialLang(): Lang {
  try {
    const param = new URLSearchParams(window.location.search).get('lang');
    if (param === 'zh' || param === 'en') return param;
    const stored = window.localStorage.getItem('sc.review.lang');
    if (stored === 'zh' || stored === 'en') return stored;
  } catch {
    /* optional */
  }
  return 'zh';
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<Lang>(initialLang);

  useEffect(() => {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.documentElement.dataset.lang = lang;
    try {
      window.localStorage.setItem('sc.review.lang', lang);
    } catch {
      /* optional */
    }
  }, [lang]);

  const value = useMemo<LanguageValue>(() => ({ lang, zh: lang === 'zh', setLang }), [lang]);

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage(): LanguageValue {
  const value = useContext(LanguageContext);
  if (!value) throw new Error('useLanguage outside LanguageProvider');
  return value;
}
