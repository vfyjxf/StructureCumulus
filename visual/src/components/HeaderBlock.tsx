import type { PageMeta } from '../data/types';
import { StatusChip, stageKind } from './StatusChip';
import { useT } from '../i18n/LangContext';

interface HeaderBlockProps {
  page: PageMeta;
}

export function HeaderBlock({ page }: HeaderBlockProps) {
  const t = useT();
  const fields: { label: string; value: string; cls?: string }[] = [
    { label: t.chrome.sheetNo, value: page.sheet, cls: 'hdr-field--sheet' },
    { label: t.chrome.rev, value: page.revision, cls: 'hdr-field--rev' },
    { label: t.chrome.scale, value: page.scale, cls: 'hdr-field--scale' },
    { label: t.chrome.drawn, value: '—', cls: 'hdr-field--optional' },
    { label: t.chrome.checked, value: '—', cls: 'hdr-field--optional' },
    { label: t.chrome.date, value: page.date, cls: 'hdr-field--date' },
    { label: t.chrome.zone, value: page.zone, cls: 'hdr-field--zone' },
  ];

  return (
    <>
      <header className="header-block" aria-label={t.chrome.sheetHeaderAria}>
        <div className="hdr-top">
          <div className="hdr-title">
            <h1>{t.nav[page.id]}</h1>
            <div className="hdr-sub">{t.pages[page.id].subtitle}</div>
          </div>
          <div className="hdr-right">
            <StatusChip kind={stageKind(page.stage)} size="lg" title={t.chrome.stageTitle}>
              {page.stage}
            </StatusChip>
            <span className="stamp">{page.stamp}</span>
          </div>
        </div>
        <div className="hdr-fields">
          {fields.map((field) => (
            <div className={`hdr-field ${field.cls ?? ''}`.trim()} key={field.label}>
              <span className="hdr-field-label">{field.label}</span>
              <span className="hdr-field-value">{field.value}</span>
            </div>
          ))}
        </div>
      </header>
      <div className="rule-soft" aria-hidden="true" />
    </>
  );
}
