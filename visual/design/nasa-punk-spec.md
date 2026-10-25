# NASA-punk Technical Visualization Suite — 设计规范 v1.0

> 适用对象：StructureCumulus 的可视化前端（匹配结果、finding 表、remedy overlay、stage 时间线）。
> 本文是**实现契约**：所有颜色、尺寸、间距、边框、状态均已给定；实现方不需要再做设计判断。
> 唯一允许的偏离：字体文件的获取方式（见 §3.2）。其余数值不得"大概"取值。
> 状态：可执行草案，2026-10-21。

---

## 0. 这套风格是什么，不是什么

不是"太空壁纸"。它是一份**能被复印、能被拍成黑白照片、能在磷光屏上读**的工程图纸。1975 年 NASA 图形标准手册把识别系统压缩到"一个字标 + 一个红色 + 一套网格"；1960 年代休斯顿任务控制室的 Philco-Ford 控制台把面板做成 1.75 in × 4.75 in 的模块倍数，并且给每个操作员一个**极性开关**：数据密的时候黑底白字看不清，就翻成白底黑字。这两件事定义了本规范的核心：**网格是结构，单色是底线，颜色是信号而不是装饰。**

NASA-punk 这个词来自 Starfield 美术指导 Istvan Pely 对 1970 年代 NASA 语汇的引用——"不是全息，是按钮；不是未来，是一段真实发生过的时期"。落到界面上就是：**可触摸的控件感、机械刻度、印刷标记、没有一处是纯装饰。**

---

## 1. 原则（6 条，冲突时按序号裁决）

| # | 原则 | 可检验的判据 |
| --- | --- | --- |
| P1 | **功能先于形式** | 任何图元若删掉后不损失信息，就删掉。装饰性渐变、光晕、投影、圆角一律不加。 |
| P2 | **每个标记都有标签** | 图上不存在无标签的色块、线、点。单位永远紧跟数值（`12.4 mm`，不写 `12.4`）。 |
| P3 | **网格是结构** | 所有元素吸附 4px 基线网格与 12 列列网格；不居中对齐"看起来差不多"的位置。 |
| P4 | **受限信号调色板** | 一屏最多 2 个信号色（橙 + 一个状态色）。红只用于失败，黄只用于危险条带，绿只用于确认。 |
| P5 | **印刷 / 磷光双态** | 同一信息在 `paper` 与 `crt` 两套主题下**等价**：去掉颜色、去掉发光后仍可读（复印测试）。任何只靠发光表达的信息都是缺陷。 |
| P6 | **密度靠层级，不靠留白** | 用字号 / 字重 / 线宽 / 灰度四级区分主次；不靠大面积空白"呼吸"，也不靠卡片堆叠分层。 |

**复印测试（P5 的验收动作）**：截屏 → 转灰度 → 若仍有 ≥3 级可辨层级、且失败态仍可辨认，通过。

---

## 2. 颜色

### 2.1 印刷态（`paper`，默认主题）

| Token | Hex | 用途 | 禁止用途 |
| --- | --- | --- | --- |
| `--np-paper` | `#F2EFE6` | 图纸底、画布底 | 不用纯白（`#FFFFFF` 反光过强，且不像纸） |
| `--np-paper-deep` | `#E6E2D4` | 面板头、表头、图例底 | 不作正文底（与 paper 对比不足） |
| `--np-ink` | `#14160F` | 正文、结构线、标题 | — |
| `--np-ink-muted` | `#5C5B50` | 次级文字、单位、轴标签 | 不用于 ≤10px 的正文（见 §2.3） |
| `--np-grid` | `#C6C0AE` | 装饰性网格、刻度底线（**非信息**） | 不用于任何承载状态或分组的线 |
| `--np-rule-soft` | `#B6AF9C` | 表格行分隔、面板内细分隔 | — |
| `--np-rule` | `#7E7A6A` | **结构性**边框、面板外框、坐标轴 | 不用作文字色 |
| `--np-signal` | `#FF8200` | 信号橙：填充、色块、2px 以上标记线、50% 透明底 | **不用于纸上文字或 1px 线**（对比 2.16） |
| `--np-signal-ink` | `#A84600` | 橙色文字、1px 选中描边 | — |
| `--np-safety` | `#FFD100` | 危险条带填充、越界区 | 不用于文字、不作背景大面积使用 |
| `--np-ok-ink` | `#00683F` | 确认态文字/描边（印刷态绿） | — |
| `--np-alert` | `#CE1126` | 失败态填充、3px 边条 | — |
| `--np-alert-ink` | `#B30E20` | 失败态文字/1px 描边 | — |
| `--np-caution` | `#0072CE` | 信息态填充 | 作为文字底时改用 `--np-caution-ink`（`#0072CE` 上白字仅 4.25，不达标） |
| `--np-caution-ink` | `#005FAF` | 蓝色文字、1px 描边 | — |
| `--np-warn-ink` | `#7A4E00` | 警示态文字/描边（黄不能作字，用暗琥珀） | — |

平涂色（badge 底、选中行底，均不含文字对比要求）：

| Token | Hex | Token | Hex |
| --- | --- | --- | --- |
| `--np-tint-signal` | `#F7E3CC` | `--np-tint-alert` | `#F0D8DA` |
| `--np-tint-safety` | `#F6EDC6` | `--np-tint-caution` | `#D8E3EE` |
| `--np-tint-ok` | `#D8E5DA` | `--np-tint-neutral` | `#E3DFD1` |

### 2.2 磷光态（`crt`，`[data-theme="crt"]`）

| Token | Hex | 对标印刷 token | 说明 |
| --- | --- | --- | --- |
| `--np-void` | `#0A0D0A` | `--np-paper` | 带绿的近黑，不是纯黑（P1 磷光余辉偏绿） |
| `--np-void-deep` | `#111711` | `--np-paper-deep` | 面板头 |
| `--np-lit` | `#D6E6CC` | `--np-ink` | 正文；不用纯绿（长读疲劳） |
| `--np-lit-muted` | `#8C9C86` | `--np-ink-muted` | 次级文字 |
| `--np-grid` | `#22301F` | 同 | 装饰网格 |
| `--np-rule-soft` | `#2C3A29` | 同 | 细分隔 |
| `--np-rule` | `#546B4E` | 同 | 结构边框 |
| `--np-signal` | `#FF9A3C` | `--np-signal` | 磷光橙（暗底上提亮） |
| `--np-signal-ink` | `#FF9A3C` | `--np-signal-ink` | 暗底上两者合一（9.25） |
| `--np-safety` | `#FFD84D` | `--np-safety` | 条带黄，配 `--np-void` 墨 |
| `--np-phosphor` | `#4BFF87` | `--np-ok-ink` | P1 型磷光绿（525 nm 峰值），只用于读数与描边 |
| `--np-alert` / `--np-alert-ink` | `#FF5A5A` | `--np-alert` / `--np-alert-ink` | 暗底合并 |
| `--np-caution` / `--np-caution-ink` | `#4FB0FF` | 同 | — |
| `--np-warn-ink` | `#FFC24D` | `--np-warn-ink` | 暗底上的黄不能作字，用亮琥珀（12.17） |

暗底平涂：`#2A1B0C`（signal）/ `#2A1315`（alert）/ `#0E1D2C`（caution）/ `#10241A`（ok）/ `#1A2118`（neutral）。

### 2.3 对比度（实测值，WCAG 2.1 相对亮度公式）

**印刷态**

