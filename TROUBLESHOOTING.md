# Troubleshooting

## 1. `gradlew` 无法执行

如果你在 macOS / Linux 上遇到：

```text
permission denied: ./gradlew
```

请先给 Gradle Wrapper 增加执行权限：

```bash
chmod +x ./gradlew
```

然后重新执行：

```bash
./gradlew test
```

## 2. JDK 版本不对

FlowBus 当前使用 Java 17 编译。如果本地 JDK 版本不匹配，Gradle 同步或编译可能失败。

可以先检查本地版本：

```bash
java --version
```

如果版本不是 17，请切换到 JDK 17 后再重试。

## 3. IntelliJ IDEA / Android Studio 对 AGP 支持不一致

当前项目使用较新的 Android Gradle Plugin 与 compileSdk 组合。如果你在旧版本 IntelliJ IDEA 中打开项目，可能会看到 AGP 兼容性提示。

建议优先使用较新的 Android Studio 打开项目；如果必须使用 IntelliJ IDEA，请确认 IDE 对当前 AGP 版本有足够支持。

## 4. `post*` 看起来“没反应”

`post*` 是 best-effort 发送。它会立即尝试写入，底层缓冲无法接收时，事件可能会被丢弃。

建议：

1. 想确认本次 `tryEmit` 有没有被底层流拒绝：使用 `tryPostEvent(...)` / `tryPostEventTo(...)`
2. 想看订阅数和溢出策略：使用 `tryPostEventResult(...)`
3. `SUSPEND` 且存在活跃订阅者时，需要等待缓冲可用：使用 `emitEvent(...)` / `emitEventTo(...)`；切换 API 不会改变溢出策略
4. 调试时关注日志里的 rejected / dropped event warning；丢弃策略下 `accepted=true` 也可能发生实时丢弃，不能只靠 warning 判断送达

普通事件没有 replay：尚未订阅、页面未进入收集的生命周期状态，或已停止收集时发送，未来订阅者不会补收。`emit*` 在无人订阅时也可以立即完成；发送完成不等于业务处理成功。先确认事件名、bus/owner 和订阅是否活跃，再检查缓冲策略。对必须恢复或可靠完成的业务使用状态、数据库或持久化任务。

## 5. Fragment / Activity 作用域事件收不到

先确认你是否把事件发到了正确的 owner：

1. `postEvent(...)`：发到全局总线
2. `postEventTo(owner, ...)`：发到指定 owner 对应的局部总线
3. `onEvent(from = owner)` / `eventFlowFrom(owner)`：从指定 owner 对应的局部总线接收

常见误区不是“谁在监听”，而是“事件挂在哪个作用域上”。

## 6. sticky event 行为和预期不一致

sticky event 适合“后来订阅的人也应该拿到最近一次值”的场景，例如最近一次初始化结果、最近一次配置、最近一次同步状态。

如果你发的是 Toast、导航、点击动作这类一次性事件，通常不应该用 sticky。

另外请区分：

1. `clearSticky*`：只清 replay 缓存，保留 Flow
2. `removeSticky*`：移除当前 store 里的 sticky 条目并清空 replay；已经拿到手的旧 Flow 引用不会被主动 cancel

sticky replay 与实时广播独立管理。`DROP_LATEST` 下新值可能被实时缓冲丢掉，却仍成为后订阅者的 replay；例如活跃慢订阅者收到 `[1, 2]`，新订阅者先读到 `3`。这是现有契约，replay 不能作为“所有订阅者已接收”的证据。

