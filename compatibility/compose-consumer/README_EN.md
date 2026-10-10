# Compose consumer verification example

[中文](README.md)

This directory is an isolated consumer template for evaluating Compose compilation and release R8 on the existing Kotlin 1.9.25 baseline. It is not a production module or a published `flowbus-compose` artifact. Repository verification tasks generate and build the consumer; this directory has no publication task.

The default consumer uses activity-compose `1.8.2`, Compose runtime/ui `1.5.4`, compiler `1.5.15`, and a direct Lifecycle `2.6.2` declaration with the current candidate FlowBus artifact. Runtime/ui `1.7.8` is a separate optional experiment. These dependencies apply only to the consumer; the base library does not add Compose or change its dependency, SDK, or Kotlin requirements. Keep evidence for the two combinations separate: declared versions do not establish resolved dependencies or device compatibility.

`FlowBusEffect` accepts an explicit `FlowBus`, `EventKey<T>`, and caller-provided `LifecycleOwner`, collecting from STARTED by default. Navigation callers can provide their `NavBackStackEntry`; the template adds no navigation dependency and does not use LocalLifecycleOwner. Only the callback uses rememberUpdatedState. Changes to source, event name/type, owner, minimum lifecycle state, or sticky selection restart the effect. INITIALIZED is rejected synchronously during composition.

[Actual NavHost verification](../navhost-consumer/README_EN.md) optionally adds navigation-compose with the navHost property and records standard ComposeNavigator behavior, default animation thresholds, and Activity recreation. An incoming page may be STARTED before RESUMED; select RESUMED explicitly for side effects restricted to the interactive page. An outgoing page remaining composed or animated does not imply an active owner. Collect according to the actual entry lifecycle rather than visual presence.

Each active lifecycle entry fetches Flow from the source and uses core suspend collection with the source logger/error policy. Composition disposal, owner/source changes, or lifecycle inactivity cancel collection and its cooperative suspend callback without closing the bus. Each subscription handles events sequentially, without collectLatest. Normal events are not retained; sticky replay is explicit. Slow callbacks still follow existing buffering/overflow policy. Updating the callback affects subsequent invocations and does not replace work already executing. Application blocking/non-cancellable work must handle cancellation appropriately.

The launcher `ConsumerActivity` actually invokes the effects and uses AndroidView with native buttons and text controls instead of Material. The Application provides the root bus; a separate session bus is constructor-injected into the ViewModel. Both use the same Toast key to exercise instance isolation. Buttons update the captured callback version, post normal events, update StateFlow business state, publish sticky results, and remove/restore the sticky effect. There is no subscription-readiness barrier: post only after active collection starts, since registration does not imply immediate readiness.

StateFlow independently expresses the business counter and restores the latest in-memory value after lifecycle re-entry. Events express transient Toast notifications. The ViewModel preserves state through configuration changes, but the template has no persistent storage or SavedStateHandle and does not guarantee recovery after process death.

Record device checks by scenario: the default 1.5.4 and optional 1.7.8 combinations each passed ten API 24 interaction stages in separate serial runs; both combinations independently passed the added controlled-effect nine stages; process-death recovery, deep links, and other NavHost configurations remain untested. Compilation and R8 do not establish those remaining runtime behaviors.

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

`-Dmaven.repo.local` selects the same location for publication, cleanup, artifact checks, and consumer dependencies. The default remains `~/.m2/repository`. Root publication first deletes the two current-version directories for `flowbus-core` and `flowbus` under the configured FlowBus group, then republishes. Use the isolated candidate directory to avoid replacing existing user-local artifacts. Generated consumers use exclusiveContent so FlowBus dependencies can only resolve from that location and cannot silently fall back to stale artifacts elsewhere.

