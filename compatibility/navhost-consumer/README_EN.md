# Actual NavHost consumer verification

[中文](README.md)

This optional fixture uses androidx.navigation.compose.NavHost, rememberNavController, and composable with the standard ComposeNavigator. It is separate from the [custom Navigator back-stack checks](../navigation-consumer/README_EN.md), is not published, and adds no base-library dependencies.

## Generate and build

Select both navigationVersion and navHost. The default consumer and runtime-only navigation consumer do not copy this fixture or add navigation-compose. navHost accepts only true/false; enabling it without a navigation version fails during configuration.

```bash
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.navigationVersion=2.4.1 -Pflowbus.consumer.navHost=true verifyMavenLocalComposeConsumer
./gradlew -Dmaven.repo.local="$PWD/build/candidate-maven" -Pflowbus.consumer.navigationVersion=2.8.3 -Pflowbus.consumer.composeVersion=1.7.8 -Pflowbus.consumer.navHost=true verifyMavenLocalComposeConsumer
```

The generated project remains build/maven-local-compose-consumer, overlaying base Compose, Navigation, and this fixture. ConsumerActivity and NavigationConsumerActivity remain available; the driver explicitly starts NavHostConsumerActivity. Generated NavHostVerificationProfile requires the selected newer combination to observe default animation window. The 2.4.1 combination does not claim animation coverage. Actual releaseRuntimeClasspath establishes resolution; no force is used.

## Eight stages and device entry

Each Activity stage has a 30-second fixture timeout; the driver permits 70 seconds across recreation. Frames, actual cancellation/Deferred barriers, and subscription counts establish readiness without fixed delays. The shared protocol requires current runId, FlowBusNavHost logs, same-package UI, and all phases. Sign a test copy with an existing debug key using the [Android consumer instructions](../android-consumer/README.md#本地测试签名).

```bash
FLOWBUS_NAVHOST_SIGNED_APK="/path/to/test-signed-navhost-release.apk"
python3 -B compatibility/navhost-consumer/verify-navhost-device.py --adb /path/to/android-sdk/platform-tools/adb --serial device-serial --expected-api 24 --apk "$FLOWBUS_NAVHOST_SIGNED_APK" --report-dir build/navhost-device
python3 -B compatibility/navigation-consumer/verify_navigation_test.py
```

| Phase | Target |
| --- | --- |
| home-active | Actual home STARTED, RESUMED, and sticky subscriptions are ready |
| transition-owner-thresholds | Push cancels home RESUMED work; newer default animation observes both pages still composed, outgoing CREATED without subscriptions and incoming STARTED receiving before RESUMED subscription; only detail remains active afterward |
| covered-home-does-not-receive | Covered home receives neither normal nor sticky |
| pop-restores-sticky-without-normal-replay | Pop restores the same home with latest sticky but no missed normal replay |
| popped-owner-does-not-receive | DESTROYED detail receives no later events |
| new-detail-owner-receives-retained-source | A new detail identity receives retained sticky without missed normal replay |
| activity-recreation-restores-detail-owner | Activity.recreate restores detail's actual back stack with a new owner; old owner is DESTROYED and its suspended callback cancels |
| restored-back-stack-returns-home | Restored BackPressedDispatcher returns home and retained sticky arrives |

A test ViewModel retains source and evidence across Activity configuration recreation. The fixture does not manually saveState/restoreState to impersonate rememberNavController. Old entry references are held only for identity/destruction assertions and cleared on completion or failure. Actual NavHost owns entries, page owners, and compositions.

The default STARTED threshold can collect on the incoming page before its enter animation finishes. A page still composed during exit animation does not imply an active owner. Applications can explicitly choose RESUMED for side effects restricted to the currently interactive page. This is not a global single-consumer or navigation-deduplication protocol. Each fixture page uses independent keys; broadcast is not presented as single-recipient delivery. The library's STARTED default remains unchanged.

## Verification results and limits

| Navigation Compose | Compose runtime/ui | Resolved Lifecycle | API 24 |
| --- | --- | --- | --- |
| 2.4.1 | 1.5.4 | 2.6.2 | Compilation, metadata, release R8, and device checks passed |
| 2.8.3 | 1.7.8 | 2.8.3 | Compilation, metadata, release R8, and device checks passed |

No dependency force is used. Disabling optional parameters removes navigation sources, manifest entries, and dependencies; default Lifecycle remains 2.6.2. Published artifacts exclude fixtures. Preserve current runId logs/UI, dependency resolution, and APK signature evidence for each run.

Verify API 21 and remote CI against the final commit through the [release checklist](../../docs/en/release-checklist.md). Process death, deep links, arbitrary animation configurations, and complete NavHost UI after errors remain outside verified coverage. Activity configuration recreation uses a retained ViewModel in the same process and does not prove event persistence. See [Compose policy checks](../compose-consumer/README_EN.md#error-policy-and-non-cooperative-callback-verification) and the [compatibility assessment](../../docs/en/compatibility.md).
