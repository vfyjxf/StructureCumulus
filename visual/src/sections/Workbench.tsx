import { useEffect, useMemo, useState } from 'react';
import {
  BAYS,
  CAMPFIRE,
  CAMPFIRE_CELLS,
  CHECKS,
  DOCUMENT_SNIPPET,
  FURNACE,
  INVARIANTS,
  JAVA_SNIPPET,
  MOMENTS,
  PHASES,
  QUARTER_TURNS,
  SITING,
  orientPoint,
  sitingImage,
  type BayId,
  type CampfireCell,
  type MomentId,
  type TurnQuarter,
} from '../data/campfire';
import { CodeBlock, Reveal, SectionHead } from '../components/ui';
import { ZH_BAYS, ZH_FURNACE, ZH_INVARIANTS, ZH_MOMENTS, ZH_QUARTERS, ZH_SITING } from '../data/campfire.zh';
import { useLanguage } from '../i18n/Language';
import { IsometricCampfire } from '../components/IsometricCampfire';
import '../styles/workbench.css';

type SourceId = 'document' | 'java';

const MOMENT_ORDER: MomentId[] = MOMENTS.map((entry) => entry.id);

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

const LOCAL_MAP = { cell: 24, pad: 16 };
const WORLD_MAP = { cell: 11, pad: 8, span: 12 };

const FOOT_CORNERS: [number, number][] = [[0, 0], [3, 0], [3, 3], [0, 3]];
const FIRE_CORNERS: [number, number][] = [[1, 1], [2, 1], [2, 2], [1, 2]];

const polygon = (points: string[]) => points.join(' ');

const localPoint = (x: number, z: number) =>
  `${LOCAL_MAP.pad + x * LOCAL_MAP.cell},${LOCAL_MAP.pad + z * LOCAL_MAP.cell}`;

const worldPoint = (x: number, z: number) =>
  `${WORLD_MAP.pad + x * WORLD_MAP.cell},${WORLD_MAP.pad + z * WORLD_MAP.cell}`;

