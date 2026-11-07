import { useEffect, useMemo, useState } from 'react';
import { decisions, DECISION_COUNT } from '../data/decisions';
import { TEST_GROUPS, TEST_COUNT, testClasses } from '../data/tests';
import { DOCS, GATES, LAYERS } from '../data/site';
import { DECISION_ZH } from '../data/decisions.zh';
import { ZH_DOCS, ZH_GATES, ZH_GROUPS, ZH_LAYERS } from '../data/site.zh';
import { useLanguage } from '../i18n/Language';
import { Reveal, SectionHead } from '../components/ui';

const REGEN = [
  { n: '01', t: 'Archive', tZh: '归档', d: 'tar the old tree; never migrate it', dZh: '把旧树 tar 起来；绝不迁移' },
  { n: '02', t: 'Delete', tZh: '删除', d: 'kernel, tests, util and docs/spec', dZh: 'kernel、tests、util 和 docs/spec' },
  { n: '03', t: 'Write', tZh: '书写', d: 'requirements → constitution → model', dZh: 'requirements → constitution → model' },
  { n: '04', t: 'Split', tZh: '拆分', d: 'eleven ranked kernel modules + adapter spec', dZh: '十一个 rank 化的 kernel 模块 + adapter 规范' },
  { n: '05', t: 'Generate', tZh: '生成', d: 'skeleton, then implementation', dZh: '先骨架，再实现' },
  { n: '06', t: 'Prove', tZh: '证明', d: 'javac, JUnit, purity, spec-check', dZh: 'javac、JUnit、purity、spec-check' },
  { n: '07', t: 'Review', tZh: 'Review', d: 'attack the design, not the prose', dZh: '攻击设计，不是散文' },
];

