[English](README_EN.md) · [文档索引](../docs/README.md)

# FlowBus Android

`library-android` 是 FlowBus 的 Android 适配模块。

Gradle 模块目录名是 `library-android`，对外发布的依赖坐标是：

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.2.0")
```

如果你是 Android 项目接入 FlowBus，通常就从这个模块开始，不需要先研究 `flowbus-core`。

## 阅读导航

- [快速开始](#3-分钟上手) · [API 选择](#api-选择矩阵)
- [owner 配置](#android-owner-configuration) · [挂起回调与实例接入](#suspend-callbacks)
- [sticky](#sticky-与-replay) · [全局默认配置](#可选配置)
- [兼容性](../docs/compatibility.md) · [排障](../TROUBLESHOOTING.md)

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
implementation("io.github.logan0817:flowbus:1.2.0")
```

### 2. 定义一个事件类型

如果只是一个简单动作，直接一个 `data class` 或 `data object` 就够了：

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 3. 先在页面里注册接收事件

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

### 4. 在接收方进入活跃生命周期、订阅开始后发送

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

`onEvent(...)` / `collectEvent(...)` 的注册不等于底层订阅立即就绪，它们受生命周期和协程调度影响。普通事件没有 replay，尚未开始收集或处于非活跃状态时不会补收。下面场景中的发送和接收通常位于不同位置，应先注册接收方，在实际订阅活跃后由用户操作或业务流程发送；不能把示例中的相邻语句当作就绪屏障。后订阅者需要最近结果时显式使用 sticky，可恢复的页面状态使用 `StateFlow`。

看到这里就已经可以开始用了：1. 发送：`postEvent(...)`。2. 接收：`onEvent<T> { ... }`。3. 如果要限制在某个 Activity / Fragment / NavBackStackEntry 作用域内，再用 `owner` 版本。

<a id="先记住这-7-句话"></a>

## 使用约定

1. `postEvent(...)` / `emitEvent(...)` 发的是全局事件，整个 app 都能订阅。
2. `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` 发的是局部事件，只在这个 `owner` 对应的总线里流动。
3. 默认按“事件类型”分发；如果同一类型要拆成多个业务通道，就用 `eventChannel<T>("name")`。
4. `post*` 会立即尝试发送；`tryPost*` 会返回本次 `tryEmit` 有没有被底层流拒绝。
5. `tryPost*Result` 会返回总线层诊断结果，但不代表订阅回调已经处理完成。
6. `emit*` 遵循既定溢出策略；`SUSPEND` 且存在活跃订阅者时可背压，不保证普通事件留存、业务成功或进程死亡后恢复。
7. `onEvent(...)` 是推荐的 UI 最短路径；`collectEvent(flow)` 更适合你已经拿到了 `Flow`。

<a id="为什么文档常写-onevent示例里有时会写-collecteventeventflow"></a>

## 订阅入口与 Flow 组合

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

<a id="最常见的-4-个使用场景"></a>

## 常用场景

### 场景 1：全局广播，一个事件多个地方都能收到

适合：

1. 登录成功后让多个页面刷新
2. 某个后台任务完成后让多个观察者同步更新
3. 全局消息、埋点通知、刷新指令

```kotlin
data class SyncFinishedEvent(val successCount: Int)

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}

postEvent(SyncFinishedEvent(successCount = 3))
```

这类事件没有 owner 限制，谁订阅谁就能收到。

### 场景 2：Fragment 和 Activity 之间通信

适合：

1. Fragment 通知 Activity 刷新标题栏
2. Fragment 请求 Activity 执行导航、弹窗、权限流程
3. 某个页面树只想在当前 Activity 内部广播，不想影响全局

```kotlin
data object ReloadToolbarEvent

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}

requireActivity().postScopedEvent(ReloadToolbarEvent)
```

这里的 `requireActivity()` 不是监听者，而是“事件总线挂在哪个作用域上”的意思。
同理，你也可以把事件挂在 `NavBackStackEntry` 或自定义 `ViewModelStoreOwner` 上。

如果你更喜欢 owner 在前的写法，也可以这样：

```kotlin
viewLifecycleOwner.collectEvent(requireActivity().scopedEventFlow<ReloadToolbarEvent>()) {
    renderToolbar()
}

requireActivity().postScopedEvent(ReloadToolbarEvent)
```

### 场景 3：同样都是 `String`，但你不想到处写字符串通道名

适合：

1. `Toast`、`SnackBar`、导航命令这类“值类型简单，但语义不同”的事件
2. 同一个类型需要拆成多个明确的业务 channel
3. 你不想在业务代码里散落 `"ui.toast"`、`"page.reload"` 这类裸字符串

