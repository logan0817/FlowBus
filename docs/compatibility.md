# FlowBus 兼容性说明

[English](en/compatibility.md) · [文档索引](README.md)

FlowBus 发布 Kotlin/JVM core 和 Android 适配两个坐标，不提供 Kotlin Multiplatform、跨进程、Compose 或 Navigation 制品。Android 坐标传递依赖同版本 core。

## 接入要求与默认依赖

Android minSdk 为 21，两个坐标均输出 Java 8 字节码。构建本仓库需要 JDK 17 与 Gradle Wrapper；库自身构建版本不等于所有消费者工具链的最低版本。依赖解析可能提升传递依赖版本，排查时应核对消费者实际依赖图。

## 1.1.0 历史矩阵

基础库保持 Kotlin 1.9.25、Coroutines 1.8.1、Lifecycle 2.6.2、AGP 8.6.1、Gradle 8.7、构建 JDK 17、Java 8 字节码及 minSdk 21 / compileSdk 35 / targetSdk 35。消费者可以使用自己的兼容工具链；库的构建配置不等于全部消费者的最低要求。

下表记录 1.1.0 的历史验证，不推导为任意组合均受支持，也不代表当前 1.2.0 工作区已验收。当前候选证据单独列于下节。

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

## 1.2.0 候选验证

候选保持基础依赖、Java 8 字节码、minSdk 21，以及旧公开构造器、copy/component 描述符。通道配置为新增 API；使用新入口后回退 1.1.0，需同时移除对应调用。

| 范围 | 已记录的候选证据 |
| --- | --- |
| core 二进制消费者 | 1.0.7、1.1.0 原版编译的 Kotlin/Java 字节码在 Java 8 运行；保留 631/681 个 JVM 描述符 |
| Android 二进制消费者 | 固定原版 AAR 符号检查、1.1.0 原版挂起/实例 class 配合候选 runtime 运行，以及 API 21 minified 设备验收 |
| 消费者工具链 | Kotlin 1.8.22/1.9.22/2.0.21/2.1.0，AGP 8.6.1/Gradle 8.7；compileSdk 33；release R8 |
| 设备 | API 21 库和 minified Maven 消费者；API 35 库/示例 instrumentation |
| 可选接入 | 默认 Compose/Navigation，以及较新 Navigation 2.8.3 + Compose 1.7.8/NavHost 消费者构建 |

受测提交和 CI 批次统一记录在 [1.2.0 发布说明](release-notes-1.2.0.md)。这些提交之后的本地文档修改不继承已有 CI 结论；合并或发布前须重新检查受影响范围和当前 head 的 CI。

## 边界与复现

描述符检查与选定消费者运行不是完整 ABI 认证。Android 静态符号不能证明所有旧路径在设备上执行。NavHost 配置重建使用同进程 retained ViewModel，不证明进程死亡恢复或 sticky 持久化。完整旧工具链和 AGP 9.x 尚未认证。

- [发布清单](release-checklist.md)：API、lint、R8、元数据、API 21/API 35 与发布任务图。
- [core 二进制消费者](../compatibility/core-binary-consumer/README.md)：固定原版编译和候选替换运行。
- [Android 消费者](../compatibility/android-consumer/README.md)：测试签名、设备六组验收、原版 AAR 符号。
- [Compose](../compatibility/compose-consumer/README.md)、[Navigation](../compatibility/navigation-consumer/README.md)、[NavHost](../compatibility/navhost-consumer/README.md)：独立消费者与命令。
- [JVM 性能分析](../benchmarks/jvm/results/current/analysis.md)：实测成本与边界，不包含 Android ART 或跨框架排名。
