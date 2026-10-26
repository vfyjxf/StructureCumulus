import { useEffect, useMemo, useState } from 'react';
import { decisions, DECISION_COUNT } from '../data/decisions';
import { TEST_GROUPS, TEST_COUNT, testClasses } from '../data/tests';
import { DOCS, GATES } from '../data/site';
import { Reveal, SectionHead } from '../components/ui';

const REGEN = [
  { n: '01', t: 'Archive', d: 'tar the old tree; never migrate it' },
  { n: '02', t: 'Delete', d: 'kernel, tests, util and docs/spec' },
  { n: '03', t: 'Write', d: 'requirements → constitution → model' },
  { n: '04', t: 'Split', d: 'eleven ranked kernel modules + adapter spec' },
  { n: '05', t: 'Generate', d: 'skeleton, then implementation' },
  { n: '06', t: 'Prove', d: 'javac, JUnit, purity, spec-check' },
  { n: '07', t: 'Review', d: 'attack the design, not the prose' },
];

const FALSIFIERS = [
  { claim: 'A section computation reads the world through the siting', gate: 'sectionComputationMapsThroughSiting', detail: 'nonzero origin + a refused local section' },
  { claim: 'A no-op release may keep revision 0', gate: 'aNoOpReleaseKeepsRevisionZero', detail: 'fresh ledger, empty release' },
  { claim: 'Derive does not charge the settle spend again', gate: 'campfireChecksSettlesAndDerives', detail: 'spend equals the settle spend on both calls' },
  { claim: 'A clipped entity is unreadable, not a clipped match', gate: 'aClippedEntityFootprintIsUnreadable', detail: 'no rows written' },
  { claim: 'A document build error keeps its specific code', gate: 'documentBuildErrorsKeepTheirSpecificCode', detail: 'overlapping exclusive bays → SC-BPT-0002' },
  { claim: 'A missing section contribution is CTX-0001, not CTX-0006', gate: 'aMissingSectionContributionIsAComputationFailure', detail: 'unreadable fold stays a different code' },
];

const TERMINAL = `$ tools/build-kernel.sh
  ✓ javac --release 21 -Werror
$ tools/test-kernel.sh
  95 tests / 95 successful / 0 failed
$ python3 tools/spec-check.py
  spec-check: ok
$ grep -rn "net.minecraft\\|net.neoforged\\|com.mojang" src/kernel/java
  none`;

