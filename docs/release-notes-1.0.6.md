# FlowBus 1.0.6 发布说明

[English](en/release-notes-1.0.6.md)

## 1. 发布结论

`1.0.6` 是一次可靠性和可观测性增强版本，重点不是增加更短的调用写法，而是让发送结果、scope 生命周期、sticky replay 和发布验证更清楚。

适合升级的团队：1. 已经在 Android UI、ViewModel、Repository 或 Worker 中使用 FlowBus。2. 需要排查事件是否被总线接收。3. 需要清理 sticky 最近值。4. 希望主仓统一维护 core 和 Android 产物。

## 2. 能力总览

| 能力 | 新增或强化入口 | 适用场景 | 边界说明 |
| --- | --- | --- | --- |
| 主仓统一维护 | 根工程发布 `flowbus-core` 和 `flowbus` | 发布、CI、API 基线统一管理 | 不再按旧子仓工作流初始化或发布 |
| 发送结果诊断 | `tryPostResult(...)`、`tryPostStickyResult(...)`、`tryPostEventResult(...)` | 日志、单测断言、发送失败排查 | 只说明 `tryEmit` 是否被拒绝，不代表订阅者处理成功 |
| 只读诊断快照 | `inspect()`、`inspector().snapshot()`、`inspectScope(...)` | 查看 event name、scope、订阅数、sticky replay 数和发送指标 | 只暴露元数据，不暴露 sticky payload |
| scope 异步关闭 | `closeSuspending()`、`tryClose(timeoutMillis)`、`FlowBusCloseResult` | UI 或单线程环境中关闭 scope | 超时返回结果，不强行阻塞调用线程 |
| sticky 最新值一次性消费 | `consumeStickyLatest(...)`、`consumeStickyLatestEvent(...)`、`channel.consumeStickyLatest()` | 读取最近 sticky 结果后立即清 replay | 只处理当前 store 内的 sticky replay，不替代状态管理 |
| 发布验证增强 | `apiCheck`、release lint、assemble、本地 Maven 产物校验、发布 dry-run | 上线前校验 API、产物和发布任务图 | connected test 依赖设备或 CI emulator 环境 |
| 发布脚本整理 | `gradle/release-publishing.gradle.kts`、`gradle/release-verification.gradle.kts` | 区分发布入口和发布前校验 | 根 `build.gradle.kts` 只保留通用配置和脚本入口 |

## 3. API 状态对比

| 状态或目标 | 推荐 API | 是否挂起 | 是否保留 replay | 是否清 replay | 结果怎么看 |
| --- | --- | --- | --- | --- | --- |
| 普通轻量事件 | `post(...)` / `postEvent(...)` | 否 | 否 | 否 | best-effort，失败时可能返回 `false` 或记录 warning |
| 普通事件诊断 | `tryPostResult(...)` / `tryPostEventResult(...)` | 否 | 否 | 否 | 返回订阅数、溢出策略、sticky replay 数和结果分类 |
| 关键事件写入 | `emit(...)` / `emitEvent(...)` | 是 | 否 | 否 | 按背压规则等待底层流接收 |
| sticky 最近值 | `postSticky(...)` / `postStickyEvent(...)` | 否 | 是 | 否 | 后订阅者能先收到最近值 |
| sticky 关键写入 | `emitSticky(...)` | 是 | 是 | 否 | 按背压规则等待 sticky 写入完成 |
| sticky 只读清理 | `clearSticky(...)` / `clearStickyEvent(...)` | 否 | 已清空 | 是 | 保留通道，只清 replay 缓存 |
| sticky 通道移除 | `removeSticky(...)` / `removeStickyEvent(...)` | 否 | 已清空 | 是 | 移除当前 store 条目，后续访问会按需新建 |
| sticky 一次性消费 | `consumeStickyLatest(...)` / `consumeStickyLatestEvent(...)` | 否 | 读取后清空 | 是 | 返回当前最新 replay 值；没有值时返回 `null` |

## 4. Android 与 core 对照

| 能力 | Android 入口 | core 入口 | 推荐读法 |
| --- | --- | --- | --- |
| 全局发送 | `postEvent(...)` | `DefaultFlowBus.post(...)` | Android 项目优先用 Android 入口 |
| owner 作用域发送 | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | `FlowBus.scoped(...)` / `FlowBusScope` | Android 页面间局部事件用 owner 作用域 |
| 命名通道 | `eventChannel<T>("name")` | `eventChannel<T>("name")` | 长期复用的业务通道优先命名 |
| 生命周期安全接收 | `onEvent(...)` / `collectEvent(...)` | `collect(...)` / 自行管理 `CoroutineScope` | UI 层优先 Android 生命周期入口 |
| sticky 一次性消费 | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | `consumeStickyLatest(...)` | 只用于边界明确的 sticky replay 清理 |
| 诊断快照 | 通过底层 bus 能力暴露 | `inspect()` / `inspector().snapshot()` | 用于调试、日志和测试，不进入核心业务判断 |

