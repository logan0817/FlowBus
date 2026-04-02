英文文档 [English Document](./README_EN.md)

# library-android

`library-android` 是 FlowBus 的 Android 适配模块。

Gradle 模块目录名是 `library-android`，对外发布的 Android 依赖坐标是：

```gradle
implementation("io.github.logan0817:flowbus:<latest-version>")
```

它构建在 `flowbus-core` 之上，也是 Android 项目的推荐入口。

## 适合使用这个模块的场景

- Android 应用开发
- 需要全局事件或基于 `ViewModelStoreOwner` 的局部事件
- 更偏好 `eventFlow<T>()` 这种 Flow-first API
- 需要 `collectEvent(...)` 这种生命周期安全收集方式

## 在使用前，先理解 FlowBus 的定位

FlowBus 更适合“事件广播”，例如跨页面、跨模块、跨层的通知类事件，
以及 UI、ViewModel、Repository、Worker 之间的异步事件传播。

不适合的场景：
- 页面内部状态管理：优先 `StateFlow`
- 明确的一对一调用：优先直接方法调用 / use case
- 严格的请求-响应配对：优先返回值、挂起函数或专用通道

如果你需要平台无关的底层能力，或要基于 FlowBus 构建自己的适配层，
请查看 `flowbus-core`：

