# 1.3.0 开发候选定向 JVM 测量

测量日期：2026-10-30。正式 1.2.0 对照未发布的 1.3.0-SNAPSHOT；本报告仅覆盖所列场景，不是完整三组基准，也不包含 ART 或跨框架排名。

原始参数、置信区间、迭代读数和 GC/接收计数见 [metrics.json](metrics.json)，制品/源码/报告身份、执行顺序和机器负载见 [environment.json](environment.json)。原始含采样直方图的 JMH JSON 仍在本地 `benchmarks/jvm/build/reports/`。历史 `results/current/` 未修改。

环境：Apple M1 / macOS-26.5.2-arm64-arm-64bit；JDK 17.0.18、Kotlin 1.9.25、Coroutines 1.8.1、JMH 1.37、heap 512m。所有行均为 3 forks，每 fork 5 × 1s 预热和 5 × 1s 测量；误差为 JMH 默认 99.9% 区间。

所有 9 组报告均通过 sidecar 身份校验，绑定同一候选 core SHA-256 `1d7b645de2c41bd2e79c716d161373dd262abb1d079f78aecbbf57b793bf32e9` 与正式 1.2.0 SHA-256 `525c10846d00e7a41f932ac0ce45dba47b7b8b1da58bc68266e33df4a4b974f2`。baseline、candidate、shared 工作量自检通过；新增订阅场景的每次实际发送/接收 ID 另有断言。

## 读数

ops/ms 是完整 invocation/ms；稳定发送每次调用发送 128 条且无订阅者，接收数为 0，不表示投递吞吐。首次注册每次新建 bus 并取得 normal Flow。plain/hook 每次在已有零缓冲 SUSPEND 通道建立订阅、独立生产者发送一条、核对 ID 与计数后取消，发送/接收均为 1。B/op 同样按完整操作计量。

| 组别 | 场景 | 吞吐 ops/ms ± 误差 | 采样整次 ms/op ± 误差 | 吞吐组 B/op |
| --- | --- | --- | --- | --- |
| baseline-stable-1.2.0 | noSubscribers | 85.638 ± 47.676 | 0.006544 ± 0.000516 | 1746.8 |
| baseline-stable-1.2.0 | stickySend | 88.045 ± 14.624 | 0.011149 ± 0.000192 | 15400.1 |
| candidate-stable-1.3.0 | noSubscribers | 192.299 ± 2.585 | 0.006312 ± 0.000399 | 40.0 |
| candidate-stable-1.3.0 | stickySend | 93.638 ± 8.148 | 0.015962 ± 0.000925 | 15400.1 |
| baseline-registration-1.2.0 | registration | 10893.297 ± 1082.791 | 0.000210 ± 0.000044 | 1000.0 |
| candidate-registration-1.3.0 | registration | 7499.108 ± 3354.582 | 0.000877 ± 0.000274 | 1000.0 |
| candidate-subscription-1.3.0 | plain | 287.319 ± 22.967 | 0.003927 ± 0.000099 | 3589.4 |
| candidate-subscription-1.3.0 | hook | 259.431 ± 35.029 | 0.003812 ± 0.000049 | 3840.0 |
| candidate-stable-1.3.0-reverse | noSubscribers | 185.372 ± 10.449 | 0.005425 ± 0.000034 | 40.0 |
| candidate-stable-1.3.0-reverse | stickySend | 86.291 ± 14.896 | 0.010607 ± 0.000067 | 15400.1 |
| baseline-stable-1.2.0-reverse | noSubscribers | 189.917 ± 9.887 | 0.009939 ± 0.001512 | 40.0 |
| baseline-stable-1.2.0-reverse | stickySend | 71.079 ± 23.423 | 0.024188 ± 0.001897 | 15400.1 |
| candidate-registration-1.3.0-reverse | registration | 6258.119 ± 3450.157 | 0.000828 ± 0.000364 | 1000.0 |
| baseline-registration-1.2.0-reverse | registration | 3549.585 ± 2597.389 | 0.000814 ± 0.000735 | 986.7 |

## 结论与限制

- 普通无订阅发送首轮 baseline 的 fork 均值约 45/78/135 ops/ms，首轮 B/op 也明显不稳定；反向复测候选 185.4 ± 10.4、baseline 189.9 ± 9.9 ops/ms，均约 40 B/批次。不能使用首轮差值宣称候选提速。
- sticky 两轮吞吐区间重叠，分配均约 15400 B/批次；候选采样 ms/op 的首轮偏高未在反向复测保持。没有可据此宣称的性能优势。
- 首次注册的候选/baseline 吞吐比由首轮约 0.69 变为反向复测约 1.76；fork 内、fork 间波动明显，两版均约 1000 B/次。现有工作站后台负载未受控，发布前仍需在空闲环境复测，不能判定无回退，也不能凭该读数归因为实现回退。
- 新订阅 plain 为 287.3 ± 23.0、hook 为 259.4 ± 35.0 ops/ms，区间重叠；分配分别约 3589/3840 B/次，额外约 250 B。全部已测 sent/received 计数相等。结果包含 runBlocking、订阅、调度、收发、校验与取消，不能解读为 hook 本体的单独成本或 ACK 保证。
- 机器负载在各组运行前后持续变化，顺序复测只帮助识别波动，未提供受控性能认证。没有运行完整 baseline/candidate/shared 正式矩阵，shared 仅运行工作量自检；结果不覆盖 Android ART。

复现参数见 [基准说明](../../README.md)。原顺序为 baseline 稳定发送 → candidate 稳定发送 → baseline 注册 → candidate 注册 → candidate 新订阅；反向复测分别按 candidate → baseline 执行稳定发送与注册，所有结果均保留。

## 首次注册补验（2026-11-10）

复用相同 JAR、源码与 JMH 参数，按基线 → 候选 → 候选 → 基线串行复测四组，每组仍为 3 forks。指标见 [补验数据](registration-followup-2026-11-10.json)，报告身份、执行参数及负载见 [补验环境](registration-followup-2026-11-10-environment.json)。

| 顺序 | 制品 | 吞吐 ops/ms ± 99.9% 误差 | 分配 B/op |
| --- | --- | --- | --- |
| 1 | 1.2.0 | 10508.2 ± 2135.1 | 1000.0 |
| 2 | 1.3.0-SNAPSHOT | 11843.3 ± 503.8 | 1000.0 |
| 3 | 1.3.0-SNAPSHOT | 11968.3 ± 443.2 | 1000.0 |
| 4 | 1.2.0 | 9596.1 ± 2057.8 | 1000.0 |

两轮均未复现此前候选注册吞吐偏低的结果。候选两组读数接近，但基线仍有明显波动，后台应用未隔离；因此不宣称提速，也不将本轮视为受控空闲环境认证。此前九组结果及其限制保持原始含义。
