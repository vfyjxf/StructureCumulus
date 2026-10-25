/**
 * Bilingual dictionary. UI chrome lives here, grouped by page id; long prose owned by
 * `src/data/*.ts` is translated in COPY, keyed by its English source string.
 */
export type Lang = 'zh' | 'en';

const enChrome = {
  brandSub: 'VISUAL SPECIFICATION SUITE · SC-SERIES · 2026-10-21',
  polarity: 'Polarity',
  language: 'Language',
  palette: 'Palette',
  paletteAria: 'Palette polarity: print or CRT',
  print: 'Print',
  printTitle: 'Paper palette',
  crt: 'CRT',
  crtTitle: 'Phosphor palette',
  langAria: 'Interface language: Chinese or English',
  zhOption: '中文',
  zhTitle: 'Chinese interface',
  enOption: 'EN',
  enTitle: 'English interface',
  sheetHeaderAria: 'Sheet header',
  sheetNo: 'Sheet no.',
  rev: 'Rev',
  scale: 'Scale',
  drawn: 'Drawn',
  checked: 'Checked',
  date: 'Date',
  zone: 'Zone',
  stageTitle: 'Stage of the units this sheet documents',
  sourcesAria: 'Source references',
  sources: 'SOURCES',
  designContract: 'Design contract: visual/design/nasa-punk-spec.md',
  build: 'STRUCTURECUMULUS · build 2026-10-21',
  doNotScale: 'DO NOT SCALE · VERIFY AGAINST SNAPSHOT 7f3a91c',
  sheet: 'SHEET',
  tabAria: 'Sheet index',
  legend: 'Legend',
  why: 'Why this exists',
  skipped: '(skipped)',
  stepAria: 'Step',
  increase: 'increase',
  decrease: 'decrease',
};

const zhChrome: typeof enChrome = {
  brandSub: '视觉规范套件 · SC 系列 · 2026-10-21',
  polarity: '极性',
  language: '语言',
  palette: '配色',
  paletteAria: '配色极性：纸面或 CRT',
  print: '纸面',
  printTitle: '纸面配色',
  crt: 'CRT',
  crtTitle: '荧光配色',
  langAria: '界面语言：中文或英文',
  zhOption: '中文',
  zhTitle: '中文界面',
  enOption: 'EN',
  enTitle: '英文界面',
  sheetHeaderAria: '图纸页眉',
  sheetNo: '图号',
  rev: '版本',
  scale: '比例',
  drawn: '绘制',
  checked: '审核',
  date: '日期',
  zone: '分区',
  stageTitle: '本图纸所记录单元所处的阶段',
  sourcesAria: '来源引用',
  sources: '来源',
  designContract: '设计契约：visual/design/nasa-punk-spec.md',
  build: 'STRUCTURECUMULUS · 构建 2026-10-21',
  doNotScale: '请勿缩放 · 以快照 7f3a91c 为准',
  sheet: '图纸',
  tabAria: '图纸索引',
  legend: '图例',
  why: '为什么存在',
  skipped: '（跳过）',
  stepAria: '步骤',
  increase: '增加',
  decrease: '减少',
};

const enDemo = {
  transport: 'Playback controls',
  play: 'Play',
  pause: 'Pause',
  prev: 'Previous step',
  next: 'Next step',
  stepOf: 'Step {n} of {total}',
  speed: 'Playback speed',
  speedOption: '{n}× speed',
  steps: '{n} steps',
  scenarios: 'Scenarios',
  currentStop: 'Current stop',
  stopOf: 'stop {n} / {total}',
  object: 'Object',
  carries: 'Carries',
  code: 'Code',
  holdHint: 'Auto-cycle holds while the pointer or keyboard focus is inside this panel.',
  frame: 'Frame',
  foldOf: 'fold {n}/{total} · running {value}',
  partialOf: '{n} / {total} contributions',
  settlePhases: ['Placements', 'Rows written', 'Matrix compared'],
  dragHint: 'Drag structure B on the plane; it snaps to whole cells. Keyboard: X / Z steppers.',
  predicateFocus: 'Focus',
  predicateFound: 'Subject found',
  predicateMissing: 'Subject missing · Refused',
  predicateChild: 'Combinator child',
  predicateReply: 'Reply',
  revealed: 'Reply {reply}',
};

const zhDemo: typeof enDemo = {
  transport: '播放控制',
  play: '播放',
  pause: '暂停',
  prev: '上一格',
  next: '下一格',
  stepOf: '第 {n} 步，共 {total} 步',
  speed: '播放速度',
  speedOption: '{n}× 速度',
  steps: '{n} 步',
  scenarios: '示例场景',
  currentStop: '当前落点',
  stopOf: '第 {n} / {total} 步',
  object: '对象',
  carries: '携带',
  code: '代码',
  holdHint: '指针或键盘焦点留在本面板内时，自动循环会暂停。',
  frame: '帧',
  foldOf: '折叠 {n}/{total} · 累计 {value}',
  partialOf: '{n} / {total} 个贡献',
  settlePhases: ['摆放', '写入行', '比对矩阵'],
  dragHint: '在平面上拖动结构 B，它会对齐到整格。键盘可用 X / Z 步进器。',
  predicateFocus: '焦点',
  predicateFound: '主体已找到',
  predicateMissing: '主体缺失 · 已拒读',
  predicateChild: '组合子子节点',
  predicateReply: '回复',
  revealed: '回复 {reply}',
};

const enNav = {
  overview: 'System overview',
  mission: 'End-to-end mission',
  space: 'Space & orientation',
  authoring: 'Structure authoring',
  anatomy: 'Bay anatomy',
  voxel: '3D structure bay',
  slice: '2D slice inspector',
  predicates: 'Predicate sandbox',
  context: 'Context dataflow',
  pipeline: 'Pipeline',
  claims: 'Claims',
  results: 'Results & diagnostics',
  glossary: 'Glossary',
};

const zhNav: typeof enNav = {
  overview: '系统总览',
  mission: '全流程',
  space: '空间与朝向',
  authoring: '结构编写',
  anatomy: '舱位解剖',
  voxel: '3D 结构台',
  slice: '2D 切片',
  predicates: '谓词沙盒',
  context: '上下文数据流',
  pipeline: '流水线',
  claims: '声明',
  results: '结果与诊断',
  glossary: '术语表',
};

const enPages = {
  overview: { subtitle: 'Four layers · document → object graph → execution → result' },
  mission: { subtitle: 'Document → capture → match → context → claims → runtime, in fifteen steps' },
  space: { subtitle: 'Lattice, half-open cuboids, the 48-element group, the siting function' },
  authoring: { subtitle: 'One document, four concerns, one object graph' },
  anatomy: { subtitle: 'The 7×6×7 furnace split by subject, stage and ownership' },
  voxel: { subtitle: '294 voxels · orbit, zoom, clip, overlay kind / bay / claim' },
  slice: { subtitle: 'X / Y / Z planes, cell readout, second placing comparison' },
  predicates: { subtitle: 'Focus + expectation, pinned to one subject type T' },
  context: { subtitle: 'Contribution → canonical fold (x, z, y) → tier frame' },
  pipeline: { subtitle: 'Eight steps, three stages, one sealed verdict' },
  claims: { subtitle: 'One grid, two owners, one conflict predicate' },
  results: { subtitle: 'Five result arms, nine finding components, five sort keys' },
  glossary: { subtitle: 'Terms with a shape and a reason to exist' },
};

const zhPages: typeof enPages = {
  overview: { subtitle: '四个层级 · 文档 → 对象图 → 执行 → 结果' },
  mission: { subtitle: '文档 → 捕获 → 匹配 → 上下文 → 声明 → 运行时，十五步' },
  space: { subtitle: '格点、半开长方体、48 元朝向群、摆放函数' },
  authoring: { subtitle: '一份文档，四类关注点，一张对象图' },
  anatomy: { subtitle: '7×6×7 高炉按主体、阶段与归属拆分' },
  voxel: { subtitle: '294 个体素 · 旋转、缩放、切片、按类型/舱位/声明叠加' },
  slice: { subtitle: 'X / Y / Z 切面、格读数、第二座摆放对比' },
  predicates: { subtitle: '焦点 + 期望，钉在同一个主体类型 T 上' },
  context: { subtitle: '贡献 → 规范折叠 (x, z, y) → 层级帧' },
  pipeline: { subtitle: '八个步骤、三个阶段、一次封存裁决' },
  claims: { subtitle: '一张网格，两个所有者，一个冲突谓词' },
  results: { subtitle: '五种结果分支、九个诊断组件、五个排序键' },
  glossary: { subtitle: '每个术语都有形状，也有存在的理由' },
};

const enOverview = {
  why:
    'The suite shows one structure from four altitudes: the file you write, the graph the kernel builds, the plan it runs, and the verdict it seals. Most defects live in a hand-off between two altitudes — a field lowered into the wrong object — not inside a single box.',
  layerStack: 'Layer stack',
  layerStackCount: '4 layers · select one',
  layerOrder: 'top → bottom',
  diagramLabel: 'Four-layer stack: document, object graph, execution, result',
  diagramTitle: 'Four layers of one check',
  diagramDesc:
    'Each layer is selectable. Vertical orthogonal arrows carry the hand-off labels compile/build, expand(stage) and seal/settle.',
  layerAria: 'Layer {no} {name}: {question}',
  layerObjects: 'OBJECTS',
  readouts: 'Readouts',
  readoutsCount: 'one primary',
  selected: 'Selected · {name}',
  owns: 'Owns',
  question: 'Question',
  layerTag: 'Layer tag',
  objects: 'Objects',
  layerNote:
    'Every layer is a projection of the same facts. No layer owns a second semantics; the JSON is not kept alive at runtime, and a result is not a document.',
  conceptsOwned: 'Concepts owned by {name}',
  conceptsCount: '{n} definitions',
  conceptsAction: 'click a layer above',
  handoffs: 'Hand-offs',
  handoffsCount: '{n} boundaries',
  thHandoff: 'Hand-off',
  thOperation: 'Operation',
  thCrosses: 'What crosses',
};

const zhOverview: typeof enOverview = {
  why:
    '本套图纸从四个高度描述同一个结构：你写下的文件、内核构建的对象图、它执行的计划、以及它封存的裁决。大多数缺陷发生在两层之间的交接处——某个字段被降进了错误的对象——而不是某个方框内部。',
  layerStack: '层级栈',
  layerStackCount: '4 层 · 选择一层',
  layerOrder: '自上而下',
  diagramLabel: '四层栈：文档、对象图、执行、结果',
  diagramTitle: '一次检查的四层',
  diagramDesc: '每一层都可选择。垂直正交箭头标注交接操作：compile/build、expand(stage) 与 seal/settle。',
  layerAria: '层级 {no} {name}：{question}',
  layerObjects: '个对象',
  readouts: '读数',
  readoutsCount: '一个主读数',
  selected: '已选 · {name}',
  owns: '拥有',
  question: '问题',
  layerTag: '层级标签',
  objects: '对象',
  layerNote:
    '每一层都是同一批事实的投影。没有任何一层拥有第二套语义；JSON 不在运行时存活，结果也不是文档。',
  conceptsOwned: '{name} 拥有的概念',
  conceptsCount: '{n} 条定义',
  conceptsAction: '点击上方某一层',
  handoffs: '交接',
  handoffsCount: '{n} 个边界',
  thHandoff: '交接',
  thOperation: '操作',
  thCrosses: '跨越的内容',
};

const enAnatomy = {
  why:
    'A bay is the smallest unit that can be judged, owned and staged independently. The furnace is the proof: three parts with three subject types, three ownership sets and two stages — one structure, and no single check can be all of them.',
  study: 'Exploded bay study',
  studyCount: '{bounds} · 294 cells',
  studyAction: 'front elevation · 7 × 6',
  diagramLabel: 'Exploded front elevation of the furnace: wall, mouth, interior',
  diagramTitle: 'The same 7 × 6 × 7 furnace, split into three bays',
  diagramDesc:
    'Each panel is a front elevation. Wall fills the shell face, mouth fills the two-cell opening, interior fills the 5 × 4 cavity cross-section. Clicking a panel selects that bay.',
  sectionLabel: 'MYMOD:HUGE_FURNACE · Z = 0 SECTION',
  patternKey: 'CELL PATTERN KEY: SHELL / OPENING / CAVITY',
  bayAria: 'Bay {name}, {cells} cells, {coverage}, {scope}',
  cellsUnit: 'CELLS',
  mouthCells: '1 × 2 = 2 CELLS',
  impossible: 'ONE Check<?> · IMPOSSIBLE',
  arithmetic: 'WALL = BOUNDS − CAVITY = 194 · CAVITY = 5 × 4 × 5 = 100 · MOUTH = 1 × 2 = 2',
  legendTitle: 'Cell pattern · three bays',
  legendWall: 'wall · shell face',
  legendMouth: 'mouth · opening',
  legendInterior: 'interior · cavity section',
  legendSelected: 'selected bay',
  legendSelectedCount: '2 px + ticks',
  oneCheck: 'One-check control',
  overlayOn: 'overlay on',
  overlayOff: 'overlay off',
  giantCheck: 'One giant Check',
  boundsVolume: 'Bounds volume',
  wall: 'Wall',
  cavity: 'Cavity',
  mouth: 'Mouth',
  selected: 'Selected · {name}',
  selectedCount: '{n} cells',
  coverage: 'Coverage',
  mode: 'Mode',
  scope: 'Scope',
  subject: 'Subject',
  stage: 'Stage',
  focus: 'Focus',
  owns: 'Owns',
  hazard:
    'One Check<T> cannot span this furnace — four independent constraints, four errors if forced.',
  whyGiant: 'Why one giant check cannot work',
  whyGiantCount: '4 constraints',
  whyGiantAction: 'toggle the overlay',
  register: 'Bay register',
  registerCount: '{n} bays · 296 asserted cells',
  thBay: 'Bay',
  thCells: 'Cells',
  thCoverage: 'Coverage',
  thMode: 'Mode',
  thScope: 'Scope',
  thSubject: 'Subject T',
  thStage: 'Stage',
  thOwned: 'Owned',
  registerNote:
    "194 + 100 = 294 covers the bounds exactly. The mouth's two cells lie in the shell face and are named by the mouth bay; the 194 count is the arithmetic of the shell, not a fourth disjoint set. The register's 296 is asserted cells, not owned cells: a door bay can assert and own the same two cells under a different subject.",
  legality: 'Coverage × scope legality',
  thLegal: 'Legal scopes',
  thAssertion: 'Assertion',
  legalityNote: 'SUBJECT + Owned is the other illegal pair: a one-cell check cannot claim a 100-cell bay.',
  whyNotPresence: 'Why not PRESENCE',
  presenceFormula: 'interior: coverage = {coverage} · scope = {scope} → {code}',
  presenceNote:
    "The cavity's air checks are per-cell OccupantSnapshot checks, so CUBIC has decision cells to cover; the capacity value is a separate Long check at SETTLE. That is the split the single check cannot make.",
};

const zhAnatomy: typeof enAnatomy = {
  why:
    '舱位是能够被独立判定、拥有和分阶段的最小单元。高炉就是证明：三个部分、三种主体类型、三套归属、两个阶段——同一个结构，而没有任何单个检查能同时是这三者。',
  study: '舱位拆解研究',
  studyCount: '{bounds} · 294 格',
  studyAction: '正立面 · 7 × 6',
  diagramLabel: '高炉的拆解正立面：墙体、开口、腔体',
  diagramTitle: '同一个 7 × 6 × 7 高炉，拆成三个舱位',
  diagramDesc:
    '每个面板都是一张正立面。墙体填满壳体面，开口填满两格洞口，腔体填满 5 × 4 的腔截面。点击面板即选中该舱位。',
  sectionLabel: 'MYMOD:HUGE_FURNACE · Z = 0 剖面',
  patternKey: '格型图例：壳体 / 开口 / 腔体',
  bayAria: '舱位 {name}，{cells} 格，{coverage}，{scope}',
  cellsUnit: '格',
  mouthCells: '1 × 2 = 2 格',
  impossible: '一个 Check<?> · 不可能',
  arithmetic: 'WALL = BOUNDS − CAVITY = 194 · CAVITY = 5 × 4 × 5 = 100 · MOUTH = 1 × 2 = 2',
  legendTitle: '格型 · 三个舱位',
  legendWall: '墙体 · 壳体面',
  legendMouth: '开口 · 洞口',
  legendInterior: '腔体 · 腔截面',
  legendSelected: '选中的舱位',
  legendSelectedCount: '2 px + 角标',
  oneCheck: '单检查控制',
  overlayOn: '叠加层开启',
  overlayOff: '叠加层关闭',
  giantCheck: '一个巨型 Check',
  boundsVolume: '包围盒体积',
  wall: '墙体',
  cavity: '腔体',
  mouth: '开口',
  selected: '已选 · {name}',
  selectedCount: '{n} 格',
  coverage: '覆盖方式',
  mode: '声明方式',
  scope: '声明范围',
  subject: '主体',
  stage: '阶段',
  focus: '焦点',
  owns: '拥有',
  hazard: '一个 Check<T> 无法覆盖这座高炉——四条各自独立的约束，强行合并就是四个错误。',
  whyGiant: '为什么一个巨型检查行不通',
  whyGiantCount: '4 条约束',
  whyGiantAction: '切换叠加层',
  register: '舱位登记表',
  registerCount: '{n} 个舱位 · 296 个断言格',
  thBay: '舱位',
  thCells: '格数',
  thCoverage: '覆盖方式',
  thMode: '声明方式',
  thScope: '声明范围',
  thSubject: '主体 T',
  thStage: '阶段',
  thOwned: '拥有',
  registerNote:
    '194 + 100 = 294，恰好铺满包围盒。开口的两格位于壳体面内，由开口舱位命名；194 是壳体的算术结果，不是第四组不相交的集合。登记表的 296 是断言格而非拥有格：门舱位可以在不同主体下断言并拥有同样的两格。',
  legality: '覆盖方式 × 声明范围合法性',
  thLegal: '合法范围',
  thAssertion: '断言内容',
  legalityNote: 'SUBJECT + Owned 是另一对非法组合：单格检查无法声明一个 100 格的舱位。',
  whyNotPresence: '为什么不是 PRESENCE',
  presenceFormula: 'interior: coverage = {coverage} · scope = {scope} → {code}',
  presenceNote:
    '腔体的空气检查是逐格的 OccupantSnapshot 检查，所以 CUBIC 有可供覆盖的判定格；容量值则是 SETTLE 阶段独立的 Long 检查。这正是单个检查无法完成的拆分。',
};

