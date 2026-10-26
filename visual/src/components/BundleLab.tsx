import { useMemo, useState } from 'react';

type Severity = 'ERROR' | 'WARNING' | 'INFO';
type RawFinding = {
  id: string;
  code: string;
  severity: Severity;
  phase: 'COMPILE' | 'CHECK' | 'SETTLE';
  message: string;
  note?: string;
  dedupe: string;
};

const RAW: RawFinding[] = [
  { id: 'f1', code: 'SC-BPT-0002', severity: 'ERROR', phase: 'COMPILE', message: 'two exclusive bays overlap at cell 0', dedupe: 'bpt2' },
  { id: 'f2', code: 'SC-CTX-0006', severity: 'ERROR', phase: 'CHECK', message: 'a fold has a refused section', note: 'S-01 returned DENIED', dedupe: 'ctx6' },
  { id: 'f3', code: 'SC-OCC-0002', severity: 'WARNING', phase: 'CHECK', message: 'the entity footprint leaves the capture', note: 'only the readable part was compared', dedupe: 'occ2' },
  { id: 'f4', code: 'SC-CLM-0002', severity: 'WARNING', phase: 'SETTLE', message: 'the ledger cannot answer the re-read', dedupe: 'clm2' },
  { id: 'f5', code: 'SC-OCC-0002', severity: 'WARNING', phase: 'CHECK', message: 'the entity footprint leaves the capture', note: 'only the readable part was compared', dedupe: 'occ2' },
  { id: 'f6', code: 'SC-DOC-0012', severity: 'INFO', phase: 'COMPILE', message: 'the reader produced no spans', dedupe: 'doc12' },
  { id: 'f7', code: 'SC-ENG-0005', severity: 'WARNING', phase: 'CHECK', message: 'the attempt was cancelled by the host', dedupe: 'eng5' },
  { id: 'f8', code: 'SC-OCC-0002', severity: 'WARNING', phase: 'CHECK', message: 'another clipped footprint', note: 'a different direction', dedupe: 'occ2b' },
];

const SEV_ORDER: Record<Severity, number> = { ERROR: 0, WARNING: 1, INFO: 2 };
const PHASE_ORDER = { COMPILE: 0, CHECK: 1, SETTLE: 2 } as const;

function sevClass(severity: Severity) {
  if (severity === 'ERROR') return 'tag-accent';
  if (severity === 'WARNING') return 'tag-ochre';
  return 'tag-blue';
}

