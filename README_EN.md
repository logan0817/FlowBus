[中文](README.md) · [Documentation](docs/en/README.md)

# FlowBus

FlowBus is a Flow-first event framework built on Kotlin Coroutines / Flow. It is designed for event broadcast, named channels, scoped events, and sticky latest-value replay in Android and Kotlin projects.

It solves the “one place sends a notification, multiple places may react” problem. It is not a state-management framework, a replacement for direct function calls, a request-response mechanism, or a reliable queue.

## Features

| Capability | Entry point | Good for |
| --- | --- | --- |
| Global event broadcast | `postEvent(...)` / `onEvent<T> { ... }` | login success, background sync completion, multi-screen refresh |
| Owner-scoped events | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | Fragment notifying Activity, communication inside a NavBackStackEntry |
| Named channels | `eventChannel<T>("name")` | different `String` events for Toast, SnackBar, and navigation commands |
| Flow-first subscription | `eventFlow<T>()` / `collectEvent(flow)` | getting a `Flow` first, then using `map`, `filter`, `debounce`, or `combine` |
| Sticky latest value | `postStickyEvent(...)` / `onEvent<T>(isSticky = true)` | replaying the latest initialization result after page recreation |
| Send diagnostics | `tryPostEventResult(...)` | inspecting subscriber count, overflow policy, and whether `tryEmit` was rejected |
| Core multi-instance and scope control | `flowbus-core` / `DefaultFlowBus.openScope(...)` | non-Android usage, Repository / Worker / Session isolation |

## Install

Android projects should usually start with `flowbus`:

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.2.0")
```

Pure Kotlin / Coroutines / non-Android projects should use `flowbus-core`:

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.2.0")
```

Module choice:

1. Android screen, ViewModel, Fragment, and Activity communication: start from [`library-android/README_EN.md`](./library-android/README_EN.md).
2. Non-Android usage, multiple bus instances, and explicit scope lifecycle: read [`flowbus-core/README_EN.md`](./flowbus-core/README_EN.md).

## 5-minute quick start

### 1. Define an event

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 2. Register the receiver first

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    viewModel.refreshFrom(event.source)
}
```

### 3. Send after the receiver becomes active and starts collecting

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

Registering `onEvent(...)` does not mean the underlying subscription is immediately ready: lifecycle state and coroutine scheduling matter. Normal events have no replay and are not retained before collection or while the receiver is inactive. Use sticky explicitly for a latest result needed by late subscribers, and `StateFlow` for recoverable UI state.

That is the shortest Android path. First remember this: send with `postEvent(...)`, receive in UI with `onEvent<T> { ... }`.

## Common scenarios

### Scenario 1: global refresh notification

Use this after login, config updates, or background sync completion when multiple screens should react.

```kotlin
data class SyncFinishedEvent(val successCount: Int)

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}

postEvent(SyncFinishedEvent(successCount = 3))
```

### Scenario 2: Fragment notifies Activity

Use this for toolbar refresh, navigation requests, or communication inside the current Activity.

```kotlin
data object ReloadToolbarEvent

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}

requireActivity().postScopedEvent(ReloadToolbarEvent)
```

Here `requireActivity()` means the owner scope that stores the event bus, not the receiver.

### Scenario 3: multiple named channels for the same type

Use this when several events are all `String`, but their business meanings are different.

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}

toastChannel.post("Saved")
```

### Scenario 4: non-Android or lower-level scope control

Use this for Repository / Worker / Session isolation, or when you need multiple `FlowBus` instances.

```kotlin
val syncScope = DefaultFlowBus.openScope("sync-task", closeWhen = scope)

scope.launch(start = CoroutineStart.UNDISPATCHED) {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}

syncScope.post(SyncProgress(percent = 10))
```

This example directly collects the normal Flow with `CoroutineStart.UNDISPATCHED` before sending. Suspending upstream work, `flowOn`, or lifecycle gates still require separate subscription-readiness checks.

## API selection cheat sheet

| Scenario | Recommended entry | Suspends | sticky replay | Result boundary |
| --- | --- | --- | --- | --- |
| Global lightweight broadcast | `postEvent(...)` | No | No | best-effort; may fail in edge cases |
| Only need to know whether `tryEmit` was rejected | `tryPostEvent(...)` | No | No | returns `Boolean`; not business handling success |
| Need a diagnostic send result | `tryPostEventResult(...)` | No | normal / sticky | returns subscriber count, sticky replay count, overflow policy, and outcome |
| Suspending send under configured policy | `emitEvent(...)` | Yes | normal / sticky | backpressure with `SUSPEND` and active subscribers; still follows the configured overflow policy |
| Send to an owner scope | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | No | No | enters only the bus bound to that owner |
| Named channel | `eventChannel<T>("name")` + `channel.post(...)` | No | normal / sticky | good for long-lived business channels |
| Shortest one-line subscription | `onEvent(...)` | No | controlled by `isSticky` | automatically bound to `LifecycleOwner` |
| Get `Flow` first and compose it yourself | `eventFlow<T>()` / `owner.scopedEventFlow<T>()` | No | controlled by `isSticky` | good for `map`, `filter`, and `debounce` |
| Lifecycle-safe collection of any `Flow` | `collectEvent(flow) { ... }` | No | depends on the input `Flow` | not limited to FlowBus |
| Non-Android / multi-instance / scope lifecycle | `flowbus-core` | depends on API | normal / sticky | caller manages `FlowBus`, scopes, and coroutine lifecycle |

