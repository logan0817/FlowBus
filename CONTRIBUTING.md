# 贡献指南

感谢参与 FlowBus。这个仓库直接维护 `flowbus-core`、`library-android` 和 `app`，提交前请保持改动小而清楚，避免把无关重构、文档整理和功能改动混在同一个 PR 里。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 17 |
| Android SDK | `compileSdk` 35，`minSdk` 21 |
| Gradle | 使用仓库内的 Gradle Wrapper |
| Kotlin | 1.9.25 |
| Android Gradle Plugin | 8.6.1 |

## 获取代码

```bash
git clone https://github.com/logan0817/FlowBus.git
cd FlowBus
```

`flowbus-core` 已经是主仓内目录，不再需要 `git submodule update`。

## 本地验证

Windows：

```powershell
.\gradlew.bat :flowbus-core:test
.\gradlew.bat :library-android:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :library-android:lintRelease :app:lintRelease
```

macOS / Linux：

```bash
./gradlew :flowbus-core:test
./gradlew :library-android:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :library-android:lintRelease :app:lintRelease
```

发布前请参考 [`docs/release-checklist.md`](./docs/release-checklist.md)。

## PR 要求

1. PR 标题说明改动范围，例如 `core: fix scope close cleanup` 或 `docs: update quick start`。2. PR 描述包含目的、主要改动、验证命令和结果。3. 涉及行为变化时补充测试或说明无法补测的原因。4. 涉及公开 API、依赖版本或使用方式时同步更新 README、README_EN、KDoc、API 基线和发布说明。5. 不提交本地密钥、签名文件、IDE 私有配置和构建产物。6. 不在一个 PR 中混合无关格式化、重命名和功能变化。

## 代码与文档风格

1. Kotlin 代码保持现有命名、包结构和测试风格。2. Android 相关改动优先放在 `library-android` 或 `app`，平台无关逻辑优先放在 `flowbus-core`。3. 中文文档保持简洁，中文与英文、数字之间保留空格。4. 示例代码优先给出可直接复制运行的最小片段。