export function BundleLab() {
  const [includeInfo, setIncludeInfo] = useState(false);
  const [includeNotes, setIncludeNotes] = useState(true);
  const [maxTotal, setMaxTotal] = useState(2);
  const [maxPerCode, setMaxPerCode] = useState(1);

  const result = useMemo(() => {
    const sorted = [...RAW].sort((a, b) => SEV_ORDER[a.severity] - SEV_ORDER[b.severity] || PHASE_ORDER[a.phase] - PHASE_ORDER[b.phase] || a.code.localeCompare(b.code));
    const seen = new Set<string>();
    const deduped: RawFinding[] = [];
    let dedupedCount = 0;
    for (const finding of sorted) {
      if (seen.has(finding.dedupe)) { dedupedCount += 1; continue; }
      seen.add(finding.dedupe);
      deduped.push(finding);
    }
    let filtered = 0;
    const visible = deduped.filter((finding) => {
      if (!includeInfo && finding.severity === 'INFO') { filtered += 1; return false; }
      return true;
    });
    const kept: RawFinding[] = [];
    let elided = 0;
    let total = 0;
    const perCode = new Map<string, number>();
    for (const finding of visible) {
      if (finding.severity === 'ERROR') { kept.push(finding); continue; }
      const codeCount = perCode.get(finding.code) ?? 0;
      if (total >= maxTotal || codeCount >= maxPerCode) { elided += 1; continue; }
      kept.push(finding);
      perCode.set(finding.code, codeCount + 1);
      total += 1;
    }
    return { sorted, dedupedCount, filtered, elided, kept, tail: elided + filtered > 0 };
  }, [includeInfo, maxTotal, maxPerCode]);

  const displayed = includeNotes ? result.kept : result.kept.map((finding) => ({ ...finding, note: undefined }));

  return (
    <div className="bundle-lab">
      <div className="bundle-intro">
        <div className="eyebrow">Bundle lab / the report algebra</div>
        <h4>Sort. Dedupe. Cap. One tail.</h4>
        <p>
          The bundle is not a list with a counter. Errors are never capped; warnings are capped per code
          and in total; info is filtered by policy. The tail is one synthetic <code>SC-DIA-0001</code>.
        </p>
      </div>

      <div className="bundle-controls">
        <div className="bundle-control">
          <span className="k">include info</span>
          <button type="button" className="toggle" data-on={includeInfo} onClick={() => setIncludeInfo((value) => !value)}>
            <span /> {includeInfo ? 'on' : 'off'}
          </button>
        </div>
        <div className="bundle-control">
          <span className="k">include notes</span>
          <button type="button" className="toggle" data-on={includeNotes} onClick={() => setIncludeNotes((value) => !value)}>
            <span /> {includeNotes ? 'on' : 'off'}
          </button>
        </div>
        <div className="bundle-control">
          <span className="k">max total</span>
          <div className="bundle-stepper">
            {[2, 4, 6].map((value) => (
              <button key={value} type="button" data-on={maxTotal === value} onClick={() => setMaxTotal(value)}>{value}</button>
            ))}
          </div>
        </div>
        <div className="bundle-control">
          <span className="k">max per code</span>
          <div className="bundle-stepper">
            {[1, 2, 3].map((value) => (
              <button key={value} type="button" data-on={maxPerCode === value} onClick={() => setMaxPerCode(value)}>{value}</button>
            ))}
          </div>
        </div>
      </div>

      <div className="bundle-flow">
        <div><span>{RAW.length}</span><em>raw</em></div>
        <i>−</i>
        <div><span>{result.dedupedCount}</span><em>dedupe</em></div>
        <i>−</i>
        <div><span>{result.filtered}</span><em>filter</em></div>
        <i>−</i>
        <div data-accent={result.elided > 0}><span>{result.elided}</span><em>cap</em></div>
        <i>=</i>
        <div><span>{result.kept.length}</span><em>in bundle</em></div>
        {result.tail ? <><i>+</i><div className="bundle-tail-count"><span>1</span><em>tail</em></div></> : null}
        <i>→</i>
        <div className="bundle-tail-count"><span>{result.kept.length + (result.tail ? 1 : 0)}</span><em>emitted</em></div>
      </div>

      <div className="bundle-list">
        {displayed.map((finding) => (
          <div className="bundle-row" key={finding.id}>
            <span className={`tag ${sevClass(finding.severity)}`}>{finding.severity.slice(0, 4)}</span>
            <span className="bundle-code">{finding.code}</span>
            <span className="bundle-msg">{finding.message}</span>
            {finding.note ? <span className="bundle-note">{finding.note}</span> : null}
          </div>
        ))}
        {result.tail ? (
          <div className="bundle-row bundle-tail">
            <span className="tag tag-ochre">WARN</span>
            <span className="bundle-code">SC-DIA-0001</span>
            <span className="bundle-msg">{result.elided + result.filtered} elided or filtered findings were omitted.</span>
            <span className="bundle-note">the only synthetic finding</span>
          </div>
        ) : null}
        {displayed.length === 0 && !result.tail ? <div className="code-empty">No finding survives this policy.</div> : null}
      </div>

      <div className="bundle-law">
        <span><b>order</b> severity → phase → code → primary locus</span>
        <span><b>dedupe</b> (severity, phase, code, labels, notes)</span>
        <span><b>cap</b> errors uncapped; non-errors only</span>
      </div>
    </div>
  );
}