| 前景 / 背景 | 比值 | 等级 | 允许用途 |
| --- | --- | --- | --- |
| `#14160F` / `#F2EFE6` | **15.86** | AAA | 正文、标题 |
| `#5C5B50` / `#F2EFE6` | **5.95** | AA | 次级文字，≥12px |
| `#14160F` / `#E6E2D4` | **14.06** | AAA | 面板内正文 |
| `#5C5B50` / `#E6E2D4` | **5.28** | AA | 面板内次级文字 |
| `#A84600` / `#F2EFE6` | **5.16** | AA | 橙色文字 |
| `#A84600` / `#E6E2D4` | **4.57** | AA | 面板内橙色文字（下限） |
| `#B30E20` / `#F2EFE6` | **6.09** | AA | 失败态文字 |
| `#B30E20` / `#E6E2D4` | **5.40** | AA | — |
| `#00683F` / `#F2EFE6` | **5.98** | AA | 确认态文字 |
| `#005FAF` / `#F2EFE6` | **5.61** | AA | 信息态文字 |
| `#7A4E00` / `#F2EFE6` | **6.26** | AA | 警示态文字 |
| `#14160F` / `#FFD100` | **12.48** | AAA | 黄底黑字（条带内文字） |
| `#F2EFE6` / `#CE1126` | **4.89** | AA | 红底纸色字 |
| `#F2EFE6` / `#005FAF` | **5.61** | AA | 蓝底纸色字（**用 005FAF，不用 0072CE**） |
| `#7E7A6A` / `#F2EFE6` | **3.74** | 非文字 3:1 通过 | 结构边框仅此用途 |
| `#C6C0AE` / `#F2EFE6` | 1.58 | — | 纯装饰网格，**不得承载信息** |
| `#FF8200` / `#F2EFE6` | 2.16 | 不通过 | 仅填充 / ≥2px 标记 |
| `#FFD100` / `#F2EFE6` | 1.27 | 不通过 | 仅填充 |

**磷光态**

| 前景 / 背景 | 比值 | 等级 |
| --- | --- | --- |
| `#D6E6CC` / `#0A0D0A` | **14.93** | AAA |
| `#D6E6CC` / `#111711` | **13.90** | AAA |
| `#8C9C86` / `#0A0D0A` | **6.71** | AA |
| `#4BFF87` / `#0A0D0A` | **14.84** | AAA |
| `#FF9A3C` / `#0A0D0A` | **9.25** | AAA |
| `#FF5A5A` / `#0A0D0A` | **6.38** | AA |
| `#4FB0FF` / `#0A0D0A` | **8.36** | AAA |
| `#FFC24D` / `#0A0D0A` | **12.17** | AAA |
| `#0A0D0A` / `#4BFF87` | **14.84** | AAA（绿底黑字用于实心 chip） |
| `#0A0D0A` / `#FFD84D` | **14.12** | AAA（黄底黑字） |
| `#546B4E` / `#0A0D0A` | **3.34** | 非文字 3:1 通过 |
| `#22301F` / `#0A0D0A` | 1.41 | 纯装饰 |

**硬规则**
1. 文字色只能取上表出现过的组合；新增组合必须实测并写入本表。
2. `--np-signal`、`--np-safety`、`--np-grid` 永不作文字色。
3. 10px 及以下文字不得使用 `--np-ink-muted`（5.95 在 10px 下不可读），改用 `--np-ink`。
4. 状态不得只靠颜色区分（见 §6.6）。

---

## 3. 字体

### 3.1 家族

| 角色 | 首选（Google Fonts，开源） | 理由 | 备选 |
| --- | --- | --- | --- |
| Display / 标题 / 大数字 | **Inter** 400/500/600 | NASA 现行 Horizon Design System 用它做 display 与 heading；大 x-height，屏显稳 | `Archivo`、`IBM Plex Sans` |
| 正文 / UI | **Public Sans** 400/500/600 | 同属 Horizon 三族体系，正文可读性优于 Inter | `Source Sans 3`、`Noto Sans` |
| 等宽 / 数值 / 代码 / 坐标 | **DM Mono** 400/500 | Horizon 的 mono 选择；数字与 `0/O`、`1/l` 区分干净 | `IBM Plex Mono`、`JetBrains Mono` |
| 中文 | **Noto Sans SC** 400/500/700 | 与 Public Sans 同骨架、可自托管子集 | `Source Han Sans SC`、`PingFang SC`、`Hiragino Sans GB`、`Microsoft YaHei` |
| （可选）1975 复刻腔调 | **Nimbus Sans**（URW，Helvetica 度量克隆） | 手册正文规定 Helvetica Light/Medium；需要"手册味"的封面/图纸头可用 | `Helvetica Neue`、`Arial` |

### 3.2 加载方式（硬约束）

**运行时禁止请求远程字体。** 不允许 `<link href="https://fonts.googleapis.com/...">`、不允许 `@import url(...)`、不允许任何 CDN。

- 方式 A（推荐）：`@fontsource-variable/inter`、`@fontsource/public-sans`、`@fontsource/dm-mono`、`@fontsource/noto-sans-sc`，安装后在入口 `import` 具体 weight 子集，产物内自带 `@font-face` 与 woff2。
- 方式 B：把 woff2 放进 `public/fonts/`，自写 `@font-face`，`font-display: swap`，`unicode-range` 按 `latin` / `latin-ext` / `chinese-simplified` 拆分。
- 中文子集必须拆分（Noto Sans SC 全量约 10 MB+）；只打包实际出现的字符集时用 `subset` 工具生成，但**兜底栈必须能在字体缺失时保持版式不塌**。
- 首屏禁止 FOIT；`font-display: swap` + `size-adjust` 对齐回退字体（Inter 100%，Public Sans 100%，DM Mono 100%）。

### 3.3 字号阶梯（px / line-height / tracking / weight）

| Token | px / lh | Tracking | Weight | 用途 |
| --- | --- | --- | --- | --- |
| `--np-fs-micro` | 10 / 14 | `+0.12em` | 500 | 字段标签、刻度、坐标、图注编号（全大写） |
| `--np-fs-label` | 11 / 16 | `+0.08em` | 500 | 面板标题、表头、badge（全大写） |
| `--np-fs-body-s` | 12 / 18 | `+0.01em` | 400 | 表格单元、图例、密集 UI |
| `--np-fs-body` | 13 / 20 | `0` | 400 | 默认正文 |
| `--np-fs-body-l` | 15 / 24 | `-0.005em` | 400 | 长文（说明面板、finding note） |
| `--np-fs-title` | 18 / 24 | `-0.01em` | 500 | 面板主标题、卡片标题 |
| `--np-fs-h2` | 22 / 28 | `-0.015em` | 500 | 区块标题 |
| `--np-fs-h1` | 28 / 32 | `-0.02em` | 500 | 图纸标题（sheet title） |
| `--np-fs-readout` | 32 / 32 | `-0.02em` | 500 mono | 主读数（每屏最多 1 个） |
| `--np-fs-display` | 44 / 44 | `-0.03em` | 500 | 极端强调（每屏最多 1 个，通常不用） |

**排版规则**
1. **大写只用于 ≤11px 的标签**（`micro` / `label`）与 classification 印章。标题用句首大写（sentence case），中文标题不加 `text-transform`。
2. 中文不加大写转换、不加 `letter-spacing`（`letter-spacing: 0`），行高比同字号拉丁文多 2px（12/20、13/22、15/26）。
3. 数字一律 `font-variant-numeric: tabular-nums slashed-zero;`；表格数值列右对齐，单位单独一列或跟在值后用 `--np-ink-muted`（12px，与值之间 4px）。
4. 代码、ID、坐标、时间戳、hash 全部用 mono，**不缩写**；截断用中间省略（`SC-STR-…-014`），不用尾部省略。
5. 斜体全站禁用（技术印刷惯例）；变量名在正文中用 mono 表示。
6. 正文最大行宽 `68ch`，面板内 `42ch`；超长段落不分栏，因为分栏会破坏 12 列对齐。
7. 禁止 `text-align: justify`（产生河沟）。
8. 数字与单位之间不断行：`white-space: nowrap`。

### 3.4 回退栈（无 `@font-face` 也能用）

```css
--np-font-display: "Inter", "Helvetica Neue", Helvetica, "Nimbus Sans", Arial,
                   "PingFang SC", "Noto Sans SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
--np-font-body:    "Public Sans", "Inter", "Helvetica Neue", Helvetica, Arial,
                   "PingFang SC", "Noto Sans SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
--np-font-mono:    "DM Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Consolas,
                   "Noto Sans Mono CJK SC", "PingFang SC", monospace;
```

