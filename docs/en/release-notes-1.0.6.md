# FlowBus 1.0.6 Release Notes

[中文](../release-notes-1.0.6.md)

## 1. Release Summary

`1.0.6` improves reliability and observability. The focus is not shorter call sites, but clearer send results, scope lifecycle behavior, sticky replay handling, and release verification.

This upgrade is a good fit for teams that: 1. already use FlowBus in Android UI, ViewModel, Repository, or Worker code. 2. need to inspect whether an event was accepted by the bus. 3. need to clear the latest sticky value after reading it. 4. want core and Android artifacts maintained from the main repository.

## 2. Capability Overview

| Capability | New or strengthened entry | Use case | Boundary |
| --- | --- | --- | --- |
| Main-repository maintenance | root project publishes `flowbus-core` and `flowbus` | unified release, CI, and API baseline | no old child-repository initialization or release workflow |
| Send-result diagnostics | `tryPostResult(...)`, `tryPostStickyResult(...)`, `tryPostEventResult(...)` | logs, unit-test assertions, send failure investigation | only reports whether `tryEmit` was rejected, not business handling success |
| Read-only diagnostics snapshot | `inspect()`, `inspector().snapshot()`, `inspectScope(...)` | inspect event name, scope, subscription count, sticky replay count, and send metrics | exposes metadata only, not sticky payload |
| Direct scope close | `FlowBusScope.close()`, `bindTo(job)` | lifecycle callbacks and UI-layer handle release | invalidates the handle immediately; operations already started keep the original store until cleanup runs |
| Async scope close | `closeSuspending()`, `tryClose(timeoutMillis)`, `FlowBusCloseResult` | closing scopes from UI or single-thread environments | returns timeout result instead of forcing a blocking close |
| One-time latest sticky consumption | `consumeStickyLatest(...)`, `consumeStickyLatestEvent(...)`, `channel.consumeStickyLatest()` | read the latest sticky result and clear replay | only handles sticky replay in the current store; not a state-management replacement |
| Stronger release verification | `apiCheck`, release lint, assemble, local Maven artifact check, release dry-run | verify API, artifacts, and publication task graph before release | connected tests need a device or CI emulator |
| Release script layout | `gradle/release-publishing.gradle.kts`, `gradle/release-verification.gradle.kts` | separate publication entry points from pre-release verification | the root `build.gradle.kts` only keeps shared configuration and script entry points |

## 3. API State Comparison

| State or goal | Recommended API | Suspends | Keeps replay | Clears replay | How to read the result |
| --- | --- | --- | --- | --- | --- |
| Lightweight normal event | `post(...)` / `postEvent(...)` | No | No | No | best-effort; failure may return `false` or log a warning |
| Normal event diagnostics | `tryPostResult(...)` / `tryPostEventResult(...)` | No | No | No | returns subscription count, overflow policy, sticky replay count, and outcome |
| Important event write | `emit(...)` / `emitEvent(...)` | Yes | No | No | waits until the underlying flow accepts the value under backpressure rules |
| Latest sticky value | `postSticky(...)` / `postStickyEvent(...)` | No | Yes | No | late subscribers receive the latest value first |
| Important sticky write | `emitSticky(...)` | Yes | Yes | No | waits until the sticky write is accepted under backpressure rules |
| Sticky replay cleanup | `clearSticky(...)` / `clearStickyEvent(...)` | No | Cleared | Yes | keeps the channel and clears replay cache |
| Sticky channel removal | `removeSticky(...)` / `removeStickyEvent(...)` | No | Cleared | Yes | removes the current store entry; later access recreates it lazily |
| One-time sticky consumption | `consumeStickyLatest(...)` / `consumeStickyLatestEvent(...)` | No | Cleared after read | Yes | returns the latest replay value; returns `null` when none exists |

## 4. Android And Core Mapping

| Capability | Android entry | core entry | Recommended reading |
| --- | --- | --- | --- |
| Global send | `postEvent(...)` | `DefaultFlowBus.post(...)` | Android apps should start with the Android entry |
| Owner-scoped send | `postEventTo(owner, ...)` / `owner.postScopedEvent(...)` | `FlowBus.scoped(...)` / `FlowBusScope` | use owner scope for local Android page events |
| Named channel | `eventChannel<T>("name")` | `eventChannel<T>("name")` | prefer named channels for reusable business meaning |
| Lifecycle-safe receiving | `onEvent(...)` / `collectEvent(...)` | `collect(...)` / caller-managed `CoroutineScope` | UI code should prefer Android lifecycle entries |
| One-time sticky consumption | `consumeStickyLatestEvent(...)` / `channel.consumeStickyLatest()` | `consumeStickyLatest(...)` | use only for clearly bounded sticky replay cleanup |
| Diagnostics snapshot | exposed through core bus behavior | `inspect()` / `inspector().snapshot()` | use for debugging, logs, and tests, not core business decisions |

