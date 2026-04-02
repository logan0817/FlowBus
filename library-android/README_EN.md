Chinese document [中文文档](./README.md)

# library-android

`library-android` is the Android adapter module of FlowBus.

The Gradle module name is `library-android`, and the published Android artifact is:

```gradle
implementation("io.github.logan0817:flowbus:<latest-version>")
```

This module is built on top of `flowbus-core` and is the recommended entry point for Android apps.

## Choose this module when

- you are building an Android app
- you want global or `ViewModelStoreOwner`-scoped events
- you prefer Flow-first APIs such as `eventFlow<T>()`
- you want lifecycle-aware collection with `collectEvent(...)`

## Understand the role of FlowBus first

In Android apps, FlowBus is a better fit for event broadcasts such as cross-screen, cross-module,
or cross-layer notifications between UI, ViewModel, Repository, and Worker code.

It is not a good fit for:
- page-local state management: prefer `StateFlow`
- explicit one-to-one calls: prefer direct function calls / use cases
- strict request-response pairs: prefer return values, suspend functions, or dedicated channels

If you need platform-neutral primitives or want to build your own adapter layer,
use `flowbus-core` instead:

- local core doc: [`../flowbus-core/README_EN.md`](../flowbus-core/README_EN.md)
- GitHub URL: [flowbus-core README](https://github.com/logan0817/FlowBus/blob/master/flowbus-core/README_EN.md)

## Recommended Android API

- `postEvent(...)` / `postStickyEvent(...)`: send global events with best-effort delivery
- `emitEvent(...)` / `emitStickyEvent(...)`: suspend until global delivery succeeds
- `postEventTo(owner = ...)` / `postStickyEventTo(owner = ...)`: send owner-scoped events with best-effort delivery
- `emitEventTo(owner = ...)` / `emitStickyEventTo(owner = ...)`: suspend until owner-scoped delivery succeeds
- `eventFlow<T>()` / `stickyEventFlow<T>()`: read global events as `Flow`
- `eventChannel<T>("...")`: declare a reusable named event handle
- `channel.post(...)` / `channel.flow()`: organize global event usage around the named handle
- `channel.postTo(owner, ...)` / `channel.flowFrom(owner)`: organize scoped event usage around the named handle
- `owner.eventFlow<T>()` / `owner.stickyEventFlow<T>()`: a more natural owner-centric way to read scoped events
- `eventFlowFrom<T>(owner = ...)` / `stickyEventFlowFrom<T>(owner = ...)`: read owner-scoped events
- `LifecycleOwner.collectEvent(flow) { ... }`: collect with lifecycle awareness
- `LifecycleOwner.onEvent(channel)` / `LifecycleOwner.onEvent(from = ..., channel = ...)`: subscribe directly with a named handle
- `LifecycleOwner.onEvent<T>(from = ...)` / `CoroutineScope.onEvent<T>(from = ...)`: shorter direct subscription aliases
- `LifecycleOwner.subscribeEvent(owner = ...)` / `CoroutineScope.subscribeEventFrom(owner = ...)`: subscribe directly to owner-scoped events
- `removeStickyEvent<T>()` / `clearStickyEvent<T>()`: remove or clear global sticky events
- `removeStickyEvent<T>(owner)` / `clearStickyEvent<T>(owner)`: remove or clear owner-scoped sticky events
- `FlowBusAndroid.configure(...)`: override the default core config before first use

## Recommended usage rules

- FlowBus routes events by type by default; the same type means the same event channel
- The Android API now also supports explicit `eventName`, so one payload type can back multiple semantic channels
- Multiple subscribers of the same type all receive the event; this is a broadcast model, not an exclusive queue
- Use `postEvent(...)` / `eventFlow<T>()` for global events
- Use `postEventTo(owner, ...)` / `eventFlowFrom<T>(owner = ...)` or the more natural `owner.eventFlow<T>()` for scoped events
- In UI code, prefer pairing FlowBus with `collectEvent(...)` so collection follows lifecycle state
- Use `emitEvent(...)` or `emitEventTo(...)` when delivery must succeed
- `postEvent(...)` is best-effort and can be dropped if the underlying buffer cannot accept the value immediately
- Sticky events are only for cases where late subscribers should still see the latest value

## Event modeling advice

Prefer explicit business event types over sending raw `String`, `Int`, or other primitive values long-term.
Typical patterns are `data class` for one clear event, and `sealed interface` / `sealed class` for multiple actions in one domain.

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

## Shortest style

```kotlin
requireActivity().postEvent(ActivityEvent.Reload)
viewLifecycleOwner.collectEvent(requireActivity().eventFlow<ActivityEvent>()) {
    renderActivity(it)
}

viewLifecycleOwner.onEvent<GlobalEvent> {
    renderGlobal(it)
}

postEvent(event = "toast", eventName = "ui.toast")
viewLifecycleOwner.collectEvent(eventFlow<String>(eventName = "ui.toast")) {
    showToast(it)
}
```

## Named channel style

```kotlin
val toastChannel = eventChannel<String>("ui.toast")
val activityReload = eventChannel<ActivityEvent>("activity.reload")

toastChannel.post("Saved")
activityReload.postTo(requireActivity(), ActivityEvent.Reload)

viewLifecycleOwner.collectEvent(toastChannel.flow()) {
    showToast(it)
}

viewLifecycleOwner.onEvent(from = requireActivity(), channel = activityReload) {
    renderActivity(it)
}
```

## Fragment example

```kotlin
class DemoFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postEvent(GlobalEvent.Refresh)
        postEventTo(owner = requireActivity(), event = ActivityEvent.Reload)

        viewLifecycleOwner.collectEvent(eventFlow<GlobalEvent>()) {
            renderGlobal(it)
        }

        viewLifecycleOwner.collectEvent(
            eventFlowFrom<ActivityEvent>(owner = requireActivity())
        ) {
            renderActivity(it)
        }
    }
}
```

## Optional configuration

Call `FlowBusAndroid.configure(...)` before the first `postEvent(...)`,
`emitEvent(...)`, `eventFlow(...)`, or other bus access if you want to customize the underlying
core configuration:

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
