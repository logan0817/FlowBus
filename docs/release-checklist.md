# 发布清单

[English](en/release-checklist.md)

发布 `1.0.6` 前按这份清单核对。

目标是确认 `flowbus-core` 和 `flowbus` 都由 FlowBus 主仓库统一构建，并且本地验证与远程发布任务图都可检查。

## 1. 发布前验证命令

```bash
./gradlew apiCheck --rerun-tasks --warning-mode=all
./gradlew :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease :app:assembleDebug :app:assembleRelease verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalConsumer --rerun-tasks --warning-mode=all
./gradlew releaseToMavenCentral --dry-run --warning-mode=all
git diff --check -- . ':(exclude)flowbus-core/api/*.api' ':(exclude)library-android/api/*.api'
```

说明：`*.api` 是 API 校验插件生成的快照文件，由 `apiCheck` 专门校验。原始 diff 空白检查排除这些文件，避免生成器保留的文件尾空行造成误报。

检查结果：
1. `flowbus-core` JVM 单测通过。
2. `library-android` 和 `app` debug 单测通过。
3. `library-android` 和 `app` release lint 通过。
4. 示例应用 debug 和 release APK 都能构建。
5. `apiCheck` 能阻止公开 API 在没有更新基线的情况下漂移。
6. `:app:assembleRelease` 必须走 R8/minify，用示例 app 验证消费者 shrink 场景。
7. `verifyMavenLocalArtifacts` 能生成并检查 `flowbus-core` 和 `flowbus` 的本地 Maven 产物、POM、module metadata、license / developer 元数据、公开依赖 scope 和 AAR 内容。
8. `verifyMavenLocalCoreConsumer` 能用 Maven local 坐标生成一个真实 Kotlin/JVM 消费者，并直接编译 `flowbus-core` 坐标。
9. `verifyMavenLocalConsumer` 能用 Maven local 坐标生成一个真实 Android 消费者，并通过 release R8/minify 构建。
10. `releaseToMavenCentral --dry-run` 能作为主仓库统一远程发布任务图的 smoke check，并确认 `clean` 会进入任务图。
11. 构建失败时先看真正失败任务；AGP / Gradle 兼容 warning 要单独记录，不要当成发布失败根因。

如果本次确实修改了公开 API，先运行：

```bash
./gradlew apiDump
```

然后人工审查这 2 个文件：

1. `flowbus-core/api/flowbus-core.api`
2. `library-android/api/library-android.api`

## 2. 设备测试命令

