# Release Checklist

[中文](../release-checklist.md)

Run this checklist before publishing `1.0.6`.

The goal is to verify that `flowbus-core` and `flowbus` are built from the FlowBus main repository, and that local verification plus the remote release task graph can be checked.

Release script locations:

| Logic | File |
| --- | --- |
| Publication entry points, Maven local cleanup, remote publication task graph, signing task trigger conditions | `gradle/release-publishing.gradle.kts` |
| Maven local artifact checks, POM / module metadata checks, real consumer build checks | `gradle/release-verification.gradle.kts` |

The root `build.gradle.kts` only keeps shared plugins, API validation configuration, project version configuration, and script entry points. Manual publication commands still run from the repository root; no submodule directory switch is needed.

## 1. Pre-Release Verification Command

```bash
./gradlew apiCheck --rerun-tasks --warning-mode=all
./gradlew :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease :app:assembleDebug :app:assembleRelease verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalConsumer --rerun-tasks --warning-mode=all
./gradlew releaseToMavenCentral --dry-run --warning-mode=all
git diff --check -- . ':(exclude)flowbus-core/api/*.api' ':(exclude)library-android/api/*.api'
```

Note: `*.api` files are generated API snapshots and are validated by `apiCheck`. The raw diff whitespace check excludes them to avoid false positives from the generator's trailing blank line format.

Expected result:
1. `flowbus-core` JVM unit tests pass.
2. `library-android` and `app` debug unit tests pass.
3. `library-android` and `app` release lint pass.
4. sample app debug and release APKs build.
5. `apiCheck` blocks public API drift unless the API baseline is intentionally updated.
6. `:app:assembleRelease` must run R8/minify so the sample app validates a consumer shrink scenario.
7. `verifyMavenLocalArtifacts` creates and checks local Maven artifacts, POM, module metadata, license / developer metadata, public dependency scopes, and AAR contents for both `flowbus-core` and `flowbus`.
8. `verifyMavenLocalCoreConsumer` generates a real Kotlin/JVM consumer from Maven local coordinates and directly compiles against the `flowbus-core` coordinate.
9. `verifyMavenLocalConsumer` generates a real Android consumer from Maven local coordinates and passes release R8/minify.
10. `releaseToMavenCentral --dry-run` works as a smoke check for the unified remote publication task graph and confirms `clean` is part of the graph.
11. When a build fails, inspect the actual failed task first; record AGP / Gradle compatibility warnings separately instead of treating them as the release failure root cause.

If this change intentionally modifies public API, run:

```bash
./gradlew apiDump
```

Then manually review these 2 files:

1. `flowbus-core/api/flowbus-core.api`
2. `library-android/api/library-android.api`

## 2. Device Test Command