const FALSIFIERS = [
  { claim: 'A section computation reads the world through the siting', gate: 'sectionComputationMapsThroughSiting', detail: 'nonzero origin + a refused local section' },
  { claim: 'A no-op release may keep revision 0', gate: 'aNoOpReleaseKeepsRevisionZero', detail: 'fresh ledger, empty release' },
  { claim: 'Derive does not charge the settle spend again', gate: 'campfireChecksSettlesAndDerives', detail: 'spend equals the settle spend on both calls' },
  { claim: 'A clipped entity is unreadable, not a clipped match', gate: 'aClippedEntityFootprintIsUnreadable', detail: 'no rows written' },
  { claim: 'A document build error keeps its specific code', gate: 'documentBuildErrorsKeepTheirSpecificCode', detail: 'overlapping exclusive bays → SC-BPT-0002' },
  { claim: 'A missing section contribution is CTX-0001, not CTX-0006', gate: 'aMissingSectionContributionIsAComputationFailure', detail: 'unreadable fold stays a different code' },
  { claim: 'A host value missing from the snapshot is unreadable, not a mismatch', gate: 'aMissingHostValueIsUnreadableWithTheSuppliedAbsentCode', detail: 'missing snapshot value keeps the caller absent code' },
  { claim: 'The current attempt shadows a cached context value', gate: 'theCurrentAttemptValueShadowsTheCachedValue', detail: 'D-31 cache is host-owned and secondary' },
  { claim: 'A structure-set settle failure releases earlier structures in reverse order', gate: 'aSettleFailureReleasesInReverseOrder', detail: 'claims released, then Broken for each formed instance; a removal failure is SC-SET-0007' },
  { claim: 'A set check admits both members and writes no row', gate: 'checkAllWritesNothingThenSettleAllWritesClaims', detail: 'one capture for the union bounds; settle writes the three cells' },
  { claim: 'A set command writes nothing on check and the claims on settle', gate: 'setCheckWritesNothingAndSetSettleWritesTheClaims', detail: 'the built-in structurecumulus:set_demo over the dispatcher' },
  { claim: 'A satellite placed exactly on the root is rejected', gate: 'aSatellitePlacedExactlyOnTheRootIsRejected', detail: 'SC-SET-0008 in the compiler, StructureSet.place and the host' },
  { claim: 'Presence and every member attempt share one set budget', gate: 'presenceAndEveryStructureAttemptShareOneBudgetCeiling', detail: 'presence (1) + root (1) + satellite (1) under Budget 3' },
  { claim: 'A hand-built set on the root siting is rejected before presence', gate: 'aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence', detail: 'real-level GameTest: SC-SET-0008, no outcome, instance, row or revision' },
  { claim: 'A one-cell set budget leaves the root check undecided', gate: 'aOneCellSetBudgetLeavesTheRootCheckUndecided', detail: 'real-level GameTest: presence takes the cell; the root is Undecided (SC-ENG-0001)' },
  { claim: 'A set rollback removes every formed instance and its context', gate: 'rollbackRemovesTheKernelContextOfEveryFormedAttempt', detail: 'claims released and Broken dispatched; the manager drops instance and context together' },
  { claim: 'A failed context removal is SC-SET-0007 and does not stop the other rollbacks', gate: 'aFailedContextRemovalIsScSet0007AndKeepsTheOtherRemovals', detail: 'the release verdict does not skip the manager Broken dispatch' },
  { claim: 'An envelope-only blueprint gets one derived bay and check', gate: 'anEnvelopeOnlyBlueprintGetsTheLastBayAndCheck', detail: 'D-30; an empty set is a no-op' },
  { claim: 'A capability trait is the live capability object', gate: 'chestInventoryIsTheLiveCapability', detail: 'GameTest: IItemHandler, no copy' },
  { claim: 'A subject-binding focus that cannot claim is rejected at build', gate: 'nestedAnySubjectBoundBodyFailsBuild', detail: 'D-37: SC-BPT-0005 when the bay footprint claims' },
  { claim: 'Claim release deletes the whole instance key', gate: 'releaseTakesTheWholeInstanceAndBumpsOnlyWhenItRemovesRows', detail: 'D-38/D-45: no per-bay or per-row release; duplicate rows collapse' },
  { claim: 'Water in a NOTAIR slot is admitted', gate: 'waterInANotAirSlotIsAdmitted', detail: 'D-39: NOTAIR is exactly not(air()); water is non-air' },
  { claim: 'empty and nonFluid classify air, water, lava and stone', gate: 'emptyAndNonFluidClassifyAirWaterLavaAndStone', detail: 'D-39: empty = air or fluid block; nonFluid = everything else' },
  { claim: 'A committed attempt becomes a bounds-bearing instance', gate: 'beginPublishesCommitsAndFindsByCell', detail: 'D-40: begin reserves, settle publishes, commit(bounds) forms; findAt returns it' },
  { claim: 'Abort drops the draft and releases the siting', gate: 'abortDropsTheDraftAndReleasesTheSiting', detail: 'D-40: one-shot commit/abort; the aborted serial is not reused' },
  { claim: 'A moving write breaks the structure and releases every row', gate: 'movingWriteBreaksTheStructureAndReleasesEveryClaimRow', detail: 'D-45: a move is a removal, not a translation; a rebuild is a fresh settle' },
  { claim: 'Broken and Invalidated are idempotent', gate: 'brokenAndInvalidatedAreIdempotent', detail: 'D-40: an unknown id is a no-op' },
  { claim: 'Unloaded clears one world and Loaded raises the serial', gate: 'unloadedRemovesOnlyThatWorldAndLoadedRaisesTheSerial', detail: 'D-40: per-world registry; Loaded is the only bulk replace' },
  { claim: 'A block placement dispatches Invalidated through WatchEvents', gate: 'blockPlaceDispatchesInvalidated', detail: 'D-40: the adapter detects and dispatches; D-38 releases the claim rows' },
  { claim: 'A publish after commit updates the bound instance context', gate: 'publishAfterCommitUpdatesTheBoundInstance', detail: 'D-40: the manager replaces the whole StructureInstance record' },
];

