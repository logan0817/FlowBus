Chinese document [中文文档](./README.md)

# library-android

`library-android` is the Android adapter module of FlowBus.

The Gradle module directory is `library-android`, and the published artifact is:

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.6") // Use 1.0.6 after release; the badge above is the source of truth.
```

If you are integrating FlowBus into an Android app, this is usually the place to start.

## Features

| Capability | Entry point | Good for |
| --- | --- | --- |
| Global event broadcast | `postEvent(...)` / `onEvent<T> { ... }` | login success, background sync, multi-screen refresh |
| Owner-scoped events | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | Fragment notifying Activity, NavBackStackEntry-local communication |
| Named channels | `eventChannel<T>("name")` | Toast, SnackBar, navigation commands, and other same-type events with different meanings |
| Flow composition | `eventFlow<T>()` / `collectEvent(flow)` | getting a `Flow` first, then using `map`, `filter`, or `debounce` |
| Sticky latest value | `postStickyEvent(...)` / `onEvent<T>(isSticky = true)` | reading the latest initialization result after page recreation |
| Send diagnostics | `tryPostEventResult(...)` | inspecting subscriber count, overflow policy, and whether `tryEmit` was rejected |

FlowBus is a good fit for asynchronous events where one place sends a notification and multiple places may care. For long-lived page-local state, prefer `StateFlow`; for explicit one-to-one calls, prefer method calls, use cases, or suspend functions.

## 3-minute quick start

### 1. Add the dependency

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.6") // Use 1.0.6 after release; the badge above is the source of truth.
```

### 2. Define an event type

If it is just one simple action, a `data class` or `data object` is enough:

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 3. Send the event

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

### 4. Receive the event in UI

Shortest style:

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    viewModel.refreshFrom(event.source)
}
```

If you prefer to get the `Flow` first and then compose it yourself:

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<RefreshHomeEvent>()) { event ->
    viewModel.refreshFrom(event.source)
}
```

At this point, you already know the main path: 1. send with `postEvent(...)`. 2. receive with `onEvent<T> { ... }`. 3. move to owner-based APIs only when the event should stay inside one Activity / Fragment / NavBackStackEntry scope.

## Remember these 7 rules first

1. `postEvent(...)` / `emitEvent(...)` send global events, so the whole app can subscribe.
2. `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` send local events that only flow inside the bus attached to that `owner`.
3. By default, events are routed by event type. If one payload type needs multiple channels, use `eventChannel<T>("name")`.
4. `post*` tries immediately; `tryPost*` reports whether the underlying `tryEmit` call was rejected.
5. `tryPost*Result` returns bus-layer diagnostics, but it still does not mean subscriber callbacks already finished.
6. `emit*` suspends until the event is accepted by the underlying flow, which fits important send paths.
7. `onEvent(...)` is the recommended shortest UI API; `collectEvent(flow)` is better when you already have a `Flow`.

## Why do the docs often use `onEvent(...)`, while the sample app sometimes uses `collectEvent(eventFlow(...))`

These are not competing APIs. They are the same capability shown at different levels:

1. `onEvent(...)`: subscribe directly to FlowBus events, best for most UI code
2. `eventFlow(...)` / `scopedEventFlow(...)` / `channel.flow()`: get the event as a `Flow` first
3. `collectEvent(flow)`: collect that `Flow` with `LifecycleOwner` safety

For a simple global event, the two examples below listen to the same source. They only differ in how much of the flow pipeline is shown:

```kotlin
viewLifecycleOwner.onEvent<AddCartEvent> { event ->
    render(event)
}
```

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<AddCartEvent>()) { event ->
    render(event)
}
```

The same mapping applies to owner-scoped events:

```kotlin
viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}
```

```kotlin
viewLifecycleOwner.collectEvent(requireActivity().scopedEventFlow<ReloadToolbarEvent>()) {
    renderToolbar()
}
```

Recommended rule of thumb:

1. if the page just needs to receive an event, use `onEvent(...)`
2. if you want to `map`, `filter`, `debounce`, or `combine`, get the `Flow` first and use `collectEvent(...)`
3. the sample app shows both styles on purpose, so readers can understand both the shortest path and the “get Flow first” style

## The 4 most common scenarios

### Scenario 1: global broadcast

Good for:

1. refreshing multiple screens after login succeeds
2. notifying multiple observers after a background task finishes
3. global messages or refresh instructions

```kotlin
data class SyncFinishedEvent(val successCount: Int)

