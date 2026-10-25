import type { DemoScenario, Stage } from './types';

export type MissionScenarioId = 'formed' | 'refused' | 'conflict' | 'stale';
export type MissionHighlight = 'all' | 'cavity' | 'mouth' | 'shell' | 'none';
export type MissionResult = 'run' | 'ok' | 'fail' | 'unknown' | 'skip';

export interface MissionStep {
  no: number;
  stage: Stage;
  title: string;
  titleZh: string;
  detail: string;
  detailZh: string;
  state: string;
  stateZh: string;
  result: MissionResult;
  rule: string;
  ruleZh: string;
  explain: string;
  highlight: MissionHighlight;
  spend: [number, number, number];
}

export const MISSION_SCENARIOS: DemoScenario<MissionScenarioId>[] = [
  { id: 'formed', label: 'Standard run', note: 'A clean furnace reaches Formed.', steps: 15 },
  { id: 'refused', label: 'Refused section', note: 'One section is unreadable; the key is Missing.', steps: 15 },
  { id: 'conflict', label: 'Claim conflict', note: 'A second structure already owns the cells.', steps: 15 },
  { id: 'stale', label: 'Stale re-check', note: 'A dirty mark re-emits Formed at generation + 1.', steps: 15 },
];

const base: MissionStep[] = [
  {
    no: 1, stage: 'DESIGN', title: 'Load document', titleZh: '载入文档',
    detail: 'schema 2 → Blueprint: bounds, bays, anchors, context keys.',
    detailZh: 'schema 2 → Blueprint：bounds、bays、anchors、context keys。',
    state: 'Blueprint ready', stateZh: '蓝图就绪', result: 'ok',
    rule: 'A document only builds objects; runtime never sees JSON.', ruleZh: '文档只负责造对象；运行时看不到 JSON。',
    explain: 'blueprint', highlight: 'shell', spend: [0, 0, 0],
  },
  {
    no: 2, stage: 'DESIGN', title: 'Resolve siting', titleZh: '解析安放位',
    detail: 'Read the anchor at the definition origin, map heading → Siting.',
    detailZh: '在定义原点读锚点，把朝向映射成 Siting。',
    state: 'Siting ready', stateZh: '安放位就绪', result: 'ok',
    rule: 'candidate.apply(authoredFront) == anchorHeading', ruleZh: 'candidate.apply(authoredFront) == anchorHeading',
    explain: 'stage', highlight: 'mouth', spend: [0, 1, 1],
  },
  {
    no: 3, stage: 'PROBE', title: 'Capture snapshot', titleZh: '捕获快照',
    detail: 'Copy the read-closure sections, open a lease.',
    detailZh: '复制读闭包内的区段，打开租约。',
    state: '1 section · lease', stateZh: '1 个区段 · 租约', result: 'ok',
    rule: 'Checks run on a worker; the world is never read live.', ruleZh: '检查在 worker 上跑，绝不读活世界。',
    explain: 'snapshotlease', highlight: 'all', spend: [0, 294, 1],
  },
  {
    no: 4, stage: 'PROBE', title: 'Expand plan', titleZh: '展开计划',
    detail: 'Section computations → barrier → aggregates → checks.',
    detailZh: '区段计算 → 屏障 → 聚合 → 检查。',
    state: '2 checks · 2 computations', stateZh: '2 项检查 · 2 项计算', result: 'ok',
    rule: 'Order is fixed so results are reproducible.', ruleZh: '顺序固定，结果才可复现。',
    explain: 'check', highlight: 'shell', spend: [2, 0, 0],
  },
  {
    no: 5, stage: 'PROBE', title: 'PROBE verdict', titleZh: 'PROBE 判定',
    detail: 'Cheap block checks pass.',
    detailZh: '廉价的方块检查通过。',
    state: 'Admitted(PROBE)', stateZh: 'Admitted(PROBE)', result: 'ok',
    rule: 'Admitted is not a structure: no claims, no instance.', ruleZh: 'Admitted 不是结构：没有声明，没有实例。',
    explain: 'admitted', highlight: 'shell', spend: [2, 0, 1],
  },
  {
    no: 6, stage: 'FORM', title: 'Submit FORM', titleZh: '提交 FORM',
    detail: 'The lease moves with the ticket; FORM re-captures only if needed.',
    detailZh: '租约随票据移动；需要时 FORM 重新捕获。',
    state: 'ticket 41 → FORM', stateZh: '票据 41 → FORM', result: 'ok',
    rule: 'A coalesced submit must still release its lease.', ruleZh: '被合并掉的提交也必须释放租约。',
    explain: 'ticket', highlight: 'all', spend: [0, 0, 0],
  },
  {
    no: 7, stage: 'FORM', title: 'Section contributions', titleZh: '区段贡献',
    detail: 'The cavity computation contributes 100 air cells.',
    detailZh: '内腔计算贡献 100 个空气格。',
    state: '100 → VOLUME', stateZh: '100 → VOLUME', result: 'run',
    rule: 'draft.forSection(k).contribute(key, partial)', ruleZh: 'draft.forSection(k).contribute(key, partial)',
    explain: 'contribution', highlight: 'cavity', spend: [1, 100, 1],
  },
  {
    no: 8, stage: 'FORM', title: 'Barrier fold', titleZh: '屏障折叠',
    detail: 'Sort by SectionKey, combine the contributions.',
    detailZh: '按 SectionKey 排序，合并贡献。',
    state: 'VOLUME = 100', stateZh: 'VOLUME = 100', result: 'ok',
    rule: 'No identity: a refused section makes the key Missing.', ruleZh: '没有单位元：区段被拒就整键 Missing。',
    explain: 'fold', highlight: 'cavity', spend: [1, 0, 0],
  },
  {
    no: 9, stage: 'FORM', title: 'Aggregate', titleZh: '聚合',
    detail: 'capacity = volume × 4.',
    detailZh: 'capacity = volume × 4。',
    state: 'CAPACITY = 400', stateZh: 'CAPACITY = 400', result: 'ok',
    rule: 'Aggregates read the folded value, never re-walk the world.', ruleZh: '聚合读折叠值，绝不重扫世界。',
    explain: 'contextkey', highlight: 'cavity', spend: [1, 0, 0],
  },
  {
    no: 10, stage: 'FORM', title: 'Publish FORM frame', titleZh: '发布 FORM 帧',
    detail: 'VOLUME and CAPACITY enter revision 1.',
    detailZh: 'VOLUME 与 CAPACITY 进入修订 1。',
    state: 'contextRevision 1', stateZh: 'contextRevision 1', result: 'ok',
    rule: 'A stale base is refused with SC-CTX-0108.', ruleZh: '基修订过期会被 SC-CTX-0108 拒绝。',
    explain: 'sc-ctx-0108', highlight: 'cavity', spend: [0, 0, 0],
  },
  {
    no: 11, stage: 'SETTLE', title: 'SETTLE judge', titleZh: 'SETTLE 判定',
    detail: 'Door, interior and state checks all match.',
    detailZh: '门、内腔与状态检查全部通过。',
    state: 'MATCH', stateZh: 'MATCH', result: 'ok',
    rule: 'Unreadable outranks mismatch; mismatch outranks abstain.', ruleZh: '读不到压过不匹配；不匹配压过弃权。',
    explain: 'reply', highlight: 'mouth', spend: [3, 0, 1],
  },
  {
    no: 12, stage: 'SETTLE', title: 'Claim planning', titleZh: '声明规划',
    detail: 'wall 192 + mouth 2 + interior 100 rows.',
    detailZh: '外壳 192 + 门洞 2 + 内腔 100 行。',
    state: '294 rows', stateZh: '294 行', result: 'run',
    rule: 'Occupied owns non-air bound footprints; Owned owns bay cells.', ruleZh: 'Occupied 拥有非空气绑定足迹；Owned 拥有舱位格子。',
    explain: 'claimscope', highlight: 'all', spend: [0, 0, 0],
  },
  {
    no: 13, stage: 'SETTLE', title: 'Settle CAS', titleZh: '结算 CAS',
    detail: 'One compare-and-set replaces (owner, siting).',
    detailZh: '一次 CAS 替换 (owner, siting)。',
    state: 'Settled @ revision 1', stateZh: 'Settled @ revision 1', result: 'ok',
    rule: 'RevisionMoved is retried once, then Undecided.', ruleZh: 'RevisionMoved 重试一次，之后 Undecided。',
    explain: 'claimverdict', highlight: 'all', spend: [0, 0, 0],
  },
  {
    no: 14, stage: 'SETTLE', title: 'Formed + runtime', titleZh: 'Formed + 运行时',
    detail: 'Formed carries claims, generation, revisions; RuntimeState persists the SETTLE frame.',
    detailZh: 'Formed 携带声明、代次与修订号；RuntimeState 持久化 SETTLE 帧。',
    state: 'Formed · generation 1', stateZh: 'Formed · generation 1', result: 'ok',
    rule: 'Formed exists only after Settled.', ruleZh: '只有 Settled 之后才存在 Formed。',
    explain: 'formed', highlight: 'shell', spend: [0, 0, 0],
  },
  {
    no: 15, stage: 'STALE', title: 'Watch and re-check', titleZh: '监视与重检',
    detail: 'A dirty mark inside the watch window starts a new attempt.',
    detailZh: '监视窗口内出现脏标记，开始新一次尝试。',
    state: 'idle', stateZh: '空闲', result: 'ok',
    rule: 'A re-run is a new attempt over a fresh capture.', ruleZh: '重跑必须是新一次捕获，不是同一快照内递归。',
    explain: 'dirtyindex', highlight: 'shell', spend: [0, 0, 0],
  },
];

