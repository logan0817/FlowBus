# FlowBus 1.3.1 development notes (unpublished)

[中文](../release-notes-1.3.1.md) · [Implementation plan](../implementation-plan-1.3.1.md) (Chinese) · [Documentation](README.md)

**1.3.1** is not published. The public stable version remains **1.3.0**. These notes describe candidate work and intermediate evidence, not a completed release. Consumers should use public 1.3.0 unless explicitly verifying local changes through the [isolated candidate workflow](../../CONTRIBUTING.md#candidate-builds).

Local validation uses `130c4aa375155917fdd296e312d4dfab14acee66` plus the candidate changes. That base commit does not identify these fixes. Final tested commits, artifact hashes and complete CI results must be recorded before release.

## Scope and status

| Change | Problem | Current status |
| --- | --- | --- |
| Sticky reentrant publication | Receiving `1` and immediately publishing `2` yields live `[1, 2]` but latest replay `1` | Local core/Android regressions pass; formal performance and API 21/35 verification remain pending |
| Scope binding failure safety | A CoroutineScope without a Job makes openScope throw after registering an unreachable handle | Validation before registration is implemented; local targeted tests passed |
| Lifecycle thresholds and lookup side effects | DESTROYED silently never subscribes; invalid calls may obtain a global/owner source first | Android/Compose validation is implemented; Android L01–L07 regressions passed; real Compose device stages are pending |
| Published 1.3.0 consumer baseline | Newly released APIs lack original-bytecode execution against a replacement candidate | Original core bytecode passes on Java 8; Android/Compose R8 and frozen identity checks pass; device/CI evidence remains pending |

The target keeps the three coordinates, Java 8 bytecode, Android API 21, and existing base toolchain/dependency versions. It adds no public API, production dependency, durable queue, or reliable-delivery guarantee. Final public-symbol compatibility still requires candidate API and binary gates.

## Scope creation failure

`openScope(name, closeWhen: CoroutineScope)` and the owner overload check for a Job before registering a handle. A missing Job still produces a clear exception, without retaining a new scope name or removing/changing an existing shared store. Pass a caller-managed structured scope containing a Job.

Invalid arguments to `bindTo` on an already held handle do not destroy that handle. Valid, completed and cancelled Jobs retain the existing lifecycle-close protocol. Previously acquired Flow references are not actively cancelled by this fix.

## Lifecycle thresholds and upgrading

Only `CREATED`, `STARTED`, and `RESUMED` are valid collection thresholds. `INITIALIZED` and `DESTROYED` synchronously throw `IllegalArgumentException`, identifying the supplied and valid values. Generic Flow collectors, direct buses, explicit root/shared/owned sources and Compose effects use the same rule.

Global and owner convenience entry points validate before obtaining the source. Invalid calls therefore do not lock default configuration or access an owner's ViewModelStore. An already destroyed owner with a valid threshold still completes without subscribing; that differs from choosing DESTROYED as the threshold.

Recompile consumers to obtain the complete fix. Public inline `collectEvent` may contain old validation in consumer bytecode. An old reified wrapper may also contain source lookup even if it calls an updated non-inline method. Replacing the AAR alone cannot update those instructions. Defaults for valid thresholds, dispatcher, sticky selection and source ownership retain their established contract. Java's default STARTED entry points add no state parameter.

## Sticky verification boundary

The final fix must preserve rejected/cancelled sends not polluting replay, generation isolation for clear/consume, existing backpressure and DROP policies, and subscription notification before historical replay. Moving replay commit before a potentially rejected send, delaying a reentrant send, or weakening assertions is not an acceptable substitute.

The 1.3.0 failure and formal regression are confirmed; the final candidate sticky fix is not yet verified. `accepted=true` still does not guarantee live delivery without drops or completed business work. Existing DROP_LATEST differences between live delivery and replay remain, and no total order is promised for otherwise unordered concurrent calls.

## Local evidence so far

- W1's three formal behavioral regressions failed for the expected reasons before fixes. Local reports are under `build/verification-1.3.1/w1-red-results/`.
- Scope creation-failure tests passed; the execution log is `build/verification-1.3.1/w3-scope.log`.
- Android `LifecycleThresholdTest` passed 5 tests and `InstanceCollectEventTest` passed 12, covering invalid thresholds, mutable global defaults, untouched owner stores, supported thresholds and destroyed receivers. The corresponding `w2-w4-integration.log` still contained a core failure, so the overall run was not green.
- The Compose policy fixture now has 11 stages, up from 8, adding `invalid-lifecycle-thresholds-rejected`, `supported-lifecycle-thresholds`, and `destroyed-owner-does-not-subscribe`. An isolated Recomposer checks synchronous parameter failures at an ordinary Kotlin boundary and releases test resources in finally; production Composables do not swallow the failures.
- Both Compose driver test methods passed, including rejection when any individual phase is missing. Mock results do not establish real Composition, release R8, or API 21/35 execution.

- `w7-local-quality-final.log`: 201 core, 81 Android and 2 sample JVM tests pass, along with all three API checks, Android/Compose/sample release lint, sample debug/release R8 builds and 1.3.1 deployment-name validation. Lint XML reports zero errors and warnings.
- `w5-core130-fixed.log`: unchanged 1.3.0 consumer bytecode runs against original and candidate on Java 8.
- `w5-identity-final.log`: Android/Compose frozen fixture, release R8 and candidate runtime digest checks pass; device execution is still required.
- `w5-negative-results.json`: five negative cases reject changed fixture/JAR, class and baseline hashes, original input leaking into runtime, and missing reports. Stale success reports are removed; normal input passes again after restoration.
- All three 1.3.0 public-symbol snapshots have no missing descriptors. Four raw JVM changes belong to implementation classes explicitly marked private in the original InnerClasses metadata. They remain in otherJvmRemovals; public/protected/internal class descriptor removals still fail. Nine script regressions pass.

These are intermediate results and must be reassessed when their inputs change. No 1.3.1 PR/main CI, device acceptance, or public Maven artifact evidence is recorded here yet. Historical 1.3.0 passes cannot fill those gaps.

## Required before release

1. Pass final sticky regressions, all affected unit tests, API checks and release lint.
2. Run original 1.3.0 core/Android/Compose compiled consumers against the candidate, verify hashes and symbols, and retain older baseline coverage.
3. Complete the existing Kotlin matrix, Java 8, API 21/35 instrumentation and Maven release R8 consumers. The new 11 Compose policy stages need actual logs, same-package UI and the current run ID.
4. Record directed performance comparisons between public 1.3.0 and the final candidate. Smoke does not establish speedup or absence of regressions.
5. Add final candidate commit/hashes, complete CI runs, unresolved limits and rollback details here. Publication follows the applicable authorization and [release checklist](release-checklist.md); these notes do not authorize publication.

## Performance review in progress

Initial formal measurements found approximately 48 additional bytes per sticky event. JFR samples included TreeMap entries, boxed Long keys and temporary lists used only to count replay. The candidate now stores replay=1 directly and reads replay size without a list, preserving ordering, generations and cancellation. `w6-optimized-quality.log` passes APIs, 284 JVM tests, release lint and local artifacts. Reversed producer resumption covers replay=1/2. Optimized formal measurements and reversed-order baseline measurements remain in progress; no performance acceptance is claimed yet.

## Rollback

Public 1.3.0 artifacts and tags remain intact. Returning from a local candidate to 1.3.0 restores the original sticky reentrancy, failed scope registration and DESTROYED-threshold issues. Recompile and verify the affected integration. Revert development fixes through separate commits rather than replacing published artifacts or rewriting shared history.