- 本地文档：[`../flowbus-core/README.md`](../flowbus-core/README.md)
- GitHub 地址：[flowbus-core README](https://github.com/logan0817/FlowBus/blob/master/flowbus-core/README.md)

## 推荐 Android API

- `postEvent(...)` / `postStickyEvent(...)`：发送全局事件，best-effort
- `emitEvent(...)` / `emitStickyEvent(...)`：发送全局事件，挂起直到成功写入
- `postEventTo(owner = ...)` / `postStickyEventTo(owner = ...)`：发送局部事件，best-effort
- `emitEventTo(owner = ...)` / `emitStickyEventTo(owner = ...)`：发送局部事件，挂起直到成功写入
- `eventFlow<T>()` / `stickyEventFlow<T>()`：以 `Flow` 形式读取全局事件
- `eventChannel<T>("...")`：声明一个可复用的命名事件句柄
- `channel.post(...)` / `channel.flow()`：围绕命名句柄组织全局事件
- `channel.postTo(owner, ...)` / `channel.flowFrom(owner)`：围绕命名句柄组织局部事件
- `owner.eventFlow<T>()` / `owner.stickyEventFlow<T>()`：更直觉的 owner-centric 局部事件读取方式
- `eventFlowFrom<T>(owner = ...)` / `stickyEventFlowFrom<T>(owner = ...)`：以 `Flow` 形式读取局部事件
- `LifecycleOwner.collectEvent(flow) { ... }`：生命周期安全收集
- `LifecycleOwner.onEvent(channel)` / `LifecycleOwner.onEvent(from = ..., channel = ...)`：基于命名句柄直接订阅
- `LifecycleOwner.onEvent<T>(from = ...)` / `CoroutineScope.onEvent<T>(from = ...)`：更短的直接订阅别名
- `LifecycleOwner.subscribeEvent(owner = ...)` / `CoroutineScope.subscribeEventFrom(owner = ...)`：直接订阅局部事件
- `removeStickyEvent<T>()` / `clearStickyEvent<T>()`：移除或清空全局 sticky event
- `removeStickyEvent<T>(owner)` / `clearStickyEvent<T>(owner)`：移除或清空局部 sticky event
- `FlowBusAndroid.configure(...)`：首次使用前覆盖默认 core 配置

## 推荐使用规则

- FlowBus 默认按事件类型分发；同一类型就是同一个事件通道
- Android 层现在也支持显式 `eventName`，因此同一类型可以声明多个语义清晰的 channel
- 同一类型的多个订阅者都会收到事件；它是广播模型，不是独占消费队列
- 全局事件用 `postEvent(...)` / `eventFlow<T>()`
- 局部作用域事件用 `postEventTo(owner, ...)` / `eventFlowFrom<T>(owner = ...)`
- UI 层优先配合 `collectEvent(...)` 使用，让收集过程跟随生命周期
- 需要严格保证送达时，使用 `emitEvent(...)` 或 `emitEventTo(...)`
- `postEvent(...)` 是 best-effort 发送，底层缓冲无法立即接收时可能丢失
- sticky event 只适合“后来的订阅者也需要读到最近一次值”的场景

## 事件建模建议

优先给业务定义明确的事件类型，而不是长期直接发送 `String`、`Int` 等基础类型。
常见做法是单个事件用 `data class`，同一业务域的多个动作用 `sealed interface` / `sealed class`。

例如：

```kotlin
sealed interface MainEvent {
    data object Refresh : MainEvent
    data class OpenDetail(val id: Long) : MainEvent
    data class ShowToast(val message: String) : MainEvent
}

postEvent<MainEvent>(MainEvent.Refresh)
postEvent<MainEvent>(MainEvent.OpenDetail(id = 42L))
postEvent<MainEvent>(MainEvent.ShowToast(message = "success"))
```

## 最简洁写法

```kotlin
requireActivity().postEvent(ActivityEvent.Reload)
viewLifecycleOwner.collectEvent(requireActivity().eventFlow<ActivityEvent>()) {
    renderActivity(it)
}

viewLifecycleOwner.onEvent<GlobalEvent> {
    renderGlobal(it)
}

postEvent(event = "toast", eventName = "ui.toast")
viewLifecycleOwner.collectEvent(eventFlow<String>(eventName = "ui.toast")) {
    showToast(it)
}
```

## 命名事件句柄写法

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
val activityReload = eventChannel<ActivityEvent>("activity.reload")

toastChannel.post("Saved")
activityReload.postTo(requireActivity(), ActivityEvent.Reload)

viewLifecycleOwner.collectEvent(toastChannel.flow()) {
    showToast(it)
}

viewLifecycleOwner.onEvent(from = requireActivity(), channel = activityReload) {
    renderActivity(it)
}
```

## Fragment 示例

```kotlin
class DemoFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postEvent(GlobalEvent.Refresh)
        postEventTo(owner = requireActivity(), event = ActivityEvent.Reload)

        viewLifecycleOwner.collectEvent(eventFlow<GlobalEvent>()) {
            renderGlobal(it)
        }

        viewLifecycleOwner.collectEvent(
            eventFlowFrom<ActivityEvent>(owner = requireActivity())
        ) {
            renderActivity(it)
        }
    }
}
```

## 可选配置

如果你希望调整底层 core 配置，请在第一次调用 `postEvent(...)`、
`emitEvent(...)`、`eventFlow(...)` 或其他总线入口之前执行 `FlowBusAndroid.configure(...)`：

```kotlin
FlowBusAndroid.configure(
    FlowBusConfig(
        logger = MyFlowBusLogger,
        errorHandler = FlowBusErrorHandler.Rethrow
    )
)
```

## flowbus-core 提供了什么

`library-android` 底层依赖 `flowbus-core`，后者提供：

- `FlowBus`
- `EventKey`
- sticky event 支持
- 命名作用域与可关闭作用域
- logger / error handler / buffer 配置

如果你需要直接使用这些更底层的 API，请查看：

- 本地文档：[`../flowbus-core/README.md`](../flowbus-core/README.md)
- GitHub 地址：[flowbus-core README](https://github.com/logan0817/FlowBus/blob/master/flowbus-core/README.md)

## 仓库链接

- 仓库主页：[FlowBus](https://github.com/logan0817/FlowBus)
- Core 模块：[flowbus-core](https://github.com/logan0817/FlowBus/tree/master/flowbus-core)
- Demo 模块：[app](https://github.com/logan0817/FlowBus/tree/master/app)

