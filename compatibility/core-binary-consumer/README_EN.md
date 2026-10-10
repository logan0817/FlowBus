# Core 1.0.7 binary consumer regression

[中文](README.md)

The fixture only calls published 1.0.7 APIs. It compiles Kotlin and Java consumers against the original Maven Central core JAR, then runs the same compiled classes against the original and candidate JARs. This complements recompilation against the candidate by exercising existing inline, default-argument, and direct Java JVM references.

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

The task publishes the 1.1.0 candidate to the selected local repository, without remote publication. The baseline is the non-transitive original io.github.logan0817:flowbus-core:1.0.7 from Maven Central. Candidate execution uses an absolute JAR path; local artifacts must never be used as the published baseline.

The generated build/maven-local-binary-core-consumer compiles only against the original core, baseline stdlib, and Coroutines. Candidate execution removes the original core from its classpath. Both executions verify the actual FlowBus codeSource. JAR hashes must differ, and consumer class hashes must remain unchanged across execution. A failed runtime verification does not retain an earlier successful runtime report.

## Coverage and evidence

Coverage includes old reified collect defaults on root/Default/shared/owned sources; channel default and explicit sources; sticky replay/consume/clear; close/reopen; Job-bound close; removeScope; legacy error-handler continuation; and Java constructors, Class key factory, and scope calls. Readiness uses UNDISPATCHED, Deferred, and subscriptions rather than fixed delays.

The separate verify_symbols.py uses javap to compare declared public/protected JVM member descriptors and static/instance form on stable named classes. Artifact hashes must match the runtime report. Default methods, bridges, and PublishedApi JVM symbols remain included; numbered compiler implementation classes and ACC_SYNTHETIC access$ private implementation accessors are excluded. The original 46 named classes and 631 descriptors passed. The current seven regression tests also cover safe classes.jar-only AAR reading and cleanup, missing classes.jar rejection, changed artifacts, and cross-checking a public API snapshot against original bytecode.

### Static AAR mode

The script accepts --baseline-artifact and --candidate-artifact for direct JAR/AAR comparison. It reads classes.jar only from an AAR; this does not certify resources, manifests, consumer rules, or device behavior. See [Android consumer verification](../android-consumer/README.md#已发布-107-aar-的-jvm-符号核对) for the original Android artifact, fixed hash, public snapshot, and commands.

Optional --baseline-api-snapshot limits the supported API gate to an original binary-compatibility-validator snapshot, first verified against actual original bytecode. Raw JVM differences remain in otherJvmRemovals, including internal changes. The snapshot must belong to the original release, rather than the current module with candidate additions. Without a snapshot, all included descriptors must remain; the existing core runtime-report command retains this behavior.

This is not full ABI certification: visibility, inheritance, interface implementation, Android AAR binary replacement, all public API behavior, arbitrary dependency versions, and other toolchains are outside the symbol check. Unexecuted inline paths have no runtime evidence. Verify API 21 device checks and remote CI against the final commit through the [release checklist](../../docs/en/release-checklist.md). Base dependencies remain unchanged; 1.1.0 is an unreleased candidate.
