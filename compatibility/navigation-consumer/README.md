# Navigation 消费者设备自检

[English](README_EN.md)

此目录是可选消费者 fixture，使用真实 `NavHostController` 和 `NavBackStackEntry` 验证 FlowBusEffect 对 Navigation 返回栈生命周期的响应。它不是发布模块；导航依赖仅在显式选择消费者参数时加入，不提高 FlowBus 基础坐标的依赖要求。默认 Compose consumer 不复制本目录源码/manifest，也不声明 Navigation 依赖。

标准 navigation-compose NavHost 另见 [独立 NavHost fixture](../navhost-consumer/README.md)，通过额外 navHost 参数启用，覆盖实际页面组合、默认动画阈值及 Activity 配置重建。两类验证分开记录，不把本目录自定义 Navigator 的结果扩大为 NavHost 动画证明。

主仓生成任务先复制 [Compose consumer](../compose-consumer/README.md)，选择 `flowbus.consumer.navigationVersion` 时再叠加本目录 app 模板和独立 manifest。生成工程仍为 `build/maven-local-compose-consumer`，普通 ConsumerActivity 保留 launcher，导航 driver 显式启动 exported NavigationConsumerActivity。不同参数会重新生成该目录，不应手工修改或提交生成文件。

## 构建、测试签名与设备入口

从主仓根目录执行，使用 Navigation runtime 2.4.1 与默认 Compose 1.5.4：

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.navigationVersion=2.4.1 verifyMavenLocalComposeConsumer
```

较新组合只用于独立消费者评估；选择 Navigation 2.8.3 / Compose 1.7.8 时，两项参数必须同时设置：

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.navigationVersion=2.8.3 -Pflowbus.consumer.composeVersion=1.7.8 verifyMavenLocalComposeConsumer
```

`navigationVersion` 只增加此生成消费者的 `androidx.navigation:navigation-runtime`，不加入 navigation-compose NavHost。实际依赖仍以 releaseRuntimeClasspath 图为准，参见 [Compose 权威依赖说明](../compose-consumer/README.md#生成与构建)。任务发布候选到隔离 Maven 目录、生成工程、执行 release R8，不自动签名或运行设备。

unsigned APK 为 `build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk`，mapping 为同一 app 输出的 `mapping/release/mapping.txt`。安装前按 [本地测试签名步骤](../android-consumer/README.md#本地测试签名) 对该 Navigation APK 对齐/签名，替换输入输出路径，不使用生产密钥。以下占位值须改为本机已签名 APK、已有 adb、目标设备 serial 和实际期望 API：

```bash
FLOWBUS_NAVIGATION_SIGNED_APK="/path/to/test-signed-navigation-release.apk"
python3 -B compatibility/navigation-consumer/verify-navigation-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_NAVIGATION_SIGNED_APK" --report-dir build/navigation-device
```

驱动薄入口复用 [共用设备协议](../device_verification.py)，检查 API/boot、安装已签名 APK、force-stop 自己的包，携带本次 runId 冷启动 NavigationConsumerActivity。fixture 整体超时 30 秒，driver 最多轮询 40 秒；仅接受 `FlowBusNavigation` tag 的本次 `PASS API=<实际 API> checks=7 runId=<本次 UUID>`，并要求当前 `com.logan.flowbus.composeconsumer` UI 匹配同一结果和七个阶段名称。失败、旧结果、错误前台包、缺阶段或超时均非零退出；证据位于 report-dir/runId 的 start.txt、current.log、current-ui.xml 和通过后才生成的 result.txt。请与 instrumentation/其他 UI driver 串行运行。

普通 Android 与 Compose effect driver 也复用此协议，既有 CLI 和超时保持。导航集成协议 mock 可运行：

```bash
python3 -B compatibility/navigation-consumer/verify_navigation_test.py
```

协议回归覆盖七阶段成功、缺阶段拒绝和错误检查数量拒绝，并与 NavHost 协议同文件验证。mock 只证明判断协议，不代表返回栈设备运行或 API 21 通过。

## 七阶段与验证边界

NavHostController 绑定真实 Activity 的 LifecycleOwner、ViewModelStore 与 BackPressedDispatcher。自定义 Navigator 仅创建 destination，由标准 Navigation push/pop 管理 entry 生命周期。Composition 故意保留被覆盖及已 pop 页面，确保取消来自真实 entry 生命周期，而非仅靠移除组合。

| 阶段 | 验证目标 |
| --- | --- |
| `home-active` | home entry 活跃并能收到普通事件 |
| `navigate-stops-covered-home` | push detail 后 home 降为 CREATED，挂起回调取消且普通/sticky 订阅为零 |
| `covered-home-does-not-receive` | 被覆盖期间 home 不处理 normal 或 sticky |
| `pop-restores-sticky-without-normal-replay` | pop 恢复同一 home，最新 sticky 到达，错过的 normal 不 replay |
| `popped-owner-does-not-receive` | DESTROYED detail 不处理后续 normal/sticky |
| `new-detail-owner-receives-retained-source` | 再次 push 的新 detail 身份接收 retained sticky，旧 detail 仍 DESTROYED |
| `back-dispatcher-returns-home` | 通过真实 BackPressedDispatcher 返回 home 并取消新 detail 回调 |

测试通过 probe entered/cancelled 与帧驱动的诊断 subscriptionCount 就绪条件建立时序，不用固定 delay。诊断快照仅用于此测试就绪，不作为业务控制或处理完成保证。

这是实际 NavController/entry 返回栈验证，仍不等于 navigation-compose NavHost 动画、saved state/进程恢复、深链接或全部导航场景。默认 Rethrow、Ignore、旧式/自定义处理器和两类受控非协作回调已在独立 Composition 的两条 API 24 组合通过八阶段，详见 [错误与取消自检](../compose-consumer/README.md#错误策略与非协作回调自检)；这不等于 NavHost 在异常后的 UI 行为已验证。API 21 完整设备验证仍未完成。

## 验证结果与限制

| Navigation | Compose runtime/ui | 实际 Lifecycle | API 24 |
| --- | --- | --- | --- |
| 2.4.1 | 1.5.4 | 2.6.2 | 编译、metadata、release R8 与设备自检通过 |
| 2.8.3 | 1.7.8 | 2.8.3 | 编译、metadata、release R8 与设备自检通过 |

不使用依赖 force。关闭可选参数后默认消费者无导航源码、manifest 入口或依赖残留，Lifecycle 保持 2.6.2；基础发布产物不含 fixture。每次验收应保存当前 runId 的日志/UI、实际依赖图和 APK 签名记录，不复用旧 PASS。

API 21 与远端 CI 尚未验收。进程死亡恢复、深链接、任意动画配置和异常后的完整 NavHost UI 不在已验证范围。已测 Activity 配置重建依赖同一进程 retained ViewModel，不证明事件持久化。错误与受控非协作取消见[Compose 自检](../compose-consumer/README.md#错误策略与非协作回调自检)，总体范围见[兼容性评估](../../docs/next-version-plan.md#兼容性评估)。
