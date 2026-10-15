# FlowBus 文档

[English](en/README.md) · [项目首页](../README.md)

最新正式版本为 **1.2.0**，已于 2026-10-15 发布到 Maven Central。标注“1.2.0 起”的 API 可直接使用正式坐标；发布与公开制品验证见[发布说明](release-notes-1.2.0.md)。

## 使用指南

| 文档 | 内容 |
| --- | --- |
| [快速开始](../README.md#5-分钟上手) | 安装、全局通知、owner 和命名通道 |
| [Android](../library-android/README.md) | 生命周期、挂起回调、显式实例、owner 配置 |
| [Core](../flowbus-core/README.md) | Kotlin/JVM、多实例、scope、sticky、诊断 |
| [兼容性](compatibility.md) | 工具链、旧消费者、设备验证及承诺边界 |
| [排障](../TROUBLESHOOTING.md) | 来源、key、订阅就绪、溢出、构建与清理 |
| [JVM 性能基准](../benchmarks/jvm/README.md) | 复现方法、结果及测量限制 |

示例应用位于 [app](../app)。[Compose](../compatibility/compose-consumer/README.md)、[Navigation](../compatibility/navigation-consumer/README.md) 和 [NavHost](../compatibility/navhost-consumer/README.md) 是独立消费者，不是发布模块。

## 版本记录

| 版本 | 状态 | 说明 |
| --- | --- | --- |
| [1.2.0](release-notes-1.2.0.md) | 已发布 | 按通道配置、投递契约、性能基线 |
| [1.1.0](release-notes-1.1.0.md) | 已发布 | API 21 兼容、关闭可靠性、挂起订阅和实例接入 |
| [1.0.7](release-notes-1.0.7.md) | 历史版本 | scope 关闭与移除竞态修复 |
| [1.0.6](release-notes-1.0.6.md) | 历史版本 | 单仓维护、诊断、关闭等待和 sticky 消费 |

汇总见[更新日志](../CHANGELOG.md)。历史发布说明只描述对应版本；旧版安装示例和验证记录不替代当前版本的发布与验收证据。

## 贡献与维护

- [贡献指南](../CONTRIBUTING.md)：环境、验证、分支/提交命名、文档维护。
- [支持与提问](../SUPPORT.md)、[安全策略](../SECURITY.md)、[行为准则](../CODE_OF_CONDUCT.md)。
- [发布清单](release-checklist.md)：从候选制品验证到正式发布和回退。
- [1.2.0 实现方案](implementation-plan-1.2.0.md)：版本设计记录，保留实施时的需求与取舍；当前行为以使用指南和源码为准。

## 文档约定

中文为默认维护语言，根目录与模块英文文档沿用 `README_EN.md`，其他英文说明位于 `docs/en/`。同一主题优先更新现有文档；文件名使用表达用途的英文小写与连字符，发布说明包含版本号。使用指南按功能组织，版本细节归入发布说明，CI 证据注明受测提交，不能把历史通过结果当作当前 HEAD 的结果。

`next-version-plan.md` 保留为旧链接入口，兼容性矩阵统一维护在 `compatibility.md`。新增主题从本索引链接；调整标题时同步站内锚点，必要时保留稳定的显式锚点。
