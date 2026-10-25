import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Panel } from '../components/Panel';
import { StatusChip } from '../components/StatusChip';
import { WhyNote } from '../components/WhyNote';
import { DiagramSvg, NodeBox, Wire } from '../components/Diagram';
import { Toggle, SelectField } from '../components/Controls';
import { PlaybackBar, usePlayback } from '../components/Playback';
import { ScenarioPicker, useScenarioState } from '../components/ScenarioPicker';
import {
  AUTHORING_ANCHORS,
  AUTHORING_LOWERING,
  AUTHORING_SCENARIOS,
  CAMPFIRE_ROWS,
  COMPACT_CANONICAL,
  EXPECT_RULES,
  GRAPH_EDGES,
  GRAPH_NODES,
  MAPPINGS,
  type AuthoringScenarioId,
  type JsonRow,
} from '../data/authoring';
import { useLang, useT } from '../i18n/LangContext';
import { fmt } from '../i18n/dict';

function JsonToken({ row }: { row: JsonRow }) {
  if (row.kind === 'open') {
    return (
      <span>
        <span className="json-key">"{row.key}"</span>
        <span className="json-punct">: {'{'}</span>
      </span>
    );
  }
  if (row.kind === 'close') {
    return <span className="json-punct">{row.value}</span>;
  }
  if (row.kind === 'item') {
    return <span className="json-val">{row.value}</span>;
  }
  return (
    <span>
      <span className="json-key">"{row.key}"</span>
      <span className="json-punct">: </span>
      <span className={row.value?.startsWith('"') ? 'json-val json-val--str' : 'json-val'}>{row.value}</span>
      <span className="json-punct">,</span>
    </span>
  );
}

