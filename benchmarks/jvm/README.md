# FlowBus JVM 性能基准

独立 Gradle 工程，不属于根项目 settings，不进入生产依赖或 Maven 发布产物。对照为固定 SHA-256 的 Maven Central 1.1.0、隔离仓库候选 1.2.0 和原生 SharedFlow。Kotlin 1.9.25、Coroutines 1.8.1、JMH 1.37、JDK 17 固定，fork heap 为 -Xms512m -Xmx512m；JMH 采用 [GPLv2 with Classpath Exception](https://github.com/openjdk/jmh/blob/1.37/LICENSE)，仅为开发测量工具，FlowBus 发布许可证仍为 MIT。

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

所有 Gradle 调用串行，不能同时运行多个正式基准或构建。使用 Android Studio 时，与 IDE 复用相同的 Gradle JDK、Android SDK 和 Gradle user home。baseline 单独运行已发布 1.1.0 JAR；candidate 使用 `build/candidate-maven/.../flowbus-core/1.2.0/flowbus-core-1.2.0.jar`，shared 同一依赖版本但收发直接使用 MutableSharedFlow。baseline 的新配置场景不执行。构建检查已发布 JAR 摘要，不允许用当前源码重建旧基线。

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
| configuredRegistration | 仅候选：新 bus + 通道覆盖配置 + 首次 Flow | 独立测量，不与旧版本功能等价比较 |
| scope | 同名 scope 创建、sticky 写入/消费、关闭、inspector 断言无登记 | 1 / 1；SharedFlow 无此所有权功能，不作对照 |

JMH Throughput 的 ops/ms 是**整批 invocation/ms**，SampleTime 的 ms/op 是整批耗时，不能直接写成单事件延迟。sent/received 辅助计数提供发送/实际收集量；收到的 ID 和计数另有强断言。GC 的 B/op 同样按整批或整个 scope/注册操作计量。

active/slow/producers 使用同一 Default dispatcher、容量、overflow 和校验工作量，直接收集在 UNDISPATCHED 的首次挂起前建立订阅，不依赖固定 delay。批次包含 runBlocking、订阅创建/取消与 ID 校验，因此反映端到端批次开销；noSubscribers/stickySend 使用预建通道测稳定发送。SUSPEND 和 DROP_OLDEST 分别测量，不把丢弃的高发送速率解释为高投递速率。无活跃 sticky 订阅者的发布/replay 与原生 SharedFlow 具有本基准所需的等价语义；不比较两者不同的 consume/clear/live DROP_LATEST 语义。

原始 JSON/log 位于 `build/reports/`。summarize.py 保留完整运行参数、置信区间、原始迭代值、GC 和辅助计数，去掉体积大的采样直方图，导出 results/current/metrics.json 与报告。环境信息含 commit、工作区 patch 摘要、制品摘要、CPU/OS/JDK 和测量时间。源码尚未提交时必须同时记录 patch 摘要；commit 不能单独标识候选。

先测正式 1.1.0，再测候选与 SharedFlow。约 10% 默认路径回退只触发人工调查，需要结合误差和复测/profile，不能作为共享 CI 硬阈值。smoke 仅证明可运行。JVM 数据不能推导 Android ART 性能，也不能据此宣布优于 EventBus/LiveEventBus。完整机器状态无法由脚本控制，报告须保留后台负载、顺序与环境限制。

## 本次结果与注册 profile

完整结果见 [实测报告](results/current/report.md)与[分析](results/current/analysis.md)。注册路径变化用既有 JDK 17 的 JFR 采样定位，不改变生产依赖：

```bash
./gradlew -p benchmarks/jvm profileRegistration -Pbenchmark.target=baseline
./gradlew -p benchmarks/jvm profileRegistration -Pbenchmark.target=candidate
```

JFR 为 1 fork、3 × 1 秒预热/测量，仅用于分配来源，不替代 3 forks 正式读数；weighted sample bytes 不是每类精确分配量。记录位于 build/profiles，提取摘要见 results/current/registration-profile.json。