postEvent(SyncFinishedEvent(successCount = 3))

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}
```

### Scenario 2: Fragment communicates with Activity

Good for:

1. refreshing the toolbar
2. asking Activity to navigate, show a dialog, or start a permission flow
3. keeping communication inside the current Activity tree instead of global scope

```kotlin
data object ReloadToolbarEvent

requireActivity().postScopedEvent(ReloadToolbarEvent)

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}
```

Here, `requireActivity()` is not the listener. It means “which scope the event bus is attached to”.
The same idea also works with `NavBackStackEntry` or any custom `ViewModelStoreOwner`.

### Scenario 3: same payload type, different semantic channels

Good for:

1. `Toast`, `SnackBar`, navigation commands
2. multiple business meanings that all use `String`
3. avoiding raw channel strings scattered across the codebase

Recommended style:

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("Saved")

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

If this should stay inside the current Activity scope:

```kotlin
val activityCommand = eventChannel<String>("activity.command")

activityCommand.postTo(requireActivity(), "reload")

viewLifecycleOwner.onEvent(from = requireActivity(), channel = activityCommand) { command ->
    handleActivityCommand(command)
}
```

### Scenario 4: ViewModel / Repository / Worker notifies UI

Good for:

1. notifying UI after background sync completes
2. letting Repository notify multiple screens
3. emitting results from Worker and receiving them when UI becomes active again

```kotlin
data class UploadFinishedEvent(val taskId: String)

class UploadViewModel : ViewModel() {
    fun onUploadSuccess(taskId: String) {
        postEvent(UploadFinishedEvent(taskId = taskId))
    }
}

class UploadFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.onEvent<UploadFinishedEvent> { event ->
            showToast("Task ${event.taskId} finished")
        }
    }
}
```

If this should only notify the current Activity tree instead of the whole app, send to the owner scope:

```kotlin
requireActivity().postScopedEvent(UploadFinishedEvent(taskId = taskId))
```

Receive it with:

```kotlin
viewLifecycleOwner.onEvent<UploadFinishedEvent>(from = requireActivity()) { event ->
    showToast("Task ${event.taskId} finished")
}
```

## API selection matrix

| Scenario | Recommended API | Suspends | Scope | sticky replay | Result boundary |
| --- | --- | --- | --- | --- | --- |
| Global lightweight send | `postEvent(...)` | No | global | No | best-effort; logs a warning on failure |
| Global send with `tryEmit` result | `tryPostEvent(...)` | No | global | No | only reports whether the underlying flow rejected the value |
| Global send with diagnostics | `tryPostEventResult(...)` | No | global | normal / sticky | returns event name, subscription count, sticky replay count, overflow policy, and outcome |
| Owner-scoped send | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | No | target owner | No | does not broadcast to other owners |
| Named channel send | `eventChannel<T>("name")` + `channel.post(...)` | No | global or owner | normal / sticky | good for maintaining stable business channel names |
| Lifecycle-safe listener | `onEvent(...)` | No | global or owner | controlled by `isSticky` | shortest UI receiving entry |
| Get `Flow` first and compose | `eventFlow(...)` + `collectEvent(...)` | No | global or owner | controlled by `isSticky` | good for `map`, `filter`, and `debounce` |
| Guaranteed write | `emitEvent(...)` / `emitStickyEvent(...)` | Yes | global or owner | normal / sticky | waits until the write is accepted under backpressure rules |
| One-time latest sticky consumption | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | No | global or owner | cleared after read | returns the latest value; returns `null` when replay is empty |

## How to choose sending APIs

| API | Suspends | Supports delay | Return value | Recommended use | Boundary |
| --- | --- | --- | --- | --- | --- |
| `postEvent(...)` | No | Yes | none | lightweight UI notifications | best-effort; may fail when the buffer cannot accept immediately |
| `tryPostEvent(...)` | No | Yes | `Boolean` | checking whether underlying `tryEmit` was rejected | when `delayMillis > 0`, it only means the delayed task was scheduled |
| `tryPostEventResult(...)` | No | No | `FlowBusPostResult` | logs, test assertions, subscription count and overflow-policy investigation | does not mean subscriber callbacks already completed |
| `emitEvent(...)` | Yes | Yes | none | important paths that should wait under backpressure rules | caller must use it from a coroutine |
| `postStickyEvent(...)` / `emitStickyEvent(...)` | depends on API | Yes | depends on API | late subscribers need the latest value | not a replacement for long-lived state management |

Common global send:

```kotlin
postEvent(MyEvent(...))
```

Diagnostic send result:

```kotlin
val result = tryPostEventResult(MyEvent(...))
```

When sending to a specific owner, these two forms are equivalent:

```kotlin
postEventTo(owner = requireActivity(), event = ReloadToolbarEvent)
```

```kotlin
requireActivity().postScopedEvent(ReloadToolbarEvent)
```

Named channel send:

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("Saved")
toastChannel.postTo(requireActivity(), "Saved")
```

