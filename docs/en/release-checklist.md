# 1.2.0 candidate release checklist

[中文](../release-checklist.md)

Current local candidate: VERSION_NAME=1.2.0, APP_VERSION_NAME=1.2.0, APP_VERSION_CODE=9. It is not published. Track final-candidate evidence in the [1.2.0 notes](release-notes-1.2.0.md). API 21/API 35 and remote CI remain required; historical 1.1.0 success is insufficient.

Remote acceptance for `codex/flowbus-1.2.0` is tracked in [PR #7 current-head checks](https://github.com/logan0817/FlowBus/pull/7/checks). Confirm its current head, complete CI, actual device identities, and original reports from the same run. Draft status does not establish acceptance or publication.

## Historical 1.1.0 publication evidence

1.1.0 was published on 2026-10-12, with VERSION_NAME=1.1.0, APP_VERSION_NAME=1.1.0, APP_VERSION_CODE=8, and catalog version 1.1.0. This checklist records the release gates and maintenance procedure for future versions. Always verify final artifacts rather than relying only on earlier reports.

The published batch is `io.github.logan0817-FlowBus-1.1.0`, ID `24a88a3e-fc49-479e-8605-140989c944f2`, with Portal status `PUBLISHED`. All 10 public Central artifacts and 10 signatures match the accepted candidate checksums, with valid signatures. The independent core consumer resolved from Central passed normal/sticky, scope, and suspending collection checks on Java 8; the independent Android consumer passed release R8. Local evidence is in `build/release-1.1.0/pr-followup/published-artifacts-verification.json` and `central-consumers/{core,android}/build/reports/central-artifacts.json`; these generated reports are not committed. The Portal status API also returned a missing-components message. Public artifacts and signatures were verified independently rather than relying on that API message.

## Local quality and artifact gates

Use the existing JDK 17, Android SDK, and wrapper from the repository root. Root publishToMavenLocal removes the selected repository's two current-version directories before publishing; use an isolated candidate repository.

```bash
./gradlew apiCheck :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :app:lintRelease :app:assembleDebug :app:assembleRelease --warning-mode=all
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8 --warning-mode=all
./gradlew verifyMavenLocalBinaryCoreConsumer -Pflowbus.consumer.baselineVersion=1.1.0 "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
./gradlew verifyCentralDeploymentName --warning-mode=all
./gradlew releaseToMavenCentral --dry-run --warning-mode=all
git diff --check HEAD -- . ':(exclude)flowbus-core/api/*.api' ':(exclude)library-android/api/*.api'
```

The Java parameter selects only the consumer runtime; Gradle still uses JDK 17. Use an existing JDK 8. Review apiDump changes for intentional public API changes; do not bypass apiCheck. Generated API snapshot trailing lines are excluded from ordinary whitespace checks.

Repository documentation and release configuration are explicit JVM test inputs. Changes to those files must rerun the gates rather than reuse earlier passing results.

- Core/Android/app JVM tests and release lint pass; sample release uses R8/minify.
- flowbus-core-1.2.0.jar, flowbus-1.2.0.aar, sources/javadoc, POM, and module metadata exist.
- Check dependency scopes, license, developer, SCM, and versions: core exposes Coroutines; Android exposes matching core and Lifecycle runtime/viewmodel.
- AAR includes classes.jar and proguard.txt; both coordinates use Java 8 bytecode without fixture, Compose/Navigation, or local configuration.
- Run the Maven core consumer on JDK 8. Compile binary consumers separately against pinned Central 1.0.7 and 1.1.0 and run identical bytecode against original and candidate. Android/Compose consumers pass release R8.
- Verify published 1.0.7 and 1.1.0 JVM symbols using consumer READMEs. Preserve raw internal differences; static symbols are not full ABI certification.
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
3. Verify each selected version combination independently: [Android](../../compatibility/android-consumer/README.md) six stages, [Compose](../../compatibility/compose-consumer/README_EN.md) nine effect/eight policy stages, [Navigation](../../compatibility/navigation-consumer/README_EN.md) seven stages, and [actual NavHost](../../compatibility/navhost-consumer/README_EN.md) eight stages. Drivers require current runId logs, same-package UI, and every stage.
4. Mocks verify protocol only. Missing devices, timeouts, missing stages, and stale results are not passes.
5. CI configures API 21 and API 35 gates; remote success must match the final commit.

The API 21 step records and asserts the selected emulator's actual API and boot state before testing. Whether the workflow succeeds or fails, it uploads `flowbus-verification-<commit SHA>` reports for 14 days: unit tests, lint, instrumentation, JVM symbols, and current consumer logs/UI. Download reports matching the commit when diagnosing failures. Missing files can indicate an earlier step never ran and do not establish a pass.

emulator-runner executes each `script` line in a separate shell, so multiline heredocs and continued commands must live in a standalone script. API 21 uses one `bash compatibility/run-api21.sh` command for device identity checks, test signing, and consumer verification, stopping at the first failure. Keep compound commands in that script. Mocked-ADB script regression checks identity rejection and failure propagation; it does not replace device verification. JDK keytool generates consumer test signing material in `build/api21-signing/`, without depending on a user-home debug keystore. Keys are neither uploaded nor committed and are not used for publication.

API 35 UI checks use `bash compatibility/run-api35.sh` to wake the emulator and dismiss a non-secure keyguard before library and sample release tests. Before exit, the script saves `build/api35-environment/window.txt` and `power.txt` to diagnose resumed Activities without Espresso window focus. Diagnostics preserve the original test exit code. When an Activity lacks focus, the sample test rule prints window state before finishing it. Use that in-test evidence; Launcher focus after the whole suite does not identify the failure-time owner.

Before screen preparation or Gradle, the API 35 script records `build/api35-environment/device.json` and asserts actual API=35 and boot_completed=1. Wrong devices and incomplete boot stop execution while preserving exit diagnostics. Three mocked-ADB regressions cover preparation with original test-failure propagation, wrong-API rejection, and incomplete-boot rejection; these do not establish device execution. The channel sample's release instrumentation includes Activity recreation, idempotent retained-owner configuration, new ordinary-event collection, and absence of old-value replay.

A Google emulator Launcher ANR dialog can steal sample window focus. Before launch, the test rule checks window state; only when it finds `Application Not Responding: com.google.android.apps.nexuslauncher` does it preserve the evidence and stop that Launcher process before starting the sample normally. Target-app ANRs, other system errors, and Espresso focus assertions still fail as usual. Environment recovery does not establish a test pass.

The shared consumer device protocol writes UI hierarchy files under `/data/local/tmp/` with the current runId, avoiding shared-storage assumptions on older emulators and stale hierarchy reuse. It saves dump output as `ui-dump.txt`; command failures, ERROR output, missing hierarchy files, or invalid XML cannot pass. Inspect this output before comparing `current.log` and `current-ui.xml`. A log PASS alone does not establish successful verification.

CI SDK setup explicitly selects `platform-tools`; the setup-android Action prepares command-line tools separately. Do not restore its default package list containing the removed `tools` package. The next installation step selects platform and Build Tools versions.

The [final main CI](https://github.com/logan0817/FlowBus/actions/runs/38207876160) for the historical 1.1.0 release targets `efff42874b6c28a6074f72716b31acfcf6fa66b7` and passed all gates, including API 21 and API 35 checks. Its report artifact is `flowbus-verification-efff42874b6c28a6074f72716b31acfcf6fa66b7`. No API 21 image is available locally. Future releases must still confirm complete CI success for the final commit, the actual API 21 device identity, and all consumer reports. Started/queued runs and older successful commits do not replace this check. See the [compatibility assessment](next-version-plan.md#compatibility-assessment) for tested combinations and uncertified toolchains.

## Compatibility, sources, and documentation

Keep Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2, AGP 8.6.1, Gradle 8.7, build JDK 17, Java 8 bytecode, and minSdk 21 / compileSdk 35 / targetSdk 35. Consumer compiler/compileSdk matrices do not certify every older toolchain.

Check bilingual root/module READMEs, CHANGELOG, 1.2.0 notes, API comments, versions, and CI paths. Preserve 1.0.7 only for baseline/history/semantics. Delivery, sticky replay, closure, error policy, and cooperative cancellation documentation must match implementation.

Inspect git status and working/staged diffs. Exclude generated artifacts, signing material, secrets, logs, local configuration, temporary conversation records, and unrelated changes. Retain useful fixtures, API baselines, and tests. Both modules belong to the main repository without nested Git/submodule or independent publication configuration.

## Remote publication and rollback

Remote actions require separate authorization. Configure Central token/GPG signing only after all gates pass; never commit credentials.

### GPG signing and upload credentials

Both modules use `signing { useGpgCmd() }`, delegating signing to the local GPG executable and existing keyring. In Gradle 8.7, this implementation reads `signing.gnupg.*`; it does not automatically use or translate the traditional PGP properties `signing.keyId`, `signing.password`, or `signing.secretKeyRingFile`. Without `signing.gnupg.keyName`, key selection follows GPG configuration and defaults. Verify the actual signing key before publication rather than relying on an ineffective `signing.keyId`.

To select a key explicitly, optionally add the following to the user-level `~/.gradle/gradle.properties`, replacing the placeholder with your full key fingerprint. Never put private keys or passphrases in project configuration:

```properties
# Optional: select an existing signing key in the local keyring.
signing.gnupg.keyName=<full-key-fingerprint>
```

By default, GPG / `gpg-agent` manages private-key unlocking. Manual and automated invocation use the same mechanism; a cached passphrase can avoid another prompt. An `UP-TO-DATE` signing task reuses existing signatures without unlocking again. No password dialog does not establish that `signing.password` was used, a passphrase leaked, or a signing task actually ran. Check task execution status and verify `.asc` signatures. Enter a required passphrase through the local GPG prompt. If unlocking or signing fails, inspect GPG errors and the local agent/pinentry environment; do not bypass failure by disabling passphrase protection or placing a password in command-line arguments.

`signing.gnupg.passphrase` is the explicit passphrase property supported by GPG command-line signing. This project does not require it and prefers the local agent. The publication plugin also supports separate `signingInMemoryKey` settings; setting that property switches to in-memory PGP signing instead of the GPG command-line path described here. Do not mix configuration from these two signing methods when determining the actual passphrase source.

`mavenCentralUsername` / `mavenCentralPassword` are the two parts of a Central Portal user token. They authenticate uploads and queries to Sonatype's official HTTPS endpoints and do not unlock GPG private keys. Keep long-lived credentials in user-level configuration or controlled secrets. Never print their values or include passphrases, tokens, private keys, or user-level configuration files in logs, Git, or publication bundles. Upload artifacts, metadata, signatures, and checksums; public `.asc` signatures contain neither private keys nor unlocking passphrases. Maintainers handle credential cleanup and passphrase rotation separately; publication scripts must not rewrite them automatically.

### Upload, verification, and rollback

Future upload batches use `GROUP-root project name-VERSION_NAME`, currently `io.github.logan0817-FlowBus-1.2.0`, so different projects with the same group and version remain recognizable. The name is for human identification; queries, release, and drop must use the unique deployment ID returned by that upload and verify the batch contents. Actual Maven coordinates remain `io.github.logan0817:flowbus-core:1.2.0` and `io.github.logan0817:flowbus:1.2.0`.

Publication plugin 0.33.0 has no public deployment-name setting. The root publication script sets only the staging artifact identity of both `createStagingRepository` tasks to the root project name, allowing the plugin to derive a name from one shared staging identity. It does not change `MavenPublication` or publication file paths. Single-module uploads use the same project name; actual coordinates distinguish the module contents. `verifyCentralDeploymentName` checks both staging identities and actual publication coordinates locally and in CI, and runs before staging tasks. It performs no signing or network upload. After upgrading the publication plugin, review its naming algorithm and staging task properties, then run this check and the remote dry-run again.

Changing the naming configuration does not automatically affect existing deployments. Replacing an unpublished deployment requires separate authorization: verify its state and coordinates using the original deployment ID, drop it, then upload and verify the replacement. Use the newly returned deployment ID for all operations on the replacement. Published coordinates cannot be overwritten.

Publication is in gradle/release-publishing.gradle.kts and validation in gradle/release-verification.gradle.kts. publishToMavenCentral / releaseToMavenCentral upload a Central Portal deployment; this does not automatically release it. Inspect both coordinates, sources/javadoc, .asc signatures, and checksums before releasing or dropping in Portal. Maven local does not generate remote signatures.

After authorized publication and Central availability, resolve 1.2.0 from the remote repository in an independent consumer and run a normal/sticky delivery smoke check. Verify the resolved version so local candidates or old caches cannot stand in for the published artifacts.

Rollback consumers to published 1.0.7 and revert new API calls when necessary. Drop unpublished deployments; never overwrite a released version.
