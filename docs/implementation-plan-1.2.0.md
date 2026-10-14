# FlowBus 下一版本实现方案（建议 1.2.0）

> 状态：实施中，候选验收未完成。创建日期：2026-10-14。基线：已发布 1.1.0。
>
> 通道配置核心与 Android 入口已在本地 1.2.0 候选落地；不能直接用于已发布 1.1.0。版本配置已更新，正式安装示例保留 1.1.0 并另列本地候选用法。完整性能和最终设备/CI 门禁尚未完成，见文末进度。

## 1. 版本目标与取舍

建议将下一版本定位为“按通道配置、契约清晰、性能可测”的兼容性增强版本，暂定 1.2.0。核心新增能力是同一 bus 内按通道分别配置缓冲与溢出策略；同时把已有事件、sticky 和 scope 行为转成可复现的使用说明与回归验证。

用户收益：刷新通知和较慢的业务通知可以共用一个有明确所有者的 bus，分别选择丢弃策略或背压策略；接入者可以直接判断事件在哪些条件下保留、丢弃、取消和重放。

| 优先级 | 工作项 | 完成条件 |
| --- | --- | --- |
| P0 | 固定投递契约与旧行为基线 | 明确普通/sticky 分流、订阅就绪、背压、取消及关闭边界；必要行为有可运行验证 |
| P1 | 通道级配置 | core、scope、Android owner 和实例接入完整；配置绑定原子且诊断结果使用实际策略 |
| P1 | 建立性能基线 | 发布 1.1.0、候选版与原生 SharedFlow 的可复现测试方法及实测报告 |
| P1 | 收敛推荐用法与资源管理说明 | 每种场景有首选入口，示例说明发送源、订阅生命周期及清理责任 |
| 发布门禁 | 兼容、产物与设备验证 | 最终候选通过现有门禁，并覆盖新配置的真实消费者用法 |

本版保留：普通事件默认缓冲 64、DROP_OLDEST、sticky 默认 replay 1、普通/sticky 独立分发、默认异常 Rethrow、现有 scope 关闭及旧 Flow 引用语义。

本版不纳入：可靠消息队列、ACK/重试/持久化、跨进程或跨 App、KMP、事件继承分发、优先级与拦截、新 Compose 发布模块、自动删除零订阅通道、破坏性 API 精简、完整泛型运行时校验。它们分别涉及新的协议、平台、生命周期或兼容承诺，应有独立需求后再设计。

## 2. 当前依据与实际缺口

