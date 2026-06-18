# Changelog

## 1.0.7

完整发布说明见 [`docs/release-notes-1.0.7.md`](./docs/release-notes-1.0.7.md)。

| 类型 | 变更 | 影响 |
| --- | --- | --- |
| core 可靠性 | 修复 `removeScope(scopeName)` 并发介入 `FlowBusScope.close()` 时可能提前清理旧 store 的竞态 | 已开始的发送 / 取流操作能继续使用原 store，同名 scope 重开不受旧 cleanup 误伤 |
| 测试覆盖 | 补充 `removeScope()` 与 pending close 并发的回归测试 | `FlowBusScopeCloseTest` 覆盖 close、closeSuspending、tryClose、同名重开和 removeScope 竞态 |
| 文档整理 | 优化 core README 的术语速查、API 选择矩阵、sticky replay、close 对比说明 | 表格补充功能解释、适合场景和排查入口，减少重复最短用法 |
| 发布准备 | 版本、安装坐标、发布说明和发布清单统一到 `1.0.7` | 避免发布前文档和 Gradle 元数据仍指向 1.0.6 |
| 发布校验 | 发布就绪测试检查 1.0.7 文档、版本、README、`verifyMavenLocalCoreConsumer`、`verifyMavenLocalConsumer`、license 和 developer 元数据说明 | 发布前能更早发现版本漂移、文档漏改和 Maven 元数据缺口 |

## 1.0.6

完整发布说明见 [`docs/release-notes-1.0.6.md`](./docs/release-notes-1.0.6.md)。

| 类型 | 变更 | 影响 |
| --- | --- | --- |
| 仓库维护 | `flowbus-core` 改为由 FlowBus 主仓直接维护 | 发布、CI 和 API 基线都从主仓执行 |
| 诊断能力 | 新增 `inspect()`、`inspector()`、scope snapshot、事件指标和发送结果快照 | 更容易排查 event name、scope、订阅数和 sticky replay |
| 发送可观测性 | 新增 `tryPostResult(...)`、`tryPostStickyResult(...)` 和 Android 层 `tryPostEventResult(...)` | 可区分总线层 `tryEmit` 接收结果和业务处理结果 |
| scope 生命周期 | 新增 `closeSuspending()`、`tryClose(timeoutMillis)` 和关闭结果 `FlowBusCloseResult` | UI 或单线程关闭场景可避免同步阻塞 |
| sticky 一次性消费 | 新增 `consumeStickyLatest(...)`、`consumeStickyLatestEvent(...)` 和 channel 入口 | 可读取当前 sticky replay 最新值并清 replay |
| 竞态修复 | 修复 scope 关闭竞态和 sticky replay 写入 / 消费串行化风险 | 关闭中不再误报 timeout，同名 scope 可稳定重新打开 |
| 示例与测试 | 示例页面改为场景化案例，connected test 增加启动和 ready 检查 | 示例链路更贴近真实接入场景 |
| 发布校验 | 本地 Maven 校验覆盖 jar / aar、POM、Gradle module metadata、sources jar、javadoc jar、license / developer metadata、`verifyMavenLocalCoreConsumer` 和 `verifyMavenLocalConsumer` | 发布前能检查产物结构、发布元数据和真实消费者接入 |
| 构建维护 | 发布入口拆到 `gradle/release-publishing.gradle.kts`，发布前校验拆到 `gradle/release-verification.gradle.kts` | 根 `build.gradle.kts` 只保留通用配置和脚本入口，手动发布命令保持不变 |

| 兼容项 | 说明 |
| --- | --- |
| 推荐接入线 | Kotlin 1.9.25、AGP 8.6.1、Gradle 8.7、JDK 17、Android minSdk 21 |
| 发布字节码 | 保持 Java 8，方便 Android API 21+ 项目接入 |
| 旧构建线 | Kotlin 1.8 及以下、AGP 7.x 项目不承诺无痛接入，需要单独验证 |

| 验证项 | 说明 |
| --- | --- |
| 本地基础验证 | JVM 单测、Android 单元测试、API 检查、release lint、release assemble、`verifyMavenLocalArtifacts`、`verifyMavenLocalCoreConsumer`、`verifyMavenLocalConsumer` 和 Maven Central dry-run |
| 设备回归验证 | `:library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest` 依赖真机或 CI emulator；设备未在线时先排查设备环境和 Activity 启动链路 |
