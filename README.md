英文文档 [English Document](./README_EN.md)

# FlowBus

FlowBus 是一个基于 Kotlin Coroutines / Flow 的 Flow-first 事件框架，用于处理 Android 和 Kotlin 项目里的事件广播、命名通道、作用域事件和 sticky 最近值。

它解决的是“一个地方发通知，多个地方可能响应”的问题；它不是状态管理框架，也不是直接函数调用、请求-响应或可靠队列的替代品。

## 功能点

| 能力 | 用法入口 | 适合场景 |
| --- | --- | --- |
| 全局事件广播 | `postEvent(...)` / `onEvent<T> { ... }` | 登录成功、后台同步完成、多个页面刷新 |
| owner 局部事件 | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | Fragment 通知 Activity、NavBackStackEntry 内部通信 |
| 命名通道 | `eventChannel<T>("name")` | 同样是 `String`，但要区分 Toast、SnackBar、导航命令 |
| Flow-first 订阅 | `eventFlow<T>()` / `collectEvent(flow)` | 先拿到 `Flow`，再做 `map`、`filter`、`debounce`、`combine` |
| sticky 最近值 | `postStickyEvent(...)` / `onEvent<T>(isSticky = true)` | 页面恢复后需要先拿最近一次初始化结果 |
| 发送诊断 | `tryPostEventResult(...)` | 排查订阅数、溢出策略、底层 `tryEmit` 是否拒绝 |
| core 多实例和 scope | `flowbus-core` / `DefaultFlowBus.openScope(...)` | 非 Android、Repository / Worker / Session 级别隔离 |

## 直接安装

Android 项目优先使用 `flowbus`：

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.1.0")
```

纯 Kotlin / Coroutines / 非 Android 场景使用 `flowbus-core`：

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.1.0")
```

模块选择：

1. Android 页面、ViewModel、Fragment、Activity 通信：先看 [`library-android/README.md`](./library-android/README.md)。
2. 非 Android、多实例、显式 scope 生命周期：再看 [`flowbus-core/README.md`](./flowbus-core/README.md)。

## 5 分钟上手

### 1. 定义事件

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 2. 先注册接收事件

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    viewModel.refreshFrom(event.source)
}
```

### 3. 在接收方进入活跃生命周期、订阅开始后发送

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

`onEvent(...)` 注册不等于底层订阅立即就绪；它受生命周期和协程调度影响。普通事件没有 replay，接收方未开始收集或处于非活跃状态时不会补收。后订阅读取最近结果应显式使用 sticky；可恢复的页面状态应使用 `StateFlow`。

这就是最短 Android 接入路径。先记住：发送用 `postEvent(...)`，页面接收用 `onEvent<T> { ... }`。

## 常用场景

### 场景 1：全局刷新通知

适合登录成功、配置更新、后台同步完成后通知多个页面。

```kotlin
data class SyncFinishedEvent(val successCount: Int)

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}

postEvent(SyncFinishedEvent(successCount = 3))
```

### 场景 2：Fragment 通知 Activity

适合刷新标题栏、请求导航、当前 Activity 内部页面通信。

```kotlin
data object ReloadToolbarEvent

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}

requireActivity().postScopedEvent(ReloadToolbarEvent)
```

这里的 `requireActivity()` 表示事件挂在哪个 owner 作用域，不表示监听者。

### 场景 3：同一类型多个命名通道

适合同样都是 `String`，但业务语义不同的事件。

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}

toastChannel.post("保存成功")
```

### 场景 4：非 Android 或底层 scope 控制

适合 Repository / Worker / Session 级别隔离，或需要多个 `FlowBus` 实例。

```kotlin
val syncScope = DefaultFlowBus.openScope("sync-task", closeWhen = scope)

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}

syncScope.post(SyncProgress(percent = 10))
```

示例直接收集普通 Flow，使用 `CoroutineStart.UNDISPATCHED` 先执行收集再发送；上游挂起、`flowOn` 或生命周期门控等组合仍需另行确认实际订阅就绪。

## API 选择速记

| 场景 | 推荐入口 | 是否挂起 | sticky replay | 结果边界 |
| --- | --- | --- | --- | --- |
| 全局轻量广播 | `postEvent(...)` | 否 | 否 | best-effort，极端情况下可能发送失败 |
| 只想知道 `tryEmit` 有没有被拒绝 | `tryPostEvent(...)` | 否 | 否 | 返回 `Boolean`，不代表业务处理成功 |
| 需要发送诊断结果 | `tryPostEventResult(...)` | 否 | 普通 / sticky 都可用 | 返回订阅数、sticky replay 数、溢出策略和结果分类 |
| 按配置进行挂起发送 | `emitEvent(...)` | 是 | 普通 / sticky 都可用 | `SUSPEND` 且存在活跃订阅者时可背压；仍遵循既定溢出策略 |
| 发送到某个 owner | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | 否 | 否 | 事件只进入该 owner 对应的局部总线 |
| 命名通道 | `eventChannel<T>("name")` + `channel.post(...)` | 否 | 普通 / sticky 都可用 | 适合长期复用的业务通道 |
| 最短一行监听 | `onEvent(...)` | 否 | 由 `isSticky` 决定 | 自动绑定 `LifecycleOwner` |
| 先拿到 `Flow` 自己组合 | `eventFlow<T>()` / `owner.scopedEventFlow<T>()` | 否 | 由 `isSticky` 决定 | 适合 `map`、`filter`、`debounce` 等操作 |
| 生命周期安全收集任意 `Flow` | `collectEvent(flow) { ... }` | 否 | 取决于传入的 `Flow` | 不只限于 FlowBus |
| 非 Android / 多实例 / scope 生命周期 | `flowbus-core` | 取决于 API | 普通 / sticky 都可用 | 自行管理 `FlowBus`、scope 和协程生命周期 |

