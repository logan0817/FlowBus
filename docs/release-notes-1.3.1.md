# FlowBus 1.3.1 开发说明（未发布）

[English](en/release-notes-1.3.1.md) · [实施方案](implementation-plan-1.3.1.md) · [文档索引](README.md)

**1.3.1** 尚未发布，当前公开正式版本仍为 **1.3.0**。本文记录正在实施的候选行为与阶段性证据，不是发布完成声明。普通消费者继续使用已发布的 1.3.0；本地候选仅通过[隔离仓库流程](../CONTRIBUTING.md#candidate-builds)验证。

本地验证基于 `130c4aa375155917fdd296e312d4dfab14acee66` 加本轮候选差异。该基线提交本身不包含以下修复；最终受测提交、候选摘要和完整 CI 结果需在发布前补齐。

## 本版目标与状态

| 变更 | 解决的问题 | 当前状态 |
| --- | --- | --- |
| sticky 重入一致性 | 回调收到 `1` 并立即发送 `2`，实时收到 `[1, 2]` 但最近缓存回退为 `1` | 行为回归、正式 CI 性能对照与 API21/35 通过 |
| scope 绑定异常安全 | 不含 Job 的 CoroutineScope 使 openScope 抛错后残留登记、占用同名句柄 | 登记前校验已实现，本地定向回归通过 |
| 生命周期阈值与入口副作用 | DESTROYED 阈值静默不订阅；错误调用还可能先获取全局/owner 总线 | L01–L07 及真实 Compose API21/35 阶段通过 |
| 1.3.0 原版消费者基线 | 最新已发布接口缺少原版编译字节码替换候选的验证 | core 同字节码 Java8、Android/Compose R8/身份和设备运行通过 |

1.3.1 保持三个坐标、Java 8 字节码、Android API 21，以及现有基础工具链和依赖基线。没有增加公共 API、生产依赖、持久化队列或可靠投递承诺；公开符号的最终兼容结论仍需候选 API/二进制门禁确认。

## scope 创建失败的行为

`openScope(name, closeWhen: CoroutineScope)` 和 owner 重载现在在登记句柄之前检查 Job。无 Job 时仍抛出明确异常，但不保留新的 scope 名称，不移除或改写已有同名共享 store。调用者应传入自己管理、包含 Job 的结构化 scope。

对已有句柄直接调用 `bindTo` 时，参数错误不应关闭调用者已经持有的句柄。有效、已完成和已取消 Job 的生命周期绑定继续遵循既有关闭协议；不会为了本次修复主动取消已获取的旧 Flow 引用。

## 生命周期阈值与升级边界

最低收集状态仅允许 `CREATED`、`STARTED`、`RESUMED`。`INITIALIZED`、`DESTROYED` 均同步抛 `IllegalArgumentException`，信息包含实际值与合法值；generic Flow、直接 bus、显式 root/shared/owned 来源和 Compose effect 使用一致规则。

全局和 owner 便捷入口先校验阈值，再获取来源，避免无效调用冻结默认配置或访问 owner 的 ViewModelStore。owner 本身已经销毁但阈值合法时，仍正常不订阅；“销毁的 owner”与“DESTROYED 最低阈值”是两个不同情况。

重新编译消费者以获得完整修复。公开 inline `collectEvent` 可能已内嵌旧校验；reified wrapper 即使调用候选非 inline 方法，其先获取来源的代码也可能已内嵌。仅替换 AAR 不能保证更新这些调用点。有效阈值的默认 STARTED、回调 dispatcher、sticky 选择和来源所有权保持原约定；Java 默认 STARTED 入口没有新增状态参数。

## sticky 验证边界

修复必须同时维持被拒绝或取消的发送不污染 replay、clear/consume 的 generation 隔离、既定背压与 DROP 策略，以及订阅建立通知先于历史 replay 的时序。不能仅把缓存提交提前到可能拒绝的发送之前，也不能通过异步延迟或放宽断言规避重入问题。

当前候选的本地 sticky 行为回归通过，完整发布前验收尚未完成。`accepted=true` 仍不是实时无丢弃或业务处理完成的保证，`DROP_LATEST` 下实时流与 replay 的既有差异不变；本版不承诺未定义的并发调用全局顺序。

## 已取得的本地证据

- W1 三个正式行为回归在修复前按原因失败，报告保存于本地 `build/verification-1.3.1/w1-red-results/`。
- scope 创建失败的定向测试通过，执行日志为 `build/verification-1.3.1/w3-scope.log`。
- Android `LifecycleThresholdTest` 5 项及 `InstanceCollectEventTest` 12 项通过，覆盖非法阈值、全局配置保持可用、owner store 不被读取、三个合法阈值与已销毁 receiver。`w2-w4-integration.log` 的整体执行当时仍有 core 失败，不能把该日志记为整批通过。
- Compose policy fixture 已由 8 个阶段扩展为 11 个，新增 `invalid-lifecycle-thresholds-rejected`、`supported-lifecycle-thresholds`、`destroyed-owner-does-not-subscribe`。参数异常由独立 Recomposer 的普通 Kotlin 调用边界断言，finally 清理全部测试资源；不会在生产 Composable 内吞异常。
- Compose driver 的单测包含对每一个阶段缺失的拒绝检查，2 个测试方法通过。mock 成功不替代实际 Composition、release R8 或 API 21/35 运行。

- `w7-local-quality-final.log`：core 201、Android 81、示例 JVM 2 项均通过，三个模块 API 检查及 Android/Compose/示例 release lint 通过，示例 debug/release（R8）构建通过，发布名称校验为 1.3.1。lint XML 均为 0 error、0 warning。
- `w5-core130-fixed.log`：正式 1.3.0 编译的同一批消费者 class 在原版与候选上运行通过，运行环境为 Java 8。
- `w5-identity-final.log`：Android 与 Compose 的原版编译 fixture、冻结 JAR、release R8 和候选运行依赖摘要检查通过；报告仍标记设备执行待验收。
- `w5-negative-results.json`：fixture/JAR、class、原版摘要、运行图污染及缺报告五项负向测试均被拒绝，旧成功报告不残留；恢复正常输入后验收通过。
- 三模块 1.3.0 公开符号无缺失。旧版 raw JVM 比较的四处变更属于原版 `InnerClasses` 明确标记 private 的实现类；检查器保留 `otherJvmRemovals`，继续阻止公开/受保护/internal 类的描述符缺失，9 项脚本回归通过。

以上为开发阶段证据，输入继续变化后应按最终范围重验。本版首轮 PR CI 与设备证据见下节；main CI 与正式发布验收尚未完成。

## 首轮 PR 验收

[PR #16](https://github.com/logan0817/FlowBus/pull/16) 的提交 `c90cb55` 对应 [CI 42969426491](https://github.com/logan0817/FlowBus/actions/runs/42969426491)，attempt=1，合成 checkout 为 `475c0f141c2b40b2288e7cc79952044143160546`。九项检查全部通过：284 项 JVM 测试、三模块 API/release lint、四个 Kotlin 版本、Java8、四个历史 core 字节码、公开符号、release R8 和 API21/35。API35 库 instrumentation 3 项、示例 release instrumentation 20 项均无失败或跳过。

API21/35 的 Android/effect/policy 分别通过 8/12/11 阶段，包括原版 1.3.0 编译的 Java/Kotlin/Composable；已下载报告核对本次 runId、同包 UI、全部阶段和候选清单。API21 保留 Navigation/NavHost 7/8 阶段。云端 core 与本地受测候选的全部 98 个 class、100 个文件内容一致；ZIP 容器摘要不同，分别记录，不能混用。

本机性能数据受持续负载干扰，记录见[定向性能报告](../benchmarks/jvm/results/1.3.1/report.md)。PR 保持草稿，下一次 CI 在现有 consumers runner 显式执行正式对照；最终性能验收及后续 PR/main CI 仍待完成。上述首轮成功不自动覆盖后续改动。

## 发布前必须补齐

1. sticky 最终回归、全部受影响单元测试、API 和 release lint 通过。
2. 原版 1.3.0 core/Android/Compose 编译结果在候选上运行，摘要与符号检查通过，旧基线覆盖保留。
3. Kotlin 现有矩阵、Java 8、API 21/35 的 instrumentation 与 Maven release R8 消费者完成，Android 8 阶段、Compose effect 12 阶段和 policy 11 阶段有实际日志、同包 UI 和本次 runId 证据。
4. 正式 1.3.0 与最终候选的必要定向性能对照；不能以 smoke 宣称提速或无回退。
5. 最终候选提交与制品摘要、完整 CI run、未解决限制及回退说明进入本文件。实际发布仍按当前授权和[发布清单](release-checklist.md)执行，本文不授予发布权限。

## 正式性能与第二轮 CI

首轮正式测量发现 stickySend 分配增加约 48 B/事件；JFR 后对 replay=1 直接保存最近值，并直接读取缓存数量，保留排序、代际和取消。反序恢复测试同时覆盖 replay=1/2，284 项 JVM 测试/API/lint 通过。本机多轮耗时受后台负载扰动，原始限制保留。

提交 `7dd6bdf` 的 [CI 42971579052](https://github.com/logan0817/FlowBus/actions/runs/42971579052) 九项检查全部成功，attempt=1，checkout=`1380cf166d7519be2b015e131eeaa43aff120938`。同 runner 正式三场景吞吐比约为 1.003/1.018/0.998，均有误差重叠，未观察到明显耗时退化；sticky 分配约下降 22%。[正式报告](../benchmarks/jvm/results/1.3.1/report.md)保存参数、原始迭代值、摘要和限制，不解释为稳定提速或 ART 承诺。

两档 API 的 8/12/11 阶段、Kotlin 四版本、Java8、历史字节码/符号和 R8 再次通过，已审计本次日志/UI/清单。接下来最终文档提交仍执行完整 CI，再合并并核验 main；生产和性能输入不再改变，不重复正式测量。最终 head、合并提交及 main 检查以 [PR #16](https://github.com/logan0817/FlowBus/pull/16) 合并记录/Checks 和本次交付记录为准，避免为引用自身运行编号而重复提交。

## 回退

公开 1.3.0 坐标和标签保持原样。消费者若从本地候选回退到 1.3.0，原来的 sticky 重入、scope 创建失败登记和 DESTROYED 阈值问题也会恢复；应重新编译并重新验证所用入口。开发阶段回退使用独立修复提交，不覆盖已发布制品或重写共享历史。