## 5. 兼容与验证

| 项目 | 发布基线 |
| --- | --- |
| Kotlin | `1.9.25` |
| Android Gradle Plugin | `8.6.1` |
| Gradle | `8.7` |
| Gradle 运行 JDK | `17` |
| 发布字节码 | Java 8 |
| Android SDK | `minSdk=21`，`compileSdk=35`，`targetSdk=35` |

| 验证项 | 推荐命令 | 结果判断 |
| --- | --- | --- |
| API 兼容 | `./gradlew apiCheck --warning-mode=all` | 公开 API 基线没有非预期漂移 |
| 单测与 lint | `./gradlew :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease --warning-mode=all` | core、Android 封装和 release lint 通过 |
| 示例构建 | `./gradlew :app:assembleDebug :app:assembleRelease --warning-mode=all` | debug / release APK 均可构建 |
| 本地产物 | `./gradlew verifyMavenLocalArtifacts --warning-mode=all` | jar / aar / POM / module metadata / license / developer / sources / javadoc 结构完整 |
| Core 消费者 | `./gradlew verifyMavenLocalCoreConsumer --warning-mode=all` | `flowbus-core` 坐标可被真实 Kotlin/JVM consumer 编译 |
| Android 消费者 | `./gradlew verifyMavenLocalConsumer --warning-mode=all` | `flowbus` 坐标可被真实 Android consumer 解析并通过 release shrink |
| 发布任务图 | `./gradlew releaseToMavenCentral --dry-run --warning-mode=all` | 远程发布任务图可解析，且不会真实上传 |
| 设备回归 | `./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all` | 有设备或 CI emulator 时执行；失败先查设备状态和 Activity 启动链路 |

## 6. 升级风险与处理

| 风险点 | 触发条件 | 建议处理 | 回退方式 |
| --- | --- | --- | --- |
| 误把发送结果当业务成功 | 只看 `accepted = true` 就认为订阅者已处理 | 文档、日志和测试里统一写成“总线层接收结果” | 改回 `tryPostEvent(...)` 或补业务 ACK |
| 溢出策略被误当可靠队列 | 使用 `DROP_OLDEST` / `DROP_LATEST` 承载关键链路 | 关键事件改用 `emit*` 或专用队列 | 回退到 `SUSPEND` 策略或业务队列 |
| sticky replay 被长期状态化 | 用 sticky event 替代页面状态容器 | 长期状态继续使用 `StateFlow` | 去掉 sticky，改回状态容器 |
| 依赖 sticky Flow 的运行时实现 | 外部代码把 `stickyFlow(...)` 强转为 `MutableSharedFlow` 或直接读取 `replayCache` / `subscriptionCount` | 只按公开 API 的 `Flow` 契约收集；需要诊断时使用 `inspect()` | 移除强转，改为 `consumeStickyLatest(...)`、`stickyReplayCache(...)` 或诊断快照 |
| 一次性消费被误读为全局互斥 | 多线程同时读写同一 sticky 业务结果 | 仅用于当前 store 内 replay 读清；如果要阻止后续写入，需要业务层锁或状态机 | 改用业务状态机、数据库事务或专用 channel |
| Android 配置时序过晚 | 首个 `FlowEventBus` 创建后才 configure | 放到 `Application.onCreate()` | 恢复默认配置或提前初始化 |

## 7. 发布交付物

| 交付物 | 位置 | 用途 |
| --- | --- | --- |
| 变更记录 | [`CHANGELOG.md`](../CHANGELOG.md) | 面向版本历史的简短记录 |
| 发布说明 | [`docs/release-notes-1.0.6.md`](./release-notes-1.0.6.md) | 面向版本能力说明和升级评估 |
| 根 README | [`README.md`](../README.md) | 面向首次接入和模块选择 |
| Android README | [`library-android/README.md`](../library-android/README.md) | 面向 Android 接入和生命周期 API |
| core README | [`flowbus-core/README.md`](../flowbus-core/README.md) | 面向纯 Kotlin 和底层语义 |
| 发布清单 | [`docs/release-checklist.md`](./release-checklist.md) | 面向上线前核对 |
| 发布脚本 | [`gradle/release-publishing.gradle.kts`](../gradle/release-publishing.gradle.kts)、[`gradle/release-verification.gradle.kts`](../gradle/release-verification.gradle.kts) | 面向 Maven 发布和发布前校验 |
