# FlowBus 1.2.0 发布说明

[English](en/release-notes-1.2.0.md)

1.2.0 已于 2026-10-15 正式发布到 Maven Central。版本配置为 VERSION_NAME=1.2.0、APP_VERSION_NAME=1.2.0、APP_VERSION_CODE=9。下方验证记录标明受测提交，不能替代后续修改的当前 head 验收。

## 使用者变化

- 新增 FlowBusChannelConfig，null 字段继承 bus 默认；普通与 sticky 分别配置缓冲和 overflow。
- FlowBus / ScopedFlowBus / FlowBusScope / FlowEventBus 增加 configureChannel；Android configureEventChannel 使用明确 owner 或全局来源。
- 首次配置或首次使用原子绑定类型和有效策略；相同有效配置幂等，类型冲突 IllegalArgumentException，策略冲突 IllegalStateException。失败无半注册。
- removeEvent/removeSticky/clearSticky 保留配置；完整 scope 清理释放配置；同名重开恢复 bus 默认。scope 在途配置遵循关闭保护。
- PostResult.overflowPolicy 返回对应流实际策略；inspect().config 仍是 bus 默认摘要。
- 示例 channel 页面将 Toast/Navigation 配置为不同策略，统一使用 Activity owner，Navigation 使用 emit 背压。
- 文档新增中英文索引、独立兼容性指南和集中候选安装步骤；模块按功能导航，贡献、安全与支持政策同步整理，并保留旧文档链接。

普通默认缓冲 64、DROP_OLDEST、sticky replay 1、默认 Rethrow、普通/sticky 分流、旧 Flow 引用和 scope 关闭语义保持。既有公开数据类的构造、copy/component JVM 描述符保留；minSdk 21、Java 8 字节码、Kotlin/Coroutines/Lifecycle 等基础依赖不升级。

