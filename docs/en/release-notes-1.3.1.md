# FlowBus 1.3.1 development notes (unpublished)

[中文](../release-notes-1.3.1.md) · [Implementation plan](../implementation-plan-1.3.1.md) (Chinese) · [Documentation](README.md)

**1.3.1** is not published. The public stable version remains **1.3.0**. These notes describe candidate work and intermediate evidence, not a completed release. Consumers should use public 1.3.0 unless explicitly verifying local changes through the [isolated candidate workflow](../../CONTRIBUTING.md#candidate-builds).

Local validation uses `130c4aa375155917fdd296e312d4dfab14acee66` plus the candidate changes. That base commit does not identify these fixes. Final tested commits, artifact hashes and complete CI results must be recorded before release.

## Scope and status

| Change | Problem | Current status |
| --- | --- | --- |
| Sticky reentrant publication | Receiving `1` and immediately publishing `2` yields live `[1, 2]` but latest replay `1` | Behavior, formal CI performance and API21/35 pass |
| Scope binding failure safety | A CoroutineScope without a Job makes openScope throw after registering an unreachable handle | Validation before registration is implemented; local targeted tests passed |
| Lifecycle thresholds and lookup side effects | DESTROYED silently never subscribes; invalid calls may obtain a global/owner source first | L01–L07 and real Compose API21/35 stages pass |
| Published 1.3.0 consumer baseline | Newly released APIs lack original-bytecode execution against a replacement candidate | Core unchanged bytecode on Java8 and Android/Compose R8, identity and device execution pass |

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

Candidate sticky behavior regressions pass locally and in the first PR CI. Final pre-release acceptance remains pending. `accepted=true` still does not guarantee live delivery without drops or completed business work. Existing DROP_LATEST differences between live delivery and replay remain, and no total order is promised for otherwise unordered concurrent calls.

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

These are intermediate results and must be reassessed when their inputs change. First PR/device evidence follows below; main CI and public release acceptance remain pending.

## First PR acceptance

[PR #16](https://github.com/logan0817/FlowBus/pull/16), head `c90cb55`, passed all nine checks in [CI 42969426491](https://github.com/logan0817/FlowBus/actions/runs/42969426491), attempt 1, checkout `475c0f141c2b40b2288e7cc79952044143160546`. Coverage includes 284 JVM tests, APIs/release lint, four Kotlin versions, Java 8, four original core bytecode baselines, public symbols, release R8 and API21/35. API35 library instrumentation passed 3 tests and sample release instrumentation passed 20, without failures or skips.

Downloaded Android/effect/policy reports pass 8/12/11 phases respectively on both APIs, including original-compiled 1.3.0 Java/Kotlin/Composable calls. Current run IDs, same-package UI, every phase and candidate manifests match. API21 retains Navigation/NavHost 7/8 phases. All 98 class files and 100 runtime file contents in the CI core JAR match the local measured candidate; container hashes differ and remain separately identified.

Workstation timing data remains disturbed by background load; see the [directed performance report](../../benchmarks/jvm/results/1.3.1/report.md). The PR remains draft, with an explicit formal comparison in the existing consumers runner next. Final performance and subsequent PR/main CI acceptance remain pending; the first pass does not cover later edits automatically.

## Required before release

1. Pass final sticky regressions, all affected unit tests, API checks and release lint.
2. Run original 1.3.0 core/Android/Compose compiled consumers against the candidate, verify hashes and symbols, and retain older baseline coverage.
3. Complete the existing Kotlin matrix, Java 8, API 21/35 instrumentation and Maven release R8 consumers. The new 11 Compose policy stages need actual logs, same-package UI and the current run ID.
4. Record directed performance comparisons between public 1.3.0 and the final candidate. Smoke does not establish speedup or absence of regressions.
5. Add final candidate commit/hashes, complete CI runs, unresolved limits and rollback details here. Publication follows the applicable authorization and [release checklist](release-checklist.md); these notes do not authorize publication.

## Formal performance and second CI acceptance

Initial measurements found approximately 48 additional bytes per sticky event. JFR motivated direct single-value replay and size reads, preserving ordering, generations and cancellation. Reversed resumption covers replay=1/2; APIs, 284 JVM tests and lint pass. Workstation timing remained disturbed and is retained with its limitations.

[CI 42971579052](https://github.com/logan0817/FlowBus/actions/runs/42971579052), head `7dd6bdf`, attempt 1, checkout `1380cf166d7519be2b015e131eeaa43aff120938`, passed all nine checks. A formal same-runner comparison yields throughput ratios approximately 1.003/1.018/0.998 with overlapping error intervals; no material timing regression was observed, while sticky allocation drops about 22%. The [formal report](../../benchmarks/jvm/results/1.3.1/report.md) retains settings, iterations, identities and limits, without promising stable speedups or ART performance.

Both APIs again pass 8/12/11 consumer stages, alongside four Kotlin versions, Java8, historical bytecode/symbols and R8. Logs/UI/manifests were audited. The final documentation commit must still pass full CI before merge and main CI verification. Production and benchmark inputs stay unchanged, so formal measurement is reused. Final head, merge commit and main checks are identified by [PR #16](https://github.com/logan0817/FlowBus/pull/16) merge/Checks records and final delivery, avoiding recursive commits solely to reference their own run IDs.

## Rollback

Public 1.3.0 artifacts and tags remain intact. Returning from a local candidate to 1.3.0 restores the original sticky reentrancy, failed scope registration and DESTROYED-threshold issues. Recompile and verify the affected integration. Revert development fixes through separate commits rather than replacing published artifacts or rewriting shared history.
