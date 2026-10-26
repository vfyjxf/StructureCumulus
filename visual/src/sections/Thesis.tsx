import { useState } from 'react';
import { PRINCIPLES, REMOVED } from '../data/site';
import { Reveal, SectionHead } from '../components/ui';

export function Thesis() {
  const [active, setActive] = useState(0);
  const removed = REMOVED[active];

  return (
    <section className="section" id="thesis" data-section>
      <SectionHead
        no="00"
        kicker="The argument"
        title={<>The kernel is a <em>proof object</em>, not a runtime with a document attached.</>}
        note={<>Every state has one owner. Every report is a value. Every module is a rank, not a folder. The design is small because the alternatives were deleted, not because the problem is small.</>}
      />

      <Reveal>
        <div className="thesis-grid">
          <div className="thesis-main">
            <h3>
              A blueprint is a <em>declaration</em>.<br />
              A match is an <em>argument</em>.<br />
              A settlement is one claim write and one <em>publication</em>.
            </h3>
            <p>
              There is no stage machine to advance, no mutable context draft to patch, and no claim plan
              to reconcile. The check reads one immutable view; the settle writes one ledger revision and
              publishes one context revision; the derive folds the published values once more.
            </p>
            <div className="thesis-list">
              <div>
                <dt>One IR</dt>
                <dd>JSON data and a Java builder lower to the same <code>Blueprint</code> value.</dd>
              </div>
              <div>
                <dt>One key</dt>
                <dd><code>ContextKey&lt;T&gt;</code> carries id, type, retention and merge. Nothing else identifies a value.</dd>
              </div>
              <div>
                <dt>One ledger</dt>
                <dd>Claims are per world; rows carry cell × owner × siting × bay. <code>Admitted</code> carries bindings and a revision, never rows.</dd>
              </div>
              <div>
                <dt>One direction</dt>
                <dd>The package graph is ranked and checked. An upward import is a design change, not a refactor.</dd>
              </div>
            </div>
            <div className="big-rule" />
            <blockquote className="thesis-quote">
              <p>“If the code and the design disagree, the design wins and the code is regenerated.”</p>
              <cite>— docs/README.md, the one rule</cite>
            </blockquote>
          </div>

          <aside className="thesis-side">
            <h4>The four laws this review keeps returning to</h4>
            <div className="principles">
              {PRINCIPLES.map((principle) => (
                <div className="principle" key={principle.n}>
                  <span className="n">{principle.n}</span>
                  <div>
                    <b>{principle.title}</b>
                    <span>{principle.text}</span>
                  </div>
                </div>
              ))}
            </div>
            <div style={{ marginTop: 22 }}>
              <div className="eyebrow">Review posture</div>
              <p style={{ marginTop: 8, fontSize: 11, lineHeight: 1.55, color: 'var(--muted)' }}>
                This is not a feature tour. Each chapter is a claim the kernel must survive: a frame
                mapping, a write, a conflict, a missing value, a finding.
              </p>
              <div className="stamp" style={{ marginTop: 14 }}>adversarial reviewed</div>
            </div>
          </aside>
        </div>
      </Reveal>

      <Reveal delay={100}>
        <div className="subtraction">
          <div className="subtraction-head">
            <h4>What is deliberately missing</h4>
            <span className="mono muted">click a deleted concept</span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 300px' }}>
            <div className="removed-grid">
              {REMOVED.map((item, index) => (
                <button
                  key={item.term}
                  type="button"
                  className="removed-item"
                  data-active={index === active}
                  onClick={() => setActive(index)}
                >
                  <s>{item.term}</s>
                  <b>{item.replacement}</b>
                  <span>{item.why}</span>
                </button>
              ))}
            </div>
            <div className="removed-detail">
              <div className="kicker">Deleted / {String(active + 1).padStart(2, '0')}</div>
              <div className="removed-detail-term">{removed.term}</div>
              <div className="removed-detail-arrow">→ {removed.replacement}</div>
              <p>{removed.why}</p>
              <div className="mono muted" style={{ marginTop: 14 }}>the replacement is a value or a law, not a flag</div>
            </div>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
