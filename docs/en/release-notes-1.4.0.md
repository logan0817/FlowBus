# FlowBus 1.4.0 candidate notes

**1.4.0** is under development, unpublished, and has not passed pre-release review. Public installation remains **1.3.1**.

[中文](../release-notes-1.4.0.md) · [Implementation plan (Chinese)](../implementation-plan-1.4.0.md)

This version adds an opt-in per-instance diagnostics observer for publication results, actual subscription start/end, failure phases and source identities that distinguish recreated streams with the same name. Records contain no business values or Throwable objects. Ordinary observer exceptions are isolated from business error policy. Install the observer before obtaining a Flow to observe its complete subscription lifecycle.

It also fixes custom logger failures bypassing the original errorHandler or replacing Android buffer-rejection results. Default delivery, cancellation, sticky and scope lifecycle contracts remain intact; existing FlowBusConfig constructor and copy descriptors are preserved.

Source versions are VERSION_NAME=1.4.0, APP_VERSION_NAME=1.4.0 and APP_VERSION_CODE=13; test APKs use versionCode=3. Current evidence and outstanding gates belong in the implementation plan. Historical 1.3.1 reports do not establish acceptance for this version.
