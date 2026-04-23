英文文档 [English Document](./README_EN.md)

# FlowBus

FlowBus 是一个基于 Kotlin Coroutines / Flow 的 Flow-first 事件框架。

它主要解决的是“事件广播”问题，不是状态管理框架，也不是对直接函数调用的替代品。

当前仓库包含两个对外模块：

- `flowbus-core`：平台无关的核心模块
- `flowbus`（仓库目录 `library-android`）：Android 推荐接入模块

## FlowBus 是做什么的

如果你经常会遇到这些问题，FlowBus 就会比较顺手：

- 页面 A 想通知页面 B 刷新，但不想直接持有引用
- Fragment 需要通知 Activity 执行某个动作
- ViewModel / Repository / Worker 想异步通知 UI
- 同一个事件需要被多个观察者同时收到
- 你想把事件当成 `Flow` 来订阅、组合、过滤

它更像一个“按类型或命名 channel 分发的广播系统”。

## 它不适合做什么

这些场景通常不建议用 FlowBus：

- 页面内部状态管理：优先 `StateFlow`
- 明确的一对一调用：优先直接方法调用 / use case
- 严格请求-响应：优先返回值、挂起函数、专用 channel
- 长期共享状态：优先状态容器，而不是一次性事件

简单理解：

- “一个地方发通知，很多地方可能要响应” -> 可以考虑 FlowBus
- “A 调 B 拿结果” -> 通常不该用 FlowBus

## 先选哪个模块

### 如果你是 Android 项目

直接从这个依赖开始：

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.5")  // 替换为上方徽章显示的最新版本
```

适合你如果你需要：

- 全局事件广播
- 基于 `ViewModelStoreOwner` 的局部事件作用域
- `eventFlow<T>()` / `owner.scopedEventFlow<T>()`
- `eventChannel<T>("...")` 命名事件句柄
- 生命周期安全的 `collectEvent(...)` / `onEvent(...)`

文档入口：

- 文档：[`library-android/README.md`](./library-android/README.md)

### 如果你是纯 Kotlin / Coroutines / 非 Android 场景

从这个依赖开始：

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.0.4")  // 替换为上方徽章显示的最新版本
```

适合你如果你需要：

- 自己管理 `FlowBus` 实例
- root bus + scoped bus
- `FlowBusScope` 生命周期绑定
- `EventKey`、`eventChannel(...)`、sticky event
- 非 Android 环境或自己封装上层适配

文档入口：

- 文档：[`flowbus-core/README.md`](./flowbus-core/README.md)

## 仓库结构

- 主仓库 `FlowBus` 继续维护 Android 模块、Demo 和整体集成。
- `flowbus-core` 现在作为 git submodule 挂载在当前目录下，同时拥有自己的独立仓库。
- 首次拉取主仓库时请使用 `git clone --recurse-submodules ...`，或在已有工作区执行 `git submodule update --init --recursive`。

## 第一次接触，建议这样开始

1. Android 用户先看 [`library-android/README.md`](./library-android/README.md)。
2. 先只记住最短路径：发送 `postEvent(...)`，接收 `onEvent<T> { ... }`。
3. 如果你需要感知 best-effort 发送是否成功，使用 `tryPostEvent(...)` / `tryPostEventTo(...)`。
4. 需要限制在某个 Activity / Fragment / NavBackStackEntry 作用域内，再看 owner 版本。
5. 同一类型需要多个通道时，再引入 `eventChannel<T>("name")`。
6. 只有当你需要多实例、显式 scope 生命周期或非 Android 使用时，再继续下沉到 `flowbus-core`。

## `onEvent(...)` 和 `collectEvent(eventFlow(...))` 是什么关系

第一次看 demo 时，最容易疑惑的点就是这里：

- 文档里更常写 `onEvent<T> { ... }`
- demo 里有时会写 `collectEvent(eventFlow<T>()) { ... }`

它们不是两套不同体系，监听的是同一条事件流，只是展开层级不同：

- `onEvent(...)`：UI 层最短写法，适合大多数页面监听
- `eventFlow(...)` / `scopedEventFlow(...)` / `channel.flow()`：先把事件拿成 `Flow`
- `collectEvent(flow)`：把任意 `Flow` 绑定到 `LifecycleOwner` 安全收集

最简单的全局事件，这两种写法可以理解成：

```kotlin
viewLifecycleOwner.onEvent<AddCartEvent> { event ->
    render(event)
}
```

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<AddCartEvent>()) { event ->
    render(event)
}
```

owner 作用域也是同样关系：

```kotlin
viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}
```

```kotlin
viewLifecycleOwner.collectEvent(requireActivity().scopedEventFlow<ReloadToolbarEvent>()) {
    renderToolbar()
}
```

建议你这样记：

- 页面里只是想“收一个事件”，优先 `onEvent(...)`
- 你还想自己做 `map`、`filter`、`debounce`、`combine`，就先拿 `eventFlow(...)` 再配合 `collectEvent(...)`
- demo 里同时出现这两种写法，是为了把“最短监听路径”和“先拿 Flow 再组合”都演示出来，不代表事件来源不同

## 3 分钟上手

下面是 Android 最短示例，也是大多数人第一次接入时最有帮助的路径。

### 1. 定义一个事件

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 2. 发送事件

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

### 3. 接收事件

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    viewModel.refreshFrom(event.source)
}
```

