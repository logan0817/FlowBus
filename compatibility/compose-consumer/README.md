# Compose 消费者验证示例

[English](README_EN.md)

此目录是独立消费者模板，用于评估当前 Kotlin 1.9.25 基线的 Compose 编译和 release R8 接入，不是生产模块或已发布的 `flowbus-compose` 坐标。模板的生成、构建和验证由主仓验证任务负责；本目录没有独立发布任务。

消费者默认使用 activity-compose `1.8.2`、Compose runtime/ui `1.5.4`、Compose compiler `1.5.15`，直接声明 Lifecycle `2.6.2`，消费当前候选 FlowBus 产物。另可选择 runtime/ui `1.7.8` 进行新版实验。全部仅作用于此消费者；基础库不传递引入 Compose，也不改变基础依赖、SDK 或 Kotlin 要求。两种组合的结果必须分开记录，声明版本不能代替实际解析或设备证据。

`FlowBusEffect` 接收显式 `FlowBus`、`EventKey<T>`、调用方提供的 `LifecycleOwner`，默认从 STARTED 收集。可以由导航调用方传入 `NavBackStackEntry`，示例不依赖导航库或 LocalLifecycleOwner。只有回调通过 rememberUpdatedState 更新；来源、事件名称/类型、owner、最低状态、sticky 选择变化都会重建 effect。INITIALIZED 在组合时直接被拒绝。

[实际 NavHost 验证](../navhost-consumer/README.md) 通过可选 navHost 参数加入 navigation-compose，记录标准 ComposeNavigator、默认动画阈值及 Activity 重建。转场中的新页面可能已 STARTED 而尚未 RESUMED；只有当前交互页应处理的副作用可显式选择 RESUMED。旧页面仍处于 Composition 或退出动画不代表其 owner 活跃；按实际 entry 生命周期收集，不按“是否还画在屏幕上”推断。

每次生命周期重新活跃都从来源重新取流，调用 core 的挂起收集入口并沿用来源的日志/错误策略。离开组合、owner/来源变化或生命周期停止时取消订阅和当前协作式挂起回调；不关闭传入 bus。单订阅串行执行，不默认使用 collectLatest。普通事件不留存，sticky 需显式开启；慢回调仍受既有缓冲/溢出策略影响。回调更新影响后续调用，不替换已经在执行的旧回调。业务应遵循取消，不把阻塞或不可取消工作误认为会立即停止。

可启动的 `ConsumerActivity` 实际调用 effect，并使用 AndroidView 的原生按钮和文本控件，避免引入 Material 组件。根 bus 由 Application 组装，会话 bus 经构造注入 ViewModel；同名 Toast key 分别发送到两个实例，可检查隔离。按钮可以更新回调版本后发送事件、移除/恢复 sticky effect，再观察最近值是否重放。没有准确的订阅就绪屏障，按钮事件须在页面活跃、订阅开始后发送，注册不等于立即就绪。

独立 StateFlow 表达业务计数，生命周期恢复后重新读取当前值；事件用于临时 Toast，不替代状态。ViewModel 保留配置变化中的状态，当前示例没有持久化存储或 SavedStateHandle，不能保证进程死亡后恢复。

设备验证结果按场景记录：默认 1.5.4 与可选 1.7.8 各自已在 API 24 串行通过十阶段交互，默认与新版各自新增受控 effect 九阶段已通过；进程死亡恢复、深链接及其他 NavHost 配置仍未运行；编译与 R8 不能替代这些剩余运行验证。

## 生成与构建

从主仓根目录执行，推荐一次批量完成所有产物与消费者任务：

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer
```

只构建 Compose 消费者时可执行：

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" verifyMavenLocalComposeConsumer
```

该任务依赖根 `publishToMavenLocal`，由 `prepareMavenLocalComposeConsumer` 将本目录 app 模板复制到 `build/maven-local-compose-consumer`，生成独立 settings/build 脚本，再执行 `:app:assembleRelease`。release 开启 minify/R8，实际 launcher Activity 调用 effects，编译不会只验证未被调用的工具函数。不要编辑生成目录或提交构建产物。