`onEvent(...)` 和 `collectEvent(eventFlow(...))` 不是两套体系。前者是 UI 最短写法，后者是先拿 `Flow` 再交给生命周期安全收集；它们监听的是同一条事件流。

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    render(event)
}

viewLifecycleOwner.collectEvent(eventFlow<RefreshHomeEvent>()) { event ->
    render(event)
}
```

## 边界说明

1. 页面内部长期状态优先用 `StateFlow`，不要把 FlowBus 当状态容器。
2. 明确的一对一调用优先直接方法调用、use case 或挂起函数。
3. `tryPost*Result.accepted = true` 只说明底层 `tryEmit` 没有拒绝，不代表订阅者已经处理完成。
4. `emit*` 仍遵循既定溢出策略；`SUSPEND` 且存在活跃订阅者时可通过挂起施加背压。普通事件无人订阅时不保存，发送完成不等于业务处理成功。必须恢复或可靠完成的操作使用业务状态、数据库或持久化任务机制。
5. sticky replay 与实时广播独立管理；`DROP_LATEST` 下新值即使未送入活跃订阅者的缓冲，也可能成为后订阅者的 replay。sticky 不适合替代长期状态管理。
6. `consumeStickyLatestEvent(...)` 只读取并清空当前 sticky replay，不会阻止其他线程后续写入新的 sticky 值。
7. 日志里的 `eventName`、`scopeName` 不要包含手机号、token、订单号、用户 ID 等敏感信息。


## 1.2.0 候选：按通道配置

1.2.0 当前为本地候选，尚未发布到 Maven Central。上面的 1.1.0 安装示例仍用于正式版本。体验本节 API 时，先从仓库根目录将候选发布到隔离目录：

```bash
./gradlew publishToMavenLocal "-Dmaven.repo.local=$PWD/build/candidate-maven"
```

消费者将该目录作为 `maven { url = uri("/absolute/path/to/FlowBus/build/candidate-maven") }` 仓库，并选择 `io.github.logan0817:flowbus-core:1.2.0`（Android 使用 `io.github.logan0817:flowbus:1.2.0`）。不要把本地候选的成功构建当作正式发布或设备验收通过。实施状态见[1.2.0 发布说明](docs/release-notes-1.2.0.md)。

详见 [core 通道配置](flowbus-core/README.md#120-候选按通道配置)、[Android owner 配置](library-android/README.md#android-owner-配置)和 [JVM 性能基准](benchmarks/jvm/README.md)。

## 1.1.0 接入能力

1.1.0 新增挂起回调和显式实例的 Android 收集入口，旧 API 保留。下面入口从 1.1.0 开始提供，基础依赖和 minSdk 目标保持不变。

| 需求 | API 入口 | 行为 |
| --- | --- | --- |
| core 直接执行挂起函数 | `collectSuspending` / `collectStickySuspending` | 单条订阅串行处理，复用实例的错误策略和 dispatcher |
| Android 全局或 owner 挂起订阅 | `onEventSuspending` | 提供 LifecycleOwner / CoroutineScope、reified / channel 入口 |
| Android 收集注入实例 | `collectEventFrom` / `collectEventFromSuspending` | 显式 `FlowBus` / `ScopedFlowBus` / `FlowBusScope` 与 `EventKey`，不关闭来源 |
| 通用 Flow 挂起收集 | `collectEventSuspending(flow)` | 生命周期收集，异常直接传播，不自动应用总线错误策略 |

```kotlin
viewLifecycleOwner.onEventSuspending<RefreshHomeEvent> { event ->
    viewModel.refreshSuspending(event.source)
}
```

这里的 `refreshSuspending` 是业务提供的挂起函数。上一条回调完成后才处理下一条；页面离开收集状态会请求取消当前回调，业务应遵循协作式取消。同一次生命周期收集重启会等待旧回调结束；阻塞或 NonCancellable 工作不能被强制停止，取消也不回滚副作用。慢回调仍受原有缓冲和溢出策略影响，注册也不意味着订阅立即就绪。需要最新值或实例隔离时，分别显式选择 sticky 和实例来源。详见 [Android 挂起回调与实例接入](library-android/README.md#110-挂起回调与实例接入) 和 [core 挂起订阅](flowbus-core/README.md#110-挂起订阅)。

Compose 提供[独立消费者示例](compatibility/compose-consumer/README.md)，并可选择 [Navigation](compatibility/navigation-consumer/README.md) 或[实际 NavHost](compatibility/navhost-consumer/README.md) 验证生命周期行为。它们不进入发布产物，也不增加基础坐标依赖。已测组合及未验证范围统一见[兼容性评估](docs/next-version-plan.md#兼容性评估)。

## 兼容与发布状态

`1.1.0` 已于 2026-10-12 发布到 Maven Central，两个坐标均可解析。完整变化见 [1.1.0 发布说明](docs/release-notes-1.1.0.md)，发布验证入口见[发布清单](docs/release-checklist.md)。

| 项目 | 版本 |
| --- | --- |
| Kotlin | `1.9.25` |
| Android Gradle Plugin | `8.6.1` |
| Gradle | `8.7` |
| Gradle 运行 JDK | `17` |
| 发布字节码 | Java 8 |
| Android SDK | `minSdk=21`，`compileSdk=35`，`targetSdk=35` |

发布前门禁：

1. `apiCheck` 保护公开 API。
2. `:flowbus-core:test`、`:library-android:testDebugUnitTest` 和 `:app:testDebugUnitTest` 覆盖核心、Android 包装和示例单测。
3. `:library-android:lintRelease`、`:app:lintRelease`、`:app:assembleDebug`、`:app:assembleRelease` 覆盖库和示例应用 release 构建质量。
4. `:library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest` 覆盖设备回归，`app` 走 release R8/minify。
5. `verifyMavenLocalArtifacts` 校验本地 Maven 产物、POM、module metadata、license 和 developer 元数据。
6. `verifyMavenLocalCoreConsumer` 编译真实 Kotlin/JVM consumer，验证 `flowbus-core` 坐标可被独立消费。
7. `verifyMavenLocalConsumer` 构建真实 Android consumer，验证 `flowbus` 坐标和 release shrink。
8. `verifyCentralDeploymentName` 校验包含项目名的上传批次标识与实际 Maven 坐标；`releaseToMavenCentral --dry-run` 只检查远程发布任务图，两者均不会真实上传。

## 文档导航

1. Android 接入与完整场景：[`library-android/README.md`](./library-android/README.md)
2. Core 能力、多实例和 scope 生命周期：[`flowbus-core/README.md`](./flowbus-core/README.md)
3. 发布清单：[`docs/release-checklist.md`](./docs/release-checklist.md)
4. 发布说明：[`docs/release-notes-1.1.0.md`](./docs/release-notes-1.1.0.md)
5. 版本记录：[`CHANGELOG.md`](./CHANGELOG.md)
6. 英文文档：[`README_EN.md`](./README_EN.md)
7. 1.1.0 范围、兼容性评估与后续扩展条件：[`docs/next-version-plan.md`](./docs/next-version-plan.md)
8. [1.0.7 core 二进制消费者回归](compatibility/core-binary-consumer/README.md)：相同旧消费者字节码在原版和候选 JAR 上分别通过 Java 8 运行，另核对旧 JVM 成员描述符；范围不等于完整 ABI 认证。
9. [下一版本实现方案（建议 1.2.0）](docs/implementation-plan-1.2.0.md)：通道级配置、投递契约、性能基线和验收安排；尚未实施。

## 仓库结构

| 目录 | 作用 |
| --- | --- |
| `flowbus-core` | 平台无关核心模块，对外坐标 `io.github.logan0817:flowbus-core`；`flowbus-core` 由 FlowBus 主仓库直接维护 |
| `library-android` | Android 适配模块，对外坐标 `io.github.logan0817:flowbus`；依赖主仓库内的 `flowbus-core` |
| `app` | 示例应用和集成验证入口 |
| `docs` | 发布清单和发布说明 |
| `gradle/release-publishing.gradle.kts` | Maven local 与 Maven Central 发布入口 |
| `gradle/release-verification.gradle.kts` | Maven 产物、POM、module metadata 和真实 consumer 校验 |

## 示例应用

<img src="GIF.gif" width="350" />

示例源码位于 [`app`](./app) 模块，可直接运行或按当前 debug 配置本地构建；仓库不提交生成的 APK 产物，避免把调试包误当正式渠道分发包。建议按这个顺序看：

1. `MainActivity`：全局事件、owner 事件、非 UI 案例入口。
2. `ScopeCaseActivity`：Activity 作用域事件、`eventChannel`，以及 Activity / Fragment 在同一 owner 作用域内共享接收。
3. `StickyCaseActivity`：同一 owner 内的 sticky 最近状态回放。
4. `LoginActivity`：owner 局部总线示例，表单密码只做本地校验，不进入事件 payload。

## License

```text
MIT License
```
