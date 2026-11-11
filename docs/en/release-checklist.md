# 1.3.1 pre-release checklist

**1.3.1 is an unpublished candidate.** Source target: VERSION_NAME=1.3.1, APP_VERSION_NAME=1.3.1, APP_VERSION_CODE=12; test APKs use versionCode=3. The latest public release remains 1.3.0. Final candidate evidence belongs in [1.3.1 release notes](release-notes-1.3.1.md); prior 1.3.0 evidence below remains historical. Android/effect/policy consumers now require 8/12/11 stages respectively; verify the actual PHASES and run identity. Publication is outside the current pre-release validation goal.

The stable version is **1.3.0, published to Maven Central**. See the [release notes](release-notes-1.3.0.md) for final main CI, public files and consumer acceptance. Historical evidence below describes its original version. Local publication now prepares core, Android and optional Compose artifacts. API 35 additionally verifies the same sealed Android/Compose candidates as API 21, using separate APK/report paths before serial device execution.

[中文](../release-checklist.md)

This checklist defines the current candidate procedure; tested commits and reports belong in the [1.3.1 release notes](release-notes-1.3.1.md). Historical evidence remains in the [1.3.0](release-notes-1.3.0.md), [1.2.0](release-notes-1.2.0.md) and [1.1.0](release-notes-1.1.0.md) notes.

## Release sequence

1. Finish implementation and review; verify versions, APIs, documentation, and candidate artifacts.
2. Confirm all current-head PR checks, including API 21/API 35 devices. After merge, verify CI on the resulting main commit.
3. Upload only after publication authorization, then verify coordinates, signatures, checksums, and the returned deployment ID.
4. Release the verified deployment separately. Resolve from public Maven Central in independent consumers; update stable installation examples, changelog/date, and release status only after success.

Record fixed tested snapshots in release notes and use the candidate PR's Checks tab or [CI runs](https://github.com/logan0817/FlowBus/actions) to verify the current head SHA of later commits. A PR workflow artifact name contains the synthetic merge SHA (`github.sha`); map it through the run's head SHA and report digest. Reports expire after 14 days; preserve required evidence securely before expiration, excluding keys and private environment files.

## CI jobs and candidate identity

The former single serial `verify` job is now `prepare` → parallel `consumers`, four `kotlin` matrix workspaces, `api21`, and `api35` → final `verify`. Preparation checks APIs, fresh JVM unit tests, release lint and publishes one isolated Maven candidate. Consumers retain Java 8, default Kotlin Android R8, 1.0.7/1.1.0/1.2.0/1.3.0 core unchanged-bytecode and corresponding core/Android/Compose public-symbol baselines, newer Compose/Navigation, all three default JMH smoke targets, separate candidate subscription smoke and release dry-run. API 21 retains Kotlin 1.8.22 / compileSdk 33 Android, baseline Compose, Navigation 2.4.1 and NavHost. API 35 runs library debug/sample release instrumentation followed serially by Android eight-stage, Compose twelve-effect/eleven-policy checks using separate APK/report paths. No toolchain or SDK baseline changes.

`flowbus-candidate-<workflow SHA>-<attempt>` contains `candidate-maven/` and `candidate-manifest.json`, preserving Maven paths and metadata. The manifest binds the checkout SHA, exact file set and SHA-256 of every file. Preparation also exports the manifest digest through an independent job output. Downstream jobs download into `build/`, explicitly reject a mismatch against that digest and check file identity before and after verification, and pass `-Pflowbus.verification.reuseCandidate=true` so verification never republishes the candidate. Missing or altered candidates fail. Rerun the entire workflow rather than mixing jobs from different attempts. Local entry points without this property retain automatic publication. A dirty local checkout also needs its patch to identify tested sources.

