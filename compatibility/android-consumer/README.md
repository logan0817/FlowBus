# 普通 Android Maven 消费者设备自检

[English](README_EN.md)

此目录保存独立普通 Android consumer 的 Activity 源码模板，用于检查真实候选 Maven 产物经过 release R8 后的设备行为。它不是可单独构建的 Gradle 工程，也不进入 FlowBus 发布库。基础库不因此新增依赖、提高 minSdk 或配置生产签名。

生成工程位于 `build/maven-local-consumer`。`prepareMavenLocalConsumer` 追踪 Activity、同目录 Java fixture 和消费者配置作为 inputs；模板变化时重新生成工程，复制 Activity 和 `.java` 源码，生成 `exported=true` 的 MAIN/LAUNCHER manifest，并保留原 `ConsumerApplication` 的 channel smoke 入口。重新生成会覆盖整个生成目录，修改应写入此源码模板。

## 生成与构建

从项目根目录执行，沿用项目已有 JDK、Android SDK 和 Gradle wrapper：

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.kotlinVersion=1.8.22 -Pflowbus.consumer.compileSdk=33 verifyMavenLocalConsumer
```

默认先发布当前候选到隔离 Maven 目录；CI 下游传入 `-Pflowbus.verification.reuseCandidate=true`，校验封存候选后直接使用，不重新发布。候选清单和本地复现入口见[发布清单](../../docs/release-checklist.md#ci-任务关系与候选边界)。随后生成 consumer 并执行 `:app:assembleRelease`；FlowBus group 通过 exclusiveContent 仅从该目录解析。单独 `prepareMavenLocalConsumer` 只生成工程，不发布候选、不编译或运行设备。`flowbus.consumer.kotlinVersion` 和 `flowbus.consumer.compileSdk` 只选择普通消费者相应配置，不改变库自身依赖或工具链；compileSdk 默认 35，targetSdk 保持仓库基线 35，minSdk 21，Java 字节码目标 1.8。

release 使用 R8，未配置签名。预期输出为：

- APK：`build/maven-local-consumer/app/build/outputs/apk/release/app-release-unsigned.apk`
- mapping：`build/maven-local-consumer/app/build/outputs/mapping/release/mapping.txt`

此目录验证本地源码生成的 **1.3.0** 发布候选，尚未正式发布；Maven Central 当前正式安装仍使用 **1.2.0**。兼容基线包含 1.0.7、1.1.0 和正式 1.2.0；本 APK 的旧版实际调用 fixture 仍单独固定为 1.1.0，其他基线按相应静态符号或 core 二进制入口验证，不混称本 APK 已运行全部旧 Android 字节码。本地候选验收不能替代公开仓库验证。

## 本地测试签名

安装前需要本机 Android SDK Build Tools 的 zipalign/apksigner，以及自己已有的本地测试 keystore 和 alias。不要使用生产密钥，不提交 keystore 或密码；以下占位路径须先替换，apksigner 按工具提示读取密码：

```bash
FLOWBUS_BUILD_TOOLS="/path/to/android-sdk/build-tools/35.0.0"
FLOWBUS_TEST_KEYSTORE="/path/to/local-test.jks"
FLOWBUS_TEST_KEY_ALIAS="local-test"
"$FLOWBUS_BUILD_TOOLS/zipalign" -f -p 4 build/maven-local-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/maven-local-consumer/app/build/outputs/apk/release/app-release-aligned.apk
"$FLOWBUS_BUILD_TOOLS/apksigner" sign --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias "$FLOWBUS_TEST_KEY_ALIAS" --out build/maven-local-consumer/app/build/outputs/apk/release/app-release-test-signed.apk build/maven-local-consumer/app/build/outputs/apk/release/app-release-aligned.apk
"$FLOWBUS_BUILD_TOOLS/apksigner" verify --verbose build/maven-local-consumer/app/build/outputs/apk/release/app-release-test-signed.apk
```

若设备上同 applicationId 应用的签名不同，安装会失败；应先确认设备与已有应用的用途，避免直接卸载其他任务需要的应用。

## 指定设备冷启动并核对本次结果

使用 Python 3 与 [verify-device.py](verify-device.py) 驱动验证。先执行 `adb devices -l`，选择状态为 device 的目标；下例的 adb 路径、serial 和期望 API 必须替换成实际值，APK 必须已完成上述本地测试签名：

```bash
python3 compatibility/android-consumer/verify-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --apk build/maven-local-consumer/app/build/outputs/apk/release/app-release-test-signed.apk --expected-api 21
```

驱动先检查实际 API 与 `--expected-api` 一致及 `sys.boot_completed=1`，然后安装已签名 APK，force-stop 自己的 `com.logan.flowbus.consumer` 包，并携带本次 UUID 冷启动 Activity。UUID 通过 intent extra `flowbus.consumer.runId` 传入；手动启动未指定时 Activity 自行生成 UUID。每次 PASS/FAIL 日志和结果 UI 都携带 runId。

Activity 最初显示 `FlowBus consumer: RUNNING`，自检整体超时为 10 秒，Java 互通阶段另外有 5 秒上限。驱动使用 API 21 支持的按 tag 读取 logcat 方式，最多轮询 30 秒，只接受本次 `PASS API=<设备实际 API> checks=7 runId=<本次 UUID>`；匹配后解析当前 UI XML，要求该包下的文本同时包含同一 PASS 与七组完成名称。它不清理全局 logcat，也不依赖较新系统的进程日志筛选选项；本次 UUID 隔离历史 PASS 和其他运行。

成功时脚本退出码为 0，打印同一 PASS 和证据目录；本次 FAIL、超时、启动/安装失败、API 不符或前台 UI 冲突均以非零退出。默认证据位于 `build/android-consumer-device/<本次 UUID>/`，包含 `start.txt`、持续更新的 `current.log`、UI 采集输出 `ui-dump.txt`、成功匹配后读取的 `current-ui.xml`，以及通过全部检查后才写入的 `result.txt`；也可用 `--report-dir` 选择父目录。失败阶段尚未生成的文件可能不存在，应保留已有日志分析。持续 RUNNING、进程退出、缺少本次日志或只找到旧 PASS 均不能记录为通过。设备运行结果不能仅由 APK 存在、mapping 或编译成功推断。

## 驱动回归与 CI 门禁

无需设备即可运行驱动的 mocked-ADB 回归：

```bash
python3 -B compatibility/android-consumer/verify_device_test.py
```

九项驱动回归，覆盖本次 PASS 与 UI 匹配、缺少 Java 阶段拒绝、历史 PASS 超时拒绝、本次 FAIL 拒绝、其他前台包拒绝、错误 API 在安装前拒绝，以及 UI dump 失败/缺失/命令失败。它们模拟 ADB 输出，只验证驱动判断与报告行为，不是 Android API 21 上实际安装、Main/Lifecycle、自检或 R8 运行的证据。

CI 先通过 `bash compatibility/prepare-api21.sh` 在模拟器启动前构建 Kotlin 1.8.22 / compileSdk 33 普通 Android、固定基线 Compose、Navigation、NavHost，并分别保存 `build/api21-apks/{android,compose,navigation,navhost}.apk`；设备入口通过 `bash compatibility/run-api21.sh` 执行复合命令，并在第一处失败停止。另有四项 Shell 入口回归，实际执行身份记录的 heredoc，覆盖错误 API、未完成启动、库测试失败及测试密钥生成失败后停止；不启动设备。运行全部十三项回归：

```bash
python3 -B -m unittest discover -s compatibility/android-consumer -p '*_test.py' -v
```

CI 已新增该驱动回归步骤，并在既有 API 21 库层 instrumentation 后，在 build/api21-signing/ 生成专用测试 keystore，为当前 minified Maven consumer 本地测试签名，再执行设备驱动。当前远端结果按最终提交核对，入口见[发布清单](../../docs/release-checklist.md)。

## 覆盖与限制

| 完成名称 | 自检范围 |
| --- | --- |
| `normal-sticky-scope` | normal 订阅/发送；sticky 读取与消费后为空；owned scope closeSuspending；同名重开不保留旧缓存 |
| `legacy-android` | generic Flow 收集；全局与 owner 定向 sticky 隔离；UNDISPATCHED 先订阅普通 scoped Flow，再使用旧 scoped 发布入口 |
| `owner-suspending` | owner 定向挂起回调实际挂起后接收，并取消订阅 |
| `explicit-lifecycle` | 显式 scope 串行挂起回调；受控停止取消；恢复读取最新 sticky；另一个实例不串流；DESTROYED 不关闭 source |
| `channel-config` | owner 配置幂等、零容量 SUSPEND 下 tryPost 拒绝/emit 接收、实际策略与 bus 默认摘要区分 |
| `binary-1.1.0-android` | 仅用固定 1.1.0 core/AAR 编译的挂起/实例入口，在候选运行图上实际执行 |
| `java-interop` | 真实 `.java` 调用新静态入口与 SAM；Java/Kotlin 双向收发、global/owner 及普通/sticky 隔离、实际订阅通知、STOP/重进/销毁、主动和在途取消、分流诊断与参数校验 |

这些检查运行在真实 Dispatchers.Main.immediate，使用受控 LifecycleRegistry owner 变更 CREATED/STARTED/DESTROYED；它们不是 Activity 前后台、旋转、导航或进程重建的交互证据。Deferred、Channel、实际 onSubscribed 通知与直接收集的 UNDISPATCHED 建立订阅/完成时序，无需任意 delay。七组共享 10 秒超时；任何异常或超时输出 FAIL。

新增 [JavaApiCalls.java](app/src/main/java/com/logan/flowbus/consumer/JavaApiCalls.java) 中的业务调用由 javac 编译，不要求 Java 业务处理 Kotlin `Unit`、`KClass` 或 suspend 参数；它与 Kotlin Activity 一同消费候选 Maven AAR 并经过 release R8。Activity 用五路 Channel 分别断言 global/owner 的普通/sticky 以及 Kotlin 收集器，等待每一次真实订阅通知后才发送，不把 inspector 订阅数当就绪屏障。STOP 后普通消息丢弃、sticky 仅回放最新值；主动取消 Java owner 订阅后，Kotlin 同源订阅继续工作；Java 回调内部取消自己时先核对取消请求已记录、回调尚未完成，再由外层等待结束。诊断只读核对各流计数，未知通道查询不登记 channel；Android 来源首次获取仍可能创建 FlowEventBus。

各组通过 finally 清理自己持有的 owner、订阅、source 或 channel；显式 lifecycle 组先取消订阅并同步关闭 source/channel，再 join，避免已取消协程在挂起等待时跳过资源关闭。Activity onDestroy 取消验证 scope。正常销毁导致的取消既不生成 PASS，也不误报 FAIL；验证中途关闭 Activity 应重新冷启动完成验证。

历史 1.1.0/1.2.0 的结果见兼容性评估；它们不证明当前七阶段发布候选已通过设备验证。API 21 或其他设备必须核对最终候选的实际报告，APK 名称、签名或静态检查不代表最低系统通过。完整已测组合与未认证范围见[兼容性评估](../../docs/compatibility.md)。

## 已发布 1.0.7 AAR 的 JVM 符号核对

原版固定来自 [Maven Central](https://repo.maven.apache.org/maven2/io/github/logan0817/flowbus/1.0.7/flowbus-1.0.7.aar)。固定 SHA-256 与 Central 的 .sha256 一致，为 84380a3e456285a77133a4535dd4ff69b2eeb85ee516e3a24326411503ff5592。候选 AAR 来自隔离目录 build/candidate-maven。

[api/flowbus-1.0.7.api](api/flowbus-1.0.7.api) 保存提交 c886b93 的已发布版本公开 API 快照；脚本已确认其中 88 个成员实际存在于原版 AAR 的 classes.jar。不要用当前包含新增候选 API 的 library-android/api 快照替换该基准。

先按本文已有命令生成候选产物，再从项目根目录复现静态检查：

```bash
mkdir -p build/published-baseline
curl --fail --location --silent --show-error \
  --output build/published-baseline/flowbus-1.0.7.aar \
  https://repo.maven.apache.org/maven2/io/github/logan0817/flowbus/1.0.7/flowbus-1.0.7.aar

