import { useMemo, useState } from 'react';
import { CodeBlock, Reveal, SectionHead, Segmented } from '../components/ui';

type Sharing = 'EXCLUSIVE' | 'SHARED';
type Owner = 'A' | 'B';

const CELLS = [0, 1, 2, 3, 4, 5];
const WIDTH = 2;

const SNIPPET = `public static boolean conflicts(ClaimRow a, ClaimRow b) {
    if (!a.cell().equals(b.cell())) return false;
    if (a.owner().equals(b.owner())
            && a.siting().equals(b.siting())) return false;
    return a.sharing().blocks() || b.sharing().blocks();
}`;

export function Ledger() {
  const [startA, setStartA] = useState(0);
  const [startB, setStartB] = useState(3);
  const [sharingA, setSharingA] = useState<Sharing>('EXCLUSIVE');
  const [sharingB, setSharingB] = useState<Sharing>('SHARED');
  const [settled, setSettled] = useState(false);
  const [raced, setRaced] = useState(false);

  const cellsA = useMemo(() => new Set(Array.from({ length: WIDTH }, (_, index) => startA + index).filter((cell) => CELLS.includes(cell))), [startA]);
  const cellsB = useMemo(() => new Set(Array.from({ length: WIDTH }, (_, index) => startB + index).filter((cell) => CELLS.includes(cell))), [startB]);
  const contested = CELLS.filter((cell) => cellsA.has(cell) && cellsB.has(cell));
  const conflict = contested.length > 0 && (sharingA === 'EXCLUSIVE' || sharingB === 'EXCLUSIVE');
  const result = settled ? (conflict ? 'Failed' : 'Formed') : 'Admitted?';
  const rows = cellsA.size + cellsB.size;

  const setOwner = (owner: Owner, delta: number) => {
    if (owner === 'A') setStartA((value) => Math.max(0, Math.min(CELLS.length - WIDTH, value + delta)));
    else setStartB((value) => Math.max(0, Math.min(CELLS.length - WIDTH, value + delta)));
    setSettled(false);
    setRaced(false);
  };

  const settle = () => {
    setSettled(true);
    setRaced(false);
  };

  return (
    <section className="section" id="ledger" data-section>
      <SectionHead
        no="04"
        kicker="Claims / the write path"
        title={<>A row is a cell. <em>A plan is not a type.</em></>}
        note={<>Move the two bays and change their sharing. The matrix is the only conflict rule; the ledger compares one revision and writes once.</>}
      />

      <Reveal>
        <div className="ledger-layout">
          <div className="ledger-main">
            <div className="ledger-legend">
              <span><i style={{ background: 'var(--accent)' }} />owner A</span>
              <span><i style={{ background: 'var(--blue)' }} />owner B</span>
              <span><i style={{ border: '1px solid var(--accent)', background: 'transparent' }} />contested</span>
              <span className="muted">one row per owner × cell × bay</span>
            </div>

            <div className="cell-strip" aria-label="Six-cell claim strip">
              {CELLS.map((cell) => {
                const inA = cellsA.has(cell);
                const inB = cellsB.has(cell);
                const both = inA && inB;
                return (
                  <div className="strip-cell" key={cell} data-conflict={settled && both && conflict}>
                    <span className="idx">CELL {cell}</span>
                    <span className="marks">
                      {inA ? <i style={{ background: 'var(--accent)' }} title="owner A" /> : null}
                      {inB ? <i style={{ background: 'var(--blue)' }} title="owner B" /> : null}
                    </span>
                    <span className="mono" style={{ color: both ? 'var(--accent-ink)' : 'var(--faint)', fontSize: 7 }}>
                      {both ? 'both' : inA ? 'A' : inB ? 'B' : 'free'}
                    </span>
                  </div>
                );
              })}
            </div>

            <div className="owner-controls">
              <div className="owner-row">
                <span className="owner-name"><i style={{ background: 'var(--accent)' }} /> OWNER A</span>
                <Segmented
                  value={sharingA}
                  options={[{ value: 'EXCLUSIVE', label: 'exclusive' }, { value: 'SHARED', label: 'shared' }]}
                  onChange={(value) => { setSharingA(value); setSettled(false); }}
                  label="Owner A sharing"
                />
                <div className="stepper">
                  <button type="button" className="step-btn" onClick={() => setOwner('A', -1)} aria-label="Move owner A left">−</button>
                  <b>{startA}</b>
                  <button type="button" className="step-btn" onClick={() => setOwner('A', 1)} aria-label="Move owner A right">+</button>
                </div>
              </div>
              <div className="owner-row">
                <span className="owner-name"><i style={{ background: 'var(--blue)' }} /> OWNER B</span>
                <Segmented
                  value={sharingB}
                  options={[{ value: 'EXCLUSIVE', label: 'exclusive' }, { value: 'SHARED', label: 'shared' }]}
                  onChange={(value) => { setSharingB(value); setSettled(false); }}
                  label="Owner B sharing"
                />
                <div className="stepper">
                  <button type="button" className="step-btn" onClick={() => setOwner('B', -1)} aria-label="Move owner B left">−</button>
                  <b>{startB}</b>
                  <button type="button" className="step-btn" onClick={() => setOwner('B', 1)} aria-label="Move owner B right">+</button>
                </div>
              </div>
            </div>

            <div className="ledger-actions">
              <button type="button" className="btn btn-solid" onClick={settle}>settle at revision 0</button>
              <button type="button" className="btn btn-ghost" onClick={() => { setRaced(true); setSettled(false); }}>bump revision (race)</button>
              <button type="button" className="btn btn-ghost" onClick={() => { setStartA(0); setStartB(3); setSharingA('EXCLUSIVE'); setSharingB('SHARED'); setSettled(false); setRaced(false); }}>reset</button>
              <span className="mono muted" style={{ marginLeft: 'auto', alignSelf: 'center' }}>
                {raced ? 'retry expected 0, actual 1' : 'expected revision 0'}
              </span>
            </div>

            <div className="ledger-result">
              <div className="ledger-result-head">
                <span>ClaimPlanner.derive → SettleRequest</span>
                <span className={conflict ? 'tag tag-accent' : 'tag tag-green'}>{result}</span>
              </div>
              <div className="ledger-result-body">
                <div>
                  <div className="v">{rows}</div>
                  <div className="k">rows in the request</div>
                </div>
                <div>
                  <div className="v" style={{ color: conflict ? 'var(--accent)' : 'var(--green)' }}>{conflict ? 'yes' : 'no'}</div>
                  <div className="k">ClaimMatrix.conflicts</div>
                </div>
              </div>
            </div>
          </div>

          <aside className="ledger-side">
            <div className="eyebrow">The ledger is a port</div>
            <h4 style={{ fontFamily: 'var(--serif)', fontWeight: 400, fontSize: 27, letterSpacing: '-.03em', marginTop: 7 }}>
              Admitted has bindings, not rows.
            </h4>
            <p style={{ marginTop: 10, fontSize: 11, lineHeight: 1.55, color: 'var(--muted)' }}>
              The check predicts the rows for the conflict report, then forgets them. Settle re-derives from the
              bindings and the revision it read. A moved revision gets one fresh read and one retry.
            </p>
            <div className="matrix" style={{ marginTop: 16 }}>
              <div className="head">conflicts</div>
              <div className="head">A</div>
              <div className="head">B</div>
              <div>A</div>
              <div className="no">no</div>
              <div className={conflict ? 'yes' : 'no'}>{conflict ? 'yes' : 'no'}</div>
              <div>B</div>
              <div className={conflict ? 'yes' : 'no'}>{conflict ? 'yes' : 'no'}</div>
              <div className="no">no</div>
            </div>
            <div className="key-stack" style={{ marginTop: 16 }}>
              <div><span className="k">settle</span><span className="v">{raced ? 'RevisionMoved → re-read' : conflict ? 'Conflict' : 'Settled'}</span></div>
              <div><span className="k">ledger</span><span className="v">{settled ? 'rev 1' : 'rev 0'}</span></div>
              <div><span className="k">context</span><span className="v">{settled && !conflict ? 'rev 1' : '—'}</span></div>
              <div><span className="k">rows</span><span className="v">{settled && !conflict ? rows : 'not written'}</span></div>
            </div>
            <div className="ledger-code">
              <div className="eyebrow" style={{ marginBottom: 8 }}>The only predicate</div>
              <CodeBlock code={SNIPPET} language="java" />
            </div>
            <div className="figure-label">
              <span>Fig. 04 — two owners, one cell</span>
              <span>{contested.length} contested</span>
            </div>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