| 已确认现状 | 依据 | 下一版本处理 |
| --- | --- | --- |
| 缓冲与溢出策略按 bus 统一配置 | [FlowBusConfig](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBusConfig.kt)、[FlowBusStore](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBusStore.kt) | 新增按通道覆盖，旧默认保持 |
| 普通与 sticky 使用不同内部流 | [FlowBusStore](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBusStore.kt) | 保持行为，补齐迁移说明与直接交叉订阅验证 |
| DROP_LATEST 下 sticky replay 可不同于实时收到的值 | [FlowBusCompatibilityTest](../flowbus-core/src/test/java/com/logan/flowbus/core/FlowBusCompatibilityTest.kt) | 复用现有测试，增加通道覆盖策略下的验证 |
| owner、显式实例与 core scope 均有接入入口 | [FlowEventBus](../library-android/src/main/java/com/logan/flowbus/FlowEventBus.kt)、[实例收集入口](../library-android/src/main/java/com/logan/flowbus/InstanceCollectEventExtension.kt) | 新配置覆盖这些来源，沿用已有收发 API |
| 移除通道不取消旧 Flow；scope 清理有在途操作保护 | [FlowBusScope](../flowbus-core/src/main/java/com/logan/flowbus/core/FlowBusScope.kt)、[core 边界说明](../flowbus-core/README.md#常见边界说明) | 保持所有权语义，验证配置与同名重建的关系 |
| 仓库有 API、二进制消费者、R8 和设备门禁 | [CI](../.github/workflows/ci.yml)、[发布清单](release-checklist.md) | 增加 1.1.0 基线和新配置消费者，不仅更新 API 快照 |
| 本次检查未发现可用于横向比较的性能基准 | 当前仓库结构与验证入口 | 建立基准后再决定是否优化热路径，不预设提速比例 |

这些行为中有多项已经有测试或文档；实施时先复用，只有缺口才补充。当前分析未确认需要改变旧语义的 Bug，也没有吞吐量、延迟或内存占用的实测排名。

## 3. P0：先固定契约，再扩展配置

### 3.1 对使用者明确的行为

| 场景 | 必须说明和验证的结果 |
| --- | --- |
| 普通事件无人收集 | 不为未来订阅者保存；extra buffer 不是离线队列 |
| 注册后立即发送 | 注册返回 Job 不等于底层订阅就绪；不依赖固定延时或“相邻语句”保证就绪 |
| SUSPEND 下调用 emit | 只在存在活跃订阅者且需要等待缓冲等条件满足时背压；不代表业务完成 |
| DROP_OLDEST / DROP_LATEST | 发送未被拒绝仍可能有事件被丢弃；结果不能用作投递回执 |
| 普通/sticky 交叉发送和订阅 | post 只进入普通流，postSticky 只进入 sticky 流；同名不合并 |
| sticky 后订阅 | 显式选择 sticky；生命周期重启可能再次重放，不承诺副作用只执行一次 |
| sticky consume | 读取并清空当前 replay；不撤回已进入实时投递的数据，不阻止后续写入 |
| sealed / 父类型事件 | 发送与订阅使用相同父类型或稳定 channel；没有自动继承分发 |
| 回调异常与取消 | 总线收集入口使用来源错误策略；通用 Flow 收集直接传播；取消不当作普通错误 |
| scope 关闭或移除 | 不主动终止已持有的旧 Flow；新句柄与旧句柄的所有权保持区分 |

### 3.2 文档和示例调整

- 在 Android/core 现有 README 增加简短的“从 EventBus/LiveEventBus 迁移”说明，突出 sticky 分流、生命周期阈值及父类型通道。
- 将全局通知、owner 内通知、实例注入、任意 Flow 组合分别指定一个首选入口；保留旧 API，不在 1.2.0 增加弃用警告。
- 用一个普通/sticky 对照示例和一个慢订阅者示例展示结果，不再复制多套等价接入教程。
- 更新 TROUBLESHOOTING，先检查 bus/owner/key、订阅活跃状态，再检查溢出策略。
- 清理仍把已发布 1.1.0 行为称为“未发布修订”的表述，保留真实历史发布记录。

## 4. P1：通道级配置设计

### 4.1 配置模型

新增 `FlowBusChannelConfig`，只负责缓冲与溢出策略。logger、errorHandler 仍由 bus 管理，避免把通道配置扩展为另一套总线配置体系。

建议字段如下，`null` 表示继承所属 bus 的对应默认值：

```kotlin
// 本地 1.2.0 候选 API；正式 1.1.0 尚未提供。
data class FlowBusChannelConfig(
    val normalBufferCapacity: Int? = null,
    val normalOverflowPolicy: BufferOverflow? = null,
    val stickyReplay: Int? = null,
    val stickyExtraBufferCapacity: Int? = null,
    val stickyOverflowPolicy: BufferOverflow? = null
)
```

其中普通与 sticky 的 overflow 分别继承 `FlowBusConfig.overflowPolicy`。内部解析为不可变的完整配置，收发热路径读取已绑定结果，不重复合并 nullable 字段。

不向现有 `FlowBusConfig`、`FlowBusPostResult` 或各类 snapshot 数据类追加构造字段，保留原有构造器、默认参数、copy 和 component 的 JVM 描述符。

### 4.2 最小公开入口

在 `FlowBus`、`ScopedFlowBus`、`FlowBusScope` 各增加一个同名实例方法：

```kotlin
// 分别作为三个现有类型的成员方法；仅展示拟议签名。
fun <T : Any> configureChannel(
    key: EventKey<T>,
    config: FlowBusChannelConfig
)
```

- `DefaultFlowBus` 通过已有 `raw().configureChannel(...)` 使用，暂不复制一组转发或 reified 重载。
- 使用 core `EventChannel` 时，通过已有 `asEventKey()` 配置，不改变 channel 的身份、相等性或发送方式。
- `FlowBusScope` 的配置操作必须进入已有 `withOpenOperation` 路径，固定在途操作所使用的 store，并参与关闭等待。

Android 层增加 `FlowEventBus.configureChannel(key, config)`，并提供一个与现有 Android channel 配套的便利入口：

```kotlin
// 新 API 草案；owner 指定来源总线，须在主线程获取。
@MainThread
fun <T : Any> configureEventChannel(
    channel: EventChannel<T>,
    config: FlowBusChannelConfig,
    owner: ViewModelStoreOwner = GlobalViewModelStore
)
```

该入口在指定 owner 上获取既有 `FlowEventBus` 后转发到 core，不另建实例。显式注入的 core bus 直接调用实例方法，随后沿用 `collectEventFrom` / `collectEventFromSuspending` 收集。

### 4.3 目标用法

下面是未来 core API 的示例，`CoroutineScope` 由调用方管理：

```kotlin
val bus = FlowBus()
val refresh = eventKey<RefreshEvent>("home.refresh")
val workProgress = eventKey<WorkProgress>("work.progress")

bus.configureChannel(
    refresh,
    FlowBusChannelConfig(
        normalBufferCapacity = 1,
        normalOverflowPolicy = BufferOverflow.DROP_OLDEST
    )
)
bus.configureChannel(
    workProgress,
    FlowBusChannelConfig(
        normalBufferCapacity = 16,
        normalOverflowPolicy = BufferOverflow.SUSPEND
    )
)

// 注册收集并确认实际活跃后，由业务流程执行发送。
bus.post(refresh, RefreshEvent())
bus.emit(workProgress, WorkProgress(percent = 10))
```

`RefreshEvent` 与 `WorkProgress` 由业务定义。这里的 SUSPEND 用于有活跃慢订阅者时施加背压；任务进度恢复仍应依赖业务状态，不能将该示例解释为可靠任务队列。

### 4.4 绑定、冲突与生命周期

配置身份为“当前 store + eventName”。同一 key 的普通和 sticky 配置一次性绑定；两种流分别使用各自字段。

1. 首次 `configureChannel` 绑定运行时类型与解析后的策略，但不创建普通/sticky Flow，不生成发送计数。
2. 未显式配置时，第一次 post/emit/flow 或对应 sticky 操作绑定 bus 默认策略；配置不得在已有使用之后变更。
3. 已绑定后，同类型、相同有效配置的重复调用为幂等，便于 owner 配置重建时复用。不同类型抛 `IllegalArgumentException`，不同有效配置抛 `IllegalStateException`；不静默采用后来的配置。
4. 参数校验与冲突检查失败不得留下半份注册信息，不创建 Flow、类型绑定或发送指标。
5. `removeEvent`、`removeSticky` 和 `clearSticky` 不解除已有配置或类型绑定，避免旧 Flow 与重新获取的同名 Flow 使用不同策略。
6. scope store 被完整清理后，通道覆盖配置随该 store 释放。同名 scope 重新建立时继承 bus 默认值，所有者需要重新配置；共享 `ScopedFlowBus` 不自动重放旧配置。
7. root store 的配置跟随 bus 实例存在；需要更换策略时创建新实例，由业务明确切换来源与订阅。
8. 关闭中的 `FlowBusScope` 拒绝新配置；与关闭并发、已经开始的配置操作遵循现有在途保护，不误配置同名新 store。
9. 对未知通道的只读 inspect、无缓存的清理/消费，不应为了新配置逻辑额外创建通道。旧 raw key 的兼容路径及类型保护保持现有规则。

首次配置与首次使用并发时，以原子绑定的先后确定结果：自定义配置先绑定则首次使用采用它；默认策略先绑定则不同的自定义配置失败。相同有效配置可幂等成功，不产生混合策略。

### 4.5 内部实现约束

- 在 `FlowBusStore` 集中解析和绑定类型/策略，使用不可变绑定记录，避免各公开入口分别维护配置。
- 延续 API 21 可用的并发手段；不引入 `ConcurrentHashMap.compute*` 等已有平台兼容问题。
- 注册阶段保证原子性，发送阶段不持有注册锁执行 `emit`、挂起回调、日志或错误处理器。
- 固定锁顺序：先完成并释放配置注册，再进入 sticky replay/commit 逻辑；不可从持有 sticky 锁的路径反向获取注册锁。清理路径同步核对这一约束。
- 普通容量、sticky replay 与额外容量均须非负；无缓冲时只能使用 SUSPEND；用安全算术校验 sticky replay 与额外容量之和，拒绝溢出组合。
- 创建 Flow、判断 sticky 是否有存储、分类发送结果时，全部使用该 key 的有效配置。
- `FlowBusPostResult.overflowPolicy` 返回本次普通或 sticky 流实际使用的策略，不能继续读取 bus 默认值；结果分类同步更新。
- 现有 `inspect().config` 仍是 bus 默认配置摘要，本版不改变 snapshot 结构。文档明确它不包含每通道覆盖；发送结果可核对实际溢出策略，但本版不提供完整通道缓冲快照。
- 不新增“实际丢失数”指标：底层 tryEmit 返回 true 不能证明没有丢弃，不能用 accepted/rejected 计数推算真实送达率。

## 5. P1：推荐入口与资源管理

| 场景 | 推荐路径 | 生命周期责任 |
| --- | --- | --- |
| 简单全局通知 | `postEvent` + `onEvent`，需要挂起回调则 `onEventSuspending` | 接收方使用合适的 LifecycleOwner；通道名固定且有限 |
| Activity/导航 owner 内通信 | 稳定 channel + 明确 owner | owner 决定来源；接收方决定何时活跃 |
| 需要不同通道策略 | 配置 channel 后沿用上述收发 API | 在来源首次使用前完成配置 |
| 构造注入与测试隔离 | 显式 `FlowBus` + `EventKey` + `collectEventFrom` | 来源实例由持有者管理，订阅结束不关闭它 |
| Session/Worker/任务 | `openScope(..., closeWhen = job)` | 绑定业务 Job；显式取消收集，按需要等待关闭完成 |
| 已组合的 Flow | `collectEvent` / `collectEventSuspending` | 调用方决定操作符、异常和取消语义 |
| 可恢复页面状态 | `StateFlow` | 使用业务状态和必要的持久化 |

资源管理文档必须说明：取消订阅不等于删除通道；移除通道不取消旧 Flow；按用户或任务动态生成大量全局通道名会持续保留注册信息。新增一个绑定 Job 的 scope 生命周期示例，通过现有 inspector 验证 scope 清理后的登记项消失，不以偶然 GC 成功作为回归断言。

不实施自动零订阅清理。它会影响暂时没有订阅者的 sticky、旧 Flow 引用和重新订阅行为，不能作为本版的透明优化。

## 6. P1：性能基线与优化规则

### 6.1 基准范围

建立独立的 `benchmarks/jvm` 工程，消费隔离 Maven 仓库中的候选 core 制品。优先使用 JMH；其版本、许可证和运行要求在落地时核对并固定。benchmark 依赖不进入发布产物或生产消费者依赖，不要求正常库构建先运行基准。

对照组为已发布 1.1.0、候选版和原生 SharedFlow。每组使用相同 Coroutines 版本、缓冲、溢出策略、生产者数量和回调工作量；配置注册开销与稳定发送开销分开测量。

| 必测场景 | 指标与解释 |
| --- | --- |
| 普通事件、0 个订阅者 | 发送开销和分配；明确事件被丢弃，不能称为投递吞吐量 |
| 普通事件、1/8 个活跃订阅者 | 发送/收集速率、延迟及分配；就绪同步后开始，核对实际收到的事件 ID |
| 单个慢订阅者，SUSPEND 与 DROP_OLDEST | 分别报告背压等待及实际收集量，不能只比较发送调用次数 |
| sticky replay=1、发送与后订阅 | 分别测发布和 replay 获取；只比较行为等价的配置 |
| 多生产者与重复创建/关闭 scope | 检查竞争开销及登记项清理；保持同名重建的正确性 |

至少记录 commit、制品版本、操作系统、CPU、JDK、Coroutines、参数、预热、测量时长、fork 数和 GC 分配指标。正式基线建议使用 3 个 fork，每 fork 5 次预热和 5 次测量，每次 1 秒；全部对照使用同一设置，报告误差范围。

### 6.2 优化准入

先建立 1.1.0 基线，再比较候选。若默认通道路径出现跨多次测量稳定复现且超过误差的回退，必须分析注册、配置查询、结果对象或指标计数的成本。约 10% 可作为人工调查提示，不作为共享 CI 的硬性阈值，也不是提速承诺。

只有 profile 确认且不改变契约时，才优化热路径。本版不预先关闭诊断、不移除类型保护，也不重写 sticky 算法。JVM 基准结果不外推为 Android ART 性能；如后续确需对外声称 Android 性能优势，另做设备基准。

基准 smoke 可进入 CI 检查可运行性，正式性能报告在稳定环境生成；同一阶段不重复启动多份全量基准。

## 7. 修改位置与职责

| 位置 | 计划改动 |
| --- | --- |
| `flowbus-core/.../FlowBusChannelConfig.kt`（新增） | 通道覆盖配置与公开参数说明 |
| `FlowBusStore.kt` | 原子绑定、有效策略解析、Flow 创建、发送结果及 store 清理 |
| `FlowBus.kt` / `ScopedFlowBus.kt` / `FlowBusScope.kt` | 三种来源的 configureChannel 转发及在途操作保护 |
| `FlowEventBus.kt` / `EventChannelExtension.kt` | Android 实例、全局和 owner 配置入口，复用 core 行为 |
| core 与 Android 现有 test 目录 | 行为矩阵、竞争和生命周期回归；新增独立 ChannelConfigTest 按现有命名习惯落位 |
| `app` 现有 channel/scope 示例 | 展示两条不同策略通道及清理责任，不新建演示架构 |
| `benchmarks/jvm`（新增） | 独立基准、参数、运行说明和结果模板 |
| `compatibility` / `gradle/release-verification.gradle.kts` | 1.1.0 二进制基线、新配置真实消费者和必要门禁 |
| README、模块 README、TROUBLESHOOTING、发布文档及对应英文文档 | 推荐路径、迁移差异、配置和兼容性说明 |

只提取配置解析、原子绑定等确有共用的逻辑；不借此合并全部扩展文件或调整无关命名。

## 8. 验证矩阵与通过标准

| 领域 | 必要验证 | 通过标准 |
| --- | --- | --- |
| 旧行为 | 现有默认配置、sticky、异常、取消及 scope close 测试 | 未配置新能力的消费者保持 1.1.0 行为 |
| 新配置 | 默认继承、部分覆盖、普通/sticky 不同策略、两条通道独立 | 实际缓冲行为和 PostResult 都使用正确策略 |
| 配置失败 | 负容量、无缓冲丢弃策略、整数溢出、类型/策略冲突 | 明确失败，无半注册和错误发送计数 |
| 配置竞争 | 自定义配置与首次 post/flow 并发；重复同值配置 | 只存在一个有效绑定，结果符合先绑定规则 |
| 来源隔离 | root、不同 scope、不同 bus、不同 Android owner | 同名通道不串配置或事件 |
| 清理与重建 | removeEvent/removeSticky、scope 清理与同名重开 | 单通道删除保留策略；新 store 不继承旧覆盖；旧 Flow 不被强制取消 |
| scope 关闭竞争 | configure 与 close/removeScope 并发 | 关闭拒绝新操作；已开始操作只作用于原 store，既有关闭结果不回退 |
| Android 生命周期 | STARTED/STOP、销毁、配置重建、挂起回调取消 | 活跃阶段正确收集，重建可幂等配置，不擅自关闭注入来源 |
| 二进制兼容 | 1.0.7 与 1.1.0 原版编译的 Kotlin/Java 消费者，不重新编译后替换候选运行 | 支持范围内旧描述符保留；1.1.0 挂起和实例入口实际执行 |
| 产物与平台 | API、元数据、Java 8、R8、API 21 和 API 35 | 最终候选制品通过既有门禁和新 API 消费者路径 |
| 性能 | 同环境的完整对照和 benchmark smoke | 报告可复现、语义可比，无未解释的稳定回退 |

并发测试使用可控屏障、CompletableDeferred、subscriptionCount 或调度器推进确认条件；超时只用于检测挂起失败，不用 Thread.sleep 或任意 delay 充当订阅就绪证明。

最低主线检查沿用现有任务：

```bash
./gradlew :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest apiCheck
./gradlew :library-android:lintRelease :app:lintRelease :app:assembleRelease
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalConsumer verifyMavenLocalBinaryCoreConsumer
```

以上为候选验证入口；实际执行结果按下方进度与发布说明记录。Maven 验证的隔离仓库、Java 8 路径参数、Compose/Navigation 消费者、设备任务和最终 CI 仍按现有[发布清单](release-checklist.md)执行；二进制 fixture 已支持固定 1.0.7 与 1.1.0 原版基线，两个版本分别验证。

Android/Gradle 验证复用 Android Studio 的项目 JDK、SDK 和缓存。只在输入变化或发现覆盖缺口时重跑对应验证，不为交付阶段重复已经覆盖最终输入的成功检查。

## 9. 分阶段实施与交付

| 阶段 | 依赖 | 工作与交付物 | 阶段出口 |
| --- | --- | --- | --- |
| A：契约和基线 | 无 | 旧行为缺口测试、迁移差异清单、最小性能对照 | 明确当前行为，得到优化前基线 |
| B：core 配置 | A | 配置类型、原子绑定、三种来源入口、有效策略结果 | core 配置/并发/关闭回归通过 |
| C：Android 接入 | B | owner/global 配置转发、实例用法、现有示例更新 | Android 生命周期、owner 隔离及重建验证通过 |
| D：性能与文档 | B、C | 完整基准报告、必要的小范围优化、推荐 API 与排障说明 | 最终逻辑验证通过，报告和文档对应实际实现 |
| E：候选验收 | A–D | 版本、发布说明、兼容消费者、API/R8/设备和最终 CI | 达到发布就绪；实际远程操作仍单独判断授权 |

A 阶段的文档整理与基准准备可独立进行；同一个 FlowBusStore 的绑定、清理和 sticky 相关修改由同一个实施主体负责，避免并发改动紧密耦合区域。完整构建与设备回归由一个负责人统一执行。

未明确实施人和设备排期前，不给出日历工期承诺。若只接受文档和验证工作、决定取消通道级配置，则重新按 1.1.x 维护版本评估范围，不能保留 1.2.0 的新增能力宣传。

## 10. 文档、兼容与发布安排

- 本文作为下一版方案入口；[1.1.0 范围与兼容性评估](next-version-plan.md)继续保存历史事实。
- 功能落地时同步中英文 Android/core README、根 README 与 TROUBLESHOOTING；发布阶段新增实际版本发布说明并更新 CHANGELOG、发布清单和消费者说明。
- 更新公开 API 快照前检查新增符号及旧描述符差异，不能以 apiDump 覆盖删除项来消除兼容失败。
- 1.1.0 基线从正式制品建立，固定来源和摘要；保留 1.0.7 已有回归。源码重编译成功不能代替未重编译消费者运行。
- 继续保持当前 minSdk 21、Java 8 字节码和基础依赖；本版不顺带升级 Kotlin、AGP、Coroutines 或 Lifecycle。
- 版本号、APP_VERSION_CODE、安装示例和发布门禁中的版本断言在候选准备阶段统一修改；当前方案不改变它们。
- 用户未使用新 API 时可直接回退到 1.1.0；使用新配置入口时需要同时回退调用并恢复 bus 级策略或拆分实例。已发布制品不得覆盖上传。
- 本方案只授权范围建议，不代表已授权创建提交、推送、PR、远程 CI 写入、上传或 Maven Central 发布。

## 11. 风险与暂缓项

| 风险/限制 | 处理原则 |
| --- | --- |
| 配置绑定增加热路径查询或锁竞争 | 一次解析、不可变绑定；基准定位后再优化 |
| 配置首次使用竞争导致启动期失败 | 文档要求初始化顺序；同有效配置幂等；冲突明确暴露 |
| shared scope 重建后忘记重新配置 | 明确覆盖配置属于 store；推荐业务持有者创建 owned scope 时集中配置 |
| 旧数据类或 inline/internal 依赖的 ABI 变化 | 保留已有描述符；固定原版消费者、符号与设备验证分别取证 |
| 误把 SUSPEND、sticky 或 accepted 当可靠投递 | 使用说明、示例和断言都区分发送、接收与业务成功 |
| API 继续扩张 | 只新增配置类型与必要成员/Android 入口；不复制整套语法糖 |
| Inspector 无每通道完整缓冲摘要 | 明确 bus 默认摘要边界；本版仅通过发送结果展示实际溢出策略 |
| JVM 基准不代表 Android 实际运行 | 报告限定平台；Android 性能结论需要独立设备数据 |

后续若有明确需求，再分别立项：完整通道配置快照、可安全迁移的动态策略、自动资源回收、Compose 发布模块、跨进程、KMP 或可靠消息协议。当前版本不预留这些需求的公共接口。

## 12. 实施完成判定

只有同时满足以下条件，才将 1.2.0 标为实现完成：

1. 所有保留的 P0/P1 项均有实现、准确文档和实际验证证据；未实施项已从版本承诺中移除。
2. 原有消费者不使用新配置时行为和支持范围保持；新配置的来源隔离、失败与并发路径通过验证。
3. 1.0.7/1.1.0 兼容证据、API 21 与 API 35、R8、元数据等门禁覆盖最终候选，不能引用旧提交成功代替。
4. 性能报告来自真实运行，保留环境和参数；不把“发送未拒绝”描述为“全部送达”。
5. 工作区差异聚焦，没有敏感信息、临时产物或无关依赖/格式改动；发布说明与制品一致。

满足上述条件代表本地实现及候选验收完成；正式发布状态以获授权后实际上传、发布和消费验证结果为准。

## 13. 实施进度（2026-10-14）

- B/C 核心能力：已实现 nullable 覆盖配置、原子类型/策略绑定、来源隔离、生命周期及在途关闭保护、有效策略诊断；新增 core 19 项和 Android 6 项回归初次通过。
- A/D 契约和用法：已更新中英文模块/root README、TROUBLESHOOTING、owner channel 示例、Job-bound scope 清理示例，正式安装仍指向已发布 1.1.0。
- 二进制/产物：Java 8 新配置 Maven core 消费者、候选 Android R8、元数据通过；1.0.7/1.1.0 原版 core Kotlin/Java 未重编译运行通过，旧 Android 挂起/显式实例 class 在候选 JVM runtime 运行通过，两个旧版 AAR 静态公开符号通过。Kotlin 编译器矩阵、compileSdk 33、可选 Compose/Navigation/NavHost R8、发布任务图 dry-run 通过；主线 231 项 JVM 测试、API、release lint、debug/release 构建通过。
- 性能：benchmarks/jvm 为独立工程；固定正式 1.1.0、候选和 SharedFlow 三组完整 JMH 共 58 组测量通过，旧配置不一致数据排除。普通无人订阅/ sticky 发布吞吐与基线接近，未确认默认稳定发送回退；首次注册约慢 14.5%、多 112 B/op，GC/JFR 与源码分析已披露，见[实测分析](../benchmarks/jvm/results/current/analysis.md)。
- E 候选验收：版本设为 1.2.0/code 9，新增中英文发布说明。API 21/API 35 与最终远端 CI 尚缺运行证据，本机没有可用于本项验收的 API 21/API 35 设备，不标记完成。用户已授权创建候选分支、提交、推送与草稿 PR，正在准备；合并、上传与正式发布未授权、未执行。

设备门禁的覆盖复核补充了 channel 页 Activity 重建后的配置幂等、普通事件不重放及新事件收集用例；API 35 脚本在任何测试前保存并断言实际设备版本与启动状态。驱动三项 mocked-ADB 回归与 Shell 语法检查通过；app release instrumentation R8 编译、库 debug instrumentation APK、相关 release lint、231 项 JVM 测试及 API 检查通过。最终设备执行仍缺证据，编译不能替代运行。

详细状态与最终证据入口见 [1.2.0 发布说明](release-notes-1.2.0.md)及[发布清单](release-checklist.md)。本节是实施状态，前文保留完整验收范围；不能因当前环境缺少门禁而缩减完成条件。
