英文文档 [English Document](./README_EN.md)

# FlowBus

FlowBus 是一个基于 Kotlin Coroutines / Flow 的 Flow-first 事件框架。

当前仓库包含两个对外模块：
- `flowbus-core`：平台无关的基础核心模块
- `flowbus`（`library-android`）：构建在 `flowbus-core` 之上的 Android 适配模块

## 为什么创建 FlowBus

FlowBus 用来处理更像“事件广播”的场景，例如 UI、ViewModel、Repository、Worker 之间的跨层通知，
以及全局事件或局部作用域事件的统一建模。

它不是状态管理或一对一调用的替代品：
- 页面内部状态优先 `StateFlow`
- 明确调用关系优先直接方法调用 / use case
- 严格请求-响应优先返回值、挂起函数或专用通道

## 先选对模块

### Android 项目

优先使用：

```gradle
implementation("io.github.logan0817:flowbus:<latest-version>")
```

适合你如果你需要：
- Android 应用里的全局事件
- 基于 `ViewModelStoreOwner` 的局部事件
- `eventFlow<T>()` / `eventFlowFrom<T>(owner = ...)`
- `eventChannel<T>("...")` 这种可复用的命名事件句柄
- `collectEvent(flow) { ... }`
- Activity / Fragment / ViewModel 场景下的生命周期安全收集

Android 模块文档：
- 本地文档：[`library-android/README.md`](./library-android/README.md)
- GitHub 地址：[library-android README](https://github.com/logan0817/FlowBus/blob/master/library-android/README.md)

### 平台无关 Kotlin / Coroutine 场景

使用：

```gradle
implementation("io.github.logan0817:flowbus-core:<latest-version>")
```

适合你如果你需要：
- root bus 与 named scoped bus
- `FlowBus`、`DefaultFlowBus`、`EventKey`、sticky event
- `EventChannel<T>` / `eventChannel(...)`
- `FlowBusScope`
- 基于 `Job` / `CoroutineScope` 的 scope 生命周期绑定
- `bus.post(value)` / `DefaultFlowBus.flow<T>()` 这类默认简化 API
- 自定义 logger / error handler / buffer 策略
- 自己构建上层适配，或在非 Android 架构中使用

Core 模块文档：
- 本地文档：[`flowbus-core/README.md`](./flowbus-core/README.md)
- GitHub 地址：[flowbus-core README](https://github.com/logan0817/FlowBus/blob/master/flowbus-core/README.md)

## 模块关系

- `flowbus-core` 定义事件模型与核心运行时行为
- `flowbus` 提供面向 Android 的推荐 API
- Android 用户默认从 `flowbus` 开始
- 非 Android 用户或适配层开发者从 `flowbus-core` 开始

## 使用原则

- FlowBus 是按事件类型分发的广播模型；同一类型的事件会进入同一个事件流
- Android 项目里优先用明确的 `data class` / `sealed class` 定义业务事件
- 全局广播用 `eventFlow<T>()`，局部作用域事件用 `eventFlowFrom<T>(owner = ...)` 或更直觉的 `owner.eventFlow<T>()`
- 必须保证送达时使用 `emitEvent*`，允许 best-effort 时才使用 `postEvent*`
- sticky event 只适合“后来订阅者也需要拿到最近一次值”的场景

## 仓库结构

- `flowbus-core`：核心框架模块
- `library-android`：Android 适配模块
- `app`：demo app

## 快速导航

### Android 用法

Android 推荐 API 在 `flowbus`：
- `postEvent(...)` / `emitEvent(...)`
- `postEventTo(owner = ..., event = ...)` / `emitEventTo(owner = ..., event = ...)`
- `eventFlow<T>()`
- `eventFlowFrom<T>(owner = ...)`
- `eventChannel<T>("...")`
- `channel.post(...)` / `channel.flow()`
- `channel.postTo(owner, ...)` / `channel.flowFrom(owner)`
- `owner.eventFlow<T>()`
- `stickyEventFlow<T>()`
- `stickyEventFlowFrom<T>(owner = ...)`
- `LifecycleOwner.onEvent(channel)` / `LifecycleOwner.onEvent(from = ..., channel = ...)`
- `LifecycleOwner.onEvent<T>(from = ...)`
- `collectEvent(flow) { ... }`
- `removeStickyEvent<T>()` / `clearStickyEvent<T>()`
- `postEvent*` / `postStickyEvent*`：best-effort 发送
- `emitEvent*` / `emitStickyEvent*`：遵循背压，挂起直到成功写入

查看：
- [`library-android/README.md`](./library-android/README.md)
- [Android 模块 GitHub 页面](https://github.com/logan0817/FlowBus/tree/master/library-android)

### Core 用法

Core 推荐 API 在 `flowbus-core`：
- `DefaultFlowBus`
- `FlowBus`
- `EventKey`
- `eventKey(...)`
- `EventChannel<T>` / `eventChannel(...)`
- `FlowBus.post(value)` / `FlowBus.flow<T>()`
- `channel.post(...)` / `channel.flow()`
- `channel.postOn(bus, ...)` / `channel.flowOn(bus)`
- `FlowBus.scoped(...)` / `DefaultFlowBus.scoped(...)`
- `FlowBus.openScope(...)` / `DefaultFlowBus.openScope(...)`
- `FlowBusScope.bindTo(job)`
- `FlowBusScope.bindTo(scope)`

查看：
- [`flowbus-core/README.md`](./flowbus-core/README.md)
- [Core 模块 GitHub 页面](https://github.com/logan0817/FlowBus/tree/master/flowbus-core)

## Demo

<img src="GIF.gif" width="350" />

Demo 源码位于 [`app`](./app) 模块，可直接运行或自行打包体验。

## License

```text
MIT License
```

