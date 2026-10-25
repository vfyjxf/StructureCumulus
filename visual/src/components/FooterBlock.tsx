import { useT } from '../i18n/LangContext';

interface FooterBlockProps {
  sources: string[];
  pageNo: string;
  pageCount: number;
  date: string;
}

export function FooterBlock({ sources, pageNo, pageCount, date }: FooterBlockProps) {
  const t = useT();
  return (
    <div>
      <div className="footer-src" aria-label={t.chrome.sourcesAria}>
        <span className="strong ink">{t.chrome.sources}</span>
        {sources.map((source) => (
          <span key={source}>{source}</span>
        ))}
        <span className="grow" />
        <span>{t.chrome.designContract}</span>
      </div>
      <footer className="footer-bar">
        <span>{t.chrome.build}</span>
        <span className="do-not-scale">{t.chrome.doNotScale}</span>
        <span className="right">
          {t.chrome.sheet} {pageNo}/{String(pageCount).padStart(2, '0')} · {date}T09:14Z
        </span>
      </footer>
    </div>
  );
}
