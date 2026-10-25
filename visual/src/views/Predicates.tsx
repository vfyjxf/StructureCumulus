import { useEffect, useMemo, useState } from 'react';
import { Panel } from '../components/Panel';
import { StatusChip, replyKind } from '../components/StatusChip';
import { WhyNote } from '../components/WhyNote';
import { Segmented, Toggle, TextField, SelectField } from '../components/Controls';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  BLOCK_OPTIONS,
  COMBINATOR_TABLE,
  DEFAULT_SAMPLE,
  EXPECTATIONS,
  FOCUSES,
  MISMATCH_FINDING_CODE,
  PREDICATE_SCENARIOS,
  REPLY_LADDER,
  REPLY_ORDER,
  SUBJECT_MISMATCH_CODE,
  SUBJECT_MISMATCH_MESSAGE,
  evalNode,
  leafText,
  makeCombinator,
  makeLeaf,
  type ExpectId,
  type FocusId,
  type PredNode,
  type PredicatePhase,
  type PredicateScenarioId,
  type ReplyState,
  type Sample,
} from '../data/predicates';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

const initialRoot = (): PredNode => ({
  id: 'root',
  kind: 'allOf',
  children: [makeLeaf('block')],
});

const removeNode = (node: PredNode, id: string): PredNode => ({
  ...node,
  children: node.children.filter((child) => child.id !== id).map((child) => removeNode(child, id)),
});

function TreeRow({
  node,
  depth,
  root,
  focus,
  sample,
  selectedId,
  activeId,
  onSelect,
  onRemove,
}: {
  node: PredNode;
  depth: number;
  root: boolean;
  focus: FocusId;
  sample: Sample;
  selectedId: string;
  activeId: string | null;
  onSelect: (id: string) => void;
  onRemove: (id: string) => void;
}) {
  const t = useT();
  const reply = evalNode(node, focus, sample);
  const glyph = node.kind === 'allOf' ? '∧' : node.kind === 'anyOf' ? '∨' : node.kind === 'not' ? '¬' : '·';
  const expr =
    node.kind === 'allOf' || node.kind === 'anyOf' || node.kind === 'not'
      ? `${node.kind}(${fmt(
          node.children.length === 1 ? t.predicates.childrenOne : t.predicates.childrenMany,
          { n: node.children.length },
        )})`
      : leafText(node);
  return (
    <>
      <div
        className={`pred-node${depth > 0 ? ' pred-node--child' : ''}`}
        data-selected={node.id === selectedId}
        data-active={node.id === activeId}
        style={{ marginLeft: depth * 16 }}
        onClick={() => onSelect(node.id)}
        role="button"
        tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onSelect(node.id);
          }
        }}
        aria-pressed={node.id === selectedId}
        aria-label={`${expr}, reply ${reply}`}
      >
        <span className="pred-glyph">{glyph}</span>
        <div>
          <div className="pred-expr">{expr}</div>
          {node.kind === 'allOf' || node.kind === 'anyOf' || node.kind === 'not' ? (
            <div className="pred-sub">{root ? t.predicates.root : t.predicates.nestedCombinator}</div>
          ) : (
            <div className="pred-sub">{fmt(t.predicates.leaf, { kind: node.kind })}</div>
          )}
        </div>
        <div className="row" style={{ gap: 4 }}>
          <StatusChip kind={replyKind(reply)} size="sm">
            {reply}
          </StatusChip>
          {root ? null : (
            <button
              type="button"
              className="pred-remove"
              aria-label={fmt(t.predicates.removeNode, { expr })}
              onClick={(event) => {
                event.stopPropagation();
                onRemove(node.id);
              }}
            >
              ×
            </button>
          )}
        </div>
      </div>
      {node.children.map((child) => (
        <TreeRow
          key={child.id}
          node={child}
          depth={depth + 1}
          root={false}
          focus={focus}
          sample={sample}
          selectedId={selectedId}
          activeId={activeId}
          onSelect={onSelect}
          onRemove={onRemove}
        />
      ))}
    </>
  );
}