const enPipeline = {
  why:
    'Eight steps exist because each one removes a class of non-determinism: a live world, an unordered plan, a monolithic walk, a partial sum, a recomputed value, a scanning predicate, an unversioned frame, an unsettled claim. Remove one and the verdict stops being a function of the request.',
  eightStep: 'Eight-step pipeline',
  stagePlan: 'Stage plan',
  stepperAria: 'Pipeline steps',
  atStep: 'at {step}',
  verdict: 'Verdict',
  verdictCount: 'stage {stage}',
  claimsField: 'claims',
  claimsValue: '294 rows · rev 4',
  generationField: 'generation',
  contextRevisionField: 'contextRevision',
  leaseField: 'lease',
  leaseValue: 'child handle · close()',
  admittedNote: 'Admitted is not a structure. It carries no instance id, no claims, and ',
  admittedNoteTail: ' refuses it.',
  failedNote:
    'Failed licenses a repair that changes the world. It is never merged with Undecided, which licenses a re-read instead.',
  stage: 'Stage',
  conditions: 'Conditions',
  conditionsCount: 'inject one at a time',
  clear: 'Clear',
  thCondition: 'Condition',
  thInjectedAt: 'Injected at',
  thEffect: 'Effect',
  conflictOnly:
    'Claim conflict is a SETTLE-only condition. At {stage} the settle step is skipped, so the toggle has no effect on this plan.',
  stopOrder:
    'A MISMATCH outranks every stop reason: one decisive violation gives Failed even when another unit was unreadable. The stop reasons are ordered CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS.',
  stepDetail: 'Step detail · {name}',
  stepDetailCount: '{treatment} at {stage}',
  does: 'Does',
  whyIt: 'Why it exists',
  removeIt: 'Remove it',
  codeContract: 'Code / contract',
  matrix: 'Stage × step matrix',
  matrixCount: 'run · partial · skip',
  thStep: 'Step',
  thWhy: 'Why',
  verdictFold: 'Verdict fold',
  verdictFoldCount: 'three rules',
  running: 'RUNNING',
  stoppedHere: 'run stopped here',
};

const zhPipeline: typeof enPipeline = {
  why:
    '之所以有八个步骤，是因为每一步都消除一类不确定性：活的世界、无序的计划、单体遍历、部分和、被重算的值、扫描式谓词、没有版本的帧、未结算的声明。去掉任何一步，裁决就不再是请求的函数。',
  eightStep: '八步流水线',
  stagePlan: '阶段计划',
  stepperAria: '流水线步骤',
  atStep: '发生于 {step}',
  verdict: '裁决',
  verdictCount: '阶段 {stage}',
  claimsField: 'claims',
  claimsValue: '294 行 · rev 4',
  generationField: 'generation',
  contextRevisionField: 'contextRevision',
  leaseField: 'lease',
  leaseValue: '子句柄 · close()',
  admittedNote: 'Admitted 不是结构。它没有实例 id、没有声明，',
  admittedNoteTail: '会拒绝它。',
  failedNote: 'Failed 授权一次会改动世界的修复。它从不与 Undecided 合并，后者授权的是重新读取。',
  stage: '阶段',
  conditions: '条件',
  conditionsCount: '一次注入一个',
  clear: '清除',
  thCondition: '条件',
  thInjectedAt: '注入点',
  thEffect: '影响',
  conflictOnly: '声明冲突只属于 SETTLE。在 {stage} 阶段 settle 步骤被跳过，因此该开关对本计划无效。',
  stopOrder:
    'MISMATCH 压过所有停止原因：只要有一处决定性违规，即使另一个单元不可读也判 Failed。停止原因的优先级为 CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS。',
  stepDetail: '步骤详情 · {name}',
  stepDetailCount: '{treatment} 于 {stage}',
  does: '做什么',
  whyIt: '为何存在',
  removeIt: '去掉它',
  codeContract: '代码 / 契约',
  matrix: '阶段 × 步骤矩阵',
  matrixCount: 'run · partial · skip',
  thStep: '步骤',
  thWhy: '为什么',
  verdictFold: '裁决折叠',
  verdictFoldCount: '三条规则',
  running: '运行中',
  stoppedHere: '运行停在此步',
};

const enContext = {
  why:
    'A section pass must be order-independent, but a fold must not be. Sorting contributions by SectionKey (x, z, y) makes the value a function of the contribution set; refusing one section makes the key absent, because a short sum is worse than no sum.',
  contributions: 'Section contributions',
  contributionsCount: '{n} sections · authored order differs',
  stepFold: 'Step fold',
  canonicalOrder: 'Canonical order · ascending',
  valuesRefused: 'values · refused',
  partial: 'Partial',
  contributionAria: 'Contribution from section {x}, {z}, {y}',
  refused: 'Refused',
  declined: 'Declined',
  readable: 'Readable',
  refuseTitle: 'Refuse the section slot',
  emptyRegion: 'Empty region · no contribution',
  authoredOrder: 'authored: (1,0,0) · (0,1,0) · (0,0,0)',
  fold: 'Fold',
  noContribution: 'no present contribution — there is no identity element',
  missing: 'Missing',
  consumed: '{step}/{total} consumed · SectionKey {order}',
  refusedLine: 'One section is Refused. The barrier makes the key absent;',
  refusedLineTail: 'returns Missing and the check is UNREADABLE — never 0.',
  foldNote: 'The fold begins at the first present contribution. A refusal aborts the key rather than producing a smaller number.',
  foldedResult: 'Folded result',
  foldedResultCount: 'one per key',
  cellsUnit: 'cells',
  missingNotZero: 'Missing · not 0',
  contributionsUnit: '{n} contributions',
  singleWriter: 'single writer',
  whenAbsentNote:
    ' is a declared field, not a default. mymod:heat declares 0 and uses it only when every section is readable and nothing contributed. Refusing a section never reaches it.',
  reader: 'Reader',
  readAtTier: 'Read at tier',
  volumeAt: 'volume at {tier} = {value}',
  probeNote: 'A PROBE reader cannot see a FORM value; the read returns null, not a stale number.',
  tierFrames: 'Tier frames',
  tierFramesCount: '{n} frames',
  readerAt: 'reader = {tier}',
  thKey: 'Key',
  tierAria: 'Context values per tier frame',
  published: 'published → BLUEPRINT · PROBE · FORM · SETTLE',
  readerSees: 'reader at {tier} sees frames at or below it',
  absencePaths: 'Absence paths',
  absenceCount: 'four cases',
  thCase: 'Case',
  thResult: 'Result',
  thCode: 'Code',
  thWhy: 'Why',
  foldRules: 'Fold rules',
  foldRulesCount: 'deterministic',
  foldFormula: 'MERGE {combine(left, right)} · no identity · key order (id, type name)',
};

const zhContext: typeof enContext = {
  why:
    '区段遍历必须与顺序无关，而折叠不能。按 SectionKey (x, z, y) 排序贡献，值就只取决于贡献集合；拒读一个区段会让整个键缺席，因为短的和比没有和更糟。',
  contributions: '区段贡献',
  contributionsCount: '{n} 个区段 · 编写顺序与之不同',
  stepFold: '单步折叠',
  canonicalOrder: '规范顺序 · 升序',
  valuesRefused: '取值 · 拒读',
  partial: '部分值',
  contributionAria: '来自区段 {x}, {z}, {y} 的贡献',
  refused: '拒读',
  declined: '已拒绝',
  readable: '可读',
  refuseTitle: '拒读该区段槽',
  emptyRegion: '空区域 · 无贡献',
  authoredOrder: '编写顺序: (1,0,0) · (0,1,0) · (0,0,0)',
  fold: '折叠',
  noContribution: '没有在场的贡献——不存在单位元',
  missing: '缺失',
  consumed: '已计入 {step}/{total} · SectionKey {order}',
  refusedLine: '有一个区段被拒读。屏障让键缺席；',
  refusedLineTail: '返回 Missing，检查为 UNREADABLE——绝不是 0。',
  foldNote: '折叠从第一个在场的贡献开始。拒读会让整个键中止，而不是给出一个更小的数。',
  foldedResult: '折叠结果',
  foldedResultCount: '每个键一份',
  cellsUnit: '格',
  missingNotZero: '缺失 · 不是 0',
  contributionsUnit: '{n} 个贡献',
  singleWriter: '单写者',
  whenAbsentNote:
    ' 是声明字段，不是默认值。mymod:heat 声明 0，且只在所有区段都可读、无任何贡献时才使用它。拒读区段永远走不到它。',
  reader: '读者',
  readAtTier: '读取层级',
  volumeAt: '在 {tier} 读取 volume = {value}',
  probeNote: 'PROBE 读者看不到 FORM 值；读取返回 null，而不是过期数字。',
  tierFrames: '层级帧',
  tierFramesCount: '{n} 个帧',
  readerAt: '读者 = {tier}',
  thKey: '键',
  tierAria: '各层级帧的上下文取值',
  published: '已发布 → BLUEPRINT · PROBE · FORM · SETTLE',
  readerSees: '{tier} 的读者只能看到其自身及以下的帧',
  absencePaths: '缺席路径',
  absenceCount: '四种情形',
  thCase: '情形',
  thResult: '结果',
  thCode: '代码',
  thWhy: '为什么',
  foldRules: '折叠规则',
  foldRulesCount: '确定性',
  foldFormula: 'MERGE {combine(left, right)} · 无单位元 · 键序 (id, type name)',
};

const enClaims = {
  why:
    'A claim is per cell, not per structure. Two 3 × 3 footprints can overlap by one cell and the ledger must be able to say exactly which cell, which owner and which mode — otherwise two furnaces both report Formed on land only one of them occupies.',
  plane: 'Claim plane',
  planeCount: '{grid} × {grid} cells · x → · z ↓',
  conflict: 'Conflict',
  clear: 'Clear',
  diagramLabel: 'Six by six claim grid with two three by three structures',
  diagramTitle: 'Two structures on one six by six claim plane',
  diagramDesc:
    'Structure A is solid-outlined, structure B is dashed. Orange and blue tints are owned cells; the hatched cells are overlaps, and alert hatching marks a conflict.',
  matrixRev: 'CLAIM MATRIX · REV {rev}',
  matrixKey: 'SOLID = A · DASHED = B · HATCH = CONFLICT',
  axisX: 'X →',
  axisZ: 'Z ↓',
  gridSummary: '{rows} own rows · {overlap} shared cells · {conflicts} conflicts',
  ownership: 'Ownership',
  ownedA: 'A owned',
  ownedB: 'B owned',
  sharedCell: 'shared cell',
  conflictCell: 'conflict',
  placeDeclare: 'Place & declare',
  placeDeclareCount: 'per structure',
  mode: 'Mode',
  scope: 'Scope',
  ledger: 'Ledger',
  ledgerCount: 'captured {rev}',
  conflicts: 'Conflicts',
  cellsUnit: 'cells',
  rows: 'Rows',
  rowsUnit: 'rows',
  ledgerRev: 'Ledger',
  revUnit: 'rev',
  settle: 'Settle',
  bumpLedger: 'Bump ledger',
  retry: 'Retry at current',
  secondMove: 'Second move during retry',
  emptyState: 'NO SETTLE ATTEMPTED',
  verdictSettled: '{revision} · {rows} rows · Formed is now possible',
  verdictConflict: '{n} conflicting cell(s): {cells}',
  verdictMoved: 'expected {expected}, actual {actual} · no rows written by this attempt',
  verdictMovedCode: 'retried once inside the call',
  verdictLedger: 'second movement during the retry · token {token}',
  declarationRules: 'Declaration rules',
  declarationRulesCount: 'one predicate',
  modeScope: 'Mode × scope',
  modeScopeCount: 'what a row means',
  thMode: 'Mode',
  thRule: 'Rule',
  thScope: 'Scope',
  thOwnedCells: 'Owned cells',
  exclusivePresent: 'exclusive present',
  sharedOnly: 'shared only',
  conflictSummary: '{conflicts} conflict · {shared} shared without conflict',
  modeScopeNote:
    'Same (owner, siting) rows are excluded inside the predicate; A and B differ, so every overlap is compared. RESERVED reads like SHARED and blocks EXCLUSIVE until the host reservation expires.',
};

const zhClaims: typeof enClaims = {
  why:
    '声明是按格的，不是按结构的。两个 3 × 3 足迹可以只重叠一格，账本必须能准确说出是哪一格、哪个所有者、哪种方式——否则两座高炉都会报告 Formed，而地块只属于其中一座。',
  plane: '声明平面',
  planeCount: '{grid} × {grid} 格 · x → · z ↓',
  conflict: '冲突',
  clear: '无冲突',
  diagramLabel: '6 × 6 声明网格，上面有两个 3 × 3 结构',
  diagramTitle: '同一张 6 × 6 声明平面上的两个结构',
  diagramDesc:
    '结构 A 用实线框，结构 B 用虚线框。橙色和蓝色底纹是拥有的格；斜纹格是重叠格，警示斜纹表示冲突。',
  matrixRev: '声明矩阵 · REV {rev}',
  matrixKey: '实线 = A · 虚线 = B · 斜纹 = 冲突',
  axisX: 'X →',
  axisZ: 'Z ↓',
  gridSummary: '{rows} 个拥有行 · {overlap} 个共享格 · {conflicts} 处冲突',
  ownership: '归属',
  ownedA: 'A 拥有',
  ownedB: 'B 拥有',
  sharedCell: '共享格',
  conflictCell: '冲突',
  placeDeclare: '摆放与声明',
  placeDeclareCount: '每个结构',
  mode: '声明方式',
  scope: '声明范围',
  ledger: '账本',
  ledgerCount: '已捕获 {rev}',
  conflicts: '冲突',
  cellsUnit: '格',
  rows: '行数',
  rowsUnit: '行',
  ledgerRev: '账本',
  revUnit: '版本',
  settle: '结算',
  bumpLedger: '推进账本',
  retry: '在当前版本重试',
  secondMove: '重试期间再次移动',
  emptyState: '尚未尝试结算',
  verdictSettled: '版本 {revision} · {rows} 行 · 现在可以 Formed',
  verdictConflict: '{n} 个冲突格：{cells}',
  verdictMoved: '期望 {expected}，实际 {actual} · 本次尝试未写入任何行',
  verdictMovedCode: '在调用内重试一次',
  verdictLedger: '重试期间发生了第二次移动 · token {token}',
  declarationRules: '声明规则',
  declarationRulesCount: '一个谓词',
  modeScope: '方式 × 范围',
  modeScopeCount: '一行的含义',
  thMode: '方式',
  thRule: '规则',
  thScope: '范围',
  thOwnedCells: '拥有格',
  exclusivePresent: '存在 EXCLUSIVE',
  sharedOnly: '仅共享',
  conflictSummary: '{conflicts} 处冲突 · {shared} 处共享但无冲突',
  modeScopeNote:
    '同一 (owner, siting) 的行会在谓词内部被排除；A 与 B 不同，因此每处重叠都会被比较。RESERVED 读起来像 SHARED，并在宿主预留到期前挡住 EXCLUSIVE。',
};

const enAuthoring = {
  why:
    'A document writes four things at once: geometry, assertions, dataflow and placement. This sheet traces each field to the single runtime object it creates, so a wrong tag is caught at the field instead of three hundred lines into a plan.',
  document: 'Document',
  documentCount: 'mymod:campfire.json · schema 2',
  documentAction: 'select a field',
  treeAria: 'Campfire document tree',
  documentNote: 'Selected path {path}. Click a graph node on the right to jump back to the field that creates it.',
  fieldObject: 'Field → object',
  unmapped: 'UNMAPPED',
  jumpToField: 'Jump to field',
  jsonPath: 'JSON path',
  javaType: 'Java type',
  runtimeRole: 'Runtime role',
  unmappedNote: 'This field is metadata; it creates no object of its own.',
  cuboidNoteLead: ' is origin + size, not two corners. Runtime max is (a+d, b+e, c+f) and is exclusive.',
  expectTag: 'expect · one tag',
  expectTagCount: 'tagged union',
  canonical: 'Canonical',
  canonicalTitle: 'Show the canonical kind/args form',
  expectNote: 'The key is the tag and the value is its argument. Two tags in one object are SC-DOC-0605, not an implicit AND.',
  objectGraph: 'Object graph',
  objectGraphCount: '{nodes} nodes · {edges} edges',
  objectGraphAction: 'selected field highlighted',
  graphLabel: 'Object graph created by the campfire document',
  graphTitle: 'Document fields lowered into one Blueprint object graph',
  graphDesc:
    'Each node is an immutable runtime value. The currently selected JSON field highlights the node it creates and the edges are orthogonal.',
  graphCaption: 'DESIGN · IMMUTABLE AFTER build()',
  graphHint: 'CLICK A NODE TO JUMP TO ITS FIELD · SELECTION = 2PX STROKE + CORNER TICKS',
  sixCases: 'expect · the six cases',
  sixCasesCount: 'SC-DOC-0605 / 0606 / SC-CHK-0101',
  legal: 'Legal',
  rejected: 'Rejected',
  built: 'Built',
  partial: 'Partial',
  specOnly: 'Spec only',
};

const zhAuthoring: typeof enAuthoring = {
  why:
    '一份文档同时写下四样东西：几何、断言、数据流与摆放。本图纸把每个字段追溯到它创建的那一个运行时对象，于是错误的标签在字段处就会被抓住，而不是等到计划写到第三百行。',
  document: '文档',
  documentCount: 'mymod:campfire.json · schema 2',
  documentAction: '选择一个字段',
  treeAria: '营火文档树',
  documentNote: '当前路径 {path}。点击右侧的对象图节点，可以跳回创建它的字段。',
  fieldObject: '字段 → 对象',
  unmapped: '未映射',
  jumpToField: '跳转到字段',
  jsonPath: 'JSON 路径',
  javaType: 'Java 类型',
  runtimeRole: '运行时角色',
  unmappedNote: '该字段是元数据，本身不创建任何对象。',
  cuboidNoteLead: ' 是原点 + 尺寸，不是两个角点。运行时 max 为 (a+d, b+e, c+f)，且是排他的。',
  expectTag: 'expect · 一个标签',
  expectTagCount: '标签联合',
  canonical: '规范形式',
  canonicalTitle: '显示规范的 kind/args 形式',
  expectNote: '键是标签，值是该标签的参数。一个对象里出现两个标签就是 SC-DOC-0605，而不是隐式 AND。',
  objectGraph: '对象图',
  objectGraphCount: '{nodes} 个节点 · {edges} 条边',
  objectGraphAction: '高亮所选字段',
  graphLabel: '营火文档创建的对象图',
  graphTitle: '文档字段降级进同一张 Blueprint 对象图',
  graphDesc: '每个节点都是一个不可变的运行时值。当前选中的 JSON 字段会高亮它创建的节点，边均为正交。',
  graphCaption: 'DESIGN · build() 之后不可变',
  graphHint: '点击节点跳到其字段 · 选中 = 2PX 描边 + 角标',
  sixCases: 'expect · 六种情形',
  sixCasesCount: 'SC-DOC-0605 / 0606 / SC-CHK-0101',
  legal: '合法',
  rejected: '被拒绝',
  built: '已构建',
  partial: '部分',
  specOnly: '仅规范',
};

