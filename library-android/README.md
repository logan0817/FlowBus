英文文档 [English Document](./README_EN.md)

# library-android

`library-android` 是 FlowBus 的 Android 适配模块。

Gradle 模块目录名是 `library-android`，对外发布的依赖坐标是：

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.7")  // 发布后可使用 1.0.7，实际 latest 以上方徽章为准
```

如果你是 Android 项目接入 FlowBus，通常就从这个模块开始，不需要先研究 `flowbus-core`。

## 功能点

| 能力 | 用法入口 | 适合场景 |
| --- | --- | --- |
| 全局事件广播 | `postEvent(...)` / `onEvent<T> { ... }` | 登录成功、后台同步、多个页面刷新 |
| owner 局部事件 | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | Fragment 通知 Activity、NavBackStackEntry 内部通信 |
| 命名 channel | `eventChannel<T>("name")` | Toast、SnackBar、导航命令等同类型不同语义事件 |
| Flow 组合 | `eventFlow<T>()` / `collectEvent(flow)` | 先拿 `Flow`，再做 `map`、`filter`、`debounce` |
| sticky 最近值 | `postStickyEvent(...)` / `onEvent<T>(isSticky = true)` | 页面恢复后先拿最近一次初始化结果 |
| 发送诊断 | `tryPostEventResult(...)` | 排查订阅数、溢出策略、底层 `tryEmit` 是否拒绝 |

FlowBus 适合“某个地方发出通知，多个地方可能关心”的异步事件。页面内部长期状态优先用 `StateFlow`，明确的一对一调用优先用方法调用、use case 或挂起函数。

## 3 分钟上手

### 1. 添加依赖
[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.7")  // 发布后可使用 1.0.7，实际 latest 以上方徽章为准
```

### 2. 定义一个事件类型

如果只是一个简单动作，直接一个 `data class` 或 `data object` 就够了：

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 3. 在任意地方发送事件

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

### 4. 在页面里接收事件

最短写法：

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    viewModel.refreshFrom(event.source)
}
```

如果你更喜欢先拿到 `Flow` 再自己组合：

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<RefreshHomeEvent>()) { event ->
    viewModel.refreshFrom(event.source)
}
```

看到这里就已经可以开始用了：1. 发送：`postEvent(...)`。2. 接收：`onEvent<T> { ... }`。3. 如果要限制在某个 Activity / Fragment / NavBackStackEntry 作用域内，再用 `owner` 版本。

## 先记住这 7 句话

1. `postEvent(...)` / `emitEvent(...)` 发的是全局事件，整个 app 都能订阅。
2. `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` 发的是局部事件，只在这个 `owner` 对应的总线里流动。
3. 默认按“事件类型”分发；如果同一类型要拆成多个业务通道，就用 `eventChannel<T>("name")`。
4. `post*` 会立即尝试发送；`tryPost*` 会返回本次 `tryEmit` 有没有被底层流拒绝。
5. `tryPost*Result` 会返回总线层诊断结果，但不代表订阅回调已经处理完成。
6. `emit*` 会挂起到事件被底层流接收，适合关键发送链路。
7. `onEvent(...)` 是推荐的 UI 最短路径；`collectEvent(flow)` 更适合你已经拿到了 `Flow`。

## 为什么文档常写 `onEvent(...)`，示例里有时会写 `collectEvent(eventFlow(...))`

这两个写法不是对立关系，而是同一套能力的两个入口层级：

1. `onEvent(...)`：直接帮你订阅 FlowBus 事件，适合大多数 UI 页面
2. `eventFlow(...)` / `scopedEventFlow(...)` / `channel.flow()`：先把事件拿成 `Flow`
3. `collectEvent(flow)`：把这个 `Flow` 绑定到 `LifecycleOwner` 安全收集

所以最简单的全局事件，下面两种写法监听的是同一个来源，只是展开层级不同：

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

如果是 owner 作用域，也是一一对应的：

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

1. 页面里只是想“收一个事件”，优先 `onEvent(...)`
2. 你还想自己做 `map`、`filter`、`debounce`、`combine`，就用 `eventFlow(...) + collectEvent(...)`
3. 示例同时出现这两种写法，是为了把“最短路径”和“先拿 Flow 再组合”都演示出来，不代表它们的事件来源不同

