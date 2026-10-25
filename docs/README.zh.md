# StructureCumulus 设计集（design set）

入口：[spec/zh/_index.md](spec/zh/_index.md)。第一次接触？先读
[guide.zh.md](guide.zh.md)：它从一个结构写起，然后依次介绍相关系统。

本设计分为一个基础层、若干单元文件，以及一份合并设计。从以下几份开始：

1. [spec/zh/00-overview.md](spec/zh/00-overview.md) —— 需求、不变量、单元。
2. [spec/zh/01-glossary.md](spec/zh/01-glossary.md) —— 封闭词汇表。
3. [spec/zh/02-architecture.md](spec/zh/02-architecture.md) —— 包图、阶段、源集。
4. [spec/zh/03-naming.md](spec/zh/03-naming.md) —— 冲突禁用与模块模板。
5. [spec/zh/04-api-doctrine.md](spec/zh/04-api-doctrine.md) —— 组合、类型安全、黄金示例。
6. [spec/zh/17-worked-example.md](spec/zh/17-worked-example.md) —— 巨型熔炉端到端。
7. [spec/zh/18-verification.md](spec/zh/18-verification.md) —— 门禁与反例。
8. [spec/zh/19-open-proposals.md](spec/zh/19-open-proposals.md) —— 推迟的特性与对账记录。
9. [spec/zh/20-execution-and-sections.md](spec/zh/20-execution-and-sections.md) —— 工作线程执行、区段快照、脏状态、区段缓存。
10. [spec/zh/21-authoring.md](spec/zh/21-authoring.md) —— 紧凑 JSON 创作 profile 与 capture/import 工具。
11. [spec/zh/22-consolidated-design.md](spec/zh/22-consolidated-design.md) —— 合并设计（含共享中间状态与跨区段判据）。

内核源集是 `src/kernel/java`；它只靠 JSpecify 即可编译。Minecraft 1.21.1 NeoForge 适配器
位于 `src/main/java/dev/vfyjxf/cumulus/mc`。`src/kernel` 下的任何文件都不得出现
Minecraft、NeoForge 或 Mojang；`./gradlew compileKernelJava kernelPurity` 可证明这一点。
本设计是可证伪的草稿：各模块的待决问题列在各自的单元文件中。

设计跑在内核前面。单元文件描述某个类型或某条路径而源码尚未具备时，意图由设计陈述、已建成
的部分由 `src/**` 陈述；两者之间没有单独的账本，关于内核的说法只和它指向的代码一样可靠。