const enPredicates = {
  why:
    'The compiler pins the focus and the expectation to one subject type. This sandbox is that constructor check made visible: change the subject, and the check stops existing before any world is read. Change the sample, and the same predicate returns one of exactly four replies.',
  focusExpectation: 'Focus & expectation',
  focusCount: 'one T',
  focusLabel: 'Focus · takes the subject',
  expectationLabel: 'Expectation · judges it',
  declaredSubject: 'Declared subject T',
  subjectMismatch: 'Declared T "{typed}" does not match {expr}.subject() = {subject}.',
  expectationMismatch: 'Expectation<{required}> cannot judge a {typed}. {message}',
  tree: 'Predicate tree',
  treeCount: '{n} root children',
  reset: 'Reset',
  root: 'root',
  nestedCombinator: 'nested combinator',
  leaf: 'leaf · {kind}',
  childrenOne: '{n} child',
  childrenMany: '{n} children',
  removeNode: 'Remove {expr}',
  simulatedReply: 'Simulated reply',
  notBuilt: 'NOT BUILT',
  constructorRefused: 'SC-CHK-0101 · constructor refused',
  replyMatch: 'Bindings exist only on MATCH. The check contributes no finding.',
  replyMismatch: 'The subject was read and violates the expectation. One finding is attached.',
  replyUnreadable: 'The focus could not produce a subject. test() was never called; this is not a mismatch.',
  replyAbstain: 'The predicate declines to judge. Not a pass, and not a finding.',
  node: 'Node',
  subtree: 'Subtree',
  subtreeReply: 'Subtree reply',
  addChild: 'Add child',
  thFinding: 'Finding',
  thSeverity: 'Severity',
  thLocus: 'Locus',
  thMessage: 'Message',
  error: 'Error',
  leafMessage: 'the subject does not satisfy the leaf expectation',
  specimen: 'Specimen',
  specimenCount: 'simulation only',
  occupantBlock: 'Occupant block',
  campfireLit: 'Campfire lit',
  hasItemInventory: 'Has item_inventory',
  sectionRefused: 'Section refused',
  specimenNote:
    'A refused section makes every focus return Missing, so every leaf is UNREADABLE. The predicate is never called and no Finding is produced by it.',
  fourReplies: 'The four replies',
  fourRepliesCount: 'attempt fold order',
  thReply: 'Reply',
  thPrecedence: 'Precedence',
  thMeaning: 'Meaning',
  thCode: 'Code',
  attemptFormula: 'attempt reply = UNREADABLE > MISMATCH > ABSTAIN > MATCH',
  attemptNote: 'The attempt folds replies in that order. Inside one combinator the order is different:',
  attemptNoteTail: 'lets a known MISMATCH beat an UNREADABLE sibling.',
  combinators: 'Combinators',
  combinatorsCount: 'subject type shared',
  thOp: 'Op',
  thMatch: 'MATCH',
  thMismatch: 'MISMATCH',
  thUnknown: 'Unknown',
  combinatorNote:
    'Children of one anyOf/allOf must share the subject type. block and trait both test OccupantSnapshot and may mix; state tests T and needs a second check with its own focus.',
};

const zhPredicates: typeof enPredicates = {
  why:
    '编译器把焦点和期望钉在同一个主体类型上。这个沙盒就是那个构造函数检查的可视化：改掉主体，检查在读取任何世界之前就不存在了。改掉样本，同一个谓词只会返回四种应答之一。',
  focusExpectation: '焦点与期望',
  focusCount: '一个 T',
  focusLabel: '焦点 · 取得主体',
  expectationLabel: '期望 · 判定它',
  declaredSubject: '声明的主体 T',
  subjectMismatch: '声明 T "{typed}" 与 {expr}.subject() = {subject} 不一致。',
  expectationMismatch: 'Expectation<{required}> 无法判定一个 {typed}。{message}',
  tree: '谓词树',
  treeCount: '{n} 个根子项',
  reset: '重置',
  root: '根',
  nestedCombinator: '嵌套组合子',
  leaf: '叶子 · {kind}',
  childrenOne: '{n} 个子项',
  childrenMany: '{n} 个子项',
  removeNode: '移除 {expr}',
  simulatedReply: '模拟应答',
  notBuilt: '未构建',
  constructorRefused: 'SC-CHK-0101 · 构造函数拒绝',
  replyMatch: '绑定只存在于 MATCH。该检查不贡献任何诊断。',
  replyMismatch: '主体已被读取，且违反期望。附带一条诊断。',
  replyUnreadable: '焦点无法产出主体。test() 从未被调用；这不是不匹配。',
  replyAbstain: '谓词拒绝判定。既不是通过，也不是诊断。',
  node: '节点',
  subtree: '子树',
  subtreeReply: '子树应答',
  addChild: '添加子项',
  thFinding: '诊断',
  thSeverity: '级别',
  thLocus: '位置',
  thMessage: '消息',
  error: '错误',
  leafMessage: '主体不满足该叶子期望',
  specimen: '样本',
  specimenCount: '仅模拟',
  occupantBlock: '占用体方块',
  campfireLit: '营火已点燃',
  hasItemInventory: '带有 item_inventory',
  sectionRefused: '区段拒读',
  specimenNote:
    '区段拒读会让每个焦点都返回 Missing，于是每个叶子都是 UNREADABLE。谓词从未被调用，也不会由它产生任何诊断。',
  fourReplies: '四种应答',
  fourRepliesCount: '尝试折叠顺序',
  thReply: '应答',
  thPrecedence: '优先级',
  thMeaning: '含义',
  thCode: '代码',
  attemptFormula: 'attempt reply = UNREADABLE > MISMATCH > ABSTAIN > MATCH',
  attemptNote: '整次尝试按该顺序折叠应答。在单个组合子内部顺序不同：',
  attemptNoteTail: '允许已知的 MISMATCH 压过不可读的兄弟项。',
  combinators: '组合子',
  combinatorsCount: '共享主体类型',
  thOp: '运算',
  thMatch: 'MATCH',
  thMismatch: 'MISMATCH',
  thUnknown: '未知',
  combinatorNote:
    '同一个 anyOf/allOf 的子项必须共享主体类型。block 与 trait 都测试 OccupantSnapshot，可以混用；state 测试 T，需要第二个带自己焦点的检查。',
};

const enResults = {
  why:
    'A result is one of five sealed values, and a finding is one of nine record components. The five arms keep “the world is wrong” apart from “I could not tell”; the nine components keep a report addressable, deduplicable and readable without the code path that produced it.',
  stateMachine: 'MatchResult state machine',
  stateMachineCount: '5 arms · 1 sealed verdict',
  stateMachineAction: 'click an arm',
  diagramLabel: 'MatchResult state machine from request to five arms',
  diagramTitle: 'One attempt, five result arms',
  diagramDesc:
    'The request reaches the judge; a SETTLE match reaches settle; a verdict bus distributes to Admitted, Formed, Failed, Undecided and Deferred.',
  caption: 'ONE ATTEMPT · SEALED ONCE',
  requestSub: 'ticket · stage · lease',
  verdictEdge: 'verdict',
  noDispatch: 'no dispatch · capacity / closed lease / policy',
  footer: 'REQUEST → JUDGE → SETTLE',
  armTitle: 'Arm · {name}',
  thField: 'Field',
  thType: 'Type',
  findingAnatomy: 'Finding anatomy',
  findingAnatomyCount: '9 components · F-0004',
  findingAnatomyAction: 'click a component',
  findingAria: 'Finding components',
  thHash: '#',
  thComponent: 'Component',
  thExample: 'Example',
  findingNote: 'FINDING {id} · {severity} · select a row to inspect it.',
  example: 'Example',
  whyComponent: 'Why this component exists',
  findingValueNote:
    'A finding is a value, never an emitter side effect. Producers hand it to a sink; only build() orders, dedups and caps it.',
  bundle: 'Finding bundle order',
  bundleCount: '{n} findings · {depth} keys',
  reset: 'Reset',
  thId: 'ID',
  thSeverity: 'Severity',
  thStage: 'Stage',
  thCode: 'Code',
  thLocus: 'Primary locus',
  thInsert: 'Insert',
  thMessage: 'Message',
  activeKeys: 'Active keys',
  reemit: 'DIRTY_UNSTABLE · re-emission',
  outcome: 'Outcome digest',
  generation: 'generation {n}',
  generationLabel: 'generation',
  reorder: 'Reorder',
};

const zhResults: typeof enResults = {
  why:
    '结果是五种封存值之一，诊断是九个记录组件之一。五个分支把“世界是错的”和“我说不出来”分开；九个组件让一份报告可寻址、可去重、并且不依赖产生它的代码路径也能读懂。',
  stateMachine: 'MatchResult 状态机',
  stateMachineCount: '5 个分支 · 一次封存裁决',
  stateMachineAction: '点击一个分支',
  diagramLabel: '从请求到五个分支的 MatchResult 状态机',
  diagramTitle: '一次尝试，五种结果分支',
  diagramDesc: '请求到达判定器；SETTLE 的匹配进入结算；裁决总线分发到 Admitted、Formed、Failed、Undecided 与 Deferred。',
  caption: '一次尝试 · 只封存一次',
  requestSub: 'ticket · 阶段 · 租约',
  verdictEdge: '裁决',
  noDispatch: '未派发 · 容量 / 租约已关 / 策略',
  footer: 'REQUEST → JUDGE → SETTLE',
  armTitle: '分支 · {name}',
  thField: '字段',
  thType: '类型',
  findingAnatomy: '诊断解剖',
  findingAnatomyCount: '9 个组件 · F-0004',
  findingAnatomyAction: '点击一个组件',
  findingAria: '诊断组件',
  thHash: '#',
  thComponent: '组件',
  thExample: '示例',
  findingNote: '诊断 {id} · {severity} · 选择一行查看。',
  example: '示例',
  whyComponent: '这个组件为何存在',
  findingValueNote:
    '诊断是一个值，绝不是 emitter 的副作用。生产者把它交给 sink；只有 build() 负责排序、去重与封顶。',
  bundle: '诊断包顺序',
  bundleCount: '{n} 条诊断 · {depth} 个键',
  reset: '重置',
  thId: 'ID',
  thSeverity: '级别',
  thStage: '阶段',
  thCode: '代码',
  thLocus: '主位置',
  thInsert: '插入',
  thMessage: '消息',
  activeKeys: '生效键',
  reemit: 'DIRTY_UNSTABLE · 再次发射',
  outcome: '产物摘要',
  generation: '第 {n} 代',
  generationLabel: 'generation',
  reorder: '重排',
};

const enGlossary = {
  why:
    'A glossary is not a dictionary. Every card pairs the shape of a term with the failure it prevents: the reason a second key type, a PartialKey or a per-bay claim row was rejected is the only thing that keeps the design from drifting back to them.',
  termIndex: 'Term index',
  termIndexCount: '{shown} / {total}',
  searchTerms: 'Search terms',
  searchPlaceholder: 'e.g. Occupied, whenAbsent, Reply, SC-CHK-0101',
  searchAria: 'Search glossary terms',
  all: 'All',
  groupAria: 'Filter by group',
  indexNote: 'what it is / why it exists',
  clear: 'Clear',
  empty: 'NO TERMS · adjust the query or group filter',
  whyCard: 'Why it exists',
};

const zhGlossary: typeof enGlossary = {
  why:
    '术语表不是词典。每张卡片都把术语的形状与它所阻止的失败配在一起：第二套键类型、PartialKey 或按舱位的声明行为何被否决——正是这条理由让设计不会重新漂回它们。',
  termIndex: '术语索引',
  termIndexCount: '{shown} / {total}',
  searchTerms: '搜索术语',
  searchPlaceholder: '例如 Occupied、whenAbsent、Reply、SC-CHK-0101',
  searchAria: '搜索术语表',
  all: '全部',
  groupAria: '按分组筛选',
  indexNote: '它是什么 / 为何存在',
  clear: '清除',
  empty: '没有匹配术语 · 请调整查询或分组筛选',
  whyCard: '为何存在',
};

const enSpace = {
  why:
    'A siting is a function, not a search. The anchor fixes the orientation, so the same document lands the same way on every world and every reload; the eight candidates that satisfy the anchor are indistinguishable on the anchor cell and only the document\u2019s front decides among them.',
  axonometric: 'Axonometric study',
  axonometricCount: 'bounds 3 × 3 × 3',
  axonometricAction: 'world ↔ local',
  diagramLabel: 'Rotating wireframe cube with a highlighted marker cell and world axes',
  diagramTitle: 'A 3 × 3 × 3 blueprint under the current orientation',
  diagramDesc:
    'The cube is the campfire bounds. The orange quad is the fire bay at local (1,0,1); the arrow marks the authored front, north.',
  boundsLabel: 'BOUNDS {bounds}',
  orientationLabel: 'ORIENTATION {id} · {triple}',
  markerLabel: 'MARKER {marker} → {world}',
  frontLabel: 'FRONT · {heading}',
  worldAxes: 'WORLD AXES',
  fireBayCell: 'FIRE BAY CELL',
  cubeEdge: 'CUBE EDGE',
  authoredFront: 'AUTHORED FRONT',
  controls: 'Orientation controls',
  controlsCount: '48 elements',
  turnLabel: 'Turn · yaw about Y',
  tiltLabel: 'Tilt · face up',
  tiltNote:
    'One tilt per face-up direction. The six tilts × four turns × two parities = 48; the canonical triple is (turn, tilt, parity).',
  flip: 'Flip · FRONT_BACK',
  ordinal: 'Ordinal',
  ordinalUnit: '/ 47',
  candidates: 'Satisfying candidates',
  candidatesUnit: '/ 48',
  anchor: 'Anchor',
  anchorCount: 'one cell read',
  anchorHeading: 'Anchor heading',
  candidate: 'Candidate',
  pass: 'Pass',
  noMatch: 'No match',
  candidateFormula: 'candidate.apply(NORTH) = {applied} · anchorHeading = {heading}',
  headingMap: 'Heading map',
  headingMapCount: 'local → world',
  thLocal: 'Local',
  thWorld: 'World',
  thVector: 'Vector',
  headingNote:
    "Six headings, eight orientations each. The cube's image is x ∈ [{x0}, {x1}), y ∈ [{y0}, {y1}), z ∈ [{z0}, {z1}) — volume {volume} cells at every orientation.",
  sitingFunction: 'Siting function',
  sitingCount: '{n} of 48 candidate',
  satisfyingFor: 'Satisfying ordinals for anchor {heading}',
  orientationTitle: 'Orientation {o}',
  sitingNote:
    'Turn is cyclic of order 4; tilt is a transversal with six distinct up() images; parity is the determinant. 4 × 6 × 2 = 48, and (turn, tilt, parity) is a bijection — the three-generator names with three flips are 72 names for 48 elements.',
};

const zhSpace: typeof enSpace = {
  why:
    '摆放是一个函数，不是一次搜索。锚点固定朝向，因此同一份文档在每个世界、每次重载后都以同样方式落地；满足锚点的八个候选在锚点格上无法区分，只有文档的正面能决定选哪个。',
  axonometric: '轴测研究',
  axonometricCount: '包围盒 3 × 3 × 3',
  axonometricAction: '世界 ↔ 局部',
  diagramLabel: '旋转的线框立方体，带高亮标记格与世界轴',
  diagramTitle: '当前朝向下的 3 × 3 × 3 蓝图',
  diagramDesc: '立方体就是营火的包围盒。橙色四边形是局部 (1,0,1) 的火塘舱位；箭头标出编写的正面，北方。',
  boundsLabel: '包围盒 {bounds}',
  orientationLabel: '朝向 {id} · {triple}',
  markerLabel: '标记 {marker} → {world}',
  frontLabel: '正面 · {heading}',
  worldAxes: '世界轴',
  fireBayCell: '火塘格',
  cubeEdge: '立方体棱',
  authoredFront: '编写的正面',
  controls: '朝向控制',
  controlsCount: '48 个元素',
  turnLabel: '转向 · 绕 Y 偏航',
  tiltLabel: '倾斜 · 朝上面',
  tiltNote: '每个朝上面方向对应一种倾斜。六种倾斜 × 四种转向 × 两种奇偶 = 48；规范三元组是 (turn, tilt, parity)。',
  flip: '镜像 · FRONT_BACK',
  ordinal: '序号',
  ordinalUnit: '/ 47',
  candidates: '满足条件的候选',
  candidatesUnit: '/ 48',
  anchor: '锚点',
  anchorCount: '读取一格',
  anchorHeading: '锚点朝向',
  candidate: '候选',
  pass: '通过',
  noMatch: '不匹配',
  candidateFormula: 'candidate.apply(NORTH) = {applied} · anchorHeading = {heading}',
  headingMap: '朝向映射',
  headingMapCount: '局部 → 世界',
  thLocal: '局部',
  thWorld: '世界',
  thVector: '向量',
  headingNote:
    '六个朝向，每个八种朝向。立方体的像为 x ∈ [{x0}, {x1})、y ∈ [{y0}, {y1})、z ∈ [{z0}, {z1})——任何朝向下体积都是 {volume} 格。',
  sitingFunction: '摆放函数',
  sitingCount: '48 个候选中的 {n} 个',
  satisfyingFor: '锚点 {heading} 的满足序号',
  orientationTitle: '朝向 {o}',
  sitingNote:
    '转向是四阶循环；倾斜是横截，up() 的像有六个不同值；奇偶是行列式。4 × 6 × 2 = 48，且 (turn, tilt, parity) 是双射——三个生成元名配三种镜像，是 48 个元素的 72 个名字。',
};

const enExplain = {
  open: 'Explain',
  openTitle: 'Open the explain layer',
  kicker: 'EXPLAIN',
  title: 'Explain layer',
  close: 'Close explain layer',
  search: 'Search the index',
  searchPlaceholder: 'term, code or rule…',
  searchAria: 'Search explain entries',
  filter: 'Category filter',
  all: 'All',
  count: '{shown} / {total}',
  what: 'What it is',
  why: 'Why it exists',
  rule: 'Rule',
  source: 'Source',
  related: 'Related',
  index: 'Index',
  empty: 'No entry matches that query.',
  hint: 'ESC closes. Opening an entry keeps page, scenario, step and language in the URL.',
  fromStep: 'Explain this frame',
  cellRole: 'Cell role',
  categories: {
    Model: 'Model',
    Space: 'Space',
    Predicates: 'Predicates',
    Context: 'Context',
    Execution: 'Execution',
    Claims: 'Claims',
    Results: 'Results',
    Codes: 'Codes',
  } as Record<string, string>,
};

