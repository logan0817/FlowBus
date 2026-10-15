# FlowBus 1.2.0 release notes

[中文](../release-notes-1.2.0.md)

1.2.0 was published to Maven Central on 2026-10-15. Release configuration: VERSION_NAME=1.2.0, APP_VERSION_NAME=1.2.0, APP_VERSION_CODE=9. Verification below identifies tested snapshots and does not establish acceptance of later working-tree changes.

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

## Published-artifact acceptance (2026-10-15)

- [Main CI run 28](https://github.com/logan0817/FlowBus/actions/runs/38679806444) passed for merge commit `5e35694fc52e6ab10f10a54387147cfe2ccf17c5`, covering the unit, API/lint, consumer, compatibility, API 21/API 35 device, and publication-graph gates above. Run 26 remains evidence for the candidate stage.
- Central deployment `498daa48-c148-4b71-91bd-acf0b2111dc9`, named `io.github.logan0817-FlowBus-1.2.0`, reached `PUBLISHED`. Both coordinates were downloaded from public Maven Central.
- All 100 public files match the accepted signed bundle byte for byte. All 10 GPG signatures and 80 MD5/SHA-1/SHA-256/SHA-512 checksums passed. The primary signing-key fingerprint is `8BB25F160F45FD8919DC985A152B32281C3E9240`.
- An independent core project uses only Maven Central for dependencies, verifies the loaded JAR version and SHA-256, and runs Kotlin/Java consumers on Java 8. Normal/sticky delivery, consumption/cleanup, scope ownership, suspend callbacks, and new channel configuration passed.
- An independent Android project resolves both 1.2.0 coordinates from public repositories, checks their SHA-256 values, and passes `:app:assembleRelease` with R8. This is published-artifact build acceptance; device evidence comes from main CI above, without claiming another device run here.

| Published artifact | SHA-256 |
| --- | --- |
| [flowbus-core-1.2.0.jar](https://repo.maven.apache.org/maven2/io/github/logan0817/flowbus-core/1.2.0/flowbus-core-1.2.0.jar) | `525c10846d00e7a41f932ac0ce45dba47b7b8b1da58bc68266e33df4a4b974f2` |
| [flowbus-1.2.0.aar](https://repo.maven.apache.org/maven2/io/github/logan0817/flowbus/1.2.0/flowbus-1.2.0.aar) | `c10b2978c3ebdf69ea97ed9112c212fa65cf9b5744da0ff0b5b8823c8c1819bd` |

The published AAR matches the main-CI AAR hash; the published core JAR differs from the main-CI archive hash. All 36 Kotlin files in the two sources JARs match merged main byte for byte. The actual signed core JAR separately passed Java 8 consumers, unchanged-bytecode replacement runs for original 1.0.7/1.1.0 consumers, and 631/681 descriptor checks. Public downloads match that tested JAR. The main-CI core archive hash is not used as acceptance evidence for the published JAR.

## Rollback

Consumers without new APIs can return to released 1.1.0. Consumers using configureChannel must revert those calls and restore bus-wide policies or separate instances. Published coordinates must not be overwritten. Commit, push, remote CI writes, upload, and Portal release require their respective authorization.
