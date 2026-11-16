# 1.4.0 事件排障定向性能记录

状态：2026-11-13 本地定向测量已完成并核对输入身份；云端同 runner 对照与最终 API21/35 仍未执行，尚未通过发布前审查。公开版本仍为 1.3.1。

## 输入与方法

正式基线为 Central 1.3.1，core SHA-256 `c28674aac6e420fc31ee04c6baddccd9371c12087fe421ac686a9f86791d0df4`。当前候选 core SHA-256 `7e7cd4aa404acbb5c5c6b69a9c81a5b9666d57a70aa4f8682029e05c84036eca`，源码基线 HEAD=`1a18d376157916daa34f3128e81a4a1b811c0918`，存在本轮未提交修改；各生产/基准源码的逐文件摘要保存在 environment.json。

Apple M1、macOS 26.5.2、JDK17、JMH 1.37、Coroutines 1.8.1；3 forks，每 fork 5×1s 预热和 5×1s 测量，512MB heap、GC profiler。顺序为 baseline 默认、candidate 默认、baseline 注册、candidate 注册、candidate 诊断；同一工作区串行，测量期间本任务不并行重型构建。工作站后台负载无法控制，顺序、uptime 和环境限制均保留。

## 默认关闭的对照

| 场景 | 1.3.1 批次/ms ± 误差 | 候选批次/ms ± 误差 | 候选/基线 | 1.3.1 B/op | 候选 B/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| noSubscribers | 194.589 ± 3.818 | 186.609 ± 1.150 | 0.959 | 40.03 | 40.03 |
| stickySend | 79.902 ± 0.778 | 79.659 ± 1.262 | 0.997 | 10280.08 | 10280.08 |
| active1 | 33.223 ± 2.526 | 31.753 ± 2.439 | 0.956 | 23021.43 | 24107.65 |
| registration | 12342.086 ± 85.822 | 11546.048 ± 56.301 | 0.936 | 1000.00 | 1064.00 |

noSubscribers、stickySend、active1 每次 invocation 发送 128 个 ID，active1 实收 128；registration 是新实例加首次 Flow 注册的一次操作。吞吐单位不等于单事件速度。stickySend 与 active1 的吞吐置信区间重叠；noSubscribers 和 registration 本机显示较小成本，不能声称零开销或稳定提速。active1 吞吐模式分配约增加 4.7%，仍包含订阅建立、协程调度、取消和校验，不能归因给单个字段。

本轮最初候选注册分配约 1264 B/op，相对正式版约增加 26.4%，因此中止尚未完成的开启态测量并保留现场。将观察器列表、流身份与 ID 改为启用后按需创建，当前注册约 1064 B/op，增加缩小到 6.4%。原始中间结果位于本地 `build/verification-1.4.0/performance/`；没有将中断报告当作最终通过，也没有混用两代候选。

## 候选内开启成本

同一候选使用相同工作量，观察器仅以 AtomicLong 计数；开启态包含同步回调、ThreadLocal 重入保护和记录分配。

| 场景 | 关闭批次/ms | 开启批次/ms | 开启/关闭 | 关闭 B/批次 | 开启 B/批次 |
| --- | ---: | ---: | ---: | ---: | ---: |
| noSubscribers | 184.493 | 61.036 | 0.331 | 0.03 | 12288.13 |
| stickySend | 78.156 | 42.788 | 0.547 | 10240.08 | 22528.14 |
| active1 | 71.355 | 36.218 | 0.508 | 30680.08 | 43496.17 |

开启成本明显。该功能默认关闭，适合按来源临时排障；观察器必须快速返回，不能据此建议在高频业务上持续开启。仅保存元数据不等于没有分配。active1 使用托管 collectSuspending、SUSPEND、128 条实际接收及 digest=8128，观察到 1 Started + 128 Published + 1 Ended；与上一节 raw Flow 的 active1 不是同一条收集调用链，只比较本节内开关，不跨表混算。

## 完整性与限制

五份定向正式报告共 28 行，均保留 Throughput/SampleTime 两种模式；三组默认 smoke 为 20/22/16 行，额外 Subscription smoke 4 行、Diagnostics smoke 12 行。全部自检、实际 ID/计数断言及报告摘要/候选/生产源码/基准源码/构建脚本身份核验通过。smoke 只证明场景可运行，不替代正式测量。

[metrics.json](metrics.json)保留参数、原始迭代值、置信区间、GC 与采样分位数；仅省略大采样直方图。[environment.json](environment.json)保留输入身份与原始完整报告 SHA-256，绝对 JVM/制品路径已作可读归一化。原始报告、identity 和日志仍在忽略目录 `build/verification-1.4.0/performance-final/`；不声称这些本地文件会随 Git 保留。

未运行完整三组正式基准，不更新 `results/current/` 的历史报告；不推导 Android ART、跨框架排名、投递或 ACK 保证。现有 CI 显式入口仍需在本版本授权后运行默认三场景的同 runner 对照，API21/35 也必须验收对应最终候选。
