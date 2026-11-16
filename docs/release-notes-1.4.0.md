# FlowBus 1.4.0 候选说明

**1.4.0** 尚未发布；2026-11-16 生产实现的发布前专项审查通过，最终 PR/main 检查按实施方案收尾。公开安装仍使用 **1.3.1**。

[English](en/release-notes-1.4.0.md) · [实施方案](implementation-plan-1.4.0.md) · [排障指南](../TROUBLESHOOTING.md)

本版增加实例级排障观察器，围绕发送结果、真实订阅起止、错误阶段和同名流来源身份定位问题。观察默认关闭，记录不携带业务值或 Throwable；普通观察器失败隔离，不替代业务错误策略。完整订阅记录要求先安装观察器，再取得 Flow。

同时修复自定义 logger 抛异常会跳过原 errorHandler，以及 Android warning 失败会覆盖缓冲拒绝结果的问题。保持默认投递、取消、sticky 和 scope 生命周期契约；不修改现有 FlowBusConfig 构造及 copy 描述符。

源码版本 VERSION_NAME=1.4.0、APP_VERSION_NAME=1.4.0、APP_VERSION_CODE=13；测试 APK versionCode=3。实际验证和未完成门禁持续记录在实施方案中，1.3.1 的历史报告不作为本版通过证据。


2026-11-16 [PR #18 首轮 CI](https://github.com/logan0817/FlowBus/actions/runs/43782753659) attempt 1 九项通过，327 项 JVM、API/lint、四 Kotlin、Java8、R8、五版 core 原字节码和 12 份公开符号检查通过；API21/35 Android/effect/policy 8/12/11 的日志、runId 和同包 UI 已核对，API35 示例 22 项包含新增排障页两项。固定 head、PR 合成提交、候选摘要与收尾门禁见[方案第 9 节](implementation-plan-1.4.0.md#9-2026-11-16-云端验收与收尾规则)。

默认关闭仍有成本：云端 SampleTime 无订阅/sticky 整批均值增加约 8.4%/5.3%；开启诊断成本显著，仅适合临时排障。真实读数、分配变化和限制见[性能记录](../benchmarks/jvm/results/1.4.0/report.md)。本次不执行 Maven 上传、tag 或 GitHub Release，不能直接按公开坐标解析 1.4.0。
