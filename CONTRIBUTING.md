# 贡献指南

[English](docs/en/contributing.md) · [文档索引](docs/README.md)

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

所有模块均在主仓内维护，无需初始化子模块。

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

## 真实产物与消费者验证

从项目根目录使用隔离 Maven 目录，避免替换用户已有本地产物：

```bash
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
```

构建仍用 JDK 17，Java 参数只选择已有 JDK 8 消费者运行环境。根 publishToMavenLocal 会先删除所选仓库中 flowbus-core/flowbus 的当前版本目录再发布；生成消费者使用 exclusiveContent 固定读取候选目录，不能回退远端旧产物。生成工程、日志和测试签名不得提交。

| 入口 | 验证范围 |
| --- | --- |
| verifyMavenLocalArtifacts | JAR/AAR、POM/module metadata、公开依赖 scope、sources/javadoc、license/developer |
| verifyMavenLocalCoreConsumer | 候选 core Kotlin/Java 编译与指定 JVM 运行 |
| [verifyMavenLocalBinaryCoreConsumer](compatibility/core-binary-consumer/README.md) | 原版 1.0.7 / 1.1.0 编译、同一字节码在原版/候选运行、稳定 JVM 符号 |
| [verifyMavenLocalConsumer](compatibility/android-consumer/README.md) | 普通 Android release R8、设备六组验收、Android AAR 公开符号 |
| [verifyMavenLocalComposeConsumer](compatibility/compose-consumer/README.md) | 独立 Compose release R8、effect 与错误/取消设备自检 |
| [可选 Navigation](compatibility/navigation-consumer/README.md) / [实际 NavHost](compatibility/navhost-consumer/README.md) | 返回栈、动画生命周期阈值与 Activity 配置重建 |

消费者参数不会修改基础库：

```bash
./gradlew verifyMavenLocalCoreConsumer verifyMavenLocalConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.kotlinVersion=1.8.22 -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
./gradlew verifyMavenLocalConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.kotlinVersion=1.8.22 -Pflowbus.consumer.compileSdk=33
./gradlew :flowbus-core:verifyCoreRuntime -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
```

Kotlin 参数只选择 core/普通 Android 消费者编译器，不用于 Compose compiler 配对。已测组合、实际依赖解析和未认证范围统一见[兼容性评估](docs/compatibility.md)。

设备测试使用 :library-android:connectedDebugAndroidTest 与 :app:connectedReleaseAndroidTest，必须与 UI driver 串行。driver 要求当前 runId 日志、同包 UI 与全部阶段一致；mock 不替代设备执行。API 21 与远端 CI 仍为发布门禁，不能从 API 24 结果推导通过。完整环境和签名步骤见[发布清单](docs/release-checklist.md)。

## PR 要求

1. PR 标题说明最终改动，例如 `fix(core): 修复 scope 关闭后的清理竞态` 或 `docs: 整理入门指南`。
2. PR 描述包含目的、主要改动、验证命令和结果。
3. 涉及行为变化时补充测试或说明无法补测的原因。
4. 涉及公开 API、依赖版本或使用方式时同步更新 README、README_EN、KDoc、API 基线和发布说明。
5. 不提交本地密钥、签名文件、IDE 私有配置和构建产物。
6. 不在一个 PR 中混合无关格式化、重命名和功能变化。

## 代码与文档风格

1. Kotlin 代码保持现有命名、包结构和测试风格。
2. Android 相关改动优先放在 `library-android` 或 `app`，平台无关逻辑优先放在 `flowbus-core`。
3. 中文文档保持简洁，中文与英文、数字之间保留空格。
4. 示例代码优先给出可直接复制运行的最小片段。

## 命名与文档维护

- 新分支按 `<type>/<short-description>` 命名，例如 `fix/scope-cleanup`、`docs/release-guides`；已有候选分支不为统一形式而重命名。
- 提交标题使用 `<type>(<scope>): <中文简述>`，scope 可省略；正文说明实际变更和验证。常用 type 为 `feat`、`fix`、`docs`、`test`、`build`、`ci`、`refactor`、`perf`。
- 不在分支、文件、提交或 PR 名称中加入与功能无关的执行工具或模型身份。
- 文档命名与双语组织见[文档约定](docs/README.md#文档约定)。根 README 负责选型与入门，模块 README 负责功能用法，兼容性矩阵统一维护，发布说明保存版本变化和受测提交，发布清单维护操作流程。
- 调整文档位置或标题时检查相对链接、锚点和双语入口。安装示例保持最新已发布版本，新 API 显式标注起始版本；正式发布后再同步版本状态。

<a id="candidate-builds"></a>

<a id="本地体验候选版本"></a>

## 本地验证开发制品

1.2.0 已发布到 Maven Central，普通接入直接使用正式仓库。验证本地源码修改时，在仓库根目录执行：

```bash
./gradlew publishToMavenLocal "-Dmaven.repo.local=$PWD/build/candidate-maven"
```

在消费者的依赖仓库中加入候选目录，并仅将本项目 group 指向该目录，避免解析到远端旧制品：

```kotlin
repositories {
    exclusiveContent {
        forRepository {
            maven { url = uri("/absolute/path/to/FlowBus/build/candidate-maven") }
        }
        filter { includeGroup("io.github.logan0817") }
    }
    mavenCentral()
    google() // Android 消费者
}
```

Android 使用 `implementation("io.github.logan0817:flowbus:1.2.0")`；纯 JVM 使用 `implementation("io.github.logan0817:flowbus-core:1.2.0")`。若使用集中仓库管理，将仓库配置放入消费者 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories`。不要把本地候选安装成功当作正式发布或完整设备验收。
