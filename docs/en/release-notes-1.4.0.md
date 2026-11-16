# FlowBus 1.4.0 release notes

**1.4.0** was published to Maven Central on **2026-11-16**. All three coordinates passed public file, signature and independent consumer verification; the frozen production source and acceptance evidence are recorded below.

[中文](../release-notes-1.4.0.md) · [Implementation plan (Chinese)](../implementation-plan-1.4.0.md)

This version adds an opt-in per-instance diagnostics observer for publication results, actual subscription start/end, failure phases and source identities that distinguish recreated streams with the same name. Records contain no business values or Throwable objects. Ordinary observer exceptions are isolated from business error policy. Install the observer before obtaining a Flow to observe its complete subscription lifecycle.

It also fixes custom logger failures bypassing the original errorHandler or replacing Android buffer-rejection results. Default delivery, cancellation, sticky and scope lifecycle contracts remain intact; existing FlowBusConfig constructor and copy descriptors are preserved.

Source versions are VERSION_NAME=1.4.0, APP_VERSION_NAME=1.4.0 and APP_VERSION_CODE=13; test APKs use versionCode=3. Implementation and acceptance history are recorded in the implementation plan. Historical 1.3.1 reports do not establish acceptance for this version.


[PR #18's first CI](https://github.com/logan0817/FlowBus/actions/runs/43782753659), attempt 1 on 2026-11-16, passed all nine jobs: 327 JVM cases, API/lint, four Kotlin versions, Java 8, R8, five historical core bytecode baselines and 12 public-symbol reports. API21/35 Android/effect/policy 8/12/11 stages were checked against logs, current run IDs and same-package UI. API35 ran 22 sample tests including both new diagnostics cases. Fixed source/merge commits, candidate hashes and final PR/main gates are in section 9 of the implementation plan.

Disabled diagnostics still has a cost: cloud SampleTime batch means rose about 8.4%/5.3% for no-subscriber/sticky scenarios. Enabled diagnostics has substantial overhead and is intended for temporary investigation. See the [performance record (Chinese)](../../benchmarks/jvm/results/1.4.0/report.md) for actual measurements, allocation changes and limits. Version 1.4.0 is now public. Rollback requires removing new diagnostics calls and type references, then recompiling consumers.

## Formal publication acceptance (2026-11-16)

| Item | Evidence |
| --- | --- |
| Source commit | `a8e3c8a9811795d4851c09c2e8248ba2ce4b94a3` |
| Main CI | [43786039022 attempt 1](https://github.com/logan0817/FlowBus/actions/runs/43786039022), 9 / 9 success |
| Candidate manifest SHA-256 | `2088813689c965cd13ccdadd8a5cd2d936fe325cca8cd0cc7fdbcb9b232c3004` |
| Central deployment | `cf3eaf35-ad00-4d60-a7fb-33cfdcd1127e` / `PUBLISHED` |
| Batch | `io.github.logan0817-FlowBus-1.4.0` |
| Public GPG fingerprint | `8BB25F160F45FD8919DC985A152B32281C3E9240` |
| Signed bundle SHA-256 | `a56539992a2f11e9cfa7f16a4a018e07e27e2ad097b7715c2e82670a081dfbe1` |
| Public verification | 150 files / 15 signatures / 120 checksums; exact bytes |
| Independent consumers | Exclusive Central resolution; Java8 smoke, Android/Compose release R8, original 1.1.0/1.3.0 fixture linkage and exact artifact hashes passed. |
| Public-device acceptance | Redmi 23078RKD5C / Android16 / API36: Android8, effect12, policy11; three unique run IDs, current logs and same-package UI checked. |

Published coordinates and primary artifact SHA-256 values:

| Coordinate | SHA-256 |
| --- | --- |
| `io.github.logan0817:flowbus-core:1.4.0` | `503fa89c605755dd15adc8f530211ef62c1ca508708a80614c14a6a33e531bb0` |
| `io.github.logan0817:flowbus:1.4.0` | `90f958f9cc1b155ec8bd900db48d3b59a4e37f34573929af826014ac239dbd6f` |
| `io.github.logan0817:flowbus-compose:1.4.0` | `a6a6f2a38aec6c8cee1bcbfdc935f50b74f5add57cc302e0b912fc4f60a91ef3` |

Published files were signed directly from the frozen main CI candidate, without rebuilding. Formal performance is from run 43782753659; primary archive member contents match final PR and production main. Existing API21/35 evidence tests that candidate; public-dependency device acceptance above is API36. These are distinct scopes, with no ART performance claim. Detailed originals remain local under `build/release-1.4.0/` and `build/verification-1.4.0/`; they are not assumed to ship with Git.
