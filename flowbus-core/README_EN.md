[中文](README.md) · [Documentation](../docs/en/README.md)

# FlowBus Core

`flowbus-core` is the Android-independent Kotlin/JVM core of FlowBus. It does not currently publish Kotlin Multiplatform artifacts.

Built on Kotlin Coroutines and Flow, it provides:

1. typed event dispatch
2. sticky events for late subscribers
3. root bus / scoped bus isolation
4. explicit lifecycle control through `FlowBusScope`
5. named event handles via `EventChannel<T>`
6. concise Kotlin-style APIs such as `event.send()`

If you are not building an Android app, or if you want to manage bus instances, scope lifecycle, or multi-instance isolation yourself, this is the main entry point.

## Guide map

- [Install](#install) · [Quick start](#quick-start)
- [Scopes](#scopes-and-ownership) · [API selection](#api-selection-matrix)
- [Channel configuration](#channel-configuration) · [Suspend collection](#suspend-collection)
- [Diagnostics](#diagnostics) · [Behavior boundaries](#behavior-boundaries)
- [Compatibility](../docs/en/compatibility.md) · [Troubleshooting](../TROUBLESHOOTING.md) (Chinese)

## Good fit

Start from `flowbus-core` directly if any of these is true:

1. You are not in an Android-first integration scenario.
2. You want to create `FlowBus()` yourself instead of relying on the default singleton.
3. You need multi-instance isolation, dependency injection ownership, or Session / Repository / Worker / Task-level scopes.
4. You want to build your own adapter layer on top of FlowBus.

If you are building an Android app, the Android adapter is usually the faster starting point:

1. Android adapter docs: [library-android README](../library-android/README_EN.md)

<a id="read-these-9-rules-first"></a>

## Usage conventions

1. If you want zero setup, start with `DefaultFlowBus`.
2. If you need DI, multi-instance isolation, or explicit lifecycle ownership, create `FlowBus()` yourself.
3. The default event name is the fully qualified event type name, not the short class name.
4. If one payload type needs multiple channels, use `eventChannel<T>("name")` or an explicit `eventName`.
5. `EventChannel` is better for stable reusable business channels, while event extension helpers are better for shorter send calls.
6. `scoped(...)` gives you a shared named scope view, while `openScope(...)` gives you a scope handle with explicit lifecycle.
7. `post*` / `send()` try immediately and return `Boolean`; `emit*` / `awaitSend*` are suspending APIs that still follow the configured overflow policy and do not guarantee delivery.
8. `tryPost*Result` returns bus-layer diagnostics; `consumeStickyLatest(...)` only reads and clears current sticky replay.
9. `DefaultFlowBus.configure(...)` / `install(...)` must happen before the first real use.

Cleanup APIs have 2 extra rules:

1. `removeScope()` closes an open `FlowBusScope` handle with the same name and clears the current store.
2. `removeEvent()` and `removeSticky()` do not cancel old `Flow` references you already hold.

## Install
[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.1.0")
```

<a id="start-with-the-shortest-path"></a>

## Quick start

For a first pass, only remember these 4 patterns:

1. Send on the default singleton: `DefaultFlowBus.post(MyEvent(...))`
2. Subscribe on the default singleton: `DefaultFlowBus.flow<MyEvent>()`
3. If you prefer the “event sends itself” style, use `MyEvent(...).send()`
4. Use `emit(...)` / `awaitSend()` when `SUSPEND` should wait for active subscribers to make buffer space

Smallest usable example:

```kotlin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class SyncFinishedEvent(val taskId: String)

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    DefaultFlowBus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}

DefaultFlowBus.post(SyncFinishedEvent(taskId = "task-1"))
```

Use `send()` if you prefer the “event sends itself” style. Use `awaitSend()` for suspending emission under the configured policy.

Here `scope` is an active `CoroutineScope` managed by the caller. When collecting this library's normal Flow directly, `CoroutineStart.UNDISPATCHED` executes collection to its first suspension before sending. It is not a subscription-readiness guarantee for arbitrary upstream Flows: suspending initialization, `flowOn`, buffering operators, or lifecycle gates can delay the actual subscription. Confirm readiness for the specific pipeline.

If you want to replace the default singleton configuration, call `DefaultFlowBus.configure(...)` or `install(...)` before the first real use.

Quick terminology:

| Term | Meaning | Why it matters |
| --- | --- | --- |
| `tryEmit` | Kotlin `SharedFlow`'s non-suspending write attempt | `post(...)` / `send()` depend on it, so failure only means the bus did not accept the value immediately |
| replay | the latest-value cache stored by a sticky channel | late subscribers can see the latest value, but it is not long-lived state management |
| scope store | the internal container for event flows and sticky cache under one `scopeName` | `close()` / `removeScope()` clean the current store, but do not actively cancel old `Flow` references |
| `DROP_OLDEST` / `DROP_LATEST` | overflow policies that drop old or new values when buffers are full | use them for lightweight notifications, not reliable delivery |
| `DefaultFlowBus` | the default singleton entry point | good for quick setup; use `FlowBus()` when you need isolation or dependency injection |

<a id="when-to-switch-from-defaultflowbus-to-flowbus"></a>

## Instances and dependency injection

Create your own instance when:

1. tests, tenants, features, or sessions must be isolated from each other
2. lifecycle should be controlled through dependency injection
3. you need different bus configurations
4. you do not want a process-wide singleton

Example:

```kotlin
val bus = FlowBus()

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    bus.flow<SyncFinishedEvent>().collect { event ->
        handle(event.taskId)
    }
}

bus.post(SyncFinishedEvent(taskId = "task-1"))
```

In that case, all events stay inside this `bus` instance and never mix with `DefaultFlowBus`.

## Named event handles: `eventChannel(...)`

Promote the channel itself to a first-class object when either case applies:

1. You do not want raw `eventName` strings scattered across your code.
2. The same payload type needs multiple semantic channels.

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.flow().collect { message ->
        showToast(message)
    }
}

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.collect { message ->
        showToast(message)
    }
}

toastChannel.post("Saved")
```

If you created your own bus, you can also target it explicitly:

```kotlin
val bus = FlowBus()
val toastChannel = eventChannel<String>("ui.toast")

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.flowOn(bus).collect { message ->
        showToast(message)
    }
}

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    toastChannel.collectOn(bus) { message ->
        showToast(message)
    }
}

toastChannel.postOn(bus, "Saved")
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

<a id="how-to-think-about-scopes-root-scoped-openscope"></a>

## Scopes and ownership

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

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    featureBus.flow<FeatureRefreshEvent>().collect { event ->
        refresh(event.reason)
    }
}

featureBus.post(FeatureRefreshEvent(reason = "manual"))
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

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}

syncScope.post(SyncProgress(percent = 10))
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

<a id="lower-level-typed-keys"></a>

## EventKey and type constraints

Most of the time, `bus.post(value)` / `bus.flow<T>()` is enough.

If you want to hold a stable key explicitly, or you need Java / lower-level interop, use `EventKey<T>`:

```kotlin
val refreshKey = eventKey<FeatureRefreshEvent>("feature.refresh")
val bus = FlowBus()

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    bus.flow(refreshKey).collect { event ->
        refresh(event.reason)
    }
}

bus.post(refreshKey, FeatureRefreshEvent(reason = "manual"))
```

`EventChannel<T>` can always be converted back to `EventKey<T>`:

```kotlin
val channel = eventChannel<String>("ui.toast")
val key = channel.asEventKey()
```

## API selection matrix

| Need | Recommended API | What it does | When to choose it |
| --- | --- | --- | --- |
| Default singleton | `DefaultFlowBus` | provides one process-wide default bus | quick setup, demos, no DI requirement |
| Multi-instance isolation | `FlowBus()` | each instance owns its own root bus and scopes | test isolation, tenant isolation, repository-owned lifecycle |
| Shortest non-suspending send | `post(...)` / `send()` | tries to write immediately and returns `Boolean` | lightweight broadcasts where occasional dropping is acceptable |
| Diagnostic send result | `tryPostResult(...)` / `tryPostStickyResult(...)` | returns subscriber count, overflow policy, sticky replay count, and outcome | logs, tests, send-failure investigation |
| Suspending send under configured policy | `emit(...)` / `awaitSend()` | backpressure with `SUSPEND` and active subscribers; still follows overflow policy | active broadcasts that should wait for buffer space |
| Subscribe with FlowBus error handling | `collect(...)` / `collectSticky(...)` | applies the logger and error handler from `FlowBusConfig` | avoiding repeated try/catch and logging boilerplate |
| Compose Flow operators yourself | `flow(...)` / `stickyFlow(...)` | returns the raw `Flow<T>` | `map`, `filter`, `debounce`, `combine`, and custom collection |
| Named channel | `eventChannel<T>("name")` | binds the event name and type into a stable object | same-type events with different meanings, reuse across files, public business channels |
| Named shared scope view | `scoped(...)` | shares a named local bus without close ownership | multiple call sites reuse a scope whose lifecycle is managed elsewhere |
| Explicit lifecycle scope | `openScope(...)` | returns a closeable `FlowBusScope` handle | Session, Worker, Task, or Repository cache cleanup |
| Inspect metadata | `inspect()` / `inspector().snapshot()` | returns a read-only snapshot without sticky payloads | debugging, logs, and test assertions |
| Remove current normal-event channel | `removeEvent(...)` | removes the normal event flow from the current store | later access should create a fresh normal channel |
| Clear sticky replay or sticky channel | `clearSticky(...)` / `removeSticky(...)` | clears replay, or removes the current sticky entry too | page exit, session switch, expired latest value |
| Read and clear latest sticky value | `consumeStickyLatest(...)` | reads current replay latest value and clears it | one-time consumption of the latest result |

<a id="how-to-choose-sending-apis"></a>

## Sending APIs

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

### Suspending send under the configured policy

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

| Result | Meaning | What to do |
| --- | --- | --- |
| `accepted = true` | `tryEmit` did not immediately reject this call | treat it only as bus-layer acceptance; add a business ACK or state update if business completion matters |
| `accepted = false` | the underlying flow rejected this non-suspending write | inspect buffer, subscribers, and overflow policy; use `emit(...)` to wait under `SUSPEND` |
| `AcceptedWithDropOldestPolicy` | the new value may overwrite an older value | use only for discardable refresh-style notifications |
| `AcceptedWithDropLatestPolicy` | the call may be accepted, but the current value still may not enter the live buffer | `emit(...)` does not change this policy; use business state, a database, or persistent tasks for reliability |

It does not mean subscribers already handled the event. `DROP_OLDEST` / `DROP_LATEST` are not reliable-queue policies.


<a id="120-candidate-channel-configuration"></a>
<a id="channel-configuration"></a>

## Channel configuration

Available since 1.2.0. This branch contains an unpublished candidate; released 1.1.0 does not provide these APIs. See [candidate builds](../docs/en/contributing.md#candidate-builds) to try `io.github.logan0817:flowbus-core:1.2.0` or `io.github.logan0817:flowbus:1.2.0`, and [release notes](../docs/en/release-notes-1.2.0.md) for changes and evidence.

### Core, scopes, and explicit instances

```kotlin
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusChannelConfig
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.channels.BufferOverflow

val bus = FlowBus()
val refresh = eventKey<String>("home.refresh")
val progress = eventKey<Int>("work.progress")
bus.configureChannel(refresh, FlowBusChannelConfig(normalBufferCapacity = 1))
bus.configureChannel(progress, FlowBusChannelConfig(
    normalBufferCapacity = 16,
    normalOverflowPolicy = BufferOverflow.SUSPEND
))
// Send from the business flow after the actual subscription becomes active.
bus.post(refresh, "refresh")
bus.emit(progress, 10) // In a suspending function; backpressure is not processing completion.
```

FlowBus, shared ScopedFlowBus, and owned FlowBusScope expose configureChannel(key, config). Convert a core EventChannel with asEventKey(); use DefaultFlowBus.raw() for the default bus.

The nullable fields normalBufferCapacity, normalOverflowPolicy, stickyReplay, stickyExtraBufferCapacity, and stickyOverflowPolicy inherit bus defaults. Both overflow fields inherit FlowBusConfig.overflowPolicy. Default normal capacity 64, DROP_OLDEST, and sticky replay 1 remain. Capacities must be nonnegative, zero normal capacity or zero total sticky capacity requires SUSPEND, and the sticky capacity sum must fit Int.

Identity is eventName within the current store; normal and sticky policies bind together. Configure before the first post/emit/flow, including sticky operations. Otherwise first use binds defaults. The same type and effective policy are idempotent. A type conflict throws IllegalArgumentException; a policy conflict throws IllegalStateException. Failure leaves no partial registration, flow, or send counters. First configuration racing first use follows the winning atomic binding.

removeEvent/removeSticky/clearSticky retain the binding. Complete scope cleanup releases overrides; a reopened same-name scope requires configuration again. Root overrides live with the bus. Use a new instance and explicitly switch subscriptions to change an already bound policy. inspect().config describes bus defaults; PostResult.overflowPolicy describes the actual normal or sticky policy. Acceptance counters cannot establish delivery rate.

### Migration and resource ownership

When migrating from EventBus / LiveEventBus, choose the source and stable key first, then normal versus sticky. post reaches only normal collectors; postSticky reaches only sticky collectors. Normal events with no subscribers are lost, and a returned registration Job is not a readiness barrier. Sticky may replay after lifecycle restart. consume clears replay but cannot withdraw live in-flight events. SUSPEND + emit waits for active slow subscribers; DROP_OLDEST discards older notifications. Neither acknowledges business completion.

Normal/sticky example with one key: after both collectors become active, call `bus.post(key, 1)` and `bus.postSticky(key, 2)`. The normal collector receives `[1]`, while the sticky collector receives `[2]`. A later normal subscription receives no historical value; a later sticky subscription first receives `2`. `consumeStickyLatest(key)` returns `2` and clears replay, while both original collectors stay active. See the runnable [FlowBusDeliveryContractTest](src/test/java/com/logan/flowbus/core/FlowBusDeliveryContractTest.kt).

Slow-subscriber example: configure normal capacity 1 with DROP_OLDEST, pause the callback after receiving `1`, then send `2` and `3`. Both sends can be accepted; after resuming, the collector receives `[1, 3]` because `2` was displaced. On a separate zero-capacity SUSPEND channel, `post` returns false while an active slow collector is paused, and `emit` waits for collection to continue. This still does not acknowledge business completion. See the runnable [FlowBusChannelConfigTest](src/test/java/com/logan/flowbus/core/FlowBusChannelConfigTest.kt).

There is no automatic inheritance dispatch. For sealed parents, send and collect through the same EventKey<Parent>, such as eventKey<UiEvent>("ui.event"), bus.post(key, ChildEvent(...)), and bus.flow(key). Full generic types are not checked at runtime. Bus collectors use their source error handler (default Rethrow); generic Flow collection propagates errors directly. Cooperative cancellation is not a normal callback failure and does not roll back side effects.

Cancelling a subscriber does not delete channels; removing a channel does not cancel old Flow references. Avoid unbounded global per-user/task names. Bind task scopes to a Job and verify cleanup via inspector, rather than GC:

```kotlin
import kotlinx.coroutines.Job

val taskJob = Job()
val source = bus.openScope("sync-task", closeWhen = taskJob)
source.configureChannel(progress, FlowBusChannelConfig(normalBufferCapacity = 1))
source.postSticky(progress, 10)
check(bus.inspect().scopes.any { it.scopeName == "sync-task" })
taskJob.cancel()
source.closeSuspending() // In a suspending function; cancel collection Jobs separately.
check(bus.inspect().scopes.none { it.scopeName == "sync-task" })
```

Prefer the default bus for simple global notifications, stable channels with explicit owners for owner-local events, FlowBus + EventKey for injected instances, openScope(closeWhen = job) for tasks, generic Flow collectors for composed flows, and StateFlow for recoverable state. There is no automatic zero-subscriber cleanup.

<a id="110-suspend-collection"></a>
<a id="suspend-collection"></a>

## Suspend collection

1.1.0 adds separately named suspend-callback APIs and retains existing `collect` / `collectSticky` APIs. These APIs are available starting with 1.1.0 without adding dependencies or raising base toolchain requirements.

`FlowBus`, `DefaultFlowBus`, `ScopedFlowBus`, and `FlowBusScope` provide `collectSuspending` / `collectStickySuspending` with `EventKey<T>` and reified variants. Core `EventChannel<T>` provides `collectSuspending` / `collectStickySuspending` on the default instance and `collectSuspendingOn` / `collectStickySuspendingOn` for an explicit `FlowBus`, `ScopedFlowBus`, or `FlowBusScope`.

```kotlin
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

val bus = FlowBus()
val refreshKey = eventKey<SyncFinishedEvent>("sync.finished")

val subscription = scope.launch(start = CoroutineStart.UNDISPATCHED) {
    bus.collectSuspending(refreshKey) { event ->
        refreshSuspending(event.taskId)
    }
}
bus.post(refreshKey, SyncFinishedEvent(taskId = "task-1"))

// Call subscription.cancel() when its business lifecycle ends; this does not close the bus.
```

`scope` is an active caller-managed coroutine scope, and `refreshSuspending` is application suspend work. Without a dispatcher and with direct collection, UNDISPATCHED reaches collection suspension before sending; other upstream pipelines still require readiness checks. Import `com.logan.flowbus.core.collectSuspending` for the reified extension or the corresponding channel extension such as `collectSuspendingOn`.

Callbacks execute sequentially within each subscription. The API does not launch per-event work concurrently or use `collectLatest`. An explicit dispatcher changes the callback context while preserving sequence; otherwise the current collection context is used. Separate subscriptions are not globally serialized. Suspend callbacks do not change broadcast, buffering, dropping, sticky replay, or business acknowledgement semantics.

Type validation, logger, and errorHandler are shared with existing APIs. Ordinary callback exceptions are logged and passed to the instance errorHandler. Default `Rethrow` terminates that collection; collection continues only when the handler returns normally. A handler's own failure propagates. `CancellationException` propagates directly without entering the bus error policy. Cancelling the subscription requests callback cancellation; blocking or NonCancellable work cannot be forcibly stopped, and cancellation does not roll back side effects.

Closing a `FlowBusScope` does not actively cancel subscriptions holding old Flow references, but starting collection through a closed handle fails. Bus/scope lifecycle remains with its owner; subscriptions do not acquire close ownership.

<a id="how-to-choose-subscription-apis"></a>

## Subscription APIs

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

<a id="how-to-use-diagnostics-apis"></a>

## Diagnostics

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

<a id="eventchannel-event-extension-helpers-or-plain-eventname"></a>

## Event identity and sending extensions

If you are deciding between the 3 styles, use this rule of thumb:

| Situation | Better choice |
| --- | --- |
| The channel is reused in many places and you want one stable definition | `eventChannel<T>("name")` |
| You only want the shortest send call at one call site | `event.send()` / `event.sendOn(target)` |
| You already have a stable name locally and do not want another wrapper object | plain `eventName` |
| Public API or long-lived code | prefer `eventChannel<T>("name")` |
| Internal one-off broadcast | event extension helpers or direct `post(value)` are both fine |

You can think of them as 3 levels:

1. `eventChannel<T>("name")`: model the channel itself as a stable object. Best for reuse and public business meaning.
2. `event.send()` / `event.sendOn(target)`: event extension helpers that shorten the sending call. Best for local call sites.
3. `eventName = "..."`: most direct, but also the easiest way to scatter strings.

Direct event extension helper mapping:

| API | Actual behavior |
| --- | --- |
| `event.send()` | same as `DefaultFlowBus.post(event)` |
| `event.awaitSend()` | same as `DefaultFlowBus.emit(event)` |
| `event.sendSticky()` | same as `DefaultFlowBus.postSticky(event)` |
| `event.awaitSendSticky()` | same as `DefaultFlowBus.emitSticky(event)` |
| `event.sendOn(target)` | same as calling the matching `post(...)` on that target |
| `event.awaitSendOn(target)` | same as calling the matching `emit(...)` on that target |

In short:

1. Event extension helpers are only shorter syntax, not a different dispatch model.
2. The default `eventName` is still the fully qualified event type name.
3. Custom `eventName` still must not be blank.
4. Sending to `FlowBus`, `ScopedFlowBus`, or `FlowBusScope` keeps the same behavior as the matching `post*` / `emit*` API.

<a id="how-to-understand-post-emit-send-and-awaitsend"></a>

## Sending semantics

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

1. Suspending, but follows the configured `overflowPolicy`; it does not turn a dropping policy into `SUSPEND`.
2. With `SUSPEND` and active subscribers, it can wait for buffer space and apply backpressure.
3. `DROP_OLDEST` can still drop older values, and `DROP_LATEST` can still drop the current live value.
4. Normal events complete immediately without subscribers and are not retained for future subscribers. Emission completion does not mean callback or business completion.

Use it for active broadcasts that should wait under the configured policy. Use business state, a database, or persistent tasks when operations must survive process death or confirm business success.

## Sticky events

Sticky events are for “late subscribers should still see the latest value”.

Replay means the latest-value cache stored inside the sticky channel. It solves “I subscribed late but still need the latest result”, but it should not become long-lived state management. Keep page state, form state, and complex business state in `StateFlow`, a database, or a business state machine.

Sticky replay is managed separately from live broadcast. After the underlying send is accepted, the independent replay cache is committed. With `DROP_LATEST`, the live buffer can drop the current value while `tryEmit` still returns `true`, so that value can still enter replay. For example, a slow live subscriber may receive `[1, 2]` while a new subscriber first reads `3`. This is the existing contract: live reception does not determine replay, and replay is not a receipt or business-completion record. `emitSticky(...)` also follows the configured overflow policy.

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

<a id="how-subscriber-failures-are-handled"></a>

## Errors and cancellation

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

<a id="how-to-design-event-types-clearly"></a>

## Event modeling

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

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    DefaultFlowBus.flow<SyncEvent>().collect { event ->
        when (event) {
            SyncEvent.Start -> onStart()
            is SyncEvent.Progress -> onProgress(event.percent)
            SyncEvent.Finish -> onFinish()
        }
    }
}

DefaultFlowBus.post<SyncEvent>(SyncEvent.Start)
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

<a id="common-behavior-boundaries"></a>

## Behavior boundaries

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

| API | Invalidates the handle immediately | Waits for store cleanup | Best for | Check first if it behaves unexpectedly |
| --- | --- | --- | --- | --- |
| `close()` | Yes | No | UI code, lifecycle callbacks, and cases that only need to stop using the current handle | whether a started send or flow lookup is still running |
| `closeSuspending()` | When initiating closure, rejects new operations first; `isClosed=false` while waiting for in-flight operations | Yes, cancellable waiting | coroutine code that must wait for cleanup | backpressured sends and which call initiated closure |
| `tryClose(timeoutMillis)` | When initiating closure, rejects new operations first; `isClosed=false` while waiting for in-flight operations | Up to the timeout | tests, shutdown flows, and explicit timeout handling | timeout duration and whether an earlier close invalidated the handle |

If the scope follows an outer lifecycle, prefer `openScope(name, closeWhen = job)` or `bindTo(job)` so the scope is not forgotten.

If a close result reports `ClosingInProgress`, another waiting API initiated closure and is still waiting for in-flight operations. This call did not take over. New sends and flow lookups are rejected during this stage, but `isClosed` remains `false`, preserving the 1.0.7 state semantics.

Cancellation in 1.1.0: if `closeSuspending()` initiates closure and is cancelled before store cleanup is prepared, and no permanent close request has arrived, that wait is withdrawn and the scope becomes usable again for a later retry. If `close()` or a lifecycle binding already invalidated the handle and prepared closure, `closeSuspending()` only waits for that closure: cancellation ends this caller's wait without restoring the handle or undoing cleanup. Cancellation after cleanup preparation does not roll back closure either. Explicit `close()`, `removeScope()`, and bound Job completion requests arriving during the wait are retained: cancelling the initiating waiter still proceeds with closure instead of restoring the scope. Waiting suspends cancellably without blocking a thread on a condition variable.

Calling `close()` during another closing wait retains a permanent close request while new operations are already rejected. `isClosed` follows the initiating wait's existing state until successful waiting, cancellation, timeout, or interruption prepares cleanup.

`tryClose(timeoutMillis)` restores the scope after a timeout while its own closure waits for in-flight operations if no permanent close request has arrived; a timeout waiting for a prepared `close()` or lifecycle closure only means cleanup has not finished. Explicit `close()`, `removeScope()`, and bound Job completion requests arriving during the wait are retained through timeout or thread interruption.

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
