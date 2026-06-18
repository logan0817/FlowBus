# FlowBus 1.0.7 发布说明

[English](en/release-notes-1.0.7.md)

## 1. 发布结论

`1.0.7` 是一次发布前可靠性修复版本，重点是把 `FlowBusScope` 关闭中的 store 清理竞态补齐，并把 README、发布清单和发布说明同步到当前版本。

适合升级的团队：1. 使用 `openScope(...)` 管理 Session / Repository / Worker / Task 生命周期。2. 会在关闭 scope 后立即重开同名 scope。3. 发布前依赖本地 Maven 产物、真实 consumer 和 release dry-run 做验证。4. 需要更清晰的 core README 来判断 API 选择。

## 2. 变更总览

| 类型 | 变更 | 影响范围 | 说明 |
| --- | --- | --- | --- |
| scope 关闭竞态 | `removeScope(scopeName)` 遇到关闭中的 `FlowBusScope` 时不再提前清理旧 store | `flowbus-core` | 已开始的发送 / 取流操作继续使用原 store，等关闭动作完成后再清理 |
| 同名 scope 重开 | 补充 `removeScope()` 并发介入关闭流程的回归测试 | `FlowBusScopeCloseTest` | 覆盖 `close()` 已让句柄失效但旧操作尚未结束的分支 |
| 关闭结果文档 | 修正 `tryClose(timeoutMillis)` 的超时语义说明 | `FlowBusScope`、`FlowBusCloseResult`、core README | 区分“本次 tryClose 发起关闭超时后可重试”和“已有 close 在进行时只是等待超时” |
| 文档结构 | core README 增加术语速查、API 功能解释、close 排查提示，并删除重复最短用法 | `flowbus-core/README.md`、`flowbus-core/README_EN.md` | 表格不只写对应 API，也解释功能、适用场景和边界 |
| 发布版本 | 版本、安装坐标、发布清单和发布说明统一到 `1.0.7` | 根 README、模块 README、Gradle 配置、docs | 避免 1.0.7 发布准备仍指向 1.0.6 |
| 发布门禁 | 发布就绪测试新增 1.0.7 文档和版本一致性断言 | `FlowBusReleaseReadinessTest` | 防止后续漏改版本号、发布说明链接或安装坐标 |

## 3. 关闭语义

| API | 做什么 | 适合场景 | 边界 |
| --- | --- | --- | --- |
| `close()` | 立即让当前 `FlowBusScope` 句柄失效，并在已开始操作结束后清理 store | 生命周期回调、UI 层释放句柄 | 不等待清理完成，旧 `Flow` 引用不会被主动 cancel |
| `closeSuspending()` | 在后台调度器等待关闭和清理完成 | 协程中需要确认资源已清理 | 如果有挂起发送被 collector 阻塞，会继续等待 |
| `tryClose(timeoutMillis)` | 最多等待指定时间，并返回 `FlowBusCloseResult` | 测试、退出流程、需要明确超时结果 | 如果已有 `close()` 先让句柄失效，超时不代表旧句柄恢复可用 |
| `removeScope(scopeName)` | 移除当前 scope store；如果同名句柄还在，会先走关闭路径 | 外部统一清理命名 scope | 不再绕过关闭状态机提前清理 in-flight store |

## 4. 验证清单

| 验证项 | 命令 | 结果判断 |
| --- | --- | --- |
| scope 关闭回归 | `./gradlew :flowbus-core:test --tests com.logan.flowbus.core.FlowBusScopeCloseTest --warning-mode=all` | close、closeSuspending、tryClose、同名重开和 removeScope 并发分支通过 |
| 发布文档门禁 | `./gradlew :library-android:testDebugUnitTest --tests com.logan.flowbus.FlowBusReleaseReadinessTest --warning-mode=all` | 版本、发布说明、README、release checklist 一致 |
| API 兼容 | `./gradlew apiCheck --warning-mode=all` | 公开 API 没有非预期漂移 |
| 本地发布产物 | `./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalConsumer --warning-mode=all` | jar / aar / POM / module metadata / license / developer /真实 consumer 验证通过 |
| Android 设备回归 | `./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all` | 有设备或 CI emulator 时执行，app 走 release R8/minify |
| 发布任务图 | `./gradlew releaseToMavenCentral --dry-run --warning-mode=all` | 远程发布任务图可解析，且不会真实上传 |

## 5. 升级风险与处理

| 风险点 | 触发条件 | 建议处理 |
| --- | --- | --- |
| 把 `accepted = true` 当成业务成功 | 只看发送结果就认为订阅者已经处理 | 文档、日志和测试里统一写成“总线层接收结果”，业务成功另做 ACK 或状态更新 |
| 把 `DROP_OLDEST` / `DROP_LATEST` 当可靠队列 | 用溢出策略承载关键链路 | 关键事件用 `emit*`、业务队列或状态机 |
| 把 sticky replay 当长期状态 | 用 sticky event 替代页面状态容器 | 长期状态继续使用 `StateFlow`、数据库或业务状态机 |
| 误解 `tryClose` timeout | 已有 `close()` 在进行时又调用 `tryClose(timeoutMillis)` | 看 `scope.isClosed` 和 `FlowBusCloseOutcome`，不要把 timeout 解读成旧句柄恢复可用 |
| 依赖 sticky Flow 运行时实现 | 外部代码强转 `stickyFlow(...)` 或读取运行时 `replayCache` | 需要读清最新值用 `consumeStickyLatest(...)`，需要诊断数量用 `inspect()` |

## 6. 交付物

| 交付物 | 位置 | 用途 |
| --- | --- | --- |
| 变更记录 | [`CHANGELOG.md`](../CHANGELOG.md) | 面向版本历史的简短记录 |
| 发布说明 | [`docs/release-notes-1.0.7.md`](./release-notes-1.0.7.md) | 面向版本能力说明和升级评估 |
| 发布清单 | [`docs/release-checklist.md`](./release-checklist.md) | 面向上线前核对 |
| core README | [`flowbus-core/README.md`](../flowbus-core/README.md) | 面向纯 Kotlin、scope 生命周期和底层语义 |
| Android README | [`library-android/README.md`](../library-android/README.md) | 面向 Android 接入和生命周期 API |
