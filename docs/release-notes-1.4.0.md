# FlowBus 1.4.0 发布说明

**1.4.0** 已于 **2026-11-16** 正式发布到 Maven Central。三个坐标的公开文件、签名与独立消费者验收通过；固定生产源码和验收证据见下方。

[English](en/release-notes-1.4.0.md) · [实施方案](implementation-plan-1.4.0.md) · [排障指南](../TROUBLESHOOTING.md)

本版增加实例级排障观察器，围绕发送结果、真实订阅起止、错误阶段和同名流来源身份定位问题。观察默认关闭，记录不携带业务值或 Throwable；普通观察器失败隔离，不替代业务错误策略。完整订阅记录要求先安装观察器，再取得 Flow。

同时修复自定义 logger 抛异常会跳过原 errorHandler，以及 Android warning 失败会覆盖缓冲拒绝结果的问题。保持默认投递、取消、sticky 和 scope 生命周期契约；不修改现有 FlowBusConfig 构造及 copy 描述符。

源码版本 VERSION_NAME=1.4.0、APP_VERSION_NAME=1.4.0、APP_VERSION_CODE=13；测试 APK versionCode=3。实施过程和验收历史记录在实施方案中，1.3.1 的历史报告不作为本版通过证据。


2026-11-16 [PR #18 首轮 CI](https://github.com/logan0817/FlowBus/actions/runs/43782753659) attempt 1 九项通过，327 项 JVM、API/lint、四 Kotlin、Java8、R8、五版 core 原字节码和 12 份公开符号检查通过；API21/35 Android/effect/policy 8/12/11 的日志、runId 和同包 UI 已核对，API35 示例 22 项包含新增排障页两项。固定 head、PR 合成提交、候选摘要与收尾门禁见[方案第 9 节](implementation-plan-1.4.0.md#9-2026-11-16-云端验收与收尾规则)。

默认关闭仍有成本：云端 SampleTime 无订阅/sticky 整批均值增加约 8.4%/5.3%；开启诊断成本显著，仅适合临时排障。真实读数、分配变化和限制见[性能记录](../benchmarks/jvm/results/1.4.0/report.md)。本次已完成正式发布，可直接使用 1.4.0 正式坐标；回退需移除新增诊断引用并重新编译。

## 正式发布验收（2026-11-16）

| 项目 | 证据 |
| --- | --- |
| 受测源码 | `a8e3c8a9811795d4851c09c2e8248ba2ce4b94a3` |
| Main CI | [43786039022 attempt 1](https://github.com/logan0817/FlowBus/actions/runs/43786039022), 9 / 9 success |
| 候选清单 SHA-256 | `2088813689c965cd13ccdadd8a5cd2d936fe325cca8cd0cc7fdbcb9b232c3004` |
| Central deployment | `cf3eaf35-ad00-4d60-a7fb-33cfdcd1127e` / `PUBLISHED` |
| 批次 | `io.github.logan0817-FlowBus-1.4.0` |
| GPG 公钥完整指纹 | `8BB25F160F45FD8919DC985A152B32281C3E9240` |
| 签名包 SHA-256 | `a56539992a2f11e9cfa7f16a4a018e07e27e2ad097b7715c2e82670a081dfbe1` |
| 公开校验 | 150 文件、15 签名、120 校验文件逐字节一致 |
| 独立消费者 | 只解析公开 Central；Java8 smoke、Android/Compose release R8、原版 1.1.0/1.3.0 fixture 链接与实际制品摘要通过。 |
| 公开依赖真机 | 红米 23078RKD5C / Android16 / API36：Android8、effect12、policy11；三个独立 runId、本轮日志及同包 UI 核对。 |

正式坐标与主制品 SHA-256：

| Coordinate | SHA-256 |
| --- | --- |
| `io.github.logan0817:flowbus-core:1.4.0` | `503fa89c605755dd15adc8f530211ef62c1ca508708a80614c14a6a33e531bb0` |
| `io.github.logan0817:flowbus:1.4.0` | `90f958f9cc1b155ec8bd900db48d3b59a4e37f34573929af826014ac239dbd6f` |
| `io.github.logan0817:flowbus-compose:1.4.0` | `a6a6f2a38aec6c8cee1bcbfdc935f50b74f5add57cc302e0b912fc4f60a91ef3` |

公开制品直接签名自固定 main CI 候选，未重新构建。正式性能来自 run 43782753659，已核对主制品归档内部内容与最终 PR、生产 main 一致。API21/35 证据验证同一候选；上方公开依赖真机为 API36，两者范围分别记录，不推导 ART 性能。原始材料保留于本机 `build/release-1.4.0/` 和 `build/verification-1.4.0/`，不能假定随 Git 分发。
