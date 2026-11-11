# FlowBus 1.3.1 详细实施方案

状态：审查证据已确认；本文件为 AI 实施与人工验收规范，开发进行中；本次目标限定为发布前验证，不执行 W8 正式发布。方案日期：2026-11-11。

[文档索引](README.md) · [当前版本说明](release-notes-1.3.0.md) · [兼容性](compatibility.md) · [发布清单](release-checklist.md)

## 执行导航

后续实施者先阅读第 1–3 节了解契约与缺陷，再按[任务依赖](#execution-tasks)执行。第 7–14 节是逐项工作说明；常用入口为[验证命令](#verification-commands)、[验收与交接](#delivery-handoff)和[AI 启动提示词](#implementation-prompt)。方案不要求记住原聊天，也不依赖任何 `build/` 或 `/tmp/` 文件永久存在。

本文使用三个标记：**现有**表示本次已核对的文件/任务，**拟新增**表示需要先实现，**设计门禁**表示必须取得验证证据才能进入下一步。标为“未开始”的任务不能因已有历史 CI 成功而勾选完成。

## 1. 版本决策

**推荐下一版先发布 1.3.1。** 本次发现并运行复现了 3 个边界问题，均可围绕既有契约修复，优先于新增公共能力。1.4.0 保留为后续增强版本；只有新增诊断能力或接入能力有真实使用场景、API 设计和验收证据时才立项。

本方案最初基于 1.3.0 审查编写；当前正式版本仍为 1.3.0，工作区已推进到未发布的 1.3.1 候选。实现与验证状态统一见第 6 节和最近实施记录，不能把方案、源码版本切换或局部通过视为发布完成。

| 版本 | 目标 | 纳入范围 | 启动 / 发布条件 |
| --- | --- | --- | --- |
| 1.3.1 | 修复既有行为的边界缺陷 | sticky 重入一致性、scope 创建失败清理、生命周期阈值校验；补齐 1.3.0 兼容基线与必要回归 | 本文必修项完成，最终候选通过兼容和设备门禁 |
| 1.4.0 | 有真实场景支撑的诊断与接入增强 | 可选诊断出口；经测量证明必要的开销优化；可复用的接入测试示例 | 1.3.1 稳定，至少一个真实消费者验证需求；新增 API 单独评审 |

1.3.1 保持现有发布坐标、Java 8 字节码、Android API 21、Kotlin 1.9.25 / Coroutines 1.8.1 / Lifecycle 2.6.2 构建基线。不得把提高最低支持版本、切换默认 overflow、改变普通事件离线语义或取消旧 API 混入补丁版本。

## 2. 审查基线、范围和证据

- 源码基线：`main` 的 `130c4aa375155917fdd296e312d4dfab14acee66`。审查开始时本地 `8941e6f` 与该提交的文件树相同；计划分支基于该 main 建立。
- 发布基线：1.3.0；发布 tag 对应 `299f6a7`。`130c4aa` 包含发布后的 UI 采证补强，未改库的生产实现。
- 深入检查 core 的 sticky 发布/回放、scope 登记与绑定、收集与错误处理、配置与诊断；Android 生命周期与 Java 入口；Compose effect 的订阅重建；相关单元测试和消费者/兼容基线。
- 运行负载假设：单进程内多个生产者和订阅者，包含同步恢复的协程、回调重入、页面生命周期变化、任务 scope 创建失败。不是跨进程消息中间件。
- 官方对照采用固定版本源码；只借鉴契约和设计，不根据名称或功能表判断实现正确性。
- GitHub 当前没有开放 PR；仍开放的 [Issue #2：Java 兼容入口](https://github.com/logan0817/FlowBus/issues/2) 与已发布能力存在状态差异，作为维护事项核对，不当作缺少 Java 功能。

### 本轮运行结果

通过项目 Android Studio 共享构建环境执行 `:flowbus-core:test :library-android:testDebugUnitTest --continue`，外部 init script 临时追加 3 个审查用例并将报告写入独立构建目录。

| 范围 | 执行数 | 通过 | 失败 | 解释 |
| --- | ---: | ---: | ---: | --- |
| core | 183 | 181 | 2 | 既有 181 项通过；新增 sticky 重入和 scope 创建失败回归暴露问题 |
| Android | 75 | 74 | 1 | 既有 74 项通过；新增 DESTROYED 阈值回归暴露问题 |
| 合计 | 258 | 255 | 3 | 0 error、0 skipped；本轮整体测试命令返回失败，不能写为全绿 |

临时复现和报告保存在本地 `build/audit-20261111/`，不属于生产代码或受控测试。三个新增用例断言应有行为，当前失败是审查证据；实施时应迁入正式测试并修到通过。复现路径与实际失败值见下文，避免仅依赖临时文件。

历史 [PR CI](https://github.com/logan0817/FlowBus/actions/runs/42804628685) 与 [main CI](https://github.com/logan0817/FlowBus/actions/runs/42806745383) 全部通过，但没有覆盖新增的这 3 个边界。本轮没有重跑设备矩阵、Compose 渲染测试、Maven 发布或正式 JMH 测量；这些历史结果不替代未来修复候选的验证。

## 3. 已确认缺陷

### F1 · P1：sticky 重入发送可能让最近值回退

**位置：** [FlowBusStore.kt](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBusStore.kt) 的 `StickyEventFlow.tryEmit`（454–460 行）、`emit`（448–451 行）和 `commitIfCurrent`（525–535 行）。行号基于本次审查提交。

**触发：** 使用可立即恢复的订阅协程；收到 sticky 值 `1` 时，在回调内再次 `postSticky(key, 2)`。默认 replay=1，无需缓冲溢出。

核心复现逻辑如下，放在 `runBlocking` 中，使用 `Dispatchers.Unconfined`，并在 finally 中取消订阅：

```kotlin
val bus = FlowBus()
val key = eventKey<Int>("audit.sticky.reentrant")
val received = mutableListOf<Int>()
val collector = launch(Dispatchers.Unconfined) {
    bus.stickyFlow(key).collect { value ->
        received += value
        if (value == 1) check(bus.postSticky(key, 2))
    }
}
try {
    check(bus.postSticky(key, 1))
    assertEquals(listOf(1, 2), received)
    assertEquals(2, bus.consumeStickyLatest(key))
} finally {
    collector.cancelAndJoin()
}
```

**实测：** 第一条断言通过，实时收到 `[1, 2]`；第二条失败，最新 replay 实际为 `1`。回调中的新事件先完成 replay 提交，外层旧事件恢复后再提交，把新值挤出容量为 1 的缓存。

**影响：** 实时订阅者已看到新值，稍后消费/订阅 replay 却可能看到旧值。公共 root、共享 scope 和 owned scope 均复用该存储实现；本轮确定性复现覆盖 root 的非挂起发送，不把其他路径描述为已运行复现。

**修复方向：** 明确一次 sticky 发布被接受、进入实时流和提交 replay 的顺序，使重入时旧提交不能覆盖较新的发布。同时检查挂起发送与并发发送；实现应保留既有 clear/consume 的 generation 隔离、拒绝发送不污染缓存，以及已说明的 DROP_LATEST 实时流/replay 区别。不能简单把 `commitIfCurrent` 移到 `tryEmit` 前，也不能仅用函数返回顺序定义事件新旧。

**验收：**

- 上述用例变绿；replay=1 与 replay>1 的重入顺序明确且可重复验证。
- root、`scoped`、`openScope` 以及挂起/非挂起入口覆盖共同实现；Android 至少一条实际回调重入测试。
- 保留 SUSPEND 背压、取消、RejectedByBuffer、DROP_OLDEST、DROP_LATEST、clear/consume/remove 的现有行为；不能用异步延迟发送规避复现。
- 新订阅与发布交界用确定性同步验证；未明确排序的并发调用不承诺全局总顺序。

### F2 · P2：scope 创建绑定失败后遗留不可达句柄

**位置：** [FlowBus.kt](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBus.kt) 的 `openScope(String, CoroutineScope)`（332–333 行）和 owner 重载（358–359 行），以及 [FlowBusScope.kt](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBusScope.kt) 的 `bindTo(CoroutineScope)`（333–337 行）。

**触发：** 向 `openScope` 传入上下文中没有 `Job` 的 CoroutineScope。自定义 scope 或 `GlobalScope` 均可能没有 Job。最小复现：

```kotlin
val bus = FlowBus()
val noJobScope = object : CoroutineScope {
    override val coroutineContext = EmptyCoroutineContext
}
assertThrows(IllegalArgumentException::class.java) {
    bus.openScope("audit.invalid", noJobScope)
}
assertNull(bus.inspectScope("audit.invalid"))
bus.openScope("audit.invalid").close()
```

**实测：** 第一个异常按预期抛出，第二条断言失败，快照仍为 `hasOpenScopeHandle=true, isClosed=false`。实现先登记句柄，再校验 Job；调用者没有拿到句柄。同名创建会被已有登记拒绝，需额外 `removeScope` 才能释放。

**修复方向：** 在登记任何 scope 状态前提取并校验 Job，然后委托 Job 重载。覆盖字符串和 owner 重载，并审视其他绑定失败路径的异常安全；回滚不得误删已有的同名共享 store 或业务数据。

**验收：** 无 Job 时仍清晰抛错，但 inspector 无新增登记、同名重试成功；已有同名共享 store 的 sticky 数据保留；有效、已完成和已取消的 Job 仍按既有生命周期关闭语义工作。对已有句柄直接调用 `bindTo` 失败，不应把已有句柄一并销毁。

### F3 · P2：DESTROYED 被接受为收集阈值，形成静默无效订阅

**位置：** [InstanceCollectEventExtension.kt](../library-android/src/main/java/com/logan/flowbus/InstanceCollectEventExtension.kt) 的 `launchFlowBusCollection`（166–170 行）；相同校验还存在于 [CollectEventExtension.kt](../library-android/src/main/java/com/logan/flowbus/CollectEventExtension.kt)（34、60 行）、[FlowEventBus.kt](../library-android/src/main/java/com/logan/flowbus/FlowEventBus.kt)（99、191 行）及 [FlowBusEffect.kt](../library-compose/src/main/java/com/logan/flowbus/compose/FlowBusEffect.kt)（33 行）。

**触发：** receiver 已 RESUMED，调用 `collectEventFromSuspending` 并传入 `minLifecycleState = Lifecycle.State.DESTROYED`。当前仅拒绝 INITIALIZED，参数会进入 `repeatOnLifecycle`。

**实测：** `rejected=null jobActive=true subscriptions=0 channel=null`。看似注册成功，实际没有建立订阅，`onSubscribed` 永不触发。此处指错误的“最低收集状态”参数，不能与“owner 本身已经销毁”混为一谈。后者应正常不订阅，不需要抛错。

**修复方向：** 公开生命周期入口一致地只接受 CREATED、STARTED、RESUMED，在创建 Job / effect 前失败并给出可用值；便捷入口的校验还应先于其自身获取全局/owner 总线，避免无效调用冻结默认配置。通用 `collectEvent` 是 inline API，历史已编译调用者可能内嵌旧校验；升级说明应提示重新编译，不能承诺只替换 AAR 就修复内嵌代码。

**验收：** 五个 Lifecycle.State 枚举值参数化验证；两个无效阈值被拒绝，三个有效阈值保持启动/停止/重入语义；generic Flow、旧 onEvent、显式 bus/scope 入口和 Compose 均覆盖。Compose 本次仅确认相同校验路径，尚未运行该参数的实际组合测试，实施时必须补齐。Java 默认 STARTED 路径无须新增状态参数。

## 4. 必要改进与待证问题

### G1 · 发布门禁：冻结 1.3.0 原版消费者与公开符号

[baselines.json](../compatibility/core-binary-consumer/baselines.json)、[构建入口](../gradle/release-verification.gradle.kts) 和 [CI](../.github/workflows/ci.yml) 目前列出 1.0.7、1.1.0、1.2.0 历史基线，没有 1.3.0。现有 `apiCheck` 仍有效，这不是“完全没有兼容校验”；缺的是对最新已发布版本、尤其 1.3.0 新增接口的原版字节码运行验证。

1.3.1 前增加原版 1.3.0 core/Android/Compose 制品摘要及 JVM/API 符号基线；用正式版本编译订阅通知、Java Subscription、inspectChannel 和 Compose effect 的最小消费者，再用候选运行。保留原版编译结果，不允许用候选重新编译替代二进制验收。原有历史覆盖继续保留，新增输入接入当前 candidate artifact 和稳定 `verify` 门禁。

### G2 · 性能证据：更新正式基线后再决定优化

[基准说明](../benchmarks/jvm/README.md) 已诚实标注：完整报告仍是 1.2.0 对 1.1.0，1.3.0 定向报告来自开发期 SNAPSHOT，CI smoke 只证明可执行。当前发送路径会创建结果对象并更新诊断计数，但本轮没有 profile 证明它是实际瓶颈，不能据此删除指标或宣称性能回退。

1.3.1 对 F1 的修改增加与正式 1.3.0 的定向测量，至少覆盖无订阅发送、sticky 发布、活跃订阅和重入回归涉及的路径。性能优化另开范围；1.4.0 只有在 profile 指出明确瓶颈且契约测试不变时再做。保持批次吞吐、实际接收数、分配量和环境/制品身份，不把 smoke 或 JVM 结果解释为 Android 性能承诺。

### G3 · 接入与问题跟踪

补充“回调内再次发送”“合法生命周期阈值”“scope 绑定失败”的最小用法/排障说明；发布前验证现有独立消费者的 release R8，真实业务项目在具备明确目标与授权后追加接入验收，不把未指定的外部项目作为发布阻塞条件。复用现有 Java、Compose 和导航消费者，不为计划另建样例框架。Issue #2 的处理是核对原提问者所需场景与已交付 Java API 后更新状态；本次审查未修改 Issue。

### 待证项：sticky 订阅交界和锁内回调

`backing.onSubscription` 登记与自建 `replaySnapshot` 获取并非同一步；`postResult` 也在持有 stickyReplayLock 时调用可能同步恢复协程的 `tryEmit`。这些是与 F1 相关的审查入口，需要验证重复/漏收交界和重入锁行为。本轮没有取得确定性重复/漏收或跨线程死锁复现，因此不计入已确认缺陷，不承诺一定发生。实施 F1 时用屏障或可控调度补证；若确认则纳入同一契约修复。

API 35 偶发失焦仍是待复现的 UI 测试问题，归因未确认。PR #15 已补齐清理前采证并通过 PR/main CI，本轮没有新失败现场，不把它计入库缺陷或新增“失焦修复”工作。

## 5. 同类项目的取舍

检索日期为 2026-11-11。下表使用固定版本官方源码，版本仅作为分析对象，不宣称是检索日最新版本。

| 来源 | 可核实的设计 | 对 FlowBus 的具体借鉴 | 边界 |
| --- | --- | --- | --- |
| [kotlinx.coroutines SharedFlow 1.8.1](https://github.com/Kotlin/kotlinx.coroutines/blob/1.8.1/kotlinx-coroutines-core/common/src/flow/SharedFlow.kt#L110) | 同步保护内部状态，在锁外恢复收集/发送协程，明确无订阅者、缓冲与 replay 契约 | F1 必须同时验证同步重入、发送结果和 replay；不因底层 SharedFlow 线程安全就推断封装自动正确 | FlowBus 自建 sticky replay 支持 consume/clear 语义，不能直接替换而改变契约 |
| [greenrobot EventBus V3.3.1](https://github.com/greenrobot/EventBus/blob/V3.3.1/EventBus/src/org/greenrobot/eventbus/EventBus.java#L311) | postSticky 先保存最近值再分发；post 使用线程内队列处理嵌套发布；可报告无人订阅 | 明确状态提交与用户回调的先后；1.4 候选可借鉴可关闭的无人订阅诊断 | EventBus 没有 FlowBus 相同的 SUSPEND/拒绝语义，不能照搬“先存后发”修 F1 |
| [RxJava PublishSubject v3.1.10](https://github.com/ReactiveX/RxJava/blob/v3.1.10/src/main/java/io/reactivex/rxjava3/subjects/PublishSubject.java#L52) / [SerializedSubject](https://github.com/ReactiveX/RxJava/blob/v3.1.10/src/main/java/io/reactivex/rxjava3/subjects/SerializedSubject.java#L85) | 显式说明序列化要求与重入保护；把嵌套通知入队，再在锁外通知订阅者 | 将重入写进公共契约与测试矩阵；审查用户回调执行时持有哪些内部锁 | 不引入 RxJava 生产依赖，不把其无背压 Subject 行为替代 Flow 的背压 |

FlowBus 1.3.0 已有类型通道、每通道配置、订阅就绪通知、Java、可选 Compose、取消/错误策略、发送结果和配置快照。这些属于现状，不重复列成 1.4 新能力。

保持项目的进程内广播定位。注解扫描、事件继承分发、ThreadMode 枚举、事件优先级/中断传播、ACK、持久化队列、跨进程总线和后台网络上报均没有本轮需求证据，不纳入计划。状态恢复仍优先 StateFlow/业务状态；可靠任务使用数据库或持久化任务机制。

<a id="execution-tasks"></a>

## 6. 任务分解、依赖与执行规则

### 6.1 当前状态与任务编号

审查已经运行；下表描述后续开发，不代表已完成。后续 AI 必须保持任务编号稳定，并把本节作为唯一实施状态入口。

| 编号 | 任务 | 依赖 | 主要输出 | 当前状态 |
| --- | --- | --- | --- | --- |
| W0 | 恢复现场、核对授权和工具链 | 无 | 实际基线、修改清单、任务状态 | 完成 |
| W1 | 将 3 个复现转为正式行为回归 | W0 | 修复前失败证据、必要测试资源清理 | 完成 |
| W2 | 修复 sticky 重入与提交顺序 | W1 | 局部内部实现、设计说明、完整行为矩阵 | 本地行为通过；性能/设备待验收 |
| W3 | 修复 scope 创建绑定失败 | W1 | 登记前校验、异常安全回归 | 首轮 PR CI 通过 |
| W4 | 修复生命周期阈值与入口副作用 | W1 | Android/Compose 参数验证及真实消费者阶段 | 首轮 PR API21/35 真实阶段通过 |
| W5 | 补齐正式 1.3.0 兼容基线 | W0；最终运行等待 W2–W4 | 固定原版摘要/API、旧字节码运行证据 | 首轮 PR CI 通过 |
| W6 | 正式 1.3.0 定向性能对照 | W2、W5 基线可用 | 带身份的正式 JMH 数据与解释 | 进行中 |
| W7 | 版本候选、文档、集成与 CI | W2–W6 | 1.3.1 最终候选及完整门禁 | 进行中 |
| W8 | 授权发布与公开制品验收 | W7；对应远端授权 | Central 状态、公开消费者、tag/Release | 本次目标不执行 |

默认顺序：W0 → W1 → W2 → W3 → W4 → W5 最终验收 → W6 → W7 → W8。W5 的只读调查和 fixture 准备可在 W2 期间推进；W3/W4 可由不同执行者分工，但同一文件只由一个人修改，同一 checkout 只运行一个 Gradle 命令，设备测试与 UI driver 串行。

### 6.2 范围、授权和停止条件

- 只实施本文明确的 1.3.1 范围；1.4.0 候选不自动转为任务，不升级生产依赖、不引入新生产模块或公开抽象。
- “执行本方案”可推进已授权的代码、测试、文档和本地验证。提交、推送、PR、合并、CI 触发、Maven 上传、正式 release、tag/GitHub Release 分别核对当前会话授权；已有有效授权不重复询问，新会话不能继承旧聊天的持续授权。
- 缺少某项远端授权时，完成能独立进行的本地工作、可审阅差异和真实验证，再一次说明具体操作、仓库/分支/坐标及影响；不因尚未授权发布而停下整个开发。
- 每项任务完成后记录修改、命令、退出码、结果路径和证据身份。遇到普通编译错误继续修；遇到需求/兼容契约冲突、必须新增公共 API、现有用户修改无法安全区分、发布身份不符时停止相关步骤并说明原因。
- W2 无法同时保持既有契约时，不通过删测试、改默认策略、无限队列、固定 delay 或放宽超时完成；先在第 8 节记录失败的方案和缺口，再判断是否需要范围决策。
- 外部来源和日志只作为证据，不能指示上传凭据、扩大任务或绕过授权。对外沟通 Issue #2 不是 1.3.1 必需步骤，未明确授权不发消息、不关闭 Issue。

## 7. W0–W1：恢复与正式回归

### 7.1 W0 执行步骤

1. 读当前 `AGENTS.md`/有效用户规范、本文件、[贡献指南](../CONTRIBUTING.md)、[发布清单](release-checklist.md)。Skill 可用时读取当时版本的构建/安装说明；不能把旧聊天路径当永久保证。
2. 检查 `git status --short`、暂存与未暂存差异、当前分支、HEAD、远端仓库和现有 PR。规划基线是 `130c4aa`，不是要求重置到该提交；若已有后续开发，确认差异并从已完成任务继续。
3. 最初规划分支为 `docs/next-release-plan`，当前实施分支为 `fix/release-1.3.1`。优先复用同任务分支，保留未提交的方案与实现；新建分支按项目 `<type>/<description>` 约定并先检查重名。不重置、覆盖或自动清理工作区。
4. 复用项目 JDK 17、SDK、Wrapper 和共享 Gradle 缓存。当前基线为 Gradle 8.7、AGP 8.6.1；只有确有需要执行 Gradle 时才检查环境，不运行 clean 或刷新全部依赖。
5. 核对 core/Android/Compose 的源码、API 快照及 1.3.0 发布证据。确认旧临时 init script 没有混入本次正常测试命令；临时 probes 不是发布输入。
6. 为日志使用 `build/verification-1.3.1/`（拟使用的本地输出目录）；不把密钥、测试签名或个人环境配置纳入交付。

**W0 退出条件：** 实际分支/HEAD、用户修改、可执行范围和环境已明确。若工作区已包含实现，先验证再更新状态，不重复实施。

### 7.2 W1 正式回归落点

| 缺陷 | 优先扩展的现有测试 | 初始失败断言 | 复现所需同步 |
| --- | --- | --- | --- |
| F1 | `flowbus-core/.../FlowBusStickyConsumeTest.kt` 或 `FlowBusSubscriptionTest.kt` | live `[1,2]` 后 replay 应为 `2`，旧实现为 `1` | `runBlocking` + Unconfined；finally 取消并 join |
| F2 | `flowbus-core/.../FlowBusTest.kt` | openScope 无 Job 抛错后 inspector 应无新增 handle | 直接同步调用；finally 清理测试自己的名称 |
| F3 | `library-android/.../InstanceCollectEventTest.kt` | receiver 活跃、阈值 DESTROYED 应同步抛错 | `StandardTestDispatcher`、`runCurrent`、unsafe test LifecycleRegistry |

路径中的 `...` 分别为 `src/test/java/com/logan/flowbus/core` 和 `src/test/java/com/logan/flowbus`；使用 `rg` 定位现有类，不凭表格创建平行目录。第 3 节 F1/F2 代码可直接作为行为骨架，补齐 import、测试注解与 finally。F3 需先把 owner 推进到 RESUMED，再调用带 `onSubscribed` 的 `collectEventFromSuspending`；捕获同步异常，确保最终取消 job、销毁 owner、恢复 Main dispatcher。

**W1 退出条件：** 未修复生产代码时 3 个行为回归按原因失败；排除编译失败、未发现测试、资源未释放超时等伪复现。记录失败值和命令后，继续 W2–W4。红测试可以暂存在工作区，不能把带失败门禁的候选当可合并版本。

## 8. W2：sticky 修复设计与行为矩阵

### 8.1 修改范围与必须保留的不变量

主要实现限定在 [FlowBusStore.kt](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBusStore.kt) 的 `StickyValue`、`StickyEventFlow.emit/tryEmit`、replay 提交与快照处理。必要时调整它们与外层 `stickyReplayLock` 的关系，但须说明锁顺序。root/shared/owned 继续复用该存储，不新增公共发送 API。

1. 收到 `1` 的回调发布 `2` 后，旧调用迟完成不能覆盖 `2`；replay=2、链式 `1→2→3` 应保留 `[2,3]`。
2. 事件身份与 payload 相等性分离。两次相同 payload 是两次发布，不以 equals/hashCode 去重。
3. 非挂起拒绝的值和挂起发送尚未成功恢复前取消的值不进入 replay；已实时交付不撤回，已成功完成的发送不会因之后取消其 Job 而回滚 replay。发送结果和指标只记录一次，取消按既有契约传播。
4. clear/consume/remove 提升代际后，旧代挂起发送恢复不能重新填入 replay；已提交的实时投递仍按现有行为处理。
5. 保留 DROP_LATEST 下“live 可丢新值，但 replay 可记录新值”的文档契约；不是所有 accepted 都等于实际投递。
6. replay 快照在 `onSubscribed` 通知前固定；通知中发送的新事件不能同时作为该次 replay 注入。通知不得在阻止另一线程发布的内部锁中执行。
7. replay=0、总容量为 0、SUSPEND、来源隔离、scope 关闭/重开、旧 Flow 引用的契约保持。不会增加无界 pending 表或后台发布任务。

### 8.2 设计门禁：不得把未经验证的算法当成定案

执行 AI 在本节追加简短设计记录，至少回答：

- 每次发布何时“被接受”、何时 replay 可见、何时失效？相同发布的重复提交如何防止？
- 请求进入顺序、backing 接受顺序和函数返回顺序分别是什么？顺序编号若存在，代表哪个时点？
- Unconfined/立即调度回调恢复时持有哪些内部锁？不同线程调用 publish/clear/configure 是否能完成？
- replay>1 如何保留有序成功历史？未完成、被拒绝、取消和旧代发布如何释放元数据？
- 新订阅登记与 replay 快照交界如何处理？哪些并发顺序不作承诺？

**推荐评估路线：** 优先保留 `MutableSharedFlow<StickyDelivery>` 和自建 replay，评估“独立发布身份 + 幂等提交 + 有序 replay 元数据”的局部实现；是否需要序号或 pending 状态必须由上述时点和测试证明。先做最小原型并跑本节矩阵，再扩展封装层。若局部方案不能保持契约，记录具体失败用例，再评估内部替换；不能直接自研整套广播队列。

以下方式不能直接作为修复结论：

| 简单改法 | 必须先排除的问题 |
| --- | --- |
| 把 commit 放到 tryEmit/emit 前 | 最后被拒绝/取消的值会提前暴露 |
| 只加 AtomicLong，保留最大值 | 请求编号不必等于接受顺序；replay>1 会丢历史 |
| 用全局 Mutex 包住 emit | 挂起和重入可能等待自身，且同步 post 无法自然获取挂起锁 |
| 增加 synchronized 包住所有流程 | monitor 可重入不能纠正提交顺序；可能在锁内恢复用户回调 |
| 每次发送 launch 一个协程 | 改变同步结果、顺序和取消归属，可能积压 |
| 直接改为原生 replay SharedFlow | 改变 consume/clear 和 DROP_LATEST 契约 |

本方案不要求先暂停向用户汇报每次内部设计选择；在既定契约内自主实现和验证。只有必须改变公共行为/范围时才请求决策。

### 8.2.1 当前局部实现与额外验收

工作区保留原生 SharedFlow 的背压和实时分发，自建 replay 按每通道发布顺序保存有界成功历史；清理以 generation 隔离旧发送。短临界区内分配顺序并启动原生发送到完成或首次挂起，不跨挂起持锁。只有成功结果提交 replay；函数恢复反序不能反转历史顺序。

每次 collect 的原生 slot 登记与 replay 快照在同一短临界区启动；立即恢复的通知/回调先延后到最外层锁释放，保留 UNDISPATCHED 的同步通知语义。其他线程在锁释放后继续；用户回调不持内部发布锁。延期恢复只服务当前临界区，不是后台任务或无界发布队列。清理持有外层 stickyReplayLock 时只操作短期状态，实际发送移到外层锁之外，避免回调跨线程清理被外层锁阻塞。

该实现使用低级 continuation 桥接，必须额外验证同步完成/异常、真实挂起/取消、恰好一次恢复、通知异常和 take 提前结束释放 slot、多订阅者失败隔离，以及跨线程 dispatcher/context 保持。不能仅凭 F1 或 replay=1 通过判定设计安全。正式性能数据与 API21/35 执行仍是退出条件。

### 8.3 W2 验收矩阵

不机械执行所有维度的笛卡尔积；先覆盖共享实现和真实入口，再按风险扩展。

| ID | 场景 | 同步与关键断言 |
| --- | --- | --- |
| S01 | root/shared/owned 的 postSticky 重入 | 立即收集：live `[1,2]`，最新 replay `2` |
| S02 | 外层 emitSticky，回调 postSticky | 使用不会填满缓冲的布局；结果同 S01 |
| S03 | replay=2，链式 `1→2→3` | live `[1,2,3]`；新订阅 replay `[2,3]` |
| S04 | 相同 payload 连续两次成功发布 | 是两次事件；容量允许时保留两次 |
| S05 | SUSPEND 缓冲满时 tryPost | Deferred 阻塞回调并填满缓冲；false/RejectedByBuffer，replay 不含拒绝值 |
| S06 | 缓冲满时取消挂起 emit | cancelAndJoin 后取消值不在 replay；后续发布仍可用 |
| S07 | clear/consume/remove 与挂起发送交叉 | 复用旧测试 gate；旧代恢复不污染 replay，既有 live 语义不变 |
| S08 | onSubscribed 内另一线程发布 | 复用已有 latch；不被通知持锁阻塞，不重复 replay/live |
| S09 | 通知中 clear/consume 后再发布 | 旧快照失效，只收到新值；consume 返回清理前缓存 |
| S10 | replay=0、extra=0、SUSPEND | 无历史；活跃订阅时 tryPost 被拒绝；emit 背压成立 |
| S11 | DROP_LATEST | 复用慢订阅测试；live `[1,2]`、replay `3` 的既有契约保留 |
| S12 | 新订阅与发布交界 | 屏障/可控调度覆盖快照前后；同一发布不重复注入；记录排序边界 |
| S13 | scope 关闭、同名重开 | 旧 Flow 不接到新 store；旧发送不污染新作用域 |
| S14 | Android 立即调度回调重入 | 活跃 owner + 适当的立即测试调度器；同 S01，不以延后调度掩盖错误 |
| S15 | 多生产者接受顺序与发送者恢复顺序错位 | 在无丢弃布局中以可控调度/屏障记录 live 接受顺序，构造发送者恢复/返回顺序反转；覆盖 post/emit 混合及 replay>1，replay 为有序成功历史的尾部，无重复/遗漏 |

S12 中 `onSubscribed` 已在快照取得后执行，不能只阻塞这个 hook 就声称覆盖了快照前的竞态。必要时使用局部内部测试控制点，不能新增公共 hook；若仍无法确定性控制，明确记录未覆盖项并以更强的状态机证据和真实并发验证补充，不伪称已测。

必须保留的现有回归包括 `FlowBusStickyConsumeTest` 的挂起发送失效、`FlowBusSubscriptionTest` 的通知期间无锁发布/历史失效、`FlowBusChannelConfigTest` 的 DROP_LATEST 和零容量、`FlowBusScopeCloseTest` 的关闭竞态。源码字符串断言若因内部实现变化失效，先识别保护意图，用等价或更强的行为验证替换，不能简单删除或改字符串过关。

S15 在所有待比较的成功发送完成后验证已控制和观察到的接受顺序，不为未同步的并发调用规定全局先后，也不要求发送者尚未恢复时的 replay 快照包含所有实时值。若公开 API 无法控制目标时点，按 S12 的局部内部控制方式补证并说明限制；不能把请求分配的序号直接当接受顺序。

**W2 退出条件：** W1 的 F1 用例及适用 S01–S15 通过，设计记录能解释关键时点，公开 API 无变化，必要 Android 集成通过。没有证据的重复/漏收或死锁疑点继续标为疑点。

## 9. W3：scope 创建失败的异常安全

### 9.1 最小实现步骤

1. 修改 `FlowBus.openScope(String, CoroutineScope)`：保留名称校验，先 `requireNotNull(closeWhen.coroutineContext[Job])`，再委托既有 Job 重载。
2. 修改 `FlowBus.openScope(FlowBusOwner, CoroutineScope)`：保留已关闭 owner 和名称的校验语义，在任何登记前检查 Job，委托 owner+Job 重载。不要直接绕过 owner 检查转成字符串版本。
3. 核对 `DefaultFlowBus` 和扩展方法的委托关系；能被共同实现覆盖就不重复修复。
4. 不使用 catch 后 `removeScope(name)` 回滚，因为同名可能存在合法共享 store；不扩大到重写 `FlowBusScope` 关闭状态机。

### 9.2 W3 验收矩阵

| ID | 输入/现有状态 | 结果 |
| --- | --- | --- |
| O01 | 新名称 + 无 Job scope | 抛 IllegalArgumentException，inspectScope=null，随后同名创建成功 |
| O02 | 普通 FlowBusOwner + 无 Job scope | 同 O01 |
| O03 | 已有同名 shared store，含 sticky/config | 抛错；原数据和配置保留，无新增 owned handle |
| O04 | 已有同名 owned handle | 原句柄不关闭、不替换，仍可使用 |
| O05 | 对已有句柄直接 bindTo(noJob) | 抛错但原句柄保持有效 |
| O06 | 有效 Job，随后完成/取消 | 最终释放登记与绑定，不能阻塞 completion handler |
| O07 | 传入已完成/已取消 Job | 最终关闭完成，无残留句柄；用完成信号而非 sleep 等待 |
| O08 | 已关闭 FlowBusScope 作为 owner | 拒绝，不打开同名新句柄 |
| O09 | 空白名称同时无 Job | 输入校验清晰，无任何登记 |

优先扩展 `FlowBusTest.kt`，复用 `RepositoryOwnershipTest.kt`、`FlowBusScopeCloseTest.kt`。**退出条件：** O01–O09 的新增或既有覆盖有明确映射，F2 用例转绿，公共签名与已有正常绑定语义保持。

## 10. W4：生命周期阈值的完整入口修复

### 10.1 输入契约与文件范围

只允许 CREATED、STARTED、RESUMED。INITIALIZED、DESTROYED 同步抛 `IllegalArgumentException`，错误信息给出实际值与合法值。使用一致的短谓词，如 `state.isAtLeast(CREATED)`；不新增公共 enum、helper 或配置。

| 文件/调用链 | 实施要求 |
| --- | --- |
| `CollectEventExtension.kt` 的普通/挂起收集 | 在访问 lifecycleScope、启动 Job 前校验 |
| `FlowEventBus.kt` 的直接生命周期收集入口 | 保留直接公开调用的防御校验 |
| `InstanceCollectEventExtension.kt` 的 launchFlowBusCollection | 一处覆盖 root/shared/owned 的现有委托 |
| `SubscribeEventExtension.kt` 的前四个 LifecycleOwner 重载 | 在 GlobalViewModelStore.get、ViewModelProvider 或 flowEventBusFor 前校验 |
| `OnEventSuspendingExtension.kt` 的全局 reified/channel、owner-channel 重载 | 表达式体可改为块体，先校验后获取总线；owner-reified 复用委托 |
| `OnEventExtension.kt` | 核对别名委托，不为形式一致重复实现 |
| `library-compose/.../FlowBusEffect.kt` | 在 rememberUpdatedState/LaunchedEffect 前校验 |

不改没有生命周期阈值的 CoroutineScope API；Java 默认 STARTED 路径不增加新参数。调用者预先构造的 bus 无法撤销；“无来源获取副作用”只针对便捷入口自己的操作。

公开 inline 函数建议保持短谓词，不为去重新增 `@PublishedApi` JVM 符号。既有方法签名、默认参数、文件级 JVM 名称不变；`apiCheck` 预期无差异。1.3.0 已编译 generic collectEvent 可能内嵌旧实现；reified wrapper 即使调用新版非 inline 校验，旧的来源获取顺序也可能已内嵌。升级文档须说明获得完整修复需要重新编译消费者。

### 10.2 Android 测试

优先扩展现有 `FlowBusAndroidTest`、`InstanceCollectEventTest`、`FlowBusSubscriptionTest`、`OnEventSuspendingTest`，保持 `FlowBusJavaTest` 通过。

| ID | 场景 | 关键断言 |
| --- | --- | --- |
| L01 | INITIALIZED/DESTROYED × generic/直接 bus/显式来源入口 | 同步拒绝；无收集、无 onSubscribed，无新通道；owned handle 保留 |
| L02 | 无效参数首次调用全局便捷入口 | 配置工厂调用数 0，之后 FlowBusAndroid.configure 仍成功 |
| L03 | 无效参数 owner 便捷入口 | 记录 ViewModelStoreOwner getter 次数，错误调用不访问来源 store |
| L04 | CREATED 阈值 | INITIALIZED 无订阅→CREATED 开始→升至 STARTED/RESUMED 不重复启动→销毁结束 |
| L05 | STARTED 阈值 | CREATED 无订阅→STARTED 开始→CREATED 取消→STARTED 重订阅 |
| L06 | RESUMED 阈值 | STARTED 无订阅→RESUMED 开始→STARTED 取消→RESUMED 重订阅 |
| L07 | owner 已销毁，阈值合法 | 正常不订阅，无异常，Job 最终结束 |

测试 owner 先 CREATED 再 DESTROYED；不要把已创建的 LifecycleRegistry 降回 INITIALIZED。用 runCurrent、onSubscribed、Deferred/gate 控制进度，finally 取消任务、恢复 Main dispatcher、清理测试自己的全局配置。

### 10.3 真实 Compose runtime 验证

复用 [ComposePolicyVerification.kt](../compatibility/compose-consumer/app/src/main/java/com/logan/flowbus/composeconsumer/ComposePolicyVerification.kt) 的独立 Recomposer 和现有 Maven 候选桥接；`library-compose` 当前没有独立测试 runner，不另建重复测试框架。

无效参数可能在 `composition.setContent` 初次组合时同步抛出。现有 PolicyComposition 在构造器内 setContent，若直接加非法参数，异常会发生在登记 sessions 前并遗留 runner。因此测试 helper 应先创建资源，再在普通 Kotlin 的 try/finally 内调用 setContent，断言原异常类型、无回调/订阅及无通道登记，最终 dispose/cancel/join 所有资源。不能在生产 Composable 内吞异常，也不能用 Ignore 错误策略当成功。

建议新增三个明确阶段：`invalid-lifecycle-thresholds-rejected`、`supported-lifecycle-thresholds`、`destroyed-owner-does-not-subscribe`。若按此实现，policy 从历史 8 阶段变为 11 阶段；原 effect 11 阶段保留，W5 再增加 `binary-1.3.0-composable` 成为 12 阶段。最终数量以实际 PHASES 为准。

同步 `verify-policies-device.py` 的 PHASES、`verify_effects_test.py` 的缺阶段拒绝回归，以及当前消费者中英文 README/贡献指南/兼容性/发布清单。历史 1.3.0 说明的 8 阶段记录保持不动。

**W4 退出条件：** L01–L07、候选 Compose 新阶段、既有错误/取消场景通过，真实 API 21/35 release R8 结果进入 W7；mock 通过仅表示协议检查可用。

## 11. W5：1.3.0 原版二进制与公开符号基线

### 11.1 现有能力和差距

| 验证 | 当前真实能力 | 本任务新增 |
| --- | --- | --- |
| apiCheck | 三个模块当前 API 快照 | 预期补丁无差异；不靠重写 apiDump 消除报错 |
| core 原版消费者 | 1.0.7、1.1.0、1.2.0 同字节码替换 | 1.3.0 metadata、fixture、快照、generator 与 CI |
| Android legacy | 固定原版 1.1.0 编译路径 | 保留旧路径，增加原版 1.3.0 Java/Kotlin fixture |
| Compose consumer | 每次针对候选编译并 release R8 | 原版 1.3.0 编译的真实 Composable fixture，交给候选运行 |
| verify_symbols.py | 能比较 JAR/AAR 并用 API 快照限定符号 | 三个 1.3.0 制品的冻结符号对照 |

本节“现有”描述审查提交 `130c4aa`；该提交的 `-Pflowbus.consumer.baselineVersion=1.3.0` 不可用。当前工作区已有 W5 实现，恢复时先核对并验证，不覆盖重做；只有实际命令通过后才能标记为已验证。

### 11.2 固定原版输入

从 `https://repo.maven.apache.org/maven2/io/github/logan0817/` 下载三个 1.3.0 正式主制品，暂存 `build/published-baseline/`；记录 URL、大小和 SHA-256。与[1.3.0 发布证据](release-notes-1.3.0.md)中下列摘要核对，网络异常不能改用当前源码重建原版：

| 制品 | 已记录的正式 SHA-256；实施时须重新核验 |
| --- | --- |
| flowbus-core-1.3.0.jar | `3e15654a40e3a071d2ca2df51efa1288587d96f407de690c30369092ad2149b4` |
| flowbus-1.3.0.aar | `729ad9a00caf20d50b659fa355f91288e247a3f2caab989730b69d6e3e56a564` |
| flowbus-compose-1.3.0.aar | `5a3cc9c7284fa54fb8ed78d543a5b433eff2b9b32016545ca5080993fcf06e2f` |

冻结 API 快照从发布提交 `299f6a7b9720aca884be3d85f7cf122c803a6e73` 读取：`flowbus-core/api/flowbus-core.api`、`library-android/api/library-android.api`、`library-compose/api/library-compose.api`。保持原字节，分别写入拟新增的 `compatibility/core-binary-consumer/api/flowbus-core-1.3.0.api`、`compatibility/android-consumer/api/flowbus-1.3.0.api`、`compatibility/compose-consumer/api/flowbus-compose-1.3.0.api`。用原版制品核对快照符号存在，不能从候选生成“旧版基线”。

### 11.3 fixture 与 generator 实施

**core：**

1. 在现有 `baselines.json` 增加 1.3.0 和三个核验后的主制品摘要，旧条目不变；拟新增 `src-1.3.0/`，与现有按版本源码组织保持一致。
2. fixture 真实调用 1.3.0 的订阅通知重载、inspectChannel、Kotlin 默认参数，以及至少一条`.java`调用。只验证两版都支持的正常契约；F1–F3 的修复后断言留在候选回归，不要求旧版通过。
3. 扩展 `gradle/release-verification.gradle.kts` 中 generator 的输入、复制分支、版本专属 verify 调用和报告路径。只加 json 条目不算完成。
4. 沿用 `runReleased → runCandidate`：只对原版编译一次，运行时核对加载位置，替换前后`.class`摘要相同。template 当前要求原版/候选摘要不同；在候选尚未改动时相同摘要失败不是 ABI 问题，不能删除身份断言。

**Android：**

- 优先在当前生成消费者中增加拟新增 `:legacy130`，源码 fixture 放在 `compatibility/android-consumer/legacy130/`，保留 1.1.0 的`:legacy`。
- 编译只引用原版 1.3.0 core/Android classes 和固定外部依赖；包含真实 Java 调用 FlowBusJava.channel、subscribe/cancel、post/inspectChannel 及正常生命周期。
- 将已编译 fixture 交给候选验证宿主，候选运行图排除原版；Android/Compose 的最低门禁为原版编译后在候选 release/R8 真实运行，core 则要求同字节码分别运行原版和候选。检查实际 runtime 依赖、fixture class 摘要和执行阶段；R8 不能把未调用的 fixture 全部移除而仍报告成功。
- 默认接入既有 `verifyMavenLocalConsumer` 和设备 driver，不另造不必要顶层任务。若构建约束需要新任务，先记录真实名称并同步第 15 节命令/CI/文档。

**Compose：**

- 拟在 `compatibility/compose-consumer/legacy130/` 增加最小 fixture，用 Kotlin1.9.25、Compose compiler1.5.15 对原版 core/compose 编译实际`@Composable`调用，含默认参数和 onSubscribed。
- 复用生成工程的编译环境，消费该已编译 fixture，在真实 Composition 中检查订阅、回调更新、生命周期和取消。普通候选源码 fixture 继续存在，分别证明源码接入与二进制链接。
- 检查 fixture 在进入最终应用打包前的 class 摘要保持不变；R8 可重写最终 DEX，不要求两版 APK/DEX 字节相同。不能在切换候选库时重新编译原版 fixture 而声称“同字节码”。
- 默认扩展 `verifyMavenLocalComposeConsumer` 的生成模块与输入，依旧由 API21/35 的现有消费者执行；没有新增实现前不能宣称该入口已经覆盖旧 Composable 链接。

### 11.4 CI 和负向验收

core 新基线与三份符号比较进入 `consumers` job；Android/Compose fixture 在 API21/35 中运行。报告纳入现有 artifact，保留候选 manifest、原版/候选摘要、fixture 摘要、真实依赖和执行阶段。保留稳定 `verify` 汇总及历史版本矩阵，不添加非阻塞例外。

负向检查必须能拒绝：原版摘要错误、候选运行图混入旧库、fixture 被重新编译或摘要变化、必要阶段/报告缺失、旧成功报告残留。现有生成脚本测试可以扩展，不为了检查字符串新增大框架。

**W5 退出条件：** 三个原版输入可信、core 同字节码运行及三模块符号验证通过、Android/Compose 旧 fixture 在候选真实运行，报告身份完整。静态符号通过不等于设备运行通过。

## 12. W6：定向性能验证

1. 将 `benchmarks/jvm/build.gradle.kts` 和 `benchmarks/jvm/summarize.py` 中 baseline 版本/摘要一起更新为核验后的正式 1.3.0；核对汇总表头中硬编码版本、`summarize_test.py`、README 与 identity 输出。历史 results 不改写。
2. 复用现有 JMH，不添加生产依赖。正式测量至少对照 `BusBenchmark` 的 `noSubscribers,stickySend,active1`；它们分别反映基础发送、sticky 路径和实际活跃收集。新重入的正确性留在单测，不传不存在的 JMH 场景名。
3. 基线与候选使用相同场景、依赖、heap、fork、预热和测量参数。正式命令不带 benchmark.smoke；现有配置为 3 forks、5×1s 预热和 5×1s 测量。
4. 保存 JSON、identity、日志、机器/JDK 及当时负载。以同一工作量比较吞吐、采样耗时与分配，区分批次和单事件。不要与其他重型构建并行；不能停止不属于本任务的进程，应等待合适环境并记录限制。
5. 定向结果使用单独报告名，不调用完整汇总器冒充三组完整对照。只有完成 baseline20/candidate22/shared16 完整组和身份验证后，才可按现有归档流程更新完整报告。
6. 超过噪声的约 10%退化触发调查和有理由的反向顺序复测，不直接设共享 CI 硬阈值。因修复正确性带来必要开销时，记录数据、原因和取舍，不删掉正确性或指标绕过问题。
7. 本机反向复测若仍受后台负载干扰，可在已授权的 PR 正文显式加入 `<!-- flowbus-formal-performance -->`，复用现有 consumers runner 串行取得相同参数的正式 baseline/candidate 数据；步骤不替换 smoke、扩大矩阵或设置硬阈值。报告纳入现有 artifact，执行时间计入原 20 分钟 job 超时；需先核对历史耗时仍有余量。当前首轮 consumers 用时约 4 分 30 秒，正式测量另需约 6 分钟。

**W6 退出条件：** 针对最终相关实现有可比正式数据和明确结论；若环境无法测量，标“待验证”并说明影响，不能将 smoke 作为通过。正式版本切换可以在测量前完成；当前已采用 1.3.1 候选测量。报告文件名仅为标签，实际身份以 JSON 中版本与 JAR 摘要为准。测量后若纯版本切换，至少重跑 smoke 并核对实现未变，不把旧版本 JAR 测量称为正式 1.3.1 制品实测；性能相关输入变化时重做受影响正式测量。

## 13. W7：版本、文档、候选和集成

### 13.1 版本状态切换

修复阶段可保持现有源码版本，在隔离本地仓库验收；不把它称为新的公开 1.3.0。避免为了调试反复改版本。

进入正式候选时，同一批次更新以下文件并重新验证：

| 位置 | 候选阶段目标 | 发布后动作 |
| --- | --- | --- |
| gradle.properties | VERSION_NAME/APP_VERSION_NAME=1.3.1；APP_VERSION_CODE 取当前最大值递增，当前 11 时为 12 | 保留受测值 |
| gradle/libs.versions.toml | flowbus_version=1.3.1，与源码候选一致 | 保持 |
| app 与 library-android 的 androidTest/AndroidManifest.xml | versionName 同步 1.3.1；test versionCode 核对安装约束后递增，当前 2 时建议 3；保持标签 | 保留受测值 |
| FlowBusReleaseReadinessTest.kt | 分开最新公开版本 1.3.0、源码候选 1.3.1；同时校验两者对应文档/manifest | 公开验收后 releaseVersion 改 1.3.1，不靠删断言完成 |
| 新增中英文 release-notes-1.3.1.md、CHANGELOG | 标注候选/未发布，列修复、行为变化、验证及已知限制 | 写真实日期、commit/run/摘要/deployment/公开验收 |
| 根/模块 README、文档索引、发布清单 | 正式安装仍指 1.3.0；明确源码候选与使用限制 | 公开 Central 可解析并验收后更新正式安装 |

不全仓替换`1.3.0`；历史 fixture、tag、发布证据和新冻结基线必须保持原版号。具体测试名称/源码文本断言可能随实现变化，按语义维护，不机械扩大成全仓文案整理。

### 13.2 文档与升级说明

更新本次实际受影响的 core/Android/Compose 中英文指南、KDoc、TROUBLESHOOTING 及兼容矩阵。至少说明：

- sticky 重入修复的触发场景与发送接受/业务处理完成的区别。
- openScope 绑定无 Job 时保持抛错，但不留下新登记；已有 handle 的失败绑定不会自动销毁原 handle。
- 合法阈值与无效参数同步拒绝；正常已销毁 owner 为无订阅行为；inline 消费者获得完整修复须重新编译。
- Compose 新增阶段和 1.3.0 原版 fixture 的实际覆盖，历史阶段数量保持原样。
- 1.3.1 回退到 1.3.0 会恢复三个旧问题，不涉及移除 1.3.0 新增 API；沿用发布清单时改掉其中面向旧版 1.2.0 的历史回退建议。

### 13.3 候选身份与验证顺序

1. 集中完成相关修改后执行第 15 节本地质量检查。记录实际执行/增量复用/缓存命中；有需要时使用现有 freshTests 属性，不全图 rerun。
2. `verifyMavenLocalArtifacts`生成三个坐标到规范路径`build/candidate-maven/`，只写隔离本地仓库。随后 seal、check，再执行带`reuseCandidate=true`的消费者。
3. seal 当前只记录 HEAD 和 Maven 文件集合/摘要，不包含未提交 patch，也不证明 JAR 来自当前源码。本地必须额外保存相关 diff 和新增文件摘要；正式验收以干净 CI checkout 为准。
4. 封存后不要再不带 reuseCandidate 调用消费者入口。默认入口会重发三个当前版本目录，使之前候选身份失效。源码/版本/构建输入变化时重新生成并 seal，不只改 manifest“认领”旧包。
5. 当前目录可能保留旧版本制品；先盘点 build/candidate-maven，备份有价值证据，仅清理本轮可确认的旧候选内容或使用新的专用验证工作区。不能直接删除未知内容或共享`.m2`。
6. 在现有授权范围内提交/推送/PR；PR 运行所有必要矩阵，合并后核对对应 main CI。本地不为文档检查触发云端；新代码/版本等最终输入需要真实 CI。

**W7 退出条件：** 所有必需检查成功，最终正式版本候选与 commit/run/attempt/manifest/artifact 摘要一致；没有缺阶段、意外跳过、取消或待修问题。不能混用不同 attempt 的候选和报告。

## 14. W8：发布与公开验收

本节是执行说明，不是发布授权。沿用[发布清单](release-checklist.md)“默认本机固定候选发布流程”；这里强调下一版本的输入和边界，不复制维护第二套凭据/API 说明。

1. 确认本次会话对上传、正式 release、tag 和 GitHub Release 各项操作的授权；核对仓库`logan0817/FlowBus`及三个 1.3.1 坐标。
2. 下载最终正式版本成功 main CI 的候选与报告，验证 run/attempt、源码提交、ZIP 摘要、manifest 及文件摘要。签名上传使用这批文件，不能重新编译替换。
3. 复用本机已存在的 GPG keyring/agent 和 Central 凭据，重新确认签名身份；不导出私钥、不打印配置值、不添加发布 secrets。三坐标各五个主文件，共 15 个，不上传 maven-metadata-local.xml。
4. USER_MANAGED 上传后保存 deployment ID；响应不明确先查询该批次，不能盲目重传。VALIDATED 后回下载核对制品、签名及摘要，再对同一 ID 执行已授权 release。
5. 等同一部署 PUBLISHED，再从公开 Central 下载并比对；独立消费者只从 Central 解析 FlowBus 三个坐标，确认没有本地候选或错误版本替代，运行 core Java8 和 Android/Compose release R8 及必要设备验收。
6. 取得真实证据后更新正式安装文档、更新日志、发布说明，并把`v1.3.1` tag/GitHub Release 绑定实际受测源码提交；核对远端结果。

当前 Gradle `releaseToMavenCentral` 会构建/上传，无 dry-run 时还会执行其 clean 依赖，不能当成“上传固定 CI 文件”快捷入口。`prepareCentralBundle`不属于本仓库，旧聊天的临时发布脚本不属于永久命令；若恢复本机流程，先核对现存实现和官方接口，再操作。

**W8 退出条件：** 三个公开坐标、签名、摘要和消费者均验收，发布记录/tag/Release 身份一致。任何阶段缺授权或凭据时记录“待执行”，仍交付已完成本地结果。已发布坐标不可覆盖；未发布批次的 drop 同样核对授权和准确 ID。

<a id="verification-commands"></a>

## 15. 验证命令手册

### 15.1 环境入口

以下命令从仓库根目录执行。macOS 本机已知构建 Skill 可用时，可在当前 shell 定义短函数；先检查路径，不写入全局 shell 配置。若路径不存在，读取当前可用 Skill/项目环境说明寻找真实入口，不安装另一套工具链。Windows 使用相应 PowerShell 脚本和同名 Gradle 任务。

```bash
FLOWBUS_BUILD_HELPER=/Users/logan/.codex/skills/android-studio-build-env/scripts/check-android-env.sh
test -x "$FLOWBUS_BUILD_HELPER"
"$FLOWBUS_BUILD_HELPER" --project-root . --check
flowbus_gradle() {
  "$FLOWBUS_BUILD_HELPER" --project-root . --run-gradle "$@"
}
```

`test -x`或环境检查失败时不要继续复制后续命令。`FLOWBUS_JAVA8`须由执行者设为已核验的本机 Java8 可执行文件；`FLOWBUS_JAVAP`为已核验 JDK17 的 javap。下列检查故意在未设置时失败，禁止使用占位路径或以 JDK17 伪装 Java8：

```bash
: "${FLOWBUS_JAVA8:?先设置为现有Java8可执行文件}"
: "${FLOWBUS_JAVAP:?先设置为现有JDK17的javap}"
"$FLOWBUS_JAVA8" -version
"$FLOWBUS_JAVAP" -version
```

### 15.2 代码与 API（现有，不发布）

W1 定向运行已有类中的新回归，必要时使用 Gradle `--tests`；类和方法筛选必须核对真实名称，确认不是 0 tests。修改完成后执行：

```bash
flowbus_gradle apiCheck :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest \
  :library-android:lintRelease :library-compose:lintRelease :app:lintRelease \
  --warning-mode=all
```

确需本轮实际执行全部 JVM Test 时附加`-Pflowbus.verification.freshTests=true`；相同输入成功后不重复。公开 API 有差异时先查原因，本版不预期增加或删除公开符号。

### 15.3 脚本检查（现有，按改动范围选择）

```bash
python3 -B -m unittest discover -s compatibility -p '*_test.py' -v
python3 -B -m unittest discover -s compatibility/core-binary-consumer -p '*_test.py' -v
python3 -B -m unittest discover -s compatibility/android-consumer -p '*_test.py' -v
python3 -B -m unittest discover -s compatibility/compose-consumer -p '*_test.py' -v
python3 -B -m unittest discover -s benchmarks/jvm -p '*_test.py' -v
bash -n compatibility/prepare-api21.sh
bash -n compatibility/run-api21.sh
bash -n compatibility/run-api35.sh
```

CI 改动还执行现有可用平台静态检查；未安装工具不为文档方案临时安装。YAML 可解析不等于 GitHub 依赖/表达式正确，平台行为由真实 CI 验证。冻结 API 快照保留原字节，普通格式检查可以按现有项目排除它们，不能排除普通源码。

### 15.4 本地候选（现有，只写隔离仓库）

```bash
flowbus_gradle verifyMavenLocalArtifacts \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" --warning-mode=all
python3 -B compatibility/candidate_bundle.py seal
python3 -B compatibility/candidate_bundle.py check
flowbus_gradle verifyMavenLocalCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.verification.reuseCandidate=true \
  "-Pflowbus.consumer.java=$FLOWBUS_JAVA8" -Pflowbus.consumer.expectedJava=1.8 --warning-mode=all
```

当前这组消费者任务只能证明现有覆盖；W5 修改完成后才包含拟新增 legacy130。执行末尾再 check 候选。1.0.7/1.1.0/1.2.0 按原入口继续验证，不能用最新基线替代旧基线。

### 15.5 1.3.0 旧字节码与符号（W5 完成后才能执行）

```bash
flowbus_gradle verifyMavenLocalBinaryCoreConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.verification.reuseCandidate=true \
  -Pflowbus.consumer.baselineVersion=1.3.0 \
  "-Pflowbus.consumer.java=$FLOWBUS_JAVA8" -Pflowbus.consumer.expectedJava=1.8
python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --runtime-report build/maven-local-binary-core-consumer-1.3.0/build/reports/binary-compatibility.json \
  --baseline-api-snapshot compatibility/core-binary-consumer/api/flowbus-core-1.3.0.api \
  --javap "$FLOWBUS_JAVAP" --output build/core-1.3.0-jvm-symbol-verification.json
```

Android/Compose 符号检查复用同一脚本的`--baseline-artifact`、`--candidate-artifact`、`--baseline-api-snapshot`参数；候选路径读取当前 VERSION_NAME，不写死开发期 1.3.0。W5 实现后把三份具体命令加入现有 CI 及对应消费者 README，不能只在聊天中保留。

### 15.6 设备（CI 现有；本机有额外前置条件）

CI 复用`prepare-api21.sh → run-api21.sh`和`run-api35.sh --prepare → run-api35.sh`，每个 job 独立工作区，实际模拟器为对应 API 的 emulator-5554。它们会安装应用、调整专用模拟器设置、运行测试，不能作为纯构建命令在用户真机直接执行。

本机 APK 安装按当时适用的`android-adb-install` Skill。当前`device_verification.py`会直接 adb install，尚无预安装/skip-install 模式；Gradle connected 任务也会自动安装。可选路径：

- 优先让 API21/35 正式消费者门禁在已授权 CI 执行；本机只做补充 instrumentation，Skill 安装后用明确 serial 和包名执行`am instrument`。
- 若确需本机消费者 driver，先实现并测试明确的预安装模式，核验设备/包/本次 APK 及安装证据，默认 CI 路径保持原样。此项是有需要才做的辅助变更，当前不能假设存在该参数。

不要先 Skill 安装再原样运行 driver，后者仍会重复安装。设备进程退出码 0 不必然等于测试通过，必须解析 instrumentation/driver 结果；每阶段核对 runId、全部 PHASES 和同包 UI，保留失败现场。

### 15.7 性能（W6 基线切换后，现有任务）

```bash
flowbus_gradle -p benchmarks/jvm selfCheck -Pbenchmark.target=baseline
flowbus_gradle -p benchmarks/jvm selfCheck -Pbenchmark.target=candidate
flowbus_gradle -p benchmarks/jvm jmh -Pbenchmark.target=baseline \
  '-Pbenchmark.filter=.*BusBenchmark.*' -Pbenchmark.scenarios=noSubscribers,stickySend,active1 \
  -Pbenchmark.report=baseline-1.3.0-sticky-fix
flowbus_gradle -p benchmarks/jvm jmh -Pbenchmark.target=candidate \
  '-Pbenchmark.filter=.*BusBenchmark.*' -Pbenchmark.scenarios=noSubscribers,stickySend,active1 \
  -Pbenchmark.report=candidate-1.3.1-sticky-fix
```

不带 smoke，不用这两份定向 JSON 运行完整`summarize.py`。真正输入变化后重测相关场景；不为得到期望比值重复采样挑结果。

### 15.8 发布任务图（现有，仅 dry-run）

```bash
flowbus_gradle verifyCentralDeploymentName --warning-mode=all
flowbus_gradle releaseToMavenCentral --dry-run --warning-mode=all \
  -PmavenCentralUsername=dry-run-unused -PmavenCentralPassword=dry-run-unused
```

此处必须保留`--dry-run`，仅检查任务图，不签名、不上传、不执行 clean。真正固定候选上传按第 14 节和发布清单，不能通过删掉 dry-run 切换过去。

<a id="delivery-handoff"></a>

## 16. 交付、验收勾选与恢复模板

### 16.1 开发完成与发布完成分开验收

- [x] W1 正式回归已保存，失败来自三个真实行为问题。
- [ ] W2 设计时点/锁说明和 S 矩阵有证据，F1 转绿，旧清理/背压/取消契约保留。
- [ ] W3 O 矩阵覆盖，无遗留新 handle，也不误清理旧数据。
- [ ] W4 L 矩阵覆盖，Compose 真实候选阶段成功，无效便捷调用不获取来源。
- [ ] W5 三坐标原版摘要/API/fixture 可信，旧字节码在候选运行，历史基线保留。
- [ ] W6 正式定向性能数据与限制可追溯，未将 smoke 当性能结论。
- [ ] W7 当前最终输入的 API、单测、lint、消费者、API21/35、Kotlin 矩阵及 verify 成功。
- [ ] 当前受影响文档已更新，历史记录未被覆盖，版本与 manifest 一致。
- [ ] 最终 diff 无凭据、日志、签名、临时 probe、无关修改；格式检查与链接检查完成。
- [ ] W8 已获相应授权并完成公开发布验收；若未执行，明确写“开发已验证，发布待执行”。

测试数量可以因新用例合理增加，不能把 255 作为硬编码目标；确认发现的用例执行、无跳过、错误路径被覆盖。没有 formatter 时沿用现有风格并运行 diff/结构检查，不能声称运行过不存在的格式工具。

### 16.2 后续 AI 的进度与交接记录

每阶段结束、会话中断或交给另一 AI 时，在本节追加一个简短的“最近实施状态”，更新 W 表，不另开重复的方案文档。原审查数字保持为历史证据。详细日志放 build 目录，受控文档只保留必要摘要。

```text
最近实施状态：YYYY-MM-DD
仓库/分支/HEAD：
工作区未提交变更及归属：
W0–W8状态：未开始 / 进行中 / 本地通过 / CI通过 / 待授权 / 完成
本阶段最终修改及契约决定：
验证：命令；退出码；执行/缓存；结果数；报告路径
候选：源码版本；commit；patch/新增文件摘要（若有）；manifest摘要
远端：PR；run ID/attempt；受测head及合成merge SHA；各job结果
发布（若已授权执行）：三坐标；固定文件摘要；deployment ID/状态；tag
当前授权范围：只用于本会话恢复判断，新会话须依据新用户指令
未解决项：确认缺陷 / 待证疑点 / 环境限制；具体影响
下一步：一项可直接执行的操作与前置条件
已验证可复用的结果及失效条件：
```

不要把“执行过命令”写成“验证通过”。恢复时先检查日志/进程/远端实际状态，再决定重试；网络/上传结果不明时尤其避免重复副作用。最初 3 项临时审查 probe 不等于 W1；W1 完成必须引用迁入受控测试后的红测试证据。

### 16.3 建议提交组织与工作量

在获得提交授权后按逻辑组织：`fix(core): 修复 sticky 重入缓存顺序`、`fix(core): 避免 scope 绑定失败遗留登记`、`fix(android): 拒绝无效生命周期收集阈值`、`test(compat): 增加 1.3.0 原版消费者基线`；必要版本/文档提交单独可审查。标题为示例，不要求拆成固定数量，不提交独立红测试候选来触发无意义 CI。

工作量以当前详细范围重新估算：回归与 F1 设计/实现约 2–4 人日，F2/F3 及 Compose 集成约 1–2 人日，新增三模块兼容 fixture 约 1–2 人日，性能/候选/发布验收约 1–2 人日，总计约 5–10 人日。此估算替代初版 4–7.5 人日的粗略数字；主要不确定性是 F1 并发模型和旧 Composable 字节码集成，不是发布承诺。

<a id="implementation-prompt"></a>

## 17. 可直接交给 AI 的实施提示词

在项目中打开后，可复制下面的提示词。它只授权本地开发和验证；如希望同一会话完成提交/PR/发布，应另行明确相应操作范围，避免把方案文案当长期授权。

```text
请在当前 FlowBus 仓库实施 docs/implementation-plan-1.3.1.md。
先完整阅读方案、适用 AGENTS.md、CONTRIBUTING.md 和相关 Skill，检查实际分支、HEAD、工作区及已有进度。
本次授权按方案完成本地生产代码、正式回归、兼容fixture、必要文档和本地构建验证，不执行未获授权的远端或设备写入。
按 W0–W8 的依赖推进；优先确认 W1 的三项行为复现，再完成 W2 sticky设计门禁、W3 scope异常安全、W4生命周期入口及 W5兼容基线。
不要只给我建议或重新写一份计划。已有有效测试和环境直接复用，在范围内自主处理普通实现选择，不逐阶段重复索要确认。
每次验证必须对应最终相关输入；当前不可用的1.3.0验证入口先按W5实现，不能把计划命令当现有能力。
保持全部现有公共API、默认投递/背压/取消/所有权语义；不能用延迟、重试、改快照、放宽断言或减少矩阵获得通过。
修复sticky时必须解释接受/提交/代际/订阅交界与锁行为，不能只令replay=1的单个用例通过。
同一checkout只运行一个Gradle命令，同一设备上的测试和driver串行，安装遵守当前设备Skill。
完成本地可执行工作后更新方案中的W状态和最近实施记录，报告改动、真实验证、剩余门禁与所需具体授权。
若需要改变公开契约、扩大范围、覆盖无法区分的用户修改，或需要新的外部操作授权，先完成独立可推进的准备，再集中说明需决策的问题。
本次目标是1.3.1，不实现第18节的1.4.0候选功能。
```

恢复已有实施会话时可用：

```text
继续实施 docs/implementation-plan-1.3.1.md。先核对第6节任务状态、第16节最近实施记录、Git差异、未结束进程和远端结果，确认已完成事项再继续下一个未完成任务。不要重置工作区或重做仍覆盖当前输入的成功验证；授权以本会话已有用户指令为准。
```

## 18. 保留的 1.4.0 方向与审查限制

1.4.0 候选为可选诊断出口、可运行接入测试示例和测量驱动的开销优化。必须先有真实使用场景、API/兼容评审和默认开销证据；1.3.0 已经具备的 Java、Compose、订阅通知、通道配置与快照不重复规划。本轮不增加观察器、flowbus-testing 模块、可靠队列、ACK 或跨进程能力。

本方案不等于逐行安全认证；没有 Android ART 性能结果、真实业务项目接入结果、sticky 订阅交界竞态或 API35 真实失焦的新复现。执行者应明确报告这些限制，并在对应任务中取得所需证据。写完方案、已有 255 项测试通过、或历史 CI 全绿，都不能替代 1.3.1 修复完成与发布验收。

## 最近实施状态 · 2026-11-11

- 当前分支：`fix/release-1.3.1`；起点 `130c4aa`，工作区包含原规划文档以及本轮实现。正式发布不在本次目标内。
- W0：核对工作区、分工与工具链，复用 JDK17 / Gradle8.7 / 共享缓存；Java8运行环境已验证。
- W1：三项正式回归修复前均按预期失败，报告 `build/verification-1.3.1/w1-red-results/`，日志 `w1-red.log`。
- W3：预校验Job后委托原重载；scope测试与关闭测试定向通过，日志 `w3-scope.log`。最终集成仍需覆盖后续输入。
- W2/W4：core 201、Android 81、示例 JVM 2 项通过；新增 Android Main.immediate 重入、跨线程 dispatcher/context、通知异常及提前结束回归均通过。S01–S04/S12/S15 由 `FlowBusStickyPublicationTest` 覆盖，S05–S11/S13 同时复用 sticky consume、subscription、channel config 与 scope close 测试，S14 位于 `FlowBusAndroidStickyConsumeTest`。L01–L07 见 `LifecycleThresholdTest` 与 `InstanceCollectEventTest`。Compose 真实设备阶段待验收。
- W5：公开 1.3.0 三个主制品 SHA-256 与发布证据一致，三份 API 快照与发布提交原字节一致。`w5-core130-fixed.log` 同一原版编译 class 在原版/候选 Java 8 均通过；`w5-identity-final.log` Android/Compose release R8 与冻结字节码/候选 runtime 摘要通过。五项实际 Gradle 负向检查拒绝错误摘要、class 变化、原版运行图污染及缺报告，且无旧成功报告；恢复正常输入后通过。
- W5 的旧版符号检查曾将 private 内部类的 public 方法算作外部 API。现依据原版 `InnerClasses` 的明确 private 标记区分，仍记录四项 raw 差异；公开/受保护嵌套类及 Kotlin internal 的公开 JVM 成员不豁免。1.0.7/1.1.0/1.3.0 符号预检通过，9 项脚本回归通过；历史版本的最终运行仍需最终候选 CI。
- W6：基准改为正式 1.3.0，汇总器同步；脚本回归与两版本 selfCheck 通过，正式三场景测量进行中，尚无性能结论。
- W7：源码版本已为未发布 1.3.1；`w7-local-quality-final.log` 三模块 API、284 项 JVM 测试、全部 release lint、示例 debug/release R8 和发布名称检查通过，lint XML 均 0 error/0 warning。后续文档输入修改需最终复验；PR/main CI、API21/35 及完整 Kotlin 矩阵尚未执行。
- 本地封存补充记录 `local-source-identity.json`、`workspace.patch` 和 `untracked-inputs.json`；HEAD 仍只标识基线，不能代替未提交 patch。正式验收须使用干净 CI checkout。用户已持续授权本次会话在 logan0817/FlowBus 的 fix/release-1.3.1 提交、推送、创建或更新面向 main 的 PR、现有 CI，并在性能验收和全部必需检查通过后合并与核验 main CI；不包含 Maven 上传、正式发布、tag 或 GitHub Release。
- 验证由主执行者串行运行Gradle；各执行者按文件分工，日志保留在 `build/verification-1.3.1/`。

### 首轮 PR 验收与性能复核

提交 `c90cb55` 已推送至 `fix/release-1.3.1`，草稿 [PR #16](https://github.com/logan0817/FlowBus/pull/16) 首轮 [CI 42969426491](https://github.com/logan0817/FlowBus/actions/runs/42969426491) 的九项检查全部成功。PR 合成提交为 `475c0f141c2b40b2288e7cc79952044143160546`，attempt=1；284 项 JVM 测试重新执行，三份 lint XML 均无 issue。四个 Kotlin 版本、Java8、四个历史 core 原版字节码基线、公开符号、R8、API21/35 均通过。

下载并核对 API21/35 的 Android 8、effect 12、policy 11 阶段：本次 runId 日志、同包 UI 和全部阶段一致；API21 另保留 Navigation/NavHost 7/8 阶段。API35 的库 instrumentation 3 项与示例 release instrumentation 20 项均无失败或跳过。五份已下载报告中的候选清单一致；十份兼容运行/冻结报告指向同一候选摘要。云端 core JAR 与本地性能候选的 98 个 class 和全部 100 个文件内容一致；ZIP 容器摘要分别保留，不谎称整体文件相同。

本机首轮性能发现约 48 B/事件的 sticky 额外分配，JFR 后将 replay=1 改为直接保存最近值、计数直接读 size；修复后全部行为/API/lint 通过。正式吞吐模式分配从原版约 120 B/事件变为约 80 B/事件。但多轮耗时受同机高负载和 swap 干扰，普通未修改路径也明显波动；[定向记录](../benchmarks/jvm/results/1.3.1/report.md)保留全部数据，不把它们当耗时验收。下一次 PR CI 在现有 consumers 中显式运行正式对照。取得可信数据、完成后续最终 PR/main CI 前，W6/W7 不勾选完成，PR 保持草稿。
