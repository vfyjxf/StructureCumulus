import type { ModuleId, SectionId } from './site';

export const ZH_NAV: Record<SectionId, { label: string; sub: string }> = {
  thesis: { label: '论点', sub: '论证的形状' },
  lattice: { label: '图谱', sub: '十一个 package，一个方向' },
  object: { label: '对象', sub: 'Bay、check、blueprint、outcome' },
  workbench: { label: '工作台', sub: '营火，从头到尾' },
  ledger: { label: '账本', sub: '没有 plan 的 claim' },
  context: { label: '折叠', sub: '缺席也是值' },
  codes: { label: '代码', sub: '报告层' },
  proof: { label: '证据', sub: '决策、测试、重生' },
};

export const ZH_MODULES: Record<ModuleId, { owns: string; law: string }> = {
  core: { owns: '标识 · 阶段 · 键 · 值 · 目录', law: '没有几何。没有报告。没有遍历。' },
  space: { owns: '格点 · 立方体集合 · 方向 · siting', law: '几何是值；siting 只做一次局部到世界的映射。' },
  finding: { owns: 'finding · bundle · code 表 · renderer', law: 'finding 是值；bundle 是唯一聚合器。' },
  world: { owns: 'occupant · trait · section · capture lease', law: '快照不可变；lease 只有 isCurrent()。' },
  claim: { owns: 'row · sharing · footprint · ledger port', law: '每个 cell × owner × siting 一行；只有一个冲突谓词。' },
  structure: { owns: 'bay · check · blueprint · focus · plan', law: 'bay 拥有 cells；check 是 focus × expectation。' },
  context: { owns: 'store · key 语义 · fold · deriver', law: '一个 key，两种 retention，一次发布。' },
  document: { owns: 'IR · compiler · reader/writer ports', law: '数据进；一个 blueprint 或一组 finding 出。' },
  engine: { owns: 'request · result · planner · pipeline bodies', law: '一次 attempt；没有 stage machine。' },
  remedy: { owns: 'repair · preview · edit plan · provider SPI', law: 'remedy 是值，不是执行器。' },
  spi: { owns: 'catalogs · facade · host ports · built-ins', law: '唯一知道所有 package 的 package。' },
};

export const ZH_PRINCIPLES: Record<string, { title: string; text: string }> = {
  '01': { title: '一个 key', text: 'ContextKey<T> 是 context value 的唯一身份。同一个 id、不同的 Java 类型，就是两个 key。' },
  '02': { title: '一次发布', text: '成功的 settle 只发布一次 INSTANCE；derive 只发布一个 batch 一次。没有别的地方写 context。' },
  '03': { title: '一个方向', text: 'package 只能 import rank 更低的 package。图会被检查；边表就是法律。' },
  '04': { title: '一个谓词', text: 'ClaimMatrix.conflicts 是唯一的冲突规则。plan 不能再编码第二个。' },
};

export const ZH_REMOVED: Record<string, string> = {
  Stage: '事实携带“何时可知”，但 request 从不选择执行阶段。',
  ContextTier: 'MATCH 或 INSTANCE 就是完整的生命周期决定；没有第二条可见性轴。',
  Requirement: 'EVERY_CELL、ONE_SUBJECT、ANY_SUBJECT 已经说明 bay 欠什么。',
  BayBuilder: 'bay 就是一个带 CuboidSet 的值；没有两步构造的结。',
  Segment: 'shell、door、L 形房间都是同一个代数。',
  Overlap: 'overlap 从 cells 和一个 sharing 值检查，不需要成对声明。',
  NestedView: '嵌套 view 就是同一个不可变递归面，只是 frame 不同。',
  ClaimPlan: 'row 在 check 之后派生；Admitted 带 bindings 和 revision，不带 plan。',
  DecisionKind: 'focus 声明它决定哪些 cells、是否绑定 subject。',
  ReadSet: '一个 ReadScope 闭包 cells、keys、windows；没有失效索引。',
  Deferred: 'retry 是一次新调用，不是存起来的 token。',
  Augment: '一个类型化 key；值不会被 patch 进单独的 augmentation 层。',
};

export const ZH_DOCS: Record<string, string> = {
  'docs/README.md': '设计之书',
  'docs/design/00-requirements.md': '需求',
  'docs/design/01-constitution.md': '宪法',
  'docs/design/02-model.md': '模型',
  'docs/design/03-modules.md': '模块',
  'docs/design/04-api.md': 'API 导览',
  'docs/design/05-decisions.md': '决策账本',
  'docs/design/06-verification.md': '验证',
  'docs/design/07-regeneration.md': '重生',
  'docs/modules/structure.md': 'structure',
  'docs/modules/document.md': 'document',
  'docs/modules/engine.md': 'engine',
  'docs/modules/claim.md': 'claim',
  'docs/modules/world.md': 'world',
  'docs/modules/context.md': 'context',
  'docs/modules/finding.md': 'finding',
  'docs/modules/core.md': 'core',
  'docs/modules/space.md': 'space',
  'docs/modules/remedy.md': 'remedy',
  'docs/modules/spi.md': 'spi',
  'docs/modules/adapter.md': 'adapter（S6 完成）',
};

export const ZH_GROUPS: Record<string, string> = {
  Design: '设计',
  Modules: '模块',
};

export const ZH_GATES: Record<string, { label: string; note: string }> = {
  Compile: { label: '编译', note: 'javac 21 · gradlew check 通过' },
  Purity: { label: '纯度', note: '0 个 Minecraft 名字' },
  Tests: { label: '测试', note: 'kernel JUnit 5 · 33 个容器；adapter 141 · GameTests 66/66' },
  'Spec index': { label: '规范索引', note: 'spec-check.py · 56 codes' },
  'No stubs': { label: '无空桩', note: 'TODO · FIXME · UOE 扫描' },
  Determinism: { label: '确定性', note: 'DeterminismTest · canonical bytes、fixed point、compile runs' },
};

export const ZH_HERO_STATS: Record<string, { label: string; note: string }> = {
  tests: { label: '测试', note: 'kernel JUnit 5 · adapter 141 · GameTests 66/66' },
  codes: { label: '代码', note: '12 个 kernel family + MC' },
  'purity leaks': { label: '纯度泄漏', note: 'kernel 里的 Minecraft 名字' },
  decisions: { label: '决策', note: 'ruling，不是偏好' },
};