1.1.0 已发布的 scope 关闭等待支持取消：`closeSuspending()` 自己发起关闭、等待在途操作时，禁止新操作但 `isClosed=false`，其他等待关闭返回 `ClosingInProgress`；准备清理前取消且未收到永久关闭请求时会恢复可用。若 `close()` 或生命周期绑定已准备关闭，取消只结束这次等待，句柄和清理都不会回滚。等待期间到达的显式 `close()`、`removeScope()` 或绑定 Job 完成请求都会保留，取消、`tryClose` 超时或线程中断都不会让该 scope 恢复使用或丢失请求。详细边界见 [core README](flowbus-core/README.md#scope-关闭后会发生什么)。

## 7. `flowbus-core` 目录缺文件或无法编译

flowbus-core 已由主仓库直接维护，正常拉取 FlowBus 主仓库后应该能直接看到源码、测试和 `build.gradle.kts`。

如果目录缺文件，优先检查当前工作区是否残留旧子仓或嵌套 Git 元数据：

```bash
git status --short
ls flowbus-core
```

如果 `flowbus-core` 下仍有 `.git`、独立 `settings.gradle.kts`、独立 `gradlew` 或独立 `gradle.properties`，说明本地目录还停在旧结构。

建议先备份本地改动，再重新拉取最新主仓库，或清理这些旧结构文件。

主仓库内的正确验证入口是：

```bash
./gradlew :flowbus-core:test :library-android:testDebugUnitTest
```

## 8. API 21–23 遇到 `ConcurrentHashMap.compute*` 方法缺失

1.0.7 的 core 产物包含 Android API 24 才提供的 `compute`、`computeIfPresent`、`computeIfAbsent` 调用。`minSdk=21` 声明本身不会补齐这些平台方法；普通 D8 转换也不能替代 core library desugaring。

1.1.0 已将这些调用替换为低 API 可用的并发操作与内部同步，不提高 minSdk 或基础依赖要求。最终 main CI 已通过 API 21 库层和未启用 core library desugaring 的 minified Maven 消费者设备验收；使用旧版本遇到该问题时可升级到 1.1.0。已测组合及支持边界见[兼容性评估](docs/next-version-plan.md)。

## 9. 挂起订阅未收到事件或处理被取消

`collectSuspending`、`onEventSuspending`、`collectEventFromSuspending` 等入口从 `1.1.0` 开始提供，不属于 `1.0.7`。先核对实际解析版本，以及发送和接收是否来自同一个实例、owner 和事件 key；构造注入实例不会与默认总线自动共享事件。

LifecycleOwner 订阅离开指定活跃状态会向当前回调发出取消请求；同一次 repeatOnLifecycle 再次活跃时，等待旧回调结束后重新订阅。业务使用阻塞调用或 NonCancellable 时，取消不能强制停止执行，也不会回滚已发生的副作用；不要在主线程阻塞。普通事件不补收，sticky 需显式开启。注册返回 Job 不意味着底层订阅就绪；不要依靠相邻语句或任意延时确认它。单条订阅等待上一条回调完成才处理下一条，慢回调仍受缓冲/溢出策略影响，不会自动并发或采用 collectLatest。

总线挂起入口的普通回调异常使用来源 errorHandler，默认重新抛出并终止订阅；CancellationException 直接传播。通用 `collectEventSuspending(flow)` 的上游和回调异常都直接传播，不自动套用总线日志/错误策略。显式来源取流失败（例如再次活跃时传入 scope 句柄已关闭）也直接传播，不会悄悄换到同名新句柄。

收集取消不会关闭注入实例或 scope。业务持有者需管理它们的生命周期；业务挂起函数应遵循协作式取消。详细用法见 [Android 挂起回调与实例接入](library-android/README.md#110-挂起回调与实例接入)。

## 1.2.0 通道配置冲突或策略不符合预期

先核对发送和接收是否使用同一个 bus/owner/store 与 key，再核对订阅是否真正活跃，最后检查有效 overflow。全局配置不会修改 owner 或注入实例；普通发送不会进入 sticky 订阅。STARTED 以下无普通事件补收，增加容量也不会变成离线队列。

configureChannel 必须早于首次 post/emit/flow。IllegalArgumentException 表示非法容量或同名不同运行时类型；IllegalStateException 表示当前 store 已绑定不同有效配置。清理单条流不会解除绑定；完整 scope 清理后的同名新 store 需要重新配置。相同有效配置可重复调用，不要在业务发送后动态调整策略。

inspect().config 显示 bus 默认值；tryPostResult/tryPostStickyResult.overflowPolicy 才是本次流策略。accepted 不代表没有丢弃或业务完成。确认来源、就绪、生命周期和实际策略后再排查回调错误，避免以 sticky 或固定延时掩盖时序问题。详细契约见 [core README](flowbus-core/README.md#120-候选按通道配置)。
