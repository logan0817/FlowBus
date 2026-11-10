# Contributing to FlowBus

[中文](../../CONTRIBUTING.md) · [Documentation](README.md)

This repository maintains core, the Android adapter, the optional Compose adapter, and the sample app together. Keep changes focused, and describe their behavior and validation in the PR.

## Environment and verification

Use JDK 17, the repository Gradle wrapper, and Android SDK 35. The base build uses Kotlin 1.9.25 and AGP 8.6.1; Android minSdk is 21. No submodule initialization is required.

```bash
git clone https://github.com/logan0817/FlowBus.git
cd FlowBus
./gradlew :flowbus-core:test :library-android:testDebugUnitTest :app:testDebugUnitTest apiCheck
./gradlew :library-android:lintRelease :library-compose:lintRelease :app:lintRelease
```

On Windows use `gradlew.bat` with the same task arguments. Choose checks matching the change. Public API changes require reviewed API snapshots and compatibility checks; do not edit snapshots just to bypass a failure.

Real artifact gates use an isolated Maven directory and an existing JDK 8 for consumer execution; the build still runs on JDK 17:

```bash
./gradlew verifyMavenLocalArtifacts verifyMavenLocalCoreConsumer verifyMavenLocalBinaryCoreConsumer verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
./gradlew verifyMavenLocalBinaryCoreConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.baselineVersion=1.1.0 -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
./gradlew verifyMavenLocalBinaryCoreConsumer "-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.consumer.baselineVersion=1.2.0 -Pflowbus.consumer.java=/path/to/jdk8/bin/java -Pflowbus.consumer.expectedJava=1.8
```

Root `publishToMavenLocal` deletes the selected repository's current-version directories for core, Android, and optional Compose before republishing. Do not point candidate checks at a personal Maven repository. Generated consumers use `exclusiveContent` to prevent fallback to remote artifacts. The binary task defaults to 1.0.7; repeat it for 1.1.0 and 1.2.0 as above. Android has seven device stages including actual Java source, and Compose has eleven effect/eight policy stages against its optional published-format AAR.

Device tests and UI drivers must run sequentially against the selected device. Both API 21 and API 35 verify candidate Android/Compose APKs. Before API 35 emulator startup run `bash compatibility/run-api35.sh --prepare`, then invoke the same script without arguments after boot. Follow candidate sealing and environment setup in the [release checklist](release-checklist.md). Mocks or local results on another API do not replace API 21/35/cloud evidence. Consumer compiler parameters change the generated fixture, not the base library. Keep Kotlin/Compose compiler pairings compatible; see the [compatibility guide](compatibility.md).

## PRs, naming, and documentation

- Keep platform-neutral changes in core, Android integration in `library-android`, optional Compose effects in `library-compose`, and examples in `app`.
- Use descriptive branches such as `fix/scope-cleanup` or `docs/release-guides`. Do not rename existing candidates only for style.
- Use a type prefix and optional scope for commits and PRs, for example `fix(core): 修复 scope 关闭后的清理竞态`. Commit bodies explain changes and actual validation. Chinese is the default maintenance language.
- Do not add unrelated tool/model identities to branch, file, commit, or PR names.
- Document public behavior, API changes, dependencies, and defaults in the corresponding Chinese and English guides, KDoc, and release notes.
- Follow the [documentation conventions](README.md#documentation-conventions). Check local links and heading anchors after moves or renames. Keep stable installation examples on the published version; label candidate APIs explicitly.
- Do not commit credentials, signing keys, personal configuration, generated APKs, logs, or build outputs.
- Use the PR template to explain background, behavior, scope, actual checks, limitations, and rollback when relevant.

<a id="candidate-builds"></a>

## Local development artifacts

1.2.0 is available from Maven Central for normal installation. To verify local source changes, publish development artifacts from the repository root:

```bash
./gradlew publishToMavenLocal "-Dmaven.repo.local=$PWD/build/candidate-maven"
```

Use an absolute path in the consumer's dependency repositories, restricting the FlowBus group to that repository:

```kotlin
repositories {
    exclusiveContent {
        forRepository {
            maven { url = uri("/absolute/path/to/FlowBus/build/candidate-maven") }
        }
        filter { includeGroup("io.github.logan0817") }
    }
    mavenCentral()
    google() // Android consumers
}
```

For centralized repository management, put this in `dependencyResolutionManagement.repositories` in the consumer's `settings.gradle.kts`. Use `implementation("io.github.logan0817:flowbus:1.3.0-SNAPSHOT")` on Android, or `implementation("io.github.logan0817:flowbus-core:1.3.0-SNAPSHOT")` on JVM. Local consumption does not establish publication or complete device acceptance.

Current sources are 1.3.0-SNAPSHOT and are not published. Compose consumers additionally use `io.github.logan0817:flowbus-compose:1.3.0-SNAPSHOT`; ordinary users keep public 1.2.0. Local verification must not be described as a public release.