`-Dmaven.repo.local` 同时决定发布、清理、校验和消费者产物来源；不指定时使用 `~/.m2/repository`。根发布会先删除 FlowBus group 下 `flowbus-core/<当前版本>` 与 `flowbus/<当前版本>` 的两个目录，因此推荐专用候选目录。消费者的 `exclusiveContent` 将 FlowBus group 固定在该目录，缺失时不会从其他仓库回退旧产物。

较新 runtime/ui 实验使用独立属性，不改变基础库：

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.composeVersion=1.7.8 verifyMavenLocalComposeConsumer
```

生成配置使用仓库 Kotlin/AGP/SDK 基线。Lifecycle 直接声明 `2.6.2` 不等于最终解析版本；应保留实际依赖图及 AAR metadata 结果。声明依赖、编译通过和设备最低版本支持是不同证据。

| Compose runtime/ui | 实际 Lifecycle | 实际 core-ktx |
| --- | --- | --- |
| 默认 1.5.4 | 2.6.2 | 1.10.0 |
| 可选 1.7.8 | 2.8.3 | 1.12.0 |

两组使用 Kotlin 1.9.25、compiler 1.5.15、activity-compose 1.8.2、Coroutines 1.8.1、AGP 8.6.1 和 Java 8 字节码。保存完整 releaseRuntimeClasspath 图；直接声明与 APK META-INF 标记不代表解析版本。两组分别通过 API 24 十阶段交互、九阶段 effect 和八阶段错误/取消验证，API 21 按最终提交的 CI 设备报告验收，入口见[发布清单](../../docs/release-checklist.md)。完整范围见[兼容性评估](../../docs/compatibility.md)。

## 受控 Composition 自检入口

仓库入口为 [verify-effects-device.py](verify-effects-device.py)，运行真实 launcher Activity 的 ComposeEffectVerification 分支；原交互页面仍可正常启动。此入口需要 Python 3、已有 adb、已启动的指定设备，以及包含新 fixture 的测试签名 release APK。先以默认组合构建：

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" verifyMavenLocalComposeConsumer
```

