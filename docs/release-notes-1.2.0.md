# FlowBus 1.2.0 发布说明（候选）

[English](en/release-notes-1.2.0.md)

当前为已提交并推送的实现候选，尚未上传或发布到 Maven Central。正式可安装版本仍为 1.1.0。版本配置为 VERSION_NAME=1.2.0、APP_VERSION_NAME=1.2.0、APP_VERSION_CODE=9。状态按实际验证更新，不沿用 1.1.0 的远端或设备成功结论。

## 使用者变化

- 新增 FlowBusChannelConfig，null 字段继承 bus 默认；普通与 sticky 分别配置缓冲和 overflow。
- FlowBus / ScopedFlowBus / FlowBusScope / FlowEventBus 增加 configureChannel；Android configureEventChannel 使用明确 owner 或全局来源。
- 首次配置或首次使用原子绑定类型和有效策略；相同有效配置幂等，类型冲突 IllegalArgumentException，策略冲突 IllegalStateException。失败无半注册。
- removeEvent/removeSticky/clearSticky 保留配置；完整 scope 清理释放配置；同名重开恢复 bus 默认。scope 在途配置遵循关闭保护。
- PostResult.overflowPolicy 返回对应流实际策略；inspect().config 仍是 bus 默认摘要。
- 示例 channel 页面将 Toast/Navigation 配置为不同策略，统一使用 Activity owner，Navigation 使用 emit 背压。

普通默认缓冲 64、DROP_OLDEST、sticky replay 1、默认 Rethrow、普通/sticky 分流、旧 Flow 引用和 scope 关闭语义保持。既有公开数据类的构造、copy/component JVM 描述符保留；minSdk 21、Java 8 字节码、Kotlin/Coroutines/Lifecycle 等基础依赖不升级。

用法与迁移见 [core README](../flowbus-core/README.md#120-候选按通道配置)、[Android README](../library-android/README.md#android-owner-配置)、[排障](../TROUBLESHOOTING.md)。性能工程见 [benchmarks/jvm](../benchmarks/jvm/README.md)，结果仅限 JVM，不能外推为 Android ART 性能。

## 候选验证状态

| 验证 | 当前证据 |
| --- | --- |
| core/Android/app JVM 单测 | 164 + 65 + 2 项通过，包括配置、契约、并发和 lifecycle；必要文档输入在最终检查中覆盖 |
| API、release lint 与示例构建 | apiCheck 通过，快照只有新增项；库/app release lint 及 debug/release 构建通过 |
| 真实 Maven core 新配置消费者 | 1.2.0 候选在真实 Java 8 通过 |
| Maven 元数据与 Android release R8 | 1.2.0 候选通过，包含新配置和旧挂起/实例调用；原版 1.1.0 class 在候选 JVM runtime 运行通过，API 21 minified Maven/R8 consumer 设备运行通过 |
| 1.0.7/1.1.0 未重编译 core 消费者 | 两组 Kotlin/Java 原版编译后替换候选，真实 Java 8 通过；631/681 个 JVM 描述符保留 |
| 1.1.0 Android AAR 符号 | 固定原版 AAR + 旧公开快照核验通过，无受支持符号删除；不代替旧消费者运行 |
| Kotlin/compileSdk 与可选消费者 | Kotlin 1.8.22/1.9.22/2.0.21/2.1.0、compileSdk 33 的 core Java 8/Android R8 通过；默认及较新 Compose/Navigation/NavHost R8 通过 |
| 性能 | 统一 sticky extra=0 后三组完整 JMH 共 58 组测量通过，3 forks、5 × 1 秒预热/测量；原始指标、环境和注册 JFR 分析已保存 |
| 最终 API 21/API 35、UI 与远端 CI | [CI run 24](https://github.com/logan0817/FlowBus/actions/runs/38594783249) 对应最终候选 `a374d5e078610e09fc227a3ac232d03ef1e54cf4`，全部 27 个步骤成功；API 21 实际设备为 `emulator-5554 / Android 5.0.2 (API 21)`，API 35 设备身份、instrumentation/UI 与消费者报告随同批 artifact 保存 |

设备验收入口已增加 channel 页 Activity 重建回归，并为 API 35 记录/断言真实设备身份与启动状态；三项 mocked-ADB 驱动回归及 Shell 语法检查通过。`:app:assembleReleaseAndroidTest`（含 R8）和 `:library-android:assembleDebugAndroidTest` 通过，相关 release lint、231 项 JVM 测试和 API 检查通过。最终候选 `a374d5e078610e09fc227a3ac232d03ef1e54cf4` 的 [CI run 24](https://github.com/logan0817/FlowBus/actions/runs/38594783249) 已完成 API 21/API 35 设备门禁；同批报告 artifact 为 `flowbus-verification-138cb3c2a72e3e50d84d0a5b155a5adab333217a`（digest `sha256:f499b0a89dade6fa0457599ddc1e0e3500da3b52f929620d8aadd5c57f0b3d89`）。

1.0.7 Android 公开快照的受支持描述符保留；raw JVM 比较仍记录 1 处 internal `createFlowBusConfig$library_android_release` 移除，这是已发布 1.1.0 相对 1.0.7 的既有差异。保留该报告，不将静态符号检查描述为完整 ABI 认证。发布任务图 dry-run 与部署名称检查通过，已创建候选草稿 PR；未上传或发布 Maven Central 制品。

性能见[完整报告](../benchmarks/jvm/results/current/report.md)和[分析与限制](../benchmarks/jvm/results/current/analysis.md)。候选普通无订阅发送吞吐约为 1.1.0 的 98.7%，sticky 发布约 99.8%，误差区间重叠；未确认默认稳定发送路径出现超出误差的约 10% 回退。首次注册吞吐约为基线的 85.5%，分配从 888 增至 1000 B/op，明确保留这一代价。原生 SharedFlow 在等价负载中开销更低；不宣称跨框架或 Android 性能优势。

复现命令与所有门禁见[发布清单](release-checklist.md)。库层设备测试执行 `:library-android:connectedDebugAndroidTest`，示例执行经过 R8 的 `:app:connectedReleaseAndroidTest`。构建、静态符号与 mock driver 结果不等于设备通过。

## 回退

未使用新 API 的消费者可回退到已发布 1.1.0；使用 configureChannel 的消费者需同时回退调用，并恢复 bus 级策略或拆分来源实例。不能覆盖已发布坐标。提交、推送、远端 CI 写入、上传、Portal release 分别判断授权。
