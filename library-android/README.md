英文文档 [English Document](./README_EN.md)

# library-android

`library-android` 是 FlowBus 的 Android 适配模块。

Gradle 模块目录名是 `library-android`，对外发布的依赖坐标是：

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.5")  // 替换为上方徽章显示的最新版本
```

如果你是 Android 项目接入 FlowBus，通常就从这个模块开始，不需要先研究 `flowbus-core`。

## 它到底是做什么的

FlowBus 更适合处理“事件广播”问题，也就是：

- 某个地方发出一个通知，多个地方都可能关心
- 发送方和接收方不想直接互相持有引用
- 事件天然是异步的，更适合用 `Flow` 收
- 你想按页面、Activity、NavBackStackEntry 或全局维度组织事件

Android 项目里最常见的使用方式是：

- 页面 A 通知页面 B 刷新
- Fragment 通知 Activity 执行某个 UI 动作
- ViewModel / Repository / Worker 通知 UI 弹 Toast、刷新列表、重新拉数据
- 某个作用域里广播一次事件，让多个观察者同时响应

它不适合下面这些场景：

- 页面内部状态管理：优先 `StateFlow`
- 明确的一对一调用：优先直接方法调用 / use case
- 严格请求-响应：优先返回值、挂起函数、专用通道
- 长期共享状态：优先状态容器，而不是事件总线

## 先记住这 5 句话

1. `postEvent(...)` / `emitEvent(...)` 发的是全局事件，整个 app 都能订阅。
2. `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` 发的是局部事件，只在这个 `owner` 对应的总线里流动。
3. 默认按“事件类型”分发；如果同一类型要拆成多个业务通道，就用 `eventChannel<T>("name")`。
4. `post*` 是立即尝试发送，`tryPost*` 会额外返回是否已被当前总线接收，`emit*` 是挂起直到成功写入。
5. `onEvent(...)` 是推荐的 UI 最短路径；`collectEvent(flow)` 更适合你已经拿到了 `Flow`。

## 为什么文档常写 `onEvent(...)`，demo 里有时会写 `collectEvent(eventFlow(...))`

这两个写法不是对立关系，而是同一套能力的两个入口层级：

- `onEvent(...)`：直接帮你订阅 FlowBus 事件，适合大多数 UI 页面
- `eventFlow(...)` / `scopedEventFlow(...)` / `channel.flow()`：先把事件拿成 `Flow`
- `collectEvent(flow)`：把这个 `Flow` 绑定到 `LifecycleOwner` 安全收集

所以最简单的全局事件，下面两种写法可以理解成“效果等价，只是展开层级不同”：

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

- 页面里只是想“收一个事件”，优先 `onEvent(...)`
- 你还想自己做 `map`、`filter`、`debounce`、`combine`，就用 `eventFlow(...) + collectEvent(...)`
- demo 同时出现这两种写法，是为了把“最短路径”和“先拿 Flow 再组合”都演示出来，不代表它们的事件来源不同

## 3 分钟上手

### 1. 添加依赖
[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.5")  // 替换为上方徽章显示的最新版本
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

看到这里就已经可以开始用了：

- 发送：`postEvent(...)`
- 接收：`onEvent<T> { ... }`
- 如果要限制在某个 Activity / Fragment / NavBackStackEntry 作用域内，再用 `owner` 版本

## 最常见的 4 个使用场景

### 场景 1：全局广播，一个事件多个地方都能收到

适合：

- 登录成功后让多个页面刷新
- 某个后台任务完成后让多个观察者同步更新
- 全局消息、埋点通知、刷新指令

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

- Fragment 通知 Activity 刷新标题栏
- Fragment 请求 Activity 执行导航、弹窗、权限流程
- 某个页面树只想在当前 Activity 内部广播，不想影响全局

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

- `Toast`、`SnackBar`、导航命令这类“值类型简单，但语义不同”的事件
- 同一个类型需要拆成多个明确的业务 channel
- 你不想在业务代码里散落 `"ui.toast"`、`"page.reload"` 这类裸字符串

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

- 后台同步完成后提示 UI 刷新
- Repository 完成某个动作后发通知给多个页面
- Worker 执行完任务后，页面恢复时自动拿到结果

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

| 需求 | 推荐 API |
| --- | --- |
| 全局发送 | `postEvent(...)` |
| 需要返回是否接收 | `tryPostEvent(...)` |
| 发到 owner 作用域 | `postEventTo(owner, ...)` |
| 命名通道 | `eventChannel<T>("name")` + `channel.post(...)` |
| 最短监听 | `onEvent(...)` |
| 先拿 Flow 再组合 | `eventFlow(...)` + `collectEvent(...)` |
| 保证写入成功 | `emitEvent(...)` |

## 发送 API 怎么选

### 发到全局

最常用：

```kotlin
postEvent(MyEvent(...))
```

如果你想知道这次 best-effort 发送是否被当前总线接收：

```kotlin
val accepted = tryPostEvent(MyEvent(...))
```

需要挂起直到真正写入成功：

```kotlin
viewModelScope.launch {
    emitEvent(MyEvent(...))
}
```

### 发到指定 owner

这两种写法等价，按你更顺手的风格选一种：

```kotlin
postEventTo(owner = requireActivity(), event = ReloadToolbarEvent)
```

```kotlin
requireActivity().postScopedEvent(ReloadToolbarEvent)
```

### 发到命名 channel

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("保存成功")
toastChannel.postTo(requireActivity(), "保存成功")
```

