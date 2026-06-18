Chinese document [中文文档](./README.md)

# flowbus-core

`flowbus-core` is the platform-neutral core module of FlowBus.

Built on Kotlin Coroutines and Flow, it provides:

1. typed event dispatch
2. sticky events for late subscribers
3. root bus / scoped bus isolation
4. explicit lifecycle control through `FlowBusScope`
5. named event handles via `EventChannel<T>`
6. concise Kotlin-style APIs such as `event.send()`

If you are not building an Android app, or if you want to manage bus instances, scope lifecycle, or multi-instance isolation yourself, this is the main entry point.

## Good fit

Start from `flowbus-core` directly if any of these is true:

1. You are not in an Android-first integration scenario.
2. You want to create `FlowBus()` yourself instead of relying on the default singleton.
3. You need multi-instance isolation, dependency injection ownership, or Session / Repository / Worker / Task-level scopes.
4. You want to build your own adapter layer on top of FlowBus.

If you are building an Android app, the Android adapter is usually the faster starting point:

1. Android adapter docs: [library-android README](https://github.com/logan0817/FlowBus/blob/main/library-android/README_EN.md)

## Read These 9 Rules First

1. If you want zero setup, start with `DefaultFlowBus`.
2. If you need DI, multi-instance isolation, or explicit lifecycle ownership, create `FlowBus()` yourself.
3. The default event name is the fully qualified event type name, not the short class name.
4. If one payload type needs multiple channels, use `eventChannel<T>("name")` or an explicit `eventName`.
5. `EventChannel` is better for stable reusable business channels, while value-sugar APIs are better for shorter send calls.
6. `scoped(...)` gives you a shared named scope view, while `openScope(...)` gives you a scope handle with explicit lifecycle.
7. `post*` / `send()` try immediately and return `Boolean`; switch to `emit*` / `awaitSend*` if silent failure is not acceptable.
8. `tryPost*Result` returns bus-layer diagnostics; `consumeStickyLatest(...)` only reads and clears current sticky replay.
9. `DefaultFlowBus.configure(...)` / `install(...)` must happen before the first real use.

Cleanup APIs have 2 extra rules:

1. `removeScope()` closes an open `FlowBusScope` handle with the same name and clears the current store.
2. `removeEvent()` and `removeSticky()` do not cancel old `Flow` references you already hold.

## Install
[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.0.6") // Use 1.0.6 after release; the badge above is the source of truth.
```

## Start with the shortest path

For a first pass, only remember these 4 patterns:

1. Send on the default singleton: `DefaultFlowBus.post(MyEvent(...))`
2. Subscribe on the default singleton: `DefaultFlowBus.flow<MyEvent>()`
3. If you prefer the “event sends itself” style, use `MyEvent(...).send()`
4. If silent send failure is not acceptable, switch from `post(...)` / `send()` to `emit(...)` / `awaitSend()`

Smallest usable example:

```kotlin
data class SyncFinishedEvent(val taskId: String)

DefaultFlowBus.post(SyncFinishedEvent(taskId = "task-1"))

scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}
```

Use `send()` if you prefer the “event sends itself” style. Use `awaitSend()` if delivery must succeed before continuing.

If you want to replace the default singleton configuration, call `DefaultFlowBus.configure(...)` or `install(...)` before the first real use.

## When to switch from `DefaultFlowBus` to `FlowBus()`

Create your own instance when:

1. tests, tenants, features, or sessions must be isolated from each other
2. lifecycle should be controlled through dependency injection
3. you need different bus configurations
4. you do not want a process-wide singleton

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

Promote the channel itself to a first-class object when either case applies:

1. You do not want raw `eventName` strings scattered across your code.
2. The same payload type needs multiple semantic channels.

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("Saved")

scope.launch {
    toastChannel.flow().collect { message ->
        showToast(message)
    }
}

scope.launch {
    toastChannel.collect { message ->
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

scope.launch {
    toastChannel.collectOn(bus) { message ->
        showToast(message)
    }
}
```

### When to use `eventChannel` vs plain `eventName`

Prefer `eventChannel(...)` when:

1. the channel is reused in many places
2. you want business meaning declared in one place
3. you do not want duplicated strings like `"ui.toast"`

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

1. global broadcast inside that bus instance
2. shared events within the same bus instance

### `scoped(...)`

`scoped("feature-a")` returns a shared named scope view.

Good for:

1. isolating events under one scope name
2. cases where lifecycle is managed externally
3. multiple places that only need to reuse the same named scope

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

1. Session
2. Repository lifecycle
3. Worker / Task chains
4. business processes that should remove the current scope store and clear its cached values when finished

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

1. `scoped(...)`: shared named bus view, does not own close lifecycle
2. `openScope(...)`: explicit lifecycle handle, can call `close()`

One more detail:

1. `close()` invalidates the current `FlowBusScope` handle immediately, so the handle can no longer send events or look up flows
2. sends or flow lookups that already started keep using the original store, and that store is cleaned after those operations finish
3. old `Flow` references you already hold are still not actively cancelled

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
| Need to know whether `tryEmit` was rejected | `post(...)` / `send()` return `Boolean` |
| Need a diagnostic send result | `tryPostResult(...)` / `tryPostStickyResult(...)` |
| Guarantee delivery | `emit(...)` / `awaitSend()` |
| Subscribe directly with FlowBus error handling | `collect(...)` / `collectSticky(...)` |
| Compose Flow operators yourself | `flow(...)` / `stickyFlow(...)` |
| Named channel | `eventChannel<T>("name")` |
| Named shared scope view | `scoped(...)` |
| Explicit lifecycle scope | `openScope(...)` |
| Inspect event metadata | `inspect()` / `inspector().snapshot()` |
| Remove the current normal-event channel | `removeEvent(...)` |
| Clear sticky replay or sticky channel | `clearSticky(...)` / `removeSticky(...)` |
| Read and clear the latest sticky value | `consumeStickyLatest(...)` |

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

### Need a diagnostic send result

```kotlin
val result = DefaultFlowBus.tryPostResult(MyEvent(...))

if (!result.accepted) {
    logger.warn("FlowBus dropped ${result.eventName}: ${result.outcome}")
}
```

`FlowBusPostResult` only describes the bus-layer result.

| Result | Meaning |
| --- | --- |
| `accepted = true` | `tryEmit` did not immediately reject this call |
| `accepted = false` | the underlying flow rejected this non-suspending write |
| `AcceptedWithDropOldestPolicy` | the new value may overwrite an older value |
| `AcceptedWithDropLatestPolicy` | the call may be accepted, but the current value still may not enter the buffer |

It does not mean subscribers already handled the event. `DROP_OLDEST` / `DROP_LATEST` are not reliable-queue policies.

## How to choose subscription APIs

If you only need to receive events and want to reuse the logger and error handler from `FlowBusConfig`, prefer `collect(...)` / `collectSticky(...)`:

```kotlin
scope.launch {
    DefaultFlowBus.collect<SyncFinishedEvent> { event ->
        handle(event.taskId)
    }
}

scope.launch {
    toastChannel.collect { message ->
        showToast(message)
    }
}
```

If you need Flow operators such as `map`, `filter`, or `debounce`, get the `Flow` first and collect it yourself:

```kotlin
scope.launch {
    DefaultFlowBus.flow<SyncFinishedEvent>()
        .collect { event ->
            handle(event.taskId)
        }
}
```

Direct subscription APIs cover `DefaultFlowBus`, `FlowBus`, `ScopedFlowBus`, `FlowBusScope`, and `EventChannel`.

For an `EventChannel` with an explicit target, use `collectOn(target) { ... }` / `collectStickyOn(target) { ... }`.

## How to use diagnostics APIs

`inspect()` is meant for debugging, unit-test assertions, and log-based investigation. Avoid using it as normal business-branch control.

It returns a read-only snapshot with this metadata:

1. config summary.
2. root events and scoped events.
3. event name and event type name.
4. normal-flow and sticky-flow presence.
5. sticky replay count.
6. subscription count and non-suspending send counters.

It does not expose business payload stored in sticky replay.

If you write diagnostics snapshots to logs, keep sensitive values out of `eventName` and `scopeName`.

Sensitive values include phone numbers, order IDs, tokens, user IDs, and similar business identifiers. Use redaction or an internal trace ID when business correlation is needed.

For `DefaultFlowBus`, `inspect()` / `inspector()` does not initialize the default bus.

When the default bus has not been initialized, the snapshot returns the default config summary and empty event lists. Later `configure(...)` / `install(...)` calls are still allowed.

```kotlin
val snapshot = DefaultFlowBus.inspect()

snapshot.root.events.forEach { event ->
    println("${event.eventName}: subscribers=${event.subscriptionCount}, accepted=${event.metrics.acceptedPostCount}")
}
```

If you need to read the current state of the same bus repeatedly, keep the diagnostics entry:

```kotlin
val inspector = DefaultFlowBus.inspector()
val firstSnapshot = inspector.snapshot()
val secondSnapshot = inspector.snapshot()
```

If you only care about one scope, query it directly:

```kotlin
val bus = FlowBus()
val session = bus.inspectScope("session")
```

Diagnostics snapshots only expose metadata. Keep these 6 boundaries in mind:

1. Reading a snapshot does not create new event flows or scopes.
2. `scopeName = null` means root bus.
3. `stickyReplayCount` exposes count only, not values.
4. `inspectScope(...)` returns `null` when that scope has not been created.
5. `events` means registered event metadata, not necessarily active subscribers.
6. `metrics` only describes bus-layer `tryEmit` acceptance or rejection, not business handling success.

## `EventChannel`, value-sugar APIs, or plain `eventName`?

If you are deciding between the 3 styles, use this rule of thumb:

| Situation | Better choice |
| --- | --- |
| The channel is reused in many places and you want one stable definition | `eventChannel<T>("name")` |
| You only want the shortest send call at one call site | `event.send()` / `event.sendOn(target)` |
| You already have a stable name locally and do not want another wrapper object | plain `eventName` |
| Public API or long-lived code | prefer `eventChannel<T>("name")` |
| Internal one-off broadcast | value-sugar APIs or direct `post(value)` are both fine |

You can think of them as 3 levels:

1. `eventChannel<T>("name")`: model the channel itself as a stable object. Best for reuse and public business meaning.
2. `event.send()` / `event.sendOn(target)`: shorten the sending call. Best for local call sites.
3. `eventName = "..."`: most direct, but also the easiest way to scatter strings.

Direct value-sugar mapping:

| API | Actual behavior |
| --- | --- |
| `event.send()` | same as `DefaultFlowBus.post(event)` |
| `event.awaitSend()` | same as `DefaultFlowBus.emit(event)` |
| `event.sendSticky()` | same as `DefaultFlowBus.postSticky(event)` |
| `event.awaitSendSticky()` | same as `DefaultFlowBus.emitSticky(event)` |
| `event.sendOn(target)` | same as calling the matching `post(...)` on that target |
| `event.awaitSendOn(target)` | same as calling the matching `emit(...)` on that target |

In short:

1. Value-sugar APIs are only shorter syntax, not a different dispatch model.
2. The default `eventName` is still the fully qualified event type name.
3. Custom `eventName` still must not be blank.
4. Sending to `FlowBus`, `ScopedFlowBus`, or `FlowBusScope` keeps the same behavior as the matching `post*` / `emit*` API.

## How to understand `post`, `emit`, `send`, and `awaitSend`

### `post(...)` / `send()`

Characteristics:

1. non-suspending
2. shortest to write
3. best-effort
4. returns `false` when the buffer cannot accept the value immediately

Good for:

1. lightweight fire-and-forget notifications
2. events where occasional dropping is acceptable

### `emit(...)` / `awaitSend()`

Characteristics:

1. suspending
2. respects backpressure
3. does not silently drop because `tryEmit` failed

Good for:

1. more important events
2. flows where delivery must complete before continuing

## Sticky events

Sticky events are for “late subscribers should still see the latest value”.

| State | Recommended API | Can late subscribers read the latest value | Clears replay | Good for | Boundary |
| --- | --- | --- | --- | --- | --- |
| Normal event | `post(...)` / `emit(...)` / `flow(...)` | No | No | one-time notification, command, click action | no replay cache |
| Latest sticky value | `postSticky(...)` / `emitSticky(...)` / `stickyFlow(...)` | Yes | No | initialization result, session info, page-restoration config | not a long-lived state-management replacement |
| Clear sticky replay | `clearSticky(...)` | No | Yes | keep the channel but clear the latest value | does not actively cancel old `Flow` references |
| Remove sticky channel | `removeSticky(...)` | No | Yes | current sticky entry is no longer needed and later access may recreate it | old `Flow` references are not actively cancelled |
| One-time latest sticky consumption | `consumeStickyLatest(...)` | not after read | Yes | latest result should be read only once | does not prevent another thread from writing a new sticky value later |
| Remove normal-event channel | `removeEvent(...)` | not applicable | not applicable | make later normal-event access create a fresh channel | does not affect the sticky channel with the same name |

`consumeStickyLatest(...)` reads the latest value in sticky replay and immediately clears that replay cache.

It is for narrow “consume the latest result once” cases. The read and clear are only guaranteed to happen continuously inside the current store. It does not prevent another thread from writing a new sticky value later, and it should not replace state management.

Normal events do not have replay cache. If you only want to remove the current normal-event channel so later access recreates it lazily, use:

```kotlin
DefaultFlowBus.removeEvent<SessionReadyEvent>()
toastChannel.removeEvent()
toastChannel.removeEventOn(bus)
```

`removeEvent(...)` does not affect the sticky channel with the same name, and it does not clear the type guard bound to the same `EventKey` name.

## How subscriber failures are handled

`flowbus-core` does not silently swallow subscriber failures by default. It separates 3 cases:

1. The event value does not match the expected runtime type: it writes `logger.warn(...)`, then sends `FlowBusErrorPhase.ValueCast` to `errorHandler`.
2. The subscriber callback throws a normal exception: it writes `logger.warn(...)`, then sends `FlowBusErrorPhase.SubscriberCallback` to `errorHandler`.
3. The subscriber callback throws `CancellationException`: it is rethrown and is not swallowed by logging or `errorHandler`.

If you want to control that pipeline, the main extension points are:

1. `FlowBusLogger`: decides whether warnings are logged.
2. `FlowBusErrorHandler`: decides whether the failure is rethrown, ignored, or forwarded into your own handling logic.
3. `FlowBusErrorContext`: tells you which event failed, in which phase, whether it was sticky, whether it came from a scope, and which dispatcher was used.

The two most common strategies are:

1. Default strategy: keep `FlowBusErrorHandler.Rethrow` so subscriber failures surface immediately.
2. Fallback strategy: provide a custom `errorHandler` to report, trace, or downgrade failures before deciding what to do next.

## How to design event types clearly

Use a single event type for a single action:

```kotlin
data class LoginSuccessEvent(val userId: String)
```

For multiple actions in the same domain, a sealed type usually reads better:

```kotlin
sealed interface SyncEvent {
    data object Start : SyncEvent
    data class Progress(val percent: Int) : SyncEvent
    data object Finish : SyncEvent
}

DefaultFlowBus.post(SyncEvent.Start)

scope.launch {
    DefaultFlowBus.flow<SyncEvent>().collect { event ->
        when (event) {
            SyncEvent.Start -> onStart()
            is SyncEvent.Progress -> onProgress(event.percent)
            SyncEvent.Finish -> onFinish()
        }
    }
}
```

## Default singleton configuration

If you want to replace the default configuration before first use:

```kotlin
DefaultFlowBus.configure(
    FlowBusConfig(
        stickyReplay = 1,
        normalBufferCapacity = 32
    )
)
```

If you want to install your own prebuilt bus:

```kotlin
DefaultFlowBus.install(
    FlowBus(
        config = FlowBusConfig(normalBufferCapacity = 32)
    )
)
```

“Before first use” includes:

1. `DefaultFlowBus.raw()`
2. `DefaultFlowBus.post(...)` / `emit(...)`
3. `DefaultFlowBus.flow(...)` / `stickyFlow(...)`
4. `DefaultFlowBus.scoped(...)` / `openScope(...)`

If any of those have already run, later `configure(...)` or `install(...)` calls throw `IllegalStateException`.

## If you only want the shortest usage

### Default singleton

```kotlin
DefaultFlowBus.post(MyEvent(...))
scope.launch { DefaultFlowBus.flow<MyEvent>().collect { handle(it) } }
```

### Value-style sending

```kotlin
MyEvent(...).send()
scope.launch { DefaultFlowBus.flow<MyEvent>().collect { handle(it) } }
```

### Named channel

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
toastChannel.post("Saved")
scope.launch { toastChannel.flow().collect { showToast(it) } }
```

### Explicit scope

```kotlin
val taskScope = DefaultFlowBus.openScope("task", closeWhen = scope)
taskScope.post(TaskProgress(percent = 10))
scope.launch { taskScope.flow<TaskProgress>().collect { render(it) } }
```

## Common behavior boundaries

### Name validation

These names must not be blank:

1. `eventName`
2. `scopeName`
3. `EventChannel` / `EventKey` names

Passing `"   "` throws `IllegalArgumentException` immediately.

### Buffer and overflow policy

If normal events have no extra buffer, `overflowPolicy` must be `BufferOverflow.SUSPEND`.

If sticky events have neither replay nor extra buffer, `overflowPolicy` must also be `BufferOverflow.SUSPEND`.

Otherwise sticky events have no usable live buffer or replay storage, and FlowBus rejects the configuration during initialization with `IllegalArgumentException`.

### What happens after a scope is closed

`FlowBusScope.close()` does two things:

1. It invalidates the current `FlowBusScope` handle immediately and rejects later sends, flow lookups, and sticky operations.
2. It lets sends or flow lookups that already started keep using the original store, then removes that store and clears cached values after those operations finish.

Only one closeable `FlowBusScope` handle can be open for the same `scopeName` at a time.

If you only need to share the same named scope without owning its close lifecycle, use `scoped(...)`.

`close()` itself does not wait until cleanup finishes. Use the waiting APIs below when you need to know that the store has been removed or when you need a timeout result.

Close API comparison:

| API | Invalidates the handle immediately | Waits for store cleanup | Best for |
| --- | --- | --- | --- |
| `close()` | Yes | No | UI code, lifecycle callbacks, and cases that only need to stop using the current handle |
| `closeSuspending()` | Yes | Yes | coroutine code that must wait for cleanup |
| `tryClose(timeoutMillis)` | Yes | Up to the timeout | tests, shutdown flows, and explicit timeout handling |

If the scope follows an outer lifecycle, prefer `openScope(name, closeWhen = job)` or `bindTo(job)` so the scope is not forgotten.

If a close result reports `ClosingInProgress`, another close call is already handling that scope and this call did not take over.

What it does not do:

1. It does not actively cancel old `Flow` references you already captured.
2. It does not prevent a new store from being created later with the same scope name.

### `clearSticky` vs `removeSticky`

| API | Clears replay | Removes current store entry | Later access | Old held `Flow` references |
| --- | --- | --- | --- | --- |
| `clearSticky(...)` | Yes | No | reuses the current sticky channel | not actively cancelled |
| `removeSticky(...)` | Yes | Yes | recreates the sticky channel lazily | not actively cancelled |
| `consumeStickyLatest(...)` | Yes | No | reuses the current sticky channel | not actively cancelled |

### `removeEvent` boundaries

1. `removeEvent(...)` only removes the normal-event entry from the current store. It does not affect a sticky channel with the same name.
2. `removeEvent(...)` keeps the type binding for that channel name, so the same `eventName` cannot be reused with a different event type.
3. If you already captured an old normal-event `Flow` reference, it is not actively cancelled. Later access to the same normal event creates a fresh channel lazily.
4. Calling `removeEvent(...)` on `FlowBusScope` still follows scope lifecycle rules. A closed scope throws `IllegalStateException`.

### Error handling and logging

If you use FlowBus subscriber-side error handling, keep these 4 rules in mind:

1. Value type mismatch logs through `logger.warn(...)` first, then goes to `errorHandler`.
2. A normal exception thrown by your subscriber callback also logs first, then goes to `errorHandler`.
3. `CancellationException` is not swallowed. It is rethrown.
4. `FlowBusErrorContext.phase` tells you whether the failure happened during value casting or inside the subscriber callback.

Mapping:

1. `ValueCast`: the received value does not match the expected type.
2. `SubscriberCallback`: type conversion succeeded, but your callback threw.
3. `logger`: records warnings only. It does not decide control flow.
4. `errorHandler`: decides whether to rethrow, ignore, or handle the failure differently.

If you do not configure anything, the default is `FlowBusErrorHandler.Rethrow`, which means failures are thrown after logging/context collection.

## Module boundary

`flowbus-core` focuses on platform-neutral event bus capabilities: event dispatch, sticky events, named scopes, lifecycle binding, logging, and error handling configuration.

If you need a platform-specific adapter layer, you can build it on top of this module.

If you only want quick Android integration without managing bus instances yourself, start with the Android adapter.

If you need multi-instance isolation, dependency injection, or explicit lifecycle control, use raw `FlowBus`.

## Repository links

1. current repository: [FlowBus](https://github.com/logan0817/FlowBus)
2. core module directory: [flowbus-core](https://github.com/logan0817/FlowBus/tree/main/flowbus-core)
3. Android adapter module: [library-android](https://github.com/logan0817/FlowBus/tree/main/library-android)
4. sample app module: [app](https://github.com/logan0817/FlowBus/tree/main/app)
