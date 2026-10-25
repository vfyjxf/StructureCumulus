import type { ReactNode } from 'react';
import { useT } from '../i18n/LangContext';

interface WhyNoteProps {
  children: ReactNode;
  label?: string;
}

export function WhyNote({ children, label }: WhyNoteProps) {
  const t = useT();
  return (
    <div className="why">
      <div className="why-bar" aria-hidden="true" />
      <div className="why-body">
        <div className="why-label">{label ?? t.chrome.why}</div>
        <p className="why-text">{children}</p>
      </div>
    </div>
  );
}

