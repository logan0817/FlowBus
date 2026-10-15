# 实际 NavHost 消费者验证

[English](README_EN.md)

此目录是可选验证模板，使用 `androidx.navigation.compose.NavHost`、`rememberNavController` 和 `composable`，页面由标准 ComposeNavigator 管理。它与 [自定义 Navigator 返回栈验证](../navigation-consumer/README.md) 分开记录，不是发布模块，也不增加 FlowBus 基础依赖。

## 生成与构建

必须同时设置 navigationVersion 和 navHost；默认消费者及只选择 navigationVersion 的消费者不会复制本目录或引入 navigation-compose。参数值只接受 true/false，缺少导航版本会在配置时失败。

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.navigationVersion=2.4.1 -Pflowbus.consumer.navHost=true verifyMavenLocalComposeConsumer
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.navigationVersion=2.8.3 -Pflowbus.consumer.composeVersion=1.7.8 -Pflowbus.consumer.navHost=true verifyMavenLocalComposeConsumer
```

生成工程仍为 build/maven-local-compose-consumer，叠加基础 Compose、Navigation 和本目录模板；普通 ConsumerActivity 与 NavigationConsumerActivity 保留，独立 NavHostConsumerActivity 供驱动启动。生成的 NavHostVerificationProfile 根据所选组合要求观察新版默认动画窗口；2.4.1 不宣称动画覆盖。actual releaseRuntimeClasspath 图决定真实依赖，不使用 force，不把直接声明版本当作解析结果。

## 八阶段与设备入口

fixture 每个 Activity 阶段超时 30 秒，跨重建 driver 轮询上限 70 秒。真实帧、Job/Deferred 和 subscriptionCount 条件建立就绪时序，不依赖固定 delay。driver 使用共用 runId/日志/同包 UI 协议，要求 FlowBusNavHost tag 及全部八阶段。签名用已有 debug key 的测试副本，步骤见 [Android 消费者](../android-consumer/README.md#本地测试签名)；不改变生产签名。

```bash
FLOWBUS_NAVHOST_SIGNED_APK="/path/to/test-signed-navhost-release.apk"
python3 -B compatibility/navhost-consumer/verify-navhost-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_NAVHOST_SIGNED_APK" --report-dir build/navhost-device
python3 -B compatibility/navigation-consumer/verify_navigation_test.py
```

| 阶段 | 目标 |
| --- | --- |
| home-active | 实际 NavHost home 的 STARTED、RESUMED 和 sticky 订阅就绪 |
| transition-owner-thresholds | push 取消 home 的 RESUMED 回调；新版默认动画观察两页仍在组合，旧页 CREATED 无订阅、新页 STARTED 可接收但 RESUMED 尚无订阅；结束后仅 detail 活跃 |
| covered-home-does-not-receive | 稳定覆盖后 home 不接收普通/sticky |
| pop-restores-sticky-without-normal-replay | pop 恢复同一 home，sticky 到达、错过的普通事件不补发 |
| popped-owner-does-not-receive | 已 pop 的 DESTROYED detail 不接收后续事件 |
| new-detail-owner-receives-retained-source | 新 detail identity 接收旧来源保留的 sticky，旧普通事件不补发 |
| activity-recreation-restores-detail-owner | Activity.recreate 后真实返回栈恢复 detail，新 owner identity 取代已 DESTROYED 旧 owner，旧挂起回调取消 |
| restored-back-stack-returns-home | 恢复后的 BackPressedDispatcher 返回 home，其 retained sticky 到达 |

来源与验收证据由测试 ViewModel 在 Activity 配置重建中保留；没有手动 saveState/restoreState 冒充 rememberNavController 的保存。旧 entry 引用仅为身份/销毁断言短暂保留，完成或失败后清除。返回栈、页面 owner 与 Composition 由实际 NavHost 管理。

默认 STARTED 阈值会让进入动画尚未结束的新页面开始收集；旧页仍在退出动画中不代表 owner 活跃。只有当前可交互页应执行的副作用，可由业务明确选择 RESUMED；这仍不是全局单次消费或导航去重协议。fixture 给每页独立 key，不把广播误当作单接收者保证。库的默认值保持 STARTED。

## 验证结果与限制

| Navigation Compose | Compose runtime/ui | 实际 Lifecycle | API 24 |
| --- | --- | --- | --- |
| 2.4.1 | 1.5.4 | 2.6.2 | 编译、metadata、release R8 与设备自检通过 |
| 2.8.3 | 1.7.8 | 2.8.3 | 编译、metadata、release R8 与设备自检通过 |

不使用依赖 force。关闭可选参数后默认消费者无导航源码、manifest 入口或依赖残留，Lifecycle 保持 2.6.2；基础发布产物不含 fixture。每次验收应保存当前 runId 的日志/UI、实际依赖图和 APK 签名记录，不复用旧 PASS。

API 21 与远端 CI 结果按最终提交核对，入口见[发布清单](../../docs/release-checklist.md)。进程死亡恢复、深链接、任意动画配置和异常后的完整 NavHost UI 不在已验证范围。已测 Activity 配置重建依赖同一进程 retained ViewModel，不证明事件持久化。错误与受控非协作取消见[Compose 自检](../compose-consumer/README.md#错误策略与非协作回调自检)，总体范围见[兼容性评估](../../docs/compatibility.md)。