---

## 4. 网格与版式

### 4.1 单位与间距

- 基线单位 `--np-u: 4px`；间距阶梯 **4 / 8 / 12 / 16 / 24 / 32 / 48 / 64**，只用这些值。
- 行内间距 4/8；面板内边距 12；区块间距 16/24；图纸外边距 32。
- 所有尺寸为 4 的倍数；1px 边框不计入盒模型（`box-sizing: border-box`），因此内容区可能比标称少 1–2px —— 允许，不再补偿。

### 4.2 12 列技术图纸

| 属性 | ≥1440px | 1024–1439px | <1024px |
| --- | --- | --- | --- |
| 图纸最大宽 | 1440 | 100% | 100% |
| 外边距 | 32 | 24 | 16 |
| 列数 | 12 | 12 | 4 |
| 列间距 | 16 | 16 | 12 |
| 内容列宽 | `(W − 64 − 176) / 12` | 同比例 | `(W − 32 − 36) / 4` |

列跨度只用 **2 / 3 / 4 / 6 / 8 / 9 / 12**（避免 5、7、10、11 的碎列）。
默认三区：索引 `col 1–2`、画布 `col 3–9`、检查器 `col 10–12`。
`<1024px` 时折叠为单列：画布在上、检查器抽屉、索引为顶部横向 tab。

### 4.3 图纸框与分区标记

- 外框：`1px solid var(--np-rule)`，距视口边 `12px`；外框与内容之间再留 `12px`（合计 24px 净空）。
- 四角注册标记：`12px × 1px` 短线，两条一组，距角 4px，颜色 `--np-rule`。
- 分区标记（zone）：顶部 `8` 段（数字 `1–8`，自左向右），左侧 `6` 段（字母 `A–F`，自上向下）；每段长度按对应边均分（顶 `(W − 24) / 8`，左 `(H − 24) / 6`）；标记 10px mono，`--np-ink-muted`，居中于边框内侧 2px。坐标读作 `C-4`（左字母在前，顶数字在后）。
- 右侧与底部不重复分区（屏幕不是纸，重复会噪声）。

### 4.4 页头块（Header Block）

总高 **72px**（≥1280）/ **64px**（<1280）：标题区 `52px`（上内边距 6px）+ 字段条 `20px`。下边 `1.5px solid var(--np-rule)`，其下 4px 处再一条 `1px solid var(--np-rule-soft)`（双规线，纸图惯例）。

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ TITLE (28/32, Inter 500)                          [ STAGE CHIP ]  [ CLASS ]  │ 6–46px
│ SUBTITLE (11px, uppercase, +0.08em, muted)                                   │ 46–58px
├──────────────────────────────────────────────────────────────────────────────┤
│ SHEET NO.      REV   SCALE    DRAWN      CHECKED     DATE          ZONE      │ 52–72px
│ SC-STR-014     C     1:1      —          —           2026-10-21    C-4       │
└──────────────────────────────────────────────────────────────────────────────┘
```

字段条（20px 高）：标签 10px uppercase `--np-ink-muted`，值 11px mono `--np-ink`；标签与值基线对齐，字段间距 24px。

| 字段 | 内容规则 | 宽度 |
| --- | --- | --- |
| `SHEET NO.` | `SC-<DOMAIN 3>-<NNN>`，如 `SC-STR-014` | 96px |
| `REV` | 单字母 `A–Z`，每次内容变更 +1 | 32px |
| `SCALE` | `1:1` / `1:2` / `NTS`；屏幕图默认 `1:1`，"不按比例"时必写 `NTS` | 48px |
| `DRAWN` / `CHECKED` | 人名缩写，未知写 `—`（em dash，不写 `N/A`） | 72px |
| `DATE` | ISO `YYYY-MM-DD` | 88px |
| `ZONE` | 当前选中对象所在分区，如 `C-4`；无选中写 `—` | 40px |

**Classification 印章**：`96 × 18px`，`1px solid var(--np-alert-ink)`，文字 9px uppercase `+0.14em`、`--np-alert-ink`，右对齐，内容如 `INTERNAL` / `DRAFT` / `UNCLASSIFIED`。这是全站唯一允许 9px 字的地方。

**Stage chip**：见 §5.3；位于标题行右端，与印章间距 8px。

### 4.5 页脚块（Footer Block）

高 **32px**，上边 `1px solid var(--np-rule)`，左右内边距 12px，三区等分：

| 区 | 内容 | 字体 |
| --- | --- | --- |
| 左 | `STRUCTURECUMULUS · <build>` | 10px mono, muted |
| 中 | `DO NOT SCALE · VERIFY AGAINST SNAPSHOT <hash7>` | 10px mono, `--np-ink`（这条是硬性提醒，不 muted） |
| 右 | `SHEET 03/12 · 2026-10-21T09:14Z` | 10px mono, muted |

`DO NOT SCALE` 直接借用制图惯例（"DO NOT SCALE DRAWINGS"），在本项目里的含义是：图上尺寸不作为真值，真值来自 snapshot hash。

### 4.6 线宽与角

| Token | 值 | 用途 |
| --- | --- | --- |
| `--np-stroke-hair` | `1px` | 表格行、面板内分隔、装饰网格、刻度 |
| `--np-stroke` | `1.5px` | 面板外框、组件边框、页头下边、节点边框 |
| `--np-stroke-heavy` | `3px` | 图纸外框、区块分断、选中节点、readout 左边条、危险横幅上边 |

SVG 内部：`0.75 / 1.5 / 2.5`（配 `vector-effect="non-scaling-stroke"`），视觉上对应上表。不存在 2px 与 4px 的中间值。

**角**：结构面（panel / node / table / canvas / callout 方框）一律 `border-radius: 0`；只有 **badge / chip** 允许 `2px`；**callout 气泡**允许正圆（Ø16，唯一例外）。禁止 `4px` 以上的圆角、禁止胶囊形。

**阴影**：全站 `box-shadow: none`。层级只由线宽、灰度、纸张明度差表达。焦点环是 `outline`，不是阴影。

**渐变**：只允许**硬边重复条纹**（危险条带、失败角标、unknown 量表填充），即 `repeating-linear-gradient` 且每个色标都是硬断点（`0 8px` 而非 `0%, 8px`）。任何带插值过渡的渐变（线性/径向/锥形、发光、暗角、玻璃高光）一律禁止。

### 4.7 面板分割规则

- 相邻面板之间只用 **1px `--np-rule`** 分界，不叠加间距 + 双线。
- 同一面板内的分区用 `--np-rule-soft`，跨面板用 `--np-rule`。
- 不允许"卡片套卡片"超过两层；第三层改用分组标题 + 缩进 12px。

---

## 5. 组件规范

所有组件共用：
- 字体：标签 `--np-fs-label`（11px uppercase），正文 `--np-fs-body-s`（12px），数值 `--np-font-mono`。
- 焦点：`outline: 1px solid var(--np-signal-ink); outline-offset: 2px;`（CRT 下 `--np-signal`）。**不允许移除 outline**。
- 禁用态：`opacity: 0.4`，`cursor: not-allowed`，不改变颜色。
- 状态色映射（纸态 / CRT 态）：ok → `--np-ok-ink` / `--np-phosphor`；warn → `--np-warn-ink` / `--np-warn-ink`；fail → `--np-alert-ink` / `--np-alert`；info → `--np-caution-ink` / `--np-caution`；unknown → `--np-ink-muted` / `--np-lit-muted`。
- 组件只读 `--np-status-*` 语义别名，不直接引用色相 token（换主题时不需要改组件）。

---

### 5.1 Panel

| 属性 | 值 |
| --- | --- |
| 最小尺寸 | `min-height: 96px`，宽随列 |
| 外框 | `1px solid var(--np-rule)`，`radius 0`，`background: var(--np-paper)` |
| 面板头 | 高 `28px`，`background: var(--np-paper-deep)`，下边 `1px solid var(--np-rule)`，内边距 `0 8px`，`display:flex; align-items:center; gap:8px` |
| 头标题 | `11px / 16`, `500`, uppercase, `+0.08em`, `--np-ink` |
| 头右侧 | 计数 `10px mono muted` + 可选操作按钮 16×16 |
| 主体 | `padding: 12px 12px 14px`；子区块间距 12px |
| 紧凑态 | `data-density="compact"`：头 24px，主体 `8px 10px 10px` |
| 折叠态 | 只留头，高 28px；头右端 `＋` / `－` 16×16，1px 描边 |
| 焦点 | 外框内 1px `--np-signal-ink`，`inset 0 0 0 1px`（用 `outline-offset:-1px`） |
| 错误态 | 左边 `3px solid var(--np-alert)`，头右侧 `!` 12×12，标题不变色 |
| 加载态 | 头右侧 3 个 `3×3` 方块，按 0/160/320ms 依次 `opacity 0.25 → 1`，周期 960ms（**不用 skeleton 渐变**） |

**不得**：圆角、投影、hover 抬升、透明毛玻璃、彩色标题底。

---

### 5.2 Readout（读数）

```
┌ 3px 状态条
│ MATCH COVERAGE             ← 10px uppercase +0.12em, muted
│ 98.2 %                     ← 32px mono 500, tabular
│ ▲ +0.4 vs prev             ← 11px mono, ok-ink (可选)
```

| 属性 | 值 |
| --- | --- |
| 结构 | `display:grid; grid-template-columns: 3px 1fr;` 左条 + 内容 |
| 左条 | 宽 `3px`，高 100%，颜色随状态（nominal = `--np-rule`；active = `--np-signal`；fail = `--np-alert`；unknown = 虚线 2/2 `--np-rule`） |
| 内容内边距 | `8px 10px`；标签与值间距 `4px` |
| 标签 | `--np-fs-micro`（10/14），uppercase，`+0.12em`，`--np-ink-muted`；中文标签不加 tracking |
| 值 | `--np-font-mono`，`32/32`，`500`，`--np-ink`，`font-variant-numeric: tabular-nums slashed-zero` |
| 单位 | `12px`，`--np-ink-muted`，与值间距 `4px`，基线对齐 |
| 最小值 | `min-width: 104px`；同组 readout 等宽（取组内最大值） |
| 值宽度 | 预留 `min-width: 6ch`，避免更新时抖动 |
| 无值 | 显示 `—`（em dash），`--np-ink-muted`，**不显示 0、不显示空白** |
| delta | `11px mono`；升 `▲`、降 `▼`、平 `–`；颜色只表示好坏方向，不表示增减方向 |

**每屏只允许一个 `32px` 主读数**；其余降为 `22/26 mono`（`--np-fs-h2` 字号但用 mono）。

---

### 5.3 Badge / Status chip

| 属性 | 值 |
| --- | --- |
| 高 | `18px`（默认）/ `16px`（`sm`）/ `20px`（`lg`） |
| 内边距 | `0 6px` |
| 边框 | `1px solid currentColor` |
| 圆角 | `2px`（唯一允许的圆角值） |
| 字体 | `10px / 14`，`600`，uppercase，`+0.1em` |
| 前置标记 | `4×4` 方块，`margin-right: 5px`，`background: currentColor` |
| 颜色对 | ok: 文字/边 `--np-ok-ink`，底 `--np-tint-ok`；warn: `--np-warn-ink` / `--np-tint-safety`；fail: `--np-alert-ink` / `--np-tint-alert`；info: `--np-caution-ink` / `--np-tint-caution`；neutral: `--np-ink-muted` / `--np-tint-neutral`；unknown: `--np-ink-muted`，底透明，虚线边 `2 2` |
| 实心变体 | `data-solid`：底 = 状态色（`--np-alert` 等），文字 `--np-paper`（红底 4.89 ✅、蓝底用 `#005FAF`）；黄底必须黑字（12.48） |
| 中文 | 10px 中文偏小，中文状态文字改用 `11px`，不 uppercase，不 tracking |
| 交互 | 可点 chip 加 `cursor: pointer`，hover 时底色加深一档（用对应 tint 的深色版），不加阴影 |

