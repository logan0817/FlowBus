# 1.1.0 release checklist

[中文](../release-checklist.md)

Target: unreleased 1.1.0, with VERSION_NAME=1.1.0, APP_VERSION_NAME=1.1.0, APP_VERSION_CODE=8, and catalog version 1.1.0. Verify final artifacts rather than relying only on earlier reports.

## Local quality and artifact gates

Use the existing JDK 17, Android SDK, and wrapper from the repository root. Root publishToMavenLocal removes the selected repository's two current-version directories before publishing; use an isolated candidate repository.

```bash
./gradlew apiCheck :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease :app:assembleDebug :app:assembleRelease --warning-mode=all
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8 --warning-mode=all
./gradlew releaseToMavenCentral --dry-run --warning-mode=all
git diff --check HEAD -- . ':(exclude)flowbus-core/api/*.api' ':(exclude)library-android/api/*.api'
```

The Java parameter selects only the consumer runtime; Gradle still uses JDK 17. Use an existing JDK 8. Review apiDump changes for intentional public API changes; do not bypass apiCheck. Generated API snapshot trailing lines are excluded from ordinary whitespace checks.

Repository documentation and release configuration are explicit JVM test inputs. Changes to those files must rerun the gates rather than reuse earlier passing results.

- Core/Android/app JVM tests and release lint pass; sample release uses R8/minify.
- flowbus-core-1.1.0.jar, flowbus-1.1.0.aar, sources/javadoc, POM, and module metadata exist.
- Check dependency scopes, license, developer, SCM, and versions: core exposes Coroutines; Android exposes matching core and Lifecycle runtime/viewmodel.
- AAR includes classes.jar and proguard.txt; both coordinates use Java 8 bytecode without fixture, Compose/Navigation, or local configuration.
- Run the Maven core consumer on JDK 8. Compile the binary consumer only against Central 1.0.7 and run identical bytecode against original and candidate. Android/Compose consumers pass release R8.
- Verify published 1.0.7 JVM symbols using consumer READMEs. Preserve raw internal differences; static symbols are not full ABI certification.
- Remote dry-run verifies the unified graph and cleanup order; it executes neither clean nor upload/signature verification.

## Device and minimum API gates

Confirm target API/boot and run instrumentation and UI drivers serially. Set each animation scale to zero only on the selected test device, then restore original values.

```bash
adb devices -l
# Replace the serial with the selected test device.
adb -s device-serial shell settings put global window_animation_scale 0
adb -s device-serial shell settings put global transition_animation_scale 0
adb -s device-serial shell settings put global animator_duration_scale 0
ANDROID_SERIAL=device-serial ./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --warning-mode=all
```

1. Run library instrumentation and minified sample release instrumentation.
2. minSdk 21 requires API 21 library and test-signed minified Maven consumer execution. API 24, filenames, and static D8 checks cannot replace it.
3. Verify each selected version combination independently: [Android](../../compatibility/android-consumer/README.md) four stages, [Compose](../../compatibility/compose-consumer/README_EN.md) nine effect/eight policy stages, [Navigation](../../compatibility/navigation-consumer/README_EN.md) seven stages, and [actual NavHost](../../compatibility/navhost-consumer/README_EN.md) eight stages. Drivers require current runId logs, same-package UI, and every stage.
4. Mocks verify protocol only. Missing devices, timeouts, missing stages, and stale results are not passes.
5. CI configures API 21 and API 35 gates; remote success must match the final commit.

The API 21 step records and asserts the selected emulator's actual API and boot state before testing. Whether the workflow succeeds or fails, it uploads `flowbus-verification-<commit SHA>` reports for 14 days: unit tests, lint, instrumentation, JVM symbols, and current consumer logs/UI. Download reports matching the commit when diagnosing failures. Missing files can indicate an earlier step never ran and do not establish a pass.

emulator-runner executes each `script` line in a separate shell, so multiline heredocs and continued commands must live in a standalone script. API 21 uses one `bash compatibility/run-api21.sh` command for device identity checks, test signing, and consumer verification, stopping at the first failure. Keep compound commands in that script. Mocked-ADB script regression checks identity rejection and failure propagation; it does not replace device verification. JDK keytool generates consumer test signing material in `build/api21-signing/`, without depending on a user-home debug keystore. Keys are neither uploaded nor committed and are not used for publication.

API 35 UI checks use `bash compatibility/run-api35.sh` to wake the emulator and dismiss a non-secure keyguard before library and sample release tests. Before exit, the script saves `build/api35-environment/window.txt` and `power.txt` to diagnose resumed Activities without Espresso window focus. Diagnostics preserve the original test exit code. When an Activity lacks focus, the sample test rule prints window state before finishing it. Use that in-test evidence; Launcher focus after the whole suite does not identify the failure-time owner.

CI SDK setup explicitly selects `platform-tools`; the setup-android Action prepares command-line tools separately. Do not restore its default package list containing the removed `tools` package. The next installation step selects platform and Build Tools versions.

Current remote candidate status is available in [PR #4 CI checks](https://github.com/logan0817/FlowBus/pull/4/checks). No API 21 image is available locally. Before release, confirm complete CI success for the final commit, the actual API 21 device identity, and all consumer reports. Started/queued runs and older successful commits do not replace this check. See the [compatibility assessment](next-version-plan.md#compatibility-assessment) for tested combinations and uncertified toolchains.

## Compatibility, sources, and documentation

Keep Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2, AGP 8.6.1, Gradle 8.7, build JDK 17, Java 8 bytecode, and minSdk 21 / compileSdk 35 / targetSdk 35. Consumer compiler/compileSdk matrices do not certify every older toolchain.

Check bilingual root/module READMEs, CHANGELOG, 1.1.0 notes, API comments, versions, and CI paths. Preserve 1.0.7 only for baseline/history/semantics. Delivery, sticky replay, closure, error policy, and cooperative cancellation documentation must match implementation.

Inspect git status and working/staged diffs. Exclude generated artifacts, signing material, secrets, logs, local configuration, temporary conversation records, and unrelated changes. Retain useful fixtures, API baselines, and tests. Both modules belong to the main repository without nested Git/submodule or independent publication configuration.

## Remote publication and rollback

Remote actions require separate authorization. Configure Central token/GPG signing only after all gates pass; never commit credentials.

Publication is in gradle/release-publishing.gradle.kts and validation in gradle/release-verification.gradle.kts. publishToMavenCentral / releaseToMavenCentral upload a Central Portal deployment; this does not automatically release it. Inspect both coordinates, sources/javadoc, .asc signatures, and checksums before releasing or dropping in Portal. Maven local does not generate remote signatures.

After authorized publication and Central availability, resolve 1.1.0 from the remote repository in an independent consumer and run a normal/sticky delivery smoke check. Verify the resolved version so local candidates or old caches cannot stand in for the published artifacts.

Rollback consumers to published 1.0.7 and revert new API calls when necessary. Drop unpublished deployments; never overwrite a released version.
