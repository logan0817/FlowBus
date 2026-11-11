# Compose consumer verification example

[中文](README.md)

This directory is an isolated consumer template for evaluating Compose compilation and release R8 on the existing Kotlin 1.9.25 baseline. It now resolves candidate Maven artifacts and calls [FlowBusEffect](../../library-compose/README_EN.md) from the production module source. `flowbus-compose:1.3.0` is published to Maven Central. This entry still verifies isolated local source candidates by default; see the [release notes](../../docs/en/release-notes-1.3.0.md) for public dependency verification. Repository verification tasks generate and build the consumer; this template has no publication task.

The default consumer uses activity-compose `1.8.2`, Compose runtime/ui `1.5.4`, compiler `1.5.15`, and a direct Lifecycle `2.6.2` declaration with the current candidate FlowBus artifact. Runtime/ui `1.7.8` is a separate optional experiment. These dependencies apply only to the consumer; the base library does not add Compose or change its dependency, SDK, or Kotlin requirements. Keep evidence for the two combinations separate: declared versions do not establish resolved dependencies or device compatibility.

`FlowBusEffect` accepts an explicit `FlowBus`, `EventKey<T>`, and caller-provided `LifecycleOwner`, collecting from STARTED by default. Navigation callers can provide their `NavBackStackEntry`; the template adds no navigation dependency and does not use LocalLifecycleOwner. The event callback and optional `onSubscribed` notification use rememberUpdatedState, so updating either does not resubscribe. Changes to source, event name/type, owner, minimum lifecycle state, or sticky selection restart the effect. The 1.3.1 candidate rejects INITIALIZED and DESTROYED thresholds synchronously during composition. Supported values are CREATED, STARTED, and RESUMED; an already destroyed owner with a valid threshold completes without subscribing.

[Actual NavHost verification](../navhost-consumer/README_EN.md) optionally adds navigation-compose with the navHost property and records standard ComposeNavigator behavior, default animation thresholds, and Activity recreation. An incoming page may be STARTED before RESUMED; select RESUMED explicitly for side effects restricted to the interactive page. An outgoing page remaining composed or animated does not imply an active owner. Collect according to the actual entry lifecycle rather than visual presence.

Each active lifecycle entry fetches Flow from the source and uses core suspend collection with the source logger/error policy. Composition disposal, owner/source changes, or lifecycle inactivity cancel collection and its cooperative suspend callback without closing the bus. Each subscription handles events sequentially, without collectLatest. Normal events are not retained; sticky replay is explicit. Slow callbacks still follow existing buffering/overflow policy. Updating the callback affects subsequent invocations and does not replace work already executing. Application blocking/non-cancellable work must handle cancellation appropriately.

The launcher `ConsumerActivity` actually invokes the effects and uses AndroidView with native buttons and text controls instead of Material. The Application provides the root bus; a separate session bus is constructor-injected into the ViewModel. Both use the same Toast key to exercise instance isolation. Buttons update the captured callback version, post normal events, update StateFlow business state, publish sticky results, and remove/restore the sticky effect. Manual demonstration buttons have no additional handshake and should be used after active collection starts. Controlled effect verification uses the specific `onSubscribed` notification as a barrier rather than inferring readiness from registration return or inspector totals.

StateFlow independently expresses the business counter and restores the latest in-memory value after lifecycle re-entry. Events express transient Toast notifications. The ViewModel preserves state through configuration changes, but the template has no persistent storage or SavedStateHandle and does not guarantee recovery after process death.

Historical 1.2.0 fixture results: default 1.5.4 and optional 1.7.8 each passed ten API 24 interaction stages, nine controlled-effect stages, and eight policy stages. Public 1.3.0 consumes the optional module and passed its then-current eleven effect/eight policy stages. The unpublished 1.3.1 development candidate extends this to twelve effect/eleven policy stages. Historical results do not replace this candidate's build and device verification. Process-death recovery, deep links, and other NavHost configurations still require independent checks.

## Generation and build

From the repository root, prefer one batch using an absolute isolated Maven directory:

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer
```

For the Compose consumer alone:

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" verifyMavenLocalComposeConsumer
```