固定文案（不要自创同义词）：`FORMED` / `BROKEN` / `STALE` / `PENDING` / `UNKNOWN` / `SKIPPED`。

---

### 5.4 Legend（图例）

| 属性 | 值 |
| --- | --- |
| 容器 | `padding: 8px 10px`，上边 `1px solid var(--np-rule)`（标题行）+ 每行 `1px --np-rule-soft` |
| 标题 | `10px uppercase +0.12em`，`--np-ink-muted`，`margin-bottom: 6px` |
| 行高 | `18px`，行间距 `6px` |
| 色块样本 | `12×12`，`1px solid var(--np-rule)`；线样本 `24×8`（线在中间，1.5px） |
| 文本 | `11px / 16`，`--np-ink`；右端可附计数 `10px mono muted` |
| 列 | ≤6 项单列；>6 项双列，列间距 16px，按 layer 分组 |
| 溢出 | 最多 8 行，超出显示 `+N MORE`（10px，可点开） |
| 必填性 | 图上出现 ≥2 种 layer 颜色时，图例**必须**出现，位置固定画布右下，距边 12px |

---

### 5.5 Data table

| 属性 | 值 |
| --- | --- |
| 行高 | `28px`（默认）/ `24px`（compact）/ `32px`（comfortable） |
| 表头 | 高 `24px`，`background: var(--np-paper-deep)`，下边 `1.5px solid var(--np-rule)`，`position: sticky; top: 0` |
| 表头文字 | `11px uppercase +0.08em`，`--np-ink`；可排序列右端 `8×5` 实心三角 |
| 单元格 | `padding: 0 8px`；首列 `padding-left: 12px` |
| 分隔 | 行间 `1px solid var(--np-rule-soft)`；分组间 `2px solid var(--np-rule)`（**不用斑马纹**） |
| 对齐 | 文本左、数值右、状态列左（chip）、ID 列左（mono） |
| 列宽 | 显式声明；ID 列 `min 96px`，状态列 `min 88px`，数值列 `min 72px` |
| 选中行 | 左 `2px solid var(--np-signal)` + 底 `--np-tint-signal`；同时保留 1px 焦点内框 |
| hover 行 | 底 `--np-tint-neutral`（不做位移、不做阴影） |
| 排序 | 三角 `8×5`，激活列文字 `--np-ink`，非激活 `--np-ink-muted` |
| 空态 | 高 `40px`，居中 `12px` 文字 `NO ROWS`，`--np-ink-muted` |
| 截断 | mono 内容中间省略；文本尾部省略；`title` 属性给全值 |
| 粘性首列 | 宽表启用，首列 `background: var(--np-paper)`，右边 `1px --np-rule` |

---

### 5.6 Node

| 属性 | 值 |
| --- | --- |
| 尺寸 | 默认 `160 × 48`；最小 `96 × 40`；最大 `240 × 72` |
| 内边距 | `6px 8px` |
| 边框 | `1.5px solid var(--np-rule)`；`radius 0` |
| 标题 | `12px / 16`，`500`，Inter，`--np-ink`，单行省略 |
| 副行 | `10px mono`，`--np-ink-muted`，内容为 ID 或坐标，`margin-top: 2px` |
| 端口 | `6×6` 方块，居中贴边，`background: var(--np-paper)`，`1px solid var(--np-rule)`；hover 填 `--np-signal` |
| 选中 | 边框 `2px solid var(--np-signal-ink)` + 四角注册刻度（`6×1px`，距角 3px）+ 图例同步高亮 |
| 激活（运行中） | 边框 `1.5px solid var(--np-ok-ink)` + 左侧 `3px` 实心条；**不闪、不呼吸**（见 §7.3） |
| 失败 | 边框 `1.5px solid var(--np-alert)` + 右上角 `12×12` 45° 斜纹角标（4px 周期）+ 副行前置 `!` |
| 未知 | 边框 `1px dashed var(--np-rule)`（dash `4 3`）+ 标题 `--np-ink-muted` |
| 封存（sealed） | 双线：外 `1px`，内 `1px`，间距 `2px`（用 `box-shadow: inset 0 0 0 2px var(--np-paper), inset 0 0 0 3px var(--np-rule)`）——表示不可再变的 `MatchResult` |
| 分组 | 不画容器框；用 `1px dashed var(--np-rule)` 包围 + 左上角 `10px` 分组名（在框线上，纸色底遮线） |

