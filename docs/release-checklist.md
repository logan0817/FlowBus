# 1.2.0 发布清单

[English](en/release-checklist.md)

目标配置：VERSION_NAME=1.2.0、APP_VERSION_NAME=1.2.0、APP_VERSION_CODE=9。1.2.0 已于 2026-10-15 正式发布到 Maven Central。本清单维护操作流程，受测提交与报告统一记录在 [1.2.0 发布说明](release-notes-1.2.0.md)；1.1.0 历史发布证据见[对应发布说明](release-notes-1.1.0.md)。

## 发布顺序

1. 完成实现与审查，核对版本、API、文档和候选制品。
2. 确认 PR 当前 head 的全部门禁，包含 API 21/API 35 设备；合并后核对 main 对应提交的 CI。
3. 获得发布授权后上传，核验坐标、签名、校验和与本次返回的 deployment ID。
4. 单独 release 已验收批次；从公开 Maven Central 用独立消费者验证成功后，再同步正式安装示例、更新日志日期与发布状态。

发布说明记录固定受测快照，后续提交通过候选 PR 的 Checks 页或 [CI 运行记录](https://github.com/logan0817/FlowBus/actions)核对当前 head SHA。PR 工作流 artifact 名含合成合并提交 SHA（`github.sha`），需结合 run 的 head SHA 和 digest 对应。报告保留 14 天，到期前妥善保留所需证据，排除密钥及个人环境文件。

## 本地质量与产物门禁

从仓库根目录使用已有 JDK 17、Android SDK 和 Gradle wrapper。隔离目录避免覆盖用户 Maven local；根 publishToMavenLocal 会删除所选仓库中两个坐标的当前版本目录再重新生成。

```bash
./gradlew apiCheck :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease :app:assembleDebug :app:assembleRelease --warning-mode=all
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8 --warning-mode=all
./gradlew verifyMavenLocalBinaryCoreConsumer -Pflowbus.consumer.baselineVersion=1.1.0 "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
./gradlew verifyCentralDeploymentName --warning-mode=all
./gradlew releaseToMavenCentral --dry-run --warning-mode=all
git diff --check HEAD -- . ':(exclude)flowbus-core/api/*.api' ':(exclude)library-android/api/*.api'
```

Java 参数只选择消费者运行 JVM，构建仍用 JDK 17。需要本机已有 JDK 8。不修改已审查的 API 快照来绕过 apiCheck；公共 API 变化应审查 apiDump 的差异。生成快照的尾空行由 API 检查处理，普通差异检查排除这两个文件。

仓库文档与发布配置作为 JVM 测试的显式输入；修改这些文件后应重新执行门禁，不能复用修改前的通过结果。

- core / Android / app JVM 测试和 release lint 通过，示例 release 必须启用 R8/minify。
- flowbus-core-1.2.0.jar、flowbus-1.2.0.aar、sources/javadoc、POM 与 module metadata 均存在。
- POM/module metadata 的依赖 scope、license、developer、SCM 与版本正确：core 暴露 Coroutines；Android 暴露同版本 core 和 Lifecycle runtime/viewmodel。
- AAR 包含 classes.jar、proguard.txt；两个坐标均为 Java 8 字节码，不含消费者 fixture、Compose/Navigation 依赖或本地配置。
- 真实 Maven core 消费者在 JDK 8 运行；旧 core 消费者只用 Central 固定原版 1.0.7 与 1.1.0 分别编译，相同字节码分别运行原版与候选；Android / Compose 消费者经过 release R8。
- core 与 Android 的已发布 1.0.7 与 1.1.0 JVM 符号按各消费者 README 核对。保留 raw internal 差异，不把静态符号检查当作完整 ABI 认证。
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
3. [Android 消费者](../compatibility/android-consumer/README.md)六阶段、[Compose](../compatibility/compose-consumer/README.md)九阶段 effect / 八阶段错误取消、[Navigation](../compatibility/navigation-consumer/README.md)七阶段及[实际 NavHost](../compatibility/navhost-consumer/README.md)八阶段按版本分别验收。driver 必须同时匹配本次 runId 日志、同包 UI 和全部阶段。
4. mock 只验证驱动协议；设备不可用、超时、缺阶段或旧结果均不能写为通过。
5. CI 已配置 API 21 与 API 35 设备门禁，远端成功记录需对应本次提交。

API 21 步骤会先记录并断言指定模拟器的实际 API 与启动状态，再执行测试。工作流无论成功或失败都会上传 `flowbus-verification-<workflow SHA>` 报告，保留 14 天，包含单测、lint、instrumentation、JVM 符号及消费者当前运行日志/UI。排查时先下载对应提交的报告；缺少文件可能表示前置步骤尚未执行，不能视为通过。

emulator-runner 的 `script` 会按行分别启动 Shell，不支持跨行 heredoc 或续行命令。API 21 流程通过单条 `bash compatibility/run-api21.sh` 调用，设备身份检查、测试签名与消费者验收在同一脚本内执行；脚本遇到任何失败立即停止。维护时不要将复合命令重新内嵌到 Action 的 `script` 中。脚本回归以模拟 ADB 验证身份拒绝及失败停止，不代替设备验收。消费者测试签名由 JDK keytool 在 `build/api21-signing/` 生成，不依赖用户目录的 debug keystore；密钥不上传、不提交，也不用于正式发布。

API 35 UI 门禁通过 `bash compatibility/run-api35.sh` 显式唤醒模拟器并解除非安全锁屏，再执行库层与示例 release 测试。退出前保存 `build/api35-environment/window.txt` 与 `power.txt`，用于分析 Activity 已恢复但 Espresso 无窗口焦点的失败；诊断不会覆盖原测试退出码。示例测试规则在 Activity 失焦时于 finish 前输出窗口状态，优先检查该现场；整批结束后的 Launcher 焦点不能单独证明失败占用方。

API 35 脚本在屏幕准备和 Gradle 之前记录 `build/api35-environment/device.json`，并断言实际 API=35、boot_completed=1；错误设备或未启动完成会停止，退出诊断仍保留。三项 mocked-ADB 回归覆盖正常准备后的原始测试失败、错误 API 拒绝及未完成启动拒绝；它们不是设备运行证据。channel 示例的 release instrumentation 增加 Activity 重建后配置幂等、新普通事件收集与不重放旧值的用例。

Google 模拟器的 Launcher ANR 系统窗口可能抢占示例焦点。测试规则在启动前检查窗口状态，仅在发现 `Application Not Responding: com.google.android.apps.nexuslauncher` 时保留现场并停止该 Launcher 进程，再正常启动示例。目标应用 ANR、其他系统错误和 Espresso 焦点断言仍按原规则失败；恢复环境本身不代表测试通过。

消费者共用设备协议将 UI 层级写入 `/data/local/tmp/` 下包含本次 runId 的文件，避免依赖旧模拟器共享存储或读到上次层级。采集输出保存在 `ui-dump.txt`；命令失败、输出 ERROR、层级缺失或 XML 无效均不能通过。出现采集失败时先检查该输出，再核对 `current.log` 和 `current-ui.xml`，不能仅凭日志 PASS 认定验收成功。

CI 的 SDK 初始化显式选择 `platform-tools`，命令行工具由 setup-android Action 准备；不要恢复其包含已下架 `tools` 包的默认安装列表。平台和 Build Tools 版本由后续安装步骤指定。

## 兼容性、源码与文档检查

基础版本保持 Kotlin 1.9.25、Coroutines 1.8.1、Lifecycle 2.6.2、AGP 8.6.1、Gradle 8.7、构建 JDK 17、Java 8 字节码、minSdk 21 / compileSdk 35 / targetSdk 35。Kotlin/compileSdk 消费者矩阵不能推导为所有旧工具链通过。

正式仓库解析成功后，同步根/模块 README 正式坐标、文档索引候选状态、本地候选说明及更新日志发布日期。保留历史版本说明和固定旧版 fixture，不全仓替换旧版本号。

核对根与模块 README 中英文、CHANGELOG、1.2.0 发布说明、API 注释、版本配置及 CI 路径。1.0.7 仅保留为已发布基准、历史或语义说明。普通无人订阅、sticky replay、关闭等待、错误策略和协作取消说明必须与实现一致。

检查 git status、工作区与暂存区差异；不提交构建产物、测试签名、密钥、日志、个人环境配置、临时沟通记录或无关改动。保留必要 fixtures、API 基准和回归测试。主仓直接维护两个模块，不能含子仓 .git/.gitmodules 或独立发布配置。

## 远程发布与回退

远程操作另行授权后执行。先配置 Maven Central token 与 GPG 签名，确认最终门禁通过；不得将凭据写入仓库。

### GPG 签名与上传凭据

两个模块使用 `signing { useGpgCmd() }`，由本机 GPG 命令行和现有 keyring 完成签名。当前 Gradle 8.7 的该签名实现读取 `signing.gnupg.*`，不会自动使用或转换传统 PGP 签名的 `signing.keyId`、`signing.password`、`signing.secretKeyRingFile`。不指定 `signing.gnupg.keyName` 时，密钥选择遵循 GPG 的配置与默认规则；发布前应核对实际签名密钥，而不是依据未生效的 `signing.keyId` 判断。

若需要明确选择签名密钥，可在用户级 `~/.gradle/gradle.properties` 设置以下配置，替换为自己的完整指纹；不要将私钥或口令写入项目配置：

```properties
# 可选：明确选择本机 keyring 中已有的签名密钥。
signing.gnupg.keyName=<完整密钥指纹>
```

默认由 GPG / `gpg-agent` 管理私钥解锁。手动执行和自动执行采用同一机制；agent 已缓存解锁口令时可能不再提示输入。签名任务为 `UP-TO-DATE` 时会复用已有签名，也不会重新解锁。没有密码框不能证明程序读取了 `signing.password`、口令泄露或签名任务实际执行；需要结合任务状态和 `.asc` 验证结果判断。需要口令时在本机 GPG 提示中输入；若解锁或签名失败，应先检查 GPG 错误和本机 agent/pinentry 环境，不通过关闭口令保护或将密码传入命令行绕过失败。

`signing.gnupg.passphrase` 是 GPG 命令行实现支持的显式口令属性，本项目不要求配置，优先使用本机 agent。发布插件还支持 `signingInMemoryKey` 等独立配置；设置该属性会切换到内存 PGP 签名，不再采用这里的 GPG 命令行路径。不要混用两种签名方式的配置来推断实际口令来源。

`mavenCentralUsername` / `mavenCentralPassword` 是 Central Portal user token 的两部分，仅用于向 Sonatype 官方 HTTPS 接口认证上传与查询，不用于解锁 GPG 私钥。长期凭据保存在用户级配置或受控 secrets 中，不打印属性值，不将口令、token、私钥或用户级配置文件纳入日志、Git 或发布包。上传的是制品、元数据、签名和校验文件；`.asc` 签名可以公开，不包含私钥或解锁口令。用户级凭据清理与口令轮换由维护者单独操作，不应通过发布脚本自动改写。

### 上传、验收与回退

后续上传批次名称采用 `GROUP-根项目名-VERSION_NAME`，当前为 `io.github.logan0817-FlowBus-1.2.0`，避免同一组下不同项目的相同版本只显示组名与版本。批次名称用于人工识别；查询、release 或 drop 必须依据本次上传返回的唯一 deployment ID，并核对批次内容。实际 Maven 坐标仍为 `io.github.logan0817:flowbus-core:1.2.0` 与 `io.github.logan0817:flowbus:1.2.0`。

发布插件 0.33.0 尚无公开的批次名称配置；根发布脚本只将两个 `createStagingRepository` 任务的暂存 artifact 标识统一为根项目名，让插件按同一个暂存标识生成名称，不修改 `MavenPublication` 或发布文件路径。单模块上传也使用同一项目名称，模块内容通过实际坐标区分。`verifyCentralDeploymentName` 在本地与 CI 核对两个暂存标识及实际发布坐标，并作为暂存任务的前置检查；该检查不执行签名或网络上传。升级发布插件后，必须重新核对其命名规则与暂存任务属性，并执行该检查及 remote dry-run。

命名修改不会自动处理已有批次。若要替换未发布批次，必须另行获得授权，按原 deployment ID 核对其状态与坐标后 drop，再上传并验收新批次；操作新批次必须使用新返回的 deployment ID。已经正式发布的坐标不能覆盖。

发布逻辑位于 gradle/release-publishing.gradle.kts，产物验证位于 gradle/release-verification.gradle.kts。publishToMavenCentral / releaseToMavenCentral 当前只上传 Central Portal deployment，不会自动 release。上传后逐个检查两个坐标及 sources/javadoc 的 .asc 签名与 checksum，再在 Portal 决定 release 或 drop。本地 Maven 产物不生成远程签名，不能用其替代签名验收。

正式发布且 Central 坐标可解析后，使用独立消费者从远端解析 1.2.0，完成普通/sticky 发送与订阅的 smoke check；核对实际解析版本，避免本地候选仓库或旧缓存替代已发布产物。

消费者回退使用已发布 1.1.0，新增 API 调用需一起回退；未发布 deployment 可 drop。禁止覆盖已发布版本。
