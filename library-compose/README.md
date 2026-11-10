[English](README_EN.md) · [项目首页](../README.md)

# FlowBus Compose

`flowbus-compose` 是可选的 Jetpack Compose 适配坐标，通过 `FlowBusEffect` 在明确 owner 的活跃生命周期内收集事件。它依赖 core、Compose runtime 和 Lifecycle，不包含 Material、Compose UI 或 Navigation；现有 `flowbus-core` 与 `flowbus` 不会传递依赖它。

## 开发状态与候选安装

当前模块属于 **1.3.0-SNAPSHOT 本地开发候选，尚未发布**。公开正式版本仍为 1.2.0，Maven Central 的 1.2.0 不包含 `flowbus-compose` 坐标，也不包含订阅建立通知 API。

先按[本地候选安装流程](../CONTRIBUTING.md#candidate-builds)生成并配置隔离 Maven 仓库，再在消费者中声明：

```kotlin
implementation("io.github.logan0817:flowbus-compose:1.3.0-SNAPSHOT")
```

候选仓库应对 `io.github.logan0817` 使用 `exclusiveContent`，确保三个坐标来自同一批候选，缺失时不会回退到远端旧制品。不要把上述坐标当作已公开发布的安装方式。

## 推荐入口

```kotlin
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import com.logan.flowbus.compose.FlowBusEffect
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey

data class RefreshEvent(val source: String)

private val refreshKey = eventKey<RefreshEvent>("home.refresh")

@Composable
fun RefreshEvents(
    bus: FlowBus,
    owner: LifecycleOwner,
    onRefresh: suspend (RefreshEvent) -> Unit
) {
    FlowBusEffect(from = bus, key = refreshKey, owner = owner) { event ->
        onRefresh(event)
    }
}
```

来源由调用方持有和注入；owner 可以是 Activity、Fragment 的 viewLifecycleOwner 或 Navigation 返回栈条目。模块不自动查找全局总线，也不自动获取导航 owner。

默认 `minState = STARTED`、`isSticky = false`。只应由当前交互页面处理的副作用可显式选择 `RESUMED`；`INITIALIZED` 会在组合时被拒绝。是否仍处于 Composition 不等于 owner 处于活跃生命周期。

## 订阅、回调与取消

| 变化或操作 | 行为 |
| --- | --- |
| 来源、key 名称/类型、owner、最低状态或 sticky 选择改变 | 请求取消旧 effect，并为新参数建立订阅 |
| 仅更新 `onReceived` / `onSubscribed` | 不重建订阅，后续调用使用最新回调 |
| 当前事件回调已经开始 | 继续执行本次已捕获的回调；更新不会替换在途调用 |
| owner 低于最低状态、销毁或离开 Composition | 请求取消订阅和在途协作式回调，不关闭来源 bus |
| owner 再次活跃 | 重新从来源取得流并建立新订阅 |

单个订阅按顺序执行挂起回调，不默认采用 `collectLatest`。事件回调沿用来源的 logger / errorHandler；取消不当作普通业务异常处理。阻塞或 `NonCancellable` 工作不能被强制停止；同一生命周期收集循环需要等待旧回调结束才能重启，切换参数也不保证已产生的副作用能被撤回。

## 订阅建立通知

可传入可选的 `onSubscribed: (() -> Unit)?`。通知对应**这一次具体的底层订阅已登记**；每次生命周期重新活跃并建立新订阅时再次调用。只更新通知回调本身不会重建订阅，也不会补发已经过去的建立通知。

通知不表示历史 sticky replay 已处理、业务处理完成或未来持续活跃。它在收集协程上下文执行，应只完成一个短通知，由独立生产协程按既定缓冲策略发送。不要阻塞等待或在通知中等待同一零容量通道的发送完成。通知自身抛出的异常直接使本次收集失败，不伪装成某条事件的处理错误。

一次性 `CompletableDeferred` 可用于首轮订阅握手，但不能代表后续所有生命周期活跃阶段。普通事件无人订阅时不留存；即使已通知，零容量 `tryPost` 仍可能拒绝，DROP 策略仍可能丢弃数据。发送完成不代表业务完成。

需要 sticky 时显式选择 `isSticky = true`。普通与 sticky 独立分流，生命周期重进可能再次回放。可恢复页面状态和不可遗漏的业务结果使用 `StateFlow`、业务状态或持久化机制。

## 构建组合与验证入口

模块沿用 Kotlin 1.9.25、Compose compiler 1.5.15、runtime 1.5.4、Lifecycle 2.6.2、Coroutines 1.8.1、minSdk 21 与 Java 8 字节码目标。消费者可以选择经过独立验证的较新 runtime；直接声明不等于最终解析版本，也不代表任意工具链组合兼容。

[Compose 消费者](../compatibility/compose-consumer/README.md)通过候选 Maven 坐标和 release R8 调用此模块，复用 effect 生命周期、替换、取消、错误策略与非协作回调验证，并检查订阅建立通知及回调更新。其默认 runtime/ui 1.5.4 与可选 1.7.8 的依赖解析、构建和设备证据必须分开记录。

完整门禁见[发布清单](../docs/release-checklist.md)。编译、R8 和 driver 单测不能替代 API 21/35 设备运行；历史 1.2.0 fixture 的通过结果也不等于本模块候选已通过。
