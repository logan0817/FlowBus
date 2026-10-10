# 1.1.0 发布清单

[English](en/release-checklist.md)

当前目标为尚未发布的 1.1.0：VERSION_NAME=1.1.0、APP_VERSION_NAME=1.1.0、APP_VERSION_CODE=8，版本目录同步为 1.1.0。发布前必须检查最终候选产物，不能只依据旧批次报告。

## 本地质量与产物门禁

从仓库根目录使用已有 JDK 17、Android SDK 和 Gradle wrapper。隔离目录避免覆盖用户 Maven local；根 publishToMavenLocal 会删除所选仓库中两个坐标的当前版本目录再重新生成。

```bash
./gradlew apiCheck :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease :app:assembleDebug :app:assembleRelease --warning-mode=all
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8 --warning-mode=all
./gradlew releaseToMavenCentral --dry-run --warning-mode=all
git diff --check HEAD -- . ':(exclude)flowbus-core/api/*.api' ':(exclude)library-android/api/*.api'
```

Java 参数只选择消费者运行 JVM，构建仍用 JDK 17。需要本机已有 JDK 8。不修改已审查的 API 快照来绕过 apiCheck；公共 API 变化应审查 apiDump 的差异。生成快照的尾空行由 API 检查处理，普通差异检查排除这两个文件。

- core / Android / app JVM 测试和 release lint 通过，示例 release 必须启用 R8/minify。
- flowbus-core-1.1.0.jar、flowbus-1.1.0.aar、sources/javadoc、POM 与 module metadata 均存在。
- POM/module metadata 的依赖 scope、license、developer、SCM 与版本正确：core 暴露 Coroutines；Android 暴露同版本 core 和 Lifecycle runtime/viewmodel。
- AAR 包含 classes.jar、proguard.txt；两个坐标均为 Java 8 字节码，不含消费者 fixture、Compose/Navigation 依赖或本地配置。
- 真实 Maven core 消费者在 JDK 8 运行；旧 core 消费者只用 Central 原版 1.0.7 编译，相同字节码分别运行原版与候选；Android / Compose 消费者经过 release R8。
- core 与 Android 的已发布 1.0.7 JVM 符号按各消费者 README 核对。保留 raw internal 差异，不把静态符号检查当作完整 ABI 认证。
- remote dry-run 确认统一发布与清理顺序；它不执行 clean，也不代表上传或签名成功。

## 设备与最低系统门禁

先确认目标设备 API/boot。instrumentation 与所有 UI driver 必须串行执行；共享设备应先确认使用范围。仅在测试设备关闭动画，操作后恢复原值。

```bash
adb devices -l
# 替换 serial；只操作指定测试设备。
adb -s device-serial shell settings put global window_animation_scale 0
adb -s device-serial shell settings put global transition_animation_scale 0
adb -s device-serial shell settings put global animator_duration_scale 0
ANDROID_SERIAL=device-serial ./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all
```

1. 库 instrumentation 与示例 release instrumentation 覆盖主链路，示例走 R8/minify。
2. minSdk 21 必须有 API 21 库层和测试签名的 minified Maven 消费者设备证据；API 24 通过、APK 文件名或 D8 静态检查均不能替代。
3. [Android 消费者](../compatibility/android-consumer/README.md)四阶段、[Compose](../compatibility/compose-consumer/README.md)九阶段 effect / 八阶段错误取消、[Navigation](../compatibility/navigation-consumer/README.md)七阶段及[实际 NavHost](../compatibility/navhost-consumer/README.md)八阶段按版本分别验收。driver 必须同时匹配本次 runId 日志、同包 UI 和全部阶段。
4. mock 只验证驱动协议；设备不可用、超时、缺阶段或旧结果均不能写为通过。
5. CI 已配置 API 21 与 API 35 设备门禁，远端成功记录需对应本次提交。

API 21 步骤会先记录并断言指定模拟器的实际 API 与启动状态，再执行测试。工作流无论成功或失败都会上传 `flowbus-verification-<commit SHA>` 报告，保留 14 天，包含单测、lint、instrumentation、JVM 符号及消费者当前运行日志/UI。排查时先下载对应提交的报告；缺少文件可能表示前置步骤尚未执行，不能视为通过。

CI 的 SDK 初始化显式选择 `platform-tools`，命令行工具由 setup-android Action 准备；不要恢复其包含已下架 `tools` 包的默认安装列表。平台和 Build Tools 版本由后续安装步骤指定。

**当前缺口：本机无 API 21 image，API 21 与远端 CI 尚未验收。** 其余已测组合及未认证工具链见[兼容性评估](next-version-plan.md#兼容性评估)。

## 兼容性、源码与文档检查

基础版本保持 Kotlin 1.9.25、Coroutines 1.8.1、Lifecycle 2.6.2、AGP 8.6.1、Gradle 8.7、构建 JDK 17、Java 8 字节码、minSdk 21 / compileSdk 35 / targetSdk 35。Kotlin/compileSdk 消费者矩阵不能推导为所有旧工具链通过。

核对根与模块 README 中英文、CHANGELOG、1.1.0 发布说明、API 注释、版本配置及 CI 路径。1.0.7 仅保留为已发布基准、历史或语义说明。普通无人订阅、sticky replay、关闭等待、错误策略和协作取消说明必须与实现一致。

检查 git status、工作区与暂存区差异；不提交构建产物、测试签名、密钥、日志、个人环境配置、临时沟通记录或无关改动。保留必要 fixtures、API 基准和回归测试。主仓直接维护两个模块，不能含子仓 .git/.gitmodules 或独立发布配置。

## 远程发布与回退

远程操作另行授权后执行。先配置 Maven Central token 与 GPG 签名，确认最终门禁通过；不得将凭据写入仓库。

发布逻辑位于 gradle/release-publishing.gradle.kts，产物验证位于 gradle/release-verification.gradle.kts。publishToMavenCentral / releaseToMavenCentral 当前只上传 Central Portal deployment，不自动 release。上传后逐个检查两个坐标及 sources/javadoc 的 .asc 签名与 checksum，再在 Portal 决定 release 或 drop。本地 Maven 产物不生成远程签名，不能用其替代签名验收。

消费者回退使用已发布 1.0.7，新增 API 调用需一起回退；未发布 deployment 可 drop。禁止覆盖已发布版本。