Only `prepare` writes the existing setup-gradle cache; downstream jobs read it. Root JVM tests always execute in CI (locally select `-Pflowbus.verification.freshTests=true`); legacy Android runtime tests execute on every invocation. Compilation, lint, API and packaging retain incremental reuse. Instrumentation uses task-local `--rerun`, never full-graph `--rerun-tasks`. Source Java 8 smoke and library/sample instrumentation build from the same checkout; they are source integration evidence, separate from Maven candidate acceptance. APKs are prepared before emulator startup. API 21 copies each consumer variant into its own `build/api21-apks/` filename before changing the shared generated project. Signing keys stay local and outside cache/artifact paths.

The final `verify` uses `always()` and requires all five job groups to succeed, including the whole Kotlin matrix (`fail-fast: false`). Failure, cancellation, skipped or missing jobs cannot pass. The existing required check name `verify` is preserved; maintainers should confirm actual contexts/rulesets after the first cloud run and adjust any explicitly enumerated checks themselves. New commits cancel only older runs of the same PR; main/master runs remain independent. Trigger paths are unchanged; Markdown cannot be blanket-ignored because some documents feed tests/generated outputs.

To reproduce candidate reuse, publish `verifyMavenLocalArtifacts` with `-Dmaven.repo.local=$PWD/build/candidate-maven`, run `python3 -B compatibility/candidate_bundle.py seal`, then use the reuse property on consumers. Run `bash compatibility/prepare-api21.sh` before starting the dedicated API 21 emulator and `bash compatibility/run-api21.sh` after it boots. In the separate API 35 workspace, run `bash compatibility/run-api35.sh --prepare` before emulator startup, then the same script without arguments after boot. Republishing requires resealing and fresh acceptance evidence. Use the existing JDK/SDK environment.

Timeouts allow for historical timings and cold setup: 20 minutes for preparation, consumers and devices, 15 for each compiler and 5 for aggregation. Reassess after the first run.

Compare the next authorized cloud run's wall time including queues, per-job/step durations, Gradle executed/UP-TO-DATE/FROM-CACHE counts, cache and artifact transfer time, emulator startup, summed job time and billed minutes. Parallelism can increase total runner minutes. Jobs API timings for runs 29/30 show API 35 at 268/200 s, Kotlin matrix at 218/139 s and API 21 at 213/167 s. Full logs returned 403, so these timings do not identify costs within each step or justify a speedup promise.

## Local quality and artifact gates

Use the existing JDK 17, Android SDK, and wrapper from the repository root. Root publishToMavenLocal removes the selected repository's three current-version coordinate directories before publishing; use an isolated candidate repository.

```bash
./gradlew apiCheck :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest :library-android:lintRelease :library-compose:lintRelease :app:lintRelease :app:assembleDebug :app:assembleRelease --warning-mode=all
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8 --warning-mode=all
./gradlew verifyMavenLocalBinaryCoreConsumer -Pflowbus.consumer.baselineVersion=1.1.0 "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
./gradlew verifyCentralDeploymentName --warning-mode=all
./gradlew releaseToMavenCentral --dry-run --warning-mode=all -PmavenCentralUsername=dry-run-unused -PmavenCentralPassword=dry-run-unused
git diff --check HEAD -- . ':(exclude)flowbus-core/api/*.api' ':(exclude)library-android/api/*.api' ':(exclude)library-compose/api/*.api' ':(exclude)compatibility/android-consumer/api/*.api' ':(exclude)compatibility/core-binary-consumer/api/*.api'
```

The Java parameter selects only the consumer runtime; Gradle still uses JDK 17. Use an existing JDK 8. Review apiDump changes for intentional public API changes; do not bypass apiCheck. Generated API snapshot trailing lines are excluded from ordinary whitespace checks.

Repository documentation and release configuration are explicit JVM test inputs. Changes to those files must rerun the gates rather than reuse earlier passing results.

