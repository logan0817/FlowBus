# FlowBus 1.3.0 发布说明

[English](en/release-notes-1.3.0.md)

正式版本 **1.3.0** 已于 2026-11-10 发布到 Maven Central，包含 core、Android 与可选 Compose 三个坐标。以下先记录正式发布证据，开发版历史结果保留原始版本与限制。

## 使用者变化

- core 的 FlowBus、ScopedFlowBus、FlowBusScope 新增 flow/stickyFlow 和挂起收集的 onSubscribed 重载。通知对应某次底层订阅登记，每次重新收集各通知一次；它不代表 replay 或业务处理完成，也不改变缓冲、溢出与取消规则。旧方法及默认参数桥保留。
- Android 显式来源 collectEventFromSuspending 新增通知重载，沿用来源错误策略和 repeatOnLifecycle。每次生命周期重新活跃并建立订阅时重新通知；hook 抛错直接终止该次收集，不伪装为事件回调异常。
- FlowBusJava 提供 Class/typed channel 工厂、全局/owner 普通与 sticky 发送、发送结果、生命周期订阅及取消句柄。Java lambda 无须返回 Unit；取消请求和处理结束分别用 isCancelled/isCompleted 判断。默认 STARTED/Main.immediate，来源 owner 与接收生命周期保持独立。
- 新增可选坐标 flowbus-compose，提供明确来源、key 和 owner 的 FlowBusEffect。更新回调不重订阅；来源、key、owner、生命周期阈值或 sticky 选择改变时重建。离开组合或非活跃时请求取消，不关闭注入来源。无 Material、Navigation 或 DI 依赖，现有两个基础坐标不传递 Compose。
- inspectChannel(key) 返回独立 FlowBusChannelSnapshot，包含实际普通/sticky 配置、Flow 是否存在与各自活跃订阅数。core 未知查询不创建 store、绑定或 Flow；Android 静态入口首次获取来源可按原规则创建 bus，但不创建未知通道。旧快照数据类保持。owned scope 关闭中/关闭后拒绝查询。

## 接入与边界

