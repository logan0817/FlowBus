Chinese document [中文文档](./README.md)

# library-android

`library-android` is the Android adapter module of FlowBus.

The Gradle module directory is `library-android`, and the published artifact is:

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.1.0") // 1.1.0 is unreleased; use after publication. Published baseline: 1.0.7.
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
implementation("io.github.logan0817:flowbus:1.1.0") // 1.1.0 is unreleased; use after publication. Published baseline: 1.0.7.
```

### 2. Define an event type

If it is just one simple action, a `data class` or `data object` is enough:

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 3. Register the UI receiver first

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

### 4. Send after the receiver becomes active and starts collecting

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

Registering `onEvent(...)` / `collectEvent(...)` does not make the underlying subscription immediately ready: lifecycle state and coroutine scheduling matter. Normal events have no replay and are not retained before collection or while the receiver is inactive. In the following scenarios, sending and receiving usually happen in different places: register the receiver first, then send from a user action or business flow once collection is active. Adjacent example statements are not a readiness barrier. Use sticky explicitly for a latest result needed by late subscribers, and `StateFlow` for recoverable UI state.

At this point, you already know the main path: 1. send with `postEvent(...)`. 2. receive with `onEvent<T> { ... }`. 3. move to owner-based APIs only when the event should stay inside one Activity / Fragment / NavBackStackEntry scope.

## Remember these 7 rules first

1. `postEvent(...)` / `emitEvent(...)` send global events, so the whole app can subscribe.
2. `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` send local events that only flow inside the bus attached to that `owner`.
3. By default, events are routed by event type. If one payload type needs multiple channels, use `eventChannel<T>("name")`.
4. `post*` tries immediately; `tryPost*` reports whether the underlying `tryEmit` call was rejected.
5. `tryPost*Result` returns bus-layer diagnostics, but it still does not mean subscriber callbacks already finished.
6. `emit*` follows the configured overflow policy. `SUSPEND` can apply backpressure with active subscribers; it does not guarantee normal-event retention, business completion, or process-death recovery.
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

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}

postEvent(SyncFinishedEvent(successCount = 3))
```

### Scenario 2: Fragment communicates with Activity

Good for:

1. refreshing the toolbar
2. asking Activity to navigate, show a dialog, or start a permission flow
3. keeping communication inside the current Activity tree instead of global scope

```kotlin
data object ReloadToolbarEvent

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}

requireActivity().postScopedEvent(ReloadToolbarEvent)
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

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}

toastChannel.post("Saved")
```

If this should stay inside the current Activity scope:

```kotlin
val activityCommand = eventChannel<String>("activity.command")

viewLifecycleOwner.onEvent(from = requireActivity(), channel = activityCommand) { command ->
    handleActivityCommand(command)
}

activityCommand.postTo(requireActivity(), "reload")
```

### Scenario 4: ViewModel / Repository / Worker notifies UI

Good for:

1. notifying UI after background sync completes
2. letting Repository notify multiple screens
3. notifying an actively collecting UI after Worker completion; use sticky or persistent business state when the result is needed after restoration

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
| Suspending send under configured policy | `emitEvent(...)` / `emitStickyEvent(...)` | Yes | global or owner | normal / sticky | backpressure with `SUSPEND` and active subscribers; dropping policies still apply |
| One-time latest sticky consumption | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | No | global or owner | cleared after read | returns the latest value; returns `null` when replay is empty |

## How to choose sending APIs

| API | Suspends | Supports delay | Return value | Recommended use | Boundary |
| --- | --- | --- | --- | --- | --- |
| `postEvent(...)` | No | Yes | none | lightweight UI notifications | best-effort; may fail when the buffer cannot accept immediately |
| `tryPostEvent(...)` | No | Yes | `Boolean` | checking whether underlying `tryEmit` was rejected | when `delayMillis > 0`, it only means the delayed task was scheduled |
| `tryPostEventResult(...)` | No | No | `FlowBusPostResult` | logs, test assertions, subscription count and overflow-policy investigation | does not mean subscriber callbacks already completed |
| `emitEvent(...)` | Yes | Yes | none | active broadcasts that should wait for buffer space under the configured policy | coroutine call; normal events without subscribers are not retained, and dropping policies still apply |
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
| Write model | tries to write immediately | tries to write immediately | tries to write immediately and returns diagnostics | follows the configured overflow policy; backpressure under `SUSPEND` |
| Return information | none | `Boolean` | `FlowBusPostResult` | none |
| Good for | UI clicks, lightweight hints, refresh notifications | checking whether `tryEmit` was rejected | logs, tests, subscription count and overflow-policy investigation | active broadcasts that should wait under configured policy |
| Does not mean | the event definitely reached business code | subscribers handled it | business handling succeeded | delivery or business completion is guaranteed |

`FlowBusPostResult` includes: 1. event name. 2. subscription count. 3. sticky replay count. 4. overflow policy. 5. outcome. It only says whether this `tryEmit` call was rejected by the underlying flow. It does not mean subscriber callbacks already handled the event.

`emitEvent(...)` does not override the configured overflow policy. `SUSPEND` can apply backpressure with active subscribers; `DROP_OLDEST` can still drop older values, and `DROP_LATEST` can still drop the current live value. Normal events complete immediately without subscribers and are not retained for future subscribers. Completion does not mean business handling finished. Use business state, a database, or persistent tasks for operations that must recover or complete reliably.