- Core/Android/app JVM tests and release lint pass; sample release uses R8/minify.
- flowbus-core-1.3.1.jar, flowbus-1.3.1.aar, flowbus-compose-1.3.1.aar, and sources/javadoc, POM, and module metadata for all three coordinates exist.
- Check dependency scopes, license, developer, SCM, and versions: core exposes Coroutines; Android exposes matching core and Lifecycle runtime/viewmodel; optional Compose exposes matching core, Lifecycle runtime and Compose runtime 1.5.4.
- AARs include classes.jar and proguard.txt; all three coordinates use Java 8 bytecode without fixtures, Navigation, or local configuration. Core/Android remain free of Compose; only the optional Compose coordinate adds that dependency.
- Run the Maven core consumer on JDK 8. Compile binary consumers separately against pinned Central 1.0.7, 1.1.0, 1.2.0 and 1.3.0 and run identical bytecode against original and candidate. Android/Compose consumers pass release R8; Android/Compose 1.3.0 Java/Kotlin/Composable fixtures compile only against the original release; freeze their class/JAR identities and verify candidate runtime dependencies plus real device execution.
- Verify published 1.0.7, 1.1.0 and 1.2.0 core/Android JVM symbols using consumer READMEs. Add core/Android/Compose 1.3.0 public-symbol gates. The 1.2.0 core supported-symbol gate uses its original API snapshot and preserves raw internal differences; static symbols are neither full ABI certification nor old Android bytecode device evidence.
- Remote dry-run verifies the unified graph and cleanup order; it executes neither clean nor upload/signature verification. Gradle still checks credential presence during dry-run configuration, so the command supplies invalid placeholders. Ordinary verification needs no real Central token.

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
3. Verify each selected version combination independently: [Android](../../compatibility/android-consumer/README_EN.md) eight stages, [Compose](../../compatibility/compose-consumer/README_EN.md) twelve effect/eleven policy stages, [Navigation](../../compatibility/navigation-consumer/README_EN.md) seven stages, and [actual NavHost](../../compatibility/navhost-consumer/README_EN.md) eight stages. API 35 additionally runs the same candidate's Android/Compose three drivers serially. Drivers require current runId logs, same-package UI, and every stage.
4. Mocks verify protocol only. Missing devices, timeouts, missing stages, and stale results are not passes.
5. CI configures API 21 and API 35 gates; remote success must match the final commit.

The API 21 step records and asserts the selected emulator's actual API and boot state before testing. Whether the workflow succeeds or fails, it uploads `flowbus-verification-<job-or-kotlin-version>-<workflow SHA>-<attempt>` reports for 14 days: unit tests, lint, instrumentation, JVM symbols, and current consumer logs/UI. Download all report artifacts from the same run/attempt plus its candidate artifact and Actions digest. Compare the candidate manifests before combining evidence. Historical single-artifact names apply only to their historical runs. Missing files can indicate an earlier step never ran and do not establish a pass.

emulator-runner executes each `script` line in a separate shell, so multiline heredocs and continued commands must live in a standalone script. API 21 first runs `bash compatibility/prepare-api21.sh` to build and snapshot distinct APKs, then uses one `bash compatibility/run-api21.sh` command inside the emulator for device identity checks, test signing, and consumer verification, stopping at the first failure. Keep compound commands in that script. Mocked-ADB script regression checks identity rejection and failure propagation; it does not replace device verification. JDK keytool generates consumer test signing material in `build/api21-signing/`, without depending on a user-home debug keystore. Keys are neither uploaded nor committed and are not used for publication.

Before API 35 emulator startup, `bash compatibility/run-api35.sh --prepare` validates the sealed candidate, builds instrumentation/Maven consumers and saves default Android/Compose variants to `build/api35-apks/{android,compose}.apk`. After boot, invoke it without arguments to wake/dismiss a non-secure keyguard and serially run instrumentation and the three consumer drivers. Consumers use local `build/api35-signing/` material, with reports under `build/api35-consumers/{android,compose-effects,compose-policies}/`. Before exit, the script saves `build/api35-environment/window.txt` and `power.txt`; diagnostics preserve the original test/driver exit code.

