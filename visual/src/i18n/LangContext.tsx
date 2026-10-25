import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { COPY, DICT, type Dict, type Lang } from './dict';

interface LangValue {
  lang: Lang;
  setLang: (lang: Lang) => void;
  t: Dict;
  c: (text: string) => string;
}

const LangContext = createContext<LangValue | null>(null);

interface LangProviderProps {
  lang: Lang;
  setLang: (lang: Lang) => void;
  children: ReactNode;
}

export function LangProvider({ lang, setLang, children }: LangProviderProps) {
  const value = useMemo<LangValue>(
    () => ({
      lang,
      setLang,
      t: DICT[lang],
      c: (text: string) => (lang === 'en' ? text : (COPY.zh[text] ?? text)),
    }),
    [lang, setLang],
  );
  return <LangContext.Provider value={value}>{children}</LangContext.Provider>;
}

export function useLang(): LangValue {
  const value = useContext(LangContext);
  if (!value) throw new Error('useLang must be used inside LangProvider');
  return value;
}

export function useT(): Dict {
  return useLang().t;
}
