# FlowBus 1.2.0 release notes (candidate)

[中文](../release-notes-1.2.0.md)

1.2.0 is an unpublished candidate. 1.1.0 remains the stable installation. Target configuration: VERSION_NAME=1.2.0, APP_VERSION_NAME=1.2.0, APP_VERSION_CODE=9. Verification below identifies tested snapshots and does not establish acceptance of later working-tree changes.

## Changes

- FlowBusChannelConfig inherits bus defaults through nullable fields and configures normal/sticky capacities and overflow independently.
- configureChannel is available on FlowBus, ScopedFlowBus, FlowBusScope, and FlowEventBus; Android configureEventChannel uses the existing global or explicit-owner source.
- First configuration/use atomically binds type and effective policies. Equal effective configuration is idempotent; type/policy conflicts throw IllegalArgumentException/IllegalStateException without partial registration.
- Removing individual flows retains bindings. Complete scope cleanup releases overrides; a same-name new store starts from defaults. Owned-scope configuration participates in in-flight closure protection.
- PostResult.overflowPolicy reports the actual stream policy; inspect().config still describes bus defaults.
- The channel sample uses one Activity owner with different Toast/Navigation policies and suspending Navigation sends.
- Documentation now has Chinese/English indexes, a dedicated compatibility guide, shared candidate-build instructions, functional module navigation, and aligned contribution/security/support policies. Legacy document links remain available.

Default capacity 64, DROP_OLDEST, sticky replay 1, Rethrow, independent normal/sticky delivery, old Flow references, scope closure, and existing public data-class JVM descriptors remain. minSdk 21, Java 8 bytecode, and base dependency/toolchain versions do not change.

See [core usage and migration](../../flowbus-core/README_EN.md#channel-configuration), [Android owners](../../library-android/README_EN.md#android-owner-configuration), and [JVM benchmarks](../../benchmarks/jvm/README.md). JVM measurements do not establish ART performance.

## Verification evidence

The tested candidate snapshot is recorded below:

| Snapshot | Evidence |
| --- | --- |
| `56812f8c7f24520480e03a44b6816fdc950c9e59` | [CI run 26](https://github.com/logan0817/FlowBus/actions/runs/38601076450), all steps successful; includes API 21/API 35 devices, artifact consumers, binary compatibility, and publication task graph |
| Artifact for run 26 | `flowbus-verification-d6c7bae559732e3810a6303a8b58cc20373e58a2`, digest `sha256:8a4fcbb1d835432b5a9fe5d67c2d1aaed0c1457c8ee15e514be309dcc635e366` |

The artifact name uses the PR synthetic merge SHA; the run's head SHA identifies the branch snapshot. Reports expire after 14 days. Later documentation cleanup requires relevant local verification and current-head CI; run 26 does not cover later changes.

| Area | Snapshot result |
| --- | --- |
| Core/Android/app JVM tests | 164/65/2 passing, including configuration, delivery, concurrency, lifecycle, and release-readiness checks |
| API/lint/build | Additive API snapshot; apiCheck, release lint, debug/release sample builds |
| Maven consumers | Java 8 core; Android/Compose release R8; POM and module metadata |
| Legacy binaries | Original 1.0.7/1.1.0 core bytecode runs unchanged; 631/681 descriptors retained; original 1.1.0 Android suspend/instance classes run with candidate runtime |
| Toolchains | Kotlin 1.8.22/1.9.22/2.0.21/2.1.0 and compileSdk 33; optional Navigation/NavHost consumer builds |
| Devices | API 21 library and minified Maven consumer; API 35 library/sample instrumentation including channel-page recreation |
| JVM benchmark | Three formal groups, 58 scenario/mode measurements; 3 forks, 5 × 1s warmup/measurement; CI smoke checks runnability |

Published 1.0.7 Android public descriptors remain. Raw comparison retains the pre-existing internal `createFlowBusConfig$library_android_release` removal introduced in 1.1.0. Static symbols and selected consumer runs are not complete ABI certification. See the [compatibility guide](compatibility.md).

## Performance

See the [complete report](../../benchmarks/jvm/results/current/report.md) and [analysis](../../benchmarks/jvm/results/current/analysis.md). Candidate no-subscriber normal-send throughput is 98.7% of 1.1.0, sticky publishing 99.8%, with overlapping uncertainty intervals. This run does not establish an approximately 10% default steady-send regression beyond measurement uncertainty. First registration throughput is 85.5% of baseline and allocates 1000 instead of 888 B/op; this cost is disclosed. JFR sampling locates construction/registration work but does not precisely assign the extra bytes to individual configuration classes. Raw SharedFlow has lower overhead in equivalent workloads. No ART or cross-framework performance advantage is claimed.

Reproduce checks through the [release checklist](release-checklist.md). Device tasks are `:library-android:connectedDebugAndroidTest` and minified sample `:app:connectedReleaseAndroidTest`. Compilation, static symbols, and mocked drivers do not replace device execution.

## Rollback

Consumers without new APIs can return to released 1.1.0. Consumers using configureChannel must revert those calls and restore bus-wide policies or separate instances. Published coordinates must not be overwritten. Commit, push, remote CI writes, upload, and Portal release require their respective authorization.
