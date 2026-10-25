import type { ReactNode } from 'react';

interface PanelProps {
  title: string;
  count?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  flush?: boolean;
  compact?: boolean;
  error?: boolean;
  className?: string;
  id?: string;
  'aria-labelledby'?: string;
}

export function Panel({
  title,
  count,
  actions,
  children,
  flush = false,
  compact = false,
  error = false,
  className = '',
  id,
}: PanelProps) {
  const headingId = id ? `${id}-title` : undefined;
  return (
    <section
      className={`panel${compact ? ' panel--compact' : ''}${error ? ' panel--error' : ''} ${className}`.trim()}
      id={id}
      aria-labelledby={headingId}
    >
      <header className="panel-hd">
        <h2 className="panel-hd-title" id={headingId}>
          {title}
        </h2>
        {count !== undefined && count !== null ? <span className="panel-count">{count}</span> : null}
        {actions ? <div className="panel-actions">{actions}</div> : null}
      </header>
      <div className={`panel-bd${flush ? ' panel-bd--flush' : ''}`}>{children}</div>
    </section>
  );
}