const zhExplain: typeof enExplain = {
  open: '解释',
  openTitle: '打开解释层',
  kicker: '解释',
  title: '解释层',
  close: '关闭解释层',
  search: '检索索引',
  searchPlaceholder: '术语、代码或规则…',
  searchAria: '检索解释条目',
  filter: '类别筛选',
  all: '全部',
  count: '{shown} / {total}',
  what: '是什么',
  why: '为什么存在',
  rule: '规则',
  source: '出处',
  related: '相关条目',
  index: '索引',
  empty: '没有匹配该查询的条目。',
  hint: 'ESC 关闭。切换条目时，page、scenario、step 与 lang 都保留在 URL 中。',
  fromStep: '解释这一帧',
  cellRole: '格的角色',
  categories: {
    Model: '模型',
    Space: '空间',
    Predicates: '谓词',
    Context: '上下文',
    Execution: '执行',
    Claims: '声明',
    Results: '结果',
    Codes: '代码',
  },
};

const en = {
  chrome: enChrome,
  demo: enDemo,
  explain: enExplain,
  nav: enNav,
  pages: enPages,
  overview: enOverview,
  anatomy: enAnatomy,
  pipeline: enPipeline,
  context: enContext,
  claims: enClaims,
  authoring: enAuthoring,
  predicates: enPredicates,
  results: enResults,
  glossary: enGlossary,
  space: enSpace,
};

const zh: typeof en = {
  chrome: zhChrome,
  demo: zhDemo,
  explain: zhExplain,
  nav: zhNav,
  pages: zhPages,
  overview: zhOverview,
  anatomy: zhAnatomy,
  pipeline: zhPipeline,
  context: zhContext,
  claims: zhClaims,
  authoring: zhAuthoring,
  predicates: zhPredicates,
  results: zhResults,
  glossary: zhGlossary,
  space: zhSpace,
};

export const DICT = { en, zh } as const;

export type Dict = typeof en;

/** Fills a `{name}` template from the dictionary. */
export function fmt(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? ''));
}