export function Workbench() {
  const { zh } = useLanguage();
  const [moment, setMoment] = useState<MomentId>('check');
  const [source, setSource] = useState<SourceId>('document');
  const [selectedBay, setSelectedBay] = useState<BayId | 'all'>('all');
  const [exploded, setExploded] = useState(false);
  const [cutaway, setCutaway] = useState(true);
  const [playing, setPlaying] = useState(false);
  const [hoverCell, setHoverCell] = useState<CampfireCell | null>(null);
  const [hoverBay, setHoverBay] = useState<BayId | null>(null);
  const [origin, setOrigin] = useState(SITING.origin);
  const [quarter, setQuarter] = useState<TurnQuarter>(SITING.quarter);

  const momentIndex = MOMENT_ORDER.indexOf(moment);
  const phaseIndex = PHASES.findIndex((entry) => entry.id === moment);
  const momentData = MOMENTS.find((entry) => entry.id === moment)!;
  const momentZh = zh ? ZH_MOMENTS[moment] : null;
  const focusBay = hoverCell?.bay ?? hoverBay ?? (selectedBay === 'all' ? null : selectedBay);
  const focusMeta = focusBay ? BAYS.find((entry) => entry.id === focusBay)! : null;
  const selectedMeta = selectedBay === 'all' ? null : BAYS.find((entry) => entry.id === selectedBay)!;
  const image = sitingImage(origin, quarter, SITING.fire);
  const turnMeta = QUARTER_TURNS[quarter];
  const readCount = focusMeta?.count ?? CAMPFIRE.cells;
  const phaseComplete = moment === 'derive' ? PHASES.length : phaseIndex + 1;

  useEffect(() => {
    if (!playing) return;
    const timer = window.setInterval(() => {
      setMoment((current) => {
        const index = MOMENT_ORDER.indexOf(current);
        return MOMENT_ORDER[(index + 1) % MOMENT_ORDER.length];
      });
    }, 2200);
    return () => window.clearInterval(timer);
  }, [playing]);

  const claims = useMemo(
    () => CAMPFIRE_CELLS.map((cell) => ({ ...cell, key: `${cell.x}-${cell.y}-${cell.z}` })),
    [],
  );

  const worldOf = (x: number, z: number): [number, number] => {
    const [ox, , oz] = orientPoint(x, 0, z, quarter);
    return [origin.x + ox, origin.z + oz];
  };

  const worldPolygon = (corners: [number, number][]) =>
    polygon(corners.map(([x, z]) => {
      const [wx, wz] = worldOf(x, z);
      return worldPoint(wx, wz);
    }));

  const worldGrid = [1, 2].map((index) => ({
    column: [worldOf(index, 0), worldOf(index, 3)],
    row: [worldOf(0, index), worldOf(3, index)],
  }));

  const pickMoment = (next: MomentId) => {
    setMoment(next);
    setPlaying(false);
  };

  const selectBay = (bay: BayId | 'all') => {
    setSelectedBay(bay);
    setHoverCell(null);
    setHoverBay(null);
    if (!exploded && bay !== 'all' && bay !== 'shell') setCutaway(true);
  };

  const hoverLegend = (bay: BayId | null) => {
    setHoverBay(bay);
    setHoverCell(null);
  };

  const moveOrigin = (axis: 'x' | 'z', delta: number) => {
    setOrigin((current) => ({ ...current, [axis]: clamp(current[axis] + delta, SITING.min, SITING.max) }));
  };

  const rotate = (delta: number) => {
    setQuarter((((quarter + delta) % 4 + 4) % 4) as TurnQuarter);
  };

  const resetSiting = () => {
    setOrigin(SITING.origin);
    setQuarter(SITING.quarter);
  };

  const toggleCutaway = () => {
    setCutaway((value) => {
      const next = !value;
      if (!next && (selectedBay === 'fire' || selectedBay === 'hollow')) setSelectedBay('all');
      return next;
    });
  };

  return (
    <section className="section" id="workbench" data-section>
      <SectionHead
        no="03"
        kicker={zh ? '完整示例 / 营火' : 'Worked example / campfire'}
        title={zh ? <>一次 attempt，<em>三个阶段</em>，然后 derive。</> : <>One attempt, <em>three phases</em>, then derive.</>}
        note={zh
          ? <>完整的 3×3×3 营火 document 放在 Java builder 摘录旁边。走 COMPILE、CHECK、SETTLE；settle 向 <code>StructureAttempt</code> 发布，<code>commit(bounds)</code> 形成带 bounds 的 <code>StructureInstance</code>（D-40）；derive 是作用在 <strong>Formed</strong> 上的又一次纯折叠，不是第四个阶段。</>
          : <>The complete 3×3×3 campfire document sits beside a Java builder excerpt. Walk COMPILE, CHECK and SETTLE; the settle publishes into a <code>StructureAttempt</code> and <code>commit(bounds)</code> forms the bounds-bearing <code>StructureInstance</code> (D-40); derive is one more pure fold on <strong>Formed</strong>, not a fourth phase.</>}
      />

      <Reveal>
        <div className="wb">
          <div className="wb-top">
            <div className="wb-chamber">
              <div className="wb-chamber-head">
                <div className="wb-chamber-id">
                  <span className="wb-fig">FIG. 03</span>
                  <strong>kernel.structure / campfire</strong>
                </div>
                <div className="wb-view">
                  <div className="wb-view-group" role="group" aria-label="Scene view">
                    <button type="button" data-on={!exploded} aria-pressed={!exploded} onClick={() => setExploded(false)}>
                      assembled
                    </button>
                    <button type="button" data-on={exploded} aria-pressed={exploded} onClick={() => setExploded(true)}>
                      exploded
                    </button>
                  </div>
                  <button
                    type="button"
                    className="wb-xray"
                    data-on={cutaway && !exploded}
                    data-off={!cutaway}
                    aria-pressed={cutaway}
                    disabled={exploded}
                    onClick={toggleCutaway}
                  >
                    x-ray
                  </button>
                </div>
              </div>

              <div className="wb-chamber-sub">
                <span>{zh ? '3×3×3 · 27 个 cell · shell 25 / hollow 1 / fire 1' : '3×3×3 · 27 cells · shell 25 / hollow 1 / fire 1'}</span>
                <span>
                  {exploded ? 'three layers / separated' : cutaway ? 'near octant / x-ray' : 'shell closed'}
                </span>
              </div>

              <div className="wb-viewport">
                <IsometricCampfire
                  selectedBay={selectedBay}
                  focusBay={focusBay}
                  hoverCell={hoverCell}
                  onHoverCell={(cell) => {
                    setHoverCell(cell);
                    if (cell) setHoverBay(null);
                  }}
                  onSelectBay={selectBay}
                  exploded={exploded}
                  cutaway={cutaway}
                />
              </div>

              <div className="wb-chamber-foot">
                <div className="wb-baybar" role="group" aria-label="Bays">
                  <button
                    type="button"
                    data-active={selectedBay === 'all'}
                    aria-pressed={selectedBay === 'all'}
                    onMouseEnter={() => hoverLegend(null)}
                    onClick={() => selectBay('all')}
                  >
                    <span className="wb-bay-swatch wb-swatch-all" />
                    <span className="wb-bay-name">{zh ? '全部 bays' : 'all bays'}</span>
                    <span className="wb-bay-count">27</span>
                  </button>
                  {BAYS.map((entry) => (
                    <button
                      key={entry.id}
                      type="button"
                      data-active={selectedBay === entry.id}
                      aria-pressed={selectedBay === entry.id}
                      onMouseEnter={() => hoverLegend(entry.id)}
                      onMouseLeave={() => hoverLegend(null)}
                      onFocus={() => hoverLegend(entry.id)}
                      onBlur={() => hoverLegend(null)}
                      onClick={() => selectBay(entry.id)}
                    >
                      <span className="wb-bay-swatch" data-bay={entry.id} />
                      <span className="wb-bay-name">{entry.label}</span>
                      <span className="wb-bay-count">{entry.count}</span>
                    </button>
                  ))}
                </div>

                <div className="wb-chamber-readrow">
                  <div className="wb-chamber-read">
                    <div className="wb-chamber-read-main">
                      <span className="wb-k">{hoverCell ? 'cell' : focusBay ? 'bay' : 'frame'}</span>
                      <span className="wb-v">
                        {hoverCell ? `(${hoverCell.x},${hoverCell.y},${hoverCell.z})` : focusBay ? focusMeta!.label : 'local'}
                      </span>
                      <span className="wb-k">{hoverCell ? 'bay' : focusMeta ? 'coverage' : 'origin'}</span>
                      <span className="wb-v">
                        {hoverCell ? hoverCell.bay : focusMeta ? focusMeta.coverage : '0,0,0'}
                      </span>
                    </div>
                    <p>
                      {hoverCell
                        ? (zh ? `${hoverCell.bay} cell — 点击选中其 bay` : `${hoverCell.bay} cell — click to select the bay`)
                        : focusMeta
                          ? (zh ? `${ZH_BAYS[focusMeta.id].note} · ${focusMeta.footprint} footprint` : `${focusMeta.note} · ${focusMeta.footprint} footprint`)
                          : exploded
                            ? (zh ? '三个 y 层 · fire cell 从炉膛里拉出' : 'three y layers · the fire cell is pulled from the hearth')
                            : (zh ? '悬停 cell · 点击保留其 bay · frame 留在 local' : 'hover a cell · click to hold its bay · frame stays local')}
                    </p>
                  </div>
                  <div className="wb-chamber-count">
                    <span className="wb-n">{readCount}</span>
                    <span className="wb-l">{zh ? '个 cell' : readCount === 1 ? 'cell' : 'cells'}</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="wb-attempt">
              <div className="wb-attempt-head">
                <span className="wb-kicker">{zh ? 'attempt / 一个 request' : 'attempt / one request'}</span>
                <span className="wb-attempt-id">#04 · campfire</span>
              </div>

              <div className="wb-phases" role="group" aria-label={zh ? 'attempt 阶段' : 'Attempt phases'}>
                {PHASES.map((entry, index) => (
                  <button
                    key={entry.id}
                    type="button"
                    className="wb-phase"
                    aria-pressed={entry.id === moment}
                    data-active={entry.id === moment}
                    data-done={moment === 'derive' || index < phaseIndex}
                    onClick={() => pickMoment(entry.id)}
                  >
                    <span className="wb-phase-no">{entry.no}</span>
                    <span className="wb-phase-label">{entry.label}</span>
                  </button>
                ))}
              </div>

              <button
                type="button"
                className="wb-aftermath"
                data-active={moment === 'derive'}
                aria-pressed={moment === 'derive'}
                onClick={() => pickMoment('derive')}
              >
                <span className="wb-aftermath-mark" aria-hidden="true">↳</span>
                <span className="wb-aftermath-label">{zh ? 'derive（后续）' : 'derive'}</span>
                <span className="wb-aftermath-note">{zh ? 'Formed 之后 · 再一次纯折叠' : 'after Formed · one more pure fold'}</span>
                <span className="wb-aftermath-rev">context rev 2</span>
              </button>

              <div className="wb-moment-copy">
                <div className="wb-moment-copy-top">
                  <span className="wb-kicker">
                    {momentData.kind === 'phase'
                      ? (zh ? `阶段 ${momentData.no} / ${momentData.label}` : `PHASE ${momentData.no} / ${momentData.label}`)
                      : (zh ? 'FORMED 之后 / DERIVE' : 'AFTER FORMED / DERIVE')}
                  </span>
                  <span className="wb-tag">{momentData.result}</span>
                </div>
                <h3>{momentZh?.title ?? momentData.title}</h3>
                <p>{momentZh?.note ?? momentData.note}</p>
              </div>

              <div className="wb-readout">
                {momentData.readout.map(([key, value]) => {
                  const shown = momentZh?.readout[key] ?? value;
                  return (
                    <div key={key}>
                      <span className="wb-k">{key}</span>
                      <span
                        className="wb-v"
                        data-ok={value === 'match' || value === 'same' || value === 'Formed' || value === 'Admitted' || value === '10'}
                      >
                        {shown}
                      </span>
                    </div>
                  );
                })}
              </div>

              <div className="wb-log">
                <div className="wb-section-head">
                  <span>{zh ? 'attempt 日志' : 'attempt log'}</span>
                  <span>{zh ? '三个阶段 · 一次 derive' : 'three phases · one derive'}</span>
                </div>
                {MOMENTS.map((entry, index) => {
                  const state = index < momentIndex ? 'done' : index === momentIndex ? 'live' : 'next';
                  return (
                    <button
                      key={entry.id}
                      type="button"
                      className="wb-log-row"
                      data-state={entry.kind === 'aftermath' && state !== 'live' ? 'after' : state}
                      data-kind={entry.kind}
                      aria-current={entry.id === moment ? 'step' : undefined}
                      onClick={() => pickMoment(entry.id)}
                    >
                      <span className="wb-log-rail" aria-hidden="true">
                        <i />
                      </span>
                      <span className="wb-log-main">
                        <span className="wb-log-line">
                          <span className="wb-log-phase">
                            <span className="wb-log-no">{entry.no}</span>
                            {entry.label}
                          </span>
                          <span className="wb-log-out">{entry.result}</span>
                        </span>
                        <span className="wb-log-meta">
                          <span>{entry.ledger === '—' ? 'ledger —' : `ledger ${entry.ledger}`}</span>
                          <span>{entry.context === '—' ? 'context —' : `context ${entry.context}`}</span>
                          <span>{entry.writes}</span>
                        </span>
                        <span className="wb-log-note">{zh ? ZH_MOMENTS[entry.id].log : entry.log}</span>
                      </span>
                    </button>
                  );
                })}
              </div>

              <div className="wb-invariants">
                <div className="wb-section-head">
                  <span>{zh ? '不变量' : 'invariants'}</span>
                  <span>{zh ? '贯穿整个 attempt' : 'held across the attempt'}</span>
                </div>
                {INVARIANTS.map((entry) => {
                  const live = entry.moments.includes(moment);
                  return (
                    <div key={entry.id} className="wb-invariant" data-live={live}>
                      <span className="wb-inv-no">{entry.no}</span>
                      <div className="wb-inv-body">
                        <div className="wb-inv-title">
                          {zh ? ZH_INVARIANTS[entry.id].title : entry.title}
                          <span className="wb-inv-code">{entry.code}</span>
                        </div>
                        <p>{zh ? ZH_INVARIANTS[entry.id].detail : entry.detail}</p>
                      </div>
                      <span className="wb-inv-mark" aria-hidden="true">✓</span>
                    </div>
                  );
                })}
              </div>

              <div className="wb-attempt-foot">
                <button type="button" className="wb-run" onClick={() => setPlaying((value) => !value)}>
                  {zh ? (playing ? '暂停 attempt' : '运行 attempt') : (playing ? 'pause attempt' : 'run attempt')}
                  <span aria-hidden="true">{playing ? 'Ⅱ' : '▶'}</span>
                </button>
                <div className="wb-progress">
                  <div className="wb-phase-dots" aria-hidden="true">
                    {PHASES.map((entry, index) => (
                      <span
                        key={entry.id}
                        data-on={phaseComplete > index}
                        data-live={moment === entry.id}
                      />
                    ))}
                  </div>
                  <span className="wb-progress-label">
                    {moment === 'derive'
                      ? `after settle · ${momentData.result}`
                      : (zh ? `阶段 ${phaseIndex + 1} / 3 · ${momentData.result}` : `phase ${phaseIndex + 1} / 3 · ${momentData.result}`)}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="wb-siting">
            <div className="wb-siting-copy">
              <div className="wb-kicker">{zh ? 'siting / frame · D-22' : 'siting / frame · D-22'}</div>
              <h3>{zh ? ZH_SITING.title : 'The blueprint stays local.'}</h3>
              <p>
                {zh ? <>Bays、checks 和 computations 都是 local cells。每个 focus、section、aggregate 参数都会在读取前由 request 的 <code>Siting</code> 映射一次，所以 document 从不携带 world 坐标。capture 是 world frame 的一份不可变快照（D-33）：<code>property</code> / <code>state</code> check 通过 <code>HostType</code> 读取捕获值，descriptor 仍是 <code>(type, kind)</code>。</> : <>Bays, checks and computations are local cells. Every focus, section and aggregate argument is mapped once by the request&rsquo;s <code>Siting</code> before the read, so the document never carries a world coordinate. The capture is one immutable world-frame snapshot (D-33): a <code>property</code> or <code>state</code> check reads the captured value through a <code>HostType</code>, and the descriptor stays <code>(type, kind)</code>.</>}
              </p>
              <div className="wb-siting-tags">
                <span>{zh ? 'local 固定' : 'local fixed'}</span>
                <span>{zh ? 'world 图' : 'world image'}</span>
                <span>{zh ? '每次读取映射一次' : 'one mapping per read'}</span>
                <span>{zh ? '一份类型化快照' : 'one typed snapshot'}</span>
              </div>
            </div>

            <div className="wb-siting-map">
              <div className="wb-map-cell">
                <svg viewBox="0 0 116 116" role="img" aria-label={zh ? '固定的 local 3×3 footprint 与 fire cell' : 'Fixed local 3 by 3 footprint with the fire cell'}>
                  {[0, 1, 2, 3].map((index) => (
                    <g key={index} className="wb-local-grid">
                      <line x1={LOCAL_MAP.pad + index * LOCAL_MAP.cell} y1={LOCAL_MAP.pad} x2={LOCAL_MAP.pad + index * LOCAL_MAP.cell} y2={LOCAL_MAP.pad + 3 * LOCAL_MAP.cell} />
                      <line x1={LOCAL_MAP.pad} y1={LOCAL_MAP.pad + index * LOCAL_MAP.cell} x2={LOCAL_MAP.pad + 3 * LOCAL_MAP.cell} y2={LOCAL_MAP.pad + index * LOCAL_MAP.cell} />
                    </g>
                  ))}
                  <polygon points={polygon(FOOT_CORNERS.map(([x, z]) => localPoint(x, z)))} className="wb-local-edge" />
                  <polygon points={polygon(FIRE_CORNERS.map(([x, z]) => localPoint(x, z)))} className="wb-local-fire" />
                  <path d="M16 16 L108 16 M103 13 L108 16 L103 19" className="wb-map-axis" />
                  <path d="M16 16 L16 108 M13 103 L16 108 L19 103" className="wb-map-axis" />
                  <text x="110" y="11" textAnchor="end" className="wb-map-cap">X</text>
                  <text x="21" y="114" className="wb-map-cap">Z</text>
                  <circle cx={LOCAL_MAP.pad} cy={LOCAL_MAP.pad} r="2.6" className="wb-map-origin" />
                </svg>
                <span className="wb-map-caption">{zh ? 'local · 固定' : 'local · fixed'}</span>
              </div>

              <div className="wb-map-cell">
                <svg viewBox="0 0 150 150" role="img" aria-label={zh ? `营火的 world 图：origin ${origin.x},${origin.z}；fire 在 world ${image.x},${image.z}` : `World image of the campfire at origin ${origin.x},${origin.z}; fire at world ${image.x},${image.z}`}>
                  {Array.from({ length: WORLD_MAP.span + 1 }, (_, index) => (
                    <g key={index} className="wb-world-grid">
                      <line x1={WORLD_MAP.pad + index * WORLD_MAP.cell} y1={WORLD_MAP.pad} x2={WORLD_MAP.pad + index * WORLD_MAP.cell} y2={WORLD_MAP.pad + WORLD_MAP.span * WORLD_MAP.cell} />
                      <line x1={WORLD_MAP.pad} y1={WORLD_MAP.pad + index * WORLD_MAP.cell} x2={WORLD_MAP.pad + WORLD_MAP.span * WORLD_MAP.cell} y2={WORLD_MAP.pad + index * WORLD_MAP.cell} />
                    </g>
                  ))}
                  <polygon points={worldPolygon(FOOT_CORNERS)} className="wb-world-image" />
                  {worldGrid.map((line, index) => (
                    <g key={index} className="wb-world-grid-fine">
                      <line
                        x1={WORLD_MAP.pad + line.column[0][0] * WORLD_MAP.cell}
                        y1={WORLD_MAP.pad + line.column[0][1] * WORLD_MAP.cell}
                        x2={WORLD_MAP.pad + line.column[1][0] * WORLD_MAP.cell}
                        y2={WORLD_MAP.pad + line.column[1][1] * WORLD_MAP.cell}
                      />
                      <line
                        x1={WORLD_MAP.pad + line.row[0][0] * WORLD_MAP.cell}
                        y1={WORLD_MAP.pad + line.row[0][1] * WORLD_MAP.cell}
                        x2={WORLD_MAP.pad + line.row[1][0] * WORLD_MAP.cell}
                        y2={WORLD_MAP.pad + line.row[1][1] * WORLD_MAP.cell}
                      />
                    </g>
                  ))}
                  <polygon points={worldPolygon(FIRE_CORNERS)} className="wb-world-fire" />
                  <path d="M8 8 L142 8 M137 5 L142 8 L137 11" className="wb-map-axis" />
                  <path d="M8 8 L8 142 M5 137 L8 142 L11 137" className="wb-map-axis" />
                  <text x="144" y="5" textAnchor="end" className="wb-map-cap">X</text>
                  <text x="13" y="148" className="wb-map-cap">Z</text>
                  <circle cx={WORLD_MAP.pad + origin.x * WORLD_MAP.cell} cy={WORLD_MAP.pad + origin.z * WORLD_MAP.cell} r="2.6" className="wb-map-origin" />
                  <circle cx={WORLD_MAP.pad + origin.x * WORLD_MAP.cell} cy={WORLD_MAP.pad + origin.z * WORLD_MAP.cell} r="0.9" className="wb-map-origin-core" />
                </svg>
                <span className="wb-map-caption">{zh ? 'world · 图' : 'world · image'}</span>
              </div>
            </div>

            <div className="wb-siting-controls">
              <div className="wb-control-row">
                <span className="wb-k">{zh ? 'origin X' : 'origin x'}</span>
                <div className="wb-stepper">
                  <button type="button" aria-label={zh ? '减小 origin X' : 'decrease origin x'} onClick={() => moveOrigin('x', -1)}>−</button>
                  <b>{origin.x}</b>
                  <button type="button" aria-label={zh ? '增大 origin X' : 'increase origin x'} onClick={() => moveOrigin('x', 1)}>+</button>
                </div>
                <span className="wb-k">{zh ? 'origin Z' : 'origin z'}</span>
                <div className="wb-stepper">
                  <button type="button" aria-label={zh ? '减小 origin Z' : 'decrease origin z'} onClick={() => moveOrigin('z', -1)}>−</button>
                  <b>{origin.z}</b>
                  <button type="button" aria-label={zh ? '增大 origin Z' : 'increase origin z'} onClick={() => moveOrigin('z', 1)}>+</button>
                </div>
              </div>

              <div className="wb-control-row">
                <span className="wb-k">{zh ? '90° 转向' : 'quarter turn'}</span>
                <div className="wb-turn">
                  <button type="button" aria-label={zh ? '逆时针转 90°' : 'turn counterclockwise 90 degrees'} onClick={() => rotate(-1)}>↺</button>
                  <b>{turnMeta.label}</b>
                  <button type="button" aria-label={zh ? '顺时针转 90°' : 'turn clockwise 90 degrees'} onClick={() => rotate(1)}>↻</button>
                </div>
                <span className="wb-turn-name">{turnMeta.turn}</span>
                <button type="button" className="wb-reset" onClick={resetSiting}>{zh ? '重置' : 'reset'}</button>
              </div>

              <div className="wb-world-line" aria-live="polite">
                <span className="wb-world-local">LOCAL (1,0,1)</span>
                <span className="wb-world-arrow" aria-hidden="true">→</span>
                <strong className="wb-world">WORLD ({image.x},{image.y},{image.z})</strong>
                <span className="wb-world-turns">{quarter} / 4</span>
              </div>
            </div>

            <aside className="wb-siting-card">
              <div className="wb-card-head">
                <span>SITING</span>
                <span>value</span>
              </div>
              <dl>
                <div><dt>{zh ? 'origin' : 'origin'}</dt><dd>({origin.x}, {origin.y}, {origin.z})</dd></div>
                <div><dt>{zh ? 'placement' : 'placement'}</dt><dd>{turnMeta.turn}</dd></div>
                <div><dt>{zh ? 'quarter turns' : 'quarter turns'}</dt><dd>{quarter} / 4</dd></div>
                <div><dt>{zh ? 'world fire' : 'world fire'}</dt><dd>({image.x}, {image.y}, {image.z})</dd></div>
                <div><dt>{zh ? 'mapping' : 'mapping'}</dt><dd>origin + o(local)</dd></div>
                <div><dt>{zh ? 'host read' : 'host read'}</dt><dd>HostType&lt;H&gt;</dd></div>
                <div><dt>{zh ? 'descriptor' : 'descriptor'}</dt><dd>(type, kind)</dd></div>
              </dl>
              <p>{zh ? '仿射、每次读取检查一次；只有 image 移动。' : 'Affine and checked once per read; only the image moves.'}</p>
            </aside>
          </div>

          <div className="wb-bottom">
            <div className="wb-source">
              <div className="wb-source-head">
                <div className="wb-source-title">
                  <span className="wb-kicker">{zh ? '声明' : 'the declaration'}</span>
                  <span className="wb-source-sub">{zh ? 'document 完整 · java 摘录' : 'document complete · java excerpt'}</span>
                </div>
                <div className="wb-source-tabs">
                  <button type="button" data-on={source === 'document'} onClick={() => setSource('document')}>document</button>
                  <button type="button" data-on={source === 'java'} onClick={() => setSource('java')}>java</button>
                </div>
              </div>
              <CodeBlock
                className="wb-source-code"
                code={source === 'document' ? DOCUMENT_SNIPPET : JAVA_SNIPPET}
                language={source === 'document' ? 'json' : 'java'}
              />
              <div className="wb-source-foot">
                <span>{zh ? '3 个 bay · 3 个 check · 1 个 computation' : '3 bays · 3 checks · 1 computation'}</span>
                <span>{zh ? 'property 是 adapter 的 host leaf · descriptor 是 (type, kind)' : 'property is an adapter host leaf · descriptor is (type, kind)'}</span>
              </div>
            </div>

            <aside className="wb-aside">
              <div className="wb-aside-claims">
              <div className="wb-section-head">
                <span>{zh ? 'claim 预测' : 'claim prediction'}</span>
                <span>{zh ? '预测，不写入' : 'predicted, not written'}</span>
              </div>
              <div className="wb-claim-total">
                <strong>{selectedMeta ? selectedMeta.count : CAMPFIRE.claims}</strong>
                <span>{selectedMeta ? (zh ? `${selectedMeta.label} 预测行` : `${selectedMeta.label} rows predicted`) : (zh ? '预测行 · exclusive' : 'predicted rows · exclusive')}</span>
              </div>
              <div className="wb-claim-layers">
                {[0, 1, 2].map((layer) => (
                  <div className="wb-claim-layer" key={layer}>
                    <span className="wb-layer-label">y {layer}</span>
                    <div className="wb-claim-grid">
                      {claims.filter((cell) => cell.y === layer).map((cell) => (
                        <span
                          key={cell.key}
                          className="wb-claim"
                          data-bay={cell.bay}
                          data-dim={focusBay !== null && cell.bay !== focusBay}
                          title={`${cell.bay} (${cell.x},${cell.y},${cell.z})`}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
              <div className="wb-legend">
                <span><i data-bay="shell" />shell 25</span>
                <span><i data-bay="hollow" />hollow 1</span>
                <span><i data-bay="fire" />fire 1</span>
                <span className="wb-legend-frame">{zh ? 'x → · z ↓ 每层' : 'x → · z ↓ per layer'}</span>
              </div>
              </div>
              <div className="wb-aside-checks">
              <div className="wb-hairline" />
              <div className="wb-section-head">
                <span>{zh ? 'checks' : 'checks'}</span>
                <span>{zh ? '3 / 3 匹配' : '3 / 3 matched'}</span>
              </div>
              <div className="wb-checks">
                {CHECKS.map((check) => (
                  <div className="wb-check" key={check.id}>
                    <span className="wb-check-dot" data-bay={check.bay} />
                    <b>{check.id}</b>
                    <span className="wb-check-focus">{check.focus}</span>
                    <span className="wb-check-expect">{check.expect}</span>
                  </div>
                ))}
              </div>
              <div className="wb-hairline" />
              <dl className="wb-aside-facts">
                <div><dt>{zh ? 'sharing' : 'sharing'}</dt><dd>EXCLUSIVE · 3 bays</dd></div>
                <div><dt>{zh ? 'writes' : 'writes'}</dt><dd>{zh ? 'settle 前为 0' : '0 until settle'}</dd></div>
                <div><dt>{zh ? 'publication' : 'publication'}</dt><dd>{zh ? 'settle 一次 · derive 一次' : 'settle once · derive once'}</dd></div>
              </dl>
              </div>
            </aside>
          </div>
        </div>
      </Reveal>

      <div className="wb-pipe">
        <div className="wb-pipe-lead">
          <span className="wb-kicker">{zh ? 'pipeline / 一个 request' : 'pipeline / one request'}</span>
          <strong>{zh ? '一个 lease · 一个 spend · 一次 derive' : 'one lease · one spend · one derive'}</strong>
        </div>
        {PHASES.map((entry, index) => {
          const state = index < momentIndex ? 'done' : index === momentIndex ? 'live' : 'next';
          return (
            <button
              key={entry.id}
              type="button"
              className="wb-pipe-stop"
              data-state={state}
              onClick={() => pickMoment(entry.id)}
            >
              <span className="wb-pipe-node" aria-hidden="true" />
              <span className="wb-pipe-no">{entry.no}</span>
              <span className="wb-pipe-label">{entry.label}</span>
              <span className="wb-pipe-out">{entry.result}</span>
              <span className="wb-pipe-meta">
                {entry.writes}
                <span className="wb-dot">·</span>
                {entry.ledger === '—' ? 'ledger —' : `ledger ${entry.ledger}`}
              </span>
            </button>
          );
        })}
        <button
          type="button"
          className="wb-pipe-stop wb-pipe-aftermath"
          data-state={moment === 'derive' ? 'live' : 'after'}
          onClick={() => pickMoment('derive')}
        >
          <span className="wb-pipe-node" aria-hidden="true">↳</span>
          <span className="wb-pipe-no">{zh ? 'Formed 之后' : 'after Formed'}</span>
          <span className="wb-pipe-label">DERIVE</span>
          <span className="wb-pipe-out">Context</span>
          <span className="wb-pipe-meta">{zh ? '一个 batch · context rev 2' : 'one batch · context rev 2'}</span>
        </button>
      </div>

      <Reveal delay={80}>
        <div className="wb-furnace">
          <div className="wb-furnace-id">
            <span className="wb-kicker">{zh ? '第二个样本' : 'second specimen'} / {FURNACE.bounds}</span>
            <h3>{zh ? ZH_FURNACE.title : FURNACE.title}</h3>
            <code>{FURNACE.id}</code>
            <p>{zh ? ZH_FURNACE.note : FURNACE.note}</p>
          </div>
          <div className="wb-furnace-bars">
            <div className="wb-furnace-head">
              <span>{zh ? 'bay' : 'bay'}</span>
              <span>{zh ? 'cells' : 'cells'}</span>
              <span>{zh ? 'coverage / footprint' : 'coverage / footprint'}</span>
            </div>
            {FURNACE.bays.map((entry) => (
              <div className="wb-furnace-bar" data-kind={entry.id} key={entry.id}>
                <span className="wb-furnace-name">{entry.id}</span>
                <span className="wb-track">
                  <span
                    className="wb-fill"
                    style={{ width: `${(entry.cells / FURNACE.claims) * 100}%` }}
                  />
                </span>
                <span className="wb-furnace-cells">{entry.cells}</span>
                <span className="wb-furnace-tags">{entry.coverage} / {entry.footprint}</span>
              </div>
            ))}
            <div className="wb-furnace-sum">
              <span>192 + 100 + 2 = 294</span>
              <span>{zh ? 'exclusive · 每个 owned cell 一行' : 'exclusive · one row per owned cell'}</span>
            </div>
          </div>
          <div className="wb-furnace-total">
            <span className="wb-furnace-n">{FURNACE.claims}</span>
            <span className="wb-furnace-l">{zh ? 'claim 行' : 'claim rows'}</span>
            <span className="wb-furnace-note">= {FURNACE.bounds} volume</span>
            <span className="wb-furnace-note">{zh ? '0 重叠' : '0 overlap'}</span>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
