# FlowBus 1.0.7 Release Notes

[中文](../release-notes-1.0.7.md)

## 1. Release Summary

`1.0.7` is a pre-release reliability update. It closes a remaining `FlowBusScope` store-cleanup race and aligns README, release checklist, release notes, and version metadata with the current release.

Upgrade if you: 1. use `openScope(...)` for Session / Repository / Worker / Task lifecycles. 2. close a scope and reopen the same name immediately. 3. rely on Maven local artifacts, real consumers, and release dry-run before publishing. 4. want clearer core README guidance for API selection.

## 2. Changes

| Type | Change | Scope | Notes |
| --- | --- | --- | --- |
| Scope close race | `removeScope(scopeName)` no longer clears the old store early when a `FlowBusScope` is already closing | `flowbus-core` | in-flight sends / flow lookups keep the original store until the close action finishes |
| Same-name reopen | added a regression test for `removeScope()` racing with close | `FlowBusScopeCloseTest` | covers the branch where `close()` already invalidated the handle but old operations are still running |
| Close result docs | clarified `tryClose(timeoutMillis)` timeout semantics | `FlowBusScope`, `FlowBusCloseResult`, core README | separates “this tryClose timed out and can be retried” from “another close already invalidated the handle” |
| Documentation structure | added terminology, functional explanations, close troubleshooting, and removed duplicate shortest-usage blocks | `flowbus-core/README.md`, `flowbus-core/README_EN.md` | tables now explain what each API does, when to use it, and its boundary |
| Release version | aligned version, install coordinates, release checklist, and release notes to `1.0.7` | root README, module READMEs, Gradle config, docs | avoids publishing preparation that still points to 1.0.6 |
| Release gate | added a release-readiness test for 1.0.7 docs and version consistency | `FlowBusReleaseReadinessTest` | prevents future missed version, release-note, or install-coordinate updates |

## 3. Close Semantics

| API | What it does | Best for | Boundary |
| --- | --- | --- | --- |
| `close()` | invalidates the current `FlowBusScope` handle immediately, then cleans the store after already-started operations finish | lifecycle callbacks and UI-layer handle release | does not wait for cleanup; old `Flow` references are not actively cancelled |
| `closeSuspending()` | waits for close and cleanup on a background dispatcher | coroutine code that must confirm cleanup | keeps waiting if a suspending send is blocked by a collector |
| `tryClose(timeoutMillis)` | waits up to the timeout and returns `FlowBusCloseResult` | tests, shutdown flows, explicit timeout handling | if another `close()` already invalidated the handle, timeout does not restore that handle |
| `removeScope(scopeName)` | removes the current scope store; when a same-name handle exists, it goes through the close path first | external cleanup of named scopes | no longer bypasses the close state machine to clear an in-flight store early |

## 4. Verification Checklist

| Check | Command | Pass criteria |
| --- | --- | --- |
| Scope close regression | `./gradlew :flowbus-core:test --tests com.logan.flowbus.core.FlowBusScopeCloseTest --warning-mode=all` | close, closeSuspending, tryClose, same-name reopen, and racing removeScope branches pass |
| Release docs gate | `./gradlew :library-android:testDebugUnitTest --tests com.logan.flowbus.FlowBusReleaseReadinessTest --warning-mode=all` | versions, release notes, README files, and release checklist match |
| API compatibility | `./gradlew apiCheck --warning-mode=all` | public API has no unintended drift |
| Local publication artifacts | `./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalConsumer --warning-mode=all` | jar / aar / POM / module metadata / license / developer / real consumer checks pass |
| Android device regression | `./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all` | run when a device or CI emulator is available; app tests use release R8/minify |
| Publication task graph | `./gradlew releaseToMavenCentral --dry-run --warning-mode=all` | remote publication graph resolves without uploading |

## 5. Upgrade Risks

| Risk | Trigger | Suggested handling |
| --- | --- | --- |
| Treating `accepted = true` as business success | assuming subscribers already handled an event from the send result alone | document and log it as bus-layer acceptance; add business ACK or state updates when completion matters |
| Treating `DROP_OLDEST` / `DROP_LATEST` as a reliable queue | using overflow policies for critical paths | use `emit*`, a business queue, or a state machine for important events |
| Turning sticky replay into long-lived state | replacing a page state holder with sticky events | keep long-lived state in `StateFlow`, a database, or a business state machine |
| Misreading `tryClose` timeout | calling `tryClose(timeoutMillis)` while another `close()` is already in progress | check `scope.isClosed` and `FlowBusCloseOutcome`; timeout does not mean the old handle became usable again |
| Depending on runtime sticky Flow internals | external code casts `stickyFlow(...)` or reads runtime `replayCache` | use `consumeStickyLatest(...)` to read-and-clear the latest value, and `inspect()` for diagnostic counts |

## 6. Deliverables

| Deliverable | Location | Purpose |
| --- | --- | --- |
| Changelog | [`CHANGELOG.md`](../../CHANGELOG.md) | short version history |
| Release notes | [`docs/release-notes-1.0.7.md`](../release-notes-1.0.7.md) | release capability summary and upgrade evaluation |
| Release checklist | [`docs/release-checklist.md`](../release-checklist.md) | pre-release verification |
| core README | [`flowbus-core/README.md`](../../flowbus-core/README.md) | pure Kotlin, scope lifecycle, and lower-level semantics |
| Android README | [`library-android/README.md`](../../library-android/README.md) | Android integration and lifecycle APIs |
