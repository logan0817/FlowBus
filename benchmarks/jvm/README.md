# FlowBus JVM 性能基准

独立 Gradle 工程，不属于根项目 settings，不进入生产依赖或 Maven 发布产物。当前对照为固定 SHA-256 的 Maven Central **1.2.0**、隔离仓库 **1.3.0** 发布候选和原生 SharedFlow。Kotlin 1.9.25、Coroutines 1.8.1、JMH 1.37、JDK 17 固定，fork heap 为 -Xms512m -Xmx512m；JMH 采用 [GPLv2 with Classpath Exception](https://github.com/openjdk/jmh/blob/1.37/LICENSE)，仅为开发测量工具，FlowBus 发布许可证仍为 MIT。`results/current/` 目前仍保存 **1.2.0 对照 1.1.0 的历史完整报告**，不代表本开发版的性能结果。

## 运行

先按项目已有 JDK/SDK/Gradle 环境发布候选，不安装新工具、不清理共享缓存：

```bash
./gradlew publishToMavenLocal "-Dmaven.repo.local=$PWD/build/candidate-maven"
# 三组均先自检；smoke 为 1 fork、1 次 100ms 预热、1 次 100ms 测量。
./gradlew -p benchmarks/jvm selfCheck jmh -Pbenchmark.target=baseline -Pbenchmark.smoke
./gradlew -p benchmarks/jvm selfCheck jmh -Pbenchmark.target=candidate -Pbenchmark.smoke
./gradlew -p benchmarks/jvm selfCheck jmh -Pbenchmark.target=shared -Pbenchmark.smoke
# 正式测量：3 forks，每 fork 5 x 1s 预热 + 5 x 1s 测量，GC profiler。
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=baseline
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=candidate
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=shared
python3 benchmarks/jvm/summarize.py
```

同一工作区的 Gradle 调用串行，不能同时运行多个正式基准或构建。CI 在独立 `consumers` job 内依次运行三组默认 smoke，再单独选择候选 SubscriptionBenchmark 生成 `subscription-smoke.json`；候选从本次 `prepare` artifact 下载并校验，不重新发布。其余编译器/设备使用独立工作区并行。基准报告位于该 job 的 `flowbus-verification-consumers-<workflow SHA>-<attempt>`，候选一致性和收集方式见[发布清单](../../docs/release-checklist.md#ci-任务关系与候选边界)。使用 Android Studio 时，与 IDE 复用相同的 Gradle JDK、Android SDK 和 Gradle user home。baseline 单独运行正式 1.2.0 JAR，SHA-256 固定为 `525c10846d00e7a41f932ac0ce45dba47b7b8b1da58bc68266e33df4a4b974f2`；candidate 使用 `build/candidate-maven/io/github/logan0817/flowbus-core/1.3.0/flowbus-core-1.3.0.jar`（版本读取根 gradle.properties），shared 依赖相同但收发直接使用 MutableSharedFlow。不允许用当前源码重建正式基线。

默认筛选：shared 仅 `BusBenchmark`，baseline 为 `BusBenchmark|BusLifecycleBenchmark`，candidate 为这两类加 `CandidateConfigBenchmark`。默认任务保留原完整报告范围，不自动混入仅候选支持的 `SubscriptionBenchmark`。新参数均为可选：`benchmark.filter` 替换类名正则，`benchmark.scenarios` 以逗号选择该类支持的 scenario，`benchmark.report` 选择 `build/reports/<名称>.json`，避免定向报告覆盖完整数据。仅包含所选类真实参数的组合才有效。

```bash
# 默认路径与注册的定向正式对照；不带 smoke，仍使用 3 forks 的正式参数。
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=baseline '-Pbenchmark.filter=.*BusBenchmark.*' -Pbenchmark.scenarios=noSubscribers,stickySend -Pbenchmark.report=baseline-stable-1.2.0
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=candidate '-Pbenchmark.filter=.*BusBenchmark.*' -Pbenchmark.scenarios=noSubscribers,stickySend -Pbenchmark.report=candidate-stable-1.3.0
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=baseline '-Pbenchmark.filter=.*BusLifecycleBenchmark.*' -Pbenchmark.scenarios=registration -Pbenchmark.report=baseline-registration-1.2.0
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=candidate '-Pbenchmark.filter=.*BusLifecycleBenchmark.*' -Pbenchmark.scenarios=registration -Pbenchmark.report=candidate-registration-1.3.0
# 新 hook 仅在候选中与同工作量的普通订阅比较，不能在旧版 runtime 上运行。
./gradlew -p benchmarks/jvm jmh -Pbenchmark.target=candidate '-Pbenchmark.filter=.*SubscriptionBenchmark.*' -Pbenchmark.scenarios=plain,hook -Pbenchmark.report=candidate-subscription-1.3.0
```

2026-10-30 使用当时的 **1.3.0-SNAPSHOT** 完成五组以及稳定发送/首次注册的两组反向复测，共九组定向正式测量，见 [开发版定向报告](results/1.3.0/report.md)。这些历史测量不验证当前 `1.3.0` 制品；上方命令按当前版本生成新结果。工作站负载未受控，注册吞吐的快慢关系在复测中反转，不能据此宣称提速或完全无回退。不要将多个定向结果拼成完整三组基准。

JMH 成功后生成同名 `.identity.json`，绑定报告摘要、实际 JAR、源码与基准构建脚本。`summarize.py` 在写入结果前验证三份完整报告及身份；缺少标记、输入变化或摘要不符均拒绝。旧无标记报告保留为历史，不能手工补标记后改称当前测量。

## 场景与读数

| 场景 | 配置 / 工作量 | 每次 invocation 的发送 / 实际接收 |
| --- | --- | --- |
| noSubscribers | 默认 DROP_OLDEST、normal 容量 64，连续发送 128 个 ID | 128 / 0；是调用开销，不是投递吞吐 |
| active1 / active8 | normal 64、SUSPEND，1 或 8 个订阅者，128 个 ID | 128 / 128 或 1024 |
| slowSuspend | 同配置，首个回调暂停；填满 64 个位置并断言下一次 emit 被背压 | 128 / 128 |
| slowDrop | normal 64、DROP_OLDEST，首个在途事件 -1 被暂停后发送 128 个 ID | 129 / 65；核对 -1 与最后 64 个 ID |
| stickySend | replay 1、extra 0、DROP_OLDEST，无活跃订阅者 | 128 / 0；仅发布调用 |
| stickyReplay | 同配置，发布后新订阅取最近 ID | 1 / 1 |
| producers | normal 64、SUSPEND，4 个 Default 生产者各发送 32 个不同 ID | 128 / 128；核对完整 ID 集合 |
| registration | 新 bus + 首次 normal Flow 注册 | 独立测量，不计发送 |
| configuredRegistration | 仅候选：新 bus + 通道覆盖配置 + 首次 Flow | 独立测量；1.2.0 已有配置 API，但当前矩阵未设该项对照 |
| scope | 同名 scope 创建、sticky 写入/消费、关闭、inspector 断言无登记 | 1 / 1；SharedFlow 无此所有权功能，不作对照 |
| plain / hook | 仅候选 SubscriptionBenchmark：预建同一零缓冲 SUSPEND 通道，每次订阅、通知同步、独立生产者发送、校验与取消 | 1 / 1；比较有无实际订阅通知的整次操作成本 |

JMH Throughput 的 ops/ms 是**整批 invocation/ms**，SampleTime 的 ms/op 是整批耗时，不能直接写成单事件延迟。sent/received 辅助计数提供发送/实际收集量；收到的 ID 和计数另有强断言。GC 的 B/op 同样按整批或整个 scope/注册操作计量。

active/slow/producers 使用同一 Default dispatcher、容量、overflow 和校验工作量，直接收集在 UNDISPATCHED 的首次挂起前建立订阅，不依赖固定 delay。批次包含 runBlocking、订阅创建/取消与 ID 校验，因此反映端到端批次开销；noSubscribers/stickySend 使用预建通道测稳定发送。SUSPEND 和 DROP_OLDEST 分别测量，不把丢弃的高发送速率解释为高投递速率。无活跃 sticky 订阅者的发布/replay 与原生 SharedFlow 具有本基准所需的等价语义；不比较两者不同的 consume/clear/live DROP_LATEST 语义。

SubscriptionBenchmark 两组都使用相同 Deferred/发送/取消工作量。plain 仅限直接未组合的 Flow，以 UNDISPATCHED 到首次挂起点建立订阅；hook 使用实际登记后的通知。结果包含 runBlocking、订阅建立、协程调度、一次发送接收和取消，不能称为 hook 本身的单次纳秒开销，更不能当成投递或 ACK 保证。注册与稳定发送另用相应场景测量。

原始 JSON/log 位于 `build/reports/`。summarize.py 只接受默认三组的完整正式输出（baseline 20、candidate 22、shared 16 个场景/模式，均 3 forks、5 × 1s 预热/测量），拒绝缺失/重复、smoke 或错误 adapter；定向测量和 SubscriptionBenchmark 不进入这份完整报告。它保留参数、置信区间、原始迭代值、GC 和辅助计数，去掉大的采样直方图，导出 results/current/metrics.json 与报告。只有确实完成当前全部对照，并另行归档保留现有历史证据后，才能运行并更新 results/current。环境信息含 commit、工作区 patch 摘要、制品摘要、CPU/OS/JDK 和时间；未提交源码不能仅用 commit 标识。

先测正式 1.2.0，再测候选与 SharedFlow。约 10% 默认路径回退只触发人工调查，需要结合误差和复测/profile，不能作为共享 CI 硬阈值。smoke 仅证明可运行。JVM 数据不能推导 Android ART 性能，也不能据此宣布优于 EventBus/LiveEventBus。完整机器状态无法由脚本控制，报告须保留后台负载、顺序与环境限制。

## 历史完整结果与注册 profile

[实测报告](results/current/report.md)、[分析](results/current/analysis.md)、[环境](results/current/environment.json)记录 1.2.0 对照 1.1.0 的已测快照，保留原始含义，不用于证明 1.3.0 性能。当前配置下重新定位注册开销时使用以下入口，不改变生产依赖：

```bash
./gradlew -p benchmarks/jvm profileRegistration -Pbenchmark.target=baseline
./gradlew -p benchmarks/jvm profileRegistration -Pbenchmark.target=candidate
```

JFR 为 1 fork、3 × 1 秒预热/测量，仅用于分配来源，不替代 3 forks 正式读数；weighted sample bytes 不是每类精确分配量。新记录位于 build/profiles；results/current/registration-profile.json 仍是历史 1.2.0/1.1.0 采样，不应与当前配置下的新运行混用。
