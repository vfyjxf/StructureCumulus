import { useState } from 'react';
import { PRINCIPLES, REMOVED } from '../data/site';
import { ZH_PRINCIPLES, ZH_REMOVED } from '../data/site.zh';
import { useLanguage } from '../i18n/Language';
import { Reveal, SectionHead } from '../components/ui';

export function Thesis() {
  const { zh } = useLanguage();
  const [active, setActive] = useState(0);
  const removed = REMOVED[active];

  return (
    <section className="section" id="thesis" data-section>
      <SectionHead
        no="00"
        kicker={zh ? '论证' : 'The argument'}
        title={zh ? <>kernel 是一个<em>证明对象</em>，不是带着文档的 runtime。</> : <>The kernel is a <em>proof object</em>, not a runtime with a document attached.</>}
        note={zh ? <>每个状态只有一个所有者。每份报告都是值。每个模块是一个 rank，不是文件夹。设计之所以小，是因为替代方案被删掉了，不是因为问题小。</> : <>Every state has one owner. Every report is a value. Every module is a rank, not a folder. The design is small because the alternatives were deleted, not because the problem is small.</>}
      />

      <Reveal>
        <div className="thesis-grid">
          <div className="thesis-main">
            <h3>
              {zh ? <>blueprint 是<em>声明</em>。<br />match 是<em>论证</em>。<br />settlement 是一次 claim 写入 + 一次<em>发布</em>。</> : <>A blueprint is a <em>declaration</em>.<br />A match is an <em>argument</em>.<br />A settlement is one claim write and one <em>publication</em>.</>}
            </h3>
            <p>
              {zh
                ? '没有要推进的 stage machine，没有要 patch 的可变 context 草稿，也没有要调和的 claim plan。check 读一个不可变 view；settle 写一个 ledger revision、发布一个 context revision；derive 再把已发布的值折叠一次。'
                : 'There is no stage machine to advance, no mutable context draft to patch, and no claim plan to reconcile. The check reads one immutable view; the settle writes one ledger revision and publishes one context revision; the derive folds the published values once more.'}
            </p>
            <div className="thesis-list">
              <div>
                <dt>{zh ? '一份 IR' : 'One IR'}</dt>
                <dd>{zh ? <>JSON 数据和 Java builder 下沉到同一个 <code>Blueprint</code> 值。</> : <>JSON data and a Java builder lower to the same <code>Blueprint</code> value.</>}</dd>
              </div>
              <div>
                <dt>{zh ? '一个 key' : 'One key'}</dt>
                <dd>{zh ? <><code>ContextKey&lt;T&gt;</code> 携带 id、type、retention 和 merge。没有别的东西标识一个值。</> : <><code>ContextKey&lt;T&gt;</code> carries id, type, retention and merge. Nothing else identifies a value.</>}</dd>
              </div>
              <div>
                <dt>{zh ? '一个账本' : 'One ledger'}</dt>
                <dd>{zh ? <>Claim 按 world 划分；row 携带 cell × owner × siting × bay。<code>Admitted</code> 只带 bindings 和 revision，从不带 rows。</> : <>Claims are per world; rows carry cell × owner × siting × bay. <code>Admitted</code> carries bindings and a revision, never rows.</>}</dd>
              </div>
              <div>
                <dt>{zh ? '一个方向' : 'One direction'}</dt>
                <dd>{zh ? 'package 图有 rank，并且会被检查。向上 import 是设计变更，不是重构。' : 'The package graph is ranked and checked. An upward import is a design change, not a refactor.'}</dd>
              </div>
              <div>
                <dt>{zh ? '一次 host 读取' : 'One host read'}</dt>
                <dd>{zh ? <><code>HostType&lt;H&gt;</code> + <code>WorldView.get</code> 从一份快照读取；descriptor 仍是 <code>(type, kind)</code>，property/state/tag/fluid 是 adapter leaf。</> : <><code>HostType&lt;H&gt;</code> + <code>WorldView.get</code> read one snapshot. The descriptor stays <code>(type, kind)</code>; property, state, tag and fluid are adapter leaves.</>}</dd>
              </div>
            </div>
            <div className="big-rule" />
            <blockquote className="thesis-quote">
              <p>{zh ? '“如果代码和设计冲突，以设计为准，代码重新生成。”' : '“If the code and the design disagree, the design wins and the code is regenerated.”'}</p>
              <cite>{zh ? '— docs/README.md，唯一规则' : '— docs/README.md, the one rule'}</cite>
            </blockquote>
          </div>

          <aside className="thesis-side">
            <h4>{zh ? '这套 review 反复回到的五条法' : 'The five laws this review keeps returning to'}</h4>
            <div className="principles">
              {PRINCIPLES.map((principle) => {
                const copy = zh ? ZH_PRINCIPLES[principle.n] : principle;
                return (
                  <div className="principle" key={principle.n}>
                    <span className="n">{principle.n}</span>
                    <div>
                      <b>{copy.title}</b>
                      <span>{copy.text}</span>
                    </div>
                  </div>
                );
              })}
            </div>
            <div style={{ marginTop: 22 }}>
              <div className="eyebrow">{zh ? 'Review 姿态' : 'Review posture'}</div>
              <p style={{ marginTop: 8, fontSize: 11, lineHeight: 1.55, color: 'var(--muted)' }}>
                {zh
                  ? '这不是功能导览。每一章都是 kernel 必须活下来的一个主张：一次 frame 映射、一次写入、一次冲突、一个缺失值、一个 finding。'
                  : 'This is not a feature tour. Each chapter is a claim the kernel must survive: a frame mapping, a write, a conflict, a missing value, a finding.'}
              </p>
              <div className="stamp" style={{ marginTop: 14 }}>{zh ? '对抗式审查' : 'adversarial reviewed'}</div>
            </div>
          </aside>
        </div>
      </Reveal>

      <Reveal delay={100}>
        <div className="subtraction">
          <div className="subtraction-head">
            <h4>{zh ? '被刻意删掉的东西' : 'What is deliberately missing'}</h4>
            <span className="mono muted">{zh ? '点一个被删概念' : 'click a deleted concept'}</span>
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
                  <span>{zh ? ZH_REMOVED[item.term] : item.why}</span>
                </button>
              ))}
            </div>
            <div className="removed-detail">
              <div className="kicker">{zh ? '已删除' : 'Deleted'} / {String(active + 1).padStart(2, '0')}</div>
              <div className="removed-detail-term">{removed.term}</div>
              <div className="removed-detail-arrow">→ {removed.replacement}</div>
              <p>{zh ? ZH_REMOVED[removed.term] : removed.why}</p>
              <div className="mono muted" style={{ marginTop: 14 }}>{zh ? '替代物是值或法律，不是 flag' : 'the replacement is a value or a law, not a flag'}</div>
            </div>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
