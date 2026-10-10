# FlowBus 1.1.0 release notes (unreleased candidate)

[中文](../release-notes-1.1.0.md)

1.1.0 combines reliability fixes and new suspending collection APIs. Maven Central determines availability. Relative to published 1.0.7, base dependencies, minSdk, and bytecode requirements remain unchanged; existing callers need no migration.

## Changes

- Replace core ConcurrentHashMap.compute* methods introduced on Android API 24, preserving atomic updates and cleanup isolation from reopened scopes.
- Make closeSuspending waits cancellable. Cancellation before cleanup preparation can restore availability when the caller initiated closure and no permanent request exists. Cancelling a wait on prepared closure does not undo closure. Explicit close, removeScope, and bound Job completion requests survive cancellation, timeout, and interruption.
- Freeze Android configuration only after successful construction, allowing recovery after initialization failure; global reads use synchronized initialization.
- Add core collectSuspending / collectStickySuspending, typed-key/reified and default/explicit-source channel helpers.
- Add Android onEventSuspending, FlowEventBus.subscribeEventSuspending, collectEventSuspending, and collectEventFrom / collectEventFromSuspending for root, shared, and owned sources.
- Add independent consumers, old-core binary regression, and Android public JVM symbol checks. Compose, Navigation, and actual NavHost remain fixtures rather than published modules.

## Adoption and migration

After publication:

```kotlin
implementation("io.github.logan0817:flowbus:1.1.0")
// Non-Android:
implementation("io.github.logan0817:flowbus-core:1.1.0")
```

Existing collect, onEvent, collectEvent, and sending APIs remain. Choose separately named APIs for suspending work:

```kotlin
viewLifecycleOwner.onEventSuspending<RefreshHomeEvent> { event ->
    viewModel.refreshSuspending(event.source)
}
```

The event and business method are application-defined. Each subscription processes callbacks sequentially, without implicit collectLatest. Ordinary callback errors use source logger/errorHandler; default Rethrow terminates collection, while returning handlers allow continuation. CancellationException propagates directly. Generic collectEventSuspending(flow) propagates errors without automatically applying a bus policy.

Lifecycle stop requests cancellation. Restart of the same lifecycle collection waits for the old callback. Blocking and NonCancellable work cannot be forcibly stopped, and cancellation does not roll back effects. Callers own explicit sources; owner destruction only cancels collection.

Normal events without subscribers are not retained; registration does not establish readiness. emit/awaitSend follow overflow policy; SUSPEND can backpressure only with active subscribers. Completion or accepted=true does not establish business success. Sticky replay is independent of live broadcast: a DROP_LATEST value rejected from a live buffer can still become replay. Durable work needs business state or storage.

## Compatibility and verification

Base settings remain Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2, AGP 8.6.1, Gradle 8.7, build JDK 17, Java 8 bytecode, and minSdk 21 / compileSdk 35 / targetSdk 35. See the [compatibility assessment](next-version-plan.md#compatibility-assessment) for tested combinations and actual resolution.

Gates include apiCheck, JVM tests, release lint/R8, POM/module metadata, license/developer, verifyMavenLocalArtifacts, verifyMavenLocalCoreConsumer, verifyMavenLocalBinaryCoreConsumer, verifyMavenLocalConsumer, and the independent Compose consumer. Device tasks are :library-android:connectedDebugAndroidTest and :app:connectedReleaseAndroidTest; the sample uses release R8.

Published 1.0.7 core has stable-descriptor and unchanged-consumer execution evidence. Android public-snapshot symbols are retained, with one non-PublishedApi internal removal recorded in the raw report. These checks are not full ABI certification.

**Release gates: the final commit must pass API 21 library and minified Maven consumer device checks and complete remote CI.** See the [release checklist](release-checklist.md) for current candidate results and reports. API 24 cannot replace minimum-API verification. Full older toolchains, AGP 9.x, process-death recovery, deep links, and arbitrary animations remain uncertified. A published Compose module is outside this release.

## Publication and rollback

Use the [release checklist](release-checklist.md) and isolated Maven repository. Remote dry-run checks only the task graph. Upload, signature acceptance, and Central release are separate operations.

Consumers can return to published 1.0.7, reverting calls to new APIs when necessary. Never overwrite a published version.
