import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

export const PLAYBACK_SPEEDS = [0.5, 1, 2] as const;

export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() => {
    try {
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    } catch {
      return false;
    }
  });
  useEffect(() => {
    let query: MediaQueryList | null = null;
    try {
      query = window.matchMedia('(prefers-reduced-motion: reduce)');
    } catch {
      return;
    }
    const onChange = (event: MediaQueryListEvent) => setReduced(event.matches);
    query.addEventListener('change', onChange);
    return () => query?.removeEventListener('change', onChange);
  }, []);
  return reduced;
}

export interface PlaybackOptions {
  /** Controlled step. Omit to let the hook own it. */
  step?: number;
  onStep?: (step: number) => void;
  /** Base dwell per step at 1×. */
  intervalMs?: number;
  /** Start playing on mount unless reduced motion is requested. */
  autoStart?: boolean;
  /** Wrap to step 0 at the end instead of stopping. Only Space uses this. */
  loop?: boolean;
  /** Temporarily hold the cursor without changing the play state (hover / focus). */
  hold?: boolean;
}

export interface PlaybackController {
  step: number;
  count: number;
  setStep: (step: number) => void;
  next: () => void;
  prev: () => void;
  playing: boolean;
  toggle: () => void;
  pause: () => void;
  playFromStart: () => void;
  speed: number;
  setSpeed: (speed: number) => void;
  reduced: boolean;
}

export function usePlayback(stepCount: number, options: PlaybackOptions = {}): PlaybackController {
  const count = Math.max(1, Math.floor(stepCount));
  const reduced = useReducedMotion();
  const [ownStep, setOwnStep] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);

  const controlled = options.step !== undefined;
  const rawStep = controlled ? (options.step as number) : ownStep;
  const step = Math.max(0, Math.min(count - 1, rawStep));

  const onStepRef = useRef(options.onStep);
  onStepRef.current = options.onStep;
  const stepRef = useRef(step);
  stepRef.current = step;
  const countRef = useRef(count);
  countRef.current = count;
  const speedRef = useRef(speed);
  speedRef.current = speed;
  const holdRef = useRef(Boolean(options.hold));
  holdRef.current = Boolean(options.hold);
  const loopRef = useRef(Boolean(options.loop));
  loopRef.current = Boolean(options.loop);
  const intervalRef = useRef(options.intervalMs ?? 900);
  intervalRef.current = options.intervalMs ?? 900;

  const setStep = useCallback(
    (next: number) => {
      const clamped = Math.max(0, Math.min(countRef.current - 1, next));
      if (onStepRef.current) onStepRef.current(clamped);
      else setOwnStep(clamped);
    },
    [],
  );

  const next = useCallback(() => setStep(stepRef.current + 1), [setStep]);
  const prev = useCallback(() => setStep(stepRef.current - 1), [setStep]);

  const toggle = useCallback(() => {
    setPlaying((current) => {
      if (!current && !loopRef.current && stepRef.current >= countRef.current - 1) setStep(0);
      return !current;
    });
  }, [setStep]);

  const pause = useCallback(() => setPlaying(false), []);

  const playFromStart = useCallback(() => {
    setStep(0);
    setPlaying(true);
  }, [setStep]);

  useEffect(() => {
    if (rawStep > count - 1) setStep(count - 1);
  }, [rawStep, count, setStep]);

  useEffect(() => {
    if (options.autoStart && !reduced) setPlaying(true);
  }, [options.autoStart, reduced]);

  useEffect(() => {
    if (reduced) setPlaying(false);
  }, [reduced]);

  useEffect(() => {
    if (!playing) return;
    let frame = 0;
    let last = performance.now();
    let accumulated = 0;
    const tick = (now: number) => {
      if (holdRef.current) {
        last = now;
        accumulated = 0;
        frame = window.requestAnimationFrame(tick);
        return;
      }
      accumulated += now - last;
      last = now;
      const dwell = intervalRef.current / speedRef.current;
      if (accumulated >= dwell) {
        accumulated = 0;
        const atEnd = stepRef.current >= countRef.current - 1;
        if (atEnd) {
          if (loopRef.current) setStep(0);
          else {
            setPlaying(false);
            return;
          }
        } else {
          setStep(stepRef.current + 1);
        }
      }
      frame = window.requestAnimationFrame(tick);
    };
    frame = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(frame);
  }, [playing, speed, count, setStep]);

  return { step, count, setStep, next, prev, playing, toggle, pause, playFromStart, speed, setSpeed, reduced };
}

interface PlaybackBarProps {
  step: number;
  count: number;
  playing: boolean;
  onToggle: () => void;
  onStep: (step: number) => void;
  speed: number;
  onSpeed: (speed: number) => void;
  caption?: ReactNode;
  extra?: ReactNode;
  startLabel?: string;
}

export function PlaybackBar({
  step,
  count,
  playing,
  onToggle,
  onStep,
  speed,
  onSpeed,
  caption,
  extra,
  startLabel,
}: PlaybackBarProps) {
  const t = useT();
  const progress = count > 0 ? (Math.max(0, Math.min(count, step + 1)) / count) : 0;
  return (
    <div className="np-playback" role="group" aria-label={t.demo.transport}>
      <div className="np-playback-btns">
        <button
          type="button"
          className="np-playback-btn"
          aria-label={t.demo.prev}
          title={t.demo.prev}
          disabled={step <= 0}
          onClick={() => onStep(step - 1)}
        >
          |◀
        </button>
        <button
          type="button"
          className="np-playback-btn np-playback-btn--play"
          data-playing={playing}
          aria-label={playing ? t.demo.pause : (startLabel ?? t.demo.play)}
          title={playing ? t.demo.pause : (startLabel ?? t.demo.play)}
          aria-pressed={playing}
          onClick={onToggle}
        >
          {playing ? '❚❚' : '▶'}
        </button>
        <button
          type="button"
          className="np-playback-btn"
          aria-label={t.demo.next}
          title={t.demo.next}
          disabled={step >= count - 1}
          onClick={() => onStep(step + 1)}
        >
          ▶|
        </button>
      </div>

      <div className="np-playback-track" aria-hidden="true">
        <span className="np-playback-fill" style={{ transform: `scaleX(${progress})` }} />
      </div>

      <span className="np-playback-count np-num" aria-label={fmt(t.demo.stepOf, { n: step + 1, total: count })}>
        {String(step + 1).padStart(2, '0')}/{String(count).padStart(2, '0')}
      </span>

      <div className="np-playback-speed" role="group" aria-label={t.demo.speed}>
        {PLAYBACK_SPEEDS.map((option) => (
          <button
            key={option}
            type="button"
            className="np-playback-btn np-playback-btn--speed"
            aria-pressed={speed === option}
            title={fmt(t.demo.speedOption, { n: option })}
            onClick={() => onSpeed(option)}
          >
            {option}×
          </button>
        ))}
      </div>

      {extra ? <div className="np-playback-extra">{extra}</div> : null}
      {caption ? (
        <p className="np-playback-caption" aria-live="polite">
          {caption}
        </p>
      ) : null}
    </div>
  );
}
