# Android Maven consumer device checks

[中文](README.md)

This directory holds source fixtures for an independent consumer of the real candidate Maven artifacts after release R8. It is not a standalone Gradle project or a published library. It does not add production dependencies, raise minSdk, or configure production signing.

This entry verifies the local source version from `gradle.properties`; both sources and the stable public release use **1.3.1**. The current APK includes original-compiled 1.1.0 and 1.3.0 fixtures; candidate and public dependency verification remain separate. See the [release notes](../../docs/en/release-notes-1.3.1.md).

## Generate and build

From the repository root, reuse its JDK, Android SDK, and Gradle wrapper:

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.kotlinVersion=1.8.22 -Pflowbus.consumer.compileSdk=33 verifyMavenLocalConsumer
```

Preparation tracks the Activity, `legacy` / `legacy130` Kotlin and Java fixtures, and consumer settings. It recreates `build/maven-local-consumer`, copies those sources, and generates an exported MAIN/LAUNCHER manifest plus the existing application smoke check. Edit the fixtures rather than the generated directory.

By default the verification task publishes a candidate into the isolated Maven directory, builds the legacy runtime test, and assembles release. CI downstream jobs pass `-Pflowbus.verification.reuseCandidate=true` to verify and reuse the sealed candidate instead. FlowBus resolves exclusively from that candidate repository. `prepareMavenLocalConsumer` alone generates source without publishing, compiling, or running devices. See the [release checklist](../../docs/en/release-checklist.md) for candidate identity rules.

The Kotlin and compileSdk properties configure this consumer only. Default compileSdk/targetSdk are 35; minSdk is 21 and bytecode targets Java 8. Release uses R8 and is unsigned:

- APK: `build/maven-local-consumer/app/build/outputs/apk/release/app-release-unsigned.apk`
- Mapping: `build/maven-local-consumer/app/build/outputs/mapping/release/mapping.txt`

## Local test signing

Use Android Build Tools and an existing local test keystore. Replace these placeholder paths and alias; let apksigner prompt for credentials. Never use production keys or commit passwords/keystores.

```bash
FLOWBUS_BUILD_TOOLS="/path/to/android-sdk/build-tools/35.0.0"
FLOWBUS_TEST_KEYSTORE="/path/to/local-test.jks"
FLOWBUS_TEST_KEY_ALIAS="local-test"
"$FLOWBUS_BUILD_TOOLS/zipalign" -f -p 4 build/maven-local-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/maven-local-consumer/app/build/outputs/apk/release/app-release-aligned.apk
"$FLOWBUS_BUILD_TOOLS/apksigner" sign --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias "$FLOWBUS_TEST_KEY_ALIAS" --out build/maven-local-consumer/app/build/outputs/apk/release/app-release-test-signed.apk build/maven-local-consumer/app/build/outputs/apk/release/app-release-aligned.apk
"$FLOWBUS_BUILD_TOOLS/apksigner" verify --verbose build/maven-local-consumer/app/build/outputs/apk/release/app-release-test-signed.apk
```

A previously installed package signed with another key causes installation to fail. Check its purpose before removing anything another task may need.

## Cold-start a selected device

Select an online target using `adb devices -l`. Replace the adb path, serial, and expected API with actual values; the APK must already be test-signed:

```bash
python3 compatibility/android-consumer/verify-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --apk build/maven-local-consumer/app/build/outputs/apk/release/app-release-test-signed.apk --expected-api 21
```

The driver checks actual API and completed boot, installs the APK, force-stops only `com.logan.flowbus.consumer`, and cold-starts the Activity with a new `flowbus.consumer.runId` UUID. PASS/FAIL logs and UI text include that UUID. It neither clears global logcat nor relies on newer pid filters.

The Activity initially displays `FlowBus consumer: RUNNING`. All eight phases share a 10-second timeout; Java interop also has a five-second timeout. The driver polls for up to 30 seconds and accepts only `PASS API=<actual API> checks=8 runId=<current UUID>`, followed by matching same-package UI containing all eight phase names. Missing Java results, stale logs, another foreground package, failures, and timeouts are rejected.

Evidence lives under `build/android-consumer-device/<UUID>/` unless `--report-dir` overrides the parent: `start.txt`, `current.log`, `ui-dump.txt`, `current-ui.xml` when available, and `result.txt` only after complete success. Preserve existing files after failure; files from later stages may not exist. APK/mapping presence or compilation alone is not device evidence.

## Coverage and boundaries

| Phase | Behavior checked |
| --- | --- |
| `normal-sticky-scope` | Normal send/receive; sticky read/consume; owned scope close and fresh same-name replay |
| `legacy-android` | Generic Flow collection; global/owner sticky isolation; directly subscribed scoped Flow with old publishing APIs |
| `owner-suspending` | Owner suspend callback and cancellation |
| `explicit-lifecycle` | Sequential explicit-source callback; controlled stop cancellation; latest sticky on restart; instance isolation; receiver destruction leaves the source owned by its caller |
| `channel-config` | Idempotent owner configuration; zero-buffer SUSPEND rejection versus suspending send; effective policy versus bus defaults |
| `binary-1.1.0-android` | Calls compiled only against fixed original 1.1.0 core/AAR, running with the candidate |
| `java-interop` | Original 1.3.0 Java compilation and SAM callbacks; Java/Kotlin exchange; global/owner and normal/sticky isolation; actual subscription notifications; stop/restart/destruction; active cancellation and completion; channel diagnostics and parameter validation |
| `binary-1.3.0-android` | Original 1.3.0 Kotlin default-argument and subscription-notification calls actually subscribe/cancel and query diagnostics on the candidate |

[JavaApiCalls.java](legacy130/src/main/java/com/logan/flowbus/consumer/JavaApiCalls.java) now resides in `legacy130` and compiles only against public 1.3.0, without requiring Kotlin Unit, KClass, or suspend arguments. Its frozen old classes are passed to release R8 with candidate runtime dependencies. The Activity is compiled against the candidate and is not itself old-bytecode evidence. Five result channels distinguish global/owner normal/sticky and a Kotlin collector. Real `onSubscribed` notifications establish readiness; inspector counts are assertions, not barriers. Ordinary stopped-period sends are lost, sticky replays the latest value, and cancelling the Java owner subscription leaves the Kotlin subscription working. A Java listener cancels itself and checks cancellation requested before its in-flight callback finishes; the caller then waits for completion.

These checks execute on real `Dispatchers.Main.immediate` with controlled `LifecycleRegistry` owners moving through CREATED/STARTED/DESTROYED. Deferred, Channel, actual subscription notifications, and direct UNDISPATCHED collection control ordering without arbitrary delay. This does not establish Activity foreground/background interactions, rotation, navigation, or process restoration. Unknown-channel inspection does not register a channel; initial Android source acquisition can still create its FlowEventBus.

Each phase cleans its owners, subscriptions, scopes, and channels in finally. Java cleanup destroys the receiver and requests cancellation before clearing source owners/channels; it does not depend on suspending cleanup. Activity destruction cancels the verification scope and cannot create a misleading PASS. Restart from cold after interruption. Historical 1.1.0/1.2.0 results do not certify these eight candidate phases; use the final candidate's actual device reports and [compatibility boundaries](../../docs/en/compatibility.md).

## Driver and CI checks

Run the mocked-ADB driver cases without a device:

```bash
python3 -B compatibility/android-consumer/verify_device_test.py
```

They validate current-run/UI matching, missing Java phase rejection, stale PASS rejection, current failure, wrong foreground package/API, and UI dump failure/missing/command failure. They do not prove Android Main, Lifecycle, R8, or device execution. Four separate Shell cases check identity recording and failure propagation. Run all cases in this directory with:

```bash
python3 -B -m unittest discover -s compatibility/android-consumer -p '*_test.py' -v
```

CI prepares Kotlin 1.8.22 / compileSdk 33 Android plus fixed-baseline Compose, Navigation, and NavHost APKs before starting the API 21 emulator via `compatibility/prepare-api21.sh`. It keeps them under `build/api21-apks/`. `compatibility/run-api21.sh` verifies the selected device, runs library instrumentation, creates task-local test signing, and invokes each driver, stopping at the first failure. Current cloud results must match the final commit/candidate; see the [release checklist](../../docs/en/release-checklist.md).

## Published binary baselines

Android API snapshots for 1.0.7, 1.1.0, and 1.2.0 remain in `api/`; do not replace them with development apiDump output. Fixed artifact hashes are in [baselines.json](../core-binary-consumer/baselines.json). The symbol verifier compares original AAR `classes.jar` descriptors with the candidate, using the fixed original snapshot and JDK 17 javap:

```bash
python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --baseline-artifact build/published-baseline/flowbus-1.0.7.aar \
  --candidate-artifact build/candidate-maven/io/github/logan0817/flowbus/1.3.1/flowbus-1.3.1.aar \
  --baseline-api-snapshot compatibility/android-consumer/api/flowbus-1.0.7.api \
  --javap /path/to/jdk17/bin/javap \
  --output build/android-jvm-symbol-verification.json
