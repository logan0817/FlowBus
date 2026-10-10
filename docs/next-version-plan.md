# 1.1.0 范围与兼容性评估

[English](en/next-version-plan.md)

1.1.0 是尚未发布的候选版本。此文档记录版本范围、兼容性证据和后续扩展条件；具体发布步骤见[发布清单](release-checklist.md)，使用方式见模块 README。

## 本次版本范围

| 范围 | 决策 |
| --- | --- |
| API 21–23 平台兼容 | 替换 core 的 ConcurrentHashMap.compute* 调用，保留原子更新和旧 store 清理隔离；不要求消费者新增 desugaring 配置 |
| scope 关闭 | closeSuspending 等待可取消，维持 1.0.7 状态语义；显式永久关闭请求不会因等待取消、超时或中断而丢失 |
| core 挂起订阅 | collectSuspending / collectStickySuspending 与 channel 入口；单订阅串行处理，沿用来源错误策略和 dispatcher |
| Android 接入 | onEventSuspending、collectEventSuspending、collectEventFrom / collectEventFromSuspending；显式来源由调用方管理，不随 owner 销毁而关闭 |
| 投递契约 | 普通事件无人订阅不留存；发送完成不代表业务成功；sticky replay 与实时广播独立管理 |
| 扩展示例 | Compose、Navigation 与实际 NavHost 仅作为独立消费者 fixture，不发布新模块或新增生产依赖 |

## 兼容性评估

基础库保持 Kotlin 1.9.25、Coroutines 1.8.1、Lifecycle 2.6.2、AGP 8.6.1、Gradle 8.7、构建 JDK 17、Java 8 字节码及 minSdk 21 / compileSdk 35 / targetSdk 35。消费者可以使用自己的兼容工具链；库的构建配置不等于全部消费者的最低要求。

以下结果来自本地候选实现的独立验证，不能推导为任意版本组合均受支持。版本整理后的最终产物仍须按发布清单验收。

| 已测组合 | 结果与边界 |
| --- | --- |
| Kotlin 1.8.22 / 1.9.22 / 1.9.25 / 2.0.21 / 2.1.0 消费者，AGP 8.6.1 / Gradle 8.7 | core/Android 编译、真实 JDK 8 core 运行、Android release R8 通过；实际 stdlib 最低解析为 1.9.25，不能理解为降低发布依赖版本 |
| Kotlin 1.8.22 + compileSdk 33，AGP 8.6.1 | Android 编译、AAR metadata 与 R8 通过；完整依赖图的最高 minCompileSdk 为 33 |
| AGP 7.2.2 / Gradle 7.4.2 / Kotlin 1.8.22 / JDK 17 实验 | 编译与 R8 成功，但 lint 存在 Kotlin metadata 解析诊断，不能认证完整旧工具链 |
| 已发布 1.0.7 core 二进制消费者 | 同一 Kotlin/Java 字节码在原版与候选上真实 JDK 8 运行通过；46 个稳定类的 631 个 JVM 描述符保留 |
| 已发布 1.0.7 Android AAR 静态符号 | 公开快照的 88 个成员保留；原始比较保留一处非 PublishedApi Kotlin internal 方法变化，详见 Android 消费者说明 |
| API 24 设备 | 库 instrumentation 3 项、示例 release instrumentation 17 项、普通 Maven 消费者 4 阶段通过 |
| Compose 1.5.4 / Lifecycle 2.6.2 | 编译/R8、API 24 十阶段交互、九阶段 effect、八阶段错误与取消自检通过 |
| Compose 1.7.8 / Lifecycle 2.8.3 | 同类独立验证通过；新版消费者实际提高 Lifecycle，不影响基础坐标 |
| Navigation 2.4.1 / Compose 1.5.4，以及 2.8.3 / 1.7.8 | 两组 API 24 自定义 Navigator 七阶段、实际 NavHost 八阶段通过；新版 NavHost 覆盖默认动画生命周期阈值，两组覆盖 Activity 配置重建 |

二进制验证不是完整 ABI 认证；Android 静态符号不等于旧消费者字节码在设备上运行。NavHost 配置重建使用同一进程的 retained ViewModel，不能当作进程死亡恢复或 sticky 持久化。

## 发布门禁与验证入口

API 21 是当前最低支持目标，必须补齐库层 instrumentation 和 minified Maven 消费者设备验收。本机没有 API 21 image，API 24 通过及 D8 静态检查均不能替代该门禁。CI 已配置相应任务，但远端尚未运行。

- [发布清单](release-checklist.md)：基础测试、API、lint、R8、元数据、最低系统与发布任务图。
- [core 二进制消费者](../compatibility/core-binary-consumer/README.md)：原版编译、候选替换运行及 JVM 符号验证。
- [Android 消费者](../compatibility/android-consumer/README.md)：测试签名、四阶段设备自检及 AAR 符号检查。
- [Compose](../compatibility/compose-consumer/README.md)、[Navigation](../compatibility/navigation-consumer/README.md)、[实际 NavHost](../compatibility/navhost-consumer/README.md)：扩展组合与复现命令。

## 后续扩展条件

1. API 21 与远端 CI 通过后，才能完成当前版本的最低系统支持验收。
2. 完整旧工具链和 AGP 9.x 尚未认证，后续应按实际消费者需求选择矩阵，不能仅凭单次编译扩大承诺。
3. 独立 Compose 模块需要先明确维护范围和版本配对策略；当前保留示例，不预先增加公共 API 或传递依赖。
4. 进程死亡恢复、深链接、任意导航动画及异常后的完整 NavHost UI 不在本次已验证范围。事件持久化应由业务状态或持久化任务机制承担。