---

### 5.7 Connector（正交连线）

| 属性 | 值 |
| --- | --- |
| 走线 | 只允许水平 / 垂直段，转角 `radius 0`；每段最短 `12px` |
| 线宽 | `1.5px` 默认；`1px` 次要/背景关系；`2px` 选中；`3px` 总线（bus） |
| 颜色 | 跟随来源节点的 layer 色（§6.4）；中性关系用 `--np-rule` |
| 箭头 | 实心三角 `8 × 6`，无描边，贴目标端口；箭头尖端对齐端口外沿 `2px` |
| 端点 | 起点 `3×3` 方块（无箭头），终点箭头；双向关系两端箭头 |
| 交叉 | 不画跳线圆弧；**竖向线在交叉处断开 6px**（`stroke-dasharray` 或分段 path），横线连续 |
| 拐角 | 直角；若必须避让，用 2 段 45°? **禁止**——只允许再加一段正交折线 |
| 标签 | `10px mono`，置于线段**上方** `6px`，水平段居中；竖段标签旋转 `-90°` 或改为引线标注（优先引线） |
| 标签底 | 纸色描边 `paint-order: stroke`，`stroke-width: 3px`，避免线穿字 |
| 自环 | 从右侧出、上折、回左侧入，环宽 ≥ 24px |
| 选中 | 线宽 +0.5px 且换 `--np-signal-ink`；不加发光 |

---

### 5.8 Callout（标注）

| 属性 | 值 |
| --- | --- |
| 气泡 | Ø `16px` 正圆，`1px solid var(--np-rule)`，`background: var(--np-paper)`，内含 `9px mono 500` 序号 |
| 引线 | `1px solid var(--np-rule)`，正交：先水平 `≥8px`，再垂直；末端 `3×3` 实心方块（**不用箭头**） |
| 文本块 | 宽 `≤ 240px`，`11px / 15`，`--np-ink`；标题（可选）`10px uppercase +0.1em` muted |
| 文本与引线间距 | `6px` |
| 对齐 | 文本块左边缘与图元左边缘对齐；不跨出画布 12px 安全边 |
| hover/选中 | 气泡底 `--np-tint-signal`、边 `--np-signal-ink`；对应图元同步 1px 描边 |
| 遮挡 | 标注不得覆盖节点标题；冲突时自动改为右上/右侧 |
| 编号 | 同一图内 1..N 连续，与右侧 finding 列表序号一致 |

面板内的行内注解（不引出画布）：`1px dashed var(--np-rule-soft)` 左边框 + `8px` 内边距 + 11px 文字。

---

### 5.9 Hazard stripe（危险条带）

| 属性 | 值 |
| --- | --- |
| 图案 | 45° 斜纹，`8px` 黄 + `8px` 墨，硬边（无渐变过渡） |
| CSS | `repeating-linear-gradient(45deg, var(--np-safety) 0 8px, var(--np-ink) 8px 16px)` |
| CRT | `var(--np-safety)` = `#FFD84D`，墨 = `var(--np-void)` |
| 高度 | `8px`（分隔）/ `16px`（横幅）/ `24px`（全宽警告，仅用于不可逆操作确认） |
| 边框 | 上下各 `1px solid var(--np-ink)`（纸）/ `--np-rule`（CRT） |
| 条带内文字 | 仅 16px 及以上；`11px uppercase 600 +0.12em`，黑底用黄字需加黑底块（黄字在斜纹上不可读） |
| 使用上限 | 视口面积 `≤ 5%`；一屏最多 1 处 |
| 禁用 | 装饰性使用、hover 出现、循环动画、斜纹上的彩色文字 |

---

### 5.10 Gauge / Scale（量表）

只做**线性**量表。径向表、仪表盘、半圆表一律禁止（装饰性）。

| 属性 | 值 |
| --- | --- |
| 轨道 | 高 `6px`，`1px solid var(--np-rule)`，底 `--np-paper-deep` |
| 填充 | 平涂 `--np-ink`；超阈值改 `--np-alert`；无渐变、无圆头 |
| 刻度 | 每 `10%` 一条 `1px × 4px`，`--np-rule`；主刻度（0/50/100）`1px × 8px` |
| 刻度标签 | `10px mono`，`--np-ink-muted`，居刻度下方 `4px` |
| 容差带 | `--np-tint-ok` 填充，`1px` 上下边，覆盖区间 |
| 阈值标记 | `2px × 10px`，`--np-alert`，上下各出轨道 `2px` |
| 宽 | `min 120px`，默认 `240px` |
| 数值 | **必须**伴随 readout 或行内数值（`11px mono`），量表本身不承载精确值 |
| 方向 | 左→右增长；不允许从右向左（除时间轴倒序，见 §5.11） |
| 状态 | nominal / warn（填充 `--np-warn-ink`）/ fail（`--np-alert`）/ unknown（填充斜纹 45°，`4px` 周期，`--np-rule`） |

---

### 5.11 Timeline（时间线）

| 属性 | 值 |
| --- | --- |
| 行高 | `32px`；多轨时每轨 `24px` |
| 主轴 | `1px solid var(--np-rule)`，贯穿宽度 |
| 事件刻度 | `1px × 8px`（向上）+ 可选 `1px × 4px`（向下，次级） |
| 事件点 | `6×6` 方块（不用圆点），`1px` 描边，填充状态色 |
| 标签 | `10px mono`；密集时上下交错（偶数上、奇数下），间距 `4px` |
| 播放头 | `1px solid var(--np-signal-ink)`，高 `24px`，顶部 `6×6` 实心方块手柄 |
| 区间选择 | `1px` 边框 + `--np-tint-caution` 底，两端各 `2px` 竖把手 |
| 时间刻度 | 顶部 `10px mono`，ISO 或相对 `T+mm:ss`；单位一致，不混用 |
| 吸附 | 事件吸附到 `4px` 网格；不吸附到任意像素 |
| 缩放 | 1h / 1d / 1w / all 四档，切换不改变行高 |
| 空段 | 不画斜线、不画"无数据"字样；留空 + 在轴上标注 `NO EVENTS` 一次 |
| 顺序 | 左→右为时间正序；倒序时轴端必须有 `◀ OLDER` 方向标记 |

---

### 5.12 Stepper（步骤条）

用于 `DESIGN → PROBE → FORM → SETTLE → STALE`（默认 5 步）。

| 属性 | 值 |
| --- | --- |
| 块高 | `52px`（含标签）/ `32px`（仅节点，compact） |
| 节点 | `14×14` 方块，`1px solid var(--np-rule)`，内含 `9px mono` 序号，`background: var(--np-paper)` |
| 连接线 | `1px solid var(--np-rule)`，节点之间水平贯通，长度自适应 |
| 标签 | `11px / 16`，节点下方 `6px`，水平居中；块宽不足以居中时改为左对齐节点左缘，允许 2 行但块高固定 `52px`（不因换行变高） |
| 完成 | 节点填充 `--np-ink`，序号 `--np-paper`；连线 `--np-ink`；标签 `--np-ink` |
| 当前 | 节点 `2px solid var(--np-signal-ink)` + 四角刻度；标签 `600`；连线到当前段为 `--np-signal-ink` |
| 待办 | 节点 `1px dashed var(--np-rule)`；标签 `--np-ink-muted` |
| 失败 | 节点填充 `--np-alert`，序号纸色；右侧 `8px` 处 `!` 标记；标签 `--np-alert-ink` |
| 跳过 | 节点透明底 + `1px` 斜线穿过（`-45°`，`1px`），标签 `--np-ink-muted` + `(SKIPPED)` |
| 垂直变体 | 宽 `220px`，节点在左、标签在右，行高 `32px`，连线垂直 |
| 交互 | 可点步骤切换视图；不可点步骤 `cursor: default` 且不加 hover 效果 |

