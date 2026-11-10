# FlowBus 1.3.0 release candidate

[中文](../release-notes-1.3.0.md)

The current release candidate version is **1.3.0**, not a published release. The public installation version remains 1.2.0. These notes describe the local candidate; historical 1.2.0 results do not validate current inputs.

## Changes

- FlowBus, ScopedFlowBus and FlowBusScope add flow/stickyFlow and suspending collection overloads with onSubscribed. Each actual collection registration invokes its own notification. This does not acknowledge replay processing or business completion and does not change buffering, overflow or cancellation. Existing methods and default bridges remain.
- Android explicit-source collectEventFromSuspending adds the notification overload, preserving source error policy and repeatOnLifecycle. Each new active collection notifies again. A hook failure propagates as collection setup failure rather than an event callback error.
- FlowBusJava exposes Class/typed channels, global/owner normal and sticky publishing, results, lifecycle subscriptions and a Job-backed cancellation handle. Java lambdas do not return Unit. isCancelled reports a cancellation request; isCompleted confirms processing has ended. Defaults remain STARTED/Main.immediate; source owner and receiver lifecycle are separate.
- Optional flowbus-compose supplies FlowBusEffect with explicit source, key and owner. Callback updates do not resubscribe; source, key, owner, lifecycle threshold and sticky changes do. Composition exit or inactive lifecycle requests cancellation without closing the source. It adds no Material, Navigation or DI dependency; the base coordinates do not acquire Compose dependencies.
- inspectChannel(key) returns an independent FlowBusChannelSnapshot with effective normal/sticky configuration, flow presence and separate active counts. Unknown core queries create no store, binding or flow. Android static queries may obtain/create their source using existing rules but do not create an unknown channel. Existing snapshot data classes remain. Owned handles reject queries while closing or closed.

## Candidate usage and limits

Published installation examples remain on 1.2.0. Use the isolated local repository described in [contributing](contributing.md) for new APIs:

```kotlin
implementation("io.github.logan0817:flowbus:1.3.0")
// Only for Compose consumers:
implementation("io.github.logan0817:flowbus-compose:1.3.0")
```

Keep the hook short: complete a signal and let an independent producer send. Blocking the hook or waiting for the same zero-buffer SUSPEND subscription to consume can deadlock. Cancellation can follow the notification immediately; DROP policies can still discard values and zero-buffer tryPost can still reject. Replay processing follows notification. A one-shot first-registration signal does not represent every later lifecycle activation.

Normal/sticky streams remain separate. One subscription processes sequentially; no global callback order is promised across subscriptions. Durable or critical results still require business state and persistence. This version adds no ACK, reliable queue, inheritance dispatch, global priority, IPC or automatic zero-subscriber cleanup.

Compose uses the existing Kotlin 1.9.25 / compiler 1.5.15 / runtime 1.5.4 combination. Base Coroutines 1.8.1, Lifecycle 2.6.2, minSdk 21 and Java 8 bytecode remain. Record resolved consumer dependency graphs rather than assuming declared versions are final.

## Verification boundaries

Existing API, JVM, R8, Maven, Java 8, compiler matrix, API 21/35 and publication dry-run gates remain. New checks add published 1.2.0 core consumer bytecode reuse and Android JVM symbols, actual .java consumer calls, and a Compose consumer resolving the optional candidate artifact.

Tests use controlled scheduling and explicit signals for registration, cancellation, stream/source isolation and replay/live boundaries. The Android consumer adds Java/Kotlin interoperability. Compose adds registration counts and callback updates without resubscription, retaining error/cancellation policy checks. Performance comparisons now target public 1.2.0; existing 1.2.0 reports remain historical.

Local results are reported with the implementation delivery. API 36 does not replace API 21/35 evidence; an unrun cloud matrix is not a successful release gate.

## Final-version candidate preparation (2026-11-10)

Current sources use `1.3.0`, the sample uses `versionName=1.3.0` / `versionCode=11`, and test APKs use version `2 / 1.3.0`. All three coordinates are prepared only in an isolated Maven repository. No release signing, upload or public publication has occurred; public installation examples retain 1.2.0.

