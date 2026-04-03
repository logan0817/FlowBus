# Troubleshooting

## 1. `gradlew` 无法执行

如果你在 macOS / Linux 上遇到：

```text
permission denied: ./gradlew
```

请先给 Gradle Wrapper 增加执行权限：

```bash
chmod +x ./gradlew
```

然后重新执行：

```bash
./gradlew test
```

## 2. JDK 版本不对

FlowBus 当前使用 Java 17 编译。如果本地 JDK 版本不匹配，Gradle 同步或编译可能失败。

可以先检查本地版本：

```bash
java --version
```

如果版本不是 17，请切换到 JDK 17 后再重试。

## 3. IntelliJ IDEA / Android Studio 对 AGP 支持不一致

当前项目使用较新的 Android Gradle Plugin 与 compileSdk 组合。如果你在旧版本 IntelliJ IDEA 中打开项目，可能会看到 AGP 兼容性提示。

建议优先使用较新的 Android Studio 打开项目；如果必须使用 IntelliJ IDEA，请确认 IDE 对当前 AGP 版本有足够支持。

## 4. `post*` 看起来“没反应”

`post*` 是 best-effort 发送：如果底层缓冲已满，事件可能会被丢弃。

建议：

- 想确认是否发送成功：使用 `tryPostEvent(...)` / `tryPostEventTo(...)`
- 想保证写入成功：使用 `emitEvent(...)` / `emitEventTo(...)`
- 调试时关注日志中的 dropped event warning

## 5. Fragment / Activity 作用域事件收不到

先确认你是否把事件发到了正确的 owner：

- `postEvent(...)`：发到全局总线
- `postEventTo(owner, ...)`：发到指定 owner 对应的局部总线
- `onEvent(from = owner)` / `eventFlowFrom(owner)`：从指定 owner 对应的局部总线接收

常见误区不是“谁在监听”，而是“事件挂在哪个作用域上”。

## 6. sticky event 行为和预期不一致

sticky event 适合“后来订阅的人也应该拿到最近一次值”的场景，例如最近一次初始化结果、最近一次配置、最近一次同步状态。

如果你发的是 Toast、导航、点击动作这类一次性事件，通常不应该用 sticky。

另外请区分：

- `clearSticky*`：只清 replay 缓存，保留 Flow
- `removeSticky*`：连 sticky Flow 一起移除
