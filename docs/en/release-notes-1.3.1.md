# FlowBus 1.3.1 release notes

[中文](../release-notes-1.3.1.md) · [Implementation plan](../implementation-plan-1.3.1.md) (Chinese) · [Documentation](README.md)

**1.3.1** was published to Maven Central on 2026-11-11 with core, Android and optional Compose coordinates. [GitHub Release v1.3.1](https://github.com/logan0817/FlowBus/releases/tag/v1.3.1) is public and its tag points to the tested source below.

```kotlin
implementation("io.github.logan0817:flowbus:1.3.1")
// Optional for JVM-only consumers
implementation("io.github.logan0817:flowbus-core:1.3.1")
// Optional for Compose consumers
implementation("io.github.logan0817:flowbus-compose:1.3.1")
```

## Fixes and upgrade guidance

| Fix | Trigger and resulting behavior |
| --- | --- |
| Sticky reentrant ordering | When a callback receives `1` and immediately publishes `2`, replay remains `2` even if the outer publication resumes later |
| Scope binding exception safety | `openScope(name, closeWhen)` validates the CoroutineScope Job before registration; invalid parameters still throw without leaving a new handle or changing an existing shared store |
| Lifecycle thresholds and source access | Only `CREATED`, `STARTED`, and `RESUMED` are valid minima; Android/Compose reject `INITIALIZED` and `DESTROYED` synchronously with `IllegalArgumentException`, before convenience APIs obtain their global/owner source |

Public APIs, coordinates, Java 8 bytecode, minSdk 21 and base dependencies remain: Kotlin 1.9.25, Coroutines 1.8.1, Lifecycle 2.6.2. Optional Compose retains runtime 1.5.4 / compiler 1.5.15; the two base artifacts do not transitively introduce Compose.

**Recompile consumers when upgrading.** Old inline `collectEvent` calls may embed earlier validation; old reified wrappers may embed the source-access order. Replacing only the AAR cannot update these call sites. Default STARTED, callback dispatcher, sticky selection and source ownership retain their contract. An already destroyed owner with a valid threshold still completes without subscribing.

Sticky changes preserve rejection/cancellation rules, clear/consume generations, backpressure, DROP policies and notification-before-replay ordering. `accepted=true` does not mean business processing completed. Existing live/replay differences under `DROP_LATEST` remain, and unsynchronized concurrent calls do not gain a global order. Invalid `bindTo` arguments on a held handle do not destroy that handle.

## Fixed publication source

| Identity | Verified value |
| --- | --- |
| Source / tag target | `2dbd9581af58ae376141d8ada62d4f26e6507754`, merge commit of [PR #16](https://github.com/logan0817/FlowBus/pull/16) |
| main CI | [42975281531, attempt 2](https://github.com/logan0817/FlowBus/actions/runs/42975281531/attempts/2), all nine checks successful |
| CI candidate manifest SHA-256 | `d2e6409b5a2b89441da087eb2d7a08411dd71fbfa7d3ec177cc485f5147347b5` |
| Full GPG fingerprint | `8BB25F160F45FD8919DC985A152B32281C3E9240` |
| Central deployment | `00ac090a-26b4-430c-820b-455fae6a4189`, `PUBLISHED` |
| Signed bundle SHA-256 | `da4bff803a47a4798f4c801f09ea73266817a3f9d4a50ad644822e7431ceabf2` |

The original main CI files were signed locally without rebuilding the libraries. After uploading 15 payloads and their signatures, all 30 were downloaded and compared in `VALIDATED` state before publishing that same deployment. Public Maven Central verification then matched **150 files, 15 signatures and 120 checksums** against the fixed signed bundle.

| Main artifact | SHA-256 |
| --- | --- |
| flowbus-core-1.3.1.jar | `c28674aac6e420fc31ee04c6baddccd9371c12087fe421ac686a9f86791d0df4` |
| flowbus-1.3.1.aar | `bb3db06162d21f4c4f12fb797f40c89522c34d27a1c4a2acca131fc8a8940615` |
| flowbus-compose-1.3.1.aar | `a6a6f2a38aec6c8cee1bcbfdc935f50b74f5add57cc302e0b912fc4f60a91ef3` |

Subsequent documentation commits and their CI candidates do not replace these public artifacts. The version tag targets the tested source above. The final documentation PR Checks and resulting main CI establish the later documentation state.

## Verification and limits

- Final main: 284 JVM tests (core 201 / Android 81 / sample 2), three-module API checks, three release lint reports with zero issues, Kotlin 1.8.22 / 1.9.22 / 2.0.21 / 2.1.0, Java 8 and release R8 passed.
- The same original-compiled core bytecode from 1.0.7 / 1.1.0 / 1.2.0 / 1.3.0 ran on the candidate. Frozen 1.3.0 Android/Compose consumers and public symbols passed; nine symbol reports and ten binary/identity reports were audited. Raw private implementation differences remain in `otherJvmRemovals`; static symbols are not complete ABI certification.
- API 21/35: Android **8**, Compose effect **12**, policy **11** stages passed, with current run IDs, matching-package UI, logs and candidate identities. API 21 also retained Navigation/NavHost 7/8 stages; API 35 passed 3 library and 20 sample release instrumentation tests.
- Independent public consumers resolved FlowBus exclusively from Maven Central, and actual Gradle runtime artifact hashes matched the table above. Core ran on Java 8; Android/Compose passed release R8 and OnePlus API 33 device checks with **8 / 12 / 11** stages.
- The formal [directed performance report](../../benchmarks/jvm/results/1.3.1/report.md) uses a same-runner comparison from [CI 42971579052](https://github.com/logan0817/FlowBus/actions/runs/42971579052). noSubscribers / stickySend / active1 throughput ratios were approximately 1.003 / 1.018 / 0.998 with overlapping error intervals; sticky allocation decreased by about 22%. This does not promise stable speedups or Android ART performance.

The first main CI attempt encountered API 35 emulator ColorBuffer/texture errors, a black screen and InputDispatcher `NO_WINDOW`, followed by an Espresso window-focus timeout. Failure evidence was retained; a complete rerun of the same source passed all nine checks in attempt 2 without weakening assertions or coverage. The emulator root cause remains unresolved.

Development red tests, negative identity tests and two PR validation rounds are documented in the [implementation plan](../implementation-plan-1.3.1.md) and [PR #16](https://github.com/logan0817/FlowBus/pull/16). The identity table above is authoritative for publication.

## Rollback

Restore 1.3.0 dependencies, recompile and revalidate the entry points you use. The sticky reentrancy, failed scope registration and DESTROYED threshold defects also return. Public 1.3.0/1.3.1 artifacts and tags must not be overwritten; further fixes require a new version.
