import type { BayId, MomentId } from './campfire';

export const ZH_BAYS: Record<BayId, { note: string }> = {
  shell: { note: '断言 25 个方块；拥有 3×3×3 的 shell' },
  hollow: { note: '空腔；拥有一个空气 cell' },
  fire: { note: '营火；断言 lit=true' },
};

export const ZH_MOMENTS: Record<MomentId, { title: string; note: string; log: string; readout: Record<string, string> }> = {
  compile: {
    title: '数据变成一个 blueprint',
    note: '定义被摊平；keys、bays、checks、computations 下沉到与 Java DSL 相同的值。坏 document 返回 findings，绝不返回半成品 blueprint。',
    log: 'document + java → 一个 blueprint',
    readout: { lowering: '一个 blueprint' },
  },
  check: {
    title: '一次不可变的读取',
    note: 'plan 完整执行：sections 折叠、aggregates 读取、checks 汇合。Claims 只被预测，不写入。lease 是唯一的门。',
    log: '27 条 claim 被预测，尚未写入',
    readout: { result: 'Admitted', checks: '3 / 3 匹配', spend: '28 次读取 · 4 个 section', claims: '预测 27' },
  },
  settle: {
    title: '一次写入，一次发布',
    note: 'ledger 的 compare-and-set 写入 rows 并推进 revision。context 只发布一次 INSTANCE values。结果是纯值。',
    log: 'compare-and-set · 发布 air_cells',
    readout: { result: 'Formed', 'claim rows': '27', published: 'air_cells = 1' },
  },
  derive: {
    title: '再一次纯折叠',
    note: 'deriver 只读已发布的 context。derive 是作用在 Formed 上的操作，不是第四个阶段；capacity 从 air 派生，第二次发布保持 settle 的 spend，只推进一次 revision。',
    log: 'capacity 10 来自 air_cells = 1',
    readout: { writes: '一个 batch' },
  },
};

export const ZH_INVARIANTS: Record<string, { title: string; detail: string }> = {
  lease: {
    title: 'lease 是唯一的门',
    detail: 'Host 作用域；进入和退出都检查 isCurrent()。运行中 close 就是 Dropped，绝不会得到 partial result。',
  },
  writes: {
    title: 'check 不写任何东西',
    detail: '27 条 claim rows 从 bindings 预测出来，然后随 request 一起丢弃。只有 settle 可以写。',
  },
  value: {
    title: '结果是纯值',
    detail: 'Admitted 和 Formed 携带 view、values、spend。lease 从不被保留，也没有 handle 逃逸。',
  },
  publish: {
    title: 'settle 时只发布一次',
    detail: 'settle 发布一次 INSTANCE；derive 在 Formed 之后再加一个 batch，并且只推进一次 revision。没有别的地方写。',
  },
  spend: {
    title: '一个 spend，一个 snapshot',
    detail: '4 个 section 共 28 次读取：shell 25 + hollow 1 + fire 1 个 check，再加 1-cell air section。Claims 仍是 27。',
  },
};

export const ZH_QUARTERS: Record<number, string> = {
  0: '恒等',
  1: '顺时针',
  2: '180°',
  3: '逆时针',
};

export const ZH_SITING = {
  title: 'blueprint 留在 local。',
  body: 'Bays、checks 和 computations 都是 local cells。每个 focus、section、aggregate 的 cell 参数都会在读取前按 request 的 Siting 映射一次，所以 document 从不携带 world 坐标。',
  local: 'local 固定',
  world: 'world 移动',
  localNote: 'local 图不动',
  worldNote: 'origin + 方向后的世界图',
  oneMapping: '每次读取只映射一次',
  rootFrame: '根 frame 0',
  originX: 'origin X',
  originZ: 'origin Z',
  quarterTurn: '90° 转向',
  reset: '重置',
  origin: 'origin',
  placement: 'placement',
  quarter: 'quarter turns',
  worldFire: 'world fire',
  mapping: 'mapping',
  staysLocal: 'blueprint 留在 local；只有 image 移动。',
};

export const ZH_FURNACE = {
  title: '巨型熔炉',
  note: '还是同一模型，7×6×7。一个 door 绑定两个 cells；cavity 断言 100 个空气 cells，并计算自己的 volume。192 + 100 + 2 = 294 行，每行一个 owned cell，无重叠。',
};
