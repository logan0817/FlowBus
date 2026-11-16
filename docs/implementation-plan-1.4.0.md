# FlowBus 1.4.0 实施方案：事件排障

**当前状态（2026-11-16）：1.4.0 已正式发布。** W0–W7、PR #18、合并后 main CI 及正式公开验收完成；第 1–9 节保留当时开发与发布前阶段的范围和门禁，正式发布状态以第 10 节及[发布说明](release-notes-1.4.0.md)为准。

原发布前阶段记录（已完成）：2026-11-16 生产实现审查、最终 PR 与 main CI 通过。当时公开安装版本为 **1.3.1**，该阶段仅授权实现、兼容/性能/设备验收及发布前检查；后续正式发布授权与结果见第 10 节。

[文档索引](README.md) · [排障指南](../TROUBLESHOOTING.md) · [兼容性](compatibility.md) · [发布清单](release-checklist.md)

## 1. 目标、基线与范围

基线为 `main` 的 `1a18d376157916daa34f3128e81a4a1b811c0918`，生产发布基线为 1.3.1。工作分支为 `feat/event-diagnostics`。保留 Java 8 字节码、Android API 21、Kotlin 1.9.25、Coroutines 1.8.1、Lifecycle 2.6.2 及现有默认投递/错误策略。

用户需要回答的问题是：

1. 事件发到了哪个实例、哪个 scope、哪一代同名流？
2. 发送操作返回了什么结果？挂起发送是否被取消？
3. 订阅是否真正建立、何时退出、为何退出？
4. 回调失败发生在哪个阶段？日志或诊断本身会不会改变业务行为？
5. Android 生命周期、Java 和 Compose 是否使用同一套事实？

本版采用实例级、默认关闭、可关闭的结构化观察器。它不提供可靠消息、处理确认、精确丢弃追踪、跨进程传输或自动上传。core 不保存无界历史、不创建后台线程或诊断协程。示例页使用有界本地记录，供开发者复现问题。

## 2. 审查结果

以下位置以基线符号为准；增强缺口不冒充已确认的投递 Bug。

| 编号 | 分类 | 证据、触发与影响 | 实施 |
| --- | --- | --- | --- |
| F1 | P1 缺陷 | `FlowBusCollector.kt` 在三个错误分支先执行 `logger.warn`。logger 抛异常时原业务异常被替换，`Ignore`/自定义 errorHandler 不执行，与 logger 只负责记录的契约冲突 | 先加入可运行回归，再隔离辅助日志失败，保持原错误策略 |
| F2 | 排障缺口 | `FlowBusStore.recordResult` 只有聚合计数；`emit` 取消无完成结果 | 输出原 PostResult；取消单独记录，不修改 rejected 指标含义 |
| F3 | 接入陷阱 | `ScopedFlowBus.post/postSticky` 经 `store.post` 把内部结果 scopeName 写成 null | 诊断接入时统一来源，保留旧公开签名 |
| F4 | 排障缺口 | removeEvent/removeSticky/scope 重开后旧 Flow 仍可存在，新发送进入新流；只按 scopeName+eventName 会混淆 | 实例、store、流及单次订阅使用不携带业务对象的进程内身份 |
| F5 | 排障缺口 | Inspector 不是历史，Job 存在也不代表 lifecycle 已 STARTED；旧 Android 入口直接调用共享 collector | 在实际注册后、replay 前记录开始，在 collect 真正退出后记录结束；共享 collector 统一记录业务错误 |
| F6 | 相邻开销 | `stickyReplayCount` 为读数量构造 payload 列表；成功回调路径提前计算 dispatcher 字符串 | 用已有 replaySize；仅在错误分支读取 dispatcher 描述，不声称未经测量的整体提速 |
| F7 | P1 相邻缺陷 | Android `FlowEventBus.postOrWarn/warnIfRejected` 已取得缓冲拒绝结果后直接调用 logger，失败 logger 会覆盖 false/结果；延迟路径 catch 再次调用同一 logger，可能形成未处理协程异常 | 新增零缓冲活跃订阅回归；仅隔离三处 warning 的普通异常，不捕获真正发送/配置/取消错误 |

