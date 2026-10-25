import type { KeyboardEvent } from 'react';
import type { PageId, PageMeta } from '../data/types';
import { useT } from '../i18n/LangContext';

interface TabNavProps {
  pages: PageMeta[];
  active: PageId;
  onChange: (id: PageId) => void;
}

export function TabNav({ pages, active, onChange }: TabNavProps) {
  const t = useT();
  const activeIndex = Math.max(
    0,
    pages.findIndex((p) => p.id === active),
  );

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft' && event.key !== 'Home' && event.key !== 'End') return;
    event.preventDefault();
    let next = activeIndex;
    if (event.key === 'ArrowRight') next = activeIndex === pages.length - 1 ? 0 : activeIndex + 1;
    if (event.key === 'ArrowLeft') next = activeIndex === 0 ? pages.length - 1 : activeIndex - 1;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = pages.length - 1;
    onChange(pages[next].id);
    const tabs = event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs[next]?.focus();
  };

  return (
    <div className="tabs" role="tablist" aria-label={t.chrome.tabAria} onKeyDown={onKeyDown}>
      {pages.map((page, index) => (
        <button
          key={page.id}
          type="button"
          role="tab"
          id={`tab-${page.id}`}
          aria-selected={page.id === active}
          aria-controls={`panel-${page.id}`}
          tabIndex={index === activeIndex ? 0 : -1}
          className="tab"
          onClick={() => onChange(page.id)}
        >
          <span className="tab-no">{page.no}</span>
          <span>{t.nav[page.id]}</span>
        </button>
      ))}
    </div>
  );
}