用法与迁移见 [core README](../flowbus-core/README.md#channel-configuration)、[Android README](../library-android/README.md#android-owner-配置)、[排障](../TROUBLESHOOTING.md)。性能工程见 [benchmarks/jvm](../benchmarks/jvm/README.md)，结果仅限 JVM，不能外推为 Android ART 性能。

## 验证证据

候选受测快照如下：

| 快照 | 证据 |
| --- | --- |
| `56812f8c7f24520480e03a44b6816fdc950c9e59` | [CI run 26](https://github.com/logan0817/FlowBus/actions/runs/38601076450) 全部步骤成功，覆盖 API 21/API 35 设备、制品消费者、二进制兼容与发布任务图 |
| Run 26 artifact | `flowbus-verification-d6c7bae559732e3810a6303a8b58cc20373e58a2`，digest `sha256:8a4fcbb1d835432b5a9fe5d67c2d1aaed0c1457c8ee15e514be309dcc635e366` |

artifact 名使用 PR 合成合并 SHA，run 的 head SHA 对应分支快照，报告保留 14 天。后续文档整理须完成对应本地验证和当前 head 的 CI，不能沿用 Run 26 作为新改动通过的证据。

| 范围 | 快照结果 |
| --- | --- |
| core/Android/app JVM 测试 | 164/65/2 项通过，覆盖配置、投递、并发、生命周期和发布就绪检查 |
| API/lint/构建 | API 快照仅新增；apiCheck、release lint、示例 debug/release 构建通过 |
| Maven 消费者 | Java 8 core、Android/Compose release R8，以及 POM/module metadata 通过 |
| 旧二进制 | 1.0.7/1.1.0 core 原版字节码替换候选运行，保留 631/681 个描述符；1.1.0 Android 挂起/实例 class 配合候选 runtime 运行 |
| 工具链 | Kotlin 1.8.22/1.9.22/2.0.21/2.1.0、compileSdk 33，以及可选 Navigation/NavHost 消费者构建通过 |
| 设备 | API 21 库和 minified Maven 消费者、API 35 库/示例 instrumentation 通过，包含 channel 页重建 |
| JVM 基准 | 三组完整测量，共 58 组场景/模式；3 forks，5 × 1 秒预热/测量；CI smoke 验证可运行性 |

1.0.7 Android 公开快照描述符保留；原始比较仍记录 1.1.0 已引入的 internal `createFlowBusConfig$library_android_release` 移除。静态符号和选定消费者不等于完整 ABI 认证，边界见[兼容性说明](compatibility.md)。

## 性能

性能见[完整报告](../benchmarks/jvm/results/current/report.md)和[分析与限制](../benchmarks/jvm/results/current/analysis.md)。候选普通无订阅发送吞吐约为 1.1.0 的 98.7%，sticky 发布约 99.8%，误差区间重叠；未确认默认稳定发送路径出现超出误差的约 10% 回退。首次注册吞吐约为基线的 85.5%，分配从 888 增至 1000 B/op，明确保留这一代价。原生 SharedFlow 在等价负载中开销更低；不宣称跨框架或 Android 性能优势。

复现命令与所有门禁见[发布清单](release-checklist.md)。库层设备测试执行 `:library-android:connectedDebugAndroidTest`，示例执行经过 R8 的 `:app:connectedReleaseAndroidTest`。构建、静态符号与 mock driver 结果不等于设备通过。

## 正式发布验收（2026-10-15）

- 合并提交 `5e35694fc52e6ab10f10a54387147cfe2ccf17c5` 的 [main CI run 28](https://github.com/logan0817/FlowBus/actions/runs/38679806444) 通过，覆盖上述单测、API/lint、消费者、兼容性、API 21/API 35 设备与发布任务图门禁。本节记录正式发布，前述 Run 26 保留为候选阶段证据。
- Central 批次 `io.github.logan0817-FlowBus-1.2.0`、deployment ID `498daa48-c148-4b71-91bd-acf0b2111dc9` 已返回 `PUBLISHED`。两个坐标均已从公开 Maven Central 实际下载。
- 公开仓库的 100 个文件与已验收签名包逐字节一致；10 份 GPG 签名与 80 份 MD5/SHA-1/SHA-256/SHA-512 校验和均通过。签名主密钥指纹为 `8BB25F160F45FD8919DC985A152B32281C3E9240`。
- 独立 core 工程仅以 Maven Central 为依赖仓库，核对实际加载 JAR 的版本与 SHA-256，并在 Java 8 上运行 Kotlin/Java 消费者。普通/sticky 投递、消费清理、scope 所有权、挂起回调与新通道配置均通过。
- 独立 Android 工程从公开仓库解析两个 1.2.0 坐标并核对 SHA-256，`:app:assembleRelease` 经过 R8 构建成功。此项是正式制品的构建验收；设备结果来自上述 main CI，不将本次构建描述为再次设备运行。

| 正式制品 | SHA-256 |
| --- | --- |
| [flowbus-core-1.2.0.jar](https://repo.maven.apache.org/maven2/io/github/logan0817/flowbus-core/1.2.0/flowbus-core-1.2.0.jar) | `525c10846d00e7a41f932ac0ce45dba47b7b8b1da58bc68266e33df4a4b974f2` |
| [flowbus-1.2.0.aar](https://repo.maven.apache.org/maven2/io/github/logan0817/flowbus/1.2.0/flowbus-1.2.0.aar) | `c10b2978c3ebdf69ea97ed9112c212fa65cf9b5744da0ff0b5b8823c8c1819bd` |

正式 AAR 与 main CI 归档的 AAR 摘要相同；正式 core JAR 与 main CI 归档摘要不同。两个 sources JAR 中的 36 个 Kotlin 源文件与合并后的 main 逐字节一致；本次签名 core JAR 已单独通过 Java 8 消费者、1.0.7/1.1.0 原版消费者的同字节码替换运行及 631/681 个描述符检查，公开下载与该受测 JAR 一致。未将 main CI 的 core JAR 摘要当作正式 JAR 的验收依据。

## 回退

未使用新 API 的消费者可回退到已发布 1.1.0；使用 configureChannel 的消费者需同时回退调用，并恢复 bus 级策略或拆分来源实例。不能覆盖已发布坐标。提交、推送、远端 CI 写入、上传、Portal release 分别判断授权。
