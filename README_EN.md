
Chinese document [中文文档](./README.md)

FlowBus is a Kotlin Event Bus implemented using Kotlin Coroutines and Flows.

FlowBus supports the following features: Sticky Events, Thread Switching, Multiple Subscriptions,
Delayed Posting, Lifecycle Awareness, and Ordered Message Reception.

 Feature                             | Detailed Explanation                                                                                                                                                                                                                                                                                                                                                                                                                                                   
-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Achieves "Sticky" Effect            | When replay is set to 1, the Flow caches the most recently emitted value.<br>Any new subscriber will immediately receive this cached value upon starting collection, even if the value was published before the subscription began.                                                                                                                                                                                                                                    
 Easy Thread Switching               | Inside the subscribe method, FlowBus collects events in receive order and then switches to the target dispatcher with withContext(dispatcher).<br>* Collection (Collect): Can be safely started/stopped inside a lifecycle-aware coroutine.<br>* Handling (Handle): Each received event is processed serially on the requested dispatcher, which avoids blocking the UI thread while preserving per-subscriber ordering. 
 Multiple Subscriptions              | SharedFlow is a Hot Flow that supports Multicast.<br>The same Flow instance can be simultaneously subscribed to by multiple collect calls.<br>When an event is published via tryEmit(), all currently active observers receive the event simultaneously.                                                                                                                                                                                                               
 Automatic Event Cleanup             | When a MutableSharedFlow's replay is set to 0, published events (via tryEmit or emit) will be directly discarded if no active subscribers are listening.This effectively prevents the Event Backlog problem, stopping the application's memory usage from rising due to excessive events when there are no subscribers for a long time.                                                                                                                                
 Lifecycle Awareness                 | Ensures that the coroutine block inside repeatOnLifecycle (i.e., the collect operation) is launched only when the state of the lifecycleOwner (such as an Activity/Fragment) is greater than or equal to startState.                                                                                                                                                                                                                                                   
 Control Over Launch Timing          | Allows control over when event responses should begin by passing in a specific Lifecycle.State.                                                                                                                                                                                                                                                                                                                                                                        
 Coroutine Suspension and Resumption | When the lifecycleOwner's state drops below startState (e.g., transitioning from STARTED to STOPPED), repeatOnLifecycle automatically cancels its internal collect coroutine.<br>When the state returns to startState again, a new collect coroutine is automatically restarted.                                                                                                                                                                                       

## Integration

### Gradle:

1. Add the remote repository to your Project's **build.gradle** or **setting.gradle**

    ```gradle
    repositories {
        //
        mavenCentral()
    }
    ```

2. Add the dependency to your Module's **build.gradle**
   [![Maven Central](https://img.shields.io/maven-central/v/io.github.logan0817/flowbus.svg?label=Latest%20Release)](https://central.sonatype.com/artifact/io.github.logan0817/flowbus)

    ```gradle
   implementation 'io.github.logan0817:flowbus:1.0.1' // Replace with the latest version shown by the badge above
    ```

## Demo Effect
<img src="GIF.gif" width="350" />

> You can also directly download the [Demo App](https://raw.githubusercontent.com/logan0817/FlowBus/master/app/release/app-release.apk) to experience the effect.


## post Examples

```kotlin
//Global Scope
postEvent(GlobalEvent("Test GlobalEvent"))

//Activity Scope
postEvent(requireActivity(), ActivityEvent("Test ActivityEvent"))

//Fragment Scope
postEvent(this@TestFragment, FragmentEvent("Test FragmentEvent"))
```

```kotlin
//延迟发送
postEvent(GlobalEvent(value = "Delay GlobalEvent"), 1000)
```

## subscribe Examples

```kotlin
/** subscribeForever
 *  Use a lifecycle-managed CoroutineScope when possible
 */
lifecycleScope.subscribeEvent<GlobalEvent> {

}

/** subscribe GlobalScopeEvent
 */
subscribeEvent<GlobalEvent> {

}
/** subscribe ActivityEvent
 */
subscribeEvent<ActivityEvent>(scope = activity) {

}
/** subscribe FragmentEvent
 */
subscribeEvent<FragmentEvent>(scope = fragment) {

}

```

## Thread Switching Example

```kotlin
subscribeEvent<XEvent>(dispatcher = Dispatchers.IO) {
    // Do background work only; do not update Views directly here

}
```

## Lifecycle Awareness Example

```kotlin
subscribeEvent<XEvent>(minLifecycleState = Lifecycle.State.RESUMED) {

}
```

## Sticky Subscription Example

```kotlin
subscribeEvent<XEvent>(isSticky = true) {

}
```

```kotlin
// Post a sticky event
postStickyEvent(XEvent(...))
```

## removeStickyEvent

```kotlin
/**
 * Remove specified sticky event stream
 * Only do this when you explicitly own that sticky scope;
 * do not clear global sticky state automatically from page teardown.
 */
//In GlobalScope
removeStickyEvent<XEvent>()
//In Activity
removeStickyEvent<XEvent>(scope = activity)
//In Fragment
removeStickyEvent<XEvent>(scope = fragment)
```

## clearStickyEvent

```kotlin
/**
 * Clears the replay cache for the local sticky event type T, but keeps the Flow instance.
 * Prefer clearing only sticky scopes that your component explicitly owns.
 */
//In GlobalScope
clearStickyEvent<GlobalEvent>()
//In Activity
clearStickyEvent<XEvent>(scope = activity)
//In Fragment
clearStickyEvent<XEvent>(scope = fragment)
```

### License

```
MIT License

Copyright (c) 2025 Logan Gan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
