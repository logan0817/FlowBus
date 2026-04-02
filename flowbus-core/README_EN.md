Chinese document [中文文档](./README.md)

# flowbus-core

`flowbus-core` is a standalone Kotlin event bus library that is ready to use directly.

Built on Kotlin Coroutines and Flow, it provides typed event channels, sticky events, shared scopes, and lifecycle binding.

## Install

```gradle
implementation("io.github.logan0817:flowbus-core:<latest-version>")
```

## Choose this module when

- you are working in a pure Kotlin / Coroutines project
- you need the bus in server, desktop, CLI, worker, or shared domain layers
- you want explicit `FlowBus`, `EventKey`, and scope control
- you want to start with `DefaultFlowBus` / `bus.flow<T>()` style APIs and drop down to the raw API only when needed
- you need to bind bus scopes to a `Job` or `CoroutineScope`
- you are building your own adapter layer on top of FlowBus

## Core API

- `FlowBus`: root bus entry
- `DefaultFlowBus`: default singleton entry that also exposes the full root bus API
- `EventKey<T>` / `eventKey(...)`: typed event keys
- `post(...)` / `emit(...)`: send normal events
- `postSticky(...)` / `emitSticky(...)`: send sticky events
- `flow(...)` / `stickyFlow(...)`: read events as `Flow`
- `FlowBus.post(value)` / `FlowBus.flow<T>()`: simplified overloads that avoid declaring `EventKey` explicitly
- `event.send()` / `event.sendOn(bus)`: value-first sugar for a more Kotlin-idiomatic sending style
- `EventChannel<T>` / `eventChannel(...)`: turn a named event stream into a first-class object
- `FlowBus.scoped(...)` / `DefaultFlowBus.scoped(...)`: get a named shared scope
- `FlowBus.openScope(...)` / `DefaultFlowBus.openScope(...)`: create an explicitly closeable scope
- `FlowBusScope.bindTo(job)` / `bindTo(scope)`: close a scope with coroutine lifecycle
- `FlowBusConfig`: customize logger, error handler, and buffer policy

## Basic usage

```kotlin
val bus = FlowBus()
val syncEvent = eventKey<SyncEvent>("sync.event")

bus.post(syncEvent, SyncEvent.Start)

scope.launch {
    bus.flow(syncEvent).collect { event ->
        handle(event)
    }
}
```

## Default out-of-the-box usage

If you do not want to create `FlowBus` and `EventKey` up front, you can use `DefaultFlowBus` directly,
or call the simplified overloads on an existing `FlowBus` / `ScopedFlowBus` / `FlowBusScope`:

```kotlin
DefaultFlowBus.post(SyncEvent.Start)

scope.launch {
    DefaultFlowBus.flow<SyncEvent>().collect { event ->
        handle(event)
    }
}

val bus = FlowBus()
bus.post(SyncEvent.Start)

scope.launch {
    bus.flow<SyncEvent>().collect { event ->
        handle(event)
    }
}
```

By default, the event channel name is the fully qualified type name.
If you prefer a value-first style where the event sends itself, you can also write:

```kotlin
SyncEvent.Start.send()
SyncEvent.Done.sendOn(bus)
SyncEvent.Done.sendOn(bus, eventName = "sync.secondary")
```

If you want the named channel itself to become a stable handle instead of repeating raw strings,
you can also write:

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("Saved")

scope.launch {
    toastChannel.flow().collect { message ->
        showToast(message)
    }
}
```

If you need multiple channels for the same payload type, pass `eventName` explicitly:

```kotlin
bus.post(value = SyncEvent.Start, eventName = "sync.primary")
bus.flow<SyncEvent>(eventName = "sync.primary")
```

If you want the default singleton to start with a fixed config or a DI-managed bus,
install it before the first use:

```kotlin
DefaultFlowBus.configure(
    FlowBusConfig(stickyReplay = 1)
)

// or
DefaultFlowBus.install(
    FlowBus(
        config = FlowBusConfig(normalBufferCapacity = 32)
    )
)
```

If the simplified API is not enough, you can still use typed keys and root scope APIs directly on `DefaultFlowBus`:

```kotlin
val syncEvent = eventKey<SyncEvent>("sync.event")
val session = DefaultFlowBus.openScope("sync-session", closeWhen = scope)

DefaultFlowBus.post(syncEvent, SyncEvent.Start)
session.post(syncEvent, SyncEvent.Done)
```

## Scoped usage

```kotlin
val bus = FlowBus()
val session = bus.openScope("sync-session", closeWhen = scope)
val syncState = eventKey<SyncState>("sync.state")

session.postSticky(syncState, SyncState.Running)

scope.launch {
    session.stickyFlow(syncState).collect { state ->
        render(state)
    }
}
```

If you only need a shared scoped view and do not want to own its close lifecycle, use `scoped(...)` directly:

```kotlin
val featureBus = DefaultFlowBus.scoped("feature-a")
featureBus.post(FeatureEvent.Loading)
```

## Module boundary

`flowbus-core` focuses on platform-neutral event bus capabilities: event dispatching,
sticky events, named scopes, lifecycle binding, logging, and error handling configuration.

If you need a platform-specific adapter layer, you can build it on top of this module.

If you only want a quick integration without managing bus instances yourself, start with `DefaultFlowBus`.
If you need multi-instance isolation, dependency injection, or explicit lifecycle control, drop back to raw `FlowBus`.

## Repository links

- repository root (the Android integration module `library-android` is in the same repo): [FlowBus](https://github.com/logan0817/FlowBus)
- demo module: [app](https://github.com/logan0817/FlowBus/tree/master/app)
