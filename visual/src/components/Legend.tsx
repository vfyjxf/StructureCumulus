import { useT } from '../i18n/LangContext';

export type SwatchKind = 'rect' | 'line' | 'dash' | 'signal' | 'tint' | 'hatch' | 'overlap' | 'owned' | 'unknown';

export interface LegendItem {
  label: string;
  kind: SwatchKind;
  count?: string;
  color?: string;
  title?: string;
}

interface LegendProps {
  title?: string;
  items: LegendItem[];
  columns?: 1 | 2;
}

export function Legend({ title, items, columns = 2 }: LegendProps) {
  const t = useT();
  const heading = title ?? t.chrome.legend;
  return (
    <div className="legend" aria-label={heading}>
      <div className="legend-title">{heading}</div>
      <div className={`legend-grid${columns === 1 ? ' legend-grid--one' : ''}`}>
        {items.map((item) => (
          <div className="legend-row" key={item.label} title={item.title}>
            <span
              className={`swatch swatch--${item.kind}`}
              style={item.color ? { background: item.color } : undefined}
              aria-hidden="true"
            />
            <span className="legend-label">{item.label}</span>
            {item.count ? <span className="legend-count">{item.count}</span> : null}
          </div>
        ))}
      </div>
    </div>
  );
}

export function CornerTicks() {
  return (
    <span className="ticks" aria-hidden="true">
      <span />
      <span />
      <span />
      <span />
      <span />
      <span />
      <span />
      <span />
    </span>
  );
}