export function PredicatesView() {
  const [focusId, setFocusId] = useState<FocusId>('occupant');
  const [expectId, setExpectId] = useState<ExpectId>('block');
  const [typedSubject, setTypedSubject] = useState('OccupantSnapshot');
  const [sample, setSample] = useState<Sample>(DEFAULT_SAMPLE);
  const [root, setRoot] = useState<PredNode>(initialRoot);
  const [selectedNode, setSelectedNode] = useState('root');
  const totalSteps = 3 + root.children.length;
  const { scenario, setScenario, step, setStep } = useScenarioState<PredicateScenarioId>({
    page: 'predicates',
    scenarios: PREDICATE_SCENARIOS,
    defaultScenario: 'match',
    defaultStep: 3,
    stepCount: totalSteps,
  });

  const t = useT();
  const c = useLang().c;

  const preset = PREDICATE_SCENARIOS.find((item) => item.id === scenario) ?? PREDICATE_SCENARIOS[0];

  useEffect(() => {
    setFocusId('occupant');
    setExpectId('block');
    setRoot(initialRoot());
    setSelectedNode('root');
    setTypedSubject('OccupantSnapshot');
    setSample(preset.sample);
    // A scenario seeds the sandbox once; later edits belong to the reviewer.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [preset]);

  const activeStep = Math.min(step, totalSteps - 1);
  const phase: PredicatePhase =
    activeStep === 0 ? 'focus' : activeStep === 1 ? 'subject' : activeStep === totalSteps - 1 ? 'reply' : 'child';
  const activeChildIndex = phase === 'child' ? activeStep - 2 : -1;
  const activeNodeId = phase === 'child' ? (root.children[activeChildIndex]?.id ?? null) : null;
  const playback = usePlayback(totalSteps, { step: activeStep, onStep: setStep });

  const focus = FOCUSES.find((f) => f.id === focusId) ?? FOCUSES[0];
  const expectation = EXPECTATIONS.find((e) => e.id === expectId) ?? EXPECTATIONS[0];
  const required = expectation.subject(focus);

  const replyNote: Record<ReplyState, string> = {
    MATCH: t.predicates.replyMatch,
    MISMATCH: t.predicates.replyMismatch,
    UNREADABLE: t.predicates.replyUnreadable,
    ABSTAIN: t.predicates.replyAbstain,
  };

  const subjectError = useMemo(() => {
    if (typedSubject.trim() !== focus.subject) {
      return fmt(t.predicates.subjectMismatch, {
        typed: typedSubject.trim() || '—',
        expr: focus.expr,
        subject: focus.subject,
      });
    }
    if (typedSubject.trim() !== required) {
      return fmt(t.predicates.expectationMismatch, {
        required,
        typed: typedSubject.trim(),
        message: c(SUBJECT_MISMATCH_MESSAGE),
      });
    }
    return null;
  }, [typedSubject, focus, required, t, c]);

  const reply = subjectError ? null : evalNode(root, focusId, sample);

  const onFocusChange = (next: FocusId) => {
    setFocusId(next);
    const spec = FOCUSES.find((f) => f.id === next);
    if (spec) setTypedSubject(spec.subject);
    setRoot(initialRoot());
    setSelectedNode('root');
  };

  const addLeaf = () => {
    setRoot((current) => ({ ...current, children: [...current.children, makeLeaf(expectId)] }));
  };
  const addCombinator = (kind: 'allOf' | 'anyOf' | 'not') => {
    setRoot((current) => ({ ...current, children: [...current.children, makeCombinator(kind, expectId)] }));
  };

  const selected = (() => {
    const find = (node: PredNode): PredNode | null => {
      if (node.id === selectedNode) return node;
      for (const child of node.children) {
        const hit = find(child);
        if (hit) return hit;
      }
      return null;
    };
    return find(root) ?? root;
  })();

  const checkCode = `new Check<${typedSubject || '?'}>(
  NamespacedId.of("mymod", "lit"), Stage.FORM, fireBay,
  ${focus.expr},
  ${expectation.expr(focus)},
  8)`;

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.predicates.why}</WhyNote>
      </div>

      <div className="c4 np-phase" data-phase="focus" data-active={phase === 'focus'}>
        <Panel title={t.predicates.focusExpectation} count={t.predicates.focusCount}>
          <Segmented
            label={t.predicates.focusLabel}
            options={FOCUSES.map((f) => ({ value: f.id, label: c(f.label), title: f.subject }))}
            value={focusId}
            onChange={(value) => onFocusChange(value as FocusId)}
          />
          <Segmented
            label={t.predicates.expectationLabel}
            options={EXPECTATIONS.map((e) => ({ value: e.id, label: c(e.label) }))}
            value={expectId}
            onChange={(value) => setExpectId(value as ExpectId)}
          />
          <div className="np-phase-block" data-active={phase === 'subject'}>
            <TextField
              label={t.predicates.declaredSubject}
              value={typedSubject}
              onChange={setTypedSubject}
              invalid={Boolean(subjectError)}
              describedBy="subject-error"
            />
            <div className="formula formula--plain">
              focus.subject() = {focus.subject}
              <br />
              expectation.subject() = {required}
            </div>
          </div>
          <div className="note">{c(focus.line)}</div>
        </Panel>
      </div>

      <div className="c8">
        <Panel
          title={t.predicates.tree}
          count={fmt(t.predicates.treeCount, { n: root.children.length })}
          error={Boolean(subjectError)}
          actions={
            <button
              type="button"
              className="btn btn--sm"
              onClick={() => {
                setRoot(initialRoot());
                setSelectedNode('root');
              }}
            >
              {t.predicates.reset}
            </button>
          }
        >
          {subjectError ? (
            <div className="error-line" id="subject-error" role="alert">
              <StatusChip kind="fail" size="sm" solid>
                {SUBJECT_MISMATCH_CODE}
              </StatusChip>
              <span>{subjectError}</span>
            </div>
          ) : null}

          <div className="np-demo">
            <ScenarioPicker
              scenarios={PREDICATE_SCENARIOS}
              value={scenario}
              onChange={(id) => {
                setScenario(id, 0);
                playback.pause();
              }}
            />
            <PlaybackBar
              step={activeStep}
              count={totalSteps}
              playing={playback.playing}
              onToggle={playback.toggle}
              onStep={setStep}
              speed={playback.speed}
              onSpeed={playback.setSpeed}
              caption={
                <>
                  <span className="mono">
                    {phase === 'child'
                      ? `${t.demo.predicateChild} ${activeChildIndex + 1}/${root.children.length}`
                      : phase === 'focus'
                        ? t.demo.predicateFocus
                        : phase === 'subject'
                          ? sample.refused
                            ? t.demo.predicateMissing
                            : t.demo.predicateFound
                          : t.demo.predicateReply}
                  </span>
                  <span className="np-playback-sep"> · </span>
                  {phase === 'reply'
                    ? t.demo.revealed.replace('{reply}', subjectError ? t.predicates.notBuilt : (reply ?? 'ABSTAIN'))
                    : c(focus.expr)}
                </>
              }
            />
          </div>

          <div className="pred-tree">
            <TreeRow
              node={root}
              depth={0}
              root
              focus={focusId}
              sample={sample}
              selectedId={selectedNode}
              activeId={activeNodeId}
              onSelect={setSelectedNode}
              onRemove={(id) => setRoot((current) => removeNode(current, id))}
            />
          </div>

          <div className="row">
            <span className="micro">{t.predicates.addChild}</span>
            <button type="button" className="btn btn--sm" onClick={addLeaf}>
              + {expectId}
            </button>
            <button type="button" className="btn btn--sm" onClick={() => addCombinator('allOf')}>
              + allOf
            </button>
            <button type="button" className="btn btn--sm" onClick={() => addCombinator('anyOf')}>
              + anyOf
            </button>
            <button type="button" className="btn btn--sm" onClick={() => addCombinator('not')}>
              + not
            </button>
          </div>

          <div className="row" style={{ alignItems: 'stretch', gap: 12 }}>
            <div style={{ flex: '1 1 180px' }}>
              <div
                className="verdict"
                data-active={phase === 'reply'}
                data-state={reply === null ? 'unknown' : reply === 'MATCH' ? 'ok' : reply === 'MISMATCH' ? 'fail' : 'unknown'}
              >
                <div className="verdict-bar" aria-hidden="true" />
                <div className="verdict-body">
                  <div className="micro">{t.predicates.simulatedReply}</div>
                  <div className="verdict-arm" style={{ fontSize: 32, lineHeight: '32px', letterSpacing: '-0.02em' }}>
                    {phase !== 'reply' ? '…' : subjectError ? t.predicates.notBuilt : reply}
                  </div>
                  <div className="verdict-detail">
                    {phase !== 'reply'
                      ? c(focus.expr)
                      : subjectError
                        ? t.predicates.constructorRefused
                        : replyNote[reply ?? 'ABSTAIN']}
                  </div>
                </div>
              </div>
            </div>
            <div style={{ flex: '1 1 240px' }}>
              <dl className="kv">
                <dt>{t.predicates.node}</dt>
                <dd>{selected.id === 'root' ? t.predicates.root : selected.id}</dd>
                <dt>{t.predicates.subtree}</dt>
                <dd>{selected.kind}</dd>
                <dt>{t.predicates.subtreeReply}</dt>
                <dd>{subjectError ? '—' : evalNode(selected, focusId, sample)}</dd>
              </dl>
            </div>
          </div>

          {reply === 'MISMATCH' ? (
            <div className="tbl-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t.predicates.thFinding}</th>
                    <th>{t.predicates.thSeverity}</th>
                    <th>{t.predicates.thLocus}</th>
                    <th>{t.predicates.thMessage}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td className="mono-cell">{MISMATCH_FINDING_CODE}</td>
                    <td>
                      <StatusChip kind="fail" size="sm">
                        {t.predicates.error}
                      </StatusChip>
                    </td>
                    <td className="mono-cell">LOCAL (1,0,1)</td>
                    <td className="wrap-cell">{t.predicates.leafMessage}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          ) : null}

          <pre className="code">{checkCode}</pre>
        </Panel>
      </div>

      <div className="c4">
        <Panel title={t.predicates.specimen} count={t.predicates.specimenCount}>
          <SelectField
            label={t.predicates.occupantBlock}
            value={sample.block}
            options={BLOCK_OPTIONS.map((b) => ({ value: b, label: b }))}
            onChange={(block) => setSample({ ...sample, block })}
          />
          <Toggle pressed={sample.lit} onChange={(lit) => setSample({ ...sample, lit })}>
            {t.predicates.campfireLit}
          </Toggle>
          <Toggle
            pressed={sample.traits.includes('structurecumulus:item_inventory')}
            onChange={(next) =>
              setSample({
                ...sample,
                traits: next
                  ? [...sample.traits, 'structurecumulus:item_inventory']
                  : sample.traits.filter((trait) => trait !== 'structurecumulus:item_inventory'),
              })
            }
          >
            {t.predicates.hasItemInventory}
          </Toggle>
          <Toggle
            pressed={sample.refused}
            onChange={(refused) => {
              setSample({ ...sample, refused });
              playback.pause();
              if (refused) setStep(totalSteps - 1);
            }}
          >
            {t.predicates.sectionRefused}
          </Toggle>
          <div className="note">{t.predicates.specimenNote}</div>
        </Panel>
      </div>

      <div className="c8">
        <Panel title={t.predicates.fourReplies} count={t.predicates.fourRepliesCount}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.predicates.thReply}</th>
                  <th>{t.predicates.thPrecedence}</th>
                  <th>{t.predicates.thMeaning}</th>
                  <th>{t.predicates.thCode}</th>
                </tr>
              </thead>
              <tbody>
                {REPLY_LADDER.map((row) => {
                  const isCurrent = reply === row.state;
                  return (
                    <tr key={row.state} data-selected={isCurrent}>
                      <td>
                        <StatusChip kind={replyKind(row.state)} size="sm">
                          {row.state}
                        </StatusChip>
                      </td>
                      <td className="num">
                        {String(4 - REPLY_ORDER.indexOf(row.state as (typeof REPLY_ORDER)[number])).padStart(2, '0')}
                      </td>
                      <td className="wrap-cell">{c(row.note)}</td>
                      <td className="mono-cell">{c(row.code)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="formula">{t.predicates.attemptFormula}</div>
          <p className="note">
            {t.predicates.attemptNote} <span className="mono">allOf</span> {t.predicates.attemptNoteTail}
          </p>
        </Panel>
      </div>

      <div className="c12">
        <Panel title={t.predicates.combinators} count={t.predicates.combinatorsCount}>
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t.predicates.thOp}</th>
                  <th>{t.predicates.thMatch}</th>
                  <th>{t.predicates.thMismatch}</th>
                  <th>{t.predicates.thUnknown}</th>
                </tr>
              </thead>
              <tbody>
                {COMBINATOR_TABLE.map((row) => (
                  <tr key={row.op}>
                    <td className="mono-cell">{row.op}</td>
                    <td className="wrap-cell">{c(row.match)}</td>
                    <td className="wrap-cell">{c(row.mismatch)}</td>
                    <td className="wrap-cell">{c(row.unknown)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="note">{t.predicates.combinatorNote}</div>
          <p className="small muted">{c(expectation.line)}</p>
        </Panel>
      </div>
    </div>
  );
}
