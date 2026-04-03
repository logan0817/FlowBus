Chinese document [中文文档](./README.md)

# library-android

`library-android` is the Android adapter module of FlowBus.

The Gradle module directory is `library-android`, and the published artifact is:

```gradle
implementation("io.github.logan0817:flowbus:<latest-version>")
```

If you are integrating FlowBus into an Android app, this is usually the place to start.

## What it is for

FlowBus is a better fit for event broadcast in Android apps, such as:

- one place emits a notification and multiple places may care
- sender and receiver should not hold direct references to each other
- the event is naturally asynchronous and works well as `Flow`
- you want to organize events by global scope, Activity scope, Fragment scope, or other `ViewModelStoreOwner` scopes

Typical Android use cases:

- screen A asks screen B to refresh
- a Fragment asks its Activity to perform a UI action
- ViewModel / Repository / Worker code notifies UI to show a toast or refresh data
- one scope broadcasts an event and multiple observers react

It is not a good fit for:

- page-local state management: prefer `StateFlow`
- explicit one-to-one calls: prefer direct function calls / use cases
- strict request-response: prefer return values, suspend functions, or dedicated channels
- long-lived shared state: prefer state containers instead of an event bus

## Remember these 5 rules first

1. `postEvent(...)` / `emitEvent(...)` send global events, so the whole app can subscribe.
2. `postEventTo(owner, ...)` / `owner.postEvent(...)` send local events that only flow inside the bus attached to that `owner`.
3. By default, events are routed by event type. If one payload type needs multiple channels, use `eventChannel<T>("name")`.
4. `post*` tries immediately, `tryPost*` also tells you whether the current call was accepted, and `emit*` suspends until delivery succeeds.
5. `onEvent(...)` is the recommended shortest UI API; `collectEvent(flow)` is better when you already have a `Flow`.

## 3-minute quick start

### 1. Add the dependency

```gradle
implementation("io.github.logan0817:flowbus:<latest-version>")
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

At this point, you already know the main path:

- send with `postEvent(...)`
- receive with `onEvent<T> { ... }`
- move to owner-based APIs only when the event should stay inside one scope

## The 4 most common scenarios

### Scenario 1: global broadcast

Good for:

- refreshing multiple screens after login succeeds
- notifying multiple observers after a background task finishes
- global messages or refresh instructions

```kotlin
data class SyncFinishedEvent(val successCount: Int)

postEvent(SyncFinishedEvent(successCount = 3))

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}
```

### Scenario 2: Fragment communicates with Activity

Good for:

- refreshing the toolbar
- asking Activity to navigate, show a dialog, or start a permission flow
- keeping communication inside the current Activity tree instead of global scope

```kotlin
data object ReloadToolbarEvent

requireActivity().postEvent(ReloadToolbarEvent)

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}
```

Here, `requireActivity()` is not the listener. It means “which scope the event bus is attached to”.
The same idea also works with `NavBackStackEntry` or any custom `ViewModelStoreOwner`.

### Scenario 3: same payload type, different semantic channels

Good for:

- `Toast`, `SnackBar`, navigation commands
- multiple business meanings that all use `String`
- avoiding raw channel strings scattered across the codebase

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

- notifying UI after background sync completes
- letting Repository notify multiple screens
- emitting results from Worker and receiving them when UI becomes active again

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
requireActivity().postEvent(UploadFinishedEvent(taskId = taskId))
```

Receive it with:

```kotlin
viewLifecycleOwner.onEvent<UploadFinishedEvent>(from = requireActivity()) { event ->
    showToast("Task ${event.taskId} finished")
}
```

## API selection matrix

| Need | Recommended API |
| --- | --- |
| Global send | `postEvent(...)` |
| Need to know whether the current call was accepted | `tryPostEvent(...)` |
| Send to an owner scope | `postEventTo(owner, ...)` |
| Named channel | `eventChannel<T>("name")` + `channel.post(...)` |
| Shortest subscription | `onEvent(...)` |
| Get `Flow` first and compose it yourself | `eventFlow(...)` + `collectEvent(...)` |
| Guarantee delivery | `emitEvent(...)` |

## How to choose sending APIs

### Send globally

Most common:

```kotlin
postEvent(MyEvent(...))
```

If you want to know whether this best-effort send was accepted by the current bus:

```kotlin
val accepted = tryPostEvent(MyEvent(...))
```

If delivery must succeed:

```kotlin
viewModelScope.launch {
    emitEvent(MyEvent(...))
}
```

### Send to a specific owner

These are equivalent; choose the style that reads better in your codebase:

```kotlin
postEventTo(owner = requireActivity(), event = ReloadToolbarEvent)
```

```kotlin
requireActivity().postEvent(ReloadToolbarEvent)
```

### Send through a named channel

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("Saved")
toastChannel.postTo(requireActivity(), "Saved")
```

## How to choose receiving APIs

### Shortest one-line listener

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    render(event)
}
```

Or listen to a named channel:

```kotlin
viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

### Get the `Flow` first, then combine / filter / transform it

```kotlin
viewLifecycleOwner.collectEvent(eventFlow<RefreshHomeEvent>()) { event ->
    render(event)
}
```

Owner-based version:

```kotlin
viewLifecycleOwner.collectEvent(requireActivity().eventFlow<ReloadToolbarEvent>()) {
    renderToolbar()
}
```

Named channel version:

```kotlin
viewLifecycleOwner.collectEvent(toastChannel.flow()) { message ->
    showToast(message)
}
```

Simple summary:

- `onEvent(...)`: shortest and most direct, recommended for most UI listeners
- `eventFlow(...)`: better when you still want `map`, `filter`, `debounce`, or stream composition
- `collectEvent(flow)`: lifecycle-safe collection for any `Flow`, not only FlowBus

## What is the difference between `postEvent` and `emitEvent`

- `postEvent(...)`: best-effort, non-suspending, shortest to write
- `tryPostEvent(...)`: still non-suspending, but returns whether the current call was accepted
- `emitEvent(...)`: suspends until delivery succeeds under the current backpressure policy

Use `emitEvent(...)` for more important events. Use `postEvent(...)` when dropping is acceptable.

## Sticky events

Sticky events are for “late subscribers should still receive the latest value”.
Typical examples are:

- latest initialization result
- latest config snapshot
- latest sync status

They are usually a poor fit for:

- toast messages
- navigation commands
- click actions

Also distinguish:

- `clearSticky*`: clears replay cache but keeps the sticky flow
- `removeSticky*`: removes the sticky flow completely

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

## What lives in flowbus-core

`library-android` is built on top of `flowbus-core`, which provides:

- `FlowBus`
- `EventKey`
- sticky event support
- named and closeable scopes
- logger / error handler / buffer configuration

If you need those lower-level APIs directly, see:

- local core doc: [`../flowbus-core/README_EN.md`](../flowbus-core/README_EN.md)
- GitHub URL: [flowbus-core README](https://github.com/logan0817/FlowBus/blob/master/flowbus-core/README_EN.md)

## Repository links

- repository root: [FlowBus](https://github.com/logan0817/FlowBus)
- core module: [flowbus-core](https://github.com/logan0817/FlowBus/tree/master/flowbus-core)
- demo module: [app](https://github.com/logan0817/FlowBus/tree/master/app)
