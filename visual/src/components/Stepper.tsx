import { useT } from '../i18n/LangContext';

export type StepVisualState = 'done' | 'current' | 'todo' | 'failed' | 'skipped' | 'warn';

export interface StepItem {
  id: string;
  no: string;
  label: string;
  state: StepVisualState;
}

interface StepperProps {
  steps: StepItem[];
  selectedId: string;
  onSelect: (id: string) => void;
  ariaLabel: string;
}

export function Stepper({ steps, selectedId, onSelect, ariaLabel }: StepperProps) {
  const t = useT();
  const selectedIndex = Math.max(
    0,
    steps.findIndex((s) => s.id === selectedId),
  );

  return (
    <div className="dscroll" style={{ paddingBottom: 2 }}>
      <div className="stepper" role="group" aria-label={ariaLabel}>
        {steps.map((step, index) => {
          const isCurrent = index === selectedIndex;
          const state: StepVisualState =
            isCurrent && step.state !== 'failed' && step.state !== 'warn' ? 'current' : step.state;
          const leftClass =
            index === 0
              ? 'step-line step-line--hidden'
              : steps[index - 1].state === 'done'
                ? 'step-line step-line--done'
                : index <= selectedIndex
                  ? 'step-line step-line--current'
                  : 'step-line';
          const rightClass =
            index === steps.length - 1
              ? 'step-line step-line--hidden'
              : step.state === 'done'
                ? 'step-line step-line--done'
                : 'step-line';
          return (
            <button
              key={step.id}
              type="button"
              className="step"
              data-state={state}
              aria-pressed={isCurrent}
              aria-label={`${t.chrome.stepAria} ${step.no} ${step.label}, ${state}`}
              onClick={() => onSelect(step.id)}
            >
              <span className="step-rail" aria-hidden="true">
                <span className={leftClass} />
                <span className="step-node">{step.no}</span>
                <span className={rightClass} />
              </span>
              <span className="step-label">
                {step.label}
                {(state === 'failed' || state === 'warn') && <span className="step-excl"> !</span>}
                {state === 'skipped' && <span className="muted"> {t.chrome.skipped}</span>}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
