# FlowBus 1.2.0 release notes (candidate)

[中文](../release-notes-1.2.0.md)

This is a committed and pushed implementation candidate, not uploaded or published to Maven Central. 1.1.0 remains the released installation. Configuration: VERSION_NAME=1.2.0, APP_VERSION_NAME=1.2.0, APP_VERSION_CODE=9. Historical device and remote CI evidence does not establish candidate acceptance.

## Changes

- FlowBusChannelConfig inherits bus defaults through nullable fields and configures normal/sticky capacities and overflow independently.
- configureChannel is available on FlowBus, ScopedFlowBus, FlowBusScope, and FlowEventBus; Android configureEventChannel uses the existing global or explicit-owner source.
- First configuration/use atomically binds type and effective policies. Equal effective configuration is idempotent; type/policy conflicts throw IllegalArgumentException/IllegalStateException without partial registration.
- Removing individual flows retains bindings. Complete scope cleanup releases overrides; a same-name new store starts from defaults. Owned-scope configuration participates in in-flight closure protection.
- PostResult.overflowPolicy reports the actual stream policy; inspect().config still describes bus defaults.
- The channel sample uses one Activity owner with different Toast/Navigation policies and suspending Navigation sends.

Default capacity 64, DROP_OLDEST, sticky replay 1, Rethrow, independent normal/sticky delivery, old Flow references, scope closure, and existing public data-class JVM descriptors remain. minSdk 21, Java 8 bytecode, and base dependency/toolchain versions do not change.

See [core usage and migration](../../flowbus-core/README_EN.md#120-candidate-channel-configuration), [Android owners](../../library-android/README_EN.md#android-owner-configuration), and [JVM benchmarks](../../benchmarks/jvm/README.md). JVM measurements do not establish ART performance.

## Verification status

Final API 21/API 35, UI, and complete CI acceptance is tracked in [PR #7 checks](https://github.com/logan0817/FlowBus/pull/7/checks) and its original reports. Verify the current PR head and every required gate; queued, running, or older successful runs are insufficient. The candidate remains a draft and is not published.

The core/Android/app JVM suites passed 164/65/2 tests, including configuration, contract, concurrency, and lifecycle coverage. Required documentation inputs are covered by the final checks. API dumps contain additions only; apiCheck, library/app release lint, and sample debug/release builds passed. The actual 1.2.0 Maven core consumer passed on Java 8, and artifact metadata plus the minified Android consumer compiled successfully including the new APIs.

Original 1.0.7/1.1.0 Kotlin/Java core consumers ran unchanged against the candidate on Java 8; 631/681 JVM descriptors were retained. Published 1.1.0 Android AAR symbols matched its fixed public snapshot. Original 1.1.0 Android suspend/explicit-instance class files also ran against verified candidate core/Android class bytes on the JVM and compiled into the minified APK; device acceptance is evaluated using the final PR run.

Kotlin 1.8.22/1.9.22/2.0.21/2.1.0 and compileSdk 33 consumer gates passed, plus default and newer optional Compose/Navigation/NavHost R8 builds. Deployment-name and remote task-graph dry-run checks passed without remote operations. All three formal benchmark groups completed with matching sticky extra=0: 58 scenario/mode measurements, 3 forks, 5 × 1s warmup and measurement, GC profiler. Earlier mismatched measurements are excluded.

See the [complete report](../../benchmarks/jvm/results/current/report.md) and [analysis](../../benchmarks/jvm/results/current/analysis.md). Candidate no-subscriber normal-send throughput is 98.7% of 1.1.0, sticky publishing 99.8%, with overlapping uncertainty intervals. This run does not establish an approximately 10% default steady-send regression beyond measurement uncertainty. First registration throughput is 85.5% of baseline and allocates 1000 instead of 888 B/op; this cost is disclosed. JFR sampling locates construction/registration work but does not precisely assign the extra bytes to individual configuration classes. Raw SharedFlow has lower overhead in equivalent workloads. No ART or cross-framework performance advantage is claimed.

Published 1.0.7 Android public descriptors remain. Raw comparison records one pre-existing internal createFlowBusConfig$library_android_release removal from the released 1.1.0 change. Static symbol checks are not complete ABI certification.

Final API 21/API 35, UI, and remote CI acceptance must be confirmed against PR #7 current-head checks and original reports; no local API 21/API 35 device is available. See the [checklist](release-checklist.md) for all commands. Device tasks are `:library-android:connectedDebugAndroidTest` and minified sample `:app:connectedReleaseAndroidTest`. Build, static-symbol, and mocked-driver success cannot replace device execution.

Device entry points now include channel-page Activity recreation coverage and API 35 actual-device/boot assertions with an identity report. Three mocked-ADB driver regressions and shell syntax checks passed. `:app:assembleReleaseAndroidTest` (including R8) and `:library-android:assembleDebugAndroidTest` passed, together with relevant release lint, 231 JVM tests, and API checks. Compiling test APKs does not replace device execution, whose evidence must match the final PR head.

## Rollback

Consumers without new APIs can return to released 1.1.0. Consumers using configureChannel must revert those calls and restore bus-wide policies or separate instances. Published coordinates must not be overwritten. Commit, push, remote CI writes, upload, and Portal release require their respective authorization.
