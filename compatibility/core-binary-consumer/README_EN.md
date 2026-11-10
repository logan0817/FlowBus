# Core 1.0.7 / 1.1.0 / 1.2.0 binary consumer regression

[中文](README.md)

The fixture compiles Kotlin and Java consumers against a selected original Maven Central core JAR, then runs the same compiled classes against original and candidate JARs. Baselines are 1.0.7 (default), 1.1.0 and 1.2.0. This complements recompilation against the candidate by exercising existing inline, default-argument, and direct Java JVM references. Current sources and public installation use **1.3.0**. This entry verifies isolated local candidates by default and does not replace public dependency verification.

## Run

Run from the repository root. The build uses the project JDK 17; replace the consumer executable with an existing JDK 8:

```bash
./gradlew verifyMavenLocalBinaryCoreConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" \
  -Pflowbus.consumer.java=/path/to/jdk8/bin/java \
  -Pflowbus.consumer.expectedJava=1.8

python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --runtime-report build/maven-local-binary-core-consumer/build/reports/binary-compatibility.json \
  --javap /path/to/jdk17/bin/javap \
  --output build/core-jvm-symbol-verification.json

python3 -B -m unittest discover -s compatibility/core-binary-consumer -p '*_test.py' -v
```

By default the task publishes the current 1.3.0 candidate to the selected local repository, without remote publication. `-Pflowbus.verification.reuseCandidate=true` instead checks/reuses a sealed candidate. The selected original version resolves non-transitively from Central, defaulting to io.github.logan0817:flowbus-core:1.0.7. Candidate execution uses an absolute JAR path; local artifacts must never be used as the published baseline.

The generated build/maven-local-binary-core-consumer compiles only against the original core, baseline stdlib, and Coroutines. Candidate execution removes the original core from its classpath. Both executions verify the actual FlowBus codeSource. JAR hashes must differ, and consumer class hashes must remain unchanged across execution. A failed runtime verification does not retain an earlier successful runtime report.

## Coverage and evidence

Coverage includes old reified collect defaults on root/Default/shared/owned sources; channel default and explicit sources; sticky replay/consume/clear; close/reopen; Job-bound close; removeScope; legacy error-handler continuation; and Java constructors, Class key factory, and scope calls. Readiness uses UNDISPATCHED, Deferred, and subscriptions rather than fixed delays.

The separate verify_symbols.py uses javap to compare declared public/protected JVM member descriptors and static/instance form on stable named classes. Artifact hashes must match the runtime report. Default methods, bridges, and PublishedApi JVM symbols remain included; numbered compiler implementation classes and ACC_SYNTHETIC access$ private implementation accessors are excluded. The historical 1.0.7 comparison included 46 named classes and 631 descriptors; that does not certify the current candidate. Regression tests also cover safe classes.jar-only AAR reading and cleanup, missing classes.jar rejection, changed artifacts, and cross-checking a public API snapshot against original bytecode.

### Static AAR mode

The script accepts --baseline-artifact and --candidate-artifact for direct JAR/AAR comparison. It reads classes.jar only from an AAR; this does not certify resources, manifests, consumer rules, or device behavior. See [Android consumer verification](../android-consumer/README_EN.md#published-binary-baselines) for the original Android artifact, fixed hash, public snapshot, and commands.

Optional --baseline-api-snapshot limits the supported API gate to an original binary-compatibility-validator snapshot, first verified against actual original bytecode. Raw JVM differences remain in otherJvmRemovals, including internal changes. The snapshot must belong to the original release, rather than the current module with candidate additions. Without a snapshot, all included descriptors must remain; the existing core runtime-report command retains this behavior.

This is not full ABI certification: visibility, inheritance, interface implementation, Android AAR binary replacement, all public API behavior, arbitrary dependency versions, and other toolchains are outside the symbol check. Unexecuted inline paths have no runtime evidence. Verify API 21/35 device checks and remote CI against the final commit through the [release checklist](../../docs/en/release-checklist.md). Core base dependencies remain unchanged.

## 1.1.0 baseline

Repeat verifyMavenLocalBinaryCoreConsumer with -Pflowbus.consumer.baselineVersion=1.1.0. The version-specific fixture additionally executes suspend callbacks on root/shared/owned sources and channel adapters, plus Java FlowBusConfig constructor/component/copy descriptors. baselines.json pins the original Central JAR SHA-256. The unchanged-bytecode runtime report is build/maven-local-binary-core-consumer-1.1.0/build/reports/binary-compatibility.json; pass that path to verify_symbols.py. The default still preserves 1.0.7 coverage. This task checks locally built candidates and does not replace public-repository acceptance. Android old-bytecode runtime coverage is documented in the [Android consumer](../android-consumer/README_EN.md).

## 1.2.0 baseline

The new baseline preserves 1.0.7/1.1.0 coverage and pins the original 1.2.0 SHA-256 in [baselines.json](baselines.json). Do not rebuild the release from source. In addition to existing Kotlin/Java calls, `src-1.2.0` exercises channel-config construction/copy, root/shared/owned configuration, idempotent configuration, effective overflow policy and sticky operations. Generated files and reports use a separate version suffix:

```bash
./gradlew verifyMavenLocalBinaryCoreConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" \
  -Pflowbus.consumer.baselineVersion=1.2.0 \
  -Pflowbus.consumer.java=/path/to/jdk8/bin/java \
  -Pflowbus.consumer.expectedJava=1.8

python3 -B compatibility/core-binary-consumer/verify_symbols.py \
  --runtime-report build/maven-local-binary-core-consumer-1.2.0/build/reports/binary-compatibility.json \
  --baseline-api-snapshot compatibility/core-binary-consumer/api/flowbus-core-1.2.0.api \
  --javap /path/to/jdk17/bin/javap \
  --output build/core-1.2.0-jvm-symbol-verification.json
```

The original fixed API snapshot is first verified against the original JAR, then scopes the supported-symbol gate. Other JVM changes remain in `otherJvmRemovals` for review. Android 1.2.0 uses the original AAR and [fixed API snapshot](../android-consumer/api/flowbus-1.2.0.api) in static mode; see the [Android consumer](../android-consumer/README_EN.md#published-binary-baselines). Neither core unchanged-bytecode execution nor Android static symbols prove old Android 1.2.0 bytecode executing on a device.
