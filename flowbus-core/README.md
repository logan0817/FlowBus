英文文档 [English Document](./README_EN.md)

# flowbus-core

`flowbus-core` 是一个可直接使用的独立 Kotlin 事件总线库。

它基于 Kotlin Coroutines 与 Flow，提供类型安全事件通道、sticky event、共享作用域与生命周期绑定能力。

## 安装

```gradle
implementation("io.github.logan0817:flowbus-core:<latest-version>")
```

## 适合使用这个模块的场景

- 纯 Kotlin / Coroutines 项目
- 服务端、桌面端、CLI、后台任务或共享业务层
- 需要显式使用 `FlowBus`、`EventKey` 和作用域控制
- 想先用 `DefaultFlowBus` / `bus.flow<T>()` 这类默认 API 快速接入，再按需下沉到底层 API
- 需要把总线作用域绑定到 `Job` 或 `CoroutineScope`
- 需要在 FlowBus 之上构建自己的平台适配层

## 核心 API

- `FlowBus`：根总线入口
- `DefaultFlowBus`：默认单例入口，可直接暴露完整 root bus API
- `EventKey<T>` / `eventKey(...)`：类型安全事件 key
- `post(...)` / `emit(...)`：发送普通事件
- `postSticky(...)` / `emitSticky(...)`：发送粘性事件
- `flow(...)` / `stickyFlow(...)`：以 `Flow` 形式读取事件
- `FlowBus.post(value)` / `FlowBus.flow<T>()`：不显式声明 `EventKey` 的简化重载
- `EventChannel<T>` / `eventChannel(...)`：把命名事件通道提升成一等对象
- `event.send()` / `event.sendOn(bus)`：对象式发送语法糖，适合更贴近 Kotlin 的调用风格
- `FlowBus.scoped(...)` / `DefaultFlowBus.scoped(...)`：获取命名共享作用域
- `FlowBus.openScope(...)` / `DefaultFlowBus.openScope(...)`：创建可显式关闭的作用域
- `FlowBusScope.bindTo(job)` / `bindTo(scope)`：随协程生命周期自动关闭作用域
- `FlowBusConfig`：配置 logger、错误处理与缓冲策略

## 基础用法

```kotlin
val bus = FlowBus()
val syncEvent = eventKey<SyncEvent>("sync.event")

bus.post(syncEvent, SyncEvent.Start)

scope.launch {
    bus.flow(syncEvent).collect { event ->
        handle(event)
    }
}
```

## 默认开箱即用用法

如果你不想先创建 `FlowBus` 和 `EventKey`，可以直接使用 `DefaultFlowBus`，
或者直接在已有 `FlowBus` / `ScopedFlowBus` / `FlowBusScope` 上使用默认重载：

```kotlin
DefaultFlowBus.post(SyncEvent.Start)

scope.launch {
    DefaultFlowBus.flow<SyncEvent>().collect { event ->
        handle(event)
    }
}

val bus = FlowBus()
bus.post(SyncEvent.Start)

scope.launch {
    bus.flow<SyncEvent>().collect { event ->
        handle(event)
    }
}
```

默认会以事件类型全名作为 event name。
如果你更偏好“事件对象自己发送自己”的写法，也可以这样写：

```kotlin
SyncEvent.Start.send()
SyncEvent.Done.sendOn(bus)
SyncEvent.Done.sendOn(bus, eventName = "sync.secondary")
```

如果你希望把命名事件本身提成稳定句柄，避免在业务代码里反复散落字符串，也可以这样写：

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("Saved")

scope.launch {
    toastChannel.flow().collect { message ->
        showToast(message)
    }
}
```

如果同一类型需要多个 channel，也可以显式传入 `eventName`：

```kotlin
bus.post(value = SyncEvent.Start, eventName = "sync.primary")
bus.flow<SyncEvent>(eventName = "sync.primary")
```

如果你希望默认单例在初始化阶段就带上固定配置或被依赖注入接管，可以在首次使用前安装：

```kotlin
DefaultFlowBus.configure(
    FlowBusConfig(stickyReplay = 1)
)

// 或者
DefaultFlowBus.install(
    FlowBus(
        config = FlowBusConfig(normalBufferCapacity = 32)
    )
)
```

如果默认简化 API 不够，也可以直接在 `DefaultFlowBus` 上使用 typed key/root scope 能力：

```kotlin
val syncEvent = eventKey<SyncEvent>("sync.event")
val session = DefaultFlowBus.openScope("sync-session", closeWhen = scope)

DefaultFlowBus.post(syncEvent, SyncEvent.Start)
session.post(syncEvent, SyncEvent.Done)
```

## 作用域用法

```kotlin
val bus = FlowBus()
val session = bus.openScope("sync-session", closeWhen = scope)
val syncState = eventKey<SyncState>("sync.state")

session.postSticky(syncState, SyncState.Running)

scope.launch {
    session.stickyFlow(syncState).collect { state ->
        render(state)
    }
}
```

如果你希望只拿一个共享视图、不负责 close 生命周期，也可以直接用 `scoped(...)`：

```kotlin
val featureBus = DefaultFlowBus.scoped("feature-a")
featureBus.post(FeatureEvent.Loading)
```

## 模块边界

`flowbus-core` 只提供平台无关的事件总线能力，包括事件分发、sticky event、命名作用域、
生命周期绑定、日志与错误处理配置。

如果你需要特定平台的适配层，可以在此基础上自行封装。

如果你只想快速接入而不自己管理总线实例，优先从 `DefaultFlowBus` 开始；
如果你需要多实例隔离、依赖注入或更明确的生命周期控制，再切回原始 `FlowBus`。

## 仓库链接

- 仓库主页（Android 集成模块 `library-android` 也在此仓库中）：[FlowBus](https://github.com/logan0817/FlowBus)
- Demo 模块：[app](https://github.com/logan0817/FlowBus/tree/master/app)

