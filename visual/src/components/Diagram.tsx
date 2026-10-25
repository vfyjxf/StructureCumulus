import type { ReactNode } from 'react';

export type Side = 'left' | 'right' | 'top' | 'bottom';

export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface Point {
  x: number;
  y: number;
}

export const port = (box: Box, side: Side): Point => {
  switch (side) {
    case 'left':
      return { x: box.x, y: box.y + box.h / 2 };
    case 'right':
      return { x: box.x + box.w, y: box.y + box.h / 2 };
    case 'top':
      return { x: box.x + box.w / 2, y: box.y };
    case 'bottom':
      return { x: box.x + box.w / 2, y: box.y + box.h };
  }
};

export const orthoPath = (from: Point, fromSide: Side, to: Point, toSide: Side): string => {
  const horizontalFrom = fromSide === 'left' || fromSide === 'right';
  const horizontalTo = toSide === 'left' || toSide === 'right';
  if (horizontalFrom && horizontalTo) {
    if (Math.abs(to.y - from.y) < 0.5) return `M ${from.x} ${from.y} H ${to.x}`;
    const midX = (from.x + to.x) / 2;
    return `M ${from.x} ${from.y} H ${midX} V ${to.y} H ${to.x}`;
  }
  if (!horizontalFrom && !horizontalTo) {
    if (Math.abs(to.x - from.x) < 0.5) return `M ${from.x} ${from.y} V ${to.y}`;
    const midY = (from.y + to.y) / 2;
    return `M ${from.x} ${from.y} V ${midY} H ${to.x} V ${to.y}`;
  }
  if (horizontalFrom) {
    return `M ${from.x} ${from.y} H ${to.x} V ${to.y}`;
  }
  return `M ${from.x} ${from.y} V ${to.y} H ${to.x}`;
};

const arrowPoints = (tip: Point, side: Side): string => {
  switch (side) {
    case 'left':
      return `${tip.x} ${tip.y} ${tip.x + 7} ${tip.y - 3.5} ${tip.x + 7} ${tip.y + 3.5}`;
    case 'right':
      return `${tip.x} ${tip.y} ${tip.x - 7} ${tip.y - 3.5} ${tip.x - 7} ${tip.y + 3.5}`;
    case 'top':
      return `${tip.x} ${tip.y} ${tip.x - 3.5} ${tip.y + 7} ${tip.x + 3.5} ${tip.y + 7}`;
    case 'bottom':
      return `${tip.x} ${tip.y} ${tip.x - 3.5} ${tip.y - 7} ${tip.x + 3.5} ${tip.y - 7}`;
  }
};

interface WireProps {
  from: Box;
  to: Box;
  fromSide?: Side;
  toSide?: Side;
  label?: string;
  dash?: boolean;
  signal?: boolean;
  hair?: boolean;
  arrow?: boolean;
  labelSide?: 'left' | 'right';
}

export function Wire({
  from,
  to,
  fromSide = 'right',
  toSide = 'left',
  label,
  dash = false,
  signal = false,
  hair = false,
  arrow = true,
  labelSide = 'left',
}: WireProps) {
  const a = port(from, fromSide);
  const b = port(to, toSide);
  const d = orthoPath(a, fromSide, b, toSide);
  const horizontalFrom = fromSide === 'left' || fromSide === 'right';
  const horizontalTo = toSide === 'left' || toSide === 'right';
  let lx = (a.x + b.x) / 2;
  let ly = (a.y + b.y) / 2 - 6;
  let textAnchor: 'start' | 'middle' = 'middle';
  if (horizontalFrom && horizontalTo) {
    if (Math.abs(a.y - b.y) < 0.5) {
      lx = (a.x + b.x) / 2;
      ly = a.y - 6;
    } else {
      lx = (a.x + b.x) / 2 + 6;
      ly = (a.y + b.y) / 2;
      textAnchor = 'start';
    }
  } else if (!horizontalFrom && !horizontalTo) {
    lx = a.x + 6;
    ly = (a.y + b.y) / 2;
    textAnchor = 'start';
  } else if (horizontalFrom) {
    lx = (a.x + b.x) / 2;
    ly = a.y - 6;
  } else {
    lx = a.x + 6;
    ly = (a.y + b.y) / 2;
    textAnchor = 'start';
  }
  return (
    <g>
      <path
        className={`wire${dash ? ' wire--dash' : ''}${signal ? ' wire--signal' : ''}${hair ? ' wire--hair' : ''}`}
        d={d}
        vectorEffect="non-scaling-stroke"
      />
      {arrow ? <polygon className={signal ? 'arrow arrow--signal' : 'arrow'} points={arrowPoints(b, toSide)} /> : null}
      {label ? (
        <text
          className="t-mono t-muted"
          x={lx}
          y={ly}
          fontSize={9}
          textAnchor={textAnchor}
          paintOrder="stroke"
          stroke="var(--np-paper)"
          strokeWidth={3}
        >
          {label}
        </text>
      ) : null}
    </g>
  );
}