## 接收 API 怎么选

### 只想一行开始监听

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    render(event)
}
```

或者监听命名 channel：

```kotlin
viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

### 想先拿到 Flow，再自己做组合、过滤、转换

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

简单理解：

- `onEvent(...)`：最短、最直接，适合大多数 UI 监听
- `eventFlow(...)`：适合你还要 `map`、`filter`、`debounce` 或组合多个流
- `collectEvent(flow)`：把任意 `Flow` 绑定到 `LifecycleOwner` 安全收集，不只限于 FlowBus

## `postEvent` 和 `emitEvent` 到底有什么区别

### `postEvent(...)`

特点：

- 非挂起
- 调用最轻便
- best-effort
- 当底层缓冲无法立刻接收时，可能发送失败

适合：

- UI 层一次性通知
- 可以容忍极端情况下丢失的轻量事件
- 你就是想快速广播一下

### `emitEvent(...)`

特点：

- 挂起函数
- 遵循背压
- 会等待直到成功写入

适合：

- 你不希望事件悄悄丢掉
- 发送时机比“调用手感”更重要
- Repository / Worker / ViewModel 内的关键通知

经验上可以这样选：

- UI 点击、轻量提示：先用 `postEvent`
- 关键业务链路、必须送达：用 `emitEvent`

## sticky event 什么时候该用

sticky event 的含义是：

- 事件发出去后，会保留最近一次值
- 后来才开始订阅的人，也能立刻拿到最近一次值

适合：

- “当前配置已准备好”
- “最近一次会话信息”
- “初始化结果”
- “页面恢复时需要读到最近一次成功状态”

不适合：

- Toast
- 导航
- 一次性点击事件
- 只该消费一次的瞬时动作

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

简单理解：

- `clearStickyEvent`：清 replay 缓存，但保留通道
- `removeStickyEvent`：把这个 sticky 通道整体移除

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

## 什么时候再去看 `flowbus-core`

当你有这些需求时，再看 core 文档就够了：

- 需要自己管理 `FlowBus` 实例，而不是使用 Android 默认入口
- 需要多个 bus 实例隔离
- 需要非 Android 环境复用同一套事件模型
- 需要 `FlowBusScope`、`EventKey`、`DefaultFlowBus` 这些更底层能力

文档入口：
- GitHub 地址：[flowbus-core README](https://github.com/logan0817/flowbus-core/blob/main/README.md)

## 仓库链接

- 仓库主页：[FlowBus](https://github.com/logan0817/FlowBus)
- Core 模块：[flowbus-core](https://github.com/logan0817/flowbus-core)
- Demo 模块：[app](https://github.com/logan0817/FlowBus/tree/master/app)