unsigned 输出为 `build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk`。安装前按 [本地测试签名步骤](../android-consumer/README.md#本地测试签名) 对该 Compose APK 对齐/签名，替换示例中的输入与输出路径，不使用生产密钥。以下 signed APK、adb 与 serial 占位值须替换成自己实际值：

```bash
FLOWBUS_COMPOSE_SIGNED_APK="/path/to/test-signed-compose-release.apk"
python3 -B compatibility/compose-consumer/verify-effects-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_COMPOSE_SIGNED_APK" --report-dir build/compose-effects-device
```

驱动校验实际 API 和 boot_completed，安装 APK、force-stop 自己的包，携带 `flowbus.compose.verifyEffects=true` 和本次 `flowbus.consumer.runId` 冷启动。fixture 整体超时 30 秒，驱动最多轮询 40 秒；只有本次 `FlowBusCompose` 日志和当前 `com.logan.flowbus.composeconsumer` UI 同时匹配 `PASS API=<实际 API> checks=9 runId=<本次 UUID>` 与以下全部阶段才成功。FAIL、旧 runId、错误前台包、缺阶段或超时均不能通过；证据保存为 report-dir 下本次 UUID 的 start.txt、current.log、ui-dump.txt、current-ui.xml 和最终 result.txt。请与 instrumentation 串行运行，避免争用 UiAutomation。

| 阶段 | 覆盖行为 |
| --- | --- |
| `initial-replay` | 初始 sticky probe 被 effect 接收 |
| `source-replaced` | 替换 source，旧协作回调取消、新来源 probe 进入 |
| `key-replaced` | 替换 key，旧回调取消、新 key probe 进入 |
| `owner-replaced` | 替换受控 owner，销毁旧 owner 后新订阅仍接收 |
| `min-state-replaced` | STARTED 改为 RESUMED，低于阈值时不接收、恢复后接收 |
| `composition-disposed-restored` | 移除 effect 取消旧回调，恢复组合后接收最新 sticky |
| `lifecycle-stopped-restarted` | 生命周期停止取消旧回调，恢复后接收最新 sticky |
| `callback-error-continued` | 普通异常交给配置的继续型 handler，随后仍接收 |
| `destroyed-source-retained` | DESTROYED 取消；source 仍可发布，并由新 owner 接收；取消不交给普通异常 handler |

驱动判断回归可独立复现：

```bash
python3 -B compatibility/compose-consumer/verify_effects_test.py
```

其中原 effect 协议为 1 个 unittest、6 个 mocked-ADB subtests；同文件已加入 policy 的 1 test/7 subtests，合计 2 tests/13 subtests，覆盖本次 PASS、旧 PASS、当前 FAIL、其他前台包、缺阶段、安装前错误 API；已通过，但不等于 API 21 真机或模拟器通过。默认 1.5.4 与可选 1.7.8 的新 fixture 已分别通过九阶段 API 24 设备验证。进程死亡恢复、深链接及其他 NavHost 配置仍需独立验证；勿将此前十阶段临时 driver 作为仓库入口。

## 错误策略与非协作回调自检

`ConsumerActivity` 增加独立 `flowbus.compose.verifyPolicies` 模式，由 `verify-policies-device.py` 启动。它使用真实 Compose runtime 的 `Composition` 与独立 `Recomposer` 运行同一个 FlowBusEffect，结果仍由外层 Activity 的真实组合显示。内部组合只有 effect、没有 UI 节点；受控 LifecycleRegistry owner 不等于导航返回栈或 NavHost UI。

测试重组器隔离预期异常，避免终止外层结果页面。默认错误处理器未被替换，FlowBusEffect 内没有新增捕获；自检同时观察 CoroutineExceptionHandler 和 runner 的终止原因，要求异常场景的重组器关闭、订阅归零并保留原始异常身份。这是测试基础设施，不是建议业务通过监督作用域忽略失败。

| 八阶段 | 断言 |
| --- | --- |
| `default-rethrow-terminates-source-retained` | 默认策略传播原始回调异常、终止收集，但同一来源仍能供新组合订阅 |
| `ignore-logs-and-continues` | Ignore 保留 logger 告警并继续下一条 |
| `custom-handler-context-and-continue` | 自定义处理器收到事件名、类型、phase、scope/sticky/dispatcher 上下文，返回后继续 |
| `legacy-handler-continues` | fromThrowableHandler 收到同一异常并继续 |
| `handler-failure-propagates` | 自定义处理器抛出的新异常继续传播并终止组合 |
| `cancellation-bypasses-error-handler` | CancellationException 不进入普通错误策略或 logger，后续生命周期切换可重订阅 |
| `noncancellable-restart-waits-for-callback` | 已请求取消的 NonCancellable 回调仍等待屏障，生命周期重启不并发执行新回调 |
| `blocking-worker-restart-waits-for-callback` | Dispatchers.Default 中受控阻塞工作不能被取消强制停止，释放后结束并重启订阅 |

非协作阶段使用可显式释放的 Deferred/CountDownLatch；等待真实 Job 取消状态，检查订阅数量及回调不重叠，没有用固定 delay 推断。finally 总会释放屏障并销毁内部组合。取消不回滚回调已发生的副作用；不要在主线程阻塞，也不要认为任意业务回调都能被立即停止。同一 repeatOnLifecycle 的重启需要等待旧回调结束，这与重建其他来源/owner 的独立 effect 不同。

构建与测试签名使用上文相同消费者 APK，驱动 CLI 与共用协议保持一致：

```bash
FLOWBUS_COMPOSE_SIGNED_APK="/path/to/test-signed-compose-release.apk"
python3 -B compatibility/compose-consumer/verify-policies-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_COMPOSE_SIGNED_APK" --report-dir build/compose-policies-device
python3 -B compatibility/compose-consumer/verify_effects_test.py
```

驱动要求本次 runId 的 FlowBusComposePolicy 日志、同包 UI 与全部八阶段；fixture 超时 30 秒，driver 40 秒。mock 共 2 tests / 13 subtests，验证协议与错误边界，不代替设备。两组 API 24 自检通过；API 21 与远端 CI 结果按最终提交核对，入口见[发布清单](../../docs/release-checklist.md)。
