# FlowBus documentation

[中文](../README.md) · [Project home](../../README_EN.md)

The latest stable version is **1.2.0**, published to Maven Central on 2026-10-15. APIs marked “since 1.2.0” are available through the released coordinates. Publication and public-artifact verification are recorded in the [release notes](release-notes-1.2.0.md).

## User guides

| Guide | Content |
| --- | --- |
| [Quick start](../../README_EN.md#5-minute-quick-start) | Installation, global events, owners, and named channels |
| [Android](../../library-android/README_EN.md) | Lifecycle, suspend callbacks, explicit instances, owner configuration |
| [Core](../../flowbus-core/README_EN.md) | Kotlin/JVM, instances, scopes, sticky, and diagnostics |
| [Compatibility](compatibility.md) | Toolchains, legacy consumers, device checks, and limits |
| [Troubleshooting](../../TROUBLESHOOTING.md) (Chinese) | Sources, keys, readiness, overflow, builds, and cleanup |
| [JVM benchmarks](../../benchmarks/jvm/README.md) (Chinese) | Reproduction, results, and measurement limits |

The sample app is in [app](../../app). [Compose](../../compatibility/compose-consumer/README_EN.md), [Navigation](../../compatibility/navigation-consumer/README_EN.md), and [NavHost](../../compatibility/navhost-consumer/README_EN.md) are independent consumers, not published modules.

## Releases

| Version | Status | Changes |
| --- | --- | --- |
| [1.2.0](release-notes-1.2.0.md) | Published | Channel configuration, delivery contracts, performance baseline |
| [1.1.0](release-notes-1.1.0.md) | Published | API 21, scope closure, suspend callbacks, instance integration |
| [1.0.7](release-notes-1.0.7.md) | Historical | Scope close/remove race fix |
| [1.0.6](release-notes-1.0.6.md) | Historical | Single repository, diagnostics, close waiting, sticky consumption |

See the [changelog](../../CHANGELOG.md) for a summary. Historical notes describe their own release; old installation examples and verification records do not replace evidence for the current release.

## Contributing and maintenance

- [Contributing](contributing.md): environment, verification, naming, and documentation.
- [Support](support.md), [security policy](security.md), and [code of conduct](code-of-conduct.md).
- [Release checklist](release-checklist.md): candidate artifacts, publication, and rollback.
- [1.2.0 implementation plan](../implementation-plan-1.2.0.md) (Chinese): historical requirements and design decisions. User guides and source describe current behavior.

## Documentation conventions

Chinese is the default maintenance language. Root and module English guides retain `README_EN.md`; other English guides live in `docs/en/`. Use descriptive lowercase, hyphenated filenames and versioned release notes. Update existing authoritative documents before adding another account of the same topic. Organize user guides by capability, put version details in release notes, and identify the tested commit in CI evidence. Historical passes do not establish current HEAD success.

`next-version-plan.md` remains a legacy link entry; the matrix is maintained in `compatibility.md`. Link new topics from this index and update anchors when headings change, retaining stable explicit anchors where needed.
