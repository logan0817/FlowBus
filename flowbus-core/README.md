[English](README_EN.md) · [文档索引](../docs/README.md)

# FlowBus Core

`flowbus-core` 是 FlowBus 不依赖 Android 的 Kotlin/JVM 核心模块，目前不包含 Kotlin Multiplatform 产物。

它基于 Kotlin Coroutines 与 Flow，提供：

1. 类型安全事件分发
2. sticky event（后订阅者也能读到最近一次值）
3. root bus / scoped bus 隔离
4. 显式生命周期控制的 `FlowBusScope`
5. 命名事件句柄 `EventChannel<T>`
6. 更简洁的 Kotlin 风格语法糖，如 `event.send()`

如果你不是 Android 项目，或者你需要自己管理 bus 实例、scope 生命周期、多实例隔离，这个模块就是主入口。

## 阅读导航

- [安装](#安装) · [快速开始](#快速开始)
- [scope](#scope-与所有权) · [API 选择](#api-选择矩阵)
- [通道配置](#channel-configuration) · [挂起订阅](#suspend-collection)
- [诊断](#诊断) · [行为边界](#行为边界)
- [兼容性](../docs/compatibility.md) · [排障](../TROUBLESHOOTING.md)

## 适合场景

如果你符合下面任意一种情况，就可以直接从 `flowbus-core` 开始：

1. 你不是 Android 项目
2. 你想自己创建 `FlowBus()`，而不是依赖默认单例
3. 你需要多实例隔离，或者要把 bus 生命周期交给依赖注入
4. 你需要 Session / Repository / Worker / Task 级别的 scope

如果你是 Android 项目，通常先看 Android 适配模块会更快：

1. Android 模块文档：[library-android README](../library-android/README.md)

<a id="先看这-9-句"></a>

## 使用约定

1. 想开箱即用，就先用 `DefaultFlowBus`。
2. 需要依赖注入、多实例隔离或明确生命周期，就自己创建 `FlowBus()`。
3. 默认事件名不是类短名，而是事件类型完整类名。
4. 同一类型要拆多个通道时，用 `eventChannel<T>("name")` 或显式 `eventName`。
5. `EventChannel` 适合长期复用和公开语义，对象式发送扩展适合把发送调用写短，直接写 `eventName` 适合局部少量使用。
6. `scoped(...)` 是共享命名视图，`openScope(...)` 是带显式生命周期的 scope 句柄。
7. `post*` / `send()` 立即尝试写入并返回 `Boolean`；`emit*` / `awaitSend*` 是挂起入口，仍遵循既定溢出策略，不提供可靠投递保证。
8. `tryPost*Result` 返回总线层诊断结果；`consumeStickyLatest(...)` 只读取并清理当前 sticky replay。
9. `DefaultFlowBus.configure(...)` / `install(...)` 必须在第一次真正使用前完成。

清理 API 单独记住这 2 点：

1. `removeScope()` 会关闭同名打开中的 `FlowBusScope` 句柄，并清理当前 store。
2. `removeEvent()`、`removeSticky()` 不会主动 cancel 你已经拿到手的旧 `Flow` 引用。

## 安装
[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.1.0")
```

<a id="先看最短路径"></a>

## 快速开始

第一次使用时，只记 4 件事就够了：

1. 默认单例发送：`DefaultFlowBus.post(MyEvent(...))`
2. 默认单例订阅：`DefaultFlowBus.flow<MyEvent>()`
3. 偏好“事件自己发送自己”的写法：`MyEvent(...).send()`
4. 需要在 `SUSPEND` 策略下等待活跃订阅者腾出缓冲时，使用 `emit(...)` / `awaitSend()`

最短可用示例：

```kotlin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class SyncFinishedEvent(val taskId: String)

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}

DefaultFlowBus.post(SyncFinishedEvent(taskId = "task-1"))
```

对象式发送就用 `send()`；需要遵循配置进行挂起发送时用 `awaitSend()`。

示例中的 `scope` 是调用方管理的活跃 `CoroutineScope`。直接收集本库普通 Flow 时，`CoroutineStart.UNDISPATCHED` 会先执行到收集的首次挂起点，再发送普通事件。这不是对任意上游 Flow 的订阅就绪保证：上游初始化挂起、`flowOn`、缓冲操作符或生命周期门控都可能推迟实际订阅，应按具体链路确认就绪。

如果你准备替换默认单例配置，要先调用 `DefaultFlowBus.configure(...)` 或 `install(...)`，再第一次真正使用它。

术语速查：

| 术语 | 含义 | 为什么会影响你 |
| --- | --- | --- |
| `tryEmit` | Kotlin `SharedFlow` 的非挂起写入尝试 | `post(...)` / `send()` 依赖它，所以失败只说明总线没立刻接收，不代表业务失败 |
| replay | sticky 通道保存的最近值缓存 | 后订阅者能先读到最近值，但它不是长期状态容器 |
| scope store | 某个 scopeName 下当前保存事件流和 sticky 缓存的内部容器 | `close()` / `removeScope()` 清理的是当前 store，不会主动 cancel 旧 `Flow` 引用 |
| `DROP_OLDEST` / `DROP_LATEST` | 缓冲满时丢旧值或丢新值的策略 | 适合轻量通知，不适合关键链路可靠投递 |
| `DefaultFlowBus` | 默认单例入口 | 适合开箱即用；需要隔离或依赖注入时改用 `FlowBus()` |

<a id="什么时候该从-defaultflowbus-切到-flowbus"></a>

## 实例与依赖注入

以下情况建议自己创建实例：

1. 你希望测试、功能模块、租户、会话之间完全隔离
2. 你想通过依赖注入控制 bus 生命周期
3. 你需要不同配置的 bus
4. 你不想使用默认全局单例

示例：

```kotlin
val bus = FlowBus()

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    bus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}

bus.post(SyncFinishedEvent(taskId = "task-1"))
```

这时所有事件都只在这个 `bus` 实例里流动，不会和 `DefaultFlowBus` 混在一起。

## 命名事件句柄：`eventChannel(...)`

当你不想把 `eventName` 字符串散落在业务代码里，或者同一个类型需要多个语义不同的通道时，推荐把通道本身抽成一个对象。

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.flow().collect { message ->
        showToast(message)
    }
}

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.collect { message ->
        showToast(message)
    }
}

toastChannel.post("Saved")
```

如果你自己创建了 bus，也可以显式指定目标：

```kotlin
val bus = FlowBus()
val toastChannel = eventChannel<String>("ui.toast")

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.flowOn(bus).collect { message ->
        showToast(message)
    }
}

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.collectOn(bus) { message ->
        showToast(message)
    }
}

toastChannel.postOn(bus, "Saved")
```

### 什么时候用 `eventChannel`，什么时候直接写 `eventName`

优先推荐 `eventChannel(...)` 的情况：

1. 这个通道会被复用很多次
2. 你想把业务语义集中定义
3. 你不想在多个文件里复制 `"ui.toast"` 这样的字符串

直接传 `eventName` 也没问题：

```kotlin
bus.post(value = "Saved", eventName = "ui.toast")
bus.flow<String>(eventName = "ui.toast")
```

但如果这是公开 API 或长期维护代码，`eventChannel` 的可读性通常更好。

<a id="作用域怎么理解rootscopedopenscope"></a>

## scope 与所有权

### root bus

`FlowBus()` 默认就有一个根总线，所有 `bus.post(...)` / `bus.flow<T>()` 都是在 root bus 上工作。

适合：

1. 全局广播
2. 这个 bus 实例内部的公共事件

### `scoped(...)`

`scoped("feature-a")` 返回的是一个共享作用域视图。

适合：

1. 你只想按名字把事件隔离到某个 scope
2. 生命周期由外部统一管理
3. 多处代码只需要拿同一个命名 scope 来收发

```kotlin
val featureBus = DefaultFlowBus.scoped("feature-a")

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    featureBus.flow<FeatureRefreshEvent>().collect { event ->
        refresh(event.reason)
    }
}

featureBus.post(FeatureRefreshEvent(reason = "manual"))
```

### `openScope(...)`

`openScope(...)` 返回的是 `FlowBusScope`，它有明确的“打开 / 关闭”生命周期。

适合：

1. Session
2. Repository 生命周期
3. Worker / Task 链路
4. 一段业务流程结束后要移除当前 scope 对应的内部 store，并清掉其中缓存

```kotlin
val syncScope = DefaultFlowBus.openScope("sync-task", closeWhen = scope)

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}

syncScope.post(SyncProgress(percent = 10))
```

如果你想手动结束：

```kotlin
val sessionScope = DefaultFlowBus.openScope("session-42")

sessionScope.post(SessionEvent.Started)
sessionScope.close()
```

简单区分：

1. `scoped(...)`：共享命名 bus 视图，不负责 close
2. `openScope(...)`：带显式生命周期的 scope 句柄，可以 `close()`

补一句：

1. `close()` 会立即让当前 `FlowBusScope` 句柄失效，后续不能再用这个句柄发送或取流
2. 已经开始的发送 / 取流操作会继续使用原 store，等这些操作结束后再清理 store
3. 已经拿到手的旧 `Flow` 引用不会被主动 cancel；之后再通过同名 scope 访问时，会按需创建新的通道

<a id="如果你还想使用更底层的-typed-key"></a>

## EventKey 与类型约束

大多数时候你可以直接用 `bus.post(value)` / `bus.flow<T>()`。

但如果你想显式持有一个稳定 key，或者要和 Java / 底层 API 互操作，可以用 `EventKey<T>`：

```kotlin
val refreshKey = eventKey<FeatureRefreshEvent>("feature.refresh")
val bus = FlowBus()

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    bus.flow(refreshKey).collect { event ->
        refresh(event.reason)
    }
}

bus.post(refreshKey, FeatureRefreshEvent(reason = "manual"))
```

`EventChannel<T>` 可以随时转回 `EventKey<T>`：

```kotlin
val channel = eventChannel<String>("ui.toast")
val key = channel.asEventKey()
```

## API 选择矩阵

| 需求 | 推荐 API | 功能解释 | 什么时候选 |
| --- | --- | --- | --- |
| 默认单例 | `DefaultFlowBus` | 提供一个进程内默认 bus | 快速接入、Demo、没有 DI 要求 |
| 多实例隔离 | `FlowBus()` | 每个实例有独立 root bus 和 scope 容器 | 测试隔离、租户隔离、Repository 自持生命周期 |
| 最短非挂起发送 | `post(...)` / `send()` | 立即尝试写入，返回 `Boolean` | 轻量广播、偶发丢失可接受 |
| 发送诊断结果 | `tryPostResult(...)` / `tryPostStickyResult(...)` | 返回订阅数、溢出策略、sticky replay 数和结果分类 | 日志排查、单测断言、分析发送失败 |
| 按配置进行挂起发送 | `emit(...)` / `awaitSend()` | `SUSPEND` 且存在活跃订阅者时可背压；仍遵循溢出策略 | 需要等待缓冲可用的活跃广播 |
| 直接订阅并复用错误处理 | `collect(...)` / `collectSticky(...)` | 内部套用 `FlowBusConfig` 的 logger 和 errorHandler | 不想每处手写 try/catch 和日志策略 |
| 自己组合 Flow 操作符 | `flow(...)` / `stickyFlow(...)` | 返回原始 `Flow<T>` | 要做 `map`、`filter`、`debounce`、`combine` |
| 命名通道 | `eventChannel<T>("name")` | 把事件名和类型绑定成稳定对象 | 同类型多语义、跨文件复用、公开业务通道 |
| 命名 scope 视图 | `scoped(...)` | 共享一个命名局部 bus，不拥有关闭权 | 多处代码复用同一 scope，但生命周期由外部管理 |
| 显式生命周期 scope | `openScope(...)` | 返回可关闭的 `FlowBusScope` 句柄 | Session、Worker、Task、Repository 结束后要清缓存 |
| 查看诊断元数据 | `inspect()` / `inspector().snapshot()` | 返回只读快照，不暴露 sticky payload | 调试、日志、测试断言 |
| 移除普通事件当前通道 | `removeEvent(...)` | 移除当前 store 里的普通事件流 | 让后续访问重新建普通通道 |
| 清理 sticky 缓存或通道 | `clearSticky(...)` / `removeSticky(...)` | 清 replay，或连当前 sticky 条目一起移除 | 页面退出、会话切换、最近值过期 |
| 读取并清空 sticky 最新值 | `consumeStickyLatest(...)` | 读取当前最新 replay 后清空 | 一次性消费最近结果 |

<a id="发送-api-怎么选"></a>

## 发送 API

### 想最短、最顺手

```kotlin
DefaultFlowBus.post(MyEvent(...))
MyEvent(...).send()
```

### 想发到特定 bus / scope

```kotlin
val bus = FlowBus()
MyEvent(...).sendOn(bus)

val sessionScope = DefaultFlowBus.openScope("session")
MyEvent(...).sendOn(sessionScope)
```

### 需要按配置进行挂起发送

```kotlin
scope.launch {
    DefaultFlowBus.emit(MyEvent(...))
}
```

或者：

```kotlin
scope.launch {
    MyEvent(...).awaitSend()
}
```

### 想知道发送诊断结果

```kotlin
val result = DefaultFlowBus.tryPostResult(MyEvent(...))

if (!result.accepted) {
    logger.warn("FlowBus dropped ${result.eventName}: ${result.outcome}")
}
```

`FlowBusPostResult` 只说明总线这一层的接收结果。

| 结果 | 含义 | 你该怎么处理 |
| --- | --- | --- |
| `accepted = true` | `tryEmit` 没有立刻拒绝这次调用 | 只能说明总线层接收尝试通过，业务完成要另做 ACK 或状态更新 |
| `accepted = false` | 底层流拒绝了这次非挂起写入 | 检查缓冲、订阅者和溢出策略；`SUSPEND` 下需要等待时用 `emit(...)` |
| `AcceptedWithDropOldestPolicy` | 新值可能挤掉旧值 | 只用于可丢弃的刷新类通知，不要承载关键链路 |
| `AcceptedWithDropLatestPolicy` | 调用可能被接受，但当前值仍可能不会进入实时缓冲 | `emit(...)` 不改变该策略；可靠业务使用状态、数据库或持久化任务 |

它不表示订阅者已经处理事件。`DROP_OLDEST` / `DROP_LATEST` 也不是可靠队列策略。


<a id="120-候选按通道配置"></a>
<a id="channel-configuration"></a>

## 按通道配置

从 1.2.0 起提供。当前分支为未发布候选，正式 1.1.0 不包含这些 API。[本地体验候选版本](../CONTRIBUTING.md#candidate-builds)可使用 `io.github.logan0817:flowbus-core:1.2.0` 或 `io.github.logan0817:flowbus:1.2.0`；变化与验收记录见[发布说明](../docs/release-notes-1.2.0.md)。

### core、scope 与实例用法

```kotlin
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusChannelConfig
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.channels.BufferOverflow

val bus = FlowBus()
val refresh = eventKey<String>("home.refresh")
val progress = eventKey<Int>("work.progress")
bus.configureChannel(refresh, FlowBusChannelConfig(normalBufferCapacity = 1))
bus.configureChannel(progress, FlowBusChannelConfig(
    normalBufferCapacity = 16,
    normalOverflowPolicy = BufferOverflow.SUSPEND
))
// 在订阅真正活跃后，由业务流程发送。
bus.post(refresh, "refresh")
bus.emit(progress, 10) // 位于挂起函数；有慢订阅者时可能背压，不代表处理成功。
```

`FlowBus`、共享 `ScopedFlowBus`、owned `FlowBusScope` 均提供 `configureChannel(key, config)`。core `EventChannel` 通过 `channel.asEventKey()` 配置；默认单例通过 `DefaultFlowBus.raw().configureChannel(...)` 配置。

五个可空字段 `normalBufferCapacity`、`normalOverflowPolicy`、`stickyReplay`、`stickyExtraBufferCapacity`、`stickyOverflowPolicy` 的 `null` 值继承 bus 默认值。两种 overflow 分别继承 `FlowBusConfig.overflowPolicy`。默认普通缓冲 64、DROP_OLDEST、sticky replay 1 保持。容量不得为负；普通零容量或 sticky 总容量为零时只能用 SUSPEND；sticky replay 与额外容量之和不能超过 Int.MAX_VALUE。

配置身份是当前 store 内的 eventName，普通与 sticky 策略一次绑定。在首次 post/emit/flow（含 sticky）前配置；未配置的首次使用绑定默认值。相同类型和相同**有效配置**重复调用是幂等，不同类型抛 IllegalArgumentException，不同策略抛 IllegalStateException。参数或冲突失败不会创建半份注册、Flow 或发送计数。

removeEvent/removeSticky/clearSticky 不解除配置和类型绑定。完整 scope 清理会释放覆盖，同名 scope 重开时需重新配置；root 配置跟随 bus 实例。更换已绑定策略需要新实例及明确切换订阅。配置与首次使用并发时以先绑定者为准；应由来源持有者集中初始化。`inspect().config` 仍是 bus 默认摘要，不包含通道覆盖；`tryPostResult` / `tryPostStickyResult` 的 overflowPolicy 才是对应流实际策略。accepted/rejected 计数不能推算送达率。

### 迁移与资源管理

从 EventBus / LiveEventBus 迁移时，首先确定来源 bus/owner 和稳定 key，再确定普通或 sticky。post 只进入普通流，postSticky 只进入 sticky 流，同名不合并。普通无人订阅不会留存；注册返回 Job 不等于订阅就绪；sticky 的生命周期重启可再次重放，consume 只清除 replay，不撤回实时在途数据。慢订阅者使用 SUSPEND + emit 等待缓冲，或 DROP_OLDEST 丢弃旧通知；两者都不提供 ACK 或业务完成保证。

同一 key 的普通/sticky 对照：两个收集器均已活跃，依次执行 `bus.post(key, 1)` 与 `bus.postSticky(key, 2)`。普通收集器得到 `[1]`，sticky 收集器得到 `[2]`；之后新建的普通订阅没有历史值，新建的 sticky 订阅先得到 `2`。`consumeStickyLatest(key)` 返回 `2` 并清空 replay，但两个原收集器仍保持活跃。可运行验证见 [FlowBusDeliveryContractTest](src/test/java/com/logan/flowbus/core/FlowBusDeliveryContractTest.kt)。

慢订阅者对照：将普通通道容量设为 1、策略设为 DROP_OLDEST，收集器收到 `1` 后暂停回调，再发送 `2`、`3`。两次发送都可能返回 accepted；恢复回调后实际得到 `[1, 3]`，`2` 已被挤掉。若另一通道配置为零容量 SUSPEND，活跃慢收集器暂停回调时 `post` 返回 false，`emit` 等待其继续收集；这仍不表示业务处理完成。可运行验证见 [FlowBusChannelConfigTest](src/test/java/com/logan/flowbus/core/FlowBusChannelConfigTest.kt)。

没有自动继承分发。需要 sealed 父类型时，两端使用相同 `EventKey<Parent>`，例如 `val key = eventKey<UiEvent>("ui.event")`，再用 `bus.post(key, ChildEvent(...))` 与 `bus.flow(key)`；不要按子类默认 key 发送、按父类默认 key 订阅。完整泛型类型不作运行时验证。

总线收集入口遵循来源的 errorHandler（默认 Rethrow）；通用 Flow 收集直接传播错误。协作取消不会作为普通回调错误处理，也不会撤销已产生的副作用。

取消订阅不删除通道，移除通道也不取消旧 Flow。避免为每个用户或任务创建永久全局 key。任务使用 Job 绑定的 scope，清理以 inspector 的登记项为依据：

```kotlin
import kotlinx.coroutines.Job

val taskJob = Job()
val source = bus.openScope("sync-task", closeWhen = taskJob)
source.configureChannel(progress, FlowBusChannelConfig(normalBufferCapacity = 1))
source.postSticky(progress, 10)
check(bus.inspect().scopes.any { it.scopeName == "sync-task" })
taskJob.cancel()
source.closeSuspending() // 挂起函数中等待清理；收集 Job 仍由调用方取消。
check(bus.inspect().scopes.none { it.scopeName == "sync-task" })
```

首选入口：简单全局通知使用默认 bus；owner 内通知使用稳定 channel + 明确 owner；注入与测试使用显式 FlowBus + EventKey；任务使用 openScope(closeWhen = job)；已有 Flow 组合使用通用 Flow 收集；可恢复状态使用 StateFlow。没有零订阅自动清理。

<a id="110-挂起订阅"></a>
<a id="suspend-collection"></a>

## 挂起订阅

1.1.0 增加独立命名的挂起回调入口，旧 `collect` / `collectSticky` 与公开入口保留。下面 API 从 1.1.0 开始提供，不要求新增依赖或升级现有基础工具链。

`FlowBus`、`DefaultFlowBus`、`ScopedFlowBus`、`FlowBusScope` 提供 `collectSuspending` / `collectStickySuspending` 的 `EventKey<T>` 与 reified 类型入口。core `EventChannel<T>` 提供默认实例的 `collectSuspending` / `collectStickySuspending`，以及显式来源的 `collectSuspendingOn` / `collectStickySuspendingOn`；显式来源可以是 `FlowBus`、`ScopedFlowBus` 或 `FlowBusScope`。

```kotlin
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

val bus = FlowBus()
val refreshKey = eventKey<SyncFinishedEvent>("sync.finished")

val subscription = scope.launch(start = CoroutineStart.UNDISPATCHED) {
    bus.collectSuspending(refreshKey) { event ->
        refreshSuspending(event.taskId)
    }
}
bus.post(refreshKey, SyncFinishedEvent(taskId = "task-1"))

// 在业务生命周期结束时调用 subscription.cancel()；不会因此关闭 bus。
```

`scope` 是调用方管理的活跃协程作用域，`refreshSuspending` 是业务挂起函数。无 dispatcher 且直接收集时，上面的 UNDISPATCHED 用法先执行到收集挂起点；其他上游组合仍需确认真实订阅就绪。若使用 reified 入口，需要导入 `com.logan.flowbus.core.collectSuspending`；若使用 core channel 扩展，需要导入对应扩展，例如 `collectSuspendingOn`。

单条订阅顺序执行回调，不并发启动每条事件的处理，也不会自动采用 `collectLatest`。提供 `dispatcher` 时切换回调调度器，串行语义保持；省略时使用当前收集协程上下文。不同订阅之间没有全局串行承诺。挂起回调不会改变广播、缓冲、丢弃、sticky replay 或业务确认语义。

类型校验、logger 和 errorHandler 与旧入口共用。回调普通异常先记日志，再交给实例 errorHandler；默认 `Rethrow` 终止该收集，处理器正常返回才继续下一条。处理器自身抛出的异常继续传播。`CancellationException` 直接传播，不进入总线错误策略；取消当前订阅会请求取消回调，业务中的阻塞或 NonCancellable 操作不能被强制停止，也不会回滚已发生的副作用。

`FlowBusScope` 关闭后不会主动取消已持有 Flow 的订阅；但重新用已关闭句柄建立订阅会失败。总线或 scope 的生命周期由持有者管理，订阅不会取得关闭权。

<a id="订阅-api-怎么选"></a>

## 订阅 API

如果你只想收事件并沿用 `FlowBusConfig` 里的日志和错误处理策略，优先用 `collect(...)` / `collectSticky(...)`：

```kotlin
scope.launch {
    DefaultFlowBus.collect<SyncFinishedEvent> { event ->
        handle(event.taskId)
    }
}

scope.launch {
    toastChannel.collect { message ->
        showToast(message)
    }
}
```

如果你需要 `map`、`filter`、`debounce` 这类 Flow 操作符，就先拿 `Flow` 再自己 `collect`：

```kotlin
scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>()
        .collect { event ->
            handle(event.taskId)
        }
}
```

直接订阅入口覆盖 `DefaultFlowBus`、`FlowBus`、`ScopedFlowBus`、`FlowBusScope` 和 `EventChannel`。

如果 `EventChannel` 要显式指定目标，使用 `collectOn(target) { ... }` / `collectStickyOn(target) { ... }`。

<a id="诊断-api-怎么用"></a>

## 诊断

`inspect()` 适合调试、单测断言和日志排查。不要把它放进正式业务分支控制。

它返回只读快照，包含这些元数据：

1. 配置摘要。
2. root 事件和 scope 事件。
3. 事件名和事件类型名。
4. 普通流、sticky 流是否存在。
5. sticky replay 数量。
6. 订阅数和非挂起发送计数。

它不会暴露 sticky replay 中的业务 payload。

如果你会把诊断快照写入日志，不要把敏感值放进 `eventName` 或 `scopeName`。

敏感值包括手机号、订单号、token、用户 ID 等。确实需要关联业务对象时，先做脱敏或使用内部追踪 ID。

对于 `DefaultFlowBus`，`inspect()` / `inspector()` 不会触发默认总线初始化。

默认总线尚未初始化时，快照只会返回默认配置摘要和空事件列表。后续仍然可以调用 `configure(...)` / `install(...)`。

```kotlin
val snapshot = DefaultFlowBus.inspect()

snapshot.root.events.forEach { event ->
    println("${event.eventName}: subscribers=${event.subscriptionCount}, accepted=${event.metrics.acceptedPostCount}")
}
```

如果你需要多次读取同一个 bus 的当前状态，可以保留诊断入口：

```kotlin
val inspector = DefaultFlowBus.inspector()
val firstSnapshot = inspector.snapshot()
val secondSnapshot = inspector.snapshot()
```

如果只关心某个 scope，可以直接查询：

```kotlin
val bus = FlowBus()
val session = bus.inspectScope("session")
```

诊断快照只看元数据。边界记住这 6 点：

1. 读取快照不会创建新的事件流或 scope。
2. `scopeName = null` 表示 root bus。
3. `stickyReplayCount` 只给数量，不给业务值。
4. `inspectScope(...)` 在 scope 尚未创建时返回 `null`。
5. `events` 表示当前已登记的事件元数据，不等于一定有活跃订阅者。
6. `metrics` 只统计 `tryEmit` 层面的接受或拒绝，不代表业务处理成功。

<a id="eventchannel对象式发送扩展eventname-怎么选"></a>

## 事件标识与发送扩展

如果你在这 3 种写法里犹豫，可以直接按下面的规则选：

| 场景 | 更推荐的写法 |
| --- | --- |
| 这个通道会被很多地方复用，想把名字集中定义 | `eventChannel<T>("name")` |
| 只是临时发一次，想写得最短 | `event.send()` / `event.sendOn(target)` |
| 已经有稳定名字，但不想单独建 channel 对象 | 直接传 `eventName` |
| 面向公开 API 或长期维护代码 | 优先 `eventChannel<T>("name")` |
| 只是内部简单广播 | 对象式发送扩展或直接 `post(value)` 都可以 |

可以把它们理解成 3 个层级：

1. `eventChannel<T>("name")`：把“通道”本身抽成一个稳定对象，最适合长期复用。
2. `event.send()` / `event.sendOn(target)`：对象式发送扩展，把“发送动作”写短，最适合一次性调用点。
3. `eventName = "..."`：最直接，但也最容易把字符串散落在代码里。

对象式发送扩展的直接对应关系：

| 写法 | 实际语义 |
| --- | --- |
| `event.send()` | 等价于 `DefaultFlowBus.post(event)` |
| `event.awaitSend()` | 等价于 `DefaultFlowBus.emit(event)` |
| `event.sendSticky()` | 等价于 `DefaultFlowBus.postSticky(event)` |
| `event.awaitSendSticky()` | 等价于 `DefaultFlowBus.emitSticky(event)` |
| `event.sendOn(target)` | 等价于对这个目标调用对应的 `post(...)` |
| `event.awaitSendOn(target)` | 等价于对这个目标调用对应的 `emit(...)` |

补一句：

1. 这些对象式发送扩展只是更短的写法，不是另一套分发规则。
2. 默认 `eventName` 仍然是事件类型完整类名。
3. 自定义 `eventName` 仍然不能为空白字符串。
4. 发到 `FlowBus`、`ScopedFlowBus`、`FlowBusScope` 时，行为边界和对应的 `post*` / `emit*` 完全一致。

<a id="postemitsendawaitsend-怎么理解"></a>

## 发送语义

### `post(...)` / `send()`

特点：

1. 非挂起
2. 手感最轻
3. best-effort
4. 缓冲无法立刻接收时会返回 `false`

适合：

1. 普通广播
2. 可以接受极端情况下发送失败的事件

### `emit(...)` / `awaitSend()`

特点：

1. 挂起函数，遵循当前 `overflowPolicy`，不会自动把丢弃策略变成 `SUSPEND`。
2. `SUSPEND` 且存在活跃订阅者时，可以等待缓冲可用并施加背压。
3. `DROP_OLDEST` 仍可能丢旧值，`DROP_LATEST` 仍可能丢本次实时值。
4. 普通事件没有订阅者时立即完成，不为未来订阅者保存；发送完成也不表示回调或业务处理完成。

适合需要按既定策略等待缓冲可用的活跃广播。必须在进程死亡后恢复或确认业务成功的操作，使用业务状态、数据库或持久化任务机制。

<a id="sticky-event-什么时候该用"></a>

## sticky 与 replay

sticky event 会保存最近一次值，后来订阅的人也能立刻读到。

这里的 replay 指 sticky 通道内部保存的最近值缓存。它能解决“订阅晚了也想先拿最近结果”的问题，但不适合承载长期状态；页面状态、表单状态和复杂业务状态仍应交给 `StateFlow`、数据库或业务状态机。

sticky replay 与实时广播独立管理。发送被底层接受后会提交独立 replay；`DROP_LATEST` 下实时缓冲可能丢掉本次值，但 `tryEmit` 仍返回 `true`，因此该值仍可能进入 replay。例如慢订阅者实时收到 `[1, 2]`，新订阅者却先读到 `3`。这是现有契约，不能用实时接收结果推断 replay，也不能把 replay 当作所有订阅者已接收或处理的记录。`emitSticky(...)` 同样遵循配置的溢出策略。

| 状态 | 推荐 API | 后订阅者能否读到最近值 | 是否清 replay | 适合场景 | 边界 |
| --- | --- | --- | --- | --- | --- |
| 普通事件 | `post(...)` / `emit(...)` / `flow(...)` | 否 | 否 | 一次性通知、命令、点击动作 | 没有 replay 缓存 |
| sticky 最近值 | `postSticky(...)` / `emitSticky(...)` / `stickyFlow(...)` | 是 | 否 | 初始化结果、会话信息、页面恢复配置 | 不适合替代长期状态管理 |
| 清空 sticky replay | `clearSticky(...)` | 否 | 是 | 通道继续复用，只清掉最近值 | 不会主动 cancel 旧 `Flow` 引用 |
| 移除 sticky 通道 | `removeSticky(...)` | 否 | 是 | 当前 sticky 条目不再需要，后续访问按需新建 | 旧 `Flow` 引用不会被主动 cancel |
| 一次性读取 sticky 最新值 | `consumeStickyLatest(...)` | 读取后不再保留 | 是 | 最近结果只应该被读取一次 | 不会阻止其他线程后续写入新的 sticky 值 |
| 移除普通事件通道 | `removeEvent(...)` | 不适用 | 不适用 | 让后续普通事件访问重新建通道 | 不影响同名 sticky 通道 |

示例：

```kotlin
data class SessionReadyEvent(val userId: Long)

DefaultFlowBus.postSticky(SessionReadyEvent(userId = 42L))

scope.launch {
    DefaultFlowBus.stickyFlow<SessionReadyEvent>().collect { event ->
        bindSession(event.userId)
    }
}
```

清理方式：

```kotlin
DefaultFlowBus.clearSticky<SessionReadyEvent>()
DefaultFlowBus.removeSticky<SessionReadyEvent>()
```

`consumeStickyLatest(...)` 会读取当前 sticky replay 的最新值，并立刻清空 replay 缓存。

它适合“只消费一次最近结果”的窄场景。这个读和清只保证在当前 store 内连续完成，不会阻止其他线程后续写入新的 sticky 值，也不能替代状态管理。

普通事件没有 replay 缓存。如果你只想移除当前普通事件通道，让后续访问重新创建，可以用：

```kotlin
DefaultFlowBus.removeEvent<SessionReadyEvent>()
toastChannel.removeEvent()
toastChannel.removeEventOn(bus)
```

`removeEvent(...)` 不会影响同名 sticky 通道，也不会清掉同名 `EventKey` 的类型保护。

<a id="订阅异常怎么处理"></a>

## 异常与取消

`flowbus-core` 默认不会帮你把订阅错误“悄悄吃掉再当没事发生”。它区分 3 类情况：

1. 事件值和期望类型对不上：记一条 `logger.warn(...)`，然后把 `FlowBusErrorPhase.ValueCast` 交给 `errorHandler`。
2. 订阅回调自己抛普通异常：记一条 `logger.warn(...)`，然后把 `FlowBusErrorPhase.SubscriberCallback` 交给 `errorHandler`。
3. 订阅回调抛 `CancellationException`：继续向外抛，不会被日志或 `errorHandler` 吞掉。

如果你需要接管这条链路，重点看这 3 个入口：

1. `FlowBusLogger`：决定是否记录告警日志。
2. `FlowBusErrorHandler`：决定收到错误后是继续抛出、忽略，还是转成你自己的处理逻辑。
3. `FlowBusErrorContext`：告诉你这是哪个事件、哪个阶段、是否 sticky、是否带 scope、当时用了哪个 dispatcher。

最常见的两种策略：

1. 默认策略：用 `FlowBusErrorHandler.Rethrow`，一旦订阅失败就直接暴露问题。
2. 兜底策略：自定义 `errorHandler`，把错误上报或打点，再决定是否继续。

<a id="事件类型怎么设计更清楚"></a>

## 事件建模

推荐顺序：

1. 单个动作：`data class` / `data object`
2. 同一业务域多个动作：`sealed interface` / `sealed class`
3. 简单值类型只在语义已经足够明确时再使用

### 单个动作

```kotlin
data object RefreshEvent
data class ShowToastEvent(val message: String)
```

### 同一业务域多个动作

```kotlin
sealed interface SyncEvent {
    data object Start : SyncEvent
    data class Progress(val percent: Int) : SyncEvent
    data object Finish : SyncEvent
}
```

发送时要显式指定父类型，这样这些子事件才会进入同一个通道：

```kotlin
scope.launch(start = CoroutineStart.UNDISPATCHED) {
    DefaultFlowBus.flow<SyncEvent>().collect { event ->
        when (event) {
            SyncEvent.Start -> onStart()
            is SyncEvent.Progress -> onProgress(event.percent)
            SyncEvent.Finish -> onFinish()
        }
    }
}

DefaultFlowBus.post<SyncEvent>(SyncEvent.Start)
DefaultFlowBus.post<SyncEvent>(SyncEvent.Progress(percent = 50))
```

<a id="默认单例如何配置"></a>

## 默认单例配置

如果你想在首次使用前替换默认配置：

```kotlin
DefaultFlowBus.configure(
    FlowBusConfig(
        stickyReplay = 1,
        normalBufferCapacity = 32
    )
)
```

如果你想安装自己创建的 bus：

```kotlin
DefaultFlowBus.install(
    FlowBus(
        config = FlowBusConfig(normalBufferCapacity = 32)
    )
)
```

注意：配置或安装要在第一次真正使用 `DefaultFlowBus` 之前完成。

这里的“第一次真正使用”包括：

1. `DefaultFlowBus.raw()`
2. `DefaultFlowBus.post(...)` / `emit(...)`
3. `DefaultFlowBus.flow(...)` / `stickyFlow(...)`
4. `DefaultFlowBus.scoped(...)` / `openScope(...)`

如果这些入口已经调用过，再执行 `configure(...)` 或 `install(...)` 会抛 `IllegalStateException`。

<a id="常见边界说明"></a>

## 行为边界

### 名称校验

以下名称都不能为空白字符串：

1. `eventName`
2. `scopeName`
3. `EventChannel` / `EventKey` 的 `name`

如果你传入 `"   "` 这类值，API 会直接抛 `IllegalArgumentException`，不会帮你兜底成默认值。

### 缓冲和溢出策略

如果普通事件没有额外缓冲，`overflowPolicy` 必须使用 `BufferOverflow.SUSPEND`。

如果 sticky 事件同时没有 replay 和额外缓冲，也必须使用 `BufferOverflow.SUSPEND`。

否则 sticky 事件没有可用的实时缓冲或 replay 存储，FlowBus 会在初始化阶段提前抛 `IllegalArgumentException`。

### scope 关闭后会发生什么

`FlowBusScope.close()` 做的是两件事：

1. 立即让这个 `FlowBusScope` 句柄失效，拒绝后续发送、取流和 sticky 操作。
2. 让已开始的发送 / 取流操作继续使用原 store，并在这些操作结束后移除 store、清掉缓存。

同一个 `scopeName` 同一时间只能打开一个可关闭的 `FlowBusScope` 句柄；如果你只是想共享同名 scope，不想持有关闭权，请使用 `scoped(...)`。

`close()` 本身不等待清理完成。如果你需要确认 store 已清理，或者希望拿到超时结果，请使用下面的等待入口。

关闭入口对比如下：

| 入口 | 是否立即让句柄失效 | 是否等待 store 清理 | 适合场景 | 不符合预期先查 |
| --- | --- | --- | --- | --- |
| `close()` | 是 | 否 | UI、生命周期回调、只需要禁止继续使用当前句柄 | 是否还有已开始的发送 / 取流操作未结束 |
| `closeSuspending()` | 自己发起关闭时先禁止新操作，等待在途操作期间 `isClosed=false` | 是，可取消等待 | 协程中需要等待清理完成 | 是否有挂起发送被背压卡住，以及由谁先发起关闭 |
| `tryClose(timeoutMillis)` | 自己发起关闭时先禁止新操作，等待在途操作期间 `isClosed=false` | 最多等待指定时间 | 测试、退出流程、需要明确超时结果 | timeout 是否太短，或是否已有关闭使句柄失效 |

如果 scope 跟随外层生命周期，优先用 `openScope(name, closeWhen = job)` 或 `bindTo(job)`，避免忘记关闭。

关闭结果里如果出现 `ClosingInProgress`，表示已有另一个等待入口发起关闭且仍在等待在途操作，本次调用没有重复抢占。这个阶段禁止新发送和取流，但 `isClosed` 仍为 `false`，保留 1.0.7 的状态语义。

1.1.0 已发布的取消规则：如果 `closeSuspending()` 自己发起关闭，在准备 store 清理之前被取消，且没有收到永久关闭请求，会撤销这次等待并恢复 scope 可用，调用方可以稍后重试。若 `close()` 或生命周期绑定已经先让句柄失效并准备关闭，`closeSuspending()` 只等待该关闭；取消只终止当前调用方的等待，不会恢复旧句柄或撤销清理。准备清理之后取消也不会回滚关闭。若本次关闭等待期间收到显式 `close()`、`removeScope()` 或绑定 Job 完成请求，库会保留该永久关闭请求：即使发起等待随后取消，也继续关闭，不恢复 scope 可用。等待使用可取消挂起，不占用线程进行条件变量等待。

在另一关闭等待期间调用 `close()`，新操作已经被拒绝，永久关闭请求会被记住；`isClosed` 仍沿用该等待的状态，直到等待成功或取消/超时/中断后准备清理才变为 true。

`tryClose(timeoutMillis)` 自己发起关闭并在等待在途操作时超时，且没有已到达的永久关闭请求时，scope 恢复可用；等待已有 `close()` 或生命周期关闭时超时，仅表示清理尚未完成。等待期间已到达的显式 `close()`、`removeScope()` 和绑定 Job 完成请求，都不会因超时或线程中断丢失。

它不会做的事：

1. 不会主动 cancel 你之前已经拿到手的旧 `Flow` 引用。
2. 不会阻止你后续再用同名 scope 重新创建新的 store。

### `clearSticky` 和 `removeSticky` 的区别

| API | 清 replay | 移除当前 store 条目 | 后续访问 | 已持有旧 `Flow` 引用 |
| --- | --- | --- | --- | --- |
| `clearSticky(...)` | 是 | 否 | 继续复用当前 sticky 通道 | 不会被主动 cancel |
| `removeSticky(...)` | 是 | 是 | 按需新建 sticky 通道 | 不会被主动 cancel |
| `consumeStickyLatest(...)` | 是 | 否 | 继续复用当前 sticky 通道 | 不会被主动 cancel |

### `removeEvent` 的边界

1. `removeEvent(...)` 只移除当前 store 里的普通事件条目，不会影响同名 sticky 通道。
2. `removeEvent(...)` 会保留同名通道的类型绑定，不能把同一个 `eventName` 换成另一个事件类型复用。
3. 如果你已经拿到了旧的普通事件 `Flow` 引用，它不会被主动 cancel；后续再次访问同名普通事件时会按需新建通道。
4. 在 `FlowBusScope` 上调用 `removeEvent(...)` 仍然遵守 scope 生命周期，scope 关闭后会抛 `IllegalStateException`。

### 错误处理与日志

如果你使用订阅侧的错误处理能力，核心规则可以直接记这 4 句：

1. 类型不匹配会先记一条 `logger.warn(...)`，再走 `errorHandler`。
2. 订阅回调自己抛普通异常，也会先记日志，再走 `errorHandler`。
3. `CancellationException` 不会被吞掉，会继续往外抛。
4. `FlowBusErrorContext.phase` 用来区分到底是“值类型不匹配”还是“回调执行失败”。

对应关系：

1. `ValueCast`：收到的值和期望类型对不上。
2. `SubscriberCallback`：类型已经过了，但你的回调代码自己抛异常。
3. `logger`：负责记录告警，不决定是否继续执行。
4. `errorHandler`：负责决定异常是继续向外抛，还是改成忽略 / 自定义处理。

如果你什么都不配，默认行为是 `FlowBusErrorHandler.Rethrow`，也就是收到异常后继续抛出。

## 模块边界

`flowbus-core` 专注平台无关的事件总线能力：事件分发、sticky event、命名 scope、生命周期绑定、日志和错误处理配置。

如果你需要平台适配层，可以基于这个模块自己封装。

如果你只想在 Android 项目里快速接入，并且不想自己管理 bus 实例，优先看 Android 适配模块。

如果你需要多实例隔离、依赖注入或显式生命周期控制，直接使用原始 `FlowBus`。

## 仓库链接

1. 当前仓库主页：[FlowBus](https://github.com/logan0817/FlowBus)
2. Core 模块目录：[flowbus-core](https://github.com/logan0817/FlowBus/tree/main/flowbus-core)
3. Android 模块文档：[library-android README](../library-android/README.md)
4. 示例应用模块：[app](https://github.com/logan0817/FlowBus/tree/main/app)
