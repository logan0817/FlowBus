# FlowBus 1.4.0 candidate notes

**1.4.0** is unpublished. Its production implementation passed the focused pre-release review on 2026-11-16; final PR/main checks follow the implementation plan. Public installation remains **1.3.1**.

[中文](../release-notes-1.4.0.md) · [Implementation plan (Chinese)](../implementation-plan-1.4.0.md)

This version adds an opt-in per-instance diagnostics observer for publication results, actual subscription start/end, failure phases and source identities that distinguish recreated streams with the same name. Records contain no business values or Throwable objects. Ordinary observer exceptions are isolated from business error policy. Install the observer before obtaining a Flow to observe its complete subscription lifecycle.

It also fixes custom logger failures bypassing the original errorHandler or replacing Android buffer-rejection results. Default delivery, cancellation, sticky and scope lifecycle contracts remain intact; existing FlowBusConfig constructor and copy descriptors are preserved.

Source versions are VERSION_NAME=1.4.0, APP_VERSION_NAME=1.4.0 and APP_VERSION_CODE=13; test APKs use versionCode=3. Current evidence and outstanding gates belong in the implementation plan. Historical 1.3.1 reports do not establish acceptance for this version.


[PR #18's first CI](https://github.com/logan0817/FlowBus/actions/runs/43782753659), attempt 1 on 2026-11-16, passed all nine jobs: 327 JVM cases, API/lint, four Kotlin versions, Java 8, R8, five historical core bytecode baselines and 12 public-symbol reports. API21/35 Android/effect/policy 8/12/11 stages were checked against logs, current run IDs and same-package UI. API35 ran 22 sample tests including both new diagnostics cases. Fixed source/merge commits, candidate hashes and final PR/main gates are in section 9 of the implementation plan.

Disabled diagnostics still has a cost: cloud SampleTime batch means rose about 8.4%/5.3% for no-subscriber/sticky scenarios. Enabled diagnostics has substantial overhead and is intended for temporary investigation. See the [performance record (Chinese)](../../benchmarks/jvm/results/1.4.0/report.md) for actual measurements, allocation changes and limits. This workflow does not upload Maven artifacts, create a tag or publish a GitHub Release; public coordinates cannot yet resolve 1.4.0.
