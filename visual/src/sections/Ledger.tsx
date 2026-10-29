import { useMemo, useState } from 'react';
import { CodeBlock, Reveal, SectionHead, Segmented } from '../components/ui';
import { useLanguage } from '../i18n/Language';

type Sharing = 'EXCLUSIVE' | 'SHARED';
type Owner = 'A' | 'B';
type Attempt = 'ready' | 'stale' | 'moved' | 'conflict' | 'formed';

const CELLS = [0, 1, 2, 3, 4, 5];
const WIDTH = 2;

const SNIPPET = `public static boolean conflicts(ClaimRow a, ClaimRow b) {
    if (!a.cell().equals(b.cell())) return false;
    if (a.owner().equals(b.owner())
            && a.siting().equals(b.siting())) return false;
    return a.sharing().blocks() || b.sharing().blocks();
}`;

export function Ledger() {
  const { zh } = useLanguage();
  const [startA, setStartA] = useState(0);
  const [startB, setStartB] = useState(1);
  const [sharingA, setSharingA] = useState<Sharing>('EXCLUSIVE');
  const [sharingB, setSharingB] = useState<Sharing>('SHARED');
  const [expected, setExpected] = useState(0);
  const [ledger, setLedger] = useState(0);
  const [attempt, setAttempt] = useState<Attempt>('ready');

  const cellsA = useMemo(() => new Set(Array.from({ length: WIDTH }, (_, index) => startA + index).filter((cell) => CELLS.includes(cell))), [startA]);
  const cellsB = useMemo(() => new Set(Array.from({ length: WIDTH }, (_, index) => startB + index).filter((cell) => CELLS.includes(cell))), [startB]);
  const contested = CELLS.filter((cell) => cellsA.has(cell) && cellsB.has(cell));
  const conflict = contested.length > 0 && (sharingA === 'EXCLUSIVE' || sharingB === 'EXCLUSIVE');
  const result = attempt === 'formed' ? 'Formed'
    : attempt === 'conflict' ? 'Failed'
      : attempt === 'moved' ? 'RevisionMoved'
        : attempt === 'stale' ? 'Retry' : 'Ready';
  const rows = cellsA.size + cellsB.size;
  const settled = attempt === 'formed';

  const setOwner = (owner: Owner, delta: number) => {
    if (owner === 'A') setStartA((value) => Math.max(0, Math.min(CELLS.length - WIDTH, value + delta)));
    else setStartB((value) => Math.max(0, Math.min(CELLS.length - WIDTH, value + delta)));
    setExpected(ledger);
    setAttempt('ready');
  };

  const settle = () => {
    if (expected !== ledger) {
      setAttempt('moved');
      return;
    }
    if (conflict) {
      setAttempt('conflict');
      return;
    }
    setAttempt('formed');
    setLedger(expected + 1);
  };

  const bump = () => {
    setLedger((value) => value + 1);
    setAttempt('stale');
  };

  const retry = () => {
    setExpected(ledger);
    setAttempt('ready');
  };

  return (
    <section className="section" id="ledger" data-section>
      <SectionHead
        no="04"
        kicker={zh ? 'claim / 写入路径' : 'Claims / the write path'}
        title={zh ? <>一行就是一个 cell。<em>plan 不是类型。</em></> : <>A row is a cell. <em>A plan is not a type.</em></>}
        note={zh ? <>移动两个 bay、改变 sharing。矩阵是唯一的冲突规则；ledger 只比较一次 revision，只写一次。结构集合（D-32）增加的是 host 编排和 S5 的 set 命令，不是 kernel 事务；一次 union capture 不带 orientation、不做 orientation audit，所以 satellite cell 不会收到多余的 <code>SC-MC-0004</code>；S6 给 set 一个共享 budget，并让 rollback 通过 <code>ContextStore.remove</code> 失效 context（D-35）。</> : <>Move the two bays and change their sharing. The matrix is the only conflict rule; the ledger compares one revision and writes once. Structure sets (D-32) add host orchestration and the S5 set command, not a kernel transaction; the one union capture is unsited and runs no orientation audit, so a satellite cell gets no spurious <code>SC-MC-0004</code>; S6 gives the set one shared budget and lets rollback invalidate contexts through <code>ContextStore.remove</code> (D-35).</>}
      />

      <Reveal>
        <div className="ledger-layout">
          <div className="ledger-main">
            <div className="ledger-legend">
              <span><i style={{ background: 'var(--accent)' }} />owner A</span>
              <span><i style={{ background: 'var(--blue)' }} />owner B</span>
              <span><i style={{ border: '1px solid var(--accent)', background: 'transparent' }} />{zh ? '争议' : 'contested'}</span>
              <span className="muted">{zh ? '每个 owner × cell × siting × bay 一行' : 'one row per owner × cell × siting × bay'}</span>
            </div>

            <div className="cell-strip" aria-label="Six-cell claim strip">
              {CELLS.map((cell) => {
                const inA = cellsA.has(cell);
                const inB = cellsB.has(cell);
                const both = inA && inB;
                return (
                  <div className="strip-cell" key={cell} data-conflict={both && conflict}>
                    <span className="idx">{zh ? 'CELL' : 'CELL'} {cell}</span>
                    <span className="marks">
                      {inA ? <i style={{ background: 'var(--accent)' }} title="owner A" /> : null}
                      {inB ? <i style={{ background: 'var(--blue)' }} title="owner B" /> : null}
                    </span>
                    <span className="mono" style={{ color: both ? 'var(--accent-ink)' : 'var(--faint)', fontSize: 7 }}>
                      {both ? (zh ? '双方' : 'both') : inA ? 'A' : inB ? 'B' : (zh ? '空闲' : 'free')}
                    </span>
                  </div>
                );
              })}
            </div>

            <div className="owner-controls">
              <div className="owner-row">
                <span className="owner-name"><i style={{ background: 'var(--accent)' }} /> {zh ? 'OWNER A' : 'OWNER A'}</span>
                <Segmented
                  value={sharingA}
                  options={[{ value: 'EXCLUSIVE', label: 'exclusive' }, { value: 'SHARED', label: 'shared' }]}
                  onChange={(value) => { setSharingA(value); setExpected(ledger); setAttempt('ready'); }}
                  label={zh ? 'owner A sharing' : 'Owner A sharing'}
                />
                <div className="stepper">
                  <button type="button" className="step-btn" onClick={() => setOwner('A', -1)} aria-label={zh ? 'owner A 左移' : 'Move owner A left'}>−</button>
                  <b>{startA}</b>
                  <button type="button" className="step-btn" onClick={() => setOwner('A', 1)} aria-label={zh ? 'owner A 右移' : 'Move owner A right'}>+</button>
                </div>
              </div>
              <div className="owner-row">
                <span className="owner-name"><i style={{ background: 'var(--blue)' }} /> {zh ? 'OWNER B' : 'OWNER B'}</span>
                <Segmented
                  value={sharingB}
                  options={[{ value: 'EXCLUSIVE', label: 'exclusive' }, { value: 'SHARED', label: 'shared' }]}
                  onChange={(value) => { setSharingB(value); setExpected(ledger); setAttempt('ready'); }}
                  label={zh ? 'owner B sharing' : 'Owner B sharing'}
                />
                <div className="stepper">
                  <button type="button" className="step-btn" onClick={() => setOwner('B', -1)} aria-label={zh ? 'owner B 左移' : 'Move owner B left'}>−</button>
                  <b>{startB}</b>
                  <button type="button" className="step-btn" onClick={() => setOwner('B', 1)} aria-label={zh ? 'owner B 右移' : 'Move owner B right'}>+</button>
                </div>
              </div>
            </div>

            <div className="ledger-actions">
              <button type="button" className="btn btn-solid" onClick={settle}>{zh ? '在 revision' : 'settle at revision'} {expected}</button>
              <button type="button" className="btn btn-ghost" onClick={bump}>{zh ? '推进 revision（race）' : 'bump revision (race)'}</button>
              {(attempt === 'moved' || attempt === 'stale') ? (
                <button type="button" className="btn btn-ghost" onClick={retry}>{zh ? '在 revision' : 'retry at revision'} {ledger}</button>
              ) : null}
              <button type="button" className="btn btn-ghost" onClick={() => { setStartA(0); setStartB(1); setSharingA('EXCLUSIVE'); setSharingB('SHARED'); setExpected(0); setLedger(0); setAttempt('ready'); }}>{zh ? '重置' : 'reset'}</button>
              <span className="mono muted" style={{ marginLeft: 'auto', alignSelf: 'center' }}>
                {attempt === 'moved' || attempt === 'stale' ? (zh ? `期望 ${expected} / 实际 ${ledger}` : `expected ${expected} / actual ${ledger}`) : (zh ? `期望 revision ${expected}` : `expected revision ${expected}`)}
              </span>
            </div>

            <div className="ledger-foot">
              <div><b>check</b>{zh ? '为报告预测 rows；Admitted 保留 bindings + revision' : 'predicts rows for the report; Admitted keeps bindings + revision'}</div>
              <div><b>settle</b>{zh ? '对 revision 做 CAS；写一次；Formed 携带 rows' : 'CAS on revision; writes once; Formed carries rows'}</div>
              <div><b>release</b>{zh ? '第三个 writer；no-op 可以在 revision 0 settle' : 'the third writer; a no-op may settle at revision 0'}</div>
            </div>

            <div className="ledger-result">
              <div className="ledger-result-head">
                <span>ClaimPlanner.derive → SettleRequest</span>
                <span className={attempt === 'formed' ? 'tag tag-green' : attempt === 'conflict' || attempt === 'moved' ? 'tag tag-accent' : 'tag'}>{result}</span>
              </div>
              <div className="ledger-result-body">
                <div>
                  <div className="v">{rows}</div>
                  <div className="k">{attempt === 'formed' ? (zh ? '写入的 rows' : 'rows written') : (zh ? 'request 里的 rows' : 'rows in the request')}</div>
                </div>
                <div>
                  <div className="v" style={{ color: conflict ? 'var(--accent)' : 'var(--green)' }}>{conflict ? (zh ? '是' : 'yes') : (zh ? '否' : 'no')}</div>
                  <div className="k">ClaimMatrix.conflicts</div>
                </div>
              </div>
            </div>
          </div>

          <aside className="ledger-side">
            <div className="eyebrow">{zh ? 'ledger 是一个 port' : 'The ledger is a port'}</div>
            <h4 style={{ fontFamily: 'var(--serif)', fontWeight: 400, fontSize: 27, letterSpacing: '-.03em', marginTop: 7 }}>
              {zh ? 'Admitted 带 bindings，不带 rows。' : 'Admitted has bindings, not rows.'}
            </h4>
            <p style={{ marginTop: 10, fontSize: 11, lineHeight: 1.55, color: 'var(--muted)' }}>
              {zh ? 'check 为冲突报告预测 rows，然后把它们忘掉。settle 从 bindings 和它读到的 revision 重新派生。revision 被移动时，得到一次新读取和一次 retry。' : 'The check predicts the rows for the conflict report, then forgets them. Settle re-derives from the bindings and the revision it read. A moved revision gets one fresh read and one retry.'}
            </p>
            <div className="matrix" style={{ marginTop: 16 }}>
              <div className="head">{zh ? '冲突' : 'conflicts'}</div>
              <div className="head">A</div>
              <div className="head">B</div>
              <div>A</div>
              <div className="no">{zh ? '否' : 'no'}</div>
              <div className={conflict ? 'yes' : 'no'}>{conflict ? (zh ? '是' : 'yes') : (zh ? '否' : 'no')}</div>
              <div>B</div>
              <div className={conflict ? 'yes' : 'no'}>{conflict ? (zh ? '是' : 'yes') : (zh ? '否' : 'no')}</div>
              <div className="no">{zh ? '否' : 'no'}</div>
            </div>
            <div className="key-stack" style={{ marginTop: 16 }}>
              <div><span className="k">settle</span><span className="v">{attempt === 'formed' ? (zh ? 'Settled' : 'Settled') : attempt === 'conflict' ? (zh ? 'Conflict' : 'Conflict') : attempt === 'moved' || attempt === 'stale' ? 'RevisionMoved → retry' : (zh ? '等待' : 'pending')}</span></div>
              <div><span className="k">ledger</span><span className="v">rev {ledger}</span></div>
              <div><span className="k">{zh ? '期望' : 'expected'}</span><span className="v">rev {expected}</span></div>
              <div><span className="k">context</span><span className="v">{settled ? `rev ${ledger}` : '—'}</span></div>
              <div><span className="k">rows</span><span className="v">{settled ? rows : (zh ? '未写入' : 'not written')}</span></div>
            </div>
            <div className="ledger-code">
              <div className="eyebrow" style={{ marginBottom: 8 }}>{zh ? '唯一的谓词' : 'The only predicate'}</div>
              <CodeBlock code={SNIPPET} language="java" />
            </div>
            <div className="aside-rule" />
            <div className="eyebrow">{zh ? '结构集合 / D-32 → D-35' : 'Structure sets / D-32 → D-35'}</div>
            <h4 style={{ fontFamily: 'var(--serif)', fontWeight: 400, fontSize: 24, letterSpacing: '-.03em', marginTop: 7 }}>
              {zh ? 'root + satellites，由 host 编排。' : 'Root + satellites, sequenced by the host.'}
            </h4>
            <p style={{ marginTop: 10, fontSize: 11, lineHeight: 1.55, color: 'var(--muted)' }}>
              {zh ? <>kernel 仍然一次只 check、settle 一个 blueprint。<code>StructureSetHost</code> 负责 presence、link 校验、check-all、settle-all 和 release；S5 增加 <code>/cumulus set check|settle</code> 和一次不带 orientation 的 union capture（不做 orientation audit，不会为 satellite cell 产生多余的 <code>SC-MC-0004</code>），S6 让 presence 与每个成员 attempt 共享一个 budget，并在 rollback 时移除每个已形成的 context；同 siting 拒绝和一格 budget 由真实 level 的 <code>StructureSetHostGameTest</code> 覆盖。</> : <>The kernel still checks and settles one blueprint at a time. <code>StructureSetHost</code> owns presence, link validation, check-all, settle-all and release; S5 adds <code>/cumulus set check|settle</code> over one unsited union capture (no orientation audit, so no spurious <code>SC-MC-0004</code> for satellite cells), and S6 shares one budget across presence and every member attempt, removes each formed context on rollback, and adds the real-level <code>StructureSetHostGameTest</code> cases for the same-siting rejection and the one-cell budget.</>}
            </p>
            <div className="key-stack" style={{ marginTop: 12 }}>
              <div><span className="k">order</span><span className="v">presence → links → check-all → settle-all → release</span></div>
              <div><span className="k">command</span><span className="v">/cumulus set check|settle · one union capture</span></div>
              <div><span className="k">capture</span><span className="v">{zh ? '不带 orientation · 不做 orientation audit · 无多余 SC-MC-0004' : 'unsited · no orientation audit · no spurious SC-MC-0004'}</span></div>
              <div><span className="k">budget</span><span className="v">{zh ? 'presence + 每个 attempt 共享一个 set budget' : 'one set budget: presence + every attempt'}</span></div>
              <div><span className="k">siting</span><span className="v">{zh ? 'satellite 不得与 root 同 siting · SC-SET-0008' : 'no satellite on the root siting · SC-SET-0008'}</span></div>
              <div><span className="k">{zh ? '回滚' : 'rollback'}</span><span className="v">{zh ? '逆序；release + ContextStore.remove；失败是 SC-SET-0007' : 'reverse order; release + ContextStore.remove; failure is SC-SET-0007'}</span></div>
              <div><span className="k">claims</span><span className="v">{zh ? '按 (owner, siting, bay) 过滤' : 'filtered by (owner, siting, bay)'}</span></div>
              <div><span className="k">context</span><span className="v">{zh ? 'rollback 时由 store 移除' : 'removed by the store on rollback'}</span></div>
            </div>
            <div className="figure-label">
              <span>{zh ? '图 04 — 两个 owner，一个 cell' : 'Fig. 04 — two owners, one cell'}</span>
              <span>{contested.length} {zh ? '个争议' : 'contested'}</span>
            </div>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
