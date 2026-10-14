# 1.1.0 scope and compatibility assessment

[中文](../next-version-plan.md)

1.1.0 was published to Maven Central on 2026-10-12. This document records scope, evidence, and conditions for future extensions. See the [release checklist](release-checklist.md) for gates and results, and module READMEs for usage.

See the [next-version implementation proposal (suggested 1.2.0, Chinese)](../implementation-plan-1.2.0.md) for proposed scope and implementation steps. It is not implemented yet; this document retains the 1.1.0 scope and verification history.

## Release scope

| Area | Decision |
| --- | --- |
| API 21–23 compatibility | Replace core ConcurrentHashMap.compute* calls while preserving atomic updates and old-store cleanup isolation; no additional consumer desugaring configuration |
| Scope closure | Make closeSuspending waits cancellable while preserving 1.0.7 state semantics; permanent close requests survive cancellation, timeout, and interruption |
| Core suspending collection | collectSuspending / collectStickySuspending and channel helpers; sequential callbacks using source error policy and dispatcher |
| Android integration | onEventSuspending, collectEventSuspending, collectEventFrom / collectEventFromSuspending; callers retain source ownership |
| Delivery contract | Normal events without subscribers are not retained; send completion is not business completion; sticky replay and live broadcast are managed independently |
| Extension examples | Compose, Navigation, and actual NavHost remain independent consumer fixtures, with no new published module or production dependency |

## Compatibility assessment

Base settings remain Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2, AGP 8.6.1, Gradle 8.7, build JDK 17, Java 8 bytecode, and minSdk 21 / compileSdk 35 / targetSdk 35. Library build settings do not establish minimum requirements for every consumer.

These record independent local checks of the candidate implementation, not certification of arbitrary combinations. Complete final-commit CI and published artifacts have passed checklist verification; see the release gates below.

| Tested combination | Evidence and limits |
| --- | --- |
| Consumer Kotlin 1.8.22 / 1.9.22 / 1.9.25 / 2.0.21 / 2.1.0, AGP 8.6.1 / Gradle 8.7 | Core/Android compilation, actual JDK 8 core execution, and Android release R8 passed; resolved stdlib is at least 1.9.25, without lowering published dependency versions |
| Kotlin 1.8.22 + compileSdk 33, AGP 8.6.1 | Android compilation, AAR metadata, and R8 passed; highest minCompileSdk in the complete graph is 33 |
| AGP 7.2.2 / Gradle 7.4.2 / Kotlin 1.8.22 / JDK 17 experiment | Compilation/R8 succeeded, but lint has Kotlin metadata parsing diagnostics; full old toolchain is not certified |
| Published 1.0.7 core binary consumer | Unchanged Kotlin/Java bytecode passed actual JDK 8 execution with original and candidate; 631 JVM descriptors across 46 stable classes retained |
| Published 1.0.7 Android AAR symbols | All 88 public-snapshot members retained; raw comparison records one non-PublishedApi Kotlin internal removal, documented in the Android consumer |
| API 24 device | Library instrumentation: 3 tests; sample release instrumentation: 17 tests; ordinary Maven consumer: 4 stages passed |
| Compose 1.5.4 / Lifecycle 2.6.2 | Compilation/R8, ten API 24 interaction stages, nine effect stages, and eight error/cancellation stages passed |
| Compose 1.7.8 / Lifecycle 2.8.3 | Equivalent independent checks passed; newer consumer resolution does not change base coordinates |
| Navigation 2.4.1 / Compose 1.5.4 and 2.8.3 / 1.7.8 | Both API 24 combinations passed seven custom-Navigator stages and eight actual NavHost stages; newer NavHost covers default animation owner thresholds, both cover Activity configuration recreation |

Binary checks are not full ABI certification. Android symbol checks do not establish old-consumer bytecode execution on devices. NavHost recreation uses a retained ViewModel in the same process and proves neither process-death recovery nor sticky persistence.

## Release gates and entry points

API 21 remains the minimum target. The [final main CI](https://github.com/logan0817/FlowBus/actions/runs/38207876160) passed API 21 library instrumentation, minified Maven consumer device checks, and API 35 checks. No API 21 image is available locally; the minimum-system evidence comes from remote devices, not API 24 or static D8 checks. See the [release checklist](release-checklist.md) for published Central artifacts, signatures, and independent consumer results.

- [Release checklist](release-checklist.md): tests, API, lint, R8, metadata, minimum API, and publication task graph.
- [Core binary consumer](../../compatibility/core-binary-consumer/README_EN.md): original compilation, candidate runtime replacement, and JVM symbols.
- [Android consumer](../../compatibility/android-consumer/README.md): signing, four device stages, and AAR symbols.
- [Compose](../../compatibility/compose-consumer/README_EN.md), [Navigation](../../compatibility/navigation-consumer/README_EN.md), and [actual NavHost](../../compatibility/navhost-consumer/README_EN.md): extension combinations and commands.

## Conditions for future extensions

1. This release passed API 21 and complete remote CI verification; future versions must repeat those gates against their final commits.
2. Full older toolchains and AGP 9.x remain uncertified. Choose future matrices from actual consumer needs.
3. A published Compose module needs a maintenance scope and compiler/runtime pairing policy; retain examples for now.
4. Process-death recovery, deep links, arbitrary animations, and full NavHost UI behavior after errors remain outside verified coverage. Persistence belongs to business state or durable task mechanisms.
