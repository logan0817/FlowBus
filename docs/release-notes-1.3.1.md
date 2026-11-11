# FlowBus 1.3.1 发布说明

[English](en/release-notes-1.3.1.md) · [实施方案](implementation-plan-1.3.1.md) · [文档索引](README.md)

**1.3.1** 已于 2026-11-11 发布到 Maven Central，包含 core、Android 和可选 Compose 三个坐标。 [GitHub Release v1.3.1](https://github.com/logan0817/FlowBus/releases/tag/v1.3.1) 已公开，标签指向下表固定受测源码。

```kotlin
implementation("io.github.logan0817:flowbus:1.3.1")
// 纯 JVM 场景按需使用
implementation("io.github.logan0817:flowbus-core:1.3.1")
// Compose 场景按需添加
implementation("io.github.logan0817:flowbus-compose:1.3.1")
```

## 修复与升级

| 修复 | 触发条件与结果 |
| --- | --- |
| sticky 重入一致性 | 回调收到 `1` 后立即发送 `2`，最近 replay 保持 `2`，不会因外层发送较晚恢复而回退为 `1` |
| scope 绑定异常安全 | `openScope(name, closeWhen)` 在登记前检查 `CoroutineScope` 的 Job；参数错误仍抛异常，但不留下新句柄，也不修改已有同名共享 store |
| 生命周期阈值与入口副作用 | 最低状态只允许 `CREATED`、`STARTED`、`RESUMED`；Android/Compose 对 `INITIALIZED`、`DESTROYED` 同步抛 `IllegalArgumentException`，便捷入口在获取全局/owner 来源前校验 |

保持公开 API、三个坐标、Java 8 字节码、minSdk 21 和基础依赖：Kotlin 1.9.25、Coroutines 1.8.1、Lifecycle 2.6.2。可选 Compose 保持 runtime 1.5.4 / compiler 1.5.15，基础两个坐标不传递引入 Compose。

**升级时重新编译消费者。** 旧 inline `collectEvent` 可能内嵌旧校验，旧 reified wrapper 可能内嵌先获取来源的顺序；仅替换 AAR 无法保证更新这些调用点。合法阈值的默认 STARTED、dispatcher、sticky 选择和来源所有权保持原约定。owner 本身已销毁但阈值合法时仍正常不订阅。

sticky 修复保留拒绝/取消发送不提交 replay、clear/consume 代际隔离、背压和 DROP 策略以及订阅通知先于 replay 的时序。`accepted=true` 不表示业务处理完成，`DROP_LATEST` 下实时流与 replay 的既有差异保持；不承诺未同步并发调用的全局顺序。已有句柄直接调用 `bindTo` 的参数错误不会销毁该句柄。

## 固定发布来源

| 身份 | 已核验值 |
| --- | --- |
| 源码 / tag 目标 | `2dbd9581af58ae376141d8ada62d4f26e6507754`（[PR #16](https://github.com/logan0817/FlowBus/pull/16) 合并提交） |
| main CI | [42975281531，attempt 2](https://github.com/logan0817/FlowBus/actions/runs/42975281531/attempts/2)，九项检查成功 |
| CI 候选清单 SHA-256 | `d2e6409b5a2b89441da087eb2d7a08411dd71fbfa7d3ec177cc485f5147347b5` |
| GPG 完整指纹 | `8BB25F160F45FD8919DC985A152B32281C3E9240` |
| Central deployment | `00ac090a-26b4-430c-820b-455fae6a4189`，`PUBLISHED` |
| 签名包 SHA-256 | `da4bff803a47a4798f4c801f09ea73266817a3f9d4a50ad644822e7431ceabf2` |

仅签名并上传该 main CI 的原文件，没有重新构建发布库。15 个发布文件在本机签名，`VALIDATED` 时回下载 30 个制品/签名比对后，对原 deployment 正式发布；随后从公开 Maven Central 核验全部 **150 个文件、15 份签名、120 份校验和**，与固定签名包一致。

| 主制品 | SHA-256 |
| --- | --- |
| flowbus-core-1.3.1.jar | `c28674aac6e420fc31ee04c6baddccd9371c12087fe421ac686a9f86791d0df4` |
| flowbus-1.3.1.aar | `bb3db06162d21f4c4f12fb797f40c89522c34d27a1c4a2acca131fc8a8940615` |
| flowbus-compose-1.3.1.aar | `a6a6f2a38aec6c8cee1bcbfdc935f50b74f5add57cc302e0b912fc4f60a91ef3` |

发布后的文档提交和其 CI 候选不替换上述公开制品。版本标签指向上述实际受测源码；文档最终状态由后续文档 PR 的 Checks 与 main CI 核对。

## 验证结果与限制

- 最终 main：284 项 JVM 测试（core 201 / Android 81 / 示例 2）、三模块 API、三份 release lint（0 issue）、Kotlin 1.8.22 / 1.9.22 / 2.0.21 / 2.1.0、Java 8 和 release R8 通过。
- 1.0.7 / 1.1.0 / 1.2.0 / 1.3.0 原版 core 编译的同一字节码在候选上运行通过；1.3.0 Android/Compose 冻结消费者与公开符号检查通过。9 份符号报告、10 份二进制/身份报告均已核对。private 实现类的原始差异保留于 `otherJvmRemovals`，不冒充公开 API 缺失；静态符号检查不是完整 ABI 认证。
- API 21/35：Android **8**、Compose effect **12**、policy **11** 阶段全部通过，本次 runId、同包 UI、日志和候选摘要一致。API 21 另有 Navigation/NavHost 7/8 阶段；API 35 库 instrumentation 3、示例 release instrumentation 20 项通过。
- 公开依赖独立工程仅从 Maven Central 解析 FlowBus，实际 Gradle runtime 制品摘要与上表一致；core 在 Java 8 运行，Android/Compose release R8 和一加 API 33 真机 Android **8** / effect **12** / policy **11** 阶段通过。
- 正式[定向性能报告](../benchmarks/jvm/results/1.3.1/report.md)来自 [CI 42971579052](https://github.com/logan0817/FlowBus/actions/runs/42971579052) 同 runner 对照。noSubscribers / stickySend / active1 吞吐比约 1.003 / 1.018 / 0.998，误差区间重叠；sticky 分配约下降 22%。不承诺稳定提速或 Android ART 性能。

main CI 首轮 API 35 遇到模拟器 ColorBuffer/纹理错误、黑屏及 InputDispatcher `NO_WINDOW`，示例 Espresso 等待窗口焦点超时；保留失败现场，同一源码整轮重跑 attempt 2 后九项成功，没有修改断言或减少覆盖。该模拟器问题的根因尚未修复，不能把复测通过表述为根因解决。

开发阶段红测试、负向身份测试和两轮 PR 验收过程见[实施方案](implementation-plan-1.3.1.md)及 [PR #16](https://github.com/logan0817/FlowBus/pull/16)。发布固定来源以本页表格为准。

## 回退

回退依赖至 1.3.0 后重新编译、重新验证所用入口；sticky 重入、scope 创建失败登记和 DESTROYED 阈值问题也会恢复。已公开的 1.3.0/1.3.1 制品和标签不覆盖；需要进一步修复时发布新版本。
