# 实测分析与限制

完整报告见 [report.md](report.md)、[原始指标](metrics.json)、[环境与制品摘要](environment.json)。三组均为 JMH 1.37 / JDK 17.0.18 / Coroutines 1.8.1，3 forks、5 次预热与 5 次测量，每次 1 秒，-Xms512m -Xmx512m，GC profiler。共 58 组场景/测量模式完成，全部行为自检与 ID 校验通过；默认发送路径未因基准结果作额外优化。

## 与正式 1.1.0 的比较

普通无人订阅发送（noSubscribers）候选吞吐为基线的 98.7%，sticky 发布为 99.8%，多生产者为 99.7%；误差区间重叠。候选无订阅普通发送的每批次分配仍约 40 B，sticky 发布仍约 15400 B，未出现新增稳定发送分配。活跃订阅、慢订阅和 sticky replay 的区间大多重叠，部分基线误差明显较大；不能将平均值较高宣传为确定提速。这批数据未确认默认稳定发送路径存在约 10% 且超出误差的回退。

注册场景是“构造新 bus + 首次普通 Flow”，候选吞吐约为基线的 85.5%，从 14168.233 ± 1237.927 降至 12108.467 ± 453.381 批次/ms；区间未重叠，需明确披露。GC 分配从约 888 B/op 增至 1000 B/op，增加 112 B/op。该路径包含每个 store 的默认有效配置解析、注册锁和类型/策略绑定对象，源码可确认这些新增对象属于注册路径；它们没有在每次稳定发送时重新创建。显式配置注册独立测得约 1064 B/op，无旧版等价功能，不作回退比较。

[JFR 注册采样](registration-profile.json)分别以 1 fork、3 × 1 秒预热和测量采集，仅用于定位，不能替代完整基准。采样栈主要落在 FlowBus 构造、FlowBusStore 创建、ConcurrentHashMap 注册和 SharedFlow 建立，符合首次注册的工作范围。但采样粒度没有可靠识别全部新增配置对象，不能仅凭 weighted sample bytes 将 112 B 精确归因给某一个类。精确分配增量来自 GC profiler，对象来源解释同时依赖源码差异。JFR 开销下的吞吐读数不纳入正式性能结论。

scope 操作每次创建、sticky 写入/消费、关闭并校验登记清理，候选分配约 4176 B/op，基线约 4019 B/op；吞吐区间重叠。此场景是带所有权的完整操作，不能当作单事件发送延迟。

这版选择保留原子配置绑定与诊断，接受首次注册的可见开销；没有通过关闭类型保护、删除指标或修改 sticky 契约降低成本。数据没有确认发送热路径出现稳定回退，当前也没有足够 profile 证据支持进一步改变实现。若业务大量高频重建 bus，需把该注册成本纳入选型；优先由已有业务持有者复用 bus，不自动添加缓存或改变资源所有权。

## 与原生 SharedFlow 的比较

原生 SharedFlow 在本批次语义等价场景的平均吞吐更高，例如无订阅普通发送约 841 批次/ms，候选约 190；sticky 无订阅发布约 762，候选约 98。这体现总线类型保护、结果/指标、sticky 自定义 replay 等功能的成本，不能声称 FlowBus 性能优于直接使用 SharedFlow。

active1/8、slowSuspend/slowDrop 和 producers 包含统一的订阅建立、runBlocking、Default dispatcher、ID 校验与取消开销。吞吐单位是整批次/ms；报告另列按断言接收量推算的实际收集 ID/ms。SampleTime 是整批耗时，不能标成单事件延迟。普通无人订阅和 stickySend 的实际接收量为 0；慢 DROP_OLDEST 的 129 次发送只收到 65 个 ID，不能把发送次数作为全部投递数量。

SharedFlow 没有此处 scope 配置/清理的同等公开功能，因此未设置这些对照。不比较 consume/clear/live DROP_LATEST 的非等价语义。

## 复现边界

本机 Apple M1、macOS 26.5.2 工作站后台负载未受控，三组按 baseline → candidate → shared 顺序串行运行。已记录制品摘要、源码摘要、构建设置摘要及各报告更新时间；工作区未提交，commit 不能单独标识当前候选。原始 JMH JSON 与日志保留在 build/reports，提交用 metrics.json 保留参数、原始迭代值、误差、分位数、GC 和计数，仅删除较大的采样直方图。

此前发现 SharedFlow sticky extra 使用 64、bus 默认 0；旧数据已归档到 build/reports/discarded-sticky-extra-mismatch 并排除，本报告对应统一 extra=0 后全部重跑的数据。JVM 结果不能外推 Android ART，也不能作为 EventBus、LiveEventBus 等未实测框架的性能排名。