---

## 6. 图示规则（React / SVG）

### 6.1 坐标系与像素对齐

- `viewBox` 用整数；画布 `preserveAspectRatio="xMidYMid meet"`。
- 1px 线画在整数坐标会导致 2px 灰边：**奇数线宽偏移 0.5**（`x + 0.5`），偶数线宽用整数坐标。
- 纯水平/垂直的 1px 线与矩形边框加 `shape-rendering="crispEdges"`；曲线与文字不加。
- 所有坐标吸附 `4px` 网格；节点宽高为 4 的倍数。

### 6.2 线宽（SVG 单位）

| 语义 | 值 | 用途 |
| --- | --- | --- |
| 装饰 | `0.75` | 网格、刻度、辅助线 |
| 基础 | `1.5` | 连线、节点边框 |
| 强调 | `2` | 选中、当前路径 |
| 重型 | `2.5` | 总线、分组外框、区块分断 |

全部加 `vector-effect="non-scaling-stroke"`，缩放时线宽不变。

### 6.3 图元形状（只此 6 种）

| 形状 | 尺寸 | 含义 |
| --- | --- | --- |
| 矩形 | `160×48`（可 96–240） | 单元 / 段 / 检查（默认） |
| 双线矩形 | 内缩 `2px` 再描 1px | 封存结果（不可变） |
| 切角矩形 | 右上切 `8px` 斜角 | 扩展 / SPI / 外部实现 |
| 菱形 | `72×72` | 判定（claim 冲突、谓词分支） |
| 横条 | `120×8` | 数据 / 通道 / 覆盖区 |
| 方括号 | `12px` 开口 | 分组边界（不闭合，避免与节点框混淆） |

禁止：圆角矩形、云朵、圆柱、人物、图标化节点（图标只能出现在节点**内部** 12×12，不能替代形状）。

### 6.4 按系统层着色（最多 5 层）

| 层 | 语义 | 纸态 | CRT 态 |
| --- | --- | --- | --- |
| `L1 kernel` | 核心、不变量 | `--np-ink` `#14160F` | `--np-lit` `#D6E6CC` |
| `L2 adapter` | 宿主绑定、平台 | `--np-caution-ink` `#005FAF` | `#4FB0FF` |
| `L3 document` | 蓝图、数据、段库 | `--np-ok-ink` `#00683F` | `#4BFF87` |
| `L4 remedy` | 修复、预览、overlay | `--np-signal-ink` `#A84600` | `#FF9A3C` |
| `L5 diagnostic` | finding、告警 | `--np-alert-ink` `#B30E20` | `#FF5A5A` |

规则：默认全部 `L1`；**只有跨层关系才着色**，单层图不配色。着色时图例必现（§5.4）。同图最多 3 层着色，超出改用分组 + 标签。

### 6.5 标签放置（优先级固定）

1. 节点标签：节点内部，顶对齐，左右内边距 8px。
2. 连线标签：线上方 6px，水平居中；竖线则放右侧 6px，`writing-mode: vertical-rl` 仅在空间不足时使用。
3. 图元外部注记：优先右侧 → 下方 → 上方 → 左侧；距离 `8px`。
4. 引线阈值：标签中心距图元 > `24px` 时必须画引线（§5.8）。
5. 标签不得旋转（除坐标轴刻度与竖线标签）；不得跨线；不得压端口。
6. 避让：两个标签间距 < `8px` 时，按上述优先级换位；仍冲突则合并为 `A / B` 或提升为 callout。

### 6.6 状态表达（四态，三重编码）

| 状态 | 颜色 | 形状 / 线型 | 文字 |
| --- | --- | --- | --- |
| `selected` | `--np-signal-ink`（`--np-signal`） | `2px` 实线 + 四角 `6×1px` 刻度 | 图例同步 + `ZONE` 字段更新 |
| `active` | `--np-ok-ink`（`--np-phosphor`） | `1.5px` 实线 + 左 `3px` 实心条 | chip `ACTIVE` |
| `failed` | `--np-alert`（`#FF5A5A`） | `1.5px` 实线 + 右上 `12×12` 斜纹角标 + 副行 `!` | chip `FAILED` |
| `unknown` | `--np-ink-muted` | `1px` 虚线 `4 3` + `opacity .6` 文字 | chip `UNKNOWN` |

**硬规则**：任何状态都必须同时具备"颜色 + 线型/形状 + 文本"三者中的至少两项。`selected` 与 `active` 是唯一一对**只靠色相区分**的邻居状态（橙 vs 绿），因此二者的形状标记不可互换：selected = 四角刻度，active = 左实心条。删掉任一形状标记即视为实现错误。

### 6.7 React 结构建议（不强制，但组件边界按此切）

```
<DiagramCanvas viewBox="0 0 960 540">
  <GridLayer />            // 0.75px, --np-grid, aria-hidden
  <ConnectorLayer />       // 先画线，后画节点
  <NodeLayer />
  <CalloutLayer />
  <SelectionOverlay />     // 只画刻度与 2px 框，不改节点自身
</DiagramCanvas>
```

- 状态用 `data-state="selected|active|failed|unknown"`，CSS 选择器着色；不要为每个状态生成不同 JSX 结构（否则 React 重渲染会丢焦点）。
- 交互命中区用 `<rect fill="transparent" pointer-events="all">`，不小于 `24×24`（可访问性下限），与实际图元分离。
- `<title>` + `<desc>` 必须存在；`role="img"` + `aria-label` 给整图摘要。
- 导出 PNG 时背景强制 `--np-paper`（透明底打印会变成黑）。

---

## 7. 动效

### 7.1 时长与缓动

| Token | 值 | 用途 |
| --- | --- | --- |
| `--np-dur-micro` | `80ms` | hover、focus、chip 变色 |
| `--np-dur-state` | `140ms` | 选中、状态切换、行高亮 |
| `--np-dur-panel` | `220ms` | 面板展开/折叠、抽屉 |
| `--np-dur-view` | `320ms` | 视图切换、布局重排 |
| `--np-ease` | `cubic-bezier(0.2, 0, 0, 1)` | 所有进入/状态变化 |
| `--np-ease-exit` | `cubic-bezier(0.3, 0, 0.8, 0.15)` | 退出/收起 |
| `--np-ease-linear` | `linear` | 仅扫描线、进度条 |

上限 `400ms`。禁止 spring、bounce、overshoot、`ease-in-out` 默认曲线。

**可动画属性白名单**：`opacity`、`color`、`background-color`、`border-color`、`transform: translate/scale(≤1.02)`、`stroke-dashoffset`、`width/height`（仅面板）。禁止动画 `box-shadow`、`filter`、`top/left`。

### 7.2 扫描线（CRT 专属）

- 一条 `1px` 线，`2px` 周期（`repeating-linear-gradient` 硬边），`opacity: 0.05`；
- 垂直移动，`8s`，`linear`，`infinite`；
- 只在 `[data-theme="crt"]` 且 `prefers-reduced-motion: no-preference` 时渲染；
- 不遮挡文字（`pointer-events: none`，`z-index` 在内容之上但透明度 ≤0.05）；
- 禁止色差（RGB shift）、禁止桶形畸变、禁止闪烁噪点。

### 7.3 闪烁