/** Page copy translated from the English data files, keyed by that English source. */
export const COPY: Record<Lang, Record<string, string>> = {
  en: {},
  zh: {
    // mission scenarios
    'Standard run': '标准流程',
    'Stale re-check': '陈旧重检',
    'A clean furnace reaches Formed.': '干净的高炉走到 Formed。',
    'One section is unreadable; the key is Missing.': '一个区段不可读；整键 Missing。',
    'A second structure already owns the cells.': '第二座结构已经拥有这些格子。',
    'A dirty mark re-emits Formed at generation + 1.': '脏标记让 Formed 以代次 +1 重发。',
    // demo scenarios
    'Trace one assertion': '追踪一条断言',
    'A campfire check travels from the authored file to Formed. Each stop names the object the token is inside and what it carries.':
      '一个营火检查从作者文件一路走到 Formed。每一站都说明令牌所在的对象以及它携带的内容。',
    'Trace a refutation': '追踪一次反驳',
    'The same route with one unlit campfire: the token stops on Failed and no claim row is ever written.':
      '同一条路线，但营火没有点燃：令牌停在 Failed，任何声明行都不会写入。',
    'bounds + expect{"block":"minecraft:campfire"} + checks[] + context[]':
      'bounds + expect{"block":"minecraft:campfire"} + checks[] + context[]',
    'Focus<T> and Expectation<T> pinned to the same T at construction':
      'Focus<T> 与 Expectation<T> 在构造时被钉在同一个 T 上',
    'focus.take → Found → expectation.test → MATCH, folded into one attempt reply':
      'focus.take → Found → expectation.test → MATCH，折叠为一次尝试的回复',
    'MatchResult + ClaimSet (294 rows) + FindingBundle, sealed after Settled':
      'MatchResult + ClaimSet（294 行）+ FindingBundle，在 Settled 之后封存',
    'one tag, one property, one expected value on the fire bay check':
      '火舱检查上的一个标签、一个属性、一个期望值',
    'focus occupant(1, 0, 1); expectation state("lit") judges the frozen snapshot':
      '焦点 occupant(1, 0, 1)；期望 state("lit") 判定冻结的快照',
    'the subject was read and violated the expectation; refutation outranks every unreadable cell':
      '主体已被读取并违反期望；反驳压过任何一个不可读的格',
    'FindingBundle with one ERROR; no claims, no instance id, derive() refuses it':
      '带一个 ERROR 的 FindingBundle；没有声明、没有实例 id，derive() 会拒绝它',

    'Turn all 48': '转动 48',
    'Cycles every ordinal at 600 ms per step, 4 turns × 6 tilts × 2 parities. Hover or focus the elevation to hold the frame.':
      '每 600 ms 前进一个序号，覆盖 4 转向 × 6 倾斜 × 2 奇偶。指针或焦点进入视图时暂停当前帧。',
    'Only the 8 satisfying': '仅 8 个满足项',
    'Visits just the ordinals whose candidate maps the authored front onto the anchor heading; the other 40 never qualify.':
      '只访问那些能把作者正面映射到锚点朝向的序号；其余 40 个永远不合格。',

    'Lowering, line by line': '逐行 lowering',
    'Four JSON lines, four runtime values. Each step highlights the line and pulses the object the builder creates from it.':
      '四行 JSON，四个运行时值。每一步高亮一行，并脉冲高亮构建器由此创建的对象。',
    'Anchor → Siting': '锚点 → 摆放',
    'The two placement lines at the end of the file: an AnchorSpec is read at the definition origin, and a Siting consumes the heading it yields.':
      '文件末尾的两条摆放语句：AnchorSpec 在定义原点读取，Siting 消费它产生的朝向。',
    'bounds → Cuboid': 'bounds → Cuboid',
    '[0,0,0,3,3,3] becomes min/max and stays half-open: 27 cells, and not automatically any bay.':
      '[0,0,0,3,3,3] 变成 min/max 且保持半开：27 格，并且不会自动成为任何舱位。',
    'bays[] → Bay': 'bays[] → Bay',
    'One array entry becomes one bay with ids, cells, Coverage, ClaimMode and ClaimScope; a shell face is not one bay.':
      '一个数组项变成一个舱位，带有 id、cells、Coverage、ClaimMode 与 ClaimScope；外壳的一个面不是一个舱位。',
    'checks[] → Check': 'checks[] → Check',
    'focus and expect are read together, and the constructor pins both sides to one T; a bay-level expect lowers separately.':
      'focus 与 expect 一起读取，构造函数把两侧钉在同一个 T 上；舱位级的 expect 单独降级。',
    'context[] → ContextKey': 'context[] → ContextKey',
    '(id, type, Retention, merge, whenAbsent); the computation that writes this key is lowered from computations[].':
      '(id, type, Retention, merge, whenAbsent)；写入该键的计算从 computations[] 降级而来。',
    'anchors[0] → AnchorSpec': 'anchors[0] → AnchorSpec',
    'role + property + value; it is read once at the definition origin, before any orientation is chosen.':
      'role + property + value；它在定义原点读取一次，早于任何朝向选择。',
    'orientation → Siting': 'orientation → Siting',
    'The authored front is mapped onto the heading the anchor yielded; the resolver itself is still a spec.':
      '作者正面被映射到锚点产生的朝向；解析器本身仍只是规范。',
    '$ → Blueprint': '$ → Blueprint',
    'The four groups land in one immutable record: bounds, bays, checks and computations, anchors and the siting.':
      '四组内容落进一个不可变记录：bounds、bays、checks 与 computations、anchors 与摆放。',

    'How one giant Check breaks': '一个大 Check 怎么坏',
    'Four contradictions accrue one at a time — subject type, stage, ownership, chunking — then the elevation splits into the three bays that can actually be judged.':
      '四个矛盾逐一累加——主体类型、阶段、归属、分块——最后立面拆成三个真正可判定的舱位。',
    'The legal split': '合法拆分',
    'The same cavity read as three bays: each step lights one bay and states the coverage, scope and stage it alone can carry.':
      '同一个空腔按三个舱位来读：每一步点亮一个舱位，并给出它独自能承担的 coverage、scope 与阶段。',
    'T mismatch': '类型不匹配',
    'Stage mismatch': '阶段不匹配',
    'Ownership mismatch': '归属不匹配',
    'Split into three bays': '拆成三个舱位',

    'Three sections, one fold': '三个区段，一次折叠',
    'Three contributions arrive out of authored order; the fold sorts by SectionKey (x, z, y), adds them one at a time, then lights the tier frames.':
      '三个贡献不按编写顺序到达；折叠按 SectionKey (x, z, y) 排序，逐一相加，然后依次点亮层级帧。',
    'A refused middle section': '中间区段被拒读',
    'The second canonical section is refused. The fold aborts at that contribution, mymod:volume becomes Missing, and no predicate is ever called.':
      '规范顺序中的第二个区段被拒读。折叠在该贡献处中止，mymod:volume 变成 Missing，谓词从未被调用。',
    'Declined, not refused': '被拒绝，而不是被拒读',
    'Every section is readable and none contributes: volume stays Missing, while heat honours whenAbsent = 0. This is the only path where a fallback applies.':
      '每个区段都可读且都没有贡献：volume 保持 Missing，而 heat 兑现 whenAbsent = 0。这是唯一适用回退值的路径。',

    'SETTLE · Formed': 'SETTLE · Formed',
    'The happy path. Capture, expand, sections, fold, aggregate, judge, publish, then one ledger CAS returns Settled.':
      '顺利路径：捕获、展开、区段、折叠、聚合、判定、发布，然后一次账本 CAS 返回 Settled。',
    'SC-FOC-0301 · Undecided': 'SC-FOC-0301 · Undecided',
    'A section slot is refused. The contributions stop at the barrier, mymod:volume is absent, and Judge never invokes the predicate.':
      '某个区段槽位被拒读。贡献在屏障处停止，mymod:volume 缺席，Judge 从不调用谓词。',
    'SC-ENG-0001 · Undecided': 'SC-ENG-0001 · Undecided',
    'The instance budget refuses one aggregate unit. The run stops at step 05 with BUDGET_EXHAUSTED; a re-read is licensed, a repair is not.':
      '实例预算拒绝了一个聚合单元。运行在第 05 步以 BUDGET_EXHAUSTED 停止；允许重读，不允许修复。',
    'SC-CLM-0002 · Failed': 'SC-CLM-0002 · Failed',
    'Every step passes, then SETTLE finds an incumbent on an EXCLUSIVE cell. The attempt fails with the incumbent rows attached.':
      '每一步都通过，随后 SETTLE 在某个 EXCLUSIVE 格上发现既有占用者。尝试失败，并附带既有行。',

    'EXCLUSIVE overlap': 'EXCLUSIVE 重叠',
    'Both structures claim EXCLUSIVE + Owned on the same two cells. Rows are written, the matrix compares them, and the second settle fails.':
      '两个结构都对同样的两格声明 EXCLUSIVE + Owned。行被写入，矩阵完成比对，第二次结算失败。',
    'SHARED overlap': 'SHARED 重叠',
    'The same footprint, both SHARED. The overlap is real, the matrix records it, and Settled is returned: shared rows never conflict with each other.':
      '同样的足迹，两者都是 SHARED。重叠真实存在，矩阵记录了它，并返回 Settled：共享行彼此从不冲突。',
    'RESERVED blocks EXCLUSIVE': 'RESERVED 阻止 EXCLUSIVE',
    'A is EXCLUSIVE, B is RESERVED. RESERVED reads like SHARED but blocks EXCLUSIVE until the host’s reservation expires, so the settle fails.':
      'A 是 EXCLUSIVE，B 是 RESERVED。RESERVED 读起来像 SHARED，但在宿主的预留到期前会阻止 EXCLUSIVE，因此结算失败。',
    'Revision moved': '版本已移动',
    'No cell conflict, but the attempt captured revision 4 while the ledger is at 5. RevisionMoved is retried once, then Undecided(LEDGER_MOVED).':
      '没有格子冲突，但尝试捕获的是版本 4，而账本已在 5。RevisionMoved 会重试一次，随后是 Undecided(LEDGER_MOVED)。',

    'Arm transitions': '分支迁移',
    'Each step selects one arm and draws the orthogonal path it was reached by: request → bus → arm, or Admitted → later SETTLE → Formed.':
      '每一步选择一个分支，并画出到达它的正交路径：request → 总线 → 分支，或 Admitted → 之后的 SETTLE → Formed。',
    'DIRTY_UNSTABLE re-emission': 'DIRTY_UNSTABLE 再次发射',
    'The same outcome is emitted twice. generation increments, ledgerRevision increments, and the outcome digest stays byte-identical.':
      '同一批事实发射两次。generation 递增，ledgerRevision 递增，而每个事实摘要保持逐字节相同。',
    'Reorder by five keys': '按五个键重排',
    'The bundle sorts by severity, stage, code, primary locus and insertion, one key at a time; rows only highlight, they never fly.':
      '诊断包依次按级别、阶段、代码、主位置和插入序排序，每次只加一个键；行只高亮，绝不飞入。',
    'First emission after Settled. Claims carry generation 1 and the SETTLE frame revision 7.':
      'Settled 之后的首次发射。声明携带第 1 代与 SETTLE 帧版本 7。',
    'A re-read produced the same outcome and the same context frame; only generation and ledgerRevision moved.':
      '一次重读产生了相同的事实与相同的上下文帧；只有 generation 与 ledgerRevision 变化。',

    'Campfire · MATCH': '营火 · MATCH',
    'Occupant focus, one block leaf. The subject is Found, the leaf reads the frozen snapshot, and the attempt returns MATCH.':
      '占用者焦点，一个 block 叶子。主体为 Found，叶子读取冻结快照，整次尝试返回 MATCH。',
    'Stone → MISMATCH': '石头 → MISMATCH',
    'The same predicate with stone bricks in the cell. The subject is still Found, the leaf is decisive, and one SC-EXP-0100 finding follows.':
      '同一个谓词，但格子里是石砖。主体仍是 Found，叶子起决定作用，并产生一条 SC-EXP-0100 诊断。',
    'Refused section → UNREADABLE': '拒读区段 → UNREADABLE',
    'A refused section makes the cell unreadable before any predicate runs. focus.take returns Missing and UNREADABLE wins the reply fold.':
      '被拒读的区段让该格在任何谓词运行之前就不可读。focus.take 返回 Missing，UNREADABLE 赢得回复折叠。',

    // overview · layer stack
    Document: '文档',
    'Object graph': '对象图',
    Execution: '执行',
    Result: '结果',
    'What is declared?': '声明了什么？',
    'What does the runtime hold?': '运行时持有什么？',
    'What runs, in what order?': '什么在运行，按什么顺序？',
    'What is true after the call?': '调用之后什么是真的？',
    'the authored file: geometry, assertions, dataflow, placement':
      '作者写下的文件：几何、断言、数据流、摆放',
    'immutable values assembled at DESIGN; the JSON never survives into execution':
      '在 DESIGN 阶段装配好的不可变值；JSON 从不存活到执行阶段',
    'plan, capture, sections, the barrier, judging, publication': '计划、捕获、区段、屏障、判定、发布',
    'verdict, claims, context frames, findings': '裁决、声明、上下文帧、诊断',

    // overview · concepts
    'One file holds four concerns — geometry, assertions, dataflow and placement — under one schemaVersion.':
      '一份文件在一个 schemaVersion 下容纳四类关注点——几何、断言、数据流与摆放。',
    '[x, y, z, sizeX, sizeY, sizeZ] is origin + size; the runtime Cuboid is half-open, so [0,0,0,3,3,3] is 27 cells.':
      '[x, y, z, sizeX, sizeY, sizeZ] 是原点 + 尺寸；运行时 Cuboid 半开，所以 [0,0,0,3,3,3] 是 27 格。',
    'One tag per object: block, air, trait, state, or a combinator. Two tags are SC-DOC-0605, not an implicit AND.':
      '每个对象一个标签：block、air、trait、state 或一个组合子。两个标签是 SC-DOC-0605，而不是隐式的 AND。',
    'phase: section contributes a partial per section; phase: aggregate runs once after the barrier and writes the fold.':
      'phase: section 每个区段贡献一个部分值；phase: aggregate 在屏障之后运行一次并写入折叠结果。',
    'A typed key with a Retention and an optional Merge; merge == null is a single writer, merge != null allows section contributions.':
      '带 Retention 和可选 Merge 的类型化键；merge == null 表示单写者，merge != null 允许区段贡献。',
    'role + property + value, read at the definition origin; the heading it yields fixes the siting, not the other way round.':
      'role + property + value，在定义原点处读取；由它得到的朝向决定摆放，而不是反过来。',
    '(id, bounds, bays, anchors, derivations) — the complete declaration, immutable after build.':
      '(id, bounds, bays, anchors, derivations)——完整声明，build 之后不可变。',
    '(id, cells, Coverage, ClaimMode, ClaimScope, Requirement) — a cuboid that can be judged, owned and staged on its own.':
      '(id, cells, Coverage, ClaimMode, ClaimScope, Requirement)——可被独立判定、拥有并分阶段的长方体。',
    'List<Check<?>> + List<Computation<?>> for one bay; empty is illegal because a bay exists to assert.':
      '一个舱位的 List<Check<?>> + List<Computation<?>>；空是非法的，因为舱位存在的意义就是断言。',
    'Focus<T> + Expectation<T> + stage + admission; the constructor pins both sides to the same T (SC-CHK-0101).':
      'Focus<T> + Expectation<T> + 阶段 + 计费额；构造函数把两侧钉在同一个 T 上（SC-CHK-0101）。',
    'One output ContextKey<T>, a region, inputs for the Kahn order, and a SECTION or AGGREGATE phase.':
      '一个输出 ContextKey<T>、一个区域、用于 Kahn 排序的输入，以及 SECTION 或 AGGREGATE 阶段。',
    '(id, type, Retention, merge, whenAbsent); equality is (id, type), and a second registration is SC-CTX-0102.':
      '(id, type, Retention, merge, whenAbsent)；相等性是 (id, type)，重复注册是 SC-CTX-0102。',
    'Section computations → barrier → aggregate computations and derivers in Kahn order → checks, after the units they read.':
      '区段计算 → 屏障 → 按 Kahn 序的聚合计算与派生器 → 检查，均排在其读取的单元之后。',
    'A section computation runs once per section in region ∩ section and contributes a partial; it cannot read a ContextKey.':
      '区段计算在 region ∩ section 的每个区段运行一次并贡献部分值；它不能读取 ContextKey。',
    'Contributions sort by SectionKey (x, z, y) and combine left-to-right; no identity element, and a refusal makes the key absent.':
      '贡献按 SectionKey (x, z, y) 排序并从左到右合并；没有单位元，任一拒读都会让键缺席。',
    'focus.take returns Found or Missing; only Found reaches expectation.test. The attempt folds UNREADABLE > MISMATCH > ABSTAIN > MATCH.':
      'focus.take 返回 Found 或 Missing；只有 Found 会到达 expectation.test。整次尝试按 UNREADABLE > MISMATCH > ABSTAIN > MATCH 折叠。',
    'One draft per stage boundary; a stale baseRevision is SC-CTX-0108 and nothing is written.':
      '每个阶段边界一份草稿；baseRevision 过期即 SC-CTX-0108，什么也不会写入。',
    'One ledger transaction replaces every row of (owner, siting); only Settled can produce Formed.':
      '一次账本事务替换 (owner, siting) 的所有行；只有 Settled 才能产生 Formed。',
    'Admitted, Formed, Failed, Undecided, Deferred; only SETTLE + Settled produces Formed, and Admitted is not a structure.':
      'Admitted、Formed、Failed、Undecided、Deferred；只有 SETTLE + Settled 产生 Formed，而 Admitted 不是结构。',
    'The rows and the ledger revision Formed settled at; a later replacement is detectable from that number.':
      'Formed 结算时的行与账本版本；后续的替换可以从这个数字检出。',
    '(instance, revision, frames) — tier-stamped reads, so a PROBE reader never sees a FORM value.':
      '(instance, revision, frames)——按层级打戳的读取，所以 PROBE 读者永远看不到 FORM 值。',
    'Deduplicated and capped; order is severity → stage → code → primary locus → insertion, never insertion alone.':
      '去重并封顶；顺序为 severity → stage → code → 主位置 → insertion，绝不只按 insertion。',

    // overview · hand-offs
    document: '文档',
    objects: '对象图',
    execution: '执行',
    result: '结果',
    'Read text, validate, lower to records. The four JSON groups all land in one Blueprint graph.':
      '读取文本、校验、降为记录。四组 JSON 全部落进同一张 Blueprint 图。',
    'One ordered plan per stage; ties by admission ascending, specificity descending, declaration order.':
      '每个阶段一份有序计划；同序时按计费额升序、特异性降序、声明顺序。',
    'Findings are values; the verdict is sealed once and never mutated. Formed exists only after Settled.':
      '诊断是值；裁决只封存一次，永不修改。Formed 只在 Settled 之后存在。',

    // overview · readouts
    Layers: '层级',
    'Pipeline steps': '流水线步骤',
    'Reply states': '应答状态',
    'Result arms': '结果分支',
    'document → result': '文档 → 结果',
    steps: '步',
    states: '种',
    arms: '个分支',

    // anatomy · bay notes
    'The shell is a holed box, so it is not one bay: lowering emits one bay per face box, ids #box@x,y,z:sx,sy,sz. 294 − 100 = 194.':
      '壳体是带洞的盒子，因此不是一个舱位：降级时每个面盒子各自生成一个舱位，id 形如 #box@x,y,z:sx,sy,sz。294 − 100 = 194。',
    'Both halves are one subject. SUBJECT asserts the bay cells equal the union of bound footprints; Occupied owns the non-air footprint, not the empty cell. Shown here as the two-cell door variant; the worked example’s mouth is an iron-bars CUBIC bay.':
      '两半是同一个主体。SUBJECT 断言舱位格等于绑定足迹的并集；Occupied 拥有非空足迹，而不是空格。此处展示的是两格门变体；完整示例中的开口是铁栏杆 CUBIC 舱位。',
    'The cavity owns empty cells. CUBIC is asserted by per-cell air checks; a separate Long check reads the folded capacity. The capacity computation is a FORM aggregate; its reader may run at SETTLE.':
      '腔体拥有空格。CUBIC 由逐格空气检查断言；另一个 Long 检查读取折叠后的容量。容量计算是 FORM 聚合，其读者可以在 SETTLE 运行。',
    '194 cells': '194 格',
    '2 non-air bound footprints': '2 个非空绑定足迹',
    '100 cells': '100 格',

    // anatomy · ledger
    'Bounds 7 × 6 × 7': '包围盒 7 × 6 × 7',
    'Cavity 5 × 4 × 5': '腔体 5 × 4 × 5',
    Shell: '壳体',
    Mouth: '开口',
    'half-open volume': '半开体积',
    'empty, Owned': '空，Owned',
    'inside the shell face': '位于壳体面内',

    // anatomy · giant check reasons
    'The subject type differs': '主体类型不同',
    'One Check<T> has exactly one T. The shell judges 194 OccupantSnapshots, the mouth judges one DoorView with two halves, and the capacity is a Long. There is no T that is all three; forcing one is the constructor error, not a workaround.':
      '一个 Check<T> 只有一个 T。壳体判定 194 个 OccupantSnapshot，开口判定一个含两半的 DoorView，容量则是一个 Long。不存在同时是这三者的 T；强行合并就是构造函数错误，而不是变通方案。',
    'The stage differs': '阶段不同',
    'A door and an air cell are knowable at FORM. The cavity volume must be contributed per section, folded at the barrier, then scaled. A single check declares one stage; run it early and the value is Missing, run it late and the shell is not protected until SETTLE.':
      '门与空气格在 FORM 阶段即可知。腔体容量必须先按区段贡献、在屏障处折叠，再缩放。单个检查只能声明一个阶段：跑早了值是 Missing，跑晚了壳体要到 SETTLE 才受保护。',
    'The ownership differs': '归属不同',
    'The shell owns its solid cells, the mouth owns non-air bound footprints (Occupied), and the cavity must Own 100 empty cells. One scope cannot express three owned sets; SUBJECT + Owned is SC-CHK-0106, and Occupied would leave the cavity unprotected.':
      '壳体拥有它的实心格，开口拥有非空绑定足迹（Occupied），腔体必须 Own 100 个空格。一个范围无法表达三套拥有集合：SUBJECT + Owned 是 SC-CHK-0106，而 Occupied 会让腔体失去保护。',
    'Chunking needs boundaries': '分块需要边界',
    'Sections, read closures and cache entries are keyed by rectangular regions. One predicate over 294 cells has no section boundary, cannot be split across workers, and cannot be cached. CUBIC coverage must also prove that decision cells cover bay.cells at DESIGN.':
      '区段、读闭包与缓存条目都以矩形区域为键。一个覆盖 294 格的谓词没有区段边界，无法跨工作线程拆分，也无法缓存。CUBIC 覆盖还必须在 DESIGN 阶段证明判定格覆盖 bay.cells。',

    // anatomy · presence rules
    'Every cell of bay.cells is decided': 'bay.cells 的每一格都被判定',
    'bay.cells equals the union of bound footprints': 'bay.cells 等于绑定足迹的并集',
    'No cell asserted; a presence/state criterion': '不断言任何格；存在/状态判据',
    'PRESENCE asserts no cells; it is a presence/state criterion, legal only with Audit or Declared. Pairing PRESENCE with Owned would claim 100 cells while deciding none — SC-CHK-0106. The cavity must be CUBIC + Owned precisely because its 100 cells are empty.':
      'PRESENCE 不断言任何格；它是存在/状态判据，只能与 Audit 或 Declared 搭配。PRESENCE 配 Owned 会声明 100 格却一格都不判定——SC-CHK-0106。腔体必须是 CUBIC + Owned，恰恰因为它的 100 格是空的。',

    // pipeline · step names
    Capture: '捕获',
    Expand: '展开',
    'Section pass': '区段遍历',
    'Barrier fold': '屏障折叠',
    Aggregate: '聚合',
    Judge: '判定',
    Publish: '发布',
    Settle: '结算',

    // pipeline · step detail
    'Freeze the read closure on the server thread; one server-thread call, getChunkNow, never Level.getChunk.':
      '在服务端线程冻结读闭包；只做一次服务端线程调用，用 getChunkNow，绝不用 Level.getChunk。',
    'The check runs on a worker and may not read a live world.': '检查在工作线程上运行，不能读取活的世界。',
    'A half-changed world is read, and the same request produces a different verdict on every run.':
      '读到改了一半的世界，同一个请求每次运行都会给出不同的裁决。',
    'blueprint.expand(stage) emits section computations → barrier → aggregates/derivers in Kahn order → checks.':
      'blueprint.expand(stage) 依次发出区段计算 → 屏障 → 按 Kahn 序的聚合/派生器 → 检查。',
    'One fixed order and one fixed read closure per stage; ties by admission ascending, specificity descending, declaration order.':
      '每个阶段一个固定顺序、一个固定读闭包；同序时按计费额升序、特异性降序、声明顺序。',
    'Results depend on execution order, the read closure is unknown, and no cache key can be formed.':
      '结果取决于执行顺序，读闭包未知，也构造不出缓存键。',
    'Each SECTION computation runs once per section in region ∩ section and contributes a partial.':
      '每个 SECTION 计算在 region ∩ section 的每个区段运行一次，并贡献一个部分值。',
    'A large structure is split into sections that parallelise and cache independently.':
      '大结构被拆成可并行、可独立缓存的区段。',
    'One walk over the whole structure: no parallelism, no incrementality, no per-section retry.':
      '对整个结构做一次遍历：没有并行、没有增量，也没有按区段重试。',
    'Sort contributions by SectionKey (x, z, y) and combine left-to-right with merge.combine.':
      '按 SectionKey (x, z, y) 排序贡献，并用 merge.combine 从左到右合并。',
    'Cross-section criteria must be composed before any check reads them.': '跨区段的条件必须在任何检查读取之前先合成。',
    'Each section judges alone and the attempt reports mutually contradictory conclusions.':
      '各区段各自判定，同一次尝试会报告互相矛盾的结论。',
    'AGGREGATE computations read folded values once and put the derived key; no world reads, no further contributions.':
      'AGGREGATE 计算读取一次折叠值并写入派生键；不读世界，也不再贡献。',
    'A complex value is computed once and shared by every predicate that needs it.':
      '复杂值只算一次，供所有需要它的谓词共享。',
    'Every predicate re-walks 100 cells and two predicates can compute different values for the same fact.':
      '每个谓词都要重走 100 格，两个谓词可能对同一事实算出不同的值。',
    'focus.take → Found or Missing; only Found calls expectation.test. The attempt folds replies.':
      'focus.take → Found 或 Missing；只有 Found 才调用 expectation.test。整次尝试折叠应答。',
    'Predicates judge one typed subject and never scan the world, so they compose, cache and reuse.':
      '谓词只判定一个类型化主体、从不扫描世界，因此可组合、可缓存、可复用。',
    'Predicates own world reads; no combinator, no read scope, no billing.':
      '谓词自行读世界：没有组合子、没有读取范围，也没有计费。',
    'One ContextDraft per stage boundary, published against the baseRevision it read.':
      '每个阶段边界一份 ContextDraft，并针对它读取到的 baseRevision 发布。',
    'Context values are versioned, and a stage frame is immutable once published.':
      '上下文值带版本，阶段帧一旦发布就不可变。',
    'A slow old computation overwrites a newer result and readers cannot tell which is current.':
      '慢的旧计算会覆盖更新的结果，读者无法判断哪个才是当前的。',
    'Derive ClaimRows from Found subjects and each bay’s mode × scope; one CAS replaces all rows of (owner, siting).':
      '由 Found 的主体与每个舱位的 mode × scope 派生 ClaimRow；一次 CAS 替换 (owner, siting) 的全部行。',
    'Two structures must not both own one cell, and Formed must exist only after Settled.':
      '两个结构不能同时拥有同一格，而 Formed 必须只在 Settled 之后存在。',
    'Two overlapping furnaces both report Formed and the second silently steals the first’s cells.':
      '两个重叠的高炉都会报告 Formed，后者会悄悄抢走前者的格。',

    // pipeline · stages
    'Cheap pass: bounds, anchor, census.': '轻量遍历：包围盒、锚点、普查。',
    'No claims and no instance id. derive() refuses an Admitted result.':
      '没有声明，也没有实例 id。derive() 拒绝 Admitted 结果。',
    'Assert the bays and run the computations.': '断言各舱位并运行计算。',
    'Still no claims. A structure that passes FORM is admissible, not formed.':
      '仍然没有声明。通过 FORM 的结构是可接纳的，而不是已成形。',
    'Judge, then settle claims in one ledger transaction.': '先判定，再在一次账本事务中结算声明。',
    'Formed carries claims, generation, contextRevision and ledgerRevision; only this stage can produce it.':
      'Formed 携带声明、generation、contextRevision 与 ledgerRevision；只有这个阶段能产生它。',

    // pipeline · toggles
    'World mismatch': '世界不匹配',
    'One Check returns MISMATCH → Failed, even beside unreadable cells.':
      '一个 Check 返回 MISMATCH → Failed，即使旁边还有不可读的格。',
    'Refused section': '区段拒读',
    'A section slot is Refused → key absent → reply UNREADABLE.': '某个区段槽为 Refused → 键缺席 → 应答 UNREADABLE。',
    'Budget exhausted': '预算耗尽',
    'The instance budget refuses a unit → Undecided(BUDGET_EXHAUSTED).':
      '实例预算拒绝一个单元 → Undecided(BUDGET_EXHAUSTED)。',
    'Stale publish': '过期的发布',
    'The base revision moved → SC-CTX-0108, no frame written → Undecided(DIRTY_UNSTABLE).':
      'base 版本已移动 → SC-CTX-0108，不写帧 → Undecided(DIRTY_UNSTABLE)。',
    'Claim conflict': '声明冲突',
    'An incumbent holds an EXCLUSIVE cell → Failed(SC-CLM-0002).': '既有者持有 EXCLUSIVE 格 → Failed(SC-CLM-0002)。',

    // pipeline · verdict rules and phase notes
    'Failed ⇔ mismatch ≥ 1 ∨ claimConflict ≥ 1. Refutation outranks ignorance.':
      'Failed ⇔ mismatch ≥ 1 ∨ claimConflict ≥ 1。反驳压过无知。',
    'Undecided ⇔ no mismatch, no conflict, and unreadable ∨ abstain ∨ untested ∨ no judging unit ∨ cancelled ∨ budget refused.':
      'Undecided ⇔ 无 mismatch、无冲突，且 unreadable ∨ abstain ∨ untested ∨ 无判定单元 ∨ cancelled ∨ 预算拒绝。',
    'Formed ⇔ no mismatch, no conflict, no unreadable, no abstain, no untested, and at least one judging unit.':
      'Formed ⇔ 无 mismatch、无冲突、无 unreadable、无 abstain、无 untested，且至少有一个判定单元。',
    'Reason precedence: CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS.':
      '原因优先级：CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS。',
    'Only SETTLE + Settled produces Formed. PROBE and FORM success produce Admitted.':
      '只有 SETTLE + Settled 产生 Formed。PROBE 与 FORM 的成功产生 Admitted。',
    'PROBE reads bounds and the anchor, runs the census, and stops. No section pass, no claims.':
      'PROBE 读取包围盒与锚点，执行普查后停止。没有区段遍历，也没有声明。',
    'FORM runs sections, the fold, aggregates and the bay checks, then publishes the FORM frame.':
      'FORM 运行区段、折叠、聚合与舱位检查，然后发布 FORM 帧。',
    'SETTLE re-runs the plan against the snapshot, folds, publishes, then settles claims once.':
      'SETTLE 针对快照重跑计划、折叠、发布，然后一次结算声明。',

    // pipeline · verdict details
    'An incumbent holds an EXCLUSIVE cell; the ClaimConflict verdict is returned with the incumbent rows.':
      '既有者持有 EXCLUSIVE 格；返回 ClaimConflict 裁决并附带既有行。',
    'One Check returned MISMATCH. Refutation outranks ignorance, even beside unreadable cells.':
      '一个 Check 返回 MISMATCH。反驳压过无知，即使旁边有不可读的格。',
    'The publish base revision moved; no frame was written. The attempt stopped before a verdict.':
      '发布基准版本已移动，没有写入任何帧。尝试在产生裁决之前停止。',
    'The instance budget refused a unit. A re-read is licensed; a repair is not.':
      '实例预算拒绝了一个单元。这授权重新读取，而不是修复。',
    'A refused section made a key absent; the predicate was never called, so nothing was learned.':
      '某个区段拒读导致键缺席；谓词从未被调用，因此什么也没学到。',
    'Claims settled in one CAS: 294 rows, generation 1, contextRevision 7, ledgerRevision 4.':
      '声明在一次 CAS 中结算：294 行，generation 1，contextRevision 7，ledgerRevision 4。',
    'PROBE passed: bounds, anchor and census. No claims, no instance id; derive() refuses it.':
      'PROBE 通过：包围盒、锚点与普查。没有声明、没有实例 id；derive() 会拒绝它。',
    'FORM passed: bays asserted, computations folded, checks judged. Still no claims; this is not a structure.':
      'FORM 通过：舱位已断言、计算已折叠、检查已判定。仍然没有声明；这不是一个结构。',
    'reason only': '仅有原因',

    // context · keys
    'Three section contributions fold left-to-right. A refused section makes the whole key absent; there is no short sum.':
      '三个区段贡献从左到右折叠。任一区段拒读都会让整个键缺席；不存在短的和。',
    'Single writer: an aggregate reads the folded volume and puts 4 × volume once, after the barrier.':
      '单写者：聚合在屏障之后读取折叠后的 volume，并写入一次 4 × volume。',
    'whenAbsent is honoured only when every section was readable and nothing contributed; a refusal never becomes 0.':
      'whenAbsent 只在所有区段可读且无任何贡献时才生效；拒读永远不会变成 0。',

    // context · absence paths
    'One section is Refused. draft.refuse(key) is called; the key is absent at the barrier; require() returns Missing; the predicate is never invoked and reports UNREADABLE.':
      '有一个区段被拒读。调用了 draft.refuse(key)；键在屏障处缺席；require() 返回 Missing；谓词从未被调用，报告 UNREADABLE。',
    'Every section was readable and no contribution was produced: draft.declined(key). Only here does whenAbsent apply.':
      '所有区段都可读，且没有产生任何贡献：draft.declined(key)。只有这里 whenAbsent 才生效。',
    'Same readable/declined path, but a key with whenAbsent == null stays absent. Absence is a fact, not a zero.':
      '同样的可读/拒绝路径，但 whenAbsent == null 的键保持缺席。缺席是一个事实，而不是零。',
    'publish() with a baseRevision the store has moved past writes nothing. The newer frame stays intact.':
      '当 store 已经越过 baseRevision 时，publish() 什么也不写。更新的帧保持完好。',
    'A reader at tier t sees the newest frame at or below t. A PROBE reader returns null for a FORM value; it never looks above its tier.':
      '处于层级 t 的读者看到 t 及以下最新的帧。PROBE 读者对 FORM 值返回 null；它从不越过自己的层级向上看。',

    // context · fold rules
    'Contributions are sorted by SectionKey (x, z, y) and combined left-to-right with merge.combine.':
      '贡献按 SectionKey (x, z, y) 排序，并用 merge.combine 从左到右合并。',
    'The fold starts at the first present contribution. There is no identity element and no zero seed.':
      '折叠从第一个在场的贡献开始。没有单位元，也没有零种子。',
    'Any refused section in the region makes the key absent; a short sum is unrepresentable.':
      '区域内任一区段拒读都会让键缺席；短的和无法表示。',
    'Parallel section passes are allowed: the fold order is fixed, so the value is a function of the contribution set, not the schedule.':
      '允许并行的区段遍历：折叠顺序固定，因此值只取决于贡献集合，而与时序无关。',
    'declared value': '声明值',
    'no declared fallback': '无声明回退',

    // claims · rules
    'ClaimRow(cell, mode, owner, siting, bay) is the only row shape; the ledger has no second opinion.':
      'ClaimRow(cell, mode, owner, siting, bay) 是唯一的行形状；账本没有第二种说法。',
    'ClaimMatrix.conflicts(a, b) is the only conflict predicate. Rows of one (owner, siting) are excluded there.':
      'ClaimMatrix.conflicts(a, b) 是唯一的冲突谓词。同一 (owner, siting) 的行会在其中被排除。',
    'settle replaces every row of (owner, siting) in one CAS. A partial replacement does not exist.':
      'settle 在一次 CAS 中替换 (owner, siting) 的所有行。不存在部分替换。',
    'Occupied owns non-air bound footprints; Owned owns bay.cells; Declared owns an explicit CuboidSet; Audit owns nothing and reads anywhere.':
      'Occupied 拥有非空绑定足迹；Owned 拥有 bay.cells；Declared 拥有显式 CuboidSet；Audit 不拥有任何格，可在任意处读取。',
    'Uncovered blueprint bounds are SC-CLM-0104 (WARNING); a non-Audit read outside cells ∪ footprint is SC-BAY-0108 at SETTLE.':
      '未被覆盖的蓝图包围盒是 SC-CLM-0104（WARNING）；非 Audit 读取越出 cells ∪ footprint 在 SETTLE 是 SC-BAY-0108。',
    'Conflicts with any other mode on the same cell; the second settle fails.':
      '与同一格上的任何其他方式冲突；后一次结算失败。',
    'Never conflicts with SHARED; two furnaces can both hold the same hatch cell.':
      '永不与 SHARED 冲突；两座高炉可以同时持有同一个舱口格。',
    'Reads like SHARED and blocks EXCLUSIVE until the host’s reservation expires.':
      '读起来像 SHARED，并在宿主预留到期前挡住 EXCLUSIVE。',
    'Owns bay.cells, including empty ones. A cavity must use this.':
      '拥有 bay.cells，包括空格。腔体必须使用它。',
    'Owns the union of non-air bound footprints. The pipe corner asserts 9 cells and owns 4.':
      '拥有非空绑定足迹的并集。管道转角断言 9 格，拥有 4 格。',
    'The attempt captured revision 4. A settle carrying 4 against a ledger at 5 is RevisionMoved, retried once inside the call. A second movement is Undecided(LEDGER_MOVED).':
      '本次尝试捕获的是版本 4。带着 4 去结算一个已经到 5 的账本即为 RevisionMoved，会在调用内重试一次。第二次移动则是 Undecided(LEDGER_MOVED)。',

    // authoring · runtime roles
    'Blueprint bounds; half-open min/max; it is not automatically any bay.':
      '蓝图包围盒；半开的 min/max；不会自动成为任何舱位。',
    'Lowered to one CUBIC bay per face box, ids #box@x,y,z:sx,sy,sz; not one id spanning six boxes.':
      '每个面盒子降为一个 CUBIC 舱位，id 为 #box@x,y,z:sx,sy,sz；而不是一个横跨六个盒子的 id。',
    'The cells a bay asserts and maybe owns; the cells of a named bay are carved out of the shell.':
      '舱位断言、并可能拥有的格；具名舱位的格是从壳体里挖出来的。',
    'How many cells one MATCH asserts: CUBIC every cell, SUBJECT footprint equality, PRESENCE none.':
      '一次 MATCH 断言多少格：CUBIC 每一格，SUBJECT 足迹相等，PRESENCE 零格。',
    'Exclusivity and owned-cell set; SUBJECT + Owned is SC-CHK-0106.':
      '互斥性与拥有格集合；SUBJECT + Owned 是 SC-CHK-0106。',
    'A bay-level predicate becomes a Check whose focus is the bay or each cell.':
      '舱位级谓词会变成 Check，其焦点是该舱位或每个格。',
    'Takes a typed subject; absence is Outcome.Missing with an SC-FOC code, never null.':
      '取得一个类型化主体；缺席是带 SC-FOC 代码的 Outcome.Missing，绝不是 null。',
    'Judges the subject; the check constructor pins it to the focus subject type (SC-CHK-0101).':
      '判定该主体；检查构造函数把它钉在焦点的主体类型上（SC-CHK-0101）。',
    'Which stage plan the check belongs to: DESIGN, PROBE, FORM, SETTLE, STALE.':
      '检查属于哪个阶段计划：DESIGN、PROBE、FORM、SETTLE、STALE。',
    'Reads a region, returns an Outcome<T>, writes one ContextKey; SECTION or AGGREGATE.':
      '读取一个区域，返回 Outcome<T>，写入一个 ContextKey；SECTION 或 AGGREGATE。',
    'The single key this computation writes; two outputs means two computations.':
      '该计算写入的唯一键；两个输出就意味着两个计算。',
    'Kahn dependency edges; a cycle is SC-CHK-0102.': 'Kahn 依赖边；成环即 SC-CHK-0102。',
    'The cells the computation reads; sections intersect it, and the read closure is cached.':
      '计算读取的格；各区段与之相交，读闭包会被缓存。',
    'ATTEMPT dropped with the attempt, OUTCOME in MatchOutcome, CONTEXT in the stage frame.':
      'ATTEMPT 随尝试丢弃，OUTCOME 进入 MatchOutcome，CONTEXT 进入阶段帧。',
    'Non-null allows per-section contributions and one left fold; null is a single writer.':
      '非空允许逐区段贡献与一次左折叠；空表示单写者。',
    '(id, type, Retention, merge, whenAbsent); equality is (id, type).':
      '(id, type, Retention, merge, whenAbsent)；相等性判定用 (id, type)。',
    'Read at the definition origin to obtain a heading; the resolver is not implemented.':
      '在定义原点处读取以获得朝向；解析器尚未实现。',
    'Maps the authored front onto the anchor heading; still a spec, no resolver code.':
      '把编写的正面映射到锚点朝向；仍然是规范，没有解析器代码。',

    // authoring · expect rules
    'One tag per object': '每个对象一个标签',
    'Two tags is not an implicit AND. Write {"allOf": [...]} if both must hold.':
      '两个标签不是隐式 AND。若两者都必须成立，请写 {"allOf": [...]}。',
    'anyOf: one of two blocks': 'anyOf：两种方块之一',
    'Both children test OccupantSnapshot, so they may share one combinator.':
      '两个子项都测试 OccupantSnapshot，因此可以共用一个组合子。',
    'allOf: block plus trait': 'allOf：方块加特性',
    'Block and trait both read OccupantSnapshot; state reads T and cannot join this group.':
      'block 与 trait 都读取 OccupantSnapshot；state 读取 T，不能加入这一组。',
    'not: anything but air': 'not：除空气之外的一切',
    'Negation preserves ABSTAIN and UNREADABLE; it only swaps MATCH and MISMATCH.':
      '取反保留 ABSTAIN 与 UNREADABLE；它只交换 MATCH 与 MISMATCH。',
    'Empty combinator': '空组合子',
    'The array cannot be empty: a vacuous all-true / all-false hides the authoring error.':
      '数组不能为空：空洞的全真/全假会掩盖编写错误。',
    'Different subjects in one anyOf': '同一个 anyOf 里混用主体',
    'state is judged against T, not OccupantSnapshot. Split it into a second Check with its own focus.':
      'state 是针对 T 判定的，而不是 OccupantSnapshot。请拆成第二个 Check，并配它自己的焦点。',

    // authoring · graph node subtitles
    'min / max · half-open': 'min / max · 半开',
    'id · bounds · bays · anchors': 'id · 边界 · 舱位 · 锚点',
    'cells · coverage · mode': '格 · 覆盖 · 方式',
    'takes the subject': '取得主体',
    'same T on both sides': '两侧同一个 T',
    'returns Reply': '返回 Reply',
    'one output key': '一个输出键',
    'origin + placement': '原点 + 摆放',
    'Cuboid of asserted cells': '断言格的长方体',

    // predicates · focus and expectation labels
    Occupant: '占用体',
    Bay: '舱位',
    Context: '上下文',
    Block: '方块',
    Air: '空气',
    Trait: '特性',
    State: '状态',
    'The common case: one cell, one occupant, one predicate.': '最常见的情形：一格、一个占用体、一个谓词。',
    'A door or a multi-cell bay arrives as one subject; expectation never re-reads the world.':
      '门或多格舱位作为单一主体到达；期望从不重新读取世界。',
    'Exactly ctx.context().require(key); absence is Missing, never a zero.':
      '正是 ctx.context().require(key)；缺席是 Missing，绝不是零。',
    'Compares the occupant descriptor; a block expectation never looks at a bay or a Long.':
      '比较占用体描述符；方块期望从不查看舱位或 Long。',
    'Air is a readable occupant; a refused section is never air.': '空气是可读的占用体；拒读的区段永远不是空气。',
    'A priced capability test; the meta requires the trait so the plan can charge for it.':
      '带计价的能力测试；meta 要求该特性，计划才能据此计费。',
    'Generic over T: the same interface serves an occupant property, a bay property and a Long.':
      '对 T 泛型：同一接口可服务占用体属性、舱位属性与 Long。',

    // predicates · reply ladder
    'A subject or key is missing; nothing was judged.': '主体或键缺席；什么都没有判定。',
    'The subject was read and violates the expectation; one finding.': '主体已读取且违反期望；一条诊断。',
    'The predicate declines to judge; not a pass.': '谓词拒绝判定；不是通过。',
    'The subject satisfies the expectation; bindings only here.': '主体满足期望；绑定只出现在这里。',
    'finding from the expectation': '来自期望的诊断',
    'no finding': '无诊断',

    // predicates · combinators
    'all children MATCH': '所有子项 MATCH',
    'any child MISMATCH is decisive': '任一子项 MISMATCH 即为决定性',
    'UNREADABLE if any, else ABSTAIN': '有则 UNREADABLE，否则 ABSTAIN',
    'any child MATCH': '任一子项 MATCH',
    'all children MISMATCH': '所有子项 MISMATCH',
    'child MISMATCH': '子项 MISMATCH',
    'child MATCH': '子项 MATCH',
    'UNREADABLE and ABSTAIN pass through': 'UNREADABLE 与 ABSTAIN 原样传递',
    'Check<T> constructor: focus subject and expectation subject differ. The check is not constructed.':
      'Check<T> 构造函数：焦点主体与期望主体不一致。该检查不会被构造。',

    // results · arms
    'PROBE / FORM passed.': 'PROBE / FORM 通过。',
    'The fold is MATCH at PROBE or FORM. No claims are minted, there is no instance id, and derive() refuses the result.':
      '折叠在 PROBE 或 FORM 为 MATCH。不产生声明，没有实例 id，derive() 拒绝该结果。',
    'PROBE or FORM; never SETTLE.': 'PROBE 或 FORM；绝不是 SETTLE。',
    'The placement the attempt used.': '本次尝试使用的摆放。',
    'What this attempt produced: the view it read and the values it published.': '本次尝试的产物：它读了哪个视图、发布了哪些值。',
    'Child handle; AutoCloseable.': '子句柄；AutoCloseable。',
    'Warnings and notes, never a blocking error.': '警告与提示，绝不是阻断性错误。',
    'SETTLE passed and claims settled.': 'SETTLE 通过，声明已结算。',
    'The fold is MATCH at SETTLE and the ledger transaction returned Settled. This is the only arm that is a structure.':
      '折叠在 SETTLE 为 MATCH，且账本事务返回 Settled。这是唯一构成结构的分支。',
    'The instance the host registers.': '宿主注册的实例。',
    'Frozen with the claims.': '随声明一起冻结。',
    'The outcome plus the context the attempt published.': '产物，以及本次尝试发布的上下文。',
    'Rows and the revision they settled at.': '行，以及它们结算时的版本。',
    'Increments on every emitted Formed, including a re-emission.': '每产生一次 Formed 递增一次，重新产生也算。',
    'The SETTLE frame revision.': 'SETTLE 帧的版本。',
    'The revision a later replacement is measured against.': '后续替换所对照的版本。',
    'AutoCloseable; closing releases child handles.': 'AutoCloseable；关闭时释放子句柄。',
    'Non-blocking findings only.': '仅非阻断性诊断。',
    'The world really does not satisfy.': '世界确实不满足。',
    'At least one MISMATCH, or a ClaimConflict at SETTLE. A repair may change the world; this is not a soft failure.':
      '至少一个 MISMATCH，或在 SETTLE 出现 ClaimConflict。修复可以改动世界；这不是软失败。',
    'One or more; codes come from the failing unit.': '一条或多条；代码来自失败的那个单元。',
    'We stopped, or learned nothing.': '我们停下了，或者什么也没学到。',
    'No mismatch and no conflict, but unreadable / abstain / untested / no judging unit / cancelled / budget refused / ledger moved. A re-read is licensed.':
      '没有不匹配也没有冲突，但存在 unreadable / abstain / untested / 无判定单元 / cancelled / 预算拒绝 / 账本移动。允许重新读取。',
    'The findings that explain the stop, when there are any.': '解释这次停止的诊断（如果有）。',
    'What was produced before the stop, if anything.': '停止前已产出的东西（如果有）。',
    'Present for LEDGER_MOVED and some stop reasons.': '在 LEDGER_MOVED 及部分停止原因下存在。',
    'Nothing executed.': '什么都没执行。',
    'The request never ran: capacity, a closed lease or a policy refusal. Terminal with a retry token; only AUTO re-submits automatically.':
      '请求从未运行：容量、租约已关闭或策略拒绝。终态，附重试 token；只有 AUTO 会自动重投。',
    'The only way back in; there is no partial outcome.': '唯一的返回路径；不存在部分产物。',
    'Why no unit was dispatched.': '为什么没有派发任何单元。',

    // results · finding components
    'Addressability inside one bundle, for related. Re-minted when a cached finding is reused.':
      '在一个诊断包内可寻址，供 related 使用。缓存诊断被复用时重新编号。',
    'Stable identity: family plus number. A well-formed code with no registry entry still orders and renders.':
      '稳定身份：族加编号。格式正确但未登记的代码仍可排序与渲染。',
    'Rank 0..2. The first sort key, because a reader triages by how much a finding counts.':
      '等级 0..2。第一排序键，因为读者按诊断的分量分流。',
    'Prose. Excluded from dedup so a reword or a translation never splits one finding into two.':
      '散文。不参与去重，这样改写或翻译都不会把一条诊断拆成两条。',
    'Each Label is (role, locus, message). The constructor sorts them; the primary locus is the fourth sort key.':
      '每个 Label 是 (role, locus, message)。构造函数会排序；主位置是第四排序键。',
    'Values with no cell of their own. Included in dedup: two findings differing in a note are different facts.':
      '没有自己格位的事实。参与去重：note 不同就是不同的事实。',
    'A hint, not a plan. Only the top applicability rung may auto-apply; U10 owns the edit.':
      '提示而非计划。只有最高适用等级可以自动套用；编辑由 U10 负责。',
    'Other findings in the same bundle that share a cause. Excluded from dedup.':
      '同一诊断包中共享成因的其他诊断。不参与去重。',
    'When the failure became knowable. The second sort key: DESIGN before PROBE before FORM before SETTLE.':
      '失败何时变得可知。第二排序键：DESIGN 先于 PROBE、FORM、SETTLE。',

    // results · sort keys
    'family ordinal, then number': '族序号，然后编号',
    'the attempt append index; unique, last resort': '尝试内的追加下标；唯一，最后手段',

    // results · demo findings
    'two tags in one expect object': '一个 expect 对象里有两个标签',
    'incumbent owns an EXCLUSIVE cell': '既有者拥有 EXCLUSIVE 格',
    'SUBJECT coverage cannot own cells': 'SUBJECT 覆盖方式不能拥有格',
    'decision cells do not cover the bay': '判定格未覆盖该舱位',
    'refused section makes the key absent': '区段拒读导致键缺席',
    'discard budget exhausted': '丢弃预算耗尽',
    'the campfire is not lit': '营火没有点燃',
    'stale base revision': '过期的 base 版本',
    'bay "mouth" declares SUBJECT + Owned': '舱位 "mouth" 声明了 SUBJECT + Owned',
    'change ClaimScope to Occupied': '把 ClaimScope 改为 Occupied',
    'Dedup is value equality on (severity, stage, code, labels as [(role, locus, source)], notes as [message]). Message, label prose, suggestion and related are excluded.':
      '去重是 (severity, stage, code, labels 按 [(role, locus, source)], notes 按 [message]) 的值相等。Message、label 散文、suggestion 与 related 不参与。',
    'After dedup the bundle sorts once, then applies maxPerCode and maxTotal, so a cap truncates the documented order, never a hash order.':
      '去重后诊断包只排序一次，再应用 maxPerCode 与 maxTotal，因此封顶截断的是文档化顺序，绝不是哈希顺序。',

    // glossary · groups
    Model: '模型',
    Space: '空间',
    Predicates: '谓词',
    Claims: '声明',
    Results: '结果',

    // glossary · WorldView
    'The read-only surface: slot(SectionKey), occupant(cell), occupants(box, filter), nested(cell), contentDigest(section).':
      '只读面：slot(SectionKey)、occupant(cell)、occupants(box, filter)、nested(cell)、contentDigest(section)。',
    'Predicates and computations never own a world handle; one read face keeps capture, read scope and caching honest.':
      '谓词与计算都不持有世界句柄；单一读面让捕获、读取范围与缓存保持一致。',
    'ClaimScope.Occupied never needs a second lookup; the ref already knows which cells the subject covers.':
      'ClaimScope.Occupied 不需要第二次查询；引用本身就知道主体覆盖了哪些格。',
    'A declared box inflated by MAX_ENTITY_SPAN = 3 cells.': '声明盒子按 MAX_ENTITY_SPAN = 3 格外扩后的区域。',
    'An entity can straddle cells; the window is the smallest region that can witness a join, a leave or an outward move.':
      '实体可以跨越格；该窗口是能见证加入、离开或外移的最小区域。',
    'Both halves of a door delivered as one subject, with one footprint.':
      '门的两半作为一个主体交付，共享一个足迹。',
    'A door is one assertion, not two independent cells; the expectation must not re-read the world to find the second half.':
      '门是一个断言，而不是两个独立格；期望不得为了找另一半而重读世界。',
    'One ledger transaction has exactly three answers; only Settled can let the caller report Formed.':
      '一次账本事务只有三种答案；只有 Settled 才能让调用方报告 Formed。',

    // glossary · MatchOutcome, Blueprint, Bay, Requirement, Coverage, scopes
    'What one attempt produced, including the context values at its retention.':
      '一次尝试的可读事实，包括按其保留策略留下的上下文值。',
    'Admitted and Formed carry the outcome, so derive() and remedies do not re-walk the structure to recover what was already known.':
      'Admitted 与 Formed 都携带事实，derive() 与补救无需重走结构来重新获得已知信息。',
    '(id, bounds, bays, anchors, derivations) — the complete, immutable declaration of one structure.':
      '(id, bounds, bays, anchors, derivations)——一个结构的完整、不可变声明。',
    'One object graph replaces four separate readings of the document; every plan, cache key and finding points at it.':
      '一张对象图取代对文档的四次独立读取；每个计划、缓存键与诊断都指向它。',
    'A cuboid plus Coverage, ClaimMode, ClaimScope and a Requirement; it can be judged, owned and staged on its own.':
      '一个长方体加上 Coverage、ClaimMode、ClaimScope 与 Requirement；可被独立判定、拥有并分阶段。',
    'The shell, the door and the cavity have different subjects, stages and ownership. One giant check cannot express all three.':
      '壳体、门与腔体的主体、阶段与归属各不相同。一个巨型检查无法表达这三者。',
    'The list of Checks and Computations that belong to one bay.':
      '属于一个舱位的 Check 与 Computation 列表。',
    'A bay exists to assert something; an empty Requirement would be a claim with no evidence.':
      '舱位存在的意义就是断言；空的 Requirement 就是没有证据的声明。',
    'CUBIC (every cell decided), SUBJECT (cells equal bound footprints), PRESENCE (no cell asserted).':
      'CUBIC（每格都被判定）、SUBJECT（格等于绑定足迹）、PRESENCE（不断言任何格）。',
    'It prevents “one cell passed, so the whole bay passed” from being expressible.':
      '它让“一格通过，所以整个舱位通过”这种说法无法被表达。',
    'Owned (bay.cells), Occupied (non-air bound footprints), Declared (explicit set), Audit (none).':
      'Owned（bay.cells）、Occupied（非空绑定足迹）、Declared（显式集合）、Audit（无）。',
    'Read cells are not owned cells. The cavity needs Owned to protect 100 empty cells; a pipe corner uses Occupied to own 4 of 8.':
      '读过的格不等于拥有的格。腔体需要 Owned 来保护 100 个空格；管道转角用 Occupied 拥有 8 格中的 4 格。',
    'Two structures can be allowed to share a hatch (SHARED) or forbidden to share one shell cell (EXCLUSIVE) without a second conflict rule.':
      '两个结构可以允许共用舱口（SHARED），也可以禁止共用一个壳体格（EXCLUSIVE），而无需第二套冲突规则。',

    // glossary · predicates
    'Takes a typed subject from the world or a context key; returns Found<T> or Missing.':
      '从世界或上下文键取得一个类型化主体；返回 Found<T> 或 Missing。',
    'Predicates must not scan the world. A focus owns the read, the scope and the absence code.':
      '谓词不得扫描世界。焦点负责读取、范围与缺席代码。',
    'test(T) → Reply, plus meta and reads. It judges one typed subject and produces no state.':
      'test(T) → Reply，另有 meta 与 reads。它只判定一个类型化主体，不产生状态。',
    'A predicate that writes context cannot be reused, cached or combined; judgement stays a pure four-state answer.':
      '会写上下文的谓词无法复用、缓存或组合；判定保持纯四态应答。',
    'Focus<T> + Expectation<T> + stage + admission, attached to exactly one bay.':
      'Focus<T> + Expectation<T> + 阶段 + 计费额，且只挂在一个舱位上。',
    'It is the only place focus and expectation meet, so the compiler can pin both to the same T (SC-CHK-0101).':
      '它是焦点与期望唯一相遇的地方，编译器因此能把两者钉在同一个 T 上（SC-CHK-0101）。',
    'MATCH, MISMATCH, UNREADABLE or ABSTAIN. Bindings exist only on MATCH.':
      'MATCH、MISMATCH、UNREADABLE 或 ABSTAIN。绑定只存在于 MATCH。',
    'Four states keep “I did not look” (UNREADABLE) and “I decline” (ABSTAIN) from silently becoming a pass.':
      '四种状态让“我没看”（UNREADABLE）与“我拒绝”（ABSTAIN）不会悄悄变成通过。',
    'Absence is explicit and carries a Locus. A missing subject is never null, silence or test(null).':
      '缺席是显式的，并携带一个 Locus。缺失的主体绝不是 null、沉默或 test(null)。',
    'The cells and context keys a unit reads.': '一个单元读取的格与上下文键。',
    'It forms the cache closure, the dirty mark and the read-closure gate; a unit that reads outside its bay is SC-BAY-0108.':
      '它构成缓存闭包、脏标记与读闭包门禁；读取越出自己舱位的单元是 SC-BAY-0108。',
    'A DESIGN-time floor: decision cells + read cells + sections touched, rounded up. An authored value may only raise it.':
      'DESIGN 阶段的下限：判定格 + 读取格 + 触及区段，向上取整。作者填的值只能抬高它。',
    'Billing must be independent of cache and schedule, otherwise a warm run could turn BUDGET_EXHAUSTED into Formed.':
      '计费必须与缓存和时序无关，否则热运行会把 BUDGET_EXHAUSTED 变成 Formed。',

    // glossary · lattice, space
    'An integer cell coordinate in the definition space.': '定义空间中的整数格坐标。',
    'Lattice space allows exact half-open cuboids and a finite orientation group; floating point would not.':
      '格点空间允许精确的半开长方体和有限的朝向群；浮点数做不到。',
    'One long packing (x:26, z:26, y:12).': '打包进一个 long（x:26, z:26, y:12）。',
    'A cell must be a map key and a claim identity without allocating an object per lookup.':
      '格必须能作为映射键与声明身份，而每次查询不分配对象。',
    'A half-open box: min inclusive, max exclusive. [0,0,0,3,3,3] is 27 cells.':
      '半开盒子：min 含、max 不含。[0,0,0,3,3,3] 是 27 格。',
    'Half-open removes the off-by-one between extent and cell count; volume is exact, not max − min + 1.':
      '半开消除了范围与格数之间的差一错误；体积是精确的，而不是 max − min + 1。',
    'A normal form: disjoint, sorted, overlapping boxes merged; touching boxes stay separate.':
      '一种规范形式：不相交、有序，重叠盒合并；仅相触的盒保持分开。',
    'Regions need algebra without enumerating cells. Semantic equality is two-way containsAll, not equals.':
      '区域需要不枚举格就能做的代数。语义相等是双向 containsAll，而不是 equals。',
    'One of exactly 48 signed axis permutations; Turn × Tilt × parity, ordinal 0..47.':
      '恰好 48 种带符号轴置换之一；Turn × Tilt × 奇偶，序号 0..47。',
    'A lattice isometry is either a rotation or a reflection; 48 is the whole group, and a volume never changes.':
      '格等距要么是旋转要么是反射；48 就是整个群，体积永不改变。',
    'origin + placement, where placement is an orientation plus an integer offset.':
      '原点 + placement，而 placement 是一个朝向加整数偏移。',
    'Local↔world must be affine and re-sitable: a local delta is oriented before it is added.':
      '本地↔世界必须是仿射且可重新摆放：本地增量先定向，再相加。',
    'role + property + value, read at the definition origin to obtain a heading.':
      'role + property + value，在定义原点读取以得到朝向。',
    'Orientation becomes a function of the anchor instead of a search with tie-breaks: candidate.apply(authoredFront) == anchorHeading.':
      '朝向变成锚点的函数，而不是带平局的搜索：candidate.apply(authoredFront) == anchorHeading。',

    // glossary · context
    'A section coordinate ordered (x, z, y).': '按 (x, z, y) 排序的区段坐标。',
    'The fold order must be fixed and independent of thread scheduling; (x,z,y) is that documented order.':
      '折叠顺序必须固定且与线程调度无关；(x,z,y) 就是那个文档化的顺序。',
    'combine(left, right); no identity element. sum, union, min, max, and, or.':
      'combine(left, right)；没有单位元。sum、union、min、max、and、or。',
    'A short sum is worse than no sum. Without an identity, a missing contribution cannot masquerade as 0.':
      '短的和比没有和更糟。没有单位元，缺失的贡献就无法冒充 0。',
    'Sort contributions by SectionKey and combine left-to-right; the value exists once, after the barrier.':
      '按 SectionKey 排序贡献并从左到右合并；值只在屏障之后存在一次。',
    'Every predicate reads the same folded value, so two predicates cannot disagree about the same fact.':
      '每个谓词读到同一个折叠值，两个谓词就不会对同一事实产生分歧。',
    'A declared fallback honoured only when every section was readable and nothing contributed.':
      '声明的回退值，只在所有区段可读且无任何贡献时生效。',
    'It separates “the region was empty” from “a section was refused”; the second must stay Missing.':
      '它把“区域是空的”和“有区段被拒读”分开；后者必须保持 Missing。',
    'Shared values need a type, a lifetime and a version. There is no second key type and no PartialKey.':
      '共享值需要类型、生命周期与版本。不存在第二种键类型，也没有 PartialKey。',
    'ATTEMPT (dropped), OUTCOME (in MatchOutcome), CONTEXT (published into the stage frame).':
      'ATTEMPT（丢弃）、OUTCOME（进入 MatchOutcome）、CONTEXT（发布进阶段帧）。',
    'BLUEPRINT, PROBE, FORM, SETTLE. A reader at t sees frames at or below t.':
      'BLUEPRINT、PROBE、FORM、SETTLE。处于 t 的读者只能看到 t 及以下的帧。',
    'A PROBE reader must not consume a FORM value; tiers make that a null, not a race.':
      'PROBE 读者不得消费 FORM 值；层级把这件事变成 null，而不是竞态。',
    'The revision of the newest published frame for one instance.': '某个实例最新已发布帧的版本。',
    'A publish against a moved base is refused (SC-CTX-0108) instead of overwriting a newer value.':
      '针对已移动基线的发布会遭到拒绝（SC-CTX-0108），而不是覆盖更新的值。',

    // glossary · execution
    'Turns a blueprint and a stage into an ordered plan: section units → barrier → aggregates/derivers → checks.':
      '把蓝图与阶段变成有序计划：区段单元 → 屏障 → 聚合/派生 → 检查。',
    'One documented order makes the result deterministic and the cache key meaningful.':
      '一种文档化顺序让结果确定，也让缓存键有意义。',
    'One server-thread call freezes the read closure; the lease hands out child handles.':
      '一次服务端线程调用冻结读闭包；租约分发子句柄。',
    'The boundary between SECTION contributions and AGGREGATE reads.':
      'SECTION 贡献与 AGGREGATE 读取之间的边界。',
    'A cross-section value must be complete before a check reads it; no check runs before its stage’s barrier.':
      '跨区段的值必须在检查读取之前完整；任何检查都不早于其阶段的屏障。',
    'A unit that read a changed cell is dropped, not patched. A re-run is a new attempt over a fresh capture.':
      '读到已变更格的单元会被丢弃，而不是打补丁。重跑是在新捕获之上的新尝试。',

    // glossary · claims, results
    '(cell, mode, owner, siting, bay) — one cell, one owner, one bay.':
      '(cell, mode, owner, siting, bay)——一格、一个所有者、一个舱位。',
    'A row is the smallest unit a ledger can compare; per-bay granularity would hide a single stolen cell.':
      '行是账本能比较的最小单元；按舱位粒度会掩盖被偷走的一格。',
    'The only conflict predicate: conflicts(a, b). Rows of one (owner, siting) are excluded.':
      '唯一的冲突谓词：conflicts(a, b)。同一 (owner, siting) 的行被排除。',
    'Replace every row of (owner, siting) in one CAS; verdict Settled, ClaimConflict or RevisionMoved.':
      '在一次 CAS 中替换 (owner, siting) 的所有行；裁决为 Settled、ClaimConflict 或 RevisionMoved。',
    'Formed exists only after Settled. A partial replacement would leave a structure half-owning its cells.':
      'Formed 只在 Settled 之后存在。部分替换会让结构半拥有它的格。',
    '“The world is wrong” and “I could not tell” license different actions: a repair versus a re-read.':
      '“世界是错的”与“我说不出来”授权不同动作：修复，还是重新读取。',
    'The SETTLE result that carries an InstanceId, ClaimSet, generation, contextRevision and ledgerRevision.':
      '携带 InstanceId、ClaimSet、generation、contextRevision 与 ledgerRevision 的 SETTLE 结果。',
    'A 9-component value: id, code, severity, message, labels, notes, suggestion, related, stage.':
      '一个九组件的值：id、code、severity、message、labels、notes、suggestion、related、stage。',
    'Ordered by severity → stage → code → primary locus → insertion, deduplicated, capped.':
      '按 severity → stage → code → 主位置 → insertion 排序，去重并封顶。',
    'A report whose errors are scattered through world coordinates is unreadable; caps must truncate the documented order.':
      '错误散落在世界坐标里的报告无法阅读；封顶必须截断文档化顺序。',
    'DEFINITION, LOCAL, WORLD or KEY; a KEY locus carries (InstanceId, key id) and no cells.':
      'DEFINITION、LOCAL、WORLD 或 KEY；KEY 位置携带 (InstanceId, key id)，不含格。',
    'A finding must point at a definition span, a world cell or a context key with one address space.':
      '诊断必须能在同一个地址空间里指向定义区段、世界格或上下文键。',

    // space · siting rules
    'The anchor cell is the definition origin. It is read once, before any orientation is chosen, so there is no circularity.':
      '锚点格就是定义原点。它在选择任何朝向之前只读取一次，因此不存在循环依赖。',
    'A candidate that does not satisfy the equation is not a candidate. There is no tie-break: orientation is a function of the anchor.':
      '不满足等式的候选就不是候选。没有平局裁决：朝向是锚点的函数。',
    'For one authored front and one anchor heading, exactly 48 / 6 = 8 orientations satisfy the equation. The other 40 are rejected.':
      '对一个编写的正面和一个锚点朝向，恰好 48 / 6 = 8 种朝向满足等式。其余 40 种被拒绝。',
    'A blueprint with no anchor takes a Siting directly — commands and tools bypass the resolver.':
      '没有锚点的蓝图直接接收一个 Siting——命令与工具绕过解析器。',

    // explain layer · model
    'The compiled structure — id, bounds, bays, anchors and derivations — that the kernel actually runs.':
      '内核真正运行的已编译结构：id、bounds、bays、anchors 与 derivations。',
    'The document is an authoring form; the blueprint is the object graph, and only one of the two needs to exist at runtime.':
      '文档是编写形态，蓝图是对象图；运行时只需要两者之一存在。',
    'blueprint.expand(stage) emits section computations → barrier → aggregates/derivers in Kahn order → checks; a cycle is SC-CHK-0102.':
      'blueprint.expand(stage) 依次排出：区段计算 → 屏障 → 按 Kahn 序的聚合/派生 → 检查；成环即 SC-CHK-0102。',
    'The smallest unit that can be judged, owned and staged: cells, Coverage, ClaimMode, ClaimScope and a Requirement.':
      '能够被独立判定、拥有和分阶段的最小单元：cells、Coverage、ClaimMode、ClaimScope 与 Requirement。',
    'One check cannot be the whole structure; a bay is the boundary where judgement, ownership and stage agree.':
      '单个检查不可能覆盖整个结构；舱位是判定、归属与阶段三者一致的边界。',
    'A check belongs to exactly one bay (SC-CHK-0107); bay.cells is a half-open cuboid or a canonical box set.':
      '一个检查只属于一个舱位（SC-CHK-0107）；bay.cells 是半开长方体或规范盒集。',
    'What a MATCH asserts about bay.cells: CUBIC decides every cell, SUBJECT equals the union of bound footprints, PRESENCE asserts no cell.':
      'MATCH 对 bay.cells 断言什么：CUBIC 判定每一格，SUBJECT 等于绑定足迹之并，PRESENCE 不断言任何格。',
    'Without the distinction one air predicate would claim a hundred empty cells and call it a match.':
      '没有这层区分，一个空气谓词就会声称拥有 100 个空格，并把它叫做匹配。',
    'CUBIC requires the union of static decision cells to cover bay.cells at DESIGN (SC-BAY-0107); PRESENCE cannot be EXCLUSIVE without a Declared scope (SC-CHK-0106).':
      'CUBIC 要求在 DESIGN 时静态判定格之并覆盖 bay.cells（SC-BAY-0107）；PRESENCE 不带 Declared 范围就不能是 EXCLUSIVE（SC-CHK-0106）。',
    'Owned (bay.cells), Occupied (union of non-air bound footprints), Declared (an explicit box set), Audit (none).':
      'Owned（bay.cells）、Occupied（非空气绑定足迹之并）、Declared（显式盒集）、Audit（不拥有）。',
    'Claims protect what a structure occupies or declares, never what it reads.':
      '声明保护结构占据或声明的格，绝不保护它读过的格。',
    'SUBJECT + Owned is SC-CHK-0106; a non-Audit bay whose read cells leave bay.cells ∪ footprint raises SC-BAY-0108 at SETTLE.':
      'SUBJECT + Owned 是 SC-CHK-0106；非 Audit 舱位的读取格越出 bay.cells ∪ footprint 时，SETTLE 报 SC-BAY-0108。',
    'EXCLUSIVE, SHARED or RESERVED on every bay.':
      '每个舱位上的 EXCLUSIVE、SHARED 或 RESERVED。',
    'Two structures may legitimately share a hatch and must not share a wall; mode is the only place that intention is written.':
      '两个结构可以合法共享一扇舱门，却不能共享一堵墙；模式是唯一写下这个意图的地方。',
    'ClaimMatrix.conflicts(a, b) excludes rows of one (owner, siting); an EXCLUSIVE overlap is SC-CLM-0002.':
      'ClaimMatrix.conflicts(a, b) 排除同一 (owner, siting) 的行；EXCLUSIVE 重叠即 SC-CLM-0002。',

    // explain layer · predicates
    'Check<T>(id, stage, bay, focus, expectation, admission) — the only place a focus and an expectation meet.':
      'Check<T>(id, stage, bay, focus, expectation, admission)——焦点与期望唯一相遇的地方。',
    'Pinning both sides to one T makes a subject-type mismatch a constructor error instead of a runtime surprise.':
      '两边钉在同一个 T 上，主体类型不匹配就成了构造期错误，而不是运行期惊喜。',
    'reads() = focus.reads().withKeys(expectation.meta().requires()); a focus failure is Missing and test is never called with a fabricated subject.':
      'reads() = focus.reads().withKeys(expectation.meta().requires())；焦点失败即 Missing，绝不用伪造主体调用 test。',
    'The subject reader: occupant(cell), allCells(), bay(), context(key), entity(uuid), nested(cell), door(origin).':
      '主体读取器：occupant(cell)、allCells()、bay()、context(key)、entity(uuid)、nested(cell)、door(origin)。',
    'Predicates only judge; when a single focus owns every world read, read scope and caching have one author.':
      '谓词只判定；当所有世界读取都归一个焦点所有，读取范围与缓存就只有一位作者。',
    'take returns Outcome<T>; absence is Missing with an SC-FOC-* code, never test(null).':
      'take 返回 Outcome<T>；缺席是带 SC-FOC-* 代码的 Missing，绝不是 test(null)。',
    'Reply test(ExpectationContext<T>) plus meta: id, specificity, requires and a finding template.':
      'Reply test(ExpectationContext<T>)，外加 meta：id、specificity、requires 与诊断模板。',
    'A predicate is a value, so it can be catalogued, billed, composed and tested without a world.':
      '谓词是一个值，因此可以被编目、计费、组合，并在没有世界的情况下测试。',
    'The same interface serves element predicates (T = OccupantSnapshot), bay predicates (T = BayView) and state predicates (T = S); meta.requires joins the read closure.':
      '同一个接口服务元素谓词（T = OccupantSnapshot）、舱位谓词（T = BayView）与状态谓词（T = S）；meta.requires 并入读取闭包。',
    'MATCH, MISMATCH, UNREADABLE or ABSTAIN, non-generic, with typed Bindings.':
      'MATCH、MISMATCH、UNREADABLE 或 ABSTAIN，非泛型，携带类型化 Bindings。',
    'Four states separate “the world is wrong” from “I could not tell”, which license different actions.':
      '四种状态把“世界是错的”与“我说不出来”分开，它们授权不同动作。',
    'The attempt folds UNREADABLE > MISMATCH > ABSTAIN > MATCH; Bindings.merge refuses a same-key-different-value pair (SC-EXP-0003).':
      '一次尝试按 UNREADABLE > MISMATCH > ABSTAIN > MATCH 折叠；Bindings.merge 拒绝同键不同值（SC-EXP-0003）。',
    'Found(value, subjects, notes) or Missing(findings).': 'Found(value, subjects, notes) 或 Missing(findings)。',
    'Without a Missing arm a failed focus would have to fabricate a subject and the predicate would judge null.':
      '没有 Missing 分支，失败的焦点就只能伪造一个主体，谓词于是去判定 null。',
    'fold(onFound, onMissing); Found.subjects is the only way a binding is built, and a missing subject carries a KEY, WORLD or LOCAL locus.':
      'fold(onFound, onMissing)；binding 只能由 Found.subjects 构建，缺席主体携带 KEY、WORLD 或 LOCAL 位置。',
    'Absence is a fact with a reason; a zero would be a lie the next computation builds on.':
      '缺席是带原因的事实；零会是一个谎，而下一个计算会建立在它上面。',
    'require returns Outcome and get returns null; a missing key is SC-FOC-0302, a refused section SC-FOC-0301.':
      'require 返回 Outcome，get 返回 null；键缺席是 SC-FOC-0302，区段被拒是 SC-FOC-0301。',

    // explain layer · context
    '(id, type, retention, merge?, whenAbsent?); equality is (id, type).':
      '(id, type, retention, merge?, whenAbsent?)；相等性由 (id, type) 决定。',
    'Shared values need a type, a lifetime and a version; a second key type would be a second semantics.':
      '共享值需要类型、生命周期与版本；第二种键类型就是第二套语义。',
    'merge == null is single-writer put; merge != null allows contribute and folds at the barrier; two registrations of one id are SC-CTX-0102.':
      'merge == null 是单写者 put；merge != null 允许 contribute 并在屏障处折叠；同一 id 注册两次是 SC-CTX-0102。',
    'combine(left, right) with no identity element: sum, union, min, max, and, or.':
      '没有单位元的 combine(left, right)：sum、union、min、max、and、or。',
    'Without an identity a missing contribution cannot masquerade as zero.':
      '没有单位元，缺失的贡献就无法冒充零。',
    'Contributions are sorted by SectionKey (x, z, y) and combined left-to-right starting at the first present contribution.':
      '贡献按 SectionKey (x, z, y) 排序，从第一个存在者开始从左到右合并。',
    'ATTEMPT dropped with the attempt, OUTCOME in MatchOutcome, CONTEXT published into the stage frame.':
      'ATTEMPT 随尝试丢弃，OUTCOME 进入 MatchOutcome，CONTEXT 发布到阶段帧。',
    'Only CONTEXT values are visible through a tier frame; OUTCOME stays on the result value.':
      '只有 CONTEXT 值能通过层级帧看见；OUTCOME 留在结果值上。',
    'A per-section partial written with draft.contribute(key, partial).':
      '用 draft.contribute(key, partial) 写入的按区段部分值。',
    'A hundred-cell region is cut by section boundaries so it can parallelise, cache and retry per section.':
      '100 格的区域被区段边界切开，才能并行、缓存并按区段重试。',
    'A SECTION computation runs once per section in region ∩ section; a refused section in the region makes the whole key absent.':
      'SECTION 计算在 region ∩ section 内每个区段跑一次；区域内任一区段被拒，整键缺席。',
    'The barrier step that combines every contribution into one value.':
      '把全部贡献合成一个值的屏障步骤。',
    'Cross-section facts must be complete before a check reads them, or each section judges alone.':
      '跨区段事实必须在检查读取之前完整，否则每个区段都在单独判定。',
    'Fixed order SectionKey (x, z, y); there is no identity element; any refused section in the region makes the key absent.':
      '固定顺序 SectionKey (x, z, y)；没有单位元；区域内任一区段被拒，键就缺席。',

    // explain layer · space & execution
    'Outcome.Missing(FindingBundle) with a KEY, WORLD or LOCAL locus.':
      '带 KEY、WORLD 或 LOCAL 位置的 Outcome.Missing(FindingBundle)。',
    'Air(revision), Present(SectionSnapshot) or Refused(status, code).':
      'Air(revision)、Present(SectionSnapshot) 或 Refused(status, code)。',
    'ReaderStatus is READABLE, OUT_OF_BOUNDS, NOT_CAPTURED, DENIED or STALE; a refused section contributes nothing and defeats the fold.':
      'ReaderStatus 为 READABLE、OUT_OF_BOUNDS、NOT_CAPTURED、DENIED 或 STALE；被拒区段不贡献任何值，并破坏折叠。',
    '(ref, kind, descriptor, footprint, traits, nested?) where ref carries the footprint.':
      '(ref, kind, descriptor, footprint, traits, nested?)，其中 ref 自带 footprint。',
    'Identity includes the footprint, so ClaimScope.Occupied never needs a second lookup.':
      '身份包含足迹，因此 ClaimScope.Occupied 永远不需要二次查询。',
    'WorldView.occupant(cell) is nullable; air is readable and a refused cell returns no snapshot.':
      'WorldView.occupant(cell) 可为空；空气可读，被拒的格不返回快照。',
    'An AutoCloseable handle over one captured revision: id, view, retain, isOpen, close.':
      '覆盖一次已捕获修订的 AutoCloseable 句柄：id、view、retain、isOpen、close。',
    'One owner (the instance record) and child handles for Admitted/Formed; retain charges retainedBytes and can refuse (SC-MC-0502).':
      '一个所有者（实例记录）与 Admitted/Formed 的子句柄；retain 计入 retainedBytes，并可拒绝（SC-MC-0502）。',
    'Maps window → Set<InstanceRef> and keeps the per-cell stamps the gate compares.':
      '维护 window → Set<InstanceRef>，并保存闸门要比对的逐格戳。',
    'A unit that read a changed cell must be dropped; a read cannot be patched after the fact.':
      '读过已变更格的单元必须被丢弃；读取无法事后打补丁。',
    'Each tick compares the live cell → EntityKey set with the captured one; the gate’s fourth term is the per-unit cellStamp since capture.':
      '每 tick 把活的 cell → EntityKey 集合与捕获时比较；闸门第四项是单元自捕获以来的 cellStamp。',
    'The InstanceRecord counter incremented on every emitted Formed.':
      'InstanceRecord 上每发出一次 Formed 就自增的计数器。',
    'A host cache can only tell two Formed values apart by generation.':
      '宿主缓存只能靠 generation 区分两个 Formed 值。',
    'generation increments on a re-emission after DIRTY_UNSTABLE, so the newer value is current rather than a repeat.':
      'DIRTY_UNSTABLE 后的重发会让 generation 自增，因此新值才是当前值，而不是重复。',
    'DESIGN, PROBE, FORM, SETTLE or STALE; a plan is expanded once per stage.':
      'DESIGN、PROBE、FORM、SETTLE 或 STALE；计划按阶段各展开一次。',
    'PROBE/FORM success is Admitted and only SETTLE + Settled is Formed; STALE re-runs the units whose read closure intersects the change.':
      'PROBE/FORM 成功是 Admitted，只有 SETTLE + Settled 才是 Formed；STALE 重跑读取闭包与变更相交的单元。',
    'A readable occupant: air is a snapshot, not the absence of one.':
      '可读占用体：空气是一份快照，而不是快照的缺席。',
    'An owned cavity must be judged cell by cell even though it is empty; treating air as null would make it unjudgeable.':
      '被拥有的空腔即使为空也必须逐格判定；把空气当作 null 会让它无法被判定。',
    'SectionSlot.Air(revision) is readable; a refused cell is never air.':
      'SectionSlot.Air(revision) 可读；被拒的格永远不是空气。',
    'Both halves of a door as one subject, produced by Focuses.door(origin).':
      '一扇门的两个半边作为同一个主体，由 Focuses.door(origin) 产出。',
    'A door expectation must never re-read the world to find the second half.':
      '门的谓词绝不能为了找另一半而重新读世界。',
    'SUBJECT coverage asserts bay.cells equals the union of bound footprints; Occupied owns the non-air footprint, not the empty cell.':
      'SUBJECT 覆盖断言 bay.cells 等于绑定足迹之并；Occupied 拥有非空气足迹，而不是空格。',

    // explain layer · results
    'The sealed result: Admitted, Formed, Failed, Undecided or Deferred.':
      '封存的结果：Admitted、Formed、Failed、Undecided 或 Deferred。',
    'Five arms distinguish passed cheaply, is a structure, world is wrong, we stopped, and never ran.':
      '五个分支分别表示：便宜地通过、构成结构、世界是错的、我们停下了、从未运行。',
    'Admitted is not a structure and derive refuses it; only SETTLE produces Formed.':
      'Admitted 不是结构，derive 拒绝它；只有 SETTLE 产出 Formed。',
    'A PROBE or FORM success: stage, siting, MatchOutcome, a SnapshotLease and findings, with no instance id and no claims.':
      'PROBE 或 FORM 的成功：stage、siting、MatchOutcome、SnapshotLease 与诊断，没有实例 id，也没有声明。',
    'A structure that passes FORM is admissible, not formed; giving it claims would let two owners overlap.':
      '通过 FORM 的结构只是可准入，不是已成形；给它声明就会让两个所有者重叠。',
    'derive() refuses an Admitted result; the host closes its lease when it stops using it.':
      'derive() 拒绝 Admitted 结果；宿主不再使用时关闭其租约。',
    'SETTLE + Settled: InstanceId, siting, outcome, ClaimSet, generation, contextRevision, ledgerRevision and lease.':
      'SETTLE + Settled：InstanceId、siting、outcome、ClaimSet、generation、contextRevision、ledgerRevision 与租约。',
    'Formed exists only after Settled; a conflict is Failed(SC-CLM-0002) with the incumbent rows.':
      'Formed 只存在于 Settled 之后；冲突是带现有行的 Failed(SC-CLM-0002)。',
    'The world genuinely does not satisfy the structure; the arm carries findings only.':
      '世界确实不满足结构；该分支只携带诊断。',
    '“The world is wrong” licenses a repair, while “I could not tell” only licenses a re-read.':
      '“世界是错的”授权修复，而“我说不出来”只授权重新读取。',
    'Failed ⇔ mismatch ≥ 1 ∨ claimConflict ≥ 1; refutation outranks ignorance.':
      'Failed ⇔ mismatch ≥ 1 ∨ claimConflict ≥ 1；反驳优先于无知。',
    'A reason, findings, an optional partial outcome and an optional RetryToken.':
      '一个原因、诊断、可选的部分事实与可选的重试令牌。',
    'Stopping without a verdict must be distinguishable from refuting the world.':
      '没有裁决的停止必须能与反驳世界区分开。',
    'Reason precedence CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS.':
      '原因优先级 CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS。',
    'A terminal arm carrying a RetryToken; no unit ran.':
      '携带 RetryToken 的终止分支；没有任何单元运行。',
    'A queue that refuses work must say so without pretending the world failed.':
      '拒绝工作的队列必须说出来，而不是假装世界失败了。',
    'Only AUTO re-submits automatically; INTERACTIVE holds one reserved permit and AUTO is disabled on a one-worker host.':
      '只有 AUTO 会自动重投；INTERACTIVE 保留一张许可，单工作线程宿主上 AUTO 被禁用。',
    'The ledger of ClaimRows — (cell, mode, owner, siting, bay) — with one conflict predicate.':
      'ClaimRow 的账本——(cell, mode, owner, siting, bay)——配一个冲突谓词。',
    'conflicts(a, b) compares rows while excluding one (owner, siting); settle replaces every row of (owner, siting) in a single CAS.':
      'conflicts(a, b) 比较行并排除同一 (owner, siting)；settle 在一次 CAS 中替换该 (owner, siting) 的所有行。',
    'Settled(revision, rows), ClaimConflict(rows, findings) or RevisionMoved(expected, actual).':
      'Settled(revision, rows)、ClaimConflict(rows, findings) 或 RevisionMoved(expected, actual)。',
    'A partial replacement would leave a structure half-owning its cells.':
      '部分替换会让结构半拥有它的格。',
    'Nine components: id, code, severity, message, labels, notes, suggestion, related, stage.':
      '九个组件：id、code、severity、message、labels、notes、suggestion、related、stage。',
    'A label sits on a Locus of DEFINITION, LOCAL, WORLD or KEY; a KEY locus carries (InstanceId, key id) and no cells.':
      '标签落在 DEFINITION、LOCAL、WORLD 或 KEY 位置上；KEY 位置携带 (InstanceId, key id)，不含格。',
    'An ordered, deduplicated, capped collection of findings.':
      '按序、去重并封顶的诊断集合。',
    'Errors scattered through world coordinates are unreadable; the order is the report.':
      '错误散落在世界坐标里就无法阅读；顺序就是报告。',
    'Sort by severity → stage → code → primary locus → insertion, deduplicate by (code, primary label).':
      '按 severity → stage → code → 主位置 → 插入序排序，按 (code, primary label) 去重。',

    // explain layer · codes & runtime
    'A section in the read region is Refused, so the focus finds no subject.':
      '读取区域内有区段被拒，因此焦点找不到主体。',
    'A refused section is never air and never a short sum.':
      '被拒区段永远不是空气，也永远不是一个短的和。',
    'draft.refuse(key) makes the key absent at the barrier, require returns Missing and the reply folds to UNREADABLE — never MISMATCH.':
      'draft.refuse(key) 让键在屏障处缺席，require 返回 Missing，回复折叠为 UNREADABLE——绝不是 MISMATCH。',
    'publish() was handed a baseRevision the store has already moved past.':
      'publish() 收到的 baseRevision 已被存储越过。',
    'A slow old computation must not overwrite a newer frame.':
      '缓慢的旧计算不得覆盖更新的帧。',
    'publish writes nothing and returns Missing(SC-CTX-0108); the newer frame stays intact and the attempt ends Undecided(DIRTY_UNSTABLE).':
      'publish 不写入任何值并返回 Missing(SC-CTX-0108)；新帧保持完整，尝试以 Undecided(DIRTY_UNSTABLE) 结束。',
    'An incumbent holds an EXCLUSIVE cell that the new structure also claims.':
      '现有结构持有新结构也想声明的 EXCLUSIVE 格。',
    'Formed exists only after Settled, so a stolen cell can never be reported as success.':
      'Formed 只在 Settled 之后存在，因此被偷走的格永远不会被报告为成功。',
    'ClaimConflict becomes Failed(SC-CLM-0002) with the incumbent rows attached; no Formed is emitted.':
      'ClaimConflict 变成附带现有行的 Failed(SC-CLM-0002)；不会发出 Formed。',
    'The instance budget ran out before the plan finished.':
      '实例预算在计划完成之前耗尽。',
    'A budget stop licenses a re-read, not a repair; it points at the step, not at a cell.':
      '预算停止授权重新读取，而不是修复；它指向步骤，而不是某一格。',
    'Carries no label; reason BUDGET_EXHAUSTED wins over UNREADABLE, and admission is charged on dispatch whether or not the unit is cached.':
      '不带标签；BUDGET_EXHAUSTED 优先于 UNREADABLE，且无论是否命中缓存，admission 都在派发时计费。',
    'The persisted projection of SETTLE plus host upgrades.':
      'SETTLE 加宿主升级之后的持久化投影。',
    'Readers need the last settled value without re-running the plan.':
      '读者需要最后一次已结算的值，而不必重跑计划。',
    'RUNTIME has no producer; a host upgrade raises contextRevision and keeps the tier lineage.':
      'RUNTIME 没有生产者；宿主升级抬高 contextRevision，并保留层级血缘。',
    'The monotonic request id carried in MatchRequest and checked by the gate.':
      'MatchRequest 携带、由闸门检查的单调请求 id。',
    'A newer request must supersede an older one without a second in-flight attempt.':
      '更新的请求必须取代旧请求，而不是多一个在飞的尝试。',
    'One in-flight per (world, owner); newer requests coalesce by ticket and cancel() is cooperative.':
      '每个 (world, owner) 只有一个在飞；更新的请求按票据合并，cancel() 是协作式的。',
  },
};