The sample rule records focus at `RESUMED` and subsequent focus changes without waiting, retrying or requesting focus. On test/setup failure, before Activity cleanup, it saves full `dumpsys window`, `dumpsys input`, `dumpsys activity activities`, a screenshot and the app focus timeline under `/data/local/tmp/flowbus-ui-focus/<test-name>-<UUID>/`. API 35 pulls these into `build/api35-environment/flowbus-ui-focus/` for the existing artifact upload. Diagnostic errors are suppressed on the original exception; test failure and cleanup remain intact. Inspect this failure-time evidence first; Launcher focus after the suite does not identify the earlier owner. The `controlledFailure` directory from `ShellLaunchedActivityRuleTest` is an intentionally thrown/caught regression proving collection and exception preservation, not reproduction of actual focus loss.

The sequential home-navigation test finishes each child Activity on the main thread and waits for its destruction before confirming that the home Activity resumed and clicking the next entry. `RESUMED` or an idle main thread does not establish that the outgoing window has been removed. If logcat reports `InputDispatcher: No new touched window`, inspect click/window-exit ordering rather than extending the launch timeout or retrying the click. Cleanup timeouts still fail, and a regression rejects treating an obscured, undestroyed Activity as cleaned up. On an authorized device, run `ANDROID_SERIAL=device-serial ./gradlew :app:connectedReleaseAndroidTest --rerun -Pandroid.testInstrumentationRunnerArguments.class=com.logan.flowbusapp.app.MainActivityTest --warning-mode=all` for focused validation. Local results on another API do not replace the API 35 cloud gate.

Before screen preparation or Gradle, the API 35 script records `build/api35-environment/device.json` and asserts actual API=35 and boot_completed=1. Wrong devices and incomplete boot stop execution while preserving exit diagnostics. Three mocked-ADB regressions cover preparation with original test-failure propagation, wrong-API rejection, and incomplete-boot rejection; these do not establish device execution. The channel sample's release instrumentation includes Activity recreation, idempotent retained-owner configuration, new ordinary-event collection, and absence of old-value replay.

A Google emulator Launcher ANR dialog can steal sample window focus. Before launch, the test rule checks window state; only when it finds `Application Not Responding: com.google.android.apps.nexuslauncher` does it preserve the evidence and stop that Launcher process before starting the sample normally. Target-app ANRs, other system errors, and Espresso focus assertions still fail as usual. Environment recovery does not establish a test pass.

The shared consumer device protocol writes UI hierarchy files under `/data/local/tmp/` with the current runId, avoiding shared-storage assumptions on older emulators and stale hierarchy reuse. It saves dump output as `ui-dump.txt`; command failures, ERROR output, missing hierarchy files, or invalid XML cannot pass. Inspect this output before comparing `current.log` and `current-ui.xml`. A log PASS alone does not establish successful verification.

CI SDK setup explicitly selects `platform-tools`; the setup-android Action prepares command-line tools separately. Do not restore its default package list containing the removed `tools` package. The next installation step selects platform and Build Tools versions.

## Compatibility, sources, and documentation

Keep Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2, AGP 8.6.1, Gradle 8.7, build JDK 17, Java 8 bytecode, and minSdk 21 / compileSdk 35 / targetSdk 35. Consumer compiler/compileSdk matrices do not certify every older toolchain.

After public resolution succeeds, update stable coordinates in root/module READMEs, candidate status in the documentation indexes, candidate-build instructions, and the changelog release date. Retain historical version notes and baseline fixtures.

Check bilingual root/module READMEs, CHANGELOG, 1.3.1 release notes, API comments, versions, and CI paths while preserving actual historical evidence. Delivery, sticky replay, closure, subscription notifications, error policy, and cooperative cancellation documentation must match implementation.