推荐写法：

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}

toastChannel.post("保存成功")
```

如果这个事件只想在当前 Activity 范围内传播：

```kotlin
val activityCommand = eventChannel<String>("activity.command")

viewLifecycleOwner.onEvent(from = requireActivity(), channel = activityCommand) { command ->
    handleActivityCommand(command)
}

activityCommand.postTo(requireActivity(), "reload")
```

你当然也可以继续直接写 `eventName`：

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<String>(eventName = "ui.toast")) {
    showToast(it)
}

postEvent(event = "保存成功", eventName = "ui.toast")
```

但对外公开或复用频繁的通道，优先推荐 `eventChannel(...)`，更清楚，也更不容易写错。

### 场景 4：ViewModel / Repository / Worker 通知 UI

适合：

1. 后台同步完成后提示 UI 刷新
2. Repository 完成某个动作后发通知给多个页面
3. Worker 执行完任务后，通知正在活跃收集的页面；恢复时必须读到的结果改用 sticky 或持久化业务状态

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
| 按配置进行挂起发送 | `emitEvent(...)` / `emitStickyEvent(...)` | 是 | 全局或 owner | 普通 / sticky 都可用 | `SUSPEND` 且存在活跃订阅者时可背压；丢弃策略仍生效 |
| 一次性读取 sticky 最新值 | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | 否 | 全局或 owner | 读取后清空 | 返回最新值；没有 replay 时返回 `null` |

<a id="发送-api-怎么选"></a>

## 发送 API

| API | 是否挂起 | 是否支持延迟 | 返回值 | 推荐场景 | 风险边界 |
| --- | --- | --- | --- | --- | --- |
| `postEvent(...)` | 否 | 是 | 无 | UI 层轻量通知 | best-effort，缓冲无法立刻接收时可能失败 |
| `tryPostEvent(...)` | 否 | 是 | `Boolean` | 想知道底层 `tryEmit` 是否被拒绝 | `delayMillis > 0` 时只代表延迟任务已安排 |
| `tryPostEventResult(...)` | 否 | 否 | `FlowBusPostResult` | 日志、测试断言、排查订阅数和溢出策略 | 不代表订阅回调已执行成功 |
| `emitEvent(...)` | 是 | 是 | 无 | 活跃广播需要按配置等待缓冲可用 | 协程调用；无人订阅普通事件不保存，丢弃策略仍生效 |
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

<a id="接收-api-怎么选"></a>

## 接收 API

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

<a id="postevent-和-emitevent-到底有什么区别"></a>

## 发送语义

| 维度 | `postEvent(...)` | `tryPostEvent(...)` | `tryPostEventResult(...)` | `emitEvent(...)` |
| --- | --- | --- | --- | --- |
| 是否挂起 | 否 | 否 | 否 | 是 |
| 写入方式 | 立即尝试写入 | 立即尝试写入 | 立即尝试写入并返回诊断结果 | 按既定溢出策略发送；`SUSPEND` 下可背压 |
| 返回信息 | 无 | `Boolean` | `FlowBusPostResult` | 无 |
| 适合场景 | UI 点击、轻量提示、刷新通知 | 需要知道 `tryEmit` 是否被拒绝 | 日志、单测、排查订阅数和溢出策略 | 需要按配置等待缓冲可用的活跃广播 |
| 不代表什么 | 不代表一定送达 | 不代表订阅者已处理 | 不代表业务处理成功 | 不保证一定送达或业务处理成功 |

`FlowBusPostResult` 包含：1. 事件名。2. 订阅数。3. sticky replay 数。4. 溢出策略。5. 结果分类。它只说明本次 `tryEmit` 有没有被底层流拒绝，不说明订阅回调已经处理成功。

`emitEvent(...)` 不会覆盖配置的溢出策略。`SUSPEND` 且存在活跃订阅者时可以通过挂起施加背压；`DROP_OLDEST` 仍可能丢旧值，`DROP_LATEST` 仍可能丢本次实时值。普通事件无人订阅时立即完成，不为未来订阅者保留；返回也不等于订阅者业务处理完成。必须恢复或可靠完成的操作应使用业务状态、数据库或持久化任务机制。

<a id="sticky-event-什么时候该用"></a>

## sticky 与 replay

sticky event 的核心语义是“事件发出后保留最近值，后来订阅的人也能先拿到这个值”。

