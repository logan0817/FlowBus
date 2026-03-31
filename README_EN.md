Chinese document [中文文档](./README.md)

# FlowBus

FlowBus is an Android event bus built on Kotlin Coroutines and SharedFlow.

It provides:
- global events
- Activity / Fragment scoped events
- lifecycle-aware subscriptions
- sticky events
- delayed post
- dispatcher switching

## Installation

```gradle
repositories {
    mavenCentral()
}
```

[![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

```gradle
implementation("io.github.logan0817:flowbus:1.0.3")
```

## Two simple rules

### 1. `owner` decides where the event is posted

- no `owner`: post to the global bus
- with `owner`: post to the local bus owned by that `Activity` or `Fragment`

```kotlin
postEvent(GlobalEvent("refresh app"))
postEventTo(owner = requireActivity(), event = ActivityEvent("refresh activity"))
postEventTo(owner = this@DemoFragment, event = FragmentEvent("refresh fragment"))
```

### 2. The receiver decides who owns the subscription lifecycle

- the receiver of `subscribeEvent(...)` controls the subscription lifecycle
- `owner = ...` tells FlowBus which bus scope to read from

```kotlin
// Use the Fragment view lifecycle to receive Activity-scoped events
viewLifecycleOwner.subscribeEvent<ActivityEvent>(owner = requireActivity()) {
    render(it)
}
```

## Define events

Prefer dedicated event classes over raw `String` or `Int` values.

```kotlin
data class GlobalEvent(val message: String)
data class ActivityEvent(val message: String)
data class FragmentEvent(val message: String)
```

## Common usage

### Post events

```kotlin
// Global event
postEvent(GlobalEvent("refresh app"))

// Activity-scoped event
postEventTo(owner = requireActivity(), event = ActivityEvent("refresh activity"))

// Fragment-scoped event
postEventTo(owner = this@DemoFragment, event = FragmentEvent("refresh fragment"))

// Delayed post
postEvent(event = GlobalEvent("delay"), delayMillis = 1_000)
```

### Post sticky events

```kotlin
postStickyEvent(GlobalEvent("latest global state"))
postStickyEventTo(owner = requireActivity(), event = ActivityEvent("latest activity state"))
```

### Subscribe inside an Activity

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global events
        subscribeEvent<GlobalEvent> {
            renderGlobal(it)
        }

        // Activity-scoped events owned by this Activity
        subscribeEvent<ActivityEvent>(owner = this) {
            renderActivity(it)
        }
    }
}
```

### Subscribe inside a Fragment

If the callback touches views, prefer `viewLifecycleOwner`.

```kotlin
class DemoFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Global bus
        viewLifecycleOwner.subscribeEvent<GlobalEvent> {
            renderGlobal(it)
        }

        // Activity-scoped bus
        viewLifecycleOwner.subscribeEvent<ActivityEvent>(owner = requireActivity()) {
            renderActivity(it)
        }

        // Fragment-scoped bus
        viewLifecycleOwner.subscribeEvent<FragmentEvent>(owner = this@DemoFragment) {
            renderFragment(it)
        }
    }
}
```

### Subscribe inside a ViewModel or CoroutineScope

```kotlin
class DemoViewModel : ViewModel() {

    init {
        // Global events
        viewModelScope.subscribeEvent<GlobalEvent> {
            handleGlobal(it)
        }

        // Activity / Fragment scoped events
        viewModelScope.subscribeEventFrom<ActivityEvent>(owner = activityOwner) {
            handleActivity(it)
        }
    }
}
```

## Threading and lifecycle

```kotlin
subscribeEvent<GlobalEvent>(dispatcher = Dispatchers.IO) {
    saveToDisk(it)
}

subscribeEvent<GlobalEvent>(
    minLifecycleState = Lifecycle.State.RESUMED
) {
    render(it)
}
```

## Sticky cleanup

```kotlin

/**
 * Remove specified sticky event stream
 * Only do this when you explicitly own that sticky scope;
 * do not clear global sticky state automatically from page teardown.
 */
removeStickyEvent<GlobalEvent>()
removeStickyEvent<ActivityEvent>(owner = requireActivity())
removeStickyEvent<FragmentEvent>(owner = this@TestFragment)

/**
 * Clears the replay cache for the local sticky event type T, but keeps the Flow instance.
 * Prefer clearing only sticky scopes that your component explicitly owns.
 */
clearStickyEvent<GlobalEvent>()
clearStickyEvent<ActivityEvent>(owner = requireActivity())
clearStickyEvent<ActivityEvent>(owner = this@TestFragment)
```

- `clearStickyEvent`: clears the latest cached value but keeps the Sticky Flow
- `removeStickyEvent`: removes the Sticky Flow completely

## Notes

- In Fragments, prefer `viewLifecycleOwner` when touching views
- Normal events are for UI communication, not for guaranteed task queues
- Sticky events keep only the latest value
- Move heavy work to `Dispatchers.IO` or `Dispatchers.Default`
- Old scoped APIs still work, but the explicit `owner` API is recommended for production code

## Demo

<img src="GIF.gif" width="350" />

> You can also download the [demo app](https://raw.githubusercontent.com/logan0817/FlowBus/master/app/release/app-release.apk) to try it directly.

## License

```text
MIT License
```