export function truncate(text: string, width: number, charWidth = 6.4): string {
  const max = Math.max(4, Math.floor((width - 16) / charWidth));
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

interface NodeBoxProps extends Box {
  title: string;
  sub?: string;
  state?: 'default' | 'selected' | 'active' | 'failed' | 'unknown';
  onSelect?: () => void;
  selected?: boolean;
  ariaLabel?: string;
  ports?: boolean;
  tint?: boolean;
  subColor?: 'muted' | 'signal' | 'fail';
}

export function NodeBox({
  x,
  y,
  w,
  h,
  title,
  sub,
  state = 'default',
  onSelect,
  selected,
  ariaLabel,
  ports = false,
  tint = false,
  subColor = 'muted',
}: NodeBoxProps) {
  const resolved = selected ? 'selected' : state;
  const interactive = Boolean(onSelect);
  const subClass = subColor === 'signal' ? 't-signal' : subColor === 'fail' ? 't-fail' : 't-muted';
  return (
    <g
      className={interactive ? 'interactive' : undefined}
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-label={ariaLabel ?? `${title}${sub ? `, ${sub}` : ''}`}
      aria-pressed={interactive ? Boolean(selected) : undefined}
      onClick={onSelect}
      onKeyDown={
        interactive
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onSelect?.();
              }
            }
          : undefined
      }
    >
      {tint ? <rect x={x} y={y} width={w} height={h} fill="var(--np-tint-signal)" /> : null}
      <rect
        className="node"
        data-state={resolved}
        x={x}
        y={y}
        width={w}
        height={h}
        vectorEffect="non-scaling-stroke"
      />
      {resolved === 'active' ? <rect x={x} y={y + 1} width={3} height={h - 2} fill="var(--np-ok-ink)" /> : null}
      {resolved === 'failed' ? (
        <g aria-hidden="true">
          <rect x={x + w - 14} y={y} width={14} height={14} fill="var(--np-paper)" stroke="var(--np-alert)" strokeWidth={1} />
          <line x1={x + w - 14} y1={y + 14} x2={x + w} y2={y} stroke="var(--np-alert)" strokeWidth={2} />
          <line x1={x + w - 14} y1={y + 8} x2={x + w - 8} y2={y} stroke="var(--np-alert)" strokeWidth={2} />
        </g>
      ) : null}
      {resolved === 'selected' ? (
        <g stroke="var(--np-signal-ink)" strokeWidth={1} aria-hidden="true">
          <line x1={x + 3} y1={y + 3} x2={x + 9} y2={y + 3} />
          <line x1={x + 3} y1={y + 3} x2={x + 3} y2={y + 9} />
          <line x1={x + w - 9} y1={y + 3} x2={x + w - 3} y2={y + 3} />
          <line x1={x + w - 3} y1={y + 3} x2={x + w - 3} y2={y + 9} />
          <line x1={x + 3} y1={y + h - 3} x2={x + 9} y2={y + h - 3} />
          <line x1={x + 3} y1={y + h - 9} x2={x + 3} y2={y + h - 3} />
          <line x1={x + w - 9} y1={y + h - 3} x2={x + w - 3} y2={y + h - 3} />
          <line x1={x + w - 3} y1={y + h - 9} x2={x + w - 3} y2={y + h - 3} />
        </g>
      ) : null}
      {ports ? (
        <g aria-hidden="true">
          <rect x={x - 3} y={y + h / 2 - 3} width={6} height={6} fill="var(--np-paper)" stroke="var(--np-rule)" />
          <rect x={x + w - 3} y={y + h / 2 - 3} width={6} height={6} fill="var(--np-paper)" stroke="var(--np-rule)" />
        </g>
      ) : null}
      <text className="t-title" x={x + 8} y={y + 19} fontSize={12}>
        {truncate(title, w, 6.8)}
      </text>
      {sub ? (
        <text className={`t-mono ${subClass}`} x={x + 8} y={y + 34} fontSize={9}>
          {truncate(sub, w, 5.6)}
        </text>
      ) : null}
    </g>
  );
}