const FALSIFIER_ZH: Record<string, { claim: string; detail: string }> = {
  sectionComputationMapsThroughSiting: { claim: 'section computation 通过 siting 读 world', detail: '非零 origin + 被拒绝的 local section' },
  aNoOpReleaseKeepsRevisionZero: { claim: 'no-op release 可以保持 revision 0', detail: '新 ledger，空 release' },
  campfireChecksSettlesAndDerives: { claim: 'derive 不会重复收取 settle 的 spend', detail: '两次调用都等于 settle spend' },
  aClippedEntityFootprintIsUnreadable: { claim: '被裁剪的 entity 是 unreadable，不是 clipped match', detail: '没有写入任何 row' },
  documentBuildErrorsKeepTheirSpecificCode: { claim: 'document 构建错误保留具体 code', detail: 'exclusive bays 重叠 → SC-BPT-0002' },
  aMissingSectionContributionIsAComputationFailure: { claim: '缺失 section contribution 是 CTX-0001，不是 CTX-0006', detail: 'unreadable fold 仍是另一个 code' },
  aMissingHostValueIsUnreadableWithTheSuppliedAbsentCode: { claim: '缺失的 host 值是 unreadable，不是 mismatch', detail: '缺失快照值保留 caller 的 absent code' },
  theCurrentAttemptValueShadowsTheCachedValue: { claim: '当前 attempt 会遮蔽 cached context 值', detail: 'D-31 缓存是 host 拥有且次级的' },
  aSettleFailureReleasesInReverseOrder: { claim: 'set 的 settle 失败会按逆序释放已形成的结构', detail: '释放 claims，再对每个 formed instance 派发 Broken；removal 失败是 SC-SET-0007' },
  checkAllWritesNothingThenSettleAllWritesClaims: { claim: 'set check 准入两个成员，且不写任何 row', detail: 'union bounds 只 capture 一次；settle 写入三个 cell' },
  setCheckWritesNothingAndSetSettleWritesTheClaims: { claim: 'set 命令 check 不写，settle 写 claims', detail: 'dispatcher 上的内置 structurecumulus:set_demo' },
  aSatellitePlacedExactlyOnTheRootIsRejected: { claim: '正好落在 root 上的 satellite 会被拒绝', detail: 'compiler、StructureSet.place 和 host 都报 SC-SET-0008' },
  presenceAndEveryStructureAttemptShareOneBudgetCeiling: { claim: 'presence 和每个成员 attempt 共享一个 set budget', detail: 'Budget 3 下 presence (1) + root (1) + satellite (1)' },
  aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence: { claim: '手写 set 的 satellite 若与 root 同 siting，会在 presence 前被拒绝', detail: '真实 level GameTest：SC-SET-0008，没有 outcome、instance、row 或 revision' },
  aOneCellSetBudgetLeavesTheRootCheckUndecided: { claim: '一格 set budget 会让 root check 变成 undecided', detail: '真实 level GameTest：presence 用掉唯一一格，root 是 Undecided（SC-ENG-0001）' },
  rollbackRemovesTheKernelContextOfEveryFormedAttempt: { claim: 'set rollback 移除每个已形成的 instance 和它的 context', detail: 'claims 释放并派发 Broken；manager 一起移除 instance 和 context' },
  aFailedContextRemovalIsScSet0007AndKeepsTheOtherRemovals: { claim: 'removal 失败是 SC-SET-0007，且不阻断其余 rollback', detail: 'release verdict 不会跳过 manager 的 Broken 派发' },
  anEnvelopeOnlyBlueprintGetsTheLastBayAndCheck: { claim: '只有 envelope 的 blueprint 得到一个推导 bay 和 check', detail: 'D-30；空集合是 no-op' },
  chestInventoryIsTheLiveCapability: { claim: 'capability trait 就是 live capability 对象', detail: 'GameTest：IItemHandler，不做拷贝' },
  nestedAnySubjectBoundBodyFailsBuild: { claim: '绑定 subject 但不能 claim 的 focus 在 build 时被拒绝', detail: 'D-37：bay footprint 会 claim 时报告 SC-BPT-0005' },
  releaseTakesTheWholeInstanceAndBumpsOnlyWhenItRemovesRows: { claim: 'claim release 删除整个 instance 键', detail: 'D-38/D-45：没有 per-bay 或 per-row release；重复行会折叠' },
  waterInANotAirSlotIsAdmitted: { claim: 'NOTAIR slot 中的 water 会被接纳', detail: 'D-39：NOTAIR 精确等于 not(air())；water 是 non-air' },
  emptyAndNonFluidClassifyAirWaterLavaAndStone: { claim: 'empty 和 nonFluid 会分类 air、water、lava、stone', detail: 'D-39：empty = air 或 fluid block；nonFluid = 其余' },
  beginPublishesCommitsAndFindsByCell: { claim: 'commit 后的 attempt 变成带 bounds 的 instance', detail: 'D-40：begin 预留、settle 发布、commit(bounds) 形成；findAt 能找到' },
  abortDropsTheDraftAndReleasesTheSiting: { claim: 'abort 丢弃 draft 并释放 siting', detail: 'D-40：commit/abort 只能调用一次；aborted serial 不复用' },
  movingWriteBreaksTheStructureAndReleasesEveryClaimRow: { claim: 'moving write 会 break 结构并释放其全部 rows', detail: 'D-45：move 是移除不是平移；重建是一次新的 settle' },
  brokenAndInvalidatedAreIdempotent: { claim: 'Broken 和 Invalidated 是幂等的', detail: 'D-40：未知 id 是 no-op' },
  unloadedRemovesOnlyThatWorldAndLoadedRaisesTheSerial: { claim: 'Unloaded 只清一个 world，Loaded 抬高 serial', detail: 'D-40：per-world registry；Loaded 是唯一的批量替换' },
  blockPlaceDispatchesInvalidated: { claim: 'block place 通过 WatchEvents 派发 Invalidated', detail: 'D-40：adapter 负责侦测和派发；D-38 释放 claim rows' },
  publishAfterCommitUpdatesTheBoundInstance: { claim: 'commit 后的 publish 更新绑定的 instance context', detail: 'D-40：manager 整体替换 StructureInstance record' },
};

