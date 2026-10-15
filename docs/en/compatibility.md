# FlowBus compatibility

[中文](../compatibility.md) · [Documentation](README.md)

FlowBus publishes a Kotlin/JVM core and an Android adapter. There are no Kotlin Multiplatform, cross-process, Compose, or Navigation artifacts. The Android coordinate depends transitively on the matching core version.

## Requirements and defaults

Android minSdk is 21. Both coordinates target Java 8 bytecode; the repository build requires JDK 17 and the Gradle wrapper. Build versions describe the library build, not the minimum version of every consumer toolchain. Gradle may raise resolved transitive versions; inspect the consumer dependency graph when diagnosing compatibility.

## Historical 1.1.0 matrix

Base settings remain Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2, AGP 8.6.1, Gradle 8.7, build JDK 17, Java 8 bytecode, and minSdk 21 / compileSdk 35 / targetSdk 35. Library build settings do not establish minimum requirements for every consumer.

The table below records historical 1.1.0 checks. It does not certify arbitrary version combinations or the current 1.2.0 working tree. Evidence for 1.2.0 is listed separately below.

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

<a id="120-candidate-checks"></a>

## 1.2.0 checks

1.2.0 retains base dependencies, Java 8 bytecode, minSdk 21, and existing public constructor/copy/component descriptors. New channel configuration is additive; code using the new APIs must remove those calls before rolling back to 1.1.0.

| Area | Recorded verification evidence |
| --- | --- |
| Core binary consumers | Unchanged 1.0.7 and 1.1.0 Kotlin/Java bytecode runs on Java 8; 631/681 JVM descriptors retained |
| Android binary consumers | Fixed original AAR symbol checks and original 1.1.0 suspend/instance consumer classes run with candidate runtime; minified API 21 execution |
| Consumer toolchains | Kotlin 1.8.22/1.9.22/2.0.21/2.1.0 with AGP 8.6.1/Gradle 8.7; compileSdk 33; release R8 |
| Devices | API 21 library and minified Maven consumer; API 35 library/sample instrumentation |
| Optional integrations | Default Compose/Navigation and newer Navigation 2.8.3 with Compose 1.7.8/NavHost consumer builds |

Tested commits and CI runs are recorded in the [1.2.0 notes](release-notes-1.2.0.md). Local documentation changes after those commits do not inherit their CI pass. Repeat relevant checks and verify current-head CI before merging or publishing.

## Limits and reproduction

Binary descriptors and selected consumer runs are not complete ABI certification. Android static symbols do not prove every legacy path executes on devices. NavHost recreation uses a retained ViewModel in one process and establishes neither process-death recovery nor sticky persistence. Old complete toolchains and AGP 9.x remain uncertified.

- [Release checklist](release-checklist.md): API, lint, R8, metadata, API 21/API 35, publication task graph.
- [Core binary consumer](../../compatibility/core-binary-consumer/README_EN.md): pinned original compilation and runtime replacement.
- [Android consumer](../../compatibility/android-consumer/README.md) (Chinese): test signing, six device groups, original AAR symbols.
- [Compose](../../compatibility/compose-consumer/README_EN.md), [Navigation](../../compatibility/navigation-consumer/README_EN.md), [NavHost](../../compatibility/navhost-consumer/README_EN.md): independent consumers and commands.
- [JVM performance](../../benchmarks/jvm/results/current/analysis.md) (Chinese): measured overhead and limits; no Android ART or cross-framework ranking.