## 最常见的 4 个使用场景

### 场景 1：全局广播，一个事件多个地方都能收到

适合：

1. 登录成功后让多个页面刷新
2. 某个后台任务完成后让多个观察者同步更新
3. 全局消息、埋点通知、刷新指令

```kotlin
data class SyncFinishedEvent(val successCount: Int)

postEvent(SyncFinishedEvent(successCount = 3))

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}
```

这类事件没有 owner 限制，谁订阅谁就能收到。

### 场景 2：Fragment 和 Activity 之间通信

适合：

1. Fragment 通知 Activity 刷新标题栏
2. Fragment 请求 Activity 执行导航、弹窗、权限流程
3. 某个页面树只想在当前 Activity 内部广播，不想影响全局

```kotlin
data object ReloadToolbarEvent

requireActivity().postScopedEvent(ReloadToolbarEvent)

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}
```

这里的 `requireActivity()` 不是监听者，而是“事件总线挂在哪个作用域上”的意思。
同理，你也可以把事件挂在 `NavBackStackEntry` 或自定义 `ViewModelStoreOwner` 上。

如果你更喜欢 owner 在前的写法，也可以这样：

```kotlin
requireActivity().postScopedEvent(ReloadToolbarEvent)

viewLifecycleOwner.collectEvent(requireActivity().scopedEventFlow<ReloadToolbarEvent>()) {
    renderToolbar()
}
```

### 场景 3：同样都是 `String`，但你不想到处写字符串通道名

适合：

1. `Toast`、`SnackBar`、导航命令这类“值类型简单，但语义不同”的事件
2. 同一个类型需要拆成多个明确的业务 channel
3. 你不想在业务代码里散落 `"ui.toast"`、`"page.reload"` 这类裸字符串

推荐写法：

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("保存成功")

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

如果这个事件只想在当前 Activity 范围内传播：

```kotlin
val activityCommand = eventChannel<String>("activity.command")

activityCommand.postTo(requireActivity(), "reload")

viewLifecycleOwner.onEvent(from = requireActivity(), channel = activityCommand) { command ->
    handleActivityCommand(command)
}
```

你当然也可以继续直接写 `eventName`：

```kotlin
postEvent(event = "保存成功", eventName = "ui.toast")
viewLifecycleOwner.collectEvent(eventFlow<String>(eventName = "ui.toast")) {
    showToast(it)
}
```

但对外公开或复用频繁的通道，优先推荐 `eventChannel(...)`，更清楚，也更不容易写错。

### 场景 4：ViewModel / Repository / Worker 通知 UI

适合：

1. 后台同步完成后提示 UI 刷新
2. Repository 完成某个动作后发通知给多个页面
3. Worker 执行完任务后，页面恢复时自动拿到结果

```kotlin
data class UploadFinishedEvent(val taskId: String)

class UploadViewModel : ViewModel() {
    fun onUploadSuccess(taskId: String) {
        postEvent(UploadFinishedEvent(taskId = taskId))
    }
}

class UploadFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.onEvent<UploadFinishedEvent> { event ->
            showToast("任务 ${event.taskId} 上传完成")
        }
    }
}
```

如果你只想通知当前 Activity 相关页面，而不是全 app，就把发送改成：

```kotlin
requireActivity().postScopedEvent(UploadFinishedEvent(taskId = taskId))
```

接收时改成：

```kotlin
viewLifecycleOwner.onEvent<UploadFinishedEvent>(from = requireActivity()) { event ->
    showToast("任务 ${event.taskId} 上传完成")
}
```

## API 选择矩阵

