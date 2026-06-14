package com.logan.flowbus.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryOwnershipTest {

    private val rootDir: File = File("..").canonicalFile

    @Test
    fun `main repository owns flowbus core source tree`() {
        assertFalse("flowbus-core must not be registered as a git submodule.", File(rootDir, ".gitmodules").exists())
        assertFalse("flowbus-core must not keep nested git metadata.", File(rootDir, "flowbus-core/.git").exists())
        assertFalse("flowbus-core must not keep an independent Gradle settings file.", File(rootDir, "flowbus-core/settings.gradle.kts").exists())
        assertFalse("flowbus-core must not keep an independent Gradle properties file.", File(rootDir, "flowbus-core/gradle.properties").exists())
        assertFalse("flowbus-core must not keep an independent Gradle wrapper.", File(rootDir, "flowbus-core/gradlew").exists())
        assertFalse("flowbus-core must not keep an independent Gradle wrapper batch file.", File(rootDir, "flowbus-core/gradlew.bat").exists())
        assertFalse("flowbus-core must not keep an independent Gradle wrapper directory.", File(rootDir, "flowbus-core/gradle").exists())

        val settings = File(rootDir, "settings.gradle.kts").readText()
        assertTrue(settings.contains("\"flowbus-core\""))
        assertTrue(File(rootDir, "flowbus-core/src/main/java/com/logan/flowbus/core/FlowBus.kt").exists())
    }

    @Test
    fun `root project owns release entry points`() {
        val rootBuildFile = File(rootDir, "build.gradle.kts").readText()
        val coreBuildFile = File(rootDir, "flowbus-core/build.gradle.kts").readText()
        val androidBuildFile = File(rootDir, "library-android/build.gradle.kts").readText()

        assertTrue(rootBuildFile.contains("tasks.register(\"publishToMavenCentral\")"))
        assertTrue(rootBuildFile.contains("tasks.register(\"releaseToMavenCentral\")"))
        assertTrue(rootBuildFile.contains("tasks.register(\"verifyMavenLocalArtifacts\")"))
        assertTrue(rootBuildFile.contains(":flowbus-core:publishAllPublicationsToMavenCentralRepository"))
        assertTrue(rootBuildFile.contains(":library-android:publishAllPublicationsToMavenCentralRepository"))
        assertTrue(rootBuildFile.contains(":flowbus-core:publishToMavenLocal"))
        assertTrue(rootBuildFile.contains(":library-android:publishToMavenLocal"))
        assertTrue(rootBuildFile.contains("remoteMavenPublicationRequested"))
        assertTrue(rootBuildFile.contains("tasks.withType<Sign>().configureEach"))
        assertTrue(coreBuildFile.contains("publishToMavenCentral(false)"))
        assertTrue(androidBuildFile.contains("publishToMavenCentral(false)"))

        assertFalse(coreBuildFile.contains("layout.projectDirectory.file(\"gradle.properties\")"))
        assertFalse(coreBuildFile.contains("flowbus-core.git"))
        assertFalse(coreBuildFile.contains("publishToMavenCentral(true)"))
        assertFalse(androidBuildFile.contains("publishToMavenCentral(true)"))
    }

    @Test
    fun `repository keeps no generated apk or signing material`() {
        val readme = File(rootDir, "README.md").readText()
        val readmeEn = File(rootDir, "README_EN.md").readText()
        val gitignore = File(rootDir, ".gitignore").readText()

        assertFalse(
            "Release signing keystore must not be stored in the source repository.",
            File(rootDir, "signing/FlowBus.jks").exists()
        )
        assertFalse(
            "Signing passwords or keystore notes must not be stored in the source repository.",
            File(rootDir, "signing/info.txt").exists()
        )
        assertFalse(
            "Debug APK artifacts must not be stored in the source repository.",
            File(rootDir, "app/apk/app-debug.apk").exists()
        )
        assertTrue(gitignore.contains("/signing/"))
        assertTrue(gitignore.contains("/app/apk/*"))
        assertFalse(gitignore.contains("!/app/apk/app-debug.apk"))
        assertFalse(readme.contains("app/apk/app-debug.apk"))
        assertFalse(readmeEn.contains("app/apk/app-debug.apk"))
    }

    @Test
    fun `sample app manifest keeps distributable builds inside a narrow data boundary`() {
        val mainManifest = File(rootDir, "app/src/main/AndroidManifest.xml").readText()
        val debugManifest = File(rootDir, "app/src/debug/AndroidManifest.xml").readText()
        val dataExtractionRules = File(rootDir, "app/src/main/res/xml/data_extraction_rules.xml")
        val backupRules = File(rootDir, "app/src/main/res/xml/backup_rules.xml")

        assertTrue(mainManifest.contains("android:allowBackup=\"false\""))
        assertTrue(mainManifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(mainManifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertFalse(mainManifest.contains("tools:ignore=\"AllowBackup\""))
        assertTrue(dataExtractionRules.exists())
        assertTrue(backupRules.exists())
        assertTrue(dataExtractionRules.readText().contains("<exclude domain=\"root\" path=\".\""))
        assertTrue(backupRules.readText().contains("<exclude domain=\"root\" path=\".\""))
        assertTrue(debugManifest.contains("android:testOnly=\"true\""))
    }

    @Test
    fun `release guardrails include api compatibility and device stability checks`() {
        val versionCatalog = File(rootDir, "gradle/libs.versions.toml").readText()
        val rootBuildFile = File(rootDir, "build.gradle.kts").readText()
        val ciWorkflow = File(rootDir, ".github/workflows/ci.yml").readText()
        val releaseChecklist = File(rootDir, "docs/release-checklist.md").readText()
        val releaseChecklistEn = File(rootDir, "docs/en/release-checklist.md").readText()
        val releaseDocs = releaseChecklist + "\n" + releaseChecklistEn

        assertTrue(versionCatalog.contains("binary-compatibility-validator"))
        assertTrue(rootBuildFile.contains("alias(libs.plugins.binary.compatibility.validator)"))
        assertTrue(rootBuildFile.contains("apiValidation"))
        assertTrue(rootBuildFile.contains("ignoredProjects.add(\"app\")"))
        assertTrue(ciWorkflow.contains("./gradlew apiCheck"))
        assertTrue(ciWorkflow.contains("window_animation_scale 0"))
        assertTrue(ciWorkflow.contains("transition_animation_scale 0"))
        assertTrue(ciWorkflow.contains("animator_duration_scale 0"))
        assertTrue(ciWorkflow.contains("connectedDebugAndroidTest"))
        assertTrue(releaseDocs.contains("apiCheck"))
        assertTrue(releaseDocs.contains("connectedDebugAndroidTest"))
        assertTrue(releaseDocs.contains("animation scale"))
        assertTrue(releaseDocs.contains("动画"))
    }

    @Test
    fun `published artifacts keep the modern compatibility floor`() {
        val versionCatalog = File(rootDir, "gradle/libs.versions.toml").readText()
        val settings = File(rootDir, "settings.gradle.kts").readText()
        val gradleWrapper = File(rootDir, "gradle/wrapper/gradle-wrapper.properties").readText()
        val coreBuildFile = File(rootDir, "flowbus-core/build.gradle.kts").readText()
        val androidBuildFile = File(rootDir, "library-android/build.gradle.kts").readText()
        val rootReadme = File(rootDir, "README.md").readText()
        val rootReadmeEn = File(rootDir, "README_EN.md").readText()
        val releaseChecklist = File(rootDir, "docs/release-checklist.md").readText()
        val releaseChecklistEn = File(rootDir, "docs/en/release-checklist.md").readText()
        val docs = rootReadme + "\n" + rootReadmeEn + "\n" + releaseChecklist + "\n" + releaseChecklistEn

        assertTrue(versionCatalog.contains("agp = \"8.6.1\""))
        assertTrue(versionCatalog.contains("kotlin = \"1.9.25\""))
        assertTrue(versionCatalog.contains("compile_sdk_version = \"35\""))
        assertTrue(versionCatalog.contains("target_sdk_version = \"35\""))
        assertTrue(gradleWrapper.contains("gradle-8.7-bin.zip"))
        assertTrue(
            gradleWrapper.contains(
                "distributionSha256Sum=544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d"
            )
        )
        assertTrue(settings.contains("kotlin(\"android\") version \"1.9.25\""))
        assertTrue(settings.contains("kotlin(\"jvm\") version \"1.9.25\""))
        assertTrue(coreBuildFile.contains("JavaVersion.VERSION_1_8"))
        assertTrue(coreBuildFile.contains("JvmTarget.JVM_1_8"))
        assertTrue(androidBuildFile.contains("JavaVersion.VERSION_1_8"))
        assertTrue(androidBuildFile.contains("JvmTarget.JVM_1_8"))
        assertTrue(docs.contains("Kotlin 1.9.25"))
        assertTrue(docs.contains("AGP 8.6.1"))
        assertTrue(docs.contains("Gradle 8.7"))
        assertTrue(docs.contains("JDK 17"))
        assertTrue(docs.contains("Java 8"))
    }

    @Test
    fun `documentation describes unified repository ownership`() {
        val readme = File(rootDir, "README.md").readText()
        val readmeEn = File(rootDir, "README_EN.md").readText()
        val troubleshooting = File(rootDir, "TROUBLESHOOTING.md").readText()
        val releaseChecklist = File(rootDir, "docs/release-checklist.md")
        val releaseChecklistEn = File(rootDir, "docs/en/release-checklist.md")

        assertTrue(releaseChecklist.exists())
        assertTrue(releaseChecklistEn.exists())

        val releaseDocs = releaseChecklist.readText() + "\n" + releaseChecklistEn.readText()
        assertTrue(readme.contains("`flowbus-core` 由 FlowBus 主仓库直接维护"))
        assertTrue(readmeEn.contains("`flowbus-core` is maintained directly in the FlowBus main repository"))
        assertTrue(troubleshooting.contains("flowbus-core 已由主仓库直接维护"))
        assertTrue(releaseDocs.contains(":flowbus-core:test"))
        assertTrue(releaseDocs.contains(":library-android:testDebugUnitTest"))
        assertTrue(releaseDocs.contains("verifyMavenLocalArtifacts"))
        assertTrue(releaseDocs.contains("publishToMavenLocal"))
        assertTrue(releaseDocs.contains("releaseToMavenCentral --dry-run"))
        assertTrue(releaseDocs.contains("不会自动 release"))
        assertTrue(releaseDocs.contains("does not automatically release"))
        assertTrue(releaseDocs.contains("smoke check"))

        assertFalse(readme.contains("git submodule"))
        assertFalse(readmeEn.contains("git submodule"))
        assertFalse(troubleshooting.contains("git submodule update"))
        assertFalse(troubleshooting.contains("git clone --recurse-submodules"))
    }
}
