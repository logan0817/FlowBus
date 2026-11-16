# FlowBus 1.4.0 候选说明

**1.4.0** 正在开发，尚未发布，发布前审查未完成。公开安装仍使用 **1.3.1**。

[English](en/release-notes-1.4.0.md) · [实施方案](implementation-plan-1.4.0.md) · [排障指南](../TROUBLESHOOTING.md)

本版增加实例级排障观察器，围绕发送结果、真实订阅起止、错误阶段和同名流来源身份定位问题。观察默认关闭，记录不携带业务值或 Throwable；普通观察器失败隔离，不替代业务错误策略。完整订阅记录要求先安装观察器，再取得 Flow。

同时修复自定义 logger 抛异常会跳过原 errorHandler，以及 Android warning 失败会覆盖缓冲拒绝结果的问题。保持默认投递、取消、sticky 和 scope 生命周期契约；不修改现有 FlowBusConfig 构造及 copy 描述符。

源码版本 VERSION_NAME=1.4.0、APP_VERSION_NAME=1.4.0、APP_VERSION_CODE=13；测试 APK versionCode=3。实际验证和未完成门禁持续记录在实施方案中，1.3.1 的历史报告不作为本版通过证据。