`onEvent(...)` and `collectEvent(eventFlow(...))` are not two different systems. The first is the shortest UI-facing API. The second gets a `Flow` first and collects it with lifecycle safety. They listen to the same event stream.

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    render(event)
}

viewLifecycleOwner.collectEvent(eventFlow<RefreshHomeEvent>()) { event ->
    render(event)
}
```

## Boundaries

1. Use `StateFlow` for long-lived page-local state. Do not use FlowBus as a state container.
2. Use direct method calls, use cases, or suspend functions for explicit one-to-one calls.
3. `tryPost*Result.accepted = true` only means the underlying `tryEmit` call was not rejected. It does not mean subscribers already handled the event.
4. `emit*` still follows the configured overflow policy. `SUSPEND` can apply backpressure when active subscribers exist. Normal events without subscribers are not retained, and emission completion does not mean business completion. Use business state, a database, or persistent tasks for operations that must recover or complete reliably.
5. Sticky replay is managed separately from live broadcast. With `DROP_LATEST`, a value dropped from the live buffer can still become replay for late subscribers. Sticky is not a replacement for long-lived state management.
6. `consumeStickyLatestEvent(...)` only reads and clears the current sticky replay. It does not prevent another thread from writing a new sticky value later.
7. Do not put phone numbers, tokens, order IDs, user IDs, or similar sensitive values into `eventName` or `scopeName` logs.


<a id="120-candidate"></a>

## Version 1.2.0

The latest stable version is **1.2.0**, published to Maven Central on 2026-10-15. It adds per-channel buffer and overflow configuration.

- [Channel configuration](flowbus-core/README_EN.md#channel-configuration) and [Android owner configuration](library-android/README_EN.md#android-owner-configuration)
- Maven Central coordinates: `io.github.logan0817:flowbus:1.2.0` or `io.github.logan0817:flowbus-core:1.2.0`
- [Changes, migration, verification, and performance limits](docs/en/release-notes-1.2.0.md)

## Compatibility and release status

Android supports API 21+. Published bytecode targets Java 8. The library is built with Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2, AGP 8.6.1, Gradle 8.7, JDK 17, and compileSdk/targetSdk 35. Consumer toolchain requirements and tested combinations are described in the [compatibility guide](docs/en/compatibility.md).

Use `mavenCentral()` in your dependency repositories. The Android coordinate includes core transitively. There is no published Compose or Navigation module; independent [Compose](compatibility/compose-consumer/README_EN.md), [Navigation](compatibility/navigation-consumer/README_EN.md), and [NavHost](compatibility/navhost-consumer/README_EN.md) consumers demonstrate integration.

## Documentation map

| Goal | Guide |
| --- | --- |
| Android lifecycle, owners, and configuration | [Android guide](library-android/README_EN.md) |
| JVM, explicit instances, scope ownership, and diagnostics | [Core guide](flowbus-core/README_EN.md) |
| Suspend callbacks | [Android](library-android/README_EN.md#suspend-callbacks) / [core](flowbus-core/README_EN.md#suspend-collection) |
| Troubleshoot delivery, sticky, or builds | [Troubleshooting](TROUBLESHOOTING.md) (Chinese) |
| Browse releases and maintained documentation | [Documentation index](docs/en/README.md) / [changelog](CHANGELOG.md) |
| Contribute, report a bug, or report a vulnerability | [Contributing](docs/en/contributing.md) / [support](docs/en/support.md) / [security](docs/en/security.md) |

## Repository layout

`flowbus-core` is maintained directly in the FlowBus main repository, sharing build, CI, and publication with the Android adapter.

| Directory | Purpose |
| --- | --- |
| `flowbus-core` | Kotlin/JVM core, published as `io.github.logan0817:flowbus-core` |
| `library-android` | Android adapter, published as `io.github.logan0817:flowbus` |
| `app` | Sample app and release instrumentation |
| `compatibility` | Independent consumer fixtures and device verification drivers |
| `benchmarks/jvm` | JVM benchmarks and measured results |
| `docs` | Compatibility, release procedure, release notes, and design history |
| `gradle` | Publication and artifact-verification scripts |

Contributors can start with `./gradlew :flowbus-core:test :library-android:testDebugUnitTest :library-android:lintRelease`. See [contributing](docs/en/contributing.md) and the [release checklist](docs/en/release-checklist.md) for environment setup and the complete gates.

## Sample app

<img src="GIF.gif" width="350" />

The sample source lives in the [`app`](./app) module and can be run directly or built locally from the current debug configuration. Generated APK artifacts are not committed to the repository, so a debug package cannot be mistaken for a formal distribution build. A good reading order is:

1. `MainActivity`: global events, owner-scoped events, and the non-UI case entry.
2. `ScopeCaseActivity`: Activity-scoped events, `eventChannel`, and shared receiving inside the same Activity owner scope from both Activity and Fragment.
3. `StickyCaseActivity`: sticky latest-state replay inside the same owner.
4. `LoginActivity`: owner-local bus sample; the form password is validated locally and is not put into event payloads.

## License

[MIT](LICENSE). The core module retains its [copyright notice](flowbus-core/LICENSE). See the [code of conduct](docs/en/code-of-conduct.md) for project participation.
