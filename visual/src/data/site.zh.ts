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
  world: { owns: 'occupant · 类型化 host 读取 · trait · section · capture lease', law: '快照不可变；lease 只有 isCurrent()。' },
  claim: { owns: 'row · sharing · footprint · ledger port', law: '每个 cell × owner × siting 一行；只有一个冲突谓词。' },
  structure: { owns: 'bay · check · blueprint · focus · plan · envelope', law: 'bay 拥有 cells；check 是 focus × expectation。' },
  context: { owns: 'store · key 语义 · fold · deriver · cached context', law: '一个 key，两种 retention，一次发布。' },
  document: { owns: 'IR · compiler · reader/writer ports · structure set', law: '数据进；一个 blueprint 或一组 finding 出。' },
  engine: { owns: 'request · result · planner · pipeline bodies', law: '一次 attempt；没有 stage machine。' },
  remedy: { owns: 'repair · preview · edit plan · provider SPI', law: 'remedy 是值，不是执行器。' },
  spi: { owns: 'catalogs · facade · host ports · built-ins', law: '唯一知道所有 package 的 package。' },
};

export const ZH_PRINCIPLES: Record<string, { title: string; text: string }> = {
  '01': { title: '一个 key', text: 'ContextKey<T> 是 context value 的唯一身份。同一个 id、不同的 Java 类型，就是两个 key。' },
  '02': { title: '一次发布', text: '成功的 settle 向 structure attempt 只发布一次 INSTANCE；commit 把它绑定到 StructureInstance；derive 只发布一个 batch 一次。StructureManager 拥有 registry 和 context 生命周期（D-40）。' },
  '03': { title: '一个方向', text: 'package 只能 import rank 更低的 package。图会被检查；边表就是法律。' },
  '04': { title: '一个谓词', text: 'ClaimMatrix.conflicts 是唯一的冲突规则。plan 不能再编码第二个。' },
  '05': { title: '一次 host 读取', text: '一个 host 值就是一次来自不可变快照的类型化读取。descriptor 始终是 (type, kind)；kernel 从不命名或建模这个值。' },
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
  'HostData table': '一份不可变快照，多种类型化读取；没有 per-cell host map，也没有 union snapshot。',
  'Descriptor projection': 'descriptor 是 (type, kind)。property、state、tag 和 fluid check 在它自己的 frame 里读取捕获值。',
  'readBlock / readEntity': 'provider 只声明一次 holder；没有 provider 就是缺席，不是隐藏的 default。',
  'blockEntityNbt / entityNbt': 'trait 就是所属 API 的值；序列化不是 API，也不存在 item holder。',
  'Structure-set transaction': 'root、satellites 和 links 都是 host 编排；kernel 仍然一次只 check、settle 一个 blueprint。',
  'Per-attempt set budget': 'presence 和每个成员 attempt 从同一个上限读取；set 才是 host 准入的单位。',
  'Kernel context rollback': 'set rollback 释放 claims 并派发 Broken；manager 一起移除 instance 和它的 context。kernel 仍然没有事务。',
  CapabilityLookup: '一个 per-holder registry 取代聚合的 block/entity lookup；新增 subject kind 只需加 holder 和 provider，不改查找接口。',
  SavedContexts: 'context 绑定到 StructureInstance；begin/commit 是唯一的创建路径，manager 是 in-memory 的，库不持久化任何东西。',
  'NOTAIR = no fluid': 'NOTAIR 精确等于 not(air())：water 和 lava 是 non-air，因而通过。empty() 和 nonFluid() 是独立的 opt-in leaf（D-39）。',
  'Per-bay claim release': '一个 instance 就是一个键；release 删除该 instance 的全部 rows，没有单独的行被释放（D-38/D-45）。',
  'Claimable by default': 'focus 必须 opt in；当 bay 会 claim 时，build 拒绝不能 claim 的 subject-binding focus（SC-BPT-0005，D-37）。',
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
  'docs/design/08-context-cache.md': 'context 缓存',
  'docs/design/09-structure-sets.md': '结构集合',
  'docs/design/10-host-data.md': 'host 快照',
  'docs/design/11-trait-providers.md': 'trait provider',
  'docs/design/12-structure-instances.md': '结构实例与 manager',
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
  'docs/modules/adapter.md': 'adapter',
};

export const ZH_GROUPS: Record<string, string> = {
  Design: '设计',
  Modules: '模块',
};