本地连接模拟器或真机后运行下面命令。三项动画设置必须先返回 `0`，再执行 connected test。

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings get global window_animation_scale
adb shell settings get global transition_animation_scale
adb shell settings get global animator_duration_scale
./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --rerun-tasks --warning-mode=all
```

检查结果：
1. `library-android` connected test 能在关闭动画后通过。
2. `app` connected release test 能在 R8/minify 后覆盖全局、scope、sticky、channel、非 UI 和登录示例链路。
3. `testBuildType = "release"` 会让 app instrumentation 只生成 release 测试变体，因此只保留 app release 设备测试入口。
4. 如果设备测试失败，先查模拟器启动状态、AndroidX Test Runner、三项动画配置，再查业务断言。

## 3. 产物检查

本地 Maven 产物必须满足：
1. `flowbus-core-1.0.6.jar` 和 `flowbus-1.0.6.aar` 存在。
2. 两个模块都必须生成 `-sources.jar`、`-javadoc.jar` 和 `.module`。
3. `flowbus` 主产物必须是 AAR，不能退回普通 Jar。
4. `flowbus` POM 中依赖 `flowbus-core`，版本必须等于本次版本。
5. `flowbus-core` POM 与 module metadata 必须把 `kotlinx-coroutines-core` 暴露为 compile/API 依赖。
6. `flowbus` POM 与 module metadata 必须把 `flowbus-core`、`androidx.lifecycle:lifecycle-runtime-ktx`、`androidx.lifecycle:lifecycle-viewmodel-ktx` 暴露为 compile/API 依赖。
7. `flowbus` AAR 内包含 `classes.jar` 和 `proguard.txt`。
8. `flowbus-core` 和 `flowbus` 都使用根 `gradle.properties` 中的发布元数据。
9. POM 必须包含 license 与 developer 元数据，避免 Maven Central 展示信息不完整。
10. `verifyMavenLocalCoreConsumer` 必须直接依赖 `flowbus-core`，确保 core 坐标能被普通 Kotlin/JVM 消费者解析和编译。
11. `verifyMavenLocalConsumer` 必须启用 `android.useAndroidX=true`、Java/Kotlin 1.8 target 和 release minify，确保 Maven local 坐标能被真实 Android 消费者解析和压缩。

发布元数据包括：1. `GROUP`。2. `VERSION_NAME`。3. POM。4. SCM。5. issue 信息。6. license 信息。7. developer 信息。

这些断言由 `verifyMavenLocalArtifacts`、`verifyMavenLocalCoreConsumer` 和 `verifyMavenLocalConsumer` 统一执行。

## 4. 仓库归属检查

发布前必须确认：
1. 根仓库不再存在 `.gitmodules`。
2. `flowbus-core` 目录内不再存在 `.git`、独立 Gradle Wrapper、独立 `settings.gradle.kts` 或独立 `gradle.properties`。
3. `settings.gradle.kts` 仍 include `flowbus-core` 和 `library-android`。
4. 根 `.github/workflows/ci.yml` 覆盖 diff 空白检查、`apiCheck`、单测、release lint、示例应用 assemble、library debug connected test、app release connected test、本地 Maven 产物、core consumer、minified Android consumer 和发布 dry-run。

## 5. 兼容版本检查

发布前必须确认：

| 项目 | 要求 |
| --- | --- |
| Kotlin | `1.9.25` |
| AGP | `8.6.1` |
| Gradle | `8.7` |
| Gradle 运行 JDK | `17` |
| 发布字节码 | Java 8 |
| Android SDK | `minSdk=21`，`compileSdk=35`，`targetSdk=35` |

Kotlin 1.8 及以下、AGP 7.x 项目不承诺无痛接入。历史项目需要单独验证。

一句话记住当前兼容基线：Kotlin 1.9.25、AGP 8.6.1、Gradle 8.7、JDK 17、Java 8。

## 6. 文档与版本检查

发布前按这张表核对：

| 检查项 | 责任文档 | 通过标准 |
| --- | --- | --- |
| 版本号 | 根 `gradle.properties` | `VERSION_NAME=1.0.6` |
| 安装坐标 | 根 README、Android README、core README 的中英文版本 | `flowbus` 和 `flowbus-core` 坐标与版本一致 |
| 中英文入口 | 根 README、模块 README、`docs` 中英文页面 | 中文和英文文档互相可跳转 |
| 文档导航 | 根 README | 能找到 Android 模块、core 模块、版本记录和英文文档 |
| 发送结果边界 | 根 README、Android README、core README、发布说明 | `tryPostEventResult(...)` / `tryPostResult(...)` 只代表 `tryEmit` 是否被拒绝，不代表业务处理成功 |
| 溢出策略边界 | 根 README、Android README、core README、发布说明 | `DROP_OLDEST` / `DROP_LATEST` 不是可靠队列策略 |
| scope 关闭边界 | core README、发布说明 | `closeSuspending()` / `tryClose(timeoutMillis)` 适合 UI 或单线程关闭场景 |
| sticky 一次性消费边界 | 根 README、Android README、core README、发布说明 | `consumeStickyLatest(...)` / `consumeStickyLatestEvent(...)` 只处理当前 sticky replay 的读取和清理，不会阻止其他线程后续写入新的 sticky 值 |
| 旧结构排查 | `TROUBLESHOOTING.md` | 只保留主仓直接维护后的本地旧结构排查说明，不再引导用户按旧子仓工作流初始化或发布 |

## 7. 远程发布前检查

远程发布前，再确认本机或 CI 已配置 Maven Central token 和 GPG 签名环境。

本地任务不要求签名：

1. `verifyMavenLocalArtifacts`
2. `verifyMavenLocalCoreConsumer`
3. `verifyMavenLocalConsumer`
4. `publishToMavenLocal`

远程任务要求发布账号和签名可用：

1. `publishToMavenCentral`
2. `releaseToMavenCentral`

远程发布成功后，必须确认 Central Portal 部署中的每个产物都有 `.asc` 签名文件和 checksum 文件。本地 `publishToMavenLocal` 不生成远程签名文件，因此只做结构校验。

当前配置只上传 Central Portal deployment，不会自动 release。

```bash
./gradlew releaseToMavenCentral
```

发布任务成功后，到 Central Portal deployments 页面确认部署状态、签名文件和 checksum 文件，再决定 release 或 drop。

不要把 token、GPG 私钥、`signing.properties`、`release.properties` 提交到仓库。
