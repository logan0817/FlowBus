[中文](README.md) · [Project home](../README_EN.md)

# FlowBus Compose

`flowbus-compose` is an optional Jetpack Compose artifact. `FlowBusEffect` collects events while an explicit owner is in its active lifecycle state. The module depends on core, Compose runtime, and Lifecycle, without Material, Compose UI, or Navigation. Neither `flowbus-core` nor `flowbus` transitively adds it.

## Installation

The stable version is **1.3.0**, published to Maven Central. This coordinate is available since 1.3.0; 1.2.0 does not contain this module.

Configure `mavenCentral()` in the consumer repositories, then declare:

```kotlin
implementation("io.github.logan0817:flowbus-compose:1.3.0")
```

Use the isolated [local candidate workflow](../CONTRIBUTING.md#candidate-builds) only when verifying local source changes; candidate verification does not establish public publication.

## Recommended entry point

```kotlin
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import com.logan.flowbus.compose.FlowBusEffect
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey

data class RefreshEvent(val source: String)

private val refreshKey = eventKey<RefreshEvent>("home.refresh")

@Composable
fun RefreshEvents(
    bus: FlowBus,
    owner: LifecycleOwner,
    onRefresh: suspend (RefreshEvent) -> Unit
) {
    FlowBusEffect(from = bus, key = refreshKey, owner = owner) { event ->
        onRefresh(event)
    }
}
```

The caller owns and injects the source. The owner may be an Activity, a Fragment's viewLifecycleOwner, or a navigation back-stack entry. The module does not locate a global bus or navigation owner automatically.

Defaults are `minState = STARTED` and `isSticky = false`. Choose `RESUMED` explicitly for side effects restricted to the currently interactive page. `INITIALIZED` is rejected during composition. Remaining in the Composition does not imply an active owner lifecycle.

## Subscriptions, callbacks, and cancellation

| Change or operation | Behavior |
| --- | --- |
| Source, key name/type, owner, minimum state, or sticky selection changes | Request cancellation of the previous effect and subscribe with the new parameters |
| Only `onReceived` / `onSubscribed` changes | Keep the subscription; subsequent calls use the latest callback |
| An event callback has already started | Finish that captured invocation; an update does not replace work in flight |
| Owner falls below the threshold, is destroyed, or leaves the Composition | Request cancellation of collection and cooperative callback work, without closing the source bus |
| Owner becomes active again | Obtain the flow again and establish a new subscription |

A single subscription executes suspend callbacks sequentially and does not use `collectLatest` by default. Event callbacks use the source logger / errorHandler. Cancellation is not an ordinary business exception. Blocking or `NonCancellable` work cannot be forcibly stopped. The same lifecycle collection loop waits for its previous callback before restarting; parameter changes cannot undo side effects already performed.

## Subscription-established notifications

The optional `onSubscribed: (() -> Unit)?` callback means **this particular underlying subscription has been registered**. Each newly active lifecycle collection invokes it again. Updating the notification callback does not resubscribe or retroactively notify an establishment that has already happened.

The notification does not mean historical sticky replay has been processed, business work has completed, or the subscription will remain active. It runs in the collection coroutine context and should only issue a short signal. Let an independent producer coroutine publish according to the configured buffering policy. Do not block or wait for a send on the same zero-capacity channel from this callback. A notification exception fails that collection directly rather than being classified as an event callback failure.

A one-shot `CompletableDeferred` may coordinate initial subscription only; it cannot represent all subsequent active lifecycle intervals. Ordinary events are not retained without subscribers. Even after notification, zero-capacity `tryPost` can be rejected and DROP policies can discard values. Send completion is not business completion.

Select `isSticky = true` explicitly for sticky collection. Ordinary and sticky streams are separate, and lifecycle restarts can replay again. Use `StateFlow`, business state, or persistence for restorable UI state and business results that must not be missed.

## Build combination and verification

The module uses Kotlin 1.9.25, Compose compiler 1.5.15, runtime 1.5.4, Lifecycle 2.6.2, Coroutines 1.8.1, minSdk 21, and Java 8 bytecode. Consumers may select newer independently verified runtime versions. Direct declarations do not establish resolved versions or compatibility with arbitrary toolchain combinations.

The [Compose consumer](../compatibility/compose-consumer/README_EN.md) resolves candidate Maven artifacts and invokes this module after release R8. It reuses lifecycle, replacement, cancellation, error-policy, and non-cooperative callback checks, adding subscription notifications and callback-update assertions. Record dependency resolution, builds, and device evidence separately for default runtime/ui 1.5.4 and optional 1.7.8.

See the [release checklist](../docs/en/release-checklist.md) for complete gates. Compilation, R8, and driver unit tests do not replace API 21/35 device execution. Historical passing results for the 1.2.0 fixture do not establish that this module's candidate has passed.