| 场景 | 推荐 API | 是否挂起 | 作用域 | sticky replay | 结果边界 |
| --- | --- | --- | --- | --- | --- |
| 全局轻量发送 | `postEvent(...)` | 否 | 全局 | 否 | best-effort，失败时记录 warning |
| 全局发送并看 `tryEmit` 结果 | `tryPostEvent(...)` | 否 | 全局 | 否 | 只返回是否被底层流拒绝 |
| 全局发送并拿诊断结果 | `tryPostEventResult(...)` | 否 | 全局 | 普通 / sticky 都可用 | 返回 event name、订阅数、sticky replay 数、溢出策略和结果分类 |
| owner 局部发送 | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | 否 | 指定 owner | 否 | 不会广播到其他 owner |
| 命名通道发送 | `eventChannel<T>("name")` + `channel.post(...)` | 否 | 全局或 owner | 普通 / sticky 都可用 | 适合集中维护稳定业务通道名 |
| 生命周期安全监听 | `onEvent(...)` | 否 | 全局或 owner | 由 `isSticky` 决定 | 最短 UI 接收入口 |
| 先拿 `Flow` 再组合 | `eventFlow(...)` + `collectEvent(...)` | 否 | 全局或 owner | 由 `isSticky` 决定 | 适合 `map`、`filter`、`debounce` |
| 保证写入成功 | `emitEvent(...)` / `emitStickyEvent(...)` | 是 | 全局或 owner | 普通 / sticky 都可用 | 按背压规则等待写入完成 |
| 一次性读取 sticky 最新值 | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | 否 | 全局或 owner | 读取后清空 | 返回最新值；没有 replay 时返回 `null` |

## 发送 API 怎么选

| API | 是否挂起 | 是否支持延迟 | 返回值 | 推荐场景 | 风险边界 |
| --- | --- | --- | --- | --- | --- |
| `postEvent(...)` | 否 | 是 | 无 | UI 层轻量通知 | best-effort，缓冲无法立刻接收时可能失败 |
| `tryPostEvent(...)` | 否 | 是 | `Boolean` | 想知道底层 `tryEmit` 是否被拒绝 | `delayMillis > 0` 时只代表延迟任务已安排 |
| `tryPostEventResult(...)` | 否 | 否 | `FlowBusPostResult` | 日志、测试断言、排查订阅数和溢出策略 | 不代表订阅回调已执行成功 |
| `emitEvent(...)` | 是 | 是 | 无 | 关键链路，需要按背压规则等待写入 | 调用方必须在协程中使用 |
| `postStickyEvent(...)` / `emitStickyEvent(...)` | 取决于具体 API | 是 | 取决于具体 API | 后订阅者需要收到最近值 | 不适合替代长期状态管理 |

常见全局发送：

```kotlin
postEvent(MyEvent(...))
```

如果要看总线层诊断结果：

```kotlin
val result = tryPostEventResult(MyEvent(...))
```

发到指定 owner 时，这两种写法等价，按代码风格选一种：

```kotlin
postEventTo(owner = requireActivity(), event = ReloadToolbarEvent)
```

```kotlin
requireActivity().postScopedEvent(ReloadToolbarEvent)
```

发到命名 channel：

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("保存成功")
toastChannel.postTo(requireActivity(), "保存成功")
```

## 接收 API 怎么选

| 场景 | 推荐 API | 生命周期托管 | 是否直接拿到 `Flow` | sticky 支持 | 推荐理由 |
| --- | --- | --- | --- | --- | --- |
| UI 层最短监听 | `viewLifecycleOwner.onEvent<T> { ... }` | 自动绑定 `LifecycleOwner` | 否 | 通过 `isSticky` 开启 | 适合大多数页面事件 |
| 命名 channel 监听 | `viewLifecycleOwner.onEvent(channel) { ... }` | 自动绑定 `LifecycleOwner` | 否 | 使用 sticky channel 入口 | 避免字符串散落 |
| 先组合再监听 | `eventFlow<T>()` + `collectEvent(...)` | `collectEvent` 托管 | 是 | 通过 `isSticky` 开启 | 适合 `map`、`filter`、`debounce` |
| owner 局部监听 | `owner.scopedEventFlow<T>()` + `collectEvent(...)` | `collectEvent` 托管 | 是 | 通过 `isSticky` 开启 | 只接收指定 owner 作用域事件 |
| 任意 `Flow` 生命周期收集 | `collectEvent(flow) { ... }` | 自动绑定 `LifecycleOwner` | 已由调用方提供 | 取决于传入流 | 不只限于 FlowBus |

最短监听：

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    render(event)
}
```