审查未确认新的 Android/Compose 投递缺陷。现有 sticky 重入、scope 关闭及非协作取消算法继续保留，不为排障重写。

## 3. 同类项目取舍

使用固定版本官方源码作为契约参考，不假定这些版本为当前最新版：

| 参考 | 借鉴 | 不采用 |
| --- | --- | --- |
| [EventBus V3.3.1](https://github.com/greenrobot/EventBus/blob/V3.3.1/EventBus/src/org/greenrobot/eventbus/EventBus.java) | 无人订阅与回调异常分开，诊断递归有保护 | 原事件、subscriber、Throwable 引用；将诊断发回业务总线 |
| [SharedFlow 1.8.1](https://github.com/Kotlin/kotlinx.coroutines/blob/1.8.1/kotlinx-coroutines-core/common/src/flow/SharedFlow.kt) / [onSubscription](https://github.com/Kotlin/kotlinx.coroutines/blob/1.8.1/kotlinx-coroutines-core/common/src/flow/operators/Share.kt) | 注册后、replay 前通知；锁外恢复；忠实呈现 tryEmit 语义 | 把 accepted 或 DROP 策略解释为已处理、精确丢弃 |
| [DebugProbes 1.8.1](https://github.com/Kotlin/kotlinx.coroutines/blob/1.8.1/kotlinx-coroutines-debug/src/DebugProbes.kt) | 显式启停、说明抓取创建堆栈的成本 | 调试代理依赖、每次发送抓堆栈、轮询 |
| [Reactor Hooks v3.6.6](https://github.com/reactor/reactor-core/blob/v3.6.6/reactor-core/src/main/java/reactor/core/publisher/Hooks.java) | 按 key 撤销 hook、识别递归调用风险 | 进程全局累计 hook、值的 toString、观察器失败穿透业务 |
| [RxJavaPlugins v3.1.8](https://github.com/ReactiveX/RxJava/blob/v3.1.8/src/main/java/io/reactivex/rxjava3/plugins/RxJavaPlugins.java) | 区分无法交付错误与程序错误，提供显式错误处理入口 | 默认打印业务异常消息/堆栈或改变 FlowBus 原错误策略 |

## 4. 公开契约与实现边界

- 新增 `FlowBus.observeDiagnostics(observer)` 与幂等 `close()` 句柄；DefaultFlowBus、Android 全局/owner 和 Java 可访问同一机制。不向 `FlowBusConfig` data class 主构造追加字段，保留 constructor/copy/default 的 ABI。
- 元数据包含实例/store/流身份、eventName、scopeName、normal/sticky、订阅身份及阶段；发送成功记录直接使用既有 PostResult。异常只存类型名，不存 Throwable、message、stack、payload、owner、Job 或 callback。
- 观察器在触发阶段的线程同步执行，可能并发；普通观察器异常隔离。JVM 致命错误不作恢复保证。观察器应快速返回；需要异步导出时由应用使用有界队列并脱敏。
- 同步重入诊断受保护：观察器中 post/inspect/自关闭可以正常执行，但同一线程中由诊断再次触发的诊断通知被抑制。跨线程顺序及应用自行调度的递归不作保证。
- 所有通知在 store、sticky replay/commit、scope registry 与 operation 锁外执行。关闭不等待回调，不清理业务缓存、不取消业务订阅；已开始的回调允许结束。
- 默认关闭时返回原普通 SharedFlow，不增加诊断 Flow 包装、不分配诊断事件、不读取诊断时间或逐次分配 ID。观察器列表及流身份在启用后按需创建；默认仍有 Runtime、普通流容器/字段与辅助分派成本，不能宣称零开销。完整订阅观察要求**先安装观察器，再取得并收集 Flow**；此前已经返回的 Flow 不补历史。临时开启后，可结合 Inspector 查看已有订阅，重建需要追踪的订阅。
- normal/sticky 在底层发送结束、取得结果后同步通知；Unconfined 下回调错误可能先于发送完成。subscriptionCount 是发送前取样，accepted 不等于处理成功；不承诺每次并发发送与接收一一配对。
- generic Flow/map/filter 的下游失败只可记录收集结束原因。只有 FlowBus 托管 collector 能准确报告 ValueCast/SubscriberCallback；原 onSubscribed 失败仍直接传播，不进入业务 errorHandler。

## 5. 任务与验收

| 阶段 | 工作 | 完成条件 | 状态 |
| --- | --- | --- | --- |
| W0 | 审查、官方对照、版本方案 | 缺陷和增强分开；契约可执行 | 已完成；F1 修复前 5 项测试中 4 项失败 |
| W1 | 日志隔离与相邻开销 | throwing logger 回归；原错误/取消测试通过 | core 5 项及 Android 10 项修复后回归通过；保留原取消与错误策略 |
| W2 | core 观察器、身份、发送/订阅/错误 | 默认关闭、异常隔离、重入、自关闭、并发、scope/旧流/取消覆盖 | 22 项诊断及 3 项并发回归通过；PR 首轮 327 JVM / 九项 CI 通过 |
| W3 | Android/Java/Compose 接入 | lifecycle 真实起止，旧入口一致，真实 Java 编译，Compose 不因重组假重订阅 | 本地及云端四编译器、Java8、当前候选 API21/35 Android/effect/policy 8/12/11 通过 |
| W4 | 开发者示例、双语使用/排障说明 | 可操作的无人订阅、来源错配、拒绝、回调错误；有界记录、正确释放 | 双语文档及示例完成；API35 release instrumentation 22 项通过，包含新增四场景/重建两项 |
| W5 | 候选与兼容门禁 | 三模块 API、JVM、lint、R8、Java8、四 Kotlin、新旧二进制及协议验证 | 本地 327 JVM、API、三 lint 0 issue、R8、Java8、四 Kotlin、五版 core 二进制及较新 Navigation 消费者通过 |
| W6 | 性能与 API21/35 | 同环境 1.3.1 对照默认成本，单独衡量启用成本；设备验证本轮候选身份 | 本地默认/注册/开关及云端同 runner 默认对照完成，API21/35 证据审计通过；成本与限制见第 9 节 |
| W7 | 发布前独立审查与收尾 | 最终输入全部门禁通过；阻塞缺陷清零；证据可追溯 | 独立只读审查、最终 PR verify 和合并后 main 九项 CI 均通过；固定源码见第 10 节 |

具体行为测试：

1. root/shared/owned/Default/Android owner 的发送及普通/sticky 分离；旧流与同名新流身份分离。
2. 无订阅 accepted、SUSPEND tryPost 拒绝、emit 挂起取消；DROP 策略只报告策略。
3. 开始发生在注册后且 replay 前；取消、onSubscribed 抛错、业务错误后准确退出且一次结束。
4. Ignore 后继续、Rethrow 保留原异常、CancellationException 不误报 callback failure。
5. observer/logger 抛普通异常不改变发送、释放或 errorHandler；自关闭、重入 post/inspect、并发注册关闭无死锁。
6. Android CREATED/STARTED/CREATED/STARTED/DESTROYED，Java listener，Compose callback 更新、source 变化、离开组合与 NonCancellable 回调。
7. 数据模型无业务对象引用；不调用 payload/Throwable 的 toString/message；关闭态无逐值诊断包装。
8. 既有 sticky 重入与关闭竞态、旧字节码、release R8/设备协议继续通过。

## 6. 验证入口与证据

构建统一使用 Android Studio 环境辅助脚本，首次先 `--check`；同一工作区同一时间一个 Gradle。新本地证据保存在忽略目录 `build/verification-1.4.0/`，不覆盖旧版结果。

基础门禁：

```bash
./gradlew apiCheck :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest \
  :library-android:lintRelease :library-compose:lintRelease :app:lintRelease \
  :app:assembleDebug :app:assembleRelease
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer \
  verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" \
  -Pflowbus.consumer.java=/path/to/java8/bin/java -Pflowbus.consumer.expectedJava=1.8
```

实际运行须通过环境 helper，不照抄 Java 路径。候选源码版本改为 1.4.0，公开安装示例保留 1.3.1。旧 fixtures/baseline 不替换；增加 1.3.1 历史基线时校验公开摘要。API dump 只在核对增量设计后更新，不以 dump 掩盖旧符号删除。

沿用现有 API21/API35、consumer driver 与性能工作流；新增诊断断言嵌入既有必经阶段，保留全部协议与缺阶段拒绝测试。API33 真机不能替代 API21/35。2026-11-16 用户授权本轮相关提交、推送 `logan0817/FlowBus` 的 `feat/event-diagnostics`、PR/现有 CI、全部门禁通过后合并并核验 main；本次止于发布前检查。

## 7. 发布前审查与后续交接

- [x] W0–W7 的生产实现及专项证据完成，绑定下列源码/候选摘要；最终文档 PR 与 main 检查按下列收尾规则验收。
- [x] 现有受支持公开 API 无不兼容删除/描述符变化；新接口说明完整且 Java 可用。
- [x] 1.3.1 关闭态性能对照及开启态开销有实际结果，未把 JVM 结果外推 ART。
- [x] API21/API35 本轮设备阶段、日志、runId、同包 UI 及制品身份核对。
- [x] 文档清楚区分已发布 1.3.1 与候选 1.4.0，不把历史通过写成本轮通过。
- [x] 独立审查未确认新增阻塞问题；已有 F1/F7 修复及回归、性能边界明确。

后续实施者先核对 Git 差异与本表，再阅读 core/Android 调用链及本轮报告；不能根据聊天中的“已执行”推定通过。只有所有要求有证据才标记目标完成。正式发布是另一个授权阶段，届时固定已通过候选及源码，不重新构建替换受测字节。

## 8. 2026-11-13 本地阶段记录

2026-11-13 本地阶段，证据位于 `build/verification-1.4.0/`。这些文件是本机验收材料，不能假定会随 Git 保留。当时工作区未提交，`HEAD=1a18d376` 仅代表基线，须结合当时源码摘要和差异。该实现已于 2026-11-16 提交为 `4d3891aa5d9568e20bd99d5d607792ddff111ce4`；新的云端证据见第 9 节。

当前候选：core `7e7cd4aa404acbb5c5c6b69a9c81a5b9666d57a70aa4f8682029e05c84036eca`、Android `7ad28eabe3ad74e8fe9819d4a350c0becf52abbdd9c162ddc861d1ad54021633`、Compose `a6a6f2a38aec6c8cee1bcbfdc935f50b74f5add57cc302e0b912fc4f60a91ef3`。封存清单摘要 `bafb082c76c2ae32b6b1a2c9d8b5403f0453572a16e7edf1f6a787d99acc743c`，保存在 `final-candidate/candidate-manifest.json`。

| 项目 | 本轮结果 | 证据 / 边界 |
| --- | --- | --- |
| F1 / F7 修复前 | core 5 项中 4 项失败；Android 10 项中 5 项失败 | `logger-before-fix.log`、`logger-before-results/`、`android-logger-before-fix.log`、`android-logger-before-results/`；logger 覆盖原错误或发送结果 |
| 优化后基础门禁 | 228 core + 94 Android + 2 app = 324 项，0 failed/error/skipped；三模块 API；三份 lint 0 issue；app debug/release 及 release 测试 APK 构建 | `final-local-gates.log`、`final-jvm-results/`；最终文档另以 `final-readiness.log` 针对版本状态复验 |
| 新并发分支 | 3 项通过，累计 327 项；关闭重开身份、6 线程首次发布、32 轮 close/register 竞争 | `final-compatibility/registration-races.log`、`registration-race-results/`；没有固定 sleep |
| 隔离 Maven | 当前三坐标 metadata 与 core/Android/Compose 消费者通过，包含新诊断 API、真实 Java 编译和 release R8 | `final-local-gates.log`；候选摘要见上方，未上传 Central |
| Java8 旧字节码 | 正式 1.0.7、1.1.0、1.2.0、1.3.0、1.3.1 原版编译后不变字节码分别运行原版与当前候选 | `final-compatibility/binary-*.log` 及 `final-local-gates.log`；旧 manifest 曾正确拒绝中间候选，核对后才封存新候选，没有关闭校验 |
| core 符号 | 五版受支持公开符号缺失均为 0 | `final-compatibility/core-*-symbols.json` 保留其他 JVM/internal/private 差异；不是完整 ABI 认证 |
| 1.3.1 Android/Compose 符号 | 当前候选受支持公开缺失为 0 | `final-compatibility/{android,compose}-1.3.1-symbols.json`；原版制品从 Central 重新下载核对 |
| 四编译器 | Kotlin 1.8.22 / 1.9.22 / 2.0.21 / 2.1.0 的 core 和 Android 消费者通过；core 实际使用 Java8 | `final-compatibility/kotlin-*.log`；不改变生产编译器 |
| 较新 Navigation | Navigation 2.8.3 / Compose 1.7.8 实际 NavHost 消费者 release R8 通过 | `final-compatibility/newer-navigation.log`；不改变基础库依赖；仅构建，不冒充设备通过 |
| API33 release 示例 | 中间候选两项新排障页测试通过 | `sample-diagnostics-device.log`；覆盖四场景及配置重建。受测 core 为 `0f00c7f3…`，早于关闭态优化/F7；额外手工检查遇到其他应用占前台后停止 |
| API33 Maven 消费者 | 中间候选 Android 8、effect 12、policy 11 阶段通过 | 三组报告的日志、runId 与同包 UI 匹配；受测 core 为 `0f00c7f3…`，不能代替当前候选 API21/35 |
| 文档与结构 | 双语 core 排障示例从 README 原样提取编译；29 份 Markdown 相对目标、7 份 XML、3 份 JSON 解析与 `git diff --check` 通过 | `readme-examples-compile.log`、`static-structure-audit.json`；没有项目 Markdown formatter，仅沿用现有风格；不冒充全部渲染/锚点验证 |
| 协议测试 | 六组共 48 项 Python 测试通过 | `python-drivers.log`；包括缺阶段拒绝、符号验证器与基准报告身份拒绝 |
| 性能优化依据 | 中间候选注册 1264 B/op，正式基线约 1000 B/op，增加 26.4%；停止剩余开启态测量，保留结果后减少未启用对象 | `performance/`、`pre-optimization-decision.json`；该目录不是最终性能验收 |
| 优化后性能 | 5 组定向正式测量 28 行与 5 组 smoke 74 行均完成，报告及输入身份通过；默认三场景吞吐比约 0.959/0.997/0.956；注册分配 1000→1064 B/op | `performance-final/analysis.json`；正式归档见 [性能记录](../benchmarks/jvm/results/1.4.0/report.md)。开启态三场景吞吐约为关闭态 33%/55%/51%，适合临时排障；当时云端对照尚未执行，现见第 9 节 |

本机性能不推导 ART；开启诊断的显著成本已明确记录，不宣称持续监控成本可忽略。

只读交叉审查未确认当前 core、Android、Java、示例及 Compose 消费者的新增阻塞 Bug。已核对锁外通知、身份安全发布、最后观察器关闭/重开、真正取消、40 条示例记录边界及非协作取消的真实退出断言。Android 首次 lifecycle stop 的单次 yield 检查仍作为调度稳健性观察项保留，没有当前失败证据，不据此判断生产缺陷。当时 API21/35 与云端 CI 尚未执行，现已补齐首轮证据，见第 9 节。

交叉审查已落实的修正：保留默认完整 JMH 筛选，候选诊断用独立 filter；修正诊断 active1 的背压配置；当前候选发布清单改为 1.4.0，回退要求移除新增 API 后重编译。未完成事项仍以 W 表和发布前勾选为准，不能把本节局部成功理解为审查通过。

## 9. 2026-11-16 云端验收与收尾规则

[PR #18](https://github.com/logan0817/FlowBus/pull/18) 的生产实现提交为 `4d3891aa5d9568e20bd99d5d607792ddff111ce4`，首轮 [CI 43782753659](https://github.com/logan0817/FlowBus/actions/runs/43782753659) attempt 1 九项成功，无失败后重试。PR 合成受测提交为 `95b534f87fcb5133f060d856e7bdedc2ad92fdb9`，不是分支 head；候选清单 SHA-256 为 `80cdc066769269445566625f0a5407727ef160bc203e07809e35badcdbc8aa02`。18 个 Maven 文件与 9 份清单副本一致，全部 artifact ZIP 摘要核对通过。

| 门禁 | 当前证据 |
| --- | --- |
| API / JVM / lint | 三模块 API、231 core + 94 Android + 2 app = 327 JVM，0 failed/error/skipped；三份 release lint 0 issue；原始日志确认本轮执行测试 |
| 消费者与兼容 | metadata、Java8、四 Kotlin、release R8、五版 core 不变字节码、15 份二进制报告；12 份符号报告无受支持公开符号缺失；较新 Navigation 构建通过 |
| API21 | 库 instrumentation 3 项；Android/effect/policy 8/12/11，Navigation 7、NavHost 8；全部 result、当前 runId、日志、同包 UI 与阶段匹配 |
| API35 | 库 instrumentation 3 项、示例 release instrumentation 22 项（含排障示例两项）；Android/effect/policy 8/12/11，全部原始证据匹配 |
| 性能 | 同一 consumers runner 的正式 1.3.1/候选三场景对照与全部 smoke 完成；本地开关/注册测量的生产和基准输入摘要与提交一致；详见[性能记录](../benchmarks/jvm/results/1.4.0/report.md) |
| 审查 | core、Android/Java、Compose/示例及 CI/文档独立只读复核无新增阻塞；单次 yield 的观察项未在本轮 API21/35 复现失败，未通过放宽断言取得成功 |

首轮云端主制品 SHA-256：core `88b49e096bd27acd2e6fe7446b34781d091ebbdc59061ef8d8d2623daa58c0c4`；Android `90f958f9cc1b155ec8bd900db48d3b59a4e37f34573929af826014ac239dbd6f`；Compose `a6a6f2a38aec6c8cee1bcbfdc935f50b74f5add57cc302e0b912fc4f60a91ef3`。云端 core 与本地受测 core 的全部 JAR 成员字节一致；Android 新鲜构建中一处旧 inline/default 方法比本地多两条对 Class 常量的 `checkcast java/lang/Class`，其余成员相同。没有把本地 Android 字节称作云端字节；以本轮云端包完成全部消费者和设备检查。

性能结论有明确限制：吞吐比约 0.940/0.967/0.973，误差区间重叠；SampleTime 无订阅/sticky 整批均值增加约 8.4%/5.3%，误差区间不重叠，属于观察到的关闭态成本。active1 吞吐分配增加约 4.6%，sticky 的 SampleTime 分配增加约 16.6%；不混用模式或将较好的读数当成零成本。开启态吞吐约为关闭态 33%/55%/51%，仅建议按来源临时启用。以上不推导 ART、单事件延迟或持续监控能力。

本次后续提交仅整理文档和性能归档，不改变生产/基准源码、构建脚本或测量参数，因而移除 PR 的可选正式性能 marker，复用已验证且绑定输入摘要的正式结果。其余九项 CI 与全部 smoke 继续执行；若任何测量输入改变则必须重新测量，不能复用本节结果。**最终文档提交必须通过 PR 的 `verify` 才能合并；合并后独立检查 main 的九项 CI 并下载该次候选与证据，核对其生产内容与受测实现一致。** 完成状态以 PR/main 实际检查和本机 `completion-audit.json` 为准，不能只凭本节提前视作 main 已通过。

本地保留 `build/verification-1.4.0/pr18-initial/` 的原始 ZIP、manifest、JVM/lint、二进制、设备和 JMH 报告，以及 `evidence-audit.json`。正式性能的裁剪归档及原始报告摘要已纳入 Git；其他本地材料不会自动随 Git 保留，Actions 保留期为 14 天。未来正式发布时须固定已通过的 main 候选与提交，另行获得 Maven 上传、tag 和 GitHub Release 授权。

## 10. 2026-11-16 正式发布

用户在发布前检查完成后要求继续正式发布。本阶段沿用已验收 main `a8e3c8a9811795d4851c09c2e8248ba2ce4b94a3`、run `43786039022` attempt `1` 的固定候选，未重建 Maven 制品。三个坐标已正式发布；公开文件、签名、独立消费者和设备验收、成本限制及回退见[发布说明](release-notes-1.4.0.md)。后续发布状态文档继续通过 PR 与 main CI 验证，tag 指向实际受测生产提交。
