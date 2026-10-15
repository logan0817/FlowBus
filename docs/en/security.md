# Security policy

[中文](../../SECURITY.md) · [Support](support.md)

Do not disclose exploitable details in public issues, PRs, discussions, or social media.

## Reporting

Email `notwalnut@163.com` with subject `[FlowBus Security] Short description`. If private vulnerability reporting is enabled, you may use the [GitHub security entry](https://github.com/logan0817/FlowBus/security). Include affected modules and versions, reproduction steps, impact, possible mitigations, and any previous disclosure.

## Supported versions

| Version | Policy |
| --- | --- |
| Latest stable | Priority assessment and fixes or mitigation guidance |
| Older versions | Best-effort help, normally recommending an upgrade; no long-term branch or backport commitment |
| Candidates | Confirmed issues handled before publication; candidates are not recommended as stable installations |

See the [project home](../../README_EN.md#install) for the latest release. There is no fixed response or repair SLA. Maintainers assess reproducibility and actual impact, aim to provide a fix or mitigation before coordinated disclosure, and may request more information. A report is not confirmation of a vulnerability or a repair commitment.

## Exposed credentials

Treat exposed tokens, signing keys, passwords, or keystores as compromised. Revoke or rotate them first, remove plaintext files, review ignore rules, assess history/branches/tags/releases, and rotate affected local or CI secrets. History rewriting or destructive cleanup requires coordination with repository owners. Document impact and migration without publishing secret values.

Use [support](support.md) for ordinary bugs, build failures, and documentation errors. Application misuse or unrelated authorization/logging errors are normally outside the library's security scope. Dependency CVEs need an exploitable FlowBus path or demonstrated amplification; automated scanner output may need reproduction evidence. Scenarios requiring physical access, existing device control, or malicious debugging environments normally do not receive high priority.

Do not send long-lived credentials through events. Redact sensitive payloads and keep personal data out of `eventName`, `scopeName`, logs, and error handlers.
