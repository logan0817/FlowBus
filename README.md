英文文档 [English Document](./README_EN.md)

# FlowBus

FlowBus 是一个基于 Kotlin Coroutines 和 SharedFlow 的 Android 事件总线。

它提供：
- 全局事件
- Activity / Fragment 局部事件
- 生命周期感知订阅
- Sticky 事件
- 延迟发送
- 指定线程处理

## 引入

```gradle
repositories {
    mavenCentral()
}
```

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.3")
```

## 先记住两个规则

### 1. 发送到哪里，看 `owner`

- 不传 `owner`：发送到全局总线
- 传 `owner`：发送到该 `Activity` / `Fragment` 自己持有的局部总线

```kotlin
postEvent(GlobalEvent("refresh app"))
postEventTo(owner = requireActivity(), event = ActivityEvent("refresh activity"))
postEventTo(owner = this@DemoFragment, event = FragmentEvent("refresh fragment"))
```

### 2. 谁来管理订阅生命周期，看接收者

- `subscribeEvent(...)` 的接收者是谁，就由谁管理生命周期
- `owner = ...` 表示你要从哪个总线作用域收消息

```kotlin
// 用 Fragment view 的生命周期，订阅 Activity 作用域事件
viewLifecycleOwner.subscribeEvent<ActivityEvent>(owner = requireActivity()) {
    render(it)
}
```

## 定义事件

建议使用明确的事件类，不要长期复用 `String` / `Int`。

```kotlin
data class GlobalEvent(val message: String)
data class ActivityEvent(val message: String)
data class FragmentEvent(val message: String)
```

## 常用写法

### 发送事件

```kotlin
// 全局事件
postEvent(GlobalEvent("refresh app"))

// Activity 作用域事件
postEventTo(owner = requireActivity(), event = ActivityEvent("refresh activity"))

// Fragment 作用域事件
postEventTo(owner = this@DemoFragment, event = FragmentEvent("refresh fragment"))

// 延迟发送
postEvent(event = GlobalEvent("delay"), delayMillis = 1_000)
```

### 发送 Sticky 事件

```kotlin
postStickyEvent(GlobalEvent("latest global state"))
postStickyEventTo(owner = requireActivity(), event = ActivityEvent("latest activity state"))
```

### 在 Activity 中订阅

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 订阅全局事件
        subscribeEvent<GlobalEvent> {
            renderGlobal(it)
        }

        // 订阅当前 Activity 作用域事件
        subscribeEvent<ActivityEvent>(owner = this) {
            renderActivity(it)
        }
    }
}
```

### 在 Fragment 中订阅

如果回调里会访问 View，请优先使用 `viewLifecycleOwner`。

```kotlin
class DemoFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 订阅全局事件
        viewLifecycleOwner.subscribeEvent<GlobalEvent> {
            renderGlobal(it)
        }

        // 订阅 Activity 作用域事件
        viewLifecycleOwner.subscribeEvent<ActivityEvent>(owner = requireActivity()) {
            renderActivity(it)
        }

        // 订阅当前 Fragment 作用域事件
        viewLifecycleOwner.subscribeEvent<FragmentEvent>(owner = this@DemoFragment) {
            renderFragment(it)
        }
    }
}
```

### 在 ViewModel / CoroutineScope 中订阅

```kotlin
class DemoViewModel : ViewModel() {

    init {
        // 全局事件
        viewModelScope.subscribeEvent<GlobalEvent> {
            handleGlobal(it)
        }

        // Activity / Fragment 局部事件
        viewModelScope.subscribeEventFrom<ActivityEvent>(owner = activityOwner) {
            handleActivity(it)
        }
    }
}
```

## 线程与生命周期

```kotlin
subscribeEvent<GlobalEvent>(dispatcher = Dispatchers.IO) {
    saveToDisk(it)
}

subscribeEvent<GlobalEvent>(
    minLifecycleState = Lifecycle.State.RESUMED
) {
    render(it)
}
```

## Sticky 事件清理

```kotlin

/**
 * 移除指定的粘性事件流
 * 仅在你明确拥有该 sticky 作用域时再调用；
 * 不要在页面销毁时顺手清理全局 sticky 事件。
 */
removeStickyEvent<GlobalEvent>()
removeStickyEvent<ActivityEvent>(owner = requireActivity())
removeStickyEvent<FragmentEvent>(owner = this@TestFragment)

/**
 * 清除本地粘性事件类型 T 的缓存，但保留 Flow 实例。
 * 同样只建议清理你明确拥有的 sticky 作用域。
 */
clearStickyEvent<GlobalEvent>()
clearStickyEvent<ActivityEvent>(owner = requireActivity())
clearStickyEvent<ActivityEvent>(owner = this@TestFragment)
```

- `clearStickyEvent`：清空最后一次缓存，但保留 Sticky Flow
- `removeStickyEvent`：彻底移除 Sticky Flow

## 使用建议

- Fragment 回调里操作 View 时，优先使用 `viewLifecycleOwner`
- 普通事件适合页面通信，不适合做“绝对不能丢消息”的任务队列
- Sticky 事件只保留最新一条
- 重任务请切到 `Dispatchers.IO` 或 `Dispatchers.Default`
- 旧的 scoped API 仍可用，但已不推荐；生产代码建议改用显式 `owner` 写法

## Demo

<img src="GIF.gif" width="350" />

> 你也可以直接下载 [演示 App](https://raw.githubusercontent.com/logan0817/FlowBus/master/app/release/app-release.apk) 查看效果

## License

```text
MIT License
```
