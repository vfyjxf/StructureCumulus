import { useEffect, useRef, useState, type ReactNode } from 'react';

export function Reveal({ children, className = '', delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            node.dataset.in = 'true';
            observer.disconnect();
          }
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.06 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);
  return (
    <div ref={ref} className={`reveal ${className}`} style={{ transitionDelay: `${delay}ms` }}>
      {children}
    </div>
  );
}

export function SectionHead({
  no,
  title,
  note,
  kicker,
}: {
  no: string;
  title: ReactNode;
  note: ReactNode;
  kicker?: string;
}) {
  return (
    <div className="section-head">
      <div className="section-no">{no}</div>
      <div>
        {kicker ? <div className="kicker" style={{ marginBottom: 12 }}>{kicker}</div> : null}
        <h2 className="section-title">{title}</h2>
      </div>
      <p className="section-note">{note}</p>
    </div>
  );
}

type SegmentedProps<T extends string> = {
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
  label?: string;
};

export function Segmented<T extends string>({ value, options, onChange, label }: SegmentedProps<T>) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          data-active={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

const KEYWORDS = new Set([
  'public', 'private', 'static', 'final', 'record', 'class', 'interface', 'enum', 'extends',
  'implements', 'new', 'return', 'if', 'else', 'for', 'while', 'null', 'true', 'false',
  'void', 'int', 'long', 'boolean', 'import', 'package', 'this', 'super', 'throws',
]);

function tokenClass(token: string): string {
  if (/^".*"$/.test(token)) return 'str';
  if (/^\/\//.test(token)) return 'com';
  if (KEYWORDS.has(token)) return 'kw';
  if (/^[A-Z][A-Za-z0-9_]*$/.test(token)) return 'typ';
  return '';
}

function lineTokens(line: string) {
  const parts = line.split(/("(?:\\.|[^"\\])*"|\/\/.*$|\b[A-Za-z_][A-Za-z0-9_]*\b)/g);
  return parts.map((part, index) => {
    if (!part) return null;
    const cls = tokenClass(part);
    return cls ? <span key={index} className={cls}>{part}</span> : <span key={index}>{part}</span>;
  });
}

export function CodeBlock({
  code,
  language,
  className = '',
}: {
  code: string;
  language?: string;
  className?: string;
}) {
  const lines = code.split('\n');
  return (
    <pre className={`code-block ${className}`} aria-label={language ? `${language} source` : 'source'}>
      <code>
        {lines.map((line, index) => (
          <span key={index} className="code-line">
            <span className="ln">{String(index + 1).padStart(2, '0')}</span>
            {lineTokens(line)}
            {'\n'}
          </span>
        ))}
      </code>
    </pre>
  );
}

export function Stat({ value, label }: { value: ReactNode; label: string }) {
  return (
    <div className="stat-cell">
      <span className="v">{value}</span>
      <span className="k">{label}</span>
    </div>
  );
}

export function CopyButton({ text, label = 'copy' }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      className="link-btn"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setCopied(true);
          window.setTimeout(() => setCopied(false), 1400);
        } catch {
          setCopied(false);
        }
      }}
    >
      {copied ? 'copied' : label}
    </button>
  );
}