export function Proof() {
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState('D-22');
  const [copied, setCopied] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return decisions;
    return decisions.filter((decision) => `${decision.id} ${decision.title} ${decision.summary}`.toLowerCase().includes(q));
  }, [query]);

  const active = filtered.find((decision) => decision.id === selected) ?? filtered[0] ?? null;

  useEffect(() => {
    if (filtered.length > 0 && !filtered.some((decision) => decision.id === selected)) {
      setSelected(filtered[0].id);
    }
  }, [filtered, selected]);
  const maxTests = Math.max(...TEST_GROUPS.map((group) => group.count));
  const testClassCount = testClasses.length;

  const copy = async (file: string) => {
    try {
      await navigator.clipboard.writeText(file);
      setCopied(file);
      window.setTimeout(() => setCopied(null), 1400);
    } catch {
      setCopied(null);
    }
  };

  return (
    <section className="section" id="proof" data-section>
      <SectionHead
        no="07"
        kicker="Proof / the review closes"
        title={<>A design is only real if it can <em>run.</em></>}
        note={<>Six gates, 95 tests, 55 codes and 23 decisions. Five gates are green; determinism was not run in this sandbox. The adapter is deliberately absent from this round; the kernel is not.</>}
      />

      <Reveal>
        <div className="proof-top">
          <div className="proof-gates">
            <div className="eyebrow">Current gate report / 2026.10.26</div>
            <div className="gate-grid" style={{ marginTop: 12 }}>
              {GATES.map((gate) => (
                <div className="gate" key={gate.label}>
                  <span className="v">{gate.value}</span>
                  <span className="l">{gate.label}</span>
                  <span className="n">{gate.note}</span>
                </div>
              ))}
            </div>
            <div className="test-bars">
              <div className="eyebrow" style={{ marginBottom: 4 }}>Tests by package</div>
              {TEST_GROUPS.map((group) => (
                <div className="test-bar" key={group.name}>
                  <span>{group.name}</span>
                  <span className="track"><span className="fill" style={{ display: 'block', width: `${(group.count / maxTests) * 100}%` }} /></span>
                  <span style={{ textAlign: 'right' }}>{group.count}</span>
                </div>
              ))}
              <div className="mono muted" style={{ marginTop: 9 }}>
                {TEST_COUNT} tests / {testClassCount} classes · 15 JUnit containers
              </div>
            </div>
          </div>
          <div className="terminal">
            <div className="cmd">$ the review harness</div>
            <div style={{ height: 8 }} />
            {TERMINAL.split('\n').map((line, index) => (
              <div key={index} className={line.includes('✓') || line.includes('successful') || line.includes('ok') ? 'ok' : line.startsWith('$') ? 'cmd' : ''}>
                {line || ' '}
              </div>
            ))}
            <div style={{ height: 8 }} />
            <div className="warn">warn: Gradle/Spotless were not run; compile is the direct javac harness, not the shipping build.</div>
            <div style={{ height: 14 }} />
            <div className="cmd">Next attack: adapter implementation, not the kernel.</div>
          </div>
        </div>
      </Reveal>

      <Reveal delay={60}>
        <div className="falsifiers">
          <div className="falsifiers-head">
            <div>
              <div className="eyebrow">Falsifiers / six ways this review could be wrong</div>
              <div className="mono muted" style={{ marginTop: 4 }}>Each claim has a test that would fail loudly if the design regressed.</div>
            </div>
            <span className="stamp">all green</span>
          </div>
          <div className="falsifier-grid">
            {FALSIFIERS.map((item) => (
              <div className="falsifier" key={item.gate}>
                <div className="falsifier-claim">{item.claim}</div>
                <div className="falsifier-gate">{item.gate}</div>
                <div className="falsifier-detail">{item.detail}</div>
                <span className="tag tag-green">pass</span>
              </div>
            ))}
          </div>
        </div>
      </Reveal>

      <Reveal delay={80}>
        <div className="decision-layout">
          <div className="decision-main">
            <div className="decision-toolbar">
              <div>
                <div className="eyebrow">Decision ledger / {DECISION_COUNT} rulings</div>
                <div className="mono muted" style={{ marginTop: 4 }}>Every ruling replaced a previous design; none is a preference.</div>
              </div>
              <div style={{ width: 'min(100%, 260px)' }}>
                <input className="input" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="search decisions…" aria-label="Search decisions" />
              </div>
            </div>

            <div className="decision-grid">
              {filtered.map((decision) => (
                <button
                  key={decision.id}
                  type="button"
                  className="decision"
                  data-active={decision.id === selected}
                  onClick={() => setSelected(decision.id)}
                >
                  <span className="id">{decision.id}</span>
                  <span className="title">{decision.title}</span>
                  <span className="summary">{decision.summary}</span>
                </button>
              ))}
            </div>
            {filtered.length === 0 ? <div className="code-empty">No decision matches this filter.</div> : null}
          </div>

          <aside className="decision-side">
            {active ? (
              <div className="decision-detail" key={active.id}>
                <div className="id">{active.id} / ruling</div>
                <h4>{active.title}</h4>
                <p>{active.summary}</p>
                <div className="producer"><strong>Source</strong>{" "}docs/design/05-decisions.md</div>
              </div>
            ) : (
              <div className="code-empty">No ruling selected.</div>
            )}
          </aside>
        </div>
      </Reveal>

      <Reveal delay={120}>
        <div style={{ marginTop: 34 }}>
          <div className="eyebrow">Regeneration protocol / one direction</div>
          <div className="regen">
            {REGEN.map((step) => (
              <div className="regen-step" key={step.n}>
                <span className="n">{step.n}</span>
                <span className="t">{step.t}</span>
                <span className="d">{step.d}</span>
              </div>
            ))}
          </div>
        </div>

        <div style={{ marginTop: 30 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'baseline', flexWrap: 'wrap' }}>
            <div className="eyebrow">Read the source / 21 documents</div>
            <div className="mono muted">click to copy a path · English is the only authority</div>
          </div>
          <div className="doc-index">
            {DOCS.map((doc) => (
              <button key={doc.file} type="button" className="doc-item" onClick={() => copy(doc.file)}>
                <span className="g">{doc.group}</span>
                <span className="t">{doc.title}</span>
                <span className="f">{copied === doc.file ? 'copied to clipboard' : doc.file}</span>
              </button>
            ))}
          </div>
        </div>
      </Reveal>
    </section>
  );
}