sticky replay 与实时广播独立管理。`DROP_LATEST` 下，本次值可能未进入活跃订阅者的实时缓冲，但底层 `tryEmit` 返回 `true` 后仍可能成为 replay。例如慢订阅者实时收到 `[1, 2]`，新订阅者却先读到 `3`。这沿用现有契约，`emitStickyEvent(...)` 也不会改变溢出策略；replay 不表示所有订阅者已经接收或处理成功。

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

<a id="事件类型怎么设计最容易读也最不容易出错"></a>

## 事件建模

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

<a id="如果你只想记最短用法看这里"></a>

<a id="120-候选按通道配置"></a>
<a id="channel-configuration"></a>

## 按通道配置

从已发布的 1.2.0 起提供，1.1.0 不包含这些 API。通过 Maven Central 使用 `io.github.logan0817:flowbus-core:1.2.0` 或 `io.github.logan0817:flowbus:1.2.0`；变化与验收记录见[发布说明](../docs/release-notes-1.2.0.md)。

<a id="android-owner-configuration"></a>

### Android owner 配置

```kotlin
import com.logan.flowbus.configureEventChannel
import com.logan.flowbus.eventChannel
import com.logan.flowbus.onEvent
import com.logan.flowbus.emitTo
import com.logan.flowbus.core.FlowBusChannelConfig
import kotlinx.coroutines.channels.BufferOverflow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

val progressChannel = eventChannel<Int>("work.progress")
// Activity.onCreate 中、首次使用来源之前；owner 的获取必须在主线程。
configureEventChannel(progressChannel, FlowBusChannelConfig(
    normalBufferCapacity = 16,
    normalOverflowPolicy = BufferOverflow.SUSPEND
), owner = this)
onEvent(channel = progressChannel, from = this) { progress -> renderProgress(progress) }
// 后续用户操作、订阅已活跃时发送；renderProgress 由业务提供。
lifecycleScope.launch { progressChannel.emitTo(this@MyActivity, 10) }
```

省略 owner 配置全局来源；指定 owner 则配置该 owner 已有的 FlowEventBus。发送与订阅必须使用相同 owner。Activity 重建保留 ViewModelStore 时重复同有效配置幂等，store 真正清理后重新配置。默认 STARTED 阈值、取消和 sticky 重放行为保持不变；SUSPEND 不为 STOP 期间的普通事件离线保存。