```

Obtain and verify the original AAR first; the [Chinese guide](README.md#已发布-107-aar-的-jvm-符号核对) provides the fixed 1.0.7 download/hash example. Static symbols do not prove complete ABI, resources/manifest compatibility, or old Android bytecode executing on a device. The historical 1.0.7 comparison records removal of an unsupported Kotlin-internal generated method; such removals still require source/call-path review.

`legacy/src/main` remains compiled only against original **1.1.0** core/AAR classes plus Lifecycle. `verifyMavenLocalConsumer` runs `:legacy:testReleaseUnitTest` with candidate runtime before the minified APK build. It verifies loaded class bytes and unchanged original consumer class digests while exercising suspend/root/shared/owned/owner paths. The report is `build/maven-local-consumer/legacy/build/reports/binary-android-compatibility.txt`. Its controlled JVM Main/Lifecycle environment is distinct from the device phase, which runs the same legacy fixture.

The new **1.2.0** baseline adds its fixed Android snapshot and original-compiled core replacement run through the [core binary consumer](../core-binary-consumer/README_EN.md). This APK runs 1.1.0 legacy and 1.3.0 legacy130 bytecode. Do not describe 1.2.0 Android static symbols and core runtime checks as old 1.2.0 Android bytecode device execution.

## Published 1.3.0 Android bytecode baseline

`legacy130/src/main/java` contains `JavaApiCalls.java` and `Legacy130AndroidCalls.kt`. Generation validates the fixed hashes in [baselines.json](../core-binary-consumer/baselines.json) and extracts compileOnly inputs from public 1.3.0 core JAR / Android AAR. Original implementations do not enter the final runtime. `:legacy130:freezeLegacy130` creates `legacy130/build/frozen/legacy130-android.jar` and `legacy130/build/reports/compile-identity.json`; the candidate app consumes this frozen JAR before R8.

After assembling the APK, `verifyMavenLocalConsumer` runs `:legacy130:verifyLegacy130Identity`, checking unchanged consumer class/JAR hashes and actual runtime versions/artifact hashes against the isolated candidate. The report is `build/maven-local-consumer/legacy130/build/reports/binary-compatibility.json`. Its `deviceExecution=required-separately` explicitly requires the eight-stage device run; producing the report alone does not establish successful runtime calls. The static public-symbol baseline is [api/flowbus-1.3.0.api](api/flowbus-1.3.0.api).

```bash
# Download and verify the published baseline as specified in docs/implementation-plan-1.3.1.md first.
FLOWBUS_VERSION=$(sed -n 's/^VERSION_NAME=//p' gradle.properties)
python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --baseline-artifact build/published-baseline/flowbus-1.3.0.aar \
  --candidate-artifact "build/candidate-maven/io/github/logan0817/flowbus/$FLOWBUS_VERSION/flowbus-$FLOWBUS_VERSION.aar" \
  --baseline-api-snapshot compatibility/android-consumer/api/flowbus-1.3.0.api \
  --javap /path/to/jdk17/bin/javap \
  --output build/android-1.3.0-jvm-symbol-verification.json
```