The preceding development version merged through [PR #12](https://github.com/logan0817/FlowBus/pull/12) as `311c30f249631371877e25c0cb714a5ccc4917a8`. Its [main CI](https://github.com/logan0817/FlowBus/actions/runs/42771676046) passed all nine checks for `1.3.0-SNAPSHOT`. The local performance and device records below retain their original version identity; they do not establish new `1.3.0` artifact hashes or current device results.

Local validation passed 257 JVM tests, API checks for the three libraries, Release Lint for Android/Compose/the sample app, metadata checks for all three coordinates, core consumer execution on Java 8, and real Maven Release R8 verification for Android and Compose. Compiler and API 21/35 evidence must come from this PR and the post-merge CI.

This release-preparation PR must regenerate the `1.3.0` candidate and pass API, JVM, Lint, metadata, Java 8, real Maven/R8, old bytecode/symbol, compiler matrix and API 21/35 gates. Verify final main CI after merge. Release signing, upload, tags and GitHub Release require separate authorization; public installation examples change only after successful publication.

## Historical development-candidate verification (2026-10-30)

That development candidate was verified from an uncommitted workspace; HEAD `26df17a` alone does not identify the new sources. `build/verification-130/source-identity.json` records 50 source/build inputs. The sealed manifest SHA-256 is `529d3e6ae52262d6f105bd492c38e7cdaa3fd79df3b1ab03223968cf2445af02`; historical files remain in the isolated repository. Historical primary artifact SHA-256 values for that SNAPSHOT are:

| Artifact | SHA-256 |
| --- | --- |
| core JAR | `1d7b645de2c41bd2e79c716d161373dd262abb1d079f78aecbbf57b793bf32e9` |
| Android AAR | `729ad9a00caf20d50b659fa355f91288e247a3f2caab989730b69d6e3e56a564` |
| Compose AAR | `5a3cc9c7284fa54fb8ed78d543a5b433eff2b9b32016545ca5080993fcf06e2f` |

| Scope | Verified result |
| --- | --- |
| JVM tests | core 181, Android 74, sample 2: 257 tests with zero failures/errors/skips |
| API, Lint and artifacts | Three-module API checks; Android/Compose/sample release Lint; all three coordinates including sources/javadoc, POM/module metadata and Java 8 bytecode |
| Maven core consumer | Actual Kotlin/Java compilation and Java 8 execution against the candidate |
| Old core bytecode | Identical consumer classes compiled against original 1.0.7 / 1.1.0 / 1.2.0 run on both original and candidate Java 8 runtimes |
| JVM symbols | core 631/681/723 descriptors and Android 179/213/216 public descriptors retained; the Android 1.0.7 non-public internal difference remains separately reported, not full ABI certification |
| Kotlin consumer matrix | 1.8.22 / 1.9.22 / 2.0.21 / 2.1.0 each pass actual core Java 8 execution and Android release R8 against the same sealed candidate |
| compileSdk 33 | Kotlin 1.8.22 consumers pass core Java 8 execution and Android release R8 |
| API 36 Maven/R8 device | Android 7 stages including actual Java, Compose 11 effect stages and 8 policy stages pass with matching current logs, same-package UI, phases and run IDs |
| Newer Compose / NavHost | Compose 1.7.8 / Navigation 2.8.3 compilation, release R8 and API 36 effects 11, policies 8 and NavHost 8 stages pass with matching logs, same-package UI and current run IDs |
| Release entry | `verifyCentralDeploymentName` and `releaseToMavenCentral --dry-run` pass; all three coordinates enter the task graph without executing clean, signing or upload |
| Instrumentation | Library and sample test APKs build; API 36 rejects library-test installation with `INSTALL_FAILED_USER_RESTRICTED`, executing zero tests; sample tests do not proceed |
| Directed performance | Five groups plus reversed stable-send/registration comparisons produce nine formal 3-fork JMH groups; three workload self-checks pass and artifact/source/report identities match. See the [measured report](../../benchmarks/jvm/results/1.3.0/report.md) for results and limits |

Device evidence is under local `build/verification-130/`: Android run `7878952c-6f92-47ec-a91b-ccb1d5f216f6`, final default effects `6ea67ee7-6a66-4748-9803-44bca77db02c`, policies `e1c822d7-0c43-489f-92ac-4b0b80b6200e`. APKs use test signatures and the same candidate; no production release keys were used.

The newer-combination run IDs are `ce43a7ba-8b90-417a-a6d3-6649b53c072f`, `07d840b1-39a8-4e18-8378-cfad176547d5` and `f1b29285-1175-478d-b065-715d5676e5c7`; passing evidence remains under `build/verification-130/compose-newer-*-native/`. Test pages display their live result in an Activity-native TextView to accommodate the [Compose 1.7.8 accessibility delegate](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.7.8/ui-android-1.7.8-sources.jar), which filters UIAutomator-only queries. Drivers retain strict matching between current logs and foreground same-package UI.

Both complete `releaseRuntimeClasspath` reports are saved locally as `/tmp/flowbus-130-dependencies-default.log` and `/tmp/flowbus-130-dependencies.log`. The default resolves Compose runtime/ui 1.5.4, Lifecycle 2.6.2 and core-ktx 1.10.0; the newer combination resolves Compose runtime/ui 1.7.8, Navigation 2.8.3, Lifecycle 2.8.3 and core-ktx 1.12.0. Both use Kotlin stdlib 1.9.25. Base-library declarations still use Lifecycle 2.6.2; consumer resolution does not establish a new base-library minimum.

No local API 21/35 devices are available and cloud CI has not been triggered. Their extended entry points and driver tests do not establish minimum-device success.

The measurements do not support a speedup claim: reversed normal-send intervals overlap, and registration order reverses between runs under changing load. Repeat registration on an idle workstation before release. The complete hook-subscription workload adds roughly 250 B; this is not the isolated cost of the hook callback. The historical full benchmark was preserved, and this run establishes no ART performance result.

## Instrumentation follow-up (2026-11-10)

Tests were installed and rerun on a OnePlus 8 Pro (IN2020, Android 13 / API 33, build `IN2020_13.1.0.190(CN01)`). Dedicated test manifests add only test-package version `1 / 1.3.0-SNAPSHOT` and labels for installation confirmation and version checks. Production libraries and public APIs remain unchanged. The installation tool matched `com.oplus.appdetail 5.26.0`; all three APKs installed successfully with verified package versions.

`:library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all` succeeded. Current XML reports confirm **3 library tests** and **19 release sample tests**, **22 total**, with zero failures, errors or skips. Installation reports, APK hashes, test XML, build logs and source hashes are stored locally under `build/verification-130/instrumentation-2026-11-10/`. This verifies source instrumentation; it does not establish Maven-candidate consumer or API 21/35 execution. The 2026-10-30 installation failure retains its historical meaning.

## Pre-release review and registration follow-up (2026-11-10)

Final change review found no confirmed defects. All 446 previous core and 117 previous Android public members remain. Review covered subscription ordering, cancellation, channel inspection, Java/Compose callers, all three publication coordinates, candidate reuse and CI failure/skip paths. Static review does not replace device or cloud execution.

Four formal 3-fork registration groups ran in baseline → candidate → candidate → baseline order. Neither comparison reproduced lower candidate throughput; both versions allocate about 1000 B per operation. Baseline readings still vary, so this is neither a speedup claim nor controlled-environment certification. See the [performance follow-up](../../benchmarks/jvm/results/1.3.0/report.md#首次注册补验2026-11-10) for readings and identities. No local API 21/35 images, AVDs or SDK command-line tools are available; those device gates still require this candidate's cloud results.

## Rollback

Existing calls need no migration. To return to 1.2.0 after adopting new APIs, remove notification overloads and Java convenience calls; Compose consumers remove the new coordinate and restore their own earlier integration. Existing defaults and source ownership remain.