- 硬上限 **1.6 Hz**（周期 ≥ 625ms；WCAG 2.3.1 的 3 次/秒是绝对红线）。
- 只允许"需要确认"的状态闪：未确认的 fail 用 `1px` 外环 `opacity 0.35 ↔ 1`，周期 `1200ms`，最多 `6` 次（7.2s）后自动转常亮。
- 禁止闪烁：文字、背景大面积、图标填充、任何 `> 24×24` 的区域。
- `active` 状态**不呼吸**（§5.6）；运行中用静态左条 + readout 数字变化表达。

### 7.4 过渡与数据更新

- 数字变化不做滚动计数（rolling number），直接切换；变化瞬间加 `80ms` 的 `--np-tint-signal` 底闪一次。
- 图布局变化用 `transform` 过渡 `220ms`；节点位置不可用 `left/top` 动画。
- 表格排序不做行飞入；只做 `140ms` 背景高亮。
- 首屏不做逐元素入场动画（`stagger` 禁用）。

### 7.5 `prefers-reduced-motion: reduce`

| 项 | 行为 |
| --- | --- |
| 所有 transition / animation | `0.01ms`（不是 0，保留 transitionend 事件） |
| 扫描线 | 不渲染 |
| 闪烁 | 改为静态双环（`2px` + 间隔 `2px` 的 `1px` 环） |
| 播放头 | 不自动跟随；只在用户操作时跳转 |
| 图表绘制 | 不做 draw-on，直接终态 |
| 布局变化 | 直接跳变 |

---

## 8. Do / Don't

| # | Do | Don't |
| --- | --- | --- |
| 1 | 用 `1px` 结构线 + 灰度分区 | 用投影、圆角卡片、毛玻璃分层 |
| 2 | 每个数值带单位、每个图元带标签 | 出现无标签的色块、线、点 |
| 3 | 所有元素吸附 12 列 + 4px 基线 | "视觉居中"式自由摆放 |
| 4 | 一屏 1 个信号色 + 1 个状态色 | 橙、黄、绿、蓝同时出现在一张图 |
| 5 | 状态 = 颜色 + 线型 + 文字三重编码 | 只用颜色区分 FORM/FORMED/STALE |
| 6 | 连线只走正交，交叉处断 6px | 斜线、贝塞尔曲线、跳线圆弧 |
| 7 | 数字用 mono、`tabular-nums`、右对齐 | 比例字体居中数字，更新时宽度抖动 |
| 8 | 转灰度后仍可读（复印测试） | 信息只存在于发光或色相里 |
| 9 | 靠字号/字重/线宽建立层级 | 靠大留白或卡片堆叠建立层级 |
| 10 | 动效只标示状态变化，≤220ms | 装饰性循环动画、入场 stagger、数字滚动 |

---

## 9. CSS 变量块（可直接粘贴）

```css
/* ============================================================
   NASA-punk Technical Visualization Suite — tokens v1.0
   No @font-face here: the app self-hosts fonts (§3.2).
   ============================================================ */

:root {
  /* ---- surfaces / ink (paper) ---- */
  --np-paper:        #F2EFE6;
  --np-paper-deep:   #E6E2D4;
  --np-ink:          #14160F;
  --np-ink-muted:    #5C5B50;

  /* ---- lines ---- */
  --np-grid:         #C6C0AE;  /* decorative only, 1.58:1 */
  --np-rule-soft:    #B6AF9C;
  --np-rule:         #7E7A6A;  /* structural, 3.74:1 */

  /* ---- signal ---- */
  --np-signal:       #FF8200;  /* fill / marks >= 2px only, 2.16:1 */
  --np-signal-ink:   #A84600;  /* text / 1px stroke, 5.16:1 */
  --np-safety:       #FFD100;  /* hazard stripe fill only */
  --np-ok-ink:       #00683F;  /* 5.98:1 */
  --np-alert:        #CE1126;  /* fill */
  --np-alert-ink:    #B30E20;  /* text, 6.09:1 */
  --np-caution:      #0072CE;  /* fill (never text bg) */
  --np-caution-ink:  #005FAF;  /* text, 5.61:1 */
  --np-warn-ink:     #7A4E00;  /* text, 6.26:1 */

  /* ---- flat tints ---- */
  --np-tint-signal:  #F7E3CC;
  --np-tint-safety:  #F6EDC6;
  --np-tint-alert:   #F0D8DA;
  --np-tint-caution: #D8E3EE;
  --np-tint-ok:      #D8E5DA;
  --np-tint-neutral: #E3DFD1;

  /* ---- semantic aliases (components use these, not raw hues) ---- */
  --np-bg:           var(--np-paper);
  --np-surface:      var(--np-paper-deep);
  --np-fg:           var(--np-ink);
  --np-fg-muted:     var(--np-ink-muted);
  --np-line:         var(--np-rule);
  --np-line-soft:    var(--np-rule-soft);
  --np-accent:       var(--np-signal-ink);
  --np-status-ok:    var(--np-ok-ink);
  --np-status-warn:  var(--np-warn-ink);
  --np-status-fail:  var(--np-alert-ink);
  --np-status-info:  var(--np-caution-ink);
  --np-status-unknown: var(--np-ink-muted);

  /* ---- type ---- */
  --np-font-display: "Inter", "Helvetica Neue", Helvetica, "Nimbus Sans", Arial,
                     "PingFang SC", "Noto Sans SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  --np-font-body:    "Public Sans", "Inter", "Helvetica Neue", Helvetica, Arial,
                     "PingFang SC", "Noto Sans SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  --np-font-mono:    "DM Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Consolas,
                     "Noto Sans Mono CJK SC", "PingFang SC", monospace;

  --np-fs-micro:    10px;  --np-lh-micro:  14px;  --np-tr-micro: 0.12em;
  --np-fs-label:    11px;  --np-lh-label:  16px;  --np-tr-label: 0.08em;
  --np-fs-body-s:   12px;  --np-lh-body-s: 18px;
  --np-fs-body:     13px;  --np-lh-body:   20px;
  --np-fs-body-l:   15px;  --np-lh-body-l: 24px;
  --np-fs-title:    18px;  --np-lh-title:  24px;
  --np-fs-h2:       22px;  --np-lh-h2:     28px;
  --np-fs-h1:       28px;  --np-lh-h1:     32px;
  --np-fs-readout:  32px;  --np-lh-readout:32px;
  --np-fs-display:  44px;  --np-lh-display:44px;

  /* ---- spacing / grid ---- */
  --np-u:            4px;
  --np-sp-1:         4px;
  --np-sp-2:         8px;
  --np-sp-3:         12px;
  --np-sp-4:         16px;
  --np-sp-6:         24px;
  --np-sp-8:         32px;
  --np-sp-12:        48px;
  --np-sp-16:        64px;
  --np-cols:         12;
  --np-gutter:       16px;
  --np-margin:       32px;
  --np-sheet-max:    1440px;
  --np-panel-header: 28px;
  --np-header:       72px;
  --np-footer:       32px;

  /* ---- strokes / radius ---- */
  --np-stroke-hair:  1px;
  --np-stroke:       1.5px;
  --np-stroke-heavy: 3px;
  --np-radius:       0px;
  --np-radius-chip:  2px;

  /* ---- motion ---- */
  --np-dur-micro:    80ms;
  --np-dur-state:    140ms;
  --np-dur-panel:    220ms;
  --np-dur-view:     320ms;
  --np-ease:         cubic-bezier(0.2, 0, 0, 1);
  --np-ease-exit:    cubic-bezier(0.3, 0, 0.8, 0.15);
  --np-ease-linear:  linear;

  /* ---- elevation: none, by policy ---- */
  --np-shadow: none;
}

[data-theme="crt"] {
  --np-paper:        #0A0D0A;   /* alias kept so components stay theme-agnostic */
  --np-paper-deep:   #111711;
  --np-void:         #0A0D0A;
  --np-void-deep:    #111711;
  --np-ink:          #D6E6CC;
  --np-ink-muted:    #8C9C86;
  --np-lit:          #D6E6CC;
  --np-lit-muted:    #8C9C86;

  --np-grid:         #22301F;
  --np-rule-soft:    #2C3A29;
  --np-rule:         #546B4E;

  --np-signal:       #FF9A3C;
  --np-signal-ink:   #FF9A3C;
  --np-safety:       #FFD84D;
  --np-phosphor:     #4BFF87;
  --np-ok-ink:       #4BFF87;
  --np-alert:        #FF5A5A;
  --np-alert-ink:    #FF5A5A;
  --np-caution:      #4FB0FF;
  --np-caution-ink:  #4FB0FF;
  --np-warn-ink:     #FFC24D;

  --np-tint-signal:  #2A1B0C;
  --np-tint-safety:  #2A2212;
  --np-tint-alert:   #2A1315;
  --np-tint-caution: #0E1D2C;
  --np-tint-ok:      #10241A;
  --np-tint-neutral: #1A2118;
}

/* ---- reusable primitives ---- */

.np-sheet {
  background: var(--np-bg);
  color: var(--np-fg);
  font-family: var(--np-font-body);
  font-size: var(--np-fs-body);
  line-height: var(--np-lh-body);
  max-width: var(--np-sheet-max);
  margin-inline: auto;
  padding: var(--np-margin);
}

.np-hatch {
  background-image: repeating-linear-gradient(
    45deg,
    var(--np-safety) 0 8px,
    var(--np-ink) 8px 16px
  );
  border-block: 1px solid var(--np-ink);
}

.np-hatch-fail { /* 12x12 corner flag on failed nodes */
  background-image: repeating-linear-gradient(
    45deg,
    var(--np-alert) 0 4px,
    transparent 4px 8px
  );
}

.np-num {
  font-family: var(--np-font-mono);
  font-variant-numeric: tabular-nums slashed-zero;
  font-feature-settings: "zero" 1;
}

.np-label {
  font-family: var(--np-font-body);
  font-size: var(--np-fs-label);
  line-height: var(--np-lh-label);
  font-weight: 500;
  letter-spacing: var(--np-tr-label);
  text-transform: uppercase;
  color: var(--np-fg-muted);
}

.np-focusable:focus-visible {
  outline: 1px solid var(--np-signal-ink);
  outline-offset: 2px;
}

.np-scanline { /* CRT only; texture is static, the sweep is the only motion */
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  background-image: repeating-linear-gradient(
    to bottom,
    var(--np-lit) 0 1px,
    transparent 1px 3px
  );
  opacity: 0.05;
}

.np-scanline::after {
  content: "";
  position: absolute;
  inset-inline: 0;
  top: 0;
  height: 100%;
  border-top: 1px solid var(--np-lit);
  /* the sweep inherits the parent's 0.05 opacity — do not raise it */
  animation: np-sweep 8s linear infinite;
}

@keyframes np-sweep {
  from { transform: translateY(-100%); }
  to   { transform: translateY(100%); }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
  .np-scanline { display: none; }
}
```

