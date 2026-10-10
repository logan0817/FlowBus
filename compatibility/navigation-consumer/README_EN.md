# Navigation consumer device verification

[中文](README.md)

Standard navigation-compose NavHost has a [separate fixture](../navhost-consumer/README_EN.md), enabled with the additional navHost property. It covers actual page compositions, default animation thresholds, and Activity configuration recreation. Results are recorded separately; this custom Navigator fixture does not establish NavHost animation behavior.

This optional consumer fixture uses actual `NavHostController` and `NavBackStackEntry` to verify FlowBusEffect with Navigation back-stack lifecycles. It is not a published module. Navigation dependencies are added only when explicitly selecting the consumer property; the base FlowBus coordinates do not gain them. Default Compose consumption includes no navigation fixture source, manifest, or dependency.

The repository generator first copies the [Compose consumer](../compose-consumer/README_EN.md), then overlays this app template and its manifest when flowbus.consumer.navigationVersion is selected. The generated project remains build/maven-local-compose-consumer. ConsumerActivity remains the launcher; the navigation driver explicitly starts the exported NavigationConsumerActivity. Changing parameters regenerates that directory; do not edit or commit generated files.

## Build, test signing, and device entry point

From the repository root, the current experiment selects Navigation runtime 2.4.1 with default Compose 1.5.4:

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.navigationVersion=2.4.1 verifyMavenLocalComposeConsumer
```

navigationVersion adds only androidx.navigation:navigation-runtime to this generated consumer, without a navigation-compose NavHost. Actual dependency versions come from the releaseRuntimeClasspath graph; see the [Compose dependency authority](../compose-consumer/README_EN.md#generation-and-build). The task publishes candidates to the isolated Maven directory, generates the project, and runs release R8; it does not sign or run the APK.

The unsigned APK is build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk; its mapping is under the same app output's mapping/release/mapping.txt. Align and sign this Navigation APK using the [local test-signing procedure](../android-consumer/README.md#本地测试签名), substituting the input/output paths and avoiding production keys. Replace these placeholders with an actual signed APK, existing adb, selected device serial, and expected API:

```bash
FLOWBUS_NAVIGATION_SIGNED_APK="/path/to/test-signed-navigation-release.apk"
python3 -B compatibility/navigation-consumer/verify-navigation-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_NAVIGATION_SIGNED_APK" --report-dir build/navigation-device
```

The thin driver uses the [shared device protocol](../device_verification.py), checks API/boot, installs the signed APK, force-stops its own package, and cold-starts NavigationConsumerActivity with the current runId. The fixture has a 30-second timeout and the driver polls for up to 40 seconds. Success requires current FlowBusNavigation logs with PASS API=<actual API> checks=7 runId=<current UUID> and matching current com.logan.flowbus.composeconsumer UI with all seven phase names. Failures, stale results, other foreground packages, missing phases, or timeouts return nonzero. Evidence is stored under report-dir/runId as start.txt, current.log, current-ui.xml, and result.txt written only after success. Run serially with instrumentation and other UI drivers.

Ordinary Android and Compose effect drivers share the same protocol, retaining their existing CLI/timeouts. Reproduce navigation protocol mocks with:

```bash
python3 -B compatibility/navigation-consumer/verify_navigation_test.py
```

Three navigation mocked-ADB subtests passed: current seven-stage success, missing-phase rejection, and rejection of another fixture's nine-stage result. The existing five ordinary-Android and six Compose-effect scenarios also passed. These mocks verify protocol decisions only, not Navigation runtime or API 21 success.

## Seven stages and scope

NavHostController binds to the actual Activity LifecycleOwner, ViewModelStore, and BackPressedDispatcher. A custom Navigator only creates destinations; standard Navigation push/pop manages entry lifecycles. Composition intentionally retains covered and popped entries so cancellation must come from the actual entry lifecycle, not composition removal alone.

| Stage | Verification target |
| --- | --- |
| `home-active` | Active home receives a normal event |
| `navigate-stops-covered-home` | Pushing detail moves home to CREATED, cancels its callback, and leaves zero normal/sticky subscriptions |
| `covered-home-does-not-receive` | Covered home handles neither normal nor sticky events |
| `pop-restores-sticky-without-normal-replay` | Popping restores the same home, replays latest sticky, and does not replay missed normal events |
| `popped-owner-does-not-receive` | DESTROYED detail handles no later normal/sticky events |
| `new-detail-owner-receives-retained-source` | A new detail entry receives retained sticky while the old detail stays DESTROYED |
| `back-dispatcher-returns-home` | Actual BackPressedDispatcher returns to home and cancels the new detail callback |

Probe entered/cancelled barriers and frame-based diagnostic subscriptionCount readiness establish timing without fixed delays. Diagnostic snapshots are test readiness aids, not business-control or completion guarantees.

This targets actual NavController/entry back-stack behavior; it does not establish navigation-compose NavHost animations, saved-state/process recovery, deep links, or all navigation scenarios. Default Rethrow, Ignore, legacy/custom handlers, and two controlled non-cooperative callback cases passed eight stages in two independent Composition combinations on API 24; see [error/cancellation verification](../compose-consumer/README_EN.md#error-policy-and-non-cooperative-callback-verification). That does not establish NavHost UI behavior after failures. Complete API 21 device verification remains unfinished.

## Verification results and limits

| Navigation | Compose runtime/ui | Resolved Lifecycle | API 24 |
| --- | --- | --- | --- |
| 2.4.1 | 1.5.4 | 2.6.2 | Compilation, metadata, release R8, and device checks passed |
| 2.8.3 | 1.7.8 | 2.8.3 | Compilation, metadata, release R8, and device checks passed |

No dependency force is used. Disabling optional parameters removes navigation sources, manifest entries, and dependencies; default Lifecycle remains 2.6.2. Published artifacts exclude fixtures. Preserve current runId logs/UI, dependency resolution, and APK signature evidence for each run.

API 21 and remote CI remain pending. Process death, deep links, arbitrary animation configurations, and complete NavHost UI after errors remain outside verified coverage. Activity configuration recreation uses a retained ViewModel in the same process and does not prove event persistence. See [Compose policy checks](../compose-consumer/README_EN.md#error-policy-and-non-cooperative-callback-verification) and the [compatibility assessment](../../docs/en/next-version-plan.md#compatibility-assessment).
