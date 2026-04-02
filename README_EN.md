Chinese document [中文文档](./README.md)

# FlowBus

FlowBus is a Flow-first event framework built on Kotlin Coroutines / Flow.

This repository contains two public modules:
- `flowbus-core`: platform-neutral core module
- `flowbus` (`library-android`): Android adapter module built on top of `flowbus-core`

## Why FlowBus exists

FlowBus is for communication that naturally looks like event broadcast, such as cross-layer delivery
between UI, ViewModel, Repository, and Worker code, plus one model for both global and scoped events.

It is not meant to replace:
- page-local state management: prefer `StateFlow`
- explicit one-to-one calls: prefer direct function calls / use cases
- strict request-response pairs: prefer return values, suspend functions, or dedicated channels

## Choose the right module

### For Android projects

Start with:

```gradle
implementation("io.github.logan0817:flowbus:<latest-version>")
```

Use this module if you want:
- global events in Android apps
- `ViewModelStoreOwner` scoped events
- `eventFlow<T>()` / `eventFlowFrom<T>(owner = ...)`
- reusable named event handles such as `eventChannel<T>("...")`
- `collectEvent(flow) { ... }`
- lifecycle-aware collection in Activity / Fragment / ViewModel code

Android module docs:
- local doc: [`library-android/README_EN.md`](./library-android/README_EN.md)
- GitHub URL: [library-android README](https://github.com/logan0817/FlowBus/blob/master/library-android/README_EN.md)

### For platform-neutral Kotlin / Coroutine usage

Use:

```gradle
implementation("io.github.logan0817:flowbus-core:<latest-version>")
```

Use this module if you want:
- root bus and named scoped bus
- `FlowBus`, `DefaultFlowBus`, `EventKey`, sticky events
- `EventChannel<T>` / `eventChannel(...)`
- `FlowBusScope`
- scope lifecycle binding with `Job` / `CoroutineScope`
- simplified APIs like `bus.post(value)` / `DefaultFlowBus.flow<T>()`
- custom logger / error handler / buffer strategy
- your own upper-layer adapter or non-Android architecture integration

Core module docs:
- local doc: [`flowbus-core/README_EN.md`](./flowbus-core/README_EN.md)
- GitHub URL: [flowbus-core README](https://github.com/logan0817/FlowBus/blob/master/flowbus-core/README_EN.md)

## Module relationship

- `flowbus-core` defines the event model and core runtime behavior
- `flowbus` provides Android-oriented APIs on top of `flowbus-core`
- Android users should treat `flowbus` as the default entry
- non-Android or adapter authors should start from `flowbus-core`

## Usage principles

- FlowBus uses a type-based broadcast model; events of the same type go through the same event stream
- In Android apps, prefer explicit business event types with `data class` / `sealed class`
- Use `eventFlow<T>()` for global broadcasts and `eventFlowFrom<T>(owner = ...)` for `ViewModelStoreOwner`-scoped events
- Use `emitEvent*` when delivery must succeed, and `postEvent*` only when best-effort delivery is acceptable
- Sticky events are only for "late subscribers should still see the latest value"

## Repository layout

- `flowbus-core`: core framework module
- `library-android`: Android adapter module
- `app`: demo app

## Quick navigation

### Android usage

Recommended Android APIs live in `flowbus`:
- `postEvent(...)` / `emitEvent(...)`
- `postEventTo(owner = ..., event = ...)` / `emitEventTo(owner = ..., event = ...)`
- `eventFlow<T>()`
- `eventFlowFrom<T>(owner = ...)`
- `eventChannel<T>("...")`
- `channel.post(...)` / `channel.flow()`
- `channel.postTo(owner, ...)` / `channel.flowFrom(owner)`
- `owner.eventFlow<T>()`
- `stickyEventFlow<T>()`
- `stickyEventFlowFrom<T>(owner = ...)`
- `LifecycleOwner.onEvent(channel)` / `LifecycleOwner.onEvent(from = ..., channel = ...)`
- `LifecycleOwner.onEvent<T>(from = ...)`
- `collectEvent(flow) { ... }`
- `removeStickyEvent<T>()` / `clearStickyEvent<T>()`
- `postEvent*` / `postStickyEvent*`: best-effort delivery
- `emitEvent*` / `emitStickyEvent*`: suspend until delivery succeeds under backpressure

See:
- [`library-android/README_EN.md`](./library-android/README_EN.md)
- [Android module on GitHub](https://github.com/logan0817/FlowBus/tree/master/library-android)

### Core usage

Recommended core APIs live in `flowbus-core`:
- `DefaultFlowBus`
- `FlowBus`
- `EventKey`
- `eventKey(...)`
- `EventChannel<T>` / `eventChannel(...)`
- `FlowBus.post(value)` / `FlowBus.flow<T>()`
- `channel.post(...)` / `channel.flow()`
- `channel.postOn(bus, ...)` / `channel.flowOn(bus)`
- `FlowBus.scoped(...)` / `DefaultFlowBus.scoped(...)`
- `FlowBus.openScope(...)` / `DefaultFlowBus.openScope(...)`
- `FlowBusScope.bindTo(job)`
- `FlowBusScope.bindTo(scope)`

See:
- [`flowbus-core/README_EN.md`](./flowbus-core/README_EN.md)
- [Core module on GitHub](https://github.com/logan0817/FlowBus/tree/master/flowbus-core)

## Demo

<img src="GIF.gif" width="350" />

Demo source lives in the [`app`](./app) module, so you can run it locally or build the APK yourself.

## License

```text
MIT License
```