Inspect git status and working/staged diffs. Exclude generated artifacts, signing material, secrets, logs, local configuration, temporary conversation records, and unrelated changes. Retain useful fixtures, API baselines, and tests. All three publication modules belong to the main repository without nested Git/submodule or independent publication configuration.

## Remote publication and rollback

Remote actions require separate authorization. Configure Central token/GPG signing only after all gates pass; never commit credentials.

### Default local publication from a fixed CI candidate

Use the existing local Central Portal token, GPG keyring and GPG agent. GitHub release environments, Actions secrets and private-key export are not prerequisites. Version 1.3.0 used this workflow; its commit, run/attempt, fingerprint, deployment ID and public acceptance are recorded in the [release notes](release-notes-1.3.0.md#formal-publication-acceptance-2026-11-10).

1. Select successful main push CI for a final version. Verify the commit, run ID, attempt, all nine checks, artifact ZIP digest, candidate manifest and every file SHA-256. Sign and upload these exact tested files without rebuilding replacements.
2. Include matching core, Android and Compose coordinates: primary artifact, POM, module metadata, sources and javadoc for each, totaling 15 files. Exclude `maven-metadata-local.xml`. Sign with the verified full local GPG fingerprint; generate MD5/SHA-1/SHA-256/SHA-512 for payloads and signatures. Check signature identity, checksums and ZIP contents.
3. Upload the signed bundle via the [Central Publisher API](https://central.sonatype.org/publish/publish-portal-api/) or Portal using `USER_MANAGED` and the project batch name. Send credentials only to official Sonatype HTTPS endpoints; never expose tokens in logs, command arguments, Git or bundles.
4. Save the returned deployment ID immediately. At `VALIDATED`, verify that ID and all three coordinates, then download payloads/signatures and compare them with the fixed bundle. If an upload response is lost, inspect the original deployment before any retry.
5. With publication authorization, publish that same verified deployment and wait for `PUBLISHED`. Continue querying its ID while `PUBLISHING`; do not upload or publish again.
6. Download public Maven Central payloads, signatures and checksums; compare every file and verify signatures. Use independent core/Android/Compose consumers resolving FlowBus exclusively from public Central, checking resolved versions and file hashes, Java 8, Release R8 and devices. Update installation/release records before publishing the tag and GitHub Release for the tested source commit.

Ignored local release files support recovery, not a portable repository command. Recheck current entry points and official APIs for each version. Other projects' `prepareCentralBundle` is not a task in this repository. The Gradle upload entries below build or rebuild artifacts and do not directly replace publication of fixed CI files.

### GPG signing and upload credentials

All three publication modules use `signing { useGpgCmd() }`, delegating signing to the local GPG executable and existing keyring. In Gradle 8.7, this implementation reads `signing.gnupg.*`; it does not automatically use or translate the traditional PGP properties `signing.keyId`, `signing.password`, or `signing.secretKeyRingFile`. Without `signing.gnupg.keyName`, key selection follows GPG configuration and defaults. Verify the actual signing key before publication rather than relying on an ineffective `signing.keyId`.

To select a key explicitly, optionally add the following to the user-level `~/.gradle/gradle.properties`, replacing the placeholder with your full key fingerprint. Never put private keys or passphrases in project configuration:

```properties
# Optional: select an existing signing key in the local keyring.
signing.gnupg.keyName=<full-key-fingerprint>
```

By default, GPG / `gpg-agent` manages private-key unlocking. Manual and automated invocation use the same mechanism; a cached passphrase can avoid another prompt. An `UP-TO-DATE` signing task reuses existing signatures without unlocking again. No password dialog does not establish that `signing.password` was used, a passphrase leaked, or a signing task actually ran. Check task execution status and verify `.asc` signatures. Enter a required passphrase through the local GPG prompt. If unlocking or signing fails, inspect GPG errors and the local agent/pinentry environment; do not bypass failure by disabling passphrase protection or placing a password in command-line arguments.

`signing.gnupg.passphrase` is the explicit passphrase property supported by GPG command-line signing. This project does not require it and prefers the local agent. The publication plugin also supports separate `signingInMemoryKey` settings; setting that property switches to in-memory PGP signing instead of the GPG command-line path described here. Do not mix configuration from these two signing methods when determining the actual passphrase source.

`mavenCentralUsername` / `mavenCentralPassword` are the two parts of a Central Portal user token. They authenticate uploads and queries to Sonatype's official HTTPS endpoints and do not unlock GPG private keys. Keep long-lived credentials in user-level configuration or controlled secrets. Never print their values or include passphrases, tokens, private keys, or user-level configuration files in logs, Git, or publication bundles. Upload artifacts, metadata, signatures, and checksums; public `.asc` signatures contain neither private keys nor unlocking passphrases. Maintainers handle credential cleanup and passphrase rotation separately; publication scripts must not rewrite them automatically.

### Upload, verification, and rollback

Upload batches use `GROUP-root project name-VERSION_NAME`; the target 1.3.1 batch is `io.github.logan0817-FlowBus-1.3.1`. Its actual publication state is recorded in the version notes; a batch name alone proves neither upload nor public availability. Regenerate and verify the candidate, including dry-run, after changing versions. Target coordinates are `io.github.logan0817:flowbus-core:1.3.1`, `io.github.logan0817:flowbus:1.3.1` and `io.github.logan0817:flowbus-compose:1.3.1`. Names identify batches to humans; queries, release and drop use the upload's unique deployment ID after checking its contents.

Publication plugin 0.33.0 has no public deployment-name setting. The root publication script sets only the staging artifact identity of all three `createStagingRepository` tasks to the root project name, allowing the plugin to derive a name from one shared staging identity. It does not change `MavenPublication` or publication file paths. Single-module uploads use the same project name; actual coordinates distinguish module contents. `verifyCentralDeploymentName` checks all three staging identities and actual publication coordinates locally and in CI, before staging tasks. It performs no signing or network upload. After upgrading the plugin, review its naming algorithm/task properties, then repeat this check and remote dry-run.

Changing the naming configuration does not automatically affect existing deployments. Replacing an unpublished deployment requires separate authorization: verify its state and coordinates using the original deployment ID, drop it, then upload and verify the replacement. Use the newly returned deployment ID for all operations on the replacement. Published coordinates cannot be overwritten.

Publication is in gradle/release-publishing.gradle.kts and validation in gradle/release-verification.gradle.kts. publishToMavenCentral / releaseToMavenCentral upload a Central Portal deployment; this does not automatically release it. Inspect all three coordinates, sources/javadoc, .asc signatures, and checksums before releasing or dropping in Portal. Maven local does not generate remote signatures.

After authorized publication and Central availability, resolve the final version remotely in independent consumers and run core/Android normal/sticky, Java interoperability and optional Compose effect smoke checks. Verify all three resolved versions so local candidates or old caches cannot stand in for published artifacts.

Rollback consumers to published 1.3.0, recompile and verify affected calls. No public API was added in 1.3.1, so Java, subscription notifications, diagnostics and optional Compose calls remain available. Rolling back restores the sticky reentrancy, failed scope registration and DESTROYED-threshold defects. Drop unpublished deployments only within authorization; never overwrite a released version.

## Retained 1.3.0 gates

```bash
./gradlew verifyMavenLocalBinaryCoreConsumer -Pflowbus.consumer.baselineVersion=1.2.0 "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
```

Review apiDump additions for all three modules. Old descriptors must remain; do not replace compatibility assertions with regenerated snapshots. The Compose AAR, POM/module metadata, sources/javadoc and public runtime dependencies must be verified. Test Java calls from actual .java sources, not only Kotlin calls to the facade. API 36 local results do not replace API 21/35 or cloud CI.
