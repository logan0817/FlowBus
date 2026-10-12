# FlowBus 1.1.0 发布说明

[English](en/release-notes-1.1.0.md)

1.1.0 已于 2026-10-12 发布到 Maven Central，包含可靠性修复和新增挂起订阅 API。相对已发布 1.0.7，不提高基础依赖、minSdk 或字节码要求，旧调用方无需迁移。

## 主要变化

- core 替换 Android API 24 才提供的 ConcurrentHashMap.compute* 调用，保持 store 更新原子性及旧 store 清理与同名 scope 重开隔离。
- closeSuspending 使用可取消挂起等待。自己发起关闭、尚未准备清理且没有永久关闭请求时，取消可恢复可用；等待已经准备的关闭时取消不会撤销关闭。显式 close、removeScope 或绑定 Job 完成请求不会因等待取消、超时或中断丢失。
- Android 初始化仅在实际构造成功后冻结配置，失败后允许正确配置并重试；全局实例读取沿用同步初始化路径。
- core 新增 collectSuspending / collectStickySuspending、typed key / reified 和 channel 默认/显式来源入口。
- Android 新增 onEventSuspending、FlowEventBus.subscribeEventSuspending、collectEventSuspending 以及 collectEventFrom / collectEventFromSuspending，支持 root、shared 和 owned 来源。
- 增加独立消费者、旧 core 二进制回归和 Android 公开 JVM 符号检查；Compose、Navigation 与实际 NavHost 仍为验证示例，不发布新坐标。

## 接入与迁移

使用：

```kotlin
implementation("io.github.logan0817:flowbus:1.1.0")
// 非 Android：
implementation("io.github.logan0817:flowbus-core:1.1.0")
```

旧 collect、onEvent、collectEvent 与发送入口继续保留。需要在回调中直接调用挂起业务时，选择独立命名的新入口：

```kotlin
viewLifecycleOwner.onEventSuspending<RefreshHomeEvent> { event ->
    viewModel.refreshSuspending(event.source)
}
```

事件类型和 refreshSuspending 由业务定义。单订阅串行处理，不自动采用 collectLatest；普通回调异常交给来源的 logger/errorHandler，默认 Rethrow 终止收集，处理器正常返回才继续。CancellationException 直接传播。通用 collectEventSuspending(flow) 的异常直接传播，不自动应用总线错误策略。

生命周期停止请求取消当前回调。同一次生命周期重启等待旧回调结束，阻塞或 NonCancellable 工作不能被强制停止，取消不回滚副作用。显式实例来源由调用方负责关闭，owner 销毁只取消订阅。

普通事件无人订阅时不留存；注册不等于底层订阅就绪。emit/awaitSend 仍遵循溢出策略，SUSPEND 仅在存在活跃订阅者时可背压。发送完成或 accepted=true 不代表业务处理成功。sticky replay 与实时广播独立管理，DROP_LATEST 下未进入实时缓冲的新值仍可能成为后订阅 replay；需要持久化的工作使用业务状态或数据库。

## 兼容性与验证

基础线保持 Kotlin 1.9.25、Coroutines 1.8.1、Lifecycle 2.6.2、AGP 8.6.1、Gradle 8.7、构建 JDK 17、Java 8 字节码、minSdk 21 / compileSdk 35 / targetSdk 35。已测消费者组合、解析依赖及边界见[兼容性评估](next-version-plan.md#兼容性评估)，不以库自身构建版本强制要求全部使用者升级。

发布清单覆盖 apiCheck、JVM 单测、release lint/R8、POM/module metadata、license/developer、verifyMavenLocalArtifacts、verifyMavenLocalCoreConsumer、verifyMavenLocalBinaryCoreConsumer、verifyMavenLocalConsumer 和独立 Compose 消费者。设备入口为 :library-android:connectedDebugAndroidTest 与 :app:connectedReleaseAndroidTest，示例设备验收走 release R8。

已发布 1.0.7 core 的稳定 JVM 描述符与旧消费者运行回归有独立证据；Android 公开快照成员保留，原始报告记录一处非 PublishedApi internal 方法变化。静态符号与选定消费者测试不等于完整 ABI 认证。

**发布门禁已通过：[最终 main CI](https://github.com/logan0817/FlowBus/actions/runs/38207876160) 对应 `efff42874b6c28a6074f72716b31acfcf6fa66b7`，包含 API 21 库层、minified Maven 消费者设备验收及完整检查。** 正式 Maven Central 的 10 个制品及 10 个签名与已验收候选摘要一致，GPG 签名全部有效；从正式仓库解析的独立 core 消费者通过 Java 8 普通/sticky、scope 和挂起订阅验证，独立 Android 消费者通过 release R8 构建。报告入口见[发布清单](release-checklist.md)。旧完整工具链、AGP 9.x、进程死亡恢复、深链接和任意导航动画尚未认证，Compose 新模块不在本次发布范围。

## 发布与回退

本次正式批次为 `io.github.logan0817-FlowBus-1.1.0`，deployment ID 为 `24a88a3e-fc49-479e-8605-140989c944f2`，Portal 状态为 `PUBLISHED`。两个正式坐标均可从 Maven Central 解析；批次名称不改变 Maven 坐标。后续版本按[发布清单](release-checklist.md)分别执行产物检查、上传、签名验收和 Central release，远程 dry-run 只检查任务图。

消费者可回退到已发布 1.0.7；使用新增 API 的调用方须同时恢复旧调用。不要覆盖或重新上传已经发布的版本。