The task depends on root `publishToMavenLocal`. `prepareMavenLocalComposeConsumer` copies this app template into `build/maven-local-compose-consumer`, generates independent settings/build scripts, and runs `:app:assembleRelease` with minify/R8 enabled. The reachable launcher Activity calls the effects. Do not edit or commit generated files.

`-Dmaven.repo.local` selects the same location for publication, cleanup, artifact checks, and consumer dependencies. The default remains `~/.m2/repository`. Root publication first cleans the current-version directories for `flowbus-core`, `flowbus`, and `flowbus-compose` under the configured FlowBus group, then republishes. Use the isolated candidate directory to avoid replacing existing user-local artifacts. Generated consumers use exclusiveContent so FlowBus dependencies can only resolve from that location and cannot silently fall back to stale artifacts elsewhere.

Select the newer runtime/ui experiment with a consumer-only property:

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.composeVersion=1.7.8 verifyMavenLocalComposeConsumer
```

The consumer uses repository Kotlin/AGP/SDK settings. Preserve actual dependency resolution and AAR metadata results: the direct Lifecycle `2.6.2` declaration does not establish its resolved version. Declaration, compilation, and lowest-API device support are separate evidence.

| Historical 1.2.0 Compose runtime/ui | Then-resolved Lifecycle | Then-resolved core-ktx |
| --- | --- | --- |
| Default 1.5.4 | 2.6.2 | 1.10.0 |
| Optional 1.7.8 | 2.8.3 | 1.12.0 |

Both continue to use Kotlin 1.9.25, compiler 1.5.15, activity-compose 1.8.2, Coroutines 1.8.1, AGP 8.6.1, and Java 8 bytecode. Preserve this candidate's complete releaseRuntimeClasspath graph; direct declarations and APK META-INF markers do not prove resolved versions. Rerun current twelve-stage effects and eleven-stage parameter/error/cancellation policies against this candidate. Verify API 21/35 through actual device reports using the [release checklist](../../docs/en/release-checklist.md). See the [compatibility assessment](../../docs/en/compatibility.md).

## Controlled Composition verification entry point

The repository entry point is [verify-effects-device.py](verify-effects-device.py), which launches the real Activity's ComposeEffectVerification branch; ordinary launch still shows the interaction screen. It requires Python 3, an existing adb, a running explicitly selected device, and a test-signed release APK containing the new fixture. Build the default combination first:

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" verifyMavenLocalComposeConsumer
```

