英文文档 [English Document](./README_EN.md)

# flowbus-core

`flowbus-core` 是 FlowBus 的平台无关核心模块。

它基于 Kotlin Coroutines 与 Flow，提供：

- 类型安全事件分发
- sticky event
- root bus / scoped bus
- 显式生命周期控制的 `FlowBusScope`
- 命名事件句柄 `EventChannel<T>`
- 更简洁的 Kotlin 风格语法糖，如 `event.send()`

如果你不是 Android 项目，或者你需要自己管理 bus 实例、scope 生命周期、多实例隔离，这个模块就是主入口。

## 什么时候应该直接用它

适合：

- 纯 Kotlin / Coroutines 项目
- 服务端、桌面端、CLI、共享业务层
- 想自己创建 `FlowBus()`，而不是依赖默认单例
- 需要多个 bus 实例隔离
- 需要 Session / Repository / Worker / Task 级别的 scope
- 想基于 FlowBus 再封一层自己的平台适配

如果你是 Android 项目，通常先看 Android 适配模块会更快：

- 本地文档：[`../library-android/README.md`](../library-android/README.md)
- GitHub 地址：[library-android README](https://github.com/logan0817/FlowBus/blob/master/library-android/README.md)

## 先记住这 5 句话

1. 想开箱即用，就先用 `DefaultFlowBus`。
2. 需要依赖注入、多实例隔离或明确生命周期，就自己创建 `FlowBus()`。
3. 默认按事件类型分发；同一类型需要多个通道时，用 `eventChannel<T>("name")` 或显式 `eventName`。
4. `scoped(...)` 是共享作用域视图，`openScope(...)` 是带显式生命周期的 scope 句柄。
5. `post*` 是立即尝试发送，`emit*` / `awaitSend*` 是挂起直到成功写入。

## 安装

```gradle
implementation("io.github.logan0817:flowbus-core:<latest-version>")
```

## 3 分钟上手

### 1. 最简单的方式：直接用 `DefaultFlowBus`

```kotlin
data class SyncFinishedEvent(val taskId: String)

DefaultFlowBus.post(SyncFinishedEvent(taskId = "task-1"))

scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

这已经是完整可用的最短路径了：

- 发送：`DefaultFlowBus.post(...)`
- 接收：`DefaultFlowBus.flow<T>()`

### 2. 如果你更喜欢“事件对象自己发送自己”的写法

```kotlin
SyncFinishedEvent(taskId = "task-1").send()

scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

如果事件要发到某个特定 bus / scope：

```kotlin
val bus = FlowBus()

SyncFinishedEvent(taskId = "task-2").sendOn(bus)
```

### 3. 如果你需要挂起直到成功写入

```kotlin
scope.launch {
    DefaultFlowBus.emit(SyncFinishedEvent(taskId = "task-1"))
}
```

或者使用语法糖：

```kotlin
scope.launch {
    SyncFinishedEvent(taskId = "task-1").awaitSend()
}
```

## 什么时候该从 `DefaultFlowBus` 切到 `FlowBus()`

以下情况建议自己创建实例：

- 你希望测试、功能模块、租户、会话之间完全隔离
- 你想通过依赖注入控制 bus 生命周期
- 你需要不同配置的 bus
- 你不想使用默认全局单例

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
```

### 什么时候用 `eventChannel`，什么时候直接写 `eventName`

优先推荐 `eventChannel(...)` 的情况：

- 这个通道会被复用很多次
- 你想把业务语义集中定义
- 你不想在多个文件里复制 `"ui.toast"` 这样的字符串

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

- 全局广播
- 这个 bus 实例内部的公共事件

### `scoped(...)`

`scoped("feature-a")` 返回的是一个共享作用域视图。

适合：

- 你只想按名字把事件隔离到某个 scope
- 生命周期由外部统一管理
- 多处代码只需要拿同一个命名 scope 来收发

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

- Session
- Repository 生命周期
- Worker / Task 链路
- 一段业务流程结束后要彻底清掉事件缓存和 Flow

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

- `scoped(...)`：共享命名 bus 视图，不负责 close
- `openScope(...)`：带显式生命周期的 scope 句柄，可以 `close()`

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

## `post`、`emit`、`send`、`awaitSend` 怎么理解

### `post(...)` / `send()`

特点：

- 非挂起
- 手感最轻
- best-effort
- 缓冲无法立刻接收时会返回 `false`

适合：

- 普通广播
- 可以接受极端情况下发送失败的事件

### `emit(...)` / `awaitSend()`

特点：

- 挂起函数
- 遵循背压
- 会等待直到成功写入

适合：

- 关键链路通知
- 不希望事件默默丢掉

## sticky event 什么时候该用

sticky event 会保存最近一次值，后来订阅的人也能立刻读到。

适合：

- 最近一次初始化结果
- 最近一次会话信息
- 页面恢复后仍然要拿到的最新配置

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

简单理解：

- `clearSticky`：清 replay 缓存，但保留通道
- `removeSticky`：彻底移除这个 sticky 通道

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

## 仓库链接

- 仓库主页（Android 集成模块也在此仓库中）：[FlowBus](https://github.com/logan0817/FlowBus)
- Android 模块文档：[`../library-android/README.md`](../library-android/README.md)
- Demo 模块：[app](https://github.com/logan0817/FlowBus/tree/master/app)