## 5. Compatibility And Verification

| Item | Release baseline |
| --- | --- |
| Kotlin | `1.9.25` |
| Android Gradle Plugin | `8.6.1` |
| Gradle | `8.7` |
| Gradle runtime JDK | `17` |
| Published bytecode | Java 8 |
| Android SDK | `minSdk=21`, `compileSdk=35`, `targetSdk=35` |

| Verification item | Recommended command | Pass criteria |
| --- | --- | --- |
| API compatibility | `./gradlew apiCheck --warning-mode=all` | public API baseline has no unintended drift |
| Unit tests and lint | `./gradlew :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease --warning-mode=all` | core, Android wrappers, and release lint pass |
| Sample build | `./gradlew :app:assembleDebug :app:assembleRelease --warning-mode=all` | debug and release APKs both build |
| Local artifacts | `./gradlew verifyMavenLocalArtifacts --warning-mode=all` | jar / aar / POM / module metadata / license / developer / sources / javadoc structure is complete |
| Core consumer | `./gradlew verifyMavenLocalCoreConsumer --warning-mode=all` | the `flowbus-core` coordinate compiles in a real Kotlin/JVM consumer |
| Android consumer | `./gradlew verifyMavenLocalConsumer --warning-mode=all` | the `flowbus` coordinate resolves in a real Android consumer and passes release shrink |
| Publication task graph | `./gradlew releaseToMavenCentral --dry-run --warning-mode=all` | remote publication task graph resolves without uploading |
| Device regression | `./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all` | run when a device or CI emulator is available; inspect device state and Activity startup first on failure |

## 6. Upgrade Risks And Handling

| Risk | Trigger | Suggested handling | Rollback |
| --- | --- | --- | --- |
| Treating send result as business success | reading `accepted = true` as “subscriber handled it” | document and log it as “bus-layer acceptance” | go back to `tryPostEvent(...)` or add a business ACK |
| Treating overflow policy as a reliable queue | using `DROP_OLDEST` / `DROP_LATEST` for critical paths | use `emit*` or a dedicated queue for important events | return to `SUSPEND` or business-owned queueing |
| Turning sticky replay into long-lived state | replacing a state holder with sticky events | keep long-lived state in `StateFlow` | remove sticky usage and restore the state holder |
| Depending on the runtime sticky Flow implementation | external code casts `stickyFlow(...)` to `MutableSharedFlow` or reads `replayCache` / `subscriptionCount` directly | collect it only through the public `Flow` contract; use `consumeStickyLatest(...)` to read-and-clear the latest value, and `inspect()` for diagnostic counts | remove the cast and use public APIs or diagnostic snapshots |
| Reading one-time consumption as global mutual exclusion | concurrent threads read and write the same sticky business result | use it only for current-store replay read-and-clear; preventing later writes belongs to business locking or a state machine | use a business state machine, database transaction, or dedicated channel |
| Configuring Android too late | calling configure after the first `FlowEventBus` is created | configure in `Application.onCreate()` | restore default config or initialize earlier |

## 7. Release Deliverables

| Deliverable | Location | Purpose |
| --- | --- | --- |
| Changelog | [`CHANGELOG.md`](../../CHANGELOG.md) | short version-history record |
| Release notes | [`docs/release-notes-1.0.6.md`](../release-notes-1.0.6.md) | capability summary and upgrade evaluation |
| Root README | [`README.md`](../../README.md) | first-time setup and module selection |
| Android README | [`library-android/README.md`](../../library-android/README.md) | Android setup and lifecycle APIs |
| core README | [`flowbus-core/README.md`](../../flowbus-core/README.md) | pure Kotlin usage and core semantics |
| Release checklist | [`docs/release-checklist.md`](../release-checklist.md) | pre-release verification |
| Release scripts | [`gradle/release-publishing.gradle.kts`](../../gradle/release-publishing.gradle.kts), [`gradle/release-verification.gradle.kts`](../../gradle/release-verification.gradle.kts) | Maven publication and pre-release verification |