## How to choose receiving APIs

| Scenario | Recommended API | Lifecycle owner | Gets `Flow` directly | Sticky support | Why |
| --- | --- | --- | --- | --- | --- |
| Shortest UI listener | `viewLifecycleOwner.onEvent<T> { ... }` | automatic | No | via `isSticky` | good for most page events |
| Named channel listener | `viewLifecycleOwner.onEvent(channel) { ... }` | automatic | No | use sticky channel entry | avoids scattered strings |
| Compose before collecting | `eventFlow<T>()` + `collectEvent(...)` | managed by `collectEvent` | Yes | via `isSticky` | good for `map`, `filter`, and `debounce` |
| Owner-scoped listener | `owner.scopedEventFlow<T>()` + `collectEvent(...)` | managed by `collectEvent` | Yes | via `isSticky` | receives only events from that owner scope |
| Lifecycle-safe collection of any `Flow` | `collectEvent(flow) { ... }` | automatic | provided by caller | depends on input flow | not limited to FlowBus |

Shortest listener:

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    render(event)
}
```

Named channel listener:

```kotlin
viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

Get the `Flow` first, then combine / filter / transform it:

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<RefreshHomeEvent>()) { event ->
    render(event)
}
```

Owner-based version:

```kotlin
viewLifecycleOwner.collectEvent(requireActivity().scopedEventFlow<ReloadToolbarEvent>()) {
    renderToolbar()
}
```

Named channel version:

```kotlin
viewLifecycleOwner.collectEvent(toastChannel.flow()) { message ->
    showToast(message)
}
```

## What is the difference between `postEvent` and `emitEvent`

| Dimension | `postEvent(...)` | `tryPostEvent(...)` | `tryPostEventResult(...)` | `emitEvent(...)` |
| --- | --- | --- | --- | --- |
| Suspends | No | No | No | Yes |
| Write model | tries to write immediately | tries to write immediately | tries to write immediately and returns diagnostics | waits under backpressure rules |
| Return information | none | `Boolean` | `FlowBusPostResult` | none |
| Good for | UI clicks, lightweight hints, refresh notifications | checking whether `tryEmit` was rejected | logs, tests, subscription count and overflow-policy investigation | important notifications in Repository / Worker / ViewModel flows |
| Does not mean | the event definitely reached business code | subscribers handled it | business handling succeeded | subscriber business logic cannot fail |

`FlowBusPostResult` includes: 1. event name. 2. subscription count. 3. sticky replay count. 4. overflow policy. 5. outcome. It only says whether this `tryEmit` call was rejected by the underlying flow. It does not mean subscriber callbacks already handled the event.

`DROP_OLDEST` / `DROP_LATEST` are not reliable queue policies. The former may overwrite older events. The latter may keep the current event from entering the buffer. For critical paths, use `emitEvent(...)`, a business-owned queue, or a state machine.

## Sticky events

Sticky events mean “keep the latest value after sending it, so late subscribers can receive that value first”.

| State | Recommended API | Can late subscribers read the latest value | Clears replay | Good for | Poor fit |
| --- | --- | --- | --- | --- | --- |
| Normal event | `postEvent(...)` / `emitEvent(...)` | No | No | Toast, navigation, one-time click events | latest result needed after page restoration |
| Latest sticky value | `postStickyEvent(...)` / `emitStickyEvent(...)` | Yes | No | current config, latest session info, initialization result, page restoration state | long-lived state management |
| Clear sticky replay | `clearStickyEvent(...)` / `channel.clearSticky()` | No | Yes | keep the channel but stop replaying the old value | fully removing the channel |
| Remove sticky channel | `removeStickyEvent(...)` / `channel.removeSticky()` | No | Yes | current sticky entry is no longer needed and later access may recreate it | expecting old `Flow` references to be actively cancelled |
| One-time latest sticky consumption | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | not after read | Yes | latest result should be read only once | replacing `StateFlow` or preventing other threads from writing a new sticky value later |

If a sticky result should be read once, consume the latest value and clear replay immediately:

```kotlin
val event = consumeStickyLatestEvent<SessionReadyEvent>()
```

Owner-scoped and named channel APIs are available too:

```kotlin
val scopedEvent = consumeStickyLatestEvent<SessionReadyEvent>(owner = requireActivity())

