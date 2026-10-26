import { useState } from 'react';
import { Reveal, SectionHead, Segmented } from '../components/ui';

const CHAIN = [
  {
    id: 'world',
    title: 'WorldView',
    role: 'one immutable read face',
    detail: 'The world is not a mutable level. It is a frozen, recursive view with a snapshot id, a frame, a section grid and a lease that answers one question: is it still current?',
    signature: `SnapshotId snapshotId();
int frame();
LatticePoint origin();
@Nullable OccupantSnapshot occupant(LatticePoint cell);`,
    side: 'A nested view is the same type with a unique frame. A section key answers only its own frame.',
  },
  {
    id: 'bay',
    title: 'Bay',
    role: 'cells with a coverage and a footprint',
    detail: 'A bay is a CuboidSet. A shell is not six synthetic bays; a door is two cells; an L-room is three boxes. Coverage says what the checks must decide; footprint says what the bay owns.',
    signature: `record Bay(NamespacedId id, CuboidSet cells,
        Coverage coverage, Footprint footprint,
        Sharing sharing, @Nullable String description)`,
    side: 'EVERY_CELL · ONE_SUBJECT · ANY_SUBJECT. The three words replace an entire requirement language.',
  },
  {
    id: 'check',
    title: 'Check',
    role: 'a focus × an expectation',
    detail: 'The focus takes one subject, the expectation tests it, and the check is the only place the two meet. There is no predicate registry, no requirement object and no second dispatch path.',
    signature: `record Check<T>(NamespacedId id, Bay bay,
        Focus<T> focus, Expectation<T> expectation,
        @Nullable String description)`,
    side: 'Match, Mismatch, Unreadable and Abstain never collapse. A mismatch is a refutation; unreadable is ignorance.',
  },
  {
    id: 'blueprint',
    title: 'Blueprint',
    role: 'the whole declaration',
    detail: 'A builder validates coverage, overlap, footprints, plans, anchors and derivations once. The result is immutable and carries a cached Plan: sections first, then a topological aggregate order, then checks.',
    signature: `Blueprint.builder(id)
    .bounds(...)
    .check(...)
    .computation(...)
    .derive(...)
    .build();`,
    side: 'A blueprint is the value document compilation targets. A bad document returns findings; it never constructs a partial blueprint.',
  },
  {
    id: 'outcome',
    title: 'MatchOutcome',
    role: 'values, bindings, spend',
    detail: 'The check returns one outcome: folded values, one binding per bay, a read scope and a spend. The result arm adds a revision or a verdict; it never hides a second mutable state.',
    signature: `record MatchOutcome(WorldView view,
        Map<ContextKey<?>, Object> values,
        List<BayBinding> bindings,
        ReadScope reads, Spend spend)`,
    side: 'Admitted carries the bindings and the revision it read. Formed carries the written rows and the context revision.',
  },
];

export function ObjectModel() {
  const [active, setActive] = useState('check');
  const [retention, setRetention] = useState<'INSTANCE' | 'MATCH'>('INSTANCE');
  const selected = CHAIN.find((item) => item.id === active) ?? CHAIN[2];

  return (
    <section className="section" id="object" data-section>
      <SectionHead
        no="02"
        kicker="The object model"
        title={<>One meeting point. <em>No second dispatch.</em></>}
        note={<>A check is not a rule engine. It is a focus that takes a subject and an expectation that answers. Everything else is a value around that meeting.</>}
      />

      <Reveal>
        <div className="object-layout">
          <div className="object-main">
            <div className="chain">
              {CHAIN.map((item, index) => (
                <button
                  key={item.id}
                  type="button"
                  className="chain-card"
                  data-active={item.id === active}
                  onClick={() => setActive(item.id)}
                >
                  <span className="idx">0{index + 1}</span>
                  <span className="t">{item.title}</span>
                  <span className="r">{item.role}</span>
                </button>
              ))}
            </div>
            <div className="object-detail">
              <div>
                <h4>{selected.title}</h4>
                <p>{selected.detail}</p>
                <pre className="code-block sig" style={{ marginTop: 16, fontSize: 10 }}>
                  <code>{selected.signature}</code>
                </pre>
              </div>
              <div>
                <div className="eyebrow">The invariant</div>
                <p style={{ marginTop: 8, color: 'var(--muted)', fontSize: 12, lineHeight: 1.6 }}>{selected.side}</p>
                <div style={{ marginTop: 20, paddingTop: 12, borderTop: '1px solid var(--line)' }}>
                  <div className="mono muted">the only meeting</div>
                  <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <span className="tag">Focus</span>
                    <span className="mono muted">×</span>
                    <span className="tag tag-blue">Expectation</span>
                    <span className="mono muted">=</span>
                    <span className="tag tag-accent">Check</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <aside className="object-side">
            <div className="eyebrow">The one key</div>
            <div className="big">ContextKey&lt;T&gt;</div>
            <p>
              Identity is the tuple. A key with the same id but a different Java type is a different key;
              there is no lookup by string.
            </p>
            <div className="key-diagram">
              <div className="key-row"><span className="k">id</span><span className="v">mymod:air_cells</span></div>
              <div className="key-row"><span className="k">type</span><span className="v">java.lang.Long</span></div>
              <div className="key-row"><span className="k">retention</span><span className="v">{retention}</span></div>
              <div className="key-row"><span className="k">merge</span><span className="v">sum</span></div>
              <div className="key-row"><span className="k">whenAbsent</span><span className="v">null</span></div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Segmented
                value={retention}
                options={[{ value: 'INSTANCE', label: 'instance' }, { value: 'MATCH', label: 'match' }]}
                onChange={setRetention}
                label="Context retention"
              />
            </div>
            <div className="key-state" data-instance={retention === 'INSTANCE'}>
              {retention === 'INSTANCE'
                ? 'published once by settle or derive'
                : 'attempt-local; never published'}
            </div>
            <div className="aside-rule" />
            <div className="eyebrow">A note on absence</div>
            <p style={{ marginTop: 8 }}>
              <code>Outcome</code>, <code>FocusResult</code>, <code>Reply</code> and <code>MatchResult</code>
              {' '}are four different questions. The design keeps four types instead of one nullable value.
            </p>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
