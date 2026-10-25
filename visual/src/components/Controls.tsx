import type { KeyboardEvent } from 'react';

export interface Option<T extends string | number> {
  value: T;
  label: string;
  title?: string;
}

interface SegmentedProps<T extends string | number> {
  label: string;
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel?: string;
  hideLabel?: boolean;
}

export function Segmented<T extends string | number>({
  label,
  options,
  value,
  onChange,
  ariaLabel,
  hideLabel = false,
}: SegmentedProps<T>) {
  const selectedIndex = Math.max(
    0,
    options.findIndex((o) => o.value === value),
  );

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft' && event.key !== 'ArrowDown' && event.key !== 'ArrowUp' && event.key !== 'Home' && event.key !== 'End') {
      return;
    }
    event.preventDefault();
    const last = options.length - 1;
    let next = selectedIndex;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = selectedIndex === last ? 0 : selectedIndex + 1;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = selectedIndex === 0 ? last : selectedIndex - 1;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = last;
    onChange(options[next].value);
    const group = event.currentTarget;
    const buttons = group.querySelectorAll<HTMLButtonElement>('[role="radio"]');
    buttons[next]?.focus();
  };

  return (
    <div className="field">
      {hideLabel ? null : <span className="field-label">{label}</span>}
      <div className="seg" role="radiogroup" aria-label={ariaLabel ?? label} onKeyDown={onKeyDown}>
        {options.map((option, index) => (
          <button
            key={String(option.value)}
            type="button"
            role="radio"
            aria-checked={option.value === value}
            tabIndex={index === selectedIndex ? 0 : -1}
            className="btn"
            title={option.title}
            onClick={() => onChange(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

interface ToggleProps {
  pressed: boolean;
  onChange: (next: boolean) => void;
  children: React.ReactNode;
  title?: string;
}

export function Toggle({ pressed, onChange, children, title }: ToggleProps) {
  return (
    <button type="button" className="toggle" aria-pressed={pressed} onClick={() => onChange(!pressed)} title={title}>
      <span className="toggle-box" aria-hidden="true" />
      <span>{children}</span>
    </button>
  );
}

interface NumberStepperProps {
  label: string;
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  suffix?: string;
}

export function NumberStepper({ label, value, min, max, onChange, suffix }: NumberStepperProps) {
  return (
    <div className="field">
      <span className="field-label">{label}</span>
      <div className="mini-num">
        <button type="button" aria-label={`${label} decrease`} disabled={value <= min} onClick={() => onChange(Math.max(min, value - 1))}>
          −
        </button>
        <span aria-live="polite" aria-label={`${label} ${value}${suffix ?? ''}`}>
          {value}
          {suffix ?? ''}
        </span>
        <button type="button" aria-label={`${label} increase`} disabled={value >= max} onClick={() => onChange(Math.min(max, value + 1))}>
          +
        </button>
      </div>
    </div>
  );
}

interface TextFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  invalid?: boolean;
  describedBy?: string;
  width?: number;
  placeholder?: string;
}

export function TextField({ label, value, onChange, invalid, describedBy, width, placeholder }: TextFieldProps) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      <input
        className={`input input--text${invalid ? ' input--error' : ''}`}
        style={width ? { width } : undefined}
        value={value}
        placeholder={placeholder}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        spellCheck={false}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

interface SelectFieldProps {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
  hideLabel?: boolean;
}

export function SelectField({ label, value, options, onChange, hideLabel = false }: SelectFieldProps) {
  return (
    <label className="field">
      {hideLabel ? null : <span className="field-label">{label}</span>}
      <select className="input" value={value} onChange={(event) => onChange(event.target.value)} aria-label={label}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