Select the newer runtime/ui experiment with a consumer-only property:

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.composeVersion=1.7.8 verifyMavenLocalComposeConsumer
```

The consumer uses repository Kotlin/AGP/SDK settings. Preserve actual dependency resolution and AAR metadata results: the direct Lifecycle `2.6.2` declaration does not establish its resolved version. Declaration, compilation, and lowest-API device support are separate evidence.

| Compose runtime/ui | Resolved Lifecycle | Resolved core-ktx |
| --- | --- | --- |
| Default 1.5.4 | 2.6.2 | 1.10.0 |
| Optional 1.7.8 | 2.8.3 | 1.12.0 |

Both use Kotlin 1.9.25, compiler 1.5.15, activity-compose 1.8.2, Coroutines 1.8.1, AGP 8.6.1, and Java 8 bytecode. Preserve the complete releaseRuntimeClasspath graph; direct declarations and APK META-INF markers do not prove resolved versions. Independent API 24 ten-stage interaction, nine effect, and eight policy checks passed for both combinations. Verify API 21 against the final-commit CI device reports through the [release checklist](../../docs/en/release-checklist.md). See the [compatibility assessment](../../docs/en/next-version-plan.md#compatibility-assessment).

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

The driver checks actual API and boot_completed, installs the APK, force-stops its own package, and cold-starts with flowbus.compose.verifyEffects=true and the current flowbus.consumer.runId. The fixture has a 30-second global timeout; the driver polls for up to 40 seconds. Success requires the current FlowBusCompose logs and com.logan.flowbus.composeconsumer UI to match PASS API=<actual API> checks=9 runId=<current UUID> and every stage below. FAIL, stale runId, another foreground package, missing stages, or timeout cannot pass. Evidence is saved under report-dir/current-UUID as start.txt, current.log, ui-dump.txt, current-ui.xml, and the final result.txt. Run serially with instrumentation to avoid UiAutomation contention.

| Stage | Verified behavior |
| --- | --- |
| `initial-replay` | The effect receives the initial sticky probe |
| `source-replaced` | Changing source cancels the old cooperative callback and enters the new-source probe |
| `key-replaced` | Changing key cancels the old callback and enters the new-key probe |
| `owner-replaced` | Changing controlled owner preserves new collection after destroying the old owner |
| `min-state-replaced` | Changing STARTED to RESUMED prevents receipt below the threshold and resumes receipt afterward |
| `composition-disposed-restored` | Effect removal cancels the callback; restored composition receives latest sticky |
| `lifecycle-stopped-restarted` | Inactivity cancels the callback; lifecycle restart receives latest sticky |
| `callback-error-continued` | A normal exception reaches the configured continuing handler; later callbacks still execute |
| `destroyed-source-retained` | DESTROYED cancels; the source still accepts posts and a new owner receives them; cancellation does not reach the normal-exception handler |

Reproduce the driver-decision regression independently:

```bash
python3 -B compatibility/compose-consumer/verify_effects_test.py
```

The original effect method is one unittest with six mocked-ADB subtests; the same file now adds a policy method with seven subtests, for two tests/thirteen subtests total for current PASS, stale PASS, current FAIL, another foreground package, missing stages, and wrong API before installation. They passed but do not establish API 21 runtime success. Default 1.5.4 and optional 1.7.8 new fixtures each have actual nine-stage API 24 evidence. Process-death recovery, deep links, and other NavHost configurations require separate verification. The earlier temporary ten-stage driver is not the repository entry point.

An optional [real Navigation consumer](../navigation-consumer/README_EN.md) now selects navigation-runtime only with -Pflowbus.consumer.navigationVersion=2.4.1. Default Compose source/manifest/dependencies remain free of navigation. The fixture binds actual NavHostController entries to the Activity and passed compilation, release R8, and seven push/pop/back-dispatcher lifecycle stages on API 24 in two independent combinations: Navigation 2.4.1 / Compose 1.5.4 / Lifecycle 2.6.2 and Navigation 2.8.3 / Compose 1.7.8 / Lifecycle 2.8.3. Evidence is build/navigation-verification.json; it does not cover navigation-compose NavHost animations, saved-state/process restoration, or deep links. Returning to the default configuration also passed compilation/R8 and verified no navigation source/manifest/dependency residue, with Lifecycle still at 2.6.2. Android, Compose-effect, and Navigation thin drivers share compatibility/device_verification.py without CLI/timeout changes. Existing five/six mocked scenarios and three Navigation subtests passed; mocks do not establish device success.

## Error-policy and non-cooperative callback verification

ConsumerActivity adds a separate flowbus.compose.verifyPolicies mode started by verify-policies-device.py. It uses an actual Compose runtime Composition and isolated Recomposer to run the same FlowBusEffect; the outer Activity composition renders results. The inner composition contains effects only, without UI nodes. Controlled LifecycleRegistry owners do not establish navigation back-stack or NavHost UI behavior.

The test recomposer isolates expected failures from the result screen. It does not replace the default error handler or add catches inside FlowBusEffect. It observes both CoroutineExceptionHandler and the runner completion cause, requiring recomposer shutdown, zero subscriptions, and original exception identity. This is test infrastructure, not advice to hide application failures with a supervisor.

| Eight stages | Assertion |
| --- | --- |
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

The driver requires current-run FlowBusComposePolicy logs, same-package UI, and all eight stages. Fixture timeout is 30 seconds; driver timeout is 40 seconds. Mocks contain 2 tests / 13 subtests and verify protocol only. Both combinations passed API 24 checks; Verify API 21 and remote CI against the final commit through the [release checklist](../../docs/en/release-checklist.md).
