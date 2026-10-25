import type { ReactNode } from 'react';

interface ReadoutProps {
  label: string;
  value: ReactNode;
  unit?: string;
  state?: 'nominal' | 'active' | 'fail' | 'unknown' | 'ok';
  delta?: string;
  primary?: boolean;
  title?: string;
}

export function Readout({ label, value, unit, state = 'nominal', delta, primary = false, title }: ReadoutProps) {
  return (
    <div
      className={`readout${primary ? '' : ' readout--secondary'}`}
      data-state={state}
      title={title}
      role="group"
      aria-label={`${label}: ${String(value)}${unit ? ` ${unit}` : ''}`}
    >
      <div className="readout-bar" aria-hidden="true" />
      <div className="readout-body">
        <div className="readout-label">{label}</div>
        <div className="readout-value-row">
          <span className="readout-value">{value}</span>
          {unit ? <span className="readout-unit">{unit}</span> : null}
        </div>
        {delta ? <div className="readout-delta">{delta}</div> : null}
      </div>
    </div>
  );
}