function withScenario(scenario: MissionScenarioId): MissionStep[] {
  const steps = base.map((step) => ({ ...step }));
  if (scenario === 'refused') {
    steps[6] = { ...steps[6], state: 'REFUSED · NOT_CAPTURED', stateZh: 'REFUSED · NOT_CAPTURED', result: 'fail' };
    steps[7] = { ...steps[7], state: 'VOLUME Missing', stateZh: 'VOLUME Missing', result: 'unknown' };
    steps[8] = { ...steps[8], state: 'aborted', stateZh: '已中止', result: 'unknown' };
    steps[9] = { ...steps[9], state: 'no publish', stateZh: '未发布', result: 'unknown' };
    steps[10] = { ...steps[10], state: 'Undecided · SC-FOC-0301', stateZh: 'Undecided · SC-FOC-0301', result: 'unknown' };
    for (let i = 11; i < steps.length; i += 1) steps[i] = { ...steps[i], state: 'skipped', stateZh: '已跳过', result: 'skip' };
  }
  if (scenario === 'conflict') {
    steps[12] = { ...steps[12], state: 'ClaimConflict · SC-CLM-0002', stateZh: 'ClaimConflict · SC-CLM-0002', result: 'fail' };
    steps[13] = { ...steps[13], state: 'no Formed', stateZh: '没有 Formed', result: 'fail' };
    steps[14] = { ...steps[14], state: 'skipped', stateZh: '已跳过', result: 'skip' };
  }
  if (scenario === 'stale') {
    steps[14] = {
      ...steps[14],
      title: 'Dirty → DIRTY_UNSTABLE',
      titleZh: '脏标记 → DIRTY_UNSTABLE',
      detail: 'The fourth consecutive drop keeps the prior Formed and bumps its generation.',
      detailZh: '连续第四次丢弃保留上一个 Formed，并把代次加一。',
      state: 'Formed · generation 2', stateZh: 'Formed · generation 2', result: 'unknown',
      explain: 'generation',
    };
  }
  return steps;
}

export const MISSION_STEPS: Record<MissionScenarioId, MissionStep[]> = {
  formed: withScenario('formed'),
  refused: withScenario('refused'),
  conflict: withScenario('conflict'),
  stale: withScenario('stale'),
};