interface DiagramSvgProps {
  viewBox: string;
  minWidth?: number;
  label: string;
  title: string;
  desc: string;
  children: ReactNode;
  interactive?: boolean;
  maxWidth?: number;
  svgRef?: React.Ref<SVGSVGElement>;
  svgStyle?: React.CSSProperties;
  onPointerDown?: React.PointerEventHandler<SVGSVGElement>;
  onPointerMove?: React.PointerEventHandler<SVGSVGElement>;
  onPointerUp?: React.PointerEventHandler<SVGSVGElement>;
  onPointerCancel?: React.PointerEventHandler<SVGSVGElement>;
}

export function DiagramSvg({
  viewBox,
  minWidth = 640,
  label,
  title,
  desc,
  children,
  interactive = false,
  maxWidth,
  svgRef,
  svgStyle,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onPointerCancel,
}: DiagramSvgProps) {
  return (
    <div className="dscroll">
      <svg
        ref={svgRef}
        className="diagram"
        viewBox={viewBox}
        style={{ minWidth, maxWidth, ...svgStyle }}
        role={interactive ? 'group' : 'img'}
        aria-label={label}
        preserveAspectRatio="xMidYMid meet"
        xmlns="http://www.w3.org/2000/svg"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
      >
        <title>{title}</title>
        <desc>{desc}</desc>
        {children}
      </svg>
    </div>
  );
}

export function GridLayer({ w, h, step = 16 }: { w: number; h: number; step?: number }) {
  const lines: ReactNode[] = [];
  for (let x = step; x < w; x += step) {
    lines.push(<line key={`v${x}`} className="grid-line" x1={x + 0.5} y1={0} x2={x + 0.5} y2={h} />);
  }
  for (let y = step; y < h; y += step) {
    lines.push(<line key={`h${y}`} className="grid-line" x1={0} y1={y + 0.5} x2={w} y2={y + 0.5} />);
  }
  return (
    <g aria-hidden="true" shapeRendering="crispEdges">
      {lines}
    </g>
  );
}

export function PatternDefs() {
  return (
    <svg width="0" height="0" aria-hidden="true" focusable="false" style={{ position: 'absolute' }}>
      <defs>
        <pattern id="np-hatch-conflict" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="8" height="8" fill="var(--np-paper)" />
          <line x1="0" y1="0" x2="0" y2="8" stroke="var(--np-alert)" strokeWidth="3" />
        </pattern>
        <pattern id="np-hatch-overlap" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="8" height="8" fill="var(--np-tint-neutral)" />
          <line x1="0" y1="0" x2="0" y2="8" stroke="var(--np-rule)" strokeWidth="1.5" />
        </pattern>
        <pattern id="np-hatch-fail" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="8" height="8" fill="var(--np-paper)" />
          <line x1="0" y1="0" x2="0" y2="8" stroke="var(--np-alert)" strokeWidth="4" />
        </pattern>
      </defs>
    </svg>
  );
}
