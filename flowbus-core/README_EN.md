Chinese document [中文文档](./README.md)

# flowbus-core

`flowbus-core` is the platform-neutral core module of FlowBus.

Built on Kotlin Coroutines and Flow, it provides:

- typed event dispatch
- sticky events
- root bus / scoped bus
- explicit lifecycle control through `FlowBusScope`
- named event handles via `EventChannel<T>`
- concise Kotlin-style APIs such as `event.send()`

If you are not building an Android app, or if you want to manage bus instances, scope lifecycle, or multi-instance isolation yourself, this is the main entry point.

## When you should use it directly

Good for:

- pure Kotlin / Coroutines projects
- server, desktop, CLI, or shared domain layers
- cases where you want to create `FlowBus()` instead of relying on the default singleton
- multi-instance isolation
- Session / Repository / Worker / Task-level scopes
- building your own adapter layer on top of FlowBus

If you are building an Android app, the Android adapter is usually the faster starting point:

- local doc: [`../library-android/README_EN.md`](../library-android/README_EN.md)
- GitHub URL: [library-android README](https://github.com/logan0817/FlowBus/blob/master/library-android/README_EN.md)

## Remember these 5 rules first

1. If you want zero setup, start with `DefaultFlowBus`.
2. If you need DI, multi-instance isolation, or explicit lifecycle ownership, create `FlowBus()` yourself.
3. By default, events are routed by event type. If one payload type needs multiple channels, use `eventChannel<T>("name")` or an explicit `eventName`.
4. `scoped(...)` gives you a shared named scope view, while `openScope(...)` gives you a scope handle with explicit lifecycle.
5. `post*` / `send()` try immediately and return `Boolean`; `emit*` / `awaitSend*` suspend until delivery succeeds.

## Install

```gradle
implementation("io.github.logan0817:flowbus-core:<latest-version>")
```

## 3-minute quick start

### 1. The simplest way: use `DefaultFlowBus`

```kotlin
data class SyncFinishedEvent(val taskId: String)

DefaultFlowBus.post(SyncFinishedEvent(taskId = "task-1"))

scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

This is already the shortest usable path:

- send with `DefaultFlowBus.post(...)`
- receive with `DefaultFlowBus.flow<T>()`

### 2. If you prefer the event object to send itself

```kotlin
SyncFinishedEvent(taskId = "task-1").send()

scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

If the event should go to a specific bus or scope:

```kotlin
val bus = FlowBus()

SyncFinishedEvent(taskId = "task-2").sendOn(bus)
```

### 3. If you need to suspend until delivery succeeds

```kotlin
scope.launch {
    DefaultFlowBus.emit(SyncFinishedEvent(taskId = "task-1"))
}
```

Or the sugar version:

```kotlin
scope.launch {
    SyncFinishedEvent(taskId = "task-1").awaitSend()
}
```

## When to switch from `DefaultFlowBus` to `FlowBus()`

Create your own instance when:

- tests, tenants, features, or sessions must be isolated from each other
- lifecycle should be controlled through dependency injection
- you need different bus configurations
- you do not want a process-wide singleton

Example:

```kotlin
val bus = FlowBus()

bus.post(SyncFinishedEvent(taskId = "task-1"))

scope.launch {
    bus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

In that case, all events stay inside this `bus` instance and never mix with `DefaultFlowBus`.

## Named event handles: `eventChannel(...)`

If you do not want raw `eventName` strings scattered across your code, or if the same payload type needs multiple semantic channels, promote the channel itself to a first-class object.

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("Saved")

scope.launch {
    toastChannel.flow().collect { message ->
        showToast(message)
    }
}
```

If you created your own bus, you can also target it explicitly:

```kotlin
val bus = FlowBus()
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.postOn(bus, "Saved")

scope.launch {
    toastChannel.flowOn(bus).collect { message ->
        showToast(message)
    }
}
```

### When to use `eventChannel` vs plain `eventName`

Prefer `eventChannel(...)` when:

- the channel is reused in many places
- you want business meaning declared in one place
- you do not want duplicated strings like `"ui.toast"`

Using `eventName` directly is still fine:

```kotlin
bus.post(value = "Saved", eventName = "ui.toast")
bus.flow<String>(eventName = "ui.toast")
```

But for public APIs or long-lived code, `eventChannel` is usually easier to read and harder to mistype.

## How to think about scopes: root, scoped, openScope

### root bus

Every `FlowBus()` already has a root bus. All `bus.post(...)` / `bus.flow<T>()` calls work on that root bus.

Good for:

- global broadcast inside that bus instance
- shared events within the same bus instance

### `scoped(...)`

`scoped("feature-a")` returns a shared named scope view.

Good for:

- isolating events under one scope name
- cases where lifecycle is managed externally
- multiple places that only need to reuse the same named scope

```kotlin
val featureBus = DefaultFlowBus.scoped("feature-a")

featureBus.post(FeatureRefreshEvent(reason = "manual"))

scope.launch {
    featureBus.flow<FeatureRefreshEvent>().collect { event ->
        refresh(event.reason)
    }
}
```

### `openScope(...)`

`openScope(...)` returns a `FlowBusScope`, which has an explicit open / close lifecycle.

Good for:

- Session
- Repository lifecycle
- Worker / Task chains
- business processes that should remove cached flows and events when finished

```kotlin
val syncScope = DefaultFlowBus.openScope("sync-task", closeWhen = scope)

syncScope.post(SyncProgress(percent = 10))

scope.launch {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}
```

If you want to finish it manually:

```kotlin
val sessionScope = DefaultFlowBus.openScope("session-42")

sessionScope.post(SessionEvent.Started)
sessionScope.close()
```

Quick distinction:

- `scoped(...)`: shared named bus view, does not own close lifecycle
- `openScope(...)`: explicit lifecycle handle, can call `close()`

## Lower-level typed keys

Most of the time, `bus.post(value)` / `bus.flow<T>()` is enough.

If you want to hold a stable key explicitly, or you need Java / lower-level interop, use `EventKey<T>`:

```kotlin
val refreshKey = eventKey<FeatureRefreshEvent>("feature.refresh")
val bus = FlowBus()

bus.post(refreshKey, FeatureRefreshEvent(reason = "manual"))

scope.launch {
    bus.flow(refreshKey).collect { event ->
        refresh(event.reason)
    }
}
```

`EventChannel<T>` can always be converted back to `EventKey<T>`:

```kotlin
val channel = eventChannel<String>("ui.toast")
val key = channel.asEventKey()
```

## API selection matrix

| Need | Recommended API |
| --- | --- |
| Default singleton | `DefaultFlowBus` |
| Multi-instance isolation | `FlowBus()` |
| Shortest send | `post(...)` / `send()` |
| Need to know whether the current call was accepted | `post(...)` / `send()` return `Boolean` |
| Guarantee delivery | `emit(...)` / `awaitSend()` |
| Named channel | `eventChannel<T>("name")` |
| Named shared scope view | `scoped(...)` |
| Explicit lifecycle scope | `openScope(...)` |

## How to choose sending APIs

### Shortest and most convenient

```kotlin
DefaultFlowBus.post(MyEvent(...))
MyEvent(...).send()
```

### Send to a specific bus or scope

```kotlin
val bus = FlowBus()
MyEvent(...).sendOn(bus)

val sessionScope = DefaultFlowBus.openScope("session")
MyEvent(...).sendOn(sessionScope)
```

### Delivery must succeed

```kotlin
scope.launch {
    DefaultFlowBus.emit(MyEvent(...))
}
```

Or:

```kotlin
scope.launch {
    MyEvent(...).awaitSend()
}
```

## How to understand `post`, `emit`, `send`, and `awaitSend`

### `post(...)` / `send()`

Characteristics:

- non-suspending
- shortest to write
- best-effort
- returns `false` when the buffer cannot accept the value immediately

Good for:

- lightweight fire-and-forget notifications
- events where occasional dropping is acceptable

### `emit(...)` / `awaitSend()`

Characteristics:

- suspending
- respects backpressure
- does not silently drop because `tryEmit` failed

Good for:

- more important events
- flows where delivery must complete before continuing

## Sticky events

Sticky events are for “late subscribers should still see the latest value”.

Good examples:

- latest initialization result
- latest configuration snapshot
- latest sync progress

Usually not a good fit for:

- toast
- navigation
- one-time click actions

Also note:

- `clearSticky(...)`: clears replay cache but keeps the sticky flow
- `removeSticky(...)`: removes the sticky flow completely

## Module boundary

`flowbus-core` focuses on platform-neutral event bus capabilities: event dispatch, sticky events, named scopes, lifecycle binding, logging, and error handling configuration.

If you need a platform-specific adapter layer, you can build it on top of this module.

If you only want quick Android integration without managing bus instances yourself, start with the Android adapter. If you need multi-instance isolation, dependency injection, or explicit lifecycle control, use raw `FlowBus`.

## Repository links

- repository root: [FlowBus](https://github.com/logan0817/FlowBus)
- Android adapter module: [library-android](https://github.com/logan0817/FlowBus/tree/master/library-android)
- demo module: [app](https://github.com/logan0817/FlowBus/tree/master/app)
