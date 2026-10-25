import { useEffect, useMemo, useRef, useState } from 'react';
import type { DemoScenario, PageId } from '../data/types';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

function readParams(): URLSearchParams {
  try {
    return new URLSearchParams(window.location.search);
  } catch {
    return new URLSearchParams();
  }
}

function writeParams(patch: Record<string, string>) {
  try {
    const params = readParams();
    for (const [key, value] of Object.entries(patch)) params.set(key, value);
    const query = params.toString();
    window.history.replaceState(null, '', `${window.location.pathname}${query ? `?${query}` : ''}${window.location.hash}`);
  } catch {
    /* file:// may refuse history */
  }
}

export interface ScenarioState<Id extends string> {
  scenario: Id;
  setScenario: (id: Id, step?: number) => void;
  step: number;
  setStep: (step: number) => void;
}

/**
 * Keeps the selected scenario and step in `?scenario=&step=` while leaving
 * `page`, `lang` and `theme` untouched. Invalid ids fall back to the default.
 */
export function useScenarioState<Id extends string>({
  page,
  scenarios,
  defaultScenario,
  defaultStep = 0,
  stepCount,
}: {
  page: PageId;
  scenarios: readonly DemoScenario<Id>[];
  defaultScenario: Id;
  defaultStep?: number;
  stepCount: number;
}): ScenarioState<Id> {
  const ids = useMemo(() => scenarios.map((scenario) => scenario.id), [scenarios]);
  const initial = useMemo(() => {
    const params = readParams();
    const rawScenario = params.get('scenario');
    const scenario = ids.includes(rawScenario as Id) ? (rawScenario as Id) : defaultScenario;
    const rawStep = Number(params.get('step'));
    const step = params.has('step') && Number.isFinite(rawStep) ? Math.max(0, Math.floor(rawStep)) : defaultStep;
    return { scenario, step };
    // The URL is read once; later changes are driven by state and popstate.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [scenario, setScenarioState] = useState<Id>(initial.scenario);
  const [step, setStepState] = useState(initial.step);
  const stepCountRef = useRef(stepCount);
  stepCountRef.current = stepCount;

  const clamp = (value: number) => Math.max(0, Math.min(stepCountRef.current - 1, Math.floor(value)));

  useEffect(() => {
    setStepState((current) => Math.min(current, Math.max(0, stepCount - 1)));
  }, [stepCount]);

  useEffect(() => {
    writeParams({ page, scenario, step: String(step) });
  }, [page, scenario, step]);

  useEffect(() => {
    const sync = () => {
      const params = readParams();
      const rawScenario = params.get('scenario');
      if (ids.includes(rawScenario as Id)) setScenarioState(rawScenario as Id);
      const rawStep = Number(params.get('step'));
      if (Number.isFinite(rawStep)) setStepState(Math.max(0, Math.floor(rawStep)));
    };
    window.addEventListener('popstate', sync);
    window.addEventListener('hashchange', sync);
    return () => {
      window.removeEventListener('popstate', sync);
      window.removeEventListener('hashchange', sync);
    };
  }, [ids]);

  const setScenario = (id: Id, nextStep = 0) => {
    setScenarioState(id);
    setStepState(clamp(nextStep));
  };

  return { scenario, setScenario, step, setStep: (value: number) => setStepState(clamp(value)) };
}

interface ScenarioPickerProps<Id extends string> {
  scenarios: readonly DemoScenario<Id>[];
  value: Id;
  onChange: (id: Id) => void;
  label?: string;
  showNote?: boolean;
}

export function ScenarioPicker<Id extends string>({
  scenarios,
  value,
  onChange,
  label,
  showNote = true,
}: ScenarioPickerProps<Id>) {
  const t = useT();
  const c = useLang().c;
  const current = scenarios.find((scenario) => scenario.id === value) ?? scenarios[0];
  return (
    <div className="np-scenarios">
      <div className="np-scenarios-hd">
        <span className="np-scenarios-label">{label ?? t.demo.scenarios}</span>
        {current?.steps ? <span className="np-scenarios-count">{fmt(t.demo.steps, { n: current.steps })}</span> : null}
      </div>
      <div className="np-scenarios-list" role="group" aria-label={label ?? t.demo.scenarios}>
        {scenarios.map((scenario) => {
          const selected = scenario.id === value;
          return (
            <button
              key={scenario.id}
              type="button"
              className="np-scenario"
              aria-pressed={selected}
              title={c(scenario.note)}
              onClick={() => onChange(scenario.id)}
            >
              <span className="np-scenario-id np-num">{scenario.id}</span>
              <span className="np-scenario-name">{c(scenario.label)}</span>
            </button>
          );
        })}
      </div>
      {showNote && current ? <p className="np-scenario-note">{c(current.note)}</p> : null}
    </div>
  );
}