监听命名 channel：

```kotlin
viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

先拿到 `Flow`，再自己组合、过滤、转换：

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<RefreshHomeEvent>()) { event ->
    render(event)
}
```

owner 版本：

```kotlin
viewLifecycleOwner.collectEvent(requireActivity().scopedEventFlow<ReloadToolbarEvent>()) {
    renderToolbar()
}
```

命名 channel 版本：

```kotlin
viewLifecycleOwner.collectEvent(toastChannel.flow()) { message ->
    showToast(message)
}
```

## `postEvent` 和 `emitEvent` 到底有什么区别

| 维度 | `postEvent(...)` | `tryPostEvent(...)` | `tryPostEventResult(...)` | `emitEvent(...)` |
| --- | --- | --- | --- | --- |
| 是否挂起 | 否 | 否 | 否 | 是 |
| 写入方式 | 立即尝试写入 | 立即尝试写入 | 立即尝试写入并返回诊断结果 | 按背压规则等待写入 |
| 返回信息 | 无 | `Boolean` | `FlowBusPostResult` | 无 |
| 适合场景 | UI 点击、轻量提示、刷新通知 | 需要知道 `tryEmit` 是否被拒绝 | 日志、单测、排查订阅数和溢出策略 | 关键通知、Repository / Worker / ViewModel 内的重要链路 |
| 不代表什么 | 不代表一定送达 | 不代表订阅者已处理 | 不代表业务处理成功 | 不代表订阅者业务逻辑不会失败 |

`FlowBusPostResult` 包含：1. 事件名。2. 订阅数。3. sticky replay 数。4. 溢出策略。5. 结果分类。它只说明本次 `tryEmit` 有没有被底层流拒绝，不说明订阅回调已经处理成功。

`DROP_OLDEST` / `DROP_LATEST` 都不是可靠队列策略。前者可能覆盖旧事件，后者可能让当前事件不进入缓冲。关键链路要么用 `emitEvent(...)`，要么使用业务专用队列或状态机。

## sticky event 什么时候该用

sticky event 的核心语义是“事件发出后保留最近值，后来订阅的人也能先拿到这个值”。

| 状态 | 推荐 API | 后订阅者能否拿到最近值 | 是否清 replay | 适合场景 | 不适合场景 |
| --- | --- | --- | --- | --- | --- |
| 普通事件 | `postEvent(...)` / `emitEvent(...)` | 否 | 否 | Toast、导航、一次性点击事件 | 页面恢复后仍要读最近结果 |
| sticky 最近值 | `postStickyEvent(...)` / `emitStickyEvent(...)` | 是 | 否 | 当前配置、最近一次会话信息、初始化结果、页面恢复状态 | 长期状态管理 |
| 清空 sticky replay | `clearStickyEvent(...)` / `channel.clearSticky()` | 否 | 是 | 通道还要复用，只是不想继续回放旧值 | 需要彻底移除通道 |
| 移除 sticky 通道 | `removeStickyEvent(...)` / `channel.removeSticky()` | 否 | 是 | 当前 sticky 条目不再需要，后续访问按需新建 | 期待旧 `Flow` 引用被主动 cancel |
| 一次性读取 sticky 最新值 | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | 读取后不再保留 | 是 | 最近结果只应该被读取一次 | 替代 `StateFlow`，或阻止其他线程后续写入新 sticky 值 |

示例：

```kotlin
data class SessionReadyEvent(val userId: Long)

postStickyEvent(SessionReadyEvent(userId = 42L))

viewLifecycleOwner.onEvent<SessionReadyEvent>(isSticky = true) { event ->
    bindSession(event.userId)
}
```

不再需要时可以清掉：

```kotlin
clearStickyEvent<SessionReadyEvent>()
removeStickyEvent<SessionReadyEvent>()
```

如果某个 sticky 结果只应该被读取一次，可以消费最新值并立即清掉 replay：

```kotlin
val event = consumeStickyLatestEvent<SessionReadyEvent>()
```

owner 和命名 channel 也有对应入口：

