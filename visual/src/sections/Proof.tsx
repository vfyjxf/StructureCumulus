import { useEffect, useMemo, useState } from 'react';
import { decisions, DECISION_COUNT } from '../data/decisions';
import { TEST_GROUPS, TEST_COUNT, testClasses } from '../data/tests';
import { DOCS, GATES } from '../data/site';
import { DECISION_ZH } from '../data/decisions.zh';
import { ZH_DOCS, ZH_GATES, ZH_GROUPS } from '../data/site.zh';
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
];

const FALSIFIER_ZH: Record<string, { claim: string; detail: string }> = {
  sectionComputationMapsThroughSiting: { claim: 'section computation 通过 siting 读 world', detail: '非零 origin + 被拒绝的 local section' },
  aNoOpReleaseKeepsRevisionZero: { claim: 'no-op release 可以保持 revision 0', detail: '新 ledger，空 release' },
  campfireChecksSettlesAndDerives: { claim: 'derive 不会重复收取 settle 的 spend', detail: '两次调用都等于 settle spend' },
  aClippedEntityFootprintIsUnreadable: { claim: '被裁剪的 entity 是 unreadable，不是 clipped match', detail: '没有写入任何 row' },
  documentBuildErrorsKeepTheirSpecificCode: { claim: 'document 构建错误保留具体 code', detail: 'exclusive bays 重叠 → SC-BPT-0002' },
  aMissingSectionContributionIsAComputationFailure: { claim: '缺失 section contribution 是 CTX-0001，不是 CTX-0006', detail: 'unreadable fold 仍是另一个 code' },
};

const TERMINAL = `$ tools/build-kernel.sh
  ✓ javac --release 21 -Werror
$ tools/test-kernel.sh
  116 tests / 116 successful / 0 failed
$ python3 tools/spec-check.py
  spec-check: ok
$ grep -rn "net.minecraft\\|net.neoforged\\|com.mojang" src/kernel/java
  none`;

export function Proof() {
  const { zh } = useLanguage();
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState('D-22');
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
        note={zh ? <>六道 gate、116 个 kernel 测试、106 个 adapter 单元测试、56 个 code、27 条决策。六道 gate 全部通过。adapter 已提交到 M4：M2c 的 Model C、body identity、total read 和四态 traits；M2d 的 nested extents、frames 和 cuts；M3 的 <code>SavedClaims</code>/<code>SavedContexts</code> 在 <code>d4b6d82</code> 提交，兼容 Model C。M4a JSON reader/writer 在 <code>f2659c7</code> 提交；M4b 的 datapack reload 在 <code>76a8e5c</code> 提交；命令切片（settle 在 <code>4899d86</code>，另有 compile/explain/claims）已实现；M5 的 determinism gate 在 <code>DeterminismTest</code> 里落地，capture-revision lease invalidation 在 <code>0f0bf25</code> 提交；watched-region mark counter 仍待完成。非 upright pitch/roll、<code>SC-MC-0004</code>/<code>0005</code> emission 和 deferred <code>SC-MC-0003</code> channel 仍待完成。</> : <>Six gates, 116 kernel tests, 106 adapter unit tests, 56 codes and 27 decisions. All six gates are green. The adapter is committed through M4: M2c Model C, body identity, total read and four-state traits; M2d nested extents, frames and cuts; M3 <code>SavedClaims</code>/<code>SavedContexts</code> at <code>d4b6d82</code>, compatible with Model C. M4a JSON reader/writer at <code>f2659c7</code>; M4b datapack reload at <code>76a8e5c</code>; the command slice (settle at <code>4899d86</code>, plus compile, explain, claims); M5 determinism gate landed in <code>DeterminismTest</code> and capture-revision lease invalidation at <code>0f0bf25</code>; the watched-region mark counter remains pending. Non-upright pitch/roll, <code>SC-MC-0004</code>/<code>0005</code> emission and the deferred <code>SC-MC-0003</code> channel remain pending.</>}
      />

      <Reveal>
        <div className="proof-top">
          <div className="proof-gates">
            <div className="eyebrow">{zh ? '当前 gate 报告 / 2026.10.27' : 'Current gate report / 2026.10.27'}</div>
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
                {TEST_COUNT} {zh ? '个测试' : 'tests'} / {testClassCount} {zh ? '个 class' : 'classes'} · 16 JUnit containers
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
            <div className="warn">check: ./gradlew check is green; the transcript above is the direct javac/JUnit harness. GameTests: runGameTestServer 32/32 (M1 capture/host; M2a entity precedence; M2b orientation; M3 claims; M2c traits; M2d nested frames; lease lifecycle; M4 command surface).</div>
            <div style={{ height: 14 }} />
            <div className="cmd">{zh ? '下一个攻击点：M5 的 watched-region mark counter、非 upright tilt 与 SC-MC-0004/0005 emission、SC-MC-0003 channel，以及更新的 review screenshots。' : 'Next attack: the M5 watched-region mark counter, non-upright tilt and SC-MC-0004/0005 emission, the SC-MC-0003 channel, and fresh review screenshots.'}</div>
          </div>
        </div>
      </Reveal>

      <Reveal delay={60}>
        <div className="falsifiers">
          <div className="falsifiers-head">
            <div>
              <div className="eyebrow">{zh ? 'Falsifier / 这个 review 可能错的六种方式' : 'Falsifiers / six ways this review could be wrong'}</div>
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
            <div className="eyebrow">{zh ? '读源文件 / 21 份文档' : 'Read the source / 21 documents'}</div>
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