## Sticky events

Sticky events mean “keep the latest value after sending it, so late subscribers can receive that value first”.

Sticky replay is managed separately from live broadcast. With `DROP_LATEST`, a value may not enter the live buffer, but can still become replay after underlying `tryEmit` returns `true`. For example, a slow subscriber may receive `[1, 2]` live while a new subscriber first reads `3`. This preserves the existing contract. `emitStickyEvent(...)` does not change the overflow policy, and replay does not prove all subscribers received or successfully handled a value.

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

## Next-version suspend callbacks and instance integration (unreleased)

These APIs are in candidate source, not published `1.0.7`. Existing APIs remain. The candidate targets 1.1.0; Kotlin, Coroutines, Lifecycle, and minSdk requirements stay unchanged; no Compose, Hilt, or Koin dependency is added.

### Call suspend work directly from callbacks

```kotlin
import com.logan.flowbus.onEventSuspending

viewLifecycleOwner.onEventSuspending<RefreshHomeEvent> { event ->
    viewModel.refreshSuspending(event.source)
}

viewLifecycleOwner.onEventSuspending<RefreshHomeEvent>(from = requireActivity()) { event ->
    viewModel.refreshSuspending(event.source)
}
```

`refreshSuspending` is application-provided. Global and owner sources both support reified types or Android `EventChannel<T>`: use `onEventSuspending(channel)` or `onEventSuspending(from = owner, channel = channel)`. The source owner and receiver lifecycle have different roles. These are source alternatives, not duplicate registrations for one requirement.

`LifecycleOwner` variants default to `STARTED` and expose dispatcher, minLifecycleState, and isSticky. Register on the main thread; `INITIALIZED` is rejected immediately. Leaving the active state requests callback cancellation. Restart within the same repeatOnLifecycle waits for the previous callback to finish before resubscribing. Blocking or NonCancellable work cannot be forcibly stopped, and side effects are not rolled back. Normal events do not replay, while explicitly selected sticky collection can replay the latest value.

`CoroutineScope.onEventSuspending` offers global/owner and type/channel variants managed by the caller's scope, without automatic UI lifecycle gating. Owner acquisition must occur on the main thread. These variants have no dispatcher parameter and use the collection coroutine context. Cancel the returned Job to stop collection. A custom `FlowEventBus` exposes `subscribeEventSuspending`: the lifecycle version returns a Job, and the suspending version runs in a caller-managed coroutine.

Bus APIs reuse their source instance's type validation, logger, and errorHandler. Each subscription awaits callbacks sequentially without collectLatest; slow handling still follows the existing buffer/overflow policy. Ordinary callback exceptions use bus error policy; the default rethrows and terminates collection. Cancellation propagates directly without error reporting. Cooperative suspend work is cancelled with the subscription; blocking or non-cancellable work still requires application care.

### Preserve instance isolation through constructor injection

Create a bus once in application composition and give the same instance to sender and collector. An existing DI container can provide it; no extra DI integration module is required.

```kotlin
import com.logan.flowbus.collectEventFromSuspending
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey

val refreshKey = eventKey<RefreshHomeEvent>("home.refresh")

class RefreshNotifier(private val bus: FlowBus) {
    fun notifyRefresh(source: String): Boolean =
        bus.post(refreshKey, RefreshHomeEvent(source))
}

val featureBus = FlowBus()
val notifier = RefreshNotifier(featureBus)

viewLifecycleOwner.collectEventFromSuspending(from = featureBus, key = refreshKey) { event ->
    viewModel.refreshSuspending(event.source)
}
// Once collection is active, call notifier.notifyRefresh("login") from business work.
```

`collectEventFrom` accepts an ordinary callback and `collectEventFromSuspending` a suspending callback. Both accept an explicit `FlowBus`, `ScopedFlowBus`, or `FlowBusScope` with a core `EventKey<T>`. Convert a core channel via `coreChannel.asEventKey()`. Defaults are `Dispatchers.Main.immediate`, `STARTED`, and `isSticky=false`; the source instance's error policy is used. Registration does not establish immediate subscription readiness.

Stopping collection or destroying the receiver does not close the source. Each active lifecycle entry fetches Flow again. A `ScopedFlowBus` whose named store was rebuilt can use the new Flow on the next active entry; it does not switch held Flow while active. A `FlowBusScope` is a fixed handle: after closure, re-entry fails rather than silently replacing it with a reopened same-name handle. Source lookup errors propagate directly instead of becoming callback errors handled by errorHandler. The instance owner controls scope creation, replacement, and closure.

Injected instances are not silently merged with DefaultFlowBus or Android global APIs. A send to another instance/owner or a different event key will not reach this subscription.

### Collect an already composed arbitrary Flow

```kotlin
import com.logan.flowbus.collectEventSuspending

viewLifecycleOwner.collectEventSuspending(eventFlow<RefreshHomeEvent>()) { event ->
    viewModel.refreshSuspending(event.source)
}
```

`collectEventSuspending` is a generic Flow helper with sequential lifecycle-bound suspend callbacks. Upstream and callback exceptions propagate directly without automatically applying FlowBus logger/errorHandler. Choose `collectEventFromSuspending` for instance policy. Use native Flow `collectLatest` explicitly when previous handling should be cancelled; that behavior is not the default for these APIs.

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