```kotlin
val scopedEvent = consumeStickyLatestEvent<SessionReadyEvent>(owner = requireActivity())

val channelEvent = sessionReadyChannel.consumeStickyLatest()
```

这类 API 只读取当前 sticky replay 中的最新值，然后清掉 replay 缓存。它不消费普通事件，也不会阻止其他线程后续写入新的 sticky 值，因此不适合替代 `StateFlow` 做长期状态。

## 事件类型怎么设计，最容易读也最不容易出错

推荐顺序：

1. 单个动作，用一个明确的 `data class` / `data object`
2. 同一业务域的多个动作，用 `sealed interface` / `sealed class`
3. 只有在值真的非常简单而且语义明确时，才直接发 `String` / `Int`

### 推荐：单个动作直接一个类型

```kotlin
data object ReloadToolbarEvent
data class ShowToastEvent(val message: String)
```

### 推荐：同一业务域多个动作用 sealed

```kotlin
sealed interface MainUiEvent {
    data object Refresh : MainUiEvent
    data class ShowToast(val message: String) : MainUiEvent
    data class OpenDetail(val id: Long) : MainUiEvent
}
```

发送时记得指定父类型，这样这些子事件才会进入同一个事件通道：

```kotlin
postEvent<MainUiEvent>(MainUiEvent.Refresh)
postEvent<MainUiEvent>(MainUiEvent.ShowToast(message = "success"))

viewLifecycleOwner.onEvent<MainUiEvent> { event ->
    when (event) {
        MainUiEvent.Refresh -> refresh()
        is MainUiEvent.ShowToast -> showToast(event.message)
        is MainUiEvent.OpenDetail -> openDetail(event.id)
    }
}
```

如果你不显式写父类型，Kotlin 可能会把通道推断成具体子类型，这不是 FlowBus 的限制，而是类型推断的自然结果。

## 如果你只想记最短用法，看这里

### 全局事件

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
viewLifecycleOwner.onEvent<RefreshHomeEvent> { render(it) }
```

### Activity 局部事件

```kotlin
requireActivity().postScopedEvent(ReloadToolbarEvent)
viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) { renderToolbar() }
```

### 命名 channel

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("保存成功")
viewLifecycleOwner.onEvent(toastChannel) { showToast(it) }
```

## 可选配置

如果你想改底层缓冲、日志、错误处理，请在第一次调用任何总线 API 之前配置：

```kotlin
FlowBusAndroid.configure(
    FlowBusConfig(
        logger = MyFlowBusLogger,
        errorHandler = FlowBusErrorHandler.Rethrow
    )
)
```

只要已经创建过任意 `FlowEventBus`，后续再调用 `FlowBusAndroid.configure(...)` 就会抛 `IllegalStateException`。

建议把配置放在 `Application.onCreate()`，并且早于这些入口：

1. `postEvent(...)`
2. `eventFlow(...)`
3. `subscribeEvent(...)`
4. owner scope 示例代码

Android 模块没有单独做 UI 面板式诊断。

需要调试底层事件元数据时，可以在自建 `FlowEventBus` / core `FlowBus` 场景中使用 `flowbus-core` 的 `inspect()`。

写日志前请确认 `eventName`、`scopeName` 没有包含手机号、token、订单号等敏感值。

## 什么时候再去看 `flowbus-core`

当你有这些需求时，再看 core 文档就够了：

1. 需要自己管理 `FlowBus` 实例，而不是使用 Android 默认入口
2. 需要多个 bus 实例隔离
3. 需要非 Android 环境复用同一套事件模型
4. 需要 `FlowBusScope`、`EventKey`、`DefaultFlowBus` 这些更底层能力

文档入口：
1. GitHub 地址：[flowbus-core README](https://github.com/logan0817/FlowBus/blob/main/flowbus-core/README.md)

## 仓库链接

1. 仓库主页：[FlowBus](https://github.com/logan0817/FlowBus)
2. Core 模块：[flowbus-core](https://github.com/logan0817/FlowBus/tree/main/flowbus-core)
3. 示例应用模块：[app](https://github.com/logan0817/FlowBus/tree/main/app)