val channelEvent = sessionReadyChannel.consumeStickyLatest()
```

These APIs only read the latest value currently in sticky replay and then clear that replay cache. They do not consume normal events, do not prevent another thread from writing a new sticky value later, and should not replace `StateFlow` for long-lived state.

## Event modeling advice

Recommended order:

1. single action → `data class` / `data object`
2. multiple actions in one business domain → `sealed interface` / `sealed class`
3. only use raw `String` / `Int` when the value is truly simple and obvious

For example:

```kotlin
sealed interface MainEvent {
    data object Refresh : MainEvent
    data class OpenDetail(val id: Long) : MainEvent
    data class ShowToast(val message: String) : MainEvent
}

postEvent<MainEvent>(MainEvent.Refresh)
postEvent<MainEvent>(MainEvent.OpenDetail(id = 42L))
postEvent<MainEvent>(MainEvent.ShowToast(message = "success"))
```

## If You Only Remember The Shortest Usage

### Global event

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
viewLifecycleOwner.onEvent<RefreshHomeEvent> { render(it) }
```

### Activity-scoped event

```kotlin
requireActivity().postScopedEvent(ReloadToolbarEvent)
viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) { renderToolbar() }
```

### Named channel

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("saved")
viewLifecycleOwner.onEvent(toastChannel) { showToast(it) }
```

## Optional configuration

Call `FlowBusAndroid.configure(...)` before the first `postEvent(...)`, `emitEvent(...)`, or `eventFlow(...)` call if you want to customize the underlying core config:

```kotlin
FlowBusAndroid.configure(
    FlowBusConfig(
        logger = MyFlowBusLogger,
        errorHandler = FlowBusErrorHandler.Rethrow
    )
)
```

Once any `FlowEventBus` has been created, a later `FlowBusAndroid.configure(...)` call throws `IllegalStateException`.

Put configuration in `Application.onCreate()`, before these entry points:

1. `postEvent(...)`
2. `eventFlow(...)`
3. `subscribeEvent(...)`
4. owner-scoped sample code

The Android module does not add a UI diagnostics panel.

When you need lower-level event metadata in custom `FlowEventBus` / core `FlowBus` scenarios, use `inspect()` from `flowbus-core`.

Before logging snapshots, make sure `eventName` and `scopeName` do not contain phone numbers, tokens, order IDs, or other sensitive values.

## What lives in flowbus-core

`library-android` is built on top of `flowbus-core`, which provides:

1. `FlowBus`
2. `EventKey`
3. sticky event support
4. named and closeable scopes
5. logger / error handler / buffer configuration

If you need those lower-level APIs directly, see:
1. GitHub URL: [flowbus-core README](https://github.com/logan0817/FlowBus/blob/main/flowbus-core/README_EN.md)

## Repository links

1. repository root: [FlowBus](https://github.com/logan0817/FlowBus)
2. core module: [flowbus-core](https://github.com/logan0817/FlowBus/tree/main/flowbus-core)
3. sample app module: [app](https://github.com/logan0817/FlowBus/tree/main/app)
