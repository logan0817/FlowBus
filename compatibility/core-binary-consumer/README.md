# 1.0.7 core 二进制消费者回归

[English](README_EN.md)

此 fixture 只调用已发布 1.0.7 的入口。它先用 Maven Central 的原始 core JAR 编译 Kotlin 与 Java 消费者，再将同一批消费者 class 文件分别放到原版和候选 JAR 上运行。它补充“针对候选重新编译”的消费者验证，检查旧 inline、默认参数和直接 Java JVM 调用的链接路径。

## 运行

从项目根目录执行。构建仍用项目 JDK 17；Java 参数只控制消费者运行 JVM，应替换为本机已有 JDK 8 的可执行文件：

```bash
./gradlew verifyMavenLocalBinaryCoreConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" \
  -Pflowbus.consumer.java=/path/to/jdk8/bin/java \
  -Pflowbus.consumer.expectedJava=1.8

python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --runtime-report build/maven-local-binary-core-consumer/build/reports/binary-compatibility.json \
  --javap /path/to/jdk17/bin/javap \
  --output build/core-jvm-symbol-verification.json

python3 -B -m unittest discover -s compatibility/core-binary-consumer -p '*_test.py' -v
```

Gradle 任务会将 1.1.0 候选发布到指定本地目录，不做远端发布。原版固定为 Maven Central 的 io.github.logan0817:flowbus-core:1.0.7，非传递解析；候选 JAR 按绝对路径加入第二次运行。不能把本地产物当作原版基准。

生成工程为 build/maven-local-binary-core-consumer。消费者编译 classpath 只含原版 core、基线 stdlib 和 Coroutines；运行候选时移除原版 core。两次运行都检查 FlowBus 的实际 codeSource，检查产物摘要不同，并核对消费者 class 摘要在切换前后不变。任务失败时不保留旧的成功 runtime report。

## 范围与结果

消费者覆盖根/Default/共享/owned scope 的旧 reified collect 默认参数、channel 默认/显式来源、sticky replay/消费/清理、关闭重开、绑定 Job 关闭、removeScope、legacy 异常处理器继续，以及 Java 无参构造器、Class key 工厂与 scope 调用。就绪由 UNDISPATCHED、Deferred 和流订阅建立，不使用固定 delay。报告位于 build/maven-local-binary-core-consumer/build/reports/binary-compatibility.json。

独立 verify_symbols.py 使用 JDK javap 核对稳定具名类的 declared public/protected JVM 成员描述符及 static/instance 形式，检查 JAR 与 runtime report 的摘要一致。它保留默认参数、bridge 和 PublishedApi 相关 JVM 符号；仅排除含数字的编译器实现类，以及带 ACC_SYNTHETIC 标记的 access$ 私有实现桥接。原版 46 个具名类、631 个描述符核对通过。脚本当前 7 项回归覆盖描述符/static/类缺失、默认参数/bridge 保留、synthetic accessor 排除、无法识别的输出拒绝、AAR 只读取 classes.jar 与临时文件清理、缺 classes.jar 拒绝、产物变化拒绝、公开快照与原版字节码交叉核对。

### AAR 静态模式

脚本也支持 --baseline-artifact 与 --candidate-artifact，直接检查 JAR 或 AAR；AAR 只读取 classes.jar，不代表整个 AAR 的资源、manifest、consumer rules 或设备运行兼容。原版 Android AAR 的来源、固定摘要、公开快照和复现命令见 [Android 消费者说明](../android-consumer/README.md#已发布-107-aar-的-jvm-符号核对)。

可选 --baseline-api-snapshot 将受支持 API 门禁限定为原版 binary-compatibility-validator 快照；脚本先确认快照符号实际存在于原版字节码，再比较候选。原始 JVM 差异仍保存在 otherJvmRemovals，不因 internal 变化自动消失。该模式的快照必须来自固定原版，不能使用已增加候选 API 的当前模块快照。未指定快照时仍要求全部已纳入比较的描述符保留，core runtime-report 命令不受影响。

这不是完整 ABI 认证：符号检查不判断全部可见性、继承关系或接口实现兼容，也不覆盖 Android AAR 二进制替换、所有公开 API 行为、任意依赖版本及不同工具链。旧消费者中未执行的 inline 路径也没有运行证明。API 21 设备与远端 CI 结果按最终提交核对，入口见[发布清单](../../docs/release-checklist.md)；基础依赖保持不变，候选为尚未发布的 1.1.0。
