Chinese document [中文文档](./README.md)

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
implementation("io.github.logan0817:flowbus:1.0.6") // Use 1.0.6 after release; the Maven Central badge is the source of truth for the latest published version.
```

Pure Kotlin / Coroutines / non-Android projects should use `flowbus-core`:

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus-core.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus-core)

```gradle
implementation("io.github.logan0817:flowbus-core:1.0.6") // Use 1.0.6 after release; the Maven Central badge is the source of truth for the latest published version.
```

Module choice:

1. Android screen, ViewModel, Fragment, and Activity communication: start from [`library-android/README_EN.md`](./library-android/README_EN.md).
2. Non-Android usage, multiple bus instances, and explicit scope lifecycle: read [`flowbus-core/README_EN.md`](./flowbus-core/README_EN.md).

## 5-minute quick start

### 1. Define an event

```kotlin
data class RefreshHomeEvent(val source: String)
```

### 2. Send the event

```kotlin
postEvent(RefreshHomeEvent(source = "login"))
```

### 3. Receive the event

```kotlin
viewLifecycleOwner.onEvent<RefreshHomeEvent> { event ->
    viewModel.refreshFrom(event.source)
}
```

That is the shortest Android path. First remember this: send with `postEvent(...)`, receive in UI with `onEvent<T> { ... }`.

## Common scenarios

### Scenario 1: global refresh notification

Use this after login, config updates, or background sync completion when multiple screens should react.

```kotlin
data class SyncFinishedEvent(val successCount: Int)

postEvent(SyncFinishedEvent(successCount = 3))

viewLifecycleOwner.onEvent<SyncFinishedEvent> { event ->
    showResult(event.successCount)
}
```

### Scenario 2: Fragment notifies Activity

Use this for toolbar refresh, navigation requests, or communication inside the current Activity.

```kotlin
data object ReloadToolbarEvent

requireActivity().postScopedEvent(ReloadToolbarEvent)

viewLifecycleOwner.onEvent<ReloadToolbarEvent>(from = requireActivity()) {
    renderToolbar()
}
```

Here `requireActivity()` means the owner scope that stores the event bus, not the receiver.

### Scenario 3: multiple named channels for the same type

Use this when several events are all `String`, but their business meanings are different.

```kotlin
val toastChannel = eventChannel<String>("ui.toast")

toastChannel.post("Saved")

viewLifecycleOwner.onEvent(toastChannel) { message ->
    showToast(message)
}
```

### Scenario 4: non-Android or lower-level scope control

Use this for Repository / Worker / Session isolation, or when you need multiple `FlowBus` instances.

```kotlin
val syncScope = DefaultFlowBus.openScope("sync-task", closeWhen = scope)

syncScope.post(SyncProgress(percent = 10))

scope.launch {
    syncScope.flow<SyncProgress>().collect { progress ->
        render(progress)
    }
}
```

## API selection cheat sheet

| Scenario | Recommended entry | Suspends | sticky replay | Result boundary |
| --- | --- | --- | --- | --- |
| Global lightweight broadcast | `postEvent(...)` | No | No | best-effort; may fail in edge cases |
| Only need to know whether `tryEmit` was rejected | `tryPostEvent(...)` | No | No | returns `Boolean`; not business handling success |
| Need a diagnostic send result | `tryPostEventResult(...)` | No | normal / sticky | returns subscriber count, sticky replay count, overflow policy, and outcome |
| Must wait for write acceptance | `emitEvent(...)` | Yes | normal / sticky | waits for the underlying flow under backpressure rules |
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
4. `DROP_OLDEST` / `DROP_LATEST` are not reliable-queue policies. For critical paths, use `emit*`, a business queue, or a state machine.
5. Sticky events only keep the latest value. They are good for initialization results and page restoration, not long-lived state management.
6. `consumeStickyLatestEvent(...)` only reads and clears the current sticky replay. It does not prevent another thread from writing a new sticky value later.
7. Do not put phone numbers, tokens, order IDs, user IDs, or similar sensitive values into `eventName` or `scopeName` logs.

## Compatibility and release status

This documentation targets `1.0.6`. The actual published version on Maven Central is determined by the badge and Central artifact page.

| Item | Version |
| --- | --- |
| Kotlin | `1.9.25` |
| Android Gradle Plugin | `8.6.1` |
| Gradle | `8.7` |
| Gradle runtime JDK | `17` |
| Published bytecode | Java 8 |
| Android SDK | `minSdk=21`, `compileSdk=35`, `targetSdk=35` |

Release gates:

1. `apiCheck` protects public API compatibility.
2. `:flowbus-core:test`, `:library-android:testDebugUnitTest`, and `:app:testDebugUnitTest` cover core, Android wrappers, and sample unit tests.
3. `:library-android:lintRelease`, `:app:lintRelease`, `:app:assembleDebug`, and `:app:assembleRelease` cover library and sample release build quality.
4. `:library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest` covers device regression; the app tests run against release R8/minify.
5. `verifyMavenLocalArtifacts` validates local Maven artifacts, POM, module metadata, license metadata, and developer metadata.
6. `verifyMavenLocalCoreConsumer` compiles a real Kotlin/JVM consumer, validating the standalone `flowbus-core` coordinate.
7. `verifyMavenLocalConsumer` builds a real Android consumer, validating the `flowbus` coordinate and release shrink.
8. `releaseToMavenCentral --dry-run` checks the remote publication task graph without uploading.

## Documentation map

1. Android integration and full scenarios: [`library-android/README_EN.md`](./library-android/README_EN.md)
2. Core capabilities, multi-instance usage, and scope lifecycle: [`flowbus-core/README_EN.md`](./flowbus-core/README_EN.md)
3. Release checklist: [`docs/en/release-checklist.md`](./docs/en/release-checklist.md)
4. Release notes: [`docs/en/release-notes-1.0.6.md`](./docs/en/release-notes-1.0.6.md)
5. Version history: [`CHANGELOG.md`](./CHANGELOG.md)
6. Chinese document: [`README.md`](./README.md)

## Repository layout

| Directory | Purpose |
| --- | --- |
| `flowbus-core` | platform-neutral core module, published as `io.github.logan0817:flowbus-core`; `flowbus-core` is maintained directly in the FlowBus main repository |
| `library-android` | Android adapter module, published as `io.github.logan0817:flowbus`; it depends on the in-repository `flowbus-core` |
| `app` | sample app and integration verification entry |
| `docs` | release checklist and release notes |

## Sample app

<img src="GIF.gif" width="350" />

The sample source lives in the [`app`](./app) module and can be run directly or built locally from the current debug configuration. Generated APK artifacts are not committed to the repository, so a debug package cannot be mistaken for a formal distribution build. A good reading order is:

1. `MainActivity`: global events, owner-scoped events, and the non-UI case entry.
2. `ScopeCaseActivity`: Activity-scoped events, `eventChannel`, and shared receiving inside the same Activity owner scope from both Activity and Fragment.
3. `StickyCaseActivity`: sticky latest-state replay inside the same owner.
4. `LoginActivity`: owner-local bus sample; the form password is validated locally and is not put into event payloads.

## License

```text
MIT License
```