这三步就已经够你在项目里开始试用了。

## 最常见的 4 个使用场景

### 场景 1：全局刷新通知

适合：

- 登录成功后多个页面都要刷新
- 后台同步完成后多个观察者都要更新

```kotlin
data class SyncFinishedEvent(val successCount: Int)

postEvent(SyncFinishedEvent(successCount = 3))

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}
```

### 场景 2：Fragment 通知 Activity

适合：

- 刷新标题栏
- 请求 Activity 导航
- 当前页面树内部通信，但不想污染全局

```kotlin
data object ReloadToolbarEvent

requireActivity().postScopedEvent(ReloadToolbarEvent)

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}
```

### 场景 3：同一类型多个命名通道

适合：

- `Toast`
- `SnackBar`
- 导航命令
- 同样都是 `String`，但业务语义不同

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("保存成功")

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

### 场景 4：非 Android 或更底层的作用域控制

适合：

- Repository / Worker / Session 级别隔离
- 需要多个 bus 实例
- 需要 scope 生命周期跟随 `CoroutineScope` / `Job`

```kotlin
val syncScope = DefaultFlowBus.openScope("sync-task", closeWhen = scope)

syncScope.post(SyncProgress(percent = 10))

scope.launch {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}
```

更详细说明见 [`flowbus-core/README.md`](./flowbus-core/README.md)。

## API 选择速记

| 需求 | 推荐 API |
| --- | --- |
| 全局发送 | `postEvent(...)` |
| 想知道 best-effort 是否成功 | `tryPostEvent(...)` |
| 必须保证写入成功 | `emitEvent(...)` |
| 发送到某个 owner | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` |
| 命名通道 | `eventChannel<T>("name")` + `channel.post(...)` |
| 最短一行监听 | `onEvent(...)` |
| 先拿到 `Flow` 自己组合 | `eventFlow<T>()` / `owner.scopedEventFlow<T>()` |
| 生命周期安全收集任意 `Flow` | `collectEvent(flow) { ... }` |
| 非 Android / 多实例 / scope 生命周期 | `flowbus-core` |

### `post` 和 `emit` 怎么选

- `post*`：非挂起，best-effort，写法最轻
- `tryPost*`：非挂起，但会返回当前调用是否已被总线接收
- `emit*`：挂起直到成功写入，适合更关键的事件

### sticky event 什么时候用

适合：

- 最新配置
- 最近一次初始化结果
- 后来订阅的人也需要拿到最近一次值

不适合：

- Toast
- 导航
- 一次性点击动作

### 事件类型怎么建模

推荐顺序：

1. 单个动作：`data class` / `data object`
2. 同一业务域多个动作：`sealed interface` / `sealed class`
3. 只有值真的很简单时，才直接发 `String` / `Int`

如果你用 `sealed interface` 把多个子事件放进同一个通道，发送时记得显式指定父类型：

```kotlin
sealed interface MainUiEvent {
    data object Refresh : MainUiEvent
    data class ShowToast(val message: String) : MainUiEvent
}

postEvent<MainUiEvent>(MainUiEvent.Refresh)

viewLifecycleOwner.onEvent<MainUiEvent> { event ->
    when (event) {
        MainUiEvent.Refresh -> refresh()
        is MainUiEvent.ShowToast -> showToast(event.message)
    }
}
```

## 文档导航

- Android 接入与场景说明：[`library-android/README.md`](./library-android/README.md)
- Core 能力与多实例 / scope 用法：[`flowbus-core/README.md`](./flowbus-core/README.md)
- 英文文档：[`README_EN.md`](./README_EN.md)

## 仓库结构

- `flowbus-core`：核心框架模块
- `library-android`：Android 适配模块
- `app`：demo app

## Demo

<img src="GIF.gif" width="350" />

可下载 [Demo APK](https://raw.githubusercontent.com/logan0817/FlowBus/master/app/apk/app-debug.apk) 体验。

Demo 源码位于 [`app`](./app) 模块，可直接运行。建议按这个顺序看：

1. `MainActivity`：全局事件、owner 事件、非 UI demo 入口
2. `TestFragmentActivity`：演示 Activity 作用域事件、`eventChannel`，以及 Activity / Fragment 在同一 owner 作用域内共享接收
3. `LoginActivity`：owner 局部总线示例

## License

```text
MIT License
```
