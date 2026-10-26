import { useEffect, useMemo, useRef, useState } from 'react';
import { MODULES, NAV, type ModuleId, type SectionId } from '../data/site';
import { ZH_MODULES, ZH_NAV } from '../data/site.zh';
import { useLanguage } from '../i18n/Language';

type Item =
  | { kind: 'section'; id: SectionId; label: string; no: string; hint: string }
  | { kind: 'module'; id: ModuleId; label: string; no: string; hint: string };

export function CommandPalette({
  open,
  onClose,
  onSection,
  onModule,
}: {
  open: boolean;
  onClose: () => void;
  onSection: (id: SectionId) => void;
  onModule: (id: ModuleId) => void;
}) {
  const { zh } = useLanguage();
  const [query, setQuery] = useState('');
  const [index, setIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  const items = useMemo<Item[]>(() => {
    const sections: Item[] = NAV.map((entry) => ({
      kind: 'section',
      id: entry.id,
      label: zh ? ZH_NAV[entry.id].label : entry.label,
      no: entry.no,
      hint: zh ? ZH_NAV[entry.id].sub : entry.sub,
    }));
    const modules: Item[] = MODULES.map((entry) => ({
      kind: 'module',
      id: entry.id,
      label: `kernel.${entry.id}`,
      no: `R${entry.rank}`,
      hint: zh ? ZH_MODULES[entry.id].owns : entry.owns,
    }));
    const all = [...sections, ...modules];
    const q = query.trim().toLowerCase();
    if (!q) return all;
    return all.filter((item) => `${item.label} ${item.hint} ${item.no}`.toLowerCase().includes(q));
  }, [query, zh]);

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    setQuery('');
    setIndex(0);
    const timer = window.setTimeout(() => inputRef.current?.focus(), 20);
    return () => {
      window.clearTimeout(timer);
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      } else if (event.key === 'ArrowDown') {
        event.preventDefault();
        setIndex((current) => Math.min(current + 1, Math.max(0, items.length - 1)));
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        setIndex((current) => Math.max(0, current - 1));
      } else if (event.key === 'Enter') {
        event.preventDefault();
        const item = items[index];
        if (!item) { onClose(); return; }
        if (item.kind === 'section') onSection(item.id);
        else onModule(item.id);
        onClose();
      } else if (event.key === 'Tab') {
        const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('input, button, [href]') ?? []);
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, items, index, onClose, onSection, onModule]);

  useEffect(() => {
    setIndex((current) => Math.min(current, Math.max(0, items.length - 1)));
  }, [items.length]);

  if (!open) return null;

  return (
    <div className="palette-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div ref={dialogRef} className="palette" role="dialog" aria-modal="true" aria-label={zh ? 'Review 索引' : 'Review index'}>
        <input
          ref={inputRef}
          className="palette-input"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={zh ? '跳到章节或 package…' : 'Jump to a chapter or package…'}
          aria-label={zh ? '搜索 review 索引' : 'Search review index'}
        />
        <div className="palette-list">
          {items.length === 0 ? (
            <div className="palette-group">{zh ? '没有匹配' : 'No matches'}</div>
          ) : (
            items.map((item, itemIndex) => (
              <div key={`${item.kind}-${item.id}`}>
                {itemIndex === 0 || item.kind !== items[itemIndex - 1]?.kind ? (
                  <div className="palette-group">{item.kind === 'section' ? (zh ? '章节' : 'Chapters') : (zh ? '内核 package' : 'Kernel packages')}</div>
                ) : null}
                <button
                  type="button"
                  className="palette-item"
                  data-active={itemIndex === index}
                  onMouseEnter={() => setIndex(itemIndex)}
                  onClick={() => {
                    if (item.kind === 'section') onSection(item.id);
                    else onModule(item.id);
                    onClose();
                  }}
                >
                  <span>{item.label}</span>
                  <span className="no">{item.no}</span>
                </button>
              </div>
            ))
          )}
        </div>
        <div className="palette-foot">
          <span><span className="palette-kbd">↑↓</span> {zh ? '选择' : 'navigate'} · <span className="palette-kbd">↵</span> {zh ? '打开' : 'open'}</span>
          <span><span className="palette-kbd">esc</span> {zh ? '关闭' : 'close'}</span>
        </div>
      </div>
    </div>
  );
}