Run the following commands after a local emulator or device is connected. The three animation scale reads must return `0` before connected tests run.

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings get global window_animation_scale
adb shell settings get global transition_animation_scale
adb shell settings get global animator_duration_scale
./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --rerun-tasks --warning-mode=all
```

Expected result:
1. `library-android` connected tests pass with animations disabled.
2. `app` connected release tests cover global, scope, sticky, channel, non-UI, and login sample flows after R8/minify.
3. `testBuildType = "release"` means the app instrumentation task only generates the release test variant, so the app keeps only the release device-test entry.
4. If device tests fail, inspect emulator readiness, AndroidX Test Runner, and the three animation scale settings before checking business assertions.

## 3. Artifact Checks

Local Maven artifacts must satisfy:
1. `flowbus-core-1.0.6.jar` and `flowbus-1.0.6.aar` exist.
2. Both modules generate `-sources.jar`, `-javadoc.jar`, and `.module`.
3. The `flowbus` main artifact is an AAR and does not fall back to a plain Jar.
4. The `flowbus` POM depends on `flowbus-core` with the same release version.
5. The `flowbus-core` POM and module metadata expose `kotlinx-coroutines-core` as a compile/API dependency.
6. The `flowbus` POM and module metadata expose `flowbus-core`, `androidx.lifecycle:lifecycle-runtime-ktx`, and `androidx.lifecycle:lifecycle-viewmodel-ktx` as compile/API dependencies.
7. The `flowbus` AAR contains `classes.jar` and `proguard.txt`.
8. Both `flowbus-core` and `flowbus` use publication metadata from the root `gradle.properties`.
9. POM files must contain license and developer metadata, so Maven Central displays complete project information.
10. `verifyMavenLocalCoreConsumer` must depend directly on `flowbus-core`, so the core coordinate is resolved and compiled by a plain Kotlin/JVM consumer.
11. `verifyMavenLocalConsumer` must enable `android.useAndroidX=true`, Java/Kotlin 1.8 targets, and release minify so Maven local coordinates are resolved and shrunk by a real Android consumer.

Publication metadata includes: 1. `GROUP`. 2. `VERSION_NAME`. 3. POM. 4. SCM. 5. issue metadata. 6. license metadata. 7. developer metadata.

`verifyMavenLocalArtifacts`, `verifyMavenLocalCoreConsumer`, and `verifyMavenLocalConsumer` run these assertions in one place.

## 4. Repository Ownership Checks

Before publishing, confirm:
1. The root repository no longer contains `.gitmodules`.
2. `flowbus-core` no longer contains `.git`, an independent Gradle Wrapper, an independent `settings.gradle.kts`, or an independent `gradle.properties`.
3. `settings.gradle.kts` still includes `flowbus-core` and `library-android`.
4. The root `.github/workflows/ci.yml` covers submitted-diff whitespace checks, `apiCheck`, unit tests, release lint, sample app assemble, library debug connected tests, app release connected tests, local Maven artifacts, core consumer, minified Android consumer, and release dry-run.

## 5. Compatibility Checks

Before publishing, confirm:

| Item | Requirement |
| --- | --- |
| Kotlin | `1.9.25` |
| AGP | `8.6.1` |
| Gradle | `8.7` |
| Gradle runtime JDK | `17` |
| Published bytecode | Java 8 |
| Android SDK | `minSdk=21`, `compileSdk=35`, `targetSdk=35` |

Kotlin 1.8 or lower and AGP 7.x projects are not promised frictionless integration. Legacy projects need separate validation.

Current compatibility floor in one line: Kotlin 1.9.25, AGP 8.6.1, Gradle 8.7, JDK 17, Java 8.

## 6. Documentation And Version Checks

Before publishing, use this table:

| Check | Responsible docs | Pass criteria |
| --- | --- | --- |
| Version name | root `gradle.properties` | `VERSION_NAME=1.0.6` |
| Install coordinates | root README, Android README, and core README in Chinese and English | `flowbus` and `flowbus-core` coordinates and versions match |
| Chinese / English links | root README, module README files, and `docs` pages | Chinese and English docs link to each other |
| Documentation map | root README | links to the Android module, core module, version history, and Chinese document |
| Send-result boundary | root README, Android README, core README, release notes | `tryPostEventResult(...)` / `tryPostResult(...)` only describe whether `tryEmit` was rejected, not business handling success |
| Overflow-policy boundary | root README, Android README, core README, release notes | `DROP_OLDEST` / `DROP_LATEST` are not reliable-queue policies |
| Scope-close boundary | core README, release notes | `close()` invalidates the handle immediately, while `closeSuspending()` / `tryClose(timeoutMillis)` wait for cleanup or return a timeout result |
| One-time sticky consumption boundary | root README, Android README, core README, release notes | `consumeStickyLatest(...)` / `consumeStickyLatestEvent(...)` only reads and clears the current sticky replay, and does not prevent another thread from writing a new sticky value later |
| Old-layout troubleshooting | `TROUBLESHOOTING.md` | keeps only local old-structure troubleshooting for the main-repository layout, without old child-repository initialization or release workflow |

## 7. Before Remote Publication

Before remote publication, confirm the local machine or CI has Maven Central tokens and GPG signing configured.

Local tasks do not require signing:

1. `verifyMavenLocalArtifacts`
2. `verifyMavenLocalCoreConsumer`
3. `verifyMavenLocalConsumer`
4. `publishToMavenLocal`

Remote tasks require publishing credentials and signing:

1. `publishToMavenCentral`
2. `releaseToMavenCentral`

After a remote upload succeeds, confirm that each artifact in the Central Portal deployment has `.asc` signature files and checksum files. Local `publishToMavenLocal` does not create remote signature files, so it only verifies artifact structure.

The current configuration uploads a Central Portal deployment and does not automatically release it.

```bash
./gradlew releaseToMavenCentral
```

After the publication task succeeds, check deployment status, signature files, and checksum files in Central Portal, then decide whether to release or drop it.

Do not commit tokens, GPG private keys, `signing.properties`, or `release.properties`.
