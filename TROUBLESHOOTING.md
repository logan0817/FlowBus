# 排障指南

[项目首页](README.md) · [文档索引](docs/README.md)

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

构建 FlowBus 仓库需要 JDK 17，发布字节码仍为 Java 8。如果本地 JDK 版本不匹配，Gradle 同步或编译可能失败。

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

1.1.0 已将这些调用替换为低 API 可用的并发操作与内部同步，不提高 minSdk 或基础依赖要求。最终 main CI 已通过 API 21 库层和未启用 core library desugaring 的 minified Maven 消费者设备验收；使用旧版本遇到该问题时可升级到 1.1.0。已测组合及支持边界见[兼容性评估](docs/compatibility.md)。

## 9. 挂起订阅未收到事件或处理被取消

`collectSuspending`、`onEventSuspending`、`collectEventFromSuspending` 等入口从 `1.1.0` 开始提供，不属于 `1.0.7`。先核对实际解析版本，以及发送和接收是否来自同一个实例、owner 和事件 key；构造注入实例不会与默认总线自动共享事件。

LifecycleOwner 订阅离开指定活跃状态会向当前回调发出取消请求；同一次 repeatOnLifecycle 再次活跃时，等待旧回调结束后重新订阅。业务使用阻塞调用或 NonCancellable 时，取消不能强制停止执行，也不会回滚已发生的副作用；不要在主线程阻塞。普通事件不补收，sticky 需显式开启。注册返回 Job 不意味着底层订阅就绪；不要依靠相邻语句或任意延时确认它。单条订阅等待上一条回调完成才处理下一条，慢回调仍受缓冲/溢出策略影响，不会自动并发或采用 collectLatest。

总线挂起入口的普通回调异常使用来源 errorHandler，默认重新抛出并终止订阅；CancellationException 直接传播。通用 `collectEventSuspending(flow)` 的上游和回调异常都直接传播，不自动套用总线日志/错误策略。显式来源取流失败（例如再次活跃时传入 scope 句柄已关闭）也直接传播，不会悄悄换到同名新句柄。

