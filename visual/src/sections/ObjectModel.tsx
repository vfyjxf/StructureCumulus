import { useState } from 'react';
import { Reveal, SectionHead, Segmented } from '../components/ui';
import { useLanguage } from '../i18n/Language';

const CHAIN = [
  {
    id: 'world',
    title: 'WorldView',
    role: 'one immutable read face',
    detail: 'The world is not a mutable level. It is a frozen, recursive view with a snapshot id, a frame, a section grid, typed host reads and a lease that answers one question: is it still current?',
    signature: `SnapshotId snapshotId();
int frame();
LatticePoint origin();
@Nullable OccupantSnapshot occupant(LatticePoint cell);
@Nullable <H> H get(LatticePoint cell, HostType<H> type);`,
    side: 'A nested view is the same type with a unique frame. HostType reads one snapshot; the descriptor is (type, kind) and carries no properties or tags.',
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
    side: 'Match, Mismatch, Unreadable and Abstain never collapse. A mismatch is a refutation; unreadable is ignorance. A focus also declares bindsClaimableSubject (D-37): a subject-binding focus that cannot claim is rejected at build with SC-BPT-0005 when the bay claims. NOTAIR is exactly not(air()); empty() and nonFluid() are opt-in leaves (D-39).',
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
    side: 'A blueprint is the value document compilation targets. A bad document returns findings; it never constructs a partial blueprint. envelope() derives the closed world from bounds; a foreign occupant is SC-EXP-0006.',
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

const ZH_CHAIN: Record<string, { role: string; detail: string; side: string }> = {
  world: {
    role: '一个不可变读取面',
    detail: 'world 不是可变 level。它是一个冻结的递归 view：snapshot id、frame、section 网格，以及一个只回答“它还 current 吗？”的 lease。',
    side: '嵌套 view 是同一个类型，只是 frame 不同。HostType 读取一份快照；descriptor 是 (type, kind)，不携带 properties 或 tags。',
  },
  bay: {
    role: '带 coverage 和 footprint 的 cells',
    detail: 'bay 是一个 CuboidSet。shell 不是六个合成 bay；door 是两个 cell；L 形房间是三个 box。coverage 说明 check 必须决定什么；footprint 说明 bay 拥有什么。',
    side: 'EVERY_CELL · ONE_SUBJECT · ANY_SUBJECT。三个词替代了整套 requirement 语言。',
  },
  check: {
    role: '一个 focus × 一个 expectation',
    detail: 'focus 取一个 subject，expectation 检验它，check 是两者唯一相遇的地方。没有 predicate registry、没有 requirement 对象、没有第二条 dispatch 路径。',
    side: 'Match、Mismatch、Unreadable、Abstain 永不合并。Mismatch 是反驳；Unreadable 是无知。focus 还声明 bindsClaimableSubject（D-37）：当 bay 会 claim 时，不能 claim 的 subject-binding focus 在 build 时以 SC-BPT-0005 被拒绝。NOTAIR 精确等于 not(air())；empty() 和 nonFluid() 是 opt-in leaf（D-39）。',
  },
  blueprint: {
    role: '完整声明',
    detail: 'builder 一次校验 coverage、overlap、footprint、plan、anchor 和 derivation。结果是不可变的，并携带缓存 Plan：先 sections，再拓扑 aggregate 顺序，最后 checks。',
    side: 'blueprint 是 document 编译的目标值。坏 document 返回 findings；它从不构造半成品 blueprint。envelope() 从 bounds 推导闭合世界；外来 occupant 是 SC-EXP-0006。',
  },
  outcome: {
    role: 'values、bindings、spend',
    detail: 'check 返回一个 outcome：折叠后的 values、每个 bay 一个 binding、read scope 和 spend。result arm 再加 revision 或 verdict；它从不藏第二个可变状态。',
    side: 'Admitted 携带 bindings 和它读到的 revision。Formed 携带写入的 rows 和 context revision。',
  },
};

export function ObjectModel() {
  const { zh } = useLanguage();
  const [active, setActive] = useState('check');
  const [retention, setRetention] = useState<'INSTANCE' | 'MATCH'>('INSTANCE');
  const selected = CHAIN.find((item) => item.id === active) ?? CHAIN[2];
  const copy = zh ? ZH_CHAIN[selected.id] : { role: selected.role, detail: selected.detail, side: selected.side };

  return (
    <section className="section" id="object" data-section>
      <SectionHead
        no="02"
        kicker={zh ? '对象模型' : 'The object model'}
        title={zh ? <>一个汇合点。<em>没有第二套 dispatch。</em></> : <>One meeting point. <em>No second dispatch.</em></>}
        note={zh ? <>check 不是规则引擎。它是一个取 subject 的 focus 和一个负责回答的 expectation。其余一切都只是围绕这次汇合的值。</> : <>A check is not a rule engine. It is a focus that takes a subject and an expectation that answers. Everything else is a value around that meeting.</>}
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
                  <span className="r">{zh ? ZH_CHAIN[item.id].role : item.role}</span>
                </button>
              ))}
            </div>
            <div className="object-detail">
              <div>
                <h4>{selected.title}</h4>
                <p>{copy.detail}</p>
                <pre className="code-block sig" style={{ marginTop: 16, fontSize: 10 }}>
                  <code>{selected.signature}</code>
                </pre>
              </div>
              <div>
                <div className="eyebrow">{zh ? '不变量' : 'The invariant'}</div>
                <p style={{ marginTop: 8, color: 'var(--muted)', fontSize: 12, lineHeight: 1.6 }}>{copy.side}</p>
                <div style={{ marginTop: 20, paddingTop: 12, borderTop: '1px solid var(--line)' }}>
                  <div className="mono muted">{zh ? '唯一的汇合' : 'the only meeting'}</div>
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
            <div className="eyebrow">{zh ? '唯一的 key' : 'The one key'}</div>
            <div className="big">ContextKey&lt;T&gt;</div>
            <p>
              {zh
                ? '身份就是整个元组。同一个 id、不同 Java 类型的 key 就是两个 key；没有按字符串查找。'
                : 'Identity is the tuple. A key with the same id but a different Java type is a different key; there is no lookup by string.'}
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
                label={zh ? 'context retention' : 'Context retention'}
              />
            </div>
            <div className="key-state" data-instance={retention === 'INSTANCE'}>
              {retention === 'INSTANCE'
                ? (zh ? '由 settle 或 derive 发布一次' : 'published once by settle or derive')
                : (zh ? 'attempt 局部；从不发布' : 'attempt-local; never published')}
            </div>
            <div className="aside-rule" />
            <div className="eyebrow">{zh ? 'host payload / D-33 · structure / D-40' : 'Host payload / D-33 · structure / D-40'}</div>
            <div className="key-stack" style={{ marginTop: 10 }}>
              <div><span className="k">read</span><span className="v">WorldView.get(pos, type)</span></div>
              <div><span className="k">bridge</span><span className="v">Expectations.host</span></div>
              <div><span className="k">descriptor</span><span className="v">(type, kind)</span></div>
              <div><span className="k">traits</span><span className="v">TraitProvider&lt;T, H&gt;</span></div>
              <div><span className="k">nested</span><span className="v">NestedExtentLookup</span></div>
              <div><span className="k">instance</span><span className="v">StructureInstance</span></div>
              <div><span className="k">manager</span><span className="v">StructureManager</span></div>
              <div><span className="k">events</span><span className="v">Broken · Moved · Loaded · Unloaded · Invalidated</span></div>
              <div><span className="k">D-29</span><span className="v">{zh ? '已被取代' : 'superseded'}</span></div>
            </div>
            <p style={{ marginTop: 10 }}>
              {zh
                ? <>一份不可变快照、多种类型化读取。descriptor 不再有 properties/tags；property、state、tag、fluid 是 adapter 在 <code>Expectations.host</code> 上的 leaf。capability trait 直接返回 <code>IItemHandler</code> / <code>IEnergyStorage</code> / <code>IFluidHandler</code>。nested extent 是同一套 per-holder 形状：一个 <code>NestedExtentLookup</code>、每个 holder 一个 <code>NestedExtentProvider</code>，以及一个 <code>CapabilityNestedExtentProvider</code>；旧的聚合 lookup 和 <code>CapabilityLookup</code> 已删除。D-40 把每个 <code>StructureContext</code> 绑定到它的 <code>StructureInstance</code>：<code>begin</code>/<code>commit</code> 是唯一的创建路径，manager 是 in-memory 的，库不持久化任何东西。</>
                : <>One immutable snapshot, many typed reads. The descriptor has no properties or tags; property, state, tag and fluid are adapter leaves over <code>Expectations.host</code>. Capability traits return <code>IItemHandler</code>, <code>IEnergyStorage</code> and <code>IFluidHandler</code> directly. Nested extents follow the same per-holder shape: one <code>NestedExtentLookup</code>, a <code>NestedExtentProvider</code> per holder and one <code>CapabilityNestedExtentProvider</code>; the old aggregate lookup and <code>CapabilityLookup</code> are gone. D-40 binds each <code>StructureContext</code> to its <code>StructureInstance</code>: <code>begin</code>/<code>commit</code> is the only creation path, the manager is in-memory and the library persists nothing.</>}
            </p>
            <div className="aside-rule" />
            <div className="eyebrow">{zh ? '关于缺席' : 'A note on absence'}</div>
            <p style={{ marginTop: 8 }}>
              {zh ? <>四个不同的提问：<code>Outcome</code>、<code>FocusResult</code>、<code>Reply</code>、<code>MatchResult</code>。设计保留四种类型，而不是一个可空值。</> : <><code>Outcome</code>, <code>FocusResult</code>, <code>Reply</code> and <code>MatchResult</code>{' '}are four different questions. The design keeps four types instead of one nullable value.</>}
            </p>
          </aside>
        </div>
      </Reveal>
    </section>
  );
}