python3 - <<'PY'
import hashlib
from pathlib import Path
actual = hashlib.sha256(Path("build/published-baseline/flowbus-1.0.7.aar").read_bytes()).hexdigest()
assert actual == "84380a3e456285a77133a4535dd4ff69b2eeb85ee516e3a24326411503ff5592", actual
PY

python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --baseline-artifact build/published-baseline/flowbus-1.0.7.aar \
  --candidate-artifact build/candidate-maven/io/github/logan0817/flowbus/1.3.0/flowbus-1.3.0.aar \
  --baseline-api-snapshot compatibility/android-consumer/api/flowbus-1.0.7.api \
  --javap /path/to/jdk17/bin/javap \
  --output build/android-jvm-symbol-verification.json
```

实际原版 15 个稳定具名类、179 个 JVM public/protected 成员描述符完成比较，其中公开快照的 88 个描述符在原版与候选均存在，受支持公开描述符缺失为 0。原始比较有一处其他 JVM 移除：createFlowBusConfig$library_android_release，报告的 otherJvmRemovals 保留该记录。源码确认它是 Kotlin internal，未标记 PublishedApi，不在公开快照中；已有构造失败恢复修订将配置生成改为实际 FlowBus 构造成功后再冻结配置。该差异不属于受支持公开 API，报告仍保留原始比较结果。

这是 classes.jar 的静态符号证据，不等于 Android 旧消费者字节码在设备上已运行，也不检查全部继承/可见性、资源、manifest 或 consumer rules。其他 JVM 移除需要结合源码和旧调用路径人工判断，不能因它不在公开快照中就自动宣称所有 inline 路径兼容。脚本当前 7 项回归通过；原有 core 运行报告模式复验仍保留 631 个描述符。CI 固定原版摘要并执行 AAR 检查；远端和 API 21 设备结果统一按[发布清单](../../docs/release-checklist.md)验收。

## 1.1.0 原版编译的 Android 消费者

legacy/src/main 是独立 Android library fixture，compileOnly 只含正式 1.1.0 的 core JAR/AAR classes.jar 和原有 Lifecycle；prepare 先核对 baselines.json 中的固定 SHA-256，候选 APK 只将其编译好的 class 与当前 1.3.0 runtime 一起交给 R8。旧实现代码不引用新配置 API。

verifyMavenLocalConsumer 先执行 :legacy:testReleaseUnitTest，再构建 minified APK。单测使用候选 Maven Android/core 依赖并明确剔除原版 compileOnly JAR，校验实际加载的 FlowBus/FlowEventBus class 字节与候选制品一致，运行旧挂起回调及 root/shared/owned/owner 入口，检查旧 main class 全部摘要在执行前后不变。报告为 build/maven-local-consumer/legacy/build/reports/binary-android-compatibility.txt。JVM 单测使用可控 Main dispatcher 和 createUnsafe LifecycleRegistry，不代表 Android Main/设备运行；设备七阶段还会执行同一旧 fixture。

1.1.0 AAR 固定 SHA-256：bc7eb2750832da36f49f6be797ea76e922b864093b8ff0d3513b41a3f3450b6a；core：ffafa4c77ed1e7b8183f53de26a14904b37a5d6958b49fa6930d2273173fcb9f。旧公开 API 快照为 api/flowbus-1.1.0.api，来自升级前的已发布 1.1.0 快照，并与正式 AAR 字节码交叉核验。

## 正式 1.2.0 兼容基线

[api/flowbus-1.2.0.api](api/flowbus-1.2.0.api) 保留正式发布时的 Android 公开符号，固定原版摘要在 [baselines.json](../core-binary-consumer/baselines.json)；该快照不能用新开发版 apiDump 覆盖。1.2.0 core 原版编译、候选替换运行由[core 二进制消费者](../core-binary-consumer/README.md)执行。本 APK 目前仍运行 1.1.0 legacy 字节码与新 Java 业务路径；1.2.0 Android 静态符号检查和 core 运行不等于旧 1.2.0 Android 字节码已在设备上运行。
