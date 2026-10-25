import type { StatusKind } from '../data/types';

interface StatusChipProps {
  kind?: StatusKind;
  solid?: boolean;
  size?: 'sm' | 'md' | 'lg';
  children: React.ReactNode;
  title?: string;
}

export function StatusChip({ kind = 'neutral', solid = false, size = 'md', children, title }: StatusChipProps) {
  const sizeClass = size === 'sm' ? ' chip--sm' : size === 'lg' ? ' chip--lg' : '';
  return (
    <span
      className={`chip chip--${kind}${solid ? ' chip--solid' : ''}${sizeClass}`}
      title={title}
    >
      {children}
    </span>
  );
}

export function replyKind(reply: string): StatusKind {
  if (reply === 'MATCH') return 'ok';
  if (reply === 'MISMATCH') return 'fail';
  if (reply === 'UNREADABLE') return 'warn';
  if (reply === 'ABSTAIN') return 'unknown';
  return 'neutral';
}

export function stageKind(stage: string): StatusKind {
  if (stage === 'SETTLE') return 'ok';
  if (stage === 'FORM') return 'info';
  if (stage === 'PROBE') return 'neutral';
  if (stage === 'STALE') return 'warn';
  return 'neutral';
}