const TERMINAL = `$ tools/build-kernel.sh
  ✓ javac --release 21 -Werror
$ tools/test-kernel.sh
  572 tests / 572 successful / 0 failed
$ python3 tools/spec-check.py
  spec-check: ok
$ grep -rn "net.minecraft\\|net.neoforged\\|com.mojang" src/kernel/java
  none`;

export function Proof() {
  const { zh } = useLanguage();
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState('D-40');
  const [copied, setCopied] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return decisions;
    return decisions.filter((decision) => {
      const zhText = DECISION_ZH[decision.id];
      return `${decision.id} ${decision.title} ${decision.summary} ${zhText?.title ?? ''} ${zhText?.summary ?? ''}`.toLowerCase().includes(q);
    });
  }, [query, zh]);

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
        kicker={zh ? '证据 / review 收束' : 'Proof / the review closes'}
        title={zh ? <>设计只有能<em>跑</em>，才算真的。</> : <>A design is only real if it can <em>run.</em></>}
        note={zh ? <>六道 gate、572 个 kernel 测试（68 个 container）、443 个 adapter 单元测试（46 个 container）、140 个 GameTests、67 个 code、82 条决策（D-83）。adapter 已实现到 H1–H4、T1–T3、N1–N7 和 S5/S6：类型化 host 快照（D-33 取代 D-29）、推导 envelope（D-30）、cached context（D-31）、结构集合（D-32：<code>/cumulus set check|settle</code>、一次不带 orientation 的 union capture、内置 set_demo）、set 收束（D-35）、nested-extent lookup（D-36，被 design 13 的 capability/attachment 拆分取代）、claimability（D-37）、instance 键的 whole-key release（D-38/D-45）、empty/nonFluid（D-39）、host 驱动的 StructureManager（D-40：begin/commit/abort、context 绑定 instance、in-memory）和会话级 claims（D-44）。完整 <code>./gradlew check</code> 和 <code>runGameTestServer</code> 在当前 D-83 tree 都是绿的（140/140）。剩下按已测成本债行动（07 §7）和新的 review screenshots。</> : <>Six gates, 572 kernel tests in 68 containers, 443 adapter unit tests in 46 containers, 140 GameTests, 67 codes and 82 decisions (D-83). The adapter is implemented through H1–H4, T1–T3, N1–N7 and the S5/S6 set close-out: the typed host snapshot (D-33 supersedes D-29), the derived envelope (D-30), cached context (D-31), structure sets (D-32: <code>/cumulus set check|settle</code>, one unsited union capture, the built-in set_demo), the set close-out (D-35), the nested-extent lookup (D-36, superseded by design 13's capability/attachment split), claimability (D-37), the instance-keyed whole-key release (D-38/D-45), empty/nonFluid (D-39), the host-driven StructureManager (D-40: begin/commit/abort, instance-bound context, in-memory) and the session-scoped claims (D-44). A full <code>./gradlew check</code> and <code>runGameTestServer</code> are green on the current D-83 tree (140/140). Pending only the measured cost debts (07 §7) and fresh review screenshots.</>}
      />

      <Reveal>
        <div className="proof-top">
          <div className="proof-gates">
            <div className="eyebrow">{zh ? '当前 gate 报告 / 2026.11.07' : 'Current gate report / 2026.11.07'}</div>
            <div className="gate-grid" style={{ marginTop: 12 }}>
              {GATES.map((gate) => {
                const copy = zh ? ZH_GATES[gate.label] : gate;
                return (
                  <div className="gate" key={gate.label}>
                    <span className="v">{gate.value}</span>
                    <span className="l">{copy.label}</span>
                    <span className="n">{copy.note}</span>
                  </div>
                );
              })}
            </div>
            <div className="test-bars">
              <div className="eyebrow" style={{ marginBottom: 4 }}>{zh ? '按 package 的测试' : 'Tests by package'}</div>
              {TEST_GROUPS.map((group) => (
                <div className="test-bar" key={group.name}>
                  <span>{group.name}</span>
                  <span className="track"><span className="fill" style={{ display: 'block', width: `${(group.count / maxTests) * 100}%` }} /></span>
                  <span style={{ textAlign: 'right' }}>{group.count}</span>
                </div>
              ))}
              <div className="mono muted" style={{ marginTop: 9 }}>
                {TEST_COUNT} {zh ? '个测试' : 'tests'} / {testClassCount} {zh ? '个 class' : 'classes'} · {zh ? '68 个 JUnit container · adapter 443 / 46 个 container · GameTests 140/140' : '68 JUnit containers · adapter 443 / 46 containers · GameTests 140/140'}
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
            <div className="warn">check: ./gradlew check is green on the D-83 tree; the transcript above is the direct javac/JUnit harness. GameTests: runGameTestServer 140/140 (through N7).</div>
            <div style={{ height: 14 }} />
            <div className="cmd">{zh ? '下一个攻击点：已测成本债（07 §7），以及新的 review screenshots。' : 'Next attack: the measured cost debts (07 §7) and fresh review screenshots.'}</div>
          </div>
        </div>
      </Reveal>

      <Reveal delay={40}>
        <div style={{ marginTop: 26 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'baseline', flexWrap: 'wrap' }}>
            <div className="eyebrow">{zh ? '当前 ruling / D-30 … D-40' : 'Current rulings / D-30 … D-40'}</div>
            <div className="mono muted">{zh ? 'host 层与 kernel 的边界' : 'the host boundary around the kernel'}</div>
          </div>
          <div className="gate-grid" style={{ marginTop: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))' }}>
            {LAYERS.map((layer) => {
              const copy = zh ? ZH_LAYERS[layer.id] : layer;
              return (
                <div className="gate" key={layer.id}>
                  <span className="v">{layer.id}</span>
                  <span className="l">{copy.title}</span>
                  <span className="n">{copy.text}</span>
                </div>
              );
            })}
          </div>
        </div>
      </Reveal>

      <Reveal delay={60}>
        <div className="falsifiers">
          <div className="falsifiers-head">
            <div>
              <div className="eyebrow">{zh ? 'Falsifier / 这个 review 可能错的方式' : 'Falsifiers / the ways this review could be wrong'}</div>
              <div className="mono muted" style={{ marginTop: 4 }}>{zh ? '每个主张都有一个测试；设计退化时它会大声失败。' : 'Each claim has a test that would fail loudly if the design regressed.'}</div>
            </div>
            <span className="stamp">{zh ? '全部通过' : 'all green'}</span>
          </div>
          <div className="falsifier-grid">
            {FALSIFIERS.map((item) => (
              <div className="falsifier" key={item.gate}>
                <div className="falsifier-claim">{zh ? FALSIFIER_ZH[item.gate].claim : item.claim}</div>
                <div className="falsifier-gate">{item.gate}</div>
                <div className="falsifier-detail">{zh ? FALSIFIER_ZH[item.gate].detail : item.detail}</div>
                <span className="tag tag-green">{zh ? '通过' : 'pass'}</span>
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
                <div className="eyebrow">{zh ? '决策账本' : 'Decision ledger'} / {DECISION_COUNT} rulings</div>
                <div className="mono muted" style={{ marginTop: 4 }}>{zh ? '每条 ruling 都替换了旧设计；没有一条只是偏好。' : 'Every ruling replaced a previous design; none is a preference.'}</div>
              </div>
              <div style={{ width: 'min(100%, 260px)' }}>
                <input className="input" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={zh ? '搜索决策…' : 'search decisions…'} aria-label={zh ? '搜索决策' : 'Search decisions'} />
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
                  <span className="title">{zh ? DECISION_ZH[decision.id]?.title ?? decision.title : decision.title}</span>
                  <span className="summary">{zh ? DECISION_ZH[decision.id]?.summary ?? decision.summary : decision.summary}</span>
                </button>
              ))}
            </div>
            {filtered.length === 0 ? <div className="code-empty">{zh ? '没有决策匹配这个筛选。' : 'No decision matches this filter.'}</div> : null}
          </div>

          <aside className="decision-side">
            {active ? (
              <div className="decision-detail" key={active.id}>
                <div className="id">{active.id} / ruling</div>
                <h4>{zh ? DECISION_ZH[active.id]?.title ?? active.title : active.title}</h4>
                <p>{zh ? DECISION_ZH[active.id]?.summary ?? active.summary : active.summary}</p>
                <div className="producer"><strong>Source</strong>{" "}docs/design/05-decisions.md</div>
              </div>
            ) : (
              <div className="code-empty">{zh ? '没有选中 ruling。' : 'No ruling selected.'}</div>
            )}
          </aside>
        </div>
      </Reveal>

      <Reveal delay={120}>
        <div style={{ marginTop: 34 }}>
          <div className="eyebrow">{zh ? '重生协议 / 一个方向' : 'Regeneration protocol / one direction'}</div>
          <div className="regen">
            {REGEN.map((step) => (
              <div className="regen-step" key={step.n}>
                <span className="n">{step.n}</span>
                <span className="t">{zh ? step.tZh : step.t}</span>
                <span className="d">{zh ? step.dZh : step.d}</span>
              </div>
            ))}
          </div>
        </div>

        <div style={{ marginTop: 30 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'baseline', flexWrap: 'wrap' }}>
            <div className="eyebrow">{zh ? `读源文件 / ${DOCS.length} 份文档` : `Read the source / ${DOCS.length} documents`}</div>
            <div className="mono muted">{zh ? '点击复制路径 · 英文是唯一权威' : 'click to copy a path · English is the only authority'}</div>
          </div>
          <div className="doc-index">
            {DOCS.map((doc) => (
              <button key={doc.file} type="button" className="doc-item" onClick={() => copy(doc.file)}>
                <span className="g">{zh ? ZH_GROUPS[doc.group] ?? doc.group : doc.group}</span>
                <span className="t">{zh ? ZH_DOCS[doc.file] ?? doc.title : doc.title}</span>
                <span className="f">{copied === doc.file ? (zh ? '已复制到剪贴板' : 'copied to clipboard') : doc.file}</span>
              </button>
            ))}
          </div>
        </div>
      </Reveal>
    </section>
  );
}