在仓库配置 `mavenCentral()` 后可直接使用正式坐标；仅验证本地源码修改时使用[隔离候选流程](../CONTRIBUTING.md#本地体验候选版本)：

```kotlin
implementation("io.github.logan0817:flowbus:1.3.0")
// 仅 Compose 消费者添加：
implementation("io.github.logan0817:flowbus-compose:1.3.0")
```

新 hook 只执行短通知，可完成 CompletableDeferred，让独立生产协程继续发送。不要阻塞 hook，或在同一零缓冲 SUSPEND 通道内等待自己开始取值。通知后仍可能立即取消，DROP 策略仍可能丢值，零缓冲 tryPost 仍可能拒绝。历史 replay 在通知后处理，不能将一次性首轮信号解释为后续全部生命周期阶段永久就绪。

普通和 sticky 继续分流；同一订阅串行执行，跨订阅不承诺全局回调顺序。必须恢复或不可遗漏的结果仍使用业务状态和必要持久化，不新增 ACK、队列、继承分发、全局优先级、跨进程或自动零订阅清理。

新模块用现有 Kotlin 1.9.25 / Compose compiler 1.5.15 / runtime 1.5.4 组合，基础库 Coroutines 1.8.1、Lifecycle 2.6.2、minSdk 21 和 Java 8 字节码保持。消费者最终解析版本须看依赖图，不由直接声明推定。

## 验收入口与证据边界

现有 API、JVM、R8、Maven、Java 8、编译器矩阵、API 21/35 和发布 dry-run 门禁保留，新增正式 1.2.0 core 同字节码运行与 Android JVM 符号基线；Compose 消费者改为依赖真实候选坐标，Java 业务调用来自实际 .java 文件。

- 订阅时序测试使用可控调度和屏障，区分具体订阅、普通/sticky、来源、取消及 replay/live 交界。
- 真实 Android 消费者增加 Java/Kotlin 双向调用、生命周期重进、取消与诊断阶段。
- Compose effect 自检增加建立次数与回调更新不重订阅，原错误/取消自检保留。
- 性能对照基线转为公开 1.2.0；1.2.0 的已有结果仍保留为历史，不代表本候选性能。

正式版本的 API 21/35 与完整云端 CI 已取得独立结果，见下节；历史本地 API 36 结果仍不能替代最低版本设备证据。

## 正式发布验收（2026-11-10）

三个正式坐标为 `io.github.logan0817:flowbus-core:1.3.0`、`io.github.logan0817:flowbus:1.3.0`、`io.github.logan0817:flowbus-compose:1.3.0`。源码固定为 `299f6a7b9720aca884be3d85f7cf122c803a6e73`，采用 [main CI 42781622177](https://github.com/logan0817/FlowBus/actions/runs/42781622177) attempt 1 的受测 Maven 文件；后续安装文档提交不替换已发布制品。该轮九项检查全部成功，候选 manifest SHA-256 为 `71ae0e1a6def4fa8195be6473ec525280a5c5217a8214000662fc412052a8bee`。

本机复用现有 GPG keyring / agent 和用户级 Central token，不导出私钥或配置 GitHub 发布 secrets。签名主钥完整指纹为 `8BB25F160F45FD8919DC985A152B32281C3E9240`。固定 15 个文件及其签名和校验和生成 150 文件签名包，ZIP SHA-256 为 `1469985d5ef9097818ec81e455f7ec7d10d496f4cc2b0d280f45f43f1f0ef7b3`。批次名 `io.github.logan0817-FlowBus-1.3.0`，deployment ID `36326b76-36bb-4982-be4e-6b539478cee1`；以 `USER_MANAGED` 上传，在 `VALIDATED` 回下载 30 个制品/签名逐一核验后，对原 deployment 正式发布，最终状态为 **PUBLISHED**。

| 验证范围 | 正式版本证据 |
| --- | --- |
| main CI | 257 项 JVM 测试、三库 API、Android/Compose/示例 Release Lint、metadata、Java 8、三版旧 core 同字节码及六份符号报告、消费者 R8、JMH smoke、发布 dry-run 全通过 |
| 编译器 | Kotlin 1.8.22 / 1.9.22 / 2.0.21 / 2.1.0 消费者全通过 |
| API 21 / Android 5.0.2 x86 | 库 3 项 instrumentation；Android 7、Compose effect 11、policy 8、Navigation 7、NavHost 8 阶段通过；旧 Android runtime 另 1 项通过 |
| API 35 / Android 15 x86_64 | 库 3 项、示例 Release 19 项 instrumentation；Android 7、Compose effect 11、policy 8 阶段通过；旧 Android runtime 另 1 项通过 |
| 公开 Maven 文件 | 三坐标 150 个文件逐字节 SHA-256 与签名包一致；15 份 GPG 签名和 120 份校验和全部通过 |
| 公开 core 消费者 | 仅从 Maven Central 解析 1.3.0，真实 Kotlin/Java 调用在 Java 8 运行通过；实际加载 JAR 摘要与固定 CI 文件一致 |
| 公开 Android / Compose 消费者 | 仅从 Maven Central 解析 FlowBus 三坐标，实际依赖版本和 JAR/AAR 摘要匹配；Release R8 通过 |
| 公开依赖真机自检 | 一加 IN2020 / API 33 的 Android 7、Compose effect 11、policy 8 阶段通过；日志、同包 UI 与各自本次 runId 一致 |

| 公开主制品 | SHA-256 |
| --- | --- |
| flowbus-core-1.3.0.jar | `3e15654a40e3a071d2ca2df51efa1288587d96f407de690c30369092ad2149b4` |
| flowbus-1.3.0.aar | `729ad9a00caf20d50b659fa355f91288e247a3f2caab989730b69d6e3e56a564` |
| flowbus-compose-1.3.0.aar | `5a3cc9c7284fa54fb8ed78d543a5b433eff2b9b32016545ca5080993fcf06e2f` |

本地证据位于 `build/release-1.3.0/public-acceptance.json`、`signed-manifest.json` 及相邻回执/设备报告，均在版本发布目录中保留。真机 runId：Android `26f92615-a859-473c-84c7-fef15f7da07f`，effect `9a4890df-e12a-4e96-b71c-2f6fda03de9e`，policy `285f4fe0-ab3b-413c-8602-8d63a7ed1d8d`。APK 仅使用本地测试签名，Maven 使用上述 GPG 发布签名。

PR 验证首轮 API 35 曾有十三项窗口失焦失败，同一提交第二轮全通过；最终 main 首轮全通过。具体失焦原因未确认，原失败报告保留。静态符号检查不是完整 ABI 认证，JMH smoke 不证明性能无回退或提速；下文性能数据继续保留历史边界。

## 正式版本候选准备（2026-11-10）

准备阶段源码版本为 `1.3.0`，示例为 `versionName=1.3.0` / `versionCode=11`，测试 APK 为版本 `2 / 1.3.0`。该阶段三个坐标只在隔离 Maven 仓库准备，尚未签名上传；当时公开安装仍为 1.2.0。正式发布结果另见本轮记录。

前一开发版已随 [PR #12](https://github.com/logan0817/FlowBus/pull/12) 合并为 `311c30f249631371877e25c0cb714a5ccc4917a8`，[main CI](https://github.com/logan0817/FlowBus/actions/runs/42771676046) 九项检查全部通过。它验证的是 `1.3.0-SNAPSHOT`；以下本地性能和设备记录也保留原始版本含义，不能作为 `1.3.0` 新制品摘要或本轮设备结果。

本地已通过 257 项 JVM 测试、三个库模块的 API 检查、Android／Compose／示例应用的 Release Lint、三个坐标的 metadata、core 消费者的 Java 8 运行，以及 Android／Compose 的真实 Maven Release R8 验证。云端编译器与 API 21/35 结果以本轮 PR 及合并后的 CI 为准。

发布准备 [PR #13](https://github.com/logan0817/FlowBus/pull/13) 已合并，最终 main 提交 `299f6a7b9720aca884be3d85f7cf122c803a6e73` 的 [CI 42781622177](https://github.com/logan0817/FlowBus/actions/runs/42781622177) 九项检查全部通过。正式发布采用该轮 attempt 1 的固定文件，不在本机重新编译替换。

## 开发版历史验证（2026-10-30）

该开发版当时的证据来自未提交工作区；HEAD `26df17a` 不能单独标识新源码。`build/verification-130/source-identity.json` 记录 50 份生产源码/构建输入摘要，候选 manifest 摘要为 `529d3e6ae52262d6f105bd492c38e7cdaa3fd79df3b1ab03223968cf2445af02`。当时的本地封存目录包含历史文件，以下仅为该 SNAPSHOT 三个坐标的历史摘要：

| 候选主制品 | SHA-256 |
| --- | --- |
| core JAR | `1d7b645de2c41bd2e79c716d161373dd262abb1d079f78aecbbf57b793bf32e9` |
| Android AAR | `729ad9a00caf20d50b659fa355f91288e247a3f2caab989730b69d6e3e56a564` |
| Compose AAR | `5a3cc9c7284fa54fb8ed78d543a5b433eff2b9b32016545ca5080993fcf06e2f` |

| 验证范围 | 已取得结果 |
| --- | --- |
| JVM 单元测试 | core 181、Android 74、示例 2，共 257 项，零失败/错误/跳过 |
| API / Lint / 制品 | 三模块 API 检查；Android、Compose、示例 release Lint；三个坐标的主制品、sources/javadoc、POM/module metadata 与 Java 8 字节码检查通过 |
| Maven core 消费者 | 候选 Kotlin/Java 编译并在 Java 8 真实运行通过 |
| 旧 core 二进制 | 1.0.7 / 1.1.0 / 1.2.0 原版编译的同一字节码分别在原版和本候选 Java 8 运行通过 |
| JVM 符号 | core 631/681/723 个描述符、Android 179/213/216 个公开描述符保留；Android 1.0.7 的非公开内部差异仍单独报告，不作为完整 ABI 认证 |
| Kotlin 消费者矩阵 | 1.8.22 / 1.9.22 / 2.0.21 / 2.1.0 均完成真实 core Java 8 运行及 Android release R8；各组校验同一封存候选 |
| compileSdk 33 | Kotlin 1.8.22 消费者完成 core Java 8 运行及 Android release R8 |
| API 36 真机 Maven/R8 | Android 七阶段（含真实 Java）、Compose 十一阶段 effect 与八阶段 policy 全部通过；日志、前台同包 UI、阶段集合和各自 runId 一致 |
| 较新 Compose / NavHost | Compose 1.7.8 / Navigation 2.8.3 编译与 release R8，以及 API 36 effect 11、policy 8、NavHost 8 阶段全部通过；日志、同包 UI 与本次 runId 一致 |
| 发布入口 | `verifyCentralDeploymentName` 与 `releaseToMavenCentral --dry-run` 通过；三个坐标均进入任务图，未实际执行 clean、签名或上传 |
| instrumentation | 库与示例测试 APK 已构建；API 36 库测试包安装被系统以 `INSTALL_FAILED_USER_RESTRICTED` 拒绝，实际执行 0 项，示例测试未继续 |
| 定向性能 | 五组加稳定发送/注册反向复测，共九组 JMH 正式 3-fork 测量及三种工作量自检通过；制品/源码/报告身份已核对，结果与限制见[实测报告](../benchmarks/jvm/results/1.3.0/report.md) |

真机证据位于本地 `build/verification-130/`：Android runId `7878952c-6f92-47ec-a91b-ccb1d5f216f6`，最终默认 effect runId `6ea67ee7-6a66-4748-9803-44bca77db02c`，policy runId `e1c822d7-0c43-489f-92ac-4b0b80b6200e`。这些结果消费同一候选，APK 经测试签名；没有使用正式发布密钥。

较新组合的 runId 分别为 `ce43a7ba-8b90-417a-a6d3-6649b53c072f`、`07d840b1-39a8-4e18-8378-cfad176547d5`、`f1b29285-1175-478d-b065-715d5676e5c7`，通过证据保留在 `build/verification-130/compose-newer-*-native/`。测试页将实时结果显示在 Activity 的原生 TextView 中，以兼容 [Compose 1.7.8 无障碍委托](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.7.8/ui-android-1.7.8-sources.jar) 对仅有 UIAutomator 查询的过滤；脚本继续严格核对日志与前台同包 UI。

两组消费者完整 `releaseRuntimeClasspath` 已保存到本地 `/tmp/flowbus-130-dependencies-default.log` 和 `/tmp/flowbus-130-dependencies.log`：默认实际解析 Compose runtime/ui 1.5.4、Lifecycle 2.6.2、core-ktx 1.10.0；较新组合为 Compose runtime/ui 1.7.8、Navigation 2.8.3、Lifecycle 2.8.3、core-ktx 1.12.0；两组 Kotlin stdlib 均为 1.9.25。基础库声明仍使用 Lifecycle 2.6.2，不能将较新消费者解析结果写成基础库最低要求。

API 21/35 当前没有本地可用设备，云端 CI 尚未触发；相关入口与回归检查已保留并扩展，不能因此写成最低版本已通过。

性能数据不支持提速承诺：普通发送反向复测两版区间重叠，首次注册两轮的快慢关系反转且负载变化明显，发布前需空闲环境复测。新增 hook 整次订阅工作量额外分配约 250 B；它不是单独 hook 本体的开销。历史完整基准未覆盖，本轮没有 ART 性能结论。

## instrumentation 补验（2026-11-10）

在一加 8 Pro（IN2020、Android 13 / API 33、系统 `IN2020_13.1.0.190(CN01)`）重新安装并执行测试。两个测试 APK 新增专用 AndroidManifest，只声明测试包版本 `1 / 1.3.0-SNAPSHOT` 和标签，便于安装确认与版本核验；生产库和公开 API 未变化。安装工具匹配设备安装组件 `com.oplus.appdetail 5.26.0`，三个 APK 均安装成功且版本核验通过。

执行 `:library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all` 成功。本次 XML 报告确认库 **3 项**、示例 release **19 项**，合计 **22 项**，零失败、错误或跳过。安装报告、APK 摘要、测试 XML、构建日志和源码摘要保存在本地 `build/verification-130/instrumentation-2026-11-10/`。这次结果补齐源码 instrumentation，不作为 Maven 候选消费者或 API 21/35 的运行证据；2026-10-30 的安装失败记录保留原始含义。

## 发布前审查与注册性能补验（2026-11-10）

最终差异审查未发现可确认缺陷；core 原有 446 个、Android 原有 117 个公开成员均保留。检查覆盖订阅时序、取消、通道查询、Java/Compose 调用方、三个发布坐标、候选复用及 CI 失败/跳过路径。静态审查不替代设备或云端执行。

首次注册按基线 → 候选 → 候选 → 基线补跑四组正式 3-fork 测量，两轮均未复现候选吞吐偏低；两版分配均约 1000 B/次。基线仍存在波动，不宣称提速或受控环境认证，详细读数及身份见[性能补验](../benchmarks/jvm/results/1.3.0/report.md#首次注册补验2026-11-10)。当前本地没有 API 21/35 镜像、AVD 或 SDK 命令行工具，相关设备验收仍需本轮云端 CI 结果。

## 回退

新能力均为增加接口，已有调用无需迁移。使用新 API 的消费者若回退到 1.2.0，应移除通知重载和 Java 入口；Compose 用户恢复自行维护的旧接入代码并移除新坐标。旧默认发送、sticky、所有权与异常策略保持。