export const ZH_GATES: Record<string, { label: string; note: string }> = {
  Compile: { label: '编译', note: 'gradlew check 在 D-82 tree 通过' },
  Purity: { label: '纯度', note: '0 个 Minecraft 名字' },
  Tests: { label: '测试', note: 'kernel JUnit 5 · 68 个容器；adapter 442 / 46 个容器；GameTests 140/140' },
  'Spec index': { label: '规范索引', note: 'spec-check.py · 66 codes' },
  'No stubs': { label: '无空桩', note: 'TODO · FIXME · UOE 扫描' },
  Determinism: { label: '确定性', note: 'DeterminismTest · canonical bytes、fixed point、compile runs' },
};

export const ZH_HERO_STATS: Record<string, { label: string; note: string }> = {
  tests: { label: '测试', note: 'kernel JUnit 5 · adapter 442 · GameTests 140/140' },
  codes: { label: '代码', note: '13 个 kernel family · 一张 Codes 表' },
  'purity leaks': { label: '纯度泄漏', note: 'kernel 里的 Minecraft 名字' },
  decisions: { label: '决策', note: 'D-82 · ruling，不是偏好' },
};

export const ZH_LAYERS: Record<string, { title: string; text: string }> = {
  'D-30': {
    title: '推导出的 envelope',
    text: 'bounds 减去已声明的 cells，得到一个保留 bay 和一个 check；外来 occupant 是 SC-EXP-0006，空集合是 no-op。',
  },
  'D-31': {
    title: 'cached context',
    text: 'MatchRequest.cachedContext 是 host 拥有的 (id, type) 缓存，只服务已声明的 instance keys；当前 attempt 永远优先。',
  },
  'D-32': {
    title: '结构集合',
    text: 'root + satellites + links + presence，由 StructureSetHost 编排；S5 提供 /cumulus set check|settle 和一次不带 orientation 的 union capture（不做 orientation audit，因此不会为 satellite cell 产生多余的 SC-MC-0004）；kernel 仍然没有 set 类型。',
  },
  'D-33': {
    title: '类型化 host 快照',
    text: '每个 frame 一份快照，HostType 读取和 Expectations.host；OccupantDescriptor 是 (type, kind)，D-29 已被取代。',
  },
  'D-34': {
    title: 'trait providers',
    text: '每个 holder 一个 TraitProvider<T, H>/TraitHolder，一个 CapabilityTraitProvider 类；capability traits 直接返回 IItemHandler、IEnergyStorage 和 IFluidHandler。没有 NBT trait，也没有 item holder。',
  },
  'D-35': {
    title: 'set 收束',
    text: 'SC-SET-0008 拒绝与 root 同 siting 的 satellite（compiler、StructureSet.place、host check）；presence 和每个 attempt 共享一个 set budget；rollback 通过 StructureManager 的 Broken 事件失效已形成的 context（D-40）。真实 level 的 StructureSetHostGameTest 覆盖同 siting 拒绝和一格共享 budget。',
  },
  'D-36': {
    title: 'nested-extent lookup',
    text: 'NestedExtentHolder/Provider/Lookup 取代聚合的 nested lookup：NestedExtentHolders.block/entity，一个通过 NestedExtentAccesses 覆盖两种签名的 CapabilityNestedExtentProvider，以及标准的 NestedExtentProviders。旧的 mc.NestedExtentLookup 和 CapabilityLookup 已删除。',
  },
  'D-37': {
    title: '可 claim 的 subject',
    text: 'Focus.bindsClaimableSubject() 默认 false；当 bay 会 claim 时，BlueprintBuilder 拒绝不能支撑 claim 的 subject-binding focus（SC-BPT-0005）。运行时的 SC-CLM-0004 仍是权威。',
  },
  'D-38': {
    title: 'whole-key release',
    text: 'ClaimLedger.release(instance) 删除该 instance 的全部 rows，是唯一的 release API（D-45），因此没有 per-bay 或 per-row release。observation funnel 对被移动的结构 break 或 invalidate 并释放其 rows——move 是移除，不是平移。失败的 release 是 SC-SET-0007。',
  },
  'D-39': {
    title: 'empty 与 non-fluid',
    text: 'Expectations.empty() 匹配 air 或 vanilla fluid block；nonFluid() 匹配其余 occupant（air 是 non-fluid）。NOTAIR 精确等于 not(air())，所以 water/lava 通过、air 失败。没有新 code。',
  },
  'D-40': {
    title: 'structure manager',
    text: 'StructureManager 拥有 StructureSpec/Instance/Attempt/Event：begin/commit/abort、bounds lookup、Broken/Invalidated/Loaded/Unloaded，每个 (world, owner, siting) 一个 formed instance。context 绑定到 instance，manager 是 in-memory 的；adapter 派发生命周期。',
  },
};