显式 FlowEventBus 调用其 configureChannel(key, config)；注入的 core FlowBus/ScopedFlowBus/FlowBusScope 调用实例方法，随后使用 collectEventFrom 或 collectEventFromSuspending。接收方销毁只取消订阅，不关闭注入来源。容量继承、首次绑定/冲突、单通道删除、scope 重建及 inspector 边界详见 [core 配置与迁移说明](../flowbus-core/README.md#120-候选按通道配置)。

从 EventBus/LiveEventBus 迁移时，普通与 sticky 是独立流；显式选择 isSticky，不按同名合并。注册返回 Job 不保证就绪；父类型事件需相同 EventKey<Parent> 或稳定父类型 channel。全局通知优先 postEvent/onEvent，owner 内通信优先稳定 channel + 明确 owner，注入来源优先 collectEventFrom，已有 Flow 组合用 collectEvent，可恢复状态用 StateFlow。默认 Rethrow 仅由总线收集入口应用；通用 Flow 收集直接传播错误。

<a id="110-挂起回调与实例接入"></a>
<a id="suspend-callbacks"></a>

## 挂起回调与实例接入

下面 API 从 1.1.0 开始提供，旧入口保留。Kotlin、Coroutines、Lifecycle、minSdk 等基础要求不因这些入口提高，不新增 Compose、Hilt 或 Koin 依赖。

### 在回调中直接调用挂起函数

```kotlin
import com.logan.flowbus.onEventSuspending

viewLifecycleOwner.onEventSuspending<RefreshHomeEvent> { event ->
    viewModel.refreshSuspending(event.source)
}

viewLifecycleOwner.onEventSuspending<RefreshHomeEvent>(from = requireActivity()) { event ->
    viewModel.refreshSuspending(event.source)
}
```

`refreshSuspending` 由业务提供。全局和 owner 来源都支持 reified 类型或 Android `EventChannel<T>`；channel 写法为 `onEventSuspending(channel)`，owner 写法为 `onEventSuspending(from = owner, channel = channel)`。`from` 决定来源，接收方决定收集生命周期，两个示例是来源选择，不应为同一需求重复注册。

`LifecycleOwner` 入口默认在 `STARTED` 收集，支持 `dispatcher`、`minLifecycleState` 和 `isSticky`；须在主线程注册。`INITIALIZED` 会立即被拒绝。降到收集阈值以下会请求取消当前处理；同一次 repeatOnLifecycle 再次活跃时，等待旧回调结束后重新订阅。阻塞或 NonCancellable 工作不能被强制停止，也不回滚已发生的副作用；普通事件不补收，sticky 显式开启后可重放最近值。

`CoroutineScope.onEventSuspending` 提供相同的全局/owner、类型/channel 来源选择，由调用方 scope 管理订阅，不自动绑定 UI 生命周期；owner 来源须在主线程获取。它不提供 dispatcher 参数，回调沿用收集协程上下文。返回的 `Job` 可主动取消。自建 `FlowEventBus` 可以使用 `subscribeEventSuspending`：生命周期版本返回 `Job`，协程内的挂起版本由调用方管理收集。

这些总线入口沿用来源实例的类型校验、logger 和 errorHandler。单条订阅逐个等待回调，不采用 `collectLatest`；慢处理仍遵循原有缓冲/溢出策略。普通回调异常交给总线策略，默认重新抛出并终止订阅；取消直接传播，不作为错误上报。协作式挂起函数会随订阅取消，阻塞或不可取消工作不会自动变得可取消。

### 用构造注入保持实例隔离

先在应用的组装入口创建一次 bus，并把同一个实例传给发送方和收集方。可以由现有 DI 容器提供，不要求额外的 DI 框架模块。

```kotlin
import com.logan.flowbus.collectEventFromSuspending
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey

val refreshKey = eventKey<RefreshHomeEvent>("home.refresh")

class RefreshNotifier(private val bus: FlowBus) {
    fun notifyRefresh(source: String): Boolean =
        bus.post(refreshKey, RefreshHomeEvent(source))
}

val featureBus = FlowBus()
val notifier = RefreshNotifier(featureBus)

viewLifecycleOwner.collectEventFromSuspending(from = featureBus, key = refreshKey) { event ->
    viewModel.refreshSuspending(event.source)
}
// 订阅实际活跃后，在业务操作中调用：notifier.notifyRefresh("login")。
```

`collectEventFrom` 使用普通回调，`collectEventFromSuspending` 使用挂起回调；两者接收显式 `FlowBus`、`ScopedFlowBus` 或 `FlowBusScope` 与 core `EventKey<T>`。可用 `coreChannel.asEventKey()` 转换 core 通道。默认 `Dispatchers.Main.immediate`、`STARTED`、`isSticky=false`，复用传入实例的错误策略。注册不等于底层订阅就绪。

取消订阅或销毁接收方不会关闭来源。每次重新进入活跃状态都会从传入实例重新取 Flow：`ScopedFlowBus` 同名 store 重建后可在下一次活跃阶段取新流；活跃期间不自动切换旧 Flow。`FlowBusScope` 是固定句柄，关闭后重新活跃会取流失败，不会偷偷改用同名新句柄；来源取流失败直接传播，不作为回调异常交给 errorHandler。scope 的创建、替换与关闭由实例持有者负责。

注入实例不会与 `DefaultFlowBus` 或 Android 全局入口静默合并。发送到别的实例或 owner、事件 key 不同，都不会被这里订阅到。

### 收集已经组合好的任意 Flow

```kotlin
import com.logan.flowbus.collectEventSuspending

viewLifecycleOwner.collectEventSuspending(eventFlow<RefreshHomeEvent>()) { event ->
    viewModel.refreshSuspending(event.source)
}
```

`collectEventSuspending` 是通用 Flow 工具，按生命周期顺序执行挂起回调；上游和回调异常直接传播，不自动套用 FlowBus logger/errorHandler。需要实例策略时选择 `collectEventFromSuspending`。需要取消上一条处理时使用原生 Flow 的 `collectLatest` 并明确业务取消含义，不把它作为这些新入口的默认行为。

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

<a id="什么时候再去看-flowbus-core"></a>

## Core 模块边界

当你有这些需求时，再看 core 文档就够了：

1. 需要自己管理 `FlowBus` 实例，而不是使用 Android 默认入口
2. 需要多个 bus 实例隔离
3. 需要非 Android 环境复用同一套事件模型
4. 需要 `FlowBusScope`、`EventKey`、`DefaultFlowBus` 这些更底层能力

文档入口：
1. GitHub 地址：[flowbus-core README](../flowbus-core/README.md)

## 仓库链接

1. 仓库主页：[FlowBus](https://github.com/logan0817/FlowBus)
2. Core 模块：[flowbus-core](https://github.com/logan0817/FlowBus/tree/main/flowbus-core)
3. 示例应用模块：[app](https://github.com/logan0817/FlowBus/tree/main/app)
