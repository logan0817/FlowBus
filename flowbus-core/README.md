英文文档 [English Document](./README_EN.md)

# flowbus-core

`flowbus-core` 是 FlowBus 的平台无关核心模块。

它基于 Kotlin Coroutines 与 Flow，提供：

1. 类型安全事件分发
2. sticky event（后订阅者也能读到最近一次值）
3. root bus / scoped bus 隔离
4. 显式生命周期控制的 `FlowBusScope`
5. 命名事件句柄 `EventChannel<T>`
6. 更简洁的 Kotlin 风格语法糖，如 `event.send()`

如果你不是 Android 项目，或者你需要自己管理 bus 实例、scope 生命周期、多实例隔离，这个模块就是主入口。

## 适合场景

如果你符合下面任意一种情况，就可以直接从 `flowbus-core` 开始：

1. 你不是 Android 项目
2. 你想自己创建 `FlowBus()`，而不是依赖默认单例
3. 你需要多实例隔离，或者要把 bus 生命周期交给依赖注入
4. 你需要 Session / Repository / Worker / Task 级别的 scope

如果你是 Android 项目，通常先看 Android 适配模块会更快：

1. Android 模块文档：[library-android README](https://github.com/logan0817/FlowBus/blob/main/library-android/README.md)

## 先看这 9 句

1. 想开箱即用，就先用 `DefaultFlowBus`。
2. 需要依赖注入、多实例隔离或明确生命周期，就自己创建 `FlowBus()`。
3. 默认事件名不是类短名，而是事件类型完整类名。
4. 同一类型要拆多个通道时，用 `eventChannel<T>("name")` 或显式 `eventName`。
5. `EventChannel` 适合长期复用和公开语义，值糖 API 适合把发送调用写短，直接写 `eventName` 适合局部少量使用。
6. `scoped(...)` 是共享命名视图，`openScope(...)` 是带显式生命周期的 scope 句柄。
7. `post*` / `send()` 是立即尝试写入并返回 `Boolean`；不能接受静默失败时，改用 `emit*` / `awaitSend*`。
8. `tryPost*Result` 返回总线层诊断结果；`consumeStickyLatest(...)` 只读取并清理当前 sticky replay。
9. `DefaultFlowBus.configure(...)` / `install(...)` 必须在第一次真正使用前完成。

清理 API 单独记住这 2 点：

1. `removeScope()` 会关闭同名打开中的 `FlowBusScope` 句柄，并清理当前 store。
2. `removeEvent()`、`removeSticky()` 不会主动 cancel 你已经拿到手的旧 `Flow` 引用。

## 安装
[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.0.6")  // 发布后可使用 1.0.6，实际 latest 以上方徽章为准
```

## 先看最短路径

第一次使用时，只记 4 件事就够了：

1. 默认单例发送：`DefaultFlowBus.post(MyEvent(...))`
2. 默认单例订阅：`DefaultFlowBus.flow<MyEvent>()`
3. 偏好“事件自己发送自己”的写法：`MyEvent(...).send()`
4. 不能接受静默失败时：把 `post(...)` / `send()` 换成 `emit(...)` / `awaitSend()`

最短可用示例：

```kotlin
data class SyncFinishedEvent(val taskId: String)

DefaultFlowBus.post(SyncFinishedEvent(taskId = "task-1"))

scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

对象式发送就用 `send()`；必须保证写入成功就用 `awaitSend()`。

如果你准备替换默认单例配置，要先调用 `DefaultFlowBus.configure(...)` 或 `install(...)`，再第一次真正使用它。

## 什么时候该从 `DefaultFlowBus` 切到 `FlowBus()`

以下情况建议自己创建实例：

1. 你希望测试、功能模块、租户、会话之间完全隔离
2. 你想通过依赖注入控制 bus 生命周期
3. 你需要不同配置的 bus
4. 你不想使用默认全局单例

示例：

```kotlin
val bus = FlowBus()

bus.post(SyncFinishedEvent(taskId = "task-1"))

scope.launch {
    bus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

这时所有事件都只在这个 `bus` 实例里流动，不会和 `DefaultFlowBus` 混在一起。

## 命名事件句柄：`eventChannel(...)`

当你不想把 `eventName` 字符串散落在业务代码里，或者同一个类型需要多个语义不同的通道时，推荐把通道本身抽成一个对象。

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("Saved")

scope.launch {
    toastChannel.flow().collect { message ->
        showToast(message)
    }
}

scope.launch {
    toastChannel.collect { message ->
        showToast(message)
    }
}
```

如果你自己创建了 bus，也可以显式指定目标：

```kotlin
val bus = FlowBus()
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.postOn(bus, "Saved")

scope.launch {
    toastChannel.flowOn(bus).collect { message ->
        showToast(message)
    }
}

scope.launch {
    toastChannel.collectOn(bus) { message ->
        showToast(message)
    }
}
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

## 作用域怎么理解：root、scoped、openScope

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

featureBus.post(FeatureRefreshEvent(reason = "manual"))

scope.launch {
    featureBus.flow<FeatureRefreshEvent>().collect { event ->
        refresh(event.reason)
    }
}
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

syncScope.post(SyncProgress(percent = 10))

scope.launch {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}
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

## 如果你还想使用更底层的 typed key

大多数时候你可以直接用 `bus.post(value)` / `bus.flow<T>()`。

但如果你想显式持有一个稳定 key，或者要和 Java / 底层 API 互操作，可以用 `EventKey<T>`：

```kotlin
val refreshKey = eventKey<FeatureRefreshEvent>("feature.refresh")
val bus = FlowBus()

bus.post(refreshKey, FeatureRefreshEvent(reason = "manual"))

scope.launch {
    bus.flow(refreshKey).collect { event ->
        refresh(event.reason)
    }
}
```

`EventChannel<T>` 可以随时转回 `EventKey<T>`：

```kotlin
val channel = eventChannel<String>("ui.toast")
val key = channel.asEventKey()
```

## API 选择矩阵

| 需求 | 推荐 API |
| --- | --- |
| 默认单例 | `DefaultFlowBus` |
| 多实例隔离 | `FlowBus()` |
| 想最短发送 | `post(...)` / `send()` |
| 需要返回是否接收 | `post(...)` / `send()` 的 `Boolean` |
| 需要发送诊断结果 | `tryPostResult(...)` / `tryPostStickyResult(...)` |
| 保证写入成功 | `emit(...)` / `awaitSend()` |
| 直接订阅并复用 FlowBus 错误处理 | `collect(...)` / `collectSticky(...)` |
| 自己组合 Flow 操作符 | `flow(...)` / `stickyFlow(...)` |
| 命名通道 | `eventChannel<T>("name")` |
| 命名 scope 视图 | `scoped(...)` |
| 显式生命周期 scope | `openScope(...)` |
| 查看事件诊断元数据 | `inspect()` / `inspector().snapshot()` |
| 移除普通事件当前通道 | `removeEvent(...)` |
| 清理 sticky 缓存或通道 | `clearSticky(...)` / `removeSticky(...)` |
| 读取并清空 sticky 最新值 | `consumeStickyLatest(...)` |

## 发送 API 怎么选

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

### 必须保证写入成功

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

| 结果 | 含义 |
| --- | --- |
| `accepted = true` | `tryEmit` 没有立刻拒绝这次调用 |
| `accepted = false` | 底层流拒绝了这次非挂起写入 |
| `AcceptedWithDropOldestPolicy` | 新值可能挤掉旧值 |
| `AcceptedWithDropLatestPolicy` | 这次调用可能被接受，但当前值仍可能不会进入缓冲 |

它不表示订阅者已经处理事件。`DROP_OLDEST` / `DROP_LATEST` 也不是可靠队列策略。

## 订阅 API 怎么选

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

## 诊断 API 怎么用

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

## `EventChannel`、值糖 API、`eventName` 怎么选

如果你在这 3 种写法里犹豫，可以直接按下面的规则选：

| 场景 | 更推荐的写法 |
| --- | --- |
| 这个通道会被很多地方复用，想把名字集中定义 | `eventChannel<T>("name")` |
| 只是临时发一次，想写得最短 | `event.send()` / `event.sendOn(target)` |
| 已经有稳定名字，但不想单独建 channel 对象 | 直接传 `eventName` |
| 面向公开 API 或长期维护代码 | 优先 `eventChannel<T>("name")` |
| 只是内部简单广播 | 值糖 API 或直接 `post(value)` 都可以 |

可以把它们理解成 3 个层级：

1. `eventChannel<T>("name")`：把“通道”本身抽成一个稳定对象，最适合长期复用。
2. `event.send()` / `event.sendOn(target)`：把“发送动作”写短，最适合一次性调用点。
3. `eventName = "..."`：最直接，但也最容易把字符串散落在代码里。

值糖 API 的直接对应关系：

| 写法 | 实际语义 |
| --- | --- |
| `event.send()` | 等价于 `DefaultFlowBus.post(event)` |
| `event.awaitSend()` | 等价于 `DefaultFlowBus.emit(event)` |
| `event.sendSticky()` | 等价于 `DefaultFlowBus.postSticky(event)` |
| `event.awaitSendSticky()` | 等价于 `DefaultFlowBus.emitSticky(event)` |
| `event.sendOn(target)` | 等价于对这个目标调用对应的 `post(...)` |
| `event.awaitSendOn(target)` | 等价于对这个目标调用对应的 `emit(...)` |

补一句：

1. 这些值糖 API 只是更短的写法，不是另一套分发规则。
2. 默认 `eventName` 仍然是事件类型完整类名。
3. 自定义 `eventName` 仍然不能为空白字符串。
4. 发到 `FlowBus`、`ScopedFlowBus`、`FlowBusScope` 时，行为边界和对应的 `post*` / `emit*` 完全一致。

## `post`、`emit`、`send`、`awaitSend` 怎么理解

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

1. 挂起函数
2. 遵循背压
3. 会等待直到成功写入

适合：

1. 关键链路通知
2. 不希望事件默默丢掉

## sticky event 什么时候该用

sticky event 会保存最近一次值，后来订阅的人也能立刻读到。

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

## 订阅异常怎么处理

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

## 事件类型怎么设计更清楚

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
DefaultFlowBus.post<SyncEvent>(SyncEvent.Start)
DefaultFlowBus.post<SyncEvent>(SyncEvent.Progress(percent = 50))

scope.launch {
    DefaultFlowBus.flow<SyncEvent>().collect { event ->
        when (event) {
            SyncEvent.Start -> onStart()
            is SyncEvent.Progress -> onProgress(event.percent)
            SyncEvent.Finish -> onFinish()
        }
    }
}
```

## 默认单例如何配置

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

## 如果你只想记最短用法，看这里

### 默认单例

```kotlin
DefaultFlowBus.post(MyEvent(...))
scope.launch { DefaultFlowBus.flow<MyEvent>().collect { handle(it) } }
```

### 对象式发送

```kotlin
MyEvent(...).send()
scope.launch { DefaultFlowBus.flow<MyEvent>().collect { handle(it) } }
```

### 命名 channel

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("Saved")
scope.launch { toastChannel.flow().collect { showToast(it) } }
```

### 显式 scope

```kotlin
val taskScope = DefaultFlowBus.openScope("task", closeWhen = scope)
taskScope.post(TaskProgress(percent = 10))
scope.launch { taskScope.flow<TaskProgress>().collect { render(it) } }
```

## 常见边界说明

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

| 入口 | 是否立即让句柄失效 | 是否等待 store 清理 | 适合场景 |
| --- | --- | --- | --- |
| `close()` | 是 | 否 | UI、生命周期回调、只需要禁止继续使用当前句柄 |
| `closeSuspending()` | 是 | 是 | 协程中需要等待清理完成 |
| `tryClose(timeoutMillis)` | 是 | 最多等待指定时间 | 测试、退出流程、需要明确超时结果 |

如果 scope 跟随外层生命周期，优先用 `openScope(name, closeWhen = job)` 或 `bindTo(job)`，避免忘记关闭。

关闭结果里如果出现 `ClosingInProgress`，表示已有另一个关闭动作正在处理这个 scope，本次调用没有重复抢占。

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
3. Android 模块文档：[library-android README](https://github.com/logan0817/FlowBus/blob/main/library-android/README.md)
4. 示例应用模块：[app](https://github.com/logan0817/FlowBus/tree/main/app)