export function AuthoringView() {
  const [selectedId, setSelectedId] = useState('r4');
  const [canonical, setCanonical] = useState(false);
  const { scenario, setScenario, step, setStep } = useScenarioState<AuthoringScenarioId>({
    page: 'authoring',
    scenarios: AUTHORING_SCENARIOS,
    defaultScenario: 'lowering',
    defaultStep: 0,
    stepCount: AUTHORING_LOWERING.length,
  });
  const steps = scenario === 'lowering' ? AUTHORING_LOWERING : AUTHORING_ANCHORS;
  const activeStep = Math.min(step, steps.length - 1);
  const currentStep = steps[activeStep];
  const playback = usePlayback(steps.length, { step: activeStep, onStep: setStep });

  const t = useT();
  const c = useLang().c;

  useEffect(() => {
    setSelectedId(currentStep.rowId);
    // Step changes drive the highlight; manual row clicks do not move the step.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentStep]);

  const row = CAMPFIRE_ROWS.find((r) => r.id === selectedId) ?? CAMPFIRE_ROWS[3];
  const mapping = MAPPINGS.find((m) => m.id === row.mapsTo);
  const mappedNodeId = mapping?.id;

  const statusChip = (status: 'yes' | 'partial' | 'no') => {
    if (status === 'yes') return <StatusChip kind="ok" size="sm">{t.authoring.built}</StatusChip>;
    if (status === 'partial') return <StatusChip kind="warn" size="sm">{t.authoring.partial}</StatusChip>;
    return <StatusChip kind="unknown" size="sm">{t.authoring.specOnly}</StatusChip>;
  };

  const jumpOptions = useMemo(
    () =>
      CAMPFIRE_ROWS.filter((r) => r.mapsTo).map((r) => ({
        value: r.id,
        label: r.path,
      })),
    [],
  );

  const selectNode = (nodeId: string) => {
    const target = CAMPFIRE_ROWS.find((r) => r.mapsTo === nodeId);
    if (target) setSelectedId(target.id);
    const index = steps.findIndex((entry) => entry.nodeId === nodeId);
    if (index >= 0) setStep(index);
  };

  return (
    <div className="g12">
      <div className="c12">
        <WhyNote>{t.authoring.why}</WhyNote>
      </div>

      <div className="c12 split-rail">
        <Panel
          title={t.authoring.document}
          count={t.authoring.documentCount}
          actions={<span className="micro">{t.authoring.documentAction}</span>}
        >
          <div className="json-tree" role="group" aria-label={t.authoring.treeAria}>
            {CAMPFIRE_ROWS.map((item) => (
              <button
                key={item.id}
                type="button"
                className="json-row"
                style={{ '--depth': item.depth } as CSSProperties}
                aria-pressed={item.id === selectedId}
                data-demo={item.id === currentStep.rowId}
                title={item.value ?? item.key ?? item.path}
                onClick={() => {
                  setSelectedId(item.id);
                  const index = steps.findIndex((entry) => entry.rowId === item.id);
                  if (index >= 0) setStep(index);
                }}
                onMouseEnter={() => setSelectedId(item.id)}
              >
                <span style={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  <JsonToken row={item} />
                </span>
                {item.mapsTo ? (
                  <span className="json-map">{MAPPINGS.find((m) => m.id === item.mapsTo)?.javaType.split(' ')[0]}</span>
                ) : (
                  <span />
                )}
              </button>
            ))}
          </div>
          <div className="note">{fmt(t.authoring.documentNote, { path: row.path })}</div>
        </Panel>

        <div className="stack">
          <Panel
            title={t.authoring.fieldObject}
            count={mapping ? mapping.status.toUpperCase() : t.authoring.unmapped}
          >
            <div className="np-demo">
              <ScenarioPicker
                scenarios={AUTHORING_SCENARIOS}
                value={scenario}
                onChange={(id) => {
                  setScenario(id, 0);
                  playback.pause();
                }}
              />
              <PlaybackBar
                step={activeStep}
                count={steps.length}
                playing={playback.playing}
                onToggle={playback.toggle}
                onStep={setStep}
                speed={playback.speed}
                onSpeed={playback.setSpeed}
                caption={
                  <>
                    <span className="mono">{currentStep.code}</span>
                    <span className="np-playback-sep"> · </span>
                    {c(currentStep.title)}
                  </>
                }
              />
            </div>
            <div className="np-demo-readout" aria-live="polite">
              <div className="micro">{c(currentStep.title)}</div>
              <p className="small">{c(currentStep.carries)}</p>
            </div>
            <SelectField
              label={t.authoring.jumpToField}
              value={row.id}
              options={jumpOptions}
              onChange={(value) => setSelectedId(value)}
            />
            <dl className="kv">
              <dt>{t.authoring.jsonPath}</dt>
              <dd>{row.path}</dd>
              <dt>{t.authoring.javaType}</dt>
              <dd>{mapping?.javaType ?? '—'}</dd>
            </dl>
            <div>
              <div className="micro">{t.authoring.runtimeRole}</div>
              <p className="small" style={{ marginTop: 2 }}>
                {mapping ? c(mapping.runtimeRole) : t.authoring.unmappedNote}
              </p>
            </div>
            {mapping ? <div className="row">{statusChip(mapping.status)}</div> : null}
            {row.mapsTo === 'cuboid' ? (
              <div className="note">
                <strong>[a, b, c, d, e, f]</strong>
                {t.authoring.cuboidNoteLead}
              </div>
            ) : null}
          </Panel>

          <Panel
            title={t.authoring.expectTag}
            count={t.authoring.expectTagCount}
            actions={
              <Toggle pressed={canonical} onChange={setCanonical} title={t.authoring.canonicalTitle}>
                {t.authoring.canonical}
              </Toggle>
            }
          >
            <pre className="code">{canonical ? COMPACT_CANONICAL.canonical : COMPACT_CANONICAL.compact}</pre>
            <p className="note">{t.authoring.expectNote}</p>
          </Panel>
        </div>
      </div>

      <div className="c12">
        <Panel
          title={t.authoring.objectGraph}
          count={fmt(t.authoring.objectGraphCount, { nodes: GRAPH_NODES.length, edges: GRAPH_EDGES.length })}
          actions={<span className="micro">{t.authoring.objectGraphAction}</span>}
        >
          <DiagramSvg
            viewBox="0 0 900 404"
            minWidth={760}
            interactive
            label={t.authoring.graphLabel}
            title={t.authoring.graphTitle}
            desc={t.authoring.graphDesc}
          >
            <text className="t-label t-muted" x={12} y={16}>
              {t.authoring.graphCaption}
            </text>
            {GRAPH_EDGES.map((edge) => {
              const from = GRAPH_NODES.find((n) => n.id === edge.from) ?? GRAPH_NODES[0];
              const to = GRAPH_NODES.find((n) => n.id === edge.to) ?? GRAPH_NODES[1];
              return (
                <Wire
                  key={`${edge.from}-${edge.to}-${edge.label}`}
                  from={from}
                  to={to}
                  fromSide={edge.fromSide}
                  toSide={edge.toSide}
                  label={edge.label}
                />
              );
            })}
            {GRAPH_NODES.map((node) => {
              const hasField = CAMPFIRE_ROWS.some((r) => r.mapsTo === node.id);
              const pulsed = node.id === currentStep.nodeId;
              return (
                <g
                  key={`${node.id}-${pulsed ? activeStep : 'idle'}`}
                  className={`${pulsed ? 'np-pulse' : ''}${pulsed ? ' np-demo-node' : ''}`.trim() || undefined}
                >
                  <NodeBox
                    x={node.x}
                    y={node.y}
                    w={node.w}
                    h={node.h}
                    title={node.name}
                    sub={c(node.sub)}
                    selected={node.id === mappedNodeId}
                    onSelect={hasField ? () => selectNode(node.id) : undefined}
                    ports
                    ariaLabel={`${node.name}, ${c(node.sub)}`}
                  />
                </g>
              );
            })}
            <text className="t-mono t-muted" x={12} y={396} fontSize={9}>
              {t.authoring.graphHint}
            </text>
          </DiagramSvg>
        </Panel>
      </div>

      <div className="c12">
        <Panel title={t.authoring.sixCases} count={t.authoring.sixCasesCount}>
          <div className="g12" style={{ gap: 12, alignItems: 'start' }}>
            {EXPECT_RULES.map((rule) => (
              <div className="c4" key={rule.id}>
                <div className="stack-2" style={{ borderLeft: '3px solid var(--np-rule)', paddingLeft: 10 }}>
                  <div className="row-between">
                    <StatusChip kind={rule.verdict === 'ok' ? 'ok' : 'fail'} size="sm">
                      {rule.verdict === 'ok' ? t.authoring.legal : t.authoring.rejected}
                    </StatusChip>
                    <span className="mono small muted">{rule.code}</span>
                  </div>
                  <div className="strong small">{c(rule.label)}</div>
                  <pre className="code code--wrap" style={{ fontSize: 10, lineHeight: '15px' }}>
                    {rule.snippet}
                  </pre>
                  <p className="note">{c(rule.note)}</p>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </div>
  );
}