The unsigned output is build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk. Align and sign this Compose APK using the [local test-signing procedure](../android-consumer/README.md#本地测试签名), substituting its input/output paths and avoiding production keys. Replace the following APK, adb, and serial placeholders with actual local values:

```bash
FLOWBUS_COMPOSE_SIGNED_APK="/path/to/test-signed-compose-release.apk"
python3 -B compatibility/compose-consumer/verify-effects-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_COMPOSE_SIGNED_APK" --report-dir build/compose-effects-device
```

The driver checks actual API and boot_completed, installs the APK, force-stops its own package, and cold-starts with flowbus.compose.verifyEffects=true and the current flowbus.consumer.runId. The fixture has a 30-second global timeout; the driver polls for up to 40 seconds. Success requires the current FlowBusCompose logs and com.logan.flowbus.composeconsumer UI to match PASS API=<actual API> checks=12 runId=<current UUID> and every stage below. FAIL, stale runId, another foreground package, missing stages, or timeout cannot pass. Evidence is saved under report-dir/current-UUID as start.txt, current.log, ui-dump.txt, current-ui.xml, and the final result.txt. Run serially with instrumentation to avoid UiAutomation contention.

| Stage | Verified behavior |
| --- | --- |
| `initial-replay` | The effect receives the initial sticky probe |
| `subscription-established` | Exactly one initial notification identifies the source, key, owner, and minimum state of this subscription |
| `callback-updated-without-resubscribe` | Updating both callbacks does not resubscribe; the next event and subsequent establishment notification use the latest revision |
| `source-replaced` | Changing source cancels the old cooperative callback and enters the new-source probe |
| `key-replaced` | Changing key cancels the old callback and enters the new-key probe |
| `owner-replaced` | Changing controlled owner preserves new collection after destroying the old owner |
| `min-state-replaced` | Changing STARTED to RESUMED prevents receipt below the threshold and resumes receipt afterward |
| `composition-disposed-restored` | Effect removal cancels the callback; restored composition receives latest sticky |
| `lifecycle-stopped-restarted` | Inactivity cancels the callback; lifecycle restart receives latest sticky |
| `callback-error-continued` | A normal exception reaches the configured continuing handler; later callbacks still execute |
| `destroyed-source-retained` | DESTROYED cancels; the source still accepts posts and a new owner receives them; cancellation does not reach the normal-exception handler |
| `binary-1.3.0-composable` | An original-1.3.0-compiled Composable runs against the candidate, checking notifications, callback updates without resubscription, lifecycle restart and disposal cancellation |

Reproduce the driver-decision regression independently:

```bash
python3 -B compatibility/compose-consumer/verify_effects_test.py
```

Current effects and policy protocols each have one unittest, rejecting the absence of every individual phase and covering current PASS, stale PASS, current FAIL, another foreground package, missing stages, incorrect check counts, and wrong API before installation. Mocks only verify driver decisions, not device execution. Effect replacement/lifecycle phases also assert notification totals: one initially and one each for source, key, owner, threshold restoration, composition restoration, lifecycle restart, and a new owner after destruction, for eight total. Callback updates and continuing after errors must not add subscriptions. Process-death recovery, deep links, and other NavHost configurations require separate verification. The earlier temporary ten-stage driver is not the repository entry point.

An optional [real Navigation consumer](../navigation-consumer/README_EN.md) now selects navigation-runtime only with -Pflowbus.consumer.navigationVersion=2.4.1. Default Compose source/manifest/dependencies remain free of navigation. The fixture binds actual NavHostController entries to the Activity and passed compilation, release R8, and seven push/pop/back-dispatcher lifecycle stages on API 24 in two independent combinations: Navigation 2.4.1 / Compose 1.5.4 / Lifecycle 2.6.2 and Navigation 2.8.3 / Compose 1.7.8 / Lifecycle 2.8.3. Evidence is build/navigation-verification.json; it does not cover navigation-compose NavHost animations, saved-state/process restoration, or deep links. Returning to the default configuration also passed compilation/R8 and verified no navigation source/manifest/dependency residue, with Lifecycle still at 2.6.2. Android, Compose-effect, and Navigation thin drivers share compatibility/device_verification.py without CLI/timeout changes. Existing five/six mocked scenarios and three Navigation subtests passed; mocks do not establish device success.

## Error-policy and non-cooperative callback verification

ConsumerActivity adds a separate flowbus.compose.verifyPolicies mode started by verify-policies-device.py. It uses an actual Compose runtime Composition and isolated Recomposer to run the same FlowBusEffect; the outer composition updates an Activity-native text view. The inner composition contains effects only, without UI nodes. Controlled LifecycleRegistry owners do not establish navigation back-stack or NavHost UI behavior.

Effects, policy and NavHost checks display live status in an Activity-native TextView. A composition SideEffect updates the same text and disposal removes the view. This keeps results readable when Compose 1.7.8 filters UIAutomator-only semantics queries. Device accessibility settings and strict current-log/package/phase/run-ID checks remain intact.

The test recomposer isolates expected failures from the result screen. It does not replace the default error handler or add catches inside FlowBusEffect. It observes both CoroutineExceptionHandler and the runner completion cause, requiring recomposer shutdown, zero subscriptions, and original exception identity. This is test infrastructure, not advice to hide application failures with a supervisor.

| Eleven stages | Assertion |
| --- | --- |
| invalid-lifecycle-thresholds-rejected | INITIALIZED/DESTROYED fail synchronously during composition without registration/callbacks; finally releases composition resources |
| supported-lifecycle-thresholds | CREATED/STARTED/RESUMED start at their thresholds, preserve lifecycle restart behavior and cancel on destruction |
| destroyed-owner-does-not-subscribe | An already destroyed owner with a valid threshold does not subscribe or fail |
| default-rethrow-terminates-source-retained | Default policy propagates the original callback error and stops collection; a new composition can still use the same source |
| ignore-logs-and-continues | Ignore retains logger warnings and processes the next event |
| custom-handler-context-and-continue | A returning custom handler receives event/type/phase/scope/sticky/dispatcher context and continues |
| legacy-handler-continues | fromThrowableHandler receives the original error and continues |
| handler-failure-propagates | A custom handler's new failure propagates and terminates the composition |
| cancellation-bypasses-error-handler | CancellationException bypasses ordinary error handling and logging; lifecycle transitions can resubscribe |
| noncancellable-restart-waits-for-callback | A cancelled NonCancellable callback remains behind a gate; lifecycle restart does not overlap it |
| blocking-worker-restart-waits-for-callback | Gated blocking work on Dispatchers.Default is not forcibly stopped; releasing it permits completion and restart |

Non-cooperative checks use explicitly releasable Deferred/CountDownLatch barriers, actual Job cancellation, subscription counts, and overlap assertions without fixed delays. Finally releases gates and disposes internal compositions. Cancellation does not roll back side effects; do not block Main or assume arbitrary callbacks stop immediately. Restart within the same repeatOnLifecycle waits for the previous callback, unlike rebuilding independent source/owner effects.

Build and test-sign the same consumer APK described above. The driver uses the shared CLI/protocol:

```bash
FLOWBUS_COMPOSE_SIGNED_APK="/path/to/test-signed-compose-release.apk"
python3 -B compatibility/compose-consumer/verify-policies-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_COMPOSE_SIGNED_APK" --report-dir build/compose-policies-device
python3 -B compatibility/compose-consumer/verify_effects_test.py
```

The driver requires current-run FlowBusComposePolicy logs, same-package UI, and all eleven stages. Fixture timeout is 30 seconds; driver timeout is 40 seconds. Mocks contain two test methods, checking every missing effect/policy phase and other protocol failure boundaries; they do not execute a device. Record this candidate's API 21/35 and other device results through the [release checklist](../../docs/en/release-checklist.md).

## Published 1.3.0 Composable bytecode baseline

[Legacy130FlowBusEffect.kt](legacy130/src/main/java/com/logan/flowbus/composeconsumer/Legacy130FlowBusEffect.kt) compiles only against public 1.3.0 core / flowbus-compose with fixed Compose compiler 1.5.15 and runtime 1.5.4. Generation validates original hashes from [baselines.json](../core-binary-consumer/baselines.json). Original implementations are compileOnly inputs, while the app runtime resolves candidate coordinates.

`:legacy130:freezeLegacy130` records original compiled class/JAR identity, and the app consumes `legacy130/build/frozen/legacy130-compose.jar` before release R8. After APK construction, `:legacy130:verifyLegacy130Identity` checks unchanged old classes/JAR and actual runtime versions/hashes from the isolated candidate. Its report is `build/maven-local-compose-consumer/legacy130/build/reports/binary-compatibility.json`. `deviceExecution=required-separately` still requires the current twelve-stage effects run; successful freezing, compilation or R8 is not evidence that the old Composable executed on a device. Public symbols are frozen in [api/flowbus-compose-1.3.0.api](api/flowbus-compose-1.3.0.api).

```bash
# Download and verify the published baseline as specified in docs/implementation-plan-1.3.1.md first.
FLOWBUS_VERSION=$(sed -n 's/^VERSION_NAME=//p' gradle.properties)
python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --baseline-artifact build/published-baseline/flowbus-compose-1.3.0.aar \
  --candidate-artifact "build/candidate-maven/io/github/logan0817/flowbus-compose/$FLOWBUS_VERSION/flowbus-compose-$FLOWBUS_VERSION.aar" \
  --baseline-api-snapshot compatibility/compose-consumer/api/flowbus-compose-1.3.0.api \
  --javap /path/to/jdk17/bin/javap \
  --output build/compose-1.3.0-jvm-symbol-verification.json
```