收集取消不会关闭注入实例或 scope。业务持有者需管理它们的生命周期；业务挂起函数应遵循协作式取消。详细用法见 [Android 挂起回调与实例接入](library-android/README.md#110-挂起回调与实例接入)。

## 1.2.0 通道配置冲突或策略不符合预期

先核对发送和接收是否使用同一个 bus/owner/store 与 key，再核对订阅是否真正活跃，最后检查有效 overflow。全局配置不会修改 owner 或注入实例；普通发送不会进入 sticky 订阅。STARTED 以下无普通事件补收，增加容量也不会变成离线队列。

configureChannel 必须早于首次 post/emit/flow。IllegalArgumentException 表示非法容量或同名不同运行时类型；IllegalStateException 表示当前 store 已绑定不同有效配置。清理单条流不会解除绑定；完整 scope 清理后的同名新 store 需要重新配置。相同有效配置可重复调用，不要在业务发送后动态调整策略。

inspect().config 显示 bus 默认值；tryPostResult/tryPostStickyResult.overflowPolicy 才是本次流策略。accepted 不代表没有丢弃或业务完成。确认来源、就绪、生命周期和实际策略后再排查回调错误，避免以 sticky 或固定延时掩盖时序问题。详细契约见 [core README](flowbus-core/README.md#120-候选按通道配置)。

## 1.3.0 的订阅、Java 与 Compose 排查

以下适用于已发布的 `1.3.0` 或更新版本；1.2.0 不提供这些新接口。

- 需要确认自己的订阅已登记时，使用 `onSubscribed` 重载；不要用 inspector 总订阅数或 `Flow.onStart` 充当屏障。通知只执行短操作，让独立生产协程继续；它不表示 replay 或业务完成，每次生命周期重新收集都会再次通知，通知后仍可能取消。
- 查询某条通道的实际配置与普通/sticky 各自订阅数，使用来源的 `inspectChannel(key)`；旧 `inspect().config` 仍是默认值。未知 core 查询不创建通道；Android 静态入口首次获取来源可以初始化 bus。快照不能作为正式业务控制或就绪依据。
- Java 优先使用 `FlowBusJava` 的 Class/typed channel 与生命周期订阅；owner 决定来源，receiver 决定活跃阶段。`cancel()` 请求取消，`isCompleted()` 才确认在途处理结束。普通与 sticky 不交叉投递。
- Compose 新坐标为可选 `flowbus-compose`，不包含在正式 1.2.0 中。只更新回调不重订阅；来源、key、owner、阈值或 sticky 变化才重建。离开组合/停止取消收集，不关闭注入来源；需要当前交互页面才能执行的副作用应选择合适 owner 和 RESUMED 阈值。

完整候选用法见 [core](flowbus-core/README.md#subscription-notification)、[Android](library-android/README.md#development-130) 和 [Compose](library-compose/README.md)。

<a id="131-candidate-boundaries"></a>

## 1.3.1 的边界修复与升级排查

当前公开版本为 **1.4.0**，已发布到 Maven Central。以下保留 1.3.0 已复现问题与 1.3.1 修复记录；当前排障 API 与正式验收见 [1.4.0 发布说明](docs/release-notes-1.4.0.md)。

### sticky 回调再次发送后，最近值反而变旧

1.3.0 已复现：订阅回调收到 sticky `1` 时立即再次发送 `2`，实时收到 `[1, 2]`，随后 `consumeStickyLatest` 却读到 `1`。这与 `DROP_LATEST` 下实时丢弃但保留 replay 的既有语义不同，是同步重入期间的缓存提交顺序问题。1.3.1 已实现修复，本地挂起发送、清理、取消、订阅交界和立即回调回归通过；完整设备、正式定向性能与最终 main CI 验收已完成。

定位时保存最小重入路径、实际 dispatcher、buffer/replay/overflow 配置及两侧收到的值。不要添加固定 delay 或重复发送来掩盖问题，也不要把 replay 当作业务完成确认；当前需要可恢复状态的业务仍应使用 `StateFlow` 或业务持久化状态。

### openScope 抛出缺少 Job，但随后同名创建仍失败

检查 `closeWhen.coroutineContext[Job]` 是否存在。无 Job 的自定义 `CoroutineScope` 和 `GlobalScope` 不能用于该生命周期绑定。1.3.0 会先登记句柄再抛错，可能留下调用者未拿到的句柄；1.3.1 改成登记前校验，定向本地回归已通过。

先修正传入的 scope。如果正在排查 1.3.0，使用 inspector 确认残留登记的来源和所有权；不要直接对可能由其他业务共享的名称调用 `removeScope`，该操作会关闭句柄并清理当前 store。有效 Job、已完成/取消 Job 以及直接 `bindTo` 的关闭语义以 core 指南为准。

### Job 看似活跃，但 onSubscribed 从未触发

除检查来源、key 和实际 owner 生命周期外，还要检查最低收集状态。阈值只应为 `CREATED`、`STARTED` 或 `RESUMED`；`DESTROYED` 不是一种“销毁时收集”的模式。1.3.0 接受该参数时可能返回活跃 Job 而不订阅；1.3.1 会像拒绝 `INITIALIZED` 一样同步抛出 `IllegalArgumentException`，便捷入口也不会先获取全局/owner 总线。Android 参数与副作用回归、Compose 新增 API21/35 真实阶段均已通过，具体受测提交见发布说明。

owner 本身已经销毁且阈值合法时，应正常不订阅并完成 Job。不要把这种情况改成非法阈值，也不要捕获所有异常后当作订阅成功。

升级测试需要重新编译消费者：inline `collectEvent` 的校验和旧 reified wrapper 的来源获取顺序可能已内嵌到调用方，单纯替换 AAR 不会更新这些字节码。使用 1.3.1 release R8 包核对实际解析版本，再按对应入口验证；公开制品与 API21/35 已验收，后续本地修改仍需单独验证。

<a id="event-diagnostics-140"></a>

## 1.4.0：用事件记录定位收不到与订阅退出

本节针对已发布的 **1.4.0**，可直接解析 Maven Central 正式坐标。验证本地修改时使用[隔离本地流程](CONTRIBUTING.md#candidate-builds)；API 与完整契约见 [core](flowbus-core/README.md#event-diagnostics-140) 和 [Android](library-android/README.md#event-diagnostics-140)。

### 同名事件发送和订阅看起来正常，仍然收不到

在实际来源上安装 `observeDiagnostics`；Android 全局/owner 入口使用 `observeEventDiagnostics`，Java 使用 `FlowBusJava.observeDiagnostics`。Android owner 决定来源，不能用全局观察器诊断另一个 owner 或注入实例。便捷入口首次可创建来源，应先完成全局配置，并在主线程取得 Android 来源。

然后按记录逐步核对：

1. 对比 `channel.busId`、`storeId`、`flowId` 及普通/sticky 标记。名称相同不保证来源相同；scope 清理或移除事件流后的新旧 Flow 也有不同身份，旧订阅不会自动切换。
2. 查找发送之前对应收集的 `SubscriptionStarted`，用 `subscriptionId` 区分每次生命周期活跃阶段。记录表示底层已经登记，先于 replay 和用户 `onSubscribed`；业务需要自己的就绪通知时仍使用 `onSubscribed`，诊断不是业务屏障。
3. 查 `Published.result`。`AcceptedWithoutActiveSubscriber` 表示采样时无活跃订阅者，不是可靠投递；普通事件不会等未来订阅。DROP 类结果只说明策略，不能断言某条事件实际丢弃，`accepted=true` 也不能证明送达或处理完成。
4. 查同一订阅的 `Failure` 和 `SubscriptionEnded`。托管 collect 可区分类型校验、事件回调或 `onSubscribed` 失败；任意 Flow 的 `map` / `filter`、直接 collect 或通用生命周期 collect 出错，只能从退出元数据判断，不能自动归为事件回调错误。普通业务异常仍由原有错误策略决定，取消不是业务错误。

记录无跨线程总顺序。`Unconfined` 等立即恢复场景中，订阅者可以先执行、失败或退出，然后才出现发送方的 `Published`；不要仅按日志出现顺序推断因果或统计业务成功率。

### 临时打开诊断后，只有发送记录，没有订阅开始

诊断默认关闭，不保留历史，也不会改造此前已经取得的普通 Flow 或现存订阅。完整跟踪需要**先安装观察器，再取得 Flow，再收集**。可以先用 Inspector 查看当前已登记流和订阅数，再由订阅持有者安全取消并等待旧收集退出、重新取得 Flow 建立需要跟踪的订阅；不要重复注册、清理共享 scope 或给发送增加固定延时来制造“通过”。

关闭观察器不会结束业务订阅。需要保留结束记录时，先等待被跟踪的收集真正退出，再调用诊断句柄的 `close()`。关闭幂等且不等待已经开始的通知，不清 sticky，也不关闭来源。

### 页面已停止或取消 Job，但结束记录没有出现

取消请求不等于收集已经退出。阻塞调用或 `NonCancellable` 回调会推迟实际退出与 `SubscriptionEnded`；观察器不会强制终止它们。同一次生命周期收集循环仍须等待旧回调结束后再启动下一轮。先检查业务是否遵循协作式取消，不要把未退出的收集记成已结束。

### 观察器抛错、重入或导出日志

通知同步运行在触发线程/协程，并可被不同线程并发调用；观察器应快速返回。它抛出的普通异常，包括自身取消异常，会被隔离，不能代替业务错误处理；JVM 致命错误不保证恢复。观察器同步重入的业务操作仍执行，同一线程嵌套诊断被抑制，因此不能靠观察器自己再次发事件来构造完整诊断链。

记录不持有事件 payload、异常对象、异常消息、owner、Job 或业务回调，不提供堆栈和历史日志。`eventName`、`scopeName` 仍可能包含敏感信息，向外导出前由应用脱敏；需要异步导出时也应由应用控制队列容量和生命周期。