**中文栈注意**：`text-transform: uppercase` 对中文无效果，但会改变混排里的拉丁词；中文标签元素不要加 `.np-label`，改用：

```css
.np-label-zh {
  font-size: 11px;
  line-height: 18px;
  font-weight: 500;
  letter-spacing: 0;
  color: var(--np-fg-muted);
}
```

---

## 10. Sources

**1975 年 NASA 图形标准手册（一手）**
1. NASA Graphics Standards Manual, NHB 1430.2（1976-01 版 PDF，公有领域）— https://nasa.gov/wp-content/uploads/2015/01/nasa_graphics_manual_nhb_1430-2_jan_1976.pdf
2. Internet Archive 全本 — https://archive.org/details/NASA_Graphics_Standards_Manual
3. NASA 品牌指南（现行 Horizon Design System：Inter / Public Sans / DM Mono；logotype 颜色限制）— https://www.nasa.gov/nasa-brand-center/brand-guidelines/
4. NASA，“NASA's Iconic Logotype Began with 'Shock and Surprise'”（Danne & Blackburn、1975 手册缘起）— https://www.nasa.gov/wp-content/uploads/2025/09/appel-nasas-iconic-logotype-began-with-%E2%80%98shock-and-surprise-1.pdf
5. Slate，2015 年重印报道（NASA Red = Pantone 179，据 Danne & Blackburn 设计师 Stephen Loges）— https://slate.com/human-interest/2015/09/nasas-1975-graphics-standards-manual-is-reissued-via-kickstarter-by-jesse-reed-and-hamish-smyth-of-pentagram.html
6. MoMA 藏品记录（1976 印本，版本/材质信息）— https://www.moma.org/collection/works/467172

**任务控制室与 CRT（一手/机构）**
7. H. C. Hendrickson, “The display/control complex of the Manned Space Mission Control Center”, *Information Display*, 1967（1.75 in × 4.75 in 模块网格；黑白极性开关与数据密度）— https://sid.onlinelibrary.wiley.com/doi/full/10.1002/j.2637-496X.1967.tb01263.x
8. NASA JSC, Apollo Mission Control Center Restoration（控制台、CRT 复原）— https://www.nasa.gov/johnson/history/apollo-mcc-restoration/
9. NASA, “Display Systems of the Mission Operations Control Room”（MOCR 显示系统、投影绘图）— https://www.nasa.gov/wp-content/uploads/2025/09/apollomc-habshaer.pdf
10. Phosphor Technology, CRT phosphors 表（P1 峰值 525 nm、CIE 0.2126/0.712；P4 白）— https://www.phosphor-technology.com/crt-phosphors/
11. Sylvania, *Characteristics of Sylvania Cathode Ray Tubes*, 1962（P1/P3/P4 定义）— http://bitsavers.computerhistory.org/components/sylvania/CRT/Characteristics_of_Sylvania_Cathode_Ray_Tubes_196203.pdf

**制图与安全色标准**
12. ASME Y14.1-2020, Drawing Sheet Size and Format（图幅、标题栏、修订栏）— https://webstore.ansi.org/standards/asme/asmey142020
13. ASME Y14.2-2014, Line Conventions and Lettering（线宽体系、箭头、字高）— https://files.asme.org/Catalog/Codes/PrintBook/35656.pdf
14. USITT Graphic Recommended Practice v5.0（0.15/0.30/0.70 mm 线宽层级；标题栏置于右下；"DO NOT SCALE DRAWINGS"）— https://www.usitt.org/sites/default/files/2023-05/USITT%20Graphic%20Recommended%20Practice%20Guideline%20Version%205.0%20copy_3_0.pdf
15. ANSI/NEMA Z535.1-2022 前言与范围（安全色体系；Pantone 对应：红 186C、橙 151C、黄 109C、绿 335C、蓝 285C）— https://www.nema.org/docs/default-source/standards-document-library/ansi-z535_1-2022-contents-and-scope.pdf

**NASA-punk 语汇**
16. GameSpot, “Starfield's Aesthetic Aims To Be 'NASA-Punk'”（Istvan Pely 原话）— https://www.gamespot.com/articles/starfields-aesthetic-aims-to-be-nasa-punk/1100-6500847/
17. Starfield Wiki, “NASA punk”（"It's got buttons. They're tactile."）— https://starfieldwiki.net/wiki/General:NASA_punk

### 未能取得的资料（据实说明）

- **ASME Y14.1 / Y14.2 正文**：付费标准，只读到目录、范围与公开预览页；线宽与标题栏数值取自 USITT 公开推荐做法与 ASME 目录结构，未逐条引用标准原文条款。
- **ANSI Z535.1-2022 正文**：付费；使用了 NEMA 公开的前言/范围（含 Pantone 对照表），安全色的 Munsell/CIE 容差数值未取得。
- **1975 手册 220 页重印本（Standards Manual LLC）**：受版权保护，未访问；使用的是 NASA 公有领域 PDF 与公开引文。
- **MoMA 藏品页**：仅取得条目元数据，无图像。
- 本文件中所有对比度数值由 WCAG 2.1 相对亮度公式对上述 hex **实算**得出，非引用。
